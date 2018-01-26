///////////////////////////////////////////////////////////////////////////////
// FILE:          TSI3Cam.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Thorlabs Scientific Imaging compatible camera adapter,
//                SDK 3
//                
// AUTHOR:        Nenad Amodaj, 2017
// COPYRIGHT:     Thorlabs
//
// DISCLAIMER:    This file is provided WITHOUT ANY WARRANTY;
//                without even the implied warranty of MERCHANTABILITY or
//                FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//


#pragma once

#include <DeviceBase.h>
#include <ImgBuffer.h>
#include <DeviceUtils.h>
#include <DeviceThreads.h>
#include "TsiLibrary.h"
#include "thorlabs_tsi_camera_sdk.h"

#ifdef WIN32
//...
#endif

#ifdef __APPLE__
//...
#endif

#ifdef linux
//...
#endif

#include <string>
#include <map>

struct Tsi3RoiBin
{
   int xOrigin;
   int yOrigin;
   int xPixels;
   int yPixels;
   int xBin;
   int yBin;
   int pixDepth;
   int bitDepth;

   Tsi3RoiBin()
   {
      xOrigin = 0;
      yOrigin = 0;
      xPixels = 0;
      yPixels = 0;
      xBin = 1;
      yBin = 1;
      pixDepth = 2;
      bitDepth = 16;
   }
};

//////////////////////////////////////////////////////////////////////////////
// Implementation of the MMDevice and MMCamera interfaces
// for all TSI SDK 3 api compatible cameras
//
class Tsi3Cam : public CCameraBase<Tsi3Cam>
{

public:
   Tsi3Cam();
   ~Tsi3Cam();

   // MMDevice API
   int Initialize();
   int Shutdown();
   void GetName(char* pszName) const;
   bool Busy();
   
   // MMCamera API
   int SnapImage();
   const unsigned char* GetImageBuffer(unsigned chNum);
   const unsigned int* GetImageBufferAsRGB32();
   const unsigned char* GetImageBuffer();

   unsigned GetNumberOfComponents() const;
   unsigned GetNumberOfChannels() const;
   int GetChannelName(unsigned channel, char* name);

   unsigned GetImageWidth() const {return img.Width();}
   unsigned GetImageHeight() const {return img.Height();}
   unsigned GetImageBytesPerPixel() const {return img.Depth();} 
   long GetImageBufferSize() const;
   unsigned GetBitDepth() const;
   int GetBinning() const;
   int SetBinning(int binSize);
   double GetExposure() const;
   void SetExposure(double dExp);
   int SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize); 
   int GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize);
   int ClearROI();

   // overrides the same in the base class
   int InsertImage();
   int PrepareSequenceAcqusition();
   int StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow);
   int StartSequenceAcquisition(double interval);
   int StopSequenceAcquisition(); 
   bool IsCapturing();
   
   int IsExposureSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}

   // action interface
   int OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnReadoutRate(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTemperature(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTemperatureSetPoint(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnFps(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTriggerMode(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTriggerPolarity(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   int ResizeImageBuffer();
   void ResetImageBuffer();
   bool StopCamera();
   bool StartCamera(int frames);

   static void frame_available_callback(void* sender, unsigned short* image_buffer, int image_width, int image_height, int bit_depth, int number_of_color_channels, int frame_count, void* context);

   ImgBuffer img;
   bool initialized;
   bool stopOnOverflow;
   int imageCount;
   void* camHandle;
   long acquiringSequence;
   long acquiringFrame;

   Tsi3RoiBin fullFrame;

   TRIGGER_TYPE trigger;
   TRIGGER_POLARITY triggerPolarity;
};
