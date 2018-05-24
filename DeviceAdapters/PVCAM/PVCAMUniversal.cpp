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
#include "AcqThread.h"
#include "Version.h"

using namespace std;

//#define DEBUG_METHOD_NAMES

#ifdef DEBUG_METHOD_NAMES
#define START_METHOD(name)              LogAdapterMessage(name);
#define START_ONPROPERTY(name,action)   LogAdapterMessage(string(name)+(action==MM::AfterSet?"(AfterSet)":"(BeforeGet)"));
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
// Global PVCAM lock controlling access to PVCAM for all cameras and threads
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
const char* g_Keyword_ClearMode       = "ClearMode";
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
const char* g_Keyword_AcqMethod           = "AcquisitionMethod";             // Callbacks/Polling
const char* g_Keyword_AcqMethod_Callbacks = "Callbacks";
const char* g_Keyword_AcqMethod_Polling   = "Polling";
const char* g_Keyword_OutputTriggerFirstMissing = "OutputTriggerFirstMissing";
const char* g_Keyword_CircBufFrameCnt      = "CircularBufferFrameCount";
const char* g_Keyword_CircBufSizeAuto      = "CircularBufferAutoSize";       // ON/OFF
const char* g_Keyword_CircBufFrameRecovery = "CircularBufferFrameRecovery";  // ON/OFF
const char* g_Keyword_CircBufEnabled       = "CircularBufferEnabled";        // ON/OFF
#ifdef PVCAM_SMART_STREAMING_SUPPORTED
const char* g_Keyword_SmartStreamingValues   = "SMARTStreamingValues[ms]";
const char* g_Keyword_SmartStreamingEnable   = "SMARTStreamingEnabled";
#endif
const char* g_Keyword_MetadataEnabled  = "MetadataEnabled";
const char* g_Keyword_CentroidsEnabled = "CentroidsEnabled";
const char* g_Keyword_CentroidsRadius  = "CentroidsRadius";
const char* g_Keyword_CentroidsCount   = "CentroidsCount";
const char* g_Keyword_FanSpeedSetpoint = "FanSpeedSetpoint";
const char* g_Keyword_PMode            = "PMode";

const char* g_Keyword_TimingExposureTimeNs     = "Timing-ExposureTimeNs";
const char* g_Keyword_TimingReadoutTimeNs      = "Timing-ReadoutTimeNs";
const char* g_Keyword_TimingClearingTimeNs     = "Timing-ClearingTimeNs";
const char* g_Keyword_TimingPostTriggerDelayNs = "Timing-PostTriggerDelayNs";
const char* g_Keyword_TimingPreTriggerDelayNs  = "Timing-PreTriggerDelayNs";

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


//=============================================================================
//===================================================== LOCAL UTILITY FUNCTIONS


static inline double round(double value)
{
    return floor(0.5 + value);
};


//=============================================================================
//=================================================================== Universal

Universal::Universal(short cameraId) :
    CCameraBase<Universal>(),
    initialized_(false),
    imagesToAcquire_(0), imagesInserted_(0), imagesAcquired_(0), imagesRecovered_(0),
    hPVCAM_(0),
    cameraId_(cameraId),
    cameraModel_(PvCameraModel_Generic),
    circBufFrameCount_(CIRC_BUF_FRAME_CNT_DEF), // Sizes larger than 3 caused image tearing in ICX-674. Reason unknown.
    circBufFrameRecoveryEnabled_(false),
    stopOnOverflow_(true),
    snappingSingleFrame_(false),
    singleFrameModeReady_(false),
    sequenceModeReady_(false),
    callPrepareForAcq_(true),
    isAcquiring_(false),
    triggerTimeout_(10),
    microsecResSupported_(false),
    microsecResMax_(1000000), // Will run in usec for up to 1 second
    pollingThd_(0),
    notificationThd_(0),
    acqThd_(0),
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
    eofEvent_(false, false),
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
    prmExposureTime_(0),
    prmExposeOutMode_(0),
    prmClearCycles_(0),
    prmClearMode_(0),
    prmReadoutPort_(0),
    prmSpdTabIndex_(0),
    prmColorMode_(0),
    prmFrameBufSize_(0),
    prmRoiCount_(0),
    prmMetadataEnabled_(0),
    prmCentroidsEnabled_(0),
    prmCentroidsRadius_(0),
    prmCentroidsCount_(0),
    prmFanSpeedSetpoint_(0),
    prmTrigTabSignal_(0),
    prmLastMuxedSignal_(0),
    prmPMode_(0),
    prmAdcOffset_(0),
    prmReadoutTime_(0),
    prmClearingTime_(0),
    prmPreTriggerDelay_(0),
    prmPostTriggerDelay_(0)
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
    SetErrorText(ERR_OPERATION_TIMED_OUT, "The operation has timed out");
    SetErrorText(ERR_FRAME_READOUT_FAILED, "Frame readout failed");
    SetErrorText(ERR_TOO_MANY_ROIS, "Too many ROIs"); // Later overwritten by more specific message

    pollingThd_ = new PollingThread(this);             // Pointer to the sequencing thread

    deviceLabel_[0] = '\0';

    // The notification thread will have slightly smaller queue than the circular buffer.
    // This is to reduce the risk of frames being overwritten by PVCAM when the circular
    // buffer starts to be full. Whith smaller queue we will simply start throwing old
    // frames away earlier because those old frames could soon get overwritten.
    notificationThd_ = new NotificationThread(this);
    notificationThd_->activate();

    acqThd_ = new AcqThread(this);
    acqThd_->Start(); // Starts the thread loop but waits for Resume()
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
    delete prmExposureTime_;
    delete prmTriggerMode_;
    delete prmExposeOutMode_;
    delete prmClearCycles_;
    delete prmClearMode_;
    delete prmSpdTabIndex_;
    delete prmReadoutPort_;
    delete prmColorMode_;
    delete prmFrameBufSize_;
    delete prmRoiCount_;
    delete prmMetadataEnabled_;
    delete prmCentroidsEnabled_;
    delete prmCentroidsRadius_;
    delete prmCentroidsCount_;
    delete prmFanSpeedSetpoint_;
    delete prmTrigTabSignal_;
    delete prmLastMuxedSignal_;
    delete prmPMode_;
    delete prmAdcOffset_;
    delete prmReadoutTime_;
    delete prmClearingTime_;
    delete prmPreTriggerDelay_;
    delete prmPostTriggerDelay_;
#ifdef PVCAM_SMART_STREAMING_SUPPORTED
    delete prmSmartStreamingEnabled_;
    delete prmSmartStreamingValues_;
#endif

    // Delete universal parameters
    for ( unsigned i = 0; i < universalParams_.size(); i++ )
        delete universalParams_[i];
    universalParams_.clear();
}


//=============================================================================
//==================================================================== MMDevice


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
        LogAdapterMessage("Initializing PVCAM");
        if (!pl_pvcam_init())
        {
            LogPvcamError(__LINE__, "First PVCAM init failed");
            // Try once more:
            if (!pl_pvcam_uninit())
                LogPvcamError(__LINE__, "PVCAM uninit failed");
            if (!pl_pvcam_init())
                return LogPvcamError(__LINE__, "Second PVCAM init failed");
        }
        PVCAM_initialized_ = true;

#ifdef PVCAM_ADAPTER_CUSTOM_BUILD
        // If this is a custom build show a warning popup before initializing the adapter.
        char msg[256];
        sprintf_s(msg, "You are using a Micro-Manager with custom PVCAM adapter build.\nAdapter version %u.%u.%u.",
            PVCAM_ADAPTER_VERSION_MAJOR, PVCAM_ADAPTER_VERSION_MINOR, PVCAM_ADAPTER_VERSION_REVISION);
        MessageBoxA(NULL, msg, "Warning", MB_OK | MB_ICONWARNING | MB_SETFOREGROUND);
#endif
    }
    else
    {
        LogAdapterMessage("PVCAM already initialized");
    }

    // gather information about the camera
    // ------------------------------------------

    // Get PVCAM version
    uns16 version;
    if (!pl_pvcam_get_ver(&version))
        return LogPvcamError(__LINE__, "pl_pvcam_get_ver() FAILED");

    int16 numCameras;
    if (!pl_cam_get_total(&numCameras))
        return LogPvcamError(__LINE__, "pl_cam_get_total() FAILED");

    uns16 major = (version >> 8) & 0xFF;
    uns16 minor = (version >> 4) & 0xF;
    uns16 trivial = version & 0xF;

    stringstream ver;
    ver << major << "." << minor << "." << trivial;
    nRet = CreateProperty("PVCAM Version", ver.str().c_str(), MM::String, true);
    ver << ". Number of cameras detected: " << numCameras;
    LogAdapterMessage("PVCAM version: " + ver.str());
    assert(nRet == DEVICE_OK);

    stringstream verAdapter;
    verAdapter << PVCAM_ADAPTER_VERSION_MAJOR << "." << PVCAM_ADAPTER_VERSION_MINOR << "." << PVCAM_ADAPTER_VERSION_REVISION;
    nRet = CreateProperty("PVCAM Adapter Version", verAdapter.str().c_str(), MM::String, true);
    LogAdapterMessage("PVCAM Adapter version: " + verAdapter.str());
    assert(nRet == DEVICE_OK);

    // find camera
    if (!pl_cam_get_name(cameraId_, camName_))
    {
        LogPvcamError(__LINE__, "pl_cam_get_name");
        return ERR_CAMERA_NOT_FOUND;
    }

    LogAdapterMessage("Opening camera...");

    // Get a handle to the camera
    if (!pl_cam_open(camName_, &hPVCAM_, OPEN_EXCLUSIVE ))
        return LogPvcamError(__LINE__, "pl_cam_open failed" );

    refCount_++;

    /// --- STATIC PROPERTIES
    /// are properties that are not changed during session. These are read-out only once.
    LogAdapterMessage( "Initializing Static Camera Properties" );
    nRet = initializeStaticCameraParams();
    if ( nRet != DEVICE_OK )
        return nRet;

    /// --- BUILD THE SPEED TABLE
    LogAdapterMessage( "Building Speed Table" );
    nRet = initializeSpeedTable();
    if ( nRet != DEVICE_OK )
        return nRet;

    // TODO: Remove the camCurrentSpeed_ and move port/speed/gain handling to applyAcqConfig()
    acqCfgNew_.PortId = camCurrentSpeed_.portIndex;
    acqCfgNew_.SpeedIndex = camCurrentSpeed_.spdIndex;

    /// --- DYNAMIC PROPERTIES
    /// are properties that may be updated by a camera or changed by the user during session.
    /// These are read upon opening the camera and then updated on various events. These usually
    /// needs a handler that is called by MM when the GUI asks for the property value.
    LogAdapterMessage("Initializing Dynamic Camera Properties");

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
    // Start with 80 chars for each of 512 centroids
    metaAllRoisStr_.reserve(80 * 512);

    /// PARAM_FRAME_BUFFER_SIZE, no UI property but we use it later in the code
    prmFrameBufSize_ = new PvParam<ulong64>( "PARAM_FRAME_BUFFER_SIZE", PARAM_FRAME_BUFFER_SIZE, this, true );

    /// MULTI-ROI SUPPORT CHECK
    prmRoiCount_ = new PvParam<uns16>("PARAM_ROI_COUNT", PARAM_ROI_COUNT, this, true);

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
        pAct = new CPropertyAction(this, &Universal::OnClearCycles);
        const uns16 cur = prmClearCycles_->Current();
        const char* curStr = CDeviceUtils::ConvertToString(cur);

        nRet = CreateProperty(g_Keyword_ClearCycles, curStr, MM::Integer, prmClearCycles_->IsReadOnly(), pAct);
        assert(nRet == DEVICE_OK);
        nRet = SetPropertyLimits(g_Keyword_ClearCycles, 0, 16);
        assert(nRet == DEVICE_OK);

        acqCfgNew_.ClearCycles = cur;
    }

    /// CLEAR MODE
    prmClearMode_ = new PvEnumParam("PARAM_CLEAR_MODE", PARAM_CLEAR_MODE, this, true);
    if (prmClearMode_->IsAvailable())
    {
        pAct = new CPropertyAction(this, &Universal::OnClearMode);
        const int32 cur = prmClearMode_->Current();
        const char* curStr = prmClearMode_->GetEnumString(cur).c_str();

        nRet = CreateProperty(g_Keyword_ClearMode, curStr, MM::String, prmClearMode_->IsReadOnly(), pAct);
        assert(nRet == DEVICE_OK);
        nRet = SetAllowedValues(g_Keyword_ClearMode, prmClearMode_->GetEnumStrings());
        assert(nRet == DEVICE_OK);

        acqCfgNew_.ClearMode = cur;
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
        LogAdapterMessage("This camera supports SMART streaming");
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
                LogAdapterMessage("SMART streaming disabled on launch");
            }

        }
        //not handling else for the first if because prmSmartStreamingEnabled->Set always returns DEVICE_OK, might be added later

        pAct = new CPropertyAction (this, &Universal::OnSmartStreamingValues);
        nRet = CreateProperty(g_Keyword_SmartStreamingValues, "", MM::String, false, pAct);
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

    /// SPEED
    /// Note that this can change depending on output port
    prmSpdTabIndex_ = new PvParam<int16>("PARAM_SPDTAB_INDEX", PARAM_SPDTAB_INDEX, this, true);
    if (prmSpdTabIndex_->IsAvailable())
    {
        pAct = new CPropertyAction (this, &Universal::OnReadoutRate);
        nRet = CreateProperty(g_ReadoutRate, camCurrentSpeed_.spdString.c_str(), MM::String, false, pAct);
        if (nRet != DEVICE_OK)
            return nRet;

        // Fill in the GUI speed choices
        std::vector<std::string> spdChoices;
        uns32 curPort = prmReadoutPort_->IsAvailable() ? prmReadoutPort_->Current() : 0;
        std::map<int16, SpdTabEntry>::iterator i = camSpdTable_[curPort].begin();
        for( ; i != camSpdTable_[curPort].end(); ++i )
            spdChoices.push_back(i->second.spdString);
        // Set the allowed readout rates
        SetAllowedValues(g_ReadoutRate, spdChoices);
    }

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
            LogAdapterError(DEVICE_ERR, __LINE__, "Current gain name not applicable!" );
        nRet = CreateProperty(MM::g_Keyword_Gain, gainName.c_str(), MM::String, false, pAct);
        if (nRet != DEVICE_OK)
            return nRet;

        // Fill in the GUI gain choices
        vector<string> gainChoices;
        for (int16 i = camCurrentSpeed_.gainMin; i <= camCurrentSpeed_.gainMax; i++)
            gainChoices.push_back(camCurrentSpeed_.gainNameMapReverse.at(i));
        SetAllowedValues(MM::g_Keyword_Gain, gainChoices);
    }

    /// EXPOSURE RESOLUTION
    // The PARAM_EXP_RES_INDEX is used to get and set the current exposure resolution (usec, msec, sec, ...)
    // The PARAM_EXP_RES is only used to enumerate the supported exposure resolutions and their string names
    microsecResSupported_ = false;
    acqCfgNew_.ExposureMs  = 10.0;
    acqCfgNew_.ExposureRes = EXP_RES_ONE_MILLISEC;
    prmExpResIndex_ = new PvParam<uns16>( "PARAM_EXP_RES_INDEX", PARAM_EXP_RES_INDEX, this, true );
    prmExpRes_ = new PvEnumParam( "PARAM_EXP_RES", PARAM_EXP_RES, this, true );
    // The PARAM_EXPOSURE_TIME also returns the camera actual exposure time, if supported
    prmExposureTime_ = new PvParam<ulong64>( "PARAM_EXPOSURE_TIME", PARAM_EXPOSURE_TIME, this, true );
    if ( prmExposureTime_->IsAvailable() )
    {
        pAct = new CPropertyAction (this, &Universal::OnTimingExposureTimeNs);
        nRet = CreateProperty(g_Keyword_TimingExposureTimeNs, "0", MM::Float, true, pAct);
        if (nRet != DEVICE_OK)
            return nRet;
    }
    if ( prmExpResIndex_->IsAvailable() )
    {
        if ( prmExpRes_->IsAvailable() )
        {
            std::vector<int32> enumVals = prmExpRes_->GetEnumValues();
            for ( unsigned i = 0; i < enumVals.size(); ++i )
            {
                if ( enumVals[i] == EXP_RES_ONE_MICROSEC )
                {
                    // If microsec is supported and the camera reports the range,
                    // we check what is the max microsec range and keep the camera
                    // running in microsec up to the max range.
                    if (prmExposureTime_->IsAvailable())
                    {
                        // Switch to microsec...
                        nRet = prmExpRes_->SetAndApply(EXP_RES_ONE_MICROSEC);
                        if (nRet != DEVICE_OK)
                            return nRet; // Error logged in SetAndApply()
                        // We need to re-read the exposure time parameter from PVCAM to
                        // update the cached values.
                        nRet = prmExposureTime_->Update();
                        if (nRet != DEVICE_OK)
                            return nRet; // Error logged in Update()
                        // Read the microsec max range
                        microsecResMax_ = static_cast<uns32>(prmExposureTime_->Max());
                    }

                    microsecResSupported_ = true;
                    break;
                }
            }
            // Switch the resolution to usec if available. We will later switch it
            // back and forth dynamically based on exposure value and microsec max range
            if (microsecResSupported_)
            {
                nRet = prmExpRes_->SetAndApply(EXP_RES_ONE_MICROSEC);
                if (nRet != DEVICE_OK)
                    return nRet; // Error logged in Update()
                acqCfgNew_.ExposureRes = EXP_RES_ONE_MICROSEC;
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
            LogAdapterMessage("This Camera reports EM Gain available but it's ICX285, so no EM.");
        }
        else
        {
            LogAdapterMessage("This Camera has Em Gain");
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
        LogAdapterMessage("This Camera does not have EM Gain");


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
        LogAdapterMessage( "Frame Transfer mode is available" );
        uns32 pmode = PMODE_FT;
        if ( pl_set_param( hPVCAM_, PARAM_PMODE, &pmode ) != PV_OK )
            LogPvcamError( __LINE__, "pl_set_param PARAM_PMODE PMODE_FT" );
    }
    else LogAdapterMessage( "Frame Transfer mode not available" );

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
    LogAdapterMessage( "Initializing Post Processing features..." );
    initializePostProcessing();
    LogAdapterMessage( "Post Processing initialized" );

    // Circular buffer auto/manual switch
    acqCfgNew_.CircBufSizeAuto = true;
    pAct = new CPropertyAction(this, &Universal::OnCircBufferSizeAuto);
    nRet = CreateProperty( g_Keyword_CircBufSizeAuto,
        acqCfgNew_.CircBufSizeAuto ? g_Keyword_ON : g_Keyword_OFF, MM::String, false, pAct);
    AddAllowedValue(g_Keyword_CircBufSizeAuto, g_Keyword_ON);
    AddAllowedValue(g_Keyword_CircBufSizeAuto, g_Keyword_OFF);

    // Circular buffer size. This allows the user to set how many frames we want to allocate the PVCAM
    // PVCAM circular buffer for. The default value is fine for most cases, however chaning this value
    // may help in some cases (e.g. lowering it down to 3 helped to resolve ICX-674 image tearing issues)
    pAct = new CPropertyAction(this, &Universal::OnCircBufferFrameCount);
    nRet = CreateProperty( g_Keyword_CircBufFrameCnt,
        CDeviceUtils::ConvertToString(CIRC_BUF_FRAME_CNT_DEF), MM::Integer, acqCfgNew_.CircBufSizeAuto, pAct);
    // If we are in auto mode the property has no limits because the value is read only and adjusted
    // automatically by internal algorithm or PVCAM. In manual mode we just set the initial range that
    // is later dynamically adjusted by actual frame size.
    // Note: passing 0, 0 will disable the limit checking but for some reason returns an error so
    // we don't check the error code for SetPropertyLimits() here. See also: updateCircBufRange()
    if (acqCfgNew_.CircBufSizeAuto)
        SetPropertyLimits( g_Keyword_CircBufFrameCnt, 0, 0);
    else
        SetPropertyLimits( g_Keyword_CircBufFrameCnt, CIRC_BUF_FRAME_CNT_MIN, CIRC_BUF_FRAME_CNT_MAX );


    initializeUniversalParams();

    // CALLBACKS
    // Check if we can use PVCAM callbacks. This is recommended way to get notified when the frame
    // readout is finished. Otherwise we will fall back to old polling method.
    acqCfgNew_.CallbacksEnabled = false;
#ifdef PVCAM_CALLBACKS_SUPPORTED
    if ( pl_cam_register_callback_ex3( hPVCAM_, PL_CALLBACK_EOF, PvcamCallbackEofEx3, this ) == PV_OK )
    {
        pAct = new CPropertyAction(this, &Universal::OnAcquisitionMethod);
        nRet = CreateProperty(g_Keyword_AcqMethod, g_Keyword_AcqMethod_Polling, MM::String, false, pAct );
        AddAllowedValue(g_Keyword_AcqMethod, g_Keyword_AcqMethod_Polling);
        AddAllowedValue(g_Keyword_AcqMethod, g_Keyword_AcqMethod_Callbacks);
        LogAdapterMessage( "Using callbacks for frame acquisition" );
        acqCfgNew_.CallbacksEnabled = true;
    }
    else
    {
        LogAdapterMessage( "pl_cam_register_callback_ex3 failed! Using polling for frame acquisition" );
    }
#endif

    // FRAME_INFO SUPPORT
#ifdef PVCAM_FRAME_INFO_SUPPORTED
    // Initialize the FRAME_INFO structure, this will contain the frame metadata provided by PVCAM
    if ( !pl_create_frame_info_struct( &pFrameInfo_ ) )
    {
        return LogPvcamError(__LINE__, "Failed to initialize the FRAME_INFO structure");
    }
#endif

    // TRIGGER TABLE
    // We will create a property for every TrigTab and LastMuxed combination so we will end up with something similar
    // to how Post Processing is displayed:
    //  Trigger-Expose Out-Mux
    //  Trigger-Expose Out-AnyOtherFutureProperty
    //  Trigger-Read Out-Mux
    //  Trigger-Read Out-AnyOtherFutureProperty
    prmTrigTabSignal_ = new PvEnumParam( "PARAM_TRIGTAB_SIGNAL", PARAM_TRIGTAB_SIGNAL, this, true );
    prmLastMuxedSignal_ = new PvParam<uns8>( "PARAM_LAST_MUXED_SIGNAL", PARAM_LAST_MUXED_SIGNAL, this, true );
    if (prmTrigTabSignal_->IsAvailable())
    {
        const std::vector<std::string>& trigTabStrs = prmTrigTabSignal_->GetEnumStrings();
        const std::vector<int>&         trigTabVals = prmTrigTabSignal_->GetEnumValues();

        for (size_t iTrigSig = 0; iTrigSig < trigTabVals.size(); ++iTrigSig)
        {
            const std::string trigName = trigTabStrs[iTrigSig]; // ExposeOut, ReadOut, etc.
            const int         trigVal  = trigTabVals[iTrigSig]; // 0,         1,       etc.
            // Apply the signal we want to work with
            nRet = prmTrigTabSignal_->SetAndApply(trigVal);
            if (nRet != DEVICE_OK)
                return LogAdapterError(nRet, __LINE__, "Failed to set trigger signal");

            // Check the property of each signal and build the UI properties
            // At the moment we support the PARAM_LAST_MUXED_SIGNAL only
            if (prmLastMuxedSignal_->IsAvailable())
            {
                const std::string propName = "Trigger-" + trigName + "-Mux";
                const uns8 curVal = prmLastMuxedSignal_->Current();
                const uns8 minVal = prmLastMuxedSignal_->Min();
                const uns8 maxVal = prmLastMuxedSignal_->Max();
                CPropertyActionEx *pExAct = new CPropertyActionEx(this, &Universal::OnTrigTabLastMux, trigVal);
                nRet = CreateProperty(propName.c_str(), CDeviceUtils::ConvertToString(curVal), MM::String, false, pExAct);
                if (nRet != DEVICE_OK)
                    return LogAdapterError(nRet, __LINE__, "Failed to create property for PARAM_LAST_MUXED_SIGNAL");
                // The MUX won't be a high number (4 physical cables for Prime) so we will display
                // the property as a combo box with a couple of allowed values
                for (int val = minVal; val <= maxVal; ++val)
                {
                    AddAllowedValue(propName.c_str(), CDeviceUtils::ConvertToString(val));
                }
                // Store the actual Last Mux value of this signal to our settings map
                acqCfgNew_.TrigTabLastMuxMap[trigVal] = curVal;
            }
            // TODO: Any other signal properties (similar to PARAM_LAST_MUXED_SIGNAL)
            // will be added here with corresponding map created in settings container.
            // e.g. acqCfgNew_.TrigTabSuperMuxMap
        }
    }

    // PMODE (FRAME TRANSFER)
    prmPMode_ = new PvEnumParam( "PARAM_PMODE", PARAM_PMODE, this, true );
    if (prmPMode_->IsAvailable())
    {
        const std::vector<std::string>& pmodeStrs = prmPMode_->GetEnumStrings();
        const int32 cur = prmPMode_->Current();
        const std::string curStr = prmPMode_->GetEnumString(cur);

        pAct = new CPropertyAction(this, &Universal::OnPMode);
        nRet = CreateProperty(g_Keyword_PMode, curStr.c_str(), MM::String, false, pAct);
        if (nRet != DEVICE_OK)
            return LogAdapterError(nRet, __LINE__, "Failed to create property for PARAM_PMODE");

        for (size_t i = 0; i < pmodeStrs.size(); ++i)
        {
            AddAllowedValue(g_Keyword_PMode, pmodeStrs[i].c_str());
        }

        acqCfgNew_.PMode = cur;
    }

    // ADC Offset parameter
    prmAdcOffset_ = new PvParam<int16>("PARAM_ADC_OFFSET", PARAM_ADC_OFFSET, this, true);
    if (prmAdcOffset_->IsAvailable())
    {
        pAct = new CPropertyAction(this, &Universal::OnAdcOffset);
        nRet = CreateProperty(MM::g_Keyword_Offset, "0", MM::Integer, prmAdcOffset_->IsReadOnly(), pAct);
        SetPropertyLimits(MM::g_Keyword_Offset, prmAdcOffset_->Min(), prmAdcOffset_->Max());
        acqCfgNew_.AdcOffset = prmAdcOffset_->Current();
    }

    nRet = initializePostSetupParams();
    if (nRet != DEVICE_OK)
        return nRet;

    // CIRCULAR BUFFER MODE (ON, OFF)
    pAct = new CPropertyAction(this, &Universal::OnCircBufferEnabled);
    nRet = CreateProperty( g_Keyword_CircBufEnabled, g_Keyword_ON, MM::String, false, pAct);
    if (nRet != DEVICE_OK)
        return LogAdapterError(nRet, __LINE__, "Failed to create property for Circular Buffer mode");
    AddAllowedValue(g_Keyword_CircBufEnabled, g_Keyword_ON);
    AddAllowedValue(g_Keyword_CircBufEnabled, g_Keyword_OFF);

    // Initialize the acquisition configuration
    unsigned int maxRois = 1;
    if (prmRoiCount_ && prmRoiCount_->IsAvailable())
        maxRois = prmRoiCount_->Max();
    acqCfgNew_.Rois.SetCapacity(maxRois);
    acqCfgNew_.Rois.Add(PvRoi(0, 0, camSerSize_, camParSize_));
    // We know the max ROIs so update our error message
    SetErrorText(ERR_TOO_MANY_ROIS,
        std::string("Device supports only " + std::to_string((long long)maxRois) + " ROI(s).").c_str());

    // Make sure our configs are synchronized
    acqCfgCur_ = acqCfgNew_;

    // Force sending initial setup to camera to have up to date "post-setup" parameters
    nRet = applyAcqConfig(true);
    if (nRet != DEVICE_OK)
        return LogAdapterError(nRet, __LINE__, "Failed to apply initial settings to camera");

    initialized_ = true;
    START_METHOD("<<< Universal::Initialize");
    return DEVICE_OK;
}

int Universal::Shutdown()
{
    if (initialized_)
    {
        rs_bool ret;

#ifdef PVCAM_CALLBACKS_SUPPORTED
        if ( acqCfgCur_.CallbacksEnabled )
        {
            pl_cam_deregister_callback( hPVCAM_, PL_CALLBACK_EOF );
        }
#endif
        ret = pl_cam_close(hPVCAM_);
        if (!ret)
            LogPvcamError(__LINE__, "pl_cam_close");
        assert(ret);     
        refCount_--;      
        if (PVCAM_initialized_ && refCount_ <= 0)      
        {
            refCount_ = 0;
            if (!pl_pvcam_uninit())
                LogPvcamError(__LINE__, "pl_pvcam_uninit");
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

void Universal::GetName(char* name) const 
{
    CDeviceUtils::CopyLimitedString(name, camName_);
}

bool Universal::Busy()
{
    START_METHOD("Universal::Busy");
    return snappingSingleFrame_;
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


//=============================================================================
//==================================================================== MMCamera


int Universal::SnapImage()
{
    int nRet = DEVICE_ERR;
    MM::MMTime startTs = GetCurrentMMTime();
    {
        MMThreadGuard acqGuard(&acqLock_);
        START_METHOD("Universal::SnapImage");

        if(snappingSingleFrame_)
        {
            LogAdapterMessage("SnapImage() failed: GetImage() has not been done for previous frame", true);
            return DEVICE_ERR;
        }
        if(isAcquiring_)
        {
            LogAdapterMessage("SnapImage() failed: Camera already acquiring.", true);
            return DEVICE_CAMERA_BUSY_ACQUIRING;
        }

        startTs = GetCurrentMMTime();

        acqCfgNew_.AcquisitionType = AcqType_Snap;
        nRet = applyAcqConfig();
        if (nRet != DEVICE_OK)
            return nRet;

        if(!singleFrameModeReady_)
        {
            // TODO: Do this at the end of previous snap.
            //       The live mode is always stopped by pl_exp_abort
            //       (which is the same as pl_exp_stop_cont)
            //       and pl_exp_finish_seq should be called for sequence acquisitions only.
            g_pvcamLock.Lock();
            if (pl_exp_stop_cont(hPVCAM_, CCS_HALT) != PV_OK)
                LogPvcamError(__LINE__, "pl_exp_stop_cont() failed");
            // Address the TODO above and this workaround won't be needed
            void* buf = circBuf_.Data();
            if (buf != NULL)
            {
                if (pl_exp_finish_seq(hPVCAM_, circBuf_.Data(), 0) != PV_OK)
                    LogPvcamError(__LINE__, "pl_exp_finish_seq() failed");
            }
            g_pvcamLock.Unlock();

            nRet = resizeImageBufferSingle();
            if (nRet != DEVICE_OK) 
                return LogAdapterError(nRet, __LINE__, "Failed to resize the image buffer");
            singleFrameModeReady_ = true;
        }

        snappingSingleFrame_ = true;
        imagesToAcquire_ = 1; 
        imagesInserted_ = 0;
        lastPvFrameNr_ = 0;

        eofEvent_.Reset();
        nRet = acquireFrameSeq();
        if (nRet != DEVICE_OK)
            return nRet; // Error logged in previous call

        isAcquiring_ = true;
    }

    nRet = waitForFrameSeq();

    {
        MMThreadGuard acqGuard(&acqLock_);

        if (nRet == DEVICE_OK)
        {
            nRet = postProcessSingleFrame(&singleFrameBufFinal_, singleFrameBufRaw_, singleFrameBufRawSz_);
        }
        else
        {
            // Exposure was not done correctly. if application nevertheless 
            // tries to get (wrong) image by calling GetImage, the error will be reported
            snappingSingleFrame_ = false;
            singleFrameModeReady_ = false;
        }

        const MM::MMTime endTs = GetCurrentMMTime();
        LogTimeDiff(startTs, endTs, "SnapImage() took: ", true);

        isAcquiring_ = false;
        return nRet;
    }
}

const unsigned char* Universal::GetImageBuffer()
{
    START_METHOD("Universal::GetImageBuffer");

    if(!snappingSingleFrame_)
    {
        LogAdapterMessage(__LINE__, "Warning: GetImageBuffer called before SnapImage()");
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
        LogAdapterMessage(__LINE__, "Warning: GetImageBufferAsRGB32 called before SnapImage()");
        return 0;
    }

    snappingSingleFrame_ = false;

    return (unsigned int*)singleFrameBufFinal_;
}

unsigned Universal::GetImageWidth() const
{
    const unsigned int width = acqCfgCur_.Rois.ImpliedRoi().ImageRgnWidth();
    return width;
}

unsigned Universal::GetImageHeight() const 
{
    const unsigned int height = acqCfgCur_.Rois.ImpliedRoi().ImageRgnHeight();
    return height;
}

unsigned Universal::GetImageBytesPerPixel() const
{
    const unsigned int bpp = acqCfgCur_.ColorProcessingEnabled ? 4 : 2;
    return bpp;
}

long Universal::GetImageBufferSize() const
{
    const int bytesPerPixel = acqCfgCur_.ColorProcessingEnabled ? 4 : 2;
    const long bufferSize = acqCfgCur_.Rois.ImpliedRoi().ImageRgnWidth() * acqCfgCur_.Rois.ImpliedRoi().ImageRgnHeight() * bytesPerPixel;
    return bufferSize;
}

unsigned Universal::GetBitDepth() const
{
    const unsigned int bitDepth = acqCfgCur_.ColorProcessingEnabled ? 8 : (unsigned)camCurrentSpeed_.bitDepth;
    return bitDepth;
}

int Universal::GetBinning() const 
{
    const int bin = acqCfgCur_.Rois.BinX();
    return bin;
}

int Universal::SetBinning(int binSize) 
{
    ostringstream os;
    os << binSize << "x" << binSize;
    return SetProperty(MM::g_Keyword_Binning, os.str().c_str());
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
        LogAdapterError(ret, __LINE__, "Failed to set the exposure");
}

int Universal::IsExposureSequenceable(bool& isSequenceable) const
{
    isSequenceable = false;
    return DEVICE_OK;
}

unsigned Universal::GetNumberOfComponents() const
{
    return acqCfgCur_.ColorProcessingEnabled ? 4 : 1;
}

int Universal::SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize)
{
    START_METHOD("Universal::SetROI");

    int nRet = DEVICE_OK;

    // PVCAM does not like ROIs smaller than 2x2 pixels (8 bytes)
    // (This check avoids crash for 1x1 ROIs in PVCAM 2.9.5)
    if ( xSize * ySize < 4 )
    {
        return LogAdapterError( ERR_ROI_SIZE_NOT_SUPPORTED, __LINE__,
            "Universal::SetROI ROI size not supported" );
    }

    // We keep the ROI definition in sensor coordinates however the coordinates given by uM are
    // related to the actual image the ROI was drawn on. We need to take the current binning
    // into account.

    // Calling this function for camera with multi ROI support should clear
    // all the multi ROI definitions and revert to single ROI operation.
    const uns16 bx = acqCfgCur_.Rois.BinX();
    const uns16 by = acqCfgCur_.Rois.BinY();
    acqCfgNew_.Rois.Clear();
    acqCfgNew_.Rois.Add(PvRoi(
        static_cast<uns16>(x * bx), static_cast<uns16>(y * by),
        static_cast<uns16>(xSize * bx), static_cast<uns16>(ySize * by), bx, by));
    nRet = applyAcqConfig();

    return nRet;
}

int Universal::GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize)
{
    START_METHOD("Universal::GetROI");

    const PvRoi& curRoi = acqCfgCur_.Rois.At(0);

    x = curRoi.ImageRgnX();
    y = curRoi.ImageRgnY();
    xSize = curRoi.ImageRgnWidth();
    ySize = curRoi.ImageRgnHeight();

    return DEVICE_OK;
}

int Universal::ClearROI()
{
    START_METHOD("Universal::ClearROI");

    int nRet = DEVICE_OK;

    // Clear the ROI only, keep the binning.
    const uns16 bx = acqCfgCur_.Rois.BinX();
    const uns16 by = acqCfgCur_.Rois.BinY();

    acqCfgNew_.Rois.Clear();
    acqCfgNew_.Rois.Add(PvRoi(0, 0, camSerSize_, camParSize_, bx, by));
    nRet = applyAcqConfig();

    return nRet;
}

bool Universal::SupportsMultiROI()
{
    const bool supported = (prmRoiCount_ != 0 && prmRoiCount_->IsAvailable() && prmRoiCount_->Max() > 1);
    return supported;
}

bool Universal::IsMultiROISet()
{
    const bool isSet = (acqCfgCur_.Rois.Count() > 1);
    return isSet;
}

int Universal::GetMultiROICount(unsigned& count)
{
    count = static_cast<unsigned>(acqCfgCur_.Rois.Count());
    return DEVICE_OK;
}

int Universal::SetMultiROI(const unsigned* xs, const unsigned* ys, const unsigned* widths, const unsigned* heights, unsigned numROIs)
{
    int nRet = DEVICE_OK;

    if (numROIs > acqCfgCur_.Rois.Capacity())
        return ERR_TOO_MANY_ROIS;

    // Get the current binning
    const uns16 bx = acqCfgCur_.Rois.BinX();
    const uns16 by = acqCfgCur_.Rois.BinY();

    acqCfgNew_.Rois.Clear();
    for (unsigned int i = 0; i < numROIs; ++i)
    {
        const PvRoi roi(
            static_cast<uns16>(xs[i] * bx), static_cast<uns16>(ys[i] * by),
            static_cast<uns16>(widths[i] * bx), static_cast<uns16>(heights[i] * by),
            bx, by);
        acqCfgNew_.Rois.Add(roi);
    }

    nRet = applyAcqConfig();
    return nRet;
}

int Universal::GetMultiROI(unsigned* xs, unsigned* ys, unsigned* widths, unsigned* heights, unsigned* length)
{
    const unsigned roiCount = acqCfgCur_.Rois.Count();
    if (roiCount > *length)
    {
       // This should never happen.
       return DEVICE_INTERNAL_INCONSISTENCY;
    }

    for (unsigned int i = 0; i < roiCount; ++i)
    {
        const PvRoi& roi = acqCfgCur_.Rois.At(i);
        xs[i] = roi.ImageRgnX();
        ys[i] = roi.ImageRgnY();
        widths[i] = roi.ImageRgnWidth();
        heights[i] = roi.ImageRgnHeight();
    }

    *length = roiCount;

    return DEVICE_OK;
}

bool Universal::IsCapturing()
{
    acqLock_.Lock();
    const bool bCapturing = isAcquiring_;
    acqLock_.Unlock();
    return bCapturing;
}

int Universal::PrepareSequenceAcqusition()
{
    START_METHOD("Universal::PrepareSequenceAcqusition");

    if (isAcquiring_)
        return ERR_BUSY_ACQUIRING;

    bool& modeReadyFlag = sequenceModeReady_;
    int (Universal::*resizeImageBufferFn)() = &Universal::resizeImageBufferContinuous;
    //auto resizeImageBufferFn = &Universal::resizeImageBufferContinuous;

    if (acqCfgCur_.CircBufEnabled)
    {
        modeReadyFlag = sequenceModeReady_;
        // Reconfigure anything that has to do with pl_exp_setup_cont
        resizeImageBufferFn =  &Universal::resizeImageBufferContinuous;
    }
    else
    {
        modeReadyFlag = singleFrameModeReady_;
        // For non-circular buffer acquisition we use the single frame buffer
        // and all the single frame mode logic.
        resizeImageBufferFn =  &Universal::resizeImageBufferSingle;
    }

    if (!modeReadyFlag)
    {
        int ret = (this->*resizeImageBufferFn)();
        if (ret != DEVICE_OK)
            return ret;
        GetCoreCallback()->InitializeImageBuffer(1, 1, GetImageWidth(), GetImageHeight(), GetImageBytesPerPixel());
        modeReadyFlag = true;
        callPrepareForAcq_ = true;
    }

    if (callPrepareForAcq_)
    {
        int ret = GetCoreCallback()->PrepareForAcq(this);
        if (ret != DEVICE_OK)
            return ret;
        callPrepareForAcq_ = false;
    }

    return DEVICE_OK;
}

int Universal::StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow)
{
    MMThreadGuard acqGuard(&acqLock_);
    START_METHOD("Universal::StartSequenceAcquisition");

    int ret = DEVICE_OK;

    acqCfgNew_.AcquisitionType = AcqType_Live;
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

    // initially start with the exposure time as the actual interval estimate
    SetProperty(MM::g_Keyword_ActualInterval_ms, CDeviceUtils::ConvertToString(acqCfgCur_.ExposureMs)); 

    // Cache the current device label so we don't have to copy it for every frame
    GetLabel(deviceLabel_); 
    eofEvent_.Reset(); // Reset the EOF event, we will wait for it to become signalled
    if (acqCfgCur_.CircBufEnabled)
    {
        g_pvcamLock.Lock();
        if (!pl_exp_start_cont(hPVCAM_, circBuf_.Data(), static_cast<uns32>(circBuf_.Size())))
        {
            const int mmErr = LogPvcamError(__LINE__, "pl_exp_start_cont()");
            g_pvcamLock.Unlock(); // Next calls require unlocked  g_pvcamLock
            resizeImageBufferSingle();
            return mmErr;
        }
        g_pvcamLock.Unlock();
    }
    else
    {
        // Fire up the non-cb acquisition thread
        acqThd_->Resume();
    }
    startTime_ = GetCurrentMMTime();

    // Once we call start_cont() we don't want to spend much time in this function because
    // the callbacks will start coming pretty fast. Do not waste time here, what can be done
    // before start_cont() should be done there.

    if ( !acqCfgCur_.CallbacksEnabled && acqCfgCur_.CircBufEnabled )
    {
        pollingThd_->Start();
    }
    isAcquiring_ = true;

    ostringstream os;
    os << "Started sequence on " << deviceLabel_ << ", at " << startTime_.serialize() << ", with " << numImages << " and " << interval_ms << " ms" << endl;
    LogAdapterMessage(os.str().c_str());

    return DEVICE_OK;
}

int Universal::StopSequenceAcquisition()
{
    int nRet = DEVICE_OK;
    {
        MMThreadGuard acqGuard(&acqLock_);
        START_METHOD("Universal::StopSequenceAcquisition");

        nRet = abortAcquisitionInternal();
    }
    // LW: Give the camera some time to stop acquiring. This reduces occasional
    //     crashes/hangs when frequently starting/stopping with some fast cameras.
    //     Please note this has to be called after the acqLock is unlocked, otherwise
    //     the PVCAM callback will be kept holding and no flush will occur.
    CDeviceUtils::SleepMs(100);

    return nRet;
}


//=============================================================================
//============================================================= Action handlers


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

        acqCfgNew_.Rois.SetBinning(
            static_cast<uns16>(binningValuesX_[index]),
            static_cast<uns16>(binningValuesY_[index]));
        // Adjust the coordinates to binnning factor immediately because uM will
        // ask what is the current image size right after that.
        acqCfgNew_.Rois.AdjustCoords();
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
        ss << acqCfgCur_.Rois.BinX() << "x" << acqCfgCur_.Rois.BinY();
        pProp->Set(ss.str().c_str());
    }
    return nRet;
}

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
            nRet = DEVICE_INVALID_PROPERTY_VALUE;
            LogAdapterError( nRet, __LINE__, "Value of BinningX has to be positive" );
        }
        else
        {
            acqCfgNew_.Rois.SetBinningX(static_cast<uns16>(binX));
            acqCfgNew_.Rois.AdjustCoords();
            nRet = applyAcqConfig();
        }
    }
    else if (eAct == MM::BeforeGet)
    {
        pProp->Set((long)acqCfgNew_.Rois.BinX());
    }
    return nRet;
}

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
            nRet = DEVICE_INVALID_PROPERTY_VALUE;
            LogAdapterError( nRet, __LINE__, "Value of BinningY has to be positive" );
        }
        else
        {
            acqCfgNew_.Rois.SetBinningY(static_cast<uns16>(binY));
            acqCfgNew_.Rois.AdjustCoords();
            nRet = applyAcqConfig();
        }
    }
    else if (eAct == MM::BeforeGet)
    {
        pProp->Set((long)acqCfgNew_.Rois.BinY());
    }
    return nRet;
}

int Universal::OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    START_ONPROPERTY("Universal::OnExposure", eAct);
    // Micro manager passes the Exposure value in milli-seconds, as double.
    // PVCAM exposure resolution is switchable, se we will need to convert
    // this value later on.
    int nRet = DEVICE_OK;
    if (eAct == MM::BeforeGet)
    {
        pProp->Set(acqCfgCur_.ExposureMs);
    }
    else if (eAct == MM::AfterSet)
    {
        double newExposure;
        pProp->Get(newExposure);

        acqCfgNew_.ExposureMs = newExposure;
        nRet = applyAcqConfig();
    }
    return nRet;
}

int Universal::OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    START_ONPROPERTY("Universal::OnPixelType", eAct);

    if (eAct == MM::BeforeGet)
    {
        char buf[8];
        snprintf(buf, 8, "%ubit", camCurrentSpeed_.bitDepth); // 12bit, 14bit, 16bit, ...
        pProp->Set(buf);
    }

    return DEVICE_OK;
}

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
            nRet = DEVICE_CAN_NOT_SET_PROPERTY;
            LogAdapterError(nRet, __LINE__, "Gain not supported");
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

int Universal::OnReadoutPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    START_ONPROPERTY("Universal::OnReadoutPort", eAct);

    if (eAct == MM::AfterSet)
    {
        string portStr;
        pProp->Get( portStr );

        acqCfgNew_.PortId = prmReadoutPort_->GetEnumValue(portStr);

        return applyAcqConfig();
    }
    else if (eAct == MM::BeforeGet)
    {
        pProp->Set(prmReadoutPort_->GetEnumString(acqCfgCur_.PortId).c_str());
    }

    return DEVICE_OK;
}

int Universal::OnReadoutRate(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    START_ONPROPERTY("Universal::OnReadoutRate", eAct);

    uns32 currentPort = prmReadoutPort_->Current();

    if (eAct == MM::AfterSet)
    {
        string selectedSpdString;
        pProp->Get(selectedSpdString);

        // Find the corresponding speed index from reverse speed table
        const SpdTabEntry selectedSpd = camSpdTableReverse_[currentPort][selectedSpdString];

        acqCfgNew_.SpeedIndex = selectedSpd.spdIndex;
        return applyAcqConfig();
    }
    else if (eAct == MM::BeforeGet)
    {
        pProp->Set(camCurrentSpeed_.spdString.c_str());
    }

    return DEVICE_OK;
}

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

int Universal::OnPMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    START_ONPROPERTY("Universal::OnPMode", eAct);

    if (eAct == MM::BeforeGet)
    {
        pProp->Set(prmPMode_->GetEnumString(acqCfgCur_.PMode).c_str());
    }
    else if (eAct == MM::AfterSet)
    {
        string valStr;
        pProp->Get( valStr );

        acqCfgNew_.PMode = prmPMode_->GetEnumValue(valStr);
        return applyAcqConfig();
    }
    return DEVICE_OK;
}

int Universal::OnAdcOffset(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    START_ONPROPERTY("Universal::OnAdcOffset", eAct);

    if (eAct == MM::BeforeGet)
    {
        pProp->Set((long)acqCfgCur_.AdcOffset);
    }
    else if (eAct == MM::AfterSet)
    {
        long val;
        pProp->Get( val );

        acqCfgNew_.AdcOffset = val;
        return applyAcqConfig();
    }
    return DEVICE_OK;
}

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

int Universal::OnClearCycles(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    START_ONPROPERTY("Universal::OnClearCycles", eAct);

    if (eAct == MM::AfterSet)
    {
        long val;
        pProp->Get( val );
        acqCfgNew_.ClearCycles = val;
        return applyAcqConfig();
    }
    else if (eAct == MM::BeforeGet)
    {
        pProp->Set( (long)acqCfgCur_.ClearCycles );
    }

    return DEVICE_OK;
}

int Universal::OnClearMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    START_ONPROPERTY("Universal::OnClearMode", eAct);

    if (eAct == MM::AfterSet)
    {
        string valStr;
        pProp->Get( valStr );
        acqCfgNew_.ClearMode = prmClearMode_->GetEnumValue(valStr);
        return applyAcqConfig();
    }
    else if (eAct == MM::BeforeGet)
    {
        pProp->Set(prmClearMode_->GetEnumString(acqCfgCur_.ClearMode).c_str());
    }

    return DEVICE_OK;
}

int Universal::OnCircBufferEnabled(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    START_ONPROPERTY("Universal::OnCircBufferEnabled", eAct);
    int ret = DEVICE_OK;
    if (eAct == MM::AfterSet)
    {
        string choice;
        pProp->Get(choice);
        acqCfgNew_.CircBufEnabled = (choice == g_Keyword_ON) ? true : false;
        ret = applyAcqConfig();
    }
    else if (eAct == MM::BeforeGet)
    {
        pProp->Set(acqCfgCur_.CircBufEnabled ? g_Keyword_ON : g_Keyword_OFF);
    }
    return ret;
}

int Universal::OnCircBufferSizeAuto(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    START_ONPROPERTY("Universal::OnCircBufferSizeAuto", eAct);
    int ret = DEVICE_OK;
    if (eAct == MM::AfterSet)
    {
        string choice;
        pProp->Get(choice);
        acqCfgNew_.CircBufSizeAuto = (choice == g_Keyword_ON) ? true : false;
        ret = applyAcqConfig();
    }
    else if (eAct == MM::BeforeGet)
    {
        const bool bReadOnly = !acqCfgCur_.CircBufEnabled;
        static_cast<MM::Property*>(pProp)->SetReadOnly(bReadOnly);
        pProp->Set(acqCfgCur_.CircBufSizeAuto ? g_Keyword_ON : g_Keyword_OFF);
    }
    return ret;
}

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
        const bool bReadOnly = acqCfgCur_.CircBufSizeAuto | !acqCfgCur_.CircBufEnabled;
        static_cast<MM::Property*>(pProp)->SetReadOnly(bReadOnly);
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
        const bool bReadOnly = !acqCfgCur_.CircBufEnabled;
        static_cast<MM::Property*>(pProp)->SetReadOnly(bReadOnly);
        if ( circBufFrameRecoveryEnabled_ )
            pProp->Set(g_Keyword_ON);
        else
            pProp->Set(g_Keyword_OFF);
    }

    return DEVICE_OK;
}

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

int Universal::OnTrigTabLastMux(MM::PropertyBase* pProp, MM::ActionType eAct, long trigSignal)
{
    START_ONPROPERTY("Universal::OnTrigTabLastMux", eAct);

    // Check if the signal exists. This is more a developer check, the trigSignal
    // value cannot be set by the user, it's assigned to every property and the
    // TrigTabLastMuxMap is build during initialization.
    if (acqCfgCur_.TrigTabLastMuxMap.find(trigSignal) == acqCfgCur_.TrigTabLastMuxMap.end())
        return DEVICE_CAN_NOT_SET_PROPERTY;

    if (eAct == MM::BeforeGet)
    {
        pProp->Set((long)acqCfgCur_.TrigTabLastMuxMap[trigSignal]);
    }
    else if (eAct == MM::AfterSet)
    {
        long val;
        pProp->Get( val );

        acqCfgNew_.TrigTabLastMuxMap[trigSignal] = static_cast<int>(val);
        return applyAcqConfig();
    }
    return DEVICE_OK;
}

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

        acqCfgNew_.CallbacksEnabled = (val.compare(g_Keyword_AcqMethod_Callbacks) == 0);
        applyAcqConfig();
    }
    else if (eAct == MM::BeforeGet)
    {
        pProp->Set(acqCfgCur_.CallbacksEnabled ? g_Keyword_AcqMethod_Callbacks : g_Keyword_AcqMethod_Polling);
    }
    return DEVICE_OK;
}
#endif // PVCAM_CALLBACKS_SUPPORTED

#ifdef WIN32
int Universal::OnPostProcProperties(MM::PropertyBase* pProp, MM::ActionType eAct, long index)
{
    // When user changes a PP property in UI this method is called twice: first with MM::AfterSet followed by 
    // immediate MM::BeforeGet to obtain the actual value and display it back in UI.
    // When live mode is active and user sets the property the MM stops acquisition, calls this method with
    // MM::AfterSet, resumes the acquisition and asks for the value back with MM::BeforeGet. For this reason
    // we cannot get the actual property value directly from the camera with pl_get_param because the streaming
    // might be already active. (we cannot call pl_get or pl_set when continuous streaming mode is active)

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
            LogPvcamError(__LINE__, "pl_set_param PARAM_PP_INDEX");
            revertPostProcValue( index, pProp );
            return DEVICE_CAN_NOT_SET_PROPERTY;
        }

        ppIndx = (int16)PostProc_[index].GetpropIndex();
        if (!pl_set_param(hPVCAM_, PARAM_PP_PARAM_INDEX, &ppIndx))
        {
            LogPvcamError(__LINE__, "pl_set_param PARAM_PP_PARAM_INDEX");
            revertPostProcValue( index, pProp );
            return DEVICE_CAN_NOT_SET_PROPERTY;
        }

        // translate the value from the actual control in MM
        if (PostProc_[index].IsBoolean())
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
            LogPvcamError( __LINE__, "pl_set_param PARAM_PP_PARAM" );
            revertPostProcValue( index, pProp );
            return DEVICE_CAN_NOT_SET_PROPERTY;
        }

        // Read the value back so we know what value was really applied
        if (!pl_get_param(hPVCAM_, PARAM_PP_PARAM, ATTR_CURRENT, &ppValue))
        {
            LogPvcamError( __LINE__, "pl_get_param PARAM_PP_PARAM ATTR_CURRENT" );
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
        if (PostProc_[index].IsBoolean())
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
                LogPvcamError(__LINE__, "pl_pp_reset");
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
#endif

#ifdef PVCAM_SMART_STREAMING_SUPPORTED
int Universal::OnSmartStreamingEnable(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    START_ONPROPERTY("Universal::OnSmartStreamingEnable", eAct);

    if (eAct == MM::AfterSet)
    {
        string val;
        pProp->Get(val);

        acqCfgNew_.SmartStreamingEnabled = (val.compare(g_Keyword_Yes) == 0);
        applyAcqConfig();
    }
    else if (eAct == MM::BeforeGet)
    {
        pProp->Set( acqCfgCur_.SmartStreamingEnabled ? g_Keyword_Yes : g_Keyword_No );
    }
    return DEVICE_OK;
}

int Universal::OnSmartStreamingValues(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    START_ONPROPERTY("Universal::OnSmartStreamingValues", eAct);
    int nRet = DEVICE_OK;

    // S.M.A.R.T streaming exposures are stored as array of doubles. The exposure
    // is represented in milli-seconds, the same as the generic exposure time value.
    if (eAct == MM::BeforeGet)
    {
        const size_t exposureCount = acqCfgCur_.SmartStreamingExposures.size();
        const std::vector<double>& exposures = acqCfgCur_.SmartStreamingExposures;
        std::stringstream str;
        // Set precision to 0.000 and disable scientific notation
        str << std::setprecision(3) << std::fixed;

        for (size_t i = 0; i < exposureCount; i++)
        {
            str << exposures[i];
            if (i < exposureCount - 1)
                str << ";"; // Add semicolon to all but last entry
        }
        pProp->Set(str.str().c_str());
    }
    else if (eAct == MM::AfterSet)
    {
        // The S.M.A.R.T streaming is entered by the user in form of:
        // "10;20;30;40;50" string so we will need to parse the values.
        std::vector<double> parsedValues;

        string expListChars;
        pProp->Get(expListChars);

        // Check only allowed characters have been entered
        if (expListChars.find_first_not_of("0123456789;.") != std::string::npos)
        {
            return LogAdapterError(DEVICE_INVALID_PROPERTY_VALUE, __LINE__,
                "SMART Streaming exposures contain forbidden characters");
        }
        // Check that user entered non-empty string
        if (expListChars.length() == 0)
        {
            return LogAdapterError(DEVICE_INVALID_PROPERTY_VALUE, __LINE__,
                "SMART Streaming values are empty");
        }

        // Add semicolon after the last entry if user failed to do so
        // to make the further value processing simpler
        if (expListChars.at(expListChars.length() - 1) != ';') 
        {
            expListChars.append(";");
        }

        // Get number of SMART streaming entries
        const int exposuresEntered = static_cast<int>(std::count(expListChars.begin(), expListChars.end(), ';'));

        // Parse the input string
        std::size_t foundAt = 0;
        std::size_t oldFoundAt = 0;
        for (int i = 0; i < exposuresEntered; i++)
        {
            // look for semicolons and read values
            foundAt = expListChars.find(';', foundAt);

            // check the length of each exposure entry
            std::size_t expCharLength = foundAt - oldFoundAt;

            // if two semicolons were entered with no value between them
            // reject this SMART streaming exposure list
            if (expCharLength == 0)
            {
                return LogAdapterError(DEVICE_CAN_NOT_SET_PROPERTY, __LINE__,
                    "SMART streaming exposure value empty (two semicolons with no value between them)");
            }

            // we should not need more than 10 values before decimal point and 10 values after decimal point, 
            // add one character for decimal point
            // user enters values in miliseconds so this allows hours of exposures, additionally there is no 
            // reason to use SMART streaming with exposures longer than a few hundred miliseconds
            if (expCharLength > 21)
            {
                return LogAdapterError(DEVICE_CAN_NOT_SET_PROPERTY, __LINE__,
                    "SMART streaming exposure value too large");
            }

            std::string substringExposure = expListChars.substr(oldFoundAt, expCharLength);

            // check number of decimal points in each exposure time, return error if more than 
            // one decimal point is found in any of the values
            long long nrOfPeriods = std::count(substringExposure.begin(), substringExposure.end(), '.');
            if (nrOfPeriods > 1)
            {
                return LogAdapterError(DEVICE_CAN_NOT_SET_PROPERTY, __LINE__,
                    "SMART streaming exposure value contains too many decimal points");
            }

            const double parsedVal = atof(substringExposure.c_str());
            parsedValues.push_back(parsedVal);

            oldFoundAt = ++foundAt;
        }

        acqCfgNew_.SmartStreamingExposures = parsedValues;
        nRet = applyAcqConfig();
    }

    return nRet;
}
#endif // PVCAM_SMART_STREAMING_SUPPORTED

int Universal::OnTimingExposureTimeNs(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    START_ONPROPERTY("Universal::OnTimingExposureTimeNs", eAct);
    if (eAct == MM::BeforeGet)
    {
        // This parameter unit depends on currently selected exposure resolution
        const ulong64 camVal = prmExposureTime_->Current();
        double valNs = 0;
        switch (acqCfgCur_.ExposureRes)
        {
        case EXP_RES_ONE_SEC:
            valNs = static_cast<double>(camVal) * 1000000000.0;
            break;
        case EXP_RES_ONE_MILLISEC:
            valNs = static_cast<double>(camVal) * 1000000.0;
            break;
        case EXP_RES_ONE_MICROSEC:
            valNs = static_cast<double>(camVal) * 1000.0;
            break;
        default:
            valNs = static_cast<double>(camVal);
            break;
        }
        pProp->Set(valNs);
    }
    // Nothing to set, this is a read-only property
    return DEVICE_OK;
}

int Universal::OnTimingReadoutTimeNs(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    START_ONPROPERTY("Universal::OnTimingReadoutTimeNs", eAct);
    if (eAct == MM::BeforeGet)
    {
        // The PARAM_READOUT_TIME returns value in micro-seconds
        const uns32 camVal = prmReadoutTime_->Current();
        const double valNs = 1000.0 * camVal;
        pProp->Set(valNs);
    }
    // Nothing to set, this is a read-only property
    return DEVICE_OK;
}

int Universal::OnTimingClearingTimeNs(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    START_ONPROPERTY("Universal::OnTimingClearingTimeNs", eAct);
    if (eAct == MM::BeforeGet)
    {
        pProp->Set(static_cast<double>(prmClearingTime_->Current()));
    }
    // Nothing to set, this is a read-only property
    return DEVICE_OK;
}

int Universal::OnTimingPreTriggerDelayNs(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    START_ONPROPERTY("Universal::OnTimingPreTriggerDelayNs", eAct);
    if (eAct == MM::BeforeGet)
    {
        pProp->Set(static_cast<double>(prmPreTriggerDelay_->Current()));
    }
    // Nothing to set, this is a read-only property
    return DEVICE_OK;
}

int Universal::OnTimingPostTriggerDelayNs(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    START_ONPROPERTY("Universal::OnTimingPostTriggerDelayNs", eAct);
    if (eAct == MM::BeforeGet)
    {
        pProp->Set(static_cast<double>(prmPostTriggerDelay_->Current()));
    }
    // Nothing to set, this is a read-only property
    return DEVICE_OK;
}


//=============================================================================
//====================================================================== PUBLIC

short Universal::Handle()
{
    return hPVCAM_;
}

int Universal::LogPvcamError(int lineNr, const std::string& message, int16 pvErrCode, bool debug) throw()
{
    const int mmErrCode = ERR_PVCAM_OFFSET + pvErrCode;
    try
    {
        char pvErrMsg[ERROR_MSG_LEN];
        if(!pl_error_message (pvErrCode, pvErrMsg))
        {
            CDeviceUtils::CopyLimitedString(pvErrMsg, "[pl_error_message() FAILED!]");
        }

        ostringstream os;

        // Construct the debug log message and UI message
        // Log example:
        //   "[PVCAM] ERROR: 'Acquisition failed'. PVCAM Err:36, Msg:'Script invalid (C0_SCRIPT_INVALID)' [PVCamUniversal.cpp(36)]"
        // UI message does not contain the file and line information

        os << "[PVCAM] ERR: " << message;
        os << ", pvErr:" << pvErrCode << ", pvMsg:'" << pvErrMsg << "'";

        // Stop right here and make it a UI message
        SetErrorText(mmErrCode, os.str().c_str());

        // Append the file and line info: "[PVCAMUniversal.cpp(1234)]"
        os << " [" << __FILE__ << "(" << lineNr << ")]"; 
        LogMessage(os.str(), debug);
    }
    catch(...){}

    return mmErrCode;
}

int Universal::LogAdapterError(int mmErrCode, int lineNr, const std::string& message, bool debug) const throw()
{
    try
    {
        char mmErrMsg[MM::MaxStrLength];
        if (!CCameraBase<Universal>::GetErrorText(mmErrCode, mmErrMsg))
        {
            CDeviceUtils::CopyLimitedString(mmErrMsg, "Unknown");
        }

        ostringstream os;
        os << "[PVCAM] ERR: " << message;
        os << ", mmErr:"<< mmErrCode << ", mmMsg:'" <<  mmErrMsg << "'";
        
        // Append the file and line info: "[PVCAMUniversal.cpp(1234)]"
        os << " [" << __FILE__ << "(" << lineNr << ")]"; 
        LogMessage(os.str(), debug);
    }
    catch(...) {}

    return mmErrCode;
}

void Universal::LogAdapterMessage(int lineNr, const std::string& message, bool debug) const throw()
{
    try
    {
        ostringstream os;
        os << "[PVCAM] INF: " << message;

        // Append the file and line info: "[PVCAMUniversal.cpp(1234)]"
        os << " [" << __FILE__ << "(" << lineNr << ")]"; 
        LogMessage(os.str(), debug);
    }
    catch(...){}
}

void  Universal::LogAdapterMessage(const std::string& message, bool debug) const throw()
{
    try
    {
        ostringstream os;
        os << "[PVCAM] INF: " << message;
        LogMessage(os.str(), debug);
    }
    catch(...){}
}


//=============================================================================
//=================================================================== PROTECTED


int Universal::FrameAcquired()
{
    MMThreadGuard acqGuard(&acqLock_);
    START_METHOD("Universal::FrameAcquired");

    // Ignore any callbacks that might be arriving after stopping the acquisition
    if (!isAcquiring_)
        return DEVICE_OK;

    rs_bool     rsbRet = FALSE;
    void_ptr    pCurrFramePtr = 0;
    PvFrameInfo currFrameNfo;
    currFrameNfo.SetTimestampMsec(GetCurrentMMTime().getMsec());

#ifdef PVCAM_FRAME_INFO_SUPPORTED
    g_pvcamLock.Lock();
    rsbRet = pl_exp_get_latest_frame_ex(hPVCAM_, &pCurrFramePtr, pFrameInfo_ );
    if (rsbRet != PV_OK)
        LogPvcamError(__LINE__, "pl_exp_get_latest_frame_ex() failed");
    g_pvcamLock.Unlock();
    if (rsbRet == PV_OK)
    {
        currFrameNfo.SetPvHCam(pFrameInfo_->hCam);
        currFrameNfo.SetPvFrameNr(pFrameInfo_->FrameNr);
        currFrameNfo.SetPvReadoutTime(pFrameInfo_->ReadoutTime);
        currFrameNfo.SetPvTimeStamp(pFrameInfo_->TimeStamp);
        currFrameNfo.SetPvTimeStampBOF(pFrameInfo_->TimeStampBOF);

        if (acqCfgCur_.CircBufEnabled)
        {
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
                            NotificationEntry recNotif(pRecFrameData,
                                static_cast<unsigned int>(circBuf_.FrameSize()), recFrNfo);
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
    }
#else
    // FRAME_INFO is not supported so we cannot do much. Just retrieve the frame pointer.
    g_pvcamLock.Lock();
    rsbRet = pl_exp_get_latest_frame(hPVCAM_, &pCurrFramePtr ); 
    if (rsbRet != PV_OK)
        LogPvcamError(__LINE__, "pl_exp_get_latest_frame() failed");
    g_pvcamLock.Unlock();
#endif // PVCAM_FRAME_INFO_SUPPORTED

    if ( rsbRet != PV_OK )
    {
        g_pvcamLock.Lock();
        if (pl_exp_abort( hPVCAM_, CCS_CLEAR ) != PV_OK)
            LogPvcamError(__LINE__, "pl_exp_abort() failed");
        g_pvcamLock.Unlock();
        return DEVICE_ERR;
    }

    imagesAcquired_++; // A new frame has been successfully retrieved from the camera

    // The FrameAcquired() is also called for SnapImage() when using callbacks, so we have to
    // check. In case of SnapImage the img_ already contains the data (since its passed
    // to pl_start_seq() and no PushImage is done - the single image is retrieved with GetImageBuffer()
    if ( !snappingSingleFrame_ )
    {
        if (acqCfgCur_.CircBufEnabled)
        {
            const NotificationEntry notif(pCurrFramePtr,
                static_cast<unsigned int>(circBuf_.FrameSize()), currFrameNfo);
            circBuf_.ReportFrameArrived(currFrameNfo, pCurrFramePtr);
            notificationThd_->PushNotification( notif );
        }
        else
        {
            // If we run in non-circular buffer mode we pass the frame directly to
            // uM core, since we use single frame pointer for all frames we cannot
            // post it for processing to other thread but we need to wait for every
            // frame to finish processing before we acquire another one.
            const NotificationEntry notif(pCurrFramePtr,
                static_cast<unsigned int>(singleFrameBufRawSz_), currFrameNfo);
            ProcessNotification(notif);
        }
    }
    else
    {
        // Single snap: just increase the number of actually acquired frames.
        imagesInserted_++;
    }

    eofEvent_.Set();
    return DEVICE_OK;
}

int Universal::PushImageToMmCore(const unsigned char* pPixBuffer, Metadata* pMd )
{
    START_METHOD("Universal::PushImageToMmCore");

    int nRet = DEVICE_ERR;
    MM::Core* pCore = GetCoreCallback();
    // This method inserts a new image into the circular buffer (residing in MMCore)
    nRet = pCore->InsertImage(this, pPixBuffer, GetImageWidth(), GetImageHeight(),
        GetImageBytesPerPixel(), pMd->Serialize().c_str());

    if (!stopOnOverflow_ && nRet == DEVICE_BUFFER_OVERFLOW)
    {
        // do not stop on overflow - just reset the buffer
        pCore->ClearImageBuffer(this);
        nRet = pCore->InsertImage(this, pPixBuffer, GetImageWidth(), GetImageHeight(),
            GetImageBytesPerPixel(), pMd->Serialize().c_str(), false);
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

    // Ignore any callbacks that might be arriving after stopping the acquisition
    if (!isAcquiring_) // Cannot guard it with acqLock_
        return DEVICE_OK;

    int ret = DEVICE_ERR;

    const PvFrameInfo& frameNfo = entry.FrameMetadata();

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
    ret = postProcessSingleFrame(&pOutBuf, (unsigned char*)entry.FrameData(), entry.FrameDataSize());
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
        snprintf(metaRoiStr_, sizeof(metaRoiStr_),
                "[%u, %u, %u, %u, %u, %u]",
                iRoi.s1, iRoi.s2, iRoi.sbin, iRoi.p1, iRoi.p2, iRoi.pbin);
        md.PutImageTag<std::string>("PVCAM-FMD-ImpliedRoi", metaRoiStr_); 
        // Per-ROI metadata
        metaAllRoisStr_ = "[";
        for (int i = 0; i < metaFrameStruct_->roiCount; ++i)
        {
            const md_frame_roi_header* rHdr = metaFrameStruct_->roiArray[i].header;
            // Since we cannot add per-ROI metadata we will format the MD to a simple JSON array
            // and add it as a per-Frame metadata TAG. Example:
            // "[{"nr":1,"coords":[0,0,0,0,0,0],"borNs":123,"eorNs":456},{"nr":2,"coords":[0,0,0,0,0,0],"borNs":123,"eorNs":456}]"
            snprintf(metaRoiStr_, sizeof(metaRoiStr_),
                    "{\"nr\":%u,\"coords\":[%u,%u,%u,%u,%u,%u],\"borNs\":%llu,\"eorNs\":%llu}",
                    rHdr->roiNr,
                    rHdr->roi.s1, rHdr->roi.s2, rHdr->roi.sbin, rHdr->roi.p1, rHdr->roi.p2, rHdr->roi.pbin,
                    (ulong64)rHdr->timestampBOR * fHdr->roiTimestampResNs,
                    (ulong64)rHdr->timestampEOR * fHdr->roiTimestampResNs);
            metaAllRoisStr_.append(metaRoiStr_);
            if (i != metaFrameStruct_->roiCount - 1)
                metaAllRoisStr_.append(",");
        }
        metaAllRoisStr_.append("]");
        md.PutImageTag<std::string>("PVCAM-FMD-RoiMD", metaAllRoisStr_);
    }
#endif

    ret = PushImageToMmCore( pOutBuf, &md );

    if ( ret == DEVICE_OK )
        imagesInserted_++;

    // If we already have all frames inserted tell the camera to stop
    if ( acqCfgCur_.CallbacksEnabled )
    {
        if ( imagesInserted_ >= imagesToAcquire_ || ret != DEVICE_OK )
        {
            abortAcquisitionInternal();
        }
    }

    return ret;
}

#ifndef linux
/*
* Overrides a virtual function from the CCameraBase class
* Do actual capture
* Called from the acquisition thread function
*/
int Universal::PollingThreadRun(void)
{
    START_METHOD(">>>Universal::PollingThreadRun");

    int  ret = DEVICE_ERR;
    char dbgBuf[128]; // Debug log buffer
    pollingThd_->setStop(false); // make sure this thread's status is updated properly.

    try
    {
        const double estReadTimeSec = getEstimatedMaxReadoutTimeMs() / 1000.0f;
        const MM::MMTime timeout((long)(triggerTimeout_ + estReadTimeSec + 2*GetExposure() * 0.001), (long)(2*GetExposure() * 1000));

        do
        {
            ret = waitForFrameConPolling(timeout);
            if (ret == DEVICE_OK)
            {
                ret = FrameAcquired();
            }
            else
            {
                break;
            }
        }
        while (DEVICE_OK == ret && !pollingThd_->getStop() && imagesInserted_ < imagesToAcquire_);

        sprintf( dbgBuf, "ACQ LOOP FINISHED: thdGetStop:%u, ret:%u, imagesInserted_: %lu, imagesToAcquire_: %lu", \
            pollingThd_->getStop(), ret, imagesInserted_, imagesToAcquire_);
        LogAdapterMessage( __LINE__, dbgBuf );

        if (imagesInserted_ >= imagesToAcquire_)
            imagesInserted_ = 0;
        PollingThreadExiting();
        pollingThd_->setStop(true);

        START_METHOD("<<<Universal::ThreadRun");
        return ret;

    }
    catch(...)
    {
        LogAdapterMessage(g_Msg_EXCEPTION_IN_THREAD, false);
        OnThreadExiting();
        pollingThd_->setStop(true);
        return ret;
    }

}

void Universal::PollingThreadExiting() throw ()
{
    try
    {
        g_pvcamLock.Lock();
        if (!pl_exp_stop_cont(hPVCAM_, CCS_HALT)) 
            LogPvcamError(__LINE__, "pl_exp_stop_cont");
        if (!pl_exp_finish_seq(hPVCAM_, circBuf_.Data(), 0))
            LogPvcamError(__LINE__, "pl_exp_finish_seq");
        g_pvcamLock.Unlock();

        sequenceModeReady_ = false;
        isAcquiring_       = false;

        LogAdapterMessage(g_Msg_SEQUENCE_ACQUISITION_THREAD_EXITING);
        if (GetCoreCallback())
            GetCoreCallback()->AcqFinished(this, 0);
    }
    catch (...)
    {
        LogAdapterMessage(__LINE__, g_Msg_EXCEPTION_IN_ON_THREAD_EXITING);
    }
}

#endif


//=============================================================================
//===================================================================== PRIVATE


Universal::Universal(Universal&)
{
    // Empty private copy constructor
}

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
        LogAdapterMessage("PARAM_CAM_FW_VERSION: " + std::string(buf));
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
        return LogAdapterError(DEVICE_ERR, __LINE__, "PARAM_PAR_SIZE is not available or incorrect!");
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
        return LogPvcamError(__LINE__, "PARAM_SER_SIZE is not available or incorrect!");
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
                        LogAdapterMessage("The property has too large range. Not setting limits.");
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

                                        PpParam ppParam(paramNameStream.str().c_str(), i,j);

                                        // create a special drop-down control box for booleans
                                        if (min == 0 && max == 1)
                                        {
                                            ppParam.SetBoolean(true);
                                            nRet = CreateProperty(paramNameStream.str().c_str(), currentValueStream.str().c_str(), MM::String, false, pExAct);
                                            SetAllowedValues(paramNameStream.str().c_str(), boolValues);
                                        }
                                        else 
                                        {
                                            nRet = CreateProperty(paramNameStream.str().c_str(), currentValueStream.str().c_str(), MM::Integer, false, pExAct);
                                            SetPropertyLimits(paramNameStream.str().c_str(), min, max);
                                        }

                                        PostProc_.push_back (ppParam);
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
            LogPvcamError(__LINE__, "pl_pp_reset");
        }

        refreshPostProcValues();
    }

#endif
    return nRet;
}

int Universal::initializeSpeedTable()
{
    uns32 portCount = 0;  // Total number of readout ports
    uns32 portCurIdx = 0; // Current PORT selected by the camera, we will restore it
    int32 spdCount = 0;   // Number of speed choices for each port
    int16 spdCurIdx = 0;  // Current SPEED selected by the camera, we will restore it
    camSpdTable_.clear();
    camSpdTableReverse_.clear();

    if (pl_get_param(hPVCAM_, PARAM_READOUT_PORT, ATTR_COUNT, (void_ptr)&portCount) != PV_OK)
        return LogPvcamError(__LINE__, "pl_get_param PARAM_READOUT_PORT ATTR_COUNT" );

    // Read the current camera port and speed, we will want to restore the camera to this
    // configuration once we read out the entire speed table
    if (pl_get_param(hPVCAM_, PARAM_READOUT_PORT, ATTR_CURRENT, (void_ptr)&portCurIdx) != PV_OK)
        return LogPvcamError(__LINE__, "pl_get_param PARAM_READOUT_PORT ATTR_CURRENT" );
    if (pl_get_param(hPVCAM_, PARAM_SPDTAB_INDEX, ATTR_CURRENT, (void_ptr)&spdCurIdx) != PV_OK)
        return LogPvcamError(__LINE__, "pl_get_param PARAM_SPDTAB_INDEX ATTR_CURRENT" );

    // Iterate through each port and fill in the speed table
    for (uns32 portIndex = 0; portIndex < portCount; portIndex++)
    {
        if (pl_set_param(hPVCAM_, PARAM_READOUT_PORT, (void_ptr)&portIndex) != PV_OK)
            return LogPvcamError(__LINE__, "pl_set_param PARAM_READOUT_PORT" );

        if (pl_get_param(hPVCAM_, PARAM_SPDTAB_INDEX, ATTR_COUNT, (void_ptr)&spdCount) != PV_OK)
            return LogPvcamError(__LINE__, "pl_get_param PARAM_SPDTAB_INDEX ATTR_COUNT" );

        // Read the "default" speed for every port, we will select this one if port changes.
        // Please note we don't read the ATTR_DEFAULT as this one is not properly reported (PVCAM 3.1.9.1)
        int16 portDefaultSpdIdx = 0;
        if (pl_get_param(hPVCAM_, PARAM_SPDTAB_INDEX, ATTR_CURRENT, (void_ptr)&portDefaultSpdIdx) != PV_OK)
            return LogPvcamError(__LINE__, "pl_get_param PARAM_SPDTAB_INDEX ATTR_CURRENT" );

        for (int16 spdIndex = 0; spdIndex < spdCount; spdIndex++)
        {
            SpdTabEntry spdEntry;
            spdEntry.portIndex = portIndex;
            spdEntry.spdIndex = spdIndex;
            spdEntry.portDefaultSpdIdx = portDefaultSpdIdx;

            if (pl_set_param(hPVCAM_, PARAM_SPDTAB_INDEX, (void_ptr)&spdEntry.spdIndex) != PV_OK)
                return LogPvcamError(__LINE__, "pl_set_param PARAM_SPDTAB_INDEX" );

            // Read the pixel time for this speed choice
            if (pl_get_param(hPVCAM_, PARAM_PIX_TIME, ATTR_CURRENT, (void_ptr)&spdEntry.pixTime) != PV_OK)
            {
                LogPvcamError(__LINE__, "pl_get_param PARAM_PIX_TIME failed, using default pix time" );
                spdEntry.pixTime = MAX_PIX_TIME;
            }
            // Read the bit depth for this speed choice
            if (pl_get_param(hPVCAM_, PARAM_BIT_DEPTH, ATTR_CURRENT, &spdEntry.bitDepth) != PV_OK )
            {
                return LogPvcamError(__LINE__, "pl_get_param PARAM_GAIN_INDEX ATTR_CURRENT" );
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
                    return LogPvcamError(__LINE__, "pl_get_param PARAM_COLOR_MODE ATTR_COUNT" );
                }
                int32 colorCur = 0;
                if (pl_get_param(hPVCAM_, PARAM_COLOR_MODE, ATTR_CURRENT, &colorCur) != PV_OK)
                {
                    return LogPvcamError(__LINE__, "pl_get_param PARAM_COLOR_MODE ATTR_CURRENT" );
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
                        return LogPvcamError(__LINE__, "pl_enum_str_length PARAM_COLOR_MODE" );
                    }
                    char* enumStrBuf = new char[enumStrLen+1];
                    enumStrBuf[enumStrLen] = '\0';
                    int32 enumVal = 0;
                    if (pl_get_enum_param(hPVCAM_, PARAM_COLOR_MODE, i, &enumVal, enumStrBuf, enumStrLen) != PV_OK)
                    {
                        return LogPvcamError(__LINE__, "pl_get_enum_param PARAM_COLOR_MODE" );
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
                    return LogAdapterError(DEVICE_INTERNAL_INCONSISTENCY, __LINE__,
                        "ATTR_CURRENT of PARAM_COLOR_MODE does not correspond to any reported ENUM value" );
            }

            // Read the gain range for this speed choice if applicable
            if (pl_get_param(hPVCAM_, PARAM_GAIN_INDEX, ATTR_AVAIL, &spdEntry.gainAvail) != PV_OK )
            {
                LogPvcamError(__LINE__, "pl_get_param PARAM_GAIN_INDEX ATTR_AVAIL failed, not using gain at this speed" );
                spdEntry.gainAvail = FALSE;
            }
            if (spdEntry.gainAvail)
            {
                if (pl_get_param(hPVCAM_, PARAM_GAIN_INDEX, ATTR_MIN, &spdEntry.gainMin) != PV_OK )
                {
                    LogPvcamError(__LINE__, "pl_get_param PARAM_GAIN_INDEX ATTR_MIN failed, using default" );
                    spdEntry.gainMin = 1;
                }
                if (pl_get_param(hPVCAM_, PARAM_GAIN_INDEX, ATTR_MAX, &spdEntry.gainMax) != PV_OK )
                {
                    LogPvcamError(__LINE__, "pl_get_param PARAM_GAIN_INDEX ATTR_MAX failed, using default" );
                    spdEntry.gainMax = 1;
                }
                if (pl_get_param(hPVCAM_, PARAM_GAIN_INDEX, ATTR_DEFAULT, &spdEntry.gainDef) != PV_OK )
                {
                    LogPvcamError(__LINE__, "pl_get_param PARAM_GAIN_INDEX ATTR_DEFAULT failed, using min" );
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
                            char pvGainName[MAX_GAIN_NAME_LEN];
                            if (pl_get_param(hPVCAM_, PARAM_GAIN_NAME, ATTR_CURRENT, pvGainName) == PV_OK)
                            {
                                // Workaround if for some reason PVCAM returns empty string
                                if (strlen(pvGainName) != 0)
                                {
                                    gainNameStr.append("-");
                                    gainNameStr.append(pvGainName);
                                }
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
    const SpdTabEntry& spdDef = camSpdTable_[portCurIdx][spdCurIdx];

    if (pl_set_param(hPVCAM_, PARAM_READOUT_PORT, (void_ptr)&spdDef.portIndex) != PV_OK)
        return LogPvcamError(__LINE__, "pl_set_param PARAM_READOUT_PORT" );
    if (pl_set_param(hPVCAM_, PARAM_SPDTAB_INDEX, (void_ptr)&spdDef.spdIndex) != PV_OK)
        return LogPvcamError(__LINE__, "pl_set_param PARAM_SPDTAB_INDEX" );
    if (spdDef.gainAvail)
    {
        if (pl_set_param(hPVCAM_, PARAM_GAIN_INDEX, (void_ptr)&spdDef.gainDef) != PV_OK)
            return LogPvcamError(__LINE__, "pl_set_param PARAM_GAIN_INDEX" );
    }
    camCurrentSpeed_ = spdDef;

    return DEVICE_OK;
}

int Universal::initializePostSetupParams()
{
    int nRet = DEVICE_OK;
    CPropertyAction* pAct = NULL;

    // ACTUAL READOUT TIME - Reported by camera if supported
    prmReadoutTime_ = new PvParam<uns32>( "PARAM_READOUT_TIME", PARAM_READOUT_TIME, this, true );
    if ( prmReadoutTime_->IsAvailable() )
    {
        pAct = new CPropertyAction (this, &Universal::OnTimingReadoutTimeNs);
        nRet = CreateProperty(g_Keyword_TimingReadoutTimeNs, "0", MM::Float, true, pAct);
        if (nRet != DEVICE_OK)
            return nRet;
    }
    // ACTUAL CLEARING TIME - Reported by camera if supported
    prmClearingTime_ = new PvParam<long64>( "PARAM_CLEARING_TIME", PARAM_CLEARING_TIME, this, true );
    if ( prmClearingTime_->IsAvailable() )
    {
        pAct = new CPropertyAction (this, &Universal::OnTimingClearingTimeNs);
        nRet = CreateProperty(g_Keyword_TimingClearingTimeNs, "0", MM::Float, true, pAct);
        if (nRet != DEVICE_OK)
            return nRet;
    }
    // ACTUAL POST TRIGGER DELAY - Reported by camera if supported
    prmPostTriggerDelay_ = new PvParam<long64>( "PARAM_POST_TRIGGER_TIME", PARAM_POST_TRIGGER_DELAY, this, true );
    if ( prmPostTriggerDelay_->IsAvailable() )
    {
        pAct = new CPropertyAction (this, &Universal::OnTimingPostTriggerDelayNs);
        nRet = CreateProperty(g_Keyword_TimingPostTriggerDelayNs, "0", MM::Float, true, pAct);
        if (nRet != DEVICE_OK)
            return nRet;
    }
    // ACTUAL PRE TRIGGER DELAY - Reported by camera if supported
    prmPreTriggerDelay_ = new PvParam<long64>( "PARAM_PRE_TRIGGER_TIME", PARAM_PRE_TRIGGER_DELAY, this, true );
    if ( prmPreTriggerDelay_->IsAvailable() )
    {
        pAct = new CPropertyAction (this, &Universal::OnTimingPreTriggerDelayNs);
        nRet = CreateProperty(g_Keyword_TimingPreTriggerDelayNs, "0", MM::Float, true, pAct);
        if (nRet != DEVICE_OK)
            return nRet;
    }

    return nRet;
}

int Universal::resizeImageBufferContinuous()
{
    START_METHOD("Universal::ResizeImageBufferContinuous");

    int nRet = DEVICE_ERR;

    try
    {
        // Obtain the PVCAM exposure time and trigger configuration for pl_exp_seup()
        int16 pvExposureMode = 0;
        uns32 pvExposure = 0;
        nRet = getPvcamExposureSetupConfig( pvExposureMode, acqCfgCur_.ExposureMs, pvExposure );
        if ( nRet != DEVICE_OK )
            return nRet;

        // However, if we are running S.M.A.R.T we need to make sure to send a non-zero exposure
        // value to the pl_exp_setup(). So we need to override it.
        if (acqCfgCur_.SmartStreamingActive)
            pvExposure = 10; // Just some random non-zero value

        g_pvcamLock.Lock();
        const rgn_type* rgnArr = acqCfgCur_.Rois.ToRgnArray();
        const uns16     rgnTot = static_cast<uns16>(acqCfgCur_.Rois.Count());
        uns32           frameSize = 0;
        if (!pl_exp_setup_cont(hPVCAM_, rgnTot, rgnArr, pvExposureMode, pvExposure, &frameSize, CIRC_OVERWRITE)) 
        {
            nRet = LogPvcamError(__LINE__, "pl_exp_setup_cont() failed");
            g_pvcamLock.Unlock(); // Next calls require unlocked  g_pvcamLock
            SetBinning(1); // The error might have been caused by not supported BIN or ROI, so do a reset
            this->GetCoreCallback()->OnPropertiesChanged(this); // Notify the MM UI to update the BIN and ROI
            SetErrorText( nRet, "Failed to setup the acquisition" );
            return nRet;
        }
        g_pvcamLock.Unlock();

        nRet = postExpSetupInit(frameSize);
        if (nRet != DEVICE_OK)
            return nRet; // Message logged in the failing method

        nRet = resizeImageProcessingBuffers();
        if (nRet != DEVICE_OK)
            return nRet; // Message logged in the failing method

        // set up a circular buffer for specified number of frames
        if (acqCfgCur_.CircBufSizeAuto)
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

        const ulong64 bufferSize = circBufFrameCount_ * static_cast<ulong64>(frameSize);

        // PVCAM API does not support buffers larger that 4GB
        if (bufferSize > 0xFFFFFFFF)
            return LogAdapterError(ERR_BUFFER_TOO_LARGE, __LINE__, "Buffer too large");

        // In manual mode we return an error if the buffer size is over the limit. Although
        // we dynamically update the slider range the check here is needed because in some
        // corner cases (like switching binning and not clicking the "Refresh" button) the
        // UI temporarily allows the user to set the incorrect value.
        if (!acqCfgCur_.CircBufSizeAuto && bufferSize > CIRC_BUF_SIZE_MAX_USER)
            return LogAdapterError(ERR_BUFFER_TOO_LARGE, __LINE__, "Buffer too large");

        circBuf_.Resize(frameSize, circBufFrameCount_);

        // Set the queue size to slightly less than the CB size to avoid PVCAM overwritting
        // the oldest frame. This way we start throwing old frames away a little earlier.
        notificationThd_->SetQueueCapacity(static_cast<int>(circBufFrameCount_ * 0.7) + 1);

        nRet = DEVICE_OK;
    }
    catch( const std::bad_alloc& e )
    {
        nRet = DEVICE_OUT_OF_MEMORY;
        LogAdapterMessage( e.what() );
    }
    catch( const std::exception& e)
    {
        nRet = DEVICE_ERR;
        LogAdapterMessage( e.what() );
    }
    catch(...)
    {
        nRet = DEVICE_ERR;
        LogAdapterMessage("Unknown exception in ResizeImageBufferContinuous", false);
    }

    singleFrameModeReady_ = false;
    LogAdapterMessage("ResizeImageBufferContinuous singleFrameModeReady_=false", true);
    return nRet;
}

int Universal::resizeImageBufferSingle()
{
    START_METHOD("Universal::ResizeImageBufferSingle");

    int nRet = DEVICE_ERR;

    try
    {
        int16 pvExposureMode = 0;
        uns32 pvExposure = 0;
        nRet = getPvcamExposureSetupConfig( pvExposureMode, acqCfgCur_.ExposureMs, pvExposure );
        if ( nRet != DEVICE_OK )
            return nRet;

        g_pvcamLock.Lock();

        const rgn_type* rgnArr = acqCfgCur_.Rois.ToRgnArray();
        const uns16     rgnTot = static_cast<uns16>(acqCfgCur_.Rois.Count());
        const uns32     expTot = 1;
        uns32           expSize = 0;
        if (!pl_exp_setup_seq(hPVCAM_, expTot, rgnTot, rgnArr, pvExposureMode, pvExposure, &expSize))
        {
            nRet = LogPvcamError(__LINE__, "pl_exp_setup_seq() failed");
            g_pvcamLock.Unlock(); // Next calls require unlocked  g_pvcamLock
            SetBinning(1); // The error might have been caused by not supported BIN or ROI, so do a reset
            this->GetCoreCallback()->OnPropertiesChanged(this); // Notify the MM UI to update the BIN and ROI
            SetErrorText( nRet, "Failed to setup the acquisition" );
            return nRet;
        }
        g_pvcamLock.Unlock();

        const uns32 frameSize = expSize / expTot;

        nRet = postExpSetupInit(frameSize);
        if (nRet != DEVICE_OK)
            return nRet; // Message logged in the failing method

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
        nRet = resizeImageProcessingBuffers();
        if (nRet != DEVICE_OK)
            return nRet; // Message logged in the failing method

        // Reflect all changes that occured in this function in the Device/Property window
        // LW: 2016-06-14 Removing this because it is causing hangs with Single Snap in Dual Camera setup
        //     Some UI properties may not be properly updated though. Review this code should issues arise.
        //this->GetCoreCallback()->OnPropertiesChanged(this);
    }
    catch (const std::bad_alloc& e)
    {
        nRet = DEVICE_OUT_OF_MEMORY;
        LogAdapterMessage( e.what() );
    }
    catch (const std::exception& e)
    {
        nRet = DEVICE_ERR;
        LogAdapterMessage( e.what() );
    }
    catch(...)
    {
        nRet = DEVICE_ERR;
        LogAdapterMessage("Caught error in ResizeImageBufferSingle", false);
    }

    return nRet;
}

int Universal::resizeImageProcessingBuffers()
{
#ifdef PVCAM_3_0_12_SUPPORTED
    // In case of metadata-enabled acquisition we may need temporary processing
    // buffer. This one should be allocated only if needed and freed otherwise.
    if (acqCfgCur_.FrameMetadataEnabled && acqCfgCur_.RoiCount > 1)
    {
        // Metadata-enabled frames with single ROI don't need black-filling,
        // we can use the data of the single ROI directly.
        // With more ROIs we need blackfilling into separate buffer
        const size_t imgDataSz = acqCfgCur_.Rois.ImpliedRoi().ImageRgnWidth() * acqCfgCur_.Rois.ImpliedRoi().ImageRgnHeight() * sizeof(uns16);
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
    if (acqCfgCur_.FrameMetadataEnabled)
    {
        // Allocate the helper structure if needed
        const uns16 roiCount = static_cast<uns16>(acqCfgCur_.RoiCount);
        if (metaFrameStruct_)
        {
            if (metaFrameStruct_->roiCapacity < acqCfgCur_.RoiCount)
            {
                // We need to reallocate the structure for more ROIs
                if (pl_md_release_frame_struct(metaFrameStruct_) != PV_OK)
                {
                    LogPvcamError( __LINE__, "pl_md_release_frame_struct() failed" );
                    return DEVICE_ERR;
                }
                if (pl_md_create_frame_struct_cont(&metaFrameStruct_, roiCount) != PV_OK)
                {
                    LogPvcamError( __LINE__, "pl_md_create_frame_struct_cont() failed" );
                    return DEVICE_OUT_OF_MEMORY;
                }
            }
        }
        else
        {
            if (pl_md_create_frame_struct_cont(&metaFrameStruct_, roiCount) != PV_OK)
            {
                LogPvcamError( __LINE__, "pl_md_create_frame_struct_cont() failed" );
                return DEVICE_OUT_OF_MEMORY;
            }
        }
    }
    else if (metaFrameStruct_)
    {
        if (pl_md_release_frame_struct(metaFrameStruct_) != PV_OK)
        {
            LogPvcamError( __LINE__, "pl_md_release_frame_struct() failed" );
            return DEVICE_ERR;
        }
        metaFrameStruct_ = NULL;
    }
#endif

    // In case of RGB acquisition we need yet another buffer for demosaicing
    if (acqCfgCur_.ColorProcessingEnabled)
    {
        const PvRoi& roi = acqCfgCur_.Rois.ImpliedRoi();
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

int Universal::acquireFrameSeq()
{
    int nRet = DEVICE_OK;

    g_pvcamLock.Lock();
    if (pl_exp_start_seq(hPVCAM_, singleFrameBufRaw_) != PV_OK)
        nRet = LogPvcamError(__LINE__, "pl_exp_start_seq() FAILED");
    g_pvcamLock.Unlock();

    return nRet;
}

int Universal::waitForFrameSeq()
{
    START_METHOD("Universal::waitForFrameSeq");

    int nRet = DEVICE_OK;

    const double estReadTimeSec = getEstimatedMaxReadoutTimeMs() / 1000.0f;
    const MM::MMTime timeout((long)(triggerTimeout_ + estReadTimeSec + 2*GetExposure() * 0.001), (long)(2*GetExposure() * 1000));

    if (!acqCfgCur_.CallbacksEnabled)
    {
        nRet = waitForFrameSeqPolling(timeout);
    }
    else
    {
        nRet = waitForFrameSeqCallbacks(timeout);
    }

    return nRet;
}

int Universal::waitForFrameSeqPolling(const MM::MMTime& timeout)
{
    // This function can be called very often so avoid any frequent
    // logging or other expensive calls.
    rs_bool pvRet    = FALSE;
    int16   pvErr    = 0;
    int16   pvStatus = READOUT_NOT_ACTIVE;
    uns32   pvBytesArrived = 0;

    MM::MMTime       timeElapsed(0,0);
    const MM::MMTime startTime = GetCurrentMMTime();

    // Poll PVCAM for status changes. If we miss the EXPOSURE_IN_PROGRESS we
    // silently skip to check READOUT_IN_PROGRESS, after that we assume that
    // the frame is ready.
    do
    {
        CDeviceUtils::SleepMs(1);
        g_pvcamLock.Lock();
        pvRet = pl_exp_check_status(hPVCAM_, &pvStatus, &pvBytesArrived);
        if (pvRet != PV_OK)
            pvErr = pl_error_code();
        g_pvcamLock.Unlock();
        timeElapsed = GetCurrentMMTime() - startTime;
    }
    while (pvRet == TRUE && pvStatus == EXPOSURE_IN_PROGRESS && timeElapsed < timeout); 

    while (pvRet == TRUE && pvStatus == READOUT_IN_PROGRESS && timeElapsed < timeout)
    {
        CDeviceUtils::SleepMs(1);
        g_pvcamLock.Lock();
        pvRet = pl_exp_check_status(hPVCAM_, &pvStatus, &pvBytesArrived);
        if (pvRet != PV_OK)
            pvErr = pl_error_code();
        g_pvcamLock.Unlock();
        timeElapsed = GetCurrentMMTime() - startTime;
    }

    if (pvRet == TRUE && pvStatus != READOUT_FAILED && timeElapsed < timeout)
    {
        return DEVICE_OK;
    }
    else
    {
        g_pvcamLock.Lock();
        // Abort the acquisition (ignore error if abort fails, just log it)
        if (!pl_exp_abort(hPVCAM_, CCS_HALT))
            LogPvcamError(__LINE__, "waitForFrameSeqPolling(): pl_exp_abort() failed");
        g_pvcamLock.Unlock();
        if (pvRet == FALSE)
            return LogPvcamError(__LINE__, "waitForFrameSeqPolling(): pl_exp_check_cont_status() failed", pvErr);
        if (pvStatus == READOUT_FAILED)
            return LogAdapterError(ERR_FRAME_READOUT_FAILED, __LINE__, "waitForFrameSeqPolling(): pvStatus == READOUT_FAILED");
        if (timeElapsed > timeout)
            return LogAdapterError(ERR_OPERATION_TIMED_OUT, __LINE__, "waitForFrameSeqPolling(): timeElapsed > timeout");
    }
    return DEVICE_ERR;
}

int Universal::waitForFrameSeqCallbacks(const MM::MMTime& timeout)
{
    const bool arrivedInTime = eofEvent_.Wait(static_cast<unsigned int>(timeout.getMsec()));
    if (!arrivedInTime)
    {
        g_pvcamLock.Lock();
        // Abort the acquisition (ignore error if abort fails, just log it)
        if (!pl_exp_abort(hPVCAM_, CCS_HALT))
            LogPvcamError(__LINE__, "waitForFrameSeqCallbacks(): pl_exp_abort() failed");
        g_pvcamLock.Unlock();
        return LogAdapterError(ERR_OPERATION_TIMED_OUT, __LINE__, "waitForFrameSeqCallbacks(): Readout has timed out");
    }
    else
    {
        return DEVICE_OK;
    }
}

int Universal::waitForFrameConPolling(const MM::MMTime& timeout)
{
    rs_bool pvRet    = FALSE;
    int16   pvErr    = 0;
    int16   pvStatus = READOUT_NOT_ACTIVE;
    uns32   pvBytesArrived = 0;
    uns32   pvBufferCnt    = 0;

    MM::MMTime       timeElapsed(0,0);
    const MM::MMTime startTime = GetCurrentMMTime();

    bool bStop = false;

    do
    {
        CDeviceUtils::SleepMs(1);
        g_pvcamLock.Lock();
        pvRet = pl_exp_check_cont_status(hPVCAM_, &pvStatus, &pvBytesArrived, &pvBufferCnt);
        if (pvRet != PV_OK)
            pvErr = pl_error_code();
        g_pvcamLock.Unlock();
        timeElapsed = GetCurrentMMTime() - startTime;
        bStop = pollingThd_->getStop();
    }
    while (pvRet && (pvStatus == EXPOSURE_IN_PROGRESS || pvStatus == READOUT_NOT_ACTIVE) && timeElapsed < timeout && !bStop);

    while (pvRet && (pvStatus == READOUT_IN_PROGRESS) && timeElapsed < timeout && !bStop)
    {
        CDeviceUtils::SleepMs(1);
        g_pvcamLock.Lock();
        pvRet = pl_exp_check_cont_status(hPVCAM_, &pvStatus, &pvBytesArrived, &pvBufferCnt);
        if (pvRet != PV_OK)
            pvErr = pl_error_code();
        g_pvcamLock.Unlock();
        timeElapsed = GetCurrentMMTime()  - startTime;
        bStop = pollingThd_->getStop();
    }

    if (bStop)
    {
        LogAdapterMessage( "waitForFrameConPolling(): Stop called - breaking the loop", true);
        return DEVICE_ERR;
    }
    if (pvRet == TRUE && timeElapsed < timeout && pvStatus != READOUT_FAILED)
    {
        // Because we could miss the FRAME_AVAILABLE and the camera could of gone back to EXPOSURE_IN_PROGRESS and so on depending
        // on how long we could of been stalled in this thread we only check for READOUT_FAILED and assume that because we got here
        // we have one or more frames ready.
        return DEVICE_OK;
    }
    else
    {
        g_pvcamLock.Lock();
        // Abort the acquisition (ignore error if abort fails, just log it)
        if (!pl_exp_abort(hPVCAM_, CCS_HALT))
            LogPvcamError(__LINE__, "waitForFrameConPolling(): pl_exp_abort() failed");
        g_pvcamLock.Unlock();
        if (pvRet == FALSE)
            return LogPvcamError(__LINE__, "waitForFrameConPolling(): pl_exp_check_cont_status() failed", pvErr);
        if (pvStatus == READOUT_FAILED)
            return LogAdapterError(ERR_FRAME_READOUT_FAILED, __LINE__, "waitForFrameConPolling(): pvStatus == READOUT_FAILED");
        if (timeElapsed > timeout)
            return LogAdapterError(ERR_OPERATION_TIMED_OUT, __LINE__, "waitForFrameConPolling(): timeElapsed > timeout");
    }
    return DEVICE_ERR;
}

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
            LogPvcamError(__LINE__, "Unable to decode the metadata-enabled frame");
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
            const PvRoi& roi = acqCfgCur_.Rois.ImpliedRoi();
            
            const uns16 bx = acqCfgCur_.Rois.BinX();
            const uns16 by = acqCfgCur_.Rois.BinY();
            // HACK! When binning is applied the PVCAM (3.1.9.1) does not reflect that
            // in the implied ROI (it keeps reporting binning 1). The recompose function
            // then fails. So we simply "fix" it ourselves.
            metaFrameStruct_->impliedRoi.sbin = bx;
            metaFrameStruct_->impliedRoi.pbin = by;
            // HACK-END
            uns16 offX = 0;
            uns16 offY = 0;
            const uns16 recW = roi.ImageRgnWidth();
            const uns16 recH = roi.ImageRgnHeight();
            // If we are running centroids we should blackfill the destination frame
            // before every use (to erase all previous centroids) because the new frame
            // may contain centroids on different positions.
            if (acqCfgCur_.CentroidsEnabled)
            {
                // With centroids we also need to shift the ROis to their sensor
                // positions (beause with centroids we use full frame for display, not
                // the implied ROI that can change with every frame)
                offX = metaFrameStruct_->impliedRoi.s1 - roi.SensorRgnX();
                offY = metaFrameStruct_->impliedRoi.p1 - roi.SensorRgnY();

                memset(metaBlackFilledBuf_, 0, metaBlackFilledBufSz_);
            }
            if (pl_md_frame_recompose(metaBlackFilledBuf_, offX, offY, recW, recH, metaFrameStruct_) != PV_OK)
            {
                LogPvcamError(__LINE__, "Unable to recompose the metadata-enabled frame");
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

int Universal::abortAcquisitionInternal()
{
    START_METHOD("Universal::abortAcquisitionInternal");
    int nRet = DEVICE_OK;

    // removed redundant calls to pl_exp_stop_cont &
    //  pl_exp_finish_seq because they get called automatically when the thread exits.
    if(isAcquiring_)
    {
        if (acqCfgCur_.CircBufEnabled)
        {
            if (acqCfgCur_.CallbacksEnabled)
            {
                g_pvcamLock.Lock();
                if (!pl_exp_stop_cont( hPVCAM_, CCS_CLEAR ))
                {
                    nRet = DEVICE_ERR;
                    LogPvcamError( __LINE__, "pl_exp_stop_cont() failed" );
                }
                g_pvcamLock.Unlock();
                sequenceModeReady_ = false;
                // Inform the core that the acquisition has finished
                // (this also closes the shutter if used)
                GetCoreCallback()->AcqFinished(this, nRet);
            }
            else
            {
                pollingThd_->setStop(true);
                pollingThd_->wait();
            }
        }
        else
        {
            acqThd_->Pause();
        }
        isAcquiring_ = false;
        eofEvent_.Set();
    }
    return nRet;
}

#ifdef PVCAM_SMART_STREAMING_SUPPORTED
int Universal::sendSmartStreamingToCamera(const std::vector<double>& exposures, int exposureRes)
{
    START_METHOD("Universal::SendSmartStreamingToCamera");

    int nRet = DEVICE_OK;

    const size_t expCount = exposures.size();

    // the SMART streaming exposure values sent to cameras are uns32 while internally we need
    // to be working with doubles
    // allocate and populate regular smart_stream_type structure with values received from the UI
    smart_stream_type smartStreamInts = prmSmartStreamingValues_->Current();
    smartStreamInts.entries = static_cast<uns16>(expCount);

    // We need to propertly fill the PVCAM S.M.A.R.T streaming structure, we keep the
    // exposure values in an array of doubles and milliseconds, however the PVCAM structure
    // needs to be filled based on current exposure resolution selection.
    const double mult = (exposureRes == EXP_RES_ONE_MICROSEC) ? 1000.0 : 1.0;
    for (size_t i = 0; i < expCount; i++)
        smartStreamInts.params[i] = (uns32)(exposures[i] * mult);

    g_pvcamLock.Lock();
    // Send the SMART streaming structure to camera
    nRet = prmSmartStreamingValues_->Set(smartStreamInts);
    if (nRet != DEVICE_OK)
        return nRet;
    nRet = prmSmartStreamingValues_->Apply();
    if (nRet != DEVICE_OK)
        return nRet;
    g_pvcamLock.Unlock();

    return nRet;
}
#endif

int Universal::getPvcamExposureSetupConfig(int16& pvExposureMode, double inputExposureMs, uns32& pvExposureValue)
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

    pvExposureMode = (trigModeValue | eposeOutModeValue);

    // Prepare the exposure value

    if (acqCfgCur_.ExposureRes == EXP_RES_ONE_MICROSEC)
        pvExposureValue = (uns32)(1000 * inputExposureMs);
    else
        pvExposureValue = (uns32)inputExposureMs;

    return nRet;
}

unsigned int Universal::getEstimatedMaxReadoutTimeMs() const
{
    const unsigned int pixCount = GetImageHeight() * GetImageWidth();
    // Sensor read time rough estimation, pixTime = nano-seconds/pixel
    const unsigned int readTimeEstMs = ((camCurrentSpeed_.pixTime * (unsigned long long)pixCount) / 1000000);
    // Transfer over the wire. Let's just assume the slowest interface does 4MB/s
    const unsigned int transferSpeedKBs = 4000; 
    const unsigned int transferTimeMaxMs = (pixCount*GetImageBytesPerPixel()) / transferSpeedKBs;
    return readTimeEstMs + transferTimeMaxMs;
}

#ifdef WIN32
int Universal::refreshPostProcValues()
{
    int16 ppIndx;
    uns32 ppValue;
    for (uns32 i = 0; i < PostProc_.size(); i++)
    {
        ppIndx = (int16)PostProc_[i].GetppIndex();
        if (!pl_set_param(hPVCAM_, PARAM_PP_INDEX, &ppIndx))
        {
            LogPvcamError(__LINE__, "pl_set_param PARAM_PP_INDEX"); 
            return DEVICE_ERR;
        }
        ppIndx = (int16)PostProc_[i].GetpropIndex();
        if (!pl_set_param(hPVCAM_, PARAM_PP_PARAM_INDEX, &ppIndx))
        {
            LogPvcamError(__LINE__, "pl_set_param PARAM_PP_PARAM_INDEX"); 
            return DEVICE_ERR;
        }
        if (!pl_get_param(hPVCAM_, PARAM_PP_PARAM, ATTR_CURRENT, &ppValue))
        {
            LogPvcamError(__LINE__, "pl_get_param PARAM_PP_PARAM ATTR_CURRENT"); 
            return DEVICE_ERR;
        }
        PostProc_[i].SetcurValue(ppValue);
    }
    return DEVICE_OK;
}

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
#endif // WIN32

int Universal::postExpSetupInit(unsigned int frameSize)
{
    int nRet = DEVICE_OK;

    if (prmTempSetpoint_ != NULL && prmTempSetpoint_->IsAvailable())
    {
        nRet = prmTempSetpoint_->Update();
        if (nRet != DEVICE_OK)
            return nRet;
    }
    if (prmExposureTime_ != NULL && prmExposureTime_->IsAvailable())
    {
        nRet = prmExposureTime_->Update();
        if (nRet != DEVICE_OK)
            return nRet;
    }
    if (prmReadoutTime_ != NULL && prmReadoutTime_->IsAvailable())
    {
        nRet = prmReadoutTime_->Update();
        if (nRet != DEVICE_OK)
            return nRet;
    }
    if (prmClearingTime_ != NULL && prmClearingTime_->IsAvailable())
    {
        nRet = prmClearingTime_->Update();
        if (nRet != DEVICE_OK)
            return nRet;
    }
    if (prmPostTriggerDelay_ != NULL && prmPostTriggerDelay_->IsAvailable())
    {
        nRet = prmPostTriggerDelay_->Update();
        if (nRet != DEVICE_OK)
            return nRet;
    }
    if (prmPreTriggerDelay_ != NULL && prmPreTriggerDelay_->IsAvailable())
    {
        nRet = prmPreTriggerDelay_->Update();
        if (nRet != DEVICE_OK)
            return nRet;
    }

    // This will update the CircularBufferFrameCount property in the Device/Property
    // browser. We need to call this because we need to reflect the change in circBufFrameCount_.
    // Also, if we are in manual buffer size mode and binning/ROI is changed during live the
    // circular buffer limits may get adjusted and this needs to be reflected as well.
    nRet = updateCircBufRange(frameSize);

    return nRet;
}

int Universal::updateCircBufRange(unsigned int frameSize)
{
    int nRet = DEVICE_OK;

    if (acqCfgCur_.CircBufSizeAuto)
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
    // LW: 2016-06-20 Commenting out because the call to OnPropertiesChanged now causes
    //     an immediate call of StartSequenceAcquisition() -> results in hang of Live mode.
    //nRet = GetCoreCallback()->OnPropertiesChanged(this);

    return nRet;
}

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

int Universal::applyAcqConfig(bool forceSetup)
{
    int nRet = DEVICE_OK;

    // If we are capturing do not do anything, this function will be called
    // again once the acquisition is restarted.
    if (isAcquiring_)
    {
        return DEVICE_OK;
    }

    // If we are not acquiring, we can configure the camera right away

    // Some changes will require reallocation of our buffers
    // TODO: Better name would be "setupRequired" or similar, setting this flag will
    // force the call to pl_exp_setup() functions
    bool bufferResizeRequired = false;
    // This function is called on several places. After a change in property and
    // upon starting acquisition. If we are not running live mode the configuration
    // will get applied immediately. To avoid re-applying the configuration in
    // StartSequenceAcquisition() we use the following flag.
    // TODO: Write custom comparer for AcqConfig class and use (cfgNew != cfgOld)
    bool configChanged = false;

    // Please note the order of calls in this function may be important. For example,
    // when Centroiding is enabled the Metadata needs to be enabled automatically
    // right after that which may additionally affect buffers. If S.M.A.R.T streaming
    // values change we may need to change the exposure resolution etc.

    // Enabling or disabling color mode requires buffer reallocation
    if (acqCfgNew_.ColorProcessingEnabled != acqCfgCur_.ColorProcessingEnabled)
    {
        configChanged = true;
        bufferResizeRequired = true;
    }

    PvRoiCollection& newRois = acqCfgNew_.Rois;
    const PvRoiCollection& curRois = acqCfgCur_.Rois;

    // NOTE: Some features do not work when turned on together. E.g. Centroids may
    // not work with Binning, or something else may not work when ROI is selected.
    // Do the validation FIRST, BEFORE sending any other property to the camera.

    // VALIDATE the ROI dimensions. This is especially useful when user is trying to apply
    // ROI that was drawn on a different image. Or drawn on an image acquired with 1x1 binning but
    // the current configuration uses 2x2 binning. We cannot easily detect that because uM
    // won't tell us what binning we should use when applying ROI.
    if (!newRois.IsValid(camSerSize_, camParSize_))
    {
        acqCfgNew_ = acqCfgCur_; // New settings not accepted, reset it back to previous state
        return LogAdapterError( ERR_ROI_DEFINITION_INVALID, __LINE__,
            "Universal::applyAcqConfig() ROI definition is invalid for current camera configuration" );
    }

    // VALIDATE Centroids (PrimeLocate), so far it works with 1x1 binning only
    if (acqCfgNew_.CentroidsEnabled && acqCfgNew_.Rois.BinX() > 1 && acqCfgNew_.Rois.BinY() > 1)
    {
        acqCfgNew_ = acqCfgCur_; // New settings not accepted, reset it back to previous state
        return LogAdapterError( ERR_BINNING_INVALID, __LINE__,
            "Universal::applyAcqConfig() Centroids (PrimeLocate) is not supported with binning." );
    }
    // Centroids also do not work with user defined Multiple ROIs
    if (acqCfgNew_.CentroidsEnabled && acqCfgNew_.Rois.Count() > 1)
    {
        acqCfgNew_ = acqCfgCur_; // New settings not accepted, reset it back to previous state
        return LogAdapterError( ERR_ROI_DEFINITION_INVALID, __LINE__,
            "Universal::applyAcqConfig() Centroids (PrimeLocate) is not supported with multiple ROIs." );
    }

    // Change in ROI or binning requires buffer reallocation
    if (!newRois.Equals(curRois))
    {
        // If binning has changed adjust the coordinates to the binning factor
        newRois.AdjustCoords();
        configChanged = true;
        bufferResizeRequired = true;
    }

    // Multi-ROIs require metadata
    if (acqCfgNew_.Rois.Count() > 1)
        if (!acqCfgCur_.FrameMetadataEnabled)
            acqCfgNew_.FrameMetadataEnabled = true;

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
    }

    // Update the "output" ROI count so the md_frame structure can be reinitialized if needed
    if (acqCfgNew_.CentroidsEnabled)
        acqCfgNew_.RoiCount = acqCfgNew_.CentroidsCount;
    else
        acqCfgNew_.RoiCount = static_cast<int>(acqCfgNew_.Rois.Count());

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

    // Clear cycles
    if (acqCfgNew_.ClearCycles != acqCfgCur_.ClearCycles)
    {
        configChanged = true;
        nRet = prmClearCycles_->SetAndApply(static_cast<uns16>(acqCfgNew_.ClearCycles));
        if (nRet != DEVICE_OK)
        {
            acqCfgNew_ = acqCfgCur_; // New settings not accepted, reset it back to previous state
            return nRet; // Error logged in SetAndApply()
        }
        // Workaround: When it changes we force buffer reinitialization (which results
        // in pl_exp_setup_seq/cont getting called, which applies script, which then
        // forces the camera to update the "post-setup parameters" and this
        // results in GUI getting immediately updated with correct values for timing params.
        bufferResizeRequired = true;
    }

    // Clear mode
    if (acqCfgNew_.ClearMode != acqCfgCur_.ClearMode)
    {
        configChanged = true;
        nRet = prmClearMode_->SetAndApply(acqCfgNew_.ClearMode);
        if (nRet != DEVICE_OK)
        {
            acqCfgNew_ = acqCfgCur_; // New settings not accepted, reset it back to previous state
            return nRet; // Error logged in SetAndApply()
        }
        // Workaround: When it changes we force buffer reinitialization (which results
        // in pl_exp_setup_seq/cont getting called, which applies script, which then
        // forces the camera to update the "post-setup parameters" and this
        // results in GUI getting immediately updated with correct values for timing params.
        bufferResizeRequired = true;
    }

    // Debayering algorithm selection
    if (acqCfgNew_.DebayerAlgMaskAuto && acqCfgNew_.ColorProcessingEnabled)
    {
        // TODO: We need to have per-roi mask selection :(
        const int xPos = acqCfgNew_.Rois.At(0).SensorRgnX();
        const int yPos = acqCfgNew_.Rois.At(0).SensorRgnY();
        acqCfgNew_.DebayerAlgMask = selectDebayerAlgMask(xPos, yPos, camCurrentSpeed_.colorMask);
        // Config changes only if the current and previous mask actually differs (see below)
    }
    if (acqCfgNew_.DebayerAlgMask != acqCfgCur_.DebayerAlgMask)
    {
        configChanged = true;
        // TODO: With Multi-ROI we need to debayer each ROI individually and with individual mask,
        //       remove this and set the OrderIndex for every ROI.
        debayer_.SetOrderIndex(acqCfgNew_.DebayerAlgMask);
    }
    if (acqCfgNew_.DebayerAlgInterpolation != acqCfgCur_.DebayerAlgInterpolation)
    {
        configChanged = true;
        debayer_.SetAlgorithmIndex(acqCfgNew_.DebayerAlgInterpolation);
    }

    // Iterate over the trigger "last mux map" and check if any signal has changed the Mux value
    for(std::map<int, int>::iterator it = acqCfgNew_.TrigTabLastMuxMap.begin(); it != acqCfgNew_.TrigTabLastMuxMap.end(); it++)
    {
        const int32 trigSig = it->first;
        const uns8  muxValNew  = static_cast<uns8>(it->second);
        const uns8  muxValCur  = static_cast<uns8>(acqCfgCur_.TrigTabLastMuxMap[trigSig]);
        if (muxValNew != muxValCur)
        {
            configChanged = true;
            // Set the PARAM_TRIGTAB_SIGNAL we want to work with first (e.g. 0-Expose Out)
            nRet = prmTrigTabSignal_->SetAndApply(trigSig);
            if (nRet != DEVICE_OK)
            {
                acqCfgNew_ = acqCfgCur_; // New settings not accepted, reset it back to previous state
                return nRet; // Error logged in SetAndApply()
            }
            // Set the PARAM_LAST_MUXED_SIGNAL, e.g. to 4
            nRet = prmLastMuxedSignal_->SetAndApply(muxValNew);
            if (nRet != DEVICE_OK)
            {
                acqCfgNew_ = acqCfgCur_; // New settings not accepted, reset it back to previous state
                return nRet; // Error logged in SetAndApply()
            }
        }
    }

    if (acqCfgNew_.PMode != acqCfgCur_.PMode)
    {
        configChanged = true;
        nRet = prmPMode_->SetAndApply(acqCfgNew_.PMode);
        if (nRet != DEVICE_OK)
        {
            acqCfgNew_ = acqCfgCur_; // New settings not accepted, reset it back to previous state
            return nRet; // Error logged in SetAndApply()
        }
        // Workaround: When PMODE changes we force buffer reinitialization (which results
        // in pl_exp_setup_seq/cont getting called, which applies script, which then
        // forces the camera to update the PARAM_TEMP_SETPOINT for DELTA LS and this
        // results in GUI getting immediately updated with correct setpoint.
        bufferResizeRequired = true;
    }

    bool speedReset = false;
    if (acqCfgNew_.PortId != acqCfgCur_.PortId)
    {
        configChanged = true;
        nRet = prmReadoutPort_->SetAndApply(acqCfgNew_.PortId);
        if (nRet != DEVICE_OK)
        {
            acqCfgNew_ = acqCfgCur_; // New settings not accepted, reset it back to previous state
            return nRet; // Error logged in SetAndApply()
        }

        // Port has changed so we need to reset the speed
        // Read the available speeds for this port from our speed table
        std::vector<std::string> spdChoices;
        const uns32 curPort = prmReadoutPort_->Current();
        std::map<int16, SpdTabEntry>::iterator i = camSpdTable_[curPort].begin();
        for(; i != camSpdTable_[curPort].end(); ++i)
            spdChoices.push_back(i->second.spdString);
        // Set the allowed readout rates
        SetAllowedValues(g_ReadoutRate, spdChoices);

        // Set the current speed to the default rate, this will cause the speed to be reset as well call.
        acqCfgNew_.SpeedIndex = camSpdTable_[curPort][0].portDefaultSpdIdx;

        // Since Port has changed we need to reset the speed, which in turn resets gain
        speedReset = true;
        bufferResizeRequired = true;
    }

    if (acqCfgNew_.SpeedIndex != acqCfgCur_.SpeedIndex || speedReset)
    {
        configChanged = true;
        // TODO: This is not fully implemeneted, we still handle port/gain changes 
        // directly via properties. We should move it here.
        nRet = prmSpdTabIndex_->SetAndApply((int16)acqCfgNew_.SpeedIndex);
        if (nRet != DEVICE_OK)
        {
            acqCfgNew_ = acqCfgCur_; // New settings not accepted, reset it back to previous state
            return nRet; // Error logged in SetAndApply()
        }

        // Find the actual speed definition from the speed table
        SpdTabEntry spd;
        const int32 curPort = prmReadoutPort_->Current();
        for (int16 i = 0; i < (int16)camSpdTable_[curPort].size(); ++i)
        {
            if (camSpdTable_[curPort][i].spdIndex == acqCfgNew_.SpeedIndex)
            {
                spd = camSpdTable_[curPort][i];
                break;
            }
        }
        camCurrentSpeed_ = spd;

        // When speed changes, the Gain range may need to be updated
        vector<string> gainChoices;
        for (int16 i = spd.gainMin; i <= spd.gainMax; i++)
            gainChoices.push_back(spd.gainNameMapReverse.at(i));
        SetAllowedValues(MM::g_Keyword_Gain, gainChoices);

        // If the current gain is applicable for the new speed we want to restore it.
        // Change in speed automatically resets GAIN in PVCAM, so we want to preserve it.
        // We can use the prmGainIndex_->Current() because it still contains the previous
        // cached value (we didn't call Update/Apply yet)
        int16 curGain = prmGainIndex_->Current();
        if ( curGain < spd.gainMin || curGain > spd.gainMax )
            curGain = spd.gainDef; // The new speed does not support this gain index, so we reset it to the first available
        // Re-apply the gain (fixes issues with some cameras that do not reset the gain themselves)
        nRet = prmGainIndex_->SetAndApply(curGain);
        if (nRet != DEVICE_OK)
        {
            acqCfgNew_ = acqCfgCur_; // New settings not accepted, reset it back to previous state
            return nRet; // Error logged in SetAndApply()
        }

        // When speed changes the ADC offset may change as well, so update it
        nRet = prmAdcOffset_->Update();
        if (nRet != DEVICE_OK)
        {
            acqCfgNew_ = acqCfgCur_; // New settings not accepted, reset it back to previous state
            return nRet; // Error logged in Update()
        }
        // TODO: Should we try to set the previous ADC offset? This will fail on modern
        // cameras as they do not allow the ADC offset to be changed.
        // Set both configurations to the same value to avoid the setter to be called later.
        acqCfgNew_.AdcOffset = prmAdcOffset_->Current();
        acqCfgCur_.AdcOffset = prmAdcOffset_->Current();
        // Speed may cause bit depth change, so buffer reallocation is recommended
        bufferResizeRequired = true;
    }

    if (acqCfgNew_.AdcOffset != acqCfgCur_.AdcOffset)
    {
        configChanged = true;
        nRet = prmAdcOffset_->SetAndApply((int16)acqCfgNew_.AdcOffset);
        if (nRet != DEVICE_OK)
        {
            acqCfgNew_ = acqCfgCur_; // New settings not accepted, reset it back to previous state
            return nRet; // Error logged in SetAndApply()
        }
    }

    if (acqCfgNew_.CircBufEnabled != acqCfgCur_.CircBufEnabled)
    {
        configChanged = true;
        bufferResizeRequired = true;
    }
    if (acqCfgNew_.CircBufSizeAuto != acqCfgCur_.CircBufSizeAuto)
    {
        configChanged = true;
        bufferResizeRequired = true;
    }

    if (acqCfgNew_.CallbacksEnabled != acqCfgCur_.CallbacksEnabled)
    {
        configChanged = true;
        bufferResizeRequired = true;
        g_pvcamLock.Lock();
        if (acqCfgNew_.CallbacksEnabled)
        {
            if (pl_cam_register_callback_ex3(hPVCAM_, PL_CALLBACK_EOF, PvcamCallbackEofEx3, this) != PV_OK)
            {
                acqCfgNew_ = acqCfgCur_; // New settings not accepted, reset it back to previous state
                nRet = LogPvcamError(__LINE__, "pl_cam_register_callback_ex3() failed");
                g_pvcamLock.Unlock();
                return nRet;
            }
        }
        else
        {
            if (pl_cam_deregister_callback(hPVCAM_, PL_CALLBACK_EOF) != PV_OK)
            {
                acqCfgNew_ = acqCfgCur_; // New settings not accepted, reset it back to previous state
                nRet = LogPvcamError(__LINE__, "pl_cam_deregister_callback() failed");
                g_pvcamLock.Unlock();
                return nRet;
            }
        }
        g_pvcamLock.Unlock();
    }

    // Change in exposure only means to reconfigure the acquisition
    if (acqCfgNew_.ExposureMs != acqCfgCur_.ExposureMs)
    {
        configChanged = true;
        bufferResizeRequired = true;
    }
    // The exposure time value that will be used to decide which exposure
    // resolution to set, this depends on S.M.A.R.T streaming as well.
    double exposureTimeDecisiveMs = acqCfgNew_.ExposureMs;

    // S.M.A.R.T streaming is quite tricky. We want to have it active only for Live mode because
    // it does not work for single snaps. For single snaps (a sequence of one frame) we want to
    // use the original exposure time so we need to temporarily suppress the S.M.A.R.T streaming.

    bool doReconfigureSmart = false;
    if (prmSmartStreamingEnabled_->IsAvailable())
    {
        if (acqCfgNew_.AcquisitionType != acqCfgCur_.AcquisitionType)
        {
            doReconfigureSmart = true;
            // No GUI property changed thus no need to set configChanged
        }
        if (acqCfgNew_.SmartStreamingEnabled != acqCfgCur_.SmartStreamingEnabled)
        {
            doReconfigureSmart = true;
            configChanged = true;
        }
        if (acqCfgNew_.SmartStreamingExposures != acqCfgCur_.SmartStreamingExposures)
        {
            doReconfigureSmart = true;
            configChanged = true;
        }
    }
    if (doReconfigureSmart)
    {
        // Check if we have correct amount of exposures
        if (acqCfgNew_.SmartStreamingExposures.size() > prmSmartStreamingValues_->Max().entries)
        {
            acqCfgNew_ = acqCfgCur_; // New settings not accepted, reset it back to previous state
            return LogAdapterError( DEVICE_CAN_NOT_SET_PROPERTY,
                __LINE__, "Universal::applyAcqConfig() Too many S.M.A.R.T exposures entered" );
        }
        // S.M.A.R.T streaming is active in Live mode only
        acqCfgNew_.SmartStreamingActive =
            (acqCfgNew_.AcquisitionType == AcqType_Live) && (acqCfgNew_.SmartStreamingEnabled);
        // Enable or disable the S.M.A.R.T streaming in PVCAM
        nRet = prmSmartStreamingEnabled_->SetAndApply(acqCfgNew_.SmartStreamingActive ? TRUE : FALSE);
        if (nRet != DEVICE_OK)
        {
            acqCfgNew_ = acqCfgCur_; // New settings not accepted, reset it back to previous state
            return nRet; // Error logged in SetAndApply()
        }
    }


    // If the S.M.A.R.T streaming is active we need to check the exposure values
    // and possibly switch the camera exposure resolution accordingly (see below)
    if (acqCfgNew_.SmartStreamingActive)
        exposureTimeDecisiveMs = *std::max_element(
            acqCfgNew_.SmartStreamingExposures.begin(), acqCfgNew_.SmartStreamingExposures.end());

    // Now we know for sure wheter S.M.A.R.T is going to be used or not, so we have
    // the exposure time that should be used to select the right exposure resolution

    // If the exposure is smaller than 'microsecResMax' milliseconds (MM works in milliseconds but uses float type)
    // we switch the camera to microseconds so user can type 59.5 and we send 59500 to PVCAM.
    if (exposureTimeDecisiveMs < (microsecResMax_/1000.0) && microsecResSupported_)
        acqCfgNew_.ExposureRes = EXP_RES_ONE_MICROSEC;
    else
        acqCfgNew_.ExposureRes = EXP_RES_ONE_MILLISEC;

    // The exposure resolution is switched automatically and depends on two features that are handled above:
    // The generic exposure value or the highest S.M.A.R.T streaming value. Because of that the order
    // of the calls matters and we need to switch the exposure resolution here, once we know whether
    // we are running S.M.A.R.T, what are the S.M.A.R.T exposures or whether we run simple acquisition.
    if (acqCfgNew_.ExposureRes != acqCfgCur_.ExposureRes)
    {
        // If the PARAM_EXP_RES_INDEX is not available, we use the exposure number as it is.
        if (prmExpResIndex_->IsAvailable())
        {
            nRet = prmExpResIndex_->SetAndApply(static_cast<uns16>(acqCfgNew_.ExposureRes));
            if (nRet != DEVICE_OK)
            {
                acqCfgNew_ = acqCfgCur_; // New settings not accepted, reset it back to previous state
                return nRet; // Error logged in SetAndApply()
            }
        }
    }

    // Finally finish S.M.A.R.T reconfiguration because at this point we know the exposure
    // resolution that is important for S.M.A.R.T streaming exposure values conversion
    if (doReconfigureSmart)
    {
        nRet = sendSmartStreamingToCamera(acqCfgNew_.SmartStreamingExposures, acqCfgNew_.ExposureRes);
        if (nRet != DEVICE_OK)
        {
            acqCfgNew_ = acqCfgCur_; // New settings not accepted, reset it back to previous state
            return nRet; // Error logged in SetAndApply()
        }
        bufferResizeRequired = true;
    }

    // If the acquisition type changes (Snap vs Live) we need to reconfigure buffer which
    // in turn reconfigures the camera with pl_exp_setup_xxx() calls.
    if (acqCfgNew_.AcquisitionType != acqCfgCur_.AcquisitionType)
    {
        bufferResizeRequired = true;
    }

    // The new properties have been applied. Since we now reinitialize the buffers
    // immediately the acqCfgCur_ must already contain correct configuration.
    acqCfgCur_ = acqCfgNew_;

    if (bufferResizeRequired || forceSetup)
    {
        // Automatically prepare the acquisition. This helps with following problem:
        // Some parameters (PARAM_TEMP_SETPOINT, PARAM_READOUT_TIME) update their values only
        // after the pl_exp_setup is called. This means that the GUI would not be updated immediately
        // but after the user presses Snap or Live. (for example on Delta LS when user changes PMODE
        // the temperature setpoint is updated, however without calling pl_exp_setup the GUI would
        // keep displaying the incorrect value)
        // See postExpSetupInit()
        // We prepare the acquisition based on previous configuration. If user was snapping single
        // frames, we prepare the single frame, if user was running live, we prepare live.
        if (acqCfgNew_.AcquisitionType == AcqType_Live)
        {
            nRet = resizeImageBufferContinuous();
            sequenceModeReady_ = true;
        }
        else
        {
            nRet = resizeImageBufferSingle();
            singleFrameModeReady_ = true;
        }
        if (nRet != DEVICE_OK)
        {
            sequenceModeReady_ = false;
            singleFrameModeReady_ = false;
            return nRet;
        }

        GetCoreCallback()->InitializeImageBuffer(1, 1, GetImageWidth(), GetImageHeight(), GetImageBytesPerPixel());
        callPrepareForAcq_ = true;
    }

    // Update the Device/Property browser UI
    // LW: 2016-06-20 We may need to comment this out as well because the call to
    // OnPropertiesChanged often causes an immediate call to StartSequenceAcquisition()
    // which in turn results in hang of Live mode. (happens in uM 2.0, not 1.4)
    if (configChanged || forceSetup)
        nRet = this->GetCoreCallback()->OnPropertiesChanged(this);

    return nRet;
}


//=============================================================================
//============================================================== PRIVATE STATIC


#ifdef PVCAM_CALLBACKS_SUPPORTED
void Universal::PvcamCallbackEofEx3(PFRAME_INFO /*pFrameInfo*/, void* pContext)
{
    // We don't need the FRAME_INFO because we will get it in FrameAcquired via get_latest_frame
    Universal* pCam = (Universal*)pContext;
    pCam->FrameAcquired();
    // Do not call anything else here, handle it in the Universal class.
}
#endif // PVCAM_CALLBACKS_SUPPORTED
