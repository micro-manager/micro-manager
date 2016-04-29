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

#include "IIDCVideoMode.h"

#include "IIDCError.h"

#include <boost/lexical_cast.hpp>


namespace IIDC {

PixelFormat
PixelFormatForLibDC1394ColorCoding(dc1394color_coding_t coding)
{
   switch (coding)
   {
      case DC1394_COLOR_CODING_MONO8: return PixelFormatGray8;
      case DC1394_COLOR_CODING_MONO16: return PixelFormatGray16;
      case DC1394_COLOR_CODING_YUV444: return PixelFormatYUV444;
      case DC1394_COLOR_CODING_YUV422: return PixelFormatYUV422;
      case DC1394_COLOR_CODING_YUV411: return PixelFormatYUV411;
      case DC1394_COLOR_CODING_RGB8: return PixelFormatRGB8;
      case DC1394_COLOR_CODING_RAW8: return PixelFormatRaw8;
      case DC1394_COLOR_CODING_RAW16: return PixelFormatRaw16;
      default: return PixelFormatUnsupported;
   }
}


std::string
VideoMode::GetColorCodingName() const
{
   switch (GetLibDC1394Coding())
   {
      case DC1394_COLOR_CODING_MONO8: return "Mono8";
      case DC1394_COLOR_CODING_MONO16: return "Mono16";
      case DC1394_COLOR_CODING_YUV444: return "YUV444";
      case DC1394_COLOR_CODING_YUV422: return "YUV422";
      case DC1394_COLOR_CODING_YUV411: return "YUV411";
      case DC1394_COLOR_CODING_RGB8: return "RGB8";
      case DC1394_COLOR_CODING_RGB16: return "RGB16";
      case DC1394_COLOR_CODING_RAW8: return "Raw8";
      case DC1394_COLOR_CODING_RAW16: return "Raw16";
      default: return "(unsupported)";
   }
}


PixelFormat
VideoMode::GetPixelFormat() const
{
   return PixelFormatForLibDC1394ColorCoding(libdc1394coding_);
}


ConventionalVideoMode::ConventionalVideoMode(dc1394camera_t* libdc1394camera,
      dc1394video_mode_t libdc1394mode) :
   VideoMode(libdc1394mode)
{
   uint32_t width, height;
   dc1394error_t err;
   err = dc1394_get_image_size_from_video_mode(libdc1394camera, libdc1394mode, &width, &height);
   if (err != DC1394_SUCCESS)
      throw Error(err, "Cannot get image size from video mode");
   SetImageSize(width, height);

   dc1394color_coding_t libdc1394coding;
   err = dc1394_get_color_coding_from_video_mode(libdc1394camera, libdc1394mode, &libdc1394coding);
   if (err != DC1394_SUCCESS)
      throw Error(err, "Cannot get color coding from video mode");
   SetLibDC1394Coding(libdc1394coding);
}


Format7VideoMode::Format7VideoMode(dc1394camera_t* libdc1394camera,
      dc1394video_mode_t libdc1394mode, dc1394color_coding_t libdc1394coding) :
   VideoMode(libdc1394mode)
{
   uint32_t width, height;
   dc1394error_t err;
   err = dc1394_format7_get_max_image_size(libdc1394camera, libdc1394mode, &width, &height);
   if (err != DC1394_SUCCESS)
      throw Error(err, "Cannot get maximum image size for format 7 video mode");
   SetImageSize(width, height);

   SetLibDC1394Coding(libdc1394coding);
}


std::string
ConventionalVideoMode::ToString() const
{
   dc1394video_mode_t libdc1394mode = GetLibDC1394Mode();
   int formatNr = -1, modeNr = -1;
   if (libdc1394mode >= DC1394_VIDEO_MODE_160x120_YUV444 &&
         libdc1394mode <= DC1394_VIDEO_MODE_640x480_MONO16)
   {
      formatNr = 0;
      modeNr = libdc1394mode - DC1394_VIDEO_MODE_160x120_YUV444;
   }
   else if (libdc1394mode >= DC1394_VIDEO_MODE_800x600_YUV422 &&
         libdc1394mode <= DC1394_VIDEO_MODE_1024x768_MONO16)
   {
      formatNr = 1;
      modeNr = libdc1394mode - DC1394_VIDEO_MODE_800x600_YUV422;
   }
   else if (libdc1394mode >= DC1394_VIDEO_MODE_1280x960_YUV422 &&
         libdc1394mode <= DC1394_VIDEO_MODE_1600x1200_MONO16)
   {
      formatNr = 2;
      modeNr = libdc1394mode - DC1394_VIDEO_MODE_1280x960_YUV422;
   }
   std::string formatModeStr = (formatNr >= 0 && modeNr >= 0) ?
      "[f" + boost::lexical_cast<std::string>(formatNr) + "-m" +
      boost::lexical_cast<std::string>(modeNr) + "]" :
      "[?-?]";

   return formatModeStr + " " +
      boost::lexical_cast<std::string>(GetMaxWidth()) + "x" +
      boost::lexical_cast<std::string>(GetMaxHeight()) + " " +
      GetColorCodingName();
}


std::string
Format7VideoMode::ToString() const
{
   int modeNr = static_cast<int>(GetLibDC1394Mode()) - DC1394_VIDEO_MODE_FORMAT7_MIN;

   return "[f7-m" + boost::lexical_cast<std::string>(modeNr) + "] " +
      boost::lexical_cast<std::string>(GetMaxWidth()) + "x" +
      boost::lexical_cast<std::string>(GetMaxHeight()) + " " +
      GetColorCodingName();
}

} // namespace
