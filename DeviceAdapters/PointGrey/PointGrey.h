///////////////////////////////////////////////////////////////////////////////
// FILE:          PointGrey.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Point Grey FlyCapture Micro-Manager adapter
//                
// AUTHOR:        Nico Stuurman
// COPYRIGHT:     University of California, 2016
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
#include "MMDeviceConstants.h"
#include <bitset>

#include "FlyCapture2.h"

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_IN_READ_REGISTER                       12300
#define ERR_NOT_READY_FOR_SOFTWARE_TRIGGER         12301

// Trigger sources
#define TRIGGER_GPIO_0                          0
#define TRIGGER_GPIO_1                          1
#define TRIGGER_GPIO_2                          2
#define TRIGGER_GPIO_3                          3
#define TRIGGER_SOFTWARE                        7

// Trigger mode defs from FC2 register reference
#define TMODE_STD                               0
#define TMODE_EXPOSURE                          1
#define TMODE_SKIP_N_FRAMES                     3
#define TMODE_MULTIPLE_EXPOSURE                 4
#define TMODE_MULTIPLE_EXPOSURE_PULSE_WIDTH     5
#define TMODE_LOW_SMEAR                         13
#define TMODE_OVERLAPPED_READOUT                14
#define TMODE_MULTI_SHOT                        15

// Trigger polarity codes
#define TPOL_LOW                                0
#define TPOL_HIGH                               1

// Trigger On/Off codes
#define TRIGGER_OFF                             0
#define TRIGGER_ON                              1

using namespace FlyCapture2;

//////////////////////////////////////////////////////////////////////////////

class SequenceThread;

class PointGrey : public CCameraBase<PointGrey>  
{
public:
   PointGrey(const char* deviceName);
   ~PointGrey();
  
   //////////////////////////////////////////////////////////////
   // MMDevice API
   int Initialize();
   int Shutdown();
   void GetName(char* name) const;      
   
   //////////////////////////////////////////////////////////////
   // PointGreyCamera API
   int SnapImage();
   const unsigned char* GetImageBuffer();
   unsigned int GetNumberOfComponents()  const { return nComponents_;};
   //////////////////////////////////////////////////////////////
   unsigned int GetImageWidth() const;
   unsigned int GetImageHeight() const;
   //////////////////////////////////////////////////////////////
   unsigned int GetImageBytesPerPixel() const;
   unsigned int GetBitDepth() const;
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
   int      InsertImage(Image* pImg) const;
   int      GetBinning() const;
   int      SetBinning(int binSize);
   int      IsExposureSequenceable(bool& seq) const {seq = false; return DEVICE_OK;}
   /////////////////////////////////////////////////////////////
   // Functions to convert between PGR and MM
   int CameraPGRGuid(FlyCapture2::BusManager* busMgr, FlyCapture2::PGRGuid* guid, int nr);
   int static CameraID(FlyCapture2::PGRGuid id, std::string* camIDString);
   int CameraGUIDfromOurID(FlyCapture2::BusManager* busMgr, FlyCapture2::PGRGuid* guid, std::string ourID);
   void VideoModeAndFrameRateStringFromEnums(std::string &readableString, FlyCapture2::VideoMode vm, FlyCapture2::FrameRate fr) const;
   int VideoModeAndFrameRateEnumsFromString(std::string readableString, FlyCapture2::VideoMode &vm, FlyCapture2::FrameRate &fr) const;
   std::string PixelTypeAsString(PixelFormat pixelFormat) const;
   PixelFormat PixelFormatFromString(std::string pixelType) const;
   std::string Format7ModeAsString(Mode mode) const;
   int Format7ModeFromString(std::string pixelType,  Mode* mode) const;

   //////////////////////////////////////////////////////////////
   // action interface
   int OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnBinningFromFormat7Mode(MM::PropertyBase* pProp, MM::ActionType eAct, long value);
   int OnAbsValue(MM::PropertyBase* pProp, MM::ActionType eAct, long value);
   int OnValue(MM::PropertyBase* pProp, MM::ActionType eAct, long value);
   int OnOnOff(MM::PropertyBase* pProp, MM::ActionType eAct, long value);
   int OnAutoManual(MM::PropertyBase* pProp, MM::ActionType eAct, long value);
   int OnVideoModeAndFrameRate(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnFormat7Mode(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTriggerMode(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTriggerSource(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTriggerPolarity(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTriggerParameter(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTriggerOnOff(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTriggerTimeout(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   void updatePixelFormats(unsigned int pixelFormatBitField);
   int SetEndianess(bool little);
   const char* GetBusSpeedAsString(BusSpeed speed);
   int SetGrabTimeout(const unsigned long timeoutMs);
   int PowerCameraOn(const unsigned int timeoutMs);
   const unsigned char* RGBToRGBA(const unsigned char* img) const;

   //Trigger functions
   //int CheckSoftwareTriggerPresence(bool& result);
   int PollForTriggerReady(const unsigned long timeoutMs);
   bool FireSoftwareTrigger();
   int SetTriggerSource(const unsigned short newSource);
   int SetTriggerMode(const unsigned short newMode);
   int TriggerSourceFromString(std::string source, unsigned short& tSource);
   std::string TriggerSourceAsString(const unsigned short source) const;
   int TriggerModeFromString(std::string mode, unsigned short& tMode);
   std::string TriggerModeAsString(const unsigned short mode) const;
   int TriggerPolarityFromString(std::string polarity, unsigned short& tPol);
   std::string TriggerPolarityAsString(const unsigned short polarity) const;
   int TriggerOnOffFromString(std::string onOff, unsigned short& tOnOff);
   std::string TriggerOnOffAsString(const unsigned short onOff) const;
   int GetTriggerMode();
   int GetTriggerInfo();
   int FindSupportedTriggerModes();
   int FindSupportedTriggerSources();
   int SetTriggerPolarity(unsigned short polarity);
   int SetTriggerOnOff(unsigned short onOff);


   FlyCapture2::PGRGuid guid_;
   FlyCapture2::Camera cam_;
   FlyCapture2::Image image_;
   unsigned int nComponents_;
   bool initialized_;
   std::string deviceName_;
   MM::MMTime sequenceStartTime_;
   MM::MMTime sequenceStartTimeStamp_;
   long imageCounter_;
   bool stopOnOverflow_;
   long desiredNumImages_;
   bool isCapturing_;
   bool snapSoftwareTrigger_;
   FlyCapture2::Format7Info format7Info_;
   std::map<VideoMode, std::vector<FrameRate>> videoModeFrameRateMap_;
   std::map<long, std::string> bin2Mode_;
   std::map<const std::string, long> mode2Bin_;
   std::vector<FlyCapture2::Mode> availableFormat7Modes_;
   bool f7InUse_;
   double exposureTimeMs_;
   long externalTriggerGrabTimeout_;
   unsigned short bytesPerPixel_;
   MMThreadLock imgBuffLock_;
   const unsigned char* imgBuf_;
   const unsigned long bufSize_;
   FlyCapture2::PixelFormat pixelFormat8Bit_;
   FlyCapture2::PixelFormat pixelFormat16Bit_;
   FlyCapture2::TriggerModeInfo triggerModeInfo_;
   FlyCapture2::TriggerMode triggerMode_;
   std::map<const int, std::string> triggerModesSupported_;
   std::map<const int, std::string> triggerSourcesSupported_;

};