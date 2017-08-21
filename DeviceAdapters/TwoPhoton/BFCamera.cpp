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
// UPDATED:		  Henry Pinkard, 2015
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
	imageCount_(0),
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

void BFCamera::UseVFGs(string s) {
	useVFGs_.clear();
	  for (unsigned i=0; i<8; i++) {
		  char val = *(s.c_str() + i);
		  useVFGs_.push_back(val == '0' ? 0 : 1);
	  }
}

bool BFCamera::VFGActive(int index) {
	return useVFGs_.size() > index && useVFGs_[index] == 1;
}

int BFCamera::Initialize(MM::Device* caller, MM::Core* core) {
	caller_ = caller;
	core_ = core;

	

	// close existing boards
	Shutdown();
	// find the number of available boards
	BFU32 num = 0;

	BFRC ret = CiSysBrdEnum(CISYS_TYPE_R64, &num);
	if (ret != BF_OK)
		return ret;
	core_->LogMessage(caller_,"num bitflow channels", true );
	core_->LogMessage(caller_, CDeviceUtils::ConvertToString((int)num), true );
	if (num == 0) {
		core_->LogMessage(caller_,"0 bitflow channels detected", true );
		return BF_NO_CHANNELS_DETECTED;
	}

	// open all of them
	if (!dual_ && num > 4)
		num = 4;

	core_->LogMessage(caller_,CDeviceUtils::ConvertToString((int) num), true );
	boards_.clear();
	eofSignals_.clear();
	for (unsigned i=0; i<num; i++) {  
		//ignore problematic boards
		if  (!useVFGs_[i]) {
			continue;
		}

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

		if (width_==0 && height_==0 && depth_ == 0) {
			// first time
			width_ = width;
			height_ = height;
			depth_ = depth;
		} else {
			if (width != width_ || height != height_ || depth_ != depth)
			{
				CiBrdClose(board);
				return BF_INCOMPATIBLE_CAMERAS;
			}
		}

		boards_.push_back(board);
		eofSignals_.push_back(new CiSIGNAL());
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

    // create signals for end of frames here
	//do this on camera init instead of startup to eliminate unneccsary wating of boards on successive signals
	for (int i = 0; i < boards_.size(); i++) {
		BFRC ret = CiSignalCreate(boards_[i], CiIntTypeEOD, eofSignals_[i]);
		if (ret != BF_OK)
			return ret;
	}

	initialized_ = true;

	return DEVICE_OK;
}



int BFCamera::Shutdown() {
	for (int i = 0; i < eofSignals_.size(); i++) {
		CiSignalFree(boards_[i], eofSignals_[i]); //shut down end of frame signals
		delete eofSignals_[i];
	}

	for (unsigned i=0; i<boards_.size(); i++)
		CiBrdClose(boards_[i]);

	boards_.clear();
	delete[] buf_;
	buf_ = 0;


	eofSignals_.clear();

	width_ = 0;
	height_ = 0;
	depth_ = 0;

	initialized_ = false;

	return DEVICE_OK;
}

void BFCamera::LogInterrupts() {
	char message[200];
	strcpy(message,"Num interrupts by channel ");
	for (int i = 0; i < boards_.size(); i++) {
		BFU32 numIn;
		CiSignalQueueSize(boards_[i],eofSignals_[i],&numIn);
		int numI = (int) numIn;
		strcat(message, CDeviceUtils::ConvertToString(numI));
		strcat(message," ");
	}
	core_->LogMessage(caller_,message, true );
}

const unsigned char* BFCamera::GetImageCont() {
	//this function returns the location of the buffer that the bitflow boards are DMAing data into,
	//The boards will continue to overwrite data at this location, so the only way to guarantee
	//frames aren't lost is if copying data from this buffer is a lot faster than Bitflow can DMA
	if (!initialized_ || boards_.empty())
		return 0;

	//Like CiSignalWait, this function waits efficiently for an interrupt. However, this version
	//always ignores any interrupts that might have occurred since it was called last, and
	//just waits for the next interrupt.
	for (int i = 0; i < boards_.size(); i++) {
		//BFRC ret = CiSignalNextWait(boards_[i], eofSignals_[i], timeoutMs_); // this one will wait for next interrupt
		//-CiSignalWait -- efficiently waits for an interrupt to occur. Returns immediately if one has occurred
		//since the function was last called.
		//-The first time this function is called with a given signal, 
		//it will always wait, even if the interrupt has occurred many times in the threads lifetime.
		//---For this reason, creat and start the signals on initialization of the camera, to eliminate the possibility of 
		//unneccesary waiting at the start of acq

		char message[200];
		strcpy(message,"interrupts before wait for channel ");
		strcat(message,  CDeviceUtils::ConvertToString(i));
		//core_->LogMessage(caller_,message, true );
		//LogInterrupts();

		BFU32 numInterrupts;
		BFRC ret = CiSignalWait(boards_[i], eofSignals_[i], timeoutMs_, &numInterrupts);

		char message2[200];
		strcpy(message2,"Interrupts after wait for channel ");
		strcat(message2,  CDeviceUtils::ConvertToString(i));
		//core_->LogMessage(caller_,message2, true );
		//LogInterrupts();

		if (SignalWaitErrorInterpret(ret) == 0) {
			return 0;
		}
	}
	return buf_;
}

void BFCamera::ReloadBoardsAfterTimeoutError() {
	//close shutter
	//core_->SetDeviceProperty("EOMShutter","State",0);
	VirtualShutter* shutter = (VirtualShutter*) (core_->GetDevice(caller_,"EOMShutter"));
	shutter->SetOpen(false);
	//reinitialize to recreate signals (initialize will cll sutdown)
	Initialize(caller_, core_);
	//after this function an error is still thrown, but this should ensure that on a retry, the image can be snapped
}

int BFCamera::SignalWaitErrorInterpret(BFRC ret) {
	if (ret != BF_OK) {
		if (ret == CISYS_ERROR_BAD_BOARDPTR){ 
			core_->LogMessage(caller_,"BF Get image error: An invalid board handle was passed to the function",false);	
		} else if (ret == BF_SIGNAL_TIMEOUT) { 
			core_->LogMessage(caller_,"BF Get image error: Timeout has expired before interrupt occurred",false);
			//ReloadBoardsAfterTimeoutError();
		} else if (ret == BF_SIGNAL_CANCEL ) {
			core_->LogMessage(caller_,"BF Get image error: Signal was canceled by another thread (see CiSignalCancel)",false);	
		} else if (ret == BF_BAD_SIGNAL ) {
			core_->LogMessage(caller_,"BF Get image error: Signal has not been created correctly or was not created for this board",false);	
		} else if (ret == BF_WAIT_FAILED) { 
			core_->LogMessage(caller_,"BF Get image error: Operating system killed the signal",false);	
		} else {
			core_->LogMessage(caller_,"BF Get image error: unknown error",false);			
		}
		return 0;
	}
	return 1;
}

int BFCamera::StartAcquiring() {
	if (isAcquiring())
		return BF_BUSY_ACQUIRING;

	if (!initialized_ || boards_.empty())
		return BF_NOT_INITIALIZED;

	// start acquiring
	for (unsigned i=0; i<boards_.size(); i++) {
		//CiConWait - For a grab, the function will wait until the first frame has begun to be acquired
		BFRC ret;
		if (i == boards_.size() - 1)
			//only wait for the first frame to begin on the last board to start
			//since all boards presumably recieve the same sync signals
			ret = CiAqCommand(boards_[i], CiConGrab, CiConWait, CiQTabBank0, AqEngJ);
		else 
			 ret = CiAqCommand(boards_[i], CiConGrab, CiConAsync, CiQTabBank0, AqEngJ);

		if (ret != BF_OK) {
			return ret;
		}
	}
	//now clear the previous history if interrupts, so we're sure that interupts recieved corrspond to ones
	//specific to this acquisition
	for (unsigned i=0; i<boards_.size(); i++) {
		CiSignalQueueClear(boards_[i], eofSignals_[i]);
	}
	//What if an interrupt from a previous frame DMA transfer occurs after the next frame has started? is that possible

	acquiring_ = true;
	return DEVICE_OK;
}

int BFCamera::StopAcquiring() {
	if (!isAcquiring())
		return DEVICE_OK;

	//CiConFreeze - stop acquiring at the end of the current frame. If in between
	//frames, do not acquire any more frames.
	for (int i=0; i<boards_.size(); i++) {
		//could call CIConAsync instead of wait to return quicker and end snapimage sooner,
		//but what would happen if tried to start acquiring again before frame ended, as unlikely as that seems...		
		//-Async return right away before interrupts accuulate, as expected
		//-Wait doesnt introduce a delay if frames go directly into circular buffer, so use it to be safe
		BFRC ret = CiAqCommand(boards_[i], CiConFreeze, CiConWait, CiQTabBank0, AqEngJ);
		//BFRC ret = CiAqCommand(boards_[i], CiConFreeze, CiConAsync, CiQTabBank0, AqEngJ);

		//char message[200];
		//strcpy(message,"interrupts after closing channel ");
		//strcat(message,  CDeviceUtils::ConvertToString(i));
		//core_->LogMessage(caller_,message, false );
		//LogInterrupts();
		if (ret != BF_OK) {
			acquiring_ = false;
			return ret; // returning immediately, but what about remaining boards?
		}
	}


	acquiring_ = false;
	return DEVICE_OK;
}

