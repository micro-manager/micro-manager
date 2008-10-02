///////////////////////////////////////////////////////////////////////////////
// FILE:          DemoCamera.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   The example implementation of the demo camera.
//                Simulates generic digital camera and associated automated
//                microscope devices and enables testing of the rest of the
//                system without the need to connect to the actual hardware. 
//                
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 06/08/2005
//
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

#ifdef WIN32
   #define WIN32_LEAN_AND_MEAN
   #include <windows.h>
   #define snprintf _snprintf 
#endif

#include "DemoCamera.h"
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>
using namespace std;
const int CDemoCamera::imageSize_;
const double CDemoCamera::nominalPixelSizeUm_ = 1.0;

// External names used used by the rest of the system
// to load particular device from the "DemoCamera.dll" library
const char* g_CameraDeviceName = "DCam";
const char* g_WheelDeviceName = "DWheel";
const char* g_StateDeviceName = "DStateDevice";
const char* g_LightPathDeviceName = "DLightPath";
const char* g_ObjectiveDeviceName = "DObjective";
const char* g_StageDeviceName = "DStage";
const char* g_XYStageDeviceName = "DXYStage";
const char* g_AutoFocusDeviceName = "DAutoFocus";
const char* g_ShutterDeviceName = "DShutter";
const char* g_DADeviceName = "D-DA";
const char* g_MagnifierDeviceName = "DOptovar";

// constants for naming pixel types (allowed values of the "PixelType" property)
const char* g_PixelType_8bit = "8bit";
const char* g_PixelType_16bit = "16bit";

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

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////

MODULE_API void InitializeModuleData()
{
   AddAvailableDeviceName(g_CameraDeviceName, "Demo camera");
   AddAvailableDeviceName(g_WheelDeviceName, "Demo filter wheel");
   AddAvailableDeviceName(g_StateDeviceName, "Demo State Device");
   AddAvailableDeviceName(g_ObjectiveDeviceName, "Demo objective turret");
   AddAvailableDeviceName(g_StageDeviceName, "Demo stage");
   AddAvailableDeviceName(g_XYStageDeviceName, "Demo XY stage");
   AddAvailableDeviceName(g_LightPathDeviceName, "Demo light path");
   AddAvailableDeviceName(g_AutoFocusDeviceName, "Demo auto focus");
   AddAvailableDeviceName(g_ShutterDeviceName, "Demo shutter");
   AddAvailableDeviceName(g_DADeviceName, "Demo DA");
   AddAvailableDeviceName(g_MagnifierDeviceName, "Demo Optovar");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   // decide which device class to create based on the deviceName parameter
   if (strcmp(deviceName, g_CameraDeviceName) == 0)
   {
      // create camera
      return new CDemoCamera();
   }
   else if (strcmp(deviceName, g_WheelDeviceName) == 0)
   {
      // create filter wheel
      return new CDemoFilterWheel();
   }
   else if (strcmp(deviceName, g_ObjectiveDeviceName) == 0)
   {
      // create objective turret
      return new CDemoObjectiveTurret();
   }
   else if (strcmp(deviceName, g_StateDeviceName) == 0)
   {
      // create state device
      return new CDemoStateDevice();
   }
   else if (strcmp(deviceName, g_StageDeviceName) == 0)
   {
      // create stage
      return new CDemoStage();
   }
   else if (strcmp(deviceName, g_XYStageDeviceName) == 0)
   {
      // create stage
      return new CDemoXYStage();
   }
   else if (strcmp(deviceName, g_LightPathDeviceName) == 0)
   {
      // create light path
      return new CDemoLightPath();
   }
   else if (strcmp(deviceName, g_ShutterDeviceName) == 0)
   {
      // create shutter
      return new DemoShutter();
   }
   else if (strcmp(deviceName, g_AutoFocusDeviceName) == 0)
   {
      // create autoFocus
      return new DemoAutoFocus();
   }
   else if (strcmp(deviceName, g_DADeviceName) == 0)
   {
      // create DA
      return new DemoDA();
   }
   else if (strcmp(deviceName, g_MagnifierDeviceName) == 0)
   {
      // create Optovar 
      return new DemoMagnifier();
   }

   // ...supplied name not recognized
   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
// CDemoCamera implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~

/**
 * CDemoCamera constructor.
 * Setup default all variables and create device properties required to exist
 * before intialization. In this case, no such properties were required. All
 * properties will be created in the Initialize() method.
 *
 * As a general guideline Micro-Manager devices do not access hardware in the
 * the constructor. We should do as little as possible in the constructor and
 * perform most of the initialization in the Initialize() method.
 */
CDemoCamera::CDemoCamera() :
   CCameraBase<CDemoCamera> (),
   initialized_(false),
   busy_(false),
   readoutUs_(0.0),
   scanMode_(1)
{
   // call the base class method to set-up default error codes/messages
   InitializeDefaultErrorMessages();
   readoutStartTime_ = GetCurrentMMTime();
}

/**
 * CDemoCamera destructor.
 * If this device used as intended within the Micro-Manager system,
 * Shutdown() will be always called before the destructor. But in any case
 * we need to make sure that all resources are properly released even if
 * Shutdown() was not called.
 */
CDemoCamera::~CDemoCamera()
{
   // no clean-up required for this device
}

/**
 * Obtains device name.
 * Required by the MM::Device API.
 */
void CDemoCamera::GetName(char* name) const
{
   // We just return the name we use for referring to this
   // device adapter.
   CDeviceUtils::CopyLimitedString(name, g_CameraDeviceName);
}

/**
 * Tells us if device is still processing asynchronous command.
 * Required by the MM:Device API.
 */
bool CDemoCamera::Busy()
{
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
int CDemoCamera::Initialize()
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
   nRet = CreateProperty(MM::g_Keyword_Description, "Demo Camera Device Adapter", MM::String, true);
   if (DEVICE_OK != nRet)
      return nRet;

   // CameraName
   nRet = CreateProperty(MM::g_Keyword_CameraName, "DemoCamera-MultiMode", MM::String, true);
   assert(nRet == DEVICE_OK);
		      
   // CameraID
   nRet = CreateProperty(MM::g_Keyword_CameraID, "V1.0", MM::String, true);
   assert(nRet == DEVICE_OK);

   // binning
   CPropertyAction *pAct = new CPropertyAction (this, &CDemoCamera::OnBinning);
   nRet = CreateProperty(MM::g_Keyword_Binning, "1", MM::Integer, false, pAct);
   assert(nRet == DEVICE_OK);

   nRet = SetAllowedBinning();
   if (nRet != DEVICE_OK)
      return nRet;

   // pixel type
   pAct = new CPropertyAction (this, &CDemoCamera::OnPixelType);
   nRet = CreateProperty(MM::g_Keyword_PixelType, g_PixelType_8bit, MM::String, false, pAct);
   assert(nRet == DEVICE_OK);

   vector<string> pixelTypeValues;
   pixelTypeValues.push_back(g_PixelType_8bit);
   pixelTypeValues.push_back(g_PixelType_16bit);
   nRet = SetAllowedValues(MM::g_Keyword_PixelType, pixelTypeValues);
   if (nRet != DEVICE_OK)
      return nRet;

   // exposure
   nRet = CreateProperty(MM::g_Keyword_Exposure, "100.0", MM::Float, false);
   assert(nRet == DEVICE_OK);
   SetPropertyLimits(MM::g_Keyword_Exposure, 0, 10000);

   // scan mode
   pAct = new CPropertyAction (this, &CDemoCamera::OnScanMode);
   nRet = CreateProperty("ScanMode", "1", MM::Integer, false, pAct);
   assert(nRet == DEVICE_OK);
   AddAllowedValue("ScanMode","1");
   AddAllowedValue("ScanMode","2");
   AddAllowedValue("ScanMode","3");

   // camera gain
   nRet = CreateProperty(MM::g_Keyword_Gain, "0", MM::Integer, false);
   assert(nRet == DEVICE_OK);
   SetPropertyLimits(MM::g_Keyword_Gain, -5, 8);

   // camera offset
   nRet = CreateProperty(MM::g_Keyword_Offset, "0", MM::Integer, false);
   assert(nRet == DEVICE_OK);

   // camera temperature
   nRet = CreateProperty(MM::g_Keyword_CCDTemperature, "0", MM::Float, false);
   assert(nRet == DEVICE_OK);
   SetPropertyLimits(MM::g_Keyword_CCDTemperature, -100, 10);

   // readout time
   pAct = new CPropertyAction (this, &CDemoCamera::OnReadoutTime);
   nRet = CreateProperty(MM::g_Keyword_ReadoutTime, "0", MM::Float, false, pAct);
   assert(nRet == DEVICE_OK);

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

/**
 * Shuts down (unloads) the device.
 * Required by the MM::Device API.
 * Ideally this method will completely unload the device and release all resources.
 * Shutdown() may be called multiple times in a row.
 * After Shutdown() we should be allowed to call Initialize() again to load the device
 * without causing problems.
 */
int CDemoCamera::Shutdown()
{
   initialized_ = false;
   return DEVICE_OK;
}

/**
 * Performs exposure and grabs a single image.
 * Required by the MM::Camera API.
 */
int CDemoCamera::SnapImage()
{
   MM::MMTime startTime = GetCurrentMMTime();
   double exp = GetExposure();
   double expUs = exp * 1000.0;
   GenerateSyntheticImage(img_, exp);

   while (GetCurrentMMTime() - startTime < MM::MMTime(expUs)) {}
   readoutStartTime_ = GetCurrentMMTime();

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
const unsigned char* CDemoCamera::GetImageBuffer()
{
   MM::MMTime curTime = GetCurrentMMTime();
   MM::MMTime readoutTime(readoutUs_);
   while (readoutTime > (curTime - readoutStartTime_)) {}
   return img_.GetPixels();
}

/**
 * Returns image buffer X-size in pixels.
 * Required by the MM::Camera API.
 */
unsigned CDemoCamera::GetImageWidth() const
{
   return img_.Width();
}

/**
 * Returns image buffer Y-size in pixels.
 * Required by the MM::Camera API.
 */
unsigned CDemoCamera::GetImageHeight() const
{
   return img_.Height();
}

/**
 * Returns image buffer pixel depth in bytes.
 * Required by the MM::Camera API.
 */
unsigned CDemoCamera::GetImageBytesPerPixel() const
{
   return img_.Depth();
} 

/**
 * Returns the bit depth (dynamic range) of the pixel.
 * This does not affect the buffer size, it just gives the client application
 * a guideline on how to interpret pixel values.
 * Required by the MM::Camera API.
 */
unsigned CDemoCamera::GetBitDepth() const
{
   return 8 * GetImageBytesPerPixel();
}

/**
 * Returns the size in bytes of the image buffer.
 * Required by the MM::Camera API.
 */
long CDemoCamera::GetImageBufferSize() const
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
int CDemoCamera::SetROI(unsigned /*x*/, unsigned /*y*/, unsigned xSize, unsigned ySize)
{
   if (xSize == 0 && ySize == 0)
      // effectively clear ROI
      ResizeImageBuffer();
   else
      // apply ROI
      img_.Resize(xSize, ySize);

   return DEVICE_OK;
}

/**
 * Returns the actual dimensions of the current ROI.
 * Required by the MM::Camera API.
 */
int CDemoCamera::GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize)
{
   x = 0;
   y = 0;

   xSize = img_.Width();
   ySize = img_.Height();

   return DEVICE_OK;
}

/**
 * Resets the Region of Interest to full frame.
 * Required by the MM::Camera API.
 */
int CDemoCamera::ClearROI()
{
   ResizeImageBuffer();
   return DEVICE_OK;
}

/**
 * Returns the current exposure setting in milliseconds.
 * Required by the MM::Camera API.
 */
double CDemoCamera::GetExposure() const
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
void CDemoCamera::SetExposure(double exp)
{
   SetProperty(MM::g_Keyword_Exposure, CDeviceUtils::ConvertToString(exp));
}

/**
 * Returns the current binning factor.
 * Required by the MM::Camera API.
 */
int CDemoCamera::GetBinning() const
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
int CDemoCamera::SetBinning(int binFactor)
{
   return SetProperty(MM::g_Keyword_Binning, CDeviceUtils::ConvertToString(binFactor));
}

int CDemoCamera::SetAllowedBinning() 
{
   vector<string> binValues;
   binValues.push_back("1");
   binValues.push_back("2");
   if (scanMode_ < 3)
      binValues.push_back("4");
   if (scanMode_ < 2)
      binValues.push_back("8");
   LogMessage("Setting Allowed Binning settings", true);
   return SetAllowedValues(MM::g_Keyword_Binning, binValues);
}

///////////////////////////////////////////////////////////////////////////////
// CDemoCamera Action handlers
///////////////////////////////////////////////////////////////////////////////

/**
 * Handles "Binning" property.
 */
int CDemoCamera::OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      // the user just set the new value for the property, so we have to
      // apply this value to the 'hardware'.
      long binFactor;
      pProp->Get(binFactor);
      const long imageSize(512);

      if (binFactor > 0 && binFactor < 10)
      {
         img_.Resize(imageSize/binFactor, imageSize/binFactor);
      }
      else
      {
         // on failure reset default binning of 1
         img_.Resize(imageSize, imageSize);
         pProp->Set(1L);
         return ERR_UNKNOWN_MODE;
      }
   }
   else if (eAct == MM::BeforeGet)
   {
      // the user is requesting the current value for the property, so
      // either ask the 'hardware' or let the system return the value
      // cached in the property.
   }

   return DEVICE_OK; 
}

/**
 * Handles "PixelType" property.
 */
int CDemoCamera::OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      string pixelType;
      pProp->Get(pixelType);

      if (pixelType.compare(g_PixelType_8bit) == 0)
      {
         img_.Resize(img_.Width(), img_.Height(), 1);
      }
      else if (pixelType.compare(g_PixelType_16bit) == 0)
      {
         img_.Resize(img_.Width(), img_.Height(), 2);
      }
      else
      {
         // on error switch to default pixel type
         img_.Resize(img_.Width(), img_.Height(), 1);
         pProp->Set(g_PixelType_8bit);
         return ERR_UNKNOWN_MODE;
      }
   }

   return DEVICE_OK;
}

/**
 * Handles "ReadoutTime" property.
 */
int CDemoCamera::OnReadoutTime(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      double readoutMs;
      pProp->Get(readoutMs);

      readoutUs_ = readoutMs * 1000.0;
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set(readoutUs_ / 1000.0);
   }

   return DEVICE_OK;
}

/*
 * Handles "ScanMode" property.
 * Changes allowed Binning values to test whether the UI updates properly
 */
int CDemoCamera::OnScanMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{ 
   if (eAct == MM::AfterSet) {
      pProp->Get(scanMode_);
      SetAllowedBinning();
      int ret = OnPropertiesChanged();
      if (ret != DEVICE_OK)
         return ret;
   } else if (eAct == MM::BeforeGet) {
      pProp->Set(scanMode_);
   }
   return DEVICE_OK;
}
    

///////////////////////////////////////////////////////////////////////////////
// Private CDemoCamera methods
///////////////////////////////////////////////////////////////////////////////

/**
 * Sync internal image buffer size to the chosen property values.
 */
int CDemoCamera::ResizeImageBuffer()
{
   char buf[MM::MaxStrLength];
   int ret = GetProperty(MM::g_Keyword_Binning, buf);
   if (ret != DEVICE_OK)
      return ret;
   long binSize = atol(buf);

   ret = GetProperty(MM::g_Keyword_PixelType, buf);
   if (ret != DEVICE_OK)
      return ret;

   int byteDepth = 1;
   if (strcmp(buf, g_PixelType_16bit) == 0)
      byteDepth = 2;
   
   img_.Resize(imageSize_/binSize, imageSize_/binSize, byteDepth);
   return DEVICE_OK;
}

/**
 * Generate a spatial sine wave.
 */
void CDemoCamera::GenerateSyntheticImage(ImgBuffer& img, double exp)
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
      unsigned char* pBuf = const_cast<unsigned char*>(img.GetPixels());
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
      unsigned short* pBuf = (unsigned short*) const_cast<unsigned char*>(img.GetPixels());
      for (j=0; j<img.Height(); j++)
      {
         for (k=0; k<img.Width(); k++)
         {
            long lIndex = img.Width()*j + k;
            *(pBuf + lIndex) = (unsigned short) min((double)USHRT_MAX, pedestal + dAmp16 * sin(dPhase + dLinePhase + (2.0 * cPi * k) / lPeriod));
         }
         dLinePhase += cLinePhaseInc;
      }         
   }

   dPhase += cPi / 4.0;
}


///////////////////////////////////////////////////////////////////////////////
// CDemoFilterWheel implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

CDemoFilterWheel::CDemoFilterWheel() : 
   numPos_(10), 
   busy_(false), 
   initialized_(false), 
   changedTime_(0.0),
   position_(0)
{
   InitializeDefaultErrorMessages();
   EnableDelay(); // signals that the dealy setting will be used
}

CDemoFilterWheel::~CDemoFilterWheel()
{
   Shutdown();
}

void CDemoFilterWheel::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_WheelDeviceName);
}


int CDemoFilterWheel::Initialize()
{
   if (initialized_)
      return DEVICE_OK;

   // set property list
   // -----------------
   
   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, g_WheelDeviceName, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "Demo filter wheel driver", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Set timer for the Busy signal, or we'll get a time-out the first time we check the state of the shutter, for good measure, go back 'delay' time into the past
   changedTime_ = GetCurrentMMTime();   

   // create default positions and labels
   const int bufSize = 1024;
   char buf[bufSize];
   for (long i=0; i<numPos_; i++)
   {
      snprintf(buf, bufSize, "State-%ld", i);
      SetPositionLabel(i, buf);
   }

   // State
   // -----
   CPropertyAction* pAct = new CPropertyAction (this, &CDemoFilterWheel::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // Label
   // -----
   pAct = new CPropertyAction (this, &CStateBase::OnLabel);
   ret = CreateProperty(MM::g_Keyword_Label, "", MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

bool CDemoFilterWheel::Busy()
{
   MM::MMTime interval = GetCurrentMMTime() - changedTime_;
   MM::MMTime delay(GetDelayMs()*1000.0);
   if (interval < delay)
      return true;
   else
      return false;
}


int CDemoFilterWheel::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int CDemoFilterWheel::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(position_);
      // nothing to do, let the caller to use cached property
   }
   else if (eAct == MM::AfterSet)
   {
      // Set timer for the Busy signal
      changedTime_ = GetCurrentMMTime();

      long pos;
      pProp->Get(pos);
      if (pos >= numPos_ || pos < 0)
      {
         pProp->Set(position_); // revert
         return ERR_UNKNOWN_POSITION;
      }
      position_ = pos;
   }

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// CDemoStateDevice implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

CDemoStateDevice::CDemoStateDevice() : 
   numPos_(10), 
   busy_(false), 
   initialized_(false), 
   changedTime_(0.0),
   position_(0)
{
   InitializeDefaultErrorMessages();
   EnableDelay(); // signals that the dealy setting will be used

   // Number of positions
   // -----
   CPropertyAction* pAct = new CPropertyAction (this, &CDemoStateDevice::OnNumberOfStates);
   CreateProperty("Number of positions", "0", MM::Integer, false, pAct, true);
}

CDemoStateDevice::~CDemoStateDevice()
{
   Shutdown();
}

void CDemoStateDevice::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_StateDeviceName);
}


int CDemoStateDevice::Initialize()
{
   if (initialized_)
      return DEVICE_OK;

   // set property list
   // -----------------
   
   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, g_StateDeviceName, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "Demo state device driver", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Set timer for the Busy signal, or we'll get a time-out the first time we check the state of the shutter, for good measure, go back 'delay' time into the past
   changedTime_ = GetCurrentMMTime();   

   // create default positions and labels
   const int bufSize = 1024;
   char buf[bufSize];
   for (long i=0; i<numPos_; i++)
   {
      snprintf(buf, bufSize, "State-%ld", i);
      SetPositionLabel(i, buf);
   }

   // State
   // -----
   CPropertyAction* pAct = new CPropertyAction (this, &CDemoStateDevice::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // Label
   // -----
   pAct = new CPropertyAction (this, &CStateBase::OnLabel);
   ret = CreateProperty(MM::g_Keyword_Label, "", MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

bool CDemoStateDevice::Busy()
{
   MM::MMTime interval = GetCurrentMMTime() - changedTime_;
   MM::MMTime delay(GetDelayMs()*1000.0);
   if (interval < delay)
      return true;
   else
      return false;
}


int CDemoStateDevice::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int CDemoStateDevice::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(position_);
      // nothing to do, let the caller to use cached property
   }
   else if (eAct == MM::AfterSet)
   {
      // Set timer for the Busy signal
      changedTime_ = GetCurrentMMTime();

      long pos;
      pProp->Get(pos);
      if (pos >= numPos_ || pos < 0)
      {
         pProp->Set(position_); // revert
         return ERR_UNKNOWN_POSITION;
      }
      position_ = pos;
   }

   return DEVICE_OK;
}

int CDemoStateDevice::OnNumberOfStates(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(numPos_);
   }
   else if (eAct == MM::AfterSet)
   {
      if (!initialized_)
         pProp->Get(numPos_);
   }

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// CDemoLightPath implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

CDemoLightPath::CDemoLightPath() : 
   numPos_(3), 
   busy_(false), 
   initialized_(false)
{
   InitializeDefaultErrorMessages();
}

CDemoLightPath::~CDemoLightPath()
{
   Shutdown();
}

void CDemoLightPath::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_LightPathDeviceName);
}


int CDemoLightPath::Initialize()
{
   if (initialized_)
      return DEVICE_OK;

   // set property list
   // -----------------
   
   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, g_LightPathDeviceName, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "Demo light-path driver", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // create default positions and labels
   const int bufSize = 1024;
   char buf[bufSize];
   for (long i=0; i<numPos_; i++)
   {
      snprintf(buf, bufSize, "State-%ld", i);
      SetPositionLabel(i, buf);
   }

   // State
   // -----
   CPropertyAction* pAct = new CPropertyAction (this, &CDemoLightPath::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // Label
   // -----
   pAct = new CPropertyAction (this, &CStateBase::OnLabel);
   ret = CreateProperty(MM::g_Keyword_Label, "", MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

int CDemoLightPath::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int CDemoLightPath::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // nothing to do, let the caller to use cached property
   }
   else if (eAct == MM::AfterSet)
   {
      long pos;
      pProp->Get(pos);
      if (pos >= numPos_ || pos < 0)
      {
         pProp->Set(position_); // revert
         return ERR_UNKNOWN_POSITION;
      }
      position_ = pos;
   }

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// CDemoObjectiveTurret implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

CDemoObjectiveTurret::CDemoObjectiveTurret() : 
   numPos_(6), 
   busy_(false), 
   initialized_(false)
{
   InitializeDefaultErrorMessages();
}

CDemoObjectiveTurret::~CDemoObjectiveTurret()
{
   Shutdown();
}

void CDemoObjectiveTurret::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_ObjectiveDeviceName);
}


int CDemoObjectiveTurret::Initialize()
{
   if (initialized_)
      return DEVICE_OK;

   // set property list
   // -----------------
   
   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, g_ObjectiveDeviceName, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "Demo objective turret driver", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // create default positions and labels
   const int bufSize = 1024;
   char buf[bufSize];
   for (long i=0; i<numPos_; i++)
   {
      snprintf(buf, bufSize, "Objective-%ld", i);
      SetPositionLabel(i, buf);
   }

   // State
   // -----
   CPropertyAction* pAct = new CPropertyAction (this, &CDemoObjectiveTurret::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // Label
   // -----
   pAct = new CPropertyAction (this, &CStateBase::OnLabel);
   ret = CreateProperty(MM::g_Keyword_Label, "", MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

int CDemoObjectiveTurret::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int CDemoObjectiveTurret::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // nothing to do, let the caller to use cached property
   }
   else if (eAct == MM::AfterSet)
   {
      long pos;
      pProp->Get(pos);
      if (pos >= numPos_ || pos < 0)
      {
         pProp->Set(position_); // revert
         return ERR_UNKNOWN_POSITION;
      }
      position_ = pos;
   }

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// CDemoStage implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~

CDemoStage::CDemoStage() : 
   stepSize_um_(0.025),
   pos_um_(0.0),
   busy_(false),
   initialized_(false),
   lowerLimit_(0.0),
   upperLimit_(20000.0)
{
   InitializeDefaultErrorMessages();
}

CDemoStage::~CDemoStage()
{
   Shutdown();
}

void CDemoStage::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_StageDeviceName);
}

int CDemoStage::Initialize()
{
   if (initialized_)
      return DEVICE_OK;

   // set property list
   // -----------------
   
   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, g_StageDeviceName, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "Demo stage driver", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Position
   // --------
   CPropertyAction* pAct = new CPropertyAction (this, &CDemoStage::OnPosition);
   ret = CreateProperty(MM::g_Keyword_Position, "0", MM::Float, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

int CDemoStage::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int CDemoStage::OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // nothing to do, let the caller use cached property
   }
   else if (eAct == MM::AfterSet)
   {
      double pos;
      pProp->Get(pos);
      if (pos > upperLimit_ || lowerLimit_ > pos)
      {
         pProp->Set(pos_um_); // revert
         return ERR_UNKNOWN_POSITION;
      }
      pos_um_ = pos;
   }

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// CDemoXYStage implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~

CDemoXYStage::CDemoXYStage() : 
   CXYStageBase<CDemoXYStage>(),
   stepSize_um_(0.015),
   posX_um_(0.0),
   posY_um_(0.0),
   busy_(false),
   initialized_(false),
   lowerLimit_(0.0),
   upperLimit_(20000.0)
{
   InitializeDefaultErrorMessages();
}

CDemoXYStage::~CDemoXYStage()
{
   Shutdown();
}

void CDemoXYStage::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_XYStageDeviceName);
}

int CDemoXYStage::Initialize()
{
   if (initialized_)
      return DEVICE_OK;

   // set property list
   // -----------------
   
   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, g_XYStageDeviceName, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "Demo XY stage driver", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

int CDemoXYStage::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////
// none implemented


///////////////////////////////////////////////////////////////////////////////
// CDemoShutter implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~~
void DemoShutter::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_ShutterDeviceName);
}

int DemoShutter::Initialize()
{
   if (initialized_)
      return DEVICE_OK;

   // set property list
   // -----------------
   
   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, g_ShutterDeviceName, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "Demo shutter driver", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   changedTime_ = GetCurrentMMTime();

   // state
   CPropertyAction* pAct = new CPropertyAction (this, &DemoShutter::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct); 
   if (ret != DEVICE_OK) 
      return ret; 

   AddAllowedValue(MM::g_Keyword_State, "0"); // Closed
   AddAllowedValue(MM::g_Keyword_State, "1"); // Open

   state_ = false;
   
   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}


bool DemoShutter::Busy()
{
   MM::MMTime interval = GetCurrentMMTime() - changedTime_;

   if ( interval < MM::MMTime(1000.0 * GetDelayMs()))
      return true;
   else
      return false;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int DemoShutter::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      if (state_)
         pProp->Set(1L);
      else
         pProp->Set(0L);
   }
   else if (eAct == MM::AfterSet)
   {
      // Set timer for the Busy signal
      changedTime_ = GetCurrentMMTime();

      long pos;
      pProp->Get(pos);

      // apply the value
      state_ = pos == 0 ? false : true;
   }

   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// CDemoAutoFocus implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
void DemoAutoFocus::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_AutoFocusDeviceName);
}

int DemoAutoFocus::Initialize()
{
   if (initialized_)
      return DEVICE_OK;

   // set property list
   // -----------------
   
   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, g_AutoFocusDeviceName, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "Demo auto-focus adapter", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;
   
   running_ = false;   
   
   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// CDemoMagnifier implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~~
int DemoMagnifier::Initialize()
{
   CPropertyAction* pAct = new CPropertyAction (this, &DemoMagnifier::OnPosition);
   int ret = CreateProperty("Position", "1x", MM::String, false, pAct); 
   if (ret != DEVICE_OK) 
      return ret; 

   position = 0;

   AddAllowedValue("Position", "1x"); 
   AddAllowedValue("Position", "1.6x"); 
   
   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

double DemoMagnifier::GetMagnification() {
   if (position == 0)
      return 1.0;
   return 1.6;
}

int DemoMagnifier::OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct) 
{
   if (eAct == MM::BeforeGet)
   {
      // nothing to do, let the caller use cached property
   }
   else if (eAct == MM::AfterSet)
   {
      std::string pos;
      pProp->Get(pos);
      if (pos == "1x")
            position = 0;
      else
         position = 1;
   }

   return DEVICE_OK;
}


/****
 * Demo DA device
 */

DemoDA::DemoDA () : 
   volt_(0), 
   gatedVolts_(0), 
   open_(true) 
{
}

DemoDA::~DemoDA() {
}

int DemoDA::SetGateOpen(bool open) 
{
   open_ = open; 
   if (open_) 
      gatedVolts_ = volt_; 
   else 
      gatedVolts_ = 0;
   
   return DEVICE_OK;
}

int DemoDA::GetGateOpen(bool& open) 
{
   open = open_; 
   return DEVICE_OK;
}

int DemoDA::SetSignal(double volts) {
   volt_ = volts; 
   if (open_)
      gatedVolts_ = volts;

   return DEVICE_OK;
}

int DemoDA::GetSignal(double& volts) 
{
   volts = volt_; 
   return DEVICE_OK;
}

