///////////////////////////////////////////////////////////////////////////////
// FILE:          Andor.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Andor camera module 
//                
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 06/30/2006
// COPYRIGHT:     University of California, San Francisco, 2006
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
// REVISIONS:     May 21, 2007, Jizhen Zhao, Andor Technologies
//                Temerature control and other additional related properties added,
//                gain bug fixed, refernce counting fixed for shutter adapter.
//
//              May 23 & 24, 2007, Daigang Wen, Andor Technology plc added/modified:
//              Cooler is turned on at startup and turned off at shutdown
//              Cooler control is changed to cooler mode control
//              Pre-Amp-Gain property is added
//              Temperature Setpoint property is added
//              Temperature is resumed as readonly
//              EMGainRangeMax and EMGainRangeMin are added
//
//              April 3 & 4, 2008, Nico Stuurman, UCSF
//              Changed Sleep statement in AcqSequenceThread to be 20% of the actualInterval instead of 5 ms
//            Added property limits to the Gain (EMGain) property
//            Added property limits to the Temperature Setpoint property and delete TempMin and TempMax properties
//
// FUTURE DEVELOPMENT: From September 1 2007, the development of this adaptor is taken over by Andor Technology plc. Daigang Wen (d.wen@andor.com) is the main contact. Changes made by him will not be labeled.
// LINUX DEVELOPMENT: From February 1, 2009, Linux compatibility was done by Karl Bellve at the Biomedical Imaging Group at the University of Massachusetts (Karl.Bellve@umassmed.edu)
// CVS:           $Id$

#ifdef WIN32
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include "atmcd32d.h"
#else 
#include "atmcdLXd.h"
#include <dlfcn.h>
#include <stdio.h>
#ifndef MAX_PATH
#define MAX_PATH PATH_MAX
#endif
#define stricmp strcasecmp 
#define strnicmp strncasecmp 
#endif

#include "../../MMDevice/ModuleInterface.h"
#include "Andor.h"
#include "SpuriousNoiseFilterControl.h"
#include "ReadModeControl.h"
#include "SRRFControl.h"

#include <string>
#include <sstream>
#include <iomanip>
#include <math.h>

#include <iostream>
#include <algorithm>


#pragma warning(disable : 4996) // disable warning for deperecated CRT functions on Windows 

using namespace std;

// global constants
const char* g_AndorName = "Andor";
const char* g_IxonName = "Ixon";

const char* g_PixelType_8bit = "8bit";
const char* g_PixelType_16bit = "16bit";

const char* g_Keyword_KeepCleanTime = "KeepCleanTime";

const char* g_ShutterMode = "Shutter";
const char* g_ExternalShutterMode = "Shutter (External)";
const char* g_InternalShutterMode = "Shutter (Internal)";
const char* g_ShutterModeOpeningTime = "Shutter Opening Time";
const char* g_ShutterModeClosingTime = "Shutter Closing Time";
const char* g_ShutterModeOpeningTimeInvalid = "Shutter Opening Time is Invalid";
const char* g_ShutterModeClosingTimeInvalid = "Shutter Closing Time is Invalid";
const char* g_ShutterModeInvalid = "Shutter Mode is Invalid";
const char* g_ShutterMode_Auto = "Auto";
const char* g_ShutterMode_Open = "Open";
const char* g_ShutterMode_Closed = "Closed";
const char* g_ShutterTTL = "Shutter TTL Value";
const char* g_ShutterTTLHighToOpen = "High to Open";
const char* g_ShutterTTLLowToOpen = "Low to Open";

const char* g_SnapImageDelay = "Advanced | Snap Image Additional Delay (ms)";
const char* g_SnapImageMode = "Advanced | Snap Image Timing Mode";
const char* g_SnapImageModeDelayForExposure = "Delay for Exposure";
const char* g_SnapImageModeWaitForReadout = "Wait for Readout";
const char* g_SnapImageDelayInvalid = "Invalid Snap Image Delay (Cannot be negative)";

const char* g_FanMode_Full = "Full";
const char* g_FanMode_Low = "Low";
const char* g_FanMode_Off = "Off";

const char* g_CoolerMode_FanOffAtShutdown = "Fan off at shutdown";
const char* g_CoolerMode_FanOnAtShutdown = "Fan on at shutdown";

const char* g_FrameTransferOn = "On";
const char* g_FrameTransferOff = "Off";
const char* g_OutputAmplifier = "Output_Amplifier";
const char* g_OutputAmplifier_EM = "Standard EMCCD gain register";
const char* g_OutputAmplifier_Conventional = "Conventional CCD register";

const char* g_ADChannel = "AD_Converter";
const char* g_VerticalSpeedProperty = "VerticalSpeed (microseconds)";

const char* g_EMGain = "EMSwitch";
const char* g_EMGainValue = "Gain";
const char* g_TimeOut = "TimeOut";
const char* g_CameraInformation = "1. Camera Information : | Type | Model | Serial No. |";

const char* g_cropMode = "Isolated Crop Mode";

const char* g_ROIProperty = "Region of Interest";
const char* g_ROIFullImage = "Full Image";
const char* g_ROIFVB = "FVB";
const char* g_ROICustom = "Custom ROI";

const char* g_External = "External";
const char* g_ExternalExposure = "External Exposure";
const char* g_ExternalStart = "External Start";
const char* g_FastExternal = "Fast External";
const char* g_Internal = "Internal";
const char* g_Software = "Software";

const char* const g_Keyword_Metadata_SRRF_Frame_Time = "SRRFFrameTime-ms";

const int NUMULTRA897CROPROIS = 9;
AndorCamera::ROI g_Ultra897CropROIs[NUMULTRA897CROPROIS] = {
   // left  bot   ht    width
   {  122,  127,  256,  256   },
   {  156,  159,  192,  192   },
   {  188,  191,  128,  128   },
   {  208,  207,  96,   96    },
   {  218,  223,  64,   64    },
   {  240,  239,  32,   32    },
   {  7,    248,  496,  16    },
   {  7,    251,  496,  8     },
   {  7,    253,  496,  4     }
};

const int NUMULTRA888CROPROIS = 9;
AndorCamera::ROI g_Ultra888CropROIs[NUMULTRA888CROPROIS] = {
   // left  bot   ht    width
   {  240,  255,  512,  512   },
   {  368,  383,  256,  256   },
   {  432,  447,  128,  128   },
   {  475,  479,  64,   64    },
   {  486,  495,  32,   32    },
   {  0,    495,  1024, 32    },
   {  0,    503,  1024, 16    },
   {  0,    507,  1024,  8     },
   {  0,    509,  1024,  4     }
};

const at_u32 MAX_IMAGES_PER_DMA = 64;
const at_u32 MAX_CHARS_PER_DESCRIPTION = 64;
const at_u32 MAX_CHARS_PER_OA_DESCRIPTION = 256;

// singleton instance
AndorCamera* AndorCamera::instance_ = 0;
unsigned int AndorCamera::refCount_ = 0;

// global Andor driver thread lock
MMThreadLock g_AndorDriverLock;

#ifndef WIN32
#include <sys/times.h>
#define WORD ushort 
long GetTickCount()
{
   tms tm;
   return times(&tm);
}
#endif
// end of kdb 
///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////

MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_AndorName, MM::CameraDevice, "Generic Andor Camera Adapter");
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{

   delete pDevice;

}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0) {
      return 0;
   }

   string strName(deviceName);

   if (strcmp(deviceName, g_AndorName) == 0) {
      return AndorCamera::GetInstance();
   }
   else if (strcmp(deviceName, g_IxonName) == 0) {
      return AndorCamera::GetInstance();
   }


   return 0;
}


///////////////////////////////////////////////////////////////////////////////
// AndorCamera constructor/destructor

AndorCamera::AndorCamera() :
initialized_(false),
busy_(false),
snapInProgress_(false),
binSize_(1),
expMs_(0.0),
driverDir_(""),
fullFrameBuffer_(0),
fullFrameX_(0),
fullFrameY_(0),
EmCCDGainLow_(0),
EmCCDGainHigh_(0),
EMSwitch_(true),
minTemp_(0),
ThermoSteady_(0),
lSnapImageCnt_(0),
currentGain_(-1),
ReadoutTime_(50),
ADChannelIndex_(0),
sequenceRunning_(false),
startTime_(0),
imageCounter_(0),  
imageTimeOut_ms_(10000),
sequenceLength_(0),
OutputAmplifierIndex_(0),
HSSpeedIdx_(0),
PreAmpGainIdx_(0),
biCamFeaturesSupported_(false),
maxTemp_(0),
myCameraID_(-1),
pImgBuffer_(0),
currentExpMS_(0.0),
cropModeSwitch_(OFF),
currentCropWidth_(64),
currentCropHeight_(64),
bFrameTransfer_(0),
stopOnOverflow_(true),
iCurrentTriggerMode_(INTERNAL),
strCurrentTriggerMode_(""),
ui_swVersion(0),
sequencePaused_(0),
countConvertMode_(""),
countConvertWavelength_(0.0),
optAcquireModeStr_(""),
optAcquireDescriptionStr_(""),
iSnapImageDelay_(0),
bSnapImageWaitForReadout_(false),
stateBeforePause_(PREPAREDFORSINGLESNAP),
metaDataAvailable_(false),
spuriousNoiseFilterControl_(nullptr),
readModeControl_(nullptr),
SRRFControl_(nullptr)
{ 
   InitializeDefaultErrorMessages();

   // add custom messages
   SetErrorText(ERR_BUSY_ACQUIRING, "Camera Busy.  Stop camera activity first.");
   SetErrorText(ERR_NO_AVAIL_AMPS, "No available amplifiers.");
   SetErrorText(ERR_TRIGGER_NOT_SUPPORTED, "Trigger Not supported.");
   SetErrorText(ERR_INVALID_VSPEED, "Invalid Vertical Shift Speed.");
   SetErrorText(ERR_INVALID_PREAMPGAIN, "Invalid Pre-Amp Gain.");
   SetErrorText(ERR_CAMERA_DOES_NOT_EXIST, "No Camera Found.  Make sure it is connected and switched on, and try again.");
   SetErrorText(DRV_NO_NEW_DATA, "No new data arrived within a reasonable time.");
   SetErrorText(ERR_SOFTWARE_TRIGGER_IN_USE, "Only one camera can use software trigger."); 
   SetErrorText(ERR_INVALID_SHUTTER_OPENTIME, g_ShutterModeOpeningTimeInvalid);
   SetErrorText(ERR_INVALID_SHUTTER_CLOSETIME, g_ShutterModeClosingTimeInvalid);
   SetErrorText(ERR_INVALID_SHUTTER_MODE, g_ShutterModeInvalid);
   SetErrorText(DRV_ERROR_NOCAMERA, "DRV_ERROR_NOCAMERA: No Camera Detected");
   SetErrorText(DRV_NOT_AVAILABLE,"DRV_NOT_AVAILABLE: Feature not available");
   SetErrorText(DRV_ERROR_PAGELOCK, "DRV_ERROR_PAGELOCK: Please ensure you have enough memory available to store the acquisition.");

   SetErrorText(ERR_INVALID_SNAPIMAGEDELAY, g_SnapImageDelayInvalid);
   SRRFImage_ = new ImgBuffer();
   seqThread_ = new AcqSequenceThread(this);

#ifdef __linux__
   hAndorDll = dlopen("libandor.so.2", RTLD_LAZY|RTLD_GLOBAL);
   if (!hAndorDll)
   {
      fprintf(stderr,"Failed to find libandor.so.2\n");
      exit(1);
   } 
   driverDir_ = "/usr/local/etc/andor/";
#endif

   if (GetListOfAvailableCameras() != DRV_SUCCESS) 
      //exit(1);
      LogMessage("No Andor camera found!");
}

AndorCamera::~AndorCamera()
{
   DriverGuard dg(this);
   delete [] fullFrameBuffer_;
   delete SRRFImage_;
   delete seqThread_;
   delete spuriousNoiseFilterControl_;
   delete readModeControl_;
   delete SRRFControl_;
   refCount_--;
   if (refCount_ == 0) {
      // release resources
      if (initialized_) {
         SetToIdle();
         int ShutterMode = 2;  //0: auto, 1: open, 2: close
         SetShutter(1, ShutterMode, 20,20);//0, 0);
      }


      if (initialized_ && mb_canSetTemp) {
         CoolerOFF();  //turn off the cooler at shutdown
      }

      if (initialized_) {
         Shutdown();
      }
      // clear the instance pointer
      instance_ = 0;
   }
#ifdef __linux__
   if (hAndorDll) dlclose(hAndorDll);
#endif
}

AndorCamera* AndorCamera::GetInstance()
{
   //if (!instance_) {
   instance_ = new AndorCamera();
   //}


   refCount_++;
   return instance_;
}


///////////////////////////////////////////////////////////////////////////////
// API methods
// ~~~~~~~~~~~

/**
* Get list of all available cameras
*/
int AndorCamera::GetListOfAvailableCameras()
{
   unsigned int ret;
   vCameraType.clear();
   NumberOfAvailableCameras_ = 0;
   ret = GetAvailableCameras(&NumberOfAvailableCameras_);
   if (ret != DRV_SUCCESS) {
      return ret;
   }
   if(NumberOfAvailableCameras_ == 0) {
      return ERR_CAMERA_DOES_NOT_EXIST;
   }

   at_32 CameraID;
   int UnknownCameraIndex = 0;
   NumberOfWorkableCameras_ = 0;
   cameraName_.clear();
   cameraID_.clear();
   for(int i=0;i<NumberOfAvailableCameras_; i++) {
      ret = GetCameraHandle(i, &CameraID);
      stringstream msg;
      msg << "Andor detected: ID " << CameraID << "\n" ;
      LogMessage(msg.str());

      if( ret == DRV_SUCCESS ) {
         ret = SetCurrentCamera(CameraID);
         if( ret == DRV_SUCCESS ) {
            ret=::Initialize(const_cast<char*>(driverDir_.c_str()));
            if( ret != DRV_SUCCESS && ret != DRV_ERROR_FILELOAD ) {
               ret = ShutDown();
            }
         }
         if( ret == DRV_SUCCESS) {
            NumberOfWorkableCameras_++;

            std::string anStr;
            char chars[255];
            ret = GetHeadModel(chars);
            if( ret!=DRV_SUCCESS ) {
               anStr = "UnknownModel";
            }
            else {
               anStr = chars;
            }
            // mm can't deal with commas!!
            size_t ifind = anStr.find(",");
            while(std::string::npos != ifind)
            {
	           anStr.replace(ifind,1,"~");
	           ifind =  anStr.find(",");
            }

            int id;
            ret = GetCameraSerialNumber(&id);
            if( ret!=DRV_SUCCESS ) {
               UnknownCameraIndex ++;
               id = UnknownCameraIndex;
            }
            sprintf(chars, "%d", id);

            std::string camType = getCameraType();
            vCameraType.push_back(camType);
            anStr = "| " + camType + " | " + anStr + " | " + chars + " |";
            cameraName_.push_back(anStr);

            cameraID_.push_back((int)CameraID);
         }

         myCameraID_ = CameraID;
      }
   }

   if(NumberOfWorkableCameras_>=1)
   {
      //camera property for multiple camera support
      //removed because list boxes in Property Browser of MM are unable to update their values after switching camera
      CPropertyAction *pAct = new CPropertyAction (this, &AndorCamera::OnCamera);
      int nRet = CreateProperty("Camera", cameraName_[NumberOfWorkableCameras_-1].c_str(), MM::String, false, pAct, true);
      assert(nRet == DEVICE_OK);
      nRet = SetAllowedValues("Camera", cameraName_);

      return DRV_SUCCESS;
   } else {
      return ERR_CAMERA_DOES_NOT_EXIST;
   }

}

   /**
   * Set camera
   */
   int AndorCamera::OnCamera(MM::PropertyBase* pProp, MM::ActionType eAct)
   {
      if (eAct == MM::AfterSet)
      {
         //added to use RTA
         SetToIdle();

         string CameraName;
         pProp->Get(CameraName);
         for (unsigned i=0; i<cameraName_.size(); ++i) {
            if (cameraName_[i].compare(CameraName) == 0)
            {
               initialized_ = false;
               myCameraID_ = cameraID_[i];
               return DEVICE_OK;
            }
         }
         assert(!"Unrecognized Camera");
      }
      else if (eAct == MM::BeforeGet) {
         // Empty path
      }
      return DEVICE_OK;
   }

   /**
   * Camera Name
   */
   int AndorCamera::OnCameraName(MM::PropertyBase* pProp, MM::ActionType eAct)
   {
      if (eAct == MM::AfterSet)
      {
      }
      else if (eAct == MM::BeforeGet)
      {
         pProp->Set(CameraName_.c_str());
      }
      return DEVICE_OK;
   }

   /**
   * iCam Features
   */
   int AndorCamera::OniCamFeatures(MM::PropertyBase* pProp, MM::ActionType eAct)
   {
      if (eAct == MM::AfterSet)
      {
      }
      else if (eAct == MM::BeforeGet)
      {
         pProp->Set(iCamFeatures_.c_str());
      }
      return DEVICE_OK;
   }

   /**
   * Temperature Range Min
   */
   int AndorCamera::OnTemperatureRangeMin(MM::PropertyBase* pProp, MM::ActionType eAct)
   {
      if (eAct == MM::AfterSet)
      {
      }
      else if (eAct == MM::BeforeGet)
      {
         pProp->Set(TemperatureRangeMin_.c_str());
      }
      return DEVICE_OK;
   }

   /**
   * Temperature Range Min
   */
   int AndorCamera::OnTemperatureRangeMax(MM::PropertyBase* pProp, MM::ActionType eAct)
   {
      if (eAct == MM::AfterSet)
      {
      }
      else if (eAct == MM::BeforeGet)
      {
         pProp->Set(TemperatureRangeMax_.c_str());
      }
      return DEVICE_OK;
   }



   /**
   * Initialize the camera.
   */
   int AndorCamera::Initialize()
   {
      if (initialized_)
         return DEVICE_OK;

      unsigned ret;
      int nRet;
      CPropertyAction *pAct;

      if(myCameraID_ == -1)
      {
         ret = 0;
         for(int i = 0; i < NumberOfWorkableCameras_; ++i) {
            myCameraID_ = cameraID_[i];
            ret = SetCurrentCamera(myCameraID_);
            if (ret == DRV_SUCCESS)
               ret = ::Initialize(const_cast<char*>(driverDir_.c_str()));
         }
         if(ret != DRV_SUCCESS) {
            return ret;
         }
      }
      else
      {
         ret = SetCurrentCamera(myCameraID_);
         if (ret != DRV_SUCCESS)
            return ret;
         ret = ::Initialize(const_cast<char*>(driverDir_.c_str()));
         if (ret != DRV_SUCCESS)
            return ret;
      }

      // Name
      int currentCameraIdx = 0;
      if(cameraID_.size()>1)
      {
         for(unsigned int i=0;i<cameraID_.size();i++)
         {
            if(cameraID_[i] == myCameraID_)
            {
               currentCameraIdx = i;
               break;
            }
         }
      }
      CameraName_ = cameraName_[currentCameraIdx];
      m_str_camType = vCameraType[currentCameraIdx];

      if(HasProperty(g_CameraInformation))
      {
         nRet = SetProperty(g_CameraInformation,cameraName_[currentCameraIdx].c_str());   
      }
      else
      {
         pAct = new CPropertyAction (this, &AndorCamera::OnCameraName);
         nRet = CreateProperty(g_CameraInformation, cameraName_[currentCameraIdx].c_str(), MM::String, true, pAct);
      }
      assert(nRet == DEVICE_OK);

      // Description
      if (!HasProperty(MM::g_Keyword_Description))
      {
         nRet = CreateProperty(MM::g_Keyword_Description, "Andor camera adapter", MM::String, true);
      }
      assert(nRet == DEVICE_OK);

      // Get various version numbers
      unsigned int eprom, cofFile, vxdRev, vxdVer, dllRev, dllVer;
      ret = GetSoftwareVersion(&eprom, &cofFile, &vxdRev, &vxdVer, &dllRev, &dllVer);
      if (ret == DRV_SUCCESS) {
         std::ostringstream verInfo;
         verInfo << "Camera version info: " << std::endl;
         verInfo << "EPROM: " << eprom << std::endl;
         verInfo << "COF File: " << cofFile << std::endl;
         verInfo << "Driver: " << vxdVer << "." << vxdRev << std::endl;
         verInfo << "DLL: " << dllVer << "." << dllRev << std::endl;
         LogMessage(verInfo.str().c_str(), false);
         ui_swVersion = (100 * dllVer) + dllRev;
      }

      // capabilities
      AndorCapabilities caps;
      caps.ulSize = sizeof(AndorCapabilities);
      ret = GetCapabilities(&caps);
      if (ret != DRV_SUCCESS)
         return ret;

	  readModeControl_			  = new ReadModeControl(this);

      ret = createTriggerProperty(&caps);
      if(ret != DRV_SUCCESS) {
         return ret;
      }

      //Set EM Gain mode
      ret = createGainProperty(&caps);
      if(ret != DRV_SUCCESS) {
         return ret;
      }

      mb_canSetTemp = ((caps.ulSetFunctions & AC_SETFUNCTION_TEMPERATURE) == AC_SETFUNCTION_TEMPERATURE);
      mb_canUseFan  = ((caps.ulFeatures & AC_FEATURES_FANCONTROL) == AC_FEATURES_FANCONTROL);

      //Output amplifier

      int numAmplifiers;
      mapAmps.clear();
      vAvailAmps.clear();
      ret = GetNumberAmp(&numAmplifiers);
      if (ret != DRV_SUCCESS)
         return ret;

      char sz_ampName[MAX_CHARS_PER_DESCRIPTION];
      for(int i = 0; i < numAmplifiers; ++i) {
         memset(sz_ampName, '\0', MAX_CHARS_PER_DESCRIPTION);
         GetAmpDesc(i, sz_ampName, MAX_CHARS_PER_DESCRIPTION);
         vAvailAmps.push_back(std::string(sz_ampName));
         mapAmps[std::string(sz_ampName)] = i;
      }

      if(numAmplifiers > 1)
      {
         if(!HasProperty(g_OutputAmplifier))
         {
            pAct = new CPropertyAction (this, &AndorCamera::OnOutputAmplifier);
            nRet = CreateProperty(g_OutputAmplifier, vAvailAmps[0].c_str(), MM::String, false, pAct);
         }
         nRet = SetAllowedValues(g_OutputAmplifier, vAvailAmps);
         assert(nRet == DEVICE_OK);
         nRet = SetProperty(g_OutputAmplifier,  vAvailAmps[0].c_str());   
         assert(nRet == DEVICE_OK);
         if (nRet != DEVICE_OK)
            return nRet;
      }

      //AD channel (pixel bitdepth)
      int numADChannels;
      ret = GetNumberADChannels(&numADChannels);
      if (ret != DRV_SUCCESS)
         return ret;

      vChannels.clear();
      for(int i = 0; i < numADChannels; ++i) {
         int depth;
         ::GetBitDepth(i, &depth);
         char * buffer = new char[MAX_CHARS_PER_DESCRIPTION];
         sprintf(buffer, "%d. %dbit",(i+1), depth);
         std::string temp(buffer);
         vChannels.push_back(temp);
         delete [] buffer;
      }
      if(numADChannels > 1)
      {

         if(!HasProperty(g_ADChannel))
         {
            pAct = new CPropertyAction (this, &AndorCamera::OnADChannel);
            nRet = CreateProperty(g_ADChannel,vChannels[0].c_str() , MM::String, false, pAct);
            assert(nRet == DEVICE_OK);
         }
         nRet = SetAllowedValues(g_ADChannel, vChannels);
         assert(nRet == DEVICE_OK);
         if (nRet != DEVICE_OK)
            return nRet;
         nRet = SetProperty(g_ADChannel,  vChannels[0].c_str());   
         if (nRet != DEVICE_OK)
            return nRet;
      }

      ret = SetADChannel(0);  //0:14bit, 1:16bit(if supported)
      if (ret != DRV_SUCCESS)
         return ret;
      ret = SetOutputAmplifier(0);  //0:EM port, 1:Conventional port
      if (ret != DRV_SUCCESS)
         return ret;

      // head model
      char model[32];
      ret = GetHeadModel(model);
      if (ret != DRV_SUCCESS)
         return ret;

      // Get detector information
      ret = GetDetector(&fullFrameX_, &fullFrameY_);
      if (ret != DRV_SUCCESS)
         return ret;

      roi_.x = 0;
      roi_.y = 0;
      roi_.xSize = fullFrameX_;
      roi_.ySize = fullFrameY_;

      binSize_ = 1;
      fullFrameBuffer_ = new short[fullFrameX_ * fullFrameY_];

      // setup image parameters
      // ----------------------
      if(iCurrentTriggerMode_ == SOFTWARE)
         ret = SetAcquisitionMode(5);// 1: single scan mode, 5: RTA
      else
         ret = SetAcquisitionMode(1);// 1: single scan mode, 5: RTA

      if (ret != DRV_SUCCESS)
         return ret;

      ret = createROIProperties(&caps);
      if (ret != DRV_SUCCESS)
         return ret;

      // binning
      if(!HasProperty(MM::g_Keyword_Binning))
      {
         pAct = new CPropertyAction (this, &AndorCamera::OnBinning);
         nRet = CreateProperty(MM::g_Keyword_Binning, "1", MM::Integer, false, pAct);
         assert(nRet == DEVICE_OK);
      }
      else
      {
         nRet = SetProperty(MM::g_Keyword_Binning,  "1");   
         if (nRet != DEVICE_OK)
            return nRet;
      }

	  PopulateBinningDropdown();

      // pixel type
      if(!HasProperty(MM::g_Keyword_PixelType))
      {
         pAct = new CPropertyAction (this, &AndorCamera::OnPixelType);
         nRet = CreateProperty(MM::g_Keyword_PixelType, g_PixelType_16bit, MM::String, false, pAct);
         assert(nRet == DEVICE_OK);
      }

      vector<string> pixelTypeValues;
      pixelTypeValues.push_back(g_PixelType_16bit);
      nRet = SetAllowedValues(MM::g_Keyword_PixelType, pixelTypeValues);
      if (nRet != DEVICE_OK)
         return nRet;
      nRet = SetProperty(MM::g_Keyword_PixelType, pixelTypeValues[0].c_str());
      if (nRet != DEVICE_OK)
         return nRet;

      // exposure
      if(!HasProperty(MM::g_Keyword_Exposure))
      {
         pAct = new CPropertyAction (this, &AndorCamera::OnExposure);
         nRet = CreateProperty(MM::g_Keyword_Exposure, "10.0", MM::Float, false, pAct);
         assert(nRet == DEVICE_OK);
      }
      else
      {
         nRet = SetProperty(MM::g_Keyword_Exposure,"10.0");
         assert(nRet == DEVICE_OK);
      }

      //Shutter
      ret = createShutterProperty(&caps);
      if(ret != DRV_SUCCESS)
         return ret;

      ret = createSnapTriggerMode();
      if(ret != DEVICE_OK)
         return ret;

      // readout mode
      int numSpeeds;
      ret = GetNumberHSSpeeds(0, 0, &numSpeeds);
      if (ret != DRV_SUCCESS)
         return ret;

      char speedBuf[100];
      readoutModes_.clear();
      for (int i=0; i<numSpeeds; i++)
      {
         float sp;
         ret = GetHSSpeed(0, 0, i, &sp); 
         if (ret != DRV_SUCCESS)
            return ret;
         sprintf(speedBuf, "%.3f MHz", sp);
         readoutModes_.push_back(speedBuf);
      }
      if (readoutModes_.empty())
         return ERR_INVALID_READOUT_MODE_SETUP;

      if(!HasProperty(MM::g_Keyword_ReadoutMode))
      {
         pAct = new CPropertyAction (this, &AndorCamera::OnReadoutMode);
         bool make_readonly = false;//numSpeeds <= 1;  
         // FMC [30/09/2009] can only set read only if 1 speed but this didn't take into consideration 
         // all combinations of amplifier/Channels.
         nRet = CreateProperty(MM::g_Keyword_ReadoutMode, readoutModes_[0].c_str(), MM::String, make_readonly, pAct);
         assert(nRet == DEVICE_OK);
      }
      nRet = SetAllowedValues(MM::g_Keyword_ReadoutMode, readoutModes_);
      nRet = SetProperty(MM::g_Keyword_ReadoutMode,readoutModes_[0].c_str());
      HSSpeedIdx_ = 0;

      // Pre-Amp-Gain
      ret = UpdatePreampGains();
      if(DRV_SUCCESS != ret)
         return ret;

      // Vertical Shift Speed
      int numVSpeed;
      ret = GetNumberVSSpeeds(&numVSpeed);
      if (ret == DRV_SUCCESS)
      {

         char VSpeedBuf[10];
         VSpeeds_.clear();
         for (int i = 0; i < numVSpeed; i++)
         {
            float vsp;
            ret = GetVSSpeed(i, &vsp);
            if (ret != DRV_SUCCESS)
               return ret;
            sprintf(VSpeedBuf, "%.2f", vsp);
            VSpeeds_.push_back(VSpeedBuf);
         }
         if (VSpeeds_.empty())
            return ERR_INVALID_VSPEED;

         if (!HasProperty(g_VerticalSpeedProperty))
         {
            pAct = new CPropertyAction(this, &AndorCamera::OnVSpeed);
            if (numVSpeed > 1)
               nRet = CreateProperty(g_VerticalSpeedProperty, VSpeeds_[numVSpeed - 1].c_str(), MM::String, false, pAct);
            else
               nRet = CreateProperty(g_VerticalSpeedProperty, VSpeeds_[numVSpeed - 1].c_str(), MM::String, true, pAct);
            assert(nRet == DEVICE_OK);
         }
         nRet = SetAllowedValues(g_VerticalSpeedProperty, VSpeeds_);
         assert(nRet == DEVICE_OK);
         nRet = SetProperty(g_VerticalSpeedProperty, VSpeeds_[VSpeeds_.size() - 1].c_str());
         assert(nRet == DEVICE_OK);
      }


      // Vertical Clock Voltage 
      int numVCVoltages;
      ret = GetNumberVSAmplitudes(&numVCVoltages);

      if(DRV_NOT_AVAILABLE != ret) //not available on all cameras (e.g. luca)
      {
         if (ret != DRV_SUCCESS) {
            numVCVoltages = 0;
            ostringstream eMsg;
            eMsg << "Andor driver returned error code: " << ret << " to GetNumberVSAmplitudes";
            LogMessage(eMsg.str().c_str(), true);
         }
         VCVoltages_.clear();
         if(ui_swVersion >= 292) {
	         for (int i = 0; i < numVCVoltages; i++)
	         {
		         char VCAmp[10];
		         ret = GetVSAmplitudeString(i, VCAmp);

		         if (ret != DRV_SUCCESS) {
			        numVCVoltages = 0;
			        ostringstream eMsg;
			        eMsg << "Andor driver returned error code: " << ret << " to GetVSAmplitudeString";
			        LogMessage(eMsg.str().c_str(), true);
		         }
		         else
		         {
			         VCVoltages_.push_back(VCAmp);
		         }
	         }
         }
         else {
	         if(numVCVoltages>5)
		        numVCVoltages = 5;
	         switch(numVCVoltages)
	         {
	         case 1:
		        VCVoltages_.push_back("Normal");
		        break;
	         case 2:
		        VCVoltages_.push_back("Normal");
		        VCVoltages_.push_back("+1");
		        break;
	         case 3:
		        VCVoltages_.push_back("Normal");
		        VCVoltages_.push_back("+1");
		        VCVoltages_.push_back("+2");
		        break;
	         case 4:
		        VCVoltages_.push_back("Normal");
		        VCVoltages_.push_back("+1");
		        VCVoltages_.push_back("+2");
		        VCVoltages_.push_back("+3");
		        break;
	         case 5:
		        VCVoltages_.push_back("Normal");
		        VCVoltages_.push_back("+1");
		        VCVoltages_.push_back("+2");
		        VCVoltages_.push_back("+3");
		        VCVoltages_.push_back("+4");
		        break;
	         default:
		        VCVoltages_.push_back("Normal");
	         }
         }
         if (numVCVoltages>=1)
         {
            if(!HasProperty("VerticalClockVoltage"))
            {
               pAct = new CPropertyAction (this, &AndorCamera::OnVCVoltage);
               if(numVCVoltages>1)
                  nRet = CreateProperty("VerticalClockVoltage", VCVoltages_[0].c_str(), MM::String, false, pAct);
               else
                  nRet = CreateProperty("VerticalClockVoltage", VCVoltages_[0].c_str(), MM::String, true, pAct);
               assert(nRet == DEVICE_OK);
            }
            nRet = SetAllowedValues("VerticalClockVoltage", VCVoltages_);
            assert(nRet == DEVICE_OK);
            nRet = SetProperty("VerticalClockVoltage", VCVoltages_[0].c_str());
            VCVoltage_ = VCVoltages_[0];
            assert(nRet == DEVICE_OK);
         }
      }

      std::string strTips("");

      // camera temperature
      // temperature range

      if(mb_canSetTemp) {
         int minTemp, maxTemp;
         ret = GetTemperatureRange(&minTemp, &maxTemp);
         if (ret != DRV_SUCCESS)
            return ret;
         minTemp_ = minTemp;
         maxTemp_ = maxTemp;
         ostringstream tMin; 
         ostringstream tMax; 
         tMin << minTemp;
         tMax << maxTemp;


         //added to show some tips
         strTips = "Wait for temperature to stabilize before acquisition.";
         if(!HasProperty("CCDTemperature Help"))
         {
            nRet = CreateProperty("CCDTemperature Help", strTips.c_str(), MM::String, true);
         }
         else
         {
            nRet = SetProperty("CCDTemperature Help", strTips.c_str());
         }
      }
      assert(nRet == DEVICE_OK);

      if(iCurrentTriggerMode_ == SOFTWARE)
      {
         strTips = "To maximize frame rate, do not change camera parameters except Exposure in Configuration Presets.";
         if(!HasProperty(" Tip2"))
         {
            nRet = CreateProperty(" Tip2", strTips.c_str(), MM::String, true);
         }
         else
         {
            nRet = SetProperty(" Tip2", strTips.c_str());
         }
         assert(nRet == DEVICE_OK);
      }

      int temp;
      ret = GetTemperature(&temp);
      ostringstream strTemp;
      strTemp<<temp;
      if(!HasProperty(MM::g_Keyword_CCDTemperature))
      {
         pAct = new CPropertyAction (this, &AndorCamera::OnTemperature);
         nRet = CreateProperty(MM::g_Keyword_CCDTemperature, strTemp.str().c_str(), MM::Integer, true, pAct);//Daigang 23-may-2007 changed back to read temperature only
      }
      else
      {
         nRet = SetProperty(MM::g_Keyword_CCDTemperature,  strTemp.str().c_str());
      }
      assert(nRet == DEVICE_OK);

      std::string strTempSetPoint;
      // Temperature Set Point
      if(mb_canSetTemp) {

         if(minTemp_ < -70) {
            strTempSetPoint = "-70";
         }
         else {
            strTempSetPoint = TemperatureRangeMin_; 
         }
         if(!HasProperty(MM::g_Keyword_CCDTemperatureSetPoint)) {
            pAct = new CPropertyAction (this, &AndorCamera::OnTemperatureSetPoint);
            nRet = CreateProperty(MM::g_Keyword_CCDTemperatureSetPoint, strTempSetPoint.c_str(), MM::Integer, false, pAct);
            ret = SetPropertyLimits(MM::g_Keyword_CCDTemperatureSetPoint, minTemp_, maxTemp_);
         }
         else {
            nRet = SetProperty(MM::g_Keyword_CCDTemperatureSetPoint, strTempSetPoint.c_str());
         }
         assert(nRet == DEVICE_OK);


         // Cooler  
         if(!HasProperty("CoolerMode"))
         {
            pAct = new CPropertyAction (this, &AndorCamera::OnCooler);
            nRet = CreateProperty("CoolerMode", g_CoolerMode_FanOffAtShutdown, MM::String, false, pAct); 
         }
         assert(nRet == DEVICE_OK);
         AddAllowedValue(/*Daigang 24-may-2007 "Cooler" */"CoolerMode", g_CoolerMode_FanOffAtShutdown);
         AddAllowedValue(/*Daigang 24-may-2007 "Cooler" */"CoolerMode", g_CoolerMode_FanOnAtShutdown);
         nRet = SetProperty("CoolerMode", g_CoolerMode_FanOffAtShutdown);
         assert(nRet == DEVICE_OK);

      }

      // Fan
      if(mb_canUseFan) {
         if(!HasProperty("FanMode"))
         {
            pAct = new CPropertyAction (this, &AndorCamera::OnFanMode);
            nRet = CreateProperty("FanMode", g_FanMode_Full, MM::String, false, pAct); 
         }
         assert(nRet == DEVICE_OK);
         AddAllowedValue("FanMode", g_FanMode_Full);
         if((caps.ulFeatures&AC_FEATURES_MIDFANCONTROL)==AC_FEATURES_MIDFANCONTROL) {
            AddAllowedValue("FanMode", g_FanMode_Low);
         }
         AddAllowedValue("FanMode", g_FanMode_Off);
         nRet = SetProperty("FanMode", g_FanMode_Full);
         assert(nRet == DEVICE_OK);
      }

      // frame transfer mode
      if(((caps.ulAcqModes & AC_ACQMODE_FRAMETRANSFER) == AC_ACQMODE_FRAMETRANSFER)
         || ((caps.ulAcqModes & AC_ACQMODE_OVERLAP) == AC_ACQMODE_OVERLAP)) {

            if(!HasProperty(m_str_frameTransferProp.c_str()))
            {
               if(m_str_camType == "Clara") {
                  m_str_frameTransferProp = "Overlap";
               }
               else {
                  m_str_frameTransferProp = "FrameTransfer";
               }
               pAct = new CPropertyAction (this, &AndorCamera::OnFrameTransfer);
               nRet = CreateProperty(m_str_frameTransferProp.c_str(), g_FrameTransferOff, MM::String, false, pAct); 
            }
            std::string str_frameTransferTip = m_str_frameTransferProp + " Help";
            std::string strHelp("Should only turn on ");
            strHelp.append(m_str_frameTransferProp).append(" if using Burst or Live mode.");
            nRet = CreateProperty(str_frameTransferTip.c_str(), strHelp.c_str(), MM::String, true);
            assert(nRet == DEVICE_OK);
            AddAllowedValue(m_str_frameTransferProp.c_str(), g_FrameTransferOff);
            AddAllowedValue(m_str_frameTransferProp.c_str(), g_FrameTransferOn);
            nRet = SetProperty(m_str_frameTransferProp.c_str(), g_FrameTransferOff);
            assert(nRet == DEVICE_OK);
      }

      // actual interval
      // used by the application to get information on the actual camera interval
      if(!HasProperty(MM::g_Keyword_ActualInterval_ms))
      {
         pAct = new CPropertyAction (this, &AndorCamera::OnActualIntervalMS);
         nRet = CreateProperty(MM::g_Keyword_ActualInterval_ms, "0.0", MM::String, true, pAct);
      }
      else
      {
         nRet = SetProperty(MM::g_Keyword_ActualInterval_ms, "0.0");
      }
      assert(nRet == DEVICE_OK);


      if(!HasProperty(MM::g_Keyword_ReadoutTime))
      {
         pAct = new CPropertyAction (this, &AndorCamera::OnReadoutTime);
         nRet = CreateProperty(MM::g_Keyword_ReadoutTime, "1", MM::Float, true, pAct);
      }
      else
      {
         nRet = SetProperty(MM::g_Keyword_ReadoutTime, "1");
      }
      assert(nRet == DEVICE_OK);

      if(!HasProperty(g_Keyword_KeepCleanTime))
      {
         pAct = new CPropertyAction (this, &AndorCamera::OnKeepCleanTime);
         nRet = CreateProperty(g_Keyword_KeepCleanTime, "1", MM::Float, true, pAct);
      }
      else
      {
         nRet = SetProperty(g_Keyword_KeepCleanTime, "1");
      }
      assert(nRet == DEVICE_OK);

   //baseline clamp
   if(caps.ulSetFunctions&AC_SETFUNCTION_BASELINECLAMP) //some camera such as Luca might not support this
      {
         if(!HasProperty("BaselineClamp"))
         {
            pAct = new CPropertyAction (this, &AndorCamera::OnBaselineClamp);
            nRet = CreateProperty("BaselineClamp", "Enabled", MM::String, false, pAct);
            assert(nRet == DEVICE_OK);
         }
         BaselineClampValues_.clear();
         BaselineClampValues_.push_back("Enabled");
         BaselineClampValues_.push_back("Disabled");
         nRet = SetAllowedValues("BaselineClamp", BaselineClampValues_);
         assert(nRet == DEVICE_OK);
         nRet = SetProperty("BaselineClamp", BaselineClampValues_[0].c_str());
         BaselineClampValue_ = BaselineClampValues_[0];
         assert(nRet == DEVICE_OK);
      }
      // CountConvert
      if(caps.ulFeatures&AC_FEATURES_COUNTCONVERT) //some cameras might not support this
      {
        if(!HasProperty("CountConvert"))
        {
           pAct = new CPropertyAction (this, &AndorCamera::OnCountConvert);
           nRet = CreateProperty("CountConvert", "Counts", MM::String, false, pAct);
           assert(nRet == DEVICE_OK);
        }
        else
        {
           nRet = SetProperty("CountConvert",  "Counts");  
           if (nRet != DEVICE_OK)
              return nRet;
        }

        SetProperty("CountConvert",  "Counts");
        vector<string> CCValues;
        CCValues.push_back("Counts");
        CCValues.push_back("Electrons");
        CCValues.push_back("Photons");
        nRet = SetAllowedValues("CountConvert", CCValues);
        
        if (nRet != DEVICE_OK)
           return nRet;
      

        // CountCOnvertWavelength
        if(!HasProperty("CountConvertWavelength"))
        {
           pAct = new CPropertyAction (this, &AndorCamera::OnCountConvertWavelength);
           nRet = CreateProperty("CountConvertWavelength", "0.0", MM::Float, false, pAct);
        }
        else
        {
           nRet = SetProperty("CountConvertWavelength", "0.0");
        }
        assert(nRet == DEVICE_OK);
      }

      spuriousNoiseFilterControl_ = new SpuriousNoiseFilterControl(this);
	
      //OptAcquire
      if(caps.ulFeatures&AC_FEATURES_OPTACQUIRE) //some cameras might not support this
      {    
         unsigned int ui_numberOfModes = 0;
         char * pc_acqModes;
         vector<string> OAModeNames;  
		   unsigned int ui_retVal = 0;
		   try 
		   {
            ui_retVal = ::OA_Initialize("C:\\userconfig.xml", (unsigned int) strlen("C:\\userconfig.xml"));
   		
			   //Get the number of available Preset modes for the current camera
            ui_retVal = ::OA_GetNumberOfPreSetModes(&ui_numberOfModes);
			   if(ui_retVal == DRV_SUCCESS && ui_numberOfModes > 0) {
			      //Allocate enough memory to hold the list of Mode names remembering to add space for the delimiter
			      pc_acqModes = static_cast<char *>(malloc((ui_numberOfModes*MAX_PATH) + (ui_numberOfModes + 1)));
			      //Get a list of Preset mode names
			      ui_retVal = OA_GetPreSetModeNames(pc_acqModes);

			      if(ui_retVal == DRV_SUCCESS) {

				      if(!HasProperty("OptAcquireMode"))
				      {
				         pAct = new CPropertyAction (this, &AndorCamera::OnOptAcquireMode);
				         nRet = CreateProperty("OptAcquireMode", "NoMode", MM::String, false, pAct);
				         assert(nRet == DEVICE_OK);
				      }
				      else
				      {
				         nRet = SetProperty("OptAcquireMode",  "NoMode");  
				         if (nRet != DEVICE_OK)
					        return nRet;
				      }
      	      
				      SetProperty("OptAcquireMode",  "NoMode"); 
				      //Add Preset mode names to list 
				      char * pc_result = strtok( pc_acqModes, "," );
				      for(unsigned int i = 0; i < ui_numberOfModes; i++){              
				         if (NULL != pc_result) {        		
					         OAModeNames.push_back(pc_result);
				         }
				         pc_result = strtok(NULL, "," );
				      }
			      }
			      nRet = SetAllowedValues("OptAcquireMode", OAModeNames);
			      free(pc_acqModes);
			   }
			   if (nRet != DEVICE_OK)
			      return nRet;
   	      
			    // Description
			   if (!HasProperty("OptAcquireMode Description"))
			   {
			      pAct = new CPropertyAction (this, &AndorCamera::OnOADescription);
			      nRet = CreateProperty("OptAcquireMode Description", "Selected OptAcquireMode Description", MM::String, true, pAct);
			   }
			   assert(nRet == DEVICE_OK);

		   } 
		   catch (...)
		   {
			   LogMessage("Caught an exception in the Andor driver while calling OA_Initialize");
		   }

      }
      
      //DMA parameters
      //if(caps.ulSetFunctions & AC_SETFUNCTION_DMAPARAMETERS)
      { 
         int NumFramesPerDMA = 1;
         float SecondsPerDMA = 0.001f;
         ret = SetDMAParameters(NumFramesPerDMA, SecondsPerDMA);
         if (DRV_SUCCESS != ret)
            return (int)ret;
      } 
      

      pAct = new CPropertyAction (this, &AndorCamera::OnTimeOut);
      nRet = CreateProperty(g_TimeOut, CDeviceUtils::ConvertToString(imageTimeOut_ms_), MM::Integer, false, pAct);

	  //SRRF
	  SRRFControl_ = new SRRFControl(this);
	  if (SRRFControl_->GetLibraryStatus() != SRRFControl::READY) {
         LogMessage(SRRFControl_->GetLastErrorString());
	  }


      // synchronize all properties
      // --------------------------
      /*
      nRet = UpdateStatus();
      if (nRet != DEVICE_OK)
      return nRet;

      // setup the buffer
      // ----------------
      nRet = ResizeImageBuffer();
      if (nRet != DEVICE_OK)
         return nRet;
      */

      // explicitely set properties which are not readable from the camera
      nRet = SetProperty(MM::g_Keyword_Binning, "1");
      if (nRet != DEVICE_OK)
         return nRet;

      if(mb_canSetTemp) {
         nRet = SetProperty(MM::g_Keyword_CCDTemperatureSetPoint, strTempSetPoint.c_str());
         if (nRet != DEVICE_OK) {
            return nRet;
         }
      }


      nRet = SetProperty(MM::g_Keyword_Exposure, "10.0");
      if (nRet != DEVICE_OK)
         return nRet;

      nRet = SetProperty(MM::g_Keyword_ReadoutMode, readoutModes_[0].c_str());
      if (nRet != DEVICE_OK)
         return nRet;
      if(mb_canUseFan) {
         nRet = SetProperty("FanMode", g_FanMode_Full);
         if (nRet != DEVICE_OK)
            return nRet;
      }

      if(((caps.ulAcqModes & AC_ACQMODE_FRAMETRANSFER) == AC_ACQMODE_FRAMETRANSFER)
         || ((caps.ulAcqModes & AC_ACQMODE_OVERLAP) == AC_ACQMODE_OVERLAP)){

            nRet = SetProperty(m_str_frameTransferProp.c_str(), g_FrameTransferOff);
            if (nRet != DEVICE_OK)
               return nRet;
      }
      if(mb_canSetTemp) {
         nRet = SetProperty("CoolerMode", g_CoolerMode_FanOffAtShutdown);
         if (nRet != DEVICE_OK) {
            return nRet;
         }
         ret = CoolerON();  //turn on the cooler at startup
         if (DRV_SUCCESS != ret) {
            return (int)ret;
         }
      }
      if(HasProperty(g_EMGainValue)) {
         if((EmCCDGainHigh_>=300) && ((caps.ulSetFunctions&AC_SETFUNCTION_EMADVANCED) == AC_SETFUNCTION_EMADVANCED))
         {
            ret = SetEMAdvanced(1);  //Enable extended range of EMGain
            if (DRV_SUCCESS != ret)
               return (int)ret;
         }

         UpdateEMGainRange();
         currentGain_ = EmCCDGainLow_;
         ret = SetEMCCDGain(static_cast<int>(currentGain_));
         if(ret != DRV_SUCCESS) {
            return (int)ret;
         }
      }

      SetDefaultVSSForUltra888WithValidSRRF();


      nRet = UpdateTimings();
      if (nRet != DRV_SUCCESS)
         return nRet;

      nRet = UpdateStatus();
      if (nRet != DEVICE_OK)
         return nRet;

      initialized_ = true;

      if (biCamFeaturesSupported_)
      {
         iCurrentTriggerMode_ = SOFTWARE;
         strCurrentTriggerMode_ = "Software";
         UpdateSnapTriggerMode();
      }

      initialiseMetaData();

      PrepareSnap();

      return DEVICE_OK;
   }

   void AndorCamera::initialiseMetaData()
   {
      AndorCapabilities caps;
      caps.ulSize = sizeof(caps);
      unsigned int ret = GetCapabilities(&caps);

      if(ret==DRV_SUCCESS && (caps.ulFeatures & AC_FEATURES_METADATA) != 0)
      {
         ret = SetMetaData(1);
         if(ret == DRV_SUCCESS)
         {
            metaDataAvailable_=true;
         }
      }
   }

   void AndorCamera::GetName(char* name) const 
   {
      CDeviceUtils::CopyLimitedString(name, g_AndorName);
   }

   /**
   * Deactivate the camera, reverse the initialization process.
   */
   int AndorCamera::Shutdown()
   {
      DriverGuard dg(this);

      int ret;

      if (initialized_)
      {
         SetToIdle();
         int ShutterMode = 2;  //0: auto, 1: open, 2: close
         SetShutter(1, ShutterMode, 20,20);//0, 0);
         if(mb_canSetTemp) {CoolerOFF();}  //Daigang 24-may-2007 turn off the cooler at shutdown
         ret = ShutDown();
      }

      initialized_ = false;
      return DEVICE_OK;
   }


   double AndorCamera::GetExposure() const
   {
      char Buf[MM::MaxStrLength];
      Buf[0] = '\0';
      GetProperty(MM::g_Keyword_Exposure, Buf);
      return atof(Buf);
   }

   void AndorCamera::SetExposure(double dExp)
   {
      SetProperty(MM::g_Keyword_Exposure, CDeviceUtils::ConvertToString(dExp));
   }


   void AndorCamera::LogStatus()
   {
      int status;
      char statStr[20];
      GetStatus(&status);
      sprintf(statStr,"%d",status);
      LogMessage(statStr,false);
   }

   unsigned AndorCamera::GetImageWidth() const
   {
      return SRRFControl_->GetSRRFEnabled() ? SRRFImage_->Width() : img_.Width(); 
   }

   unsigned AndorCamera::GetImageHeight() const
   {
      return SRRFControl_->GetSRRFEnabled() ? SRRFImage_->Height() : img_.Height();
   }

   long AndorCamera::GetImageBufferSize() const
   {
      return SRRFControl_->GetSRRFEnabled() ? 
         SRRFImage_->Width() * SRRFImage_->Height() * GetImageBytesPerPixel() :
         img_.Width() * img_.Height() * GetImageBytesPerPixel();
   }

   int AndorCamera::PrepareSnap()
   {
      DriverGuard dg(this);

      int ret;
      if (initialized_ && !sequenceRunning_ && !sequencePaused_) {
         LogMessage("PrepareSnap();",false);

         if(!IsAcquiring())
         {
            if(INTERNAL == iCurrentTriggerMode_)
            {
               AbortAcquisition();
               int status = DRV_ACQUIRING;
               int error = DRV_SUCCESS;
               while (error == DRV_SUCCESS && status == DRV_ACQUIRING) {
                  error = GetStatus(&status); 
               }
            }

            ret = ApplyROI(true);
            if(DRV_SUCCESS!=ret)
               return ret;

            if (INTERNAL != iCurrentTriggerMode_)
            {
               ret = StartAcquisition();
               if (ret != DRV_SUCCESS)
                  return ret;
            }
            else
            {
               PrepareAcquisition();
            }
         }
      }
      return DEVICE_OK;
   }


   //added to use RTA
   /**
   * Acquires a single frame.
   * Micro-Manager expects that this function blocks the calling thread until the exposure phase is over.
   * This wait is implemented either by sleeping for the exposure time.
   * or waiting until the readout event notification, this is set by the SnapImageMode property.
   */
   int AndorCamera::SnapImage()
   {
      {
         int ret;
         Log("[Snap Image] called...");
         DriverGuard dg(this);

         if (sequenceRunning_)   // If we are in the middle of a SequenceAcquisition
            return ERR_BUSY_ACQUIRING;

         if (SRRFControl_->GetSRRFEnabled())
         {
            int returnCodeFromSRRF = SRRFControl_->ApplySRRFParameters(&img_, false);
            if (returnCodeFromSRRF != AT_SRRF_SUCCESS)
            {
               return DEVICE_SNAP_IMAGE_FAILED;
            }

            return SnapImageSRRF();
         }

         ret = SnapImageNormal();

         if (DRV_SUCCESS != ret)
            return ret;
      }

      CDeviceUtils::SleepMs(iSnapImageDelay_);
      return DEVICE_OK;
   }

   int AndorCamera::SnapImageNormal()
   {
      unsigned ret = DRV_SUCCESS;
      if (iCurrentTriggerMode_ == SOFTWARE)
      {
         ret = SendSoftwareTrigger();
         if (DRV_SUCCESS != ret)
            return ret;
      }
      else if (iCurrentTriggerMode_ == INTERNAL)
      {
         ret = StartAcquisition();
         if (DRV_SUCCESS != ret)
            return ret;
      }

      if (bSnapImageWaitForReadout_)
      {
         ret = WaitForAcquisitionByHandleTimeOut(myCameraID_, imageTimeOut_ms_);
         if (DRV_SUCCESS != ret)
            return ret;
      }
      else
      {
         CDeviceUtils::SleepMs((long)(currentExpMS_ + 0.99));
      }

      return ret;
   }

   int AndorCamera::SnapImageSRRF()
   {
      unsigned ret = DRV_SUCCESS;
      if (iCurrentTriggerMode_ == SOFTWARE)
      {
         //ostringstream oss;
         ret = SendSoftwareTrigger();
         //oss << "[SnapImageSRRF] SW TRIGGER MODE Send 1st ret: " << ret << endl;
         //Log(oss.str().c_str());
         if (DRV_SUCCESS != ret)
            return ret;

         AT_SRRF_U16 numberFramesPerBurst = SRRFControl_->GetFrameBurst();
         for (int i = 1; i <= numberFramesPerBurst; ++i)
         {
            //refactor later
            ret = WaitForAcquisitionByHandleTimeOut(myCameraID_, imageTimeOut_ms_);
            //oss.str("");
            //oss << "[SnapImageSRRF] WaitForAcquisitionByHandleTimeOut returned: " << ret << " for camera: " << myCameraID_ << endl;
            //Log(oss.str().c_str());
            if (ret != DRV_SUCCESS)
               return ret;
            
            ret = (i == numberFramesPerBurst) ? DRV_SUCCESS : SendSoftwareTrigger();
            //oss.str("");
            //oss << "[SnapImageSRRF] Send [Next] Software Trigger returned: " << ret << " burst number: " << numberFramesPerBurst << endl;
            //Log(oss.str().c_str());
            if (DRV_SUCCESS != ret)
               return ret;
            
            ret = GetMostRecentImage16((WORD*)img_.GetPixelsRW(), img_.Width()*img_.Height());
            //oss.str("");
            //oss << "[SnapImageSRRF] GetMostRecentImage16 returned: " << ret << endl;
            //Log(oss.str().c_str());
            if (ret != DRV_SUCCESS)
               return ret;

            SRRFControl_->ProcessSingleFrameOnCPU(img_.GetPixelsRW(), img_.Width()*img_.Height()*img_.Depth());
            //oss.str("");
            //oss << "[SnapImageSRRF] SRRFControl_->ProcessSingleFrameOnCPU returned: " << frameBurstComplete << endl;
            //Log(oss.str().c_str());
         }
      }
      else if (iCurrentTriggerMode_ == INTERNAL)
      {
         ret = StartAcquisition();
         if (DRV_SUCCESS != ret)
            return ret;
         Log("Internal Trigger Mode - not yet supported!!!");
      }

      return DEVICE_OK;
   }

   const unsigned char* AndorCamera::GetImageBuffer()
   {
      Log("[GetImageBuffer] called...");
      if (SRRFControl_->GetSRRFEnabled())
      {
         return GetAcquiredImageSRRF();
      }

      if (IsAcquiring() && !bSnapImageWaitForReadout_)
      {
         DriverGuard dg(this);

         int ret = WaitForAcquisitionByHandleTimeOut(myCameraID_, imageTimeOut_ms_);
         if (ret != DRV_SUCCESS)
            return 0;
      } 

      const unsigned char* rawBuffer = GetAcquiredImage();

      PrepareSnap();
      return rawBuffer;
   }

   unsigned char* AndorCamera::GetAcquiredImage() {
      assert(fullFrameBuffer_ != 0);
      int array_Length = roi_.xSize/binSize_ * roi_.ySize/binSize_;

      DriverGuard dg(this);

      unsigned int ret = GetMostRecentImage16((WORD*)fullFrameBuffer_, array_Length);
      if(ret != DRV_SUCCESS) {
         std::ostringstream os;
         os << "Andor driver reports error #: " << ret;
         LogMessage(os.str().c_str(), false);
         return 0;
      }

      return (unsigned char*)fullFrameBuffer_;
   }

   const unsigned char* AndorCamera::GetAcquiredImageSRRF() {
      DriverGuard dg(this);

      AT_SRRF_U64 outputBufferSize = GetImageBufferSize();
      SRRFControl_->GetSRRFResult(SRRFImage_->GetPixelsRW(), outputBufferSize);
      PrepareSnap();
      return SRRFImage_->GetPixels();
   }

   /**
   * Readout time
   */ 
   unsigned int AndorCamera::UpdateTimings()
   {
      unsigned int ret;
      float fReadOutTime, fKeepCleanTime, fAccumTime, fExposure, fKinetic;

      ret = GetAcquisitionTimings(&fExposure, &fAccumTime, &fKinetic);
      if (DRV_SUCCESS != ret)
         return ret;

      ret = GetReadOutTime(&fReadOutTime);
      if (DRV_SUCCESS != ret)
         return ret;

      ret = GetKeepCleanTime(&fKeepCleanTime);
      if (DRV_NOT_AVAILABLE == ret)
      {
         fKeepCleanTime = 0.000f;
      }
      else if (DRV_SUCCESS != ret)
         return ret;

      //convert to ms      
      fReadOutTime *= 1000.f;
      fKeepCleanTime *= 1000.f;
      fExposure *= 1000.f;

      ReadoutTime_ = fReadOutTime;
      KeepCleanTime_ = fKeepCleanTime;

      bool externalMode = EXTERNAL == iCurrentTriggerMode_ || EXTERNALEXPOSURE == iCurrentTriggerMode_ || FASTEXTERNAL == iCurrentTriggerMode_;
      
      if(externalMode) //calculate minimum period
      {
         if(EXTERNALEXPOSURE == iCurrentTriggerMode_)
         {
            fExposure = 0.f; //set by trigger pulse
         }
      }

      if(bFrameTransfer_)
      {
         ActualInterval_ms_ = max(fExposure, fKeepCleanTime + fReadOutTime);
      }
      else
      {
         ActualInterval_ms_ = fExposure + fKeepCleanTime + fReadOutTime;
         if(AUTO == iShutterMode_ || AUTO==iInternalShutterMode_) //add shutter transfer times
         {
            ActualInterval_ms_ = ActualInterval_ms_+iShutterOpeningTime_ + iShutterClosingTime_;
         }
      }

      ActualInterval_ms_str_ = CDeviceUtils::ConvertToString((double)ActualInterval_ms_) ;
      if(externalMode)
      {
         
         if(EXTERNALEXPOSURE == iCurrentTriggerMode_ && !bFrameTransfer_)
         {
            ActualInterval_ms_str_ += " + ExternalExposureTime";
         }

         ActualInterval_ms_str_ += " (minimum)";
      }
	  
	   //SetProperty(MM::g_Keyword_Exposure, CDeviceUtils::ConvertToString(fExposure));
      OnPropertyChanged(MM::g_Keyword_ReadoutTime, CDeviceUtils::ConvertToString(ReadoutTime_));
      OnPropertyChanged(g_Keyword_KeepCleanTime, CDeviceUtils::ConvertToString(KeepCleanTime_));
      OnPropertyChanged(MM::g_Keyword_ActualInterval_ms,ActualInterval_ms_str_.c_str());

      return DRV_SUCCESS;
   }


   /**
   * Sets the image Region of Interest (ROI).
   * The internal representation of the ROI uses the full frame coordinates
   * in combination with binning factor.
   */
   int AndorCamera::SetROI(unsigned uX, unsigned uY, unsigned uXSize, unsigned uYSize)
   {
      if (SRRFControl_->GetSRRFEnabled())
      {
         uX /= SRRFControl_->GetRadiality();
         uY /= SRRFControl_->GetRadiality();
         uXSize /= SRRFControl_->GetRadiality();
         uYSize /= SRRFControl_->GetRadiality();
      }

      int ret;
      int roiPosition = -1;
      //find it in list of predefined ROIs
      customROI_.x = uX*binSize_;
      customROI_.y = uY*binSize_;
      customROI_.xSize = uXSize*binSize_;
      customROI_.ySize = uYSize*binSize_;

      for(unsigned int i=0; i<roiList.size(); i++)
      {
         ROI current = roiList[i];
         if(current.x == customROI_.x && current.y == customROI_.y && current.xSize == customROI_.xSize && current.ySize == customROI_.ySize)
         {
            roiPosition=i;
            break;
         }
      }
      if(roiPosition !=-1)
      {
         char buffer[MAX_CHARS_PER_DESCRIPTION];
         GetROIPropertyName(roiPosition, customROI_.xSize, customROI_.ySize, buffer,cropModeSwitch_ );
         ret = SetProperty(g_ROIProperty, buffer);
      }
      else
      {
         AddAllowedValue(g_ROIProperty, g_ROICustom, -1);
         ret = SetProperty(g_ROIProperty, g_ROICustom);
      }
      return ret;
   }

   unsigned AndorCamera::GetBitDepth() const
   {
      DriverGuard dg(this);

      int depth;
      unsigned ret = ::GetBitDepth(ADChannelIndex_, &depth);
      if (ret != DRV_SUCCESS)
         depth = 0;
      return depth;
   }

   int AndorCamera::GetBinning () const
   {
      return binSize_;
   }

   int AndorCamera::SetBinning (int binSize) 
   {
      ostringstream os;                                                         
      os << binSize;
      return SetProperty(MM::g_Keyword_Binning, os.str().c_str());                                                                                     
   } 

   int AndorCamera::GetROI(unsigned& uX, unsigned& uY, unsigned& uXSize, unsigned& uYSize)
   {
      uX = roi_.x / binSize_;
      uY = roi_.y / binSize_;
      uXSize = roi_.xSize / binSize_;
      uYSize = roi_.ySize / binSize_;

      return DEVICE_OK;
   }

   int AndorCamera::ClearROI()
   {
      if(readModeControl_->getCurrentMode() == FVB)
      {
         SetProperty(g_ROIProperty, g_ROIFVB);
      }
      else
      {
         SetProperty(g_ROIProperty, g_ROIFullImage);
      }
      return DEVICE_OK;
   }


   double AndorCamera::GetPixelSizeUm() const
   {
      DriverGuard dg(this);
      float x, y;
      unsigned ret = ::GetPixelSize(&x, &y);
      if (ret == DRV_SUCCESS)
      {
         return (double)x;
      }

      return GetBinning();
   }

   ///////////////////////////////////////////////////////////////////////////////
   // Action handlers
   // ~~~~~~~~~~~~~~~

   /**
   * Set the directory for the Andor native driver dll.
   */
   /*int AndorCamera::OnDriverDir(MM::PropertyBase* pProp, MM::ActionType eAct)
   {
   if (eAct == MM::BeforeGet)
   {
   pProp->Set(driverDir_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
   pProp->Get(driverDir_);
   }
   return DEVICE_OK;
   }
   */

   /**
   * Set binning.
   */
   int AndorCamera::OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct)
   {
      if (eAct == MM::AfterSet)
      {
         DriverGuard dg(this);
         if (sequenceRunning_)
            return ERR_BUSY_ACQUIRING;

         //added to use RTA
         SetToIdle();

         long bin;
         pProp->Get(bin);
         if (bin <= 0)
            return DEVICE_INVALID_PROPERTY_VALUE;

         int oldBin = binSize_;
         binSize_ = (int) bin;
         // adjust roi to accomodate the new bin size
         ROI oldRoi = roi_;
         roi_.xSize = fullFrameX_;
         roi_.ySize = fullFrameY_;
         roi_.x = 0;
         roi_.y = 0;

         // setting the binning factor will reset the image to full frame
         unsigned aret = ApplyROI(true);
         if (DRV_SUCCESS!=aret)
         {
            roi_ = oldRoi;
            binSize_ = oldBin;
            return aret;
         }

         PrepareSnap();
      }
      else if (eAct == MM::BeforeGet)
      {
         pProp->Set((long)binSize_);
      }
      return DEVICE_OK;
   }

   /**
   * Set camera exposure (milliseconds).
   */
   int AndorCamera::OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct)
   {
      // exposure property is stored in milliseconds,
      // while the driver returns the value in seconds
      if (eAct == MM::BeforeGet)
      {
         pProp->Set(currentExpMS_);
      }
      else if (eAct == MM::AfterSet)
      {
         double exp;
         pProp->Get(exp);
         

         if(fabs(exp-currentExpMS_)>0.001)
         {
            bool requiresSequenceAcquisitionStop = sequenceRunning_;
            if (requiresSequenceAcquisitionStop)
               StopSequenceAcquisition(true);
            
            bool fastExposureSupported = biCamFeaturesSupported_ && SOFTWARE == iCurrentTriggerMode_;

            if(!fastExposureSupported)
              SetToIdle();

            if (sequenceRunning_)
               return ERR_BUSY_ACQUIRING;
             
            {
               DriverGuard dg(this);
               currentExpMS_ = exp;

               unsigned ret = SetExposureTime((float)(exp / 1000.0));
               if (DRV_SUCCESS != ret)
                  return (int)ret;
               expMs_ = exp;

               UpdateTimings();
            }

            if (requiresSequenceAcquisitionStop)
               StartSequenceAcquisition(sequenceLength_ - imageCounter_, intervalMs_, stopOnOverflow_);

            if(!fastExposureSupported)
              PrepareSnap();
         }
      }
      return DEVICE_OK;
   }


   /**
   * Set camera pixel type. 
   * We support only 16-bit mode here.
   */
   int AndorCamera::OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct)
   {
      if (eAct == MM::BeforeGet)
         pProp->Set(g_PixelType_16bit);
      return DEVICE_OK;
   }

   /**
   * Set readout mode.
   */
   int AndorCamera::OnReadoutMode(MM::PropertyBase* pProp, MM::ActionType eAct)
   {
      
      if (eAct == MM::AfterSet)
      {
         bool acquiring = sequenceRunning_;
         if (acquiring)
            StopSequenceAcquisition(true);

         DriverGuard dg(this); //moved driver guard to here to allow AcqSequenceThread to terminate properly
         if (sequenceRunning_)
            return ERR_BUSY_ACQUIRING;

         //added to use RTA
         SetToIdle();

         string mode;
         pProp->Get(mode);
         for (unsigned i=0; i<readoutModes_.size(); ++i)
            if (readoutModes_[i].compare(mode) == 0)
            {
               unsigned ret = SetHSSpeed(OutputAmplifierIndex_, i);
               if (DRV_SUCCESS != ret)
                  return (int)ret;
               else
               {
                  HSSpeedIdx_ = i;
                  int retCode = UpdateTimings();
                  if (DRV_SUCCESS != retCode)
                     return retCode;

                  retCode = UpdatePreampGains();
                  if (DRV_SUCCESS != retCode)
                     return retCode;

                  if (acquiring)
                     StartSequenceAcquisition(sequenceLength_ - imageCounter_, intervalMs_, stopOnOverflow_);
                  PrepareSnap();
                  return DEVICE_OK;
               }
            }
            assert(!"Unrecognized readout mode");
      }
      else if (eAct == MM::BeforeGet)
      {
		  DriverGuard dg(this);
         pProp->Set(readoutModes_[HSSpeedIdx_].c_str());
      }
      return DEVICE_OK;
   }

   /**
   * Provides information on readout time.
   */
   int AndorCamera::OnReadoutTime(MM::PropertyBase* pProp, MM::ActionType eAct)
   {
      if (eAct == MM::BeforeGet)
      {
         pProp->Set(ReadoutTime_);
      }

      return DEVICE_OK;
   }

   int AndorCamera::OnKeepCleanTime(MM::PropertyBase* pProp, MM::ActionType eAct)
   {
      if (eAct == MM::BeforeGet)
      {
         pProp->Set(KeepCleanTime_);
      }

      return DEVICE_OK;
   }


   /**
   * Set camera "regular" gain.
   */
   int AndorCamera::OnGain(MM::PropertyBase* pProp, MM::ActionType eAct)
   {
	   
      if (eAct == MM::AfterSet)
      {
         long gain;
         pProp->Get(gain);

		 if (!EMSwitch_) {
			currentGain_ = gain;
			return DEVICE_OK;
		 }
		 if(gain == currentGain_)
			return DEVICE_OK;

         bool acquiring = sequenceRunning_;
         if (acquiring)
            StopSequenceAcquisition(true);
		 
		 {
			 DriverGuard dg(this);

			 if (sequenceRunning_)
				return ERR_BUSY_ACQUIRING;

			 if (gain!=0 && gain < (long) EmCCDGainLow_ ) 
				gain = (long)EmCCDGainLow_;
			 if (gain > (long) EmCCDGainHigh_ ) 
				gain = (long)EmCCDGainHigh_;
			 pProp->Set(gain);

			 //added to use RTA
			 if(!(iCurrentTriggerMode_ == SOFTWARE))
				SetToIdle();

			 unsigned ret = SetEMCCDGain((int)gain);
			 if (DRV_SUCCESS != ret)
				return (int)ret;
			 currentGain_ = gain;

          int retCode = UpdatePreampGains();
          if (DRV_SUCCESS != retCode)
             return retCode;

			 if (acquiring)
				StartSequenceAcquisition(sequenceLength_ - imageCounter_, intervalMs_, stopOnOverflow_);

			 PrepareSnap();
		 }
      }
      else if (eAct == MM::BeforeGet)
      {
		  DriverGuard dg(this); //not even sure this is needed
         pProp->Set(currentGain_);
      }
      return DEVICE_OK;
   }

   /**
   * Set camera "regular" gain.
   */
   int AndorCamera::OnEMSwitch(MM::PropertyBase* pProp, MM::ActionType eAct)
   {
      
      if (eAct == MM::AfterSet)
      {
         std::string EMSwitch;

         pProp->Get(EMSwitch);
         if (EMSwitch == "Off" && !EMSwitch_)
            return DEVICE_OK;
         if (EMSwitch == "On" && EMSwitch_)
            return DEVICE_OK;

         bool acquiring = sequenceRunning_;
         if (acquiring)
            StopSequenceAcquisition(true);

         {
            DriverGuard dg(this); //moved driver guard to here to allow AcqSequenceThread to terminate properly

            if (sequenceRunning_)
            return ERR_BUSY_ACQUIRING;

            //added to use RTA
            if(!(iCurrentTriggerMode_ == SOFTWARE))
            SetToIdle();

            unsigned ret = DRV_SUCCESS;
            if (EMSwitch == "On") 
            {
               ret = SetEMCCDGain((int)currentGain_);
               // Don't change EMGain property limits here -- causes errors.
               EMSwitch_ = true;
            } 
            else 
            {
               ret = SetEMCCDGain(0);
               // Don't change EMGain property limits here -- causes errors.
               EMSwitch_ = false;
            }

            //if (initialized_) {
            //  OnPropertiesChanged();
            //}

            if (DRV_SUCCESS != ret)
               return (int)ret;

            if (acquiring)
               StartSequenceAcquisition(sequenceLength_ - imageCounter_, intervalMs_, stopOnOverflow_);

            PrepareSnap();
         }
      }
      else if (eAct == MM::BeforeGet)
      {
         if (EMSwitch_)
            pProp->Set("On");
         else
            pProp->Set("Off");
      }
      return DEVICE_OK;
   }


   /**
   * Enable or Disable Software Trigger.
   */
   int AndorCamera::OnSelectTrigger(MM::PropertyBase* pProp, MM::ActionType eAct)
   {
      
      if (eAct == MM::AfterSet)
      {
         bool acquiring = sequenceRunning_;
         if (acquiring)
            StopSequenceAcquisition(true);
         
         DriverGuard dg(this); //moved driver guard to here to allow AcqSequenceThread to terminate properly

         if (sequenceRunning_)
            return ERR_BUSY_ACQUIRING;

         std::string trigger;
         pProp->Get(trigger);
         if(trigger == strCurrentTriggerMode_)
            return DEVICE_OK;

         SetToIdle();


         iCurrentTriggerMode_= GetTriggerModeInt(trigger);
         strCurrentTriggerMode_ = trigger;
         



	
		  if (acquiring)
			StartSequenceAcquisition(sequenceLength_ - imageCounter_, intervalMs_, stopOnOverflow_);
	      else
	      {
			UpdateSnapTriggerMode();
			PrepareSnap();
	      }

      }
      else if (eAct == MM::BeforeGet)
      {
         pProp->Set(strCurrentTriggerMode_.c_str());
      }
      return DEVICE_OK;
   }

   //Daigang 24-may-2007
   /**
   * Set camera pre-amp-gain.
   */
   int AndorCamera::OnPreAmpGain(MM::PropertyBase* pProp, MM::ActionType eAct)
   {
      
      if (eAct == MM::AfterSet)
      {
         bool acquiring = sequenceRunning_;
         if (acquiring)
            StopSequenceAcquisition(true);

         DriverGuard dg(this); //moved driver guard to here to allow AcqSequenceThread to terminate properly

         if (sequenceRunning_)
            return ERR_BUSY_ACQUIRING;

         //added to use RTA
         SetToIdle();

         string PreAmpGain;
         pProp->Get(PreAmpGain);
         for (unsigned i=0; i<PreAmpGains_.size(); ++i)
         {
            if (PreAmpGains_[i].compare(PreAmpGain) == 0)
            {
               unsigned ret = SetPreAmpGain(i);
               if (DRV_SUCCESS != ret)
                  return (int)ret;
               else
               {
                  if (acquiring)
                     StartSequenceAcquisition(sequenceLength_ - imageCounter_, intervalMs_, stopOnOverflow_);
                  PrepareSnap();
                  PreAmpGain_=PreAmpGain;
                  PreAmpGainIdx_ = i;
                  return DEVICE_OK;
               }
            }
         }
         assert(!"Unrecognized Pre-Amp-Gain");
      }
      else if (eAct == MM::BeforeGet)
      {
         pProp->Set(PreAmpGain_.c_str());
      }
      return DEVICE_OK;
   }
   //eof Daigang

   /**
   * Set camera Vertical Clock Voltage
   */
   int AndorCamera::OnVCVoltage(MM::PropertyBase* pProp, MM::ActionType eAct)
   {
      
      if (eAct == MM::AfterSet)
      {
         bool acquiring = sequenceRunning_;
         if (acquiring)
            StopSequenceAcquisition(true);

         DriverGuard dg(this); //moved driver guard to here to allow AcqSequenceThread to terminate properly

         if (sequenceRunning_)
            return ERR_BUSY_ACQUIRING;

         //added to use RTA
         SetToIdle();

         string VCVoltage;
         pProp->Get(VCVoltage);
         for (unsigned i=0; i<VCVoltages_.size(); ++i)
         {
            if (VCVoltages_[i].compare(VCVoltage) == 0)
            {
               unsigned ret = SetVSAmplitude(i);
               if (DRV_SUCCESS != ret)
                  return (int)ret;
               else
               {
                  if (acquiring)
                     StartSequenceAcquisition(sequenceLength_ - imageCounter_, intervalMs_, stopOnOverflow_);
                  PrepareSnap();
                  VCVoltage_=VCVoltage;
                  return DEVICE_OK;
               }
            }
         }
         assert(!"Unrecognized Vertical Clock Voltage");
      }
      else if (eAct == MM::BeforeGet)
      {
         pProp->Set(VCVoltage_.c_str());
      }
      return DEVICE_OK;
   }

   /**
   * Set camera Baseline Clamp.
   */
   int AndorCamera::OnBaselineClamp(MM::PropertyBase* pProp, MM::ActionType eAct)
   {
      
      if (eAct == MM::AfterSet)
      {
         bool acquiring = sequenceRunning_;
         if (acquiring)
            StopSequenceAcquisition(true);

         DriverGuard dg(this); //moved driver guard to here to allow AcqSequenceThread to terminate properly

         if (sequenceRunning_)
            return ERR_BUSY_ACQUIRING;

         //added to use RTA
         SetToIdle();

         string BaselineClampValue;
         pProp->Get(BaselineClampValue);
         for (unsigned i=0; i<BaselineClampValues_.size(); ++i)
         {
            if (BaselineClampValues_[i].compare(BaselineClampValue) == 0)
            {
               int iState = 1; 
               if(i==0)
                  iState = 1;  //Enabled
               if(i==1)
                  iState = 0;  //Disabled
               unsigned ret = SetBaselineClamp(iState);
               if (DRV_SUCCESS != ret)
                  return (int)ret;
               else
               {
                  if (acquiring)
                     StartSequenceAcquisition(sequenceLength_ - imageCounter_, intervalMs_, stopOnOverflow_);
                  PrepareSnap();
                  BaselineClampValue_=BaselineClampValue;
                  return DEVICE_OK;
               }
            }
         }
         assert(!"Unrecognized BaselineClamp");


      }
      else if (eAct == MM::BeforeGet)
      {
         pProp->Set(BaselineClampValue_.c_str());
      }
      return DEVICE_OK;
   }


   /**
   * Set camera vertical shift speed.
   */
   int AndorCamera::OnVSpeed(MM::PropertyBase* pProp, MM::ActionType eAct)
   {
     
      if (eAct == MM::AfterSet)
      {
         bool acquiring = sequenceRunning_;
         if (acquiring)
            StopSequenceAcquisition(true);

         DriverGuard dg(this); //moved driver guard to here to allow AcqSequenceThread to terminate properly

         if (sequenceRunning_)
            return ERR_BUSY_ACQUIRING;

         //added to use RTA
         SetToIdle();

         string VSpeed;
         pProp->Get(VSpeed);
         for (unsigned i=0; i<VSpeeds_.size(); ++i)
         {
            if (VSpeeds_[i].compare(VSpeed) == 0)
            {
               unsigned ret = SetVSSpeed(i);
               if (DRV_SUCCESS != ret)
                  return (int)ret;
               else
               {
                  ret = UpdateTimings();
                  if (DRV_SUCCESS != ret)
                  return (int)ret;
                  if (acquiring)
                     StartSequenceAcquisition(sequenceLength_ - imageCounter_, intervalMs_, stopOnOverflow_);
                  PrepareSnap();
                  VSpeed_ = VSpeed;
                  return DEVICE_OK;
               }
            }
         }
         assert(!"Unrecognized Vertical Speed");
      }
      else if (eAct == MM::BeforeGet)
      {
         pProp->Set(VSpeed_.c_str());
      }
      return DEVICE_OK;
   }

   /**
   * Obtain temperature in Celsius.
   */
   int AndorCamera::OnTemperature(MM::PropertyBase* pProp, MM::ActionType eAct)
   {
      DriverGuard dg(this);
      if (eAct == MM::AfterSet)
      {

         /* //Daigang 23-may-2007 removed for readonly
         //jizhen 05.10.2007
         long temp;
         pProp->Get(temp);
         if (temp < (long) minTemp_ ) temp = (long)minTemp_;
         if (temp > (long) maxTemp_ ) temp = (long)maxTemp_;
         unsigned ret = SetTemperature((int)temp);
         if (DRV_SUCCESS != ret)
         return (int)ret;
         ret = CoolerON();
         if (DRV_SUCCESS != ret)
         return (int)ret;
         // eof jizhen
         */
      }
      else if (eAct == MM::BeforeGet)
      {
         int temp;
         //Daigang 24-may-2007
         //GetTemperature(&temp);
         unsigned ret = GetTemperature(&temp);
         if(ret == DRV_TEMP_STABILIZED)
            ThermoSteady_ = true;
         else if(ret == DRV_TEMP_OFF || ret == DRV_TEMP_NOT_REACHED)
            ThermoSteady_ = false;
         //eof Daigang

         pProp->Set((long)temp);
      }
      return DEVICE_OK;
   }

   //Daigang 23-May-2007
   /**
   * Set temperature setpoint in Celsius.
   */
   int AndorCamera::OnTemperatureSetPoint(MM::PropertyBase* pProp, MM::ActionType eAct)
   {
      if (eAct == MM::AfterSet)
      {
         bool acquiring = sequenceRunning_;
         if (acquiring)
            StopSequenceAcquisition(true);

         DriverGuard dg(this); //moved driver guard to here to allow AcqSequenceThread to terminate properly

         if (sequenceRunning_)
            return ERR_BUSY_ACQUIRING;

         //added to use RTA
         SetToIdle();

         long temp;
         pProp->Get(temp);
         if (temp < (long) minTemp_ )
            temp = (long)minTemp_;
         if (temp > (long) maxTemp_ ) 
            temp = (long)maxTemp_;
         unsigned ret = SetTemperature((int)temp);
         if (DRV_SUCCESS != ret)
            return (int)ret;
         ostringstream strTempSetPoint;
         strTempSetPoint<<temp;
         TemperatureSetPoint_ = strTempSetPoint.str();
         if(HasProperty(g_EMGainValue)) {
            UpdateEMGainRange();
         }

         if (acquiring)
            StartSequenceAcquisition(sequenceLength_ - imageCounter_, intervalMs_, stopOnOverflow_);
         PrepareSnap();

      }
      else if (eAct == MM::BeforeGet)
      {
         pProp->Set(TemperatureSetPoint_.c_str());
      }
      return DEVICE_OK;
   }



   //jizhen 05.11.2007
   /**
   * Set cooler on/off.
   */
   int AndorCamera::OnCooler(MM::PropertyBase* pProp, MM::ActionType eAct)
   {
    
      if (eAct == MM::AfterSet)
      {
         bool acquiring = sequenceRunning_;
         if (acquiring)
            StopSequenceAcquisition(true);

         DriverGuard dg(this); //moved driver guard to here to allow AcqSequenceThread to terminate properly

         if (sequenceRunning_)
            return ERR_BUSY_ACQUIRING;

         //added to use RTA
         SetToIdle();

         string mode;
         pProp->Get(mode);
         int modeIdx = 0;
         if (mode.compare(g_CoolerMode_FanOffAtShutdown) == 0)
            modeIdx = 0;
         else if (mode.compare(g_CoolerMode_FanOnAtShutdown) == 0)
            modeIdx = 1;
         else
            return DEVICE_INVALID_PROPERTY_VALUE;

         // wait for camera to finish acquiring
         int status = DRV_IDLE;
         unsigned ret = GetStatus(&status);
         while (status == DRV_ACQUIRING && ret == DRV_SUCCESS)
            ret = GetStatus(&status);

         ret = SetCoolerMode(modeIdx);
         if (ret != DRV_SUCCESS)
            return (int)ret;

         if (acquiring)
            StartSequenceAcquisition(sequenceLength_ - imageCounter_, intervalMs_, stopOnOverflow_);
         PrepareSnap();
      }
      else if (eAct == MM::BeforeGet)
      {

      }
      return DEVICE_OK;
   }
   // eof jizhen

   //jizhen 05.16.2007
   /**
   * Set fan mode.
   */
   int AndorCamera::OnFanMode(MM::PropertyBase* pProp, MM::ActionType eAct)
   {
     
      if (eAct == MM::AfterSet)
      {
         bool acquiring = sequenceRunning_;
         if (acquiring)
            StopSequenceAcquisition(true);

         DriverGuard dg(this); //moved driver guard to here to allow AcqSequenceThread to terminate properly

         if (sequenceRunning_)
            return ERR_BUSY_ACQUIRING;

         //added to use RTA
         SetToIdle();

         string mode;
         pProp->Get(mode);
         int modeIdx = 0;
         if (mode.compare(g_FanMode_Full) == 0)
            modeIdx = 0;
         else if (mode.compare(g_FanMode_Low) == 0)
            modeIdx = 1;
         else if (mode.compare(g_FanMode_Off) == 0)
            modeIdx = 2;
         else
            return DEVICE_INVALID_PROPERTY_VALUE;

         // wait for camera to finish acquiring
         int status = DRV_IDLE;
         unsigned ret = GetStatus(&status);
         while (status == DRV_ACQUIRING && ret == DRV_SUCCESS)
            ret = GetStatus(&status);

         ret = SetFanMode(modeIdx);
         if (ret != DRV_SUCCESS)
            return (int)ret;

         if (acquiring)
            StartSequenceAcquisition(sequenceLength_ - imageCounter_, intervalMs_, stopOnOverflow_);
         PrepareSnap();
      }
      else if (eAct == MM::BeforeGet)
      {
      }
      return DEVICE_OK;
   }
   // eof jizhen

   int AndorCamera::OnInternalShutterMode(MM::PropertyBase* pProp, MM::ActionType eAct)
   {
      if (eAct == MM::AfterSet)
      {
         bool acquiring = sequenceRunning_;
         if (acquiring)
            StopSequenceAcquisition(true);
         
         DriverGuard dg(this); //moved driver guard to here to allow AcqSequenceThread to terminate properly

         if (sequenceRunning_)
            return ERR_BUSY_ACQUIRING;

         //added to use RTA
         SetToIdle();

         string mode;
         pProp->Get(mode);
         // int modeIdx = 0;
         if (mode.compare(g_ShutterMode_Auto) == 0)
            iInternalShutterMode_ = AUTO;
         else if (mode.compare(g_ShutterMode_Open) == 0)
            iInternalShutterMode_ = OPEN;
         else if (mode.compare(g_ShutterMode_Closed) == 0)
            iInternalShutterMode_ = CLOSED;
         else
            return DEVICE_INVALID_PROPERTY_VALUE;

         // wait for camera to finish acquiring
         int status = DRV_IDLE;
         unsigned ret = GetStatus(&status);
         while (status == DRV_ACQUIRING && ret == DRV_SUCCESS)
            ret = GetStatus(&status);

         ret = ApplyShutterSettings();
         if (ret != DRV_SUCCESS)
            return (int)ret;

         if (acquiring)
            StartSequenceAcquisition(sequenceLength_ - imageCounter_, intervalMs_, stopOnOverflow_);

         PrepareSnap();
      }
      else if (eAct == MM::BeforeGet)
      {
      }
      return DEVICE_OK;
   }

   int AndorCamera::OnShutterMode(MM::PropertyBase* pProp, MM::ActionType eAct)
   {
      if (eAct == MM::AfterSet)
      {
         bool acquiring = sequenceRunning_;
         if (acquiring)
            StopSequenceAcquisition(true);
         
         DriverGuard dg(this); //moved driver guard to here to allow AcqSequenceThread to terminate properly

         if (sequenceRunning_)
            return ERR_BUSY_ACQUIRING;

         //added to use RTA
         SetToIdle();

         string mode;
         pProp->Get(mode);
         // int modeIdx = 0;
         if (mode.compare(g_ShutterMode_Auto) == 0)
            iShutterMode_ = AUTO;
         else if (mode.compare(g_ShutterMode_Open) == 0)
            iShutterMode_ = OPEN;
         else if (mode.compare(g_ShutterMode_Closed) == 0)
            iShutterMode_ = CLOSED;
         else
            return DEVICE_INVALID_PROPERTY_VALUE;

         // wait for camera to finish acquiring
         int status = DRV_IDLE;
         unsigned ret = GetStatus(&status);
         while (status == DRV_ACQUIRING && ret == DRV_SUCCESS)
            ret = GetStatus(&status);

         ret = ApplyShutterSettings();
         if (ret != DRV_SUCCESS)
            return (int)ret;

         if (acquiring)
            StartSequenceAcquisition(sequenceLength_ - imageCounter_, intervalMs_, stopOnOverflow_);

         PrepareSnap();
      }
      else if (eAct == MM::BeforeGet)
      {
      }
      return DEVICE_OK;
   }
   
   int AndorCamera::OnShutterOpeningTime(MM::PropertyBase* pProp, MM::ActionType eAct)
   {
      if (eAct == MM::AfterSet)
      {
         bool acquiring = sequenceRunning_;
         if (acquiring)
            StopSequenceAcquisition(true);
         
         DriverGuard dg(this); //moved driver guard to here to allow AcqSequenceThread to terminate properly

         if (sequenceRunning_)
            return ERR_BUSY_ACQUIRING;

         //added to use RTA
         SetToIdle();

         long time;
         pProp->Get(time);

         iShutterOpeningTime_ = static_cast <int> (time);

         // wait for camera to finish acquiring
         int status = DRV_IDLE;
         unsigned ret = GetStatus(&status);
         while (status == DRV_ACQUIRING && ret == DRV_SUCCESS)
            ret = GetStatus(&status);

         ret = ApplyShutterSettings();
         if (ret != DRV_SUCCESS)
            return ret;

         if (acquiring)
            StartSequenceAcquisition(sequenceLength_ - imageCounter_, intervalMs_, stopOnOverflow_);

         PrepareSnap();
      }
      else if (eAct == MM::BeforeGet)
      {
      }
      return DEVICE_OK;
   }

    int AndorCamera::OnShutterTTL(MM::PropertyBase* pProp, MM::ActionType eAct)
   {
      if (eAct == MM::AfterSet)
      {
         bool acquiring = sequenceRunning_;
         if (acquiring)
            StopSequenceAcquisition(true);
         
         DriverGuard dg(this); //moved driver guard to here to allow AcqSequenceThread to terminate properly

         if (sequenceRunning_)
            return ERR_BUSY_ACQUIRING;

         //added to use RTA
         SetToIdle();

         string mode;
         pProp->Get(mode);
         // int modeIdx = 0;
         if (mode.compare(g_ShutterTTLHighToOpen) == 0)
            iShutterTTL_ = 1;
         else if (mode.compare(g_ShutterTTLLowToOpen) == 0)
            iShutterTTL_ = 0;
         else
            return DEVICE_INVALID_PROPERTY_VALUE;

         // wait for camera to finish acquiring
         int status = DRV_IDLE;
         unsigned ret = GetStatus(&status);
         while (status == DRV_ACQUIRING && ret == DRV_SUCCESS)
            ret = GetStatus(&status);

         ret = ApplyShutterSettings();
         if (ret != DRV_SUCCESS)
            return (int)ret;

         if (acquiring)
            StartSequenceAcquisition(sequenceLength_ - imageCounter_, intervalMs_, stopOnOverflow_);

         PrepareSnap();
      }
      else if (eAct == MM::BeforeGet)
      {
      }
      return DEVICE_OK;
   }


    int AndorCamera::OnSnapImageDelay(MM::PropertyBase* pProp, MM::ActionType eAct)
    {
      if (eAct == MM::AfterSet)
      {
         long time;
         pProp->Get(time);

         if(time<0)
         {
            iSnapImageDelay_= 0;
            pProp->Set((long)iSnapImageDelay_);
            return ERR_INVALID_SNAPIMAGEDELAY;
         }
         iSnapImageDelay_ = time;
      }
      else if (eAct == MM::BeforeGet)
      {

      }
      return DEVICE_OK;
    }

    int AndorCamera::OnSnapImageMode(MM::PropertyBase* pProp, MM::ActionType eAct)
    {
      if (eAct == MM::AfterSet)
      {
         string mode;
         pProp->Get(mode);
         if (mode.compare(g_SnapImageModeDelayForExposure) == 0)
            bSnapImageWaitForReadout_ = false;
         else if (mode.compare(g_SnapImageModeWaitForReadout) == 0)
            bSnapImageWaitForReadout_ = true;
         else
            return DEVICE_INVALID_PROPERTY_VALUE;
      }
      else if (eAct == MM::BeforeGet)
      {

      }
      return DEVICE_OK;
    }


   int AndorCamera::OnShutterClosingTime(MM::PropertyBase* pProp, MM::ActionType eAct)
   {
      if (eAct == MM::AfterSet)
      {
         bool acquiring = sequenceRunning_;
         if (acquiring)
            StopSequenceAcquisition(true);
         
         DriverGuard dg(this); //moved driver guard to here to allow AcqSequenceThread to terminate properly

         if (sequenceRunning_)
            return ERR_BUSY_ACQUIRING;

         //added to use RTA
         SetToIdle();

         long time;
         pProp->Get(time);

         iShutterClosingTime_ = static_cast <int> (time);

         // wait for camera to finish acquiring
         int status = DRV_IDLE;
         unsigned ret = GetStatus(&status);
         while (status == DRV_ACQUIRING && ret == DRV_SUCCESS)
            ret = GetStatus(&status);

         ret = ApplyShutterSettings();
         if (ret != DRV_SUCCESS)
            return ret;

         if (acquiring)
            StartSequenceAcquisition(sequenceLength_ - imageCounter_, intervalMs_, stopOnOverflow_);

         PrepareSnap();
      }
      else if (eAct == MM::BeforeGet)
      {
      }
      return DEVICE_OK;
   }

   




   int AndorCamera::OnCountConvert(MM::PropertyBase* pProp, MM::ActionType eAct)
   {
      if (eAct == MM::AfterSet)
      { 
         bool acquiring = sequenceRunning_;
         if (acquiring)
            StopSequenceAcquisition(true);

         if (sequenceRunning_)
            return ERR_BUSY_ACQUIRING;

         string countConvertModeStr;
         long countConvertMode = 0;
         pProp->Get(countConvertModeStr);
         
         if(countConvertModeStr.compare("Counts") == 0)
         {
           countConvertMode = 0;
         }
         else if(countConvertModeStr.compare("Electrons") == 0)
         {
           countConvertMode = 1;
         }
         else if(countConvertModeStr.compare("Photons") == 0)
         {
           countConvertMode = 2;
         }
         if(countConvertModeStr == countConvertMode_)
            return DEVICE_OK;          

         //added to use RTA
         SetToIdle();

         unsigned int ret = SetCountConvertMode(countConvertMode);
         if (ret != DRV_SUCCESS)
         {           
            return DEVICE_CAN_NOT_SET_PROPERTY;
         }
        
         pProp->Set(countConvertModeStr.c_str());
         countConvertMode_ = countConvertModeStr;
         if (acquiring)
            StartSequenceAcquisition(sequenceLength_ - imageCounter_, intervalMs_, stopOnOverflow_);
         PrepareSnap();
      }
      else if (eAct == MM::BeforeGet)
      {
         pProp->Set(countConvertMode_.c_str());
      }
      return DEVICE_OK;
   }

   int AndorCamera::OnCountConvertWavelength(MM::PropertyBase* pProp, MM::ActionType eAct)
   {
      if (eAct == MM::AfterSet)
      {
         bool acquiring = sequenceRunning_;
         if (acquiring)
            StopSequenceAcquisition(true);

         if (sequenceRunning_)
            return ERR_BUSY_ACQUIRING;

         //added to use RTA
         SetToIdle();

         double countConvertWavelength = 0;
         pProp->Get(countConvertWavelength);
         
         if(countConvertWavelength == countConvertWavelength_)
            return DEVICE_OK;
          
         // wait for camera to finish acquiring
         int status = DRV_IDLE;
         unsigned int ret = GetStatus(&status);
         while (status == DRV_ACQUIRING && ret == DRV_SUCCESS)
            ret = GetStatus(&status);

         ret  = SetCountConvertWavelength(static_cast<float>(countConvertWavelength));
         if (ret != DRV_SUCCESS)
         {           
            return DEVICE_CAN_NOT_SET_PROPERTY;
         }
        
         pProp->Set(countConvertWavelength);
         countConvertWavelength_ = countConvertWavelength;
         if (acquiring)
            StartSequenceAcquisition(sequenceLength_ - imageCounter_, intervalMs_, stopOnOverflow_);
         PrepareSnap();
      }
      else if (eAct == MM::BeforeGet)
      {
         pProp->Set(countConvertWavelength_);
      }
      return DEVICE_OK;
   }  

   int AndorCamera::OnOADescription(MM::PropertyBase* pProp, MM::ActionType eAct)
   {
      if (eAct == MM::BeforeGet)
      {
        pProp->Set(optAcquireDescriptionStr_.c_str());
      }

      return DEVICE_OK;
   }

   int AndorCamera::OnOptAcquireMode(MM::PropertyBase* pProp, MM::ActionType eAct)
   {
      if (eAct == MM::AfterSet)
      {
         bool acquiring = sequenceRunning_;
         if (acquiring)
            StopSequenceAcquisition(true);

         if (sequenceRunning_)
            return ERR_BUSY_ACQUIRING;

         //added to use RTA
         SetToIdle();

         string optAcquireModeStr;
         pProp->Get(optAcquireModeStr);
         
         if(optAcquireModeStr == optAcquireModeStr_)
            return DEVICE_OK;

          // wait for camera to finish acquiring
         int status = DRV_IDLE;
         unsigned int ret = GetStatus(&status);
         while (status == DRV_ACQUIRING && ret == DRV_SUCCESS)
            ret = GetStatus(&status);

         ret = OA_EnableMode(optAcquireModeStr.c_str());
         if (ret != DRV_SUCCESS)
         {           
            return DEVICE_CAN_NOT_SET_PROPERTY;
         }
        
         pProp->Set(optAcquireModeStr.c_str());
         optAcquireModeStr_ = optAcquireModeStr;
         if (acquiring)
            StartSequenceAcquisition(sequenceLength_ - imageCounter_, intervalMs_, stopOnOverflow_);
         PrepareSnap();
         UpdateOAParams(optAcquireModeStr.c_str());

      }
      else if (eAct == MM::BeforeGet)
      {
         pProp->Set(optAcquireModeStr_.c_str());
      }
      
      
      return DEVICE_OK;
   }
   

   ///////////////////////////////////////////////////////////////////////////////
   // Utility methods
   ///////////////////////////////////////////////////////////////////////////////

   int AndorCamera::ResizeImageBuffer()
   {
      // resize internal buffers
      // NOTE: we are assuming 16-bit pixel type
      const int bytesPerPixel = 2;
      img_.Resize(roi_.xSize / binSize_, roi_.ySize / binSize_, bytesPerPixel);

      unsigned int radiality = SRRFControl_->GetRadiality();
      ResizeSRRFImage(radiality);

      return DEVICE_OK;
   }
   
   void AndorCamera::ResizeSRRFImage(long radiality)
   {
      SRRFImage_->Resize(img_.Width()*radiality, img_.Height()*radiality, img_.Depth());
   }

   void AndorCamera::UpdateOAParams(const char* OAModeName)
   {
      unsigned int ui_retVal;
      int i_temp;
      float f_temp;
      char c_temp[MAX_CHARS_PER_OA_DESCRIPTION];
      memset(c_temp, '\0', MAX_CHARS_PER_OA_DESCRIPTION);  

      ui_retVal = ::OA_GetString(OAModeName, "mode_description", &c_temp[0], MAX_CHARS_PER_OA_DESCRIPTION);
      optAcquireDescriptionStr_ = c_temp;
      SetProperty("OptAcquireMode Description", c_temp);  
      ui_retVal = ::OA_GetString(OAModeName, "frame_transfer", &c_temp[0], MAX_CHARS_PER_OA_DESCRIPTION);
      if (0 == stricmp(c_temp, "ON")){
        SetProperty("FrameTransfer", "On");  
      } 
      else {
        SetProperty("FrameTransfer", "Off");  
      }

      ui_retVal = ::OA_GetInt(OAModeName, "electron_multiplying_gain", &i_temp);
      sprintf(c_temp, "%d", i_temp);

      SetProperty("Gain",c_temp);  
      ui_retVal = ::OA_GetInt(OAModeName, "readout_rate", &i_temp);
      f_temp = static_cast<float>(i_temp);

      sprintf(c_temp, "%.3f MHz", f_temp);

      //check if Readout rate is valid
      int numADChannels;
      ui_retVal = GetNumberADChannels(&numADChannels);

      for(int i = 0; i < numADChannels; ++i) {
        char * buffer = new char[MAX_CHARS_PER_DESCRIPTION];
        int depth;
        ::GetBitDepth(i, &depth);
        sprintf(buffer, "%d. %dbit",(i+1), depth);

        char speedBuf[MAX_CHARS_PER_DESCRIPTION];
        int numSpeeds;
        unsigned ret = GetNumberHSSpeeds(i, OutputAmplifierIndex_, &numSpeeds);
        for (int j=0; j<numSpeeds; j++)
        {
           float sp;
           ret = GetHSSpeed(i, OutputAmplifierIndex_, j, &sp);
           sprintf(speedBuf, "%.3f MHz", sp);
           if(0 == stricmp(c_temp, speedBuf)){
             SetProperty("AD_Converter", buffer);
             SetProperty("ReadoutMode", speedBuf);
             break;
           }
        }
        delete [] buffer;
      }

      ui_retVal = ::OA_GetString(OAModeName, "output_amplifier", &c_temp[0], MAX_CHARS_PER_OA_DESCRIPTION);
      SetProperty("Output_Amplifier", c_temp);  

      ui_retVal = ::OA_GetInt(OAModeName, "vertical_clock_amplitude", &i_temp);
      if(i_temp == 0) {
        SetProperty("VerticalClockVoltage", "Normal");
      }
      else {
        sprintf(c_temp, "+%d", i_temp);
        SetProperty("VerticalClockVoltage", c_temp);  
      }
      ui_retVal = ::OA_GetFloat(OAModeName, "preamplifier_gain", &f_temp);
      sprintf(c_temp, "%.2f", f_temp);
      SetProperty("Pre-Amp-Gain", c_temp);  
      ui_retVal = ::OA_GetFloat(OAModeName, "shift_speed", &f_temp);
      sprintf(c_temp, "%.2f", f_temp);
      SetProperty(g_VerticalSpeedProperty, c_temp);
   }


   void AndorCamera::UpdateEMGainRange()
   {
      DriverGuard dg(this);
      SetToIdle();

      int EmCCDGainLow, EmCCDGainHigh;
      unsigned ret = GetEMGainRange(&EmCCDGainLow, &EmCCDGainHigh);
      if (ret != DRV_SUCCESS)
         return;
      EmCCDGainLow_ = EmCCDGainLow;
      EmCCDGainHigh_ = EmCCDGainHigh;
      ostringstream emgLow; 
      ostringstream emgHigh; 
      emgLow << EmCCDGainLow;
      emgHigh << EmCCDGainHigh;

      ret = SetPropertyLimits(g_EMGainValue, EmCCDGainLow, EmCCDGainHigh);

      PrepareSnap();

      if (ret != DEVICE_OK)
         return;

   }


   /**
   * EMGain Range Max
   */
   int AndorCamera::OnEMGainRangeMax(MM::PropertyBase* pProp, MM::ActionType eAct)
   {
      if (eAct == MM::AfterSet)
      {
      }
      else if (eAct == MM::BeforeGet)
      {
         pProp->Set((long)EmCCDGainHigh_);
      }
      return DEVICE_OK;
   }


   /**
   * ActualInterval_ms
   */
   int AndorCamera::OnActualIntervalMS(MM::PropertyBase* pProp, MM::ActionType eAct)
   {
      if (eAct == MM::BeforeGet)
      {
         pProp->Set(ActualInterval_ms_str_.c_str());
      }
      return DEVICE_OK;
   }

   int AndorCamera::OnROI(MM::PropertyBase* pProp, MM::ActionType eAct)
   {
      if (eAct == MM::AfterSet)
      {

         ROI oldRoi = roi_;
         
         long data;
         GetCurrentPropertyData(g_ROIProperty, data);

         if(-1!=data) //dropdown option
         {
            if(data >= 0 && data < (long)roiList.size())
            {
               roi_ = roiList[data];
            }
         }
         else //its a custom ROI
         {
            roi_ = customROI_;
         }

         bool acquiring = sequenceRunning_;
         if (acquiring) {
            StopSequenceAcquisition(true);
         }

         {
            DriverGuard dg(this);
 
            SetToIdle();

            if (Busy())
               return ERR_BUSY_ACQUIRING;

            
            unsigned int ret = ApplyROI(!acquiring);
            if(DRV_SUCCESS!=ret)
            {
               roi_ = oldRoi;
               return ret;
            }

            if (acquiring)
               StartSequenceAcquisition(sequenceLength_ - imageCounter_, intervalMs_, stopOnOverflow_);

            PrepareSnap();
         }
      }
      else if (eAct == MM::BeforeGet)
      {
         if(readModeControl_->getCurrentMode() == FVB)
         {
            pProp->Set(g_ROIFVB);
         }
         else
         {
            //check if current ROI is on list
            int roiPosition = -1;
            for(unsigned int i=0; i<roiList.size(); i++)
            {
               ROI current = roiList[i];
               if(current.x == roi_.x && current.y == roi_.y && current.xSize == roi_.xSize && current.ySize == roi_.ySize)
               {
                  roiPosition=i;
                  break;
               }
            }
		      if(roiPosition !=-1)
            {
               char buffer[MAX_CHARS_PER_DESCRIPTION];
               GetROIPropertyName(roiPosition, roi_.xSize, roi_.ySize, buffer,cropModeSwitch_);
               pProp->Set(buffer);
            }
            else
            {
               pProp->Set(g_ROICustom);
            }
         }
      }
      return DEVICE_OK;
   }


   /**
   * EMGain Range Max
   */
   int AndorCamera::OnEMGainRangeMin(MM::PropertyBase* pProp, MM::ActionType eAct)
   {
      if (eAct == MM::AfterSet)
      {
      }
      else if (eAct == MM::BeforeGet)
      {
         pProp->Set((long)EmCCDGainLow_);
      }
      return DEVICE_OK;
   }


   int AndorCamera::OnTimeOut(MM::PropertyBase* pProp, MM::ActionType eAct)
   {
      if (eAct == MM::AfterSet)
      {
         long imageTimeOut_ms;
         pProp->Get(imageTimeOut_ms);
         if(imageTimeOut_ms == imageTimeOut_ms_)
            return DEVICE_OK;
         pProp->Set(imageTimeOut_ms);
         imageTimeOut_ms_ = imageTimeOut_ms;
      }
      else if (eAct == MM::BeforeGet)
      {
         pProp->Set(CDeviceUtils::ConvertToString(imageTimeOut_ms_));
      }
      return DEVICE_OK;
   }


   /**
   * Frame transfer mode ON or OFF.
   */
   int AndorCamera::OnFrameTransfer(MM::PropertyBase* pProp, MM::ActionType eAct)
   {
      
      if (eAct == MM::AfterSet)
      {
         bool bOldFTMode = bFrameTransfer_;
         string mode;
         pProp->Get(mode);
         int modeIdx = 0;
         if (mode.compare(g_FrameTransferOn) == 0)
         {
            modeIdx = 1;
            bFrameTransfer_ = true;
         }
         else if (mode.compare(g_FrameTransferOff) == 0)
         {
            modeIdx = 0;
            bFrameTransfer_ = false;
         }
         else 
         {
            return DEVICE_INVALID_PROPERTY_VALUE;
         }

            

         if(bOldFTMode != bFrameTransfer_) 
         {
            bool acquiring = sequenceRunning_;
            if (acquiring) {
               StopSequenceAcquisition(true);
            }
         
            {
               DriverGuard dg(this); //moved driver guard to here to allow AcqSequenceThread to terminate properly

               if (sequenceRunning_) {
                  return ERR_BUSY_ACQUIRING;
               }


               if (bFrameTransfer_ == false) {
                  SetProperty(g_cropMode, "Off");
               }
                  
               SetToIdle();

               unsigned int ret = SetFrameTransferMode(modeIdx);
               if (ret != DRV_SUCCESS) {
                  return ret;
               }
               int noAmps;
               ret = ::GetNumberAmp(&noAmps);
               if (ret != DRV_SUCCESS) {
                  return ret;
               }

               
               if(HasProperty(g_OutputAmplifier)) 
               {
                  bool changeAmp(false);
                  if("Clara" == m_str_camType) 
                  {

                     std::map<std::string, int>::iterator iter, iterLast;
                     iterLast = mapAmps.end();
                     vAvailAmps.clear();
                     for(iter = mapAmps.begin(); iter != iterLast; ++iter) {
                        unsigned int status = IsAmplifierAvailable(iter->second);
                        if(status == DRV_SUCCESS) {
                           vAvailAmps.push_back(iter->first);
                        }
                        else {
                           if(OutputAmplifierIndex_ == iter->second) {
                              changeAmp = true;
                           }
                        }
                     }
                  
                    SetAllowedValues(g_OutputAmplifier, vAvailAmps);
                    UpdateProperty(g_OutputAmplifier);

                    if(changeAmp) {
                       if(vAvailAmps.size() > 0) {
                          OutputAmplifierIndex_ = mapAmps[vAvailAmps[0]];
                          int nRet = SetProperty(g_OutputAmplifier,  vAvailAmps[0].c_str());   
                          assert(nRet == DEVICE_OK);
                          if (nRet != DEVICE_OK) {
                             return nRet;
                          }
                       }
                       else {
                          return ERR_NO_AVAIL_AMPS;
                       }
                    }
                  }
                  UpdateHSSpeeds();
                  ret = UpdatePreampGains();
                  if(DRV_SUCCESS!=ret)
                     return ret;

                  
               }

               if (acquiring)
                     StartSequenceAcquisition(sequenceLength_ - imageCounter_, intervalMs_, stopOnOverflow_);

               PrepareSnap();
            }
         }
      }
      else if (eAct == MM::BeforeGet)
      {
         // use cached value
      }
      return DEVICE_OK;
   }



   /**
   * Set caemra to idle
   */
   void AndorCamera::SetToIdle()
   {
      if(!initialized_ || !IsAcquiring())
         return;
      unsigned ret = AbortAcquisition();
      if (ret != DRV_SUCCESS)
         CheckError(ret);

     int status = DRV_ACQUIRING;
     int error = DRV_SUCCESS;
     while (error == DRV_SUCCESS && status == DRV_ACQUIRING) {
       error = GetStatus(&status); 
     }
   }


   /**
   * check if camera is acquiring
   */
   bool AndorCamera::IsAcquiring()
   {
      DriverGuard dg(this);
      if(!initialized_)
         return 0;

      int status = DRV_IDLE;
      GetStatus(&status);
      if (status == DRV_ACQUIRING)
         return true;
      else
         return false;

   }



   /**
   * check if camera is thermosteady
   */
   bool AndorCamera::IsThermoSteady()
   {
      return ThermoSteady_;
   }

   void AndorCamera::CheckError(unsigned int /*errorVal*/)
   {
   }

   /**
   * Set output amplifier.
   */
   int AndorCamera::OnOutputAmplifier(MM::PropertyBase* pProp, MM::ActionType eAct)
   {
      
      if (eAct == MM::AfterSet)
      {
         string strAmp;
         pProp->Get(strAmp);
         if(strAmp.compare(strCurrentAmp) != 0 ) {
            strCurrentAmp = strAmp;
            OutputAmplifierIndex_ = mapAmps[strAmp];

            bool acquiring = sequenceRunning_;
            if (acquiring)
               StopSequenceAcquisition(true);

            DriverGuard dg(this); //moved driver guard to here to allow AcqSequenceThread to terminate properly

            if (sequenceRunning_)
               return ERR_BUSY_ACQUIRING;

            SetToIdle();


            unsigned ret = SetOutputAmplifier(OutputAmplifierIndex_);
            if (ret != DRV_SUCCESS) {
               return (int)ret;
            }

            UpdateHSSpeeds();
            int retCode = UpdatePreampGains();
            if(DRV_SUCCESS!=retCode)
               return retCode;

            if (acquiring) {
               StartSequenceAcquisition(sequenceLength_ - imageCounter_, intervalMs_, stopOnOverflow_);
            }
            PrepareSnap();
            return DEVICE_OK;

         }
      }
      else if (eAct == MM::BeforeGet)
      {
      }
      return DEVICE_OK;
   }



   /**
   * Set output amplifier.
   */
   int AndorCamera::OnADChannel(MM::PropertyBase* pProp, MM::ActionType eAct)
   {
      DriverGuard dg(this);
      if (eAct == MM::AfterSet)
      {
         bool acquiring = sequenceRunning_;
         if (acquiring) {
            StopSequenceAcquisition(true);
         }
         if (sequenceRunning_) {
            return ERR_BUSY_ACQUIRING;
         }

         SetToIdle();

         string strADChannel;
         pProp->Get(strADChannel);
         int ADChannelIdx = 0;
         if(strCurrentChannel.compare(strADChannel) != 0) {
            if (strADChannel.compare(vChannels[0]) == 0) {
               ADChannelIdx = 0;
            }
            else if (strADChannel.compare(vChannels[1]) == 0) {
               ADChannelIdx = 1;
            }
            else {
               return DEVICE_INVALID_PROPERTY_VALUE;
            }

            ADChannelIndex_ = ADChannelIdx;

            unsigned int ret = SetADChannel(ADChannelIdx);
            if (ret != DRV_SUCCESS) {
               return (int)ret;
            }

            if(HasProperty(g_OutputAmplifier)) 
            {
               bool changeAmp(false);
               if(ui_swVersion > 283) {
                  std::map<std::string, int>::iterator iter, iterLast;
                  iterLast = mapAmps.end();
                  vAvailAmps.clear();
                  for(iter = mapAmps.begin(); iter != iterLast; ++iter) {
                     unsigned int status = IsAmplifierAvailable(iter->second);
                     if(status == DRV_SUCCESS) {
                        vAvailAmps.push_back(iter->first);
                     }
                     else {
                        if(OutputAmplifierIndex_ == iter->second) {
                           changeAmp = true;
                        }
                     }
                  }
               }
               int nRet = SetAllowedValues(g_OutputAmplifier, vAvailAmps);
               if (nRet != DEVICE_OK) {
                  return nRet;
               }

               if(changeAmp) {
                  if(vAvailAmps.size() > 0) {
                     OutputAmplifierIndex_ = mapAmps[vAvailAmps[0]];
                     nRet = SetProperty(g_OutputAmplifier,  vAvailAmps[0].c_str());   
                     assert(nRet == DEVICE_OK);
                     if (nRet != DEVICE_OK) {
                        return nRet;
                     }
                  }
                  else {
                     return ERR_NO_AVAIL_AMPS;
                  }
               }

               
            }
            ret = UpdatePreampGains();
            if(DRV_SUCCESS != ret)
               return ret;

            UpdateHSSpeeds();
            
            if (initialized_) {
               OnPropertiesChanged();
            }

            if (acquiring) {
               StartSequenceAcquisition(sequenceLength_ - imageCounter_, intervalMs_, stopOnOverflow_);
            }
            PrepareSnap();
            return DEVICE_OK;

         }
      }
      else if (eAct == MM::BeforeGet) {
      }
      return DEVICE_OK;
   }


   void AndorCamera::UpdateHSSpeeds()
   {
      int numSpeeds;
      unsigned ret = GetNumberHSSpeeds(ADChannelIndex_, OutputAmplifierIndex_, &numSpeeds);
      if (ret != DRV_SUCCESS)
         return;

      char speedBuf[100];
      readoutModes_.clear();
      for (int i=0; i<numSpeeds; i++)
      {
         float sp;
         ret = GetHSSpeed(ADChannelIndex_, OutputAmplifierIndex_, i, &sp); 
         if (ret != DRV_SUCCESS)
            return;
         sprintf(speedBuf, "%.3f MHz", sp);
         readoutModes_.push_back(speedBuf);
      }
      SetAllowedValues(MM::g_Keyword_ReadoutMode, readoutModes_);

      if(HSSpeedIdx_ >= (int)readoutModes_.size())
      {
         HSSpeedIdx_ = 0;
      }
      ret = SetHSSpeed(OutputAmplifierIndex_, HSSpeedIdx_);
      if (ret == DRV_SUCCESS)
         SetProperty(MM::g_Keyword_ReadoutMode,readoutModes_[HSSpeedIdx_].c_str());

      UpdateTimings();

   }

   /*
      Updates the PreAmpGains List with the allowed preamp gains for the current settings, 
      if an invalid PAG is selected a valid preamp gain is selected.  
   */
   int AndorCamera::UpdatePreampGains()
   {
         int ret, nRet;
         int numPreAmpGain;
         int gainAvailable;
         int numAvailGains;

         bool acquiring = IsAcquiring();
         if(acquiring) 
            SetToIdle();

         ret = GetNumberPreAmpGains(&numPreAmpGain);
         if (ret != DRV_SUCCESS)
            return ret; 
         
         if (numPreAmpGain > 0 ) 
         {
            //Repopulate List
            const int PreAmpGainBufLength = 30;
            char PreAmpGainBuf[PreAmpGainBufLength];
            PreAmpGains_.clear();
            numAvailGains = 0;
        
            for (int i=0; i<numPreAmpGain; i++)
            {
               gainAvailable;
               ret = IsPreAmpGainAvailable(ADChannelIndex_, OutputAmplifierIndex_, HSSpeedIdx_, i, &gainAvailable);
               if(DRV_SUCCESS != ret)
                  return ret;

               if(1 == gainAvailable)
               {
                  numAvailGains++;
                  GetPreAmpGainString(i,PreAmpGainBuf,PreAmpGainBufLength);
                  PreAmpGains_.push_back(PreAmpGainBuf);
               }
            }
            if (PreAmpGains_.empty())
               return ERR_INVALID_PREAMPGAIN;

            if(!HasProperty("Pre-Amp-Gain"))
            {
               CPropertyAction *pAct = new CPropertyAction (this, &AndorCamera::OnPreAmpGain);
               if(numPreAmpGain>1)
                     nRet = CreateProperty("Pre-Amp-Gain", PreAmpGains_[numAvailGains-1].c_str(), MM::String, false, pAct);
               else
                     nRet = CreateProperty("Pre-Amp-Gain", PreAmpGains_[numAvailGains-1].c_str(), MM::String, true, pAct);
               assert(nRet == DEVICE_OK);
            }
            nRet = SetAllowedValues("Pre-Amp-Gain", PreAmpGains_);
            if(DRV_SUCCESS != ret)
               return ret;


            //Check PAG index is valid selection
            ret = IsPreAmpGainAvailable(ADChannelIndex_, OutputAmplifierIndex_, HSSpeedIdx_, PreAmpGainIdx_, &gainAvailable);
            if(DRV_P4INVALID == ret) //index out of range
               gainAvailable = 0;
            else if(DRV_SUCCESS!=ret)
               return ret;

            //also check to see that the value of the PAG is still the original value
            GetPreAmpGainString(PreAmpGainIdx_,PreAmpGainBuf,PreAmpGainBufLength);
            if(0!=PreAmpGain_.compare( PreAmpGainBuf)) 
               gainAvailable = 0;

            if(0 == gainAvailable) 
            {
               for(int i=numPreAmpGain-1; i>=0; i--) //find a valid PAG
               {
                  ret = IsPreAmpGainAvailable(ADChannelIndex_, OutputAmplifierIndex_, HSSpeedIdx_, i, &gainAvailable);
                  if(1 == gainAvailable)
                  {
                     PreAmpGainIdx_ = i;

                     GetPreAmpGainString(i,PreAmpGainBuf,PreAmpGainBufLength);
                     PreAmpGain_ = PreAmpGainBuf;

                     SetProperty("Pre-Amp-Gain",PreAmpGain_.c_str());
                     SetPreAmpGain(PreAmpGainIdx_);
                     break;
                  }
               }
            }
         }
          if(acquiring) 
            PrepareSnap();
      return DRV_SUCCESS;
   }

    int AndorCamera::UpdateExposureFromCamera()
   {
      float actualExp, actualAccumulate, actualKinetic;
      unsigned int ret;
      DriverGuard dg(this);
      ret = GetAcquisitionTimings(&actualExp, &actualAccumulate, &actualKinetic);
      if (DRV_SUCCESS != ret)
         return (int)ret;

      if(fabs(currentExpMS_ - actualExp*1000.0f)>0.01)
      {
         currentExpMS_ = actualExp*1000.0f;
         OnPropertyChanged(MM::g_Keyword_Exposure, CDeviceUtils::ConvertToString(currentExpMS_));
         GetCoreCallback()->OnExposureChanged(this, currentExpMS_);
      }
      return DRV_SUCCESS;
   }

   int AndorCamera::GetPreAmpGainString(int PreAmpGainIdx, char * PreAmpGainString, int PreAmpGainStringLength )
   {
      bool useText = ui_swVersion >= 292;
      int ret = DRV_NOT_SUPPORTED;
      

      if(useText) 
      {
         ret = GetPreAmpGainText(PreAmpGainIdx, PreAmpGainString, PreAmpGainStringLength);
         if(DRV_NOT_SUPPORTED == ret)
            useText = false;
      }
                  
      if(!useText) 
      {
         float pag;
         ret = GetPreAmpGain(PreAmpGainIdx, &pag); 
         sprintf(PreAmpGainString, "%.2f", pag);
      }
      if (ret != DRV_SUCCESS)
         return ret;

      return DRV_SUCCESS;
   }

void AndorCamera::CalculateAndSetupCameraImageBuffer(at_u32 & width, at_u32 & height, at_u32 & bytesPerPixel)
{
   width = img_.Width();
   height = img_.Height();
   bytesPerPixel = img_.Depth();
   at_u32 imagesPerDMA = MAX_IMAGES_PER_DMA;
   GetImagesPerDMA(&imagesPerDMA);
   cameraBuffer_ = new ImgBuffer(width * imagesPerDMA, height, bytesPerPixel);
   SetCameraImageBuffer(cameraBuffer_->GetPixelsRW());
}

int AndorCamera::GetCameraAcquisitionProgress(at_32* series)
{
   DriverGuard dg(this);
   at_32 acc = 0;
   int ret = GetAcquisitionProgress(&acc, series);
   //ostringstream os("GetAcquisitionProgress (retCode): ");
   //os << ret << " | returned (series progress): " << *series << endl;
   //GetCoreCallback()->LogMessage(this, os.str().c_str(), true);
   if (ret != DRV_SUCCESS)
   {
      ostringstream os;
      os << "Error in GetAcquisitionProgress: " << ret << " | Stopping Acquisition";
      GetCoreCallback()->LogMessage(this, os.str().c_str(), true);
      StopCameraAcquisition();
   }

   return ret;
}


   ///////////////////////////////////////////////////////////////////////////////
   // Continuous acquisition
   //

   /**
   * Continuous acquisition thread service routine.
   * Starts acquisition on the AndorCamera and repeatedly calls PushImage()
   * to transfer any new images to the MMCore circularr buffer.
   */
   int AcqSequenceThread::svc(void)
   {
      at_32 series(0);
      at_32 seriesInit(0);
      unsigned ret;
      std::ostringstream os;

      camera_->Log("Starting Andor svc\n");

      at_u32 width(0);
      at_u32 height(0);
      at_u32 bytesPerPixel(0);

      camera_->CalculateAndSetupCameraImageBuffer(width, height, bytesPerPixel);

      long timePrev = GetTickCount();
      long imageWait = 0;

      // wait for frames to start coming in
      do
      {
         ret = camera_->GetCameraAcquisitionProgress(&series);
         if (ret != DRV_SUCCESS)
            return ret;

         CDeviceUtils::SleepMs(waitTime_);

      } while (series == seriesInit && !stop_);
      camera_->Log("Images appearing");

      at_32 seriesPrev = 0;

      do
      {
         {
            DriverGuard dg(camera_);
            ret = GetTotalNumberImagesAcquired(&series);
            //os.str("");
            //os << "[svc] Thread GetTotalNumberImagesAcquired returned: " << ret << " Series number returned was: " << series << endl;
            //camera_->Log(os.str().c_str());
         }
		
		
         if (ret == DRV_SUCCESS)
         {
            if (series > seriesPrev)
            {
               long imageCountFirst, imageCountLast;
               int returnc;
               returnc = GetNumberNewImages(&imageCountFirst, &imageCountLast);
               if (ret != DRV_SUCCESS)
               {
                  os.str("");
                  os << "GetNumberNewImages PushImage error : " << ret << " first: " << imageCountFirst << " last: " << imageCountLast << endl;
                  camera_->Log(os.str().c_str());
                  return (int)ret;
               }
               // new frame arrived
               int retCode = camera_->PushImage(width, height, bytesPerPixel, imageCountFirst, imageCountLast);
               if (retCode != DEVICE_OK)
               {
                  os << "PushImage failed with error code " << retCode;
                  camera_->Log(os.str().c_str());
                  printf("%s\n", os.str().c_str());
                  os.str("");
                  //camera_->StopSequenceAcquisition();
                  //return ret;
               }

               // report time elapsed since previous frame
               //printf("Frame %d captured at %ld ms!\n", ++frameCounter, GetTickCount() - timePrev);
			   
               seriesPrev = series;
               timePrev = GetTickCount();
            } 
            else 
            {
               imageWait = GetTickCount() - timePrev;
               if (imageWait > imageTimeOut_) {
                  os << "Time out reached at frame " << camera_->imageCounter_;
                  camera_->LogMessage("Time out reached", true);
                  printf("%s\n", os.str().c_str());
                  os.str("");
                  camera_->StopCameraAcquisition();
                  return 0;
               }
            }
            
            CDeviceUtils::SleepMs(waitTime_);

         }


      }

      while (ret == DRV_SUCCESS && camera_->imageCounter_ < numImages_ && !stop_);
      
	   

      if (ret != DRV_SUCCESS && series != 0)
      {
         camera_->StopCameraAcquisition();

         os << "Error: " << ret;
         printf("%s\n", os.str().c_str());
         os.str("");
		
         return ret;
      }

      if (stop_)
      {
         printf ("Acquisition interrupted by the user!\n");
         return DEVICE_OK;
      }

      if ((series-seriesInit) == numImages_)
      {
         printf("Did not get the intended number of images\n");
         camera_->StopCameraAcquisition();
         return DEVICE_OK;
      }

      os << "series: " << series << " seriesInit: " << seriesInit << " numImages: "<< numImages_;
      printf("%s\n", os.str().c_str());
      camera_->LogMessage("Aquire Thread: We can get here if we are not fast enough", true);
      camera_->StopCameraAcquisition();
      return 3; // we can get here if we are not fast enough.  Report?  
   }

   /**
   * Starts continuous acquisition.
   */
   int AndorCamera::StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow)
   {
      DriverGuard dg(this);

      if (sequenceRunning_)
         return ERR_BUSY_ACQUIRING;

      //Check here required to stop SDK blowing-up if was Live, and then change property, 
      // subtraction occurs and sets number of kinetics to extremely large value.
      if (LONG_MAX == sequenceLength_)
      {
         numImages = LONG_MAX;
      }

      sequencePaused_ = false;
      stopOnOverflow_ = stopOnOverflow;
      sequenceLength_ = numImages;
      intervalMs_ = interval_ms;

      if(IsAcquiring())
      {
         SetToIdle();
      }

      // prepare the camera
      bool kineticSeries = LONG_MAX != numImages;

      if (SRRFControl_->GetSRRFEnabled())
      {
         int returnCodeFromSRRF = SRRFControl_->ApplySRRFParameters(&img_, !kineticSeries);
         if (returnCodeFromSRRF != AT_SRRF_SUCCESS)
         {
            return DEVICE_ERR;
         }

         numImages *= kineticSeries ? SRRFControl_->GetFrameBurst() : 1;  //Live numImages=LONG_MAX; SRRF_Enabled may cause arithmetic overflow...
      }

      ostringstream os;

      os << "Started sequence acquisition: " << numImages << " images  at " << interval_ms << " ms" << endl;
      LogMessage(os.str().c_str());

      LogMessage("Setting DMA Parameters", true);
      
      if (EXTERNAL != iCurrentTriggerMode_ && EXTERNALEXPOSURE != iCurrentTriggerMode_ && FASTEXTERNAL != iCurrentTriggerMode_) {
        // optimise the DMA setting unless we are running in external trigger or external exposure
        // in those modes, the SDK set up the DMA to 1 which prevents time outs in case of slow trigger
        int imagesPerDma = MAX_IMAGES_PER_DMA;
        if(imagesPerDma>numImages)
           imagesPerDma=numImages;

        int ret = SetDMAParameters(imagesPerDma, 0.001f);
      
        if (DRV_SUCCESS != ret)
           return (int)ret;

      }
      
      LogMessage("Setting Trigger Mode", true);
      int ret0;
      ret0 = ApplyTriggerMode(SOFTWARE == iCurrentTriggerMode_ ? INTERNAL : iCurrentTriggerMode_);
      if(DRV_SUCCESS!=ret0)
         return ret0;

      if (interval_ms > 0 && SRRFControl_->GetSRRFEnabled())
      {
         kineticSeries = false;
      }

      int ret = SetAcquisitionMode(kineticSeries ? 3 : 5); // kinetic series : run till abort
      if (ret != DRV_SUCCESS)
         return ret;
      string s("Set acquisition mode to ");
      s += kineticSeries ? "3 (Kinetic series)" : "5 (Run till abort)";
      LogMessage(s, true);

      if(kineticSeries) {
        ret = SetNumberKinetics(numImages);
        os.str("");
        os << "Set Number of kinetics to " << numImages;
        LogMessage(os.str().c_str(), true);
          if (ret != DRV_SUCCESS) {
           return ret;
          }
      }

      SetExposureTime((float) (expMs_/1000.0));

      LogMessage ("Set Exposure time", true);
      ret = SetNumberAccumulations(1);
      if (ret != DRV_SUCCESS)
      {
         SetAcquisitionMode(1);
         return ret;
      }
      LogMessage("Set Number of accumulations to 1", true);

      if (kineticSeries) {
         ret = SetKineticCycleTime((float)(interval_ms / 1000.0));
         if (ret != DRV_SUCCESS)
         {
            SetAcquisitionMode(1);
            return ret;
         }
         LogMessage("Set Kinetic cycle time", true);
      }

      ret = ApplyROI(false);
      if(DRV_SUCCESS!=ret)
         return ret;
         
      // start thread
      imageCounter_ = 0;

      os.str("");
      os << "Setting thread length to " << numImages << " Images";
      LogMessage(os.str().c_str(), true);
      seqThread_->SetLength(numImages);

      ret = UpdateTimings();
      if (DRV_SUCCESS != ret)
      {
         return ret;
      }

      seqThread_->SetWaitTime((at_32) (ActualInterval_ms_ / 5));
      seqThread_->SetTimeOut(imageTimeOut_ms_);

      //Notify GUI and Properties of new exposure
      ret = UpdateExposureFromCamera();
      if(DRV_SUCCESS!=ret)
         return ret;

      // prepare the core
      ret = GetCoreCallback()->PrepareForAcq(this);
      if (ret != DEVICE_OK)
      {
         SetAcquisitionMode(1);
         return ret;
      }

      LogMessage("Starting acquisition in the camera", true);
      startTime_ = GetCurrentMMTime();
      ret = ::StartAcquisition();

      if (ret != DRV_SUCCESS)
      {
         os.str("");
         os << "Andor driver returned error value : " << ret;
         Log(os.str());
         return ret;
      } else 
      {
         startSRRFImageTime_ = GetCurrentMMTime();
         seqThread_->Start();
         sequenceRunning_ = true;
      }

      return DEVICE_OK;
   }

   int AndorCamera::RestartSequenceAcquisition()
   {
      return StartSequenceAcquisition(sequenceLength_ - imageCounter_, intervalMs_, stopOnOverflow_);
   }

   void AndorCamera::PrepareToApplySetting()
   {
      stateBeforePause_ = PREPAREDFORSINGLESNAP;
      if (sequenceRunning_)
      {
         stateBeforePause_ = SEQUENCEACQUISITION;
         StopSequenceAcquisition(true);
      }

      SetToIdle();
   }

   void AndorCamera::ResumeAfterApplySetting()
   {
      if (SEQUENCEACQUISITION == stateBeforePause_)
         RestartSequenceAcquisition();
      else
         PrepareSnap();
   }


   /**
   * Stop Seq sequence acquisition
   * This is the function for internal use and can/should be called from the thread
   */
   int AndorCamera::StopCameraAcquisition()
   {
      {
		 
         DriverGuard dg(this);
         if (!sequenceRunning_)
            return DEVICE_OK;
		 
         LogMessage("Stopped sequence acquisition");
         AbortAcquisition();
         int status = DRV_ACQUIRING;
         int error = DRV_SUCCESS;
         while (error == DRV_SUCCESS && status == DRV_ACQUIRING) {
           error = GetStatus(&status); 
         }

         sequenceRunning_ = false;

         UpdateSnapTriggerMode();
      }
	  

      MM::Core* cb = GetCoreCallback();
      if (cb)
         return cb->AcqFinished(this, 0);
      else
         return DEVICE_OK;
   }

   /**
   * Stops Sequence acquisition
   * This is for external use only (if called from the sequence acquisition thread, deadlock will ensue!
   */
   int AndorCamera::StopSequenceAcquisition()
   {
      return StopSequenceAcquisition(false);
   }


   int AndorCamera::StopSequenceAcquisition(bool temporary)
   {
      {
         DriverGuard dg(this);
         sequencePaused_ = temporary;
         StopCameraAcquisition();
      }

      seqThread_->Stop();
      seqThread_->wait();
      delete cameraBuffer_;

      if (!temporary)
      {
         sequenceLength_ = 0;
         PrepareSnap();
         int ret = UpdateExposureFromCamera();
         if(ret!=DRV_SUCCESS)
            return ret;
      }

      return DEVICE_OK;
   }

   /**
   * Waits for new image and inserts it in the circular buffer.
   * This method is called by the acquisition thread AcqSequenceThread::svc()
   * in an infinite loop.
   *
   * In case of error or if the sequence is finished StopSequenceAcquisition()
   * is called, which will raise the stop_ flag and cause the thread to exit.
   */
   int AndorCamera::PushImage(at_u32 width, at_u32 height, at_u32 bytesPerPixel, at_32 imageCountFirst, at_32 imageCountLast)
   {
      if (SRRFControl_->GetSRRFEnabled())
      {
         return PushImageWithSRRF(imageCountFirst, imageCountLast);
      }

      at_u32 imagesPerDMA;
      at_32 validFirst = 0, validLast = 0;

      {
         DriverGuard dg(this);
         unsigned ret;
         ret = GetImagesPerDMA(&imagesPerDMA);

         long imagesAvailable = imageCountLast - imageCountFirst;

         if( (unsigned long) imagesAvailable >= imagesPerDMA)
         {
            imagesAvailable = imagesPerDMA-1;
         }

         if(stopOnOverflow_)
         {
            imageCountLast = imageCountFirst+imagesAvailable; //get oldest images
         }
         else
         {
            imageCountFirst = imageCountLast-imagesAvailable; //get newest images
         }

         unsigned long imageBufferSizePixels = (imagesAvailable + 1)*width*height;
         if (NeedToAllocateExtraBuffers(imageBufferSizePixels))
         {
            delete[] fullFrameBuffer_;
            fullFrameBuffer_ = new short[imageBufferSizePixels];
         }

         ret = GetImages16(imageCountFirst,imageCountLast, (WORD*) fullFrameBuffer_, imageBufferSizePixels, &validFirst, &validLast);
         if (ret == DRV_NO_NEW_DATA) {
           int status = 0;
           ret = GetStatus (&status);
           if (status == DRV_ACQUIRING) {
             ret = WaitForAcquisition ();
             if (ret != DRV_SUCCESS) {
               return ret;
             }
           }
         }
         else if (ret != DRV_SUCCESS) {
           return ret;
         }

      }

      // process image
      // imageprocesssor now called from core

     if(validLast > sequenceLength_) 
            validLast = sequenceLength_; //don't push more images at the circular buffer than are in the sequence.  

      int retCode;
      short*  imagePtr = fullFrameBuffer_;
      for(int i=validFirst; i<=validLast; i++)
      {

         // create metadata
         char label[MM::MaxStrLength];
         this->GetLabel(label);

         Metadata md;
         AddMetadataInfo(md);

         imageCounter_++;

         // This method inserts new image in the circular buffer (residing in MMCore)
         retCode = GetCoreCallback()->InsertImage(this, (unsigned char*) imagePtr,
            width,
            height,
            bytesPerPixel,
            md.Serialize().c_str());

         if (!stopOnOverflow_ && DEVICE_BUFFER_OVERFLOW == retCode)
         {
            // do not stop on overflow - just reset the buffer
            GetCoreCallback()->ClearImageBuffer(this);
            GetCoreCallback()->InsertImage(this, (unsigned char*) imagePtr,
               width,
               height,
               bytesPerPixel,
               md.Serialize().c_str(),
               false);
         }

         imagePtr += width*height;
      }

      return DEVICE_OK;
   }


   int AndorCamera::PushImageWithSRRF(at_32 imageCountFirst, at_32 imageCountLast)
   {

      unsigned int width;
      unsigned int height;
      unsigned int bytesPerPixel;
      at_32 validFirst = 0, validLast = 0;

      unsigned ret;

      width = img_.Width();
      height = img_.Height();
      bytesPerPixel = GetImageBytesPerPixel();

      {
         DriverGuard dg(this);
         //ostringstream oss;

         //oss << "[PushSRRFImage] called GetNumberNewImages | first:" << imageCountFirst << " last:" << imageCountLast << "  Returned:" << ret << endl;
         //Log(oss.str().c_str());
         //oss.str("");
         at_u32 numberOfPixels = width * height * (imageCountLast - imageCountFirst + 1);
         cameraBuffer_->Resize(width * (imageCountLast - imageCountFirst + 1), height);
         SetCameraImageBuffer(cameraBuffer_->GetPixelsRW());
         WORD* cameraBufferImagePtr = (WORD*)pImgBuffer_;

         ret = GetImages16(imageCountFirst, imageCountLast, cameraBufferImagePtr, numberOfPixels, &validFirst, &validLast);
         //oss << "[PushSRRFImage] called GetImages16 | first:" << imageCountFirst << " last:" << imageCountLast << " numberOfPixels to get:" << numberOfPixels;
         //oss << "  validFirst:" << validFirst << " validLast:" << validLast << "  Returned:" << ret << endl;
         //Log(oss.str().c_str());
         if (ret == DRV_NO_NEW_DATA) 
         {
            GetImages16(imageCountLast, imageCountLast, cameraBufferImagePtr, width*height, &validFirst, &validLast);
            //oss << "[PushSRRFImage] called GetImages16 again (NO_NEW_DATA) | first:" << imageCountLast << " last:" << imageCountLast << " w x h:" << width*height;
            //oss << "  validFirst:" << validFirst << " validLast:" << validLast << "  Returned:" << ret << endl;
            //Log(oss.str().c_str());
         }
         else if (ret != DRV_SUCCESS) 
         {
            return ret;
         }
      }

      for (int i = validFirst; i <= validLast; i++)
      {
         //ostringstream oss;
         WORD* imagePtr = (WORD*)pImgBuffer_;
         bool frameBurstComplete = SRRFControl_->ProcessSingleFrameOnCPU(imagePtr, width*height*bytesPerPixel);
         if (frameBurstComplete)
         {
            AT_SRRF_U64 outputBufferSize = GetImageBufferSize();
            SRRFControl_->GetSRRFResult(SRRFImage_->GetPixelsRW(), outputBufferSize);

            Metadata md;
            AddSRRFMetadataInfo(md);
            startSRRFImageTime_ = GetCurrentMMTime();

            // Only increment the image counter after metadata added, as uses this value...
            imageCounter_++;

            int corecallbackInsertImageReturn = GetCoreCallback()->InsertImage(
               this,
               SRRFImage_->GetPixels(),
               SRRFImage_->Width(),
               SRRFImage_->Height(),
               SRRFImage_->Depth(),
               md.Serialize().c_str(),
               false);
            //oss.str("");
            //oss << "[PushImageWithSRRF] sent up an image to MMCore and returned: " << corecallbackInsertImageReturn << endl;
            //Log(oss.str().c_str());

            if (DEVICE_OK != corecallbackInsertImageReturn)
            {
               return corecallbackInsertImageReturn;
            }

            if (!stopOnOverflow_ && DEVICE_BUFFER_OVERFLOW == corecallbackInsertImageReturn)
            {
               // do not stop on overflow - just reset the buffer
               //Log("[PushImageWithSRRF] Circular Buffer overflowed. Clearing and sending up image again...");
               GetCoreCallback()->ClearImageBuffer(this);
               GetCoreCallback()->InsertImage(
                  this,
                  SRRFImage_->GetPixels(),
                  SRRFImage_->Width(),
                  SRRFImage_->Height(),
                  SRRFImage_->Depth(),
                  md.Serialize().c_str(),
                  false);
            }
         }

         imagePtr += width*height;
      }

      return DEVICE_OK;
   }

   std::string AndorCamera::getCameraType() {
      std::string retVal("");
      AndorCapabilities caps;

      caps.ulSize = sizeof(AndorCapabilities);
      GetCapabilities(&caps);
      

      unsigned long camType = caps.ulCameraType;
      switch(camType) {
     case(AC_CAMERATYPE_PDA):
        retVal = "PDA";
        break;
     case(AC_CAMERATYPE_IXON):
        retVal = "iXon";
        break;
     case(AC_CAMERATYPE_INGAAS):
        retVal = "inGaAs";
        break;
     case(AC_CAMERATYPE_ICCD):
        retVal = "ICCD";
        break;
     case(AC_CAMERATYPE_EMCCD):
        retVal = "EMICCD";
        break;
     case(AC_CAMERATYPE_CCD):
        retVal = "CCD";
        break;
     case(AC_CAMERATYPE_ISTAR):
        retVal = "iStar";
        break;
     case(AC_CAMERATYPE_VIDEO):
        retVal = "Video";
        break;
     case(AC_CAMERATYPE_IDUS):
        retVal = "iDus";
        break;
     case(AC_CAMERATYPE_NEWTON):
        retVal = "Newton";
        break;
     case(AC_CAMERATYPE_SURCAM):
        retVal = "Surcam";
        break;
     case(AC_CAMERATYPE_USBICCD):
        retVal = "USB ICCD";
        break;
     case(AC_CAMERATYPE_LUCA):
        retVal = "Luca";
        break;
     case(AC_CAMERATYPE_RESERVED):
        retVal = "Reserved";
        break;
     case(AC_CAMERATYPE_IKON):
        retVal = "iKon";
        break;
     case(AC_CAMERATYPE_IVAC):
        retVal = "iVac";
        break;
     case(17):  // Should say AC_CAMERATYPE_CLARA but this only defined in versions > 2.83 [01/04/2009]
        retVal = "Clara";
        break;
     case(AC_CAMERATYPE_IXONULTRA):
        retVal = "iXon Ultra";
        break;
     case(AC_CAMERATYPE_UNPROGRAMMED):
        retVal = "Unprogrammed";
        break;
     default:
        retVal = "Unknown";
        break;
      }

      return retVal;
   }

   unsigned int AndorCamera::createGainProperty(AndorCapabilities * caps) {
      DriverGuard dg(this);
      unsigned int retVal(DRV_SUCCESS);
      bEMGainSupported  = ((caps->ulSetFunctions & AC_SETFUNCTION_EMCCDGAIN) == AC_SETFUNCTION_EMCCDGAIN);

      int state = 1;  // for setting the em gain advanced state
      int mode  = 0;  // for setting the em gain mode

      if(bEMGainSupported) {
         if((caps->ulEMGainCapability&AC_EMGAIN_REAL12) == AC_EMGAIN_REAL12)
         {
            mode = 3;         //Real EM gain
         } 
         else if((caps->ulEMGainCapability&AC_EMGAIN_LINEAR12) == AC_EMGAIN_LINEAR12)
         { 
            mode = 2;         //Linear mode
         }
         else if((caps->ulEMGainCapability&AC_EMGAIN_12BIT) ==  AC_EMGAIN_12BIT)
         {
            mode = 1;         //The EM Gain is controlled by DAC settings in the range 0-4095
         }
         else if((caps->ulEMGainCapability&AC_EMGAIN_8BIT) == AC_EMGAIN_8BIT)
         {
            state = 0;        //Disable access
            mode = 0;         //The EM Gain is controlled by DAC settings in the range 0-255. Default mode
         }

         if((caps->ulSetFunctions&AC_SETFUNCTION_EMADVANCED) == AC_SETFUNCTION_EMADVANCED) {
            retVal = SetEMAdvanced(state);
            if(retVal != DRV_SUCCESS) {
               return retVal;
            }
         }
         retVal = SetEMGainMode(mode);
         if(retVal != DRV_SUCCESS) {
            return retVal;
         }

         int i_gainLow, i_gainHigh;
         retVal = GetEMGainRange(&i_gainLow, &i_gainHigh);
         if (retVal != DRV_SUCCESS) {
            return retVal;
         }

         if(!HasProperty(g_EMGainValue)) {
            CPropertyAction *pAct = new CPropertyAction(this, &AndorCamera::OnGain);
            int nRet = CreateProperty(g_EMGainValue,"0", MM::Integer,false, pAct);
            assert(nRet == DEVICE_OK);
            nRet = SetPropertyLimits(g_EMGainValue, 0, i_gainHigh);
            assert(nRet == DEVICE_OK);
         }
      }
      if(bEMGainSupported) {
         CPropertyAction *pAct = new CPropertyAction(this, &AndorCamera::OnEMSwitch);
         int nRet = CreateProperty(g_EMGain, "On", MM::String, false, pAct);
         if (nRet != DEVICE_OK) {
            return nRet;
         }
         AddAllowedValue(g_EMGain, "On");      
         AddAllowedValue(g_EMGain, "Off");
      }
      
  return retVal;
}


   /**
   * Set camera crop mode on/off.
   */
   int AndorCamera::OnCropModeSwitch(MM::PropertyBase* /*pProp*/, MM::ActionType eAct)
   {
      
      if (eAct == MM::AfterSet)
      {
         long data;
         GetCurrentPropertyData(g_cropMode, data);
         
         CROPMODE mode = (CROPMODE) data;

         if(mode == cropModeSwitch_)
            return DEVICE_OK;

         bool acquiring = sequenceRunning_;
         if (acquiring)
            StopSequenceAcquisition(true);
         
         {
            DriverGuard dg(this); 
            if (sequenceRunning_)
               return ERR_BUSY_ACQUIRING;

            SetToIdle();

            if (OFF != mode){
               SetProperty(m_str_frameTransferProp.c_str(), "On");           
            }

            cropModeSwitch_ = mode;

            PopulateROIDropdown();

            //if (initialized_) {
            //   OnPropertiesChanged();
            //}

            unsigned int ret = UpdateTimings();
            if (DRV_SUCCESS != ret)
               return (int)ret;

            ResizeImageBuffer();

            if (acquiring)
               StartSequenceAcquisition(sequenceLength_ - imageCounter_, intervalMs_, stopOnOverflow_);

            PrepareSnap();
         }
      }
      return DEVICE_OK;
   }



unsigned int AndorCamera::createROIProperties(AndorCapabilities * caps)
{
   unsigned int ret(DRV_SUCCESS);

   //create Isolated Crop Mode Property
   if(caps->ulSetFunctions&AC_SETFUNCTION_CROPMODE) 
   {
      CPropertyAction *pAct = new CPropertyAction(this, &AndorCamera::OnCropModeSwitch);
      int nRet = CreateProperty(g_cropMode, "Off", MM::String, false, pAct);
      if (DEVICE_OK!= nRet) {
         return nRet;
      }

      if(AC_CAMERATYPE_IXONULTRA==caps->ulCameraType) 
      {
         AddAllowedValue(g_cropMode, "On (any ROI)",CENTRAL);
      }
      AddAllowedValue(g_cropMode, "On (bottom corner)", BOTTOM);
      AddAllowedValue(g_cropMode, "Off",OFF);
   }
  
   ret = PopulateROIDropdown();

   return ret;
}

void AndorCamera::PopulateTriggerDropdown()
{
   if (readModeControl_->getCurrentMode() == FVB)
   {
	   SetAllowedValues("Trigger", triggerModesFVB_);
   }
   else
   {
	   SetAllowedValues("Trigger", triggerModesIMAGE_);
   }
}

unsigned int AndorCamera::PopulateBinningDropdown()
{
	vector<string> binValues;
    binValues.push_back("1");

	if (readModeControl_->getCurrentMode() != FVB)
	{
		binValues.push_back("2");
		binValues.push_back("4");
		binValues.push_back("8");
	}
    
    return SetAllowedValues(MM::g_Keyword_Binning, binValues);
}

unsigned int AndorCamera::PopulateROIDropdown()
{
   if(!HasProperty(g_ROIProperty))
   {
      CPropertyAction *pAct = new CPropertyAction(this, &AndorCamera::OnROI);
      int nRet = CreateProperty(g_ROIProperty, g_ROIFullImage, MM::String, false, pAct);
      if (DEVICE_OK!= nRet) {
         return nRet;
      }
   }

   
   
   //if off populate sensor size/2 down to 32 pixels
   int vSize = fullFrameX_;
   int hSize = fullFrameY_;
   int uiROICount =0;

   ClearAllowedValues(g_ROIProperty);
   roiList.clear();

   AddAllowedValue(g_ROIProperty, g_ROIFullImage,uiROICount);
   ROI full;
   full.x=0;
   full.y=0;
   full.xSize=fullFrameX_;
   full.ySize=fullFrameY_;
   roiList.push_back(full);
   uiROICount++;
   
   if(CENTRAL != cropModeSwitch_)
   {
      while(vSize >=64)
      {
         vSize = vSize/2;
         hSize = hSize/2;

         ROI roi;
         roi.xSize = vSize;
         roi.ySize = hSize;
       
         //if isolated crop is off center the ROIs
         if(OFF == cropModeSwitch_)
         {
            roi.x = (fullFrameX_-vSize)/2;
            roi.y = (fullFrameY_-hSize)/2;
         }
         else
         {

            if(OutputAmplifierIndex_ == 0) //EM Mode
            {
               roi.x=0;
               roi.y=0;
            }
            else //conventional mode (preselect AOIs in other corner)
            {
               roi.x= fullFrameX_ - roi.xSize;
               roi.y= fullFrameX_ - roi.xSize;
            }
            
         }


         roiList.push_back(roi);

         char buffer[MAX_CHARS_PER_DESCRIPTION];
         GetROIPropertyName(uiROICount, roi.xSize, roi.ySize, buffer,cropModeSwitch_);
         AddAllowedValue(g_ROIProperty, buffer,uiROICount);

         uiROICount++;
      }
   }
   else
   {  //if centered add custom Ultra params
      int numcroprois = NUMULTRA897CROPROIS;
      ROI* UltraCropROIs = g_Ultra897CropROIs;

      if(IsIxonUltra888())
      {
         numcroprois = NUMULTRA888CROPROIS;
         UltraCropROIs = g_Ultra888CropROIs;
      }

      for(int i=0; i<numcroprois; i++)
      {
         ROI roi = UltraCropROIs[i];
         roiList.push_back(roi);
         char buffer[MAX_CHARS_PER_DESCRIPTION];
         GetROIPropertyName(uiROICount, roi.xSize, roi.ySize, buffer,cropModeSwitch_);
         AddAllowedValue(g_ROIProperty, buffer,uiROICount);
         uiROICount++;
      }
   }

   return DRV_SUCCESS;
}

unsigned int AndorCamera::PopulateROIDropdownFVB()
{
   ClearAllowedValues(g_ROIProperty);
   roiList.clear();

   ROI full;
   full.x=0;
   full.y=0;
   full.xSize=fullFrameX_;
   full.ySize=1;
   roiList.push_back(full);


   AddAllowedValue(g_ROIProperty, g_ROIFVB,0);

	return DRV_SUCCESS;
}

   unsigned int AndorCamera::AddTriggerProperty(int mode)
   {
      unsigned int retVal;
       if(iCurrentTriggerMode_ == mode) 
         {
            retVal = ApplyTriggerMode(mode);
            if (retVal != DRV_SUCCESS)
            {
               ShutDown();
               string s;
               s="Could not set \""+GetTriggerModeString(mode)+"\" trigger mode";
               LogMessage(s);
               return retVal;
            }
            strCurrentTriggerMode_ = GetTriggerModeString(mode);
         }
         triggerModesIMAGE_.push_back(GetTriggerModeString(mode));
		 if(mode != GetTriggerModeInt(g_Software) && mode != GetTriggerModeInt(g_ExternalExposure)) 
            triggerModesFVB_.push_back(GetTriggerModeString(mode));
         return DRV_SUCCESS;
   }

   void AndorCamera::AddSRRFMetadataInfo(Metadata & md)
   {
      char label[MM::MaxStrLength];
      this->GetLabel(label);
      MM::MMTime tEnd = GetCurrentMMTime();

      // Copy the metadata inserted by other processes:
      std::vector<std::string> keys = GetTagKeys();
      for (unsigned int j = 0; j < keys.size(); j++) {
         md.put(keys[j], GetTagValue(keys[j].c_str()).c_str());
      }

      MetadataSingleTag mstSRRFFrameTime(g_Keyword_Metadata_SRRF_Frame_Time, label, true);
      mstSRRFFrameTime.SetValue(CDeviceUtils::ConvertToString((tEnd - startSRRFImageTime_).getMsec()));
      md.SetTag(mstSRRFFrameTime);
   }

   void AndorCamera::AddMetadataInfo(Metadata & md)
   {
      // create metadata
      char label[MM::MaxStrLength];
      this->GetLabel(label);

      MM::MMTime timestamp = this->GetCurrentMMTime();
      // Copy the metadata inserted by other processes:
      std::vector<std::string> keys = GetTagKeys();
      for (unsigned int j = 0; j < keys.size(); j++) {
         md.put(keys[j], GetTagValue(keys[j].c_str()).c_str());
      }

      //These append md tag name to label of device; transient props appear per image.  All in .txt file with stack.
      //Plan of attack...
      // remove binning - should always be present as part of MM GUI and unchanging property - doesn't need set every image
      // remove first .put deprecated and check if any changes. Perhaps take 5seq MDA first using current build, then rebuild and check difference
      // Check both MM Metadata, and .text file generated by stack images.
      // add SRRF - may need better timings, perhaps a new m_var_ but can decide during impl.
      //md.put("Camera", label);
      //md.put(MM::g_Keyword_Metadata_StartTime, CDeviceUtils::ConvertToString(startTime_.getMsec()));
      //md.put(MM::g_Keyword_Elapsed_Time_ms, CDeviceUtils::ConvertToString((timestamp - startTime_).getMsec()));
      //md.put(MM::g_Keyword_Metadata_ImageNumber, CDeviceUtils::ConvertToString(imageCounter_));
      //md.put(MM::g_Keyword_Binning, binSize_);

      MetadataSingleTag mstStartTime(MM::g_Keyword_Metadata_StartTime, label, true);
      mstStartTime.SetValue(CDeviceUtils::ConvertToString(startTime_.getMsec()));
      md.SetTag(mstStartTime);

      MetadataSingleTag mst(MM::g_Keyword_Elapsed_Time_ms, label, true);
      mst.SetValue(CDeviceUtils::ConvertToString(timestamp.getMsec()));
      md.SetTag(mst);

      if (metaDataAvailable_)
      {
         SYSTEMTIME timeOfStart;
         float timeFromStart = 0.f;
         unsigned int ret = GetMetaDataInfo(&timeOfStart, &timeFromStart, imageCounter_);
         if (ret == DRV_SUCCESS)
         {
            MetadataSingleTag mstHW("ElapsedTime-ms(HW)", label, true);
            mstHW.SetValue(CDeviceUtils::ConvertToString(timeFromStart));
            md.SetTag(mstHW);
         }
      }

      MetadataSingleTag mstCount(MM::g_Keyword_Metadata_ImageNumber, label, true);
      mstCount.SetValue(CDeviceUtils::ConvertToString(imageCounter_));
      md.SetTag(mstCount);

      MetadataSingleTag mstB(MM::g_Keyword_Binning, label, true);
      mstB.SetValue(CDeviceUtils::ConvertToString(binSize_));
      md.SetTag(mstB);
   }

   bool AndorCamera::IsIxonUltra()
   {
      AndorCapabilities caps;
      caps.ulSize = sizeof(AndorCapabilities);
      GetCapabilities(&caps);
      return AC_CAMERATYPE_IXONULTRA == caps.ulCameraType;
   }

   bool AndorCamera::IsIxonUltra888()
   {
      if(!IsIxonUltra()) return false;

      char head[100] = "";
      GetHeadModel(head);
      std::string headStr = head;
      if(headStr.find("888") != std::string::npos)
         return true;

      return false;
   }

   void AndorCamera::SetDefaultVSSForUltra888WithValidSRRF()
   {
      if (IsIxonUltra888())
      {
         if (SRRFControl_->GetLibraryStatus() == SRRFControl::READY) {
            string requestedSpeed = "1.13";
            ptrdiff_t pos = find(VSpeeds_.begin(), VSpeeds_.end(), requestedSpeed) - VSpeeds_.begin();
            if (pos < (long long)VSpeeds_.size())
            {
               SetProperty(g_VerticalSpeedProperty, VSpeeds_[pos].c_str());
            }
         }
      }
   }

   unsigned int AndorCamera::createShutterProperty(AndorCapabilities * caps)
   {
      unsigned int ret = DRV_SUCCESS;
      int mechShut=0;
      bool propertyCreated = false;
      ret = IsInternalMechanicalShutter(&mechShut);
      iShutterMode_ = OPEN;
      iInternalShutterMode_=OPEN;
      iShutterOpeningTime_ = 20;
      iShutterClosingTime_ = 20;
      iShutterTTL_ = 1;

      int minOpeningTime, minClosingTime;

      ret = GetShutterMinTimes(&minClosingTime, &minOpeningTime); 
      if(DRV_SUCCESS!=ret)
        return ret;
      if(iShutterOpeningTime_ < minOpeningTime) {
        iShutterOpeningTime_ = minOpeningTime;
      }
      if(iShutterClosingTime_ < minClosingTime) {
        iShutterClosingTime_ = minClosingTime;
      }
      

      vector<string> shutterValues;
      shutterValues.push_back(g_ShutterMode_Open);
      shutterValues.push_back(g_ShutterMode_Closed);
      shutterValues.push_back(g_ShutterMode_Auto);

      bool externalCapability = (caps->ulFeatures & AC_FEATURES_SHUTTEREX) != 0;
      bool internalCapability = (caps->ulFeatures & AC_FEATURES_SHUTTER) != 0;

      if(externalCapability)
      {
         bShuttersIndependant_ = true;
         propertyCreated = true;
         if(!HasProperty(g_ExternalShutterMode))
         {
            CPropertyAction *pAct = new CPropertyAction (this, &AndorCamera::OnShutterMode);
            ret = CreateProperty(g_ExternalShutterMode, g_ShutterMode_Open, MM::String, false, pAct);
            assert(ret == DEVICE_OK);
         }
         
         ret = SetAllowedValues(g_ExternalShutterMode, shutterValues);
         if (ret != DEVICE_OK)
            return ret;

         if(!HasProperty(g_InternalShutterMode))
         {
            CPropertyAction *pAct = new CPropertyAction (this, &AndorCamera::OnInternalShutterMode);
            ret = CreateProperty(g_InternalShutterMode, g_ShutterMode_Open, MM::String, false, pAct);
            assert(ret == DEVICE_OK);
         }

         ret = SetAllowedValues(g_InternalShutterMode, shutterValues);
         if (ret != DEVICE_OK)
            return ret;

      }
      else if(internalCapability )//single shutter control
      {
         propertyCreated = true;
         bShuttersIndependant_ = false;
         if(!HasProperty(g_InternalShutterMode))
         {
            CPropertyAction *pAct = new CPropertyAction (this, &AndorCamera::OnShutterMode);
            ret = CreateProperty(g_ShutterMode, g_ShutterMode_Open, MM::String, false, pAct);
            assert(ret == DEVICE_OK);
         }
         ret = SetAllowedValues(g_ShutterMode, shutterValues);
         if (ret != DEVICE_OK)
            return ret;
      }

      if(propertyCreated)
      {
         if(!HasProperty(g_ShutterModeOpeningTime))
         {
            CPropertyAction *pAct = new CPropertyAction (this, &AndorCamera::OnShutterOpeningTime);
            ret = CreateProperty(g_ShutterModeOpeningTime, CDeviceUtils::ConvertToString(iShutterOpeningTime_), MM::Integer, false, pAct);
            if (ret != DEVICE_OK)
               return ret;
         }

         if(!HasProperty(g_ShutterModeClosingTime))
         {
            CPropertyAction *pAct  = new CPropertyAction (this, &AndorCamera::OnShutterClosingTime);
            ret = CreateProperty(g_ShutterModeClosingTime, CDeviceUtils::ConvertToString(iShutterClosingTime_), MM::Integer, false, pAct);
            if (ret != DEVICE_OK)
               return ret;
         }

         if(0==mechShut&&!HasProperty(g_ShutterTTL))
         {
            vector<string> ttlValues;
            ttlValues.push_back(g_ShutterTTLHighToOpen);
            ttlValues.push_back(g_ShutterTTLLowToOpen);

            CPropertyAction *pAct  = new CPropertyAction (this, &AndorCamera::OnShutterTTL);
            ret = CreateProperty(g_ShutterTTL, g_ShutterTTLHighToOpen, MM::String, false, pAct);
            if (ret != DEVICE_OK)
               return ret;

            ret = SetAllowedValues(g_ShutterTTL, ttlValues);
            if (ret != DEVICE_OK)
              return ret;
         }

      }

      if(internalCapability || externalCapability)
      {
         //initialise and apply settings
         ret = ApplyShutterSettings();
      }

      return ret;
   }

   unsigned int AndorCamera::createSnapTriggerMode()
   {
      int ret = DEVICE_OK;
      if(!HasProperty(g_SnapImageDelay))
      {
         CPropertyAction *pAct = new CPropertyAction (this, &AndorCamera::OnSnapImageDelay);
         ret = CreateProperty(g_SnapImageDelay, CDeviceUtils::ConvertToString(iSnapImageDelay_), MM::Integer, false, pAct);
         if (ret != DEVICE_OK)
            return ret;
      }

      if(!HasProperty(g_SnapImageMode))
      {
         vector<string> modes;
         modes.push_back(g_SnapImageModeDelayForExposure);
         modes.push_back(g_SnapImageModeWaitForReadout);
      
         CPropertyAction *pAct = new CPropertyAction (this, &AndorCamera::OnSnapImageMode);
         ret = CreateProperty(g_SnapImageMode, g_SnapImageModeDelayForExposure, MM::String, false, pAct);
         if (ret != DEVICE_OK)
            return ret;
            
         ret = SetAllowedValues(g_SnapImageMode, modes);
      }
      
      return ret;
   }


   unsigned int AndorCamera::createTriggerProperty(AndorCapabilities * caps) 
   {
      DriverGuard dg(this);
      triggerModesIMAGE_.clear();  
	  triggerModesFVB_.clear();
      unsigned int retVal = DRV_SUCCESS;
      if(caps->ulTriggerModes & AC_TRIGGERMODE_INTERNAL)
      { 
         retVal = AddTriggerProperty(INTERNAL);
         if (retVal != DRV_SUCCESS)
         {
            return retVal;
         }
      }
	  if(caps->ulTriggerModes & AC_TRIGGERMODE_CONTINUOUS)
      {
         retVal = AddTriggerProperty(SOFTWARE);
         if (retVal != DRV_SUCCESS)
         {
            return retVal;
         }
         biCamFeaturesSupported_ = true;
      }
      if(caps->ulTriggerModes & AC_TRIGGERMODE_EXTERNAL) 
      {
         retVal = AddTriggerProperty(EXTERNAL);
         if (retVal != DRV_SUCCESS)
         {
            return retVal;
         }
         retVal = AddTriggerProperty(FASTEXTERNAL);
         if (retVal != DRV_SUCCESS)
         {
            return retVal;
         }
      }
      
      if(caps->ulTriggerModes & AC_TRIGGERMODE_EXTERNALEXPOSURE) 
      {
         retVal = AddTriggerProperty(EXTERNALEXPOSURE);
         if (retVal != DRV_SUCCESS)
         {
            return retVal;
         }
      }
      if(caps->ulTriggerModes & AC_TRIGGERMODE_EXTERNALSTART)
      {
         retVal = AddTriggerProperty(EXTERNALSTART);
         if (retVal != DRV_SUCCESS)
         {
            return retVal;
         }
      }


      if(!HasProperty("Trigger"))
      {
         CPropertyAction *pAct = new CPropertyAction (this, &AndorCamera::OnSelectTrigger);
         int nRet = CreateProperty("Trigger", "Trigger Mode", MM::String, false, pAct);
         if (nRet != DEVICE_OK) {
            return nRet;
         }
      }
      int nRet = SetAllowedValues("Trigger", triggerModesIMAGE_);
      assert(nRet == DEVICE_OK);
      nRet = SetProperty("Trigger", strCurrentTriggerMode_.c_str());
      assert(nRet == DEVICE_OK);
      

      return retVal;
   }

   int AndorCamera::GetTriggerModeInt(string mode)
   {
      if(g_Internal == mode)
         return INTERNAL;
      else if(g_External == mode)
         return EXTERNAL;
      else if(g_ExternalExposure == mode)
         return EXTERNALEXPOSURE;
      else if(g_ExternalStart == mode)
         return EXTERNALSTART;
      else if(g_Software == mode)
         return SOFTWARE;
      else if(g_FastExternal == mode)
         return FASTEXTERNAL;
      
      return -1;
   }

   string AndorCamera::GetTriggerModeString(int mode)
   {
      string s;
      if(INTERNAL== mode)
         s = g_Internal;
      else if(EXTERNAL == mode)
         s = g_External;
      else if(EXTERNALEXPOSURE == mode)
         s = g_ExternalExposure;
      else if(EXTERNALSTART == mode)
         s = g_ExternalStart;
      else if(SOFTWARE == mode)
         s = g_Software;
      else if(FASTEXTERNAL == mode)
         s = g_FastExternal;
      else
         s = "Unknown Trigger Mode!";
      return s;
   }

   void AndorCamera::GetROIPropertyName(int position, int hSize, int vSize, char * buffer, int mode)
   {
      if(position==0) //full frame
      {
         sprintf(buffer, "%s",g_ROIFullImage);
      }
      else
      {
        
         int offset = sprintf(buffer, "%d. %d x %d",position, hSize, vSize);

         if(OFF==mode)
         {
            sprintf(buffer+offset," (centered)");
         }
         else if(BOTTOM==mode)
         {
           sprintf(buffer+offset," (bottom corner)");
         }
         else
         {
           sprintf(buffer+offset," (centered - ROI optimised for performance)");
         }

      }
   }

   unsigned int AndorCamera::ApplyTriggerMode(int mode)
   {
      int actualmode = mode;
      int fastmode = 0;
      unsigned int ret;

      if(FASTEXTERNAL == mode)
      {
         actualmode = EXTERNAL;
         fastmode=1;
      }

      ret = SetTriggerMode(actualmode);
      if(DRV_SUCCESS!=ret)
         return ret;

      ret = SetFastExtTrigger(fastmode);
      return ret;
   }

   unsigned int AndorCamera::ApplyShutterSettings()
   {
      unsigned int ret;
      if(bShuttersIndependant_)
      {
         ret = SetShutterEx(iShutterTTL_, iInternalShutterMode_, iShutterClosingTime_, iShutterOpeningTime_,iShutterMode_);
      }
      else
      {
         ret = SetShutter(iShutterTTL_, iShutterMode_, iShutterClosingTime_,iShutterOpeningTime_);
      }
      if(DRV_P2INVALID == ret) return ERR_INVALID_SHUTTER_MODE;
      if(DRV_P3INVALID == ret) return ERR_INVALID_SHUTTER_OPENTIME;
      if(DRV_P4INVALID == ret) return ERR_INVALID_SHUTTER_CLOSETIME;
      if(DRV_P5INVALID == ret) return ERR_INVALID_SHUTTER_MODE;
      return ret;
   }

   unsigned int AndorCamera::ApplyROI(bool forSingleSnap)
   {
      unsigned int ret = DRV_SUCCESS;

      if (roi_.x + roi_.xSize > fullFrameX_ || roi_.y + roi_.ySize > fullFrameY_)
         return ERR_INVALID_ROI;

      // adjust image extent to conform to the bin size
      roi_.xSize -= roi_.xSize % binSize_;
      roi_.ySize -= roi_.ySize % binSize_;


      if(forSingleSnap)
      {
         if(HasProperty(m_str_frameTransferProp.c_str()))
         {
            ret = SetFrameTransferMode(0);  //Software trigger mode can not be used in FT mode
            if (ret != DRV_SUCCESS)
               return ret;


            if(HasProperty(g_cropMode))
            {
               ret = SetIsolatedCropMode(0, roi_.ySize, roi_.xSize, binSize_, binSize_);
               if (ret != DRV_SUCCESS)
                  return ret;
            }
            

         }

         ret = SetImage(binSize_, binSize_, roi_.x+1, roi_.x+roi_.xSize, roi_.y+1, roi_.y+roi_.ySize);
         if (ret != DRV_SUCCESS)
            return ret;
      }
      else
      {

         if(HasProperty(m_str_frameTransferProp.c_str()))
         {
            ret = SetFrameTransferMode(bFrameTransfer_==1?1:0);
            if (ret != DRV_SUCCESS)
               return ret;

            if (bFrameTransfer_) 
            {
               CROPMODE actualCropMode = cropModeSwitch_;
               if(roi_.xSize == fullFrameX_ && roi_.ySize == fullFrameY_) //don't use cropmode for full frame
                  actualCropMode = OFF;

               if(BOTTOM==actualCropMode)
               {
                  ret = SetIsolatedCropMode(1, roi_.ySize, roi_.xSize, binSize_, binSize_);
                  if (ret != DRV_SUCCESS)
                     return ret;
               }
               else if(CENTRAL==actualCropMode)
               {
                  ret = SetIsolatedCropModeEx(1,roi_.ySize, roi_.xSize, binSize_, binSize_,roi_.x+1,roi_.y+1);
                  if (ret != DRV_SUCCESS)
                     return ret;
               }
               else //just plain frame xfer
               {
                  if(HasProperty(g_cropMode))
                  {
                     ret = SetIsolatedCropMode(0, roi_.ySize, roi_.xSize, binSize_, binSize_);
                     if (ret != DRV_SUCCESS)
                        return ret;
                  }

                  ret = SetImage(binSize_, binSize_, roi_.x+1, roi_.x+roi_.xSize, roi_.y+1, roi_.y+roi_.ySize);
                  if (ret != DRV_SUCCESS)
                     return ret;
               }
            }
         }
         else
         {
            ret = SetImage(binSize_, binSize_, roi_.x+1, roi_.x+roi_.xSize, roi_.y+1, roi_.y+roi_.ySize);
            if (ret != DRV_SUCCESS)
               return ret;
         }
      }

      ret = UpdateTimings();
      if (DRV_SUCCESS != ret)
         return ret;

      ret = ResizeImageBuffer();
      if (ret != DEVICE_OK)
         return ret;

      return DRV_SUCCESS;
   }

   unsigned int AndorCamera::UpdateSnapTriggerMode()
   {
      DriverGuard dg(this);
      int actualMode= iCurrentTriggerMode_;
      int acqMode = 1; //single scan
      unsigned int ret;

     
      if(EXTERNALSTART==actualMode)
      {
         actualMode = EXTERNAL;
      }

	  if(SOFTWARE==actualMode)
	  {
         acqMode = 5;  // run till abort used in software trigger
	  }

      ret = ApplyTriggerMode(actualMode);
      if(DRV_SUCCESS != ret)
         return ret;

      ret = SetAcquisitionMode(acqMode);
      if(DRV_SUCCESS != ret)
         return ret;

      return DRV_SUCCESS;
   }

int AndorCamera::AddProperty(const char* name, const char* value, MM::PropertyType eType, bool readOnly, MM::ActionFunctor* pAct)
{
   if(!HasProperty(name))
   {
      CreateProperty(name, value, eType, readOnly, pAct);
   }
   else
   {
      SetProperty(name, value);
   }
   return DRV_SUCCESS;
}





   DriverGuard::DriverGuard(const AndorCamera * cam)
   {
	    
      g_AndorDriverLock.Lock();
      if (cam != 0 && cam->GetNumberOfWorkableCameras() > 1)
      {
	      // must be defined as 32bit in order to compile on 64bit systems since GetCurrentCamera 
         // only takes 32bit -kdb		
         at_32 currentCamera;
         GetCurrentCamera(&currentCamera);
         if (currentCamera != cam->GetMyCameraID())
         {
            int ret = SetCurrentCamera(cam->GetMyCameraID());
            if (ret != DRV_SUCCESS)
			{
               printf("Error switching active camera");
			}
         }
      }
   }

   DriverGuard::~DriverGuard()
   {
      g_AndorDriverLock.Unlock();
   }
