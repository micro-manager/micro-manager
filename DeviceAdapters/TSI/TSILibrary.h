///////////////////////////////////////////////////////////////////////////////
// FILE:          TSILibrary.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Thorlabs Scientific Imaging device library
//                
// AUTHOR:        Nenad Amodaj, 2012, 2017
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

static const char* g_DeviceTsiCam = "TSICam";
static const char* g_DeviceTsi3Cam = "TSI3Cam";

static const char* g_ReadoutRate = "ReadoutRate";
static const char* g_Gain = "Gain";
static const char* g_NumberOfTaps = "Taps";
static const char* g_ColorFilterArray = "SensorArray";
static const char* g_WhiteBalance = "WhiteBalance";
static const char* g_TriggerMode = "TriggerMode";
static const char* g_TriggerPolarity = "TriggerPolarity";
static const char* g_ColorEnable = "Color";
static const char* g_FirmwareVersion = "FirmwareVersion";
static const char* g_SerialNumber = "SerialNumber";


static const char* g_Set = "SetNow";
static const char* g_Off = "Off";
static const char* g_On = "On";
static const char* g_Yes = "Yes";
static const char* g_No = "No";
static const char* g_Software = "Software";
static const char* g_HardwareEdge = "HardwareStandard";
static const char* g_HardwareDuration = "HardwareBulb";
static const char* g_Positive = "Positive";
static const char* g_Negative = "Negative";


//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_TSI_DLL_LOAD_FAILED           10010
#define ERR_TSI_SDK_LOAD_FAILED           10011
#define ERR_TSI_CAMERA_NOT_FOUND          10012
#define ERR_TSI_OPEN_FAILED               10013
#define ERR_CAMERA_OPEN_FAILED            10014
#define ERR_IMAGE_TIMED_OUT               10015
#define ERR_INVALID_CHANNEL_INDEX         16016
#define ERR_INTERNAL_ERROR                16017
#define ERR_ROI_BIN_FAILED                16018
#define ERR_TRIGGER_FAILED                16019

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



