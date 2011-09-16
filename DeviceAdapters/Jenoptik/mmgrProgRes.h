///////////////////////////////////////////////////////////////////////////////
// FILE:          mmgrProgRes.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Implementation of Jenoptik ProgRes camera driver.
//                
// AUTHOR:        Jiri Kominek
//
// COPYRIGHT:     Jenoptik
//                
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
// CVS:           $Id: ProgRes.h 2099 2009-02-11 21:34:04Z nenad $
//

#ifndef _ProgRes_H_
#define _ProgRes_H_

#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/ImgBuffer.h"
#include "../../MMDevice/DeviceThreads.h"
#include "sdk/MexExl.h"
#include <string>
#include <map>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_UNKNOWN_MODE         102
#define ERR_UNKNOWN_POSITION     103
#define ERR_BUSY_ACQUIRING       105
#define ERR_NO_CAMERA            106
#define ERR_OPEN_FAILED          107
#define ERR_GET_TYPE_SUMMARY     108
#define ERR_GET_SERIAL           109
#define ERR_ACQ_PARAMS_ERR       110
#define ERR_ACQ_EXPOSURE         111
#define ERR_ACQ_GAMMA            112
#define ERR_ACQ_GAIN             113
#define ERR_ACQ_WHITE_BALANCE    114
#define ERR_ACQ_FOCUS            115
#define ERR_ACQ_SATURATION       116
#define ERR_ACQ_EQUALIZER        117
#define ERR_ACQ_COOLING          118
#define ERR_ACQ_TRIGGER_IN       119
#define ERR_ACQ_TRIGGER_OUT      120
#define ERR_ACQ_TRIGGER_LEVEL    121
#define ERR_ACQ_TRIGGER_ABORTED  122

#define MAX_CAMERA_NAME_LENGTH 60
//////////////////////////////////////////////////////////////////////////////
// CProgRes class
// Simulation of the Camera device
//////////////////////////////////////////////////////////////////////////////

class CProgRes : public CCameraBase<CProgRes>  
{
public:
	CProgRes();
	~CProgRes();
  
	// MMDevice API
	// ------------
	int Initialize();
	int Shutdown();
  
	void GetName(char* name) const;      
	bool CProgRes::Busy();
   
	// MMCamera API
	// ------------
	int SnapImage();
	const unsigned char* GetImageBuffer();
	const unsigned int* GetImageBufferAsRGB32();
	unsigned GetNumberOfChannels() const;
	int GetChannelName(unsigned int channel, char* name);
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
	int StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow);
	int StopSequenceAcquisition();
	double GetNominalPixelSizeUm() const {return nominalPixelSizeUm_;}
	double GetPixelSizeUm() const {return nominalPixelSizeUm_ * GetBinning();}
	int GetBinning() const;
	int SetBinning(int binSize);
   int IsExposureSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}
    int SetPixelTypesValues();

	// action interface
	// ----------------
	int OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSnapUseBlackRef(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnReadoutTime(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnColorMode(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnGain(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnAcqMode(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnGammaEnable(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnGammaValue(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnWBRed(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnWBGreen(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnWBBlue(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnEqualizerEnable(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnEqualizerLimit(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnFocusState(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnFocusRed(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnFocusGreen(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnFocusBlue(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnFocusValue(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSaturationMode(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSaturationValue(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnCameraCooling(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriggerInEnable(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriggerOutOnStartExposure(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriggerOutOnEndExposure(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriggerOutOnEndTransfer(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriggerOutLevel(MM::PropertyBase* pProp, MM::ActionType eAct);
	void SetStopTime();

	// camera GUID
	unsigned __int64 m_GUID;
	// List of cameras properties
	mexCameraTypeSummary m_CameraTypeSummary;
	mexAcquisParams m_AcqParams;
	unsigned short* m_pRed;
	unsigned short* m_pGreen;
	unsigned short* m_pBlue;
	ImgBuffer m_Image;
	CRITICAL_SECTION	m_CSec;
	bool m_SnapFinished;
	int PushImage();
	long m_FocusValue;
	int m_TriggerIn;
	
private:

	MM::MMTime readoutStartTime_;
	MM::MMTime readoutStopTime_;

	static const double nominalPixelSizeUm_;

	//Do necessary for capturing
	//Is called from the thread function
	//Overrides ones defined in the CCameraBase class 
	int ThreadRun();
	//   AcqSequenceThread* acqThread_;

	//unsigned char* rawBuffer_;
	//ImgBuffer img_;
	bool initialized_;
	double readoutUs_;
	long scanMode_;
	bool color_;
	bool acquiring_;
	double exposure_;
	double wbred_;
	double wbgreen_;
	double wbblue_;
	mexEqualizer equalizer_;
	mexGamma gamma_;
	mexExposureCtrl exp_control_;
	mexFocus focus_;
	mexSaturationCtrl saturation_;
	mexTriggerOut trigger_;

	int ResizeImageBuffer();
	int SetupAcquisition(int live = 0);
	void RecalculateROI();
	RECT roi_;
};

#endif //_mmgrProgRes_H_
