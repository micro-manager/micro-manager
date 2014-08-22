///////////////////////////////////////////////////////////////////////////////
// FILE:          DeviceThreads.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMDevice - Device adapter kit
//-----------------------------------------------------------------------------
// DESCRIPTION:   Cross-platform wrapper class for using events in MMDevices
//
// AUTHOR:        Jeffrey Kuhn, jrkuhn@vt.edu 10/22/2009
// COPYRIGHT:     Virginia Polytechnic Institute and State University, 2009
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

#pragma once

#ifdef WIN32
#   define WIN32_LEAN_AND_MEAN
#   include <windows.h>
    typedef HANDLE MMQC_THREAD_EVENT;
#   define MMQC_THREAD_CREATE_EVENT(pevent,manual,initial)    *pevent = CreateEvent(NULL,manual,initial,NULL)
#   define MMQC_THREAD_DELETE_EVENT(pevent)                   CloseHandle(*pevent)
#else // WIN32
#   include <sys/errno.h>
#   include <pthread.h>
#   include "../../MMCore/CoreUtils.h"
    typedef pthread_mutex_t MMQC_THREAD_GUARD;
#   ifdef linux
#      define _MUTEX_RECURSIVE PTHREAD_MUTEX_RECURSIVE_NP
#   else
#      define _MUTEX_RECURSIVE PTHREAD_MUTEX_RECURSIVE
#   endif
#   define MMQC_THREAD_INITIALIZE_GUARD(plock) do { \
         pthread_mutexattr_t a; \
         pthread_mutexattr_init(&a); \
         pthread_mutexattr_settype(&a, _MUTEX_RECURSIVE); \
         pthread_mutex_init(plock, &a); \
         pthread_mutexattr_destroy(&a); \
    } while (0)
#   define MMQC_THREAD_DELETE_GUARD(plock) pthread_mutex_destroy(plock)
#   define MMQC_THREAD_GUARD_LOCK(plock) pthread_mutex_lock(plock)
#   define MMQC_THREAD_GUARD_UNLOCK(plock) pthread_mutex_unlock(plock)
    typedef pthread_cond_t MMQC_THREAD_EVENT;
#   define MMQC_THREAD_CREATE_EVENT(pevent,manual,initial)    pthread_cond_init(pevent,NULL)
#   define MMQC_THREAD_DELETE_EVENT(pevent)                   pthread_cond_destroy(pevent)
#endif

//////////////////////////////////////////////////////////////////////////////
// Global constants
//
// Infinite timeout to pass to MMEvent::Wait()
#define MM_TIMEOUT_INFINITE    (-1L)

// Values returned by MMEvent::Wait()
#define MM_WAIT_OK              0       // The event was signalled
#define MM_WAIT_TIMEOUT         1       // No event was signalled
#define MM_WAIT_FAILED          2       // A system error occurred and the
                                        //   wait terminated prematurely

/**
 * Base class for Events (aka Conditions in Linux/Unix/Mac)
 */
class MMEvent
{
private:
   MMQC_THREAD_EVENT event_;
#ifndef WIN32
   MMQC_THREAD_GUARD lock_;
   bool isSet_;
#endif
   
public:
   /**
    * Creates an event with a given initial state (either Set or Reset)
    */
   MMEvent(bool initialState)
   {
      MMQC_THREAD_CREATE_EVENT(&event_, false, initialState);
#ifndef WIN32
      MMQC_THREAD_INITIALIZE_GUARD(&lock_);
      isSet_ = initialState;
#endif
   }
   
   MMEvent()
   {
      MMQC_THREAD_CREATE_EVENT(&event_, false, false);
#ifndef WIN32
      MMQC_THREAD_INITIALIZE_GUARD(&lock_);
      isSet_ = false;
#endif
   }
   
   /** Default destructor */
   ~MMEvent()
   {
      MMQC_THREAD_DELETE_EVENT(&event_);
#ifndef WIN32
      MMQC_THREAD_DELETE_GUARD(&lock_);
#endif
   }
   
   /**
    * Signal the event
    */
   void Set() 
   {
      
#ifdef WIN32
      SetEvent(event_);
#else
      MMQC_THREAD_GUARD_LOCK(&lock_);
      pthread_cond_signal(&event_);
      isSet_ = true;
      MMQC_THREAD_GUARD_UNLOCK(&lock_);
#endif
   }
   
   /**
    * Stop signaling the event. 
    *
    * NOTE: Events are auto-reset (reset after successful wait), 
    * so Reset() will rarely explicitely be needed.
    */
   void Reset()
   {
#ifdef WIN32
      ResetEvent(event_);
#else
      MMQC_THREAD_GUARD_LOCK(&lock_);
      isSet_ = false;
      MMQC_THREAD_GUARD_UNLOCK(&lock_);
#endif
   }
   
   /**
    * Wait for an event to be signalled.
    *
    * @param msTimeout - time to wait (int msec) before returning
    *                    an error code
    */
   int Wait(long msTimeout=MM_TIMEOUT_INFINITE)
   {
#ifdef WIN32
      // wait signal
      switch (WaitForSingleObject(event_, (DWORD)msTimeout)) {
         case WAIT_OBJECT_0:
            return MM_WAIT_OK;
         case WAIT_TIMEOUT:
            return MM_WAIT_TIMEOUT;
         default:
            return MM_WAIT_FAILED;
      }
#else
      MMQC_THREAD_GUARD_LOCK(&lock_);
      if (isSet_) {
         // already signalled. Don't call pthread_cond_wait or it will lock.
         isSet_ = false;
         MMQC_THREAD_GUARD_UNLOCK(&lock_);
         return MM_WAIT_OK;
      }
      //printf("Begin wait...\n");
      int err = 0;
      if (msTimeout == MM_TIMEOUT_INFINITE) {
         // wait indefinitely
         err = pthread_cond_wait(&event_, &lock_);
      } else {
         // determine the endtime
			const long NS_PER_US = 1000;

			timeval startTime;
			gettimeofday(&startTime, 0L);

         timespec endtime;
         long msTimeoutS = msTimeout / 1000;
         long msTimeoutNS = (msTimeout % 1000) * 1000000;
			endtime.tv_sec = startTime.tv_sec + msTimeoutS;
			endtime.tv_nsec = startTime.tv_usec * NS_PER_US + msTimeoutNS;
			long overflowS = endtime.tv_nsec / 1000000000L;
			endtime.tv_sec += overflowS;
         endtime.tv_nsec %= 1000000000L;
         
			//printf("msTimeout = %ld\n",msTimeout);
			//printf("startTime: %ld s, %ld us\n",startTime.tv_sec,startTime.tv_usec);
			//printf("endtime: %ld s, %ld ns\n",endtime.tv_sec, endtime.tv_nsec);
			err = pthread_cond_timedwait(&event_, &lock_, &endtime);
         //printf("err: %ld\n",err);
         //printf("afterwardsTime: %ld s, %ld us\n",GetMMTimeNow().sec_,GetMMTimeNow().uSec_);
      }
      int result;
      if (err == 0) { // success
         isSet_ = false;
         result = MM_WAIT_OK;
      } else if (err == ETIMEDOUT) {
         result = MM_WAIT_TIMEOUT;
      } else if (err == EINVAL) {
         result = MM_WAIT_FAILED;
      } else {
         result = MM_WAIT_FAILED;
      }
      MMQC_THREAD_GUARD_UNLOCK(&lock_);
      return result;
#endif
   }
};

