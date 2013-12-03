///////////////////////////////////////////////////////////////////////////////
// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCore
//-----------------------------------------------------------------------------
// DESCRIPTION:   Logging facilities
//
// COPYRIGHT:     University of California, San Francisco, 2013.
//
// LICENSE:       This file is distributed under the BSD license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

#pragma once

#ifdef _MSC_VER
#  pragma warning(push)
#  pragma warning(disable: 4100)
#  pragma warning(disable: 4510)
#  pragma warning(disable: 4512)
#  pragma warning(disable: 4610)
#endif
#include <boost/log/common.hpp>
#include <boost/log/sinks.hpp>
#ifdef _MSC_VER
#  pragma warning(pop)
#endif

#include <boost/shared_ptr.hpp>
#include <boost/utility.hpp>

#include <ostream>
#include <string>

#include <ctime>

class Logger;


class Logging /* final */ : boost::noncopyable
{
public:
   enum Level
   {
      LevelTrace,
      LevelDebug,
      // Here is the boundary when using a two-level (debug vs normal) setting.
      LevelInfo,
      LevelWarning,
      LevelError,
      LevelFatal,
   };

   typedef boost::log::sources::severity_channel_logger_mt<Level, std::string> Source;

   friend std::ostream& operator<<(std::ostream& os, Level level);

   explicit Logging(const std::string& logDirectory,
         const std::string& filenamePrefix = "CoreLog",
         Level initialLevel = LevelInfo,
         bool stderrLoggingInitiallyEnabled = true);

   ~Logging();

   void SetLogLevel(Level level);
   Level GetLogLevel() { return logLevel_; }

   void SetStderrLoggingEnabled(bool enabled);
   bool GetStderrLoggingEnabled() { return stderrSink_; }

   boost::shared_ptr<Logger> NewLogger(const char* channel);

   /// Duplicate the log contents and send to a TCP port on localhost.
   /**
    * This sets up a TCP client that connects to the given port and starts
    * sending log entries. It is intended for log viewers and processors
    * written in a different (non-C++) language.
    *
    * The log level specified by level remains in effect for the lifetime of
    * the connection, independent of the global Logging log level (set by
    * SetLogLevel()).
    *
    * Multiple duplications to sockets can be set up at the same time, so long
    * as the socket numbers differ.
    */
   void StartSendingToSocket(int port, unsigned long connectionTimeoutMs, Level level);

   /// Stop duplication to TCP socket set up with StartSendingToSocket().
   /**
    * No error is thrown if there is no current connection to the port (whether
    * StopSendingToSocket() has already been called, the connection was
    * dropped, or a connection never existed).
    */
   void StopSendingToSocket(int port);

   /// Delete old log files
   /**
    * This only deletes log files in the directory and with the prefix set by
    * the Logging constructor. It is not the business of this logger
    * implementation to deal with log files produced by previous logger
    * implementations (that is an application-level concern).
    *
    * The current log file is never deleted, even if cutoffSecondsAgo is less
    * than 1.
    */
   // XXX Should we make the delete-old-files behavior a stateful setting
   // rather than a one-time action? I feel that the Java layer should deal
   // with that (just delete old files at startup and/or whenever it is deemed
   // appropriate).
   void DeleteOldLogFiles(std::time_t cutoffSecondsAgo);

   /// Clear the current log file
   /**
    * This function is deprecated since its inception. It is solely for
    * transitional purposes.
    *
    * The log file should not be altered for the short-term convenience of the
    * user, and better means should be provided for the purpose of viewing a
    * clean section of the log.
    */
   // XXX We need a macro for deprecating functions...
#ifdef _MSC_VER
   __declspec(deprecated)
#endif
   void ClearCurrentLogFile()
#ifdef __GNUC__
   __attribute__((deprecated))
#endif
   ;

   /// Get the contents of the current log file
   /**
    * This function is deprecated since its inception. It is solely for
    * transitional purposes.
    *
    * The caller must call the delete[] operator on the return value.
    *
    * The size of the contents is returned in dataSize. The returned data is
    * not null-terminated (XXX ?).
    *
    * Not a good mechanism because it requires clearing the log
    * (ClearCurrentLogFile()) when the aim is just to extract a section of the
    * log (it is crazy that using the Report Problem function requires clearing
    * the log on disk). Use of this function should be replaced with a
    * mechanism based on StartSendingToSocket().
    */
#ifdef _MSC_VER
   __declspec(deprecated)
#endif
   char* GetContentsOfCurrentLogFile(std::size_t& dataSize)
#ifdef __GNUC__
   __attribute__((deprecated))
#endif
   ;

private:
   Level logLevel_;

   typedef boost::log::sinks::text_ostream_backend StreamBackend;
   typedef boost::log::sinks::asynchronous_sink<StreamBackend> StderrSink;
   boost::shared_ptr<StderrSink> stderrSink_;

   const std::string logDirectory_;
   const std::string logFileNamePrefix_;
   std::string logFilePath_;

   boost::shared_ptr<Source> loggingLogger_;
};
