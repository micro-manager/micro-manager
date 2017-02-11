#include "LogManager.h"

#include "CoreUtils.h"
#include "Error.h"

#include <boost/make_shared.hpp>
#include <boost/shared_ptr.hpp>

#include <utility>
#include <vector>

namespace mm
{

using namespace mm::logging;

namespace
{

const char* StringForLogLevel(LogLevel level)
{
   switch (level)
   {
      case LogLevelTrace: return "trace";
      case LogLevelDebug: return "debug";
      case LogLevelInfo: return "info";
      case LogLevelWarning: return "warning";
      case LogLevelError: return "error";
      case LogLevelFatal: return "fatal";
      default: return "(unknown)";
   }
}

} // anonymous namespace

LogManager::LogManager() :
   loggingCore_(boost::make_shared<LoggingCore>()),
   internalLogger_(loggingCore_->NewLogger("LogManager")),
   primaryLogLevel_(LogLevelInfo),
   usingStdErr_(false),
   nextSecondaryHandle_(0)
{}


void
LogManager::SetUseStdErr(bool flag)
{
   boost::lock_guard<boost::mutex> lock(mutex_);

   if (flag == usingStdErr_)
      return;

   usingStdErr_ = flag;
   if (flag)
   {
      if (!stdErrSink_)
      {
         stdErrSink_ = boost::make_shared<StdErrLogSink>();
         stdErrSink_->SetFilter(
               boost::make_shared<LevelFilter>(primaryLogLevel_));
      }
      loggingCore_->AddSink(stdErrSink_, PrimarySinkMode);

      LOG_INFO(internalLogger_) << "Enabled logging to stderr";
   }
   else
   {
      LOG_INFO(internalLogger_) << "Disabling logging to stderr";

      loggingCore_->RemoveSink(stdErrSink_, PrimarySinkMode);
   }
}


bool
LogManager::IsUsingStdErr() const
{
   boost::lock_guard<boost::mutex> lock(mutex_);
   return usingStdErr_;
}


void
LogManager::SetPrimaryLogFilename(const std::string& filename, bool truncate)
{
   boost::lock_guard<boost::mutex> lock(mutex_);

   if (filename == primaryFilename_)
      return;

   primaryFilename_ = filename;

   if (primaryFilename_.empty())
   {
      if (primaryFileSink_)
      {
         LOG_INFO(internalLogger_) << "Disabling primary log file";
         loggingCore_->RemoveSink(primaryFileSink_, PrimarySinkMode);
         primaryFileSink_.reset();
      }
      return;
   }

   boost::shared_ptr<LogSink> newSink;
   try
   {
      newSink = boost::make_shared<FileLogSink>(primaryFilename_, !truncate);
   }
   catch (const CannotOpenFileException&)
   {
      LOG_ERROR(internalLogger_) << "Failed to open file " <<
         filename << " as primary log file";
      if (primaryFileSink_)
      {
         LOG_INFO(internalLogger_) << "Disabling primary log file";
         loggingCore_->RemoveSink(primaryFileSink_, PrimarySinkMode);
      }
      primaryFileSink_.reset();
      primaryFilename_.clear();
      throw CMMError("Cannot open file " + ToQuotedString(filename));
   }

   newSink->SetFilter(boost::make_shared<LevelFilter>(primaryLogLevel_));

   if (!primaryFileSink_)
   {
      loggingCore_->AddSink(newSink, PrimarySinkMode);
      primaryFileSink_ = newSink;
      LOG_INFO(internalLogger_) << "Enabled primary log file " <<
         primaryFilename_;
   }
   else
   {
      // We will use atomic swapping so that no entries get lost between the
      // two files. This makes it possible to use this function for log
      // rotation.

      LOG_INFO(internalLogger_) << "Switching primary log file";
      std::vector< std::pair<boost::shared_ptr<LogSink>, SinkMode> > toRemove;
      std::vector< std::pair<boost::shared_ptr<LogSink>, SinkMode> > toAdd;
      toRemove.push_back(
            std::make_pair(primaryFileSink_, PrimarySinkMode));
      toAdd.push_back(std::make_pair(newSink, PrimarySinkMode));

      loggingCore_->AtomicSwapSinks(toRemove.begin(), toRemove.end(),
            toAdd.begin(), toAdd.end());
      primaryFileSink_ = newSink;
      LOG_INFO(internalLogger_) << "Switched primary log file to " <<
         primaryFilename_;
   }
}


std::string
LogManager::GetPrimaryLogFilename() const
{
   boost::lock_guard<boost::mutex> lock(mutex_);
   return primaryFilename_;
}


bool
LogManager::IsUsingPrimaryLogFile() const
{
   boost::lock_guard<boost::mutex> lock(mutex_);
   return !primaryFilename_.empty();
}


void
LogManager::SetPrimaryLogLevel(LogLevel level)
{
   boost::lock_guard<boost::mutex> lock(mutex_);

   if (level == primaryLogLevel_)
      return;

   LogLevel oldLevel = primaryLogLevel_;
   primaryLogLevel_ = level;

   LOG_INFO(internalLogger_) << "Switching primary log level from " <<
      StringForLogLevel(oldLevel) << " to " << StringForLogLevel(level);

   boost::shared_ptr<EntryFilter> filter =
      boost::make_shared<LevelFilter>(level);

   std::vector<
      std::pair<
         std::pair<boost::shared_ptr<LogSink>, SinkMode>,
         boost::shared_ptr<EntryFilter>
      >
   > changes;
   if (stdErrSink_)
   {
      changes.push_back(
            std::make_pair(std::make_pair(stdErrSink_, PrimarySinkMode),
               filter));
   }
   if (primaryFileSink_)
   {
      changes.push_back(
            std::make_pair(std::make_pair(primaryFileSink_, PrimarySinkMode),
               filter));
   }

   loggingCore_->AtomicSetSinkFilters(changes.begin(), changes.end());

   LOG_INFO(internalLogger_) << "Switched primary log level from " <<
      StringForLogLevel(oldLevel) << " to " << StringForLogLevel(level);
}


LogLevel
LogManager::GetPrimaryLogLevel() const
{
   boost::lock_guard<boost::mutex> lock(mutex_);
   return primaryLogLevel_;
}


LogManager::LogFileHandle
LogManager::AddSecondaryLogFile(LogLevel level,
      const std::string& filename, bool truncate, SinkMode mode)
{
   boost::lock_guard<boost::mutex> lock(mutex_);

   boost::shared_ptr<LogSink> sink;
   try
   {
      sink = boost::make_shared<FileLogSink>(filename, !truncate);
   }
   catch (const CannotOpenFileException&)
   {
      LOG_ERROR(internalLogger_) << "Failed to open file " <<
         filename << " as secondary log file";
      throw CMMError("Cannot open file " + ToQuotedString(filename));
   }

   sink->SetFilter(boost::make_shared<LevelFilter>(level));

   LogFileHandle handle = nextSecondaryHandle_++;
   secondaryLogFiles_.insert(std::make_pair(handle,
            LogFileInfo(filename, sink, mode)));

   loggingCore_->AddSink(sink, mode);

   LOG_INFO(internalLogger_) << "Added secondary log file " << filename <<
      " with log level " << StringForLogLevel(level);

   return handle;
}


void
LogManager::RemoveSecondaryLogFile(LogManager::LogFileHandle handle)
{
   boost::lock_guard<boost::mutex> lock(mutex_);

   std::map<LogFileHandle, LogFileInfo>::iterator foundIt =
      secondaryLogFiles_.find(handle);
   if (foundIt == secondaryLogFiles_.end())
   {
      LOG_ERROR(internalLogger_) << "Cannot remove secondary log file (" <<
         handle << ": no such handle)";
      return;
   }

   LOG_INFO(internalLogger_) << "Removing secondary log file " <<
      foundIt->second.filename_;
   loggingCore_->RemoveSink(foundIt->second.sink_, foundIt->second.mode_);
   secondaryLogFiles_.erase(foundIt);
}


Logger
LogManager::NewLogger(const std::string& label)
{
   return loggingCore_->NewLogger(label);
}

} // namespace mm
