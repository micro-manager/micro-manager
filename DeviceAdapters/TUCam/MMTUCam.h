///////////////////////////////////////////////////////////////////////////////
// FILE:          MMTUCam.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
//
// COPYRIGHT:     Tucsen Photonics Co., Ltd.  2018
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

#ifndef _MMTUCAM_H_
#define _MMTUCAM_H_

#include "TUCamApi.h"
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
#define HUB_NOT_AVAILABLE        107

const char* NoHubError = "Parent Hub not defined.";

// Defines which segments in a seven-segment display are lit up for each of
// the numbers 0-9. Segments are:
//
//  0       1
// 1 2     2 4
//  3       8
// 4 5    16 32
//  6      64
const int SEVEN_SEGMENT_RULES[] = {1+2+4+16+32+64, 4+32, 1+4+8+16+64,
      1+4+8+32+64, 2+4+8+32, 1+2+8+32+64, 2+8+16+32+64, 1+4+32,
      1+2+4+8+16+32+64, 1+2+4+8+32+64};
// Indicates if the segment is horizontal or vertical.
const int SEVEN_SEGMENT_HORIZONTALITY[] = {1, 0, 0, 1, 0, 0, 1};
// X offset for this segment.
const int SEVEN_SEGMENT_X_OFFSET[] = {0, 0, 1, 0, 0, 1, 0};
// Y offset for this segment.
const int SEVEN_SEGMENT_Y_OFFSET[] = {0, 0, 0, 1, 1, 1, 2};

////////////////////////
// DemoHub
//////////////////////

class DemoHub : public HubBase<DemoHub>
{
public:
   DemoHub() :
      initialized_(false),
      busy_(false)
   {}
   ~DemoHub() {}

   // Device API
   // ---------
   int Initialize();
   int Shutdown() {return DEVICE_OK;};
   void GetName(char* pName) const; 
   bool Busy() { return busy_;} ;

   // HUB api
   int DetectInstalledDevices();

private:
   void GetPeripheralInventory();

   std::vector<std::string> peripherals_;
   bool initialized_;
   bool busy_;
};


//////////////////////////////////////////////////////////////////////////////
// CMMTUCam class
// Simulation of the Camera device
//////////////////////////////////////////////////////////////////////////////

class CTUCamThread;

class CMMTUCam : public CCameraBase<CMMTUCam>  
{
public:
    CMMTUCam();
    ~CMMTUCam();
  
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

    int PrepareSequenceAcqusition() { return DEVICE_OK; }
    int StartSequenceAcquisition(double interval);
    int StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow);
    int StopSequenceAcquisition();
    int InsertImage();
    int RunSequenceOnThread(MM::MMTime startTime);
    bool IsCapturing();
    void OnThreadExiting() throw(); 
    double GetNominalPixelSizeUm() const {return nominalPixelSizeUm_;}
    double GetPixelSizeUm() const {return nominalPixelSizeUm_ * GetBinning();}
    int GetBinning() const;
    int SetBinning(int bS);

    int IsExposureSequenceable(bool& isSequenceable) const;
    int GetExposureSequenceMaxLength(long& nrEvents) const;
    int StartExposureSequence();
    int StopExposureSequence();
    int ClearExposureSequence();
    int AddToExposureSequence(double exposureTime_ms);
    int SendExposureSequence() const;

    unsigned  GetNumberOfComponents() const { return nComponents_;};

    // action interface
    // ----------------
    // floating point read-only properties for testing
    int OnTestProperty(MM::PropertyBase* pProp, MM::ActionType eAct, long);

    int OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnPixelClock(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnGlobalGain(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnCMSMode(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnImageMode(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnBitDepth(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnFlipH(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnFlipV(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnGamma(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnContrast(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnSaturation(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnWhiteBalance(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnRedGain(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnGreenGain(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnBlueGain(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnATExposure(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnTemperature(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnFan(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnLeftLevels(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnRightLevels(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnImageFormat(MM::PropertyBase* pProp, MM::ActionType eAct);
/*
   int OnMaxExposure(MM::PropertyBase* pProp, MM::ActionType eAct);             // 设置曝光最大值上限
 
   int OnMono(MM::PropertyBase* pProp, MM::ActionType eAct);					// 彩色模式

   int OnTemperatureState(MM::PropertyBase* pProp, MM::ActionType eAct);        // 温控开关
   int OnTemperatureCurrent(MM::PropertyBase* pProp, MM::ActionType eAct);      // 当前温度
   int OnTemperatureCooling(MM::PropertyBase* pProp, MM::ActionType eAct);      // 目标温度
*/ 

    int OnReadoutTime(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnScanMode(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnErrorSimulation(MM::PropertyBase* , MM::ActionType );
    int OnCameraCCDXSize(MM::PropertyBase* , MM::ActionType );
    int OnCameraCCDYSize(MM::PropertyBase* , MM::ActionType );
    int OnTriggerDevice(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnDropPixels(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnFastImage(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnSaturatePixels(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnFractionOfPixelsToDropOrSaturate(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnShouldRotateImages(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnShouldDisplayImageNumber(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnStripeWidth(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnCCDTemp(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnIsSequenceable(MM::PropertyBase* pProp, MM::ActionType eAct);


private:
    int SetAllowedBinning();
    int SetAllowedPixelClock();
    int SetAllowedFanGear();
    int SetAllowedImageMode();

    void TestResourceLocking(const bool);
    void GenerateEmptyImage(ImgBuffer& img);
    void GenerateSyntheticImage(ImgBuffer& img, double exp);
    int ResizeImageBuffer();

    static const double nominalPixelSizeUm_;

    double exposureMaximum_;
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
    bool shouldRotateImages_;
    bool shouldDisplayImageNumber_;
    double stripeWidth_;

    double testProperty_[10];
    MMThreadLock imgPixelsLock_;
    int nComponents_;

    friend class CTUCamThread;
    CTUCamThread * thd_;

private:
	void TestImage(ImgBuffer& img, double exp);

    static void __cdecl GetTemperatureThread(LPVOID lParam);    // The thread get the value of temperature
    static void __cdecl WaitForFrameThread(LPVOID lParam);      // The thread wait for frame

    void RunWaiting();
    void RunTemperature();

    int InitTUCamApi();
    int UninitTUCamApi();

    int AllocBuffer();
    int ResizeBuffer();
    int ReleaseBuffer();

    int StopCapture();
    int StartCapture();
    int WaitForFrame(ImgBuffer& img);

    bool SaveRaw(char *pfileName, unsigned char *pData, unsigned long ulSize);

    int             m_nIdxGain;             // The gain mode
    int             m_nMaxHeight;           // The max height size
    char            m_szImgPath[MAX_PATH];  // The save image path
    float           m_fCurTemp;             // The current temperature
    float           m_fValTemp;             // The temperature value
    int             m_nMidTemp;             // The middle value of temperature
    bool            m_bROI;                 // The ROI state
    bool            m_bSaving;              // The tag of save image            
    bool            m_bTemping;             // The get temperature state
    bool            m_bLiving;              // The capturing state
	HANDLE          m_hThdWaitEvt;          // The waiting frame thread event handle
    HANDLE          m_hThdTempEvt;          // To get the value of temperature event handle

    TUCAM_INIT       m_itApi;               // The initialize SDK environment
    TUCAM_OPEN       m_opCam;               // The camera open parameters
    TUCAM_FRAME      m_frame;               // The frame object
};

class CTUCamThread : public MMDeviceThreadBase
{
   friend class CMMTUCam;
   enum { default_numImages=1, default_intervalMS = 100 };
   public:
      CTUCamThread(CMMTUCam* pCam);
      ~CTUCamThread();
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
      CMMTUCam* camera_;                                                     
      MM::MMTime startTime_;                                                    
      MM::MMTime actualDuration_;                                               
      MM::MMTime lastFrameTime_;                                                
      MMThreadLock stopLock_;                                                   
      MMThreadLock suspendLock_;                                                
}; 


#endif //_MMTUCAM_H_
