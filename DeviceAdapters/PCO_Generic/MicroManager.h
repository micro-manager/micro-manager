///////////////////////////////////////////////////////////////////////////////
// FILE:          Micromanager.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   pco.camera generic camera module
//                
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 06/08/2005
//                Franz Reitner, pco ag, 13.12.2013
// COPYRIGHT:     University of California, San Francisco, 2006
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
//                License text is included with the source distribution.
//
#ifndef _PCO_GENERIC_H_
#define _PCO_GENERIC_H_

#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/ImgBuffer.h"
#include "../../MMDevice/DeviceThreads.h"
#include "../../MMDevice/DeviceUtils.h"
#include "Camera.h"
#include <string>
#include <map>

#if !defined KAMLIBVERSION
#pragma message ("*****************************************************************************************")
#pragma message ("* Please upgrade Kamlib library to current version!")
#pragma message ("* Copy the content of the pco_generic.zip file to the correct library/include folder.")
#error Missing current pco library (in camera.h). Please copy pco lib into correct library/include folder.
#endif

#define KAMLIBVERSION_MM 240  // Will be incremented by pco when a new Kamlib is present (do not change)
#if KAMLIBVERSION != KAMLIBVERSION_MM
#define STRING2(x) #x
#define STRING(x) STRING2(x)
#pragma message ("*****************************************************************************************")
#pragma message ("* Please upgrade Kamlib library to current version!")
#pragma message ("* Copy the content of the pco_generic.zip file to the correct library/include folder.")
#pragma message ("* Current kamblib version:" STRING(KAMLIBVERSION))
#pragma message ("*    This kamblib version:" STRING(KAMLIBVERSION_MM))
#pragma message ("*****************************************************************************************")
#error Wrong Kamlib version
#endif

#define MM_PCO_GENERIC_MAX_STRLEN      400
#define ERR_UNKNOWN_CAMERA_TYPE         11
#define ERR_TIMEOUT                     12


//////////////////////////////////////////////////////////////////////////////
// Implementation of the MMDevice and MMCamera interfaces
//
class CPCOCam : public CCameraBase<CPCOCam>
{
public:
  CPCOCam();
  ~CPCOCam();

  // MMDevice API
  int Initialize();
  int Shutdown();

  void GetName(char* pszName) const;
  bool Busy() {return m_bBusy;}
  void WriteLog(char *message, int err);

  // MMCamera API
  int SnapImage();
  const unsigned char* GetImageBuffer();
  const unsigned char* GetBuffer(int ibufnum);
  const unsigned int*  GetImageBufferAsRGB32();
  unsigned GetImageWidth() const {return img_.Width();}
  unsigned GetImageHeight() const {return img_.Height();}
  unsigned GetImageBytesPerPixel() const;
  unsigned GetBitDepth() const;
  unsigned int GetNumberOfComponents() const;
  int GetBinning() const;
  int SetBinning(int binSize);
  int IsExposureSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}

  long GetImageBufferSize() const {return img_.Width() * img_.Height() * GetImageBytesPerPixel();}
  double GetExposure() const {return m_dExposure;}
  void SetExposure(double dExp);
  int SetROI(unsigned uX, unsigned uY, unsigned uXSize, unsigned uYSize); 
  int GetROI(unsigned& uX, unsigned& uY, unsigned& uXSize, unsigned& uYSize); 
  int ClearROI();
  int PrepareSequenceAcqusition();
  int StartSequenceAcquisition(long numImages, double /*interval_ms*/, bool stopOnOverflow);
  int StopSequenceAcquisition();
  int StoppedByThread();
  bool IsCapturing();
  void SetSizes(int iw, int ih, int ib);

  // action interface
  //int OnBoard(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnCameraType(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnCCDType(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnAcquireMode(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnTriggerMode(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnFpsMode(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnNoiseFilterMode(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnFps(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnPixelRate(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnGain(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnEMGain(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnEMLeftROI(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnDemoMode(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnTimestampMode( MM::PropertyBase* pProp, MM::ActionType eAct );
  int OnDoubleShutterMode( MM::PropertyBase* pProp, MM::ActionType eAct );
  int OnIRMode( MM::PropertyBase* pProp, MM::ActionType eAct );
  /*
  int OnMode(CPropertyBase* pProp, ActionType eAct);
  int OnSubMode(CPropertyBase* pProp, ActionType eAct);
  int OnRoiXMax(CPropertyBase* pProp, ActionType eAct);
  int OnRoiYMax(CPropertyBase* pProp, ActionType eAct);
  int OnRoiYMin(CPropertyBase* pProp, ActionType eAct);
  int OnHBin(CPropertyBase* pProp, ActionType eAct);
  int OnVBin(CPropertyBase* pProp, ActionType eAct);
  int OnCCDType(CPropertyBase* pProp, ActionType eAct);   
  */

private:
  int ResizeImageBuffer();
  int SetupCamera();
  int CleanupSequenceAcquisition();
  int SetNCheckROI(int *Roix0, int *Roix1, int *Roiy0, int *Roiy1);


  class SequenceThread : public MMDeviceThreadBase
  {
  public:
    SequenceThread(CPCOCam* pCam) : stop_(false), numImages_(0) {camera_ = pCam;}
    ~SequenceThread() {}

    int svc (void);

    void Stop() {stop_ = true;}

    void Start(int width, int height, int ibyteperpixel)
    {
      m_svcWidth = width;
      m_svcHeight = height;
      m_svcBytePP = ibyteperpixel;
      stop_ = false;
      activate();
    }

    void SetLength(long images) {numImages_ = images;}

  private:
    CPCOCam* camera_;
    bool stop_;
    long numImages_;
    int m_svcWidth, m_svcHeight, m_svcBytePP;
  };

  SequenceThread* sthd_;
  bool m_bStopOnOverflow;

  int InsertImage();

  ImgBuffer img_;
  int pixelDepth_;
  float pictime_;
  bool m_bSequenceRunning;

  CCamera *m_pCamera;
  int m_bufnr;
  WORD *m_pic;
  bool m_bDemoMode;
  bool m_bStartStopMode;

  double m_dExposure; 
  double m_dFps; 
  int    m_iFpsMode;
  int    m_iNoiseFilterMode;
  int    m_iPixelRate;
  int    m_iDoubleShutterMode;
  int    m_iIRMode;
  bool m_bBusy;
  bool m_bInitialized;
  bool m_bDoAutoBalance;

  // pco generic data
  int m_iCameraNum;
  int m_iInterFace;
  int m_iCameraNumAtInterface;
  int m_nCameraType;
  char m_pszTimes[MM_PCO_GENERIC_MAX_STRLEN];
  int m_nTimesLen;
  int m_nSubMode;
  int m_nMode;
  int m_nTrig;
  int m_iXRes, m_iYRes;
  int m_iWidth, m_iHeight, m_iBytesPerPixel;
  int m_nRoiXMax;
  int m_nRoiXMin;
  int m_nRoiYMax;
  int m_nRoiYMin;
  int m_nHBin;
  int m_nVBin;
  int m_nCCDType;
  int roiXMaxFull_;
  int roiYMaxFull_;
  int m_iGain;
  int m_iEMGain;
  int m_iGainCam;
  int m_iOffset;
  int m_iTimestamp;
  unsigned int m_uiFlags;
  bool m_bSettingsChanged;
  int m_iNextBufferToUse[4];
  int m_iLastBufferUsed[4];
  int m_iNextBuffer;
  CRITICAL_SECTION m_cCrisec;
  int m_iNumImages;
  int m_iNumImagesInserted;
  double dIntervall;
  int m_iAcquireMode;
};

#endif //_PCO_GENERIC_H_
