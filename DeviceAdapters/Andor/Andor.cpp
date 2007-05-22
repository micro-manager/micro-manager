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
// CVS:           $Id$
//
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include "../../MMDevice/ModuleInterface.h"
#include "atmcd32d.h"
#include "Andor.h"
#include <string>
#include <sstream>
#include <iomanip>

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
   AddAvailableDeviceName(g_IxonShutterName, "Andor iXon shutter adapter");
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

   if (strcmp(deviceName, g_IxonName) == 0)
      return Ixon::GetInstance();

   else if (strcmp(deviceName, g_IxonShutterName) == 0)
   {
      return new Shutter();
   }

   return 0;
}

///////////////////////////////////////////////////////////////////////////////
// Ixon constructor/destructor

Ixon::Ixon() :
   initialized_(false),
   busy_(false),
   snapInProgress_(false),
   binSize_(1),
   expMs_(10.0),
   driverDir_(""),
   fullFrameBuffer_(0),
   fullFrameX_(0),
   fullFrameY_(0),
   EmCCDGainLow_(0),
   EmCCDGainHigh_(0),
   minTemp_(0),
   maxTemp_(0)
{
   InitializeDefaultErrorMessages();

   // Pre-initialization properties
   // -----------------------------

   // Driver location
   CPropertyAction *pAct = new CPropertyAction (this, &Ixon::OnDriverDir);
   int nRet = CreateProperty("DriverDir", "", MM::String, false, pAct, true);
   assert(nRet == DEVICE_OK);
}

Ixon::~Ixon()
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
 * Initialize the camera.
 */
int Ixon::Initialize()
{
   if (initialized_)
      return DEVICE_OK;

   unsigned ret = ::Initialize(const_cast<char*>(driverDir_.c_str()));
   if (ret != DRV_SUCCESS)
      return ret;

   // Name
   int nRet = CreateProperty(MM::g_Keyword_Name, g_IxonName, MM::String, true);
   assert(nRet == DEVICE_OK);

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "Andor iXon device adapter", MM::String, true);
   assert(nRet == DEVICE_OK);

   // driver location

   // capabilities
   AndorCapabilities caps;
   caps.ulSize = sizeof(AndorCapabilities);
   ret = GetCapabilities(&caps);
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
   ret = SetAcquisitionMode(1); // single scan mode
   if (ret != DRV_SUCCESS)
      return ret;

   ret = SetReadMode(4); // image mode
   if (ret != DRV_SUCCESS)
      return ret;

   // binning
   CPropertyAction *pAct = new CPropertyAction (this, &Ixon::OnBinning);
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
   pAct = new CPropertyAction (this, &Ixon::OnPixelType);
   nRet = CreateProperty(MM::g_Keyword_PixelType, g_PixelType_16bit, MM::String, false, pAct);
   assert(nRet == DEVICE_OK);

   vector<string> pixelTypeValues;
   pixelTypeValues.push_back(g_PixelType_16bit);
   nRet = SetAllowedValues(MM::g_Keyword_PixelType, pixelTypeValues);
   if (nRet != DEVICE_OK)
      return nRet;

   // exposure
   pAct = new CPropertyAction (this, &Ixon::OnExposure);
   nRet = CreateProperty(MM::g_Keyword_Exposure, "10.0", MM::Float, false, pAct);
   assert(nRet == DEVICE_OK);

   // shutter mode
   pAct = new CPropertyAction (this, &Ixon::OnShutterMode);
   nRet = CreateProperty(g_ShutterMode, g_ShutterMode_Auto, MM::String, false, pAct);
   assert(nRet == DEVICE_OK);

   vector<string> shutterValues;
   shutterValues.push_back(g_ShutterMode_Auto);
   shutterValues.push_back(g_ShutterMode_Open);
   shutterValues.push_back(g_ShutterMode_Closed);
   nRet = SetAllowedValues("ShutterMode", shutterValues);
   if (nRet != DEVICE_OK)
      return nRet;

   // camera gain
   pAct = new CPropertyAction (this, &Ixon::OnGain);
   nRet = CreateProperty(MM::g_Keyword_Gain, "0", MM::Integer, false, pAct);
   assert(nRet == DEVICE_OK);

   // EM gain
   // jizhen 05.08.2007
   // EMCCDGain range
   int EmCCDGainLow, EmCCDGainHigh;
   ret = GetEMGainRange(&EmCCDGainLow, &EmCCDGainHigh);
   if (ret != DRV_SUCCESS)
      return ret;
   EmCCDGainLow_ = EmCCDGainLow;
   EmCCDGainHigh_ = EmCCDGainHigh;
   ostringstream emgLow;
   ostringstream emgHigh;
   emgLow << EmCCDGainLow;
   emgHigh << EmCCDGainHigh;
   CreateProperty("EmCCDGainLow", emgLow.str().c_str(), MM::Integer, true);
   CreateProperty("EmCCDGainHigh", emgHigh.str().c_str(), MM::Integer, true);
   // eof jizhen
   pAct = new CPropertyAction (this, &Ixon::OnEMGain);
   nRet = CreateProperty(MM::g_Keyword_EMGain, "0", MM::Integer, false, pAct);
   assert(nRet == DEVICE_OK);

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

   pAct = new CPropertyAction (this, &Ixon::OnReadoutMode);
   nRet = CreateProperty(MM::g_Keyword_ReadoutMode, readoutModes_[0].c_str(), MM::String, false, pAct);
   assert(nRet == DEVICE_OK);
   nRet = SetAllowedValues(MM::g_Keyword_ReadoutMode, readoutModes_);

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
   CreateProperty("MinTemp", tMin.str().c_str(), MM::Integer, true);
   CreateProperty("MaxTemp", tMax.str().c_str(), MM::Integer, true);
   // eof jizhen
   pAct = new CPropertyAction (this, &Ixon::OnTemperature);
   nRet = CreateProperty(MM::g_Keyword_CCDTemperature, "1", MM::Integer, false, pAct); //true, pAct); // jizhen 05.08.2007
   assert(nRet == DEVICE_OK);

   //jizhen 05.11.2007
   // Cooler
   pAct = new CPropertyAction (this, &Ixon::OnCooler);
   nRet = CreateProperty("Cooler", "0", MM::Integer, false, pAct);
   assert(nRet == DEVICE_OK);
   AddAllowedValue("Cooler", "0");
   AddAllowedValue("Cooler", "1");
   // eof jizhen

   //jizhen 05.16.2007
   // Fan
   pAct = new CPropertyAction (this, &Ixon::OnFanMode);
   nRet = CreateProperty("FanMode", "0", MM::Integer, false, pAct);
   assert(nRet == DEVICE_OK);
   AddAllowedValue("FanMode", "0"); // high
   AddAllowedValue("FanMode", "1"); // low
   AddAllowedValue("FanMode", "2"); // off

   // eof jizhen

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
   nRet = SetProperty(g_ShutterMode, g_ShutterMode_Auto);
   if (nRet != DEVICE_OK)
      return nRet;
   nRet = SetProperty(MM::g_Keyword_Exposure, "10.0");
   if (nRet != DEVICE_OK)
      return nRet;
   nRet = SetProperty(MM::g_Keyword_Gain, "0");
   if (nRet != DEVICE_OK)
      return nRet;
   nRet = SetProperty(MM::g_Keyword_EMGain, "0");
   if (nRet != DEVICE_OK)
      return nRet;
   nRet = SetProperty(MM::g_Keyword_ReadoutMode, readoutModes_[0].c_str());
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
   ShutDown();
   initialized_ = false;
   return DEVICE_OK;
}

/**
 * Acquires a single frame.
 * Micro-Manager expects that this function blocks the calling thread until the exposure phase is over.
 */
int Ixon::SnapImage()
{
   unsigned ret = ::StartAcquisition();
   if (ret != DRV_SUCCESS)
      return ret;

   // Wait until exposure time expires.
   // StartAcquisition returns immediately, so if didn't wait here
   // MMCore would close the shutter too soon.
   long startT = GetTickCount();
   long delta = 0;
   do
   {
      delta = GetTickCount() - startT;
   } while( delta < (long)(expMs_ + 50.0));
   // TODO: <<< hack to fix shutter closing too soon due to jitter - add 50ms to the exposure time

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

/**
 * Returns the raw image buffer.
 */
const unsigned char* Ixon::GetImageBuffer()
{
   // wait until the frame becomes available
   int status = DRV_IDLE;
   unsigned ret = GetStatus(&status);
   while (status == DRV_ACQUIRING && ret == DRV_SUCCESS)
      ret = GetStatus(&status);

   if (ret != DRV_SUCCESS)
      return 0;

   assert(fullFrameBuffer_ != 0);
   ret = GetAcquiredData16((WORD*)fullFrameBuffer_, roi_.xSize/binSize_ * roi_.ySize/binSize_);
   if (ret != DRV_SUCCESS)
      return 0;

   assert(img_.Depth() == 2);
   unsigned char* rawBuffer = const_cast<unsigned char*> (img_.GetPixels());
   memcpy(rawBuffer, fullFrameBuffer_, img_.Width() * img_.Height() * img_.Depth());

   // capture complete
   return (unsigned char*)rawBuffer;
}

/**
 * Sets the image Region of Interest (ROI).
 * The internal representation of the ROI uses the full frame coordinates
 * in combination with binning factor.
 */
int Ixon::SetROI(unsigned uX, unsigned uY, unsigned uXSize, unsigned uYSize)
{
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
   unsigned ret = ::GetBitDepth(0, &depth);
   if (ret != DRV_SUCCESS)
      depth = 0;
   return depth;
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
      pProp->Set(expMs_);
   }
   else if (eAct == MM::AfterSet)
   {
      double exp;
      pProp->Get(exp);
      unsigned ret = SetExposureTime((float)(exp / 1000.0));
      if (DRV_SUCCESS != ret)
         return (int)ret;
      expMs_ = exp;
   }
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
      string mode;
      pProp->Get(mode);
      for (unsigned i=0; i<readoutModes_.size(); ++i)
         if (readoutModes_[i].compare(mode) == 0)
         {
            unsigned ret = SetHSSpeed(0, i);
            if (DRV_SUCCESS != ret)
               return (int)ret;
            else
               return DEVICE_OK;
         }
      assert(!"Unrecognized readout mode");
   }
   else if (eAct == MM::BeforeGet)
   {
   }
   return DEVICE_OK;
}

/**
 * Provides information on readout time.
 * TODO: Not implemented
 */
int Ixon::OnReadoutTime(MM::PropertyBase* pProp, MM::ActionType eAct)
{
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
	  //jizhen 05.10.2007
	  if (gain < (long) EmCCDGainLow_ ) gain = (long)EmCCDGainLow_;
      if (gain > (long) EmCCDGainHigh_ ) gain = (long)EmCCDGainHigh_;
	  pProp->Set(gain);
	  // eof jizhen
      unsigned ret = SetEMCCDGain((int)gain);
      if (DRV_SUCCESS != ret)
         return (int)ret;
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
      long gain;
      pProp->Get(gain);
      unsigned ret = SetPreAmpGain((int)gain);
      if (DRV_SUCCESS != ret)
         return (int)ret;
   }
   else if (eAct == MM::BeforeGet)
   {
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
   }
   else if (eAct == MM::BeforeGet)
   {
      int temp;
	  //jizhen 05.11.2007
	  //ret = GetTemperature(&temp);
      int ret = GetTemperature(&temp);
#define iDebug
#ifdef iDebug
	  if ( ret == DRV_NOT_INITIALIZED) cout << ret << ": DRV_NOT_INITIALIZED" << endl;
	  else if ( ret == DRV_ACQUIRING) cout << ret << ": DRV_ACQUIRING" << endl;
	  else if ( ret == DRV_ERROR_ACK) cout << ret << ": DRV_ERROR_ACK" << endl;
	  else if ( ret == DRV_TEMP_OFF) cout << ret << ": DRV_TEMP_OFF" << endl;
	  else if ( ret == DRV_TEMP_STABILIZED) cout << ret << ": DRV_TEMP_STABILIZED" << endl;
	  else if ( ret == DRV_TEMP_NOT_REACHED) cout << ret << ": DRV_TEMP_NOT_REACHED" << endl;
	  else cout << ret << ": ???" << endl;
#endif
	  // eof jizhen
      pProp->Set((long)temp);
   }
   return DEVICE_OK;
}

//jizhen 05.11.2007
/**
 * Set cooler on/off.
 */
int Ixon::OnCooler(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      long OnOff;
      pProp->Get(OnOff);
	  unsigned ret;
	  if ( OnOff == 1 ) ret = CoolerON();
	  else ret = CoolerOFF();
      if (DRV_SUCCESS != ret)
         return (int)ret;
   }
   else if (eAct == MM::BeforeGet)
   {
      int temp;
      unsigned int ret = GetTemperature(&temp);
	  if ( ret == DRV_TEMP_OFF) pProp->Set( (long)0);
	  else pProp->Set( (long)1);
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
      long mode;
      pProp->Get(mode);
	  unsigned int ret;
	  ret = SetFanMode((int)mode);
      if (DRV_SUCCESS != ret)
         return (int)ret;
   }
   else if (eAct == MM::BeforeGet)
   {
   }
   return DEVICE_OK;
}
// eof jizhen

int Ixon::OnShutterMode(MM::PropertyBase* pProp, MM::ActionType eAct)
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

      // wait for camera to finish acquiring
      int status = DRV_IDLE;
      unsigned ret = GetStatus(&status);
      while (status == DRV_ACQUIRING && ret == DRV_SUCCESS)
         ret = GetStatus(&status);

      // the first parameter in SetShutter, must be "1" in order for
      // the shutter logic to work as described in the documentation
      ret = SetShutter(1, modeIdx, 0, 0);
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

///////////////////////////////////////////////////////////////////////////////
// Shutter
// ~~~~~~~
// iXon built-in shutter.

Shutter::Shutter() : initialized_(false), camera_(0)
{
   InitializeDefaultErrorMessages();
   camera_ = Ixon::GetInstance();
}

Shutter::~Shutter()
{
   this->Shutdown();
   //delete camera_;

   // jizhen 05.16.2007
   if ( camera_) {
	   unsigned refCount = camera_->DeReference();
	   if ( refCount <= 0) {
		   //delete camera_;
	   }// delete the camera_ here?
	   camera_=0;
   }
   // eof jizhen
}

void Shutter::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_IxonShutterName);
}

int Shutter::Initialize()
{

   if (camera_ == 0)
      return ERR_CAMERA_DOES_NOT_EXIST;

   int ret = camera_->Initialize();
   if (ret != DEVICE_OK)
      return ret;

   // set property list
   // -----------------

   // Name
   CreateProperty(MM::g_Keyword_Name, g_IxonShutterName, MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "Andor iXon built-in shutter adapter", MM::String, true);

   // State
   // -----
   CPropertyAction* pAct = new CPropertyAction (this, &Shutter::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   AddAllowedValue(MM::g_Keyword_State, "0");
   AddAllowedValue(MM::g_Keyword_State, "1");

   // Delay
   // -----
   pAct = new CPropertyAction (this, &Shutter::OnDelay);
   ret = CreateProperty(MM::g_Keyword_Delay, "0.0", MM::Float, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;
   return DEVICE_OK;
}

int Shutter::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

bool Shutter::Busy()
{
   return false;
}

int Shutter::SetOpen(bool open)
{
   long pos;
   if (open)
      pos = 1;
   else
      pos = 0;
   return SetProperty(MM::g_Keyword_State, CDeviceUtils::ConvertToString(pos));
}

int Shutter::GetOpen(bool& open)
{
   char buf[MM::MaxStrLength];
   int ret = GetProperty(MM::g_Keyword_State, buf);
   if (ret != DEVICE_OK)
      return ret;
   long pos = atol(buf);
   pos == 1 ? open = true : open = false;

   return DEVICE_OK;
}

int Shutter::Fire(double /*deltaT*/)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

int Shutter::SetShutterPosition(bool state)
{
   return camera_->SetProperty(g_ShutterMode, state ? g_ShutterMode_Open : g_ShutterMode_Closed);
}

/**
 * Check the state of the shutter.
 */
int Shutter::GetShutterPosition(bool& state)
{
   char mode[MM::MaxStrLength];
   int ret = camera_->GetProperty(g_ShutterMode, mode);
   if (ret != DEVICE_OK)
      return ret;

   if (strcmp(mode, g_ShutterMode_Open) == 0)
      state = true;
   else
      state = false;

   return ret;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int Shutter::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      bool state;
      int ret = GetShutterPosition(state);
      if (ret != DEVICE_OK)
         return ret;
      if (state)
         pProp->Set(1L);
      else
         pProp->Set(0L);
   }
   else if (eAct == MM::AfterSet)
   {
      long pos;
      pProp->Get(pos);

      // apply the value
      return SetShutterPosition(pos == 0 ? false : true);
   }

   return DEVICE_OK;
}

int Shutter::OnDelay(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(this->GetDelayMs());
   }
   else if (eAct == MM::AfterSet)
   {
      double delay;
      pProp->Get(delay);
      this->SetDelayMs(delay);
   }

   return DEVICE_OK;
}
