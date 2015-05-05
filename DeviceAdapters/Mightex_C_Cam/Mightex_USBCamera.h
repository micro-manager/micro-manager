///////////////////////////////////////////////////////////////////////////////
// FILE:          Mightex_USBCamera.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   The example implementation of the Mightex USB camera.
//                Simulates generic digital camera and associated automated
//                microscope devices and enables testing of the rest of the
//                system without the need to connect to the actual hardware.
//
// AUTHOR:        Yihui, mightexsystem.com, 12/17/2014
//
// COPYRIGHT:     University of California, San Francisco, 2006
//                100X Imaging Inc, 2008
//                Mightex Systems, 2014-2015
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

#ifndef _Mightex_USBCamera_H_
#define _Mightex_USBCamera_H_

#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/ImgBuffer.h"
#include "../../MMDevice/DeviceThreads.h"
#include <string>
#include <map>
#include <algorithm>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_UNKNOWN_MODE         102
#define ERR_UNKNOWN_POSITION     103
#define ERR_IN_SEQUENCE          104
#define ERR_SEQUENCE_INACTIVE    105
#define ERR_STAGE_MOVING         106
#define SIMULATED_ERROR          200
#define HUB_NOT_AVAILABLE        107

const char* NoHubError = "Parent Hub not defined.";


//////////////////////////////////////////////////////////////////////////////
// CMightex_BUF_USBCCDCamera class
// Simulation of the Camera device
//////////////////////////////////////////////////////////////////////////////

class MySequenceThread;

class CMightex_BUF_USBCCDCamera : public CCameraBase<CMightex_BUF_USBCCDCamera>  
{
public:
   CMightex_BUF_USBCCDCamera();
   ~CMightex_BUF_USBCCDCamera();
  
   // MMDevice API
   // ------------
   int Initialize();
   int Shutdown();
  
   void GetName(char* name) const;      
   
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
   int PrepareSequenceAcqusition()
   {
      return DEVICE_OK;
   }
   int StartSequenceAcquisition(double interval);
   int StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow);
   int StopSequenceAcquisition();
   int InsertImage();
   int ThreadRun(MM::MMTime startTime);
   bool IsCapturing();
   void OnThreadExiting() throw(); 
   double GetNominalPixelSizeUm() const {return nominalPixelSizeUm_;}
   double GetPixelSizeUm() const {return nominalPixelSizeUm_ * GetBinning();}
   int GetBinning() const;
   int SetBinning(int bS);

   int IsExposureSequenceable(bool& isSequenceable) const;
   int GetExposureSequenceMaxLength(long& nrEvents);
   int StartExposureSequence();
   int StopExposureSequence();
   int ClearExposureSequence();
   int AddToExposureSequence(double exposureTime_ms);
   int SendExposureSequence() const;

   unsigned  GetNumberOfComponents() const { return nComponents_;};

   // action interface
   // ----------------
	// floating point read-only properties for testing
	//int OnTestProperty(MM::PropertyBase* pProp, MM::ActionType eAct, long);
	//int OnSwitch(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct);
   //int OnBitDepth(MM::PropertyBase* pProp, MM::ActionType eAct);
   //int OnReadoutTime(MM::PropertyBase* pProp, MM::ActionType eAct);
   //int OnScanMode(MM::PropertyBase* pProp, MM::ActionType eAct);
   //int OnErrorSimulation(MM::PropertyBase* , MM::ActionType );
   //int OnCameraCCDXSize(MM::PropertyBase* , MM::ActionType );
   //int OnCameraCCDYSize(MM::PropertyBase* , MM::ActionType );
   //int OnTriggerDevice(MM::PropertyBase* pProp, MM::ActionType eAct);
   //int OnDropPixels(MM::PropertyBase* pProp, MM::ActionType eAct);
   //int OnFastImage(MM::PropertyBase* pProp, MM::ActionType eAct);
   //int OnSaturatePixels(MM::PropertyBase* pProp, MM::ActionType eAct);
   //int OnFractionOfPixelsToDropOrSaturate(MM::PropertyBase* pProp, MM::ActionType eAct);
   //int OnCCDTemp(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnIsSequenceable(MM::PropertyBase* pProp, MM::ActionType eAct);

	int InitCamera();
	int GetCameraBufferCount(int width, int height);
   void RGB3toRGB4(const char* srcPixels, char* destPixels, int width, int height);
   void RGB3toRGB1(const char* srcPixels, char* destPixels, int width, int height);
   int OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnGain(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnResolution(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnXStart(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnYStart(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnH_Mirror(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnV_Flip(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   int SetAllowedBinning();
   void TestResourceLocking(const bool);
   void GenerateEmptyImage(ImgBuffer& img);
   int ResizeImageBuffer();

   static const double nominalPixelSizeUm_;

   double dPhase_;
   ImgBuffer img_;
   bool busy_;
   bool stopOnOverFlow_;
   bool initialized_;
   double readoutUs_;
   MM::MMTime readoutStartTime_;
   long scanMode_;
   int bitDepth_;
   unsigned roiX_;
   unsigned roiY_;
   MM::MMTime sequenceStartTime_;
   bool isSequenceable_;
   long sequenceMaxLength_;
   bool sequenceRunning_;
   unsigned long sequenceIndex_;
   double GetSequenceExposure();
   std::vector<double> exposureSequence_;
   long imageCounter_;
	long binSize_;
	long cameraCCDXSize_;
	long cameraCCDYSize_;
   double ccdT_;
	std::string triggerDevice_;

   bool stopOnOverflow_;

	bool dropPixels_;
   bool fastImage_;
	bool saturatePixels_;
	double fractionOfPixelsToDropOrSaturate_;

	double testProperty_[10];
   MMThreadLock* pDemoResourceLock_;
   MMThreadLock imgPixelsLock_;
   friend class MySequenceThread;
   int nComponents_;
   MySequenceThread * thd_;


	HINSTANCE HDll;
	char camNames[64];
	int deviceType;
	int deviceColorType;
	int MAX_RESOLUTION;
	int s_MAX_RESOLUTION;
	bool is_initCamera;
	long yStart;
	long h_Mirror;
	long v_Flip;
};


class MySequenceThread : public MMDeviceThreadBase
{
   friend class CMightex_BUF_USBCCDCamera;
   enum { default_numImages=1, default_intervalMS = 100 };
   public:
      MySequenceThread(CMightex_BUF_USBCCDCamera* pCam);
      ~MySequenceThread();
      void Stop();
      void Start(long numImages, double intervalMs);
      bool IsStopped();
      void Suspend();
      bool IsSuspended();
      void Resume();
      double GetIntervalMs(){return intervalMs_;}                               
      void SetLength(long images) {numImages_ = images;}                        
      long GetLength() const {return numImages_;}
      long GetImageCounter(){return imageCounter_;}                             
      MM::MMTime GetStartTime(){return startTime_;}                             
      MM::MMTime GetActualDuration(){return actualDuration_;}
   private:                                                                     
      int svc(void) throw();
      double intervalMs_;                                                       
      long numImages_;                                                          
      long imageCounter_;                                                       
      bool stop_;                                                               
      bool suspend_;                                                            
      CMightex_BUF_USBCCDCamera* camera_;                                                     
      MM::MMTime startTime_;                                                    
      MM::MMTime actualDuration_;                                               
      MM::MMTime lastFrameTime_;                                                
      MMThreadLock stopLock_;                                                   
      MMThreadLock suspendLock_;                                                
}; 

#endif //_Mightex_USBCamera_H_
