///////////////////////////////////////////////////////////////////////////////
// FILE:          CameraAdapter.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   The example implementation of the demo camera.
//                Simulates generic digital camera and associated automated
//                microscope devices and enables testing of the rest of the
//                system without the need to connect to the actual hardware. 
//                
// AUTHOR:        Terry L. Sprout, Terry.Sprout@Agile-Automation.com
//
// COPYRIGHT:     (c) 2011, AgileAutomation, Inc, All rights reserved
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
//

#pragma once


//
//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_MULTIPLE_LIBRARY     1001
#define ERR_MULTIPLE_CAMERA      1002
#define ERR_BUSY_ACQUIRING       1003
#define ERR_INVALID_GRABBER      1004
#define ERR_INVALID_CAMERA       1005
#define ERR_INVALID_CAMERA_ID    1006
#define ERR_INVALID_CAMERA_MODE  1007


//////////////////////////////////////////////////////////////////////////////
// CCameraAdapter class
// Simulation of the Camera device
//////////////////////////////////////////////////////////////////////////////
class CCameraAdapter : public CCameraBase<CCameraAdapter>  
{
public:
   CCameraAdapter( LPCTSTR pszName );
   ~CCameraAdapter();
  
   void Capture();
   void OnGrabberChanged( LPCTSTR pszGrabber );
   void OnCameraChanged( LPCTSTR pszCamera, int nId );
   void OnCameraIdChanged( LPCTSTR pszSn );
   void OnCameraModeChanged( LPCTSTR pszMode );
   void OnCameraPropertiesChanged();
   void OnIonFeedbackSizeChanged( int nPixels );
   void OnIonFeedbackEnabled( BOOL bEnabled );
   void OnHistogramTransferChanged( double fBrightness, double fContrast, double fGamma );
   void OnHistogramAutoContrast( BOOL bOn );
   void OnDiscriminatorMaxValue( double fMax );
   void OnDiscriminatorNewValues( double fAverage, double fStdDev, double fMeasured, double fSigma, BOOL bDiscarded );
   void OnDiscriminatorDiscardCount( int nNumDiscarded, int nNumTotal );
   void OnDiscriminatorEnabled( BOOL bEnabled );
   void OnIntegratorDepthChanged( INT32 unSize );
   void OnIntegratorCurrentDepth( INT32 unDepth );
   void OnIntegratorRateChanged( double fRate );
   void OnIntegratorEnabled( BOOL bEnabled );
   void OnAveragingDepthChanged( INT32 unSize );
   void OnAveragingCurrentDepth( INT32 unDepth );
   void OnAveragingRateChanged( double fRate );
   void OnSmoothAveragingEnabled( BOOL bEnabled );
   void OnFastAveragingEnabled( BOOL bEnabled );

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
   int StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow);
   int StopSequenceAcquisition();
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
   double GetNominalPixelSizeUm() const {return nominalPixelSizeUm_;}
   double GetPixelSizeUm() const {return nominalPixelSizeUm_ * GetBinning();}
   int GetBinning() const;
   int SetBinning(int binSize);
   int IsExposureSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}
   bool IsCapturing();

   // action interface
   // ----------------
   int OnShowControlPanel(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnShowLogSelections(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnShowLogFile(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnClearLogFile(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnEnableLogGroup(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnEnableLogConstruct(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnEnableLogDestruct(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnEnableLogSetProps(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnEnableLogGetProps(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnEnableLogMethodCalls(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnEnableLogMethodFrame(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnEnableLogMethodCallTrace(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnEnableLogCallbacks(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnEnableLogCallbackFrame(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnEnableLogCallbackTrace(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnGrabber(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnCamera(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnCameraId(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnMode(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnVideoGain(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnVideoOffset(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnIntensifierVolts(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnIntensifierGain(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnFrameLeft(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnFrameTop(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnFrameWidth(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnFrameHeight(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnIntegration(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSourceBrightness(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSourceContrast(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSourceGamma(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSourceThreshold(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSourceAutoContrast(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSourceNormalContrast(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnIonFeedbackFilterSize(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnEnableIonFeedback(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnRamStackDepth(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnRamStackExposure(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnEnableRamStack(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnRamAverageDepth(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnRamAverageExposure(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnEnableSmoothRamAverage(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnEnableFastRamAverage(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnReadoutTime(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   static const double nominalPixelSizeUm_;

   CString m_sMyName;
   BOOL m_bIsConnected;
   bool m_bIsInitialized;
   bool m_bIsBusy;
   double readoutUs_;
   MM::MMTime readoutStartTime_;
   CSyncObject *m_pCS;
   HANDLE m_hCaptureThread;
   HANDLE m_hCaptureEnded;
   BOOL m_bChangingGrabber;
   BOOL m_bChangingCamera;
   BOOL m_bChangingCameraSn;
   BOOL m_bChangingCameraMode;
   BOOL m_bStream;
   long m_nStreamImages;
   long m_nStreamCount;
   double m_fStreamAvgTime;
   UINT m_unTimeout;
   BOOL m_bShowControlPanel;
   BOOL m_bShowLog;
   BOOL m_bStopOnOverflow;

   // operator settings
   double m_fSourceBrightness;
   double m_fSourceContrast;
   double m_fSourceGamma;
   INT16 m_nSourceThreshold;
   BOOL m_bSourceAutoContrast;
   INT16 m_nIonFeedbackFilterSize;
   BOOL m_bIonFeedbackEnabled;
   INT32 m_nIntegratorDepth;
   INT32 m_nIntegratorCurrentDepth;
   double m_fIntegratorRate;
   double m_fIntegratorExposure;
   BOOL m_bIntegratorEnabled;
   INT32 m_nAveragingDepth;
   INT32 m_nAveragingCurrentDepth;
   double m_fAveragingRate;
   BOOL m_bSmoothAvgEnabled;
   BOOL m_bFastAvgEnabled;
   double m_fDiscriminatorMax;
   double m_fDiscriminatorAverage;
   double m_fDiscriminatorStdDev;
   double m_fDiscriminatorMeasured;
   double m_fDiscriminatorSigma;
   BOOL m_bDiscriminatorDiscarded;
   int m_nDiscriminatorNumDiscarded;
   int m_nDiscriminatorNumTotal;
   BOOL m_bDiscriminatorEnabled;

   CString m_sVersion;
   vector<string> m_asGrabbers;
   vector<string> m_asCameras;
   vector<string> m_asCameraSns;
   vector<string> m_asCameraModes;
   vector<string> m_asBooleans;
   vector<string> m_asPixelTypes;
   INT16 m_nCurGrabber;
   INT16 m_nCurCamera;
   INT16 m_nCurCameraSn;
   INT16 m_nCurCameraMode;

   BOOL m_bIsWorking;
   BOOL m_bModeChanged;
   BOOL m_bPropsChanged;
   BOOL m_bClearROI;
   INT16 m_nNewCameraMode;
   string m_sNewMode;

   double m_fCapFrameRate;
   INT16 m_nCapBits;
   INT16 m_nCapLeft;
   INT16 m_nCapWidth;
   INT16 m_nCapTop;
   INT16 m_nCapHeight;
   INT16 m_nCapXBin;
   INT16 m_nCapYBin;
   INT16 m_nVideoGain;
   INT16 m_nVideoOffset;
   double m_fIntensifierVolts;
   double m_fIntensifierGain;
   CString m_sPixel;

   INT16 m_nImageLeft;
   INT16 m_nImageWidth;
   INT16 m_nImageTop;
   INT16 m_nImageHeight;
   INT16 m_nImageStride;
   INT16 m_nImagePixelBytes;
   UINT m_nImageImageBytes;
   UINT m_unImageSaturation;
   double m_fImageFrameRate;

   INT16 m_nStreamLeft;
   INT16 m_nStreamWidth;
   INT16 m_nStreamTop;
   INT16 m_nStreamHeight;
   INT16 m_nStreamStride;
   INT16 m_nStreamPixelBytes;
   UINT m_nStreamImageBytes;
   UINT m_unStreamSaturation;
   double m_fStreamFrameRate;

   INT16 m_nRoiLeft;
   INT16 m_nRoiWidth;
   INT16 m_nRoiTop;
   INT16 m_nRoiHeight;
   INT16 m_nRoiStride;
   UINT m_nRoiImageBytes;
   PUCHAR m_punRoi;
   INT16 m_nFrameClocks;
   double m_fExposure;
   INT16 m_nIntervalCount;
   //int m_nIntegratorDepth;

   INT16 m_nMMFrameClocks;
   BOOL m_bUpdateInProgress;
   BOOL m_bExposureInSync;

   INT16 ConnectToPiper();
   INT16 ConnectPipe( INT16 nEnum );
   INT16 SelectCamera( INT16 nEnum );
   INT16 GetCameraChoices();
   INT16 SelectCameraSn( INT16 nEnum );
   INT16 SelectCameraMode( INT16 nEnum );
   INT16 SetIntegration( INT16 nFrames );
   INT16 GetProperties();

   int ResizeImageBuffer();
   void UpdateGUI();
};
