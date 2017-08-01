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

#include "SLMColor.h"

#include <Windows.h>


class OffscreenBuffer
{
   const DWORD width_;
   const DWORD height_;
   HDC hDC_;
   HBITMAP hBitmap_;
   HBITMAP hOriginalBitmap_;

   void* pixels_; // Memory managed by hBitmap_

public:
   OffscreenBuffer(HDC onscreenDC, DWORD width, DWORD height);
   ~OffscreenBuffer();

   DWORD GetWidth() const { return width_; }
   DWORD GetHeight() const { return height_; }

   void FillWithColor(COLORREF color);
   void DrawImage(unsigned int* pixels); // RGBA
   void DrawImage(unsigned char* pixels, SLMColor color, bool invert); // Gray8
   void BlitTo(HDC onscreenDC, DWORD op);

private:
   DWORD GetWidthBytes();

private:
   OffscreenBuffer& operator=(const OffscreenBuffer&);
};
