///////////////////////////////////////////////////////////////////////////////
// FILE:          IDS_uEye.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Driver for IDS uEye series of USB cameras
//                (also Thorlabs DCUxxxx USB, Edmund EO-xxxxM USB
//                 which are produced by IDS)
//
//                based on IDS uEye SDK and Micromanager DemoCamera example
//                tested with SDK version 3.82, 4.02, 4.20 and 4.30
//                (3.82-specific functions are still present but not used)
//                
// AUTHOR:        Wenjamin Rosenfeld
//
// YEAR:          2012 - 2014
//                
// VERSION:       1.1.1
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
//LAST UPDATE:    22.01.2014 WR



#ifndef _IDS_uEYE_H_
#define _IDS_uEYE_H_

#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/ImgBuffer.h"
#include "../../MMDevice/DeviceThreads.h"
#include <string>
#include <map>
#include <algorithm>

#ifdef _MSC_VER
// Disable Visual C++ warnings (caused by const fields in a struct)
#pragma warning(push)
#pragma warning(disable: 4510)
#pragma warning(disable: 4512)
#pragma warning(disable: 4610)
#endif // _MSC_VER

#include <uEye.h>

#ifdef _MSC_VER
#pragma warning(pop)
#endif // _MSC_VER



using namespace std;


//////////////////////////////////////////////////////////////////////////////
// General definitions
//

#define EXPOSURE_MAX 10000               //maximal exposure (ms) to use, even if the camera reports higher values


//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_UNKNOWN_MODE         102

#define ERR_CAMERA_NOT_FOUND     103
#define ERR_MEM_ALLOC            105
#define ERR_ROI_INVALID          110


const char* Err_UNKNOWN_MODE = "Unknown mode";
const char* Err_CAMERA_NOT_FOUND = "uEye camera not found";
const char* Err_MEM_ALLOC = "Could not allocate image memory";
const char* Err_ROI_INVALID = "Invalid ROI size";




////////////////////////////////////////////////////////////////////////////
// Binning mode names
//
#define NUM_BIN_MODES 4
string binModeString[]={"1x1","1x2","2x1","2x2"};


//////////////////////////////////////////////////////////////////////////////
// Sensor data which can not be directly read out
// based on definitions from uEye.h and datasheets from IDS
// defines the ADC bit per pixel, real bit per pixel and pixel pitch in um
//


#define IS_SENSOR_MAX_ID            0x0200      //defines the size of sensor data arrays

int IS_SENSOR_ADC_BPP[IS_SENSOR_MAX_ID];        //bit depth of the ADC
int IS_SENSOR_REAL_BPP[IS_SENSOR_MAX_ID];       //effective bit depth of the data
double IS_SENSOR_PITCH[IS_SENSOR_MAX_ID];       //pixel pitch (um) (not needed since v. 4.0)


void initializeSensorData(){

  int i;

  for(i=0; i<IS_SENSOR_MAX_ID;i++){
    IS_SENSOR_ADC_BPP[i]=0;  
    IS_SENSOR_REAL_BPP[i]=0;
    IS_SENSOR_PITCH[i]=0;
  }


  // CMOS Sensors

  //VGA rolling shutter, monochrome
  IS_SENSOR_ADC_BPP[IS_SENSOR_UI154X_M]=        10;
  IS_SENSOR_REAL_BPP[IS_SENSOR_UI154X_M]=       8;
  IS_SENSOR_PITCH[IS_SENSOR_UI154X_M]=          5.20;


  // 5MP rolling shutter, monochrome
  IS_SENSOR_ADC_BPP[IS_SENSOR_UI148X_M]=        12;
  IS_SENSOR_REAL_BPP[IS_SENSOR_UI148X_M]=       8;
  IS_SENSOR_PITCH[IS_SENSOR_UI148X_M]=          2.20;


  // WVGA global shutter, monochrome
  IS_SENSOR_ADC_BPP[IS_SENSOR_UI122X_M]=        10;
  IS_SENSOR_REAL_BPP[ IS_SENSOR_UI122X_M]=      8;
  IS_SENSOR_PITCH[IS_SENSOR_UI122X_M]=          6.0;


  // 10MP rolling shutter, monochrome
  IS_SENSOR_ADC_BPP[IS_SENSOR_UI149X_M]=        12;
  IS_SENSOR_REAL_BPP[IS_SENSOR_UI149X_M]=       8;
  IS_SENSOR_PITCH[IS_SENSOR_UI149X_M]=          1.67;


  // 0768x576, HDR sensor, monochrome
  IS_SENSOR_ADC_BPP[IS_SENSOR_UI112X_M]=        12;
  IS_SENSOR_REAL_BPP[IS_SENSOR_UI112X_M]=       8;
  IS_SENSOR_PITCH[IS_SENSOR_UI112X_M]=          10.0;


  // SXGA global shutter, monochrome
  IS_SENSOR_ADC_BPP[IS_SENSOR_UI1240_M]=        10;
  IS_SENSOR_REAL_BPP[IS_SENSOR_UI1240_M]=       8;
  IS_SENSOR_PITCH[IS_SENSOR_UI1240_M]=          5.30;


  // SXGA global shutter, monochrome, single board
  IS_SENSOR_ADC_BPP[IS_SENSOR_UI1240LE_M]=      10;     
  IS_SENSOR_REAL_BPP[IS_SENSOR_UI1240LE_M]=     8;
  IS_SENSOR_PITCH[IS_SENSOR_UI1240LE_M]=        5.30;



  // CCD Sensors

  // Sony CCD sensor - XGA monochrome (Thorlabs part# DCU223M)
  IS_SENSOR_ADC_BPP[IS_SENSOR_UI223X_M]=        12;
  IS_SENSOR_REAL_BPP[IS_SENSOR_UI223X_M]=       8;
  IS_SENSOR_PITCH[IS_SENSOR_UI223X_M]=          4.65;


  // Sony CCD sensor - VGA monochrome
  IS_SENSOR_ADC_BPP[IS_SENSOR_UI241X_M]=        12;
  IS_SENSOR_REAL_BPP[IS_SENSOR_UI241X_M]=       12;
  IS_SENSOR_PITCH[IS_SENSOR_UI241X_M]=          7.40;


  // Sony CCD sensor - VGA monochrome
  IS_SENSOR_ADC_BPP[IS_SENSOR_UI221X_M]=        12;
  IS_SENSOR_REAL_BPP[IS_SENSOR_UI221X_M]=       12;
  IS_SENSOR_PITCH[IS_SENSOR_UI221X_M]=          9.90;


  // Sony CCD sensor - CCIR / PAL monochrome
  IS_SENSOR_ADC_BPP[IS_SENSOR_UI222X_M]=        12;
  IS_SENSOR_REAL_BPP[IS_SENSOR_UI222X_M]=       12;
  IS_SENSOR_PITCH[IS_SENSOR_UI222X_M]=          8.30;


  // Sony CCD sensor - SXGA monochrome (Thorlabs part# DCU224M)
  IS_SENSOR_ADC_BPP[IS_SENSOR_UI224X_M]=        12;
  IS_SENSOR_REAL_BPP[IS_SENSOR_UI224X_M]=       8;
  IS_SENSOR_PITCH[IS_SENSOR_UI224X_M]=          4.65; 
 
  // Sony CCD sensor - UXGA monochrome
  IS_SENSOR_ADC_BPP[IS_SENSOR_UI225X_M]=        12;
  IS_SENSOR_REAL_BPP[IS_SENSOR_UI225X_M]=       12;
  IS_SENSOR_PITCH[IS_SENSOR_UI225X_M]=          4.40;


  // Sony CCD sensor - SXGA monochrome    
  IS_SENSOR_ADC_BPP[IS_SENSOR_UI214X_M]=        14;
  IS_SENSOR_REAL_BPP[IS_SENSOR_UI214X_M]=       12;
  IS_SENSOR_PITCH[IS_SENSOR_UI214X_M]=          3.75;  


  // Sony CCD sensor - QXGA monochrome
  IS_SENSOR_ADC_BPP[IS_SENSOR_UI228X_M]=        14;
  IS_SENSOR_REAL_BPP[IS_SENSOR_UI228X_M]=       12;
  IS_SENSOR_PITCH[IS_SENSOR_UI228X_M]=          3.45; 

  


  /*
  IS_SENSOR_ADC_BPP[]=        12; 
  IS_SENSOR_REAL_BPP[]=       8;
  IS_SENSOR_PITCH[]=          4.65; 
  */




}

/*
//all sensors (those who are in database are commented out)


// CMOS Sensors
#define IS_SENSOR_UI141X_M          0x0001      // VGA rolling shutter, monochrome
#define IS_SENSOR_UI141X_C          0x0002      // VGA rolling shutter, color
#define IS_SENSOR_UI144X_M          0x0003      // SXGA rolling shutter, monochrome
#define IS_SENSOR_UI144X_C          0x0004      // SXGA rolling shutter, SXGA color

//#define IS_SENSOR_UI154X_M          0x0030      // SXGA rolling shutter, monochrome
#define IS_SENSOR_UI154X_C          0x0031      // SXGA rolling shutter, color
#define IS_SENSOR_UI145X_C          0x0008      // UXGA rolling shutter, color

#define IS_SENSOR_UI146X_C          0x000a      // QXGA rolling shutter, color
//#define IS_SENSOR_UI148X_M          0x000b      // 5MP rolling shutter, monochrome
#define IS_SENSOR_UI148X_C          0x000c      // 5MP rolling shutter, color

#define IS_SENSOR_UI121X_M          0x0010      // VGA global shutter, monochrome
#define IS_SENSOR_UI121X_C          0x0011      // VGA global shutter, VGA color
//#define IS_SENSOR_UI122X_M          0x0012      // WVGA global shutter, monochrome
#define IS_SENSOR_UI122X_C          0x0013      // WVGA global shutter, color

#define IS_SENSOR_UI164X_C          0x0015      // SXGA rolling shutter, color

#define IS_SENSOR_UI155X_C          0x0017      // UXGA rolling shutter, color

#define IS_SENSOR_UI1223_M          0x0018      // WVGA global shutter, monochrome
#define IS_SENSOR_UI1223_C          0x0019      // WVGA global shutter, color

//#define IS_SENSOR_UI149X_M          0x003E      // 10MP rolling shutter, monochrome
#define IS_SENSOR_UI149X_C          0x003F      // 10MP rolling shutter, color

// LE models with xxx5
#define IS_SENSOR_UI1225_M          0x0022      // WVGA global shutter, monochrome, LE model
#define IS_SENSOR_UI1225_C          0x0023      // WVGA global shutter, color, LE model

#define IS_SENSOR_UI1645_C          0x0025      // SXGA rolling shutter, color, LE model
#define IS_SENSOR_UI1555_C          0x0027      // UXGA rolling shutter, color, LE model
#define IS_SENSOR_UI1545_M          0x0028      // SXGA rolling shutter, monochrome, LE model
#define IS_SENSOR_UI1545_C          0x0029      // SXGA rolling shutter, color, LE model
#define IS_SENSOR_UI1455_C          0x002B      // UXGA rolling shutter, color, LE model
#define IS_SENSOR_UI1465_C          0x002D      // QXGA rolling shutter, color, LE model
#define IS_SENSOR_UI1485_M          0x002E      // 5MP rolling shutter, monochrome, LE model
#define IS_SENSOR_UI1485_C          0x002F      // 5MP rolling shutter, color, LE model
#define IS_SENSOR_UI1495_M          0x0040      // 10MP rolling shutter, monochrome, LE model
#define IS_SENSOR_UI1495_C          0x0041      // 10MP rolling shutter, color, LE model

//#define IS_SENSOR_UI112X_M          0x004A      // 0768x576, HDR sensor, monochrome
#define IS_SENSOR_UI112X_C          0x004B      // 0768x576, HDR sensor, color

#define IS_SENSOR_UI1008_M          0x004C
#define IS_SENSOR_UI1008_C          0x004D

//#define IS_SENSOR_UI1240_M          0x0050      // SXGA global shutter, monochrome
#define IS_SENSOR_UI1240_C          0x0051      // SXGA global shutter, color
//#define IS_SENSOR_UI1240LE_M        0x0054      // SXGA global shutter, monochrome, single board
#define IS_SENSOR_UI1240LE_C        0x0055      // SXGA global shutter, color, single board

// custom board level designs
#define IS_SENSOR_UI1543_M          0x0032      // SXGA rolling shutter, monochrome, single board
#define IS_SENSOR_UI1543_C          0x0033      // SXGA rolling shutter, color, single board

#define IS_SENSOR_UI1544_M          0x003A      // SXGA rolling shutter, monochrome, single board
#define IS_SENSOR_UI1544_C          0x003B      // SXGA rolling shutter, color, single board
#define IS_SENSOR_UI1543_M_WO       0x003C      // SXGA rolling shutter, monochrome, single board
#define IS_SENSOR_UI1543_C_WO       0x003D      // SXGA rolling shutter, color, single board
#define IS_SENSOR_UI1453_C          0x0035      // UXGA rolling shutter, color, single board
#define IS_SENSOR_UI1463_C          0x0037      // QXGA rolling shutter, color, single board
#define IS_SENSOR_UI1483_M          0x0038      // QSXG rolling shutter, monochrome, single board
#define IS_SENSOR_UI1483_C          0x0039      // QSXG rolling shutter, color, single board
#define IS_SENSOR_UI1493_M          0x004E      // 10Mp rolling shutter, monochrome, single board
#define IS_SENSOR_UI1493_C          0x004F      // 10MP rolling shutter, color, single board

#define IS_SENSOR_UI1463_M_WO       0x0044      // QXGA rolling shutter, monochrome, single board
#define IS_SENSOR_UI1463_C_WO       0x0045      // QXGA rolling shutter, color, single board

#define IS_SENSOR_UI1553_C_WN       0x0047      // UXGA rolling shutter, color, single board
#define IS_SENSOR_UI1483_M_WO       0x0048      // QSXGA rolling shutter, monochrome, single board
#define IS_SENSOR_UI1483_C_WO       0x0049      // QSXGA rolling shutter, color, single board

#define IS_SENSOR_UI1580_M          0x005A      // 5MP rolling shutter, monochrome
#define IS_SENSOR_UI1580_C          0x005B      // 5MP rolling shutter, color
#define IS_SENSOR_UI1580LE_M        0x0060      // 5MP rolling shutter, monochrome, single board
#define IS_SENSOR_UI1580LE_C        0x0061      // 5MP rolling shutter, color, single board

// CCD Sensors
//#define IS_SENSOR_UI223X_M          0x0080      // Sony CCD sensor - XGA monochrome
#define IS_SENSOR_UI223X_C          0x0081      // Sony CCD sensor - XGA color

//#define IS_SENSOR_UI241X_M          0x0082      // Sony CCD sensor - VGA monochrome
#define IS_SENSOR_UI241X_C          0x0083      // Sony CCD sensor - VGA color

#define IS_SENSOR_UI234X_M          0x0084      // Sony CCD sensor - SXGA monochrome
#define IS_SENSOR_UI234X_C          0x0085      // Sony CCD sensor - SXGA color

//#define IS_SENSOR_UI221X_M          0x0088      // Sony CCD sensor - VGA monochrome
#define IS_SENSOR_UI221X_C          0x0089      // Sony CCD sensor - VGA color

#define IS_SENSOR_UI231X_M          0x0090      // Sony CCD sensor - VGA monochrome
#define IS_SENSOR_UI231X_C          0x0091      // Sony CCD sensor - VGA color

//#define IS_SENSOR_UI222X_M          0x0092      // Sony CCD sensor - CCIR / PAL monochrome
#define IS_SENSOR_UI222X_C          0x0093      // Sony CCD sensor - CCIR / PAL color

//#define IS_SENSOR_UI224X_M          0x0096      // Sony CCD sensor - SXGA monochrome
#define IS_SENSOR_UI224X_C          0x0097      // Sony CCD sensor - SXGA color

//#define IS_SENSOR_UI225X_M          0x0098      // Sony CCD sensor - UXGA monochrome
#define IS_SENSOR_UI225X_C          0x0099      // Sony CCD sensor - UXGA color

//#define IS_SENSOR_UI214X_M          0x009A      // Sony CCD sensor - SXGA monochrome
#define IS_SENSOR_UI214X_C          0x009B      // Sony CCD sensor - SXGA color

//#define IS_SENSOR_UI228X_M          0x009C      // Sony CCD sensor - QXGA monochrome
#define IS_SENSOR_UI228X_C          0x009D      // Sony CCD sensor - QXGA color

#define IS_SENSOR_UI241X_M_R2       0x0182      // Sony CCD sensor - VGA monochrome
#define IS_SENSOR_UI251X_M          0x0182      // Sony CCD sensor - VGA monochrome
#define IS_SENSOR_UI241X_C_R2       0x0183      // Sony CCD sensor - VGA color
#define IS_SENSOR_UI251X_C          0x0183      // Sony CCD sensor - VGA color

#define IS_SENSOR_UI2130_M          0x019E      // Sony CCD sensor - WXGA monochrome
#define IS_SENSOR_UI2130_C          0x019F      // Sony CCD sensor - WXGA color



*/



//////////////////////////////////////////////////////////////////////////////
// CIDS_uEye class
//
//////////////////////////////////////////////////////////////////////////////

class MySequenceThread;

class CIDS_uEye : public CCameraBase<CIDS_uEye>  
{
  
 public:

  CIDS_uEye();                                           //constructor
  ~CIDS_uEye();                                          //destructor
  

  // uEye interface
  // ------------
  HIDS hCam;                                            //handle for the camera
  char* pcImgMem;                                       //image memory
  int memPid;                                           //ID for image memory


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
  
  int PrepareSequenceAcqusition()
  {
    return DEVICE_OK;
  }

  int StartSequenceAcquisition(double interval);
  int StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow);
  int StopSequenceAcquisition();
  int InsertImage();
  int ThreadRun();
  bool IsCapturing();
  void OnThreadExiting() throw(); 

  double GetNominalPixelSizeUm() const 
  {return nominalPixelSizeUm_;}

  double GetPixelSizeUm() const 
  {return nominalPixelSizeUm_ * GetBinning();}

  int GetBinning() const;
  int SetBinning(int bS);
  int IsExposureSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}

  unsigned  GetNumberOfComponents() const
  { return nComponents_;};


  //convenience functions
  void GetPixelClockRange(HIDS hCam, UINT* pixClkMin, UINT* pixClkMax);
  void SetPixelClock(UINT pixClk);
  void GetFramerateRange(HIDS hCam, double* fpsMin, double* fpsMax);
  void GetExposureRange(HIDS hCam, double* expMin, double* expMax, double* expIncrement);


  // action interface
  // ----------------

  // handlers for properties
  int OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnPixelClock(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnFramerate(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnReadoutTime(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnErrorSimulation(MM::PropertyBase* , MM::ActionType );
  int OnCameraCCDXSize(MM::PropertyBase* , MM::ActionType );
  int OnCameraCCDYSize(MM::PropertyBase* , MM::ActionType );
  int OnTriggerDevice(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnDropPixels(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnFractionOfPixelsToDropOrSaturate(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnGainMaster(MM::PropertyBase* pProp, MM::ActionType eAct);

 private:

  double nominalPixelSizeUm_;                                   //pixel pitch

  int bitDepthADC_;
  int bitDepthReal_;
  
  double dPhase_;
  ImgBuffer img_;
  bool busy_;
  bool stopOnOverflow_;
  bool initialized_;
  double readoutUs_;
  MM::MMTime readoutStartTime_;
  int bitDepth_;

  unsigned roiX_;                                               //x-position of the ROI corner
  unsigned roiY_;                                               //y-position of the ROI corner
  unsigned roiXSize_;                                           //width of the ROI
  unsigned roiYSize_;                                           //height of the ROI
  
  long cameraCCDXSize_;                                         //sensor width in pixels
  long cameraCCDYSize_;                                         //sensor height in pixels


  MM::MMTime sequenceStartTime_;
  long imageCounter_;
  double ccdT_;
  std::string triggerDevice_;

  UINT pixelClkMin_;                                            //minimal pixel clock (MHz)
  UINT pixelClkMax_;                                            //maximal pixel clock (MHz)
  UINT pixelClkCur_;                                            //current (real) pixel clock
  UINT pixelClkDef_;                                            //default pixel clock 

  double framerateMin_;                                         //minimal framerate
  double framerateMax_;                                         //maximal framerate
  double framerateCur_;                                         //current (real) framerate

  double exposureMin_;                                          //minimal allowed exposure
  double exposureMax_;                                          //maximal allowed exposure
  double exposureIncrement_;
  double exposureSet_;                                          //set (desired) exposure value
  double exposureCur_;                                          //current (real) exposure value

  long gainMaster_;                                             //master gain
  long gainRed_;
  long gainGreen_;
  long gainBlue_;

  long binX_;                                                   //horizontal binning factor
  long binY_;                                                   //vertical binning factor
  long binMode_;                                                //binning mode number (name defined in binModeString)
  

  bool dropPixels_;
  bool saturatePixels_;
  double fractionOfPixelsToDropOrSaturate_;
  
  double testProperty_[10];
  MMThreadLock* pDemoResourceLock_;
  MMThreadLock imgPixelsLock_;
  int nComponents_;
  friend class MySequenceThread;
  MySequenceThread * thd_;

  
  int SetAllowedBinning();
  void TestResourceLocking(const bool);
  void ClearImageBuffer(ImgBuffer& img);
  int ResizeImageBuffer();

  int SetImageMemory();

  int setSensorPixelParameters(WORD sensorID);
  



};

class MySequenceThread : public MMDeviceThreadBase
{

  friend class CIDS_uEye;
  enum { default_numImages=1, default_intervalMS = 100 };

 public:
  MySequenceThread(CIDS_uEye* pCam);
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
  CIDS_uEye* camera_;                                                     
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




#endif //_IDS_UEYE_H_
