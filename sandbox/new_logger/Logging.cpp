///////////////////////////////////////////////////////////////////////////////
// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCore
//-----------------------------------------------------------------------------
// DESCRIPTION:   Logging facilities
//
// COPYRIGHT:     University of California, San Francisco, 2013.
//
// LICENSE:       This library is free software; you can redistribute it and/or
//                modify it under the terms of the GNU Lesser General Public
//                License as published by the Free Software Foundation.
//
//                You should have received a copy of the GNU Lesser General Public
//                License along with the source distribution; if not, write to
//                the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
//                Boston, MA  02111-1307  USA
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

#include "Logging.h"

#include "Logger.h"

#include <boost/date_time/posix_time/time_formatters.hpp>
#include <boost/date_time/posix_time/posix_time_types.hpp>
#include <boost/filesystem/path.hpp>
#include <boost/log/attributes/clock.hpp>
#include <boost/log/attributes/function.hpp>
#include <boost/log/expressions.hpp>
#include <boost/log/support/date_time.hpp>
#include <boost/utility/empty_deleter.hpp>
#include <boost/make_shared.hpp>

#include <ostream>


// Includes and defs for proccess and thread ids.
#ifdef WIN32
#  include <windows.h>
   typedef DWORD ProcessId;
   typedef DWORD ThreadId;
#else
#  include <pthread.h>
#  include <sys/types.h>
#  include <unistd.h>
   typedef pid_t ProcessId;
   typedef pthread_t ThreadId;
#endif


#ifdef _MSC_VER
#  pragma warning(disable: 4503) // Boost.Log formatting expressions
#  pragma warning(disable: 4714) // BOOST_LOG_SEV()
#endif


// Stream-style logging within the Logging class.
#define LOGGING_LOG(_level) if (loggingLogger_) BOOST_LOG_SEV(*loggingLogger_, (_level))


// Return the process id.
static inline ProcessId
GetPID()
{
#ifdef WIN32
   return GetCurrentProcessId();
#else
   return getpid();
#endif
}


// Return the current thread id in the same format as used in JVM crash logs.
static inline ThreadId
GetTID()
{
#ifdef WIN32
   return GetCurrentThreadId();
#else
   // TODO Is the the thread id used in HotSpot crash logs on Linux?
   // (On OS X it is, even though pthread_mach_thread_np(pthread_self())
   // returns a 'nicer' id.)
   return pthread_self();
#endif
}


std::ostream&
operator<<(std::ostream& os, Logging::Level level)
{
   switch (level)
   {
      case Logging::LevelTrace:   os << "trc"; break;
      case Logging::LevelDebug:   os << "dbg"; break;
      case Logging::LevelInfo:    os << "IFO"; break;
      case Logging::LevelWarning: os << "WRN"; break;
      case Logging::LevelError:   os << "ERR"; break;
      case Logging::LevelFatal:   os << "FTL"; break;
      default: os << "???"; break;
   }
   return os;
}


static void
SetUpLogAttributes()
{
   boost::shared_ptr<boost::log::core> blc = boost::log::core::get();

   blc->add_global_attribute("TimeStamp", boost::log::attributes::local_clock());

   // Avoid boost::log::attributes::current_thread_id; we want our own
   // defenition of the thread id (to match JVM and/or native crash logs).
   blc->add_global_attribute("ThreadID", boost::log::attributes::make_function(&GetTID));
}


template <typename TSink>
static void
SetUpFormatter(boost::shared_ptr<TSink> sink)
{
   namespace expr = boost::log::expressions;

   sink->set_formatter
   (
      expr::stream <<
         expr::format_date_time<boost::posix_time::ptime>("TimeStamp", "%Y-%m-%d %H:%M:%S.%f") <<
         " t:" << expr::attr<ThreadId>("ThreadID") <<
         " [" << expr::attr<Logging::Level>("Severity") << "]"
         "[" << expr::attr<std::string>("Channel") << "] " <<
         expr::smessage
   );
}


static inline std::string
MakeFilename(const std::string& prefix)
{
   boost::posix_time::ptime now = boost::posix_time::second_clock::local_time();
   std::string nowString = boost::posix_time::to_iso_string(now);
   ProcessId pid = GetPID();
   return prefix + nowString + "-pid" + boost::lexical_cast<std::string>(pid) + ".txt";
}


static void
SetUpFileLogging(const std::string& filename)
{
   boost::shared_ptr<boost::log::core> blc = boost::log::core::get();

   typedef boost::log::sinks::text_file_backend FileBackend;
   typedef boost::log::sinks::synchronous_sink<FileBackend> FileSink;

   boost::shared_ptr<FileBackend> backend = boost::make_shared<FileBackend>();
   backend->set_file_name_pattern(filename);
   backend->auto_flush(true); // This does not appear to impact performance

   boost::shared_ptr<FileSink> sink = boost::make_shared<FileSink>(backend);
   SetUpFormatter(sink);

   blc->add_sink(sink);
}


Logging::Logging(const std::string& logDirectory,
      const std::string& filenamePrefix,
      Level initialLevel,
      bool stderrLoggingInitiallyEnabled) :
   logDirectory_(logDirectory),
   logFileNamePrefix_(filenamePrefix)
{
   SetUpLogAttributes();

   SetLogLevel(initialLevel);
   SetStderrLoggingEnabled(stderrLoggingInitiallyEnabled);

   const std::string filename = MakeFilename(filenamePrefix);
   boost::filesystem::path path(logDirectory);
   path /= filename;
   path.make_preferred();
   logFilePath_ = path.string();
   SetUpFileLogging(logFilePath_);

   loggingLogger_ = boost::make_shared<Source>(boost::log::keywords::channel = "Logging");

   LOGGING_LOG(LevelInfo) << "Logging initialized";
   LOGGING_LOG(LevelInfo) << "Log file is " << logFilePath_;
   LOGGING_LOG(LevelInfo) << "Logging level is [" << GetLogLevel() << "]";
   LOGGING_LOG(LevelInfo) << "Stderr logging is " <<
      (GetStderrLoggingEnabled() ? "enabled" : "disabled");
}


Logging::~Logging()
{
   LOGGING_LOG(LevelInfo) << "Logging shutting down";

   boost::shared_ptr<boost::log::core> blc = boost::log::core::get();
   blc->remove_all_sinks();
}


void
Logging::SetLogLevel(Level level)
{
   // TODO We should set the level filter only for the file and stderr
   // backends, but for now we set it globally.

   namespace expr = boost::log::expressions;

   boost::shared_ptr<boost::log::core> blc = boost::log::core::get();

   {
      // TODO Make thread-safe (wrt other access to logLevel_)

      blc->set_filter(expr::attr<Level>("Severity") >= level);
      logLevel_ = level;
   }

   LOGGING_LOG(LevelInfo) << "Logging level set to [" << level << "]";
}


void
Logging::SetStderrLoggingEnabled(bool enabled)
{
   boost::shared_ptr<boost::log::core> blc = boost::log::core::get();

   {
      // TODO Make thread-safe (wrt other access to stderrSink_)

      if (enabled && !stderrSink_)
      {
         LOGGING_LOG(LevelInfo) << "Enabling stderr logging";

         boost::shared_ptr<StreamBackend> backend = boost::make_shared<StreamBackend>();
         backend->add_stream(boost::shared_ptr<std::ostream>(&std::clog,
                  boost::empty_deleter()));
         backend->auto_flush(true);
         boost::shared_ptr<StderrSink> sink = boost::make_shared<StderrSink>(backend);
         SetUpFormatter(sink);

         stderrSink_ = sink;
         blc->add_sink(stderrSink_);

         LOGGING_LOG(LevelInfo) << "Enabled stderr logging";
      }
      else if (!enabled && stderrSink_)
      {
         LOGGING_LOG(LevelInfo) << "Disabling stderr logging";

         blc->remove_sink(stderrSink_);
         stderrSink_.reset();

         LOGGING_LOG(LevelInfo) << "Disabled stderr logging";
      }
   }
}


boost::shared_ptr<Logger>
Logging::NewLogger(const char* channel)
{
   boost::shared_ptr<Source> boostLogger =
      boost::make_shared<Source>(boost::log::keywords::channel = channel);

   return boost::make_shared<Logger>(channel, boostLogger, loggingLogger_);
}


void
Logging::ClearCurrentLogFile()
{
   // TODO Implement
}


char*
Logging::GetContentsOfCurrentLogFile(std::size_t& dataSize)
{
   // TODO Implement
   dataSize = 0;
   return 0;
}
