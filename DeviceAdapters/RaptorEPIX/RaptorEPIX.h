///////////////////////////////////////////////////////////////////////////////
// FILE:          RaptorEPIX.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Implements the Raptor camera device adaptor using EPIX frame grabber.
//				  Supported cameras: Falcon, Kite and OWL.
//				  DemoCamera.cpp modified by KBIS for Raptor Photonics camera support
//                
// AUTHOR:        DB @ KBIS, 10/15/2012
//
// COPYRIGHT:     Raptor Photonics Ltd, (2011, 2012)
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

#ifndef _RaptorEPIX_H_
#define _RaptorEPIX_H_

#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/ImgBuffer.h"
#include "../../MMDevice/DeviceThreads.h"
//#include "../../MMDevice/Debayer.h"

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
#define SIMULATED_ERROR          200




//////////////////////////////////////////////////////////////////////////////
// CRaptorEPIX class
// Raptor Photonics Camera attached to EPIX frame grabber device
//////////////////////////////////////////////////////////////////////////////

class MySequenceThread;

class CRaptorEPIX : public CCameraBase<CRaptorEPIX>  
{
public:
   CRaptorEPIX() {CRaptorEPIX(0);};
   CRaptorEPIX(int nCameraType);
   ~CRaptorEPIX();
  
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
   double GetExposureCore() const;
   void SetExposure(double exp) {SetExposure(exp, true);};
   void SetExposure(double exp, bool bUpdate);
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
   int ThreadRun();
   bool IsCapturing();
   void OnThreadExiting() throw(); 
   double GetNominalPixelSizeUm() const {return nominalPixelSizeUm_;}
   double GetPixelSizeUm() const {return nominalPixelSizeUm_ * GetBinning();}
   int GetBinning() const;
   int SetBinning(int bS);
   int GetBinningX() const;
   int SetBinningX(int bS);
   int GetBinningY() const;
   int SetBinningY(int bS);
   int GetExtTrigger() const;
   int SetExtTrigger(int val);
   double GetFrameRate() const;
   double GetFixedFrameRate() const;
   void SetFrameRate(double dRate);
   void SetFixedFrameRate(double dRate);

   int SetVideoFormat(int cameraType_, char* driverparms);

   unsigned long long Convert4UCharToULong(unsigned char* val) const;
   unsigned long long Convert5UCharToULong(unsigned char* val) const;
   double GetTrigDelay() const;
   void SetTrigDelay(double val);

   double ConvertTECValueToTemp(int nVaue) const ;
   int ConvertTECTempToValue(double dVaue) const ;

   double GetTECSetPoint() const;
   void SetTECSetPoint(double nValue) ;

   void SetNUCState(int val);
   int  GetNUCState() const;
   void SetPeakAvgLevel(int val);
   int  GetPeakAvgLevel() const;
   void SetAGCExpSpeed(int nAGC, int nExp);
   int  GetAGCExpSpeed(int *nAGC, int *nExp) const;
   void SetROIAppearance(int val);
   int  GetROIAppearance() const;
   void SetAutoExposure(int val);
   int  GetAutoExposure() const;
   void SetAutoLevel(int val);
   int GetAutoLevel() const;
   void SetHighGain(int val);
   int GetHighGain() const;
   void SetHighPreAmpGain(int val);
   int GetHighPreAmpGain() const;
   void SetBlackOffset(int val);
   int GetBlackOffset() const;
   void SetHighDynamicRange(int val);
   int GetHighDynamicRange() const;
   void SetNUCMap(int val);
   int GetNUCMap() const;

   void SetHorizontalFlip(bool val) ;
   void SetBadPixel(bool val) ;
   void SetInvertVideo(bool val) ;
   void SetImageSharpen(bool val) ;
   bool GetHorizontalFlip() const;
   bool GetBadPixel() const;
   bool GetInvertVideo() const;
   bool GetImageSharpen() const;
   void SetShutterMode(int nMode);
   int GetShutterMode();

   int GetVideoPeak() const;
   int GetVideoAvg() const;

   long GetEMGain() const;
   int SetEMGain(long nGain);
   double GetGain() const;
   int SetGain(double dGain);
   int IsExposureSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}

	int GetReadoutMode() const;
	int SetReadoutMode(unsigned char v1 );
	int GetReadoutClock( ) const;
	int SetReadoutClock(int nRate ) ;

	double GetShutterDelayOpen() const;
	double GetShutterDelayClose() const;
	void SetShutterDelayOpen(double dVal);
	void SetShutterDelayClose(double dVal);

   unsigned  GetNumberOfComponents() const { return nComponents_;};

   // action interface
   // ----------------
	// floating point read-only properties for testing
	int OnTestProperty(MM::PropertyBase* pProp, MM::ActionType eAct, long);
   int OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnBinningX(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnBinningY(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnBinningXY(MM::PropertyBase* pProp, MM::ActionType eAct, bool bBinX);

   int OnGain(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnBitDepth(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnReadoutTime(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnScanMode(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnErrorSimulation(MM::PropertyBase* , MM::ActionType );
   int OnCameraCCDXSize(MM::PropertyBase* , MM::ActionType );
   int OnCameraCCDYSize(MM::PropertyBase* , MM::ActionType );
   int OnTriggerDevice(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnDropPixels(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSaturatePixels(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnFractionOfPixelsToDropOrSaturate(MM::PropertyBase* pProp, MM::ActionType eAct);
   MM::MMTime CurrentTime(void) { return GetCurrentMMTime(); };
   int OnMaximumExposure(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPCBTemp(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnCCDTemp(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnMicroReset(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnExtTrigger(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTrigger(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnCaptureMode(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnFrameRate(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnFrameRateUser(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnBlackOffset(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnFrameAccumulate(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnNUCMap(MM::PropertyBase* pProp, MM::ActionType eAct);

   int OnTECooler(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTECFan(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnAntiBloom(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTestPattern(MM::PropertyBase* pProp, MM::ActionType eAct);

   int OnTECSetPoint(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnNUCState(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPeakAvgLevel(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnAGCSpeed(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnExpSpeed(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnROIAppearance(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnAutoExposure(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnAutoLevel(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnHighGain(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnVideoPeak(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnVideoAvg(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTrigDelay(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnHighDynamicRange(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnFixedFrameRate(MM::PropertyBase* pProp, MM::ActionType eAct);

   int OnForceUpdate(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPostCaptureROI(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnUseDefaults(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnEPIXUnit(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnEPIXMultiUnitMask(MM::PropertyBase* pProp, MM::ActionType eAct);

   int OnUseAOI(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnAOILeft(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnAOITop(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnAOIWidth(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnAOIHeight(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTriggerTimeout(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnFrameInterval(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnUseSerialLog(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnDebayerMethod(MM::PropertyBase* pProp, MM::ActionType eAct);

   int OnReadoutRate(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnReadoutMode(MM::PropertyBase* pProp, MM::ActionType eAct);

   int OnHorizontalFlip(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnBadPixel(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnInvertVideo(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnImageSharpen(MM::PropertyBase* pProp, MM::ActionType eAct);

   int OnShutterMode(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnHighPreAmpGain(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnShutterDelayOpen(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnShutterDelayClose(MM::PropertyBase* pProp, MM::ActionType eAct);

   void UpdateAOI();


private:
   int SetAllowedBinning();
   void TestResourceLocking(const bool);
   void GenerateEmptyImage(ImgBuffer& img);
   void CaptureImage(ImgBuffer& img, double exp);
   int  GetNewEPIXImage(ImgBuffer& img, double exp);
   int ResizeImageBuffer();
   int SetMaxExposureFixedFrameRate();

	double GetMicroVersion() const;
	double GetFPGAVersion() const;
	int GetSerialNumber() const;
	int SetSystemState(unsigned char nState) const;
	unsigned char GetSystemState() const;
	int GetBloomingStatus() const ;
	double GetPCBTemp(bool bSnap=false) const ;
	double GetCCDTemp(bool bSnap=false)  ;
	int GetFPGACtrl() const ;
	int GetExtTrigStatus() const ;
	
	void SetExtTrigStatus(unsigned char val) const ;
	int GetBinningFactor() const ;
	int GetBinningFactorX() const ;
	int GetBinningFactorY() const ;
	void SetBinningFactor(int nBin) ;
	void SetBinningFactorXY(int nBinX, int nBinY) ;
	void SetMicroReset() const ;

	void SetFPGACtrl(unsigned char val) const ;
	void SetBloomingStatus(int val) const ;

	void GetROIStatus(unsigned int *nWidth, unsigned int *nHeight, unsigned int *nXOffset, unsigned int *nYOffset) const ;
	void SetROIStatus(unsigned int nWidth, unsigned int nHeight, unsigned int nXOffset, unsigned int nYOffset) const;

	int serialReadRaptorRegister1(int unit, unsigned char nReg, unsigned char* val) const;
	int serialWriteRaptorRegister1(int unit, unsigned char nReg, unsigned char val) const;

	//int serialWriteReadCmd(int unit, unsigned char* bufin, int insize, unsigned char* bufout, int outsize ) ;
	int SetLiveVideo(bool bLive) const;
	int GetLiveVideo() const;

	int GetEPROMManuData() ;
	int DisableMicro() const;

	void MyDebayer(unsigned short* pInput, unsigned short* pOutput, int nWidth, int nHeight, int nStep, int nMethod=0);

   //int serialWriteReadCmd(int unit, unsigned char* bufin, int insize, unsigned char* bufout, int outsize );

   static const double nominalPixelSizeUm_;

   int cameraType_;
   double dPhase_;
   ImgBuffer img_;
   ImgBuffer img2_;
   ImgBuffer img3_;
   bool busy_;
   bool stopOnOverflow_;
   bool initialized_;
   bool serialOK_;
   double readoutUs_;
   MM::MMTime readoutStartTime_;
   double myReadoutStartTime_;
   double myIntervalWaitTime_;
   double myFrameDiffTime_;
   long scanMode_;
   int bitDepth_;
   unsigned roiX_;
   unsigned roiY_;
   unsigned roiSnapX_;
   unsigned roiSnapY_;
   unsigned snapWidth_;
   unsigned snapHeight_;
   unsigned snapBinX_;
   unsigned snapBinY_;
   bool useAOI_;
   bool updatingAOI_;
   unsigned AOILeft_;
   unsigned AOITop_;
   unsigned AOIWidth_;
   unsigned AOIHeight_;
   unsigned LastAOILeft_;
   unsigned LastAOITop_;
   unsigned LastAOIWidth_;
   unsigned LastAOIHeight_;
   double TECSetPoint_;
   long OWLNUCState_;
   long OWLPeakAvgLevel_;
   long OWLAGCSpeed_;
   long OWLExpSpeed_;
   long OWLROIAppearance_;
   long OWLAutoExp_;
   long OWLAutoLevel_;
   long OWLHighGain_;
   double OWLTrigDelay_;
   double OWLFrameRate_;
   long OWLBlackOffset_;
   bool ForceUpdate_;
   long AntiBloom_;
	long TestPattern_;
	long FPGACtrl_;
	long ExtTrigStatus_;
	bool PostCaptureROI_;
	double dPCBTemp_;
	char strCCDTemp_[64];
	int nCCDTempCalibrated_;
	double dCCDTemp_;
	long nCCDTempCount_;
	long nPCBTempCount_;
	bool bSuspend_;
	long FrameAccumulate_;
	long FrameCount_;
	long fieldCount_;
	long fieldBuffer_;
	long fieldCount0_;
	long fieldCount1_;
	long fieldCount2_;
	long liveMode_;
	long trigSnap_;
	long HighDynamicRange_;
	long NUCMap_;
	long nBPP_;
	long nDebayerMethod_;
	double readoutRate_;
	long readoutMode_;
	long nCapturing_;

	bool bHorizontalFlip_;
	bool bInvertVideo_;
	bool bBadPixel_;
	bool bImageSharpen_;

	long nShutterMode_;
	bool bHighPreAmpGain_;
	double dShutterDelayOpen_;
	double dShutterDelayClose_;

	long binSizeX_;
	long binSizeY_;

   int serialNum_;
   int buildDateDD_;
   int buildDateMM_;
   int buildDateYY_;
   char buildCode_[6];
   int EPROM_ADC_Cal_0C_;
   int EPROM_ADC_Cal_40C_;
   int EPROM_DAC_Cal_0C_;
   int EPROM_DAC_Cal_40C_;


   MM::MMTime sequenceStartTime_;
   double mySequenceStartTime_;
   double mySnapLastTime_;
   long imageCounter_;
	long binSize_;
	long cameraCCDXSize_;
	long cameraCCDYSize_;
	std::string triggerDevice_;
	const char* cameraDeviceName_;
	const char* cameraName_;
	double triggerTimeout_;
	double frameInterval_;
	double myCaptureTime_;
	double myCaptureTime2_;
	double FixedFrameRate_;

	bool dropPixels_;
	bool saturatePixels_;
	double fractionOfPixelsToDropOrSaturate_;
	long EMGain_;
	double Gain_;

	double exposure_;
	double exposureMax_;
	double testProperty_[10];
	int triggerMode_;
	int captureMode_;
   MMThreadLock* pDemoResourceLock_;
   MMThreadLock imgPixelsLock_;
   
   int nComponents_;
   friend class MySequenceThread;
   MySequenceThread * thd_;

   int nSerialBlock_;
   //Debayer debayer_;

    int UNITS;
	int UNITMASK;
	int UNITSOPENMAP;
	int UNITSMAP;
	int MULTIUNITMASK;

};

class MySequenceThread : public MMDeviceThreadBase
{
   friend class CRaptorEPIX;
   enum { default_numImages=1, default_intervalMS = 100 };
   public:
      MySequenceThread(CRaptorEPIX* pCam);
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
      CRaptorEPIX* camera_;                                                     
      bool stop_;                                                               
      bool suspend_;                                                            
      long numImages_;                                                          
      long imageCounter_;                                                       
      double intervalMs_;                                                       
      MM::MMTime startTime_;                                                    
      MM::MMTime actualDuration_;                                               
      MM::MMTime lastFrameTime_;                                                
      MMThreadLock stopLock_;                                                   
      MMThreadLock suspendLock_;                                                
}; 



#endif //_RaptorEPIX_H_
