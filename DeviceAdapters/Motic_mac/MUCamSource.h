///////////////////////////////////////////////////////////////////////////////
// FILE:          MUCamSource.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Motic camera device adapter for OS X
// COPYRIGHT:     2014 Motic China Group Co., Ltd.
//                All rights reserved.
//
//                This library is free software; you can redistribute it and/or
//                modify it under the terms of the GNU Lesser General Public
//                License as published by the Free Software Foundation.
//
//                This library is distributed in the hope that it will be
//                useful, but WITHOUT ANY WARRANTY; without even the implied
//                warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
//                PURPOSE. See the GNU Lesser General Public License for more
//                details.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
//                LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
//                EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
//                You should have received a copy of the GNU Lesser General
//                Public License along with this library; if not, write to the
//                Free Software Foundation, Inc., 51 Franklin Street, Fifth
//                Floor, Boston, MA 02110-1301 USA.
//
// AUTHOR:        Motic

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
    static const int MAX_BIT_DEPTH = 12;
    
    int binning_;
    float gain_;
    float *gains_;
    int bytesPerPixel_;
    int colorChannel_;
    bool initialized_;
    int width_, height_;
    double exposureMs_;
    float exposureMin_;
    float exposureMax_;
    ImgBuffer img_;
    int roiX_, roiY_;
    int cameraCnt_, currentCam_;
    MUCam_Handle hCameras_[100];
    vector<std::string> DevicesVec_;
    vector<long>BinningsVec_;
    int bitDepth_;
    int bitCnt_;
    unsigned char *tmpbuf_;
    void *handle_;
    int bufferSize_;
    
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
    MUCam_getGainCountUPP MUCam_getGainCount;
    MUCam_getGainListUPP MUCam_getGainList;
    MUCam_setRGBGainIndexUPP MUCam_setRGBGainIndex;
    MUCam_setRGBGainValueUPP MUCam_setRGBGainValue;
    MUCam_releaseCameraUPP MUCam_releaseCamera;
    
    
    int ResizeImageBuffer();
    void ReAllocalBuffer(int size);
    void GenerateImage();
    int FindMoticCameras();
    void GetMotiCamNAME(MUCam_Handle hCamera, char* sName);
    int InitDevice();
    void InitBinning();
    void InitPixelType();
    void InitGain();
    void InitExposure();
    bool OpenCamera(MUCam_Handle hCamera);
};

#endif /* defined(__MUCamSource__MUCamSource__) */
