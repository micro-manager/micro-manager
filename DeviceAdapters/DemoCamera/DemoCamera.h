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
//                Karl Hoover (stuff such as programmable CCD size  & the various image processors)
//                Arther Edelstein ( equipment error simulation)
//
// COPYRIGHT:     University of California, San Francisco, 2006-2015
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

#ifndef _DEMOCAMERA_H_
#define _DEMOCAMERA_H_

#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/ImgBuffer.h"
#include "../../MMDevice/DeviceThreads.h"
#include <string>
#include <map>
#include <algorithm>
#include <stdint.h>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_UNKNOWN_MODE         102
#define ERR_UNKNOWN_POSITION     103
#define ERR_IN_SEQUENCE          104
#define ERR_SEQUENCE_INACTIVE    105
#define ERR_STAGE_MOVING         106
#define HUB_NOT_AVAILABLE        107

const char* NoHubError = "Parent Hub not defined.";

// Defines which segments in a seven-segment display are lit up for each of
// the numbers 0-9. Segments are:
//
//  0       1
// 1 2     2 4
//  3       8
// 4 5    16 32
//  6      64
const int SEVEN_SEGMENT_RULES[] = {1+2+4+16+32+64, 4+32, 1+4+8+16+64,
      1+4+8+32+64, 2+4+8+32, 1+2+8+32+64, 2+8+16+32+64, 1+4+32,
      1+2+4+8+16+32+64, 1+2+4+8+32+64};
// Indicates if the segment is horizontal or vertical.
const int SEVEN_SEGMENT_HORIZONTALITY[] = {1, 0, 0, 1, 0, 0, 1};
// X offset for this segment.
const int SEVEN_SEGMENT_X_OFFSET[] = {0, 0, 1, 0, 0, 1, 0};
// Y offset for this segment.
const int SEVEN_SEGMENT_Y_OFFSET[] = {0, 0, 0, 1, 1, 1, 2};

class ImgManipulator 
{
   public:
      virtual int ChangePixels(ImgBuffer& img) = 0;
};

////////////////////////
// DemoHub
//////////////////////

class DemoHub : public HubBase<DemoHub>
{
public:
   DemoHub() :
      initialized_(false),
      busy_(false)
   {}
   ~DemoHub() {}

   // Device API
   // ---------
   int Initialize();
   int Shutdown() {return DEVICE_OK;};
   void GetName(char* pName) const; 
   bool Busy() { return busy_;} ;

   // HUB api
   int DetectInstalledDevices();

private:
   void GetPeripheralInventory();

   std::vector<std::string> peripherals_;
   bool initialized_;
   bool busy_;
};


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
   bool SupportsMultiROI();
   bool IsMultiROISet();
   int GetMultiROICount(unsigned& count);
   int SetMultiROI(const unsigned* xs, const unsigned* ys,
           const unsigned* widths, const unsigned* heights,
           unsigned numROIs);
   int GetMultiROI(unsigned* xs, unsigned* ys, unsigned* widths,
           unsigned* heights, unsigned* length);
   int PrepareSequenceAcqusition() { return DEVICE_OK; }
   int StartSequenceAcquisition(double interval);
   int StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow);
   int StopSequenceAcquisition();
   int InsertImage();
   int RunSequenceOnThread(MM::MMTime startTime);
   bool IsCapturing();
   void OnThreadExiting() throw(); 
   double GetNominalPixelSizeUm() const {return nominalPixelSizeUm_;}
   double GetPixelSizeUm() const {return nominalPixelSizeUm_ * GetBinning();}
   int GetBinning() const;
   int SetBinning(int bS);

   int IsExposureSequenceable(bool& isSequenceable) const;
   int GetExposureSequenceMaxLength(long& nrEvents) const;
   int StartExposureSequence();
   int StopExposureSequence();
   int ClearExposureSequence();
   int AddToExposureSequence(double exposureTime_ms);
   int SendExposureSequence() const;

   unsigned  GetNumberOfComponents() const { return nComponents_;};

   // action interface
   // ----------------
   int OnMaxExposure(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTestProperty(MM::PropertyBase* pProp, MM::ActionType eAct, long);
   int OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnBitDepth(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnReadoutTime(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnScanMode(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnErrorSimulation(MM::PropertyBase* , MM::ActionType );
   int OnCameraCCDXSize(MM::PropertyBase* , MM::ActionType );
   int OnCameraCCDYSize(MM::PropertyBase* , MM::ActionType );
   int OnTriggerDevice(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnDropPixels(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnFastImage(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSaturatePixels(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnFractionOfPixelsToDropOrSaturate(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnShouldRotateImages(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnShouldDisplayImageNumber(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStripeWidth(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSupportsMultiROI(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnMultiROIFillValue(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnCCDTemp(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnIsSequenceable(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnMode(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPCF(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPhotonFlux(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnReadNoise(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnCrash(MM::PropertyBase* pProp, MM::ActionType eAct);

   // Special public DemoCamera methods
   void AddBackgroundAndNoise(ImgBuffer& img, double mean, double stdDev);
   void AddSignal(ImgBuffer& img, double photonFlux, double exp, double cf);
   // this function replace normal_distribution in C++11
   double GaussDistributedValue(double mean, double std);

   int RegisterImgManipulatorCallBack(ImgManipulator* imgManpl);


private:
   int SetAllowedBinning();
   void TestResourceLocking(const bool);
   void GenerateEmptyImage(ImgBuffer& img);
   void GenerateSyntheticImage(ImgBuffer& img, double exp);
   bool GenerateColorTestPattern(ImgBuffer& img);
   int ResizeImageBuffer();

   static const double nominalPixelSizeUm_;

   double exposureMaximum_;
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
   bool isSequenceable_;
   long sequenceMaxLength_;
   bool sequenceRunning_;
   unsigned long sequenceIndex_;
   double GetSequenceExposure();
   std::vector<double> exposureSequence_;
   long imageCounter_;
	long binSize_;
	long cameraCCDXSize_;
	long cameraCCDYSize_;
   double ccdT_;
	std::string triggerDevice_;

   bool stopOnOverflow_;

	bool dropPixels_;
   bool fastImage_;
	bool saturatePixels_;
	double fractionOfPixelsToDropOrSaturate_;
   bool shouldRotateImages_;
   bool shouldDisplayImageNumber_;
   double stripeWidth_;
   bool supportsMultiROI_;
   int multiROIFillValue_;
   std::vector<unsigned> multiROIXs_;
   std::vector<unsigned> multiROIYs_;
   std::vector<unsigned> multiROIWidths_;
   std::vector<unsigned> multiROIHeights_;

	double testProperty_[10];
   MMThreadLock imgPixelsLock_;
   friend class MySequenceThread;
   int nComponents_;
   MySequenceThread * thd_;
   int mode_;
   ImgManipulator* imgManpl_;
   double pcf_;
   double photonFlux_;
   double readNoise_;
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
      double intervalMs_;                                                       
      long numImages_;                                                          
      long imageCounter_;                                                       
      bool stop_;                                                               
      bool suspend_;                                                            
      CDemoCamera* camera_;                                                     
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
  
   void GetName(char* pszName) const ;
   bool Busy() {return busy_;}
   unsigned long GetNumberOfPositions()const {return numPos_;}

   // action interface
   // ----------------
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTrigger(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   long numPos_;
   bool busy_;
   bool initialized_;
   bool sequenceRunning_;
   unsigned long sequenceMaxSize_;
   unsigned long sequenceIndex_;
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
   int GetPositionSteps(long& steps)
   {
      steps = (long)(pos_um_ / stepSize_um_);
      return DEVICE_OK;
   }
   int SetOrigin() { return DEVICE_OK; }
   int GetLimits(double& lower, double& upper)
   {
      lower = lowerLimit_;
      upper = upperLimit_;
      return DEVICE_OK;
   }
   int Move(double /*v*/) {return DEVICE_OK;}

   bool IsContinuousFocusDrive() const {return false;}

   // action interface
   // ----------------
   int OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSequence(MM::PropertyBase* pProp, MM::ActionType eAct);

   // Sequence functions
   int IsStageSequenceable(bool& isSequenceable) const;
   int GetStageSequenceMaxLength(long& nrEvents) const;
   int StartStageSequence();
   int StopStageSequence();
   int ClearStageSequence();
   int AddToStageSequence(double /* position */);
   int SendStageSequence();

private:
   void SetIntensityFactor(double pos);
   double stepSize_um_;
   double pos_um_;
   bool busy_;
   bool initialized_;
   double lowerLimit_;
   double upperLimit_;
   bool sequenceable_;
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

   bool Busy();
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
   virtual int SetPositionSteps(long x, long y);
   virtual int GetPositionSteps(long& x, long& y);
   virtual int SetRelativePositionSteps(long x, long y);
   virtual int Home() { return DEVICE_OK; }
   virtual int Stop() { return DEVICE_OK; }

   /* This sets the 0,0 position of the adapter to the current position.  
    * If possible, the stage controller itself should also be set to 0,0
    * Note that this differs form the function SetAdapterOrigin(), which 
    * sets the coordinate system used by the adapter
    * to values different from the system used by the stage controller
    */
   virtual int SetOrigin() { return DEVICE_OK; }

   virtual int GetLimitsUm(double& xMin, double& xMax, double& yMin, double& yMax)
   {
      xMin = lowerLimit_; xMax = upperLimit_;
      yMin = lowerLimit_; yMax = upperLimit_;
      return DEVICE_OK;
   }

   virtual int GetStepLimits(long& /*xMin*/, long& /*xMax*/, long& /*yMin*/, long& /*yMax*/)
   { return DEVICE_UNSUPPORTED_COMMAND; }
   double GetStepSizeXUm() { return stepSize_um_; }
   double GetStepSizeYUm() { return stepSize_um_; }
   int Move(double /*vx*/, double /*vy*/) {return DEVICE_OK;}

   int IsXYStageSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}


   // action interface
   // ----------------
   int OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   double stepSize_um_;
   double posX_um_;
   double posY_um_;
   bool busy_;
   MM::TimeoutMs* timeOutTimer_;
   double velocity_;
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
   DemoShutter() : state_(false), initialized_(false), changedTime_(0.0)
   {
      EnableDelay(); // signals that the dealy setting will be used
      
      // parent ID display
      CreateHubIDProperty();
   }
   ~DemoShutter() {}

   int Initialize();
   int Shutdown() {initialized_ = false; return DEVICE_OK;}

   void GetName (char* pszName) const;
   bool Busy();

   // Shutter API
   int SetOpen (bool open = true)
   {
      state_ = open;
      changedTime_ = GetCurrentMMTime();
      return DEVICE_OK;
   }
   int GetOpen(bool& open)
   {
      open = state_;
      return DEVICE_OK;
   }
   int Fire(double /*deltaT*/)
   { return DEVICE_UNSUPPORTED_COMMAND; }

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
   DemoDA (uint8_t n);
   ~DemoDA ();

   int Shutdown() {return DEVICE_OK;}
   void GetName(char* name) const;
   int SetGateOpen(bool open); 
   int GetGateOpen(bool& open);
   int SetSignal(double volts);
   int GetSignal(double& volts);
   int GetLimits(double& minVolts, double& maxVolts)
   {
      minVolts=0.0; maxVolts= 10.0;
      return DEVICE_OK;
   }
   bool Busy() {return false;}
   int Initialize();

   // Sequence functions
   int IsDASequenceable(bool& isSequenceable) const
   {
      isSequenceable = true;
      return DEVICE_OK;
   }
   int GetDASequenceMaxLength(long& nrEvents) const 
   {
      nrEvents = 256;
      return DEVICE_OK;
   }
   int StartDASequence()
   {
      (const_cast<DemoDA *>(this))->SetSequenceStateOn();
      return DEVICE_OK;
   }
   int StopDASequence()
   {
      (const_cast<DemoDA *>(this))->SetSequenceStateOff();
      return DEVICE_OK;
   }
   int SendDASequence();
   int ClearDASequence();
   int AddToDASequence(double voltage);

   int OnTrigger(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnVoltage(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnRealVoltage(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   uint8_t n_;
   double volt_;
   double gatedVolts_;
   bool open_;
   bool sequenceRunning_;
   unsigned long sequenceIndex_;
   std::vector<double> sentSequence_;
   std::vector<double> nascentSequence_;

   void SetSequenceStateOn() { sequenceRunning_ = true; }
   void SetSequenceStateOff() { sequenceRunning_ = false; sequenceIndex_ = 0; }

   void SetSentSequence();
};


//////////////////////////////////////////////////////////////////////////////
// DemoMagnifier class
// Simulation of magnifier Device
//////////////////////////////////////////////////////////////////////////////
class DemoMagnifier : public CMagnifierBase<DemoMagnifier>
{
public:
   DemoMagnifier();

   ~DemoMagnifier () {};

   int Shutdown() { return DEVICE_OK; }

   void GetName(char* name) const;

   bool Busy() {return false;}
   int Initialize();

   double GetMagnification();

   // action interface
   // ----------------
   int OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnZoom(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnHighMag(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnVariable(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   std::string highMagString();

   int position_;
   double zoomPosition_;
   double highMag_;
   bool variable_;
};

//////////////////////////////////////////////////////////////////////////////
// TransposeProcessor class
// transpose an image
// K.H.
//////////////////////////////////////////////////////////////////////////////
class TransposeProcessor : public CImageProcessorBase<TransposeProcessor>
{
public:
   TransposeProcessor () : inPlace_ (false), pTemp_(NULL), tempSize_(0), busy_(false)
   {
      // parent ID display
      CreateHubIDProperty();
   }
   ~TransposeProcessor () {if( NULL!= pTemp_) free(pTemp_); tempSize_=0;  }

   int Shutdown() {return DEVICE_OK;}
   void GetName(char* name) const {strcpy(name,"TransposeProcessor");}

   int Initialize();

   bool Busy(void) { return busy_;};

    // really primative image transpose algorithm which will work fine for non-square images... 
   template <typename PixelType>
   int TransposeRectangleOutOfPlace(PixelType* pI, unsigned int width, unsigned int height)
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

   
   template <typename PixelType>
   void TransposeSquareInPlace(PixelType* pI, unsigned int dim)
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
// ImageFlipX class
// flip an image
// K.H.
//////////////////////////////////////////////////////////////////////////////
class ImageFlipX : public CImageProcessorBase<ImageFlipX>
{
public:
   ImageFlipX () :  busy_(false) {}
   ~ImageFlipX () {  }

   int Shutdown() {return DEVICE_OK;}
   void GetName(char* name) const {strcpy(name,"ImageFlipX");}

   int Initialize();
   bool Busy(void) { return busy_;};

   template <typename PixelType>
   int Flip(PixelType* pI, unsigned int width, unsigned int height)
   {
      PixelType tmp;
      int ret = DEVICE_OK;
      for( unsigned long iy = 0; iy < height; ++iy)
      {
         for( unsigned long ix = 0; ix <  (width>>1) ; ++ix)
         {
            tmp = pI[ ix + iy*width];
            pI[ ix + iy*width] = pI[ width - 1 - ix + iy*width];
            pI[ width -1 - ix + iy*width] = tmp;
         }
      }
      return ret;
   }

   int Process(unsigned char* buffer, unsigned width, unsigned height, unsigned byteDepth);

   int OnPerformanceTiming(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   bool busy_;
   MM::MMTime performanceTiming_;
};


//////////////////////////////////////////////////////////////////////////////
// ImageFlipY class
// flip an image
// K.H.
//////////////////////////////////////////////////////////////////////////////
class ImageFlipY : public CImageProcessorBase<ImageFlipY>
{
public:
   ImageFlipY () : busy_(false), performanceTiming_(0.) {}
   ~ImageFlipY () {  }

   int Shutdown() {return DEVICE_OK;}
   void GetName(char* name) const {strcpy(name,"ImageFlipY");}

   int Initialize();
   bool Busy(void) { return busy_;};

   template <typename PixelType>
   int Flip(PixelType* pI, unsigned int width, unsigned int height)
   {
      PixelType tmp;
      int ret = DEVICE_OK;
      for( unsigned long ix = 0; ix < width ; ++ix)
      {
         for( unsigned long iy = 0; iy < (height>>1); ++iy)
         {
            tmp = pI[ ix + iy*width];
            pI[ ix + iy*width] = pI[ ix + (height - 1 - iy)*width];
            pI[ ix + (height - 1 - iy)*width] = tmp;
         }
      }
      return ret;
   }


   int Process(unsigned char* buffer, unsigned width, unsigned height, unsigned byteDepth);

   // action interface
   // ----------------
   int OnPerformanceTiming(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   bool busy_;
   MM::MMTime performanceTiming_;

};



//////////////////////////////////////////////////////////////////////////////
// MedianFilter class
// apply Median filter an image
// K.H.
//////////////////////////////////////////////////////////////////////////////
class MedianFilter : public CImageProcessorBase<MedianFilter>
{
public:
   MedianFilter () : busy_(false), performanceTiming_(0.),pSmoothedIm_(0), sizeOfSmoothedIm_(0)
   {
      // parent ID display
      CreateHubIDProperty();
   };
   ~MedianFilter () { if(0!=pSmoothedIm_) free(pSmoothedIm_); };

   int Shutdown() {return DEVICE_OK;}
   void GetName(char* name) const {strcpy(name,"MedianFilter");}

   int Initialize();
   bool Busy(void) { return busy_;};

   // NOTE: this utility MODIFIES the argument, make a copy yourself if you want the original data preserved
   template <class U> U FindMedian(std::vector<U>& values ) {
      std::sort(values.begin(), values.end());
      return values[(values.size())>>1];
   };


   template <typename PixelType>
   int Filter(PixelType* pI, unsigned int width, unsigned int height)
   {
      int ret = DEVICE_OK;
      int x[9];
      int y[9];

      const unsigned long thisSize = sizeof(*pI)*width*height;
      if( thisSize != sizeOfSmoothedIm_)
      {
         if(NULL!=pSmoothedIm_)
         {
            sizeOfSmoothedIm_ = 0;
            free(pSmoothedIm_);
         }
         // malloc is faster than new...
         pSmoothedIm_ = (PixelType*)malloc(thisSize);
         if(NULL!=pSmoothedIm_)
         {
            sizeOfSmoothedIm_ = thisSize;
         }
      }

      PixelType* pSmooth = (PixelType*) pSmoothedIm_;

      if(NULL != pSmooth)
      {
      /*Apply 3x3 median filter to reduce shot noise*/
      for (unsigned int i=0; i<width; i++) {
         for (unsigned int j=0; j<height; j++) {
            x[0]=i-1;
            y[0]=(j-1);
            x[1]=i;
            y[1]=(j-1);
            x[2]=i+1;
            y[2]=(j-1);
            x[3]=i-1;
            y[3]=(j);
            x[4]=i;
            y[4]=(j);
            x[5]=i+1;
            y[5]=(j);
            x[6]=i-1;
            y[6]=(j+1);
            x[7]=i;
            y[7]=(j+1);
            x[8]=i+1;
            y[8]=(j+1);
            // truncate the median filter window  -- duplicate edge points
            // this could be more efficient, we could fill in the interior image [1,w0-1]x[1,h0-1] then explicitly fill in the edge pixels.
            // also the temporary image could be as small as 2 rasters of the image
            for(int ij =0; ij < 9; ++ij)
            {
               if( x[ij] < 0)
                  x[ij] = 0;
               else if( int(width-1) < x[ij])
                  x[ij] = int(width-1);
               if( y[ij] < 0)
                  y[ij] = 0;
               else if( int(height-1) < y[ij])
                  y[ij] = (int)(height-1);
            }
            std::vector<PixelType> windo;
            for(int ij = 0; ij < 9; ++ij)
            {
               windo.push_back(pI[ x[ij] + width*y[ij]]);
            }
            pSmooth[i + j*width] = FindMedian(windo);
         }
      }

      memcpy( pI, pSmoothedIm_, thisSize);
      }
      else
         ret = DEVICE_ERR;

      return ret;
   }
   int Process(unsigned char* buffer, unsigned width, unsigned height, unsigned byteDepth);

   // action interface
   // ----------------
   int OnPerformanceTiming(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   bool busy_;
   MM::MMTime performanceTiming_;
   void*  pSmoothedIm_;
   unsigned long sizeOfSmoothedIm_;
   


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
      {
         CreateHubIDProperty();
      }

   ~DemoAutoFocus() {}
      
   // MMDevice API
   bool Busy() {return busy_;}
   void GetName(char* pszName) const;

   int Initialize();
   int Shutdown(){initialized_ = false; return DEVICE_OK;}

   // AutoFocus API
   virtual int SetContinuousFocusing(bool state)
   {
      running_ = state;
      return DEVICE_OK;
   }
   virtual int GetContinuousFocusing(bool& state)
   {
      state = running_;
      return DEVICE_OK;
   }
   virtual bool IsContinuousFocusLocked() { return running_; }
   virtual int FullFocus() { return DEVICE_OK; }
   virtual int IncrementalFocus() { return DEVICE_OK; }
   virtual int GetLastFocusScore(double& score)
   {
      score = 0.0;
      return DEVICE_OK;
   }
   virtual int GetCurrentFocusScore(double& score)
   {
      score = 1.0;
      return DEVICE_OK;
   }
   virtual int GetOffset(double& /*offset*/) { return DEVICE_OK; }
   virtual int SetOffset(double /*offset*/) { return DEVICE_OK; }

private:
   bool running_;
   bool busy_;
   bool initialized_;
};

struct Point
{
   public:
      Point(int lx, int ly) {x = lx; y = ly;};
   int x;
   int y;
};

struct PointD
{
   public:
      PointD(double lx, double ly) {x = lx; y = ly;};
   double x;
   double y;
};
//////////////////////////////////////////////////////////////////////////////
// DemoGalvo class
// Simulation of Galvo device
//////////////////////////////////////////////////////////////////////////////
class DemoGalvo : public CGalvoBase<DemoGalvo>, ImgManipulator
{
public:
   DemoGalvo();
   ~DemoGalvo();
      
   // MMDevice API
   bool Busy() {return busy_;}
   void GetName(char* pszName) const;

   int Initialize();
   int Shutdown(){initialized_ = false; return DEVICE_OK;}

   // Galvo API
   int PointAndFire(double x, double y, double pulseTime_us); 
   int SetSpotInterval(double pulseTime_us);
   int SetPosition(double x, double y);
   int GetPosition(double& x, double& y);
   int SetIlluminationState(bool on);
   int AddPolygonVertex(int polygonIndex, double x, double y);
   int DeletePolygons();
   int LoadPolygons();
   int SetPolygonRepetitions(int repetitions);
   int RunPolygons();
   int RunSequence();
   int StopSequence();
   int GetChannel(char* channelName);                         

   double GetXRange();                         
   double GetYRange(); 

   int ChangePixels(ImgBuffer& img);
   static bool PointInTriangle(Point p, Point p0, Point p1, Point p2);

private:

   unsigned short gaussianMask_[10][10];

   double GaussValue(double amplitude, double sigmaX, double sigmaY, int muX, int muY, int x, int y);
   Point GalvoToCameraPoint(PointD GalvoPoint, ImgBuffer& img);
   void GetBoundingBox(std::vector<Point>& vertex, std::vector<Point>& bBox);
   bool InBoundingBox(std::vector<Point> boundingBox, Point testPoint);

   std::map<int, std::vector<PointD> > vertices_;
   MM::MMTime pfExpirationTime_;
   bool initialized_;
   bool busy_;
   bool illuminationState_;
   bool pointAndFire_;
   bool runROIS_;
   double xRange_;
   double yRange_;
   double currentX_;
   double currentY_;
   int offsetX_;
   double vMaxX_;
   int offsetY_;
   double vMaxY_;
};



#endif //_DEMOCAMERA_H_
