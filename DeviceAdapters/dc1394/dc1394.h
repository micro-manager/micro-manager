///////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////
// FILE:       dc1394.h
// PROJECT:    MicroManage
// SUBSYSTEM:  DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:
// Firewire camera module for OS X and Linux using libdc1394 API
//                
// AUTHOR: Nico Stuurman, 12/27/2006
// NOTES: 
//
//
#ifndef _DC1394_H_
#define _DC1394_H_

#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/ImgBuffer.h"
#include "../../MMDevice/DeviceUtils.h"
#include "../../MMDevice/DeviceThreads.h"
#include <dc1394/control.h>
#include <string>
#include <map>
#include <boost/make_shared.hpp>
#include <boost/shared_ptr.hpp>
#include <boost/weak_ptr.hpp>

// error codes
#define ERR_BUFFER_ALLOCATION_FAILED 101
#define ERR_INCOMPLETE_SNAP_IMAGE_CYCLE 102
#define ERR_CAMERA_NOT_FOUND 103
#define ERR_CAPTURE_SETUP_FAILED 104
#define ERR_TRANSMISSION_FAILED 105
#define ERR_MODE_LIST_NOT_FOUND 106
#define ERR_CAPTURE_FAILED 107
#define ERR_GET_IMAGE_SIZE_FAILED 108
#define ERR_SET_TRIGGER_MODE_FAILED 109
#define ERR_SET_TRANSMISSION_FAILED 110
#define ERR_GET_TRANSMISSION_FAILED 111
#define ERR_CAMERA_DOES_NOT_SEND_DATA 112
#define ERR_SET_MODE_FAILED 113
#define ERR_ROI_NOT_SUPPORTED 114
#define ERR_GET_CAMERA_FEATURE_SET_FAILED 115
#define ERR_SET_FRAMERATE_FAILED 117
#define ERR_GET_FRAMERATES_FAILED 118
#define ERR_INITIALIZATION_FAILED 119
#define ERR_UNSUPPORTED_COLOR_CODING 120
#define ERR_GET_F7_ROI_FAILED 121
#define ERR_SET_F7_ROI_FAILED 122
#define ERR_GET_F7_BYTESPERPACKET_FAILED 123
#define ERR_SET_F7_BYTESPERPACKET_FAILED 124
#define ERR_GET_F7_COLOR_CODING_FAILED 125
#define ERR_SET_F7_COLOR_CODING_FAILED 126
#define ERR_GET_F7_MAX_IMAGE_SIZE_FAILED 127
#define ERR_NOT_IMPLEMENTED 128
#define ERR_BUSY_ACQUIRING 129
#define ERR_DC1394 130
#define ERR_CAPTURE_TIMEOUT 131

// From Guppy Tech Manual there is:
// 00 0A 47 …. Node_Vendor_Id
#define AVT_VENDOR_ID 2631


// Pseudo-RAII for dc1394 library context
// (Take care of reference counting in one place and ensure library is released
// when all cameras are released.)
class DC1394Context
{
public:
   void Acquire()
   {
      if (singleton_) {
         return;
      }

      singleton_ = s_singleton_instance.lock();
      if (!singleton_) {
         singleton_ = boost::make_shared<Singleton>();
         s_singleton_instance = singleton_;

#ifdef WIN32
         s_retained_singleton = singleton_;
#endif
      }
   }

   dc1394_t* Get() { return singleton_->Get(); }

private:
   class Singleton
   {
      dc1394_t* ctx_;
   public:
      Singleton() { ctx_ = dc1394_new(); }
      ~Singleton() { if (ctx_) dc1394_free(ctx_); }
      dc1394_t* Get() { return ctx_; }
   };

   static boost::weak_ptr<Singleton> s_singleton_instance;

#ifdef WIN32
   // On Windows calling dc1394_free() appears to crash, at least in
   // some cases, so hold on to it while the DLL is loaded (may still
   // crash upon DLL unload).
   static boost::shared_ptr<Singleton> s_retained_singleton;
#endif

   boost::shared_ptr<Singleton> singleton_;
};


// forward declaration
class AcqSequenceThread;


class Cdc1394 : public CCameraBase<Cdc1394>
{
   
friend class AcqSequenceThread;
   
public:
   Cdc1394();
   ~Cdc1394();
   
   // MMDevice API
   int Initialize();
   int Shutdown();
   void GetName(char* pszName) const;
   bool Busy() { return false; }
   bool IsCapturing() {return acquiring_;}
   
   // MMCamera API
   int SnapImage();
   int ProcessImage(dc1394video_frame_t *frame, const unsigned char* destination); 
   const unsigned char* GetImageBuffer();
   unsigned GetImageWidth() const {return img_.Width();}
   unsigned GetImageHeight() const {return img_.Height();}
   unsigned GetImageBytesPerPixel() const {return img_.Depth();} 
   unsigned int GetNumberOfComponents() const;
   int GetComponentName(unsigned channel, char* name);
   long GetImageBufferSize() const {return img_.Width() * img_.Height() * GetImageBytesPerPixel();}
   unsigned GetBitDepth() const;
   int GetBinning() const;
   int SetBinning(int binSize);
   double GetExposure() const;
   void SetExposure(double dExp);
   int SetROI(unsigned uX, unsigned uY, unsigned uXSize, unsigned uYSize); 
   int GetROI(unsigned& uX, unsigned& uY, unsigned& uXSize, unsigned& uYSize);
   int ClearROI();
   int IsExposureSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}

   // action interface
   int OnBrightness(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnBrightnessMode(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnIntegration(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnFrameRate(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnScanMode(MM::PropertyBase* /* pProp */, MM::ActionType /* eAct */);
   int OnTimeout(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnGain(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnGainMode(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnGamma(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnGammaMode(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnShutter(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnShutterMode(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTemperature(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnMode(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnExternalTrigger(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnHue(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnHueMode(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSaturation(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSaturationMode(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTemp(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTempMode(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnExposureMode(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnWhitebalanceMode(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnWhitebalanceUB(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnWhitebalanceVR(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnWhiteshadingMode(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnWhiteshadingRed(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnWhiteshadingBlue(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnWhiteshadingGreen(MM::PropertyBase* pProp, MM::ActionType eAct);
	
   // high-speed interface
   int StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow);
   int StartSequenceAcquisition(double interval_ms);
   int StopSequenceAcquisition();
   int PushImage(dc1394video_frame_t *myframe);

private:
   int InitFeatureModeProperty(const dc1394feature_info_t& featureInfo,
         bool& hasManualMode, // out param
         int (Cdc1394::*cb_onfeaturemode)(MM::PropertyBase*, MM::ActionType),
         const std::string& overridePropertyName = ""); // if not using the default feature name + "Setting"

   int InitManualFeatureProperty(const dc1394feature_info_t& featureInfo,
         uint32_t &value, uint32_t &valueMin, uint32_t &valueMax, // out params
         int (Cdc1394::*cb_onfeature)(MM::PropertyBase*, MM::ActionType),
         const std::string& overridePropertyName = ""); // if not using the default (feature name string)

   int StartCapture();
   int StopCapture();

   int SetManual(dc1394feature_t feature);
   int ShutdownImageBuffer();
   int SetUpCamera();
   int StartCamera();

   // Whether the camera is in color mode. NOT whether we return color images to Micro-Manager.
   bool IsColor() const;

   int SetVideoMode(dc1394video_mode_t newMode);

   bool Timeout(MM::MMTime startTime);

   int OnFeature(MM::PropertyBase* pProp, MM::ActionType eAct, uint32_t &value, int valueMin, int valueMax, dc1394feature_t feature);
   int OnFeatureMode(MM::PropertyBase* pProp, MM::ActionType eAct, dc1394feature_t feature);

   void rgb8ToMono8(uint8_t* dest, uint8_t* src, uint32_t width, uint32_t height); 
   void rgb8ToBGRA8(uint8_t* dest, uint8_t* src, uint32_t width, uint32_t height); 
   void rgb8AddToMono16(uint16_t* dest, uint8_t* src, uint32_t width, uint32_t height); 
   void mono8AddToMono16(uint16_t* dest, uint8_t* src, uint32_t width, uint32_t height);

   void avtDeinterlaceMono8(uint8_t* dest, uint8_t* src, uint32_t outputWidth, uint32_t outputHeight);
   void avtDeinterlaceMono16(uint16_t* dest, uint16_t* src, uint32_t outputWidth, uint32_t outputHeight);
   
   bool InArray(dc1394framerate_t *array, int size, uint32_t num);
   int GetBytesPerPixel() const;
   double X700Shutter2Exposure(int shutter) const;
   int X700Exposure2Shutter(double exposure);

   // Property values for video mode
   static const std::map<dc1394video_mode_t, std::string>& MakeVideoModeMap();
   static std::string StringForVideoMode(dc1394video_mode_t mode);
   static dc1394video_mode_t VideoModeForString(const std::string& str);
   // Property values for framerate
   static const std::map<dc1394framerate_t, std::string>& MakeFramerateMap();
   static std::string StringForFramerate(dc1394framerate_t framerate);
   static dc1394framerate_t FramerateForString(const std::string& str);

   // Reference-counted dc1394 library context
   DC1394Context dc1394Context_;

   // The main dc1394 camera object; released in Shutdown() (or dtor)
   // Nonzero iff connected to camera
   dc1394camera_t *camera_;

   // A dc1394 DMA frame buffer; memory is managed by dc1394 library
   dc1394video_frame_t *frame_;

   // Current video mode
   dc1394video_mode_t mode_;

   // GJ keep track of whether the camera is interlaced
   bool avtInterlaced_;
   bool isSonyXCDX700_;

   // GJ will store whether we have absolute shutter control
   // (using a float value in seconds)
   bool absoluteShutterControl_;
   
   dc1394color_coding_t colorCoding_;
   dc1394framerates_t framerates_;
   dc1394framerate_t framerate_;
   uint32_t width_, height_, depth_;
   uint32_t brightness_, brightnessMin_, brightnessMax_;
   uint32_t gain_, gainMin_, gainMax_;
   uint32_t shutter_, shutterMin_, shutterMax_;
   uint32_t exposure_, exposureMin_, exposureMax_;
   uint32_t hue_, hueMin_, hueMax_;
   uint32_t saturation_, saturationMin_, saturationMax_;
   uint32_t gamma_, gammaMin_, gammaMax_;
   uint32_t temperature_, temperatureMin_, temperatureMax_;
	
   // for color settings
   enum colorAdjustment { COLOR_UB, COLOR_VR, COLOR_RED, COLOR_GREEN, COLOR_BLUE };
   int OnColorFeature(MM::PropertyBase* pProp, MM::ActionType eAct, uint32_t &value, int valueMin, int valueMax, colorAdjustment valueColor);
   uint32_t colub_, colvr_;
   uint32_t colred_, colblue_, colgreen_;
   uint32_t colMin_, colMax_;
	
   ImgBuffer img_;
   static const int dmaBufferSize_ = 16;

   bool frameRatePropDefined_;
   int integrateFrameNumber_;

   std::ostringstream logMsg_;
   MM::MMTime longestWait_;

   bool dequeued_; // indicates whether or not the current frame is dequeued
   
   // For sequence acquisition
   bool stopOnOverflow_;
   bool multi_shot_;
   bool acquiring_;
   unsigned long imageCounter_;
   unsigned long sequenceLength_;

   AcqSequenceThread* acqThread_; // burst mode thread
};


class AcqSequenceThread : private MMDeviceThreadBase
{
public:
   AcqSequenceThread(Cdc1394* pCam) : 
      camera_(pCam), intervalMs_(100.0), numImages_(1), stop_(true) {}
   ~AcqSequenceThread() { Stop(); }
   int svc(void);

   void SetInterval(double intervalMs) {intervalMs_ = intervalMs;}
   void SetLength(long images) {numImages_ = images;}

   void Stop() { if (!stop_) { stop_ = true; wait(); } }
   void Start();

private:
   Cdc1394* camera_;
   double intervalMs_;
   long numImages_;
   bool stop_;
};

#endif //_DC1394_H_
