///////////////////////////////////////////////////////////////////////////////
// FILE:          CoreUtils.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCore
//-----------------------------------------------------------------------------
// DESCRIPTION:   Utility classes and functions for use in MMCore
//              
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 09/27/2005
//
// COPYRIGHT:     University of California, San Francisco, 2006
//
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
// CVS:           $Id$
//

#ifndef _CORE_UTILS_H_
#define _CORE_UTILS_H_

#include "ace/High_Res_Timer.h"
#include "ace/Log_Msg.h"

#define CORE_DEBUG_PREFIX "DBG(%P, %t:) "
#define CORE_LOG_PREFIX "LOG(%P, %t:): "

#define CORE_DEBUG(FMT)                         ACE_DEBUG((LM_DEBUG, CORE_DEBUG_PREFIX FMT))
#define CORE_DEBUG1(FMT, arg1)                  ACE_DEBUG((LM_DEBUG, CORE_DEBUG_PREFIX FMT, arg1))
#define CORE_DEBUG2(FMT, arg1, arg2)            ACE_DEBUG((LM_DEBUG, CORE_DEBUG_PREFIX FMT, arg1, arg2))
#define CORE_DEBUG3(FMT, arg1, arg2, arg3)      ACE_DEBUG((LM_DEBUG, CORE_DEBUG_PREFIX FMT, arg1, arg2, arg3))
#define CORE_DEBUG4(FMT, arg1, arg2, arg3, arg4) ACE_DEBUG((LM_DEBUG, CORE_DEBUG_PREFIX FMT, arg1, arg2, arg3, arg4))

#define CORE_LOG(FMT)                           ACE_DEBUG((LM_INFO, CORE_LOG_PREFIX FMT))
#define CORE_LOG1(FMT, arg1)                    ACE_DEBUG((LM_INFO, CORE_LOG_PREFIX FMT, arg1))
#define CORE_LOG2(FMT, arg1, arg2)              ACE_DEBUG((LM_INFO, CORE_LOG_PREFIX FMT, arg1, arg2))
#define CORE_LOG3(FMT, arg1, arg2, arg3)        ACE_DEBUG((LM_INFO, CORE_LOG_PREFIX FMT, arg1, arg2, arg3))
#define CORE_LOG4(FMT, arg1, arg2, arg3, arg4)  ACE_DEBUG((LM_INFO, CORE_LOG_PREFIX FMT, arg1, arg2, arg3, arg4))
#define CORE_TIMESTAMP()                        ACE_DEBUG((LM_INFO, CORE_LOG_PREFIX "%D\n"))

///////////////////////////////////////////////////////////////////////////////
// Utility classes
// ---------------

class TimeoutMs
{
public:
   TimeoutMs(double intervalMs) : intervalMs_(intervalMs), timer_(0)
   {
      timer_ = new ACE_High_Res_Timer();
      startTime_ = timer_->gettimeofday();
   }
   ~TimeoutMs()
   {
      delete timer_;
   }
   bool expired()
   {
      ACE_Time_Value elapsed = timer_->gettimeofday() - startTime_;
      //CORE_DEBUG2("Elapsed=%d, limit=%d\n", elapsed.usec()/1000, (long)intervalMs_);
      double elapsedMs = elapsed.sec() * 1000 + elapsed.usec() / 1000;
      if (elapsedMs > intervalMs_)
         return true;
      else
         return false;
   }

private:
   TimeoutMs(const TimeoutMs&) {}
   const TimeoutMs& operator=(const TimeoutMs&) {}

   ACE_High_Res_Timer* timer_;
   ACE_Time_Value startTime_;
   double intervalMs_;
};

class TimerMs
{
public:
   TimerMs()
   {
      startTime_ = timer_.gettimeofday();
   }
   ~TimerMs()
   {
   }
   double elapsed()
   {
      ACE_Time_Value elapsed = timer_.gettimeofday() - startTime_;
      return elapsed.sec() * 1000 + elapsed.usec() / 1000;
   }

private:
   TimerMs(const TimeoutMs&) {}
   const TimerMs& operator=(const TimeoutMs&) {}

   ACE_High_Res_Timer timer_;
   ACE_Time_Value startTime_;
};

#endif // _CORE_UTILS_H_

