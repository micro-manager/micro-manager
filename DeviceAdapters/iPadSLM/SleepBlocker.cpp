// COPYRIGHT:     (c) 2009-2015 Regents of the University of California
//                (c) 2016 Open Imaging, Inc.
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
// AUTHOR:        Mark Tsuchida, 2016
//                Based on older code by Arthur Edelstein, 2009

#include "SleepBlocker.h"


SleepBlocker::SleepBlocker() :
   stopRequested_(false)
{
   ZeroMemory(&cursorRect_, sizeof(cursorRect_));
}


SleepBlocker::SleepBlocker(const RECT& cursorClipRect) :
   stopRequested_(false)
{
   SetCursorRect(cursorClipRect);
}


void SleepBlocker::SetCursorRect(const RECT& cursorClipRect)
{
   MMThreadGuard g(lock_);
   cursorRect_ = cursorClipRect;
}


void SleepBlocker::Start()
{
   {
      MMThreadGuard g(lock_);
      if (stopRequested_)
         return; // Restarting not supported; ignore
   }
   activate();
}


void SleepBlocker::Stop()
{
   {
      MMThreadGuard g(lock_);
      stopRequested_ = true;
   }
   wait();
}


int SleepBlocker::svc()
{
   unsigned counter = 0;
   for (;;)
   {
      RECT cursorRect;

      {
         MMThreadGuard g(lock_);
         if (stopRequested_)
            break;
         cursorRect = cursorRect_;
      }

      if (cursorRect_.right - cursorRect_.left > 0 &&
            cursorRect_.bottom - cursorRect_.top > 0)
      {
         ::ClipCursor(&cursorRect);
      }

      if (counter > 30)
      {
         // Prevent screen saver or hibernation
         ::SetThreadExecutionState(ES_DISPLAY_REQUIRED | ES_SYSTEM_REQUIRED);

         counter = 0;
      }

      ::Sleep(1000);
      ++counter;
   }
   return 0;
}
