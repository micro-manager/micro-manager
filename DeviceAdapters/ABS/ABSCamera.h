/////////////////////////////////////////////////////////////////////////////
// Name:        ABSCamera.h
// Purpose:     Definition der Kameraklasse als Adapter für µManager
// Author:      Michael Himmelreich
// Created:     31. Juli 2007
// Copyright:   (c) Michael Himmelreich
// Project:     ConfoVis
/////////////////////////////////////////////////////////////////////////////



#ifndef _ABSCAMERA_H_
#define _ABSCAMERA_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/DeviceUtils.h"
#include "../../MMDevice/ImgBuffer.h"
#include "camusb_api.h"

#include <string>
#include <map>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_UNKNOWN_MODE         102
#define ERR_UNKNOWN_POSITION     103
#define ERR_BUSY_ACQIRING        104

#define ABSCAM_CIRCULAR_BUFFER_IMG_COUNT  (2)


class ABSCamera : public CCameraBase<ABSCamera>  
{
public:
   ABSCamera(int iDeviceNumber, const char* szDeviceName);
   ~ABSCamera();
  
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
   unsigned int GetNumberOfComponents() const;
   int GetComponentName(unsigned channel, char* name);
         
   unsigned GetImageWidth() const;
   unsigned GetImageHeight() const;
   unsigned GetImageBytesPerPixel() const;
   unsigned GetBitDepth() const;
   long GetImageBufferSize() const;
   double GetExposure() const;
   void SetExposure(double exp);
   int SetROI( unsigned x, unsigned y, unsigned xSize, unsigned ySize ); 
   int GetROI( unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize ); 
   int ClearROI();
   
   virtual int StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow);
   virtual int StartSequenceAcquisition(double interval)
   {
     return StartSequenceAcquisition(LONG_MAX, interval, false);   
   }
   virtual int StopSequenceAcquisition();
   virtual int PrepareSequenceAcqusition();

   double GetNominalPixelSizeUm() const {return fNominalPixelSizeUm;}
   double GetPixelSizeUm() const {return fNominalPixelSizeUm * GetBinning();}
   
   int GetBinning() const;
   int SetBinning(int binSize);
    
   // action interface
   // ----------------
   int OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnGain(MM::PropertyBase* pProp, MM::ActionType eAct);

   int OnTemperature(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnColorMode(MM::PropertyBase* pProp, MM::ActionType eAct);
   
   int OnReadoutTime(MM::PropertyBase* /* pProp */, MM::ActionType /* eAct */) { return DEVICE_OK; };
   
   int OnFlipX(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnFlipY(MM::PropertyBase* pProp, MM::ActionType eAct);
private:  

    MM_THREAD_GUARD lockImgBufPtr;

    virtual int ThreadRun();
    virtual int InsertImage();

   int UpdateExposureLimits(void);
   int UpdatePixelTypes(void);
   //! set pixel type at camera an update the internal members "numberOfChannels" and call ResizeImageBuffer
   int SetPixelType(unsigned long dwPixelType);

   long GetFlip();
    void SetFlip(long nFlip);

   // property creation
   int createExposure(void);
   int createBinning(void);
   int createPixelType(void);
   int createGain(void);
   int createOffset(void);
   int createTemperature(void);
   int createActualInterval(void);
   int createColorMode(void);

   static const double fNominalPixelSizeUm;
   std::string m_szDeviceName;

   ImgBuffer imageBuffer;
   bool initialized;   

   unsigned char *cameraProvidedImageBuffer;
   unsigned char *cameraProvidedImageHdr;

   bool bSetGetExposureActive;   

   bool bColor;
   volatile bool bAbortGetImageCalled;
   unsigned int numberOfChannels;
   unsigned char deviceNumber;
   unsigned __int64 cameraFunctionMask;

   S_CAMERA_VERSION     camVersion;
   S_RESOLUTION_CAPS*   resolutionCap;
   S_FLIP_CAPS*         flipCap;
   S_PIXELTYPE_CAPS*    pixelTypeCap;
   S_GAIN_CAPS*         gainCap;
   S_EXPOSURE_CAPS*     exposureCap;
   S_AUTOEXPOSURE_CAPS* autoExposureCap;
   S_TEMPERATURE_CAPS*  temperatureCap;
   S_FRAMERATE_CAPS*    framerateCap;

   void GenerateSyntheticImage(ImgBuffer& img, double exp);
   int ResizeImageBuffer();
   void ShowError( unsigned int errorNumber ) const;
   void ShowError() const;
   void* GetCameraCap( unsigned __int64 CamFuncID )  const;   
   int GetCameraFunction( unsigned __int64 CamFuncID, void* functionPara, unsigned long size, void* functionParamOut = NULL, unsigned long sizeOut = 0 )  const;   
   int SetCameraFunction( unsigned __int64 CamFuncID, void* functionPara, unsigned long size ) const;
  
};

#endif //_ABSCAMERA_H_
