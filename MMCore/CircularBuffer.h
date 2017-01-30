///////////////////////////////////////////////////////////////////////////////
// FILE:          CircularBuffer.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCore
//-----------------------------------------------------------------------------
// DESCRIPTION:   Generic implementation of the circular buffer
//              
// COPYRIGHT:     University of California, San Francisco, 2007,
//                100X Imaging Inc, 2008
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

#if !defined(_CIRCULAR_BUFFER_)
#define _CIRCULAR_BUFFER_

#include <vector>
#include "../MMDevice/ImgBuffer.h"
#include "../MMDevice/MMDevice.h"
#include "ErrorCodes.h"
#include "Error.h"

#ifdef WIN32
#pragma warning( disable : 4290 ) // exception declaration warning
#endif

///////////////////////////////////////////////////////////////////////////////
//
// CircularBuffer class
// ~~~~~~~~~~~~~~~~~~~~

static MMThreadLock g_bufferLock;
static MMThreadLock g_insertLock;

class CircularBuffer
{
public:
   CircularBuffer(unsigned int memorySizeMB);
   ~CircularBuffer();

   unsigned GetMemorySizeMB() const { return memorySizeMB_; }

   bool Initialize(unsigned channels, unsigned slices, unsigned int xSize, unsigned int ySize, unsigned int pixDepth);
   unsigned long GetSize() const;
   unsigned long GetFreeSize() const;
   unsigned long GetRemainingImageCount() const;

   unsigned int Width() const {MMThreadGuard guard(g_bufferLock); return width_;}
   unsigned int Height() const {MMThreadGuard guard(g_bufferLock); return height_;}
   unsigned int Depth() const {MMThreadGuard guard(g_bufferLock); return pixDepth_;}

   bool InsertImage(const unsigned char* pixArray, unsigned int width, unsigned int height, unsigned int byteDepth, const Metadata* pMd) throw (CMMError);
   bool InsertMultiChannel(const unsigned char* pixArray, unsigned int numChannels, unsigned int width, unsigned int height, unsigned int byteDepth, const Metadata* pMd) throw (CMMError);
   const unsigned char* GetTopImage() const;
   const unsigned char* GetNextImage();
   const ImgBuffer* GetTopImageBuffer(unsigned channel, unsigned slice) const;
   const ImgBuffer* GetNthFromTopImageBuffer(unsigned long n) const;
   const ImgBuffer* GetNthFromTopImageBuffer(long n, unsigned channel, unsigned slice) const;
   const ImgBuffer* GetNextImageBuffer(unsigned channel, unsigned slice);
   void Clear() {MMThreadGuard guard(g_bufferLock); insertIndex_=0; saveIndex_=0; overflow_ = false;}

   bool Overflow() {MMThreadGuard guard(g_bufferLock); return overflow_;}

private:
   unsigned int width_;
   unsigned int height_;
   unsigned int pixDepth_;
   long imageCounter_;
   std::map<std::string, long> imageNumbers_;

   // Invariants:
   // 0 <= saveIndex_ <= insertIndex_
   // insertIndex_ - saveIndex_ <= frameArray_.size()
   long insertIndex_;
   long saveIndex_;

   unsigned long memorySizeMB_;
   unsigned int numChannels_;
   unsigned int numSlices_;
   bool overflow_;
   std::vector<FrameBuffer> frameArray_;
};


#endif // !defined(_CIRCULAR_BUFFER_)
