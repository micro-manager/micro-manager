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
#include <dc1394/control.h>
#include <string>
#include <map>

// error codes
#define ERR_BUFFER_ALLOCATION_FAILED 101
#define ERR_INCOMPLETE_SNAP_IMAGE_CYCLE 102
#define ERR_CAMERA_NOT_FOUND 103
#define ERR_SET_CAPTURE_FAILED 104
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
#define ERR_MODE_NOT_AVAILABLE 116
#define ERR_SET_FRAMERATE_FAILED 117
#define ERR_GET_FRAMERATES_FAILED 118
#define ERR_INITIALIZATION_FAILED 119
#define ERR_UNSUPPORTED_COLOR_CODING 120


//////////////////////////////////////////////////////////////////////////////
// Implementation of the MMDevice and MMCamera interfaces
//
class Cdc1394 : public CCameraBase<Cdc1394>
{
public:
   static Cdc1394* GetInstance();
   ~Cdc1394();
   
   // MMDevice API
   int Initialize();
   int Shutdown();
   void GetName(char* pszName) const;
   bool Busy() {return m_bBusy;}
   
   // MMCamera API
   int SnapImage();
   const unsigned char* GetImageBuffer();
   unsigned GetImageWidth() const {return img_.Width();}
   unsigned GetImageHeight() const {return img_.Height();}
   unsigned GetImageBytesPerPixel() const {return img_.Depth();} 
   long GetImageBufferSize() const {return img_.Width() * img_.Height() * GetImageBytesPerPixel();}
   unsigned GetBitDepth() const;
   double GetExposure() const;
   void SetExposure(double dExp);
   int SetROI(unsigned uX, unsigned uY, unsigned uXSize, unsigned uYSize); 
   int GetROI(unsigned& uX, unsigned& uY, unsigned& uXSize, unsigned& uYSize);
   int ClearROI();

   // action interface
   int OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnBrightness(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnIntegration(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnFrameRate(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnScanMode(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnReadoutTime(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnGain(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnGamma(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnShutter(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTemperature(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnMode(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   Cdc1394();
   int ResizeImageBuffer();
   int SetManual(dc1394feature_t feature);
   int ShutdownImageBuffer();
   int GetCamera();
   int StartCamera();
   bool IsFeatureSupported(int featureId);
   int SetUpFrameRates();
   int StopTransmission();
   void setVideoModeMap();
   void setFrameRateMap();
   int OnFeature(MM::PropertyBase* pProp, MM::ActionType eAct, uint32_t &value, int valueMin, int valueMax, dc1394feature_t feature);
   //int AddFeature(dc1394feature_t feature, const char* label, int(Cdc1394::*fpt)(PropertyBase* pProp, ActionType eAct) , uint32_t  &value, uint32_t &valueMin, uint32_t &valueMax);
   void rgb8ToMono8(uint8_t* dest, uint8_t* src, uint32_t width, uint32_t height); 
   void rgb8AddToMono16(uint16_t* dest, uint8_t* src, uint32_t width, uint32_t height); 
   void mono8AddToMono16(uint16_t* dest, uint8_t* src, uint32_t width, uint32_t height);
   bool InArray(dc1394framerate_t *array, int size, uint32_t num);
   void GetBytesPerPixel();

   // video mode information
   std::map<dc1394video_mode_t, std::string> videoModeMap;
   typedef std::map<dc1394video_mode_t, std::string>::value_type videoModeMapType;
   // FrameRate information
   std::map<dc1394framerate_t, std::string> frameRateMap;
   typedef std::map<dc1394framerate_t, std::string>::value_type frameRateMapType;

   dc1394camera_t **cameras;                                             
   dc1394camera_t *camera;
   dc1394error_t err;                                                         
   dc1394video_frame_t *frame;                                               
   dc1394video_modes_t modes;                                               
   // current mode
   dc1394video_mode_t mode;                                               
   dc1394color_coding_t colorCoding;
   dc1394featureset_t features;
   dc1394feature_info_t featureInfo;
   dc1394framerates_t framerates;
   dc1394framerate_t framerate;
   uint32_t numCameras, width, height, depth;
   uint32_t brightness, brightnessMin, brightnessMax;
   uint32_t gain, gainMin, gainMax;
   uint32_t shutter, shutterMin, shutterMax;
   uint32_t exposure, exposureMin, exposureMax;
   int dmaBufferSize, triedCaptureCount, bytesPerPixel, integrateFrameNumber;
   int maxNrIntegration;
   
   static Cdc1394* m_pInstance;
   static unsigned refCount_;
   ImgBuffer img_;
   bool m_bBusy;
   bool m_bInitialized;
   bool snapInProgress_;
   bool frameRatePropDefined_;
   long lnBin_;
   char logMsg_[256];

};


#endif //_DC1394_H_
