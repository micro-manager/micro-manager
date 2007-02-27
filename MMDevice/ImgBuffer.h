///////////////////////////////////////////////////////////////////////////////
// MODULE:			ImgBuffer.h
// SYSTEM:        ImageBase subsystem
// AUTHOR:			Nenad Amodaj, nenad@amodaj.com
//
// DESCRIPTION:	Basic implementation for the raw image buffer data structure.
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

#if !defined(_IMG_BUFFER_)
#define _IMG_BUFFER_

#include <string>
#include <vector>
#include <map>

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
   void ResetPixels();
   const unsigned char* GetPixels() const;

   void Resize(unsigned xSize, unsigned ySize, unsigned pixDepth);
   void Resize(unsigned xSize, unsigned ySize);
   bool Compatible(const ImgBuffer& img) const;

   void Copy(const ImgBuffer& rhs);
   ImgBuffer& operator=(const ImgBuffer& rhs);

private:
   unsigned char* pixels_;
   unsigned int width_;
   unsigned int height_;
   unsigned int pixDepth_;
};

typedef std::vector<std::string> CMMStringArray;
typedef std::map<std::string, ImgBuffer> CMMChannelMap;

#endif // !defined(_IMG_BUFFER_)
