// Micro-Manager IIDC Device Adapter
//
// AUTHOR:        Mark A. Tsuchida
//
// COPYRIGHT:     2014-2015, Regents of the University of California
//                2016, Open Imaging, Inc.
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

#ifdef _MSC_VER
// Prevent windows.h from defining the min and max macros, so that std::min
// and std::max work
#define NOMINMAX
#endif

#include "MMIIDCCamera.h"

#include "IIDCConvert.h"
#include "IIDCError.h"
#include "IIDCFeature.h"
#include "IIDCVideoMode.h"
#include "IIDCVendorAVT.h"

#include "DeviceBase.h"
#include "ModuleInterface.h"

#ifdef _MSC_VER
#include <stdlib.h> // _byteswap_ushort()
#endif
#ifndef _WIN32
#include <unistd.h> // swab()
#endif

#include <boost/bind.hpp>
#include <boost/foreach.hpp>
#include <boost/format.hpp>
#include <boost/lexical_cast.hpp>
#include <boost/make_shared.hpp>
#include <boost/thread.hpp>
#include <boost/weak_ptr.hpp>

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

const char* const MMIIDC_Property_PreInitGainUnits = "Gain units";
const char* const MMIIDC_Property_PreInitGainUnits_Auto = "Auto-detect";
const char* const MMIIDC_Property_PreInitGainUnits_ArbitraryWithAbsoluteReadOut = "AU with dB readout";

const char* const MMIIDC_Property_TimeoutMs = "Timeout (ms)";
const char* const MMIIDC_Property_AlwaysPoll = "Always use polling";
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
const char* const MMIIDC_Property_SupportsAbsoluteShutter = "Camera supports integration time in physical units";
const char* const MMIIDC_Property_1394BEnabled = "1394B enabled";
const char* const MMIIDC_Property_IsoSpeed = "Transmission speed (Mbps)";
const char* const MMIIDC_Property_RightShift16BitSamples = "Right-shift 16-bit samples";
const char* const MMIIDC_Property_Format7PacketSizeNegativeDelta = "Limit Format_7 packet size";
const char* const MMIIDC_Property_VideoMode = "Video mode";
const char* const MMIIDC_Property_Format7PacketSize = "Format_7 packet size";
const char* const MMIIDC_Property_Format7PacketSizeMode = "Format_7 packet size mode";
const char* const MMIIDC_Property_Format7PacketSizeMode_UseMax =
   "Set to max when parameters change";
const char* const MMIIDC_Property_Format7PacketSizeMode_KeepSetting =
   "Keep last-set value when possible";
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
 * Endianness, etc.
 */
inline bool HostIsLittleEndian()
{
   const uint16_t test = 1;
   // Use unsigned char*, not uint8_t*, to prevent undefined behavior due to
   // the strict aliasing rule.
   return *reinterpret_cast<const unsigned char*>(&test) != 0;
}

#ifdef _MSC_VER
#define mmiidc_restrict __restrict
#else
#define mmiidc_restrict restrict
#endif

inline void ApplySoftROI(void* mmiidc_restrict dst,
      const void* mmiidc_restrict src, size_t bytesPerPixel,
      size_t srcWidth, size_t /* srcHeight */,
      size_t left, size_t top, size_t width, size_t height)
{
   size_t srcStart = top * srcWidth + left;

   size_t byteSrcStart = srcStart * bytesPerPixel;
   size_t byteSrcWidth = srcWidth * bytesPerPixel;
   size_t byteWidth = width * bytesPerPixel;

   uint8_t* destRow = static_cast<uint8_t*>(dst);
   const uint8_t* srcRow = static_cast<const uint8_t*>(src) + byteSrcStart;
   for (size_t r = 0; r < height; ++r)
   {
      memcpy(destRow, srcRow, byteWidth);
      destRow += byteWidth;
      srcRow += byteSrcWidth;
   }
}

inline void ByteSwap16OutOfPlace(uint16_t* mmiidc_restrict dst,
   const uint16_t* mmiidc_restrict src, size_t count)
{
   // TODO Rewrite using Boost.Endian once we upgrade Boost requirements
#ifdef _MSC_VER
   for (size_t i = 0; i < count; ++i)
      dst[i] = _byteswap_ushort(src[i]);
#elif defined(__APPLE__) || defined(_XOPEN_SOURCE)
   swab(src, dst, 2 * count);
#else
   for (size_t i = 0; i < count; ++i)
   {
      dst[i] = (src[i] << 8) | (src[i] >> 8);
#if defined(__GNUC__)
      // __builtin_bswap16() was missing prior to GCC 4.8
#   if ((__GNUC__ * 10000 \
      + __GNUC_MINOR__ * 100 \
      + __GNUC_PATCHLEVEL__) \
      > 40800)
      dst[i] = __builtin_bswap16(src[i]);
#   elif (defined(__clang__) && __has_builtin(__builtin_bswap16))
      dst[i] = __builtin_bswap16(src[i]);
#   else
      dst[i] = (src[i] << 8) | (src[i] >> 8);
#   endif
#else
      dst[i] = (src[i] << 8) | (src[i] >> 8);
#endif
   }
#endif
}

inline void ByteSwap16(uint16_t* dst, const uint16_t* src, size_t count)
{
   // We can assume that unless source and destination are equal, they do not
   // overlap
   if (dst != src)
   {
      ByteSwap16OutOfPlace(dst, src, count);
      return;
   }

   // Byte swap in place
   // TODO Rewrite using Boost.Endian once we upgrade Boost requirements
   for (size_t i = 0; i < count; ++i)
   {
#ifdef _MSC_VER
      dst[i] = _byteswap_ushort(dst[i]);
#elif defined(__GNUC__)
      // __builtin_bswap16() was missing prior to GCC 4.8
#   if ((__GNUC__ * 10000 \
      + __GNUC_MINOR__ * 100 \
      + __GNUC_PATCHLEVEL__) \
      > 40800)
      dst[i] = __builtin_bswap16(dst[i]);
#   elif (defined(__clang__) && __has_builtin(__builtin_bswap16))
      dst[i] = __builtin_bswap16(dst[i]);
#   else
      dst[i] = (dst[i] << 8) | (dst[i] >> 8);
#   endif
#else
      dst[i] = (dst[i] << 8) | (dst[i] >> 8);
#endif
   }
}

inline void RightShift16OutOfPlace(uint16_t* mmiidc_restrict dst,
      const uint16_t* mmiidc_restrict src,
      size_t count, unsigned shift)
{
   for (size_t i = 0; i < count; ++i)
      dst[i] = src[i] >> shift;
}

inline void RightShift16(uint16_t* dst, const uint16_t* src,
      size_t count, unsigned shift)
{
   // We can assume that unless source and destination are equal, they do not
   // overlap
   if (dst != src)
   {
      RightShift16OutOfPlace(dst, src, count, shift);
      return;
   }

   for (size_t i = 0; i < count; ++i)
      dst[i] >>= shift;
}


/*
 * Convert to Micro-Manager's idiosyncratic RGB format
 */
inline void BGR24ToRGB32(uint8_t* dst, const uint8_t* src, size_t count)
{
   for (size_t i = 0; i < count; ++i)
   {
      dst[4 * i + 0] = src[3 * i + 2];
      dst[4 * i + 1] = src[3 * i + 1];
      dst[4 * i + 2] = src[3 * i + 0];
      dst[4 * i + 3] = 0;
   }
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
 * Camera enumeration and resource management for libdc1394 context
 *
 * IIDC::Interface object must outlive all IIDC::Camera objects created from
 * it. It also must be unique (only a single instance should exist).
 *
 * A hub device for IIDC cameras would be a nice idea, but the hub-peripheral
 * interface is broken (it doesn't allow multiple distinguishable copies of the
 * same peripheral device), so we can't do that. Instead, use a global object
 * internal to the device adapter (not user-visible).
 */
class MMIIDCHub {
   typedef MMIIDCHub Self;

   boost::shared_ptr<IIDC::Interface> iidc_;
   std::set<std::string> activeCameras_;

   /*
    * We only want one copy of MMIIDCHub at any given time, but we want to
    * release that copy when not in use (so that destruction happens early
    * enough). So keep a weak pointer to the current instance.
    */
   static boost::weak_ptr<Self> instance_s;

   MMIIDCHub() : iidc_(new IIDC::Interface()) {}
   MMIIDCHub(boost::function<void (const std::string&, bool)> logger) :
      iidc_(new IIDC::Interface(logger))
   {}

public:
   static boost::shared_ptr<Self> GetInstance()
   {
      if (boost::shared_ptr<Self> instance = instance_s.lock())
         return instance;
      boost::shared_ptr<Self> instance(new Self());
      instance_s = instance;
      return instance;
   }

   static boost::shared_ptr<Self> GetInstance(
         boost::function<void (const std::string&, bool)> logger)
   {
      if (boost::shared_ptr<Self> instance = instance_s.lock())
         return instance;
      boost::shared_ptr<Self> instance(new Self(logger));
      instance_s = instance;
      return instance;
   }

   boost::shared_ptr<IIDC::Camera> GetCameraByID(const std::string& id)
   {
      if (activeCameras_.count(id))
         throw Error("Camera " + id + " is already in use");
      return iidc_->NewCamera(id);
   }

   boost::shared_ptr<IIDC::Camera> GetNextAvailableCamera()
   {
      std::vector<std::string> ids = iidc_->GetCameraIDs();
      BOOST_FOREACH(std::string id, ids)
      {
         if (!activeCameras_.count(id))
            return iidc_->NewCamera(id);
      }
      throw Error("No IIDC camera available");
   }

   void PutCamera(const std::string& id)
   {
      activeCameras_.erase(id);
   }

   void RemoveLogger()
   {
      iidc_->RemoveLogger();
   }
};

// Static member variable definition
boost::weak_ptr<MMIIDCHub> MMIIDCHub::instance_s;


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

   CreateStringProperty(MMIIDC_Property_PreInitIsoSpeed,
#ifdef _WIN32
         // 800 Mbps does not appear to work on Windows (CMU driver)
         MMIIDC_Property_PreInitIsoSpeed_400,
#else
         MMIIDC_Property_PreInitIsoSpeed_800,
#endif
         false, 0, true);
   AddAllowedValue(MMIIDC_Property_PreInitIsoSpeed, MMIIDC_Property_PreInitIsoSpeed_100, 100);
   AddAllowedValue(MMIIDC_Property_PreInitIsoSpeed, MMIIDC_Property_PreInitIsoSpeed_200, 200);
   AddAllowedValue(MMIIDC_Property_PreInitIsoSpeed, MMIIDC_Property_PreInitIsoSpeed_400, 400);
   AddAllowedValue(MMIIDC_Property_PreInitIsoSpeed, MMIIDC_Property_PreInitIsoSpeed_800, 800);

   CreateFloatProperty(MMIIDC_Property_PreInitShutterUsPerUnit, 10.0, false, 0, true);
   CreateFloatProperty(MMIIDC_Property_PreInitShutterOffsetUs, 0.0, false, 0, true);

   CreateStringProperty(MMIIDC_Property_PreInitGainUnits, MMIIDC_Property_PreInitGainUnits_Auto,
         false, 0, true);
   AddAllowedValue(MMIIDC_Property_PreInitGainUnits, MMIIDC_Property_PreInitGainUnits_Auto);
   AddAllowedValue(MMIIDC_Property_PreInitGainUnits, MMIIDC_Property_PreInitGainUnits_ArbitraryWithAbsoluteReadOut);
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
   const std::string cameraID(buf);

   err = GetProperty(MMIIDC_Property_PreInit1394Mode, buf);
   if (err != DEVICE_OK)
      return err;
   const std::string opMode(buf);

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

   err = GetProperty(MMIIDC_Property_PreInitGainUnits, buf);
   if (err != DEVICE_OK)
      return err;
   const std::string gainUnitsMode(buf);
   if (gainUnitsMode == MMIIDC_Property_PreInitGainUnits_Auto)
      absoluteGainIsReadOnly_ = false;
   else if (gainUnitsMode == MMIIDC_Property_PreInitGainUnits_ArbitraryWithAbsoluteReadOut)
      absoluteGainIsReadOnly_ = true;

   try
   {
      // We use this device for MMCore logging, if this is the first device.
      hub_ = MMIIDCHub::GetInstance(
            boost::bind(&MMIIDCCamera::LogIIDCMessage, this, _1, _2));

      if (cameraID == MMIIDC_Property_PreInitCameraID_NextAvailable || cameraID.empty())
         iidcCamera_ = hub_->GetNextAvailableCamera();
      else
         iidcCamera_ = hub_->GetCameraByID(cameraID);

      LogMessage(("Camera info from libdc1394:\n" + iidcCamera_->GetInfoDump()).c_str(), true);

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

      err = InitializeVideoModeDependentState(); // Depends on video mode
      if (err != DEVICE_OK)
         return err;

      err = InitializeFeatureProperties(); // May also depend on video mode
      if (err != DEVICE_OK)
         return err;

      // We do not support binning the usual way. Format_7 video modes usually
      // provide binning, but in a vendor-specific way.
      err = CreateIntegerProperty(MMIIDC_Property_Binning, 1, false);
      if (err != DEVICE_OK)
         return err;
      AddAllowedValue(MMIIDC_Property_Binning, "1");

      err = CreateIntegerProperty(MMIIDC_Property_TimeoutMs, 10000, false);
      if (err != DEVICE_OK)
         return err;

#ifndef _WIN32
      err = CreateStringProperty(MMIIDC_Property_AlwaysPoll, "No", false);
      if (err != DEVICE_OK)
         return err;
      AddAllowedValue(MMIIDC_Property_AlwaysPoll, "No");
      AddAllowedValue(MMIIDC_Property_AlwaysPoll, "Yes");
#endif
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
      {
         // The first camera device is used for MMCore logging, so we need to
         // disable it. For now, we do not bother to continue logging after the
         // first camera has been unloaded.
         hub_->RemoveLogger();

         hub_->PutCamera(cameraID);
      }
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

   bool usePolling = false;
   if (HasProperty(MMIIDC_Property_AlwaysPoll))
   {
      char value[MM::MaxStrLength];
      err = GetProperty(MMIIDC_Property_AlwaysPoll, value);
      if (err != DEVICE_OK)
         return err;
      usePolling = (value == std::string("Yes"));
   }

   ResetTimebase();

   try
   {
      if (iidcCamera_->IsOneShotCapable())
         iidcCamera_->StartOneShotCapture(3, timeoutMs,
               boost::bind(&MMIIDCCamera::SnapCallback, this, _1, _2, _3, _4, _5),
               boost::function<void ()>());
      else
         iidcCamera_->StartContinuousCapture(3, 1, timeoutMs, usePolling,
               boost::bind(&MMIIDCCamera::SnapCallback, this, _1, _2, _3, _4, _5),
               boost::function<void ()>());
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
   return static_cast<unsigned>(roiWidth_);
}


unsigned
MMIIDCCamera::GetImageHeight() const
{
   return static_cast<unsigned>(roiHeight_);
}


unsigned
MMIIDCCamera::GetImageBytesPerPixel() const
{
   try
   {
      switch (currentVideoMode_->GetPixelFormat())
      {
         case IIDC::PixelFormatGray8:
         case IIDC::PixelFormatRaw8:
            return 1;
         case IIDC::PixelFormatGray16:
         case IIDC::PixelFormatRaw16:
            return 2;
         case IIDC::PixelFormatYUV444:
         case IIDC::PixelFormatYUV422:
         case IIDC::PixelFormatYUV411:
         case IIDC::PixelFormatRGB8:
            return 4; // This indicates RGB_ to Micro-Manager
         default:
            return 0; // Unsupported format
      }
   }
   CATCH_AND_LOG_ERROR
   return 0;
}


unsigned
MMIIDCCamera::GetNumberOfComponents() const
{
   try
   {
      switch (currentVideoMode_->GetPixelFormat())
      {
         case IIDC::PixelFormatGray8:
         case IIDC::PixelFormatGray16:
         case IIDC::PixelFormatRaw8:
         case IIDC::PixelFormatRaw16:
            return 1;
         case IIDC::PixelFormatYUV444:
         case IIDC::PixelFormatYUV422:
         case IIDC::PixelFormatYUV411:
         case IIDC::PixelFormatRGB8:
            return 4;
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
   return cachedBitsPerSample_;
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


static void
ComputeHardROI(unsigned roiPos, unsigned roiSize, unsigned maxSize,
      unsigned posUnit, unsigned sizUnit,
      unsigned& hardPos, unsigned& hardSize)
{
   // Right/lower-most hardware ROI position to contain desired ROI
   unsigned maxHardPos = posUnit * (roiPos / posUnit);

   hardSize = maxSize; // Initialize to be defensive
   bool ok = false;
   for (hardPos = maxHardPos; ; hardPos -= posUnit)
   {
      // Smallest width/height to contain desired ROI, given hardPos
      unsigned minHardSize = roiSize + roiPos - hardPos;

      hardSize = sizUnit * ((minHardSize - 1) / sizUnit + 1);

      // If posUnit is not a multiple of sizUnit, hardSize may overshoot the
      // chip
      if (hardPos + hardSize > maxSize)
      {
         if (hardPos == 0)
            break; // Pathological, give up to prevent unsigned underflow
         else
            continue; // Try again after shifting hardPos left/up
      }

      ok = true;
      break;
   }

   // In the unlikely situation where we still don't satisfy the constraints,
   // set to full chip.
   if (!ok)
   {
      hardPos = 0;
      hardSize = maxSize;
   }
}


int
MMIIDCCamera::SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize)
{
   unsigned maxWidth = currentVideoMode_->GetMaxWidth();
   unsigned maxHeight = currentVideoMode_->GetMaxHeight();

   if (x + xSize > maxWidth)
      return AdHocErrorCode("ROI exceeds allowed maximum size");
   if (y + ySize > maxHeight)
      return AdHocErrorCode("ROI exceeds allowed maximum size");
   if (xSize == 0 || ySize == 0)
      return AdHocErrorCode("ROI must not be empty");

   roiLeft_ = x;
   roiTop_ = y;
   roiWidth_ = xSize;
   roiHeight_ = ySize;

   // Determine the minimal hard ROI that encompasses the desired ROI
   unsigned posUnitH, posUnitV;
   unsigned sizUnitH, sizUnitV;
   try
   {
      iidcCamera_->GetImagePositionUnits(posUnitH, posUnitV);
      iidcCamera_->GetImageSizeUnits(sizUnitH, sizUnitV);
   }
   CATCH_AND_RETURN_ERROR

   unsigned hardLeft, hardTop, hardWidth, hardHeight;
   ComputeHardROI(roiLeft_, roiWidth_, maxWidth,
         posUnitH, sizUnitH,
         hardLeft, hardWidth);
   ComputeHardROI(roiTop_, roiHeight_, maxHeight,
         posUnitV, sizUnitV,
         hardTop, hardHeight);

   // Remember how to apply soft ROI to images
   softROILeft_ = roiLeft_ - hardLeft;
   softROITop_ = roiTop_ - hardTop;

   LogMessage("Requested ROI: (" +
         boost::lexical_cast<std::string>(roiLeft_) + ", " +
         boost::lexical_cast<std::string>(roiTop_) + ", " +
         boost::lexical_cast<std::string>(roiWidth_) + ", " +
         boost::lexical_cast<std::string>(roiHeight_) + ");\n" +
         "Image position units: (" +
         boost::lexical_cast<std::string>(posUnitH) + ", " +
         boost::lexical_cast<std::string>(posUnitV) + ");\n" +
         "Image size units: (" +
         boost::lexical_cast<std::string>(sizUnitH) + ", " +
         boost::lexical_cast<std::string>(sizUnitV) + ");\n" +
         "Camera ROI: (" +
         boost::lexical_cast<std::string>(hardLeft) + ", " +
         boost::lexical_cast<std::string>(hardTop) + ", " +
         boost::lexical_cast<std::string>(hardWidth) + ", " +
         boost::lexical_cast<std::string>(hardHeight) + ");\n" +
         "Software ROI (relative to camera ROI): (" +
         boost::lexical_cast<std::string>(softROILeft_) + ", " +
         boost::lexical_cast<std::string>(softROITop_) + ", " +
         boost::lexical_cast<std::string>(roiWidth_) + ", " +
         boost::lexical_cast<std::string>(roiHeight_) + ")", true);

   try
   {
      iidcCamera_->SetImageROI(hardLeft, hardTop, hardWidth, hardHeight);
   }
   CATCH_AND_RETURN_ERROR

   UpdateFramerate();

   return DEVICE_OK;
}


int
MMIIDCCamera::GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize)
{
   x = roiLeft_;
   y = roiTop_;
   xSize = roiWidth_;
   ySize = roiHeight_;
   return DEVICE_OK;
}


int
MMIIDCCamera::ClearROI()
{
   return SetROI(0, 0,
         currentVideoMode_->GetMaxWidth(),
         currentVideoMode_->GetMaxHeight());
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

   bool usePolling = false;
   if (HasProperty(MMIIDC_Property_AlwaysPoll))
   {
      char value[MM::MaxStrLength];
      err = GetProperty(MMIIDC_Property_AlwaysPoll, value);
      if (err != DEVICE_OK)
         return err;
      usePolling = (value == std::string("Yes"));
   }

   stopOnOverflow_ = stopOnOverflow;

   err = GetCoreCallback()->PrepareForAcq(this);
   if (err != DEVICE_OK)
      return err;

   ResetTimebase();

   try
   {
      if (iidcCamera_->IsMultiShotCapable() && count < 65536)
      {
         iidcCamera_->StartMultiShotCapture(16, static_cast<uint16_t>(count), timeoutMs,
               usePolling,
               boost::bind(&MMIIDCCamera::SequenceCallback, this, _1, _2, _3, _4, _5),
               boost::bind(&MMIIDCCamera::SequenceFinishCallback, this));
      }
      else
      {
         size_t nrFrames = (count == LONG_MAX) ? static_cast<size_t>(count) : 0;
         iidcCamera_->StartContinuousCapture(16, nrFrames, timeoutMs,
               usePolling,
               boost::bind(&MMIIDCCamera::SequenceCallback, this, _1, _2, _3, _4, _5),
               boost::bind(&MMIIDCCamera::SequenceFinishCallback, this));
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
MMIIDCCamera::OnRightShift16BitSamples(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      bool flag;
      {
         boost::lock_guard<boost::mutex> lock(sampleProcessingMutex_);
         flag = rightShift16BitSamples_;
      }
      pProp->Set(flag ? "Yes" : "No");
   }
   else if (eAct == MM::AfterSet)
   {
      std::string value;
      pProp->Get(value);
      bool flag = (value == "Yes");
      {
         boost::lock_guard<boost::mutex> lock(sampleProcessingMutex_);
         rightShift16BitSamples_ = flag;
      }
   }
   return DEVICE_OK;
}


int
MMIIDCCamera::OnFormat7PacketSizeNegativeDelta(MM::PropertyBase*, MM::ActionType eAct)
{
   // This property can be used to disallow setting the Format_7 packet size
   // too close to the allowed maximum (which, I have found, can result in
   // corrupted images in some Linux environments -- although it is not clear
   // how common).
   try
   {
      if (eAct == MM::AfterSet)
         UpdateFramerate();
   }
   CATCH_AND_RETURN_ERROR
   return DEVICE_OK;
}


int
MMIIDCCamera::OnFormat7PacketSize(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   try
   {
      if (eAct == MM::BeforeGet)
      {
         uint32_t packetSize;
         if (currentVideoMode_->IsFormat7())
            packetSize = iidcCamera_->GetFormat7PacketSize();
         else
            packetSize = cachedPacketSize_;
         pProp->Set(static_cast<long>(packetSize));
      }
      else if (eAct == MM::AfterSet)
      {
         long v;
         pProp->Get(v);
         uint32_t packetSize = static_cast<uint32_t>(std::max(0L, v));
         if (currentVideoMode_->IsFormat7())
         {
            cachedPacketSize_ = packetSize;
            UpdateFramerate(true);
            pProp->Set(static_cast<long>(cachedPacketSize_));
         }
         else
         {
            // Forbid changing, since we don't have a good way of validating the value
            pProp->Set(static_cast<long>(cachedPacketSize_));
            return AdHocErrorCode("Packet size cannot be changed when not in a Format_7 video mode");
         }
      }
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
            brightness->SetAbsoluteValue(static_cast<float>(value));
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
         if (gain->HasAbsoluteControl() && !absoluteGainIsReadOnly_)
            pProp->Set(gain->GetAbsoluteValue());
         else
            pProp->Set(static_cast<long>(gain->GetValue()));
      }
      else if (eAct == MM::AfterSet)
      {
         if (gain->HasAbsoluteControl() && !absoluteGainIsReadOnly_)
         {
            double value;
            pProp->Get(value);
            gain->SetAbsoluteValue(static_cast<float>(value));
         }
         else
         {
            long value;
            pProp->Get(value);
            gain->SetValue(static_cast<uint32_t>(value));

            if (gain->HasAbsoluteControl() && absoluteGainIsReadOnly_)
            {
               int err;
               err = OnPropertyChanged(MMIIDC_Property_GainAbsolute,
                     boost::lexical_cast<std::string>(gain->GetAbsoluteValue()).c_str());
               if (err != DEVICE_OK)
                  return err;
            }
         }
      }
   }
   CATCH_AND_RETURN_ERROR
   return DEVICE_OK;
}


int
MMIIDCCamera::OnReadOnlyAbsoluteGain(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   boost::shared_ptr<IIDC::GainFeature> gain = iidcCamera_->GetGainFeature();
   try
   {
      if (eAct == MM::BeforeGet)
         pProp->Set(gain->GetAbsoluteValue());
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

   boost::shared_ptr<IIDC::ShutterFeature> shutter = iidcCamera_->GetShutterFeature();
   bool hasAbsoluteShutterControl =
      iidcCamera_->GetVendorAVT()->HasExtendedShutter() ||
      iidcCamera_->GetShutterFeature()->HasAbsoluteControl();
   err = CreateStringProperty(MMIIDC_Property_SupportsAbsoluteShutter,
         hasAbsoluteShutterControl ? "Yes" : "No", true);
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

   // This one needs a handler because its value is accessed asynchronously
   err = CreateStringProperty(MMIIDC_Property_RightShift16BitSamples, "No", false,
         new CPropertyAction(this, &MMIIDCCamera::OnRightShift16BitSamples));
   if (err != DEVICE_OK)
      return err;
   rightShift16BitSamples_ = false;
   AddAllowedValue(MMIIDC_Property_RightShift16BitSamples, "No");
   AddAllowedValue(MMIIDC_Property_RightShift16BitSamples, "Yes");

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
         case IIDC::PixelFormatYUV444:
         case IIDC::PixelFormatYUV422:
         case IIDC::PixelFormatYUV411:
         case IIDC::PixelFormatRGB8:
         case IIDC::PixelFormatRaw8:
         case IIDC::PixelFormatRaw16:
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
MMIIDCCamera::InitializeVideoModeDependentState()
{
   cachedBitsPerSample_ = iidcCamera_->GetBitsPerSample();

   // Always turn off "frame rate prioritized control"; as scientific imaging
   // software, it is more important to us that the exposure ("shutter") be
   // predictable
   boost::shared_ptr<IIDC::FrameRateFeature> prioritizedFramerate = iidcCamera_->GetFrameRateFeature();
   if (prioritizedFramerate->IsPresent() && prioritizedFramerate->IsSwitchable())
      prioritizedFramerate->SetOnOff(false);

   iidcCamera_->SetMaxFramerate(0);
   cachedFramerate_ = iidcCamera_->GetFramerate();
   LogMessage("IIDC Framerate now set to " +
         boost::lexical_cast<std::string>(cachedFramerate_) + " (fps)");

   int err;
   err = CreateFloatProperty(MMIIDC_Property_MaxFramerate, cachedFramerate_, true,
         new CPropertyAction(this, &MMIIDCCamera::OnMaximumFramerate));
   if (err != DEVICE_OK)
      return err;

   // For packet size, 8192 will be clipped to the maximum allowed when we
   // switch to Format_7
   cachedPacketSize_ = 8192;
   err = CreateIntegerProperty(MMIIDC_Property_Format7PacketSize, cachedPacketSize_,
         false, new CPropertyAction(this, &MMIIDCCamera::OnFormat7PacketSize));
   if (err != DEVICE_OK)
      return err;

   err = CreateStringProperty(MMIIDC_Property_Format7PacketSizeMode,
         MMIIDC_Property_Format7PacketSizeMode_UseMax, false);
   AddAllowedValue(MMIIDC_Property_Format7PacketSizeMode,
         MMIIDC_Property_Format7PacketSizeMode_UseMax);
   AddAllowedValue(MMIIDC_Property_Format7PacketSizeMode,
         MMIIDC_Property_Format7PacketSizeMode_KeepSetting);

   boost::shared_ptr<IIDC::ShutterFeature> shutter = iidcCamera_->GetShutterFeature();
   if (!shutter->IsPresent() || !shutter->HasManualMode())
   {
      LogMessage("Warning: Camera does not allow control of exposure (integration time)");
      cachedExposure_ = 0.0;
   }
   else
   {
      shutter->SetAutoMode(false);

      if (iidcCamera_->GetVendorAVT()->HasExtendedShutter())
      {
         LogMessage("Camera has AVT-specific extended shutter control; using");
      }
      else if (shutter->HasAbsoluteControl())
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

   err = ClearROI();
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

      if (gain->HasAbsoluteControl() && absoluteGainIsReadOnly_)
      {
         gain->SetAbsoluteControl(false);
         int err;
         err = CreateFloatProperty(MMIIDC_Property_GainAbsolute, gain->GetAbsoluteValue(),
               true, new CPropertyAction(this, &MMIIDCCamera::OnReadOnlyAbsoluteGain));
         if (err != DEVICE_OK)
            return err;
      }
      else if (gain->HasAbsoluteControl())
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

      if (!gain->HasAbsoluteControl() || absoluteGainIsReadOnly_)
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
MMIIDCCamera::UpdateFramerate(bool forceKeepPacketSize)
{
   int err;
   long format7PacketSizeDelta;
   err = GetProperty(MMIIDC_Property_Format7PacketSizeNegativeDelta, format7PacketSizeDelta);
   if (err != DEVICE_OK)
      return err;

   char packetSizeMode[MM::MaxStrLength];
   err = GetProperty(MMIIDC_Property_Format7PacketSizeMode, packetSizeMode);
   if (err != DEVICE_OK)
      return err;
   if (packetSizeMode == std::string(MMIIDC_Property_Format7PacketSizeMode_KeepSetting))
      forceKeepPacketSize = true;

   if (currentVideoMode_->IsFormat7() && forceKeepPacketSize)
   {
      iidcCamera_->SetFormat7PacketSize(cachedPacketSize_,
            static_cast<unsigned>(-format7PacketSizeDelta));
   }
   else
   {
      iidcCamera_->SetMaxFramerate(static_cast<unsigned>(-format7PacketSizeDelta));
   }
   cachedFramerate_ = iidcCamera_->GetFramerate();
   if (currentVideoMode_->IsFormat7())
      cachedPacketSize_ = iidcCamera_->GetFormat7PacketSize();

   LogMessage("IIDC Framerate now set to " +
         boost::lexical_cast<std::string>(cachedFramerate_) + " (fps)");
   err = OnPropertyChanged(MMIIDC_Property_MaxFramerate,
         boost::lexical_cast<std::string>(cachedFramerate_).c_str());
   if (err != DEVICE_OK)
      return err;

   err = OnPropertyChanged(MMIIDC_Property_Format7PacketSize,
         boost::lexical_cast<std::string>(cachedPacketSize_).c_str());
   if (err != DEVICE_OK)
      return err;

   return DEVICE_OK;
}


int
MMIIDCCamera::VideoModeDidChange()
{
   cachedBitsPerSample_ = iidcCamera_->GetBitsPerSample();

   int err;
   err = UpdateFramerate();
   if (err != DEVICE_OK)
      return err;
   err = ClearROI();
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
   if (iidcCamera_->GetVendorAVT()->HasExtendedShutter())
   {
      uint32_t microseconds =
         static_cast<uint32_t>(std::floor(1000.0 * milliseconds) + 0.5);
      iidcCamera_->GetVendorAVT()->SetExtendedShutterUs(microseconds);
      cachedExposure_ = GetExposureUncached();
      return;
   }

   // Standard IIDC case

   boost::shared_ptr<IIDC::ShutterFeature> shutter = iidcCamera_->GetShutterFeature();
   if (!shutter->IsPresent() || !shutter->HasManualMode())
   {
      LogMessage("Cannot set exposure (not supported by camera)");
      return;
   }
   if (shutter->GetAbsoluteControl())
   {
      std::pair<float, float> limits = shutter->GetAbsoluteMinMax();
      float seconds = static_cast<float>(milliseconds / 1000.0f);
      float actualSeconds = std::max(seconds, limits.first);
      actualSeconds = std::min(actualSeconds, limits.second);
      if (actualSeconds != seconds)
      {
         LogMessage("Requested exposure (" + boost::lexical_cast<std::string>(seconds) +
               " s) out of range (" +
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
   if (iidcCamera_->GetVendorAVT()->HasExtendedShutter())
   {
      uint32_t microseconds = iidcCamera_->GetVendorAVT()->GetExtendedShutterUs();
      return static_cast<double>(microseconds) / 1000.0;
   }

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
   if (iidcCamera_->GetVendorAVT()->HasExtendedShutter())
   {
      return std::make_pair(
            iidcCamera_->GetVendorAVT()->GetExtendedShutterMinUs() / 1000.0,
            iidcCamera_->GetVendorAVT()->GetExtendedShutterMaxUs() / 1000.0);
   }

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


// Common processing pipeline for snap or sequence images
// After performing all necessary format conversions to prepare the libdc1394
// image for passing to MMCore, calls resultCallback with the resulting image.
// The resultCallback is also passed the MM 'bytesPerPixel' indicating the
// final format.
void
MMIIDCCamera::ProcessImage(const void* source, bool ownResultBuffer,
      IIDC::PixelFormat sourceFormat,
      size_t sourceWidth, size_t sourceHeight,
      size_t destLeft, size_t destTop,
      size_t destWidth, size_t destHeight,
      uint32_t timestampUs,
      boost::function<void (const void*, size_t, uint32_t)> resultCallback)
{
   // This function implements a fixed processing pipeline that minimizes
   // unnecessary copying of the pixels.

   // The code is way too long and ought to be refactored, but at the moment
   // this is the simplest way to code it without either adding unnecessary
   // image copying or developing an elaborate pipeline library, given the
   // complicated requirements we need to satisfy (C++11 unique_ptr might make
   // it easier to refactor).

   // First, determine the necessary pixel format conversions.

   size_t destBytesPerPixel;
   bool isRGB = false;
   bool doConvertColor = false;

   switch (sourceFormat)
   {
      case IIDC::PixelFormatGray8:
      case IIDC::PixelFormatRaw8:
         destBytesPerPixel = 1;
         break;

      case IIDC::PixelFormatGray16:
      case IIDC::PixelFormatRaw16:
         destBytesPerPixel = 2;
         break;

      case IIDC::PixelFormatYUV444:
      case IIDC::PixelFormatYUV422:
      case IIDC::PixelFormatYUV411:
         destBytesPerPixel = 4;
         isRGB = true;
         doConvertColor = true;
         break;

      case IIDC::PixelFormatRGB8:
         destBytesPerPixel = 4;
         isRGB = true;
         break;

      default:
         BOOST_THROW_EXCEPTION(Error("Unsupported pixel format"));
   }

   // 'pixels' always points to the current image
   const uint8_t* pixels = static_cast<const uint8_t*>(source);

   // Scoped array objects to manage memory for intermediate buffers. Not all
   // may be used depending on the code path. Any that are used persist until
   // the result callback returns.
   boost::scoped_array<uint8_t> bufferConvertedToRGB;
   boost::scoped_array<uint8_t> bufferAppliedSoftROI;
   boost::scoped_array<uint8_t> bufferConvertedTo32Bit;
   boost::scoped_array<uint8_t> bufferByteSwapped;
   boost::scoped_array<uint8_t> bufferRightShifted;

   // In each of the following processing stages, 'pixels' is processed
   // (possibly in place) and 'pixels' is pointed to the result. If the
   // processing occurs out-of-place, the result is placed in an auto-deleted
   // buffer (scoped_array) or the final destination (if no further
   // out-of-place conversion is to occur).
   bool needOwnedCopy = ownResultBuffer;
   uint8_t* destination = 0;

   /*
    * Stage: Convert color (e.g. YUV) to RGB24
    */

   if (doConvertColor)
   {
      // Always place result in allocated buffer, since further processing
      // (conversion to 32-bit format) is needed
      uint8_t* dest = new uint8_t[sourceWidth * sourceHeight * 3];
      bufferConvertedToRGB.reset(dest);
      IIDC::ConvertToRGB8(dest, pixels,
            sourceWidth, sourceHeight, sourceFormat);
      pixels = dest;
   }

   /*
    * Stage: Apply soft ROI
    */

   if (destWidth != sourceWidth || destHeight != sourceHeight)
   {
      size_t bpp = (isRGB ? 3 : destBytesPerPixel);
      uint8_t* dest = new uint8_t[destWidth * destHeight * bpp];
      // If grayscale, all following stages can be performed in-place, so we
      // can write to the final result buffer
      if (needOwnedCopy && !isRGB)
      {
         assert (destination == 0);
         destination = dest;
         needOwnedCopy = false;
      }
      else
      {
         bufferAppliedSoftROI.reset(dest);
      }

      ApplySoftROI(dest, pixels, bpp,
            sourceWidth, sourceHeight,
            destLeft, destTop, destWidth, destHeight);
      pixels = dest;
   }

   /*
    * Stage: Convert RGB24 to RGB32
    */

   if (isRGB)
   {
      uint8_t* dest = new uint8_t[destWidth * destHeight * 4];;
      // All following stages can be performed in-place, so we can write to the
      // final result buffer
      if (needOwnedCopy)
      {
         assert (destination == 0);
         destination = dest;
         needOwnedCopy = false;
      }
      else
      {
         bufferConvertedTo32Bit.reset(dest);
      }

      // We seem to get BGR images out of libdc1394 (seen with iSight YUV
      // formats on OS X; AVT YUV or RGB formats on OS X and Windows), so
      // handle BGR-to-RGB at the same time.
      BGR24ToRGB32(dest, pixels, destWidth * destHeight);
      pixels = dest;
   }

   /*
    * Stage: Byte-swap 16-bit samples if necessary
    */

   if (HostIsLittleEndian() && destBytesPerPixel == 2)
   {
      uint8_t* dest = 0;
      if (!needOwnedCopy && pixels != source) // Can do in-place
      {
         dest = const_cast<uint8_t*>(pixels);
      }
      else
      {
         dest = new uint8_t[destWidth * destHeight * 2];
         if (needOwnedCopy)
         {
            assert (destination == 0);
            destination = dest;
            needOwnedCopy = false;
         }
         else
         {
            bufferByteSwapped.reset(dest);
         }
      }

      ByteSwap16(reinterpret_cast<uint16_t*>(dest),
            reinterpret_cast<const uint16_t*>(pixels),
            destWidth * destHeight);
      pixels = dest;
   }

   /*
    * Stage: Right-shift less-than-16-bit samples if requested
    */

   bool doRightShift = (destBytesPerPixel == 2);
   {
      boost::lock_guard<boost::mutex> g(sampleProcessingMutex_);
      doRightShift = doRightShift && rightShift16BitSamples_;
   }
   if (doRightShift)
   {
      uint8_t* dest = 0;
      if (!needOwnedCopy && pixels != source) // Can do in-place
      {
         dest = const_cast<uint8_t*>(pixels);
      }
      else
      {
         dest = new uint8_t[destWidth * destHeight * 2];
         if (needOwnedCopy)
         {
            assert (destination == 0);
            destination = dest;
            needOwnedCopy = false;
         }
         else
         {
            bufferRightShifted.reset(dest);
         }
      }

      unsigned shift = 16 - cachedBitsPerSample_;
      RightShift16(reinterpret_cast<uint16_t*>(dest),
            reinterpret_cast<const uint16_t*>(pixels),
            destWidth * destHeight, shift);
      pixels = dest;
   }

   /*
    * Final stage: Copy (if we need to own destination)
    */

   if (needOwnedCopy)
   {
      assert (pixels == source); // Haven't made a copy yet
      destination = new uint8_t[destWidth * destHeight * destBytesPerPixel];
      needOwnedCopy = false;
      memcpy(destination, pixels, destWidth * destHeight * destBytesPerPixel);
      pixels = destination;
   }

   resultCallback(pixels, destBytesPerPixel, timestampUs);
}


void
MMIIDCCamera::SnapCallback(const void* pixels, size_t width, size_t height,
   IIDC::PixelFormat format, uint32_t timestampUs)
{
   // Request that the callback be passed an owned pixel buffer
   ProcessImage(pixels, true, format, width, height,
         softROILeft_, softROITop_, roiWidth_, roiHeight_,
         timestampUs,
         boost::bind(&MMIIDCCamera::ProcessedSnapCallback,
            this, _1, roiWidth_, roiHeight_, _2, _3));
}


void
MMIIDCCamera::SequenceCallback(const void* pixels, size_t width, size_t height,
   IIDC::PixelFormat format, uint32_t timestampUs)
{
   // Request that the callback be passed an unowned pixel buffer, since the
   // pixels will be copied to the Core
   ProcessImage(pixels, false, format, width, height,
         softROILeft_, softROITop_, roiWidth_, roiHeight_,
         timestampUs,
         boost::bind(&MMIIDCCamera::ProcessedSequenceCallback,
            this, _1, roiWidth_, roiHeight_, _2, _3));
}


void
MMIIDCCamera::ProcessedSnapCallback(const void* pixels,
      size_t width, size_t height, size_t bytesPerPixel,
      uint32_t /*timestampUs*/)
{
   // We own pixels and must delete
   snappedPixels_.reset(static_cast<const unsigned char*>(pixels));
   snappedWidth_ = width;
   snappedHeight_ = height;
   snappedBytesPerPixel_ = bytesPerPixel;
   // We don't yet have a mechanism to pass metadata (such as the timestamp) to
   // the Core for snapped images.
}


void
MMIIDCCamera::ProcessedSequenceCallback(const void* pixels,
      size_t width, size_t height, size_t bytesPerPixel,
      uint32_t timestampUs)
{
   Metadata md;

   char label[MM::MaxStrLength];
   GetLabel(label);
   md.put("Camera", label);

#ifndef _WIN32
   // The Windows CMU backend does not provide a valid timestamp (the field
   // contains garbage), so we skip it.
   // Linux and OS X backends provide the 1394 bus timestamp, which is probably
   // pretty accurate.
   double timestampMs = ComputeRelativeTimestampMs(timestampUs);

   md.put(MM::g_Keyword_Elapsed_Time_ms,
         CDeviceUtils::ConvertToString(timestampMs));
#endif

   std::string serializedMD(md.Serialize());

   const unsigned char* bytes = reinterpret_cast<const unsigned char*>(pixels);

   // InsertImage() wants unsigned, not size_t; placate VC++.
   unsigned uWidth = static_cast<unsigned>(width);
   unsigned uHeight = static_cast<unsigned>(height);
   unsigned uBytesPerPixel = static_cast<unsigned>(bytesPerPixel);

   int err;
   err = GetCoreCallback()->InsertImage(this, bytes, uWidth, uHeight, uBytesPerPixel,
         serializedMD.c_str());
   if (err == DEVICE_BUFFER_OVERFLOW)
   {
      if (!stopOnOverflow_)
      {
         GetCoreCallback()->ClearImageBuffer(this);
         err = GetCoreCallback()->InsertImage(this, bytes, uWidth, uHeight, uBytesPerPixel,
               serializedMD.c_str(), false);
      }
      else
      {
         BOOST_THROW_EXCEPTION(Error("Sequence buffer overflow"));
      }
   }
   if (err != DEVICE_OK)
      BOOST_THROW_EXCEPTION(Error("Unknown error (" +
               boost::lexical_cast<std::string>(err) +
               ") while placing image in sequence buffer"));
}


void
MMIIDCCamera::SequenceFinishCallback()
{
   GetCoreCallback()->AcqFinished(this, DEVICE_OK);
}


void
MMIIDCCamera::ResetTimebase()
{
   boost::lock_guard<boost::mutex> lock(timebaseMutex_);
   timebaseUs_ = 0;
}


double
MMIIDCCamera::ComputeRelativeTimestampMs(uint32_t rawTimeStampUs)
{
   // We do not have any easy way to record the equivalent timestamp before
   // starting the capture. Instead, we will use the timestamp of the first
   // frame coming to us as the timebase.
   {
      boost::lock_guard<boost::mutex> lock(timebaseMutex_);
      if (timebaseUs_ == 0)
         std::swap(timebaseUs_, rawTimeStampUs);
      else
         rawTimeStampUs -= timebaseUs_;
   }

   return rawTimeStampUs / 1000.0;
}


int
MMIIDCCamera::AdHocErrorCode(const std::string& message)
{
   if (nextAdHocErrorCode_ > MMIIDC_Error_AdHoc_Max)
      nextAdHocErrorCode_ = MMIIDC_Error_AdHoc_Min;
   int code = nextAdHocErrorCode_++;
   SetErrorText(code, message.c_str());
   return code;
}


void
MMIIDCCamera::LogIIDCMessage(const std::string& message, bool isDebug)
{
   LogMessage(message.c_str(), isDebug);
}
