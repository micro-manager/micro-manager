///////////////////////////////////////////////////////////////////////////////
// FILE:          MMCamera.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Skeleton code for the micro-manager camera adapter. Use it as
//                starting point for writing custom device adapters
//                
// AUTHOR:        Nenad Amodaj, http://nenad.amodaj.com
//                
// COPYRIGHT:     University of California, San Francisco, 2011
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

#include "MMCamera.h"
#include "ModuleInterface.h"

using namespace std;

const char* g_CameraName = "MMCam";

const char* g_PixelType_8bit = "8bit";
const char* g_PixelType_16bit = "16bit";

const char* g_CameraModelProperty = "Model";
const char* g_CameraModel_A = "A";
const char* g_CameraModel_B = "B";

// windows DLL entry code
#ifdef WIN32
BOOL APIENTRY DllMain(  HANDLE /*hModule*/, 
                        DWORD  ul_reason_for_call, 
                        LPVOID /*lpReserved*/ )
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

/**
 * List all supported hardware devices here
 */
MODULE_API void InitializeModuleData()
{
   AddAvailableDeviceName(g_CameraName, "Micro-manager example camera");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   // decide which device class to create based on the deviceName parameter
   if (strcmp(deviceName, g_CameraName) == 0)
   {
      // create camera
      return new MMCamera();
   }

   // ...supplied name not recognized
   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
// MMCamera implementation
// ~~~~~~~~~~~~~~~~~~~~~~~

/**
* MMCamera constructor.
* Setup default all variables and create device properties required to exist
* before intialization. In this case, no such properties were required. All
* properties will be created in the Initialize() method.
*
* As a general guideline Micro-Manager devices do not access hardware in the
* the constructor. We should do as little as possible in the constructor and
* perform most of the initialization in the Initialize() method.
*/
MMCamera::MMCamera() :
   binning_ (1),
   gain_(0.8),
   bytesPerPixel_(1),
   initialized_(false),
   exposureMs_(10.0),
   roiX_(0),
   roiY_(0),
   thd_(0)
{
   // call the base class method to set-up default error codes/messages
   InitializeDefaultErrorMessages();

   // Description property
   int ret = CreateProperty(MM::g_Keyword_Description, "MMCamera example adapter", MM::String, true);
   assert(ret == DEVICE_OK);

   // camera type pre-initialization property
   ret = CreateProperty(g_CameraModelProperty, g_CameraModel_A, MM::String, false, 0, true);
   assert(ret == DEVICE_OK);

   vector<string> modelValues;
   modelValues.push_back(g_CameraModel_A);
   modelValues.push_back(g_CameraModel_A); 

   ret = SetAllowedValues(g_CameraModelProperty, modelValues);
   assert(ret == DEVICE_OK);

   // create live video thread
   thd_ = new SequenceThread(this);
}

/**
* MMCamera destructor.
* If this device used as intended within the Micro-Manager system,
* Shutdown() will be always called before the destructor. But in any case
* we need to make sure that all resources are properly released even if
* Shutdown() was not called.
*/
MMCamera::~MMCamera()
{
   if (initialized_)
      Shutdown();

   delete thd_;
}

/**
* Obtains device name.
* Required by the MM::Device API.
*/
void MMCamera::GetName(char* name) const
{
   // We just return the name we use for referring to this
   // device adapter.
   CDeviceUtils::CopyLimitedString(name, g_CameraName);
}

/**
* Intializes the hardware.
* Typically we access and initialize hardware at this point.
* Device properties are typically created here as well.
* Required by the MM::Device API.
*/
int MMCamera::Initialize()
{
   if (initialized_)
      return DEVICE_OK;

   // set property list
   // -----------------

   // binning
   CPropertyAction *pAct = new CPropertyAction (this, &MMCamera::OnBinning);
   int ret = CreateProperty(MM::g_Keyword_Binning, "1", MM::Integer, false, pAct);
   assert(ret == DEVICE_OK);

   vector<string> binningValues;
   binningValues.push_back("1");
   binningValues.push_back("2"); 

   ret = SetAllowedValues(MM::g_Keyword_Binning, binningValues);
   assert(ret == DEVICE_OK);

   // pixel type
   pAct = new CPropertyAction (this, &MMCamera::OnPixelType);
   ret = CreateProperty(MM::g_Keyword_PixelType, g_PixelType_8bit, MM::String, false, pAct);
   assert(ret == DEVICE_OK);

   vector<string> pixelTypeValues;
   pixelTypeValues.push_back(g_PixelType_8bit);
   pixelTypeValues.push_back(g_PixelType_16bit); 

   ret = SetAllowedValues(MM::g_Keyword_PixelType, pixelTypeValues);
   assert(ret == DEVICE_OK);

   ret = CreateProperty(MM::g_Keyword_Gain, "0.8", MM::Float, false);
   assert(ret == DEVICE_OK);
   SetPropertyLimits(MM::g_Keyword_Gain, 0.0, 1.0);

   // synchronize all properties
   // --------------------------
   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   // setup the buffer
   // ----------------
   ret = ResizeImageBuffer();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;
   return DEVICE_OK;
}

/**
* Shuts down (unloads) the device.
* Ideally this method will completely unload the device and release all resources.
* Shutdown() may be called multiple times in a row.
* Required by the MM::Device API.
*/
int MMCamera::Shutdown()
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
int MMCamera::SnapImage()
{
   GenerateImage();
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
const unsigned char* MMCamera::GetImageBuffer()
{
   return const_cast<unsigned char*>(img_.GetPixels());
}

/**
* Returns image buffer X-size in pixels.
* Required by the MM::Camera API.
*/
unsigned MMCamera::GetImageWidth() const
{
   return img_.Width();
}

/**
* Returns image buffer Y-size in pixels.
* Required by the MM::Camera API.
*/
unsigned MMCamera::GetImageHeight() const
{
   return img_.Height();
}

/**
* Returns image buffer pixel depth in bytes.
* Required by the MM::Camera API.
*/
unsigned MMCamera::GetImageBytesPerPixel() const
{
   return img_.Depth();
} 

/**
* Returns the bit depth (dynamic range) of the pixel.
* This does not affect the buffer size, it just gives the client application
* a guideline on how to interpret pixel values.
* Required by the MM::Camera API.
*/
unsigned MMCamera::GetBitDepth() const
{
   return img_.Depth() == 1 ? 8 : MAX_BIT_DEPTH;
}

/**
* Returns the size in bytes of the image buffer.
* Required by the MM::Camera API.
*/
long MMCamera::GetImageBufferSize() const
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
int MMCamera::SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize)
{
   if (xSize == 0 && ySize == 0)
   {
      // effectively clear ROI
      ResizeImageBuffer();
      roiX_ = 0;
      roiY_ = 0;
   }
   else
   {
      // apply ROI
      img_.Resize(xSize, ySize);
      roiX_ = x;
      roiY_ = y;
   }
   return DEVICE_OK;
}

/**
* Returns the actual dimensions of the current ROI.
* Required by the MM::Camera API.
*/
int MMCamera::GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize)
{
   x = roiX_;
   y = roiY_;

   xSize = img_.Width();
   ySize = img_.Height();

   return DEVICE_OK;
}

/**
* Resets the Region of Interest to full frame.
* Required by the MM::Camera API.
*/
int MMCamera::ClearROI()
{
   ResizeImageBuffer();
   roiX_ = 0;
   roiY_ = 0;
      
   return DEVICE_OK;
}

/**
* Returns the current exposure setting in milliseconds.
* Required by the MM::Camera API.
*/
double MMCamera::GetExposure() const
{
   return exposureMs_;
}

/**
* Sets exposure in milliseconds.
* Required by the MM::Camera API.
*/
void MMCamera::SetExposure(double exp)
{
   exposureMs_ = exp;
}

/**
* Returns the current binning factor.
* Required by the MM::Camera API.
*/
int MMCamera::GetBinning() const
{
   return binning_;
}

/**
* Sets binning factor.
* Required by the MM::Camera API.
*/
int MMCamera::SetBinning(int binF)
{
   return SetProperty(MM::g_Keyword_Binning, CDeviceUtils::ConvertToString(binF));
}

int MMCamera::PrepareSequenceAcqusition()
{
   if (IsCapturing())
      return DEVICE_CAMERA_BUSY_ACQUIRING;

   int ret = GetCoreCallback()->PrepareForAcq(this);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}


/**
 * Required by the MM::Camera API
 * Please implement this yourself and do not rely on the base class implementation
 * The Base class implementation is deprecated and will be removed shortly
 */
int MMCamera::StartSequenceAcquisition(double interval) {

   return StartSequenceAcquisition(LONG_MAX, interval, false);            
}

/**                                                                       
* Stop and wait for the Sequence thread finished                                   
*/                                                                        
int MMCamera::StopSequenceAcquisition()                                     
{                                                                         
   if (!thd_->IsStopped()) {
      thd_->Stop();                                                       
      thd_->wait();                                                       
   }                                                                      
                                                                          
   return DEVICE_OK;                                                      
} 

/**
* Simple implementation of Sequence Acquisition
* A sequence acquisition should run on its own thread and transport new images
* coming of the camera into the MMCore circular buffer.
*/
int MMCamera::StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow)
{
   if (IsCapturing())
      return DEVICE_CAMERA_BUSY_ACQUIRING;

   int ret = GetCoreCallback()->PrepareForAcq(this);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

/*
 * Inserts Image and MetaData into MMCore circular Buffer
 */
int MMCamera::InsertImage()
{

   MM::MMTime timeStamp = this->GetCurrentMMTime();
   char label[MM::MaxStrLength];
   this->GetLabel(label);
 
   // Important:  metadata about the image are generated here:
   Metadata md;

   return DEVICE_OK;
}


bool MMCamera::IsCapturing() {
   return !thd_->IsStopped();
}


///////////////////////////////////////////////////////////////////////////////
// MMCamera Action handlers
///////////////////////////////////////////////////////////////////////////////

/**
* Handles "Binning" property.
*/
int MMCamera::OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      long binSize;
      pProp->Get(binSize);
      binning_ = (int)binSize;
      return ResizeImageBuffer();
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set((long)binning_);
   }

   return DEVICE_OK;
}

/**
* Handles "PixelType" property.
*/
int MMCamera::OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      string val;
      pProp->Get(val);
      if (val.compare(g_PixelType_8bit) == 0)
         bytesPerPixel_ = 1;
      else if (val.compare(g_PixelType_16bit) == 0)
         bytesPerPixel_ = 2;
      else
         assert(false);

      ResizeImageBuffer();
   }
   else if (eAct == MM::BeforeGet)
   {
      if (bytesPerPixel_ == 1)
         pProp->Set(g_PixelType_8bit);
      else if (bytesPerPixel_ == 2)
         pProp->Set(g_PixelType_16bit);
      else
         assert(false); // this should never happen
   }

   return DEVICE_OK;
}

/**
* Handles "Gain" property.
*/
int MMCamera::OnGain(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      pProp->Get(gain_);
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set(gain_);
   }

   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// Private MMCamera methods
///////////////////////////////////////////////////////////////////////////////

/**
* Sync internal image buffer size to the chosen property values.
*/
int MMCamera::ResizeImageBuffer()
{
   img_.Resize(IMAGE_WIDTH/binning_, IMAGE_HEIGHT/binning_, bytesPerPixel_);

   return DEVICE_OK;
}

/**
 * Generate an image with fixed value for all pixels
 */
void MMCamera::GenerateImage()
{
   const int maxValue = (1 << MAX_BIT_DEPTH) - 1; // max for the 12 bit camera
   const double maxExp = 1000;
   double step = maxValue/maxExp;
   unsigned char* pBuf = const_cast<unsigned char*>(img_.GetPixels());
   memset(pBuf, (int) (step * max(exposureMs_, maxExp)), img_.Height()*img_.Width()*img_.Depth());
}
