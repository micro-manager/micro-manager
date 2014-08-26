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
#include "LogEntryMetadata.h"


namespace mm
{
namespace logging
{
namespace detail
{

template <typename TMetadata>
class LogEntryFilter
{
public:
   virtual ~LogEntryFilter() {}
   virtual bool Filter(const TMetadata& metadata) const = 0;
};


class LevelFilter :
   public LogEntryFilter<
      GenericMetadata<DefaultLoggerData, DefaultEntryData, DefaultStampData>
   >
{
   LogLevel minLevel_;

public:
   LevelFilter(LogLevel minLevel) : minLevel_(minLevel) {}

   virtual bool Filter(const GenericMetadata<DefaultLoggerData,
         DefaultEntryData, DefaultStampData>& metadata) const
   { return metadata.entryData_.GetLevel() >= minLevel_; }
};

} // namespace detail
} // namespace logging
} // namespace mm
