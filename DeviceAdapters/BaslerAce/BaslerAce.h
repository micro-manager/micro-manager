///////////////////////////////////////////////////////////////////////////////
// FILE:          BaslerAce.h
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Adapter for Basler Ace Cameras
//
// Copyright 2018 Henry Pinkard
//
// Redistribution and use in source and binary forms, with or without modification, 
// are permitted provided that the following conditions are met:
//
// 1. Redistributions of source code must retain the above copyright notice, this 
// list of conditions and the following disclaimer.
//
// 2. Redistributions in binary form must reproduce the above copyright notice, this
// list of conditions and the following disclaimer in the documentation and/or other 
// materials provided with the distribution.
//
// 3. Neither the name of the copyright holder nor the names of its contributors may
// be used to endorse or promote products derived from this software without specific 
// prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY
// EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES 
// OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT 
// SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, 
// INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
// TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR 
// BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN 
// CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN 
// ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH 
// DAMAGE.


#pragma once

#include "DeviceBase.h"
#include "DeviceThreads.h"
#include <string>
#include <vector>
#include <map>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
//#define ERR_UNKNOWN_BINNING_MODE 410


//////////////////////////////////////////////////////////////////////////////
// Basler USB Ace camera class
//////////////////////////////////////////////////////////////////////////////

class BaslerCamera : public CCameraBase<BaslerCamera>  {
public:
	BaslerCamera();
	~BaslerCamera();

	// MMDevice API
	// ------------
	int Initialize();
	int Shutdown();

	void GetName(char* name) const;      
	bool Busy() {return false;}

	// MMCamera API
	// ------------
	int SnapImage();
	const unsigned char* GetImageBuffer();

	unsigned GetImageWidth() const;
	unsigned GetImageHeight() const;
	unsigned GetImageBytesPerPixel() const;
	unsigned GetBitDepth() const;
	long GetImageBufferSize() const;
	double GetExposure() const;
	void SetExposure(double exp);
	int SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize); 
	int GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize); 
	int ClearROI();
	int GetBinning() const;
	int SetBinning(int binSize);
	int IsExposureSequenceable(bool& seq) const {seq = false; return DEVICE_OK;}

	/**
	* Starts continuous acquisition.
	*/
	int StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow);
	int StartSequenceAcquisition(double interval_ms);
	int StopSequenceAcquisition();
	int PrepareSequenceAcqusition();

	/**
	* Flag to indicate whether Sequence Acquisition is currently running.
	* Return true when Sequence acquisition is activce, false otherwise
	*/
	bool IsCapturing();

	// action interface
	// ----------------
	int OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnGain(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnOffset(MM::PropertyBase* pProp, MM::ActionType eAct);
	// int OnSensorReadoutMode(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnShutterMode(MM::PropertyBase* pProp, MM::ActionType eAct);


private:

	CInstantCamera *camera_;


	unsigned maxWidth_, maxHeight_;
	double exposure_us_, exposureMax_, exposureMin_;
	double gain_, gainMax_, gainMin_;
	double offset_, offsetMin_, offsetMax_;
	unsigned bitDepth_;
	std::string pixelType_;
	std::string sensorReadoutMode_;
	std::string shutterMode_;
	void* imgBuffer_;
	INodeMap* nodeMap_;

	bool initialized_;

	//MM::MMTime startTime_;


	void ResizeSnapBuffer();
};

//Callback class for putting frames in circular buffer as they arrive
class CircularBufferInserter : public CImageEventHandler {
private:
	BaslerCamera* dev_;

public:
	CircularBufferInserter(BaslerCamera* dev);

	virtual void OnImageGrabbed( CInstantCamera& camera, const CGrabResultPtr& ptrGrabResult);
};


