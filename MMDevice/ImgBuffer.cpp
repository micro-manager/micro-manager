////////////////////////////////////////////////////////////////////////////////
// MODULE:			ImgBuffer.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMDevice - Device adapter kit
// AUTHOR:			Nenad Amodaj, nenad@amodaj.com
//
// COPYRIGHT:     Nenad Amodaj, 2005. All rigths reserved.
//
// LICENSE:       This file is free for use, modification and distribution and
//                is distributed under terms specified in the BSD license
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
// NOTE:          Imported from ADVI for use in Micro-Manager
///////////////////////////////////////////////////////////////////////////////
#include "ImgBuffer.h"
#include <math.h>
#include <assert.h>
using namespace std;

///////////////////////////////////////////////////////////////////////////////
// ImgBuffer class
//
ImgBuffer::ImgBuffer(unsigned xSize, unsigned ySize, unsigned pixDepth) :
   pixels_(0), width_(xSize), height_(ySize), pixDepth_(pixDepth)
{
   pixels_ = new unsigned char[xSize * ySize * pixDepth];
   assert(pixels_);
   memset(pixels_, 0, xSize * ySize * pixDepth);
}

ImgBuffer::ImgBuffer() :
   pixels_(0),
   width_(0),
   height_(0),
   pixDepth_(0)
{
}

ImgBuffer::ImgBuffer(const ImgBuffer& right)                
{
   pixels_ = 0;
   *this = right;
}

ImgBuffer::~ImgBuffer()
{
   delete[] pixels_;
}

const unsigned char* ImgBuffer::GetPixels() const
{
   return pixels_;
}

unsigned char* ImgBuffer::GetPixelsRW()
{
   return pixels_;
}

void ImgBuffer::SetPixels(const void* pix)
{
   memcpy((void*)pixels_, pix, width_ * height_ * pixDepth_);
}

// Set pixels, from a source that has extra bytes at the end of each scanline
// (row).
void ImgBuffer::SetPixelsPadded(const void* pixArray, int paddingBytesPerLine)
{
   const char* src = reinterpret_cast<const char*>(pixArray);
   char* dst = reinterpret_cast<char*>(pixels_);
   const size_t lineSize = width_ * pixDepth_;

   for(size_t i = 0; i < height_; i++)
   {
      memcpy(dst, src, lineSize);
      src += lineSize + paddingBytesPerLine;
      dst += lineSize;
   }
}

void ImgBuffer::ResetPixels()
{
   if (pixels_)
      memset(pixels_, 0, width_ * height_ * pixDepth_);
}

bool ImgBuffer::Compatible(const ImgBuffer& img) const
{
   if (  Height() != img.Height() ||
         Width() != img.Width() ||
         Depth() != img.Depth())
         return false;
   
   return true;
}

void ImgBuffer::Resize(unsigned xSize, unsigned ySize, unsigned pixDepth)
{
   // re-allocate internal buffer if it is not big enough
   if (width_ * height_ * pixDepth_ < xSize * ySize * pixDepth)
   {
      delete[] pixels_;
      pixels_ = new unsigned char [xSize * ySize * pixDepth];
      assert(pixels_);
   }

   width_ = xSize;
   height_ = ySize;
   pixDepth_ = pixDepth;
}

void ImgBuffer::Resize(unsigned xSize, unsigned ySize)
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
}

void ImgBuffer::Copy(const ImgBuffer& right)
{
   if (!Compatible(right))
      Resize(right.width_, right.height_, right.pixDepth_);

   SetPixels((void*)right.GetPixels());
}

ImgBuffer& ImgBuffer::operator=(const ImgBuffer& img)
{
   if(this == &img)
      return *this;

   if (pixels_)
      delete[] pixels_;

   width_ = img.Width();
   height_ = img.Height();
   pixDepth_ = img.Depth();
   pixels_ = new unsigned char[width_ * height_ * pixDepth_];

   Copy(img);

   return *this;
}

void ImgBuffer::SetMetadata(const Metadata& md)
{
   //metadata_ = md;
   // Serialize/Restore instead of =operator used to avoid object new/delete
   // issues accross the DLL boundary (on Windows)
   // TODO: this is inefficient and should be revised
    metadata_.Restore(md.Serialize().c_str());
}


///////////////////////////////////////////////////////////////////////////////
// FrameBuffer class
///////////////////////////////////////////////////////////////////////////////

FrameBuffer::FrameBuffer(unsigned xSize, unsigned ySize, unsigned byteDepth)
{
   width_ = xSize;
   height_ = ySize;
   depth_ = byteDepth;
   handlePending_ = false;
   frameID_ = 0;
}

FrameBuffer::FrameBuffer()
{
   width_ = 0;
   height_ = 0;
   depth_ = 0;
   handlePending_ = false;
   frameID_ = 0;
}

FrameBuffer::~FrameBuffer()
{
   Clear();
}

void FrameBuffer::Clear()
{
   for (unsigned i=0; i<images_.size(); i++)
      delete images_[i];
   images_.clear();
   indexMap_.clear();
   handlePending_ = false;
}

void FrameBuffer::Preallocate(unsigned channels, unsigned slices)
{
   for (unsigned i=0; i<channels; i++)
      for (unsigned j=0; j<slices; j++)
      {
         ImgBuffer* img = FindImage(i, j);
         if (!img)
            InsertNewImage(i, j);
      }
}

void FrameBuffer::Resize(unsigned xSize, unsigned ySize, unsigned byteDepth)
{
   Clear();
   width_ = xSize;
   height_ = ySize;
   depth_ = byteDepth;
}

bool FrameBuffer::SetImage(unsigned channel, unsigned slice, const ImgBuffer& imgBuf)
{
   handlePending_ = false;
   ImgBuffer* img = FindImage(channel, slice);

   if (img)
   {
      // image already exists
      *img = imgBuf;
   }
   else
   {
      // create a new buffer
      ImgBuffer* img2 = InsertNewImage(channel, slice);
      *img2 = imgBuf;
   }

   return true;
}

bool FrameBuffer::GetImage(unsigned channel, unsigned slice, ImgBuffer& img) const
{
   ImgBuffer* imgBuf = FindImage(channel, slice);

   if (imgBuf)
   {
      img = *imgBuf;
      return true;
   }
   else
      return false;
}

bool FrameBuffer::SetPixels(unsigned channel, unsigned slice, const unsigned char* pixels)
{
   handlePending_ = false;
   ImgBuffer* img = FindImage(channel, slice);

   if (img)
   {
      // image already exists
      img->SetPixels(pixels);
   }
   else
   {
      // create a new buffer
      ImgBuffer* img2 = InsertNewImage(channel, slice);
      img2->SetPixels(pixels);
   }

   return true;
}

const unsigned char* FrameBuffer::GetPixels(unsigned channel, unsigned slice) const
{
   ImgBuffer* img = FindImage(channel, slice);
   if (img)
      return img->GetPixels();
   else
      return 0;
}

ImgBuffer* FrameBuffer::FindImage(unsigned channel, unsigned slice) const
{
   map<unsigned long, ImgBuffer*>::const_iterator it = indexMap_.find(GetIndex(channel, slice));
   if (it != indexMap_.end())
      return it->second;
   else
      return 0;
}

unsigned long FrameBuffer::GetIndex(unsigned channel, unsigned slice)
{
   // set the slice in the upper and channel in the lower part
   unsigned long idx((unsigned short)slice);
   idx = idx << 16;
   idx = idx | (unsigned short) channel;
   return idx;
}

ImgBuffer* FrameBuffer::InsertNewImage(unsigned channel, unsigned slice)
{
   assert(FindImage(channel, slice) == 0);

   ImgBuffer* img = new ImgBuffer(width_, height_, depth_);
   images_.push_back(img);
   indexMap_[GetIndex(channel, slice)] = img;
   return img;
}
