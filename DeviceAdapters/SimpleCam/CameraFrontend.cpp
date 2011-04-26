///////////////////////////////////////////////////////////////////////////////
// FILE:          CameraFrontend.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Simple Camera driver
//                
// AUTHOR:        Koen De Vleeschauwer, www.kdvelectronics.eu, 2011
//
// COPYRIGHT:     (c) 2011, Koen De Vleeschauwer, www.kdvelectronics.eu
//
// LICENSE:       This file is distributed under the LGPL license.
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

// Testing:
//
// - Connect a Canon or Nikon DSLR camera. Verify on www.gphoto.org the camera is supported by gphoto2.
// - Set the camera in PTP mode.
// - If using Mac OS X, kill the PTP daemon before starting up micro-manager.
//   Open a terminal window, and type "ps -ef | grep PTP" to see whether the PTPCamera process is running. Kill this process.
// - Start up Micro-Manager. 
// - Create a hardware config consisting of GPhoto, Demo Shutter, and Demo Stage.
// - Open the property browser. If necessary, choose "CameraName" and select your camera.
// - Click the micro-manager "Snap" button to take a picture. Check the picture appears in the micro-manager "Live" window.
// - If the camera supports jpeg and raw, take pictures in both formats. 
//
// Debugging can be done:
// - By switching on debug logging in "Tools -> Options".
// - Using the command-line program "gphoto2". 
// - Using the test application in the SimpleCam directory.
//
// Notes:
// - If a camera does not work with gphoto2, the camera will not work with this driver either.
// - A camera may not allow shutter speed to be set if in "auto" mode.
//

//
// The micro-manager driver consists of two parts:
// - CCameraFrontend, the operating system and camera independent part, which uses libfreeimage for image manipulation.
// - CSimpleCam, the operating system and camera dependent part, which does image capture using libgphoto2.
//    
// Hierarchy:
// CCameraFrontend
//    FreeImagePlus
//       FreeImage
//    SimpleCam
//       libgphoto2
//          libgphoto2_port
//            libusb
//
// Flow:
// SnapImage
//    SetCameraShutterSpeed         [CCameraFrontend, set the camera shutter speed]
//       setShutterSpeed            [SimpleCam, set the camera shutter speed]
//          gp_camera_set_config    [GPhoto2, write config to camera]
//    capture                       [SimpleCam, capture to file]
//       gp_camera_capture          [GPhoto2, captures an image]
//       gp_camera_file_get         [GPhoto2, retrieves a file from the camera]
//    SetAllowedShutterSpeeds       [CCameraFrontend, update shutter speeds]
//       listShutterSpeeds          [SimpleCam, get available shutter speeds]
//          gp_widget_count_choices [GPhoto2, counts the number of choices for shutter speed]
//          gp_widget_get_choice    [GPhoto2, gets the choices for shutter speed]
//          
//    LoadImage                     [CCameraFrontend, load image]
//       load                       [FreeImagePlus, load image]
//       rescale                    [FreeImagePlus, Binning]
//       rotate                     [FreeImagePlus, Transpose_SwapXY]
//       flipHorizontal             [FreeImagePlus, Transpose_MirrorX]
//       flipVertical               [FreeImagePlus, Transpose_MirrorY]
//       crop                       [FreeImagePlus, Region of Interest]
//       convert                    [FreeImagePlus, Grayscale/color, 8/16bpp]
//       ConvertToRawBits           [FreeImage, copy to micro-manager image buffer]
//
         
#include "CameraFrontend.h"
#include <cstdio>
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include "../../MMCore/Error.h"
#include <sstream>
#include <FreeImagePlus.h>

/* FreeImage error callback */
CCameraFrontend* FreeImageErrorCallback;
void FreeImageErrorHandler(FREE_IMAGE_FORMAT fif, const char *message); // FreeImage error handler

using namespace std;

// External names used used by the rest of the system
// to load particular device from the "DemoCamera.dll" library

const char* g_CameraDeviceName = SIMPLECAM_DEVICENAME;
const char* g_CameraDeviceDescription = SIMPLECAM_DESCRIPTION;

// constants for naming pixel types (allowed values of the "PixelType" property)
const char* g_PixelType_Gray = "Grayscale";
const char* g_PixelType_Color = "Color";

// constants for camera and shutter speed types
const char* g_CameraName_NotConnected = "Camera not connected";
const char* g_ShutterSpeed_NotSet = "Shutter speed not set";

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

#ifdef _SIMPLECAM_GPHOTO_

// define gphoto callback for logging
static void gphoto2_logger(GPLogLevel level, const char *domain, const char *format, va_list args, void *data);
static int gphoto2_log_id = 0;

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
      return new CCameraFrontend();
   }
 
   // ...supplied name not recognized
   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
// CCameraFrontend implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~

/**
* CCameraFrontend constructor.
* Setup default all variables and create device properties required to exist
* before intialization. In this case, no such properties were required. All
* properties will be created in the Initialize() method.
*
* As a general guideline Micro-Manager devices do not access hardware in the
* the constructor. We should do as little as possible in the constructor and
* perform most of the initialization in the Initialize() method.
*/
CCameraFrontend::CCameraFrontend() :
   CCameraBase<CCameraFrontend> (),
   initialized_(false),
   grayScale_(true),
   bitDepth_(8),
   keepOriginals_(false),
   cameraSupportsLiveView_(false),
   imgBinning_(1),
   imgGrayScale_(false),
   imgBitDepth_(8),
   roiX_(0),
   roiY_(0),
   roiXSize_(0),
   roiYSize_(0),
   originX_(0),
   originY_(0)
{
   // call the base class method to set-up default error codes/messages
   InitializeDefaultErrorMessages();
   // add our specific error messages
   SetErrorText(ERR_CAM_BAD_PARAM, "Parameter value wrong");
   SetErrorText(ERR_CAM_NOT_CONNECTED, "Camera is not connected");
   SetErrorText(ERR_CAM_CONNECT_FAIL, "Connecting to camera failed");
   SetErrorText(ERR_CAM_SHUTTERSPEED_FAIL, "Setting shutter speed failed");
   SetErrorText(ERR_CAM_SHUTTER, "Error releasing shutter e.g. AF failure");
   SetErrorText(ERR_CAM_NO_IMAGE, "No image found. Image saved on CF card?");
   SetErrorText(ERR_CAM_CONVERSION, "Image load failure");
   SetErrorText(ERR_CAM_CONVERSION, "Image conversion failure");
   SetErrorText(ERR_CAM_UNKNOWN, "Unexpected return status");
   // Initialize FreeImage
   FreeImage_Initialise();
   FreeImageErrorCallback = this;
   FreeImage_SetOutputMessage(FreeImageErrorHandler);
}

/**
* CCameraFrontend destructor.
* If this device is used as intended within the Micro-Manager system,
* Shutdown() will be always called before the destructor. But in any case
* we need to make sure that all resources are properly released even if
* Shutdown() was not called.
*/
CCameraFrontend::~CCameraFrontend()
{
   // End FreeImage
   FreeImage_SetOutputMessage(NULL);
   FreeImageErrorCallback = NULL;
   FreeImage_DeInitialise();
}

/**
* Obtains device name.
* Required by the MM::Device API.
*/
void CCameraFrontend::GetName(char* name) const
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
int CCameraFrontend::Initialize()
{
   if (initialized_)
      return DEVICE_OK;

   /* Log debug info */
   ostringstream msg;
   msg.str("");
   msg << "Using FreeImage " << FreeImage_GetVersion();
   LogMessage(msg.str(), false);

#ifdef _SIMPLECAM_GPHOTO_
   /* Log gphoto2 version info */
   msg.str("");
   msg << "Using gphoto2";

   const char **libgphoto2_version = gp_library_version(GP_VERSION_VERBOSE);
   if (libgphoto2_version && *libgphoto2_version)
   {
      msg << ", libgphoto2 " << *libgphoto2_version;
   }
   
   const char **libgphoto2_port_version = gp_port_library_version(GP_VERSION_VERBOSE);
   if (libgphoto2_port_version && *libgphoto2_port_version)
   {
      msg << ", libgphoto2_port " << *libgphoto2_port_version;
   }
   LogMessage(msg.str(), false);

   /* Switch on gphoto2 logging */
   gphoto2_log_id = gp_log_add_func(GP_LOG_DEBUG, gphoto2_logger, this);

#endif

   // set property list
   // -----------------

   vector<string> booleanValues;
   booleanValues.push_back("0");
   booleanValues.push_back("1"); 

   // Name
   int nRet = CreateProperty(MM::g_Keyword_Name, g_CameraDeviceName, MM::String, true);
   if (DEVICE_OK != nRet)
      return nRet;

   // Description
   char desc[] = SIMPLECAM_DESCRIPTION;

   nRet = CreateProperty(MM::g_Keyword_Description, desc, MM::String, true);
   assert(nRet == DEVICE_OK);

   // CameraID
   nRet = CreateProperty(MM::g_Keyword_CameraID, "V1.0", MM::String, true);
   assert(nRet == DEVICE_OK);

   // binning
   CPropertyAction *pAct = new CPropertyAction (this, &CCameraFrontend::OnBinning);
   nRet = CreateProperty(MM::g_Keyword_Binning, "1", MM::Integer, false, pAct);
   assert(nRet == DEVICE_OK);

   nRet = SetAllowedBinning();
   if (nRet != DEVICE_OK)
      return nRet;

   // pixel type
   pAct = new CPropertyAction (this, &CCameraFrontend::OnPixelType);
   nRet = CreateProperty(MM::g_Keyword_PixelType, g_PixelType_Color, MM::String, false, pAct);
   assert(nRet == DEVICE_OK);

   vector<string> pixelTypeValues;
   pixelTypeValues.push_back(g_PixelType_Gray);
   pixelTypeValues.push_back(g_PixelType_Color); 

   nRet = SetAllowedValues(MM::g_Keyword_PixelType, pixelTypeValues);
   assert(nRet == DEVICE_OK);

   // Keep original images
   pAct = new CPropertyAction (this, &CCameraFrontend::OnKeepOriginals);
   nRet = CreateProperty(g_Keyword_KeepOriginals, "0", MM::Integer, false, pAct);
   assert(nRet == DEVICE_OK);
   nRet = SetAllowedValues(g_Keyword_KeepOriginals, booleanValues);
   assert(nRet == DEVICE_OK);

   // Bit depth of images
   pAct = new CPropertyAction (this, &CCameraFrontend::OnBitDepth);
   nRet = CreateProperty(g_Keyword_BitDepth, "8", MM::Integer, false, pAct);
   assert(nRet == DEVICE_OK);

   vector<string> bitDepthValues;
   bitDepthValues.push_back("8");
   bitDepthValues.push_back("16"); 

   nRet = SetAllowedValues(g_Keyword_BitDepth, bitDepthValues);
   assert(nRet == DEVICE_OK);
   
   // Exposure
   nRet = CreateProperty(MM::g_Keyword_Exposure, "0.0", MM::Float, false);
   assert(nRet == DEVICE_OK);

   // Shutter Speed
   nRet = CreateProperty(g_Keyword_ShutterSpeed, g_ShutterSpeed_NotSet, MM::String, false);
   assert(nRet == DEVICE_OK);
   nRet = AddAllowedValue(g_Keyword_ShutterSpeed, g_ShutterSpeed_NotSet);
   assert(nRet == DEVICE_OK);

   // Keep original images
   nRet = CreateProperty(g_Keyword_TrackExposure, "0", MM::Integer, false);
   assert(nRet == DEVICE_OK);
   nRet = SetAllowedValues(g_Keyword_TrackExposure, booleanValues);
   assert(nRet == DEVICE_OK);

   // CameraName
   pAct = new CPropertyAction (this, &CCameraFrontend::OnCameraName);
   nRet = CreateProperty(MM::g_Keyword_CameraName, g_CameraName_NotConnected, MM::String, false, pAct);
   assert(nRet == DEVICE_OK);
   string defaultCameraName;
   nRet = SetAllowedCameraNames(defaultCameraName);
   assert(nRet == DEVICE_OK);

   // synchronize all properties
   // --------------------------
   nRet = UpdateStatus();

   // initialize image buffer
   img_.Resize(640, 480, 1); // imgGrayScale_(true), imgBitDepth_(8) implies 8bit per pixel.

   /* Finally, connect to default camera */
   SetProperty(MM::g_Keyword_CameraName, defaultCameraName.c_str());

   LogMessage("Initialized", true);

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
int CCameraFrontend::Shutdown()
{

#ifdef _SIMPLECAM_GPHOTO_
   /* Switch off gphoto2 logging */
   gp_log_remove_func(gphoto2_log_id);
#endif

   initialized_ = false;
   return DEVICE_OK;
}

/**
* Performs exposure and grabs a single image.
* This function should block during the actual exposure and return immediately afterwards 
* (i.e., before readout).  This behavior is needed for proper synchronization with the shutter.
* Required by the MM::Camera API.
*/
int CCameraFrontend::SnapImage()
{
   if (!cam_.isConnected())
      return ERR_CAM_NOT_CONNECTED;

   /* set the shutter speed */
   SetCameraShutterSpeed();
   
   /* take a picture and load image into micro-manager buffer */
   int nRet;
   if (UseCameraLiveView())
      nRet = LoadImage(cam_.capturePreview()); // live viewfinder image
   else
      nRet = LoadImage(cam_.captureImage()); // high-resolution picture

   /* Check error conditions */
   if (nRet == DEVICE_OK)
      return DEVICE_OK;

   img_.ResetPixels();
   return ERR_CAM_SHUTTER;
}


/**
* Returns pixel data.
* Required by the MM::Camera API.
* The calling program will assume the size of the buffer based on the values
* obtained from GetImageBufferSize(), which in turn should be consistent with
* values returned by GetImageWidth(), GetImageHeight() and GetImageBytesPerPixel().
* The calling program allso assumes that camera never changes the size of
* the pixel buffer on its own. In other words, the buffer can change only if
* appropriate properties are set (such as binning, pixel type, etc.)
*/
const unsigned char* CCameraFrontend::GetImageBuffer()
{
   return img_.GetPixels();
}

/**
* Returns pixel data with interleaved RGB pixels in 32 bpp format
*/

const unsigned int* CCameraFrontend::GetImageBufferAsRGB32()
{
    return (const unsigned int*)GetImageBuffer();
}

/**
* Returns the number of channels in this image. This is '1' for grayscale cameras, and '4' for RGB cameras. 
*/
unsigned int CCameraFrontend::GetNumberOfComponents() const
{
   if (imgGrayScale_)
      return 1; // grayscale
   else
      return 4; // rgb
}

/**
* Returns the name for each channel. 
*/
int CCameraFrontend::GetComponentName(unsigned int channel, char* name)
{
   if (imgGrayScale_)
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
unsigned CCameraFrontend::GetImageWidth() const
{
   return img_.Width();
}

/**
* Returns image buffer Y-size in pixels.
* Required by the MM::Camera API.
*/
unsigned CCameraFrontend::GetImageHeight() const
{
   return img_.Height();
}

/**
* Returns image buffer pixel depth in bytes.
* Required by the MM::Camera API.
*/
unsigned CCameraFrontend::GetImageBytesPerPixel() const
{
   return img_.Depth();
} 

/**
* Returns the bit depth (dynamic range) of the pixel.
* This does not affect the buffer size, it just gives the client application
* a guideline on how to interpret pixel values.
* Required by the MM::Camera API.
*/
unsigned CCameraFrontend::GetBitDepth() const
{
   return imgBitDepth_;
}

/**
* Returns the size in bytes of the image buffer.
* Required by the MM::Camera API.
*/
long CCameraFrontend::GetImageBufferSize() const
{
   return img_.Width() * img_.Height() * img_.Depth();
}

/**
* Sets the camera Region Of Interest.
* Required by the MM::Camera API.
* This command will change the dimensions of the image.
* Depending on the hardware capabilities the camera may not be able to configure the
* exact dimensions requested - but should try do as close as possible.
* If the hardware does not have this capability the software should simulate the ROI by
* appropriately cropping each frame.
* 
*  x - top-left corner coordinate
*  y - top-left corner coordinate
*  xSize - width
*  ySize - height
*/
int CCameraFrontend::SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize)
{
   if (imgBinning_ == 0)
      imgBinning_ = 1;
   roiX_ = originX_ + x * imgBinning_;
   roiY_ = originY_ + y * imgBinning_;
   roiXSize_ = xSize * imgBinning_;
   roiYSize_ = ySize * imgBinning_;
   return DEVICE_OK;
}

/**
* Returns the actual dimensions of the current ROI.
* Required by the MM::Camera API.
*/
int CCameraFrontend::GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize)
{
   if (imgBinning_ == 0)
      imgBinning_ = 1;
   x = roiX_ / imgBinning_;
   y = roiY_ / imgBinning_;
   xSize = roiXSize_ / imgBinning_;
   ySize = roiYSize_ / imgBinning_;
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
int CCameraFrontend::ClearROI()
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
double CCameraFrontend::GetExposure() const
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
void CCameraFrontend::SetExposure(double exposure_ms)
{
   SetProperty(MM::g_Keyword_Exposure, CDeviceUtils::ConvertToString(exposure_ms));
   
   /* If TrackExposure is set, shutter speed tracks exposure */
   if (GetBoolProperty(g_Keyword_TrackExposure))
      SetShutterSpeed(exposure_ms);

   OnPropertiesChanged();
}

/**
* Returns the current binning factor.
* Required by the MM::Camera API.
*/
int CCameraFrontend::GetBinning() const
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
int CCameraFrontend::SetBinning(int binFactor)
{
   int ret = SetProperty(MM::g_Keyword_Binning, CDeviceUtils::ConvertToString(binFactor));
   OnPropertiesChanged();
   return ret;
}

int CCameraFrontend::SetAllowedBinning() 
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
// CCameraFrontend Action handlers
///////////////////////////////////////////////////////////////////////////////

/**
* Handles "Binning" property.
*/
int CCameraFrontend::OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct)
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
int CCameraFrontend::OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct)
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
int CCameraFrontend::OnKeepOriginals(MM::PropertyBase* pProp, MM::ActionType eAct)
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

/**
* Handles "BitDepth" property.
*/
int CCameraFrontend::OnBitDepth(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   int ret = DEVICE_ERR;
   switch(eAct)
   {
   case MM::AfterSet:
      {
         string bitDepth;
         pProp->Get(bitDepth);

         if (bitDepth.compare("8") == 0)
         {
            bitDepth_ = 8;
            ret=DEVICE_OK;
         }
         else if (bitDepth.compare("16") == 0)
         {
            bitDepth_ = 16;
            ret=DEVICE_OK;
         }
         else
         {
            // on error switch to default
            pProp->Set("8");
            bitDepth_ = 8;
            ret = ERR_CAM_BAD_PARAM;
         }
         OnPropertiesChanged();
      } break;
   case MM::BeforeGet:
      {
         if (bitDepth_ == 16)
            pProp->Set("16");
         else
            pProp->Set("8");
         ret=DEVICE_OK;
      }break;
   }
   return ret; 
}

/*
* Handles "CameraName" property.
*/
int CCameraFrontend::OnCameraName(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   int ret = DEVICE_ERR;
   switch(eAct)
   {
   case MM::AfterSet:
      {
         if(IsCapturing())
            return DEVICE_CAMERA_BUSY_ACQUIRING;

         string cameraName;
         pProp->Get(cameraName);
         UnEscapeValue(cameraName);

         /* Disconnect current camera */
         cam_.disconnectCamera();
         /* Reset list of shutter speeds */
         SetAllowedShutterSpeeds();

         if (cameraName.compare(g_CameraName_NotConnected) == 0)
         {
            ret = DEVICE_OK; // Do nothing
         }
         else
         {
            /* Connect new camera */
            if (cam_.connectCamera(cameraName))
            {
               /* First take an image; then read shutter speeds. 
                * Some camera models require taking a picture before accessing settings */
               ret = SnapImage();
               /* update shutter speeds */
               SetAllowedShutterSpeeds();
               /* check whether camera supports Live View */
               DetectCameraLiveView();
            }
            else
               ret = ERR_CAM_CONNECT_FAIL;
         }
         if (initialized_)
            OnPropertiesChanged();
      }
      break;
   case MM::BeforeGet:
      {
         // the user is requesting the current value for the property, so either ask the 'hardware' 
         // or let the system return the value cached in the property.

         ret = DEVICE_OK;
      }
      break;
   }
   return ret; 
}

///////////////////////////////////////////////////////////////////////////////
// Private CCameraFrontend methods
///////////////////////////////////////////////////////////////////////////////


/* detect available cameras */
int CCameraFrontend::SetAllowedCameraNames(string& defaultCameraName)
{
   vector<string> cameraNameList;
   if (cam_.listCameras(cameraNameList))
   {
      // If list contains only one camera, connect to that camera.
      if (cameraNameList.size() == 1)
         defaultCameraName = cameraNameList[0];
      else  
         defaultCameraName = g_CameraName_NotConnected;
   
      /* Add no-op setting ("Camera is not connected") */
      cameraNameList.push_back(g_CameraName_NotConnected);
      /* Set allowed values to list of detected cameras. */
      EscapeValues(cameraNameList);
      int ret = ClearAllowedValues(MM::g_Keyword_CameraName);
      assert(ret == DEVICE_OK);
      ret = SetAllowedValues(MM::g_Keyword_CameraName, cameraNameList);
      assert(ret == DEVICE_OK);
      LogMessage("Reloaded camera names", true);
   }
   else
   {
      // No cameras found.
      int ret = ClearAllowedValues(MM::g_Keyword_CameraName);
      assert(ret == DEVICE_OK);
      defaultCameraName = g_CameraName_NotConnected;
      LogMessage("No cameras detected", true);
   }

   // Log list of cameras
   ostringstream msg;
   for (int i = 0; i < GetNumberOfPropertyValues(MM::g_Keyword_CameraName); i++)
   {
      char value[MM::MaxStrLength];
      if (GetPropertyValueAt(MM::g_Keyword_CameraName, i, value))
      {
         msg.str("");
         msg << "Camera " << i << ": '" << value << "'";
         LogMessage(msg.str(), true);
      }
   }

   return DEVICE_OK; 
}

/* Obtain list of available shutter speeds */
int CCameraFrontend::SetAllowedShutterSpeeds()
{
   vector<string> shutterSpeedList;
   if (cam_.isConnected() && cam_.listShutterSpeeds(shutterSpeedList))
   {
      EscapeValues(shutterSpeedList);
      ClearAllowedValues(g_Keyword_ShutterSpeed);

      /* Add no-op setting ("Shutter speed not set") */
      shutterSpeedList.push_back(g_ShutterSpeed_NotSet);

      int ret = SetAllowedValues(g_Keyword_ShutterSpeed, shutterSpeedList);
      assert(ret == DEVICE_OK);

      LogMessage("Reloaded shutter speeds", true);
   }
   else
   {
      ClearAllowedValues(g_Keyword_ShutterSpeed);
      shutterSpeedList.push_back(g_ShutterSpeed_NotSet);
      int ret = SetAllowedValues(g_Keyword_ShutterSpeed, shutterSpeedList);
      assert(ret == DEVICE_OK);
      /* Shutter speed is undefined */
      ret = SetProperty(g_Keyword_ShutterSpeed, g_ShutterSpeed_NotSet);
      assert(ret == DEVICE_OK);
      LogMessage("Could not get list of shutter speeds", true);
   }

   // Log list of shutter speeds
   ostringstream msg;
   for (int i = 0; i < GetNumberOfPropertyValues(g_Keyword_ShutterSpeed); i++)
   {
      char value[MM::MaxStrLength];
      if (GetPropertyValueAt(g_Keyword_ShutterSpeed, i, value))
      {
         msg.str("");
         msg << "Shutter speed " << i << ": '" << value << "'";
         LogMessage(msg.str(), true);
      }
   }

   if (initialized_)
      OnPropertiesChanged();

   return DEVICE_OK;
}

/* Derive shutter speed from micro-manager "Exposure" property */
int CCameraFrontend::SetShutterSpeed(double exposure_ms)
{
   string bestShutterSpeed = g_ShutterSpeed_NotSet;
   double bestError = 2.0 * exposure_ms;
   /* Loop over all shutter speeds, and choose closest approximation. */
   for (int i = 0; i < GetNumberOfPropertyValues(g_Keyword_ShutterSpeed); i++)
   {
      char currShutterSpeedChar[MM::MaxStrLength];
      if (!GetPropertyValueAt(g_Keyword_ShutterSpeed, i, currShutterSpeedChar))
         continue;
      string currShutterSpeed = currShutterSpeedChar;
      if (currShutterSpeed.compare(g_ShutterSpeed_NotSet) == 0)
         continue;
      /* convert to milliseconds */
      double currShutterSpeed_ms = ShutterSpeedToMs(currShutterSpeed);
      /* discard shutter speeds which are too small or too big */
      if (currShutterSpeed_ms <= 0.0) 
         continue;
      if (currShutterSpeed_ms > 60000.0) 
         continue;
      /* calculate difference between exposure and shutter setting */
      double currError = currShutterSpeed_ms - exposure_ms;
      /* take absolute value */
      if (currError < 0.0) 
         currError = -currError;
      /* check whether this setting is an improvement */
      if (currError < bestError)
         {
            bestShutterSpeed = currShutterSpeed;
            bestError = currError;
         }
   }
   
   if (bestShutterSpeed.compare(g_ShutterSpeed_NotSet) == 0)
      return DEVICE_OK;

   int ret = SetProperty(g_Keyword_ShutterSpeed, bestShutterSpeed.c_str());
   assert(ret == DEVICE_OK);

   // Log new shutter speed
   ostringstream msg;
   msg.str("");
   msg << "Exposure " << exposure_ms << " ms. Shutter speed set to '" << bestShutterSpeed << "' ";
   LogMessage(msg.str(), true);
   return DEVICE_OK;
}

/* set the cameras' shutter speed */
int CCameraFrontend::SetCameraShutterSpeed()
{
   char value[MM::MaxStrLength];
   GetProperty(g_Keyword_ShutterSpeed, value);
   string shutterSpeed = value;
   UnEscapeValue(shutterSpeed);
   
   if (shutterSpeed.compare(g_ShutterSpeed_NotSet) == 0)
      return DEVICE_OK;

   if (!cam_.isConnected())
      return ERR_CAM_NOT_CONNECTED;

   bool success = cam_.setShutterSpeed(shutterSpeed);
   // Log shutter speed
   ostringstream msg;
   msg.str("");
   msg << "Set camera shutter speed to '" << shutterSpeed << "' ";
   if (success)
      msg << "success";
   else
      msg << "fail";
   LogMessage(msg.str(), true);
   return success;
}

/* Convert a shutter speed from text to a value in milliseconds. 
   e.g. 1/20 and  0.050s both convert to 50 (milliseconds) */
double CCameraFrontend::ShutterSpeedToMs(string shutterSpeed)
{
   double numerator = 0.0;
   double denominator = 0.0;
   double speed_ms = -1.0;
   char slash;

   istringstream shutterString;
   shutterString.str(shutterSpeed);
   
   if ((shutterString >> numerator) && (numerator > 0))
   {
      if ((shutterString >> slash) && (slash == '/') && (shutterString >> denominator) && (denominator > 0))
         speed_ms = 1000.0 * numerator / denominator; /* speeds such as 1/125 */
      else
         speed_ms = 1000.0 * numerator; /* speeds such as 2.5 */
   }
   
   return speed_ms;
}

/*
 * DetectCameraLiveView();
 * Check whether the camera supports capturing the live viewfinder image.
 * set cameraSupportsLiveView_ variable.
 */

int CCameraFrontend::DetectCameraLiveView()
{
   /* try to acquire a live viewfinder image */
   cameraSupportsLiveView_ = cam_.capturePreview().isValid();
   if (cameraSupportsLiveView_)
      LogMessage("Camera with live viewfinder support detected", false);
   else
      LogMessage("Camera without live viewfinder support", false);
   return DEVICE_OK;
}

/*
 * InLiveMode():
 * true if micro-manager is in "Live" mode.
 */
bool CCameraFrontend::InLiveMode()
{
   /* Live View is called as StartSequenceAcquisition(LONG_MAX, 0.0, false); 
    * See whether we're in live view mode by checking acquisition is running (IsCapturing() == true) and 
    * StartSequenceAcquisition parameters are numImages == LONG_MAX, interval_ms == 0.0, stopOnOverflow == false.
    */
   bool inLiveMode = IsCapturing() && (thd_->GetLength() == LONG_MAX) && (thd_->GetIntervalMs() == 0.0) && (stopOnOverflow_ == false);

   return inLiveMode;
}

/*
 * UseCameraLiveView();
 * Decide when to use live viewfinder image and when to use normal image.
 * true if we need to use the live viewfinder image;
 * false if we need to use the high-resolution image.
 */

bool CCameraFrontend::UseCameraLiveView()
{
   /* Use live view if the camera supports live view and micro-manager is in "Live View" mode. */
   bool useCameraLiveView =  cameraSupportsLiveView_ && InLiveMode();
//   useCameraLiveView = true;
   return useCameraLiveView;
}

/*
 * EscapeValues
 * micro-manager does not accept values with a comma "," in them, 
 * as configurations are stored in comma-separated value format, and commas would cause problems.
 * Escapevalues escapes all commas in a vector of strings.
 */

void CCameraFrontend::EscapeValues(vector<string>& valueList)
{
   for(int i = 0; i < valueList.size(); i++)
   {
      string newValue = "";
      string value = valueList[i];
      string::iterator p;

      p = value.begin();
      while (p != value.end())
      {
         if (*p == '\\') newValue += "\\\\";       /* escape backslashes: \ -> \\ */
         else if (*p == ',') newValue += "\\;";    /* escape commas:      , -> \; */
         else newValue += *p;                      /* pass-through */
         p++;
      }
      valueList[i] = newValue;
   }
}

/*
 * UnEscapeValues
 * Inverse of EscapeValues. Removes all escapes and Restores all commas in a string.
 */

void CCameraFrontend::UnEscapeValue(string& value)
{
   string newValue = "";
   string::iterator p;
   bool escaped = false;

   p = value.begin();
   while (p != value.end())
   {
      if (escaped)
      {
         escaped = false;
         if (*p == ';')
            newValue += ',';
         else
            newValue += *p;
      }
      else
      {
         escaped = (*p == '\\');
         if (!escaped)
            newValue += *p;
      }
      p++;
   }
   value = newValue;
}

/*
 * Read a property, and return true if non-zero.
 */

bool CCameraFrontend::GetBoolProperty(const char* const propName)
{
   char val[MM::MaxStrLength];
   bool boolVal;

   int ret = this->GetProperty(propName, val);
   assert(ret == DEVICE_OK);
   boolVal = strcmp(val, "1") == 0 ? true : false;
   return boolVal;
}

/*
 * Load image file from disk to img_ buffer
 */ 

/* for older versions of libfreeimage (3.15.0 or previous) */
#ifndef RAW_HALFSIZE
#define RAW_HALFSIZE 0
#endif 
#ifndef RAW_DISPLAY
#define RAW_DISPLAY 0
#endif 
#ifndef RAW_DEFAULT
#define RAW_DEFAULT 0
#endif 

/* Load frame buffer from a file  */
int CCameraFrontend::LoadImage(string imageFilename)
{
   fipImage frameBitmap;   /* last captured frame */
   int flags = 0;          /* image decoding flags */
   int nRet;               /* return code */

   /* Check for an empty filename. Usually indicates the image was saved to compact flash */
   if (imageFilename.compare("") == 0)
      return ERR_CAM_NO_IMAGE;

   /* set image decoding flags */
   if (bitDepth_ == 8)
      flags = RAW_DISPLAY | RAW_HALFSIZE; /* 24bpp, gamma 2.2 */
   else
      flags = RAW_DEFAULT | RAW_HALFSIZE; /* 48bpp, linear gamma, no color interpolation */

   bool rc = frameBitmap.load(imageFilename.c_str(), flags); // Load image from disk
   
   if (rc)
      nRet = LoadImage(frameBitmap); // store image in micro-manager buffer
   else
      nRet = ERR_CAM_LOAD;

   /* If keepOriginals is set, keep the original raw bitmap as downloaded from camera. */
   if (!keepOriginals_)
      remove(imageFilename.c_str());

   return nRet;
}

/* Load frame buffer from a bitmap  */
int CCameraFrontend::LoadImage(fipImage frameBitmap)
{
   /* Log debug message */
   ostringstream msg;
   msg.str("");
   msg << "Before processing: " << frameBitmap.getWidth() << " x " << frameBitmap.getHeight() << " pixels, " << frameBitmap.getBitsPerPixel() << " bpp, type " << frameBitmap.getImageType();
   LogMessage(msg.str(), true);

   /* binning: scale image down */
   bool rc = true;
   unsigned int binning = GetBinning();

   if (binning <= 0)
   {
      LogMessage("Error: Binning value", true);
      rc = false;
   }

   if (rc)
   {
      unsigned int frameWidth = frameBitmap.getWidth();
      unsigned int frameHeight = frameBitmap.getHeight();
      unsigned int scaledWidth = frameWidth / binning;
      unsigned int scaledHeight = frameHeight / binning;
      rc = frameBitmap.rescale(scaledWidth, scaledHeight, FILTER_BILINEAR);
   }


   /* Transpose */
   if (rc)
   {
      if (GetBoolProperty(MM::g_Keyword_Transpose_Correction) && GetBoolProperty(MM::g_Keyword_Transpose_SwapXY))
         rc = frameBitmap.rotate(90);
   }
   if (rc)
   {
      if (GetBoolProperty(MM::g_Keyword_Transpose_Correction) && GetBoolProperty(MM::g_Keyword_Transpose_MirrorX))
         rc = frameBitmap.flipHorizontal();
   }
   if (rc)
   {
      if (GetBoolProperty(MM::g_Keyword_Transpose_Correction) && GetBoolProperty(MM::g_Keyword_Transpose_MirrorY))
         rc = frameBitmap.flipVertical();
   }

   /* Region Of Interest */
   if (rc)
   {
      // Apply binning to region of interest
      unsigned int transposedWidth = frameBitmap.getWidth();
      unsigned int transposedHeight = frameBitmap.getHeight();
      unsigned int clipX = roiX_ / binning;
      unsigned int clipY = roiY_  / binning;
      unsigned int clipHeight = roiYSize_  / binning;
      unsigned int clipWidth = roiXSize_  / binning;

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
      originX_ = clipX * binning;
      originY_ = clipY * binning;

      rc = frameBitmap.crop(clipX, clipY + clipHeight, clipX + clipWidth, clipY);
      }

   /* change to 8bpp grayscale, 16bpp grayscale, 32bpp rgb or 64bpp rgb */
   unsigned int bytesPerPixel = 1;

   if (rc)
   {
      if (grayScale_) 
      {
         if (bitDepth_ == 8)
         {
            rc = frameBitmap.convertTo8Bits();
            bytesPerPixel = 1;
         }
         else if (bitDepth_ == 16)
         {
            rc = frameBitmap.convertToType(FIT_UINT16);
            bytesPerPixel = 2;
            if (!rc)
            {
               /* Conversion to 16bpp failed. Try 8bpp. */
               rc = frameBitmap.convertTo8Bits();
               bytesPerPixel = 1;
            }
         }
         else
         {
            LogMessage("grayscale bitdepth not implemented", true);
            rc = false;
         }
      }
      else
      {  
         if (bitDepth_ == 8)
         {
            rc = frameBitmap.convertTo32Bits();
            bytesPerPixel = 4;
         }
         else if (bitDepth_ == 16)
         {
            rc = frameBitmap.convertToType(FIT_RGB16);
            bytesPerPixel = 8;
            if (!rc)
            {
               /* Conversion to 16bpp failed. Try 8bpp. */
               rc = frameBitmap.convertTo32Bits();
               bytesPerPixel = 4;
            }
         }
         else
         {
            LogMessage("color bitdepth not implemented", true);
            rc = false;
         }
      }
   }


   /* Copy frameBitmap to micro-manager image buffer img_ */
   if (rc)
   {
      /* Check whether image dimension changed */
      bool sizeChanged = (img_.Width() != frameBitmap.getWidth()) || (img_.Height() != frameBitmap.getHeight()) || (img_.Depth() != bytesPerPixel);

      /* Resize image buffer */
      img_.Resize(frameBitmap.getWidth(), frameBitmap.getHeight(), bytesPerPixel);

      /* If image dimension changed resize circular buffer as well */
      if (sizeChanged)
      {
         int numberOfComponents;
         if (grayScale_)
            numberOfComponents = 1;
         else
            numberOfComponents = 4;
         GetCoreCallback()->InitializeImageBuffer(numberOfComponents, 1, frameBitmap.getWidth(), frameBitmap.getHeight(), bytesPerPixel);
      }

      /* Micro-manager expects 16-bit color images as 64bpp bgra. Convert 48bpp rgb to 64bpp bgra. */
      if (bytesPerPixel == 8)
      {
         /* convert RGB16 to BGRA16 */
         fipImage newBitmap(FIT_RGBA16, frameBitmap.getWidth(), frameBitmap.getHeight(), frameBitmap.getBitsPerPixel()); /* empty bitmap for conversion results */
         for (unsigned int y = 0; y < frameBitmap.getHeight(); y++)
         {
            FIRGB16 *srcBits = (FIRGB16 *)frameBitmap.getScanLine(y);
            FIRGBA16 *dstBits = (FIRGBA16 *)newBitmap.getScanLine(y);
            for (unsigned int x = 0; x < frameBitmap.getWidth(); x++)
            {
               /* swap red and blue, and add alpha */
               dstBits[x].red = srcBits[x].blue;
               dstBits[x].green = srcBits[x].green;
               dstBits[x].blue = srcBits[x].red;
               dstBits[x].alpha = 0;
            }
         }
         frameBitmap = newBitmap;
      }

      /* Log status */
      msg.str("");
      msg << "After processing: " << frameBitmap.getWidth() << " x " << frameBitmap.getHeight() << " pixels, " << frameBitmap.getBitsPerPixel() << " bpp, type " << frameBitmap.getImageType();
      LogMessage(msg.str(), true);

      /* copy the bitmap to the image buffer */
      FreeImage_ConvertToRawBits(img_.GetPixelsRW(), frameBitmap, frameBitmap.getWidth() * bytesPerPixel, bytesPerPixel * 8, 0, 0, 0, true); 
   }

   /* save current image parameters */
   if (rc)
   {
      imgBinning_ = GetBinning();
      imgGrayScale_ = grayScale_;
      imgBitDepth_ = bitDepth_;
      return DEVICE_OK;
   }
  
   /* set image to black */
   img_.ResetPixels(); 
   return ERR_CAM_CONVERSION;
}

/*
 * FreeImage error handler 
 * log FreeImage error messages to micro-manager CoreLog
 *
 * fif: Format / Plugin responsible for the error 
 * message: Error message 
 */ 
void FreeImageErrorHandler(FREE_IMAGE_FORMAT fif, const char *message) 
{ 
   ostringstream msg;
   msg.str("");
   msg << "FreeImage: "; 
   if(fif != FIF_UNKNOWN)  
      msg << FreeImage_GetFormatFromFIF(fif); 
   msg << message;
   if (FreeImageErrorCallback)
      FreeImageErrorCallback->LogMessage(msg.str(), false);
} 

#ifdef _SIMPLECAM_GPHOTO_
/*
 * Gphoto2 logging callback
 * log gphoto 2 debug and error messages to micro-manager CoreLog
 */
static void gphoto2_logger(GPLogLevel level, const char *domain, const char *format, va_list args, void *data) 
{
   ostringstream msg;
   msg.str("");

   CCameraFrontend* thisCam;
   thisCam = (CCameraFrontend*)data;

   if (domain)
      msg << domain << ": ";

   char *ret;
   int len = vasprintf(&ret, format, args);
   assert(len >= 0);
   if (ret)
   {
      msg << ret;
      free(ret);
   }

   if (thisCam)
   {
      if (level == GP_LOG_ERROR)
         thisCam->LogMessage(msg.str(), false); // Error messages
      else
         thisCam->LogMessage(msg.str(), true); // Debug messages
   }
   return;
}
#endif

// not truncated
