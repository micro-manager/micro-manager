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

#include <ostream>


namespace mm
{
namespace logging
{
namespace internal
{


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


inline void
MetadataFormatter::FormatLinePrefix(std::ostream& stream,
      const Metadata& metadata)
{
   // TODO Avoid the slow tellp()
   std::ostream::pos_type prefixStart = stream.tellp();
   WriteTimeToStream(stream, metadata.GetStampData().GetTimestamp());
   stream << " tid" << metadata.GetStampData().GetThreadId() << ' ';
   openBracketCol_ = static_cast<size_t>(stream.tellp() - prefixStart);
   stream << '[';
   std::ostream::pos_type bracketedStart = stream.tellp();
   stream << LevelString(metadata.GetEntryData().GetLevel()) << ',' <<
      metadata.GetLoggerData().GetComponentLabel();
   bracketedWidth_ = static_cast<size_t>(stream.tellp() - bracketedStart);
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
