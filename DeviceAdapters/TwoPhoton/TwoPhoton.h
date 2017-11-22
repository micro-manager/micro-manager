///////////////////////////////////////////////////////////////////////////////
// FILE:          TwoPhoton.h
// PROJECT:       100X micro-manager extensions
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Video frame grabber interface with multiple PMTs attached
//
// COPYRIGHT:     Nenad Amodaj 2011, 100X Imaging Inc 2009
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
//                
// AUTHOR:        Nenad Amodaj, November 2009
//                

#pragma once

#include "DeviceBase.h"
#include "ImgAccumulator.h"
#include "DeviceThreads.h"
#include "BFCamera.h"
#include "VirtualShutter.h"
#include <string>
#include <vector>
#include <map>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_UNKNOWN_BINNING_MODE 410
#define ERR_UNKNOWN_INPUT_CHANNEL 411
#define ERR_HARDWARE_NOT_INITIALIZED 412
#define ERR_SNAP_FAILED 413
#define ERR_NOT_WARMED_UP 414
#define ERR_PORT_CHANGE_FORBIDDEN    10004
#define ERR_UNKNOWN_DA_DEVICE 415
#define ERR_NUM_CHANNELS 416
#define ERR_NO_CHANNELS_DETECTED 417 

//////////////////////////////////////////////////////////////////////////////
// BitFlowCamera class
// Frame-grabber video mode adapter for BitFlow
//////////////////////////////////////////////////////////////////////////////

class BitFlowCamera : public CCameraBase<BitFlowCamera>  {
public:
   BitFlowCamera(bool dual);
   ~BitFlowCamera();
  
   // MMDevice API
   // ------------
   int Initialize();
   int Shutdown();
  
   void GetName(char* name) const;      
   bool Busy() {return false;}
  
   // MMCamera API
   // ------------
   int SnapImage();
   const unsigned char* GetImageBuffer();
   const unsigned char* GetImageBuffer(unsigned chNo);

   unsigned GetNumberOfChannels() const;
   int GetChannelName(unsigned channel, char* name);

   unsigned GetNumberOfComponents() const;
   int GetComponentName(unsigned channel, char* name);

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
   int GetBinning() const;
   int SetBinning(int binSize);
   int IsExposureSequenceable(bool& seq) const {seq = false; return DEVICE_OK;}

    /**
     * Starts continuous acquisition.
     */
    int StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow);
    int StartSequenceAcquisition(double interval_ms);
    int StopSequenceAcquisition();
    int PrepareSequenceAcqusition();
    
    /**
     * Flag to indicate whether Sequence Acquisition is currently running.
     * Return true when Sequence acquisition is activce, false otherwise
     */
    bool IsCapturing();

   // action interface
   // ----------------
   int OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnInputChannel(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnDeinterlace(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnFilterMethod(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnFrameInterval(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnProcessingTime(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnCenterOffset(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnChannelOffset(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnColorMode(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnEnableChannels(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnEnableBitflowChannels(MM::PropertyBase* pProp, MM::ActionType eAct);

   void ShowError(const char* errTxt) {LogMessage(errTxt);}

private:
   static const int imageWidth_ = 406;
   static const int maxFrames_ = 200;

   std::vector<ImgAccumulator> img_;
   unsigned int numChannels_;
   int byteDepth_;

   std::vector<int> GetEnabledChannels() const {
	   std::vector<int> ech;
	   for (unsigned i=0; i<img_.size(); i++)
	   {
		  if (img_[i].IsEnabled())
			ech.push_back(i);
	   }
	   return ech;
   }
   friend class LiveThread;
   class LiveThread : public MMDeviceThreadBase {
      public:
         LiveThread(BitFlowCamera* cam) : cam_(cam), stopRunning_(false), running_(false), imageCounter_(0),
         numImages_(-1) {}
         ~LiveThread() {}
      
         bool IsRunning() {return running_;}
         void Abort();
         void SetNumImages(long num) {numImages_ = num;}

         // thread procedure
         int svc();
      
      private:
         BitFlowCamera* cam_;
         bool running_;
         bool stopRunning_;
         long imageCounter_;
         long numImages_;
   };

   struct ROI
   {
      int x;
      int y;
      int xSize;
      int ySize;

      ROI() : x(0), y(0), xSize(0), ySize(0) {}
      ~ROI() {}

      void Set(int xPos, int yPos, int xDim, int yDim)
      {
         x = xPos;
         y = yPos;
         xSize = xDim;
         ySize = yDim;
      }
   };

   enum ColorMode {
      Grayscale = 0,
      Color,
      MultiChannel
   };

   ROI roi_;

   bool initialized_;
   int expNumFrames_;
   int inputChannel_;
   LiveThread* liveThd_;
   BFCamera bfDev_;
   int binSize_;
   unsigned char* frameBuf_;
   unsigned char* scratchBuf_;
   unsigned char* lineBuf_;
   MM::MMTime startTime_;
   double intervalMs_;
   double processingTimeMs_;
   bool deinterlace_;
   bool cosineWarp_;
   bool channelsProcessed_;
   bool rawFramesToCircularBuffer_;
   int frameOffset_;
   std::vector<int> channelOffsets_;
   std::vector<int> pixelLookup_;
   std::vector<int> altPixelLookup_;
   
   int ResizeImageBuffer();
   void GenerateSyntheticImage(void* buffer, unsigned width, unsigned height, unsigned depth, double exp);
   int GetChannelOffset(int index);

   void DeinterlaceBuffer(unsigned char* buf, int buferLength, unsigned rawWidth, bool warp);
   void ConstructImage(unsigned char* buf, int bufLen, unsigned rawWidth, bool warp);
   void ConstructImageSingle(unsigned char* buf, int bufLen, unsigned rawWidth, bool cont, bool warp);

   void MirrorBuffer(unsigned char* buf, int bufLen, unsigned numChannels, unsigned rawWidth, unsigned rawHeight);
   static void GetCosineWarpLUT(std::vector<int> &new_pixel, int image_width, int raw_width);
   void setDeinterlace(bool enable) {deinterlace_ = enable;}
   bool isDeinterlace() {return deinterlace_;}
};

