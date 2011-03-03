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
// - With DSLRRemote still running, start up Micro-Manager. 
// - Create a hardware config consisting of DSLRRemoteCam, Demo Shutter, and Demo Stage.
// - Open the property browser, and select ImageDecoder "Micro-Manager".
// - Click the micro-manager "Snap" button to take a picture. Check the picture appears in the micro-manager "Live" window.
// - If the camera supports jpeg and raw, take pictures in both formats. 
// - Alternatively test using a Nikon DSLR camera, NKRemote software and NKRemoteCam driver.
//

// Flow:
//
// SnapImage
//    SetCameraExposure
//       SetShutterAperture           [breezesys, set exposure time]
//    AcquireFrame
//       ReleaseShutter               [breezesys, take picture]
//       LoadWICImage                 [load image using WIC]
//          CreateDecoderFromFilename [WIC, decode image file]
//          CreateBitmapFromSource    [WIC, store decoded bitmap]
//          LogWICMessage             [log WIC messages to micro-manager corelog]
//       LoadRawImage                 [load image using libraw]
//          dcraw_process             [libraw, decode image file]
//          dcraw_make_mem_image      [libraw, store decoded bitmap]
//          LibrawProgressCallback    [log libraw messages to micro-manager corelog]
//          LogRawWarnings            [log libraw warnings to micro-manager corelog]
//          CreateBitmap              [WIC, create empty bitmap, 48bpp RGB]
//          CopyMemory                [copy decoded bitmap to WIC bitmap]
//    ResizeImageBuffer
//       CreateBitmapScaler           [WIC, binning]
//       CreateBitmapFlipRotator      [WIC, transposing]
//       CreateBitmapClipper          [WIC, clip to region of interest]
//       CreateFormatConverter        [WIC, convert to grayscale/color, 8/16bpp]
//       CopyPixels                   [WIC, copy to micro-manager image buffer]
//       Convert64bppRGBAto64bppBGRA  [swap R and B channels for 64bpp color]
//

//
// Note:
// With hindsight, Windows Imaging Component could have been replaced with os-independent open source libraries (e.g. freeimage), and the driver split in two:
// - an operating system and camera independent part, common to all
// - an operating system and camera dependent part, which has a single method: take a picture and return the filename of the picture.
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
#include <comdef.h>

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

// constants for naming image decoder types (allowed values of the "ImageDecoder" property)
const char* g_ImageDecoder_Windows = "Windows";
const char* g_ImageDecoder_Raw = "Raw";
const char* g_ImageDecoder_Raw_No_Gamma = "Raw (no gamma compensation)";
const char* g_ImageDecoder_Raw_No_White_Balance = "Raw (no gamma compensation; no white balance)";

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
   decoder_(decoder_windows),
   grayScale_(false),
   bitDepth_(8),
   keepOriginals_(false),
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
   SetErrorText(ERR_CAM_NO_IMAGE, "No image found. Image saved on CF card?");
   SetErrorText(ERR_CAM_NOT_RUNNING, "DSLRRemote/NKRemote/PSRemote is not running");
   SetErrorText(ERR_CAM_NOT_CONNECTED, "Camera is not connected");
   SetErrorText(ERR_CAM_BUSY, "Camera is busy");
   SetErrorText(ERR_CAM_TIMEOUT, "Timeout waiting for image to be saved");
   SetErrorText(ERR_CAM_SHUTTER, "Error releasing shutter e.g. AF failure");
   SetErrorText(ERR_CAM_UNKNOWN, "Unexpected return status");
   SetErrorText(ERR_CAM_LOAD, "Image load failure");
   SetErrorText(ERR_CAM_RAW, "Error decoding raw image");
   SetErrorText(ERR_CAM_CONVERSION, "Image conversion failure");
   SetErrorText(ERR_CAM_SHUTTER_SPEEDS, "Error in list of camera shutter speeds");
}

/**
* CTetheredCamera destructor.
* If this device is used as intended within the Micro-Manager system,
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

   // Bit depth of images
   pAct = new CPropertyAction (this, &CTetheredCamera::OnBitDepth);
   nRet = CreateProperty(g_Keyword_BitDepth, "8", MM::Integer, false, pAct);
   assert(nRet == DEVICE_OK);

   vector<string> bitDepthValues;
   bitDepthValues.push_back("8");
   bitDepthValues.push_back("16"); 

   nRet = SetAllowedValues(g_Keyword_BitDepth, bitDepthValues);
   if (nRet != DEVICE_OK)
      return nRet;

   // Image decoder
   pAct = new CPropertyAction (this, &CTetheredCamera::OnImageDecoder);
   nRet = CreateProperty(g_Keyword_ImageDecoder, g_ImageDecoder_Windows, MM::String, false, pAct);
   assert(nRet == DEVICE_OK);

   vector<string> imageDecoderValues;
   imageDecoderValues.push_back(g_ImageDecoder_Windows);
   imageDecoderValues.push_back(g_ImageDecoder_Raw);
   imageDecoderValues.push_back(g_ImageDecoder_Raw_No_Gamma);
   imageDecoderValues.push_back(g_ImageDecoder_Raw_No_White_Balance);

   nRet = SetAllowedValues(g_Keyword_ImageDecoder, imageDecoderValues);
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

   // initialize image buffer
   img_.Resize(640, 480, 4); // imgGrayScale_(false), imgBitDepth_(8) implies 8bit rgb, 4 bytes per pixel.

   // Log debug info
   ostringstream msg;
   msg.str("");
   msg << "Using LibRaw " <<  LibRaw::version();
   LogMessage(msg.str(), true);
   msg.str("");
   msg << "Camera: " << cameraName_;
   LogMessage(msg.str(), true);
                   
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
* values returned by GetImageWidth(), GetImageHeight() and GetImageBytesPerPixel().
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
   if (imgGrayScale_)
      return 1; // grayscale
   else
      return 4; // rgb
}

/**
* Returns the name for each channel. 
*/
int CTetheredCamera::GetComponentName(unsigned int channel, char* name)
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
   return imgBitDepth_;
}

/**
* Returns the size in bytes of the image buffer.
* Required by the MM::Camera API.
*/
long CTetheredCamera::GetImageBufferSize() const
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
int CTetheredCamera::SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize)
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
int CTetheredCamera::GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize)
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

/**
* Handles "BitDepth" property.
*/
int CTetheredCamera::OnBitDepth(MM::PropertyBase* pProp, MM::ActionType eAct)
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

/**
* Handles "ImageDecoder" property.
*/
int CTetheredCamera::OnImageDecoder(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   int ret = DEVICE_ERR;
   switch(eAct)
   {
   case MM::AfterSet:
      {
         if(IsCapturing())
            return DEVICE_CAMERA_BUSY_ACQUIRING;

         string imageDecoder;
         pProp->Get(imageDecoder);

         if (imageDecoder.compare(g_ImageDecoder_Raw) == 0)
         {
            decoder_ = decoder_raw;
            ret = DEVICE_OK;
         }
         else if (imageDecoder.compare(g_ImageDecoder_Raw_No_Gamma) == 0)
         {
            decoder_ = decoder_raw_no_gamma;
            ret = DEVICE_OK;
         }
         else if (imageDecoder.compare(g_ImageDecoder_Raw_No_White_Balance) == 0)
         {
            decoder_ = decoder_raw_no_white_balance;
            ret = DEVICE_OK;
         }
         else if (imageDecoder.compare(g_ImageDecoder_Windows) == 0)
         {
            decoder_ = decoder_windows;
            ret = DEVICE_OK;
         }
         else
         {
            // switch to default decoder type
            pProp->Set(g_ImageDecoder_Windows);
            decoder_ = decoder_windows;
            ret = ERR_CAM_BAD_PARAM;
         }
         OnPropertiesChanged();
      } break;
   case MM::BeforeGet:
      {
         switch (decoder_)
         {
            case decoder_raw:
               pProp->Set(g_ImageDecoder_Raw);
               break;
            case decoder_raw_no_gamma:
               pProp->Set(g_ImageDecoder_Raw_No_Gamma);
               break;
            case decoder_raw_no_white_balance:
               pProp->Set(g_ImageDecoder_Raw_No_White_Balance);
               break;
            case decoder_windows:
               pProp->Set(g_ImageDecoder_Windows);
               break;
            default:
               pProp->Set(g_ImageDecoder_Windows);
               break;
         }
         ret = DEVICE_OK;
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
 * 15;13;10;8;6;5;4;3.2;2.5;2;1.6;1.3;1;0.8;0.6;0.5;0.4;0.3;1/4;1/5;1/6;1/8;1/10;1/13;1/15;1/20;1/25;1/30;1/40;1/50;1/60;1/80;1/100;1/125;1/160;1/200;1/250;1/320;1/400;1/500;1/640;1/800;1/1000;1/1250;1/1600;1/2000
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
      {
         LogMessage("Shutter speeds syntax error", true);
         return ERR_CAM_SHUTTER_SPEEDS;
      }
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

   // Raw images downloaded from www.rawsamples.ch , used for testing. 
   // Uncomment one of the following to force the driver to load the file.
   // Note: if keepOriginals_ is false, the file will be deleted.
   //strcpy(filename, "C:\\RAW_NIKON_D3X.NEF");
   //strcpy(filename, "C:\\RAW_CANON_40D_RAW_V103.CR2");

   //Initialize COM.
   CoInitialize(NULL);

   //Create the COM imaging factory.
   IWICImagingFactory *factory = NULL;
   HRESULT hr = CoCreateInstance(CLSID_WICImagingFactory, NULL, CLSCTX_INPROC_SERVER, IID_IWICImagingFactory, (LPVOID*)&factory);

   if (FAILED(hr))
   {
      SafeRelease(factory);
      LogWICMessage(hr);
      return ERR_CAM_LOAD;     
   }

   int rc = 0;
   if (decoder_ == decoder_windows)
   {
      // Load image using the WIC Codecs 
      rc = LoadWICImage(factory, filename);
   }
   else
   {
      // Load image using libraw
      rc = LoadRawImage(factory, filename);
   }

   SafeRelease(factory);

   if (rc == DEVICE_OK)
   {
      // If keepOriginals is set, keep the original raw bitmap as downloaded from camera.
      if (!keepOriginals_)
      {
         remove(filename);
         LogMessage("Remove original done", true);
      }
   
      LogMessage("AcquireFrame done", true);
      return DEVICE_OK;
   }
       
   LogMessage("AcquireFrame fail", true);
   return ERR_CAM_LOAD;
}

/*
 * ResizeImage: Clip frameBitmap to the region of interest, apply binning, transpose and store the result in img_
 */

int CTetheredCamera::ResizeImageBuffer()
{
   // Sanity check
   if (frameBitmap == NULL)
      return DEVICE_OK;

   //Initialize COM.
   CoInitialize(NULL);

   //Create the COM imaging factory.
   IWICImagingFactory *factory = NULL;
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
   
   UINT binning = GetBinning();

   if (binning <= 0) 
   {
      LogMessage("Error: Binning value", true);
      return ERR_CAM_CONVERSION;
   }

   UINT scaleFactor = binning;
   // Reduce scale factor if libraw already has scaled the image by a factor of 2.
   if ((decoder_ != decoder_windows) && (rawProcessor_.imgdata.params.half_size == 1))
      scaleFactor = binning / 2;

   scaledWidth = frameWidth / scaleFactor;
   scaledHeight = frameHeight / scaleFactor;

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
   UINT clipX = roiX_ / binning;
   UINT clipY = roiY_  / binning;
   UINT clipHeight = roiYSize_  / binning;
   UINT clipWidth = roiXSize_  / binning;

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

   /* change to 8bpp grayscale, 16bpp grayscale, 32bpp rgb or 64bpp rgb */

   IWICFormatConverter *formatConverter = NULL;
   if (SUCCEEDED(hr))
   {
      hr = factory->CreateFormatConverter(&formatConverter);
   }
   
   WICPixelFormatGUID pixelFormat = GUID_WICPixelFormat8bppGray;
   UINT imageBytesPerPixel = 1;

   if (grayScale_) 
   {
      if (bitDepth_ == 8)
      {
         pixelFormat = GUID_WICPixelFormat8bppGray;
         imageBytesPerPixel = 1;
      }
      else if (bitDepth_ == 16)
      {
         pixelFormat = GUID_WICPixelFormat16bppGray;
         imageBytesPerPixel = 2;
      }
      else
      {
         LogMessage("grayscale bitdepth not implemented", true);
         return ERR_CAM_CONVERSION;
      }
   }
   else
   {  
      if (bitDepth_ == 8)
      {
         pixelFormat = GUID_WICPixelFormat32bppPBGRA;
         imageBytesPerPixel = 4;
      }
      else if (bitDepth_ == 16)
      {
         pixelFormat = GUID_WICPixelFormat64bppRGBA /* XXX We need GUID_WICPixelFormat64bppBGRA, but this is not available */;
         imageBytesPerPixel = 8;
      }
      else
      {
         LogMessage("color bitdepth not implemented", true);
         return ERR_CAM_CONVERSION;
      }
   }

   if (SUCCEEDED(hr))
   {
      hr = formatConverter->Initialize(
      clipper,                         // Input source to convert
      pixelFormat,                     // Destination pixel format
      WICBitmapDitherTypeNone,         // Specified dither pattern
      NULL,                            // Specify a particular palette 
      0.f,                             // Alpha threshold
      WICBitmapPaletteTypeCustom       // Palette translation type
      );
   }

   /* resize micro-manager image to same dimensions as captured frame */
   UINT imageWidth = 0;
   UINT imageHeight = 0;
      
   if (SUCCEEDED(hr))
   {
      LogMessage("Clipping to roi done", true);
      hr = formatConverter->GetSize(&imageWidth, &imageHeight);
   }

   if (SUCCEEDED(hr) && ((imageWidth == 0) || (imageHeight == 0)))
   {
      LogMessage("Zero dimension image", true);
      return ERR_CAM_CONVERSION;
   }

   if (SUCCEEDED(hr))
   {
      img_.Resize(imageWidth, imageHeight, imageBytesPerPixel);
   }

   /* Copy into image buffer */
   if (SUCCEEDED(hr))
   {
      LogMessage("Resize done", true);
      hr = formatConverter->CopyPixels(NULL, imageWidth*imageBytesPerPixel, imageHeight*imageWidth*imageBytesPerPixel, img_.GetPixelsRW());
   }
   
   if (SUCCEEDED(hr) && !grayScale_ && (bitDepth_ == 16)) // Test for 64bpp RGB color
   {
      // micro-manager expects 64bpp BGRA images, but Windows Imaging Component provides us 64bpp RGBA. Convert.
      Convert64bppRGBAto64bppBGRA(&img_);
   }

   /* release all resources */
   SafeRelease(formatConverter);
   SafeRelease(clipper);
   SafeRelease(transposer);
   SafeRelease(scaler);
   SafeRelease(factory);

   if (SUCCEEDED(hr))
   {
      LogMessage("CopyPixels done", true);

      /* save current image parameters */
      imgBinning_ = GetBinning();
      imgGrayScale_ = grayScale_;
      imgBitDepth_ = bitDepth_;

      /* log and exit */
      LogMessage("ResizeImageBuffer done", true);
      return DEVICE_OK;
   }
  
   LogWICMessage(hr);
   return ERR_CAM_CONVERSION;
}

/*
* Swap R and B components in a 64bit RGBA image, converting it to BGRA.
*/
int CTetheredCamera::Convert64bppRGBAto64bppBGRA(ImgBuffer *img)
{
   UINT16* p = reinterpret_cast<UINT16 *>(img->GetPixelsRW());
   unsigned int width = img->Width();
   unsigned int height = img->Height();

   if (img->Depth() != 8) 
   {
      LogMessage("Convert64bppRGBAto64bppBGRA: not a 64bpp image", true);
      return ERR_CAM_CONVERSION;
   }

   for (unsigned int h = 0;h < height;  h++)
      for (unsigned int w = 0; w < width; w++)
      {
         UINT16 t;
         t = *p; // swap R and B
         *p = *(p+2);
         *(p+2) = t;
         p+=4; // next pixel
      }
   LogMessage("Convert64bppRGBAto64bppBGRA done", true);
   return DEVICE_OK;
}

/* 
 * Use WIC ("Windows Imaging Component") to read the image file. 
 * File can be .jpg or any WIC-supported format, including Canon and Nikon raw if the correct codecs have been installed. 
 */

int CTetheredCamera::LoadWICImage(IWICImagingFactory *factory, const char* filename)
{
   IWICBitmapDecoder *decoder = NULL;

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
   {
      LogMessage("WIC: not unicode filename", true);
      return ERR_CAM_LOAD;
   }

   HRESULT  hr = factory->CreateDecoderFromFilename(unicodefilename, NULL, GENERIC_READ, WICDecodeMetadataCacheOnDemand, &decoder);
    
   LogMessage("WIC: decoder created", true);

   /* Decode first frame to bitmap */
   UINT nFrameCount=0;

   if (SUCCEEDED(hr))
   {
      hr = decoder->GetFrameCount(&nFrameCount);
   }

   if (SUCCEEDED(hr) && (nFrameCount == 0))
   {  
      SafeRelease(decoder);
      LogMessage("WIC: no frames found", true);
      return ERR_CAM_LOAD;
   }

   IWICBitmapFrameDecode *frameDecode = NULL;

   if (SUCCEEDED(hr))
   {
      /* We have a frame! */
      hr = decoder->GetFrame(0, &frameDecode);
   }

   /* convert to bitmap */
   if (SUCCEEDED(hr))
   {
      SafeRelease(frameBitmap);
      hr = factory->CreateBitmapFromSource(frameDecode, WICBitmapCacheOnLoad, &frameBitmap);
   }

   ::SysFreeString(unicodefilename);
   SafeRelease(frameDecode);
   SafeRelease(decoder);

   if (SUCCEEDED(hr))
   {
      LogMessage("WIC: image loaded", true);
      return DEVICE_OK;
   }

   LogWICMessage(hr);
   return ERR_CAM_LOAD;
}


/*
 * Log WIC error message to Micro-Manager Corelog
 */

void CTetheredCamera::LogWICMessage(HRESULT hr)
{
   _com_error err(hr);
   ostringstream msg;
   msg.str("");
   msg << "WIC: error. " << err.ErrorMessage();
   LogMessage(msg.str(), true); 
   return;
}

/*
 * Log libraw warnings to micro-manager CoreLog
 */

void CTetheredCamera::LogRawWarnings()
{
   if (rawProcessor_.imgdata.process_warnings & LIBRAW_WARN_BAD_CAMERA_WB)
      LogMessage("Raw: Warning: Camera white balance is not suitable for use.", true);
   if (rawProcessor_.imgdata.process_warnings & LIBRAW_WARN_NO_METADATA)
      LogMessage("Raw: Warning: Metadata extraction failed.", true);
   if (rawProcessor_.imgdata.process_warnings & LIBRAW_WARN_NO_JPEGLIB)
      LogMessage("Raw: Warning: Data in JPEG format.", true);
   if (rawProcessor_.imgdata.process_warnings & LIBRAW_WARN_NO_EMBEDDED_PROFILE)
      LogMessage("Raw: Warning: No embedded input profile found.", true);
   if (rawProcessor_.imgdata.process_warnings & LIBRAW_WARN_NO_INPUT_PROFILE)
      LogMessage("Raw: Warning: Error when opening input profile ICC file.", true);
   if (rawProcessor_.imgdata.process_warnings & LIBRAW_WARN_BAD_OUTPUT_PROFILE)
      LogMessage("Raw: Warning: Error when opening output profile ICC file.", true);
   if (rawProcessor_.imgdata.process_warnings & LIBRAW_WARN_NO_BADPIXELMAP)
      LogMessage("Raw: Warning: Error when opening bad pixels map file.", true);
   if (rawProcessor_.imgdata.process_warnings & LIBRAW_WARN_BAD_DARKFRAME_FILE)
      LogMessage("Raw: Warning: Error when opening dark frame file.", true);
   if (rawProcessor_.imgdata.process_warnings & LIBRAW_WARN_BAD_DARKFRAME_DIM)
      LogMessage("Raw: Warning: Dark frame file does not have same dimensions as RAW file, or is not in 16-bit PGM format", true);
   return;
 } 

/*
 * Log libraw progress messages to micro-manager CoreLog
 */

int LibrawProgressCallback(void *data, enum LibRaw_progress stage,int iteration, int expected)
{
   ostringstream msg;
   CTetheredCamera *cam;
   cam = reinterpret_cast<CTetheredCamera*>(data);
   msg.str("");
   msg << "Raw: " << LibRaw::strprogress(stage);
   if ((expected == 2) && (iteration == 0))
      msg << " begin";
   else if ((expected == 2) && (iteration == 1))
      msg << " end";
   else
      msg << " pass " << iteration << " of " << expected;
   cam->LogMessage(msg.str(), true);
   return 0; // always return 0 to continue processing
}

/* 
 * Use libraw to read the image file. See http://www.libraw.org
 * This allows reading Canon and Nikon raw, even if no WIC codecs have been installed.
 */

int CTetheredCamera::LoadRawImage(IWICImagingFactory *factory, const char* filename)
{
   libraw_processed_image_t* rawImg = NULL;

   // Decode raw image file "filename" into 48bpp RGB bitmap "rawImg".
   int rc;

   rawProcessor_.set_progress_handler(&LibrawProgressCallback, (void *)this); // log libraw progress messages to micro-manager CoreLog
 
   rawProcessor_.imgdata.params.document_mode = 0; // standard processing (with white balance)
   rawProcessor_.imgdata.params.filtering_mode = LIBRAW_FILTERING_AUTOMATIC;
   rawProcessor_.imgdata.params.output_bps = 16; // Write 16 bits per color value
   rawProcessor_.imgdata.params.no_auto_bright = 1; // Don't use automatic increase of brightness by histogram.

   // let libraw do part of the binning for us.
   rawProcessor_.imgdata.params.half_size = (GetBinning() >= 2); // Half-size the output image. Instead  of  interpolating, reduce each 2x2 block of sensors to one pixel.

   // gamma compensation
   if (decoder_ == decoder_raw)
   {
      // Use sRGB gamma
      rawProcessor_.imgdata.params.gamm[0] = 1.0/2.4;
      rawProcessor_.imgdata.params.gamm[1] = 12.92;
   }
   else
   {
      // Use linear grayscale
      rawProcessor_.imgdata.params.gamm[0] = 1.0;
      rawProcessor_.imgdata.params.gamm[1] = 1.0;
   }

   // white balance
   if (decoder_ != decoder_raw_no_white_balance)
   {
      // Use camera white balance
      rawProcessor_.imgdata.params.use_camera_wb = 1; // If possible, use the white balance from the camera.
      rawProcessor_.imgdata.params.user_mul[0] = 0.0; // Multipliers for user-defined white balance.
      rawProcessor_.imgdata.params.user_mul[1] = 0.0;
      rawProcessor_.imgdata.params.user_mul[2] = 0.0;
      rawProcessor_.imgdata.params.user_mul[3] = 0.0;
   }
   else
   {
      // No white balance
      rawProcessor_.imgdata.params.use_camera_wb = 0; // Don't use the white balance from the camera.
      rawProcessor_.imgdata.params.user_mul[0] = 1.0; // Multipliers for user-defined white balance.
      rawProcessor_.imgdata.params.user_mul[1] = 1.0;
      rawProcessor_.imgdata.params.user_mul[2] = 1.0;
      rawProcessor_.imgdata.params.user_mul[3] = 1.0;
   }

   rc = rawProcessor_.open_file(filename);
   
   if (rc == 0)
   {
      rc = rawProcessor_.unpack();
   }
   if (rc == 0)
   {      
      rc = rawProcessor_.dcraw_process();
      LogRawWarnings();
      //rawProcessor_.dcraw_ppm_tiff_writer("tetheredcam_debug.ppm"); // dump decoded image as ppm bitmap
   }
   if (rc == 0)
   {
      rawImg = rawProcessor_.dcraw_make_mem_image(&rc);
   }

   if (rc == 0)
   {
      if (rawImg->type != LIBRAW_IMAGE_BITMAP)
      {
         LogMessage("Raw: error in raw decoding: not a bitmap", true);
         rc = 1;
      }
      if (rawImg->bits != 16)
      {
         LogMessage("Raw: error in raw decoding: bitdepth not 16", true);
         rc = 1;
      }      
      if (rawImg->colors != 3)
      {
         LogMessage("Raw: error in raw decoding: not rgb", true);
         rc = 1;
      }
   }

   // Exit if raw image conversion failed
   if (rc != 0)
   {
      ostringstream msg;
      msg.str("");
      msg << "Raw: error. " << rawProcessor_.strerror(rc);
      LogMessage(msg.str(), true);
      rawProcessor_.recycle();
      return ERR_CAM_RAW;
   }

   // Convert decoded raw bitmap "rawImg" to WIC bitmap "frameBitmap"

   UINT uiWidth = rawImg->width;
   UINT uiHeight = rawImg->height;
   WICPixelFormatGUID formatGUID = GUID_WICPixelFormat48bppRGB;

   SafeRelease(frameBitmap);
   HRESULT hr = factory->CreateBitmap(uiWidth, uiHeight, formatGUID, WICBitmapCacheOnDemand, &frameBitmap);
   if (SUCCEEDED(hr))
   {
      WICRect rcLock = { 0, 0, uiWidth, uiHeight };
      IWICBitmapLock *pLock = NULL;
      UINT destBufferSize = 0;
      UINT destStride = 0;
      BYTE *pDest = NULL;

      hr = frameBitmap->Lock(&rcLock, WICBitmapLockWrite, &pLock);
      if (SUCCEEDED(hr))
      {
         hr = pLock->GetStride(&destStride);
      }
      if (SUCCEEDED(hr))
      {
         hr = pLock->GetDataPointer(&destBufferSize, &pDest);
      }
      if (SUCCEEDED(hr))
      {
         if (rawImg->data_size <= destBufferSize)
         {
            // Copy pixels, taking stride into account.
            ZeroMemory(pDest, destBufferSize);
            BYTE *pSrc = rawImg->data;
            UINT srcStride = rawImg->width * rawImg->colors * rawImg->bits / 8 ; // size, in bytes, of one row of pixels

            for (UINT row = 0; row < uiHeight; row++)
            {
               CopyMemory(pDest, pSrc, srcStride);
               pSrc += srcStride;
               pDest += destStride;
            }
         }
         else
         {
            LogMessage("Raw: image buffer too small", true);
            hr = E_FAIL;
         }
      }

      if (SUCCEEDED(hr))
      {
         hr = pLock->Release();
      }
   }

   rawProcessor_.dcraw_clear_mem(rawImg);
   rawProcessor_.recycle();

   if (SUCCEEDED(hr))
   {
      LogMessage("Raw: image loaded", true);
      return DEVICE_OK;
   }

   LogWICMessage(hr);
   return ERR_CAM_RAW;
}
// not truncated
