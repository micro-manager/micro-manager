   ///////////////////////////////////////////////////////////////////////////////
// FILE:       dc1394.cpp
// PROJECT:    MicroManage
// SUBSYSTEM:  DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:
// dc1394 camera_ module based on libdc1394 API
//                
// AUTHOR:        Nico Stuurman, 12/29/2006, contributions by Gregory Jefferis
//                
// COPYRIGHT:     University of California, San Francisco, 2006
//
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

#ifdef WIN32
   #pragma warning(disable : 4996) // disable warning for deperecated CRT functions on Windows 
   #define WIN32_LEAN_AND_MEAN
   #include <windows.h>
#else
   #include <memory.h>
   void ZeroMemory(void* mem, int size) 
   {
      memset(mem, 0, size);
   }
#endif

#include "../../MMDevice/ModuleInterface.h"
#include "dc1394.h"
#include <dc1394/control.h>
#include <dc1394/utils.h>
#include <dc1394/conversions.h>
#include <dc1394/vendor/avt.h>
#include <string>
#include <sstream>
#include <iomanip>
#include <math.h>

#include <iostream>

using namespace std;

// global constants
const char* g_DeviceName = "dc1394_CAM";
const char* g_PixelType_8bit = "8bit";
const char* g_PixelType_16bit = "16bit";
const char* g_Keyword_Modes = "Video Modes";
const char* g_Keyword_FrameRates = "Frame Rates";

// singleton instance
Cdc1394* Cdc1394::m_pInstance = 0;
unsigned Cdc1394::refCount_ = 0;

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
   AddAvailableDeviceName(g_DeviceName, "Firewire camera_s (IIDC compatible)");
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
      return Cdc1394::GetInstance();
   
   return 0;
}


///////////////////////////////////////////////////////////////////////////////
// Cdc1394 constructor/destructor

Cdc1394::Cdc1394() :
   isSonyXCDX700_(false),
   m_bInitialized(false),
   m_bBusy(false),
   snapInProgress_(false),
   frameRatePropDefined_(false),
   dmaBufferSize_(16),
   triedCaptureCount_(0),
   integrateFrameNumber_(1),
   maxNrIntegration(1),
   lnBin_(1),
   longestWait_(1,0),
   dequeued_(false),
   stopOnOverflow_(false),
   multi_shot_(false),
   acquiring_(false)
{
   SetErrorText(ERR_DC1394, "Could not initialize libdc1394.  Someting in your system is broken");
   SetErrorText(ERR_CAMERA_NOT_FOUND, "Did not find a IIDC firewire camera_");
   SetErrorText(ERR_SET_CAPTURE_FAILED, "Failed to set capture");
   SetErrorText(ERR_TRANSMISSION_FAILED, "Problem starting transmission");
   SetErrorText(ERR_MODE_LIST_NOT_FOUND, "Did not find camera_s mode list");
   SetErrorText(ERR_ROI_NOT_SUPPORTED, "ROIs not supported with this camera_");
   SetErrorText(ERR_MODE_NOT_AVAILABLE, "Requested mode not available on this camera_");
   SetErrorText(ERR_INITIALIZATION_FAILED, "Error Initializing the camera_.  Unplug and replug the camera_ and restart this application");
   SetErrorText(ERR_CAPTURE_TIMEOUT, "Timeout during image capture.  Increase the camera timeout, increase shutter speed, or decrease exposure time");

   InitializeDefaultErrorMessages();

   // setup map to hold data on video modes:
   this->SetVideoModeMap();
   this->SetFrameRateMap();
   
   // Name
   int nRet = CreateProperty(MM::g_Keyword_Name, g_DeviceName, MM::String, true);
   assert(nRet == DEVICE_OK);

   // Description
   nRet = CreateProperty(MM::g_Keyword_Description, "Firewire camera_ (IIDC) dc1394 adapter", MM::String, true);
   assert(nRet == DEVICE_OK);
   
   // GJ set these to false for now
   avtInterlaced_ = false;
   
   // Create a thread for burst mode
   acqThread_ = new AcqSequenceThread(this); 
}

Cdc1394::~Cdc1394()
{
   refCount_--;
   if (refCount_ == 0)
   {
      // release resources
      if (m_bInitialized)
         Shutdown();

      // clear the instance pointer
      m_pInstance = 0;

      // Clear the burst mode thread
      delete acqThread_;
   }
}

Cdc1394* Cdc1394::GetInstance()
{
   if (!m_pInstance)
      m_pInstance = new Cdc1394();

   refCount_++;
   return m_pInstance;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
// ~~~~~~~~~~~~~~~

int Cdc1394::OnMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      // find the key to the mode that was just set:
      string modeString;
      pProp->Get(modeString);
      std::map<dc1394video_mode_t, std::string>::iterator it = videoModeMap_.begin();
      bool found = false;
      while ((it != videoModeMap_.end()) && !found) 
      {
          if (it->second == modeString) {
             mode_ = it->first;
             found = true;
          }
          ++it;
      }
      if (found) 
      {
         LogMessage("Now changing mode...\n", true);
         dc1394_capture_stop(camera_);
         
         // stop transmission and make sure it stopped
         if (StopTransmission() != DEVICE_OK)
            LogMessage ("Error stopping transmission\n");

         // get the new color coding mode:
         dc1394_get_color_coding_from_video_mode(camera_, mode_, &colorCoding_);

         // Get the new bitdepth
         err_ = dc1394_video_get_data_depth(camera_, &depth_);
         if (err_ != DC1394_SUCCESS)
            LogMessage ("Error establishing bit-depth\n");
         logMsg_.str("");
         logMsg_ << "In OnMode, bitDepth is now" << depth_;
         LogMessage(logMsg_.str().c_str(), true);
         maxNrIntegration = 1 << (16-depth_);
         GetBytesPerPixel ();

         // reset the list of framerates allowed:
         if (SetUpFrameRates() != DEVICE_OK)
            LogMessage ("Error changing list of framerates\n");
         return ResizeImageBuffer();
      }
      else
         return ERR_MODE_NOT_AVAILABLE;
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set(videoModeMap_[mode_].c_str());
   }

   return DEVICE_OK;
}


int Cdc1394::OnFrameRate(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      string frameString;
      pProp->Get(frameString);
      // find the key to the frameRate that was just set:
      std::map<dc1394framerate_t, std::string>::iterator it = frameRateMap_.begin();
      bool found = false;
      while ((it != frameRateMap_.end()) && !found)
      {
          if (it->second == frameString) {
             framerate_ = it->first;
             found = true;
          }  
          ++it;                                                              
      }                                                                      
      if (found)                                                             
      {                                                                      
         // Transmission needs to be stopped before changing framerates
         dc1394_capture_stop(camera_);
         
         // stop transmission and make sure it stopped
         if (StopTransmission() != DEVICE_OK)
            LogMessage ("Error stopping transmission\n");

         // get the new color coding mode:
         dc1394_get_color_coding_from_video_mode(camera_, mode_, &colorCoding_);

         // Get the new bitdepth
         err_ = dc1394_video_get_data_depth(camera_, &depth_);
         if (err_ != DC1394_SUCCESS)
            LogMessage ("Error establishing bit-depth\n");
         maxNrIntegration = 1 << (16 - depth_);
         GetBytesPerPixel ();

         // Restart capture
         return ResizeImageBuffer();
      }                                                                      
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set(frameRateMap_[framerate_].c_str());
   }
   return DEVICE_OK;
}


// Binning, not supported yet
int Cdc1394::OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   return DEVICE_OK;
}

int Cdc1394::OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   double exposure_ms;
   uint32_t exposure_us;

   if(!absoluteShutterControl_) {
      return DEVICE_OK;
   }
   
   if (eAct == MM::AfterSet)
   {
      // Get the new exposure time (in ms)
      pProp->Get(exposure_ms);
      // Send the new exposure setting to the camera_ if we have absolute control
      if(camera_->vendor_id==AVT_VENDOR_ID){
         // AVT has vendor specific code for an extended shutter
         // this accepts shutter times in µs which makes it easier to work with
         // OK, set using extended register, MM exposure is in ms so *1000 -> us
         exposure_us = (uint32_t) 1000.0*exposure_ms;
         err_=dc1394_avt_set_extented_shutter(camera_,exposure_us);
         if(err_!=DC1394_SUCCESS) return DEVICE_ERR;
      } else {
         // set using ordinary absolute shutter dc1394 function
         // this expects a float in seconds
         float minAbsShutter, maxAbsShutter;
         err_ = dc1394_feature_get_absolute_boundaries(camera_, DC1394_FEATURE_SHUTTER, &minAbsShutter, &maxAbsShutter);
         float exposure_s=0.001f*(float)exposure_ms;
         if(minAbsShutter>exposure_s || exposure_s>maxAbsShutter) return DEVICE_ERR;
         
         err_=dc1394_feature_set_absolute_control(camera_,DC1394_FEATURE_SHUTTER,DC1394_ON);
         if(err_!=DC1394_SUCCESS) return DEVICE_ERR;

         err_=dc1394_feature_set_absolute_value(camera_,DC1394_FEATURE_SHUTTER,exposure_s);
         if(err_!=DC1394_SUCCESS) return DEVICE_ERR;		 
      }
   }
   else if (eAct == MM::BeforeGet)
   {  
      if(camera_->vendor_id==AVT_VENDOR_ID){
         // AVT has vendor specific code for an extended shutter
         // this accepts shutter times in µs which makes it easier to work with
         err_=dc1394_avt_get_extented_shutter(camera_,&exposure_us);
         if(err_!=DC1394_SUCCESS) return DEVICE_ERR;
         // convert it to milliseconds
         exposure_ms = 0.001 * (double) exposure_us;         
      } else {
          // set using ordinary absolute shutter dc1394 function
          // this expects a float in seconds
         float exposure_s;
         err_=dc1394_feature_get_absolute_value(camera_,DC1394_FEATURE_SHUTTER,&exposure_s);
         if(err_!=DC1394_SUCCESS) return DEVICE_ERR;
         exposure_ms=1000.0 * (double) exposure_s;
      }      
      pProp->Set(exposure_ms);
   }
   return DEVICE_OK;
}

int Cdc1394::OnIntegration(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   long tmp;
   if (eAct == MM::AfterSet)
   {
      pProp->Get(tmp);
      if (tmp > maxNrIntegration)
         integrateFrameNumber_ = maxNrIntegration;
      else if (tmp < 1)
         integrateFrameNumber_ = 1;
      else if ( (tmp >= 1) && (tmp <= maxNrIntegration) )
         integrateFrameNumber_ = tmp;
      GetBytesPerPixel();
      img_.Resize(width, height, bytesPerPixel_);
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set((long)integrateFrameNumber_);
   }
   return DEVICE_OK;
}


int Cdc1394::OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   /*
   ccDatatype ccDataType;
   if (eAct == MM::BeforeGet)
   {
      if (!dcam_getdatatype(m_hDCAM, &ccDataType))
         return dcam_getlasterr_or(m_hDCAM, NULL, 0);

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
         return dcam_getlasterr_or(m_hDCAM, NULL, 0);

      nRet = ResizeImageBuffer();
      if (nRet != DEVICE_OK)
         return nRet;
   }
   */
   return DEVICE_OK;
}

// ScanMode
int Cdc1394::OnScanMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   return DEVICE_OK;
}

// ReadoutTime
int Cdc1394::OnTimeout(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      double to = longestWait_.getMsec();
      pProp->Set(to);
   } else if (eAct == MM::AfterSet) {
      double to;
      pProp->Get(to);
      longestWait_ = MM::MMTime(0, (long) to * 1000l);
   }
   return DEVICE_OK;
}

// Generic 'OnFeature', works only for integer values 
int Cdc1394::OnFeature(MM::PropertyBase* pProp, MM::ActionType eAct, uint32_t &value, int valueMin, int valueMax, dc1394feature_t feature)
{
   long tmp;
   if (eAct == MM::AfterSet)
   {
      pProp->Get(tmp);
      if (tmp < valueMin)
         value = valueMin;
      else if (tmp > valueMax)
         value = valueMax;
      else if ( (tmp >= valueMin) && (tmp <= valueMax) )
         value = (uint32_t) tmp;
	  // First make sure we are in manual mode, otherwise changing the value has little effect 
	  err_ = dc1394_feature_set_mode(camera_, feature, DC1394_FEATURE_MODE_MANUAL);
	  // set to value
      err_ = dc1394_feature_set_value(camera_, feature, value);
      logMsg_.str("");
      logMsg_ << "Settings feature " << feature << " to " << value <<  " result: " << err_;
      LogMessage(logMsg_.str().c_str(), true);
	  // we may have changed to manual
	  OnPropertiesChanged();
   }
   else if (eAct == MM::BeforeGet)
   {
      err_ = dc1394_feature_get_value(camera_, feature, &value);
      logMsg_.str("");
      logMsg_ << "Getting feature " << feature << ".  It is now " << value << " err: " << err_;
      LogMessage (logMsg_.str().c_str(), true);
      tmp = (long)  value;
      pProp->Set(tmp);
   }
   return DEVICE_OK;
}


int Cdc1394::OnFeatureMode(MM::PropertyBase* pProp, MM::ActionType eAct, dc1394feature_t feature)
{
	dc1394switch_t pwSwitch;
	dc1394feature_mode_t mode;
	std::string value;
	
	if (eAct == MM::AfterSet) // property was written -> apply value to hardware
	{
		pProp->Get(value);
		if ( value == "OFF" ) {
			err_ = dc1394_feature_set_power(camera_, feature, DC1394_OFF );
		} else {
			if ( value == "MANUAL" )
				mode = DC1394_FEATURE_MODE_MANUAL;
			else if ( value == "AUTO" )
				mode = DC1394_FEATURE_MODE_AUTO;
			else
				mode = DC1394_FEATURE_MODE_ONE_PUSH_AUTO;
			err_ = dc1394_feature_set_mode(camera_, feature, mode);
			if ( mode == DC1394_FEATURE_MODE_ONE_PUSH_AUTO) {
				// set to MANUAL after one push auto
				err_ = dc1394_feature_set_mode(camera_, feature, DC1394_FEATURE_MODE_MANUAL);
			}
		}
		
		OnPropertiesChanged();
		
	}
	else if (eAct == MM::BeforeGet) // property will be read -> update property with value from hardware
	{  	
		err_ = dc1394_feature_get_power(camera_, feature, &pwSwitch );
		if ( pwSwitch == DC1394_OFF ) {
			pProp->Set("OFF");
		}
		else {
			dc1394_feature_get_mode(camera_, feature, &mode);
			// write back to GUI
			switch ( mode ) {
				case DC1394_FEATURE_MODE_MANUAL:
				case DC1394_FEATURE_MODE_ONE_PUSH_AUTO:
					pProp->Set("MANUAL");
					break;
				case DC1394_FEATURE_MODE_AUTO:
					pProp->Set("AUTO");
					break;
			}
		}
	}	
	
	return DEVICE_OK; 
}


//
// Brightness
int Cdc1394::OnBrightness(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	return OnFeature(pProp, eAct, brightness, brightnessMin, brightnessMax, DC1394_FEATURE_BRIGHTNESS);
}


int Cdc1394::OnBrightnessMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	return OnFeatureMode(pProp, eAct, DC1394_FEATURE_BRIGHTNESS);
}


// Hue
int Cdc1394::OnHue(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	return OnFeature(pProp, eAct, brightness, brightnessMin, brightnessMax, DC1394_FEATURE_HUE);
}


int Cdc1394::OnHueMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	return OnFeatureMode(pProp, eAct, DC1394_FEATURE_HUE);
}


// Saturation
int Cdc1394::OnSaturation(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	return OnFeature(pProp, eAct, brightness, brightnessMin, brightnessMax, DC1394_FEATURE_SATURATION);
}


int Cdc1394::OnSaturationMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	return OnFeatureMode(pProp, eAct, DC1394_FEATURE_SATURATION);
}


// Gain
int Cdc1394::OnGain(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  return OnFeature(pProp, eAct, gain, gainMin, gainMax, DC1394_FEATURE_GAIN);
}


int Cdc1394::OnGainMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	return OnFeatureMode(pProp, eAct, DC1394_FEATURE_GAIN);
}

// Gamma
int Cdc1394::OnGamma(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   return OnFeature(pProp, eAct, gain, gainMin, gainMax, DC1394_FEATURE_GAMMA);
}


int Cdc1394::OnGammaMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	return OnFeatureMode(pProp, eAct, DC1394_FEATURE_GAMMA);
}


// Temperature
int Cdc1394::OnTemp(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	return OnFeature(pProp, eAct, gain, gainMin, gainMax, DC1394_FEATURE_TEMPERATURE);
}


int Cdc1394::OnTempMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	return OnFeatureMode(pProp, eAct, DC1394_FEATURE_TEMPERATURE);
}


// Shutter
int Cdc1394::OnShutter(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if((eAct == MM::AfterSet) && absoluteShutterControl_){
      // Need to turn off absolute mode so that we can set it using integer shutter values
      dc1394_feature_set_absolute_control(camera_, DC1394_FEATURE_SHUTTER, DC1394_OFF);
   }
   return OnFeature(pProp, eAct, shutter, shutterMin, shutterMax, DC1394_FEATURE_SHUTTER);
}


int Cdc1394::OnShutterMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	return OnFeatureMode(pProp, eAct, DC1394_FEATURE_SHUTTER);
}


// Exposure
int Cdc1394::OnExposureMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	return OnFeatureMode(pProp, eAct, DC1394_FEATURE_EXPOSURE);
}


// White balance
// Derived from generic 'OnFeature'
int Cdc1394::OnColorFeature(MM::PropertyBase* pProp, MM::ActionType eAct, uint32_t &value, int valueMin, int valueMax, colorAdjustment valueColor)
{
	long tmp;
	uint32_t ub,vr;
	uint32_t red, green, blue;
	
	if (eAct == MM::AfterSet)
	{
		pProp->Get(tmp);
		if (tmp < valueMin)
			value = valueMin;
		else if (tmp > valueMax)
			value = valueMax;
		else if ( (tmp >= valueMin) && (tmp <= valueMax) )
			value = (uint32_t) tmp;
		
		if ( valueColor == COLOR_UB || valueColor == COLOR_VR ) {
			// Set Whitebalance
			// Find the "other" value first
			err_ = dc1394_feature_whitebalance_get_value(camera_, &ub, &vr);
			std::cerr << "DC1394: Whitebalance values: ub=" << ub << ", vr=" << vr << std::endl;
			// Make sure we are in manual mode, otherwise changing the value has little effect 
			err_ = dc1394_feature_set_mode(camera_, DC1394_FEATURE_WHITE_BALANCE, DC1394_FEATURE_MODE_MANUAL);
			// set to value
			if ( valueColor == COLOR_UB )
				ub = value;
			else 
				vr = value;
			err_ = dc1394_feature_whitebalance_set_value(camera_, ub, vr);
			std::cerr << "DC1394: Whitebalance setting to values: ub=" << ub << ", vr=" << vr << std::endl;
			logMsg_.str("");
			logMsg_ << "Settings whitebalance " << " to " << value <<  " result: " << err_;
			LogMessage(logMsg_.str().c_str(), true);
		} else {
			// Set White Shading
			// Find the "other" value first
			err_ = dc1394_feature_whiteshading_get_value(camera_, &red, &green, &blue);
			// Make sure we are in manual mode, otherwise changing the value has little effect 
			err_ = dc1394_feature_set_mode(camera_, DC1394_FEATURE_WHITE_SHADING, DC1394_FEATURE_MODE_MANUAL);
			switch (valueColor) {
				case COLOR_RED:
					red = value;;
					break;
				case COLOR_GREEN:
					green = value;
					break;
				case COLOR_BLUE:
					blue = value;
					break;
				default:
					break;
			}
			err_ = dc1394_feature_whiteshading_get_value(camera_, &red, &green, &blue);
		}

		// we may have changed to manual
		OnPropertiesChanged();
	}
	else if (eAct == MM::BeforeGet)
	{
		if ( valueColor == COLOR_UB || valueColor == COLOR_VR ) {
			// White balance
			err_ = dc1394_feature_whitebalance_get_value(camera_, &ub, &vr);
			logMsg_.str("");
			logMsg_ << "Getting whitebalance " << ".  It is now " << value << " err: " << err_;
			LogMessage (logMsg_.str().c_str(), true);
			tmp = (long) valueColor == COLOR_UB ? ub : vr;
		} else {
			// White Shading
			err_= dc1394_feature_whiteshading_get_value(camera_, &red, &green, &blue);
			switch (valueColor) {
				case COLOR_RED:
					tmp = (long) red;
					break;
				case COLOR_GREEN:
					tmp = (long) green;
					break;
				case COLOR_BLUE:
					tmp = (long) blue;
					break;
				default:
					break;
			}
		}

		pProp->Set(tmp);
	}
	return DEVICE_OK;
}


int Cdc1394::OnWhitebalanceMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	return OnFeatureMode(pProp, eAct, DC1394_FEATURE_WHITE_BALANCE);
}


int Cdc1394::OnWhitebalanceUB(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	return OnColorFeature(pProp, eAct, colub, colMin, colMax, COLOR_UB);
}


int Cdc1394::OnWhitebalanceVR(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	return OnColorFeature(pProp, eAct, colvr, colMin, colMax, COLOR_VR);
}


int Cdc1394::OnWhiteshadingMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	return OnFeatureMode(pProp, eAct, DC1394_FEATURE_WHITE_SHADING);
}


int Cdc1394::OnWhiteshadingRed(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	return OnColorFeature(pProp, eAct, colred, colMin, colMax, COLOR_RED);
}


int Cdc1394::OnWhiteshadingBlue(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	return OnColorFeature(pProp, eAct, colblue, colMin, colMax, COLOR_BLUE);
}


int Cdc1394::OnWhiteshadingGreen(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	return OnColorFeature(pProp, eAct, colgreen, colMin, colMax, COLOR_GREEN);
}




// ExternalTrigger
int Cdc1394::OnExternalTrigger(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   dc1394switch_t pwr;
   long triggerStatus;
   if (eAct == MM::AfterSet)
   {
      pProp->Get(triggerStatus);
      pwr = (triggerStatus==1L) ? DC1394_ON : DC1394_OFF;
      err_ = dc1394_external_trigger_set_power(camera_, pwr);
      logMsg_.clear();
      logMsg_ << "Setting external trigger state:" << triggerStatus << " err_:" << err_;
      LogMessage(logMsg_.str().c_str(), true);
   }
   else if (eAct == MM::BeforeGet)
   {
      err_ = dc1394_external_trigger_get_power(camera_, &pwr);
      logMsg_.str("");
      logMsg_ << "Getting external trigger state:" << triggerStatus << " err_: " << err_;
      LogMessage (logMsg_.str().c_str(), true);
      triggerStatus = (pwr==DC1394_ON) ? 1L : 0L;
      pProp->Set(triggerStatus);
   }
   return DEVICE_OK;
}



///////////////////////////////////////////////////////////////////////////////
// API methods
// ~~~~~~~~~~~

void Cdc1394::GetName(char* name) const 
{
   CDeviceUtils::CopyLimitedString(name, g_DeviceName);
}

///////////////////////////////////////////////////////////////////////////////
// Function name   : Cdc1394::Initialize
// Description     : Initialize the camera_
// Return type     : bool 

int Cdc1394::Initialize()
{
   // setup the camera_
   // ----------------
   int nRet = GetCamera();
   if (nRet != DEVICE_OK)
      return nRet;

   // We have  a handle to the camera_ now:
   m_bInitialized = true;

   // Figure out what this camera_ can do
   // get supported modes
   if (dc1394_video_get_supported_modes(camera_, &modes_)!=DC1394_SUCCESS)
      return ERR_MODE_LIST_NOT_FOUND;                         
   for (unsigned int i = 0; i < modes_.num; i++) {
      logMsg_.str("");
      logMsg_ << "Mode found: " << modes_.modes[i];
      LogMessage (logMsg_.str().c_str(), true);
   }

   // Camera video modes, default to the first mode found
   CPropertyAction *pAct = new CPropertyAction (this, &Cdc1394::OnMode);
   mode_ = modes_.modes[0];
   
   logMsg_.str("");
   logMsg_ << "Camera vendor/model is: " << camera_->vendor << "/" << camera_->model;
   LogMessage (logMsg_.str().c_str(), true);

   logMsg_.str("");
   logMsg_ << "Camera vendor/model id is: " << camera_->vendor_id << "/" <<camera_->model_id;
   LogMessage (logMsg_.str().c_str(), true);

   if (!strncmp("XCD-X700", camera_->model, 8)) {
      isSonyXCDX700_ = true;
      LogMessage("Found a Sony XCD-X700 camera");
   }

   // TODO once we can set the interlacing up externally
   // check if appropriate property is set
   if(0) {
	   
   } else if(!strncmp("Guppy F025",camera_->model,10) ||
			 !strncmp("Guppy F029",camera_->model,10) || 
			 !strncmp("Guppy F038",camera_->model,10) ||
			 !strncmp("Guppy F044",camera_->model,10) ) {
	   avtInterlaced_ = true;
      logMsg_.str("");
	   logMsg_ << "Camera is Guppy interlaced series, setting interlace = true";
	   LogMessage (logMsg_.str().c_str(), true);
   }
   else avtInterlaced_ = false;
   
   nRet = CreateProperty(g_Keyword_Modes, videoModeMap_[mode_].c_str(), MM::String, false, pAct);
   assert(nRet == DEVICE_OK);
   vector<string> modeValues;
   for (unsigned int i=0; i<modes_.num; i++) {
      string tmp = videoModeMap_[ modes_.modes[i] ];
      if (tmp != "") 
         modeValues.push_back(tmp);
   }   
   nRet = SetAllowedValues(g_Keyword_Modes, modeValues);
   if (nRet != DEVICE_OK)
      return nRet;

   // get the current color coding mode:
   dc1394_get_color_coding_from_video_mode(camera_, mode_, &colorCoding_);
   if ( IsColor() ) 
   {
	   logMsg_.str("");
	   logMsg_ << "Found DC1394 color camera: " << colorCoding_;
	   LogMessage(logMsg_.str().c_str(), true);
   } else {
	   logMsg_.str("");
	   logMsg_ << "Found DC1394 monochrome camera: " << colorCoding_;
	   LogMessage(logMsg_.str().c_str(), true);
   }


   // FrameRate, this is packaged in a function since it will need to be reset whenever the video Mode changes
   nRet = SetUpFrameRates();
   if (nRet != DEVICE_OK)
      return nRet;

   // Get the new bitdepth
   err_ = dc1394_video_get_data_depth(camera_, &depth_);
   if (err_ != DC1394_SUCCESS)
      LogMessage ("Error establishing bit-depth\n");
   logMsg_.str("");
   logMsg_ << "BitDepth is " << depth_;
   LogMessage(logMsg_.str().c_str(), true);
   maxNrIntegration = 1 << (16 - depth_);
   GetBytesPerPixel();

   // Frame integration
   if (depth_ == 8)
   {
      pAct = new CPropertyAction (this, &Cdc1394::OnIntegration);
      nRet = CreateProperty("Integration", "1", MM::Integer, false, pAct);
      assert(nRet == DEVICE_OK);
   }


   // binning, not implemented, would need software binning
   pAct = new CPropertyAction (this, &Cdc1394::OnBinning);
   nRet = CreateProperty(MM::g_Keyword_Binning, "1", MM::Integer, false, pAct);
   assert(nRet == DEVICE_OK);
   vector<string> binValues;
   binValues.push_back("1");
   nRet = SetAllowedValues(MM::g_Keyword_Binning, binValues);
   if (nRet != DEVICE_OK)
      return nRet;

   pAct = new CPropertyAction(this, &Cdc1394::OnTimeout);
   nRet = CreateProperty("Timeout(ms)", "1000", MM::Float, false, pAct);
   if (nRet != DEVICE_OK)
      return nRet;

   // pixel type, defaults to 8-bit for now
   pAct = new CPropertyAction (this, &Cdc1394::OnPixelType);
   if (depth_ <= 8)
      nRet = CreateProperty(MM::g_Keyword_PixelType, g_PixelType_8bit, MM::String, false, pAct);
   else
      nRet = CreateProperty(MM::g_Keyword_PixelType, g_PixelType_16bit, MM::String, false, pAct);
   assert(nRet == DEVICE_OK);
   vector<string> pixelTypeValues;
      pixelTypeValues.push_back(g_PixelType_8bit);
   nRet = SetAllowedValues(MM::g_Keyword_PixelType, pixelTypeValues);
   if (nRet != DEVICE_OK)
      return nRet;

   // camera_ _gain
   /*
   pAct = new CPropertyAction (this, &Cdc1394::OnGain);
   nRet = CreateProperty(MM::g_Keyword_Gain, "1", MM::Integer, false, pAct);
   assert(nRet == DEVICE_OK);
   */

   // CameraName
   char	CameraName[(2 * 256) + 4] = "Unrecognized";
   strcpy (CameraName, camera_->vendor);
   strcat (CameraName, ", ");
   strcat (CameraName, camera_->model);
   nRet = CreateProperty("Vendor Info", CameraName, MM::String, true);
   assert(nRet == DEVICE_OK);
		      
   // Other features, ask what the camera_ _has to offer
   if (dc1394_feature_get_all(camera_,&features_) !=DC1394_SUCCESS) 
         return ERR_GET_CAMERA_FEATURE_SET_FAILED;                         
   int j = 0;
   for (int i= DC1394_FEATURE_MIN; i <= DC1394_FEATURE_MAX; i++, j++)  
   {
       featureInfo_ = features_.feature[j];
       if (featureInfo_.available) {
          const char *featureLabel = dc1394_feature_get_string(featureInfo_.id);
          if (strcmp(featureLabel, "Brightness") ==0) 
          {
			  bool hasManual = InitFeatureMode(featureInfo_, DC1394_FEATURE_BRIGHTNESS, "BrightnessSetting", &Cdc1394::OnBrightnessMode);
			  if (hasManual)
			  {
				  InitFeatureManual(featureInfo_, "Brightness", brightness, brightnessMin, brightnessMax, &Cdc1394::OnBrightness);
			  }
          } 
          else if (strcmp(featureLabel, "Gain") == 0) 
          {
			 bool hasManual = InitFeatureMode(featureInfo_, DC1394_FEATURE_GAIN, "GainSetting", &Cdc1394::OnGainMode);
			 if (hasManual)
			 {
				  InitFeatureManual(featureInfo_, MM::g_Keyword_Gain, gain, gainMin, gainMax, &Cdc1394::OnGain);
			 }
		  }
          else if (strcmp(featureLabel, "Shutter") == 0) 
          {
			  bool hasManual = InitFeatureMode(featureInfo_, DC1394_FEATURE_GAIN, "ShutterSetting", &Cdc1394::OnShutterMode);
			  if (hasManual)
			  {
				  InitFeatureManual(featureInfo_, "Shutter", shutter, shutterMin, shutterMax, &Cdc1394::OnShutter);
			  }
			  
             // Check if shutter has absolute control
             dc1394bool_t absolute;
             absoluteShutterControl_ = false;
             err_ = dc1394_feature_has_absolute_control(camera_, DC1394_FEATURE_SHUTTER, &absolute);
             if(absolute==DC1394_TRUE) {
                absoluteShutterControl_ = true;
             }

             if(!absoluteShutterControl_ && camera_->vendor_id==AVT_VENDOR_ID){
                logMsg_.str("");
                logMsg_ << "Checking AVT absolute shutter\n";
                LogMessage (logMsg_.str().c_str(), false);
                // for AVT camera_s, check if we have access to the extended shutter mode
                uint32_t timebase_id;
                err_=dc1394_avt_get_extented_shutter(camera_,&timebase_id);
                if(err_==DC1394_SUCCESS) absoluteShutterControl_ = true;
             }
             logMsg_.str("");
             if(absoluteShutterControl_){
                logMsg_ << "Absolute shutter";
                LogMessage (logMsg_.str().c_str(), false);                
             } else { 
                logMsg_ << " No absolute shutter";
                LogMessage (logMsg_.str().c_str(), false);                
             }
          }
          else if (strcmp(featureLabel, "Exposure") == 0) 
          {
             // Offer option to switch between auto, manual and one-push modes
			 InitFeatureMode(featureInfo_, DC1394_FEATURE_EXPOSURE, "ExposureSetting", &Cdc1394::OnExposureMode);
             err_ = dc1394_feature_get_value(camera_, DC1394_FEATURE_EXPOSURE, &exposure);
             err_ = dc1394_feature_get_boundaries(camera_, DC1394_FEATURE_EXPOSURE, &exposureMin, &exposureMax);
          }
          else if (strcmp(featureLabel, "Trigger") == 0) 
          {
              logMsg_.str("");
              logMsg_ << "Checking External Trigger Status";
              LogMessage (logMsg_.str().c_str(), false);
              // Fetch the actual trigger status from the camera_
              dc1394switch_t pwr;
              err_ = dc1394_external_trigger_get_power(camera_,&pwr);
              // and reset the trigger setting to OFF to avoid camera_ startup problems
              if(err_==DC1394_SUCCESS && pwr==DC1394_ON) {
                  pwr=DC1394_OFF;
                  err_ = dc1394_external_trigger_set_power(camera_,pwr);
              }
              if(err_==DC1394_SUCCESS) {
                 // if(pwr==DC1394_ON) logMsg_ << "External trigger currently on\n";
                 // if(pwr==DC1394_OFF) logMsg_ << "External trigger currently off\n";
                 // LogMessage (logMsg_.str().c_str(), false);
          
                 pAct = new CPropertyAction (this, &Cdc1394::OnExternalTrigger);
 
                 logMsg_.str("");
                 logMsg_ << "Creating ExternalTrigger property";
                 LogMessage (logMsg_.str().c_str(), false);
                
                 nRet = CreateProperty("ExternalTrigger", 
                    pwr==DC1394_ON ? "1" : "0", MM::Integer, false, pAct); 
                 assert(nRet == DEVICE_OK);
                 AddAllowedValue("ExternalTrigger", "0"); // Closed
                 AddAllowedValue("ExternalTrigger", "1"); // Open             
             } else {
                logMsg_.str("");
                logMsg_ << "Unable to check external trigger status\n";
                LogMessage (logMsg_.str().c_str(), false);
             }
           }
		   // EF: addtional camera parameters to play around with
		   else if (strcmp(featureLabel, "Gamma") == 0) 
		   {
			   bool hasManual = InitFeatureMode(featureInfo_, DC1394_FEATURE_GAIN, "GammaSetting", &Cdc1394::OnGammaMode);
			   if (hasManual)
			   {
				   InitFeatureManual(featureInfo_, "Gamma", gamma, gammaMin, gammaMax, &Cdc1394::OnGamma);
			   }
		   }
		   else if (strcmp(featureLabel, "Hue") == 0) 
		   {
			   bool hasManual = InitFeatureMode(featureInfo_, DC1394_FEATURE_GAIN, "HueSetting", &Cdc1394::OnHueMode);
			   if (hasManual)
			   {
				   InitFeatureManual(featureInfo_, "Hue", hue, hueMin, hueMax, &Cdc1394::OnHue);
			   }
		   }
		   else if (strcmp(featureLabel, "Saturation") == 0) 
		   {
			   bool hasManual = InitFeatureMode(featureInfo_, DC1394_FEATURE_GAIN, "SaturationSetting", &Cdc1394::OnSaturationMode);
			   if (hasManual)
			   {
				   InitFeatureManual(featureInfo_, "Saturation", saturation, saturationMin, saturationMax, &Cdc1394::OnSaturation);
			   }
		   }
		   else if (strcmp(featureLabel, "Temperature") == 0) 
		   {
			   bool hasManual = InitFeatureMode(featureInfo_, DC1394_FEATURE_GAIN, "TemperatureSetting", &Cdc1394::OnTempMode);
			   if (hasManual)
			   {
				   InitFeatureManual(featureInfo_, "Temperature", temperature, temperatureMin, temperatureMax, &Cdc1394::OnTemp);
			   }
		   }
		   else if (strcmp(featureLabel, "White Balance") == 0) 
		   {
			   bool hasManual = InitFeatureMode(featureInfo_, DC1394_FEATURE_GAIN, "WhiteBalanceSetting", &Cdc1394::OnWhitebalanceMode);
			   
			   if (hasManual)
			   {
				   // Check that this feature is read-out capable
				   if ( featureInfo_.readout_capable )
				   {	
					   colub = featureInfo_.BU_value;
					   colvr = featureInfo_.RV_value;
				   }
				   colMin = 0;
				   colMax = 255;
				   
				   char tmp[10];
				   pAct = new CPropertyAction (this, &Cdc1394::OnWhitebalanceUB);
				   sprintf(tmp,"%d",colub);
				   nRet = CreateProperty("WhitebalanceUB", tmp, MM::Integer, false, pAct);
				   assert(nRet == DEVICE_OK);
				   nRet = SetPropertyLimits("WhitebalanceUB", 0, 1000);
				   assert(nRet == DEVICE_OK);
				   pAct = new CPropertyAction (this, &Cdc1394::OnWhitebalanceVR);
				   sprintf(tmp,"%d",colvr);
				   nRet = CreateProperty("WhitebalanceVR", tmp, MM::Integer, false, pAct);
				   assert(nRet == DEVICE_OK);
				   nRet = SetPropertyLimits("WhitebalanceVR", 0, 1000);
				   assert(nRet == DEVICE_OK);
			   }
		   }
		   else if (strcmp(featureLabel, "White Shading") == 0) 
		   {
			   bool hasManual = InitFeatureMode(featureInfo_, DC1394_FEATURE_GAIN, "WhiteShadingSetting", &Cdc1394::OnWhiteshadingMode);
			   
			   if (hasManual)
			   {
				   // Check that this feature is read-out capable
				   if ( featureInfo_.readout_capable )
				   {	
					   colred = featureInfo_.R_value;
					   colgreen = featureInfo_.G_value;
					   colblue = featureInfo_.B_value;
				   }
				   colMin = 0;
				   colMax = 255;
				   
				   char tmp[10];
				   pAct = new CPropertyAction (this, &Cdc1394::OnWhiteshadingRed);
				   sprintf(tmp,"%d",colub);
				   nRet = CreateProperty("WhiteshadingRed", tmp, MM::Integer, false, pAct);
				   assert(nRet == DEVICE_OK);
				   nRet = SetPropertyLimits("WhiteshadingRed", 0, 1000);
				   assert(nRet == DEVICE_OK);
				   pAct = new CPropertyAction (this, &Cdc1394::OnWhiteshadingGreen);
				   sprintf(tmp,"%d",colub);
				   nRet = CreateProperty("WhiteshadingGreen", tmp, MM::Integer, false, pAct);
				   assert(nRet == DEVICE_OK);
				   nRet = SetPropertyLimits("WhiteshadingGreen", 0, 1000);
				   assert(nRet == DEVICE_OK);
				   pAct = new CPropertyAction (this, &Cdc1394::OnWhiteshadingBlue);
				   sprintf(tmp,"%d",colub);
				   nRet = CreateProperty("WhiteshadingBlue", tmp, MM::Integer, false, pAct);
				   assert(nRet == DEVICE_OK);
				   nRet = SetPropertyLimits("WhiteshadingBlue", 0, 1000);
				   assert(nRet == DEVICE_OK);
			   }
		   }
		   
	   }
   }

   
   // exposure
   if(absoluteShutterControl_) {
      pAct = new CPropertyAction (this, &Cdc1394::OnExposure);
      nRet = CreateProperty(MM::g_Keyword_Exposure, "10.0", MM::Float, false, pAct);
      assert(nRet == DEVICE_OK);
   }

   // synchronize all properties
   // --------------------------
   //nRet = UpdateStatus();
   //if (nRet != DEVICE_OK)
   //   return nRet;

   // setup the buffer
   nRet = ResizeImageBuffer();
   if (nRet != DEVICE_OK)
      return nRet;

   // We seem to need this on the Mac...
   SetProperty(MM::g_Keyword_Binning,"1");

   return DEVICE_OK;
}


bool Cdc1394::InitFeatureMode(dc1394feature_info_t &featureInfo, dc1394feature_t feature, const char *featureLabel, int (Cdc1394::*cb_onfeaturemode)(MM::PropertyBase*, MM::ActionType) )
{
	enum dcModes { NONE =0, OFF=1, MANUAL=2, ONE_PUSH=4, AUTO=8 };
	int modeAvail = NONE;
	int dcModeCt = 0;
	dc1394feature_mode_t modeDefault = DC1394_FEATURE_MODE_MANUAL;
	
	// First make sure the feature is switched on by default
	if ( err_ == DC1394_SUCCESS && featureInfo.on_off_capable )
	{
		modeAvail |= OFF; dcModeCt++;
		dc1394_feature_set_power(camera_, feature, DC1394_ON);
		std::cerr << featureLabel << ": Setting power on\n";
	}
	// Find out what modes are available, set by default to manual, override to auto if available
	for (int mod_no = 0; mod_no < featureInfo_.modes.num; mod_no++)  
	{
		switch ( featureInfo_.modes.modes[mod_no] ) {
			case DC1394_FEATURE_MODE_MANUAL:
				modeAvail |= MANUAL; dcModeCt++;
				std::cerr << featureLabel << ": Found manual mode\n";
				break;
			case DC1394_FEATURE_MODE_AUTO:
				modeAvail |= AUTO; dcModeCt++;
				modeDefault = DC1394_FEATURE_MODE_AUTO;
				std::cerr << featureLabel << ": Found auto mode\n";
				break;
			case DC1394_FEATURE_MODE_ONE_PUSH_AUTO:
				modeAvail |= ONE_PUSH; dcModeCt++;
				std::cerr << featureLabel << ": Found one-push mode\n";
				break;
		}
	}
	
	err_ = dc1394_feature_set_mode(camera_, feature, modeDefault);

	if ( dcModeCt > 1 )
	{	
		CPropertyAction *pAct = new CPropertyAction (this, cb_onfeaturemode);
		if ( modeDefault == DC1394_FEATURE_MODE_MANUAL ) 
		{
			CreateProperty(featureLabel, "MANUAL", MM::String, false, pAct );
		}
		else 
		{
			CreateProperty(featureLabel, "AUTO", MM::String, false, pAct );
		}
		
		if ( modeAvail & OFF )
			AddAllowedValue( featureLabel, "OFF");
		if ( modeAvail & MANUAL )
			AddAllowedValue( featureLabel, "MANUAL");
		if ( modeAvail & ONE_PUSH	)
			AddAllowedValue( featureLabel, "ONE-PUSH");
		if ( modeAvail & AUTO )
			AddAllowedValue( featureLabel, "AUTO");			  
	}
	
	if ( modeAvail & MANUAL ) {
		return true;
	} else {
		return false;
	}

}	


void Cdc1394::InitFeatureManual(dc1394feature_info_t &featureInfo, const char *featureLabel, uint32_t &value, uint32_t &valueMin, uint32_t &valueMax, int (Cdc1394::*cb_onfeature)(MM::PropertyBase*, MM::ActionType))
{
	int nRet;
	
	// Check that this feature is read-out capable
	if ( featureInfo.readout_capable )
	{	
		value = featureInfo.value;
		valueMin = featureInfo.min;
		valueMax = featureInfo.max;
		logMsg_.str("");
		logMsg_ << featureLabel << " " <<  value;
		LogMessage (logMsg_.str().c_str(), false);
		logMsg_.str("");
		logMsg_ << featureLabel << " Min: " << valueMin  << " Max: " << valueMax;
		LogMessage (logMsg_.str().c_str(), false);
	}
	else 
	{
		// some sensible (?) default values
		value = 0;
		valueMin = 0;
		valueMax = 1000;
	}
	
	char tmp[10];
	CPropertyAction *pAct = new CPropertyAction (this, cb_onfeature);
	sprintf(tmp,"%d",value);
	nRet = CreateProperty(featureLabel, tmp, MM::Integer, false, pAct);
	assert(nRet == DEVICE_OK);
	nRet = SetPropertyLimits(featureLabel, valueMin, valueMax);
	assert(nRet == DEVICE_OK);
} 



///////////////////////////////////////////////////////////////////////////////
// Function name   : Cdc1394::Shutdown
// Description     : Deactivate the camera_, reverse the initialization process
// Return type     : bool 

int Cdc1394::Shutdown()
{
   if (m_bInitialized)
   {
      // TODO: Added err_or handling
      err_ = dc1394_capture_stop(camera_);
      if (err_ != DC1394_SUCCESS)
         LogMessage ("Failed to stop capture\n");
      err_ = dc1394_video_set_transmission(camera_, DC1394_OFF);
      if (err_ != DC1394_SUCCESS)
         LogMessage ("Failed to stop tranmission\n");
      dc1394_camera_free(camera_);

      LogMessage("Shutdown camera_\n");

      m_bInitialized = false;
   }
   return DEVICE_OK;
}

int Cdc1394::GetCamera()
{
   dc1394_t * d;
   dc1394camera_list_t * list;

   // Find and initialize the camera_
   d = dc1394_new();
   if (!d)
      return ERR_DC1394;

   err_ = dc1394_camera_enumerate(d, &list); 
   if (err_ != DC1394_SUCCESS)
      return ERR_CAMERA_NOT_FOUND; 
   if (list->num == 0) 
      return ERR_CAMERA_NOT_FOUND;
   
   // TODO: work with multiple camera_s (select by name??)
   // For now we'll take the first camera_ on the bus
   camera_ = dc1394_camera_new(d, list->ids[0].guid);
   if (!camera_)
      return ERR_INITIALIZATION_FAILED;

   dc1394_camera_free_list (list);
   /*
   // free the other ones:
   for (int i=1; i<numCameras; i++)                                                 
      dc1394_camera_free(camera_s[i]);                                          
   free(camera_s);                                                             
   */

   return DEVICE_OK;
}
 
void Cdc1394::mono8AddToMono16(uint16_t* dest, uint8_t* src, uint32_t  width, uint32_t height)
{
   for (uint64_t i=0; i < (width * height); i++) 
   {
      dest[i] += src[i];
   }
}

// Adds 8 bit rgb image to 16  bit grayscale
// It is the callers responsibility that both src and destination exist
void Cdc1394::rgb8AddToMono16(uint16_t* dest, uint8_t* src, uint32_t width, uint32_t height) 
{
   for (uint64_t i=0; i < (width * height); i++) 
   {
      dest[i] += (src[3*i] + src[3*i + 1] + src[3*i + 2]) / 3;
   }
}

// converts rgb image to 8 bit grayscale
// It is the callers responsibility that both src and destination exist
void Cdc1394::rgb8ToMono8(uint8_t* dest, uint8_t* src, uint32_t width, uint32_t height) 
{
   for (uint64_t i=0; i < (width * height); i++) 
   {
      dest[i] = (src[3*i] + src[3*i + 1] + src[3*i + 2]) / 3;
   }
}

// EF: converts rgb image to Micromanager BGRA
// It is the callers responsibility that both src and destination exist
void Cdc1394::rgb8ToBGRA8(uint8_t* dest, uint8_t* src, uint32_t width, uint32_t height) 
{
	for (register uint64_t i=0, j=0; i < (width * height * 3); i+=3, j+=4 ) 
	{
		dest[j] = src[i+2];
		dest[j+1] = src[i+1];
		dest[j+2] = src[i];
		dest[j+3] = 0;
	}
}



// GJ: Deinterlace fields from interlaced AVT Guppy camera_s
// See http://www.alliedvisiontec.com
// May have general utility
void Cdc1394::avtDeinterlaceMono8(uint8_t* dest, uint8_t* src, uint32_t outputWidth, uint32_t outputHeight) {
	uint32_t s1=0;
	uint32_t s2=outputWidth * (outputHeight/2) *1;
	uint32_t outCount = 0;
	for(uint32_t i = 0; i < outputHeight/2; i++) {
		for( uint32_t j=0; j<outputWidth; j++) {
			dest[outCount++]=src[s2++];
		}
		for( uint32_t j=0; j<outputWidth; j++) {
			dest[outCount++]=src[s1++];
		}
	}
}

void Cdc1394::avtDeinterlaceMono16(uint16_t* dest, uint16_t* src, uint32_t outputWidth, uint32_t outputHeight) {
	uint32_t s1=0;
	uint32_t s2=outputWidth * (outputHeight/2) *1;
	uint32_t outCount = 0;
	for(uint32_t i = 0; i < outputHeight/2; i++) {
		for( uint32_t j=0; j<outputWidth; j++) {
			dest[outCount++]=src[s2++];
		}
		for( uint32_t j=0; j<outputWidth; j++) {
			dest[outCount++]=src[s1++];
		}
	}
}

///////////////////////////////////////////////////////////////////////////////
// Function name   : Cdc1394::SnapImage()
// Description     : Acquires a single frame and stores it in the internal
//                   buffer. This command blocks the calling thread untile the
//                   image is fully captured.
// Return type     : bool 

int Cdc1394::SnapImage()
{
   // If GetImageBuffer was not called after a previous snap, we need to enqueue the current frame
   if (dequeued_)
      dc1394_capture_enqueue(camera_, frame_);

   // find the end of the capture buffer, so that we can get a fresh frame
   bool endFound = false;
   int nrFrames = 0;
   MM::MMTime startTime = GetCurrentMMTime();
   while (!endFound && !Timeout(startTime) ) {
      err_ = dc1394_capture_dequeue(camera_,DC1394_CAPTURE_POLICY_POLL, &frame_);
      if (frame_ && err_==DC1394_SUCCESS) {
         dc1394_capture_enqueue(camera_, frame_);
         nrFrames +=1;
      } else {
         endFound=true;
      }
   }
          
   // Exposure of the following image might have started before the SnapImage function was called, so discard it:
   bool frameFound = false;
   startTime = GetCurrentMMTime();
   while (!frameFound && !Timeout(startTime) ) {
      err_ = dc1394_capture_dequeue(camera_,DC1394_CAPTURE_POLICY_POLL, &frame_);
      if (frame_ && err_==DC1394_SUCCESS) {
        frameFound = true;
        dc1394_capture_enqueue(camera_, frame_);
      } else
         CDeviceUtils::SleepMs(10);
   }
   if (!frameFound || 0 == frame_)
      return  ERR_CAPTURE_TIMEOUT;


   // Now capture the frame that we are interested in
   frameFound = false;
   startTime = GetCurrentMMTime();
   while (!frameFound && !Timeout(startTime) ) {
      err_ = dc1394_capture_dequeue(camera_,DC1394_CAPTURE_POLICY_POLL, &frame_);
      if (frame_ && err_==DC1394_SUCCESS) {
         dequeued_ = true;
         frameFound = true;
      } else 
         CDeviceUtils::SleepMs(10);
   }
   if (!frameFound || 0 == frame_)
      return  ERR_CAPTURE_TIMEOUT;



   return DEVICE_OK;
}
   
/*
* Process the frame and return it as a usable image in the buffer 'deistination'
* It is the callers reponsibility to engueue the frame when done
* It  is also the callers responsibility to provide enough memory in destination
* EF: changed for camera returning color images if integrateFrameNumber_ == 1
*/
int Cdc1394::ProcessImage(dc1394video_frame_t *frame, const unsigned char* destination) 
{
   dc1394video_frame_t *internalFrame;
   int numPixels = width * height;
   if (colorCoding_==DC1394_COLOR_CODING_YUV411 || colorCoding_==DC1394_COLOR_CODING_YUV422 || colorCoding_==DC1394_COLOR_CODING_YUV444) 
   {
      uint8_t *rgb_image = (uint8_t *)malloc(3 * numPixels);
      dc1394_convert_to_RGB8((uint8_t *)frame->image,      
             rgb_image, width, height, DC1394_BYTE_ORDER_UYVY, colorCoding_, 16);
      // no integration, straight forward stuff
      if (integrateFrameNumber_ == 1)
      {
         uint8_t* pixBuffer = const_cast<unsigned uint8_t*> (destination);
         rgb8ToBGRA8(pixBuffer, rgb_image, width, height);
      }
      // when integrating we are dealing with 16 bit images
      if (integrateFrameNumber_ > 1)
      {
         void* pixBuffer = const_cast<unsigned char*> (destination);
         // funny, we need to clear the memory since we'll add to it, not overwrite it
         memset(pixBuffer, 0, GetImageBufferSize());
         rgb8AddToMono16((uint16_t*) pixBuffer, rgb_image, width, height);
         // now repeat for the other frames:
         for (int frameNr=1; frameNr<integrateFrameNumber_; frameNr++)
         {
            if (dc1394_capture_dequeue(camera_,DC1394_CAPTURE_POLICY_WAIT, &internalFrame)!=DC1394_SUCCESS)
                  return ERR_CAPTURE_FAILED;
            dc1394_convert_to_RGB8((uint8_t *)internalFrame->image,
                      rgb_image, width, height, DC1394_BYTE_ORDER_UYVY, colorCoding_, 16);
            dc1394_capture_enqueue(camera_, internalFrame);
            rgb8AddToMono16((uint16_t*) pixBuffer, rgb_image, width, height);
         }
      }
      free(rgb_image);
   }

   else if (colorCoding_==DC1394_COLOR_CODING_MONO8 || colorCoding_==DC1394_COLOR_CODING_MONO16) 
   {
      if (integrateFrameNumber_==1) 
      {
         void* src = (void *) frame->image;
         uint8_t* pixBuffer = const_cast<unsigned char*> (destination);
		 // GJ: Deinterlace image if required
         if (avtInterlaced_) 
            avtDeinterlaceMono8 (pixBuffer, (uint8_t*) src, width, height);
		   else 
            memcpy (pixBuffer, src, GetImageBufferSize());
      }
      else if (integrateFrameNumber_ > 1)
      {
         void* src = (void *) frame->image;
         uint8_t* pixBuffer = const_cast<unsigned char*> (destination);
         memset(pixBuffer, 0, GetImageBufferSize());
         mono8AddToMono16((uint16_t*) pixBuffer, (uint8_t*) src, width, height);
         // now repeat for the other frames:
         for (int frameNr=1; frameNr<integrateFrameNumber_; frameNr++)
         {
            if (dc1394_capture_dequeue(camera_,DC1394_CAPTURE_POLICY_WAIT, &internalFrame)!=DC1394_SUCCESS)
                  return ERR_CAPTURE_FAILED;
            mono8AddToMono16((uint16_t*) pixBuffer, (uint8_t*) frame->image, width, height);
            dc1394_capture_enqueue(camera_, internalFrame);
         }
		 // GJ:  Finished integrating, so we can deinterlace the 16 bit result
		 // That does mean doing a pixel buffer shuffle unfortunately
		 // but prob better than deinterlacing every 8 bit image
		 // perhaps better still would be a mono8DeinterlacedAddToMono16Function
		 if (avtInterlaced_) {
			 uint16_t *interlacedImaged = (uint16_t *) malloc(numPixels*sizeof(uint16_t));
			 memcpy(interlacedImaged, (uint16_t*) pixBuffer, GetImageBufferSize());
			 avtDeinterlaceMono16((uint16_t*) pixBuffer, interlacedImaged, width, height);
			 free(interlacedImaged);
		 }
	   }
   }
   else if (colorCoding_==DC1394_COLOR_CODING_RGB8) 
   {
      uint8_t* src;
      src = (uint8_t *) frame->image;
      uint8_t* pixBuffer = const_cast<unsigned uint8_t*> (destination);
      rgb8ToBGRA8 (pixBuffer, src, width, height);
   }
   
   return DEVICE_OK;
}


double Cdc1394::GetExposure() const
{
   if(absoluteShutterControl_)  {
      char Buf[MM::MaxStrLength];
      Buf[0] = '\0';
      GetProperty(MM::g_Keyword_Exposure, Buf);
      return atof(Buf);
   } else if (isSonyXCDX700_) {
      char Buf[MM::MaxStrLength];
      Buf[0] = '\0';
      GetProperty("Shutter", Buf);
      double exp = X700Shutter2Exposure(atoi(Buf));
      return exp;
   }
   return 0;
}

void Cdc1394::SetExposure(double dExp)
{
   if(absoluteShutterControl_) 
      SetProperty(MM::g_Keyword_Exposure, CDeviceUtils::ConvertToString(dExp));
   else if (isSonyXCDX700_) {
      int dn = X700Exposure2Shutter(dExp);
      SetProperty("Shutter", CDeviceUtils::ConvertToString(dn));
   }
}

///////////////////////////////////////////////////////////////////////////////
// Function name   : char* Cdc1394::GetImageBuffer
// Description     : Returns the raw image buffer
// Return type     : const unsigned 

const unsigned char* Cdc1394::GetImageBuffer()
{
   if (!dequeued_)
      return 0;
   
   dequeued_ = false;
   // Now process the frame according to the video mode used
   ProcessImage(frame_, img_.GetPixels());
   // we copied the mage, so release the frame:
   dc1394_capture_enqueue(camera_, frame_);
   // capture complete
   void* pixBuffer = const_cast<unsigned char*> (img_.GetPixels());
   return (unsigned char*)pixBuffer;
}

unsigned Cdc1394::GetBitDepth() const
{
   if (integrateFrameNumber_==1)
	  return (unsigned) depth_;
   else
   {
	  // calculated log2 of frameIntegrationNumber
	  unsigned r = 0; // r will be lg(v)
	  unsigned int v = integrateFrameNumber_;
	  while (v >>= 1) // unroll for more speed...
		   r++;
	  return (unsigned) depth_ + r;
   }
}

int Cdc1394::GetBinning () const 
{
   // Not supported yet
   return 1;
   /*
   char binMode[MM::MaxStrLength];
   GetProperty(MM::g_Keyword_Binning, binMode);
   return atoi(binMode);
   */
}

int Cdc1394::SetBinning (int binSize) 
{
   // Not supported yet
   return ERR_NOT_IMPLEMENTED;
   /*
   ostringstream os;
   os << binSize;
   return SetProperty(MM::g_Keyword_Binning, os.str().c_str());
   */
}

// GJ nb uX,uY = top left
int Cdc1394::SetROI(unsigned uX, unsigned uY, unsigned uXSize, unsigned uYSize)
{
   // GJ: Unsupported unless format 7
	if ( mode_ >= DC1394_VIDEO_MODE_FORMAT7_MIN && mode_ <= DC1394_VIDEO_MODE_FORMAT7_MAX ) {
		// First thing to do is stop transmission - otherwise we won't be
		// able to (re)set ROI.
		// TODO - can't figure out what we need to do to stop capture and
		// then restart appropriately
		// For example Shutdown() and Initialize() - which seems extreme -
		// leads to an assertion failure.
		err_ = dc1394_capture_stop(camera_);
		  if (err_ != DC1394_SUCCESS)
			 LogMessage ("SetROI: Failed to stop capture\n");
		 StopTransmission();
		
       logMsg_.str("");
		 logMsg_ << "SetROI: Aout to set to  " << uX << "," << uY << "," << uXSize << "," << uYSize ;
		LogMessage (logMsg_.str().c_str(), true);
		
		dc1394error_t err_val= dc1394_format7_set_roi(camera_, mode_, 
					(dc1394color_coding_t) DC1394_COLOR_CODING_MONO8 /*color_coding*/,
					(int) DC1394_USE_MAX_AVAIL /*bytes_per_packet*/, uX, uY, uXSize, uYSize);
		if (err_val!=DC1394_SUCCESS) {
         logMsg_.str("");
			logMsg_ << "SetROI: libdc1394 err_or message " <<  err_val;
			LogMessage (logMsg_.str().c_str(), true);
			return ERR_SET_F7_ROI_FAILED;
		}
		// This will restart capture	
		return ResizeImageBuffer();
	} else return ERR_ROI_NOT_SUPPORTED;
}

int Cdc1394::GetROI(unsigned& uX, unsigned& uY, unsigned& uXSize, unsigned& uYSize)
{
	// GJ TODO - what should happen if ROI is not supported?
	// May not be correct just to return OK
	if ( mode_ >= DC1394_VIDEO_MODE_FORMAT7_MIN && mode_ <= DC1394_VIDEO_MODE_FORMAT7_MAX) {
		uint32_t bytes_per_packet;
		dc1394color_coding_t color_coding;
		if(dc1394_format7_get_roi(camera_, mode_, &color_coding, &bytes_per_packet, &uX, &uY, &uXSize, &uYSize)!=DC1394_SUCCESS)
		return ERR_GET_F7_ROI_FAILED;
	}
	return DEVICE_OK;
}

int Cdc1394::ClearROI()
{
	if ( mode_ >=DC1394_VIDEO_MODE_FORMAT7_MIN && mode_ <= DC1394_VIDEO_MODE_FORMAT7_MAX) {
		// Get the recommended ROI - ie the max size of the image in
		// current format 7 mode
		uint32_t width, height;
		if(dc1394_format7_get_max_image_size(camera_, mode_, &width, &height)!=DC1394_SUCCESS)
		return ERR_GET_F7_MAX_IMAGE_SIZE_FAILED;
		// now set that ROI
		return SetROI(0, 0, width, height);
	}
	return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Utility methods
///////////////////////////////////////////////////////////////////////////////

int Cdc1394::ResizeImageBuffer()
{
   // there are two buffers, 1: DMA buffer that the camera_ streams into (frame)
   // 2: buffer for exchange with MicroManager (img_)

   // set up the camera_ (TODO: implement iso speed control):
   dc1394_video_set_iso_speed(camera_, DC1394_ISO_SPEED_400);
   err_ = dc1394_video_set_mode(camera_,mode_);
   if (err_ != DC1394_SUCCESS)
      return ERR_SET_MODE_FAILED;
   logMsg_.str("");
   logMsg_ << "Mode set to " <<  mode_;
   LogMessage (logMsg_.str().c_str(), true);

   logMsg_.str("");
   // GJ: check whether the chosen mode is a format 7 mode
   if ( mode_ >=DC1394_VIDEO_MODE_FORMAT7_MIN && mode_ <= DC1394_VIDEO_MODE_FORMAT7_MAX) {
	   logMsg_ << "Format_7: Skipping setting of framerate";
   } else {
	   // GJ bracket this off - doesn't make sense for format7
	   err_ = dc1394_video_set_framerate(camera_,framerate_);
	   if (err_ != DC1394_SUCCESS)
		  return ERR_SET_FRAMERATE_FAILED;
		  logMsg_ <<  "Framerate set to " << framerate_;
   }
   LogMessage (logMsg_.str().c_str(), true);
   
   // Start the image capture
   if (dc1394_capture_setup(camera_,dmaBufferSize_,DC1394_CAPTURE_FLAGS_DEFAULT) != DC1394_SUCCESS) 
      return ERR_SET_CAPTURE_FAILED;

   // Set camera_ trigger mode
   // if( dc1394_external_trigger_set_mode(camera_, DC1394_TRIGGER_MODE_0) != DC1394_SUCCESS)
    //  return ERR_SET_TRIGGER_MODE_FAILED;

   // Have the camera_ start sending data
   if (dc1394_video_set_transmission(camera_, DC1394_ON) !=DC1394_SUCCESS) 
      return ERR_SET_TRANSMISSION_FAILED;

   // Sleep until the camera_ sent data
   dc1394switch_t status = DC1394_OFF;
   int i = 0;
   while( status == DC1394_OFF && i < 10 ) 
   {
     usleep(50000);
     if (dc1394_video_get_transmission(camera_, &status)!=DC1394_SUCCESS)
        return ERR_GET_TRANSMISSION_FAILED;
     i++;
   }
   if (i == 10)
      return ERR_CAMERA_DOES_NOT_SEND_DATA;

   /*
   // Make sure we can get images
   bool captureSuccess = false;
   i = 0;
   while ( captureSuccess == false && i < 10)
   {
      usleep(250000);
      err_ = dc1394_capture_dequeue(camera_, DC1394_CAPTURE_POLICY_POLL, &frame_);
      if (frame_ && err_==DC1394_SUCCESS)
      {
         captureSuccess = true;
         dc1394_capture_enqueue(camera_, frame_);
      }
      i++;
   }
   logMsg_.str("");
   logMsg_ << "tried " << i << " times";
   LogMessage (logMsg_.str().c_str(), true);
   if (i==10) {
      logMsg_.str("");
      logMsg_ << "PROBLEM!!!:: Failed to capture";
      LogMessage (logMsg_.str().c_str(), false);
      // Camera is in a bad state.  Try to rescue what we can:
      dc1394_camera_free(camera_);
      m_bInitialized = false;
      // If/when we get here, try starting the camera_ again, probably hopeless
      int nRet = GetCamera();
      logMsg_.str("");
      logMsg_ <<"Tried restarting the camera_, Result: " <<  nRet;
      LogMessage (logMsg_.str().c_str(), false);
      if (nRet == DEVICE_OK && triedCaptureCount_ <= 5)
      {
         triedCaptureCount_++;
         return ResizeImageBuffer();
      } else
         return ERR_CAPTURE_FAILED;
   }
   */

   // Get the image size from the camera_:
   // GJ: nb this is clever and returns current ROI size for format 7 modes
   err_ = dc1394_get_image_size_from_video_mode(camera_, mode_, &width, &height);
   if (err_ != DC1394_SUCCESS)
      return ERR_GET_IMAGE_SIZE_FAILED;
   GetBitDepth();
   GetBytesPerPixel();
   // Set the internal buffer (for now 8 bit gray scale only)
   // TODO: implement other pixel sizes
   img_.Resize(width, height, bytesPerPixel_);


   logMsg_.str("");
   logMsg_ << "Everything OK in ResizeImageBuffer";
   LogMessage (logMsg_.str().c_str(), true);
   
   return DEVICE_OK;
}

/*
 * Set this feature in Manual mode and switch it on
 * TODO: Error checking
 * TODO: implement this correctly!
 */
int Cdc1394::SetManual(dc1394feature_t feature)
{
    dc1394bool_t isSwitchable = DC1394_FALSE;
    err_ = dc1394_feature_is_switchable(camera_, feature, &isSwitchable);
    if (isSwitchable)
       dc1394_feature_set_power(camera_, feature, DC1394_ON);
    // dc1394bool_t hasManualMode = DC1394_FALSE;
    //hasManualMode = feature->
    //err_ = dc1394_feature_has_manual_mode(camera_, feature, &hasManualMode);
    //if (hasManualMode)
    dc1394_feature_set_mode(camera_, feature, DC1394_FEATURE_MODE_MANUAL);
    return DEVICE_OK;
}


int Cdc1394::ShutdownImageBuffer()
{
   return DEVICE_OK;
}

bool Cdc1394::IsFeatureSupported(int featureId)
{
   return DEVICE_OK;
}


// EF: determine if camera currently returns color
bool Cdc1394::IsColor() const
{
	switch (colorCoding_) {
		case DC1394_COLOR_CODING_YUV411:
		case DC1394_COLOR_CODING_YUV422:
		case DC1394_COLOR_CODING_YUV444:
		case DC1394_COLOR_CODING_RGB8:
			return true;
			break;
		default:
			return false;
			break;
	}
}	


// EF: number of components
unsigned int Cdc1394::GetNumberOfComponents() const
{
	return IsColor() == true && integrateFrameNumber_ == 1 ? 4 : 1;
}


// EF: pretty much identical to SpotCamera.cpp
int Cdc1394::GetComponentName(unsigned channel, char* name)
{
	bool bColor = IsColor();
	if (!bColor && (channel > 0))  return DEVICE_NONEXISTENT_CHANNEL;      
	
	switch (channel)
	{
		case 0:      
			if (!bColor) 
				CDeviceUtils::CopyLimitedString(name, "Grayscale");
			else 
				CDeviceUtils::CopyLimitedString(name, "B");
			break;
			
		case 1:
			CDeviceUtils::CopyLimitedString(name, "G");
			break;
			
		case 2:
			CDeviceUtils::CopyLimitedString(name, "R");
			break;
			
		default:
			return DEVICE_NONEXISTENT_CHANNEL;
			break;
	}
	return DEVICE_OK;
}


int Cdc1394::StopTransmission()
{
	// GJ: first check if off
   dc1394switch_t status;
	if (dc1394_video_get_transmission(camera_, &status)!=DC1394_SUCCESS) {
		LogMessage("Could not get ISO status");
	}
	// Camera already off
	if(status==DC1394_OFF) return DEVICE_OK;
	
	LogMessage("About to stop transmission");
	dc1394_video_set_transmission(camera_, DC1394_OFF);
   // wait untill the camera_ stopped transmitting:
   int i = 0;
   while( status == DC1394_ON && i < 50 )
   {       
      usleep(50000);
      if (dc1394_video_get_transmission(camera_, &status)!=DC1394_SUCCESS)
         return ERR_GET_TRANSMISSION_FAILED;
      i++;
   }
   logMsg_.str("");
   logMsg_ <<  "Waited for " << i << "cycles for transmission to stop"; 
   LogMessage (logMsg_.str().c_str(), true);
   if (i == 50)                                                
      return ERR_CAMERA_DOES_NOT_SEND_DATA;
   return DEVICE_OK;
}

void Cdc1394::GetBytesPerPixel()
{
   if (depth_==8 && integrateFrameNumber_==1)
	  if (IsColor() == true)
	  {
		 bytesPerPixel_ = 4;
	  }
	  else
	  {
         bytesPerPixel_ = 1;
	  }
   else if (depth_==8 && integrateFrameNumber_>1)
      bytesPerPixel_ = 2;
   else if (depth_ > 8 && depth_ <= 16)
      bytesPerPixel_ = 2;
   else {
      std::ostringstream os;
      os << "Can not figure out Bytes per Pixel.  bitdepth=" << depth_;
      LogMessage(os.str().c_str());
   }
}


bool Cdc1394::InArray(dc1394framerate_t *array, int size, uint32_t num)
{
   for(int j = 0; j < size; j++)
      if((uint32_t)array[j] == num)
         return true;
   return false;
}

/*
 * Since the possible framerates are videomode dependend, it needs to be figure out 
 * every time that a videomode is changed
 */
int Cdc1394::SetUpFrameRates() 
{
	// dc1394err_or_t err_val;
	if ( mode_ >=DC1394_VIDEO_MODE_FORMAT7_MIN && mode_ <= DC1394_VIDEO_MODE_FORMAT7_MAX) {
		// GJ Format_7: no specific framerate so just bail out early
		// framerate = 0;
		// TODO - delete list of frame rates?
      logMsg_.str("");
		logMsg_ << "SetUpFrameRates: Returning early because mode is format 7 (no defined framerate)";
		LogMessage (logMsg_.str().c_str(), true);
		// What should I return now?
		return DEVICE_OK;
	} else if (dc1394_video_get_supported_framerates(camera_,mode_,&framerates_)!=DC1394_SUCCESS) {
		return ERR_GET_FRAMERATES_FAILED;
	}
		
	// Default to the first framerate belonging to this mode
	if (!InArray(framerates_.framerates, framerates_.num, framerate_)) 
	{
	   framerate_ = framerates_.framerates[0];
	}
   // Create the MicroManager Property FrameRates, only when it was not yet defined
   if (!frameRatePropDefined_)
   {
      CPropertyAction *pAct = new CPropertyAction (this, &Cdc1394::OnFrameRate);
      int nRet = CreateProperty(g_Keyword_FrameRates, frameRateMap_[framerates_.framerates[0]].c_str(), MM::String, false, pAct);
      assert(nRet == DEVICE_OK);
      frameRatePropDefined_ = true;
   }
   
   // set allowed values, this will delete a pre-existing list of allowed values
   vector<string> rateValues;
   for (unsigned int i=0; i<framerates_.num; i++) {
      string tmp = frameRateMap_[ framerates_.framerates[i] ];
      if (tmp != "")
         rateValues.push_back(tmp);
   }   
   int nRet = SetAllowedValues(g_Keyword_FrameRates, rateValues);
   logMsg_.str("");
   logMsg_ << "FrameRate: Changed list of allowed values";
   LogMessage (logMsg_.str().c_str(), true);
   return nRet;
}


/*
 * Utility function, create property for dc1394 features 
 */


/*
int Cdc1394::AddFeature(dc1394feature_t feature, const char* label, int(Cdc1394::*fpt)(PropertyBase* pProp, ActionType eAct) , uint32_t  &value, uint32_t &valueMin, uint32_t &valueMax)
{
    // TODO: offer option to switch between auto, manual and one-push modes
    err_ = dc1394_feature_get_value(camera_, feature, &value);
    printf("%s: %d\n", label, shutter);
    err_ = dc1394_feature_get_boundaries(camera_, feature, &valueMin, &valueMax);
    printf("%s Min: %d, Max: %d\n", label, valueMin, valueMax);
    char* tmp;
    CPropertyAction *pAct = new CPropertyAction (this, fpt);
    sprintf(tmp,"%d",value);
    int nRet = CreateProperty(label, tmp, MM::Integer, false, pAct);
    assert(nRet == DEVICE_OK);
}
*/



void Cdc1394::SetVideoModeMap()
{
   videoModeMap_.insert(videoModeMapType (DC1394_VIDEO_MODE_160x120_YUV444,"160x120_YUV444"));
   videoModeMap_.insert(videoModeMapType (DC1394_VIDEO_MODE_320x240_YUV422,"320x240_YUV422"));
   videoModeMap_.insert(videoModeMapType (DC1394_VIDEO_MODE_640x480_YUV411,"640x480_YUV411"));
   videoModeMap_.insert(videoModeMapType (DC1394_VIDEO_MODE_640x480_YUV422,"640x480_YUV422"));
   videoModeMap_.insert(videoModeMapType (DC1394_VIDEO_MODE_640x480_RGB8,"640x480_RGB8"));
   videoModeMap_.insert(videoModeMapType (DC1394_VIDEO_MODE_640x480_MONO8,"640x480_MONO8"));
   videoModeMap_.insert(videoModeMapType (DC1394_VIDEO_MODE_640x480_MONO16,"640x480_MONO16"));
   videoModeMap_.insert(videoModeMapType (DC1394_VIDEO_MODE_800x600_YUV422,"800x600_YUV422"));
   videoModeMap_.insert(videoModeMapType (DC1394_VIDEO_MODE_800x600_RGB8,"800x600_RGB8"));
   videoModeMap_.insert(videoModeMapType (DC1394_VIDEO_MODE_800x600_MONO8,"800x600_MONO8"));
   videoModeMap_.insert(videoModeMapType (DC1394_VIDEO_MODE_800x600_MONO16,"800x600_MONO16"));
   videoModeMap_.insert(videoModeMapType (DC1394_VIDEO_MODE_1024x768_YUV422,"1024x768_YUV422"));
   videoModeMap_.insert(videoModeMapType (DC1394_VIDEO_MODE_1024x768_RGB8,"1024x768_RGB8"));
   videoModeMap_.insert(videoModeMapType (DC1394_VIDEO_MODE_1024x768_MONO8,"1024x768_MONO8"));
   videoModeMap_.insert(videoModeMapType (DC1394_VIDEO_MODE_1024x768_MONO16,"1024x768_MONO16"));
   videoModeMap_.insert(videoModeMapType (DC1394_VIDEO_MODE_1280x960_YUV422,"1280x960_YUV422"));
   videoModeMap_.insert(videoModeMapType (DC1394_VIDEO_MODE_1280x960_RGB8,"1280x960_RGB8"));
   videoModeMap_.insert(videoModeMapType (DC1394_VIDEO_MODE_1280x960_MONO8,"1280x960_MONO8"));
   videoModeMap_.insert(videoModeMapType (DC1394_VIDEO_MODE_1280x960_MONO16,"1280x960_MONO16"));
   videoModeMap_.insert(videoModeMapType (DC1394_VIDEO_MODE_1600x1200_YUV422,"1600x1200_YUV422"));
   videoModeMap_.insert(videoModeMapType (DC1394_VIDEO_MODE_1600x1200_RGB8,"1600x1200_RGB8"));
   videoModeMap_.insert(videoModeMapType (DC1394_VIDEO_MODE_1600x1200_MONO8,"1600x1200_MONO8"));
   videoModeMap_.insert(videoModeMapType (DC1394_VIDEO_MODE_1600x1200_MONO16,"1600x1200_MONO16"));
   // GJ Add format 7 modes
   videoModeMap_.insert(videoModeMapType (DC1394_VIDEO_MODE_FORMAT7_0,"Format_7_0"));
   videoModeMap_.insert(videoModeMapType (DC1394_VIDEO_MODE_FORMAT7_1,"Format_7_1"));
   videoModeMap_.insert(videoModeMapType (DC1394_VIDEO_MODE_FORMAT7_2,"Format_7_2"));
   videoModeMap_.insert(videoModeMapType (DC1394_VIDEO_MODE_FORMAT7_3,"Format_7_3"));
   videoModeMap_.insert(videoModeMapType (DC1394_VIDEO_MODE_FORMAT7_4,"Format_7_4"));
   videoModeMap_.insert(videoModeMapType (DC1394_VIDEO_MODE_FORMAT7_5,"Format_7_5"));
   videoModeMap_.insert(videoModeMapType (DC1394_VIDEO_MODE_FORMAT7_6,"Format_7_6"));
   videoModeMap_.insert(videoModeMapType (DC1394_VIDEO_MODE_FORMAT7_7,"Format_7_7"));
}


void Cdc1394::SetFrameRateMap()
{
   frameRateMap_.insert (frameRateMapType(DC1394_FRAMERATE_1_875, "  1.88 fps"));
   frameRateMap_.insert (frameRateMapType(DC1394_FRAMERATE_3_75, "  3.75 fps"));
   frameRateMap_.insert (frameRateMapType(DC1394_FRAMERATE_7_5, "  7.5 fps"));
   frameRateMap_.insert (frameRateMapType(DC1394_FRAMERATE_15, " 15 fps"));
   frameRateMap_.insert (frameRateMapType(DC1394_FRAMERATE_30, " 30 fps"));
   frameRateMap_.insert (frameRateMapType(DC1394_FRAMERATE_60, " 60 fps"));
   frameRateMap_.insert (frameRateMapType(DC1394_FRAMERATE_120, "120 fps"));
   frameRateMap_.insert (frameRateMapType(DC1394_FRAMERATE_240, "240 fps"));
}

bool Cdc1394::Timeout(MM::MMTime startTime)
{
   if (GetCurrentMMTime() - startTime > longestWait_)
      return true;
   return false;
}
   
double Cdc1394::X700Shutter2Exposure(int shutter) const
{
   if (shutter >= 0xB22)
      return 0.01;
   if (shutter == 0xB21)
      return 0.02;
   if (shutter == 0xB20)
      return 0.05;
   if (shutter > 0x800)
      return (489 + 1197 * (2848 - shutter))/14318.182;
   if (shutter > 1807)
      return (2048 - shutter)/0.0149520;

   return (2048 - 1808)/0.0149520;
}

int Cdc1394::X700Exposure2Shutter(double exposure)
{
   if (exposure <= 0.01)
      return 0xB22;
   if (exposure <= 0.02)
      return 0xB21;
   if (exposure <= 0.05)
      return 0xB20;
   if (exposure < 66.914)
      return (int) (2848 - (14318.182 * exposure - 489)/1197);
   if (exposure < 16051.36)
      return (int) (2048 -(exposure * 0.0149520));
   return 1808;

}

/**
 * Starts continuous acquisition.
 */
int Cdc1394::StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow)
{
   if (acquiring_)
      return ERR_BUSY_ACQUIRING;

   if (dequeued_)
      dc1394_capture_enqueue(camera_, frame_);

   logMsg_.str("");
   logMsg_ << "Started sequence acquisition: " << numImages << " at " << interval_ms << " ms" << endl;
   LogMessage(logMsg_.str().c_str());

   imageCounter_ = 0;
   sequenceLength_ = numImages;
   stopOnOverflow_ = stopOnOverflow;

   double actualIntervalMs = max(GetExposure(), interval_ms);
   acqThread_->SetInterval(actualIntervalMs);
   SetProperty(MM::g_Keyword_ActualInterval_ms, CDeviceUtils::ConvertToString(actualIntervalMs));

   err_ = dc1394_video_set_multi_shot(camera_, numImages, DC1394_ON);
   if (err_ != DC1394_SUCCESS) {
      logMsg_.str("");
      logMsg_ << "Unable to start multi-shot" << endl;
      LogMessage(logMsg_.str().c_str());
   }
   multi_shot_ = true;

   ResizeImageBuffer();

   int ret = GetCoreCallback()->PrepareForAcq(this);
   if (ret != DEVICE_OK)
      return ret;

   GetBytesPerPixel();

   acqThread_->SetLength(numImages);

   // TODO: check trigger mode, etc..

   // emtpy the buffer to ensure we'll get fresh images
   bool endFound = false;
   int nrFrames = 0;
   MM::MMTime startTime = GetCurrentMMTime();
   while (!endFound && !Timeout(startTime) ) {
      err_ = dc1394_capture_dequeue(camera_,DC1394_CAPTURE_POLICY_POLL, &frame_);
      if (frame_ && err_==DC1394_SUCCESS) {
         dc1394_capture_enqueue(camera_, frame_);
         nrFrames +=1;
      } else {
         endFound=true;
      }
   }
   if (!endFound)
      LogMessage("Timeout while emptying dma buffer before starting acquisition thread");


   acquiring_ = true;
   acqThread_->Start();


   LogMessage("Acquisition thread started");

   return DEVICE_OK;
}

int Cdc1394::StartSequenceAcquisition(double interval_ms)
{
   if (acquiring_)
      return ERR_BUSY_ACQUIRING;

   if (dequeued_)
      dc1394_capture_enqueue(camera_, frame_);

   logMsg_.str("");
   logMsg_ << "Starting continuous sequence acquisition: at " << interval_ms << " ms" << endl;
   LogMessage(logMsg_.str().c_str());

   imageCounter_ = 0;
   stopOnOverflow_ = false;
   sequenceLength_ = LONG_MAX;

   double actualIntervalMs = max(GetExposure(), interval_ms);
   acqThread_->SetInterval(actualIntervalMs);
   SetProperty(MM::g_Keyword_ActualInterval_ms, CDeviceUtils::ConvertToString(actualIntervalMs));

   ResizeImageBuffer();

   int ret = GetCoreCallback()->PrepareForAcq(this);
   if (ret != DEVICE_OK)
      return ret;

   GetBytesPerPixel();

   acqThread_->SetLength(sequenceLength_);

   // TODO: check trigger mode, etc..

   // emtpy the buffer to ensure we'll get fresh images
   bool endFound = false;
   int nrFrames = 0;
   MM::MMTime startTime = GetCurrentMMTime();
   while (!endFound && !Timeout(startTime) ) {
      err_ = dc1394_capture_dequeue(camera_,DC1394_CAPTURE_POLICY_POLL, &frame_);
      if (frame_ && err_==DC1394_SUCCESS) {
         dc1394_capture_enqueue(camera_, frame_);
         nrFrames +=1;
      } else {
         endFound=true;
      }
   }
   if (!endFound)
      LogMessage("Timeout while emptying dma buffer before starting acquisition thread");

   acquiring_ = true;
   acqThread_->Start();


   LogMessage("Acquisition thread started");

   return DEVICE_OK;
}

/**
 * Stops acquisition
 */
int Cdc1394::StopSequenceAcquisition()
{   
   printf("Stopped camera_ streaming.\n");
   acqThread_->Stop();
   acquiring_ = false;

   if (multi_shot_) {
      err_ = dc1394_video_set_multi_shot(camera_, 0, DC1394_OFF);
      if (err_ != DC1394_SUCCESS) {
         logMsg_.str("");
         logMsg_ << "Unable to stop multi-shot" << endl;
         LogMessage(logMsg_.str().c_str());
      }
      multi_shot_ = false;
   }

   // TODO: the correct termination code needs to be passed here instead of "0"
   MM::Core* cb = GetCoreCallback();
   if (cb)
      cb->AcqFinished(this, 0);
   LogMessage("Stopped streaming");
   return DEVICE_OK;
}

int Cdc1394::PushImage(dc1394video_frame_t *myframe)
{
   std::ostringstream logMsg;
   logMsg << "Pushing image " <<imageCounter_<< endl;
   LogMessage(logMsg.str().c_str());

   imageCounter_++;
   // TODO: call core to finish image snap

   // Fetch current frame
   // TODO: write a deinterlace in place routine
   // to avoid unnecessary copying
   //    uint8_t* pixBuffer = const_cast<unsigned char*> (img_.GetPixels());
   //  // GJ: Deinterlace image if required
   //    if (avtInterlaced) avtDeinterlaceMono8 (pixBuffer, (uint8_t*) src, width, height);         
   // else memcpy (pixBuffer, src, GetImageBufferSize());

   // Copy to img_ ?
   
   // process image

   int numChars = GetImageWidth() * GetImageHeight() * GetImageBytesPerPixel() ;
   unsigned char* buf = (unsigned char *)malloc(numChars);
   ProcessImage(myframe, buf);

   /*
   MM::ImageProcessor* ip = GetCoreCallback()->GetImageProcessor(this);
   if (ip)
   {
      int ret = ip->Process(img_.GetPixels(), width, height,bytesPerPixel);
      if (ret != DEVICE_OK) return ret;
   }
   */

   // insert image into the circular MMCore buffer
   int ret =  GetCoreCallback()->InsertImage(this, buf, width, height, bytesPerPixel_);

   if (!stopOnOverflow_ && ret == DEVICE_BUFFER_OVERFLOW) {
      // do not stop on overflow - just reset the buffer                     
      GetCoreCallback()->ClearImageBuffer(this);                             
      ret =  GetCoreCallback()->InsertImage(this, buf, width, height, bytesPerPixel_);
   }

   std::ostringstream os;
   os << "Inserted Image in circular buffer, with result: " << ret;
   LogMessage (os.str().c_str(), true);

   free(buf);
   return ret;
}

int AcqSequenceThread::svc(void)
{
   long imageCounter(0);
   dc1394error_t err;                                                         
   dc1394video_frame_t *myframe;  
   std::ostringstream logMsg_;
   
   do
   {
       // wait until the frame becomes available
      err=dc1394_capture_dequeue(camera_->camera_, DC1394_CAPTURE_POLICY_WAIT, &myframe);/* Capture */
      if(err!=DC1394_SUCCESS)
      {
         std::ostringstream logMsg;
         logMsg << "Dequeue failed with code: " << err;
         camera_->LogMessage(logMsg_.str().c_str());
         camera_->StopSequenceAcquisition();
         return err; 
      } else {
         std::ostringstream logMsg;
         logMsg << "Dequeued image: " << imageCounter <<
         " with timestamp: " <<myframe->timestamp << 
         " ring buffer pos: "<<myframe->id <<
         " frames_behind: "<<myframe->frames_behind<<endl ;
         camera_->LogMessage(logMsg_.str().c_str());
      }
      int ret = camera_->PushImage(myframe);
      if (ret != DEVICE_OK)
      {
         std::ostringstream logMsg;
         logMsg << "PushImage() failed with errorcode: " << ret;
         camera_->LogMessage(logMsg.str().c_str());
         camera_->StopSequenceAcquisition();
         return 2;
      }
      err=dc1394_capture_enqueue(camera_->camera_, myframe);/* Capture */
      if(err!=DC1394_SUCCESS)
      {
         std::ostringstream logMsg;
         logMsg.str("");
         logMsg<< "Failed to enqueue image" <<imageCounter<< endl;
         camera_->LogMessage(logMsg.str().c_str());

         camera_->StopSequenceAcquisition();
         return err; 
      } 
      imageCounter++;
      printf("Acquired frame %ld.\n", imageCounter);                           
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

void AcqSequenceThread::Start() {
   stop_ = false;
   activate(); 
} 
