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

#include "FlyCapture2.h"

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_IN_READ_REGISTER                    12300
#define ERR_NOT_READY_FOR_SOFTWARE_TRIGGER      12301
#define ERR_UNAVAILABLE_TRIGGER_MODE_REQUESTED  12302
#define ERR_UNKNOWN_TRIGGER_MODE_STRING         12303

// Trigger modes
#define TRIGGER_INTERNAL   0
#define TRIGGER_EXTERNAL   1
#define TRIGGER_SOFTWARE   2

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

private:
   void updatePixelFormats(unsigned int pixelFormatBitField);
   int SetEndianess(bool little);
   const char* GetBusSpeedAsString(BusSpeed speed);
   int CheckSoftwareTriggerPresence(FlyCapture2::Camera* pCam, bool& result);
   int PollForTriggerReady(FlyCapture2::Camera* pCam, const unsigned long timeoutMs);
   bool FireSoftwareTrigger(FlyCapture2::Camera* pCam);
   int SetTriggerMode(FlyCapture2::Camera* pCam, const unsigned short newMode);
   int SetGrabTimeout(FlyCapture2::Camera* pCam, const unsigned long timeoutMs);
   int TriggerModeFromString(std::string mode, unsigned short& tMode);
   std::string TriggerModeAsString(const unsigned short mode) const;


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
   FlyCapture2::Format7Info format7Info_;
   std::map<VideoMode, std::vector<FrameRate>> videoModeFrameRateMap_;
   std::map<long, std::string> bin2Mode_;
   std::map<const std::string, long> mode2Bin_;
   std::vector<FlyCapture2::Mode> availableFormat7Modes_;
   std::vector<unsigned short> availableTriggerModes_;
   bool f7InUse_;
   double exposureTimeMs_;
   unsigned short triggerMode_;
   unsigned short snapTriggerMode_;
   unsigned long externalTriggerGrabTimeout_;
   FlyCapture2::PixelFormat pixelFormat8Bit_;
   FlyCapture2::PixelFormat pixelFormat16Bit_;
};