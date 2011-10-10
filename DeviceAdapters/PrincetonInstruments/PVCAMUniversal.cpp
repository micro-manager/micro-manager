///////////////////////////////////////////////////////////////////////////////
// FILE:          Universal.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Princeton Instruments module
// COPYRIGHT:     University of California, San Francisco, 2006, 2007, 2008, 2009
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
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 06/30/2006
//                Contributions by:
//                   Nico Stuurman
//                   Arthur Edelstein
//
// HISTORY:
//                4/17/2009: Major cleanup and additions to make multiple cameras work (Nico + Nenad)
//                2010 05 10 split Princeton Instruments out of Photometrics adapter
//
// CVS:           $Id: PVCAMUniversal.cpp 4134 2010-03-06 00:44:21Z karlh $

#ifdef WIN32
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <stdint.h>
#pragma warning(disable : 4996) // disable warning for deperecated CRT functions on Windows 
#endif

#include "../../MMDevice/ModuleInterface.h"
#include "PVCAMInt.h"
#include "PVCAMUtils.h"
#include "PVCAMProperty.h"

#ifdef WIN32
#include "master.h"
#include "pvcam.h"
#endif

#ifdef __APPLE__
#define __mac_os_x
#include <PVCAM/master.h>
#include <PVCAM/pvcam.h>
#endif

#ifdef linux
#include <pvcam/master.h>
#include <pvcam/pvcam.h>
#endif

#include <string>
#include <sstream>
#include <iomanip>

using namespace std;
unsigned Universal::refCount_ = 0;
bool Universal::PVCAM_initialized_ = false;
MMThreadLock g_pvcamLock;

const int BUFSIZE = 60;
#if WIN32
#define snprintf _snprintf
#endif

// global constants
extern const char* g_PixelType_8bit;
extern const char* g_PixelType_10bit;
extern const char* g_PixelType_12bit;
extern const char* g_PixelType_14bit;
extern const char* g_PixelType_16bit;

extern const char* g_ReadoutRate;
extern const char* g_ReadoutPort;
extern const char* g_ReadoutPort_Normal;
extern const char* g_ReadoutPort_Multiplier;
extern const char* g_ReadoutPort_LowNoise;
extern const char* g_ReadoutPort_HighCap;

const char* g_TriggerMode = "TriggerMode";

const char* g_CoolingFan = "CoolingFan";

string Yes("Yes");
string No("No");
string shutter("Shutter");
string rapidCal("Rapid Calibration");
string zeroFlux("0 electrons/microsec");
string oneFlux("1 electron/msec");
string twoFlux("2 electrons/sec");

SParam param_set[] = {
   //clear
   //{"ClearMode", PARAM_CLEAR_MODE},
   {"ClearCycles", PARAM_CLEAR_CYCLES},
   {"ContineousClears", PARAM_CONT_CLEARS},
   {"MinBlock", PARAM_MIN_BLOCK},
   {"NumBlock", PARAM_NUM_MIN_BLOCK},
   {"NumStripsPerClean", PARAM_NUM_OF_STRIPS_PER_CLR},
	{"Firmware Version", PARAM_CAM_FW_VERSION},
   // readout
   {"PMode", PARAM_PMODE},
   //{"ADCOffset", PARAM_ADC_OFFSET},
   {"FTCapable", PARAM_FRAME_CAPABLE},
   {"FullWellCapacity", PARAM_FWELL_CAPACITY},
   //{"FTDummies", PARAM_FTSCAN},
   {"ClearMode", PARAM_CLEAR_MODE},
   {"PreampDelay", PARAM_PREAMP_DELAY},
   {"PreampOffLimit", PARAM_PREAMP_OFF_CONTROL}, // preamp is off during exposure if exposure time is less than this
   {"MaskLines", PARAM_PREMASK},
   {"PrescanPixels", PARAM_PRESCAN},
   {"PostscanPixels", PARAM_POSTSCAN},
   {"X-dimension", PARAM_SER_SIZE},
   {"Y-dimension", PARAM_PAR_SIZE},
   {"ShutterMode",PARAM_SHTR_OPEN_MODE},
   {"ExposureMode", PARAM_EXPOSURE_MODE},
   {"LogicOutput", PARAM_LOGIC_OUTPUT},
   {"CoolingFan", PARAM_HEAD_COOLING_CTRL}
};
const int n_param = sizeof(param_set)/sizeof(SParam);


///////////////////////////////////////////////////////////////////////////////
// &Universal constructor/destructor
Universal::Universal(short cameraId) :
CCameraBase<Universal> (),
initialized_(false),
busy_(false),
hPVCAM_(0),
exposure_(0),
binSize_(1),
bufferOK_(false),
cameraId_(cameraId),
name_("Undefined"),
nrPorts_ (1),
circBuffer_(0),
stopOnOverflow_(true),
restart_(false),
snappingSingleFrame_(false),
singleFrameModeReady_(false),
use_pl_exp_check_status_(true),
imageCounter_(0),
sequenceModeReady_(false)

{
   InitializeDefaultErrorMessages();

   // add custom messages
   SetErrorText(ERR_CAMERA_NOT_FOUND, "No Camera Found. Is it connected and switched on?");
   SetErrorText(ERR_BUSY_ACQUIRING, "Acquisition already in progress.");
   
   //Set the list of post processing features tied to their parameter, only works with Windows at the moment
#ifdef WIN32_NOT
   mPPNames.insert(PPNamesDef::value_type(PP_PARAMETER_RF_FUNCTION			,"RING FUNCTION FUNCTION"));
   mPPNames.insert(PPNamesDef::value_type(PP_FEATURE_BIAS_ENABLED				,"BIAS ENABLED"			));
   mPPNames.insert(PPNamesDef::value_type(PP_FEATURE_BIAS_LEVEL				,"BIAS LEVEL"				));
   mPPNames.insert(PPNamesDef::value_type(PP_FEATURE_BERT_ENABLED				,"B.E.R.T. ENABLED"		));
   mPPNames.insert(PPNamesDef::value_type(PP_FEATURE_BERT_THRESHOLD			,"B.E.R.T. THRESHOLD"	));
   mPPNames.insert(PPNamesDef::value_type(PP_FEATURE_QUANT_VIEW_ENABLED		,"QUANT-VIEW ENABLED"	));
   mPPNames.insert(PPNamesDef::value_type(PP_FEATURE_QUANT_VIEW_E				,"QUANT-VIEW (E)"			));
   mPPNames.insert(PPNamesDef::value_type(PP_FEATURE_BLACK_LOCK_ENABLED		,"BLACK LOCK ENABLED"	));
   mPPNames.insert(PPNamesDef::value_type(PP_FEATURE_BLACK_LOCK_BLACK_CLIP	,"BLACK LOCK BLACK CLIP"));
   mPPNames.insert(PPNamesDef::value_type(PP_FEATURE_TOP_LOCK_ENABLED		,"TOP LOCK ENABLED"		));
   mPPNames.insert(PPNamesDef::value_type(PP_FEATURE_TOP_LOCK_WHITE_CLIP	,"TOP LOCK WHITE CLIP"	));
   mPPNames.insert(PPNamesDef::value_type(PP_FEATURE_VARI_BIT_ENABLED		,"VARI-BIT ENABLED"		));
   mPPNames.insert(PPNamesDef::value_type(PP_FEATURE_VARI_BIT_BIT_DEPTH		,"VARI-BIT BIT DEPTH"	));
   mPPNames.insert(PPNamesDef::value_type(PP_FEATURE_FLUX_VIEW_ENABLED		,"FLUX VIEW ENABLED"		));
   mPPNames.insert(PPNamesDef::value_type(PP_FEATURE_FLUX_VIEW_TIME_SCALE	,"FLUX VIEW TIME SCALE"	));
#endif
}


Universal::~Universal()
{   
   refCount_--;
   if (refCount_ == 0)
   {
      // release resources
      if (initialized_)
         Shutdown();
      delete[] circBuffer_;
   }
}

int Universal::GetBinning () const 
{
   return binSize_;
}

int Universal::SetBinning (int binSize) 
{
   ostringstream os;
   os << binSize;
   return SetProperty(MM::g_Keyword_Binning, os.str().c_str());
}


///////////////////////////////////////////////////////////////////////////////
// Action handlers
// ~~~~~~~~~~~~~~~

// Binning
int Universal::OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      SuspendSequence();
      long bin;
      unsigned long oldBinSize = binSize_;
      pProp->Get(bin);
      binSize_ = bin;
      ClearROI(); // reset region of interest
      ResumeSequence(); 

      if (!IsCapturing() && (oldBinSize != binSize_)) {
         if (!pl_exp_stop_cont(hPVCAM_, CCS_HALT))
            LogCamError(__LINE__);
         if (!pl_exp_finish_seq(hPVCAM_, circBuffer_, 0))
            LogCamError(__LINE__);
         int nRet = ResizeImageBufferSingle();
         if (nRet != DEVICE_OK)
            return nRet;
      }
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set((long)binSize_);
   }
   return DEVICE_OK;
}

// Chip Name
int Universal::OnChipName(MM::PropertyBase* pProp, MM::ActionType eAct)
{

   // Read-Only
   if (eAct == MM::BeforeGet)
   {
      char chipName[CCD_NAME_LEN];
      if (!PlGetParamSafe(hPVCAM_, PARAM_CHIP_NAME, ATTR_CURRENT, chipName))
         return LogCamError(__LINE__);

      pProp->Set(chipName);
      chipName_  = chipName;
   }


   return DEVICE_OK;

}

// Sets and gets exposure
int Universal::OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   // exposure property is stored in milliseconds,
   // whereas the driver returns the value in seconds
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(exposure_);
   }
   else if (eAct == MM::AfterSet)
   {
      SuspendSequence();
      double exp, oldExposure;
      oldExposure = exposure_;
      pProp->Get(exp);
      exposure_ = exp;
      ResumeSequence();

      if (!IsCapturing() && (exposure_ != oldExposure)) {
         int nRet = ResizeImageBufferSingle();
         if (nRet != DEVICE_OK)
            return nRet;
      }

   }
   return DEVICE_OK;
}

int Universal::OnPixelType(MM::PropertyBase* pProp, MM::ActionType /*eAct*/)
{  
   int16 bitDepth;
   if (!PlGetParamSafe( hPVCAM_, PARAM_BIT_DEPTH, ATTR_CURRENT, &bitDepth))
      return LogCamError(__LINE__);

   switch (bitDepth) {
      case (8) : pProp->Set(g_PixelType_8bit); return DEVICE_OK;
      case (10) : pProp->Set(g_PixelType_10bit); return DEVICE_OK;
      case (12) : pProp->Set(g_PixelType_12bit); return DEVICE_OK;
      case (14) : pProp->Set(g_PixelType_14bit); return DEVICE_OK;
      case (16) : pProp->Set(g_PixelType_16bit); return DEVICE_OK;
   }

   return DEVICE_OK;
}


// Camera Speed
int Universal::OnReadoutRate(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   int nRet = DEVICE_OK;

   if (eAct == MM::AfterSet)
   {
      singleFrameModeReady_=false;
      long gain;
      GetLongParam_PvCam_safe(hPVCAM_, PARAM_GAIN_INDEX, &gain);

      string par;
      pProp->Get(par);
      long index;
      std::map<std::string, int>::iterator iter = rateMap_.find(par);
      if (iter != rateMap_.end())
         index = iter->second;
      else
         return ERR_INVALID_PARAMETER_VALUE;

      if (!SetLongParam_PvCam_safe(hPVCAM_, PARAM_SPDTAB_INDEX, index))
         return LogCamError(__LINE__);

      // Try setting the gain to original value, don't make a fuss when it fails
      SetLongParam_PvCam_safe(hPVCAM_, PARAM_GAIN_INDEX, gain);

      // Investigate which gain and pixeltype values are allowed at this speed
      nRet = SetGainLimits();
      if (nRet != DEVICE_OK)
         return nRet;
      nRet = SetAllowedPixelTypes();
      if (nRet != DEVICE_OK)
         return nRet;

      // update GUI to reflect these changes
      nRet = OnPropertiesChanged();
      if (nRet != DEVICE_OK)
         return nRet;

      if (!IsCapturing()) {
         nRet = ResizeImageBufferSingle();
         if (nRet != DEVICE_OK)
            return nRet;
      }

   }
   else if (eAct == MM::BeforeGet)
   {
      long index;
      if (!GetLongParam_PvCam_safe(hPVCAM_, PARAM_SPDTAB_INDEX, &index))
         return LogCamError(__LINE__);
      string mode;
      nRet = GetSpeedString(mode);
      if (nRet != DEVICE_OK)
         return nRet;
      pProp->Set(mode.c_str());
   }


   return DEVICE_OK;
}

// Readout Port
int Universal::OnReadoutPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      singleFrameModeReady_=false;
      string par;
      pProp->Get(par);
      int port;
      std::map<std::string, int>::iterator iter = portMap_.find(par);
      if (iter != portMap_.end())
         port = iter->second;
      else
         return ERR_INVALID_PARAMETER_VALUE;

      ostringstream tmp;
      tmp << "Set port to: " << par << " ID: " << port;
      LogMessage(tmp.str().c_str(), true);

      if (!SetLongParam_PvCam_safe(hPVCAM_, PARAM_READOUT_PORT, port))
         return LogCamError(__LINE__);

      // Update elements that might have changed because of port change
      int ret = GetSpeedTable();
      if (ret != DEVICE_OK)
         return ret;
      ret = SetGainLimits();
      if (ret != DEVICE_OK)
         return ret;
      ret = SetAllowedPixelTypes();
      if (ret != DEVICE_OK)
         return ret;

      // update GUI to reflect these changes
      ret = OnPropertiesChanged();
      if (ret != DEVICE_OK)
         return ret;

      if (!IsCapturing()) {
         ret = ResizeImageBufferSingle();
         if (ret != DEVICE_OK)
            return ret;
      }
   }
   else if (eAct == MM::BeforeGet)
   {
      long port;
      if (!GetLongParam_PvCam_safe(hPVCAM_, PARAM_READOUT_PORT, &port))
         return LogCamError(__LINE__);
      long enumv;
      std::string portName;// = GetPortNameAndEnum(port, enumv);
      for( std::map<std::string, int>::iterator iter = portMap_.begin(); iter != portMap_.end(); ++iter)
      {
         if (port  == iter->second)
         {
            portName = iter->first;
            break;
         }
      }

      ostringstream tmp;
      tmp << "Get port  " << portName << " ID: " << port;
      LogMessage(tmp.str().c_str(), true);

      pProp->Set(portName.c_str());
   }


   return DEVICE_OK;
}

// Gain
int Universal::OnGain(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      singleFrameModeReady_=false;
      long gain;
      pProp->Get(gain);
      if (!SetLongParam_PvCam_safe(hPVCAM_, PARAM_GAIN_INDEX, gain))
         return LogCamError(__LINE__);

      if (!IsCapturing()) {
         int ret = ResizeImageBufferSingle();
         if (ret != DEVICE_OK)
            return ret;
      }
   }
   else if (eAct == MM::BeforeGet)
   {
      long gain;
      if (!GetLongParam_PvCam_safe(hPVCAM_, PARAM_GAIN_INDEX, &gain))
         return LogCamError(__LINE__);
      pProp->Set(gain);
   }

   return DEVICE_OK;
}


void Universal::SuspendSequence()
{
   if(IsCapturing())
   {
      restart_ = true;
      thd_->Stop();
      thd_->wait();

      if (!pl_exp_stop_cont(hPVCAM_, CCS_HALT)) 
            LogCamError(__LINE__, "");
      if (!pl_exp_finish_seq(hPVCAM_, circBuffer_, 0))
            LogCamError(__LINE__, "");
   } 
}

int Universal::ResumeSequence()
{
   if(restart_) 
   {
      int ret = ResizeImageBufferContinuous();
      if(ret != DEVICE_OK) 
          return LogMMError(ret, __LINE__); 

      if (!pl_exp_start_cont(hPVCAM_, circBuffer_, bufferSize_)) 
         return LogCamError(__LINE__);

      long imageCount = thd_->GetImageCounter();
      double intervalMs = thd_->GetIntervalMs();
      thd_->Start(numImages_ - imageCount, intervalMs);
      restart_ = false;
   }

   return DEVICE_OK;
}


// EM Gain
int Universal::OnMultiplierGain(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
	   SuspendSequence();
      long gain, oldGain;
      pProp->Get(gain);

      if (!GetLongParam_PvCam_safe(hPVCAM_, PARAM_GAIN_MULT_FACTOR, &oldGain))
         return LogCamError(__LINE__);

      if (gain != oldGain) 
         if (!SetLongParam_PvCam(hPVCAM_, PARAM_GAIN_MULT_FACTOR, gain))
            return LogCamError(__LINE__);

      ResumeSequence();

      if (!IsCapturing() && gain != oldGain) {
         int nRet = ResizeImageBufferSingle();
         if (nRet != DEVICE_OK)
            return nRet;
      }

   }
   else if (eAct == MM::BeforeGet)
   {
      long gain;

      if (!GetLongParam_PvCam_safe(hPVCAM_, PARAM_GAIN_MULT_FACTOR, &gain))
         return LogCamError(__LINE__);

      pProp->Set(gain);
   }

   return DEVICE_OK;
}

// Offset
int Universal::OnOffset(MM::PropertyBase* /*pProp*/, MM::ActionType /*eAct*/)
{
   return DEVICE_OK;
}

// Temperature
int Universal::OnTemperature(MM::PropertyBase* pProp, MM::ActionType eAct)
{


   if (eAct == MM::AfterSet)
   {
   }
   else if (eAct == MM::BeforeGet)
   {
      long temp;

      if (!GetLongParam_PvCam_safe(hPVCAM_, PARAM_TEMP, &temp))
         return LogCamError(__LINE__);

      pProp->Set(temp/100);
   }

   return DEVICE_OK;
}
   
int Universal::OnCoolingFan(MM::PropertyBase* pProp, MM::ActionType eAct) {
	if (eAct == MM::AfterSet) {
	  long fanState;
      string par;
      pProp->Get(par);

	  if( par == "On" ) {
		  //fprintf( stderr, "selected fan to On; par = %s", par );
		  fanState = COOLING_FAN_CTRL_ON;
	  } else if( par == "Off" ) {
		  //fprintf( stderr, "selected fan to Off; par = %s", par );
		  fanState = COOLING_FAN_CTRL_OFF;
	  }

      if (!SetLongParam_PvCam_safe(hPVCAM_, PARAM_COOLING_FAN_CTRL, fanState))
         return LogCamError(__LINE__);
 //  
   } else if (eAct == MM::BeforeGet) {
 //     long fan;

 //     if (!GetLongParam_PvCam_safe(hPVCAM_, PARAM_COOLING_FAN_CTRL, &fan))
 //        return LogCamError(__LINE__);
	//  fprintf( stderr, "beforeget fan %li\n", fan );

 //     pProp->Set(fanOff);
   }
   return DEVICE_OK;
}

int Universal::OnTemperatureSetPoint(MM::PropertyBase* pProp, MM::ActionType eAct)
{


   if (eAct == MM::AfterSet)
   {
      long temp;
      pProp->Get(temp);
      temp = temp * 100;

      if (!SetLongParam_PvCam_safe(hPVCAM_, PARAM_TEMP_SETPOINT, temp))
         return LogCamError(__LINE__);

   }
   else if (eAct == MM::BeforeGet)
   {
      long temp;

      if (!GetLongParam_PvCam_safe(hPVCAM_, PARAM_TEMP_SETPOINT, &temp))
         return LogCamError(__LINE__);

      pProp->Set(temp/100);
   }


   return DEVICE_OK;
}

int Universal::OnUniversalProperty(MM::PropertyBase* pProp, MM::ActionType eAct, long index)
{


   if (eAct == MM::AfterSet)
   {
      singleFrameModeReady_=false;
      uns16 dataType;
      if (!PlGetParamSafe(hPVCAM_, param_set[index].id, ATTR_TYPE, &dataType) || (dataType != TYPE_ENUM)) 
      {
         long ldata;
         pProp->Get(ldata);

         if (!SetLongParam_PvCam_safe(hPVCAM_, (long)param_set[index].id, (uns32) ldata))
            return LogCamError(__LINE__, "");

      } else 
      {
         std::string mnemonic;
         pProp->Get(mnemonic);
         uns32 ldata = param_set[index].enumMap[mnemonic];

         if (!SetLongParam_PvCam_safe(hPVCAM_, (long)param_set[index].id, (uns32) ldata))
            return LogCamError(__LINE__, "");
      }

      if (!IsCapturing()) 
      {
         int ret = ResizeImageBufferSingle();
         if (ret != DEVICE_OK)
            return ret;
      }

   }
   else if (eAct == MM::BeforeGet)
   {
      long ldata;

      if (!GetLongParam_PvCam_safe(hPVCAM_, param_set[index].id, &ldata))
         return LogCamError(__LINE__);

      uns16 dataType;
      if (!PlGetParamSafe(hPVCAM_, param_set[index].id, ATTR_TYPE, &dataType) || (dataType != TYPE_ENUM)) 
      {
         pProp->Set(ldata);
      } else 
      {
         char enumStr[100];
         int32 enumValue;
         // It is absurd, but we seem to need this param_set[index].indexMap[ldata] instead of straight ldata??!!!
         if (PlGetEnumParamSafe(hPVCAM_, param_set[index].id, param_set[index].indexMap[ldata], &enumValue, enumStr, 100)) 
         {
            pProp->Set(enumStr);
         } else 
         {
            return LogCamError(__LINE__, "Error in PlGetParamSafe\n");
         }
      }
   }


   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// API methods
// ~~~~~~~~~~~

void Universal::GetName(char* name) const 
{
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

///////////////////////////////////////////////////////////////////////////////
// Function name   : &Universal::Initialize
// Description     : Initializes the camera
//                   Sets up the (single) image buffer 
// Return type     : bool 

int Universal::Initialize()
{
   rs_bool bAvail;
   // setup the camera
   // ----------------

   // Description
   int nRet = CreateProperty(MM::g_Keyword_Description, "PVCAM API device adapter", MM::String, true);
   assert(nRet == DEVICE_OK);

   // gather information about the equipment
   // ------------------------------------------

   // Camera name
   char name[CAM_NAME_LEN] = "Undef";

   if (!PVCAM_initialized_)
   {
      if (!pl_pvcam_init())
      {
         LogCamError(__LINE__, "First PVCAM init failed");
         // Try once more:
         pl_pvcam_uninit();
         if (!pl_pvcam_init())
            return LogCamError(__LINE__, "First PVCAM init failed");
      }
      PVCAM_initialized_ = true;
   }

   // Get PVCAM version
   uns16 version;
   if (!pl_pvcam_get_ver(&version))
      return LogCamError(__LINE__);

   uns16 major, minor, trivial;
   major = version; major = major >> 8; major = major & 0xFF;
   minor = version; minor = minor >> 4; minor = minor & 0xF;
   trivial = version; trivial = trivial & 0xF;
   stringstream ver;
   ver << major << "." << minor << "." << trivial;
   nRet = CreateProperty("PVCAM Version", ver.str().c_str(),  MM::String, true);
   LogMessage("PVCAM VERSION: " + ver.str());
   assert(nRet == DEVICE_OK);

   // find camera
   if (!pl_cam_get_name(cameraId_, name))
   {
      LogCamError(__LINE__, "No Camera");
      return ERR_CAMERA_NOT_FOUND;
   }

   // Get a handle to the camera
   if (!pl_cam_open(name, &hPVCAM_, OPEN_EXCLUSIVE ))
      return LogCamError(__LINE__);

   if (!pl_cam_get_diags(hPVCAM_))
      return LogCamError(__LINE__);

   refCount_++;

   name_ = name;   // Name
   nRet = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   assert(nRet == DEVICE_OK);


   // Chip Name
   CPropertyAction *pAct = new CPropertyAction (this, &Universal::OnChipName);
   nRet = CreateProperty("ChipName", "", MM::String, true, pAct);

   // Bit depth
   if (!PlGetParamSafe(hPVCAM_, PARAM_BIT_DEPTH, ATTR_CURRENT, &bitDepth_))
      return LogCamError(__LINE__, "");

   // setup image parameters
   // ----------------------

   // exposure
   pAct = new CPropertyAction (this, &Universal::OnExposure);
   nRet = CreateProperty(MM::g_Keyword_Exposure, "10.0", MM::Float, false, pAct);
   assert(nRet == DEVICE_OK);

   // binning
   pAct = new CPropertyAction (this, &Universal::OnBinning);
   nRet = CreateProperty(MM::g_Keyword_Binning, "1", MM::Integer, false, pAct);
   assert(nRet == DEVICE_OK);
   vector<string> binValues;
   // TODO: Readout available binning modes dynamically rather than hardcode them
   binValues.push_back("1");
   binValues.push_back("2");
   binValues.push_back("4");
   nRet = SetAllowedValues(MM::g_Keyword_Binning, binValues);
   if (nRet != DEVICE_OK)
      return nRet;

   // Pixel type.  
   // Note that this can change depending on the readoutport and speed.  SettAllowedPixeltypes should be called after changes in any of these
   pAct = new CPropertyAction (this, &Universal::OnPixelType);
   nRet = CreateProperty(MM::g_Keyword_PixelType, g_PixelType_16bit, MM::String, false, pAct);
   assert(nRet == DEVICE_OK);
   nRet = SetAllowedPixelTypes();
   if (nRet != DEVICE_OK)
      return nRet;

   // Gain
   // Check the allowed gain settings.  Note that this can change depending on output port, and readout rate. SetGainLimits() should be called after changing those parameters. 
   if (!PlGetParamSafe( hPVCAM_, PARAM_GAIN_INDEX, ATTR_AVAIL, &gainAvailable_))
      return LogCamError(__LINE__, "");
   if (gainAvailable_) {
      pAct = new CPropertyAction (this, &Universal::OnGain);
      nRet = CreateProperty(MM::g_Keyword_Gain, "1", MM::Integer, false, pAct);
      if (nRet != DEVICE_OK)
         return nRet;
      nRet = SetGainLimits();
      if (nRet != DEVICE_OK)
         return nRet;
   }

   // ReadoutPorts
   rs_bool readoutPortAvailable;
   if (!PlGetParamSafe( hPVCAM_, PARAM_READOUT_PORT, ATTR_AVAIL, &readoutPortAvailable))
      return LogCamError(__LINE__, "");
     
   if (readoutPortAvailable) {
      uns32 minPort, maxPort;
      //should it return?
      if (!PlGetParamSafe(hPVCAM_, PARAM_READOUT_PORT, ATTR_COUNT, &nrPorts_))
         LogCamError(__LINE__, "");
      if (!PlGetParamSafe(hPVCAM_, PARAM_READOUT_PORT, ATTR_MIN, &minPort))
         LogCamError(__LINE__, "");
      if (!PlGetParamSafe(hPVCAM_, PARAM_READOUT_PORT, ATTR_MAX, &maxPort))
         LogCamError(__LINE__, "");
      long currentPort;

      if (!GetLongParam_PvCam_safe(hPVCAM_, PARAM_READOUT_PORT, &currentPort))
         return LogCamError(__LINE__);

      ostringstream tmp;
      tmp << "Readout Ports, Nr: " << nrPorts_ << " min: " << minPort << " max: " << maxPort << "Current: " << currentPort;
      LogMessage(tmp.str().c_str(), true);

      pAct = new CPropertyAction (this, &Universal::OnReadoutPort);
      if (nrPorts_ <= 1)
         nRet = CreateProperty(g_ReadoutPort, g_ReadoutPort_Normal, MM::String, true, pAct);
      else
         nRet = CreateProperty(g_ReadoutPort, g_ReadoutPort_Normal, MM::String, false, pAct);
      assert(nRet == DEVICE_OK);
      // Found out what ports we have
      vector<string> portValues;
      for (uns32 i=minPort; i<=maxPort; i++) {
		  long enumValue = 0;
		  std::string portNameValue = GetPortNameAndEnum(i,enumValue);
        if (-1 < enumValue)
        {
         portValues.push_back(portNameValue);
         portMap_[portNameValue] = enumValue;
         std::ostringstream mes;
         mes << "adding port # " << minPort << " enum " << enumValue << " " << portNameValue;
         LogMessage(mes.str().c_str(),true);
        }
      }
      nRet = SetAllowedValues(g_ReadoutPort, portValues);
      if (nRet != DEVICE_OK)
         return nRet;
   }

   // Multiplier Gain
   rs_bool emGainAvailable;
   // The HQ2 has 'visual gain', which shows up as EM Gain.  
   // Detect whether this is an interline chip and do not expose EM Gain if it is.
   char chipName[CCD_NAME_LEN];
   if (!PlGetParamSafe(hPVCAM_, PARAM_CHIP_NAME, ATTR_CURRENT, chipName))
      return LogCamError(__LINE__);
   LogMessage(chipName);

   PlGetParamSafe( hPVCAM_, PARAM_GAIN_MULT_FACTOR, ATTR_AVAIL, &emGainAvailable);
   if (emGainAvailable && (strstr(chipName, "ICX-285") == 0) && (strstr(chipName, "ICX285") == 0) ) {
      LogMessage("This Camera has Em Gain");
      pAct = new CPropertyAction (this, &Universal::OnMultiplierGain);
      nRet = CreateProperty("MultiplierGain", "1", MM::Integer, false, pAct);
      assert(nRet == DEVICE_OK);
      int16 emGainMax;
      // Apparently, the minimum EM gain is always 1
      PlGetParamSafe( hPVCAM_, PARAM_GAIN_MULT_FACTOR, ATTR_MAX, &emGainMax);
      ostringstream s;
      s << "EMGain " << " " << emGainMax;
      LogMessage(s.str().c_str(), true);
      nRet = SetPropertyLimits("MultiplierGain", 1, emGainMax);
      if (nRet != DEVICE_OK)
         return nRet;
   } else
      LogMessage("This Camera does not have EM Gain");

   // Speed Table
   // Deduce available modes from the speed table and make all options available
   // The Speed table will change depending on the Readout Port
   pAct = new CPropertyAction (this, &Universal::OnReadoutRate);
   nRet = CreateProperty(g_ReadoutRate, "0", MM::String, false, pAct);
   if (nRet != DEVICE_OK)
      return nRet;
   nRet = GetSpeedTable();
   if (nRet != DEVICE_OK)
      return nRet;

   // camera temperature
   pAct = new CPropertyAction (this, &Universal::OnTemperature);
   nRet = CreateProperty(MM::g_Keyword_CCDTemperature, "25", MM::Integer, true, pAct);
   assert(nRet == DEVICE_OK);

   pAct = new CPropertyAction (this, &Universal::OnTemperatureSetPoint);
   nRet = CreateProperty(MM::g_Keyword_CCDTemperatureSetPoint, "-50", MM::Integer, false, pAct);
   assert(nRet == DEVICE_OK);

   //if the camera supports frame transfer, then set that as default
  	bool bFrameTransfer = false;

   // other ('Universal') parameters
   for (int i = 0 ; i < n_param; i++) 
   {
      long ldata;
      char buf[BUFSIZE];
      uns16 AccessType;
      rs_bool bAvail;
      bool versionTest = true;

      // Version cutoff is semi-arbitrary.  PI cameras with PVCAM 0x0271 need this
      if (strcmp(param_set[i].name, "PMode") == 0  && version < 0x0275)
         versionTest= false;

     //if the camera supports frame transfer, then set that as default
      if (param_set[i].id == PARAM_FRAME_CAPABLE)
         bFrameTransfer = true;

      bool getLongSuccess = GetLongParam_PvCam_safe(hPVCAM_, param_set[i].id, &ldata);

      PlGetParamSafe( hPVCAM_, param_set[i].id, ATTR_ACCESS, &AccessType);
      PlGetParamSafe( hPVCAM_, param_set[i].id, ATTR_AVAIL, &bAvail);
      if ( (AccessType != ACC_ERROR) && bAvail && getLongSuccess && versionTest) 
      {
         snprintf(buf, BUFSIZE, "%ld", ldata);
         CPropertyActionEx *pAct = new CPropertyActionEx(this, &Universal::OnUniversalProperty, (long)i);
         uint16_t dataType;
         rs_bool plResult = PlGetParamSafe(hPVCAM_, param_set[i].id, ATTR_TYPE, &dataType);
         if (!plResult)
            LogCamError(__LINE__, param_set[i].name);
         std::string propertyMsg = "Added property: " + std::string(param_set[i].name);
         LogMessage(propertyMsg.c_str(), true);
         if (!plResult || (dataType != TYPE_ENUM)) {
            nRet = CreateProperty(param_set[i].name, buf, MM::Integer, AccessType == ACC_READ_ONLY, pAct);
            if (nRet != DEVICE_OK)
               return nRet;

            // get allowed values for non-enum types 
            if (plResult) {
               nRet = SetUniversalAllowedValues(i, dataType);
               if (nRet != DEVICE_OK)
                  return nRet;
            }
            else {
               ostringstream os;
               os << "problems getting type info from parameter: " << param_set[i].name << " " << AccessType;
               LogMessage(os.str().c_str());
            }

         } else  // enum type, get the associated strings, store in a map and make accesible to the user interface
         {
            nRet = CreateProperty(param_set[i].name, buf, MM::String, AccessType == ACC_READ_ONLY, pAct);
            uns32 count, index;
            int32 enumValue;
            char enumStr[100];
            if (PlGetParamSafe(hPVCAM_, param_set[i].id, ATTR_COUNT, (void *) &count)) {
               for (index = 0; index < count; index++) {
                  if (PlGetEnumParamSafe(hPVCAM_, param_set[i].id, index, &enumValue, enumStr, 100)) {
                     AddAllowedValue(param_set[i].name, enumStr);
                     std::string tmp = enumStr;
                     param_set[i].indexMap[enumValue] = index;
                     param_set[i].enumMap[tmp] = enumValue;
                  }
                  else
                     LogMessage ("Error in PlGetParamSafe");
               }
            }
         }

         assert(nRet == DEVICE_OK);
      }
   }

   // create actual interval property
   CreateProperty(MM::g_Keyword_ActualInterval_ms, "0.0", MM::Float, false);

   // trigger mode
   const char* trigTimed = "Internal";
   const char* trigFirst = "ExternalFirst";
   const char* trigStrobed = "ExternalStrobed";
   CreateProperty(g_TriggerMode, trigTimed, MM::String, false);
   AddAllowedValue(g_TriggerMode, trigTimed, (long)TIMED_MODE);
   AddAllowedValue(g_TriggerMode, trigFirst, (long)TRIGGER_FIRST_MODE);
   AddAllowedValue(g_TriggerMode, trigStrobed, (long)STROBED_MODE);

   // cooling fan
   const char* fanOn = "On";
   const char* fanOff = "Off"; 

   int chkAvail;
   if (!PlGetParamSafe( hPVCAM_, PARAM_GAIN_INDEX, ATTR_AVAIL, &chkAvail ))
      return LogCamError(__LINE__, "");

   if( chkAvail ) {
	   pAct = new CPropertyAction (this, &Universal::OnCoolingFan);
	   nRet = CreateProperty(g_CoolingFan, fanOn, MM::String, false, pAct );
	   assert(nRet == DEVICE_OK);
	   AddAllowedValue(g_CoolingFan, fanOn, (long)COOLING_FAN_CTRL_ON);
	   AddAllowedValue(g_CoolingFan, fanOff, (long)COOLING_FAN_CTRL_OFF);
   } else
      LogMessage("This Camera's fan can't be controlled");

   // readout time is a function of the number of pixels to digitize, the ADC speed, and the overhead which includes
   //   skipping pixels, and all clocking vertical and horizontal
   // in cont mode, not triggering, frame rate should equal ( exposure time + shutter close delay + readout time )^-1
   //

   // synchronize all properties
   // --------------------------
   // nRet = UpdateStatus();
   // if(nRet != DEVICE_OK) 
   //    return LogMMError(nRet, __LINE__); 

   // setup imaging
   if (!pl_exp_init_seq())
      return LogCamError(__LINE__, "");

   /**
    * TODO: This does not seem to be used for anything.  Delete?
   // check for circular buffer support
   rs_bool availFlag;
   noSupportForStreaming_ = 
      (!PlGetParamSafe(hPVCAM_, PARAM_CIRC_BUFFER, ATTR_AVAIL, &availFlag) || !availFlag);
   */

   // setup the buffer
   // ----------------
   nRet = ResizeImageBufferSingle();
   if(nRet != DEVICE_OK) 
       return LogMMError(nRet, __LINE__); 

   initialized_ = true;
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Function name   : &Universal::Shutdown
// Description     : Deactivate the camera, reverse the initialization process
// Return type     : bool 


int Universal::Shutdown()
{
   if (initialized_)
   {
      rs_bool ret = pl_exp_uninit_seq();
      if (!ret)
         LogCamError(__LINE__, "");
      assert(ret);
      ret = pl_cam_close(hPVCAM_);
      if (!ret)
         LogCamError(__LINE__, "");
      assert(ret);	  
      refCount_--;      
      if (PVCAM_initialized_ && refCount_ == 0)      
      {         
         if (!pl_pvcam_uninit())
            LogCamError(__LINE__, "");
         PVCAM_initialized_ = false;
      }      
      initialized_ = false;
   }
   return DEVICE_OK;
}

bool Universal::Busy()
{
   return false;
}


/**
 * Function name   : &Universal::SnapImage
 * Description     : Acquires a single frame and stores it in the internal
 *                   buffer.
 *                   This command blocks the calling thread
 *                   until the image is fully captured.
 * Return type     : int 
 * Timing data     : On an Intel Mac Pro OS X 10.5 with CoolsnapEZ, 0 ms exposure
 *                   pl_exp_start_seq takes 28 msec
 *                   WaitForExposureDone takes 25 ms, for a total of 54 msec
 */                   
int Universal::SnapImage()
{
   MM::MMTime start = GetCurrentMMTime();

   int nRet = DEVICE_ERR;

   if(snappingSingleFrame_)
   {
      LogMessage("Warning: Entering SnapImage while GetImage has not been done for previous frame", true);
   }

   if (!bufferOK_) 
      return LogMMError(ERR_INVALID_BUFFER, __LINE__);

   if(!singleFrameModeReady_)
   {
      LogMessage("Resizing ImageBufferSingle in SnapImage", true);
      if (!pl_exp_stop_cont(hPVCAM_, CCS_HALT))
         LogCamError(__LINE__, "");
      if (!pl_exp_finish_seq(hPVCAM_, circBuffer_, 0))
         LogCamError(__LINE__, "");
      nRet = ResizeImageBufferSingle();
      if (nRet != DEVICE_OK) 
         return LogMMError(nRet, __LINE__);
   }

   // Make sure the camera is ready to start an exposure
   int16 status;
   uns32 not_needed;
   if (snappingSingleFrame_) { // This is needed to deal with users calling snapImage twice in a row
      LogMessage ("Waiting in SnapImage for previous exposure to clear", true);
      while(pl_exp_check_status(hPVCAM_, &status, &not_needed) && 
         (status != READOUT_COMPLETE && status != READOUT_FAILED && status != READOUT_NOT_ACTIVE) );
   }

   void* pixBuffer = const_cast<unsigned char*> (img_.GetPixels());

   if (!pl_exp_start_seq(hPVCAM_, pixBuffer))
      return LogCamError(__LINE__);

   snappingSingleFrame_=true;

   if(WaitForExposureDone())
   { 
      nRet = DEVICE_OK;
   }
   else
   {
      //Exposure was not done correctly. if application nevertheless 
      //tries to get (wrong) image by calling GetImage, the error will be reported
      snappingSingleFrame_=false;
   }

   MM::MMTime end = GetCurrentMMTime();

   LogTimeDiff(start, end, "Exposure took: ", true);

   return nRet;
}

bool Universal::WaitForExposureDone()throw()
{
   MM::MMTime startTime = GetCurrentMMTime();
   bool bRet=false;
   rs_bool rsbRet=0;

   const double exposure_time_safe_gap=50.0; //milliseconds 
   double exposure_time_safe = (exposure_ + exposure_time_safe_gap) * 1000.0;

   try
   {
      int16 status;
      uns32 not_needed;

      if(use_pl_exp_check_status_)
      {
         //Check if "pl_exp_check_status" can really be used with current camera HW
         do {
            rsbRet = pl_exp_check_status(hPVCAM_, &status, &not_needed);
         } while (rsbRet && (status == EXPOSURE_IN_PROGRESS));
         if (!rsbRet)
            LogCamError(__LINE__, "");

         MM::MMTime actual_interval = GetCurrentMMTime() - startTime;
         if( actual_interval.getUsec() < exposure_ * 1000.0 )
         {
            // As we are here we could not get the camera status correctly 
            // for whatever reason
            use_pl_exp_check_status_=false;
            ostringstream os;
            os<<"PVCAM pl_exp_check_status(): time: " << actual_interval.getUsec();
            os<<"status: "<<status<<"\n";
            LogMessage(os.str(), false);
         }
      }
      // use_pl_exp_check_status_ can be set to false during the previous step
      if(!use_pl_exp_check_status_)
      {
         do 
         {
            CDeviceUtils::SleepMs(2);
         } while ( (GetCurrentMMTime() - startTime).getUsec() < exposure_time_safe );
      }
      bRet=true;
   }
   catch(...)
   {
      LogMMMessage(__LINE__, "Unknown exception while waiting for exposure to finish", false);
   }
   return bRet;
}




const unsigned char* Universal::GetImageBuffer()
{  
   int16 status;
   uns32 not_needed;



   if(!snappingSingleFrame_)
   {
      LogMMMessage(__LINE__, "Warning: GetImageBuffer called before SnapImage()");
      return 0;
   }

   // wait for data or error
   void* pixBuffer = const_cast<unsigned char*> (img_.GetPixels());
   MM::MMTime start = GetCurrentMMTime();
   MM::MMTime maxDuration = (GetExposure() + 15000) * 1000;
//   float notscan = GetReadoutTime();
 //  float exptime = GetExposure();
  // MM::MMTime maxDuration = (GetExposure() + GetReadoutTime() + 2000 ) * 1000;
	bool timeout = false;
   // Check status, timeout when this takes exposure time + 5 seconds
   while(pl_exp_check_status(hPVCAM_, &status, &not_needed) && 
      (status != READOUT_COMPLETE && status != READOUT_FAILED) && !timeout) {
      if ((GetCurrentMMTime() - start) > maxDuration)
         timeout = true;
   }
   MM::MMTime end = GetCurrentMMTime();
   // Log duration of readout in debug mode
   LogTimeDiff(start, end, "Readout took: ", true);


   // Error handling
   if (timeout)
   {
      LogMMMessage(__LINE__, "Timeout in GetImageBuffer", false);
      return 0;
   } 
   if (status == READOUT_FAILED)
   {
      LogMMMessage(__LINE__, "GetImageBuffer: status == READOUT_FAILED", false);
      return 0;
   } 
   if (status != READOUT_COMPLETE)
   {
      LogMMMessage(__LINE__, "GetImageBuffer: status != READOUT_COMPLETE", false);
      return 0;
   } 

   snappingSingleFrame_=false;

   return (unsigned char*) pixBuffer;
}


double Universal::GetExposure() const
{
   char buf[MM::MaxStrLength];
   buf[0] = '\0';
   GetProperty(MM::g_Keyword_Exposure, buf);
   return atof(buf);
}

double Universal::GetReadoutTime() const
{
   char buf[MM::MaxStrLength];
   buf[0] = '\0';
   GetProperty(MM::g_Keyword_ReadoutTime, buf);
   return atof(buf);
}

void Universal::SetExposure(double exp)
{
   int ret = SetProperty(MM::g_Keyword_Exposure, CDeviceUtils::ConvertToString(exp));
   if (ret != DEVICE_OK)
      LogMMError(ret, __LINE__);
}

/**
* Returns the raw image buffer.
*/
unsigned Universal::GetBitDepth() const
{
   return (unsigned) bitDepth_;
}


int Universal::SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize)
{
   if (IsCapturing())
      return ERR_BUSY_ACQUIRING;

   // ROI internal dimensions are in no-binning mode, so we need to convert

   roi_.x = (uns16) (x * binSize_);
   roi_.y = (uns16) (y * binSize_);
   roi_.xSize = (uns16) (xSize * binSize_);
   roi_.ySize = (uns16) (ySize * binSize_);

   return ResizeImageBufferSingle();
}

int Universal::GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize)
{
   // ROI internal dimensions are in no-binning mode, so we need to convert

   x = roi_.x / binSize_;
   y = roi_.y / binSize_;
   xSize = roi_.xSize / binSize_;
   ySize = roi_.ySize / binSize_;

   return DEVICE_OK;
}

int Universal::ClearROI()
{
   if (IsCapturing())
      return ERR_BUSY_ACQUIRING;

   roi_.x = 0;
   roi_.y = 0;
   roi_.xSize = 0;
   roi_.ySize = 0;

   return ResizeImageBufferSingle();
}

bool Universal::GetErrorText(int errorCode, char* text) const
{
   if (CCameraBase<Universal>::GetErrorText(errorCode, text))
      return true; // base message

   char buf[ERROR_MSG_LEN];
   if (pl_error_message ((int16)errorCode, buf))
   {
      CDeviceUtils::CopyLimitedString(text, buf);
      return true;
   }
   else
      return false;
}

///////////////////////////////////////////////////////////////////////////////
// Utility methods
///////////////////////////////////////////////////////////////////////////////

int Universal::SetAllowedPixelTypes() 
{
   int16 bitDepth, minBitDepth, maxBitDepth, bitDepthIncrement;
   if (!PlGetParamSafe( hPVCAM_, PARAM_BIT_DEPTH, ATTR_CURRENT, &bitDepth))
      LogCamError(__LINE__, "");
   if (! PlGetParamSafe( hPVCAM_, PARAM_BIT_DEPTH, ATTR_INCREMENT, &bitDepthIncrement))
      LogCamError(__LINE__, "");
   if (!PlGetParamSafe( hPVCAM_, PARAM_BIT_DEPTH, ATTR_MAX, &maxBitDepth))
      LogCamError(__LINE__, "");
   if (!PlGetParamSafe( hPVCAM_, PARAM_BIT_DEPTH, ATTR_MIN, &minBitDepth))
      LogCamError(__LINE__, "");

   ostringstream os;
   os << "Pixel Type: " << bitDepth << " " << bitDepthIncrement << " " << minBitDepth << " " << maxBitDepth;
   LogMessage(os.str().c_str(), true);

   vector<string> pixelTypeValues;
   // These PVCAM folks can give some weird answers now and then:
   if (maxBitDepth < minBitDepth)
      maxBitDepth = minBitDepth;
   if (bitDepthIncrement == 0)
      bitDepthIncrement = 2;
   for (int i = minBitDepth; i <= maxBitDepth; i += bitDepthIncrement) {
      switch (i) {
         case (8) : pixelTypeValues.push_back(g_PixelType_8bit);
            break;
         case (10) : pixelTypeValues.push_back(g_PixelType_10bit);
            break;
         case (12) : pixelTypeValues.push_back(g_PixelType_12bit);
            break;
         case (14) : pixelTypeValues.push_back(g_PixelType_14bit);
            break;
         case (16) : pixelTypeValues.push_back(g_PixelType_16bit);
            break;
      }
   }
   int nRet = SetAllowedValues(MM::g_Keyword_PixelType, pixelTypeValues);
   if (nRet != DEVICE_OK)
      LogMMError(nRet, __LINE__);

   return nRet;
}

int Universal::SetGainLimits()
{
   if (!gainAvailable_)
      return DEVICE_OK;

   int nRet = DEVICE_OK;
   int16 gainMin, gainMax;

   if (!PlGetParamSafe( hPVCAM_, PARAM_GAIN_INDEX, ATTR_MIN, &gainMin))
      LogCamError(__LINE__, "");
   if (!PlGetParamSafe( hPVCAM_, PARAM_GAIN_INDEX, ATTR_MAX, &gainMax))
      LogCamError(__LINE__, "");

   ostringstream s;
   s << "Gain " << " " << gainMin << " " << gainMax;
   LogMessage(s.str().c_str(), true);

   if ((gainMax - gainMin) < 10) {
      vector<std::string> values;
      for (int16 index = gainMin; index <= gainMax; index++) {
         ostringstream os;
         os << index;
         values.push_back(os.str());
      }
      nRet = SetAllowedValues(MM::g_Keyword_Gain, values);
   } else
      nRet = SetPropertyLimits(MM::g_Keyword_Gain, (int) gainMin, (int) gainMax);

   if (nRet != DEVICE_OK)
      LogMMError(nRet, __LINE__);

   return nRet;
}

int Universal::SetUniversalAllowedValues(int i, uns16 dataType)
{
   switch (dataType) { 
      case TYPE_INT8 : {
         int8 min, max, index;
         if (PlGetParamSafe(hPVCAM_, param_set[i].id, ATTR_MIN, (void *) &min) && PlGetParamSafe(hPVCAM_, param_set[i].id, ATTR_MAX, (void*) &max ) )  
         {
            if ((max - min) > 10) {
               SetPropertyLimits(param_set[i].name, (double) min, (double) max);
            } else {
               vector<std::string> values;
               for (index = min; index <= max; index++) {
                  ostringstream os;
                  os << index;
                  values.push_back(os.str());
               }
               SetAllowedValues(param_set[i].name, values);
            }
         } else
         {
            LogCamError(__LINE__, "");
         }
         break;
                       }
      case TYPE_UNS8 : {
         uns8 min, max, index;
         if (PlGetParamSafe(hPVCAM_, param_set[i].id, ATTR_MIN, (void *) &min) && PlGetParamSafe(hPVCAM_, param_set[i].id, ATTR_MAX, (void*) &max ) )  {
            if ((max - min) > 10) {
               SetPropertyLimits(param_set[i].name, (double) min, (double) max);
            } else {
               vector<std::string> values;
               for (index = min; index <= max; index++) {
                  ostringstream os;
                  os << index;
                  values.push_back(os.str());
               }
               SetAllowedValues(param_set[i].name, values);
            }
         } else
         {
            LogCamError(__LINE__, "");
         }
         break;
                       }
      case TYPE_INT16 : {
         int16 min, max, index;
         if (PlGetParamSafe(hPVCAM_, param_set[i].id, ATTR_MIN, (void *) &min) && PlGetParamSafe(hPVCAM_, param_set[i].id, ATTR_MAX, (void*) &max ) )  {
            if ((max - min) > 10) {
               SetPropertyLimits(param_set[i].name, (double) min, (double) max);
            } else if ((max-min) < 1000000) {
               vector<std::string> values;
               for (index = min; index <= max; index++) {
                  ostringstream os;
                  os << index;
                  values.push_back(os.str());
               }
               SetAllowedValues(param_set[i].name, values);
            }
         } else
         {
            LogCamError(__LINE__, "");
         }
         break;
                        }
      case TYPE_UNS16 : {
         uns16 min, max, index;
         if (PlGetParamSafe(hPVCAM_, param_set[i].id, ATTR_MIN, (void *) &min) && PlGetParamSafe(hPVCAM_, param_set[i].id, ATTR_MAX, (void*) &max ) )  {
            if ((max - min) > 10) {
               SetPropertyLimits(param_set[i].name, (double) min, (double) max);
            } else if ((max-min) < 1000000) {
               vector<std::string> values;
               for (index = min; index <= max; index++) {
                  ostringstream os;
                  os << index;
                  values.push_back(os.str());
               }
               SetAllowedValues(param_set[i].name, values);
            }
         } else
         {
            LogCamError(__LINE__, "");
         }
         break;
                        }
      case TYPE_INT32 : {
         int32 min, max, index;
         if (PlGetParamSafe(hPVCAM_, param_set[i].id, ATTR_MIN, (void *) &min) && PlGetParamSafe(hPVCAM_, param_set[i].id, ATTR_MAX, (void*) &max ) )  {
            if ((max - min) > 10) {
               SetPropertyLimits(param_set[i].name, (double) min, (double) max);
            } else if ((max-min) < 1000000) {
               vector<std::string> values;
               for (index = min; index <= max; index++) {
                  ostringstream os;
                  os << index;
                  values.push_back(os.str());
               }
               SetAllowedValues(param_set[i].name, values);
            }
         } else
         {
            LogCamError(__LINE__, "");
         }
         break;
                        }
      case TYPE_UNS32 : {
         uns32 min, max, index;
         if (PlGetParamSafe(hPVCAM_, param_set[i].id, ATTR_MIN, (void *) &min) && PlGetParamSafe(hPVCAM_, param_set[i].id, ATTR_MAX, (void*) &max ) )  {
            if ((max - min) > 10) {
               SetPropertyLimits(param_set[i].name, (double) min, (double) max);
            } else if ((max-min) < 1000000) {
               vector<std::string> values;
               for (index = min; index <= max; index++) {
                  ostringstream os;
                  os << index;
                  values.push_back(os.str());
               }
               SetAllowedValues(param_set[i].name, values);
            }
         } else
         {
            LogCamError(__LINE__, "");
         }
         break;
                        }
   }
   return DEVICE_OK;
}

int Universal::GetSpeedString(std::string& modeString)
{
   stringstream tmp;

   // read speed table setting from camera:
   int16 spdTableIndex;
   if (!PlGetParamSafe(hPVCAM_, PARAM_SPDTAB_INDEX, ATTR_CURRENT, &spdTableIndex))
      return LogCamError(__LINE__, "");

   // read pixel time:
   long pixelTime;
   if (!GetLongParam_PvCam_safe(hPVCAM_, PARAM_PIX_TIME, &pixelTime))
      return LogCamError(__LINE__, "");

   // read bit depth
   long bitDepth;
   if (!GetLongParam_PvCam_safe(hPVCAM_, PARAM_BIT_DEPTH, &bitDepth))
      return LogCamError(__LINE__, "");

   double rate;
   if( pixelTime > 9999 ) {
	  rate = 1000000.0/pixelTime; // in kHz
      tmp << rate << " kHz " << bitDepth << "bit";
   } else {
      rate = 1000.0/pixelTime; // in MHz
      tmp << rate << " MHz " << bitDepth << "bit";
   }
   modeString = tmp.str();
   return DEVICE_OK;
}

/*
* Sets allowed values for entry "Speed"
* Also sets the Map rateMap_
*/
int Universal::GetSpeedTable()
{
   int nRet=DEVICE_ERR;
   int16 spdTableCount;
   if (!PlGetParamSafe(hPVCAM_, PARAM_SPDTAB_INDEX, ATTR_MAX, &spdTableCount))
      return LogCamError(__LINE__, "");

   //Speed Table Index is 0 based.  We got the max number but want the total count
   spdTableCount +=1;
   ostringstream os;
   os << "SpeedTableCountd: " << spdTableCount;

   // log the current settings, so that we can revert to it after cycling through the options
   int16 spdTableIndex;
   rs_bool pvcamRet = PlGetParamSafe(hPVCAM_, PARAM_SPDTAB_INDEX, ATTR_CURRENT, &spdTableIndex);
   if (!pvcamRet)
      return LogCamError(__LINE__, "");

   // cycle through the speed table entries and record the associated settings
   vector<string> speedValues;
   rateMap_.clear();
   string speedString;
   for (int16 i=0; i<spdTableCount; i++) 
   {
      stringstream tmp;
      if (!PlSetParamSafe(hPVCAM_, PARAM_SPDTAB_INDEX, &i))
        LogCamError(__LINE__, "");
      nRet = GetSpeedString(speedString);
      if(nRet != DEVICE_OK) 
          return LogMMError(nRet, __LINE__); 

      os << "\n\t" << "Speed: " << speedString.c_str();
      speedValues.push_back(speedString);
      rateMap_[speedString] = i;
   }
   LogMessage (os.str().c_str(), true);

   // switch back to original setting
   if (!PlSetParamSafe(hPVCAM_, PARAM_SPDTAB_INDEX, &spdTableIndex))
      LogCamError(__LINE__, "");

   nRet = SetAllowedValues(g_ReadoutRate, speedValues);
   if(nRet != DEVICE_OK) 
       return LogMMError(nRet, __LINE__); 

   return nRet;
}


std::string Universal::GetPortNameAndEnum(long portId, long& enumValue)
{
   // Work around bug in PVCAM:
   if (nrPorts_ == 1) 
      return "Normal";

   enumValue = -1;
   string portName;
   int32 enumIndex;
   if (!GetEnumParam_PvCam((uns32)PARAM_READOUT_PORT, (uns32)portId, portName, enumIndex))
   {
      LogMessage("Error in GetEnumParam in GetPortName");
   }
   else
   {
      enumValue = enumIndex;
      switch (enumIndex)
      {
      case 0: 
	      portName = "EM"; 
	      break;
      case 1: 
	      portName = "Normal"; 
	      break;
      case 2: 
	      portName = "LowNoise"; 
	      break;
      case 3:
	      portName = "HighCap"; 
	      break;
      default: 
	      enumValue = 1;
	      portName = "Normal";
      }
   }
   return portName;
}

bool Universal::GetEnumParam_PvCam(uns32 pvcam_cmd, uns32 index, std::string& enumString, int32& enumIndex)
{
   // First check this is an enumerated type:
   uint16_t dataType;
   if (!PlGetParamSafe(hPVCAM_, pvcam_cmd, ATTR_TYPE, &dataType)) 
   {
      LogCamError(__LINE__, "");
      return false;
   }

   if (dataType != TYPE_ENUM)
      return false;

   long unsigned int strLength;
   if (!PlEnumStrLengthSafe(hPVCAM_, pvcam_cmd, index, &strLength))
   {
      LogCamError(__LINE__, "");
      return false;
   }

   char* strTmp;
   strTmp = (char*) malloc(strLength);
   int32 tmp;

   if (!PlGetEnumParamSafe(hPVCAM_, pvcam_cmd, index, &tmp, strTmp, strLength))
   {
      LogCamError(__LINE__, "");
      return false;
   }

   enumIndex = tmp;
   enumString = strTmp;
   free (strTmp);

   return true;
}

int Universal::CalculateImageBufferSize(ROI &newROI, unsigned short &newXSize, unsigned short &newYSize, rgn_type &newRegion)
{
   unsigned short xdim, ydim;

   if (!pl_ccd_get_par_size(hPVCAM_, &ydim))
      return LogCamError(__LINE__, "");

   if (!pl_ccd_get_ser_size(hPVCAM_, &xdim))
      return LogCamError(__LINE__, "");

   if (newROI.isEmpty())
   {
      // set to full frame
      newROI.xSize = xdim;
      newROI.ySize = ydim;
   }

   // NOTE: roi dimensions are expressed in no-binning mode
   // format the image buffer
   // (assuming 2-byte pixels)
   newXSize = (unsigned short) (newROI.xSize/binSize_);
   newYSize = (unsigned short) (newROI.ySize/binSize_);

   // make an attempt to adjust the image dimensions
   while ((newXSize * newYSize * 2) % 4 != 0 && newXSize > 4 && newYSize > 4)
   {
      newROI.xSize--;
      newXSize = (unsigned short) (newROI.xSize/binSize_);
      if ((newXSize * newYSize * 2) % 4 != 0)
      {
         newROI.ySize--;
         newYSize = (unsigned short) (newROI.ySize/binSize_);
      }
   }

   newRegion.s1 = newROI.x;
   newRegion.s2 = newROI.x + newROI.xSize-1;
   newRegion.sbin = (uns16) binSize_;
   newRegion.p1 = newROI.y;
   newRegion.p2 = newROI.y + newROI.ySize-1;
   newRegion.pbin = (uns16) binSize_;

   return DEVICE_OK;
}


int Universal::ResizeImageBufferContinuous()
{
   //ToDo: use semaphore
   bufferOK_ = false;
   int nRet = DEVICE_ERR;

   ROI            newROI = roi_;
   unsigned short newXSize;
   unsigned short newYSize;
   rgn_type       newRegion;

   try
   {
      nRet = CalculateImageBufferSize(newROI, newXSize, newYSize, newRegion);
      if(nRet != DEVICE_OK) 
          return LogMMError(nRet, __LINE__); 

      img_.Resize(newXSize, newYSize, 2);

      uns32 frameSize;
      char trigMode[MM::MaxStrLength];
      nRet = GetProperty(g_TriggerMode, trigMode);
      long trigModeValue = (long)TIMED_MODE;
      if (nRet == DEVICE_OK)
      {
         GetPropertyData(g_TriggerMode, trigMode, trigModeValue);
      }

      if (!pl_exp_setup_cont(hPVCAM_, 1, &newRegion, (int16)trigModeValue, (uns32)exposure_, &frameSize, CIRC_OVERWRITE)) 
         return LogCamError(__LINE__, "");

      if (!pl_exp_set_cont_mode(hPVCAM_, CIRC_OVERWRITE ))
         return LogCamError(__LINE__, "");

      if (img_.Height() * img_.Width() * img_.Depth() != frameSize)
      {
         return DEVICE_INTERNAL_INCONSISTENCY; // buffer sizes don't match ???
      }

      // set up a circular buffer for 3 frames
      bufferSize_ = frameSize * 3;
      delete[] circBuffer_;
      circBuffer_ = new unsigned short[bufferSize_];
      roi_=newROI;
      nRet = DEVICE_OK;
   }
   catch(...)
   {
   }
   //ToDo: use semaphore
   bufferOK_ = true;
   singleFrameModeReady_=false;

   return nRet;

}

/**
 * This function calls pl_exp_seq_seq with the correct parameters, to set the camera
 * in a mode in which single images can be taken
 *
 * Timing data:      On a Mac Pro OS X 10.5 with CoolsnapEZ, this functions takes
 *                   takes 245 msec.
 */
int Universal::ResizeImageBufferSingle()
{
   LogMessage("Resizing image Buffer Single", true);
   //ToDo: use semaphore
   bufferOK_ = false;
   int nRet;

   ROI            newROI = roi_;
   unsigned short newXSize;
   unsigned short newYSize;
   rgn_type       newRegion;

   try
   {
      nRet = CalculateImageBufferSize(newROI, newXSize, newYSize, newRegion);
      if(nRet != DEVICE_OK) 
          return LogMMError(nRet, __LINE__); 

      img_.Resize(newXSize, newYSize, 2);

      uns32 frameSize;
      char trigMode[MM::MaxStrLength];
      nRet = GetProperty(g_TriggerMode, trigMode);
      long trigModeValue = (long)TIMED_MODE;
      if (nRet == DEVICE_OK)
      {
         GetPropertyData(g_TriggerMode, trigMode, trigModeValue);
      }

      if (!pl_exp_setup_seq(hPVCAM_, 1, 1, &newRegion, (int16)trigModeValue, (uns32)exposure_, &frameSize ))
         return LogCamError(__LINE__, "");

      if (img_.Height() * img_.Width() * img_.Depth() != frameSize) {
         return LogMMError(DEVICE_INTERNAL_INCONSISTENCY, __LINE__); // buffer sizes don't match ???
      }

      roi_=newROI;
      singleFrameModeReady_=true;
      LogMessage("Set singleFrameModeReady_ to true", true);
   }
   catch(...)
   {
      LogMessage("Caught error in ResizeImageBufferSingle", false);
   }
   //ToDo: use semaphore
   bufferOK_ = true;
   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// Continuous acquisition
//

#ifndef linux
/*
* Overrides a virtual function from the CCameraBase class
* Do actual capture
* Called from the acquisition thread function
*/
int Universal::ThreadRun(void)
{
   int16 status;
   uns32 byteCnt;
   uns32 bufferCnt;

   int ret=DEVICE_ERR;

   std::ostringstream txt;
   txt << name_ << " entered ThreadRun()";
   LogMMMessage(__LINE__,  txt.str(), true);

   // wait until image is ready
   MM::MMTime timeout((long)(2.5 + GetExposure()*0.001), 0);
   MM::MMTime startTime = GetCurrentMMTime();
   MM::MMTime elapsed;
   bool timedOut = false;
   rs_bool retVal = FALSE;

   retVal = pl_exp_check_cont_status(hPVCAM_, &status, &byteCnt, &bufferCnt);

   while (
      retVal
      && (status != READOUT_COMPLETE) 
      && (status != READOUT_FAILED)
      && !timedOut)
   {
      MMThreadGuard guard(g_pvcamLock);
      retVal = pl_exp_check_cont_status(hPVCAM_, &status, &byteCnt, &bufferCnt);

      std::ostringstream msg;
      msg << "Waiting with status " << (int)status;
      LogMMMessage(__LINE__, msg.str(), true);

      CDeviceUtils::SleepMs(10);
      elapsed = GetCurrentMMTime() - startTime;
      if (elapsed > timeout) {
         timedOut = true;
         LogMMMessage(__LINE__, "timed out");
      }
      if (!retVal)
         LogMMMessage(__LINE__, std::string("pl_exp_check_cont_status() failed!"));
   }

   if (status != READOUT_FAILED && !timedOut && retVal)
   {
      ret = PushImage();
   }
   else
   {
      LogMMMessage(__LINE__, "PVCamera readout failed");
   }

   ostringstream txtEnd;
   txtEnd << name_ << " exited ThreadRun()";
   LogMMMessage(__LINE__, txtEnd.str(), true);
   return ret;
}

int Universal::PrepareSequenceAcqusition()
{
   if (IsCapturing())
      return ERR_BUSY_ACQUIRING;

   sequenceModeReady_ = false;

   // prepare the camera
   int nRet = ResizeImageBufferContinuous();
   if (nRet != DEVICE_OK) 
      return LogMMError(nRet, __LINE__);

   // start thread
   // prepare the core
   nRet = GetCoreCallback()->PrepareForAcq(this);
   if (nRet != DEVICE_OK)
   {
      ResizeImageBufferSingle();
      return LogMMError(nRet, __LINE__);
   }

   sequenceModeReady_ = true;
   return DEVICE_OK;
}

/**
 * Starts continuous acquisition.
 */
int Universal::StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow)
{
   if (!sequenceModeReady_)
   {
      int ret = PrepareSequenceAcqusition();
      if (ret != DEVICE_OK)
         return ret;
   }

   stopOnOverflow_ = stopOnOverflow;
   numImages_ = numImages;

   MM::MMTime start = GetCurrentMMTime();
   if (!pl_exp_start_cont(hPVCAM_, circBuffer_, bufferSize_))
   {
      int pvcamErr = LogCamError(__LINE__, "");
      ResizeImageBufferSingle();
      return pvcamErr;
   }
   startTime_ = GetCurrentMMTime();
   imageCounter_ = 0;

   MM::MMTime end = GetCurrentMMTime();
   LogTimeDiff(start, end, true);

   // initially start with the exposure time as the actual interval estimate
   SetProperty(MM::g_Keyword_ActualInterval_ms, CDeviceUtils::ConvertToString(exposure_)); 

   thd_->Start(numImages, interval_ms);

   char label[MM::MaxStrLength];
   GetLabel(label);
   ostringstream os;
   os << "Started sequence on " << label << ", at " << startTime_.serialize() << ", with " << numImages << " and " << interval_ms << " ms" << endl;
   LogMessage(os.str().c_str());

   return DEVICE_OK;
}


void Universal::OnThreadExiting() throw ()
{
   try {
      if (!pl_exp_stop_cont(hPVCAM_, CCS_HALT)) 
         LogCamError(__LINE__, "");
      if (!pl_exp_finish_seq(hPVCAM_, circBuffer_, 0))
         LogCamError(__LINE__, "");
   } catch (...) {
      LogMMMessage(__LINE__, g_Msg_EXCEPTION_IN_ON_THREAD_EXITING);
   }
   sequenceModeReady_ = false;
   CCameraBase<Universal>::OnThreadExiting();
}

/**
* Stops acquisition
*/
int Universal::StopSequenceAcquisition()
{
   // call function of the base class, which does useful work
   int nRet = this->CCameraBase<Universal>::StopSequenceAcquisition();
   if (nRet != DEVICE_OK) 
      return LogMMError(nRet, __LINE__);

   if (!pl_exp_stop_cont(hPVCAM_, CCS_HALT)) 
      LogCamError(__LINE__, "");
   if (!pl_exp_finish_seq(hPVCAM_, circBuffer_, 0))
      LogCamError(__LINE__, "");

   nRet = ResizeImageBufferSingle();
   if (nRet != DEVICE_OK)
      return LogMMError(nRet, __LINE__);

   return nRet;
}
#endif

/**
* Waits for new image and inserts it in the circular buffer.
* This method is called by the acquisition thread AcqSequenceThread::svc()
* in an infinite loop.
*
* In case of error or if the sequence is finished StopSequenceAcquisition()
* is called, which will raise the stop_ flag and cause the thread to exit.
*/
int Universal::PushImage()
{
   ostringstream txt;
   txt << name_ << " entered PushImage()";
   LogMessage(txt.str(), true);

   int nRet = DEVICE_ERR;
   // get the image from the circular buffer
   void_ptr imgPtr;
   bool oldest_frames_get_mode=false;/*=!noSupportForStreaming_*/
   rs_bool result = oldest_frames_get_mode
      ?pl_exp_get_oldest_frame(hPVCAM_, &imgPtr) 
      :pl_exp_get_latest_frame(hPVCAM_, &imgPtr); 
   if (!result)
      return LogCamError(__LINE__);

   if(oldest_frames_get_mode)
   {
      result = pl_exp_unlock_oldest_frame(hPVCAM_); 
      if (!result)
         return LogCamError(__LINE__);
   }

   void* pixBuffer = const_cast<unsigned char*> (img_.GetPixels());
   memcpy(pixBuffer, imgPtr, GetImageBufferSize());

   // process image
   MM::ImageProcessor* ip = GetCoreCallback()->GetImageProcessor(this);
   if (ip)
   {
      nRet = ip->Process((unsigned char*) pixBuffer, GetImageWidth(), GetImageHeight(), GetImageBytesPerPixel());
      if (nRet != DEVICE_OK) 
         return LogMMError(nRet, __LINE__);
   }

   // create metadata
   char label[MM::MaxStrLength];
   GetLabel(label);

   MM::MMTime timestamp = GetCurrentMMTime();
   Metadata md;

   MetadataSingleTag mstStartTime(MM::g_Keyword_Metadata_StartTime, label, true);
	mstStartTime.SetValue(CDeviceUtils::ConvertToString(startTime_.getMsec()));
   md.SetTag(mstStartTime);

   MetadataSingleTag mstElapsed(MM::g_Keyword_Elapsed_Time_ms, label, true);
   MM::MMTime elapsed = timestamp - startTime_;
   mstElapsed.SetValue(CDeviceUtils::ConvertToString(elapsed.getMsec()));
   md.SetTag(mstElapsed);

   MetadataSingleTag mstCount(MM::g_Keyword_Metadata_ImageNumber, label, true);
   mstCount.SetValue(CDeviceUtils::ConvertToString(imageCounter_++));
   md.SetTag(mstCount);

   double actualInterval = elapsed.getMsec() / imageCounter_;
   SetProperty(MM::g_Keyword_ActualInterval_ms, CDeviceUtils::ConvertToString(actualInterval)); 

   // This method inserts a new image into the circular buffer (residing in MMCore)
   nRet = GetCoreCallback()->InsertMultiChannel(this,
      (unsigned char*) pixBuffer,
      1,
      GetImageWidth(),
      GetImageHeight(),
      GetImageBytesPerPixel(),
      &md);

   if (!stopOnOverflow_ && nRet == DEVICE_BUFFER_OVERFLOW)
   {
      // do not stop on overflow - just reset the buffer
      GetCoreCallback()->ClearImageBuffer(this);
      nRet = GetCoreCallback()->InsertMultiChannel(this,
         (unsigned char*) pixBuffer,
         1,
         GetImageWidth(),
         GetImageHeight(),
         GetImageBytesPerPixel(),
         &md);
   }

   ostringstream txtEnd;
   txtEnd << name_ << " exited PushImage()";
   LogMessage(txtEnd.str(), true);
   return nRet;
}



rs_bool Universal::PlGetParamSafe(int16 hcam, uns32 param_id,
                                     int16 param_attribute, void_ptr param_value)
{
   rs_bool ret;
   SuspendSequence();
   ret = pl_get_param (hcam, param_id, param_attribute, param_value);
   ResumeSequence();
   return ret;
}


rs_bool Universal::PlSetParamSafe(int16 hcam, uns32 param_id, void_ptr param_value)
{
   rs_bool ret;
   SuspendSequence();

   ret = pl_set_param (hcam, param_id, param_value);
   ResumeSequence();
   return ret;
}


bool Universal::GetLongParam_PvCam_safe(int16 handle, uns32 pvcam_cmd, long *value) 
{
   bool ret;
   SuspendSequence();
   ret = GetLongParam_PvCam(handle, pvcam_cmd, value);
   ResumeSequence();
   return ret;
}

bool Universal::SetLongParam_PvCam_safe(int16 handle, uns32 pvcam_cmd, long value) 
{
   bool ret;
   SuspendSequence();
   ret = SetLongParam_PvCam(handle, pvcam_cmd, value);
   ResumeSequence();
   return ret;
}


rs_bool Universal::PlGetEnumParamSafe (int16 hcam, uns32 param_id, uns32 index,
                                           int32_ptr value, char_ptr desc,
                                           uns32 length)
{
   rs_bool ret;
   SuspendSequence();
   ret = pl_get_enum_param(hcam, param_id, index, value, desc, length);
   ResumeSequence();
   return ret;
}

rs_bool Universal::PlEnumStrLengthSafe (int16 hcam, uns32 param_id, uns32 index,
                                            uns32_ptr length)
{
   rs_bool ret;
   SuspendSequence();
   ret = pl_enum_str_length(hcam, param_id, index, length);
   ResumeSequence();
   return ret;
}


int16 Universal::LogCamError(int lineNr, std::string message, bool debug) throw()
{
   int16 nErrCode = pl_error_code();
   try
   {
      char msg[ERROR_MSG_LEN];
      if(!pl_error_message (nErrCode, msg))
      {
         CDeviceUtils::CopyLimitedString(msg, "Unknown");
      }
      ostringstream os;
      os << "PVCAM API error: \""<< msg <<"\", code: " << nErrCode << "\n";
      os << "In file: " << __FILE__ << ", " << "line: " << lineNr << ", " << message; 
      LogMessage(os.str(), debug);
      SetErrorText(nErrCode, msg);
   }
   catch(...){}

   return nErrCode;
}

int Universal::LogMMError(int errCode, int lineNr, std::string message, bool debug) const throw()
{
   try
   {
      char strText[MM::MaxStrLength];
      if (!CCameraBase<Universal>::GetErrorText(errCode, strText))
      {
         CDeviceUtils::CopyLimitedString(strText, "Unknown");
      }
      ostringstream os;
      os << "Error code "<< errCode << ": " <<  strText <<"\n";
      os << "In file: " << __FILE__ << ", " << "line: " << lineNr << ", " << message; 
      LogMessage(os.str(), debug);
   }
   catch(...) {}
   return errCode;
}

void Universal::LogMMMessage(int lineNr, std::string message, bool debug) const throw()
{
   try
   {
      ostringstream os;
      os << message << ", in file: " << __FILE__ << ", " << "line: " << lineNr; 
      LogMessage(os.str(), debug);
   }
   catch(...){}
}

/**************************** Post Processing Functions ******************************/
#ifdef WIN32

int Universal::OnActGainProperties(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	//SuspendSequence();

	//long temp = 0;
	//if (eAct == MM::AfterSet)
	//{
	//	pProp->Get(temp);
	//	if (!SetLongParam_PvCam(hPVCAM_, PARAM_ACTUAL_GAIN, temp))
	//		return pl_error_code();
	//}
	//else if (eAct == MM::BeforeGet)
	//{
	//	long temp;
	//	if (!GetLongParam_PvCam(hPVCAM_, PARAM_ACTUAL_GAIN, &temp))
	//		return pl_error_code();
	//	double val = temp;
	//	val = val/100;
	//	pProp->Set(val);
	//}
	//ResumeSequence();
	return DEVICE_OK;
}

//int Universal::OnReadNoiseProperties(MM::PropertyBase* pProp, MM::ActionType eAct)
//{
//   long temp = 0;
//	SuspendSequence();
//
//	if (eAct == MM::AfterSet)
//	{
//		pProp->Get(temp);
//		if (!SetLongParam_PvCam(hPVCAM_, PARAM_READ_NOISE, temp))
//			return pl_error_code();
//	}
//	else if (eAct == MM::BeforeGet)
//	{
//		if (!GetLongParam_PvCam(hPVCAM_, PARAM_READ_NOISE, &temp))
//			return pl_error_code();
//		double val = temp;
//		val = val/100;
//		pProp->Set(val);
//	}
//	ResumeSequence();
//	return DEVICE_OK;
//}

int Universal::OnSetBias(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	SuspendSequence();

	if (eAct == MM::AfterSet)
	{
	   long temp;
	   pProp->Get(temp);
      
	   if (!SetLongParam_PvCam_safe(hPVCAM_, PARAM_ADC_OFFSET, temp))
			return pl_error_code();
      
	}
	else if (eAct == MM::BeforeGet)
	{
	  long temp;
     
      if (!GetLongParam_PvCam_safe(hPVCAM_, PARAM_ADC_OFFSET, &temp))
         return pl_error_code();
      
	  pProp->Set(temp);
	}
	ResumeSequence();
   return DEVICE_OK;
}
#endif
