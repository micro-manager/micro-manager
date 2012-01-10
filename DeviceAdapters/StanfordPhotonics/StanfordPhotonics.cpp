///////////////////////////////////////////////////////////////////////////////
// FILE:          StanfordPhotonics.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Stanford Photonics MEGA-10 adapter 
//                
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 08/24/2006
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
// NOTE:          This adapter is unfinished and does not work.
//
// CVS:           $Id$
//

#include <itxcore.h>
#include <icp.h>
#include <amvs.h> 
#include <amdig.h> 
#include <amfa.h>
#include <ams.h>

#include "../../MMDevice/ModuleInterface.h"
#include "StanfordPhotonics.h"
#include <string>
#include <sstream>
#include <iomanip>

#define isamvs(a) ((a) && !strcmp(itx_get_modname(a),"AM-VS"))
#define isams(a) ((a) && !strcmp(itx_get_modname(a),"AM-STD"))
#define isamfa(a) ((a) && !strcmp(itx_get_modname(a),"AM-FA"))
#define isamc1(a) ((a) && !strcmp(itx_get_modname(a),"AM-CLR1"))
#define isamdig(a) ((a) && !strcmp(itx_get_modname(a),"AM-DIG"))

//#pragma warning(disable : 4996) // disable warning for deperecated CRT functions on Windows 

using namespace std;

// global constants
const char* g_MegaZName = "MegaZ";

const char* g_PixelType_8bit = "8bit";
const char* g_PixelType_16bit = "16bit";

const char* g_ShutterMode = "ShutterMode";
const char* g_ShutterMode_Auto = "Auto";
const char* g_ShutterMode_Open = "Open";
const char* g_ShutterMode_Closed = "Closed";

// singleton instance
MegaZ* MegaZ::instance_ = 0;
unsigned MegaZ::refCount_ = 0;

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

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   // >>> do not declare any devices, since this adapter does not work properly
   // AddAvailableDeviceName(g_MegaZName);
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
   
   if (strcmp(deviceName, g_MegaZName) == 0)
      return MegaZ::GetInstance();
   
   return 0;
}


///////////////////////////////////////////////////////////////////////////////
// MegaZ constructor/destructor

MegaZ::MegaZ() :
   initialized_(false),
   busy_(false),
   snapInProgress_(false),
   binSize_(1),
   expMs_(10.0),
   driverDir_(""),
   fullFrameBuffer_(0),
   fullFrameX_(0),
   fullFrameY_(0),
   mod_(0),
   ammod_(0),
   bitspp_(8),
   bytespp_(1)
{
   InitializeDefaultErrorMessages();

    // Driver location
   CPropertyAction *pAct = new CPropertyAction (this, &MegaZ::OnDriverDir);
   int ret = CreateProperty("DriverDir", "", MM::String, false, pAct, true);
   assert(ret == DEVICE_OK);

}

MegaZ::~MegaZ()
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

MegaZ* MegaZ::GetInstance()
{
   if (!instance_)
      instance_ = new MegaZ();

   refCount_++;
   return instance_;
}

///////////////////////////////////////////////////////////////////////////////
// API methods
// ~~~~~~~~~~~

/**
 * Initialize the camera.
 */
int MegaZ::Initialize()
{
   if (initialized_)
      return DEVICE_OK;

   char driverDir[1024];
   strcpy(driverDir, driverDir_.c_str());

   // set-up the log and error reporting
   itx_set_display_hdlr(NULL);
   //itx_err_level(WARNING);

   // Load the IC-PCI configuration file
   if (itx_load_cnf(driverDir) != ITX_NO_ERROR)
   {
		// if cnf file cannot be found then auto-config
		itx_load_cnf("AUTO");
	}

	mod_ = itx_get_modcnf(0, "ICP", SEQ0); // use only 0 board
	if (mod_ == NULL) {
		itx_remove_sys(0);
      return ERR_BOARD_INITIALIZATION_FAILED;
	}

	// Get a MODCNF pointer to Acquisition SubModule
	ammod_ = itx_get_am(mod_);
	if (ammod_ == NULL) {
		itx_remove_sys(0);
      return ERR_BOARD_INITIALIZATION_FAILED;
	}


   // Terry's init block 1
   ITXINTR_ID intrVB;
   ITXINTR_ID intrDMA;
   ITXINTR_ID intrAM;
   write_reg(mod_, ICP_INTADR, 1);
   itx_intr_connect(mod_, ICP_INTR_VB, &intrVB);
   itx_intr_set_timeout(intrVB, 1);
   itx_intr_connect(mod_, ICP_INTR_BMDONE, &intrDMA);
   itx_intr_set_timeout(intrDMA, 1);
   itx_intr_connect(mod_, ICP_INTR_ACQ_ADRS, &intrAM );
   itx_intr_set_timeout(intrAM, 1);

   //amdig_iregs(ammod_);

   // Terry's second init block
   amdig_exsyncp(ammod_, AMDIG_EXSYNLO );
   amdig_fenpol(ammod_, AMDIG_FENHI );
   amdig_fldpol(ammod_, AMDIG_FLDHI );
   amdig_lenpol(ammod_, AMDIG_LENHI );
   amdig_pclkpol(ammod_, AMDIG_PCLKHI );
   amdig_prip(ammod_, AMDIG_PRILO );
   amdig_clkdiv(ammod_, AMDIG_DIV1 );
   amdig_fsel(ammod_, AMDIG_FSEL_40 );
   amdig_trigen(ammod_, AMDIG_TRG_DIS );
   amdig_trigsrc(ammod_, AMDIG_EXT_TRG );

   amdig_exstim(ammod_, 4095 );
   amdig_exclk(ammod_, AMDIG_EXCLK_DIV2048 );
   amdig_extmd(ammod_, AMDIG_EXTMD_SW  );
   amdig_exsen(ammod_, AMDIG_EXSEN_DIS );

   amdig_scanmd(ammod_, AMDIG_SMODE_AREA); // ??
   amdig_lensm(ammod_, AMDIG_LENSMHI );
   amdig_lvar(ammod_, AMDIG_LVARHI );
   amdig_nolmiss(ammod_, AMDIG_NOLMISSLO );
   amdig_prien(ammod_, AMDIG_PRIEN_DIS );
   amdig_xillen(ammod_, AMDIG_XILL_DIS );

   amdig_ilacemd(ammod_, AMDIG_NI_LACED ); // ??
   amdig_ilpixel(ammod_, AMDIG_NI_LEAVED ); // ??
   //amdig_mc(ammod_, 0);

//   int ret;
	int ret = itx_init_sys(0);
   if (ret != ITX_NO_ERROR) {
		itx_remove_sys(0);
      return ret;
	}

   if (isamdig(ammod_)) 
   {
		bitspp_ = 16;
		switch (amdig_psize(ammod_, INQUIRE)) 
		{
		   case AMDIG_PSIZE_8:
			   bitspp_ = 8;
            bytespp_ = 1;
			break;
		   case AMDIG_PSIZE_10:
			   bitspp_ = 10;
            bytespp_ = 2;
			break;
		   case AMDIG_PSIZE_12:
			   bitspp_ = 12;
            bytespp_ = 2;
			break;
		   case AMDIG_PSIZE_16:
			   bitspp_ = 16;
            bytespp_ = 2;
         break;
         default:
            return ERR_UNSUPPORTED_CAMERA;
		}
   }
   else
      return ERR_UNSUPPORTED_CAMERA;
   
   //frame_ = itx_get_cam_frame(mod_);
   icp_delete_all_frames(mod_);
   itx_get_acq_dim(mod_, &fullFrameX_, &fullFrameY_);
   //frame_ = icp_create_frame(mod_, fullFrameX_, fullFrameY_, (ICP_DEPTH)(bytespp_ * 8), ICP_MONO);
   frame_ = itx_get_cam_frame(mod_);
   if (frame_ == ICP_BAD_ARG)
      return frame_;
   icp_image_pitch(mod_, fullFrameX_, INQUIRE );
   icp_put_bm_aoix(mod_, fullFrameX_, INQUIRE );

    roi_.x = 0;
    roi_.y = 0;
    roi_.xSize = fullFrameX_;
    roi_.ySize = fullFrameY_;

   // Name
   ret = CreateProperty(MM::g_Keyword_Name, g_MegaZName, MM::String, true);
   assert(ret == DEVICE_OK);

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "Stanford Photonics MegaZ device adapter", MM::String, true);
   assert(ret == DEVICE_OK);

   // setup image parameters
   // ----------------------

   // binning
   CPropertyAction *pAct = new CPropertyAction (this, &MegaZ::OnBinning);
   ret = CreateProperty(MM::g_Keyword_Binning, "1", MM::Integer, false, pAct);
   assert(ret == DEVICE_OK);

   vector<string> binValues;
   binValues.push_back("1");
   //binValues.push_back("2");
   //binValues.push_back("4");
   //binValues.push_back("8");
   ret = SetAllowedValues(MM::g_Keyword_Binning, binValues);
   if (ret != DEVICE_OK)
      return ret;

   // pixel type
   pAct = new CPropertyAction (this, &MegaZ::OnPixelType);
   ret = CreateProperty(MM::g_Keyword_PixelType, g_PixelType_16bit, MM::String, false, pAct);
   assert(ret == DEVICE_OK);

   vector<string> pixelTypeValues;
   pixelTypeValues.push_back(g_PixelType_16bit);
   ret = SetAllowedValues(MM::g_Keyword_PixelType, pixelTypeValues);
   if (ret != DEVICE_OK)
      return ret;

   // exposure
   pAct = new CPropertyAction (this, &MegaZ::OnExposure);
   ret = CreateProperty(MM::g_Keyword_Exposure, "10.0", MM::Float, false, pAct);
   assert(ret == DEVICE_OK);

   // shutter mode
   pAct = new CPropertyAction (this, &MegaZ::OnShutterMode);
   ret = CreateProperty(g_ShutterMode, g_ShutterMode_Auto, MM::String, false, pAct);
   assert(ret == DEVICE_OK);

   vector<string> shutterValues;
   shutterValues.push_back(g_ShutterMode_Auto);
   shutterValues.push_back(g_ShutterMode_Open);
   shutterValues.push_back(g_ShutterMode_Closed);
   ret = SetAllowedValues("ShutterMode", shutterValues);
   if (ret != DEVICE_OK)
      return ret;

   // camera gain
   pAct = new CPropertyAction (this, &MegaZ::OnGain);
   ret = CreateProperty(MM::g_Keyword_Gain, "0", MM::Integer, false, pAct);
   assert(ret == DEVICE_OK);

   // EM gain
   pAct = new CPropertyAction (this, &MegaZ::OnEMGain);
   ret = CreateProperty(MM::g_Keyword_EMGain, "0", MM::Integer, false, pAct);
   assert(ret == DEVICE_OK);

   // readout mode
   pAct = new CPropertyAction (this, &MegaZ::OnReadoutMode);
   ret = CreateProperty(MM::g_Keyword_ReadoutMode, "Dummy", MM::String, false, pAct);
   assert(ret == DEVICE_OK);

   // synchronize all properties
   // --------------------------
   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   // setup the buffer
   // ----------------
   ret = ResizeImageBuffer();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;
   return DEVICE_OK;
}

void MegaZ::GetName(char* name) const 
{
   CDeviceUtils::CopyLimitedString(name, g_MegaZName);
}

/**
 * Deactivate the camera, reverse the initialization process.
 */
int MegaZ::Shutdown()
{
   if (!initialized_)
      return DEVICE_OK;

   icp_delete_all_frames(mod_);
   itx_remove_sys(0);
   initialized_ = false;
   return DEVICE_OK;
}

int MegaZ::SnapImage()
{
   const int numFrames = 1;
   BYTE* pBuf = const_cast<BYTE*>(img_.GetPixels());
   assert(TRUE == icp_frame_exist(mod_, frame_));
   int ret = icp_snap(mod_, frame_);
   if (ret != ITX_NO_ERROR)
      return ret;

   return DEVICE_OK;
}

double MegaZ::GetExposure() const
{
   char Buf[MM::MaxStrLength];
   Buf[0] = '\0';
   GetProperty(MM::g_Keyword_Exposure, Buf);
   return atof(Buf);
}

void MegaZ::SetExposure(double dExp)
{
   SetProperty(MM::g_Keyword_Exposure, CDeviceUtils::ConvertToString(dExp));
}

/**
 * Returns the raw image buffer.
 */ 

const unsigned char* MegaZ::GetImageBuffer()
{
   BYTE* pBuf = const_cast<BYTE*>(img_.GetPixels());
   icp_read_area(mod_, frame_, roi_.x, roi_.y, roi_.xSize, roi_.ySize, (DWORD*)pBuf);
   return img_.GetPixels();
}

/**
 * Sets the image Region of Interest (ROI).
 * The internal representation of the ROI uses the full frame coordinates
 * in combination with binning factor.
 */
int MegaZ::SetROI(unsigned uX, unsigned uY, unsigned uXSize, unsigned uYSize)
{
   return DEVICE_OK;
}

unsigned MegaZ::GetBitDepth() const
{
   return bitspp_;
}

int MegaZ::GetROI(unsigned& uX, unsigned& uY, unsigned& uXSize, unsigned& uYSize)
{
   uX = roi_.x / binSize_;
   uY = roi_.y / binSize_;
   uXSize = roi_.xSize / binSize_;
   uYSize = roi_.ySize / binSize_;

   return DEVICE_OK;
}

int MegaZ::ClearROI()
{
   return DEVICE_OK;
}



///////////////////////////////////////////////////////////////////////////////
// Action handlers
// ~~~~~~~~~~~~~~~

int MegaZ::OnDriverDir(MM::PropertyBase* pProp, MM::ActionType eAct)
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


// Binning
int MegaZ::OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
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

int MegaZ::OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   // exposure property is stored in milliseconds,
   // while the driver returns the value in seconds
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(expMs_);
   }
   else if (eAct == MM::AfterSet)
   {
      double exp;
      pProp->Get(exp);
      expMs_ = exp;
   }
   return DEVICE_OK;
}

int MegaZ::OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
      pProp->Set(g_PixelType_16bit);
   return DEVICE_OK;
}

// ScanMode
int MegaZ::OnReadoutMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      string mode;
      pProp->Get(mode);
      for (unsigned i=0; i<readoutModes_.size(); ++i)
         if (readoutModes_[i].compare(mode) == 0)
         {
            //unsigned ret = SetHSSpeed(0, i);
         }
      assert(!"Unrecognized readout mode");
   }
   else if (eAct == MM::BeforeGet)
   {
   }
   return DEVICE_OK;
}

// ReadoutTime
int MegaZ::OnReadoutTime(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   return DEVICE_OK;
}

// gain
int MegaZ::OnEMGain(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
   }
   else if (eAct == MM::BeforeGet)
   {
   }
   return DEVICE_OK;
}

// gain
int MegaZ::OnGain(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
   }
   else if (eAct == MM::BeforeGet)
   {
   }
   return DEVICE_OK;
}

// Offset
int MegaZ::OnOffset(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
   }
   else if (eAct == MM::BeforeGet)
   {
   }
   return DEVICE_OK;
}

// Temperature
int MegaZ::OnTemperature(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
   }
   else if (eAct == MM::BeforeGet)
   {
   }
   return DEVICE_OK;
}

int MegaZ::OnShutterMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
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

   }
   else if (eAct == MM::BeforeGet)
   {
   }
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Utility methods
///////////////////////////////////////////////////////////////////////////////

int MegaZ::ResizeImageBuffer()
{
   // resize internal buffers
   img_.Resize(roi_.xSize / binSize_, roi_.ySize / binSize_, bytespp_);
   return DEVICE_OK;
}
