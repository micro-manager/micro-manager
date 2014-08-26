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


// A stateful formatter for the metadata prefix and corresponding
// continuation-line prefix. Intended for single-threaded use only.
class MetadataFormatter
{
   // Reuse buffers for efficiency
   std::string buf_;
   std::ostringstream sstrm_;
   size_t openBracketCol_;
   size_t closeBracketCol_;

public:
   MetadataFormatter() : openBracketCol_(0), closeBracketCol_(0) {}

   // Format the line prefix for the first line of an entry
   void FormatLinePrefix(std::ostream& stream, const Metadata& metadata);

   // Format the line prefix for subsequent lines of an entry
   void FormatContinuationPrefix(std::ostream& stream);
};


inline void
MetadataFormatter::FormatLinePrefix(std::ostream& stream,
      const Metadata& metadata)
{
   // Pre-forming string is more efficient than writing bit by bit to stream.

   buf_ = boost::posix_time::to_iso_extended_string(
         metadata.GetStampData().GetTimestamp());
   buf_ += " tid";
   sstrm_.str(std::string());
   sstrm_ << metadata.GetStampData().GetThreadId();
   buf_ += sstrm_.str();
   buf_ += ' ';

   openBracketCol_ = buf_.size();
   buf_ += '[';

   buf_ += LevelString(metadata.GetEntryData().GetLevel());
   buf_ += ',';
   buf_ += metadata.GetLoggerData().GetComponentLabel();

   closeBracketCol_ = buf_.size();
   buf_ += ']';

   stream << buf_;
}


inline void
MetadataFormatter::FormatContinuationPrefix(std::ostream& stream)
{
   buf_.assign(closeBracketCol_ + 1, ' ');
   buf_[openBracketCol_] = '[';
   buf_[closeBracketCol_] = ']';
   stream << buf_;
}


} // namespace internal
} // namespace logging
} // namespace mm
