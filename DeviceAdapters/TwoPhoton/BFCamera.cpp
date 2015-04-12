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
	numInterrupts_(0),
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

	for (unsigned i=0; i<num; i++) {  
		//ignore board #1 + #5 because it tends to mirror...this should really be a a pre-initialization property instead
		//if  (dual_ && (i == 1 || i == 5)) {
		//	continue;
		//}

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

	for (int i = 0; i < eofSignals_.size(); i++) {
		delete eofSignals_[i];
	}
	eofSignals_.clear();

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
	for (unsigned  i=0; i<boards_.size(); i++) {
		//CiConSnap - starting at the beginning of the next frame, acquire one frame
		//so aynchronously start all channels, then wait for them
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
			//char buf[125];
			//printf(buf, "done board %d\n", i);
			//cam->ShowError(buf);
		}
	}

	retCode = ret;
	return buf_;
}

const unsigned char* BFCamera::GetImageCont()
{
	if (!initialized_ || boards_.empty())
		return 0;

	//Like CiSignalWait, this function waits efficiently for an interrupt. However, this version
	//always ignores any interrupts that might have occurred since it was called last, and
	//just waits for the next interrupt.
	for (int i = 0; i < boards_.size(); i++) {
		//BFRC ret = CiSignalNextWait(boards_[i], eofSignals_[i], timeoutMs_); // this one will wait for next interrupt
		//CiSignalWait -- efficiently waits for an interrupt to occur. Returns immediately if one has occurred
		//since the function was last called.
		//The latter seems better because it wont have possibility of skipping frames if another frame finished while it 
		//was transferring data from the previous one
		//but are the buffers overwritten in real time??
		BFRC ret = CiSignalWait(boards_[i], eofSignals_[i], timeoutMs_, &numInterrupts_);
		if (ret != BF_OK) {
			core_->LogMessage(caller_,"BF Get image error",false);			
			return 0;
		}
	}
	return buf_;
}

int BFCamera::StartAcquiring() {
	if (isAcquiring())
		return BF_BUSY_ACQUIRING;

	if (!initialized_ || boards_.empty())
		return BF_NOT_INITIALIZED;

	// create a signal
	for (int i = 0; i < boards_.size(); i++) {
		BFRC ret = CiSignalCreate(boards_[i], CiIntTypeEOD, eofSignals_[i]);
		if (ret != BF_OK)
			return ret;
	}

	// start acquiring
	for (unsigned i=0; i<boards_.size(); i++)
	{
		//CiConWait - For a grab, the function will wait until the first frame has begun to be acquired
		BFRC ret = CiAqCommand(boards_[i], CiConGrab, CiConWait, CiQTabBank0, AqEngJ);
		if (ret != BF_OK)
		{
			for (int i = 0; i < boards_.size(); i++)
				CiSignalFree(boards_[i], eofSignals_[i]);
			return ret;
		}
		//now that first frame is being acquired, clear any previous end of frame signals
		for (int i = 0; i < boards_.size(); i++)
			CiSignalQueueClear(boards_[i], eofSignals_[i]);
	}

	acquiring_ = true;

	return DEVICE_OK;
}

int BFCamera::StopAcquiring() {
	if (!isAcquiring())
		return DEVICE_OK;


	for (unsigned i=0; i<boards_.size(); i++)
	{
		//could call CIConAsync instead of wait to return quicker and end snapimage sooner,
		//but what would happen if tried to start acquiring again before frame ended, as unlikely as that seems...
		//BFRC ret = CiAqCommand(boards_[i], CiConFreeze, CiConWait, CiQTabBank0, AqEngJ);
		BFRC ret = CiAqCommand(boards_[i], CiConFreeze, CiConAsync, CiQTabBank0, AqEngJ);
		if (ret != BF_OK)
		{
			acquiring_ = false;
			return ret; // returning immediately, but what about remaining boards?
		}
	}

	for (int i = 0; i < boards_.size(); i++)
		CiSignalFree(boards_[i], eofSignals_[i]);
	acquiring_ = false;

	return DEVICE_OK;
}

