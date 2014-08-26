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

#include <boost/function.hpp>
#include <boost/utility.hpp>

#include <sstream>
#include <string>


namespace mm
{
namespace logging
{
namespace internal
{


template <typename TEntryData>
class GenericLogger
{
   boost::function<void (TEntryData, const char*)> impl_;

public:
   typedef TEntryData EntryDataType;

   GenericLogger(boost::function<void (TEntryData, const char*)> f) :
      impl_(f)
   {}

   void operator()(TEntryData entryData, const char* message) const
   { impl_(entryData, message); }

   void operator()(TEntryData entryData, const std::string& message) const
   { impl_(entryData, message.c_str()); }
};


/**
 * Log an entry upon destruction.
 */
template <class TLogger>
class GenericLogStream : public std::ostringstream, boost::noncopyable
{
public:
   typedef typename TLogger::EntryDataType EntryDataType;

   const TLogger& logger_;
   EntryDataType level_;
   bool used_;

public:
   GenericLogStream(const TLogger& logger, EntryDataType level) :
      logger_(logger),
      level_(level),
      used_(false)
   {}

   // Supporting functions for the LOG_* macros. See the macro definitions.
   bool Used() const { return used_; }
   void MarkUsed() { used_ = true; }

   virtual ~GenericLogStream()
   {
      logger_(level_, str());
   }
};

} // namespace internal
} // namespace logging
} // namespace mm
