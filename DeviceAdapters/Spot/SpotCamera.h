///////////////////////////////////////////////////////////////////////////////
// FILE:          SpotCamera.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Diagnostic Spot Camera DeviceAdapter class
// COPYRIGHT:     University of California, San Francisco, 2009
//
// AUTHOR:        Karl Hoover, UCSF
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
// CVS:           
//

#ifndef _SPOTCAMERA_H_
#define _SPOTCAMERA_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/DeviceUtils.h"
#include "../../MMDevice/ImgBuffer.h"

#include <string>
#include <map>
typedef unsigned short uint16_t;


//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_UNKNOWN_MODE         102
#define ERR_UNKNOWN_POSITION     103
#define ERR_BUSY_ACQIRING        104

#define SPOTCAM_CIRCULAR_BUFFER_IMG_COUNT  (1)
#define MEBIBYTESINVERSE (1./(1024.*1024.))

#define MAX_SPOT_CAMERAS 5  // TODO allow up to this number of camera devices to be instantiated in the system

// forward declare so that we can keep the vendor-specific #defines, etc. hidden
// from the rest of the system.
class SpotDevice;

class SpotCamera : public CCameraBase<SpotCamera>  
{
public:
	// the only public ctor
   SpotCamera(const char* szDeviceName);
   virtual ~SpotCamera();
  
   // MMDevice API
   // ------------
   int Initialize();
   int Shutdown();
  
   void GetName(char* name) const;      
   bool Busy();
   
   // MMCamera API
   // ------------
   int SnapImage();
   const unsigned char* GetImageBuffer();
   const unsigned int* GetImageBufferAsRGB32();
   unsigned int GetNumberOfComponents() const;
   int GetComponentName(unsigned channel, char* name);
         
   unsigned GetImageWidth() const;
   unsigned GetImageHeight() const;
   unsigned GetImageBytesPerPixel() const;
   unsigned GetBitDepth() const;
   long GetImageBufferSize() const;
   double GetExposure() const;
   void SetExposure(double exp);
   int SetROI( unsigned x, unsigned y, unsigned xSize, unsigned ySize ); 
   int GetROI( unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize ); 
   int ClearROI();
   
   virtual int StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow);
   virtual int StopSequenceAcquisition();
	int ThreadRun(void);
   
   int GetBinning() const;
   int SetBinning(int binSize);
    
   // Sequence related functions
   // -------------------------
  int IsExposureSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}


   // action interface
   // ----------------
   int OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnGain(MM::PropertyBase* pProp, MM::ActionType eAct);

	int OnMultiShotExposure(MM::PropertyBase* pProp_a, MM::ActionType eAct_a, long componentIndex_a);

   int OnTemperature(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTemperatureSetpoint(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnColorMode(MM::PropertyBase* pProp, MM::ActionType eAct);
   
   int OnReadoutTime(MM::PropertyBase* /* pProp */, MM::ActionType /* eAct */) { return DEVICE_OK; };
	int OnCamera(MM::PropertyBase* pProp, MM::ActionType eAct);

	// autoexposure mode calculates exposure for each snap frame
	int OnAutoExposure(MM::PropertyBase* pProp, MM::ActionType eAct);
	// autoexposure calculation can be setup to assume brightfield OR darkfield images.
	int OnAutoExpImageType(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnActualGain(MM::PropertyBase* pProp, MM::ActionType eAct);
	// display the sensor pixel size in nm
	int OnPixelSize(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriggerMode(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnBitDepth(MM::PropertyBase* pProp, MM::ActionType eAct);

	// sensor chip correction map on or off...
	int OnChipDefectCorrection(MM::PropertyBase* pProp, MM::ActionType eAct);
	// some cameras allow user-selection of continuous or preemptable clearing
	int OnClearingMode(MM::PropertyBase* pProp, MM::ActionType eAct);
	// speckle noise filter setting, factory default is 15%, we default to 0% -> off
	int OnNoiseFilterPercent(MM::PropertyBase* pProp, MM::ActionType eAct);

	// expose  CDeviceBase accessors, so that PImpl can use them
	MM::MMTime GetCurrentMMTime()
	{
		return CCameraBase<SpotCamera>::GetCurrentMMTime();
	};


	// methods for SpotCamera
	int NextSequentialImage(ImgBuffer& img);
	bool AutoExposure() const;
	void AutoExposure( const bool mode);
	int CallBackToCamera(void);
	int RestartSequenceAcquisition() { return StartSequenceAcquisition(numImages_, interval_ms_, stopOnOverflow_);}
	
	bool ExposureComplete(void)const { return exposureComplete_;};
	void ExposureComplete(const bool val_a) {exposureComplete_ = val_a;};

	// this property is a flag that is set when autoexposure computation is completed.
	bool AutoEposureCalculationDone(void) const { return autoEposureCalculationDone_;};
	void AutoEposureCalculationDone(const bool val_a) { autoEposureCalculationDone_ = val_a;};

	// SnapImageStartTime is millisecond time stamp of beginning of snapimage sequence
	double SnapImageStartTime(void)const { return snapImageStartTime_;};
	void SnapImageStartTime(double val_a) { snapImageStartTime_ = val_a;};

private:  

	// hide any default ctor
	SpotCamera(){};
	// hide any default assigner
	SpotCamera& operator=( const SpotCamera&){};
	
	// using the popular PImpl idiom.
	SpotDevice* pImplementation_;
	
	int selectedCameraIndexRequested_; // requested
	int selectedCameraIndex_; // actually opened

   std::string deviceName_;

   ImgBuffer imageBuffer;
   bool initialized;  

   unsigned int numberOfChannels_;
	unsigned long imageCounter_;
	unsigned long numImages_;
	//unsigned long sequenceLength_;
	bool init_seqStarted_;
	short frameRate_;
	bool stopOnOverflow_;
	double interval_ms_;

	// cached computed or returned exposure time
	double exposureTime_;
	double exposureTimeRequested_;

	// for performance measurement:
	double snapImageStartTime_; 
	double previousFrameRateStartTime_;


   ImgBuffer img_[3];
   bool initialized_;

	bool acquiring_;
   long readoutUs_;
   MM::MMTime readoutStartTime_;
   bool color_;
   unsigned char* rawBuffer_;
	unsigned long rawBufferSize_;

   int ResizeImageBuffer(  int imageSizeW = 1, 
                           int imageSizeH = 1);
   int ResizeImageBuffer(
                           int imageSizeW, 
                           int imageSizeH, 
                           int byteDepth, 
                           int binSize = 1);

	// interrupt whatever the camera is currently doing
	int ShutdownImageBuffer(){	/* todo */ return DEVICE_OK;}

	bool startme_;
	bool stopRequest_;
	MMThreadLock stopRequestLock_;
	MMThreadLock cameraMMLock_;
	bool WaitForExposureComplete(void);

	// this flag is set when camera signals that the image has been acquired
	// for spotcam the callback event is SPOT_STATUSIMAGEREAD which indiates that the image
	// is the process of being read
	bool exposureComplete_;
	
	bool autoEposureCalculationDone_;
};

#endif //_SPOTCAMERA_H_
