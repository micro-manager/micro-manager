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

#include "OffscreenBuffer.h"


OffscreenBuffer::OffscreenBuffer(HDC onscreenDC, DWORD width, DWORD height) :
   width_(width),
   height_(height),
   hDC_(0),
   hBitmap_(0),
   hOriginalBitmap_(0),
   pixels_(0)
{
   hDC_ = ::CreateCompatibleDC(onscreenDC);
   if (!hDC_)
      return;

   BITMAPINFOHEADER bitmapInfoHeader;
   ZeroMemory(&bitmapInfoHeader, sizeof(bitmapInfoHeader));
   bitmapInfoHeader.biSize = sizeof(bitmapInfoHeader);
   bitmapInfoHeader.biWidth = width_;
   // Negative height to make a "top-down" DIB, whose bytes are in the usual
   // image order
   bitmapInfoHeader.biHeight = -(LONG)height_;
   bitmapInfoHeader.biPlanes = 1;
   bitmapInfoHeader.biCompression = BI_RGB;
   bitmapInfoHeader.biBitCount = 32;

   BITMAPINFO bitmapInfo;
   ZeroMemory(&bitmapInfo, sizeof(bitmapInfo));
   bitmapInfo.bmiHeader = bitmapInfoHeader;

   hBitmap_ = ::CreateDIBSection(hDC_, &bitmapInfo, DIB_RGB_COLORS,
         &pixels_, 0, 0);
   if (!hBitmap_)
      return;

   hOriginalBitmap_ = (HBITMAP)(::SelectObject(hDC_, hBitmap_));
}


OffscreenBuffer::~OffscreenBuffer()
{
   if (hDC_ && hOriginalBitmap_)
      ::SelectObject(hDC_, hOriginalBitmap_);
   if (hBitmap_)
      ::DeleteObject(hBitmap_);
   if (hDC_)
      ::DeleteDC(hDC_);
}


void OffscreenBuffer::FillWithColor(COLORREF color)
{
   RECT rect;
   ZeroMemory(&rect, sizeof(rect));
   rect.right = width_;
   rect.bottom = height_;

   HBRUSH hBrush = ::CreateSolidBrush(color);
   ::FillRect(hDC_, &rect, hBrush);
   ::DeleteObject(hBrush);
}


void OffscreenBuffer::DrawImage(unsigned int* pixels)
{
   DWORD srcWidthBytes = width_ * 4;
   DWORD destWidthBytes = GetWidthBytes();

   if (srcWidthBytes == destWidthBytes)
   {
      memcpy(pixels_, pixels, srcWidthBytes * height_);
   }
   else
   {
      for (unsigned row = 0; row < height_; ++row)
      {
         memcpy((unsigned char*)pixels_ + row * destWidthBytes,
               (unsigned char*)pixels + row * srcWidthBytes,
               srcWidthBytes);
      }
   }
}


void OffscreenBuffer::DrawImage(unsigned char* pixels, SLMColor color, bool invert)
{
   DWORD srcWidthBytes = width_;
   DWORD destRowPadding = GetWidthBytes() - (4 * width_);

   unsigned char* pSrc = (unsigned char*)pixels;
   unsigned char* pDest = (unsigned char*)pixels_;

   unsigned char xorMask = invert ? 0xff : 0x00;
   unsigned char blueMask = (color & SLM_COLOR_BLUE) ? 0xff : 0x00;
   unsigned char greenMask = (color & SLM_COLOR_GREEN) ? 0xff : 0x00;
   unsigned char redMask = (color & SLM_COLOR_RED) ? 0xff : 0x00;

   for (unsigned row = 0; row < height_; ++row)
   {
      for (unsigned col = 0; col < width_; ++col)
      {
         unsigned char pixel = pSrc[col] ^ xorMask;

         *pDest++ = pixel & blueMask;
         *pDest++ = pixel & greenMask;
         *pDest++ = pixel & redMask;
         *pDest++ = 0; // Unused alpha channel
      }

      pSrc += srcWidthBytes;
      pDest += destRowPadding;
   }
}


void OffscreenBuffer::BlitTo(HDC onscreenDC, DWORD op)
{
   ::BitBlt(onscreenDC, 0, 0, width_, height_, hDC_, 0, 0, op);
}


DWORD OffscreenBuffer::GetWidthBytes()
{
   BITMAP bitmap;
   ::GetObject(hBitmap_, sizeof(bitmap), &bitmap);
   return bitmap.bmWidthBytes;
}
