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

#include "SLMWindow.h"


SLMWindow::SLMWindow(const std::string& title, DWORD x, DWORD y, DWORD w, DWORD h)
{
   hWindow_ = ::CreateWindowExA(
         WS_EX_NOPARENTNOTIFY,
         windowClass_.GetClassName().c_str(),
         title.c_str(),
         WS_POPUP | WS_CLIPCHILDREN | WS_CLIPSIBLINGS,
         x, y, w, h, 0, 0, 0, 0);
}


SLMWindow::~SLMWindow()
{
   if (hWindow_)
      ::DestroyWindow(hWindow_);
}


void SLMWindow::Show()
{
   if (!hWindow_)
      return;

   ::ShowWindow(hWindow_, SW_SHOWNORMAL);
   ::SetWindowPos(hWindow_, HWND_TOPMOST,
         0, 0, 0, 0, SWP_NOMOVE | SWP_NOSIZE);
   ::UpdateWindow(hWindow_);
}


void SLMWindow::Hide()
{
   if (!hWindow_)
      return;

   ::ShowWindow(hWindow_, SW_HIDE);
}


HDC SLMWindow::GetDC()
{
   if (!hWindow_)
      return 0;
   return ::GetDC(hWindow_);
}
