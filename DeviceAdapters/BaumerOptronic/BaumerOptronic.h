///////////////////////////////////////////////////////////////////////////////
// FILE:          BaumerOptronic.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Baumer Optronic adapter for Leica Cameras. 
//                
// AUTHOR:        Karl Hoover
//
// COPYRIGHT:     University of California, San Francisco, 2006
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
// CVS:           $Id: $
//

#ifndef _BAUMEROPTRONIC_H_
#define _BAUMEROPTRONIC_H_

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


//////////////////////////////////////////////////////////////////////////////
// Type definitions
//
typedef std::pair < int, int > FrameSize;


enum WorkerCommand { Noop = 0, 
                     InitializeLibrary = 10, 
                     StartSequence = 20, 
                     SnapCommand =30, 
                     StopSequence = 40, 
                     Exit = 999};
enum WorkerState  { Idle = 0,  
                     Ready = 10, 
                     Acquiring = 20, 
                     Snapping=30, 
                     Busy = 777, 
                     Exiting = 999};


// forward declaration for the implementation thread
class BOImplementationThread;

//////////////////////////////////////////////////////////////////////////////
// CBaumerOptronic class
// Simulation of the Camera device
//////////////////////////////////////////////////////////////////////////////
class CBaumerOptronic : public CCameraBase<CBaumerOptronic>  
{
public:

   friend class BOImplementationThread;

   CBaumerOptronic();
   ~CBaumerOptronic();
  
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
   double GetNominalPixelSizeUm() const {return nominalPixelSizeUm_;}
   double GetPixelSizeUm() const {return nominalPixelSizeUm_ * GetBinning();}
   int GetBinning() const;
   int SetBinning(int binSize);
   int IsExposureSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}

   unsigned  GetNumberOfComponents() const ;

   // high-speed interface
   int StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow);
   int StopSequenceAcquisition();
   bool IsCapturing();
   int InsertImage();
   

   // action interface
   // ----------------
	// floating point read-only property for testing
	int OnTestProperty(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnBitDepth(MM::PropertyBase* pProp, MM::ActionType eAct);
   //int OnReadoutMode(MM::Property* pProp, MM::ActionType eAct);
   int OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnGain(MM::PropertyBase* pProp, MM::ActionType eAct);


private:
   int SetAllowedBinning();
   void GenerateSyntheticImage(ImgBuffer& img, double exp);
   int ResizeImageBuffer();

   static const double nominalPixelSizeUm_;
   ImgBuffer img_;
   MMThreadLock imgBufferLock_;
   unsigned width_;
   unsigned height_;
   unsigned depth_;
   bool initialized_;
   double readoutUs_;
   MM::MMTime readoutStartTime_;
   bool stopOnOverflow_;

   BOImplementationThread* pWorkerThread_; // implementation thread

};


class BOImplementationThread : public MMDeviceThreadBase
{
   public:
      BOImplementationThread(CBaumerOptronic* pCamera);
      ~BOImplementationThread();

      int svc (void);

      void Interval(double intervalMs) {intervalMs_ = intervalMs;}
      double Interval(void) const {return intervalMs_;}
      void SetLength(long images) {numImages_ = images;}
      long FrameCount(void) const {return frameCount_;}
      void FrameCount(const long v_a) { frameCount_ = v_a;};
      void Stop() {stop_ = true;}
      void Start() {stop_ = false; activate();}

      // a lock for each data section that will be modified or viewed in the other thread
      MMThreadLock stateMachineLock_;
      MMThreadLock mmCameraLock_; // when we write data out of here onto the mm device.

      // expose this lock to the caller / container of this thread
      MMThreadLock& StateMachineLock(void) { return stateMachineLock_; };

      void TriggerMode( const bool v);
      void Exposure( int v );
      void Gain(double v);

      bool TriggerMode( void) 
      { 
         return triggerMode_; 
      };

      double Gain(void) 
      { 
         MMThreadGuard g( stateMachineLock_);
         return quantizedGain_; 
      };

      int Exposure(void) 
      { 
         MMThreadGuard g( stateMachineLock_);
         return quantizedExposure_;  // exposure in micro-seconds
      };

      void QueryCapabilities(void);
      void ParseCapabilities(void);

      std::pair<double, double> GainLimits();
      std::pair<int, int> ExposureLimits();

      std::vector< FrameSize > FrameSizes(void) const 
      { 
         return frameSizes_;
      };

      std::vector< int > BinSizes(void) const { return binSizes_;};
      std::vector< int > PixelDepths(void) const { return pixelDepths_;};
      std::vector < int > PossibleBitsInOneColor(void) const { return possibleBitsInOneColor_;};
      std::vector < int > PossibleNPlanes(void) const { return possibleNPlanes_; };

      WorkerCommand Command(void)
      {
         MMThreadGuard g( stateMachineLock_);
         return command_;
      };

      void Command(const WorkerCommand value) 
      {
         MMThreadGuard g( stateMachineLock_);
         command_= value;
      };

      WorkerState CameraState(void)
      {
         MMThreadGuard g( stateMachineLock_);
         return cameraState_;
      };

      void* CurrentImage(   unsigned short& xDim,  unsigned short& yDim, 
         unsigned short& bitsInOneColor, unsigned short& colors,  unsigned long& bufSize, 
         MMThreadGuard** pImageBufferGuard_a);

      void CurrentImageSize( unsigned short& xDim,  unsigned short& yDim, 
         unsigned short& bitsInOneColor, unsigned short& colors,  unsigned long& bufSize);

      bool MonoChrome(void);
      bool Color(void);

      void BitsInOneColor( const int bits);
      int BitsInOneColor(void) { return bitsInOneColor_;};
      void PostError(const int errorCode_a, const char* pMessage_a);

      typedef std::pair< std::string, tBoImgFormat >  NamedFormat;
      std::vector< tpBoImgFormat > imageFormats;
      std::vector< std::string > formatNames;
      typedef std::vector< tBoImgCode > ImageCodesPerFormat;
      typedef std::vector< tBoImgFilter > ImageFiltersPerFormat;
      std::vector< NamedFormat > formats;

      int BinSize (void) const ;
      void BinSize( const int v) ;
      std::string IntelligentErrorString(int fxcode);
      static MMThreadLock imageReadyLock_s;
      static MMThreadLock imageBufferLock_s;
      void SetROI( const unsigned int  x_a, const unsigned int  y_a, const unsigned int  xSize_a, const unsigned int  ySize_a);
      void GetROI(unsigned int & x,unsigned int & y,unsigned int & xSize,unsigned int & ySize);
      void CancelROI(void);

      typedef struct 
      {
         tBoImgFormat f_;
         int formatIndex_;
         std::string name_;
         ImageCodesPerFormat codes_;
         ImageFiltersPerFormat filters_;
      } CompleteFormat;

      std::vector< CompleteFormat > completeFormats_;


   private:

      HANDLE acquisitionThread_;
      HANDLE imageEventArray_[2];

      void CameraState(const WorkerState v)
      {
         MMThreadGuard g( stateMachineLock_);
         cameraState_ = v;
      };

      void Snap(void);
      void Acquire(void);

      double intervalMs_;
      long numImages_;
      bool stop_;
      CBaumerOptronic* pCamera_;
      WorkerState cameraState_;
      WorkerCommand command_;
      bool triggerMode_;
      void* pBuf_; // raw buffer from camera library
      unsigned long bufSize_;
      void* pColorBuf_; // extra buffer with empty color dithered in for gui
      unsigned long colorBufSize_;

      unsigned short xDim_;
      unsigned short yDim_;
      unsigned short bitsInOneColor_;
      unsigned short nPlanes_;
      long frameCount_;

      int BOInitializationSequence(void);
      void LLogMessage( const char*const pM_a, const bool debugOnly_a = false); // log a message to the mm device
      void LLogMessage( const std::string m_a, const bool debugOnly_a = false); // log a message to the mm device
      MM::MMTime CurrentMMTimeMM(); // MMTime as milliseconds
      double quantizedGain_;
      int quantizedExposure_; // microseconds

      std::vector< FrameSize > frameSizes_;
      std::vector< int > binSizes_;
      std::vector< int > pixelDepths_;

      std::vector < int > possibleBitsInOneColor_;
      std::vector < int > possibleNPlanes_;

      std::map < int, std::string> filters_;

      int BinSizeFromCompleteFormat(std::vector< CompleteFormat >::iterator index) const;
      int BitDepthFromCompleteFormat(std::vector< CompleteFormat >::iterator index);
      tBoImgCode ImageCode(void);
      int ImageFormat(void);
      bool ImageFormat( const int v);
      mutable std::vector< CompleteFormat >::iterator completeFormatIter_; // this should always match the format index
      void FindImageFormatInFormatCache( int imageFormatIndex);
      void QueryCameraCurrentFormat(void);

      RECT roi_;
      bool partialScanMode_; //if true then roi_ will have the ROI, else use the image format

};


#endif //_BAUMEROPTRONIC_H_