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

#include "GenericLogger.h"
#include "Metadata.h"


namespace mm
{
namespace logging
{

typedef internal::GenericLogger<EntryData> Logger;
typedef internal::GenericLogStream<Logger> LogStream;

} // namespace logging
} // namespace mm


// Shorthands for LogStream
//
// Usage:
//
//     LOG_INFO(myLogger) << x << y << z;

// You might think that we don't need the following macros, because we could
// just write
//
//     LogStream(myLogger, someLevel) << x << y << z;
//
// However, that would only work with C++11, where the standard operator<<()
// implementations include overloads for rvalue references (basic_ostream&&).
// In C++ pre-11, the above statement will fail for some data types of x (e.g.
// const char*). So, to make the left hand side of << an lvalue, we need to use
// a trick.

#define LOG_WITH_LEVEL(logger, level) \
   for (::mm::logging::LogStream strm((logger), (level)); \
         !strm.Used(); strm.MarkUsed()) \
      strm

#define LOG_TRACE(logger) LOG_WITH_LEVEL((logger), ::mm::logging::LogLevelTrace)
#define LOG_DEBUG(logger) LOG_WITH_LEVEL((logger), ::mm::logging::LogLevelDebug)
#define LOG_INFO(logger) LOG_WITH_LEVEL((logger), ::mm::logging::LogLevelInfo)
#define LOG_WARNING(logger) LOG_WITH_LEVEL((logger), ::mm::logging::LogLevelWarning)
#define LOG_ERROR(logger) LOG_WITH_LEVEL((logger), ::mm::logging::LogLevelError)
#define LOG_FATAL(logger) LOG_WITH_LEVEL((logger), ::mm::logging::LogLevelFatal)
