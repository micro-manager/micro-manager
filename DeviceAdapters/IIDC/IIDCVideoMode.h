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

#pragma once

#include "IIDCCamera.h"

#include <dc1394/dc1394.h>
#ifdef _MSC_VER
#undef restrict
#endif

#include <string>


namespace IIDC {

PixelFormat PixelFormatForLibDC1394ColorCoding(dc1394color_coding_t coding);


class VideoMode
{
   dc1394video_mode_t libdc1394mode_;
   dc1394color_coding_t libdc1394coding_;
   unsigned width_;
   unsigned height_;

protected:
   VideoMode(dc1394video_mode_t libdc1394mode) :
      libdc1394mode_(libdc1394mode)
   {}

   void SetImageSize(unsigned width, unsigned height)
   { width_ = width; height_ = height; }
   void SetLibDC1394Coding(dc1394color_coding_t coding)
   { libdc1394coding_ = coding; }

   virtual std::string GetColorCodingName() const;

public:
   virtual ~VideoMode() {}

   bool operator==(const VideoMode& rhs)
   { return (libdc1394mode_ == rhs.libdc1394mode_) && (libdc1394coding_ == rhs.libdc1394coding_); }
   bool operator!=(const VideoMode& rhs) { return !operator==(rhs); }

   virtual dc1394video_mode_t GetLibDC1394Mode() const { return libdc1394mode_; }
   virtual dc1394color_coding_t GetLibDC1394Coding() const { return libdc1394coding_; }

   virtual unsigned GetMaxWidth() const { return width_; }
   virtual unsigned GetMaxHeight() const { return height_; }
   virtual PixelFormat GetPixelFormat() const;
   virtual bool IsFormat7() const = 0;
   virtual bool IsSupported() const { return GetPixelFormat() != PixelFormatUnsupported; }
   virtual std::string ToString() const = 0;
};


class ConventionalVideoMode : public VideoMode
{
public:
   ConventionalVideoMode(dc1394camera_t* libdc1394camera, dc1394video_mode_t libdc1394mode);
   virtual bool IsFormat7() const { return false; }
   virtual std::string ToString() const;
};


class Format7VideoMode : public VideoMode
{
public:
   Format7VideoMode(dc1394camera_t* libdc1394camera,
         dc1394video_mode_t libdc1394mode, dc1394color_coding_t libdc1394coding);
   virtual bool IsFormat7() const { return true; }
   virtual std::string ToString() const;
};

} // namespace IIDC
