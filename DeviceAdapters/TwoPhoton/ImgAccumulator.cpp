///////////////////////////////////////////////////////////////////////////////
// MODULE:			ImgAccumulator.cpp
// SYSTEM:        100X Imaging base utilities
// AUTHOR:			Nenad Amodaj
//
// DESCRIPTION:	Basic implementation for the accumultor data structure (frame averaging).
//
// COPYRIGHT:     Nenad Amodaj 2011, 100X Imaging Inc 2009
//
// LICENSE:       This library is free software; you can redistribute it and/or
//                modify it under the terms of the GNU Lesser General Public
//                License as published by the Free Software Foundation.
//                
//                You should have received a copy of the GNU Lesser General Public
//                License along with the source distribution; if not, write to
//                the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
//                Boston, MA  02111-1307  USA
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.  
//                
// AUTHOR:        Nenad Amodaj, November 2009
//
///////////////////////////////////////////////////////////////////////////////

#include "ImgAccumulator.h"
#include <math.h>
#include <assert.h>
using namespace std;

///////////////////////////////////////////////////////////////////////////////
// ImgAccumulator class
//
ImgAccumulator::ImgAccumulator(unsigned xSize, unsigned ySize, unsigned pixDepth, unsigned length) :
   pixels_(0), width_(xSize), height_(ySize), pixDepth_(pixDepth), length_(length), enabled_(true)
{
   // setup image buffer
   pixels_ = new unsigned char[xSize * ySize * pixDepth];
   assert(pixels_);
   memset(pixels_, 0, xSize * ySize * pixDepth);

   // setup accumulator
   accumulator_.resize(xSize * ySize, 0.0);
   numFrames_ = 0;
}

ImgAccumulator::ImgAccumulator() :
   pixels_(0), width_(0), height_(0), pixDepth_(0), length_(1)
{
   numFrames_ = 0;
}

ImgAccumulator::~ImgAccumulator()
{
   delete[] pixels_;
}

const unsigned char* ImgAccumulator::GetPixels() const
{
   return pixels_;
}

void ImgAccumulator::SetPixels(const void* pix, unsigned sourceWidth, unsigned offsetX, unsigned offsetY)
{
   long size = width_ * height_;
   if (length_ == 0)
   {
      memset(pixels_, 0, width_ * height_ * pixDepth_);
      return;
   }

   numFrames_++;
   if (numFrames_ > length_)
      numFrames_ = length_;

   double factor = (double)(numFrames_ - 1) / numFrames_;
   double lenInv = 1.0 / numFrames_;

   if (pixDepth_ == 1)
   { 
      const unsigned char* pixPtr = static_cast<const unsigned char*>(pix);
      for (unsigned i=0; i<height_; i++)
      {
         for (unsigned j=0; j<width_; j++)
         {
            int idx = i*width_+j;
            int srcIdx = (offsetY+i)*sourceWidth+j;
            accumulator_[idx] = lenInv * pixPtr[srcIdx] + factor * accumulator_[idx];
            pixels_[idx] = (unsigned char) min(accumulator_[idx], (double)UCHAR_MAX);
         }
      }
   }
   else if (pixDepth_ == 2)
   {
      const unsigned short* pixPtr = static_cast<const unsigned short*>(pix);
      unsigned short* bufPtr = reinterpret_cast<unsigned short*>(pixels_);
      for (unsigned i=0; i<height_; i++)
      {
         for (unsigned j=0; j<width_; j++)
         {
            int idx = i*width_+j;
            int srcIdx = (offsetY+i)*sourceWidth+j;
            accumulator_[idx] = lenInv * pixPtr[srcIdx] + factor * accumulator_[idx];
            bufPtr[idx] = (unsigned short) min(accumulator_[idx], (double)USHRT_MAX);
         }
      }
   }
   else
   {
      // we don't know how to deal with anything else, so set pixels to 0
         memset(pixels_, 0, width_ * height_ * pixDepth_);
   }
}

void ImgAccumulator::AddPixels(const void* pix, unsigned sourceWidth, unsigned offsetX, unsigned offsetY)
{
   if (pixDepth_ == 1)
   { 
      const unsigned char* pixPtr = static_cast<const unsigned char*>(pix);
      for (unsigned i=0; i<height_; i++)
      {
         for (unsigned j=0; j<width_; j++)
         {
            int idx = i*width_+j;
            int srcIdx = (offsetY+i)*sourceWidth+j;
            accumulator_[idx] += pixPtr[srcIdx];
         }
      }
   }
   else if (pixDepth_ == 2)
   {
      const unsigned short* pixPtr = static_cast<const unsigned short*>(pix);
      for (unsigned i=0; i<height_; i++)
      {
         for (unsigned j=0; j<width_; j++)
         {
            int idx = i*width_+j;
            int srcIdx = (offsetY+i)*sourceWidth+j;
            accumulator_[idx] += pixPtr[srcIdx];
         }
      }
   }
   else
      assert(false);
}

void ImgAccumulator::ResetPixels()
{
   // reset pixel buffer
   if (pixels_)
      memset(pixels_, 0, width_ * height_ * pixDepth_);

   // reset accumulator
   accumulator_.clear();
   accumulator_.assign(width_ * height_, 0.0);
   numFrames_ = 0;
}

void ImgAccumulator::Resize(unsigned xSize, unsigned ySize, unsigned pixDepth)
{
   // re-allocate internal buffer if it is not big enough
   if (width_ * height_ * pixDepth_ < xSize * ySize * pixDepth)
   {
      delete[] pixels_;
      pixels_ = new unsigned char [xSize * ySize * pixDepth];
   }
   
   width_ = xSize;
   height_ = ySize;
   pixDepth_ = pixDepth;

   // initialize content
   memset(pixels_, 0, width_ * height_ * pixDepth_);
   accumulator_.resize(width_ * height_, 0.0);
   numFrames_ = 0;
}

void ImgAccumulator::Resize(unsigned xSize, unsigned ySize)
{
   // re-allocate internal buffer if it is not big enough
   if (width_ * height_ < xSize * ySize)
   {
      delete[] pixels_;
      pixels_ = new unsigned char[xSize * ySize * pixDepth_];
   }

   width_ = xSize;
   height_ = ySize;

   memset(pixels_, 0, width_ * height_ * pixDepth_);
   accumulator_.resize(width_ * height_, 0.0);
   numFrames_ = 0;
}

void ImgAccumulator::Resize(unsigned length)
{
   length_ = length;
}

void ImgAccumulator::Scale(double factor)
{
   long size = width_ * height_;
   if (pixDepth_ == 1)
   { 
      for (long i=0; i<size; i++)
      {
         accumulator_[i] *= factor;
         pixels_[i] = (unsigned char) min(accumulator_[i], (double)UCHAR_MAX);
      }
   }
   else if (pixDepth_ == 2)
   {
      unsigned short* bufPtr = reinterpret_cast<unsigned short*>(pixels_);
      for (long i=0; i<size; i++)
      {
         accumulator_[i] *= factor;
         bufPtr[i] = (unsigned short) min(accumulator_[i], (double)USHRT_MAX);
      }
   }
   else
   {
      // we don't know how to deal with anything else, so set pixels to 0
      memset(pixels_, 0, width_ * height_ * pixDepth_);
   }

}
