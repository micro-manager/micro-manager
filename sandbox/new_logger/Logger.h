///////////////////////////////////////////////////////////////////////////////
// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCore
//-----------------------------------------------------------------------------
// DESCRIPTION:   Logger
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

#include "Logging.h"

#ifdef _MSC_VER
#  pragma warning(push)
#  pragma warning(disable: 4100)
#  pragma warning(disable: 4510)
#  pragma warning(disable: 4512)
#  pragma warning(disable: 4610)
#endif
#include <boost/log/common.hpp>
#ifdef _MSC_VER
#  pragma warning(pop)
#endif

#include <boost/shared_ptr.hpp>
#include <boost/utility.hpp>

#include <cstdarg>


class Logger /* final */ : boost::noncopyable
{
public:
   typedef Logging::Level Level;
   Logger(const char* channel,
         boost::shared_ptr<Logging::Source> logger,
         boost::shared_ptr<Logging::Source> errorLogger) :
      channel_(channel),
      boostLogger_(logger),
      errorLogger_(errorLogger)
   {}

   void Log(Level level, const std::string& message);
   void LogF(Level level, const char* format, ...);
   void VLogF(Level level, const char* format, va_list ap);

   void LogTrace(const char* message) { Log(Logging::LevelTrace, message); }
   void LogTrace(const std::string& message) { Log(Logging::LevelTrace, message); }
   void LogTraceF(const char* format, ...);

   void LogDebug(const char* message) { Log(Logging::LevelDebug, message); }
   void LogDebug(const std::string& message) { Log(Logging::LevelDebug, message); }
   void LogDebugF(const char* format, ...);

   void LogInfo(const char* message) { Log(Logging::LevelInfo, message); }
   void LogInfo(const std::string& message) { Log(Logging::LevelInfo, message); }
   void LogInfoF(const char* format, ...);

   void LogWarning(const char* message) { Log(Logging::LevelWarning, message); }
   void LogWarning(const std::string& message) { Log(Logging::LevelWarning, message); }
   void LogWarningF(const char* format, ...);

   void LogError(const char* message) { Log(Logging::LevelError, message); }
   void LogError(const std::string& message) { Log(Logging::LevelError, message); }
   void LogErrorF(const char* format, ...);

   void LogFatal(const char* message) { Log(Logging::LevelFatal, message); }
   void LogFatal(const std::string& message) { Log(Logging::LevelFatal, message); }
   void LogFatalF(const char* format, ...);

private:
   const std::string channel_;
   boost::shared_ptr<Logging::Source> boostLogger_;
   boost::shared_ptr<Logging::Source> errorLogger_;
};
