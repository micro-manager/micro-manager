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

#include "TextImage.h"

#include <algorithm>
#include <string>


namespace
{

typedef uint8_t Pixel;
const int GLYPH_HEIGHT = TextImageCursor::GLYPH_HEIGHT;
const int GLYPH_SPACING = TextImageCursor::GLYPH_SPACING;


inline Pixel
SaturatePixel(Pixel p)
{
   return p ? 255 : 0;
}


class Glyph
{
   const Pixel* pixmap_;
   int width_;

   Glyph(const Pixel* pixmap, int pixmapSize) :
      width_(pixmapSize / sizeof(Pixel) / GLYPH_HEIGHT)
   {
      Pixel* saturated = new Pixel[pixmapSize];
      std::transform(pixmap, pixmap + pixmapSize, saturated, SaturatePixel);
      pixmap_ = saturated;
   }

private:
   static const Glyph* map_[256]; // ASCII-to-glyph lookup table

   static size_t CharIndex(char ch)
   { return static_cast<size_t>(static_cast<int>(ch) & 0xff); }

   static const Glyph* GetGlyph(char ch)
   {
      const Glyph* ret = map_[CharIndex(ch)];
      if (!ret)
         return map_[0];
      return ret;
   }

   friend class GlyphDef;

private:
   void Draw(TextImageCursor& cursor, bool knownToFit) const;

public:
   ~Glyph() { delete[] pixmap_; }

   int GetWidth() const { return width_; }
   void Draw(TextImageCursor& cursor) const
   { Draw(cursor, false); }

public:
   static int GetStringWidth(const std::string& s);
   static void DrawString(const std::string& s, TextImageCursor& cursor,
         bool allowLineBreak);
};

const Glyph* Glyph::map_[256];


class GlyphDef
{
public:
   GlyphDef(char ch, const Pixel* pixmap, int pixmapSize)
   { Glyph::map_[Glyph::CharIndex(ch)] = new Glyph(pixmap, pixmapSize); }
};


void
Glyph::Draw(TextImageCursor& cursor, bool knownToFit) const
{
   int glyphWidth = GetWidth();
   if (knownToFit || cursor.MakeRoom(glyphWidth))
   {
      int southWest = cursor.GetBaselineIndex();
      int northWest = southWest + GLYPH_HEIGHT * cursor.GetNorthStep();
      int pixelIndex = 0;
      for (int rowStart = northWest; rowStart != southWest;
            rowStart += cursor.GetSouthStep())
      {
         int rowEnd = rowStart + glyphWidth * cursor.GetEastStep();
         for (int pos = rowStart; pos < rowEnd; ++pos)
         {
            cursor.GetBuffer()[pos] = pixmap_[pixelIndex++];
         }
      }
      cursor.Advance(glyphWidth + GLYPH_SPACING);
   }
}


int
Glyph::GetStringWidth(const std::string& s)
{
   int width = 0;
   for (std::string::const_iterator it = s.begin(), end = s.end();
         it != end; ++it)
   {
      width += GetGlyph(*it)->GetWidth();
      width += GLYPH_SPACING;
   }
   return width - GLYPH_SPACING;
}


void
Glyph::DrawString(const std::string& s, TextImageCursor& cursor,
      bool allowLineBreak)
{
   int width = GetStringWidth(s);
   bool allWillFitInCurrentLine = cursor.HasRoom(width);
   if (!allWillFitInCurrentLine && !allowLineBreak)
   {
      cursor.NewLine();
      allWillFitInCurrentLine = cursor.HasRoom(width);
   }
   for (std::string::const_iterator it = s.begin(), end = s.end();
         it != end; ++it)
   {
      GetGlyph(*it)->Draw(cursor, allWillFitInCurrentLine);
   }
}

} // anonymous namespace


void
DrawStringOnImage(TextImageCursor& cursor,
      const std::string& string, bool allowLineBreak)
{
   Glyph::DrawString(string, cursor, allowLineBreak);
}


void
DrawTextImage(uint8_t* buffer, size_t width, size_t height,
      const std::string& text)
{
   TextImageCursor cursor(buffer, width, height);
   Glyph::DrawString(text, cursor, true);
}


namespace TextImageFont
{

// Glyphs are in ASCII order below (except for lowercase letters, which use the
// uppercase glyphs).

// Not all characters are defined. All missing characters are drawn using the
// '\0' glyph.
const Pixel glyph_null[GLYPH_HEIGHT * 4] = {
   1,0,1,0,
   0,1,0,1,
   1,0,1,0,
   0,1,0,1,
   1,0,1,0,
};
GlyphDef def_noglyph('\0', glyph_null, sizeof(glyph_null));

const Pixel glyph_space[GLYPH_HEIGHT * 4] = {
   0,0,0,0,
   0,0,0,0,
   0,0,0,0,
   0,0,0,0,
   0,0,0,0,
};
GlyphDef def_space(' ', glyph_space, sizeof(glyph_space));

const Pixel glyph_leftparen[GLYPH_HEIGHT * 3] = {
   0,0,1,
   0,1,0,
   0,1,0,
   0,1,0,
   0,0,1,
};
GlyphDef def_leftparen('(', glyph_leftparen, sizeof(glyph_leftparen));

const Pixel glyph_rightparen[GLYPH_HEIGHT * 3] = {
   1,0,0,
   0,1,0,
   0,1,0,
   0,1,0,
   1,0,0,
};
GlyphDef def_rightparen(')', glyph_rightparen, sizeof(glyph_rightparen));

const Pixel glyph_plus[GLYPH_HEIGHT * 3] = {
   0,0,0,
   0,1,0,
   1,1,1,
   0,1,0,
   0,0,0,
};
GlyphDef def_plus('+', glyph_plus, sizeof(glyph_plus));

const Pixel glyph_comma[GLYPH_HEIGHT * 2] = {
   0,0,
   0,0,
   0,0,
   0,1,
   1,0,
};
GlyphDef def_comma(',', glyph_comma, sizeof(glyph_comma));

const Pixel glyph_minus[GLYPH_HEIGHT * 3] = {
   0,0,0,
   0,0,0,
   1,1,1,
   0,0,0,
   0,0,0,
};
GlyphDef def_minus('-', glyph_minus, sizeof(glyph_minus));

const Pixel glyph_period[GLYPH_HEIGHT * 1] = {
   0,
   0,
   0,
   0,
   1,
};
GlyphDef def_period('.', glyph_period, sizeof(glyph_period));

const Pixel glyph_0[GLYPH_HEIGHT * 3] = {
   0,1,0,
   1,0,1,
   1,1,1,
   1,0,1,
   0,1,0,
};
GlyphDef def_0('0', glyph_0, sizeof(glyph_0));

const Pixel glyph_1[GLYPH_HEIGHT * 3] = {
   0,1,0,
   1,1,0,
   0,1,0,
   0,1,0,
   0,1,0,
};
GlyphDef def_1('1', glyph_1, sizeof(glyph_1));

const Pixel glyph_2[GLYPH_HEIGHT * 3] = {
   0,1,0,
   1,0,1,
   0,0,1,
   0,1,0,
   1,1,1,
};
GlyphDef def_2('2', glyph_2, sizeof(glyph_2));

const Pixel glyph_3[GLYPH_HEIGHT * 3] = {
   1,1,0,
   0,0,1,
   1,1,0,
   0,0,1,
   1,1,0,
};
GlyphDef def_3('3', glyph_3, sizeof(glyph_3));

const Pixel glyph_4[GLYPH_HEIGHT * 3] = {
   1,0,1,
   1,0,1,
   1,1,1,
   0,0,1,
   0,0,1,
};
GlyphDef def_4('4', glyph_4, sizeof(glyph_4));

const Pixel glyph_5[GLYPH_HEIGHT * 3] = {
   1,1,1,
   1,0,0,
   1,1,0,
   0,0,1,
   1,1,0,
};
GlyphDef def_5('5', glyph_5, sizeof(glyph_5));

const Pixel glyph_6[GLYPH_HEIGHT * 3] = {
   0,1,1,
   1,0,0,
   1,1,0,
   1,0,1,
   0,1,0,
};
GlyphDef def_6('6', glyph_6, sizeof(glyph_6));

const Pixel glyph_7[GLYPH_HEIGHT * 3] = {
   1,1,1,
   0,0,1,
   0,0,1,
   0,1,0,
   1,0,0,
};
GlyphDef def_7('7', glyph_7, sizeof(glyph_7));

const Pixel glyph_8[GLYPH_HEIGHT * 3] = {
   0,1,0,
   1,0,1,
   0,1,0,
   1,0,1,
   0,1,0,
};
GlyphDef def_8('8', glyph_8, sizeof(glyph_8));

const Pixel glyph_9[GLYPH_HEIGHT * 3] = {
   0,1,0,
   1,0,1,
   0,1,1,
   0,0,1,
   1,1,0,
};
GlyphDef def_9('9', glyph_9, sizeof(glyph_9));

const Pixel glyph_colon[GLYPH_HEIGHT * 2] = {
   0,0,
   1,0,
   0,0,
   1,0,
   0,0,
};
GlyphDef def_colon(':', glyph_colon, sizeof(glyph_colon));

const Pixel glyph_semicolon[GLYPH_HEIGHT * 3] = {
   0,0,0,
   0,1,0,
   0,0,0,
   0,1,0,
   1,0,0,
};
GlyphDef def_semicolon(';', glyph_semicolon, sizeof(glyph_semicolon));

const Pixel glyph_equals[GLYPH_HEIGHT * 4] = {
   0,0,0,0,
   1,1,1,1,
   0,0,0,0,
   1,1,1,1,
   0,0,0,0,
};
GlyphDef def_equals('=', glyph_equals, sizeof(glyph_equals));

const Pixel glyph_leftbracket[GLYPH_HEIGHT * 3] = {
   0,1,1,
   0,1,0,
   0,1,0,
   0,1,0,
   0,1,1,
};
GlyphDef def_leftbracket('[', glyph_leftbracket, sizeof(glyph_leftbracket));

const Pixel glyph_rightbracket[GLYPH_HEIGHT * 3] = {
   1,1,0,
   0,1,0,
   0,1,0,
   0,1,0,
   1,1,0,
};
GlyphDef def_rightbracket(']', glyph_rightbracket, sizeof(glyph_rightbracket));

const Pixel glyph_A[GLYPH_HEIGHT * 4] = {
   0,1,1,0,
   1,0,0,1,
   1,1,1,1,
   1,0,0,1,
   1,0,0,1,
};
GlyphDef def_A('A', glyph_A, sizeof(glyph_A));
GlyphDef def_a('a', glyph_A, sizeof(glyph_A));

const Pixel glyph_B[GLYPH_HEIGHT * 4] = {
   1,1,1,0,
   1,0,0,1,
   1,1,1,0,
   1,0,0,1,
   1,1,1,0,
};
GlyphDef def_B('B', glyph_B, sizeof(glyph_B));
GlyphDef def_b('b', glyph_B, sizeof(glyph_B));

const Pixel glyph_C[GLYPH_HEIGHT * 4] = {
   0,1,1,1,
   1,0,0,0,
   1,0,0,0,
   1,0,0,0,
   0,1,1,1,
};
GlyphDef def_C('C', glyph_C, sizeof(glyph_C));
GlyphDef def_c('c', glyph_C, sizeof(glyph_C));

const Pixel glyph_D[GLYPH_HEIGHT * 4] = {
   1,1,1,0,
   1,0,0,1,
   1,0,0,1,
   1,0,0,1,
   1,1,1,0,
};
GlyphDef def_D('D', glyph_D, sizeof(glyph_D));
GlyphDef def_d('d', glyph_D, sizeof(glyph_D));

const Pixel glyph_E[GLYPH_HEIGHT * 4] = {
   1,1,1,1,
   1,0,0,0,
   1,1,1,0,
   1,0,0,0,
   1,1,1,1,
};
GlyphDef def_E('E', glyph_E, sizeof(glyph_E));
GlyphDef def_e('e', glyph_E, sizeof(glyph_E));

const Pixel glyph_F[GLYPH_HEIGHT * 4] = {
   1,1,1,1,
   1,0,0,0,
   1,1,1,0,
   1,0,0,0,
   1,0,0,0,
};
GlyphDef def_F('F', glyph_F, sizeof(glyph_F));
GlyphDef def_f('f', glyph_F, sizeof(glyph_F));

const Pixel glyph_G[GLYPH_HEIGHT * 4] = {
   0,1,1,1,
   1,0,0,0,
   1,0,1,1,
   1,0,0,1,
   0,1,1,0,
};
GlyphDef def_G('G', glyph_G, sizeof(glyph_G));
GlyphDef def_g('g', glyph_G, sizeof(glyph_G));

const Pixel glyph_H[GLYPH_HEIGHT * 4] = {
   1,0,0,1,
   1,0,0,1,
   1,1,1,1,
   1,0,0,1,
   1,0,0,1,
};
GlyphDef def_H('H', glyph_H, sizeof(glyph_H));
GlyphDef def_h('h', glyph_H, sizeof(glyph_H));

const Pixel glyph_I[GLYPH_HEIGHT * 3] = {
   1,1,1,
   0,1,0,
   0,1,0,
   0,1,0,
   1,1,1,
};
GlyphDef def_I('I', glyph_I, sizeof(glyph_I));
GlyphDef def_i('i', glyph_I, sizeof(glyph_I));

const Pixel glyph_J[GLYPH_HEIGHT * 4] = {
   1,1,1,1,
   0,0,1,0,
   0,0,1,0,
   0,0,1,0,
   1,1,0,0,
};
GlyphDef def_J('J', glyph_J, sizeof(glyph_J));
GlyphDef def_j('j', glyph_J, sizeof(glyph_J));

const Pixel glyph_K[GLYPH_HEIGHT * 4] = {
   1,0,0,1,
   1,0,1,0,
   1,1,0,0,
   1,0,1,0,
   1,0,0,1,
};
GlyphDef def_K('K', glyph_K, sizeof(glyph_K));
GlyphDef def_k('k', glyph_K, sizeof(glyph_K));

const Pixel glyph_L[GLYPH_HEIGHT * 4] = {
   1,0,0,0,
   1,0,0,0,
   1,0,0,0,
   1,0,0,0,
   1,1,1,1,
};
GlyphDef def_L('L', glyph_L, sizeof(glyph_L));
GlyphDef def_l('l', glyph_L, sizeof(glyph_L));

const Pixel glyph_M[GLYPH_HEIGHT * 5] = {
   1,0,0,0,1,
   1,1,0,1,1,
   1,0,1,0,1,
   1,0,0,0,1,
   1,0,0,0,1,
};
GlyphDef def_M('M', glyph_M, sizeof(glyph_M));
GlyphDef def_m('m', glyph_M, sizeof(glyph_M));

const Pixel glyph_N[GLYPH_HEIGHT * 4] = {
   1,0,0,1,
   1,1,0,1,
   1,0,1,1,
   1,0,0,1,
   1,0,0,1,
};
GlyphDef def_N('N', glyph_N, sizeof(glyph_N));
GlyphDef def_n('n', glyph_N, sizeof(glyph_N));

const Pixel glyph_O[GLYPH_HEIGHT * 4] = {
   0,1,1,0,
   1,0,0,1,
   1,0,0,1,
   1,0,0,1,
   0,1,1,0,
};
GlyphDef def_O('O', glyph_O, sizeof(glyph_O));
GlyphDef def_o('o', glyph_O, sizeof(glyph_O));

const Pixel glyph_P[GLYPH_HEIGHT * 4] = {
   1,1,1,0,
   1,0,0,1,
   1,1,1,0,
   1,0,0,0,
   1,0,0,0,
};
GlyphDef def_P('P', glyph_P, sizeof(glyph_P));
GlyphDef def_p('p', glyph_P, sizeof(glyph_P));

const Pixel glyph_Q[GLYPH_HEIGHT * 5] = {
   0,1,1,0,0,
   1,0,0,1,0,
   1,0,0,1,0,
   1,0,1,1,0,
   0,1,1,1,1,
};
GlyphDef def_Q('Q', glyph_Q, sizeof(glyph_Q));
GlyphDef def_q('q', glyph_Q, sizeof(glyph_Q));

const Pixel glyph_R[GLYPH_HEIGHT * 4] = {
   1,1,1,0,
   1,0,0,1,
   1,1,1,0,
   1,0,1,0,
   1,0,0,1,
};
GlyphDef def_R('R', glyph_R, sizeof(glyph_R));
GlyphDef def_r('r', glyph_R, sizeof(glyph_R));

const Pixel glyph_S[GLYPH_HEIGHT * 4] = {
   0,1,1,1,
   1,0,0,0,
   0,1,1,0,
   0,0,0,1,
   1,1,1,0,
};
GlyphDef def_S('S', glyph_S, sizeof(glyph_S));
GlyphDef def_s('s', glyph_S, sizeof(glyph_S));

const Pixel glyph_T[GLYPH_HEIGHT * 5] = {
   1,1,1,1,1,
   0,0,1,0,0,
   0,0,1,0,0,
   0,0,1,0,0,
   0,0,1,0,0,
};
GlyphDef def_T('T', glyph_T, sizeof(glyph_T));
GlyphDef def_t('t', glyph_T, sizeof(glyph_T));

const Pixel glyph_U[GLYPH_HEIGHT * 4] = {
   1,0,0,1,
   1,0,0,1,
   1,0,0,1,
   1,0,0,1,
   0,1,1,0,
};
GlyphDef def_U('U', glyph_U, sizeof(glyph_U));
GlyphDef def_u('u', glyph_U, sizeof(glyph_U));

const Pixel glyph_V[GLYPH_HEIGHT * 4] = {
   1,0,0,1,
   1,0,0,1,
   1,0,0,1,
   0,1,0,1,
   0,0,1,0,
};
GlyphDef def_V('V', glyph_V, sizeof(glyph_V));
GlyphDef def_v('v', glyph_V, sizeof(glyph_V));

const Pixel glyph_W[GLYPH_HEIGHT * 5] = {
   1,0,0,0,1,
   1,0,1,0,1,
   1,0,1,0,1,
   1,0,1,0,1,
   0,1,0,1,0,
};
GlyphDef def_W('W', glyph_W, sizeof(glyph_W));
GlyphDef def_w('w', glyph_W, sizeof(glyph_W));

const Pixel glyph_X[GLYPH_HEIGHT * 4] = {
   1,0,0,1,
   1,0,0,1,
   0,1,1,0,
   1,0,0,1,
   1,0,0,1,
};
GlyphDef def_X('X', glyph_X, sizeof(glyph_X));
GlyphDef def_x('x', glyph_X, sizeof(glyph_X));

const Pixel glyph_Y[GLYPH_HEIGHT * 4] = {
   1,0,0,1,
   0,1,0,1,
   0,0,1,0,
   0,0,1,0,
   0,0,1,0,
};
GlyphDef def_Y('Y', glyph_Y, sizeof(glyph_Y));
GlyphDef def_y('y', glyph_Y, sizeof(glyph_Y));

const Pixel glyph_Z[GLYPH_HEIGHT * 4] = {
   1,1,1,1,
   0,0,1,0,
   0,1,0,0,
   1,0,0,0,
   1,1,1,1,
};
GlyphDef def_Z('Z', glyph_Z, sizeof(glyph_Z));
GlyphDef def_z('z', glyph_Z, sizeof(glyph_Z));

const Pixel glyph_underscore[GLYPH_HEIGHT * 4] = {
   0,0,0,0,
   0,0,0,0,
   0,0,0,0,
   0,0,0,0,
   1,1,1,1,
};
GlyphDef def_underscore('_', glyph_underscore, sizeof(glyph_underscore));

} // namespace TextImageFont
