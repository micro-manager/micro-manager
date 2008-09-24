///////////////////////////////////////////////////////////////////////////////
// FILE:          Hamamatsu.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Hamamatsu camera module based on DCAM API
// COPYRIGHT:     University of California, San Francisco, 2006, 2007, 2008
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
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 08/24/2005, contributions by Nico Stuurman
// NOTES:    
//
// CVS:           $Id$
//
#ifdef WIN32
   #define WIN32_LEAN_AND_MEAN
   #include <windows.h>
#else
   #include <memory.h>
   void ZeroMemory(void* mem, int size) 
   {
      memset(mem,size,0);
   }
#endif
#include "../../MMDevice/ModuleInterface.h"
#include "Hamamatsu.h"
#ifdef WIN32
#include "../../../3rdparty/Hamamatsu/DCAMSDK/2007-12/inc/dcamapix.h"
#include "../../../3rdparty/Hamamatsu/DCAMSDK/2007-12/inc/dcamprop.h"
#include "../../../3rdparty/Hamamatsu/DCAMSDK/2007-12/inc/features.h"
#pragma warning(disable : 4996) // disable warning for deperecated CRT functions on Windows 
#else
#include <dcamapi/dcamapix.h>
#include <dcamapi/dcamprop.h>
#include <dcamapi/features.h>
#endif
#include <set>
#include <string>
#include <cstring>
#include <sstream>
#include <iomanip>


using namespace std;

// global constants
const char* g_DeviceName = "Hamamatsu_DCAM";

const char* g_PixelType_8bit = "8bit";
const char* g_PixelType_16bit = "16bit";
const char* g_TrigMode = "Trigger";
const char* g_TrigMode_Internal = "Internal";
const char* g_TrigMode_Edge = "Edge";
const char* g_TrigMode_Level = "Level";
const char* g_TrigMode_MultiShot_Sensitive = "Multishot Sensitive";
const char* g_TrigMode_Cycle_Delay = "Cycle Delay";
const char* g_TrigMode_Software = "Software";
const char* g_TrigMode_FastRepetition = "Fast Repetition";
const char* g_TrigMode_TDI = "TDI";
const char* g_TrigMode_TDIInternal = "TDI Internal";
const char* g_TrigMode_Start = "Start";
const char* g_TrigMode_SyncReadout = "Sync Readout";
const char* g_TrigPolarity_Positive = "Positive";
const char* g_TrigPolarity_Negative = "Negative";


// singleton instance
//CHamamatsu* CHamamatsu::m_pInstance = 0;
unsigned CHamamatsu::refCount_ = 0;
HINSTANCE CHamamatsu::m_hDCAMModule = 0;

#ifdef WIN32
// Windows dll entry routine
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
   AddAvailableDeviceName(g_DeviceName, "Universal adapter using DCAM interface");
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   string strName(deviceName);
   
   if (strcmp(deviceName, g_DeviceName) == 0)
      return new CHamamatsu();
   
   return 0;
}


///////////////////////////////////////////////////////////////////////////////
// CHamamatsu constructor/destructor

CHamamatsu::CHamamatsu() :
   CCameraBase<CHamamatsu> (),
   m_bInitialized(false),
   m_bBusy(false),
   acquiring_(false),
   m_hDCAM(0),
   snapInProgress_(false),
   softwareTriggerEnabled_ (false),
   lnBin_(1),
   slot_(0),
   originalTrigMode_("")
{
   InitializeDefaultErrorMessages();
   // slot
   CPropertyAction *pAct = new CPropertyAction (this, &CHamamatsu::OnSlot);
   CreateProperty("Slot", "0", MM::Integer, false, pAct, true);

   seqThread_ = new AcqSequenceThread(this); 

   SetErrorText(ERR_INTERNAL_BUFFER_FULL, "Internal buffer overflow");
   SetErrorText(ERR_BUSY_ACQUIRING, "Busy acquiring an image");
   SetErrorText(ERR_BUFFER_ALLOCATION_FAILED, "Buffer allocation failed");
   SetErrorText(ERR_INCOMPLETE_SNAP_IMAGE_CYCLE, "Incomplete snap image cycle");
   SetErrorText(ERR_NO_CAMERA_FOUND, "No camera found");
}

CHamamatsu::~CHamamatsu()
{
   // release resources
   if (m_bInitialized)
      Shutdown();
   
   delete seqThread_;
}

/*
CHamamatsu* CHamamatsu::GetInstance()
{
   // if 
   if (!m_pInstance)
      m_pInstance = new CHamamatsu();

   refCount_++;
   return m_pInstance;
}
*/
///////////////////////////////////////////////////////////////////////////////
// Action handlers
// ~~~~~~~~~~~~~~~

// Binning
int CHamamatsu::OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      pProp->Get(lnBin_);
      int nRet = ShutdownImageBuffer();
      if (nRet != DEVICE_OK)
         return nRet;
      if (!dcam_setbinning(m_hDCAM, lnBin_))
         return ReportError("Error in dcam_setbinning: ");
      nRet = ResizeImageBuffer();
      if (nRet != DEVICE_OK)
         return nRet;
   }
   else if (eAct == MM::BeforeGet)
   {
      if (!dcam_getbinning(m_hDCAM, &lnBin_))
         return ReportError("Error in dcam_getbinning: ");
      pProp->Set(lnBin_);
   }
   return DEVICE_OK;
}

// Trigger polarity
int CHamamatsu::OnTrigPolarity(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      std::string triggerPolarity;
      pProp->Get(triggerPolarity);
      int nRet = ShutdownImageBuffer();
      if (nRet != DEVICE_OK)
         return nRet;
      long pol = DCAM_TRIGPOL_POSITIVE;
      if (triggerPolarity == g_TrigPolarity_Negative)
         pol = DCAM_TRIGPOL_NEGATIVE;
      if (!dcam_settriggerpolarity(m_hDCAM, pol))
         return ReportError("Error in dcam_settriggerpolarity: ");
      nRet = ResizeImageBuffer();
      if (nRet != DEVICE_OK)
         return nRet;
   }
   else if (eAct == MM::BeforeGet)
   {
      long pol;
      if (!dcam_gettriggerpolarity(m_hDCAM, &pol))
         return ReportError("Error in dcam_gettriggerpolarity: ");
      if (pol == DCAM_TRIGPOL_NEGATIVE)
         pProp->Set(g_TrigPolarity_Negative);
      else
         pProp->Set(g_TrigPolarity_Positive);
   }
   return DEVICE_OK;
}

// Trigger Mode
int CHamamatsu::OnTrigMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      std::string triggerMode;
      pProp->Get(triggerMode);
      int nRet = ShutdownImageBuffer();
      if (nRet != DEVICE_OK)
         return nRet;
      long mode = DCAM_TRIGMODE_SOFTWARE;
      if (triggerMode == g_TrigMode_Software)
         mode = DCAM_TRIGMODE_SOFTWARE;
      else if (triggerMode == g_TrigMode_Internal)
         mode = DCAM_TRIGMODE_INTERNAL;
      else if (triggerMode == g_TrigMode_Edge)
         mode = DCAM_TRIGMODE_EDGE;
      else if (triggerMode == g_TrigMode_Level)
         mode = DCAM_TRIGMODE_LEVEL;
      else if (triggerMode == g_TrigMode_MultiShot_Sensitive)
         mode = DCAM_TRIGMODE_MULTISHOT_SENSITIVE;
      else if (triggerMode == g_TrigMode_Cycle_Delay)
         mode = DCAM_TRIGMODE_CYCLE_DELAY;
      else if (triggerMode == g_TrigMode_FastRepetition)
         mode = DCAM_TRIGMODE_FASTREPETITION;
      else if (triggerMode == g_TrigMode_TDI)
         mode = DCAM_TRIGMODE_TDI;
      else if (triggerMode == g_TrigMode_TDIInternal)
         mode = DCAM_TRIGMODE_TDIINTERNAL;
      else if (triggerMode == g_TrigMode_Start)
         mode = DCAM_TRIGMODE_START;

      ostringstream os;
      os << "Setting triggermode to: " << mode;
      LogMessage(os.str().c_str(), true); 

      if (!dcam_settriggermode(m_hDCAM, mode))
         return ReportError("Error in dcam_settriggermode: ");
      triggerMode_ = triggerMode;

      nRet = ResizeImageBuffer();
      if (nRet != DEVICE_OK)
         return nRet;
   }
   else if (eAct == MM::BeforeGet)
   {
      long mode;
      if (!dcam_gettriggermode(m_hDCAM, &mode))
         return ReportError("Error in dcam_gettriggermode: ");
      ostringstream os;
      os << "Triggermode found: " << mode;
      LogMessage(os.str().c_str(), true); 
      switch (mode) {
         case DCAM_TRIGMODE_SOFTWARE: triggerMode_ = g_TrigMode_Software; break;
         case DCAM_TRIGMODE_INTERNAL: triggerMode_ = g_TrigMode_Internal; break;
         case DCAM_TRIGMODE_EDGE: triggerMode_ = g_TrigMode_Edge; break;
         case DCAM_TRIGMODE_MULTISHOT_SENSITIVE: triggerMode_ = g_TrigMode_MultiShot_Sensitive;  break;
         case DCAM_TRIGMODE_CYCLE_DELAY: triggerMode_ = g_TrigMode_Cycle_Delay; break;
         case DCAM_TRIGMODE_FASTREPETITION: triggerMode_ = g_TrigMode_FastRepetition; break;
         case DCAM_TRIGMODE_TDI: triggerMode_ = g_TrigMode_TDI; break;
         case DCAM_TRIGMODE_TDIINTERNAL: triggerMode_ = g_TrigMode_TDIInternal; break;
         case DCAM_TRIGMODE_START: triggerMode_ = g_TrigMode_Start; break;
      }
      pProp->Set(triggerMode_.c_str());
   }
   return DEVICE_OK;
}

// Exposure Time
int CHamamatsu::OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   // exposure property is stored in milliseconds,
   // while the driver returns the value in seconds
   if (eAct == MM::BeforeGet)
   {
      double dExp;
      if (!dcam_getexposuretime(m_hDCAM, &dExp))
         return ReportError("Error in dcam_getexposuretime: ");
      pProp->Set(dExp * 1000.0);
   }
   else if (eAct == MM::AfterSet)
   {
      double dExp;
      pProp->Get(dExp);
      if (!dcam_setexposuretime(m_hDCAM, dExp / 1000.0))
         return ReportError("Error in dcam_setexposuretime: ");
   }
   return DEVICE_OK;
}

// PixelType
int CHamamatsu::OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ccDatatype ccDataType;
   if (eAct == MM::BeforeGet)
   {
      if (!dcam_getdatatype(m_hDCAM, &ccDataType))
         return ReportError("Error in dcam_getdatatype: ");

      if (ccDataType == ccDatatype_uint8 || ccDataType == ccDatatype_int8)
      {
         pProp->Set(g_PixelType_8bit);
      }
      else if (ccDataType == ccDatatype_uint16 || ccDataType == ccDatatype_int16)
      {
         pProp->Set(g_PixelType_16bit);
      }
      else
      {
         // we do not support color images at this time
         return DEVICE_UNSUPPORTED_DATA_FORMAT;
      }
   }
   else if (eAct == MM::AfterSet)
   {
      ccDatatype ccDataType;
      string strType;
      pProp->Get(strType);

      if (strType.compare(g_PixelType_8bit) == 0)
         ccDataType = ccDatatype_uint8;
      else if (strType.compare(g_PixelType_16bit) == 0)
         ccDataType = ccDatatype_uint16;
      else
         return DEVICE_INTERNAL_INCONSISTENCY;

      int nRet = ShutdownImageBuffer();
      if (nRet != DEVICE_OK)
         return nRet;

      if (!dcam_setdatatype(m_hDCAM, ccDataType))
         return ReportError("Error in dcam_setdatatype: ");

      nRet = ResizeImageBuffer();
      if (nRet != DEVICE_OK)
         return nRet;
   }
   return DEVICE_OK;
}

// ScanMode
int CHamamatsu::OnScanMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   DCAM_PARAM_SCANMODE ScanMode;
   ZeroMemory((LPVOID)&ScanMode, sizeof(DCAM_PARAM_SCANMODE));
   ScanMode.hdr.cbSize = sizeof(DCAM_PARAM_SCANMODE);
   ScanMode.hdr.id = (DWORD) DCAM_IDPARAM_SCANMODE;

   if (eAct == MM::AfterSet)
   {
      long lnScanMode;
      pProp->Get(lnScanMode);
      ScanMode.speed = lnScanMode;
      int ret = ShutdownImageBuffer();
      if (ret != DEVICE_OK)
         return ret;
      if (!dcam_extended(m_hDCAM, DCAM_IDMSG_SETPARAM, (LPVOID)&ScanMode, sizeof(DCAM_PARAM_SCANMODE)))
         return ReportError("Error in dcam_extended (scanmode): ");

      ret = ResizeImageBuffer();
      if (ret != DEVICE_OK)
         return ret;

      // Reset allowedValues for binning and gain
      DWORD	cap;
      if(!dcam_getcapability(m_hDCAM, &cap, DCAM_QUERYCAPABILITY_FUNCTIONS))
         return ReportError("Error in dcam_getcapability: ");
      ret = SetAllowedBinValues(cap);
      if (ret != DEVICE_OK)
         return ret;
      if (IsFeatureSupported(DCAM_IDFEATURE_GAIN)) 
      {
         DCAM_PARAM_FEATURE_INQ featureInq = GetFeatureInquiry(DCAM_IDFEATURE_GAIN);
         ret = SetAllowedGainValues(featureInq);
         if (ret != DEVICE_OK)
            return ret;
      }
      // propagate changes to GUI
      ret = OnPropertiesChanged();
      if (ret != DEVICE_OK)
         return ret;
   }
   else if (eAct == MM::BeforeGet)
   {
      if (!dcam_extended(m_hDCAM,DCAM_IDMSG_GETPARAM, (LPVOID)&ScanMode, sizeof(DCAM_PARAM_SCANMODE)))
         return ReportError("Error in dcam_extended (ScanMode): ");
      pProp->Set(ScanMode.speed);
   }
   return DEVICE_OK;
}

// CCDMode
int CHamamatsu::OnCCDMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      double ccdMode;
      pProp->Get(ccdMode);
      int ret = ShutdownImageBuffer();
      if (ret != DEVICE_OK)
         return ret;
      if (!dcam_setpropertyvalue(m_hDCAM, DCAM_IDPROP_CCDMODE, ccdMode))
         return ReportError("Error in dcam_Setpropertyvalue (ccdMode): ");

      ret = ResizeImageBuffer();
      if (ret != DEVICE_OK)
         return ret;

      // Reset allowedValues for binning and gain
      DWORD	cap;
      if(!dcam_getcapability(m_hDCAM, &cap, DCAM_QUERYCAPABILITY_FUNCTIONS))
         return ReportError("Error in dcam_getcapability: ");
      ret = SetAllowedBinValues(cap);
      if (ret != DEVICE_OK)
         return ret;
      if (IsFeatureSupported(DCAM_IDFEATURE_GAIN)) 
      {
         DCAM_PARAM_FEATURE_INQ featureInq = GetFeatureInquiry(DCAM_IDFEATURE_GAIN);
         ret = SetAllowedGainValues(featureInq);
         if (ret != DEVICE_OK)
            return ret;
      }
      // propagate changes to GUI
      ret = OnPropertiesChanged();
      if (ret != DEVICE_OK)
         return ret;
   }
   else if (eAct == MM::BeforeGet)
   {
      double ccdMode;
      if (!dcam_getpropertyvalue(m_hDCAM, DCAM_IDPROP_CCDMODE, &ccdMode))
         return ReportError("Error in dcam_getpropertyvalue (ccdMode): ");
      //if (!dcam_querypropertyvalue(m_hDCAM, DCAM_IDPROP_CCDMODE, &ccdMode, DCAMPROP_OPTION_NONE))
        // return dcam_getlasterror(m_hDCAM, NULL, 0);

      pProp->Set(ccdMode);
   }
   return DEVICE_OK;
}

// PhotonImagingDMode
int CHamamatsu::OnPhotonImagingMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      double mode;
      pProp->Get(mode);
      int ret = ShutdownImageBuffer();
      if (ret != DEVICE_OK)
         return ret;
      if (!dcam_setpropertyvalue(m_hDCAM, DCAM_IDPROP_PHOTONIMAGINGMODE, mode))
         return ReportError("Error in dcam_setpropertyvalue (photonimagingMode): ");

      ret = ResizeImageBuffer();
      if (ret != DEVICE_OK)
         return ret;

      // Reset allowedValues for binning and gain
      DWORD	cap;
      if(!dcam_getcapability(m_hDCAM, &cap, DCAM_QUERYCAPABILITY_FUNCTIONS))
         return ReportError("Error in dcam_getcapability: ");
      ret = SetAllowedBinValues(cap);
      if (ret != DEVICE_OK)
         return ret;
      if (IsFeatureSupported(DCAM_IDFEATURE_GAIN)) 
      {
         DCAM_PARAM_FEATURE_INQ featureInq = GetFeatureInquiry(DCAM_IDFEATURE_GAIN);
         ret = SetAllowedGainValues(featureInq);
         if (ret != DEVICE_OK)
            return ret;
      }
      // propagate changes to GUI
      ret = OnPropertiesChanged();
      if (ret != DEVICE_OK)
         return ret;
   }
   else if (eAct == MM::BeforeGet)
   {
      double photonMode;
      if (!dcam_getpropertyvalue(m_hDCAM, DCAM_IDPROP_PHOTONIMAGINGMODE, &photonMode))
         return ReportError("Error in dcam_getpropertyvalue (photonimagingmode): ");
      pProp->Set(photonMode);
   }
   return DEVICE_OK;
}

// Output Trigger Polarity
int CHamamatsu::OnOutputTriggerPolarity(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      double mode;
      pProp->Get(mode);
      // Not sure if we need to shut down the image buffer
      int ret = ShutdownImageBuffer();
      if (ret != DEVICE_OK)
         return ret;
      if (!dcam_setpropertyvalue(m_hDCAM, DCAM_IDPROP_OUTPUTTRIGGER_POLARITY, mode))
         return ReportError("Error in dcam_setpropertyvalue (outputtriggerpolarity): ");

      ret = ResizeImageBuffer();
      if (ret != DEVICE_OK)
         return ret;
   }
   else if (eAct == MM::BeforeGet)
   {
      double polarity;
      if (!dcam_getpropertyvalue(m_hDCAM, DCAM_IDPROP_OUTPUTTRIGGER_POLARITY, &polarity))
         return ReportError("Error in dcam_getpropertyvalue (outputtriggerpolarity): ");
      pProp->Set(polarity);
   }
   return DEVICE_OK;
}

// ReadoutTime
int CHamamatsu::OnReadoutTime(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   DCAM_PARAM_FRAME_READOUT_TIME_INQ readoutTime;
   ZeroMemory((LPVOID)&readoutTime, sizeof(DCAM_PARAM_FRAME_READOUT_TIME_INQ));
   readoutTime.hdr.cbSize = sizeof(DCAM_PARAM_FRAME_READOUT_TIME_INQ);
   readoutTime.hdr.id = (DWORD) DCAM_IDPARAM_FRAME_READOUT_TIME_INQ;

   if (eAct == MM::BeforeGet)
   {
      if (!dcam_extended(m_hDCAM,DCAM_IDMSG_GETPARAM, (LPVOID)&readoutTime, sizeof(DCAM_PARAM_FRAME_READOUT_TIME_INQ)))
         return ReportError("Error in dcam_extended (frame_readout_time_inq): ");
      pProp->Set(readoutTime.framereadouttime * 1000.0);
   }

   return DEVICE_OK;
}

// Actual Interval
int CHamamatsu::OnActualIntervalMs(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
	   double readoutTime;
       char rT[MM::MaxStrLength];
       int ret = GetProperty(MM::g_Keyword_ReadoutTime, rT);
	   if (ret != DEVICE_OK)
		   return ret;
       readoutTime = atof(rT);
       double dExp;
       if (!dcam_getexposuretime(m_hDCAM, &dExp))
          return ReportError("Error in dcam_exposuretime: ");
       double interval = max(readoutTime, dExp * 1000);
	   pProp->Set(interval);
	}
	return DEVICE_OK;
}


// Gain
int CHamamatsu::OnGain(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	DCAM_PARAM_FEATURE FeatureValue;
   ZeroMemory((LPVOID)&FeatureValue, sizeof(DCAM_PARAM_FEATURE));
   FeatureValue.hdr.cbSize = sizeof(DCAM_PARAM_FEATURE);
	FeatureValue.hdr.id = (DWORD)DCAM_IDPARAM_FEATURE;
   FeatureValue.featureid = DCAM_IDFEATURE_GAIN;	// same as DCAM_IDFEATURE_CONTRAST

   if (eAct == MM::AfterSet)
   {
      long lnGain;
      pProp->Get(lnGain);
      FeatureValue.featurevalue = (float)lnGain;
      if (!dcam_extended(m_hDCAM, DCAM_IDMSG_SETPARAM, (LPVOID)&FeatureValue, sizeof(DCAM_PARAM_FEATURE)))
         return ReportError("Error in dcam_extended (set gain): ");
   }
   else if (eAct == MM::BeforeGet)
   {
      if (!dcam_extended(m_hDCAM, DCAM_IDMSG_GETPARAM, (LPVOID)&FeatureValue, sizeof(DCAM_PARAM_FEATURE)))
         return ReportError("Error in dcam_extended (get gain): ");
      pProp->Set(FeatureValue.featurevalue);
   }
   return DEVICE_OK;
}

// Gamma
int CHamamatsu::OnGamma(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	DCAM_PARAM_FEATURE FeatureValue;
   ZeroMemory((LPVOID)&FeatureValue, sizeof(DCAM_PARAM_FEATURE));
   FeatureValue.hdr.cbSize = sizeof(DCAM_PARAM_FEATURE);
	FeatureValue.hdr.id = (DWORD)DCAM_IDPARAM_FEATURE;
   FeatureValue.featureid = DCAM_IDFEATURE_GAMMA;	// same as DCAM_IDFEATURE_LIGHTMODE

   if (eAct == MM::AfterSet)
   {
      long gamma;
      pProp->Get(gamma);
      FeatureValue.featurevalue = (float)gamma;
      if (!dcam_extended(m_hDCAM, DCAM_IDMSG_SETPARAM, (LPVOID)&FeatureValue, sizeof(DCAM_PARAM_FEATURE)))
         return ReportError("Error in dcam_extended (set gamma): ");
   }
   else if (eAct == MM::BeforeGet)
   {
      if (!dcam_extended(m_hDCAM, DCAM_IDMSG_GETPARAM, (LPVOID)&FeatureValue, sizeof(DCAM_PARAM_FEATURE)))
         return ReportError("Error in dcam_extended (get gamma): ");
      pProp->Set((long)FeatureValue.featurevalue);
   }
   return DEVICE_OK;
}


// Offset
int CHamamatsu::OnOffset(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	DCAM_PARAM_FEATURE FeatureValue;
   ZeroMemory((LPVOID)&FeatureValue, sizeof(DCAM_PARAM_FEATURE));
   FeatureValue.hdr.cbSize = sizeof(DCAM_PARAM_FEATURE);
	FeatureValue.hdr.id = (DWORD) DCAM_IDPARAM_FEATURE;
   FeatureValue.featureid = DCAM_IDFEATURE_OFFSET;

   if (eAct == MM::AfterSet)
   {
      long lnOffset;
      pProp->Get(lnOffset);
      FeatureValue.featurevalue = (float)lnOffset;
      if (!dcam_extended(m_hDCAM, DCAM_IDMSG_SETPARAM, (LPVOID)&FeatureValue, sizeof(DCAM_PARAM_FEATURE)))
         return ReportError("Error in dcam_extended (set offset): ");
   }
   else if (eAct == MM::BeforeGet)
   {
      if (!dcam_extended(m_hDCAM, DCAM_IDMSG_GETPARAM, (LPVOID)&FeatureValue, sizeof(DCAM_PARAM_FEATURE)))
         return ReportError("Error in dcam_extended (get offset): ");
      pProp->Set(FeatureValue.featurevalue);
   }
   return DEVICE_OK;
}

// Extended
int CHamamatsu::OnExtended(MM::PropertyBase* pProp, MM::ActionType eAct, long featureId)
{
	DCAM_PARAM_FEATURE FeatureValue;
   ZeroMemory((LPVOID)&FeatureValue, sizeof(DCAM_PARAM_FEATURE));
   FeatureValue.hdr.cbSize = sizeof(DCAM_PARAM_FEATURE);
	FeatureValue.hdr.id = (DWORD) DCAM_IDPARAM_FEATURE;
   FeatureValue.featureid = (int) featureId;

   if (eAct == MM::AfterSet)
   {
      long value;
      pProp->Get(value);
      FeatureValue.featurevalue = (float)value;
      if (!dcam_extended(m_hDCAM, DCAM_IDMSG_SETPARAM, (LPVOID)&FeatureValue, sizeof(DCAM_PARAM_FEATURE)))
         return ReportError("Error in dcam_extended (set value): ");
   }
   else if (eAct == MM::BeforeGet)
   {
      if (!dcam_extended(m_hDCAM, DCAM_IDMSG_GETPARAM, (LPVOID)&FeatureValue, sizeof(DCAM_PARAM_FEATURE)))
         return ReportError("Error in dcam_extended (get value): ");
      pProp->Set(FeatureValue.featurevalue);
   }
   return DEVICE_OK;
}

// Temperature
int CHamamatsu::OnTemperature(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	DCAM_PARAM_FEATURE FeatureValue;
   ZeroMemory((LPVOID)&FeatureValue, sizeof(DCAM_PARAM_FEATURE));
   FeatureValue.hdr.cbSize = sizeof(DCAM_PARAM_FEATURE);
	FeatureValue.hdr.id = (DWORD)DCAM_IDPARAM_FEATURE;
   FeatureValue.featureid = DCAM_IDFEATURE_TEMPERATURE;

   if (eAct == MM::AfterSet)
   {
      double dT;
      pProp->Get(dT);
      FeatureValue.featurevalue = (float)dT;
      if (!dcam_extended(m_hDCAM, DCAM_IDMSG_SETPARAM, (LPVOID)&FeatureValue, sizeof(DCAM_PARAM_FEATURE)))
         return ReportError("Error in dcam_extended (set dT): ");
   }
   else if (eAct == MM::BeforeGet)
   {
      if (!dcam_extended(m_hDCAM, DCAM_IDMSG_GETPARAM, (LPVOID)&FeatureValue, sizeof(DCAM_PARAM_FEATURE)))
      {
         unsigned int errCode = dcam_getlasterror(m_hDCAM, NULL, 0);

         // this function may not be supported in older cameras
         if (errCode != DCAMERR_NOTSUPPORT) {
            ostringstream os;
            os << "Error in dcam_extended (get dT): " << errCode;
            LogMessage(os.str().c_str());
            return errCode;
         } else
            FeatureValue.featurevalue = 0.0; // default to zero if function not supported
      }
      pProp->Set(FeatureValue.featurevalue);
   }
   return DEVICE_OK;
}

int CHamamatsu::OnSlot(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      pProp->Get(slot_);
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set(slot_);
   }
   return DEVICE_OK;
}
///////////////////////////////////////////////////////////////////////////////
// API methods
// ~~~~~~~~~~~

void CHamamatsu::GetName(char* name) const 
{
   CDeviceUtils::CopyLimitedString(name, g_DeviceName);
}

long CHamamatsu::ReportError(std::string message) {
    long err = dcam_getlasterror(m_hDCAM, NULL, 0);
    ostringstream os;
    os << message << err;
    LogMessage(os.str().c_str());
    return err;
}

///////////////////////////////////////////////////////////////////////////////
// Function name   : CHamamatsu::Initialize
// Description     : Initialize the camera
// Return type     : bool 

int CHamamatsu::Initialize()
{
   // setup the camera
   // ----------------
   if (m_bInitialized)
      return DEVICE_OK;

   // Name
   int nRet = CreateProperty(MM::g_Keyword_Name, g_DeviceName, MM::String, true);
   assert(nRet == DEVICE_OK);

   // Description
   nRet = CreateProperty(MM::g_Keyword_Description, "Hamamatsu DCAM API device adapter", MM::String, true);
   assert(nRet == DEVICE_OK);

   // initialize the DCAM dll
#ifdef WIN32
      if (m_hDCAMModule == 0)
         m_hDCAMModule = ::GetModuleHandle(NULL);
#else
      m_hDCAMModule = NULL;
#endif
      long lnCameras(0);

      if (refCount_ == 0)
      {
	      if (!dcam_init(m_hDCAMModule, &lnCameras, NULL) )
            return ERR_NO_CAMERA_FOUND;
      }
      refCount_++;

   if (lnCameras < 1) {
      LogMessage("No DCAM camera found");
      return ERR_NO_CAMERA_FOUND;
   }

   // DCAM INFO
   char moduleVersion [64] = "Unrecognized";
   if (!dcam_getmodelinfo(slot_, DCAM_IDSTR_MODULEVERSION, moduleVersion, sizeof(moduleVersion)))
      LogMessage ("Error obtaining moduleVersion");
   else { 
      char msg[128] = "DCAM Module Version: ";
      LogMessage (strcat(msg, moduleVersion));
   }

   // DCAM API INFO
   char dcamAPIVersion [64] = "Unrecognized";
   if (!dcam_getmodelinfo(slot_, DCAM_IDSTR_MODULEVERSION, dcamAPIVersion, sizeof(dcamAPIVersion)))
      LogMessage ("Error obtaining DCAMP API Version");
   else  {
      char msg[128] = "DCAM API Version: ";
      LogMessage (strcat(msg, dcamAPIVersion));
   }

   // gather information about the equipment
   // ------------------------------------------

   if(!dcam_open(&m_hDCAM, slot_, NULL) && !m_hDCAM)
       return DEVICE_NATIVE_MODULE_FAILED;

   // verify buffer mode
	DWORD	cap;
	if(!dcam_getcapability(m_hDCAM, &cap, DCAM_QUERYCAPABILITY_FUNCTIONS))
       return ReportError("Error in getcapability: ");

   // We are currently not using user memory.  Log it anyways
	if(!(cap & DCAM_CAPABILITY_USERMEMORY))
      LogMessage("This camera does not support user memory");

   // Some cameras do not like setting software trigger mode later on. Setting it now helps
	if(!(cap & DCAM_CAPABILITY_TRIGGER_SOFTWARE)) {
      LogMessage("This camera does not support software triggers");
	} else {    
		if (!dcam_settriggermode(m_hDCAM, DCAM_TRIGMODE_SOFTWARE)) {
			LogMessage("This camera says it supports software triggers, but when I try to set it, the camera fails");
		}
		else {
	       softwareTriggerEnabled_ = true;
		}
	}

   // CameraName
   char	CameraName[64] = "Unrecognized";
   
   if (!dcam_getstring(m_hDCAM, DCAM_IDSTR_MODEL, CameraName, sizeof(CameraName)))
   {
      unsigned int errCode = dcam_getlasterror(m_hDCAM, NULL, 0);

      // this function may not be supported in older cameras
      if (errCode != DCAMERR_NOTSUPPORT) {
         LogMessage ("Error getting the Name of the camera");
         return errCode;
      }
   }
   char msg[128] =  "Camera Model: ";
   LogMessage (strcat(msg, CameraName)); 
   nRet = CreateProperty(MM::g_Keyword_CameraName, CameraName, MM::String, true);
   assert(nRet == DEVICE_OK);
		      
   // CameraID
   char	CameraID[64];
   if (!dcam_getstring(m_hDCAM, DCAM_IDSTR_CAMERAID, CameraID, sizeof(CameraID)))
      strcpy(CameraID, "Not available");
   strcpy (msg, "CameraID: ");
   LogMessage (strcat(msg, CameraID)); 
   nRet = CreateProperty(MM::g_Keyword_CameraID, CameraID, MM::String, true);
   assert(nRet == DEVICE_OK);

   // Camera Version
   char	CameraVersion[64];
   if (!dcam_getstring(m_hDCAM, DCAM_IDSTR_CAMERAVERSION, CameraVersion, sizeof(CameraVersion)))
      strcpy(CameraVersion, "Not available");
   strcpy (msg, "Camera Version: ");
   LogMessage (strcat(msg, CameraVersion)); 
   nRet = CreateProperty("Camera Version", CameraVersion, MM::String, true);
   assert(nRet == DEVICE_OK);

   // Driver Version
   char	DriverVersion[64];
   if (!dcam_getstring(m_hDCAM, DCAM_IDSTR_DRIVERVERSION, DriverVersion, sizeof(DriverVersion)))
      strcpy(DriverVersion, "Not available");
   strcpy (msg, "Driver version: ");
   LogMessage (strcat(msg, DriverVersion)); 
   nRet = CreateProperty("Driver Version", DriverVersion, MM::String, true);
   assert(nRet == DEVICE_OK);

   // setup image parameters
   // ----------------------

   // binning
   CPropertyAction *pAct = new CPropertyAction (this, &CHamamatsu::OnBinning);
   nRet = CreateProperty(MM::g_Keyword_Binning, "1", MM::Integer, false, pAct);
   assert(nRet == DEVICE_OK);
   nRet = SetAllowedBinValues(cap);
   assert (nRet == DEVICE_OK);

   // trigger mode
   pAct = new CPropertyAction (this, &CHamamatsu::OnTrigMode);
   if (triggerMode_.compare(g_TrigMode_Software) == 0)
      nRet = CreateProperty(g_TrigMode, g_TrigMode_Software, MM::String, false, pAct);
   else
      nRet = CreateProperty(g_TrigMode, g_TrigMode_Internal, MM::String, false, pAct);
   assert(nRet == DEVICE_OK);
   nRet = SetAllowedTrigModeValues(cap);
   assert (nRet == DEVICE_OK);

   // trigger polarity
   pAct = new CPropertyAction (this, &CHamamatsu::OnTrigPolarity);
   nRet = CreateProperty("TriggerPolarity", g_TrigPolarity_Positive, MM::String, false, pAct);
   assert(nRet == DEVICE_OK);
   AddAllowedValue("TriggerPolarity", g_TrigPolarity_Positive);
   AddAllowedValue("TriggerPolarity", g_TrigPolarity_Negative);


   // pixel type
   pAct = new CPropertyAction (this, &CHamamatsu::OnPixelType);
   nRet = CreateProperty(MM::g_Keyword_PixelType, CameraID, MM::String, false, pAct);
   assert(nRet == DEVICE_OK);

	DWORD	typeCap;
   if(!dcam_getcapability(m_hDCAM, &typeCap, DCAM_QUERYCAPABILITY_DATATYPE))
       ReportError("Error in getcapability QueryCapability_DataType");

   vector<string> pixelTypeValues;
	if(typeCap & ccDatatype_uint8)
      pixelTypeValues.push_back(g_PixelType_8bit);
 	if(typeCap & ccDatatype_uint16)
      pixelTypeValues.push_back(g_PixelType_16bit);
   nRet = SetAllowedValues(MM::g_Keyword_PixelType, pixelTypeValues);
   if (nRet != DEVICE_OK)
      return nRet;

   // exposure
   pAct = new CPropertyAction (this, &CHamamatsu::OnExposure);
   nRet = CreateProperty(MM::g_Keyword_Exposure, "10.0", MM::Float, false, pAct);
   assert(nRet == DEVICE_OK);

   // scan mode
   int32_t maxSpeed;
   if (IsScanModeSupported(maxSpeed) && (maxSpeed > 1)) 
   {
      pAct = new CPropertyAction (this, &CHamamatsu::OnScanMode);
      nRet = CreateProperty("ScanMode", "1", MM::Integer, false, pAct);
      // assume scanmode starts at 1 (seems so, but undocumented)
      if (maxSpeed < 10) {
         for (int i=0; i< maxSpeed; i++) {
            ostringstream os;
            os << (i+1);
            AddAllowedValue("ScanMode", os.str().c_str());
         }
      } else {
           SetPropertyLimits("ScanMode", 1, maxSpeed);
      }

      assert(nRet == DEVICE_OK);
   }
   
   // CCDMode
   DCAM_PROPERTYATTR propAttr;
   if (IsPropertySupported(propAttr, DCAM_IDPROP_CCDMODE))
   {
      ostringstream defaultValue;
      defaultValue << propAttr.valuedefault;
      pAct = new CPropertyAction (this, &CHamamatsu::OnCCDMode);
      nRet = CreateProperty("CCDMode", defaultValue.str().c_str(), MM::Integer, false, pAct);
      if (nRet != DEVICE_OK)
         return nRet;
      nRet = SetAllowedPropValues(propAttr, "CCDMode");
      if (nRet != DEVICE_OK)
         return nRet;
   }

   // Photon Imaging Mode
   if (IsPropertySupported(propAttr, DCAM_IDPROP_PHOTONIMAGINGMODE))
   {
      ostringstream defaultValue;
      defaultValue << propAttr.valuedefault;
      pAct = new CPropertyAction (this, &CHamamatsu::OnPhotonImagingMode);
      nRet = CreateProperty("PhotonImagingMode", defaultValue.str().c_str(), MM::Integer, false, pAct);
      if (nRet != DEVICE_OK)
         return nRet;
      nRet = SetAllowedPropValues(propAttr, "PhotonImagingMode");
      if (nRet != DEVICE_OK)
         return nRet;
   }

   // Sensivity (=EM GAIN?)
   if (IsFeatureSupported(DCAM_IDFEATURE_SENSITIVITY))
   {
      DCAM_PARAM_FEATURE_INQ featureInq = GetFeatureInquiry(DCAM_IDFEATURE_SENSITIVITY);
      CPropertyActionEx* pActEx = new CPropertyActionEx (this, &CHamamatsu::OnExtended, (long) DCAM_IDFEATURE_SENSITIVITY);
      ostringstream defaultValue;
      defaultValue << featureInq.defaultvalue;
      if (featureInq.step == 1.0)
         nRet = CreateProperty("Sensitivity", defaultValue.str().c_str(), MM::Integer, false, pActEx);
      else
         nRet = CreateProperty("Sensitivity", defaultValue.str().c_str(), MM::Float, false, pActEx);
      assert(nRet == DEVICE_OK);
      if (featureInq.max > featureInq.min)
      {
         nRet = SetPropertyLimits("Sensitivity", featureInq.min, featureInq.max);
      }
   }

   // camera gain
   if (IsFeatureSupported(DCAM_IDFEATURE_GAIN)) 
   {
      pAct = new CPropertyAction (this, &CHamamatsu::OnGain);
      DCAM_PARAM_FEATURE_INQ featureInq = GetFeatureInquiry(DCAM_IDFEATURE_GAIN);
      ostringstream defaultValue;
      defaultValue << featureInq.defaultvalue;
	   if (featureInq.step == 1.0)      
         nRet = CreateProperty(MM::g_Keyword_Gain, defaultValue.str().c_str(), MM::Integer, false, pAct);
      else
         nRet = CreateProperty(MM::g_Keyword_Gain, defaultValue.str().c_str(), MM::Float, false, pAct);
      assert(nRet == DEVICE_OK);

      SetAllowedGainValues(featureInq);
   }

   // camera gamma						
   if (IsFeatureSupported(DCAM_IDFEATURE_GAMMA))
   {
      pAct = new CPropertyAction (this, &CHamamatsu::OnGamma);
      nRet = CreateProperty("Gamma", "0", MM::Integer, false, pAct);
      assert(nRet == DEVICE_OK);

      vector<string> gammaValues;
      gammaValues.push_back("0");
      gammaValues.push_back("1");
      SetAllowedValues("Gamma", gammaValues);
   }
   
   // other camera parameters
   // -----------------------

   // readout time
   pAct = new CPropertyAction (this, &CHamamatsu::OnReadoutTime);
   nRet = CreateProperty(MM::g_Keyword_ReadoutTime, "0", MM::Float, true, pAct);
   assert(nRet == DEVICE_OK);

   // Actual Interval
   pAct = new CPropertyAction (this, &CHamamatsu::OnActualIntervalMs);
   nRet = CreateProperty(MM::g_Keyword_ActualInterval_ms, "0.0", MM::Float, true, pAct);


   // camera offset
   // for some types of cameras (like ORCA-II ER) reading the offset generates
   // an error when scan mode is 1.
   // Offset property is temporarily disabled
   //pAct = new CPropertyAction (this, &CHamamatsu::OnOffset);
   //nRet = CreateProperty(MM::g_Keyword_Offset, "1", MM::Integer, false, pAct);
   //assert(nRet == DEVICE_OK);

   // camera temperature
   if (IsFeatureSupported(DCAM_IDFEATURE_TEMPERATURE))
   {
      pAct = new CPropertyAction (this, &CHamamatsu::OnTemperature);
      nRet = CreateProperty(MM::g_Keyword_CCDTemperature, "1", MM::Float, false, pAct);
      assert(nRet == DEVICE_OK);
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

   // We seem to need this on the Mac...
   SetProperty(MM::g_Keyword_Binning,"1");

   m_bInitialized = true;
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Function name   : CHamamatsu::Shutdown
// Description     : Deactivate the camera, reverse the initialization process
// Return type     : bool 

int CHamamatsu::Shutdown()
{
   if (m_bInitialized)
   {
      if(!dcam_idle(m_hDCAM))
         return ReportError("Error in dcam_idle: ");

      if (!dcam_freeframe(m_hDCAM))
         return ReportError("Error in dcam_freeframe: ");

      if (!dcam_close(m_hDCAM))
         return ReportError("Error in dcam_close: ");
   
      if (refCount_ > 0)
      {
         refCount_--;
         if (refCount_== 0)
         {
            if (!dcam_uninit(m_hDCAMModule))
               return ReportError("Error in dcam_uninit: ");
         }
      }
      else
         refCount_ = 0; //in case refCount_ gets less then 0

   }
   m_bInitialized = false;
   if (refCount_ > 0)
      refCount_--;
   if (refCount_ == 0)
   {
      // clear the instance pointer
      m_hDCAMModule = 0;
   }
   return DEVICE_OK;
}

bool CHamamatsu::Busy()
{
   return (acquiring_ || snapInProgress_);
}

///////////////////////////////////////////////////////////////////////////////
// Function name   : CHamamatsu::SnapImage
// Description     : Acquires a single frame and stores it in the internal
//                   buffer. This command blocks the calling thread untile the
//                   image is fully captured.
// Return type     : bool 

int CHamamatsu::SnapImage()
{
   if (snapInProgress_)
   {
      GetImageBuffer();
      return ERR_INCOMPLETE_SNAP_IMAGE_CYCLE;
   }

   double dExp;
   if (!dcam_getexposuretime(m_hDCAM, &dExp))
      dcam_getlasterror(m_hDCAM, NULL, 0);

   // start capture
   // TODO: figure out what to do with Internal triggers
   if (triggerMode_.compare(g_TrigMode_Software) == 0)
      if (!dcam_firetrigger(m_hDCAM))
         return dcam_getlasterror(m_hDCAM, NULL, 0);

   snapInProgress_ = true;
#ifdef WIN32
   Sleep((DWORD)(dExp*1000.0));
#else
   usleep(dExp*1000000.0);
#endif

   return DEVICE_OK;
}

double CHamamatsu::GetExposure() const
{
   char Buf[MM::MaxStrLength];
   Buf[0] = '\0';
   GetProperty(MM::g_Keyword_Exposure, Buf);
   return atof(Buf);
}
void CHamamatsu::SetExposure(double dExp)
{
   SetProperty(MM::g_Keyword_Exposure, CDeviceUtils::ConvertToString(dExp));
}

///////////////////////////////////////////////////////////////////////////////
// Function name   : char* CHamamatsu::GetImageBuffer
// Description     : Returns the raw image buffer
// Return type     : const unsigned 

const unsigned char* CHamamatsu::GetImageBuffer()
{
    // wait until the frame becomes available
   double dExp;
   if (!dcam_getexposuretime(m_hDCAM, &dExp))
      return 0;
      // return dcam_getlasterror(m_hDCAM, NULL, 0);
   
   long lnTimeOut = (long) ((dExp + 5.0) * 1000.0);
   
   DWORD dwEvent = DCAM_EVENT_FRAMEEND;
   if (!dcam_wait(m_hDCAM, &dwEvent, lnTimeOut, NULL))
   {
      long lnLastErr = dcam_getlasterror(m_hDCAM, NULL, 0);
      if (lnLastErr != ccErr_none)
         return 0;
         //return lnLastErr;
   }

   // get pixels
   void* src;
   long sRow;
   dcam_lockdata(m_hDCAM, &src, &sRow, -1);
   void* pixBuffer = const_cast<unsigned char*> (img_.GetPixels());
   memcpy(pixBuffer, src, GetImageBufferSize());
   dcam_unlockdata(m_hDCAM);

   snapInProgress_ = false;

   // capture complete
   return (unsigned char*)pixBuffer;
}

int CHamamatsu::SetROI(unsigned uX, unsigned uY, unsigned uXSize, unsigned uYSize)
{
   int ret = ShutdownImageBuffer();
   if (ret != DEVICE_OK)
      return ret;

   // inquire about capabilities
   DCAM_PARAM_SUBARRAY_INQ SubArrayInquiry;
   ZeroMemory((LPVOID)&SubArrayInquiry,sizeof(DCAM_PARAM_SUBARRAY_INQ));
	SubArrayInquiry.hdr.cbSize = sizeof(DCAM_PARAM_SUBARRAY_INQ);
	SubArrayInquiry.hdr.id = (DWORD)DCAM_IDPARAM_SUBARRAY_INQ;
   SubArrayInquiry.binning=lnBin_;
						
	if (!dcam_extended(m_hDCAM,DCAM_IDMSG_GETPARAM,(LPVOID)&SubArrayInquiry,sizeof(DCAM_PARAM_SUBARRAY_INQ)))
      return ReportError("Error in dcam_extended (subarry_inq): ");

	DCAM_PARAM_SUBARRAY SubArrayValue;
	ZeroMemory((LPVOID)&SubArrayValue, sizeof(DCAM_PARAM_SUBARRAY));
	SubArrayValue.hdr.cbSize = sizeof(DCAM_PARAM_SUBARRAY);
	SubArrayValue.hdr.id = (DWORD)DCAM_IDPARAM_SUBARRAY;

   unsigned newX = uX - (uX % SubArrayInquiry.hposunit);
   unsigned newY = uY - (uY % SubArrayInquiry.vposunit);
   unsigned newXSize = uXSize - (uXSize % SubArrayInquiry.hunit);
   unsigned newYSize = uYSize - (uYSize % SubArrayInquiry.vunit);

	// set new SubArray settings
	SubArrayValue.hpos = newX;
	SubArrayValue.hsize = newXSize;
	SubArrayValue.vpos = newY;
	SubArrayValue.vsize = newYSize;

   if (!dcam_extended(m_hDCAM, DCAM_IDMSG_SETPARAM, (LPVOID)&SubArrayValue, sizeof(DCAM_PARAM_SUBARRAY)))
      return ReportError("Error in dcam_extended (subarry): ");

   ret = ResizeImageBuffer();
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

unsigned CHamamatsu::GetBitDepth() const
{
   if (img_.Depth() == 1)
      return 8;
   else if (img_.Depth() == 2)
   {
      long minVal(0), maxVal(0);
      if (!dcam_getdatarange(m_hDCAM, &maxVal, &minVal))
         return 16; // default for two-byte pixels

      if (maxVal < 1024)
         return 10;
      else if (maxVal < 4096)
         return 12;
      else if (maxVal < 16384)
         return 14;
      else
         return 16;
   }
   else
   {
      // LogMessage("unsupported bytes per pixel count");
      return 0; // should not happen
   }
}

int CHamamatsu::GetROI(unsigned& uX, unsigned& uY, unsigned& uXSize, unsigned& uYSize)
{
	// SubArray Set/Get Structure
	DCAM_PARAM_SUBARRAY SubArrayValue;

	ZeroMemory((LPVOID)&SubArrayValue, sizeof(DCAM_PARAM_SUBARRAY));
	SubArrayValue.hdr.cbSize = sizeof(DCAM_PARAM_SUBARRAY);
	SubArrayValue.hdr.id = (DWORD)DCAM_IDPARAM_SUBARRAY;
						
   if (!dcam_extended(m_hDCAM, DCAM_IDMSG_GETPARAM,(LPVOID)&SubArrayValue, sizeof(DCAM_PARAM_SUBARRAY)))
      return ReportError("Error in dcam_extended (subarry): ");

   uX = SubArrayValue.hpos;
   uY = SubArrayValue.vpos;
   uXSize = SubArrayValue.hsize;
   uYSize = SubArrayValue.vsize;

   return DEVICE_OK;
}

int CHamamatsu::ClearROI()
{
  // inquire about capabilities
   DCAM_PARAM_SUBARRAY_INQ SubArrayInquiry;
   ZeroMemory((LPVOID)&SubArrayInquiry,sizeof(DCAM_PARAM_SUBARRAY_INQ));
	SubArrayInquiry.hdr.cbSize = sizeof(DCAM_PARAM_SUBARRAY_INQ);
	SubArrayInquiry.hdr.id = (DWORD)DCAM_IDPARAM_SUBARRAY_INQ;
   SubArrayInquiry.binning=lnBin_;
						
	if (!dcam_extended(m_hDCAM,DCAM_IDMSG_GETPARAM,(LPVOID)&SubArrayInquiry,sizeof(DCAM_PARAM_SUBARRAY_INQ)))
      return ReportError("Error in dcam_extended (subarry_inq): ");

   // reset the frame
   int ret = SetROI(0, 0, SubArrayInquiry.hmax, SubArrayInquiry.vmax);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

int CHamamatsu::SetBinning(int binSize)
{
   ostringstream os;
   os << binSize;
   return SetProperty (MM::g_Keyword_Binning, os.str().c_str());
}

///////////////////////////////////////////////////////////////////////////////
// Utility methods
///////////////////////////////////////////////////////////////////////////////

int CHamamatsu::ResizeImageBuffer(long frameBufSize)
{
   // Set the soft trigger mode 
   // TODO: revisit trigger issue
   /*
   if (softwareTriggerCapable_) 
   {
      if (!dcam_settriggermode(m_hDCAM, DCAM_TRIGMODE_SOFTWARE))
      {
         LogMessage("dcam_settriggermode");
         return dcam_getlasterror(m_hDCAM, NULL, 0);
      }
   } else {
      if (!dcam_settriggermode(m_hDCAM, DCAM_TRIGMODE_INTERNAL))
      {
         LogMessage("dcam_settriggermode");
         return dcam_getlasterror(m_hDCAM, NULL, 0);
      }
   }
   */

   // resize internal buffers
   SIZE imgSize;
   if (!dcam_getdatasize(m_hDCAM,&imgSize))
      return ReportError("Error in dcam_getdatasize: ");

   ccDatatype ccDataType;
   if (!dcam_getdatatype(m_hDCAM,&ccDataType))
      return ReportError("Error in dcam_getdatatype: ");

#ifdef WIN32
   // format the image buffer
   if (ccDataType == ccDatatype_uint8 || ccDataType == ccDatatype_int8)
   {
      img_.Resize(imgSize.cx, imgSize.cy, 1);
   }
   else if (ccDataType == ccDatatype_uint16 || ccDataType == ccDatatype_int16)
   {
      img_.Resize(imgSize.cx, imgSize.cy, 2);
   }
   else
   {
      // we do not support color images at this time
      return DEVICE_UNSUPPORTED_DATA_FORMAT;
   }

#else
   if (ccDataType == ccDatatype_uint8 || ccDataType == ccDatatype_int8)
   {
      img_.Resize(LoWord(imgSize), HiWord(imgSize), 1);
   }
   else if (ccDataType == ccDatatype_uint16 || ccDataType == ccDatatype_int16)
   {
      img_.Resize(LoWord(imgSize), HiWord(imgSize), 2);
   }
   else
   {
      // we do not support color images at this time
      return DEVICE_UNSUPPORTED_DATA_FORMAT;
   }
#endif

   // setup the sequence capture mode
   if (!dcam_precapture(m_hDCAM, ccCapture_Sequence))
      return ReportError("Error in dcam_precapture: ");

   // set a 3 frame sequence buffercapt
   //const long frameBufSize = 3;
   if (!dcam_freeframe(m_hDCAM))
      return ReportError("Error in dcam_freeframe: ");

   if (!dcam_allocframe(m_hDCAM, frameBufSize))
      return ReportError("Error in dcam_allocframe: ");

   long numFrames;
   if (!dcam_getframecount(m_hDCAM, &numFrames))
      return ReportError("Error in dcam_getframecount: ");

   if (numFrames != frameBufSize)
      return ERR_BUFFER_ALLOCATION_FAILED;

   DWORD dwDataBufferSize;
   if (!dcam_getdataframebytes(m_hDCAM, &dwDataBufferSize))
      return ReportError("Error in dcam_getdataframebytes: ");

   if (img_.Height() * img_.Width() * img_.Depth() != dwDataBufferSize)
      return DEVICE_INTERNAL_INCONSISTENCY; // buffer sizes don't match ???

   // start capture
   if (!dcam_capture(m_hDCAM))
      return ReportError("Error in dcam_capture: ");

   return DEVICE_OK;
}

int CHamamatsu::ResizeImageBuffer()
{
   return ResizeImageBuffer(3);
}

int CHamamatsu::ShutdownImageBuffer()
{
   // interrupt whatever the camera is currently doing
   if (!dcam_idle(m_hDCAM))
      return ReportError("Error in dcam_idle: ");

   if (!dcam_freeframe(m_hDCAM))
      return ReportError("Error in dcam_freeframe: ");

   return DEVICE_OK;
}

bool CHamamatsu::IsFeatureSupported(int featureId)
{
   // inquire about capabilities
   DCAM_PARAM_FEATURE_INQ featureInquiry;
   ZeroMemory((LPVOID)&featureInquiry,sizeof(DCAM_PARAM_FEATURE_INQ));
	featureInquiry.hdr.cbSize = sizeof(DCAM_PARAM_FEATURE_INQ);
	featureInquiry.hdr.id = (DWORD)DCAM_IDPARAM_FEATURE_INQ;
	featureInquiry.hdr.oFlag = 0;
   featureInquiry.featureid = featureId;
						
   return dcam_extended(m_hDCAM, DCAM_IDMSG_GETPARAM,(LPVOID)&featureInquiry, sizeof(DCAM_PARAM_FEATURE_INQ)) == TRUE ? true : false;
}

bool CHamamatsu::IsScanModeSupported(int32_t& maxSpeed)
{
   // inquire about capabilities
   DCAM_PARAM_SCANMODE_INQ featureInquiry;
   ZeroMemory((LPVOID)&featureInquiry,sizeof(DCAM_PARAM_SCANMODE_INQ));
	featureInquiry.hdr.cbSize = sizeof(DCAM_PARAM_SCANMODE_INQ);
	featureInquiry.hdr.id = (DWORD)DCAM_IDPARAM_SCANMODE_INQ;
	featureInquiry.hdr.iFlag = dcamparam_scanmodeinq_speedmax;
	featureInquiry.hdr.oFlag = 0;
						
   // Kay Schink reported that dcam_extended failed with ScanMode whereas it worked with featureInquiry.  Add the second test here to catch those issue (might hide the real problem though)..
   DCAM_PARAM_SCANMODE ScanMode;
   ZeroMemory((LPVOID)&ScanMode, sizeof(DCAM_PARAM_SCANMODE));
   ScanMode.hdr.cbSize = sizeof(DCAM_PARAM_SCANMODE);
   ScanMode.hdr.id = (DWORD) DCAM_IDPARAM_SCANMODE;

   if (dcam_extended(m_hDCAM, DCAM_IDMSG_GETPARAM,(LPVOID)&featureInquiry, sizeof(DCAM_PARAM_SCANMODE_INQ)) == TRUE && dcam_extended(m_hDCAM,DCAM_IDMSG_GETPARAM, (LPVOID)&ScanMode, sizeof(DCAM_PARAM_SCANMODE)) == TRUE) {
      maxSpeed = featureInquiry.speedmax;
      return true;
   }
   return false;
}

bool CHamamatsu::IsPropertySupported(DCAM_PROPERTYATTR& propAttr, long property)
{
   memset(&propAttr, 0, sizeof(propAttr));
   propAttr.cbSize = sizeof(propAttr);
   propAttr.iProp = property;
   if (dcam_getpropertyattr(m_hDCAM, &propAttr))
      return true;
   return false;
}


int CHamamatsu::SetAllowedBinValues(DWORD cap)
{
   vector<string> binValues;
   binValues.push_back("1");
   if (cap & DCAM_CAPABILITY_BINNING2)
      binValues.push_back("2");
   if (cap & DCAM_CAPABILITY_BINNING4)
      binValues.push_back("4");
   if (cap & DCAM_CAPABILITY_BINNING8)
      binValues.push_back("8");
   if (cap & DCAM_CAPABILITY_BINNING16)
      binValues.push_back("16");
   if (cap & DCAM_CAPABILITY_BINNING32)
      binValues.push_back("32");
   return SetAllowedValues(MM::g_Keyword_Binning, binValues);
}

int CHamamatsu::SetAllowedTrigModeValues(DWORD cap)
{
   vector<string>trigValues;
   //if (cap & DCAM_CAPABILITY_TRIGGER_INTERNAL)
   trigValues.push_back(g_TrigMode_Internal);
   if (cap & DCAM_CAPABILITY_TRIGGER_EDGE)
      trigValues.push_back(g_TrigMode_Edge);
   if (cap & DCAM_CAPABILITY_TRIGGER_LEVEL)
      trigValues.push_back(g_TrigMode_Level);
   if (cap & DCAM_CAPABILITY_TRIGGER_MULTISHOT_SENSITIVE)
      trigValues.push_back(g_TrigMode_MultiShot_Sensitive);
   if (cap & DCAM_CAPABILITY_TRIGGER_CYCLE_DELAY)
      trigValues.push_back(g_TrigMode_Cycle_Delay);
   if (cap & DCAM_CAPABILITY_TRIGGER_SOFTWARE)
      trigValues.push_back(g_TrigMode_Software);
   if (cap & DCAM_CAPABILITY_TRIGGER_FASTREPETITION)
      trigValues.push_back(g_TrigMode_FastRepetition);
   if (cap & DCAM_CAPABILITY_TRIGGER_TDI)
      trigValues.push_back(g_TrigMode_TDI);
   if (cap & DCAM_CAPABILITY_TRIGGER_TDIINTERNAL)
      trigValues.push_back(g_TrigMode_TDIInternal);
   if (cap & DCAM_CAPABILITY_TRIGGER_START)
      trigValues.push_back(g_TrigMode_Start);
   if (cap & DCAM_CAPABILITY_TRIGGER_SYNCREADOUT)
      trigValues.push_back(g_TrigMode_SyncReadout);
   return SetAllowedValues(g_TrigMode, trigValues);
}

int CHamamatsu::SetAllowedGainValues(DCAM_PARAM_FEATURE_INQ featureInq)
{
   int ret;
   vector<std::string> values;
   // first clear the list
   SetAllowedValues(MM::g_Keyword_Gain, values);
   if ((featureInq.max > featureInq.min) && ((featureInq.max-featureInq.min) < 10) && featureInq.step == 1.0)
   {
      for (int i = (int) featureInq.min; i <= (int) featureInq.max; i++) {
         ostringstream value;
         value << i;
         ret = AddAllowedValue(MM::g_Keyword_Gain, value.str().c_str());
         if (ret != DEVICE_OK)
            return ret;
      }
   } else  if (featureInq.max > featureInq.min) 
   {
      ret = SetPropertyLimits(MM::g_Keyword_Gain, featureInq.min, featureInq.max);
      if (ret != DEVICE_OK)
         return ret;
   } 
   return DEVICE_OK;
}
	
int CHamamatsu::SetAllowedPropValues(DCAM_PROPERTYATTR propAttr, std::string propName)
{
   int ret;
   vector<string>values;
   // clear existing values
   SetAllowedValues(propName.c_str(), values);
   // low number of values, make a list
   if ( (propAttr.valuemax >= propAttr.valuemin) && ((propAttr.valuemax - propAttr.valuemin) < 10) && (propAttr.valuestep == 1.0 || propAttr.valuestep==0.0) )
   {
      for (long i = (long) propAttr.valuemin; i<= (long) propAttr.valuemax; i++) {
         ostringstream value;
         value << i;
         values.push_back(value.str());
      }
      return SetAllowedValues(propName.c_str(), values);
   } else if (propAttr.valuemax > propAttr.valuemin)
      // higher number: set limits
   {
      ret = SetPropertyLimits(propName.c_str(), propAttr.valuemin, propAttr.valuemax);
      if (ret != DEVICE_OK)
         return ret;
   }
   return DEVICE_OK;
}

DCAM_PARAM_FEATURE_INQ CHamamatsu::GetFeatureInquiry(int featureId)
{
   // inquire about capabilities
   DCAM_PARAM_FEATURE_INQ featureInquiry;
   ZeroMemory((LPVOID)&featureInquiry,sizeof(DCAM_PARAM_FEATURE_INQ));
	featureInquiry.hdr.cbSize = sizeof(DCAM_PARAM_FEATURE_INQ);
	featureInquiry.hdr.id = (DWORD)DCAM_IDPARAM_FEATURE_INQ;
	featureInquiry.hdr.oFlag = 0;
   featureInquiry.featureid = featureId;
						
   dcam_extended(m_hDCAM, DCAM_IDMSG_GETPARAM,(LPVOID)&featureInquiry, sizeof(DCAM_PARAM_FEATURE_INQ));
   return featureInquiry;
}

///////////////////////////////////////////////////////////////////////////////
// Continuous acquisition
//

int AcqSequenceThread::svc(void)
{
   long imageCounter(0);

   double dExp;                                                                       
   if (!dcam_getexposuretime(camera_->m_hDCAM, &dExp))                                         
      return  dcam_getlasterror(camera_->m_hDCAM, NULL, 0);

   do
   {
       // wait until the frame becomes available
      long lnTimeOut = (long) ((dExp + 5.0) * 1000.0); 

      DWORD dwEvent = DCAM_EVENT_FRAMEEND; 
      if (!dcam_wait(camera_->m_hDCAM, &dwEvent, lnTimeOut, NULL))
      {            
         long lnLastErr = dcam_getlasterror(camera_->m_hDCAM, NULL, 0);
         if (lnLastErr != ccErr_none)
            return 0;
            //return lnLastErr;                                                          
      }
      int ret = camera_->PushImage();
      if (ret != DEVICE_OK)
      {
	      ostringstream os;
          os << "PushImage() failed with errorcode: " << ret;
		  camera_->LogMessage(os.str().c_str());
          camera_->StopSequenceAcquisition();
          return 2;
      }
      //printf("Acquired frame %ld.\n", imageCounter);                         
      imageCounter++;
   } while (!stop_ && imageCounter < numImages_);

   if (stop_)
   {
      printf("Acquisition interrupted by the user\n");
      return 0;
   }

   camera_->StopSequenceAcquisition();
   printf("Acquisition completed.\n");
   return 0;
}


/**
 * Starts continuous acquisition
 */
int CHamamatsu::StartSequenceAcquisition(long numImages, double interval_ms)
{
   if (acquiring_)
      return ERR_BUSY_ACQUIRING;

   // Switch from software to internal trigger, leave other trigger modes alone (needs to be done before shutting down image buffer)
   char trigMode[MM::MaxStrLength];
   int ret = GetProperty(g_TrigMode, trigMode);
   if (ret != DEVICE_OK)
	   return ret;
   if (strcmp(trigMode, g_TrigMode_Software) == 0)
   {
	   originalTrigMode_ = g_TrigMode_Software;
	   ret = SetProperty(g_TrigMode, g_TrigMode_Internal);
	   if (ret != DEVICE_OK)
		   return ret;
   }

   ret = ShutdownImageBuffer();
   if (ret != DEVICE_OK)
      return ret;

   frameCount_ = 0;
   lastImage_ = 0;
   long hamBufSize = 100;

   ostringstream os;
   os << "Started sequence acquisition: " << numImages << " at " << interval_ms << " ms" << endl;
   LogMessage(os.str().c_str());


   // prepare the camera
   // TODO: prepare the camera
   frameBufSize_ = hamBufSize;
   if (numImages < hamBufSize)
      frameBufSize_ = numImages;
   ret = ResizeImageBuffer(frameBufSize_);
   if (ret != DEVICE_OK)
      return ret;

   double readoutTime;
   char rT[MM::MaxStrLength];
   ret = GetProperty(MM::g_Keyword_ReadoutTime, rT);
   readoutTime = atof(rT);
   double dExp;
   if (!dcam_getexposuretime(m_hDCAM, &dExp))
      return ReportError("Error in dcam_getexposuretime: ");
   os.clear();
   double interval = max(readoutTime, dExp * 1000);
   os << interval;
   SetProperty(MM::g_Keyword_ActualInterval_ms, os.str().c_str());

   // prepare the core
   ret = GetCoreCallback()->PrepareForAcq(this);
   if (ret != DEVICE_OK)
   {
      ResizeImageBuffer();
      return ret;
   }

   // start thread
   imageCounter_ = 0;
   sequenceLength_ = numImages;

   seqThread_->SetLength(numImages);

   // TODO: check trigger mode, etc..

   seqThread_->Start();

   acquiring_ = true;

   LogMessage("Acquisition thread started");

   return DEVICE_OK;
}

/**
 * Stops Burst acquisition
 */
int CHamamatsu::StopSequenceAcquisition()
{
   LogMessage("Stopped sequence acquisition");
   if (!dcam_idle(m_hDCAM))
      return ReportError("Error in dcam_idle: ");

   if (!dcam_freeframe(m_hDCAM))
      return ReportError("Error in dcam_freeframe ");

   seqThread_->Stop();
   acquiring_ = false;

   // Set camera back into snap state
   int nRet = ResizeImageBuffer();
   if (nRet != DEVICE_OK)
      return nRet;

   if (originalTrigMode_.compare(g_TrigMode_Software) == 0)
   {
	   int ret = SetProperty(g_TrigMode, g_TrigMode_Software);
	   if (ret != DEVICE_OK)
		   return ret;
	   originalTrigMode_ = "";
   }

   MM::Core* cb = GetCoreCallback();
   if (cb)
      return cb->AcqFinished(this, 0);
   else
      return DEVICE_OK;
}


/**
 * Waits for new image and inserts it into the circular buffer
 */
int CHamamatsu::PushImage()
{
   // get image from the circular buffer
   // wait until the frame becomes available
   long lastImage;
   long frameCount;
   if (!dcam_gettransferinfo(m_hDCAM, &lastImage, &frameCount)) 
      return ReportError("Error in dcam_gettransferinfo: ");
   if (frameCount <= frameCount_) {
      // there is no new frame, wait for a new one
      double dExp;
      if (!dcam_getexposuretime(m_hDCAM, &dExp))
         return ReportError("Error in dcam_getexposuretime: ");
      long lnTimeOut = (long) ((dExp + 5.0) * 1000.0);
      DWORD dwEvent = DCAM_EVENT_FRAMEEND;
      if (!dcam_wait(m_hDCAM, &dwEvent, lnTimeOut, NULL))
      {
         long lnLastErr = dcam_getlasterror(m_hDCAM, NULL, 0);
         if (lnLastErr != ccErr_none)
            return 0;
            //return lnLastErr;
      }
      if (!dcam_gettransferinfo(m_hDCAM, &lastImage, &frameCount)) 
         return ReportError("Error in dcam_gettransferinfo: ");
   }

   if ( (frameCount - frameCount_) >= frameBufSize_)
	   return ERR_INTERNAL_BUFFER_FULL;

   frameCount_++;
   // There is a new frame, copy it into the circular buffer
   // To make sure that we have the last frame, keep track of the lastImage acquired
   if ( (lastImage != (lastImage_ + 1))  && !(lastImage == 0 && lastImage_ == 0) && !(lastImage == 0 && (lastImage_ == (frameBufSize_ - 1))) ) {
	   if (lastImage_ == (frameBufSize_ - 1)) 
		   lastImage = 0;
	   else
		   lastImage = lastImage_ + 1;
   }

   lastImage_ = lastImage;

   // get pixels
   void* imgPtr;
   long sRow;
   //dcam_lockdata(m_hDCAM, &imgPtr, &sRow, frameCount_);
   if (!dcam_lockdata(m_hDCAM, &imgPtr, &sRow, lastImage))
      return ReportError("Error in dcam_loackdata: ");

   // process image
   MM::ImageProcessor* ip = GetCoreCallback()->GetImageProcessor(this);      
   if (ip)                                                                   
   {                                                                         
      int ret = ip->Process((unsigned char*) imgPtr, GetImageWidth(), GetImageHeight(), GetImageBytesPerPixel());
      if (ret != DEVICE_OK)                                                  
         return ret;                                                         
   }                                                                         
                                                                            
   // This method inserts new image in the circular buffer (residing in MMCore)
   int ret = GetCoreCallback()->InsertImage(this, (unsigned char*) imgPtr,      
                                           GetImageWidth(),                  
                                           GetImageHeight(),                 
                                           GetImageBytesPerPixel());         
   if (ret != DEVICE_OK)
      return ret;

   if (!dcam_unlockdata(m_hDCAM))
      return ReportError("Error in dcam_unlockdata: ");

   return DEVICE_OK;
}
