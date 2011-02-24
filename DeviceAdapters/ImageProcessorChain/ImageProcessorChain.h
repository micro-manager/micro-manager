///////////////////////////////////////////////////////////////////////////////
// FILE:          ImageProcessorChain.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Runs a chain of other ImageProcessors on each image 
//                
// AUTHOR:        Karl Hoover
//
// COPYRIGHT:     University of California, San Francisco, 2011
//
// LICENSE:       This file is distributed under the BSD license.
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
// CVS:           $Id: ImageProcessorChain.h 6583 2011-02-22 21:07:49Z karlh $
//

#ifndef _IMAGEPROCESSORCHAIN_H_
#define _IMAGEPROCESSORCHAIN_H_

#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/ImgBuffer.h"
#include "../../MMDevice/DeviceThreads.h"
#include <string>
#include <map>



//////////////////////////////////////////////////////////////////////////////
// ImageProcessorChain class
// run chain of image processors
//////////////////////////////////////////////////////////////////////////////
class ImageProcessorChain : public CImageProcessorBase<ImageProcessorChain>
{
public:
   ImageProcessorChain () : nSlots_(10), busy_(false) {}
   ~ImageProcessorChain () { }

   int Shutdown() {return DEVICE_OK;}
   void GetName(char* name) const {strcpy(name,"ImageProcessorChain");}

   int Initialize();

   bool Busy(void) { return busy_;};

   int Process(unsigned char* buffer, unsigned width, unsigned height, unsigned byteDepth);

   // action interface
   // ----------------
   int OnProcessor(MM::PropertyBase* pProp, MM::ActionType eAct, long indexx);

private:
   bool busy_;
   const int nSlots_;
   std::map< int, std::string> processorNames_;
   std::map< int, MM::ImageProcessor*> processors_;

   ImageProcessorChain& operator=( const ImageProcessorChain& ){ };

   
};




#endif //_IMAGEPROCESSORCHAIN_H_
