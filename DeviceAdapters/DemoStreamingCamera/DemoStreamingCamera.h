///////////////////////////////////////////////////////////////////////////////
// FILE:          DemoStreamingCamera.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   The fast, straming camera simulator.
//                
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 06/05/2007
//
// COPYRIGHT:     University of California, San Francisco, 2007
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
// CVS:           $Id: DemoCamera.h 73 2007-04-19 00:11:35Z nenad $
//

#ifndef _DEMO_STREAMINGCAMERA_H_
#define _DEMO_STREAMINGCAMERA_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/ImgBuffer.h"
#include "../../MMDevice/DeviceThreads.h"
#include <string>
#include <map>


//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_UNKNOWN_MODE            102
#define ERR_BUSY_ACQIRING           105
#define ERR_UNSUPPORTED_IMAGE_TYPE  106
#define ERR_DEVICE_NOT_AVAILABLE    107

class AcqSequenceThread;

//////////////////////////////////////////////////////////////////////////////
// DemoStreamingCamera class
// Simulation of the fast streaming Camera device
//////////////////////////////////////////////////////////////////////////////
class DemoStreamingCamera : public CCameraBase<DemoStreamingCamera>  
{
public:
   DemoStreamingCamera();
   ~DemoStreamingCamera();
  
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
   const unsigned int* GetImageBufferAsRGB32();
   unsigned GetNumberOfChannels() const;
   int GetChannelName(unsigned int channel, char* name);
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
   int StartSequenceAcquisition(long numImages, double interval_ms);
   int StopSequenceAcquisition();
   double GetNominalPixelSizeUm() const {return nominalPixelSizeUm_;}
   double GetPixelSizeUm() const {return nominalPixelSizeUm_ * GetBinning();}
   int GetBinning() const;
   int SetBinning(int binSize);

   // action interface
   // ----------------
   int OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnReadoutTime(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnColorMode(MM::PropertyBase* pProp, MM::ActionType eAct);

   // custom interface for thread callbacks
   long GetCounter() {return imageCounter_;}
   long GetSequenceLength() {return sequenceLength_;}
   int PushImage();

private:
   static const double nominalPixelSizeUm_;
   static const int imageSize_;

   ImgBuffer img_[3];
   bool initialized_;
   bool busy_;
   long readoutUs_;
   MM::MMTime readoutStartTime_;
   AcqSequenceThread* acqThread_;
   long imageCounter_;
   long sequenceLength_;
   bool acquiring_;
   bool color_;
   unsigned char* rawBuffer_;

   void GenerateSyntheticImage(ImgBuffer& img, double exp);
   int ResizeImageBuffer();
};

//////////////////////////////////////////////////////////////////////////////
// DemoNoiseProcessor class
// Demonstration of the image processor module.
//////////////////////////////////////////////////////////////////////////////
class DemoNoiseProcessor : public CImageProcessorBase<DemoNoiseProcessor>  
{
public:
   DemoNoiseProcessor();
   ~DemoNoiseProcessor();
  
   // MMDevice API
   // ------------
   int Initialize();
   int Shutdown();
  
   void GetName(char* name) const;      
   bool Busy();
   
   // ImageProcessor API
   // ------------------
   int Process(unsigned char* buffer, unsigned width, unsigned height, unsigned byteDepth);

   // action interface
   // ----------------
   int OnStdDev(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   bool initialized_;
   double stdDev_;
};

//////////////////////////////////////////////////////////////////////////////
// DemoNoiseProcessor class
// Demonstration of the image processor module.
//////////////////////////////////////////////////////////////////////////////
class DemoSignalGenerator : public CImageProcessorBase<DemoSignalGenerator>  
{
public:
   DemoSignalGenerator();
   ~DemoSignalGenerator();
  
   // MMDevice API
   // ------------
   int Initialize();
   int Shutdown();
  
   void GetName(char* name) const;      
   bool Busy();
   
   // ImageProcessor API
   // ------------------
   int Process(unsigned char* buffer, unsigned width, unsigned height, unsigned byteDepth);

   // action interface
   // ----------------
   int OnOutputDevice(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   bool initialized_;
   std::string outputDevice_;
   MM::State* pStateDevice_;
};

/**
 * Acquisition thread
 */
class AcqSequenceThread : public MMDeviceThreadBase
{
public:
   AcqSequenceThread(DemoStreamingCamera* pCam) : 
      intervalMs_(100.0), numImages_(1), busy_(false), stop_(false) {camera_ = pCam;}
   ~AcqSequenceThread() {}
   int svc(void);

   void SetInterval(double intervalMs) {intervalMs_ = intervalMs;}
   void SetLength(long images) {numImages_ = images;}
   void Stop() {stop_ = true;}
   void Start();

private:
   DemoStreamingCamera* camera_;
   double intervalMs_;
   long numImages_;
   bool busy_;
   bool stop_;
};


#endif //_DEMO_STREAMINGCAMERA_H_
