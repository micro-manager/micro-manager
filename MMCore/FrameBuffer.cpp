// AUTHOR:        Nenad Amodaj, nenad@amodaj.com
//
// COPYRIGHT:     2005 Nenad Amodaj
//                2005-2015 Regents of the University of California
//                2017 Open Imaging, Inc.
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

#include "FrameBuffer.h"

#include <math.h>

namespace mm {

ImgBuffer::ImgBuffer(unsigned xSize, unsigned ySize, unsigned pixDepth) :
   pixels_(0), width_(xSize), height_(ySize), pixDepth_(pixDepth)
{
   pixels_ = new unsigned char[xSize * ySize * pixDepth];
   memset(pixels_, 0, xSize * ySize * pixDepth);
}

ImgBuffer::~ImgBuffer()
{
   delete[] pixels_;
}

const unsigned char* ImgBuffer::GetPixels() const
{
   return pixels_;
}

void ImgBuffer::SetPixels(const void* pix)
{
   memcpy((void*)pixels_, pix, width_ * height_ * pixDepth_);
}

void ImgBuffer::Resize(unsigned xSize, unsigned ySize, unsigned pixDepth)
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
}

FrameBuffer::FrameBuffer()
{
   width_ = 0;
   height_ = 0;
   depth_ = 0;
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
}

void FrameBuffer::Preallocate(unsigned channels)
{
   for (unsigned i=0; i<channels; i++)
   {
      ImgBuffer* img = FindImage(i);
      if (!img)
         InsertNewImage(i);
   }
}

void FrameBuffer::Resize(unsigned xSize, unsigned ySize, unsigned byteDepth)
{
   Clear();
   width_ = xSize;
   height_ = ySize;
   depth_ = byteDepth;
}

bool FrameBuffer::SetPixels(unsigned channel, const unsigned char* pixels)
{
   ImgBuffer* img = FindImage(channel);

   if (img)
   {
      // image already exists
      img->SetPixels(pixels);
   }
   else
   {
      // create a new buffer
      ImgBuffer* img2 = InsertNewImage(channel);
      img2->SetPixels(pixels);
   }

   return true;
}

const unsigned char* FrameBuffer::GetPixels(unsigned channel) const
{
   ImgBuffer* img = FindImage(channel);
   if (img)
      return img->GetPixels();
   else
      return 0;
}

ImgBuffer* FrameBuffer::FindImage(unsigned channel) const
{
   std::map<unsigned long, ImgBuffer*>::const_iterator it = indexMap_.find(GetIndex(channel));
   if (it != indexMap_.end())
      return it->second;
   else
      return 0;
}

unsigned long FrameBuffer::GetIndex(unsigned channel)
{
   return channel;
}

ImgBuffer* FrameBuffer::InsertNewImage(unsigned channel)
{
   ImgBuffer* img = new ImgBuffer(width_, height_, depth_);
   images_.push_back(img);
   indexMap_[GetIndex(channel)] = img;
   return img;
}

} // namespace mm
