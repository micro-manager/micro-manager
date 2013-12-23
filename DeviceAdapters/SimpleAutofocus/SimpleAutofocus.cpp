///////////////////////////////////////////////////////////////////////////////
// FILE:          SimpleAutofocus.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   SimpleAutofocus controller adapter
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
#include <windows.h>
#define snprintf _snprintf
#endif


#include "SimpleAutofocus.h"
#include <string>
#include <math.h>
#include <sstream>


#include "../../MMDevice/ModuleInterface.h"
#include "boost/lexical_cast.hpp"
#include "boost/tuple/tuple.hpp"
#include <set>







#include <stdio.h>
#include <math.h>
#include <stdlib.h>




// property names:
// Controller
const char* g_ControllerName = "SimpleAutofocus";
const char* g_FocusMonitorDeviceName = "FocusMonitor";
const char* g_TPFocusDeviceName = "TPFocus";

const bool messageDebug = false;

class SAFPoint
{
public:
   SAFPoint( int seqNo, float z, float meanValue, float stdOverMeanScore, double hiPassScore, float normalizedDynamicRange ):thePoint_( seqNo, z, meanValue,  stdOverMeanScore, hiPassScore, normalizedDynamicRange )
   {
   }

   SAFPoint(  boost::tuple<int,float,float,float,double,float> apoint) : thePoint_(apoint) {}

   bool operator<(const SAFPoint& that) const
   {
      // sort by Z position
      return (boost::tuples::get<1>(thePoint_) < boost::tuples::get<1>(that.thePoint_));
   }

   const std::string DataRow() const
   {
      std::ostringstream data;
      data << std::setw(3) << boost::lexical_cast<std::string, int>( boost::tuples::get<0>(thePoint_) )<< "\t"
           << std::setprecision(5) << boost::tuples::get<1>(thePoint_) << "\t" // Z
           << std::setprecision(5) <<  boost::tuples::get<2>(thePoint_) << "\t" // mean
           << std::setprecision(6) << std::setiosflags(std::ios::scientific) << boost::tuples::get<3>(thePoint_) <<  std::resetiosflags(std::ios::scientific) << "\t"  // std / mean
           << std::setprecision(6) <<  std::setiosflags(std::ios::scientific) << boost::tuples::get<4>(thePoint_) << std::resetiosflags(std::ios::scientific) << "\t"
           << std::setprecision(5) <<  std::setiosflags(std::ios::scientific) << boost::tuples::get<5>(thePoint_) << std::resetiosflags(std::ios::scientific) ;  // normalized dynamic range
      return data.str();
   }

private:
   boost::tuple<int,float,float,float,double,float> thePoint_;

};

class SAFData
{
   std::set< SAFPoint > points_;

public:
   void InsertPoint( int seqNo, float z, float meanValue,float stdOverMeanScore,  double hiPassScore, float normalizedDynamicRange)
   {
      // SAFPoint value(int seqNo, float z, float meanValue,  float stdOverMeanScore, double hiPassScore);
      // VS 2008 thinks above line is a function decl !!!
      boost::tuple<int,float,float,float,double,float> vals(seqNo, z, meanValue, stdOverMeanScore,  hiPassScore, normalizedDynamicRange );
      SAFPoint value(vals);
      points_.insert(value);
   }

   // default dtor is ok

   void Clear()
   {
      points_.clear();
   }
   const std::string Table()
   {
      std::ostringstream data;
      data << "Acq#\t Z\tMean\tStd/Mean\tHiPassScore\tDynRange";
      std::set< SAFPoint >::iterator ii;
      for( ii = points_.begin(); ii!=points_.end(); ++ii)
      {
         data << "\n";
         data << ii->DataRow();
      }
      return data.str();
   }
};




//todo : maybe nicer to move this to a library




///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_ControllerName, MM::AutoFocusDevice, "SimpleAutofocus Finder");
   RegisterDevice(g_FocusMonitorDeviceName, MM::ImageProcessorDevice, "Focus score monitor - 100XImaging Inc.");
   RegisterDevice(g_TPFocusDeviceName, MM::AutoFocusDevice, "Three point focus - 100XImaging Inc.");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;
   if (strcmp(deviceName, g_ControllerName) == 0)
   {
      // create Controller
      SimpleAutofocus* pSimpleAutofocus = new SimpleAutofocus(g_ControllerName);
      return pSimpleAutofocus;
   }
   if (strcmp(deviceName, g_FocusMonitorDeviceName) == 0)
   {
      // create autoFocus
      return new FocusMonitor();
   }
   if (strcmp(deviceName, g_TPFocusDeviceName) == 0)
   {
      // create autoFocus
      return new ThreePointAF();
   }
   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
// Controller implementation
// ~~~~~~~~~~~~~~~~~~~~

SimpleAutofocus::SimpleAutofocus(const char* name) : 
   name_(name), 
   offset_(0.), 
   coarseStepSize_(1.), 
   coarseSteps_ (5), 
   fineStepSize_ (0.3), 
   fineSteps_ ( 5), 
   threshold_( 0.1), 
   pCore_(NULL), 
   cropFactor_(0.2), 
   busy_(false),
   latestSharpness_(0.), 
   enableAutoShuttering_(1),
   sizeOfTempShortBuffer_(0), 
   pSmoothedIm_(NULL), 
   sizeOfSmoothedIm_(0), 
   pShort_(NULL),
   recalculate_(0), 
   mean_(0.), 
   standardDeviationOverMean_(0.),
   pPoints_(NULL), 
   exposureForAutofocusAcquisition_(0.), 
   binningForAutofocusAcquisition_(0)
{
}

int SimpleAutofocus::Shutdown()
{
   return DEVICE_OK;
}


SimpleAutofocus::~SimpleAutofocus()
{
   delete pPoints_;
   if( NULL!=pShort_)
      free(pShort_);
   if(NULL!=pSmoothedIm_)
      free(pSmoothedIm_);
   Shutdown();
}

bool SimpleAutofocus::Busy()
{
   MMThreadGuard g(busyLock_);
   return busy_;
}

void SimpleAutofocus::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}






// channels are not available during initialization....
void SimpleAutofocus::RefreshChannelsToSelect(void)
{
   std::string  channelList;
   possibleChannels_.clear();
   char channelConfigName[MM::MaxStrLength];
   unsigned int channelConfigIterator = 0;
   for(;;)
   {
      pCore_->GetChannelConfig(channelConfigName, channelConfigIterator++);
      if( 0 < strlen(channelConfigName))
      {
         std::string n = std::string(channelConfigName);
         possibleChannels_.push_back(n);
         if( 0 < channelList.length() )
            channelList+=",";
         channelList += n;
      }
      else
         break;
   }
   char value[MM::MaxStrLength];
   std::string coreChannelGroup;
   if( DEVICE_OK == pCore_->GetDeviceProperty(MM::g_Keyword_CoreDevice, MM::g_Keyword_CoreChannelGroup, value))
      coreChannelGroup = std::string(value);
   std::ostringstream oMessage;
   oMessage << "in RefreshChannelsToSelect in group " << coreChannelGroup <<" channels available are: " << channelList;
   LogMessage(oMessage.str().c_str(),true);
}




int SimpleAutofocus::Initialize()
{
   if(NULL == pPoints_)
   {
      pPoints_ = new SAFData();
   }
   LogMessage("SimpleAutofocus::Initialize()");
   pCore_ = GetCoreCallback();
   CPropertyAction *pAct = new CPropertyAction (this, &SimpleAutofocus::OnExposure);
   CreateProperty(MM::g_Keyword_Exposure, "0.", MM::Float, false, pAct);
   pAct = new CPropertyAction(this, &SimpleAutofocus::OnBinning);
   CreateProperty("Binning","0",MM::Integer, false, pAct);
   pAct = new CPropertyAction(this, &SimpleAutofocus::OnCoarseStepNumber);
   CreateProperty("CoarseSteps from center","5",MM::Integer, false, pAct);
   pAct = new CPropertyAction(this, &SimpleAutofocus::OnStepsizeCoarse);
   CreateProperty("CoarseStepSize","1.0",MM::Float, false, pAct);
   pAct = new CPropertyAction(this, &SimpleAutofocus::OnFineStepNumber);
   CreateProperty("FineSteps from center","5",MM::Integer, false, pAct);
   pAct = new CPropertyAction(this, &SimpleAutofocus::OnStepSizeFine);
   CreateProperty("FineStepSize","0.3",MM::Float, false, pAct);
   // Set the sharpness threshold
   pAct = new CPropertyAction(this, &SimpleAutofocus::OnThreshold);
   CreateProperty("Threshold","0.1",MM::Float, false, pAct);
   // Set the cropping factor to speed up computation
   pAct = new CPropertyAction(this, &SimpleAutofocus::OnCropFactor);
   CreateProperty("CropFactor","0.2",MM::Float, false, pAct);
   SetPropertyLimits("CropFactor",0.1, 1.0);
   pAct = new CPropertyAction(this, &SimpleAutofocus::OnSharpnessScore);
   CreateProperty("SharpnessScore","0.0",MM::Float, true, pAct);
   pAct = new CPropertyAction(this, &SimpleAutofocus::OnMean);
   CreateProperty("Mean","0",MM::Float, true, pAct);
   pAct = new CPropertyAction(this, &SimpleAutofocus::OnEnableAutoShutter);
   CreateProperty("EnableAutoshutter","0",MM::Integer, false, pAct);
   pAct = new CPropertyAction(this, &SimpleAutofocus::OnRecalculate);
   CreateProperty("Re-acquire&EvaluateSharpness","0",MM::Integer, false, pAct);
   pAct = new CPropertyAction(this, &SimpleAutofocus::OnStandardDeviationOverMean);
   CreateProperty("StandardDeviation/Mean","0",MM::Float, true, pAct);
   pAct = new CPropertyAction(this, &SimpleAutofocus::OnChannel);
   CreateProperty("Channel","",MM::String, false, pAct);
   AddAllowedValue("Channel","");
   AddAllowedValue("Channel","...");
   selectedChannelConfig_ = "";
   pAct = new CPropertyAction(this, &SimpleAutofocus::OnSearchAlgorithm);
   CreateProperty("SearchAlgorithm","Brent",MM::String, false, pAct);
   AddAllowedValue("SearchAlgorithm","Brent");
   AddAllowedValue("SearchAlgorithm","BruteForce");
   searchAlgorithm_ = "Brent";
   UpdateStatus();
   return DEVICE_OK;
}


// API

bool SimpleAutofocus::IsContinuousFocusLocked() {
   return locked_;
} ;

int SimpleAutofocus::FullFocus()
{
   int retval = DEVICE_ERR;
   acquisitionSequenceNumber_ = 0;
   pPoints_->Clear();
   double oldExposure;
   double currentExposure;
   int oldBinning;
   std::string previousChannelConfig;
   std::string coreChannelGroup;
   char coreCameraDeviceName[MM::MaxStrLength];
   char value[MM::MaxStrLength];
   std::ostringstream ossbinning;
   // if user hasn't specified a unique binning for AF acqusiition, use the camera's current setting
   // todo - how to get list of valid property values?
   pCore_->GetDeviceProperty(MM::g_Keyword_CoreDevice, MM::g_Keyword_CoreCamera, coreCameraDeviceName);
   pCore_->GetDeviceProperty(coreCameraDeviceName, MM::g_Keyword_Binning, value);
   MM::Device* pCamera = pCore_->GetDevice(this, coreCameraDeviceName);
   if(
      ((MM::Camera*)pCamera)->IsCapturing()
   )
   {
      pCore_->PostError(DEVICE_CAMERA_BUSY_ACQUIRING, "Autofocus not possible");
      return DEVICE_CAMERA_BUSY_ACQUIRING;
   }
   std::istringstream issbinning(value);
   issbinning >> oldBinning;
   if( 0 < binningForAutofocusAcquisition_ && ( oldBinning != binningForAutofocusAcquisition_))
   {
      ossbinning << binningForAutofocusAcquisition_;
      retval = pCore_->SetDeviceProperty(coreCameraDeviceName, MM::g_Keyword_Binning, ossbinning.str().c_str());
      if( DEVICE_OK != retval)
         return retval;
   }
   // if user hasn't specified a unique exposure for AF acqusiition, use the camera's current setting
   pCore_->GetExposure(oldExposure);
   if( exposureForAutofocusAcquisition_ < 1.e-7)
      currentExposure = oldExposure;
   else
      currentExposure = exposureForAutofocusAcquisition_;
   // set the value to the camera via the core
   pCore_->SetExposure(currentExposure);
   if( 0< selectedChannelConfig_.length())
   {
      if( DEVICE_OK == pCore_->GetDeviceProperty(MM::g_Keyword_CoreDevice, MM::g_Keyword_CoreChannelGroup, value))
         coreChannelGroup = std::string(value);
      // retrieve the system's current configuration
      pCore_->GetCurrentConfig(coreChannelGroup.c_str(), MM::MaxStrLength, value);
      previousChannelConfig = std::string(value);
      retval = pCore_->SetConfig(coreChannelGroup.c_str(),selectedChannelConfig_.c_str());
      if(retval != DEVICE_OK)
         return retval;
   }
   char shutterDeviceName[MM::MaxStrLength];
   char previousShutterState[MM::MaxStrLength];
   pCore_->GetDeviceProperty(MM::g_Keyword_CoreDevice, MM::g_Keyword_CoreAutoShutter, value);
   std::istringstream iss(value);
   int ivalue ;
   iss >> ivalue;
   bool previousAutoShutterSetting = (0==ivalue?false:true);
   bool currentAutoShutterSetting = previousAutoShutterSetting;
   // allow auto-shuttering or continuous illumination
   if((0==enableAutoShuttering_) && previousAutoShutterSetting)
   {
      pCore_->SetDeviceProperty(MM::g_Keyword_CoreDevice, MM::g_Keyword_CoreAutoShutter, "0"); // disable auto-shutter
      currentAutoShutterSetting = false;
   }

   pCore_->GetDeviceProperty(MM::g_Keyword_CoreDevice, MM::g_Keyword_CoreShutter, shutterDeviceName);
   pCore_->GetDeviceProperty(shutterDeviceName, MM::g_Keyword_State, previousShutterState);

   if( !currentAutoShutterSetting)
   {
      pCore_->SetDeviceProperty(shutterDeviceName, MM::g_Keyword_State, "1"); // open shutter
   }
   //todo - this will be more beautiful using a pointer to the various search method member functions.
   if( searchAlgorithm_ == "Brent")
   {
      retval =  BrentSearch();
   }
   else if( searchAlgorithm_ == "BruteForce")
   {
      retval =  BruteForceSearch();
   }
   int tret = pCore_->SetDeviceProperty(shutterDeviceName, MM::g_Keyword_State, previousShutterState); 
   if( DEVICE_OK != tret)
      LogMessage("Error closing shutter upon exiting FullFocus",false);
   // restore system configuration;
   if( 0 < previousChannelConfig.length() && 0 < coreChannelGroup.length())
   {
      retval = pCore_->SetConfig(coreChannelGroup.c_str(), previousChannelConfig.c_str());
   }
   // restore auto-shutter setting
   pCore_->SetDeviceProperty(MM::g_Keyword_CoreDevice, MM::g_Keyword_CoreAutoShutter, previousAutoShutterSetting?"1":"0"); // restore auto-shutter
   // restore the exposure selected in the mainframe
   tret = pCore_->SetExposure(oldExposure);
   if( DEVICE_OK != tret)
      LogMessage("Error in SetExposure exiting FullFocus",false);
   ossbinning.str("");
   ossbinning << oldBinning;
   tret = pCore_->SetDeviceProperty(coreCameraDeviceName, MM::g_Keyword_Binning, ossbinning.str().c_str());
   if( DEVICE_OK != tret)
      LogMessage("Error in Setting of Binning exiting FullFocus",false);
   return retval;
};

int SimpleAutofocus::IncrementalFocus() {
   return -1;
};
int SimpleAutofocus::GetLastFocusScore(double& score) {
   score = latestSharpness_;
   return 0;
};
int SimpleAutofocus::GetCurrentFocusScore(double& score) {
   score = latestSharpness_ = SharpnessAtZ(Z());
   return 0;
};
int SimpleAutofocus::AutoSetParameters() {
   return 0;
};
int SimpleAutofocus::GetOffset(double &offset) {
   offset = offset_;
   return 0;
};
int SimpleAutofocus::SetOffset(double offset) {
   offset_ = offset;
   return 0;
};


/////////////////////////////////////////////
// Property Generators
/////////////////////////////////////////////




///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

// action interface
// ---------------
int SimpleAutofocus::OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(exposureForAutofocusAcquisition_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(exposureForAutofocusAcquisition_);
   }
   return DEVICE_OK;
}


int SimpleAutofocus::OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(binningForAutofocusAcquisition_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(binningForAutofocusAcquisition_);
   }
   return DEVICE_OK;
}




int SimpleAutofocus::OnCoarseStepNumber(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(coarseSteps_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(coarseSteps_);
   }
   return DEVICE_OK;
};

int SimpleAutofocus::OnFineStepNumber(MM::PropertyBase* pProp, MM::ActionType eAct) {
   if (eAct == MM::BeforeGet)      {
      pProp->Set(fineSteps_);
   }
   else if (eAct == MM::AfterSet)      {
      pProp->Get(fineSteps_);
   }
   return DEVICE_OK;
}
int SimpleAutofocus::OnStepsizeCoarse(MM::PropertyBase* pProp, MM::ActionType eAct) {
   if (eAct == MM::BeforeGet)      {
      pProp->Set(coarseStepSize_);
   }
   else if (eAct == MM::AfterSet)      {
      pProp->Get(coarseStepSize_);
   }
   return DEVICE_OK;
}
int SimpleAutofocus::OnStepSizeFine(MM::PropertyBase* pProp, MM::ActionType eAct) {
   if (eAct == MM::BeforeGet)      {
      pProp->Set(fineStepSize_);
   }
   else if (eAct == MM::AfterSet)      {
      pProp->Get(fineStepSize_);
   }
   return DEVICE_OK;
}
int SimpleAutofocus::OnThreshold(MM::PropertyBase* pProp, MM::ActionType eAct) {
   if (eAct == MM::BeforeGet)      {
      pProp->Set(threshold_);
   }
   else if (eAct == MM::AfterSet)      {
      pProp->Get(threshold_);
   }
   return DEVICE_OK;
}
int SimpleAutofocus::OnEnableAutoShutter(MM::PropertyBase* pProp, MM::ActionType eAct) {
   if (eAct == MM::BeforeGet)      {
      pProp->Set(enableAutoShuttering_);
   }
   else if (eAct == MM::AfterSet)      {
      pProp->Get(enableAutoShuttering_);
   }
   return DEVICE_OK;
}

int SimpleAutofocus::OnRecalculate(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(recalculate_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(recalculate_);
      if( 0!= recalculate_)
      {
         // check if camera is available
         char coreCameraDeviceName[MM::MaxStrLength];
         //char value[MM::MaxStrLength];
         pCore_->GetDeviceProperty(MM::g_Keyword_CoreDevice, MM::g_Keyword_CoreCamera, coreCameraDeviceName);
         MM::Device* pCamera = pCore_->GetDevice(this, coreCameraDeviceName);
         if(
            ((MM::Camera*)pCamera)->IsCapturing()
         )
            return DEVICE_CAMERA_BUSY_ACQUIRING;
         latestSharpness_ = SharpnessAtZ(Z());
         recalculate_ = 0;
         pProp->Set(recalculate_);
      }
   }
   return DEVICE_OK;
};


int SimpleAutofocus::OnStandardDeviationOverMean(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(standardDeviationOverMean_);
   }
   else if (eAct == MM::AfterSet)
   {
      // never do anything for a read-only property
   }
   return DEVICE_OK;
}


int SimpleAutofocus::OnSearchAlgorithm(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(searchAlgorithm_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(searchAlgorithm_);
   }
   return DEVICE_OK;
}


int SimpleAutofocus::OnChannel(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      RefreshChannelsToSelect();
      SetAllowedValues("Channel", possibleChannels_);
      std::vector<std::string>::iterator isThere = std::find(possibleChannels_.begin(),possibleChannels_.end(), selectedChannelConfig_);
      if( possibleChannels_.end() == isThere)
         selectedChannelConfig_ = "";
      //todo - triple check that this doesn't wipe out AF channel selections!!
      // if no channcel config is selected, we don't change the channel....
      //if( selectedChannelConfig_.length() < 1) // no channel is selected for AF
      //{
      //   // get the channel selected for the mainframe
      //   char value[MM::MaxStrLength];
      //   std::string coreChannelGroup;
      //   if( DEVICE_OK == pCore_->GetDeviceProperty(MM::g_Keyword_CoreDevice, MM::g_Keyword_CoreChannelGroup, value))
      //      coreChannelGroup = std::string(value);
      //   if( 0 < coreChannelGroup.length() )
      //   {
      //      pCore_->GetCurrentConfig(coreChannelGroup.c_str(),MM::MaxStrLength,value);
      //      selectedChannelConfig_ = std::string(value);
      //   }
      //}
      pProp->Set(selectedChannelConfig_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(selectedChannelConfig_);
      if ( 0 < selectedChannelConfig_.length())
      {
         char value[MM::MaxStrLength];
         std::string coreChannelGroup;
         if( DEVICE_OK == pCore_->GetDeviceProperty(MM::g_Keyword_CoreDevice, MM::g_Keyword_CoreChannelGroup, value))
            coreChannelGroup = std::string(value);
         int ret = pCore_->SetConfig(coreChannelGroup.c_str(),selectedChannelConfig_.c_str());
         if(ret != DEVICE_OK)
            return ret;
      }
   }
   return DEVICE_OK;
};







int SimpleAutofocus::OnCropFactor(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(cropFactor_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(cropFactor_);
   }
   return DEVICE_OK;
};

int SimpleAutofocus::OnSharpnessScore(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(latestSharpness_);
   }
   else if (eAct == MM::AfterSet)
   {
      // never do anything for a read-only property
   }
   return DEVICE_OK;
}

int SimpleAutofocus::OnMean(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(mean_);
   }
   else if (eAct == MM::AfterSet)
   {
      // never do anything for a read-only property
   }
   return DEVICE_OK;
}



double SimpleAutofocus::SharpnessAtZ(const double z)
{
   MMThreadGuard g(busyLock_);
   busy_ = true;
   Z(z);
   min1_ = 1.e8;
   min2_ = 1.e8;
   max1_ = -1.e8;
   max2_ = -1.e8;
   // the crop factor, median filter and 3x3 high-pass process follows the java implementation from Pakpoom Subsoontorn & Hernan Garcia  -- KH
   int w0 = 0, h0 = 0, d0 = 0;
   double sharpness = 0;
   pCore_->GetImageDimensions(w0, h0, d0);
   int width =  (int)(cropFactor_*w0);
   int height = (int)(cropFactor_*h0);
   int ow = (int)(((1-cropFactor_)/2)*w0);
   int oh = (int)(((1-cropFactor_)/2)*h0);
   const unsigned long thisSize = sizeof(*pSmoothedIm_)*width*height;
   if( thisSize != sizeOfSmoothedIm_)
   {
      if(NULL!=pSmoothedIm_)
         free(pSmoothedIm_);
      // malloc is faster than new...
      pSmoothedIm_ = (float*)malloc(thisSize);
      if(NULL!=pSmoothedIm_)
      {
         sizeOfSmoothedIm_ = thisSize;
      }
      else // todo throw out of here...
      {
         busy_=false;
         return sharpness;
      }
   }
   // copy from MM image to the working buffer
   ImgBuffer image(w0,h0,d0);
   //snap an image
   const unsigned char* pI = reinterpret_cast<const unsigned char*>(pCore_->GetImage());
   const unsigned short* pSInput = reinterpret_cast<const unsigned short*>(pI);
   int iindex;
   bool legalFormat = false;
   // to keep it simple always copy to a short array
   if( 0 != pSInput)
   {
      switch( d0)
      {
      case 1:
         legalFormat = true;
         if( sizeOfTempShortBuffer_ != sizeof(unsigned short)*w0*h0)
         {
            if( NULL != pShort_)
               free(pShort_);
            // malloc is faster than new...
            pShort_ = (unsigned short*)malloc( sizeof(unsigned short)*w0*h0);
            if( NULL!=pShort_)
            {
               sizeOfTempShortBuffer_ = sizeof(unsigned short)*w0*h0;
            }
         }
         for(iindex = 0; iindex < w0*h0; ++iindex)
         {
            pShort_[iindex] = pI[iindex];
         }
         break;
      case 2:
         legalFormat = true;
         if( sizeOfTempShortBuffer_ != sizeof(unsigned short)*w0*h0)
         {
            if( NULL != pShort_)
               free(pShort_);
            pShort_ = (unsigned short*)malloc( sizeof(unsigned short)*w0*h0);
            if( NULL!=pShort_)
            {
               sizeOfTempShortBuffer_ = sizeof(unsigned short)*w0*h0;
            }
         }
         for(iindex = 0; iindex < w0*h0; ++iindex)
         {
            pShort_[iindex] = pSInput[iindex];
         }
         break;
      default:
         break;
      }
   }
   if(legalFormat)
   {
      // calculate the standard deviation & mean
      long nPts = 0;
      mean_ = 0;
      double M2 = 0;
      double delta;
      // one-pass algorithm for mean and std from Welford / Knuth  - KH
      for (int i=0; i<width; i++)
      {
         for (int j=0; j<height; j++)
         {
            ++nPts;
            long value = pShort_[ow+i+ width*(oh+j)];
            delta = value - mean_;
            mean_ = mean_ + delta/nPts;
            M2 = M2 + delta*(value - mean_); // #This expression uses the new value of mean_
         }
      }
      //double variance_n = M2/nPts;
      double variance = M2/(nPts - 1);
      standardDeviationOverMean_ = 0.;
      double meanScaling = 1.;
      if( 0. != mean_)
      {
         standardDeviationOverMean_ = pow(variance,0.5)/mean_;
         meanScaling = 1./mean_;
      }
      LogMessage("N " + boost::lexical_cast<std::string,long>(nPts) + " mean " +  boost::lexical_cast<std::string,float>((float)mean_) + " nrmlzd std " +  boost::lexical_cast<std::string,float>((float)standardDeviationOverMean_) );
      // ToDO -- eliminate copy above.
      int x[9];
      int y[9];
      /*Apply 3x3 median filter to reduce shot noise*/
      for (int i=0; i<width; i++) {
         for (int j=0; j<height; j++) {
            float theValue;
            x[0]=ow+i-1;
            y[0]= (oh+j-1);
            x[1]=ow+i;
            y[1]= (oh+j-1);
            x[2]=ow+i+1;
            y[2]= (oh+j-1);
            x[3]=ow+i-1;
            y[3]=(oh+j);
            x[4]=ow+i;
            y[4]=(oh+j);
            x[5]=ow+i+1;
            y[5]=(oh+j);
            x[6]=ow+i-1;
            y[6]=(oh+j+1);
            x[7]=ow+i;
            y[7]=(oh+j+1);
            x[8]=ow+i+1;
            y[8]=(oh+j+1);
            // truncate the median filter window  -- duplicate edge points
            // this could be more efficient, we could fill in the interior image [1,w0-1]x[1,h0-1] then explicitly fill in the edge pixels.
            for(int ij =0; ij < 9; ++ij)
            {
               if( x[ij] < 0)
                  x[ij] = 0;
               else if( w0-1 < x[ij])
                  x[ij] = w0-1;
               if( y[ij] < 0)
                  y[ij] = 0;
               else if( h0-1 < y[ij])
                  y[ij] = h0-1;
            }
            std::vector<unsigned short> windo;
            for(int ij = 0; ij < 9; ++ij)
            {
               windo.push_back(pShort_[ x[ij] + w0*y[ij]]);
            }
            // N.B. this window filler as ported from java needs to have a pad guaranteed around the cropped image!!!! KH
            //windo[0] = pShort_[ow+i-1 + width*(oh+j-1)];
            //windo[1] = pShort_[ow+i+ width*(oh+j-1)];
            //windo[2] = pShort_[ow+i+1+ width*(oh+j-1)];
            //windo[3] = pShort_[ow+i-1+ width*(oh+j)];
            //windo[4] = pShort_[ow+i+ width*(oh+j)];
            //windo[5] = pShort_[ow+i+1+ width*(oh+j)];
            //windo[6] = pShort_[ow+i-1+ width*(oh+j+1)];
            //windo[7] = pShort_[ow+i+ width*(oh+j+1)];
            //windo[8] = pShort_[ow+i+1+ width*(oh+j+1)];
            // to reduce effect of bleaching on the high-pass sharpness measurement, i use the image normalized by the mean - KH.
            theValue = (float)((double)FindMedian(windo)*meanScaling);
            pSmoothedIm_[i + j*width] = theValue;
            // the dynamic range of the normalized image is a very strong function of the image sharpness, also  - KH
            // here I'm using dynamic range of the median-filter image
            // a faster measure could skip the median filter, but use the sum of the 5 - 10 highest and 5 - 10 lowest normalized pixels
            // average over a couple of points to lessen effect of fluctuations & noise
            // todo - make the active measure of image sharpness user-selectable
            // save the  max points and the min points
            if( theValue < min1_ )
            {
               min2_ = min1_;
               min1_ = theValue;
            }
            else if (theValue < min2_)
            {
               min2_=theValue;
            }
            if( max1_ < theValue)
            {
               max2_ = max1_;
               max1_ = theValue;
            }
            else if (max2_ < theValue )
            {
               max2_=theValue;
            }
         }
      }
      /*Edge detection using a 3x3 filter: [-2 -1 0; -1 0 1; 0 1 2]. Then sum all pixel values. Ideally, the sum is large if most edges are sharp*/
      for (int k=1; k<width-1; k++) {
         for (int l=1; l<height-1; l++)
         {
            double convolvedValue = -2.0*pSmoothedIm_[k-1 + width*(l-1)] - pSmoothedIm_[k+ width*(l-1)]-pSmoothedIm_[k-1 + width*l]+pSmoothedIm_[k+1 + width*l]+pSmoothedIm_[k+ width*(l+1)]+2.0*pSmoothedIm_[k+1+ width*(l+1)];
            sharpness = sharpness + convolvedValue*convolvedValue;
         }
      }
      //free(pShort);
   }
   busy_ = false;
   latestSharpness_ = sharpness;
   pPoints_->InsertPoint(acquisitionSequenceNumber_++,(float)z,(float)mean_,(float)standardDeviationOverMean_,latestSharpness_,(float)( 0.5*((max1_+max2_)-(min1_+min2_))));
   return sharpness;
}



void  SimpleAutofocus::Z(const double value)
{
   pCore_->SetFocusPosition(value);
}

double SimpleAutofocus::Z(void)
{
   double value;
   pCore_->GetFocusPosition(value);
   return value;
}


void SimpleAutofocus::Exposure(const int value)
{
   pCore_->SetExposure(value);
};

int SimpleAutofocus::Exposure(void) {
   double value;
   pCore_->GetExposure(value);
   return (int)(0.5+value);
};


// always calls member function SharpnessAtZ

int SimpleAutofocus::BruteForceSearch( )
{
   double baseDist = 0.;
   double bestDist = 0.;
   double curSh = 0. ;
   double bestSh = 0.;
   MM::MMTime tPrev;
   MM::MMTime tcur;
   double curDist = Z();
   baseDist = curDist - coarseStepSize_ * coarseSteps_;
   // here is the linear search algorithm from  Pakpoom Subsoontorn & Hernan Garcia  -- KH
   // start of coarse search
   LogMessage("AF start coarse search range is  " + boost::lexical_cast<std::string,double>(baseDist) + " to " + boost::lexical_cast<std::string,double>(baseDist + coarseStepSize_*(2 * coarseSteps_)), messageDebug);
   for (int i = 0; i < 2 * coarseSteps_ + 1; ++i)
   {
      tPrev = GetCurrentMMTime();
      curDist = baseDist + i * coarseStepSize_;
      std::ostringstream progressMessage;
      progressMessage << "\nAF evaluation @ " + boost::lexical_cast<std::string,double>(curDist);
      curSh = SharpnessAtZ( curDist);
      progressMessage <<  " AF metric is: " + boost::lexical_cast<std::string,double>(curSh);
      LogMessage( progressMessage.str(),  messageDebug);
      if (curSh > bestSh)
      {
         bestSh = curSh;
         bestDist = curDist;
      } else if (bestSh - curSh > threshold_ * bestSh)
      {
         break;
      }
      tcur = GetCurrentMMTime() - tPrev;
   }
   baseDist = bestDist - fineStepSize_ * fineSteps_;
   LogMessage("AF start fine search range is  " + boost::lexical_cast<std::string,double>(baseDist)+" to " + boost::lexical_cast<std::string,double>( baseDist+(2*fineSteps_)*fineStepSize_),  messageDebug);
   //Fine search
   for (int i = 0; i < 2 * fineSteps_ + 1; i++)
   {
      tPrev = GetCurrentMMTime();
      curDist =  baseDist + i * fineStepSize_;
      std::ostringstream progressMessage;
      progressMessage << "\nAF evaluation @ " + boost::lexical_cast<std::string,double>(curDist);
      curSh = SharpnessAtZ(curDist);
      progressMessage <<  " AF metric is: " + boost::lexical_cast<std::string,double>(curSh);
      LogMessage( progressMessage.str(),  messageDebug);
      if (curSh > bestSh)
      {
         bestSh = curSh;
         bestDist = curDist;
      } else if (bestSh - curSh > threshold_ * bestSh)
      {
         break;
      }
      tcur = GetCurrentMMTime() - tPrev;
   }
   LogMessage("AF best position is " + boost::lexical_cast<std::string,double>(bestDist),  messageDebug);
   LogMessage("AF Performance Table:\n" + pPoints_->Table(), messageDebug);
   Z(bestDist);
   latestSharpness_ = bestSh;
   return DEVICE_OK;
}





int SimpleAutofocus::BrentSearch( )
{
   int ret = DEVICE_OK;
   double baseDist = 0.;
   double bestDist = 0.;
   //double curSh = 0. ;
   //double bestSh = 0.;
   MM::MMTime tPrev;
   MM::MMTime tcur;
   double curDist = Z();
   baseDist = curDist - coarseStepSize_ * coarseSteps_;
   double z0 = baseDist;
   double z1 = baseDist + coarseStepSize_*(2 * coarseSteps_);
   LogMessage("AF start search range is  " + boost::lexical_cast<std::string,double>(z0) + " to " + boost::lexical_cast<std::string,double>(z1), messageDebug);
   int status=0;
   double dvalue = -1.*SharpnessAtZ(z1);
   for ( ; ; )
   {
      // query for next position to evaluate
      bestDist = local_min_rc( &z0, &z1, &status, dvalue, (double)fineStepSize_ );
      if ( status < 0 )
      {
         ret = status;
         break;
      }
      // next position
      dvalue = -1.*SharpnessAtZ( bestDist );
      if ( status == 0 )
      {
         break;
      }
      if ( 27 < acquisitionSequenceNumber_ )
      {
         LogMessage("too many steps in Autofocus Brent search, please check the parameters!",false);
         ret = DEVICE_ERR;
         break;
      }
   }
   LogMessage("AF best position is " + boost::lexical_cast<std::string,double>(bestDist),  messageDebug);
   LogMessage("AF Performance Table:\n" + pPoints_->Table(), messageDebug);
   Z(bestDist);
   return DEVICE_OK;
}


double SimpleAutofocus::DoubleFunctionOfDouble(const double v)
{
   //todo selection of sharpness measure will set a pointer to member function.....
   return SharpnessAtZ(v);
}
