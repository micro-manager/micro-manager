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

#include "DeviceThreads.h"

#include <Windows.h>


class SleepBlocker : public MMDeviceThreadBase
{
   MMThreadLock lock_;
   bool stopRequested_;
   RECT cursorRect_;

public:
   SleepBlocker();
   SleepBlocker(const RECT& cursorClipRect);
   virtual void SetCursorRect(const RECT& cursorClipRect);
   virtual void Start();
   virtual void Stop();

private:
   virtual int svc();
};
