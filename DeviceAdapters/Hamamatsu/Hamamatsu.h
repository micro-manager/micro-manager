///////////////////////////////////////////////////////////////////////////////
// FILE:          Hamamatsu.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Hamamatsu camera module based on DCAM API
// COPYRIGHT:     University of California, San Francisco, 2006, 2007, 2008
//                100X Imaging Inc, 2008
//
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
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 08/24/2005, contributions by Nico Stuurman
//
// CVS:           $Id: Hamamatsu.h 3048 2009-10-03 21:33:02Z nico $
//
#ifndef _HAMAMATSU_H_
#define _HAMAMATSU_H_

#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/ImgBuffer.h"
#include "../../MMDevice/DeviceUtils.h"
#include "../../MMDevice/DeviceThreads.h"

#ifdef WIN32
#include "../../../3rdparty/Hamamatsu/DCAMSDK/2007-12/inc/dcamapi.h"
#include "../../../3rdparty/Hamamatsu/DCAMSDK/2007-12/inc/dcamprop.h"
#include "../../../3rdparty/Hamamatsu/DCAMSDK/2007-12/inc/features.h"
#include <stdint.h>
#else
#include <Carbon/Carbon.h>
//#include <Carbon/MacTypes.h>
#include <dcamapi/dcamapi.h>
#include <dcamapi/dcamprop.h>
#include <dcamapi/features.h>
typedef void* LPVOID;
// This DCAM constant is not declared in the dcam header files on the Mac
//typedef enum DCAMERR
//{
//   DCAMERR_NOTSUPPORT       = 0x80000f03   /* function is not supported */
//};
#endif
#include <string>
#include <map>

// error codes
#define ERR_BUFFER_ALLOCATION_FAILED     1001
#define ERR_INCOMPLETE_SNAP_IMAGE_CYCLE  1002
#define ERR_BUSY_ACQUIRING               1003
#define ERR_INTERNAL_BUFFER_FULL         1004
#define ERR_NO_CAMERA_FOUND              1005

// forward declaration
class AcqSequenceThread;

//////////////////////////////////////////////////////////////////////////////
// Implementation of the MMDevice and MMCamera interfaces
//
class CHamamatsu : public CCameraBase<CHamamatsu>
{
public:

   friend class AcqSequenceThread;

   //static CHamamatsu* GetInstance();
   CHamamatsu();
   ~CHamamatsu();
   
   // MMDevice API
   int Initialize();
   int Shutdown();
   
   void GetName(char* pszName) const;
   bool Busy();
   
   // MMCamera API
   int SnapImage();
   const unsigned char* GetImageBuffer();
   unsigned GetImageWidth() const {return img_.Width();}
   unsigned GetImageHeight() const {return img_.Height();}
   unsigned GetImageBytesPerPixel() const {return img_.Depth();} 
   long GetImageBufferSize() const {return img_.Width() * img_.Height() * GetImageBytesPerPixel();}
   unsigned GetBitDepth() const;
   double GetExposure() const;
   void SetExposure(double dExp);
   int SetROI(unsigned uX, unsigned uY, unsigned uXSize, unsigned uYSize); 
   int GetROI(unsigned& uX, unsigned& uY, unsigned& uXSize, unsigned& uYSize);
   int ClearROI();
   int GetBinning() const {return lnBin_;};
   int SetBinning(int binSize); 

   // high-speed interface
   int StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow);
   int StopSequenceAcquisition();
   int RestartSequenceAcquisition();
   bool IsCapturing();
   int RestartSnapMode();

   // action interface
   int OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTrigPolarity(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTrigMode(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnOutTrigPolarity(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnExtended(MM::PropertyBase* pProp, MM::ActionType eAct, long featureId);
   int OnExtendedProperty(MM::PropertyBase* pProp, MM::ActionType eAct, long propertyId);
   int OnFrameBufferSize(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnGain(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnOffset(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnReadoutTime(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnActualIntervalMs(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSensitivity(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnScanMode(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnCCDMode(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPhotonImagingMode(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSlot(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnGamma(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTemperature(MM::PropertyBase* pProp, MM::ActionType eAct);

   // custom interface for the burst thread
   int PushImage();

private:
   int ResizeImageBuffer();
   int ResizeImageBuffer(long frameBufSize);
   int ShutdownImageBuffer();
   int SetTrigMode(std::string triggerMode);
   bool IsFeatureSupported(int featureId);
   bool IsScanModeSupported(int32_t& maxSpeed);
   bool IsPropertySupported(DCAM_PROPERTYATTR& propAttr, long propertyId);
   int AddExtendedProperty(std::string propName, long propertyId);
   long ReportError(std::string message);
   int SetAvailableTriggerModes(DWORD cap);
   int SetAllowedBinValues(DWORD cap);
   int SetAllowedTrigModeValues(DWORD cap);
   int SetAllowedGainValues(DCAM_PARAM_FEATURE_INQ featureInq);
   int SetAllowedPropValues(DCAM_PROPERTYATTR propAttr, std::string propName);
   DCAM_PARAM_FEATURE_INQ GetFeatureInquiry(int featureId);
   void SetTextInfo();

   typedef std::map<std::string, long> MapLongByString;
   typedef std::map<long, std::string> MapStringByLong;
   std::map<long, MapLongByString> dcamLongByString_;
   std::map<long, MapStringByLong> dcamStringByLong_;

 //  static CHamamatsu* m_pInstance;
   static unsigned refCount_;
   long frameBufferSize_;
   long currentBufferSize_;
   ImgBuffer img_;
   bool m_bInitialized;
   bool m_bBusy;
   bool acquiring_;
   HDCAM m_hDCAM;
   bool snapInProgress_;
   bool softwareTriggerEnabled_;
   long lnBin_;
   long slot_;
   long frameCount_;
   long lastImage_;

   static HINSTANCE m_hDCAMModule; // DCAM dll handle
   unsigned long imageCounter_;
   unsigned long sequenceLength_;
   bool init_seqStarted_;
   AcqSequenceThread* seqThread_; // burst mode thread
   std::string triggerMode_;
   std::string originalTrigMode_;
   double dExp_;
   bool stopOnOverflow_;
   double interval_ms_;
};

/*
 * Acquisition thread
 */
class AcqSequenceThread : public MMDeviceThreadBase
{
   public:
      AcqSequenceThread(CHamamatsu* camera) : 
         intervalMs_(100.0), numImages_(1), busy_(false), stop_(false), camera_(camera) {}
      ~AcqSequenceThread() {}
      int svc (void);

      void SetInterval(double intervalMs) {intervalMs_ = intervalMs;}
      void SetLength(long images) {numImages_ = images;}
      void Stop() {stop_ = true;}
      void Start() {stop_ = false; activate();}

   private:
      double intervalMs_;
      long numImages_;
      bool busy_;
      bool stop_;
      CHamamatsu* camera_;
};


#endif //_HAMAMATSU_H_
