// Micro-Manager IIDC Device Adapter
//
// AUTHOR:        Mark A. Tsuchida
//
// COPYRIGHT:     University of California, San Francisco, 2015
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

#include <dc1394/dc1394.h>
#ifdef _MSC_VER
#undef restrict
#endif


namespace IIDC {

inline void
ConvertToRGB8(uint8_t* dst, const uint8_t* src, size_t width, size_t height,
      PixelFormat format)
{
   dc1394color_coding_t coding;
   switch (format)
   {
      case PixelFormatYUV444:
         coding = DC1394_COLOR_CODING_YUV444;
         break;
      case PixelFormatYUV422:
         coding = DC1394_COLOR_CODING_YUV422;
         break;
      case PixelFormatYUV411:
         coding = DC1394_COLOR_CODING_YUV411;
         break;
      default:
         return;
   }
   dc1394_convert_to_RGB8(const_cast<uint8_t*>(src), dst, width, height,
         DC1394_BYTE_ORDER_UYVY, coding, 8);
}

} // namespace IIDC
