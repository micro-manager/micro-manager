///////////////////////////////////////////////////////////////////////////////
// FILE:          TSICam.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Thorlabs Scientific Imaging compatible camera adapter
//                
// AUTHOR:        Nenad Amodaj, 2012
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
#include <TsiSDK.h>
#include <TsiCamera.h>
#include <TsiImage.h>

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

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_TSI_DLL_LOAD_FAILED           10010
#define ERR_TSI_SDK_LOAD_FAILED           10011
#define ERR_TSI_CAMERA_NOT_FOUND          10012
#define ERR_TSI_OPEN_FAILED               10013
#define ERR_CAMERA_OPEN_FAILED            10014
#define ERR_IMAGE_TIMED_OUT               10015

//////////////////////////////////////////////////////////////////////////////
// Region of Interest
struct ROI {
   unsigned x;
   unsigned y;
   unsigned xSize;
   unsigned ySize;

   ROI() : x(0), y(0), xSize(0), ySize(0) {}
   ROI(unsigned _x, unsigned _y, unsigned _xSize, unsigned _ySize )
      : x(_x), y(_y), xSize(_xSize), ySize(_ySize) {}
   ~ROI() {}

   bool isEmpty() {return x==0 && y==0 && xSize==0 && ySize == 0;}
   void clear() {x=0; y=0; xSize=0; ySize=0;}
};

class AcqSequenceThread;

//////////////////////////////////////////////////////////////////////////////
// Implementation of the MMDevice and MMCamera interfaces
// for all TSI api compatible cameras
//
class TsiCam : public CCameraBase<TsiCam>
{
   friend AcqSequenceThread;

public:
   TsiCam();
   ~TsiCam();

   static TsiSDK* tsiSdk;

   // MMDevice API
   int Initialize();
   int Shutdown();
   void GetName(char* pszName) const;
   bool Busy();
   
   // MMCamera API
   int SnapImage();
   const unsigned char* GetImageBuffer();
   unsigned GetImageWidth() const {return img.Width();}
   unsigned GetImageHeight() const {return img.Height();}
   unsigned GetImageBytesPerPixel() const {return img.Depth();} 
   long GetImageBufferSize() const {return img.Width() * img.Height() * GetImageBytesPerPixel();}
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
   int OnTaps(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnGain(MM::PropertyBase* pProp, MM::ActionType eAct);

   // stubs
   int OnTemperature(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTemperatureSetPoint(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnFps(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTriggerMode(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnChipName(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnMultiplierGain(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnReadoutPort(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnOffset(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnUniversalProperty(MM::PropertyBase* pProp, MM::ActionType eAct, long index);
   int OnTriggerTimeOut(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   bool GetAttrValue(TSI_PARAM_ID ParamID, TSI_ATTR_ID AttrID, void *Data, uint32_t DataLength);
   bool ParamSupported (TSI_PARAM_ID ParamID);
   int ResizeImageBuffer();
   int ResizeImageBuffer(TSI_ROI_BIN& roiBin);
   void SuspendSequence();
   int ResumeSequence();
   int GetImageParameters();
   int PushImage(unsigned char* imgBuf);
   static void ReadoutComplete(int callback_type_id, TsiImage *tsi_image, void *context);

   ImgBuffer img;
   bool initialized;
   bool stopOnOverflow;
   long acquiring;
   TsiCamera* camHandle_;
   TSI_ROI_BIN roiBinData;
   TSI_ROI_BIN fullFrame;

   friend class AcqSequenceThread;
   AcqSequenceThread*   liveAcqThd_;
};

/*
 * Acquisition thread
 */
class AcqSequenceThread : public MMDeviceThreadBase
{
   public:
      AcqSequenceThread(TsiCam* camera) : 
         stop(0), camInstance(camera), numFrames(0) {}
      ~AcqSequenceThread() {}
      int svc (void);

      void Stop()
      {
         InterlockedExchange(&stop, 1);
      }
      void Start() {stop = false; activate();}
      void SetNumFrames(unsigned numf) {numFrames = numf;}
    
   private:
      long stop;
      TsiCam* camInstance;
      unsigned numFrames;
};
