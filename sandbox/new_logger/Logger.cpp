///////////////////////////////////////////////////////////////////////////////
// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCore
//-----------------------------------------------------------------------------
// DESCRIPTION:   Logger
//
// COPYRIGHT:     University of California, San Francisco, 2013.
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

#include "Logger.h"

#include <boost/lexical_cast.hpp>
#include <boost/scoped_array.hpp>

#include <string>

#include <cstdio>

#ifdef _MSC_VER
#  pragma warning(disable: 4714) // BOOST_LOG_SEV()
#endif


// Stream-style logging for logging-related errors.
#define LOGGING_LOG(_level) if (errorLogger_) BOOST_LOG_SEV(*errorLogger_, (_level))


template <typename TStream>
inline void WriteToStreamWithCorrectNewlines(TStream& stream,
      const std::string& text, const char* continuationPrefix)
{
   std::string::size_type chunkBegin = 0;
   for (;;)
   {
      std::string::size_type chunkEnd =
         text.find_first_of("\r\n", chunkBegin);

      if (chunkEnd == std::string::npos)
      {
         // End of text, with no trailing newline
         stream << text.substr(chunkBegin);
         break;
      }

      stream << text.substr(chunkBegin, chunkEnd - chunkBegin);

      // Skip over a single LF or CRLF (or CR)
      chunkBegin = chunkEnd + 1;
      if (text[chunkEnd] == '\r' && text[chunkBegin] == '\n')
         ++chunkBegin;
      if (text[chunkBegin] == '\0')
      {
         // End of text at trailing newline, which we leave out. (If there are
         // multiple trailing newlines, all but the last will be printed.)
         //
         // At some point, it would make a lot more sense to always print
         // trailing newlines and require the caller to do a better job of
         // formatting their log messages.
         break;
      }

      stream << '\n' << continuationPrefix;
   }
}


void
Logger::Log(Level level, const std::string& message)
{
   boost::log::record record =
      boostLogger_->open_record(boost::log::keywords::severity = level);
   if (record)
   {
      boost::log::record_ostream stream(record);

      WriteToStreamWithCorrectNewlines(stream, message, "    | ");
      stream.flush();

      boostLogger_->push_record(boost::move(record));
   }
}


void
Logger::LogF(Level level, const char* format, ...)
{
   va_list ap;
   va_start(ap, format);
   VLogF(level, format, ap);
   va_end(ap);
}


void
Logger::VLogF(Level level, const char* format, va_list ap)
{
   // We avoid dynamic allocation in the vast majority of cases.
   const size_t smallBufSize = 1024;
   char smallBuf[smallBufSize];
   int n = vsnprintf(smallBuf, smallBufSize, format, ap);
   if (n >= 0 && n < smallBufSize)
   {
      Log(level, smallBuf);
      return;
   }

   // Okay, now we deal with some nastiness due to the non-standard
   // implementation of Microsoft's vsnprintf() (vsnprintf_s() is no better).

#ifdef _MSC_VER
   // With Microsoft's C Runtime, n is always -1 (whether error or overflow).
   // Try a fixed-size buffer and give up if it is not large enough.
   const size_t bigBufSize = 65536;
#else
   // With C99/C++11 compliant vsnprintf(), n is -1 if error but on overflow it
   // is the required string length.
   if (n < 0)
   {
      LOGGING_LOG(Logging::LevelError) <<
         "Error in vsnprintf() while formatting "
         "log entry [" << level << ":" << channel_ << "] "
         "with format string \"" << format << "\"";
      return;
   }
   size_t bigBufSize = n + 1;
#endif

   boost::scoped_array<char> bigBuf(new char[bigBufSize]);
   if (!bigBuf)
   {
      LOGGING_LOG(Logging::LevelError) <<
         "Could not allocate " << (bigBufSize / 1024) << " kilobytes to format "
         "log entry [" << level << ":" << channel_ << "] "
         "with format string \"" << format << "\"";
      return;
   }

   n = vsnprintf(bigBuf.get(), bigBufSize, format, ap);
   if (n >= 0 && n < bigBufSize)
   {
      Log(level, bigBuf.get());
      return;
   }

#ifdef _MSC_VER
   LOGGING_LOG(Logging::LevelError) <<
      "Error or overflow in vsnprintf_s() "
      "(buffer size " << (bigBufSize / 1024) << " kilobytes) "
      "while formatting "
      "log entry [" << level << ":" << channel_ << "] "
      "with format string \"" << format << "\"";
#else
   LOGGING_LOG(Logging::LevelError) <<
      "Error in vsnprintf() while formatting "
      "log entry [" << level << ":" << channel_ << "] "
      "with format string \"" << format << "\"";
#endif
}


#define DEFINE_LOGF_FUNC(_level) \
   void Logger::Log##_level##F(const char* format, ...) \
   { \
      va_list ap; \
      va_start(ap, format); \
      VLogF(Logging::Level##_level, format, ap); \
      va_end(ap); \
   }

DEFINE_LOGF_FUNC(Trace)
DEFINE_LOGF_FUNC(Debug)
DEFINE_LOGF_FUNC(Info)
DEFINE_LOGF_FUNC(Warning)
DEFINE_LOGF_FUNC(Error)
DEFINE_LOGF_FUNC(Fatal)
