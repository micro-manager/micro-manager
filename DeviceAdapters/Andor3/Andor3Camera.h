///////////////////////////////////////////////////////////////////////////////
// FILE:          Andor3Camera.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   The example implementation of the demo camera.
//                Simulates generic digital camera and associated automated
//                microscope devices and enables testing of the rest of the
//                system without the need to connect to the actual hardware. 
//                
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 06/08/2005
//                
//                Karl Hoover (stuff such as programmable CCD size and transpose processor)
//
// COPYRIGHT:     University of California, San Francisco, 2006
//                100X Imaging Inc, 2008
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
// CVS:           $Id: Andor3Camera.h 6793 2011-03-28 19:10:30Z karlh $
//

#ifndef _ANDOR3CAMERA_H_
#define _ANDOR3CAMERA_H_

#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/ImgBuffer.h"
#include "../../MMDevice/DeviceThreads.h"
#include <string>
#include <map>
#include "atcore++.h"
#include "SnapShotControl.h"

using namespace andor;

#define NO_CIRCLE_BUFFER_FRAMES  10

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_UNKNOWN_MODE         102
#define ERR_UNKNOWN_POSITION     103
#define ERR_IN_SEQUENCE          104
#define ERR_SEQUENCE_INACTIVE    105


//////////////////////////////////////////////////////////////////////////////
// CAndor3Camera class
//////////////////////////////////////////////////////////////////////////////

class MySequenceThread;

class CAndor3Camera : public CCameraBase<CAndor3Camera>  
{
public:
   CAndor3Camera();
   ~CAndor3Camera();
  
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
   int PrepareSequenceAcqusition() {return DEVICE_OK;}
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

   unsigned  GetNumberOfComponents() const { return nComponents_;};

   // action interface
   // ----------------
   // floating point read-only properties for testing
   int OnTestProperty(MM::PropertyBase* pProp, MM::ActionType eAct, long);
   void RefreshTestProperty(long);


   int OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnReadoutTime(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnCameraCCDXSize(MM::PropertyBase* , MM::ActionType );
   int OnCameraCCDYSize(MM::PropertyBase* , MM::ActionType );
   int OnTriggerMode(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnExposureTime(MM::PropertyBase* pProp, MM::ActionType eAct);
   MM::MMTime CurrentTime(void) { return GetCurrentMMTime(); };

private:
   int SetAllowedBinning();

   void GenerateEmptyImage(ImgBuffer& img);

   void GenerateSyntheticImage(ImgBuffer& img, double exp);
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
   long imageCounter_;
   long binSize_;
   long cameraCCDXSize_;
   long cameraCCDYSize_;
   bool softwareTriggerMode_;

   std::string triggerModeString_;
   long frame_count_;
   int no_of_devices_;

   unsigned char** image_buffers_;

   MMThreadLock* pDemoResourceLock_;
   MMThreadLock imgPixelsLock_;
   int nComponents_;
   void TestResourceLocking(const bool);
   friend class MySequenceThread;
   MySequenceThread * thd_;

   SnapShotControl* snapShotController;
   IDeviceManager* deviceManager;
   IDevice* systemDevice;
   IInteger* deviceCount;
   IInteger* system_noDevices;
   IDevice* cameraDevice;
   IInteger* imageSizeBytes;
   IInteger* AOIHeight;
   IInteger* AOIWidth;
   IInteger* frameCount;
   IEnum* preAmpGainControl;
   IEnum* cycleMode;
   IFloat* exposureTime;
   IBufferControl* bufferControl;
   ICommand* startAcquisitionCommand;
   ICommand* stopAcquisitionCommand;
   IEnum* triggerMode;
   ICommand* sendSoftwareTrigger;
   IFloat* frameRate;
};

class MySequenceThread : public MMDeviceThreadBase
{
   friend class CAndor3Camera;
   enum { default_numImages=1, default_intervalMS = 100 };
   public:
      MySequenceThread(CAndor3Camera* pCam);
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
      CAndor3Camera* camera_;                                                     
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

#endif //_ANDOR3CAMERA_H_
