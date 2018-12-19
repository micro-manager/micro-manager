///////////////////////////////////////////////////////////////////////////////
// FILE:          TSI3Cam.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Thorlabs Scientific Imaging camera adapter
//                SDK 3
//                
// AUTHOR:        Nenad Amodaj, 2017
// COPYRIGHT:     Thorlabs
//
// DISCLAIMER:    This file is provided WITHOUT ANY WARRANTY;
//                without even the implied warranty of MERCHANTABILITY or
//                FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//

#ifdef WIN32
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
//#include <fcntl.h>
//#include <io.h>
#pragma warning(disable : 4996) // disable warning for deprecated CRT functions on Windows 
#endif

#include "Tsi3Cam.h"

#ifdef WIN32
#endif

#ifdef __APPLE__
#endif

#ifdef linux
#endif

#include <string>
#include <sstream>
#include <iomanip>

using namespace std;


void camera_connect_callback(char* /* cameraSerialNumber */, enum TL_CAMERA_USB_PORT_TYPE /* usb_bus_speed */, void* /* context */)
{
	// printf("camera %s connected with bus speed = %d!\n", cameraSerialNumber, usb_bus_speed);
}

void camera_disconnect_callback(char* /* cameraSerialNumber */, void* /* context */)
{
	//printf("camera %s disconnected!\n", cameraSerialNumber);
}


Tsi3Cam::Tsi3Cam() :
   initialized(0),
   stopOnOverflow(false),
   triggerPolarity(TL_CAMERA_TRIGGER_POLARITY_ACTIVE_HIGH),
   operationMode(TL_CAMERA_OPERATION_MODE_SOFTWARE_TRIGGERED),
   camHandle(nullptr),
   acquiringSequence(false),
   acquiringFrame(false),
   maxExposureMs(10000)
{
   // set default error messages
   InitializeDefaultErrorMessages();

   // set device specific error messages
   SetErrorText(ERR_TSI_DLL_LOAD_FAILED, "Couldn't find TSI SDK3 dll.\n"
      "  Make sure TSI DLLs are installed.");
   SetErrorText(ERR_TSI_SDK_LOAD_FAILED, "Error loading TSI SDK3.");
   SetErrorText(ERR_TSI_OPEN_FAILED, "Failed opening TSI SDK3.");
   SetErrorText(ERR_TSI_CAMERA_NOT_FOUND, "Couldn't detect any TSI3 cameras");
   SetErrorText(ERR_IMAGE_TIMED_OUT, "Timed out waiting for the image from the camera.");
   SetErrorText(ERR_INVALID_CHANNEL_INDEX, "Invalid channel index");

   // this identifies which camera we want to access
   CreateProperty(MM::g_Keyword_CameraID, "0", MM::Integer, false, 0, true);
}

Tsi3Cam::~Tsi3Cam()
{
   Shutdown();
}

///////////////////////////////////////////////////////////////////////////////
// MMDevice API
//
void Tsi3Cam::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_DeviceTsi3Cam);
}

int Tsi3Cam::Initialize()
{
	LogMessage("Initializing TSI3 camera...");
	
	const int maxSdkStringLength = 1024;

   if (tl_camera_sdk_dll_initialize())
   {
      return ERR_TSI_DLL_LOAD_FAILED;
   }

   if (tl_camera_open_sdk())
   {
      return ERR_TSI_OPEN_FAILED;
   }

   char camera_ids[maxSdkStringLength];

   if (tl_camera_set_camera_connect_callback(camera_connect_callback, nullptr))
   {
      return ERR_INTERNAL_ERROR;
   }

   if (tl_camera_set_camera_disconnect_callback(camera_disconnect_callback, nullptr))
   {
      return ERR_INTERNAL_ERROR;
   }

   if (tl_camera_discover_available_cameras(camera_ids, maxSdkStringLength))
   {
      return ERR_TSI_CAMERA_NOT_FOUND;
   }

   // pull out the first camera in the list
   string s_camera_ids(camera_ids);
   string s_camera_id = s_camera_ids.substr(0, s_camera_ids.find(' '));

   char camera_id[maxSdkStringLength];
   strcpy_s(camera_id, s_camera_id.c_str());

   if (tl_camera_open_camera(camera_id, &camHandle))
   {
      return ERR_CAMERA_OPEN_FAILED;
   }

   // this must be done after connecting to the camera
   tl_camera_disarm(camHandle);

   // TODO: figure out how to handle multiple cameras

   // set callback for collecting frames
   tl_camera_set_frame_available_callback(camHandle, &Tsi3Cam::frame_available_callback, this);

   // set camera name
   int ret = CreateProperty(MM::g_Keyword_CameraName, camera_id, MM::String, true);
   assert(ret == DEVICE_OK);

   // set firmware version
   char firmware_version[maxSdkStringLength];
   if (tl_camera_get_firmware_version(camHandle, firmware_version, maxSdkStringLength))
      return ERR_INTERNAL_ERROR;
   ret = CreateProperty(g_FirmwareVersion, firmware_version, MM::String, true);
   assert(ret == DEVICE_OK);

   // serial number
   char serial_number[maxSdkStringLength];
   if (tl_camera_get_serial_number(camHandle, serial_number, maxSdkStringLength))
      return ERR_INTERNAL_ERROR;
   ret = CreateProperty(g_SerialNumber, serial_number, MM::String, true);
   assert(ret == DEVICE_OK);

   // obtain full frame parameters and reset the frame
   int minWidth, minHeight;
   if (tl_camera_get_image_width_range(camHandle, &minWidth, &fullFrame.xPixels))
      return ERR_INTERNAL_ERROR;
   if (tl_camera_get_image_height_range(camHandle, &minHeight, &fullFrame.yPixels))
      return ERR_INTERNAL_ERROR;
   
   fullFrame.xOrigin = 0;
   fullFrame.yOrigin = 0;
   fullFrame.xBin = 1;
   fullFrame.yBin = 1;
   ResetImageBuffer();
   tl_camera_get_sensor_pixel_size_bytes(camHandle, &fullFrame.pixDepth);
   tl_camera_get_bit_depth(camHandle, &fullFrame.bitDepth);

   long long exp_min = 0, exp_max = 0;
   if (tl_camera_get_exposure_time_range(camHandle, &exp_min, &exp_max))
      return ERR_INTERNAL_ERROR;

   CPropertyAction *pAct = new CPropertyAction (this, &Tsi3Cam::OnExposure);
   ret = CreateProperty(MM::g_Keyword_Exposure, "2.0", MM::Float, false, pAct);
   assert(ret == DEVICE_OK);
   maxExposureMs = exp_max / 1000.0;
   SetPropertyLimits(MM::g_Keyword_Exposure, exp_min / 1000.0, maxExposureMs);

   // binning
   int hbin_min = 0, hbin_max = 0, vbin_min = 0, vbin_max = 0;
   if (tl_camera_get_binx_range(camHandle, &hbin_min, &hbin_max))
      return ERR_INTERNAL_ERROR;

   if (tl_camera_get_biny_range(camHandle, &vbin_min, &vbin_max))
      return ERR_INTERNAL_ERROR;

   int binMax = min(vbin_max, hbin_max);

   pAct = new CPropertyAction (this, &Tsi3Cam::OnBinning);
   ret = CreateProperty(MM::g_Keyword_Binning, "1", MM::Integer, false, pAct);
   assert(ret == DEVICE_OK);

   vector<string> binValues;
   for (int bin=1; bin<=binMax; bin++)
   {
      ostringstream os;
      os << bin;
      binValues.push_back(os.str());
   }
  
   ret = SetAllowedValues(MM::g_Keyword_Binning, binValues);
   assert(ret == DEVICE_OK);

   // create Trigger mode property
   pAct = new CPropertyAction(this, &Tsi3Cam::OnTriggerMode);
   operationMode = TL_CAMERA_OPERATION_MODE_SOFTWARE_TRIGGERED;
   ret = CreateProperty(g_TriggerMode, g_Software, MM::String, false, pAct);
   AddAllowedValue(g_TriggerMode, g_Software); // SOFTWARE
   AddAllowedValue(g_TriggerMode, g_HardwareEdge); // STANDARD
   AddAllowedValue(g_TriggerMode, g_HardwareDuration); // BULB

   // create Trigger polarity
   pAct = new CPropertyAction(this, &Tsi3Cam::OnTriggerPolarity);
   triggerPolarity = TL_CAMERA_TRIGGER_POLARITY_ACTIVE_HIGH;
   ret = CreateProperty(g_TriggerPolarity, g_Positive, MM::String, false, pAct);
   AddAllowedValue(g_TriggerPolarity, g_Positive);
   AddAllowedValue(g_TriggerPolarity, g_Negative);

   // create temperature property
   //pAct = new CPropertyAction(this, &Tsi3Cam::OnTemperature);
   //ret = CreateProperty(g_Temperature, "0", MM::Integer, true, pAct);

   //tl_camera_get_is_eep_supported create EEP On/Off property
	bool eepSupported(false);
	tl_camera_get_is_eep_supported(camHandle, &eepSupported);
	if (eepSupported)
	{
		pAct = new CPropertyAction(this, &Tsi3Cam::OnEEP);
		ret = CreateProperty(g_EEP, g_Off, MM::String, false, pAct);
		AddAllowedValue(g_EEP, g_Off);
		AddAllowedValue(g_EEP, g_On);
	}

   // create HotPixel threshold property
   int thrMin(0), thrMax(0);
   if (tl_camera_get_hot_pixel_correction_threshold_range(camHandle, &thrMin, &thrMax))
      return ERR_HOT_PIXEL_FAILED;

   if (thrMax != 0)
   {
      pAct = new CPropertyAction(this, &Tsi3Cam::OnHotPixThreshold);
      ret = CreateProperty(g_HotPixThreshold, "0", MM::Integer, false, pAct);
      SetPropertyLimits(g_HotPixThreshold, thrMin, thrMax);

      // create HotPixel On/Off property
      pAct = new CPropertyAction(this, &Tsi3Cam::OnHotPixEnable);
      ret = CreateProperty(g_HotPix, g_Off, MM::String, false, pAct);
      AddAllowedValue(g_HotPix, g_Off);
      AddAllowedValue(g_HotPix, g_On);
  }

   ret = ResizeImageBuffer();
   if (ret != DEVICE_OK)
      return ret;

   ret = UpdateStatus();
   if ( ret != DEVICE_OK)
      return ret;

   initialized = true;
   return DEVICE_OK;
}

int Tsi3Cam::Shutdown()
{
   if (!initialized)
      return DEVICE_OK;

   if (IsCapturing())
      StopSequenceAcquisition();

   StopCamera();

   if (tl_camera_close_camera(camHandle))
      LogMessage("TSI Camera SDK3 close failed!");

   if (tl_camera_close_sdk())
      LogMessage("TSI SDK3 close failed!");

	if (tl_camera_sdk_dll_terminate())
		LogMessage("TSI SDK3 dll terminate failed");

   initialized = false;
   return DEVICE_OK;
}

bool Tsi3Cam::Busy()
{
   return false;
}

long Tsi3Cam::GetImageBufferSize() const
{
   return img.Width() * img.Height() * GetImageBytesPerPixel();
}

/**
 * Access single image buffer 
 */
const unsigned char* Tsi3Cam::GetImageBuffer()
{
   void* pixBuf(0);
   pixBuf = const_cast<unsigned char*> (img.GetPixels()); 
   return (unsigned char*) pixBuf;
}

const unsigned char* Tsi3Cam::GetImageBuffer(unsigned /* chNum */)
{
   return GetImageBuffer();
}

const unsigned int* Tsi3Cam::GetImageBufferAsRGB32()
{
   return nullptr;
}
unsigned Tsi3Cam::GetNumberOfComponents() const
{
   return 1;
}

unsigned Tsi3Cam::GetNumberOfChannels() const
{
   // TODO: multichannel
   return 1;
}

int Tsi3Cam::GetChannelName(unsigned channel, char* name)
{
   // TODO: multichannel

   if (channel != 0)
      return ERR_INVALID_CHANNEL_INDEX;
   
   strncpy(name, "Channel-0", MM::MaxStrLength);
   return DEVICE_OK;
}

/**
 * Snaps a single image, blocks at least until exposure is finished 
 */
int Tsi3Cam::SnapImage()
{
   // set callback for collecting frames
   tl_camera_set_frame_available_callback(camHandle, &Tsi3Cam::frame_available_callback, this);
   tl_camera_set_frames_per_trigger_zero_for_unlimited(camHandle, 1);
   tl_camera_arm(camHandle, 2);

   InterlockedExchange(&acquiringFrame, 1);
   InterlockedExchange(&acquiringSequence, 0);
   if (operationMode == TL_CAMERA_OPERATION_MODE_SOFTWARE_TRIGGERED)
   {
      if (tl_camera_issue_software_trigger(camHandle))
         return ERR_TRIGGER_FAILED;
   }

   // grayscale image snap
   MM::MMTime start = GetCurrentMMTime();
   MM::MMTime timeout((long)(maxExposureMs / 1000.0) + 1000L, 0); // we are setting the upper limit on exposure

   // block until done
   while (acquiringFrame)
   {
      if ((GetCurrentMMTime() - start) > timeout)
         break;
      Sleep(1);
   };

   tl_camera_disarm(camHandle);

   // check for timeout
   if (acquiringFrame)
   {
      InterlockedExchange(&acquiringFrame, 0);
      return ERR_IMAGE_TIMED_OUT;
   }

   return DEVICE_OK;
}

unsigned Tsi3Cam::GetBitDepth() const
{
   return (unsigned)fullFrame.bitDepth;
}

int Tsi3Cam::GetBinning() const
{
   int bin(1);
   tl_camera_get_binx(camHandle, &bin); // vbin is the same
   return bin;
}

int Tsi3Cam::SetBinning(int binSize)
{
   ostringstream os;                                                         
   os << binSize;
   return SetProperty(MM::g_Keyword_Binning, os.str().c_str());                                                                                     
}

double Tsi3Cam::GetExposure() const
{
   long long exp(0);
   tl_camera_get_exposure_time(camHandle, &exp);
   return (double)exp / 1000.0; // exposure is expressed always in ms
}

void Tsi3Cam::SetExposure(double dExpMs)
{
   long long exp = (long long)(dExpMs * 1000 + 0.5);
   tl_camera_set_exposure_time(camHandle, exp);
}

int Tsi3Cam::SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize)
{
   // obtain current binning factor
   int bin(1);
   tl_camera_get_binx(camHandle, &bin); // vbin is the same

   // translate roi from screen coordinates to full frame
   int xFull = x*bin;
   int yFull = y*bin;
   int xSizeFull = xSize * bin;
   int ySizeFull = ySize * bin;

   if (tl_camera_set_roi(camHandle, xFull, yFull, xFull + xSizeFull, yFull + ySizeFull))
   {
      ResetImageBuffer();
      return ERR_ROI_BIN_FAILED;
   }
   return ResizeImageBuffer();
}

int Tsi3Cam::GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize)
{
   int bin(1);
   tl_camera_get_binx(camHandle, &bin); // vbin is the same

   int xtl(0), ytl(0), xbr(0), ybr(0); 
   if (tl_camera_get_roi(camHandle, &xtl, &ytl, &xbr, &ybr))
      return ERR_ROI_BIN_FAILED;

   x = xtl / bin;
   y = ytl / bin;
   xSize = (xbr - xtl) / bin;
   ySize = (ybr - ytl) / bin;

   return DEVICE_OK;
}

int Tsi3Cam::ClearROI()
{
   // reset roi to full frame
   if (tl_camera_set_roi(camHandle, 0, 0, fullFrame.xPixels, fullFrame.yPixels))
   {
      ResetImageBuffer();
      return ERR_ROI_BIN_FAILED;
   }
   return ResizeImageBuffer();
}

int Tsi3Cam::PrepareSequenceAcqusition()
{
   if (IsCapturing())
   {
      return DEVICE_CAMERA_BUSY_ACQUIRING;
   }

   int ret = GetCoreCallback()->PrepareForAcq(this);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

int Tsi3Cam::StartSequenceAcquisition(long numImages, double /*interval_ms*/, bool stopOnOvl)
{
   if (IsCapturing())
   {
      return DEVICE_CAMERA_BUSY_ACQUIRING;
   }

   // the camera ignores interval, running at the rate dictated by the exposure
   stopOnOverflow = stopOnOvl;
   InterlockedExchange(&acquiringSequence, 1);
   InterlockedExchange(&acquiringFrame, 0);
   StartCamera(numImages);

   return DEVICE_OK;
}

int Tsi3Cam::StartSequenceAcquisition(double /*interval_ms*/)
{
   if (IsCapturing())
      return DEVICE_CAMERA_BUSY_ACQUIRING;

   // the camera ignores interval, running at the rate dictated by the exposure
   stopOnOverflow = false;
   InterlockedExchange(&acquiringSequence, 1);
   InterlockedExchange(&acquiringFrame, 0);
   StartCamera(0);

   return DEVICE_OK;
}

int Tsi3Cam::StopSequenceAcquisition()
{
   StopCamera();
   return DEVICE_OK;
}

bool Tsi3Cam::IsCapturing()
{
   return acquiringSequence == 1;
}

///////////////////////////////////////////////////////////////////////////////
// Private utility functions

int Tsi3Cam::ResizeImageBuffer()
{
   int w(0), h(0), d(0);
   tl_camera_get_image_width(camHandle, &w);
   tl_camera_get_image_height(camHandle, &h);
   tl_camera_get_sensor_pixel_size_bytes(camHandle, &d);

   img.Resize(w, h, d);
   ostringstream os;
   os << "TSI3 resized to: " << img.Width() << " X " << img.Height() << ", camera: " << w << "X" << h;
   LogMessage(os.str().c_str());

   return DEVICE_OK;
}

int Tsi3Cam::InsertImage()
{
   int retCode = GetCoreCallback()->InsertImage(this,
         img.GetPixels(),
         img.Width(),
         img.Height(),
         img.Depth());

   if (!stopOnOverflow)
   {
      if (retCode == DEVICE_BUFFER_OVERFLOW)
      {
         // do not stop on overflow - just reset the buffer
         GetCoreCallback()->ClearImageBuffer(this);
         retCode = GetCoreCallback()->InsertImage(this,
            img.GetPixels(),
            img.Width(),
            img.Height(),
            img.Depth());
         return DEVICE_OK;
      }
      else
         return retCode;
   }

   return retCode;
}

bool Tsi3Cam::StopCamera()
{
   InterlockedExchange(&acquiringSequence, 0);
   if (tl_camera_disarm(camHandle))
      return false;
   return true;
}

bool Tsi3Cam::StartCamera( int frames )
{
   tl_camera_set_frame_available_callback(camHandle, &Tsi3Cam::frame_available_callback, this);
   tl_camera_set_frames_per_trigger_zero_for_unlimited(camHandle, frames);

   if (tl_camera_get_trigger_polarity(camHandle, &triggerPolarity))
      return false;

	if (tl_camera_get_operation_mode(camHandle, &operationMode))
		return false;

   tl_camera_arm(camHandle, 2);

   if (operationMode == TL_CAMERA_OPERATION_MODE_SOFTWARE_TRIGGERED)
      return tl_camera_issue_software_trigger(camHandle) == 0;

   return true;
}

void Tsi3Cam::frame_available_callback(void* /*sender*/, unsigned short* image_buffer, int frame_count, unsigned char* /*metadata*/, int /*metadata_size_in_bytes*/, void* context)
{
   Tsi3Cam* instance = static_cast<Tsi3Cam*>(context);
	int img_width(0);
	int img_height(0);
	tl_camera_get_image_width(instance->camHandle, &img_width);
	tl_camera_get_image_height(instance->camHandle, &img_height);

   ostringstream os;
   os << "Frame callback: " << img_width << " X " << img_height << ", frame: "
      << frame_count << ", buffer: " << instance->img.Width() << "X" << instance->img.Height();
   instance->LogMessage(os.str().c_str());
   
   if (instance->acquiringFrame)
   {
      // ONLY GRAYSCALE SUPPORTED
      // we are not supporting color or 8-bit pixels

      // reformat image buffer
      instance->img.Resize(img_width, img_height, instance->fullFrame.pixDepth);

      memcpy(instance->img.GetPixelsRW(), image_buffer, instance->fullFrame.pixDepth * img_height * img_width);
      InterlockedExchange(&instance->acquiringFrame, 0);
   }
   else if (instance->acquiringSequence)
   {
      // ONLY GRAYSCALE SUPPORTED
      // we are not supporting color or 8-bit pixels

      // reformat image buffer
      instance->img.Resize(img_width, img_height, instance->fullFrame.pixDepth);
      memcpy(instance->img.GetPixelsRW(), image_buffer, instance->fullFrame.pixDepth * img_height * img_width);
      int ret = instance->InsertImage();
      if (ret != DEVICE_OK)
      {
         ostringstream osErr;
         osErr << "Insert image failed: " << ret;

      }
   }
   else
   {
      instance->LogMessage("Callback was not serviced!");
      return;
   }
}

void Tsi3Cam::ResetImageBuffer()
{
   if (tl_camera_set_binx(camHandle, 1))
   {
      LogMessage("Error setting xbin factor");
   }

   if (tl_camera_set_biny(camHandle, 1))
   {
      LogMessage("Error setting ybin factor");
   }

   if (tl_camera_set_roi(camHandle, 0, 0,
      fullFrame.xPixels,
      fullFrame.yPixels))
   {
      LogMessage("Error setting roi");
   }

   ResizeImageBuffer();

}


