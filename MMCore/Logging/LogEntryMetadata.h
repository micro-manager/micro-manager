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

namespace mm
{
namespace logging
{

enum LogLevel
{
   LogLevelTrace,
   LogLevelDebug,
   LogLevelInfo,
   LogLevelWarning,
   LogLevelError,
   LogLevelFatal,
};


namespace detail
{

// Consider this arrangement for obtaining the current time provisional. In the
// future (when Boost versions with Chrono are widely available, or we move to
// C++11), we should have a boost::chrono or std::chrono clock (e.g.
// steady_clock or high_resolution_clock) as the template argument, and use
// UClock::now() to get the time.

template <typename TTime>
TTime Now();

template <typename TThreadId>
TThreadId GetTid();

// Implementations provided by template specializations.


/**
 * Log entry metadata.
 *
 * Warning: For efficiency reasons (and lack of C++11), this internal data
 * structure is unsafe and behaves more like a C struct than a proper class.
 */
template <typename TTime, typename UThreadId>
class GenericLogEntryMetadata
{
public:
   typedef TTime TimeType;
   typedef UThreadId ThreadIdType;

private:
   // No dynamically allocated data.
   TimeType timestamp_;
   ThreadIdType threadId_;
   LogLevel level_;
   const char* componentLabel_;

public:
   // Since we don't have C++11 emplace, for now we construct without
   // initialization. Compiler-generated copy ctor and operator=() are fine.
   void Set(TimeType timestamp, ThreadIdType threadId,
         LogLevel level, const char* componentLabel)
   {
      timestamp_ = timestamp;
      threadId_ = threadId;
      level_ = level;
      componentLabel_ = componentLabel;
   }

   void Construct(LogLevel level, const char* componentLabel)
   {
      Set(detail::Now<TimeType>(), detail::GetTid<ThreadIdType>(),
            level, componentLabel);
   }

   TimeType GetTimeStamp() const { return timestamp_; }
   ThreadIdType GetThreadId() const { return threadId_; }
   LogLevel GetLogLevel() const { return level_; }
   const char* GetComponentLabel() const { return componentLabel_; }
};

} // namespace detail
} // namespace logging
} // namespace mm
