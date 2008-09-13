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
//				  May 23 & 24, 2007, Daigang Wen, Andor Technology plc added/modified:
//				  Cooler is turned on at startup and turned off at shutdown
//				  Cooler control is changed to cooler mode control
//				  Pre-Amp-Gain property is added
//				  Temperature Setpoint property is added
//				  Temperature is resumed as readonly
//				  EMGainRangeMax and EMGainRangeMin are added
//
//				  April 3 & 4, 2008, Nico Stuurman, UCSF
//				  Changed Sleep statement in AcqSequenceThread to be 20% of the actualInterval instead of 5 ms
//            Added property limits to the Gain (EMGain) property
//            Added property limits to the Temperature Setpoint property and delete TempMin and TempMax properties
//
// FUTURE DEVELOPMENT: From September 1 2007, the development of this adaptor is taken over by Andor Technology plc. Daigang Wen (d.wen@andor.com) is the main contact. Changes made by him will not be labeled.
//
// CVS:           $Id$

#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include "../../MMDevice/ModuleInterface.h"
#include "atmcd32d.h"
#include "Andor.h"
#include <string>
#include <sstream>
#include <iomanip>
#include <math.h>

//#include <ace/Init_ACE.h>
//#include "ace/OS_NS_sys_time.h"
//#include "ace/os_ns_unistd.h"
// jizhen 05.11.2007
#include <iostream>
using namespace std;
// eof jizhen

#pragma warning(disable : 4996) // disable warning for deperecated CRT functions on Windows 

using namespace std;

// global constants
const char* g_IxonName = "Ixon";
const char* g_IxonShutterName = "Ixon-Shutter";

const char* g_PixelType_8bit = "8bit";
const char* g_PixelType_16bit = "16bit";

const char* g_ShutterMode = "ShutterMode";
const char* g_ShutterMode_Auto = "Auto";
const char* g_ShutterMode_Open = "Open";
const char* g_ShutterMode_Closed = "Closed";

const char* g_FanMode_Full = "Full";//Daigang 24-may-2007
const char* g_FanMode_Low = "Low";//Daigang 24-may-2007
const char* g_FanMode_Off = "Off";//Daigang 24-may-2007

const char* g_CoolerMode_FanOffAtShutdown = "Fan off at shutdown";//Daigang 24-may-2007
const char* g_CoolerMode_FanOnAtShutdown = "Fan on at shutdown";//Daigang 24-may-2007

const char* g_FrameTransferProp = "FrameTransfer";
const char* g_FrameTransferOn = "On";
const char* g_FrameTransferOff = "Off";
const char* g_OutputAmplifier = "Output_Amplifier";
const char* g_OutputAmplifier_EM = "Standard EMCCD gain register";
const char* g_OutputAmplifier_Conventional = "Conventional CCD register";

const char* g_ADChannel = "AD_Converter";
const char* g_ADChannel_14Bit = "14bit";
const char* g_ADChannel_16Bit = "16bit";


// singleton instance
Ixon* Ixon::instance_ = 0;
unsigned Ixon::refCount_ = 0;

// Windows dll entry routine
BOOL APIENTRY DllMain( HANDLE /*hModule*/, 
                       DWORD  ul_reason_for_call, 
                       LPVOID /*lpReserved*/
					 )
{
	switch (ul_reason_for_call)
	{
	case DLL_PROCESS_ATTACH:
   break;
	case DLL_THREAD_ATTACH:
	case DLL_THREAD_DETACH:
	case DLL_PROCESS_DETACH:
	break;
	}
    return TRUE;
}

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////

MODULE_API void InitializeModuleData()
{
   AddAvailableDeviceName(g_IxonName, "Andor iXon camera adapter");
}

char deviceName[64]; // jizhen 05.16.2007

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   // jizhen 05.16.2007
   //char* deviceName = new char[128]; // will crash the stack if put the variable here! 
   pDevice->GetName( deviceName);
   if ( strcmp(deviceName, g_IxonName) == 0) 
   {
	   Ixon::ReleaseInstance((Ixon*) pDevice);
   } 
   else 
   // eof jizhen
	   delete pDevice;
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   string strName(deviceName);
   
   if (strcmp(deviceName, g_IxonName) == 0)
      return Ixon::GetInstance();
   
   return 0;
}

void Ixon::ReleaseInstance(Ixon * ixon) {

	unsigned int refC = ixon->DeReference();
	if ( refC <=0 ) 
	{
		delete ixon;
		ixon = 0;
	}
}

///////////////////////////////////////////////////////////////////////////////
// Ixon constructor/destructor

Ixon::Ixon() :
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
   minTemp_(0),
   ThermoSteady_(0),//Daigang 24-may-2007
   lSnapImageCnt_(0),//Daigang 24-may-2007
   currentGain_(-1),//Daigang 24-may-2007
   ReadoutTime_(50),
   ADChannelIndex_(0),
   acquiring_(false),
   imageCounter_(0),
   sequenceLength_(0),
   OutputAmplifierIndex_(0),
   HSSpeedIdx_(0),
   bSoftwareTriggerSupported(0),
   maxTemp_(0),
   CurrentCameraID_(-1),
   pImgBuffer_(0),
   currentExpMS_(0.0),
   bFrameTransfer_(0)
{
   InitializeDefaultErrorMessages();

   // add custom messages
   SetErrorText(ERR_BUSY_ACQUIRING, "Acquisition already in progress.");

   seqThread_ = new AcqSequenceThread(this);

   // Pre-initialization properties
   // -----------------------------

   // Driver location
   CPropertyAction *pAct = new CPropertyAction (this, &Ixon::OnDriverDir);
   CreateProperty("DriverDir", "", MM::String, false, pAct, true);

  hAndorDll = 0;
  fpGetKeepCleanTime = 0;
  fpGetReadOutTime = 0;
  if(hAndorDll == 0)
	  hAndorDll = ::GetModuleHandle("atmcd32d.dll");
  if(hAndorDll!=NULL)
  {
    fpGetKeepCleanTime = (FPGetKeepCleanTime)GetProcAddress(hAndorDll, "GetKeepCleanTime");
    fpGetReadOutTime = (FPGetReadOutTime)GetProcAddress(hAndorDll, "GetReadOutTime");
  }

}

Ixon::~Ixon()
{
   delete seqThread_;
   
   refCount_--;
   if (refCount_ == 0)
   {
      // release resources
	if (initialized_)
	{
    	SetToIdle();
        int ShutterMode = 2;  //0: auto, 1: open, 2: close
        SetShutter(1, ShutterMode, 20,20);//0, 0);
	}

	
	if (initialized_)
        CoolerOFF();  //Daigang 24-may-2007 turn off the cooler at shutdown

      if (initialized_)
         Shutdown();
      // clear the instance pointer
      instance_ = 0;
   }
}

Ixon* Ixon::GetInstance()
{
   if (!instance_)
      instance_ = new Ixon();

   refCount_++;
   return instance_;
}

// jizhen 05.16.2007
unsigned Ixon::DeReference()
{
   refCount_--;
   return refCount_;
}
// eof jizhen

///////////////////////////////////////////////////////////////////////////////
// API methods
// ~~~~~~~~~~~

/**
* Get list of all available cameras
*/
int Ixon::GetListOfAvailableCameras()
{
   unsigned ret;

   NumberOfAvailableCameras_ = 0;
   ret = GetAvailableCameras(&NumberOfAvailableCameras_);
   if (ret != DRV_SUCCESS)
      return ret;
   if(NumberOfAvailableCameras_ == 0)
	   return ERR_CAMERA_DOES_NOT_EXIST;

   long CameraID;
   int UnknownCameraIndex = 0;
   NumberOfWorkableCameras_ = 0;
   cameraName_.clear();
   cameraID_.clear();
   for(int i=0;i<NumberOfAvailableCameras_; i++)
   {
     ret = GetCameraHandle(i, &CameraID);
     if( ret ==DRV_SUCCESS )
     {
       ret = SetCurrentCamera(CameraID);
       if( ret ==DRV_SUCCESS )
	   {
		   ret=::Initialize(const_cast<char*>(driverDir_.c_str()));
         if( ret!=DRV_SUCCESS && ret != DRV_ERROR_FILELOAD )
         {
           ret = ShutDown();
		 }
		 if( ret == DRV_SUCCESS)
		 {
           NumberOfWorkableCameras_++;
           std::string anStr;
           char chars[255];
           ret = GetHeadModel(chars);
           if( ret!=DRV_SUCCESS )
           {
             anStr = "UnknownModel";
           }
           else
           {
             anStr = chars;
           }
           int id;
           ret = GetCameraSerialNumber(&id);
           if( ret!=DRV_SUCCESS )
           {
             UnknownCameraIndex ++;
             id = UnknownCameraIndex;
           }
           sprintf(chars, "%d", id);

		   anStr = anStr + " " + chars;
		   cameraName_.push_back(anStr);
		   cameraID_.push_back((int)CameraID);
          
         
           //if there is only one camera, don't shutdown
           if( NumberOfAvailableCameras_ > 1 )
           {
             ret = ShutDown();
           }
		   else
		   {
			 CurrentCameraID_ = CameraID;
		   }
		 }
	   }
     }
   }

   if(NumberOfWorkableCameras_>=1)
   {
       //camera property for multiple camera support
       /*  //removed because list boxes in Property Browser of MM are unable to update their values after switching camera
       CPropertyAction *pAct = new CPropertyAction (this, &Ixon::OnCamera);
       int nRet = CreateProperty("Camera", cameraName_[NumberOfWorkableCameras_-1].c_str(), MM::String, false, pAct);
       assert(nRet == DEVICE_OK);
       nRet = SetAllowedValues("Camera", cameraName_);
	   */
	   return DRV_SUCCESS;
   }
   else
	   return ERR_CAMERA_DOES_NOT_EXIST;

}

/**
 * Set camera
 */
int Ixon::OnCamera(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
	  //added to use RTA
	  SetToIdle();

      string CameraName;
      pProp->Get(CameraName);
      for (unsigned i=0; i<cameraName_.size(); ++i)
         if (cameraName_[i].compare(CameraName) == 0)
         {
            int ret = ShutDown(); //shut down the used camera
			initialized_ = false;
			CurrentCameraID_ = cameraID_[i];
			ret = Initialize();
            if (DEVICE_OK != ret)
               return ret;
            else
               return DEVICE_OK;
         }
      assert(!"Unrecognized Camera");
   }
   else if (eAct == MM::BeforeGet)
   {
   }
   return DEVICE_OK;
}
/**
 * Camera Name
 */
int Ixon::OnCameraName(MM::PropertyBase* pProp, MM::ActionType eAct)
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
int Ixon::OniCamFeatures(MM::PropertyBase* pProp, MM::ActionType eAct)
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
int Ixon::OnTemperatureRangeMin(MM::PropertyBase* pProp, MM::ActionType eAct)
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
int Ixon::OnTemperatureRangeMax(MM::PropertyBase* pProp, MM::ActionType eAct)
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
int Ixon::Initialize()
{
   if (initialized_)
      return DEVICE_OK;

   unsigned ret;
   int nRet;
   CPropertyAction *pAct;

   if(CurrentCameraID_ == -1)
   {
     ret = GetListOfAvailableCameras();
     if (ret != DRV_SUCCESS)
       return ret;
	 if(NumberOfAvailableCameras_>1 && NumberOfWorkableCameras_>=1)
	 {
	   CurrentCameraID_=cameraID_[0];
       ret = SetCurrentCamera(CurrentCameraID_);
       if (ret != DRV_SUCCESS)
         return ret;
       ret = ::Initialize(const_cast<char*>(driverDir_.c_str()));
       if (ret != DRV_SUCCESS)
         return ret;
	 }
   }
   else
   {

   ret = SetCurrentCamera(CurrentCameraID_);
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
	     if(cameraID_[i] == CurrentCameraID_)
	     {
		     currentCameraIdx = i;
		     break;
	     }
     }
   }
   CameraName_ = cameraName_[currentCameraIdx];
   if(HasProperty(MM::g_Keyword_Name))
   {
	 nRet = SetProperty(MM::g_Keyword_Name,cameraName_[currentCameraIdx].c_str());   
   }
   else
   {
     CPropertyAction *pAct = new CPropertyAction (this, &Ixon::OnCameraName);
     nRet = CreateProperty(MM::g_Keyword_Name, cameraName_[currentCameraIdx].c_str(), MM::String, true, pAct);
   }
   assert(nRet == DEVICE_OK);

   // Description
   if(HasProperty(MM::g_Keyword_Description))
   {
	 ;//nRet = SetProperty(MM::g_Keyword_Description,  "Andor iXon camera adapter");   
   }
   else
   {
     nRet = CreateProperty(MM::g_Keyword_Description, "Andor iXon/Luca camera adapter", MM::String, true);
   }
   assert(nRet == DEVICE_OK);

   // driver location

   // capabilities
   AndorCapabilities caps;
   caps.ulSize = sizeof(AndorCapabilities);
   ret = GetCapabilities(&caps);
   if (ret != DRV_SUCCESS)
      return ret;

   //check iCam feature
   string striCam = "Not Supported";
   if(caps.ulTriggerModes & AC_TRIGGERMODE_CONTINUOUS)
   {
      ret = SetTriggerMode(10);  //set software trigger. mode 0:internal, 1: ext, 6:ext start, 7:bulb, 10:software
      if (ret != DRV_SUCCESS)
	  {
         ShutDown();
         return ret;
	  }
	  bSoftwareTriggerSupported = true;
      striCam = "Supported";
   }
   else
   {
   	  ret = SetTriggerMode(0);  //set internal trigger. mode 0:internal, 1: ext, 6:ext start, 7:bulb, 10:software
      if (ret != DRV_SUCCESS)
	  {
         ShutDown();
         return ret;
	  }
      bSoftwareTriggerSupported = false;

   }
   iCamFeatures_ = striCam;
   if(HasProperty("iCamFeatures"))
   {
	 nRet = SetProperty("iCamFeatures",  striCam.c_str());   
   }
   else
   {
     CPropertyAction *pAct = new CPropertyAction (this, &Ixon::OniCamFeatures);
     nRet = CreateProperty("iCamFeatures", striCam.c_str(), MM::String, true, pAct);
   }
   assert(nRet == DEVICE_OK);

//Use iCamFeatures
   if(bSoftwareTriggerSupported)
   {
     vUseSoftwareTrigger_.clear();
     vUseSoftwareTrigger_.push_back("Yes");
     vUseSoftwareTrigger_.push_back("No");
     if(!HasProperty("UseSoftwareTrigger"))
     {
       CPropertyAction *pAct = new CPropertyAction (this, &Ixon::OnUseSoftwareTrigger);
       nRet = CreateProperty("UseSoftwareTrigger", striCam.c_str(), MM::String, false, pAct);
       assert(nRet == DEVICE_OK);
     }
     nRet = SetAllowedValues("UseSoftwareTrigger", vUseSoftwareTrigger_);
     assert(nRet == DEVICE_OK);
     nRet = SetProperty("UseSoftwareTrigger", vUseSoftwareTrigger_[0].c_str());
     UseSoftwareTrigger_ = vUseSoftwareTrigger_[0];
     assert(nRet == DEVICE_OK);
   }

//Set EM Gain mode
  if(caps.ulEMGainCapability&AC_EMGAIN_REAL12)
  {
    ret = SetEMAdvanced(1);
    if (ret != DRV_SUCCESS)
      return ret;
    ret = SetEMGainMode(3);  //mode 0: 0-255; 1: 0-4095; 2: Linear; 3: real
    if (ret != DRV_SUCCESS)
      return ret;
  }
  else if(caps.ulEMGainCapability&AC_EMGAIN_LINEAR12)
  {
    ret = SetEMAdvanced(1);
    if (ret != DRV_SUCCESS)
      return ret;
    ret = SetEMGainMode(2);  //mode 0: 0-255; 1: 0-4095; 2: Linear; 3: real
    if (ret != DRV_SUCCESS)
      return ret;
  }
  else if(caps.ulEMGainCapability&AC_EMGAIN_12BIT)
  {
    ret = SetEMAdvanced(1);
    if (ret != DRV_SUCCESS)
      return ret;
    ret = SetEMGainMode(1);  //mode 0: 0-255; 1: 0-4095; 2: Linear; 3: real
    if (ret != DRV_SUCCESS)
      return ret;
  }
  else
  {
    ret = SetEMGainMode(0);  //mode 0: 0-255; 1: 0-4095; 2: Linear; 3: real
    if (ret != DRV_SUCCESS)
      return ret;
  }



//Output amplifier
   int numAmplifiers;
   ret = GetNumberAmp(&numAmplifiers);
   if (ret != DRV_SUCCESS)
      return ret;
   if(numAmplifiers > 1)
   {
     if(!HasProperty(g_OutputAmplifier))
     {
       CPropertyAction *pAct = new CPropertyAction (this, &Ixon::OnOutputAmplifier);
       nRet = CreateProperty(g_OutputAmplifier, g_OutputAmplifier_EM, MM::String, false, pAct);
	 }
     vector<string> OutputAmplifierValues;
     OutputAmplifierValues.push_back(g_OutputAmplifier_EM);
     OutputAmplifierValues.push_back(g_OutputAmplifier_Conventional);
     nRet = SetAllowedValues(g_OutputAmplifier, OutputAmplifierValues);
     assert(nRet == DEVICE_OK);
	 nRet = SetProperty(g_OutputAmplifier,  OutputAmplifierValues[0].c_str());   
     assert(nRet == DEVICE_OK);
     if (nRet != DEVICE_OK)
        return nRet;
   }

//AD channel (pixel bitdepth)
   int numADChannels;
   ret = GetNumberADChannels(&numADChannels);
   if (ret != DRV_SUCCESS)
      return ret;
   if(numADChannels > 1)
   {
     if(!HasProperty(g_ADChannel))
     {
       CPropertyAction *pAct = new CPropertyAction (this, &Ixon::OnADChannel);
       nRet = CreateProperty(g_ADChannel, g_ADChannel_14Bit, MM::String, false, pAct);
       assert(nRet == DEVICE_OK);
	 }
     vector<string> ADChannelValues;
     ADChannelValues.push_back(g_ADChannel_14Bit);
     ADChannelValues.push_back(g_ADChannel_16Bit);
     nRet = SetAllowedValues(g_ADChannel, ADChannelValues);
     assert(nRet == DEVICE_OK);
     if (nRet != DEVICE_OK)
        return nRet;
	 nRet = SetProperty(g_ADChannel,  ADChannelValues[0].c_str());   
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
   if(bSoftwareTriggerSupported)
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
     CPropertyAction *pAct = new CPropertyAction (this, &Ixon::OnBinning);
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
     pAct = new CPropertyAction (this, &Ixon::OnPixelType);
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
     pAct = new CPropertyAction (this, &Ixon::OnExposure);
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
	   bShuterIntegrated = false;
   else
   {
	   bShuterIntegrated = true;
	   if(!HasProperty("InternalShutter"))
	   {
         pAct = new CPropertyAction (this, &Ixon::OnInternalShutter);
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
   ret = SetShutter(1, ShutterMode, 20,20);//Opened any way because some old iXon has no flag for IsInternalMechanicalShutter



   // camera gain
   if(!HasProperty(MM::g_Keyword_Gain))
   {
     pAct = new CPropertyAction (this, &Ixon::OnGain);
     nRet = CreateProperty(MM::g_Keyword_Gain, "0", MM::Integer, false, pAct);
     assert(nRet == DEVICE_OK);
   }
   else
   {
	   nRet = SetProperty(MM::g_Keyword_Gain, "0");
       assert(nRet == DEVICE_OK);
   }


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
      sprintf(speedBuf, "%.1f MHz", sp);
      readoutModes_.push_back(speedBuf);
   }
   if (readoutModes_.empty())
      return ERR_INVALID_READOUT_MODE_SETUP;

   if(!HasProperty(MM::g_Keyword_ReadoutMode))
   {
     pAct = new CPropertyAction (this, &Ixon::OnReadoutMode);
	 if(numSpeeds>1)
       nRet = CreateProperty(MM::g_Keyword_ReadoutMode, readoutModes_[0].c_str(), MM::String, false, pAct);
	 else
       nRet = CreateProperty(MM::g_Keyword_ReadoutMode, readoutModes_[0].c_str(), MM::String, true, pAct);
     assert(nRet == DEVICE_OK);
   }
   nRet = SetAllowedValues(MM::g_Keyword_ReadoutMode, readoutModes_);
   nRet = SetProperty(MM::g_Keyword_ReadoutMode,readoutModes_[0].c_str());
   HSSpeedIdx_ = 0;


   //Daigang 24-may-2007
   // Pre-Amp-Gain
   int numPreAmpGain;
   ret = GetNumberPreAmpGains(&numPreAmpGain);
   if (ret != DRV_SUCCESS)
      return ret;
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
     pAct = new CPropertyAction (this, &Ixon::OnPreAmpGain);
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
   //eof Daigang


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
     pAct = new CPropertyAction (this, &Ixon::OnVSpeed);
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
       pAct = new CPropertyAction (this, &Ixon::OnVCVoltage);
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
   // jizhen 05.08.2007
   // temperature range
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

   //daigang 24-may-2007 changed
   //CreateProperty("MinTemp", tMin.str().c_str(), MM::Integer, true);
   //CreateProperty("MaxTemp", tMax.str().c_str(), MM::Integer, true);
   /*
   TemperatureRangeMin_ = tMin.str();
   TemperatureRangeMax_ = tMax.str();

   if(!HasProperty("TemperatureRangeMin"))
   {
     pAct = new CPropertyAction (this, &Ixon::OnTemperatureRangeMin);
     nRet = CreateProperty("TemperatureRangeMin", tMin.str().c_str(), MM::Integer, true, pAct);
   }
   else
   {
	 nRet = SetProperty("TemperatureRangeMin", tMin.str().c_str());
   }
   assert(nRet == DEVICE_OK);
   if(!HasProperty("TemperatureRangeMax"))
   {
     pAct = new CPropertyAction (this, &Ixon::OnTemperatureRangeMax);
     nRet = CreateProperty("TemperatureRangeMax", tMax.str().c_str(), MM::Integer, true, pAct);
   }
   else
   {
	 nRet = SetProperty("TemperatureRangeMax", tMax.str().c_str());
   }
   assert(nRet == DEVICE_OK);
   */
   //eof Daigang


//added to show some tips
   string strTips = "Wait for temperature stabilized before acquisition.";
   if(!HasProperty(" Tip1"))
   {
     nRet = CreateProperty(" Tip1", strTips.c_str(), MM::String, true);
   }
   else
   {
	 nRet = SetProperty(" Tip1", strTips.c_str());
   }
   assert(nRet == DEVICE_OK);

  if(bSoftwareTriggerSupported)
  {
   strTips = "To maximize frame rate, do not tick camera parameters except Exposure in Configuration Presets.";
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


   // eof jizhen

   int temp;
   ret = GetTemperature(&temp);
   ostringstream strTemp;
   strTemp<<temp;
   if(!HasProperty(MM::g_Keyword_CCDTemperature))
   {
     pAct = new CPropertyAction (this, &Ixon::OnTemperature);
	 nRet = CreateProperty(MM::g_Keyword_CCDTemperature, strTemp.str().c_str(), MM::Integer, true, pAct);//Daigang 23-may-2007 changed back to read temperature only
   }
   else
   {
	   nRet = SetProperty(MM::g_Keyword_CCDTemperature,  strTemp.str().c_str());
   }
   assert(nRet == DEVICE_OK);

   //if (ret != DEVICE_OK)
   //   return ret;

   //Daigang 23-may-2007
   std::string strTempSetPoint;
   if(minTemp<-70)
	   strTempSetPoint = "-70";
   else
	   strTempSetPoint = TemperatureRangeMin_; 
   if(!HasProperty(MM::g_Keyword_CCDTemperatureSetPoint))
   {
     pAct = new CPropertyAction (this, &Ixon::OnTemperatureSetPoint);
	 nRet = CreateProperty(MM::g_Keyword_CCDTemperatureSetPoint, strTempSetPoint.c_str(), MM::Integer, false, pAct);
      ret = SetPropertyLimits(MM::g_Keyword_CCDTemperatureSetPoint, minTemp_, maxTemp_);
   }
   else
   {
	   nRet = SetProperty(MM::g_Keyword_CCDTemperatureSetPoint, strTempSetPoint.c_str());
   }
   assert(nRet == DEVICE_OK);


   //jizhen 05.11.2007
   // Cooler
   if(!HasProperty("CoolerMode"))
   {
     pAct = new CPropertyAction (this, &Ixon::OnCooler);
     nRet = CreateProperty(/*Daigang 24-may-2007 "Cooler" */"CoolerMode", /*Daigang 24-may-2007 "0" */g_CoolerMode_FanOffAtShutdown, /*Daigang 24-may-2007 MM::Integer */MM::String, false, pAct); 
   }
   assert(nRet == DEVICE_OK);
   AddAllowedValue(/*Daigang 24-may-2007 "Cooler" */"CoolerMode", g_CoolerMode_FanOffAtShutdown);//"0");  //Daigang 24-may-2007
   AddAllowedValue(/*Daigang 24-may-2007 "Cooler" */"CoolerMode", g_CoolerMode_FanOnAtShutdown);//"1");  //Daigang 24-may-2007
   nRet = SetProperty("CoolerMode", g_CoolerMode_FanOffAtShutdown);
   assert(nRet == DEVICE_OK);
   // eof jizhen

   //jizhen 05.16.2007
   // Fan
   if(!HasProperty("FanMode"))
   {
     pAct = new CPropertyAction (this, &Ixon::OnFanMode);
     nRet = CreateProperty("FanMode", /*Daigang 24-may-2007 "0" */g_FanMode_Full, /*Daigang 24-may-2007 MM::Integer */MM::String, false, pAct); 
   }
   assert(nRet == DEVICE_OK);
   AddAllowedValue("FanMode", g_FanMode_Full);// "0"); // high  //Daigang 24-may-2007
   AddAllowedValue("FanMode", g_FanMode_Low);//"1"); // low  //Daigang 24-may-2007
   AddAllowedValue("FanMode", g_FanMode_Off);//"2"); // off  //Daigang 24-may-2007
   nRet = SetProperty("FanMode", g_FanMode_Full);
   assert(nRet == DEVICE_OK);
   // eof jizhen

   // frame transfer mode
   if(!HasProperty(g_FrameTransferProp))
   {
     pAct = new CPropertyAction (this, &Ixon::OnFrameTransfer);
     nRet = CreateProperty(g_FrameTransferProp, g_FrameTransferOff, MM::String, false, pAct); 
   }
   assert(nRet == DEVICE_OK);
   AddAllowedValue(g_FrameTransferProp, g_FrameTransferOff);
   AddAllowedValue(g_FrameTransferProp, g_FrameTransferOn);
   nRet = SetProperty(g_FrameTransferProp, g_FrameTransferOff);
   assert(nRet == DEVICE_OK);

   // actual interval
   // used by the application to get information on the actual camera interval
   if(!HasProperty(MM::g_Keyword_ActualInterval_ms))
   {
     pAct = new CPropertyAction (this, &Ixon::OnActualIntervalMS);
     nRet = CreateProperty(MM::g_Keyword_ActualInterval_ms, "0.0", MM::Float, false, pAct);
   }
   else
   {
     nRet = SetProperty(MM::g_Keyword_ActualInterval_ms, "0.0");
   }
   assert(nRet == DEVICE_OK);


   if(!HasProperty(MM::g_Keyword_ReadoutTime))
   {
     pAct = new CPropertyAction (this, &Ixon::OnReadoutTime);
     nRet = CreateProperty(MM::g_Keyword_ReadoutTime, "1", MM::Integer, true, pAct);
   }
   else
   {
     nRet = SetProperty(MM::g_Keyword_ReadoutTime, "1");
   }
   assert(nRet == DEVICE_OK);

   //baseline clmap
  if(caps.ulSetFunctions&AC_SETFUNCTION_BASELINEOFFSET) //some camera such as Luca might not support this
  {

   if(!HasProperty("BaselineClamp"))
   {
     pAct = new CPropertyAction (this, &Ixon::OnBaselineClamp);
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

  //DMA parameters
   //if(caps.ulSetFunctions & AC_SETFUNCTION_DMAPARAMETERS)
   {
     int NumFramesPerDMA = 1;
     float SecondsPerDMA = 0.001f;
     ret = SetDMAParameters(NumFramesPerDMA, SecondsPerDMA);
     if (DRV_SUCCESS != ret)
       return (int)ret;
   }


   // synchronize all properties
   // --------------------------
   nRet = UpdateStatus();
   if (nRet != DEVICE_OK)
      return nRet;

   // setup the buffer
   // ----------------
   nRet = ResizeImageBuffer();
   if (nRet != DEVICE_OK)
      return nRet;

   // explicitely set properties which are not readable from the camera
   nRet = SetProperty(MM::g_Keyword_Binning, "1");
   if (nRet != DEVICE_OK)
      return nRet;
   if(bShuterIntegrated)
   {
   nRet = SetProperty("InternalShutter", g_ShutterMode_Open);
   if (nRet != DEVICE_OK)
      return nRet;
   }

   nRet = SetProperty(MM::g_Keyword_CCDTemperatureSetPoint, strTempSetPoint.c_str());
   if (nRet != DEVICE_OK)
      return nRet;

   // EM gain
   // jizhen 05.08.2007
   // EMCCDGain range
   int EmCCDGainLow, EmCCDGainHigh;
   ret = GetEMGainRange(&EmCCDGainLow, &EmCCDGainHigh);
   if (ret != DRV_SUCCESS)
      return ret;
   EmCCDGainLow_ = EmCCDGainLow;
   EmCCDGainHigh_ = EmCCDGainHigh;

   // Set range for EMGain
   ret = SetPropertyLimits(MM::g_Keyword_Gain, EmCCDGainLow, EmCCDGainHigh);
   if (ret != DEVICE_OK)
      return ret;
   
   ostringstream emgLow; 
   ostringstream emgHigh; 
   emgLow << EmCCDGainLow;
   emgHigh << EmCCDGainHigh;
   //daigang 24-may-2007 changed
   //CreateProperty("EmCCDGainLow", emgLow.str().c_str(), MM::Integer, true);
   //CreateProperty("EmCCDGainHigh", emgHigh.str().c_str(), MM::Integer, true);
   if(!HasProperty("EMGainRangeMin"))
   {
     pAct = new CPropertyAction (this, &Ixon::OnEMGainRangeMin);  //daigang 24-may-2007 added
     nRet = CreateProperty("EMGainRangeMin", emgLow.str().c_str(), MM::Integer, true, pAct);
   }
   else
   {
     nRet = SetProperty("EMGainRangeMin", emgLow.str().c_str());
   }
   assert(nRet == DEVICE_OK);

   if(!HasProperty("EMGainRangeMax"))
   {
     pAct = new CPropertyAction (this, &Ixon::OnEMGainRangeMax);
     CreateProperty("EMGainRangeMax", emgHigh.str().c_str(), MM::Integer, true, pAct);
   }
   else
   {
     SetProperty("EMGainRangeMax", emgHigh.str().c_str());
   }
   assert(nRet == DEVICE_OK);
   //eof Daigang

   // eof jizhen



   nRet = SetProperty(MM::g_Keyword_Exposure, "10.0");
   if (nRet != DEVICE_OK)
      return nRet;
   nRet = SetProperty(MM::g_Keyword_Gain, emgLow.str().c_str());//use Gain to set EMGain
   if (nRet != DEVICE_OK)
      return nRet;
   nRet = SetProperty(MM::g_Keyword_ReadoutMode, readoutModes_[0].c_str());
   if (nRet != DEVICE_OK)
      return nRet;
   //Daigang 24-may-2007
   nRet = SetProperty("FanMode", g_FanMode_Full);
   if (nRet != DEVICE_OK)
      return nRet;
   nRet = SetProperty(g_FrameTransferProp, g_FrameTransferOff);
   if (nRet != DEVICE_OK)
      return nRet;
   nRet = SetProperty("CoolerMode", g_CoolerMode_FanOffAtShutdown);
   if (nRet != DEVICE_OK)
      return nRet;
   ret = CoolerON();  //turn on the cooler at startup
   if (DRV_SUCCESS != ret)
      return (int)ret;
   if(EmCCDGainHigh_>=300)
   {
   ret = SetEMAdvanced(1);  //Enable extended range of EMGain
   if (DRV_SUCCESS != ret)
      return (int)ret;
   }
   UpdateEMGainRange();
   GetReadoutTime();
   //eof Daigang

   nRet = UpdateStatus();
   if (nRet != DEVICE_OK)
      return nRet;

   initialized_ = true;
   return DEVICE_OK;
}

void Ixon::GetName(char* name) const 
{
   CDeviceUtils::CopyLimitedString(name, g_IxonName);
}

/**
 * Deactivate the camera, reverse the initialization process.
 */
int Ixon::Shutdown()
{
   if (initialized_)
   {
    	SetToIdle();
        int ShutterMode = 2;  //0: auto, 1: open, 2: close
        SetShutter(1, ShutterMode, 20,20);//0, 0);
        CoolerOFF();  //Daigang 24-may-2007 turn off the cooler at shutdown
		ShutDown();
   }

   initialized_ = false;
   return DEVICE_OK;
}


double Ixon::GetExposure() const
{
   char Buf[MM::MaxStrLength];
   Buf[0] = '\0';
   GetProperty(MM::g_Keyword_Exposure, Buf);
   return atof(Buf);
}

void Ixon::SetExposure(double dExp)
{
   SetProperty(MM::g_Keyword_Exposure, CDeviceUtils::ConvertToString(dExp));
}


//added to use RTA
/**
 * Acquires a single frame.
 * Micro-Manager expects that this function blocks the calling thread until the exposure phase is over.
 */
int Ixon::SnapImage()
{
   if (acquiring_)
      return ERR_BUSY_ACQUIRING;

   unsigned ret;
   if(!IsAcquiring())
   {
	   if(bFrameTransfer_ && bSoftwareTriggerSupported)
		   ret = SetFrameTransferMode(0);  //Software trigger mode can not be used in FT mode

       GetReadoutTime();

	   ret = ::StartAcquisition();
     if (ret != DRV_SUCCESS)
        return ret;
   }
   if(bSoftwareTriggerSupported)
   {
     SetExposure_();
     ret = SendSoftwareTrigger();
     if (ret != DRV_SUCCESS)
       return ret;
   }

   pImgBuffer_ = GetImageBuffer_();

   if(bSoftwareTriggerSupported)
     CDeviceUtils::SleepMs(KeepCleanTime_);

   return DEVICE_OK;
}


//dded to use RTA
/**
 * Returns the raw image buffer.
 */ 
unsigned char* Ixon::GetImageBuffer_()
{
   if(!IsAcquiring())
   {
     unsigned ret = ::StartAcquisition();
     if (ret != DRV_SUCCESS)
        return 0;
   }

   long startT = GetTickCount();
   long Timeout = (long)((expMs_ + ReadoutTime_) * 3);
   long delta = 0;

   int TimeoutCnt = 0;

   assert(fullFrameBuffer_ != 0);
   unsigned int ret = GetNewData16((WORD*)fullFrameBuffer_, roi_.xSize/binSize_ * roi_.ySize/binSize_);
   while (ret == DRV_NO_NEW_DATA)
   {
      if(!IsAcquiring())
      {
        unsigned ret = ::StartAcquisition();
        if (ret != DRV_SUCCESS)
           return 0;
      }

      delta = GetTickCount() - startT;
      if(delta > Timeout)
	  {
		 TimeoutCnt++;
		 if(TimeoutCnt>10)
		 {
		    unsigned char* rawBuffer = const_cast<unsigned char*> (img_.GetPixels());
			memset(rawBuffer,0,img_.Width() * img_.Height() * img_.Depth());
			return rawBuffer;

		 }
		 else
		 {
		   startT = GetTickCount();
		 }
         if(bSoftwareTriggerSupported)
		 {
           //unsigned ret1 = SendSoftwareTrigger();
           //if (ret1 != DRV_SUCCESS)
		   {
			 ;//for debug
		   }
		 }
	  }
      ret = GetNewData16((WORD*)fullFrameBuffer_, roi_.xSize/binSize_ * roi_.ySize/binSize_);
   }
   assert(img_.Depth() == 2);
   unsigned char* rawBuffer = const_cast<unsigned char*> (img_.GetPixels());
   memcpy(rawBuffer, fullFrameBuffer_, img_.Width() * img_.Height() * img_.Depth());

   // capture complete
   return (unsigned char*)rawBuffer;
}


const unsigned char* Ixon::GetImageBuffer()
{

   assert(img_.Depth() == 2);
   assert(pImgBuffer_!=0);
   unsigned char* rawBuffer = pImgBuffer_;
   // process image
   MM::ImageProcessor* ip = GetCoreCallback()->GetImageProcessor(this);
   if (ip)
   {
      int ret = ip->Process((unsigned char*) rawBuffer, img_.Width(), img_.Height(), img_.Depth());
      if (ret != DEVICE_OK)
         return 0;
   }
   return (unsigned char*)rawBuffer;
}



/**
 * Readout time
 */ 
long Ixon::GetReadoutTime()
{
   long ReadoutTime;
   float fReadoutTime;
   if(fpGetReadOutTime!=0 && bSoftwareTriggerSupported)
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
   long KeepCleanTime;
   float fKeepCleanTime;
   if(fpGetKeepCleanTime!=0 && bSoftwareTriggerSupported)
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
int Ixon::SetROI(unsigned uX, unsigned uY, unsigned uXSize, unsigned uYSize)
{
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

   return DEVICE_OK;
}

unsigned Ixon::GetBitDepth() const
{
   int depth;
   // TODO: channel 0 hardwired ???
   unsigned ret = ::GetBitDepth(ADChannelIndex_, &depth);
   if (ret != DRV_SUCCESS)
      depth = 0;
   return depth;
}

int Ixon::GetBinning () const
{
   return binSize_;
}

int Ixon::SetBinning (int binSize) 
{
   ostringstream os;                                                         
   os << binSize;
   return SetProperty(MM::g_Keyword_Binning, os.str().c_str());                                                                                     
} 

int Ixon::GetROI(unsigned& uX, unsigned& uY, unsigned& uXSize, unsigned& uYSize)
{
   uX = roi_.x / binSize_;
   uY = roi_.y / binSize_;
   uXSize = roi_.xSize / binSize_;
   uYSize = roi_.ySize / binSize_;

   return DEVICE_OK;
}

int Ixon::ClearROI()
{
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

   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// Action handlers
// ~~~~~~~~~~~~~~~

/**
 * Set the directory for the Andor native driver dll.
 */
int Ixon::OnDriverDir(MM::PropertyBase* pProp, MM::ActionType eAct)
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

/**
 * Set binning.
 */
int Ixon::OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
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
int Ixon::OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct)
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

	  if(fabs(exp-currentExpMS_)<0.001)
		  return DEVICE_OK;
	  currentExpMS_ = exp;

      if(!bSoftwareTriggerSupported)
	  {
	    SetToIdle();
        unsigned ret = SetExposureTime((float)(exp / 1000.0));
        if (DRV_SUCCESS != ret)
           return (int)ret;
        expMs_ = exp;
	  }
   }
   return DEVICE_OK;
}

/**
 * Set camera exposure (milliseconds).
 */
int Ixon::SetExposure_()
{
  if(!bSoftwareTriggerSupported)
	  return DEVICE_OK;
  if(fabs(expMs_-currentExpMS_)<0.001)
	  return DEVICE_OK;

   CDeviceUtils::SleepMs(KeepCleanTime_);
   unsigned ret = SetExposureTime((float)(currentExpMS_ / 1000.0));
   if (DRV_SUCCESS != ret)
         return (int)ret;
   expMs_ = currentExpMS_;

   return DEVICE_OK;
}


/**
 * Set camera pixel type. 
 * We support only 16-bit mode here.
 */
int Ixon::OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
      pProp->Set(g_PixelType_16bit);
   return DEVICE_OK;
}

/**
 * Set readout mode.
 */
int Ixon::OnReadoutMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
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
int Ixon::OnReadoutTime(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(ReadoutTime_);
   }

   return DEVICE_OK;
}

/**
 * Set camera EM gain.
 */
int Ixon::OnEMGain(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      long gain;
      pProp->Get(gain);
	  if(gain == currentGain_)  //Daigang 24-may-2007 added
		 return DEVICE_OK;  //Daigang 24-may-2007 added
	  //jizhen 05.10.2007
	  if (gain < (long) EmCCDGainLow_ ) gain = (long)EmCCDGainLow_;
      if (gain > (long) EmCCDGainHigh_ ) gain = (long)EmCCDGainHigh_;
	  pProp->Set(gain);
	  // eof jizhen

      //added to use RTA
      if(!bSoftwareTriggerSupported)
    	SetToIdle();


      unsigned ret = SetEMCCDGain((int)gain);
      if (DRV_SUCCESS != ret)
         return (int)ret;
	  currentGain_ = gain; //Daigang 24-may-2007
   }
   else if (eAct == MM::BeforeGet)
   {
   }
   return DEVICE_OK;
}

/**
 * Set camera "regular" gain.
 */
int Ixon::OnGain(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      //Daigang 24-may-2007 to use Gain to control EMGain due to no EMGain on GUI
      /*
	  long gain;
      pProp->Get(gain);
      unsigned ret = SetPreAmpGain((int)gain);
      if (DRV_SUCCESS != ret)
         return (int)ret;
	  */
      long gain;
      pProp->Get(gain);
	  if(gain == currentGain_)
		 return DEVICE_OK;
	  if (gain!=0 && gain < (long) EmCCDGainLow_ ) gain = (long)EmCCDGainLow_;
      if (gain > (long) EmCCDGainHigh_ ) gain = (long)EmCCDGainHigh_;
	  pProp->Set(gain);

	  //added to use RTA
      if(!bSoftwareTriggerSupported)
    	SetToIdle();

      unsigned ret = SetEMCCDGain((int)gain);
      if (DRV_SUCCESS != ret)
         return (int)ret;
	  currentGain_ = gain;
	  //eof Daigang
   }
   else if (eAct == MM::BeforeGet)
   {
	   pProp->Set(currentGain_);
   }
   return DEVICE_OK;
}


/**
 * Enable or Disable Software Trigger.
 */
int Ixon::OnUseSoftwareTrigger(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
	  std::string useSoftwareTrigger;
      pProp->Get(useSoftwareTrigger);
	  if(useSoftwareTrigger == UseSoftwareTrigger_)
		 return DEVICE_OK;

	  UseSoftwareTrigger_ = useSoftwareTrigger;

	  SetToIdle();

	  int ret;

	  if(useSoftwareTrigger == "Yes")
	  {
		  bSoftwareTriggerSupported = true;
		  ret = SetTriggerMode(10);//software trigger mode
          if (ret != DRV_SUCCESS)
            return ret;
		  ret = SetAcquisitionMode(5);//RTA
          if (ret != DRV_SUCCESS)
            return ret;
	  }
	  else
	  {
		  bSoftwareTriggerSupported = false;
		  ret = SetAcquisitionMode(1);//SingleScan
          if (ret != DRV_SUCCESS)
            return ret;
		  ret = SetTriggerMode(0);//internal trigger mode
          if (ret != DRV_SUCCESS)
            return ret;
	  }
   }
   else if (eAct == MM::BeforeGet)
   {
	   pProp->Set(UseSoftwareTrigger_.c_str());
   }
   return DEVICE_OK;
}

//Daigang 24-may-2007
/**
 * Set camera pre-amp-gain.
 */
int Ixon::OnPreAmpGain(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
	//added to use RTA
	SetToIdle();

      string PreAmpGain;
      pProp->Get(PreAmpGain);
      for (unsigned i=0; i<PreAmpGains_.size(); ++i)
         if (PreAmpGains_[i].compare(PreAmpGain) == 0)
         {
            unsigned ret = SetPreAmpGain(i);
            if (DRV_SUCCESS != ret)
               return (int)ret;
            else
			{
               PreAmpGain_=PreAmpGain;
               return DEVICE_OK;
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
int Ixon::OnVCVoltage(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
	  //added to use RTA
	  SetToIdle();

      string VCVoltage;
      pProp->Get(VCVoltage);
      for (unsigned i=0; i<VCVoltages_.size(); ++i)
         if (VCVoltages_[i].compare(VCVoltage) == 0)
         {
            unsigned ret = SetVSAmplitude(i);
            if (DRV_SUCCESS != ret)
               return (int)ret;
            else
			{
               VCVoltage_=VCVoltage;
               return DEVICE_OK;
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
int Ixon::OnBaselineClamp(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
	//added to use RTA
	SetToIdle();

      string BaselineClampValue;
      pProp->Get(BaselineClampValue);
      for (unsigned i=0; i<BaselineClampValues_.size(); ++i)
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
               BaselineClampValue_=BaselineClampValue;
               return DEVICE_OK;
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
int Ixon::OnVSpeed(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
	//added to use RTA
	SetToIdle();

      string VSpeed;
      pProp->Get(VSpeed);
      for (unsigned i=0; i<VSpeeds_.size(); ++i)
         if (VSpeeds_[i].compare(VSpeed) == 0)
         {
            unsigned ret = SetVSSpeed(i);
            if (DRV_SUCCESS != ret)
               return (int)ret;
            else
			{
               GetReadoutTime();
			   VSpeed_ = VSpeed;
               return DEVICE_OK;
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
int Ixon::OnTemperature(MM::PropertyBase* pProp, MM::ActionType eAct)
{
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
int Ixon::OnTemperatureSetPoint(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
	//added to use RTA
	SetToIdle();

      long temp;
      pProp->Get(temp);
	  if (temp < (long) minTemp_ ) temp = (long)minTemp_;
      if (temp > (long) maxTemp_ ) temp = (long)maxTemp_;
      unsigned ret = SetTemperature((int)temp);
      if (DRV_SUCCESS != ret)
         return (int)ret;
	  ostringstream strTempSetPoint;
	  strTempSetPoint<<temp;
	  TemperatureSetPoint_ = strTempSetPoint.str();

      UpdateEMGainRange();  //Daigang 24-may-2007

   }
   else if (eAct == MM::BeforeGet)
   {
      //int temp;
      //int ret = GetTemperature(&temp);
      //pProp->Set((long)temp);
	   pProp->Set(TemperatureSetPoint_.c_str());
   }
   return DEVICE_OK;
}
//eof Daigang



//jizhen 05.11.2007
/**
 * Set cooler on/off.
 */
int Ixon::OnCooler(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
	//added to use RTA
	SetToIdle();

	  //Daigang 24-may-2007
	  /*
      long OnOff;
      pProp->Get(OnOff);
	  unsigned ret;
	  if ( OnOff == 1 ) ret = CoolerON();
	  else ret = CoolerOFF();
      if (DRV_SUCCESS != ret)
         return (int)ret;
	  */
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
	  //eof Daigang
   }
   else if (eAct == MM::BeforeGet)
   {
	  //Daigang 24-may-2007
	  /*
      int temp;
      unsigned int ret = GetTemperature(&temp);
	  if ( ret == DRV_TEMP_OFF) pProp->Set( (long)0);
	  else pProp->Set( (long)1);
	  */
	  //eof Daigang
   }
   return DEVICE_OK;
}
// eof jizhen

//jizhen 05.16.2007
/**
 * Set fan mode.
 */
int Ixon::OnFanMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
	//added to use RTA
	SetToIdle();

	  //Daigang 24-may-2007
	  /*
      long mode;
      pProp->Get(mode);
	  unsigned int ret;
	  ret = SetFanMode((int)mode);
      if (DRV_SUCCESS != ret)
         return (int)ret;
	  */
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

   }
   else if (eAct == MM::BeforeGet)
   {
   }
   return DEVICE_OK;
}
// eof jizhen

int Ixon::OnInternalShutter(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
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
   }
   else if (eAct == MM::BeforeGet)
   {
   }
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Utility methods
///////////////////////////////////////////////////////////////////////////////

int Ixon::ResizeImageBuffer()
{
   // resize internal buffers
   // NOTE: we are assuming 16-bit pixel type
   const int bytesPerPixel = 2;
   img_.Resize(roi_.xSize / binSize_, roi_.ySize / binSize_, bytesPerPixel);
   return DEVICE_OK;
}


//daigang 24-may-2007
void Ixon::UpdateEMGainRange()
{
	//added to use RTA
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
   int nRet = SetProperty("EMGainRangeMin", emgLow.str().c_str());
   if (nRet != DEVICE_OK)
      return;
   nRet = SetProperty("EMGainRangeMax", emgHigh.str().c_str());
   if (nRet != DEVICE_OK)
      return;
}
//eof Daigang

//daigang 24-may-2007
/**
 * EMGain Range Max
 */
int Ixon::OnEMGainRangeMax(MM::PropertyBase* pProp, MM::ActionType eAct)
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
//eof Daigang

/**
 * ActualInterval_ms
 */
int Ixon::OnActualIntervalMS(MM::PropertyBase* pProp, MM::ActionType eAct)
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

//daigang 24-may-2007
/**
 * EMGain Range Max
 */
int Ixon::OnEMGainRangeMin(MM::PropertyBase* pProp, MM::ActionType eAct)
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
//eof Daigang




/**
 * Frame transfer mode ON or OFF.
 */
int Ixon::OnFrameTransfer(MM::PropertyBase* pProp, MM::ActionType eAct)
{
     SetToIdle();

   if (eAct == MM::AfterSet)
   {
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
         return DEVICE_INVALID_PROPERTY_VALUE;

      // wait for camera to finish acquiring
      int status = DRV_IDLE;
      unsigned ret = GetStatus(&status);
      while (status == DRV_ACQUIRING && ret == DRV_SUCCESS)
         ret = GetStatus(&status);

      ret = SetFrameTransferMode(modeIdx);
      if (ret != DRV_SUCCESS)
         return ret;
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
void Ixon::SetToIdle()
{
   if(!initialized_ || !IsAcquiring())
      return;
   unsigned ret = AbortAcquisition();
   if (ret != DRV_SUCCESS)
      CheckError(ret);
}


/**
 * check if camera is acquiring
 */
bool Ixon::IsAcquiring()
{
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
bool Ixon::IsThermoSteady()
{
	return ThermoSteady_;
}

void Ixon::CheckError(unsigned int /*errorVal*/)
{
}

/**
 * Set output amplifier.
 */
int Ixon::OnOutputAmplifier(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
     SetToIdle();

	 string strAmp;
      pProp->Get(strAmp);
      int AmpIdx = 0;
      if (strAmp.compare(g_OutputAmplifier_EM) == 0)
         AmpIdx = 0;
      else if (strAmp.compare(g_OutputAmplifier_Conventional) == 0)
         AmpIdx = 1;
      else
         return DEVICE_INVALID_PROPERTY_VALUE;

	  OutputAmplifierIndex_ = AmpIdx;

      unsigned ret = SetOutputAmplifier(AmpIdx);
      if (ret != DRV_SUCCESS)
         return (int)ret;

	  UpdateHSSpeeds();

   }
   else if (eAct == MM::BeforeGet)
   {
   }
   return DEVICE_OK;
}



/**
 * Set output amplifier.
 */
int Ixon::OnADChannel(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
     SetToIdle();

	 string strADChannel;
      pProp->Get(strADChannel);
      int ADChannelIdx = 0;
      if (strADChannel.compare(g_ADChannel_14Bit) == 0)
         ADChannelIdx = 0;
      else if (strADChannel.compare(g_ADChannel_16Bit) == 0)
         ADChannelIdx = 1;
      else
         return DEVICE_INVALID_PROPERTY_VALUE;

	  ADChannelIndex_ = ADChannelIdx;

      unsigned int ret = SetADChannel(ADChannelIdx);
      if (ret != DRV_SUCCESS)
         return (int)ret;

	  UpdateHSSpeeds();

   }
   else if (eAct == MM::BeforeGet)
   {
   }
   return DEVICE_OK;
}

//daigang 24-may-2007
void Ixon::UpdateHSSpeeds()
{
	//Daigang 28-may-2007 added to use RTA
	SetToIdle();

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
      sprintf(speedBuf, "%.1f MHz", sp);
      readoutModes_.push_back(speedBuf);
   }
   SetAllowedValues(MM::g_Keyword_ReadoutMode, readoutModes_);

   if(HSSpeedIdx_ > (int)readoutModes_.size())
   {
	   HSSpeedIdx_ = 0;
   }
   ret = SetHSSpeed(OutputAmplifierIndex_, HSSpeedIdx_);
   if (ret == DRV_SUCCESS)
     SetProperty(MM::g_Keyword_ReadoutMode,readoutModes_[HSSpeedIdx_].c_str());

   GetReadoutTime();


}
//eof Daigang




///////////////////////////////////////////////////////////////////////////////
// Continuous acquisition
//

/**
 * Continuous acquisition thread service routine.
 * Starts acquisition on the IXon and repeatedly calls PushImage()
 * to transfer any new images to the MMCore circularr buffer.
 */
int AcqSequenceThread::svc(void)
{
   long acc;
   long series(0);
   long seriesInit;
   unsigned ret;
   long waitTime;

   
   DWORD timePrev = GetTickCount();
   ret = GetAcquisitionProgress(&acc, &seriesInit);

   if (ret != DRV_SUCCESS)
   {
      camera_->StopSequenceAcquisition();
      printf("Error %d\n", ret);
      return ret;
   }
   float fExposure, fAccumTime, fKineticTime;
   GetAcquisitionTimings(&fExposure,&fAccumTime,&fKineticTime);
   float ActualInterval_ms = fKineticTime * 1000.0f;

   waitTime = (long) (ActualInterval_ms / 5);
   // wait for frames to start coming in
   do
   {
      ret = GetAcquisitionProgress(&acc, &series);
      if (ret != DRV_SUCCESS)
      {
         camera_->StopSequenceAcquisition();
         printf("Error %d\n", ret);
         return ret;
      }
   } while (series == seriesInit);
   long seriesPrev = 0;
   long frmcnt = 0;
   do
   {
      //GetStatus(&status);
      ret = GetAcquisitionProgress(&acc, &series);
      if (ret == DRV_SUCCESS)
      {
         if (series > seriesPrev)
         {
            // new frame arrived
            int retCode = camera_->PushImage();
            if (retCode != DEVICE_OK)
            {
               printf("PushImage failed with error code %d\n", retCode);
               //camera_->StopSequenceAcquisition();
               //return ret;
            }

            // report time elapsed since previous frame
            //printf("Frame %d captured at %ld ms!\n", ++frameCounter, GetTickCount() - timePrev);
            seriesPrev = series;
            frmcnt++;
            timePrev = GetTickCount();
         }
         Sleep(waitTime);
      }
   }
   //while (ret == DRV_SUCCESS && series < numImages_ && !stop_);
   while (ret == DRV_SUCCESS && frmcnt < numImages_ && !stop_);

   if (ret != DRV_SUCCESS && series != 0)
   {
      camera_->StopSequenceAcquisition();
      printf("Error %d\n", ret);
      return ret;
   }

   if (stop_)
   {
      printf("Acquisition interrupted by the user!\n");
      return 0;
   }
  
   if ((series-seriesInit) == numImages_)
   {
      printf("Done!\n");
      camera_->StopSequenceAcquisition();
      return 0;
   }
   
   printf("series: %ld, serieInit: %ld, numImages: %ld\n", series, seriesInit, numImages_);
   camera_->StopSequenceAcquisition();
   return 3; // we can get here if we are not fast enough.  Report?  
}

/**
 * Starts continuous acquisition.
 */
int Ixon::StartSequenceAcquisition(long numImages, double interval_ms)
{
   if (acquiring_)
      return ERR_BUSY_ACQUIRING;

   if(IsAcquiring())
   {
     SetToIdle();
   }
   int ret0 = SetTriggerMode(0);  //set software trigger. mode 0:internal, 1: ext, 6:ext start, 7:bulb, 10:software
   if (ret0 != DRV_SUCCESS)
      return ret0;

   if(bFrameTransfer_ && bSoftwareTriggerSupported)
     ret0 = SetFrameTransferMode(1);  //FT mode might be turned off in SnapImage when Software trigger mode is used. Resume it here


   ostringstream os;
   os << "Started sequnce acquisition: " << numImages << " at " << interval_ms << " ms" << endl;
   LogMessage(os.str().c_str());

   // prepare the camera
   int ret = SetAcquisitionMode(5); // run till abort
   if (ret != DRV_SUCCESS)
      return ret;

   ret = SetReadMode(4); // image mode
   if (ret != DRV_SUCCESS)
      return ret;

   // set AD-channel to 14-bit
//   ret = SetADChannel(0);
   if (ret != DRV_SUCCESS)
      return ret;

   SetExposureTime((float) (expMs_/1000.0));

   ret = SetNumberAccumulations(1);
   if (ret != DRV_SUCCESS)
   {
      SetAcquisitionMode(1);
      return ret;
   }

   ret = SetKineticCycleTime((float)(interval_ms / 1000.0));
   if (ret != DRV_SUCCESS)
   {
      SetAcquisitionMode(1);
      return ret;
   }

   long size;
   ret = GetSizeOfCircularBuffer(&size);
   if (ret != DRV_SUCCESS)
   {
      SetAcquisitionMode(1);
      return ret;
   }

   // re-apply the frame transfer mode setting
   char ftMode[MM::MaxStrLength];
   ret = GetProperty(g_FrameTransferProp, ftMode);
   assert(ret == DEVICE_OK);
   int modeIdx = 0;
   if (strcmp(g_FrameTransferOn, ftMode) == 0)
      modeIdx = 1;
   else if (strcmp(g_FrameTransferOff, ftMode) == 0)
      modeIdx = 0;
   else
      return DEVICE_INVALID_PROPERTY_VALUE;

   ret = SetFrameTransferMode(modeIdx);
   if (ret != DRV_SUCCESS)
   {
      SetAcquisitionMode(1);
      return ret;
   }

   // start thread
   imageCounter_ = 0;
   sequenceLength_ = numImages;

   seqThread_->SetLength(numImages);

   float fExposure, fAccumTime, fKineticTime;
   GetAcquisitionTimings(&fExposure,&fAccumTime,&fKineticTime);
   SetProperty(MM::g_Keyword_ActualInterval_ms, CDeviceUtils::ConvertToString((double)fKineticTime * 1000.0)); 
   ActualInterval_ms_ = fKineticTime * 1000.0f;


   // prepare the core
   ret = GetCoreCallback()->PrepareForAcq(this);
   if (ret != DEVICE_OK)
   {
      SetAcquisitionMode(1);
      return ret;
   }

   ret = StartAcquisition();
   seqThread_->Start();

   acquiring_ = true;
   if (ret != DRV_SUCCESS)
   {
      SetAcquisitionMode(1);
      return ret;
   }

   return DEVICE_OK;
}

/**
 * Stops acquisition
 */
int Ixon::StopSequenceAcquisition()
{
   LogMessage("Stopped sequence acquisition");
   AbortAcquisition();
   seqThread_->Stop();
   acquiring_ = false;
   int ret;
   if(bSoftwareTriggerSupported)
   {
     ret = SetTriggerMode(10);  //set software trigger. mode 0:internal, 1: ext, 6:ext start, 7:bulb, 10:software
     //if (ret != DRV_SUCCESS) //not check to allow call of AcqFinished
     //  return ret;
     ret = SetAcquisitionMode(5);//set RTA non-iCam camera
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
   MM::Core* cb = GetCoreCallback();
   if (cb)
      return cb->AcqFinished(this, 0);
   else
      return DEVICE_OK;
}

/**
 * Waits for new image and inserts it in the circular buffer.
 * This method is called by the acquisition thread AcqSequenceThread::svc()
 * in an infinite loop.
 *
 * In case of error or if the sequecne is finished StopSequenceAcquisition()
 * is called, which will raise the stop_ flag and cause the thread to exit.
 */
int Ixon::PushImage()
{
   // get the top most image from the driver
   unsigned ret = GetNewData16((WORD*)fullFrameBuffer_, roi_.xSize/binSize_ * roi_.ySize/binSize_);
   if (ret != DRV_SUCCESS)
      return (int)ret;

   // process image
   MM::ImageProcessor* ip = GetCoreCallback()->GetImageProcessor(this);
   if (ip)
   {
      int ret = ip->Process((unsigned char*) fullFrameBuffer_, GetImageWidth(), GetImageHeight(), GetImageBytesPerPixel());
      if (ret != DEVICE_OK)
         return ret;
   }

   // This method inserts new image in the circular buffer (residing in MMCore)
   return GetCoreCallback()->InsertImage(this, (unsigned char*) fullFrameBuffer_,
                                           GetImageWidth(),
                                           GetImageHeight(),
                                           GetImageBytesPerPixel());
}
