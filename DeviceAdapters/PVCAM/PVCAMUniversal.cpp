///////////////////////////////////////////////////////////////////////////////
// FILE:          PVCAMUniversal.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   PVCAM universal camera module
// COPYRIGHT:     University of California, San Francisco, 2006, 2007, 2008, 2009
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
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 06/30/2006
//                Contributions by:
//                   Ji Yu
//                   Nico Stuurman
//                   Arthur Edelstein
//                   Oleksiy Danikhnov
//
// HISTORY:
//                04/17/2009: Major cleanup and additions to make multiple cameras work (Nico + Nenad)
//                07/24/2012: Major refactoring (Photometrics / Yu Wang + Peter Pflibsen + Lubomir Walder)
//                            - Fixed the long lag/hang that occured when changing a property during live mode
//                            - Removed PARAM_CONT_CLEARS, PARAM_MIN_BLOCK, PARAM_NUM_MIN_BLOCK, PARAM_LOGIC_OUTPUT
//                              PARAM_NUM_OF_STRIPS_PER_CLR - these are no longer supported in PVCAM 2.9.3.14+
//                            - Refactored the Universal properties
//                            - Added:
//                            -- Asymmetric Binning, Serial Number, Firmware Version, Circular Buffer Size props.
//                            - Minor fixes, cleanup, renaming
//                            - Tweaks to post-processing code to make it more extensible, and not depend on FEAT_ID or PARAM_ID or the name of the features or parameters
//                             
//
// CVS:           $Id: PVCAMUniversal.cpp 8240 2011-12-04 01:05:17Z nico $

#ifdef WIN32
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#endif

#include "../../MMDevice/ModuleInterface.h"
#include "PVCAMAdapter.h"
#include "PVCAMParam.h"

#include <string>
#include <sstream>
#include <iomanip>
#include <algorithm>
#include <cmath>

#include "PollingThread.h"
#include "NotificationThread.h"

using namespace std;

//#define DEBUG_METHOD_NAMES

#ifdef DEBUG_METHOD_NAMES
    #define START_METHOD(name)              LogMessage(name);
    #define START_ONPROPERTY(name,action)   LogMessage(string(name)+(action==MM::AfterSet?"(AfterSet)":"(BeforeGet)"));
#else
    #define START_METHOD(name)              
    #define START_ONPROPERTY(name,action)   
#endif

#if WIN32
    #define snprintf _snprintf
#endif

// Number of references to this class
int  Universal::refCount_ = 0;
bool Universal::PVCAM_initialized_ = false;
MMThreadLock g_pvcamLock;

// Maximum pixel time to be used in case we fail to get the PARAM_PIX_TIME from the camera.
const int MAX_PIX_TIME = 1000;

// Circular buffer can be set to two modes: Auto and Manual. In manual mode we 
// use two limits, maximum number of frames in the buffer and maximum memory the
// buffer can use. In auto mode the size is calculated by PVCAM or estimated by
// our own simple algorithm.

// Circular buffer capacity in number of frames (manual mode)
const int CIRC_BUF_FRAME_CNT_DEF = 8;
const int CIRC_BUF_FRAME_CNT_MIN = 3;
const int CIRC_BUF_FRAME_CNT_MAX = 1024;
// Circular buffer size in bytes (manual mode). This value was recommended by Photometrics
const unsigned long long CIRC_BUF_SIZE_MAX_USER = 2048 * 1024ULL * 1024ULL - 8192;
// Circular buffer size limit for auto mode (if PARAM_FRAME_BUFFER_SIZE is unsupported)
const unsigned long long CIRC_BUF_SIZE_MAX_AUTO = 128 * 1024ULL * 1024ULL;

// global constants
extern const char* g_ReadoutRate;
extern const char* g_ReadoutPort;
extern const char* g_ReadoutPort_Normal;
extern const char* g_ReadoutPort_Multiplier;
extern const char* g_ReadoutPort_LowNoise;
extern const char* g_ReadoutPort_HighCap;

const char* g_Keyword_ChipName        = "ChipName";
const char* g_Keyword_SerialNumber    = "SerialNumber";
const char* g_Keyword_FirmwareVersion = "FirmwareVersion";
const char* g_Keyword_CCDSerSize      = "X-dimension";
const char* g_Keyword_CCDParSize      = "Y-dimension";
const char* g_Keyword_FWellCapacity   = "FullWellCapacity";
const char* g_Keyword_TriggerMode     = "TriggerMode";
const char* g_Keyword_ExposeOutMode   = "ExposeOutMode";
const char* g_Keyword_ClearCycles     = "ClearCycles";
const char* g_Keyword_ColorMode       = "ColorMode";
const char* g_Keyword_TriggerTimeout  = "Trigger Timeout (secs)";
const char* g_Keyword_ActualGain      = "Actual Gain e/ADU";
const char* g_Keyword_ReadNoise       = "Current Read Noise";
const char* g_Keyword_BinningX        = "BinningX";
const char* g_Keyword_BinningY        = "BinningY";
const char* g_Keyword_MultiplierGain  = "MultiplierGain";
const char* g_Keyword_PreampOffLimit  = "PreampOffLimit";
const char* g_Keyword_Yes             = "Yes";
const char* g_Keyword_No              = "No";
const char* g_Keyword_FrameCapable    = "FTCapable";
// Carefully when changing the following Color-related property names,
// the names are used in WhiteBalance plugin and changing some may break the plugin.
const char* g_Keyword_RGB32           = "Color";                 // ON or OFF switch, turns Debayering on or off
const char* g_Keyword_RedScale        = "Color - Red scale";     // Number (R-factor for White balance)
const char* g_Keyword_BlueScale       = "Color - Blue scale";    // Number (B-factor for White balance)
const char* g_Keyword_GreenScale      = "Color - Green scale";   // Number (G-factor for White balance)
const char* g_Keyword_AlgorithmCFA    = "Color - Algorithm CFA"; // Mask used for Debayering algorithm (can be user-selected)
const char* g_Keyword_AlgorithmCFAAuto= "Color - Algorithm CFA Auto"; // ON or OFF, if ON the Mask is auto-selected
const char* g_Keyword_AlgorithmInterp = "Color - Algorithm Interpolation"; // Debayering algorithm setting
const char* g_Keyword_SensorCFA       = "Color - Sensor CFA";    // Camera reported sensor mask name (can be used to set correct algorithm)
const char* g_Keyword_RGGB            = "R-G-G-B"; // Mask type, used in White Balance as well
const char* g_Keyword_BGGR            = "B-G-G-R"; // Mask type, used in White Balance as well
const char* g_Keyword_GRBG            = "G-R-B-G"; // Mask type, used in White Balance as well
const char* g_Keyword_GBRG            = "G-B-R-G"; // Mask type, used in White Balance as well

const char* g_Keyword_ON              = "ON";
const char* g_Keyword_OFF             = "OFF";
const char* g_Keyword_Replication     = "Nearest Neighbor Replication";
const char* g_Keyword_Bilinear        = "Bilinear";
const char* g_Keyword_SmoothHue       = "Smooth Hue";
const char* g_Keyword_AdaptiveSmoothHue = "Adaptive Smooth Hue (edge detecting)";
const char* g_Keyword_AcqMethod      = "AcquisitionMethod";
const char* g_Keyword_AcqMethod_Callbacks = "Callbacks";
const char* g_Keyword_AcqMethod_Polling   = "Polling";
const char* g_Keyword_OutputTriggerFirstMissing = "OutputTriggerFirstMissing";
const char* g_Keyword_CircBufFrameCnt      = "CircularBufferFrameCount";
const char* g_Keyword_CircBufSizeAuto      = "CircularBufferAutoSize";
const char* g_Keyword_CircBufFrameRecovery = "CircularBufferFrameRecovery";
#ifdef PVCAM_SMART_STREAMING_SUPPORTED
const char* g_Keyword_SmartStreamingValues   = "SMARTStreamingValues[ms]";
const char* g_Keyword_SmartStreamingEnable   = "SMARTStreamingEnabled";
#endif
const char* g_Keyword_MetadataEnabled  = "MetadataEnabled";
const char* g_Keyword_CentroidsEnabled = "CentroidsEnabled";
const char* g_Keyword_CentroidsRadius  = "CentroidsRadius";
const char* g_Keyword_CentroidsCount   = "CentroidsCount";
const char* g_Keyword_FanSpeedSetpoint = "FanSpeedSetpoint";

// Universal parameters
// These parameters, their ranges or allowed values are read out from the camera automatically.
// Use these parameters for simple camera properties that do not need special treatment when a
// parameter is changed. See PVCAMProperty class and OnUniversalProperty(). These are still
// not perfect, due to PVCAM and MM nature it's always better to create custom property with
// unique hanler to properly handle the change in the property.
// - Parameter that is not supported by a particular camera is not displayed.
// - Parameter that is read-only is displayed as read-only
// - Enum parameters are displayed as combo boxes with strings read out from the camera
// - So far only parameters in double range can be used
// Do not use these for static camera properties that never changes. It's more efficient to create
// a simple readonly MM property without a handler (see examples in Initialize())
ParamNameIdPair g_UniversalParams[] = {
   {MM::g_Keyword_Offset, "PARAM_ADC_OFFSET",         PARAM_ADC_OFFSET},         // INT16
   {"PMode",              "PARAM_PMODE",              PARAM_PMODE},              // ENUM
   {"ClearMode",          "PARAM_CLEAR_MODE",         PARAM_CLEAR_MODE},         // ENUM
   {"PreampDelay",        "PARAM_PREAMP_DELAY",       PARAM_PREAMP_DELAY},       // UNS16
   {"PreampOffLimit",     "PARAM_PREAMP_OFF_CONTROL", PARAM_PREAMP_OFF_CONTROL}, // UNS32 // preamp is off during exposure if exposure time is less than this
   {"MaskLines",          "PARAM_PREMASK",            PARAM_PREMASK},            // UNS16
   {"PrescanPixels",      "PARAM_PRESCAN",            PARAM_PRESCAN},            // UNS16
   {"PostscanPixels",     "PARAM_POSTSCAN",           PARAM_POSTSCAN},           // UNS16
   {"ShutterMode",        "PARAM_SHTR_OPEN_MODE",     PARAM_SHTR_OPEN_MODE},     // ENUM
   {"ShutterOpenDelay",   "PARAM_SHTR_OPEN_DELAY",    PARAM_SHTR_OPEN_DELAY},    // UNS16 (milliseconds)
   {"ShutterCloseDelay",  "PARAM_SHTR_CLOSE_DELAY",   PARAM_SHTR_CLOSE_DELAY},   // UNS16 (milliseconds)
};
const int g_UniversalParamsCount = sizeof(g_UniversalParams)/sizeof(ParamNameIdPair);

///////////////////////////////////////////////////////////////////////////////
// &Universal constructor/destructor
Universal::Universal(short cameraId) :
CCameraBase<Universal> (),
initialized_(false),
imagesToAcquire_(0), imagesInserted_(0), imagesAcquired_(0), imagesRecovered_(0),
hPVCAM_(0),
cameraId_(cameraId),
cameraModel_(PvCameraModel_Generic),
circBufSizeAuto_(true),
circBufFrameCount_(CIRC_BUF_FRAME_CNT_DEF), // Sizes larger than 3 caused image tearing in ICX-674. Reason unknown.
circBufFrameRecoveryEnabled_(true),
stopOnOverflow_(true),
snappingSingleFrame_(false),
singleFrameModeReady_(false),
sequenceModeReady_(false),
isUsingCallbacks_(false),
isAcquiring_(false),
triggerTimeout_(10),
microsecResSupported_(false),
pollingThd_(0),
notificationThd_(0),
outputTriggerFirstMissing_(0),
exposure_(10),
prmTemp_(0),
prmTempSetpoint_(0),
prmGainIndex_(0),
prmGainMultFactor_(0),
prmBinningSer_(0),
prmBinningPar_(0),
binningRestricted_(false),
redScale_(1.0),
greenScale_(1.0),
blueScale_(1.0),
#ifdef PVCAM_3_0_12_SUPPORTED
metaFrameStruct_(0),
#endif
metaBlackFilledBuf_(0),
metaBlackFilledBufSz_(0),
singleFrameBufRaw_(0),
singleFrameBufRawSz_(0),
rgbImgBuf_(0),
#ifdef PVCAM_SMART_STREAMING_SUPPORTED
smartStreamEntries_(4),
ssWasOn_(false),
#endif
#ifdef PVCAM_FRAME_INFO_SUPPORTED
pFrameInfo_(0),
#endif
#ifdef PVCAM_SMART_STREAMING_SUPPORTED
prmSmartStreamingValues_(0),
prmSmartStreamingEnabled_(0),
#endif
prmTriggerMode_(0),
prmExpResIndex_(0),
prmExpRes_(0),
prmExposeOutMode_(0),
prmClearCycles_(0),
prmReadoutPort_(0),
prmColorMode_(0),
prmFrameBufSize_(0),
prmMetadataEnabled_(0),
prmCentroidsEnabled_(0),
prmCentroidsRadius_(0),
prmCentroidsCount_(0),
prmFanSpeedSetpoint_(0)
{
   InitializeDefaultErrorMessages();

   // add custom messages
   SetErrorText(ERR_CAMERA_NOT_FOUND, "No Camera Found. Is it connected and switched on?");
   SetErrorText(ERR_BUSY_ACQUIRING, "Acquisition already in progress.");
   SetErrorText(ERR_ROI_SIZE_NOT_SUPPORTED, "Selected ROI is not supported by the camera");
   SetErrorText(ERR_BUFFER_TOO_LARGE, "Buffer too large");
   SetErrorText(ERR_ROI_DEFINITION_INVALID, "Selected ROI is invalid for current camera configuration");
   SetErrorText(ERR_BUFFER_PROCESSING_FAILED, "Failed to process the image buffer");
   SetErrorText(ERR_BINNING_INVALID, "Binning value is not valid for current configuration");

   pollingThd_ = new PollingThread(this);             // Pointer to the sequencing thread

   deviceLabel_[0] = '\0';

   // The notification thread will have slightly smaller queue than the circular buffer.
   // This is to reduce the risk of frames being overwritten by PVCAM when the circular
   // buffer starts to be full. Whith smaller queue we will simply start throwing old
   // frames away earlier because those old frames could soon get overwritten.
   notificationThd_ = new NotificationThread(this);
   notificationThd_->activate();
}


Universal::~Universal()
{   
   if (--refCount_ <= 0)
   {
      refCount_ = 0; // having the refCount as uint caused underflow and incorrect behavior in Shutdown()
      // release resources
      if (initialized_)
         Shutdown();
   }
   if (!pollingThd_->getStop()) {
      pollingThd_->setStop(true);
      pollingThd_->wait();
   }
   delete pollingThd_;
   delete notificationThd_;

#ifdef PVCAM_3_0_12_SUPPORTED
   if (metaFrameStruct_)
       pl_md_release_frame_struct(metaFrameStruct_);
#endif

   // Delete all buffers
   delete[] metaBlackFilledBuf_;
   delete[] singleFrameBufRaw_;
   delete rgbImgBuf_;
   // Delete all PVCAM parameter wrappers
   delete prmTemp_;
   delete prmTempSetpoint_;
   delete prmGainIndex_;
   delete prmGainMultFactor_;
   delete prmBinningSer_;
   delete prmBinningPar_;
   delete prmExpResIndex_;
   delete prmExpRes_;
   delete prmTriggerMode_;
   delete prmExposeOutMode_;
   delete prmClearCycles_;
   delete prmReadoutPort_;
   delete prmColorMode_;
   delete prmFrameBufSize_;
   delete prmMetadataEnabled_;
   delete prmCentroidsEnabled_;
   delete prmCentroidsRadius_;
   delete prmCentroidsCount_;
   delete prmFanSpeedSetpoint_;
#ifdef PVCAM_SMART_STREAMING_SUPPORTED
   delete prmSmartStreamingEnabled_;
   delete prmSmartStreamingValues_;
#endif

   // Delete universal parameters
   for ( unsigned i = 0; i < universalParams_.size(); i++ )
       delete universalParams_[i];
   universalParams_.clear();
}


///////////////////////////////////////////////////////////////////////////////
// Function name   : &Universal::Initialize
// Description     : Initializes the camera
//                   Sets up the (single) image buffer 
// Return type     : bool 
int Universal::Initialize()
{
   START_METHOD(">>> Universal::Initialize");

   int nRet;               // MM error code
   CPropertyAction *pAct;

   // Property: Description of the adapter
   nRet = CreateProperty(MM::g_Keyword_Description, "PVCAM API device adapter", MM::String, true);
   assert(nRet == DEVICE_OK);

   if (!PVCAM_initialized_)
   {
      LogMessage("PVCAM: Initializing PVCAM");
      if (!pl_pvcam_init())
      {
         LogCamError(__LINE__, "First PVCAM init failed");
         // Try once more:
         pl_pvcam_uninit();
         if (!pl_pvcam_init())
            return LogCamError(__LINE__, "Second PVCAM init failed");
      }
      PVCAM_initialized_ = true;
   }
   else
   {
      LogMessage("PVCAM: PVCAM already initialized");
   }

   // gather information about the camera
   // ------------------------------------------

   // Get PVCAM version
   uns16 version;
   if (!pl_pvcam_get_ver(&version))
      return LogCamError(__LINE__);

   int16 numCameras;
   if (!pl_cam_get_total(&numCameras))
      return LogCamError(__LINE__);

   uns16 major = (version >> 8) & 0xFF;
   uns16 minor = (version >> 4) & 0xF;
   uns16 trivial = version & 0xF;

   stringstream ver;
   ver << major << "." << minor << "." << trivial;
   nRet = CreateProperty("PVCAM Version", ver.str().c_str(), MM::String, true);
   ver << ". Number of cameras detected: " << numCameras;
   LogMessage("PVCAM: PVCAM version: " + ver.str());
   assert(nRet == DEVICE_OK);

   // find camera
   if (!pl_cam_get_name(cameraId_, camName_))
   {
      LogCamError(__LINE__, "pl_cam_get_name");
      return ERR_CAMERA_NOT_FOUND;
   }

   LogMessage("PVCAM: Opening camera...");

   // Get a handle to the camera
   if (!pl_cam_open(camName_, &hPVCAM_, OPEN_EXCLUSIVE ))
      return LogCamError(__LINE__, "pl_cam_open" );

   refCount_++;

   /// --- STATIC PROPERTIES
   /// are properties that are not changed during session. These are read-out only once.
   LogMessage( "PVCAM: Initializing Static Camera Properties" );
   nRet = initializeStaticCameraParams();
   if ( nRet != DEVICE_OK )
       return nRet;

   /// --- BUILD THE SPEED TABLE
   LogMessage( "PVCAM: Building Speed Table" );
   nRet = buildSpdTable();
   if ( nRet != DEVICE_OK )
       return nRet;

   /// --- DYNAMIC PROPERTIES
   /// are properties that may be updated by a camera or changed by the user during session.
   /// These are read upon opening the camera and then updated on various events. These usually
   /// needs a handler that is called by MM when the GUI asks for the property value.
   LogMessage("PVCAM: Initializing Dynamic Camera Properties");

   /// COLOR MODE
   bool isColorCcd = false;
   prmColorMode_ = new PvEnumParam( g_Keyword_ColorMode, PARAM_COLOR_MODE, this, true );
   if ( prmColorMode_->IsAvailable() )
   {
       if ( prmColorMode_->Current() != COLOR_NONE )
          isColorCcd = true;

       if (isColorCcd)
       {
           // Initialize the default color-related configuration
           acqCfgNew_.DebayerAlgMask = CFA_RGGB;
           acqCfgNew_.DebayerAlgInterpolation = ALG_REPLICATION;
           acqCfgNew_.DebayerAlgMaskAuto = true;

           debayer_.SetOrderIndex(acqCfgNew_.DebayerAlgMask);
           debayer_.SetAlgorithmIndex(acqCfgNew_.DebayerAlgInterpolation);
           // The color properties may be a bit confusing, explanation:
           // - "ColorMode" property is used to switch color processing ON/OFF
           // - "Sensor CFA" property displays read-only value reported by PVCAM (PARAM_COLOR_MODE)
           // - "Algorithm CFA" property allows user to select the mask for the Debayering Algorithm
           // - "Algorithm CFA Auto" uses the "Sensor CFA" and ROI coordinates to select proper "Algorithm CFA"
           pAct = new CPropertyAction (this, &Universal::OnSensorCfaMask);
           CreateProperty(g_Keyword_SensorCFA, camCurrentSpeed_.colorMaskStr.c_str(), MM::String, true, pAct);

           // White Balance Scale properties
           pAct = new CPropertyAction (this, &Universal::OnRedScale);
           CreateProperty(g_Keyword_RedScale, "1.0", MM::Float, !isColorCcd, pAct);
           nRet = SetPropertyLimits(g_Keyword_RedScale, 0, 20);
           pAct = new CPropertyAction (this, &Universal::OnGreenScale);
           CreateProperty(g_Keyword_GreenScale, "1.0", MM::Float, !isColorCcd, pAct);
           nRet = SetPropertyLimits(g_Keyword_GreenScale, 0, 20);
           pAct = new CPropertyAction (this, &Universal::OnBlueScale);
           CreateProperty(g_Keyword_BlueScale, "1.0", MM::Float, !isColorCcd, pAct);
           nRet = SetPropertyLimits(g_Keyword_BlueScale, 0, 20);
           // Property for selecting the Debayering alogrithm CFA mask
           pAct = new CPropertyAction (this, &Universal::OnAlgorithmCfaMask);
           CreateProperty(g_Keyword_AlgorithmCFA, g_Keyword_RGGB, MM::String, !isColorCcd, pAct);
           AddAllowedValue(g_Keyword_AlgorithmCFA, g_Keyword_RGGB);
           AddAllowedValue(g_Keyword_AlgorithmCFA, g_Keyword_BGGR);
           AddAllowedValue(g_Keyword_AlgorithmCFA, g_Keyword_GRBG);
           AddAllowedValue(g_Keyword_AlgorithmCFA, g_Keyword_GBRG);

           // Auto CFA mask selection (ON/OFF)
           pAct = new CPropertyAction (this, &Universal::OnAlgorithmCfaMaskAuto);
           CreateProperty(g_Keyword_AlgorithmCFAAuto, acqCfgNew_.DebayerAlgMaskAuto ? g_Keyword_ON : g_Keyword_OFF, MM::String, false, pAct);
           AddAllowedValue(g_Keyword_AlgorithmCFAAuto, g_Keyword_OFF);
           AddAllowedValue(g_Keyword_AlgorithmCFAAuto, g_Keyword_ON);

           pAct = new CPropertyAction (this, &Universal::OnInterpolationAlgorithm);
           CreateProperty(g_Keyword_AlgorithmInterp, g_Keyword_Replication, MM::String, !isColorCcd, pAct);
           AddAllowedValue(g_Keyword_AlgorithmInterp, g_Keyword_Replication);
           AddAllowedValue(g_Keyword_AlgorithmInterp, g_Keyword_Bilinear);
           AddAllowedValue(g_Keyword_AlgorithmInterp, g_Keyword_SmoothHue);
           AddAllowedValue(g_Keyword_AlgorithmInterp, g_Keyword_AdaptiveSmoothHue);
       }
   }
   // the camera can interpret pixels as color data with the Bayer pattern
   pAct = new CPropertyAction (this, &Universal::OnColorMode);
   // If not color CCD then make the property OFF and read-only (grayed out)
   CreateProperty(g_Keyword_RGB32, g_Keyword_OFF, MM::String, !isColorCcd, pAct);
   AddAllowedValue(g_Keyword_RGB32, g_Keyword_ON);
   AddAllowedValue(g_Keyword_RGB32, g_Keyword_OFF);

#ifdef PVCAM_3_0_12_SUPPORTED
   /// PARAM_FRAME_BUFFER_SIZE, no UI property but we use it later in the code
   prmFrameBufSize_ = new PvParam<ulong64>( "PARAM_FRAME_BUFFER_SIZE", PARAM_FRAME_BUFFER_SIZE, this, true );

   /// EMBEDDED FRAME METADATA FEATURE
   acqCfgNew_.FrameMetadataEnabled = false; // Disabled by default
   prmMetadataEnabled_ =  new PvParam<rs_bool>("PARAM_METADATA_ENABLED", PARAM_METADATA_ENABLED, this, true);
   if ( prmMetadataEnabled_->IsAvailable() )
   {
      nRet = prmMetadataEnabled_->SetAndApply(acqCfgNew_.FrameMetadataEnabled ? TRUE : FALSE);
      if (nRet != DEVICE_OK)
          return nRet;
      // CentroidsEnabled UI property
      pAct = new CPropertyAction (this, &Universal::OnMetadataEnabled);
      CreateProperty(g_Keyword_MetadataEnabled, 
          acqCfgNew_.FrameMetadataEnabled ? g_Keyword_Yes : g_Keyword_No, MM::String, false, pAct);
      AddAllowedValue(g_Keyword_MetadataEnabled, g_Keyword_No);
      AddAllowedValue(g_Keyword_MetadataEnabled, g_Keyword_Yes);
   }

   /// CENTROIDS FEATURE
   acqCfgNew_.CentroidsEnabled = false; // Disabled by default
   prmCentroidsEnabled_ = new PvParam<rs_bool>("PARAM_CENTROIDS_ENABLED", PARAM_CENTROIDS_ENABLED, this, true);
   if ( prmCentroidsEnabled_->IsAvailable() )
   {
      nRet = prmCentroidsEnabled_->SetAndApply(acqCfgNew_.CentroidsEnabled ? TRUE : FALSE);
      if (nRet != DEVICE_OK)
          return nRet;
      // CentroidsEnabled UI property
      pAct = new CPropertyAction (this, &Universal::OnCentroidsEnabled);
      CreateProperty(g_Keyword_CentroidsEnabled,
          acqCfgNew_.CentroidsEnabled ? g_Keyword_Yes : g_Keyword_No, MM::String, false, pAct);
      AddAllowedValue(g_Keyword_CentroidsEnabled, g_Keyword_No);
      AddAllowedValue(g_Keyword_CentroidsEnabled, g_Keyword_Yes);
   }

   prmCentroidsRadius_ = new PvParam<uns16>("PARAM_CENTROIDS_RADIUS", PARAM_CENTROIDS_RADIUS, this, true);
   if ( prmCentroidsRadius_->IsAvailable() )
   {
      // Just read the current value, we don't have any specific setting to use
      acqCfgNew_.CentroidsRadius = prmCentroidsRadius_->Current();
      // CentroidsRadius UI property
      pAct = new CPropertyAction (this, &Universal::OnCentroidsRadius);
      CreateProperty(g_Keyword_CentroidsRadius, 
          CDeviceUtils::ConvertToString(prmCentroidsRadius_->Current()),
          MM::Integer, false, pAct);
      SetPropertyLimits(g_Keyword_CentroidsRadius,
          prmCentroidsRadius_->Min(), prmCentroidsRadius_->Max());
   }
   prmCentroidsCount_ = new PvParam<uns16>("PARAM_CENTROIDS_COUNT", PARAM_CENTROIDS_COUNT, this, true);
   if (prmCentroidsCount_->IsAvailable())
   {
      // Just read the current value, we don't have any specific setting to use
      acqCfgNew_.CentroidsCount = prmCentroidsCount_->Current();
      // CentroidsCount UI property
      pAct = new CPropertyAction (this, &Universal::OnCentroidsCount);
      CreateProperty(g_Keyword_CentroidsCount, 
          CDeviceUtils::ConvertToString(prmCentroidsCount_->Current()),
          MM::Integer, false, pAct);
      SetPropertyLimits(g_Keyword_CentroidsCount,
          prmCentroidsCount_->Min(), prmCentroidsCount_->Max());
   }

   /// FAN SPEED SETPOINT
   prmFanSpeedSetpoint_ = new PvEnumParam( "PARAM_FAN_SPEED_SETPOINT", PARAM_FAN_SPEED_SETPOINT, this, true );
   if ( prmFanSpeedSetpoint_->IsAvailable() )
   {
      acqCfgNew_.FanSpeedSetpoint = prmFanSpeedSetpoint_->Current();
      pAct = new CPropertyAction (this, &Universal::OnFanSpeedSetpoint);
      CreateProperty(g_Keyword_FanSpeedSetpoint, prmFanSpeedSetpoint_->ToString().c_str(), MM::String, false, pAct);
      SetAllowedValues(g_Keyword_FanSpeedSetpoint, prmFanSpeedSetpoint_->GetEnumStrings());
   }
#endif

   /// TRIGGER MODE (EXPOSURE MODE)
   prmTriggerMode_ = new PvEnumParam( "PARAM_EXPOSURE_MODE", PARAM_EXPOSURE_MODE, this, true );
   if ( prmTriggerMode_->IsAvailable() )
   {
      pAct = new CPropertyAction (this, &Universal::OnTriggerMode);
      // The PARAM_EXPOSURE_MODE is buggy, the ATTR_CURRENT does not work correctly
      // in 3.0.5.2 (last checked on Sept 2015). So as current value we use the first one.
      const char* currentMode = prmTriggerMode_->GetEnumStrings()[0].c_str();
      // The ATTR_CURRENT returns 0 which might not be among the allowed values so we need
      // to make sure that the cache contains correct value. NOTE: The parameter is read only
      // but we use the class as a cache to store the selected value (we use Set, not Apply)
      prmTriggerMode_->Set(currentMode);
      CreateProperty(g_Keyword_TriggerMode, currentMode, MM::String, false, pAct);
      SetAllowedValues( g_Keyword_TriggerMode, prmTriggerMode_->GetEnumStrings());

      pAct = new CPropertyAction (this, &Universal::OnTriggerTimeOut);
      CreateProperty(g_Keyword_TriggerTimeout, "2", MM::Integer, false, pAct);
   }

   /// EXPOSE OUT MODE
#ifdef PVCAM_PARAM_EXPOSE_OUT_DEFINED
   prmExposeOutMode_ = new PvEnumParam( "PARAM_EXPOSE_OUT_MODE", PARAM_EXPOSE_OUT_MODE, this, true );
   if ( prmExposeOutMode_->IsAvailable() )
   {
      pAct = new CPropertyAction (this, &Universal::OnExposeOutMode);
      const char* currentMode = prmExposeOutMode_->GetEnumStrings()[0].c_str();
      CreateProperty(g_Keyword_ExposeOutMode, currentMode, MM::String, false, pAct);
      SetAllowedValues( g_Keyword_ExposeOutMode, prmExposeOutMode_->GetEnumStrings() );
   }
#else
   // If the flag is not defined the prmExposeOutMode_ stays NULL, the property is not created - event handlers are not called,
   // the code that still uses the param should first check the variable for NULL, then try to call it. We need to flag this
   // part of the code because the PARAM_EXPOSE_OUT_MODE is defined for WIN only and compilation on other platforms would fail.
#endif

   /// CLEAR CYCLES
   // The Clear Cycles needs a bit different handling, the PVCAM allows range of 0-65535 but we want to limit it to 
   // 0-16 in the UI because users can easily hang the camera just by clicking on the property scrollbar - which
   // increases the value by a huge amount.
   prmClearCycles_ = new PvParam<uns16>("PARAM_CLEAR_CYCLES", PARAM_CLEAR_CYCLES, this, true);
   if (prmClearCycles_->IsAvailable())
   {
      pAct = new CPropertyAction (this, &Universal::OnClearCycles);
      nRet = CreateProperty(g_Keyword_ClearCycles, "1", MM::Integer, false, pAct);
      assert(nRet == DEVICE_OK);
      nRet = SetPropertyLimits(g_Keyword_ClearCycles, 0, 16);
      if (nRet != DEVICE_OK)
         return nRet;
   }

   /// CAMERA TEMPERATURE
   /// The actual value is read out from the camera in OnTemperature(). Please note
   /// we cannot read the temperature when continuous sequence is running.
   prmTemp_ = new PvParam<int16>( "PARAM_TEMP", PARAM_TEMP, this, true );
   if ( prmTemp_->IsAvailable() )
   {
      pAct = new CPropertyAction (this, &Universal::OnTemperature);
      nRet = CreateProperty(MM::g_Keyword_CCDTemperature,
         CDeviceUtils::ConvertToString((double)prmTemp_->Current()/100.0), MM::Float, true, pAct);
      assert(nRet == DEVICE_OK);
   }

   /// CAMERA TEMPERATURE SET POINT
   /// The desired value of the CCD chip
   prmTempSetpoint_ = new PvParam<int16>( "PARAM_TEMP_SETPOINT", PARAM_TEMP_SETPOINT, this, true );
   if ( prmTempSetpoint_->IsAvailable() )
   {
      pAct = new CPropertyAction (this, &Universal::OnTemperatureSetPoint);
      nRet = CreateProperty(MM::g_Keyword_CCDTemperatureSetPoint,
          CDeviceUtils::ConvertToString((double)prmTempSetpoint_->Current()/100.0), MM::Float, false, pAct);
      SetPropertyLimits(MM::g_Keyword_CCDTemperatureSetPoint, prmTempSetpoint_->Min()/100.0,prmTempSetpoint_->Max()/100.0);
   }

   /// EXPOSURE TIME
   pAct = new CPropertyAction (this, &Universal::OnExposure);
   nRet = CreateProperty(MM::g_Keyword_Exposure, "10.0", MM::Float, false, pAct);
   assert(nRet == DEVICE_OK);

   
#ifdef PVCAM_SMART_STREAMING_SUPPORTED
   /// SMART STREAMING
   /// SMART streaming is enabled/disabled in OnSmartStreamingEnable
   /// SMART streaming values are updated in OnSmartStreamingValues
   /// SMART streaming vlaues are sent to camera in SendSmartStreamingToCamera


   prmSmartStreamingEnabled_ = new PvParam<rs_bool>( "PARAM_SMART_STREAM_MODE_ENABLED", PARAM_SMART_STREAM_MODE_ENABLED, this, true );
   prmSmartStreamingValues_ = new PvParam<smart_stream_type>( "PARAM_SMART_STREAM_EXP_PARAMS", PARAM_SMART_STREAM_EXP_PARAMS, this, true );
   if (prmSmartStreamingEnabled_->IsAvailable() && prmSmartStreamingValues_->IsAvailable())
   {
      LogMessage("PVCAM: This camera supports SMART streaming");
      pAct = new CPropertyAction (this, &Universal::OnSmartStreamingEnable);
      nRet = CreateProperty(g_Keyword_SmartStreamingEnable, g_Keyword_No, MM::String, false, pAct);
      assert(nRet == DEVICE_OK);
      AddAllowedValue(g_Keyword_SmartStreamingEnable, g_Keyword_No);
      AddAllowedValue(g_Keyword_SmartStreamingEnable, g_Keyword_Yes);
      
      // disable SMART streaming on launch as it is not reset to OFF by PVCAM and camera 
      // would remember the previous settings unless it was power-cycled
      if (DEVICE_OK == prmSmartStreamingEnabled_->Set(FALSE))
      {
          if (DEVICE_OK == prmSmartStreamingEnabled_->Apply())
          {
              LogMessage("PVCAM: SMART streaming disabled on launch");
          }
      
      }
      //not handling else for the first if because prmSmartStreamingEnabled->Set always returns DEVICE_OK, might be added later

      //number of smartStreamEntries_ initialized has been initiailized to 4 in the constructor, so now 
      //set initial values to SMART streaming parameters to populate the UI on launch
      smartStreamValuesDouble_[0] = 10000;
      smartStreamValuesDouble_[1] = 20000;
      smartStreamValuesDouble_[2] = 30000;
      smartStreamValuesDouble_[3] = 40000;
      
      pAct = new CPropertyAction (this, &Universal::OnSmartStreamingValues);
      nRet = CreateProperty(g_Keyword_SmartStreamingValues, "10000;20000;30000;40000", MM::String, false, pAct);
      assert(nRet == DEVICE_OK);
   }
#endif

   /// BINNING
   binningRestricted_ = false;
   binningLabels_.clear();
   binningValuesX_.clear();
   binningValuesY_.clear();
#ifdef PVCAM_3_0_12_SUPPORTED
   prmBinningSer_ = new PvEnumParam("PARAM_BINNING_SER", PARAM_BINNING_SER, this, true);
   prmBinningPar_ = new PvEnumParam("PARAM_BINNING_PAR", PARAM_BINNING_PAR, this, true);
   if (prmBinningSer_->IsAvailable() && prmBinningPar_->IsAvailable())
   {
      binningRestricted_ = true;
   }
#endif
   if (binningRestricted_)
   {
      // If the camera reports supported binning modes the Binning property
      // is treated as string and filled with all options, e.g. "1x1","1x4","4x2", etc.
      // The same strings are also displayed in the MM GUI dropdown where we previously displayed
      // only integer numbers, e.g. "1", "2", "4", "8"
      binningValuesX_ = prmBinningSer_->GetEnumValues();
      binningValuesY_ = prmBinningPar_->GetEnumValues();
      // Both params should report the same strings, so we can use either one
      binningLabels_  = prmBinningSer_->GetEnumStrings();

      pAct = new CPropertyAction (this, &Universal::OnBinning);
      nRet = CreateProperty(MM::g_Keyword_Binning, binningLabels_[0].c_str(), MM::String, false, pAct);
      assert(nRet == DEVICE_OK);
      nRet = SetAllowedValues(MM::g_Keyword_Binning, binningLabels_);
      assert(nRet == DEVICE_OK);
   }
   else
   {
      // If the camera does report binnings we will use the old approach: basic symmetric binnigs
      // and two extra properties for custom assymetric bins
      const int symBins[] = {1, 2, 4, 8}; // Add more if needed
      const size_t symBinsCount = sizeof(symBins) / sizeof(symBins[0]);
      binningValuesX_.assign(symBins, symBins + symBinsCount);
      binningValuesY_ = binningValuesX_;

      for (int i = 0; i < symBinsCount; ++i)
      {
         const std::string binStr = CDeviceUtils::ConvertToString(symBins[i]);
         binningLabels_.push_back(binStr + "x" + binStr); // 1x1, 2x2, 4x4, etc
      }

      /// SYMMETRIC BINNING used to set the bin from MM GUI. Instead of asymmetric binning the
      /// value is restricted to specific values.
      pAct = new CPropertyAction (this, &Universal::OnBinning);
      nRet = CreateProperty(MM::g_Keyword_Binning, binningLabels_[0].c_str(), MM::String, false, pAct);
      assert(nRet == DEVICE_OK);
      nRet = SetAllowedValues(MM::g_Keyword_Binning, binningLabels_);
      assert(nRet == DEVICE_OK);

      /// ASYMMETRIC BINNINGS. We don't set any allowed values here, this is an
      /// advanced feature so users should know what they do. The value can be set only from
      /// Device/Property browser. Changing the asymmetric binning does not change the symmetric
      /// bin value, but changing the symmetric bin updates bots asymmetric values accordingly.
      pAct = new CPropertyAction (this, &Universal::OnBinningX);
      nRet = CreateProperty(g_Keyword_BinningX, "1", MM::Integer, false, pAct);
      assert(nRet == DEVICE_OK);
      pAct = new CPropertyAction (this, &Universal::OnBinningY);
      nRet = CreateProperty(g_Keyword_BinningY, "1", MM::Integer, false, pAct);
      assert(nRet == DEVICE_OK);
   }

   /// PIXEL TYPE (BIT DEPTH).  
   /// The value changes with selected port and speed
   pAct = new CPropertyAction (this, &Universal::OnPixelType);
   nRet = CreateProperty(MM::g_Keyword_PixelType, "", MM::String, true, pAct);
   if (nRet != DEVICE_OK)
      return nRet;

   /// Gain and speed depends on readout port. At first we just prepare these properties and then apply
   /// a readout port value which will update the allowed values of gain and speed properties accordingly.
   /// Changing the port resets the speed.
   /// Changing the speed causes change in Gain range, Pixel time and current Bit depth

   /// GAIN
   /// Note that this can change depending on output port, and readout rate.
   prmGainIndex_ = new PvParam<int16>( "PARAM_GAIN_INDEX", PARAM_GAIN_INDEX, this, true );
   if (prmGainIndex_->IsAvailable())
   {
      pAct = new CPropertyAction (this, &Universal::OnGain);
      const int16 gainCur = prmGainIndex_->Current();
      std::string gainName = "";
      if (camCurrentSpeed_.gainNameMapReverse.find(gainCur) != camCurrentSpeed_.gainNameMapReverse.end())
          gainName = camCurrentSpeed_.gainNameMapReverse.at(prmGainIndex_->Current());
      else
          // This must be a developer error, but let's just continue, the correct current 
          // property value is reported in the OnGain() handler anyway.
          LogCamError( __LINE__, "Current gain name not applicable!" );
      nRet = CreateProperty(MM::g_Keyword_Gain, gainName.c_str(), MM::String, false, pAct);
      if (nRet != DEVICE_OK)
         return nRet;
   }

   /// SPEED
   /// Note that this can change depending on output port, and readout rate.
   pAct = new CPropertyAction (this, &Universal::OnReadoutRate);
   nRet = CreateProperty(g_ReadoutRate, camCurrentSpeed_.spdString.c_str(), MM::String, false, pAct);
   if (nRet != DEVICE_OK)
      return nRet;

   /// READOUT PORT
   prmReadoutPort_ = new PvEnumParam("PARAM_READOUT_PORT", PARAM_READOUT_PORT, this, true );
   if ( prmReadoutPort_->IsAvailable() )
   {
      pAct = new CPropertyAction (this, &Universal::OnReadoutPort);
      vector<string> portStrings = prmReadoutPort_->GetEnumStrings();
      // If there is more than 1 port we make it selectable, otherwise just display readonly value
      if ( portStrings.size() > 1 )
      {
         nRet = CreateProperty(g_ReadoutPort, prmReadoutPort_->ToString().c_str(), MM::String, false, pAct);
         nRet = SetAllowedValues(g_ReadoutPort, prmReadoutPort_->GetEnumStrings());
      }
      else
         nRet = CreateProperty(g_ReadoutPort, prmReadoutPort_->ToString().c_str(), MM::String, true, pAct);
   }

   /// EXPOSURE RESOLUTION
   // The PARAM_EXP_RES_INDEX is used to get and set the current exposure resolution (usec, msec, sec, ...)
   // The PARAM_EXP_RES is only used to enumerate the supported exposure resolutions and their string names
   microsecResSupported_ = false;
   prmExpResIndex_ = new PvParam<uns16>( "PARAM_EXP_RES_INDEX", PARAM_EXP_RES_INDEX, this, true );
   prmExpRes_ = new PvEnumParam( "PARAM_EXP_RES", PARAM_EXP_RES, this, true );
   if ( prmExpResIndex_->IsAvailable() )
   {
       if ( prmExpRes_->IsAvailable() )
       {
           std::vector<int32> enumVals = prmExpRes_->GetEnumValues();
           for ( unsigned i = 0; i < enumVals.size(); ++i )
           {
               if ( enumVals[i] == EXP_RES_ONE_MICROSEC )
               {
                   microsecResSupported_ = true;
                   break;
               }
           }
       }
   }

   /// MULTIPLIER GAIN
   // The HQ2 has 'visual gain', which shows up as EM Gain.  
   // Detect whether this is an interline chip and do not expose EM Gain if it is.
   prmGainMultFactor_ = new PvParam<uns16>("PARAM_GAIN_MULT_FACTOR", PARAM_GAIN_MULT_FACTOR, this, true);
   if (prmGainMultFactor_->IsAvailable())
   {
      // Some older cameras errorneously report PARAM_GAIN_MULT_FACTOR but are not EM.
      if ((camChipName_.find("ICX-285") != std::string::npos) || (camChipName_.find("ICX285") != std::string::npos))
      {
          LogMessage("PVCAM: This Camera reports EM Gain available but it's ICX285, so no EM.");
      }
      else
      {
         LogMessage("PVCAM: This Camera has Em Gain");
         pAct = new CPropertyAction (this, &Universal::OnMultiplierGain);
         nRet = CreateProperty(g_Keyword_MultiplierGain, "1", MM::Integer, false, pAct);
         assert(nRet == DEVICE_OK);
         // The ATTR_MIN is 0 but according to PVCAM manual the range is from 1 to ATTR_MAX
         nRet = SetPropertyLimits(g_Keyword_MultiplierGain, 1, prmGainMultFactor_->Max());
         if (nRet != DEVICE_OK)
            return nRet;
      }
   }
   else
      LogMessage("PVCAM: This Camera does not have EM Gain");


   if (cameraModel_ == PvCameraModel_OptiMos_M1)
   {
       uns32 clearMode = CLEAR_PRE_SEQUENCE;
       pl_set_param(hPVCAM_, PARAM_CLEAR_MODE, (void *)&clearMode);
   }

   // create actual interval property, this param is set in PushImage2()
   CreateProperty(MM::g_Keyword_ActualInterval_ms, "0.0", MM::Float, false);

   /// FRAME TRANSFER MODE
   /// Enable the Frame Transfer mode if available, do not return errors if we fail
   PvParam<rs_bool> prmFrameCapable( "PARAM_FRAME_CAPABLE", PARAM_FRAME_CAPABLE, this, true );
   if (prmFrameCapable.IsAvailable() && prmFrameCapable.Current() == TRUE)
   {
      LogMessage( "PVCAM: Frame Transfer mode is available" );
      uns32 pmode = PMODE_FT;
      if ( pl_set_param( hPVCAM_, PARAM_PMODE, &pmode ) != PV_OK )
         LogCamError( __LINE__, "pl_set_param PARAM_PMODE PMODE_FT" );
   }
   else LogMessage( "PVCAM: Frame Transfer mode not available" );

   /// FRAME RECOVERY
   /// Enable/Disable the feature that attempts to recover from lost callbacks
#ifdef PVCAM_FRAME_INFO_SUPPORTED
   pAct = new CPropertyAction (this, &Universal::OnCircBufferFrameRecovery);
   nRet = CreateProperty(g_Keyword_CircBufFrameRecovery, g_Keyword_ON, MM::String, false, pAct);
   assert(nRet == DEVICE_OK);
   AddAllowedValue(g_Keyword_CircBufFrameRecovery, g_Keyword_ON);
   AddAllowedValue(g_Keyword_CircBufFrameRecovery, g_Keyword_OFF);
#endif

   /// properties that allow to enable/disable/set various post processing features
   /// supported by Photometrics cameras. The parameter properties are read out from
   /// the camera and created automatically.
   LogMessage( "PVCAM: Initializing Post Processing features..." );
   initializePostProcessing();
   LogMessage( "PVCAM: Post Processing initialized" );

   // setup imaging
   if (!pl_exp_init_seq())
      return LogCamError(__LINE__, "pl_exp_init_seq");

   // The _outputTriggerFirstMissing does not seem to be used anywhere, we may
   // want to remove it later.
   pAct = new CPropertyAction (this, &Universal::OnOutputTriggerFirstMissing);
   nRet = CreateProperty(g_Keyword_OutputTriggerFirstMissing, "0", MM::Integer, false, pAct);
   AddAllowedValue(g_Keyword_OutputTriggerFirstMissing, "0");
   AddAllowedValue(g_Keyword_OutputTriggerFirstMissing, "1");

   // Circular buffer auto/manual switch
   pAct = new CPropertyAction(this, &Universal::OnCircBufferSizeAuto);
   nRet = CreateProperty( g_Keyword_CircBufSizeAuto,
      circBufSizeAuto_ ? g_Keyword_ON : g_Keyword_OFF, MM::String, false, pAct);
   AddAllowedValue(g_Keyword_CircBufSizeAuto, g_Keyword_ON);
   AddAllowedValue(g_Keyword_CircBufSizeAuto, g_Keyword_OFF);

   // Circular buffer size. This allows the user to set how many frames we want to allocate the PVCAM
   // PVCAM circular buffer for. The default value is fine for most cases, however chaning this value
   // may help in some cases (e.g. lowering it down to 3 helped to resolve ICX-674 image tearing issues)
   pAct = new CPropertyAction(this, &Universal::OnCircBufferFrameCount);
   nRet = CreateProperty( g_Keyword_CircBufFrameCnt,
      CDeviceUtils::ConvertToString(CIRC_BUF_FRAME_CNT_DEF), MM::Integer, circBufSizeAuto_, pAct);
   // If we are in auto mode the property has no limits because the value is read only and adjusted
   // automatically by internal algorithm or PVCAM. In manual mode we just set the initial range that
   // is later dynamically adjusted by actual frame size.
   // Note: passing 0, 0 will disable the limit checking but for some reason returns an error so
   // we don't check the error code for SetPropertyLimits() here. See also: updateCircBufRange()
   if (circBufSizeAuto_)
      SetPropertyLimits( g_Keyword_CircBufFrameCnt, 0, 0);
   else
      SetPropertyLimits( g_Keyword_CircBufFrameCnt, CIRC_BUF_FRAME_CNT_MIN, CIRC_BUF_FRAME_CNT_MAX );


   initializeUniversalParams();

   // CALLBACKS
   // Check if we can use PVCAM callbacks. This is recommended way to get notified when the frame
   // readout is finished. Otherwise we will fall back to old polling method.
   isUsingCallbacks_ = false;
#ifdef PVCAM_CALLBACKS_SUPPORTED
   if ( pl_cam_register_callback_ex3( hPVCAM_, PL_CALLBACK_EOF, PvcamCallbackEofEx3, this ) == PV_OK )
   {
      pAct = new CPropertyAction(this, &Universal::OnAcquisitionMethod);
      nRet = CreateProperty(g_Keyword_AcqMethod, g_Keyword_AcqMethod_Polling, MM::String, false, pAct );
      AddAllowedValue(g_Keyword_AcqMethod, g_Keyword_AcqMethod_Polling);
      AddAllowedValue(g_Keyword_AcqMethod, g_Keyword_AcqMethod_Callbacks);
      LogMessage( "PVCAM: Using callbacks for frame acquisition" );
      isUsingCallbacks_ = true;
   }
   else
   {
      LogMessage( "PVCAM: pl_cam_register_callback_ex3 failed! Using polling for frame acquisition" );
   }
#endif
   
   // FRAME_INFO SUPPORT
#ifdef PVCAM_FRAME_INFO_SUPPORTED
   // Initialize the FRAME_INFO structure, this will contain the frame metadata provided by PVCAM
   if ( !pl_create_frame_info_struct( &pFrameInfo_ ) )
   {
      return LogCamError(__LINE__, "Failed to initialize the FRAME_INFO structure");
   }
#endif

   // Initialize the acquisition configuration
   acqCfgNew_.Roi = PvRoi(0, 0, camSerSize_, camParSize_);

   // Make sure our configs are synchronized
   acqCfgCur_ = acqCfgNew_;

   // Force updating the port. This updates the speed choices (and allowed values for the current port),
   // gain range, current bit depth, current pix time (readout speed), color and all parameters that depend on speed
   // table settings. All the MM parameters must be already instantiated so we do at here at the end.
   portChanged();

   initialized_ = true;
   START_METHOD("<<< Universal::Initialize");
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Function name   : &Universal::Shutdown
// Description     : Deactivate the camera, reverse the initialization process
// Return type     : bool 
int Universal::Shutdown()
{
   if (initialized_)
   {
      rs_bool ret;

#ifdef PVCAM_CALLBACKS_SUPPORTED
      if ( isUsingCallbacks_ )
      {
         pl_cam_deregister_callback( hPVCAM_, PL_CALLBACK_EOF );
      }
#endif
      ret = pl_exp_uninit_seq();
      if (!ret)
         LogCamError(__LINE__, "pl_exp_uninit_seq");
      assert(ret);
      ret = pl_cam_close(hPVCAM_);
      if (!ret)
         LogCamError(__LINE__, "pl_cam_close");
      assert(ret);     
      refCount_--;      
      if (PVCAM_initialized_ && refCount_ <= 0)      
      {
         refCount_ = 0;
         if (!pl_pvcam_uninit())
            LogCamError(__LINE__, "pl_pvcam_uninit");
         PVCAM_initialized_ = false;
      }      
#ifdef PVCAM_FRAME_INFO_SUPPORTED
      if ( pFrameInfo_ )
      {
         pl_release_frame_info_struct( pFrameInfo_ );
         pFrameInfo_ = NULL;
      }
#endif
      initialized_ = false;
   }
   return DEVICE_OK;
}


bool Universal::IsCapturing()
{
   return isAcquiring_;
}

int Universal::GetBinning () const 
{
   return acqCfgCur_.Roi.BinX();
}

int Universal::SetBinning (int binSize) 
{
   ostringstream os;
   os << binSize << "x" << binSize;
   return SetProperty(MM::g_Keyword_Binning, os.str().c_str());
}

/***
* Read and create basic static camera properties that will be displayed in
* Device/Property Browser. These properties are read only and does not change
* during camera session.
*/
int Universal::initializeStaticCameraParams()
{
   START_METHOD("Universal::initializeStaticCameraProperties");
   int nRet;

   // Read the static parameres to class variables. Some of them are also used elswhere.
   // Some are not critical so we don't return error everytime

   // Camera name: "PM1394Cam00" etc.
   nRet = CreateProperty(MM::g_Keyword_Name, camName_, MM::String, true);
   assert(nRet == DEVICE_OK);

   // Camera chip name: "EX2-ICX285" etc.
   PvStringParam paramChipName("PARAM_CHIP_NAME", PARAM_CHIP_NAME, CCD_NAME_LEN, this, true);
   if (paramChipName.IsAvailable())
   {
       camChipName_ = paramChipName.Current();
       nRet = CreateProperty(g_Keyword_ChipName, camChipName_.c_str(), MM::String, true);
       assert(nRet == DEVICE_OK);
   }

   // Camera serial number: "A09J821001" etc.
   PvStringParam paramHeadSerNumAlpha("PARAM_HEAD_SER_NUM_ALPHA", PARAM_HEAD_SER_NUM_ALPHA, MAX_ALPHA_SER_NUM_LEN, this, true );
   if (paramHeadSerNumAlpha.IsAvailable())
   {
       nRet = CreateProperty(g_Keyword_SerialNumber, paramHeadSerNumAlpha.Current().c_str(), MM::String, true);
       assert(nRet == DEVICE_OK);
   }

   PvParam<uns16> paramCamFwVersion("PARAM_CAM_FW_VERSION", PARAM_CAM_FW_VERSION, this, true);
   if (paramCamFwVersion.IsAvailable())
   {
      const uns16 fwVersion = paramCamFwVersion.Current();
      char buf[7]; // MMM.mmm
      uns16 versionMinor = fwVersion & 0x00FF;
      uns16 versionMajor = (fwVersion >> 8) & 0x00FF;
      sprintf( buf, "%d.%d", versionMajor, versionMinor );
      nRet = CreateProperty(g_Keyword_FirmwareVersion, buf, MM::String, true);
      LogMessage("PVCAM: PARAM_CAM_FW_VERSION: " + std::string(buf));
   }

   // CCD Full Well capacity
   PvParam<uns32> paramFwellCapacity("PARAM_FWELL_CAPACITY", PARAM_FWELL_CAPACITY, this, true);
   if (paramFwellCapacity.IsAvailable())
   {
      const uns32 fwell = paramFwellCapacity.Current();
      nRet = CreateProperty(g_Keyword_FWellCapacity, CDeviceUtils::ConvertToString((long)fwell), MM::Integer, true);
      assert(nRet == DEVICE_OK);
   }

   // Camera CCD size
   PvParam<uns16> paramParSize("PARAM_PAR_SIZE", PARAM_PAR_SIZE, this, true);
   if (paramParSize.IsAvailable())
   {
      camParSize_ = paramParSize.Current();
      nRet = CreateProperty(g_Keyword_CCDParSize, CDeviceUtils::ConvertToString(camParSize_), MM::Integer, true);
      assert(nRet == DEVICE_OK);
   }
   if (!paramParSize.IsAvailable() || paramParSize.Current() == 0) 
   {   // This is a serious error, we cannot continue
       return LogCamError(__LINE__, "PVCAM: PARAM_PAR_SIZE is not available or incorrect!");
   }
   PvParam<uns16> paramSerSize("PARAM_SER_SIZE", PARAM_SER_SIZE, this, true);
   if (paramSerSize.IsAvailable())
   {
      camSerSize_ = paramSerSize.Current();
      nRet = CreateProperty(g_Keyword_CCDSerSize, CDeviceUtils::ConvertToString(camSerSize_), MM::Integer, true);
      assert(nRet == DEVICE_OK);
   }
   if (!paramParSize.IsAvailable() || paramParSize.Current() == 0)
   {   // This is a serious error, we cannot continue
       return LogCamError(__LINE__, "PVCAM: PARAM_SER_SIZE is not available or incorrect!");
   }

   // Frame transfer mode capability is static readonly value
   PvParam<rs_bool> prmFrameCapable( "PARAM_FRAME_CAPABLE", PARAM_FRAME_CAPABLE, this, true );
   if (prmFrameCapable.IsAvailable() && prmFrameCapable.Current() == TRUE)
      nRet = CreateProperty(g_Keyword_FrameCapable, g_Keyword_Yes, MM::String, true);
   else
      nRet = CreateProperty(g_Keyword_FrameCapable, g_Keyword_No, MM::String, true);
   assert(nRet == DEVICE_OK);

   // Try to identify the camera model, even if this is discouraged we need to
   // make some camera-specific workarounds to make our adapter more user friendly.
   if (camChipName_.find("QI_OptiMOS_M1") != std::string::npos)
      cameraModel_ = PvCameraModel_OptiMos_M1;
   if (camChipName_.find("QI_Retiga6000C") != std::string::npos)
      cameraModel_ = PvCameraModel_Retiga6000C;

   return nRet;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
// ~~~~~~~~~~~~~~~

/***
* Symmetric binning property that is changed from main uManager GUI.
* Changing the symmetric binning updates the asymmetric bin values accordingly
* but not vice versa. We cannot display the asymmetric bin in GUI so we don't
* update the symmetric bin value if the asymmetric bin changes.
*/
int Universal::OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   START_ONPROPERTY("Universal::OnBinning", eAct);

   int nRet = DEVICE_OK;

   if (eAct == MM::AfterSet)
   {
      string choice;
      pProp->Get(choice);
      int index = -1;
      for (size_t i = 0; i < binningLabels_.size(); ++i)
      {
         if (binningLabels_[i] == choice)
         {
            index = static_cast<int>(i);
            break;
         }
      }
      if (index < 0)
         return DEVICE_CAN_NOT_SET_PROPERTY;

      acqCfgNew_.Roi.SetBinning(
          static_cast<uns16>(binningValuesX_[index]),
          static_cast<uns16>(binningValuesY_[index]));
      nRet = applyAcqConfig();
      // Workaround: When binning is not accepted by the camera or simply
      // not valid for current configuration (e.g. Centorids active) the
      // applyAcqConfig() returns an error and refuse to set the binning.
      // Unfortunately for unknown reason when Binning is set via main GUI
      // the GUI does not update and keep the invalid binning value. I.e.
      // the GUI should call the OnBinning() handler immediately with
      // MM::BeforeGet and retrieve the actual value. It does not do that.
      // When Binning is set via Property Browser it works fine. So if
      // we fail to set the binning we force update the GUI here.
      if (nRet != DEVICE_OK)
          this->GetCoreCallback()->OnPropertiesChanged(this);
   }
   else if (eAct == MM::BeforeGet)
   {
      std::stringstream ss;
      ss << acqCfgCur_.Roi.BinX() << "x" << acqCfgCur_.Roi.BinY();
      pProp->Set(ss.str().c_str());
   }
   return nRet;
}

/***
* Assymetric binning can be only set in Property Browser.
* If the user sets the BinningX the symmetric binning combo box in MM GUI 
* is not updated (because there is no way to display asymmetric bin in main GUI).
* However, if the user sets the symmetric bin value, then both the asymmetric
* bin values are updated accordingly.
*/
int Universal::OnBinningX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   START_ONPROPERTY("Universal::OnBinningX", eAct);

   int nRet = DEVICE_OK;

   if (eAct == MM::AfterSet)
   {
      long binX;
      pProp->Get(binX);
      if (binX < 1)
      {
         LogMMError( 0, __LINE__, "Value of BinningX has to be positive" );
         nRet = DEVICE_INVALID_PROPERTY_VALUE;
      }
      else
      {
         acqCfgNew_.Roi.SetBinningX(static_cast<uns16>(binX));
         nRet = applyAcqConfig();
      }
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set((long)acqCfgNew_.Roi.BinX());
   }
   return nRet;
}
/***
* Assymetric binning can be only set in Property Browser.
* If the user sets the BinningY the symmetric binning combo box in MM GUI 
* is not updated (because there is no way to display asymmetric bin in main GUI).
* However, if the user sets the symmetric bin value, then both the asymmetric
* bin values are updated accordingly.
*/
int Universal::OnBinningY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   START_ONPROPERTY("Universal::OnBinningY", eAct);

   int nRet = DEVICE_OK;

   if (eAct == MM::AfterSet)
   {
      long binY;
      pProp->Get(binY);
      if (binY < 1)
      {
         LogMMError( 0, __LINE__, "Value of BinningY has to be positive" );
         nRet = DEVICE_INVALID_PROPERTY_VALUE;
      }
      else
      {
         acqCfgNew_.Roi.SetBinningY(static_cast<uns16>(binY));
         nRet = applyAcqConfig();
      }
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set((long)acqCfgNew_.Roi.BinY());
   }
   return nRet;
}

/***
* This does not seem to be used anywhere.
*/
int Universal::OnOutputTriggerFirstMissing(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   START_ONPROPERTY("Universal::OnOutputTriggerFirstMissing", eAct);
   if (eAct == MM::AfterSet)
   {
      pProp->Get(outputTriggerFirstMissing_);
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set(outputTriggerFirstMissing_);
   }
   return DEVICE_OK;
}

/***
* Turns the automatic circular buffer sizing ON or OFF
*/
int Universal::OnCircBufferSizeAuto(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   START_ONPROPERTY("Universal::OnCircBufferSizeAuto", eAct);
   int ret = DEVICE_OK;
   if (eAct == MM::AfterSet)
   {
      string choice;
      pProp->Get(choice);
      circBufSizeAuto_ = (choice == g_Keyword_ON) ? true : false;

      if (IsCapturing())
      {
          StopSequenceAcquisition();
      }
      else
      {
          // Do not update at live mode, the value gets updated in ResizeImageBufferContinuous
          const unsigned int size = acqCfgCur_.Roi.ImageRgnWidth() * acqCfgCur_.Roi.ImageRgnHeight() * 2;
          ret = updateCircBufRange(size);
      }

      sequenceModeReady_ = false;
   }
   else if (eAct == MM::BeforeGet)
   {
       pProp->Set(circBufSizeAuto_ ? g_Keyword_ON : g_Keyword_OFF);
   }
   return ret;
}

/***
* The size of the frame buffer. Increasing this value may help in a situation when
* camera is delivering frames faster than MM can retrieve them.
*/
int Universal::OnCircBufferFrameCount(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   START_ONPROPERTY("Universal::OnCircBufferFrameCount", eAct);
   int ret = DEVICE_OK;
   if (eAct == MM::AfterSet)
   {
      long value;
      pProp->Get(value);
      circBufFrameCount_ = value;

      if (IsCapturing())
         StopSequenceAcquisition();

      sequenceModeReady_ = false;
   }
   else if (eAct == MM::BeforeGet)
   {
       static_cast<MM::Property*>(pProp)->SetReadOnly(circBufSizeAuto_);
       pProp->Set(static_cast<long>(circBufFrameCount_));
   }
   return ret;
}

int Universal::OnCircBufferFrameRecovery(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   START_METHOD("Universal::OnCircBufFrameRecovery");

   if (eAct == MM::AfterSet)
   {
      string choice;
      pProp->Get(choice);

      circBufFrameRecoveryEnabled_ = (choice.compare(g_Keyword_ON) == 0);
   }
   else if (eAct == MM::BeforeGet)
   {
      if ( circBufFrameRecoveryEnabled_ )
         pProp->Set(g_Keyword_ON);
      else
         pProp->Set(g_Keyword_OFF);
   }

   return DEVICE_OK;
}

/***
* Sets or gets the current expose time
*/
int Universal::OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   START_ONPROPERTY("Universal::OnExposure", eAct);
   // exposure property is stored in milliseconds,
   // whereas the driver returns the value in seconds
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(exposure_);
   }
   else if (eAct == MM::AfterSet)
   {
      double oldExposure = exposure_;
      pProp->Get(exposure_);

      // we need to make sure to reconfigure the acquisition when exposure time changes.
      if (exposure_ != oldExposure)
      {
         // we need to make sure to stop the acquisition when exposure time is changed.
         if (IsCapturing())
            StopSequenceAcquisition();

         sequenceModeReady_ = false;
         singleFrameModeReady_ = false;
      }

   }
   return DEVICE_OK;
}
#ifdef PVCAM_SMART_STREAMING_SUPPORTED
/**
* Enable or disable SMART streaming based on user's input
*/
int Universal::OnSmartStreamingEnable(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   START_ONPROPERTY("Universal::OnSmartStreamingEnable", eAct);

   if (eAct == MM::AfterSet)
   {
      string val;
      
      pProp->Get(val);

      // restart the acquisition only if SMART streaming enable value 
      // has changed, currently it appears MM is restarting acquisition
      // whenever a parameter was touched by user in the UI
      if (prmSmartStreamingEnabled_->Current() != (0 == val.compare(g_Keyword_Yes)) ||
          !prmSmartStreamingEnabled_->Current()!= (0 == val.compare(g_Keyword_No)))
      {
          // The acquisition must be stopped, and will be automatically started again by MMCore
          if (IsCapturing())
             StopSequenceAcquisition();

          // this param requires reconfiguration of the acquisition
          singleFrameModeReady_ = false;
          sequenceModeReady_ = false;
      }

      // enable SMART streaming if user selected Yes
      if ( val.compare(g_Keyword_Yes) == 0 )
      {
         if (DEVICE_OK == prmSmartStreamingEnabled_->Set(TRUE))
         {
             if (DEVICE_OK != prmSmartStreamingEnabled_->Apply())
                return DEVICE_CAN_NOT_SET_PROPERTY;
         }
      }
      // disable SMART streaming if user selected No
      else
      {
         if (DEVICE_OK == prmSmartStreamingEnabled_->Set(FALSE))
         {
             if (DEVICE_OK != prmSmartStreamingEnabled_->Apply())
                return DEVICE_CAN_NOT_SET_PROPERTY;
         }
      }
   }
   else if (eAct == MM::BeforeGet)
   {
      if ( prmSmartStreamingEnabled_->Current() == TRUE )
         pProp->Set( g_Keyword_Yes );
      else
         pProp->Set( g_Keyword_No );
   }
   return DEVICE_OK;
}
#endif

#ifdef PVCAM_SMART_STREAMING_SUPPORTED
/***
* Updates SMART streaming values based on user's input
* User always enters the values in miliseconds
* Internally value is converted to microseconds
*/
int Universal::OnSmartStreamingValues(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   START_ONPROPERTY("Universal::OnSmartStreamingValues", eAct);
   // exposure property is stored in milliseconds,
   // whereas the driver returns the value in seconds
   if (eAct == MM::BeforeGet)
   {
       char expListChars[SMART_STREAM_MAX_EXPOSURES*20];
       int written = 0;
       
       // for values with decimal part > 0.001 display decimal part
       // otherwise display value as integer
       for (int i = 0; i < smartStreamEntries_; i++)
       {
           if (fabs(smartStreamValuesDouble_[i]/1000-round(smartStreamValuesDouble_[i]/1000)) >= 0.001)
           {
              //add semicolon to all but last entry
              if (i<smartStreamEntries_-1)
                 written += sprintf(expListChars+written, "%.3f;", smartStreamValuesDouble_[i]/1000);
              else
                 written += sprintf(expListChars+written, "%.3f", smartStreamValuesDouble_[i]/1000);
           }
           else
           {
              //add semicolon to all but last entry
               if (i<smartStreamEntries_-1)
                 written += sprintf(expListChars+written, "%.0f;", smartStreamValuesDouble_[i]/1000);
              else
                 written += sprintf(expListChars+written, "%.0f", smartStreamValuesDouble_[i]/1000);
           }
       }
       pProp->Set(expListChars);
   }
   else if (eAct == MM::AfterSet)
   {
      string expListChars;

      // The acquisition must be stopped, and will be automatically started again by MMCore
      if (IsCapturing())
         StopSequenceAcquisition();

      // this param requires reconfiguration of the acquisition
      singleFrameModeReady_ = false;
      sequenceModeReady_ = false;

      pProp->Get(expListChars);
      // check only allowed characters have been entered
      if (expListChars.find_first_not_of("0123456789;.") != std::string::npos)
      {
          LogCamError(__LINE__, "SMART Streaming exposures contain forbidden characters");
          return DEVICE_INVALID_PROPERTY_VALUE;
      }

      // currently our cameras support maximum 12 exposures in the SMART streaming list
      // we have allocated space for 128 (SMART_STREAM_MAX_EXPOSURES) exposures each 
      // 20 characters long, check that this hasn't been exceeded
      if (expListChars.length() > 20*SMART_STREAM_MAX_EXPOSURES)
      {
          LogCamError(__LINE__, "SMART Streaming exposure string is too long");
          return DEVICE_INVALID_PROPERTY_VALUE;
      }

      // check that user entered non-empty string
      if (expListChars.length() == 0)
      {
          LogCamError(__LINE__, "SMART Streaming values are empty");
          return DEVICE_INVALID_PROPERTY_VALUE;
      }


      // add semicolon after the last entry if user failed to do so
      // to make the further value processing simpler
      if (expListChars.at(expListChars.length()-1) != ';') 
      {
         expListChars.append(";");
      }

      // back up current number of exposures
      uns16 smartStreamEntriesRecovery = smartStreamEntries_;
      
      // get number of SMART streaming entries
      smartStreamEntries_ = (uns16)std::count(expListChars.begin(), expListChars.end(), ';');
      
      // if user entered more than max allowed number of entries
      // return error and restore previous value of smartStreamEntries
      if (smartStreamEntries_ > prmSmartStreamingValues_->Max().entries)
      {
          LogCamError(__LINE__, "Too many SMART Streaming exposures requested");
          smartStreamEntries_ = smartStreamEntriesRecovery;
          return DEVICE_CAN_NOT_SET_PROPERTY;
      }

      // parse the input string and load the SMART streaming values to our 
      // internal structure smartStreamValuesDouble_
      std::size_t foundAt = 0;
      std::size_t oldFoundAt = 0;
      

      for (int i = 0; i < smartStreamEntries_; i++)
      {
          // look for semicolons and read values
          foundAt = expListChars.find(';', foundAt);

          // check the length of each exposure entry
          std::size_t expCharLength = foundAt - oldFoundAt;

          // if two semicolons were entered with no value between them
          // reject this SMART streaming exposure list
          if (expCharLength == 0)
          {
              LogCamError(__LINE__, "SMART streaming exposure value empty (two semicolons with no value between them)");
              smartStreamEntries_ = smartStreamEntriesRecovery;
              return DEVICE_CAN_NOT_SET_PROPERTY;
          }

          // we should not need more than 10 values before decimal point and 10 values after decimal point, 
          // add one character for decimal point
          // user enters values in miliseconds so this allows hours of exposures, additionally there is no 
          // reason to use SMART streaming with exposures longer than a few hundred miliseconds
          if (expCharLength > 21)
          {
              LogCamError(__LINE__, "SMART streaming exposure value too large");
              smartStreamEntries_ = smartStreamEntriesRecovery;
              return DEVICE_CAN_NOT_SET_PROPERTY;
          }
          
          // 
          std::string substringExposure = expListChars.substr(oldFoundAt, expCharLength);
          
          // check number of decimal points in each exposure time, return error if more than 
          // one decimal point is found in any of the values
          long long nrOfPeriods = std::count(substringExposure.begin(), substringExposure.end(), '.');
          if (nrOfPeriods > 1)
          {
             LogCamError(__LINE__, "SMART streaming exposure value contains too many decimal points");
             smartStreamEntries_ = smartStreamEntriesRecovery;
             return DEVICE_CAN_NOT_SET_PROPERTY;
          }

          smartStreamValuesDouble_[i] = 1000*atof(substringExposure.c_str());
          oldFoundAt = ++foundAt;
      }

   }
   return DEVICE_OK;
}
#endif

/***
* Enables or disables the embedded frame metadata
*/
int Universal::OnMetadataEnabled(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   START_ONPROPERTY("Universal::OnMetadataEnabled", eAct);
   if (eAct == MM::BeforeGet)
   {
      pProp->Set( acqCfgCur_.FrameMetadataEnabled ? g_Keyword_Yes : g_Keyword_No);
   }
   else if (eAct == MM::AfterSet)
   {
      string val;
      pProp->Get(val);
      // The new settings will be applied once the acquisition is restarted
      acqCfgNew_.FrameMetadataEnabled = (0 == val.compare(g_Keyword_Yes));
      return applyAcqConfig();
   }
   return DEVICE_OK;
}

/***
* Enables or disables Centroids acquisition
*/
int Universal::OnCentroidsEnabled(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   START_ONPROPERTY("Universal::OnCentroidsEnabled", eAct);
   if (eAct == MM::BeforeGet)
   {
      pProp->Set( acqCfgCur_.CentroidsEnabled ? g_Keyword_Yes : g_Keyword_No);
   }
   else if (eAct == MM::AfterSet)
   {
      string val;
      pProp->Get(val);
      // The new settings will be applied once the acquisition is restarted
      acqCfgNew_.CentroidsEnabled = (0 == val.compare(g_Keyword_Yes));
      return applyAcqConfig();
   }
   return DEVICE_OK;
}

/***
* Gets and sets the Centroids radius in pixels
*/
int Universal::OnCentroidsRadius(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   START_ONPROPERTY("Universal::OnCentroidsRadius", eAct);

   if (eAct == MM::BeforeGet)
   {
      pProp->Set( (long)acqCfgCur_.CentroidsRadius );
   }
   else if (eAct == MM::AfterSet)
   {
      long val;
      pProp->Get( val );
      // The new settings will be applied once the acquisition is restarted
      acqCfgNew_.CentroidsRadius = val;
      return applyAcqConfig();
   }
   return DEVICE_OK;
}

/***
* Gets and sets the Centroids count (number of ROIs)
*/
int Universal::OnCentroidsCount(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   START_ONPROPERTY("Universal::OnCentroidsCount", eAct);

   if (eAct == MM::BeforeGet)
   {
      pProp->Set( (long)acqCfgCur_.CentroidsCount );
   }
   else if (eAct == MM::AfterSet)
   {
      long val;
      pProp->Get( val );
      // The new settings will be applied once the acquisition is restarted
      acqCfgNew_.CentroidsCount = val;
      return applyAcqConfig();
   }
   return DEVICE_OK;
}

/***
* Gets and sets the Fan speed setpoint
*/
int Universal::OnFanSpeedSetpoint(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   START_ONPROPERTY("Universal::OnFanSpeedSetpoint", eAct);

   if (eAct == MM::BeforeGet)
   {
       pProp->Set(prmFanSpeedSetpoint_->GetEnumString(acqCfgCur_.FanSpeedSetpoint).c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      string valStr;
      pProp->Get( valStr );

      acqCfgNew_.FanSpeedSetpoint = prmFanSpeedSetpoint_->GetEnumValue(valStr);
      return applyAcqConfig();
   }
   return DEVICE_OK;
}

/***
* The PARAM_BIT_DEPTH is read only. The bit depth depends on selected Port and Speed.
*/
int Universal::OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct)
{  
   START_ONPROPERTY("Universal::OnPixelType", eAct);
   
   char buf[8];
   if (eAct == MM::BeforeGet)
   {
      snprintf(buf, 8, "%ubit", camCurrentSpeed_.bitDepth); // 12bit, 14bit, 16bit, ...
      pProp->Set(buf);
   }

   return DEVICE_OK;
}


/***
* Gets or sets the readout speed. The available choices are obtained from the
* speed table which is build in Initialize(). If a change in speed occurs we need
* to update Gain range, Pixel time, Actual Gain, Bit depth and Read Noise.
* See speedChanged()
*/
int Universal::OnReadoutRate(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   START_ONPROPERTY("Universal::OnReadoutRate", eAct);

   uns32 currentPort = prmReadoutPort_->Current();
    
   if (eAct == MM::AfterSet)
   {
      string selectedSpdString;
      pProp->Get(selectedSpdString);

      if (IsCapturing())
          StopSequenceAcquisition();

      // Find the corresponding speed index from reverse speed table
      SpdTabEntry selectedSpd = camSpdTableReverse_[currentPort][selectedSpdString];
      if ( pl_set_param(hPVCAM_, PARAM_SPDTAB_INDEX, (void_ptr)&selectedSpd.spdIndex) != PV_OK )
      {
          LogCamError(__LINE__, "pl_set_param PARAM_SPDTAB_INDEX");
          return DEVICE_CAN_NOT_SET_PROPERTY;
      }
      // Update the current speed if everything succeed
      camCurrentSpeed_ = selectedSpd;
      // Update all speed-dependant variables
      speedChanged();
   }
   else if (eAct == MM::BeforeGet)
   {
       pProp->Set(camCurrentSpeed_.spdString.c_str());
   }

   return DEVICE_OK;
}

/***
* Gets or sets the readout port. Change in readout port resets the speed which
* in turn changes Gain range, Pixel time, Actual Gain, Bit depth and Read Noise.
* see portChanged() and speedChanged();
*/
int Universal::OnReadoutPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   START_ONPROPERTY("Universal::OnReadoutPort", eAct);

   if (eAct == MM::AfterSet)
   {
      string portStr;
      pProp->Get( portStr );
      if ( IsCapturing() )
         StopSequenceAcquisition();

      prmReadoutPort_->Set( portStr );
      prmReadoutPort_->Apply();
      // Update other properties that might have changed because of port change
      portChanged();
   }
   else if (eAct == MM::BeforeGet)
   {
       pProp->Set(prmReadoutPort_->ToString().c_str());
   }

   return DEVICE_OK;
}

/***
* The TriggerTimeOut is used in WaitForExposureDone() to specify how long should we wait
* for a frame to arrive. Increasing this value may help to avoid timouts on long exposures
* or when there are long pauses between triggers.
*/
int Universal::OnTriggerTimeOut(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   START_ONPROPERTY("Universal::OnTriggerTimeOut", eAct);

   if (eAct == MM::AfterSet)
   {
      pProp->Get(triggerTimeout_);
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set(triggerTimeout_);
   }

   return DEVICE_OK;
}

/***
* Trigger mode is set in ResizeImageBufferContinuous
*/
int Universal::OnTriggerMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   START_ONPROPERTY("Universal::OnTriggerMode", eAct);

   if (eAct == MM::AfterSet)
   {
      // The acquisition must be stopped, and will be
      // automatically started again by MMCore
      if (IsCapturing())
         StopSequenceAcquisition();

      // request reconfiguration of acquisition before next use
      singleFrameModeReady_ = false;
      sequenceModeReady_ = false;

      string valStr;
      pProp->Get( valStr );

      prmTriggerMode_->Set( valStr );
      // We don't call Write() here because the PARAM_EXPOSURE_MODE cannot be set,
      // it can only be read and used in pl_setup_cont so we use the
      // prmTriggerMode just as a cache to store our value
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set( prmTriggerMode_->ToString().c_str() );
   }

   return DEVICE_OK;
}

/***
* PARAM_EXPOSE_OUT_MODE
*/
int Universal::OnExposeOutMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   START_ONPROPERTY("Universal::OnExposeOutMode", eAct);

   if (eAct == MM::AfterSet)
   {
      // The acquisition must be stopped, and will be
      // automatically started again by MMCore
      if (IsCapturing())
         StopSequenceAcquisition();

      // request reconfiguration of acquisition before next use
      singleFrameModeReady_ = false;
      sequenceModeReady_ = false;

      string valStr;
      pProp->Get( valStr );

      prmExposeOutMode_->Set( valStr );
      // We don't call Write() here because the PARAM_EXPOSE_OUT_MODE cannot be set,
      // it can only be retrieved and used in pl_setup_cont so we use the
      // prmExposeOutMode just as a cache to store our value
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set( prmExposeOutMode_->ToString().c_str() );
   }

   return DEVICE_OK;
}

/**
* PARAM_CLEAR_CYCLES
*/
int Universal::OnClearCycles(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   START_ONPROPERTY("Universal::OnClearCycles", eAct);

   if (eAct == MM::AfterSet)
   {
      // The acquisition must be stopped, and will be automatically started again by MMCore
      if (IsCapturing())
         StopSequenceAcquisition();

      // this param requires reconfiguration of the acquisition
      singleFrameModeReady_ = false;
      sequenceModeReady_ = false;

      long val;
      pProp->Get( val );
      uns16 pvVal = (uns16)val;

      prmClearCycles_->Set( pvVal );
      prmClearCycles_->Apply();
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set( prmClearCycles_->ToString().c_str() );
   }

   return DEVICE_OK;
}


// Gain
int Universal::OnGain(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   START_ONPROPERTY("Universal::OnGain", eAct);

   int nRet = DEVICE_OK;
   if (eAct == MM::AfterSet)
   {
      std::string gainStr;
      pProp->Get(gainStr); // Get the value MM is trying to set

      if (IsCapturing())
         StopSequenceAcquisition();

      // Convert the gain UI string to actual gain index and apply
      if (camCurrentSpeed_.gainNameMap.find(gainStr) == camCurrentSpeed_.gainNameMap.end())
      {
          LogCamError(__LINE__, "Gain not supported");
          nRet = DEVICE_CAN_NOT_SET_PROPERTY;
      }
      else
      {
          const int16 gainIdx = camCurrentSpeed_.gainNameMap.at(gainStr);
          nRet = prmGainIndex_->SetAndApply(gainIdx);
      }

      singleFrameModeReady_ = false;
   }
   else if (eAct == MM::BeforeGet)
   {
       const std::string gainStr =
           camCurrentSpeed_.gainNameMapReverse.at(prmGainIndex_->Current());
       pProp->Set(gainStr.c_str());
   }

   return nRet;
}


// EM Gain
int Universal::OnMultiplierGain(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   START_ONPROPERTY("Universal::OnMultiplierGain", eAct);

   if (eAct == MM::AfterSet)
   {
      long gain;
      pProp->Get(gain);
      uns16 pvGain = (uns16)gain;

      if (IsCapturing())
         StopSequenceAcquisition();

      prmGainMultFactor_->Set(pvGain);
      prmGainMultFactor_->Apply();
   }
   else if (eAct == MM::BeforeGet)
   {
       pProp->Set((long)prmGainMultFactor_->Current());
   }
   return DEVICE_OK;
}

// Current camera temperature
int Universal::OnTemperature(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   START_ONPROPERTY("Universal::OnTemperature", eAct);
   if (eAct == MM::AfterSet)
   { // Nothing to set, param is read-only
   }
   else if (eAct == MM::BeforeGet)
   {
      // We can read the temperature only if the streaming is not active
      if (!IsCapturing())
      {
         prmTemp_->Update();
      }
      pProp->Set((double)prmTemp_->Current() / 100.0);
   }

   return DEVICE_OK;
}

// Desired camera temperature
int Universal::OnTemperatureSetPoint(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   START_ONPROPERTY("Universal::OnTemperatureSetPoint)", eAct);
   if (eAct == MM::AfterSet)
   {
      double temp;
      pProp->Get(temp);
      int16 pvTemp = (int16)(temp * 100);

      if (IsCapturing())
         StopSequenceAcquisition();

      // Set the value to desired one
      prmTempSetpoint_->Set( pvTemp );
      prmTempSetpoint_->Apply();
   }
   else if (eAct == MM::BeforeGet)
   {
       pProp->Set((double)prmTempSetpoint_->Current()/100.0);
   }

   return DEVICE_OK;
}

/*
* Universal property value handler
* The universal properties are automatically read from the camera and does not need a custom
* value handler. This is useful for simple camera parameters that does not need special treatment.
* So far only Enum and Integer values are supported. Other types should be implemented manaully.
*/
int Universal::OnUniversalProperty(MM::PropertyBase* pProp, MM::ActionType eAct, long index)
{
   START_ONPROPERTY("Universal::OnUniversalProperty", eAct);
   PvUniversalParam* param = universalParams_[index];
   if (eAct == MM::AfterSet)
   {
      // Before sending any value to the camera we must disable the streaming.
      // If the streaming is active the MM will resume it automatically as soon as this method finishes.
      if ( IsCapturing() )
         StopSequenceAcquisition();

      if ( param->IsEnum() )
      {
         // Enum values are treated as strings. The value is displayed as a combo box with values
         // read out from the camera. When the user picks the option, the string is compared in Set
         // method and proper enum value is send to the camera.
         std::string valToSet;
         pProp->Get( valToSet ); // Get the value that MM wants us to set
         param->Set( valToSet ); // Set the value to the PVCAM parameter
      }
      else
      {
          double valToSet;
          pProp->Get( valToSet );
          param->Set( valToSet );
      }
      // We can only Write the parameters to the camera when the streaming is off, this is assured
      // only in this place.
      param->Write();
      // We immediately read the parameter back from the camera because it might get adjusted.
      // The parameter value is cached internaly and as soon as MM resumes the streaming we return
      // this cached value and do not touch the camera at all.
      param->Read();

      // Force the reinitialization of the acquisition
      singleFrameModeReady_ = false;
   }
   else if (eAct == MM::BeforeGet)
   {
      // Here we can only return the cached parameter value. At this point the MM might already
      // resumed the streaming so no pl_set_param or pl_get_param should be called.
      if ( param->IsEnum() )
      {
          pProp->Set( param->ToString().c_str() );
      }
      else
      {
          // So far we only support 64bit double or Enum values for "Universal" properties.
          // No other value types should be added to g_UniversalParams, a regular property
          // with hand made handler should be manually created instead.
          pProp->Set( param->ToDouble() );
      }
   }

   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// API methods
// ~~~~~~~~~~~

void Universal::GetName(char* name) const 
{
   CDeviceUtils::CopyLimitedString(name, camName_/*.c_str()*/);
}

int Universal::initializeUniversalParams()
{
   int nRet = DEVICE_OK;
   universalParams_.clear();
   long propertyIndex = 0;

   // Iterate through all the parameters we have allowed to be used as Universal
   for ( int i = 0; i < g_UniversalParamsCount; i++ ) 
   {
      PvUniversalParam* p = new PvUniversalParam( g_UniversalParams[i].debugName, g_UniversalParams[i].id, this, true);
      if (p->IsAvailable())
      {
         if (p->IsEnum())
         {
            CPropertyActionEx *pAct = new CPropertyActionEx(this, &Universal::OnUniversalProperty, propertyIndex);
            nRet = CreateProperty( g_UniversalParams[i].name, p->ToString().c_str(), MM::String, p->IsReadOnly(), pAct);
            if ( !p->IsReadOnly() )
               SetAllowedValues( g_UniversalParams[i].name, p->GetEnumStrings() );
         }
         else
         {
            CPropertyActionEx *pAct = new CPropertyActionEx(this, &Universal::OnUniversalProperty, propertyIndex);
            nRet = CreateProperty( g_UniversalParams[i].name, p->ToString().c_str(), MM::Integer, p->IsReadOnly(), pAct);
            if ( !p->IsReadOnly() )
            {
               double min = p->GetMin();
               double max = p->GetMax();
               if ( (max-min) > 10 )
               {
                  // The property will show up as slider with defined range
                  SetPropertyLimits(g_UniversalParams[i].name, min, max);
               }
               else if ( (max-min) < 1000000 )
               {
                  // The property will show up as combo box with predefined values
                  vector<std::string> values;
                  for (int j = (int)min; j <= (int)max; j++)
                  {
                     ostringstream os;
                     os << j;
                     values.push_back(os.str());
                  }
                  SetAllowedValues(g_UniversalParams[i].name, values);
               }
               else
               {
                   // The property will be a simple edit box with editable value
                   LogMessage("PVCAM: The property has too large range. Not setting limits.");
               }
            }
         }
         universalParams_.push_back(p);
         propertyIndex++;
      }
      else
      {
         delete p;
      }
   }

   return nRet;
}

int Universal::initializePostProcessing()
{
   int nRet = DEVICE_OK;

#ifdef WIN32

   rs_bool bAvail;
   CPropertyAction *pAct;

   if (pl_get_param(hPVCAM_, PARAM_PP_INDEX, ATTR_AVAIL, &bAvail) && bAvail)
   {

      long CntPP = 0;
      uns32 PP_count = 0;
      ostringstream resetName;

      // begin setup standard value names
      vector<std::string> boolValues;
      boolValues.push_back(g_Keyword_No);
      boolValues.push_back(g_Keyword_Yes);
      // end setup standard value names

      pAct = new CPropertyAction (this, &Universal::OnResetPostProcProperties);

      assert(nRet == DEVICE_OK);

      if (pl_get_param(hPVCAM_, PARAM_PP_INDEX, ATTR_COUNT, &PP_count))
      {
         for (int16 i = 0 ; i < (int16)PP_count; i++) 
         {
            char featName[PARAM_NAME_LEN];
            char propName[PARAM_NAME_LEN];

            uns32 min, max, curValueInt; 

            if (pl_set_param(hPVCAM_, PARAM_PP_INDEX, &i))
            {
               if (pl_get_param(hPVCAM_,PARAM_PP_FEAT_NAME, ATTR_CURRENT, featName))
               {
                  uns32 paramCnt =  0;
                  ostringstream featNameStream;

                  // encourage a meaningful sort in the micromanager property browser window
                  featNameStream << "PP" << setw(3) << i << " " << featName;

                  // create a read-only property for the name of the feature
                  nRet = CreateProperty(featNameStream.str().c_str(), featName, MM::String, true);

                  if (pl_get_param(hPVCAM_, PARAM_PP_PARAM_INDEX, ATTR_COUNT, &paramCnt))
                  {
                     for (int16 j = 0; j < (int16)paramCnt; j++)
                     {
                        if (pl_set_param(hPVCAM_, PARAM_PP_PARAM_INDEX, &j))
                        {
                           ostringstream paramNameStream;
                           ostringstream currentValueStream;

                           if( pl_get_param(hPVCAM_, PARAM_PP_PARAM_NAME, ATTR_CURRENT, propName) )
                           {
                              // encourage a meaningful sort in the micromanager property browser window
                              //  note that we want the properties to show up under their respective feature name
                              paramNameStream << "PP" << setw(3) << i+1 << "   " << propName;

                              pl_get_param(hPVCAM_, PARAM_PP_PARAM, ATTR_MIN, &min);
                              pl_get_param(hPVCAM_, PARAM_PP_PARAM, ATTR_MAX, &max);
                              pl_get_param(hPVCAM_, PARAM_PP_PARAM, ATTR_CURRENT, &curValueInt);

                              // convert current value of parameter to string
                              currentValueStream << curValueInt;

                              CPropertyActionEx *pExAct = new CPropertyActionEx(this, &Universal::OnPostProcProperties, CntPP++);

                              // create a special drop-down control box for booleans
                              if (max - min == 1)
                              {
                                 nRet = CreateProperty(paramNameStream.str().c_str(), currentValueStream.str().c_str(), MM::String, false, pExAct);
                                 SetAllowedValues(paramNameStream.str().c_str(), boolValues);
                              }
                              else 
                              {
                                 nRet = CreateProperty(paramNameStream.str().c_str(), currentValueStream.str().c_str(), MM::Integer, false, pExAct);
                                 SetPropertyLimits(paramNameStream.str().c_str(), min, max);
                              }

                              PpParam* ptr = new PpParam(paramNameStream.str().c_str(), i,j);
                              ptr->SetRange(max-min);
                              PostProc_.push_back (*ptr);
                              delete ptr;
                           }
                        }
                     }
                  }
               }
            }
         }  
      }
         
      // encourage a meaningful sort in the micromanager property browser window
      resetName << "PP" << setw(3) << PP_count+1 << " Reset";
      nRet = CreateProperty(resetName.str().c_str(), g_Keyword_No, MM::String, false, pAct);
      nRet = SetAllowedValues(resetName.str().c_str(), boolValues);

      // Reset the post processing and reload all PP values
      if(!pl_pp_reset(hPVCAM_))
      {
         LogCamError(__LINE__, "pl_pp_reset");
      }

      refreshPostProcValues();
   }

#endif
   return nRet;
}



bool Universal::Busy()
{
   START_METHOD("Universal::Busy");
   return snappingSingleFrame_;
}


/**
 * Function name   : &Universal::SnapImage
 * Description     : Acquires a single frame and stores it in the internal
 *                   buffer.
 *                   This command blocks the calling thread
 *                   until the image is fully captured.
 * Return type     : int 
 * Timing data     : On an Intel Mac Pro OS X 10.5 with CoolsnapEZ, 0 ms exposure
 *                   pl_exp_start_seq takes 28 msec
 *                   WaitForExposureDone takes 25 ms, for a total of 54 msec
 */                   
int Universal::SnapImage()
{
   START_METHOD("Universal::SnapImage");

   MM::MMTime start = GetCurrentMMTime();

   int nRet = DEVICE_ERR;

   nRet = applyAcqConfig();
   if (nRet != DEVICE_OK)
      return nRet;

   if(snappingSingleFrame_)
   {
      LogMessage("Warning: Entering SnapImage while GetImage has not been done for previous frame", true);
      return nRet;
   }

   if(IsCapturing())
      return DEVICE_CAMERA_BUSY_ACQUIRING;

   if(!singleFrameModeReady_)
   {
      g_pvcamLock.Lock();
      if (!pl_exp_stop_cont(hPVCAM_, CCS_HALT))
         LogCamError(__LINE__, "");
      if (!pl_exp_finish_seq(hPVCAM_, circBuf_.Data(), 0))
         LogCamError(__LINE__, "");
      g_pvcamLock.Unlock();

      MM::MMTime mid = GetCurrentMMTime();
      LogTimeDiff(start, mid, "Exposure took 1: ", true);

      nRet = ResizeImageBufferSingle();
      if (nRet != DEVICE_OK) 
         return LogMMError(nRet, __LINE__);
      singleFrameModeReady_ = true;

      mid = GetCurrentMMTime();
      LogTimeDiff(start, mid, "Exposure took 2: ", true);
  }

   snappingSingleFrame_ = true;
   imagesToAcquire_ = 1; 
   imagesInserted_ = 0;
   lastPvFrameNr_ = 0;

   g_pvcamLock.Lock();
   if (!pl_exp_start_seq(hPVCAM_, singleFrameBufRaw_))
   {
      g_pvcamLock.Unlock();
      return LogCamError(__LINE__);
   }
   else 
   {
      g_pvcamLock.Unlock();
   }
   MM::MMTime end = GetCurrentMMTime();

   LogTimeDiff(start, end, "Exposure took 3: ", true);

   if (WaitForExposureDone())
   { 
        nRet = postProcessSingleFrame(&singleFrameBufFinal_, singleFrameBufRaw_, singleFrameBufRawSz_);
   }
   else
   {
      //Exposure was not done correctly. if application nevertheless 
      //tries to get (wrong) image by calling GetImage, the error will be reported
      snappingSingleFrame_ = false;
      singleFrameModeReady_ = false;
   }

   end = GetCurrentMMTime();

#ifdef PVCAM_SMART_STREAMING_SUPPORTED
   g_pvcamLock.Lock(); 

   //after the image was snapped enable SMART streaming if it was enabled before the Snap
   if (ssWasOn_ == true)
   {
       SetProperty(g_Keyword_SmartStreamingEnable, g_Keyword_Yes);
   }
   g_pvcamLock.Unlock();
#endif

   LogTimeDiff(start, end, "Exposure took 4: ", true);

   return nRet;
}

/**
* Called from SnapImage(). Waits until the acquisition of single frame finishes.
* This method is used for single frame acquisition only.
*/
bool Universal::WaitForExposureDone()throw()
{
   START_METHOD("Universal::WaitForExposureDone");

   MM::MMTime startTime = GetCurrentMMTime();
   bool bRet = false;
   rs_bool rsbRet = 0;

   try
   {
      int16 status;
      uns32 not_needed;

      const double estReadTimeSec = EstimateMaxReadoutTimeMs() / 1000.0f;
      // make the time out 2 seconds (default trigger timeout) plus twice the exposure
      MM::MMTime timeout((long)(triggerTimeout_ + estReadTimeSec + 2*GetExposure() * 0.001), (long)(2*GetExposure() * 1000));
      MM::MMTime startTime = GetCurrentMMTime();
      MM::MMTime elapsed(0,0);

      if ( !isUsingCallbacks_ )
      {  // Polling
         do 
         {
            CDeviceUtils::SleepMs(1);
            g_pvcamLock.Lock();
            rsbRet = pl_exp_check_status(hPVCAM_, &status, &not_needed);
            g_pvcamLock.Unlock();
            elapsed = GetCurrentMMTime()  - startTime;
        } while (rsbRet && (status == EXPOSURE_IN_PROGRESS) && elapsed < timeout); 

         while (rsbRet && (status == READOUT_IN_PROGRESS) && elapsed < timeout)
         {
            CDeviceUtils::SleepMs(1);
            g_pvcamLock.Lock();
            rsbRet = pl_exp_check_status(hPVCAM_, &status, &not_needed);
            g_pvcamLock.Unlock();
            elapsed = GetCurrentMMTime() - startTime;
         }
        
         if (rsbRet == TRUE && elapsed < timeout && status != READOUT_FAILED)
         {
            bRet=true;
         }
         else
         {
            LogCamError(__LINE__, "Readout Failed");
            g_pvcamLock.Lock();
            if (!pl_exp_abort(hPVCAM_, CCS_HALT))
               LogCamError(__LINE__, "");
            g_pvcamLock.Unlock();
         }
      }
      else
      {  // Callbacks
         // Once the notification thread inserts a frame to the MMCore the
         // imagesInserted_ is increased
         while ( imagesInserted_ != imagesToAcquire_ && elapsed < timeout )
         {
            elapsed = GetCurrentMMTime()  - startTime;
            CDeviceUtils::SleepMs(1);
         }
         if ( elapsed < timeout )
         {
            bRet = true;
         }
         else
         {
            g_pvcamLock.Lock();
            if (!pl_exp_abort(hPVCAM_, CCS_HALT))
               LogCamError(__LINE__, "");
            g_pvcamLock.Unlock();
            LogCamError(__LINE__, "Readout Timeouted");
         }
      }
   }
   catch(...)
   {
      LogMMMessage(__LINE__, "Unknown exception while waiting for exposure to finish", false);
   }

   return bRet;
}

const unsigned char* Universal::GetImageBuffer()
{  
   START_METHOD("Universal::GetImageBuffer");

   if(!snappingSingleFrame_)
   {
      LogMMMessage(__LINE__, "Warning: GetImageBuffer called before SnapImage()");
      return 0;
   }

   snappingSingleFrame_ = false;

   return (unsigned char*)singleFrameBufFinal_;
}

const unsigned int* Universal::GetImageBufferAsRGB32()
{  
   START_METHOD("Universal::GetImageBufferAsRGB32");

   if(!snappingSingleFrame_)
   {
      LogMMMessage(__LINE__, "Warning: GetImageBufferAsRGB32 called before SnapImage()");
      return 0;
   }

   snappingSingleFrame_ = false;

   return (unsigned int*)singleFrameBufFinal_;
}


double Universal::GetExposure() const
{
   START_METHOD("Universal::GetExposure");
   char buf[MM::MaxStrLength];
   buf[0] = '\0';
   GetProperty(MM::g_Keyword_Exposure, buf);
   return atof(buf);
}

void Universal::SetExposure(double exp)
{
   START_METHOD("Universal::SetExposure");
   int ret = SetProperty(MM::g_Keyword_Exposure, CDeviceUtils::ConvertToString(exp));
   if (ret != DEVICE_OK)
      LogMMError(ret, __LINE__);
}

/**
* Returns the number of bits per pixel.
* IN COLOR MODE THIS MEHOD RETURNS MODIFIED VALUE
* 
*/
unsigned Universal::GetBitDepth() const
{
   return acqCfgCur_.ColorProcessingEnabled ? 8 : (unsigned)camCurrentSpeed_.bitDepth;
}

long Universal::GetImageBufferSize() const
{
   const int bytesPerPixel = acqCfgCur_.ColorProcessingEnabled ? 4 : 2;
   return acqCfgCur_.Roi.ImageRgnWidth() * acqCfgCur_.Roi.ImageRgnHeight() * bytesPerPixel;
}


int Universal::SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize)
{
   START_METHOD("Universal::SetROI");

   int nRet = DEVICE_OK;

   // PVCAM does not like ROIs smaller than 2x2 pixels (8 bytes)
   // (This check avoids crash for 1x1 ROIs in PVCAM 2.9.5)
   if ( xSize * ySize < 4 )
   {
      LogCamError( __LINE__, "Universal::SetROI ROI size not supported" );
      return ERR_ROI_SIZE_NOT_SUPPORTED;
   }

   // We keep the ROI definition in sensor coordinates however the coordinates given by uM are
   // related to the actual image the ROI was drawn on. We need to take the current binning
   // into account.
   const PvRoi& curRoi = acqCfgCur_.Roi;
   acqCfgNew_.Roi.SetSensorRgn(
       static_cast<uns16>(x * curRoi.BinX()),
       static_cast<uns16>(y * curRoi.BinY()),
       static_cast<uns16>(xSize * curRoi.BinX()),
       static_cast<uns16>(ySize * curRoi.BinY()));
   nRet = applyAcqConfig();

   return nRet;
}

int Universal::GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize)
{
   START_METHOD("Universal::GetROI");

   const PvRoi& roi = acqCfgCur_.Roi;
   x = roi.ImageRgnX();
   y = roi.ImageRgnY();
   xSize = roi.ImageRgnWidth();
   ySize = roi.ImageRgnHeight();

   return DEVICE_OK;
}

int Universal::ClearROI()
{
   START_METHOD("Universal::ClearROI");

   int nRet = DEVICE_OK;

   acqCfgNew_.Roi.SetSensorRgn(0, 0, camSerSize_, camParSize_);
   nRet = applyAcqConfig();

   return nRet;
}

bool Universal::GetErrorText(int errorCode, char* text) const
{
   if (CCameraBase<Universal>::GetErrorText(errorCode, text))
      return true; // base message

   char buf[ERROR_MSG_LEN];
   if (pl_error_message ((int16)errorCode, buf))
   {
      CDeviceUtils::CopyLimitedString(text, buf);
      return true;
   }
   else
      return false;
}

///////////////////////////////////////////////////////////////////////////////
// Utility methods
///////////////////////////////////////////////////////////////////////////////

int Universal::portChanged()
{
    std::vector<std::string> spdChoices;
    uns32 curPort = prmReadoutPort_->Current();

    // Read the available speeds for this port from our speed table
    std::map<int16, SpdTabEntry>::iterator i = camSpdTable_[curPort].begin();
    for( ; i != camSpdTable_[curPort].end(); ++i )
    {
        spdChoices.push_back(i->second.spdString);
    }

    // Set the allowed readout rates
    SetAllowedValues( g_ReadoutRate, spdChoices );

    // Set the current speed to first avalable rate, this will cause the speedChanged() call.
    // TODO: This needs to be revisited. When SetProperty is called on a read-only property
    // the handler is not called at all. If we have port options that have just single speed
    // and thus we make that property read-only this call will not work properly.
    SetProperty( g_ReadoutRate, spdChoices[0].c_str()); 

    return DEVICE_OK;
}

int Universal::speedChanged()
{
    // Prepare the gain options for the current speed
    vector<string> gainChoices;
    for (int16 i = camCurrentSpeed_.gainMin; i <= camCurrentSpeed_.gainMax; i++)
    {
        gainChoices.push_back(camCurrentSpeed_.gainNameMapReverse.at(i));
    }
    SetAllowedValues(MM::g_Keyword_Gain, gainChoices);

    // If the current gain is applicable for the new speed we want to restore it.
    // Change in speed automatically resets GAIN in PVCAM, so we want to preserve it.
    // We can use the prmGainIndex_->Current() because it still contains the previous
    // cached value (we didn't call Update/Apply yet)
    int16 curGain = prmGainIndex_->Current();
    if ( curGain < camCurrentSpeed_.gainMin || curGain > camCurrentSpeed_.gainMax )
    {
        // The new speed does not support this gain index, so we reset it to the first available
        curGain = camCurrentSpeed_.gainDef;
    }

    // TODO: This needs to be revisited. When SetProperty is called on a read-only property
    // the handler is not called at all. If we have speed options that have just single gain
    // and thus we make that property read-only this call will not work properly.
    SetProperty( MM::g_Keyword_Gain, camCurrentSpeed_.gainNameMapReverse.at(curGain).c_str());

    return DEVICE_OK;
}

/*
* Build the speed table based on camera settings. We use the speed table to get actual
* bit depth, readout speed and gain range based on speed index.
*/
int Universal::buildSpdTable()
{
    uns32 portCount = 0; // Total number of readout ports
    int32 spdCount = 0;  // Number of speed choices for each port
    camSpdTable_.clear();
    camSpdTableReverse_.clear();

    if (pl_get_param(hPVCAM_, PARAM_READOUT_PORT, ATTR_COUNT, (void_ptr)&portCount) != PV_OK)
       return LogCamError(__LINE__, "pl_get_param PARAM_READOUT_PORT ATTR_COUNT" );

    // Iterate through each port and fill in the speed table
    for (uns32 portIndex = 0; portIndex < portCount; portIndex++)
    {
        if (pl_set_param(hPVCAM_, PARAM_READOUT_PORT, (void_ptr)&portIndex) != PV_OK)
           return LogCamError(__LINE__, "pl_set_param PARAM_READOUT_PORT" );

        if (pl_get_param(hPVCAM_, PARAM_SPDTAB_INDEX, ATTR_COUNT, (void_ptr)&spdCount) != PV_OK)
           return LogCamError(__LINE__, "pl_get_param PARAM_SPDTAB_INDEX ATTR_COUNT" );

        for (int16 spdIndex = 0; spdIndex < spdCount; spdIndex++)
        {
           SpdTabEntry spdEntry;
           spdEntry.portIndex = portIndex;
           spdEntry.spdIndex = spdIndex;

           if (pl_set_param(hPVCAM_, PARAM_SPDTAB_INDEX, (void_ptr)&spdEntry.spdIndex) != PV_OK)
              return LogCamError(__LINE__, "pl_set_param PARAM_SPDTAB_INDEX" );

           // Read the pixel time for this speed choice
           if (pl_get_param(hPVCAM_, PARAM_PIX_TIME, ATTR_CURRENT, (void_ptr)&spdEntry.pixTime) != PV_OK)
           {
               LogCamError(__LINE__, "pl_get_param PARAM_PIX_TIME failed, using default pix time" );
               spdEntry.pixTime = MAX_PIX_TIME;
           }
           // Read the bit depth for this speed choice
           if (pl_get_param(hPVCAM_, PARAM_BIT_DEPTH, ATTR_CURRENT, &spdEntry.bitDepth) != PV_OK )
           {
               return LogCamError(__LINE__, "pl_get_param PARAM_GAIN_INDEX ATTR_CURRENT" );
           }
           // Read the sensor color mask for the current speed
           rs_bool colorAvail = FALSE;
           // Deafult values for cameras that do not support color mode
           spdEntry.colorMaskStr = "Grayscale";
           spdEntry.colorMask = COLOR_NONE;
           if (pl_get_param(hPVCAM_, PARAM_COLOR_MODE, ATTR_AVAIL, &colorAvail) == PV_OK && colorAvail == TRUE)
           {
               int32 colorCount = 0;
               if (pl_get_param(hPVCAM_, PARAM_COLOR_MODE, ATTR_COUNT, &colorCount) != PV_OK || colorCount < 1)
               {
                   return LogCamError(__LINE__, "pl_get_param PARAM_COLOR_MODE ATTR_COUNT" );
               }
               int32 colorCur = 0;
               if (pl_get_param(hPVCAM_, PARAM_COLOR_MODE, ATTR_CURRENT, &colorCur) != PV_OK)
               {
                   return LogCamError(__LINE__, "pl_get_param PARAM_COLOR_MODE ATTR_CURRENT" );
               }
               // We need to find the value/string that corresponds to ATTR_CURRENT value
               // First a couple of hacks for older cameras. Old PVCAM prior 3.0.5.2 (inclusive) reported
               // only two values, COLOR_NONE and COLOR_RGGB. However some cameras actually had different mask.
               bool bFound = false;
               if (colorCount == 2 && cameraModel_ == PvCameraModel_OptiMos_M1)
               {
                   // OptiMos actually uses GRBG mask
                   bFound = true;
                   spdEntry.colorMask = COLOR_GRBG;
                   spdEntry.colorMaskStr.assign("Color (GRBG)");
               }
               else if (colorCount == 2 && cameraModel_ == PvCameraModel_Retiga6000C)
               {
                   // Retiga 6000C with initial firmware had GRBG mask on slowest speed.
                   bFound = true;
                   if (portIndex == 0 && spdIndex == 2)
                   {
                       spdEntry.colorMask = COLOR_GRBG;
                       spdEntry.colorMaskStr.assign("Color (GRBG)");
                   }
                   else
                   {
                       spdEntry.colorMask = COLOR_RGGB;
                       spdEntry.colorMaskStr.assign("Color (RGGB)");
                   }
               }
               for (int32 i = 0; i < colorCount && !bFound; ++i)
               {
                   uns32 enumStrLen = 0;
                   if (pl_enum_str_length( hPVCAM_, PARAM_COLOR_MODE, i, &enumStrLen) != PV_OK)
                   {
                       return LogCamError(__LINE__, "pl_enum_str_length PARAM_COLOR_MODE" );
                   }
                   char* enumStrBuf = new char[enumStrLen+1];
                   enumStrBuf[enumStrLen] = '\0';
                   int32 enumVal = 0;
                   if (pl_get_enum_param(hPVCAM_, PARAM_COLOR_MODE, i, &enumVal, enumStrBuf, enumStrLen) != PV_OK)
                   {
                       return LogCamError(__LINE__, "pl_get_enum_param PARAM_COLOR_MODE" );
                   }
                   if (enumVal == colorCur)
                   {
                       bFound = true;
                       spdEntry.colorMask = enumVal;
                       spdEntry.colorMaskStr.assign(enumStrBuf);
                   }
                   delete[] enumStrBuf;
               }
               if (!bFound)
                   return LogCamError(__LINE__, "ATTR_CURRENT of PARAM_COLOR_MODE does not correspond to any reported ENUM value" );
           }

           // Read the gain range for this speed choice if applicable
           if (pl_get_param(hPVCAM_, PARAM_GAIN_INDEX, ATTR_AVAIL, &spdEntry.gainAvail) != PV_OK )
           {
               LogCamError(__LINE__, "pl_get_param PARAM_GAIN_INDEX ATTR_AVAIL failed, not using gain at this speed" );
               spdEntry.gainAvail = FALSE;
           }
           if (spdEntry.gainAvail)
           {
               if (pl_get_param(hPVCAM_, PARAM_GAIN_INDEX, ATTR_MIN, &spdEntry.gainMin) != PV_OK )
               {
                   LogCamError(__LINE__, "pl_get_param PARAM_GAIN_INDEX ATTR_MIN failed, using default" );
                   spdEntry.gainMin = 1;
               }
               if (pl_get_param(hPVCAM_, PARAM_GAIN_INDEX, ATTR_MAX, &spdEntry.gainMax) != PV_OK )
               {
                   LogCamError(__LINE__, "pl_get_param PARAM_GAIN_INDEX ATTR_MAX failed, using default" );
                   spdEntry.gainMax = 1;
               }
               if (pl_get_param(hPVCAM_, PARAM_GAIN_INDEX, ATTR_DEFAULT, &spdEntry.gainDef) != PV_OK )
               {
                   LogCamError(__LINE__, "pl_get_param PARAM_GAIN_INDEX ATTR_DEFAULT failed, using min" );
                   spdEntry.gainDef = spdEntry.gainMin;
               }

               // Iterate all gains and read each gain name if supported. If not supported or
               // if we cannot successfully retrieve the string we fall back to a simple
               // string_value:value pair. (e.g. "1":1, "2":2, ...)
               for (int16 gainIdx = spdEntry.gainMin; gainIdx <= spdEntry.gainMax; ++gainIdx)
               {
                   // Let's assume we won't succeed and prepare the name as a simple number,
                   // this just makes the following code much easier.
                   std::string gainNameStr = CDeviceUtils::ConvertToString(gainIdx);
#ifdef PVCAM_3_0_12_SUPPORTED
                   rs_bool gainNameAvail = FALSE;
                   if (pl_get_param(hPVCAM_, PARAM_GAIN_NAME, ATTR_AVAIL, &gainNameAvail) == PV_OK
                       && gainNameAvail == TRUE)
                   {
                       if (pl_set_param(hPVCAM_, PARAM_GAIN_INDEX, &gainIdx) == PV_OK)
                       {
                           char gainName[MAX_GAIN_NAME_LEN];
                           if (pl_get_param(hPVCAM_, PARAM_GAIN_NAME, ATTR_CURRENT, gainName) == PV_OK)
                           {
                               gainNameStr.assign(gainName);
                           }
                       }
                   }
#endif // PVCAM_3_0_12_SUPPORTED
                   spdEntry.gainNameMap[gainNameStr] = gainIdx;
                   spdEntry.gainNameMapReverse[gainIdx] = gainNameStr;
               }
           } // if (spdEntry.gainAvail)

           // Save the string we will use in user interface for this choice
           stringstream tmp;
           // Convert the pix time to MHz and append bit depth
           tmp << 1000.0f/spdEntry.pixTime << "MHz " << spdEntry.bitDepth << "bit";
           spdEntry.spdString = tmp.str();

           camSpdTable_[portIndex][spdIndex] = spdEntry;
           camSpdTableReverse_[portIndex][tmp.str()] = spdEntry;
        }
    }
    // Since we have iterated through all the ports/speeds/gains we should reset the cam to default state
    if (pl_set_param(hPVCAM_, PARAM_READOUT_PORT, (void_ptr)&camSpdTable_[0][0].portIndex) != PV_OK)
        return LogCamError(__LINE__, "pl_set_param PARAM_READOUT_PORT" );
    if (pl_set_param(hPVCAM_, PARAM_SPDTAB_INDEX, (void_ptr)&camSpdTable_[0][0].spdIndex) != PV_OK)
        return LogCamError(__LINE__, "pl_set_param PARAM_SPDTAB_INDEX" );
    if (camSpdTable_[0][0].gainAvail)
    {
        if (pl_set_param(hPVCAM_, PARAM_GAIN_INDEX, (void_ptr)&camSpdTable_[0][0].gainDef) != PV_OK)
            return LogCamError(__LINE__, "pl_set_param PARAM_GAIN_INDEX" );
    }
    camCurrentSpeed_ = camSpdTable_[0][0];

    return DEVICE_OK;
}

/**
* Calculates and sets the circular buffer count limits based on frame
* size and hardcoded limits.
*/
int Universal::updateCircBufRange(unsigned int frameSize)
{
   int nRet = DEVICE_OK;

   if (circBufSizeAuto_)
   {
      // In auto mode the property is read-only and has no limits. There is a little catch though,
      // to disable the "slider" when the property is made read-only we need to call 
      // SetPropertyLimits(..., 0, 0), however this returns an error DEVICE_INVALID_PROPERTY_LIMTS
      // even though the SetLimits() implementation suggests that 0, 0 will disable the limit checking.
      // For this reason, no error checking here.
      SetPropertyLimits( g_Keyword_CircBufFrameCnt, 0, 0 );
   }
   else
   {
      // For circular buffer in manual mode we have two limits. First is the
      // maximum size in bytes and second is maximum number of frames in the buffer.
      int maxCount = static_cast<int>(CIRC_BUF_SIZE_MAX_USER / frameSize);
      if (maxCount > CIRC_BUF_FRAME_CNT_MAX)
         maxCount = CIRC_BUF_FRAME_CNT_MAX;

      // If the current configuration is over the new limit, adjust it
      if (circBufFrameCount_ > maxCount)
         circBufFrameCount_ = maxCount;

      // Adjust the property limits so the user cannot set unsupported value
      nRet = SetPropertyLimits( g_Keyword_CircBufFrameCnt, CIRC_BUF_FRAME_CNT_MIN, maxCount );
   }

   if (nRet != DEVICE_OK)
      return nRet;

   // Unfortunately we need to call the OnPropert*ies*Changed because this seems to
   // be the only way that correctly updates the property range in the UI. The 
   // OnPropert*y*Changed updates the value only.
   nRet = OnPropertiesChanged();

   return nRet;
}

/**
* Selects the correct mask setting for debayering algorithm based on current
* ROI and sensor physical mask
* NOTE: The function takes the PVCAM color mode (sensor mask reported by PVCAM)
* but return the PvDebayer.h interpolation algorithm. These two are basically the
* same but each group have different values (COLOR_RGGB != CFA_RGGB)
* @param xRoiPos ROI serial position in sensor coordinates (binning agnostic)
* @param yRoiPos ROI parallel position in sensor coordinates (binning agnostic)
* @param pvcamColorMode A color mode as returned by PARAM_COLOR_MODE
*/
int Universal::selectDebayerAlgMask(int xRoiPos, int yRoiPos, int32 pvcamColorMode) const
{
    // Check if ROI position is odd in any direction, we will need to shift
    // the final mask appropriately
    const int xShift = (xRoiPos % 2);
    const int yShift = (yRoiPos % 2);

    // A simple color mask lookup table that assumes the RGGB mask is default,
    // it represents a simple 3x3 pixel array:
    // R G R
    // G B G
    // R G R
    // Based on ROI shift it will simply help us to pick the correct mask
    static const int maskMatrix[3][3] = {
        {CFA_RGGB, CFA_GRBG, CFA_RGGB},
        {CFA_GBRG, CFA_BGGR, CFA_GBRG},
        {CFA_RGGB, CFA_GRBG, CFA_RGGB}};

    switch (pvcamColorMode)
    {
    case COLOR_RGGB:
        return maskMatrix[xShift + 0][yShift + 0];
    case COLOR_GRBG:
        return maskMatrix[xShift + 1][yShift + 0];
    case COLOR_GBRG:
        return maskMatrix[xShift + 0][yShift + 1];
    case COLOR_BGGR:
        return maskMatrix[xShift + 1][yShift + 1];
    default:
        return CFA_RGGB;
    }
}

/**
* This function should be called every time the user changes the camera
* configuration, either by selecting a ROI or changing a Device Property via
* Device/Property Browser or any other event.
* This function should validate and apply the new acquisition configuration
* to the camera, if not accepted the setting should be reverted and error returned.
*/
int Universal::applyAcqConfig()
{
   // If we are capturing do not do anything, this function will be called
   // again once the acquisition is restarted
   if (IsCapturing())
   {
      return DEVICE_OK;
   }

   int nRet = DEVICE_OK;

   // If we are not acquiring, we can configure the camera right away

   // Some changes will require reallocation of our buffers
   bool bufferResizeRequired = false;
   // This function is called on several places. After a change in property and
   // upon starting acquisition. If we are not running live mode the configuration
   // will get applied immediately. To avoid re-applying the configuration in
   // StartSequenceAcquisition() we use the following flag.
   // TODO: Write custom comparer for AcqConfig class and use (cfgNew != cfgOld)
   bool configChanged = false;

   // Enabling or disabling color mode requires buffer reallocation
   if (acqCfgNew_.ColorProcessingEnabled != acqCfgCur_.ColorProcessingEnabled)
   {
       configChanged = true;
       bufferResizeRequired = true;
   }

   const PvRoi& newRoi = acqCfgNew_.Roi;
   const PvRoi& curRoi = acqCfgCur_.Roi;

   // NOTE: Some features do not work when turned on together. E.g. Centroids may
   // not work with Binning, or something else may not work when ROI is selected.
   // Do the validation FIRST, BEFORE sending any other property to the camera.

   // VALIDATE the ROI dimensions. This is especially useful when user is trying to apply
   // ROI that was drawn on a different image. Or drawn on an image acquired with 1x1 binning but
   // the current configuration uses 2x2 binning. We cannot easily detect that because uM
   // won't tell us what binning we should use when applying ROI.
   if (newRoi.SensorRgnWidth() > camSerSize_ || newRoi.SensorRgnHeight() > camParSize_ ||
       newRoi.SensorRgnX() > camSerSize_ || newRoi.SensorRgnY() > camParSize_ ||
       newRoi.SensorRgnX() + newRoi.SensorRgnWidth() > camSerSize_ ||
       newRoi.SensorRgnY() + newRoi.SensorRgnHeight() > camParSize_)
   {
      acqCfgNew_ = acqCfgCur_; // New settings not accepted, reset it back to previous state
      LogCamError( __LINE__, "Universal::applyAcqConfig() ROI definition is invalid for current camera configuration" );
      return ERR_ROI_DEFINITION_INVALID;
   }

   // VALIDATE Centroids (PrimeEnhance), so far it works with 1x1 binning only
   if (acqCfgNew_.CentroidsEnabled && acqCfgNew_.Roi.BinX() > 1 && acqCfgNew_.Roi.BinY() > 1)
   {
      acqCfgNew_ = acqCfgCur_; // New settings not accepted, reset it back to previous state
      LogCamError( __LINE__, "Universal::applyAcqConfig() Centroids (PrimeEnhance) is not supported with binning." );
      return ERR_BINNING_INVALID;
   }

   // Change in ROI or binning requires buffer reallocation
   if (newRoi.BinX() != curRoi.BinX() || newRoi.BinY() != curRoi.BinY() ||
       newRoi.SensorRgnX() != curRoi.SensorRgnX() || newRoi.SensorRgnY() != curRoi.SensorRgnY() ||
       newRoi.SensorRgnWidth() != curRoi.SensorRgnWidth() || newRoi.SensorRgnHeight() != curRoi.SensorRgnHeight())
   {
       configChanged = true;
       bufferResizeRequired = true;
   }

   // Centroids require frame metadata so we need to check this first and
   // enable the metadata automatically if needed, the setting will be sent
   // to the camera right after this statement.
   if ((acqCfgNew_.CentroidsEnabled != acqCfgCur_.CentroidsEnabled) && acqCfgNew_.CentroidsEnabled)
       if (!acqCfgCur_.FrameMetadataEnabled)
           acqCfgNew_.FrameMetadataEnabled = true;

   // Frame metadata feature
   if (acqCfgNew_.FrameMetadataEnabled != acqCfgCur_.FrameMetadataEnabled)
   {
      configChanged = true;
      bufferResizeRequired = true;
      nRet = prmMetadataEnabled_->SetAndApply(acqCfgNew_.FrameMetadataEnabled ? TRUE : FALSE);
      if (nRet != DEVICE_OK)
      {
         acqCfgNew_ = acqCfgCur_; // New settings not accepted, reset it back to previous state
         return nRet; // Error logged in SetAndApply()
      }
   }

   // Centroids require buffer reallocation and metadata
   if (acqCfgNew_.CentroidsEnabled != acqCfgCur_.CentroidsEnabled)
   {
      configChanged = true;
      bufferResizeRequired = true;
      nRet = prmCentroidsEnabled_->SetAndApply(acqCfgNew_.CentroidsEnabled ? TRUE : FALSE);
      if (nRet != DEVICE_OK)
      {
         acqCfgNew_ = acqCfgCur_; // New settings not accepted, reset it back to previous state
         return nRet; // Error logged in SetAndApply()
      }
      // Update the ROI count
      acqCfgNew_.RoiCount = acqCfgNew_.CentroidsEnabled ? acqCfgNew_.CentroidsCount : 1;
   }
   if (acqCfgNew_.CentroidsRadius != acqCfgCur_.CentroidsRadius)
   {
      configChanged = true;
      bufferResizeRequired = true;
      if ((nRet = prmCentroidsRadius_->SetAndApply(static_cast<uns16>(acqCfgNew_.CentroidsRadius))) != DEVICE_OK)
      {
         acqCfgNew_ = acqCfgCur_; // New settings not accepted, reset it back to previous state
         return nRet; // Error logged in SetAndApply()
      }
   }
   if (acqCfgNew_.CentroidsCount != acqCfgCur_.CentroidsCount)
   {
      configChanged = true;
      bufferResizeRequired = true;
      if ((nRet = prmCentroidsCount_->SetAndApply(static_cast<uns16>(acqCfgNew_.CentroidsCount))) != DEVICE_OK)
      {
         acqCfgNew_ = acqCfgCur_; // New settings not accepted, reset it back to previous state
         return nRet; // Error logged in SetAndApply()
      }
      // Update the generic ROI count so the md_frame structure can be reinitialized if needed
      if (acqCfgCur_.CentroidsEnabled)
         acqCfgNew_.RoiCount = acqCfgNew_.CentroidsCount;
   }

   // Fan speed setpoint
   if (acqCfgNew_.FanSpeedSetpoint != acqCfgCur_.FanSpeedSetpoint)
   {
       configChanged = true;
       nRet = prmFanSpeedSetpoint_->SetAndApply(acqCfgNew_.FanSpeedSetpoint);
       if (nRet != DEVICE_OK)
       {
          acqCfgNew_ = acqCfgCur_; // New settings not accepted, reset it back to previous state
          return nRet; // Error logged in SetAndApply()
       }
   }

   // Debayering algorithm selection
   if (acqCfgNew_.DebayerAlgMaskAuto && acqCfgNew_.ColorProcessingEnabled)
   {
      const int xPos = acqCfgNew_.Roi.SensorRgnX();
      const int yPos = acqCfgNew_.Roi.SensorRgnY();
      acqCfgNew_.DebayerAlgMask = selectDebayerAlgMask(xPos, yPos, camCurrentSpeed_.colorMask);
      // Config changes only if the current and previous mask actually differs (see below)
   }
   if (acqCfgNew_.DebayerAlgMask != acqCfgCur_.DebayerAlgMask)
   {
      configChanged = true;
      debayer_.SetOrderIndex(acqCfgNew_.DebayerAlgMask);
   }
   if (acqCfgNew_.DebayerAlgInterpolation != acqCfgCur_.DebayerAlgInterpolation)
   {
      configChanged = true;
      debayer_.SetAlgorithmIndex(acqCfgNew_.DebayerAlgInterpolation);
   }

   if (bufferResizeRequired)
   {
      sequenceModeReady_ = false;
      singleFrameModeReady_ = false;
   }

   // The new properties have been applied
   acqCfgCur_ = acqCfgNew_;

   // Update the Device/Property browser UI
   if (configChanged)
      nRet = this->GetCoreCallback()->OnPropertiesChanged(this);

   return nRet;
}

/**
* This function reallocates all temporary buffers that might be required
* for the current acquisition. Such buffers are used when we need to do
* additional image processing before the image is pushed to MMCore.
* @throws std::bad_alloc
* @return Error code if buffer reallocation fails
*/
int Universal::reinitProcessingBuffers()
{
#ifdef PVCAM_3_0_12_SUPPORTED
   // In case of metadata-enabled acquisition we may need temporary processing
   // buffer. This one should be allocated only if needed and freed otherwise.
   if (acqCfgNew_.FrameMetadataEnabled && acqCfgNew_.RoiCount > 1)
   {
      // Metadata-enabled frames with single ROI don't need black-filling,
      // we can use the data of the single ROI directly.
      // With more ROIs we need blackfilling into separate buffer
      const size_t imgDataSz = acqCfgCur_.Roi.ImageRgnWidth() * acqCfgCur_.Roi.ImageRgnHeight() * sizeof(uns16);
      if (metaBlackFilledBufSz_ != imgDataSz)
      {
         delete[] metaBlackFilledBuf_;
         metaBlackFilledBuf_ = NULL;
         metaBlackFilledBufSz_ = 0;
         metaBlackFilledBuf_ = new unsigned char[imgDataSz]; // May throw bad_alloc
         metaBlackFilledBufSz_ = imgDataSz;
         // Make sure to blackfill the buffer so it does not contain any
         // residual memory garbage.
         memset(metaBlackFilledBuf_, 0, metaBlackFilledBufSz_);
      }
   }
   else if (metaBlackFilledBuf_)
   {
      delete[] metaBlackFilledBuf_; // Delete the buffer if we don't need it
      metaBlackFilledBuf_ = NULL;
      metaBlackFilledBufSz_ = 0;
   }

   // In case of frame metadata enabled we may need to update the helper
   // structure for decoding.
   if (acqCfgNew_.FrameMetadataEnabled)
   {
      // Allocate the helper structure if needed
      const uns16 roiCount = static_cast<uns16>(acqCfgNew_.RoiCount);
      if (metaFrameStruct_)
      {
         if (metaFrameStruct_->roiCapacity < acqCfgNew_.RoiCount)
         {
            // We need to reallocate the structure for more ROIs
            if (pl_md_release_frame_struct(metaFrameStruct_) != PV_OK)
            {
               LogCamError( __LINE__, "pl_md_release_frame_struct() failed" );
               return DEVICE_ERR;
            }
            if (pl_md_create_frame_struct_cont(&metaFrameStruct_, roiCount) != PV_OK)
            {
               LogCamError( __LINE__, "pl_md_create_frame_struct_cont() failed" );
               return DEVICE_OUT_OF_MEMORY;
            }
         }
      }
      else
      {
         if (pl_md_create_frame_struct_cont(&metaFrameStruct_, roiCount) != PV_OK)
         {
            LogCamError( __LINE__, "pl_md_create_frame_struct_cont() failed" );
            return DEVICE_OUT_OF_MEMORY;
         }
      }
   }
   else if (metaFrameStruct_)
   {
      if (pl_md_release_frame_struct(metaFrameStruct_) != PV_OK)
      {
         LogCamError( __LINE__, "pl_md_release_frame_struct() failed" );
         return DEVICE_ERR;
      }
      metaFrameStruct_ = NULL;
   }
#endif

   // In case of RGB acquisition we need yet another buffer for demosaicing
   if (acqCfgNew_.ColorProcessingEnabled)
   {
      const PvRoi& roi = acqCfgNew_.Roi;
      if (rgbImgBuf_)
      {
         // Reallocate the rgb image buffer only if really needed
         if (rgbImgBuf_->Width() != roi.ImageRgnWidth() || rgbImgBuf_->Height() != roi.ImageRgnHeight())
         {
            delete rgbImgBuf_;
            rgbImgBuf_ = NULL;
            rgbImgBuf_ = new ImgBuffer(roi.ImageRgnWidth(), roi.ImageRgnHeight(), 4); // May throw bad_alloc
         }
      }
      else
      {
         rgbImgBuf_ = new ImgBuffer(roi.ImageRgnWidth(), roi.ImageRgnHeight(), 4); // May throw bad_alloc
      }
   }
   else if (rgbImgBuf_)
   {
      delete rgbImgBuf_; // Delete the RGB buffer if we don't need it
      rgbImgBuf_ = NULL;
   }

   return DEVICE_OK;
}

/**
* Prepares a raw PVCAM frame buffer for use in MM::Core
* @param [OUT] pOutBuf A pointer to the post processed image buffer. This will point
*              to one of the internal buffers that were already allocated in reinitProcessingBuffers()
* @param [IN] pInBuf A raw PVCAM image buffer
* @param [IN] inBufSz Size of the PVCAM image buffer in bytes
* @return MM error code
*/
int Universal::postProcessSingleFrame(unsigned char** pOutBuf, unsigned char* pInBuf, size_t inBufSz)
{
   // wait for data or error
   unsigned char* pixBuffer = pInBuf;

#ifdef PVCAM_3_0_12_SUPPORTED
   if (acqCfgCur_.FrameMetadataEnabled)
   {
#if _DEBUG
      md_frame_header* pFrameHdrTmp = (md_frame_header*)pInBuf;
      md_frame_roi_header* pRoiHdrTmp = (md_frame_roi_header*)(pInBuf + sizeof(md_frame_header));
#endif
      if (pl_md_frame_decode(metaFrameStruct_, pInBuf, (uns32)inBufSz) != PV_OK)
      {
         LogCamError(__LINE__, "Unable to decode the metadata-enabled frame");
         return ERR_BUFFER_PROCESSING_FAILED;
      }
#if _DEBUG
      for (int i = 0; i < metaFrameStruct_->roiCount; ++i)
      {
         md_frame_roi pRoi = metaFrameStruct_->roiArray[i];
         md_frame_roi_header* pRoiHdr = pRoi.header;
      }
      
#endif
      if (acqCfgCur_.RoiCount > 1)
      {
         // If there are more ROIs we need to black-fill...
         const PvRoi& roi = acqCfgCur_.Roi;
         const uns16 offX = metaFrameStruct_->impliedRoi.s1 - roi.SensorRgnX();
         const uns16 offY = metaFrameStruct_->impliedRoi.p1 - roi.SensorRgnY();
         const uns16 recW = roi.ImageRgnWidth();
         const uns16 recH = roi.ImageRgnHeight();
         // If we are running centroids we should blackfill the destination frame
         // before every use (to erase all previous centroids) because the new frame
         // may contain centroids on different positions.
         if (acqCfgCur_.CentroidsEnabled)
         {
            memset(metaBlackFilledBuf_, 0, metaBlackFilledBufSz_);
         }
         if (pl_md_frame_recompose(metaBlackFilledBuf_, offX, offY, recW, recH, metaFrameStruct_) != PV_OK)
         {
            LogCamError(__LINE__, "Unable to recompose the metadata-enabled frame");
            return ERR_BUFFER_PROCESSING_FAILED;
         }
         else
         {
            pixBuffer = metaBlackFilledBuf_;
         }
      }
      else
      {
         // In case of a single ROI we can use the ROI directly
         pixBuffer = (unsigned char*)metaFrameStruct_->roiArray[0].data;
      }
   }
   else
   {
      pixBuffer = pInBuf;
   }
#endif

   if (acqCfgCur_.ColorProcessingEnabled)
   {
      // debayer the image and convert to color
      RGBscales rgbScales = {redScale_, greenScale_, blueScale_};
      debayer_.SetRGBScales(rgbScales);
      //debayer_.Process(colorImg_, img_, (unsigned)camCurrentSpeed_.bitDepth);
      debayer_.Process(*rgbImgBuf_, (unsigned short*)pixBuffer,
         rgbImgBuf_->Width(), rgbImgBuf_->Height(), (unsigned)camCurrentSpeed_.bitDepth);
      pixBuffer = rgbImgBuf_->GetPixelsRW();
   }

   *pOutBuf = pixBuffer;
   return DEVICE_OK;
}

/**
* This function returns the correct exposure mode and exposure value to be used in both
* pl_exp_setup_seq and pl_exp_setup_cont
*/
int Universal::GetPvExposureSettings( int16& pvExposeOutMode, uns32& pvExposureValue )
{
    int nRet = DEVICE_OK;

    // Prepare the exposure mode
    int16 trigModeValue = (int16)prmTriggerMode_->Current();
    // Some cameras like the OptiMos allow special expose-out modes.
    int16 eposeOutModeValue = 0;
    if ( prmExposeOutMode_ && prmExposeOutMode_->IsAvailable() )
    {
        eposeOutModeValue = (int16)prmExposeOutMode_->Current();
    }

    pvExposeOutMode = (trigModeValue | eposeOutModeValue);

    // Prepare the exposure value

    uns16 expRes = EXP_RES_ONE_MILLISEC;

    // If the exposure is smaller than 60 milliseconds (MM works in milliseconds but uses float type)
    // we switch the camera to microseconds so user can type 59.5 and we send 59500 to PVCAM.
    if (exposure_ < 60 && microsecResSupported_)
    {
        expRes = EXP_RES_ONE_MICROSEC;
        pvExposureValue = (uns32)(1000*exposure_);
    }
    else
    {
        expRes = EXP_RES_ONE_MILLISEC;
        pvExposureValue = (uns32)exposure_;
    }

    g_pvcamLock.Lock();
    // If the PARAM_EXP_RES_INDEX is not available, we use the exposure number as it is.
    if ( prmExpResIndex_->IsAvailable() )
    {
        nRet = prmExpResIndex_->Set( expRes );
        if (nRet == DEVICE_OK)
            nRet = prmExpResIndex_->Apply();
    }
    g_pvcamLock.Unlock();

    return nRet;
}

/**
* This method is used to estimate how long it might take to read out one frame.
* The calculation is very inaccurate, it is only used when calculating acquisition timeout.
*/
unsigned int Universal::EstimateMaxReadoutTimeMs() const
{
    const unsigned int pixCount = GetImageHeight() * GetImageWidth();
    // Sensor read time rough estimation, pixTime = nano-seconds/pixel
    const unsigned int readTimeEstMs = ((camCurrentSpeed_.pixTime * (unsigned long long)pixCount) / 1000000);
    // Transfer over the wire. Let's just assume the slowest interface does 4MB/s
    const unsigned int transferSpeedKBs = 4000; 
    const unsigned int transferTimeMaxMs = (pixCount*GetImageBytesPerPixel()) / transferSpeedKBs;
    return readTimeEstMs + transferTimeMaxMs;
}


#ifdef PVCAM_SMART_STREAMING_SUPPORTED
int Universal::SendSmartStreamingToCamera()
{
    START_METHOD("Universal::SendSmartStreamingToCamera");

    int nRet = DEVICE_OK;
    double greatestSmartExp;
    uns16 expRes = EXP_RES_ONE_MILLISEC;

    // If the exposure is smaller than 60 milliseconds (MM works in milliseconds but uses float type)
    // we switch the camera to microseconds so user can type 59.5 and we send 59500 to PVCAM.
    
    // find the greatest SMART streaming exposure so a decision can be made whether to use 
    // microsecond or milisecond exposure resolution
    if (prmSmartStreamingEnabled_->Current() == TRUE)
    {
        greatestSmartExp = smartStreamValuesDouble_[0];
        for (int i = 1; i < smartStreamEntries_; i++)
        {
            if (smartStreamValuesDouble_[i] > greatestSmartExp)
            {
                greatestSmartExp = smartStreamValuesDouble_[i];
            }
        } 
        
        // the SMART streaming exposure values sent to cameras are uns32 while internally we need
        // to be working with doubles
        // allocate and populate regular smart_stream_type structure with values received from the UI
        smart_stream_type smartStreamInts = prmSmartStreamingValues_->Current();
        smartStreamInts.entries = smartStreamEntries_;

        // if all exposures are shorter than 60ms and camera supports microsecond resolution
        // just convert doubles to uns32 exposures and send values to camera in microseconds
        if (greatestSmartExp < 60000 && microsecResSupported_)
        {
            expRes = EXP_RES_ONE_MICROSEC;
            for (int i = 0; i < smartStreamEntries_; i++)
            {
                smartStreamInts.params[i] = (uns32)(smartStreamValuesDouble_[i]);
            }
        }
        // if either one exposure is longer than 60ms or microsecond resolution is not supported
        // convert the exposures to miliseconds and uns32
        // in this case all exposures shorter than 1ms will be reduced to 0ms
        else
        {
            expRes = EXP_RES_ONE_MILLISEC;
            for (int i = 0; i < smartStreamEntries_; i++)
            {
                smartStreamInts.params[i] = (uns32)(smartStreamValuesDouble_[i] / 1000.0);
            }
            
        }
        g_pvcamLock.Lock();

        // send the SMART streaming structure to camera
        prmSmartStreamingValues_->Set(smartStreamInts);
        prmSmartStreamingValues_->Apply();

        // If the PARAM_EXP_RES_INDEX is not available, we use the exposure number as it is.
        if ( prmExpResIndex_->IsAvailable() )
        {
            nRet = prmExpResIndex_->Set( expRes );
            if (nRet == DEVICE_OK)
                nRet = prmExpResIndex_->Apply();
        }
        g_pvcamLock.Unlock();
    }

  return 0;
}
#endif

int Universal::ResizeImageBufferContinuous()
{
   START_METHOD("Universal::ResizeImageBufferContinuous");

   int nRet = DEVICE_ERR;

   try
   {
      uns32 frameSize = 0;
      int16 pvExposureMode = 0;
      uns32 pvExposure = 0;
      nRet = GetPvExposureSettings( pvExposureMode, pvExposure );
      if ( nRet != DEVICE_OK )
          return nRet;
#ifdef PVCAM_SMART_STREAMING_SUPPORTED
      if (prmSmartStreamingEnabled_->IsAvailable() && (prmSmartStreamingEnabled_->Current() == TRUE) )
      {
          SendSmartStreamingToCamera();
          pvExposure = 10; //make sure non-zero exposure time is sent in setup in Smart Streaming mode
      }
#endif
      g_pvcamLock.Lock();
      rgn_type roi = acqCfgCur_.Roi.ToRgnType();
      if (!pl_exp_setup_cont(hPVCAM_, 1, &roi, pvExposureMode, pvExposure, &frameSize, CIRC_OVERWRITE)) 
      {
         g_pvcamLock.Unlock();

         nRet = LogCamError(__LINE__, "pl_exp_setup_seq failed");
         SetBinning(1); // The error might have been caused by not supported BIN or ROI, so do a reset
         this->GetCoreCallback()->OnPropertiesChanged(this); // Notify the MM UI to update the BIN and ROI
         SetErrorText( nRet, "Failed to setup the acquisition" );
         return nRet;
      }
      g_pvcamLock.Unlock();

      nRet = reinitProcessingBuffers();
      if (nRet != DEVICE_OK)
         return nRet; // Message logged in the failing method

      // set up a circular buffer for specified number of frames
      if (circBufSizeAuto_)
      {
         if (prmFrameBufSize_ && prmFrameBufSize_->IsAvailable())
         {
            // The PARAM_FRAME_BUFFER_SIZE becomes valid after setup_cont(). Only newer
            // PVCAM (3.0.12+) supports this parameter.
            prmFrameBufSize_->Update(); // Read the actual value(s)
            const ulong64 pvcamRecommendedSize = prmFrameBufSize_->Default();
            circBufFrameCount_ = static_cast<int>(pvcamRecommendedSize / frameSize);
         }
         else
         {
            // If not supported (users have old PVCAM), we will estimate it ourselves
            // Use the maximum size in MB and eventually cap it by max frame count
            circBufFrameCount_ = static_cast<int>(CIRC_BUF_SIZE_MAX_AUTO / frameSize);
            circBufFrameCount_ = (std::min)(circBufFrameCount_, CIRC_BUF_FRAME_CNT_MAX);
         }
      }

      // This will update the CircularBufferFrameCount property in the Device/Property
      // browser. We need to call this because we need to reflect the change in circBufFrameCount_.
      // Also, if we are in manual buffer size mode and binning/ROI is changed during live the
      // circular buffer limits may get adjusted and this needs to be reflected as well.
      nRet = updateCircBufRange(frameSize);
      if (nRet != DEVICE_OK)
         return nRet;

      const ulong64 bufferSize = circBufFrameCount_ * static_cast<ulong64>(frameSize);

      // PVCAM API does not support buffers larger that 4GB
      if (bufferSize > 0xFFFFFFFF)
          return LogMMError(ERR_BUFFER_TOO_LARGE, __LINE__);

      // In manual mode we return an error if the buffer size is over the limit. Although
      // we dynamically update the slider range the check here is needed because in some
      // corner cases (like switching binning and not clicking the "Refresh" button) the
      // UI temporarily allows the user to set the incorrect value.
      if (!circBufSizeAuto_ && bufferSize > CIRC_BUF_SIZE_MAX_USER)
          return LogMMError(ERR_BUFFER_TOO_LARGE, __LINE__);

      circBuf_.Resize(frameSize, circBufFrameCount_);

      // Set the queue size to slightly less than the CB size to avoid PVCAM overwritting
      // the oldest frame. This way we start throwing old frames away a little earlier.
      notificationThd_->SetQueueCapacity(static_cast<int>(circBufFrameCount_ * 0.7) + 1);

      nRet = DEVICE_OK;
   }
   catch( const std::bad_alloc& e )
   {
       nRet = DEVICE_OUT_OF_MEMORY;
       LogMessage( e.what() );
   }
   catch( const std::exception& e)
   {
       nRet = DEVICE_ERR;
       LogMessage( e.what() );
   }
   catch(...)
   {
       nRet = DEVICE_ERR;
       LogMessage("Unknown exception in ResizeImageBufferContinuous", false);
   }

   singleFrameModeReady_ = false;
   LogMessage("ResizeImageBufferContinuous singleFrameModeReady_=false", true);
   return nRet;

}

/**
 * This function calls pl_exp_setup_seq with the correct parameters, to set the camera
 * in a mode in which single images can be taken
 *
 * Timing data:      On a Mac Pro OS X 10.5 with CoolsnapEZ, this functions takes
 *                   takes 245 msec.
 */
int Universal::ResizeImageBufferSingle()
{
   START_METHOD("Universal::ResizeImageBufferSingle");

   int nRet = DEVICE_ERR;

   try
   {
      uns32 frameSize = 0;
      int16 pvExposureMode = 0;
      uns32 pvExposure = 0;
      nRet = GetPvExposureSettings( pvExposureMode, pvExposure );
      if ( nRet != DEVICE_OK )
          return nRet;

      g_pvcamLock.Lock();
#ifdef PVCAM_SMART_STREAMING_SUPPORTED 
      // in the single Snap mode turn off the SMART streaming so the exposure used is the one in the exposure field,
      // not the first one from the SMART streaming list
      // SMART streaming will be returned to its current state in SnapImage() function 
      // after the current frame is returned
      if (prmSmartStreamingEnabled_->Current() == TRUE)
      {
          ssWasOn_ = true;
          SetProperty(g_Keyword_SmartStreamingEnable, g_Keyword_No);
      }
#endif
      rgn_type roi = acqCfgCur_.Roi.ToRgnType();
      if (!pl_exp_setup_seq(hPVCAM_, 1, 1, &roi, pvExposureMode, pvExposure, &frameSize))
      {
         g_pvcamLock.Unlock();
         nRet = LogCamError(__LINE__, "pl_exp_setup_seq failed");
         SetBinning(1); // The error might have been caused by not supported BIN or ROI, so do a reset
         this->GetCoreCallback()->OnPropertiesChanged(this); // Notify the MM UI to update the BIN and ROI
         SetErrorText( nRet, "Failed to setup the acquisition" );
         return nRet;
      }
      g_pvcamLock.Unlock();

      // Reallocate the single frame buffer if needed. This is the raw buffer
      // that is sent to PVCAM in start_seq(). We always need this buffer.
      if (singleFrameBufRawSz_ != frameSize)
      {
         delete singleFrameBufRaw_;
         singleFrameBufRaw_ = NULL;
         singleFrameBufRawSz_ = 0;

         // TODO: Temporary hack to avoid heap corruption when unaligned size is returned
         const unsigned int align = 64;
         if (frameSize % align != 0)
         {
            const unsigned int alignedSize = ((frameSize / align) + 1) * align;
            singleFrameBufRaw_ = new unsigned char[alignedSize]; // May throw bad_alloc
         }
         else
         {
            singleFrameBufRaw_ = new unsigned char[frameSize]; // May throw bad_alloc
         }
         singleFrameBufRawSz_ = frameSize;
      }

      // Reinit other image post-processing buffers if needed. These buffers are
      // shared for both single snap and live acquisition modes.
      nRet = reinitProcessingBuffers();
      if (nRet != DEVICE_OK)
         return nRet; // Message logged in the failing method

      // Reflect all changes that occured in this function in the Device/Property window
      this->GetCoreCallback()->OnPropertiesChanged(this);
   }
   catch (const std::bad_alloc& e)
   {
       nRet = DEVICE_OUT_OF_MEMORY;
       LogMessage( e.what() );
   }
   catch (const std::exception& e)
   {
       nRet = DEVICE_ERR;
       LogMessage( e.what() );
   }
   catch(...)
   {
      nRet = DEVICE_ERR;
      LogMessage("Caught error in ResizeImageBufferSingle", false);
   }

   return nRet;
}


///////////////////////////////////////////////////////////////////////////////
// Continuous acquisition
//

#ifndef linux
/*
* Overrides a virtual function from the CCameraBase class
* Do actual capture
* Called from the acquisition thread function
*/
int Universal::PollingThreadRun(void)
{
   START_METHOD(">>>Universal::ThreadRun");

   int16   status;
   uns32   byteCnt;
   uns32   bufferCnt;
   int     ret = DEVICE_ERR;
   rs_bool retVal = TRUE;
   char dbgBuf[128]; // Debug log buffer
   pollingThd_->setStop(false); // make sure this thread's status is updated properly.

   try 
   {
      do
      {
         const double estReadTimeSec = EstimateMaxReadoutTimeMs() / 1000.0f;
         // make the time out 2 seconds (default trigger timeout) plus twice the exposure
         MM::MMTime timeout((long)(triggerTimeout_ + estReadTimeSec + 2*GetExposure() * 0.001), (long)(2*GetExposure() * 1000));
         MM::MMTime startTime = GetCurrentMMTime();
         MM::MMTime elapsed(0,0);

         do
         {
            CDeviceUtils::SleepMs(1);
            g_pvcamLock.Lock();
            retVal = pl_exp_check_cont_status(hPVCAM_, &status, &byteCnt, &bufferCnt);
            g_pvcamLock.Unlock();
            elapsed = GetCurrentMMTime()  - startTime;
         } while (retVal && (status == EXPOSURE_IN_PROGRESS || status == READOUT_NOT_ACTIVE) && elapsed < timeout && !pollingThd_->getStop());

         if ( pollingThd_->getStop() ) {
            LogMessage( "Stop called: Breaking the loop" , true);
            break;
         }

         while (retVal && (status == READOUT_IN_PROGRESS) && elapsed < timeout && !pollingThd_->getStop())
         {
            CDeviceUtils::SleepMs(1);
            g_pvcamLock.Lock();
            retVal = pl_exp_check_cont_status(hPVCAM_, &status, &byteCnt, &bufferCnt);
            g_pvcamLock.Unlock();
            elapsed = GetCurrentMMTime()  - startTime;
         };
  
         if ( pollingThd_->getStop() ) {
            LogMessage( "Stop called: Breaking the loop" , true);
            break;
         }

         if (retVal == TRUE && elapsed < timeout && status != READOUT_FAILED)
         {
            // Because we could miss the FRAME_AVAILABLE and the camera could of gone back to EXPOSURE_IN_PROGRESS and so on depending
            // on how long we could of been stalled in this thread we only check for READOUT_FAILED and assume that because we got here
            // we have one or more frames ready.
            ret = FrameAcquired();
         }
         else
         {
            break;
         } 
      }
      while (DEVICE_OK == ret && !pollingThd_->getStop() && imagesInserted_ < imagesToAcquire_);

      sprintf( dbgBuf, "ACQ LOOP FINISHED: thdGetStop:%u, ret:%u, retVal:%u, imagesInserted_: %lu, imagesToAcquire_: %lu", \
         pollingThd_->getStop(), ret, retVal, imagesInserted_, imagesToAcquire_);
      LogMMMessage( __LINE__, dbgBuf );

      if (imagesInserted_ >= imagesToAcquire_)
         imagesInserted_ = 0;
      PollingThreadExiting();
      pollingThd_->setStop(true);
     
      START_METHOD("<<<Universal::ThreadRun");
      return ret;

   }
   catch(...)
   {
      LogMessage(g_Msg_EXCEPTION_IN_THREAD, false);
      OnThreadExiting();
      pollingThd_->setStop(true);
      return ret;
   }

}

/**
 * Micromanager calls the "live" acquisition a "sequence"
 *  don't get this confused with a PVCAM sequence acquisition, it's actually circular buffer mode
 */
int Universal::PrepareSequenceAcqusition()
{
   START_METHOD("Universal::PrepareSequenceAcqusition");

   if (IsCapturing())
   {
      return ERR_BUSY_ACQUIRING;
   }
   else if (!sequenceModeReady_)
   {
      // reconfigure anything that has to do with pl_exp_setup_cont
      int nRet = ResizeImageBufferContinuous();
      if ( nRet != DEVICE_OK )
      {
         return nRet;
      }
      GetCoreCallback()->InitializeImageBuffer( 1, 1, GetImageWidth(), GetImageHeight(), GetImageBytesPerPixel() );
      GetCoreCallback()->PrepareForAcq(this);
      sequenceModeReady_ = true;
   }

   return DEVICE_OK;
}

/**
 * Micromanager calls the "live" acquisition a "sequence"
 *  don't get this confused with a PVCAM sequence acquisition, it's actually circular buffer mode
 */
int Universal::StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow)
{
   START_METHOD("Universal::StartSequenceAcquisition");

   int ret = DEVICE_OK;

   ret = applyAcqConfig();
   if (ret != DEVICE_OK)
      return ret;

   ret = PrepareSequenceAcqusition();
   if (ret != DEVICE_OK)
      return ret;

   stopOnOverflow_  = stopOnOverflow;
   imagesToAcquire_ = numImages;
   imagesInserted_  = 0;
   imagesAcquired_  = 0;
   imagesRecovered_ = 0;
   lastPvFrameNr_   = 0;

   // Cache the current device label so we don't have to copy it for every frame
   GetLabel(deviceLabel_); 

   MM::MMTime start = GetCurrentMMTime();
   g_pvcamLock.Lock();
   if (!pl_exp_start_cont(hPVCAM_, circBuf_.Data(), static_cast<uns32>(circBuf_.Size())))
   {
      g_pvcamLock.Unlock();
      int pvcamErr = LogCamError(__LINE__, "pl_exp_start_cont");
      ResizeImageBufferSingle();
      return pvcamErr;
   }
   g_pvcamLock.Unlock();
   startTime_ = GetCurrentMMTime();

   MM::MMTime end = GetCurrentMMTime();
   LogTimeDiff(start, end, true);

   // initially start with the exposure time as the actual interval estimate
   SetProperty(MM::g_Keyword_ActualInterval_ms, CDeviceUtils::ConvertToString(exposure_)); 

   if ( !isUsingCallbacks_ )
   {
      pollingThd_->Start();
   }
   isAcquiring_ = true;

   char label[MM::MaxStrLength];
   GetLabel(label);
   ostringstream os;
   os << "Started sequence on " << label << ", at " << startTime_.serialize() << ", with " << numImages << " and " << interval_ms << " ms" << endl;
   LogMessage(os.str().c_str());

   return DEVICE_OK;
}

/**
 * Micromanager calls the "live" acquisition a "sequence"
 *  don't get this confused with a PVCAM sequence acquisition, it's actually circular buffer mode
 */
int Universal::StopSequenceAcquisition()
{
   START_METHOD("Universal::StopSequenceAcquisition");
   // call function of the base class, which does useful work
   int nRet = DEVICE_OK;

   // removed redundant calls to pl_exp_stop_cont &
   //  pl_exp_finish_seq because they get called automatically when the thread exits.
   if(IsCapturing())
   {
      if ( isUsingCallbacks_ )
      {
         g_pvcamLock.Lock();
         if (!pl_exp_stop_cont( hPVCAM_, CCS_CLEAR ))
         {
            nRet = DEVICE_ERR;
            LogCamError( __LINE__, "pl_exp_stop_cont failed" );
         }
         g_pvcamLock.Unlock();
         sequenceModeReady_ = false;
         // Inform the core that the acquisition has finished
         // (this also closes the shutter if used)
         GetCoreCallback()->AcqFinished(this, nRet );
      }
      else
      {
         pollingThd_->setStop(true);
         pollingThd_->wait();
      }
      isAcquiring_ = false;
   }

   // LW: Give the camera some time to stop acquiring. This reduces occasional
   //     crashes/hangs when frequently starting/stopping with some fast cameras.
   CDeviceUtils::SleepMs( 50 );

   return nRet;
}

void Universal::PollingThreadExiting() throw ()
{
   try
   {
      g_pvcamLock.Lock();
      if (!pl_exp_stop_cont(hPVCAM_, CCS_HALT)) 
         LogCamError(__LINE__, "pl_exp_stop_cont");
      if (!pl_exp_finish_seq(hPVCAM_, circBuf_.Data(), 0))
         LogCamError(__LINE__, "pl_exp_finish_seq");
      g_pvcamLock.Unlock();

      sequenceModeReady_ = false;
      isAcquiring_       = false;

      LogMessage(g_Msg_SEQUENCE_ACQUISITION_THREAD_EXITING);
      GetCoreCallback()?GetCoreCallback()->AcqFinished(this,0):DEVICE_OK;
   }
   catch (...)
   {
      LogMMMessage(__LINE__, g_Msg_EXCEPTION_IN_ON_THREAD_EXITING);
   }
}

#endif

/**
* This method is called from the static PVCAM callback or polling thread.
* The method should finish as fast as possible to avoid blocking the PVCAM.
* If the execution of this method takes longer than frame readout + exposure,
* the FrameAcquired for the next frame may not be called.
*/
int Universal::FrameAcquired()
{
   START_METHOD("Universal::FrameDone");
   MMThreadGuard scopeLock(&g_pvcamLock);

   rs_bool bRet;
   void_ptr pCurrFramePtr;
   PvFrameInfo currFrameNfo;
   currFrameNfo.SetTimestampMsec(GetCurrentMMTime().getMsec());

#ifdef PVCAM_FRAME_INFO_SUPPORTED
   bRet = pl_exp_get_latest_frame_ex(hPVCAM_, &pCurrFramePtr, pFrameInfo_ ); 
   if (bRet)
   {
      currFrameNfo.SetPvHCam(pFrameInfo_->hCam);
      currFrameNfo.SetPvFrameNr(pFrameInfo_->FrameNr);
      currFrameNfo.SetPvReadoutTime(pFrameInfo_->ReadoutTime);
      currFrameNfo.SetPvTimeStamp(pFrameInfo_->TimeStamp);
      currFrameNfo.SetPvTimeStampBOF(pFrameInfo_->TimeStampBOF);

      const int currFrameNr = currFrameNfo.PvFrameNr();
      const int prevFrameNr = lastPvFrameNr_;

      if (currFrameNr == prevFrameNr)
      {
         // Received a duplicate callback? This seems like a bug in PVCAM,
         // it occurs for optiMos at high frame rates. For now just silently ignore it,
         // because the next one will correctly arrive right after that.
         return DEVICE_OK;
      }

      // Check whether we haven't missed a callback
      if (currFrameNr > prevFrameNr + 1)
      {
         const int missedCbCount = currFrameNr - prevFrameNr - 1;
         // We cannot perform frame recovery if our notification queue is full.
         // This means that the circular buffer has overrun because the application
         // cannot process the frames fast enough. Increasing the CB may help.
         if (missedCbCount >= notificationThd_->Capacity())
         {
            // TODO: Should we somewhat return an error that our circular
            // buffer has overrun? For now the behavior is the same as with
            // previous code - we simply start skipping frames.
         }
         else if (circBufFrameRecoveryEnabled_)
         {
            // Get the last known frame index in the CB
            const int lastFrIdx = circBuf_.LatestFrameIndex();
            if (lastFrIdx < 0)
            {
                // We cannot perform frame recovery because we don't have a frame in the buffer yet
                // so we cannot recover the metadata. This mostly happens with Polling acquisition
                // because it can easily miss several frames when starting acquisition.
            }
            else
            {
                const PvFrameInfo& lastFrNfo = circBuf_.FrameInfo(lastFrIdx);

                // We need to re-create the FRAME_INFOs by averaging the known frame infos.
                // This is not really nice way of fixing things but since the camera is running on
                // constant rate the recovered data will be accurate enough. Plus, we mark the frame as recovered
                // so the user will be aware of this.
                const int recReadoutTm = static_cast<int>((lastFrNfo.PvReadoutTime() + currFrameNfo.PvReadoutTime()) / 2);
                const long long lastPvTimestampBOF   = lastFrNfo.PvTimeStampBOF();
                const long long lastPvTimestampEOF   = lastFrNfo.PvTimeStamp();
                const double    lastApTimestampMsec  = lastFrNfo.TimeStampMsec();
                const double div = missedCbCount + 1;
                const double avgBofDiff = (currFrameNfo.PvTimeStampBOF()  - lastPvTimestampBOF) / div;
                const double avgEofDiff = (currFrameNfo.PvTimeStamp()  - lastPvTimestampEOF) / div;
                const double avgAppDiff = (currFrameNfo.TimeStampMsec() - lastApTimestampMsec) / div;

                for (int i = 0; i < missedCbCount; ++i)
                {
                   // Get the index of the next frame in the CB. The data for this frame has been
                   // correctly delivered by the driver, however since we missed a callback we also
                   // missed the FRAME_INFO. Thus we need to recreate the FRAME_INFO ourselves.
                   // This can be removed once PVCAM implements better way of retrieving particular frames.
                   const unsigned int nextFrIdx = (lastFrIdx + i + 1) % circBuf_.Capacity();

                   // Retrieve the data pointer for the skipped callback
                   void* pRecFrameData = circBuf_.FrameData(nextFrIdx);

                   // Re-create the FRAME_INFO
                   const short int recHCam = lastFrNfo.PvHCam();
                   const int       recFrameNr = prevFrameNr + i + 1;
                   const long long recTimeStampBOF = static_cast<long long>(lastPvTimestampBOF + ((i + 1)*avgBofDiff));
                   const long long recTimeStampEOF = static_cast<long long>(lastPvTimestampEOF + ((i + 1)*avgEofDiff));
                   const double    recAppTimeStampEOF = lastApTimestampMsec + ((i + 1)*avgAppDiff);

                   PvFrameInfo recFrNfo;
                   recFrNfo.SetPvHCam(recHCam);
                   recFrNfo.SetPvFrameNr(recFrameNr);
                   recFrNfo.SetPvReadoutTime(recReadoutTm);
                   recFrNfo.SetPvTimeStamp(recTimeStampEOF);
                   recFrNfo.SetPvTimeStampBOF(recTimeStampBOF);
                   recFrNfo.SetTimestampMsec(recAppTimeStampEOF);
                   recFrNfo.SetRecovered(true);

                   // Notify our CB wrapper that a new frame has "arrived", it will increase
                   // its internal counters and indexes.
                   circBuf_.ReportFrameArrived(recFrNfo, pRecFrameData);

                   // Prepare the notification and push the new frame + info to our queue, 
                   // the same way as the frame would arrive correctly with a callback.
                   NotificationEntry recNotif(pRecFrameData, recFrNfo);
                   notificationThd_->PushNotification(recNotif);
                   imagesAcquired_++;
                   imagesRecovered_++;
                }
             }
         }
         else
         {  // Frame recovery is disabled
            // TODO: Again, should we report an error?
         }
      }
      lastPvFrameNr_ = currFrameNr;
   }
#else
   // FRAME_INFO is not supported so we cannot do much. Just retrieve the frame pointer.
   bRet = pl_exp_get_latest_frame(hPVCAM_, &pCurrFramePtr ); 
#endif // PVCAM_FRAME_INFO_SUPPORTED

   if ( bRet != PV_OK )
   {
      pl_exp_abort( hPVCAM_, CCS_CLEAR );
      LogCamError(__LINE__, "pl_exp_get_latest_frame");
      return DEVICE_ERR;
   }

   imagesAcquired_++; // A new frame has been successfully retrieved from the camera

   // The FrameDone() is also called for SnapImage() when using callbacks, so we have to
   // check. In case of SnapImage the img_ already contains the data (since its passed
   // to pl_start_seq() and no PushImage is done - the single image is retrieved with GetImageBuffer()
   if ( !snappingSingleFrame_ )
   {
      circBuf_.ReportFrameArrived(currFrameNfo, pCurrFramePtr);
      NotificationEntry notif(pCurrFramePtr, currFrameNfo);
      notificationThd_->PushNotification( notif );
   }
   else
   {
      // Single snap: just increase the number of actually acquired frames.
      imagesInserted_++;
   }

   return DEVICE_OK;
}

int Universal::PushImageToMmCore(const unsigned char* pPixBuffer, Metadata* pMd )
{
   START_METHOD("Universal::PushImageToMmCore");

   int nRet = DEVICE_ERR;
   MM::Core* pCore = GetCoreCallback();
   // This method inserts a new image into the circular buffer (residing in MMCore)
   nRet = pCore->InsertMultiChannel(this,
      pPixBuffer,
      1,
      GetImageWidth(),
      GetImageHeight(),
      GetImageBytesPerPixel(),
#ifdef _DEBUG
      NULL); // Inserting the md causes crash in debug builds
#else
      pMd);
#endif
   if (!stopOnOverflow_ && nRet == DEVICE_BUFFER_OVERFLOW)
   {
      // do not stop on overflow - just reset the buffer
      pCore->ClearImageBuffer(this);
      nRet = pCore->InsertMultiChannel(this,
         pPixBuffer,
         1,
         GetImageWidth(),
         GetImageHeight(),
         GetImageBytesPerPixel(),
#ifdef _DEBUG
         NULL); // Inserting the md causes crash in debug builds
#else
         pMd);
#endif
   }

   return nRet;
}


int Universal::ProcessNotification( const NotificationEntry& entry )
{
   // Ignore inserts if we already have all images inserted.
   // This may happen if the notification queue still contains some acquired frames
   // due to excesssive buffering
   if ( imagesInserted_ >= imagesToAcquire_ )
       return DEVICE_OK;

   int ret = DEVICE_ERR;

   const PvFrameInfo frameNfo = entry.FrameMetadata();

   // Build the metadata
   Metadata md;
   md.PutImageTag("Camera", deviceLabel_);
   md.PutImageTag("TimeStampMsec", CDeviceUtils::ConvertToString(frameNfo.TimeStampMsec()));

#ifdef PVCAM_FRAME_INFO_SUPPORTED
   md.PutImageTag<int32>( "PVCAM-CameraHandle",  frameNfo.PvHCam() );
   md.PutImageTag<int32>( "PVCAM-FrameNr",       frameNfo.PvFrameNr() );
   md.PutImageTag<int32>( "PVCAM-ReadoutTime",   frameNfo.PvReadoutTime() );
   md.PutImageTag<long64>( "PVCAM-TimeStamp",    frameNfo.PvTimeStamp() );
   md.PutImageTag<long64>( "PVCAM-TimeStampBOF", frameNfo.PvTimeStampBOF() );
   if (circBufFrameRecoveryEnabled_)
   {
      md.PutImageTag<bool>( "PVCAM-FrameRecovered", frameNfo.IsRecovered() );
      md.PutImageTag<int32>( "PVCAM-FramesRecoveredTotal", imagesRecovered_ );
   }
#endif

   const double startTimeMsec   = startTime_.getMsec();
   const double elapsedTimeMsec = frameNfo.TimeStampMsec() - startTimeMsec;

   // The start time of the acquisition
   md.PutTag(MM::g_Keyword_Metadata_StartTime, deviceLabel_, CDeviceUtils::ConvertToString(startTimeMsec));

   // The time elapsed since start of the acquisition until current frame readout
   // Now added by MM automatically, no need to do it here.
   // md.PutTag(MM::g_Keyword_Elapsed_Time_ms, deviceLabel_, CDeviceUtils::ConvertToString(elapsedTimeMsec));

   const double actualInterval = elapsedTimeMsec / imagesInserted_;
   SetProperty(MM::g_Keyword_ActualInterval_ms, CDeviceUtils::ConvertToString(actualInterval)); 

   unsigned char* pOutBuf = NULL;
   ret = postProcessSingleFrame(&pOutBuf, (unsigned char*)entry.FrameData(), circBuf_.FrameSize());
   if (ret != DEVICE_OK)
   {
       StopSequenceAcquisition();
       // TODO: Display an error message?
       return ret;
   }

#ifdef PVCAM_3_0_12_SUPPORTED
   // The post-processing done above also decodes the frame metadata if supported
   if (acqCfgCur_.FrameMetadataEnabled)
   {
       // FMD stands for Frame-MetaData, we should somehow distinguish the embedded
       // metadata from other metadata and keep them grouped or close together.
       const md_frame_header* fHdr = metaFrameStruct_->header;
       md.PutImageTag<uns16>( "PVCAM-FMD-BitDepth", fHdr->bitDepth); // Need to use uns16 because uns8 is displayed as char
       const char* cKeywordColorMask = "PVCAM-FMD-ColorMask";
       switch (fHdr->colorMask)
       {
       case COLOR_NONE:
           md.PutImageTag(cKeywordColorMask, "None");
           break;
       case COLOR_RGGB:
           md.PutImageTag(cKeywordColorMask, "RGGB");
           break;
       case COLOR_GRBG:
           md.PutImageTag(cKeywordColorMask, "GRBG");
           break;
       case COLOR_GBRG:
           md.PutImageTag(cKeywordColorMask, "GBRG");
           break;
       case COLOR_BGGR:
           md.PutImageTag(cKeywordColorMask, "BGGR");
           break;
       default:
           md.PutImageTag(cKeywordColorMask, "Unknown");
           break;
       }
       // Selected metadata from the frame header
       md.PutImageTag<ulong64>( "PVCAM-FMD-ExposureTimeNs",
           (ulong64)fHdr->exposureTime * fHdr->exposureTimeResNs );
       md.PutImageTag<uns32>( "PVCAM-FMD-FrameNr",   fHdr->frameNr );
       md.PutImageTag<uns16>( "PVCAM-FMD-RoiCount",  fHdr->roiCount );
       md.PutImageTag<ulong64>( "PVCAM-FMD-TimestampBofNs",
           (ulong64)fHdr->timestampBOF * fHdr->timestampResNs );
       md.PutImageTag<ulong64>( "PVCAM-FMD-TimestampEofNs",
           (ulong64)fHdr->timestampEOF * fHdr->timestampResNs );
       // Implied ROI
       const rgn_type& iRoi = metaFrameStruct_->impliedRoi;
       std::stringstream iRoiStr;
       iRoiStr << "[" << iRoi.s1 << ", " << iRoi.s2 << ", " << iRoi.sbin << ", "
           << iRoi.p1 << ", " << iRoi.p2 << ", " << iRoi.pbin << "]"; 
       md.PutImageTag<std::string>("PVCAM-FMD-ImpliedRoi", iRoiStr.str()); 
       // Per-ROI metadata
       std::stringstream roiMdStr;
       roiMdStr << "[";
       for (int i = 0; i < metaFrameStruct_->roiCount; ++i)
       {
           const md_frame_roi_header* rHdr = metaFrameStruct_->roiArray[i].header;
           // Since we cannot add per-ROI metadata we will format the MD to a simple JSON array
           // and add it as a per-Frame metadata TAG. Example:
           // "[{"nr":1,"coords":[0,0,0,0,0,0],"borNs":123,"eorNs":456},{"nr":2,"coords":[0,0,0,0,0,0],"borNs":123,"eorNs":456}]"
           roiMdStr << "{\"nr\":" << rHdr->roiNr
               << ",\"coords\":["
               << rHdr->roi.s1 << "," << rHdr->roi.s2 << "," << rHdr->roi.sbin << ","
               << rHdr->roi.p1 << "," << rHdr->roi.p2 << "," << rHdr->roi.pbin << "],"
               << "\"borNs\":" << (ulong64)rHdr->timestampBOR * fHdr->roiTimestampResNs << ","
               << "\"eorNs\":" << (ulong64)rHdr->timestampEOR * fHdr->roiTimestampResNs << "}";
           if (i != metaFrameStruct_->roiCount - 1)
              roiMdStr << ",";
           else
              roiMdStr << "]";
       }
       md.PutImageTag<std::string>("PVCAM-FMD-RoiMD", roiMdStr.str());
   }
#endif

   ret = PushImageToMmCore( pOutBuf, &md );

   if ( ret == DEVICE_OK )
      imagesInserted_++;

   // If we already have all frames inserted tell the camera to stop
   if ( isUsingCallbacks_ )
   {
      if ( imagesInserted_ >= imagesToAcquire_ || ret != DEVICE_OK )
      {
         StopSequenceAcquisition();
      }
   }

   return ret;
}


int16 Universal::LogCamError(int lineNr, std::string message, bool debug) throw()
{
   int16 nErrCode = pl_error_code();
   try
   {
      char msg[ERROR_MSG_LEN];
      if(!pl_error_message (nErrCode, msg))
      {
         CDeviceUtils::CopyLimitedString(msg, "Unknown");
      }
      ostringstream os;
      os << "PVCAM API error: \""<< msg <<"\", code: " << nErrCode << "\n";
      os << "In file: " << __FILE__ << ", " << "line: " << lineNr << ", " << message; 
      LogMessage(os.str(), debug);
      SetErrorText(nErrCode, msg);
   }
   catch(...){}

   return nErrCode;
}

int Universal::LogMMError(int errCode, int lineNr, std::string message, bool debug) const throw()
{
   try
   {
      char strText[MM::MaxStrLength];
      if (!CCameraBase<Universal>::GetErrorText(errCode, strText))
      {
         CDeviceUtils::CopyLimitedString(strText, "Unknown");
      }
      ostringstream os;
      os << "Error code "<< errCode << ": " <<  strText <<"\n";
      os << "In file: " << __FILE__ << ", " << "line: " << lineNr << ", " << message; 
      LogMessage(os.str(), debug);
   }
   catch(...) {}
   return errCode;
}

void Universal::LogMMMessage(int lineNr, std::string message, bool debug) const throw()
{
   try
   {
      ostringstream os;
      os << message << ", in file: " << __FILE__ << ", " << "line: " << lineNr; 
      LogMessage(os.str(), debug);
   }
   catch(...){}
}

// Handle color mode property (Debayer ON or OFF)
int Universal::OnColorMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   START_ONPROPERTY("Universal::OnColorMode", eAct);

   int nRet = DEVICE_OK;

   if (eAct == MM::AfterSet)
   {
       string val;
       pProp->Get(val);
       acqCfgNew_.ColorProcessingEnabled = (val == g_Keyword_ON);
       nRet = applyAcqConfig();
   }
   else if (eAct == MM::BeforeGet)
   {
       pProp->Set(acqCfgCur_.ColorProcessingEnabled ? g_Keyword_ON : g_Keyword_OFF);
   }
   return nRet;
}

// Camera sensor mask - read only, reported by camera. Note that this
// property may change with port/speed so we need a full property for it.
int Universal::OnSensorCfaMask(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   START_ONPROPERTY("Universal::OnSensorCfaMask", eAct);
   if (eAct == MM::AfterSet)
   { // Nothing to set, this is a read only property
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set(camCurrentSpeed_.colorMaskStr.c_str());
   }

   return DEVICE_OK;
}

int Universal::OnRedScale(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   START_ONPROPERTY("Universal::OnRedScale", eAct);

   if (eAct == MM::BeforeGet)
   {
      pProp->Set(redScale_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(redScale_);
   }
   return DEVICE_OK;
}

int Universal::OnGreenScale(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   START_ONPROPERTY("Universal::OnGreenScale", eAct);

   if (eAct == MM::BeforeGet)
   {
      pProp->Set(greenScale_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(greenScale_);
   }
   return DEVICE_OK;
}

int Universal::OnBlueScale(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   START_ONPROPERTY("Universal::OnBlueScale", eAct);

   if (eAct == MM::BeforeGet)
   {
      pProp->Set(blueScale_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(blueScale_);
   }
   return DEVICE_OK;
}


int Universal::OnAlgorithmCfaMask(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   START_ONPROPERTY("Universal::OnAlgorithmCfaMask", eAct);

   int nRet = DEVICE_OK;

   if (eAct == MM::AfterSet)
   {
      string val;
      pProp->Get(val);
      if (val == g_Keyword_RGGB)
      {
          acqCfgNew_.DebayerAlgMask = CFA_RGGB;
      }
      else if (val == g_Keyword_BGGR)
      {
          acqCfgNew_.DebayerAlgMask = CFA_BGGR;
      }
      else if (val == g_Keyword_GRBG)
      {
          acqCfgNew_.DebayerAlgMask = CFA_GRBG;
      }
      else if (val == g_Keyword_GBRG)
      {
          acqCfgNew_.DebayerAlgMask = CFA_GBRG;
      }
      else
      {
          acqCfgNew_.DebayerAlgMask = COLOR_RGGB;
      }
      nRet = applyAcqConfig();
   }
   else if (eAct == MM::BeforeGet)
   {
      switch (acqCfgCur_.DebayerAlgMask)
      {
          case CFA_RGGB:
          pProp->Set(g_Keyword_RGGB);
          break;

          case CFA_BGGR:
          pProp->Set(g_Keyword_BGGR);
          break;

          case CFA_GRBG:
          pProp->Set(g_Keyword_GRBG);
          break;

          case CFA_GBRG:
          pProp->Set(g_Keyword_GBRG);
          break;
          
          default:
          pProp->Set(g_Keyword_RGGB);
          break;
      }
      // When CFA Auto is selected this property becomes read only. This can be done
      // only in the Get() handler. (other set handlers do not have access to other
      // MM::Property instances)
      static_cast<MM::Property*>(pProp)->SetReadOnly(acqCfgCur_.DebayerAlgMaskAuto);
   }
   return DEVICE_OK;
}

int Universal::OnAlgorithmCfaMaskAuto(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   START_ONPROPERTY("Universal::OnAlgorithmCfaMaskAuto", eAct);
   if (eAct == MM::AfterSet)
   {
      string val;
      pProp->Get(val);
      acqCfgNew_.DebayerAlgMaskAuto = (val == g_Keyword_ON) ? true : false;
      applyAcqConfig();
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set(acqCfgCur_.DebayerAlgMaskAuto ? g_Keyword_ON : g_Keyword_OFF);
   }
   return DEVICE_OK;
}

int Universal::OnInterpolationAlgorithm(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   START_ONPROPERTY("Universal::OnInterpolationAlgorithm", eAct);

   int nRet = DEVICE_OK;

   if (eAct == MM::AfterSet)
   {
      string val;
      pProp->Get(val);

      if (val == g_Keyword_Replication)
          acqCfgNew_.DebayerAlgInterpolation = ALG_REPLICATION;
      else if (val == g_Keyword_Bilinear)
          acqCfgNew_.DebayerAlgInterpolation = ALG_BILINEAR;
      else if (val == g_Keyword_SmoothHue)
          acqCfgNew_.DebayerAlgInterpolation =  ALG_SMOOTH_HUE;
      else if (val == g_Keyword_AdaptiveSmoothHue)
          acqCfgNew_.DebayerAlgInterpolation =  ALG_ADAPTIVE_SMOOTH_HUE;
      else 
          acqCfgNew_.DebayerAlgInterpolation =  ALG_REPLICATION;


      nRet = applyAcqConfig();
   }
   else if (eAct == MM::BeforeGet)
   {
      switch (acqCfgCur_.DebayerAlgInterpolation)
      {
          case ALG_REPLICATION:
          pProp->Set(g_Keyword_Replication);
          break;

          case ALG_BILINEAR:
          pProp->Set(g_Keyword_Bilinear);
          break;

          case ALG_SMOOTH_HUE:
          pProp->Set(g_Keyword_SmoothHue);
          break;

          case ALG_ADAPTIVE_SMOOTH_HUE:
          pProp->Set(g_Keyword_AdaptiveSmoothHue);
          break;
          
          default:
          pProp->Set(g_Keyword_Replication);
          break;
      }
   }
   return nRet;
}



#ifdef PVCAM_CALLBACKS_SUPPORTED
int Universal::OnAcquisitionMethod(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   START_ONPROPERTY("Universal::OnAcquisitionMethod", eAct);
   if (eAct == MM::AfterSet)
   {
      string val;
      pProp->Get(val);

      if ( IsCapturing() )
         StopSequenceAcquisition();

      if ( val.compare(g_Keyword_AcqMethod_Callbacks) == 0 )
      {
         if ( pl_cam_register_callback_ex3( hPVCAM_, PL_CALLBACK_EOF, PvcamCallbackEofEx3, this ) == PV_OK )
            isUsingCallbacks_ = true;
         else
            LogCamError(__LINE__, "pl_cam_register_callback_ex3 failed" );
      }
      else
      {
         pl_cam_deregister_callback( hPVCAM_, PL_CALLBACK_EOF );
         isUsingCallbacks_ = false;
      }
   }
   else if (eAct == MM::BeforeGet)
   {
      if ( isUsingCallbacks_ )
      {
         pProp->Set( g_Keyword_AcqMethod_Callbacks );
      }
      else
      {
         pProp->Set( g_Keyword_AcqMethod_Polling );
      }
   }
   return DEVICE_OK;
}
#endif // PVCAM_CALLBACKS_SUPPORTED


/**************************** Post Processing Functions ******************************/
#ifdef WIN32

int Universal::OnResetPostProcProperties(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   START_METHOD("Universal::OnResetPostProcProperties");

   if (eAct == MM::AfterSet)
   {
      string choice;
      pProp->Get(choice);
      if (choice.compare(g_Keyword_Yes) == 0)
      {
         if (IsCapturing())
            StopSequenceAcquisition();

         if(!pl_pp_reset(hPVCAM_))
         {
             LogCamError(__LINE__, "pl_pp_reset");
             return DEVICE_CAN_NOT_SET_PROPERTY;
         }
         refreshPostProcValues();
      }
   }
   else if (eAct == MM::BeforeGet)
   {
      // The value is always "No" as this is not a switch but rather a 'trigger'
      pProp->Set(g_Keyword_No);
   }

   return DEVICE_OK;
}

/**
* Reads current values of all post processing parameters from the camera
* and stores the values in local array.
*/
int Universal::refreshPostProcValues()
{
   int16 ppIndx;
   uns32 ppValue;
   for (uns32 i = 0; i < PostProc_.size(); i++)
   {
      ppIndx = (int16)PostProc_[i].GetppIndex();
      if (!pl_set_param(hPVCAM_, PARAM_PP_INDEX, &ppIndx))
      {
         LogCamError(__LINE__, "pl_set_param PARAM_PP_INDEX"); 
         return DEVICE_ERR;
      }
      ppIndx = (int16)PostProc_[i].GetpropIndex();
      if (!pl_set_param(hPVCAM_, PARAM_PP_PARAM_INDEX, &ppIndx))
      {
         LogCamError(__LINE__, "pl_set_param PARAM_PP_PARAM_INDEX"); 
         return DEVICE_ERR;
      }
      if (!pl_get_param(hPVCAM_, PARAM_PP_PARAM, ATTR_CURRENT, &ppValue))
      {
         LogCamError(__LINE__, "pl_get_param PARAM_PP_PARAM ATTR_CURRENT"); 
         return DEVICE_ERR;
      }
      PostProc_[i].SetcurValue(ppValue);
   }
   return DEVICE_OK;
}

/**
* Reverts a single setting that we know had an error
*/
int Universal::revertPostProcValue( long absoluteParamIdx, MM::PropertyBase* pProp )
{
   uns32 ppValue;

   // get previous value from PVCAM, and restore the value back into the control
   //  and other data structures
   if( pl_get_param(hPVCAM_, PARAM_PP_PARAM, ATTR_CURRENT, &ppValue) )
   {
      pProp->Set( (long) ppValue );
      PostProc_[absoluteParamIdx].SetcurValue(ppValue);
   }

   return DEVICE_OK;
}

/**
* When user changes a PP property in UI this method is called twice: first with MM::AfterSet followed by 
* immediate MM::BeforeGet to obtain the actual value and display it back in UI.
* When live mode is active and user sets the property the MM stops acquisition, calls this method with
* MM::AfterSet, resumes the acquisition and asks for the value back with MM::BeforeGet. For this reason
* we cannot get the actual property value directly from the camera with pl_get_param because the streaming
* might be already active. (we cannot call pl_get or pl_set when continuous streaming mode is active)
*/
int Universal::OnPostProcProperties(MM::PropertyBase* pProp, MM::ActionType eAct, long index)
{
   START_ONPROPERTY("Universal::OnPostProcProperties", eAct);
   uns32  ppValue = 0; // This is the actual value that will be sent to camera
   int16  ppIndx;      // Used for PARAM_PP_INDEX and PARAM_PP_PARAM_INDEX
   string valueStr;    // Temporary variables used for converting the value from UI
   long   valueLng;    //    representation to PVCAM value. 

   if (eAct == MM::AfterSet)
   {
      if (IsCapturing())
         StopSequenceAcquisition();

      // The user just set a new value, find out what is the desired value,
      // convert it to PVCAM PP value and send it to the camera.
      ppIndx = (int16)PostProc_[index].GetppIndex();

      if (!pl_set_param(hPVCAM_, PARAM_PP_INDEX, &ppIndx))
      {
          LogCamError(__LINE__, "pl_set_param PARAM_PP_INDEX");
          revertPostProcValue( index, pProp );
          return DEVICE_CAN_NOT_SET_PROPERTY;
      }

      ppIndx = (int16)PostProc_[index].GetpropIndex();
      if (!pl_set_param(hPVCAM_, PARAM_PP_PARAM_INDEX, &ppIndx))
      {
          LogCamError(__LINE__, "pl_set_param PARAM_PP_PARAM_INDEX");
          revertPostProcValue( index, pProp );
          return DEVICE_CAN_NOT_SET_PROPERTY;
      }

      // translate the value from the actual control in MM
      if (PostProc_[index].GetRange() == 1)
      {
         pProp->Get(valueStr);

         if (valueStr == g_Keyword_Yes)
            ppValue = 1;
         else
            ppValue = 0;
      }
      else
      {
         pProp->Get(valueLng);

         ppValue = valueLng;
      }

      // set the actual parameter value in the camera
      if (!pl_set_param(hPVCAM_, PARAM_PP_PARAM, &ppValue))
      {
          LogCamError( __LINE__, "pl_set_param PARAM_PP_PARAM" );
          revertPostProcValue( index, pProp );
          return DEVICE_CAN_NOT_SET_PROPERTY;
      }

      // Read the value back so we know what value was really applied
      if (!pl_get_param(hPVCAM_, PARAM_PP_PARAM, ATTR_CURRENT, &ppValue))
      {
          LogCamError( __LINE__, "pl_get_param PARAM_PP_PARAM ATTR_CURRENT" );
          revertPostProcValue( index, pProp );
          return DEVICE_CAN_NOT_SET_PROPERTY;
      }

      // update the control in the user interface
      PostProc_[index].SetcurValue(ppValue);
   }
   else if (eAct == MM::BeforeGet)
   {
      // Here we return the 'cached' parameter values only. We cannot ask camera directly
      // because this part of code might be called when sequence acquisition is active and
      // we cannot ask camera when streaming is on.
      if (PostProc_[index].GetRange() == 1)
      {
         // The property is of a Yes/No type
         ppValue = (uns32)PostProc_[index].GetcurValue();

         if (ppValue == 1)
            valueStr = g_Keyword_Yes;
         else 
            valueStr = g_Keyword_No;

         pProp->Set(valueStr.c_str());
      }
      else
      {
         // The property is a range type
         ppValue = (uns32)PostProc_[index].GetcurValue();

         pProp->Set((long)ppValue);
      }
   }

   return DEVICE_OK;
}

#endif // WIN32

//===========================================================================

#ifdef PVCAM_CALLBACKS_SUPPORTED
// Static PVCAM callback handler
void Universal::PvcamCallbackEofEx3(PFRAME_INFO /*pFrameInfo*/, void* pContext)
{
    // We don't need the FRAME_INFO because we will get it in FrameDone via get_latest_frame
    Universal* pCam = (Universal*)pContext;
    pCam->FrameAcquired();
}
#endif // PVCAM_CALLBACKS_SUPPORTED



