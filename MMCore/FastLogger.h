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
#include "../MMDevice/DeviceThreads.h"


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
class LoggerThread;

class FastLogger: public IMMLogger
{
public:

   FastLogger();
   virtual ~FastLogger();

   friend class LoggerThread;
   /**
   * methods declared in IMMLogger as pure virtual
   * refere to IMMLogger declaration
   */
   bool Initialize(std::string logFileName, std::string logInstanceName)throw(IMMLogger::runtime_exception);
   void Shutdown()throw(IMMLogger::runtime_exception);
   bool Reset()throw(IMMLogger::runtime_exception);
   bool Open(const std::string f_a);
   void SetPriorityLevel(priority level)throw();
   bool EnableLogToStderr(bool enable)throw();
   void Log(IMMLogger::priority p, const char*, ...) throw();

   // read the current log into memory ( for automated trouble report )
   // since the log file can be extremely large, pass back exactly the buffer that was read
   // CALLER IS RESPONSIBLE FOR delete[] of the array!!
   void LogContents(char** /* ppContents */, unsigned long& /*len*/);
   std::string LogPath(void);

	unsigned long flags(void) const { return fast_log_flags_;};
	void set_flags( unsigned long bits_a) { fast_log_flags_ |= bits_a;};
	void clr_flags( unsigned long bits_a) { fast_log_flags_ &=(~bits_a);};

private:
   const char * GetFormatPrefix(IMMLogger::priority p);
   void ReportLogFailure()throw();

private:
   priority       level_;
   unsigned long  fast_log_flags_;
   std::string    logFileName_;
   std::ofstream * plogFile_;
   bool           failureReported;
   std::string    logInstanceName_;
   MMThreadLock logFileLock_g;
   MMThreadLock logStringLock_g;
   std::string stringToWrite_g;
   std::ofstream * plogFile_g;
};
