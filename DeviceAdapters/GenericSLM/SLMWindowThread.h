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

#pragma once

#include "OffscreenBuffer.h"
#include "SLMWindow.h"

#include "DeviceThreads.h"

#include <Windows.h>


// A thread on which the SLM window and its corresponding offscreen buffer is
// created. A dedicated thread is required, because the thread must survive
// the window and the window must be destroyed on the thread on which it is
// created.
class SLMWindowThread : private MMDeviceThreadBase
{
   MMThreadLock lock_;
   HANDLE startedEvent_;
   DWORD threadId_;
   SLMWindow* window_;
   OffscreenBuffer* offscreen_;
   const std::string title_;
   const DWORD x_, y_, w_, h_;
   const bool testMode_;

public:
   SLMWindowThread(bool testMode, const std::string& title,
         DWORD x, DWORD y, DWORD w, DWORD h);
   virtual ~SLMWindowThread();

   void Show();
   HDC GetDC();
   OffscreenBuffer* GetOffscreenBuffer();

private:
   virtual int svc();
};
