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

#pragma once

#include "../MMDevice/ImageMetadata.h"

#include <string>
#include <vector>
#include <map>

namespace mm {

class ImgBuffer
{
   unsigned char* pixels_;
   unsigned int width_;
   unsigned int height_;
   unsigned int pixDepth_;
   Metadata metadata_;

public:
   ImgBuffer(unsigned xSize, unsigned ySize, unsigned pixDepth);
   ~ImgBuffer();

   unsigned int Width() const {return width_;}
   unsigned int Height() const {return height_;}
   unsigned int Depth() const {return pixDepth_;}
   void SetPixels(const void* pixArray);
   const unsigned char* GetPixels() const;

   void Resize(unsigned xSize, unsigned ySize, unsigned pixDepth);
   void Resize(unsigned xSize, unsigned ySize);

   void SetMetadata(const Metadata& md);
   const Metadata& GetMetadata() const {return metadata_;}

private:
   ImgBuffer& operator=(const ImgBuffer&);
};

class FrameBuffer
{
   // Holds null for any unallocated channels, and is as long as need to
   // contain the allocated channels.
   std::vector<ImgBuffer*> channels_;
   unsigned int width_;
   unsigned int height_;
   unsigned int depth_;

public:
   FrameBuffer(unsigned xSize, unsigned ySize, unsigned byteDepth);
   FrameBuffer();
   ~FrameBuffer();

   void Resize(unsigned xSize, unsigned ySize, unsigned pixDepth);
   void Clear();
   void Preallocate(unsigned channels);

   ImgBuffer* FindImage(unsigned channel) const;
   const unsigned char* GetPixels(unsigned channel) const;
   bool SetPixels(unsigned channel, const unsigned char* pixels);
   unsigned Width() const {return width_;}
   unsigned Height() const {return height_;}
   unsigned Depth() const {return depth_;}

private:
   // The following line should be uncommented once we upgrade to
   // VC++ >= 2013. (Or operator= should be declared deleted, C++11-style.)
   // For the description of the standard library bug necessitating this
   // workaround, see http://stackoverflow.com/a/25423089
   // FrameBuffer& operator=(const FrameBuffer&);

private:
   ImgBuffer* InsertNewImage(unsigned channel);
};

} // namespace mm
