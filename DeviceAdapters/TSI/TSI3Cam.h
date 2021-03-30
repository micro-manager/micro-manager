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
#include "tl_camera_sdk.h"
#include "tl_camera_sdk_load.h"

#ifdef WIN32
//...
#endif

#ifdef __APPLE__
//...
#endif

#ifdef __linux__
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

struct Roi
{
	unsigned x;
	unsigned y;
	unsigned xSize;
	unsigned ySize;

	Roi()
	{
		x = 0;
		y = 0;
		xSize = 0;
		ySize = 0;
	}

	bool isSet() { return xSize != 0 && ySize  != 0;}
};

enum PolarImageType
{
	Intensity = 0,
	Raw,
	Azimuth,
	DoLP,
	Quad
};

static const char* dllLoadErr = "Error loading color processing functions from the dll";

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
   unsigned GetImageBytesPerPixel() const;
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
   int OnEEP(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnHotPixEnable(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnHotPixThreshold(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnWhiteBalance(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPolarImageType(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnGain(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   int ResizeImageBuffer();
   void ResetImageBuffer();
   bool StopCamera();
   bool StartCamera(int frames);
	int ColorProcess16to32(unsigned short* monoBuffer, unsigned char* colorBuffer, int width, int height);
	int ColorProcess16to48WB(unsigned short* monoBuffer, unsigned short* colorBuffer, int width, int height);
	int ColorProcess16to64(unsigned short* monoBuffer, unsigned char* colorBuffer, int width, int height);
	int InitializeColorProcessor(bool wb=false);
	int InitializePolarizationProcessor();
	int TransformPolarizationImage(unsigned short* monoBuffer, unsigned char* outBuffer, int width, int height, PolarImageType imgType);
	static void SeparateQuadViewAngles(int polarPhase, unsigned short* sourceImage, unsigned short* destImage, int sourceWidth, int sourceHeight);
	int ShutdownColorProcessor();
	int ShutdownPolarizationProcessor();
	int ClearWhiteBalance();
	int SetWhiteBalance();
	int ApplyWhiteBalance(double redScaler, double greenScaler, double blueScaler);
	void EnableColorOutputLUTs();
	int GetCameraROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize);

   static void frame_available_callback(void* sender, unsigned short* image_buffer, int frame_count, unsigned char* metadata, int metadata_size_in_bytes, void* context);

   ImgBuffer img;
   int frameNumber;
	std::vector<unsigned short> demosaicBuffer;
   bool initialized;
   bool prepared;
	static bool globalColorInitialized;
	static bool globalPolarizationInitialized;
   bool stopOnOverflow;
   void* camHandle;
   void* colorProcessor;
	void* polarizationProcessor;
   long acquiringSequence;
   long acquiringFrame;
   double maxExposureMs;
   bool color;
   bool polarized;
	PolarImageType polarImageType;
	bool whiteBalance;
	int pixelSize;
	int bitDepth;
	LONG whiteBalancePending;
	std::string sdkPath;

   Tsi3RoiBin fullFrame;
	Roi cachedRoi;

   TL_CAMERA_OPERATION_MODE operationMode;
   TL_CAMERA_TRIGGER_POLARITY triggerPolarity;
	TL_COLOR_FILTER_ARRAY_PHASE cfaPhase;
	TL_POLARIZATION_PROCESSOR_POLAR_PHASE polarPhase;
	int cachedImgWidth;
	int cachedImgHeight;
};
