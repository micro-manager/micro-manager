///////////////////////////////////////////////////////////////////////////////
// FILE:          OpenCVgrabber.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   OpenCVgrabber utilises the easy image capture interface provided
//                by highgui in the OpenCV project, supporting almost any WDM or DirectShow image capture hardware
//                
// AUTHOR:        (Original file - democamera.h) Nenad Amodaj, nenad@amodaj.com, 06/08/2005
//                
//                (Original file - democamera.h) Karl Hoover (stuff such as programmable CCD size  & the various image processors)
//                (Original file - democamera.h) Arther Edelstein ( equipment error simulation)
//				  Ed Simmons OpenCVgrabber.h 2011
//
// COPYRIGHT:     Ed Simmons 2011
//				  ESImaging 2011
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


#ifndef _OPENCVGRABBER_H_
#define _OPENCVGRABBER_H_

#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/ImgBuffer.h"
#include "../../MMDevice/DeviceThreads.h"
#ifdef WIN32
#include "DeviceEnumerator.h"
#endif
#include <string>
#include <map>
#include <algorithm>

#ifdef _MSC_VER
#pragma warning(push)
#pragma warning(disable: 4267)
#endif
#include "opencv/highgui.h"
#ifdef _MSC_VER
#pragma warning(pop)
#endif

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_UNKNOWN_MODE         102
#define ERR_UNKNOWN_POSITION     103
#define ERR_IN_SEQUENCE          104
#define ERR_SEQUENCE_INACTIVE    105
#define SIMULATED_ERROR          200
#define FAILED_TO_GET_IMAGE      201
#define CAMERA_NOT_INITIALIZED   202


//////////////////////////////////////////////////////////////////////////////
// COpenCVgrabber class
//////////////////////////////////////////////////////////////////////////////

class MySequenceThread;

class COpenCVgrabber : public CCameraBase<COpenCVgrabber>  
{
public:
   COpenCVgrabber();
   ~COpenCVgrabber();
  
   // MMDevice API
   // ------------
   int Initialize();
   int Shutdown();
  
   void GetName(char* name) const;      
   
   // MMCamera API
   // ------------
   int SnapImage();
   const unsigned char* GetImageBuffer();
   //const unsigned char* GetImageBuffer(unsigned channel);
   //const unsigned int* GetImageBufferAsRGB32();
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
   int ThreadRun();
   bool IsCapturing();
   void OnThreadExiting() throw(); 
   double GetNominalPixelSizeUm() const {return nominalPixelSizeUm_;}
   double GetPixelSizeUm() const {return nominalPixelSizeUm_ * GetBinning();}
   int GetBinning() const;
   int SetBinning(int bS);
   int IsExposureSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}

   unsigned  GetNumberOfComponents() const { return nComponents_;};

   // action interface
   // ----------------

	int OnCameraID(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnGain(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnBitDepth(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnReadoutTime(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnScanMode(MM::PropertyBase* pProp, MM::ActionType eAct);

   int OnCameraCCDXSize(MM::PropertyBase* , MM::ActionType );
   int OnCameraCCDYSize(MM::PropertyBase* , MM::ActionType );
   int OnTriggerDevice(MM::PropertyBase* pProp, MM::ActionType eAct);
   MM::MMTime CurrentTime(void) { return GetCurrentMMTime(); };
   int OnResolution(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnFlipX(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnFlipY(MM::PropertyBase* pProp, MM::ActionType eAct);
private:
   int SetAllowedBinning();

   void GenerateEmptyImage(ImgBuffer& img);

   void RGB3toRGB4(const char* srcPixels, char* destPixels, int width, int height);

   int ResizeImageBuffer();

   static const double nominalPixelSizeUm_;


   // CvCapture* capture_;
   // IplImage* frame_; // do not modify, do not release!

   long int cameraID_;
   ImgBuffer img_;
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
   int nComponents_;
   bool xFlip_;
   bool yFlip_;
	std::string triggerDevice_;
   bool stopOnOverFlow_;

   MMThreadLock imgPixelsLock_;
   

   friend class MySequenceThread;
   MySequenceThread * thd_;
};

class MySequenceThread : public MMDeviceThreadBase
{
   friend class COpenCVgrabber;
   enum { default_numImages=1, default_intervalMS = 100 };
   public:
      MySequenceThread(COpenCVgrabber* pCam);
      ~MySequenceThread();
      void Stop()/* {MMThreadGuard(this->stopLock_); stop_ = true;} */;
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
      COpenCVgrabber* camera_;   
      MM::MMTime startTime_;                                                    
      MM::MMTime actualDuration_;                                               
      MM::MMTime lastFrameTime_;                                                

      MMThreadLock stopLock_;                                                   
      MMThreadLock suspendLock_;                                                
}; 




#endif //_DEMOCAMERA_H_
