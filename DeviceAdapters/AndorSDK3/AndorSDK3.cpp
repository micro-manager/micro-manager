///////////////////////////////////////////////////////////////////////////////
// FILE:          AndorSDK3Camera.cpp
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
// COPYRIGHT:     University of California, San Francisco, 2006
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
// CVS:           $Id: AndorSDK3Camera.cpp 6819 2011-03-30 18:21:18Z karlh $
//

#include "AndorSDK3.h"
#include "../../MMDevice/ModuleInterface.h"
#include <map>
#include <string>
#include <sstream>
#include <algorithm>
#include <stdexcept>
#include "atcore++.h"
#include "SnapShotControl.h"
#include "EnumProperty.h"
#include "IntegerProperty.h"
#include "FloatProperty.h"
#include "AOIProperty.h"
#include "BooleanProperty.h"
#include "BooleanPropertyWithPoiseControl.h"
#include "ExposureProperty.h"

#ifdef _WINDOWS
#include "atunpacker.h"
#else
#include "atutility.h"
#endif
#include "triggerremapper.h"
#include "AndorSDK3Strings.h"
#include "EventsManager.h"
#include "CallBackManager.h"


using namespace std;
using namespace andor;

const double CAndorSDK3Camera::nominalPixelSizeUm_ = 1.0;
double g_IntensityFactor_ = 1.0;

// External names used used by the rest of the system
// to load particular device from the "AndorSDK3Camera.dll" library
const char * const g_CameraName = "Andor sCMOS Camera";
const char * const g_CameraDeviceDescription = "SDK3 Device Adapter for sCMOS cameras";
const char * const g_Keyword_FirmwareVersion = "CameraFirmware";
const char * const g_Keyword_CameraModel = "CameraModel";
const char * const g_Keyword_SoftwareVersion = "CurrentSoftware";
const char * const g_Keyword_ExtTrigTimeout = "Ext (Exp) Trigger Timeout[ms]";

const char * const g_CameraDefaultBinning = "1x1";

static const unsigned int MAX_NUMBER_DEVICES = 8;
bool DEVICE_IN_USE[MAX_NUMBER_DEVICES] = {false, false, false, false, false, false, false, false};

static const wstring g_RELEASE_2_0_FIRMWARE_VERSION = L"11.1.12.0";
static const wstring g_RELEASE_2_1_FIRMWARE_VERSION = L"11.7.30.0";

static const unsigned int MAX_CHARS_INFO_STRING = 64;
static const unsigned int LENGTH_FIELD_SIZE = 4;
static const unsigned int CID_FIELD_SIZE = 4;

static const unsigned int NUMBER_MDA_BUFFERS = 10;
static const unsigned int NUMBER_LIVE_BUFFERS = 2;


///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////

MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_CameraName, MM::CameraDevice, g_CameraDeviceDescription);
}

MODULE_API MM::Device * CreateDevice(const char * deviceName)
{
   if (deviceName == 0)
      return 0;

   // decide which device class to create based on the deviceName parameter
   if (strcmp(deviceName, g_CameraName) == 0)
   {
      // create camera
      MM::Device * openedDevice = new CAndorSDK3Camera();
      CAndorSDK3Camera * cameraPtr = dynamic_cast<CAndorSDK3Camera *>(openedDevice);
      if (cameraPtr && cameraPtr->GetNumberOfDevicesPresent() > 0)
      {
         return openedDevice;
      }
      else
      {
         delete openedDevice;
      }
   }

   // ...supplied name not recognized
   return 0;
}

MODULE_API void DeleteDevice(MM::Device * pDevice)
{
   delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
// CAndorSDK3Camera implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~

/**
* CAndorSDK3Camera constructor.
* Setup default all variables and create device properties required to exist
* before intialization. In this case, no such properties were required. All
* properties will be created in the Initialize() method.
*
* As a general guideline Micro-Manager devices do not access hardware in the
* the constructor. We should do as little as possible in the constructor and
* perform most of the initialization in the Initialize() method.
*/
CAndorSDK3Camera::CAndorSDK3Camera()
: CCameraBase<CAndorSDK3Camera> (),
  deviceManager(NULL),
  cameraDevice(NULL),
  bufferControl(NULL),
  startAcquisitionCommand(NULL),
  sendSoftwareTrigger(NULL),
  initialized_(false),
  b_cameraPresent_(false),
  number_of_devices_(0),
  deviceInUseIndex_(0),
  sequenceStartTime_(0),
  fpgaTSclockFrequency_(0),
  timeStamp_(0),
  defaultExposureTime_(0.0f),
  pDemoResourceLock_(0),
  image_buffers_(NULL),
  numImgBuffersAllocated_(0),
  currentSeqExposure_(0),
  keep_trying_(false),
  stopOnOverflow_(false)
{
   // call the base class method to set-up default error codes/messages
   InitializeDefaultErrorMessages();
   //Add in some others not currently in base impl, will show in CoreLog/Msgbox on error
   SetErrorText(DEVICE_BUFFER_OVERFLOW, " Circular Buffer Overflow code from MMCore");
   SetErrorText(DEVICE_OUT_OF_MEMORY, " Allocation Failure - out of memory");
   SetErrorText(DEVICE_SNAP_IMAGE_FAILED, " Snap Image Failure");
   
#ifdef TESTRESOURCELOCKING
   pDemoResourceLock_ = new MMThreadLock();
#endif
   thd_ = new MySequenceThread(this);

   // Create an atcore++ device manager
   deviceManager = new TDeviceManager;

   // Open a system device
   IDevice * systemDevice = deviceManager->OpenSystemDevice();
   IInteger * deviceCount = systemDevice->GetInteger(L"DeviceCount");
   SetNumberOfDevicesPresent(static_cast<int>(deviceCount->Get()));
   systemDevice->Release(deviceCount);
   IString * swVersion = systemDevice->GetString(L"SoftwareVersion");
   currentSoftwareVersion_ = swVersion->Get();
   systemDevice->Release(swVersion);
   deviceManager->CloseDevice(systemDevice);
}

/**
* CAndorSDK3Camera destructor.
* If this device used as intended within the Micro-Manager system,
* Shutdown() will be always called before the destructor. But in any case
* we need to make sure that all resources are properly released even if
* Shutdown() was not called.
*/
CAndorSDK3Camera::~CAndorSDK3Camera()
{
   StopSequenceAcquisition();
   delete thd_;
#ifdef TESTRESOURCELOCKING
   delete pDemoResourceLock_;
#endif

   delete deviceManager;
}

/**
* Obtains device name.
* Required by the MM::Device API.
*/
void CAndorSDK3Camera::GetName(char * name) const
{
   // We just return the name we use for referring to this device adapter.
   CDeviceUtils::CopyLimitedString(name, g_CameraName);
}

wstring CAndorSDK3Camera::PerformReleaseVersionCheck()
{
   wstring ws(L"Error retrieving Firmware");
   //Release 2 / 2.1 checks
   try
   {
      IString * firmwareVersion = cameraDevice->GetString(L"FirmwareVersion");
      if (firmwareVersion && firmwareVersion->IsImplemented())
      {
         ws = firmwareVersion->Get();
         if (g_RELEASE_2_0_FIRMWARE_VERSION == ws)
         {
            LogMessage("Warning: Release 2.0 Camera firmware detected! Please upgrade your camera to Release 3");
         }
         else if (g_RELEASE_2_1_FIRMWARE_VERSION == ws)
         {
            LogMessage("Warning: Release 2.1 Camera firmware detected! Please upgrade your camera to Release 3");
         }
      }
      cameraDevice->Release(firmwareVersion);
   }
   catch (NotImplementedException & e)
   {
      LogMessage(e.what());
   }
   return ws;
}


double CAndorSDK3Camera::CalculateDefaultExposure(wstring & interfaceType)
{
   double d_retValue = 0.0340f;
   if (0 == interfaceType.compare(L"CL 10 Tap") )
   {
      d_retValue = 0.0100f;
   }
   return d_retValue;
}

CAndorSDK3Camera::CameraId CAndorSDK3Camera::DetermineCameraId(wstring & cameraSerialCheck)
{
   CameraId id = CIDNeo;
   try {
      cameraSerialCheck.erase(4);
      if (0 == cameraSerialCheck.compare(L"VSC-") )
      {
         id = CIDZyla;
      }
	  else if (0 == cameraSerialCheck.compare(L"ISC-"))
	  {
		  id = CIDiStar;
	  }
   }
   catch (const std::out_of_range&)
   {
   }
   return id;
}

std::string CAndorSDK3Camera::GenerateCameraName(unsigned cameraID, wstring & cameraModelCheck)
{
   string s_cameraName("Neo 5.5 "); 
   if (CIDZyla == cameraID)
   {
      try {
         cameraModelCheck.erase(8);
      }
      catch (const std::out_of_range&)
      {
         cameraModelCheck.erase();
      }

      s_cameraName = "Zyla 5.5 ";
      if (0 == cameraModelCheck.compare(L"ZYLA-4.2") )
      {
         s_cameraName = "Zyla 4.2 ";
      }
   }
   else if (CIDiStar == cameraID)
   {
	   s_cameraName = "iStar-sCMOS ";
   }
   
   return s_cameraName;
}

void CAndorSDK3Camera::InitialiseSDK3Defaults()
{
   IEnum * e_feature = NULL;
   IInteger * i_feature = NULL;
   IFloat * f_feature = NULL;
   IBool * b_feature = NULL;
   try
   {
      //Sensor cooling mode on
      b_feature = cameraDevice->GetBool(L"SensorCooling");
      b_feature->Set(true);
      cameraDevice->Release(b_feature);
      b_feature = NULL;
      e_feature = cameraDevice->GetEnum(L"ElectronicShutteringMode");
      e_feature->Set(L"Rolling");
      cameraDevice->Release(e_feature);
      e_feature = NULL;
      e_feature = cameraDevice->GetEnum(L"SimplePreAmpGainControl");
      e_feature->Set(L"11-bit (low noise)");
      cameraDevice->Release(e_feature);
      e_feature = NULL;
      //Image Size
      i_feature = cameraDevice->GetInteger(L"AOIWidth");
      i_feature->Set(i_feature->Max());
      cameraDevice->Release(i_feature);
      i_feature = NULL;
      i_feature = cameraDevice->GetInteger(L"AOILeft");
      i_feature->Set(i_feature->Min());
      cameraDevice->Release(i_feature);
      i_feature = NULL;
      i_feature = cameraDevice->GetInteger(L"AOIHeight");
      i_feature->Set(i_feature->Max());
      cameraDevice->Release(i_feature);
      i_feature = NULL;
      i_feature = cameraDevice->GetInteger(L"AOITop");
      i_feature->Set(i_feature->Min());
      cameraDevice->Release(i_feature);
      i_feature = NULL;
      //more enums - readout rate and trigger mode
      e_feature = cameraDevice->GetEnum(L"PixelReadoutRate");
      e_feature->Set(L"280 MHz");
      cameraDevice->Release(e_feature);
      e_feature = NULL;
      e_feature = cameraDevice->GetEnum(L"TriggerMode");
      e_feature->Set(L"Internal");
      cameraDevice->Release(e_feature);
      e_feature = NULL;
      //Overlap mode
      b_feature = cameraDevice->GetBool(L"Overlap");
      b_feature->Set(true);
      cameraDevice->Release(b_feature);
      b_feature = NULL;
      //Exposure
      f_feature = cameraDevice->GetFloat(L"ExposureTime");
      f_feature->Set(GetDefaultExpsoure());
      cameraDevice->Release(f_feature);
      f_feature = NULL;
      //MetaData / TimeStamp enable
      b_feature = cameraDevice->GetBool(L"MetadataEnable");
      b_feature->Set(true);
      cameraDevice->Release(b_feature);
      b_feature = NULL;
      b_feature = cameraDevice->GetBool(L"MetadataTimestamp");
      b_feature->Set(true);
      cameraDevice->Release(b_feature);
      b_feature = NULL;
      //TimestampClockFrequency
      i_feature = cameraDevice->GetInteger(L"TimestampClockFrequency");
      fpgaTSclockFrequency_ = i_feature->Get();
      cameraDevice->Release(i_feature);
      i_feature = NULL;
   }
   catch (exception & e)
   {
      string s("[InitialiseSDK3Defaults] Caught Exception with message: ");
      s += e.what();
      LogMessage(s);
   }
   cameraDevice->Release(b_feature);
   cameraDevice->Release(f_feature);
   cameraDevice->Release(i_feature);
   cameraDevice->Release(e_feature);
}


/**
* Intializes the hardware.
* Required by the MM::Device API.
* Typically we access and initialize hardware at this point.
* Device properties are typically created here as well, except
* the ones we need to use for defining initialization parameters.
* Such pre-initialization properties are created in the constructor.
* (This device does not have any pre-initialization properties)
*/
int CAndorSDK3Camera::Initialize()
{
   if (initialized_)
      return DEVICE_OK;

   if (0 == GetNumberOfDevicesPresent())
   {
      initialized_ = false;
      return DEVICE_NOT_CONNECTED;
   }

   for (int i = 0; i < GetNumberOfDevicesPresent(); i++)
   {
      if (!DEVICE_IN_USE[i])
      {
         try
         {
           cameraDevice = deviceManager->OpenDevice(i);
         }
         catch (exception & e)
         {
            LogMessage(e.what());
            continue;
         }
         IString * cameraFamilyString = cameraDevice->GetString(L"CameraFamily");
         std::wstring temp_ws = cameraFamilyString->Get();
         cameraDevice->Release(cameraFamilyString);
         if (temp_ws.compare(L"Andor sCMOS") == 0)
         {
            b_cameraPresent_ = true;
            DEVICE_IN_USE[i] = true;
            deviceInUseIndex_ = i;
            break;
         }
         else
         {
            deviceManager->CloseDevice(cameraDevice);
            cameraDevice = NULL;
            if (temp_ws.compare(L"Andor Apogee") == 0) {
              return DEVICE_NOT_SUPPORTED;
            }
         }
      }
   }

   if (cameraDevice == NULL)
   {
      return DEVICE_NOT_CONNECTED;
   }

   // Description
   int ret = CreateProperty(MM::g_Keyword_Description, g_CameraDeviceDescription, MM::String, true);
   assert(DEVICE_OK == ret);

   //Camera Firmware
   wstring temp_ws = PerformReleaseVersionCheck();
   char * p_cameraInfoString = new char[MAX_CHARS_INFO_STRING];
   memset(p_cameraInfoString, 0, MAX_CHARS_INFO_STRING);
   wcstombs(p_cameraInfoString, temp_ws.c_str(), temp_ws.size());
   ret = CreateProperty(g_Keyword_FirmwareVersion, p_cameraInfoString, MM::String, true);
   assert(DEVICE_OK == ret);
   
   if (GetCameraPresent())
   {
      bufferControl = cameraDevice->GetBufferControl();
      startAcquisitionCommand = cameraDevice->GetCommand(L"AcquisitionStart");
      sendSoftwareTrigger = cameraDevice->GetCommand(L"SoftwareTrigger");
#ifdef linux
      AT_InitialiseUtilityLibrary();
#endif
   }
   else
   {
      return DEVICE_NOT_YET_IMPLEMENTED;
   }

   // CameraID(Serial))
   IString * cameraSerialNumber = cameraDevice->GetString(L"SerialNumber");
   temp_ws = cameraSerialNumber->Get();
   memset(p_cameraInfoString, 0, MAX_CHARS_INFO_STRING);
   wcstombs(p_cameraInfoString, temp_ws.c_str(), temp_ws.size());
   cameraDevice->Release(cameraSerialNumber);
   ret = CreateProperty(MM::g_Keyword_CameraID, p_cameraInfoString, MM::String, true);
   assert(DEVICE_OK == ret);
   wstring cameraSerialCheck(temp_ws);

   // Camera Model
   IString * cameraModel = cameraDevice->GetString(L"CameraModel");
   temp_ws = cameraModel->Get();
   memset(p_cameraInfoString, 0, MAX_CHARS_INFO_STRING);
   wcstombs(p_cameraInfoString, temp_ws.c_str(), temp_ws.size());
   cameraDevice->Release(cameraModel);
   ret = CreateProperty(g_Keyword_CameraModel, p_cameraInfoString, MM::String, true);
   assert(DEVICE_OK == ret);
   wstring cameraModelCheck(temp_ws);

   //Name and Interface type
   IString * cameraInterfaceType = cameraDevice->GetString(L"InterfaceType");
   temp_ws = cameraInterfaceType->Get();
   memset(p_cameraInfoString, 0, MAX_CHARS_INFO_STRING);
   wcstombs(p_cameraInfoString, temp_ws.c_str(), temp_ws.size());
   cameraDevice->Release(cameraInterfaceType);
   
   SetDefaultExpsoure(CalculateDefaultExposure(temp_ws));

   string s_cameraName = GenerateCameraName(DetermineCameraId(cameraSerialCheck), cameraModelCheck) + p_cameraInfoString;
   ret = CreateProperty(MM::g_Keyword_CameraName, s_cameraName.c_str(), MM::String, true);
   assert(DEVICE_OK == ret);

   // Current Software Version running
   memset(p_cameraInfoString, 0, MAX_CHARS_INFO_STRING);
   wcstombs(p_cameraInfoString, currentSoftwareVersion_.c_str(), currentSoftwareVersion_.size());
   ret = CreateProperty(g_Keyword_SoftwareVersion, p_cameraInfoString, MM::String, true);
   assert(DEVICE_OK == ret);

   ret = CreateProperty(g_Keyword_ExtTrigTimeout, "5000", MM::Integer, false);
   assert(DEVICE_OK == ret);

   delete [] p_cameraInfoString;

   InitialiseSDK3Defaults();

   //Create event manager and snapshot controller here

   eventsManager_ = new CEventsManager(cameraDevice);
   snapShotController_ = new SnapShotControl(cameraDevice, eventsManager_);
   callbackManager_ = new CCallBackManager(this, thd_, snapShotController_);

   // Properties
   electronicShutteringMode_property = new TEnumProperty(TAndorSDK3Strings::ELECTRONIC_SHUTTERING_MODE,
                                                         cameraDevice->GetEnum(L"ElectronicShutteringMode"), this, 
                                                         thd_, snapShotController_, false, false);

   preAmpGain_property = new TEnumProperty(TAndorSDK3Strings::GAIN_TEXT, 
                                           cameraDevice->GetEnum(L"SimplePreAmpGainControl"),
                                           this, thd_, snapShotController_, false, true);

   pixelEncoding_property = new TEnumProperty(TAndorSDK3Strings::PIXEL_ENCODING,
                                              cameraDevice->GetEnum(L"PixelEncoding"), this, thd_, 
                                              snapShotController_, true, false);

   binning_property = new TEnumProperty(MM::g_Keyword_Binning, cameraDevice->GetEnum(L"AOIBinning"),
                                        this, thd_, snapShotController_, false, false);
   //To support Rel2.1 cameras if no binning, always have 1x1
   AddAllowedValue(MM::g_Keyword_Binning, g_CameraDefaultBinning);

   aoi_property_ = new TAOIProperty(TAndorSDK3Strings::ACQUISITION_AOI, callbackManager_, false);

   pixelReadoutRate_property = new TEnumProperty(TAndorSDK3Strings::PIXEL_READOUT_RATE,
                                                 cameraDevice->GetEnum(L"PixelReadoutRateMapper"),
                                                 this, thd_, snapShotController_, false, false);

   temperatureControl_property = new TEnumProperty(TAndorSDK3Strings::TEMPERATURE_CONTROL,
                                                   cameraDevice->GetEnum(L"TemperatureControl"), this, thd_, 
                                                   snapShotController_, CIDZyla==DetermineCameraId(cameraSerialCheck), false);

   accumulationLength_property = new TIntegerProperty(TAndorSDK3Strings::ACCUMULATE_COUNT,
                                                      cameraDevice->GetInteger(L"AccumulateCount"), this, thd_, 
                                                      snapShotController_, false, false);

   temperatureStatus_property = new TEnumProperty(TAndorSDK3Strings::TEMPERATURE_STATUS,
                                                  cameraDevice->GetEnum(L"TemperatureStatus"), this, thd_, 
                                                  snapShotController_, true, false);

   fanSpeed_property = new TEnumProperty(TAndorSDK3Strings::FAN_SPEED, cameraDevice->GetEnum(L"FanSpeed"), this,
                                         thd_, snapShotController_, false, false);

   spuriousNoiseFilter_property = new TBooleanProperty(TAndorSDK3Strings::SPURIOUS_NOISE_FILTER,
                                                       cameraDevice->GetBool(L"SpuriousNoiseFilter"),
                                                       callbackManager_, false);

   staticBlemishCorrection_property = new TBooleanProperty(TAndorSDK3Strings::STATIC_BLEMISH_CORRECTION,
                                                       cameraDevice->GetBool(L"StaticBlemishCorrection"),
                                                       callbackManager_, false);

   sensorCooling_property = new TBooleanProperty(TAndorSDK3Strings::SENSOR_COOLING, 
                                                 cameraDevice->GetBool(L"SensorCooling"), callbackManager_, false);

   rollingShutterGlobalClear_property = new TBooleanProperty(TAndorSDK3Strings::GLOBAL_CLEAR, 
                                                 cameraDevice->GetBool(L"RollingShutterGlobalClear"), callbackManager_, false);

   triggerMode_Enum = cameraDevice->GetEnum(L"TriggerMode");
   triggerMode_remapper = new TTriggerRemapper(snapShotController_, triggerMode_Enum);
   std::map<std::wstring, std::wstring> triggerMode_map;
   triggerMode_map[L"Software"] = L"Software (Recommended for Live Mode)";
   triggerMode_map[L"Internal"] = L"Internal (Recommended for fast acquisitions)";
   triggerMode_valueMapper = new TAndorEnumValueMapper(
      triggerMode_remapper, triggerMode_map);
   
   triggerMode_property = new TEnumProperty(TAndorSDK3Strings::TRIGGER_MODE, triggerMode_valueMapper,
                                            this, thd_, snapShotController_, false, false);

   gateMode_property = new TEnumProperty(TAndorSDK3Strings::GATE_MODE, cameraDevice->GetEnum(L"GateMode"),
                                            this, thd_, snapShotController_, false, false);

   readTemperature_property = new TFloatProperty(TAndorSDK3Strings::SENSOR_TEMPERATURE, 
                                                 cameraDevice->GetFloat(L"SensorTemperature"), 
                                                 callbackManager_, true, false);

   overlap_property = new TBooleanPropertyWithPoiseControl(TAndorSDK3Strings::OVERLAP, cameraDevice->GetBool(L"Overlap"),
                                           callbackManager_, false);

   exposureTime_property = new TExposureProperty(MM::g_Keyword_Exposure,
                                       new TAndorFloatValueMapper(cameraDevice->GetFloat(L"ExposureTime"), 1000),
                                       callbackManager_, false, false);
   
   frameRateLimits_property = new TFloatStringProperty(TAndorSDK3Strings::FRAME_RATE_LIMITS, 
                                                 cameraDevice->GetFloat(L"FrameRate"), callbackManager_, true, true);
   
   frameRate_property = new TFloatProperty(TAndorSDK3Strings::FRAME_RATE, 
                                             new TAndorFloatCache(cameraDevice->GetFloat(L"FrameRate")),  
                                             callbackManager_, false, true);
   //Aux TTL
   auxOutSignal_property = new TEnumProperty(TAndorSDK3Strings::AUX_SOURCE, 
                                             cameraDevice->GetEnum(L"AuxiliaryOutSource"), 
                                             this, thd_, snapShotController_, false, false);
   auxOutTwoSignal_property = new TEnumProperty(TAndorSDK3Strings::AUX_SOURCE_TWO, 
                                             cameraDevice->GetEnum(L"AuxOutSourceTwo"), 
                                             this, thd_, snapShotController_, false, false);
   shutterOutputMode_property = new TEnumProperty("ShutterOutputMode", 
                                             cameraDevice->GetEnum(L"ShutterOutputMode"), 
                                             this, thd_, snapShotController_, false, false);

   shutterTransferTime_property = new TFloatProperty("ShutterTransferTime [s]", 
                                             cameraDevice->GetFloat(L"ShutterTransferTime"),  
                                             callbackManager_, false, true);

   LSPSensorReadoutMode_property = new TEnumProperty("LightScanPlus-SensorReadoutMode", 
                                            cameraDevice->GetEnum(L"SensorReadoutMode"), 
                                            this, thd_, snapShotController_, false, false);

   LSPSequentialPortReadoutMode_property = new TBooleanProperty("LightScanPlus-AlternatingReadoutDirection", 
                                             cameraDevice->GetBool(L"AlternatingReadoutDirection"), 
                                             callbackManager_, false);

   LSPExposedPixelHeight_property = new TIntegerProperty("LightScanPlus-ExposedPixelHeight",
                                             cameraDevice->GetInteger(L"ExposedPixelHeight"), this, thd_, 
                                             snapShotController_, false, false);

   LSPScanSpeedControlEnable_property = new TBooleanProperty("LightScanPlus-ScanSpeedControlEnable", 
                                             cameraDevice->GetBool(L"ScanSpeedControlEnable"),
                                             callbackManager_, false);

   LSPLineScanSpeed_property =  new TFloatProperty("LightScanPlus-LineScanSpeed [lines/sec]", 
                                             cameraDevice->GetFloat(L"LineScanSpeed"),  
                                             callbackManager_, false, false);

   LSPRowReadTime_property = new TFloatProperty("LightScanPlus-RowReadTime", 
                                             cameraDevice->GetFloat(L"RowReadTime"),  
                                             callbackManager_, false, false);

   LSPExternalTriggerDelay_Property = new TFloatProperty("LightScanPlus-ExternalTriggerDelay [s]", 
                                             cameraDevice->GetFloat(L"ExternalTriggerDelay"), 
                                             callbackManager_, false, false);

   pretrigger_property = new TBooleanProperty(TAndorSDK3Strings::PRETRIGGER, cameraDevice->GetBool(L"PreTriggerEnable"), 
											callbackManager_, false);
											
   piv_property = new TBooleanProperty(TAndorSDK3Strings::PIV, cameraDevice->GetBool(L"PIVEnable"), 
											callbackManager_, false);

   MCPIntelligate_property = new TBooleanProperty(TAndorSDK3Strings::MCP_INTELLIGATE, cameraDevice->GetBool(L"MCPIntelligate"), 
											callbackManager_, false);

   MCPGain_property = new TIntegerProperty(TAndorSDK3Strings::MCP_GAIN,
                                                      cameraDevice->GetInteger(L"MCPGain"), this, thd_, 
                                                      snapShotController_, false, false);

   MCPVoltage_property = new TIntegerProperty(TAndorSDK3Strings::MCP_VOLTAGE,
                                                      cameraDevice->GetInteger(L"MCPVoltage"), this, thd_, 
                                                      snapShotController_, false, false);

   DDGIOCEnable_property = new TBooleanProperty(TAndorSDK3Strings::DDG_IOC_ENABLE, cameraDevice->GetBool(L"DDGIOCEnable"), 
											callbackManager_, false);

   InsertionDelay_property = new TEnumProperty(TAndorSDK3Strings::INSERTION_DELAY, cameraDevice->GetEnum(L"InsertionDelay"),
                                            this, thd_, snapShotController_, false, true);

   DDGIOCNumberOfPulses_property = new TIntegerProperty(TAndorSDK3Strings::DDG_IOC_NUMBER_OF_PULSES,
                                             cameraDevice->GetInteger(L"DDGIOCNumberOfPulses"), this, thd_, 
                                             snapShotController_, false, true);

   DDGIOCPeriod_property = new TIntegerProperty(TAndorSDK3Strings::DDG_IOC_PERIOD,
                                             cameraDevice->GetInteger(L"DDGIOCPeriod"), this, thd_, 
                                             snapShotController_, false, true);

   DDGIOCOutputDelay_property = new TIntegerProperty(TAndorSDK3Strings::DDG_OUTPUT_DELAY,
                                             cameraDevice->GetInteger(L"DDGOutputDelay"), this, thd_, 
                                             snapShotController_, false, true);

   DDGOutputEnable_property = new TBooleanProperty(TAndorSDK3Strings::DDG_OUTPUT_ENABLE, 
                                             cameraDevice->GetBool(L"DDGOutputEnable"),
                                             callbackManager_, false);

   DDGOutputStepEnable_property = new TBooleanProperty(TAndorSDK3Strings::DDG_OUTPUT_STEP_ENABLE, 
                                             cameraDevice->GetBool(L"DDGOutputStepEnable"),
                                             callbackManager_, false);

   DDGStepEnabled_property = new TBooleanProperty(TAndorSDK3Strings::DDG_STEP_ENABLED, 
                                             cameraDevice->GetBool(L"DDGStepEnabled"),
                                             callbackManager_, true);

   DDGOpticalWidthEnable_property = new TBooleanProperty(TAndorSDK3Strings::DDG_OPTICAL_WIDTH_ENABLE, 
                                             cameraDevice->GetBool(L"DDGOpticalWidthEnable"),
                                             callbackManager_, false);

   DDGOutputPolarity_property = new TEnumProperty(TAndorSDK3Strings::DDG_OUTPUT_POLARITY, cameraDevice->GetEnum(L"DDGOutputPolarity"),
                                            this, thd_, snapShotController_, false, true);

   DDGOutputSelector_property = new TEnumProperty(TAndorSDK3Strings::DDG_OUTPUT_SELECTOR, cameraDevice->GetEnum(L"DDGOutputSelector"),
                                            this, thd_, snapShotController_, false, true);

   DDGOutputWidth_property = new TIntegerProperty(TAndorSDK3Strings::DDG_OUTPUT_WIDTH,
                                             cameraDevice->GetInteger(L"DDGOutputWidth"), this, thd_, 
                                             snapShotController_, false, true);

   DDGStepCount_property = new TIntegerProperty(TAndorSDK3Strings::DDG_STEP_COUNT,
                                             cameraDevice->GetInteger(L"DDGStepCount"), this, thd_, 
                                             snapShotController_, false, true);

   DDGStepDelayCoefficientA_property = new TFloatProperty(TAndorSDK3Strings::DDG_STEP_DELAY_COEFFICIENT_A, 
                                             cameraDevice->GetFloat(L"DDGStepDelayCoefficientA"), 
                                             callbackManager_, false, true);

   DDGStepDelayCoefficientB_property = new TFloatProperty(TAndorSDK3Strings::DDG_STEP_DELAY_COEFFICIENT_B, 
                                             cameraDevice->GetFloat(L"DDGStepDelayCoefficientB"), 
                                             callbackManager_, false, true);

   DDGStepDelayMode_property = new TEnumProperty(TAndorSDK3Strings::DDG_STEP_DELAY_MODE, cameraDevice->GetEnum(L"DDGStepDelayMode"),
                                            this, thd_, snapShotController_, false, false);

   DDGStepWidthCoefficientA_property = new TFloatProperty(TAndorSDK3Strings::DDG_STEP_WIDTH_COEFFICIENT_A, 
                                             cameraDevice->GetFloat(L"DDGStepWidthCoefficientA"), 
                                             callbackManager_, false, true);

   DDGStepWidthCoefficientB_property = new TFloatProperty(TAndorSDK3Strings::DDG_STEP_WIDTH_COEFFICIENT_B, 
                                             cameraDevice->GetFloat(L"DDGStepWidthCoefficientB"), 
                                             callbackManager_, false, true);

   DDGStepWidthMode_property = new TEnumProperty(TAndorSDK3Strings::DDG_STEP_WIDTH_MODE, cameraDevice->GetEnum(L"DDGStepWidthMode"),
                                            this, thd_, snapShotController_, false, true);

   char errorStr[MM::MaxStrLength];
   if (false == eventsManager_->Initialise(errorStr) )
   {
      LogMessage(errorStr);
   }

   initialized_ = true;
   ResizeImageBuffer();
   try {
      snapShotController_->poiseForSnapShot();
   }
   catch (ComException & e) {
      string s("[Initialize] ComException thrown: ");
      s += e.what();
      LogMessage(s);
      return DEVICE_ERR;
   }
   catch (exception & e)
   {
      string s("[Initialize] Caught Exception with message: ");
      s += e.what();
      LogMessage(s);
      return DEVICE_ERR;
   }
   
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
int CAndorSDK3Camera::Shutdown()
{
   int retCode = DEVICE_OK;
   if (initialized_)
   {
      try {
         snapShotController_->leavePoisedMode();
      }
      catch (ComException & e) {
         LogMessage(e.what());
         retCode = DEVICE_ERR;
      }
      catch (exception & e) {
         string s("[Shutdown] Caught Exception with message: ");
         s += e.what();
         LogMessage(s);
         retCode = DEVICE_ERR;
      }
      delete binning_property;
      delete preAmpGain_property;
      delete electronicShutteringMode_property;
      delete temperatureControl_property;
      delete pixelReadoutRate_property;
      delete pixelEncoding_property;

      delete accumulationLength_property;
      delete readTemperature_property;
      delete temperatureStatus_property;
      delete rollingShutterGlobalClear_property;
      delete sensorCooling_property;
      delete overlap_property;
	  delete pretrigger_property;
	  delete piv_property;
      delete frameRate_property;
      delete frameRateLimits_property;
      delete fanSpeed_property;
      delete spuriousNoiseFilter_property;
	    delete staticBlemishCorrection_property;
      delete aoi_property_;
      delete triggerMode_property;
	  delete gateMode_property;
      delete exposureTime_property;
      delete auxOutSignal_property;
      delete auxOutTwoSignal_property;
      delete shutterOutputMode_property;
      delete shutterTransferTime_property;
      delete LSPSensorReadoutMode_property;
      delete LSPSequentialPortReadoutMode_property;
      delete LSPExposedPixelHeight_property;
      delete LSPScanSpeedControlEnable_property;
      delete LSPLineScanSpeed_property;
      delete LSPRowReadTime_property;
      delete LSPExternalTriggerDelay_Property;
	    delete MCPGain_property;
	    delete MCPVoltage_property;
	    delete MCPIntelligate_property;
	    delete InsertionDelay_property;
	    delete DDGIOCEnable_property;
	    delete DDGIOCNumberOfPulses_property;
	    delete DDGIOCPeriod_property;
	    delete DDGIOCOutputDelay_property;
	    delete DDGOutputEnable_property;
	    delete DDGOutputStepEnable_property;
	    delete DDGStepEnabled_property;
	    delete DDGOpticalWidthEnable_property;
	    delete DDGOutputPolarity_property;
	    delete DDGOutputSelector_property;
	    delete DDGOutputWidth_property;
	    delete DDGStepCount_property;
	    delete DDGStepDelayCoefficientA_property;
	    delete DDGStepDelayCoefficientB_property;
	    delete DDGStepDelayMode_property;
	    delete DDGStepWidthCoefficientA_property;
	    delete DDGStepWidthCoefficientB_property;
	    delete DDGStepWidthMode_property;

      delete callbackManager_;
      delete snapShotController_;
      // clean up objects used by the property browser
      delete triggerMode_remapper;
      cameraDevice->Release(triggerMode_Enum);
      delete eventsManager_;

      // Clean up atcore++ stuff
      cameraDevice->ReleaseBufferControl(bufferControl);
      cameraDevice->Release(startAcquisitionCommand);
      cameraDevice->Release(sendSoftwareTrigger);
#ifdef linux
      AT_FinaliseUtilityLibrary();
#endif
      deviceManager->CloseDevice(cameraDevice);
      DEVICE_IN_USE[deviceInUseIndex_] = false;
   }

   initialized_ = false;
   return retCode;
}

void CAndorSDK3Camera::UnpackDataWithPadding(unsigned char * _pucSrcBuffer)
{
   wstring ws_pixelEncoding(L"Mono12Packed");
   
   if (!snapShotController_->isMono12Packed())
   {
      ws_pixelEncoding = L"Mono16";
   }
   
   MMThreadGuard g(imgPixelsLock_);
   unsigned char * pucDstData = const_cast<unsigned char *>(img_.GetPixels());
#ifdef _WINDOWS
   unsigned int ret_code = AT_UnpackBuffer(_pucSrcBuffer, pucDstData, aoi_property_->GetWidth(), aoi_property_->GetHeight(), 
                                          aoi_property_->GetStride(), ws_pixelEncoding.c_str(), L"Mono16", 0);
#else
   unsigned int ret_code = AT_ConvertBuffer(_pucSrcBuffer, pucDstData, aoi_property_->GetWidth(), aoi_property_->GetHeight(), 
                                          aoi_property_->GetStride(), ws_pixelEncoding.c_str(), L"Mono16");
#endif                                          
   if (AT_SUCCESS != ret_code)
   {
      stringstream ss;
      ss << "[UnpackDataWithPadding] failed with code: " << ret_code << endl;
      LogMessage(ss.str().c_str());
   }
}


/**
* Performs exposure and grabs a single image.
* This function should block during the actual exposure and return immediately afterwards 
* (i.e., before readout).  This behavior is needed for proper synchronization with the shutter.
* Required by the MM::Camera API.
*/
int CAndorSDK3Camera::SnapImage()
{
   int ret = (snapShotController_->takeSnapShot() ? DEVICE_OK : DEVICE_SNAP_IMAGE_FAILED);

   return ret;
}


/**
* Returns pixel data.
* Required by the MM::Camera API.
* The calling program will assume the size of the buffer based on the values
* obtained from GetImageBufferSize(), which in turn should be consistent with
* values returned by GetImageWidth(), GetImageHight() and GetImageBytesPerPixel().
* The calling program allso assumes that camera never changes the size of
* the pixel buffer on its own. In other words, the buffer can change only if
* appropriate properties are set (such as binning, pixel type, etc.)
*/
const unsigned char * CAndorSDK3Camera::GetImageBuffer()
{
   unsigned char * return_buffer = NULL;

   snapShotController_->getData(return_buffer);
   
   if (return_buffer)
   {
      timeStamp_ = GetTimeStamp(return_buffer);
      UnpackDataWithPadding(return_buffer);
   }

   MMThreadGuard g(imgPixelsLock_);

   return img_.GetPixels();
}

/**
* Returns image buffer X-size in pixels.
* Required by the MM::Camera API.
*/
unsigned CAndorSDK3Camera::GetImageWidth() const
{
   return static_cast<unsigned>(aoi_property_->GetWidth());
}

/**
* Returns image buffer Y-size in pixels.
* Required by the MM::Camera API.
*/
unsigned CAndorSDK3Camera::GetImageHeight() const
{
   return static_cast<unsigned>(aoi_property_->GetHeight());
}

/**
* Returns image buffer pixel depth in bytes.
* Required by the MM::Camera API.
*/
unsigned CAndorSDK3Camera::GetImageBytesPerPixel() const
{
   return aoi_property_->GetBytesPerPixel();
}

/**
* Returns the bit depth (dynamic range) of the pixel.
* This does not affect the buffer size, it just gives the client application
* a guideline on how to interpret pixel values.
* Required by the MM::Camera API.
*/
unsigned CAndorSDK3Camera::GetBitDepth() const
{
   IEnum * pBitDepth = cameraDevice->GetEnum(L"BitDepth");
   wstring ws_bitdepth = pBitDepth->GetStringByIndex(pBitDepth->GetIndex());
   ws_bitdepth.erase(2);
   cameraDevice->Release(pBitDepth);
   unsigned ret_bitDepth = 11;
   wstringstream wss(ws_bitdepth);
   wss >> ret_bitDepth;
   return ret_bitDepth;
}

/**
* Returns the size in bytes of the image buffer.
* Required by the MM::Camera API.
*/
long CAndorSDK3Camera::GetImageBufferSize() const
{
   return img_.Width() * img_.Height() * img_.Depth();
}


int CAndorSDK3Camera::ResizeImageBuffer()
{
   if (initialized_)
   {
      if (GetImageBytesPerPixel() == img_.Depth() )
      {
         //This memsets the new size to 0 - if any issues occur,
         // a blank image will be shown as opposed to corrupt image.
         img_.Resize(GetImageWidth(), GetImageHeight() );
      }
      else
      {
         img_.Resize(GetImageWidth(), GetImageHeight(), GetImageBytesPerPixel() );
      }
   }
   return DEVICE_OK;
}


/**
* Sets the camera Region Of Interest.
* Required by the MM::Camera API.
* This command will change the dimensions of the image.
* Depending on the hardware capabilities the camera may not be able to configure the
* exact dimensions requested - but should try do as close as possible.
* If the hardware does not have this capability the software should simulate the ROI by
* appropriately cropping each frame.
* @param x - top-left corner coordinate - Left
* @param y - top-left corner coordinate - Top
* @param xSize - width
* @param ySize - height
*/
int CAndorSDK3Camera::SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize)
{
   if (xSize == 0 && ySize == 0)
   {
      ClearROI();
   }
   else
   {
      x += 1;
      y += 1;
      //Adjust for binning
      int binning = GetBinning();
      x *= binning;
      y *= binning;

      const char* propStrValue = aoi_property_->SetCustomAOISize(x, y, xSize, ySize);
      ResizeImageBuffer();
      this->OnPropertyChanged(TAndorSDK3Strings::ACQUISITION_AOI.c_str(), propStrValue);
   }
   return DEVICE_OK;
}

/**
* Returns the actual dimensions of the current ROI.
* Required by the MM::Camera API.
*/
int CAndorSDK3Camera::GetROI(unsigned & x, unsigned & y, unsigned & xSize, unsigned & ySize)
{
   //Adjust for binning
   int binning = GetBinning();
   x = static_cast<unsigned>(aoi_property_->GetLeftOffset() / binning);
   y = static_cast<unsigned>(aoi_property_->GetTopOffset() / binning);

   //Micro-Manager image dims are zero based
   x -= 1;
   y -= 1;

   xSize = static_cast<unsigned>(aoi_property_->GetWidth());
   ySize = static_cast<unsigned>(aoi_property_->GetHeight());

   return DEVICE_OK;
}

/**
* Resets the Region of Interest to full frame.
* Required by the MM::Camera API.
*/
int CAndorSDK3Camera::ClearROI()
{
   const char * propStrValue = aoi_property_->ResetToFullImage();
   this->OnPropertyChanged(TAndorSDK3Strings::ACQUISITION_AOI.c_str(), propStrValue);
   return DEVICE_OK;
}

/**
* Returns the current exposure setting in milliseconds.
* Required by the MM::Camera API.
*/
double CAndorSDK3Camera::GetExposure() const
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
void CAndorSDK3Camera::SetExposure(double exp)
{
   SetProperty(MM::g_Keyword_Exposure, CDeviceUtils::ConvertToString(exp));
}

/**
* Returns the current binning factor.
* Required by the MM::Camera API.
*/
int CAndorSDK3Camera::GetBinning() const
{
   char buf[MM::MaxStrLength];
   int ret = GetProperty(MM::g_Keyword_Binning, buf);
   if (ret != DEVICE_OK)
      return 1;
   return atoi(&buf[0]);
}

/**
* Sets binning factor.
* Required by the MM::Camera API.
*/
int CAndorSDK3Camera::SetBinning(int binF)
{
   return SetProperty(MM::g_Keyword_Binning, CDeviceUtils::ConvertToString(binF));
}


/**
 * Required by the MM::Camera API
 * Please implement this yourself and do not rely on the base class implementation
 * The Base class implementation is deprecated and will be removed shortly
 */
int CAndorSDK3Camera::StartSequenceAcquisition(double interval)
{
   return StartSequenceAcquisition(LONG_MAX, interval, false);
}

/**                                                                       
* Stop and wait for the Sequence thread finished                                   
*/
int CAndorSDK3Camera::StopSequenceAcquisition()
{
   if (!thd_->IsStopped())
   {
      thd_->Stop();
      keep_trying_ = false;
      thd_->wait();
   }

   return DEVICE_OK;
}

bool CAndorSDK3Camera::InitialiseDeviceCircularBuffer(const unsigned numBuffers)
{
   bool b_ret = false;
   IInteger* imageSizeBytes = NULL;
   numImgBuffersAllocated_ = 0;
   try
   {
      imageSizeBytes = cameraDevice->GetInteger(L"ImageSizeBytes");
      AT_64 ImageSize = imageSizeBytes->Get();
      image_buffers_ = new unsigned char * [numBuffers];
      for (unsigned int i = 0; i < numBuffers; i++)
      {
         image_buffers_[i] = new unsigned char[static_cast<int>(ImageSize)];
         memset(image_buffers_[i], 0, static_cast<int>(ImageSize));
         ++numImgBuffersAllocated_;
         bufferControl->Queue(image_buffers_[i], static_cast<int>(ImageSize));
      }
      cameraDevice->Release(imageSizeBytes);
      b_ret = true;
   }
   catch (bad_alloc & ba)
   {
      string s("[InitialiseDeviceCircularBuffer] Caught Bad Allocation with message: ");
      s += ba.what();
      LogMessage(s);
      b_ret = false;
   }
   catch (exception & e)
   {
      string s("[InitialiseDeviceCircularBuffer] Caught Exception with message: ");
      s += e.what();
      LogMessage(s);
      b_ret = false;
      cameraDevice->Release(imageSizeBytes);
   }
   return b_ret;
}

bool CAndorSDK3Camera::CleanUpDeviceCircularBuffer()
{
   if (image_buffers_ != NULL)
   {
      for (unsigned int i = 0; i < numImgBuffersAllocated_; i++)
      {
         delete [] image_buffers_[i];
      }
      delete [] image_buffers_;
      image_buffers_ = NULL;
   }
   return true;
}

int CAndorSDK3Camera::SetupCameraForSeqAcquisition(long numImages)
{
   int retCode = DEVICE_OK;
   bool b_memOkRet = false;
   IEnum * cycleMode = cameraDevice->GetEnum(L"CycleMode");

   if (LONG_MAX != numImages)
   {
      cycleMode->Set(L"Fixed");
      IInteger * frameCount = cameraDevice->GetInteger(L"FrameCount");
      frameCount->Set(numImages);
      cameraDevice->Release(frameCount);
      b_memOkRet = InitialiseDeviceCircularBuffer(NUMBER_MDA_BUFFERS);
   }
   else
   {
      // When using the Micro-Manager GUI, this code is executed when entering live mode
      cycleMode->Set(L"Continuous");
      snapShotController_->setupTriggerModeSilently();
      b_memOkRet = InitialiseDeviceCircularBuffer(NUMBER_LIVE_BUFFERS);
   }

   if (b_memOkRet)
   {
      ResizeImageBuffer();
   }
   else
   {
      bufferControl->Flush();
      CleanUpDeviceCircularBuffer();
      retCode = DEVICE_OUT_OF_MEMORY;
   }
   cameraDevice->Release(cycleMode);
   return retCode;
}


int CAndorSDK3Camera::CameraStart()
{
   int retCode = DEVICE_OK;
   try
   {
      startAcquisitionCommand->Do();
      if (snapShotController_->isSoftware())
      {
         sendSoftwareTrigger->Do();
      }
   }
   catch (NoMemoryException & e)
   {
      string s("[StartSequenceAcquisition] NoMemoryException: ");
      s += e.what();
      LogMessage(s);
      bufferControl->Flush();
      CleanUpDeviceCircularBuffer();
      retCode = DEVICE_OUT_OF_MEMORY;
   }
   catch (exception & e)
   {
      string s("[StartSequenceAcquisition] Caught Exception with message: ");
      s += e.what();
      LogMessage(s);
      retCode = DEVICE_ERR;
   }
   return retCode;
}

/**
* Simple implementation of Sequence Acquisition
* A sequence acquisition should run on its own thread and transport new images
* coming of the camera into the MMCore circular buffer.
*/
int CAndorSDK3Camera::StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow)
{
   int retCode = DEVICE_OK;
   
   // The camera's default state is in software trigger mode, poised to make an
   // acquisition. We now Abort and re-setup. Also release any buffers that were queued.
   // This may be called twice, if e.g. out of memory first time is returned 
   // - a second attmept may be made. Need to ensure no memory issues or exceptions

   try {
      if (snapShotController_->isPoised() )
      {
         snapShotController_->leavePoisedMode();
      }
   }
   catch (ComException & e) {
      string s("[StartSequenceAcquisition] ComException thrown: ");
      s += e.what();
      LogMessage(s);
   }

   if (IsCapturing())
   {
      retCode = DEVICE_CAMERA_BUSY_ACQUIRING;
   }
   else
   {
      retCode = GetCoreCallback()->PrepareForAcq(this);
   }

   if (DEVICE_OK == retCode)
   {
      aoi_property_->SetReadOnly(true);
      retCode = SetupCameraForSeqAcquisition(numImages);
   }

   if (DEVICE_OK == retCode)
   {
      retCode = CameraStart();
   }

   if (DEVICE_OK == retCode)
   {
      double d_exp = GetExposure();
      currentSeqExposure_ = static_cast<unsigned int>(d_exp);
      keep_trying_ = true;
      thd_->Start(numImages, interval_ms);
      stopOnOverflow_ = stopOnOverflow;
   }

   return retCode;
}

void CAndorSDK3Camera::RestartLiveAcquisition()
{
   CleanUpDeviceCircularBuffer();
   snapShotController_->setupTriggerModeSilently();
   if (false == InitialiseDeviceCircularBuffer(NUMBER_LIVE_BUFFERS) )
   {
      bufferControl->Flush();
      CleanUpDeviceCircularBuffer();
   }
   ResizeImageBuffer();
   CameraStart();
}

AT_64 CAndorSDK3Camera::GetTimeStamp(unsigned char* pBuf)
{
#if defined(linux) && defined(_LP64)
   typedef unsigned int    AT_U32;
#else
   typedef unsigned long   AT_U32;
#endif
   IInteger* imageSizeBytes = NULL;
   AT_64 imageSize = 0;
   try
   {
      imageSizeBytes = cameraDevice->GetInteger(L"ImageSizeBytes");
      imageSize = imageSizeBytes->Get();
      cameraDevice->Release(imageSizeBytes);
   }
   catch (exception & e)
   {
      string s("[GetTimeStamp] Caught Exception with message: ");
      s += e.what();
      LogMessage(s);
      cameraDevice->Release(imageSizeBytes);
   }
   AT_64 i64_timestamp = 0;

   bool foundTimestamp = false;
   AT_U8* puc_metadata = pBuf + static_cast<int>(imageSize); //start at end of buffer
   do {    
      //move pointer to length field
      puc_metadata -= LENGTH_FIELD_SIZE;
      AT_U32 featureSize = *(reinterpret_cast<AT_U32*>(puc_metadata));
    
      //move pointer to Chunk identifier
      puc_metadata -= CID_FIELD_SIZE;
      AT_U32 cid = *(reinterpret_cast<AT_U32*>(puc_metadata));
    
      //move pointer to start of data
      puc_metadata -= (featureSize-CID_FIELD_SIZE);

      if (CID_FPGA_TICKS == cid) {
        i64_timestamp = *(reinterpret_cast<AT_64*>(puc_metadata));
        foundTimestamp = true;
      }
   }
   while(!foundTimestamp && puc_metadata > pBuf);

   return i64_timestamp;
}

/*
 * Inserts Image and MetaData into MMCore circular Buffer
 */
int CAndorSDK3Camera::InsertImage()
{
   char deviceName[MM::MaxStrLength];
   GetProperty(MM::g_Keyword_CameraName, deviceName);
   
   Metadata md;

   MetadataSingleTag mstCount(MM::g_Keyword_Metadata_ImageNumber, deviceName, true);
   mstCount.SetValue(CDeviceUtils::ConvertToString(thd_->GetImageCounter()));      
   md.SetTag(mstCount);

   if (0 == thd_->GetImageCounter())
   {
      sequenceStartTime_ = timeStamp_;
   }

   stringstream ss;
   ss << sequenceStartTime_;
   MetadataSingleTag mstStartTime(MM::g_Keyword_Metadata_StartTime, deviceName, true);
   mstStartTime.SetValue(ss.str().c_str());
   md.SetTag(mstStartTime);

   ss.str("");
   ss.clear();
   double d_result = (timeStamp_-sequenceStartTime_)/static_cast<double>(fpgaTSclockFrequency_);
   ss << d_result*1000 << " [" << d_result << " seconds]";
   MetadataSingleTag mst(MM::g_Keyword_Elapsed_Time_ms, deviceName, true);
   mst.SetValue(ss.str().c_str());
   md.SetTag(mst);

   MMThreadGuard g(imgPixelsLock_);

   const unsigned char * pData = img_.GetPixels();
   unsigned int w = img_.Width();
   unsigned int h = img_.Height();
   unsigned int b = img_.Depth();

   int ret = GetCoreCallback()->InsertImage(this, pData, w, h, b, md.Serialize().c_str());
   if (!stopOnOverflow_ && ret == DEVICE_BUFFER_OVERFLOW)
   {
      // do not stop on overflow - just reset the buffer
      GetCoreCallback()->ClearImageBuffer(this);
      // don't process this same image again...
      ret = GetCoreCallback()->InsertImage(this, pData, w, h, b, md.Serialize().c_str(), false);
   }

   return ret;
}


int CAndorSDK3Camera::checkForBufferOverflow()
{
   int ret = DEVICE_ERR;
   if (eventsManager_->IsEventRegistered(CEventsManager::EV_BUFFER_OVERFLOW_EVENT) )
   {
      if (eventsManager_->HasEventFired(CEventsManager::EV_BUFFER_OVERFLOW_EVENT) )
      {
         LogMessage("[ThreadRun] HW Buffer Overflow event, acquisition was aborted, on-head RAM empty");
         eventsManager_->ResetEvent(CEventsManager::EV_BUFFER_OVERFLOW_EVENT);
         ret = AT_ERR_HARDWARE_OVERFLOW;
      }
   }
   return ret;
}

bool CAndorSDK3Camera::waitForData(unsigned char *& return_buffer, int & buffer_size)
{
   bool got_image = false;
   bool endExpEventFired = false;
   bool softwareTrigger = snapShotController_->isSoftware();
   //if support events & SW, wait for end exp and fire next trigger
   if (softwareTrigger && eventsManager_->IsEventRegistered(CEventsManager::EV_EXPOSURE_END_EVENT) )
   {
      endExpEventFired = eventsManager_->WaitForEvent(CEventsManager::EV_EXPOSURE_END_EVENT, AT_INFINITE);
      
      if (endExpEventFired)
      {
         sendSoftwareTrigger->Do();
      }
   }

   //else just wait on frame (1st trigger sent at Acq start)
   int timeout_ms = currentSeqExposure_ + SnapShotControl::WAIT_DATA_TIMEOUT_BUFFER_MILLISECONDS;
   if (snapShotController_->isExternal() && 0 == thd_->GetImageCounter())
   {
     long extTrigTimeoutValue = 0;
     GetProperty(g_Keyword_ExtTrigTimeout, extTrigTimeoutValue);
     timeout_ms += extTrigTimeoutValue;
   }
   got_image = bufferControl->Wait(return_buffer, buffer_size, timeout_ms);
   //if NO events supported, send next SW trigger
   if (softwareTrigger && !eventsManager_->IsEventRegistered(CEventsManager::EV_EXPOSURE_END_EVENT) )
   {
      sendSoftwareTrigger->Do();
   }
   return got_image;
}


/*
 * Do actual capturing
 * Called from inside the thread  
 */
int CAndorSDK3Camera::ThreadRun(void)
{
   int ret = DEVICE_ERR;
   unsigned char * return_buffer = NULL;
   int buffer_size = 0;

   bool got_image = false;
   while (!got_image && keep_trying_ && !thd_->IsSuspended())
   {
      try
      {
         got_image = waitForData(return_buffer, buffer_size);
      }
      catch (exception & e)
      {
         string s("[ThreadRun] Exception caught with Message: ");
         s += e.what();
         LogMessage(s);
      }
      catch (...)
      {
         LogMessage("[ThreadRun] Unrecognised Exception caught!");
      }

      if (!got_image)
      {
         LogMessage("[ThreadRun] WaitBuffer returned false, no data!");
         ret = checkForBufferOverflow();
         if (AT_ERR_HARDWARE_OVERFLOW == ret)
         {
            break;
         }
      }
   }

   if (got_image)
   {
      bufferControl->Queue(return_buffer, buffer_size);
      timeStamp_ = GetTimeStamp(return_buffer);
      UnpackDataWithPadding(return_buffer);
      ret = InsertImage();
   }

   if (thd_->IsSuspended() )
   {
      ret = DEVICE_OK;
   }
   return ret;
};


bool CAndorSDK3Camera::IsCapturing()
{
   return !thd_->IsStopped();
}

/*
 * called from the thread function before exit 
 */
void CAndorSDK3Camera::OnThreadExiting() throw()
{
   snapShotController_->resetCameraAcquiring();
   CleanUpDeviceCircularBuffer();
   aoi_property_->SetReadOnly(false);

   try
   {
      LogMessage(g_Msg_SEQUENCE_ACQUISITION_THREAD_EXITING);
      GetCoreCallback() ? GetCoreCallback()->AcqFinished(this, 0) : DEVICE_OK;
      //restart in SW trigger ready to snap
      snapShotController_->poiseForSnapShot();
   }
   catch (ComException & e) {
      string s("[OnThreadExiting] ComException thrown: ");
      s += e.what();
      LogMessage(s);
   }
   catch (...)
   {
      LogMessage(g_Msg_EXCEPTION_IN_ON_THREAD_EXITING, false);
   }
}


MySequenceThread::MySequenceThread(CAndorSDK3Camera * pCam)
: intervalMs_(default_intervalMS),
  numImages_(default_numImages),
  imageCounter_(0),
  stop_(true),
  suspend_(false),
  camera_(pCam),
  startTime_(0),
  actualDuration_(0)
{
}

MySequenceThread::~MySequenceThread() {};

void MySequenceThread::Stop()
{
   MMThreadGuard(this->stopLock_);
   stop_ = true;
}

void MySequenceThread::Start(long numImages, double intervalMs)
{
   MMThreadGuard(this->stopLock_);
   MMThreadGuard(this->suspendLock_);
   numImages_ = numImages;
   intervalMs_ = intervalMs;
   imageCounter_ = 0;
   stop_ = false;
   suspend_ = false;
   activate();
   actualDuration_ = 0;
   startTime_ = camera_->GetCurrentMMTime();
}

bool MySequenceThread::IsStopped()
{
   MMThreadGuard(this->stopLock_);
   return stop_;
}

void MySequenceThread::Suspend()
{
   MMThreadGuard(this->suspendLock_);
   suspend_ = true;
}

bool MySequenceThread::IsSuspended()
{
   MMThreadGuard(this->suspendLock_);
   return suspend_;
}

void MySequenceThread::Resume()
{
   MMThreadGuard(this->suspendLock_);
   suspend_ = false;
}

int MySequenceThread::svc(void) throw()
{
   int ret = DEVICE_ERR;
   try
   {
      do
      {
         ret = camera_->ThreadRun();
      } 
      while (DEVICE_OK == ret && !IsStopped() && imageCounter_++ < numImages_ - 1);

      if (IsStopped())
      {
   	     camera_->LogMessage("[svc] SeqAcquisition interrupted by the user");
      }

      if (DEVICE_BUFFER_OVERFLOW == ret)
      {
         camera_->LogMessage("[MySequenceThread::svc] Circular Buffer Overflow code from MMCore; Thread exiting...");
      }
      else if (AT_ERR_HARDWARE_OVERFLOW == ret)
      {
         camera_->LogMessage("[MySequenceThread::svc] Internal Hardware Buffer Overflow; Thread exiting...");
         //camera_->GetCoreCallback()->PostError(ret, "Internal Hardware Buffer Overflow occured");
      }
      else if (DEVICE_OK != ret)
      {
         camera_->LogMessage("[MySequenceThread::svc] Unknown Error occured");
      }
   }
   catch (exception & e)
   {
      camera_->LogMessage(e.what());
   }
   catch (...)
   {
      camera_->LogMessage(g_Msg_EXCEPTION_IN_THREAD, false);
   }
   actualDuration_ = camera_->GetCurrentMMTime() - startTime_;
   camera_->OnThreadExiting();
   Stop();
   return ret;
}

///////////////////////////////////////////////////////////////////////////////
// Private CAndorSDK3Camera methods
///////////////////////////////////////////////////////////////////////////////

void CAndorSDK3Camera::TestResourceLocking(const bool recurse)
{
   MMThreadGuard g(*pDemoResourceLock_);
   if (recurse)
      TestResourceLocking(false);
}
