///////////////////////////////////////////////////////////////////////////////
// FILE:          SpotCamera.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Diagnostic Spot Camera DeviceAdapter class
// COPYRIGHT:     University of California, San Francisco, 2009
//
// AUTHOR:        Karl Hoover, UCSF
//
// LICENSE:       This file is distributed under the BSD license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
// CVS:           
//

#ifdef WIN32
   #define WIN32_LEAN_AND_MEAN
   #include <windows.h>
   #define snprintf _snprintf 
#endif

#include <string>
#include <math.h>
#include <sstream>
#include <algorithm>


#include "SpotCamera.h"
#include "SpotDevice.h"

#include "../../MMDevice/ModuleInterface.h"
#include "CodeUtility.h"

bool requestShutdown;
using namespace std;
const char* g_DeviceName = "Spot";

//
typedef std::pair<std::string, std::string> DeviceInfo;
extern std::vector<DeviceInfo> g_availableDevices;

static bool bApiAvailable_s;


// ------------------------------ DLL main --------------------------------------
//
// windows DLL entry code
#ifdef WIN32
BOOL APIENTRY DllMain( HANDLE /*hModule*/, DWORD  ul_reason_for_call, LPVOID /*lpReserved*/ ) 
{
	switch (ul_reason_for_call)
	{
	case DLL_PROCESS_ATTACH:

	case DLL_THREAD_ATTACH:
		break;
	case DLL_THREAD_DETACH:
	case DLL_PROCESS_DETACH:
		requestShutdown = true;
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
	// could query driver and load up all cameras present on the system
   AddAvailableDeviceName(g_DeviceName, "SpotCam");
}

// the device name will contain the name specified by the spot driver
// so at the beginning of time we need to allow the 'device' to be created
// with an empty name, so that the pre-initialization properties can be filled in

MODULE_API MM::Device* CreateDevice(const char* szDeviceName)
{
	// true if the .dll wasn't found
	MM::Device* pdev = new SpotCamera(szDeviceName);

	return bApiAvailable_s ?pdev: NULL;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{

	delete pDevice;
	
}




///////////////////////////////////////////////////////////////////////////////
// SpotCamera device adapter
// ~~~~~~~~~~~~~~~~~~~~~~~~~~

//const double SpotCamera::fNominalPixelSizeUm = 1.0;

/**
 * SpotCamera constructor.
 * Setup default all variables and create device properties required to exist
 * before intialization. In this case, no such properties were required. All
 * properties will be created in the Initialize() method.
 *
 * As a general guideline Micro-Manager devices do not access hardware in the
 * the constructor. We should do as little as possible in the constructor and
 * perform most of the initialization in the Initialize() method.
 */
SpotCamera::SpotCamera(const char* /*szDeviceName*/) : CCameraBase<SpotCamera>(),
   initialized( false ),   
   numberOfChannels_( 1 ),
	pImplementation_(NULL),
	rawBuffer_(NULL),
	rawBufferSize_(0),
	stopOnOverflow_(true),
   interval_ms_ (0.),
	snapImageStartTime_(0.),
	previousFrameRateStartTime_(0.),
	startme_(false),
	stopRequest_(false)
	

{
 

	// create a pre-initialization property and list all the available cameras

	// Spot sends us the Model Name + (serial number)
   CPropertyAction *pAct = new CPropertyAction (this, &SpotCamera::OnCamera);
   CreateProperty("SpotCamera", "", MM::String, false, pAct, true);
	AddAllowedValue( "SpotCamera", ""); // no camera yet

	try
	{

		// remember device name and set the device index    
		//deviceName_ = std::string(szDeviceName);
	   
		pImplementation_ = new SpotDevice(this);
		pImplementation_->SelectedCameraIndex(-1);

		bApiAvailable_s = true;
		// 
		std::map<int, std::string> cams = pImplementation_->AvailableSpotCameras();
		std::map<int, std::string>::iterator ii;
		for( ii = cams.begin(); cams.end()!=ii; ++ii)
		{
			AddAllowedValue( "SpotCamera", ii->second.c_str());
		}
	}

	catch( SpotBad& ex)
	{
		// 
		delete pImplementation_;
		pImplementation_ = NULL;
		CodeUtility::DebugOutput( ex.ReasonText());

	}
	

   // call the base class method to set-up default error codes/messages
   InitializeDefaultErrorMessages();
}

/**
 * SpotCamera destructor.
 * If this device used as intended within the Micro-Manager system,
 * Shutdown() will be always called before the destructor. But in any case
 * we need to make sure that all resources are properly released even if
 * Shutdown() was not called.
 */
SpotCamera::~SpotCamera()
{

	if ( this->initialized ) this->Shutdown();  
	delete pImplementation_;
	pImplementation_ = NULL;

}

/**
 * Obtains device name.
 * Required by the MM::Device API.
 */
void SpotCamera::GetName(char* name) const
{
   // We just return the name we use for referring to this
   // device adapter. 
   CDeviceUtils::CopyLimitedString(name, (const char*) deviceName_.c_str());
}

/**
 * Tells us if device is still processing asynchronous command.
 * Required by the MM:Device API.
 */
bool SpotCamera::Busy()
{
  return CCameraBase<SpotCamera>::Busy();
}

/**
 * Intializes the hardware.
 * Required by the MM::Device API.
 * Typically we access and initialize hardware at this point.
 * Device properties are typically created here as well, except
 * the ones we need to use for defining initialization parameters.
 * Such pre-initialization properties are created in the constructor.
 * (This device does not have any pre-initialization properties)
 */
int SpotCamera::Initialize()
{
	int nRet = DEVICE_OK;
	try
	{
		CodeUtility::DebugOutput("spot camera Initialize ");
		CodeUtility::DebugOutput(deviceName_);
		
		if (NULL == pImplementation_)
			return DEVICE_ERR;
		pImplementation_->Initialize(deviceName_);

		// read camera information

		// set camera name - SPOT cameras have mode & serial number together
		// set device name
		nRet = CreateProperty(MM::g_Keyword_Name, (char*) deviceName_.c_str(), MM::String, true);
		if ( nRet != DEVICE_OK ) return nRet;

		// set device description
		nRet = CreateProperty(MM::g_Keyword_Description, "Spot Cameras", MM::String, true);
		if ( nRet != DEVICE_OK ) return nRet;

		// setup properties
		CPropertyAction *pAct;
		pAct = new CPropertyAction (this, &SpotCamera::OnPixelSize); // function called if property is read or written
		CreateProperty("PixelSize", "0x0 nm", MM::String, true, pAct ); // create property

		pAct = new CPropertyAction (this, &SpotCamera::OnExposure); // function called if exposure time property is read or written
		// create exposure property 
		nRet = CreateProperty(MM::g_Keyword_Exposure, CDeviceUtils::ConvertToString(exposureTime_), MM::Float, false, pAct ); // create property
		if ( nRet != DEVICE_OK ) return nRet;

		double minExposureTime; // min. allowed exposure time
		double maxExposureTime; // max. allowed exposure time
		
		pImplementation_->ExposureLimits( minExposureTime, maxExposureTime);
      if( maxExposureTime < 0.)
      {
         std::ostringstream oStringStream;
         oStringStream <<"Spot API returned invalid maxExposureTime: " << maxExposureTime << " this has been changed to 100000.";
         LogMessage(oStringStream.str().c_str(), false);
         maxExposureTime = 100000.;

      }
		SetPropertyLimits( MM::g_Keyword_Exposure, minExposureTime, maxExposureTime ); // set limits 

		// create "actual" gain property 
		pAct = new CPropertyAction (this, &SpotCamera::OnActualGain); // function called if property is read or written
		nRet = CreateProperty("ActualGain", "0.", MM::Float, true, pAct ); // create property
	


		if (pImplementation_->CanComputeExposure())
		{
			// create auto exposure property 
			pAct = new CPropertyAction (this, &SpotCamera::OnAutoExposure); // handle AutoExposure mode setting
			CreateProperty("AutoExposure", "OFF", MM::String, false, pAct ); // create property
			AddAllowedValue( "AutoExposure", "OFF");
			AddAllowedValue( "AutoExposure", "ON");
	
			pAct = new CPropertyAction (this, &SpotCamera::OnAutoExpImageType); // handle AutoExposure mode setting
			CreateProperty("AutoExpImageType", "BRIGHTFIELD", MM::String, false, pAct ); // create property
			AddAllowedValue( "AutoExpImageType", "BRIGHTFIELD");
			AddAllowedValue( "AutoExpImageType", "DARKFIELD");
	
			SetProperty("AutoExpImageType", "BRIGHTFIELD");
			// turn autoexposure off
			SetProperty("AutoExposure", "OFF");

		}

		if(pImplementation_->DoesMultiShotColor())
		{
			std::string colors = pImplementation_->ColorOrder();
			CPropertyActionEx* pAct;
			std::string::iterator iit = colors.begin();
			int iii;
			std::ostringstream pname;
			for( iit = colors.begin(), iii = 0; colors.end() != iit; ++iit, ++iii)
			{
				pname.str("");
				pname << "ExposureTime for " << colors[iii];
				CodeUtility::DebugOutput (pname.str());

				pAct = new CPropertyActionEx(this, &SpotCamera::OnMultiShotExposure, iii);
				CreateProperty(pname.str().c_str(), "0", MM::Float, false, pAct);
				SetPropertyLimits(pname.str().c_str(), minExposureTime, maxExposureTime ); // set limits 

			}
		}

		// TRIGGER MODE PROPERTY

		if( pImplementation_->CanDoEdgeTrigger()  || pImplementation_->CanDoBulbTrigger())
		{
			pAct = new CPropertyAction (this, &SpotCamera::OnTriggerMode); // handler for mode setting
			CreateProperty("TriggerMode", "NONE", MM::String, false, pAct ); // create property
			AddAllowedValue( "TriggerMode", "NONE");
			if(pImplementation_->CanDoEdgeTrigger()) AddAllowedValue( "TriggerMode", "EDGE");
			if(pImplementation_->CanDoBulbTrigger()) AddAllowedValue( "TriggerMode", "BULB");

			SetProperty("TriggerMode", "NONE");
		}
	
		//CHIP DEFECT CORRECTION PROPERTY

		pAct = new CPropertyAction (this, &SpotCamera::OnChipDefectCorrection); // handler for mode setting
		CreateProperty("ChipDefectCorrection", "NONE", MM::String, false, pAct ); // create property
		AddAllowedValue("ChipDefectCorrection", "ON");
		AddAllowedValue("ChipDefectCorrection", "OFF");
		SetProperty("ChipDefectCorrection", "ON");


		//
		// chip clearing mode property
		pAct = new CPropertyAction (this, &SpotCamera::OnClearingMode); // handler for mode setting
		CreateProperty("ClearingMode", "NONE", MM::String, false, pAct ); // create property

		std::vector<ClearingModes> cmodez = pImplementation_->PossibleClearingModes();
		//enum ClearingModes { Continuous = 1, Preemptable =3 } //Preemptable = 2, Never = 4};
		for( std::vector<ClearingModes>::iterator czi = cmodez.begin(); cmodez.end() != czi; ++czi)
		{
			if( *czi == ClearingModesNS::Continuous)
				AddAllowedValue("ClearingMode", "CONTINUOUS" );
			if( *czi == ClearingModesNS::Preemptable)
				AddAllowedValue("ClearingMode", "PREEMPTABLE" );
			if( *czi == ClearingModesNS::Never)
				AddAllowedValue("ClearingMode", "NEVER" );
		}

		// GET THE CURRENT SETTING FOR THE CLEARING MODE
		ClearingModes cmode = pImplementation_->ClearingMode();
		std::string cvalue;

		if( cmode == ClearingModesNS::Continuous)
				cvalue = "CONTINUOUS";
		else if(cmode == ClearingModesNS::Preemptable)
				cvalue =  "PREEMPTABLE";
		else if(cmode == ClearingModesNS::Never)
				cvalue ="NEVER";
		SetProperty( "ClearingMode", cvalue.c_str());

		// the noise filter percent property
		pAct = new CPropertyAction (this, &SpotCamera::OnNoiseFilterPercent); // handler for mode setting
		CreateProperty   ("NoiseFilterThreshold%", "0", MM::Integer, false, pAct ); // create property
		SetPropertyLimits("NoiseFilterThreshold%", 0., 100.);

		SetProperty("NoiseFilter%", "0");
		
		// create bit depth property 
		pAct = new CPropertyAction (this, &SpotCamera::OnBitDepth); // function called if exposure time property is read or written
		nRet = CreateProperty("BitDepth", (pImplementation_->CanDoColor()?"24":"8"), MM::Integer, false, pAct ); // create property



		std::vector<short> bitdepths = pImplementation_->PossibleBitDepths();
		std::vector<std::string> depthStrings;

		for( std::vector<short>::iterator ii = bitdepths.begin(); bitdepths.end() != ii; ++ii)
		{
			ostringstream s;
			s << *ii;
			depthStrings.push_back(s.str());
		}
		SetAllowedValues("BitDepth", depthStrings);

		// binsize property
		std::vector<short> binsizes = pImplementation_->BinSizes();
		// create vector of allowed values
		std::vector<string> binValues;

		for( std::vector<short>::iterator ibs = binsizes.begin(); binsizes.end() != ibs; ibs++)
		{
			ostringstream s;
			s << *ibs;
			binValues.push_back(s.str());
		}

		pAct = new CPropertyAction (this, &SpotCamera::OnBinning); // function called if binning property is read or written
		nRet = CreateProperty(MM::g_Keyword_Binning, "1", MM::Integer, false, pAct); // create binning property
		if ( nRet != DEVICE_OK ) return nRet;

		SetAllowedValues(MM::g_Keyword_Binning, binValues); // set allowed values for binning property

		// setup gain property

		//float gain = 1.0f;
		pAct = new CPropertyAction (this, &SpotCamera::OnGain);   // function called if gain is read or written
		nRet = CreateProperty(MM::g_Keyword_Gain, "1", MM::String, false, pAct );

		// retrieve the possible integer gain settings
		// todo -- the list of allowed values needs to refresh when bitdepth is changed.
		std::vector<short> vals = pImplementation_->PossibleIntegerGains(pImplementation_->BitDepthPerChannel());
		std::vector<std::string> svals;
		std::vector<short>::iterator iii;

		for( iii = vals.begin(); vals.end() != iii; ++iii)
		{
			std::ostringstream ss;
			ss << *iii;
			svals.push_back(ss.str());
		}
		SetAllowedValues(MM::g_Keyword_Gain, svals);

		// optionally create sensor temperature property
		if (pImplementation_-> CanReadSensorTemperature())
		{
			pAct = new CPropertyAction (this, &SpotCamera::OnTemperature);   // function called if temperature is read or written
			CreateProperty(MM::g_Keyword_CCDTemperature, "0.0", MM::Float, true, pAct);

			// optionally create Temperature setpoint property
			if( pImplementation_->CanRegulateSensorTemperature())
			{
				pAct = new CPropertyAction (this, &SpotCamera::OnTemperatureSetpoint);   // function called if temperature setpoint is read or written
				CreateProperty("TemperatureSetpoint", "0.0", MM::Float, true, pAct);

				// get the upper and lower limits, degrees C
				std::pair<double, double> bounds = pImplementation_->RegulatedTemperatureLimits();
				SetPropertyLimits("TemperatureSetpoint", bounds.first, bounds.second);




			}
		}

		// synchronize all properties
		// --------------------------
		nRet = UpdateStatus();
		if (nRet != DEVICE_OK) return nRet;

		// setup the image buffer
		// ----------------




		nRet = this->ResizeImageBuffer();
	//	if (nRet != DEVICE_OK) return nRet;

	
		SetProperty(MM::g_Keyword_Exposure, "50."); // 50 milliseconds
		SetProperty(MM::g_Keyword_Gain, "1");


		this->initialized = true;

	}
	catch( SpotBad& ex)
	{

		nRet = DEVICE_LOCALLY_DEFINED_ERROR;
		std::ostringstream  messs;
		messs << " Spot Camera Initialization fails: " << ex.ReasonText() << std::endl;
		LogMessage(messs.str().c_str());
		SetErrorText(DEVICE_LOCALLY_DEFINED_ERROR,messs.str().c_str());
		CodeUtility::DebugOutput(messs.str().c_str());
	}

  return nRet;

}




/**
 * Shuts down (unloads) the device.
 * Required by the MM::Device API.
 * Ideally this method will completely unload the device and release all resources.
 * Shutdown() may be called multiple times in a row.
 * After Shutdown() we should be allowed to call Initialize() again to load the device
 * without causing problems.
 */
int SpotCamera::Shutdown()
{
  StopSequenceAcquisition();

  pImplementation_->ShutdownCamera();

  //
  this->initialized = false;
  
   
  return DEVICE_OK;
}



/**
 * Performs exposure and grabs a single image.
 * Required by the MM::Camera API.
 */
int SpotCamera::SnapImage()
{

	// record the beginning of this image acquisition
	SnapImageStartTime(this->GetCurrentMMTime().getMsec());

	// calculate the framerate for second and subsequent frame
	if( 0. < previousFrameRateStartTime_)
	{
		double el = SnapImageStartTime() - previousFrameRateStartTime_;
		if ( 0. != el)
		{
			double framesPerSecond = 1000./el;
			ostringstream o;
			o << " current acq rate " << framesPerSecond << " frames per sec. \n";
			CodeUtility::DebugOutput( o.str());
		}
	}
	previousFrameRateStartTime_  = SnapImageStartTime();

	int nRet = DEVICE_OK;
	try
	{
		AutoEposureCalculationDone(false);
		ExposureComplete(false);
		pImplementation_->SetupImageSequence( 1);
		WaitForExposureComplete();
	}
	catch(SpotBad& ex)
	{
		nRet = DEVICE_LOCALLY_DEFINED_ERROR;
		std::ostringstream  messs;
		messs << " Spot Camera Snap fails: " << ex.ReasonText() << std::endl;
		LogMessage(messs.str().c_str());
		SetErrorText(DEVICE_LOCALLY_DEFINED_ERROR,messs.str().c_str());
		CodeUtility::DebugOutput(messs.str().c_str());
	}

	double snapImageElapsedTime = this->GetCurrentMMTime().getMsec() - SnapImageStartTime();
	std::ostringstream x;
	x << "snap image time, SetupSequence -> Exposure Complete " << snapImageElapsedTime << std::endl;

	CodeUtility::DebugOutput(x.str());


	return nRet;
}

/**
 * Returns pixel data.
 * Required by the MM::Camera API.
 * The calling program will assume the size of the buffer based on the values
 * obtained from GetImageBufferSize(), which in turn should be consistent with
 * values returned by GetImageWidth(), GetImageHight() and GetImageBytesPerPixel().
 * The calling program allso assumes that camera never changes the size of
 * the pixel buffer on its own. In other words, the buffer can change only if
 * appropriate properties are set (such as binning, pixel type, etc.)
 */
const unsigned char* SpotCamera::GetImageBuffer()
{     

	unsigned long singleChannelSize =0 ;
	//CodeUtility::DebugOutput("entering getimagebuffer ...\n");

	try
	{

		NextSequentialImage(img_[0] );
		

	  //  while (GetCurrentMMTime() - readoutStartTime_ < MM::MMTime(readoutUs_)) {CDeviceUtils::SleepMs(5);}
		singleChannelSize = img_[0].Width() * img_[0].Height() * img_[0].Depth();

		memcpy(rawBuffer_, img_[0].GetPixels(), singleChannelSize);
	}
	
	catch(SpotBad& ex)
	{

		std::ostringstream  messs;
		messs << " Spot Camera GetImageBuffer fails: " << ex.ReasonText() << std::endl;
		LogMessage(messs.str().c_str());
		CodeUtility::DebugOutput(messs.str().c_str());

		// a mechanism to throw an error here would be nice
	}

	//double elapsedMilli = this->GetCurrentMMTime().getMsec() - snapImageStartTime_ - pImplementation_->ExposureTime();


	//double mebibytesPerSecond = singleChannelSize*MEBIBYTESINVERSE/(elapsedMilli/1000.);
	//double megabytesPerSecond = singleChannelSize*0.000001/(elapsedMilli/1000.);

	//std::stringstream ostring;

	//ostring << " throughput: " << mebibytesPerSecond << " mebibytes per sec. " << ", i.e. " << megabytesPerSecond << " mega bytes / sec. \n";

	//CodeUtility::DebugOutput(ostring.str());
	

	if( pImplementation_->AutoExposure())
	{
		SetExposure(pImplementation_->ExposureTime());
		SetProperty(MM::g_Keyword_Gain, CDeviceUtils::ConvertToString(pImplementation_->Gain()));

	}
	//CodeUtility::DebugOutput("exiting getimagebuffer ...\n");


   return rawBuffer_;
}




int SpotCamera::ResizeImageBuffer(int imageSizeW, int imageSizeH, int byteDepth, int binSize /*=1*/)
{

	//todo - simplify this to use ONE buffer instead of three.

   img_[0].Resize(imageSizeW/binSize, imageSizeH/binSize, byteDepth);
	imageBuffer.Resize(imageSizeW/binSize, imageSizeH/binSize, byteDepth);
   delete[] rawBuffer_;
   rawBuffer_ = new unsigned char[img_[0].Width() * img_[0].Height() * img_[0].Depth()];
	rawBufferSize_ = img_[0].Width() * img_[0].Height() * img_[0].Depth();
   return DEVICE_OK;
}

// this converts the device image into a MM image, for example,
// color images have a 4 channel interleaved into them

int SpotCamera::NextSequentialImage(ImgBuffer& img)
{
	int nRet = DEVICE_OK;

	unsigned int  bytesPerPixel; // e.g. 4 for RGB
	// values from the device
	unsigned int sourceheight, sourcewidth, sourcedepth;
	char cdepth;
	char *pData;
	pData = 	pImplementation_->GetNextSequentialImage( sourceheight, sourcewidth, cdepth  );


	sourcedepth = (int)cdepth;
	if (3 == sourcedepth)
	{
		bytesPerPixel = 4;
	}
	else
	{
		bytesPerPixel = (int)sourcedepth;
	}

	const unsigned long bytesRequired = sourceheight*sourcewidth*bytesPerPixel;


	if( (rawBufferSize_ < bytesRequired ) || (sourceheight != (int)img.Height()) || (sourcewidth != (int)img.Width()) || (bytesPerPixel !=img.Depth()) )
	{
		this->ResizeImageBuffer( sourcewidth, sourceheight, bytesPerPixel);
	}


	unsigned int destdepth = img.Depth();
	unsigned int destwidth = img.Width();
	unsigned int destheight = img.Height();

	//memset(ptemp, 0, destdepth*destwidth*destheight);

	// handle case where buffer doesn't match returned image size
	unsigned int xdest, ydest;//, xsource, ysource;
	int roffsetdest, goffsetdest, boffsetdest;
	int roffsetsource, goffsetsource, boffsetsource;

	unsigned int workingwidth = min(destwidth, sourcewidth);
	unsigned int workingheight = min(destheight, sourceheight);

	unsigned char* ptemp = img.GetPixelsRW();

	memset(ptemp,0, destdepth*destwidth*destheight);
#ifndef WIN32 // __APPLE__
	memcpy( ptemp, pData, destdepth*destwidth*destheight);
#else


#if 1 // no right left swap needed
	for( ydest = 0; ydest < workingheight; ++ydest)
	{
		for( xdest = 0; xdest < workingwidth; ++xdest)
		{
			roffsetdest = xdest*destdepth + destdepth*ydest*destwidth;
			goffsetdest = roffsetdest + 1;
			boffsetdest = goffsetdest + 1;

			roffsetsource = xdest*sourcedepth + sourcedepth*ydest*sourcewidth;
			goffsetsource = roffsetsource + 1;
			boffsetsource = goffsetsource + 1;
			ptemp[roffsetdest] = pData[roffsetsource];
			if( 1 < destdepth)
			{
			ptemp[goffsetdest] = pData[goffsetsource];			
			ptemp[boffsetdest] = pData[boffsetsource];
			}
		}
	}
#endif


#if 0

	for( ydest = 0; ydest < workingheight; ++ydest)
	{
		for( int xsource  = 0; xsource < workingwidth; ++xsource)
		{
			xdest = workingwidth - 1 - xsource;
			roffsetdest = xdest*destdepth + destdepth*ydest*destwidth;
			goffsetdest = roffsetdest + 1;
			boffsetdest = goffsetdest + 1;

			roffsetsource = xsource*sourcedepth + sourcedepth*ydest*sourcewidth;
			goffsetsource = roffsetsource + 1;
			boffsetsource = goffsetsource + 1;
			ptemp[roffsetdest] = pData[roffsetsource];
			if( 1 < destdepth)
			{
				ptemp[goffsetdest] = pData[goffsetsource];			
				ptemp[boffsetdest] = pData[boffsetsource];
			}
		}
	}
#endif
	//img.SetPixels(ptemp);
#endif 

	return nRet;

}


// used for color images
const unsigned int* SpotCamera::GetImageBufferAsRGB32()
{
    return (const unsigned int*)GetImageBuffer();    
}

unsigned int SpotCamera::GetNumberOfComponents() const
{
  return this->numberOfChannels_;  
}


int SpotCamera::GetComponentName(unsigned channel, char* name)
{
	bool bColor = (pImplementation_->CanDoColor() && (1 == pImplementation_->BinSize()));
  if (!bColor && (channel > 0))  return DEVICE_NONEXISTENT_CHANNEL;      
  
  switch (channel)
  {
  case 0:      
    if (!bColor) 
      CDeviceUtils::CopyLimitedString(name, "Grayscale");
    else 
      CDeviceUtils::CopyLimitedString(name, "B");
    break;

  case 1:
    CDeviceUtils::CopyLimitedString(name, "G");
    break;

  case 2:
    CDeviceUtils::CopyLimitedString(name, "R");
    break;

  default:
    return DEVICE_NONEXISTENT_CHANNEL;
    break;
  }
  return DEVICE_OK;
}
 

/**
 * Returns image buffer X-size in pixels.
 * Required by the MM::Camera API.
 */
unsigned SpotCamera::GetImageWidth() const
{

   return imageBuffer.Width();
}

/**
 * Returns image buffer Y-size in pixels.
 * Required by the MM::Camera API.
 */
unsigned SpotCamera::GetImageHeight() const
{

   return imageBuffer.Height();
}

/**
 * Returns image buffer pixel depth in bytes.
 * Required by the MM::Camera API.
 */
unsigned SpotCamera::GetImageBytesPerPixel() const
{

   return imageBuffer.Depth() / GetNumberOfComponents();
} 

/**
 * Returns the bit depth (dynamic range) of the pixel.
 * This does not affect the buffer size, it just gives the client application
 * a guideline on how to interpret pixel values.
 * Required by the MM::Camera API.
 */
unsigned SpotCamera::GetBitDepth() const
{

  // read current pixel type from camera
  unsigned int bitDepth  = (unsigned int) pImplementation_->BitDepth();
  if( 16 < bitDepth)
	  bitDepth /= 3;
  return bitDepth;
}

/**
 * Returns the size in bytes of the image buffer.
 * Required by the MM::Camera API.
 */
long SpotCamera::GetImageBufferSize() const
{
   return GetImageWidth() * GetImageHeight() * GetImageBytesPerPixel() * GetNumberOfComponents();
}

/**
 * Sets the camera Region Of Interest.
 * Required by the MM::Camera API.
 * This command will change the dimensions of the image.
 * Depending on the hardware capabilities the camera may not be able to configure the
 * exact dimensions requested - but should try do as close as possible.
 * If the hardware does not have this capability the software should simulate the ROI by
 * appropriately cropping each frame.
 * @param x - top-left corner coordinate
 * @param y - top-left corner coordinate
 * @param xSize - width
 * @param ySize - height
 */
int SpotCamera::SetROI( unsigned x, unsigned y, unsigned xSize, unsigned ySize )
{ 
	int nRet = DEVICE_OK;
	std::ostringstream messs;
	//if(IsCapturing())
	//	return ERR_BUSY_ACQIRING;
	try
	{
		unsigned old_x, old_y, junk;
		// requested ROI position is coordinates of the current ROI
		pImplementation_->GetROI(old_x, old_y, junk, junk);

		int bz = pImplementation_->BinSize();
		if( bz < 1)
		{
			nRet = DEVICE_LOCALLY_DEFINED_ERROR;
			messs << "Spot Camera illegal bin size: " << bz;
		}
		else
		{
			// implementation works with sensor pixels,
			// UI specfies ROI in display pixels, so convert
			x *= bz;	y *= bz;	xSize *= bz; ySize *= bz;		// set the ROI

			pImplementation_->SetROI((const unsigned int)(old_x+x), (const unsigned int)(old_y+y), (const unsigned int)xSize, (const unsigned int)ySize);
			
			// resize image buffer
			nRet = this->ResizeImageBuffer();
		}

	}

	catch( SpotBad& exx)
	{
		nRet = DEVICE_LOCALLY_DEFINED_ERROR;
		messs << " Spot Camera SetROI fails: " << exx.ReasonText() << std::endl;

	}

	if ( DEVICE_OK != nRet)
	{
		LogMessage(messs.str().c_str());
		SetErrorText(DEVICE_LOCALLY_DEFINED_ERROR,messs.str().c_str());
		CodeUtility::DebugOutput(messs.str().c_str());
	}

	return nRet;

}

/**
 * Returns the actual dimensions of the current ROI.
 * Required by the MM::Camera API.

   actual dimensions:  it scales the ROI from the camera by 1/binsize
 */
int SpotCamera::GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize)
{
	int nRet = DEVICE_OK;
	std::ostringstream messs;
	
	try
	{
		int bz = pImplementation_->BinSize();
		if( bz < 1)
		{
			nRet = DEVICE_LOCALLY_DEFINED_ERROR;
			messs << "Spot Camera illegal bin size: " << bz;
		}
		else
		{
			// get the ROI
			pImplementation_->GetROI( x, y, xSize, ySize, false);
			x/=bz; y/=bz; xSize/=bz; ySize/=bz;
		}

	}
	catch( SpotBad& exx)
	{
		nRet = DEVICE_LOCALLY_DEFINED_ERROR;
		messs << " Spot Camera GetROI fails: " << exx.ReasonText() << std::endl;

	}

	if ( DEVICE_OK != nRet)
	{
		LogMessage(messs.str().c_str());
		SetErrorText(DEVICE_LOCALLY_DEFINED_ERROR,messs.str().c_str());
		CodeUtility::DebugOutput(messs.str().c_str());
	}

  return nRet;
}

/**
 * Resets the Region of Interest to full frame.
 * Required by the MM::Camera API.
 */
int SpotCamera::ClearROI()
{
	int nRet;

	unsigned int x, y, xSize, ySize;
	try
	{
		// get the full sensor size
		pImplementation_->GetROI( x, y, xSize, ySize, true);
		// this that as the ROI
		pImplementation_->SetROI( x, y, xSize, ySize);
		nRet = ResizeImageBuffer();
	}
	catch( SpotBad& ex)
	{
		nRet = DEVICE_LOCALLY_DEFINED_ERROR;
		std::ostringstream  messs;
		messs << " Spot Camera ClearROI fails: " << ex.ReasonText() << std::endl;
		LogMessage(messs.str().c_str());
		SetErrorText(DEVICE_LOCALLY_DEFINED_ERROR,messs.str().c_str());
		CodeUtility::DebugOutput(messs.str().c_str());
	}
	return nRet;


}

/**
 * Returns the current exposure setting in milliseconds.
 * Required by the MM::Camera API.
 */
double SpotCamera::GetExposure() const
{
   char Buf[MM::MaxStrLength];
   Buf[0] = '\0';
   GetProperty(MM::g_Keyword_Exposure, Buf);
   return atof(Buf);
}
void SpotCamera::SetExposure(double dExp)
{
   SetProperty(MM::g_Keyword_Exposure, CDeviceUtils::ConvertToString(dExp));
}

// handle exposure property
int SpotCamera::OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int retValue = DEVICE_OK;  
	//	MMThreadGuard cgard(this->cameraMMLock_);
	double exposure;

	try
	{

		if (eAct == MM::AfterSet) // property was written -> apply value to hardware
		{
			//if (!IsCapturing())
			//{
				pProp->Get(exposure);
				CodeUtility::DebugOutput("changing camera exposure to ");
				std::ostringstream o;
				o<<exposure;
				CodeUtility::DebugOutput(o.str().c_str());
				pImplementation_->ExposureTime(exposure);
				CodeUtility::DebugOutput(" ...changed camera exposure!\n");
			//}

	#if 0
			double quantizedExposure = pImplementation_->ExposureTime();

			if( 0. < quantizedExposure)
			{
			  double fractionDelta = fabs(quantizedExposure - exposure)/quantizedExposure;

			  if ( (0.005 < fractionDelta ) && (fractionDelta < 0.10)  )
			  {
				  pProp->Set(quantizedExposure);
			  }
			}
	#endif
		 
		}
		else if (eAct == MM::BeforeGet) // property will be read -> update property with value from hardware
		{  

		//	bool wasAcq = IsCapturing();

		//	if(! wasAcq)
		//	{
				CodeUtility::DebugOutput("getting exposure... ");
				exposure = pImplementation_->ExposureTime();
				exposureTime_ = exposure;
				CodeUtility::DebugOutput("got exposure...\n");
		//	}
		//	else
		//	{
		//		exposure = exposureTime_;
		//	}

			// write hardware value to property
			pProp->Set( exposure );


		}
	}
	catch( SpotBad& ex)
	{
		retValue = DEVICE_LOCALLY_DEFINED_ERROR;
		std::ostringstream  messs;
		messs << " error in OnExposure " << ex.ReasonText() << std::endl;
		LogMessage(messs.str().c_str());
		SetErrorText(DEVICE_LOCALLY_DEFINED_ERROR,messs.str().c_str());
		CodeUtility::DebugOutput(messs.str().c_str());
	}

	return retValue;

}


int SpotCamera::OnActualGain(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int retValue = DEVICE_OK;

	if (eAct == MM::AfterSet) 
	{
	  // never do anything for a readback

	}
	else if (eAct == MM::BeforeGet) // property will be read -> update property with value from hardware
	{
		try
		{
			//if(IsCapturing()) return DEVICE_OK;
			CodeUtility::DebugOutput("getting actual gain\n");
			double again = pImplementation_->ActualGain();
			pProp->Set( again );
		}
		catch( SpotBad& ex)
		{
			retValue = DEVICE_LOCALLY_DEFINED_ERROR;
			std::ostringstream  messs;
			messs << " error in OnActualGain " << ex.ReasonText() << std::endl;
			LogMessage(messs.str().c_str());
			SetErrorText(DEVICE_LOCALLY_DEFINED_ERROR,messs.str().c_str());
			CodeUtility::DebugOutput(messs.str().c_str());
		}
	}
	return retValue;
}


int SpotCamera::OnPixelSize(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::AfterSet) 
	{
	  // never do anything for an immutable attribute...

	}
	else if (eAct == MM::BeforeGet) // property will be read -> update property with value from hardware
	{
		CodeUtility::DebugOutput("getting pixel size\n");
		pProp->Set( pImplementation_->PixelSize().c_str() );
	}
	return DEVICE_OK;
}


int SpotCamera::OnTemperature(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::AfterSet) 
	{
	  // never do anything for a readback

	}
	else if (eAct == MM::BeforeGet) // property will be read -> update property with value from hardware
	{
		CodeUtility::DebugOutput("getting sensor temp\n");
		pProp->Set( pImplementation_->SensorTemperature() );

	}

  return DEVICE_OK;
}

// handles CCD sensor temperature regulation setpoin
int SpotCamera::OnTemperatureSetpoint(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	double setpoint;
	std::string value;
		//	if(IsCapturing()) return DEVICE_OK;
	if (eAct == MM::AfterSet) // property was written -> apply value to hardware
	{
		pProp->Get(value);
		pImplementation_->SensorTemperatureSetpoint( atof(value.c_str()));

	}
	else if (eAct == MM::BeforeGet) // property will be read -> update property with value from hardware
	{  
		CodeUtility::DebugOutput("getting sensor temp setpoint\n");
		setpoint = pImplementation_->SensorTemperatureSetpoint();
		std::ostringstream ost;
		ost << setpoint;
		// are "ON" and "OFF" validated to be allowed values of the property??
		pProp->Set( ost.str().c_str()); // write back to GUI
	}


  return DEVICE_OK; 
}



// handles gain property
int SpotCamera::OnGain(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	long gain = 1;
	
	int retValue = DEVICE_OK;
	
	try
	{
		if (eAct == MM::AfterSet) // property was written -> apply value to hardware
		{
		//	if(IsCapturing())
			//	return DEVICE_CAMERA_BUSY_ACQUIRING;

			pProp->Get(gain);
			 //set the value to the camera
			pImplementation_->Gain(static_cast<short>(gain));

			 //the 'actual gain' will changed, so propagate...
			OnPropertiesChanged();

		}
		else if (eAct == MM::BeforeGet) // property will be read -> update property with value from hardware
		{    
			CodeUtility::DebugOutput("getting gain\n");
			// get the value from the camera
			gain = pImplementation_->Gain();
			pProp->Set( gain ); // write back to GUI
		}
	}
	catch(SpotBad& ex)
	{
		retValue = DEVICE_LOCALLY_DEFINED_ERROR;
		std::ostringstream  messs;
		messs << " error in OnGain " << ex.ReasonText() << std::endl;
		LogMessage(messs.str().c_str());
		SetErrorText(DEVICE_LOCALLY_DEFINED_ERROR,messs.str().c_str());
		CodeUtility::DebugOutput(messs.str().c_str());
	}
	

  return retValue; 
}


int SpotCamera::OnMultiShotExposure(MM::PropertyBase* pProp, MM::ActionType eAct, long componentIndex)
{
	int retValue = DEVICE_OK;
	double exp;

	//if(IsCapturing()) return DEVICE_OK;
	try
	{

		if (eAct == MM::AfterSet) // property was written -> apply value to hardware
		{

			pProp->Get(exp);
			 //set the value to the camera
			pImplementation_->ExposureTimeForMultiShot(exp, componentIndex);
			CodeUtility::DebugOutput("calling OnPropertiesChanged\n");
			 //the 'actual gain' will changed, so propagate...
			OnPropertiesChanged();
			CodeUtility::DebugOutput("called OnPropertiesChanged\n");


		}
		else if (eAct == MM::BeforeGet) // property will be read -> update property with value from hardware
		{    
			CodeUtility::DebugOutput("getting MultiShotExposure\n");
			// get the value from the camera
			exp = pImplementation_->ExposureTimeForMultiShot(componentIndex);
			pProp->Set( exp ); // write back to GUI
			CodeUtility::DebugOutput("got MultiShotExposure\n");

		}
	}
	catch(SpotBad& ex)
	{
		retValue = DEVICE_LOCALLY_DEFINED_ERROR;
		std::ostringstream  messs;
		messs << " error in OnGain " << ex.ReasonText() << std::endl;
		LogMessage(messs.str().c_str());
		SetErrorText(DEVICE_LOCALLY_DEFINED_ERROR,messs.str().c_str());
		CodeUtility::DebugOutput(messs.str().c_str());
	}

  return retValue; 
}



int SpotCamera::OnBitDepth(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	long depth;
	//		if(IsCapturing()) return DEVICE_OK;
	if (eAct == MM::AfterSet) // property was written -> apply value to hardware
	{
		pProp->Get(depth);
		// set the value to the camera
		pImplementation_->BitDepth(static_cast<short>(depth));
		ResizeImageBuffer();

	}
	else if (eAct == MM::BeforeGet) // property will be read -> update property with value from hardware
	{ 

		CodeUtility::DebugOutput("getting bit depth... ");
			//	if (IsCapturing())return DEVICE_OK;
		// get the value from the camera
		depth = pImplementation_->BitDepth();
      pProp->Set( depth ); // write back to GUI
		
		CodeUtility::DebugOutput(" ...got bit depth \n ");
	}
	return DEVICE_OK;
}


int SpotCamera::OnAutoExpImageType(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	//if(IsCapturing()) return DEVICE_OK;

	ExposureComputationImageType itype = ExposureComputationImageType(0); //{ BrightField = 1, DarkField = 2 };
	std::string value;

		if (eAct == MM::AfterSet) // property was written -> apply value to hardware
		{
			pProp->Get(value);
			if( value == "BRIGHTFIELD")
				itype = ExposureComputationImageTypeNS::BrightField;
			else if( value == "DARKFIELD")
				itype = ExposureComputationImageTypeNS::DarkField;
			pImplementation_->ExposureComputationImageSetting( itype);
		}
		else if (eAct == MM::BeforeGet) // property will be read -> update property with value from hardware
		{         


			CodeUtility::DebugOutput("getting auto exp image type\n");
			itype = pImplementation_->ExposureComputationImageSetting();

			if( ExposureComputationImageTypeNS::BrightField == itype)
				value = "BRIGHTFIELD";
			else if( ExposureComputationImageTypeNS::DarkField == itype)
				value = "DARKFIELD";
			// are "ON" and "OFF" validated to be allowed values of the property??
			pProp->Set( value.c_str()); // write back to GUI
		}

  return DEVICE_OK; 
}

// handles autoexposure property
int SpotCamera::OnAutoExposure(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	bool autoexp;
	std::string value;
	//		if(IsCapturing()) return DEVICE_OK;

	if (eAct == MM::AfterSet) // property was written -> apply value to hardware
	{
		pProp->Get(value);
		pImplementation_->AutoExposure(CodeUtility::StringToBool(value));

	}
	else if (eAct == MM::BeforeGet) // property will be read -> update property with value from hardware
	{  

		CodeUtility::DebugOutput("getting auto exp\n");

		autoexp = pImplementation_->AutoExposure();
		// are "ON" and "OFF" validated to be allowed values of the property??
		pProp->Set( autoexp?"ON":"OFF"); // write back to GUI		
		CodeUtility::DebugOutput("got auto exp\n");

	}


  return DEVICE_OK; 
}

int SpotCamera::OnTriggerMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	//		if(IsCapturing()) return DEVICE_OK;
	TriggerModes tmode = TriggerModes(0);
	std::string value;


	if (eAct == MM::AfterSet) // property was written -> apply value to hardware
	{
		pProp->Get(value);
		if ( "NONE" == value)
			tmode = TriggerModesNS::None;
		else if( "EDGE" == value)
			tmode = TriggerModesNS::Edge;
		else if ( "BULB" == value)
			tmode = TriggerModesNS::Bulb;
		pImplementation_->TriggerMode(tmode);

	}
	else if (eAct == MM::BeforeGet) // property will be read -> update property with value from hardware
	{         

		tmode = pImplementation_->TriggerMode();
		if( TriggerModesNS::None == tmode)
			value = "NONE";
		if( TriggerModesNS::Edge == tmode)
			value = "EDGE";
		if( TriggerModesNS::Bulb == tmode)
			value = "BULB";

		pProp->Set( value.c_str()); // write back to GUI
	}


  return DEVICE_OK; 
}



// handles chip defect correction property
int SpotCamera::OnChipDefectCorrection(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	//			if(IsCapturing()) return DEVICE_OK;
	bool enabled;
	std::string value;

	if (eAct == MM::AfterSet) // property was written -> apply value to hardware
	{
		pProp->Get(value);
		pImplementation_->ChipDefectCorrection(CodeUtility::StringToBool(value));

	}
	else if (eAct == MM::BeforeGet) // property will be read -> update property with value from hardware
	{         

		enabled = pImplementation_->ChipDefectCorrection();
		// are "ON" and "OFF" validated to be allowed values of the property??
		pProp->Set( enabled?"ON":"OFF"); // write back to GUI
	}


  return DEVICE_OK; 
}

// handles noise filter percent threshold
int SpotCamera::OnNoiseFilterPercent(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	//		if(IsCapturing()) return DEVICE_OK;
	short percent;
	std::string value;

	if (eAct == MM::AfterSet) // property was written -> apply value to hardware
	{
		pProp->Get(value);
		pImplementation_->NoiseFilterPercent((short)atoi(value.c_str()));

	}
	else if (eAct == MM::BeforeGet) // property will be read -> update property with value from hardware
	{


		percent = pImplementation_->NoiseFilterPercent();
		std::ostringstream svalue;
		svalue << percent;

		pProp->Set( svalue.str().c_str()); // write back to GUI
	}
	return DEVICE_OK; 
}



// Sensor Clear Modes for Use with SPOT_SENSORCLEARMODE and SPOT_SENSORCLEARMODES
//#define SPOT_SENSORCLEARMODE_CONTINUOUS    0x01   // Continuously clear sensor
//#define SPOT_SENSORCLEARMODE_PREEMPTABLE   0x02   // Allow exposures to pre-emp sensor clearing
//#define SPOT_SENSORCLEARMODE_NEVER         0x04   // Never clear sensor
//enum ClearingModes { Continuous = 1, Mode3 = 3  } ..., Never = 4};

// handles "clear mode" setting
int SpotCamera::OnClearingMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	ClearingModes cmode = ClearingModes(0);
	std::string value;
	//		if(IsCapturing()) return DEVICE_OK;
	if (eAct == MM::AfterSet) // property was written -> apply value to hardware
	{
		pProp->Get(value);
		if( "CONTINUOUS" == value)
			cmode = ClearingModesNS::Continuous;
		else if( "PREEMPTABLE" == value)
			cmode = ClearingModesNS::Preemptable;
		else if( "NEVER" == value)
			cmode = ClearingModesNS::Never;

		pImplementation_->ClearingMode(cmode);

	}
	else if (eAct == MM::BeforeGet) // property will be read -> update property with value from hardware
	{         

		cmode  = pImplementation_->ClearingMode();

		if( cmode == ClearingModesNS::Continuous)
			value = "CONTINUOUS";
		else if(cmode == ClearingModesNS::Preemptable)
			value =  "PREEMPTABLE";
		else if(cmode == ClearingModesNS::Never)
			value ="NEVER";


		pProp->Set( value.c_str()); // write back to GUI
	}


  return DEVICE_OK; 
}




/**
 * Returns the current binning factor.
 * Required by the MM::Camera API.
 */
int SpotCamera::GetBinning() const
{
  char buf[MM::MaxStrLength];
  int ret = GetProperty(MM::g_Keyword_Binning, buf);
  if (ret != DEVICE_OK) return 1;
  return atoi(buf);

}

/**
 * Sets binning factor.
 * Required by the MM::Camera API.
 */
int SpotCamera::SetBinning(int binFactor)
{
  if(IsCapturing())
    return ERR_BUSY_ACQIRING;
  return SetProperty(MM::g_Keyword_Binning, CDeviceUtils::ConvertToString(binFactor));
}


///////////////////////////////////////////////////////////////////////////////
// SpotCamera Action handlers
///////////////////////////////////////////////////////////////////////////////

int SpotCamera::OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int nRet = DEVICE_OK;
	long value;

   if (eAct == MM::AfterSet)
   {



      pProp->Get(value);
		pImplementation_->BinSize((short)value);

		//todo - determine how to show newly restricted set of property values for bitdepths

      nRet = ResizeImageBuffer();
      if (nRet != DEVICE_OK)
         return nRet;

   }
   else if (eAct == MM::BeforeGet)
   {

		CodeUtility::DebugOutput("getting binning...");
		//		if (IsCapturing())return DEVICE_OK;
		long value;
		value = pImplementation_->BinSize();
      pProp->Set(value);
		CodeUtility::DebugOutput(" ...got binning\n");

   }
   return DEVICE_OK;
}




int SpotCamera::OnCamera(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	// get the list of cameras from the driver (i.e. the dll)
	std::map<int, std::string> cams = pImplementation_->AvailableSpotCameras();
	std::map<int, std::string>::iterator ii;

	// match to the name which was set
   if (eAct == MM::AfterSet)
   {
      pProp->Get(deviceName_);

		for(ii = cams.begin(); ii!= cams.end(); ++ii)
		{
			if( deviceName_ == ii->second)
			{
				pImplementation_->SelectedCameraIndex((const short)(ii->first));
            std::ostringstream stringStreamMessage;
            stringStreamMessage << "select Spot Camera " << deviceName_;
            LogMessage(stringStreamMessage.str(), true);
				break;
			}
		}
   }
   else if (eAct == MM::BeforeGet)
   {
		CodeUtility::DebugOutput("getting camera index\n");
		// find the selected camera
		short selCam = pImplementation_->SelectedCameraIndex();
		for(ii = cams.begin(); ii!= cams.end(); ++ii)
		{
			if( selCam == ii->first)
			{
				deviceName_ = ii->second;
            std::ostringstream stringStreamMessage;
            stringStreamMessage << "select Spot Camera " << deviceName_;
            LogMessage(stringStreamMessage.str(), true);
				break;
			}
		}

      pProp->Set(deviceName_.c_str());
   }
   return DEVICE_OK;
}


/**
 * Sync internal image buffer size to the chosen property values.
 */
int SpotCamera::ResizeImageBuffer(int , int)
{
	unsigned int x, y, sizeX, sizeY;

	// read current ROI

	int nRet = this->GetROI( x, y, sizeX, sizeY );
	if ( nRet != DEVICE_OK ) return nRet;

	// read binning property
//	int binSize = pImplementation_->BinSize();
	

	int byteDepth = (pImplementation_->BitDepth()+7)/ 8;
	if( 3==byteDepth)
		byteDepth = 4;

	//// resize image buffer
	//imageBuffer.Resize( sizeX / binSize, sizeY / binSize, byteDepth);
	//// was missing!! wow!
	//img_[0].Resize(sizeX / binSize, sizeY / binSize, byteDepth);

	// resize image buffer
	imageBuffer.Resize( sizeX, sizeY , byteDepth);
	// was missing!! wow!
	img_[0].Resize(sizeX , sizeY, byteDepth);


	// if thead don't run make sure the core image buffer is updated as well (if thread is active it is done before/allready)
	if (!IsCapturing())
	{
	 // make sure the circular buffer is properly sized => use 2 Buffers
	 GetCoreCallback()->InitializeImageBuffer(GetNumberOfComponents(), SPOTCAM_CIRCULAR_BUFFER_IMG_COUNT, GetImageWidth(), GetImageHeight(), GetImageBytesPerPixel());
	}

	return DEVICE_OK;
}


//int SpotCamera::StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow)
//{
//	// performance timing:
//	previousFrameRateStartTime_ = 0.;
//
//	CCameraBase<SpotCamera>::StartSequenceAcquisition(numImages, interval_ms, stopOnOverflow);
//
//	return DEVICE_OK;
//}



int SpotCamera::StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow)
{

	// performance timing:
	previousFrameRateStartTime_ = 0.;

   stopOnOverflow_ = stopOnOverflow;
	//thd_->numImages_ = numImages;

   MM::MMTime start = GetCurrentMMTime();
	
	
	{
		MMThreadGuard g(stopRequestLock_);
		stopRequest_ = false;
	}
	startme_ = true;
	//thd_->startTime_ = GetCurrentMMTime();
   imageCounter_ = 0;
	numImages_ = numImages;

	
   thd_->Start(numImages, interval_ms);


   return DEVICE_OK;
}


int SpotCamera::ThreadRun(void)
{
	if( startme_)
	{
		startme_ = false;
		exposureTime_ = pImplementation_->ExposureTime();
		pImplementation_->SetupNonStopSequence();

	}
	return DEVICE_OK;

}
  
int SpotCamera::StopSequenceAcquisition()
{
	{
		MMThreadGuard g(stopRequestLock_);
		stopRequest_ = true;
	}
	pImplementation_->StopDevice();

	CCameraBase<SpotCamera>::StopSequenceAcquisition();

	return DEVICE_OK;
}

int SpotCamera::CallBackToCamera()
{

	{
	MMThreadGuard g(stopRequestLock_);
	if (stopRequest_)
		return DEVICE_OK;
	}

	//MMThreadGuard cgard(this->cameraMMLock_);
	// calculate the framerate for second and subsequent frame
	if( 0. < previousFrameRateStartTime_)
	{
		double el = snapImageStartTime_ - previousFrameRateStartTime_;
		if ( 0. != el)
		{
			double framesPerSecond = 1000./el;
			ostringstream o;
			o << " current acq rate " << framesPerSecond << " frames per sec. \n";
			CodeUtility::DebugOutput( o.str());
		}
	}
	previousFrameRateStartTime_  = GetCurrentMMTime().getMsec();
	
	// wait for next image and put it into 'circular' buffer
	int nRet = CCameraBase<SpotCamera>::InsertImage();
	if( DEVICE_OK == nRet)
	{
		bool complete = (numImages_ <= ++imageCounter_);
		if(complete)
		{
			nRet = DEVICE_LOCALLY_DEFINED_ERROR;
			std::ostringstream  messs;
			messs << " sequence complete! \n" ;
			SetErrorText(DEVICE_LOCALLY_DEFINED_ERROR,messs.str().c_str());
			CodeUtility::DebugOutput(messs.str().c_str());
			pImplementation_->StopDevice();
		}
	}

	return nRet;


}


bool SpotCamera::WaitForExposureComplete(void)
{
	bool ret = true;
	MM::MMTime t0 = GetCurrentMMTime();
	MM::MMTime elapsed(0,0);
	MM::MMTime autoElapsed(0,0);

	// exposure
	double exp = GetExposure()*1000.;
   char buffer[MM::MaxStrLength];
   buffer[0] = '\0';
	GetProperty("AutoExposure", buffer);
	std::string sautoexposure(buffer);
	bool bautoexposure( CodeUtility::StringToBool( sautoexposure));

	if(bautoexposure)
	{
		while(! this->AutoEposureCalculationDone())
		{
			autoElapsed = GetCurrentMMTime() - t0;
			if( 5.e6 < autoElapsed.getUsec())
			{
				CodeUtility::DebugOutput("AutoEposureCalculation timed out!\n");
				ret = false;
				break;
			}
		}
		exp = pImplementation_->ExposureTime();
	}

	std::ostringstream osss;
	osss << "entered WaitForExposureComplete " << t0.getMsec() - SnapImageStartTime() << "\n";

	while( !ExposureComplete())
	{
		if(!bautoexposure)
		{
			elapsed = GetCurrentMMTime() - t0;
			if( (200000+ exp + autoElapsed.getUsec() ) < elapsed.getUsec())
			{
				CodeUtility::DebugOutput("WaitForExposureComplete timed out!\n");
				ret = false;
				break;
			}
		}
		CDeviceUtils::SleepMs(2);
	}
	osss << "exited WaitForExposureComplete " << GetCurrentMMTime().getMsec() - SnapImageStartTime() << "\n";
	CodeUtility::DebugOutput(osss.str().c_str());

	return ret;

}
