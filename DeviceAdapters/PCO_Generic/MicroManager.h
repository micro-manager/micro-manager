///////////////////////////////////////////////////////////////////////////////
// FILE:          Sensicam.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Sensicam camera module
//                
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 06/08/2005
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
// CVS:           $Id: Sensicam.h 1966 2009-01-14 21:14:57Z OD $
//
#ifndef _SENSICAM_H_
#define _SENSICAM_H_

#include "..\..\MMDevice/DeviceBase.h"
#include "..\..\MMDevice/ImgBuffer.h"
#include "..\..\MMDevice/DeviceUtils.h"
#include "..\..\..\3rdparty\Pco\Windows\pco_generic\Camera.h"
#include <string>
#include <map>

#if !defined KAMLIBVERSION
#error Missing current pco 3rdparty library v224 (in camera.h). Please copy pco lib into 3rdparty folder. See pco_generic.zip in DeviceAdapters/pco_generic.
#endif
#if KAMLIBVERSION < 224
#error Old pco library found (< v224 in camera.h). Please update your pco lib in 3rdparty folder. See pco_generic.zip in DeviceAdapters/pco_generic.
#endif

#define MMSENSICAM_MAX_STRLEN    400
#define ERR_UNKNOWN_CAMERA_TYPE  11
#define ERR_TIMEOUT              12

class CPCOMicroManagerApp : public CWinApp
{
public:
	CPCOMicroManagerApp();

// Overrides
	// ClassWizard generated virtual function overrides
	//{{AFX_VIRTUAL(CPCOMicroManagerApp)
	public:
	virtual int ExitInstance();
	virtual BOOL InitInstance();
	//}}AFX_VIRTUAL

	//{{AFX_MSG(CPCOMicroManagerApp)
		// NOTE - the ClassWizard will add and remove member functions here.
		//    DO NOT EDIT what you see in these blocks of generated code !
	//}}AFX_MSG
	DECLARE_MESSAGE_MAP()
};

//////////////////////////////////////////////////////////////////////////////
// Implementation of the MMDevice and MMCamera interfaces
//
class CPCOCam : public CCameraBase<CPCOCam>
{
public:
   static CPCOCam* GetInstance()
   {
      if (!m_pInstance)
         m_pInstance = new CPCOCam();
      return m_pInstance;
   }
   ~CPCOCam();
   
   // MMDevice API
   int Initialize();
   int Shutdown();
   
   void GetName(char* pszName) const {CDeviceUtils::CopyLimitedString(pszName, "Sensicam");}
   bool Busy() {return m_bBusy;}
   void WriteLog(char *message, int err);
   
   // MMCamera API
   int SnapImage();
   const unsigned char* GetImageBuffer();
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

   // action interface
   //int OnBoard(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnCameraType(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnCCDType(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnFpsMode(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnFps(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPixelRate(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnGain(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnEMGain(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnEMLeftROI(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnDemoMode(MM::PropertyBase* pProp, MM::ActionType eAct);
 
   /*
   int OnMode(CPropertyBase* pProp, ActionType eAct);
   int OnSubMode(CPropertyBase* pProp, ActionType eAct);
   int OnTrigger(CPropertyBase* pProp, ActionType eAct);
   int OnRoiXMax(CPropertyBase* pProp, ActionType eAct);
   int OnRoiYMax(CPropertyBase* pProp, ActionType eAct);
   int OnRoiYMin(CPropertyBase* pProp, ActionType eAct);
   int OnHBin(CPropertyBase* pProp, ActionType eAct);
   int OnVBin(CPropertyBase* pProp, ActionType eAct);
   int OnCCDType(CPropertyBase* pProp, ActionType eAct);   
   */

private:
   CPCOCam();
   int ResizeImageBuffer();
   int SetupCamera();

   class SequenceThread : public MMDeviceThreadBase
   {
      public:
         SequenceThread(CPCOCam* pCam) : stop_(false), numImages_(0) {camera_ = pCam;}
         ~SequenceThread() {}

         int svc (void);

         void Stop() {stop_ = true;}

         void Start()
         {
            stop_ = false;
            activate();
         }

         void SetLength(long images) {numImages_ = images;}

      private:
         CPCOCam* camera_;
         bool stop_;
         long numImages_;
   };

   SequenceThread* sthd_;
   bool stopOnOverflow_;

   int InsertImage();


   static CPCOCam* m_pInstance;
   ImgBuffer img_;
   int pixelDepth_;
   float pictime_;
   bool sequenceRunning_;

   CCamera *m_pCamera;
   int m_bufnr;
   WORD *m_pic;
   bool m_bDemoMode;
   bool m_bStartStopMode;
   int  m_iSkipImages;

   double m_dExposure; 
   double m_dFps; 
   int    m_iFpsMode;
   int    m_iPixelRate;
   bool m_bBusy;
   bool m_bInitialized;

   // sensicam data
   int m_nBoard;
   int m_nCameraType;
   char m_pszTimes[MMSENSICAM_MAX_STRLEN];
   int m_nTimesLen;
   int m_nSubMode;
   int m_nMode;
   int m_nTrig;
   int m_iXRes, m_iYRes;
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
   int m_iOffset;
   unsigned int m_uiFlags;
};

#endif //_SENSICAM_H_
