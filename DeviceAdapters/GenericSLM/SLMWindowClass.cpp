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

#include "SLMWindowClass.h"

#include <boost/lexical_cast.hpp>


static LRESULT CALLBACK
EmptyWindowProcedureA(HWND hWindow, unsigned int message, WPARAM wParam, LPARAM lParam)
{
   switch (message)
   {
      case WM_DESTROY:
         ::PostQuitMessage(0);
         return 0;
   }
   return ::DefWindowProcA(hWindow, message, wParam, lParam);
}


SLMWindowClass::SLMWindowClass()
{
   // Create a 1-by-1-pixel invisible cursor
   const BYTE andMask[1] = { 1 };
   const BYTE xorMask[1] = { 0 };
   hInvisibleCursor_ = ::CreateCursor(0, 0, 0, 1, 1, andMask, xorMask);

   className_ = GetClassPrefix() + boost::lexical_cast<std::string>(this);

   ZeroMemory(&windowClass_, sizeof(windowClass_));
   windowClass_.lpfnWndProc = EmptyWindowProcedureA;
   windowClass_.hCursor = hInvisibleCursor_;
   // Set background to 50% gray, so that we can distinguish it from black (for
   // debugging purposes). The brush is destroyed by UnregisterClass().
   windowClass_.hbrBackground = ::CreateSolidBrush(RGB(0x7f, 0x7f, 0x7f));
   windowClass_.lpszClassName = className_.c_str();

   ::RegisterClassA(&windowClass_);
}


SLMWindowClass::~SLMWindowClass()
{
   ::UnregisterClassA(windowClass_.lpszClassName, windowClass_.hInstance);
   ::DestroyCursor(hInvisibleCursor_);
}