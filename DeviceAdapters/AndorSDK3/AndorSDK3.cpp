///////////////////////////////////////////////////////////////////////////////
// FILE:          AndorSDK3Camera.cpp
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
// CVS:           $Id: AndorSDK3Camera.cpp 6819 2011-03-30 18:21:18Z karlh $
//

#include "AndorSDK3.h"
#include <cstdio>
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include "../../MMCore/Error.h"
#include "WriteCompactTiffRGB.h"
#include <sstream>
#include <algorithm>
#include <iostream>
#include <map>
#include "datapacking.h"
#include "triggerremapper.h"
#include "andorwindowstime.h"
#include "lineparser.h"

using namespace std;
const double CAndorSDK3Camera::nominalPixelSizeUm_ = 1.0;
double g_IntensityFactor_ = 1.0;

// External names used used by the rest of the system
// to load particular device from the "AndorSDK3Camera.dll" library
const char* const g_CameraName = "Neo";
const char* const g_CameraDeviceName = "Andor Neo";
const char* const g_CameraDeviceDescription = "Andor sCMOS Camera Device Adapter";

const char* const g_CameraDefaultBinning = "1x1";

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

/**
 * List all suppoerted hardware devices here
 * Do not discover devices at runtime.  To avoid warnings about missing DLLs, Micro-Manager
 * maintains a list of supported device (MMDeviceList.txt).  This list is generated using 
 * information supplied by this function, so runtime discovery will create problems.
 */
MODULE_API void InitializeModuleData()
{
   AddAvailableDeviceName(g_CameraName, g_CameraDeviceDescription);
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   // decide which device class to create based on the deviceName parameter
   if (strcmp(deviceName, g_CameraName) == 0)
   {
      // create camera
      MM::Device* openedDevice = new CAndorSDK3Camera();
      if(openedDevice) {
         CAndorSDK3Camera * cameraPtr = dynamic_cast<CAndorSDK3Camera*>(openedDevice);
         if (cameraPtr && cameraPtr->GetCameraPresent() ) {
            return openedDevice;
         }
      }
      else {
         return 0;
      }
   }

   // ...supplied name not recognized
   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
// CAndorSDK3Camera implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~

/**
* CAndorSDK3Camera constructor.
* Setup default all variables and create device properties required to exist
* before intialization. In this case, no such properties were required. All
* properties will be created in the Initialize() method.
*
* As a general guideline Micro-Manager devices do not access hardware in the
* the constructor. We should do as little as possible in the constructor and
* perform most of the initialization in the Initialize() method.
*/
CAndorSDK3Camera::CAndorSDK3Camera() :
   CCameraBase<CAndorSDK3Camera> (),
   deviceManager(NULL),
   systemDevice(NULL),
   cameraDevice(NULL),
   imageSizeBytes(NULL),
   cycleMode(NULL),
   exposureTime(NULL),
   bufferControl(NULL),
   startAcquisitionCommand(NULL),
   stopAcquisitionCommand(NULL),
   triggerMode(NULL),
   sendSoftwareTrigger(NULL),
   frameCount(NULL),
   frameRate(NULL),
   initialized_(false),
   b_cameraPresent_(false),
   number_of_devices_(0),
   sequenceStartTime_(0),
   nComponents_(1),
   pDemoResourceLock_(0),
   image_buffers_(NULL),
   d_frameRate_(0),
   keep_trying_(false),
   roiX_(0),
   roiY_(0)
{
   // call the base class method to set-up default error codes/messages
   InitializeDefaultErrorMessages();
   readoutStartTime_ = GetCurrentMMTime();
#ifdef TESTRESOURCELOCKING
   pDemoResourceLock_ = new MMThreadLock();
#endif
   thd_ = new MySequenceThread(this);
   
   // Create an atcore++ device manager
   deviceManager = new TDeviceManager;

   // Open a system device
   systemDevice = deviceManager->OpenSystemDevice();
   IInteger* deviceCount = systemDevice->GetInteger(L"DeviceCount");
   SetNumberOfDevicesPresent( static_cast<int>(deviceCount->Get()) );
   systemDevice->Release(deviceCount);

   if (GetNumberOfDevicesPresent() > 0) {
      for (int i=0; i<GetNumberOfDevicesPresent(); i++) {
         cameraDevice = deviceManager->OpenDevice(i);
         IString* cameraModelString = cameraDevice->GetString(L"CameraModel");
         std::wstring temp_ws = cameraModelString->Get();
         temp_ws.erase(3);
         cameraDevice->Release(cameraModelString);
         if (temp_ws.compare(L"DC-") == 0) {
            b_cameraPresent_ = true;
            break;
         }
         else {
            deviceManager->CloseDevice(cameraDevice);
         }
      }
   }

   if ( GetCameraPresent() ) {
      imageSizeBytes = cameraDevice->GetInteger(L"ImageSizeBytes");
      exposureTime = cameraDevice->GetFloat(L"ExposureTime");
      bufferControl = cameraDevice->GetBufferControl();
      startAcquisitionCommand = cameraDevice->GetCommand(L"AcquisitionStart");
      stopAcquisitionCommand = cameraDevice->GetCommand(L"AcquisitionStop");
      cycleMode = cameraDevice->GetEnum(L"CycleMode");
      triggerMode = cameraDevice->GetEnum(L"TriggerMode");
      sendSoftwareTrigger = cameraDevice->GetCommand(L"SoftwareTrigger");
      frameCount = cameraDevice->GetInteger(L"FrameCount");
      frameRate = cameraDevice->GetFloat(L"FrameRate");

      snapShotController_ = new SnapShotControl(cameraDevice);
   }
}

/**
* CAndorSDK3Camera destructor.
* If this device used as intended within the Micro-Manager system,
* Shutdown() will be always called before the destructor. But in any case
* we need to make sure that all resources are properly released even if
* Shutdown() was not called.
*/
CAndorSDK3Camera::~CAndorSDK3Camera()
{
   StopSequenceAcquisition();
   delete thd_;
   delete pDemoResourceLock_;

   // Clean up atcore++ stuff
   cameraDevice->Release(imageSizeBytes);
   cameraDevice->Release(exposureTime);
   cameraDevice->ReleaseBufferControl(bufferControl);
   cameraDevice->Release(startAcquisitionCommand);
   cameraDevice->Release(stopAcquisitionCommand);
   cameraDevice->Release(cycleMode);
   cameraDevice->Release(triggerMode);
   cameraDevice->Release(sendSoftwareTrigger);
   cameraDevice->Release(frameCount);
   cameraDevice->Release(frameRate);
   delete snapShotController_;
   deviceManager->CloseDevice(cameraDevice);
   deviceManager->CloseDevice(systemDevice);
   delete deviceManager;
}

/**
* Obtains device name.
* Required by the MM::Device API.
*/
void CAndorSDK3Camera::GetName(char* name) const
{
   // We just return the name we use for referring to this
   // device adapter.
   CDeviceUtils::CopyLimitedString(name, g_CameraName);
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
int CAndorSDK3Camera::Initialize()
{
   if (initialized_)
      return DEVICE_OK;

   if(0 == GetNumberOfDevicesPresent() ) {
      initialized_ = false;
      return DEVICE_NOT_CONNECTED;
   }

   // set property list
   // -----------------

   // Name
   int nRet = CreateProperty(MM::g_Keyword_Name, g_CameraName, MM::String, true);
   if (DEVICE_OK != nRet)
      return nRet;

   // CameraName
   nRet = CreateProperty(MM::g_Keyword_CameraName, g_CameraDeviceName, MM::String, true);
   assert(nRet == DEVICE_OK);

   // Description
   nRet = CreateProperty(MM::g_Keyword_Description, g_CameraDeviceDescription, MM::String, true);
   if (DEVICE_OK != nRet)
      return nRet;

   // CameraID
   IString* cameraSerialNumber = cameraDevice->GetString(L"SerialNumber");
   std::wstring temp_ws = cameraSerialNumber->Get();
   char * p_cameraSerialNumber = new char[temp_ws.size() + 1];
   memset(p_cameraSerialNumber, '\0', temp_ws.size() + 1);
   wcstombs(p_cameraSerialNumber, temp_ws.c_str(), temp_ws.size() );
   cameraDevice->Release(cameraSerialNumber);
   nRet = CreateProperty(MM::g_Keyword_CameraID, p_cameraSerialNumber, MM::String, true);
   assert(nRet == DEVICE_OK);
   delete [] p_cameraSerialNumber;

   // Properties
   binning_property = new TEnumProperty(MM::g_Keyword_Binning, cameraDevice->GetEnum(L"AOIBinning"), 
                                          this, thd_, snapShotController_, false, false);
   AddAllowedValue(MM::g_Keyword_Binning, g_CameraDefaultBinning);
   SetProperty(MM::g_Keyword_Binning, g_CameraDefaultBinning);

   preAmpGain_Enum = cameraDevice->GetEnum(L"PreAmpGainControl");
   preAmpGain_property = new TEnumProperty(MM::g_Keyword_Gain, preAmpGain_Enum,
                                       this, thd_, snapShotController_, false, true);

   electronicShutteringMode_Enum = cameraDevice->GetEnum(L"ElectronicShutteringMode");
   electronicShutteringMode_property = new TEnumProperty("ElectronicShutteringMode",
               electronicShutteringMode_Enum, this, thd_, snapShotController_, false, false);

   temperatureControl_Enum = cameraDevice->GetEnum(L"TemperatureControl");
   temperatureControl_proptery = new TEnumProperty("TemperatureControl",
              temperatureControl_Enum, this, thd_, snapShotController_, false, false);

   pixelReadoutRate_Enum = cameraDevice->GetEnum(L"PixelReadoutRate");
   pixelReadoutRate_property = new TEnumProperty("PixelReadoutRate",
                pixelReadoutRate_Enum, this, thd_, snapShotController_, false, true);

   pixelEncoding_Enum = cameraDevice->GetEnum(L"PixelEncoding");
   pixelEncoding_property = new TEnumProperty("PixelEncoding",
                    pixelEncoding_Enum, this, thd_, snapShotController_, true, false);

   accumulationLength_Integer = cameraDevice->GetInteger(L"AccumulateCount");
   accumulationLength_property = new TIntegerProperty("AccumulateCount",
      accumulationLength_Integer, this, thd_, snapShotController_, false, false);

   temperatureStatus_Enum = cameraDevice->GetEnum(L"TemperatureStatus");
   temperatureStatus_property = new TEnumProperty("TemperatureStatus",
               temperatureStatus_Enum, this, thd_, snapShotController_, true, false);

   fanSpeed_Enum = cameraDevice->GetEnum(L"FanSpeed");
   fanSpeed_property = new TEnumProperty("FanSpeed", fanSpeed_Enum, this,
                                             thd_, snapShotController_, false, false);

   spuriousNoiseFilter_Boolean = cameraDevice->GetBool(L"SpuriousNoiseFilter");
   spuriousNoiseFilter_property = new TBooleanProperty("SpuriousNoiseFilter",
                  spuriousNoiseFilter_Boolean, this, thd_, snapShotController_, false);

   sensorCooling_Boolean = cameraDevice->GetBool(L"SensorCooling");
   sensorCooling_property = new TBooleanProperty("SensorCooling",
               sensorCooling_Boolean, this, thd_, snapShotController_, false);
   IBool* sensorCooling = cameraDevice->GetBool(L"SensorCooling");
   sensorCooling->Set(true);
   cameraDevice->Release(sensorCooling);

   overlap_Boolean = cameraDevice->GetBool(L"Overlap");
   overlap_property = new TBooleanProperty("Overlap", overlap_Boolean,
                                      this, thd_, snapShotController_, false);

   // Because the camera wil back up with images in live mode with internal trigger, switch
   // to software trigger by default
   triggerMode->Set(L"Software");
   triggerMode_Enum = cameraDevice->GetEnum(L"TriggerMode");
   triggerMode_remapper = new TTriggerRemapper(snapShotController_, triggerMode_Enum);
   std::map<std::wstring, std::wstring> triggerMode_map;
   triggerMode_map[L"Software"] = L"Software (Recommended for Live Mode)";
   triggerMode_map[L"Internal"] = L"Internal (Recommended for fast acquisitions)";
   triggerMode_valueMapper = new TAndorEnumValueMapper(
                                            triggerMode_remapper, triggerMode_map);
   triggerMode_property = new TEnumProperty("TriggerMode", triggerMode_valueMapper,
                                       this, thd_, snapShotController_, false, false);
   
   readTemperature_Float = cameraDevice->GetFloat(L"SensorTemperature");
   readTemperature_property = new TFloatProperty("SensorTemperature",
          readTemperature_Float, this, thd_, snapShotController_, true, false);

   frameRate_Float = cameraDevice->GetFloat(L"FrameRate");
   frameRate_floatHolder = new TAndorFloatHolder(snapShotController_, frameRate_Float);
   frameRate_property = new TFloatProperty("FrameRate", frameRate_floatHolder,
                                 this, thd_, snapShotController_, false, true);

   exposureTime_Float = cameraDevice->GetFloat(L"ExposureTime");
   exposureTime_valueMapper = new TAndorFloatValueMapper(exposureTime_Float, 1000);
   exposureTime_property = new TFloatProperty(MM::g_Keyword_Exposure,
          exposureTime_valueMapper, this, thd_, snapShotController_, false, false);

   aoi_property_ = new TAOIProperty("AcquisitionWindow", this, cameraDevice, thd_,
               snapShotController_, false);

   // synchronize all properties
   // --------------------------
   nRet = UpdateStatus();
   if (nRet != DEVICE_OK)
      return nRet;

   initialized_ = true;

   ClearROI();

#ifdef TESTRESOURCELOCKING
   TestResourceLocking(true);
   LogMessage("TestResourceLocking OK",true);
#endif

   snapShotController_->prepareCamera();
   snapShotController_->poiseForSnapShot();
   return DEVICE_OK;
}

/**
* Shuts down (unloads) the device.
* Required by the MM::Device API.
* Ideally this method will completely unload the device and release all resources.
* Shutdown() may be called multiple times in a row.
* After Shutdown() we should be allowed to call Initialize() again to load the device
* without causing problems.
*/
int CAndorSDK3Camera::Shutdown()
{
   if(initialized_) {
      delete binning_property;
      delete preAmpGain_property;
      delete electronicShutteringMode_property;
      delete temperatureControl_proptery;
      delete pixelReadoutRate_property;
      delete pixelEncoding_property;
      delete accumulationLength_property;
      delete readTemperature_property;
      delete temperatureStatus_property;
      delete sensorCooling_property;
      delete overlap_property;
      delete frameRate_property;
      delete fanSpeed_property;
      delete spuriousNoiseFilter_property;
      delete aoi_property_;
      delete triggerMode_valueMapper;
      delete exposureTime_property;
      delete exposureTime_valueMapper;
      delete accumulationLength_Integer;

      // clean up objects used by the property browser
      cameraDevice->Release(preAmpGain_Enum);
      cameraDevice->Release(electronicShutteringMode_Enum);
      cameraDevice->Release(temperatureControl_Enum);
      cameraDevice->Release(pixelReadoutRate_Enum);
      cameraDevice->Release(pixelEncoding_Enum);
      cameraDevice->Release(temperatureStatus_Enum);
      cameraDevice->Release(fanSpeed_Enum);
      cameraDevice->Release(triggerMode_Enum);
      cameraDevice->Release(readTemperature_Float);
      cameraDevice->Release(frameRate_Float);
      cameraDevice->Release(exposureTime_Float);
   }

   initialized_ = false;
   return DEVICE_OK;
}


void CAndorSDK3Camera::UnpackDataWithPadding(unsigned char* _pucSrcBuffer)
{
   andoru32 u32_pixelHeight = static_cast<andoru32>(aoi_property_->GetHeight() );
   andoru32 u32_pixelWidth = static_cast<andoru32>(aoi_property_->GetWidth() );
   andoru32 u32_destinationStride = u32_pixelWidth * aoi_property_->GetBytesPerPixel();
   andoru32 u32_sourceStride = static_cast<andoru32>(aoi_property_->GetStride() );

   TLineParser * p_sourceLineParser = NULL;
   TLineParser * p_destinationLineParser = NULL;

   p_sourceLineParser = new TLineParser(u32_sourceStride, u32_pixelHeight);
   p_destinationLineParser = new TLineParser(u32_destinationStride, u32_pixelHeight);

   MMThreadGuard g(imgPixelsLock_);
   unsigned char * pucDstData = const_cast<unsigned char *>(img_.GetPixels());
   
   unsigned char * puc_sourceLine = p_sourceLineParser->GetFirstLine(_pucSrcBuffer, u32_sourceStride * u32_pixelHeight);
   unsigned char * puc_destinationLine = p_destinationLineParser->GetFirstLine(pucDstData, u32_destinationStride * u32_pixelHeight);

   while (puc_sourceLine != NULL && puc_destinationLine != NULL)
   {
      if ( snapShotController_->isMono12Packed() ) 
      {
         // Convert from Mono12Packed to Mono12 using stride
         unsigned short * unpacked_buffer = reinterpret_cast<unsigned short *>(puc_destinationLine);
         for (andoru32 i=0; i<u32_pixelWidth/2; ++i) 
         {
            unpacked_buffer[i*2  ] = static_cast<unsigned short>(EXTRACTLOWPACKED(puc_sourceLine));
            unpacked_buffer[i*2+1] = static_cast<unsigned short>(EXTRACTHIGHPACKED(puc_sourceLine));
            puc_sourceLine += 3;
         }
         if (u32_pixelWidth%2 != 0) 
         {
            unpacked_buffer[u32_pixelWidth-1] = static_cast<unsigned short>(EXTRACTLOWPACKED(puc_sourceLine));
         }
      }
      else
      {
         memcpy(puc_destinationLine, puc_sourceLine, u32_destinationStride);
      }
      puc_sourceLine = p_sourceLineParser->GetNextLine();
      puc_destinationLine = p_destinationLineParser->GetNextLine();
   } //end While loop

   delete p_sourceLineParser;
   delete p_destinationLineParser;
}


/**
* Performs exposure and grabs a single image.
* This function should block during the actual exposure and return immediately afterwards 
* (i.e., before readout).  This behavior is needed for proper synchronization with the shutter.
* Required by the MM::Camera API.
*/
int CAndorSDK3Camera::SnapImage()
{
   unsigned char* return_buffer = NULL;

   snapShotController_->takeSnapShot(return_buffer);

   UnpackDataWithPadding(return_buffer);

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
const unsigned char* CAndorSDK3Camera::GetImageBuffer()
{
   MMThreadGuard g(imgPixelsLock_);
   MM::MMTime readoutTime(readoutUs_);
   while (readoutTime > (GetCurrentMMTime() - readoutStartTime_)) {}

		
   MM::ImageProcessor* ip = NULL;
#ifdef PROCESSIMAGEINDEVICEADAPTER
   ip = GetCoreCallback()->GetImageProcessor(this);
#endif
   unsigned char *pB = (unsigned char*)(img_.GetPixels());

   if (ip)
   {
      // huh...
      ip->Process(pB, GetImageWidth(), GetImageHeight(), GetImageBytesPerPixel());
   }
   return pB;
}

/**
* Returns image buffer X-size in pixels.
* Required by the MM::Camera API.
*/
unsigned CAndorSDK3Camera::GetImageWidth() const
{
   return aoi_property_->GetWidth();
}

/**
* Returns image buffer Y-size in pixels.
* Required by the MM::Camera API.
*/
unsigned CAndorSDK3Camera::GetImageHeight() const
{
   return aoi_property_->GetHeight();
}

/**
* Returns image buffer pixel depth in bytes.
* Required by the MM::Camera API.
*/
unsigned CAndorSDK3Camera::GetImageBytesPerPixel() const
{
   return aoi_property_->GetBytesPerPixel();
} 

/**
* Returns the bit depth (dynamic range) of the pixel.
* This does not affect the buffer size, it just gives the client application
* a guideline on how to interpret pixel values.
* Required by the MM::Camera API.
*/
unsigned CAndorSDK3Camera::GetBitDepth() const
{
   IEnum * pBitDepth = cameraDevice->GetEnum(L"BitDepth");
   wstring ws_bitdepth = pBitDepth->GetStringByIndex(pBitDepth->GetIndex());
   ws_bitdepth.erase(2);
   cameraDevice->Release(pBitDepth);
   unsigned ret_bitDepth = 11;
   wstringstream wss(ws_bitdepth);
   wss >> ret_bitDepth;
   return ret_bitDepth;
}

/**
* Returns the size in bytes of the image buffer.
* Required by the MM::Camera API.
*/
long CAndorSDK3Camera::GetImageBufferSize() const
{
   return img_.Width() * img_.Height() * img_.Depth();
}


int CAndorSDK3Camera::ResizeImageBuffer()
{
   if(initialized_) {
      img_.Resize(aoi_property_->GetWidth(), aoi_property_->GetHeight(), aoi_property_->GetBytesPerPixel() );
   }
   return DEVICE_OK;
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
* @param x - top-left corner coordinate - Left
* @param y - top-left corner coordinate - Top
* @param xSize - width
* @param ySize - height
*/
int CAndorSDK3Camera::SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize)
{
   if (xSize == 0 && ySize == 0)
   {
      ClearROI();
   }
   else
   {
      //Adjust for binning
      int binning = GetBinning();
      x *= binning;
      y *= binning;

      aoi_property_->SetCustomAOISize(x, y, xSize, ySize);
      roiX_ = x;
      roiY_ = y;
   }
   return DEVICE_OK;
}

/**
* Returns the actual dimensions of the current ROI.
* Required by the MM::Camera API.
*/
int CAndorSDK3Camera::GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize)
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
int CAndorSDK3Camera::ClearROI()
{
   aoi_property_->ResetToFullImage();
   roiX_ = 0;
   roiY_ = 0;
      
   return DEVICE_OK;
}

/**
* Returns the current exposure setting in milliseconds.
* Required by the MM::Camera API.
*/
double CAndorSDK3Camera::GetExposure() const
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
void CAndorSDK3Camera::SetExposure(double exp)
{
   SetProperty(MM::g_Keyword_Exposure, CDeviceUtils::ConvertToString(exp));
}

/**
* Returns the current binning factor.
* Required by the MM::Camera API.
*/
int CAndorSDK3Camera::GetBinning() const
{
   char buf[MM::MaxStrLength];
   int ret = GetProperty(MM::g_Keyword_Binning, buf);
   if (ret != DEVICE_OK)
      return 1;
   return atoi(&buf[0]);
}

/**
* Sets binning factor.
* Required by the MM::Camera API.
* Because SDK3 cannot perform binning this function will not be called. However
* it is a virtual function in MMDevice and so must be overloaded
*/
int CAndorSDK3Camera::SetBinning(int binF)
{
   return SetProperty(MM::g_Keyword_Binning, CDeviceUtils::ConvertToString(binF));
}


/**
 * Required by the MM::Camera API
 * Please implement this yourself and do not rely on the base class implementation
 * The Base class implementation is deprecated and will be removed shortly
 */
int CAndorSDK3Camera::StartSequenceAcquisition(double interval) {
   return StartSequenceAcquisition(LONG_MAX, interval, false);            
}

/**                                                                       
* Stop and wait for the Sequence thread finished                                   
*/                                                                        
int CAndorSDK3Camera::StopSequenceAcquisition()                                     
{                                                                         
   if (!thd_->IsStopped()) {
      thd_->Stop();
      if(snapShotController_->isExternal()) {
         // thd_ will still be waiting for an external trigger. Instruct to
         // stop waiting
         keep_trying_ = false;
      }
      else {
         thd_->wait();
      }
   }
                                                                          
   return DEVICE_OK;                                                      
} 

void CAndorSDK3Camera::initialiseDeviceCircularBuffer()
{
   try
   {
      image_buffers_ = new unsigned char*[NO_CIRCLE_BUFFER_FRAMES];
      for (int i=0; i< NO_CIRCLE_BUFFER_FRAMES; i++) {
         image_buffers_[i]= new unsigned char[(unsigned int)imageSizeBytes->Get()];
         bufferControl->Queue(image_buffers_[i], (int)imageSizeBytes->Get());
      }
   }
   catch (exception &e)
   {
      string s("[initialiseDeviceCircularBuffer] Caught Exception [Live] with message: ");
      s += e.what();
      LogMessage(s);
   }
}


/**
* Simple implementation of Sequence Acquisition
* A sequence acquisition should run on its own thread and transport new images
* coming of the camera into the MMCore circular buffer.
*/
int CAndorSDK3Camera::StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow)
{
   int retCode = DEVICE_OK;
   // The camera's default state is in software trigger mode, poised to make an
   // acquisition. Stop acquisition so that properties can be set for the
   // sequence acquisition. Also release the two buffers that were queued to
   // to take acquisition
   snapShotController_->leavePoisedMode();

   if (IsCapturing())
   {
      retCode = DEVICE_CAMERA_BUSY_ACQUIRING;
   }
   else
   {
      retCode = GetCoreCallback()->PrepareForAcq(this);
   }

   if (DEVICE_OK == retCode)
   {
      sequenceStartTime_ = GetCurrentMMTime();
      imageCounter_ = 0;

      initialiseDeviceCircularBuffer();

      if(LONG_MAX != numImages) {
         cycleMode->Set(L"Fixed");
         frameCount->Set(numImages);
      }
      else {
         // When using the Micro-Manager GUI, this code is executed when entering live mode
         cycleMode->Set(L"Continuous");
      }

      // Set the frame rate to that held by the frame rate holder. Check the limits
      double held_fr = 0.0;
      if (frameRate->IsWritable()) 
      {
         held_fr = frameRate_floatHolder->Get();
         if (held_fr > frameRate->Max()) 
         {
            held_fr = frameRate->Max();
            frameRate_floatHolder->Set(held_fr);
         }
         else if (held_fr < frameRate->Min()) 
         {
            held_fr = frameRate->Min();
            frameRate_floatHolder->Set(held_fr);
         }
         frameRate->Set(held_fr);
      }
      
      try
      {
         startAcquisitionCommand->Do();
         if(snapShotController_->isSoftware()) 
         {
            sendSoftwareTrigger->Do();
         }
      }
      catch (exception &e)
      {
         string s("[StartSequenceAcquisition] Caught Exception [Live] with message: ");
         s += e.what();
         LogMessage(s);
         return DEVICE_ERR;
      }
      
      keep_trying_ = true;
      thd_->Start(numImages,interval_ms);
      stopOnOverflow_ = stopOnOverflow;
      
      if(initialized_) 
      {
         aoi_property_->SetReadOnly(true);
      }

   }
   
   return retCode;
}

/*
 * Inserts Image and MetaData into MMCore circular Buffer
 */
int CAndorSDK3Camera::InsertImage()
{
   MM::MMTime timeStamp = this->GetCurrentMMTime();
   char label[MM::MaxStrLength];
   this->GetLabel(label);
 
   // Important:  metadata about the image are generated here:
   Metadata md;
   md.put("Camera", label);
   //md.put(MM::g_Keyword_Metadata_StartTime, CDeviceUtils::ConvertToString(sequenceStartTime_.getMsec()));
   //md.put(MM::g_Keyword_Elapsed_Time_ms, CDeviceUtils::ConvertToString((timeStamp - sequenceStartTime_).getMsec()));
   //md.put(MM::g_Keyword_Metadata_ImageNumber, CDeviceUtils::ConvertToString(imageCounter_));
   //md.put(MM::g_Keyword_Metadata_ROI_X, CDeviceUtils::ConvertToString( (long) roiX_)); 
   //md.put(MM::g_Keyword_Metadata_ROI_Y, CDeviceUtils::ConvertToString( (long) roiY_)); 

   imageCounter_++;

   char buf[MM::MaxStrLength];
   GetProperty(MM::g_Keyword_Binning, buf);
   md.put(MM::g_Keyword_Binning, buf);

   MMThreadGuard g(imgPixelsLock_);


   const unsigned char* pData = img_.GetPixels();
   unsigned int w = GetImageWidth();
   unsigned int h = GetImageHeight();
   unsigned int b = GetImageBytesPerPixel();

   int ret = GetCoreCallback()->InsertImage(this, pData, w, h, b);
   if (!stopOnOverflow_ && ret == DEVICE_BUFFER_OVERFLOW)
   {
      // do not stop on overflow - just reset the buffer
      GetCoreCallback()->ClearImageBuffer(this);
      // don't process this same image again...
      ret = GetCoreCallback()->InsertImage(this, pData, w, h, b);
   } 

   return ret;
}

/*
 * Do actual capturing
 * Called from inside the thread  
 */
int CAndorSDK3Camera::ThreadRun (void)
{
   unsigned char* return_buffer = NULL;
   int buffer_size = 0;

   bool got_image = false;
   //TAndorTime T0, T1;
   //T0.GetCurrentSystemTime();
   while (!got_image && keep_trying_) 
   {
      try 
      {
         got_image = bufferControl->Wait(return_buffer, buffer_size, 100);
      }
      catch (exception &e) 
      {
         //if (snapShotController_->isSoftware()) {
         //   sendSoftwareTrigger->Do();
         //}
         string s("[ThreadRun] Exception caught with Message: ");
         s += e.what();
         LogMessage(s);
      }
      catch (...) 
      {
         LogMessage("[ThreadRun] Unrecognised Exception caught!");
      }

      //T1.GetCurrentSystemTime();
      //if (!snapShotController_->isExternal() &&
      //   (T1.GetTimeMs() - T0.GetTimeMs()) > static_cast<unsigned int>(timeout_)) {
      //   // Assume we timed out because the camera has filled up
      //   return DEVICE_ERR;
      //}
   }
   
   if (snapShotController_->isSoftware()) {
      sendSoftwareTrigger->Do();
   }

   if (got_image)
   {
      bufferControl->Queue(return_buffer, buffer_size);
      UnpackDataWithPadding(return_buffer);
   }
   return InsertImage();
};


bool CAndorSDK3Camera::IsCapturing() {
   return !thd_->IsStopped();
}

/*
 * called from the thread function before exit 
 */
void CAndorSDK3Camera::OnThreadExiting() throw()
{
   if(image_buffers_ != NULL)
   {
      for (int i=0; i< NO_CIRCLE_BUFFER_FRAMES; i++) {
         delete [] image_buffers_[i];
      }
      delete [] image_buffers_;
      image_buffers_ = NULL;
   }

   if(initialized_) {
      aoi_property_->SetReadOnly(false);
   }

   stopAcquisitionCommand->Do();
   bufferControl->Flush();
   snapShotController_->poiseForSnapShot();

   try
   {
      LogMessage(g_Msg_SEQUENCE_ACQUISITION_THREAD_EXITING);
      GetCoreCallback()?GetCoreCallback()->AcqFinished(this,0):DEVICE_OK;
   }

   catch( CMMError& e){
      std::ostringstream oss;
      oss << g_Msg_EXCEPTION_IN_ON_THREAD_EXITING << " " << e.getMsg() << " " << e.getCode();
      LogMessage(oss.str().c_str(), false);
   }
   catch(...)
   {
      LogMessage(g_Msg_EXCEPTION_IN_ON_THREAD_EXITING, false);
   }
}


MySequenceThread::MySequenceThread(CAndorSDK3Camera* pCam)
   :intervalMs_(default_intervalMS)
   ,numImages_(default_numImages)
   ,imageCounter_(0)
   ,stop_(true)
   ,suspend_(false)
   ,camera_(pCam)
   ,startTime_(0)
   ,actualDuration_(0)
   ,lastFrameTime_(0)
{};

MySequenceThread::~MySequenceThread() {};

void MySequenceThread::Stop() {
   MMThreadGuard(this->stopLock_);
   stop_=true;
}

void MySequenceThread::Start(long numImages, double intervalMs)
{
   MMThreadGuard(this->stopLock_);
   MMThreadGuard(this->suspendLock_);
   numImages_=numImages;
   intervalMs_=intervalMs;
   imageCounter_=0;
   stop_ = false;
   suspend_=false;
   activate();
   actualDuration_ = 0;
   startTime_= camera_->GetCurrentMMTime();
   lastFrameTime_ = 0;
}

bool MySequenceThread::IsStopped(){
   MMThreadGuard(this->stopLock_);
   return stop_;
}

void MySequenceThread::Suspend() {
   MMThreadGuard(this->suspendLock_);
   suspend_ = true;
}

bool MySequenceThread::IsSuspended() {
   MMThreadGuard(this->suspendLock_);
   return suspend_;
}

void MySequenceThread::Resume() {
   MMThreadGuard(this->suspendLock_);
   suspend_ = false;
}

int MySequenceThread::svc(void) throw()
{
   int ret=DEVICE_ERR;
   try 
   {
      do
      {
         ret=camera_->ThreadRun();
      } while (DEVICE_OK == ret && !IsStopped() && imageCounter_++ < numImages_-1);
      if (IsStopped())
         camera_->LogMessage("[svc] SeqAcquisition interrupted by the user");

   }
   catch( exception& e)
   {
      camera_->LogMessage(e.what());
   }
   catch( CMMError& e)
   {
      camera_->LogMessage(e.getMsg(), false);
      ret = e.getCode();
   }catch(...)
   {
      camera_->LogMessage(g_Msg_EXCEPTION_IN_THREAD, false);
   }
   stop_=true;
   actualDuration_ = camera_->GetCurrentMMTime() - startTime_;
   camera_->OnThreadExiting();
   return ret;
}

///////////////////////////////////////////////////////////////////////////////
// Private CAndorSDK3Camera methods
///////////////////////////////////////////////////////////////////////////////

void CAndorSDK3Camera::TestResourceLocking(const bool recurse)
{
   MMThreadGuard g(*pDemoResourceLock_);
   if(recurse)
      TestResourceLocking(false);
}