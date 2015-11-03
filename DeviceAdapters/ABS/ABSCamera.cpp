#include "ABSCamera.h"
#include <cstdio>
#include <string>
#include <math.h>
#include <sstream>
#include <algorithm>
#include <iostream>
#include <map>

#include "shlwapi.h"
#pragma comment( lib, "shlwapi.lib")
#include "abscommontools.h"
#include "stringtools.h"
#include "ccmfilestd.h"
using namespace std;

#include "AutoTimeMeasure.h"

const double CABSCamera::nominalPixelSizeUm_ = 1.0;
double g_IntensityFactor_ = 1.0;
// External names used used by the rest of the system
// to load particular device from the "DemoCamera.dll" library
const char* g_CameraName = "ABSCam";
#ifdef _AMD64_
const char* g_ApiDllName = "CamUsb_Api64_hal.dll";
#else
const char* g_ApiDllName = "CamUsb_Api_hal.dll";
#endif



// --------------------------- additional supported features -------------------

// frame rate
const char* const g_Keyword_Framerate = "Framerate (camera intern)";
const char* const g_Framerate_FreeRun = "free run";

// bit deep
const char* const g_Keyword_BitDepth = "BitDepth";
const char* const g_BitDepth_08 = "8";
const char* const g_BitDepth_10 = "10";
const char* const g_BitDepth_12 = "12";
const char* const g_BitDepth_14 = "14";
const char* const g_BitDepth_16 = "16";
const char* const g_BitDepth_Default = g_BitDepth_08;

// gain channels
const char* const g_Keyword_Gain_Red = "Gain Red";
const char* const g_Keyword_Gain_Green = "Gain Green";
const char* const g_Keyword_Gain_Green1 = "Gain Green1";
const char* const g_Keyword_Gain_Green2 = "Gain Green2";
const char* const g_Keyword_Gain_Blue = "Gain Blue";
const char* const g_Keyword_Gain_Extra = "Extra Gain";
const char* const g_Gain_Extra_x1 = "x01";
const char* const g_Gain_Extra_x2 = "x02";
const char* const g_Gain_Extra_x4 = "x04";
const char* const g_Gain_Extra_x8 = "x08";
const char* const g_Gain_Extra_x16 = "x16";

// exposure
const char* const g_Keyword_Exposure_LongTime = "Exposure (allow long time)";
const char* const g_Exposure_LongTime_Off = "Off";
const char* const g_Exposure_LongTime_On = "On";

// auto exposure
const char* const g_Keyword_AutoExposure = "Automatic exposure";
const char* const g_Keyword_AutoExposureOptions = "Automatic exposure options";
const char* const g_AutoExposure_Off = "Off";
const char* const g_AutoExposure_On = "On";
const char* const g_AutoExposure_OptNone = "none";
const char* const g_AutoExposure_Opt50Hz = "anti flicker 50Hz";
const char* const g_AutoExposure_Opt60Hz = "anti flicker 60Hz";

// edge enhancement
const char* const g_Keyword_Sharpen = "Sharpen";
const char* const g_Sharpen_Off = "Off";
const char* const g_Sharpen_Level1 = "Level 1";
const char* const g_Sharpen_Level2 = "Level 2";
const char* const g_Sharpen_Level3 = "Level 3";

// bayer mode
const char* const g_Keyword_BayerDemosaic = "Bayer Demosaic";
const char* const g_BayerDemosaic_Fast = "fast";
const char* const g_BayerDemosaic_Quality = "quality";


// white balance one push
const char* const g_Keyword_WhiteBalance = "Whitebalance";
const char* const g_WhiteBalance_OnePush = "One Push";

// blacklevel
const char* const g_Keyword_Blacklevel = "Blacklevel";
const char* const g_BlacklevelOn = "On";
const char* const g_BlacklevelOff = "Off";

// standard resolutions
const char* const g_Keyword_StdResolution = "Standard Resolutions";

const char* const g_Keyword_CCM = "Color correction";
const char* const g_CCM_Off = "Off";


// profiles
const char* const g_Keyword_ProfilesLoad = "Profiles Load";
const char* const g_Keyword_ProfilesSave = "Profiles Save";

// gamma correction
const char* const g_Keyword_Gamma = "Gamma correction";

// saturation
const char* const g_Keyword_Saturation = "Saturation";

// saturation
const char* const g_Keyword_Hue = "Hue";

// shading correction
const char* const g_Keyword_ShadingCorrection = "Shading correction";
const char* const g_Keyword_ShadingCorrection_Setup = "Shading correction setup";
const char* const g_ShadingCorrection_Off = " Off";
const char* const g_ShadingCorrection_Both = " Both";
const char* const g_ShadingCorrection_DarkRef = " Dark reference";
const char* const g_ShadingCorrection_WhiteRef = " White reference";
const char* const g_ShadingCorrection_CreateDarkRef = " Create dark reference";
const char* const g_ShadingCorrection_CreateWhiteRef= " Create white reference";


// constants for naming pixel types (allowed values of the "PixelType" property)
const char* g_PixelType_8bit = " 8bit";
const char* g_PixelType_16bit = " 16bit";
const char* g_PixelType_32bitRGB = " 32bitRGB";
const char* g_PixelType_64bitRGB = " 64bitRGB";
const char* g_PixelType_32bit = " 32bit"; // floating point greyscale

// readout mode
const char* g_ReadoutMode_Automatic  = " Automatic";
const char* g_ReadoutMode_Continuous = " Continuous";
const char* g_ReadoutMode_TriggeredSW = " Triggered SW";
const char* g_ReadoutMode_TriggeredHW = " Triggered HW";
const unsigned char MODE_AUTOMATIC = 0xF0;

// trigger ports
const char* g_Keyword_TriggerInport = "I/O Trigger inport";
const char* g_Keyword_TriggerPolarity = "I/O Trigger polarity";
const char* g_Keyword_TriggerDelay = "I/O Trigger delay (ms)";
const char* g_Keyword_StrobeOutport = "I/O Strobe outport";
const char* g_Keyword_StrobePolarity = "I/O Strobe polarity";
const char* g_Polarity_LowActive = " low acitve";
const char* g_Polarity_HighActive = " high acitve";
const char* g_IOPort_None = " none";


// ------------------------------ Macros --------------------------------------
//
// return the error code of the function instead of Successfull or Failed!!!
#define GET_RC(_func, _dev) ((!_func) ? CamUSB_GetLastError((u08)_dev) : retOK)

#define PROFILE_NONE ""
#define PROFILE_SETTINGSNAME "DefaultProfile"
#define PROFILE_DEFAULTNAME "lastUsed.profile"
#define PROFILE_PREVIEWNAME "preview.profile"
#define PROFILE_QUALITITYNAME "qualitity.profile"

///////////////////////////////////////////////////////////////////////////////
// CABSCamera implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~

/**
* CABSCamera constructor.
* Setup default all variables and create device properties required to exist
* before intialization. In this case, no such properties were required. All
* properties will be created in the Initialize() method.
*
* As a general guideline Micro-Manager devices do not access hardware in the
* the constructor. We should do as little as possible in the constructor and
* perform most of the initialization in the Initialize() method.
*/
CABSCamera::CABSCamera() :
CCameraBase<CABSCamera> (),
dPhase_(0),
initialized_(false),
readoutUs_(1.0),
readoutMode_( g_ReadoutMode_Automatic ),
bitDepth_(8),
roiX_(0),
roiY_(0),
sequenceStartTime_(0),
binSize_(1),
cameraCCDXSize_(512),
cameraCCDYSize_(512),
nComponents_(1),
triggerDevice_(""),
dropPixels_(false),
saturatePixels_(false),
fractionOfPixelsToDropOrSaturate_(0.002)
,deviceNo_( -1 )
,colorCamera_( false )
,qwSupportedFunctions_(0)
,m_BayerDemosaicMode( g_BayerDemosaic_Fast )
,bMirrorX_(false)
,bMirrorY_(false)
,bSwapXY_(false)
,triggerPortName_( g_IOPort_None )
,triggerPortPolarity_( g_Polarity_HighActive )
,triggerPortDelay_( 0 )
,strobePortName_( g_IOPort_None )
,strobePortPolarity_( g_Polarity_HighActive )
,imageCounter_(0)
,framerate_(0.0)
,exposureLongTime_( g_Exposure_LongTime_Off )
//,thread_(0)
//,thd_(0)
,abortGetImageFired_(false)
,bFirstExposureLongTimeImage_(false)
,stopOnOverflow_(false)
,numImages_(LONG_MAX)
,interval_ms_(0)
{
  memset(testProperty_,0,sizeof(testProperty_));

  // call the base class method to set-up default error codes/messages
  InitializeDefaultErrorMessages();
  readoutStartTime_ = GetCurrentMMTime();

  // release thread object of base class => to allocate my own one
  //thread_ = new CABSCameraSequenceThread(this);
//  thd_ = new CABSCameraSequenceThread(this);

  // clear camera device id string
  cameraDeviceID_.clear();

  // create a pre-initialization property and list all the available cameras
  // Spot sends us the Model Name + (serial number)
  CPropertyAction *pAct = new CPropertyAction (this, &CABSCamera::OnCameraSelection);
  // create property but add values on request
  CreateProperty("Camera", "", MM::String, false, pAct, true);
}

/**
* CABSCamera destructor.
* Calls Shutdown in case it wasn't explicity called already.
*/
CABSCamera::~CABSCamera()
{
   Shutdown();
}

//-----------------------------------------------------------------------------

int CABSCamera::GetPropertyString(const char* name, std::string & value)
{
  char buf[MM::MaxStrLength] = {0};
  int ret = GetProperty( name, (char*) buf);

  if (ret != DEVICE_OK)
    value.clear();
  else
    value = std::string( (char*) buf );
  return ret;
}

/**
* Obtains device name.
* Required by the MM::Device API.
*/
void CABSCamera::GetName(char* name) const
{
  // We just return the name we use for referring to this
  // device adapter.
  CDeviceUtils::CopyLimitedString(name, g_CameraName);
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
int CABSCamera::Initialize()
{
  int nRet;
  ostringstream serialNumberStream;

  if ( isInitialized() )
    return DEVICE_OK;

  if ( false == isApiDllAvailable() )
    return DEVICE_NATIVE_MODULE_FAILED;

  u32 serialNumber = NO_SERIAL_NUMBER;
  u08 platformID = CPID_NONE;

  qwSupportedFunctions_ = 0;

  // if id is set try to select the correct camera
  if ( 0 < cameraDeviceID().size() )
  {
    bool cameraFound = false;
    // get list of available devices
    CCameraList cCameraList;
    getAvailableCameras( cCameraList );

    if ( cCameraList.size() == 0)
      return DEVICE_NOT_CONNECTED;

    // check if the selected camera is at the list
    CCameraList::iterator iter = cCameraList.begin();
    while ( iter != cCameraList.end() )
    {
      if ( cameraDeviceID() == buildCameraDeviceID( iter->sVersion.dwSerialNumber, getDeviceNameString( iter->sVersion ) ).c_str() )
      {
        serialNumber = iter->sVersion.dwSerialNumber;
        platformID = iter->sVersion.bPlatformID;
        cameraFound = true;
        break;
      }
      iter++;
    }
  }

  // open camera device
  u32 dwRC = retOK;

  // init camera and setup device number (sCamInit->nDevNr)
  S_CAMERA_INIT sCamInit = {0};
  sCamInit.dwSerialnumber     = serialNumber;
  sCamInit.dwPlatformID       = platformID;
  sCamInit.dwFirmwareOptions  = FWOPT_AUTOMATIC;
  if ( FALSE == CamUSB_InitCameraExS( &sCamInit ) )
  {
    dwRC = CamUSB_GetLastError( GLOBAL_DEVNR );
    if (retOK == dwRC )
      dwRC = CamUSB_GetLastError( sCamInit.nDevNr );
  }

  if ( FALSE == IsNoError( dwRC ) )
  {
    return convertApiErrorCode( dwRC, __FUNCTION__ );
  }
  else // remember device number for later access
  {
    setDeviceNo( sCamInit.nDevNr );
  }

  // camera device is now open do the right things

  // read camera information
  cameraVersion_.dwStructSize = sizeof( S_CAMERA_VERSION );
  dwRC = GET_RC( CamUSB_GetCameraVersion( &cameraVersion_, deviceNo() ), deviceNo() );
  if ( FALSE == IsNoError(dwRC) )
  { nRet = convertApiErrorCode( dwRC, __FUNCTION__ ); goto Initialize_Done; }

  // disbale heartbeat
  u32 dwValue = 0;
  dwRC = GET_RC( CamUSB_HeartBeatCfg( 0x80000001, dwValue, deviceNo() ), deviceNo() );

  // basic setup
  // check if it is a color camera
  {
    // get function ID mask of all supported functions
    CamUSB_GetCameraFunctions( &qwSupportedFunctions_, deviceNo() );


    // set default pixeltype
    CamUSB_SetPixelType( PIX_MONO8, deviceNo() );

    S_RESOLUTION_CAPS * resolutionCaps = 0;
    u32 rc = getCap( FUNC_RESOLUTION, (void* &) resolutionCaps );
    if ( IsNoError( rc) )
    {
      setColor( ((resolutionCaps->wSensorType & ST_COLOR) == ST_COLOR) );

      cameraCCDXSize_ = resolutionCaps->wVisibleSizeX;
      binSize_ = 1;
      cameraCCDYSize_ = resolutionCaps->wVisibleSizeY;

      last16BitDepth_ = CDeviceUtils::ConvertToString( (long) resolutionCaps->wMaxBPP );

      // use full resolution
      CamUSB_SetCameraResolution(0, 0, resolutionCaps->wVisibleSizeX, resolutionCaps->wVisibleSizeY, 0, 0, 1, deviceNo() );

      framerate_ = 0.0;
      UpdateProperty( g_Keyword_Framerate );

      // use RGB as default for color camera's
      if ( isColor() )
        CamUSB_SetPixelType( PIX_BGRA8_PACKED, deviceNo() );
    }

    if (resolutionCaps)
      delete [] resolutionCaps;
  }



  // set property list
  // -----------------
  // Name
  nRet = CreateProperty(MM::g_Keyword_Name, g_CameraName, MM::String, true);
  if (DEVICE_OK != nRet)
  { goto Initialize_Done; }

  // Description
  nRet = CreateProperty(MM::g_Keyword_Description, "ABS GmbH UK11xx Camera Device Adapter", MM::String, true);
  if (DEVICE_OK != nRet)
  { goto Initialize_Done; }

  // CameraName
  nRet = CreateProperty(MM::g_Keyword_CameraName, getDeviceNameString().c_str() , MM::String, true);
  assert(nRet == DEVICE_OK);

  // CameraID
  serialNumberStream << hex << cameraVersion_.dwSerialNumber;
  nRet = CreateProperty(MM::g_Keyword_CameraID, serialNumberStream.str().c_str(), MM::String, true);
  assert(nRet == DEVICE_OK);

  // binning
  CPropertyAction *pAct = new CPropertyAction (this, &CABSCamera::OnBinning);
  nRet = CreateProperty(MM::g_Keyword_Binning, "1", MM::Integer, false, pAct);
  assert(nRet == DEVICE_OK);
  nRet = SetAllowedBinning();
  if (nRet != DEVICE_OK)
  { goto Initialize_Done; }
  else SetProperty(MM::g_Keyword_Binning, "1");

  // pixel type
  pAct = new CPropertyAction (this, &CABSCamera::OnPixelType);
  nRet = CreateProperty(MM::g_Keyword_PixelType, g_PixelType_8bit, MM::String, false, pAct);
  assert(nRet == DEVICE_OK);
  nRet = setAllowedPixelTypes();
  if (nRet != DEVICE_OK)
  { goto Initialize_Done; }

  if ( isColor() )
    SetProperty( MM::g_Keyword_PixelType, g_PixelType_32bitRGB );


  // Bit depth
  pAct = new CPropertyAction (this, &CABSCamera::OnBitDepth);
  nRet = CreateProperty(g_Keyword_BitDepth, g_BitDepth_Default, MM::Integer, false, pAct);
  assert(nRet == DEVICE_OK);
  nRet = setAllowedBitDepth();
  if (nRet != DEVICE_OK)
  { goto Initialize_Done; }


  // camera exposure
  pAct = new CPropertyAction (this, &CABSCamera::OnExposureLongTime);
  nRet = CreateProperty(g_Keyword_Exposure_LongTime, g_Exposure_LongTime_Off, MM::String, false, pAct);
  assert(nRet == DEVICE_OK);
  AddAllowedValue(g_Keyword_Exposure_LongTime, g_Exposure_LongTime_Off );
  AddAllowedValue(g_Keyword_Exposure_LongTime, g_Exposure_LongTime_On );


  pAct = new CPropertyAction (this, &CABSCamera::OnExposure);
  nRet = CreateProperty(MM::g_Keyword_Exposure, "10.0", MM::Float, false, pAct);
  assert(nRet == DEVICE_OK);
  nRet = setAllowedExpsoure();
  if (nRet != DEVICE_OK)
  { goto Initialize_Done; }

  // camera framerate
  pAct = new CPropertyAction (this, &CABSCamera::OnFramerate);
  nRet = CreateProperty( g_Keyword_Framerate, g_Framerate_FreeRun, MM::String, false, pAct);
  assert(nRet == DEVICE_OK);
  if (nRet != DEVICE_OK)
  { goto Initialize_Done; }
  else
    setAllowedFramerates();

  // scan mode
  pAct = new CPropertyAction (this, &CABSCamera::OnReadoutMode);
  nRet = CreateProperty(MM::g_Keyword_ReadoutMode, g_ReadoutMode_Automatic, MM::String, false, pAct);
  assert(nRet == DEVICE_OK);
  AddAllowedValue(MM::g_Keyword_ReadoutMode, g_ReadoutMode_Automatic );
  AddAllowedValue(MM::g_Keyword_ReadoutMode, g_ReadoutMode_Continuous );
  AddAllowedValue(MM::g_Keyword_ReadoutMode, g_ReadoutMode_TriggeredSW );
  AddAllowedValue(MM::g_Keyword_ReadoutMode, g_ReadoutMode_TriggeredHW );

  // camera gain
  if ( isSupported( FUNC_GAIN ) )
  {
    pAct = new CPropertyAction (this, &CABSCamera::OnGain);
    nRet = CreateProperty( MM::g_Keyword_Gain, "1.0", MM::Float, false, pAct);
    nRet = setAllowedGain( MM::g_Keyword_Gain );
    if (nRet != DEVICE_OK)
    { goto Initialize_Done; }

    if ( true == isColor() )
    {
      // camera gain red
      pAct = new CPropertyAction (this, &CABSCamera::OnGainRed);
      nRet = CreateProperty( g_Keyword_Gain_Red, "1.0", MM::Float, false, pAct);
      nRet = setAllowedGain( g_Keyword_Gain_Red );
      if (nRet != DEVICE_OK)
      { goto Initialize_Done; }

      // camera gain green
      pAct = new CPropertyAction (this, &CABSCamera::OnGainGreen);
      nRet = CreateProperty( g_Keyword_Gain_Green, "1.0", MM::Float, false, pAct);
      nRet = setAllowedGain( g_Keyword_Gain_Green );
      if (nRet != DEVICE_OK)
      { goto Initialize_Done; }

      // don't support separate green channels
      /*
      // camera gain green 1
      pAct = new CPropertyAction (this, &CABSCamera::OnGainGreen1);
      nRet = CreateProperty( g_Keyword_Gain_Green1, "1.0", MM::Float, false, pAct);
      nRet = setAllowedGain( g_Keyword_Gain_Green1 );
      if (nRet != DEVICE_OK)
      { goto Initialize_Done; }

      // camera gain green 2
      pAct = new CPropertyAction (this, &CABSCamera::OnGainGreen2);
      nRet = CreateProperty( g_Keyword_Gain_Green2, "1.0", MM::Float, false, pAct);
      nRet = setAllowedGain( g_Keyword_Gain_Green2 );
      if (nRet != DEVICE_OK)
      { goto Initialize_Done; }
      */

      // camera gain blue
      pAct = new CPropertyAction (this, &CABSCamera::OnGainBlue);
      nRet = CreateProperty( g_Keyword_Gain_Blue, "1.0", MM::Float, false, pAct);
      nRet = setAllowedGain( g_Keyword_Gain_Blue );
      if (nRet != DEVICE_OK)
      { goto Initialize_Done; }
    }
  }

  // camera gain extra
  if ( isSupported( FUNC_BITSHIFT ) )
  {
    pAct = new CPropertyAction (this, &CABSCamera::OnGainExtra);
    nRet = CreateProperty( g_Keyword_Gain_Extra, g_Gain_Extra_x1, MM::String, false, pAct);
    nRet = setAllowedGainExtra( );
    if (nRet != DEVICE_OK)
    { goto Initialize_Done; }
  }

  if ( isSupported( FUNC_WHITE_BALANCE ) )
  {
    pAct = new CPropertyAction (this, &CABSCamera::OnWhiteBalance);
    nRet = CreateProperty(g_Keyword_WhiteBalance, "", MM::String, false, pAct);
    assert(nRet == DEVICE_OK);
    AddAllowedValue(g_Keyword_WhiteBalance, "" );
    AddAllowedValue(g_Keyword_WhiteBalance, g_WhiteBalance_OnePush );
 } 

  if ( isSupported( FUNC_AUTOEXPOSURE ) )
  {
    pAct = new CPropertyAction (this, &CABSCamera::OnAutoExposure);
    nRet = CreateProperty(g_Keyword_AutoExposure, g_AutoExposure_Off, MM::String, false, pAct);
    assert(nRet == DEVICE_OK);
    AddAllowedValue(g_Keyword_AutoExposure, g_AutoExposure_Off );
    AddAllowedValue(g_Keyword_AutoExposure, g_AutoExposure_On );

    pAct = new CPropertyAction (this, &CABSCamera::OnAutoExposureOptions);
    nRet = CreateProperty(g_Keyword_AutoExposureOptions, g_AutoExposure_OptNone, MM::String, false, pAct);
    assert(nRet == DEVICE_OK);
    AddAllowedValue(g_Keyword_AutoExposureOptions, g_AutoExposure_OptNone );
    AddAllowedValue(g_Keyword_AutoExposureOptions, g_AutoExposure_Opt50Hz );
    AddAllowedValue(g_Keyword_AutoExposureOptions, g_AutoExposure_Opt60Hz );
  }

  if ( isSupported( FUNC_EDGE_ENHANCE ) )
  {
    pAct = new CPropertyAction (this, &CABSCamera::OnSharpen);
    nRet = CreateProperty(g_Keyword_Sharpen, g_Sharpen_Off, MM::String, false, pAct);
    assert(nRet == DEVICE_OK);
    AddAllowedValue(g_Keyword_Sharpen, g_Sharpen_Off );
    AddAllowedValue(g_Keyword_Sharpen, g_Sharpen_Level1 );
    AddAllowedValue(g_Keyword_Sharpen, g_Sharpen_Level2 );
    AddAllowedValue(g_Keyword_Sharpen, g_Sharpen_Level3 );
  }

  if ( isColor() )
  {
    pAct = new CPropertyAction (this, &CABSCamera::OnBayerDemosaic);
    nRet = CreateProperty(g_Keyword_BayerDemosaic, g_BayerDemosaic_Fast, MM::String, false, pAct);
    assert(nRet == DEVICE_OK);
    AddAllowedValue(g_Keyword_BayerDemosaic, g_BayerDemosaic_Fast );
    AddAllowedValue(g_Keyword_BayerDemosaic, g_BayerDemosaic_Quality );
  }

  if ( isSupported( FUNC_BLACKLEVEL ) )
  {
    pAct = new CPropertyAction (this, &CABSCamera::OnBlackLevel);
    nRet = CreateProperty(g_Keyword_Blacklevel, g_BlacklevelOn, MM::String, false, pAct);
    assert(nRet == DEVICE_OK);
    AddAllowedValue(g_Keyword_Blacklevel, g_BlacklevelOn );
    AddAllowedValue(g_Keyword_Blacklevel, g_BlacklevelOff );
  }

  if ( isSupported( FUNC_GAMMA ) )
  {
    pAct = new CPropertyAction (this, &CABSCamera::OnGamma);
    nRet = CreateProperty(g_Keyword_Gamma, "1.0", MM::Float, false, pAct);
    assert(nRet == DEVICE_OK);
    setAllowedGamma( );
  }

  if ( isSupported( FUNC_HUE_SATURATION ) )
  {
    pAct = new CPropertyAction (this, &CABSCamera::OnHue);
    nRet = CreateProperty(g_Keyword_Hue, "0", MM::Integer, false, pAct);
    assert(nRet == DEVICE_OK);

    pAct = new CPropertyAction (this, &CABSCamera::OnSaturation);
    nRet = CreateProperty(g_Keyword_Saturation, "0", MM::Integer, false, pAct);
    assert(nRet == DEVICE_OK);

    setAllowedHueSaturation( );
  }

  if ( isSupported( FUNC_SHADING_CORRECTION ) )
  {
    pAct = new CPropertyAction (this, &CABSCamera::OnShadingCorrection);
    nRet = CreateProperty(g_Keyword_ShadingCorrection, g_ShadingCorrection_Off, MM::String, false, pAct);
    assert(nRet == DEVICE_OK);

    pAct = new CPropertyAction (this, &CABSCamera::OnShadingCorrectionSetup);
    nRet = CreateProperty(g_Keyword_ShadingCorrection_Setup, "", MM::String, false, pAct);
    assert(nRet == DEVICE_OK);

    updateShadingCorrectionState();
  }

  if ( isSupported( FUNC_COLOR_CORRECTION ) )
  {
    pAct = new CPropertyAction (this, &CABSCamera::OnColorCorrection);
    nRet = CreateProperty(g_Keyword_CCM, g_CCM_Off, MM::String, false, pAct);
    assert(nRet == DEVICE_OK);
    fillCCMList();
  }

  if ( isSupported( FUNC_IO_PORTS ) )
  {
    pAct = new CPropertyAction (this, &CABSCamera::OnTriggerInport);
    nRet = CreateProperty(g_Keyword_TriggerInport, g_IOPort_None, MM::String, false, pAct);
    assert(nRet == DEVICE_OK);

    pAct = new CPropertyAction (this, &CABSCamera::OnTriggerPolarity);
    nRet = CreateProperty(g_Keyword_TriggerPolarity, g_Polarity_LowActive, MM::String, false, pAct);
    assert(nRet == DEVICE_OK);
    AddAllowedValue(g_Keyword_TriggerPolarity, g_Polarity_LowActive );
    AddAllowedValue(g_Keyword_TriggerPolarity, g_Polarity_HighActive );

    pAct = new CPropertyAction (this, &CABSCamera::OnTriggerDelay);
    nRet = CreateProperty(g_Keyword_TriggerDelay, "0", MM::Integer, false, pAct);
    assert(nRet == DEVICE_OK);

    pAct = new CPropertyAction (this, &CABSCamera::OnStrobeOutport);
    nRet = CreateProperty(g_Keyword_StrobeOutport, g_IOPort_None, MM::String, false, pAct);
    assert(nRet == DEVICE_OK);

    pAct = new CPropertyAction (this, &CABSCamera::OnStrobePolarity);
    nRet = CreateProperty(g_Keyword_StrobePolarity, g_Polarity_LowActive, MM::String, false, pAct);
    assert(nRet == DEVICE_OK);
    AddAllowedValue(g_Keyword_StrobePolarity, g_Polarity_LowActive );
    AddAllowedValue(g_Keyword_StrobePolarity, g_Polarity_HighActive );

    fillIOPorts();
  }

  // statdard camera resolutions
  if ( buildStandardResolutionList( ) )
  {
    pAct = new CPropertyAction (this, &CABSCamera::OnStdResolution);
    nRet = CreateProperty(g_Keyword_StdResolution, "", MM::String, false, pAct);
    assert(nRet == DEVICE_OK);
    fillStandardResolutionList( );
  }

  // camera profiles
  {
    pAct = new CPropertyAction (this, &CABSCamera::OnProfileLoad);
    nRet = CreateProperty(g_Keyword_ProfilesLoad, PROFILE_NONE, MM::String, false, pAct);
    assert(nRet == DEVICE_OK);
    fillProfileList( );

    pAct = new CPropertyAction (this, &CABSCamera::OnProfileSave);
    nRet = CreateProperty(g_Keyword_ProfilesSave, PROFILE_NONE, MM::String, false, pAct);
    assert(nRet == DEVICE_OK);
    AddAllowedValue(g_Keyword_ProfilesSave, PROFILE_NONE );
    AddAllowedValue(g_Keyword_ProfilesSave, PROFILE_PREVIEWNAME );
    AddAllowedValue(g_Keyword_ProfilesSave, PROFILE_QUALITITYNAME );
  }

  // camera temperature
  {
    temperatureUnit_ = 1.0;
    temperatureIndex_ = -1;

    S_TEMPERATURE_CAPS * temperatureCaps = 0;
    u32 rc = getCap( FUNC_TEMPERATURE, (void* &) temperatureCaps );
    if ( IsNoError( rc) )
    {
      if ( temperatureCaps->dwSensors > 0 )
      {
        temperatureUnit_ = temperatureCaps->sSensor[0].wUnit;
        temperatureIndex_ = 0;

        pAct = new CPropertyAction (this, &CABSCamera::OnTemperature);
        nRet = CreateProperty(MM::g_Keyword_CCDTemperature, "20.0 °C", MM::String, true, pAct);
        assert(nRet == DEVICE_OK);
      }
    }

    if (temperatureCaps)
      delete [] temperatureCaps;
  }

  // readout time
  pAct = new CPropertyAction (this, &CABSCamera::OnReadoutTime);
  nRet = CreateProperty(MM::g_Keyword_ReadoutTime, "0", MM::Float, false, pAct);
  assert(nRet == DEVICE_OK);

  // try to load the last used profile profiles and restore it
  loadProfile( PROFILE_DEFAULTNAME, PROFILE_SETTINGSNAME );

  // prepare transpose callbacks => as workaround to be notified on changes like the other functions
  initTransposeFunctions( true );

  // setup the buffer
  // ----------------
  // camera settings to internal members (image buffer)
  checkForModifiedCameraParameter();
  updateShadingCorrectionState();

  // synchronize all properties
  // --------------------------
  nRet = UpdateStatus();
  if (nRet != DEVICE_OK)
  { goto Initialize_Done; };

  setInitialized( true );

Initialize_Done:
  if (nRet != DEVICE_OK)
  {
    u08 deviceNumber = deviceNo();
    setDeviceNo( NO_CAMERA_DEVICE );

    if ( static_cast<u08>(NO_CAMERA_DEVICE) != deviceNumber )
    {
      CamUSB_FreeCamera( deviceNumber );
    }
  }
  else
  {
    // initialize image buffer
    generateEmptyImage( img_ );
  }
  return nRet;
}

/**
* Shuts down (unloads) the device.
* Required by the MM::Device API.
* Ideally this method will completely unload the device and release all resources.
* Shutdown() may be called multiple times in a row.
* After Shutdown() we should be allowed to call Initialize() again to load the device
* without causing problems.
*/
int CABSCamera::Shutdown()
{
  if ( false == isInitialized() )
    return DEVICE_OK;

  if ( NO_CAMERA_DEVICE != deviceNo() )
  {
    u32 rc;
    rc = saveProfile( PROFILE_DEFAULTNAME, PROFILE_SETTINGSNAME );
    rc = GET_RC( CamUSB_FreeCamera( (u08) deviceNo() ), deviceNo() );
    setDeviceNo( NO_CAMERA_DEVICE );
  }

  initTransposeFunctions( false );

  setInitialized( false );
  return DEVICE_OK;
}

/**
* Performs exposure and grabs a single image.
* This function should block during the actual exposure and return immediately afterwards
* (i.e., before readout). This behavior is needed for proper synchronization with the shutter.
* Required by the MM::Camera API.
*/
int CABSCamera::SnapImage()
{
  //TIMEDEBUG
  u32 rc;

    MM::MMTime startTime = GetCurrentMMTime();

    // read image
    rc = getCameraImage( img_ );

    if ( retOK == rc )
      readoutStartTime_ = startTime;

  //TIMEDEBUG_RC(rc)
  return convertApiErrorCode( rc );
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
const unsigned char* CABSCamera::GetImageBuffer()
{
  MMThreadGuard g(img_);
  unsigned char *pB = (unsigned char*)(img_.GetPixels());
  return pB;
}

 unsigned  CABSCamera::GetNumberOfComponents() const
{
  return nComponents_;
};

/**
* Returns image buffer X-size in pixels.
* Required by the MM::Camera API.
*/
unsigned CABSCamera::GetImageWidth() const
{
  return img_.Width();
}

/**
* Returns image buffer Y-size in pixels.
* Required by the MM::Camera API.
*/
unsigned CABSCamera::GetImageHeight() const
{
  return img_.Height();
}

/**
* Returns image buffer pixel depth in bytes.
* Required by the MM::Camera API.
*/
unsigned CABSCamera::GetImageBytesPerPixel() const
{
  return img_.Depth();
}

/**
* Returns the bit depth (dynamic range) of the pixel.
* This does not affect the buffer size, it just gives the client application
* a guideline on how to interpret pixel values.
* Required by the MM::Camera API.
*/
unsigned CABSCamera::GetBitDepth() const
{
  return bitDepth_;
}

/**
* Returns the size in bytes of the image buffer.
* Required by the MM::Camera API.
*/
long CABSCamera::GetImageBufferSize() const
{
  return GetImageWidth() * GetImageWidth() * GetImageBytesPerPixel();
}

/**
* Sets the camera Region Of Interest.
* Required by the MM::Camera API.
* This command will change the dimensions of the image.
* Depending on the hardware capabilities the camera may not be able to configure the
* exact dimensions requested - but should try do as close as possible.
* If the hardware does not have this capability the software should simulate the ROI by
* appropriately cropping each frame.
* This demo implementation ignores the position coordinates and just crops the buffer.
* @param x - top-left corner coordinate
* @param y - top-left corner coordinate
* @param xSize - width
* @param ySize - height
*/
int CABSCamera::SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize)
{
  u32 rc, dwSize;

  if (xSize == 0 && ySize == 0)
  {
    xSize = cameraCCDXSize_;
    ySize = cameraCCDYSize_;
  }

  S_RESOLUTION_INFO sResInfo = {0};
  sResInfo.sResIn.wSizeX = (u16) xSize;
  sResInfo.sResIn.wSizeY = (u16) ySize;
  sResInfo.sResIn.wOffsetX= (i16) x;
  sResInfo.sResIn.wOffsetY= (i16) y;

  if (binSize_ > 1)
    sResInfo.sResIn.dwBin = 0x00010001 << ( binSize_-2 );

  rc = GET_RC( CamUSB_GetCameraResolutionInfo( &sResInfo, deviceNo() ), deviceNo());

  if ( FALSE == IsNoError( rc ) )
    return convertApiErrorCode( rc, __FUNCTION__ );

  // set resolution
  dwSize = sizeof( sResInfo.sResOut );
  rc = GET_RC( CamUSB_SetFunction( FUNC_RESOLUTION, &sResInfo.sResOut, dwSize, 0, 0, 0, 0, deviceNo() ), deviceNo());
  if ( FALSE == IsNoError( rc ) )
  {
    return convertApiErrorCode( rc, __FUNCTION__ );
  }

  checkForModifiedCameraParameter();

  // update GUI for Exosure and Framerate
  setAllowedExpsoure();
  UpdateProperty( MM::g_Keyword_Exposure );
  setAllowedFramerates();
  UpdateProperty( g_Keyword_Framerate );

  // update std resolution
  UpdateProperty( g_Keyword_StdResolution );
  // clear profiles
  SetProperty( g_Keyword_ProfilesLoad, PROFILE_NONE );
  SetProperty( g_Keyword_ProfilesSave, PROFILE_NONE );

  return DEVICE_OK;
}

/**
* Returns the actual dimensions of the current ROI.
* Required by the MM::Camera API.
*/
int CABSCamera::GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize)
{
  x = roiX_;
  y = roiY_;

  xSize = img_.Width();
  ySize = img_.Height();

  return DEVICE_OK;
}

/**
* Resets the Region of Interest to full frame.
* Required by the MM::Camera API.
*/
int CABSCamera::ClearROI()
{
  return SetROI( 0, 0, 0, 0);
}

/**
* Returns the current exposure setting in milliseconds.
* Required by the MM::Camera API.
*/
double CABSCamera::GetExposure() const
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
void CABSCamera::SetExposure(double exp)
{
  SetProperty(MM::g_Keyword_Exposure, CDeviceUtils::ConvertToString(exp));
}

/**
* Returns the current binning factor.
* Required by the MM::Camera API.
*/
int CABSCamera::GetBinning() const
{
  char buf[MM::MaxStrLength];
  int ret = GetProperty(MM::g_Keyword_Binning, buf);
  if (ret != DEVICE_OK)
    return 1;
  return atoi(buf);
}

bool CABSCamera::isSupported( const unsigned long long qwFunctionID )
{
  return ((qwSupportedFunctions_ & qwFunctionID) == qwFunctionID);
}

/**
* Sets binning factor.
* Required by the MM::Camera API.
*/
int CABSCamera::SetBinning(int binF)
{
  return SetProperty(MM::g_Keyword_Binning, CDeviceUtils::ConvertToString(binF));
}

int CABSCamera::SetAllowedBinning()
{
  S_RESOLUTION_CAPS * resolutionCaps = 0;
  u32 dwRC = getCap( FUNC_RESOLUTION, (void* &) resolutionCaps );

  if ( retOK == dwRC )
  {
    vector<string> binValues;
    binValues.push_back("1");
    if ((resolutionCaps->dwBinModes & XY_BIN_2X) == XY_BIN_2X)
      binValues.push_back("2");
    if ((resolutionCaps->dwBinModes & XY_BIN_3X) == XY_BIN_3X)
      binValues.push_back("3");
    if ((resolutionCaps->dwBinModes & XY_BIN_4X) == XY_BIN_4X)
      binValues.push_back("4");
    if ((resolutionCaps->dwBinModes & XY_BIN_5X) == XY_BIN_5X)
      binValues.push_back("5");
    if ((resolutionCaps->dwBinModes & XY_BIN_6X) == XY_BIN_6X)
      binValues.push_back("6");
    if ((resolutionCaps->dwBinModes & XY_BIN_7X) == XY_BIN_7X)
      binValues.push_back("7");
    if ((resolutionCaps->dwBinModes & XY_BIN_8X) == XY_BIN_8X)
      binValues.push_back("8");

    if (resolutionCaps)
      delete [] resolutionCaps;

    LogMessage("Setting Allowed Binning settings", true);
    return SetAllowedValues(MM::g_Keyword_Binning, binValues);
  }
  else
    return convertApiErrorCode( dwRC, __FUNCTION__ );
}

int CABSCamera::setAllowedPixelTypes( void )
{
  S_RESOLUTION_CAPS * resolutionCaps = 0;
  u32 dwRC = getCap( FUNC_RESOLUTION, (void* &) resolutionCaps );

  if ( retOK == dwRC )
  {
    vector<string> pixelTypeValues;
    pixelTypeValues.push_back(g_PixelType_8bit);
    if (resolutionCaps->wMaxBPP > 8)
      pixelTypeValues.push_back(g_PixelType_16bit);

    if (resolutionCaps->wSensorType & ST_COLOR)
    {
      pixelTypeValues.push_back(g_PixelType_32bitRGB);
      if (resolutionCaps->wMaxBPP > 8)
        pixelTypeValues.push_back(g_PixelType_64bitRGB);
    }

    if (resolutionCaps)
      delete [] resolutionCaps;

    LogMessage("Setting Allowed Pixeltypes settings", true);
    return SetAllowedValues(MM::g_Keyword_PixelType, pixelTypeValues);
  }
  else
    return convertApiErrorCode( dwRC, __FUNCTION__ );
}

int CABSCamera::setAllowedBitDepth( void )
{
  S_RESOLUTION_CAPS * resolutionCaps = 0;
  u32 dwRC = getCap( FUNC_RESOLUTION, (void* &) resolutionCaps );

  if ( retOK == dwRC )
  {
    vector<string> bitDepths;
    bitDepths.push_back( g_BitDepth_08 );

    if (resolutionCaps->wMaxBPP >= 10)
      bitDepths.push_back( g_BitDepth_10 );

    if (resolutionCaps->wMaxBPP >= 12)
      bitDepths.push_back( g_BitDepth_12 );

    if (resolutionCaps->wMaxBPP >= 14)
      bitDepths.push_back( g_BitDepth_14 );

    if (resolutionCaps->wMaxBPP >= 16)
      bitDepths.push_back( g_BitDepth_16 );

    if (resolutionCaps)
      delete [] resolutionCaps;

    LogMessage("Setting Allowed BitDepth settings", true);
    return SetAllowedValues( g_Keyword_BitDepth, bitDepths);
  }
  else
    return convertApiErrorCode( dwRC, __FUNCTION__ );
}

int CABSCamera::setAllowedExpsoure( void )
{
  S_EXPOSURE_CAPS * exposureCaps = 0;
  u32 dwRC = getCap( FUNC_EXPOSURE, (void* &) exposureCaps );

  if ( retOK == dwRC )
  {
    double fMinExposure, fMaxExposure;

    fMinExposure = exposureCaps->sExposureRange[0].dwMin / 1000.0;
    fMaxExposure = exposureCaps->sExposureRange[exposureCaps->dwCountRanges-1].dwMax / 1000.0;

    if (exposureCaps)
      delete [] exposureCaps;

    LogMessage("Setting Exposure limits", true);

   if ( exposureLongTime_ != g_Exposure_LongTime_On )
     fMaxExposure = min( fMaxExposure, 250.0 );

    return SetPropertyLimits(MM::g_Keyword_Exposure, fMinExposure, fMaxExposure);
  }
  else
    return convertApiErrorCode( dwRC, __FUNCTION__ );
}

int CABSCamera::setAllowedGainExtra( void )
{
  S_BITSHIFT_CAPS * bitshiftCaps = 0;
  u32 rc = getCap( FUNC_BITSHIFT, (void* &) bitshiftCaps );

  if ( retOK == rc )
  {
    AddAllowedValue(g_Keyword_Gain_Extra, g_Gain_Extra_x1 );

    if ( bitshiftCaps->bMaxBitShift >= 1 )
      AddAllowedValue(g_Keyword_Gain_Extra, g_Gain_Extra_x2 );
    if ( bitshiftCaps->bMaxBitShift >= 2 )
      AddAllowedValue(g_Keyword_Gain_Extra, g_Gain_Extra_x4 );
    if ( bitshiftCaps->bMaxBitShift >= 3 )
      AddAllowedValue(g_Keyword_Gain_Extra, g_Gain_Extra_x8 );
    if ( bitshiftCaps->bMaxBitShift >= 4 )
      AddAllowedValue(g_Keyword_Gain_Extra, g_Gain_Extra_x16 );

    LogMessage("Setting BitShift limits", true);

    return DEVICE_OK;
  }
  else
    return convertApiErrorCode( rc, __FUNCTION__ );
}


int CABSCamera::setAllowedGain( const char* szKeyword_GainChannel )
{
  S_GAIN_CAPS * gainCaps = 0;
  u32 rc = getCap( FUNC_GAIN, (void* &) gainCaps );

  if ( retOK == rc )
  {
    double fMinGain, fMaxGain;
    fMinGain = gainCaps->sGainRange[0].dwMin / 1000.0;
    fMaxGain = gainCaps->sGainRange[gainCaps->wGainRanges-1].dwMax / 1000.0;

    if (gainCaps)
      delete [] gainCaps;

    LogMessage("Setting Gain limits", true);

    return SetPropertyLimits( szKeyword_GainChannel, fMinGain, fMaxGain);
  }
  else
    return convertApiErrorCode( rc, __FUNCTION__ );
}

/**
* Stop and wait for the Sequence thread finished
*/

int CABSCamera::StopSequenceAcquisition()
{
  TIMEDEBUG

  if ( IsCapturing() )
  {
    // abort current image transfer only for long exposure times
    if ( exposureLongTime_ == g_Exposure_LongTime_On )
    {
      abortGetImageFired_ = true;
      u32 rc = 0;
      rc = GET_RC( CamUSB_AbortGetImage( deviceNo() ), deviceNo());
      assert(rc == retOK);
    }
  }

  int iResult = __super::StopSequenceAcquisition();

    // restore readout mode
    setLiveReadoutMode( false );

    // update image buffer type
    img_.setBufferType( CAbsImgBuffer::eInternBuffer );

  return iResult;
}

/**
* Simple implementation of Sequence Acquisition
* A sequence acquisition should run on its own thread and transport new images
* coming of the camera into the MMCore circular buffer.
*/

int CABSCamera::StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow)
{

  if (IsCapturing())
    StopSequenceAcquisition();

  TIMEDEBUG
  imageCounter_ = 0;

  // at live mode should the readout mode continouse or HW-Triggered
  setLiveReadoutMode( ( LONG_MAX == numImages ) );

  img_.setBufferType( CAbsImgBuffer::eExternBuffer );

  stopOnOverflow_ = stopOnOverflow;
  numImages_      = numImages;
  interval_ms_    = interval_ms;

  if ( exposureLongTime_ == g_Exposure_LongTime_On )
    bFirstExposureLongTimeImage_ = true;

  abortGetImageFired_ = false;
  return __super::StartSequenceAcquisition(numImages, interval_ms, stopOnOverflow);
}
/*
* Inserts Image and MetaData into MMCore circular Buffer
*/
int CABSCamera::InsertImage()
{
  TIMEDEBUG

  MMThreadGuard g( img_ ); // lock the image

  MM::MMTime timeStamp = this->GetCurrentMMTime();
  char label[MM::MaxStrLength];
  this->GetLabel(label);

  // Important: metadata about the image are generated here:
  Metadata md;
  md.put("Camera", label);
  md.put(MM::g_Keyword_Metadata_StartTime, CDeviceUtils::ConvertToString(sequenceStartTime_.getMsec()));
  md.put(MM::g_Keyword_Elapsed_Time_ms, CDeviceUtils::ConvertToString((timeStamp - sequenceStartTime_).getMsec()));
  md.put(MM::g_Keyword_Metadata_ImageNumber, CDeviceUtils::ConvertToString( imageCounter_ ));
  md.put(MM::g_Keyword_Metadata_ROI_X, CDeviceUtils::ConvertToString( (long) GetImageWidth()));
  md.put(MM::g_Keyword_Metadata_ROI_Y, CDeviceUtils::ConvertToString( (long) GetImageHeight()));

  char buf[MM::MaxStrLength];
  GetProperty(MM::g_Keyword_Binning, buf);
  md.put(MM::g_Keyword_Binning, buf);

  const unsigned char* pI = GetImageBuffer();
  unsigned int w = GetImageWidth();
  unsigned int h = GetImageHeight();
  unsigned int b = GetImageBytesPerPixel();

  

  int ret = GetCoreCallback()->InsertImage(this, pI, w, h, b, md.Serialize().c_str() );

  if (!stopOnOverflow_ && ret == DEVICE_BUFFER_OVERFLOW)
  {  
    // do not stop on overflow - just reset the buffer
    GetCoreCallback()->ClearImageBuffer(this);
    // don't process this same image again...
    ret = GetCoreCallback()->InsertImage(this, pI, w, h, b, md.Serialize().c_str(), false);
  }

  if (ret == DEVICE_OK)
    imageCounter_++;

  return ret;
}

/*
* Do actual capturing
* Called from inside the thread
*/
int CABSCamera::ThreadRun (void)
{
  int ret=DEVICE_ERR;

  // only for live mode
  if ( ( LONG_MAX == GetNumberOfImages() ) && ( false == stopOnOverflow_ ) )
  {
    // only if long time exposure is enabled
    if ( exposureLongTime_ == g_Exposure_LongTime_On )
    {
      if ( bFirstExposureLongTimeImage_ )
      {
        bFirstExposureLongTimeImage_ = false;

        LogMessage("SeqAcquisition Start => insert last valid or blank image (GUI blocking workaround)\n", true);
        MMThreadGuard g( img_ ); // lock the image

        CAbsImgBuffer::EBufferType eType = img_.bufferType();
        if ( 0 == img_.GetPixels() )
          img_.setBufferType( CAbsImgBuffer::eInternBuffer );

        InsertImage();

        // restore buffer mode
        img_.setBufferType( eType );
      }
    }
  }

  // Trigger
  if (triggerDevice_.length() > 0) {
    MM::Device* triggerDev = GetDevice(triggerDevice_.c_str());
    if (triggerDev != 0) {
      //char label[256];
      //triggerDev->GetLabel(label);
      LogMessage("trigger requested");
      triggerDev->SetProperty("Trigger","+");
    }
  }

  if (abortGetImageFired_)
    return ret;

  ret = SnapImage();
  if ( (ret != DEVICE_OK) && 
       ( mmToApiErrorCode( ret ) != retUSER_ABORT) && 
       ( mmToApiErrorCode( ret ) != retNOIMGAVAIL) )
    return ret;

  if (ret == DEVICE_OK)
    ret = InsertImage();
  else // reset error code => on real errors abort function would already be done 4 lines above (now avoid get image thread to terminated)
    ret = DEVICE_OK;

  return ret;
};

/*
CABSCameraSequenceThread::CABSCameraSequenceThread( CCameraBase<CABSCamera> * pCam )
: CCameraBase<CABSCamera>::BaseSequenceThread( pCam )
{

};

CABSCameraSequenceThread::~CABSCameraSequenceThread(){};


int CABSCameraSequenceThread::svc(void) throw()
{
  CABSCamera* absCam = dynamic_cast<CABSCamera*>(GetCamera());
  int ret=DEVICE_ERR;
  try
  {

    // only for live mode
    if ( ( LONG_MAX == GetNumberOfImages() ) && ( false == absCam->stopOnOverflow_ ) )
    {
      // only if long time exposure is enabled
      if ( absCam->exposureLongTime_ == g_Exposure_LongTime_On )
      {
        absCam->LogMessage("SeqAcquisition Start => insert last valid or blank image (GUI blocking workaround)\n", true);
        MMThreadGuard g( absCam->img_ ); // lock the image

        CAbsImgBuffer::EBufferType eType = absCam->img_.bufferType();
        if ( 0 == absCam->img_.GetPixels() )
          absCam->img_.setBufferType( CAbsImgBuffer::eInternBuffer );

        absCam->InsertImage();

        // restore buffer mode
        absCam->img_.setBufferType( eType );
      }
    }

    do
    {
      ret = absCam->ThreadRun();
    } while (DEVICE_OK == ret && !IsStopped() && absCam->imageCounter_++ < GetNumberOfImages() - 1);

    if (IsStopped())
      absCam->LogMessage("SeqAcquisition interrupted by the user\n");
  }catch(...){
    absCam->LogMessage(g_Msg_EXCEPTION_IN_THREAD, false);
  }
  Stop();
  UpdateActualDuration();
  absCam->OnThreadExiting();
  return ret;
}
*/

///////////////////////////////////////////////////////////////////////////////
// CABSCamera Action handlers
///////////////////////////////////////////////////////////////////////////////

/*
* this Read Only property will update whenever any property is modified
*/

int CABSCamera::OnTestProperty(MM::PropertyBase* pProp, MM::ActionType eAct, long indexx)
{
  if (eAct == MM::BeforeGet)
  {
    pProp->Set(testProperty_[indexx]);
  }
  else if (eAct == MM::AfterSet)
  {
    pProp->Get(testProperty_[indexx]);
  }
  return DEVICE_OK;

}



/**
* Handles "Binning" property.
*/
int CABSCamera::OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  int ret = DEVICE_ERR;
  switch(eAct)
  {
  case MM::AfterSet:
    {
      // the user just set the new value for the property, so we have to
      // apply this value to the 'hardware'.
      u32 rc, dwSize;
      long binFactor;
      pProp->Get(binFactor);

      S_RESOLUTION_INFO sResInfo = {0};
      sResInfo.sResIn.wSizeX = (u16)cameraCCDXSize_;
      sResInfo.sResIn.wSizeY = (u16)cameraCCDYSize_;
      if (binFactor > 1)
        sResInfo.sResIn.dwBin = 0x00010001 << ( binFactor-2 );

      rc = GET_RC( CamUSB_GetCameraResolutionInfo( &sResInfo, deviceNo() ), deviceNo());

      if ( FALSE == IsNoError( rc ) )
        return convertApiErrorCode( rc, __FUNCTION__ );

      // set resolution
      dwSize = sizeof( sResInfo.sResOut );
      rc = GET_RC( CamUSB_SetFunction( FUNC_RESOLUTION, &sResInfo.sResOut, dwSize, 0, 0, 0, 0, deviceNo() ), deviceNo());
      if ( FALSE == IsNoError( rc ) )
      {
        return convertApiErrorCode( rc, __FUNCTION__ );
      }

      checkForModifiedCameraParameter();

      ret=DEVICE_OK;
    }break;
  case MM::BeforeGet:
    {
      ret=DEVICE_OK;
      pProp->Set(binSize_);
    }break;
  }
  return ret;
}

/**
* Handles "PixelType" property.
*/
int CABSCamera::OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  int ret = DEVICE_ERR;

  switch(eAct)
  {
  case MM::AfterSet:
    {
      if (IsCapturing())
        return DEVICE_CAMERA_BUSY_ACQUIRING;

      u32 newPixelType = 0; // mono => defaul at error case
      string pixelType, bitDepth;

      pProp->Get( pixelType ); // read current pixel type
      GetPropertyString( g_Keyword_BitDepth, bitDepth ); // read current pixel type

      ret = DEVICE_OK; // init return calue

      if (pixelType.compare( g_PixelType_8bit ) == 0)
      {
        newPixelType = PIX_MONO8;

        if ( bitDepth != g_BitDepth_08 )
          SetProperty( g_Keyword_BitDepth, g_BitDepth_08 );
      }
      else if (pixelType.compare( g_PixelType_16bit ) == 0)
      {
        if ( bitDepth == g_BitDepth_08 )
        {
          SetProperty( g_Keyword_BitDepth, last16BitDepth_.c_str() );
          bitDepth = last16BitDepth_;
        }

        switch ( atoi( bitDepth.c_str() ) )
        {
        case 16: newPixelType = PIX_MONO16; break;
        case 14: newPixelType = PIX_MONO14; break;
        case 12: newPixelType = PIX_MONO12; break;
        case 10:
        default: newPixelType = PIX_MONO10; break;
        }
      }
      else if ( pixelType.compare(g_PixelType_32bitRGB) == 0)
      {
        if ( bitDepth != g_BitDepth_08 )
          SetProperty( g_Keyword_BitDepth, g_BitDepth_08 );

        newPixelType = PIX_BGRA8_PACKED;
      }
      else if ( pixelType.compare(g_PixelType_64bitRGB) == 0)
      {
        if ( bitDepth == g_BitDepth_08 )
        {
          SetProperty( g_Keyword_BitDepth, last16BitDepth_.c_str() );
          bitDepth = last16BitDepth_;
        }

        switch ( atoi( bitDepth.c_str() ) )
        {
          //case 16: newPixelType = PIX_BGRA16_PACKED; break;
        case 14: newPixelType = PIX_BGRA14_PACKED; break;
        case 12: newPixelType = PIX_BGRA12_PACKED; break;
        case 10:
        default: newPixelType = PIX_BGRA10_PACKED; break;
        }
      }
      else
      {
        // on error switch to default pixel type
        SetProperty( g_Keyword_BitDepth, g_BitDepth_08 );
        pProp->Set( g_PixelType_8bit );
        newPixelType = PIX_MONO8;
        ret = ERR_UNKNOWN_MODE;
      }

      u32 rc = GET_RC( CamUSB_SetPixelType( newPixelType, deviceNo()), deviceNo() );
      ret = convertApiErrorCode( rc, __FUNCTION__ );

      checkForModifiedCameraParameter();
    }
    break;

  case MM::BeforeGet:
    {
      long bytesPerPixel = GetImageBytesPerPixel();
      if ( bytesPerPixel == 1 )
        pProp->Set( g_PixelType_8bit );
      else if ( bytesPerPixel == 2 )
        pProp->Set( g_PixelType_16bit );
      else if ( bytesPerPixel == 4 )
        pProp->Set( g_PixelType_32bitRGB );
      else if ( bytesPerPixel == 8 )
        pProp->Set( g_PixelType_64bitRGB );
      else
        pProp->Set( g_PixelType_8bit );
      ret=DEVICE_OK;
    }
    break;
  }
  return ret;
}

/**
* Handles "BitDepth" property.
*/
int CABSCamera::OnBitDepth(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  int ret = DEVICE_ERR;
  switch(eAct)
  {
  case MM::AfterSet:
    {
      if (IsCapturing())
        return DEVICE_CAMERA_BUSY_ACQUIRING;

      long bitDepth;
      pProp->Get(bitDepth);

      unsigned int bytesPerComponent;

      ret = DEVICE_OK;
      switch (bitDepth)
      {
      case 32:
      case 16:
      case 14:
      case 12:
      case 10:
      case 8:
        bytesPerComponent = (bitDepth+7) / 8;
        bitDepth_ = bitDepth;
        break;

      default: // on error switch to default pixel type
        bytesPerComponent = 1;
        pProp->Set((long)8);
        bitDepth_ = 8;
        ret = ERR_UNKNOWN_MODE;
        break;
      }

      if (DEVICE_OK == ret)
      {
        if ( 8 != bitDepth_)
          pProp->Get( last16BitDepth_ );
      }

      std::string pixelType;
      GetPropertyString( MM::g_Keyword_PixelType, pixelType );
      unsigned int bytesPerPixel = 1;


      // automagickally change pixel type when bit depth exceeds possible value
      if (pixelType.compare(g_PixelType_8bit) == 0)
      {
        if( 2 == bytesPerComponent)
        {
          SetProperty(MM::g_Keyword_PixelType, g_PixelType_16bit);
          bytesPerPixel = 2;
        }
        else if ( 4 == bytesPerComponent)
        {
          SetProperty(MM::g_Keyword_PixelType, g_PixelType_32bit);
          bytesPerPixel = 4;

        }
        else
        {
          SetProperty(MM::g_Keyword_PixelType, g_PixelType_8bit);
          bytesPerPixel = 1;
        }
      }
      else if (pixelType.compare(g_PixelType_16bit) == 0)
      {
        if( 1 == bytesPerComponent)
        {
          SetProperty(MM::g_Keyword_PixelType, g_PixelType_8bit);
          bytesPerPixel = 1;
        }
        else
        {
          SetProperty(MM::g_Keyword_PixelType, g_PixelType_16bit);
          bytesPerPixel = 2;
        }
      }
      else if ( pixelType.compare(g_PixelType_32bitRGB) == 0)
      {
        if( 2 == bytesPerComponent)
        {
          SetProperty(MM::g_Keyword_PixelType, g_PixelType_64bitRGB);
          bytesPerPixel = 8;
        }
        else
        {
          SetProperty(MM::g_Keyword_PixelType, g_PixelType_32bitRGB);
          bytesPerPixel = 4;
        }
      }
      else if ( pixelType.compare(g_PixelType_32bit) == 0)
      {
        bytesPerPixel = 4;
      }
      else if ( pixelType.compare(g_PixelType_64bitRGB) == 0)
      {
        if( 1 == bytesPerComponent)
        {
          SetProperty(MM::g_Keyword_PixelType, g_PixelType_32bitRGB);
          bytesPerPixel = 4;
        }
        else
        {
          SetProperty(MM::g_Keyword_PixelType, g_PixelType_64bitRGB);
          bytesPerPixel = 8;
        }
      }

      checkForModifiedCameraParameter();

    }
    break;

  case MM::BeforeGet:
    {
      pProp->Set( (long)bitDepth_ );
      ret=DEVICE_OK;
    }
    break;
  }
  return ret;
}

int CABSCamera::OnReadoutTime(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int CABSCamera::OnDropPixels(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int CABSCamera::OnSaturatePixels(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  if (eAct == MM::AfterSet)
  {
    long tvalue = 0;
    pProp->Get(tvalue);
    saturatePixels_ = (0==tvalue)?false:true;
  }
  else if (eAct == MM::BeforeGet)
  {
    pProp->Set(saturatePixels_?1L:0L);
  }

  return DEVICE_OK;
}

int CABSCamera::OnFractionOfPixelsToDropOrSaturate(MM::PropertyBase* pProp, MM::ActionType eAct)
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

void CABSCamera::setLiveReadoutMode( bool bOverwriteDefault )
{
  u32 rc;
  u08 captureMode, imageCount;

  if (  readoutMode_ == g_ReadoutMode_Automatic )
  {
    rc = GET_RC( CamUSB_GetCaptureMode( &captureMode, &imageCount, deviceNo()), deviceNo() );

    if ( true == bOverwriteDefault )
    {
      if ( IsNoError( rc ) )
      {
        // set new capture mode
        if ( ( captureMode != MODE_TRIGGERED_HW ) && ( captureMode != MODE_CONTINUOUS ) )
        {
          rc = GET_RC( CamUSB_SetCaptureMode( MODE_CONTINUOUS, 0, deviceNo(), 0, 0), deviceNo() );
        }
      }
    }
    else // resore last settings
    {
      if ( (IsNoError( rc ) && ( MODE_TRIGGERED_SW != captureMode )) || ( FALSE == IsNoError( rc )) )
      {
        rc = GET_RC( CamUSB_SetCaptureMode( MODE_TRIGGERED_SW, 1, deviceNo(), 0, 0), deviceNo() );
      }
    }
  }
}

/*
* Handles "ReadoutMode" property.
* Changes allowed Binning values to test whether the UI updates properly
*/
int CABSCamera::OnReadoutMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  u32 rc;
  u08 captureMode, countImages;

  if (eAct == MM::AfterSet)
  {
    pProp->Get( readoutMode_ );

    if ( readoutMode_ == g_ReadoutMode_Automatic )
    {
      captureMode = MODE_AUTOMATIC;
      countImages = 1; 
    }
    else if ( readoutMode_ == g_ReadoutMode_Continuous )
    {
      captureMode = MODE_CONTINUOUS;
      countImages = 0;
    }
    else if ( readoutMode_ == g_ReadoutMode_TriggeredHW )
    {
      captureMode = MODE_TRIGGERED_HW;
      countImages = 1;
    }
    else // handle all other as g_ReadoutMode_TriggeredSW
    {
      pProp->Set( g_ReadoutMode_TriggeredSW );
      readoutMode_ = g_ReadoutMode_TriggeredSW;
      captureMode = MODE_TRIGGERED_SW;
      countImages = 1;
    }

    // set capture mode at camera
    LogMessage("Set property ScanMode", true);
    rc = GET_RC( CamUSB_SetCaptureMode( captureMode & MODE_MASK_CAM, countImages, deviceNo(), 0, 0), deviceNo() );

    return convertApiErrorCode( rc, __FUNCTION__ );
  }
  else if (eAct == MM::BeforeGet)
  {
    const char* readoutMode = 0;

    LogMessage("Reading property ScanMode", true);
    rc = GET_RC( CamUSB_GetCaptureMode( &captureMode, &countImages, deviceNo() ), deviceNo() );

    if ( !IsNoError( rc ) )
    {
      captureMode = MODE_TRIGGERED_SW;
    }

    switch ( captureMode )
    {
    case MODE_CONTINUOUS: 
      if (  readoutMode_ == g_ReadoutMode_Automatic ) 
        readoutMode = g_ReadoutMode_Automatic;
      else
        readoutMode = g_ReadoutMode_Continuous; 
      break;

    case MODE_TRIGGERED_HW: readoutMode = g_ReadoutMode_TriggeredHW; break;

    case MODE_TRIGGERED_SW:
      if (  readoutMode_ == g_ReadoutMode_Automatic ) 
      {
        readoutMode = g_ReadoutMode_Automatic;
        break; // only break for automatice
      }
    default: readoutMode = g_ReadoutMode_TriggeredSW; break;
    }

    pProp->Set( readoutMode );
    readoutMode_ = readoutMode;
    return convertApiErrorCode( rc, __FUNCTION__ );
  }
  return DEVICE_OK;
}
int CABSCamera::OnExposureLongTime(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  if (eAct == MM::BeforeGet)
  {
    pProp->Set( exposureLongTime_.c_str() );
  }
  else if (eAct == MM::AfterSet)
  {
    pProp->Get( exposureLongTime_ );
    setAllowedExpsoure();
  }
  return DEVICE_OK;
}

int CABSCamera::OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  if (eAct == MM::BeforeGet)
  {
    u32 rc, exposureUs;
    rc = GET_RC( CamUSB_GetExposureTime( &exposureUs, deviceNo() ), deviceNo() );
    if ( IsNoError( rc ) )
    {
      readoutUs_= exposureUs * 1.5;
      SetDelayMs( exposureUs / (1000.0 * 100.0) ); // based on inner delay handling DelayMs * 150
      pProp->Set( exposureUs / 1000.0);
    }
    return convertApiErrorCode( rc, __FUNCTION__ );
  }
  else if (eAct == MM::AfterSet)
  {
    double value;
    u32 rc, exposureUs;

    pProp->Get(value);
    exposureUs = (u32)(value * 1000.0 + 0.5);

    if ( 0.0 != getFramerate() )
    {
      if ( exposureUs > ( 1000000.0 / getFramerate() ) )
      {
        setFramerate( 0.0 );
        UpdateProperty( g_Keyword_Framerate );
      }
    }
    rc = GET_RC( CamUSB_SetExposureTime( &exposureUs, deviceNo() ), deviceNo());
    if ( IsNoError( rc ) )
    {
      readoutUs_= exposureUs * 1.5;
      SetDelayMs( exposureUs / (1000.0 * 100.0) );// based on inner delay handling DelayMs * 150
      // update with new settings
      pProp->Set( exposureUs / 1000.0 );
    }
    return convertApiErrorCode( rc, __FUNCTION__ );
  }

  UpdateProperty( MM::g_Keyword_ReadoutTime );
  return DEVICE_OK;
}

int CABSCamera::OnFramerate(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  if (eAct == MM::BeforeGet)
  {
    pProp->Set( getFramerateString().c_str() );
  }
  else if (eAct == MM::AfterSet)
  {
    string framerate;

    pProp->Get( framerate );

    if ( g_Framerate_FreeRun == framerate )
      setFramerate( 0.0 );
    else
    {
      long fps;
      if (DEVICE_OK == GetPropertyData( g_Keyword_Framerate, framerate.c_str(), fps ) )
      {
        setFramerate( fps / 1000.0);
        setAllowedExpsoure();
        UpdateProperty( MM::g_Keyword_Exposure );
      }
    }
  }
  return DEVICE_OK;
}

int CABSCamera::OnGainCommon(const char* szKeyword_GainChannel, MM::PropertyBase* pProp, MM::ActionType eAct )
{
  u32 rc, gain;
  u16 gainChannel = 0;

  if ( szKeyword_GainChannel == MM::g_Keyword_Gain )
  {
    gainChannel = ( isColor() ) ? GAIN_GREEN : GAIN_GLOBAL;
  }
  else if ( szKeyword_GainChannel == g_Keyword_Gain_Red )
  {
    gainChannel = GAIN_RED;
  }
  else if ( szKeyword_GainChannel == g_Keyword_Gain_Green )
  {
    gainChannel = GAIN_GREEN;
  }
  else if ( szKeyword_GainChannel == g_Keyword_Gain_Green1 )
  {
    gainChannel = GAIN_GREEN1;
  }
  else if ( szKeyword_GainChannel == g_Keyword_Gain_Green2 )
  {
    gainChannel = GAIN_GREEN2;
  }
  else if ( szKeyword_GainChannel == g_Keyword_Gain_Blue )
  {
    gainChannel = GAIN_BLUE;
  }

  if (eAct == MM::BeforeGet)
  {
    rc = GET_RC( CamUSB_GetGain( &gain, gainChannel, deviceNo() ), deviceNo());
    if ( IsNoError( rc ) )
    {
      pProp->Set( gain / 1000.0 );
    }
    return convertApiErrorCode( rc, __FUNCTION__ );
  }
  else if (eAct == MM::AfterSet)
  {
    double value;

    bool bAutoExposureOn = false; // per default disabled

    if ( szKeyword_GainChannel == MM::g_Keyword_Gain )
    {
      gainChannel = ( isColor() ) ? (GAIN_GREEN | GAIN_LOCKED) : (GAIN_GLOBAL);
    }
    else // allow gain changes => for white balance modifications
    {
      bAutoExposureOn = IsPropertyEqualTo( g_Keyword_AutoExposure, g_AutoExposure_On );
      // disbale auto exposure (only short)
      if (bAutoExposureOn)
        SetProperty( g_Keyword_AutoExposure, g_AutoExposure_Off );
    }

    pProp->Get(value);
    gain = (u32)(value * 1000.0f + 0.5f);

    rc = GET_RC( CamUSB_SetGain( &gain, gainChannel, deviceNo() ), deviceNo());

    if ( IsNoError( rc ) )
    {
      // update with new settings
      pProp->Set( gain / 1000.0 );
    }

    if (bAutoExposureOn)
      SetProperty( g_Keyword_AutoExposure, g_AutoExposure_On );

    return convertApiErrorCode( rc, __FUNCTION__ );
  }
  return DEVICE_OK;
}

int CABSCamera::OnGain(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  return OnGainCommon( MM::g_Keyword_Gain, pProp, eAct );
}

int CABSCamera::OnGainRed(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  return OnGainCommon( g_Keyword_Gain_Red, pProp, eAct );
}

int CABSCamera::OnGainGreen(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  return OnGainCommon( g_Keyword_Gain_Green, pProp, eAct );
}

int CABSCamera::OnGainGreen1(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  return OnGainCommon( g_Keyword_Gain_Green1, pProp, eAct );
}

int CABSCamera::OnGainGreen2(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  return OnGainCommon( g_Keyword_Gain_Green2, pProp, eAct );
}

int CABSCamera::OnGainBlue(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  return OnGainCommon( g_Keyword_Gain_Blue, pProp, eAct );
}

int CABSCamera::OnGainExtra(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  S_BITSHIFT_PARAMS sBSP = {0};
  u32 dwSize = sizeof(sBSP);
  u32 rc;

  if (eAct == MM::BeforeGet)
  {
    rc = GET_RC( CamUSB_GetFunction( FUNC_BITSHIFT, &sBSP, &dwSize, 0, 0, 0, 0, deviceNo() ), deviceNo());
    if ( IsNoError( rc ) )
    {
      const char* szValue;
      switch ( sBSP.bBitShift )
      {
      case 4: szValue = g_Gain_Extra_x16; break;
      case 3: szValue = g_Gain_Extra_x8; break;
      case 2: szValue = g_Gain_Extra_x4; break;
      case 1: szValue = g_Gain_Extra_x2; break;
      default: szValue = g_Gain_Extra_x1; break;
      }

      pProp->Set( szValue );
    }
    return convertApiErrorCode( rc, __FUNCTION__ );
  }
  else if (eAct == MM::AfterSet)
  {
    std::string strValue;

    pProp->Get( strValue );
    if ( strValue == g_Gain_Extra_x1) sBSP.bBitShift = 0;
    else if ( strValue == g_Gain_Extra_x2) sBSP.bBitShift = 1;
    else if ( strValue == g_Gain_Extra_x4) sBSP.bBitShift = 2;
    else if ( strValue == g_Gain_Extra_x8) sBSP.bBitShift = 3;
    else if ( strValue == g_Gain_Extra_x16) sBSP.bBitShift = 4;

    rc = GET_RC( CamUSB_SetFunction( FUNC_BITSHIFT, &sBSP, dwSize, 0, 0, 0, 0, deviceNo() ), deviceNo());
    return convertApiErrorCode( rc, __FUNCTION__ );
  }
  return DEVICE_OK;
}

int CABSCamera::OnWhiteBalance(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  if (eAct == MM::BeforeGet)
  {
    pProp->Set( "" );
  }
  else if (eAct == MM::AfterSet)
  {
    S_WHITE_BALANCE_PARAMS sWBP = {0};
    u32 dwSize = sizeof( sWBP );
    u32 rc = retOK;

    std::string strWhiteBalanceMode;

    pProp->Get( strWhiteBalanceMode );

    if ( g_WhiteBalance_OnePush == strWhiteBalanceMode )
    {
      pProp->Set(""); // clear content

      sWBP.dwMode = WB_MODE_ONE_PUSH;
      rc = GET_RC( CamUSB_SetFunction( FUNC_WHITE_BALANCE, &sWBP, dwSize, 0, 0, 0, 0, deviceNo() ), deviceNo());

      if ( FALSE == IsNoError( rc ) )
        return convertApiErrorCode( rc, __FUNCTION__ );
    }
  }
  return DEVICE_OK;
}

int CABSCamera::OnAutoExposure(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  S_AUTOEXPOSURE_PARAMS sAExpP = {0};
  u32 dwSize = sizeof(sAExpP);
  u32 rc;

  if (eAct == MM::BeforeGet)
  {
    rc = GET_RC( CamUSB_GetFunction( FUNC_AUTOEXPOSURE, &sAExpP, &dwSize, 0, 0, 0, 0, deviceNo() ), deviceNo());
    if ((sAExpP.bAECActive | sAExpP.bAGCActive | sAExpP.bAICActive) != 0)
      pProp->Set( g_AutoExposure_On );
    else
      pProp->Set( g_AutoExposure_Off );
  }
  else if (eAct == MM::AfterSet)
  {
    std::string autoExposure;

    pProp->Get( autoExposure );

    rc = GET_RC( CamUSB_GetFunction( FUNC_AUTOEXPOSURE, &sAExpP, &dwSize, 0, 0, 0, 0, deviceNo() ), deviceNo());

    if ( g_AutoExposure_Off == autoExposure )
    {
      sAExpP.bAECActive = sAExpP.bAGCActive = 0;
    }
    else if ( g_AutoExposure_On == autoExposure )
    {
      sAExpP.bAECActive = sAExpP.bAGCActive = 1;
      sAExpP.wTargetBrightness = 127;
    }

    rc = GET_RC( CamUSB_SetFunction( FUNC_AUTOEXPOSURE, &sAExpP, dwSize, 0, 0, 0, 0, deviceNo() ), deviceNo());

    if ( FALSE == IsNoError( rc ) )
      return convertApiErrorCode( rc, __FUNCTION__ );
  }

  return DEVICE_OK;
}

int CABSCamera::OnAutoExposureOptions(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  S_AUTOEXPOSURE_PARAMS sAExpP = {0};
  u32 dwSize = sizeof(sAExpP);
  u32 rc;


  if (eAct == MM::BeforeGet)
  {
    rc = GET_RC( CamUSB_GetFunction( FUNC_AUTOEXPOSURE, &sAExpP, &dwSize, 0, 0, 0, 0, deviceNo() ), deviceNo());
    if ((sAExpP.wOptions & AEXP_OPTION_ANTIFLICKER_50HZ) == AEXP_OPTION_ANTIFLICKER_50HZ)
      pProp->Set( g_AutoExposure_Opt50Hz );
    else if ((sAExpP.wOptions & AEXP_OPTION_ANTIFLICKER_60HZ) == AEXP_OPTION_ANTIFLICKER_60HZ)
      pProp->Set( g_AutoExposure_Opt60Hz );
    else
      pProp->Set( g_AutoExposure_OptNone );
  }
  else if (eAct == MM::AfterSet)
  {
    std::string strValue;

    pProp->Get( strValue );

    rc = GET_RC( CamUSB_GetFunction( FUNC_AUTOEXPOSURE, &sAExpP, &dwSize, 0, 0, 0, 0, deviceNo() ), deviceNo());

    // clear options
    sAExpP.wOptions &= ~(AEXP_OPTION_ANTIFLICKER_50HZ | AEXP_OPTION_ANTIFLICKER_60HZ);

    // set selected option
    if ( g_AutoExposure_Opt50Hz == strValue )
    {
      sAExpP.wOptions |= AEXP_OPTION_ANTIFLICKER_50HZ;
    }
    else if ( g_AutoExposure_Opt60Hz == strValue )
    {
      sAExpP.wOptions |= AEXP_OPTION_ANTIFLICKER_60HZ;
    }

    rc = GET_RC( CamUSB_SetFunction( FUNC_AUTOEXPOSURE, &sAExpP, dwSize, 0, 0, 0, 0, deviceNo() ), deviceNo());

    if ( FALSE == IsNoError( rc ) )
      return convertApiErrorCode( rc, __FUNCTION__ );
  }
  return DEVICE_OK;
}

int CABSCamera::OnSharpen(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  S_EDGE_ENHANCE_PARAMS sEEP = {0};
  u32 dwSize = sizeof(sEEP);
  u32 rc;

  if (eAct == MM::BeforeGet)
  {
    rc = GET_RC( CamUSB_GetFunction( FUNC_EDGE_ENHANCE, &sEEP, &dwSize, 0, 0, 0, 0, deviceNo() ), deviceNo());

    const char* szValue;

    switch (sEEP.dwEnhanceLevel)
    {
    case 3: szValue = g_Sharpen_Level3; break;
    case 2: szValue = g_Sharpen_Level2; break;
    case 1: szValue = g_Sharpen_Level1; break;
    default: szValue = g_Sharpen_Off; break;
    }

    pProp->Set( szValue );
  }
  else if (eAct == MM::AfterSet)
  {
    std::string strEdgeEnhance;

    pProp->Get( strEdgeEnhance );

    if ( g_Sharpen_Off == strEdgeEnhance )
    {
      sEEP.dwEnhanceLevel = 0;
    }
    else if ( g_Sharpen_Level1 == strEdgeEnhance )
    {
      sEEP.dwEnhanceLevel = 1;
    }
    else if ( g_Sharpen_Level2 == strEdgeEnhance )
    {
      sEEP.dwEnhanceLevel = 2;
    }
    else if ( g_Sharpen_Level3 == strEdgeEnhance )
    {
      sEEP.dwEnhanceLevel = 3;
    }

    rc = GET_RC( CamUSB_SetFunction( FUNC_EDGE_ENHANCE, &sEEP, dwSize, 0, 0, 0, 0, deviceNo() ), deviceNo());

    if ( FALSE == IsNoError( rc ) )
      return convertApiErrorCode( rc, __FUNCTION__ );
  }
  return DEVICE_OK;
}

#define BAYER_DEMOSAIC_3X3 0 //!< default 3x3 Matrix interpolation
#define BAYER_DEMOSAIC_5X5 1 //!< better 5x5 Matrix interpolation
USBAPI BOOL CCONV CamUSB_SetBayerMode( DWORD dwBayerMode, BYTE nDevNr = 0);

int CABSCamera::OnBayerDemosaic(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  if (eAct == MM::BeforeGet)
  {
    pProp->Set( m_BayerDemosaicMode );
  }
  else if (eAct == MM::AfterSet)
  {
    u32 rc;
    std::string strBayerDemosaic;

    pProp->Get( strBayerDemosaic );

    u08 captureMode, countImages;
    rc = GET_RC( CamUSB_GetCaptureMode( &captureMode, &countImages, deviceNo()), deviceNo() );


    if ( g_BayerDemosaic_Fast == strBayerDemosaic )
    {
      rc = GET_RC( CamUSB_SetBayerMode( BAYER_DEMOSAIC_3X3, deviceNo() ), deviceNo());
      m_BayerDemosaicMode = g_BayerDemosaic_Fast;
    }
    else if ( g_BayerDemosaic_Quality == strBayerDemosaic )
    {
      rc = GET_RC( CamUSB_SetBayerMode( BAYER_DEMOSAIC_5X5, deviceNo() ), deviceNo());
      m_BayerDemosaicMode = g_BayerDemosaic_Quality;
    }

    rc = GET_RC( CamUSB_SetCaptureMode( captureMode, countImages, deviceNo(), 0, 0), deviceNo() );

    if ( FALSE == IsNoError( rc ) )
      return convertApiErrorCode( rc, __FUNCTION__ );
  }
  return DEVICE_OK;
}

int CABSCamera::OnBlackLevel(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  S_BLACKLEVEL_PARAMS sBLP = {0};
  u32 dwSize = sizeof(sBLP);
  u32 rc;

  if (eAct == MM::BeforeGet)
  {
    rc = GET_RC( CamUSB_GetFunction( FUNC_BLACKLEVEL, &sBLP, &dwSize, 0, 0, 0, 0, deviceNo() ), deviceNo());

    const char* szValue;

    switch (sBLP.bEnable)
    {
    case 1: szValue = g_BlacklevelOn; break;
    default: szValue = g_BlacklevelOff; break;
    }

    pProp->Set( szValue );
  }
  else if (eAct == MM::AfterSet)
  {
    std::string strValue;

    pProp->Get( strValue );

    if ( g_BlacklevelOn == strValue )
    {
      sBLP.bEnable = 1;
    }
    else if ( g_BlacklevelOff == strValue )
    {
      sBLP.bEnable = 0;
    }

    rc = GET_RC( CamUSB_SetFunction( FUNC_BLACKLEVEL, &sBLP, dwSize, 0, 0, 0, 0, deviceNo() ), deviceNo());

    if ( FALSE == IsNoError( rc ) )
      return convertApiErrorCode( rc, __FUNCTION__ );
  }
  return DEVICE_OK;
}

int CABSCamera::OnProfileLoad(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  if (eAct == MM::BeforeGet)
  {
    pProp->Set( strProfile_.c_str() );
  }
  else if (eAct == MM::AfterSet)
  {
    u32 rc = retFILE_OPEN;

    pProp->Get( strProfile_ );

    if (( strProfile_.size() > 0 ) && ( PROFILE_NONE != strProfile_ ) )
    {
      // restore profile
      rc = loadProfile( strProfile_.c_str(), PROFILE_SETTINGSNAME );

      if ( FALSE == IsNoError( rc ) )
      {
        strProfile_.clear();
        pProp->Set( strProfile_.c_str() );
        return convertApiErrorCode( rc, __FUNCTION__ );
      }
      else // update internal members
      {
        initTransposeFunctions(true);
        checkForModifiedCameraParameter();
        updateShadingCorrectionState();
      }
    }
    else
      strProfile_.clear();
  }
  return DEVICE_OK;
}

int CABSCamera::OnProfileSave(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  if (eAct == MM::BeforeGet)
  {
    pProp->Set( PROFILE_NONE );
  }
  else if (eAct == MM::AfterSet)
  {
    u32 rc = retFILE_OPEN;
    string strProfile;
    pProp->Get( strProfile );

    if (( strProfile_.size() > 0 ) && ( PROFILE_NONE != strProfile_ ) )
    {
      // restore profile
      rc = saveProfile( strProfile.c_str(), PROFILE_SETTINGSNAME );

      if ( FALSE == IsNoError( rc ) )
      {
        pProp->Set( strProfile.c_str() );
        return convertApiErrorCode( rc, __FUNCTION__ );
      }
      else
      {
        // update profiles to load
        fillProfileList();
      }
    }
  }
  return DEVICE_OK;
}

int CABSCamera::OnStdResolution(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  u32 rc;
  u64 stdResID;

  if (eAct == MM::BeforeGet)
  {
    rc = GET_RC( CamUSB_GetStandardRes2( &stdResID, deviceNo() ), deviceNo());

    if ( IsNoError( rc ) )
      pProp->Set( findNameById( stdResID ).c_str() );
    else
      pProp->Set( "" );
  }
  else if (eAct == MM::AfterSet)
  {

    std::string stdResName;

    pProp->Get( stdResName );

    stdResID = findIdByName( stdResName );

    if ( STDRES2_NONE != stdResID )
    {
      // restore profile
      rc = GET_RC( CamUSB_SetStandardRes2( stdResID, deviceNo() ), deviceNo());

      if ( FALSE == IsNoError( rc ) )
      {
        OnStdResolution( pProp, MM::BeforeGet );
        return convertApiErrorCode( rc, __FUNCTION__ );
      }
      else // update internal members
      {
        checkForModifiedCameraParameter();
      }
    }
    else
    {
      pProp->Set( "" );
    }
  }
  return DEVICE_OK;
}

int CABSCamera::OnShadingCorrection(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  u32 rc, size;
  S_SHADING_CORRECTION_PARAMS sShaCorP = {0};

  if (eAct == MM::BeforeGet)
  {
    const char* state = "";
    S_SHADING_CORRECTION_RETVALS sShaCorR = {0};

    sShaCorP.dwAction = SHCO_TYPE_STATE | SHCO_FUNC_GET;

    size = sizeof(sShaCorR);

    rc = GET_RC( CamUSB_GetFunction( FUNC_SHADING_CORRECTION, &sShaCorR, &size, &sShaCorP, size, 0, 0, deviceNo() ), deviceNo());

    if ( IsNoError( rc ) )
    {
      state = g_ShadingCorrection_Off;

      // shading active?
      if ( (sShaCorR.dwAction & SHCO_FUNC_ENABLE) == SHCO_FUNC_ENABLE )
      {
        if ((sShaCorR.dwFlag & (SHCO_FLAG_USE_DARK_REF | SHCO_FLAG_USE_WHITE_REF)) == (SHCO_FLAG_USE_DARK_REF | SHCO_FLAG_USE_WHITE_REF))
        {
          state = g_ShadingCorrection_Both;
        }
        else if ((sShaCorR.dwFlag & (SHCO_FLAG_USE_DARK_REF | SHCO_FLAG_USE_WHITE_REF)) == (SHCO_FLAG_USE_WHITE_REF))
        {
          state = g_ShadingCorrection_WhiteRef;
        }
        else if ((sShaCorR.dwFlag & (SHCO_FLAG_USE_DARK_REF | SHCO_FLAG_USE_WHITE_REF)) == (SHCO_FLAG_USE_DARK_REF))
        {
          state = g_ShadingCorrection_DarkRef;
        }
      }
    }

    pProp->Set( state );
  }
  else if (eAct == MM::AfterSet)
  {
    std::string shadingMode;

    pProp->Get( shadingMode );

    if ( g_ShadingCorrection_Off == shadingMode )
    {
      sShaCorP.dwAction = SHCO_ACTION_DISABLE;
    }
    else if ( g_ShadingCorrection_Both == shadingMode )
    {
      sShaCorP.dwAction = SHCO_ACTION_ENABLE;
      sShaCorP.dwFlag = SHCO_FLAG_USE_WHITE_REF | SHCO_FLAG_USE_DARK_REF;
    }
    else if ( g_ShadingCorrection_DarkRef == shadingMode )
    {
      sShaCorP.dwAction = SHCO_ACTION_ENABLE;
      sShaCorP.dwFlag = SHCO_FLAG_USE_DARK_REF;
    }
    else if ( g_ShadingCorrection_WhiteRef == shadingMode )
    {
      sShaCorP.dwAction = SHCO_ACTION_ENABLE;
      sShaCorP.dwFlag = SHCO_FLAG_USE_WHITE_REF;
    }

    // send shading settings to camera (api)
    if ( 0 != sShaCorP.dwAction )
    {
      size = sizeof(sShaCorP);
      rc = GET_RC( CamUSB_SetFunction( FUNC_SHADING_CORRECTION, &sShaCorP, size, 0, 0, 0, 0, deviceNo() ), deviceNo());

      if ( FALSE == IsNoError( rc ) )
      {
        OnShadingCorrection( pProp, MM::BeforeGet );
        return convertApiErrorCode( rc, __FUNCTION__ );
      }
    }
  }
  return DEVICE_OK;

}

int CABSCamera::OnShadingCorrectionSetup(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  if (eAct == MM::BeforeGet)
  {
    pProp->Set( "" );
  }
  else if (eAct == MM::AfterSet)
  {
    u32 rc, size;
    S_SHADING_CORRECTION_PARAMS sShaCorP = {0};

    std::string shadingSetup;

    pProp->Get( shadingSetup );
    pProp->Set( "" ); // clear input (reset to default)

    if ( g_ShadingCorrection_CreateDarkRef == shadingSetup )
    {
      sShaCorP.dwAction = SHCO_ACTION_CREATE_DARK_REF;
    }
    else if ( g_ShadingCorrection_CreateWhiteRef == shadingSetup )
    {
      sShaCorP.dwAction = SHCO_ACTION_CREATE_WHITE_REF;
      sShaCorP.dwFlag = SHCO_FLAG_EXPOSURE;
      CamUSB_GetExposureTime( &sShaCorP.dwExposure, deviceNo() );
    }

    // send shading settings to camera (api)
    if ( 0 != sShaCorP.dwAction )
    {
      size = sizeof(sShaCorP);
      rc = GET_RC( CamUSB_SetFunction( FUNC_SHADING_CORRECTION, &sShaCorP, size, 0, 0, 0, 0, deviceNo() ), deviceNo());

      if ( FALSE == IsNoError( rc ) )
        return convertApiErrorCode( rc, __FUNCTION__ );

      // modify values
      updateShadingCorrectionState();
    }
  }
  return DEVICE_OK;
}

int CABSCamera::OnGamma(MM::PropertyBase* pProp, MM::ActionType eAct )
{
  S_GAMMA_PARAMS sGaP = {0};
  u32 rc, size = sizeof(sGaP);

  if (eAct == MM::BeforeGet)
  {
    rc = GET_RC( CamUSB_GetFunction( FUNC_GAMMA, &sGaP, &size, 0, 0, 0, 0, deviceNo() ), deviceNo());
    if ( IsNoError( rc ) )
    {
      pProp->Set( sGaP.dwGamma / 1000.0 );
    }
    return convertApiErrorCode( rc, __FUNCTION__ );
  }
  else if (eAct == MM::AfterSet)
  {
    double value;

    pProp->Get(value);
    sGaP.dwGamma = (u32)(value * 1000.0f + 0.5f);

    rc = GET_RC( CamUSB_SetFunction( FUNC_GAMMA, &sGaP, size, 0, 0, 0, 0, deviceNo() ), deviceNo());

    if ( FALSE == IsNoError( rc ) )
    {
      OnGamma( pProp, MM::BeforeGet );
    }

    return convertApiErrorCode( rc, __FUNCTION__ );
  }
  return DEVICE_OK;
}

int CABSCamera::OnHueSaturationCommon(const char* propName, MM::PropertyBase* pProp, MM::ActionType eAct )
{
  u32 rc = retOK;
  u32 size;
  S_HUE_SATURATION_PARAMS sHueSatP = {0};
  size = sizeof(sHueSatP);


  if (eAct == MM::BeforeGet)
  {
    rc = GET_RC( CamUSB_GetFunction( FUNC_HUE_SATURATION, &sHueSatP, &size, 0, 0, 0, 0, deviceNo() ), deviceNo());
    if ( IsNoError( rc ) )
    {
      double value;
      if ( propName == g_Keyword_Hue )
        value = sHueSatP.wHue;
      else
        value = sHueSatP.wSaturation;

      pProp->Set( value );
    }
    else
      pProp->Set( "" );
  }
  else if (eAct == MM::AfterSet)
  {
    double value;
    pProp->Get( value );

    rc = GET_RC( CamUSB_GetFunction( FUNC_HUE_SATURATION, &sHueSatP, &size, 0, 0, 0, 0, deviceNo() ), deviceNo());

    if ( IsNoError( rc ) )
    {
      if ( propName == g_Keyword_Hue )
        sHueSatP.wHue = (i16) value;
      else
        sHueSatP.wSaturation = (i16) value;

      rc = GET_RC( CamUSB_SetFunction( FUNC_HUE_SATURATION, &sHueSatP, size, 0, 0, 0, 0, deviceNo() ), deviceNo());
    }
    else
      pProp->Set( "" );
  }

  return convertApiErrorCode( rc, __FUNCTION__ );
}

int CABSCamera::OnHue(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  return OnHueSaturationCommon( g_Keyword_Hue, pProp, eAct );
}

int CABSCamera::OnSaturation(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  return OnHueSaturationCommon( g_Keyword_Saturation, pProp, eAct );
}

int CABSCamera::OnColorCorrection(MM::PropertyBase* pProp, MM::ActionType eAct )
{
  const char* szValue = g_CCM_Off;
  u32 rc = retOK;
  u32 size;
  S_COLOR_CORRECTION_PARAMS sCCP = {0};
  size = sizeof(sCCP);

  if (eAct == MM::BeforeGet)
  {
    rc = GET_RC( CamUSB_GetFunction( FUNC_COLOR_CORRECTION, &sCCP, &size, 0, 0, 0, 0, deviceNo() ), deviceNo());

    if ( IsNoError( rc ) )
    {
      if ( 0 == sCCP.bActive )
      {
        szValue = g_CCM_Off;
      }
      else
      {
        for ( u32 n=0; n < colCorLst_.size(); n++ )
        {
          if ( 0 == memcmp( colCorLst_[n].sCCP.wCCMatrix, sCCP.wCCMatrix, sizeof( sCCP.wCCMatrix ) ) )
          {
            szValue = colCorLst_[n].strName.c_str();
            break;
          }
        }
      }
    }

    pProp->Set( szValue );

  }
  else if (eAct == MM::AfterSet)
  {
    std::string ccmFileName;
    pProp->Get( ccmFileName );

    for ( u32 n=0; n < colCorLst_.size(); n++ )
    {
      if ( colCorLst_[n].strName == ccmFileName )
      {
        szValue = colCorLst_[n].strName.c_str();
        rc = GET_RC( CamUSB_SetFunction( FUNC_COLOR_CORRECTION, &colCorLst_[n].sCCP, size, 0, 0, 0, 0, deviceNo() ), deviceNo());
        break;
      }
    }

    if ( !IsNoError( rc ) )
      pProp->Set( g_CCM_Off );
  }
  return convertApiErrorCode( rc, __FUNCTION__ );
}

int CABSCamera::OnTriggerCommon(const char* propName, MM::PropertyBase* pProp, MM::ActionType eAct )
{
  u32 rc = retOK;

  if (eAct == MM::BeforeGet)
  {
    if ( g_Keyword_TriggerInport == propName )
    {
      pProp->Set( triggerPortName_.c_str() );
    }
    else if ( g_Keyword_TriggerDelay == propName )
    {
      pProp->Set( triggerPortDelay_ );
    }
    else
    {
      pProp->Set( triggerPortPolarity_.c_str() );
    }
  }
  else if (eAct == MM::AfterSet)
  {
    std::string portName, portPolarity;
    long portDelay;

    // gather all data
    if ( g_Keyword_TriggerInport == propName )
    {
      pProp->Get( portName );
      GetPropertyString( g_Keyword_TriggerPolarity, portPolarity );
      GetProperty( g_Keyword_TriggerDelay, portDelay );
    }
    else if ( g_Keyword_TriggerDelay == propName )
    {
      GetPropertyString( g_Keyword_TriggerInport, portName );
      GetPropertyString( g_Keyword_TriggerPolarity, portPolarity );
      pProp->Get( portDelay );
    }
    else
    {
      GetPropertyString( g_Keyword_TriggerInport, portName );
      pProp->Get( portPolarity );
      GetProperty( g_Keyword_TriggerDelay, portDelay );
    }

    u32 size;
    S_IO_PORT_PARAMS ioPP = {0};
    bool bSkipSetPort = false;

    // set trigger input
    if ( portName != g_IOPort_None )
    {
      ioPP.wPortIndex = ioPortMap_[ portName ];
      ioPP.wPortFeatures |= (0 != portDelay) ? PORT_FEATURE_DELAY : 0;
      ioPP.wPortFeatures |= (portPolarity != g_Polarity_HighActive) ? PORT_FEATURE_POL_ACTLOW : PORT_FEATURE_POL_ACTHIGH;
      ioPP.wPortType = PORT_TYPE_TRIGGER_IN;
      ioPP.dwDelay = portDelay;
    }
    else // disable trigger input
    {
      bSkipSetPort = (triggerPortName_ == g_IOPort_None);

      ioPP.wPortIndex = ioPortMap_[ triggerPortName_ ];
      ioPP.wPortFeatures |= (triggerPortPolarity_ != g_Polarity_HighActive) ? PORT_FEATURE_POL_ACTLOW : PORT_FEATURE_POL_ACTHIGH;
      ioPP.wPortType = PORT_TYPE_INPUT;
    }

    if ( false == bSkipSetPort )
    {
      size = sizeof(ioPP);
      rc = GET_RC( CamUSB_SetFunction( FUNC_IO_PORTS, &ioPP, size, 0, 0, 0, 0, deviceNo() ), deviceNo());

      if ( IsNoError( rc ) )
      {
        triggerPortName_ = portName;
        triggerPortPolarity_ = portPolarity;
        triggerPortDelay_ = portDelay;
      }
      else // restore last value
      {
        UpdateProperty( propName );
      }
    }
  }
  return convertApiErrorCode( rc, __FUNCTION__ );
}

int CABSCamera::OnTriggerInport(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  return OnTriggerCommon( g_Keyword_TriggerInport, pProp, eAct );
};

int CABSCamera::OnTriggerPolarity(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  return OnTriggerCommon( g_Keyword_TriggerPolarity, pProp, eAct );
};

int CABSCamera::OnTriggerDelay(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  return OnTriggerCommon( g_Keyword_TriggerDelay, pProp, eAct );
};

int CABSCamera::OnStrobeCommon(const char* propName, MM::PropertyBase* pProp, MM::ActionType eAct )
{
  u32 rc = retOK;

  if (eAct == MM::BeforeGet)
  {
    if ( g_Keyword_StrobeOutport == propName )
    {
      pProp->Set( strobePortName_.c_str() );
    }
    else if ( g_Keyword_StrobePolarity == propName )
    {
      pProp->Set( strobePortPolarity_.c_str() );
    }
  }
  else if (eAct == MM::AfterSet)
  {
    std::string portName, portPolarity;

    // gather all data
    if ( g_Keyword_StrobeOutport == propName )
    {
      pProp->Get( portName );
      GetPropertyString( g_Keyword_StrobePolarity, portPolarity );
    }
    else
    {
      GetPropertyString( g_Keyword_StrobeOutport, portName );
      pProp->Get( portPolarity );
    }

    u32 size;
    S_IO_PORT_PARAMS ioPP = {0};
    bool bSkipSetPort = false;

    // set strobe outport
    if ( portName != g_IOPort_None )
    {
      ioPP.wPortIndex = ioPortMap_[ portName ];
      ioPP.wPortFeatures |= (portPolarity != g_Polarity_HighActive) ? PORT_FEATURE_POL_ACTLOW : PORT_FEATURE_POL_ACTHIGH;
      ioPP.wPortType = PORT_TYPE_STROBE_OUT;
    }
    else // disable strobe outport
    {
      bSkipSetPort = (strobePortName_ == g_IOPort_None);

      ioPP.wPortIndex = ioPortMap_[ strobePortName_ ];
      ioPP.wPortFeatures |= (strobePortPolarity_ != g_Polarity_HighActive) ? PORT_FEATURE_POL_ACTLOW : PORT_FEATURE_POL_ACTHIGH;
      ioPP.wPortType = PORT_TYPE_OUTPUT;
    }

    if (false == bSkipSetPort)
    {
      size = sizeof(ioPP);
      rc = GET_RC( CamUSB_SetFunction( FUNC_IO_PORTS, &ioPP, size, 0, 0, 0, 0, deviceNo() ), deviceNo());

      if ( IsNoError( rc ) )
      {
        strobePortName_ = portName;
        strobePortPolarity_ = portPolarity;
      }
      else // restore last value
      {
        UpdateProperty( propName );
      }
    }
  }
  return convertApiErrorCode( rc, __FUNCTION__ );
}

int CABSCamera::OnStrobeOutport(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  return OnStrobeCommon( g_Keyword_StrobeOutport, pProp, eAct );
};

int CABSCamera::OnStrobePolarity(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  return OnStrobeCommon( g_Keyword_StrobePolarity, pProp, eAct );
};

int CABSCamera::OnTemperature(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  if (eAct == MM::BeforeGet)
  {
    u32 rc, size;
    S_TEMPERATURE_PARAMS sTP = {0};
    S_TEMPERATURE_RETVALS sTR = {0};
    sTP.wSensorIndex = (u16)temperatureIndex_;
    size = sizeof(sTR);

    rc = GET_RC( CamUSB_GetFunction( FUNC_TEMPERATURE, &sTR, &size, &sTP, size, 0, 0, deviceNo() ), deviceNo());
    if ( IsNoError( rc ) )
    {
      string temperature;
      str::sprintf( temperature, "%3.2f °C \0\0", sTR.wSensorValue / temperatureUnit_ );
      pProp->Set( temperature.c_str() );
    }
    return convertApiErrorCode( rc, __FUNCTION__ );
  }
  else if (eAct == MM::AfterSet)
  {
    return convertApiErrorCode( retFUNCSET, __FUNCTION__ );
  }
  return DEVICE_OK;
}

int CABSCamera::OnTriggerDevice(MM::PropertyBase* pProp, MM::ActionType eAct)
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


///////////////////////////////////////////////////////////////////////////////
// Private CABSCamera methods
///////////////////////////////////////////////////////////////////////////////

void CABSCamera::generateEmptyImage( CAbsImgBuffer& img )
{
  MMThreadGuard g( img ); // lock the image

  if (img.Height() == 0 || img.Width() == 0 || img.Depth() == 0)
    return;

  unsigned char* pBuf = const_cast<unsigned char*>(img.GetPixels());
  if ( 0 != pBuf )
  {
    memset(pBuf, 0, img.Height()*img.Width()*img.Depth());

    if ( 8 == bitDepth_ )
      *pBuf = 255;
    else
      *((USHORT*)pBuf) = ((1 << bitDepth_) - 1);
  }
}

u32 CABSCamera::getCameraImage( CAbsImgBuffer & img )
{
  S_IMAGE_HEADER* imageHeader;
  u08* imagePointer;
  u32 size;
  u32 rc = retMEM;

  // lock image buffer and prepare for writting
  if ( img.getNewImgBuffer( imagePointer, imageHeader, size ) )
  {
    MM::TimeoutMs timeout( GetCurrentMMTime(), max( 2500, (unsigned long) (0.015 * readoutUs_) ) );
    do
    {
      if ( false == timeout.expired( GetCurrentMMTime() ) )
        rc = GET_RC( CamUSB_GetImage( &imagePointer, &imageHeader, size, deviceNo()), deviceNo() );
      else
        rc = retNOIMGAVAIL;

    } while ( (retOK != rc) && (TRUE == IsNoError( rc )) );

    if ( IsNoError( rc ) )
    {
      img.releasNewImgBuffer( imagePointer, imageHeader );
    }
    else
    {
      rc = rc;
      img.abortNewImgBuffer();
    }
  }

  // release unsused buffers
  // first read the pointer from CAbsImgBuffer than release it
  while ( img.getUnusedExternalBuffer( imagePointer, imageHeader ) )
  {
    if (!CamUSB_ReleaseImage( imagePointer, imageHeader, deviceNo()))
    {
      imagePointer = imagePointer;
    }
  }

  // if user stops live mode image aquisition is aborted 
  // => return retOK because the user requested the abort
  if ( retUSER_ABORT == rc )
    rc = retOK;

  return rc;

}


int CABSCamera::OnCameraSelection(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  if (eAct == MM::BeforeGet)
  {
    ClearAllowedValues("Camera");
    // it is importent to ask which camera's are connected, this is only possible by
    // communicate with the API - DLL
    // - first check if they is available
    // - if not no camera available / or no dll
    if ( isApiDllAvailable())
    {
      CCameraList cCameraList;
      getAvailableCameras( cCameraList );

      if ( cCameraList.size() > 0)
      {
        CCameraList::iterator iter = cCameraList.begin();
        while ( iter != cCameraList.end() )
        {
          // attention: "iter->sVersion.szDeviceName" isn't a zero terminated string in any case
          // so getDeviceNameString has to be used to work around this problem
          AddAllowedValue( "Camera", buildCameraDeviceID( iter->sVersion.dwSerialNumber, getDeviceNameString( iter->sVersion ) ).c_str() ); // no camera yet
          iter++;
        }
      }
      else
      {
        AddAllowedValue( "Camera", "No camera found!"); // no camera yet
      }
    }
    else
    {
      string strInfo = g_ApiDllName + string(" not found!");
      AddAllowedValue( "Camera", strInfo.c_str() ); // no API DLL found
    }
  }
  else if (eAct == MM::AfterSet)
  {
    string cameraDeviceID;
    if ( false == pProp->Get( cameraDeviceID ) )
      cameraDeviceID.clear();

    setCameraDeviceID( cameraDeviceID );
  }
  return DEVICE_OK;
}

bool CABSCamera::isApiDllAvailable( void )
{
  bool bAvailable = false;
  // Wrap all calls to delay-load DLL functions inside an SEH
  // try-except block
  __try
  {
    CamUSB_GetLastError( GLOBAL_DEVNR );
    bAvailable = true;
  }
  __except ( DelayLoadDllExceptionFilter(GetExceptionInformation()) )
  {
    std::cerr << "CABSCamera::isApiDllAvailable() failed => dll not found" << std::endl;
  }
  return bAvailable;
}

string CABSCamera::buildCameraDeviceID( unsigned long serialNumber, const string & deviceName )
{
  string cameraDeviceID;
  str::sprintf( cameraDeviceID, "#%05X", serialNumber );
  return (deviceName + cameraDeviceID);
}

bool CABSCamera::isInitialized( void ) const
{
  return initialized_;
}

void CABSCamera::setInitialized( const bool bInitialized )
{
  initialized_ = bInitialized;
}

bool CABSCamera::isColor( void ) const
{
  return colorCamera_;
}

void CABSCamera::setColor( const bool colorCamera )
{
  colorCamera_ = colorCamera;
}

void CABSCamera::setCameraDeviceID( string cameraDeviceID )
{
  cameraDeviceID_ = cameraDeviceID;
}

string CABSCamera::cameraDeviceID( void ) const
{
  return cameraDeviceID_;
}

void CABSCamera::setDeviceNo( int deviceNo )
{
  deviceNo_ = deviceNo;
}

u08 CABSCamera::deviceNo( void ) const
{
  return (u08) deviceNo_;
}

int CABSCamera::apiToMMErrorCode( unsigned long apiErrorNumber ) const
{
  if ( retOK == apiErrorNumber )
    return (DEVICE_OK);

  return (ERR_CAMERA_API_BASE + apiErrorNumber);
}

int CABSCamera::mmToApiErrorCode( unsigned long mmErrorNumber ) const
{
  if (( DEVICE_OK == mmErrorNumber ) || ( mmErrorNumber <= ERR_CAMERA_API_BASE) )
    return (retOK);

  return ( mmErrorNumber - ERR_CAMERA_API_BASE );
}

int CABSCamera::convertApiErrorCode( unsigned long apiErrorNumber, const char* functionName )
{
  string errorMessage;
  errorMessage.resize( 260, 0 );
  CamUSB_GetErrorString( (char *) errorMessage.c_str(), (u32)errorMessage.size(), apiErrorNumber );

  str::ResizeByZeroTermination( errorMessage );

  if ( IsNoError( apiErrorNumber ) )
  {
    if ( retOK != apiErrorNumber )
      errorMessage = "API-DLL Warning: " + errorMessage;
  }
  else
    errorMessage = "API-DLL Error: " + errorMessage;

  if ( 0 != functionName )
    errorMessage += string(" (") + string(functionName) + string(")");

  SetErrorText( apiToMMErrorCode( apiErrorNumber ), errorMessage.c_str() );

  if (retOK != apiErrorNumber ) // log only for warnings or errors
    LogMessage(errorMessage);

  return apiToMMErrorCode( apiErrorNumber );
}

// scan for device
void CABSCamera::getAvailableCameras( CCameraList & cCameraList )
{
  const int iCntElements = 16;
  cCameraList.resize( iCntElements );
  int nCamerasFound = CamUSB_GetCameraListEx( &cCameraList[0], (u32)cCameraList.size() );
  cCameraList.resize( min(nCamerasFound, iCntElements ) );
}

// ----------------------------------------------------------------------------

unsigned long CABSCamera::getCap( unsigned __int64 functionId, void* & capability )
{
  u32 rc, dataSize;
  rc = GET_RC( CamUSB_GetFunctionCaps( functionId, 0, &dataSize, deviceNo() ), deviceNo());
  if ( retOK == rc )
  {
    capability = new u08[ dataSize ];
    rc = GET_RC( CamUSB_GetFunctionCaps( functionId, capability, &dataSize, deviceNo() ), deviceNo());
  }
  return rc;
}

// ----------------------------------------------------------------------------

std::string CABSCamera::deviceName() const
{
  return std::string( (char*) cameraVersion_.szDeviceName );
}

// ----------------------------------------------------------------------------

std::string CABSCamera::profileDir() const
{
  std::string strDir;
  char *szDir = 0;
  strDir.resize(MM::MaxStrLength, 0);
  szDir = (char*)strDir.c_str();

  if (ABSTools::GetFolderPath(CSIDL_COMMON_APPDATA, szDir, MM::MaxStrLength))
  {
    std::string strDeviceName( deviceName() );
    str::Trim( strDeviceName );

    PathAppendA(szDir, "ABS GmbH");
    PathAppendA(szDir, "Image Capture");
    PathAppendA(szDir, "CameraProfiles");
    PathAppendA(szDir, strDeviceName.c_str() );
  }

  str::ResizeByZeroTermination( strDir );

  return szDir;
}

// ----------------------------------------------------------------------------

std::string CABSCamera::ccmDir() const
{
  std::string strDir;
  char *szDir = 0;
  strDir.resize( MM::MaxStrLength , 0);
  szDir = (char*)strDir.c_str();

  if (ABSTools::GetFolderPath(CSIDL_COMMON_APPDATA, szDir, MM::MaxStrLength))
  {
    PathAppendA(szDir, "ABS GmbH");
    PathAppendA(szDir, "Image Capture");
    PathAppendA(szDir, "CCM");
  }

  str::ResizeByZeroTermination( strDir );

  return szDir;
}

// ----------------------------------------------------------------------------

std::string CABSCamera::profilePath( const char * szProfileName )
{
  std::string strPath = profileDir();
  char *szPath = 0;
  strPath.resize( MM::MaxStrLength );
  szPath = (char*)strPath.c_str();

  PathAppendA( szPath, szProfileName);

  str::ResizeByZeroTermination(strPath);

  return strPath;
}

// ----------------------------------------------------------------------------

void CABSCamera::fillProfileList( )
{
  strProfile_.clear();
  strProfileLst_.clear();

  // clear all entries and rescan for profiles
  ClearAllowedValues( g_Keyword_ProfilesLoad );

  if ( retOK == CamUSB_ProfileGetList( deviceNo(),
    profileDir(),
    strProfileLst_ ) )
  {
    AddAllowedValue(g_Keyword_ProfilesLoad, PROFILE_NONE );
    for ( u32 n=0; n < strProfileLst_.size(); n++ )
    {
      AddAllowedValue(g_Keyword_ProfilesLoad, strProfileLst_[n].c_str() );
    }
  }
}

// ----------------------------------------------------------------------------


void CABSCamera::fillCCMList( )
{
  CStdStringLst ccmFileList;
  std::string ccmFileMask = ccmDir() + "\\*.ccm";

  colCorLst_.clear();
  colCorLst_.push_back( CColorCorrectionItem( g_CCM_Off ) );

  // scan for *.ccm files on disk
  if ( ABSTools::findFiles( ccmFileMask, ccmFileList ) > 0 )
  {
    for ( u32 n=0; n < ccmFileList.size(); n++ )
    {
      S_CCM ccm = {0};
      CCCMFile ccmFile;
      std::string ccmFilePath = ccmDir() + "\\" + ccmFileList[n];

      if ( ccmFile.read( (char*) ccmFilePath.c_str( ), ccm ) )
      {
        if ( cameraVersion_.wSensorType == ccm.wSensorType )
        {
          colCorLst_.push_back( CColorCorrectionItem( ccmFileList[n], ccm ) );
        }
      }
    }
  }

  // output to gui
  ClearAllowedValues( g_Keyword_CCM );
  for ( u32 n=0; n < colCorLst_.size(); n++ )
  {
    AddAllowedValue(g_Keyword_CCM, colCorLst_[n].strName.c_str() );
  }
}

// ----------------------------------------------------------------------------

void CABSCamera::fillStandardResolutionList( )
{
  AddAllowedValue( g_Keyword_StdResolution, "" );
  for ( u32 n=0; n < stdResolutionLst_.size(); n++ )
  {
    AddAllowedValue( g_Keyword_StdResolution, stdResolutionLst_[n].strName.c_str() );
  }
}

// ----------------------------------------------------------------------------

void CABSCamera::fillIOPorts()
{
  S_IO_PORT_CAPS * ioPortCaps = 0;

  AddAllowedValue( g_Keyword_TriggerInport, g_IOPort_None );
  AddAllowedValue( g_Keyword_StrobeOutport, g_IOPort_None );

  ioPortMap_.clear();

  getCap( FUNC_IO_PORTS, (void* &)ioPortCaps );

  if ( 0 != ioPortCaps )
  {
    for ( u32 n=0; n < ioPortCaps->wPorts; n++ )
    {
      std::string portName = std::string( (char*) ioPortCaps->sPortCap[n].szPortName );

      if ( (ioPortCaps->sPortCap[n].wPortTypeMask & PORT_TYPE_TRIGGER_IN) == PORT_TYPE_TRIGGER_IN )
      {
        AddAllowedValue( g_Keyword_TriggerInport, portName.c_str() );
        SetPropertyLimits( g_Keyword_TriggerDelay, 0, ioPortCaps->sPortCap[n].wMaxDelay );
      }
      else if ( (ioPortCaps->sPortCap[n].wPortTypeMask & PORT_TYPE_STROBE_OUT) == PORT_TYPE_STROBE_OUT )
      {
        AddAllowedValue( g_Keyword_StrobeOutport, portName.c_str() );
      }
      ioPortMap_[portName] = (u16)n;
    }
  }

  updateIOPorts();

  SAFE_DELETE_ARRAY( ioPortCaps );
}

// ----------------------------------------------------------------------------

void CABSCamera::updateIOPorts()
{
  S_IO_PORT_PARAMS ioPP = {0};
  S_IO_PORT_RETVALS ioPR = {0};
  u32 size = sizeof( ioPR );
  u32 rc;

  std::string strobeOutport = g_IOPort_None;
  std::string triggerOutport = g_IOPort_None;

  // read configuration port by port
  for ( u32 n=0; n < ioPortMap_.size(); n++ )
  {
    // request speciffic port config by index
    ioPP.wPortIndex = (u16) n;

    rc = GET_RC( CamUSB_GetFunction( FUNC_IO_PORTS, &ioPR, &size, &ioPP, sizeof(ioPP), 0, 0, deviceNo() ), deviceNo());
    if ( FALSE == IsNoError( rc ) )
      break;

    // check for trigger-inport
    if (( ioPR.wPortType & PORT_TYPE_TRIGGER_IN ) == PORT_TYPE_TRIGGER_IN )
    {
      triggerOutport = ioPortNameByIndex(n);

      if ((ioPR.wPortFeatures & PORT_FEATURE_POL_ACTHIGH) == PORT_FEATURE_POL_ACTHIGH )
      {
        triggerPortPolarity_ = g_Polarity_HighActive;
        SetProperty( g_Keyword_TriggerPolarity, g_Polarity_HighActive );
      }
      else
      {
        triggerPortPolarity_ = g_Polarity_LowActive;
        SetProperty( g_Keyword_TriggerPolarity, g_Polarity_LowActive );
      }

      triggerPortDelay_ = ioPR.dwDelay;
      SetProperty( g_Keyword_TriggerDelay, str::asDec<u32>( ioPR.dwDelay ).c_str() );
    }
    // check for strobe outport
    else if (( ioPR.wPortType & PORT_TYPE_STROBE_OUT ) == PORT_TYPE_STROBE_OUT )
    {
      strobeOutport = ioPortNameByIndex(n);

      if ((ioPR.wPortFeatures & PORT_FEATURE_POL_ACTHIGH) == PORT_FEATURE_POL_ACTHIGH )
      {
        strobePortPolarity_ = g_Polarity_HighActive;
        SetProperty( g_Keyword_StrobePolarity, g_Polarity_HighActive );
      }
      else
      {
        strobePortPolarity_ = g_Polarity_LowActive;
        SetProperty( g_Keyword_StrobePolarity, g_Polarity_LowActive );
      }
    }
  }

  triggerPortName_ = triggerOutport;
  strobePortName_ = strobeOutport;

  // apply strobe / trigger port
  SetProperty( g_Keyword_StrobeOutport, strobeOutport.c_str() );
  SetProperty( g_Keyword_TriggerInport, triggerOutport.c_str() );
}

// ----------------------------------------------------------------------------

std::string CABSCamera::ioPortNameByIndex( u32 ioPortIndex )
{
  CIOPortNameToIndexMap::iterator iter = ioPortMap_.begin();

  while ( iter != ioPortMap_.end() )
  {
    if ( ioPortIndex == iter->second )
      return iter->first;
    iter++;
  }
  return "";
}

// ----------------------------------------------------------------------------

int CABSCamera::checkForModifiedCameraParameter()
{
  u32 pixeltype, pixeltypeNew;
  S_RESOLUTION_INFO sResInfo = {0};
  S_RESOLUTION_PARAMS sResP = {0};
  u32 dwSize = sizeof( sResP );
  u32 bpp, spp, usedBpp, nComponents;
  u32 rc;

  // read pixel type
  rc = GET_RC( CamUSB_GetPixelType( &pixeltype, deviceNo() ), deviceNo() );
  if ( FALSE == IsNoError( rc ) )
    goto checkForModifiedCameraParameter_Done;

  // resolution
  rc = GET_RC( CamUSB_GetFunction( FUNC_RESOLUTION, &sResP, &dwSize, 0, 0, 0, 0, deviceNo() ), deviceNo());
  if ( FALSE == IsNoError( rc ) )
    goto checkForModifiedCameraParameter_Done;

  usedBpp = GetUsedBpp( pixeltype );
  bpp = GetBpp( pixeltype );

  switch ( bpp )
  {
  case 64:
    nComponents = 4;
    spp = 8;
    switch (usedBpp)
    {
      //case 16: newPixelType = PIX_BGRA16_PACKED; break;
    case 14: pixeltypeNew = PIX_BGRA14_PACKED; break;
    case 12: pixeltypeNew = PIX_BGRA12_PACKED; break;
    case 10:
    default: pixeltypeNew = PIX_BGRA10_PACKED; break;
    }
    break;
  case 32:
  case 24:
    nComponents = 4;
    spp = 4;
    pixeltypeNew = PIX_BGRA8_PACKED;
    break;

  case 16:
    nComponents = 1;
    spp = 2;
    switch (usedBpp)
    {
    case 16: pixeltypeNew = PIX_MONO16; break;
    case 14: pixeltypeNew = PIX_MONO14; break;
    case 12: pixeltypeNew = PIX_MONO12; break;
    case 10:
    default: pixeltypeNew = PIX_MONO10; break;
    }
    break;
  default:
    nComponents = 1;
    spp = 1;
    pixeltypeNew = PIX_MONO8;
    break;
  }

  // set pixel type
  rc = GET_RC( CamUSB_SetPixelType( pixeltypeNew, deviceNo() ), deviceNo() );
  if ( FALSE == IsNoError( rc ) )
    goto checkForModifiedCameraParameter_Done;

  sResInfo.sResIn = sResP;
  sResInfo.dwPixelType = pixeltypeNew;
  rc = GET_RC( CamUSB_GetCameraResolutionInfo( &sResInfo, deviceNo() ), deviceNo());
  if ( FALSE == IsNoError( rc ) )
    goto checkForModifiedCameraParameter_Done;

  // set resolution
  rc = GET_RC( CamUSB_SetFunction( FUNC_RESOLUTION, &sResInfo.sResOut, dwSize, 0, 0, 0, 0, deviceNo() ), deviceNo());
  if ( FALSE == IsNoError( rc ) )
  {
    framerate_ = 0.0;
    UpdateProperty( g_Keyword_Framerate );
    goto checkForModifiedCameraParameter_Done;
  }

  const bool bIsCapturing = IsCapturing();
  // calc internal mamebers and update them / resize image
  {
    if ( bIsCapturing )
    {
      StopSequenceAcquisition();
    }

    MMThreadGuard g( img_ );

    nComponents_ = nComponents;
    long binSize = GetSkipBinValue( sResInfo.sResOut.dwBin, X_BIN_MASK, X_BIN_SHIFT );
    long skipSize= GetSkipBinValue( sResInfo.sResOut.dwSkip, X_SKIP_MASK, X_SKIP_SHIFT );
    binSize_ = max( binSize, skipSize );
    bitDepth_ = usedBpp;
    roiX_ = sResInfo.sResOut.wOffsetX;
    roiY_ = sResInfo.sResOut.wOffsetY;

    if ( bSwapXY_ )
      img_.Resize( sResInfo.wImgHeight, sResInfo.wImgWidth, spp, bitDepth_);
    else
      img_.Resize( sResInfo.wImgWidth, sResInfo.wImgHeight, spp, bitDepth_);
  }

  if ( bIsCapturing )
    StartSequenceAcquisition( numImages_, interval_ms_, stopOnOverflow_ );

checkForModifiedCameraParameter_Done:
  return convertApiErrorCode( rc, __FUNCTION__ );
}

// ----------------------------------------------------------------------------

bool CABSCamera::buildStandardResolutionList( )
{
  u32 rc;
  u64 stdResMask;
  stdResolutionLst_.clear();

  rc = GET_RC( CamUSB_GetStandardRes2Caps( &stdResMask, deviceNo() ), deviceNo());
  if ( IsNoError( rc ) )
  {
    u32 number = 1;
    u64 stdResID;
    std::string stdResName;

    for ( i32 i=63; i >= 0; i-- )
    {
      stdResID = ((u64)1) << i;

      if ( stdResMask & stdResID )
      {
        str::sprintf( stdResName, "%2d) %s", number++, getStandardResolutionName( stdResID ).c_str() );
        stdResolutionLst_.push_back( CStandardResolutionItem( stdResName, stdResID ) );
      }
    }
  }
  return (stdResolutionLst_.size() > 0 );
}

// ----------------------------------------------------------------------------

u64 CABSCamera::findIdByName( const std::string & stdResName )
{
  for ( u32 n=0; n < stdResolutionLst_.size(); n++ )
  {
    if ( stdResolutionLst_[n].strName == stdResName )
      return stdResolutionLst_[n].qwID;
  }
  return STDRES2_NONE;
}

std::string CABSCamera::findNameById( const u64 & qwID )
{
  for ( u32 n=0; n < stdResolutionLst_.size(); n++ )
  {
    if ( stdResolutionLst_[n].qwID == qwID )
      return stdResolutionLst_[n].strName;
  }
  return "";
}

// ----------------------------------------------------------------------------

std::string CABSCamera::getStandardResolutionName( const u64 stdResID ) const
{
  std::string stdResName;
  stdResName.resize( 128, 0 );
  GetStdRes2String( stdResID, (char*) stdResName.c_str(), (u32) stdResName.size() );
  str::ResizeByZeroTermination( stdResName );
  return stdResName;
}

// ----------------------------------------------------------------------------

u32 CABSCamera::updateCameraTransposeCorrection( void )
{
  u32 rc = retOK;
  bool bSwap = false, bMirrorX = false, bMirrorY = false;
  // check current transpose settings (is transpose active)
  if ( true == IsPropertyEqualTo( MM::g_Keyword_Transpose_Correction, "1" ) )
  {
    bSwap = IsPropertyEqualTo( MM::g_Keyword_Transpose_SwapXY, "1" );
    bMirrorX = IsPropertyEqualTo( MM::g_Keyword_Transpose_MirrorX, "1" );
    bMirrorY = IsPropertyEqualTo( MM::g_Keyword_Transpose_MirrorY, "1" );
  }

  // transpose settings allready applied?
  if ( ( bMirrorX != bMirrorX_ ) || ( bMirrorY != bMirrorY_ ) )
  {
    // update settings
    u32 dwSize;
    S_FLIP_PARAMS sFlipP = {0};
    sFlipP.wFlipMode |= (bMirrorX) ? FLIP_HORIZONTAL : FLIP_NONE;
    sFlipP.wFlipMode |= (bMirrorY) ? FLIP_VERTICAL : FLIP_NONE;

    // set resolution
    dwSize = sizeof( sFlipP );
    rc = GET_RC( CamUSB_SetFunction( FUNC_FLIP, &sFlipP, dwSize, 0, 0, 0, 0, deviceNo() ), deviceNo());

    // remember new settings on success
    if ( IsNoError( rc ) )
    {
      bMirrorX_ = bMirrorX;
      bMirrorY_ = bMirrorY;
    }
  }

  if ( bSwap != bSwapXY_ )
  {
    // update settings
    u32 dwSize;
    S_ROTATE_REMAP_PARAMS sRRP = {0};
    
    if ( bSwap )
    {
      sRRP.dwMode = ROTREM_MODE_ROTATE_CENTER;
      sRRP.dwFlag = ROTREM_FLAG_OPT_IMG_SIZE;
      sRRP.dwInterpolation = ROTREM_INTER_NN;
      sRRP.fAngle = -90.0;

    }
    // set resolution
    dwSize = sizeof( sRRP );
    rc = GET_RC( CamUSB_SetFunction( FUNC_ROTATE_REMAP, &sRRP, dwSize, 0, 0, 0, 0, deviceNo() ), deviceNo());

    // remember new settings on success
    if ( IsNoError( rc ) )
    {
      bSwapXY_ = bSwap;

      checkForModifiedCameraParameter();
    }
  }
  return rc;
}

void CABSCamera::updateShadingCorrectionState( void )
{
  u32 rc, size;
  S_SHADING_CORRECTION_PARAMS sShaCorP = {0};
  S_SHADING_CORRECTION_RETVALS sShaCorR = {0};

  ClearAllowedValues( g_Keyword_ShadingCorrection );
  ClearAllowedValues( g_Keyword_ShadingCorrection_Setup );
  // add default values
  AddAllowedValue(g_Keyword_ShadingCorrection, g_ShadingCorrection_Off );
  AddAllowedValue(g_Keyword_ShadingCorrection_Setup, "" );
  AddAllowedValue(g_Keyword_ShadingCorrection_Setup, g_ShadingCorrection_CreateDarkRef );

  sShaCorP.dwAction = SHCO_TYPE_STATE | SHCO_FUNC_GET;

  size = sizeof(sShaCorR);

  rc = GET_RC( CamUSB_GetFunction( FUNC_SHADING_CORRECTION, &sShaCorR, &size, &sShaCorP, size, 0, 0, deviceNo() ), deviceNo());

  if ( IsNoError( rc ) )
  {
    if ( (sShaCorR.dwFlag & SHCO_FLAG_DARK_REF_SET) == SHCO_FLAG_DARK_REF_SET )
    {
      AddAllowedValue(g_Keyword_ShadingCorrection, g_ShadingCorrection_DarkRef );
      AddAllowedValue(g_Keyword_ShadingCorrection_Setup, g_ShadingCorrection_CreateWhiteRef );
    }

    if ( (sShaCorR.dwFlag & SHCO_FLAG_WHITE_REF_SET) == SHCO_FLAG_WHITE_REF_SET )
    {
      AddAllowedValue(g_Keyword_ShadingCorrection, g_ShadingCorrection_WhiteRef );
    }

    if ( (sShaCorR.dwFlag & (SHCO_FLAG_DARK_REF_SET | SHCO_FLAG_WHITE_REF_SET)) == (SHCO_FLAG_DARK_REF_SET | SHCO_FLAG_WHITE_REF_SET) )
    {
      AddAllowedValue(g_Keyword_ShadingCorrection, g_ShadingCorrection_Both );
    }
  }
}

void CABSCamera::setAllowedGamma( )
{
  S_GAMMA_CAPS * gammaCaps = 0;

  getCap( FUNC_GAMMA, (void* &)gammaCaps );

  if ( 0 != gammaCaps )
  {
    SetPropertyLimits( g_Keyword_Gamma, gammaCaps->dwMin / 1000.0 , gammaCaps->dwMax / 1000.0);
  }

  SAFE_DELETE_ARRAY( gammaCaps );
}

void CABSCamera::setAllowedHueSaturation( )
{
  S_HUE_SATURATION_CAPS * hueSatCaps = 0;

  getCap( FUNC_HUE_SATURATION, (void* &)hueSatCaps );

  if ( 0 != hueSatCaps )
  {
    SetPropertyLimits( g_Keyword_Hue, hueSatCaps->wHueMin , hueSatCaps->wHueMax);
    SetPropertyLimits( g_Keyword_Saturation, hueSatCaps->wSatMin , hueSatCaps->wSatMax);
  }

  SAFE_DELETE_ARRAY( hueSatCaps );
}

void CABSCamera::setAllowedFramerates( )
{
  S_FRAMERATE_CAPS * framerateCaps = 0;

  ClearAllowedValues( g_Keyword_Framerate );

  getCap( FUNC_FRAMERATE, (void* &)framerateCaps );

  if ( 0 != framerateCaps )
  {
    AddAllowedValue(g_Keyword_Framerate, g_Framerate_FreeRun );

    if ((framerateCaps->sFramerateRange[0].dwMin != 0) &&
        (framerateCaps->sFramerateRange[framerateCaps->dwCountRanges-1].dwMax != 0) )
    {
      u32 dwFps;
      string temp;
      map<f32, u32>::iterator iter;
      map<f32, u32> cFpsMap;

      dwFps = framerateCaps->sFramerateRange[0].dwMin;

      for(u32 l=0; l < framerateCaps->dwCountRanges; l++)
      {
        if ( framerateCaps->sFramerateRange[l].dwStep != 0)
        {
            for (u32 dwFps = framerateCaps->sFramerateRange[l].dwMin; dwFps <= framerateCaps->sFramerateRange[l].dwMax; dwFps += framerateCaps->sFramerateRange[l].dwStep)
            {
                u32 dwStep = framerateCaps->sFramerateRange[l].dwStep;

                if ((dwFps <=      (  25 * dwStep)) || (cFpsMap.size() == 0))  // step 1
                {
                    cFpsMap.insert( make_pair(dwFps/1000.0f, dwFps));
                }
                else if (dwFps <=(  50 * dwStep))  // step 5
                {
                    if ((dwFps % (5 * dwStep)) == 0) cFpsMap.insert( make_pair(dwFps/1000.0f, dwFps));
                }
                else if (dwFps < ( 100 * dwStep))  // step 10
                {
                    if ((dwFps % (10 * dwStep)) == 0) cFpsMap.insert( make_pair(dwFps/1000.0f, dwFps));
                }
                else if (dwFps < ( 300 * dwStep))  // step 25
                {
                    if ((dwFps % (25 * dwStep)) == 0) cFpsMap.insert( make_pair(dwFps/1000.0f, dwFps));
                }
                else                              // step 100
                {
                    if ((dwFps % (100 * dwStep)) == 0) cFpsMap.insert( make_pair(dwFps/1000.0f, dwFps));
                }
            }
        }
        else if ((0 != framerateCaps->sFramerateRange[l].dwMin) &&
                 (framerateCaps->sFramerateRange[l].dwMin == framerateCaps->sFramerateRange[l].dwMax))
        {
          cFpsMap.insert( make_pair(framerateCaps->sFramerateRange[l].dwMin/1000.0f, framerateCaps->sFramerateRange[l].dwMin));
        }
      }

      // add the maximum fps
      dwFps = framerateCaps->sFramerateRange[framerateCaps->dwCountRanges-1].dwMax;
      cFpsMap.insert( make_pair(dwFps/1000.0f, dwFps) );

      // put fps to combo box
      for (iter = cFpsMap.begin(); iter != cFpsMap.end(); iter++)
      {
        // float formated to string
        str::sprintf( temp, "%03.1f", iter->first );

        if (iter->second < 10000)
          temp = "0" + temp;

        AddAllowedValue(g_Keyword_Framerate, temp.c_str(), iter->second );
      }
    }
  }

  SAFE_DELETE_ARRAY( framerateCaps );
}

u32 CABSCamera::loadProfile( const char * szProfileName, const char * szSettingsName )
{
  u32 rc = retFILE_OPEN;

  std::string strProfilePath = profilePath( szProfileName );
  if ( strProfilePath.size() != 0 )
  {
    // restore last use profile
    rc = GET_RC(CamUSB_LoadCameraSettingsFromFile( (char*)strProfilePath.c_str(), (char *) szSettingsName, deviceNo()), deviceNo());

    // disable ColorRemapping
    if ( IsNoError(rc) )
    {
      S_COLOR_MAPPING_PARAMS sCMP = {0};
      sCMP.dwMode = COMA_STATE_DISABLED;
      CamUSB_SetFunction( FUNC_COLOR_MAPPING, &sCMP, sizeof(sCMP), 0, 0, 0, 0, deviceNo());
    }
  }
  return rc;
};

// ----------------------------------------------------------------------------

u32 CABSCamera::saveProfile( const char * szProfileName, const char * szSettingsName )
{
  u32 rc = retFILE_OPEN;

  // create directory before try to write to it
  if ( ABSTools::CreateFolder( (char*) profileDir().c_str() ) )
  {
    ABSTools::ChangeACLtoAllowUserRW( (char*) profileDir().c_str() );
  }

  std::string strProfilePath = profilePath( szProfileName );
  if ( 0 != strProfilePath.size() )
  {
    // save as last use profile
    rc = GET_RC(CamUSB_SaveCameraSettingsToFile( (char*)strProfilePath.c_str(), (char *) szSettingsName, deviceNo()), deviceNo());
  }
  return rc;
}

// ----------------------------------------------------------------------------

string CABSCamera::getFramerateString( void ) const
{
  if (0.0 == getFramerate() )
    return string( g_Framerate_FreeRun );

  string framerate;
  str::sprintf( framerate, "%1.1f", getFramerate() );

  return framerate;
}

// ----------------------------------------------------------------------------

string CABSCamera::getDeviceNameString( void ) const
{
  return getDeviceNameString( cameraVersion_ );
}

// ----------------------------------------------------------------------------

string CABSCamera::getDeviceNameString( const S_CAMERA_VERSION & sCamVer ) const
{
  string deviceName( (const char*)sCamVer.szDeviceName, CNT_ELEMENTS(sCamVer.szDeviceName) );
  str::ResizeByZeroTermination( deviceName );
  return deviceName;
}

// ----------------------------------------------------------------------------

double CABSCamera::getFramerate() const
{
  return framerate_;
}

// ----------------------------------------------------------------------------

void CABSCamera::setFramerate( double fps )
{
  if (fps != framerate_)
  {
    u16 wFps;
    u32 dwRC;

    if ( fps == 0.0 )
      wFps = 0; // free run
    else
      wFps = (u16)(fps + 0.5);

    dwRC = GET_RC( CamUSB_SetFramerateLimit( &wFps, deviceNo() ), deviceNo() );
    if ( IsNoError( dwRC ) )
    {
      framerate_ = wFps;
    }
  }
}

// ----------------------------------------------------------------------------

int CABSCamera::SetProperty(const char* name, const char* value)
{
  int nRet = __super::SetProperty( name, value );

  if ( DEVICE_OK == nRet )
  {
    CStringVector::iterator iter = find( transposePropertyNames_.begin(), transposePropertyNames_.end(), name );

    if ( iter != transposePropertyNames_.end() )
    {
      // check current transpose settings and apply them at camera if necessary
      u32 dwRC = updateCameraTransposeCorrection( );
      nRet = convertApiErrorCode( dwRC, __FUNCTION__ );
    }
  }

  return nRet;
}


void CABSCamera::initTransposeFunctions( bool bInitialize )
{
  transposePropertyNames_.clear();
  
  if ( bInitialize )
  {
    u32 rc;
    u32 dwSize;

    if ( isSupported( FUNC_FLIP ) || isSupported( FUNC_ROTATE_REMAP ) )
      transposePropertyNames_.push_back( MM::g_Keyword_Transpose_Correction );

    if ( isSupported( FUNC_ROTATE_REMAP ) )
    {
      bSwapXY_ = false;

      transposePropertyNames_.push_back( MM::g_Keyword_Transpose_SwapXY );

      // read settings
      
      S_ROTATE_REMAP_PARAMS sRRP = {0};
      
      dwSize = sizeof( sRRP );
      rc = GET_RC( CamUSB_GetFunction( FUNC_ROTATE_REMAP, &sRRP, &dwSize, 0, 0, 0, 0, deviceNo() ), deviceNo());

      if ( IsNoError( rc ) )
      {
        if ( (sRRP.dwMode == ROTREM_MODE_ROTATE_CENTER) &&
             (sRRP.dwFlag == ROTREM_FLAG_OPT_IMG_SIZE)  &&
             (sRRP.dwInterpolation == ROTREM_INTER_NN)  &&
             (sRRP.fAngle == -90.0f ) )
        {
          bSwapXY_ = true;
        }
        else
        {
          sRRP.dwMode = ROTREM_MODE_OFF;
          rc = GET_RC( CamUSB_SetFunction( FUNC_ROTATE_REMAP, &sRRP, dwSize, 0, 0, 0, 0, deviceNo() ), deviceNo());
        }
      }
    }

    if ( isSupported( FUNC_FLIP ) )
    {
      transposePropertyNames_.push_back( MM::g_Keyword_Transpose_MirrorX );
      transposePropertyNames_.push_back( MM::g_Keyword_Transpose_MirrorY );

      S_FLIP_PARAMS sFlipP = {0};
      dwSize = sizeof( sFlipP );
      rc = GET_RC( CamUSB_GetFunction( FUNC_FLIP, &sFlipP, &dwSize, 0, 0, 0, 0, deviceNo() ), deviceNo());

      if ( IsNoError( rc ) )
      {
        bMirrorX_ = ((sFlipP.wFlipMode & FLIP_HORIZONTAL) != FLIP_NONE);
        bMirrorY_ = ((sFlipP.wFlipMode & FLIP_VERTICAL) != FLIP_NONE);
      }
    }

    // show current transpose status
    __super::SetProperty( MM::g_Keyword_Transpose_Correction, (bSwapXY_ || bMirrorX_ || bMirrorY_ ) ? "1" : "0" );
    __super::SetProperty( MM::g_Keyword_Transpose_MirrorX,    (bMirrorX_ )                          ? "1" : "0" );
    __super::SetProperty( MM::g_Keyword_Transpose_MirrorY,    (bMirrorY_ )                          ? "1" : "0" );
    __super::SetProperty( MM::g_Keyword_Transpose_SwapXY,     (bSwapXY_ )                           ? "1" : "0" );

  }

}

