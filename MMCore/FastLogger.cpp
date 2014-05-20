///////////////////////////////////////////////////////////////////////////////
// FILE:          FastLogger.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCore
//-----------------------------------------------------------------------------
// DESCRIPTION:   Logger implementation
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
// AUTHOR:        Karl Hoover, karl.hoover@ucsf.edu, 2009 11 11
//                Mark Tsuchida, 2013-14.

#include "FastLogger.h"
#include "CoreUtils.h"
#include "../MMDevice/DeviceUtils.h"

#ifdef _WIN32
#include <windows.h>
#else
#include <sys/types.h>
#include <unistd.h>
#endif

#include <boost/algorithm/string.hpp>
#include <boost/lexical_cast.hpp>
#include <boost/scoped_array.hpp>
#include <boost/thread/thread.hpp>

#include <climits>
#include <iostream>


class LoggerThread : public MMDeviceThreadBase
{
   public:
      LoggerThread(FastLogger* l) : log_(l), stop_(false) {}
      ~LoggerThread() {}
      int svc (void);

      void Stop() {stop_ = true;}
      void Start() {stop_ = false; activate();}

   private:
      FastLogger* log_;
      bool stop_;
};


int LoggerThread::svc(void)
{
   do
   {
      std::string entries;
      {
         MMThreadGuard stringGuard(log_->entriesLock_);
         std::swap(entries, log_->pendingEntries_);
      }

      if (!entries.empty())
      {
         if (log_->stderrLoggingEnabled_)
            std::cerr << entries << std::flush;

         MMThreadGuard fileGuard(log_->logFileLock_);
         if (log_->plogFile_)
            *log_->plogFile_ << entries << std::flush;
      }
      CDeviceUtils::SleepMs(30);
   } while (!stop_ );

   return 0;
}


FastLogger::FastLogger() :
   pLogThread_(0),
   debugLoggingEnabled_(true),
   stderrLoggingEnabled_(true),
   fileLoggingEnabled_(false),
   plogFile_(0)
{
}

FastLogger::~FastLogger()
{
   if (pLogThread_)
   {
      pLogThread_->Stop();
      pLogThread_->wait();
      delete pLogThread_;
      pLogThread_ = 0;
   }
   Shutdown();
}


bool FastLogger::Initialize(const std::string& logFileName)
{
   bool bRet =false;

   {
      MMThreadGuard guard(logFileLock_);
      bRet = Open(logFileName);
      if(bRet)
         fileLoggingEnabled_ = true;
   }

   if (!pLogThread_)
   {
      pLogThread_ = new LoggerThread(this);
      pLogThread_->Start();
   }

   return bRet;
};


void FastLogger::Shutdown()
{
   MMThreadGuard guard(logFileLock_);

   if (plogFile_)
   {
      fileLoggingEnabled_ = false;
      plogFile_->close();
      delete plogFile_;
      plogFile_ = NULL;
   }
}

bool FastLogger::Reset()
{
   bool bRet =false;

   MMThreadGuard guard(logFileLock_);
   if (plogFile_)
   {
      if (plogFile_->is_open())
      {
         plogFile_->close();
      }
      //re-open same file but truncate old log content
      plogFile_->open(logFileName_.c_str(), std::ios_base::trunc);
      bRet = true;
   }
   return bRet;
};

void FastLogger::SetPriorityLevel(bool includeDebug)
{
   debugLoggingEnabled_ = includeDebug;
}

bool FastLogger::EnableLogToStderr(bool enable)
{
   if (stderrLoggingEnabled_ == enable)
      return stderrLoggingEnabled_;

   bool bRet = stderrLoggingEnabled_;
   pLogThread_->Stop();
   pLogThread_->wait();
   stderrLoggingEnabled_ = enable;
   pLogThread_->Start();

   return bRet;
};


void FastLogger::VLogF(bool isDebug, const char* format, va_list ap)
{
   // Keep a copy of the argument list
   va_list apCopy;
#ifdef _MSC_VER
   apCopy = ap;
#else
   va_copy(apCopy, ap);
#endif

   // We avoid dynamic allocation in the vast majority of cases.
   const size_t smallBufSize = 1024;
   char smallBuf[smallBufSize];
   int n = vsnprintf(smallBuf, smallBufSize, format, ap);
   if (n >= 0 && n < smallBufSize)
   {
      Log(isDebug, smallBuf);
      return;
   }

   // Okay, now we deal with some nastiness due to the non-standard
   // implementation of Microsoft's vsnprintf() (vsnprintf_s() is no better).

#ifdef _MSC_VER
   // With Microsoft's C Runtime, n is always -1 (whether error or overflow).
   // Try a fixed-size buffer and give up if it is not large enough.
   const size_t bigBufSize = 65536;
#else
   // With C99/C++11 compliant vsnprintf(), n is -1 if error but on overflow it
   // is the required string length.
   if (n < 0)
   {
      Log(isDebug, ("Error in vsnprintf() while formatting log entry with "
               "format string " + ToQuotedString(format)).c_str());
      return;
   }
   size_t bigBufSize = n + 1;
#endif

   boost::scoped_array<char> bigBuf(new char[bigBufSize]);
   if (!bigBuf)
   {
      Log(isDebug, ("Error: could not allocate " + ToString(bigBufSize/1024) +
               " kilobytes to format log entry with format string " +
               ToQuotedString(format)).c_str());
      return;
   }

   n = vsnprintf(bigBuf.get(), bigBufSize, format, apCopy);
   if (n >= 0 && n < bigBufSize)
   {
      Log(isDebug, bigBuf.get());
      return;
   }

#ifdef _MSC_VER
   Log(isDebug, ("Error or overflow in vsnprintf() (buffer size " +
            ToString(bigBufSize / 1024) + " kilobytes) while formatting "
            "log entry with format string " + ToQuotedString(format)).c_str());
#else
   Log(isDebug, ("Error in vsnprintf() while formatting log entry with "
            "format string " + ToQuotedString(format)).c_str());
#endif
}


void FastLogger::LogF(bool isDebug, const char* format, ...)
{
   va_list ap;
   va_start(ap, format);
   VLogF(isDebug, format, ap);
   va_end(ap);
}


void FastLogger::Log(bool isDebug, const char* entry)
{
   {
      MMThreadGuard guard(logFileLock_);
      if (!plogFile_)
      {
         return;
      }
   }

   if (isDebug && !debugLoggingEnabled_)
      return;

   std::ostringstream entryStream;
   WriteEntryPrefix(entryStream, isDebug);
   entryStream << entry;

   std::string entryString = entryStream.str();
   boost::algorithm::trim_right(entryString);
   entryString += '\n';

   {
      MMThreadGuard stringGuard(entriesLock_);
      pendingEntries_ += entryString;
   }
}


void FastLogger::WriteEntryPrefix(std::ostream& stream, bool isDebug)
{
   // Date
   boost::posix_time::ptime pt =
      boost::posix_time::microsec_clock::local_time();
   stream << boost::posix_time::to_iso_extended_string(pt);

   // PID
   stream << " p:";
#ifdef _WIN32
   stream << GetCurrentProcessId();
#else
   stream << getpid();
#endif

   // TID
   stream << " t:";
   // Use the platform thread id where available, so that it can be compared
   // with debugger, etc.
#ifdef _WIN32
   stream << GetCurrentThreadId();
#else
   stream << pthread_self();
#endif

   // Log level
   if (isDebug)
      stream << " [dbg] ";
   else
      stream << " [LOG] ";
}


bool FastLogger::Open(const std::string& specifiedFile)
{
   bool bRet = false;

   if (!plogFile_)
   {
      plogFile_ = new std::ofstream();
   }
   if (!plogFile_->is_open())
   {
      // N.B. we do NOT handle re-opening of the log file on a different path!!

      if(logFileName_.length() < 1) // if log file path has not yet been specified:
      {
         logFileName_ = specifiedFile;
      }

      // first try to open the specified file without any assumption about the path
      plogFile_->open(logFileName_.c_str(), std::ios_base::app);

      // if the open failed, assume that this is because the ordinary user
      // does not have write access to the application / program directory
      if (!plogFile_->is_open())
      {
         std::string homePath;
#ifdef _WINDOWS
         homePath = std::string(getenv("HOMEDRIVE")) + std::string(getenv("HOMEPATH")) + "\\";
#else
         homePath = std::string(getenv("HOME")) + "/";
#endif
         logFileName_ = homePath + specifiedFile;
         plogFile_->open(logFileName_.c_str(), std::ios_base::app);
      }
   }

   bRet = plogFile_->is_open();
   return bRet;
}

void FastLogger::LogContents(char** ppContents, unsigned long& len)
{
   *ppContents = 0;
   len = 0;

   MMThreadGuard guard(logFileLock_);

   if (plogFile_->is_open())
   {
      plogFile_->close();
   }

   // open to read, and position at the end of the file
   // XXX We simply return NULL if cannot open file or size is too large!
   std::ifstream ifile(logFileName_.c_str(),
         std::ios::in | std::ios::binary | std::ios::ate);
   if (ifile.is_open())
   {
      std::ifstream::pos_type pos = ifile.tellg();

      // XXX This is broken (sort of): on 64-bit Windows, we artificially
      // limit ourselves to 4 GB. But it is probably okay since we don't
      // expect the log contents to be > 4 GB. Fixing would require changing
      // the signature of this function.
      if (pos < ULONG_MAX)
      {
         len = static_cast<unsigned long>(pos);
         *ppContents = new char[len];
         if (0 != *ppContents)
         {
            ifile.seekg(0, std::ios::beg);
            ifile.read(*ppContents, len);
            ifile.close();
         }
      }
   }

   // re-open for logging
   plogFile_->open(logFileName_.c_str(), std::ios_base::app);

   return;
}


std::string FastLogger::LogPath(void)
{
   MMThreadGuard guard(logFileLock_);
   return logFileName_;
}
