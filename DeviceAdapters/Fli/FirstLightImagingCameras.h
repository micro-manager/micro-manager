///////////////////////////////////////////////////////////////////////////////
// FILE:          FirstLightImagingCameras.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Device adapter for C-RED2 & C-RED3 cameras for USB, Matrox, 
//				  Sapera and Edt grabbers.
//                
// AUTHOR:        JTU, 13/11/2019
//
// COPYRIGHT:     First Light Imaging Ltd, (2011-2019)
// LICENSE:       License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES. 
//

#pragma once

#include <string>
#include <thread>
#include <atomic>

#include "DeviceBase.h"
#include "FliSdk.h"

class FirstLightImagingCameras : public CCameraBase<FirstLightImagingCameras>, public IRawImageReceivedObserver
{
public:
	FirstLightImagingCameras(std::string cameraName);
	~FirstLightImagingCameras();

	// Inherited via CCameraBase
	int Initialize();
	int Shutdown();
	void GetName(char* name) const;
	long GetImageBufferSize() const;
	unsigned GetBitDepth() const;
	int GetBinning() const;
	int SetBinning(int binSize);
	void SetExposure(double exp_ms);
	double GetExposure() const;
	int SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize);
	int GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize);
	int ClearROI();
	int IsExposureSequenceable(bool& isSequenceable) const;
	const unsigned char* GetImageBuffer();
	unsigned GetImageWidth() const;
	unsigned GetImageHeight() const;
	unsigned GetImageBytesPerPixel() const;
	int SnapImage();
	int StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow);
	int StopSequenceAcquisition();
	void OnThreadExiting() throw();

	void refreshValues();

private:
	int onMaxExposure(MM::PropertyBase* pProp, MM::ActionType eAct);
	int onMaxFps(MM::PropertyBase* pProp, MM::ActionType eAct);
	int onFps(MM::PropertyBase* pProp, MM::ActionType eAct);
	int onCameraChange(MM::PropertyBase* pProp, MM::ActionType eAct);
	int onDetectCameras(MM::PropertyBase* pProp, MM::ActionType eAct);
	int onSendCommand(MM::PropertyBase* pProp, MM::ActionType eAct);
	int onBinning(MM::PropertyBase* pProp, MM::ActionType eAct);
	int onSetMaxExposure(MM::PropertyBase* pProp, MM::ActionType eAct);
	int onSetMaxFps(MM::PropertyBase* pProp, MM::ActionType eAct);
	int onBuildBias(MM::PropertyBase* pProp, MM::ActionType eAct);
	int onApplyBias(MM::PropertyBase* pProp, MM::ActionType eAct);
	int onApplySensorTemp(MM::PropertyBase* pProp, MM::ActionType eAct);
	int onShutdown(MM::PropertyBase* pProp, MM::ActionType eAct);

	void createProperties();

	// Hérité via IRawImageReceivedObserver
	virtual void imageReceived(const uint8_t* image) override;
	virtual double fpsTrigger() override;

private:
	FliSdk						_fli;
	bool						_initialized;
	std::string					_cameraName;
	FliCredTwo*					_credTwo;
	FliCredThree*				_credThree;
	std::thread					_refreshThread;
	std::atomic<bool>			_threadRunning;
	double						_fpsTrigger;
	double						_maxExposure;
	double						_maxFps;
	double						_sensorTemp;
	double						_fps;
	long						_numImages;
	bool						_croppingEnabled;
	std::vector<std::string>	_listOfCameras;
};