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

#pragma once

#ifdef WIN32
   #define WIN32_LEAN_AND_MEAN
   #include <windows.h>
#else
   #include <pthread.h>
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
#ifdef _WIN32
      DWORD id;
      thread_ = CreateThread(NULL, 0, ThreadProc, this, 0, &id);
#else
      pthread_create(&thread_, NULL, ThreadProc, this);
#endif
      return 0; // TODO: return thread id
   }

   void wait()
   {
#ifdef _WIN32
      WaitForSingleObject(thread_, INFINITE);
#else
      pthread_join(thread_, NULL);
#endif
   }

private:
   // Forbid copying
   MMDeviceThreadBase(const MMDeviceThreadBase&);
   MMDeviceThreadBase& operator=(const MMDeviceThreadBase&);

#ifdef _WIN32
   HANDLE
#else
   pthread_t
#endif
   thread_;

   static
#ifdef _WIN32
   DWORD WINAPI
#else
   void*
#endif
   ThreadProc(void* param)
   {
      MMDeviceThreadBase* pThrObj = (MMDeviceThreadBase*) param;
#ifdef _WIN32
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
#ifdef _WIN32
      InitializeCriticalSection(&lock_);
#else
      pthread_mutexattr_t a;
      pthread_mutexattr_init(&a);
      pthread_mutexattr_settype(&a,
#ifdef __linux__
         // Not sure if _NP is needed any more
         PTHREAD_MUTEX_RECURSIVE_NP
#else
         PTHREAD_MUTEX_RECURSIVE
#endif
      );
      pthread_mutex_init(&lock_, &a);
      pthread_mutexattr_destroy(&a);
#endif
   }

   ~MMThreadLock()
   {
#ifdef _WIN32
      DeleteCriticalSection(&lock_);
#else
      pthread_mutex_destroy(&lock_);
#endif
   }

   void Lock()
   {
#ifdef _WIN32
      EnterCriticalSection(&lock_);
#else
      pthread_mutex_lock(&lock_);
#endif
   }

   void Unlock()
   {
#ifdef _WIN32
      LeaveCriticalSection(&lock_);
#else
      pthread_mutex_unlock(&lock_);
#endif
   }

private:
   // Forbid copying
   MMThreadLock(const MMThreadLock&);
   MMThreadLock& operator=(const MMThreadLock&);

#ifdef _WIN32
   CRITICAL_SECTION
#else
   pthread_mutex_t
#endif
   lock_;
};

class MMThreadGuard
{
public:
   MMThreadGuard(MMThreadLock& lock) : lock_(&lock)
   {
      lock_->Lock();
   }

   MMThreadGuard(MMThreadLock* lock) : lock_(lock)
   {
      if (lock != 0)
         lock_->Lock();
   }

   bool isLocked() {return lock_ == 0 ? false : true;}

   ~MMThreadGuard()
   {
      if (lock_ != 0)
         lock_->Unlock();
   }

private:
   // Forbid copying
   MMThreadGuard(const MMThreadGuard&);
   MMThreadGuard& operator=(const MMThreadGuard&);

   MMThreadLock* lock_;
};
