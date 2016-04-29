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

#include "IIDCUtils.h"

#include "IIDCError.h"

#include <boost/format.hpp>

#include <sstream>


namespace IIDC {
namespace detail {

std::string
CameraIdToString(const dc1394camera_id_t* id)
{
   std::ostringstream strm;
   strm << (boost::format("%016x") % id->guid) << "-" << id->unit;
   return strm.str();
}


void
StringToCameraId(const std::string& idString, dc1394camera_id_t* id)
{
   size_t dashPos = idString.find('-');
   if (dashPos == std::string::npos)
      throw Error("Invalid camera id");
   try
   {
      std::istringstream guidSS(idString.substr(0, dashPos));
      guidSS >> std::hex >> id->guid;

      id->unit = boost::lexical_cast<uint16_t>(idString.substr(dashPos + 1));
   }
   catch (const boost::bad_lexical_cast&)
   {
      throw Error("Invalid camera id");
   }
}


float
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

} // namespace detail
} // namespace IIDC
