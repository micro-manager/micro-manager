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
      ImgBuffer* img = InsertNewImage(channel, slice);
      *img = imgBuf;
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
      ImgBuffer* img = InsertNewImage(channel, slice);
      img->SetPixels(pixels);
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

///////////////////////////////////////////////////////////////////////////////
// Debayer class implementation
//
///////////////////////////////////////////////////////////////////////////////
Debayer::Debayer()
{

}

Debayer::~Debayer()
{
}

int Debayer::Process(ImgBuffer& out, const ImgBuffer& input, int bitDepth)
{
   // TODO: create better error message
   if (input.Depth() != 2)
      return DEVICE_UNSUPPORTED_DATA_FORMAT;

   out.Resize(input.Width(), input.Height(), 4);
   assert(sizeof(int) == 4);
   const unsigned short* inBuf = reinterpret_cast<const unsigned short*>(input.GetPixels());
   int* outBuf = reinterpret_cast<int*>(out.GetPixelsRW());

   return Convert(inBuf, outBuf, input.Width(), input.Height(), bitDepth, 0, 0);
}

int Debayer::Process(ImgBuffer& out, const unsigned short* in, int width, int height, int bitDepth)
{
   out.Resize(width, height, 4);
   assert(sizeof(int) == 4);
   const unsigned short* inBuf = reinterpret_cast<const unsigned short*>(in);
   int* outBuf = reinterpret_cast<int*>(out.GetPixelsRW());

   return Convert(inBuf, outBuf, width, height, bitDepth, 0, 0);
}


int Debayer::Convert(const unsigned short* input, int* output, int width, int height, int bitDepth, int rowOrder, int algorithm)
{
   //const char* orders[] = {"R-G-R-G", "B-G-B-G", "G-R-G-R", "G-B-G-B"};
	//const char* algorithms[] = {"Replication", "Bilinear", "Smooth-Hue", "Adaptive-Smooth-Hue"};
				
	if (algorithm == 0)
      ReplicateDecode(input, output, width, height, bitDepth, rowOrder);
	//else if (algorithm == 1) return average_decode(input, output, width, height, rowOrder);
	//else if (algorithm == 2) return smooth_decode(input, output, width, height, rowOrder);
	//else if (algorithm == 3) return adaptive_decode(input, output, width, height, rowOrder);

   return DEVICE_OK;
}

unsigned short Debayer::GetPixel(const unsigned short* v, int x, int y, int width, int height)
{
   if (x >= width || x < 0 || y >= height || y < 0)
      return 0;
   else
      return v[y*width + x];
}

void Debayer::SetPixel(std::vector<unsigned short>& v, unsigned short val, int x, int y, int width, int height)
{
   if (x < width && x >= 0 && y < height && y >= 0)
      v[y*width + x] = val;
}


// Replication algorithm
void Debayer::ReplicateDecode(const unsigned short* input, int* output, int width, int height, int bitDepth, int rowOrder)
{
   unsigned numPixels(width*height);
   if (r.size() != numPixels)
   {
      r.resize(numPixels);
      g.resize(numPixels);
      b.resize(numPixels);
   }

   int bitShift = bitDepth - 8;
	
	if (rowOrder == 0 || rowOrder == 1) {
		for (int y=0; y<height; y+=2) {
			for (int x=0; x<width; x+=2) {
				unsigned short one = GetPixel(input, x, y, width, height);
				SetPixel(b, one, x, y, width, height);
				SetPixel(b, one, x+1, y, width, height);
				SetPixel(b, one, x, y+1, width, height); 
				SetPixel(b, one, x+1, y+1, width, height);
			}
		}
		
		for (int y=1; y<height; y+=2) {
			for (int x=1; x<width; x+=2) {
            unsigned short one = GetPixel(input, x, y, width, height);
				SetPixel(r, one, x, y, width, height);
				SetPixel(r, one, x+1, y, width, height);
				SetPixel(r, one, x, y+1, width, height); 
				SetPixel(r, one, x+1, y+1, width, height);
			}
		}
		
		for (int y=0; y<height; y+=2) {
			for (int x=1; x<width; x+=2) {
				unsigned short one = GetPixel(input, x, y, width, height);
            SetPixel(g, one, x, y, width, height);
            SetPixel(g, one, x+1, y, width, height);
			}
		}	
			
		for (int y=1; y<height; y+=2) {
			for (int x=0; x<width; x+=2) {
            unsigned short one = GetPixel(input, x, y, width, height);
            SetPixel(g, one, x, y, width, height);
            SetPixel(g, one, x+1, y, width, height);
			}
		}	
		
		if (rowOrder == 0) {
         for (int i=0; i<height*width; i++)
         {
            output[i] = 0;
            unsigned char* bytePix = (unsigned char*)(output+i);
            *bytePix = (unsigned char)(b[i] >> bitShift);
            *(bytePix+1) = (unsigned char)(g[i] >> bitShift);
            *(bytePix+2) = (unsigned char)(r[i] >> bitShift);

			   //rgb.addSlice("red",b);	
			   //rgb.addSlice("green",g);
			   //rgb.addSlice("blue",r);
         }
		}
		else if (rowOrder == 1) {
         for (int i=0; i<height*width; i++)
         {
            output[i] = 0;
            unsigned char* bytePix = (unsigned char*)(output+i);
            *bytePix = (unsigned char)(r[i] >> bitShift);
            *(bytePix+1) = (unsigned char)(g[i] >> bitShift);
            *(bytePix+2) = (unsigned char)(b[i] >> bitShift);

			   //rgb.addSlice("red",r);	
			   //rgb.addSlice("green",g);
			   //rgb.addSlice("blue",b);			
		   }
      }
	}

	else if (rowOrder == 2 || rowOrder == 3) {
		for (int y=1; y<height; y+=2) {
			for (int x=0; x<width; x+=2) {
				unsigned short one = GetPixel(input, x, y, width, height);
				SetPixel(b, one, x, y, width, height);
				SetPixel(b, one, x+1, y, width, height);
				SetPixel(b, one, x, y+1, width, height); 
				SetPixel(b, one, x+1, y+1, width, height);
			}
		}
		
		for (int y=0; y<height; y+=2) {
			for (int x=1; x<width; x+=2) {
            unsigned short one = GetPixel(input, x, y, width, height);
				SetPixel(r, one, x, y, width, height);
				SetPixel(r, one, x+1, y, width, height);
				SetPixel(r, one, x, y+1, width, height); 
				SetPixel(r, one, x+1, y+1, width, height);
			}
		}
		
		for (int y=0; y<height; y+=2) {
			for (int x=0; x<width; x+=2) {
				unsigned short one = GetPixel(input, x, y, width, height);
            SetPixel(g, one, x, y, width, height);
            SetPixel(g, one, x+1, y, width, height);
			}
		}	
			
		for (int y=1; y<height; y+=2) {
			for (int x=1; x<width; x+=2) {
            unsigned short one = GetPixel(input, x, y, width, height);
            SetPixel(g, one, x, y, width, height);
            SetPixel(g, one, x+1, y, width, height);
			}
		}	
		
		if (rowOrder == 2) {
         for (int i=0; i<height*width; i++)
         {
            output[i] = 0;
            unsigned char* bytePix = (unsigned char*)(output+i);
            *bytePix = (unsigned char)(b[i] >> bitShift);
            *(bytePix+1) = (unsigned char)(g[i] >> bitShift);
            *(bytePix+2) = (unsigned char)(r[i] >> bitShift);

            //rgb.addSlice("red",b);	
			   //rgb.addSlice("green",g);
			   //rgb.addSlice("blue",r);
         }
		}
		else if (rowOrder == 3) {
         for (int i=0; i<height*width; i++)
         {
            output[i] = 0;
            unsigned char* bytePix = (unsigned char*)(output+i);
            *bytePix = (unsigned char)(r[i] >> bitShift);
            *(bytePix+1) = (unsigned char)(g[i] >> bitShift);
            *(bytePix+2) = (unsigned char)(b[i] >> bitShift);

            //rgb.addSlice("red",r);	
			   //rgb.addSlice("green",g);
			   //rgb.addSlice("blue",b);
         }
		}
	}
}