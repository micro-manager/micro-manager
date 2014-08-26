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

#include "GenericStreamSink.h"
#include "GenericEntryFilter.h"
#include "GenericLoggingCore.h"
#include "GenericSink.h"

#include "Logger.h"
#include "Metadata.h"
#include "MetadataFormatter.h"


namespace mm
{
namespace logging
{


typedef internal::GenericLoggingCore<Metadata> LoggingCore;

typedef internal::GenericSink<Metadata> LogSink;
typedef internal::GenericStdErrLogSink<Metadata, internal::MetadataFormatter>
   StdErrLogSink;
typedef internal::GenericFileLogSink<Metadata, internal::MetadataFormatter>
   FileLogSink;


typedef internal::GenericEntryFilter<Metadata> EntryFilter;

class LevelFilter : public EntryFilter
{
   LogLevel minLevel_;

public:
   LevelFilter(LogLevel minLevel) : minLevel_(minLevel) {}

   virtual bool Filter(const Metadata& metadata) const
   { return metadata.GetEntryData().GetLevel() >= minLevel_; }
};


} // namespace logging
} // namespace mm
