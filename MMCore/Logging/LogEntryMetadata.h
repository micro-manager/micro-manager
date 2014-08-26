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

/**
 * Log entry metadata.
 *
 * Warning: For efficiency reasons (and lack of C++11), this internal data
 * structure is unsafe and behaves more like a C struct than a proper class.
 */
class LogEntryMetadata
{
private:
   // No dynamically allocated data.
   TimestampType timestamp_;
   ThreadIdType threadId_;
   LogLevel level_;
   const char* componentLabel_;

public:
   // Since we don't have C++11 emplace, for now we construct without
   // initialization. Then we can "emplace" using the placement new operator
   // with the argument-taking constructor below.
   LogEntryMetadata() {} // Leave uninitialized (!)

   LogEntryMetadata(LogLevel level, const char* componentLabel) :
      timestamp_(Now()),
      threadId_(GetTid()),
      level_(level),
      componentLabel_(componentLabel)
   {}

   // Compiler-generated copy ctor and operator=() are fine.
   // N.B. Default constructor will leave object uninitialized.

   TimestampType GetTimestamp() const { return timestamp_; }
   ThreadIdType GetThreadId() const { return threadId_; }
   LogLevel GetLogLevel() const { return level_; }
   const char* GetComponentLabel() const { return componentLabel_; }
};

} // namespace detail
} // namespace logging
} // namespace mm
