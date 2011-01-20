///////////////////////////////////////////////////////////////////////////////
// FILE:          TetheredCamera.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Camera driver for Canon and Nikon cameras using 
//                DSLRRemote, NKRemote, or PSRemote tethering software.
//                
// AUTHOR:        Koen De Vleeschauwer, www.kdvelectronics.eu, 2010
//
// COPYRIGHT:     (c) 2010, Koen De Vleeschauwer, www.kdvelectronics.eu
//                (c) 2007, Regents of the University of California
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

// Compilation:
//
// In Visual Studio, set
// Project -> Properties -> Linker -> Input -> Additional Dependencies: windowscodecs.lib
// 
// For Canon DSLR:
// Project -> Properties -> Configuration Properties -> C/C++ -> Preprocessor -> Preprocessor Definitions: add _DSLRREMOTE_
// Include library DSLRRemoteLib.lib in project
//
// For Canon Powershot:
// Project -> Properties -> Configuration Properties -> C/C++ -> Preprocessor -> Preprocessor Definitions: add _PSREMOTE_
// Include library PSRemoteLib.lib in project
//
// For Nikon:
// Project -> Properties -> Configuration Properties -> C/C++ -> Preprocessor -> Preprocessor Definitions: add _NKREMOTE_
// Include library NKRemoteLib.lib in project
//

// Testing:
//
// - Connect a Canon DSLR camera.
// - Download, install and configure DSLRRemote from www.breezesys.com. Demo version is sufficient.
// - Start up DSLRRemote. Click on the "Release" button to take a picture. Check the picture is downloaded to the PC, and displayed in the DSLRRemote main window. 
// - Install the Windows Imaging Component codec for the camera's raw image format. 
// - With DSLRRemote still running, start up Micro-Manager. 
// - Create a hardware config consisting of DSLRRemoteCam, Demo Shutter, and Demo Stage.
// - Click the micro-manager "Snap" button to take a picture. Check the picture appears in the micro-manager "Live" window.
// - Alternatively test using a Nikon DSLR camera, NKRemote software and NKRemoteCam driver.
//

#ifndef _DSLRREMOTE_
#ifndef _NKREMOTE_
#ifndef _PSREMOTE_
#error "Define one of _DSLRREMOTE_, _NKREMOTE_, or _PSREMOTE_"
#endif
#endif
#endif

//#include "stdafx.h"
#include "TetheredCamera.h"
#include <cstdio>
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include "../../MMCore/Error.h"
#include <sstream>

#ifdef _DSLRREMOTE_
#include "DSLRRemoteLib.h"
#endif

#ifdef _NKREMOTE_
#include "NKRemoteLib.h"
#endif

#ifdef _PSREMOTE_
#include "PSRemoteLib.h"
#endif

using namespace std;
const double CTetheredCamera::nominalPixelSizeUm_ = 1.0;

// External names used used by the rest of the system
// to load particular device from the "DemoCamera.dll" library
#ifdef _DSLRREMOTE_
const char* g_CameraDeviceName = "CanonDSLRCam";
const char* g_CameraDeviceDescription = "Canon DSLR Camera";
#endif

#ifdef _NKREMOTE_
const char* g_CameraDeviceName = "NikonDSLRCam";
const char* g_CameraDeviceDescription = "Nikon DSLR Camera";
#endif

#ifdef _PSREMOTE_
const char* g_CameraDeviceName = "CanonPSCam";
const char* g_CameraDeviceDescription = "Canon Powershot Camera";
#endif

// constants for naming pixel types (allowed values of the "PixelType" property)
const char* g_PixelType_Gray = "Grayscale";
const char* g_PixelType_Color = "Color";

// TODO: linux entry code

// windows DLL entry code
#ifdef WIN32
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

// Windows COM safe release code
#ifdef WIN32
template <typename T>
inline void SafeRelease(T *&p)
{
   if (p)
   {
      p->Release();
      p = NULL;
   }
}
#endif


///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////

/**
 * List all supperted hardware devices here
 * Do not discover devices at runtime.  To avoid warnings about missing DLLs, Micro-Manager
 * maintains a list of supported device (MMDeviceList.txt).  This list is generated using 
 * information supplied by this function, so runtime discovery will create problems.
 */
MODULE_API void InitializeModuleData()
{
   AddAvailableDeviceName(g_CameraDeviceName, g_CameraDeviceDescription);
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   // decide which device class to create based on the deviceName parameter
   if (strcmp(deviceName, g_CameraDeviceName) == 0)
   {
      // create camera
      return new CTetheredCamera();
   }
 
   // ...supplied name not recognized
   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
// CTetheredCamera implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~

/**
* CTetheredCamera constructor.
* Setup default all variables and create device properties required to exist
* before intialization. In this case, no such properties were required. All
* properties will be created in the Initialize() method.
*
* As a general guideline Micro-Manager devices do not access hardware in the
* the constructor. We should do as little as possible in the constructor and
* perform most of the initialization in the Initialize() method.
*/
CTetheredCamera::CTetheredCamera() :
   CCameraBase<CTetheredCamera> (),
   initialized_(false),
   cameraName_(""),
   frameBitmap(NULL),
   grayScale_(true),
   keepOriginals_(false),
   roiX_(0),
   roiY_(0),
   roiXSize_(0),
   roiYSize_(0),
   scaleFactor_(1),
   originX_(0),
   originY_(0)
{
   // call the base class method to set-up default error codes/messages
   InitializeDefaultErrorMessages();
   // add our specific error messages
   SetErrorText(ERR_CAM_BAD_PARAM, "Parameter value wrong");
   SetErrorText(ERR_CAM_NO_IMAGE, "No image found. Image saved on CF card?");
   SetErrorText(ERR_CAM_NOT_RUNNING, "DSLRRemote/NKRemote/PSRemote is not running");
   SetErrorText(ERR_CAM_NOT_CONNECTED, "Camera is not connected");
   SetErrorText(ERR_CAM_BUSY, "Camera is busy");
   SetErrorText(ERR_CAM_TIMEOUT, "Timeout waiting for image to be saved");
   SetErrorText(ERR_CAM_SHUTTER, "Error releasing shutter e.g. AF failure");
   SetErrorText(ERR_CAM_UNKNOWN, "Unexpected return status");
   SetErrorText(ERR_CAM_LOAD, "Image load failure");
   SetErrorText(ERR_CAM_CONVERSION, "Image conversion failure");
   SetErrorText(ERR_CAM_SHUTTER_SPEEDS, "Error in list of camera shutter speeds");
}

/**
* CTetheredCamera destructor.
* If this device used as intended within the Micro-Manager system,
* Shutdown() will be always called before the destructor. But in any case
* we need to make sure that all resources are properly released even if
* Shutdown() was not called.
*/
CTetheredCamera::~CTetheredCamera()
{
   // no clean-up required for this device
}

/**
* Obtains device name.
* Required by the MM::Device API.
*/
void CTetheredCamera::GetName(char* name) const
{
   // We just return the name we use for referring to this
   // device adapter.
   CDeviceUtils::CopyLimitedString(name, g_CameraDeviceName);
}

/**
* Intializes the hardware.
* Required by the MM::Device API.
* Typically we access and initialize hardware at this point.
* Device properties are typically created here as well, except
* the ones we need to use for defining initialization parameters.
* Such pre-initialization properties are created in the constructor.
* (This device does not have any pre-initialization properties)
*/
int CTetheredCamera::Initialize()
{
   if (initialized_)
      return DEVICE_OK;

   // set property list
   // -----------------

   // Name
   int nRet = CreateProperty(MM::g_Keyword_Name, g_CameraDeviceName, MM::String, true);
   if (DEVICE_OK != nRet)
      return nRet;

   // Description
#ifdef _DSLRREMOTE_
   char desc[]="DSLRRemote Canon DSLR Camera";
#endif

#ifdef _NKREMOTE_
   char desc[]="NKRemote Nikon DSLR Camera";
#endif

#ifdef _PSREMOTE_
   char desc[]="PSRemote Canon Powershot Camera";
#endif

   nRet = CreateProperty(MM::g_Keyword_Description, desc, MM::String, true);
   if (DEVICE_OK != nRet)
      return nRet;

   // Get camera manufacturer name and model.
   nRet = GetCameraName();
   if (DEVICE_OK != nRet)
      return nRet;

   // CameraName
   nRet = CreateProperty(MM::g_Keyword_CameraName, cameraName_.c_str(), MM::String, true);
   assert(nRet == DEVICE_OK);

   // CameraID
   nRet = CreateProperty(MM::g_Keyword_CameraID, "V1.0", MM::String, true);
   assert(nRet == DEVICE_OK);

   // binning
   CPropertyAction *pAct = new CPropertyAction (this, &CTetheredCamera::OnBinning);
   nRet = CreateProperty(MM::g_Keyword_Binning, "1", MM::Integer, false, pAct);
   assert(nRet == DEVICE_OK);

   nRet = SetAllowedBinning();
   if (nRet != DEVICE_OK)
      return nRet;

   // pixel type
   pAct = new CPropertyAction (this, &CTetheredCamera::OnPixelType);
   nRet = CreateProperty(MM::g_Keyword_PixelType, g_PixelType_Color, MM::String, false, pAct);
   assert(nRet == DEVICE_OK);

   vector<string> pixelTypeValues;
   pixelTypeValues.push_back(g_PixelType_Gray);
   pixelTypeValues.push_back(g_PixelType_Color); 

   nRet = SetAllowedValues(MM::g_Keyword_PixelType, pixelTypeValues);
   if (nRet != DEVICE_OK)
      return nRet;

   // Keep original images
   pAct = new CPropertyAction (this, &CTetheredCamera::OnKeepOriginals);
   nRet = CreateProperty(g_Keyword_KeepOriginals, "0", MM::Integer, false, pAct);
   assert(nRet == DEVICE_OK);

   vector<string> keepOriginalsValues;
   keepOriginalsValues.push_back("0");
   keepOriginalsValues.push_back("1"); 

   nRet = SetAllowedValues(g_Keyword_KeepOriginals, keepOriginalsValues);
   if (nRet != DEVICE_OK)
      return nRet;

   // Exposure
   nRet = CreateProperty(MM::g_Keyword_Exposure, "0.0", MM::Float, false);
   assert(nRet == DEVICE_OK);
   SetPropertyLimits(MM::g_Keyword_Exposure, 0, 10000);

   // Shutter Speeds
   nRet = CreateProperty(g_Keyword_ShutterSpeeds, "", MM::String, false);
   assert(nRet == DEVICE_OK);

   // synchronize all properties
   // --------------------------
   nRet = UpdateStatus();
   if (nRet != DEVICE_OK)
      return nRet;

   // initialize image buffer
   nRet = SnapImage();
   if (nRet != DEVICE_OK)
      return nRet;

   nRet = ResizeImageBuffer();
   return nRet;
}

/**
* Shuts down (unloads) the device.
* Required by the MM::Device API.
* Ideally this method will completely unload the device and release all resources.
* Shutdown() may be called multiple times in a row.
* After Shutdown() we should be allowed to call Initialize() again to load the device
* without causing problems.
*/
int CTetheredCamera::Shutdown()
{
   initialized_ = false;
   return DEVICE_OK;
}

/**
* Performs exposure and grabs a single image.
* This function should block during the actual exposure and return immediately afterwards 
* (i.e., before readout).  This behavior is needed for proper synchronization with the shutter.
* Required by the MM::Camera API.
*/
int CTetheredCamera::SnapImage()
{
   int nRet = SetCameraExposure(GetExposure());
   if (DEVICE_OK != nRet)
      return nRet;
   
   // Capture frame
   nRet = AcquireFrame();
   if (nRet != DEVICE_OK)
      return nRet;

   // Copy frame to image buffer
   nRet = ResizeImageBuffer();
   return nRet;
}


/**
* Returns pixel data.
* Required by the MM::Camera API.
* The calling program will assume the size of the buffer based on the values
* obtained from GetImageBufferSize(), which in turn should be consistent with
* values returned by GetImageWidth(), GetImageHight() and GetImageBytesPerPixel().
* The calling program allso assumes that camera never changes the size of
* the pixel buffer on its own. In other words, the buffer can change only if
* appropriate properties are set (such as binning, pixel type, etc.)
*/
const unsigned char* CTetheredCamera::GetImageBuffer()
{
   return img_.GetPixels();
}

/**
* Returns pixel data with interleaved RGB pixels in 32 bpp format
*/

const unsigned int* CTetheredCamera::GetImageBufferAsRGB32()
{
    return (const unsigned int*)GetImageBuffer();
}

/**
* Returns the number of channels in this image. This is '1' for grayscale cameras, and '4' for RGB cameras. 
*/
unsigned int CTetheredCamera::GetNumberOfComponents() const
{
   if (grayScale_)
      return 1; // grayscale
   else
      return 4; // rgb
}

/**
* Returns the name for each channel. 
*/
int CTetheredCamera::GetComponentName(unsigned int channel, char* name)
{
   if (grayScale_)
   {
      if (channel == 0)
         CDeviceUtils::CopyLimitedString(name, "Grayscale");
      else
         return DEVICE_NONEXISTENT_CHANNEL;
   }
   else 
   {
      switch (channel)
      {
         case 0:
            CDeviceUtils::CopyLimitedString(name, "Blue");
            break;
         case 1:
            CDeviceUtils::CopyLimitedString(name, "Green");
            break;
         case 2:
            CDeviceUtils::CopyLimitedString(name, "Red");
            break;
         case 3:
            CDeviceUtils::CopyLimitedString(name, "Alpha");
            break;
         default:
            return DEVICE_NONEXISTENT_CHANNEL;
            break;
      }
   }
   return DEVICE_OK;
}

/**
* Returns image buffer X-size in pixels.
* Required by the MM::Camera API.
*/
unsigned CTetheredCamera::GetImageWidth() const
{
   return img_.Width();
}

/**
* Returns image buffer Y-size in pixels.
* Required by the MM::Camera API.
*/
unsigned CTetheredCamera::GetImageHeight() const
{
   return img_.Height();
}

/**
* Returns image buffer pixel depth in bytes.
* Required by the MM::Camera API.
*/
unsigned CTetheredCamera::GetImageBytesPerPixel() const
{
   return img_.Depth();
} 

/**
* Returns the bit depth (dynamic range) of the pixel.
* This does not affect the buffer size, it just gives the client application
* a guideline on how to interpret pixel values.
* Required by the MM::Camera API.
*/
unsigned CTetheredCamera::GetBitDepth() const
{
   return 8; // All pixel values are 0..255, in rgb as well as in grayscale
}

/**
* Returns the size in bytes of the image buffer.
* Required by the MM::Camera API.
*/
long CTetheredCamera::GetImageBufferSize() const
{
   return img_.Width() * img_.Height() * GetImageBytesPerPixel();
}

/**
* Sets the camera Region Of Interest.
* Required by the MM::Camera API.
* This command will change the dimensions of the image.
* Depending on the hardware capabilities the camera may not be able to configure the
* exact dimensions requested - but should try do as close as possible.
* If the hardware does not have this capability the software should simulate the ROI by
* appropriately cropping each frame.
* This demo implementation ignores the position coordinates and just crops the buffer.
* @param x - top-left corner coordinate
* @param y - top-left corner coordinate
* @param xSize - width
* @param ySize - height
*/
int CTetheredCamera::SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize)
{
   if (scaleFactor_ == 0)
      scaleFactor_ = 1;
   roiX_ = originX_ + x * scaleFactor_;
   roiY_ = originY_ + y * scaleFactor_;
   roiXSize_ = xSize * scaleFactor_;
   roiYSize_ = ySize * scaleFactor_;
   return DEVICE_OK;
}

/**
* Returns the actual dimensions of the current ROI.
* Required by the MM::Camera API.
*/
int CTetheredCamera::GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize)
{
   if (scaleFactor_ == 0)
      scaleFactor_ = 1;
   x = roiX_ / scaleFactor_;
   y = roiY_ / scaleFactor_;
   xSize = roiXSize_ / scaleFactor_;
   ySize = roiYSize_ / scaleFactor_;
   if ((roiX_ == 0) && (roiY_ == 0) && (roiXSize_ == 0) && (roiYSize_ == 0)) // Select whole image
   {
      x = 0;
      y = 0;
      xSize = img_.Width();
      ySize = img_.Height();
   }
   return DEVICE_OK;
}

/**
* Resets the Region of Interest to full frame.
* Required by the MM::Camera API.
*/
int CTetheredCamera::ClearROI()
{
   roiX_ = 0;
   roiY_ = 0;
   roiXSize_ = 0;
   roiYSize_ = 0;
   return DEVICE_OK;
}

/**
* Returns the current exposure setting in milliseconds.
* Required by the MM::Camera API.
*/
double CTetheredCamera::GetExposure() const
{
   char buf[MM::MaxStrLength];
   int ret = GetProperty(MM::g_Keyword_Exposure, buf);
   if (ret != DEVICE_OK)
      return 0.0;
   return atof(buf);
}

/**
* Sets exposure in milliseconds.
* Required by the MM::Camera API.
*/
void CTetheredCamera::SetExposure(double exp)
{
   SetProperty(MM::g_Keyword_Exposure, CDeviceUtils::ConvertToString(exp));
   OnPropertiesChanged();
}

/**
* Returns the current binning factor.
* Required by the MM::Camera API.
*/
int CTetheredCamera::GetBinning() const
{
   char buf[MM::MaxStrLength];
   int ret = GetProperty(MM::g_Keyword_Binning, buf);
   if (ret != DEVICE_OK)
      return 1;
   int binning = atoi(buf);
   // Sanity check
   if (binning <= 0) 
      binning = 1;
   return binning;
}

/**
* Sets binning factor.
* Required by the MM::Camera API.
*/
int CTetheredCamera::SetBinning(int binFactor)
{
   int ret = SetProperty(MM::g_Keyword_Binning, CDeviceUtils::ConvertToString(binFactor));
   OnPropertiesChanged();
   return ret;
}

int CTetheredCamera::SetAllowedBinning() 
{
   vector<string> binValues;
   binValues.push_back("1");
   binValues.push_back("2");
   binValues.push_back("4");
   binValues.push_back("8");
   LogMessage("Setting Allowed Binning settings", true);
   return SetAllowedValues(MM::g_Keyword_Binning, binValues);
}


///////////////////////////////////////////////////////////////////////////////
// CTetheredCamera Action handlers
///////////////////////////////////////////////////////////////////////////////

/**
* Handles "Binning" property.
*/
int CTetheredCamera::OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   int ret = DEVICE_ERR;
   switch(eAct)
   {
   case MM::AfterSet:
      {
         if(IsCapturing())
            return DEVICE_CAMERA_BUSY_ACQUIRING;

         // the user just set the new value for the property, so we have to
         // apply this value to the 'hardware'.
         long binFactor;
         pProp->Get(binFactor);

         if ((binFactor == 1) || (binFactor == 2) || (binFactor == 4) || (binFactor == 8))
         {
            // Valid 
            ret=DEVICE_OK;
         }
         else
         {
            // on failure reset default binning of 1
            pProp->Set(1L);
            ret = ERR_CAM_BAD_PARAM;
         }
         OnPropertiesChanged();
      }break;
   case MM::BeforeGet:
      {
         // the user is requesting the current value for the property, so
         // either ask the 'hardware' or let the system return the value
         // cached in the property.
         ret=DEVICE_OK;
      }break;
   }
   return ret; 
}

/**
* Handles "PixelType" property.
*/
int CTetheredCamera::OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   int ret = DEVICE_ERR;
   switch(eAct)
   {
   case MM::AfterSet:
      {
         if(IsCapturing())
            return DEVICE_CAMERA_BUSY_ACQUIRING;

         string pixelType;
         pProp->Get(pixelType);

         if (pixelType.compare(g_PixelType_Gray) == 0)
         {
            grayScale_ = true;
            ret=DEVICE_OK;
         }
         else if (pixelType.compare(g_PixelType_Color) == 0)
         {
            grayScale_ = false;
            ret=DEVICE_OK;
         }
         else
         {
            // on error switch to default pixel type
            pProp->Set(g_PixelType_Color);
            grayScale_ = false;
            ret = ERR_CAM_BAD_PARAM;
         }
         OnPropertiesChanged();
      } break;
   case MM::BeforeGet:
      {
         if (grayScale_)
            pProp->Set(g_PixelType_Gray);
         else
            pProp->Set(g_PixelType_Color);
         ret=DEVICE_OK;
      }break;
   }
   return ret; 
}


/**
* Handles "KeepOriginals" property.
*/
int CTetheredCamera::OnKeepOriginals(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   int ret = DEVICE_ERR;
   switch(eAct)
   {
   case MM::AfterSet:
      {
         string keepOriginals;
         pProp->Get(keepOriginals);

         if (keepOriginals.compare("1") == 0)
         {
            keepOriginals_ = true;
            ret=DEVICE_OK;
         }
         else if (keepOriginals.compare("0") == 0)
         {
            keepOriginals_ = false;
            ret=DEVICE_OK;
         }
         else
         {
            // on error switch to default
            pProp->Set("0");
            keepOriginals_ = false;
            ret = ERR_CAM_BAD_PARAM;
         }
         OnPropertiesChanged();
      } break;
   case MM::BeforeGet:
      {
         if (keepOriginals_)
            pProp->Set("1");
         else
            pProp->Set("0");
         ret=DEVICE_OK;
      }break;
   }
   return ret; 
}

///////////////////////////////////////////////////////////////////////////////
// Private CTetheredCamera methods
///////////////////////////////////////////////////////////////////////////////

/*
 * Read a property, and return true if non-zero.
 */

bool CTetheredCamera::GetBoolProperty(const char* const propName)
{
   char val[MM::MaxStrLength];
   bool boolVal;

   int ret = this->GetProperty(propName, val);
   assert(ret == DEVICE_OK);
   boolVal = strcmp(val, "1") == 0 ? true : false;
   return boolVal;
}

/*
 * Convert return codes from DSLRRemote/NKRemote/PSRemote to micro-manager error codes.
 */

int CTetheredCamera::GetReturnCode(int status)
{
   switch(status)
      {
      case 0: // success
         LogMessage("Remotelib call success", true);
         return DEVICE_OK;
         break;
      case 1:
         LogMessage("DSLRRemote/NKRemote/PSRemote is not running", true);
         return ERR_CAM_NOT_RUNNING;
         break;
      case 2:
         LogMessage("Camera is not connected", true);
         return ERR_CAM_NOT_CONNECTED;
         break;
      case 3:
         LogMessage("Camera is busy", true);
         return ERR_CAM_BUSY;
         break;
      case 4:
         LogMessage("Timeout", true);
         return ERR_CAM_TIMEOUT;
         break;
      case 5:
         LogMessage("Error releasing shutter e.g. AF failure", true);
         return ERR_CAM_SHUTTER;
         break;
      default:
         LogMessage("Error: unexpected return status", true);
         return ERR_CAM_UNKNOWN;
      }
}

/*
 * Get camera brand and model name
 */

int CTetheredCamera::GetCameraName()
{
   cameraName_ = "Camera not connected";

   char cModel[MM::MaxStrLength];
   int status = GetCameraModel(cModel, sizeof(cModel));
   int nRet = GetReturnCode(status);
   if (DEVICE_OK == nRet)
      LogMessage("GetCameraModel success", true);
   else
      return nRet;
   cameraName_ = cModel;
   return DEVICE_OK;
}

/*
 * Sets the camera shutter speed to exp_ms milliseconds.
 * This works if the DSLRRemote/NKRemote/PSRemote "Exp Mode:" is "Tv" or "Manual".
 * If the "Tv:" drop-down list is grayed out shutter speed is determined by the camera.
 *
 * The "Shutter Speeds" property needs to contain a list of available shutter speed settings,separated by semicolons.
 * e.g. for a Canon Powershot A80 the property "ShutterSpeeds" should be as follows:
 * 15;13;10;8;6;5;4;3.2;2.5;1.6;1.3;1;0.8;0.6;0.4;0.3;1/4;1/5;1/6;1/8;1/10;1/13;1/15;1/20;1/25;1/30;1/40;1/50;1/60;1/80;1/100;1/125;1/160;1/200;1/250;1/320;1/400;1/500;1/640;1/800;1/1000;1/1250;1/1600;1/2000
 * The shutter speeds have to be in the same order as they appear in the "Tv:" drop-down box in DSLRRemote/NKRemote/PSRemote.
 *
 * Note: as a special case, a shutter speed of 0 ms in micro-manager leaves the camera shutter speed unchanged. 
 * This allows setting the shutter speed in DSLRRemote/NKRemote/PSRemote instead of in micro-manager. 
 */
int CTetheredCamera::SetCameraExposure(double exp_ms)
{
   ostringstream msg;
   /* 
      special case: 
      if exp_ms is zero, leaves the shutter speed unchanged.
    */
   if (exp_ms == 0.0) 
      return DEVICE_OK;

   /* Get list of available shutter speeds */
   int nRet;
   char shutterSpeedsStr[MM::MaxStrLength];
   nRet = GetProperty(g_Keyword_ShutterSpeeds, shutterSpeedsStr);
   if (DEVICE_OK != nRet)
      return nRet;
   LogMessage("Shutter speeds read", true);
 
   /* loop over available shutter speeds; select shutter speed which is closest to exp_ms milliseconds */
   int bestSetting = -1;
   double bestError = 2 * exp_ms;
   istringstream shutterSpeeds;
   shutterSpeeds.str(shutterSpeedsStr);

   int currSetting = 0;
   string currentShutterStr;
   while (getline(shutterSpeeds, currentShutterStr, ';'))
   {
      double numerator = 0;
      double denominator = 0;
      double currSpeed_ms = -1;
      char slash;

      istringstream currShutter;
      currShutter.str(currentShutterStr);
      if ((currShutter >> numerator) && (numerator > 0))
      {
         if ((currShutter >> slash) && (slash == '/') && (currShutter >> denominator) && (denominator > 0))
            currSpeed_ms = 1000.0 * numerator / denominator; /* speeds such as 1/125 */
         else
            currSpeed_ms = 1000.0 * numerator; /* speeds such as 2.5 */
         double currError = fabs(currSpeed_ms - exp_ms);
         msg.str("");
         msg << "Shutter speed " << currSetting << ": " << currentShutterStr << " = " << currSpeed_ms << "ms";
         LogMessage(msg.str(), true);
         if (currError < bestError)
         {
            bestError = currError;
            bestSetting = currSetting;
         }
      }
      else 
         return ERR_CAM_SHUTTER_SPEEDS;
      currSetting++;
   }

   /* apply best exposure setting to camera. */
   msg.str("");
   msg << "Shutter speed " << bestSetting << " chosen";
   LogMessage(msg.str(), true);
   int status = SetShutterAperture(bestSetting, -1);
   nRet = GetReturnCode(status);
   if (DEVICE_OK == nRet)
      LogMessage("SetShutterAperture success", true);
   return nRet;   
}

/*
 * AcquireFrame: Take a picture and store the result in frameBitmap
 */

int CTetheredCamera::AcquireFrame()
{
   char filename[MAX_PATH];

   /* take one image and report status and filename */
   int status = ReleaseShutter(60, filename, sizeof(filename));
   int nRet = GetReturnCode(status);
   if (DEVICE_OK == nRet)
      LogMessage("ReleaseShutter success", true);
   else
      return nRet;

   if (!(filename && strlen(filename)))
   {
      LogMessage("Image saved on CF card?", true);
      return ERR_CAM_NO_IMAGE;
   }

   /* Convert filename to unicode */
   int lenA = lstrlenA(filename);
   int lenW;
   BSTR unicodefilename;

   lenW = ::MultiByteToWideChar(CP_ACP, 0, filename, lenA, 0, 0);
   if (lenW > 0)
   {
     // Check whether conversion was successful
     unicodefilename = ::SysAllocStringLen(0, lenW);
     ::MultiByteToWideChar(CP_ACP, 0, filename, lenA, unicodefilename, lenW);
   }
   else
     return ERR_CAM_LOAD;

   /* 
   * Use WIC ("Windows Imaging Component") to read the image file and convert to micro-manager format. 
   * File can be .jpg or any WIC-supported format, including Canon and Nikon raw if the correct codecs have been installed. 
   */

   //Initialize COM.
   CoInitialize(NULL);

   IWICImagingFactory *factory = NULL;
   IWICBitmapDecoder *decoder = NULL;

   //Create the COM imaging factory.
   HRESULT hr = CoCreateInstance(CLSID_WICImagingFactory, NULL, CLSCTX_INPROC_SERVER, IID_IWICImagingFactory, (LPVOID*)&factory);

   if (SUCCEEDED(hr))
   {
      hr = factory->CreateDecoderFromFilename(unicodefilename, NULL, GENERIC_READ, WICDecodeMetadataCacheOnDemand, &decoder);
   }
    
   LogMessage("CreateDecoderFromFilename done", true);
   if (SUCCEEDED(hr))
   {
      // free the BSTR
      ::SysFreeString(unicodefilename);
   }

   /* Decode first frame to bitmap */
   UINT nFrameCount=0;

   if (SUCCEEDED(hr))
   {
      hr = decoder->GetFrameCount(&nFrameCount);
   }

   IWICBitmapFrameDecode *frameDecode = NULL;

   if (SUCCEEDED(hr))
   {
      if (nFrameCount == 0) return ERR_CAM_LOAD;

      /* We have a frame! */
      hr = decoder->GetFrame(0, &frameDecode);
   }

   IWICFormatConverter *formatConverter = NULL;
   if (SUCCEEDED(hr))
   {
      hr = factory->CreateFormatConverter(&formatConverter);
   }
   
   WICPixelFormatGUID pixelFormat;
   UINT frameBytesPerPixel;

   if (grayScale_) 
   {
      pixelFormat = GUID_WICPixelFormat8bppGray;
      frameBytesPerPixel = 1;
   }
   else
   {
      pixelFormat = GUID_WICPixelFormat32bppPBGRA;
      frameBytesPerPixel = 4;
   }

   if (SUCCEEDED(hr))
   {
      hr = formatConverter->Initialize(
      frameDecode,                     // Input source to convert
      pixelFormat,                     // Destination pixel format
      WICBitmapDitherTypeNone,         // Specified dither pattern
      NULL,                            // Specify a particular palette 
      0.f,                             // Alpha threshold
      WICBitmapPaletteTypeCustom       // Palette translation type
      );
   }

   UINT frameWidth = 0;
   UINT frameHeight = 0;
      
   if (SUCCEEDED(hr))
   {
      hr = formatConverter->GetSize(&frameWidth, &frameHeight);
   }
   
   if (SUCCEEDED(hr) && ((frameWidth == 0) || (frameHeight == 0)))
      return ERR_CAM_LOAD;

   /* convert to bitmap */
   if (SUCCEEDED(hr))
   {
      SafeRelease(frameBitmap);
      hr = factory->CreateBitmapFromSource(formatConverter, WICBitmapCacheOnLoad, &frameBitmap);
   }

   if (SUCCEEDED(hr))
   {
      LogMessage("CreateBitmapFromSource done", true);

      SafeRelease(formatConverter);
      SafeRelease(frameDecode);
      SafeRelease(decoder);
      SafeRelease(factory);

      // If keepOriginals is set, keep the original raw bitmap as downloaded from camera.
      if (!keepOriginals_)
      {
         remove(filename);
         LogMessage("Remove original done", true);
      }
   
      LogMessage("AcquireFrame done", true);
      return DEVICE_OK;
   }

   return ERR_CAM_LOAD;
}

/*
 * ResizeImage: Clip frameBitmap to the region of interest, apply binning, transpose and store the result in img_
 */

int CTetheredCamera::ResizeImageBuffer()
{

   /* 
    * Use WIC ("Windows Imaging Component") to scale/rotate/flip/clip bitmap.
    */

   // Sanity check
   if (frameBitmap == NULL)
      return DEVICE_OK;

   //Initialize COM.
   CoInitialize(NULL);

   IWICImagingFactory *factory = NULL;

   //Create the COM imaging factory.
   HRESULT hr = CoCreateInstance(CLSID_WICImagingFactory, NULL, CLSCTX_INPROC_SERVER, IID_IWICImagingFactory, (LPVOID*)&factory);

   /* binning: scale image down */
   UINT frameWidth = 0;
   UINT frameHeight = 0;
      
   if (SUCCEEDED(hr))
   {
      hr = frameBitmap->GetSize(&frameWidth, &frameHeight);
   }

   UINT scaledWidth = 0;
   UINT scaledHeight = 0;
   
   scaleFactor_ = GetBinning();

   if (scaleFactor_ <= 0) 
   {
      LogMessage("Error: Binning value", true);
      return ERR_CAM_CONVERSION;
   }

   scaledWidth = frameWidth / scaleFactor_;
   scaledHeight = frameHeight / scaleFactor_;

   IWICBitmapScaler *scaler = NULL;
   if (SUCCEEDED(hr))
   {
      hr = factory->CreateBitmapScaler(&scaler);
   }

   if (SUCCEEDED(hr))
   {
      hr = scaler->Initialize(frameBitmap, scaledWidth, scaledHeight, WICBitmapInterpolationModeFant); 
   }

   if (SUCCEEDED(hr))
   {
      LogMessage("Scaling done", true);
   }

   /* Transpose */ 
   IWICBitmapFlipRotator *transposer = NULL;
   WICBitmapTransformOptions transformOptions;

   int tOptions = 0;
   if (GetBoolProperty(MM::g_Keyword_Transpose_Correction))
   {
      if (GetBoolProperty(MM::g_Keyword_Transpose_SwapXY))
         tOptions += WICBitmapTransformRotate90;
      if (GetBoolProperty(MM::g_Keyword_Transpose_MirrorX))
         tOptions +=  WICBitmapTransformFlipHorizontal;
      if (GetBoolProperty(MM::g_Keyword_Transpose_MirrorY))
         tOptions +=  WICBitmapTransformFlipVertical;
   } 
   transformOptions = static_cast<WICBitmapTransformOptions>(tOptions);

   if (SUCCEEDED(hr))
   {
      hr = factory->CreateBitmapFlipRotator(&transposer);
   }

   if (SUCCEEDED(hr))
   {
      hr = transposer->Initialize(scaler, transformOptions);
   }

   /* Return region of interest (ROI)  */
   UINT transposedWidth = 0;
   UINT transposedHeight = 0;
      
   if (SUCCEEDED(hr))
   {
      LogMessage("Transposing done", true);
      hr = transposer->GetSize(&transposedWidth, &transposedHeight);
   }
   
   // Apply binning to region of interest
   UINT clipX = roiX_ / scaleFactor_;
   UINT clipY = roiY_  / scaleFactor_;
   UINT clipHeight = roiYSize_  / scaleFactor_;
   UINT clipWidth = roiXSize_  / scaleFactor_;

   if ((clipWidth == 0) || (clipHeight == 0)
      || (clipX + clipWidth > transposedWidth) || (clipY + clipHeight > transposedHeight))
   {
      // Select complete frame
      clipX = 0;
      clipY = 0;
      clipHeight = transposedHeight;
      clipWidth = transposedWidth;
   }

   // Save coordinates of ROI lower left pixel 
   originX_ = clipX * scaleFactor_;
   originY_ = clipY * scaleFactor_;

   IWICBitmapClipper *clipper = NULL;

   if (SUCCEEDED(hr))
   {
      hr = factory->CreateBitmapClipper(&clipper);
   }

   if (SUCCEEDED(hr))
   {
      WICRect roiRect;
      roiRect.X = clipX;
      roiRect.Y = clipY;
      roiRect.Height = clipHeight;
      roiRect.Width = clipWidth;
      hr = clipper->Initialize(transposer, &roiRect);
   }

   /* resize micro-manager image to same dimensions as captured frame */
   UINT imageWidth = 0;
   UINT imageHeight = 0;
      
   if (SUCCEEDED(hr))
   {
      LogMessage("Clipping to roi done", true);
      hr = clipper->GetSize(&imageWidth, &imageHeight);
   }

   UINT frameBytesPerPixel;

   if (grayScale_) 
      frameBytesPerPixel = 1;
   else 
      frameBytesPerPixel = 4;

   if (SUCCEEDED(hr))
   {
      img_.Resize(imageWidth, imageHeight, frameBytesPerPixel);
   }

   /* Copy into image buffer */
   if (SUCCEEDED(hr))
   {
      LogMessage("Resize done", true);
      hr = clipper->CopyPixels(NULL, imageWidth*frameBytesPerPixel, imageHeight*imageWidth*frameBytesPerPixel, img_.GetPixelsRW());
   }
   
   if (SUCCEEDED(hr))
   {
      LogMessage("CopyPixels done", true);
      SafeRelease(clipper);
      SafeRelease(transposer);
      SafeRelease(scaler);
      SafeRelease(factory);
      LogMessage("ResizeImageBuffer done", true);
      return DEVICE_OK;
   }
  
   return ERR_CAM_CONVERSION;
}
// not truncated
