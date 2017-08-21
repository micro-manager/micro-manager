///////////////////////////////////////////////////////////////////////////////
// FILE:          TIScamera.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   TIS (TheImagingSource) camera module
//                
// AUTHOR:        Falk Dettmar, falk.dettmar@marzhauser-st.de, 02/26/2010
// COPYRIGHT:     Marzhauser SensoTech GmbH, Wetzlar, 2010
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
//  these IEEE1394 cameras were tested:
//  DMK 21AF04 , DMK 21BF04 , DMK 31BF03
//  DFK 21BF04 , DFK 31BF03 , DFK 41BF02
//
//
#ifndef _TIS_CAMERA_H_
#define _TIS_CAMERA_H_

// enable or disable an independent active movie window
#define ACTIVEMOVIE false

#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/ImgBuffer.h"
#include "../../MMDevice/DeviceUtils.h"
#include "../../MMDevice/DeviceThreads.h"
#include <string>
#include <sstream>
#include <map>

#include "tisudshl.h"
#include <algorithm>
#include "SimplePropertyAccess.h"


// error codes


// forward declaration
class AcqSequenceThread;


//////////////////////////////////////////////////////////////////////////////
// Implementation of the MMDevice and MMCamera interfaces
//
class CTIScamera : public CCameraBase<CTIScamera>
{
public:

static CTIScamera* GetInstance();

~CTIScamera();

CPropertyAction *pSelectDevice;
CPropertyAction *pShowProperties;

// the only public ctor
CTIScamera();

// MMDevice API
int Initialize();
int Shutdown();

void GetName(char* name) const;      
//bool Busy();

// MMCamera API
int SnapImage();
const unsigned char* GetImageBuffer();
unsigned GetImageWidth() const;
unsigned GetImageHeight() const;
unsigned GetImageBytesPerPixel() const;
long     GetImageBufferSize() const;
unsigned GetBitDepth() const;

    unsigned GetNumberOfComponents() const;
	double GetPixelSizeUm() const {return 1.0 * GetBinning();};
	int GetBinning() const;
	int SetBinning(int binSize);
   int IsExposureSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}
	void SetExposure(double dExp_ms);
	double GetExposure() const;
	int SetROI(unsigned  x, unsigned  y, unsigned  xSize, unsigned  ySize);
	int GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize);
	int ClearROI();

	int StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow);

    /*
    * Continuous sequence acquisition.  
    * Default to sequence acquisition with a high number of images
    */
    int StartSequenceAcquisition(double interval)
    {
       return StartSequenceAcquisition(LONG_MAX, interval, false);
    }

	int StopSequenceAcquisition();
    int StopSequenceAcquisition(bool temporary);

	int RestartSequenceAcquisition();
	bool IsCapturing();

	// action interface for the camera
	// -------------------------------
	int OnBinning           (MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnExposure          (MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnPixelType         (MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnBrightness        (MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnGain              (MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnGainAuto          (MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnWhiteBalance      (MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnWhiteBalanceRed   (MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnWhiteBalanceBlue  (MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnWhiteBalanceGreen (MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnWhiteBalanceAuto  (MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnAutoExposure      (MM::PropertyBase* pProp, MM::ActionType eAct);

	int OnSelectDevice      (MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnShowPropertyDialog(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnFlipHorizontal    (MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnFlipVertical      (MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnRotate			(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnDeNoise           (MM::PropertyBase* pProp, MM::ActionType eAct);

	int OnCamera			(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriggerMode		(MM::PropertyBase* pProp, MM::ActionType eAct);
		/*
		int OnContrast    (MM::PropertyBase* pProp, MM::ActionType eAct);

		int OnGammaMode   (MM::PropertyBase* pProp, MM::ActionType eAct);
		int OnGamma       (MM::PropertyBase* pProp, MM::ActionType eAct);

		int OnRedGain     (MM::PropertyBase* pProp, MM::ActionType eAct);
		int OnBlueGain    (MM::PropertyBase* pProp, MM::ActionType eAct);
		int OnGreenGain   (MM::PropertyBase* pProp, MM::ActionType eAct);
		*/

	// custom interface for the burst thread
	int PushImage();
	int DisableTrigger();

private:

    static CTIScamera* instance_;
    static unsigned refCount_;

    bool initialized_;

	long lCCD_Width, lCCD_Height;
	unsigned int uiCCD_BitsPerPixel;
	bool busy_;

    double currentExpMS_; // value used by camera

	bool sequenceRunning_;
    bool sequencePaused_;
	double FPS_;
	long Brightness_;
	long Rotation_;
	long WhiteBalance_;
	long WhiteBalanceRed_;
	long WhiteBalanceBlue_;
	long WhiteBalanceGreen_;
	long Gain_;
	long DeNoiseLevel_;


	ImgBuffer img_;

	void RecalculateROI();
	int  ResizeImageBuffer();

	int StopCameraAcquisition();

	unsigned roiX_;
	unsigned roiY_;
	unsigned roiXSize_;
	unsigned roiYSize_;

	long binSize_;


	MM::MMTime sequenceStartTime_;

	double interval_ms_;
	long lastImage_;
	long imageCounter_;
    MM::MMTime startTime_;
 	unsigned long sequenceLength_;
   bool stopOnOverflow_;

	int SetupProperties();
	smart_com<DShowLib::IFrameFilter> pROIFilter;
	smart_com<DShowLib::IFrameFilter> pRotateFlipFilter;
	smart_com<DShowLib::IFrameFilter> pDeNoiseFilter;

	CSimplePropertyAccess *m_pSimpleProperties; // This class handles the camera properties
	std::string XMLPath;

	DShowLib::tIVCDAbsoluteValuePropertyPtr pExposureRange;
	DShowLib::tIVCDSwitchPropertyPtr        pExposureAuto;

	friend class AcqSequenceThread;
	AcqSequenceThread* seqThread_; // burst mode thread
};

/*
* Acquisition thread
*/
class AcqSequenceThread : public MMDeviceThreadBase
{
public:
   AcqSequenceThread(CTIScamera* pCamera) : 
	  intervalMs_(100.0),
      numImages_(1),
      waitTime_(10),
	  busy_(false),
	  stop_(false)
   {
      camera_ = pCamera;
   };
   ~AcqSequenceThread() {}

   int svc(void);

   void SetInterval(double intervalMs) {intervalMs_ = intervalMs;}
   void SetWaitTime (long waitTime) { waitTime_ = waitTime;}
   void SetTimeOut (long imageTimeOut) { imageTimeOut_ = imageTimeOut;}
   void SetLength(long images) {numImages_ = images;}
   void Stop() {stop_ = true;}
   void Start() {stop_ = false; activate();}

private:
   CTIScamera* camera_;
   double intervalMs_;
   long numImages_;
   long waitTime_;
   long imageTimeOut_;
   bool busy_;
   bool stop_;
};


class DriverGuard
{
public:
   DriverGuard(const CTIScamera * cam);
   ~DriverGuard();
};


#endif //_TIS_CAMERA_H_
