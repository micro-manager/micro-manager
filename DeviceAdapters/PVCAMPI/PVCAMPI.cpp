///////////////////////////////////////////////////////////////////////////////
// FILE:          PVCAMPI.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   PVCAM camera module for Prinston Instruments cameras
//                
// AUTHOR:        Nico Stuurman, Nenad Amodaj nenad@amodaj.com, 10/19/2007
// COPYRIGHT:     University of California, San Francisco, 2007
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
//
// CVS:           $Id: PVCAM.cpp 475 2007-09-27 19:44:59Z nenad $

#ifdef WIN32
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#endif

#include "../../MMDevice/ModuleInterface.h"
#include "PVCAMPI.h"

#ifdef WIN32
#include "../../../3rdparty/RoperScientific/Windows/PvCam_micromax/pvcam.h"
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

#pragma warning(disable : 4996) // disable warning for deperecated CRT functions on Windows 

using namespace std;

// global constants
const char* g_DeviceMicromax = "Micromax";

const char* g_PixelType_8bit = "8bit";
const char* g_PixelType_16bit = "16bit";
const char* g_ReadoutRate_Slow = "Slow";
const char* g_ReadoutRate_Fast = "Fast";
const char* g_ReadoutPort_Normal = "Normal";
const char* g_ReadoutPort_Multiplier = "Multiplier";

// singleton instances
CPVCAM* CPVCAM::instance_ = 0;
unsigned CPVCAM::refCount_ = 0;

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
   AddAvailableDeviceName(g_DeviceMicromax, "Princeton Intruments Micromax");
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;
   
   if (strcmp(deviceName, g_DeviceMicromax) == 0)
      return CPVCAM::GetInstance();
   
   return 0;
}


///////////////////////////////////////////////////////////////////////////////
// &CPVCAM constructor/destructor

CPVCAM::CPVCAM() :
   initialized_(false),
   busy_(false),
   hPVCAM_(0),
   exposure_(0),
   binSize_(1),
   bufferOK_(false)
{
}

CPVCAM::~CPVCAM()
{
   refCount_--;
   if (refCount_ == 0)
   {
      // release resources
      if (initialized_)
         Shutdown();

      // clear the instance pointer
      instance_ = 0;
   }
}

CPVCAM* CPVCAM::GetInstance()
{
   // if 
   if (!instance_)
      instance_ = new CPVCAM();

   refCount_++;
   return instance_;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
// ~~~~~~~~~~~~~~~

// Binning
int CPVCAM::OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      long bin;
      pProp->Get(bin);
      binSize_ = bin;
      ClearROI(); // reset region of interest
      int nRet = ResizeImageBuffer();
      if (nRet != DEVICE_OK)
         return nRet;
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set((long)binSize_);
   }
   return DEVICE_OK;
}

int CPVCAM::OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   // exposure property is stored in milliseconds,
   // while the driver returns the value in seconds
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(exposure_);
   }
   else if (eAct == MM::AfterSet)
   {
      double exp;
      pProp->Get(exp);
      exposure_ = exp;
      int ret = ResizeImageBuffer();
      if (ret != DEVICE_OK)
         return ret;
   }
   return DEVICE_OK;
}

int CPVCAM::OnPixelType(MM::PropertyBase* pProp, MM::ActionType /*eAct*/)
{  
   pProp->Set(g_PixelType_16bit);
   return DEVICE_OK;
   /*
   ccDatatype ccDataType;
   if (eAct == MM::BeforeGet)
   {
      if (!dcam_getdatatype(hDCAM_, &ccDataType))
         return dcam_getlasterror(hDCAM_, NULL, 0);

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

      if (!dcam_setdatatype(hDCAM_, ccDataType))
         return dcam_getlasterror(hDCAM_, NULL, 0);

      int nRet = ResizeImageBuffer();
      if (nRet != DEVICE_OK)
         return nRet;
   }
   */
}

// ScanMode
int CPVCAM::OnScanMode(MM::PropertyBase* /*pProp*/, MM::ActionType /*eAct*/)
{
   /*
   DCAM_PARAM_SCANMODE scanModeData;
	ZeroMemory((PVOID)&scanModeData, sizeof(DCAM_PARAM_SCANMODE));
   scanModeData.hdr.cbSize = sizeof(DCAM_PARAM_SCANMODE);
   scanModeData.hdr.id = (DWORD) DCAM_IDPARAM_SCANMODE;

   if (eAct == MM::AfterSet)
   {
      long scanMode;
      pProp->Get(scanMode);
      ScanMode.speed = scanMode;
      if (!dcam_extended(hDCAM_, DCAM_IDMSG_SETPARAM, (LPVOID)&scanModeData, sizeof(DCAM_PARAM_SCANMODE)))
         return dcam_getlasterror(hDCAM_, NULL, 0);
   }
   else if (eAct == MM::BeforeGet)
   {
      if (!dcam_extended(hDCAM_,DCAM_IDMSG_GETPARAM, (LPVOID)&scanModeData, sizeof(DCAM_PARAM_SCANMODE)))
         return dcam_getlasterror(hDCAM_, NULL, 0);
      pProp->Set(scanModeData.speed);
   }
   */
   return DEVICE_OK;
}

// Gain
int CPVCAM::OnGain(MM::PropertyBase* /*pProp*/, MM::ActionType /*eAct*/)
{
   // parameter not available for the MicroMax???
   /*
   if (eAct == MM::AfterSet)
   {
      long gain;
      pProp->Get(gain);
      int16 gainIndex = (int16) gain;
      if (!pl_set_param(hPVCAM_, PARAM_GAIN_INDEX, &gainIndex))
         return pl_error_code();
   }
   else if (eAct == MM::BeforeGet)
   {
      int16 gainIndex;
      if (!pl_get_param(hPVCAM_, PARAM_GAIN_INDEX, ATTR_CURRENT, &gainIndex))
         return pl_error_code();
      pProp->Set((long)gainIndex);
   }
*/

   return DEVICE_OK;
}

// Offset
int CPVCAM::OnOffset(MM::PropertyBase* /*pProp*/, MM::ActionType /*eAct*/)
{
   /*
	DCAM_PARAM_FEATURE FeatureValue;
   ZeroMemory((PVOID)&FeatureValue, sizeof(DCAM_PARAM_FEATURE));
   FeatureValue.hdr.cbSize = sizeof(DCAM_PARAM_FEATURE);
	FeatureValue.hdr.id = (DWORD) DCAM_IDPARAM_FEATURE;
   FeatureValue.featureid = DCAM_IDFEATURE_OFFSET;

   if (eAct == MM::AfterSet)
   {
      long lnOffset;
      pProp->Get(lnOffset);
      FeatureValue.featurevalue = (float)lnOffset;
      if (!dcam_extended(hDCAM_, DCAM_IDMSG_SETPARAM, (LPVOID)&FeatureValue, sizeof(DCAM_PARAM_FEATURE)))
         return dcam_getlasterror(hDCAM_, NULL, 0);
   }
   else if (eAct == MM::BeforeGet)
   {
      if (!dcam_extended(hDCAM_, DCAM_IDMSG_GETPARAM, (LPVOID)&FeatureValue, sizeof(DCAM_PARAM_FEATURE)))
         return dcam_getlasterror(hDCAM_, NULL, 0);
      pProp->Set(FeatureValue.featurevalue);
   }
   */
   return DEVICE_OK;
}

// Temperature
int CPVCAM::OnTemperature(MM::PropertyBase* /*pProp*/, MM::ActionType /*eAct*/)
{
   /*
	DCAM_PARAM_FEATURE FeatureValue;
   ZeroMemory((PVOID)&FeatureValue, sizeof(DCAM_PARAM_FEATURE));
   FeatureValue.hdr.cbSize = sizeof(DCAM_PARAM_FEATURE);
	FeatureValue.hdr.id = (DWORD)DCAM_IDPARAM_FEATURE;
   FeatureValue.featureid = DCAM_IDFEATURE_TEMPERATURE;

   if (eAct == MM::AfterSet)
   {
      double dT;
      pProp->Get(dT);
      FeatureValue.featurevalue = (float)dT;
      if (!dcam_extended(hDCAM_, DCAM_IDMSG_SETPARAM, (LPVOID)&FeatureValue, sizeof(DCAM_PARAM_FEATURE)))
         return dcam_getlasterror(hDCAM_, NULL, 0);
   }
   else if (eAct == MM::BeforeGet)
   {
      if (!dcam_extended(hDCAM_, DCAM_IDMSG_GETPARAM, (LPVOID)&FeatureValue, sizeof(DCAM_PARAM_FEATURE)))
         return dcam_getlasterror(hDCAM_, NULL, 0);
      pProp->Set(FeatureValue.featurevalue);
   }
   */
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// API methods
// ~~~~~~~~~~~

void CPVCAM::GetName(char* name) const 
{
   CDeviceUtils::CopyLimitedString(name, g_DeviceMicromax);
}

///////////////////////////////////////////////////////////////////////////////
// Function name   : &CPVCAM::Initialize
// Description     : Initialize the camera
// Return type     : bool 

int CPVCAM::Initialize()
{
   // setup the camera
   // ----------------

   // Name
   int nRet = CreateProperty(MM::g_Keyword_Name, g_DeviceMicromax, MM::String, true);
   assert(nRet == DEVICE_OK);

   // Description
   nRet = CreateProperty(MM::g_Keyword_Description, "PVCAM Micromax device adapter", MM::String, true);
   assert(nRet == DEVICE_OK);

   // gather information about the equipment
   // ------------------------------------------
   
   // Camera name
   char name[CAM_NAME_LEN];
   const char* defName = "xxxxx";
   const int maxLen = 32;
   strcpy(name, defName);
#ifdef WIN32
   GetPrivateProfileString( "Camera_1", "Name", defName, name, maxLen, "pvcam.ini" );
#endif
#ifdef __APPLE__
   if (pl_cam_get_name(0,name))
      printf("Camera Name: %s\n", name);
   else
      printf ("Could not get PVCAM camera name\n");
#endif

   nRet = CreateProperty(MM::g_Keyword_CameraName, name, MM::String, true);
   assert(nRet == DEVICE_OK);

   // Get a handle to the camera
	pl_pvcam_init();		
   if (!pl_cam_open(name, &hPVCAM_, OPEN_EXCLUSIVE))
   {
      return pl_error_code();
   }		      
   initialized_ = true;

   // setup image parameters
   // ----------------------

   // binning
   CPropertyAction *pAct = new CPropertyAction (this, &CPVCAM::OnBinning);
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

   // pixel type
//   nRet = CreateProperty(MM::g_Keyword_PixelType, g_PixelType_16bit, MM::String, true);
   
   pAct = new CPropertyAction (this, &CPVCAM::OnPixelType);
   nRet = CreateProperty(MM::g_Keyword_PixelType, g_PixelType_16bit, MM::String, false, pAct);
   assert(nRet == DEVICE_OK);

   vector<string> pixelTypeValues;
   //pixelTypeValues.push_back(g_PixelType_8bit);
   pixelTypeValues.push_back(g_PixelType_16bit);
   nRet = SetAllowedValues(MM::g_Keyword_PixelType, pixelTypeValues);
   if (nRet != DEVICE_OK)
      return nRet;

   // exposure
   pAct = new CPropertyAction (this, &CPVCAM::OnExposure);
   nRet = CreateProperty(MM::g_Keyword_Exposure, "10.0", MM::Float, false, pAct);
   assert(nRet == DEVICE_OK);

   // camera gain
   //pAct = new CPropertyAction (this, &CPVCAM::OnGain);
   //nRet = CreateProperty(MM::g_Keyword_Gain, "1", MM::Integer, false, pAct);
   nRet = CreateProperty(MM::g_Keyword_Gain, "1", MM::Integer, true);
   assert(nRet == DEVICE_OK);

/*
   // other camera parameters
   // -----------------------

   // scan mode
   pAct = new CPropertyAction (this, &CPVCAM::OnScanMode);
   nRet = CreateProperty("ScanMode", "1", MM::Integer, false, pAct);
   assert(nRet == DEVICE_OK);

   // camera gain
   pAct = new CPropertyAction (this, &CPVCAM::OnGain);
   nRet = CreateProperty(MM::g_Keyword_Gain, "1", MM::Integer, false, pAct);
   assert(nRet == DEVICE_OK);

   // camera offset
   pAct = new CPropertyAction (this, &CPVCAM::OnOffset);
   nRet = CreateProperty(MM::g_Keyword_Offset, "1", MM::Integer, false, pAct);
   assert(nRet == DEVICE_OK);

   // camera temperature
   pAct = new CPropertyAction (this, &CPVCAM::OnTemperature);
   nRet = CreateProperty(MM::g_Keyword_CCDTemperature, "1", MM::Float, false, pAct);
   assert(nRet == DEVICE_OK);
   */

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
   nRet = ResizeImageBuffer();
 
   if (nRet != DEVICE_OK)
      return nRet;

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Function name   : &CPVCAM::Shutdown
// Description     : Deactivate the camera, reverse the initialization process
// Return type     : bool 

int CPVCAM::Shutdown()
{
   if (initialized_)
   {
      // Uninit the sequence
      pl_exp_uninit_seq();
      pl_cam_close(hPVCAM_);
      pl_pvcam_uninit();
      initialized_ = false;
   }
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Function name   : &CPVCAM::SnapImage
// Description     : Acquires a single frame and stores it in the internal
//                   buffer. This command blocks the calling thread untile the
//                   image is fully captured.
// Return type     : bool 

int CPVCAM::SnapImage()
{

   if (!bufferOK_)
      return ERR_INVALID_BUFFER;

   void* pixBuffer = const_cast<unsigned char*> (img_.GetPixels());
   pl_exp_start_seq(hPVCAM_, pixBuffer);

   // block until exposure is finished
   // NOTE: Micromax camera does not signal when the exposure is finished
   #ifdef WIN32   
      long startT = GetTickCount();    
   #else    
      long startT = GetClockTicksUs();    
   #endif
   long delta = 0;
   do
   {
#ifdef WIN32
      delta = GetTickCount() - startT;
      Sleep(2);
#else
      delta = (GetClockTicksUs() -startT) / 1000;
      usleep(2000);
#endif
   } while( delta < (long)(exposure_ + 50.0));

   //int16 status;
   //uns32 not_needed;
   //rs_bool ret;
   //while((ret = pl_exp_check_status(hPVCAM_, &status, &not_needed)) && (status != READOUT_COMPLETE));
   //ostringstream out;
   //out << "PVCAM snapImage() status = " << status << endl;
   //LogMessage(out.str().c_str());
   //if (!ret)
   //   return pl_error_code();
    
   return DEVICE_OK;
}

const unsigned char* CPVCAM::GetImageBuffer()
{
   // wait for data or error
   void* pixBuffer = const_cast<unsigned char*> (img_.GetPixels());
   int16 status;
   uns32 not_needed;
   while(pl_exp_check_status(hPVCAM_, &status, &not_needed) && 
      (status != READOUT_COMPLETE && status != READOUT_FAILED) );

   ostringstream out;
   out << "PVCAM getImage() status = " << status << endl;
   LogMessage(out.str().c_str());

   // Check Error Codes
   if(status == READOUT_FAILED)
      // return pl_error_code();
      return 0;

   if (!pl_exp_finish_seq(hPVCAM_, pixBuffer, 0))
      // return pl_error_code();
      return 0;

   return (unsigned char*) pixBuffer;
}

double CPVCAM::GetExposure() const
{
   char buf[MM::MaxStrLength];
   buf[0] = '\0';
   GetProperty(MM::g_Keyword_Exposure, buf);
   return atof(buf);
}
void CPVCAM::SetExposure(double exp)
{
   SetProperty(MM::g_Keyword_Exposure, CDeviceUtils::ConvertToString(exp));
}

/**
 * Returns the raw image buffer.
 */

unsigned CPVCAM::GetBitDepth() const
{
   if (img_.Depth() == 1)
      return 8;
   else if (img_.Depth() == 2)
      return 12; // <<< TODO: obtain this setting from the hardware
   else
   {
      assert(!"unsupported bytes per pixel count");
      return 0; // should not happen
   }
}

int CPVCAM::SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize)
{
   // ROI internal dimensions are in no-binning mode, so we need to convert

   roi_.x = x * binSize_;
   roi_.y = y * binSize_;
   roi_.xSize = xSize * binSize_;
   roi_.ySize = ySize * binSize_;

   return ResizeImageBuffer();
}

int CPVCAM::GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize)
{
   // ROI internal dimensions are in no-binning mode, so we need to convert

   x = roi_.x / binSize_;
   y = roi_.y / binSize_;
   xSize = roi_.xSize / binSize_;
   ySize = roi_.ySize / binSize_;

   return DEVICE_OK;
}

int CPVCAM::ClearROI()
{
   roi_.x = 0;
   roi_.y = 0;
   roi_.xSize = 0;
   roi_.ySize = 0;

   return ResizeImageBuffer();
}

bool CPVCAM::GetErrorText(int errorCode, char* text) const
{
   if (CCameraBase<CPVCAM>::GetErrorText(errorCode, text))
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

int CPVCAM::ResizeImageBuffer()
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

   rgn_type region = { (uns16)roi_.x, (uns16)(roi_.x + roi_.xSize-1), (uns16)binSize_, (uns16)(roi_.y, roi_.y + roi_.ySize-1), (uns16)binSize_};
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
