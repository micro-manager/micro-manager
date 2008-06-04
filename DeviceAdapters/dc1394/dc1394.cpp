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

#ifdef WIN32
   #pragma warning(disable : 4996) // disable warning for deperecated CRT functions on Windows 
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
#include "dc1394.h"
#include <dc1394/control.h>
#include <dc1394/utils.h>
#include <dc1394/conversions.h>
#include <dc1394/vendor/avt.h>
#include <string>
#include <sstream>
#include <iomanip>
#include <math.h>


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
   AddAvailableDeviceName(g_DeviceName, "Firewire cameras (IIDC compatible)");
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
   m_bInitialized(false),
   m_bBusy(false),
   snapInProgress_(false),
   frameRatePropDefined_(false),
   dmaBufferSize(16),
   triedCaptureCount(0),
   integrateFrameNumber(1),
   maxNrIntegration(1),
   lnBin_(1)
{
   SetErrorText(ERR_CAMERA_NOT_FOUND, "Did not find a IIDC firewire camera");
   SetErrorText(ERR_SET_CAPTURE_FAILED, "Failed to set capture");
   SetErrorText(ERR_TRANSMISSION_FAILED, "Problem starting transmission");
   SetErrorText(ERR_MODE_LIST_NOT_FOUND, "Did not find cameras mode list");
   SetErrorText(ERR_ROI_NOT_SUPPORTED, "ROIs not supported with this camera");
   SetErrorText(ERR_MODE_NOT_AVAILABLE, "Requested mode not available on this camera");
   SetErrorText(ERR_INITIALIZATION_FAILED, "Error Initializing the camera.  Unplug and replug the camera and restart this application");

   InitializeDefaultErrorMessages();

   // setup map to hold data on video modes:
   this->setVideoModeMap();
   this->setFrameRateMap();
   
   // Name
   int nRet = CreateProperty(MM::g_Keyword_Name, g_DeviceName, MM::String, true);
   assert(nRet == DEVICE_OK);

   // Description
   nRet = CreateProperty(MM::g_Keyword_Description, "Firewire camera (IIDC) dc1394 adapter", MM::String, true);
   assert(nRet == DEVICE_OK);
   
   // GJ set these to false for now
   avtInterlaced=false;
   
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
      std::map<dc1394video_mode_t, std::string>::iterator it = videoModeMap.begin();
      bool found = false;
      while ((it != videoModeMap.end()) && !found) 
      {
          if (it->second == modeString) {
             mode = it->first;
             found = true;
          }
          ++it;
      }
      if (found) 
      {
         LogMessage("Now changing mode...\n", true);
         dc1394_capture_stop(camera);
         
         // stop transmission and make sure it stopped
         if (StopTransmission() != DEVICE_OK)
            LogMessage ("Error stopping transmission\n");

         // get the new color coding mode:
         dc1394_get_color_coding_from_video_mode(camera, mode, &colorCoding);

         // Get the new bitdepth
         err = dc1394_video_get_data_depth(camera, &depth);
         if (err != DC1394_SUCCESS)
            LogMessage ("Error establishing bit-depth\n");
         maxNrIntegration = pow(2,(16-depth));
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
      pProp->Set(videoModeMap[mode].c_str());
   }

   return DEVICE_OK;
}


int Cdc1394::OnBrightness(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   return OnFeature(pProp, eAct, brightness, brightnessMin, brightnessMax, DC1394_FEATURE_BRIGHTNESS);
}


int Cdc1394::OnFrameRate(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      string frameString;
      pProp->Get(frameString);
      // find the key to the frameRate that was just set:
      std::map<dc1394framerate_t, std::string>::iterator it = frameRateMap.begin();
      bool found = false;
      while ((it != frameRateMap.end()) && !found)
      {
          if (it->second == frameString) {
             framerate = it->first;
             found = true;
          }  
          ++it;                                                              
      }                                                                      
      if (found)                                                             
      {                                                                      
         // Transmission needs to be stopped before changing framerates
         dc1394_capture_stop(camera);
         
         // stop transmission and make sure it stopped
         if (StopTransmission() != DEVICE_OK)
            LogMessage ("Error stopping transmission\n");

         // get the new color coding mode:
         dc1394_get_color_coding_from_video_mode(camera, mode, &colorCoding);

         // Get the new bitdepth
         err = dc1394_video_get_data_depth(camera, &depth);
         if (err != DC1394_SUCCESS)
            LogMessage ("Error establishing bit-depth\n");
         maxNrIntegration = pow(2, (16 - depth));
         GetBytesPerPixel ();

         // Restart capture
         return ResizeImageBuffer();
      }                                                                      
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set(frameRateMap[framerate].c_str());
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

   if(!absoluteShutterControl) return DEVICE_OK;
   
   if (eAct == MM::AfterSet)
   {
      // Get the new exposure time (in ms)
      pProp->Get(exposure_ms);
      // Send the new exposure setting to the camera if we have absolute control
      if(camera->vendor_id==AVT_VENDOR_ID){
         // AVT has vendor specific code for an extended shutter
         // this accepts shutter times in µs which makes it easier to work with
         // OK, set using extended register, MM exposure is in ms so *1000 -> us
         exposure_us = (uint32_t) 1000.0*exposure_ms;
         err=dc1394_avt_set_extented_shutter(camera,exposure_us);
         if(err!=DC1394_SUCCESS) return DEVICE_ERR;
      } else {
         // set using ordinary absolute shutter dc1394 function
         // this expects a float in seconds
         float minAbsShutter, maxAbsShutter;
         err = dc1394_feature_get_absolute_boundaries(camera, DC1394_FEATURE_SHUTTER, &minAbsShutter, &maxAbsShutter);
         float exposure_s=0.001f*(float)exposure_ms;
         if(minAbsShutter>exposure_s || exposure_s>maxAbsShutter) return DEVICE_ERR;
         
         err=dc1394_feature_set_absolute_control(camera,DC1394_FEATURE_SHUTTER,DC1394_ON);
         if(err!=DC1394_SUCCESS) return DEVICE_ERR;

         err=dc1394_feature_set_absolute_value(camera,DC1394_FEATURE_SHUTTER,exposure_s);
         if(err!=DC1394_SUCCESS) return DEVICE_ERR;		 
      }
   }
   else if (eAct == MM::BeforeGet)
   {  
      if(camera->vendor_id==AVT_VENDOR_ID){
         // AVT has vendor specific code for an extended shutter
         // this accepts shutter times in µs which makes it easier to work with
         err=dc1394_avt_get_extented_shutter(camera,&exposure_us);
         if(err!=DC1394_SUCCESS) return DEVICE_ERR;
         // convert it to milliseconds
         exposure_ms = 0.001 * (double) exposure_us;         
      } else {
          // set using ordinary absolute shutter dc1394 function
          // this expects a float in seconds
         float exposure_s;
         err=dc1394_feature_get_absolute_value(camera,DC1394_FEATURE_SHUTTER,&exposure_s);
         if(err!=DC1394_SUCCESS) return DEVICE_ERR;
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
         integrateFrameNumber = maxNrIntegration;
      else if (tmp < 1)
         integrateFrameNumber = 1;
      else if ( (tmp >= 1) && (tmp <= maxNrIntegration) )
         integrateFrameNumber = tmp;
      GetBytesPerPixel();
      img_.Resize(width, height, bytesPerPixel);
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set((long)integrateFrameNumber);
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
   */
   return DEVICE_OK;
}

// ScanMode
int Cdc1394::OnScanMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   return DEVICE_OK;
}

// ReadoutTime
int Cdc1394::OnReadoutTime(MM::PropertyBase* pProp, MM::ActionType eAct)
{
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
      err = dc1394_feature_set_value(camera, feature, value);
      logMsg_.clear();
      logMsg_ << "Settings feature " << feature << " to " << value <<  " result: " << err;
      LogMessage(logMsg_.str().c_str(), true);
   }
   else if (eAct == MM::BeforeGet)
   {
      err = dc1394_feature_get_value(camera, feature, &value);
      logMsg_.clear();
      logMsg_ << "Getting feature " << feature << ".  It is now " << value << " err: " << err;
      LogMessage (logMsg_.str().c_str(), true);
      tmp = (long)  value;
      pProp->Set(tmp);
   }
   return DEVICE_OK;
}

//
// Gain
int Cdc1394::OnGain(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  return OnFeature(pProp, eAct, gain, gainMin, gainMax, DC1394_FEATURE_GAIN);
}

// Gamma
int Cdc1394::OnGamma(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   return DEVICE_OK;
}

// Shutter
int Cdc1394::OnShutter(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if((eAct == MM::AfterSet) && absoluteShutterControl){
      // Need to turn off absolute mode so that we can set it using integer shutter values
      dc1394_feature_set_absolute_control(camera, DC1394_FEATURE_SHUTTER, DC1394_OFF);
   }
   return OnFeature(pProp, eAct, shutter, shutterMin, shutterMax, DC1394_FEATURE_SHUTTER);
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
// Description     : Initialize the camera
// Return type     : bool 

int Cdc1394::Initialize()
{
   // setup the camera
   // ----------------
   int nRet = GetCamera();
   if (nRet != DEVICE_OK)
      return nRet;

   // We have  a handle to the camera now:
   m_bInitialized = true;

   // Figure out what this camera can do
   // get supported modes
   if (dc1394_video_get_supported_modes(camera, &modes)!=DC1394_SUCCESS)
      return ERR_MODE_LIST_NOT_FOUND;                         
   for (unsigned int i = 0; i < modes.num; i++)
      //if (modes.modes[i] != 0)
         printf("Mode found: %d\n", modes.modes[i]);

   // Camera video modes, default to the first mode found
   CPropertyAction *pAct = new CPropertyAction (this, &Cdc1394::OnMode);
   mode = modes.modes[0];
   
   logMsg_.clear();
   logMsg_ << "Camera vendor/model is: " << camera->vendor << "/" << camera->model;
   LogMessage (logMsg_.str().c_str(), true);

   logMsg_.clear();
   logMsg_ << "Camera vendor id is: " << camera->vendor_id << ", " <<camera->model_id;
   LogMessage (logMsg_.str().c_str(), true);

   // TODO once we can set the interlacing up externally
   // check if appropriate property is set
   if(0) {
	   
   } else if(!strncmp("Guppy F025",camera->model,10) ||
			 !strncmp("Guppy F029",camera->model,10) || 
			 !strncmp("Guppy F038",camera->model,10) ||
			 !strncmp("Guppy F044",camera->model,10) ) {
	   avtInterlaced=true;
      logMsg_.clear();
	   logMsg_ << "Camera is Guppy interlaced series, setting interlace = true";
	   LogMessage (logMsg_.str().c_str(), true);
   }
   else avtInterlaced=false;
   
   nRet = CreateProperty(g_Keyword_Modes, videoModeMap[mode].c_str(), MM::String, false, pAct);
   printf ("nRet: %d\n", nRet);
   assert(nRet == DEVICE_OK);
   printf ("Still here\n");
   vector<string> modeValues;
   for (unsigned int i=0; i<modes.num; i++) {
      string tmp = videoModeMap[ modes.modes[i] ];
      if (tmp != "") 
         modeValues.push_back(tmp);
   }   
   nRet = SetAllowedValues(g_Keyword_Modes, modeValues);
   if (nRet != DEVICE_OK)
      return nRet;

   // get the current color coding mode:
   dc1394_get_color_coding_from_video_mode(camera, mode, &colorCoding);

   // FrameRate, this is packaged in a function since it will need to be reset whenever the video Mode changes
   nRet = SetUpFrameRates();
   if (nRet != DEVICE_OK)
      return nRet;

   // Get the new bitdepth
   err = dc1394_video_get_data_depth(camera, &depth);
   maxNrIntegration = pow(2, (16 - depth));
   GetBytesPerPixel();

   // This should be moved to the bottom.  Check now if the camera is actually functional
   nRet = ResizeImageBuffer();
   if (nRet != DEVICE_OK)
      return nRet;

   // Frame integration
   if (depth == 8)
   {
      pAct = new CPropertyAction (this, &Cdc1394::OnIntegration);
      nRet = CreateProperty("Integration", "1", MM::Integer, false, pAct);
      assert(nRet == DEVICE_OK);
   }

   
   // exposure, not implemented (yet)
   pAct = new CPropertyAction (this, &Cdc1394::OnExposure);
   nRet = CreateProperty(MM::g_Keyword_Exposure, "10.0", MM::Float, false, pAct);
   assert(nRet == DEVICE_OK);

   // binning, not implemented, would need software binning
   pAct = new CPropertyAction (this, &Cdc1394::OnBinning);
   nRet = CreateProperty(MM::g_Keyword_Binning, "1", MM::Integer, false, pAct);
   assert(nRet == DEVICE_OK);
   vector<string> binValues;
   binValues.push_back("1");
   nRet = SetAllowedValues(MM::g_Keyword_Binning, binValues);
   if (nRet != DEVICE_OK)
      return nRet;

   // pixel type, defaults to 8-bit for now
   pAct = new CPropertyAction (this, &Cdc1394::OnPixelType);
   if (depth <= 8)
      nRet = CreateProperty(MM::g_Keyword_PixelType, g_PixelType_8bit, MM::String, false, pAct);
   else
      nRet = CreateProperty(MM::g_Keyword_PixelType, g_PixelType_16bit, MM::String, false, pAct);
   assert(nRet == DEVICE_OK);
   vector<string> pixelTypeValues;
      pixelTypeValues.push_back(g_PixelType_8bit);
   nRet = SetAllowedValues(MM::g_Keyword_PixelType, pixelTypeValues);
   if (nRet != DEVICE_OK)
      return nRet;

   // camera gain
   /*
   pAct = new CPropertyAction (this, &Cdc1394::OnGain);
   nRet = CreateProperty(MM::g_Keyword_Gain, "1", MM::Integer, false, pAct);
   assert(nRet == DEVICE_OK);
   */

   // CameraName
   char	CameraName[(2 * 256) + 4] = "Unrecognized";
   strcpy (CameraName, camera->vendor);
   strcat (CameraName, ", ");
   strcat (CameraName, camera->model);
   nRet = CreateProperty("Vendor Info", CameraName, MM::String, true);
   assert(nRet == DEVICE_OK);
		      
   // Other features, ask what the camera has to offer
   if (dc1394_feature_get_all(camera,&features) !=DC1394_SUCCESS) 
         return ERR_GET_CAMERA_FEATURE_SET_FAILED;                         
   int j = 0;
   for (int i= DC1394_FEATURE_MIN; i <= DC1394_FEATURE_MAX; i++, j++)  
   {
       featureInfo = features.feature[j];
       if (featureInfo.available) {
          const char *featureLabel = dc1394_feature_get_string(featureInfo.id);
          if (strcmp(featureLabel, "Brightness") ==0) 
          {
             // TODO: offer option to switch between auto, manual and one-push modes
             SetManual(DC1394_FEATURE_BRIGHTNESS);

             // TODO: Check that this feature is read-out capable
             err = dc1394_feature_get_value(camera, DC1394_FEATURE_BRIGHTNESS, &brightness);
             logMsg_.clear();
             logMsg_ << "Brightness " <<  brightness;
             LogMessage (logMsg_.str().c_str(), false);
             err = dc1394_feature_get_boundaries(camera, DC1394_FEATURE_BRIGHTNESS, &brightnessMin, &brightnessMax);
             logMsg_.clear();
             logMsg_ << "Brightness Min: " << brightnessMin  << " Max: " << brightnessMax;
             LogMessage (logMsg_.str().c_str(), false);

             char tmp[10];
             pAct = new CPropertyAction (this, &Cdc1394::OnBrightness);
             sprintf(tmp,"%d",brightness);
             nRet = CreateProperty("Brightness", tmp, MM::Integer, false, pAct);
             assert(nRet == DEVICE_OK);
             nRet = SetPropertyLimits("Brightness", brightnessMin, brightnessMax);
             assert(nRet == DEVICE_OK);
             
          } 
          else if (strcmp(featureLabel, "Gain") == 0) 
          {
             // TODO: offer option to switch between auto, manual and one-push modes
             SetManual(DC1394_FEATURE_GAIN);

             err = dc1394_feature_get_value(camera, DC1394_FEATURE_GAIN, &gain);
             logMsg_.clear();
             logMsg_ << "Gain: "<<  gain;
             LogMessage (logMsg_.str().c_str(), false);
             err = dc1394_feature_get_boundaries(camera, DC1394_FEATURE_GAIN, &gainMin, &gainMax);
             logMsg_.clear();
             logMsg_ << "Gain Min: " << gainMin << " Max: " << gainMax;
             LogMessage (logMsg_.str().c_str(), false);
             char tmp[10];
             pAct = new CPropertyAction (this, &Cdc1394::OnGain);
             sprintf(tmp,"%d",gain);
             nRet = CreateProperty(MM::g_Keyword_Gain, tmp, MM::Integer, false, pAct);
             assert(nRet == DEVICE_OK);
             nRet = SetPropertyLimits(MM::g_Keyword_Gain, gainMin, gainMax);
             assert(nRet == DEVICE_OK);
             
          }
          else if (strcmp(featureLabel, "Shutter") == 0) 
          {
             // TODO: offer option to switch between auto, manual and one-push modes
             SetManual(DC1394_FEATURE_SHUTTER);

             // TODO: when shutter has absolute control, couple it to exposure time
             err = dc1394_feature_get_value(camera, DC1394_FEATURE_SHUTTER, &shutter);
             logMsg_.clear();
             logMsg_ << "Shutter: " << shutter;
             LogMessage (logMsg_.str().c_str(), false);
             err = dc1394_feature_get_boundaries(camera, DC1394_FEATURE_SHUTTER, &shutterMin, &shutterMax);
             logMsg_.clear();
             logMsg_ << "Shutter Min: " << shutterMin << " Max: " << shutterMax;
             LogMessage (logMsg_.str().c_str(), false);
             char tmp[10];
             pAct = new CPropertyAction (this, &Cdc1394::OnShutter);
             sprintf(tmp,"%d",shutter);
             nRet = CreateProperty("Shutter", tmp, MM::Integer, false, pAct);
             assert(nRet == DEVICE_OK);
             nRet = SetPropertyLimits("Shutter", shutterMin, shutterMax);
             assert(nRet == DEVICE_OK);
             
             // Check if shutter has absolute control
             dc1394bool_t absolute;
             absoluteShutterControl=false;
             err = dc1394_feature_has_absolute_control(camera, DC1394_FEATURE_SHUTTER, &absolute);
             if(absolute==DC1394_TRUE) absoluteShutterControl=true;

             if(!absoluteShutterControl && camera->vendor_id==AVT_VENDOR_ID){
                logMsg_.clear();
                logMsg_ << "Checking AVT absolute shutter\n";
                LogMessage (logMsg_.str().c_str(), false);
                // for AVT cameras, check if we have access to the extended shutter mode
                uint32_t timebase_id;
                err=dc1394_avt_get_extented_shutter(camera,&timebase_id);
                if(err==DC1394_SUCCESS) absoluteShutterControl=true;
             }
             if(absoluteShutterControl){
                logMsg_.clear();
                logMsg_ << "Absolute shutter\n";
                LogMessage (logMsg_.str().c_str(), false);                
             }
          }
          else if (strcmp(featureLabel, "Exposure") == 0) 
          {
             // TODO: offer option to switch between auto, manual and one-push modes
             err = dc1394_feature_get_value(camera, DC1394_FEATURE_EXPOSURE, &exposure);
             err = dc1394_feature_get_boundaries(camera, DC1394_FEATURE_EXPOSURE, &exposureMin, &exposureMax);
          }
       }
    }


   // synchronize all properties
   // --------------------------
   nRet = UpdateStatus();
   if (nRet != DEVICE_OK)
      return nRet;

   // setup the buffer
   // ----------------
   /*
   nRet = ResizeImageBuffer();
   if (nRet != DEVICE_OK)
      return nRet;
   */

   // We seem to need this on the Mac...
   SetProperty(MM::g_Keyword_Binning,"1");

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Function name   : Cdc1394::Shutdown
// Description     : Deactivate the camera, reverse the initialization process
// Return type     : bool 

int Cdc1394::Shutdown()
{
   if (m_bInitialized)
   {
      // TODO: Added error handling
      err = dc1394_capture_stop(camera);
      if (err != DC1394_SUCCESS)
         printf ("Failed to stop capture\n");
      err = dc1394_video_set_transmission(camera, DC1394_OFF);
      if (err != DC1394_SUCCESS)
         printf ("Failed to stop tranmission\n");
      dc1394_camera_free(camera);

      printf("Shutdown camera\n");

      m_bInitialized = false;
   }
   return DEVICE_OK;
}

int Cdc1394::GetCamera()
{
   dc1394_t * d;
   dc1394camera_list_t * list;

   // Find and initialize the camera
   d = dc1394_new();
   err = dc1394_camera_enumerate(d, &list); 
   if (err != DC1394_SUCCESS)
      return ERR_CAMERA_NOT_FOUND; 
   if (list->num == 0) 
      return ERR_CAMERA_NOT_FOUND;
   
   // TODO: work with multiple cameras (select by name??)
   // For now we'll take the first camera on the bus
   camera = dc1394_camera_new(d, list->ids[0].guid);
   if (!camera)
      return ERR_INITIALIZATION_FAILED;

   dc1394_camera_free_list (list);
   /*
   // free the other ones:
   for (int i=1; i<numCameras; i++)                                                 
      dc1394_camera_free(cameras[i]);                                          
   free(cameras);                                                             
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
   if (dc1394_capture_dequeue(camera,DC1394_CAPTURE_POLICY_WAIT, &frame)!=DC1394_SUCCESS)
      return ERR_CAPTURE_FAILED;

   // find the end of the capture buffer, so that we can get a fresh frame
   dc1394video_frame_t *frame2;
   bool endFound = false;
   int nrFrames = 0;
   while (!endFound) {
      err = dc1394_capture_dequeue(camera,DC1394_CAPTURE_POLICY_POLL, &frame2);
      if (frame2 && err==DC1394_SUCCESS)
      {
         dc1394_capture_enqueue(camera, frame);
         frame = frame2;
         nrFrames +=1;
      } else
      {
         endFound=true;
      }                                                                      
   }      
   // printf("%d Frames discarded\n", nrFrames);
   // If we went through the whole buffer, toss the image and ask for a fresh one
   // I don't know why it is 2, but that is what it is with the iSight and Sony I have here
   if (nrFrames == dmaBufferSize - 2) 
   {
      dc1394_capture_enqueue(camera, frame);
      dc1394_capture_dequeue(camera,DC1394_CAPTURE_POLICY_WAIT, &frame);
   }
   
   // Now process the frame according to the video mode used
   int numPixels = width * height;
   
   if (colorCoding==DC1394_COLOR_CODING_YUV411 || colorCoding==DC1394_COLOR_CODING_YUV422 || colorCoding==DC1394_COLOR_CODING_YUV444) 
   {
      uint8_t *rgb_image = (uint8_t *)malloc(3 * numPixels);
      dc1394_convert_to_RGB8((uint8_t *)frame->image,      
             rgb_image, width, height, DC1394_BYTE_ORDER_UYVY, colorCoding, 16);
      // we copied to rgb_image, so release the frame:
      dc1394_capture_enqueue(camera, frame);
      // no integration, straight forward stuff
      if (integrateFrameNumber == 1)
      {
         uint8_t* pixBuffer = const_cast<unsigned uint8_t*> (img_.GetPixels());
         rgb8ToMono8(pixBuffer, rgb_image, width, height);
      }
      // when integrating we are dealing with 16 bit images
      if (integrateFrameNumber > 1)
      {
         void* pixBuffer = const_cast<unsigned char*> (img_.GetPixels());
         // funny, we need to clear the memory since we'll add to it, not overwrite it
         memset(pixBuffer, 0, GetImageBufferSize());
         rgb8AddToMono16((uint16_t*) pixBuffer, rgb_image, width, height);
         // now repeat for the other frames:
         for (int frameNr=1; frameNr<integrateFrameNumber; frameNr++)
         {
            if (dc1394_capture_dequeue(camera,DC1394_CAPTURE_POLICY_WAIT, &frame)!=DC1394_SUCCESS)
                  return ERR_CAPTURE_FAILED;
            dc1394_convert_to_RGB8((uint8_t *)frame->image,      
                      rgb_image, width, height, DC1394_BYTE_ORDER_UYVY, colorCoding, 16);
            dc1394_capture_enqueue(camera, frame);
            rgb8AddToMono16((uint16_t*) pixBuffer, rgb_image, width, height);
         }
      }
      free(rgb_image);
   }

   else if (colorCoding==DC1394_COLOR_CODING_MONO8 || colorCoding==DC1394_COLOR_CODING_MONO16) 
   {
      if (integrateFrameNumber==1) 
      {
         void* src = (void *) frame->image;
         uint8_t* pixBuffer = const_cast<unsigned char*> (img_.GetPixels());
		 // GJ: Deinterlace image if required
         if (avtInterlaced) avtDeinterlaceMono8 (pixBuffer, (uint8_t*) src, width, height);			 
		 else memcpy (pixBuffer, src, GetImageBufferSize());
         dc1394_capture_enqueue(camera, frame);
      }
      else if (integrateFrameNumber > 1)
      {
         void* src = (void *) frame->image;
         uint8_t* pixBuffer = const_cast<unsigned char*> (img_.GetPixels());
         memset(pixBuffer, 0, GetImageBufferSize());
         mono8AddToMono16((uint16_t*) pixBuffer, (uint8_t*) src, width, height);
         dc1394_capture_enqueue(camera, frame);
         // now repeat for the other frames:
         for (int frameNr=1; frameNr<integrateFrameNumber; frameNr++)
         {
            if (dc1394_capture_dequeue(camera,DC1394_CAPTURE_POLICY_WAIT, &frame)!=DC1394_SUCCESS)
                  return ERR_CAPTURE_FAILED;
            mono8AddToMono16((uint16_t*) pixBuffer, (uint8_t*) frame->image, width, height);
            dc1394_capture_enqueue(camera, frame);
         }
		 // GJ:  Finished integrating, so we can deinterlace the 16 bit result
		 // That does mean doing a pixel buffer shuffle unfortunately
		 // but prob better than deinterlacing every 8 bit image
		 // perhaps better still would be a mono8DeinterlacedAddToMono16Function
		 if (avtInterlaced) {
			 uint16_t *interlacedImaged = (uint16_t *) malloc(numPixels*sizeof(uint16_t));
			 memcpy(interlacedImaged, (uint16_t*) pixBuffer, GetImageBufferSize());
			 avtDeinterlaceMono16((uint16_t*) pixBuffer, interlacedImaged, width, height);
			 free(interlacedImaged);
		 }
	   }
   }
   else if (colorCoding==DC1394_COLOR_CODING_RGB8) 
   {
      uint8_t* src;
      src = (uint8_t *) frame->image;
      uint8_t* pixBuffer = const_cast<unsigned uint8_t*> (img_.GetPixels());
      rgb8ToMono8 (pixBuffer, src, width, height);
      dc1394_capture_enqueue(camera, frame);
   }
   
   return DEVICE_OK;
}

double Cdc1394::GetExposure() const
{
   char Buf[MM::MaxStrLength];
   Buf[0] = '\0';
   GetProperty(MM::g_Keyword_Exposure, Buf);
   return atof(Buf);
}

void Cdc1394::SetExposure(double dExp)
{
   if(absoluteShutterControl) 
      SetProperty(MM::g_Keyword_Exposure, CDeviceUtils::ConvertToString(dExp));
}

///////////////////////////////////////////////////////////////////////////////
// Function name   : char* Cdc1394::GetImageBuffer
// Description     : Returns the raw image buffer
// Return type     : const unsigned 

const unsigned char* Cdc1394::GetImageBuffer()
{
   // capture complete
   void* pixBuffer = const_cast<unsigned char*> (img_.GetPixels());
   return (unsigned char*)pixBuffer;
}

unsigned Cdc1394::GetBitDepth() const
{
   if (integrateFrameNumber==1)
	  return (unsigned) depth;
   else
   {
	  // calculated log2 of frameIntegrationNumber
	  unsigned r = 0; // r will be lg(v)
	  unsigned int v = integrateFrameNumber;
	  while (v >>= 1) // unroll for more speed...
		   r++;
	  return (unsigned) depth + r;
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
	if(mode>=DC1394_VIDEO_MODE_FORMAT7_MIN && mode<=DC1394_VIDEO_MODE_FORMAT7_MAX) {
		// First thing to do is stop transmission - otherwise we won't be
		// able to (re)set ROI.
		// TODO - can't figure out what we need to do to stop capture and
		// then restart appropriately
		// For example Shutdown() and Initialize() - which seems extreme -
		// leads to an assertion failure.
		err = dc1394_capture_stop(camera);
		  if (err != DC1394_SUCCESS)
			 LogMessage ("SetROI: Failed to stop capture\n");
		 StopTransmission();
		
       logMsg_.clear();
		 logMsg_ << "SetROI: Aout to set to  " << uX << "," << uY << "," << uXSize << "," << uYSize ;
		LogMessage (logMsg_.str().c_str(), true);
		
		dc1394error_t errval= dc1394_format7_set_roi(camera, mode, 
					(dc1394color_coding_t) DC1394_COLOR_CODING_MONO8 /*color_coding*/,
					(int) DC1394_USE_MAX_AVAIL /*bytes_per_packet*/, uX, uY, uXSize, uYSize);
		if (errval!=DC1394_SUCCESS) {
         logMsg_.clear();
			logMsg_ << "SetROI: libdc1394 error message " <<  errval;
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
	if(mode>=DC1394_VIDEO_MODE_FORMAT7_MIN && mode<=DC1394_VIDEO_MODE_FORMAT7_MAX) {
		uint32_t bytes_per_packet;
		dc1394color_coding_t color_coding;
		if(dc1394_format7_get_roi(camera, mode, &color_coding, &bytes_per_packet, &uX, &uY, &uXSize, &uYSize)!=DC1394_SUCCESS)
		return ERR_GET_F7_ROI_FAILED;
	}
	return DEVICE_OK;
}

int Cdc1394::ClearROI()
{
	if(mode>=DC1394_VIDEO_MODE_FORMAT7_MIN && mode<=DC1394_VIDEO_MODE_FORMAT7_MAX) {
		// Get the recommended ROI - ie the max size of the image in
		// current format 7 mode
		uint32_t width, height;
		if(dc1394_format7_get_max_image_size(camera, mode, &width, &height)!=DC1394_SUCCESS)
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
   // there are two buffers, 1: DMA buffer that the camera streams into (frame)
   // 2: buffer for exchange with MicroManager (img_)

   // set up the camera (TODO: implement iso speed control):
   dc1394_video_set_iso_speed(camera, DC1394_ISO_SPEED_400);
   err = dc1394_video_set_mode(camera,mode);
   if (err != DC1394_SUCCESS)
      return ERR_SET_MODE_FAILED;
   logMsg_.clear();
   logMsg_ << "Mode set to " <<  mode;
   LogMessage (logMsg_.str().c_str(), true);

   logMsg_.clear();
   // GJ: check whether the chosen mode is a format 7 mode
   if(mode>=DC1394_VIDEO_MODE_FORMAT7_MIN && mode<=DC1394_VIDEO_MODE_FORMAT7_MAX) {
	   logMsg_ << "Format_7: Skipping setting of framerate";
   } else {
	   // GJ bracket this off - doesn't make sense for format7
	   err = dc1394_video_set_framerate(camera,framerate);
	   if (err != DC1394_SUCCESS)
		  return ERR_SET_FRAMERATE_FAILED;
		  logMsg_ <<  "Framerate set to " << framerate;
   }
   LogMessage (logMsg_.str().c_str(), true);
   
   // Start the image capture
   if (dc1394_capture_setup(camera,dmaBufferSize,DC1394_CAPTURE_FLAGS_DEFAULT) != DC1394_SUCCESS) 
      return ERR_SET_CAPTURE_FAILED;

   // Set camera trigger mode
   // if( dc1394_external_trigger_set_mode(camera, DC1394_TRIGGER_MODE_0) != DC1394_SUCCESS)
    //  return ERR_SET_TRIGGER_MODE_FAILED;

   // Have the camera start sending data
   if (dc1394_video_set_transmission(camera, DC1394_ON) !=DC1394_SUCCESS) 
      return ERR_SET_TRANSMISSION_FAILED;

   // Sleep until the camera sent data
   dc1394switch_t status = DC1394_OFF;
   int i = 0;
   while( status == DC1394_OFF && i < 10 ) 
   {
     usleep(50000);
     if (dc1394_video_get_transmission(camera, &status)!=DC1394_SUCCESS)
        return ERR_GET_TRANSMISSION_FAILED;
     i++;
   }
   if (i == 10)
      return ERR_CAMERA_DOES_NOT_SEND_DATA;

   // Make sure we can get images
   bool captureSuccess = false;
   i = 0;
   while ( captureSuccess == false && i < 10)
   {
      usleep(250000);
      err = dc1394_capture_dequeue(camera, DC1394_CAPTURE_POLICY_POLL, &frame);
      if (frame && err==DC1394_SUCCESS)
      {
         captureSuccess = true;
         dc1394_capture_enqueue(camera, frame);
      }
      i++;
   }
   logMsg_.clear();
   logMsg_ << "tried " << i << " times";
   LogMessage (logMsg_.str().c_str(), true);
   if (i==10) {
      logMsg_.clear();
      logMsg_ << "PROBLEM!!!:: Failed to capture";
      LogMessage (logMsg_.str().c_str(), false);
      // Camera is in a bad state.  Try to rescue what we can:
      dc1394_camera_free(camera);
      m_bInitialized = false;
      // If/when we get here, try starting the camera again, probably hopeless
      int nRet = GetCamera();
      logMsg_.clear();
      logMsg_ <<"Tried restarting the camera, Result: " <<  nRet;
      LogMessage (logMsg_.str().c_str(), false);
      if (nRet == DEVICE_OK && triedCaptureCount <= 5)
      {
         triedCaptureCount++;
         return ResizeImageBuffer();
      } else
         return ERR_CAPTURE_FAILED;
   }

   // Get the image size from the camera:
   // GJ: nb this is clever and returns current ROI size for format 7 modes
   err = dc1394_get_image_size_from_video_mode(camera, mode, &width, &height);
   if (err != DC1394_SUCCESS)
      return ERR_GET_IMAGE_SIZE_FAILED;
   GetBitDepth();
   GetBytesPerPixel();
   // Set the internal buffer (for now 8 bit gray scale only)
   // TODO: implement other pixel sizes
   img_.Resize(width, height, bytesPerPixel);


   logMsg_.clear();
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
    err = dc1394_feature_is_switchable(camera, feature, &isSwitchable);
    if (isSwitchable)
       dc1394_feature_set_power(camera, feature, DC1394_ON);
    // dc1394bool_t hasManualMode = DC1394_FALSE;
    //hasManualMode = feature->
    //err = dc1394_feature_has_manual_mode(camera, feature, &hasManualMode);
    //if (hasManualMode)
    dc1394_feature_set_mode(camera, feature, DC1394_FEATURE_MODE_MANUAL);
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

int Cdc1394::StopTransmission()
{
	// GJ: first check if off
   dc1394switch_t status;
	if (dc1394_video_get_transmission(camera, &status)!=DC1394_SUCCESS) {
		LogMessage("Could not get ISO status");
	}
	// Camera already off
	if(status==DC1394_OFF) return DEVICE_OK;
	
	LogMessage("About to stop transmission");
	dc1394_video_set_transmission(camera, DC1394_OFF);
   // wait untill the camera stopped transmitting:
   int i = 0;
   while( status == DC1394_ON && i < 50 )
   {       
      usleep(50000);
      if (dc1394_video_get_transmission(camera, &status)!=DC1394_SUCCESS)
         return ERR_GET_TRANSMISSION_FAILED;
      i++;
   }
   logMsg_.clear();
   logMsg_ <<  "Waited for " << i << "cycles for transmission to stop"; 
   LogMessage (logMsg_.str().c_str(), true);
   if (i == 50)                                                
      return ERR_CAMERA_DOES_NOT_SEND_DATA;
   return DEVICE_OK;
}

void Cdc1394::GetBytesPerPixel()
{
   if (depth==8 && integrateFrameNumber==1)
      bytesPerPixel = 1;
   else if (depth==8 && integrateFrameNumber>1)
      bytesPerPixel = 2;
   else if (depth > 8 && depth <= 16)
      bytesPerPixel = 2;
   else
      printf ("Can not figure out Bytes per Pixel.  bitdepth=%u\n", depth);
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
	// dc1394error_t errval;
	if(mode>=DC1394_VIDEO_MODE_FORMAT7_MIN && mode<=DC1394_VIDEO_MODE_FORMAT7_MAX) {
		// GJ Format_7: no specific framerate so just bail out early
		// framerate = 0;
		// TODO - delete list of frame rates?
      logMsg_.clear();
		logMsg_ << "SetUpFrameRates: Returning early because mode is format 7 (no defined framerate)";
		LogMessage (logMsg_.str().c_str(), true);
		// What should I return now?
		return DEVICE_OK;
	} else if (dc1394_video_get_supported_framerates(camera,mode,&framerates)!=DC1394_SUCCESS) {
		return ERR_GET_FRAMERATES_FAILED;
	}
		
	// Default to the first framerate belonging to this mode
	if (!InArray(framerates.framerates, framerates.num, framerate)) 
	{
	   framerate = framerates.framerates[0];
	}
   // Create the MicroManager Property FrameRates, only when it was not yet defined
   if (!frameRatePropDefined_)
   {
      CPropertyAction *pAct = new CPropertyAction (this, &Cdc1394::OnFrameRate);
      int nRet = CreateProperty(g_Keyword_FrameRates, frameRateMap[framerates.framerates[0]].c_str(), MM::String, false, pAct);
      assert(nRet == DEVICE_OK);
      frameRatePropDefined_ = true;
   }
   
   // set allowed values, this will delete a pre-existing list of allowed values
   vector<string> rateValues;
   for (unsigned int i=0; i<framerates.num; i++) {
      string tmp = frameRateMap[ framerates.framerates[i] ];
      // printf ("%s\n", tmp.c_str());
      if (tmp != "")
         rateValues.push_back(tmp);
   }   
   int nRet = SetAllowedValues(g_Keyword_FrameRates, rateValues);
   logMsg_.clear();
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
    err = dc1394_feature_get_value(camera, feature, &value);
    printf("%s: %d\n", label, shutter);
    err = dc1394_feature_get_boundaries(camera, feature, &valueMin, &valueMax);
    printf("%s Min: %d, Max: %d\n", label, valueMin, valueMax);
    char* tmp;
    CPropertyAction *pAct = new CPropertyAction (this, fpt);
    sprintf(tmp,"%d",value);
    int nRet = CreateProperty(label, tmp, MM::Integer, false, pAct);
    assert(nRet == DEVICE_OK);
}
*/



void Cdc1394::setVideoModeMap()
{
   videoModeMap.insert(videoModeMapType (DC1394_VIDEO_MODE_160x120_YUV444,"160x120_YUV444"));
   videoModeMap.insert(videoModeMapType (DC1394_VIDEO_MODE_320x240_YUV422,"320x240_YUV422"));
   videoModeMap.insert(videoModeMapType (DC1394_VIDEO_MODE_640x480_YUV411,"640x480_YUV411"));
   videoModeMap.insert(videoModeMapType (DC1394_VIDEO_MODE_640x480_YUV422,"640x480_YUV422"));
   videoModeMap.insert(videoModeMapType (DC1394_VIDEO_MODE_640x480_RGB8,"640x480_RGB8"));
   videoModeMap.insert(videoModeMapType (DC1394_VIDEO_MODE_640x480_MONO8,"640x480_MONO8"));
   videoModeMap.insert(videoModeMapType (DC1394_VIDEO_MODE_640x480_MONO16,"640x480_MONO16"));
   videoModeMap.insert(videoModeMapType (DC1394_VIDEO_MODE_800x600_YUV422,"800x600_YUV422"));
   videoModeMap.insert(videoModeMapType (DC1394_VIDEO_MODE_800x600_RGB8,"800x600_RGB8"));
   videoModeMap.insert(videoModeMapType (DC1394_VIDEO_MODE_800x600_MONO8,"800x600_MONO8"));
   videoModeMap.insert(videoModeMapType (DC1394_VIDEO_MODE_800x600_MONO16,"800x600_MONO16"));
   videoModeMap.insert(videoModeMapType (DC1394_VIDEO_MODE_1024x768_YUV422,"1024x768_YUV422"));
   videoModeMap.insert(videoModeMapType (DC1394_VIDEO_MODE_1024x768_RGB8,"1024x768_RGB8"));
   videoModeMap.insert(videoModeMapType (DC1394_VIDEO_MODE_1024x768_MONO8,"1024x768_MONO8"));
   videoModeMap.insert(videoModeMapType (DC1394_VIDEO_MODE_1024x768_MONO16,"1024x768_MONO16"));
   videoModeMap.insert(videoModeMapType (DC1394_VIDEO_MODE_1280x960_YUV422,"1280x960_YUV422"));
   videoModeMap.insert(videoModeMapType (DC1394_VIDEO_MODE_1280x960_RGB8,"1280x960_RGB8"));
   videoModeMap.insert(videoModeMapType (DC1394_VIDEO_MODE_1280x960_MONO8,"1280x960_MONO8"));
   videoModeMap.insert(videoModeMapType (DC1394_VIDEO_MODE_1280x960_MONO16,"1280x960_MONO16"));
   videoModeMap.insert(videoModeMapType (DC1394_VIDEO_MODE_1600x1200_YUV422,"1600x1200_YUV422"));
   videoModeMap.insert(videoModeMapType (DC1394_VIDEO_MODE_1600x1200_RGB8,"1600x1200_RGB8"));
   videoModeMap.insert(videoModeMapType (DC1394_VIDEO_MODE_1600x1200_MONO8,"1600x1200_MONO8"));
   videoModeMap.insert(videoModeMapType (DC1394_VIDEO_MODE_1600x1200_MONO16,"1600x1200_MONO16"));
   // GJ Add format 7 modes
   videoModeMap.insert(videoModeMapType (DC1394_VIDEO_MODE_FORMAT7_0,"Format_7_0"));
   videoModeMap.insert(videoModeMapType (DC1394_VIDEO_MODE_FORMAT7_1,"Format_7_1"));
   videoModeMap.insert(videoModeMapType (DC1394_VIDEO_MODE_FORMAT7_2,"Format_7_2"));
   videoModeMap.insert(videoModeMapType (DC1394_VIDEO_MODE_FORMAT7_3,"Format_7_3"));
   videoModeMap.insert(videoModeMapType (DC1394_VIDEO_MODE_FORMAT7_4,"Format_7_4"));
   videoModeMap.insert(videoModeMapType (DC1394_VIDEO_MODE_FORMAT7_5,"Format_7_5"));
   videoModeMap.insert(videoModeMapType (DC1394_VIDEO_MODE_FORMAT7_6,"Format_7_6"));
   videoModeMap.insert(videoModeMapType (DC1394_VIDEO_MODE_FORMAT7_7,"Format_7_7"));
}


void Cdc1394::setFrameRateMap()
{
   frameRateMap.insert (frameRateMapType(DC1394_FRAMERATE_1_875, "  1.88 fps"));
   frameRateMap.insert (frameRateMapType(DC1394_FRAMERATE_3_75, "  3.75 fps"));
   frameRateMap.insert (frameRateMapType(DC1394_FRAMERATE_7_5, "  7.5 fps"));
   frameRateMap.insert (frameRateMapType(DC1394_FRAMERATE_15, " 15 fps"));
   frameRateMap.insert (frameRateMapType(DC1394_FRAMERATE_30, " 30 fps"));
   frameRateMap.insert (frameRateMapType(DC1394_FRAMERATE_60, " 60 fps"));
   frameRateMap.insert (frameRateMapType(DC1394_FRAMERATE_120, "120 fps"));
   frameRateMap.insert (frameRateMapType(DC1394_FRAMERATE_240, "240 fps"));
}

/**
 * Starts continuous acquisition.
 */
int Cdc1394::StartSequenceAcquisition(long numImages, double interval_ms)
{
   
   // If we're using the camera in some other way, stop that
   Cdc1394::StopTransmission();
   
   printf("Started camera streaming.\n");
   if (acquiring_)
      return ERR_BUSY_ACQUIRING;

   int ret = GetCoreCallback()->PrepareForAcq(this);
   if (ret != DEVICE_OK)
      return ret;

   logMsg_.clear();
   logMsg_ << "Started sequence acquisition: " << numImages << " at " << interval_ms << " ms" << endl;
   LogMessage(logMsg_.str().c_str());

   imageCounter_ = 0;
   sequenceLength_ = numImages;
   //const ACE_Time_Value curr_tv = ACE_OS::gettimeofday ();
   //ACE_Time_Value interval = ACE_Time_Value (0, (long)(interval_ms * 1000.0));
   //acqTimer_->schedule (tcb_, &timerArg_, curr_tv + ACE_Time_Value (0, 1000), interval);

   double actualIntervalMs = max(GetExposure(), interval_ms);
   acqThread_->SetInterval(actualIntervalMs);
   SetProperty(MM::g_Keyword_ActualInterval_ms, CDeviceUtils::ConvertToString(actualIntervalMs));

   err = dc1394_video_set_multi_shot(camera, numImages, DC1394_ON);
   if (err != DC1394_SUCCESS) {
      logMsg_.clear();
      logMsg_ << "Unable to start multi-shot" << endl;
      LogMessage(logMsg_.str().c_str());
      return err;
   }

   GetBytesPerPixel();

   acqThread_->SetLength(numImages);

   // TODO: check trigger mode, etc..

   acqThread_->Start();

   acquiring_ = true;

   LogMessage("Acquisition thread started");

   return DEVICE_OK;
}

/**
 * Stops acquisition
 */
int Cdc1394::StopSequenceAcquisition()
{   
   printf("Stopped camera streaming.\n");
   acqThread_->Stop();
   acquiring_ = false;
   err = dc1394_video_set_multi_shot(camera, 0, DC1394_OFF);
   if (err != DC1394_SUCCESS) {
      logMsg_.clear();
      logMsg_ << "Unable to stop multi-shot" << endl;
      LogMessage(logMsg_.str().c_str());
      return err;
   }
   // TODO: the correct termination code needs to be passed here instead of "0"
   MM::Core* cb = GetCoreCallback();
   if (cb)
      cb->AcqFinished(this, 0);
   return DEVICE_OK;
}

int Cdc1394::PushImage(dc1394video_frame_t *myframe)
{
   logMsg_.clear();
   logMsg_ << "Pushing image " <<imageCounter_<< endl;
   LogMessage(logMsg_.str().c_str());

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
   MM::ImageProcessor* ip = GetCoreCallback()->GetImageProcessor(this);
   if (ip)
   {
      int ret = ip->Process(myframe->image, width, height,bytesPerPixel);
      if (ret != DEVICE_OK) return ret;
   }

   // insert image into the circular MMCore buffer
   return GetCoreCallback()->InsertImage(this, myframe->image,
                                           width, height, bytesPerPixel);
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
      err=dc1394_capture_dequeue(camera_->camera, DC1394_CAPTURE_POLICY_WAIT, &myframe);/* Capture */
      if(err!=DC1394_SUCCESS)
      {
         logMsg_.clear();
         logMsg_ << "Dequeue failed with code: " <<err ;
         camera_->LogMessage(logMsg_.str().c_str());
         camera_->StopSequenceAcquisition();
         return err; 
      } else {
         logMsg_.clear();
         logMsg_ << "Dequeued image: " << imageCounter <<
         " with timestamp: " <<myframe->timestamp << 
         " ring buffer pos: "<<myframe->id <<
         " frames_behind: "<<myframe->frames_behind<<endl ;
         camera_->LogMessage(logMsg_.str().c_str());
      }
      int ret = camera_->PushImage(myframe);
      if (ret != DEVICE_OK)
      {
         logMsg_.clear();
         logMsg_ << "PushImage() failed with errorcode: " << ret;
         camera_->LogMessage(logMsg_.str().c_str());
         camera_->StopSequenceAcquisition();
         return 2;
      }
      err=dc1394_capture_enqueue(camera_->camera,myframe);/* Capture */
      if(err!=DC1394_SUCCESS)
      {
         logMsg_.clear();
         logMsg_<< "Failed to enqueue image" <<imageCounter<< endl;
         camera_->LogMessage(logMsg_.str().c_str());

         camera_->StopSequenceAcquisition();
         return err; 
      } 
      imageCounter++;
      //printf("Acquired frame %ld.\n", imageCounter);                           
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
