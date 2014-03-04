// Micro-Manager IIDC Device Adapter
//
// AUTHOR:        Mark A. Tsuchida
//
// COPYRIGHT:     University of California, San Francisco, 2014
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

#include "MMIIDCCamera.h"

#include "IIDCError.h"
#include "IIDCFeature.h"
#include "IIDCVideoMode.h"

#include "DeviceBase.h"
#include "ModuleInterface.h"

#ifdef _MSC_VER
#include <stdlib.h> // _byteswap_ushort()
#endif
#ifndef WIN32
#include <unistd.h> // swab()
#endif

#include <boost/bind.hpp>
#include <boost/foreach.hpp>
#include <boost/format.hpp>
#include <boost/lexical_cast.hpp>
#include <boost/make_shared.hpp>

#include <algorithm>
#include <cmath>
#include <cstring>
#include <exception>
#include <set>
#include <sstream>
#include <string>


namespace {

/*
 * Property names and values
 */

const char* const MMIIDC_Device_Camera = "IIDCCamera";

const char* const MMIIDC_Property_PreInitCameraID = "Camera ID (optional)";
const char* const MMIIDC_Property_PreInitCameraID_NextAvailable = "";

const char* const MMIIDC_Property_PreInit1394Mode = "IEEE 1394 mode";
const char* const MMIIDC_Property_PreInit1394Mode_1394A = "1394A";
const char* const MMIIDC_Property_PreInit1394Mode_1394B = "1394B";

const char* const MMIIDC_Property_PreInitIsoSpeed = "Transmission speed";
const char* const MMIIDC_Property_PreInitIsoSpeed_100 = "100 Mbps";
const char* const MMIIDC_Property_PreInitIsoSpeed_200 = "200 Mbps";
const char* const MMIIDC_Property_PreInitIsoSpeed_400 = "400 Mbps";
const char* const MMIIDC_Property_PreInitIsoSpeed_800 = "800 Mbps (requires 1394B)";

const char* const MMIIDC_Property_PreInitShutterUsPerUnit = "Shutter units (us/unit)";
const char* const MMIIDC_Property_PreInitShutterOffsetUs = "Shutter offset (us)";

const char* const MMIIDC_Property_TimeoutMs = "Timeout (ms)";
const char* const MMIIDC_Property_CameraID = "Camera ID";
const char* const MMIIDC_Property_IIDCVersion = "Camera IIDC version";
const char* const MMIIDC_Property_Vendor = "Vendor name";
const char* const MMIIDC_Property_Model = "Model name";
const char* const MMIIDC_Property_VendorID = "Vendor ID";
const char* const MMIIDC_Property_ModelID = "Model ID";
const char* const MMIIDC_Property_Supports1394B = "Camera supports 1394B";
const char* const MMIIDC_Property_SupportsOneShot = "Camera supports one-shot capture";
const char* const MMIIDC_Property_SupportsMultiShot = "Camera supports multi-shot capture";
const char* const MMIIDC_Property_SupportsPowerSwitch = "Camera supports switching power";
const char* const MMIIDC_Property_1394BEnabled = "1394B enabled";
const char* const MMIIDC_Property_IsoSpeed = "Transmission speed (Mbps)";
const char* const MMIIDC_Property_Format7PacketSizeNegativeDelta = "Limit Format_7 packet size";
const char* const MMIIDC_Property_VideoMode = "Video mode";
const char* const MMIIDC_Property_MaxFramerate = "Maximum framerate (fps)";
const char* const MMIIDC_Property_ExposureMs = MM::g_Keyword_Exposure;
const char* const MMIIDC_Property_Binning = MM::g_Keyword_Binning;
const char* const MMIIDC_Property_BrightnessAbsolute = "Brightness (black level) (%)";
const char* const MMIIDC_Property_Brightness = "Brightness (black level)";
const char* const MMIIDC_Property_GainAbsolute = "Gain (dB)";
const char* const MMIIDC_Property_Gain = "Gain (AU)";


/*
 * Error handling
 */

class Error : public std::exception {
   std::string msg_;

public:
   explicit Error(const std::string& msg) : msg_(msg) {}
   virtual ~Error() throw () {}
   virtual const char* what() const throw () { return msg_.c_str(); }
};


const int MMIIDC_Error_AdHoc_Min = 20000;
const int MMIIDC_Error_AdHoc_Max = 30000;


#define CATCH_AND_RETURN_ERROR \
   catch (const std::exception& e) \
   { \
      return AdHocErrorCode(e.what()); \
   }

#define CATCH_AND_LOG_ERROR \
   catch (const std::exception& e) \
   { \
      LogMessage((std::string("Error: ") + e.what()).c_str()); \
   }


/*
 * A hub device for IIDC cameras would be a nice idea, but the hub-peripheral
 * interface is broken (it doesn't allow multiple distinguishable copies of the
 * same peripheral device), so we can't do that. Instead, use a global object
 * internal to the device adapter (not user-visible).
 */
class IIDCHub {
   static boost::shared_ptr<IIDC::Interface> iidc_s;
   static std::set<std::string> activeCameras_s;

   static boost::shared_ptr<IIDC::Interface> GetIIDC()
   {
      if (!iidc_s)
         iidc_s = boost::make_shared<IIDC::Interface>();
      return iidc_s;
   }

public:
   static boost::shared_ptr<IIDC::Camera> GetCameraByID(const std::string& id)
   {
      if (activeCameras_s.count(id))
         throw Error("Camera " + id + " is already in use");
      return GetIIDC()->NewCamera(id);
   }

   static boost::shared_ptr<IIDC::Camera> GetNextAvailableCamera()
   {
      std::vector<std::string> ids = GetIIDC()->GetCameraIDs();
      BOOST_FOREACH(std::string id, ids)
      {
         if (!activeCameras_s.count(id))
            return GetIIDC()->NewCamera(id);
      }
      throw Error("No IIDC camera available");
   }

   static void PutCamera(const std::string& id)
   {
      activeCameras_s.erase(id);
   }
};

boost::shared_ptr<IIDC::Interface> IIDCHub::iidc_s;
std::set<std::string> IIDCHub::activeCameras_s;

/*
 * Endianness
 */
inline bool HostIsLittleEndian()
{
   const uint16_t test = 1;
   return *reinterpret_cast<const uint8_t*>(&test);
}

inline void ByteSwap16(uint16_t* restrict dst, const uint16_t* restrict src, size_t count)
{
#ifdef _MSC_VER
   for (size_t i = 0; i < count; ++i)
      dst[i] = _byteswap_ushort(src[i]);
#elif !defined(WIN32)
   swab(src, dst, 2 * count);
#else
#error Need to implement byte swap for this platform
#endif
}

} // anonymous namespace


/*
 * Module interface
 */

MODULE_API void
InitializeModuleData()
{
   RegisterDevice(MMIIDC_Device_Camera, MM::CameraDevice, "Camera compatible with IIDC (1394 DCAM)");
}


MODULE_API MM::Device*
CreateDevice(const char* name)
{
   if (std::string(name) == MMIIDC_Device_Camera)
      return new MMIIDCCamera();
   return 0;
}


MODULE_API void
DeleteDevice(MM::Device* device)
{
   delete device;
}


/*
 * Camera device implementation
 */

MMIIDCCamera::MMIIDCCamera() :
   cachedExposure_(0.0),
   nextAdHocErrorCode_(MMIIDC_Error_AdHoc_Min)
{
   CreateStringProperty(MMIIDC_Property_PreInitCameraID, MMIIDC_Property_PreInitCameraID_NextAvailable,
         false, 0, true);

   CreateStringProperty(MMIIDC_Property_PreInit1394Mode, MMIIDC_Property_PreInit1394Mode_1394B,
         false, 0, true);
   AddAllowedValue(MMIIDC_Property_PreInit1394Mode, MMIIDC_Property_PreInit1394Mode_1394A);
   AddAllowedValue(MMIIDC_Property_PreInit1394Mode, MMIIDC_Property_PreInit1394Mode_1394B);

   CreateStringProperty(MMIIDC_Property_PreInitIsoSpeed, MMIIDC_Property_PreInitIsoSpeed_800,
         false, 0, true);
   AddAllowedValue(MMIIDC_Property_PreInitIsoSpeed, MMIIDC_Property_PreInitIsoSpeed_100, 100);
   AddAllowedValue(MMIIDC_Property_PreInitIsoSpeed, MMIIDC_Property_PreInitIsoSpeed_200, 200);
   AddAllowedValue(MMIIDC_Property_PreInitIsoSpeed, MMIIDC_Property_PreInitIsoSpeed_400, 400);
   AddAllowedValue(MMIIDC_Property_PreInitIsoSpeed, MMIIDC_Property_PreInitIsoSpeed_800, 800);

   CreateFloatProperty(MMIIDC_Property_PreInitShutterUsPerUnit, 10.0, false, 0, true);
   CreateFloatProperty(MMIIDC_Property_PreInitShutterOffsetUs, 0.0, false, 0, true);
}


MMIIDCCamera::~MMIIDCCamera()
{
}


int
MMIIDCCamera::Initialize()
{
   char buf[MM::MaxStrLength];
   int err;
   err = GetProperty(MMIIDC_Property_PreInitCameraID, buf);
   if (err != DEVICE_OK)
      return err;
   std::string cameraID(buf);

   err = GetProperty(MMIIDC_Property_PreInit1394Mode, buf);
   if (err != DEVICE_OK)
      return err;
   std::string opMode(buf);

   long isoSpeedLong;
   err = GetCurrentPropertyData(MMIIDC_Property_PreInitIsoSpeed, isoSpeedLong);
   if (err != DEVICE_OK)
      return err;
   unsigned isoSpeed(isoSpeedLong);

   err = GetProperty(MMIIDC_Property_PreInitShutterUsPerUnit, shutterUsPerUnit_);
   if (err != DEVICE_OK)
      return err;

   err = GetProperty(MMIIDC_Property_PreInitShutterOffsetUs, shutterOffsetUs_);
   if (err != DEVICE_OK)
      return err;

   try
   {
      if (cameraID == MMIIDC_Property_PreInitCameraID_NextAvailable || cameraID.empty())
         iidcCamera_ = IIDCHub::GetNextAvailableCamera();
      else
         iidcCamera_ = IIDCHub::GetCameraByID(cameraID);

      iidcCamera_->Enable1394B(opMode == MMIIDC_Property_PreInit1394Mode_1394B);
      iidcCamera_->SetIsoSpeed(isoSpeed);

      /*
       * Create the post-init properties
       */

      err = InitializeInformationalProperties();
      if (err != DEVICE_OK)
         return err;

      err = InitializeBehaviorTweakProperties();
      if (err != DEVICE_OK)
         return err;

      err = InitializeVideoMode();
      if (err != DEVICE_OK)
         return err;

      err = InitializeFramerateAndExposure(); // Depends on video mode
      if (err != DEVICE_OK)
         return err;

      err = InitializeFeatureProperties(); // May depend on video mode
      if (err != DEVICE_OK)
         return err;

      err = CreateIntegerProperty(MMIIDC_Property_Binning, 1, false);
      if (err != DEVICE_OK)
         return err;
      AddAllowedValue(MMIIDC_Property_Binning, "1");

      err = CreateIntegerProperty(MMIIDC_Property_TimeoutMs, 10000, false);
      if (err != DEVICE_OK)
         return err;

      // TODO More properties
   }
   CATCH_AND_RETURN_ERROR

   return DEVICE_OK;
}


int
MMIIDCCamera::Shutdown()
{
   try
   {
      std::string cameraID;
      if (iidcCamera_)
         cameraID = iidcCamera_->GetCameraID();

      videoModes_.clear();
      iidcCamera_.reset();

      if (!cameraID.empty())
         IIDCHub::PutCamera(cameraID);
   }
   CATCH_AND_RETURN_ERROR

   return DEVICE_OK;
}


bool
MMIIDCCamera::Busy()
{
   return false;
}


void
MMIIDCCamera::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, MMIIDC_Device_Camera);
}


int
MMIIDCCamera::SnapImage()
{
   long timeoutMs;
   int err;
   err = GetProperty(MMIIDC_Property_TimeoutMs, timeoutMs);
   if (err != DEVICE_OK)
      return err;

   try
   {
      if (iidcCamera_->IsOneShotCapable())
         iidcCamera_->StartOneShotCapture(3, timeoutMs,
               boost::bind<void>(&MMIIDCCamera::SnapCallback, this, _1, _2, _3, _4));
      else
         iidcCamera_->StartContinuousCapture(3, 1, timeoutMs,
               boost::bind<void>(&MMIIDCCamera::SnapCallback, this, _1, _2, _3, _4));
      iidcCamera_->WaitForCapture();
   }
   CATCH_AND_RETURN_ERROR
   return DEVICE_OK;
}


const unsigned char*
MMIIDCCamera::GetImageBuffer(unsigned /* chan */)
{
   return snappedPixels_.get();
}


long
MMIIDCCamera::GetImageBufferSize() const
{
   if (!snappedPixels_)
      return 0;
   return static_cast<long>(snappedWidth_ * snappedHeight_ * snappedBytesPerPixel_);
}


unsigned
MMIIDCCamera::GetImageWidth() const
{
   try
   {
      // TODO Update when supporting ROI
      return static_cast<unsigned>(currentVideoMode_->GetMaxWidth());
   }
   CATCH_AND_LOG_ERROR
   return 0;
}


unsigned
MMIIDCCamera::GetImageHeight() const
{
   try
   {
      // TODO Update when supporting ROI
      return static_cast<unsigned>(currentVideoMode_->GetMaxHeight());
   }
   CATCH_AND_LOG_ERROR
   return 0;
}


unsigned
MMIIDCCamera::GetImageBytesPerPixel() const
{
   try
   {
      switch (currentVideoMode_->GetPixelFormat())
      {
         case IIDC::PixelFormatGray8:
            return 1;
         case IIDC::PixelFormatGray16:
            return 2;
         default:
            return 0; // Unsupported format
      }
   }
   CATCH_AND_LOG_ERROR
   return 0;
}


unsigned
MMIIDCCamera::GetBitDepth() const
{
   try
   {
      switch (currentVideoMode_->GetPixelFormat())
      {
         case IIDC::PixelFormatGray8:
            return 8;
         case IIDC::PixelFormatGray16:
            return 16; // TODO Return correct value
         default:
            return 0; // Unsupported format
      }
   }
   CATCH_AND_LOG_ERROR
   return 0;
}


void
MMIIDCCamera::SetExposure(double milliseconds)
{
   try
   {
      SetExposureImpl(milliseconds);
      if (HasProperty(MMIIDC_Property_ExposureMs))
      {
         std::string newExposureStr = boost::lexical_cast<std::string>(cachedExposure_);
         SetProperty(MMIIDC_Property_ExposureMs, newExposureStr.c_str());
         OnPropertyChanged(MMIIDC_Property_ExposureMs, newExposureStr.c_str());
      }
   }
   CATCH_AND_LOG_ERROR;
}


int
MMIIDCCamera::SetROI(unsigned, unsigned, unsigned, unsigned)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}


int
MMIIDCCamera::GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize)
{
   // ROI not (yet) implemented, so return bounds of maximum ROI
   x = y = 0;
   xSize = currentVideoMode_->GetMaxWidth();
   ySize = currentVideoMode_->GetMaxHeight();
   return DEVICE_OK;
}


int
MMIIDCCamera::ClearROI()
{
   // ROI not (yet) implemented, so this is a no-op.
   return DEVICE_OK;
}


int
MMIIDCCamera::StartSequenceAcquisition(long count, double /*intervalMs*/, bool stopOnOverflow)
{
   if (count < 0)
      return AdHocErrorCode("Cannot run sequence acquisition with negative count");

   long timeoutMs;
   int err;
   err = GetProperty(MMIIDC_Property_TimeoutMs, timeoutMs);
   if (err != DEVICE_OK)
      return err;

   stopOnOverflow_ = stopOnOverflow;

   try
   {
      if (iidcCamera_->IsMultiShotCapable() && count < 65536)
      {
         iidcCamera_->StartMultiShotCapture(16, static_cast<uint16_t>(count), timeoutMs,
               boost::bind<void>(&MMIIDCCamera::SequenceCallback, this, _1, _2, _3, _4));
      }
      else
      {
         size_t nrFrames = (count == LONG_MAX) ? static_cast<size_t>(count) : 0;
         iidcCamera_->StartContinuousCapture(16, nrFrames, timeoutMs,
               boost::bind<void>(&MMIIDCCamera::SequenceCallback, this, _1, _2, _3, _4));
      }
   }
   CATCH_AND_RETURN_ERROR

   return DEVICE_OK;
}


int
MMIIDCCamera::StopSequenceAcquisition()
{
   try
   {
      iidcCamera_->StopCapture();
      iidcCamera_->WaitForCapture();
   }
   CATCH_AND_RETURN_ERROR

   return DEVICE_OK;
}


bool
MMIIDCCamera::IsCapturing()
{
   try
   {
      return iidcCamera_->IsCapturing();
   }
   CATCH_AND_LOG_ERROR
   return false;
}


int
MMIIDCCamera::OnMaximumFramerate(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(cachedFramerate_);
   }
   return DEVICE_OK;
}


int
MMIIDCCamera::OnFormat7PacketSizeNegativeDelta(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   try
   {
      if (eAct == MM::AfterSet)
         VideoModeDidChange();
   }
   CATCH_AND_RETURN_ERROR
   return DEVICE_OK;
}


int
MMIIDCCamera::OnVideoMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   try
   {
      if (eAct == MM::BeforeGet)
      {
         pProp->Set(currentVideoMode_->ToString().c_str());
      }
      else if (eAct == MM::AfterSet)
      {
         std::string value;
         pProp->Get(value);
         long index;
         GetPropertyData(MMIIDC_Property_VideoMode, value.c_str(), index);
         if (*videoModes_[index] != *currentVideoMode_)
         {
            iidcCamera_->SetVideoMode(videoModes_[index]);
            currentVideoMode_ = iidcCamera_->GetVideoMode();
            VideoModeDidChange();
         }
      }
   }
   CATCH_AND_RETURN_ERROR
   return DEVICE_OK;
}


int
MMIIDCCamera::OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   try
   {
      if (eAct == MM::BeforeGet)
      {
         pProp->Set(cachedExposure_);
      }
      else if (eAct == MM::AfterSet)
      {
         double value;
         pProp->Get(value);
         SetExposureImpl(value);
         int err;
         err = OnExposureChanged(cachedExposure_);
         if (err != DEVICE_OK)
            return err;
      }
   }
   CATCH_AND_RETURN_ERROR
   return DEVICE_OK;
}


int
MMIIDCCamera::OnBrightness(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   boost::shared_ptr<IIDC::BrightnessFeature> brightness = iidcCamera_->GetBrightnessFeature();
   try
   {
      if (eAct == MM::BeforeGet)
      {
         if (brightness->HasAbsoluteControl())
            pProp->Set(brightness->GetAbsoluteValue());
         else
            pProp->Set(static_cast<long>(brightness->GetValue()));
      }
      else if (eAct == MM::AfterSet)
      {
         if (brightness->HasAbsoluteControl())
         {
            double value;
            pProp->Get(value);
            brightness->SetAbsoluteValue(value);
         }
         else
         {
            long value;
            pProp->Get(value);
            brightness->SetValue(static_cast<uint32_t>(value));
         }
      }
   }
   CATCH_AND_RETURN_ERROR
   return DEVICE_OK;
}


int
MMIIDCCamera::OnGain(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   boost::shared_ptr<IIDC::GainFeature> gain = iidcCamera_->GetGainFeature();
   try
   {
      if (eAct == MM::BeforeGet)
      {
         if (false && gain->HasAbsoluteControl()) // XXX TEMPORARILY DISABLED
            pProp->Set(gain->GetAbsoluteValue());
         else
            pProp->Set(static_cast<long>(gain->GetValue()));
      }
      else if (eAct == MM::AfterSet)
      {
         if (false && gain->HasAbsoluteControl()) // XXX TEMPORARILY DISABLED
         {
            double value;
            pProp->Get(value);
            gain->SetAbsoluteValue(value);
         }
         else
         {
            long value;
            pProp->Get(value);
            gain->SetValue(static_cast<uint32_t>(value));
         }
      }
   }
   CATCH_AND_RETURN_ERROR
   return DEVICE_OK;
}


int
MMIIDCCamera::InitializeInformationalProperties()
{
   int err;

   err = CreateStringProperty(MMIIDC_Property_CameraID, iidcCamera_->GetCameraID().c_str(), true);
   if (err != DEVICE_OK)
      return err;

   err = CreateStringProperty(MMIIDC_Property_IIDCVersion, iidcCamera_->GetIIDCVersion().c_str(), true);
   if (err != DEVICE_OK)
      return err;

   err = CreateStringProperty(MMIIDC_Property_Vendor, iidcCamera_->GetVendor().c_str(), true);
   if (err != DEVICE_OK)
      return err;

   err = CreateStringProperty(MMIIDC_Property_Model, iidcCamera_->GetModel().c_str(), true);
   if (err != DEVICE_OK)
      return err;

   std::ostringstream vendorIDStream;
   vendorIDStream << (boost::format("%06x") % iidcCamera_->GetVendorID()) << "h";
   err = CreateStringProperty(MMIIDC_Property_VendorID, vendorIDStream.str().c_str(), true);
   if (err != DEVICE_OK)
      return err;

   std::ostringstream modelIDStream;
   modelIDStream << (boost::format("%06x") % iidcCamera_->GetModelID()) << "h";
   err = CreateStringProperty(MMIIDC_Property_ModelID, modelIDStream.str().c_str(), true);
   if (err != DEVICE_OK)
      return err;

   err = CreateStringProperty(MMIIDC_Property_Supports1394B,
         iidcCamera_->Is1394BCapable() ? "Yes" : "No", true);
   if (err != DEVICE_OK)
      return err;

   err = CreateStringProperty(MMIIDC_Property_SupportsOneShot,
         iidcCamera_->IsOneShotCapable() ? "Yes" : "No", true);
   if (err != DEVICE_OK)
      return err;

   err = CreateStringProperty(MMIIDC_Property_SupportsMultiShot,
         iidcCamera_->IsMultiShotCapable() ? "Yes" : "No", true);
   if (err != DEVICE_OK)
      return err;

   err = CreateStringProperty(MMIIDC_Property_SupportsPowerSwitch,
         iidcCamera_->IsPowerSwitchable() ? "Yes" : "No", true);
   if (err != DEVICE_OK)
      return err;

   err = CreateStringProperty(MMIIDC_Property_1394BEnabled,
         iidcCamera_->Is1394BEnabled() ? "Yes" : "No", true);
   if (err != DEVICE_OK)
      return err;

   err = CreateIntegerProperty(MMIIDC_Property_IsoSpeed,
         static_cast<long>(iidcCamera_->GetIsoSpeed()), true);

   return DEVICE_OK;
}


int
MMIIDCCamera::InitializeBehaviorTweakProperties()
{
   int err;

   err = CreateIntegerProperty(MMIIDC_Property_Format7PacketSizeNegativeDelta, 0, false,
         new CPropertyAction(this, &MMIIDCCamera::OnFormat7PacketSizeNegativeDelta));
   if (err != DEVICE_OK)
      return err;
   for (int i = 0; i < 8; ++i)
      AddAllowedValue(MMIIDC_Property_Format7PacketSizeNegativeDelta,
            boost::lexical_cast<std::string>(-i).c_str());

   return DEVICE_OK;
}


int
MMIIDCCamera::InitializeVideoMode()
{
   videoModes_.clear();
   std::vector< boost::shared_ptr<IIDC::VideoMode> > allVideoModes = iidcCamera_->GetVideoModes();
   BOOST_FOREACH(boost::shared_ptr<IIDC::VideoMode> mode, allVideoModes)
   {
      switch (mode->GetPixelFormat())
      {
         case IIDC::PixelFormatGray8:
         case IIDC::PixelFormatGray16:
            videoModes_.push_back(mode);
            LogMessage("Video mode [" + mode->ToString() + "]: supported", true);
            break;
         default:
            LogMessage("Video mode [" + mode->ToString() + "]: not supported; skipping", true);
            break;
      }
   }
   if (videoModes_.empty())
      return AdHocErrorCode("None of the camera's video modes are currently supported");

   bool currentModeSupported = false;
   try
   {
      currentVideoMode_ = iidcCamera_->GetVideoMode();
   }
   catch (const IIDC::Error&)
   {
   }
   if (currentVideoMode_)
   {
      BOOST_FOREACH(boost::shared_ptr<IIDC::VideoMode> mode, videoModes_)
      {
         if (*mode == *currentVideoMode_)
         {
            currentModeSupported = true;
            break;
         }
      }
   }
   if (!currentModeSupported)
   {
      iidcCamera_->SetVideoMode(videoModes_.front());
      currentVideoMode_ = iidcCamera_->GetVideoMode();
   }

   int err;
   err = CreateStringProperty(MMIIDC_Property_VideoMode, currentVideoMode_->ToString().c_str(), false,
         new CPropertyAction(this, &MMIIDCCamera::OnVideoMode));
   if (err != DEVICE_OK)
      return err;

   long index = 0;
   BOOST_FOREACH(boost::shared_ptr<IIDC::VideoMode> mode, videoModes_)
   {
      AddAllowedValue(MMIIDC_Property_VideoMode, mode->ToString().c_str(), index);
      ++index;
   }

   return DEVICE_OK;
}


int
MMIIDCCamera::InitializeFramerateAndExposure()
{
   boost::shared_ptr<IIDC::FrameRateFeature> prioritizedFramerate = iidcCamera_->GetFrameRateFeature();
   if (prioritizedFramerate->IsPresent() && prioritizedFramerate->IsSwitchable())
      prioritizedFramerate->SetOnOff(false);

   iidcCamera_->SetMaxFramerate();
   cachedFramerate_ = iidcCamera_->GetFramerate();
   LogMessage("IIDC Framerate now set to " +
         boost::lexical_cast<std::string>(cachedFramerate_) + " (fps)");

   int err;
   err = CreateFloatProperty(MMIIDC_Property_MaxFramerate, cachedFramerate_, true,
         new CPropertyAction(this, &MMIIDCCamera::OnMaximumFramerate));
   if (err != DEVICE_OK)
      return err;

   boost::shared_ptr<IIDC::ShutterFeature> shutter = iidcCamera_->GetShutterFeature();
   if (!shutter->IsPresent() || !shutter->HasManualMode())
   {
      LogMessage("Warning: Camera does not allow control of exposure (integration time)");
      cachedExposure_ = 0.0;
   }
   else
   {
      shutter->SetAutoMode(false);

      if (shutter->HasAbsoluteControl())
      {
         LogMessage("Camera allows shutter control in absolute units; enabling");
         shutter->SetAbsoluteControl(true);
      }
      else
      {
         LogMessage("Camera does not allow shutter control in absolute units; "
               "using user-provided scaling factors");
      }

      cachedExposure_ = GetExposureUncached();
   }

   err = CreateFloatProperty(MMIIDC_Property_ExposureMs, cachedExposure_, false,
         new CPropertyAction(this, &MMIIDCCamera::OnExposure));
   if (err != DEVICE_OK)
      return err;
   std::pair<double, double> limits = GetExposureLimits();
   err = SetPropertyLimits(MMIIDC_Property_ExposureMs, limits.first, limits.second);
   if (err != DEVICE_OK)
      return err;

   return DEVICE_OK;
}


int
MMIIDCCamera::InitializeFeatureProperties()
{
   boost::shared_ptr<IIDC::BrightnessFeature> brightness = iidcCamera_->GetBrightnessFeature();
   if (brightness->IsPresent() && brightness->HasManualMode())
   {
      if (brightness->IsSwitchable())
         brightness->SetOnOff(true);
      brightness->SetAutoMode(false);

      if (brightness->HasAbsoluteControl())
      {
         brightness->SetAbsoluteControl(true);
         int err;
         err = CreateFloatProperty(MMIIDC_Property_BrightnessAbsolute, brightness->GetAbsoluteValue(),
               false, new CPropertyAction(this, &MMIIDCCamera::OnBrightness));
         if (err != DEVICE_OK)
            return err;
         std::pair<float, float> limits = brightness->GetAbsoluteMinMax();
         err = SetPropertyLimits(MMIIDC_Property_BrightnessAbsolute, limits.first, limits.second);
         if (err != DEVICE_OK)
            return err;
      }
      else
      {
         int err;
         err = CreateIntegerProperty(MMIIDC_Property_Brightness, brightness->GetValue(), false,
               new CPropertyAction(this, &MMIIDCCamera::OnBrightness));
         if (err != DEVICE_OK)
            return err;
         std::pair<uint32_t, uint32_t> limits = brightness->GetMinMax();
         err = SetPropertyLimits(MMIIDC_Property_Brightness, limits.first, limits.second);
         if (err != DEVICE_OK)
            return err;
      }
   }

   boost::shared_ptr<IIDC::GainFeature> gain = iidcCamera_->GetGainFeature();
   if (gain->IsPresent() && gain->HasManualMode())
   {
      if (gain->IsSwitchable())
         gain->SetOnOff(true);
      gain->SetAutoMode(false);

      if (false && gain->HasAbsoluteControl()) // XXX TEMPORARILY DISABLED
      {
         gain->SetAbsoluteControl(true);
         int err;
         err = CreateFloatProperty(MMIIDC_Property_GainAbsolute, gain->GetAbsoluteValue(),
               false, new CPropertyAction(this, &MMIIDCCamera::OnGain));
         if (err != DEVICE_OK)
            return err;
         std::pair<float, float> limits = gain->GetAbsoluteMinMax();
         err = SetPropertyLimits(MMIIDC_Property_GainAbsolute, limits.first, limits.second);
         if (err != DEVICE_OK)
            return err;
      }
      else
      {
         int err;
         err = CreateIntegerProperty(MMIIDC_Property_Gain, gain->GetValue(), false,
               new CPropertyAction(this, &MMIIDCCamera::OnGain));
         if (err != DEVICE_OK)
            return err;
         std::pair<uint32_t, uint32_t> limits = gain->GetMinMax();
         err = SetPropertyLimits(MMIIDC_Property_Gain, limits.first, limits.second);
         if (err != DEVICE_OK)
            return err;
      }
   }

   return DEVICE_OK;
}


int
MMIIDCCamera::VideoModeDidChange()
{
   int err;
   long format7PacketSizeDelta;
   err = GetProperty(MMIIDC_Property_Format7PacketSizeNegativeDelta, format7PacketSizeDelta);
   if (err != DEVICE_OK)
      return err;

   iidcCamera_->SetMaxFramerate(static_cast<unsigned>(-format7PacketSizeDelta));
   cachedFramerate_ = iidcCamera_->GetFramerate();
   LogMessage("IIDC Framerate now set to " +
         boost::lexical_cast<std::string>(cachedFramerate_) + " (fps)");
   err = OnPropertyChanged(MMIIDC_Property_MaxFramerate,
         boost::lexical_cast<std::string>(cachedFramerate_).c_str());
   if (err != DEVICE_OK)
      return err;

   boost::shared_ptr<IIDC::ShutterFeature> shutter = iidcCamera_->GetShutterFeature();
   if (shutter->IsPresent() && shutter->HasManualMode())
   {
      std::pair<double, double> limits = GetExposureLimits();
      double newExposure = std::max(std::min(cachedExposure_, limits.second), limits.first);
      if (newExposure != cachedExposure_)
         SetExposureImpl(newExposure);
      err = OnExposureChanged(cachedExposure_);
      if (err != DEVICE_OK)
         return err;

      err = SetPropertyLimits(MMIIDC_Property_ExposureMs, limits.first, limits.second);
      if (err != DEVICE_OK)
         return err;

      std::string newExposureStr = boost::lexical_cast<std::string>(cachedExposure_);
      err = SetProperty(MMIIDC_Property_ExposureMs, newExposureStr.c_str());
      if (err != DEVICE_OK)
         return err;
      err = OnPropertyChanged(MMIIDC_Property_ExposureMs, newExposureStr.c_str());
      if (err != DEVICE_OK)
         return err;
   }

   if (HasProperty(MMIIDC_Property_BrightnessAbsolute))
   {
      boost::shared_ptr<IIDC::BrightnessFeature> brightness = iidcCamera_->GetBrightnessFeature();
      std::pair<float, float> limits = brightness->GetAbsoluteMinMax();
      err = SetPropertyLimits(MMIIDC_Property_BrightnessAbsolute, limits.first, limits.second);
      if (err != DEVICE_OK)
         return err;
      err = OnPropertyChanged(MMIIDC_Property_BrightnessAbsolute,
            boost::lexical_cast<std::string>(brightness->GetAbsoluteValue()).c_str());
      if (err != DEVICE_OK)
         return err;
   }
   else if (HasProperty(MMIIDC_Property_Brightness))
   {
      boost::shared_ptr<IIDC::BrightnessFeature> brightness = iidcCamera_->GetBrightnessFeature();
      std::pair<uint32_t, uint32_t> limits = brightness->GetMinMax();
      err = SetPropertyLimits(MMIIDC_Property_Brightness, limits.first, limits.second);
      if (err != DEVICE_OK)
         return err;
      err = OnPropertyChanged(MMIIDC_Property_Brightness,
            boost::lexical_cast<std::string>(brightness->GetValue()).c_str());
      if (err != DEVICE_OK)
         return err;
   }

   if (HasProperty(MMIIDC_Property_GainAbsolute))
   {
      boost::shared_ptr<IIDC::GainFeature> gain = iidcCamera_->GetGainFeature();
      std::pair<float, float> limits = gain->GetAbsoluteMinMax();
      err = SetPropertyLimits(MMIIDC_Property_GainAbsolute, limits.first, limits.second);
      if (err != DEVICE_OK)
         return err;
      err = OnPropertyChanged(MMIIDC_Property_GainAbsolute,
            boost::lexical_cast<std::string>(gain->GetAbsoluteValue()).c_str());
      if (err != DEVICE_OK)
         return err;
   }
   else if (HasProperty(MMIIDC_Property_Gain))
   {
      boost::shared_ptr<IIDC::GainFeature> gain = iidcCamera_->GetGainFeature();
      std::pair<uint32_t, uint32_t> limits = gain->GetMinMax();
      err = SetPropertyLimits(MMIIDC_Property_Gain, limits.first, limits.second);
      if (err != DEVICE_OK)
         return err;
      err = OnPropertyChanged(MMIIDC_Property_Gain,
            boost::lexical_cast<std::string>(gain->GetValue()).c_str());
      if (err != DEVICE_OK)
         return err;
   }

   return DEVICE_OK;
}


void
MMIIDCCamera::SetExposureImpl(double milliseconds)
{
   boost::shared_ptr<IIDC::ShutterFeature> shutter = iidcCamera_->GetShutterFeature();
   if (!shutter->IsPresent() || !shutter->HasManualMode())
   {
      LogMessage("Cannot set exposure (not supported by camera)");
      return;
   }
   if (shutter->GetAbsoluteControl())
   {
      std::pair<float, float> limits = shutter->GetAbsoluteMinMax();
      float actualSeconds = milliseconds / 1000.0f;
      actualSeconds = std::max(actualSeconds, limits.first);
      actualSeconds = std::min(actualSeconds, limits.second);
      if (actualSeconds != milliseconds)
      {
         LogMessage("Requested exposure (" + boost::lexical_cast<std::string>(milliseconds) +
               " ms) out of range (" +
               boost::lexical_cast<std::string>(limits.first) + " - " +
               boost::lexical_cast<std::string>(limits.second) + " s); adjusted");
      }
      shutter->SetAbsoluteValue(actualSeconds);
   }
   else
   {
      double setValue = std::max(0.0,
            (milliseconds * 1000.0 - shutterOffsetUs_) / shutterUsPerUnit_);
      uint32_t intSetValue = static_cast<uint32_t>(std::floor(setValue + 0.5));

      std::pair<uint32_t, uint32_t> limits = shutter->GetMinMax();
      uint32_t actualSetValue = intSetValue;
      actualSetValue = std::max(actualSetValue, limits.first);
      actualSetValue = std::min(actualSetValue, limits.second);
      if (actualSetValue != intSetValue)
      {
         LogMessage("Requested exposure (" + boost::lexical_cast<std::string>(milliseconds) +
               " ms @ " + boost::lexical_cast<std::string>(shutterUsPerUnit_) + " us/unit, offset " +
               boost::lexical_cast<std::string>(shutterOffsetUs_) + " us = " +
               boost::lexical_cast<std::string>(intSetValue) + " units) out of range (" +
               boost::lexical_cast<std::string>(limits.first) + " - " +
               boost::lexical_cast<std::string>(limits.second) + " units); adjusted");
      }
      shutter->SetValue(actualSetValue);
   }

   cachedExposure_ = GetExposureUncached();
}


double
MMIIDCCamera::GetExposureUncached()
{
   boost::shared_ptr<IIDC::ShutterFeature> shutter = iidcCamera_->GetShutterFeature();
   if (!shutter->IsPresent() || !shutter->IsReadable())
   {
      LogMessage("Cannot get exposure (not supported by camera)");
      return 0.0;
   }
   if (shutter->GetAbsoluteControl())
   {
      float seconds = shutter->GetAbsoluteValue();
      return seconds * 1000.0;
   }
   else
   {
      uint32_t units = shutter->GetValue();
      double milliseconds = (units * shutterUsPerUnit_ + shutterOffsetUs_) / 1000.0;
      return milliseconds;
   }
}


std::pair<double, double>
MMIIDCCamera::GetExposureLimits()
{
   boost::shared_ptr<IIDC::ShutterFeature> shutter = iidcCamera_->GetShutterFeature();
   if (!shutter->IsPresent() || !shutter->HasManualMode())
   {
      return std::make_pair(0.0, 0.0);
   }
   if (shutter->GetAbsoluteControl())
   {
      std::pair<float, float> limits = shutter->GetAbsoluteMinMax();
      return std::make_pair(limits.first * 1000.0, limits.second * 1000.0);
   }
   else
   {
      std::pair<uint32_t, uint32_t> limits = shutter->GetMinMax();
      double lower = (limits.first * shutterUsPerUnit_ + shutterOffsetUs_) / 1000.0;
      double upper = (limits.second * shutterUsPerUnit_ + shutterOffsetUs_) / 1000.0;
      return std::make_pair(lower, upper);
   }
}


void
MMIIDCCamera::SnapCallback(const void* pixels, size_t width, size_t height, IIDC::PixelFormat format)
{
   size_t bytesPerPixel;
   switch (format)
   {
      case IIDC::PixelFormatGray8:
         bytesPerPixel = 1;
         break;

      case IIDC::PixelFormatGray16:
         bytesPerPixel = 2;
         break;

      default:
         throw Error("Unsupported pixel format");
   }

   size_t bufferSize = width * height * bytesPerPixel;
   snappedPixels_.reset(new unsigned char[bufferSize]);

   if (HostIsLittleEndian() && bytesPerPixel == 2)
      ByteSwap16(reinterpret_cast<uint16_t*>(snappedPixels_.get()),
            reinterpret_cast<const uint16_t*>(pixels), width * height);
   else
      std::memcpy(snappedPixels_.get(), pixels, bufferSize);

   snappedWidth_ = width;
   snappedHeight_ = height;
   snappedBytesPerPixel_ = bytesPerPixel;
   snappedPixelFormat_ = format;
}


void
MMIIDCCamera::SequenceCallback(const void* pixels, size_t width, size_t height, IIDC::PixelFormat format)
{
   size_t bytesPerPixel;
   switch (format)
   {
      case IIDC::PixelFormatGray8:
         bytesPerPixel = 1;
         break;

      case IIDC::PixelFormatGray16:
         bytesPerPixel = 2;
         break;

      default:
         throw Error("Unsupported pixel format");
   }

   boost::scoped_array<uint16_t> swapped;
   if (HostIsLittleEndian() && bytesPerPixel == 2)
   {
      swapped.reset(new uint16_t[width * height]);
      ByteSwap16(swapped.get(), reinterpret_cast<const uint16_t*>(pixels), width * height);
      pixels = swapped.get();
   }

   /*
    * Okay, ugliness begins. Close your eyes for the next 20 lines.
    */

   char label[MM::MaxStrLength];
   GetLabel(label);
   Metadata md;
   md.put("Camera", label);
   std::string serializedMD(md.Serialize());

   const unsigned char* bytes = reinterpret_cast<const unsigned char*>(pixels);

   int err;
   err = GetCoreCallback()->InsertImage(this, bytes, width, height, bytesPerPixel,
         serializedMD.c_str());
   if (!stopOnOverflow_ && err == DEVICE_BUFFER_OVERFLOW)
   {
      GetCoreCallback()->ClearImageBuffer(this);
      err = GetCoreCallback()->InsertImage(this, bytes, width, height, bytesPerPixel,
            serializedMD.c_str(), false);
   }
   if (err != DEVICE_OK)
      throw Error("Unknown error (" + boost::lexical_cast<std::string>(err) +
            " while placing image in circular buffer");
}


int
MMIIDCCamera::AdHocErrorCode(const std::string& message)
{
   int code = nextAdHocErrorCode_++;
   if (code > MMIIDC_Error_AdHoc_Max)
      code = MMIIDC_Error_AdHoc_Min;
   SetErrorText(code, message.c_str());
   return code;
}
