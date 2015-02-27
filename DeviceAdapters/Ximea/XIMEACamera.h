///////////////////////////////////////////////////////////////////////////////
// FILE:          XIMEACamera.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   XIMEA camera module.
//                
// AUTHOR:        Marian Zajko, <marian.zajko@ximea.com>
// COPYRIGHT:     Marian Zajko and XIMEA GmbH, Münster, 2011
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

#include "DeviceBase.h"
#include "ImgBuffer.h"
#include "DeviceThreads.h"
#include "ImgBuffer.h"

#include "xiApi.h"
#include "m3Api.h"
#include "m3Ext.h"

//////////////////////////////////////////////////////////////////////////////

class MySequenceThread;

class XIMEACamera : public CCameraBase<XIMEACamera>  
{
public:
   XIMEACamera(const char* name);
   ~XIMEACamera();
  
   //////////////////////////////////////////////////////////////
   // MMDevice API
   int Initialize();
   int Shutdown();
   void GetName(char* name) const;      
   
   //////////////////////////////////////////////////////////////
   // XIMEACamera API
   int SnapImage();
   const unsigned char* GetImageBuffer();
   unsigned GetNumberOfComponents()  const { return nComponents_;};
   //////////////////////////////////////////////////////////////
   unsigned GetImageWidth() const;
   unsigned GetImageHeight() const;
   //////////////////////////////////////////////////////////////
   unsigned GetImageBytesPerPixel() const;
   unsigned GetBitDepth() const;
   long     GetImageBufferSize() const;
   //////////////////////////////////////////////////////////////
   double   GetExposure() const;
   void     SetExposure(double exp);
   //////////////////////////////////////////////////////////////
   int      SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize); 
   int      GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize); 
   int      ClearROI();
   //////////////////////////////////////////////////////////////
   int      PrepareSequenceAcqusition(){ return DEVICE_OK; };
   int      StartSequenceAcquisition(double interval);
   int      StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow);
   int      StopSequenceAcquisition();
   bool     IsCapturing();
   int      InsertImage();
   int      ThreadRun();
   void     OnThreadExiting() throw(); 
   int      GetBinning() const;
   int      SetBinning(int binSize);
   int      IsExposureSequenceable(bool& seq) const {seq = false; return DEVICE_OK;}

   //////////////////////////////////////////////////////////////
   // action interface
   int OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnDataFormat(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSensorTaps(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnGain(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnAcqTout(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTrigger(MM::PropertyBase* pProp, MM::ActionType eAct);
   
   int OnGpi1(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnGpi2(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnGpi3(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnGpi4(MM::PropertyBase* pProp, MM::ActionType eAct);
   
   int OnGpo1(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnGpo2(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnGpo3(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnGpo4(MM::PropertyBase* pProp, MM::ActionType eAct);

   int OnWbRed(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnWbGreen(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnWbBlue(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnAWB(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnGammaY(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnGammaC(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSharpness(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnCcMatrix(MM::PropertyBase* pProp, MM::ActionType eAct);
	
   int OnAEAG(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnExpPrio(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnExpLim(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnGainLim(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnLevel(MM::PropertyBase* pProp, MM::ActionType eAct);

   int OnBpc(MM::PropertyBase* pProp, MM::ActionType eAct);
   
   int OnCooling(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTargetTemp(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnChipTemp(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnHousTemp(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
	const std::string name_;
	void* handle;
	XI_IMG image;
	int binning_;
	int tapcnt_;
	double acqTout_;
	int bytesPerPixel_;
	double gain_;
	int adc_;
	double exposureMs_;
	int nComponents_;   
	bool initialized_;
	bool isTrg_;
	ImgBuffer* img_;
	int roiX_, roiY_;
	MM::MMTime readoutStartTime_;
	MM::MMTime sequenceStartTime_;
	long imageCounter_;
	MMThreadLock imgPixelsLock_;
	int ResizeImageBuffer();
	friend class MySequenceThread;
	MySequenceThread * thd_;
	bool stopOnOverflow_;
	bool isAcqRunning;
};

//////////////////////////////////////////////////////////////////////////////

class MySequenceThread : public MMDeviceThreadBase
{
   friend class CDemoCamera;
   enum { default_numImages=1, default_intervalMS = 100 };
   public:
      MySequenceThread(XIMEACamera* pCam);
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
      XIMEACamera* camera_;                                                     
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

//////////////////////////////////////////////////////////////////////////////
