///////////////////////////////////////////////////////////////////////////////
// FILE:          SimpleAF.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Collection of auto-focusing algorithms and supporting devices
//                
// AUTHOR:        Prashanth Ravindran, prashanth@100ximaging.com, February, 2009
//
// COPYRIGHT:     100X Imaging Inc, 2010, http://www.100ximaging.com
//
// LICENSE:       This library is free software; you can redistribute it and/or
//                modify it under the terms of the GNU Lesser General Public
//                License as published by the Free Software Foundation.
//                
//                You should have received a copy of the GNU Lesser General Public
//                License along with the source distribution; if not, write to
//                the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
//                Boston, MA  02111-1307  USA
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

#ifdef WIN32
   #define WIN32_LEAN_AND_MEAN
   #include <windows.h>
   #define snprintf _snprintf 
#endif

#include "SimpleAF.h"
#include <string>
#include <cmath>
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>
#include <ctime>
#include "SimpleAFImageUtils.h"


using namespace std;
const char* g_AutoFocusDeviceName = "SimpleAF";
const char* g_FocusMonitorDeviceName = "FocusMonitor";
const char* g_PresetPropName = "Preset";

// windows DLL entry code
#ifdef WIN32
   BOOL APIENTRY DllMain( HANDLE /*hModule*/, 
                          DWORD  ul_reason_for_call, 
                          LPVOID /*lpReserved*/
		   			 )
   {
   	switch (ul_reason_for_call)
   	{
   	case DLL_PROCESS_ATTACH:
  	   case DLL_THREAD_ATTACH:
   	case DLL_THREAD_DETACH:
   	case DLL_PROCESS_DETACH:
   		break;
   	}
       return TRUE;
   }
#endif

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////

MODULE_API void InitializeModuleData()
{
   AddAvailableDeviceName(g_AutoFocusDeviceName, "Exhaustive search AF - 100XImaging Inc.");
   AddAvailableDeviceName(g_FocusMonitorDeviceName, "Focus score monitor - 100XImaging Inc.");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0; 
   
   // decide which device class to create based on the deviceName parameter
   if (strcmp(deviceName, g_AutoFocusDeviceName) == 0)
   {
      // create autoFocus
      return new SimpleAF();
   }
   if (strcmp(deviceName, g_FocusMonitorDeviceName) == 0)
   {
      // create autoFocus
      return new FocusMonitor();
   }

   // ...supplied name not recognized
   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

SimpleAF::SimpleAF():
		busy_(false),initialized_(false),timemeasurement_(false),
			start_(0),stop_(0),timestamp_(0),param_channel_(""),bestscore_(0.0),totalimagessnapped_(0)
{
}

void SimpleAF::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_AutoFocusDeviceName);
}

int SimpleAF::Initialize()
{
   if (initialized_)
      return DEVICE_OK;

   // set property list
   // -----------------
   
   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, g_AutoFocusDeviceName, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "AF search, 100X Imaging Inc", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Set Exposure
   CPropertyAction *pAct = new CPropertyAction (this, &SimpleAF::OnExposure);
   CreateProperty(MM::g_Keyword_Exposure, "10.0", MM::Float, false, pAct); 

   // Set the depth for coarse search
   pAct = new CPropertyAction(this, &SimpleAF::OnSearchSpanCoarse);
   CreateProperty("FullSpan","300",MM::Float, false, pAct);

   // Set the depth for fine search
   pAct = new CPropertyAction(this, &SimpleAF::OnSearchSpanFine);
   CreateProperty("IncrementalSpan","100",MM::Float, false, pAct);

   // Set the span for coarse search
   pAct = new CPropertyAction(this, &SimpleAF::OnStepsizeCoarse);
   CreateProperty("FullStep","10",MM::Float, false, pAct);

   // Set the span for fine search
   pAct = new CPropertyAction(this, &SimpleAF::OnStepSizeFine);
   CreateProperty("IncrementalStep","3",MM::Float, false, pAct);

   pAct = new CPropertyAction(this, &SimpleAF::OnChannelForAutofocus);
   CreateProperty(g_PresetPropName, "", MM::String, false, pAct);

   // Set the threshold for decision making
   pAct = new CPropertyAction(this, &SimpleAF::OnThreshold);
   CreateProperty("Threshold","0.05",MM::Float, false, pAct);

   // Get the total number of images acquired
   pAct = new CPropertyAction(this, &SimpleAF::OnNumImagesAcquired);
   CreateProperty("NumberOfImages","0",MM::Integer, true, pAct);

   // Set the cropping factor to speed up computation
   pAct = new CPropertyAction(this, &SimpleAF::OnCropFactor);
   CreateProperty("CropFactor","1.0",MM::Float, false, pAct);

  
   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;
   initialized_ = true;
   return DEVICE_OK;
}

int SimpleAF::FullFocus()
{
	Focus(FULLFOCUS);
	return DEVICE_OK;
}

int SimpleAF::IncrementalFocus()
{
	Focus(INCREMENTALFOCUS);
	return DEVICE_OK;	
}

// Measure and apply offsets that you have pre-determined
int SimpleAF::SetOffset(double offset)
{
	double currpos = 0.0;
	int ret = GetCoreCallback()->GetFocusPosition(currpos);
	if (ret != DEVICE_OK)
	{
		return ret;
	}
	ret = GetCoreCallback()->SetFocusPosition(currpos - offset);
	if (ret != DEVICE_OK)
	{
		return ret;
	}
	return DEVICE_OK;
}
int SimpleAF::GetOffset(double &offset)
{
	double homepos = 0.0f;
	// Get the current home position
	int ret = GetCoreCallback()->GetFocusPosition(homepos);
	if (ret != DEVICE_OK)
	{
		return ret;
	}
	// Do full focus
	ret = FullFocus();
	if (ret != DEVICE_OK)
	{
		return ret;
	}
	// Do incremental focus
	ret = IncrementalFocus();
	if (ret != DEVICE_OK)
	{
		return ret;
	}
	// Measure the current position
	double currentpos = 0.0f;	
	ret = GetCoreCallback()->GetFocusPosition(currentpos);
	if (ret != DEVICE_OK)
	{
		return ret;
	}
	// calculate offset
	offset = homepos - currentpos;
	return DEVICE_OK;
}

// Get the best observed focus score

int SimpleAF::GetLastFocusScore(double & score)
{
	score = score_;
	return DEVICE_OK;
}

// Calculate the focus score at a given point
int SimpleAF::GetCurrentFocusScore(double &score)
{
	exposure_.SetCore(GetCoreCallback());
	exposure_.SetExposureToAF(param_afexposure_);
	GetCoreCallback()->LogMessage(this, "Acquiring image for profiling",false);
	int width = 0, height = 0, depth = 0;
	int ret  = GetCoreCallback()->GetImageDimensions(width, height, depth);
	if(ret != DEVICE_OK)
		return ret;
   //score = 0.0;
   // Get the image for analysis
	ImgBuffer image(width,height,depth);
	GetImageForAnalysis(image);
   // score it
	// Check for cropping factor and crop it if required
	if(param_crop_ratio_ < 1.0)
	{
		int newx = (int)(param_crop_ratio_*width);
		int newy = (int)(param_crop_ratio_*height);
		if(newx && newy)
		{
			ImgBuffer tmpimage = image;
			image.Resize(newx,newy);
			long index = 0;
			long count = 0;
			int start_x_index = (int)((float)(width - newx)/2.0f);
			int end_x_index   = (int)((float)(width + newx)/2.0f);
			int start_y_index = (int)((float)(height - newy)/2.0f);
			int end_y_index   = (int)((float)(height + newy)/2.0f);
			for(int j = start_y_index ; j < end_y_index; ++j)
			{
				for(int i = start_x_index; i < end_x_index; ++i)
				{
					index = newx*j + i;
					*(image.GetPixelsRW() + count) = *(tmpimage.GetPixels() + index);
					++count;					
				}
			}
		}
	}
	score = GetScore(image);
	double zPos = 0.0;
	ret = GetCoreCallback()->GetFocusPosition(zPos);
	std::stringstream mesg;
	mesg<<"Score is "<<score<<", and the focus position is "<<zPos;
	GetCoreCallback()->LogMessage(this,mesg.str().c_str() ,false);
	mesg.str("");
	
   // report
   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// Properties
///////////////////////////////////////////////////////////////////////////////

int SimpleAF::OnStepsizeCoarse(MM::PropertyBase *pProp, MM::ActionType eAct)
{
	if(eAct == MM::AfterSet)
	{
		double StepSizeCoarse = 0.0f;
		pProp->Get(StepSizeCoarse);
		this->param_stepsize_coarse_ = StepSizeCoarse;

	}
	else if(eAct == MM::BeforeGet)
	{
		double StepSizeCoarse = 0.0f;
		pProp->Get(StepSizeCoarse);
		this->param_stepsize_coarse_ = StepSizeCoarse;
	}
	return DEVICE_OK;
}

int SimpleAF::OnChannelForAutofocus(MM::PropertyBase *pProp, MM::ActionType eAct)
{
	if(eAct == MM::AfterSet)
	{
		std::string Channel;
		pProp->Get(Channel);
		param_channel_ = Channel;
	}
   else if(eAct == MM::BeforeGet)
   {
      std::vector<std::string> presets;
      presets.reserve(100);
      int ret = GetCoreCallback()->GetChannelConfigs(presets);
      if (ret != DEVICE_OK)
         return ret;
      
      if (presets.size() > 0)
      {
         // if there are any channel presets defined, create a list of allowed values
         presets.push_back("");
         SetAllowedValues(g_PresetPropName, presets);
      }
      else
      {
         // if no chanel presets are defined allow any value
         // NOTE: in general it does not make sense to allow any value, because if this value
         // does not correspond to an existing preset a run time error will occur when the AF
         // device is used. But allowing any value removes the possibility of errors during loading
         // of the inital configuration, i.e. before actual presets are defined
         ClearAllowedValues(g_PresetPropName);
      }
   }

	return DEVICE_OK;
}

int SimpleAF::OnStepSizeFine(MM::PropertyBase *pProp, MM::ActionType eAct)
{
	if(eAct == MM::AfterSet)
	{
		double StepSizeFine = 0.0f;
		pProp->Get(StepSizeFine);
		this->param_stepsize_fine_ = StepSizeFine;
	}
	else if(eAct == MM::BeforeGet)
	{
	    double StepSizeFine = 0.0f;
		pProp->Get(StepSizeFine);
		this->param_stepsize_fine_ = StepSizeFine;
	}


	return DEVICE_OK;
}

int SimpleAF::OnThreshold(MM::PropertyBase *pProp, MM::ActionType eAct)
{
	if(eAct == MM::AfterSet)
	{
		double Threshold = 0.0f;
		pProp->Get(Threshold);
		this->param_decision_threshold_ = Threshold;
	}
	else if(eAct == MM::BeforeGet)
	{
		double Threshold = 0.0f;
		pProp->Get(Threshold);
		this->param_decision_threshold_ = Threshold;
	}


	return DEVICE_OK;
}

int SimpleAF::OnExposure(MM::PropertyBase *pProp, MM::ActionType eAct)
{
	if(eAct == MM::AfterSet)
	{
		double Exposure = 0.0f;
		pProp->Get(Exposure);
		this->param_afexposure_ = Exposure;
	}
	else if(eAct == MM::BeforeGet)
	{
		double Exposure = 0.0f;
		pProp->Get(Exposure);
		this->param_afexposure_ = Exposure;
	}
	return DEVICE_OK;
}

int SimpleAF::OnSearchSpanCoarse(MM::PropertyBase *pProp, MM::ActionType eAct)
{
	if(eAct == MM::AfterSet)
	{
		double CoarseSpan = 0.0f;
		pProp->Get(CoarseSpan);
		this->param_coarse_search_span_ = CoarseSpan;
	}
	else if(eAct == MM::BeforeGet)
	{
		double CoarseSpan = 0.0f;
		pProp->Get(CoarseSpan);
		this->param_coarse_search_span_ = CoarseSpan;
	}
	return DEVICE_OK;
}

int SimpleAF::OnNumImagesAcquired(MM::PropertyBase *pProp, MM::ActionType eAct)
{
	if(eAct == MM::AfterSet)
	{
		long images = totalimagessnapped_;
		pProp->Set(images);
	}
	else if(eAct == MM::BeforeGet)
	{
		long images = totalimagessnapped_;
		pProp->Set(images);
	}
	return DEVICE_OK;
}

int SimpleAF::OnSearchSpanFine(MM::PropertyBase *pProp, MM::ActionType eAct)
{
	if(eAct == MM::AfterSet)
	{
		double FineSpan = 0.0f;
		pProp->Get(FineSpan);
		this->param_coarse_search_span_ = FineSpan;
	}
	else if(eAct == MM::BeforeGet)
	{
		double FineSpan = 0.0f;
		pProp->Get(FineSpan);
		this->param_fine_search_span_ = FineSpan;
	}
	return DEVICE_OK;
}

int SimpleAF::OnCropFactor(MM::PropertyBase *pProp, MM::ActionType eAct)
{
	if(eAct == MM::AfterSet)
	{
		double CropRatio = 0.0f;
		pProp->Get(CropRatio);
		this->param_crop_ratio_ = CropRatio;
	}
	else if(eAct == MM::BeforeGet)
	{
		double CropRatio = 0.0f;
		pProp->Get(CropRatio);
		this->param_crop_ratio_ = CropRatio;
	}
	return DEVICE_OK;
}
// End of properties

int SimpleAF::Focus(SimpleAF::FocusMode focusmode)
{
	
	totalimagessnapped_ = 0;
	score_ = -1.0;
	bestscore_ = -1.0;
	double previousbest = -1.0;
	// Set Channel to the required channel
	int ret = GetCoreCallback()->SetConfig("Channel",param_channel_.c_str());
	if(ret != DEVICE_OK)
		return ret;
	// Do full focus
	if(focusmode == FULLFOCUS)
	{
		activespan_ = param_coarse_search_span_;
		activestep_ = param_stepsize_coarse_;

	}
	// Do incremental focus
	if(focusmode == INCREMENTALFOCUS)
	{
		activespan_ = param_fine_search_span_;
		activestep_ = param_stepsize_fine_;
	}

	// Set scope to the way you need it to do AF
	ret = InitScope();
	if(ret != DEVICE_OK)
		return ret;

	// Start looking for object of interest

	double dZHomePos = 0.0f, dzPos = 0.0f,dzTopPos = 0.0;
	GetCoreCallback()->GetFocusPosition(dZHomePos);
	double dBestZPos = dZHomePos;

	GetCoreCallback()->LogMessage(this, "Started focus method",false);

	// The old value is stored in dzHomePos -- To restore if focus fails
	// dzPos is the variable that always stores the position that you want to go to

	dzPos = dZHomePos - activespan_/2.0f;
	dzTopPos = dZHomePos + activespan_/2.0f;

	// Go to the lowest pos

	ret = GetCoreCallback()->SetFocusPosition(dzPos);
	if(ret != DEVICE_OK)
		return ret;
	bool proceed = true;

	// Get the camera and image parameters
	int width = 0, height = 0, depth = 0;
	ret  = GetCoreCallback()->GetImageDimensions(width, height, depth);
	if(ret != DEVICE_OK)
		return ret;
	int count = 0;
	std::stringstream mesg;
	ImgBuffer image(width,height,depth);
	score_ = GetScore(image);
	while(proceed)
	{
		//1. Get an image
		
		GetImageForAnalysis(image);
		Metadata IMd;
		MetadataSingleTag stgExp(MM::g_Keyword_Exposure, g_AutoFocusDeviceName, true);
		stgExp.SetValue(CDeviceUtils::ConvertToString(param_afexposure_));
		IMd.SetTag(stgExp);

		MetadataSingleTag stgZ(MM::g_Keyword_Metadata_Z, g_AutoFocusDeviceName, true);
		stgZ.SetValue(CDeviceUtils::ConvertToString(dzPos));
		IMd.SetTag(stgZ);
		
		//2. Get its sharness score
		score_ = GetScore(image);

		
		MetadataSingleTag stgScore(MM::g_Keyword_Metadata_Score, g_AutoFocusDeviceName, true);
		stgScore.SetValue(CDeviceUtils::ConvertToString(score_));
		IMd.SetTag(stgScore);
	
		reporter_.InsertCurrentImageInDebugStack(&IMd);
		

		//3. Do the exit test
		if(score_ >= bestscore_)
		{
			previousbest = bestscore_;
			bestscore_ = score_;
			dBestZPos  = dzPos;			
		}
		else if ((bestscore_ - score_) > param_decision_threshold_*bestscore_ && dBestZPos < 5000) 
		{
			proceed = false;
			// Go to the best pos
			ret = GetCoreCallback()->SetFocusPosition(dBestZPos);
			mesg<<"Large change in focus score hence aborting"<<(count);
			GetCoreCallback()->LogMessage(this, mesg.str().c_str(),false);
			mesg.str("");
			
			if(ret != DEVICE_OK)
				return ret;
        }		
		//4. Move the stage for the next run
		dzPos += activestep_;
		if(dzPos < dzTopPos)
		{
			ret = GetCoreCallback()->SetFocusPosition(dzPos);
			if(ret != DEVICE_OK)
				return ret;
		}
		else
		{
			proceed = false;
			// Go to the best pos
			ret = GetCoreCallback()->SetFocusPosition(dBestZPos);
			if(ret != DEVICE_OK)
				return ret;

		}
		++count;

		// Logging messages
		mesg<<"Acquired image "<<(count);
		GetCoreCallback()->LogMessage(this, mesg.str().c_str(),false);
		mesg.str("");
		mesg<<"Current z-pos is :"<<dzPos<<", The span is : "<<activespan_<<", The step is :"<<activestep_;
		GetCoreCallback()->LogMessage(this, mesg.str().c_str(),false);
		mesg.str("");
		mesg<<"Current score is :"<<score_;
		GetCoreCallback()->LogMessage(this, mesg.str().c_str(),false);
		mesg.str("");
		mesg<<"The top point for the search is "<<dzTopPos;
		GetCoreCallback()->LogMessage(this, mesg.str().c_str(),false);
		mesg.str("");
		mesg<<"The current focus candidate is "<<dBestZPos;
		GetCoreCallback()->LogMessage(this, mesg.str().c_str(),false);
		mesg.str("");
		// End of logging messages
	}
	
	// Restore scope to the old settings
	ret = RestoreScope();
	if(ret != DEVICE_OK)
		return ret;

	return DEVICE_OK;

}

void SimpleAF::StartClock()
{
	timemeasurement_ = true;
	this->timestamp_ = clock();
}

long SimpleAF::GetElapsedTime()
{
	if(timemeasurement_ == false)
	{
		return 0;
	}
	long elapsedtime = clock() - timestamp_;
	timestamp_ = 0;
	timemeasurement_ = false;
	return elapsedtime;
}

int SimpleAF::InitScope()
{
	// Open the shutter
	shutter_.SetCore(GetCoreCallback());
	// Set the exposure
	exposure_.SetCore(GetCoreCallback());
	int ret = shutter_.OpenCoreShutter();
	if(ret != DEVICE_OK)
		return ret;
	ret = exposure_.SetExposureToAF(param_afexposure_);
	reporter_.SetCore(GetCoreCallback());
	reporter_.InitializeDebugStack(this);
	if(ret != DEVICE_OK)
		return ret;
	return DEVICE_OK;
}

int SimpleAF::RestoreScope()
{
	// Retore the core shutter
	int ret  = shutter_.RestoreCoreShutter();
	if(ret != DEVICE_OK)
		return ret;
	// Restore the core exposure
	ret = exposure_.RestoreExposure();
	if(ret != DEVICE_OK)
		return ret;

	return DEVICE_OK;
}

int SimpleAF::GetImageForAnalysis(ImgBuffer & buffer, bool stretch )
{
	// Do unsigned char related stuff
	unsigned char * pBuf = const_cast<unsigned char *>(buffer.GetPixels());
	if(buffer.Depth() == 1)
	{
		void * sourcepixel = malloc(buffer.Depth());
		char * imageBuf = const_cast<char *>(GetCoreCallback()->GetImage());
		for(unsigned long j = 0; j < buffer.Width()*buffer.Height() ; ++j )
		{
			if(memcpy(sourcepixel,(void *)(imageBuf + j),buffer.Depth()))
			{
				unsigned char val = *(static_cast<unsigned char *>(sourcepixel));
				*(pBuf + j) = val;
			}
		}		
		free(sourcepixel); sourcepixel = 0;
		totalimagessnapped_++;
		return DEVICE_OK;
	}
	// Do unsigned short related stuff here
	else
	if(buffer.Depth() == 2)
	{
		// Getting a handle to the pixels
		unsigned char * pBuf = const_cast<unsigned char *>(buffer.GetPixels());
		// In the case of U-Short we are in slightly tricky territiry
		// We need to create another array for stretching, that has to be stretched as unsigned short, before
		// we can cast it as a char, otherwise the bit shift operation will ensure that the dynamic range is 
		// pathetic. This also means that we need to clean up in this function itself

		// 1. Create a ushort Array here
		unsigned short * StretchedImage = new unsigned short [buffer.Width()*buffer.Height()];

		// 2. Create a ushort pointer that points to the camera's image buffer

		unsigned short * ImagePointer = static_cast<unsigned short *>(
											static_cast<void *>(
												const_cast<char*>(GetCoreCallback()->GetImage())));
		int buffersize = buffer.Width()*buffer.Height()*sizeof(unsigned short);

		memcpy(StretchedImage,ImagePointer,buffersize);

		// 3. Cast the stretched image back into u-char for processing	

		for(unsigned long i = 0; i < buffer.Width()*buffer.Height(); ++i)
		{
			*(pBuf + i) = 
				static_cast<unsigned char>(StretchedImage[i]>>((buffer.Depth() -sizeof(unsigned char))*8));
		}
	    
		// Delete the ushort array that you declared. 
		delete [] StretchedImage; StretchedImage = 0;
		// Store a native copy in the image handle		
		++totalimagessnapped_;
		return DEVICE_OK;
	}
	else
	{
		return DEVICE_UNSUPPORTED_DATA_FORMAT;
	}
	return DEVICE_OK;
}

double SimpleAF::GetScore(ImgBuffer & buffer)
{
	return scorer_.GetScore(buffer);
}
