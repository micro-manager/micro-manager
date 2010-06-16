///////////////////////////////////////////////////////////////////////////////
// FILE:          IMMLogger.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCore
//-----------------------------------------------------------------------------
// DESCRIPTION:   Interface class for logging
//
// COPYRIGHT:     University of California, San Francisco, 2007
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
//
// CVS:           $Id:$
//

#pragma once

#include <string>
#include <stdexcept>

/**
* class IMMLogger
* Declares interface to the logger object
* This interface is introduces for de-coupling legacy code from
* specific implementation of the logger
* set of methods is chosen to accomodate legacy code
* 
*/
class IMMLogger
{
protected:
   IMMLogger(){};
public:
   class runtime_exception : public std::runtime_error
   {
   public:
      runtime_exception(std::string msg) : runtime_error(msg) {}
   };
   /**
   * Instance
   * returns pointer to single instance of IMMLogger object
   * Can return NULL when IsValid() returns false
   * Reentrance from multiple threads is not guarded 
   * Throws: IMMLogger::runtime_exception
   */
   static IMMLogger * Instance()throw(runtime_exception);

   /**
   * IsValid
   * returns bool if the instance of IMMLogger is valid to use
   * Reentrance from multiple threads is not guarded 
   * Must not throw exceptions
   */
   virtual bool IsValid()throw() = 0;

   /**
   * enum priority
   * This enum type defines the relative priorities of the
   *    messages sent to the log, lowest to highest priority.
   *    defined from lowest to highest
   */
   typedef enum 
   {
      none     = 0,
      trace    = 1,
      debug    = 2,
      info     = 4,
      warning  = 8,
      error    = 16,
      alert    = 32,
      any      = 0x7FFFFFFF
   }priority;

   virtual ~IMMLogger(){};

   /**
   * Initialize
   * Opens log files
   * This method supposed to be called before calling other methods of the logger
   * Returns true on success
   * Must guaranty safe reentrance
   * Throws: IMMLogger::runtime_exception
   */
   virtual bool Initialize(std::string logFileName, std::string logInstanceName)throw(runtime_exception)   =0;


   /**
   * Shutdown
   * Ensures proper flush of the open log files
   * This method must be called last
   * After this method is called, calls of other methods will have no effect
   * Returns true on success
   * Must guaranty safe reentrance
   * Throws: IMMLogger::runtime_exception
   */
   virtual void Shutdown()throw(runtime_exception)     =0;

   /**
   * Reset
   * Reset the logger. 
   * Performed action is defined in particlural implementation of the logger  
   * Returns true on success
   * Must guaranty safe reentrance
   * Throws: IMMLogger::runtime_exception
   */
   virtual bool Reset()throw(IMMLogger::runtime_exception) = 0;

   /**
   * SetPriorityLevel
   * set current priority level for the logged
   * Messages of a lower priority will be ignored
   * Parameter priority - indicates priority to be set
   * Returns previous value of the priority level
   * Must guaranty safe reentrance
   * Must not throw exceptions
   */
   virtual IMMLogger::priority SetPriorityLevel(IMMLogger::priority level)throw() = 0;

   /**
   * EnableLogToStderr
   * Enable or disable output of the logged information to standard output
   * Returns previous state: true if logging to standard output was enabled
   * Must guaranty safe reentrance
   * Must not throw exceptions
   */
   virtual bool EnableLogToStderr(bool enable)throw() = 0;

   /**
   * TimeStamp
   * Writes a single timeStamp record to the log 
   * with a specified priority level
   * Parameter priority - indicates priority level of the time stamp
   * Must guaranty safe reentrance
   * Must not throw exceptions
   */
   virtual void TimeStamp(IMMLogger::priority level = IMMLogger::info)throw() = 0;

   /**
   * EnableLogToStderr
   * Enable or disable timestamp in the log records
   * Parameter flags defines the set of priorities 
   *              for which timestamping is enabled 
   * Returns previous value of the priority flags 
   *             for which timestamping is enabled
   * Must guaranty safe reentrance
   * Must not throw exceptions
   */
   virtual IMMLogger::priority  EnableTimeStamp(IMMLogger::priority flags)throw() = 0;

   /**
   * Log
   * Sends message to the log if current priority level is equal or higher than
   * priority level of this message
   * Parameter priority - indicates priority of this message
   * Parameter format - printf-stile format string
   * Parameters ... used as specified by the parameter "format" 
   * Must guaranty safe reentrance
   * Must not throw exceptions
   */
   virtual void Log(IMMLogger::priority p, const char*, ...)throw() = 0;
   /**
   * SystemLog
   * Sends message to the log if current priority level is equal or higher than
   * priority level of this message
   * Parameter priority - indicates priority of this message
   * Parameter format - printf-stile format string
   * Parameters arg1..argN - values used as specified by the paremeter "format" 
   * Must guaranty safe reentrance
   * Must not throw exceptions
   */
   virtual void SystemLog(std::string format)throw() = 0;

   virtual void LogContents(char**  /*ppContents*/, unsigned long& /*len*/) = 0;
   virtual std::string LogPath(void) = 0;

};

/**
* Legacy macros used on MMCore
*/
#define CORE_DEBUG(FMT)                         IMMLogger::Instance()->Log(IMMLogger::debug, FMT)
#define CORE_DEBUG1(FMT, arg1)                  IMMLogger::Instance()->Log(IMMLogger::debug, FMT, arg1)
#define CORE_DEBUG2(FMT, arg1, arg2)            IMMLogger::Instance()->Log(IMMLogger::debug, FMT, arg1, arg2)
#define CORE_DEBUG3(FMT, arg1, arg2, arg3)      IMMLogger::Instance()->Log(IMMLogger::debug, FMT, arg1, arg2, arg3)
#define CORE_DEBUG4(FMT, arg1, arg2, arg3, arg4) IMMLogger::Instance()->Log(IMMLogger::debug, FMT, arg1, arg2, arg3, arg4)

#define CORE_LOG(FMT)                           IMMLogger::Instance()->Log(IMMLogger::info, FMT)
#define CORE_LOG1(FMT, arg1)                    IMMLogger::Instance()->Log(IMMLogger::info, FMT, arg1)
#define CORE_LOG2(FMT, arg1, arg2)              IMMLogger::Instance()->Log(IMMLogger::info, FMT, arg1, arg2)
#define CORE_LOG3(FMT, arg1, arg2, arg3)        IMMLogger::Instance()->Log(IMMLogger::info, FMT, arg1, arg2, arg3)
#define CORE_LOG4(FMT, arg1, arg2, arg3, arg4)  IMMLogger::Instance()->Log(IMMLogger::info, FMT, arg1, arg2, arg3, arg4)
#define CORE_LOG5(FMT, arg1, arg2, arg3, arg4, arg5)  IMMLogger::Instance()->Log(IMMLogger::info, FMT, arg1, arg2, arg3, arg4, arg5)
#define CORE_TIMESTAMP                          IMMLogger::Instance()->TimeStamp()
