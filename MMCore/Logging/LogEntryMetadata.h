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

#include "LoggingDefs.h"

#include <boost/thread.hpp>

#include <set>


namespace mm
{
namespace logging
{

namespace detail
{

template <
   typename TLoggerData,
   typename UEntryData,
   typename VStampData
>
struct GenericMetadata
{
   typedef TLoggerData LoggerDataType;
   typedef UEntryData EntryDataType;
   typedef VStampData StampDataType;

   LoggerDataType loggerData_;
   EntryDataType entryData_;
   StampDataType stampData_;

   GenericMetadata(LoggerDataType loggerData, EntryDataType entryData,
         StampDataType stampData) :
      loggerData_(loggerData),
      entryData_(entryData),
      stampData_(stampData)
   {}
};

} // namespace detail


enum LogLevel
{
   LogLevelTrace,
   LogLevelDebug,
   LogLevelInfo,
   LogLevelWarning,
   LogLevelError,
   LogLevelFatal,
};


class DefaultEntryData
{
   LogLevel level_;

public:
   // Implicitly construct from LogLevel
   DefaultEntryData(LogLevel level) : level_(level) {}

   LogLevel GetLevel() const { return level_; }
};


class DefaultStampData
{
   detail::TimestampType time_;
   detail::ThreadIdType tid_;

public:
   void Stamp()
   {
      time_ = detail::Now();
      tid_ = detail::GetTid();
   }

   detail::TimestampType GetTimestamp() const { return time_; }
   detail::ThreadIdType GetThreadId() const { return tid_; }
};


class DefaultLoggerData
{
   const char* component_;

public:
   // Construct implicitly from strings
   DefaultLoggerData(const char* componentLabel) :
      component_(InternString(componentLabel))
   {}
   DefaultLoggerData(const std::string& componentLabel) :
      component_(InternString(componentLabel))
   {}

   const char* GetComponentLabel() const { return component_; }

private:
   static const char* InternString(const std::string& s)
   {
      // Never remove strings from this set. Since we only ever insert into
      // this set, iterators (and thus const char* to the contained strings)
      // are never invalidated and can be used as a light-weight handle. Thus,
      // we need to protect only insertion by a mutex.
      static boost::mutex mutex;
      static std::set<std::string> strings;

      boost::lock_guard<boost::mutex> lock(mutex);
      return strings.insert(s).first->c_str();
   }
};


} // namespace logging
} // namespace mm
