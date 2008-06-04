///////////////////////////////////////////////////////////////////////////////
// FILE:          Universal.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   PVCAM universal camera module
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
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 06/30/2006
//
// CVS:           $Id$

#ifdef WIN32
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#pragma warning(disable : 4996) // disable warning for deperecated CRT functions on Windows 
#endif

#include "../../MMDevice/ModuleInterface.h"
#include "PVCAM.h"
#include "PVCAMUtils.h"
#include "PVCAMProperty.h"


#ifdef WIN32
#include "../../../3rdparty/RoperScientific/Windows/PvCam/SDK/Headers/pvcam.h"
#include <stdint.h>
#else
#ifdef __APPLE__
#define __mac_os_x
#include <PVCAM/master.h>
#include <PVCAM/pvcam.h>
#endif
#endif

#include <string>
#include <sstream>
#include <iomanip>

using namespace std;

Universal* Universal::instance_ = 0;
unsigned Universal::refCount_ = 0;

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

SParam param_set[] = {
	//clear
	//{"ClearMode", PARAM_CLEAR_MODE},
   {"ClearCycles", PARAM_CLEAR_CYCLES},
	{"ContineousClears", PARAM_CONT_CLEARS},
	{"MinBlock", PARAM_MIN_BLOCK},
	{"NumBlock", PARAM_NUM_MIN_BLOCK},
	{"NumStripsPerClean", PARAM_NUM_OF_STRIPS_PER_CLR},
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
	{"LogicOutput", PARAM_LOGIC_OUTPUT}
};
int n_param = sizeof(param_set)/sizeof(SParam);

///////////////////////////////////////////////////////////////////////////////
// &Universal constructor/destructor
Universal::Universal(short cameraId) :
   initialized_(false),
   busy_(false),
   acquiring_(false),
   hPVCAM_(0),
   exposure_(0),
   binSize_(1),
   bufferOK_(false),
   cameraId_(cameraId),
   name_("Undefined"),
   nrPorts_ (1),
   circBuffer_(0),
   sequenceLength_(0),
   imageCounter_(0),
   init_seqStarted_(false)
{
   // ACE::init();
   InitializeDefaultErrorMessages();

   // add custom messages
   SetErrorText(ERR_CAMERA_NOT_FOUND, "No Camera Found. Is it connected and switched on?");
   SetErrorText(ERR_BUSY_ACQUIRING, "Acquisition already in progress.");

   // create burst thread
   seqThread_ = new AcqSequenceThread(this);
}


Universal::~Universal()
{   
   refCount_--;
   if (refCount_ <= 0)
   {
      // release resources
      if (initialized_)
         Shutdown();

      // clear the instance pointer
      instance_ = 0;      
      // ACE::fini();
      delete seqThread_;
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
      if (acquiring_)
         return ERR_BUSY_ACQUIRING;

      long bin;
      pProp->Get(bin);
      binSize_ = bin;
      ClearROI(); // reset region of interest
      int nRet = ResizeImageBufferSingle();
      if (nRet != DEVICE_OK)
         return nRet;
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
      if (!pl_get_param(hPVCAM_, PARAM_CHIP_NAME, ATTR_CURRENT, chipName))
         return pl_error_code();

      pProp->Set(chipName);
      chipName_  = chipName;
   }

   return DEVICE_OK;
}

int Universal::OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   // exposure property is stored in milliseconds,
   // while the driver returns the value in seconds
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(exposure_);
   }
   else if (eAct == MM::AfterSet)
   {
      if (acquiring_)
         return ERR_BUSY_ACQUIRING;

      double exp;
      pProp->Get(exp);
      exposure_ = exp;
      int ret = ResizeImageBufferSingle();
      if (ret != DEVICE_OK)
         return ret;
   }
   return DEVICE_OK;
}

int Universal::OnPixelType(MM::PropertyBase* pProp, MM::ActionType /*eAct*/)
{  
   int16 bitDepth;
   pl_get_param( hPVCAM_, PARAM_BIT_DEPTH, ATTR_CURRENT, &bitDepth);
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
   if (eAct == MM::AfterSet)
   {
      long gain;
      GetLongParam_PvCam(hPVCAM_, PARAM_GAIN_INDEX, &gain);

      string par;
      pProp->Get(par);
      long index;
      std::map<std::string, int>::iterator iter = rateMap_.find(par);
      if (iter != rateMap_.end())
         index = iter->second;
      else
         return ERR_INVALID_PARAMETER_VALUE;

      if (!SetLongParam_PvCam(hPVCAM_, PARAM_SPDTAB_INDEX, index))
         return pl_error_code();

      // Try setting the gain to original value, don't make a fuss when it fails
      SetLongParam_PvCam(hPVCAM_, PARAM_GAIN_INDEX, gain);
      if (!SetGainLimits())
         return pl_error_code();
      if (!SetAllowedPixelTypes())
         return pl_error_code();
      // update GUI to reflect these changes
      int ret = OnPropertiesChanged();
      if (ret != DEVICE_OK)
         return ret;
   }
   else if (eAct == MM::BeforeGet)
   {
      long index;
      if (!GetLongParam_PvCam(hPVCAM_, PARAM_SPDTAB_INDEX, &index))
         return pl_error_code();
      string mode;
      int nRet = GetSpeedString(mode);
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

      if (!SetLongParam_PvCam(hPVCAM_, PARAM_READOUT_PORT, port))
         return pl_error_code();

      // Update elements that might have changes because of port change
      int ret = GetSpeedTable();
      if (ret != DEVICE_OK)
         return ret;
      if (!SetGainLimits())
         return pl_error_code();
      if (!SetAllowedPixelTypes())
         return pl_error_code();

      // update GUI to reflect these changes
      ret = OnPropertiesChanged();
      if (ret != DEVICE_OK)
         return ret;
   }
   else if (eAct == MM::BeforeGet)
   {
      long port;
      if (!GetLongParam_PvCam(hPVCAM_, PARAM_READOUT_PORT, &port))
         return pl_error_code();
      std::string portName = GetPortName(port);

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
      long gain;
      pProp->Get(gain);
      if (!SetLongParam_PvCam(hPVCAM_, PARAM_GAIN_INDEX, gain))
         return pl_error_code();
   }
   else if (eAct == MM::BeforeGet)
   {
      long gain;
      if (!GetLongParam_PvCam(hPVCAM_, PARAM_GAIN_INDEX, &gain))
         return pl_error_code();
      pProp->Set(gain);
   }

   return DEVICE_OK;
}

// EM Gain
int Universal::OnMultiplierGain(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      long gain;
      pProp->Get(gain);
      if (!SetLongParam_PvCam(hPVCAM_, PARAM_GAIN_MULT_FACTOR, gain))
         return pl_error_code();
   }
   else if (eAct == MM::BeforeGet)
   {
      long gain;
      if (!GetLongParam_PvCam(hPVCAM_, PARAM_GAIN_MULT_FACTOR, &gain))
         return pl_error_code();
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
      if (!GetLongParam_PvCam(hPVCAM_, PARAM_TEMP, &temp))
         return pl_error_code();
	  pProp->Set(temp/100);
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
	   if (!SetLongParam_PvCam(hPVCAM_, PARAM_TEMP_SETPOINT, temp))
			return pl_error_code();
	}
	else if (eAct == MM::BeforeGet)
	{
	  long temp;
      if (!GetLongParam_PvCam(hPVCAM_, PARAM_TEMP_SETPOINT, &temp))
         return pl_error_code();
	  pProp->Set(temp/100);
	}
	return DEVICE_OK;
}

int Universal::OnUniversalProperty(MM::PropertyBase* pProp, MM::ActionType eAct, long index)
{
	if (eAct == MM::AfterSet)
	{
      uns16 dataType;
      if (!pl_get_param(hPVCAM_, param_set[index].id, ATTR_TYPE, &dataType) || (dataType != TYPE_ENUM)) {
	      long ldata;
	      pProp->Get(ldata);
	      if (!SetLongParam_PvCam(hPVCAM_, (long)param_set[index].id, (uns32) ldata))
			   return pl_error_code();
      } else {
         std::string mnemonic;
         pProp->Get(mnemonic);
         uns32 ldata = param_set[index].extMap[mnemonic];
         if (!SetLongParam_PvCam(hPVCAM_, (long)param_set[index].id, (uns32) ldata))
		   	return pl_error_code();
      }
	}
	else if (eAct == MM::BeforeGet)
	{
  	   long ldata;
      if (!GetLongParam_PvCam(hPVCAM_, param_set[index].id, &ldata))
          return pl_error_code();
      uns16 dataType;
      if (!pl_get_param(hPVCAM_, param_set[index].id, ATTR_TYPE, &dataType) || (dataType != TYPE_ENUM)) {
         pProp->Set(ldata);
      } else {
         char enumStr[100];
         int32 enumValue;
         if (pl_get_enum_param(hPVCAM_, param_set[index].id, ldata, &enumValue, enumStr, 100)) {
            pProp->Set(enumStr);
         } else {
            return pl_error_code();
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
// Description     : Initialize the camera
// Return type     : bool 

int Universal::Initialize()
{
   // setup the camera
   // ----------------

   // Description
   int nRet = CreateProperty(MM::g_Keyword_Description, "PVCAM API device adapter", MM::String, true);
   assert(nRet == DEVICE_OK);

   // gather information about the equipment
   // ------------------------------------------
   
   // Camera name
   char name[CAM_NAME_LEN] = "Undef";
   if (!pl_pvcam_init())
      return pl_error_code();

   // Get PVCAM version
   uns16 version;
   if (!pl_pvcam_get_ver(&version))
      return pl_error_code();
   uns16 major, minor, trivial;
   major = version; major = major >> 8; major = major & 0xFF;
   minor = version; minor = minor >> 4; minor = minor & 0xF;
   trivial = version; trivial = trivial & 0xF;
   stringstream ver;
   ver << major << "." << minor << "." << trivial;
   nRet = CreateProperty("PVCAM Version", ver.str().c_str(),  MM::String, true);
   assert(nRet == DEVICE_OK);

   // find camera
   if (!pl_cam_get_name(cameraId_, name))
      return ERR_CAMERA_NOT_FOUND;
      //return pl_error_code();

   // Get a handle to the camera
   if (!pl_cam_open(name, &hPVCAM_, OPEN_EXCLUSIVE ))
      return pl_error_code();

   if (!pl_cam_get_diags(hPVCAM_))
      return pl_error_code();

   name_ = name;

   // Name
   nRet = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   assert(nRet == DEVICE_OK);


   // Chip Name
   CPropertyAction *pAct = new CPropertyAction (this, &Universal::OnChipName);
   nRet = CreateProperty("ChipName", "", MM::String, true, pAct);

   // setup image parameters
   // ----------------------

   // exposure
   pAct = new CPropertyAction (this, &Universal::OnExposure);
   nRet = CreateProperty(MM::g_Keyword_Exposure, "10.0", MM::Float, false, pAct);
   assert(nRet == DEVICE_OK);

   // binning
   pAct = new CPropertyAction (this, &Universal::OnBinning);
   pAct = new CPropertyAction (this, &Universal::OnBinning);
   nRet = CreateProperty(MM::g_Keyword_Binning, "1", MM::Integer, false, pAct);
   assert(nRet == DEVICE_OK);
   vector<string> binValues;
   binValues.push_back("1");
   binValues.push_back("2");
   binValues.push_back("4");
   binValues.push_back("8");
   nRet = SetAllowedValues(MM::g_Keyword_Binning, binValues);
   if (nRet != DEVICE_OK)
      return nRet;

   // Pixel type.  
   // Note that this can change depending on the readoutport and speed.  SettAllowedPixeltypes should be called after changes in any of these
   pAct = new CPropertyAction (this, &Universal::OnPixelType);
   nRet = CreateProperty(MM::g_Keyword_PixelType, g_PixelType_16bit, MM::String, false, pAct);
   assert(nRet == DEVICE_OK);
   SetAllowedPixelTypes();


   // Gain
   // Check the allowed gain settings.  Note that this can change depending on output port, and readout rate. SetGainLimits() should be called after changing those parameters. 
   pl_get_param( hPVCAM_, PARAM_GAIN_INDEX, ATTR_AVAIL, &gainAvailable_);
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
   pl_get_param( hPVCAM_, PARAM_READOUT_PORT, ATTR_AVAIL, &readoutPortAvailable);
   if (readoutPortAvailable) {
      uns32 minPort, maxPort;
      pl_get_param(hPVCAM_, PARAM_READOUT_PORT, ATTR_COUNT, &nrPorts_);
      pl_get_param(hPVCAM_, PARAM_READOUT_PORT, ATTR_MIN, &minPort);
      pl_get_param(hPVCAM_, PARAM_READOUT_PORT, ATTR_MAX, &maxPort);
      long currentPort;
      if (!GetLongParam_PvCam(hPVCAM_, PARAM_READOUT_PORT, &currentPort))
         return pl_error_code();
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
      for (uns32 i=minPort; i<nrPorts_; i++) {
         portValues.push_back(GetPortName(i));
         portMap_[GetPortName(i)] = i;
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
   if (!pl_get_param(hPVCAM_, PARAM_CHIP_NAME, ATTR_CURRENT, chipName))
      return pl_error_code();
   pl_get_param( hPVCAM_, PARAM_GAIN_MULT_FACTOR, ATTR_AVAIL, &emGainAvailable);
   if (emGainAvailable && (strstr(chipName, "ICX-285") == 0)) {
      LogMessage("This Camera has Em Gain");
      pAct = new CPropertyAction (this, &Universal::OnMultiplierGain);
      nRet = CreateProperty("MultiplierGain", "1", MM::Integer, false, pAct);
      assert(nRet == DEVICE_OK);
      int16 emGainMax;
      // Apparently, the minimum EM gain is always 1
      pl_get_param( hPVCAM_, PARAM_GAIN_MULT_FACTOR, ATTR_MAX, &emGainMax);
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

	   bool getLongSuccess = GetLongParam_PvCam(hPVCAM_, param_set[i].id, &ldata);
	   pl_get_param( hPVCAM_, param_set[i].id, ATTR_ACCESS, &AccessType);
	   pl_get_param( hPVCAM_, param_set[i].id, ATTR_AVAIL, &bAvail);
	   if ( (AccessType != ACC_ERROR) && bAvail && getLongSuccess && versionTest) 
	   {
	      snprintf(buf, BUFSIZE, "%ld", ldata);
	      CPropertyActionEx *pAct = new CPropertyActionEx(this, &Universal::OnUniversalProperty, (long)i);
         uint16_t dataType;
         rs_bool plResult = pl_get_param(hPVCAM_, param_set[i].id, ATTR_TYPE, &dataType);
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
            if (pl_get_param(hPVCAM_, param_set[i].id, ATTR_COUNT, (void *) &count)) {
               for (index = 0; index < count; index++) {
                  if (pl_get_enum_param(hPVCAM_, param_set[i].id, index, &enumValue, enumStr, 100)) {
                     AddAllowedValue(param_set[i].name, enumStr);
                     std::string tmp = enumStr;
                     param_set[i].extMap[tmp] = index;
                  }
                  else
                     printf ("Error");
               }
            }
         }

	      assert(nRet == DEVICE_OK);
	   }
   }

   // actual interval in streaming mode
   nRet = CreateProperty(MM::g_Keyword_ActualInterval_ms, "0.0", MM::Float, false);
   assert(nRet == DEVICE_OK);

   // synchronize all properties
   // --------------------------
   nRet = UpdateStatus();
   if (nRet != DEVICE_OK)
      return nRet;

   // setup imaging
   if (!pl_exp_init_seq())
       return pl_error_code();

   // setup the buffer
   // ----------------
   nRet = ResizeImageBufferSingle();
 
   if (nRet != DEVICE_OK)
      return nRet;

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
      pl_exp_uninit_seq();
      pl_cam_close(hPVCAM_);
      pl_pvcam_uninit();
      initialized_ = false;
   }
   return DEVICE_OK;
}

bool Universal::Busy()
{
   return acquiring_;
}


///////////////////////////////////////////////////////////////////////////////
// Function name   : &Universal::SnapImage
// Description     : Acquires a single frame and stores it in the internal
//                   buffer.
//                   This command blocks the calling thread
//                   until the image is fully captured.
// Return type     : int 

int Universal::SnapImage()
{
   int16 status;
   uns32 not_needed;
   rs_bool ret;

   if (!bufferOK_)
      return ERR_INVALID_BUFFER;

   void* pixBuffer = const_cast<unsigned char*> (img_.GetPixels());
   pl_exp_start_seq(hPVCAM_, pixBuffer);

   // For MicroMax cameras, wait untill exposure is finished, for others, use the pvcam check status function
   // Check for MicroMax camera in the chipname, this might not be the best method
   if (chipName_.substr(0,3).compare("PID") == 0)
   {
      MM::MMTime startTime = GetCurrentMMTime();
      do {
         CDeviceUtils::SleepMs(2);
      } while ( (GetCurrentMMTime() - startTime) < ( (exposure_ + 50) * 1000.0) );
	  ostringstream db;
	  db << chipName_.substr(0,3) << " exposure: " << exposure_;
	  LogMessage (db.str().c_str());
   } else { // All modern cameras
      // block until exposure is finished
      do {
         ret = pl_exp_check_status(hPVCAM_, &status, &not_needed);
      } while (ret && (status == EXPOSURE_IN_PROGRESS));
      if (!ret)
         return pl_error_code();   
   }

   return DEVICE_OK;
}

const unsigned char* Universal::GetImageBuffer()
{  
   int16 status;
   uns32 not_needed;

   // wait for data or error
   void* pixBuffer = const_cast<unsigned char*> (img_.GetPixels());

   while(pl_exp_check_status(hPVCAM_, &status, &not_needed) && 
      (status != READOUT_COMPLETE && status != READOUT_FAILED) );

   // Check Error Codes
   if(status == READOUT_FAILED)
      // return pl_error_code();
      return 0;

   if (!pl_exp_finish_seq(hPVCAM_, pixBuffer, 0))
      // return pl_error_code();
      return 0;

   return (unsigned char*) pixBuffer;
}

double Universal::GetExposure() const
{
   char buf[MM::MaxStrLength];
   buf[0] = '\0';
   GetProperty(MM::g_Keyword_Exposure, buf);
   return atof(buf);
}

void Universal::SetExposure(double exp)
{
   SetProperty(MM::g_Keyword_Exposure, CDeviceUtils::ConvertToString(exp));
}

/**
 * Returns the raw image buffer.
 */

unsigned Universal::GetBitDepth() const
{
   int16 bitDepth;
   if (! pl_get_param( hPVCAM_, PARAM_BIT_DEPTH, ATTR_CURRENT, &bitDepth)) {
      char buf[ERROR_MSG_LEN];
      pl_error_message (pl_error_code(), buf);
      // TODO: implement const LogMessage
      // LogMessage(buf);
      return 0;
   }
   return (unsigned) bitDepth;
}


int Universal::SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize)
{
      if (acquiring_)
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
   if (acquiring_)
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
   pl_get_param( hPVCAM_, PARAM_BIT_DEPTH, ATTR_CURRENT, &bitDepth);
   pl_get_param( hPVCAM_, PARAM_BIT_DEPTH, ATTR_INCREMENT, &bitDepthIncrement);
   pl_get_param( hPVCAM_, PARAM_BIT_DEPTH, ATTR_MAX, &maxBitDepth);
   pl_get_param( hPVCAM_, PARAM_BIT_DEPTH, ATTR_MIN, &minBitDepth);
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
   return nRet;
}

int Universal::SetGainLimits()
{
   int ret = DEVICE_OK;
   if (!gainAvailable_)
      return DEVICE_OK;
   int16 gainMin, gainMax;
   pl_get_param( hPVCAM_, PARAM_GAIN_INDEX, ATTR_MIN, &gainMin);
   pl_get_param( hPVCAM_, PARAM_GAIN_INDEX, ATTR_MAX, &gainMax);
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
      ret = SetAllowedValues(MM::g_Keyword_Gain, values);
   } else
      ret = SetPropertyLimits(MM::g_Keyword_Gain, (int) gainMin, (int) gainMax);
   return ret;
}

int Universal::SetUniversalAllowedValues(int i, uns16 dataType)
{
   switch (dataType) { 
      case TYPE_INT8 : {
         int8 min, max, index;
         if (pl_get_param(hPVCAM_, param_set[i].id, ATTR_MIN, (void *) &min) && pl_get_param(hPVCAM_, param_set[i].id, ATTR_MAX, (void*) &max ) )  {
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
         }
         break;
      }
      case TYPE_UNS8 : {
         uns8 min, max, index;
         if (pl_get_param(hPVCAM_, param_set[i].id, ATTR_MIN, (void *) &min) && pl_get_param(hPVCAM_, param_set[i].id, ATTR_MAX, (void*) &max ) )  {
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
         }
         break;
      }
      case TYPE_INT16 : {
         int16 min, max, index;
         if (pl_get_param(hPVCAM_, param_set[i].id, ATTR_MIN, (void *) &min) && pl_get_param(hPVCAM_, param_set[i].id, ATTR_MAX, (void*) &max ) )  {
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
         }
         break;
      }
      case TYPE_UNS16 : {
         uns16 min, max, index;
         if (pl_get_param(hPVCAM_, param_set[i].id, ATTR_MIN, (void *) &min) && pl_get_param(hPVCAM_, param_set[i].id, ATTR_MAX, (void*) &max ) )  {
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
         }
         break;
      }
      case TYPE_INT32 : {
         int32 min, max, index;
         if (pl_get_param(hPVCAM_, param_set[i].id, ATTR_MIN, (void *) &min) && pl_get_param(hPVCAM_, param_set[i].id, ATTR_MAX, (void*) &max ) )  {
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
         }
         break;
      }
      case TYPE_UNS32 : {
         uns32 min, max, index;
         if (pl_get_param(hPVCAM_, param_set[i].id, ATTR_MIN, (void *) &min) && pl_get_param(hPVCAM_, param_set[i].id, ATTR_MAX, (void*) &max ) )  {
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
   if (!pl_get_param(hPVCAM_, PARAM_SPDTAB_INDEX, ATTR_CURRENT, &spdTableIndex))
      return pl_error_code();

   // read pixel time:
   long pixelTime;
   if (!GetLongParam_PvCam(hPVCAM_, PARAM_PIX_TIME, &pixelTime))
         return pl_error_code();
   double rate = 1000/pixelTime; // in MHz

   // read bit depth
   long bitDepth;
   if (!GetLongParam_PvCam(hPVCAM_, PARAM_BIT_DEPTH, &bitDepth))
         return pl_error_code();

   tmp << rate << "MHz " << bitDepth << "bit";
   modeString = tmp.str();
   return DEVICE_OK;
}

/*
 * Sets allowed values for entry "Speed"
 * Also sets the Map rateMap_
 */
int Universal::GetSpeedTable()
{
   int16 spdTableCount;
   if (!pl_get_param(hPVCAM_, PARAM_SPDTAB_INDEX, ATTR_MAX, &spdTableCount))
      return pl_error_code();
   //Speed Table Index is 0 based.  We got the max number but want the total count
   spdTableCount +=1;
   ostringstream os;
   os << "SpeedTableCountd: " << spdTableCount << "\n";

   // log the current settings, so that we can revert to it after cycling through the options
   int16 spdTableIndex;
   pl_get_param(hPVCAM_, PARAM_SPDTAB_INDEX, ATTR_CURRENT, &spdTableIndex);
   // cycle through the speed table entries and record the associated settings
   vector<string> speedValues;
   rateMap_.clear();
   string speedString;
   for (int16 i=0; i<spdTableCount; i++) 
   {
      stringstream tmp;
      pl_set_param(hPVCAM_, PARAM_SPDTAB_INDEX, &i);
      int nRet = GetSpeedString(speedString);
      if (nRet != DEVICE_OK)
         return nRet;
      os << "Speed: " << speedString.c_str() << "\n";
      speedValues.push_back(speedString);
      rateMap_[speedString] = i;
   }
   LogMessage (os.str().c_str(), true);
   // switch back to original setting
   pl_set_param(hPVCAM_, PARAM_SPDTAB_INDEX, &spdTableIndex);
   return SetAllowedValues(g_ReadoutRate, speedValues);
}

std::string Universal::GetPortName(long portId)
{
   // Work around bug in PVCAM:
   if (nrPorts_ == 1) 
      return "Normal";
      
   string portName;
   int32 enumIndex;
   if (!GetEnumParam_PvCam((uns32)PARAM_READOUT_PORT, (uns32)portId, portName, enumIndex))
      LogMessage("Error in GetEnumParam in GetPortName");
   switch (enumIndex)
   {
      case 0: portName = "EM"; break;
      case 1: portName = "Normal"; break;
      case 2: portName = "LowNoise"; break;
      case 3: portName = "HighCap"; break;
      default: portName = "Normal";
   }
   return portName;
}

bool Universal::GetEnumParam_PvCam(uns32 pvcam_cmd, uns32 index, std::string& enumString, int32& enumIndex)
{
   // First check this is an enumerated type:
   uint16_t dataType;
   if (!pl_get_param(hPVCAM_, pvcam_cmd, ATTR_TYPE, &dataType))
         return false;

   if (dataType != TYPE_ENUM)
      return false;

   long unsigned int strLength;
   if (!pl_enum_str_length(hPVCAM_, pvcam_cmd, index, &strLength))                           
         return false;

   char* strTmp;
   strTmp = (char*) malloc(strLength);
   int32 tmp;
   if (!pl_get_enum_param(hPVCAM_, pvcam_cmd, index, &tmp, strTmp, strLength))
      return false;

   enumIndex = tmp;
   enumString = strTmp;
   free (strTmp);

   return true;
}

 

int Universal::ResizeImageBufferSingle()
{
   bufferOK_ = false;

   unsigned short xdim, ydim;
	if (FALSE == pl_ccd_get_par_size(hPVCAM_, &ydim))
      return pl_error_code();
	if (FALSE == pl_ccd_get_ser_size(hPVCAM_, &xdim))
      return pl_error_code();

   if (roi_.isEmpty())
   {
      // set to full frame
      roi_.xSize = xdim;
      roi_.ySize = ydim;
   }

   // NOTE: roi dimensions are expressed in no-binning mode

   // format the image buffer
   // >>> assuming 16-bit pixels!!!
   //unsigned short newXSize = (unsigned short) ((double)roi_.xSize/binSize_ + 0.5);
   //unsigned short newYSize = (unsigned short) ((double)roi_.ySize/binSize_ + 0.5);
   unsigned short newXSize = (unsigned short) (roi_.xSize/binSize_);
   unsigned short newYSize = (unsigned short) (roi_.ySize/binSize_);

   // make an attempt to adjust the image dimensions
   while ((newXSize * newYSize * 2) % 4 != 0 && newXSize > 4 && newYSize > 4)
   {
      roi_.xSize--;
      newXSize = (unsigned short) (roi_.xSize/binSize_);
      if ((newXSize * newYSize * 2) % 4 != 0)
      {
         roi_.ySize--;
         newYSize = (unsigned short) (roi_.ySize/binSize_);
      }
   }

   img_.Resize(newXSize, newYSize, 2);

   rgn_type region = { roi_.x, roi_.x + roi_.xSize-1, (uns16) binSize_, roi_.y, roi_.y + roi_.ySize-1, (uns16) binSize_};
   uns32 size;

   if (!pl_exp_setup_seq(hPVCAM_, 1, 1, &region, TIMED_MODE, (uns32)exposure_, &size ))
      return pl_error_code();

   if (img_.Height() * img_.Width() * img_.Depth() != size)
   {
      return DEVICE_INTERNAL_INCONSISTENCY; // buffer sizes don't match ???
   }

   bufferOK_ = true;

   return DEVICE_OK;
}

int Universal::ResizeImageBufferContinuous()
{
   // check for circular buffer support
   rs_bool availFlag;
   if (!pl_get_param(hPVCAM_, PARAM_CIRC_BUFFER, ATTR_AVAIL, &availFlag) || !availFlag)
      return ERR_STREAM_MODE_NOT_SUPPORTED;

   unsigned short xdim, ydim;
	if (FALSE == pl_ccd_get_par_size(hPVCAM_, &ydim))
      return pl_error_code();
	if (FALSE == pl_ccd_get_ser_size(hPVCAM_, &xdim))
      return pl_error_code();

   if (roi_.isEmpty())
   {
      // set to full frame
      roi_.xSize = xdim;
      roi_.ySize = ydim;
   }

   // NOTE: roi dimensions are expressed in no-binning mode
   // format the image buffer
   // (assuming 2-byte pixels)
   unsigned short newXSize = (unsigned short) (roi_.xSize/binSize_);
   unsigned short newYSize = (unsigned short) (roi_.ySize/binSize_);

   // make an attempt to adjust the image dimensions
   while ((newXSize * newYSize * 2) % 4 != 0 && newXSize > 4 && newYSize > 4)
   {
      roi_.xSize--;
      newXSize = (unsigned short) (roi_.xSize/binSize_);
      if ((newXSize * newYSize * 2) % 4 != 0)
      {
         roi_.ySize--;
         newYSize = (unsigned short) (roi_.ySize/binSize_);
      }
   }
   img_.Resize(newXSize, newYSize, 2);

   rgn_type region = { roi_.x, roi_.x + roi_.xSize-1, (uns16) binSize_, roi_.y, roi_.y + roi_.ySize-1, (uns16) binSize_};

   uns32 frameSize;
   if (!pl_exp_setup_cont(hPVCAM_, 1, &region, TIMED_MODE, (uns32)exposure_, &frameSize, CIRC_OVERWRITE))
      return pl_error_code();
   
   pl_exp_set_cont_mode(hPVCAM_, CIRC_OVERWRITE );

   if (img_.Height() * img_.Width() * img_.Depth() != frameSize)
   {
      return DEVICE_INTERNAL_INCONSISTENCY; // buffer sizes don't match ???
   }

   // set up a circular buffer for 3 frames
   bufferSize_ = frameSize * 3;
   delete[] circBuffer_;
   circBuffer_ = new unsigned short[bufferSize_];

   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// Continuous acquisition
//

/**
 * Continuous acquisition thread service routine.
 * Starts acquisition on the PVCAM and repeatedly calls PushImage()
 * to transfer any new images to the MMCore circular buffer.
 */
int AcqSequenceThread::svc(void)
{
   int16 status;
   uns32 byteCnt;
   uns32 bufferCnt;
   long imageCounter(0);

   printf("Entering streaming thread...\n");
   do
   {
      // wait until image is ready
      while (pl_exp_check_cont_status(camera_->hPVCAM_, &status, &byteCnt, &bufferCnt) && (status != READOUT_COMPLETE && status != READOUT_FAILED))
      {
         CDeviceUtils::SleepMs(5);
      }

      if (status == READOUT_FAILED)
      {
         camera_->StopSequenceAcquisition();
         printf("Readout failed!\n");
         return 1;
      }

      // new frame arrived
      int retCode = camera_->PushImage();
      if (retCode != DEVICE_OK)
      {
         printf("PushImage() failed, code %d\n", retCode);
         camera_->StopSequenceAcquisition();
         return 2;
      }
      //printf("Acquired frame %ld.\n", imageCounter);
      imageCounter++;
   } while (!stop_ && imageCounter < numImages_);

   if (stop_)
   {
      printf("Acquisition interrupted by the user!\n");
      return 0;
   }


   camera_->StopSequenceAcquisition();
   printf("Acquisition completed.\n");
   return 0; // finished
}

/**
 * Starts continuous acquisition.
 */
int Universal::StartSequenceAcquisition(long numImages, double interval_ms)
{
   if (acquiring_)
      return ERR_BUSY_ACQUIRING;

   ostringstream os;
   os << "Started sequnce acquisition: " << numImages << " at " << interval_ms << " ms" << endl;
   LogMessage(os.str().c_str());

   // prepare the camera
   int ret = ResizeImageBufferContinuous();
   if (ret != DEVICE_OK)
      return ret;

   // start thread
   // prepare the core
   ret = GetCoreCallback()->PrepareForAcq(this);
   if (ret != DEVICE_OK)
   {
      ResizeImageBufferSingle();
      return ret;
   }

   // start thread
   imageCounter_ = 0;
   sequenceLength_ = numImages;

   seqThread_->SetLength(numImages);
   sequenceStartTime_ = GetCurrentMMTime();

   if (!pl_exp_start_cont(hPVCAM_, circBuffer_, bufferSize_))
   {
      ResizeImageBufferSingle();
      return pl_error_code();
   }

   seqThread_->Start();
   acquiring_ = true;

   // set actual interval the same as exposure
   // with PVCAM there is no straightforward way to get actual interval
   // TODO: create a better estimate
   //SetProperty(MM::g_Keyword_ActualInterval_ms, CDeviceUtils::ConvertToString(exposure_)); 

   return DEVICE_OK;
}

/**
 * Stops acquisition
 */
int Universal::StopSequenceAcquisition()
{
   MM::MMTime sequenceStopTime = GetCurrentMMTime();
   MM::MMTime duration = sequenceStopTime - sequenceStartTime_;
   SetProperty(MM::g_Keyword_ActualInterval_ms, CDeviceUtils::ConvertToString((double) (duration.getMsec()/seqThread_->GetNumImages())));

   LogMessage("Stopped sequence acquisition");
   pl_exp_finish_seq(hPVCAM_, circBuffer_, 0);
   pl_exp_stop_cont(hPVCAM_, CCS_HALT);

   seqThread_->Stop();
   acquiring_ = false;

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
 * In case of error or if the sequence is finished StopSequenceAcquisition()
 * is called, which will raise the stop_ flag and cause the thread to exit.
 */
int Universal::PushImage()
{
   // get the image from the circular buffer
   void_ptr imgPtr;
   if (!pl_exp_get_oldest_frame(hPVCAM_, &imgPtr))
   {
      int16 errorCode = pl_error_code();
      char msg[ERROR_MSG_LEN];
      pl_error_message(errorCode, msg);
      printf("get_oldest_frame() error: %s\n", msg);
      return errorCode;
   }
   pl_exp_unlock_oldest_frame(hPVCAM_);

   // process image
   MM::ImageProcessor* ip = GetCoreCallback()->GetImageProcessor(this);
   if (ip)
   {
      int ret = ip->Process((unsigned char*) imgPtr, GetImageWidth(), GetImageHeight(), GetImageBytesPerPixel());
      if (ret != DEVICE_OK)
         return ret;
   }

   // This method inserts new image in the circular buffer (residing in MMCore)
   return GetCoreCallback()->InsertImage(this, (unsigned char*) imgPtr,
                                           GetImageWidth(),
                                           GetImageHeight(),
                                           GetImageBytesPerPixel());
}
