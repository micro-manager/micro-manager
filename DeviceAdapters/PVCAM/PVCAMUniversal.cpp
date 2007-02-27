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
#endif

#include "../../MMDevice/ModuleInterface.h"
#include "PVCAM.h"
#include "PVCAMUtils.h"

#ifdef WIN32
#include "../../../3rdparty/RoperScientific/Windows/PvCam/SDK/Headers/pvcam.h"
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

Universal* Universal::instance_ = 0;
unsigned Universal::refCount_ = 0;


// global constants
extern const char* g_PixelType_8bit;
extern const char* g_PixelType_16bit;

extern const char* g_ReadoutRate_Slow;
extern const char* g_ReadoutRate_Fast;
extern const char* g_ReadoutPort_Normal;
extern const char* g_ReadoutPort_Multiplier;


///////////////////////////////////////////////////////////////////////////////
// &Universal constructor/destructor
Universal::Universal(short cameraId) :
   initialized_(false),
   busy_(false),
   hPVCAM_(0),
   exposure_(0),
   binSize_(1),
   bufferOK_(false),
   cameraId_(cameraId),
   name_("Undefined")
{
}


Universal::~Universal()
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


///////////////////////////////////////////////////////////////////////////////
// Action handlers
// ~~~~~~~~~~~~~~~

// Binning
int Universal::OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct)
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
      double exp;
      pProp->Get(exp);
      exposure_ = exp;
      int ret = ResizeImageBuffer();
      if (ret != DEVICE_OK)
         return ret;
   }
   return DEVICE_OK;
}

int Universal::OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct)
{  
   pProp->Set(g_PixelType_16bit);
   return DEVICE_OK;
}

// Readout Rate
int Universal::OnReadoutRate(MM::PropertyBase* pProp, MM::ActionType eAct)
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
int Universal::OnReadoutPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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
int Universal::OnOffset(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   return DEVICE_OK;
}

// Temperature
int Universal::OnTemperature(MM::PropertyBase* pProp, MM::ActionType eAct)
{
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
   pl_pvcam_init();

   if (!pl_cam_get_name( cameraId_, name ))
      return pl_error_code();

   // Get a handle to the camera
   if (!pl_cam_open(name, &hPVCAM_, OPEN_EXCLUSIVE ))
      return pl_error_code();

   if (!pl_cam_get_diags(hPVCAM_))
      return pl_error_code();

   name_ = name;

   // Name
   nRet = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   assert(nRet == DEVICE_OK);

   // setup image parameters
   // ----------------------

   // binning
   CPropertyAction *pAct = new CPropertyAction (this, &Universal::OnBinning);
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
   
   pAct = new CPropertyAction (this, &Universal::OnPixelType);
   nRet = CreateProperty(MM::g_Keyword_PixelType, g_PixelType_16bit, MM::String, false, pAct);
   assert(nRet == DEVICE_OK);

   vector<string> pixelTypeValues;
   //pixelTypeValues.push_back(g_PixelType_8bit);
   pixelTypeValues.push_back(g_PixelType_16bit);
   nRet = SetAllowedValues(MM::g_Keyword_PixelType, pixelTypeValues);
   if (nRet != DEVICE_OK)
      return nRet;

   // exposure
   pAct = new CPropertyAction (this, &Universal::OnExposure);
   nRet = CreateProperty(MM::g_Keyword_Exposure, "10.0", MM::Float, false, pAct);
   assert(nRet == DEVICE_OK);

   // camera gain
   pAct = new CPropertyAction (this, &Universal::OnGain);
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
   pAct = new CPropertyAction (this, &Universal::OnMultiplierGain);
   nRet = CreateProperty("MultiplierGain", "0", MM::Integer, false, pAct);
   assert(nRet == DEVICE_OK);

   // scan mode
   pAct = new CPropertyAction (this, &Universal::OnReadoutRate);
   nRet = CreateProperty("ReadoutRate", g_ReadoutRate_Fast, MM::String, false, pAct);
   assert(nRet == DEVICE_OK);
   vector<string> scanValues;
   scanValues.push_back(g_ReadoutRate_Fast);
   scanValues.push_back(g_ReadoutRate_Slow);
   nRet = SetAllowedValues("ReadoutRate", scanValues);
   if (nRet != DEVICE_OK)
      return nRet;

   // readout port
   pAct = new CPropertyAction (this, &Universal::OnReadoutPort);
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
   pAct = new CPropertyAction (this, &Universal::OnOffset);
   nRet = CreateProperty(MM::g_Keyword_Offset, "1", MM::Integer, false, pAct);
   assert(nRet == DEVICE_OK);

   // camera temperature
   pAct = new CPropertyAction (this, &Universal::OnTemperature);
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
// Function name   : &Universal::Shutdown
// Description     : Deactivate the camera, reverse the initialization process
// Return type     : bool 


int Universal::Shutdown()
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

   if (!bufferOK_)
      return ERR_INVALID_BUFFER;

   void* pixBuffer = const_cast<unsigned char*> (img_.GetPixels());
   pl_exp_start_seq(hPVCAM_, pixBuffer);

   // block until exposure is finished
   rs_bool ret;
   while((ret = pl_exp_check_status(hPVCAM_, &status, &not_needed)) && (status == EXPOSURE_IN_PROGRESS));
   if (!ret)
      return pl_error_code();

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

int Universal::SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize)
{
   // ROI internal dimensions are in no-binning mode, so we need to convert

   roi_.x = x * binSize_;
   roi_.y = y * binSize_;
   roi_.xSize = xSize * binSize_;
   roi_.ySize = ySize * binSize_;

   return ResizeImageBuffer();
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
   roi_.x = 0;
   roi_.y = 0;
   roi_.xSize = 0;
   roi_.ySize = 0;

   return ResizeImageBuffer();
}

bool Universal::GetErrorText(int errorCode, char* text) const
{
   if (CCameraBase<Universal>::GetErrorText(errorCode, text))
      return true; // base message

   char buf[ERROR_MSG_LEN];
   if (pl_error_message (errorCode, buf))
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

int Universal::ResizeImageBuffer()
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
   unsigned short newXSize = (unsigned short) roi_.xSize/binSize_;
   unsigned short newYSize = (unsigned short) roi_.ySize/binSize_;

   // make an attempt to adjust the image dimensions
   while ((newXSize * newYSize * 2) % 4 != 0 && newXSize > 4 && newYSize > 4)
   {
      roi_.xSize--;
      newXSize = (unsigned short) roi_.xSize/binSize_;
      if ((newXSize * newYSize * 2) % 4 != 0)
      {
         roi_.ySize--;
         newYSize = (unsigned short) roi_.ySize/binSize_;
      }
   }

   img_.Resize(newXSize, newYSize, 2);

   rgn_type region = { roi_.x, roi_.x + roi_.xSize-1, binSize_, roi_.y, roi_.y + roi_.ySize-1, binSize_};
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
