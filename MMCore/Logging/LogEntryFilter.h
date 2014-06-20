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

#include "LogEntryMetadata.h"


namespace mm
{
namespace logging
{
namespace detail
{

template <typename TThreadId>
class GenericLogEntryFilter
{
public:
   virtual ~GenericLogEntryFilter() {}

   virtual bool Filter(TThreadId tid, LogLevel level,
         const char* componentLabel) const = 0;
};


template <typename TThreadId>
class GenericLevelFilter : public GenericLogEntryFilter<TThreadId>
{
   LogLevel minLevel_;

public:
   GenericLevelFilter(LogLevel minLevel) :
      minLevel_(minLevel)
   {}

   virtual bool Filter(TThreadId, LogLevel level, const char*) const
   { return level >= minLevel_; }
};

} // namespace detail
} // namespace logging
} // namespace mm
