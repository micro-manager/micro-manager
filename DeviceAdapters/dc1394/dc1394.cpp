///////////////////////////////////////////////////////////////////////////////
// FILE:       dc1394.cpp
// PROJECT:    MicroManage
// SUBSYSTEM:  DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:
// dc1394 camera module based on libdc1394 API
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

#include "../../MMDevice/ModuleInterface.h"
#include "../../MMDevice/DeviceUtils.h"
#include "dc1394.h"
#include <dc1394/control.h>
#include <dc1394/utils.h>
#include <dc1394/conversions.h>
#include <dc1394/vendor/avt.h>
#include <boost/lexical_cast.hpp>
#include <string>
#include <set>
#include <cstring> // memset(), memcpy()
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


// Macros for error chacking on dc1394 calls.
//
// All calls to dc1394 library functions that return dc1394error_t should use
// one of these.

#ifdef WIN32
#pragma warning(disable: 4127) // Conditional expression is constant
#endif

// Helper; do not use directly
// TODO Translate dc1394 codes to readable messages
#define _LOG_DC1394_ERROR(_func, _err, _msg) do { \
   ostringstream __err_oss; __err_oss << (_err); \
   ostringstream __line_oss; __line_oss << __LINE__; \
   _func("dc1394 error " + __err_oss.str() + " at " + __FILE__ + ":" + __line_oss.str() + "; " + (_msg)); \
} while (0)

// Check if _err (of type dc1394error_t) is an error. If it is, log it,
// together with _msg (const std::string or const char*), and return _mm_err
// (an int representing a MM error code) from the current function (whose
// return type must be int).
#define CHECK_DC1394_ERROR(_err, _mm_err, _msg) do { \
   dc1394error_t __err = (_err); \
   int __mm_err = (_mm_err); \
   const std::string& __msg = (_msg); \
   if (__err != DC1394_SUCCESS) { \
      _LOG_DC1394_ERROR(LogMessage, __err, __msg); \
      if (__mm_err != DEVICE_OK) { \
         ostringstream mm_err_oss; mm_err_oss << __mm_err; \
         LogMessage("Returning MM error code " + mm_err_oss.str()); \
         return __mm_err; \
      } \
   } \
} while (0)

// Check if _err is an error. If it is, log it using _func. For use outside of
// class Cdc1394.
#define LOG_DC1394_ERROR_EX(_func, _err, _msg) do { \
   dc1394error_t __err = (_err); \
   const std::string& __msg = (_msg); \
   if (__err != DC1394_SUCCESS) { \
      _LOG_DC1394_ERROR(_func, __err, __msg); \
   } \
} while (0)

// Check if _err is an error. If it is, log it. For use inside class Cdc1394.
#define LOG_DC1394_ERROR(_err, _msg) LOG_DC1394_ERROR_EX(LogMessage, (_err), (_msg))


// Macros for internal error checking in this device adapter
#define CHECK_MM_ERROR(_err) do { \
   int __err = (_err); \
   if (__err != DEVICE_OK) { \
      LogMessage("Error " + boost::lexical_cast<string>(__err) + " at " + __FILE__ + ":" + \
            boost::lexical_cast<string>(__LINE__)); \
      return __err; \
   } \
} while (0)

#define CHECK_MM_ERROR_MSG(_err, _msg) do { \
   int __err = (_err); \
   const std::string& __msg = (_msg); \
   if (__err != DEVICE_OK) { \
      LogMessage("Error " + boost::lexical_cast<string>(__err) + " at " + __FILE__ + ":" + \
            boost::lexical_cast<string>(__LINE__) + ": " + __msg); \
      return __err; \
   } \
} while (0)


///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_DeviceName, MM::CameraDevice, "FireWire camera (IIDC/DCAM compatible)");
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
      return new Cdc1394();
   
   return 0;
}


boost::weak_ptr<DC1394Context::Singleton> DC1394Context::s_singleton_instance;

#ifdef WIN32
boost::shared_ptr<DC1394Context::Singleton> DC1394Context::s_retained_singleton;
#endif


///////////////////////////////////////////////////////////////////////////////
// Cdc1394 constructor/destructor

Cdc1394::Cdc1394() :
   camera_(0),
   frame_(0),

   avtInterlaced_(false),
   isSonyXCDX700_(false),

   absoluteShutterControl_(false),

   frameRatePropDefined_(false),
   integrateFrameNumber_(1),
   longestWait_(1,0),
   dequeued_(false),
   stopOnOverflow_(false),
   multi_shot_(false),
   acquiring_(false)
{
   SetErrorText(ERR_DC1394, "Could not initialize libdc1394.  Someting in your system is broken");
   SetErrorText(ERR_CAMERA_NOT_FOUND, "Did not find a IIDC firewire camera");
   SetErrorText(ERR_CAPTURE_SETUP_FAILED, "Failed to set up capture");
   SetErrorText(ERR_TRANSMISSION_FAILED, "Problem starting transmission");
   SetErrorText(ERR_MODE_LIST_NOT_FOUND, "Did not find camera mode list");
   SetErrorText(ERR_ROI_NOT_SUPPORTED, "ROIs not supported with this camera");
   SetErrorText(ERR_INITIALIZATION_FAILED, "Error Initializing the camera.  Unplug and replug the camera and restart this application");
   SetErrorText(ERR_CAPTURE_TIMEOUT, "Timeout during image capture.  Increase the camera timeout, increase shutter speed, or decrease exposure time");

   InitializeDefaultErrorMessages();

   // Name
   int nRet = CreateProperty(MM::g_Keyword_Name, g_DeviceName, MM::String, true);
   assert(nRet == DEVICE_OK);

   // Description
   nRet = CreateProperty(MM::g_Keyword_Description, "FireWire camera (IIDC/DCAM) via libdc1394", MM::String, true);
   assert(nRet == DEVICE_OK);
   
   // Create a thread for burst mode
   acqThread_ = new AcqSequenceThread(this); 
}

Cdc1394::~Cdc1394()
{
   Shutdown();
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
      dc1394video_mode_t videoMode = VideoModeForString(modeString);
      if (videoMode < DC1394_VIDEO_MODE_MIN || videoMode > DC1394_VIDEO_MODE_MAX) {
         return DEVICE_INVALID_PROPERTY_VALUE;
      }

      if (videoMode == mode_) {
         return DEVICE_OK;
      }

      CHECK_MM_ERROR(StopCapture());
      CHECK_MM_ERROR(SetVideoMode(videoMode));
      CHECK_MM_ERROR(StartCapture());
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set(StringForVideoMode(mode_).c_str());
   }

   return DEVICE_OK;
}


int Cdc1394::OnFrameRate(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      string framerateString;
      pProp->Get(framerateString);
      dc1394framerate_t framerate = FramerateForString(framerateString);
      if (framerate < DC1394_FRAMERATE_MIN || framerate > DC1394_FRAMERATE_MAX) {
         return DEVICE_INVALID_PROPERTY_VALUE;
      }

      framerate_ = framerate;
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set(StringForFramerate(framerate_).c_str());
   }
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
      // Send the new exposure setting to the camera if we have absolute control
      if(camera_->vendor_id==AVT_VENDOR_ID){
         // AVT has vendor specific code for an extended shutter
         // this accepts shutter times in µs which makes it easier to work with
         // OK, set using extended register, MM exposure is in ms so *1000 -> us
         exposure_us = (uint32_t) 1000.0 * (uint32_t) exposure_ms;
         CHECK_DC1394_ERROR(dc1394_avt_set_extented_shutter(camera_, exposure_us), DEVICE_ERR,
               "Failed to set AVT extended shutter value");
      } else {
         // set using ordinary absolute shutter dc1394 function
         // this expects a float in seconds
         float minAbsShutter, maxAbsShutter;
         CHECK_DC1394_ERROR(dc1394_feature_get_absolute_boundaries(camera_, DC1394_FEATURE_SHUTTER,
                  &minAbsShutter, &maxAbsShutter),
               DEVICE_ERR, "Failed to get allowed range for exposure (SHUTTER)");

         float exposure_s=0.001f*(float)exposure_ms;
         if(minAbsShutter>exposure_s || exposure_s>maxAbsShutter) return DEVICE_ERR;
         
         CHECK_DC1394_ERROR(dc1394_feature_set_absolute_control(camera_, DC1394_FEATURE_SHUTTER, DC1394_ON),
               DEVICE_ERR, "Failed to set SHUTTER to ON");

         CHECK_DC1394_ERROR(dc1394_feature_set_absolute_value(camera_, DC1394_FEATURE_SHUTTER, exposure_s),
               DEVICE_ERR, "Failed to set exposure (SHUTTER) value");
      }
   }
   else if (eAct == MM::BeforeGet)
   {  
      if(camera_->vendor_id==AVT_VENDOR_ID){
         // AVT has vendor specific code for an extended shutter
         // this accepts shutter times in µs which makes it easier to work with
         CHECK_DC1394_ERROR(dc1394_avt_get_extented_shutter(camera_, &exposure_us), DEVICE_ERR,
               "Failed to get AVT extended shutter value");
         // convert it to milliseconds
         exposure_ms = 0.001 * (double) exposure_us;         
      } else {
          // set using ordinary absolute shutter dc1394 function
          // this expects a float in seconds
         float exposure_s;
         CHECK_DC1394_ERROR(dc1394_feature_get_absolute_value(camera_, DC1394_FEATURE_SHUTTER, &exposure_s),
               DEVICE_ERR, "Failed to get exposure (SHUTTER) value");
         exposure_ms=1000.0 * (double) exposure_s;
      }      
      pProp->Set(exposure_ms);
   }
   return DEVICE_OK;
}

int Cdc1394::OnIntegration(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      int maxNrIntegration = 1 << (16 - depth_);

      long tmp;
      pProp->Get(tmp);
      if (tmp > maxNrIntegration)
         integrateFrameNumber_ = maxNrIntegration;
      else if (tmp < 1)
         integrateFrameNumber_ = 1;
      else if (tmp >= 1 && tmp <= maxNrIntegration)
         integrateFrameNumber_ = tmp;
      img_.Resize(width_, height_, GetBytesPerPixel());
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set((long)integrateFrameNumber_);
   }
   return DEVICE_OK;
}

// ScanMode
int Cdc1394::OnScanMode(MM::PropertyBase* /* pProp */ , MM::ActionType /* eAct*/ )
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
      CHECK_DC1394_ERROR(dc1394_feature_set_mode(camera_, feature, DC1394_FEATURE_MODE_MANUAL), DEVICE_ERR,
            "Failed to set mode of feature to MANUAL");
      CHECK_DC1394_ERROR(dc1394_feature_set_value(camera_, feature, value), DEVICE_ERR,
            "Failed to set value for feature");
      logMsg_.str("");
      logMsg_ << "Settings feature " << feature << " to " << value;
      LogMessage(logMsg_.str().c_str(), true);
      // we may have changed to manual
      OnPropertiesChanged();
   }
   else if (eAct == MM::BeforeGet)
   {
      LOG_DC1394_ERROR(dc1394_feature_get_value(camera_, feature, &value), 
            "Failed to get value for feature " + boost::lexical_cast<string>(feature));
      logMsg_.str("");
      logMsg_ << "Getting feature " << feature << ".  It is now " << value;
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
			CHECK_DC1394_ERROR(dc1394_feature_set_power(camera_, feature, DC1394_OFF), DEVICE_ERR,
               "Failed to switch off feature");
		} else {
			if ( value == "MANUAL" )
				mode = DC1394_FEATURE_MODE_MANUAL;
			else if ( value == "AUTO" )
				mode = DC1394_FEATURE_MODE_AUTO;
			else
				mode = DC1394_FEATURE_MODE_ONE_PUSH_AUTO;
			CHECK_DC1394_ERROR(dc1394_feature_set_mode(camera_, feature, mode), DEVICE_ERR,
               "Failed to set mode of feature");
			if ( mode == DC1394_FEATURE_MODE_ONE_PUSH_AUTO) {
				// set to MANUAL after one push auto
				CHECK_DC1394_ERROR(dc1394_feature_set_mode(camera_, feature, DC1394_FEATURE_MODE_MANUAL), DEVICE_ERR,
                  "Failed to set mode of feature back to MANUAL (after setting to ONE_PUSH_AUTO)");
			}
		}
		
		OnPropertiesChanged();
		
	}
	else if (eAct == MM::BeforeGet) // property will be read -> update property with value from hardware
	{  	
		CHECK_DC1394_ERROR(dc1394_feature_get_power(camera_, feature, &pwSwitch), DEVICE_ERR,
            "Failed to get on/off state of feature");
		if ( pwSwitch == DC1394_OFF ) {
			pProp->Set("OFF");
		}
		else {
			CHECK_DC1394_ERROR(dc1394_feature_get_mode(camera_, feature, &mode), DEVICE_ERR,
               "Failed to get mode of feature");
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
	return OnFeature(pProp, eAct, brightness_, brightnessMin_, brightnessMax_, DC1394_FEATURE_BRIGHTNESS);
}


int Cdc1394::OnBrightnessMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	return OnFeatureMode(pProp, eAct, DC1394_FEATURE_BRIGHTNESS);
}


// Hue
int Cdc1394::OnHue(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	return OnFeature(pProp, eAct, brightness_, brightnessMin_, brightnessMax_, DC1394_FEATURE_HUE);
}


int Cdc1394::OnHueMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	return OnFeatureMode(pProp, eAct, DC1394_FEATURE_HUE);
}


// Saturation
int Cdc1394::OnSaturation(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	return OnFeature(pProp, eAct, brightness_, brightnessMin_, brightnessMax_, DC1394_FEATURE_SATURATION);
}


int Cdc1394::OnSaturationMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	return OnFeatureMode(pProp, eAct, DC1394_FEATURE_SATURATION);
}


// Gain
int Cdc1394::OnGain(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  return OnFeature(pProp, eAct, gain_, gainMin_, gainMax_, DC1394_FEATURE_GAIN);
}


int Cdc1394::OnGainMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	return OnFeatureMode(pProp, eAct, DC1394_FEATURE_GAIN);
}

// Gamma
int Cdc1394::OnGamma(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   return OnFeature(pProp, eAct, gain_, gainMin_, gainMax_, DC1394_FEATURE_GAMMA);
}


int Cdc1394::OnGammaMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	return OnFeatureMode(pProp, eAct, DC1394_FEATURE_GAMMA);
}


// Temperature
int Cdc1394::OnTemp(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	return OnFeature(pProp, eAct, gain_, gainMin_, gainMax_, DC1394_FEATURE_TEMPERATURE);
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
      CHECK_DC1394_ERROR(dc1394_feature_set_absolute_control(camera_, DC1394_FEATURE_SHUTTER, DC1394_OFF), DEVICE_ERR,
            "Failed to switch off absolute mode for feature SHUTTER");
   }
   return OnFeature(pProp, eAct, shutter_, shutterMin_, shutterMax_, DC1394_FEATURE_SHUTTER);
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
			CHECK_DC1394_ERROR(dc1394_feature_whitebalance_get_value(camera_, &ub, &vr), DEVICE_ERR,
               "Failed to get white balance value");
			// Make sure we are in manual mode, otherwise changing the value has little effect 
			CHECK_DC1394_ERROR(dc1394_feature_set_mode(camera_, DC1394_FEATURE_WHITE_BALANCE, DC1394_FEATURE_MODE_MANUAL),
               DEVICE_ERR, "Failed to set mode of feature WHITE_BALANCE to MANUAL");
			// set to value
			if ( valueColor == COLOR_UB )
				ub = value;
			else 
				vr = value;
			CHECK_DC1394_ERROR(dc1394_feature_whitebalance_set_value(camera_, ub, vr), DEVICE_ERR,
               "Failed to set white balance value");
			logMsg_.str("");
			logMsg_ << "Set whitebalance to " << value;
			LogMessage(logMsg_.str().c_str(), true);
		} else {
			// Set White Shading
			// Find the "other" value first
			CHECK_DC1394_ERROR(dc1394_feature_whiteshading_get_value(camera_, &red, &green, &blue), DEVICE_ERR,
               "Failed to get white shading value");
			// Make sure we are in manual mode, otherwise changing the value has little effect 
			CHECK_DC1394_ERROR(dc1394_feature_set_mode(camera_, DC1394_FEATURE_WHITE_SHADING, DC1394_FEATURE_MODE_MANUAL),
               DEVICE_ERR, "Failed to set mode of feature WHITE_SHADING to MANUAL");
			switch (valueColor) {
				case COLOR_RED:
					red = value;
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
			CHECK_DC1394_ERROR(dc1394_feature_whiteshading_get_value(camera_, &red, &green, &blue), DEVICE_ERR,
               "Failed to get white shading value");
		}

		// we may have changed to manual
		OnPropertiesChanged();
	}
	else if (eAct == MM::BeforeGet)
	{
		if ( valueColor == COLOR_UB || valueColor == COLOR_VR ) {
			// White balance
			CHECK_DC1394_ERROR(dc1394_feature_whitebalance_get_value(camera_, &ub, &vr), DEVICE_ERR,
               "Failed to get white balance value");

			logMsg_.str("");
			logMsg_ << "Got whitebalance. It is now " << value;
			LogMessage (logMsg_.str().c_str(), true);
			tmp = (long) (valueColor == COLOR_UB ? ub : vr);
		} else {
			// White Shading
			CHECK_DC1394_ERROR(dc1394_feature_whiteshading_get_value(camera_, &red, &green, &blue), DEVICE_ERR,
               "Failed to get white shading value");
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
               tmp = 0L; // Not reached.
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
	return OnColorFeature(pProp, eAct, colub_, colMin_, colMax_, COLOR_UB);
}


int Cdc1394::OnWhitebalanceVR(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	return OnColorFeature(pProp, eAct, colvr_, colMin_, colMax_, COLOR_VR);
}


int Cdc1394::OnWhiteshadingMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	return OnFeatureMode(pProp, eAct, DC1394_FEATURE_WHITE_SHADING);
}


int Cdc1394::OnWhiteshadingRed(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	return OnColorFeature(pProp, eAct, colred_, colMin_, colMax_, COLOR_RED);
}


int Cdc1394::OnWhiteshadingBlue(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	return OnColorFeature(pProp, eAct, colblue_, colMin_, colMax_, COLOR_BLUE);
}


int Cdc1394::OnWhiteshadingGreen(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	return OnColorFeature(pProp, eAct, colgreen_, colMin_, colMax_, COLOR_GREEN);
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
      CHECK_DC1394_ERROR(dc1394_external_trigger_set_power(camera_, pwr), DEVICE_ERR,
            string("Failed to turn external trigger ") + (pwr == DC1394_ON ? "on" : "off"));
      logMsg_.clear();
      logMsg_ << "Set external trigger state:" << triggerStatus;
      LogMessage(logMsg_.str().c_str(), true);
   }
   else if (eAct == MM::BeforeGet)
   {
      CHECK_DC1394_ERROR(dc1394_external_trigger_get_power(camera_, &pwr), DEVICE_ERR,
            "Failed to get on/off state of external trigger");
      triggerStatus = (pwr==DC1394_ON) ? 1L : 0L;
      logMsg_.str("");
      logMsg_ << "Got external trigger state:" << triggerStatus;
      LogMessage (logMsg_.str().c_str(), true);
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


// Unary predicate for std::find_if(); used in Initialize()
// XXX See comment where used about getting rid of this
class IsFeatureAvailable {
   dc1394feature_t id_;
public:
   IsFeatureAvailable(dc1394feature_t id) : id_(id) {}
   bool operator()(const dc1394feature_info_t& feature) { return feature.id == id_ && feature.available; }
};


int Cdc1394::Initialize()
{
   CHECK_MM_ERROR(SetUpCamera());


   //
   // Vendor and model
   //
   string vendor(camera_->vendor ? camera_->vendor : "Unknown");
   if (vendor.empty()) {
      vendor = "Unknown";
   }
   CHECK_MM_ERROR(CreateProperty("Camera Vendor", vendor.c_str(), MM::String, true));
   LogMessage("Camera Vendor: " + vendor);

   string model(camera_->model ? camera_->model : "Unknwon");
   if (model.empty()) {
      model = "Unknown";
   }
   CHECK_MM_ERROR(CreateProperty("Camera Model", model.c_str(), MM::String, true));
   LogMessage("Camera Model: " + model);


   //
   // Model-specific stuff (TODO: document these)
   //
   if (!strncmp("XCD-X700", camera_->model, 8)) {
      isSonyXCDX700_ = true;
      LogMessage("Found a Sony XCD-X700 camera");
   }

   // TODO once we can set the interlacing up externally
   // check if appropriate property is set
   if(!strncmp("Guppy F025",camera_->model,10) ||
			 !strncmp("Guppy F029",camera_->model,10) || 
			 !strncmp("Guppy F038",camera_->model,10) ||
			 !strncmp("Guppy F044",camera_->model,10) ) {
	   avtInterlaced_ = true;
	   LogMessage("Camera is Guppy interlaced series");
   }


   //
   // Video modes
   //
   dc1394video_modes_t modes;
   CHECK_DC1394_ERROR(dc1394_video_get_supported_modes(camera_, &modes), ERR_MODE_LIST_NOT_FOUND,
         "Failed to get list of supported video modes");

   CPropertyAction* pAct = new CPropertyAction(this, &Cdc1394::OnMode);
   dc1394video_mode_t videoMode = modes.modes[0];
   CHECK_MM_ERROR(CreateProperty(g_Keyword_Modes, StringForVideoMode(videoMode).c_str(), MM::String, false, pAct));
   for (unsigned i = 0; i < modes.num; i++) {
      string modeString = StringForVideoMode(modes.modes[i]);
      if (!modeString.empty()) {
         CHECK_MM_ERROR(AddAllowedValue(g_Keyword_Modes, modeString.c_str()));
         LogMessage("Found video mode: " + modeString, true);
      }
   }

   CHECK_MM_ERROR(SetVideoMode(videoMode));


   // Frame integration (Currently only implemented for 8-bit images)
   pAct = new CPropertyAction (this, &Cdc1394::OnIntegration);
   CHECK_MM_ERROR(CreateProperty("Integration", "1", MM::Integer, false, pAct));
   // TODO Set allowed range (which needs to be updated when mode changes)

   CHECK_MM_ERROR(CreateProperty("Integration Note", "Not implemented for 16-bit mono", MM::String, true));


   //
   // Binning: unimplemented
   //
   // Note: IIDC does not support binning; some vendors do through Format_7
   // video modes.
   CHECK_MM_ERROR(CreateProperty(MM::g_Keyword_Binning, "1", MM::Integer, false));
   CHECK_MM_ERROR(AddAllowedValue(MM::g_Keyword_Binning, "1"));
   SetProperty(MM::g_Keyword_Binning,"1"); // XXX Do we need this? (Comment said needed on Mac but I doubt it.)


   //
   // Timeout
   //
   pAct = new CPropertyAction(this, &Cdc1394::OnTimeout);
   CHECK_MM_ERROR(CreateProperty("Timeout(ms)", "1000", MM::Float, false, pAct));


   //
   // Other, camera-dependent features
   //
   // XXX There is no reason to use dc1394_feature_get_all(); we should just
   // use dc1394_feature_get() for each feature.
   dc1394featureset_t features;
   CHECK_DC1394_ERROR(dc1394_feature_get_all(camera_, &features), ERR_GET_CAMERA_FEATURE_SET_FAILED,
         "Failed to get camera feature list");


   // The member features.feature is an array, size DC1394_FEATURE_NUM, of
   // dc1394feature_info_t structs; we find features using the id field (using
   // pointers as C++ iterators).
   const dc1394feature_info_t* features_begin = features.feature;
   const dc1394feature_info_t* features_end = features_begin + DC1394_FEATURE_NUM;
   const dc1394feature_info_t* pFeature;


   pFeature = find_if(features_begin, features_end, IsFeatureAvailable(DC1394_FEATURE_SHUTTER));
   if (pFeature != features_end) {
      bool hasManual;
      int err = InitFeatureModeProperty(*pFeature, hasManual, &Cdc1394::OnShutterMode);
      if (err == DEVICE_OK && hasManual) {
         err = InitManualFeatureProperty(*pFeature, shutter_, shutterMin_, shutterMax_, &Cdc1394::OnShutter);
      }

      // XXX Do we ever have absolute control without having manual mode?

      dc1394bool_t hasAbsoluteShutterControl;
      dc1394error_t dcerr = dc1394_feature_has_absolute_control(camera_, DC1394_FEATURE_SHUTTER,
               &hasAbsoluteShutterControl);
      if (dcerr != DC1394_SUCCESS) {
            LogMessage("Failed to check if feature SHUTTER has absolute mode");
      }
      else {
         if (hasAbsoluteShutterControl == DC1394_TRUE) {
            absoluteShutterControl_ = true;
         }
      }

      if (!absoluteShutterControl_ && camera_->vendor_id == AVT_VENDOR_ID){
         LogMessage("Checking for AVT absolute shutter");
         uint32_t timebase_id;
         if (dc1394_avt_get_extented_shutter(camera_, &timebase_id) == DC1394_SUCCESS) {
            absoluteShutterControl_ = true;
         }
      }

      LogMessage("Absolute shutter control is " + string(absoluteShutterControl_ ? "available" : "not available"));
   }


   pFeature = find_if(features_begin, features_end, IsFeatureAvailable(DC1394_FEATURE_EXPOSURE));
   if (pFeature != features_end) {
      bool hasManual;
      int err = InitFeatureModeProperty(*pFeature, hasManual, &Cdc1394::OnExposureMode);

      if (err == DEVICE_OK) {
         LOG_DC1394_ERROR(dc1394_feature_get_value(camera_, DC1394_FEATURE_EXPOSURE, &exposure_),
               "Failed to get value of feature EXPOSURE");
         LOG_DC1394_ERROR(dc1394_feature_get_boundaries(camera_, DC1394_FEATURE_EXPOSURE, &exposureMin_, &exposureMax_),
               "Failed to get allowed range for feature EXPOSURE");
         // XXX BUG We need to tell code that uses EXPOSURE not to use it!
      }
   }

   // For exposure, use the Shutter feature if available and supports absolute
   // control; otherwise we don't have control over exposure. Note that we
   // create the Exposure property even if MANUAL control of the Shutter
   // feature is not available (XXX is this the right thing to do?).
   if(absoluteShutterControl_) {
      pAct = new CPropertyAction(this, &Cdc1394::OnExposure);
      CHECK_MM_ERROR(CreateProperty(MM::g_Keyword_Exposure, "10.0", MM::Float, false, pAct));
   }


   pFeature = find_if(features_begin, features_end, IsFeatureAvailable(DC1394_FEATURE_TRIGGER));
   if (pFeature != features_end) {
      dc1394switch_t pwr;
      dc1394error_t dcerr = dc1394_external_trigger_get_power(camera_, &pwr);
      if (dcerr != DC1394_SUCCESS) {
         LogMessage("Failed to get on/off state for external trigger");
      }
      else {
         // Start up with trigger mode disabled, to avoid confusion
         if (pwr == DC1394_ON) {
             dcerr = dc1394_external_trigger_set_power(camera_, DC1394_OFF);
             if (dcerr != DC1394_SUCCESS) {
                LogMessage("Failed to turn off external trigger");
             }
         }

         pAct = new CPropertyAction (this, &Cdc1394::OnExternalTrigger);
         CHECK_MM_ERROR(CreateProperty("ExternalTrigger", "0", MM::Integer, false, pAct)); 
         AddAllowedValue("ExternalTrigger", "0");
         AddAllowedValue("ExternalTrigger", "1");
      }
   }


   pFeature = find_if(features_begin, features_end, IsFeatureAvailable(DC1394_FEATURE_BRIGHTNESS));
   if (pFeature != features_end) {
      bool hasManual;
      int err = InitFeatureModeProperty(*pFeature, hasManual, &Cdc1394::OnBrightnessMode);
      if (err == DEVICE_OK && hasManual) {
         err = InitManualFeatureProperty(*pFeature, brightness_, brightnessMin_, brightnessMax_,
                  &Cdc1394::OnBrightness);
      }
   }


   pFeature = find_if(features_begin, features_end, IsFeatureAvailable(DC1394_FEATURE_GAIN));
   if (pFeature != features_end) {
      bool hasManual;
      int err = InitFeatureModeProperty(*pFeature, hasManual, &Cdc1394::OnGainMode);
      if (err == DEVICE_OK && hasManual) {
         err = InitManualFeatureProperty(*pFeature, gain_, gainMin_, gainMax_,
                  &Cdc1394::OnGain, MM::g_Keyword_Gain);
      }
   }


   pFeature = find_if(features_begin, features_end, IsFeatureAvailable(DC1394_FEATURE_GAMMA));
   if (pFeature != features_end) {
      bool hasManual;
      int err = InitFeatureModeProperty(*pFeature, hasManual, &Cdc1394::OnGammaMode);
      if (err == DEVICE_OK && hasManual) {
         err = InitManualFeatureProperty(*pFeature, gamma_, gammaMin_, gammaMax_,
                  &Cdc1394::OnGamma);
      }
   }


   pFeature = find_if(features_begin, features_end, IsFeatureAvailable(DC1394_FEATURE_HUE));
   if (pFeature != features_end) {
      bool hasManual;
      int err = InitFeatureModeProperty(*pFeature, hasManual, &Cdc1394::OnHueMode);
      if (err == DEVICE_OK && hasManual) {
         err = InitManualFeatureProperty(*pFeature, hue_, hueMin_, hueMax_, &Cdc1394::OnHue);
      }
   }


   pFeature = find_if(features_begin, features_end, IsFeatureAvailable(DC1394_FEATURE_SATURATION));
   if (pFeature != features_end) {
      bool hasManual;
      int err = InitFeatureModeProperty(*pFeature, hasManual, &Cdc1394::OnSaturationMode);
      if (err == DEVICE_OK && hasManual) {
         err = InitManualFeatureProperty(*pFeature, saturation_, saturationMin_, saturationMax_,
                  &Cdc1394::OnSaturation);
      }
   }


   pFeature = find_if(features_begin, features_end, IsFeatureAvailable(DC1394_FEATURE_TEMPERATURE));
   if (pFeature != features_end) {
      bool hasManual;
      int err = InitFeatureModeProperty(*pFeature, hasManual, &Cdc1394::OnTempMode);
      if (err == DEVICE_OK && hasManual) {
         err = InitManualFeatureProperty(*pFeature, temperature_, temperatureMin_, temperatureMax_,
                  &Cdc1394::OnTemp);
      }
   }


   pFeature = find_if(features_begin, features_end, IsFeatureAvailable(DC1394_FEATURE_WHITE_BALANCE));
   if (pFeature != features_end) {
      bool hasManual;
      int err = InitFeatureModeProperty(*pFeature, hasManual,
               &Cdc1394::OnWhitebalanceMode, "WhiteBalanceSetting");

      if (err == DEVICE_OK && hasManual) {
         if (pFeature->readout_capable) {
            colub_ = pFeature->BU_value;
            colvr_ = pFeature->RV_value;
         }
         colMin_ = 0;
         colMax_ = 255;
         
         pAct = new CPropertyAction(this, &Cdc1394::OnWhitebalanceUB);
         const string ubString(boost::lexical_cast<string>(colub_));
         CHECK_MM_ERROR(CreateProperty("WhitebalanceUB", ubString.c_str(), MM::Integer, false, pAct));
         CHECK_MM_ERROR(SetPropertyLimits("WhitebalanceUB", 0, 1000));

         pAct = new CPropertyAction(this, &Cdc1394::OnWhitebalanceVR);
         const string vrString(boost::lexical_cast<string>(colvr_));
         CHECK_MM_ERROR(CreateProperty("WhitebalanceVR", vrString.c_str(), MM::Integer, false, pAct));
         CHECK_MM_ERROR(SetPropertyLimits("WhitebalanceVR", 0, 1000));
      }
   }


   pFeature = find_if(features_begin, features_end, IsFeatureAvailable(DC1394_FEATURE_WHITE_SHADING));
   if (pFeature != features_end) {
      bool hasManual;
      int err = InitFeatureModeProperty(*pFeature, hasManual,
               &Cdc1394::OnWhiteshadingMode, "WhiteShadingSetting");

      if (err == DEVICE_OK && hasManual) {
         if (pFeature->readout_capable) {
            colred_ = pFeature->R_value;
            colgreen_ = pFeature->G_value;
            colblue_ = pFeature->B_value;
         }
         colMin_ = 0;
         colMax_ = 255;
         
         pAct = new CPropertyAction(this, &Cdc1394::OnWhiteshadingRed);
         const string redString(boost::lexical_cast<string>(colred_));
         CHECK_MM_ERROR(CreateProperty("WhiteshadingRed", redString.c_str(), MM::Integer, false, pAct));
         CHECK_MM_ERROR(SetPropertyLimits("WhiteshadingRed", 0, 1000));

         pAct = new CPropertyAction(this, &Cdc1394::OnWhiteshadingGreen);
         const string greenString(boost::lexical_cast<string>(colgreen_));
         CHECK_MM_ERROR(CreateProperty("WhiteshadingGreen", greenString.c_str(), MM::Integer, false, pAct));
         CHECK_MM_ERROR(SetPropertyLimits("WhiteshadingGreen", 0, 1000));

         pAct = new CPropertyAction(this, &Cdc1394::OnWhiteshadingBlue);
         const string blueString(boost::lexical_cast<string>(colblue_));
         CHECK_MM_ERROR(CreateProperty("WhiteshadingBlue", blueString.c_str(), MM::Integer, false, pAct));
         CHECK_MM_ERROR(SetPropertyLimits("WhiteshadingBlue", 0, 1000));
      }
   }

   CHECK_MM_ERROR(StartCapture());

   return DEVICE_OK;
}


// Set up a property for a feature's mode (auto/manual, etc.); determine whether manual mode is available
int Cdc1394::InitFeatureModeProperty(const dc1394feature_info_t& featureInfo,
      bool& hasManualMode,
      int (Cdc1394::*cb_onfeaturemode)(MM::PropertyBase*, MM::ActionType),
      const string& overridePropertyName)
{
   string propertyName;
   if (overridePropertyName.empty()) {
      propertyName = string(dc1394_feature_get_string(featureInfo.id)) + "Setting";
   }
   else {
      propertyName = overridePropertyName;
   }

   // libdc1394 considers manual/one-push/auto to be modes...
   set<int> availableModes;
   for (unsigned i = 0; i < featureInfo.modes.num; i++) {
      dc1394feature_mode_t mode = featureInfo.modes.modes[i];
      availableModes.insert(mode);
   }

   // ... but we want to consider "off" to be a mode, too.
   const int FEATURE_MODE_OFF = DC1394_FEATURE_MODE_MAX + 1;
   if (featureInfo.on_off_capable) {
      availableModes.insert(FEATURE_MODE_OFF);

      // Start with feature turned on by default
      CHECK_DC1394_ERROR(dc1394_feature_set_power(camera_, featureInfo.id, DC1394_ON), DEVICE_ERR,
            "Failed to turn on feature: " + string(dc1394_feature_get_string(featureInfo.id)));
   }

   dc1394feature_mode_t initialMode = DC1394_FEATURE_MODE_MANUAL;
   const char* defaultModeString = "MANUAL";
   if (availableModes.count(DC1394_FEATURE_MODE_AUTO)) {
      initialMode = DC1394_FEATURE_MODE_AUTO;
      defaultModeString = "AUTO";
   }

   // Bring hardware into sync with our default mode
   CHECK_DC1394_ERROR(dc1394_feature_set_mode(camera_, featureInfo.id, initialMode), DEVICE_ERR,
         "Failed to set mode of feature " + string(dc1394_feature_get_string(featureInfo.id)));

   if (!availableModes.empty()) {
      CPropertyAction *pAct = new CPropertyAction(this, cb_onfeaturemode);
      CHECK_MM_ERROR(CreateProperty(propertyName.c_str(), defaultModeString, MM::String, false, pAct));

      if (availableModes.count(FEATURE_MODE_OFF)) {
         AddAllowedValue(propertyName.c_str(), "OFF");
      }
      if (availableModes.count(DC1394_FEATURE_MODE_MANUAL)) {
         AddAllowedValue(propertyName.c_str(), "MANUAL");
      }
      if (availableModes.count(DC1394_FEATURE_MODE_ONE_PUSH_AUTO)) {
         AddAllowedValue(propertyName.c_str(), "ONE-PUSH");
      }
      if (availableModes.count(DC1394_FEATURE_MODE_AUTO)) {
         AddAllowedValue(propertyName.c_str(), "AUTO");
      }
   }

   hasManualMode = (availableModes.count(DC1394_FEATURE_MODE_MANUAL) != 0);

   return DEVICE_OK;
}


int Cdc1394::InitManualFeatureProperty(const dc1394feature_info_t& featureInfo,
      uint32_t &value, uint32_t &valueMin, uint32_t &valueMax,
      int (Cdc1394::*cb_onfeature)(MM::PropertyBase*, MM::ActionType),
      const string& overridePropertyName)
{
   string propertyName;
   if (overridePropertyName.empty()) {
      propertyName = dc1394_feature_get_string(featureInfo.id);
   }
   else {
      propertyName = overridePropertyName;
   }

   if (featureInfo.readout_capable) {
      value = featureInfo.value;
      valueMin = featureInfo.min;
      valueMax = featureInfo.max;
   }
   else {
      // some sensible (?) default values
      // XXX BUG: should use featureInfo.min and featureInfo.max; they should
      // be valid even if not capable of readout.
      value = 0;
      valueMin = 0;
      valueMax = 1000;
   }

   CPropertyAction *pAct = new CPropertyAction(this, cb_onfeature);
   const string valueString(boost::lexical_cast<string>(value));
   CHECK_MM_ERROR(CreateProperty(propertyName.c_str(), valueString.c_str(), MM::Integer, false, pAct));
   CHECK_MM_ERROR(SetPropertyLimits(propertyName.c_str(), valueMin, valueMax));

   return DEVICE_OK;
}


int Cdc1394::Shutdown()
{
   if (acqThread_)
   {
      LogMessage("Will stop acquisition thread");
      acqThread_->Stop();
      delete acqThread_;
      acqThread_ = 0;
      LogMessage("Did stop acquisition thread");
   }

   if (camera_)
   {
      LogMessage("Will release camera");
      dc1394_camera_free(camera_);
      camera_ = 0;
      LogMessage("Did release camera");
   }

   return DEVICE_OK;
}


int Cdc1394::SetUpCamera()
{
   dc1394Context_.Acquire();
   if (!dc1394Context_.Get()) {
      return ERR_DC1394;
   }

   dc1394camera_list_t* list;
   CHECK_DC1394_ERROR(dc1394_camera_enumerate(dc1394Context_.Get(), &list),
         ERR_CAMERA_NOT_FOUND, "Failed to get list of cameras");
   if (list->num == 0) 
      return ERR_CAMERA_NOT_FOUND;
   
   // TODO Support multiple cameras
   camera_ = dc1394_camera_new(dc1394Context_.Get(), list->ids[0].guid);
   if (!camera_)
      return ERR_INITIALIZATION_FAILED;

   dc1394_camera_free_list(list);

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



// GJ: Deinterlace fields from interlaced AVT Guppy cameras
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
      CHECK_DC1394_ERROR(dc1394_capture_enqueue(camera_, frame_), DEVICE_ERR,
            "Failed to enqueue frame buffer");

   // TODO In all that follows, the actual error returned by
   // dc1394_capture_dequeue() should be checked to see if it is
   // DC1394_NO_FRAME.

   // Empty the ring buffer to ensure we get a fresh frame
   // XXX Would it make sense to limit the number of images to dequeue to the
   // known size of the ring buffer?
   MM::MMTime startTime = GetCurrentMMTime();
   while (!Timeout(startTime)) {
      dc1394error_t err = dc1394_capture_dequeue(camera_, DC1394_CAPTURE_POLICY_POLL, &frame_);
      if (frame_ && err == DC1394_SUCCESS) {
         CHECK_DC1394_ERROR(dc1394_capture_enqueue(camera_, frame_), DEVICE_ERR,
               "Failed to enqueue frame buffer");
      } else {
         break;
      }
   }
          
   // Exposure of the following image might have started before the SnapImage function was called, so discard it:
   // XXX Why can't we just do this with DC1394_CAPTURE_POLICY_WAIT?
   bool frameFound = false;
   startTime = GetCurrentMMTime();
   while (!frameFound && !Timeout(startTime) ) {
      dc1394error_t err = dc1394_capture_dequeue(camera_, DC1394_CAPTURE_POLICY_POLL, &frame_);
      if (frame_ && err == DC1394_SUCCESS) {
         frameFound = true;
         CHECK_DC1394_ERROR(dc1394_capture_enqueue(camera_, frame_), DEVICE_ERR,
               "Failed to enqueue frame buffer");
      } else
         CDeviceUtils::SleepMs(10);
   }
   if (!frameFound)
      return  ERR_CAPTURE_TIMEOUT;


   // Now capture the frame that we are interested in
   // XXX Why can't we just do this with DC1394_CAPTURE_POLICY_WAIT?
   frameFound = false;
   startTime = GetCurrentMMTime();
   while (!frameFound && !Timeout(startTime) ) {
      dc1394error_t err = dc1394_capture_dequeue(camera_, DC1394_CAPTURE_POLICY_POLL, &frame_);
      if (frame_ && err == DC1394_SUCCESS) {
         dequeued_ = true;
         frameFound = true;
      } else 
         CDeviceUtils::SleepMs(10);
   }
   if (!frameFound)
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
   int numPixels = width_ * height_;
   if (colorCoding_==DC1394_COLOR_CODING_YUV411 || colorCoding_==DC1394_COLOR_CODING_YUV422 || colorCoding_==DC1394_COLOR_CODING_YUV444) 
   {
      uint8_t *rgb_image = (uint8_t *)malloc(3 * numPixels);
      CHECK_DC1394_ERROR(dc1394_convert_to_RGB8((uint8_t *)frame->image,
               rgb_image, width_, height_, DC1394_BYTE_ORDER_UYVY, colorCoding_, 16),
            DEVICE_ERR, "Failed to convert image to RGB8");
      // no integration, straight forward stuff
      if (integrateFrameNumber_ == 1)
      {
         uint8_t* pixBuffer = const_cast<uint8_t*> (destination);
         rgb8ToBGRA8(pixBuffer, rgb_image, width_, height_);
      }
      // when integrating we are dealing with 16 bit images
      if (integrateFrameNumber_ > 1)
      {
         void* pixBuffer = const_cast<unsigned char*> (destination);
         // we need to clear the memory since we'll add to it, not overwrite it
         memset(pixBuffer, 0, GetImageBufferSize());
         rgb8AddToMono16((uint16_t*) pixBuffer, rgb_image, width_, height_);
         // now repeat for the other frames:
         for (int frameNr=1; frameNr<integrateFrameNumber_; frameNr++)
         {
            CHECK_DC1394_ERROR(dc1394_capture_dequeue(camera_, DC1394_CAPTURE_POLICY_WAIT, &internalFrame),
                  ERR_CAPTURE_FAILED, "Failed to dequeue frame buffer (POLICY_WAIT)");
            CHECK_DC1394_ERROR(dc1394_convert_to_RGB8((uint8_t *)internalFrame->image,
                     rgb_image, width_, height_, DC1394_BYTE_ORDER_UYVY, colorCoding_, 16),
                  DEVICE_ERR, "Failed to convert image to RGB8");
            CHECK_DC1394_ERROR(dc1394_capture_enqueue(camera_, internalFrame), DEVICE_ERR,
                  "Failed to enqueue frame buffer");
            rgb8AddToMono16((uint16_t*) pixBuffer, rgb_image, width_, height_);
         }
      }
      free(rgb_image);
   }

   else if (colorCoding_==DC1394_COLOR_CODING_MONO8 || colorCoding_==DC1394_COLOR_CODING_MONO16) 
   {
      // Frame integration not implemented for 16-bit
      if (integrateFrameNumber_ <= 1 || colorCoding_ == DC1394_COLOR_CODING_MONO16) 
      {
         void* src = (void *) frame->image;
         uint8_t* pixBuffer = const_cast<unsigned char*> (destination);
         if (avtInterlaced_) {
            if (colorCoding_ == DC1394_COLOR_CODING_RGB8)
               avtDeinterlaceMono8(pixBuffer, (uint8_t*) src, width_, height_);
            else
               avtDeinterlaceMono16((uint16_t*)pixBuffer, (uint16_t*)src, width_, height_);
         }
		   else 
            memcpy (pixBuffer, src, GetImageBufferSize());
      }
      else
      {
         void* src = (void *) frame->image;
         uint8_t* pixBuffer = const_cast<unsigned char*> (destination);
         memset(pixBuffer, 0, GetImageBufferSize());
         mono8AddToMono16((uint16_t*) pixBuffer, (uint8_t*) src, width_, height_);
         // now repeat for the other frames:
         for (int frameNr=1; frameNr<integrateFrameNumber_; frameNr++)
         {
            CHECK_DC1394_ERROR(dc1394_capture_dequeue(camera_, DC1394_CAPTURE_POLICY_WAIT, &internalFrame),
                  ERR_CAPTURE_FAILED, "Failed to dequeue frame buffer (POLICY_WAIT)");
            mono8AddToMono16((uint16_t*) pixBuffer, (uint8_t*) frame->image, width_, height_);
            CHECK_DC1394_ERROR(dc1394_capture_enqueue(camera_, internalFrame), DEVICE_ERR,
                  "Failed to enqueue frame buffer");
         }
		 // GJ:  Finished integrating, so we can deinterlace the 16 bit result
		 // That does mean doing a pixel buffer shuffle unfortunately
		 // but prob better than deinterlacing every 8 bit image
		 // perhaps better still would be a mono8DeinterlacedAddToMono16Function
		 if (avtInterlaced_) {
			 uint16_t *interlacedImaged = (uint16_t *) malloc(numPixels*sizeof(uint16_t));
			 memcpy(interlacedImaged, (uint16_t*) pixBuffer, GetImageBufferSize());
			 avtDeinterlaceMono16((uint16_t*) pixBuffer, interlacedImaged, width_, height_);
			 free(interlacedImaged);
		 }
	   }
   }
   else if (colorCoding_==DC1394_COLOR_CODING_RGB8) 
   {
      uint8_t* src;
      src = (uint8_t *) frame->image;
      uint8_t* pixBuffer = const_cast<uint8_t*> (destination);
      rgb8ToBGRA8 (pixBuffer, src, width_, height_);
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
   LOG_DC1394_ERROR(dc1394_capture_enqueue(camera_, frame_), "Failed to enqueue frame buffer");
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
}

int Cdc1394::SetBinning (int /*binSize*/) 
{
   // Not supported yet
   return ERR_NOT_IMPLEMENTED;
}

// GJ nb uX,uY = top left
int Cdc1394::SetROI(unsigned uX, unsigned uY, unsigned uXSize, unsigned uYSize)
{
   // GJ: Unsupported unless format 7
	if ( mode_ >= DC1394_VIDEO_MODE_FORMAT7_MIN && mode_ <= DC1394_VIDEO_MODE_FORMAT7_MAX ) {
      CHECK_MM_ERROR(StopCapture());
		
       logMsg_.str("");
		 logMsg_ << "SetROI: Aout to set to  " << uX << "," << uY << "," << uXSize << "," << uYSize ;
		LogMessage (logMsg_.str().c_str(), true);
		
		CHECK_DC1394_ERROR(dc1394_format7_set_roi(camera_, mode_, 
					(dc1394color_coding_t) DC1394_COLOR_CODING_MONO8 /*color_coding*/,
					(int) DC1394_USE_MAX_AVAIL /*bytes_per_packet*/, uX, uY, uXSize, uYSize),
            ERR_SET_F7_ROI_FAILED, "Failed to set Format_7 ROI");
		return StartCapture();
	} else return ERR_ROI_NOT_SUPPORTED;
}

int Cdc1394::GetROI(unsigned& uX, unsigned& uY, unsigned& uXSize, unsigned& uYSize)
{
	// GJ TODO - what should happen if ROI is not supported?
	// May not be correct just to return OK
   // XXX Should just return the full image size in that case.
	if ( mode_ >= DC1394_VIDEO_MODE_FORMAT7_MIN && mode_ <= DC1394_VIDEO_MODE_FORMAT7_MAX) {
		uint32_t bytes_per_packet;
		dc1394color_coding_t color_coding;
		CHECK_DC1394_ERROR(dc1394_format7_get_roi(camera_, mode_, &color_coding, &bytes_per_packet,
               &uX, &uY, &uXSize, &uYSize),
            ERR_GET_F7_ROI_FAILED, "Failed to get Format_7 ROI");
	}
	return DEVICE_OK;
}

int Cdc1394::ClearROI()
{
	if ( mode_ >=DC1394_VIDEO_MODE_FORMAT7_MIN && mode_ <= DC1394_VIDEO_MODE_FORMAT7_MAX) {
		// Get the recommended ROI - ie the max size of the image in
		// current format 7 mode
		uint32_t width, height;
		CHECK_DC1394_ERROR(dc1394_format7_get_max_image_size(camera_, mode_, &width, &height),
            ERR_GET_F7_MAX_IMAGE_SIZE_FAILED, "Failed to get Format_7 max image size");
		// now set that ROI
		return SetROI(0, 0, width, height);
	}
	return DEVICE_OK;
}


int Cdc1394::StartCapture()
{
   // TODO This should be a property and we should allow 800.
   CHECK_DC1394_ERROR(dc1394_video_set_iso_speed(camera_, DC1394_ISO_SPEED_400),
         DEVICE_ERR, "Failed to set ISO speed");

   if (mode_ >= DC1394_VIDEO_MODE_FORMAT7_MIN && mode_ <= DC1394_VIDEO_MODE_FORMAT7_MAX) {
      // No need to set frame rate for Format_7 (why?)
   }
   else {
      CHECK_DC1394_ERROR(dc1394_video_set_framerate(camera_, framerate_),
            ERR_SET_FRAMERATE_FAILED, "Failed to set framerate");
   }

   CHECK_DC1394_ERROR(dc1394_get_image_size_from_video_mode(camera_, mode_, &width_, &height_),
         ERR_GET_IMAGE_SIZE_FAILED, "Failed to get image size from video mode");
   CHECK_DC1394_ERROR(dc1394_video_get_data_depth(camera_, &depth_),
         DEVICE_ERR, "Failed to get bit depth");
   CHECK_DC1394_ERROR(dc1394_get_color_coding_from_video_mode(camera_, mode_, &colorCoding_),
         DEVICE_ERR, "Failed to get color coding from video mode");

   img_.Resize(width_, height_, GetBytesPerPixel());

   if (dc1394_capture_setup(camera_, dmaBufferSize_, DC1394_CAPTURE_FLAGS_DEFAULT) != DC1394_SUCCESS)
   {
      // Give it one more chance. Was found necessary on OS X with iSight.
      // (But I think that was just because capture was being started
      // without stopping the ongoing capture...)

      CHECK_DC1394_ERROR(dc1394_capture_stop(camera_),
            DEVICE_ERR, "Failed to stop capture after failed start");
      CHECK_DC1394_ERROR(dc1394_capture_setup(camera_, dmaBufferSize_, DC1394_CAPTURE_FLAGS_DEFAULT),
            ERR_CAPTURE_SETUP_FAILED, "Failed to start capture");
   }

   CHECK_DC1394_ERROR(dc1394_video_set_transmission(camera_, DC1394_ON),
         ERR_SET_TRANSMISSION_FAILED, "Failed to turn on transmission");

   // Wait until transmission starts. Transmission can take time to start. It
   // may also not start forever, e.g., if you try to use a camera or video
   // mode that requires ISO speed 800 and the ISO speed is set to 400.
   dc1394switch_t status = DC1394_OFF;
   for (int i = 0; status == DC1394_OFF && i < 100; i++) {
     CDeviceUtils::SleepMs(50);
     CHECK_DC1394_ERROR(dc1394_video_get_transmission(camera_, &status), ERR_GET_TRANSMISSION_FAILED,
           "Failed to get on/off state of transmission");
   }
   if (status == DC1394_OFF)
      return ERR_CAMERA_DOES_NOT_SEND_DATA;

   return DEVICE_OK;
}


int Cdc1394::StopCapture()
{
   // Stop both capture and transmission, even of one of the calls fails.
   dc1394error_t stopTransmissionErr = DC1394_SUCCESS;
   dc1394error_t stopCaptureErr = DC1394_SUCCESS;

   dc1394switch_t transmissionState = DC1394_ON;
   dc1394error_t err = dc1394_video_get_transmission(camera_, &transmissionState);
   LOG_DC1394_ERROR(err, "Failed to determine whether transmission is on");
   if (err == DC1394_SUCCESS && transmissionState == DC1394_OFF) {
      // Transmission is already stopped.
   }
   else {
      // Either transmission is on, or we already had an error so might as well
      // attempt to force a switch off.
      stopTransmissionErr = dc1394_video_set_transmission(camera_, DC1394_OFF);
      LOG_DC1394_ERROR(stopTransmissionErr, "Failed to turn off transmission");
      for (int i = 0; transmissionState != DC1394_OFF && i < 100; i++) {
         CDeviceUtils::SleepMs(50);
         dc1394error_t err = dc1394_video_get_transmission(camera_, &transmissionState);
         LOG_DC1394_ERROR(err, "Failed to determine whether transmission is on");
      }
      if (transmissionState != DC1394_OFF) {
         LogMessage("Transmission did not turn OFF after waiting for 5000 ms");
         return DEVICE_ERR;
      }
   }

   stopCaptureErr = dc1394_capture_stop(camera_);
   LOG_DC1394_ERROR(err, "Failed to stop capture");

   if (stopTransmissionErr != DC1394_SUCCESS || stopCaptureErr != DC1394_SUCCESS) {
      return DEVICE_ERR;
   }
   return DEVICE_OK;
}


/*
 * Set this feature in Manual mode and switch it on
 * TODO: implement this correctly!
 */
int Cdc1394::SetManual(dc1394feature_t feature)
{
    dc1394bool_t isSwitchable = DC1394_FALSE;
    CHECK_DC1394_ERROR(dc1394_feature_is_switchable(camera_, feature, &isSwitchable), DEVICE_ERR,
          "Failed to determine whether feature is switchable");
    if (isSwitchable)
       CHECK_DC1394_ERROR(dc1394_feature_set_power(camera_, feature, DC1394_ON), DEVICE_ERR,
             "Failed to turn on feature");
    // dc1394bool_t hasManualMode = DC1394_FALSE;
    //hasManualMode = feature->
    //err_ = dc1394_feature_has_manual_mode(camera_, feature, &hasManualMode);
    //if (hasManualMode)
    // XXX Use CHECK, not LOG, after determining whether feature has manual mode
    LOG_DC1394_ERROR(dc1394_feature_set_mode(camera_, feature, DC1394_FEATURE_MODE_MANUAL),
          "Failed to set mode of feature to MANUAL");
    return DEVICE_OK;
}


int Cdc1394::ShutdownImageBuffer()
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


int Cdc1394::GetBytesPerPixel() const
{
   if (depth_ <= 8 && integrateFrameNumber_ == 1) {
	  if (IsColor()) {
        return 4;
	  }
	  else {
        return 1;
	  }
   }
   else if (depth_ <= 8 && integrateFrameNumber_ > 1) {
      // When integration is enabled, we always return 16-bit monochrome images.
      return 2;
   }

   // 16-bit mode
   return 2;
}


// Must be called with capture stopped
int Cdc1394::SetVideoMode(dc1394video_mode_t newMode)
{
   mode_ = newMode;

   CHECK_DC1394_ERROR(dc1394_video_set_mode(camera_, mode_),
         ERR_SET_MODE_FAILED, "Failed to set video mode");

   // Update the pixel depth
   CHECK_DC1394_ERROR(dc1394_video_get_data_depth(camera_, &depth_),
         DEVICE_ERR, "Failed to get bit depth");

   // Update the list of available framerates
	if (mode_ >= DC1394_VIDEO_MODE_FORMAT7_MIN && mode_ <= DC1394_VIDEO_MODE_FORMAT7_MAX) {
      // Framerate does not apply to Format 7
      if (frameRatePropDefined_) {
         vector<string> rateValues;
         rateValues.push_back("N/A (Format 7)");
         CHECK_MM_ERROR(SetAllowedValues(g_Keyword_FrameRates, rateValues));
      }
	} else {
      CHECK_DC1394_ERROR(dc1394_video_get_supported_framerates(camera_, mode_, &framerates_),
            ERR_GET_FRAMERATES_FAILED, "Failed to get list of supported framerates");

      // Default to the first available framerate if the current setting is no
      // longer available
      dc1394framerate_t *frBegin = framerates_.framerates, *frEnd = frBegin + framerates_.num;
      if (find(frBegin, frEnd, framerate_) == frEnd) {
         framerate_ = framerates_.framerates[0];
      }

      if (!frameRatePropDefined_) {
         CPropertyAction *pAct = new CPropertyAction(this, &Cdc1394::OnFrameRate);
         CHECK_MM_ERROR(CreateProperty(g_Keyword_FrameRates, StringForFramerate(framerate_).c_str(),
               MM::String, false, pAct));
         frameRatePropDefined_ = true;
      }

      vector<string> rateValues;
      for (unsigned int i=0; i<framerates_.num; i++) {
         string tmp = StringForFramerate(framerates_.framerates[i]);
         if (tmp != "")
            rateValues.push_back(tmp);
      }   
      CHECK_MM_ERROR(SetAllowedValues(g_Keyword_FrameRates, rateValues));
   }

   return DEVICE_OK;
}


const map<dc1394video_mode_t, string>& Cdc1394::MakeVideoModeMap()
{
   static bool initialized = false;
   static map<dc1394video_mode_t, string> map;
   if (!initialized) {
      map.insert(std::make_pair(DC1394_VIDEO_MODE_160x120_YUV444, "160x120_YUV444"));
      map.insert(std::make_pair(DC1394_VIDEO_MODE_320x240_YUV422, "320x240_YUV422"));
      map.insert(std::make_pair(DC1394_VIDEO_MODE_640x480_YUV411, "640x480_YUV411"));
      map.insert(std::make_pair(DC1394_VIDEO_MODE_640x480_YUV422, "640x480_YUV422"));
      map.insert(std::make_pair(DC1394_VIDEO_MODE_640x480_RGB8, "640x480_RGB8"));
      map.insert(std::make_pair(DC1394_VIDEO_MODE_640x480_MONO8, "640x480_MONO8"));
      map.insert(std::make_pair(DC1394_VIDEO_MODE_640x480_MONO16, "640x480_MONO16"));
      map.insert(std::make_pair(DC1394_VIDEO_MODE_800x600_YUV422, "800x600_YUV422"));
      map.insert(std::make_pair(DC1394_VIDEO_MODE_800x600_RGB8, "800x600_RGB8"));
      map.insert(std::make_pair(DC1394_VIDEO_MODE_800x600_MONO8, "800x600_MONO8"));
      map.insert(std::make_pair(DC1394_VIDEO_MODE_800x600_MONO16, "800x600_MONO16"));
      map.insert(std::make_pair(DC1394_VIDEO_MODE_1024x768_YUV422, "1024x768_YUV422"));
      map.insert(std::make_pair(DC1394_VIDEO_MODE_1024x768_RGB8, "1024x768_RGB8"));
      map.insert(std::make_pair(DC1394_VIDEO_MODE_1024x768_MONO8, "1024x768_MONO8"));
      map.insert(std::make_pair(DC1394_VIDEO_MODE_1024x768_MONO16, "1024x768_MONO16"));
      map.insert(std::make_pair(DC1394_VIDEO_MODE_1280x960_YUV422, "1280x960_YUV422"));
      map.insert(std::make_pair(DC1394_VIDEO_MODE_1280x960_RGB8, "1280x960_RGB8"));
      map.insert(std::make_pair(DC1394_VIDEO_MODE_1280x960_MONO8, "1280x960_MONO8"));
      map.insert(std::make_pair(DC1394_VIDEO_MODE_1280x960_MONO16, "1280x960_MONO16"));
      map.insert(std::make_pair(DC1394_VIDEO_MODE_1600x1200_YUV422, "1600x1200_YUV422"));
      map.insert(std::make_pair(DC1394_VIDEO_MODE_1600x1200_RGB8, "1600x1200_RGB8"));
      map.insert(std::make_pair(DC1394_VIDEO_MODE_1600x1200_MONO8, "1600x1200_MONO8"));
      map.insert(std::make_pair(DC1394_VIDEO_MODE_1600x1200_MONO16, "1600x1200_MONO16"));
      map.insert(std::make_pair(DC1394_VIDEO_MODE_FORMAT7_0, "Format_7_0"));
      map.insert(std::make_pair(DC1394_VIDEO_MODE_FORMAT7_1, "Format_7_1"));
      map.insert(std::make_pair(DC1394_VIDEO_MODE_FORMAT7_2, "Format_7_2"));
      map.insert(std::make_pair(DC1394_VIDEO_MODE_FORMAT7_3, "Format_7_3"));
      map.insert(std::make_pair(DC1394_VIDEO_MODE_FORMAT7_4, "Format_7_4"));
      map.insert(std::make_pair(DC1394_VIDEO_MODE_FORMAT7_5, "Format_7_5"));
      map.insert(std::make_pair(DC1394_VIDEO_MODE_FORMAT7_6, "Format_7_6"));
      map.insert(std::make_pair(DC1394_VIDEO_MODE_FORMAT7_7, "Format_7_7"));
      initialized = true;
   }
   return map;
}


string Cdc1394::StringForVideoMode(dc1394video_mode_t mode)
{
   const map<dc1394video_mode_t, string>& videoModeMap(MakeVideoModeMap());
   map<dc1394video_mode_t, string>::const_iterator it = videoModeMap.find(mode),
      end = videoModeMap.end();
   if (it != end) {
      return it->second;
   }
   return "";
}


dc1394video_mode_t Cdc1394::VideoModeForString(const string& str)
{
   const map<dc1394video_mode_t, string>& videoModeMap(MakeVideoModeMap());
   for (std::map<dc1394video_mode_t, std::string>::const_iterator it = videoModeMap.begin(),
         end = videoModeMap.end();
         it != end; ++it) {
      if (str == it->second) {
         return it->first;
      }
   }
   return (dc1394video_mode_t)(DC1394_VIDEO_MODE_MAX + 1);
}


const map<dc1394framerate_t, string>& Cdc1394::MakeFramerateMap()
{
   static bool initialized = false;
   static map<dc1394framerate_t, string> map;
   if (!initialized) {
      map.insert(std::make_pair(DC1394_FRAMERATE_1_875, "  1.88 fps"));
      map.insert(std::make_pair(DC1394_FRAMERATE_3_75, "  3.75 fps"));
      map.insert(std::make_pair(DC1394_FRAMERATE_7_5, "  7.5 fps"));
      map.insert(std::make_pair(DC1394_FRAMERATE_15, " 15 fps"));
      map.insert(std::make_pair(DC1394_FRAMERATE_30, " 30 fps"));
      map.insert(std::make_pair(DC1394_FRAMERATE_60, " 60 fps"));
      map.insert(std::make_pair(DC1394_FRAMERATE_120, "120 fps"));
      map.insert(std::make_pair(DC1394_FRAMERATE_240, "240 fps"));
      initialized = true;
   }
   return map;
}


string Cdc1394::StringForFramerate(dc1394framerate_t framerate)
{
   const map<dc1394framerate_t, string>& framerateMap(MakeFramerateMap());
   map<dc1394framerate_t, string>::const_iterator it = framerateMap.find(framerate),
      end = framerateMap.end();
   if (it != end) {
      return it->second;
   }
   return "";
}


dc1394framerate_t Cdc1394::FramerateForString(const string& str)
{
   const map<dc1394framerate_t, string>& framerateMap(MakeFramerateMap());
   for (map<dc1394framerate_t, string>::const_iterator it = framerateMap.begin(),
         end = framerateMap.end();
         it != end; ++it) {
      if (str == it->second) {
         return it->first;
      }
   }
   return (dc1394framerate_t)(DC1394_FRAMERATE_MAX + 1);
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
      CHECK_DC1394_ERROR(dc1394_capture_enqueue(camera_, frame_), DEVICE_ERR,
            "Failed to enqueue frame buffer");

   logMsg_.str("");
   logMsg_ << "Started sequence acquisition: " << numImages << " at " << interval_ms << " ms" << endl;
   LogMessage(logMsg_.str().c_str());

   imageCounter_ = 0;
   sequenceLength_ = numImages;
   stopOnOverflow_ = stopOnOverflow;

   double actualIntervalMs = max(GetExposure(), interval_ms);
   acqThread_->SetInterval(actualIntervalMs);
   SetProperty(MM::g_Keyword_ActualInterval_ms, CDeviceUtils::ConvertToString(actualIntervalMs));

   CHECK_MM_ERROR(StopCapture());

   dc1394error_t err = dc1394_video_set_multi_shot(camera_, numImages, DC1394_ON);
   LOG_DC1394_ERROR(err, "Failed to start multi-shot");
   if (err == DC1394_SUCCESS) {
      multi_shot_ = true;
   }

   CHECK_MM_ERROR(StartCapture());

   CHECK_MM_ERROR(GetCoreCallback()->PrepareForAcq(this));

   acqThread_->SetLength(numImages);

   // TODO: check trigger mode, etc..

   // emtpy the buffer to ensure we'll get fresh images
   bool endFound = false;
   MM::MMTime startTime = GetCurrentMMTime();
   while (!endFound && !Timeout(startTime) ) {
      dc1394error_t err = dc1394_capture_dequeue(camera_,DC1394_CAPTURE_POLICY_POLL, &frame_);
      if (frame_ && err == DC1394_SUCCESS) {
         CHECK_DC1394_ERROR(dc1394_capture_enqueue(camera_, frame_), DEVICE_ERR,
               "Failed to enqueue frame buffer");
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
      CHECK_DC1394_ERROR(dc1394_capture_enqueue(camera_, frame_), DEVICE_ERR,
            "Failed to enqueue frame buffer");

   logMsg_.str("");
   logMsg_ << "Starting continuous sequence acquisition: at " << interval_ms << " ms" << endl;
   LogMessage(logMsg_.str().c_str());

   imageCounter_ = 0;
   stopOnOverflow_ = false;
   sequenceLength_ = LONG_MAX;

   double actualIntervalMs = max(GetExposure(), interval_ms);
   acqThread_->SetInterval(actualIntervalMs);
   SetProperty(MM::g_Keyword_ActualInterval_ms, CDeviceUtils::ConvertToString(actualIntervalMs));

   CHECK_MM_ERROR(StopCapture());
   CHECK_MM_ERROR(StartCapture());

   CHECK_MM_ERROR(GetCoreCallback()->PrepareForAcq(this));

   acqThread_->SetLength(sequenceLength_);

   // TODO: check trigger mode, etc..

   // emtpy the buffer to ensure we'll get fresh images
   bool endFound = false;
   MM::MMTime startTime = GetCurrentMMTime();
   while (!endFound && !Timeout(startTime) ) {
      dc1394error_t err = dc1394_capture_dequeue(camera_,DC1394_CAPTURE_POLICY_POLL, &frame_);
      if (frame_ && err == DC1394_SUCCESS) {
         dc1394_capture_enqueue(camera_, frame_);
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
   acqThread_->Stop();
   acquiring_ = false;

   if (multi_shot_) {
      CHECK_DC1394_ERROR(dc1394_video_set_multi_shot(camera_, 0, DC1394_OFF), DEVICE_ERR,
            "Failed to stop multi-shot");
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
   imageCounter_++;
   // TODO: call core to finish image snap

   // Fetch current frame
   // TODO: write a deinterlace in place routine
   // to avoid unnecessary copying
   //    uint8_t* pixBuffer = const_cast<unsigned char*> (img_.GetPixels());
   //  // GJ: Deinterlace image if required
   //    if (avtInterlaced) avtDeinterlaceMono8 (pixBuffer, (uint8_t*) src, width_, height_);
   // else memcpy (pixBuffer, src, GetImageBufferSize());

   // Copy to img_ ?
   
   // process image

   int numChars = GetImageWidth() * GetImageHeight() * GetImageBytesPerPixel() ;
   unsigned char* buf = (unsigned char *)malloc(numChars);
   ProcessImage(myframe, buf);

   // insert image into the circular MMCore buffer
   int ret =  GetCoreCallback()->InsertImage(this, buf, width_, height_, GetBytesPerPixel());

   if (!stopOnOverflow_ && ret == DEVICE_BUFFER_OVERFLOW) {
      // do not stop on overflow - just reset the buffer                     
      GetCoreCallback()->ClearImageBuffer(this);                             
      ret =  GetCoreCallback()->InsertImage(this, buf, width_, height_, GetBytesPerPixel());
   }

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
      err = dc1394_capture_dequeue(camera_->camera_, DC1394_CAPTURE_POLICY_WAIT, &myframe);
      LOG_DC1394_ERROR_EX(camera_->LogMessage, err, "Failed to dequeue frame buffer (POLICY_WAIT)");
      if (err != DC1394_SUCCESS) {
         camera_->StopSequenceAcquisition();
         return err; 
      } else {
         std::ostringstream logMsg;
         logMsg << "Dequeued image: " << imageCounter <<
         " with timestamp: " <<myframe->timestamp << 
         " ring buffer pos: "<<myframe->id <<
         " frames_behind: "<<myframe->frames_behind<<endl ;
         camera_->LogMessage(logMsg.str().c_str(), true);
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

      err = dc1394_capture_enqueue(camera_->camera_, myframe);
      LOG_DC1394_ERROR_EX(camera_->LogMessage, err, "Failed to enqueue frame buffer");
      if (err != DC1394_SUCCESS)
      {
         camera_->StopSequenceAcquisition();
         return err; 
      } 
      imageCounter++;
   } while (!stop_ && imageCounter < numImages_);

   if (stop_)
   {
      return 0;
   }

   camera_->StopSequenceAcquisition();
   return 0;
}

void AcqSequenceThread::Start() {
   stop_ = false;
   activate(); 
} 
