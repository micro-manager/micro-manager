 ///////////////////////////////////////////////////////////////////////////////
// FILE:          IDS_uEye.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Driver for IDS uEye series of USB cameras
//                (also Thorlabs DCUxxxx USB, Edmund EO-xxxxM USB
//                 with IDS hardware)
//
//                based on IDS uEye SDK and Micromanager DemoCamera example
//                tested with SDK version 3.82, 4.02, 4.20 and 4.30
//                (3.82-specific functions are still present but not used)
//                
// AUTHOR:        Wenjamin Rosenfeld
//
// YEAR:          2012 - 2014
//                
// VERSION:       1.3
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
//LAST UPDATE:    09.04.2014 WR



#include <cstdio>
#include <string>
#include <math.h>
#include <sstream>
#include <iostream>

#include "../../MMDevice/ModuleInterface.h"

#include "IDS_uEye.h"




//definitions
#define DRV_VERSION "1.3"                       //version of this device adapter
#define ACQ_TIMEOUT 1100                        //timeout for image acquisition (in 10ms) 




using namespace std;

double g_IntensityFactor_ = 1.0;

// External names used used by the rest of the system
// to load particular device from the "IDS_uEye.dll" library
const char* g_CameraDeviceName = "IDS uEye";


// constants for naming pixel types (allowed values of the "PixelType" property)
const char* g_PixelType_8bit =          "8bit mono";
const char* g_PixelType_10bit =         "10bit mono";
const char* g_PixelType_12bit =         "12bit mono";
const char* g_PixelType_16bit =         "16bit mono";
const char* g_PixelType_8bitRGB =       "8bit RGB";
const char* g_PixelType_8bitBGR =       "8bit BGR";
const char* g_PixelType_8bitRGBA =      "8bit RGBA";
const char* g_PixelType_8bitBGRA =      "8bit BGRA";    //directly corresponds to MM image buffer with 4 components
const char* g_PixelType_32bit =         "32bit";        // floating point greyscale


// constants for naming trigger modes (allowed values of the "Triger" property)
const char* g_TriggerType_OFF =         "off";
const char* g_TriggerType_HI_LO =       "ext. falling";
const char* g_TriggerType_LO_HI =       "ext. rising";
const char* g_TriggerType_SOFTWARE =    "internal";


///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////

MODULE_API void InitializeModuleData()
{
  RegisterDevice(g_CameraDeviceName, MM::CameraDevice, "uEye Camera");
}


MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
  if (deviceName == 0)
    return 0;

  // decide which device class to create based on the deviceName parameter
  if (strcmp(deviceName, g_CameraDeviceName) == 0){

    // create camera
    return new CIDS_uEye();
  }
  

  // ...supplied name not recognized
  return 0;
}


MODULE_API void DeleteDevice(MM::Device* pDevice)
{
  delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
// CIDS_uEye implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~

/**
* CIDS_uEye constructor.
* Setup default all variables and create device properties required to exist
* before intialization. In this case, no such properties were required. All
* properties will be created in the Initialize() method.
*
* As a general guideline Micro-Manager devices do not access hardware in the
* the constructor. We should do as little as possible in the constructor and
* perform most of the initialization in the Initialize() method.
*/
CIDS_uEye::CIDS_uEye() :
   CCameraBase<CIDS_uEye> (),
   dPhase_(0),
   initialized_(false),
   readoutUs_(0.0),
   bitDepth_(8),
   roiX_(0),
   roiY_(0),
   sequenceStartTime_(0),
   cameraCCDXSize_(512),
   cameraCCDYSize_(512),
   ccdT_ (0.0),
   nComponents_(1),
   pDemoResourceLock_(0),
   triggerDevice_(""),
   dropPixels_(false),
   fractionOfPixelsToDropOrSaturate_(0.002)
{
   memset(testProperty_,0,sizeof(testProperty_));


   //error messages
   SetErrorText(ERR_UNKNOWN_MODE, Err_UNKNOWN_MODE);
   SetErrorText(ERR_CAMERA_NOT_FOUND, Err_CAMERA_NOT_FOUND); 
   SetErrorText(ERR_MEM_ALLOC, Err_MEM_ALLOC);
   SetErrorText(ERR_ROI_INVALID, Err_ROI_INVALID);


   // call the base class method to set-up default error codes/messages
   InitializeDefaultErrorMessages();
   readoutStartTime_ = GetCurrentMMTime();
   pDemoResourceLock_ = new MMThreadLock();
   thd_ = new MySequenceThread(this);

   // parent ID display
   CreateHubIDProperty();
}


/**
* CIDS_uEye destructor.
* If this device used as intended within the Micro-Manager system,
* Shutdown() will be always called before the destructor. But in any case
* we need to make sure that all resources are properly released even if
* Shutdown() was not called.
*/
CIDS_uEye::~CIDS_uEye()
{
   StopSequenceAcquisition();
   delete thd_;
   delete pDemoResourceLock_;
}

/**
* Obtains device name.
* Required by the MM::Device API.
*/
void CIDS_uEye::GetName(char* name) const
{
   // Return the name used to refer to this device adapter
   CDeviceUtils::CopyLimitedString(name, g_CameraDeviceName);
}

/**
* Initializes the hardware.
* Required by the MM::Device API.
* Typically we access and initialize hardware at this point.
* Device properties are typically created here as well, except
* the ones we need to use for defining initialization parameters.
* Such pre-initialization properties are created in the constructor.
* (This device does not have any pre-initialization properties)
*/
int CIDS_uEye::Initialize()
{

  CPropertyAction *pAct;
  std::ostringstream oss;                                       //for output
  //char tempStr[256];


   if (initialized_)
      return DEVICE_OK;
	

    
   //Open camera with ID 1
   hCam = 1;
   INT nReturn = is_InitCamera (&hCam, NULL);
   
   if (nReturn != IS_SUCCESS){                          //could not open camera
     LogMessage("could not find a uEye camera",true);
     return ERR_CAMERA_NOT_FOUND;
   }


   //get camera info
   CAMINFO camInfo;
   nReturn=is_GetCameraInfo (hCam, &camInfo);
   if(nReturn != IS_SUCCESS){
     printf("could not obtain camera properties\n");
   }
   if(nReturn == IS_SUCCESS){

     //print properties
     //LogMessage("found a uEye camera with following properties",true);
     printf("Found a uEye camera with following properties:\n");
     printf("serial number: %s\n",camInfo.SerNo);
     printf("ID: %s\n",camInfo.ID);
     printf("Version: %s\n",camInfo.Version);
     printf("Date: %s\n",camInfo.Date);
     printf("Select: %uc\n",camInfo.Select);
     printf("Type: %uc\n",camInfo.Type);
     printf("Reserved: %s\n\n",camInfo.Reserved);
   
     //  oss <<" serial:" << camInfo.SerNo;
     // LogMessage(oss.str().c_str(), false);

   }

   
   //get sensor info
   SENSORINFO sensorInfo;
   nReturn=is_GetSensorInfo (hCam, &sensorInfo);
   if(nReturn != IS_SUCCESS){
     printf("could not obtain sensor properties\n");
   }
   if(nReturn == IS_SUCCESS){

     //analyze and print sensor info
     printf("sensor properties:\n");
     printf("sensor ID: %x\n",sensorInfo.SensorID);
     printf("sensor name: %s\n",sensorInfo.strSensorName);
     printf("width x height: %d x %d \n", sensorInfo.nMaxWidth, sensorInfo.nMaxHeight);
     printf("pixel pitch: %.3f um \n", sensorInfo.wPixelSize*0.01);
     switch(sensorInfo.nColorMode){
     case(IS_COLORMODE_MONOCHROME):
       printf("color mode: monochrome\n"); break;
     case(IS_COLORMODE_BAYER):
       printf("color mode: bayer\n"); break;
     case(IS_COLORMODE_CBYCRY):
       printf("color mode: CbYCrY\n"); break;  
     }
     /*
      switch(sensorInfo.nUpperLeftBayerPixel){
     case(0):
       printf("upper left bayer pixel: red\n"); break;
     case(1):
       printf("upper left bayer pixel: green\n"); break;
     case(2):
       printf("upper left bayer pixel: blue\n"); break;        
       }
     */


     cameraCCDXSize_=sensorInfo.nMaxWidth;
     cameraCCDYSize_=sensorInfo.nMaxHeight;
     nominalPixelSizeUm_=sensorInfo.wPixelSize*0.01;

     //  oss <<" serial:" << camInfo.SerNo;
     // LogMessage(oss.str().c_str(), false);

   }


   //define starting ROI as the full image size
   roiX_=0;
   roiY_=0;
   roiXSize_=cameraCCDXSize_;
   roiYSize_=cameraCCDYSize_;
   roiXSizeReal_=cameraCCDXSize_;
   roiYSizeReal_=cameraCCDYSize_;
  

   //get pixel clock range
   GetPixelClockRange(hCam, &pixelClkMin_, &pixelClkMax_);
   printf("pixel clock range: %d - %d MHz\n", pixelClkMin_, pixelClkMax_);


   //get initial frame rate range
   GetFramerateRange(hCam, &framerateMin_, &framerateMax_);
   printf("frame rate range %.2f - %.2f\n",framerateMin_,framerateMax_);


   //get initial exposure range
   GetExposureRange(hCam, &exposureMin_, &exposureMax_, &exposureIncrement_);
   if(exposureMax_>EXPOSURE_MAX) exposureMax_=EXPOSURE_MAX;                            //limit exposure time to keep the interface responsive
   printf("Exposure (%.3f, %.3f) ms, increment %.4f ms\n", exposureMin_, exposureMax_, exposureIncrement_);



   //get initial gain
   gainMaster_=is_SetHardwareGain (hCam, IS_GET_MASTER_GAIN, 0, 0, 0);
   gainRed_=   is_SetHardwareGain (hCam, IS_GET_RED_GAIN, 0, 0, 0);
   gainGreen_= is_SetHardwareGain (hCam, IS_GET_GREEN_GAIN, 0, 0, 0);
   gainBlue_=  is_SetHardwareGain (hCam, IS_GET_BLUE_GAIN, 0, 0, 0); 
   printf("gains: master: %ld, red: %ld, green: %ld, blue: %ld\n",gainMaster_, gainRed_, gainGreen_, gainBlue_);



   // set property list
   // -----------------

   // Name (read-only)
   int nRet = CreateProperty(MM::g_Keyword_Name, g_CameraDeviceName, MM::String, true);
   if (DEVICE_OK != nRet)
      return nRet;

   // Description (read-only)
   string descr=std::string("IDS uEye Adapter v. ") + DRV_VERSION;
   nRet = CreateProperty(MM::g_Keyword_Description, descr.c_str(), MM::String, true);
   if (DEVICE_OK != nRet)
      return nRet;

   // CameraName (read-only)
   nRet = CreateProperty(MM::g_Keyword_CameraName, "IDS uEye", MM::String, true);
   assert(nRet == DEVICE_OK);

   // CameraID (read-only)
   nRet = CreateProperty(MM::g_Keyword_CameraID, camInfo.ID, MM::String, true); 
   assert(nRet == DEVICE_OK);

   // sensor name (read-only)
   nRet = CreateProperty("Sensor name", sensorInfo.strSensorName , MM::String, true);
   assert(nRet == DEVICE_OK);

   // sensor size (read-only)
   pAct = new CPropertyAction (this, &CIDS_uEye::OnCameraCCDXSize);
   CreateProperty("Sensor XSize",CDeviceUtils::ConvertToString((int)sensorInfo.nMaxWidth), MM::Integer, true, pAct);

   pAct = new CPropertyAction (this, &CIDS_uEye::OnCameraCCDYSize);
   CreateProperty("Sensor YSize",CDeviceUtils::ConvertToString((int)sensorInfo.nMaxHeight), MM::Integer, true, pAct);


   // pixel size (read-only)
   nRet = CreateProperty("Sensor pixel size",CDeviceUtils::ConvertToString(nominalPixelSizeUm_) , MM::Float, true);
   if (DEVICE_OK != nRet)
     return nRet;



   // binning
   pAct = new CPropertyAction (this, &CIDS_uEye::OnBinning);
   nRet = CreateProperty(MM::g_Keyword_Binning,"", MM::String, false, pAct);
   assert(nRet == DEVICE_OK);

   nRet = SetAllowedBinning();
   if (nRet != DEVICE_OK)
     return nRet;


   
   // pixel type
   pAct = new CPropertyAction (this, &CIDS_uEye::OnPixelType);
   nRet = CreateProperty(MM::g_Keyword_PixelType, g_PixelType_8bit, MM::String, false, pAct);
   assert(nRet == DEVICE_OK);

   //test for available color modes, determine the real, i.e., maximal bit depth
   vector<string> pixelTypeValues;

   nComponents_=1;
   bitDepthReal_=8;
   if(is_SetColorMode(hCam, IS_CM_MONO8)==IS_SUCCESS){
     pixelTypeValues.push_back(g_PixelType_8bit);
   }
   if(is_SetColorMode(hCam, IS_CM_MONO10)==IS_SUCCESS){
     bitDepthReal_=10;
     pixelTypeValues.push_back(g_PixelType_10bit);
   }
   if(is_SetColorMode(hCam, IS_CM_MONO12)==IS_SUCCESS){
     bitDepthReal_=12;
     pixelTypeValues.push_back(g_PixelType_12bit);
   }
   if(is_SetColorMode(hCam, IS_CM_MONO16)==IS_SUCCESS){
     bitDepthReal_=16;
     pixelTypeValues.push_back(g_PixelType_16bit);
   }
   bitDepth_=bitDepthReal_;                             //initial bit depth=maximal bit depth for monochrome

   if(sensorInfo.nColorMode==IS_COLORMODE_BAYER){
     nComponents_=4;
     bitDepth_=8;                                       //initial bit depth=8bit for color FIXME: support higher bit depths for color 
     //if(is_SetColorMode(hCam, IS_CM_RGB8_PACKED)==IS_SUCCESS){
     //  pixelTypeValues.push_back(g_PixelType_8bitRGB);
     //}
     //if(is_SetColorMode(hCam, IS_CM_BGR8_PACKED)==IS_SUCCESS){        //RGB8/BGR8_PACKED require a different transfer method
     //  pixelTypeValues.push_back(g_PixelType_8bitBGR);
     //}
     if(is_SetColorMode(hCam, IS_CM_RGBA8_PACKED)==IS_SUCCESS){
       pixelTypeValues.push_back(g_PixelType_8bitRGBA);
     }
     if(is_SetColorMode(hCam, IS_CM_BGRA8_PACKED)==IS_SUCCESS){
       pixelTypeValues.push_back(g_PixelType_8bitBGRA);
     }
   }


   //get the last color mode which was successfuly set, this will be used as the default
   colorModeDef_=is_SetColorMode(hCam, IS_GET_COLOR_MODE);

   //set color mode to default
   //NOTE: after unsuccessful probing a valid mode needs to be set, otherwise acquisition may be disturbed
   is_SetColorMode(hCam, colorModeDef_);
   printf("IDS_uEye: color mode %s\n", colorModeToString(is_SetColorMode(hCam, IS_GET_COLOR_MODE)).c_str());
   
   nRet = SetAllowedValues(MM::g_Keyword_PixelType, pixelTypeValues);
   if (nRet != DEVICE_OK)
      return nRet;


   // bit depth
   nRet = CreateProperty("BitDepth",CDeviceUtils::ConvertToString((int)GetBitDepth()) , MM::Integer, true);
   assert(nRet == DEVICE_OK);


   // pixel clock
   pAct = new CPropertyAction (this, &CIDS_uEye::OnPixelClock);
   nRet = CreateProperty("Pixel Clock",CDeviceUtils::ConvertToString((int)pixelClkCur_) , MM::Float, false, pAct);
   assert(nRet == DEVICE_OK);
   SetPropertyLimits("Pixel Clock",pixelClkMin_, pixelClkMax_);


   // frame rate
   pAct = new CPropertyAction (this, &CIDS_uEye::OnFramerate);
   nRet = CreateProperty("Frame Rate",CDeviceUtils::ConvertToString((double)framerateMax_) , MM::Float, false, pAct);
   assert(nRet == DEVICE_OK);
   SetPropertyLimits("Frame Rate", framerateMin_, framerateMax_);


   // exposure
   pAct = new CPropertyAction (this, &CIDS_uEye::OnExposure);
   nRet = CreateProperty(MM::g_Keyword_Exposure,CDeviceUtils::ConvertToString(exposureMin_) , MM::Float, false, pAct);
   assert(nRet == DEVICE_OK);
   SetPropertyLimits(MM::g_Keyword_Exposure, exposureMin_, exposureMax_);
   SetExposure(exposureMin_);


   //exposure interval (read-only)
   nRet = CreateProperty(MM::g_Keyword_Interval_ms,CDeviceUtils::ConvertToString(exposureIncrement_) , MM::Float, true);
	
  
   // camera gain
   pAct = new CPropertyAction (this, &CIDS_uEye::OnGainMaster);
   nRet = CreateProperty(MM::g_Keyword_Gain, "0", MM::Integer, false, pAct);
   assert(nRet == DEVICE_OK);
   SetPropertyLimits(MM::g_Keyword_Gain, 0, 100);

   // camera offset
   nRet = CreateProperty(MM::g_Keyword_Offset, "0", MM::Integer, false);
   assert(nRet == DEVICE_OK);

   // readout time
   pAct = new CPropertyAction (this, &CIDS_uEye::OnReadoutTime);
   nRet = CreateProperty(MM::g_Keyword_ReadoutTime, "0", MM::Float, false, pAct);
   assert(nRet == DEVICE_OK);

 
   // trigger
   pAct = new CPropertyAction (this, &CIDS_uEye::OnTrigger);
   nRet = CreateProperty("Trigger", g_TriggerType_SOFTWARE, MM::String, false, pAct);
   assert(nRet == DEVICE_OK);

   //test for available trigger types
   vector<string> triggerTypeValues;
   if(is_SetExternalTrigger(hCam, IS_SET_TRIGGER_OFF)==IS_SUCCESS){
     triggerTypeValues.push_back(g_TriggerType_OFF);
   }
   if(is_SetExternalTrigger(hCam, IS_SET_TRIGGER_HI_LO)==IS_SUCCESS){
     triggerTypeValues.push_back(g_TriggerType_HI_LO);
   }
   if(is_SetExternalTrigger(hCam, IS_SET_TRIGGER_LO_HI)==IS_SUCCESS){
     triggerTypeValues.push_back(g_TriggerType_LO_HI);
   }
   if(is_SetExternalTrigger(hCam, IS_SET_TRIGGER_SOFTWARE)==IS_SUCCESS){        //this should become the default
     triggerTypeValues.push_back(g_TriggerType_SOFTWARE);
   }

   nRet = SetAllowedValues("Trigger", triggerTypeValues);
   if (nRet != DEVICE_OK)
      return nRet;


   // Trigger device
   pAct = new CPropertyAction (this, &CIDS_uEye::OnTriggerDevice);
   CreateProperty("TriggerDevice","", MM::String, false, pAct);

   pAct = new CPropertyAction (this, &CIDS_uEye::OnDropPixels);
   CreateProperty("DropPixels", "0", MM::Integer, false, pAct);
   AddAllowedValue("DropPixels", "0");
   AddAllowedValue("DropPixels", "1");


   pAct = new CPropertyAction (this, &CIDS_uEye::OnFractionOfPixelsToDropOrSaturate);
   CreateProperty("FractionOfPixelsToDropOrSaturate", "0.002", MM::Float, false, pAct);
   SetPropertyLimits("FractionOfPixelsToDropOrSaturate", 0., 0.1);


   // synchronize all properties
   // --------------------------
   nRet = UpdateStatus();
   if (nRet != DEVICE_OK)
      return nRet;


   
#ifdef TESTRESOURCELOCKING
   TestResourceLocking(true);
   LogMessage("TestResourceLocking OK",true);
#endif



   // set up image buffer
   nRet = ResizeImageBuffer();
   if (nRet != DEVICE_OK)
      return nRet;


   //clear image buffer
   ClearImageBuffer(img_);

   

   //allocate image memory
   SetImageMemory();

   
   //reset ROI
   ClearROI();  


   //set the display mode to capturing bitmap into RAM (DIB)
   printf("setting display mode to DIB: ");
   nReturn = is_SetDisplayMode (hCam, IS_SET_DM_DIB);
   if (nReturn != IS_SUCCESS){
     printf("could not set display mode to DIB, error %d\n", nReturn);
   }
   printf("display mode: %d\n", is_SetDisplayMode(hCam, IS_GET_DISPLAY_MODE));


   //set initial pixel clock
   nReturn = is_PixelClock(hCam, IS_PIXELCLOCK_CMD_GET_DEFAULT,
                           (void*)&pixelClkDef_, sizeof(pixelClkDef_));

   if(nReturn==IS_SUCCESS){                     //obtained default pixel clock
     printf("default pixel clock: %d MHz\n",pixelClkDef_);
     pixelClkCur_=pixelClkDef_;
     SetPixelClock(pixelClkCur_);
   }
   else{                                        //could not obtain default pixel clock, guess one
     if(bitDepthReal_>8){
     pixelClkDef_=pixelClkMin_+(pixelClkMax_-pixelClkMin_)/4;
     }
     else{
       pixelClkDef_=pixelClkMax_;
     }
   }
   
   pixelClkCur_=pixelClkDef_;
   SetPixelClock(pixelClkCur_);


   //disable subsampling
   is_SetSubSampling(hCam, IS_SUBSAMPLING_DISABLE);

   //disable hardware gamma correction
   is_SetHardwareGamma(hCam, IS_SET_HW_GAMMA_OFF);

   
   //set timeout for image acquisition
   nReturn = is_SetTimeout (hCam, IS_TRIGGER_TIMEOUT, ACQ_TIMEOUT);
   if (nReturn != IS_SUCCESS){                          //could not set time out
     printf("IDS_uEye: could not set acquisition time out\n");
   }
 

   initialized_ = true;
   return DEVICE_OK;
}



/**
* Shuts down (unloads) the device.
* Required by the MM::Device API.
* Ideally this method will completely unload the device and release all resources.
* Shutdown() may be called multiple times in a row.
* After Shutdown() we should be allowed to call Initialize() again to load the device
* without causing problems.
*/
int CIDS_uEye::Shutdown()
{
  

   is_FreeImageMem (hCam, pcImgMem,  memPid);

   is_ExitCamera (hCam);

   initialized_ = false;
   return DEVICE_OK;
}


/**
* Performs exposure and grabs a single image.
* This function should block during the actual exposure and return immediately afterwards 
* (i.e., before readout). This behavior is needed for proper synchronization with the shutter.
* Required by the MM::Camera API.
*/
int CIDS_uEye::SnapImage()
{

  int nReturn;
  char *pErr = 0;


  static int callCounter = 0;
  ++callCounter;

  MM::MMTime startTime = GetCurrentMMTime();
  double exp = GetExposure();
  double expUs = exp * 1000.0;

  
  //nReturn=is_FreezeVideo(hCam, IS_WAIT);                       //returns when the first image is in memory
  nReturn=is_FreezeVideo(hCam, ACQ_TIMEOUT);                    //returns when the first image is in memory or timeout
  if(nReturn !=IS_SUCCESS){
    if(nReturn == IS_TRANSFER_ERROR){
      printf("IDS_uEye: failed capturing an image, transfer failed. Check/reduce pixel clock.\n");
    }
    else{
      is_GetError(hCam, &nReturn, &pErr);                       //retrieve detailed error message
      printf("IDS_uEye: failed capturing an image, error %d: %s\n", nReturn, pErr);
    }
  }
  

  /*
  //copy image into the buffer (not needed if the buffer is assigned directly via SetAllocatedImageMem)

  //copy image to the buffer using built-in function    //FIXME: (seems not to work for 12 bit with SDK 4.0 on linux)
  //nReturn=is_CopyImageMem(hCam, pcImgMem, memPid, (char*)img_.GetPixelsRW());          
  //if(nReturn !=IS_SUCCESS){
  //  printf("IDS_uEye: failed to copy image data, error %d\n", nReturn);
  // }


  //manually copy image to the buffer
  int memSize;
  //memSize=roiXSize_*roiYSize_;                           //8 bit
  memSize=roiXSize_*roiYSize_*2;                        //12 or 16 bit
  
  unsigned char* bufferMem=img_.GetPixelsRW();
  for(int i=0; i<memSize; i+=2){
    
    //method 1: normal copy (works for 8 bit and 12 bit)
    bufferMem[i]=pcImgMem[i];
    bufferMem[i+1]=pcImgMem[i+1];

    //method 2: reverse endianity
    //bufferMem[i]=pcImgMem[i+1];
    //bufferMem[i+1]=pcImgMem[i];

    //method 3: pack 16 bit to 8 by skipping every second byte
    //bufferMem[i/2]=pcImgMem[i+1]; 

  }
  */
  


  MM::MMTime s0(0,0);
  MM::MMTime t2 = GetCurrentMMTime();
  if( s0 < startTime )
    {
      // ensure wait time is non-negative
      long naptime = (long)(0.5 + expUs - (double)(t2-startTime).getUsec());
      if( naptime < 1)
        naptime = 1;
      // longest possible nap is about 38 minutes
      CDeviceUtils::NapMicros((unsigned long) naptime);
   }
  else
    {
      std::cerr << "You are operating this device adapter without setting the core callback, timing functions aren't yet available" << std::endl;
      // called without the core callback probably in off line test program
      // need way to build the core in the test program

   }

   readoutStartTime_ = GetCurrentMMTime();

   return DEVICE_OK;
}


/**
* Returns pixel data.
* Required by the MM::Camera API.
* The calling program will assume the size of the buffer based on the values
* obtained from GetImageBufferSize(), which in turn should be consistent with
* values returned by GetImageWidth(), GetImageHeight() and GetImageBytesPerPixel().
* The calling program allso assumes that camera never changes the size of
* the pixel buffer on its own. In other words, the buffer can change only if
* appropriate properties are set (such as binning, pixel type, etc.)
*/
const unsigned char* CIDS_uEye::GetImageBuffer()
{
  
   MMThreadGuard g(imgPixelsLock_);
   MM::MMTime readoutTime(readoutUs_);
   while (readoutTime > (GetCurrentMMTime() - readoutStartTime_)) {}		
   unsigned char *pB = (unsigned char*)(img_.GetPixels());
   return pB;
}

/**
* Returns image buffer X-size in pixels.
* Required by the MM::Camera API.
*/
unsigned CIDS_uEye::GetImageWidth() const
{
  
   return img_.Width();
}

/**
* Returns image buffer Y-size in pixels.
* Required by the MM::Camera API.
*/
unsigned CIDS_uEye::GetImageHeight() const
{

   return img_.Height();
}

/**
* Returns image buffer pixel depth in bytes.
* Required by the MM::Camera API.
*/
unsigned CIDS_uEye::GetImageBytesPerPixel() const
{

   return img_.Depth();
} 

/**
* Returns the bit depth (dynamic range) of the pixel.
* This does not affect the buffer size, it just gives the client application
* a guideline on how to interpret pixel values.
* Required by the MM::Camera API.
*/
unsigned CIDS_uEye::GetBitDepth() const
{

   return bitDepth_;
}

/**
* Returns the size in bytes of the image buffer.
* Required by the MM::Camera API.
*/
long CIDS_uEye::GetImageBufferSize() const
{
 
   return img_.Width() * img_.Height() * GetImageBytesPerPixel();
}

/**
* Sets the camera Region Of Interest.
* Required by the MM::Camera API.
* This command will change the dimensions of the image.
* Depending on the hardware capabilities the camera may not be able to configure the
* exact dimensions requested - but will try as close as possible.
* @param x - top-left corner coordinate
* @param y - top-left corner coordinate
* @param xSize - width
* @param ySize - height
*/
int CIDS_uEye::SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize)
{
  
  IS_RECT rectAOI;
  IS_RECT rectAOI2;
  IS_SIZE_2D sizeMin;
  IS_SIZE_2D sizeInc;


  if (xSize == 0 && ySize == 0){

    //clear ROI
    ClearROI();
    
    return DEVICE_OK;
  }
  else{

    /*
    //stop acquisition
    is_StopLiveVideo (hCam, IS_FORCE_VIDEO_STOP);
    */  

    printf("IDS_uEye: ROI of %d x %d pixel requested\n", xSize, ySize);

    //read out the current ROI
    is_AOI(hCam, IS_AOI_IMAGE_GET_AOI, (void*)&rectAOI2, sizeof(rectAOI2)); 

    //check ROI parameters and define the ROI rectangle
    is_AOI(hCam, IS_AOI_IMAGE_GET_SIZE_INC , (void*)&sizeInc, sizeof(sizeInc)); 
    is_AOI(hCam, IS_AOI_IMAGE_GET_SIZE_MIN , (void*)&sizeMin, sizeof(sizeMin)); 

    //printf("minimal ROI size: %d x %d pixels\n", sizeMin.s32Width, sizeMin.s32Height);
    //printf("ROI increment: x:%d  y:%d pixels\n", sizeInc.s32Width, sizeInc.s32Height);

    rectAOI.s32X = x;
    rectAOI.s32Y = y;

    if (((long)xSize > sizeMin.s32Width) && ((long)xSize <= cameraCCDXSize_)) {
      rectAOI.s32Width= xSize / sizeInc.s32Width * sizeInc.s32Width;
    }
    if (((long)ySize > sizeMin.s32Height) && ((long)ySize <= cameraCCDYSize_)) {
      rectAOI.s32Height= ySize / sizeInc.s32Height * sizeInc.s32Height;
    }
    

    //apply ROI  
    INT nRet = is_AOI(hCam, IS_AOI_IMAGE_SET_AOI, (void*)&rectAOI, sizeof(rectAOI)); 

    if(nRet==IS_SUCCESS){


      //update ROI parameters
      roiX_=rectAOI.s32X;
      roiY_=rectAOI.s32Y;      
      roiXSize_=rectAOI.s32Width;
      roiYSize_=rectAOI.s32Height;

      //read out the ROI
      is_AOI(hCam, IS_AOI_IMAGE_GET_AOI, (void*)&rectAOI2, sizeof(rectAOI2)); 
      printf("IDS_uEye: ROI of %d x %d pixel obtained\n", rectAOI2.s32Width, rectAOI2.s32Height );

      roiXSizeReal_=rectAOI2.s32Width*binX_;
      roiYSizeReal_=rectAOI2.s32Height*binY_;

 
      //adjust image memory
      is_FreeImageMem (hCam, pcImgMem,  memPid);
      ResizeImageBuffer();
      ClearImageBuffer(img_);
      SetImageMemory();

      
      //update frame rate range
      GetFramerateRange(hCam, &framerateMin_, &framerateMax_);
      SetPropertyLimits("Frame Rate", framerateMin_, framerateMax_);
      //printf("new frame rate range %f %f\n", framerateMin_, framerateMax_);

      //update the exposure range
      GetExposureRange(hCam, &exposureMin_, &exposureMax_, &exposureIncrement_);
      if(exposureMax_>EXPOSURE_MAX) exposureMax_=EXPOSURE_MAX;                  //limit exposure time to keep the interface responsive
      SetPropertyLimits(MM::g_Keyword_Exposure, exposureMin_, exposureMax_);

      //FIXME: device properties browser window needs to be refreshed here

      return DEVICE_OK;
    }
    else{
      LogMessage("IDS_uEye: could not set ROI",true);
      return ERR_ROI_INVALID;
    }
    
  }

}



/**
* Returns the actual dimensions of the current ROI.
* Required by the MM::Camera API.
*/
int CIDS_uEye::GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize)
{
  
   x = roiX_;
   y = roiY_;

   xSize = roiXSize_;
   ySize = roiYSize_;

   return DEVICE_OK;
}


/**
* Resets the Region of Interest to full frame.
* Required by the MM::Camera API.
*/
int CIDS_uEye::ClearROI()
{

  IS_RECT rectAOI;


  rectAOI.s32X     = 0;
  rectAOI.s32Y     = 0;
  rectAOI.s32Width = cameraCCDXSize_/binX_;
  rectAOI.s32Height =cameraCCDYSize_/binY_;
  
  
  //apply ROI  
  INT nRet = is_AOI(hCam, IS_AOI_IMAGE_SET_AOI, (void*)&rectAOI, sizeof(rectAOI)); 
  
  if(nRet==IS_SUCCESS){

    
    //update stored ROI parameters
    roiX_=rectAOI.s32X;
    roiY_=rectAOI.s32Y;      
    roiXSize_=rectAOI.s32Width;
    roiYSize_=rectAOI.s32Height;
    roiXSizeReal_=rectAOI.s32Width*binX_;
    roiYSizeReal_=rectAOI.s32Height*binY_;


    //adjust image memory
    is_FreeImageMem (hCam, pcImgMem,  memPid);
    ResizeImageBuffer();
    ClearImageBuffer(img_);
    SetImageMemory(); 
          

    //update pixel clock range
    GetPixelClockRange(hCam, &pixelClkMin_, &pixelClkMax_);
    SetPropertyLimits("Pixel Clock",pixelClkMin_, pixelClkMax_); 
    

    //update frame rate range
    GetFramerateRange(hCam, &framerateMin_, &framerateMax_);
    SetPropertyLimits("Frame Rate", framerateMin_, framerateMax_);
     
    
    //update the exposure range
    GetExposureRange(hCam, &exposureMin_, &exposureMax_, &exposureIncrement_);
    if(exposureMax_>EXPOSURE_MAX) exposureMax_=EXPOSURE_MAX;                       //limit exposure time to keep the interface responsive
    SetPropertyLimits(MM::g_Keyword_Exposure, exposureMin_, exposureMax_);  


    return DEVICE_OK;
  }
  else{
    LogMessage("could not set ROI",true);
    return ERR_ROI_INVALID;
  } 

}



/*
  convenience function for obtaining the pixel clock range
*/
void CIDS_uEye::GetPixelClockRange(HIDS hCam, UINT* pixClkMin, UINT* pixClkMax) {
  
   UINT pixClkRange[3];
   UINT nReturn = is_PixelClock(hCam, IS_PIXELCLOCK_CMD_GET_RANGE, (void*)pixClkRange, sizeof(pixClkRange));
   if (nReturn == IS_SUCCESS){
     *pixClkMin = pixClkRange[0];
     *pixClkMax = pixClkRange[1];
   }

}


/*
  convenience function for obtaining the frame rate range
*/
void CIDS_uEye::GetFramerateRange(HIDS hCam, double* fpsMin, double* fpsMax) {
    
  double minFrameTime=0;
  double maxFrameTime=0;
  double frameTimeInc=0;
  
  UINT nReturn= is_GetFrameTimeRange(hCam, &minFrameTime, &maxFrameTime, &frameTimeInc);
  
  if (nReturn == IS_SUCCESS){
    *fpsMin=1/maxFrameTime;
    *fpsMax=1/minFrameTime;

  }

}





/*
  convenience function for setting the pixel clock
*/
void CIDS_uEye::SetPixelClock(UINT pixClk) {
  
  UINT nReturn = is_PixelClock(hCam, IS_PIXELCLOCK_CMD_SET, (void*)&pixClk, sizeof(pixClk));
  if(nReturn!=IS_SUCCESS){
    printf("IDS_uEye: could not set pixel clock %d MHz\n", pixClk);
  }
  nReturn = is_PixelClock(hCam, IS_PIXELCLOCK_CMD_GET, (void*)&pixelClkCur_, sizeof(pixelClkCur_));
  printf("IDS_uEye: pixel clock set to %d MHz\n",pixClk);

}




/**
* Returns the current exposure setting in milliseconds.
* Required by the MM::Camera API.
*/
double CIDS_uEye::GetExposure() const
{
  
   char buf[MM::MaxStrLength];
   int ret = GetProperty(MM::g_Keyword_Exposure, buf);
   if (ret != DEVICE_OK)
      return 0.0;
   return atof(buf);
}

/**
* Sets exposure in milliseconds.
* Required by the MM::Camera API.
*/
void CIDS_uEye::SetExposure(double exp)
{
   exposureSet_=exp;
   //is_SetExposureTime (hCam, exposureSet_, &exposureCur_);            //deprecated since v. 4.0
   is_Exposure(hCam, IS_EXPOSURE_CMD_SET_EXPOSURE, &exposureSet_, sizeof(exposureSet_));
   is_Exposure(hCam, IS_EXPOSURE_CMD_GET_EXPOSURE, &exposureCur_, sizeof(exposureCur_));

   SetProperty(MM::g_Keyword_Exposure, CDeviceUtils::ConvertToString(exposureCur_));

   //GetCoreCallback()->OnExposureChanged(this, exposureCur_);            //FIXME: what does this do?
}


/*
  convenience function for obtaining the exposure range (compatible to v. 3.82 format)
 */
void CIDS_uEye::GetExposureRange(HIDS hCam, double* expMin, double* expMax, double* expIncrement) {
  
  double params[3];
  is_Exposure(hCam, IS_EXPOSURE_CMD_GET_EXPOSURE_RANGE, &params, sizeof(params));
  *expMin = params[0];
  *expMax = params[1];
  *expIncrement = params[2];

}


/**
* Returns the current binning factor.
* Required by the MM::Camera API.
*/
int CIDS_uEye::GetBinning() const
{
 
   char buf[MM::MaxStrLength];
   int ret = GetProperty(MM::g_Keyword_Binning, buf);
   if (ret != DEVICE_OK)
      return 1;
   return atoi(buf);
}

/**
* Sets binning factor.
* Required by the MM::Camera API.
*/
int CIDS_uEye::SetBinning(int binF)
{
  
   return SetProperty(MM::g_Keyword_Binning, CDeviceUtils::ConvertToString(binF));
}

/* determine available binning combinations */
int CIDS_uEye::SetAllowedBinning() 
{

  vector<string> binModeValues;
  int i,j;
  stringstream modeName;

  //initialize the structure
  for(i=0;i<binModesHNum;i++){
    for(j=0;j<binModesVNum;j++){ 
      binModes[i][j].name="";
      binModes[i][j].mode=IS_BINNING_DISABLE;
      binModes[i][j].binX=1;
      binModes[i][j].binY=1;
    }
  }

  //probe available modes
  //NOTE: as with SDK 4.30 for cameras which support only simultaneous binning on x and y this method yields redundant modes
  //       for such cameras IS_SUCCESS is returned if the x-binning factor is available
  for(i=0;i<binModesHNum;i++){
    for(j=0;j<binModesVNum;j++){
      is_SetBinning(hCam, IS_BINNING_DISABLE);  //clear binning
      if(is_SetBinning(hCam,binModesH[i]|binModesV[j])==IS_SUCCESS){
        binModes[i][j].mode=binModesH[i]|binModesV[j];
        binModes[i][j].binX=is_SetBinning(hCam,IS_GET_BINNING_FACTOR_HORIZONTAL);
        binModes[i][j].binY=is_SetBinning(hCam,IS_GET_BINNING_FACTOR_VERTICAL);
        
        modeName.str(std::string());            //clear stream
        modeName<<binModes[i][j].binX<<"x"<<binModes[i][j].binY<<" ("<<std::showbase<<std::hex<<binModes[i][j].mode<<")"<<std::dec;
        binModes[i][j].name=modeName.str();

        printf("IDS_uEye: binning %dx%d available\n",binModes[i][j].binX, binModes[i][j].binY);
        binModeValues.push_back(binModes[i][j].name);
      }
    }
  }

  //reset to default 1x1 mode
  is_SetBinning(hCam, IS_BINNING_DISABLE);
  binModeName_=binModes[0][0].name; 
  binX_=1;
  binY_=1;
      
  LogMessage("Setting Allowed Binning settings", true);
  return SetAllowedValues(MM::g_Keyword_Binning, binModeValues);
}



/**
 * Required by the MM::Camera API
 * Please implement this yourself and do not rely on the base class implementation
 * The Base class implementation is deprecated and will be removed shortly
 */
int CIDS_uEye::StartSequenceAcquisition(double interval) {

   return StartSequenceAcquisition(LONG_MAX, interval, false);            
}

/**                                                                       
* Stop and wait for the Sequence thread finished                                   
*/                                                                        
int CIDS_uEye::StopSequenceAcquisition()                                     
{
   if (IsCallbackRegistered())
   {
      
   }

   if (!thd_->IsStopped()) {
      thd_->Stop();                                                       
      thd_->wait();                                                       
   }                                                                      
                                                                          
   return DEVICE_OK;                                                      
} 

/**
* Simple implementation of Sequence Acquisition
* A sequence acquisition should run on its own thread and transport new images
* coming of the camera into the MMCore circular buffer.
*/
int CIDS_uEye::StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow)
{
   
   if (IsCapturing())
      return DEVICE_CAMERA_BUSY_ACQUIRING;

   int ret = GetCoreCallback()->PrepareForAcq(this);
   if (ret != DEVICE_OK)
      return ret;
   sequenceStartTime_ = GetCurrentMMTime();
   imageCounter_ = 0;
   thd_->Start(numImages,interval_ms);
   stopOnOverflow_ = stopOnOverflow;
   return DEVICE_OK;
}

/*
 * Inserts Image and MetaData into MMCore circular Buffer
 */
int CIDS_uEye::InsertImage()
{
 

   MM::MMTime timeStamp = this->GetCurrentMMTime();
   char label[MM::MaxStrLength];
   this->GetLabel(label);
 
   // Important:  metadata about the image are generated here:
   Metadata md;
   /*
   // Copy the metadata inserted by other processes:
   std::vector<std::string> keys = metadata_.GetKeys();
   for (unsigned int i= 0; i < keys.size(); i++) {
      MetadataSingleTag mst = metadata_.GetSingleTag(keys[i].c_str());
      md.PutTag(mst.GetName(), mst.GetDevice(), mst.GetValue());
   }
   */

   // Add our own metadata
   md.put("Camera", label);
   md.put(MM::g_Keyword_Metadata_StartTime, CDeviceUtils::ConvertToString(sequenceStartTime_.getMsec()));
   md.put(MM::g_Keyword_Elapsed_Time_ms, CDeviceUtils::ConvertToString((timeStamp - sequenceStartTime_).getMsec()));
   md.put(MM::g_Keyword_Metadata_ImageNumber, CDeviceUtils::ConvertToString(imageCounter_));
   md.put(MM::g_Keyword_Metadata_ROI_X, CDeviceUtils::ConvertToString( (long) roiX_)); 
   md.put(MM::g_Keyword_Metadata_ROI_Y, CDeviceUtils::ConvertToString( (long) roiY_)); 

   imageCounter_++;

   char buf[MM::MaxStrLength];
   GetProperty(MM::g_Keyword_Binning, buf);
   md.put(MM::g_Keyword_Binning, buf);

   MMThreadGuard g(imgPixelsLock_);

   const unsigned char* pI = GetImageBuffer();
   unsigned int w = GetImageWidth();
   unsigned int h = GetImageHeight();
   unsigned int b = GetImageBytesPerPixel();

   int ret = GetCoreCallback()->InsertImage(this, pI, w, h, b, md.Serialize().c_str());
   if (!stopOnOverflow_ && ret == DEVICE_BUFFER_OVERFLOW)
   {
      // do not stop on overflow - just reset the buffer
      GetCoreCallback()->ClearImageBuffer(this);
      // don't process this same image again...
      return GetCoreCallback()->InsertImage(this, pI, w, h, b, md.Serialize().c_str(), false);
   } else
      return ret;
}

/*
 * Do actual capturing
 * Called from inside the thread  
 */
int CIDS_uEye::ThreadRun (void)
{
  
   int ret=DEVICE_ERR;
   
   // Trigger
   if (triggerDevice_.length() > 0) {
      MM::Device* triggerDev = GetDevice(triggerDevice_.c_str());
      if (triggerDev != 0) {
      	LogMessage("trigger requested");
      	triggerDev->SetProperty("Trigger","+");
      }
   }
   
   ret = SnapImage();
   if (ret != DEVICE_OK)
   {
      return ret;
   }
   ret = InsertImage();
   if (ret != DEVICE_OK)
   {
      return ret;
   }
   return ret;
};

bool CIDS_uEye::IsCapturing() {
   return !thd_->IsStopped();
}

/*
 * called from the thread function before exit 
 */
void CIDS_uEye::OnThreadExiting() throw()
{
   try
   {
      LogMessage(g_Msg_SEQUENCE_ACQUISITION_THREAD_EXITING);
      GetCoreCallback()?GetCoreCallback()->AcqFinished(this,0):DEVICE_OK;
   }
   catch(...)
   {
      LogMessage(g_Msg_EXCEPTION_IN_ON_THREAD_EXITING, false);
   }
}




MySequenceThread::MySequenceThread(CIDS_uEye* pCam)
   :intervalMs_(default_intervalMS)
   ,numImages_(default_numImages)
   ,imageCounter_(0)
   ,stop_(true)
   ,suspend_(false)
   ,camera_(pCam)
   ,startTime_(0)
   ,actualDuration_(0)
   ,lastFrameTime_(0)
{};

MySequenceThread::~MySequenceThread() {};

void MySequenceThread::Stop() {
   MMThreadGuard(this->stopLock_);
   stop_=true;
}

void MySequenceThread::Start(long numImages, double intervalMs)
{
   MMThreadGuard(this->stopLock_);
   MMThreadGuard(this->suspendLock_);
   numImages_=numImages;
   intervalMs_=intervalMs;
   imageCounter_=0;
   stop_ = false;
   suspend_=false;
   activate();
   actualDuration_ = 0;
   startTime_= camera_->GetCurrentMMTime();
   lastFrameTime_ = 0;
}

bool MySequenceThread::IsStopped(){
   MMThreadGuard(this->stopLock_);
   return stop_;
}

void MySequenceThread::Suspend() {
   MMThreadGuard(this->suspendLock_);
   suspend_ = true;
}

bool MySequenceThread::IsSuspended() {
   MMThreadGuard(this->suspendLock_);
   return suspend_;
}

void MySequenceThread::Resume() {
   MMThreadGuard(this->suspendLock_);
   suspend_ = false;
}

int MySequenceThread::svc(void) throw()
{
   int ret=DEVICE_ERR;
   try 
   {
      do
      {  
         ret=camera_->ThreadRun();
      } while (DEVICE_OK == ret && !IsStopped() && imageCounter_++ < numImages_-1);
      if (IsStopped())
         camera_->LogMessage("SeqAcquisition interrupted by the user\n");
   }catch(...){
      camera_->LogMessage(g_Msg_EXCEPTION_IN_THREAD, false);
   }
   stop_=true;
   actualDuration_ = camera_->GetCurrentMMTime() - startTime_;
   camera_->OnThreadExiting();
   return ret;
}



///////////////////////////////////////////////////////////////////////////////
// CIDS_uEye Action handlers
///////////////////////////////////////////////////////////////////////////////


/**
* Handles "Binning" property.
*/
int CIDS_uEye::OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct)
{
 
  int i,j; 
  int ret = DEVICE_INVALID_PROPERTY_VALUE;
  


  if (eAct == MM::BeforeGet){
   
    pProp->Set(binModeName_.c_str());

    ret=DEVICE_OK; 
  }

  else if (eAct == MM::AfterSet){

    if(IsCapturing())
      return DEVICE_CAMERA_BUSY_ACQUIRING;

    string binModeName;
    pProp->Get(binModeName);
    
    for(i=0;i<binModesVNum;i++){
      for(j=0;j<binModesHNum;j++){
        if(strcmp(binModeName.c_str(), binModes[i][j].name.c_str())==0){
          is_SetBinning(hCam, IS_BINNING_DISABLE);                      //clear binning
          binModeName_=binModes[0][0].name;
          if(is_SetBinning (hCam, binModes[i][j].mode)==IS_SUCCESS){
            binX_= is_SetBinning(hCam,IS_GET_BINNING_FACTOR_HORIZONTAL);
            binY_= is_SetBinning(hCam,IS_GET_BINNING_FACTOR_VERTICAL);
            binModeName_=binModes[i][j].name;
            printf("IDS_uEye: set binning to %s\n",binModes[i][j].name.c_str());

            //printf("IDS_uEye: obtained binning %dx%d\n",binX_,binY_);

            ret=DEVICE_OK;
          }
        }

      }
    }

     
  
    //if the mode was changed adjust memory buffers and other dependent parameters
    if(ret==DEVICE_OK){

      //adjust image memory
      is_FreeImageMem (hCam, pcImgMem,  memPid);
      ResizeImageBuffer();
      ClearImageBuffer(img_);
      SetImageMemory();


      //update frame rate range
      GetFramerateRange(hCam, &framerateMin_, &framerateMax_);
      SetPropertyLimits("Frame Rate", framerateMin_, framerateMax_);
     
    
      //update the exposure range
      GetExposureRange(hCam, &exposureMin_, &exposureMax_, &exposureIncrement_);
      if(exposureMax_>EXPOSURE_MAX) exposureMax_=EXPOSURE_MAX;
      SetPropertyLimits(MM::g_Keyword_Exposure, exposureMin_, exposureMax_);
    }

  }
  

  return ret; 
}



/**
* Handles "Pixel Clock" property.
*/
int CIDS_uEye::OnPixelClock(MM::PropertyBase* pProp, MM::ActionType eAct)
{

  int ret = DEVICE_OK;

  if (eAct == MM::BeforeGet){
    pProp->Set((long)pixelClkCur_);
  }

  else if (eAct == MM::AfterSet){

    long pixClk;
    pProp->Get(pixClk);
    SetPixelClock((UINT)pixClk);
  

    //update frame rate range
    GetFramerateRange(hCam, &framerateMin_, &framerateMax_);
    SetPropertyLimits("Frame Rate", framerateMin_, framerateMax_);
    printf("IDS_uEye: new frame rate range %.4f - %.4f\n",framerateMin_,framerateMax_);
  

    //update the exposure range
    GetExposureRange(hCam, &exposureMin_, &exposureMax_, &exposureIncrement_);
    if(exposureMax_>EXPOSURE_MAX) exposureMax_=EXPOSURE_MAX;                       //limit exposure time to keep the interface responsive
    SetPropertyLimits(MM::g_Keyword_Exposure, exposureMin_, exposureMax_);
    printf("IDS_uEye: new exposure range %.4f - %.4f\n",exposureMin_,exposureMax_);

    //set again current exposure (FIXME: why is it necessary?)
    SetExposure(exposureCur_);

  }

  return ret; 
}



/**
* Handles "Frame Rate" property.
*/
int CIDS_uEye::OnFramerate(MM::PropertyBase* pProp, MM::ActionType eAct)
{

  int ret = DEVICE_OK;
  UINT nRet;


  if (eAct == MM::BeforeGet){
    pProp->Set(framerateCur_);
  }

  else if (eAct == MM::AfterSet){

    double framerate;
    pProp->Get(framerate);   

    nRet=is_SetFrameRate (hCam, framerate, &framerateCur_);

    if(nRet!=IS_SUCCESS){
      printf("IDS_uEye: could not set framerate\n");
    }
    else{
      printf("IDS_uEye: set framerate to %.2f\n", framerateCur_);
    }


    //update the exposure range
    GetExposureRange(hCam, &exposureMin_, &exposureMax_, &exposureIncrement_);
    if(exposureMax_>EXPOSURE_MAX) exposureMax_=EXPOSURE_MAX;                       //limit exposure time to keep the interface responsive
    SetPropertyLimits(MM::g_Keyword_Exposure, exposureMin_, exposureMax_);
    printf("IDS_uEye: new exposure range %.4f - %.4f\n",exposureMin_,exposureMax_); 

  }

  return ret; 
}



/**
* Handles "Exposure" property.
*/
int CIDS_uEye::OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct)
{

  int ret = DEVICE_OK;
  UINT nRet;

  
  if (eAct == MM::BeforeGet){
    pProp->Set(exposureCur_);
  }

  else if (eAct == MM::AfterSet){
    double exp;
    pProp->Get(exp);

    exposureSet_=exp;

    nRet=is_Exposure(hCam, IS_EXPOSURE_CMD_SET_EXPOSURE, &exposureSet_, sizeof(exposureSet_));
    is_Exposure(hCam, IS_EXPOSURE_CMD_GET_EXPOSURE, &exposureCur_, sizeof(exposureCur_));
    
    if(nRet!=IS_SUCCESS){
      printf("IDS_uEye: could not set exposure\n");
    }
    else{
      printf("IDS_uEye: set exposure to %.4f\n", exposureCur_);
    } 

  }

  return ret;  
}


//FIXME: check whether gamma/LUT, etc. functions need to be deactivated again after mode change
/**
* Handles "PixelType" property.
*/
int CIDS_uEye::OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  
  int ret = DEVICE_INVALID_PROPERTY_VALUE;
  int nRet;


  switch(eAct)
  {

  case MM::BeforeGet:{

    int colorMode;

    colorMode=is_SetColorMode(hCam, IS_GET_COLOR_MODE);
    switch(colorMode){

    case(IS_CM_MONO8):
      pProp->Set(g_PixelType_8bit);
      break;

    case(IS_CM_MONO10):
      pProp->Set(g_PixelType_10bit);
      break;

    case(IS_CM_MONO12):
      pProp->Set(g_PixelType_12bit);
      break;

    case(IS_CM_MONO16):
      pProp->Set(g_PixelType_16bit);
      break;

      
    case(IS_CM_RGB8_PACKED):
      pProp->Set(g_PixelType_8bitRGB);
      break;

    case(IS_CM_BGR8_PACKED):
      pProp->Set(g_PixelType_8bitBGR);
      break;

    case(IS_CM_RGBA8_PACKED):
      pProp->Set(g_PixelType_8bitRGBA);
      break;

    case(IS_CM_BGRA8_PACKED):
      pProp->Set(g_PixelType_8bitBGRA);
      break;  


    default:
      break;
    }


    ret=DEVICE_OK;
  }
    break;



  case MM::AfterSet:{
      
    if(IsCapturing())
      return DEVICE_CAMERA_BUSY_ACQUIRING;
        
    string pixelType;
    pProp->Get(pixelType);
    
    if (pixelType.compare(g_PixelType_8bit) == 0){
          
      nRet=is_SetColorMode(hCam, IS_CM_MONO8);
      if(nRet==IS_SUCCESS){

        nComponents_ = 1;
        bitDepth_ = 8;

        ret=DEVICE_OK;
      }

    }

    else if (pixelType.compare(g_PixelType_10bit) == 0){
          
      nRet=is_SetColorMode(hCam, IS_CM_MONO10);
      if(nRet==IS_SUCCESS){

        nComponents_ = 1;
        bitDepth_ = 10;

        ret=DEVICE_OK;
      }

    }

    else if (pixelType.compare(g_PixelType_12bit) == 0){
          
      nRet=is_SetColorMode(hCam, IS_CM_MONO12);
      if(nRet==IS_SUCCESS){

        nComponents_ = 1;
        bitDepth_ = 12;

        ret=DEVICE_OK;
      }

    }

    else if (pixelType.compare(g_PixelType_16bit) == 0){
          
      nRet=is_SetColorMode(hCam, IS_CM_MONO16);
      if(nRet==IS_SUCCESS){

        nComponents_ = 1;
        bitDepth_ = 16;

        ret=DEVICE_OK;
      }

    }


    else if (pixelType.compare(g_PixelType_8bitRGB) == 0){
      
       nRet=is_SetColorMode(hCam, IS_CM_RGB8_PACKED);
       if(nRet==IS_SUCCESS){

         nComponents_ = 4;
         bitDepth_ = 8;

         ret=DEVICE_OK;
      }
 
    }

    else if (pixelType.compare(g_PixelType_8bitBGR) == 0){
      
       nRet=is_SetColorMode(hCam, IS_CM_BGR8_PACKED);
       if(nRet==IS_SUCCESS){

         nComponents_ = 4;
         bitDepth_ = 8;

         ret=DEVICE_OK;
      }
 
    }


    else if (pixelType.compare(g_PixelType_8bitRGBA) == 0){
      
       nRet=is_SetColorMode(hCam, IS_CM_RGBA8_PACKED);
       if(nRet==IS_SUCCESS){

         nComponents_ = 4;
         bitDepth_ = 8;

         ret=DEVICE_OK;
      }
 
    }

    
    else if (pixelType.compare(g_PixelType_8bitBGRA) == 0){
      
       nRet=is_SetColorMode(hCam, IS_CM_BGRA8_PACKED);
       if(nRet==IS_SUCCESS){

         nComponents_ = 4;
         bitDepth_ = 8;

         ret=DEVICE_OK;
      }
 
    }


    else if (pixelType.compare(g_PixelType_32bit) == 0){
          
      nComponents_ = 1;
      img_.Resize(img_.Width(), img_.Height(), 4);
      ret=DEVICE_OK;
    }


    else{

      // on error do nothing
      ret = ERR_UNKNOWN_MODE;
    }



    //if the mode was changed, adjust memory buffers
    if(ret==DEVICE_OK){
      printf("IDS_uEye: set color mode %s\n", colorModeToString(is_SetColorMode(hCam, IS_GET_COLOR_MODE)).c_str());
      is_FreeImageMem (hCam, pcImgMem,  memPid);
      ResizeImageBuffer();
      ClearImageBuffer(img_);
      SetImageMemory();
    }

  }
    break;


  }
  return ret; 
}



/**
* Handles "ReadoutTime" property.
*/
int CIDS_uEye::OnReadoutTime(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   
   if (eAct == MM::AfterSet)
   {
      double readoutMs;
      pProp->Get(readoutMs);

      readoutUs_ = readoutMs * 1000.0;
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set(readoutUs_ / 1000.0);
   }

   return DEVICE_OK;
}

int CIDS_uEye::OnDropPixels(MM::PropertyBase* pProp, MM::ActionType eAct)
{

   if (eAct == MM::AfterSet)
   {
      long tvalue = 0;
      pProp->Get(tvalue);
		dropPixels_ = (0==tvalue)?false:true;
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set(dropPixels_?1L:0L);
   }

   return DEVICE_OK;
}



int CIDS_uEye::OnFractionOfPixelsToDropOrSaturate(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  
   if (eAct == MM::AfterSet)
   {
      double tvalue = 0;
      pProp->Get(tvalue);
		fractionOfPixelsToDropOrSaturate_ = tvalue;
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set(fractionOfPixelsToDropOrSaturate_);
   }

   return DEVICE_OK;
}




int CIDS_uEye::OnCameraCCDXSize(MM::PropertyBase* pProp , MM::ActionType eAct)
{
  
   if (eAct == MM::BeforeGet)
   {
     pProp->Set(cameraCCDXSize_);
   }
   else if (eAct == MM::AfterSet)
   {
      long value;
      pProp->Get(value);
		if ( (value < 16) || (33000 < value))
			return DEVICE_ERR;  // invalid image size
		if( value != cameraCCDXSize_)
		{
			cameraCCDXSize_ = value;
			img_.Resize(cameraCCDXSize_/binX_, cameraCCDYSize_/binY_);
		}
   }
	return DEVICE_OK;

}


int CIDS_uEye::OnCameraCCDYSize(MM::PropertyBase* pProp, MM::ActionType eAct)
{

   if (eAct == MM::BeforeGet)
   {
		pProp->Set(cameraCCDYSize_);
   }
   else if (eAct == MM::AfterSet)
   {
      long value;
      pProp->Get(value);
		if ( (value < 16) || (33000 < value))
			return DEVICE_ERR;  // invalid image size
		if( value != cameraCCDYSize_)
		{
			cameraCCDYSize_ = value;
			img_.Resize(cameraCCDXSize_/binX_, cameraCCDYSize_/binY_);
		}
   }
	return DEVICE_OK;

}


/**
* Handles "Trigger" property.
*/
int CIDS_uEye::OnTrigger(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  int ret = DEVICE_INVALID_PROPERTY_VALUE;
  int nRet;


  switch(eAct)
  {

  case MM::BeforeGet:{
      
    int triggerMode;
      
    triggerMode=is_SetExternalTrigger(hCam, IS_GET_EXTERNALTRIGGER);
    switch(triggerMode){

    case(IS_SET_TRIGGER_OFF):
      pProp->Set(g_TriggerType_OFF);
      break;

    case(IS_SET_TRIGGER_HI_LO):
      pProp->Set(g_TriggerType_HI_LO);
      break;

    case(IS_SET_TRIGGER_LO_HI):
      pProp->Set(g_TriggerType_LO_HI);
      break;

    case(IS_SET_TRIGGER_SOFTWARE):
      pProp->Set(g_TriggerType_SOFTWARE);
      break;

      
    default:
      break;
    }
    

    ret=DEVICE_OK;
  }
    break;

  case MM::AfterSet:{

    //if(IsCapturing())
    //  return DEVICE_CAMERA_BUSY_ACQUIRING;

    string triggerType;
    pProp->Get(triggerType);

    if (triggerType.compare(g_TriggerType_OFF) == 0){
      
      nRet=is_SetExternalTrigger(hCam, IS_SET_TRIGGER_OFF);
      if(nRet==IS_SUCCESS){
        ret=DEVICE_OK;
      }

    }

    else if (triggerType.compare(g_TriggerType_HI_LO) == 0){
      
      nRet=is_SetExternalTrigger(hCam, IS_SET_TRIGGER_HI_LO);
      if(nRet==IS_SUCCESS){
        ret=DEVICE_OK;
      }

    }

    else if (triggerType.compare(g_TriggerType_LO_HI) == 0){
      
      nRet=is_SetExternalTrigger(hCam, IS_SET_TRIGGER_LO_HI);
      if(nRet==IS_SUCCESS){
        ret=DEVICE_OK;
      }

    }

    
    else if (triggerType.compare(g_TriggerType_SOFTWARE) == 0){
      
      nRet=is_SetExternalTrigger(hCam, IS_SET_TRIGGER_SOFTWARE);
      if(nRet==IS_SUCCESS){
        ret=DEVICE_OK;
      }

    }

    
    else{
      // on error do nothing
      ret = ERR_UNKNOWN_MODE;
    }


    if(ret==DEVICE_OK){
       printf("IDS_uEye: set trigger mode %s\n", triggerModeToString(is_SetExternalTrigger(hCam, IS_GET_EXTERNALTRIGGER)).c_str());
    }

  }
    break;
      

  }


  return ret;
}


int CIDS_uEye::OnTriggerDevice(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(triggerDevice_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(triggerDevice_);
   }
   return DEVICE_OK;
}


/*
  handles "Gain" property
 */
int CIDS_uEye::OnGainMaster(MM::PropertyBase* pProp, MM::ActionType eAct){

  if (eAct == MM::BeforeGet){
    pProp->Set((long)gainMaster_);
  }

  else if (eAct == MM::AfterSet){

    long tvalue = 0;
    pProp->Get(tvalue);
    gainMaster_=tvalue;
    is_SetHardwareGain(hCam, gainMaster_, gainRed_, gainGreen_, gainBlue_);
    
     
  }
  
  return DEVICE_OK;
}



///////////////////////////////////////////////////////////////////////////////
// Private CIDS_uEye methods
///////////////////////////////////////////////////////////////////////////////

/**
* Sync internal image buffer size to the chosen property values.
*/
int CIDS_uEye::ResizeImageBuffer()
{

   //char buf[MM::MaxStrLength];
   int byteDepth = 0;

   int colorMode;



   //determine the necessary number of memory bytes per pixel for the current color mode
   colorMode=is_SetColorMode(hCam, IS_GET_COLOR_MODE);

   switch(colorMode){
   case(IS_CM_MONO8):
     byteDepth=1;
     break;

   case(IS_CM_MONO16): case(IS_CM_MONO12): case(IS_CM_MONO10):
     byteDepth=2;
     break;
     
   case(IS_CM_SENSOR_RAW8):
     byteDepth=1;
     break;
 
   case(IS_CM_SENSOR_RAW16): case(IS_CM_SENSOR_RAW12): case(IS_CM_SENSOR_RAW10):
     byteDepth=2;
     break;

   case(IS_CM_BGR565_PACKED): case(IS_CM_BGR5_PACKED):
     byteDepth=2;
     break;

   case(IS_CM_UYVY_PACKED):  //case(IS_CM_UYVY_MONO):  case(IS_CM_UYVY_BAYER):  //FIXME: MONO and BAYER not yet supported by SDK
     byteDepth=2;
     break;

   case(IS_CM_CBYCRY_PACKED):
     byteDepth=2;
     break; 
     
   case(IS_CM_RGB8_PACKED): case(IS_CM_BGR8_PACKED):
     byteDepth=4;
     break;

   case(IS_CM_RGB10_PACKED): case(IS_CM_RGBA8_PACKED): case(IS_CM_RGBY8_PACKED):
     byteDepth=4;
     break;
     
   case(IS_CM_BGR10_PACKED): case(IS_CM_BGRA8_PACKED): case(IS_CM_BGRY8_PACKED):
     byteDepth=4;
     break;

   case(IS_CM_RGB12_UNPACKED):  case(IS_CM_RGB10_UNPACKED):  case(IS_CM_BGR12_UNPACKED):  case(IS_CM_BGR10_UNPACKED):
     byteDepth=6;
     break;

   case(IS_CM_RGBA12_UNPACKED): case(IS_CM_BGRA12_UNPACKED):
     byteDepth=8;
     break;

   }

   
   
   img_.Resize(roiXSizeReal_/binX_, roiYSizeReal_/binY_, byteDepth);

   printf("IDS_uEye: MM-Image buffer size for %dx%d, color mode %s, %dx%d binning: %ld byte\n",
          roiXSizeReal_,roiYSizeReal_, colorModeToString(colorMode).c_str(), binX_, binY_, GetImageBufferSize());

   return DEVICE_OK;
}



void CIDS_uEye::ClearImageBuffer(ImgBuffer& img)
{
   MMThreadGuard g(imgPixelsLock_);
   if (img.Height() == 0 || img.Width() == 0 || img.Depth() == 0)
      return;
   unsigned char* pBuf = const_cast<unsigned char*>(img.GetPixels());
   memset(pBuf, 0, img.Height()*img.Width()*img.Depth());
}


//allocate and activate image memory correspondig to current ROI, binning and pixel depth
int CIDS_uEye::SetImageMemory()
{

  int nRet;

  //allocate image memory for the current size
  //the size of the ROI already contains binning

  /*
  //  nRet = is_AllocImageMem (hCam, roiXSizeReal_/binX_, roiYSizeReal_/binY_, bitDepthReal_, &pcImgMem, &memPid);
  */


  /*
  //method 1: assign extra memory area which is then copied into the buffer
  nRet = is_AllocImageMem (hCam, roiXSizeReal_/binX_, roiYSizeReal_/binY_, bitDepthReal_, &pcImgMem, &memPid);
  if (nRet != IS_SUCCESS){                          //could not allocate memory
    LogMessage("could not allocate ROI image memory",true);
    return ERR_MEM_ALLOC;
  }
 
  */

  //method 2: directly assign the buffer to the camera
  pcImgMem=(char*)img_.GetPixelsRW();
  nRet = is_SetAllocatedImageMem (hCam, roiXSizeReal_/binX_, roiYSizeReal_/binY_, 8*img_.Depth(), pcImgMem, &memPid);
  if (nRet != IS_SUCCESS){                          //could not assign image memory
    printf("IDS_uEye: could not assign the image buffer as the image memory, error %d\n", nRet);
  }


  
  //activate the new image memory
  nRet = is_SetImageMem (hCam, pcImgMem, memPid);
  if (nRet != IS_SUCCESS){                          //could not activate image memory
    printf("IDS_uEye: could not activate image memory, error %d\n", nRet);
  }


  return DEVICE_OK;

}


void CIDS_uEye::TestResourceLocking(const bool recurse)
{
   MMThreadGuard g(*pDemoResourceLock_);
   if(recurse)
      TestResourceLocking(false);
}



