// COPYRIGHT:     University of California, San Francisco, 2014,
//                All Rights reserved
//
// LICENSE:       This file is distributed under the "Lesser GPL" (LGPL) license.
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
// AUTHOR:        Mark Tsuchida

#pragma once

#include <boost/date_time/posix_time/posix_time.hpp>

#include <cstring>
#include <ostream>
#include <string>


namespace mm
{
namespace logging
{
namespace internal
{


inline const char*
LevelString(LogLevel logLevel)
{
   switch (logLevel)
   {
      case LogLevelTrace: return "trc";
      case LogLevelDebug: return "dbg";
      case LogLevelInfo: return "IFO";
      case LogLevelWarning: return "WRN";
      case LogLevelError: return "ERR";
      case LogLevelFatal: return "FTL";
      default: return "???";
   }
}


class MetadataFormatter
{
   size_t openBracketCol_;
   size_t bracketedWidth_;

public:
   MetadataFormatter() : openBracketCol_(0), bracketedWidth_(0) {}

   // Format the line prefix for the first line of an entry
   void FormatLinePrefix(std::ostream& stream, const Metadata& metadata);

   // Format the line prefix for subsequent lines of an entry
   void FormatContinuationPrefix(std::ostream& stream) const;
};


inline void
MetadataFormatter::FormatLinePrefix(std::ostream& stream,
      const Metadata& metadata)
{
   std::string timeStr = boost::posix_time::to_iso_extended_string(
         metadata.GetStampData().GetTimestamp());
   std::ostringstream sstrm(timeStr, std::ios_base::ate);
   sstrm << " tid" << metadata.GetStampData().GetThreadId() << ' ';
   std::string timeTid = sstrm.str();
   openBracketCol_ = timeTid.size();

   stream << timeTid;

   stream << '[';

   const char* levelStr = LevelString(metadata.GetEntryData().GetLevel());
   const char* compLabel = metadata.GetLoggerData().GetComponentLabel();
   bracketedWidth_ = strlen(levelStr) + 1 + strlen(compLabel);
   stream << levelStr << ',' << compLabel;

   stream << ']';
}


inline void
MetadataFormatter::FormatContinuationPrefix(std::ostream& stream) const
{
   for (size_t i = 0; i < openBracketCol_; ++i)
      stream.put(' ');
   stream << '[';
   for (size_t i = 0; i < bracketedWidth_; ++i)
      stream.put(' ');
   stream << ']';
}


} // namespace internal
} // namespace logging
} // namespace mm
