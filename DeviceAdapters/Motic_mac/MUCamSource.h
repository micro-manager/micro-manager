//
//  MUCamSource.h
//  MUCamSource
//
//  Created by apple on 14-1-27.
//  Copyright (c) 2014年 Motic China Group Co., Ltd. All rights reserved.
//

#ifndef __MUCamSource__MUCamSource__
#define __MUCamSource__MUCamSource__

#include "MoticUCam.h"
#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/ImgBuffer.h"
#include "../../MMDevice/DeviceThreads.h"

using namespace std;
//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_UNKNOWN_MODE         102
//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_NO_CAMERAS_FOUND			    100
#define ERR_SOFTWARE_TRIGGER_FAILED         1004
#define ERR_BUSY_ACQUIRING                  1003
#define ERR_NO_CAMERA_FOUND                 1005

//////////////////////////////////////////////////////////////////////////
//properties
#define	g_Keyword_Cooler					"Cooler"
#define g_Keyword_Cameras         "Devices"
#define g_Keyword_MoticUI         "MoticInterface"

class MSequenceThread;

class MUCamSource : public CCameraBase<MUCamSource>
{
public:
    MUCamSource();
    ~MUCamSource();
    
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
//    int PrepareSequenceAcqusition();
//    int StartSequenceAcquisition(double interval);
    virtual int StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow);
    virtual int StopSequenceAcquisition();
    int InsertImage();
    virtual int ThreadRun(void);
    bool IsCapturing();
    int GetBinning() const;
    int SetBinning(int binSize);
    int IsExposureSequenceable(bool& seq) const {seq = false; return DEVICE_OK;}
    
    // action interface
    // ----------------
    int OnDevice(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnGain(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnExposure( MM::PropertyBase* pProp, MM::ActionType eAct);
    
    //////////////////////////////////////////////////////////////////////////
    unsigned GetNumberOfComponents() const;
    int GetComponentName(unsigned channel, char* name);
    const unsigned int* GetImageBufferAsRGB32();
    
private:
    friend class MSequenceThread;
    static const int MAX_BIT_DEPTH = 12;
    
    //MSequenceThread* thd_;
    bool stopOnOverflow_;
    int binning_;
    int bytesPerPixel_;
    int colorChannel_;
    int width_, height_;
    double gain_;
    double exposureMs_;
    float exposureMin_, exposureMax_;
    bool initialized_;
    ImgBuffer img_;
    unsigned char *tmpbuf_;
    int bufferSize_;
    int roiX_, roiY_;
    int cameraCnt_, currentCam_;
    MUCam_Handle hCameras_[100];
    vector<std::string> DevicesVec_;
    vector<long>BinningsVec_;
    int bitDepth_;
    int bitCnt_;
    void *handle_;
    
    bool LoadFunctions();
    void UnloadFunctions();
    
    MUCam_setBinningIndexUPP MUCam_setBinningIndex;
    MUCam_findCameraUPP MUCam_findCamera;
    MUCam_getTypeUPP MUCam_getType;
    MUCam_openCameraUPP MUCam_openCamera;
    MUCam_getBinningCountUPP MUCam_getBinningCount;
    MUCam_getBinningListUPP MUCam_getBinningList;
    MUCam_getBinningTypeUPP MUCam_getBinningType;
    MUCam_getFrameFormatUPP MUCam_getFrameFormat;
    MUCam_closeCameraUPP MUCam_closeCamera;
    MUCam_getFrameUPP MUCam_getFrame;
    MUCam_setExposureUPP MUCam_setExposure;
    MUCam_setBitCountUPP MUCam_setBitCount;
    MUCam_getExposureRangeUPP MUCam_getExposureRange;
    
    int ResizeImageBuffer();
    void ReAllocalBuffer(int size);
    void GenerateImage();
    int FindMoticCameras();
    char *GetMotiCamNAME(MUCam_Handle hCamera);
    int InitDevice();
    void InitBinning();
    void InitPixelType();
    void InitGain();
    void InitExposure();
    bool OpenCamera(MUCam_Handle hCamera);
};

//class MSequenceThread : public MMDeviceThreadBase
//{
//public:
//    MSequenceThread(MUCamSource* pCam);
//    ~MSequenceThread();
//    void Stop();
//    void Start(long numImages, double intervalMs);
//    bool IsStopped();
//    void Suspend();
//    bool IsSuspended();
//    void Resume();
//    double GetIntervalMs(){return intervalMs_;}
//    void SetLength(long images) {numImages_ = images;}
//    long GetLength() const {return numImages_;}
//    long GetImageCounter(){return imageCounter_;}
//    MM::MMTime GetStartTime(){return startTime_;}
//    MM::MMTime GetActualDuration(){return actualDuration_;}
//private:
//    int svc(void) throw();
//    double intervalMs_;
//    long numImages_;
//    long imageCounter_;
//    bool stop_;
//    bool suspend_;
//    MUCamSource* camera_;
//    MM::MMTime startTime_;
//    MM::MMTime actualDuration_;
//    MM::MMTime lastFrameTime_;
//    MMThreadLock stopLock_;
//    MMThreadLock suspendLock_;
//};

#endif /* defined(__MUCamSource__MUCamSource__) */
