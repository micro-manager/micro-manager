///////////////////////////////////////////////////////////////////////////////
// FILE:          DemoRGBCamera.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   RGB camera simulator.
//                
// COPYRIGHT:     University of California, San Francisco, 2007
//                100X Imaging Inc, 2008
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

#include "DemoRGBCamera.h"
#include <cstdio>
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>
#include <algorithm>

using namespace std;
const int DemoRGBCamera::imageSize_ = 512;
const double DemoRGBCamera::nominalPixelSizeUm_ = 1.0;

const char* g_CameraDeviceName = "DRGBCam";
const char* g_SignalGeneratorName = "RGBSignalGenerator";
const char* g_ChannelName = "Single channel";
const char* g_Unknown = "Unknown";

// constants for naming pixel types (allowed values of the "PixelType" property)
const char* g_PixelType_8bit = "8bit";
const char* g_PixelType_16bit = "16bit";
const char* g_PixelType_32bit = "32bitRGB";

// constants for naming color modes
const char* g_ColorMode_Grayscale = "Grayscale";
const char* g_ColorMode_RGB = "RGB-32bit";

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
      break;
   case DLL_THREAD_ATTACH:
      break;
   case DLL_THREAD_DETACH:
      break;
   case DLL_PROCESS_DETACH:
      break;
   }
   return TRUE;
}
#endif

// mutex
static MMThreadLock g_lock;

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////

MODULE_API void InitializeModuleData()
{
   AddAvailableDeviceName(g_CameraDeviceName, "Demo streaming camera");
   AddAvailableDeviceName(g_SignalGeneratorName, "Demo signal generator: real-time signal output");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   // decide which device class to create based on the deviceName parameter
   if (strcmp(deviceName, g_CameraDeviceName) == 0)
   {
      // create camera
      return new DemoRGBCamera();
   }
   else if (strcmp(deviceName, g_SignalGeneratorName) == 0)
   {
      // create processor
      return new RGBSignalGenerator();
   }

   // ...supplied name not recognized
   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
// DemoRGBCamera implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~

/**
* DemoRGBCamera constructor.
* Setup default all variables and create device properties required to exist
* before intialization. In this case, no such properties were required. All
* properties will be created in the Initialize() method.
*
* As a general guideline Micro-Manager devices do not access hardware in the
* the constructor. We should do as little as possible in the constructor and
* perform most of the initialization in the Initialize() method.
*/
DemoRGBCamera::DemoRGBCamera() : 
CCameraBase<DemoRGBCamera> (),
initialized_(false),
busy_(false),
readoutUs_(0),
color_(true),
rawBuffer_(0),
stopOnOverflow_(true)
{
   // call the base class method to set-up default error codes/messages
   InitializeDefaultErrorMessages();
   readoutStartTime_ = GetCurrentMMTime();
}

/**
* DemoRGBCamera destructor.
* If this device used as intended within the Micro-Manager system,
* Shutdown() will be always called before the destructor. But in any case
* we need to make sure that all resources are properly released even if
* Shutdown() was not called.
*/
DemoRGBCamera::~DemoRGBCamera()
{
   delete[] rawBuffer_;
}

/**
* Obtains device name.
* Required by the MM::Device API.
*/
void DemoRGBCamera::GetName(char* name) const
{
   // We just return the name we use for referring to this
   // device adapter.
   CDeviceUtils::CopyLimitedString(name, g_CameraDeviceName);
}

/**
* Tells us if device is still processing asynchronous command.
* Required by the MM:Device API.
*/
bool DemoRGBCamera::Busy()
{
   //Camera should be in busy state during exposure
   //IsCapturing() is used for determining if sequence thread is run
   //ToDo: guard for thread-safety
   return busy_;
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
int DemoRGBCamera::Initialize()
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
   nRet = CreateProperty(MM::g_Keyword_Description, "Demo Streaming Camera Device Adapter", MM::String, true);
   if (DEVICE_OK != nRet)
      return nRet;

   // CameraName
   nRet = CreateProperty(MM::g_Keyword_CameraName, "Demo Streaming Camera", MM::String, true);
   assert(nRet == DEVICE_OK);

   // CameraID
   nRet = CreateProperty(MM::g_Keyword_CameraID, "V1.0", MM::String, true);
   assert(nRet == DEVICE_OK);

   // binning
   CPropertyAction *pAct = new CPropertyAction (this, &DemoRGBCamera::OnBinning);
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
   pAct = new CPropertyAction (this, &DemoRGBCamera::OnPixelType);
   nRet = CreateProperty(MM::g_Keyword_PixelType, g_PixelType_32bit, MM::String, false, pAct);
   assert(nRet == DEVICE_OK);

   color_=true;
   nRet = SetPixelTypesValues();
   assert(nRet == DEVICE_OK);

   // exposure
   nRet = CreateProperty(MM::g_Keyword_Exposure, "100.0", MM::Float, false);
   assert(nRet == DEVICE_OK);

   // scan mode
   nRet = CreateProperty("ScanMode", "1", MM::Integer, false);
   assert(nRet == DEVICE_OK);

   // camera gain
   nRet = CreateProperty(MM::g_Keyword_Gain, "0", MM::Integer, false);
   assert(nRet == DEVICE_OK);
   SetPropertyLimits(MM::g_Keyword_Gain, 0.0, 10.0);

   // camera offset
   nRet = CreateProperty(MM::g_Keyword_Offset, "0", MM::Integer, false);
   assert(nRet == DEVICE_OK);

   // camera temperature
   nRet = CreateProperty(MM::g_Keyword_CCDTemperature, "0", MM::Float, false);
   assert(nRet == DEVICE_OK);
   SetPropertyLimits(MM::g_Keyword_CCDTemperature,  5.0, 100.0);

   // readout time
   pAct = new CPropertyAction (this, &DemoRGBCamera::OnReadoutTime);
   nRet = CreateProperty(MM::g_Keyword_ReadoutTime, "0", MM::Float, false, pAct);
   assert(nRet == DEVICE_OK);

   // actual interval
   nRet = CreateProperty(MM::g_Keyword_ActualInterval_ms, "0.0", MM::Float, true);
   assert(nRet == DEVICE_OK);

   // color mode
   pAct = new CPropertyAction (this, &DemoRGBCamera::OnColorMode);
   nRet = CreateProperty(MM::g_Keyword_ColorMode, g_ColorMode_RGB, MM::String, false, pAct);
   color_ = false;
   assert(nRet == DEVICE_OK);
   vector<string> colorValues;
   colorValues.push_back(g_ColorMode_Grayscale);
   colorValues.push_back(g_ColorMode_RGB);
   nRet = SetAllowedValues(MM::g_Keyword_ColorMode, colorValues);


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

   initialized_ = true;

   // initialize image buffer
   return SnapImage();
}

int DemoRGBCamera::SetPixelTypesValues(){
   int ret = DEVICE_ERR;
   vector<string> pixelTypeValues;
   if(color_)
   {
      pixelTypeValues.push_back(g_PixelType_32bit);
   }else
   {
      pixelTypeValues.push_back(g_PixelType_8bit);
      pixelTypeValues.push_back(g_PixelType_16bit);
   }
   ret = SetAllowedValues(MM::g_Keyword_PixelType, pixelTypeValues);
   return ret;
}


/**
* Returns the number of physical channels in the image.
*/
unsigned int DemoRGBCamera::GetNumberOfComponents() const
{
   return 1;
}

int DemoRGBCamera::GetComponentName(unsigned int channel, char* name)
{
   int ret=DEVICE_ERR;
   if(channel == 0)
   {
      CDeviceUtils::CopyLimitedString(name, g_ChannelName);
      ret = DEVICE_OK;
   }
   else
   {
      CDeviceUtils::CopyLimitedString(name, g_Unknown);
      ret = DEVICE_NONEXISTENT_CHANNEL;
   }
   return ret;
}


/**
* Shuts down (unloads) the device.
* Required by the MM::Device API.
* Ideally this method will completely unload the device and release all resources.
* Shutdown() may be called multiple times in a row.
* After Shutdown() we should be allowed to call Initialize() again to load the device
* without causing problems.
*/
int DemoRGBCamera::Shutdown()
{
   initialized_ = false;
   StopSequenceAcquisition();
   delete[] rawBuffer_;
   rawBuffer_ = 0;
   return DEVICE_OK;
}

int DemoRGBCamera::SnapImage()
{
   MM::MMTime startTime = GetCurrentMMTime();
   double exp = GetExposure();
//   double expUs = exp * 1000.0;

   GenerateSyntheticImage(img_[0], exp);

   MM::ImageProcessor* ip = GetCoreCallback()->GetImageProcessor(this);
   if (ip)
   {
      int ret = ip->Process(const_cast<unsigned char*>(img_[0].GetPixels()), GetImageWidth(), GetImageHeight(), GetImageBytesPerPixel());
      if (ret != DEVICE_OK)
         return ret;
   }

   readoutStartTime_ = GetCurrentMMTime();
   CDeviceUtils::SleepMs((long)GetExposure());

   return DEVICE_OK;
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
const unsigned char* DemoRGBCamera::GetImageBuffer()
{
   while (GetCurrentMMTime() - readoutStartTime_ < MM::MMTime(readoutUs_)) {CDeviceUtils::SleepMs(5);}
   unsigned long singleChannelSize = img_[0].Width() * img_[0].Height() * img_[0].Depth();

   memcpy(rawBuffer_, img_[0].GetPixels(), singleChannelSize);

   return rawBuffer_;
}


/**
* Returns pixel data with interleaved RGB pixels in 32 bpp format
*/
const unsigned int* DemoRGBCamera::GetImageBufferAsRGB32()
{
   return (unsigned int*) GetImageBuffer();
}

/**
* Returns image buffer X-size in pixels.
* Required by the MM::Camera API.
*/
unsigned DemoRGBCamera::GetImageWidth() const
{
   return img_[0].Width();
}

/**
* Returns image buffer Y-size in pixels.
* Required by the MM::Camera API.
*/
unsigned DemoRGBCamera::GetImageHeight() const
{
   return img_[0].Height();
}

/**
* Returns image buffer pixel depth in bytes.
* Required by the MM::Camera API.
*/
unsigned DemoRGBCamera::GetImageBytesPerPixel() const
{
   return img_[0].Depth();
} 

/**
* Returns the bit depth (dynamic range) of the pixel.
* This does not affect the buffer size, it just gives the client application
* a guideline on how to interpret pixel values.
* Required by the MM::Camera API.
*/
unsigned DemoRGBCamera::GetBitDepth() const
{
   return 8 * GetImageBytesPerPixel();
}

/**
* Returns the size in bytes of the image buffer.
* Required by the MM::Camera API.
*/
long DemoRGBCamera::GetImageBufferSize() const
{
   return img_[0].Width() * img_[0].Height() * GetImageBytesPerPixel();
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
int DemoRGBCamera::SetROI(unsigned /*x*/, unsigned /*y*/, unsigned xSize, unsigned ySize)
{
   if(IsCapturing())
      return ERR_BUSY_ACQIRING;

   if (xSize == 0 && ySize == 0)
      // effectively clear ROI
      ResizeImageBuffer();
   else
   {
      char buf[MM::MaxStrLength];
      int ret = GetProperty(MM::g_Keyword_Binning, buf);
      if (ret != DEVICE_OK)
         return ret;
      long binSize = atol(buf);

      ret = GetProperty(MM::g_Keyword_PixelType, buf);
      if (ret != DEVICE_OK)
         return ret;

      int byteDepth=1;
      if (strcmp(buf, g_PixelType_8bit) == 0)
         byteDepth = 1;
      else if (strcmp(buf, g_PixelType_16bit) == 0)
         byteDepth = 2;
      else if (strcmp(buf, g_PixelType_32bit) == 0)
         byteDepth = 4;
      else
         return DEVICE_ERR;

      // apply ROI
      return ResizeImageBuffer(xSize*binSize, ySize*binSize, byteDepth, binSize);
   }

   return DEVICE_OK;
}

/**
* Returns the actual dimensions of the current ROI.
* Required by the MM::Camera API.
*/
int DemoRGBCamera::GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize)
{
   x = 0;
   y = 0;

   xSize = img_[0].Width();
   ySize = img_[0].Height();

   return DEVICE_OK;
}

/**
* Resets the Region of Interest to full frame.
* Required by the MM::Camera API.
*/
int DemoRGBCamera::ClearROI()
{
   if (Busy())
      return ERR_BUSY_ACQIRING;

   ResizeImageBuffer();
   return DEVICE_OK;
}

/**
* Returns the current exposure setting in milliseconds.
* Required by the MM::Camera API.
*/
double DemoRGBCamera::GetExposure() const
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
void DemoRGBCamera::SetExposure(double exp)
{

   SetProperty(MM::g_Keyword_Exposure, CDeviceUtils::ConvertToString(exp));
}

/**
* Returns the current binning factor.
* Required by the MM::Camera API.
*/
int DemoRGBCamera::GetBinning() const
{
   char buf[MM::MaxStrLength];
   int ret = GetProperty(MM::g_Keyword_Binning, buf);
   if (ret != DEVICE_OK)
      return 1;
   return atoi(buf);
}

/**
* Sets binning factor.
* Required by the MM::Camera API.
*/
int DemoRGBCamera::SetBinning(int binFactor)
{
   if(IsCapturing())
      return ERR_BUSY_ACQIRING;

   return SetProperty(MM::g_Keyword_Binning, CDeviceUtils::ConvertToString(binFactor));
}


///////////////////////////////////////////////////////////////////////////////
// DemoRGBCamera Action handlers
///////////////////////////////////////////////////////////////////////////////

/**
* Handles "Binning" property.
*/
int DemoRGBCamera::OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   int ret = DEVICE_ERR;
   switch(eAct)
   {
   case MM::AfterSet:
      {
         if(IsCapturing())
            return DEVICE_CAN_NOT_SET_PROPERTY;
         // the user just set the new value for the property, so we have to
         // apply this value to the 'hardware'.
         long binFactor;
         pProp->Get(binFactor);

         if (binFactor > 0 && binFactor < 10)
         {
            ret = ResizeImageBuffer(imageSize_, imageSize_, img_[0].Depth() , binFactor);
         }
         else
         {
            // on failure reset default binning of 1
            ResizeImageBuffer();
            pProp->Set(1L);
            return ERR_UNKNOWN_MODE;
         }
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
int DemoRGBCamera::OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   int ret = DEVICE_ERR;
   switch(eAct)
   {
   case MM::AfterSet:
      {
         if(IsCapturing())
            return DEVICE_CAN_NOT_SET_PROPERTY;

         string pixelType;
         pProp->Get(pixelType);

         //
         if (pixelType.compare(g_PixelType_8bit) == 0)
         {
            ret = ResizeImageBuffer(img_[0].Width(), img_[0].Height(), 1);
         }
         else if (pixelType.compare(g_PixelType_16bit) == 0)
         {
            ret = ResizeImageBuffer(img_[0].Width(), img_[0].Height(), 2);
         }
         else if (pixelType.compare(g_PixelType_32bit) == 0)
         {
            ret = ResizeImageBuffer(img_[0].Width(), img_[0].Height(), 4);
         }
         else
         {
            // on error switch to default pixel type
            pProp->Set(g_PixelType_8bit);
            ResizeImageBuffer(img_[0].Width(), img_[0].Height(), 1);
            ret = ERR_UNKNOWN_MODE;
         }

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
* Handles "ReadoutTime" property.
*/
int DemoRGBCamera::OnReadoutTime(MM::PropertyBase* pProp, MM::ActionType eAct)
{

   if (eAct == MM::AfterSet)
   {
      if (Busy())
         return ERR_BUSY_ACQIRING;

      double readoutMs;
      pProp->Get(readoutMs);

      readoutUs_ = (long)(readoutMs * 1000.0);
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set(readoutUs_ / 1000.0);
   }

   return DEVICE_OK;
}

/**
* Handles "ColorMode" property.
*/
int DemoRGBCamera::OnColorMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   int ret = DEVICE_ERR;

   if (eAct == MM::AfterSet)
   {
      if(IsCapturing())
         return DEVICE_CAN_NOT_SET_PROPERTY;

      string pixelType;
      pProp->Get(pixelType);

      if (pixelType.compare(g_ColorMode_Grayscale) == 0)
      {
         color_ = false;
         SetPixelTypesValues();
         SetProperty(MM::g_Keyword_PixelType, g_PixelType_8bit);
      }
      else if (pixelType.compare(g_ColorMode_RGB) == 0)
      {
         color_ = true;
         SetPixelTypesValues();
         SetProperty(MM::g_Keyword_PixelType, g_PixelType_32bit);
      }
      else
      {
         // on error switch to default pixel type
         color_ = false;
         return ERR_UNKNOWN_MODE;
      }
      ret = ResizeImageBuffer();
      if (initialized_) {
         OnPropertiesChanged(); // notify GUI to update
      }
   }
   else if (eAct == MM::BeforeGet)
   {
      ret = DEVICE_OK;
   }

   return ret;
}
///////////////////////////////////////////////////////////////////////////////
// Private DemoRGBCamera methods
///////////////////////////////////////////////////////////////////////////////
/**
* Sync internal image buffer size to the chosen property values.
*/
int DemoRGBCamera::ResizeImageBuffer(int imageSizeW /*= imageSize_*/, int imageSizeH /*= imageSize_*/)
{
   char buf[MM::MaxStrLength];
   int ret = GetProperty(MM::g_Keyword_Binning, buf);
   if (ret != DEVICE_OK)
      return ret;
   long binSize = atol(buf);

   ret = GetProperty(MM::g_Keyword_PixelType, buf);
   if (ret != DEVICE_OK)
      return ret;

   int byteDepth=1;
   if (strcmp(buf, g_PixelType_8bit) == 0)
      byteDepth = 1;
   else if (strcmp(buf, g_PixelType_16bit) == 0)
      byteDepth = 2;
   else if (strcmp(buf, g_PixelType_32bit) == 0)
      byteDepth = 4;
   else
      return DEVICE_ERR;

   return ResizeImageBuffer(imageSizeW, imageSizeH, byteDepth, binSize);

}
/**
* Sync internal image buffer size to the chosen property values.
*/
int DemoRGBCamera::ResizeImageBuffer(int imageSizeW, int imageSizeH, int byteDepth, int binSize /*=1*/)
{

   img_[0].Resize(imageSizeW/binSize, imageSizeH/binSize, byteDepth);

   delete[] rawBuffer_;
   rawBuffer_ = new unsigned char[img_[0].Width() * img_[0].Height() * img_[0].Depth()];
   return DEVICE_OK;
}

/**
* Generate a spatial sine wave.
*/
void DemoRGBCamera::GenerateSyntheticImage(ImgBuffer& img, double exp)
{
   if (img.Height() == 0 || img.Width() == 0 || img.Depth() == 0)
      return;

   const double cPi = 3.14;
   long lPeriod = img.Width()/2;
   static double dPhase = 0.0;
   double dLinePhase = 0.0;
   const double dAmp = exp;
   const double cLinePhaseInc = 2.0 * cPi / 4.0 / img.Height();

   unsigned j, k;
   if (img.Depth() == 1)
   {
      double pedestal = 127 * exp / 100.0;
      unsigned char* pBuf = img.GetPixelsRW();
      for (j=0; j<img.Height(); j++)
      {
         for (k=0; k<img.Width(); k++)
         {
            long lIndex = img.Width()*j + k;
            *(pBuf + lIndex) = (unsigned char) min(255.0, (pedestal + dAmp * sin(dPhase + dLinePhase + (2.0 * cPi * k) / lPeriod)));
         }
         dLinePhase += cLinePhaseInc;
      }         
   }
   else if (img.Depth() == 2)
   {
      double pedestal = USHRT_MAX/2 * exp / 100.0;
      double dAmp16 = dAmp * USHRT_MAX/255.0; // scale to behave like 8-bit
      unsigned short* pBuf = (unsigned short*) img.GetPixelsRW();
      for (j=0; j<img.Height(); j++)
      {
         for (k=0; k<img.Width(); k++)
         {
            long lIndex = img.Width()*j + k;
            *(pBuf + lIndex) = (unsigned short) min((double)USHRT_MAX, pedestal + dAmp16 * sin(dPhase + dLinePhase + (2.0 * cPi * k) / lPeriod));
         }
         dLinePhase += cLinePhaseInc;
      }         
   }else if (img.Depth() == 4)
   {
      double pedestal = 127 * exp / 100.0;
      unsigned int * pBuf = (unsigned int*) img.GetPixelsRW();
      for (j=0; j<img.Height(); j++)
      {
         for (k=0; k<img.Width(); k++)
         {
            long lIndex = img.Width()*j + k;
            unsigned int value0 = (unsigned char) min(255.0, (pedestal + dAmp * sin(dPhase + dLinePhase + (2.0 * cPi * k) / lPeriod)));
            unsigned int value1 = (unsigned char) min(255.0, (pedestal + dAmp * sin(dPhase + dLinePhase*2 + (2.0 * cPi * k) / lPeriod)));
            unsigned int value2 = (unsigned char) min(255.0, (pedestal + dAmp * sin(dPhase + dLinePhase*4 + (2.0 * cPi * k) / lPeriod)));
            *(pBuf + lIndex) = value0+(value1<<8)+(value2<<16);
         }
         dLinePhase += cLinePhaseInc;
      }         
   }         
   dPhase += cPi / 4.0;
}


/**
* Starts continuous acquisition.
*
*/
int DemoRGBCamera::StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow)
{
   ostringstream os;
   os << "Started camera streaming with an interval of " << interval_ms << " ms, for " << numImages << " images.\n";
   printf("%s", os.str().c_str());
   if (IsCapturing())
      return ERR_BUSY_ACQIRING;

   stopOnOverflow_ = stopOnOverflow;
   int ret = GetCoreCallback()->PrepareForAcq(this);
   if (ret != DEVICE_OK)
      return ret;

   // make sure the circular buffer is properly sized
   GetCoreCallback()->InitializeImageBuffer(1, 1, GetImageWidth(), GetImageHeight(), GetImageBytesPerPixel());

   double actualIntervalMs = max(GetExposure(), interval_ms);
   SetProperty(MM::g_Keyword_ActualInterval_ms, CDeviceUtils::ConvertToString(actualIntervalMs)); 

   thd_->Start(numImages,actualIntervalMs);

   return DEVICE_OK;
}

int DemoRGBCamera::PushImage()
{
   // TODO: call core to prepare for image snap
   GenerateSyntheticImage(img_[0], GetExposure());

   // process image
   MM::ImageProcessor* ip = GetCoreCallback()->GetImageProcessor(this);
   if (ip)
   {
      int ret = ip->Process(const_cast<unsigned char*>(img_[0].GetPixels()), GetImageWidth(), GetImageHeight(), GetImageBytesPerPixel());
      if (ret != DEVICE_OK)
         return ret;
   }

   // insert image into the circular buffer
   GetImageBuffer(); // this effectively copies images to rawBuffer_

   // insert all three channels at once
   int ret = GetCoreCallback()->InsertMultiChannel(this, rawBuffer_, 1, GetImageWidth(), GetImageHeight(), GetImageBytesPerPixel());
   if (!stopOnOverflow_ && ret == DEVICE_BUFFER_OVERFLOW)
   {
      // do not stop on overflow - just reset the buffer
      GetCoreCallback()->ClearImageBuffer(this);
      // repeat the insert
      return GetCoreCallback()->InsertMultiChannel(this, rawBuffer_, 1, GetImageWidth(), GetImageHeight(), GetImageBytesPerPixel());
   } else
      return ret;
}
int DemoRGBCamera::ThreadRun()
{
   int ret = PushImage();
   if (ret != DEVICE_OK)
   {
      // error occured so the acquisition must be stopped
      LogMessage("Overflow or image dimension mismatch!\n");
   }
   CDeviceUtils::SleepMs((long)GetExposure());
   return ret;
}
