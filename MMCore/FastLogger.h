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

#pragma once

#include "IMMLogger.h"
#include "../MMDevice/DeviceThreads.h"


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
   void SetPriorityLevel(bool includeDebug)throw();
   bool EnableLogToStderr(bool enable)throw();
   void Log(bool isDebug, const char*, ...) throw();

   // read the current log into memory ( for automated trouble report )
   // since the log file can be extremely large, pass back exactly the buffer that was read
   // CALLER IS RESPONSIBLE FOR delete[] of the array!!
   void LogContents(char** /* ppContents */, unsigned long& /*len*/);
   std::string LogPath(void);

private:
   std::string GetEntryPrefix(bool isDebug);
   void ReportLogFailure()throw();

private:
   bool debugLoggingEnabled_;
   bool stderrLoggingEnabled_;
   bool fileLoggingEnabled_;
   std::string    logFileName_;
   std::ofstream * plogFile_;
   bool           failureReported;
   std::string    logInstanceName_;
   MMThreadLock logFileLock_g;
   MMThreadLock logStringLock_g;
   std::string stringToWrite_g;
   std::ofstream * plogFile_g;
};
