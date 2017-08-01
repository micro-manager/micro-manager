// DESCRIPTION:   iPadSLM device adapter
// COPYRIGHT:     2009-2016 Regents of the University of California
//                2016 Open Imaging, Inc.
//
// AUTHOR:        Arthur Edelstein, arthuredelstein@gmail.com, 3/17/2009
//                Mark Tsuchida (refactor/rewrite), 2016
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

#include "SLMWindowThread.h"


SLMWindowThread::SLMWindowThread(bool testMode, const std::string& title,
      DWORD x, DWORD y, DWORD w, DWORD h) :
   threadId_(0),
   window_(0),
   offscreen_(0),
   title_(title),
   x_(x),
   y_(y),
   w_(w),
   h_(h),
   testMode_(testMode)
{
   startedEvent_ = ::CreateEventA(0, FALSE, FALSE, 0);
   activate();
   ::WaitForSingleObject(startedEvent_, INFINITE);
}


SLMWindowThread::~SLMWindowThread()
{
   bool stillRunning;
   {
      MMThreadGuard g(lock_);
      stillRunning = (threadId_ != 0);
   }
   if (stillRunning)
   {
      BOOL status = ::PostThreadMessageA(threadId_, WM_QUIT, 0, 0);
      if (status)
         wait();
   }
   ::CloseHandle(startedEvent_);
}


void SLMWindowThread::Show()
{
   MMThreadGuard g(lock_);
   window_->Show();
}


HDC SLMWindowThread::GetDC()
{
   MMThreadGuard g(lock_);
   return window_->GetDC();
}


OffscreenBuffer* SLMWindowThread::GetOffscreenBuffer()
{
   MMThreadGuard g(lock_);
   return offscreen_;
}


int SLMWindowThread::svc()
{
   {
      MMThreadGuard g(lock_);
      threadId_ = GetCurrentThreadId();
      window_ = new SLMWindow(testMode_, title_, x_, y_, w_, h_);
      offscreen_ = new OffscreenBuffer(window_->GetDC(), w_, h_);
   }

   MSG message;

   // Ensure that the message loop is created
   ::PeekMessageA(&message, 0, 0, 0, PM_NOREMOVE);

   // Notify constructor thread that we're ready
   ::SetEvent(startedEvent_);

   // We need to run a Windows message loop to handle window events
   while (BOOL status = ::GetMessageA(&message, NULL, 0, 0)) {
      if (status == -1)
         break; // Not expected

      ::DispatchMessageA(&message);
   }

   {
      MMThreadGuard g(lock_);
      delete offscreen_;
      offscreen_ = 0;
      delete window_;
      window_ = 0;
      threadId_ = 0;
   }
   return 0;
}
