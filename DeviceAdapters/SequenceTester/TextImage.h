// Mock device adapter for testing of device sequencing
//
// Copyright (C) 2014 University of California, San Francisco.
//
// This library is free software; you can redistribute it and/or modify it
// under the terms of the GNU Lesser General Public License as published by the
// Free Software Foundation.
//
// This library is distributed in the hope that it will be useful, but WITHOUT
// ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
// FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
// for more details.
//
// IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY
// DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
// You should have received a copy of the GNU Lesser General Public License
// along with this library; if not, write to the Free Software Foundation,
// Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
//
//
// Author: Mark Tsuchida

#pragma once

#include <cstddef>
#include <stdint.h>
#include <string>


class TextImageCursor
{
   uint8_t* buffer_;

   int stride_;
   int nRows_;

   int baseline_;
   int hPos_;

public:
   static const int GLYPH_HEIGHT = 5;
   static const int BASELINE_SKIP = GLYPH_HEIGHT + 4;
   static const int GLYPH_SPACING = 1;
   static const int MARGIN = 4;

public:
   TextImageCursor(uint8_t* buffer, int bufferWidth, int bufferHeight) :
      buffer_(buffer),
      stride_(bufferWidth),
      nRows_(bufferHeight),
      baseline_(MARGIN + GLYPH_HEIGHT),
      hPos_(MARGIN)
   {}

   uint8_t* GetBuffer() { return buffer_; }

   bool IsBeyondBuffer() const { return baseline_ > nRows_; }
   void NewLine() { baseline_ += BASELINE_SKIP; hPos_ = MARGIN; }
   void Space() { if (HasRoom(5)) Advance(5); else NewLine(); }
   bool HasRoom(int width) const
   {
      if (IsBeyondBuffer() || width > stride_ - 2 * MARGIN)
         return false;
      if (stride_ - hPos_ >= width + MARGIN)
         return true;
      return false;
   }
   bool MakeRoom(int width)
   {
      if (IsBeyondBuffer() || width > stride_ - 2 * MARGIN)
         return false;
      if (stride_ - hPos_ >= width + MARGIN)
         return true;
      NewLine();
      return !IsBeyondBuffer();
   }
   int GetBaselineIndex() const { return baseline_ * stride_ + hPos_; }
   int GetNorthStep() const { return -stride_; }
   int GetEastStep() const { return 1; }
   int GetWestStep() const { return -1; }
   int GetSouthStep() const { return stride_; }
   void Advance(int hDelta)
   {
      hPos_ += hDelta;
      if (hPos_ + MARGIN > stride_)
         NewLine();
   }
};


void DrawStringOnImage(TextImageCursor& cursor, const std::string& string,
      bool allowLineBreak = false);

// Draw a whole text image (no word wrapping)
void DrawTextImage(uint8_t* buffer, size_t width, size_t height,
      const std::string& text);
