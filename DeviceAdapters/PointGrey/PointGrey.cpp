///////////////////////////////////////////////////////////////////////////////
// FILE:          PointGrey.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   PointGrey camera module.
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

#include <list>
#include <string>
#include <map>
#include <iterator>
#include <algorithm>

#include "PointGrey.h"
#include "../../MMDevice/ModuleInterface.h"

using namespace FlyCapture2;

// instead of translating all PG Errors into MM error codes
// we use one error code and set the description whenever
// an error occurs
const int ALLERRORS = 10001;


/////////////////////////////////////////////////////

const char* g_Interface                = "Interface";
const char* g_VendorName               = "Vendor Name";
const char* g_SensorResolution         = "Sensor Resolution";
const char* g_SensorInfo               = "Sensor Info";
const char* g_DriverName               = "Driver Name";
const char* g_FirmwareVersion          = "Firmware Version";
const char* g_FirmwareBuildTime        = "Firmware Build Time";
const char* g_MaxBusSpeed              = "Maximum Bus Speed";
const char* g_InterfaceType            = "Interface Type";
const char* g_ColorMonoChrome          = "Color or Monochrome";
const char* g_IIDCVersion              = "IIDC version";
const char* g_CameraId                 = "CameraID";
const char* g_AdvancedMode             = "Use Advanced Mode?";
const char* g_VideoModeAndFrameRate    = "Video Mode and Frame Rate";
const char* g_NotSet                   = "Not set";
const char* g_Format7Mode              = "Format-7 Mode";
const char* g_InternalTrigger          = "Internal";
const char* g_ExternalTrigger          = "External";
const char* g_SoftwareTrigger          = "Software";

/////////////////////////////////////////////////////

const char* g_Data_Format      = "Data format";
const char* g_PixelType_Mono8  = "Mono 8";
const char* g_PixelType_Raw8   = "Raw 8";
const char* g_PixelType_Mono10 = "Mono 10";
const char* g_PixelType_Raw10  = "Raw 10";
const char* g_PixelType_Mono12 = "Mono 12";
const char* g_PixelType_Raw12  = "Raw 12";
const char* g_PixelType_Mono14 = "Mono 14";
const char* g_PixelType_Raw14  = "Raw 14";
const char* g_PixelType_RGB32  = "RGB 32";

/////////////////////////////////////////////////////

const int g_NumProps = 11;
const PropertyType g_PropertyTypes [g_NumProps] = { SHARPNESS, HUE, SATURATION, IRIS, 
   FOCUS, ZOOM, PAN, TILT, GAIN, TRIGGER_DELAY, TEMPERATURE };
const std::string g_PropertyNames [g_NumProps] = { "Sharpness", "Hue", "Saturation", 
   "Iris", "Focus", "Zoom", "Pan", "Tilt", "Gain", "Trigger Delay", 
   "Temperature"};

const int g_NumOffProps = 4;
const PropertyType g_OffPropertyTypes [g_NumOffProps] = {FRAME_RATE, BRIGHTNESS, AUTO_EXPOSURE, GAMMA};
//const std::string g_OffPropertyNames [g_NumOffProps] = {"Frame Rate", "Brightness", "Auto Exposure", "Gamma"};

const int g_NumFrameRates = 9;
const std::string g_FrameRates [g_NumFrameRates] = { "1.875 fps", "3.75 fps", 
   "7.5 fps", "15 fps", "30 fps", "60 fps", "120 fps", "240 fps", "FORMAT7"};

const int g_NumVideoModes = 24;
const std::string g_VideoModes [g_NumVideoModes] = { "160x120 YUV444", "320x240 YUV422", 
   "640x480 YUV411", "640x480 YUV422", "640x480 24-bit", "640x480 8-bit", "640x480 16-bit",
   " 800x600 YUV422", "800x600 RGB",  "800x600 8-bit", "800x600 16-bit", "1024x768YUV422", 
   "1024x768 RGB", "1024x768 8-bit", "1024x768 16-bit", "1280x960 YUV422", "1280x960 RGB",
   "1280x960 8-bit", "1280x960 16-bit", "1600x1200 YUV422", "1600x1200 RGB", "1600x1200 8-bit",
   "1600x1200 16-bit", "FORMAT7" };
 


/**
 * Callback function as defined by the typedef ImageEventCallback in 
 * CameraBase.h in the PointGreyResearch api.  
 */
void PGCallback(Image* pImage,  const void* pCallbackData)
{
  const PointGrey* pg = static_cast<const PointGrey*> (pCallbackData);
  pg->InsertImage(pImage);
  
}


///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////


/***********************************************************************
 * List all supported hardware devices here
 */
MODULE_API void InitializeModuleData()
{
   BusManager busMgr;
   Error error;
   Camera cam;
   unsigned int numCameras;

   error = busMgr.GetNumOfCameras(&numCameras);
   if (error != PGRERROR_OK)
   {
      // TODO work out how to return/report errors, 
      return;
   }
   for (unsigned int i=0; i < numCameras; i++)
   {
      PGRGuid guid;
      error = busMgr.GetCameraFromIndex(i, &guid);
      if (error != PGRERROR_OK)
      {
         // TODO work out how to return/report errors, 
         return;
      }

      std::string name;
      int ret = PointGrey::CameraID(guid, &name);
      if (ret != DEVICE_OK) 
      {
         return;
      }

      RegisterDevice(name.c_str(), MM::CameraDevice, "Point Grey Camera");
   }
}


//***********************************************************************

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
    return new PointGrey(deviceName);
}

//***********************************************************************

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
	delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
// PointGrey implementation
/***********************************************************************
* PointGrey constructor.
* Setup default all variables and create device properties required to exist
* before intialization. In this case, no such properties were required. All
* properties will be created in the Initialize() method.
*
* As a general guideline Micro-Manager devices do not access hardware in the
* the constructor. We should do as little as possible in the constructor and
* perform most of the initialization in the Initialize() method.
*/
PointGrey::PointGrey(const char* deviceName) :
   nComponents_(1),
   initialized_(false),
   deviceName_(deviceName),
   sequenceStartTime_(0),
   imageCounter_(0),
   stopOnOverflow_(false),
   isCapturing_(false),
   f7InUse_(false),
   triggerMode_(TRIGGER_INTERNAL),
   externalTriggerGrabTimeout_(60000)

	
{
	// call the base class method to set-up default error codes/messages
	InitializeDefaultErrorMessages();

   // Create pre-init property with advanced mode
   CreateProperty(g_AdvancedMode, "Yes", MM::String, false, 0, true);
   AddAllowedValue(g_AdvancedMode, "No");
   AddAllowedValue(g_AdvancedMode, "Yes");

}

/***********************************************************************
* PointGrey destructor.
* If this device is used as intended within the Micro-Manager system,
* Shutdown() will be always called before the destructor. But in any case
* we need to make sure that all resources are properly released even if
* Shutdown() was not called.
*/
PointGrey::~PointGrey()
{
	if (initialized_)
		Shutdown();
}

/***********************************************************************
* Obtains device name.
* Required by the MM::Device API.
*/
void PointGrey::GetName(char* name) const
{
	CDeviceUtils::CopyLimitedString(name, deviceName_.c_str());
}

/***********************************************************************
* Intializes the hardware.
* Gets the PGRGuid based on the CameraId set in the pre-init property
* Uses this to retrieve information about the camera and expose all
* possible properties.
* Required by the MM::Device API.
*/
int PointGrey::Initialize()
{
	if (initialized_)
		return DEVICE_OK;

   // check dll version and make sure it is compatible (the same as used to build the code)
   // Todo: find an automated way to synchronize version numbers
   FC2Version pVersion;
   Error error = Utilities::GetLibraryVersion(&pVersion);
   if (error != PGRERROR_OK)
   {
      SetErrorText(ALLERRORS, error.GetDescription());
      return ALLERRORS;
   }
   
   std::ostringstream os;
   os << "FlyCapture2_v100.dll version number is " << pVersion.major << "." << pVersion.minor << "." << pVersion.type << "." << pVersion.build;
   LogMessage(os.str().c_str(), false);

   // BE AWARE: This version number needs to be updates if/when MM is linked against another PGR version
   if (pVersion.major != 2 || pVersion.minor != 10 || pVersion.type != 3 || pVersion.build != 266) {
      SetErrorText(ALLERRORS, "Flycapture2_v100.dll is not version 2.10.3.266.  Micro-Manager works correctly only with that version");
      return ALLERRORS;
   }

   BusManager busMgr;
   
   int ret = CameraGUIDfromOurID(&busMgr, &guid_, deviceName_.c_str()); 
   if (ret != DEVICE_OK) {
      return ret;
   }

	// -------------------------------------------------------------------------------------
	// Open camera device
   error = cam_.Connect(&guid_);
   if (error != PGRERROR_OK)
   {
      SetErrorText(ALLERRORS, error.GetDescription());
      return ALLERRORS;
   }

   // Get the camera information
   CameraInfo camInfo;
   error = cam_.GetCameraInfo(&camInfo);
   if (error != PGRERROR_OK)
   {
      SetErrorText(ALLERRORS, error.GetDescription());
      return ALLERRORS;
   }

	// -------------------------------------------------------------------------------------
	// Set property list
	// -------------------------------------------------------------------------------------
	
   // camera identification and other read-only information
	char buf[FlyCapture2::sk_maxStringLength]="";
	
   sprintf(buf, "%d", camInfo.serialNumber);
	ret = CreateProperty(MM::g_Keyword_CameraID, buf, MM::String, true);
	assert(ret == DEVICE_OK);

	sprintf(buf, "%s", camInfo.modelName);
	ret = CreateProperty(MM::g_Keyword_CameraName, buf, MM::String, true);
	assert(ret == DEVICE_OK);

   sprintf(buf, "%s", camInfo.userDefinedName);
	ret = CreateProperty(MM::g_Keyword_Description, buf, MM::String, true);
	assert(ret == DEVICE_OK);

   sprintf(buf, "%s", camInfo.vendorName);
   ret = CreateProperty(g_VendorName, buf, MM::String, true);
	assert(ret == DEVICE_OK);

   sprintf(buf, "%s", camInfo.sensorInfo);
   ret = CreateProperty(g_SensorInfo, buf, MM::String, true);
	assert(ret == DEVICE_OK);

   sprintf(buf, "%s", camInfo.sensorResolution);
   ret = CreateProperty(g_SensorResolution, buf, MM::String, true);
	assert(ret == DEVICE_OK);  

   sprintf(buf, "%s", camInfo.driverName);
   ret = CreateProperty(g_DriverName, buf, MM::String, true);
	assert(ret == DEVICE_OK); 

   sprintf(buf, "%s", camInfo.firmwareVersion);
   ret = CreateProperty(g_FirmwareVersion, buf, MM::String, true);
	assert(ret == DEVICE_OK); 

   sprintf(buf, "%s", camInfo.firmwareBuildTime);
   ret = CreateProperty(g_FirmwareBuildTime, buf, MM::String, true);
   assert(ret == DEVICE_OK); 

   sprintf(buf, "%s", GetBusSpeedAsString(camInfo.maximumBusSpeed));
   ret = CreateProperty(g_MaxBusSpeed, buf, MM::String, true);
   assert (ret == DEVICE_OK);

   std::string colorType = "MonoChrome";
   if (camInfo.isColorCamera) {
      colorType = "Color";
   }
   ret = CreateProperty(g_ColorMonoChrome, colorType.c_str(), MM::String, true);

   // interface type
   std::string itType;
   InterfaceType it = camInfo.interfaceType;
   switch (it) {
      case (INTERFACE_IEEE1394) : itType = "IEEE1394"; break;
      case (INTERFACE_USB2) : itType = "USB2"; break;
      case (INTERFACE_USB3) : itType = "USB3"; break;
      case (INTERFACE_GIGE) : itType = "GigE"; break;
      default : itType = "Unknown";
   }
   ret = CreateProperty(g_InterfaceType, itType.c_str(), MM::String, true); 
   assert(ret == DEVICE_OK); 

   // IIDC version
   std::ostringstream os2;
   os2 << std::fixed << std::setprecision(2) << ( ((float) camInfo.iidcVer) / 100.0f);
   ret = CreateProperty(g_IIDCVersion, os2.str().c_str(), MM::String, true);
   
   if (it == INTERFACE_GIGE) {
      
   }

   char advancedModeRequest[MM::MaxStrLength];
   GetProperty(g_AdvancedMode,advancedModeRequest);
   bool f7Available = false;
   bool f7Requested = false;
   if (strcmp(advancedModeRequest, "Yes") == 0) {
      // Format 7 requested, check if it is available
      f7Requested = true;
      Format7Info format7Info;
      bool available;
      for (int mode = MODE_0; mode < NUM_MODES; mode++) {
         format7Info.mode = (Mode) mode;
         error = cam_.GetFormat7Info(&format7Info, &available);
         if (error == PGRERROR_OK && available) {
            availableFormat7Modes_.push_back((Mode) mode);
            f7Available = true;
            std::ostringstream os;
            os << "Format 7 mode " << mode << " is available";
            LogMessage (os.str().c_str(), false);
         }
        
         else if (error != PGRERROR_OK)
         {
            SetErrorText(ALLERRORS, error.GetDescription());
            return ALLERRORS;
         }
       
      }
      
      if (f7Requested && f7Available) {
         format7Info.mode = availableFormat7Modes_[0];
         error = cam_.GetFormat7Info(&format7Info, &available);
         Format7ImageSettings fmt7ImageSettings;
         fmt7ImageSettings.mode = format7Info.mode;
         fmt7ImageSettings.offsetX = 0;
         fmt7ImageSettings.offsetY = 0;
         fmt7ImageSettings.width = format7Info.maxWidth;
         fmt7ImageSettings.height = format7Info.maxHeight;
         // It is better to use Raw rather than Mono formats (Mono
         // formats may be processed resuling in slower frame rates).  
         // However, not all cameras provide raw images in all modes
         updatePixelFormats(format7Info.pixelFormatBitField);
         if (format7Info.pixelFormatBitField & pixelFormat16Bit_) {
            fmt7ImageSettings.pixelFormat = pixelFormat16Bit_;
         } else if (format7Info.pixelFormatBitField & pixelFormat8Bit_) {
            fmt7ImageSettings.pixelFormat = pixelFormat8Bit_;
         }
         // TODO: preference order for pixelFormat: RGB > 16bit > 8bit
         bool valid;
         Format7PacketInfo format7PacketInfo;
         error = cam_.ValidateFormat7Settings(&fmt7ImageSettings, &valid, &format7PacketInfo);
         if (valid) {
            // Set the settings to the camera
            error = cam_.SetFormat7Configuration(&fmt7ImageSettings,
                        format7PacketInfo.recommendedBytesPerPacket);
            if (error != PGRERROR_OK) {
               SetErrorText(ALLERRORS, error.GetDescription());
               return ALLERRORS;
            }

            f7InUse_ = true; // now create some Format 7-specific properties

            // Pixel Type property
            std::string pixelType = PixelTypeAsString(fmt7ImageSettings.pixelFormat);
            CPropertyAction* pAct = new CPropertyAction(this, &PointGrey::OnPixelType);
            CreateProperty("PixelType", pixelType.c_str(), MM::String, false, pAct, false);
            PixelFormat pixelFormats[3] = { pixelFormat8Bit_, pixelFormat16Bit_, PIXEL_FORMAT_RGB8 };
            for (int i = 0; i < 3; i++) {
               if (format7Info.pixelFormatBitField & pixelFormats[i]) {
                  pixelType = PixelTypeAsString(pixelFormats[i]);
                  AddAllowedValue("PixelType", pixelType.c_str());
               }
            }

            // Format 7 mode selection
            std::string f7Mode = Format7ModeAsString(fmt7ImageSettings.mode);
            pAct = new CPropertyAction(this, &PointGrey::OnFormat7Mode);
            CreateProperty(g_Format7Mode, f7Mode.c_str(), MM::String, false, pAct, false);
            for (unsigned int i = 0; i < availableFormat7Modes_.size(); i++) {
               f7Mode = Format7ModeAsString(availableFormat7Modes_[i]);
               AddAllowedValue(g_Format7Mode, f7Mode.c_str());
            }

            // Associate binning with Format-7 modes:
            long binnings[] = { 1, 2, 4};
            for (unsigned int i = 0; i < sizeof(binnings)/sizeof(int); i++) 
            {
               CPropertyActionEx* pActEx = new CPropertyActionEx(this, &PointGrey::OnBinningFromFormat7Mode, binnings[i]);
               std::ostringstream propName;
               propName << "Format 7 Mode for binning " << binnings[i];
               CreateProperty(propName.str().c_str(), g_NotSet, MM::String, false, pActEx, false);
               AddAllowedValue(propName.str().c_str(), g_NotSet);
               for (unsigned int j = 0; j < availableFormat7Modes_.size(); j++) {
                  f7Mode = Format7ModeAsString(availableFormat7Modes_[j]);
                  AddAllowedValue(propName.str().c_str(), f7Mode.c_str());
               }
            }
         }
      }
   }

   // Standard, non-format 7 modes
   if (!f7InUse_) {
      // Find current video mode and frame rate
      VideoMode currVideoMode;
      FrameRate currFrameRate;
      error = cam_.GetVideoModeAndFrameRate(&currVideoMode, &currFrameRate);
      if (error != PGRERROR_OK)
      {
         SetErrorText(ALLERRORS, error.GetDescription());
         return ALLERRORS;
      }

      // Create video mode and frame rate property
      std::string videoModeAndFrameRate;
      VideoModeAndFrameRateStringFromEnums(videoModeAndFrameRate, currVideoMode, currFrameRate);
      CPropertyAction *pAct = new CPropertyAction (this, &PointGrey::OnVideoModeAndFrameRate);
      ret = CreateProperty(g_VideoModeAndFrameRate, videoModeAndFrameRate.c_str(), MM::String, false, pAct);
      assert(ret == DEVICE_OK);

      // Figure out which video modes and frame rates are supported
      bool available = false;
      for (int v = VIDEOMODE_160x120YUV444; v < NUM_VIDEOMODES; v++) {
         for (int f = FRAMERATE_1_875; f < NUM_FRAMERATES; f++) {
            error = cam_.GetVideoModeAndFrameRateInfo((VideoMode) v, (FrameRate) f, &available);
            if (error == PGRERROR_OK && available) {
               if (videoModeFrameRateMap_.find((VideoMode) v) == videoModeFrameRateMap_.end()) {
                  std::vector<FrameRate> *vf = new std::vector<FrameRate>();
                  videoModeFrameRateMap_.insert(std::pair<VideoMode, std::vector<FrameRate>>((VideoMode) v, *vf));
               }
               std::map<VideoMode, std::vector<FrameRate>>::iterator p;
               p = videoModeFrameRateMap_.find((VideoMode) v);
               p->second.push_back((FrameRate) f);
               std::string allowedMode;
               VideoModeAndFrameRateStringFromEnums(allowedMode, (VideoMode) v, (FrameRate) f); 
               AddAllowedValue(g_VideoModeAndFrameRate, allowedMode.c_str());
            }
         }
      }
   }

   // TODO: list GigE properties



   // TODO: add other info based on devicetype


   // Add properties based on Point Grey properties
   for (int i = 0; i < g_NumProps; i++) {
      PropertyInfo* pPropInfo = new PropertyInfo(g_PropertyTypes[i]);
      error = cam_.GetPropertyInfo(pPropInfo);
      if (error != PGRERROR_OK) {
         SetErrorText(ALLERRORS, error.GetDescription());
         return ALLERRORS;
      }
      Property* pProp = new Property(g_PropertyTypes[i]);
      error = cam_.GetProperty(pProp);
      if (error != PGRERROR_OK) {
         SetErrorText(ALLERRORS, error.GetDescription());
         return ALLERRORS;
      }
      if (pPropInfo->present) {
         CPropertyActionEx* pActEx;
         if (pPropInfo->onOffSupported) {
            // create on-off property
            pActEx = new CPropertyActionEx(this, &PointGrey::OnOnOff, i);
            std::string propName = g_PropertyNames[i] + "-OnOff";
            std::string val = "Off";
            if (pProp->onOff) {
               val = "On";
            }
            CreateProperty(propName.c_str(), val.c_str(), MM::String, false, pActEx, false);
            AddAllowedValue(propName.c_str(), "On");
            AddAllowedValue(propName.c_str(), "Off");
         }
         if (pPropInfo->autoSupported && pPropInfo->manualSupported) {
            // create manual/auto selection property
            pActEx = new CPropertyActionEx(this, &PointGrey::OnAutoManual, i);
            std::string propName = g_PropertyNames[i] + "-AutoOrManual";
            std::string val = "Manual";
            if (pProp->autoManualMode) {
               // set all auto/manual properties to manual, since things like
               // automatic gain control are not good for quantitative measurements
               pProp->autoManualMode = false;
               error = cam_.SetProperty(pProp);
               if (error != PGRERROR_OK) {
                  SetErrorText(ALLERRORS, error.GetDescription());
                  return ALLERRORS;  
               }
            }
            CreateProperty(propName.c_str(), val.c_str(), MM::String, false, pActEx, false);
            AddAllowedValue(propName.c_str(), "Auto");
            AddAllowedValue(propName.c_str(), "Manual");
         }
         if (pPropInfo->absValSupported) {
            pActEx = new CPropertyActionEx(this, &PointGrey::OnAbsValue, i);
            std::string propName = g_PropertyNames[i] + "(" + pPropInfo->pUnitAbbr + ")";
            if (g_PropertyTypes[i] == TEMPERATURE) { // temperature 
               CreateProperty(propName.c_str(), "0.0", MM::Float, false, pActEx, pPropInfo->onOffSupported);  
            } else {
               CreateProperty(propName.c_str(), "0.0", MM::Float, false, pActEx);
               SetPropertyLimits(propName.c_str(), pPropInfo->absMin, pPropInfo->absMax);
            }
         } else 
         {
            pActEx = new CPropertyActionEx(this, &PointGrey::OnValue, i);
            std::ostringstream osmin;
            osmin << pPropInfo->min;
            CreateProperty(g_PropertyNames[i].c_str(), osmin.str().c_str(), MM::Integer, false, pActEx);
            SetPropertyLimits(g_PropertyNames[i].c_str(), pPropInfo->min, pPropInfo->max);
         }
      }
      delete (pPropInfo);
      delete (pProp);
   }

   // For exposure, use the shutter property.  Make sure it is present 
   // and set it to manual
   PropertyInfo propInfo;
   propInfo.type = SHUTTER;
   error = cam_.GetPropertyInfo(&propInfo);
   if (error != PGRERROR_OK) {
      SetErrorText(ALLERRORS, error.GetDescription());
      return ALLERRORS;
   }
   if (propInfo.present) {
      Property prop;
      prop.type = SHUTTER;
      cam_.GetProperty(&prop);
      exposureTimeMs_ = prop.absValue;
      if (propInfo.manualSupported) {
         prop.autoManualMode = false;
         prop.absControl = true;
         prop.onOff = true;
         cam_.SetProperty(&prop, false);
      }
   }

   // To set the camera in extended shutter mode, we need to 
   // switch the framerate property off
   // There are a few more PGR properties that only interfere with 
   // getting raw data from the camera.  Switch all of these off
   for (unsigned int i = 0; i < g_NumOffProps; i++) {
      propInfo.type = g_OffPropertyTypes[i];
      error = cam_.GetPropertyInfo(&propInfo);
      if (error != PGRERROR_OK) {
         SetErrorText(ALLERRORS, error.GetDescription());
         return ALLERRORS;
      }
      if (propInfo.present && propInfo.onOffSupported) {
         Property prop;
         prop.type = FRAME_RATE;
         cam_.GetProperty(&prop);
         prop.onOff = false;
         cam_.SetProperty(&prop, false);
      }
   }


   // TODO: check for the AutoExpose property and switch it off

   // Binning property is just a stub.  The Micro-Manager GUI does not function
   // without it.
   // User can associate Format7 modes with binning using the 
   // "Format 7 Mode for binning" property
   CPropertyAction* pAct = new CPropertyAction(this, &PointGrey::OnBinning);
   CreateProperty(MM::g_Keyword_Binning, "1", MM::Integer, false, pAct, false);
   AddAllowedValue(MM::g_Keyword_Binning, "1");

   // Determined which trigger modes are available
   availableTriggerModes_.push_back(TRIGGER_INTERNAL);  // seems to be always present
   // Check for external trigger support
	TriggerModeInfo triggerModeInfo;
	error = cam_.GetTriggerModeInfo( &triggerModeInfo );
	if (error != PGRERROR_OK)
	{
      SetErrorText(ALLERRORS, error.GetDescription());
      return ALLERRORS;
   }
	if ( triggerModeInfo.present == true )
	{
		// this seems to guarantee that external trigger is present
      // the code example is a bit ambiguous about what that means
      availableTriggerModes_.push_back(TRIGGER_EXTERNAL);
      bool softwareTriggerPresent = false;
      int result =  CheckSoftwareTriggerPresence(&cam_, softwareTriggerPresent);
      if (result != DEVICE_OK) 
      {
         return result;
      }
      if (softwareTriggerPresent) 
      {
         availableTriggerModes_.push_back(TRIGGER_SOFTWARE);
         result = SetTriggerMode(&cam_, TRIGGER_SOFTWARE);
         if (result != DEVICE_OK)
         {
            return result;
         }
      }
	}
   if (availableTriggerModes_.size() > 1)
   {
      CPropertyAction* pAct = new CPropertyAction(this, &PointGrey::OnTriggerMode);
      CreateProperty("TriggerMode", g_InternalTrigger, MM::String, false, pAct, false);
      for (std::vector<unsigned short>::const_iterator i = availableTriggerModes_.begin();
         i != availableTriggerModes_.end(); i++)
      {
         AddAllowedValue("TriggerMode", TriggerModeAsString(*i).c_str());
      }
   }

   ret = SetGrabTimeout(&cam_, (unsigned long) (3 * exposureTimeMs_) + 50);
   if (ret != DEVICE_OK) 
   {
      return ret;
   }

   // We most likely want little endian bit order
   ret = SetEndianess(true);
   if (ret != DEVICE_OK)
      return ret;

   error = cam_.StartCapture();
   if (error != PGRERROR_OK)
	{
      SetErrorText(ALLERRORS, error.GetDescription());
      return ALLERRORS;
   }

   // Make sure that we have an image so that 
   // things like bitdepth are set correctly
   ret = SnapImage();
   if (ret != DEVICE_OK) {
      return ret;
   }


	//-------------------------------------------------------------------------------------
	// synchronize all properties
	ret = UpdateStatus();
	
	return ret;
}

/***********************************************************************
* Shuts down (unloads) the device.
* Ideally this method will completely unload the device and release all resources.
* Shutdown() may be called multiple times in a row.
* Required by the MM::Device API.
*/
int PointGrey::Shutdown()
{
   cam_.StopCapture();
	if(initialized_) {
      cam_.Disconnect();
	}
	initialized_ = false;
	return DEVICE_OK;
}

/***********************************************************************
* Performs exposure and grabs a single image.
* This function should block during the actual exposure and return immediately afterwards 
* (i.e., before readout).  This behavior is needed for proper synchronization with the shutter.
* Required by the MM::Camera API.
*
* Camera is continuously capturing.  In software trigger mode, we wait for the camera to be
* ready for a trigger, then send the software trigger, then wait to retrieve the image.
* it is potentially possible to skip the wait for the image itself, but some heuristics would
* be needed (at least, I could not find anything in the API about this).  
* Readout times have gotten pretty short, so this issues is only important for very short
* exposure times.
* In external trigger mode, the code simply waits for the image.  The grabtimeout that was previously
* set determines whether or not the camera gives up.  
* In internal trigger mode, the first received image is discarded and we wait for a second one.
* Exposure of the first one most likely was started before the shutter opened.
*/
int PointGrey::SnapImage()
{
   if (triggerMode_ == TRIGGER_SOFTWARE)
   {
      int ret = PollForTriggerReady(&cam_, (unsigned long) exposureTimeMs_ + 50);
      if (ret != DEVICE_OK) 
      {
         return ret;
      }
      FireSoftwareTrigger(&cam_);
      Error error = cam_.RetrieveBuffer(&image_);
      if (error != PGRERROR_OK)
      {
         SetErrorText(ALLERRORS, error.GetDescription());
         return ALLERRORS;
      }
   }
   else 
   {
      // for extenral capture, we may need to wait longer than the timeout set so far
      Error error = cam_.RetrieveBuffer(&image_);
      if (error != PGRERROR_OK)
      {
         SetErrorText(ALLERRORS, error.GetDescription());
         return ALLERRORS;
      }
      if (triggerMode_ == TRIGGER_INTERNAL) 
         // since the first image may have been started before the shutter opened, grab a second one
      {
         error = cam_.RetrieveBuffer(&image_);
         if (error != PGRERROR_OK)
         {
            SetErrorText(ALLERRORS, error.GetDescription());
            return ALLERRORS;
         }
      }
   }
  
   return DEVICE_OK;
}

/***********************************************************************
* Returns pixel data.
* Required by the MM::Camera API.
* The calling program will assume the size of the buffer based on the values
* obtained from GetImageBufferSize(), which in turn should be consistent with
* values returned by GetImageWidth(), GetImageHight() and GetImageBytesPerPixel().
* The calling program allso assumes that camera never changes the size of
* the pixel buffer on its own. In other words, the buffer can change only if
* appropriate properties are set (such as binning, pixel type, etc.)
*/
const unsigned char* PointGrey::GetImageBuffer()
{
   if (nComponents_ == 1) {
      // This seems to work without a DeepCopy first
      return image_.GetData();
   } 
   // nComponents must be 4
   Image convertedImage;
   Error error = image_.Convert ( PIXEL_FORMAT_RGBU , &convertedImage );
   if (error != PGRERROR_OK) {
      LogMessage(error.GetDescription(), false);
      return 0;
   }
   return convertedImage.GetData();
}

/***********************************************************************
* Returns image buffer X-size in pixels.
* Required by the MM::Camera API.
*/
unsigned int PointGrey::GetImageWidth() const
{
   return image_.GetCols();
}

/***********************************************************************
* Returns image buffer Y-size in pixels.
* Required by the MM::Camera API.
*/
unsigned int PointGrey::GetImageHeight() const
{
	return image_.GetRows();
}

/***********************************************************************
* Returns image buffer pixel depth in bytes.
* Required by the MM::Camera API.
*/
unsigned int PointGrey::GetImageBytesPerPixel() const
{
   //PixelFormat pf =  image_.GetPixelFormat();
   int bpp = image_.GetBitsPerPixel();
   unsigned int bytespp = (bpp/8);
   if ( (bpp % 8) > 0) {
      bytespp += 1;
   }
   return bytespp;
} 

/***********************************************************************
* Returns the bit depth (dynamic range) of the pixel.
* This does not affect the buffer size, it just gives the client application
* a guideline on how to interpret pixel values.
* Required by the MM::Camera API.
*/
unsigned int PointGrey::GetBitDepth() const
{
   unsigned int bpp = image_.GetBitsPerPixel();
   return bpp;
}

/***********************************************************************
* Returns the size in bytes of the image buffer.
* Required by the MM::Camera API.
*/
long PointGrey::GetImageBufferSize() const
{
   return image_.GetDataSize();
}

/***********************************************************************
* Sets the camera Region Of Interest.
* Required by the MM::Camera API.
* This command will change the dimensions of the image.
* @param x - top-left corner coordinate
* @param y - top-left corner coordinate
* @param xSize - width
* @param ySize - height
*/
int PointGrey::SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize)
{
   if (!f7InUse_) 
      return DEVICE_OK;
   	  
   Format7ImageSettings format7ImageSettings;
   unsigned int packetSize;
   float percentage;
   Error error = cam_.GetFormat7Configuration(&format7ImageSettings, &packetSize, &percentage);
   if (error != PGRERROR_OK) {
      SetErrorText(ALLERRORS, error.GetDescription());
      return ALLERRORS;
   }

   Format7Info format7Info;
   format7Info.mode = format7ImageSettings.mode;
   bool supported;
   error = cam_.GetFormat7Info(&format7Info, &supported);
   x = (x / format7Info.offsetHStepSize) * format7Info.offsetHStepSize;
   y = (y / format7Info.offsetVStepSize) * format7Info.offsetVStepSize;
   xSize = (xSize / format7Info.imageHStepSize) * format7Info.imageHStepSize;
   ySize = (ySize / format7Info.imageVStepSize) * format7Info.imageVStepSize;
   format7ImageSettings.offsetX = x;
   format7ImageSettings.offsetY = y;
   format7ImageSettings.width = xSize;
   format7ImageSettings.height = ySize;
   bool valid;
   Format7PacketInfo f7pInfo;
   error = cam_.ValidateFormat7Settings(&format7ImageSettings, &valid, &f7pInfo);
   if (!valid) {
      SetErrorText(ALLERRORS, "Error setting ROI");
      return ALLERRORS;
   }
   error = cam_.SetFormat7Configuration(&format7ImageSettings, f7pInfo.recommendedBytesPerPacket);
   if (error != PGRERROR_OK) {
      SetErrorText(ALLERRORS, error.GetDescription());
      return ALLERRORS;
   }

	return DEVICE_OK;;
}

/***********************************************************************
* Returns the actual dimensions of the current ROI.
* Required by the MM::Camera API.
*/
int PointGrey::GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize)
{
   if (!f7InUse_) 
   {
      x = 0;
      y = 0;
      xSize = image_.GetCols();
      ySize = image_.GetRows();

      return DEVICE_OK;
   }
   	  
   Format7ImageSettings format7ImageSettings;
   unsigned int packetSize;
   float percentage;
   Error error = cam_.GetFormat7Configuration(&format7ImageSettings, &packetSize, &percentage);
   if (error != PGRERROR_OK) {
      SetErrorText(ALLERRORS, error.GetDescription());
      return ALLERRORS;
   }

   Format7Info format7Info;
   format7Info.mode = format7ImageSettings.mode;
   bool supported;
   error = cam_.GetFormat7Info(&format7Info, &supported);

   if (error != PGRERROR_OK) {
      SetErrorText(ALLERRORS, error.GetDescription());
      return ALLERRORS;
   }
   x = format7ImageSettings.offsetX;
   y = format7ImageSettings.offsetY;
   xSize = format7ImageSettings.width;
   ySize = format7ImageSettings.height;

	return DEVICE_OK;
}

/***********************************************************************
* Resets the Region of Interest to full frame.
* Required by the MM::Camera API.
*/
int PointGrey::ClearROI()
{
  if (!f7InUse_) 
     return DEVICE_OK;
   	  
   Format7ImageSettings format7ImageSettings;
   unsigned int packetSize;
   float percentage;
   Error error = cam_.GetFormat7Configuration(&format7ImageSettings, &packetSize, &percentage);
   if (error != PGRERROR_OK) {
      SetErrorText(ALLERRORS, error.GetDescription());
      return ALLERRORS;
   }

   Format7Info format7Info;
   format7Info.mode = format7ImageSettings.mode;
   bool supported;
   error = cam_.GetFormat7Info(&format7Info, &supported);
   format7ImageSettings.offsetX = 0;
   format7ImageSettings.offsetY = 0;
   format7ImageSettings.width = format7Info.maxWidth;
   format7ImageSettings.height = format7Info.maxHeight;
   bool valid;
   Format7PacketInfo f7pInfo;
   error = cam_.ValidateFormat7Settings(&format7ImageSettings, &valid, &f7pInfo);
   if (!valid) {
      SetErrorText(ALLERRORS, "Error clearing ROI");
      return ALLERRORS;
   }
   error = cam_.SetFormat7Configuration(&format7ImageSettings, f7pInfo.recommendedBytesPerPacket);
   if (error != PGRERROR_OK) {
      SetErrorText(ALLERRORS, error.GetDescription());
      return ALLERRORS;
   }

	return DEVICE_OK;
}

/***********************************************************************
* Returns the current exposure setting in milliseconds.
* Required by the MM::Camera API.
*/
double PointGrey::GetExposure() const
{
   // Since this function is const, we can not use the cam_ object.
   // Hence, the only way to report exposure time is to cache it when we 
   // we set it
   
	return exposureTimeMs_;
}

/***********************************************************************
* Sets exposure in milliseconds.
* Required by the MM::Camera API.
* Th initialize function sets the camera in Extended Shutter mode 
* (by switching the frame rate off).
* We set here the shutter property absolute value, then toggle the
* framerate property on and off to get a framerate close to 
* 1 / exposure time.
* This may not work for all Point Grey cameras.  Looks at the example:
* ExtendedShutterEx
*/
void PointGrey::SetExposure(double exp)
{
   PropertyInfo propInfo;
   propInfo.type = SHUTTER;
   Error error = cam_.GetPropertyInfo(&propInfo);
   if (error != PGRERROR_OK) {
      LogMessage(error.GetDescription(), false);
      return;
   }

   if (propInfo.present) {
      if (exp > propInfo.absMax) {
         exp = propInfo.absMax;
      } 
      if (exp < propInfo.absMin) {
         exp = propInfo.absMin;
      }

      Property prop;
      prop.type = SHUTTER;
      error = cam_.GetProperty(&prop);
      if (error != PGRERROR_OK) {
         LogMessage(error.GetDescription(), false);
         return;
      }
      prop.absValue = (float) exp;
      error = cam_.SetProperty(&prop);
      if (error != PGRERROR_OK) {
         LogMessage(error.GetDescription(), false);
         return;
      }
      error = cam_.GetProperty(&prop);
      if (error != PGRERROR_OK) {
         LogMessage(error.GetDescription(), false);
         return;
      }
      exposureTimeMs_ = prop.absValue;

      // Now toggle the framerate property to make the
      // framerate as fast as possible at this exposure
      propInfo.type = FRAME_RATE;
      Error error = cam_.GetPropertyInfo(&propInfo);
      if (error != PGRERROR_OK) {
         LogMessage(error.GetDescription(), false);
         return;
      }
      prop.type = FRAME_RATE;
      error = cam_.GetProperty(&prop);
      if (error != PGRERROR_OK) {
         LogMessage(error.GetDescription(), false);
         return;
      }
      prop.onOff = true;
      error = cam_.SetProperty(&prop);
      if (error != PGRERROR_OK) {
         LogMessage(error.GetDescription(), false);
         return;
      }
      // possibly need to wait here?
      prop.onOff = false;
      error = cam_.SetProperty(&prop);
      if (error != PGRERROR_OK) {
         LogMessage(error.GetDescription(), false);
         return;
      }

   }
   unsigned long timeout = (unsigned long) (3.0 * exp) + 50;
   if (triggerMode_ == TRIGGER_EXTERNAL)
   {
      timeout = externalTriggerGrabTimeout_;
   }
   SetGrabTimeout(&cam_, timeout );

}

/***********************************************************************
* Returns the current binning factor.
* Required by the MM::Camera API.
*/
int PointGrey::GetBinning() const
{
   if (HasProperty(g_Format7Mode) )
   {
      char mode[MM::MaxStrLength];
      int ret = GetProperty(g_Format7Mode, mode);
      if (ret != DEVICE_OK) 
      {
         return ret;
      }

      try
      {
         return mode2Bin_.at(mode);
      } catch (const std::out_of_range& /*oor*/) {
         // very ugly to use try/catch here, but I somehow can not get an iterator to compile
      }

   }

	return 1;
}

/***********************************************************************
* Sets binning factor.
* Required by the MM::Camera API.
*/
int PointGrey::SetBinning(int binF)
{
   if (HasProperty(g_Format7Mode) )
   {
      std::map<long, std::string>::iterator it = bin2Mode_.find(binF);
      if (it != bin2Mode_.end())
      {
         return SetProperty(g_Format7Mode, it->second.c_str());
      }
   }

   // not sure if we should return an error code here
   return DEVICE_OK;
}

/***********************************************************************
 * Required by the MM::Camera API
 */
int PointGrey::StartSequenceAcquisition(double interval)
{
	return StartSequenceAcquisition(LONG_MAX, interval, false);            
}

/***********************************************************************                                                                       
* Stop and wait for the Sequence thread to finish                                  
*/                                                                        
int PointGrey::StopSequenceAcquisition()                                     
{
   isCapturing_ = false;
   Error error = cam_.StopCapture();
   int ret = DEVICE_OK;
   if (error != PGRERROR_OK)
   {
      SetErrorText(ALLERRORS, error.GetDescription());
      ret = ALLERRORS;
   }
   ret = SetTriggerMode(&cam_, snapTriggerMode_);
   if (ret != DEVICE_OK)
   {
      return ret;
   }
   error = cam_.StartCapture();
   return GetCoreCallback()->AcqFinished(this, ret);                                                      
} 

/***********************************************************************
* This implementation of Sequence Acquisition uses callbacks from the PointGrey
* API.  The global function PGCallback matches the ImageEvent typedef.
* All it does (in a complicated way) is to call out InsertImage function
* that inserts the newly acquired image into the circular buffer.
* Because of the syntax, InsertImage needs to be const, which poses a few
* problems maintaining state.
*/
int PointGrey::StartSequenceAcquisition(long numImages, double /* interval_ms */, 
		bool stopOnOverflow)
{
   stopOnOverflow_ = stopOnOverflow;
   imageCounter_ = 0;
   desiredNumImages_ = numImages;
   sequenceStartTime_ = MM::MMTime(0);

	if (IsCapturing())
		return DEVICE_CAMERA_BUSY_ACQUIRING;

   snapTriggerMode_ = triggerMode_;
   Error error = cam_.StopCapture();
   if (error != PGRERROR_OK)
   {
      SetErrorText(ALLERRORS, error.GetDescription());
      return ALLERRORS;
   }
   int ret = SetTriggerMode(&cam_, TRIGGER_INTERNAL);
   if (ret != DEVICE_OK)
   {
      return ret;
   }

	ret = GetCoreCallback()->PrepareForAcq(this);
	if (ret != DEVICE_OK)
		return ret;

   error = cam_.StartCapture( PGCallback, this);
   if (error != PGRERROR_OK)
   {
      cam_.StopCapture();
      SetErrorText(ALLERRORS, error.GetDescription());
      return ALLERRORS;
   }
   isCapturing_ = true;

	return DEVICE_OK;
}

/***********************************************************************
 * Inserts Image and MetaData into MMCore circular Buffer
 */
int PointGrey::InsertImage(Image* pImg) const
{
	int ret = DEVICE_OK;

   int frameCounter = pImg->GetMetadata().embeddedFrameCounter;
   // frameCounter seems to be always 0???

   if (imageCounter_ == desiredNumImages_) {
      // TODO: we need to call StopCapture(), however, StopCapture() will 
      // block until the callback function (which is this function) returns
      // One solution could be to set a lock, spin up a thread that calls
      // StopSequence after the lock is released
   }

   TimeStamp ts = pImg->GetTimeStamp();
	MM::MMTime timeStamp = MM::MMTime( (long) ts.seconds, (long) ts.microSeconds);
   char label[MM::MaxStrLength];
	this->GetLabel(label);
   // TODO: we want to set the sequenceStartTimeStamp_ here but can not do so since we are const
   // if (imageCounter_ == 0) {
   //    sequenceStartTimeStamp_ = timeStamp;
   // }

	// Important:  metadata about the image are generated here:
	Metadata md;
	md.put(MM::g_Keyword_Metadata_StartTime, CDeviceUtils::ConvertToString(sequenceStartTime_.getMsec()));
	md.put(MM::g_Keyword_Elapsed_Time_ms, CDeviceUtils::ConvertToString((timeStamp).getMsec()));
	md.put(MM::g_Keyword_Metadata_ImageNumber, CDeviceUtils::ConvertToString(imageCounter_));
   md.put("FrameCounter", frameCounter); // framecounter is always 0 for me
	//md.put(MM::g_Keyword_Metadata_ROI_X, CDeviceUtils::ConvertToString( (long) roiX_)); 
	//md.put(MM::g_Keyword_Metadata_ROI_Y, CDeviceUtils::ConvertToString( (long) roiY_)); 
	
   // TODO: we want to increment the image counter but can not do so since we are const
	// imageCounter_++;

	char buf[MM::MaxStrLength];
	GetProperty(MM::g_Keyword_Binning, buf);
	md.put(MM::g_Keyword_Binning, buf);

   unsigned int w = pImg->GetCols();
   unsigned int h = pImg->GetRows();
   unsigned int b = pImg->GetDataSize() / (w * h);

   ret = GetCoreCallback()->InsertImage(this, pImg->GetData(), w, h, b, md.Serialize().c_str(), false);
	if (!stopOnOverflow_ && ret == DEVICE_BUFFER_OVERFLOW)
	{
		// do not stop on overflow - just reset the buffer
		GetCoreCallback()->ClearImageBuffer(this);
		GetCoreCallback()->InsertImage(this, pImg->GetData(), w, h, b, md.Serialize().c_str(), false);
      return DEVICE_OK;
	} 

   return ret;
}


//***********************************************************************

bool PointGrey::IsCapturing() 
{
   // TODO: evaluate if a lock is needed
   return isCapturing_;
}


/***********************************************************************
* Handles Binning property.
* Although some PGR cameras support binning by switching modes, there is no
* binning "function" in the SDK.  To full support binning would need a 
* lot of knowledge of specific cameras in this device adapter,  which is
* something we want to avoid.  Instruct the user to read the camera's data
* sheet instead.
*/
int PointGrey::OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
	{
		pProp->Set( (long) GetBinning());
   } else if (eAct == MM::AfterSet) 
   {
      long binning;
      pProp->Get(binning);
      return SetBinning((int) binning);
   }

	return DEVICE_OK;
}


/**********************************************************************
 * Let's the user specify a Format-7 mode for various binning settings
 */

int PointGrey::OnBinningFromFormat7Mode(MM::PropertyBase* pProp, MM::ActionType eAct, long value)
{
   if (eAct == MM::AfterSet)
   {
      std::string format;
      pProp->Get(format);
      bin2Mode_[value] = format;
      mode2Bin_[format] = value;
      std::ostringstream os;
      os << value;
      AddAllowedValue(MM::g_Keyword_Binning, os.str().c_str());
   } else if (eAct == MM::BeforeGet)
   {
      if (bin2Mode_.find(value) != bin2Mode_.end())
      {
         pProp->Set(bin2Mode_[value].c_str());
      } else 
      {
        bin2Mode_[value] = g_NotSet;
        pProp->Set(g_NotSet);
      }

   }

   return DEVICE_OK;
}


/***********************************************************************
 * Handles Absolute value aspect of properties
 * Has specific handling of temperature
 */
int PointGrey::OnAbsValue(MM::PropertyBase* pProp, MM::ActionType eAct, long index) 
{ 
   Property prop(g_PropertyTypes[index]);
   Error error = cam_.GetProperty(&prop);
   if (error != PGRERROR_OK) {
      SetErrorText(ALLERRORS, error.GetDescription());
      return ALLERRORS;  
   }

   if (eAct == MM::AfterSet) 
   {
      double absVal;
      pProp->Get(absVal);

      prop.absValue = (float) absVal;
      error = cam_.SetProperty(&prop);
      if (error != PGRERROR_OK) {
         SetErrorText(ALLERRORS, error.GetDescription());
         return ALLERRORS;  
      }
   } else if (eAct == MM::BeforeGet) 
   {
      if (g_PropertyTypes[index] == TEMPERATURE)  // temperature
      {
           double kelvins = prop.valueA / 10.0;
           double celcius = kelvins - 273.15;
           pProp->Set(celcius);
      } else {
         pProp->Set(prop.absValue);
      }

   }
   return DEVICE_OK;
}

/***********************************************************************
 * Handles value aspect of extended properties
 */
int PointGrey::OnValue(MM::PropertyBase* pProp, MM::ActionType eAct, long index) 
{ 
   Property prop(g_PropertyTypes[index]);
   Error error = cam_.GetProperty(&prop);
   if (error != PGRERROR_OK) {
      SetErrorText(ALLERRORS, error.GetDescription());
      return ALLERRORS;  
   }

   if (eAct == MM::AfterSet) 
   {
      long val;
      pProp->Get(val);

      prop.valueA = (unsigned int ) val;
      error = cam_.SetProperty(&prop);
      if (error != PGRERROR_OK) {
         SetErrorText(ALLERRORS, error.GetDescription());
         return ALLERRORS;  
      }
   } else if (eAct == MM::BeforeGet) 
   {
      pProp->Set( (long) prop.valueA);

   }
   return DEVICE_OK;
}

/***********************************************************************
 * Handles Properties On/Off requests
 */
int PointGrey::OnOnOff(MM::PropertyBase* pProp, MM::ActionType eAct, long index)
{ 
   Property prop(g_PropertyTypes[index]);
   Error error = cam_.GetProperty(&prop);
   if (error != PGRERROR_OK) {
      SetErrorText(ALLERRORS, error.GetDescription());
      return ALLERRORS;  
   }

   if (eAct == MM::AfterSet) 
   {
      std::string val;
      pProp->Get(val);
      bool onOff = true;
      if (val == "Off") {
         onOff = false;
      }
      if (onOff != prop.onOff) {
         prop.onOff = onOff;
         error = cam_.SetProperty(&prop);
         if (error != PGRERROR_OK) {
            SetErrorText(ALLERRORS, error.GetDescription());
            return ALLERRORS;  
         }
      }
   } else if (eAct == MM::BeforeGet) 
   {
      std::string val = "Off";
      if (prop.onOff) {
         val = "On";
      }
      pProp->Set(val.c_str());
   }

   return DEVICE_OK;
}

/***********************************************************************
 * Handles Auto/Manual requests for extended properties
 */
int PointGrey::OnAutoManual(MM::PropertyBase* pProp, MM::ActionType eAct, long index)
{
   Property prop(g_PropertyTypes[index]);
   Error error = cam_.GetProperty(&prop);
   if (error != PGRERROR_OK) {
      SetErrorText(ALLERRORS, error.GetDescription());
      return ALLERRORS;  
   }

   if (eAct == MM::AfterSet) 
   {
      std::string val;
      pProp->Get(val);
      bool autoManual = true;
      if (val == "Manual") {
         autoManual = false;
      }
      if (autoManual != prop.autoManualMode) {
         prop.autoManualMode = autoManual;
         error = cam_.SetProperty(&prop);
         if (error != PGRERROR_OK) {
            SetErrorText(ALLERRORS, error.GetDescription());
            return ALLERRORS;  
         }
      }
   } else if (eAct == MM::BeforeGet) 
   {
      std::string val = "Manual";
      if (prop.autoManualMode) {
         val = "Auto";
      }
      pProp->Set(val.c_str());
   }

   return DEVICE_OK;
}


/***********************************************************************
 * Handles VideoModeAndFrameRate Property
 */
int PointGrey::OnVideoModeAndFrameRate(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet) 
   {
      std::string requestedMode;
      pProp->Get(requestedMode);
      VideoMode vm;
      FrameRate fr;
      int ret = VideoModeAndFrameRateEnumsFromString(requestedMode, vm, fr);
      if (ret != DEVICE_OK) {
         SetErrorText(ALLERRORS, "Requested Video Mode and Frame Rate was not found");
         return ALLERRORS;
      }
      // Note: Format 7 can not be set using this function!
      Error error = cam_.SetVideoModeAndFrameRate(vm, fr);
      if (error != PGRERROR_OK) {
         SetErrorText(ALLERRORS, error.GetDescription());
         return ALLERRORS;
      }
      // Work around an issue in the Micro-Manager GUI: If we do not snap an image
      // here, the bitdepth information can easily go stale
      SnapImage();
      // make sure that sequence acquisition stops
      GetImageBuffer();
   } 
   else if (eAct == MM::BeforeGet) {
      // Find current video mode and frame rate
      VideoMode currVideoMode;
      FrameRate currFrameRate;
      Error error = cam_.GetVideoModeAndFrameRate(&currVideoMode, &currFrameRate);
      if (error != PGRERROR_OK)
      {
         SetErrorText(ALLERRORS, error.GetDescription());
         return ALLERRORS;
      }
      std::string currentMode;
      VideoModeAndFrameRateStringFromEnums(currentMode, currVideoMode, currFrameRate);
      pProp->Set(currentMode.c_str());
   }
   return DEVICE_OK;
}



/***********************************************************************
 * Handles Pixel Type requests 
 */
int PointGrey::OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (!f7InUse_) 
   {
      return DEVICE_INTERNAL_INCONSISTENCY;
   }

   Format7ImageSettings format7ImageSettings;
   unsigned int packetSize;
   float percentage;
   Error error = cam_.GetFormat7Configuration(&format7ImageSettings, &packetSize, &percentage);
   if (error != PGRERROR_OK) {
      SetErrorText(ALLERRORS, error.GetDescription());
      return ALLERRORS;
   }

   if (eAct == MM::BeforeGet)
   {
      std::string setting = PixelTypeAsString(format7ImageSettings.pixelFormat);
      pProp->Set(setting.c_str());
   } 
   else if (eAct == MM::AfterSet)
   {
      std::string setting;
      pProp->Get(setting);
      format7ImageSettings.pixelFormat = PixelFormatFromString(setting);
      bool valid;
      Format7PacketInfo format7PacketInfo;
      error = cam_.ValidateFormat7Settings(&format7ImageSettings, &valid, &format7PacketInfo);
      if (valid) {
         cam_.StopCapture();
         error = cam_.SetFormat7Configuration(&format7ImageSettings,
                        format7PacketInfo.recommendedBytesPerPacket);
         if (error != PGRERROR_OK) {
            SetErrorText(ALLERRORS, error.GetDescription());
            return ALLERRORS;
         }
         error = cam_.StartCapture();
         if (error != PGRERROR_OK) {
            SetErrorText(ALLERRORS, error.GetDescription());
            return ALLERRORS;
         }
      } else {
         SetErrorText(ALLERRORS, "Failed to generate valid Format 7 settings");
         return ALLERRORS;
      }
      if (format7ImageSettings.pixelFormat == PIXEL_FORMAT_RGB8) {
         nComponents_ = 4;
      } else {
         nComponents_ = 1;
      }
            
      // if we do not snap an image, MM 2.0 gets the bitdepths wrong. Delete once fixed upstream
      SnapImage();
      // make sure that sequence acquisition stops
      GetImageBuffer();
   }

   return DEVICE_OK;
}

/***********************************************************************
* Handles "Format7 Mode" property.
*/
int PointGrey::OnFormat7Mode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (!f7InUse_) 
   {
      return DEVICE_INTERNAL_INCONSISTENCY;
   }

   Format7ImageSettings fmt7ImageSettings;
   unsigned int packetSize;
   float percentage;
   Error error = cam_.GetFormat7Configuration(&fmt7ImageSettings, &packetSize, &percentage);
   if (error != PGRERROR_OK) {
      SetErrorText(ALLERRORS, error.GetDescription());
      return ALLERRORS;
   }

	if (eAct == MM::AfterSet)
	{
      std::string mode;
      pProp->Get(mode);
      Mode f7Mode;
      int ret = Format7ModeFromString(mode, &f7Mode);
      if (ret != DEVICE_OK)
         return ret;

      Format7Info format7Info;
      format7Info.mode = f7Mode;
      bool supported;
      error = cam_.GetFormat7Info(&format7Info, &supported);
      if (error != PGRERROR_OK) {
         SetErrorText(ALLERRORS, error.GetDescription());
         return ALLERRORS;
      }
      if (!supported) {
         return DEVICE_INTERNAL_INCONSISTENCY;
      }


      Format7ImageSettings newF7Settings;
      newF7Settings.mode = f7Mode;
      // TODO: propoagate old ROI
      newF7Settings.offsetX = 0;
      newF7Settings.offsetY = 0;
      newF7Settings.width = format7Info.maxWidth;
      newF7Settings.height = format7Info.maxHeight;

      newF7Settings.pixelFormat = fmt7ImageSettings.pixelFormat;

      bool valid;
      Format7PacketInfo format7PacketInfo;
      error = cam_.ValidateFormat7Settings(&newF7Settings, &valid, &format7PacketInfo);
      if (valid) {
         // Set the settings to the camera
         error = cam_.StopCapture();  // do not check for error, since the camera 
         // may not be capturing because of previous errors
         error = cam_.SetFormat7Configuration(&newF7Settings,
                     format7PacketInfo.recommendedBytesPerPacket);
         if (error != PGRERROR_OK) 
         {
            SetErrorText(ALLERRORS, error.GetDescription());
            return ALLERRORS;
         }
         error = cam_.StartCapture();
         if (error != PGRERROR_OK) 
         {
            SetErrorText(ALLERRORS, error.GetDescription());
            return ALLERRORS;
         }
         SnapImage();
         // make sure that sequence acquisition stops
         GetImageBuffer();
      } else {
         SetErrorText(ALLERRORS, "Failed to generate correct settings for this mode");
         return ALLERRORS;
      }

      updatePixelFormats(format7Info.pixelFormatBitField);

	}
	else if (eAct == MM::BeforeGet)
	{
      std::string mode = Format7ModeAsString(fmt7ImageSettings.mode);
      pProp->Set(mode.c_str());
	}

	return DEVICE_OK;
}

/***************************************************************
* Handles Trigger Modes
*
*/
int PointGrey::OnTriggerMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
	{
      std::string mode;
      pProp->Get(mode);
      unsigned short tMode;
      int ret = TriggerModeFromString(mode, tMode);
      if (ret != DEVICE_OK)
      {
         return ret;
      }
      Error error = cam_.StopCapture();
      // if camera is not capturing, StopCapture will return an error.
      // probably safe to ignore, it is bad to return
      if (error != PGRERROR_OK && error != PGRERROR_ISOCH_NOT_STARTED)
      {
         SetErrorText(ALLERRORS, error.GetDescription());
         return ALLERRORS;
      }
      ret = SetTriggerMode(&cam_, tMode);
      if (ret != DEVICE_OK)
      {
         return DEVICE_OK;
      }
      error = cam_.StartCapture();
      if (error != PGRERROR_OK)
      {
         SetErrorText(ALLERRORS, error.GetDescription());
         return ALLERRORS;
      }

      triggerMode_ = tMode;
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set(TriggerModeAsString(triggerMode_).c_str());
   }

   return DEVICE_OK;
}

/***********************************************************************
* Sets a register in the camera to indicate the endianness of the 
* output.  It seems that MM wants little endian, most likely since 
* everything happens on Intel platforms
*/
int PointGrey::SetEndianess(bool little)
{
   const unsigned int theRegister = 0x630;
   const unsigned int endianBit = 0x01 << 23;
   // TODO: make sure that the dc1394 > 1.32
   unsigned int registerValue;
   Error error = cam_.ReadRegister(theRegister, &registerValue);
   if (error != PGRERROR_OK) {
      SetErrorText(ALLERRORS, error.GetDescription());
      return ALLERRORS;
   }
   if (little) {
      registerValue |= endianBit;
   } else {
      registerValue &= ~endianBit;
   }

   error = cam_.WriteRegister(theRegister, registerValue);
      if (error != PGRERROR_OK) {

      SetErrorText(ALLERRORS, error.GetDescription());
      return ALLERRORS;
   }

   return DEVICE_OK;
}

void PointGrey::updatePixelFormats(unsigned int pixelFormatBitField)
{
   if (pixelFormatBitField & PIXEL_FORMAT_RAW8) {
      pixelFormat8Bit_ =  PIXEL_FORMAT_RAW8;
   } else { // Should we even check? 
      pixelFormat8Bit_ = PIXEL_FORMAT_MONO8;
   }

   if (pixelFormatBitField & PIXEL_FORMAT_RAW16) {
      pixelFormat16Bit_ = PIXEL_FORMAT_RAW16;
   } else {
      pixelFormat16Bit_ = PIXEL_FORMAT_MONO16;
   }

}

/////////////////////////////////////////////////////////////////////////////////
// Functions to manage Camera ID translation between PG and our system
////////////////////////////////////////////////////////////////////////////////
/**
 * Given a camera index, find the PGRGuid of the corresponding camera
 */
int PointGrey::CameraPGRGuid(BusManager* busMgr, PGRGuid* guid, int nr) 
{
   Error error = busMgr->GetCameraFromIndex(nr, guid);
   if (error != PGRERROR_OK)
   {
      SetErrorText(ALLERRORS, error.GetDescription());
      return ALLERRORS;
   }
   return DEVICE_OK;
}
   
/**
 * Given a PGRGUid, set our human-readable name
 */
int PointGrey::CameraID(PGRGuid id, std::string* camIdString)
{
   FlyCapture2::Camera cam;

	// -------------------------------------------------------------------------------------
	// Open camera device
   Error error = cam.Connect(&id);
   if (error != PGRERROR_OK)
   {
      //SetErrorText(ALLERRORS, error.GetDescription());
      return ALLERRORS;
   }

   // Get the camera information
   CameraInfo camInfo;
   error = cam.GetCameraInfo(&camInfo);
   if (error != PGRERROR_OK)
   {
      cam.Disconnect();
      //SetErrorText(ALLERRORS, error.GetDescription());
      return ALLERRORS;
   }

   std::string sep = "_";
   *camIdString = camInfo.modelName + sep;
   *camIdString += std::to_string( (_ULonglong) camInfo.serialNumber);
   error = cam.Disconnect();

   return DEVICE_OK;

}

/**
 * Given our human-readable ID, find the Point Grey PGRGuid
 */
int PointGrey::CameraGUIDfromOurID(BusManager* busMgr, PGRGuid* guid, std::string ourId)
{
   boolean found = false;
   unsigned int numCameras;
   PGRGuid localGuid;
   Error error = busMgr->GetNumOfCameras(&numCameras);
   if (error != PGRERROR_OK)
   {
      SetErrorText(ALLERRORS, error.GetDescription());
      return ALLERRORS;
   }
   for (unsigned int i = 0; i < numCameras && !found; i++) {
      error = busMgr->GetCameraFromIndex(i, &localGuid); 
      if (error != PGRERROR_OK)
      {
         SetErrorText(ALLERRORS, error.GetDescription());
         return ALLERRORS;
      }
      std::string testId;
      int ret = CameraID(localGuid, &testId);
      if (ret != DEVICE_OK) {
         return ret;
      }
      if (testId == ourId) {
         found = true;
         *guid = localGuid;
         return DEVICE_OK;
      }
   }

   SetErrorText(3000, "Camera not found");
   return 3000; 
}

//////////////////////////////////////////////////////////////////////////////////////
// Functions to translate human readable versions to PGR enums and back
//////////////////////////////////////////////////////////////////////////////////////

void PointGrey::VideoModeAndFrameRateStringFromEnums(std::string &readableString, 
   FlyCapture2::VideoMode vm, FlyCapture2::FrameRate fr) const
{
   readableString = g_VideoModes[vm] + '_' + g_FrameRates[fr];
}

int PointGrey::VideoModeAndFrameRateEnumsFromString(std::string readableString, 
   FlyCapture2::VideoMode &vm, FlyCapture2::FrameRate &fr) const
{
   std::vector<std::string> parts;
   std::stringstream ss(readableString);
   std::string item;
   while (getline(ss, item, '_')) {
      parts.push_back(item);
   }
   if (parts.size() != 2) {
      return DEVICE_INTERNAL_INCONSISTENCY;
   }

   // find matching Videomode and Framerate by cycling brute force through our arrays
   boolean found = false;
   unsigned int counter = 0;
   while (!found && counter < g_NumVideoModes) {
      if (parts[0] == g_VideoModes[counter]) {
         found = true;
         vm = (VideoMode) counter;
      }
      counter++;
   }
   if (!found) {
      return DEVICE_INTERNAL_INCONSISTENCY;
   }

   found = false;
   counter = 0;
   while (!found && counter < g_NumFrameRates) {
      if (parts[1] == g_FrameRates[counter]) {
         found = true;
         fr = (FrameRate) counter;
      }
      counter++;
   }

   return DEVICE_OK;
}


std::string PointGrey::PixelTypeAsString(PixelFormat pixelFormat) const
{  
   switch (pixelFormat) {
   case (PIXEL_FORMAT_MONO8) :
      return "8-bit"; break;
   case (PIXEL_FORMAT_RAW8) :
      return "8-bit"; break;
   case (PIXEL_FORMAT_MONO16) :
      return "16-bit"; break;
   case (PIXEL_FORMAT_RAW16) :
      return "16-bit"; break;
   case (PIXEL_FORMAT_RGB8) :
      return "RGB32"; break;
   case (PIXEL_FORMAT_RGB16) :
      return "RGB64"; break;
   default:
      return "Unknown";
   }
}

PixelFormat PointGrey::PixelFormatFromString(std::string pixelType) const
{
   if (pixelType == "8-bit")
      return pixelFormat8Bit_;
   else if (pixelType == "16-bit")
      return pixelFormat16Bit_;
   else if (pixelType == "RGB32")
      return PIXEL_FORMAT_RGB8;
   else if (pixelType == "RGB64")
      return PIXEL_FORMAT_RGB16;

   return UNSPECIFIED_PIXEL_FORMAT;
}

std::string PointGrey::Format7ModeAsString(Mode mode) const
{
   // take a shortcut:
   int iMode = (int) mode;
   std::ostringstream os;
   os << "Mode-" << iMode;
   return os.str();
}

std::string PointGrey::TriggerModeAsString(const unsigned short mode) const
{
   switch (mode) 
   {
   case TRIGGER_INTERNAL:
      return g_InternalTrigger;
   case TRIGGER_EXTERNAL:
      return g_ExternalTrigger;
   case TRIGGER_SOFTWARE:
      return g_SoftwareTrigger;
   }
   return "Unknown Trigger Mode";
}

int PointGrey::Format7ModeFromString(std::string modeString, Mode* mode) const
{
   // Split the mode string by "-"
   std::vector<std::string> parts;
   std::stringstream ss(modeString);
   std::string item;
   while (getline(ss, item, '-')) {
      parts.push_back(item);
   }
   if (parts.size() != 2) {
      return DEVICE_INTERNAL_INCONSISTENCY;
   }

   int iMode = atol(parts[1].c_str());
   *mode = (Mode) iMode;

   return DEVICE_OK;
}

int PointGrey::TriggerModeFromString(std::string mode, unsigned short& tMode)
{
   if (mode == g_InternalTrigger)
   {
      tMode = TRIGGER_INTERNAL;
   } else 
   if (mode == g_ExternalTrigger)
   {
      tMode = TRIGGER_EXTERNAL;
   } else 
   if (mode == g_SoftwareTrigger)
   {
      tMode = TRIGGER_SOFTWARE;
   } else 
   {
      return ERR_UNKNOWN_TRIGGER_MODE_STRING;
   }

   return DEVICE_OK;
}

const char* PointGrey::GetBusSpeedAsString(BusSpeed speed)
{  
   switch (speed) 
   {
   case BUSSPEED_S100:
      return "s100";
   case BUSSPEED_S200:
         return "s200";
   case BUSSPEED_S400:
      return "s400";
   case BUSSPEED_S480:
      return "s480";
   case BUSSPEED_S800:
      return "s800";
   case BUSSPEED_S1600:
      return "s1600";
   case BUSSPEED_S3200:
      return "s3200";
   case BUSSPEED_S5000:
      return "s5000";

   default:
      return "Unknown bus speed";
   }
}

int PointGrey::CheckSoftwareTriggerPresence(FlyCapture2::Camera* pCam, bool& result)
{
	const unsigned int k_triggerInq = 0x530;
	Error error;
	unsigned int regVal = 0;

	error = pCam->ReadRegister( k_triggerInq, &regVal );

	if (error != PGRERROR_OK)
	{
		return ERR_IN_READ_REGISTER;
	}

   result = true;
	if( ( regVal & 0x10000 ) != 0x10000 )
	{
		result = false;
	} 

   return DEVICE_OK;
}

/**
 * Blocks until the camera is ready for a software trigger
 * or given timeout expired
 */
int PointGrey::PollForTriggerReady(FlyCapture2::Camera* pCam, const unsigned long timeoutMs)
{
   MM::TimeoutMs timerOut(GetCurrentMMTime(), timeoutMs);

	const unsigned int k_softwareTrigger = 0x62C;
	Error error;
	unsigned int regVal = 0;

	do
	{
		error = pCam->ReadRegister( k_softwareTrigger, &regVal );
		if (error != PGRERROR_OK)
		{
			return ERR_IN_READ_REGISTER;
		}
	} while ( (regVal >> 31) != 0  && !timerOut.expired(GetCurrentMMTime() ) );

   if ( (regVal >> 31) != 0) 
   {
      return ERR_NOT_READY_FOR_SOFTWARE_TRIGGER;
   }


	return DEVICE_OK;
}

bool PointGrey::FireSoftwareTrigger(FlyCapture2::Camera* pCam )
{
	const unsigned int k_softwareTrigger = 0x62C;
	const unsigned int k_fireVal = 0x80000000;
	Error error;

	error = pCam->WriteRegister( k_softwareTrigger, k_fireVal );
	if (error != PGRERROR_OK)
	{
		return false;
	}

	return true;
}

int PointGrey::SetTriggerMode(FlyCapture2::Camera* pCam, const unsigned short newMode) 
{
   if ( std::find(availableTriggerModes_.begin(), availableTriggerModes_.end(), newMode) == 
            availableTriggerModes_.end() )
   {
      return ERR_UNAVAILABLE_TRIGGER_MODE_REQUESTED;
   }

   if (triggerMode_ != newMode) // no need to do anything
   {
      // Get current trigger settings
      TriggerMode triggerMode;
      Error error = pCam->GetTriggerMode( &triggerMode );
      if (error != PGRERROR_OK) {
         // software trigger mode not supported
      } else {
         // assume that triggerMode off is internal trigger
         if (newMode == TRIGGER_INTERNAL)
         {
            triggerMode.onOff = false;
         } else
         {
            triggerMode.onOff = true;
         }
         triggerMode.mode = 0;
         triggerMode.parameter = 0;
         triggerMode.source = 0;   // 7 for Software trigger, 0 for external
         if (newMode == TRIGGER_SOFTWARE) 
         {
            triggerMode.source = 7;
         }
         error = pCam->SetTriggerMode( &triggerMode );
         if (error != PGRERROR_OK)
         {
            SetErrorText(ALLERRORS, error.GetDescription());
            return ALLERRORS;
         }

         if (newMode == TRIGGER_SOFTWARE)
         {
            // Poll to ensure camera is ready
            int ret = PollForTriggerReady( pCam, 2000);
            if (ret != DEVICE_OK) 
            {
               return ret;
            }
         }
         triggerMode_ = newMode;
      }
   }

   // we only need to change the grabtimeout when switching between external trigger
   // and other modes.  Assume that this operation is not too costly.
   unsigned long timeout = (unsigned long) (3.0 * exposureTimeMs_) + 50;
   if (triggerMode_ == TRIGGER_EXTERNAL)
   {
      timeout = externalTriggerGrabTimeout_;
   }
   SetGrabTimeout(&cam_, timeout );

   return DEVICE_OK;
}

int PointGrey::SetGrabTimeout(FlyCapture2::Camera* pCam, const unsigned long timeoutMs)
{
   FC2Config config;
   Error error = pCam->GetConfiguration( &config );
   if (error != PGRERROR_OK)
   {
      SetErrorText(ALLERRORS, error.GetDescription());
      return ALLERRORS;
   }
	
	config.grabTimeout = timeoutMs;

	error = pCam->SetConfiguration( &config );
	if (error != PGRERROR_OK)
	{
		SetErrorText(ALLERRORS, error.GetDescription());
      return ALLERRORS;
   }

   return DEVICE_OK;
}