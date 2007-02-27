///////////////////////////////////////////////////////////////////////////////
// FILE:       dc1394.cpp
// PROJECT:    MicroManage
// SUBSYSTEM:  DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:
// dc1394 camera module based on libdc1394 API
//                
// AUTHOR:        Nico Stuurman, 12/29/2006
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
#include <string>
#include <sstream>
#include <iomanip>
#include <math.h>

#pragma warning(disable : 4996) // disable warning for deperecated CRT functions on Windows 

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
      sprintf (logMsg_, "Settings feature %d to %d, result: %d", feature, value, err);
      LogMessage(logMsg_, true);
   }
   else if (eAct == MM::BeforeGet)
   {
      err = dc1394_feature_get_value(camera, feature, &value);
      sprintf(logMsg_, "Getting feature %d.  It is now %d, err: %d", feature, value, err);
      LogMessage (logMsg_, true);
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

   // Camera video modes, default to the first mode found
   CPropertyAction *pAct = new CPropertyAction (this, &Cdc1394::OnMode);
   mode = modes.modes[0];
   nRet = CreateProperty(g_Keyword_Modes, videoModeMap[mode].c_str(), MM::String, false, pAct);
   assert(nRet == DEVICE_OK);
   vector<string> modeValues;
   for (int i=0; i<modes.num; i++) {
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
   char	CameraName[(2 * MAX_CHARS) + 4] = "Unrecognized";
   strcpy (CameraName, camera->vendor);
   strcat (CameraName, ", ");
   strcat (CameraName, camera->model);
   nRet = CreateProperty("Vendor Info", CameraName, MM::String, true);
   assert(nRet == DEVICE_OK);
		      
   // Other features, ask what the camera has to offer
   if (dc1394_get_camera_feature_set(camera,&features) !=DC1394_SUCCESS) 
         return ERR_GET_CAMERA_FEATURE_SET_FAILED;                         
   int j = 0;
   for (int i= DC1394_FEATURE_MIN; i <= DC1394_FEATURE_MAX; i++, j++)  
   {
       featureInfo = features.feature[j];
       if (featureInfo.available) {
          const char *featureLabel = dc1394_feature_desc[featureInfo.id - DC1394_FEATURE_MIN];
          if (strcmp(featureLabel, "Brightness") ==0) 
          {
             // TODO: offer option to switch between auto, manual and one-push modes
             SetManual(DC1394_FEATURE_BRIGHTNESS);

             // TODO: Check that this feature is read-out capable
             err = dc1394_feature_get_value(camera, DC1394_FEATURE_BRIGHTNESS, &brightness);
             sprintf(logMsg_, "Brightness %d", brightness);
             LogMessage (logMsg_, false);
             err = dc1394_feature_get_boundaries(camera, DC1394_FEATURE_BRIGHTNESS, &brightnessMin, &brightnessMax);
             sprintf(logMsg_, "Brightness Min: %d, Max: %d", brightnessMin, brightnessMax);
             LogMessage (logMsg_, false);

             char tmp[10];
             pAct = new CPropertyAction (this, &Cdc1394::OnBrightness);
             sprintf(tmp,"%d",brightness);
             nRet = CreateProperty("Brightness", tmp, MM::Integer, false, pAct);
             assert(nRet == DEVICE_OK);
          } 
          else if (strcmp(featureLabel, "Gain") == 0) 
          {
             // TODO: offer option to switch between auto, manual and one-push modes
             SetManual(DC1394_FEATURE_GAIN);

             err = dc1394_feature_get_value(camera, DC1394_FEATURE_GAIN, &gain);
             sprintf(logMsg_, "Gain: %d", gain);
             LogMessage (logMsg_, false);
             err = dc1394_feature_get_boundaries(camera, DC1394_FEATURE_GAIN, &gainMin, &gainMax);
             sprintf(logMsg_, "Gain Min: %d, Max: %d", gainMin, gainMax);
             LogMessage (logMsg_, false);
             char tmp[10];
             pAct = new CPropertyAction (this, &Cdc1394::OnGain);
             sprintf(tmp,"%d",gain);
             nRet = CreateProperty(MM::g_Keyword_Gain, tmp, MM::Integer, false, pAct);
             assert(nRet == DEVICE_OK);
          }
          else if (strcmp(featureLabel, "Shutter") == 0) 
          {
             // TODO: offer option to switch between auto, manual and one-push modes
             SetManual(DC1394_FEATURE_SHUTTER);

             // TODO: when shutter has absolute control, couple it to exposure time
             err = dc1394_feature_get_value(camera, DC1394_FEATURE_SHUTTER, &shutter);
             sprintf(logMsg_, "Shutter: %d", shutter);
             LogMessage (logMsg_, false);
             err = dc1394_feature_get_boundaries(camera, DC1394_FEATURE_SHUTTER, &shutterMin, &shutterMax);
             sprintf(logMsg_, "Shutter Min: %d, Max: %d", shutterMin, shutterMax);
             LogMessage (logMsg_, false);
             char tmp[10];
             pAct = new CPropertyAction (this, &Cdc1394::OnShutter);
             sprintf(tmp,"%d",shutter);
             nRet = CreateProperty("Shutter", tmp, MM::Integer, false, pAct);
             assert(nRet == DEVICE_OK);
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
      dc1394_free_camera(camera);

      printf("Shutdown camera\n");

      m_bInitialized = false;
   }
   return DEVICE_OK;
}

int Cdc1394::GetCamera()
{
   // Find and initialize the camera
   err = dc1394_find_cameras(&cameras, &numCameras); 
   if (err != DC1394_SUCCESS)
      return ERR_CAMERA_NOT_FOUND; 
   if (numCameras < 1) 
      return ERR_CAMERA_NOT_FOUND;
   
   // TODO: work with multiple cameras (select by name??)
   // For now we'll take the first camera on the bus
   camera = cameras[0];
   // free the other ones:
   for (int i=1; i<numCameras; i++)                                                 
      dc1394_free_camera(cameras[i]);                                          
   free(cameras);                                                             

   return DEVICE_OK;
}
 
void Cdc1394::mono8AddToMono16(uint16_t* dest, uint8_t* src, uint32_t  width, uint32_t height)
{
   for (int i=0; i < (width * height); i++) 
   {
      dest[i] += src[i];
   }
}

// Adds 8 bit rgb image to 16  bit grayscale
// It is the callers responsibility that both src and destination exist
void Cdc1394::rgb8AddToMono16(uint16_t* dest, uint8_t* src, uint32_t width, uint32_t height) 
{
   for (int i=0; i < (width * height); i++) 
   {
      dest[i] += (src[3*i] + src[3*i + 1] + src[3*i + 2]) / 3;
   }
}

// converts rgb image to 8 bit grayscale
// It is the callers responsibility that both src and destination exist
void Cdc1394::rgb8ToMono8(uint8_t* dest, uint8_t* src, uint32_t width, uint32_t height) 
{
   for (int i=0; i < (width * height); i++) 
   {
      dest[i] = (src[3*i] + src[3*i + 1] + src[3*i + 2]) / 3;
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
         memcpy (pixBuffer, src, GetImageBufferSize());
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
   // TODO: check if the exposure can be set.  It can't for the iSight:
   dExp = 30.0;
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

int Cdc1394::SetROI(unsigned uX, unsigned uY, unsigned uXSize, unsigned uYSize)
{
   // Unsupported.  Report an error:
   return ERR_ROI_NOT_SUPPORTED;
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

int Cdc1394::GetROI(unsigned& uX, unsigned& uY, unsigned& uXSize, unsigned& uYSize)
{
   return DEVICE_OK;
}

int Cdc1394::ClearROI()
{
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
   sprintf(logMsg_, "Mode set to %d", mode);
   LogMessage (logMsg_, true);
   err = dc1394_video_set_framerate(camera,framerate);
   if (err != DC1394_SUCCESS)
      return ERR_SET_FRAMERATE_FAILED;
   sprintf(logMsg_, "Framerate set to %d", framerate);
   LogMessage (logMsg_, true);

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
   while( status == DC1394_OFF && i < 5 ) 
   {
     usleep(50000);
     if (dc1394_video_get_transmission(camera, &status)!=DC1394_SUCCESS)
        return ERR_GET_TRANSMISSION_FAILED;
     i++;
   }
   if (i == 5)
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
   sprintf (logMsg_, "tried %d times", i);
   LogMessage (logMsg_, true);
   if (i==10) {
      sprintf (logMsg_, "PROBLEM!!!:: Failed to capture");
      LogMessage (logMsg_, false);
      // Camera is in a bad state.  Try to rescue what we can:
      dc1394_free_camera(camera);
      m_bInitialized = false;
      // If/when we get here, try starting the camera again, probably hopeless
      int nRet = GetCamera();
      sprintf (logMsg_, "Tried restarting the camera, Result: %d", nRet);
      LogMessage (logMsg_, false);
      if (nRet == DEVICE_OK && triedCaptureCount <= 5)
      {
         triedCaptureCount++;
         return ResizeImageBuffer();
      } else
         return ERR_CAPTURE_FAILED;
   }

   // Get the image size from the camera:
   err = dc1394_get_image_size_from_video_mode(camera, mode, &width, &height);
   if (err != DC1394_SUCCESS)
      return ERR_GET_IMAGE_SIZE_FAILED;
   GetBitDepth();
   GetBytesPerPixel();
   // Set the internal buffer (for now 8 bit gray scale only)
   // TODO: implement other pixel sizes
   img_.Resize(width, height, bytesPerPixel);


   sprintf(logMsg_, "Everything OK in ResizeImageBuffer");
   LogMessage (logMsg_, true);
   
   return DEVICE_OK;
}

/*
 * Set this feature in Manual mode and switch it on
 * TODO: Error checking
 */
int Cdc1394::SetManual(dc1394feature_t feature)
{
    dc1394bool_t isSwitchable = DC1394_FALSE;
    err = dc1394_feature_is_switchable(camera, feature, &isSwitchable);
    if (isSwitchable)
       dc1394_feature_set_power(camera, feature, DC1394_ON);
    dc1394bool_t hasManualMode = DC1394_FALSE;
    err = dc1394_feature_has_manual_mode(camera, feature, &hasManualMode);
    if (hasManualMode)
       dc1394_feature_set_mode(camera, feature, DC1394_FEATURE_MODE_MANUAL);
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
   dc1394_video_set_transmission(camera, DC1394_OFF);
   // wait untill the camera stopped transmitting:
   dc1394switch_t status = DC1394_ON;
   int i = 0;
   while( status == DC1394_ON && i < 50 )
   {       
      usleep(50000);
      if (dc1394_video_get_transmission(camera, &status)!=DC1394_SUCCESS)
         return ERR_GET_TRANSMISSION_FAILED;
      i++;
   }
   sprintf (logMsg_, "Waited for %d cycles for transmission to stop", i); 
   LogMessage (logMsg_, true);
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
      printf ("Can not figure out Bytes per Pixel.  bitdepth=%s\n", depth);
}


bool Cdc1394::InArray(dc1394framerate_t *array, int size, uint32_t num)
{
   for(int j = 0; j < size; j++)
      if(array[j] == num)
         return true;
   return false;
}

/*
 * Since the possible framerates are videomode dependend, it needs to be figure out 
 * every time that a videomode is changed
 */
int Cdc1394::SetUpFrameRates() 
{
   if (dc1394_video_get_supported_framerates(camera,mode,&framerates)!=DC1394_SUCCESS)
      return ERR_GET_FRAMERATES_FAILED;

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
   for (int i=0; i<framerates.num; i++) {
      string tmp = frameRateMap[ framerates.framerates[i] ];
      // printf ("%s\n", tmp.c_str());
      if (tmp != "")
         rateValues.push_back(tmp);
   }   
   int nRet = SetAllowedValues(g_Keyword_FrameRates, rateValues);
   sprintf (logMsg_, "FrameRate: Changed list of allowed values");
   LogMessage (logMsg_, true);
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
}


void Cdc1394::setFrameRateMap()
{
   frameRateMap.insert (frameRateMapType(DC1394_FRAMERATE_1_875, "  1.75 fps"));
   frameRateMap.insert (frameRateMapType(DC1394_FRAMERATE_3_75, "  3.75 fps"));
   frameRateMap.insert (frameRateMapType(DC1394_FRAMERATE_7_5, "  7.5 fps"));
   frameRateMap.insert (frameRateMapType(DC1394_FRAMERATE_15, " 15 fps"));
   frameRateMap.insert (frameRateMapType(DC1394_FRAMERATE_30, " 30 fps"));
   frameRateMap.insert (frameRateMapType(DC1394_FRAMERATE_60, " 60 fps"));
   frameRateMap.insert (frameRateMapType(DC1394_FRAMERATE_120, "120 fps"));
   frameRateMap.insert (frameRateMapType(DC1394_FRAMERATE_240, "240 fps"));
}
