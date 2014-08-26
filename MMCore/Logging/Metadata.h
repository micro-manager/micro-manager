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

#include "GenericMetadata.h"

#ifdef _WIN32
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#else
#include <pthread.h>
#endif

#include <boost/date_time/posix_time/posix_time_types.hpp>


namespace mm
{
namespace logging
{

namespace internal
{


typedef boost::posix_time::ptime TimestampType;

// Note: Boost's local_time() internally calls the C library function
// localtime_r() or localtime(). On the platforms we are interested in, either
// the thread-safe localtime_r() is provided (OS X, Linux), or localtime() is
// made thread-safe by using thread-local storage (Windows).
inline TimestampType
Now()
{ return boost::posix_time::microsec_clock::local_time(); }


#ifdef _WIN32
typedef DWORD ThreadIdType;

inline ThreadIdType
GetTid() { return ::GetCurrentThreadId(); }
#else
typedef pthread_t ThreadIdType;

inline ThreadIdType
GetTid() { return ::pthread_self(); }
#endif


} // namespace internal


enum LogLevel
{
   LogLevelTrace,
   LogLevelDebug,
   LogLevelInfo,
   LogLevelWarning,
   LogLevelError,
   LogLevelFatal,
};


class EntryData
{
   LogLevel level_;

public:
   // Implicitly construct from LogLevel
   EntryData(LogLevel level) : level_(level) {}

   LogLevel GetLevel() const { return level_; }
};


class StampData
{
   internal::TimestampType time_;
   internal::ThreadIdType tid_;

public:
   void Stamp()
   {
      time_ = internal::Now();
      tid_ = internal::GetTid();
   }

   internal::TimestampType GetTimestamp() const { return time_; }
   internal::ThreadIdType GetThreadId() const { return tid_; }
};


class LoggerData
{
   const char* component_;

public:
   // Construct implicitly from strings
   LoggerData(const char* componentLabel) :
      component_(InternString(componentLabel))
   {}
   LoggerData(const std::string& componentLabel) :
      component_(InternString(componentLabel))
   {}

   const char* GetComponentLabel() const { return component_; }

private:
   static const char* InternString(const std::string& s);
};


typedef internal::GenericMetadata<LoggerData, EntryData, StampData> Metadata;


} // namespace logging
} // namespace mm
