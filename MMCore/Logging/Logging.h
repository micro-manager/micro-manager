#pragma once

#include "LogEntryFilter.h"
#include "LogEntryMetadata.h"
#include "LogSink.h"
#include "Logger.h"
#include "LoggingCore.h"

#include <boost/date_time/posix_time/posix_time_types.hpp>

#ifdef _WIN32
#include <windows.h>
#else
#include <pthread.h>
#endif

namespace mm
{
namespace logging
{

namespace detail
{

// Platform-dependent types

// Note: Boost's local_time() internally calls the C library function
// localtime_r() or localtime(). On the platforms we are interested in, either
// the thread-safe localtime_r() is provided (OS X, Linux), or localtime() is
// made thread-safe by using thread-local storage (Windows).
template <>
inline boost::posix_time::ptime
Now<boost::posix_time::ptime>()
{ return boost::posix_time::microsec_clock::local_time(); }

#ifdef _WIN32
template <>
inline DWORD
GetTid<DWORD>() { return GetCurrentThreadId(); }
#else
template <>
inline pthread_t
GetTid<pthread_t>() { return pthread_self(); }
#endif

template <>
inline void
WriteTimeToStream<boost::posix_time::ptime>(std::ostream& stream,
      boost::posix_time::ptime timestamp)
{ stream << boost::posix_time::to_iso_extended_string(timestamp); }


typedef boost::posix_time::ptime PlatformTimestampType;

#ifdef _WIN32
typedef DWORD PlatformThreadIdType;
#else
typedef pthread_t PlatformThreadIdType;
#endif

typedef GenericLogEntryMetadata<PlatformTimestampType, PlatformThreadIdType>
   LogEntryMetadata;
typedef GenericLogLine<LogEntryMetadata> LogLine;

} // namespace detail


typedef detail::GenericLogEntryFilter<detail::PlatformThreadIdType>
   LogEntryFilter;
typedef detail::GenericLevelFilter<detail::PlatformThreadIdType> LevelFilter;

typedef detail::GenericLogSink<detail::LogLine> LogSink;
typedef detail::GenericStdErrLogSink<detail::LogLine> StdErrLogSink;
typedef detail::GenericFileLogSink<detail::LogLine> FileLogSink;

typedef detail::GenericLoggingCore<detail::PlatformTimestampType,
        detail::PlatformThreadIdType>
  LoggingCore;
typedef LoggingCore::LoggerType Logger;
typedef detail::GenericLogStream<Logger> LogStream;

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

} // namespace logging
} // namespace mm
