///////////////////////////////////////////////////////////////////////////////
// FILE:          Hamamatsu.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Hamamatsu camera module based on DCAM API
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
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 08/24/2005
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
#include "../../../3rdparty/Hamamatsu/DCAMSDK/2004-10/inc/dcamapix.h"
#include "../../../3rdparty/Hamamatsu/DCAMSDK/2004-10/inc/features.h"
#else
#include <dcamapi/dcamapix.h>
#include <dcamapi/features.h>
#endif
#include <string>
#include <sstream>
#include <iomanip>

#pragma warning(disable : 4996) // disable warning for deperecated CRT functions on Windows 

using namespace std;

// global constants
const char* g_DeviceName = "Hamamatsu_DCAM";

const char* g_PixelType_8bit = "8bit";
const char* g_PixelType_16bit = "16bit";

// singleton instance
CHamamatsu* CHamamatsu::m_pInstance = 0;
unsigned CHamamatsu::refCount_ = 0;

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
      return CHamamatsu::GetInstance();
   
   return 0;
}


///////////////////////////////////////////////////////////////////////////////
// CHamamatsu constructor/destructor

CHamamatsu::CHamamatsu() :
   m_bInitialized(false),
   m_bBusy(false),
   m_hDCAMModule(0),
   m_hDCAM(0),
   snapInProgress_(false),
   lnBin_(1)
{
   InitializeDefaultErrorMessages();
}

CHamamatsu::~CHamamatsu()
{
   refCount_--;
   if (refCount_ == 0)
   {
      // release resources
      if (m_bInitialized)
         Shutdown();

      // clear the instance pointer
      m_pInstance = 0;
   }
}

CHamamatsu* CHamamatsu::GetInstance()
{
   // if 
   if (!m_pInstance)
      m_pInstance = new CHamamatsu();

   refCount_++;
   return m_pInstance;
}

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
         return dcam_getlasterror(m_hDCAM, NULL, 0);
      nRet = ResizeImageBuffer();
      if (nRet != DEVICE_OK)
         return nRet;
   }
   else if (eAct == MM::BeforeGet)
   {
      if (!dcam_getbinning(m_hDCAM, &lnBin_))
         return dcam_getlasterror(m_hDCAM, NULL, 0);
      pProp->Set(lnBin_);
   }
   return DEVICE_OK;
}

int CHamamatsu::OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   // exposure property is stored in milliseconds,
   // while the driver returns the value in seconds
   if (eAct == MM::BeforeGet)
   {
      double dExp;
      if (!dcam_getexposuretime(m_hDCAM, &dExp))
         return dcam_getlasterror(m_hDCAM, NULL, 0);
      pProp->Set(dExp * 1000.0);
   }
   else if (eAct == MM::AfterSet)
   {
      double dExp;
      pProp->Get(dExp);
      if (!dcam_setexposuretime(m_hDCAM, dExp / 1000.0))
         return dcam_getlasterror(m_hDCAM, NULL, 0);
   }
   return DEVICE_OK;
}

int CHamamatsu::OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ccDatatype ccDataType;
   if (eAct == MM::BeforeGet)
   {
      if (!dcam_getdatatype(m_hDCAM, &ccDataType))
         return dcam_getlasterror(m_hDCAM, NULL, 0);

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
         return dcam_getlasterror(m_hDCAM, NULL, 0);

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
         return dcam_getlasterror(m_hDCAM, NULL, 0);
      ret = ResizeImageBuffer();
      if (ret != DEVICE_OK)
         return ret;
   }
   else if (eAct == MM::BeforeGet)
   {
      if (!dcam_extended(m_hDCAM,DCAM_IDMSG_GETPARAM, (LPVOID)&ScanMode, sizeof(DCAM_PARAM_SCANMODE)))
         return dcam_getlasterror(m_hDCAM, NULL, 0);
      pProp->Set(ScanMode.speed);
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
         return dcam_getlasterror(m_hDCAM, NULL, 0);
      pProp->Set(readoutTime.framereadouttime);
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
         return dcam_getlasterror(m_hDCAM, NULL, 0);
   }
   else if (eAct == MM::BeforeGet)
   {
      if (!dcam_extended(m_hDCAM, DCAM_IDMSG_GETPARAM, (LPVOID)&FeatureValue, sizeof(DCAM_PARAM_FEATURE)))
         return dcam_getlasterror(m_hDCAM, NULL, 0);
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
         return dcam_getlasterror(m_hDCAM, NULL, 0);
   }
   else if (eAct == MM::BeforeGet)
   {
      if (!dcam_extended(m_hDCAM, DCAM_IDMSG_GETPARAM, (LPVOID)&FeatureValue, sizeof(DCAM_PARAM_FEATURE)))
         return dcam_getlasterror(m_hDCAM, NULL, 0);
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
         return dcam_getlasterror(m_hDCAM, NULL, 0);
   }
   else if (eAct == MM::BeforeGet)
   {
      if (!dcam_extended(m_hDCAM, DCAM_IDMSG_GETPARAM, (LPVOID)&FeatureValue, sizeof(DCAM_PARAM_FEATURE)))
         return dcam_getlasterror(m_hDCAM, NULL, 0);
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
         return dcam_getlasterror(m_hDCAM, NULL, 0);
   }
   else if (eAct == MM::BeforeGet)
   {
      if (!dcam_extended(m_hDCAM, DCAM_IDMSG_GETPARAM, (LPVOID)&FeatureValue, sizeof(DCAM_PARAM_FEATURE)))
      {
         int errCode = dcam_getlasterror(m_hDCAM, NULL, 0);

         // this function may not be supported in older cameras
         if (errCode != DCAMERR_NOTSUPPORT)
            return errCode;
         else
            FeatureValue.featurevalue = 0.0; // default to zero if function not supported
      }
      pProp->Set(FeatureValue.featurevalue);
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

///////////////////////////////////////////////////////////////////////////////
// Function name   : CHamamatsu::Initialize
// Description     : Initialize the camera
// Return type     : bool 

int CHamamatsu::Initialize()
{
   // setup the camera
   // ----------------

   // Name
   int nRet = CreateProperty(MM::g_Keyword_Name, g_DeviceName, MM::String, true);
   assert(nRet == DEVICE_OK);

   // Description
   nRet = CreateProperty(MM::g_Keyword_Description, "Hamamatsu DCAM API device adapter", MM::String, true);
   assert(nRet == DEVICE_OK);

   // initialize the DCAM dll
#ifdef WIN32
   m_hDCAMModule = ::GetModuleHandle(NULL);
#else
   m_hDCAMModule = NULL;
#endif
   long lnCameras(0);

	if (!dcam_init(m_hDCAMModule, &lnCameras, NULL) || lnCameras < 1)
      return DEVICE_NATIVE_MODULE_FAILED;

   // gather the information about the equipment
   // ------------------------------------------

   // open the camera (we support only single camera at this time)
   const long clnCameraIdx = 0;
   if(!dcam_open(&m_hDCAM, clnCameraIdx, NULL) && !m_hDCAM)
       return DEVICE_NATIVE_MODULE_FAILED;

   // verify buffer mode
	DWORD	cap;
	if(!dcam_getcapability(m_hDCAM, &cap, DCAM_QUERYCAPABILITY_FUNCTIONS))
       return dcam_getlasterror(m_hDCAM, NULL, 0);

	if(!(cap & DCAM_CAPABILITY_USERMEMORY))
		return DEVICE_NOT_SUPPORTED; // we need user memory capability

   // CameraName
   char	CameraName[64] = "Unrecognized";
   
   if (!dcam_getstring(m_hDCAM, DCAM_IDSTR_MODEL, CameraName, sizeof(CameraName)))
   {
      int errCode = dcam_getlasterror(m_hDCAM, NULL, 0);

      // this function may not be supported in older cameras
      if (errCode != DCAMERR_NOTSUPPORT)
         return errCode;
   }
   nRet = CreateProperty(MM::g_Keyword_CameraName, CameraName, MM::String, true);
   assert(nRet == DEVICE_OK);
		      
   // CameraID
   char	CameraID[64];
   if (!dcam_getstring(m_hDCAM, DCAM_IDSTR_CAMERAID, CameraID, sizeof(CameraID)))
      strcpy(CameraID, "Not available");
   nRet = CreateProperty(MM::g_Keyword_CameraID, CameraID, MM::String, true);
   assert(nRet == DEVICE_OK);

   // setup image parameters
   // ----------------------

   // binning
   CPropertyAction *pAct = new CPropertyAction (this, &CHamamatsu::OnBinning);
   nRet = CreateProperty(MM::g_Keyword_Binning, "1", MM::Integer, false, pAct);
   assert(nRet == DEVICE_OK);

   vector<string> binValues;
   binValues.push_back("1");
   binValues.push_back("2");
   binValues.push_back("4");
   nRet = SetAllowedValues(MM::g_Keyword_Binning, binValues);
   if (nRet != DEVICE_OK)
      return nRet;

   // pixel type
   pAct = new CPropertyAction (this, &CHamamatsu::OnPixelType);
   nRet = CreateProperty(MM::g_Keyword_PixelType, CameraID, MM::String, false, pAct);
   assert(nRet == DEVICE_OK);

   if(!dcam_getcapability(m_hDCAM, &cap, DCAM_QUERYCAPABILITY_DATATYPE))
       return dcam_getlasterror(m_hDCAM, NULL, 0);

   vector<string> pixelTypeValues;
	if(cap & ccDatatype_uint8)
      pixelTypeValues.push_back(g_PixelType_8bit);
 	if(cap & ccDatatype_uint16)
      pixelTypeValues.push_back(g_PixelType_16bit);
   nRet = SetAllowedValues(MM::g_Keyword_PixelType, pixelTypeValues);
   if (nRet != DEVICE_OK)
      return nRet;

   // exposure
   pAct = new CPropertyAction (this, &CHamamatsu::OnExposure);
   nRet = CreateProperty(MM::g_Keyword_Exposure, "10.0", MM::Float, false, pAct);
   assert(nRet == DEVICE_OK);

   // scan mode
   pAct = new CPropertyAction (this, &CHamamatsu::OnScanMode);
   nRet = CreateProperty("ScanMode", "1", MM::Integer, false, pAct);
   assert(nRet == DEVICE_OK);
   
   // camera gain
   pAct = new CPropertyAction (this, &CHamamatsu::OnGain);
   nRet = CreateProperty(MM::g_Keyword_Gain, "1", MM::Integer, false, pAct);
   assert(nRet == DEVICE_OK);

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

   // camera offset
   // for some types of cameras (like ORCA-II ER) reading the offset generates
   // an error when scan mode is 1.
   // Offset property is temporarily disabled
   //pAct = new CPropertyAction (this, &CHamamatsu::OnOffset);
   //nRet = CreateProperty(MM::g_Keyword_Offset, "1", MM::Integer, false, pAct);
   //assert(nRet == DEVICE_OK);

   // camera temperature
   pAct = new CPropertyAction (this, &CHamamatsu::OnTemperature);
   nRet = CreateProperty(MM::g_Keyword_CCDTemperature, "1", MM::Float, false, pAct);
   assert(nRet == DEVICE_OK);

   // camera gamma

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

   // We seen to need this on the Mac...
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
         return dcam_getlasterror(m_hDCAM, NULL, 0);

      if (!dcam_freeframe(m_hDCAM))
         return dcam_getlasterror(m_hDCAM, NULL, 0);

      if (!dcam_close(m_hDCAM))
         return dcam_getlasterror(m_hDCAM, NULL, 0);
   
      if (!dcam_uninit(m_hDCAMModule))
         return dcam_getlasterror(m_hDCAM, NULL, 0);

      m_bInitialized = false;
   }
   return DEVICE_OK;
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
   if (!dcam_firetrigger(m_hDCAM))
      return dcam_getlasterror(m_hDCAM, NULL, 0);

   snapInProgress_ = true;
#ifdef WIN32
   Sleep((DWORD)(dExp*1000.0));
#else
   usleep(dExp*1000.0);
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
      return dcam_getlasterror(m_hDCAM, NULL, 0);

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
      return dcam_getlasterror(m_hDCAM, NULL, 0);

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
      assert(!"unsupported bytes per pixel count");
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
      return dcam_getlasterror(m_hDCAM, NULL, 0);

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
      return dcam_getlasterror(m_hDCAM, NULL, 0);

   // reset the frame
   int ret = SetROI(0, 0, SubArrayInquiry.hmax, SubArrayInquiry.vmax);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Utility methods
///////////////////////////////////////////////////////////////////////////////

int CHamamatsu::ResizeImageBuffer()
{
   // resize internal buffers
#ifdef WIN32
   SIZE imgSize;
   if (!dcam_getdatasize(m_hDCAM,&imgSize))
      return dcam_getlasterror(m_hDCAM, NULL, 0);

   ccDatatype ccDataType;
   if (!dcam_getdatatype(m_hDCAM,&ccDataType))
      return dcam_getlasterror(m_hDCAM, NULL, 0);

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

   SIZE imgSize;
   if (!dcam_getdatasize(m_hDCAM,&imgSize))
      return dcam_getlasterror(m_hDCAM, NULL, 0);

   ccDatatype ccDataType;
   if (!dcam_getdatatype(m_hDCAM,&ccDataType))
      return dcam_getlasterror(m_hDCAM, NULL, 0);

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

   // setup the sequence cpture mode
   if (!dcam_precapture(m_hDCAM, ccCapture_Sequence))
      return dcam_getlasterror(m_hDCAM, NULL, 0);

   // set a 3 frame sequence buffer
   const long frameBufSize = 3;
   if (!dcam_freeframe(m_hDCAM))
      return dcam_getlasterror(m_hDCAM, NULL, 0);

   if (!dcam_allocframe(m_hDCAM, frameBufSize))
      return dcam_getlasterror(m_hDCAM, NULL, 0);

   long numFrames;
   if (!dcam_getframecount(m_hDCAM, &numFrames))
      return dcam_getlasterror(m_hDCAM, NULL, 0);

   if (numFrames != frameBufSize)
      return ERR_BUFFER_ALLOCATION_FAILED;

   DWORD dwDataBufferSize;
   if (!dcam_getdataframebytes(m_hDCAM, &dwDataBufferSize))
      return dcam_getlasterror(m_hDCAM, NULL, 0);

   if (img_.Height() * img_.Width() * img_.Depth() != dwDataBufferSize)
      return DEVICE_INTERNAL_INCONSISTENCY; // buffer sizes don't match ???

   // get into the soft trigger mode
   if (!dcam_settriggermode(m_hDCAM, DCAM_TRIGMODE_SOFTWARE))
      return dcam_getlasterror(m_hDCAM, NULL, 0);

   // start capture
   if (!dcam_capture(m_hDCAM))
      return dcam_getlasterror(m_hDCAM, NULL, 0);

   return DEVICE_OK;
}

int CHamamatsu::ShutdownImageBuffer()
{
   // interrupt whatever the camera is currently doing
   if (!dcam_idle(m_hDCAM))
      return dcam_getlasterror(m_hDCAM, NULL, 0);

   if (!dcam_freeframe(m_hDCAM))
      return dcam_getlasterror(m_hDCAM, NULL, 0);

   return DEVICE_OK;
}

bool CHamamatsu::IsFeatureSupported(int featureId)
{
   // inquire about capabilities
   DCAM_PARAM_FEATURE_INQ featureInquiry;
   ZeroMemory((LPVOID)&featureInquiry,sizeof(DCAM_PARAM_FEATURE_INQ));
	featureInquiry.hdr.cbSize = sizeof(DCAM_PARAM_FEATURE_INQ);
	featureInquiry.hdr.id = (DWORD)DCAM_IDPARAM_FEATURE_INQ;
   featureInquiry.featureid = featureId;
						
   return dcam_extended(m_hDCAM, DCAM_IDMSG_GETPARAM,(LPVOID)&featureInquiry, sizeof(DCAM_PARAM_FEATURE_INQ)) == TRUE ? true : false;
}
