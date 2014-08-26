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

#include <boost/shared_ptr.hpp>
#include <boost/utility.hpp>

#include <sstream>
#include <string>


namespace mm
{
namespace logging
{
namespace detail
{

/**
 * Input port to the logger.
 *
 * Instances are obtained from LoggingCore.
 */
template <typename TLoggingCore>
class GenericLogger
{
   boost::shared_ptr<TLoggingCore> loggingCore_;
   const char* componentLabel_;

public:
   GenericLogger(boost::shared_ptr<TLoggingCore> core,
         const std::string& componentLabel) :
      loggingCore_(core),
      componentLabel_(core->RegisterComponentLabel(componentLabel))
   {}

   void Log(LogLevel level, const char* text)
   {
      // Metadata is constructed at the earliest possible opportunity, so that
      // the timestamp is accurate and so that we don't have to pass around
      // multiple parameters.
      typename TLoggingCore::MetadataType metadata(level, componentLabel_);

      loggingCore_->LogEntry(metadata, text);
   }
};

/**
 * Log an entry upon destruction.
 */
template <typename TLogger>
class GenericLogStream : public std::ostringstream, boost::noncopyable
{
   boost::shared_ptr<TLogger> logger_;
   LogLevel level_;
   bool used_;

public:
   GenericLogStream(boost::shared_ptr<TLogger> logger, LogLevel level) :
      logger_(logger),
      level_(level),
      used_(false)
   {}

   // Supporting functions for the LOG_* macros. See the macro definitions.
   bool Used() const { return used_; }
   void MarkUsed() { used_ = true; }

   virtual ~GenericLogStream()
   {
      logger_->Log(level_, str().c_str());
   }
};

} // namespace detail
} // namespace logging
} // namespace mm
