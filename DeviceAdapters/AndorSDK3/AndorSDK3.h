///////////////////////////////////////////////////////////////////////////////
// FILE:          AndorSDK3Camera.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   The example implementation of the demo camera.
//                Simulates generic digital camera and associated automated
//                microscope devices and enables testing of the rest of the
//                system without the need to connect to the actual hardware. 
//                
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 06/08/2005
//                
//                Karl Hoover (stuff such as programmable CCD size and transpose processor)
//
// COPYRIGHT:     University of California, San Francisco, 2006
//                100X Imaging Inc, 2008
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
// CVS:           $Id: AndorSDK3Camera.h 6793 2011-03-28 19:10:30Z karlh $
//

#ifndef _ANDORSDK3_H_
#define _ANDORSDK3_H_

#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/ImgBuffer.h"
#include "../../MMDevice/DeviceThreads.h"
#include <string>
#include <map>
#include "SnapShotControl.h"
#include "EnumProperty.h"
#include "IntegerProperty.h"
#include "FloatProperty.h"
#include "AOIProperty.h"
#include "BooleanProperty.h"
#include "atcore++.h"
#include "triggerremapper.h"

using namespace andor;

#define NO_CIRCLE_BUFFER_FRAMES  10

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_UNKNOWN_MODE         102
#define ERR_UNKNOWN_POSITION     103
#define ERR_IN_SEQUENCE          104
#define ERR_SEQUENCE_INACTIVE    105


//////////////////////////////////////////////////////////////////////////////
// CAndorSDK3Camera class
//////////////////////////////////////////////////////////////////////////////

class MySequenceThread;
class TAOIProperty;

class CAndorSDK3Camera : public CCameraBase<CAndorSDK3Camera>  
{
public:
   CAndorSDK3Camera();
   ~CAndorSDK3Camera();
  
   // MMDevice API
   // ------------
   int Initialize();
   int Shutdown();
  
   void GetName(char* name) const;      
   
   // MMCamera API
   // ------------
   int SnapImage();
   const unsigned char* GetImageBuffer();
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
   int PrepareSequenceAcqusition() {return DEVICE_OK;}
   int StartSequenceAcquisition(double interval);
   int StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow);
   int StopSequenceAcquisition();
   int InsertImage();
   int ThreadRun();
   bool IsCapturing();
   void OnThreadExiting() throw(); 
   double GetNominalPixelSizeUm() const {return nominalPixelSizeUm_;}
   double GetPixelSizeUm() const {return nominalPixelSizeUm_ * GetBinning();}
   int GetBinning() const;
   int SetBinning(int bS);
   int IsExposureSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}

   unsigned  GetNumberOfComponents() const { return nComponents_;};

   // action interface
   int OnTriggerMode(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnExposureTime(MM::PropertyBase* pProp, MM::ActionType eAct);
   MM::MMTime CurrentTime(void) { return GetCurrentMMTime(); };

private:
   int ResizeImageBuffer();

   static const double nominalPixelSizeUm_;

   ImgBuffer img_;
   bool busy_;
   bool stopOnOverFlow_;
   bool initialized_;
   double readoutUs_;
   MM::MMTime readoutStartTime_;
   int bitDepth_;
   unsigned roiX_;
   unsigned roiY_;
   MM::MMTime sequenceStartTime_;
   long imageCounter_;
   bool softwareTriggerMode_;
   double d_frameRate_;
   int no_of_devices_;
   bool keep_trying_;
   bool in_external_;
   double timeout_;

   unsigned char** image_buffers_;

   MMThreadLock* pDemoResourceLock_;
   MMThreadLock imgPixelsLock_;
   int nComponents_;
   void TestResourceLocking(const bool);
   friend class MySequenceThread;
   friend class TAOIProperty;
   MySequenceThread * thd_;
   SnapShotControl* snapShotController_;

   // Properties for the property browser
   TAOIProperty* aoi_property_;
   TEnumProperty* preAmpGain_property;
   TEnumProperty* electronicShutteringMode_property;
   TEnumProperty* temperatureControl_proptery;
   TEnumProperty* pixelReadoutRate_property;
   TEnumProperty* pixelEncoding_property;
   TIntegerProperty* accumulationLength_property;
   TFloatProperty* readTemperature_property;
   TEnumProperty* temperatureStatus_property;
   TBooleanProperty* sensorCooling_property;
   TBooleanProperty* overlap_property;
   TEnumProperty* triggerMode_property;
   TEnumProperty* fanSpeed_property;
   TBooleanProperty* spuriousNoiseFilter_property;
   TFloatProperty* exposureTime_property;
   TFloatProperty* frameRate_property;

   // atcore++ objects
   IDeviceManager* deviceManager;
   IDevice* systemDevice;
   IInteger* deviceCount;
   IDevice* cameraDevice;
   IInteger* imageSizeBytes;
   IEnum* cycleMode;
   IFloat* exposureTime;
   IBufferControl* bufferControl;
   ICommand* startAcquisitionCommand;
   ICommand* stopAcquisitionCommand;
   IEnum* triggerMode;
   ICommand* sendSoftwareTrigger;
   IEnum* pixelEncoding;
   IInteger* frameCount;
   IFloat* frameRate;

   // Objects used by the properties
   IEnum* preAmpGain_Enum;
   IEnum* electronicShutteringMode_Enum;
   IEnum* temperatureControl_Enum;
   IEnum* pixelReadoutRate_Enum;
   IEnum* pixelEncoding_Enum;
   IEnum* temperatureStatus_Enum;
   IEnum* fanSpeed_Enum;
   IEnum* triggerMode_Enum;
   IFloat* readTemperature_Float;
   IFloat* exposureTime_Float;
   TAndorFloatValueMapper* exposureTime_valueMapper;
   IFloat* frameRate_Float;
   TAndorFloatHolder* frameRate_floatHolder;
   TAndorEnumValueMapper* triggerMode_valueMapper;
   TTriggerRemapper* triggerMode_remapper;
   IInteger* accumulationLength_Integer;
   IBool* spuriousNoiseFilter_Boolean;
   IBool* sensorCooling_Boolean;
   IBool* overlap_Boolean;
};

class MySequenceThread : public MMDeviceThreadBase
{
   friend class CAndorSDK3Camera;
   enum { default_numImages=1, default_intervalMS = 100 };
   public:
      MySequenceThread(CAndorSDK3Camera* pCam);
      ~MySequenceThread();
      void Stop();
      void Start(long numImages, double intervalMs);
      bool IsStopped();
      void Suspend();
      bool IsSuspended();
      void Resume();
      double GetIntervalMs(){return intervalMs_;}                               
      void SetLength(long images) {numImages_ = images;}                        
      long GetLength() const {return numImages_;}
      long GetImageCounter(){return imageCounter_;}                             
      MM::MMTime GetStartTime(){return startTime_;}                             
      MM::MMTime GetActualDuration(){return actualDuration_;}
   private:                                                                     
      int svc(void) throw();
      CAndorSDK3Camera* camera_;                                                     
      bool stop_;                                                               
      bool suspend_;                                                            
      long numImages_;                                                          
      long imageCounter_;                                                       
      double intervalMs_;                                                       
      MM::MMTime startTime_;                                                    
      MM::MMTime actualDuration_;                                               
      MM::MMTime lastFrameTime_;                                                
      MMThreadLock stopLock_;                                                   
      MMThreadLock suspendLock_;                                                
};

#endif //_ANDORSDK3_H_
