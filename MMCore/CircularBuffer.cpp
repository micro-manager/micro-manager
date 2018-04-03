///////////////////////////////////////////////////////////////////////////////
// FILE:          CircularBuffer.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCore
//-----------------------------------------------------------------------------
// DESCRIPTION:   Generic implementation of the circular buffer. The buffer
//                allows only one thread to enter at a time by using a mutex lock.
//                This makes the buffer susceptible to race conditions if the
//                calling threads are mutually dependent.
//              
// COPYRIGHT:     University of California, San Francisco, 2007,
//
// LICENSE:       This file is distributed under the "Lesser GPL" (LGPL) license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 01/05/2007
// 
#include "CircularBuffer.h"
#include "CoreUtils.h"

#include "../MMDevice/DeviceUtils.h"


const long long bytesInMB = 1 << 20;
const long adjustThreshold = LONG_MAX / 2;
const unsigned long maxCBSize = 100000;    //a reasonable limit to circular buffer size

CircularBuffer::CircularBuffer(unsigned int memorySizeMB) :
   width_(0), 
   height_(0), 
   pixDepth_(0), 
   imageCounter_(0), 
   insertIndex_(0), 
   saveIndex_(0), 
   memorySizeMB_(memorySizeMB), 
   overflow_(false)
{
}

CircularBuffer::~CircularBuffer() {}

bool CircularBuffer::Initialize(unsigned channels, unsigned int w, unsigned int h, unsigned int pixDepth)
{
   MMThreadGuard guard(g_bufferLock);
   imageNumbers_.clear();

   bool ret = true;
   try
   {
      if (w == 0 || h==0 || pixDepth == 0 || channels == 0)
         return false; // does not make sense

      if (w == width_ && height_ == h && pixDepth_ == pixDepth && channels == numChannels_)
         if (frameArray_.size() > 0)
            return true; // nothing to change

      width_ = w;
      height_ = h;
      pixDepth_ = pixDepth;
      numChannels_ = channels;

      insertIndex_ = 0;
      saveIndex_ = 0;
      overflow_ = false;

      // calculate the size of the entire buffer array once all images get allocated
      // the actual size at the time of the creation is going to be less, because
      // images are not allocated until pixels become available
      unsigned long frameSizeBytes = width_ * height_ * pixDepth_ * numChannels_;
      unsigned long cbSize = (unsigned long) ((memorySizeMB_ * bytesInMB) / frameSizeBytes);

      if (cbSize == 0) 
      {
         frameArray_.resize(0);
         return false; // memory footprint too small
      }

      // set a reasonable limit to circular buffer capacity 
      if (cbSize > maxCBSize)
         cbSize = maxCBSize; 

      // TODO: verify if we have enough RAM to satisfy this request

      for (unsigned long i=0; i<frameArray_.size(); i++)
         frameArray_[i].Clear();

      // allocate buffers  - could conceivably throw an out-of-memory exception
      frameArray_.resize(cbSize);
      for (unsigned long i=0; i<frameArray_.size(); i++)
      {
         frameArray_[i].Resize(w, h, pixDepth);
         frameArray_[i].Preallocate(numChannels_);
      }
   }

   catch( ... /* std::bad_alloc& ex */)
   {
      frameArray_.resize(0);
      ret = false;
   }
   return ret;
}

unsigned long CircularBuffer::GetSize() const
{
   MMThreadGuard guard(g_bufferLock);
   return (unsigned long)frameArray_.size();
}

unsigned long CircularBuffer::GetFreeSize() const
{
   MMThreadGuard guard(g_bufferLock);
   long freeSize = (long)frameArray_.size() - (insertIndex_ - saveIndex_);
   if (freeSize < 0)
      return 0;
   else
      return (unsigned long)freeSize;
}

unsigned long CircularBuffer::GetRemainingImageCount() const
{
   MMThreadGuard guard(g_bufferLock);
   return (unsigned long)(insertIndex_ - saveIndex_);
}

/**
* Inserts a single image in the buffer.
*/
bool CircularBuffer::InsertImage(const unsigned char* pixArray, unsigned int width, unsigned int height, unsigned int byteDepth, const Metadata* pMd) throw (CMMError)
{
   return InsertMultiChannel(pixArray, 1, width, height, byteDepth, pMd);
}

/**
* Inserts a single image, possibly with multiple channels, but with 1 component, in the buffer.
*/
bool CircularBuffer::InsertMultiChannel(const unsigned char* pixArray, unsigned numChannels, unsigned width, unsigned height, unsigned byteDepth, const Metadata* pMd) throw (CMMError)
{
   return InsertMultiChannel(pixArray, numChannels, width, height, byteDepth, 1, pMd);
}

/**
* Inserts a single image, possibly with multiple components, in the buffer.
*/
bool CircularBuffer::InsertImage(const unsigned char* pixArray, unsigned int width, unsigned int height, unsigned int byteDepth, unsigned int nComponents, const Metadata* pMd) throw (CMMError)
{
    return InsertMultiChannel(pixArray, 1, width, height, byteDepth, nComponents, pMd);
}
 
/**
* Inserts a multi-channel frame in the buffer.
*/
bool CircularBuffer::InsertMultiChannel(const unsigned char* pixArray, unsigned numChannels, unsigned width, unsigned height, unsigned byteDepth, unsigned nComponents, const Metadata* pMd) throw (CMMError)
{
    MMThreadGuard guard(g_insertLock);
 
    mm::ImgBuffer* pImg;
    unsigned long singleChannelSize = (unsigned long)width * height * byteDepth;
 
    {
       MMThreadGuard guard(g_bufferLock);
 
       // check image dimensions
       if (width != width_ || height != height_ || byteDepth != pixDepth_)
          throw CMMError("Incompatible image dimensions in the circular buffer", MMERR_CircularBufferIncompatibleImage);
 
       bool overflowed = (insertIndex_ - saveIndex_) >= static_cast<long>(frameArray_.size());
       if (overflowed) {
          overflow_ = true;
          return false;
       }
    }
 
    for (unsigned i=0; i<numChannels; i++)
    {
       Metadata md;
       {
          MMThreadGuard guard(g_bufferLock);
          // we assume that all buffers are pre-allocated
          pImg = frameArray_[insertIndex_ % frameArray_.size()].FindImage(i);
          if (!pImg)
             return false;
 
          if (pMd)
          {
             // TODO: the same metadata is inserted for each channel ???
             // Perhaps we need to add specific tags to each channel
             md = *pMd;
          }

         std::string cameraName = md.GetSingleTag("Camera").GetValue();
         if (imageNumbers_.end() == imageNumbers_.find(cameraName))
         {
            imageNumbers_[cameraName] = 0;
         }

         // insert image number. 
         md.put(MM::g_Keyword_Metadata_ImageNumber, CDeviceUtils::ConvertToString(imageNumbers_[cameraName]));
         ++imageNumbers_[cameraName];
      }

      if (!md.HasTag(MM::g_Keyword_Elapsed_Time_ms))
      {
         // if time tag was not supplied by the camera insert current timestamp
         MM::MMTime timestamp = GetMMTimeNow();
         md.PutImageTag(MM::g_Keyword_Elapsed_Time_ms, CDeviceUtils::ConvertToString(timestamp.getMsec()));
      }

      md.PutImageTag("Width",width);
      md.PutImageTag("Height",height);
      if (byteDepth == 1)
         md.PutImageTag("PixelType","GRAY8");
      else if (byteDepth == 2)
         md.PutImageTag("PixelType","GRAY16");
      else if (byteDepth == 4)
      {
         if (nComponents == 1)
            md.PutImageTag("PixelType","GRAY32");
         else
            md.PutImageTag("PixelType","RGB32");
      }
      else if (byteDepth == 8)
         md.PutImageTag("PixelType","RGB64");
      else
         md.PutImageTag("PixelType","Unknown"); 

      pImg->SetMetadata(md);
      pImg->SetPixels(pixArray + i*singleChannelSize);
   }

   {
      MMThreadGuard guard(g_bufferLock);

      imageCounter_++;
      insertIndex_++;
      if ((insertIndex_ - (long)frameArray_.size()) > adjustThreshold && (saveIndex_- (long)frameArray_.size()) > adjustThreshold)
      {
         // adjust buffer indices to avoid overflowing integer size
         insertIndex_ -= adjustThreshold;
         saveIndex_ -= adjustThreshold;
      }
   }

   return true;
}
 

const unsigned char* CircularBuffer::GetTopImage() const
{
   const mm::ImgBuffer* img = GetNthFromTopImageBuffer(0, 0);
   if (!img)
      return 0;
   return img->GetPixels();
}

const mm::ImgBuffer* CircularBuffer::GetTopImageBuffer(unsigned channel) const
{
   return GetNthFromTopImageBuffer(0, channel);
}

const mm::ImgBuffer* CircularBuffer::GetNthFromTopImageBuffer(unsigned long n) const
{
   return GetNthFromTopImageBuffer(static_cast<long>(n), 0);
}

const mm::ImgBuffer* CircularBuffer::GetNthFromTopImageBuffer(long n,
      unsigned channel) const
{
   MMThreadGuard guard(g_bufferLock);

   long availableImages = insertIndex_ - saveIndex_;
   if (n + 1 > availableImages)
      return 0;

   long targetIndex = insertIndex_ - n - 1L;
   while (targetIndex < 0)
      targetIndex += (long) frameArray_.size();
   targetIndex %= frameArray_.size();

   return frameArray_[targetIndex].FindImage(channel);
}

const unsigned char* CircularBuffer::GetNextImage()
{
   const mm::ImgBuffer* img = GetNextImageBuffer(0);
   if (!img)
      return 0;
   return img->GetPixels();
}

const mm::ImgBuffer* CircularBuffer::GetNextImageBuffer(unsigned channel)
{
   MMThreadGuard guard(g_bufferLock);

   long availableImages = insertIndex_ - saveIndex_;
   if (availableImages < 1)
      return 0;

   long targetIndex = saveIndex_ % frameArray_.size();
   ++saveIndex_;
   return frameArray_[targetIndex].FindImage(channel);
}
