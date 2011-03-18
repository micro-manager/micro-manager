///////////////////////////////////////////////////////////////////////////////
// FILE:          FastLogger.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCore
//-----------------------------------------------------------------------------
// DESCRIPTION:   Definitions for an implementation of the IMMLogger interface
// COPYRIGHT:     University of California, San Francisco, 2009
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
// AUTHOR:        Karl Hoover, karl.hoover@ucsf.edu, 20091111
// 
// CVS:           $Id: FastLogger.h $

#pragma once

#include "IMMLogger.h"
enum Fast_Log_Priorities
{
  // = Note, this first argument *must* start at 1!

  /// Shutdown the logger (decimal 1).
  FL_SHUTDOWN = 01,

  /// Messages indicating function-calling sequence (decimal 2).
  FL_TRACE = 02,

  /// Messages that contain information normally of use only when
  /// debugging a program (decimal 4).
  FL_DEBUG = 04,

  /// Informational messages (decimal 8).
  FL_INFO = 010,

  /// Conditions that are not error conditions, but that may require
  /// special handling (decimal 16).
  FL_NOTICE = 020,

  /// Warning messages (decimal 32).
  FL_WARNING = 040,

  /// Initialize the logger (decimal 64).
  FL_STARTUP = 0100,

  /// Error messages (decimal 128).
  FL_ERROR = 0200,

  /// Critical conditions, such as hard device errors (decimal 256).
  FL_CRITICAL = 0400,

  /// A condition that should be corrected immediately, such as a
  /// corrupted system database (decimal 512).
  FL_ALERT = 01000,

  /// A panic condition.  This is normally broadcast to all users
  /// (decimal 1024).
  FL_EMERGENCY = 02000,

  /// The maximum logging priority.
  FL_MAX = FL_EMERGENCY,

  /// Do not use!!  This enum value ensures that the underlying
  /// integral type for this enum is at least 32 bits.
  FL_ENSURE_32_BITS = 0x7FFFFFFF
};


  enum
  {
    /// Write messages to stderr.
    STDERR = 1,
    /// Write messages to the local client logger deamon.
    LOGGER = 2,
    /// Write messages to the ostream * stored in thread-specific
    /// storage.
    OSTREAM = 4,
    /// Write messages to the callback object.
    MSG_CALLBACK = 8,
    /// Display messages in a verbose manner.
    VERBOSE = 16,
    /// Display messages in a less verbose manner (i.e., only print
    /// information that can change between calls).
    VERBOSE_LITE = 32,
    /// Do not print messages at all (just leave in thread-specific
    /// storage for later inspection).
    SILENT = 64,
    /// Write messages to the system's event log.
    SYSLOG = 128,
    /// Write messages to the user provided backend
    CUSTOM = 256
 };

/**
* class FastLogger 
* Implements interface IMMLogger with ACE logging facility
*/

class FastLogger: public IMMLogger
{
public:

   FastLogger();
   virtual ~FastLogger();

   /**
   * methods declared in IMMLogger as pure virtual
   * refere to IMMLogger declaration
   */
   bool Initialize(std::string logFileName, std::string logInstanceName)throw(IMMLogger::runtime_exception);
   bool IsValid()throw();
   void Shutdown()throw(IMMLogger::runtime_exception);
   bool Reset()throw(IMMLogger::runtime_exception);
   bool Open(const std::string f_a);
   priority SetPriorityLevel(priority level)throw();
   bool EnableLogToStderr(bool enable)throw();
   IMMLogger::priority  EnableTimeStamp(IMMLogger::priority flags)throw();
   void TimeStamp(IMMLogger::priority level = IMMLogger::info)throw();
	// TODO replace this with boost::format !!!!
   void Log(IMMLogger::priority p, const char*, ...)throw();
   void SystemLog(std::string format)throw();

   // read the current log into memory ( for automated trouble report )
   // since the log file can be extremely large, pass back exactly the buffer that was read
   // CALLER IS RESPONSIBLE FOR delete[] of the array!!
   void LogContents(char** /* ppContents */, unsigned long& /*len*/);
   std::string LogPath(void);

	unsigned long flags(void) const { return fast_log_flags_;};
	void set_flags( unsigned long bits_a) { fast_log_flags_ |= bits_a;};
	void clr_flags( unsigned long bits_a) { fast_log_flags_ &=(~bits_a);};

private:
   //helpers
   //void InitializeInCurrentThread();
   const char * GetFormatPrefix(Fast_Log_Priorities p);
   void ReportLogFailure()throw();
   //to support legacy 2-level implementation:
   //returns FL_DEBUG or FL_INFO

	Fast_Log_Priorities MatchACEPriority(IMMLogger::priority p);


private:
   priority       level_;
   priority       timestamp_level_;
   unsigned long  fast_log_flags_;
   std::string    logFileName_;
   std::ofstream * plogFile_;
   bool           failureReported;
   std::string    logInstanceName_;

};
