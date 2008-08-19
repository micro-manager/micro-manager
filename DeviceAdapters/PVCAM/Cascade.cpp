///////////////////////////////////////////////////////////////////////////////
// FILE:          Cascade.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   PVCAM Cascade camera module
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
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 06/30/2006
//
// NOTES:         This file is obsolete. Use PVCAMUniversal.cpp for new
//                development., N.A. 01/17/2007
//
// CVS:           $Id$

#ifdef WIN32
#pragma warning(disable : 4996) // disable warning for deperecated CRT functions on Windows 
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#endif

#include "../../MMDevice/ModuleInterface.h"
#include "PVCAM.h"
#include "PVCAMUtils.h"

#ifdef WIN32
#include "../../../3rdparty/RoperScientific/Windows/PvCam/SDK_PH/Headers/pvcam.h"
#else
#define __mac_os_x
#include "/Library/Frameworks/PVCAM.framework/Headers/master.h"
#include "/Library/Frameworks/PVCAM.framework/Headers/pvcam.h"
#endif

#include <string>
#include <sstream>
#include <iomanip>


using namespace std;

// global constants
extern const char* g_DeviceCascade;

extern const char* g_PixelType_8bit;
extern const char* g_PixelType_16bit;

extern const char* g_ReadoutRate_Slow;
extern const char* g_ReadoutRate_Fast;
extern const char* g_ReadoutPort_Normal;
extern const char* g_ReadoutPort_Multiplier;

// singleton instance
Cascade* Cascade::instance_ = 0;
unsigned Cascade::refCount_ = 0;

///////////////////////////////////////////////////////////////////////////////
// &Cascade constructor/destructor

Cascade::Cascade() :
   CCameraBase<Cascade> (),
   initialized_(false),
   busy_(false),
   hPVCAM_(0),
   exposure_(0),
   binSize_(1),
   bufferOK_(false)
{
}

Cascade::~Cascade()
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

Cascade* Cascade::GetInstance()
{
   // if 
   if (!instance_)
      instance_ = new Cascade();

   refCount_++;
   return instance_;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
// ~~~~~~~~~~~~~~~

// Binning
int Cascade::OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int Cascade::OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int Cascade::OnPixelType(MM::PropertyBase* pProp, MM::ActionType /*eAct*/)
{  
   pProp->Set(g_PixelType_16bit);
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
   return DEVICE_OK;
}

// Readout Rate
int Cascade::OnReadoutRate(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      string par;
      pProp->Get(par);
      long rate = 0;
      if (par.compare(g_ReadoutRate_Fast) == 0)
         rate = 0;
      else if (par.compare(g_ReadoutRate_Slow) == 0)
         rate = 1;
      else
         return ERR_INVALID_PARAMETER_VALUE;

      if (!SetLongParam_PvCam(hPVCAM_, PARAM_SPDTAB_INDEX, rate))
         return pl_error_code();
   }
   else if (eAct == MM::BeforeGet)
   {
      long rate;
      if (!GetLongParam_PvCam(hPVCAM_, PARAM_SPDTAB_INDEX, &rate))
         return pl_error_code();
      if (rate == 1)
         pProp->Set(g_ReadoutRate_Slow);
      else if (rate == 0)
         pProp->Set(g_ReadoutRate_Fast);
      else
         assert(!"Unrecognized rate parameter");
   }
   return DEVICE_OK;
}

// Readout Port
int Cascade::OnReadoutPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      string par;
      pProp->Get(par);
      long port = 0;
      if (par.compare(g_ReadoutPort_Multiplier) == 0)
         port = 0;
      else if (par.compare(g_ReadoutPort_Normal) == 0)
         port = 1;
      else
         return ERR_INVALID_PARAMETER_VALUE;

      if (!SetLongParam_PvCam(hPVCAM_, PARAM_READOUT_PORT, port))
         return pl_error_code();
   }
   else if (eAct == MM::BeforeGet)
   {
      long port;
      if (!GetLongParam_PvCam(hPVCAM_, PARAM_READOUT_PORT, &port))
         return pl_error_code();
      if (port == 1)
         pProp->Set(g_ReadoutPort_Normal);
      else if (port == 0)
         pProp->Set(g_ReadoutPort_Multiplier);
      else
         assert(!"Unrecognized rate parameter");
   }
   return DEVICE_OK;
}


// Gain
int Cascade::OnGain(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int Cascade::OnMultiplierGain(MM::PropertyBase* pProp, MM::ActionType eAct)
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
int Cascade::OnOffset(MM::PropertyBase* /*pProp*/, MM::ActionType /*eAct*/)
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
int Cascade::OnTemperature(MM::PropertyBase* /*pProp*/, MM::ActionType /*eAct*/)
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

void Cascade::GetName(char* name) const 
{
   CDeviceUtils::CopyLimitedString(name, g_DeviceCascade);
}

///////////////////////////////////////////////////////////////////////////////
// Function name   : &Cascade::Initialize
// Description     : Initialize the camera
// Return type     : bool 

int Cascade::Initialize()
{
   // setup the camera
   // ----------------

   // Name
   int nRet = CreateProperty(MM::g_Keyword_Name, g_DeviceCascade, MM::String, true);
   assert(nRet == DEVICE_OK);

   // Description
   nRet = CreateProperty(MM::g_Keyword_Description, "PVCAM API device adapter", MM::String, true);
   assert(nRet == DEVICE_OK);

   // gather information about the equipment
   // ------------------------------------------
   
   // Camera name
   char name[CAM_NAME_LEN];
   pl_pvcam_init();
   pl_cam_get_name( 0, name );
   // Get a handle to the camera
   if (!pl_cam_open(name, &hPVCAM_, OPEN_EXCLUSIVE ))
      return pl_error_code();

   if (!pl_cam_get_diags(hPVCAM_))
      return pl_error_code();

   // setup image parameters
   // ----------------------

   // binning
   CPropertyAction *pAct = new CPropertyAction (this, &Cascade::OnBinning);
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
   
   pAct = new CPropertyAction (this, &Cascade::OnPixelType);
   nRet = CreateProperty(MM::g_Keyword_PixelType, g_PixelType_16bit, MM::String, false, pAct);
   assert(nRet == DEVICE_OK);

   vector<string> pixelTypeValues;
   //pixelTypeValues.push_back(g_PixelType_8bit);
   pixelTypeValues.push_back(g_PixelType_16bit);
   nRet = SetAllowedValues(MM::g_Keyword_PixelType, pixelTypeValues);
   if (nRet != DEVICE_OK)
      return nRet;

   // exposure
   pAct = new CPropertyAction (this, &Cascade::OnExposure);
   nRet = CreateProperty(MM::g_Keyword_Exposure, "10.0", MM::Float, false, pAct);
   assert(nRet == DEVICE_OK);

   // camera gain
   pAct = new CPropertyAction (this, &Cascade::OnGain);
   nRet = CreateProperty(MM::g_Keyword_Gain, "1", MM::Integer, false, pAct);
   assert(nRet == DEVICE_OK);
   vector<string> gainValues;
   gainValues.push_back("1");
   gainValues.push_back("2");
   gainValues.push_back("3");
   nRet = SetAllowedValues(MM::g_Keyword_Gain, gainValues);
   if (nRet != DEVICE_OK)
      return nRet;

   // multiplier gain
   pAct = new CPropertyAction (this, &Cascade::OnMultiplierGain);
   nRet = CreateProperty("MultiplierGain", "0", MM::Integer, false, pAct);
   assert(nRet == DEVICE_OK);

   // scan mode
   pAct = new CPropertyAction (this, &Cascade::OnReadoutRate);
   nRet = CreateProperty("ReadoutRate", g_ReadoutRate_Fast, MM::String, false, pAct);
   assert(nRet == DEVICE_OK);
   vector<string> scanValues;
   scanValues.push_back(g_ReadoutRate_Fast);
   scanValues.push_back(g_ReadoutRate_Slow);
   nRet = SetAllowedValues("ReadoutRate", scanValues);
   if (nRet != DEVICE_OK)
      return nRet;

   // readout port
   pAct = new CPropertyAction (this, &Cascade::OnReadoutPort);
   nRet = CreateProperty("ReadoutPort", g_ReadoutPort_Multiplier, MM::String, false, pAct);
   assert(nRet == DEVICE_OK);
   vector<string> portValues;
   portValues.push_back(g_ReadoutPort_Multiplier);
   portValues.push_back(g_ReadoutPort_Normal);
   nRet = SetAllowedValues("ReadoutPort", portValues);
   if (nRet != DEVICE_OK)
      return nRet;


/*
   // other camera parameters
   // -----------------------

   // camera offset
   pAct = new CPropertyAction (this, &Cascade::OnOffset);
   nRet = CreateProperty(MM::g_Keyword_Offset, "1", MM::Integer, false, pAct);
   assert(nRet == DEVICE_OK);

   // camera temperature
   pAct = new CPropertyAction (this, &Cascade::OnTemperature);
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

   initialized_ = true;
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Function name   : &Cascade::Shutdown
// Description     : Deactivate the camera, reverse the initialization process
// Return type     : bool 

int Cascade::Shutdown()
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
// Function name   : &Cascade::SnapImage
// Description     : Acquires a single frame and stores it in the internal
//                   buffer.
//                   This command blocks the calling thread
//                   until the image is fully captured.
// Return type     : int 

int Cascade::SnapImage()
{
   int16 status;
   uns32 not_needed;

   if (!bufferOK_)
      return ERR_INVALID_BUFFER;

   void* pixBuffer = const_cast<unsigned char*> (img_.GetPixels());
   pl_exp_start_seq(hPVCAM_, pixBuffer);

   // block until exposure is finished
   while(pl_exp_check_status(hPVCAM_, &status, &not_needed) && (status == EXPOSURE_IN_PROGRESS));
    
   return DEVICE_OK;
}

const unsigned char* Cascade::GetImageBuffer()
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

double Cascade::GetExposure() const
{
   char buf[MM::MaxStrLength];
   buf[0] = '\0';
   GetProperty(MM::g_Keyword_Exposure, buf);
   return atof(buf);
}
void Cascade::SetExposure(double exp)
{
   SetProperty(MM::g_Keyword_Exposure, CDeviceUtils::ConvertToString(exp));
}

/**
 * Returns the raw image buffer.
 */

unsigned Cascade::GetBitDepth() const
{
   if (img_.Depth() == 1)
      return 8;
   else if (img_.Depth() == 2)
      return 16; // <<< TODO: obtain this setting from the hardware
   else
   {
      assert(!"unsupported bytes per pixel count");
      return 0; // should not happen
   }
}

int Cascade::GetBinning () const 
{
   return binSize_;
}

int Cascade::SetBinning (int binSize) 
{
   ostringstream os;
   os << binSize;
   return SetProperty(MM::g_Keyword_Binning, os.str().c_str());
}

int Cascade::SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize)
{
   // ROI internal dimensions are in no-binning mode, so we need to convert

   roi_.x = (uns16)(x * binSize_);
   roi_.y = (uns16)(y * binSize_);
   roi_.xSize = (uns16)(xSize * binSize_);
   roi_.ySize = (uns16)(ySize * binSize_);

   return ResizeImageBuffer();
}

int Cascade::GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize)
{
   // ROI internal dimensions are in no-binning mode, so we need to convert

   x = roi_.x / binSize_;
   y = roi_.y / binSize_;
   xSize = roi_.xSize / binSize_;
   ySize = roi_.ySize / binSize_;

   return DEVICE_OK;
}

int Cascade::ClearROI()
{
   roi_.x = 0;
   roi_.y = 0;
   roi_.xSize = 0;
   roi_.ySize = 0;

   return ResizeImageBuffer();
}

bool Cascade::GetErrorText(int errorCode, char* text) const
{
   if (CCameraBase<Cascade>::GetErrorText(errorCode, text))
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

int Cascade::ResizeImageBuffer()
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

   rgn_type region = { 0, --roi_.xSize, (uns16)binSize_, 0, --roi_.ySize, (uns16)binSize_};
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


