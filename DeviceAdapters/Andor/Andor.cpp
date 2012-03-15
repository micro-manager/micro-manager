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
#include <string>
#include <sstream>
#include <iomanip>
#include <math.h>


#include <iostream>
using namespace std;

#pragma warning(disable : 4996) // disable warning for deperecated CRT functions on Windows 

using namespace std;

// global constants
const char* g_AndorName = "Andor";
const char* g_IxonName = "Ixon";

const char* g_PixelType_8bit = "8bit";
const char* g_PixelType_16bit = "16bit";

const char* g_ShutterMode = "ShutterMode";
const char* g_ShutterMode_Auto = "Auto";
const char* g_ShutterMode_Open = "Open";
const char* g_ShutterMode_Closed = "Closed";

const char* g_FanMode_Full = "Full";
const char* g_FanMode_Low = "Low";
const char* g_FanMode_Off = "Off";

const char* g_CoolerMode_FanOffAtShutdown = "Fan off at shutdown";
const char* g_CoolerMode_FanOnAtShutdown = "Fan on at shutdown";

//const char* g_FrameTransferProp = "FrameTransfer";
const char* g_FrameTransferOn = "On";
const char* g_FrameTransferOff = "Off";
const char* g_OutputAmplifier = "Output_Amplifier";
const char* g_OutputAmplifier_EM = "Standard EMCCD gain register";
const char* g_OutputAmplifier_Conventional = "Conventional CCD register";

const char* g_ADChannel = "AD_Converter";

const char* g_EMGain = "EMSwitch";
const char* g_EMGainValue = "Gain";
const char* g_TimeOut = "TimeOut";
const char* g_CameraInformation = "1. Camera Information : | Type | Model | Serial No. |";

const char* g_cropMode = "Isolated Crop Mode";
const char* g_cropModeWidth = "Isolated Crop Width";
const char* g_cropModeHeight = "Isolated Crop Height";

// singleton instance
AndorCamera* AndorCamera::instance_ = 0;
unsigned int AndorCamera::refCount_ = 0;
bool AndorCamera::softwareTriggerUsed_ = false;

// global Andor driver thread lock
MMThreadLock g_AndorDriverLock;

// kdb 2/27/2009
#ifdef WIN32
// Windows dll entry routine
bool APIENTRY DllMain( HANDLE /*hModule*/, 
                      DWORD  ul_reason_for_call, 
                      LPVOID /*lpReserved*/ ) {
                         switch (ul_reason_for_call) {
     case DLL_PROCESS_ATTACH:
        break;
     case DLL_THREAD_ATTACH:
     case DLL_THREAD_DETACH:
     case DLL_PROCESS_DETACH:
        break;
                         }
                         return TRUE;
}
#else 
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
   AddAvailableDeviceName(g_AndorName, "Generic Andor Camera Adapter");

}

char deviceName[64]; // jizhen 05.16.2007

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
tempFrameX_(0),
tempFrameY_(0),
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
bSoftwareTriggerSupported_(0),
maxTemp_(0),
myCameraID_(-1),
pImgBuffer_(0),
currentExpMS_(0.0),
cropModeSwitch_(false),
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
spuriousNoiseFilter_(""),
spuriousNoiseFilterDescriptionStr_("")
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

   seqThread_ = new AcqSequenceThread(this);

   // Pre-initialization properties
   // -----------------------------

   // Driver location property removed.  atmcd32d.dll should be in the working directory
   hAndorDll = 0;
   fpGetKeepCleanTime = 0;
   fpGetReadOutTime = 0;

#ifdef WIN32 
   if(hAndorDll == 0)
      hAndorDll = ::GetModuleHandle("atmcd32d.dll");
   if(hAndorDll!=NULL)
   {
      fpGetKeepCleanTime = (FPGetKeepCleanTime)GetProcAddress(hAndorDll, "GetKeepCleanTime");
      fpGetReadOutTime = (FPGetReadOutTime)GetProcAddress(hAndorDll, "GetReadOutTime");
   }
#else
   // load andor.so that interfaces with the andordrvlx kernel module on linux systems
   hAndorDll = dlopen("libandor.so.2", RTLD_LAZY|RTLD_GLOBAL);
   if (!hAndorDll)
   {
      fprintf(stderr,"Failed to find libandor.so.2\n");
      exit(1);
   } 
   else
   {
      fpGetKeepCleanTime = (FPGetKeepCleanTime)dlsym(hAndorDll, "GetKeepCleanTime");
      fpGetReadOutTime = (FPGetReadOutTime)dlsym(hAndorDll, "GetReadOutTime");
   }
   // this needs to be initialized for Linux, or ::Initialize() will not return
   driverDir_ = "/usr/local/etc/andor/";
#endif

   if (GetListOfAvailableCameras() != DRV_SUCCESS) 
	   //exit(1);
      LogMessage("No Andor camera found!");
}

AndorCamera::~AndorCamera()
{
   DriverGuard dg(this);
   delete seqThread_;


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

#ifndef WIN32
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
         CPropertyAction *pAct = new CPropertyAction (this, &AndorCamera::OnCameraName);
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

      for(int i = 0; i < numAmplifiers; ++i) {
         int i_nameLength(21);
         char * sz_ampName = new char[i_nameLength];
         GetAmpDesc(i, sz_ampName, i_nameLength);
         vAvailAmps.push_back(std::string(sz_ampName));
         mapAmps[std::string(sz_ampName)] = i;
      }
      if(numAmplifiers > 1)
      {
         if(!HasProperty(g_OutputAmplifier))
         {
            CPropertyAction *pAct = new CPropertyAction (this, &AndorCamera::OnOutputAmplifier);
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
         char * buffer = new char[64];
         sprintf(buffer, "%d. %dbit",(i+1), depth);
         std::string temp(buffer);
         vChannels.push_back(temp);
         delete [] buffer;
      }
      if(numADChannels > 1)
      {

         if(!HasProperty(g_ADChannel))
         {
            CPropertyAction *pAct = new CPropertyAction (this, &AndorCamera::OnADChannel);
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
      tempFrameX_ = fullFrameX_;
      tempFrameY_ = fullFrameY_;
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

      ret = SetReadMode(4); // image mode
      if (ret != DRV_SUCCESS)
         return ret;

      // binning
      if(!HasProperty(MM::g_Keyword_Binning))
      {
         CPropertyAction *pAct = new CPropertyAction (this, &AndorCamera::OnBinning);
         nRet = CreateProperty(MM::g_Keyword_Binning, "1", MM::Integer, false, pAct);
         assert(nRet == DEVICE_OK);
      }
      else
      {
         nRet = SetProperty(MM::g_Keyword_Binning,  "1");   
         if (nRet != DEVICE_OK)
            return nRet;
      }

      vector<string> binValues;
      binValues.push_back("1");
      binValues.push_back("2");
      binValues.push_back("4");
      binValues.push_back("8");
      nRet = SetAllowedValues(MM::g_Keyword_Binning, binValues);
      if (nRet != DEVICE_OK)
         return nRet;

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

      int InternalShutter;
      ret = IsInternalMechanicalShutter(&InternalShutter);
      if(InternalShutter == 0)
         bShutterIntegrated_ = false;
      else
      {
         bShutterIntegrated_ = true;
         if(!HasProperty("InternalShutter"))
         {
            pAct = new CPropertyAction (this, &AndorCamera::OnInternalShutter);
            nRet = CreateProperty("InternalShutter", g_ShutterMode_Open, MM::String, false, pAct);
            assert(nRet == DEVICE_OK);
         }

         vector<string> shutterValues;
         shutterValues.push_back(g_ShutterMode_Open);
         shutterValues.push_back(g_ShutterMode_Closed);
         nRet = SetAllowedValues("InternalShutter", shutterValues);
         if (nRet != DEVICE_OK)
            return nRet;
         nRet = SetProperty("InternalShutter", shutterValues[0].c_str());
         if (nRet != DEVICE_OK)
            return nRet;
      }
      int ShutterMode = 1;  //0: auto, 1: open, 2: close
      ret = SetShutter(1, ShutterMode, 20,20);//Opened any way because some old AndorCamera has no flag for IsInternalMechanicalShutter


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
      int numPreAmpGain;
      ret = GetNumberPreAmpGains(&numPreAmpGain);
      if (ret != DRV_SUCCESS)
         return ret;
      if (numPreAmpGain > 0 ) {
            
        char PreAmpGainBuf[10];
        PreAmpGains_.clear();
        for (int i=0; i<numPreAmpGain; i++)
        {
            float pag;
            ret = GetPreAmpGain(i, &pag); 
            if (ret != DRV_SUCCESS)
                return ret;
            sprintf(PreAmpGainBuf, "%.2f", pag);
            PreAmpGains_.push_back(PreAmpGainBuf);
        }
        if (PreAmpGains_.empty())
            return ERR_INVALID_PREAMPGAIN;

        if(!HasProperty("Pre-Amp-Gain"))
        {
            pAct = new CPropertyAction (this, &AndorCamera::OnPreAmpGain);
            if(numPreAmpGain>1)
                nRet = CreateProperty("Pre-Amp-Gain", PreAmpGains_[numPreAmpGain-1].c_str(), MM::String, false, pAct);
            else
                nRet = CreateProperty("Pre-Amp-Gain", PreAmpGains_[numPreAmpGain-1].c_str(), MM::String, true, pAct);
            assert(nRet == DEVICE_OK);
        }
        nRet = SetAllowedValues("Pre-Amp-Gain", PreAmpGains_);
        nRet = SetProperty("Pre-Amp-Gain", PreAmpGains_[PreAmpGains_.size()-1].c_str());
        PreAmpGain_ = PreAmpGains_[numPreAmpGain-1];
        if(numPreAmpGain > 1)
        {
            ret = SetPreAmpGain(numPreAmpGain-1);
            if (ret != DRV_SUCCESS)
                return ret;
        }
      }

      // Vertical Shift Speed
      int numVSpeed;
      ret = GetNumberVSSpeeds(&numVSpeed);
      if (ret != DRV_SUCCESS)
         return ret;

      char VSpeedBuf[10];
      VSpeeds_.clear();
      for (int i=0; i<numVSpeed; i++)
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

      if(!HasProperty("VerticalSpeed"))
      {
         pAct = new CPropertyAction (this, &AndorCamera::OnVSpeed);
         if(numVSpeed>1)
            nRet = CreateProperty("VerticalSpeed", VSpeeds_[numVSpeed-1].c_str(), MM::String, false, pAct);
         else
            nRet = CreateProperty("VerticalSpeed", VSpeeds_[numVSpeed-1].c_str(), MM::String, true, pAct);
         assert(nRet == DEVICE_OK);
      }
      nRet = SetAllowedValues("VerticalSpeed", VSpeeds_);
      assert(nRet == DEVICE_OK);
      nRet = SetProperty("VerticalSpeed", VSpeeds_[VSpeeds_.size()-1].c_str());
      VSpeed_ = VSpeeds_[numVSpeed-1];
      assert(nRet == DEVICE_OK);


      // Vertical Clock Voltage 
      int numVCVoltages;
      ret = GetNumberVSAmplitudes(&numVCVoltages);
      if (ret != DRV_SUCCESS) {
         numVCVoltages = 0;
         ostringstream eMsg;
         eMsg << "Andor driver returned error code: " << ret << " to GetNumberVSAmplitudes";
         LogMessage(eMsg.str().c_str(), true);
      }
      VCVoltages_.clear();
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


      // camera temperature
      // temperature range
      std::string strTips("");
      int minTemp, maxTemp;

      if(mb_canSetTemp) {

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

         if(minTemp < -70) {
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

      if((caps.ulAcqModes & AC_ACQMODE_FRAMETRANSFER) == AC_ACQMODE_FRAMETRANSFER) {
         if(caps.ulSetFunctions & AC_SETFUNCTION_CROPMODE) {
           ret = createIsolatedCropModeProperty(&caps);
           if(ret != DRV_SUCCESS) {
             return ret;
           }
         }
      }

      // actual interval
      // used by the application to get information on the actual camera interval
      if(!HasProperty(MM::g_Keyword_ActualInterval_ms))
      {
         pAct = new CPropertyAction (this, &AndorCamera::OnActualIntervalMS);
         nRet = CreateProperty(MM::g_Keyword_ActualInterval_ms, "0.0", MM::Float, false, pAct);
      }
      else
      {
         nRet = SetProperty(MM::g_Keyword_ActualInterval_ms, "0.0");
      }
      assert(nRet == DEVICE_OK);


      if(!HasProperty(MM::g_Keyword_ReadoutTime))
      {
         pAct = new CPropertyAction (this, &AndorCamera::OnReadoutTime);
         nRet = CreateProperty(MM::g_Keyword_ReadoutTime, "1", MM::Integer, true, pAct);
      }
      else
      {
         nRet = SetProperty(MM::g_Keyword_ReadoutTime, "1");
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

      // SpuriousNoiseFilter
      if(caps.ulFeatures&AC_FEATURES_REALTIMESPURIOUSNOISEFILTER) //some cameras might not support this
      {
        if(!HasProperty("SpuriousNoiseFilter"))
        {
           pAct = new CPropertyAction (this, &AndorCamera::OnSpuriousNoiseFilter);
           nRet = CreateProperty("SpuriousNoiseFilter", "None", MM::String, false, pAct);
           assert(nRet == DEVICE_OK);
        }
        else
        {
           nRet = SetProperty("SpuriousNoiseFilter",  "None");  
           if (nRet != DEVICE_OK)
              return nRet;
        }

        SetProperty("SpuriousNoiseFilter",  "None");
        vector<string> CCValues;
        CCValues.push_back("None");
        CCValues.push_back("Median");
        CCValues.push_back("Level Above");
        nRet = SetAllowedValues("SpuriousNoiseFilter", CCValues);
        
        if (nRet != DEVICE_OK)
           return nRet;
      

        // SpuriousNoiseFilterThreshold
        if(!HasProperty("SpuriousNoiseFilterThreshold"))
        {
           pAct = new CPropertyAction (this, &AndorCamera::OnSpuriousNoiseFilterThreshold);
           nRet = CreateProperty("SpuriousNoiseFilterThreshold", "0.0", MM::Float, false, pAct);
        }
        else
        {
           nRet = SetProperty("SpuriousNoiseFilterThreshold", "0.0");
        }

         // Description
        if (!HasProperty("SpuriousNoiseFilterDescription"))
        {
           pAct = new CPropertyAction (this, &AndorCamera::OnSpuriousNoiseFilterDescription);
           nRet = CreateProperty("SpuriousNoiseFilterDescription", "Recommended Range", MM::String, true, pAct);
        }

        assert(nRet == DEVICE_OK);
      }

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


      // synchronize all properties
      // --------------------------
      /*
      nRet = UpdateStatus();
      if (nRet != DEVICE_OK)
      return nRet;
      */

      // setup the buffer
      // ----------------
      nRet = ResizeImageBuffer();
      if (nRet != DEVICE_OK)
         return nRet;

      // explicitely set properties which are not readable from the camera
      nRet = SetProperty(MM::g_Keyword_Binning, "1");
      if (nRet != DEVICE_OK)
         return nRet;

      if(bShutterIntegrated_)
      {
         nRet = SetProperty("InternalShutter", g_ShutterMode_Open);
         if (nRet != DEVICE_OK)
            return nRet;
      }
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
      GetReadoutTime();

      nRet = UpdateStatus();
      if (nRet != DEVICE_OK)
         return nRet;

      initialized_ = true;

      if (bSoftwareTriggerSupported_)
      {
         iCurrentTriggerMode_ = SOFTWARE;
         strCurrentTriggerMode_ = "Software";
         UpdateSnapTriggerMode();
      }

      PrepareSnap();

      return DEVICE_OK;
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


   int AndorCamera::PrepareSnap()
   {
      DriverGuard dg(this);

      int ret;
      if (initialized_ && !sequenceRunning_ && !sequencePaused_) {
         LogMessage("PrepareSnap();",false);
         if(iCurrentTriggerMode_ == SOFTWARE)
            ret = SetFrameTransferMode(0);  //Software trigger mode can not be used in FT mode

         if(!IsAcquiring())
         {
            SetIsolatedCropMode(0, currentCropHeight_, currentCropWidth_, 1, 1);
            SetImage(binSize_, binSize_, roi_.x+1, roi_.x+roi_.xSize, roi_.y+1, roi_.y+roi_.ySize);
            GetReadoutTime(); 
            if (iCurrentTriggerMode_ == SOFTWARE || iCurrentTriggerMode_ == EXTERNAL)
            {
               ret = StartAcquisition();
               if (ret != DRV_SUCCESS)
                  return ret;
            }
            else // iCurrentTriggerMode_ == INTERNAL
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
   * This wait is implemented by sleeping ActualInterval_ms_ - ReadoutTime_ + 0.99 ms.
   * Note that this is likely not long enough when using internal triggering.
   */
   int AndorCamera::SnapImage()
   {
      { // scope for driver guard
         DriverGuard dg(this);

         if (sequenceRunning_)   // If we are in the middle of a SequenceAcquisition
            return ERR_BUSY_ACQUIRING;

         if(iCurrentTriggerMode_ == SOFTWARE) 
         {
            /* int ret = */ PrepareSnap();
            SendSoftwareTrigger();
         }
         else if (iCurrentTriggerMode_ == INTERNAL) 
         {        
            AbortAcquisition();
            int status = DRV_ACQUIRING;
            int error = DRV_SUCCESS;
            while (error == DRV_SUCCESS && status == DRV_ACQUIRING) {
              error = GetStatus(&status); 
            }
            SetIsolatedCropMode(0, currentCropHeight_, currentCropWidth_, 1, 1);
            SetImage(binSize_, binSize_, roi_.x+1, roi_.x+roi_.xSize, roi_.y+1, roi_.y+roi_.ySize);
            StartAcquisition();
         }
      }

      CDeviceUtils::SleepMs((long) (ActualInterval_ms_ - ReadoutTime_ + 0.99)); 

      return DEVICE_OK;
   }


   const unsigned char* AndorCamera::GetImageBuffer()
   {
      DriverGuard dg(this);

      if (IsAcquiring() )
      {
         int ret = WaitForAcquisitionByHandleTimeOut(myCameraID_, imageTimeOut_ms_);
         if (ret != DRV_SUCCESS)
            return 0;
      }

      pImgBuffer_ = GetAcquiredImage();
      assert(img_.Depth() == 2);
      assert(pImgBuffer_!=0);
      unsigned char* rawBuffer = pImgBuffer_;

      PrepareSnap();
      return (unsigned char*)rawBuffer;
   }


   unsigned char* AndorCamera::GetAcquiredImage() {
      assert(fullFrameBuffer_ != 0);
      int array_Length = roi_.xSize/binSize_ * roi_.ySize/binSize_;

      unsigned int ret = GetMostRecentImage16((WORD*)fullFrameBuffer_, array_Length);
      if(ret != DRV_SUCCESS) {
         std::ostringstream os;
         os << "Andor driver reports error #: " << ret;
         LogMessage(os.str().c_str(), false);
         return 0;
      }

      return (unsigned char*)fullFrameBuffer_;
   }


   /**
   * Readout time
   */ 
   long AndorCamera::GetReadoutTime()
   {

      at_32 ReadoutTime;
      float fReadoutTime;
      if(fpGetReadOutTime!=0 && (iCurrentTriggerMode_ == SOFTWARE))
      {
         fpGetReadOutTime(&fReadoutTime);
         ReadoutTime = long(fReadoutTime * 1000);
      }
      else
      {
         unsigned ret = SetExposureTime(0.0);
         if (DRV_SUCCESS != ret)
            return (int)ret;
         float fExposure, fAccumTime, fKineticTime;
         GetAcquisitionTimings(&fExposure,&fAccumTime,&fKineticTime);
         ReadoutTime = long(fKineticTime * 1000.0);
         ret = SetExposureTime((float)(expMs_ / 1000.0));
         if (DRV_SUCCESS != ret)
            return (int)ret;
      }
      if(ReadoutTime<=0)
         ReadoutTime=35;
      ReadoutTime_ = ReadoutTime;

      float fExposure, fAccumTime, fKineticTime;
      GetAcquisitionTimings(&fExposure,&fAccumTime,&fKineticTime);
      ActualInterval_ms_ = fKineticTime * 1000.0f;
      SetProperty(MM::g_Keyword_ActualInterval_ms, CDeviceUtils::ConvertToString(ActualInterval_ms_)); 

      //whenever readout needs update, keepcleantime also needs update
      at_32 KeepCleanTime;
      float fKeepCleanTime;
      if(fpGetKeepCleanTime!=0 && (iCurrentTriggerMode_ == SOFTWARE))
      {
         fpGetKeepCleanTime(&fKeepCleanTime);
         KeepCleanTime = long(fKeepCleanTime * 1000);
      }
      else
         KeepCleanTime=10;
      if(KeepCleanTime<=0)
         KeepCleanTime=10;
      KeepCleanTime_ = KeepCleanTime;


      return ReadoutTime_;
   }


   /**
   * Sets the image Region of Interest (ROI).
   * The internal representation of the ROI uses the full frame coordinates
   * in combination with binning factor.
   */
   int AndorCamera::SetROI(unsigned uX, unsigned uY, unsigned uXSize, unsigned uYSize)
   {
      DriverGuard dg(this);

      if (Busy())
         return ERR_BUSY_ACQUIRING;

      //added to use RTA
      SetToIdle();

      ROI oldRoi = roi_;

      roi_.x = uX * binSize_;
      roi_.y = uY * binSize_;
      roi_.xSize = uXSize * binSize_;
      roi_.ySize = uYSize * binSize_;

      if (roi_.x + roi_.xSize > fullFrameX_ || roi_.y + roi_.ySize > fullFrameY_)
      {
         roi_ = oldRoi;
         return ERR_INVALID_ROI;
      }

      // adjust image extent to conform to the bin size
      roi_.xSize -= roi_.xSize % binSize_;
      roi_.ySize -= roi_.ySize % binSize_;

      unsigned uret = SetImage(binSize_, binSize_, roi_.x+1, roi_.x+roi_.xSize,
         roi_.y+1, roi_.y+roi_.ySize);
      if (uret != DRV_SUCCESS)
      {
         roi_ = oldRoi;
         return uret;
      }

      GetReadoutTime();

      int ret = ResizeImageBuffer();
      if (ret != DEVICE_OK)
      {
         roi_ = oldRoi;
         return ret;
      }

      PrepareSnap();

      return DEVICE_OK;
   }

   unsigned AndorCamera::GetBitDepth() const
   {
      DriverGuard dg(this);

      int depth;
      // TODO: channel 0 hardwired ???
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
      DriverGuard dg(this);

      if (sequenceRunning_)
         return ERR_BUSY_ACQUIRING;

      //added to use RTA
      SetToIdle();

      roi_.x = 0;
      roi_.y = 0;
      roi_.xSize = fullFrameX_;
      roi_.ySize = fullFrameY_;

      // adjust image extent to conform to the bin size
      roi_.xSize -= roi_.xSize % binSize_;
      roi_.ySize -= roi_.ySize % binSize_;
      unsigned uret = SetImage(binSize_, binSize_, roi_.x+1, roi_.x+roi_.xSize,
         roi_.y+1, roi_.y+roi_.ySize);
      if (uret != DRV_SUCCESS)
         return uret;


      GetReadoutTime();

      int ret = ResizeImageBuffer();
      if (ret != DEVICE_OK)
         return ret;

      PrepareSnap();

      return DEVICE_OK;
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
      DriverGuard dg(this);
      if (eAct == MM::AfterSet)
      {
         if (sequenceRunning_)
            return ERR_BUSY_ACQUIRING;

         //added to use RTA
         SetToIdle();

         long bin;
         pProp->Get(bin);
         if (bin <= 0)
            return DEVICE_INVALID_PROPERTY_VALUE;

         // adjust roi to accomodate the new bin size
         ROI oldRoi = roi_;
         roi_.xSize = fullFrameX_;
         roi_.ySize = fullFrameY_;
         roi_.x = 0;
         roi_.y = 0;

         // adjust image extent to conform to the bin size
         roi_.xSize -= roi_.xSize % bin;
         roi_.ySize -= roi_.ySize % bin;

         // setting the binning factor will reset the image to full frame
         unsigned aret = SetImage(bin, bin, roi_.x+1, roi_.x+roi_.xSize,
            roi_.y+1, roi_.y+roi_.ySize);
         if (aret != DRV_SUCCESS)
         {
            roi_ = oldRoi;
            return aret;
         }

         GetReadoutTime();


         // apply new settings
         binSize_ = (int)bin;
         int ret = ResizeImageBuffer();
         if (ret != DEVICE_OK)
         {
            roi_ = oldRoi;
            return ret;
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
         DriverGuard dg(this);
         pProp->Set(currentExpMS_);
      }
      else if (eAct == MM::AfterSet)
      {
         double exp;
         pProp->Get(exp);

         if(fabs(exp-currentExpMS_)>0.001)
         {
            bool acquiring = sequenceRunning_;
            if (acquiring)
               StopSequenceAcquisition(true);

            if (sequenceRunning_)
               return ERR_BUSY_ACQUIRING;
             
            {
				   DriverGuard dg(this);
				   currentExpMS_ = exp;

				   unsigned ret = SetExposureTime((float)(exp / 1000.0));
				   if (DRV_SUCCESS != ret)
					   return (int)ret;
				   expMs_ = exp;

				   UpdateHSSpeeds();
            }

            if (acquiring)
               StartSequenceAcquisition(sequenceLength_ - imageCounter_, intervalMs_, stopOnOverflow_);
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
      DriverGuard dg(this);
      if (eAct == MM::AfterSet)
      {
         bool acquiring = sequenceRunning_;
         if (acquiring)
            StopSequenceAcquisition(true);

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
                  GetReadoutTime();
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
         pProp->Set(readoutModes_[HSSpeedIdx_].c_str());
      }
      return DEVICE_OK;
   }

   /**
   * Provides information on readout time.
   * TODO: Not implemented
   */
   int AndorCamera::OnReadoutTime(MM::PropertyBase* pProp, MM::ActionType eAct)
   {
      if (eAct == MM::BeforeGet)
      {
         pProp->Set(ReadoutTime_);
      }

      return DEVICE_OK;
   }


   /**
   * Set camera "regular" gain.
   */
   int AndorCamera::OnGain(MM::PropertyBase* pProp, MM::ActionType eAct)
   {
      DriverGuard dg(this);
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

         if (acquiring)
            StartSequenceAcquisition(sequenceLength_ - imageCounter_, intervalMs_, stopOnOverflow_);

         PrepareSnap();
      }
      else if (eAct == MM::BeforeGet)
      {
         pProp->Set(currentGain_);
      }
      return DEVICE_OK;
   }

   /**
   * Set camera "regular" gain.
   */
   int AndorCamera::OnEMSwitch(MM::PropertyBase* pProp, MM::ActionType eAct)
   {
      DriverGuard dg(this);
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
  
         if (sequenceRunning_)
            return ERR_BUSY_ACQUIRING;

         //added to use RTA
         if(!(iCurrentTriggerMode_ == SOFTWARE))
            SetToIdle();

         unsigned ret = DRV_SUCCESS;
         if (EMSwitch == "On") {
            ret = SetEMCCDGain((int)currentGain_);
            // Don't change EMGain property limits here -- causes errors.
            EMSwitch_ = true;
         } else {
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
      DriverGuard dg(this);
      if (eAct == MM::AfterSet)
      {
         bool acquiring = sequenceRunning_;
         if (acquiring)
            StopSequenceAcquisition(true);

         if (sequenceRunning_)
            return ERR_BUSY_ACQUIRING;

         std::string trigger;
         pProp->Get(trigger);
         if(trigger == strCurrentTriggerMode_)
            return DEVICE_OK;

         SetToIdle();

         if(trigger == "Software")
         {  
            if (softwareTriggerUsed_)
               return ERR_SOFTWARE_TRIGGER_IN_USE;
            iCurrentTriggerMode_ = SOFTWARE;
            softwareTriggerUsed_ = true;
         }
         else if(trigger == "External")
         {
            iCurrentTriggerMode_ = EXTERNAL;
            if (strCurrentTriggerMode_ == "Software")
               softwareTriggerUsed_ = false;
         }
         else
         {
            iCurrentTriggerMode_ = INTERNAL;
            if (strCurrentTriggerMode_ == "Software")
               softwareTriggerUsed_ = false;
         }

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
      DriverGuard dg(this);
      if (eAct == MM::AfterSet)
      {
         bool acquiring = sequenceRunning_;
         if (acquiring)
            StopSequenceAcquisition(true);

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
      DriverGuard dg(this);
      if (eAct == MM::AfterSet)
      {
         bool acquiring = sequenceRunning_;
         if (acquiring)
            StopSequenceAcquisition(true);

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
      DriverGuard dg(this);
      if (eAct == MM::AfterSet)
      {
         bool acquiring = sequenceRunning_;
         if (acquiring)
            StopSequenceAcquisition(true);

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
      DriverGuard dg(this);
      if (eAct == MM::AfterSet)
      {
         bool acquiring = sequenceRunning_;
         if (acquiring)
            StopSequenceAcquisition(true);

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
                  GetReadoutTime();
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
      DriverGuard dg(this);
      if (eAct == MM::AfterSet)
      {
         bool acquiring = sequenceRunning_;
         if (acquiring)
            StopSequenceAcquisition(true);

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
      DriverGuard dg(this);
      if (eAct == MM::AfterSet)
      {
         bool acquiring = sequenceRunning_;
         if (acquiring)
            StopSequenceAcquisition(true);

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
      DriverGuard dg(this);
      if (eAct == MM::AfterSet)
      {
         bool acquiring = sequenceRunning_;
         if (acquiring)
            StopSequenceAcquisition(true);

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

   int AndorCamera::OnInternalShutter(MM::PropertyBase* pProp, MM::ActionType eAct)
   {
      DriverGuard dg(this);
      if (eAct == MM::AfterSet)
      {
         bool acquiring = sequenceRunning_;
         if (acquiring)
            StopSequenceAcquisition(true);

         if (sequenceRunning_)
            return ERR_BUSY_ACQUIRING;

         //added to use RTA
         SetToIdle();

         string mode;
         pProp->Get(mode);
         int modeIdx = 0;
         if (mode.compare(g_ShutterMode_Auto) == 0)
            modeIdx = 0;
         else if (mode.compare(g_ShutterMode_Open) == 0)
            modeIdx = 1;
         else if (mode.compare(g_ShutterMode_Closed) == 0)
            modeIdx = 2;
         else
            return DEVICE_INVALID_PROPERTY_VALUE;

         // wait for camera to finish acquiring
         int status = DRV_IDLE;
         unsigned ret = GetStatus(&status);
         while (status == DRV_ACQUIRING && ret == DRV_SUCCESS)
            ret = GetStatus(&status);

         // the first parameter in SetShutter, must be "1" in order for
         // the shutter logic to work as described in the documentation
         ret = SetShutter(1, modeIdx, 20,20);//0, 0);
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

   
int AndorCamera::OnSpuriousNoiseFilter(MM::PropertyBase* pProp, MM::ActionType eAct)
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

         string spuriousNoiseFilterStr;
         long spuriousNoiseFilter = 0;
         pProp->Get(spuriousNoiseFilterStr);
         
         if(spuriousNoiseFilterStr.compare("None") == 0)
         {
           spuriousNoiseFilter = 0;
           spuriousNoiseFilterDescriptionStr_ = "Recommended Range";
         }
         else if(spuriousNoiseFilterStr.compare("Median") == 0)
         {
           spuriousNoiseFilter = 1;
           spuriousNoiseFilterDescriptionStr_ = "Recommended Range 2-10";
         }
         else if(spuriousNoiseFilterStr.compare("Level Above") == 0)
         {
           spuriousNoiseFilter = 2;
           spuriousNoiseFilterDescriptionStr_ = "Recommended Range 10-50";
         }

         if(spuriousNoiseFilterStr == spuriousNoiseFilter_)
            return DEVICE_OK;
          
         // wait for camera to finish acquiring
         int status = DRV_IDLE;
         unsigned int ret = GetStatus(&status);
         while (status == DRV_ACQUIRING && ret == DRV_SUCCESS)
            ret = GetStatus(&status);

         ret = Filter_SetMode(spuriousNoiseFilter);
          if (ret != DRV_SUCCESS)
         {           
            return DEVICE_CAN_NOT_SET_PROPERTY;
         }
        
         pProp->Set(spuriousNoiseFilterStr.c_str());
         spuriousNoiseFilter_ = spuriousNoiseFilterStr;
         if (acquiring)
            StartSequenceAcquisition(sequenceLength_ - imageCounter_, intervalMs_, stopOnOverflow_);
         PrepareSnap();
         
         SetProperty("SpuriousNoiseFilterDescription", spuriousNoiseFilterDescriptionStr_.c_str());  
      }
      else if (eAct == MM::BeforeGet)
      {
         pProp->Set(spuriousNoiseFilter_.c_str());
      }
      return DEVICE_OK;
   }

   int AndorCamera::OnSpuriousNoiseFilterThreshold(MM::PropertyBase* pProp, MM::ActionType eAct)
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

         double spuriousNoiseFilterThreshold = 0;
         pProp->Get(spuriousNoiseFilterThreshold);
         
         if(spuriousNoiseFilterThreshold == spuriousNoiseFilterThreshold_)
            return DEVICE_OK;
          
         // wait for camera to finish acquiring
         int status = DRV_IDLE;
         unsigned int ret = GetStatus(&status);
         while (status == DRV_ACQUIRING && ret == DRV_SUCCESS)
            ret = GetStatus(&status);

         ret  = Filter_SetThreshold(static_cast<float>(spuriousNoiseFilterThreshold));
         if (ret != DRV_SUCCESS)
         {           
            return DEVICE_CAN_NOT_SET_PROPERTY;
         }
        
         pProp->Set(spuriousNoiseFilterThreshold);
         spuriousNoiseFilterThreshold_ = spuriousNoiseFilterThreshold;
         if (acquiring)
            StartSequenceAcquisition(sequenceLength_ - imageCounter_, intervalMs_, stopOnOverflow_);
         PrepareSnap();
      }
      else if (eAct == MM::BeforeGet)
      {
         pProp->Set(spuriousNoiseFilterThreshold_);
      }
      return DEVICE_OK;
   }  
  
   int AndorCamera::OnSpuriousNoiseFilterDescription(MM::PropertyBase* pProp, MM::ActionType eAct)
   {
      if (eAct == MM::BeforeGet)
      {
        pProp->Set(spuriousNoiseFilterDescriptionStr_.c_str());
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
         bool acquiring = sequenceRunning_;
         if (acquiring)
            StopSequenceAcquisition(true);

         if (sequenceRunning_)
            return ERR_BUSY_ACQUIRING;

         //added to use RTA
         SetToIdle();

      if (eAct == MM::AfterSet)
      {
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
      return DEVICE_OK;
   }
   
   void AndorCamera::UpdateOAParams(const char* OAModeName)
   {
      unsigned int ui_retVal;
      int i_temp;
      float f_temp;
      char c_temp[256];
      memset(c_temp, '\0', 256);  

      ui_retVal = ::OA_GetString(OAModeName, "mode_description", &c_temp[0], 256);
      optAcquireDescriptionStr_ = c_temp;
      SetProperty("OptAcquireMode Description", c_temp);  
      ui_retVal = ::OA_GetString(OAModeName, "frame_transfer", &c_temp[0], 256);
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
        char * buffer = new char[64];
        int depth;
        ::GetBitDepth(i, &depth);
        sprintf(buffer, "%d. %dbit",(i+1), depth);

        char speedBuf[100];
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

      ui_retVal = ::OA_GetString(OAModeName, "output_amplifier", &c_temp[0], 256);
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
      SetProperty("VerticalSpeed", c_temp);        
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
      if (eAct == MM::AfterSet)
      {
         double ActualInvertal_ms;
         pProp->Get(ActualInvertal_ms);
         if(ActualInvertal_ms == ActualInterval_ms_)
            return DEVICE_OK;
         pProp->Set(ActualInvertal_ms);
         ActualInterval_ms_ = (float)ActualInvertal_ms;
      }
      else if (eAct == MM::BeforeGet)
      {
         pProp->Set(CDeviceUtils::ConvertToString(ActualInterval_ms_));
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
         else {
            return DEVICE_INVALID_PROPERTY_VALUE;
         }

         if (bFrameTransfer_ == false){
           SetProperty(g_cropMode, "Off");           
         } 

         // wait for camera to finish acquiring
         SetToIdle();

         if(bOldFTMode != bFrameTransfer_) {

            unsigned int ret = SetFrameTransferMode(modeIdx);
            if (ret != DRV_SUCCESS) {
               return ret;
            }
            int noAmps;
            ret = ::GetNumberAmp(&noAmps);
            if (ret != DRV_SUCCESS) {
               return ret;
            }

            ::PrepareAcquisition();
            if(HasProperty(g_OutputAmplifier)) {
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
               SetAllowedValues(g_OutputAmplifier, vAvailAmps);
               UpdateProperty(g_OutputAmplifier);

               if (initialized_) {
                  OnPropertiesChanged();
               }

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
               UpdateHSSpeeds();
            }

            if (acquiring)
               StartSequenceAcquisition(sequenceLength_ - imageCounter_, intervalMs_, stopOnOverflow_);

         }



         PrepareSnap();
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
      DriverGuard dg(this);
      if (eAct == MM::AfterSet)
      {
         bool acquiring = sequenceRunning_;
         if (acquiring)
            StopSequenceAcquisition(true);

         if (sequenceRunning_)
            return ERR_BUSY_ACQUIRING;

         SetToIdle();

         string strAmp;
         pProp->Get(strAmp);
         if(strAmp.compare(strCurrentAmp) != 0 ) {
            strCurrentAmp = strAmp;
            OutputAmplifierIndex_ = mapAmps[strAmp];


            unsigned ret = SetOutputAmplifier(OutputAmplifierIndex_);
            if (ret != DRV_SUCCESS) {
               return (int)ret;
            }

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

            int numPreAmpGain;
            ret = GetNumberPreAmpGains(&numPreAmpGain);
            if (ret != DRV_SUCCESS) {
               return ret;
            }
            char PreAmpGainBuf[10];
            PreAmpGains_.clear();
            for (int i=0; i<numPreAmpGain; i++)
            {
               float pag;
               ret = GetPreAmpGain(i, &pag); 
               if (ret != DRV_SUCCESS) {
                  return ret;
               }
               sprintf(PreAmpGainBuf, "%.2f", pag);
               PreAmpGains_.push_back(PreAmpGainBuf);
            }
            std::vector<std::string>::iterator pagIter, pagIterLast;
            pagIterLast = PreAmpGains_.end();
            bool resetGain(true);
            for(pagIter = PreAmpGains_.begin(); pagIter != pagIterLast; ++pagIter) {
               if(PreAmpGain_.compare(*pagIter) == 0) {
                  resetGain = false;
                  break;
               }
            }
            if(resetGain && PreAmpGains_.size() > 0) {
               ret = SetPreAmpGain(0);
               if (ret != DRV_SUCCESS) {
                  return ret;
               }
               PreAmpGain_ = PreAmpGains_[0];
            }
            if(HasProperty("Pre-Amp-Gain")) {
               int rc = SetAllowedValues("Pre-Amp-Gain",PreAmpGains_);
               if (rc != DEVICE_OK) {
                  return ret;
               }
            }

            if(HasProperty(g_OutputAmplifier)) {
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
               UpdateHSSpeeds();
            }

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

      GetReadoutTime();

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
      at_32 acc;
      at_32 series(0);
      at_32 seriesInit;
      unsigned ret;

      printf("Starting Andor svc\n");

      long timePrev = GetTickCount();
      long imageWait = 0;
		std::ostringstream os;

      {
         DriverGuard dg(camera_);
		   ret = GetAcquisitionProgress(&acc, &seriesInit);

		   os << "GetAcquisitionProgress returned: " << acc << " and: " << seriesInit;
		   printf ("%s\n", os.str().c_str());
		   os.str("");

		   if (ret != DRV_SUCCESS)
		   {
			   camera_->StopCameraAcquisition();
			   os << "Error in GetAcquisitionProgress: " << ret;
			   printf("%s\n", os.str().c_str());
			   //core_->LogMessage(camera_, os.str().c_str(), true);
			   return ret;
		   }
      }
	  
      /*
      float fExposure, fAccumTime, fKineticTime;
      printf ("Before GetAcquisition timings\n");
      ret = GetAcquisitionTimings(&fExposure,&fAccumTime,&fKineticTime);
      if (ret != DRV_SUCCESS)
      printf ("Error in GetAcquisition Timings\n");
      os << "Exposure: " << fExposure << " AcummTime: " << fAccumTime << " KineticTime: " << fKineticTime;
      printf ("%s\n", os.str().c_str());
      os.str("")
      float ActualInterval_ms = fKineticTime * 1000.0f;
      waitTime = (long) (ActualInterval_ms / 5);

      os << "WaitTime: " << waitTime;
      //core_->LogMessage(camera_, os.str().c_str(), true);
      printf("%s\n", os.str().c_str());
      os.str("");
      */

      // wait for frames to start coming in
      do
      {
         {
            DriverGuard dg(camera_);
            ret = GetAcquisitionProgress(&acc, &series);
         }
         if (ret != DRV_SUCCESS)
         {
            camera_->StopCameraAcquisition();
            os << "Error in GetAcquisitionProgress: " << ret;
            printf("%s\n", os.str().c_str());
            os.str("");
            return ret;
         }

         CDeviceUtils::SleepMs(waitTime_);

      } while (series == seriesInit && !stop_);
      os << "Images appearing";
      printf("%s\n", os.str().c_str());
      os.str("");

      at_32 seriesPrev = 0;
      at_32 frmcnt = 0;

      do
      {

         {
            DriverGuard dg(camera_);
            //GetStatus(&status);
            ret = GetAcquisitionProgress(&acc, &series);
         }

         if (ret == DRV_SUCCESS)
         {
            if (series > seriesPrev)
            {
               // new frame arrived
               int retCode = camera_->PushImage();
               if (retCode != DEVICE_OK)
               {
                  os << "PushImage failed with error code " << retCode;
                  printf("%s\n", os.str().c_str());
                  os.str("");
                  //camera_->StopSequenceAcquisition();
                  //return ret;
               }

               // report time elapsed since previous frame
               //printf("Frame %d captured at %ld ms!\n", ++frameCounter, GetTickCount() - timePrev);
               seriesPrev = series;
               frmcnt++;
               timePrev = GetTickCount();
               // kdb 7/30/2009  
            } else 
            {
               imageWait = GetTickCount() - timePrev;
               if (imageWait > imageTimeOut_) {
                  os << "Time out reached at frame " << frmcnt;
                  printf("%s\n", os.str().c_str());
                  os.str("");
                  camera_->StopCameraAcquisition();
                  return 0;
               }
            }
            
            CDeviceUtils::SleepMs(waitTime_);

         }
      }

      while (ret == DRV_SUCCESS && frmcnt < numImages_ && !stop_);

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
         return 0;
      }

      if ((series-seriesInit) == numImages_)
      {
         printf("Did not get the intended number of images\n");
         camera_->StopCameraAcquisition();
         return 0;
      }

      os << "series: " << series << " seriesInit: " << seriesInit << " numImages: "<< numImages_;
      printf("%s\n", os.str().c_str());
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

      sequencePaused_ = false;
      stopOnOverflow_ = stopOnOverflow;
      sequenceLength_ = numImages;
      intervalMs_ = interval_ms;

      if(IsAcquiring())
      {
         SetToIdle();
      }
      LogMessage("Setting Trigger Mode", true);
      int ret0;
      if (iCurrentTriggerMode_ == SOFTWARE)
         ret0 = SetTriggerMode(0);  //set internal trigger for sequence acquisition. mode 0:internal, 1: ext, 6:ext start, 7:bulb, 10:software

      ostringstream os;
      os << "Started sequence acquisition: " << numImages << "images  at " << interval_ms << " ms" << endl;
      LogMessage(os.str().c_str());

      // prepare the camera
      int ret = SetAcquisitionMode(5); // run till abort
      if (ret != DRV_SUCCESS)
         return ret;
      LogMessage("Set acquisition mode to 5", true);

      LogMessage("Setting Frame Transfer mode on", true);
      if(bFrameTransfer_ && (iCurrentTriggerMode_ == SOFTWARE))
         ret0 = SetFrameTransferMode(1);  //FT mode might be turned off in SnapImage when Software trigger mode is used. Resume it here


      ret = SetReadMode(4); // image mode
      if (ret != DRV_SUCCESS)
         return ret;
      LogMessage("Set Read Mode to 4", true);

      // set AD-channel to 14-bit
      //   ret = SetADChannel(0);
      if (ret != DRV_SUCCESS)
         return ret;

      SetExposureTime((float) (expMs_/1000.0));

      LogMessage ("Set Exposure time", true);
      ret = SetNumberAccumulations(1);
      if (ret != DRV_SUCCESS)
      {
         SetAcquisitionMode(1);
         return ret;
      }
      LogMessage("Set Number of accumulations to 1", true);

      ret = SetKineticCycleTime((float)(interval_ms / 1000.0));
      if (ret != DRV_SUCCESS)
      {
         SetAcquisitionMode(1);
         return ret;
      }
      LogMessage("Set Kinetic cycle time", true);

      at_32 size;
      ret = GetSizeOfCircularBuffer(&size);
      if (ret != DRV_SUCCESS)
      {
         SetAcquisitionMode(1);
         return ret;
      }
      LogMessage("Get Size of circular Buffer", true);

      // re-apply the frame transfer mode setting
      char ftMode[MM::MaxStrLength];
      if(HasProperty(m_str_frameTransferProp.c_str())){
         ret = GetProperty(m_str_frameTransferProp.c_str(), ftMode);
         assert(ret == DEVICE_OK);
         int modeIdx = 0;
         if (strcmp(g_FrameTransferOn, ftMode) == 0)
            modeIdx = 1;
         else if (strcmp(g_FrameTransferOff, ftMode) == 0)
            modeIdx = 0;
         else
            return DEVICE_INVALID_PROPERTY_VALUE;
         os.str("");
         os << "Set Frame transfer mode to " << modeIdx;
         LogMessage(os.str().c_str(), true);

         ret = SetFrameTransferMode(modeIdx);

         if (modeIdx == 1 && cropModeSwitch_) {
            SetIsolatedCropMode(1, currentCropHeight_, currentCropWidth_, 1, 1);
            cropModeSwitch_ = true;
         }
      }
      if (ret != DRV_SUCCESS)
      {
         SetAcquisitionMode(1);
         return ret;
      }

      // start thread
      imageCounter_ = 0;

      os.str("");
      os << "Setting thread length to " << numImages << " Images";
      LogMessage(os.str().c_str(), true);
      seqThread_->SetLength(numImages);

      float fExposure, fAccumTime, fKineticTime;
      GetAcquisitionTimings(&fExposure,&fAccumTime,&fKineticTime);
      SetProperty(MM::g_Keyword_ActualInterval_ms, CDeviceUtils::ConvertToString((double)fKineticTime * 1000.0)); 
      ActualInterval_ms_ = fKineticTime * 1000.0f;
      os.str("");
      os << "Exposure: " << fExposure << " AcummTime: " << fAccumTime << " KineticTime: " << fKineticTime;
      LogMessage(os.str().c_str());
      float ActualInterval_ms = fKineticTime * 1000.0f;
      seqThread_->SetWaitTime((at_32) (ActualInterval_ms / 5));
      seqThread_->SetTimeOut(imageTimeOut_ms_);
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
         std::ostringstream os;
         os << "Andor driver returned error value: " << ret;
         LogMessage(os.str().c_str(), false);
         SetAcquisitionMode(1);
         return ret;
      } else 
      {
         seqThread_->Start();
         sequenceRunning_ = true;
      }

      return DEVICE_OK;
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

      if (!temporary)
        PrepareSnap();

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
   int AndorCamera::PushImage()
   {
      unsigned int width;
      unsigned int height;
      unsigned int bytesPerPixel;
       
      {
         DriverGuard dg(this);
         unsigned ret;
         // get the top most image from the driver
         if (stopOnOverflow_)
            ret = GetOldestImage16((WORD*)fullFrameBuffer_, roi_.xSize/binSize_ * roi_.ySize/binSize_);
         else
            ret = GetMostRecentImage16((WORD*)fullFrameBuffer_, roi_.xSize/binSize_ * roi_.ySize/binSize_);
         if (ret != DRV_SUCCESS)
            return (int)ret;

         width =  GetImageWidth();
         height = GetImageHeight();
         bytesPerPixel = GetImageBytesPerPixel();

      }

      // process image
      // imageprocesssor now called from core

      // create metadata
      char label[MM::MaxStrLength];
      this->GetLabel(label);


      MM::MMTime timestamp = this->GetCurrentMMTime();
      Metadata md;
      // Copy the metadata inserted by other processes:
      std::vector<std::string> keys = metadata_.GetKeys();
      for (unsigned int i= 0; i < keys.size(); i++) {
         md.put(keys[i], metadata_.GetSingleTag(keys[i].c_str()).GetValue().c_str());
      }

      md.put("Camera", label);   
      md.put(MM::g_Keyword_Metadata_StartTime, CDeviceUtils::ConvertToString(startTime_.getMsec()));
      md.put(MM::g_Keyword_Elapsed_Time_ms, CDeviceUtils::ConvertToString((timestamp - startTime_).getMsec()));
      md.put(MM::g_Keyword_Metadata_ImageNumber, CDeviceUtils::ConvertToString(imageCounter_));
      md.put(MM::g_Keyword_Binning, binSize_);

      MetadataSingleTag mstStartTime(MM::g_Keyword_Metadata_StartTime, label, true);
      mstStartTime.SetValue(CDeviceUtils::ConvertToString(startTime_.getMsec()));
      md.SetTag(mstStartTime);

      MetadataSingleTag mst(MM::g_Keyword_Elapsed_Time_ms, label, true);
      mst.SetValue(CDeviceUtils::ConvertToString(timestamp.getMsec()));
      md.SetTag(mst);

      MetadataSingleTag mstCount(MM::g_Keyword_Metadata_ImageNumber, label, true);
      mstCount.SetValue(CDeviceUtils::ConvertToString(imageCounter_));      
      md.SetTag(mstCount);

      MetadataSingleTag mstB(MM::g_Keyword_Binning, label, true);
      mstB.SetValue(CDeviceUtils::ConvertToString(binSize_));      
      md.SetTag(mstB);

      imageCounter_++;

      // This method inserts new image in the circular buffer (residing in MMCore)
      int retCode = GetCoreCallback()->InsertImage(this, (unsigned char*) fullFrameBuffer_,
         width,
         height,
         bytesPerPixel,
         md.Serialize().c_str());

      if (!stopOnOverflow_ && retCode == DEVICE_BUFFER_OVERFLOW)
      {
         // do not stop on overflow - just reset the buffer
         GetCoreCallback()->ClearImageBuffer(this);
         return GetCoreCallback()->InsertImage(this, (unsigned char*) fullFrameBuffer_,
            GetImageWidth(),
            GetImageHeight(),
            GetImageBytesPerPixel(),
            md.Serialize().c_str());
      } else
         return DEVICE_OK;
   }

   std::string AndorCamera::getCameraType() {
      std::string retVal("");
      AndorCapabilities caps;
      {
         DriverGuard dg(this);
         caps.ulSize = sizeof(AndorCapabilities);
         GetCapabilities(&caps);
      }

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
   int AndorCamera::OnCropModeSwitch(MM::PropertyBase* pProp, MM::ActionType eAct)
   {
      DriverGuard dg(this);
      if (eAct == MM::AfterSet)
      {
         std::string Switch;
         pProp->Get(Switch);
         if (Switch == "Off" && !cropModeSwitch_)
            return DEVICE_OK;
         if (Switch == "On" && cropModeSwitch_)
            return DEVICE_OK;

         bool acquiring = sequenceRunning_;
         if (acquiring)
            StopSequenceAcquisition(true);

         if (sequenceRunning_)
            return ERR_BUSY_ACQUIRING;

         if (Switch == "On"){
           SetProperty("FrameTransfer", "On");           
         } 

         //added to use RTA
         SetToIdle();

         unsigned ret = DRV_SUCCESS;
         if (Switch == "On") {
            SetAcquisitionMode(5);
            SetFrameTransferMode(1);
            ret = SetIsolatedCropMode(1, currentCropHeight_, currentCropWidth_, 1, 1);
            SetAcquisitionMode(1);
            fullFrameX_ = currentCropWidth_;
            fullFrameY_ = currentCropHeight_;
            cropModeSwitch_ = true;
         } 
         else {
            fullFrameX_ = tempFrameX_;
            fullFrameY_ = tempFrameY_;
            ret = SetIsolatedCropMode(0, currentCropHeight_, currentCropWidth_, 1, 1);
            cropModeSwitch_ = false;
         }

         if (initialized_) {
            OnPropertiesChanged();
         }

         if (DRV_SUCCESS != ret)
            return (int)ret;

         ROI oldRoi = roi_;
         roi_.xSize = fullFrameX_;
         roi_.ySize = fullFrameY_;
         roi_.x = 0;
         roi_.y = 0;

         // adjust image extent to conform to the bin size
         roi_.xSize -= roi_.xSize % binSize_;
         roi_.ySize -= roi_.ySize % binSize_;

         /* unsigned aret = */ SetImage(binSize_, binSize_, roi_.x+1, roi_.x+roi_.xSize,
            roi_.y+1, roi_.y+roi_.ySize);

         GetReadoutTime();

         ResizeImageBuffer();

         PrepareSnap();
      }
      else if (eAct == MM::BeforeGet)
      {
         if (cropModeSwitch_)
            pProp->Set("On");
         else
            pProp->Set("Off");
      }
      return DEVICE_OK;
   }


   /**
   * Set camera crop mode width.
   */
   int AndorCamera::OnCropModeWidth(MM::PropertyBase* pProp, MM::ActionType eAct)
   {
      DriverGuard dg(this);
      if (eAct == MM::AfterSet)
      {
         long cropWidth;
         pProp->Get(cropWidth);
         if (!cropModeSwitch_) {
            currentCropWidth_ = cropWidth;
            return DEVICE_OK;
         }
         if(cropWidth == currentCropWidth_)
            return DEVICE_OK;

         bool acquiring = sequenceRunning_;
         if (acquiring)
            StopSequenceAcquisition(true);

         if (sequenceRunning_)
            return ERR_BUSY_ACQUIRING;

         if (cropWidth < 1 ) 
            cropWidth = 1;
         if (cropWidth > tempFrameX_ ) 
            cropWidth = tempFrameX_;
         pProp->Set(cropWidth);

         //added to use RTA
         SetToIdle();

         unsigned ret = DRV_SUCCESS;
         if (cropModeSwitch_) {
            SetAcquisitionMode(5);
            SetFrameTransferMode(1);
            ret = SetIsolatedCropMode(1, currentCropHeight_, cropWidth, 1, 1);
            SetAcquisitionMode(1);
            fullFrameX_ = currentCropWidth_;

            if (DRV_SUCCESS != ret)
              return (int)ret;

            currentCropWidth_ = cropWidth;            
         }

         ROI oldRoi = roi_;
         roi_.xSize = fullFrameX_;
         roi_.ySize = fullFrameY_;
         roi_.x = 0;
         roi_.y = 0;

         // adjust image extent to conform to the bin size
         roi_.xSize -= roi_.xSize % binSize_;
         roi_.ySize -= roi_.ySize % binSize_;

         /* unsigned aret = */ SetImage(binSize_, binSize_, roi_.x+1, roi_.x+roi_.xSize,
            roi_.y+1, roi_.y+roi_.ySize);

         GetReadoutTime();

         ResizeImageBuffer();

         PrepareSnap();
      }
      else if (eAct == MM::BeforeGet)
      {
         pProp->Set(currentCropWidth_);
      }
      return DEVICE_OK;
   }

   /**
   * Set camera crop mode height.
   */
   int AndorCamera::OnCropModeHeight(MM::PropertyBase* pProp, MM::ActionType eAct)
   {
      DriverGuard dg(this);
      if (eAct == MM::AfterSet)
      {
         long cropHeight;
         pProp->Get(cropHeight);
         if (!cropModeSwitch_) {
            currentCropHeight_ = cropHeight;
            return DEVICE_OK;
         }
         if(cropHeight == currentCropHeight_)
            return DEVICE_OK;

         bool acquiring = sequenceRunning_;
         if (acquiring)
            StopSequenceAcquisition(true);

         if (sequenceRunning_)
            return ERR_BUSY_ACQUIRING;

         if (cropHeight < 1 ) 
            cropHeight = 1;
         if (cropHeight > tempFrameY_ ) 
            cropHeight = tempFrameY_;
         pProp->Set(cropHeight);

         //added to use RTA
         SetToIdle();

         unsigned ret = DRV_SUCCESS;
         if (cropModeSwitch_) {
            SetAcquisitionMode(5);
            SetFrameTransferMode(1);
            ret = SetIsolatedCropMode(1, cropHeight, currentCropWidth_, 1, 1);
            SetAcquisitionMode(1);
            fullFrameY_ = currentCropHeight_;

            if (DRV_SUCCESS != ret)
              return (int)ret;

            currentCropHeight_ = cropHeight;  
         }

         ROI oldRoi = roi_;
         roi_.xSize = fullFrameX_;
         roi_.ySize = fullFrameY_;
         roi_.x = 0;
         roi_.y = 0;

         SetImage(1, 1, roi_.x+1, roi_.x+roi_.xSize,
            roi_.y+1, roi_.y+roi_.ySize);

         GetReadoutTime();

         ResizeImageBuffer();

         PrepareSnap();
      }
      else if (eAct == MM::BeforeGet)
      {
         pProp->Set(currentCropHeight_);
      }
      return DEVICE_OK;
   }

unsigned int AndorCamera::createIsolatedCropModeProperty(AndorCapabilities * caps) {

  DriverGuard dg(this);
  unsigned int ret(DRV_SUCCESS);

  if((caps->ulSetFunctions&AC_SETFUNCTION_CROPMODE) == AC_SETFUNCTION_CROPMODE) {
     CPropertyAction *pAct = new CPropertyAction(this, &AndorCamera::OnCropModeSwitch);
     int nRet = CreateProperty(g_cropMode, "Off", MM::String, false, pAct);
     if (nRet != DEVICE_OK) {
        return nRet;
     }
     AddAllowedValue(g_cropMode, "On");      
     AddAllowedValue(g_cropMode, "Off");

     if(!HasProperty(g_cropModeWidth)) {
        CPropertyAction *pAct = new CPropertyAction(this, &AndorCamera::OnCropModeWidth);
        int nRet = CreateProperty(g_cropModeWidth,"64", MM::Integer,false, pAct);
        assert(nRet == DEVICE_OK);
        nRet = SetPropertyLimits(g_cropModeWidth, 1, tempFrameX_);
        assert(nRet == DEVICE_OK);
     }

     if(!HasProperty(g_cropModeHeight)) {
        CPropertyAction *pAct = new CPropertyAction(this, &AndorCamera::OnCropModeHeight);
        int nRet = CreateProperty(g_cropModeHeight,"64", MM::Integer,false, pAct);
        assert(nRet == DEVICE_OK);
        nRet = SetPropertyLimits(g_cropModeHeight, 1, tempFrameY_);
        assert(nRet == DEVICE_OK);
     }
  }

  return ret;
}

   unsigned int AndorCamera::createTriggerProperty(AndorCapabilities * caps) {
      DriverGuard dg(this);
      vTriggerModes.clear();  
      unsigned int retVal = DRV_SUCCESS;
      if(caps->ulTriggerModes & AC_TRIGGERMODE_CONTINUOUS)
      {
         if(iCurrentTriggerMode_ == SOFTWARE) {
            retVal = SetTriggerMode(10);  //set software trigger. mode 0:internal, 1: ext, 6:ext start, 7:bulb, 10:software
            if (retVal != DRV_SUCCESS)
            {
               ShutDown();
               LogMessage("Could not set trigger mode");
               return retVal;
            }
            strCurrentTriggerMode_ = "Software";
         }
         vTriggerModes.push_back("Software");
         bSoftwareTriggerSupported_ = true;
      }
      if(caps->ulTriggerModes & AC_TRIGGERMODE_EXTERNAL) {
         if(iCurrentTriggerMode_ == EXTERNAL) {
            retVal = SetTriggerMode(1);  //set software trigger. mode 0:internal, 1: ext, 6:ext start, 7:bulb, 10:software
            if (retVal != DRV_SUCCESS)
            {
               ShutDown();
               LogMessage("Could not set external trigger mode");
               return retVal;
            }
            strCurrentTriggerMode_ = "External";
         }
         vTriggerModes.push_back("External");
      }
      if(caps->ulTriggerModes & AC_TRIGGERMODE_INTERNAL) {
         if(iCurrentTriggerMode_ == INTERNAL) {
            retVal = SetTriggerMode(0);  //set software trigger. mode 0:internal, 1: ext, 6:ext start, 7:bulb, 10:software
            if (retVal != DRV_SUCCESS)
            {
               ShutDown();
               LogMessage("Could not set software trigger mode");
               return retVal;
            }
            strCurrentTriggerMode_ = "Internal";
         }
         vTriggerModes.push_back("Internal");
      }
      if(!HasProperty("Trigger"))
      {
         CPropertyAction *pAct = new CPropertyAction (this, &AndorCamera::OnSelectTrigger);
         int nRet = CreateProperty("Trigger", "Trigger Mode", MM::String, false, pAct);
         if (nRet != DEVICE_OK) {
            return nRet;
         }
      }
      int nRet = SetAllowedValues("Trigger", vTriggerModes);
      assert(nRet == DEVICE_OK);
      nRet = SetProperty("Trigger", strCurrentTriggerMode_.c_str());
      assert(nRet == DEVICE_OK);
      

      return retVal;
   }


   unsigned int AndorCamera::UpdateSnapTriggerMode()
   {
      DriverGuard dg(this);
      int ret;

      if(iCurrentTriggerMode_ == SOFTWARE)
      {
         ret = SetTriggerMode(10);  //set software trigger. mode 0:internal, 1: ext, 6:ext start, 7:bulb, 10:software
         //if (ret != DRV_SUCCESS) //not check to allow call of AcqFinished
         //  return ret;
         ret = SetAcquisitionMode(5);//set RTA non-iCam camera
         //if (ret != DRV_SUCCESS)
         //  return ret;
      }
      else if(iCurrentTriggerMode_ == EXTERNAL)
      {
         ret = SetTriggerMode(1);  //set software trigger. mode 0:internal, 1: ext, 6:ext start, 7:bulb, 10:software
         //if (ret != DRV_SUCCESS)
         //  return ret;
         ret = SetAcquisitionMode(5);//set SingleScan non-iCam camera
         //if (ret != DRV_SUCCESS)
         //  return ret;
      }
      else
      {
         ret = SetTriggerMode(0);  //set software trigger. mode 0:internal, 1: ext, 6:ext start, 7:bulb, 10:software
         //if (ret != DRV_SUCCESS)
         //  return ret;
         ret = SetAcquisitionMode(1);//set SingleScan non-iCam camera
         //if (ret != DRV_SUCCESS)
         //  return ret;
      }
      return ret;
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
               printf("Error switching active camera");
         }
      }
   }

   DriverGuard::~DriverGuard()
   {
      g_AndorDriverLock.Unlock();
   }