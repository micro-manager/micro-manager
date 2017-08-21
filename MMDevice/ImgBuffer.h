///////////////////////////////////////////////////////////////////////////////
// MODULE:			ImgBuffer.h
// SYSTEM:        ImageBase subsystem
// AUTHOR:			Nenad Amodaj, nenad@amodaj.com
//
// DESCRIPTION:	Basic implementation for the raw image buffer data structure.
//
// COPYRIGHT:     Nenad Amodaj, 2005. All rights reserved.
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

#if !defined(_IMG_BUFFER_)
#define _IMG_BUFFER_

#include <string>
#include <vector>
#include <map>
#include "MMDevice.h"
#include "ImageMetadata.h"

///////////////////////////////////////////////////////////////////////////////
//
// CImgBuffer class
// ~~~~~~~~~~~~~~~~~~
// Variable pixel depth image buffer
//

class ImgBuffer
{
public:
   ImgBuffer(unsigned xSize, unsigned ySize, unsigned pixDepth);
   ImgBuffer(const ImgBuffer& ib);
   ImgBuffer();
   ~ImgBuffer();

   unsigned int Width() const {return width_;}
   unsigned int Height() const {return height_;}
   unsigned int Depth() const {return pixDepth_;}
   void SetPixels(const void* pixArray);
   void SetPixelsPadded(const void* pixArray, int paddingBytesPerLine);
   void ResetPixels();
   const unsigned char* GetPixels() const;
   unsigned char* GetPixelsRW();

   void Resize(unsigned xSize, unsigned ySize, unsigned pixDepth);
   void Resize(unsigned xSize, unsigned ySize);
   bool Compatible(const ImgBuffer& img) const;

   void SetName(const char* name) {name_ = name;}
   const std::string& GetName() {return name_;}
   void SetMetadata(const Metadata& md);
   const Metadata& GetMetadata() const {return metadata_;}

   void Copy(const ImgBuffer& rhs);
   ImgBuffer& operator=(const ImgBuffer& rhs);

private:
   unsigned char* pixels_;
   unsigned int width_;
   unsigned int height_;
   unsigned int pixDepth_;
   std::string name_;
   Metadata metadata_;
};

#endif // !defined(_IMG_BUFFER_)
