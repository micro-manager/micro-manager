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
