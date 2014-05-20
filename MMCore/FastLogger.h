///////////////////////////////////////////////////////////////////////////////
// FILE:          FastLogger.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCore
//-----------------------------------------------------------------------------
// DESCRIPTION:   Logger interface
// COPYRIGHT:     University of California, San Francisco, 2009-2014
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
//                Mark Tsuchida, 2013-14.

#pragma once

#include "../MMDevice/DeviceThreads.h"

#include <cstdarg>
#include <fstream>
#include <string>


class LoggerThread;

class FastLogger
{
public:
   FastLogger();
   virtual ~FastLogger();

   friend class LoggerThread;

   bool Initialize(const std::string& logFileName);
   void Shutdown();
   bool Reset();

   void SetPriorityLevel(bool includeDebug);
   bool EnableLogToStderr(bool enable);

   void VLogF(bool isDebug, const char* format, va_list ap);
   void LogF(bool isDebug, const char* format, ...);
   void Log(bool isDebug, const char* entry);

   // read the current log into memory ( for automated trouble report )
   // since the log file can be extremely large, pass back exactly the buffer that was read
   // CALLER IS RESPONSIBLE FOR delete[] of the array!!
   void LogContents(char** ppContents, unsigned long& len);
   std::string LogPath(void);

private:
   std::string GetEntryPrefix(bool isDebug);
   bool Open(const std::string& f_a); // Called with logFileLock_ held

private:
   LoggerThread* pLogThread_;

   bool debugLoggingEnabled_; // Not correctly synchronized
   bool stderrLoggingEnabled_; // Not correctly synchronized

   MMThreadLock logFileLock_;
   bool fileLoggingEnabled_; // Access synchronized by logFileLock_
   std::string logFileName_; // Access synchronized by logFileLock_
   std::ofstream* plogFile_; // Access synchronized by logFileLock_

   MMThreadLock logStringLock_;
   std::string stringToWrite_; // Access synchronized by logStringLock_
};
