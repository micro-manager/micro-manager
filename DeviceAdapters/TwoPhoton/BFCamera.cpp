///////////////////////////////////////////////////////////////////////////////
// MODULE:			BFCamera.cpp
// SYSTEM:        100X Imaging base utilities
// AUTHOR:			Nenad Amodaj
//
// DESCRIPTION:	Encapsulation for the Bitflow generic camera interface Ci
//
// COPYRIGHT:     Nenad Amodaj 2011, 100X Imaging Inc 2009
//
// LICENSE:       This library is free software; you can redistribute it and/or
//                modify it under the terms of the GNU Lesser General Public
//                License as published by the Free Software Foundation.
//                
//                You should have received a copy of the GNU Lesser General Public
//                License along with the source distribution; if not, write to
//                the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
//                Boston, MA  02111-1307  USA
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.  
//                
// AUTHOR:        Nenad Amodaj, November 2009
//
///////////////////////////////////////////////////////////////////////////////

#include "BFCamera.h"
#include "MMDeviceConstants.h"
#include "TwoPhoton.h"
#include <iostream>
#include <assert.h>
#include <math.h>

using namespace std;

BFCamera::BFCamera(bool dual) :
   width_(0),
   depth_(0),
   height_(0),
   buf_(0),
   initialized_(false),
   timeoutMs_(1000),
   acquiring_(false),
   dual_(dual)
{
}

   int BFCamera::GetTimeout() 
   {
	   return timeoutMs_;
   }

BFCamera::~BFCamera()
{
   Shutdown();
   delete[] buf_;
}

int BFCamera::Initialize(MM::Device* caller, MM::Core* core)
{
	caller_ = caller;
	core_ = core;
   // close existing boards
   Shutdown();
   // find the number of available boards
   BFU32 num = 0;
   BFRC ret = CiSysBrdEnum(CISYS_TYPE_R64, &num);
   if (ret != BF_OK)
      return ret;

   // open all of them
   if (!dual_ && num > 4)
      num = 4;

   num = min(num, 6); 

   for (unsigned i=0; i<num; i++)
   {
    	
	   
      CiENTRY entry;
      ret = CiSysBrdFind(CISYS_TYPE_R64, i, &entry);
      if (ret != BF_OK)
         return ret;

      Bd board;
      ret = CiBrdOpen(&entry, &board, CiSysInitialize);
      if (ret != BF_OK)
         return ret;

      BFU32 depth;
	   ret = CiBrdInquire(board, CiCamInqBytesPerPix, &depth);
      if (ret != BF_OK)
         return ret;

      BFU32 width;
	   ret = CiBrdInquire(board, CiCamInqXSize, &width);
      if (ret != BF_OK)
         return ret;

      BFU32 height;
	   ret = CiBrdInquire(board, CiCamInqYSize0, &height);
      if (ret != BF_OK)
         return ret;

      if (width_==0 && height_==0 && depth_ == 0)
      {
         // first time
         width_ = width;
         height_ = height;
         depth_ = depth;
      }
      else
      {
         if (width != width_ || height != height_ || depth_ != depth)
         {
            CiBrdClose(board);
            return BF_INCOMPATIBLE_CAMERAS;
         }
      }

      boards_.push_back(board);
   }
   
   BFU32 boardBufSize = width_ * height_ * depth_ + MAX_FRAME_OFFSET*2;
   BFU32 actualBufSize = width_ * height_ * depth_;
   delete[] buf_;
   buf_ = new unsigned char[boardBufSize * boards_.size()];
   memset(buf_, 0, boardBufSize * boards_.size());
   for (unsigned i=0; i<boards_.size(); i++)
   {
      ret = CiAqSetup(boards_[i], buf_ + boardBufSize*i + MAX_FRAME_OFFSET, actualBufSize, (BFS32)width_,
						    CiDMADataMem, CiLutBank0, CiLut8Bit, CiQTabBank0, TRUE,
						    CiQTabModeOneBank,AqEngJ);

      if (ret != BF_OK)
         return ret;
   }

   initialized_ = true;

   return DEVICE_OK;
}

int BFCamera::Shutdown()
{
   for (unsigned i=0; i<boards_.size(); i++)
      CiBrdClose(boards_[i]);

   boards_.clear();
   delete[] buf_;
   buf_ = 0;
   
   width_ = 0;
   height_ = 0;
   depth_ = 0;

   initialized_ = false;

   return DEVICE_OK;
}

const unsigned char* BFCamera::GetImage(unsigned& retCode, char* errText, unsigned bufLen, BitFlowCamera* cam)
{
   if (!initialized_)
      return 0;

   BFRC ret (BF_OK);
   for (unsigned  i=0; i<boards_.size(); i++)
   {
      ret = CiAqCommand(boards_[i], CiConSnap, CiConAsync, CiQTabBank0, AqEngJ);
      if (ret != BF_OK)
      {
         retCode = ret;
         BFErrorGetMes(boards_[i], ret, errText, bufLen);
         char buf[125];
         //sprintf(buf, "acq board %d\n", i);
         if (cam)
            cam->ShowError(buf);
         return 0; // TODO: cleanup on error???
      }
   }
  
   for (unsigned  i=0; i<boards_.size(); i++)
   {
      ret = CiAqWaitDone(boards_[i], AqEngJ);
      if (ret != BF_OK)
      {
         retCode = ret;
         BFErrorGetMes(boards_[i], ret, errText, bufLen);
         return 0; // TODO: cleanup on error???
         char buf[125];
         //printf(buf, "done board %d\n", i);
         cam->ShowError(buf);
      }
   }
   
   retCode = ret;
   return buf_;
}

bool BFCamera::WaitForImage()
{
   for (unsigned  i=0; i<boards_.size(); i++)
   {
      int ret = CiAqWaitDone(boards_[i], AqEngJ);
      if (ret != BF_OK)
      {
         //BFErrorGetMes(boards_[i], ret, errText, bufLen);
         return false;
      }
   }
   return true;
}

const unsigned char* BFCamera::GetImageCont()
{
	core_->LogMessage(caller_,"ContImage1",true);
	if (!initialized_ || boards_.empty())
		return 0;
	core_->LogMessage(caller_,"ContImage2",true);

	BFRC ret = CiSignalNextWait(boards_[0], &eofSignal_, timeoutMs_);
	core_->LogMessage(caller_,"ContImage3",true);
	if (ret != BF_OK) {
		if (ret == BF_BAD_SIGNAL) {
			core_->LogMessage(caller_,"BF Get image error: Bad signal--invalid board handle passed to function ",true);
		} else if (ret == BF_SIGNAL_TIMEOUT) {
			core_->LogMessage(caller_,"BF Get image error: Timeout ",true);
		} else if (ret == BF_SIGNAL_CANCEL) { 
			core_->LogMessage(caller_,"BF Get image error: Signal canceled by another thread ",true);
		} else if (ret == BF_WAIT_FAILED) {
			core_->LogMessage(caller_,"BF Get image error: Operating system killed the signal ",true);
		}
		return 0;
	}

   // wait for transfer to complete
   //Sleep(5);
	core_->LogMessage(caller_,"ContImage4",true);
   return buf_;
}

int BFCamera::StartContinuousAcq()
{
   if (isAcquiring())
      return BF_BUSY_ACQUIRING;

   if (!initialized_ || boards_.empty())
      return BF_NOT_INITIALIZED;

   // create a signal
   BFRC ret = CiSignalCreate(boards_[0], CiIntTypeEOD, &eofSignal_);
   if (ret != BF_OK)
      return ret;

   // start acquiring
   for (unsigned i=0; i<boards_.size(); i++)
   {
      BFRC ret = CiAqCommand(boards_[i], CiConGrab, CiConWait, CiQTabBank0, AqEngJ);
      if (ret != BF_OK)
      {
         CiSignalFree(boards_[0], &eofSignal_);
         return ret;
      }
   }

   acquiring_ = true;

   return DEVICE_OK;
}

int BFCamera::StopContinuousAcq()
{
   if (!isAcquiring())
      return DEVICE_OK;


   for (unsigned i=0; i<boards_.size(); i++)
   {
      BFRC ret = CiAqCommand(boards_[i], CiConFreeze, CiConWait, CiQTabBank0, AqEngJ);
      if (ret != BF_OK)
      {
         acquiring_ = false;
         return ret; // returning immediately, but what about remaining boards?
      }
   }

   /*
   for (unsigned i=0; i<boards_.size(); i++)
   {
      BFRC ret = CiAqCommand(boards_[i], CiConReset, CiConWait, CiQTabBank0, AqEngJ);
      if (ret != BF_OK)
      {
         acquiring_ = false;
         return ret; // returning immediately, but what about remaining boards?
      }
   }
*/
   CiSignalFree(boards_[0], &eofSignal_);
   acquiring_ = false;

   return DEVICE_OK;
}


int BFCamera::StartSequence()
{
   if (!initialized_ || boards_.empty())
      return BF_NOT_INITIALIZED;

   core_->LogMessage(caller_,"StartSequence1",true);
   // create a signal
   BFRC ret = CiSignalCreate(boards_[0], CiIntTypeEOD, &eofSignal_);
      core_->LogMessage(caller_,"StartSequence2",true);
   if (ret != BF_OK)
      return ret;

   // start acquiring
   for (unsigned i=0; i<boards_.size(); i++)
   {
	  core_->LogMessage(caller_,"StartSequence3",true);
      BFRC ret = CiAqCommand(boards_[i], CiConGrab, CiConWait, CiQTabBank0, AqEngJ);
         core_->LogMessage(caller_,"StartSequence4",true);
	  if (ret != BF_OK)
      {
	        core_->LogMessage(caller_,"StartSequence5",true);
         CiSignalFree(boards_[0], &eofSignal_);
         return ret;
      }
   }

   return DEVICE_OK;
}

int BFCamera::StopSequence()
{
	   core_->LogMessage(caller_,"StopSequence1",true);
   for (unsigned i=0; i<boards_.size(); i++)
   {
      core_->LogMessage(caller_,"StopSequence i",true);
      core_->LogMessage(caller_,CDeviceUtils::ConvertToString((int)i),true);
      BFRC ret = CiAqCommand(boards_[i], CiConFreeze, CiConWait, CiQTabBank0, AqEngJ);
	  core_->LogMessage(caller_,"StopSequence i2",true);
      if (ret != BF_OK)
      {
		  core_->LogMessage(caller_,"StopSequence i3",true);
         return ret; // returning immediately, but what about remaining boards?
      }
   }
   core_->LogMessage(caller_,"StopSequence2",true);
   CiSignalFree(boards_[0], &eofSignal_);
   core_->LogMessage(caller_,"StopSequence3",true);
   return DEVICE_OK;
}


