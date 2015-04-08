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

#include "IIDCInterface.h"

#include "IIDCCapture.h"
#include "IIDCError.h"
#include "IIDCVideoMode.h"

#include <boost/bind.hpp>
#include <boost/format.hpp>
#include <boost/lexical_cast.hpp>

#include <boost/version.hpp>
#if BOOST_VERSION / 100 == 1048
   // In Boost 1.48, the boost::move provided by Boost.Move and Boost.Thread
   // collide. Avoid including Boost.Move header so that we use the
   // Boost.Thread version.
   // See also: https://github.com/libcoin/libcoin/issues/47
#else
#  include <boost/move/move.hpp>
#endif

#include <iomanip>
#include <ios>
#include <set>
#include <sstream>


namespace IIDC {
namespace {

// Usage: boost::lexical_cast< HexTo<uint32_t> >("1234abcd")
template <typename T>
class HexTo
{
   T value;
public:
   operator T() const { return value; }
   friend std::istream& operator>>(std::istream& instrm, HexTo& out)
   { instrm >> std::hex >> out.value; return instrm; }
};


static std::string
CameraIdToString(const dc1394camera_id_t* id)
{
   std::ostringstream strm;
   strm << (boost::format("%016x") % id->guid) << "-" << id->unit;
   return strm.str();
}


static void
StringToCameraId(const std::string& idString, dc1394camera_id_t* id)
{
   size_t dashPos = idString.find('-');
   if (dashPos == std::string::npos)
      throw Error("Invalid camera id");
   try
   {
      id->guid = boost::lexical_cast< HexTo<uint64_t> >(idString.substr(0, dashPos));
      id->unit = boost::lexical_cast<uint16_t>(idString.substr(dashPos + 1));
   }
   catch (const boost::bad_lexical_cast&)
   {
      throw Error("Invalid camera id");
   }
}


static float
GetVideoModeMaxFramerateForIsoSpeed(dc1394video_mode_t mode, unsigned nominalMbps)
{
   const size_t nrVideoModes = 23;
   const dc1394video_mode_t minVideoMode = DC1394_VIDEO_MODE_160x120_YUV444;
   const size_t nrSpeeds = 6; // 100, 200, ..., 3200
   BOOST_STATIC_ASSERT(DC1394_VIDEO_MODE_1600x1200_MONO16 - minVideoMode + 1 == nrVideoModes);

   // See tables in IIDC spec (p 58 in v1.31).
   static float maxRateTable[nrVideoModes][nrSpeeds] =
   { //   100    200   400  800 1600 3200
      {   240,   240,  240, 240, 240, 240 }, // Format_0
      {    60,    60,  120, 240, 240, 240 },
      {    15,    30,   60, 120, 240, 240 },
      {   7.5,    15,   30,  60, 120, 240 },
      {   7.5,    15,   30,  60, 120, 240 },
      {    15,    30,   60, 120, 240, 240 },
      {   7.5,    15,   30,  60, 120, 240 },

      {   7.5,    15,   30,  60, 120, 240 }, // Format_1
      {     0,   7.5,   15,  30,  60, 120 },
      {    15,    30,   60, 120, 240, 240 },
      {  3.75,   7.5,   15,  30,  60, 120 },
      { 1.875,  3.75,  7.5,  15,  30,  60 },
      {   7.5,    15,   30,  60, 120, 240 },
      {   7.5,    15,   30,  60, 120, 240 },
      {  3.75,   7.5,   15,  30,  60, 120 },

      { 1.875,  3.75,  7.5,  15,  30,  60 }, // Format_2
      { 1.875,  3.75,  7.5,  15,  30,  60 },
      {  3.75,   7.5,   15,  30,  60, 120 },
      { 1.875,  3.75,  7.5,  15,  30,  60 },
      {     0, 1.875, 3.75, 7.5,  15,  30 },
      {  3.75,   7.5,   15,  30,  60, 120 },
      { 1.875,  3.75,  7.5,  15,  30,  60 },
      { 1.875,  3.75,  7.5,  15,  30,  60 },
   };

   // 100 -> 0, 200 -> 1, ..., 3200 -> 5
   unsigned speedIndex = 0;
   for (unsigned x = nominalMbps / 100 >> 1; x > 0; x >>= 1)
      ++speedIndex;
   if (speedIndex >= nrSpeeds)
      throw Error("Invalid isochronous transmission speed");

   int modeIndex = mode - minVideoMode;
   if (modeIndex < 0 || modeIndex >= nrVideoModes)
      throw Error("Invalid or non-conventional video mode");

   return maxRateTable[modeIndex][speedIndex];
}

} // anonymous namespace


Interface::Interface()
{
   libdc1394context_ = dc1394_new();
   if (!libdc1394context_)
      throw Error("Cannot create libdc1394 context");

   // TODO dc1394_log_register_handler()
}


Interface::~Interface()
{
   dc1394_free(libdc1394context_);
}


std::vector<std::string>
Interface::GetCameraIDs()
{
   dc1394error_t err;
   dc1394camera_list_t* cameraList;
   err = dc1394_camera_enumerate(libdc1394context_, &cameraList);
   if (err != DC1394_SUCCESS)
      throw Error(err, "Cannot get list of available cameras");
   std::vector<std::string> idList;
   idList.reserve(cameraList->num);
   for (uint32_t i = 0; i < cameraList->num; ++i)
   {
      dc1394camera_id_t* id = &cameraList->ids[i];
      idList.push_back(CameraIdToString(id));
   }
   dc1394_camera_free_list(cameraList);
   return idList;
}


boost::shared_ptr<Camera>
Interface::NewCamera(const std::string& idString)
{
   dc1394camera_id_t id;
   StringToCameraId(idString, &id);
   return boost::make_shared<Camera>(shared_from_this(), id.guid, id.unit);
}


Camera::Camera(boost::shared_ptr<Interface> context, uint64_t guid, int unit)
{
   libdc1394camera_ = dc1394_camera_new_unit(context->GetLibDC1394Context(), guid, unit);
   if (!libdc1394camera_)
      throw Error("Cannot connect to camera");

   if (IsPowerSwitchable())
   {
      dc1394error_t err;
      err = dc1394_camera_set_power(libdc1394camera_, DC1394_ON);
      if (err != DC1394_SUCCESS)
         throw Error(err, "Cannot switch on camera");
   }

   // Release resources that may be left allocated by other (crashed)
   // processes. Not all platforms and drivers support this, so we ignore
   // errors.
   // It might be that some platforms do cleanup automatically, but it appears
   // that at least Linux does not.
   // TODO Test on each platform after forced kill
#ifdef WIN32 // For some reason, dc1394_iso_release_*() are not available in some Linux distros.
   // The maximum total isochronous bandwidth units is 4915 (releasing more
   // than is allocated is documented to be okay).
   dc1394_iso_release_bandwidth(libdc1394camera_, 4915);
   // Channels range from 0 to 63; release all.
   for (int i = 0; i < 64; ++i)
      dc1394_iso_release_channel(libdc1394camera_, i);
#else
   // Possible alternative:
   // (Known not to be implemented on Windows)
   dc1394_reset_bus(libdc1394camera_);
#endif

   dc1394error_t err;
   err = dc1394_camera_reset(libdc1394camera_);
   if (err != DC1394_SUCCESS)
      throw Error(err, "Cannot reset camera settings");
}


Camera::~Camera()
{
   StopCapture();

   if (IsPowerSwitchable())
   {
      dc1394error_t err;
      err = dc1394_camera_set_power(libdc1394camera_, DC1394_OFF);
      if (err != DC1394_SUCCESS)
      {
         // TODO Log
      }
   }

   dc1394_camera_free(libdc1394camera_);
}


std::string
Camera::GetCameraID() const
{
   dc1394camera_id_t id;
   id.guid = GetGUID();
   id.unit = GetUnitNo();
   return CameraIdToString(&id);
}


std::string
Camera::GetIIDCVersion() const
{
   dc1394iidc_version_t version = libdc1394camera_->iidc_version;
   switch (version)
   {
      case DC1394_IIDC_VERSION_1_04: return "1.04";
      case DC1394_IIDC_VERSION_1_20: return "1.20";
      case DC1394_IIDC_VERSION_PTGREY: return "PTGREY";
      case DC1394_IIDC_VERSION_1_30: return "1.30";
      case DC1394_IIDC_VERSION_1_31: return "1.31";
      case DC1394_IIDC_VERSION_1_32: return "1.32";
      case DC1394_IIDC_VERSION_1_33: return "1.33";
      case DC1394_IIDC_VERSION_1_34: return "1.34";
      case DC1394_IIDC_VERSION_1_35: return "1.35";
      case DC1394_IIDC_VERSION_1_36: return "1.36";
      case DC1394_IIDC_VERSION_1_37: return "1.37";
      case DC1394_IIDC_VERSION_1_38: return "1.38";
      case DC1394_IIDC_VERSION_1_39: return "1.39";
#if DC1394_IIDC_VERSION_1_39 < DC1394_IIDC_VERSION_MAX
#   error switch statement case clauses need update for this libdc1394
#endif
   }
   return "Unknown";
}


void
Camera::ResetBus()
{
   EnsureNotCapturing();

   dc1394error_t err;
   err = dc1394_reset_bus(libdc1394camera_);
   if (err != DC1394_SUCCESS)
      throw Error(err, "Cannot reset 1394 bus");
}


std::pair<uint32_t, uint32_t>
Camera::Get1394NodeAndGeneration()
{
   uint32_t node, generation;
   dc1394error_t err;
   err = dc1394_camera_get_node(libdc1394camera_, &node, &generation);
   if (err != DC1394_SUCCESS)
      throw Error(err, "Cannot get 1394 node");
   return std::make_pair(node, generation);
}


void
Camera::Enable1394B(bool flag)
{
   EnsureNotCapturing();

   if (flag && !Is1394BCapable())
      throw Error("The camera is not compatible with 1394B");
   dc1394operation_mode_t mode = flag ? DC1394_OPERATION_MODE_1394B : DC1394_OPERATION_MODE_LEGACY;
   dc1394error_t err;
   err = dc1394_video_set_operation_mode(libdc1394camera_, mode);
   if (err != DC1394_SUCCESS)
      throw Error(err, "Failed to set operation mode to " +
            std::string(flag ? "1394B" : "1394A"));
}


bool
Camera::Is1394BEnabled()
{
   dc1394operation_mode_t mode;
   dc1394error_t err;
   err = dc1394_video_get_operation_mode(libdc1394camera_, &mode);
   if (err != DC1394_SUCCESS)
      throw Error(err, "Failed to get 1394 bus operation mode (1394A/1394B)");
   return mode == DC1394_OPERATION_MODE_1394B;
}


void
Camera::SetIsoSpeed(unsigned nominalMbps)
{
   EnsureNotCapturing();

   if (!Is1394BEnabled() && nominalMbps > 400)
      throw Error("Cannot set 1394A isochronous transmission speed to greater than 400 Mbps");

   dc1394speed_t speed;
   switch (nominalMbps)
   {
      case 100: speed = DC1394_ISO_SPEED_100; break;
      case 200: speed = DC1394_ISO_SPEED_200; break;
      case 400: speed = DC1394_ISO_SPEED_400; break;
      case 800: speed = DC1394_ISO_SPEED_800; break;
      case 1600: speed = DC1394_ISO_SPEED_1600; break;
      case 3200: speed = DC1394_ISO_SPEED_3200; break;
      default:
          throw Error("Invalid isochronous transmission speed");
   }
   dc1394error_t err;
   err = dc1394_video_set_iso_speed(libdc1394camera_, speed);
   if (err != DC1394_SUCCESS)
      throw Error(err, "Failed to set bus transmission speed");
}


unsigned
Camera::GetIsoSpeed()
{
   dc1394speed_t speed;
   dc1394error_t err;
   err = dc1394_video_get_iso_speed(libdc1394camera_, &speed);
   if (err != DC1394_SUCCESS)
      throw Error(err, "Cannot get bus transmission speed");
   switch (speed)
   {
      case DC1394_ISO_SPEED_100: return 100;
      case DC1394_ISO_SPEED_200: return 200;
      case DC1394_ISO_SPEED_400: return 400;
      case DC1394_ISO_SPEED_800: return 800;
      case DC1394_ISO_SPEED_1600: return 1600;
      case DC1394_ISO_SPEED_3200: return 3200;
#if DC1394_ISO_SPEED_3200 < DC1394_ISO_SPEED_MAX
#   error switch statement case clauses need update for this libdc1394
#endif
   }
   throw Error("Unknown bus transmission speed");
}


std::vector< boost::shared_ptr<VideoMode> >
Camera::GetVideoModes()
{
   std::vector< boost::shared_ptr<VideoMode> > modeList;

   dc1394video_modes_t modes;
   dc1394error_t err;
   err = dc1394_video_get_supported_modes(libdc1394camera_, &modes);
   if (err != DC1394_SUCCESS)
      throw Error(err, "Cannot get list of supported video modes");

   for (unsigned i = 0; i < modes.num; ++i)
   {
      dc1394video_mode_t mode = modes.modes[i];
      if (dc1394_is_video_mode_still_image(mode))
         ; // Ignore (Format_6)
      else if (!dc1394_is_video_mode_scalable(mode))
         modeList.push_back(boost::make_shared<ConventionalVideoMode>(libdc1394camera_, mode));
      else // Format_7
      {
         dc1394color_codings_t codings;
         dc1394error_t err;
         err = dc1394_format7_get_color_codings(libdc1394camera_, mode, &codings);
         if (err != DC1394_SUCCESS)
            throw Error(err, "Cannot get list of supported color codings for format 7 mode " +
                  boost::lexical_cast<std::string>(mode - DC1394_VIDEO_MODE_FORMAT7_MIN));

         for (unsigned j = 0; j < codings.num; ++j)
         {
            dc1394color_coding_t coding = codings.codings[j];
            modeList.push_back(boost::make_shared<Format7VideoMode>(libdc1394camera_, mode, coding));
         }
      }
   }
   return modeList;
}


boost::shared_ptr<VideoMode>
Camera::GetVideoMode()
{
   dc1394video_mode_t mode;
   dc1394error_t err;
   err = dc1394_video_get_mode(libdc1394camera_, &mode);
   if (err != DC1394_SUCCESS)
      throw Error(err, "Cannot get current video mode");
   if (dc1394_is_video_mode_still_image(mode))
      throw Error("Camera in still image (Format_6) mode; unsupported");
   else if (!dc1394_is_video_mode_scalable(mode))
      return boost::make_shared<ConventionalVideoMode>(libdc1394camera_, mode);
   else // Format_7
   {
      dc1394color_coding_t coding;
      dc1394error_t err;
      err = dc1394_format7_get_color_coding(libdc1394camera_, mode, &coding);
      if (err != DC1394_SUCCESS)
         throw Error(err, "Cannot get current color coding for format 7 mode " +
               boost::lexical_cast<std::string>(mode - DC1394_VIDEO_MODE_FORMAT7_MIN));
      return boost::make_shared<Format7VideoMode>(libdc1394camera_, mode, coding);
   }
}


void
Camera::SetVideoMode(boost::shared_ptr<VideoMode> mode)
{
   dc1394video_mode_t libdc1394mode = mode->GetLibDC1394Mode();
   dc1394error_t err;
   err = dc1394_video_set_mode(libdc1394camera_, libdc1394mode);
   if (err != DC1394_SUCCESS)
      throw Error(err, "Cannot set video mode");
   if (mode->IsFormat7())
   {
      dc1394color_coding_t libdc1394coding = mode->GetLibDC1394Coding();
      dc1394error_t err;
      err = dc1394_format7_set_color_coding(libdc1394camera_, libdc1394mode, libdc1394coding);
      if (err != DC1394_SUCCESS)
         throw Error(err, "Cannot set color coding for format 7 mode " +
               boost::lexical_cast<std::string>(libdc1394mode - DC1394_VIDEO_MODE_FORMAT7_MIN));
   }
}


unsigned
Camera::GetBitsPerSample()
{
   boost::shared_ptr<VideoMode> mode = GetVideoMode();
   switch (mode->GetLibDC1394Coding())
   {
      case DC1394_COLOR_CODING_MONO8:
         return 8;
      case DC1394_COLOR_CODING_MONO16:
         {
            uint32_t depth;
            dc1394error_t err;
            err = dc1394_video_get_data_depth(libdc1394camera_, &depth);
            if (err != DC1394_SUCCESS)
               throw Error(err, "Cannot get bits per sample");
            if (!depth)
               return 16;
            return depth;
         }
      default:
         return 0;
   }
}


void
Camera::SetMaxFramerate(unsigned format7NegativeDeltaUnits)
{
   /*
    * Correct handling of framerates in IIDC is extremely complex. Make sure
    * you throughly understand the specification before modifying this
    * function.
    *
    * Note that this function does not consider the FRAME_RATE feature.
    */
   boost::shared_ptr<VideoMode> mode = GetVideoMode();
   if (mode->IsFormat7())
   {
      uint32_t busMaxPacketSize = 1024 * GetIsoSpeed() / 100;
      uint32_t packetSize;

      uint32_t unitPacketSize, maxPacketSize;
      dc1394error_t err;
      err = dc1394_format7_get_packet_parameters(libdc1394camera_, mode->GetLibDC1394Mode(),
            &unitPacketSize, &maxPacketSize);
      if (err != DC1394_SUCCESS)
         throw Error("Cannot get packet size parameters");

      uint32_t recommendedSize;
      err = dc1394_format7_get_recommended_packet_size(libdc1394camera_, mode->GetLibDC1394Mode(), &recommendedSize);
      if (err == DC1394_SUCCESS && recommendedSize > 0 && recommendedSize <= busMaxPacketSize)
      {
         packetSize = recommendedSize;
      }
      else
      {
         maxPacketSize = std::min(maxPacketSize, busMaxPacketSize);

         // Largest multiple of unitPacketSize that does not exceed maxPacketSize
         packetSize = maxPacketSize - (maxPacketSize % unitPacketSize);
      }

      /*
       * There are cases where corrupted images get returned at the maximum
       * packet size. I do not know if this is dependent on the camera, 1394
       * interface, driver/kernel, libdc1394 version, or some combination of
       * the above.
       * For example, the AVT Guppy PRO F031B exhibited this issue on OpenSUSE
       * 12.3, Rosewill RC-506E, 1394B S800, Format_7, Mode_0, Y16, full frame
       * (max packet size 8192, unit size 4); setting the packet size to 8188
       * resulted in correct images.
       */
      packetSize -= unitPacketSize * format7NegativeDeltaUnits;

      err = dc1394_format7_set_packet_size(libdc1394camera_, mode->GetLibDC1394Mode(), packetSize);
      if (err != DC1394_SUCCESS)
         throw Error("Cannot set IEEE 1394 packet size");
   }
   else
   {
      dc1394framerates_t framerates;
      dc1394error_t err;
      err = dc1394_video_get_supported_framerates(libdc1394camera_, mode->GetLibDC1394Mode(), &framerates);
      if (err != DC1394_SUCCESS)
         throw Error(err, "Cannot get list of supported framerates");

      // Of the framerates supported by the camera, keep only the ones that are
      // available under the current transmission speed.
      float maxFramerate = GetVideoModeMaxFramerateForIsoSpeed(mode->GetLibDC1394Mode(), GetIsoSpeed());
      std::set<dc1394framerate_t> availableFramerates;
      for (unsigned i = 0; i < framerates.num; ++i)
      {
         dc1394framerate_t framerate = framerates.framerates[i];
         float framerateFloat;
         dc1394error_t err;
         err = dc1394_framerate_as_float(framerate, &framerateFloat);
         if (err != DC1394_SUCCESS)
            throw Error(err, "Cannot convert supported framerate to float value");
         if (framerateFloat <= maxFramerate)
            availableFramerates.insert(framerate);
      }

      // Use the maximum available framerate.
      dc1394framerate_t framerate = *availableFramerates.rbegin();

      err = dc1394_video_set_framerate(libdc1394camera_, framerate);
      if (err != DC1394_SUCCESS)
         throw Error(err, "Cannot set framerate");
   }
}


float
Camera::GetFramerate()
{
   boost::shared_ptr<VideoMode> mode = GetVideoMode();
   if (mode->IsFormat7())
   {
      float interval; // Seconds
      dc1394error_t err;
      err = dc1394_format7_get_frame_interval(libdc1394camera_, mode->GetLibDC1394Mode(), &interval);
      if (err == DC1394_SUCCESS && interval > 0.0f)
         return 1.0f / interval;

      // Camera cannot report framerate; estimate it from packet payload size
      uint32_t packetSize;
      err = dc1394_format7_get_packet_size(libdc1394camera_, mode->GetLibDC1394Mode(), &packetSize);
      if (err != DC1394_SUCCESS)
         throw Error(err, "Cannot get IEEE 1394 packet size");

      uint32_t width, height;
      err = dc1394_format7_get_image_size(libdc1394camera_, mode->GetLibDC1394Mode(), &width, &height);
      if (err != DC1394_SUCCESS)
         throw Error(err, "Cannot get Format_7 image size");

      uint32_t bitsPerPixel;
      err = dc1394_get_color_coding_data_depth(mode->GetLibDC1394Coding(), &bitsPerPixel);
      if (err != DC1394_SUCCESS)
         throw Error(err, "Cannot get bits per pixel for color coding");

      // framerate = packetSize / (width * height * (bpp / 8) * 125e-6)
      return static_cast<float>(64000.0 * packetSize / (width * height * bitsPerPixel));
   }
   else
   {
      dc1394framerate_t framerateEnum;
      dc1394error_t err;
      err = dc1394_video_get_framerate(libdc1394camera_, &framerateEnum);
      if (err != DC1394_SUCCESS)
         throw Error(err, "Cannot get current framerate");

      float framerateFloat;
      err = dc1394_framerate_as_float(framerateEnum, &framerateFloat);
      if (err != DC1394_SUCCESS)
         throw Error(err, "Cannot convert supported framerate to float value");

      return framerateFloat;
   }
}


void
Camera::StartContinuousCapture(uint32_t nrDMABuffers, size_t nrFrames,
      unsigned firstFrameTimeoutMs, FrameCallbackFunction frameCallback)
{
   EnsureReadyForCapture();

   captureFrameCallback_ = frameCallback;
   boost::shared_ptr<Capture> capture =
      boost::make_shared<ContinuousCapture>(libdc1394camera_,
            nrDMABuffers, nrFrames, firstFrameTimeoutMs,
            boost::bind<void>(&Camera::HandleCapturedFrame, this, _1));
   RunCaptureInBackground(capture);
}


void
Camera::StartMultiShotCapture(uint32_t nrDMABuffers, uint16_t nrFrames,
      unsigned firstFrameTimeoutMs, FrameCallbackFunction frameCallback)
{
   EnsureReadyForCapture();

   if (!IsMultiShotCapable())
      throw Error("Multi-shot capture is not supported");

   captureFrameCallback_ = frameCallback;
   boost::shared_ptr<Capture> capture =
      boost::make_shared<MultiShotCapture>(libdc1394camera_,
            nrDMABuffers, nrFrames, firstFrameTimeoutMs,
            boost::bind<void>(&Camera::HandleCapturedFrame, this, _1));
   RunCaptureInBackground(capture);
}


void
Camera::StartOneShotCapture(uint32_t nrDMABuffers, unsigned timeoutMs,
      FrameCallbackFunction frameCallback)
{
   EnsureReadyForCapture();

   if (!IsOneShotCapable())
      throw Error("One-shot capture is not supported");

   captureFrameCallback_ = frameCallback;
   boost::shared_ptr<Capture> capture =
      boost::make_shared<OneShotCapture>(libdc1394camera_,
            nrDMABuffers, timeoutMs,
            boost::bind<void>(&Camera::HandleCapturedFrame, this, _1));
   RunCaptureInBackground(capture);
}


void
Camera::StopCapture()
{
   if (currentCapture_)
      currentCapture_->Stop();
   WaitForCapture();
}


void
Camera::WaitForCapture()
{
   // In newer Boost versions: if (captureFuture_.valid())
   if (captureFuture_.get_state() != boost::future_state::uninitialized)
   {
      try
      {
         captureFuture_.get();
      }
      catch (...)
      {
         captureFuture_ = boost::unique_future<void>();
         throw;
      }
      captureFuture_ = boost::unique_future<void>();
   }
}


bool
Camera::IsCapturing()
{
   return currentCapture_ && currentCapture_->IsRunning();
}


void
Camera::EnsureNotCapturing()
{
   if (IsCapturing())
      throw Error("Capture in progress");
}


void
Camera::EnsureReadyForCapture()
{
   if (IsCapturing())
      throw Error("Capture already in progress");
   WaitForCapture(); // In case client code failed to clean up previous capture.
}


void
Camera::RunCaptureInBackground(boost::shared_ptr<Capture> capture)
{
   currentCapture_ = capture;

   // Note: boost::packaged_task<void ()> in more recent versions of Boost.
   boost::packaged_task<void> captureTask(boost::bind(&Capture::Run, capture.get()));

   captureFuture_ = captureTask.get_future();

   boost::thread captureThread(boost::move(captureTask));
   captureThread.detach(); // No need to join thread as we can wait for the future.
}


// Called on background thread.
void
Camera::HandleCapturedFrame(dc1394video_frame_t* frame)
{
   PixelFormat pf = PixelFormatUnsupported;
   switch (frame->color_coding)
   {
      case DC1394_COLOR_CODING_MONO8:
         pf = PixelFormatGray8;
         break;
      case DC1394_COLOR_CODING_MONO16:
         pf = PixelFormatGray16;
         break;
      default:
         pf = PixelFormatUnsupported;
         break;
   }
   captureFrameCallback_(frame->image, frame->size[0], frame->size[1], pf);
}

} // namespace IIDC
