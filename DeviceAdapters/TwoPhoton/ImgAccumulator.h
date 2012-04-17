///////////////////////////////////////////////////////////////////////////////
// MODULE:			ImgAccumulator.h
// SYSTEM:        100X Imaging base utilities
// AUTHOR:			Nenad Amodaj
//
// DESCRIPTION:	Basic implementation for the accumultor data structure (frame averaging).
//
// COPYRIGHT:     100X Imaging Inc, 2009. All rigths reserved.
//
///////////////////////////////////////////////////////////////////////////////

#pragma once

#include <string>
#include <vector>
#include <map>
#include "MMDevice.h"
#include "ImageMetadata.h"

///////////////////////////////////////////////////////////////////////////////
//
// ImgAccumulator class
// ~~~~~~~~~~~~~~~~~~~~
// Variable pixel depth image buffer, with frame averaging capabilities
//

class ImgAccumulator
{
public:
   ImgAccumulator(unsigned xSize, unsigned ySize, unsigned pixDepth, unsigned numFrames);
   ImgAccumulator();
   ~ImgAccumulator();

   unsigned int Width() const {return width_;}
   unsigned int Height() const {return height_;}
   unsigned int Depth() const {return pixDepth_;}
   unsigned int Length() const {return length_;}
   void SetPixels(const void* pixArray, unsigned sourceWidth, unsigned offsetX, unsigned offsetY);
   void AddPixels(const void* pixArray, unsigned sourceWidth, unsigned offsetX, unsigned offsetY);
   void Scale(double factor);
   void ResetPixels();
   const unsigned char* GetPixels() const;

   void Resize(unsigned xSize, unsigned ySize, unsigned pixDepth);
   void Resize(unsigned xSize, unsigned ySize);
   void Resize(unsigned length);

   bool IsEnabled() const {return enabled_;}
   void SetEnable(bool s) {enabled_ = s;}

private:
   unsigned char* pixels_;
   std::vector<double> accumulator_;

   unsigned int width_;
   unsigned int height_;
   unsigned int pixDepth_;
   unsigned int length_;
   unsigned int numFrames_;
   bool enabled_;
};
