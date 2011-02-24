///////////////////////////////////////////////////////////////////////////////
// FILE:          DemoCamera.h
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
// CVS:           $Id$
//

#ifndef _DEMOCAMERA_H_
#define _DEMOCAMERA_H_

#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/ImgBuffer.h"
#include "../../MMDevice/DeviceThreads.h"
#include <string>
#include <map>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_UNKNOWN_MODE         102
#define ERR_UNKNOWN_POSITION     103
#define ERR_IN_SEQUENCE          104
#define ERR_SEQUENCE_INACTIVE    105


//////////////////////////////////////////////////////////////////////////////
// CDemoCamera class
// Simulation of the Camera device
//////////////////////////////////////////////////////////////////////////////

class MySequenceThread;

class CDemoCamera : public CCameraBase<CDemoCamera>  
{
public:
   CDemoCamera();
   ~CDemoCamera();
  
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
   int OnBitDepth(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnReadoutTime(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnScanMode(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnErrorSimulation(MM::PropertyBase* , MM::ActionType );
   int OnCameraCCDXSize(MM::PropertyBase* , MM::ActionType );
   int OnCameraCCDYSize(MM::PropertyBase* , MM::ActionType );

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
   bool errorSimulation_;
	long binSize_;
	long cameraCCDXSize_;
	long cameraCCDYSize_;

	double testProperty_[10];
   MMThreadLock* pDemoResourceLock_;
   MMThreadLock imgPixelsLock_;
   int nComponents_;
   void TestResourceLocking(const bool);
   friend class MySequenceThread;
   MySequenceThread * thd_;
};

class MySequenceThread : public MMDeviceThreadBase
{
   friend class CDemoCamera;
   enum { default_numImages=1, default_intervalMS = 100 };
   public:
      MySequenceThread(CDemoCamera* pCam);
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
      CDemoCamera* camera_;                                                     
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
// CDemoFilterWheel class
// Simulation of the filter changer (state device)
//////////////////////////////////////////////////////////////////////////////

class CDemoFilterWheel : public CStateDeviceBase<CDemoFilterWheel>
{
public:
   CDemoFilterWheel();
   ~CDemoFilterWheel();
  
   // MMDevice API
   // ------------
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy();
   unsigned long GetNumberOfPositions()const {return numPos_;}

   // action interface
   // ----------------
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   long numPos_;
   bool busy_;
   bool initialized_;
   MM::MMTime changedTime_;
   long position_;
};

//////////////////////////////////////////////////////////////////////////////
// CDemoLightPath class
// Simulation of the microscope light path switch (state device)
//////////////////////////////////////////////////////////////////////////////
class CDemoLightPath : public CStateDeviceBase<CDemoLightPath>
{
public:
   CDemoLightPath();
   ~CDemoLightPath();
  
   // MMDevice API
   // ------------
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy() {return busy_;}
   unsigned long GetNumberOfPositions()const {return numPos_;}

   // action interface
   // ----------------
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   long numPos_;
   bool busy_;
   bool initialized_;
   long position_;
};

//////////////////////////////////////////////////////////////////////////////
// CDemoObjectiveTurret class
// Simulation of the objective changer (state device)
//////////////////////////////////////////////////////////////////////////////

class CDemoObjectiveTurret : public CStateDeviceBase<CDemoObjectiveTurret>
{
public:
   CDemoObjectiveTurret();
   ~CDemoObjectiveTurret();
  
   // MMDevice API
   // ------------
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy() {return busy_;}
   unsigned long GetNumberOfPositions()const {return numPos_;}


   // Sequence related functions
   int IsPropertySequenceable(const char* name, bool& isSequenceable);
   int StartPropertySequence(const char* propertyName);
   int StopPropertySequence(const char* propertyName);

   // action interface
   // ----------------
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTrigger(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   long numPos_;
   bool busy_;
   bool initialized_;
   bool sequenceRunning_;
   int sequenceMaxSize_;
   int sequenceIndex_;
   std::vector<std::string> sequence_;
   long position_;
};

//////////////////////////////////////////////////////////////////////////////
// CDemoStateDevice class
// Simulation of a state device in which the number of states can be specified (state device)
//////////////////////////////////////////////////////////////////////////////

class CDemoStateDevice : public CStateDeviceBase<CDemoStateDevice>
{
public:
   CDemoStateDevice();
   ~CDemoStateDevice();
  
   // MMDevice API
   // ------------
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy();
   unsigned long GetNumberOfPositions()const {return numPos_;}

   // action interface
   // ----------------
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnNumberOfStates(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   long numPos_;
   bool busy_;
   bool initialized_;
   MM::MMTime changedTime_;
   long position_;
};

//////////////////////////////////////////////////////////////////////////////
// CDemoStage class
// Simulation of the single axis stage
//////////////////////////////////////////////////////////////////////////////

class CDemoStage : public CStageBase<CDemoStage>
{
public:
   CDemoStage();
   ~CDemoStage();

   bool Busy() {return busy_;}
   void GetName(char* pszName) const;

   int Initialize();
   int Shutdown();
     
   // Stage API
   int SetPositionUm(double pos);
   int GetPositionUm(double& pos) {pos = pos_um_; LogMessage("Reporting position", true); return DEVICE_OK;}
   double GetStepSize() {return stepSize_um_;}
   int SetPositionSteps(long steps) 
   {
      pos_um_ = steps * stepSize_um_; 
      return  OnStagePositionChanged(pos_um_);
   }
   int GetPositionSteps(long& steps) {steps = (long)(pos_um_ / stepSize_um_); return DEVICE_OK;}
   int SetOrigin() {return DEVICE_OK;}
   int GetLimits(double& lower, double& upper)
   {
      lower = lowerLimit_;
      upper = upperLimit_;
      return DEVICE_OK;
   }
   int Move(double /*v*/) {return DEVICE_OK;}

   // action interface
   // ----------------
   int OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct);

   // Sequence functions
   int IsStageSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}
   int GetStageSequenceMaxLength(long& nrEvents) const  {nrEvents = 0; return DEVICE_OK;}
   int StartStageSequence() const {return DEVICE_OK;}
   int StopStageSequence() const {return DEVICE_OK;}
   int LoadStageSequence(std::vector<double> positions) const {return DEVICE_OK;}

private:
   void SetIntensityFactor(double pos);
   double stepSize_um_;
   double pos_um_;
   bool busy_;
   bool initialized_;
   double lowerLimit_;
   double upperLimit_;
};

//////////////////////////////////////////////////////////////////////////////
// CDemoStage class
// Simulation of the single axis stage
//////////////////////////////////////////////////////////////////////////////

class CDemoXYStage : public CXYStageBase<CDemoXYStage>
{
public:
   CDemoXYStage();
   ~CDemoXYStage();

   bool Busy() {return busy_;}
   void GetName(char* pszName) const;

   int Initialize();
   int Shutdown();
     
   // XYStage API
   /* Note that only the Set/Get PositionStep functions are implemented in the adapter
    * It is best not to override the Set/Get PositionUm functions in DeviceBase.h, since
    * those implement corrections based on whether or not X and Y directionality should be 
    * mirrored and based on a user defined origin
    */

   // This must be correct or the conversions between steps and Um will go wrong
   virtual double GetStepSize() {return stepSize_um_;}
   virtual int SetPositionSteps(long x, long y)
   {
      posX_um_ = x * stepSize_um_;
      posY_um_ = y * stepSize_um_;
      int ret = OnXYStagePositionChanged(posX_um_, posY_um_);
      if (ret != DEVICE_OK)
         return ret;

      return DEVICE_OK;
   }
   virtual int GetPositionSteps(long& x, long& y)
   {
      x = (long)(posX_um_ / stepSize_um_);
      y = (long)(posY_um_ / stepSize_um_);
      return DEVICE_OK;
   }
   int SetRelativePositionSteps(long x, long y)                                                           
   {                                                                                                      
      long xSteps, ySteps;                                                                                
      GetPositionSteps(xSteps, ySteps);                                                   

      return this->SetPositionSteps(xSteps+x, ySteps+y);                                                  
   } 
   virtual int Home() {return DEVICE_OK;}
   virtual int Stop() {return DEVICE_OK;}

   /* This sets the 0,0 position of the adapter to the current position.  
    * If possible, the stage controller itself should also be set to 0,0
    * Note that this differs form the function SetAdapterOrigin(), which 
    * sets the coordinate system used by the adapter
    * to values different from the system used by the stage controller
    */
   virtual int SetOrigin() {return DEVICE_OK;}
   virtual int GetLimits(double& lower, double& upper)
   {
      lower = lowerLimit_;
      upper = upperLimit_;
      return DEVICE_OK;
   }
   virtual int GetLimitsUm(double& xMin, double& xMax, double& yMin, double& yMax)
   {
      xMin = lowerLimit_; xMax = upperLimit_;
      yMin = lowerLimit_; yMax = upperLimit_;
      return DEVICE_OK;
   }

   virtual int GetStepLimits(long& /*xMin*/, long& /*xMax*/, long& /*yMin*/, long& /*yMax*/)
   {
      return DEVICE_UNSUPPORTED_COMMAND;
   }
   double GetStepSizeXUm() {return stepSize_um_;}
   double GetStepSizeYUm() {return stepSize_um_;}
   int Move(double /*vx*/, double /*vy*/) {return DEVICE_OK;}

   // action interface
   // ----------------
   int OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   double stepSize_um_;
   double posX_um_;
   double posY_um_;
   bool busy_;
   bool initialized_;
   double lowerLimit_;
   double upperLimit_;
};

//////////////////////////////////////////////////////////////////////////////
// DemoShutter class
// Simulation of shutter device
//////////////////////////////////////////////////////////////////////////////
class DemoShutter : public CShutterBase<DemoShutter>
{
public:
   DemoShutter() : state_(false), initialized_(false), changedTime_(0.0) {
      EnableDelay(); // signals that the dealy setting will be used
   }
   ~DemoShutter() {}

   int Initialize();
   int Shutdown() {initialized_ = false; return DEVICE_OK;}

   void GetName (char* pszName) const;
   bool Busy();

   // Shutter API
   int SetOpen (bool open = true) {state_ = open; changedTime_ = GetCurrentMMTime(); return DEVICE_OK;}
   int GetOpen(bool& open) {open = state_; return DEVICE_OK;}
   int Fire(double /*deltaT*/) {return DEVICE_UNSUPPORTED_COMMAND;}

   // action interface
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   bool state_;
   bool initialized_;
   MM::MMTime changedTime_;
};

//////////////////////////////////////////////////////////////////////////////
// DemoShutter class
// Simulation of shutter device
//////////////////////////////////////////////////////////////////////////////
class DemoDA : public CSignalIOBase<DemoDA>
{
public:
   DemoDA ();
   ~DemoDA ();

   int Shutdown() {return DEVICE_OK;}
   void GetName(char* name) const {strcpy(name,"Demo DA");}
   int SetGateOpen(bool open); 
   int GetGateOpen(bool& open);
   int SetSignal(double volts);
   int GetSignal(double& volts);
   int GetLimits(double& minVolts, double& maxVolts) {minVolts=0.0; maxVolts= 10.0; return DEVICE_OK;}
   bool Busy() {return false;}
   int Initialize() {return DEVICE_OK;}

   // Sequence functions
   int IsDASequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}
   int GetDASequenceMaxLength(long& nrEvents) const  {nrEvents = 0; return DEVICE_OK;}
   int StartDASequence() const {return DEVICE_OK;}
   int StopDASequence() const {return DEVICE_OK;}
   int LoadDASequence(std::vector<double> voltages) const {return DEVICE_OK;}


private:
   double volt_;
   double gatedVolts_;
   bool open_;
};


//////////////////////////////////////////////////////////////////////////////
// DemoMagnifier class
// Simulation of magnifier Device
//////////////////////////////////////////////////////////////////////////////
class DemoMagnifier : public CMagnifierBase<DemoMagnifier>
{
public:
   DemoMagnifier () : position (0) {}
   ~DemoMagnifier () {}

   int Shutdown() {return DEVICE_OK;}
   void GetName(char* name) const {strcpy(name,"Demo Optovar");}

   bool Busy() {return false;}
   int Initialize();

   double GetMagnification();

   // action interface
   // ----------------
   int OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   int position;
};

//////////////////////////////////////////////////////////////////////////////
// DemoTranspose class
// transpose an image
//////////////////////////////////////////////////////////////////////////////
class DemoTranspose : public CImageProcessorBase<DemoTranspose>
{
public:
   DemoTranspose () : inPlace_ (false), pTemp_(NULL), tempSize_(0), busy_(false) {}
   ~DemoTranspose () {if( NULL!= pTemp_) free(pTemp_); tempSize_=0;  }

   int Shutdown() {return DEVICE_OK;}
   void GetName(char* name) const {strcpy(name,"DemoTranspose");}

   int Initialize();

   bool Busy(void) { return busy_;};

    // really primative image transpose algorithm which will work fine for non-square images... 
   template <typename PixelType> int TransposeRectangleOutOfPlace( PixelType* pI, unsigned int width, unsigned int height)
   {
      int ret = DEVICE_OK;
      unsigned long tsize = width*height*sizeof(PixelType);
      if( this->tempSize_ != tsize)
      {
         if( NULL != this->pTemp_)
         {
            free(pTemp_);
            pTemp_ = NULL;
         }
         pTemp_ = (PixelType *)malloc(tsize);
      }
      if( NULL != pTemp_)
      {
         PixelType* pTmpImage = (PixelType *) pTemp_;
         tempSize_ = tsize;
         for( unsigned long ix = 0; ix < width; ++ix)
         {
            for( unsigned long iy = 0; iy < height; ++iy)
            {
               pTmpImage[iy + ix*width] = pI[ ix + iy*height];
            }
         }
         memcpy( pI, pTmpImage, tsize);
      }
      else
      {
         ret = DEVICE_ERR;
      }

      return ret;

   }

   
   template <typename PixelType> void TransposeSquareInPlace( PixelType* pI, unsigned int dim) 
   { 
      PixelType tmp;
      for( unsigned long ix = 0; ix < dim; ++ix)
      {
         for( unsigned long iy = ix; iy < dim; ++iy)
         {
            tmp = pI[iy*dim + ix];
            pI[iy*dim +ix] = pI[ix*dim + iy];
            pI[ix*dim +iy] = tmp; 
         }
      }

      return;
   }

   int Process(unsigned char* buffer, unsigned width, unsigned height, unsigned byteDepth);

   // action interface
   // ----------------
   int OnInPlaceAlgorithm(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   bool inPlace_;
   void* pTemp_;
   unsigned long tempSize_;
   bool busy_;
};



//////////////////////////////////////////////////////////////////////////////
// DemoAutoFocus class
// Simulation of the auto-focusing module
//////////////////////////////////////////////////////////////////////////////
class DemoAutoFocus : public CAutoFocusBase<DemoAutoFocus>
{
public:
   DemoAutoFocus() : 
      running_(false), 
      busy_(false), 
      initialized_(false)  
   {}

   ~DemoAutoFocus() {}
      
   // MMDevice API
   bool Busy() {return busy_;}
   void GetName(char* pszName) const;

   int Initialize();
   int Shutdown(){initialized_ = false; return DEVICE_OK;}

   // AutoFocus API
   virtual int SetContinuousFocusing(bool state) {running_ = state; return DEVICE_OK;}
   virtual int GetContinuousFocusing(bool& state) {state = running_; return DEVICE_OK;}
   virtual bool IsContinuousFocusLocked() {return running_;}
   virtual int FullFocus() {return DEVICE_OK;}
   virtual int IncrementalFocus() {return DEVICE_OK;}
   virtual int GetLastFocusScore(double& score) {score = 0.0; return DEVICE_OK;}
   virtual int GetCurrentFocusScore(double& score) {score = 1.0; return DEVICE_OK;}
   virtual int GetOffset(double& /*offset*/) {return DEVICE_OK;}
   virtual int SetOffset(double /*offset*/) {return DEVICE_OK;}

private:
   bool running_;
   bool busy_;
   bool initialized_;
};


#endif //_DEMOCAMERA_H_
