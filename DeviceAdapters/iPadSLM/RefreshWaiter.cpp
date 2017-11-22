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

#include "RefreshWaiter.h"


RefreshWaiter::RefreshWaiter()
{
   hDDrawLib_ = LoadLibraryA("ddraw.dll");
   if (!hDDrawLib_)
      return;

   directDrawCreate_ = (DirectDrawCreateFunc)GetProcAddress(hDDrawLib_,
            "DirectDrawCreate");
   if (!directDrawCreate_)
      return;

   HRESULT hr = directDrawCreate_(0, &directDraw_, 0);
   if (hr != DD_OK)
   {
      directDraw_ = 0;
      return;
   }
}


RefreshWaiter::~RefreshWaiter()
{
   if (directDraw_)
      directDraw_->Release();

   if (hDDrawLib_)
      FreeLibrary(hDDrawLib_);
}


void RefreshWaiter::WaitForVerticalBlank()
{
   if (!directDraw_)
      return;

   directDraw_->WaitForVerticalBlank(DDWAITVB_BLOCKBEGIN, 0);
}
