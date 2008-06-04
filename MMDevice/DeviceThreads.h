///////////////////////////////////////////////////////////////////////////////
// FILE:          DeviceThreads.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMDevice - Device adapter kit
//-----------------------------------------------------------------------------
// DESCRIPTION:   Cross-platform wrapper class for using threads in MMDevices
//
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com 11/27/2007
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
// CVS:           $Id: DeviceUtils.h 393 2007-07-26 00:06:54Z nenad $
//

#pragma once

#ifdef WIN32
   #define WIN32_LEAN_AND_MEAN
   #include <windows.h>
   #define MM_THREAD_HANDLE HANDLE
   #define MM_THREAD_JOIN(thd) WaitForSingleObject(thd, INFINITE)
   #define MM_THREAD_GUARD CRITICAL_SECTION
   #define MM_THREAD_INITIALIZE_GUARD(plock) InitializeCriticalSection(plock)
   #define MM_THREAD_DELETE_GUARD(plock) DeleteCriticalSection(plock)
   #define MM_THREAD_GUARD_LOCK(plock) EnterCriticalSection(plock);
   #define MM_THREAD_GUARD_UNLOCK(plock) LeaveCriticalSection(plock);
   #define MM_THREAD_CREATE(pthd, threadFunc, param) DWORD id; *pthd = CreateThread(NULL, 0, threadFunc, param, 0, &id) 
   #define MM_THREAD_FUNC_DECL DWORD WINAPI
   #define MM_THREAD_FUNC_RETURN_TYPE DWORD
#else
   #include <pthread.h>
   #define MM_THREAD_HANDLE pthread_t
   #define MM_THREAD_JOIN(thd) pthread_join(thd, NULL)
   #define MM_THREAD_GUARD pthread_mutex_t
   #define MM_THREAD_INITIALIZE_GUARD(plock) pthread_mutex_init(plock, NULL)
   #define MM_THREAD_DELETE_GUARD(plock) pthread_mutex_destroy(plock)
   #define MM_THREAD_GUARD_LOCK(plock) pthread_mutex_lock(plock);
   #define MM_THREAD_GUARD_UNLOCK(plock) pthread_mutex_unlock(plock);
   #define MM_THREAD_CREATE(pthd, threadFunc, param) pthread_create(pthd, NULL, threadFunc, param)
   #define MM_THREAD_FUNC_DECL void*
   #define MM_THREAD_FUNC_RETURN_TYPE void*
#endif

/**
 * Base class for threads in MM devices
 */
class MMDeviceThreadBase
{
public:
   MMDeviceThreadBase() : thread_(0) {}
   virtual ~MMDeviceThreadBase() {}

   virtual int svc() = 0;

   virtual int activate()
   {
      MM_THREAD_CREATE(&thread_, ThreadProc, this);
      return 0; // TODO: return thread id
   };
   void wait() {MM_THREAD_JOIN(thread_);}

private:
   MM_THREAD_HANDLE thread_;
   static MM_THREAD_FUNC_DECL ThreadProc(void* param)
   {
      MMDeviceThreadBase* pThrObj = (MMDeviceThreadBase*) param;
   #ifdef WIN32
      return pThrObj->svc();
   #else
      pThrObj->svc();
      return (void*) 0;
   #endif
   }
};

/**
 * Critical section lock.
 */
class MMThreadLock
{
public:
   MMThreadLock()
   {
      MM_THREAD_INITIALIZE_GUARD(&lock_);
   }

   ~MMThreadLock()
   {
      MM_THREAD_DELETE_GUARD(&lock_);
   }

   void Lock() {MM_THREAD_GUARD_LOCK(&lock_)};
   void Unlock() {MM_THREAD_GUARD_UNLOCK(&lock_)};


private:
   MM_THREAD_GUARD lock_;
};

class MMThreadGuard
{
public:
   MMThreadGuard(MMThreadLock& lock) : lock_(lock) {lock_.Lock();}
   ~MMThreadGuard() {lock_.Unlock();}

private:
   MMThreadGuard& operator=(MMThreadGuard& /*rhs*/) {assert(false); return *this;}
   MMThreadLock& lock_;
};
