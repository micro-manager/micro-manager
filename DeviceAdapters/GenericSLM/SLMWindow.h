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

#include "SLMWindowClass.h"

#include <Windows.h>

#include <string>

// A Win32 window for drawing to the SLM
class SLMWindow
{
   SLMWindowClass windowClass_;

   // The Win32 window
   HWND hWindow_;

public:
   SLMWindow(bool testMode, const std::string& title,
         DWORD x, DWORD y, DWORD w, DWORD h);
   ~SLMWindow();

   void Show();
   void Hide();

   HDC GetDC();
};
