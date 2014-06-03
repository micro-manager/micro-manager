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
//                Mark Tsuchida, 2013-14. Now an adapter for LogManager.

#include "FastLogger.h"

#include "CoreUtils.h"
#include "Error.h"

#include <climits>
#include <fstream>


bool FastLogger::Initialize(const std::string& logFileName)
{
   manager_.SetUseStdErr(true);
   try
   {
      manager_.SetPrimaryLogFilename(logFileName, false);
   }
   catch (const CMMError&)
   {
      return false;
   }
   return true;
}


bool FastLogger::Reset()
{
   try
   {
      manager_.TruncatePrimaryLogFile();
   }
   catch (const CMMError&)
   {
      return false;
   }
   return true;
}


void FastLogger::SetPriorityLevel(bool includeDebug)
{
   manager_.SetPrimaryLogLevel(includeDebug ? mm::logging::LogLevelTrace :
         mm::logging::LogLevelInfo);
}


bool FastLogger::EnableLogToStderr(bool enable)
{
   manager_.SetUseStdErr(enable);
   return true;
}


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
   defaultLogger_->Log(isDebug ? mm::logging::LogLevelDebug :
         mm::logging::LogLevelInfo, entry);
}


void FastLogger::LogContents(char** ppContents, unsigned long& len)
{
   *ppContents = 0;
   len = 0;

   std::string filename = manager_.GetPrimaryLogFilename();
   if (filename.empty())
      return;

   manager_.SetPrimaryLogFilename("", false);

   // open to read, and position at the end of the file
   // XXX We simply return NULL if cannot open file or size is too large!
   std::ifstream ifile(filename.c_str(),
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

   manager_.SetPrimaryLogFilename(filename, false);
}


std::string FastLogger::LogPath()
{
   return manager_.GetPrimaryLogFilename();
}
