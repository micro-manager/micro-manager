/////////////////////////////////////////////////////////////////////////////
// Name:        ABSCamera.cpp
// Purpose:     Implementierung der Kameraklasse als Adapter für µManager
// Author:      Michael Himmelreich
// Created:     31. Juli 2007
// Copyright:   (c) Michael Himmelreich
// Project:     ConfoVis
/////////////////////////////////////////////////////////////////////////////


// ------------------------------ Includes --------------------------------------
//

#ifdef WIN32
   #define WIN32_LEAN_AND_MEAN
   #include <windows.h>
   #define snprintf _snprintf 
#endif

#include <string>
#include <math.h>
#include <sstream>

		
#include "./ABSCamera.h"						// main header files
#include "./../../MMDevice/ModuleInterface.h"   // module interface
#include "./include/camusb_api.h"				// ABS Camera API
#include "./include/camusb_api_util.h"			// ABS Camera API
#include "./include/camusb_api_ext.h"			// ABS Camera API

// heartbeat prototype
USBAPI BOOL CCONV CamUSB_HeartBeatCfg( u32 dwCMD, u32 &dwValue, u08 nDevNr);


// ----------------------------------------------------------------------------
//
#include "ABSDelayLoadDll.h"						// add support for Delay loading of an dll
#pragma comment(lib, "./include/camusb_api_hal.lib")	// add ABS Camera support
// don't work since VC2005 => put it manually to project settings
// #pragma comment(linker, "/DelayLoad:CamUSB_API.Dll")	// remove CamUSB-API dll from default dll import

#include "./include/safeutil.h"						// macros to delete release pointers safely


// ------------------------------ Defines --------------------------------------
//

using namespace std;

// External names used used by the rest of the system
// to load particular device from the "DemoCamera.dll" library
const char* g_CameraDeviceNameBase = "ABSCam";


SupportedPixelTypes g_sSupportedPixelTypesMono[] =
{
   {"8Bit",   PIX_MONO8},
   {"10Bit",  PIX_MONO10},
   {"12Bit",  PIX_MONO12}   
};

SupportedPixelTypes g_sSupportedPixelTypesColor[] =
{
  //{"8Bit",  PIX_RGB8_PLANAR}, 
  {"8Bit",  PIX_BGRA8_PACKED},      
  // {"10Bit", PIX_BGRA10_PACKED},
  // {"12Bit", PIX_BGRA12_PACKED}
};

// constants for naming color modes
const char* g_ColorMode_Grayscale = "Grayscale";
const char* g_ColorMode_Color	  = "Color";

// ------------------------------ Macros --------------------------------------
//
// return the error code of the function instead of Successfull or Failed!!!
#define GET_RC(func, dev)  ((!func) ? CamUSB_GetLastError(dev) : retOK)

// ------------------------------ Prototypes -----------------------------------
//
typedef std::pair<std::string, std::string> DeviceInfo;
extern std::vector<DeviceInfo> g_availableDevices;
// clears all device names provided by abs camera
void ClearAllDeviceNames(void);

// create an ABSCamera object
ABSCamera* CreateABSDevice(const char* szDeviceName);

static bool g_bInitializeModuleDataDone = false;
// ------------------------------ DLL main --------------------------------------
//
// windows DLL entry code
#ifdef WIN32
  BOOL APIENTRY DllMain( HANDLE /*hModule*/, DWORD  ul_reason_for_call, LPVOID /*lpReserved*/ ) 
  {
    switch (ul_reason_for_call)
   	{
      case DLL_PROCESS_ATTACH:
  	  case DLL_THREAD_ATTACH:
   	  case DLL_THREAD_DETACH:
   	  case DLL_PROCESS_DETACH:
   		break;
   	}
    return TRUE;
  }
#endif



///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////

MODULE_API void InitializeModuleData()
{
	u32		dwCountCameras;
	u32		dwElements = 0;	
	S_CAMERA_LIST_EX* pCamLstEx = NULL;
	
	char* szCameraDescription;	// description of the camera			
	char* szDeviveName;			// used devicename

  g_bInitializeModuleDataDone = false;

	// allocate strings
	szDeviveName		= new char[MAX_PATH + 1];
	szCameraDescription = new char[MAX_PATH + 1];
	// init strings
	ZeroMemory(szDeviveName,	 sizeof(char) * (MAX_PATH + 1));
	ZeroMemory(szCameraDescription, sizeof(char) * (MAX_PATH + 1));

  /*
	// fire the debugger directly or crash without one
	__asm
	{
      int 3;
	 }
   */

	// remove all old device names
	ClearAllDeviceNames();

	// Wrap all calls to delay-load DLL functions inside an SEH
	// try-except block
	__try 
	{
		// suche angeschlossene ABS Camera's
		dwCountCameras = CamUSB_GetCameraListEx( pCamLstEx, dwElements );
	
		if (dwCountCameras > 0)
		{
			dwElements= dwCountCameras;
			pCamLstEx = new S_CAMERA_LIST_EX[dwElements];      
			dwCountCameras = CamUSB_GetCameraListEx( pCamLstEx, dwElements );
		}

		// put the device names in
		if (dwCountCameras > 0)
		{
			for (DWORD dwCam = 0; dwCam < dwCountCameras; dwCam++)
			{
				snprintf( szDeviveName,		   MAX_PATH, "%s%02d", g_CameraDeviceNameBase, dwCam);
				snprintf( szCameraDescription, MAX_PATH, "ABS %s #%X", (char*)pCamLstEx[dwCam].sVersion.szDeviceName, pCamLstEx[dwCam].sVersion.dwSerialNumber); 											
				
				AddAvailableDeviceName( szDeviveName, szCameraDescription );
			}		
		}
		else
		{
			snprintf( szDeviveName,		   MAX_PATH, "%s", g_CameraDeviceNameBase);
			snprintf( szCameraDescription, MAX_PATH, "%s", "ABS GmbH no camera device connected!");

			AddAvailableDeviceName( szDeviveName, szCameraDescription );
		}

    g_bInitializeModuleDataDone = true;
	}
	__except (DelayLoadDllExceptionFilter(GetExceptionInformation())) 
	{
      
		// Prepare to exit elegantly  and inform the "others"		
		snprintf( szDeviveName,		   MAX_PATH, "%s", g_CameraDeviceNameBase);
		snprintf( szCameraDescription, MAX_PATH, "%s", "CamUSB_API_hal.dll not found or wrong version! Device won't work!");

		AddAvailableDeviceName( szDeviveName, szCameraDescription );
	}
		
	SAFE_DELETE_ARRAY(szDeviveName);
	SAFE_DELETE_ARRAY(szCameraDescription);
	SAFE_DELETE_ARRAY(pCamLstEx);
}

MODULE_API MM::Device* CreateDevice(const char* szDeviceName)
{
  // true if the camusb_api.dll wasn't found
	bool bApiNotAvailable = FALSE;

	// Wrap all calls to delay-load DLL functions inside an SEH
	// try-except block
	__try 
	{
		// call read last error code => to force the delay load to load CamUBS_API.dll => if fail return an error
		CamUSB_GetLastError( GLOBAL_DEVNR ); 
	}
	__except (DelayLoadDllExceptionFilter(GetExceptionInformation())) 
	{      
		// Prepare to exit elegantly  and inform the "others"		
		bApiNotAvailable = true;
	}

  // ABSCamera object will be created within CreateABSDevice to allow "structured exception handling"
	// return camera object (if possible)
	return (bApiNotAvailable) ? NULL : CreateABSDevice(szDeviceName);
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
	// Wrap all calls to delay-load DLL functions inside an SEH
	// try-except block
	__try 
	{
		SAFE_DELETE(pDevice);  
	}
	__except (DelayLoadDllExceptionFilter(GetExceptionInformation())) 
	{
  		// Prepare to exit elegantly	
	}
}

ABSCamera* CreateABSDevice(const char* szDeviceName)
{
  
  if (!g_bInitializeModuleDataDone) InitializeModuleData();

	// init camera return object pointer as ...supplied name not recognized
	ABSCamera* pABSCamera = NULL;

	// decide which device class to create based on the deviceName parameter
	if (NULL != szDeviceName)       
	{
	  int iDeviceNumber   = 0;
	  size_t iBaseNameLenght = strlen(g_CameraDeviceNameBase);
	  
	  // should the first camera be used
	  if ( (strncmp(g_CameraDeviceNameBase, szDeviceName, min( strlen(szDeviceName), iBaseNameLenght+2)) == 0) ||                    // first one
		   (-1 != sscanf_s(szDeviceName + iBaseNameLenght, "%d", &iDeviceNumber)) ) // specific camera should be used
	  {
		// create camera with specific camera id
		pABSCamera = (ABSCamera*) new ABSCamera(iDeviceNumber, szDeviceName);
	  }      
	}

	return pABSCamera;
}


///////////////////////////////////////////////////////////////////////////////
// ABSCamera implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~

const double ABSCamera::fNominalPixelSizeUm = 1.0;

/**
 * ABSCamera constructor.
 * Setup default all variables and create device properties required to exist
 * before intialization. In this case, no such properties were required. All
 * properties will be created in the Initialize() method.
 *
 * As a general guideline Micro-Manager devices do not access hardware in the
 * the constructor. We should do as little as possible in the constructor and
 * perform most of the initialization in the Initialize() method.
 */
ABSCamera::ABSCamera(int iDeviceNumber, const char* szDeviceName) : 
   initialized( false ),   
   deviceNumber( 0 ),
   cameraFunctionMask( 0 ),
   numberOfChannels( 1 ),
   resolutionCap( NULL ),
   flipCap( NULL ),
   pixelTypeCap( NULL ),
   gainCap( NULL ),
   exposureCap( NULL ),
   autoExposureCap( NULL ),   
   temperatureCap( NULL ),
   framerateCap( NULL ),
   bColor( false ),
   bAbortGetImageCalled( false ),
   bSetGetExposureActive( false ),
   cameraProvidedImageBuffer( NULL )

{
   MM_THREAD_INITIALIZE_GUARD(&lockImgBufPtr);

   this->cPixeltypes.clear();

   // remember device name and set the device index    
   m_szDeviceName = std::string(szDeviceName);
   deviceNumber   = (BYTE) iDeviceNumber;

   // call the base class method to set-up default error codes/messages
   InitializeDefaultErrorMessages();

   // set sequence acquisition to stop
   StopSequenceAcquisition();
}

/**
 * ABSCamera destructor.
 * If this device used as intended within the Micro-Manager system,
 * Shutdown() will be always called before the destructor. But in any case
 * we need to make sure that all resources are properly released even if
 * Shutdown() was not called.
 */
ABSCamera::~ABSCamera()
{
  if ( this->initialized ) this->Shutdown();  
  MM_THREAD_DELETE_GUARD(&lockImgBufPtr);
}

/**
 * Obtains device name.
 * Required by the MM::Device API.
 */
void ABSCamera::GetName(char* name) const
{
   if (!g_bInitializeModuleDataDone) InitializeModuleData();
   // We just return the name we use for referring to this
   // device adapter. 
   CDeviceUtils::CopyLimitedString(name, (char*) m_szDeviceName.c_str());
}

/**
 * Tells us if device is still processing asynchronous command.
 * Required by the MM:Device API.
 */
bool ABSCamera::Busy()
{
  return CCameraBase<ABSCamera>::Busy();
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
int ABSCamera::Initialize()
{
  u32 dwRC;
  
  if (!g_bInitializeModuleDataDone) InitializeModuleData();

  if ( this->initialized ) return DEVICE_OK;

  this->busy_ = true;
   
  // initialise camera
  // try to init camera without reboot (makes the init process a bit fast)
  dwRC = GET_RC(CamUSB_InitCamera( this->deviceNumber, NO_SERIAL_NUMBER, FALSE ), this->deviceNumber);
  
  // on error try to boot the camera
  if ( !IsNoError(dwRC) )
  {
      // init camera with reboot
      dwRC = GET_RC(CamUSB_InitCamera( this->deviceNumber ), this->deviceNumber);

      if ( !IsNoError(dwRC) )
      {
        this->ShowError( dwRC );
        return DEVICE_LOCALLY_DEFINED_ERROR;
      }
  }

  // read camera information
  camVersion.dwStructSize = sizeof( S_CAMERA_VERSION );
  unsigned long iRC;
  iRC = GET_RC( CamUSB_GetCameraVersion( &camVersion, this->deviceNumber ), this->deviceNumber );
  if ( !IsNoError(iRC) )
  {    
    this->ShowError(iRC);
    return DEVICE_LOCALLY_DEFINED_ERROR;
  }

  // disbale heartbeat
  u32 dwValue = 0;
  CamUSB_HeartBeatCfg( 0x80000001, dwValue, this->deviceNumber );

  // set device name
  int nRet = CreateProperty(MM::g_Keyword_Name, (char*) m_szDeviceName.c_str(), MM::String, true);
  if ( nRet != DEVICE_OK ) return nRet;

  // set device description
  nRet = CreateProperty(MM::g_Keyword_Description, "ABS Camera Device Adapter", MM::String, true);
  if ( nRet != DEVICE_OK ) return nRet;

  // set camera name
  ostringstream cameraName;
  cameraName << camVersion.szDeviceName;
  nRet = CreateProperty(MM::g_Keyword_CameraName, cameraName.str().c_str(), MM::String, true);
  assert(nRet == DEVICE_OK);
        
  // set camera ID
  ostringstream serialNumber;
  serialNumber << camVersion.dwSerialNumber;
  nRet = CreateProperty(MM::g_Keyword_CameraID, serialNumber.str().c_str(), MM::String, true);
  assert(nRet == DEVICE_OK);


  // try to set the default resolution for this camera
  dwRC = GET_RC(CamUSB_SetStandardRes(STDRES_FULLSENSOR, this->deviceNumber), this->deviceNumber);

  // read function mask of the camera  
  dwRC = GET_RC( CamUSB_GetCameraFunctions( &this->cameraFunctionMask, this->deviceNumber ), this->deviceNumber );
  if ( !IsNoError(dwRC) )
  {    
    this->ShowError(dwRC); 
    return DEVICE_LOCALLY_DEFINED_ERROR;
  }

  // clear camaera caps
  SAFE_DELETE(this->resolutionCap);
  SAFE_DELETE(this->flipCap);  
  SAFE_DELETE(this->pixelTypeCap);
  SAFE_DELETE(this->gainCap);
  SAFE_DELETE(this->exposureCap);
  SAFE_DELETE(this->autoExposureCap);
  SAFE_DELETE(this->temperatureCap);
  SAFE_DELETE(this->framerateCap);
  
  // read camera capabilities
  try
  {
    this->resolutionCap     = (S_RESOLUTION_CAPS*)	this->GetCameraCap( FUNC_RESOLUTION );
    this->flipCap           = (S_FLIP_CAPS*)        this->GetCameraCap( FUNC_FLIP );    
    this->pixelTypeCap      = (S_PIXELTYPE_CAPS*)   this->GetCameraCap( FUNC_PIXELTYPE );
    this->gainCap           = (S_GAIN_CAPS*)        this->GetCameraCap( FUNC_GAIN );
    this->exposureCap       = (S_EXPOSURE_CAPS*)    this->GetCameraCap( FUNC_EXPOSURE );
    this->autoExposureCap   = (S_AUTOEXPOSURE_CAPS*)this->GetCameraCap( FUNC_AUTOEXPOSURE );
    this->temperatureCap    = (S_TEMPERATURE_CAPS*) this->GetCameraCap( FUNC_TEMPERATURE );
    this->framerateCap      = (S_FRAMERATE_CAPS*)   this->GetCameraCap( FUNC_FRAMERATE );
  }
  catch ( ... )
  {    
    this->ShowError( CamUSB_GetLastError(this->deviceNumber) );
    return DEVICE_LOCALLY_DEFINED_ERROR;
  }

  // turn autoexposure off
  if ( this->autoExposureCap != NULL )
  {
    S_AUTOEXPOSURE_PARAMS autoExposurePara;
    if ( this->GetCameraFunction( FUNC_AUTOEXPOSURE, &autoExposurePara, sizeof(S_AUTOEXPOSURE_PARAMS) ) != DEVICE_OK ) return DEVICE_ERR;

    autoExposurePara.bAECActive = 0;
    autoExposurePara.bAGCActive = 0;

    if ( this->SetCameraFunction( FUNC_AUTOEXPOSURE, &autoExposurePara, sizeof(S_AUTOEXPOSURE_PARAMS) ) != DEVICE_OK ) return DEVICE_ERR;
  }     


  // setup supported pixeltypes
  if ( this->pixelTypeCap != NULL ) InitSupportedPixeltypes();
  

  // set capture mode to continuous -> higher Framerates for the live view in µManager
  dwRC = GET_RC(CamUSB_SetCaptureMode(MODE_CONTINUOUS, 0, this->deviceNumber), this->deviceNumber);
  
  if ( !IsNoError(dwRC) )
  {
    this->ShowError( dwRC );
    return DEVICE_LOCALLY_DEFINED_ERROR;
  }

  // set default color mode
  bColor = ((NULL != this->resolutionCap) && (this->resolutionCap->wSensorType & ST_COLOR)) ? true : false;

  // setup exposure
  nRet = this->createExposure();
  if ( nRet != DEVICE_OK ) return nRet;

  // setup binning
  nRet = this->createBinning();
  if ( nRet != DEVICE_OK ) return nRet;

  // setup pixel type
  nRet = this->createPixelType();
  if ( nRet != DEVICE_OK ) return nRet;

  // setup gain
  nRet = this->createGain();
  if ( nRet != DEVICE_OK ) return nRet;

  // camera offset
  nRet = this->createOffset();
  if ( nRet != DEVICE_OK ) return nRet;

  // camera temperature
  nRet = this->createTemperature();
  if ( nRet != DEVICE_OK ) return nRet;

  // camera temperature
  nRet = this->createActualInterval();
  if ( nRet != DEVICE_OK ) return nRet;

  // camera temperature
  nRet = this->createColorMode();
  if ( nRet != DEVICE_OK ) return nRet;

  if (this->flipCap)
  {
    if (this->flipCap->wFlipModeMask & FLIP_HORIZONTAL)
    {      
       /*
      MM::Property* pProp = properties_.Find(MM::g_Keyword_Transpose_MirrorX);
      if (NULL != pProp)
      {
        CPropertyAction *pAct = new CPropertyAction (this, &ABSCamera::OnFlipX); // function called if exposure time property is read or written
        pProp->RegisterAction(pAct);
      }        
      */
    }

    if (this->flipCap->wFlipModeMask & FLIP_VERTICAL)
    {
       /*
      MM::Property* pProp = properties_.Find(MM::g_Keyword_Transpose_MirrorY);
      if (NULL != pProp)
      {
        CPropertyAction *pAct = new CPropertyAction (this, &ABSCamera::OnFlipY); // function called if exposure time property is read or written
        pProp->RegisterAction(pAct);
      }                   
      */
    }
  }


  // synchronize all properties
  // --------------------------
  nRet = UpdateStatus();
  if (nRet != DEVICE_OK) return nRet;

  // setup the image buffer
  // ----------------
  nRet = this->ResizeImageBuffer();
  if (nRet != DEVICE_OK) return nRet;

  this->initialized = true;
  this->busy_ = false;

  return SnapImage();
}

// setup exposure
int ABSCamera::createExposure()
{
  u32 dwRC;
  int nRet;
  float exposureTime = 100.0f; // exposure time in ms

  unsigned long exposureValue; // current hardware exposure time in µs

  // set basic exposure value
  if (camVersion.wSensorType & ST_COLOR) // color camera?
  {
    exposureValue = 75*1000; // µs
  }
  else  
  {
    exposureValue = 35*1000; // µs
  }
    
  // set and read back the exposure
  dwRC = GET_RC(CamUSB_SetExposureTime( &exposureValue, this->deviceNumber ), this->deviceNumber);
  if (!IsNoError(dwRC))
  {
    this->ShowError(dwRC);
    return DEVICE_LOCALLY_DEFINED_ERROR;
  }
  
  CPropertyAction *pAct = new CPropertyAction (this, &ABSCamera::OnExposure); // function called if exposure time property is read or written
  

  // µs -> ms
  exposureTime = static_cast<float>( exposureValue ) / 1000.0f;

  // create exposure property 
  nRet = CreateProperty(MM::g_Keyword_Exposure, CDeviceUtils::ConvertToString(exposureTime), MM::Float, false, pAct ); // create property
  if ( nRet != DEVICE_OK ) return nRet;

  return UpdateExposureLimits();
 
}

// set limits for exposure time
int ABSCamera::UpdateExposureLimits(void)
{  
  double minExposureTime; // min. allowed exposure time
  double maxExposureTime; // max. allowed exposure time

  if ( this->exposureCap != NULL )
  {
    minExposureTime = static_cast<double>( this->exposureCap->sExposureRange[0].dwMin ) / 1000.0;
    minExposureTime = 0.0f;
    maxExposureTime = static_cast<double>( this->exposureCap->sExposureRange[this->exposureCap->dwCountRanges-1].dwMax ) / 1000.0;
  }
  else
  {
    u32 dwRC;
    u32 dwExposure = 0;
    // get exposure time
    dwRC = GET_RC(CamUSB_SetExposureTime( &dwExposure, this->deviceNumber ), this->deviceNumber);
    if (!IsNoError(dwRC))
    {
      this->ShowError(dwRC);
      return DEVICE_LOCALLY_DEFINED_ERROR;
    }
    minExposureTime = static_cast<double>( dwExposure ) / 1000.0;
    maxExposureTime = static_cast<double>( dwExposure ) / 1000.0;
  }
  return SetPropertyLimits( MM::g_Keyword_Exposure, minExposureTime, maxExposureTime ); // set limits 
}

// setup binning
int ABSCamera::createBinning()
{
  int nRet;
  CPropertyAction *pAct = NULL;
  bool bReadOnly = false;
  
  // create vector of allowed values
  vector<string> binValues;
  binValues.push_back("1"); // default for each camera

  bReadOnly = ( this->resolutionCap == NULL || this->resolutionCap->dwBinModes == XY_BIN_NONE );
  pAct = new CPropertyAction (this, &ABSCamera::OnBinning);                        // function called if binning property is read or written
  nRet = CreateProperty(MM::g_Keyword_Binning, "1", MM::Integer, bReadOnly, pAct); // create binning property
  if ( nRet != DEVICE_OK ) return nRet;

  // setup allowed values for binning
  if ( bReadOnly ) return SetAllowedValues(MM::g_Keyword_Binning, binValues);       // set allowed values for binning property;

  // fill the vector of allowed values
  if ( ( this->resolutionCap->dwBinModes & XY_BIN_2X ) != 0 ) binValues.push_back("2");
  if ( ( this->resolutionCap->dwBinModes & XY_BIN_3X ) != 0 ) binValues.push_back("3");
  if ( ( this->resolutionCap->dwBinModes & XY_BIN_4X ) != 0 ) binValues.push_back("4");
  if ( ( this->resolutionCap->dwBinModes & XY_BIN_5X ) != 0 ) binValues.push_back("5");
  if ( ( this->resolutionCap->dwBinModes & XY_BIN_6X ) != 0 ) binValues.push_back("6");
  if ( ( this->resolutionCap->dwBinModes & XY_BIN_7X ) != 0 ) binValues.push_back("7");
  if ( ( this->resolutionCap->dwBinModes & XY_BIN_8X ) != 0 ) binValues.push_back("8");
  if ( ( this->resolutionCap->dwBinModes & XY_BIN_9X ) != 0 ) binValues.push_back("9");

  return SetAllowedValues(MM::g_Keyword_Binning, binValues); // set allowed values for binning property
}

// setup pixel types
int ABSCamera::createPixelType()
{
  int nRet;
  std::string pixelTypeHW;
  CPropertyAction* pAct = new CPropertyAction (this, &ABSCamera::OnPixelType);  // function called if pixel type property is read or written

    // get default pixel string 
  pixelTypeHW = (bColor) ? g_sSupportedPixelTypesColor[0].strPixelType : g_sSupportedPixelTypesMono[0].strPixelType;
  nRet = CreateProperty(MM::g_Keyword_PixelType, pixelTypeHW.c_str(), MM::String, false, pAct); // create pixel type property
  
  if ( nRet != DEVICE_OK ) return nRet; // bei Fehler -> Abbruch

  nRet = UpdatePixelTypes();

  return nRet;
}

// update the list of valid pixel types based on the ColorMode settings coded in bColor
int ABSCamera::UpdatePixelTypes(void)
{
  int nRet;  
  int nUsedBpp;
  unsigned long pixelType;
  std::string   pixelTypeHW;
  bool bPixelTypeAssigned = false;
  SupportedPixelTypes currentPixelType;

  vector<string> pixelTypeValues; // vector of allowed pixel types
  
  if ( this->pixelTypeCap == NULL ) return DEVICE_ERR;

  // read current pixel type from hardware  
  unsigned long iRC;
  iRC = GET_RC( CamUSB_GetPixelType( &pixelType, this->deviceNumber ), this->deviceNumber );
  if ( !IsNoError(iRC) )
  {    
    this->ShowError(iRC); 
    return DEVICE_LOCALLY_DEFINED_ERROR;
  }

  // update supported types based on color on monochrome settings
  InitSupportedPixeltypes(); 

  nUsedBpp = GetUsedBpp(pixelType);
  
  for ( unsigned int p=0; p < this->cPixeltypes.size(); p++ )
  {
    // equal bit/pixel but not the last pixeltype (Max)
    if ((nUsedBpp == GetUsedBpp( this->cPixeltypes[p].dwPixelType ) ) && 
        (p+1 < this->cPixeltypes.size()) )
    {
      currentPixelType = this->cPixeltypes[p];
      bPixelTypeAssigned = true;
    }
    pixelTypeValues.push_back( this->cPixeltypes[p].strPixelType );    
  }

  // assign a default value if non assigned yet
  if (!bPixelTypeAssigned && (this->cPixeltypes.size() > 0))
  {
    currentPixelType = this->cPixeltypes[0];
  }

  nRet = SetPixelType( currentPixelType.dwPixelType );
  nRet = SetProperty( MM::g_Keyword_PixelType, currentPixelType.strPixelType.c_str() ); // set allowed pixel types
  nRet = SetAllowedValues(MM::g_Keyword_PixelType, pixelTypeValues); // set allowed pixel types

  OnPropertiesChanged(); // notify GUI to update

  return nRet;
}

// update the list of valid pixel types based on the ColorMode settings coded in bColor
int ABSCamera::SetPixelType(unsigned long dwPixelType)
{
  unsigned long iRC;
  iRC = GET_RC( CamUSB_SetPixelType( dwPixelType, this->deviceNumber), this->deviceNumber );
  if ( !IsNoError(iRC) )
  {    
    this->ShowError(iRC); 
    return DEVICE_LOCALLY_DEFINED_ERROR;
  }

  // setup number of channels depending on current pixel type  
  if ( (GetBpp( dwPixelType ) / GetUsedBpp( dwPixelType )) > 1 ) 
  {
      numberOfChannels = 4;	  	      
  }
  else
  {
    numberOfChannels = 1;
  }

  ResizeImageBuffer();

  return DEVICE_OK;
}

// setup gain
int ABSCamera::createGain()
{
  int nRet;

  float gain = 0.0f;
  CPropertyAction *pAct = new CPropertyAction (this, &ABSCamera::OnGain);   // function called if gein is read or written


  // read current gain from hardware
  if ( this->gainCap == NULL )
  {
    nRet = CreateProperty(MM::g_Keyword_Gain, "1.0", MM::Float, true, pAct ); // Property für Gain anlegen
    if ( nRet != DEVICE_OK ) return nRet;
    return SetPropertyLimits( MM::g_Keyword_Gain, 1.0, 1.0 ); // limits setzen
  }

  float gainFactor = 1.0f; // factor to calculate value for gain property

  if ( this->gainCap->bGainUnit == GAINUNIT_NONE ) gainFactor =  0.001f;
  if ( this->gainCap->bGainUnit == GAINUNIT_10X  ) gainFactor = 0.0001f;

  u32 dwRC;
  u32 dwGain;
  u16 wGainChannel;
  // check if color or monochrom camera
  BOOL bRGBGain = ((gainCap->wGainChannelMask & GAIN_RGB) == GAIN_RGB);

  // set default gain 0
  wGainChannel = (bRGBGain) ? (GAIN_GREEN | GAIN_LOCKED) : GAIN_GLOBAL;
  dwGain = 0;
  dwRC = GET_RC(CamUSB_SetGain( &dwGain, wGainChannel, this->deviceNumber), this->deviceNumber);

  // get the current gain (may it is different from the one set (WhiteBalance)
  wGainChannel = (bRGBGain) ? GAIN_GREEN : GAIN_GLOBAL;
  dwRC = GET_RC(CamUSB_GetGain( &dwGain, wGainChannel, this->deviceNumber), this->deviceNumber);
  if ( !IsNoError(dwRC) )
  {
    nRet = CreateProperty(MM::g_Keyword_Gain, "1.0", MM::Float, true, pAct ); // Property für Gain anlegen
    if ( nRet != DEVICE_OK ) return nRet;
    return SetPropertyLimits( MM::g_Keyword_Gain, 1.0, 1.0 ); // limits setzen
  }
  else // gain value read
  {
    gain = static_cast<float>( dwGain ) * gainFactor;
  }

  ostringstream gainStr;
  gainStr << gain;

  nRet = CreateProperty(MM::g_Keyword_Gain, gainStr.str().c_str(), MM::Float, false, pAct ); // create gain property 
  if ( nRet != DEVICE_OK ) return nRet;

  // set limits for gain property
  float minGain = static_cast<float>( this->gainCap->sGainRange[0].dwMin) * gainFactor; // min. allowed gain value
  float maxGain =  static_cast<float>( this->gainCap->sGainRange[this->gainCap->wGainRanges-1].dwMax) * gainFactor; // max. allowed gain value

  return SetPropertyLimits( MM::g_Keyword_Gain, minGain, maxGain ); // set limits for gain property
}


// camera offset
int ABSCamera::createOffset()
{  
  return CreateProperty(MM::g_Keyword_Offset, "0", MM::Integer, true); // Property für Offset anlegen   
}

// camera temperature
int ABSCamera::createTemperature()
{  
  int nRet = DEVICE_OK;
  
  if (( this->temperatureCap != NULL ) &&
      ( this->temperatureCap->dwSensors > 0 ))
  {
    CPropertyAction *pAct = new CPropertyAction (this, &ABSCamera::OnTemperature);   // function called if temperature is read or written
    nRet = CreateProperty(MM::g_Keyword_CCDTemperature, "0.0", MM::Float, true, pAct);
    if ( nRet != DEVICE_OK ) return nRet;

    nRet = SetPropertyLimits( MM::g_Keyword_CCDTemperature, -40.0, 90.0 ); // limits setzen
  }

  return nRet;
}

// camera ActualInterval
int ABSCamera::createActualInterval()
{  
  return CreateProperty(MM::g_Keyword_ActualInterval_ms, "0.0", MM::Float, true);
}

 
// color mode
int ABSCamera::createColorMode()
{
  int nRet = DEVICE_OK;
  CPropertyAction *pAct = NULL;
  
  if ( this->resolutionCap != NULL )
  {
    vector<string> colorValues;
    pAct = new CPropertyAction (this, &ABSCamera::OnColorMode);

    // add color if supported
    if (this->resolutionCap->wSensorType & ST_COLOR)
    {    
      colorValues.push_back(g_ColorMode_Color);
    }
    // add monochrom anyway
    colorValues.push_back(g_ColorMode_Grayscale);         
    
    std::string strDefaultColorMode = colorValues[ 0 ];
    // create property
    nRet = CreateProperty(MM::g_Keyword_ColorMode, (char*)strDefaultColorMode.c_str(), MM::String, (colorValues.size() <= 1), pAct);
    if (nRet == DEVICE_OK)
      nRet = SetAllowedValues(MM::g_Keyword_ColorMode, colorValues);
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
int ABSCamera::Shutdown()
{
  StopSequenceAcquisition();

  // release images which are not longer needed
  if ((cameraProvidedImageBuffer != NULL) &&
      (cameraProvidedImageHdr    != NULL) )
  {
    CamUSB_ReleaseImage((u08*)cameraProvidedImageBuffer, (S_IMAGE_HEADER*)cameraProvidedImageHdr, this->deviceNumber);
  }

  this->initialized = false;
  
  // free camera device
  CamUSB_FreeCamera( this->deviceNumber );

  // clear camaera caps
  SAFE_DELETE(this->resolutionCap);
  SAFE_DELETE(this->pixelTypeCap);
  SAFE_DELETE(this->gainCap);
  SAFE_DELETE(this->exposureCap);
  SAFE_DELETE(this->autoExposureCap);
  SAFE_DELETE(this->temperatureCap);
  SAFE_DELETE(this->framerateCap);    
  return DEVICE_OK;
}



/**
 * Performs exposure and grabs a single image.
 * Required by the MM::Camera API.
 */
int ABSCamera::SnapImage()
{
  // useing own buffers
  u32 dwRC;
  bool bThreadIsActive = IsCapturing();
  bool bTryAgain = true;

  // read required size of the image buffer
  unsigned long imageSize = static_cast<unsigned long>( this->GetImageBufferSize() );

  u08*            pImg    ;//= (u08*) imageBuffer.GetPixels(); // pointer of image
  //S_IMAGE_HEADER  sImgHdr = {0};
  S_IMAGE_HEADER* pImgHdr ;//= &sImgHdr; // pointer of image header
  
  unsigned char* cameraProvidedImageBufferNew = NULL;
  unsigned char* cameraProvidedImageHdrNew    = NULL;
  
  do 
  {
    pImg     = NULL;
    pImgHdr  = NULL;
    imageSize= 0;

    dwRC = GET_RC(CamUSB_GetImage( &pImg, &pImgHdr, imageSize, this->deviceNumber), this->deviceNumber);  
    

    if ((retOK == dwRC)     ||    // image successfully captured
        (!IsNoError(dwRC))  ||    // error abort image
        (bThreadIsActive && bAbortGetImageCalled && (retNOIMG == dwRC))) // retNOIMG during abort loop
    {
      if (!IsNoError(dwRC)) this->ShowError(dwRC);
      if (bThreadIsActive && bAbortGetImageCalled && (retNOIMG == dwRC)) bAbortGetImageCalled = false;
      bTryAgain = false;
    }

  } while(bTryAgain);
  
  // remeber new values
  cameraProvidedImageBufferNew = (unsigned char*) pImg;
  cameraProvidedImageHdrNew    = (unsigned char*) pImgHdr;

  if (!bThreadIsActive && bAbortGetImageCalled) bAbortGetImageCalled = false;

  // change image pointer and release the old images which are not longer needed
  if ((cameraProvidedImageBufferNew != cameraProvidedImageBuffer) &&
      (cameraProvidedImageBufferNew != NULL) &&
      (cameraProvidedImageHdrNew    != cameraProvidedImageHdr) &&      
      (cameraProvidedImageHdrNew    != NULL) )
  {
    unsigned char  *tmpIB, *tmpIH;
    tmpIB = cameraProvidedImageBuffer;
    tmpIH = cameraProvidedImageHdr;

    MM_THREAD_GUARD_LOCK(&lockImgBufPtr);
      cameraProvidedImageHdr    = cameraProvidedImageHdrNew;
      cameraProvidedImageBuffer = cameraProvidedImageBufferNew;
    MM_THREAD_GUARD_UNLOCK(&lockImgBufPtr);

    CamUSB_ReleaseImage((u08*)tmpIB, (S_IMAGE_HEADER*)tmpIH, this->deviceNumber);
  }

  return (retOK == dwRC) ? DEVICE_OK : DEVICE_ERR;
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
const unsigned char* ABSCamera::GetImageBuffer()
{     
  const unsigned char *pImgBuffer = imageBuffer.GetPixels();
  MM_THREAD_GUARD_LOCK(&lockImgBufPtr);
    if (NULL != cameraProvidedImageBuffer) pImgBuffer = cameraProvidedImageBuffer;
  MM_THREAD_GUARD_UNLOCK(&lockImgBufPtr);
  return pImgBuffer;
}
// used for color images
const unsigned int* ABSCamera::GetImageBufferAsRGB32()
{
    return (const unsigned int*)GetImageBuffer();    
}


unsigned int ABSCamera::GetNumberOfComponents() const
{
  return this->numberOfChannels;  
}


int ABSCamera::GetComponentName(unsigned channel, char* name)
{
  if (!bColor && (channel > 0))  return DEVICE_NONEXISTENT_CHANNEL;      
  
  switch (channel)
  {
  case 0:      
    if (!bColor) 
      CDeviceUtils::CopyLimitedString(name, "Grayscale");
    else 
      CDeviceUtils::CopyLimitedString(name, "B");      
    break;

  case 1:
    CDeviceUtils::CopyLimitedString(name, "G");
    break;

  case 2:
    CDeviceUtils::CopyLimitedString(name, "R");
    break;

  default:
    return DEVICE_NONEXISTENT_CHANNEL;
    break;
  }
  return DEVICE_OK;
}
 

/**
 * Returns image buffer X-size in pixels.
 * Required by the MM::Camera API.
 */
unsigned ABSCamera::GetImageWidth() const
{
   return imageBuffer.Width();
}

/**
 * Returns image buffer Y-size in pixels.
 * Required by the MM::Camera API.
 */
unsigned ABSCamera::GetImageHeight() const
{
   return imageBuffer.Height();
}

/**
 * Returns image buffer pixel depth in bytes.
 * Required by the MM::Camera API.
 */
unsigned ABSCamera::GetImageBytesPerPixel() const
{
  return imageBuffer.Depth() / GetNumberOfComponents();  
} 

/**
 * Returns the bit depth (dynamic range) of the pixel.
 * This does not affect the buffer size, it just gives the client application
 * a guideline on how to interpret pixel values.
 * Required by the MM::Camera API.
 */
unsigned ABSCamera::GetBitDepth() const
{
  
  unsigned int bitDepth = 8;
  
  // read current pixel type from hardware
  unsigned long pixelType;
  unsigned long iRC;
  iRC = GET_RC( CamUSB_GetPixelType( &pixelType, this->deviceNumber ), this->deviceNumber );
  if ( IsNoError(iRC) )
  {    
      bitDepth = GetUsedBpp( pixelType );    
  }
  else
  {
    this->ShowError(iRC); 
  }

  if ( bitDepth == 8) bitDepth *= (GetImageBytesPerPixel() * GetNumberOfComponents()) ;
  
  return bitDepth;
}

/**
 * Returns the size in bytes of the image buffer.
 * Required by the MM::Camera API.
 */
long ABSCamera::GetImageBufferSize() const
{
  return GetImageWidth() * GetImageHeight() * GetImageBytesPerPixel() * GetNumberOfComponents();  
}

/**
 * Sets the camera Region Of Interest.
 * Required by the MM::Camera API.
 * This command will change the dimensions of the image.
 * Depending on the hardware capabilities the camera may not be able to configure the
 * exact dimensions requested - but should try do as close as possible.
 * If the hardware does not have this capability the software should simulate the ROI by
 * appropriately cropping each frame.
 * @param x - top-left corner coordinate
 * @param y - top-left corner coordinate
 * @param xSize - width
 * @param ySize - height
 */
int ABSCamera::SetROI( unsigned x, unsigned y, unsigned xSize, unsigned ySize )
{ 
  u32 dwRC;

  if(IsCapturing())
      return ERR_BUSY_ACQIRING;
  
  if ( this->resolutionCap == NULL ) return DEVICE_ERR;

  short x_HW, y_HW; // hardware offsets
  unsigned short xSize_HW, ySize_HW; // hardware size
  unsigned long skip_HW, binning_HW; // hardware binning and skip

  // read hardware resolution values
  dwRC = GET_RC( CamUSB_GetCameraResolution( &x_HW, &y_HW, &xSize_HW, &ySize_HW, &skip_HW, &binning_HW, this->deviceNumber ), this->deviceNumber );
  if ( !IsNoError( dwRC ) )
  {
    this->ShowError(dwRC);
    return DEVICE_LOCALLY_DEFINED_ERROR;
  }

  dwRC = GET_RC(CamUSB_SetCameraResolution( (short) x, (short) y,
                                            (WORD) xSize, (WORD) ySize,
                                            skip_HW, binning_HW,                                              
                                            TRUE,
                                            this->deviceNumber),
                                            this->deviceNumber);

  if (IsNoError(dwRC))
  {
      // update exposure range setting because the depends on the currently set resolution
      try
      {
        SAFE_DELETE_ARRAY(this->exposureCap);
        this->exposureCap = (S_EXPOSURE_CAPS*) this->GetCameraCap( FUNC_EXPOSURE );    
        UpdateExposureLimits();
      }
      catch ( ... )
      {
        this->ShowError( CamUSB_GetLastError(this->deviceNumber) );
        return DEVICE_LOCALLY_DEFINED_ERROR;
      }
  }
  else
  {
      this->ShowError(dwRC);
      return DEVICE_LOCALLY_DEFINED_ERROR;
  }

  // resize image buffer
  return this->ResizeImageBuffer();
}

/**
 * Returns the actual dimensions of the current ROI.
 * Required by the MM::Camera API.
 */
int ABSCamera::GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize)
{

  // read current ROI from hardware
  S_RESOLUTION_RETVALS resolutionReturn;
  unsigned long size = sizeof(S_RESOLUTION_RETVALS);
  
  unsigned long iRC;
  iRC = GET_RC( CamUSB_GetFunction( FUNC_RESOLUTION, &resolutionReturn, &size, NULL, 0, NULL, 0, this->deviceNumber ), this->deviceNumber );
  if ( !IsNoError(iRC) )
  {    
    this->ShowError(iRC);
    return DEVICE_LOCALLY_DEFINED_ERROR;
  }
  
  x = resolutionReturn.wOffsetX;
  y = resolutionReturn.wOffsetY;
  xSize = resolutionReturn.wSizeX;
  ySize = resolutionReturn.wSizeY;

  return DEVICE_OK;
}

/**
 * Resets the Region of Interest to full frame.
 * Required by the MM::Camera API.
 */
int ABSCamera::ClearROI()
{
  // set ROI to full size
  if ( this->resolutionCap == NULL ) return DEVICE_ERR;
  this->SetROI( 0, 0, this->resolutionCap->wVisibleSizeX, this->resolutionCap->wVisibleSizeY );
  return DEVICE_OK;
}

/**
 * Returns the current exposure setting in milliseconds.
 * Required by the MM::Camera API.
 */
double ABSCamera::GetExposure() const
{
  double exposure = 0.0;
  unsigned long exposureValue;

  //bSetGetExposureActive = true;
  
  // read exposure time from hardware
  unsigned long iRC;
  iRC = GET_RC( CamUSB_GetExposureTime( &exposureValue, this->deviceNumber ), this->deviceNumber );
  if ( !IsNoError(iRC) )
  {    
    this->ShowError(iRC);
  }
  else
  {
    // µs -> ms
    exposure = static_cast<double>( exposureValue ) / 1000.0;
    //SetProperty(MM::g_Keyword_Exposure, CDeviceUtils::ConvertToString(exposure));        
  }

  //bSetGetExposureActive = false;

  return exposure;
  
}

/**
 * Sets exposure in milliseconds.
 * Required by the MM::Camera API.
 */
void ABSCamera::SetExposure(double exposure)
{
  bSetGetExposureActive = true;
  
  unsigned long exposureValue = static_cast<unsigned long>(exposure) * 1000;
  
  // set exposure time 
  unsigned long iRC;
  iRC = GET_RC( CamUSB_SetExposureTime( &exposureValue, this->deviceNumber ), this->deviceNumber );
  if ( !IsNoError(iRC) )
  {    
    this->ShowError(iRC);
  }

  exposure = static_cast<double>( exposureValue  / 1000.0);
  SetProperty(MM::g_Keyword_Exposure, CDeviceUtils::ConvertToString(exposure));    

  bSetGetExposureActive = false;
}


// handels exposure property
int ABSCamera::OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  double exposure;

  if (bSetGetExposureActive) return DEVICE_OK;   
  if ( this->exposureCap == NULL ) return DEVICE_OK;
  if (eAct == MM::AfterSet) // property was written -> apply value to hardware
  {    
    pProp->Get(exposure);

    unsigned long exposureValue = static_cast<unsigned long>(exposure) * 1000l;
    // set exposure time 
    unsigned long iRC;
    iRC = GET_RC( CamUSB_SetExposureTime( &exposureValue, this->deviceNumber ), this->deviceNumber );
    if ( !IsNoError(iRC) )
    {    
     this->ShowError(iRC);
    }

    exposure = static_cast<double>( exposureValue  / 1000.0);
    pProp->Set( exposure );    
  }
  else if (eAct == MM::BeforeGet) // property will be read -> update property with value from hardware
  {    
    exposure = GetExposure();

    // write hardware value to property
    pProp->Set( exposure );
  }

  return DEVICE_OK;   
}


int ABSCamera::OnTemperature(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  if ( this->temperatureCap == NULL ) return DEVICE_OK;

  // property should be written -> don't allowed
  if (eAct == MM::AfterSet) 
  {
    return DEVICE_LOCALLY_DEFINED_ERROR;
  }
  else if (eAct == MM::BeforeGet) // property will be read -> update property with value from hardware
  {
    S_TEMPERATURE_PARAMS  sTP = {0};
    S_TEMPERATURE_RETVALS sTR = {0};
    u32 dwRC;
    u32 dwSize;

    sTP.wSensorIndex = 0;
    dwSize = sizeof(sTR);
    dwRC = GET_RC(CamUSB_GetFunction(FUNC_TEMPERATURE, &sTR, &dwSize, &sTP, sizeof(sTP), NULL, 0, this->deviceNumber), this->deviceNumber); 

    if (IsNoError(dwRC))
    {
      float fTemp;
      
      switch (this->temperatureCap->dwSensor[0].wUnit)
      {
      case TEMP_SENS_UNIT_2C: fTemp = sTR.wSensorValue * 0.50f;      break;
      case TEMP_SENS_UNIT_4C: fTemp = sTR.wSensorValue * 0.25f;      break;
      default:                fTemp = sTR.wSensorValue * 1.00f;      break;
      }
      
      // write hardware value to property
      pProp->Set( fTemp );
    }
  }
  
  return DEVICE_OK;
}

int ABSCamera::OnFlipX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  long nFlip, nFlipX;
  if (NULL == this->flipCap) return DEVICE_OK;
  
  // property should be written 
  if (eAct == MM::AfterSet) 
  {
    pProp->Get(nFlipX);
    nFlip = GetFlip();
    nFlip &= ~FLIP_HORIZONTAL;
    if (nFlipX != 0)  nFlip |= FLIP_HORIZONTAL;
    SetFlip(nFlip & FLIP_BOTH);
  }
  else if (eAct == MM::BeforeGet) // property will be read -> update property with value from hardware
  {    
    nFlip = GetFlip();
    nFlip = (nFlip & FLIP_HORIZONTAL) ? 1 : 0;
    pProp->Set(nFlip);
  }

  return DEVICE_OK;
}

int ABSCamera::OnFlipY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  long nFlip, nFlipY;
  if (NULL == this->flipCap) return DEVICE_OK;
  
  // property should be written 
  if (eAct == MM::AfterSet) 
  {
    pProp->Get(nFlipY);
    nFlip = GetFlip();
    nFlip &= ~FLIP_VERTICAL;
    if (nFlipY != 0)  nFlip |= FLIP_VERTICAL;
    SetFlip(nFlip & FLIP_BOTH);
  }
  else if (eAct == MM::BeforeGet) // property will be read -> update property with value from hardware
  {    
    nFlip = GetFlip();
    nFlip = (nFlip & FLIP_VERTICAL) ? 1 : 0;
    pProp->Set(nFlip);
  }

  return DEVICE_OK;

}
  
// color mode mono / color
int ABSCamera::OnColorMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  int nRet = DEVICE_OK;
  // property should be written -> change it
  if (eAct == MM::AfterSet) 
  {
    std::string strColorMode;

    if(IsCapturing())
         return DEVICE_CAN_NOT_SET_PROPERTY;

    pProp->Get(strColorMode);

    // check if color mode is selected
    bColor = (strColorMode.compare( g_ColorMode_Color ) == 0 );

    nRet = UpdatePixelTypes();    
  }
  else if (eAct == MM::BeforeGet) // property will be read -> update property with value from hardware
  {
    pProp->Set((bColor) ? g_ColorMode_Color : g_ColorMode_Grayscale);
  }
  
  return nRet;
}

// handels gain property
int ABSCamera::OnGain(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  if ( this->gainCap == NULL ) return DEVICE_OK;
    
  // check if color or monochrom camera
  BOOL bRGBGain = ((this->gainCap->wGainChannelMask & GAIN_RGB) == GAIN_RGB);
  u32 dwRC;
  u32 dwGain;
  u16 wGainChannel;
  double gain;
  float gainFactor = 1.0f; // factor for calculating property value from hardware value 
  if ( this->gainCap->bGainUnit == GAINUNIT_NONE ) gainFactor =  0.001f;
  if ( this->gainCap->bGainUnit == GAINUNIT_10X  ) gainFactor = 0.0001f;

  if (eAct == MM::AfterSet) // property was written -> apply value to hardware
  {    
    pProp->Get(gain);

    // setup value
    dwGain = (u32) (gain / gainFactor);                             
    // set channel
    wGainChannel = (bRGBGain) ? (GAIN_GREEN | GAIN_LOCKED) : GAIN_GLOBAL;   

    dwRC = GET_RC(CamUSB_SetGain( &dwGain, wGainChannel, this->deviceNumber), this->deviceNumber);
    if ( !IsNoError(dwRC) )
    {
        this->ShowError(dwRC);
        return DEVICE_LOCALLY_DEFINED_ERROR;
    }
    else // gain value set
    {
        gain = static_cast<float>( dwGain ) * gainFactor;
        pProp->Set( gain ); // write back to GUI
    }
  }
  else if (eAct == MM::BeforeGet) // property will be read -> update property with value from hardware
  {         
    dwGain = 0;
    // set channel
    wGainChannel = (bRGBGain) ? GAIN_GREEN : GAIN_GLOBAL;   

    dwRC = GET_RC(CamUSB_GetGain( &dwGain, wGainChannel, this->deviceNumber), this->deviceNumber);
    if ( !IsNoError(dwRC) )
    {
        this->ShowError(dwRC);
        return DEVICE_LOCALLY_DEFINED_ERROR;
    }
    else // gain value set
    {
        gain = static_cast<float>( dwGain ) * gainFactor;
        pProp->Set( gain ); // write back to GUI
    }
  }
  return DEVICE_OK; 
}

/**
 * Returns the current binning factor.
 * Required by the MM::Camera API.
 */
int ABSCamera::GetBinning() const
{
  char buf[MM::MaxStrLength];
  int ret = GetProperty(MM::g_Keyword_Binning, buf);
  if (ret != DEVICE_OK) return 1;
  return atoi(buf);

}

/**
 * Sets binning factor.
 * Required by the MM::Camera API.
 */
int ABSCamera::SetBinning(int binFactor)
{
  if(IsCapturing())
    return ERR_BUSY_ACQIRING;
  return SetProperty(MM::g_Keyword_Binning, CDeviceUtils::ConvertToString(binFactor));
}


///////////////////////////////////////////////////////////////////////////////
// ABSCamera Action handlers
///////////////////////////////////////////////////////////////////////////////

/**
 * Handles "Binning" property.
 */
int ABSCamera::OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  u32 dwRC;
  if ( this->resolutionCap == NULL ) return DEVICE_OK; // property was written -> apply value to hardware
  if (eAct == MM::AfterSet)
  {
    long binning;
    pProp->Get(binning);

    unsigned long size = sizeof(S_RESOLUTION_RETVALS);
    S_RESOLUTION_RETVALS resolutionReturn;

    unsigned long iRC;
    iRC = GET_RC( CamUSB_GetFunction( FUNC_RESOLUTION, &resolutionReturn, &size, NULL, 0, NULL, 0, this->deviceNumber ), this->deviceNumber );
    if ( !IsNoError(iRC) )
    {    
      this->ShowError(iRC);
      return DEVICE_LOCALLY_DEFINED_ERROR;
    }
    
    // convert to camera value
    resolutionReturn.dwBin = BinValueToCamValue( binning );
    // limit to supported modes
    if (this->resolutionCap != 0) resolutionReturn.dwBin &= resolutionCap->dwBinModes;
    // convert back to internal value
    if ( binning != CamValueToBinValue( resolutionReturn.dwBin ))
    {
      binning = CamValueToBinValue( resolutionReturn.dwBin );
      pProp->Set( binning );
    }
    
    // don't allow negativ offset's to set by the user directly
    if ((resolutionReturn.wOffsetX < 0 ) || (resolutionReturn.wOffsetY < 0 ))
    {
        resolutionReturn.wOffsetX = 0;
        resolutionReturn.wOffsetY = 0;
    }

    // must be at least the same (Skip may be higher than bin but not contrary
    resolutionReturn.dwSkip = resolutionReturn.dwBin;

    // try to keep the current exposure, each time the resolution is changed...
    resolutionReturn.bKeepExposure = 1;

    // use CamUSB_SetCameraResolution => to update the ROI for Skip and binning
    dwRC = GET_RC(CamUSB_SetCameraResolution( resolutionReturn.wOffsetX,
                                              resolutionReturn.wOffsetY,
                                              resolutionReturn.wSizeX,
                                              resolutionReturn.wSizeY,
                                              resolutionReturn.dwSkip,
                                              resolutionReturn.dwBin,
                                              resolutionReturn.bKeepExposure,
                                              this->deviceNumber),
                                              this->deviceNumber);

    ///if (!CamUSB_SetFunction( FUNC_RESOLUTION, &resolutionReturn, size ) )
    if ( !IsNoError(dwRC) )
    {
      this->ShowError(dwRC);
      return DEVICE_LOCALLY_DEFINED_ERROR;
    }

    this->ResizeImageBuffer();
  }
  else if (eAct == MM::BeforeGet) // property will be read -> update property with value from hardware
  {
    long binning, binningHW;
    pProp->Get(binning);
    binningHW = binning;

    unsigned long size = sizeof(S_RESOLUTION_RETVALS);
    S_RESOLUTION_RETVALS resolutionReturn;

    unsigned long iRC;
    iRC = GET_RC( CamUSB_GetFunction( FUNC_RESOLUTION, &resolutionReturn, &size, NULL, 0, NULL, 0, this->deviceNumber ), this->deviceNumber );
    if ( !IsNoError(iRC) )
    {    
      this->ShowError(iRC);    
      return DEVICE_LOCALLY_DEFINED_ERROR;
    }

    binningHW = CamValueToBinValue( resolutionReturn.dwBin );
    if ( binning != binningHW ) 
    {
      pProp->Set( binningHW );
      this->ResizeImageBuffer();
    }
  }

  return DEVICE_OK; 
}

/**
 * Handles "PixelType" property.
 */
int ABSCamera::OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  int nRet = DEVICE_OK;
  if ( this->pixelTypeCap == NULL ) return nRet;

  if (eAct == MM::AfterSet) // property was written -> apply value to hardware
  {
    u32 dwPixelType = 0; 
    std::string pixelType;
    pProp->Get(pixelType);

    dwPixelType = StringToPixelType( pixelType );
    
    for ( u32 n=0; n < this->cPixeltypes.size(); n++ )
    {
      if ( pixelType.compare( this->cPixeltypes[n].strPixelType ) == 0 ) 
      {
         dwPixelType = this->cPixeltypes[n].dwPixelType;
         break;
      }
    }
    nRet = SetPixelType(dwPixelType);    
  }
  else if (eAct == MM::BeforeGet) // property will be read -> update property with value from hardware
  {
    bool bFound = false;
    std::string pixelType, pixelTypeHW;
    unsigned int nIndexPixelTypeFound = 0;

    pProp->Get(pixelType);

    // read current pixel type from hardware
    unsigned long pixType;
    unsigned long iRC;
    iRC = GET_RC( CamUSB_GetPixelType( &pixType, this->deviceNumber ), this->deviceNumber );
    if ( !IsNoError(iRC) )
    {    
      this->ShowError(iRC);
      return DEVICE_LOCALLY_DEFINED_ERROR;
    }

    for ( u32 n=0; n < this->cPixeltypes.size(); n++ )
    {
      if ( pixType == this->cPixeltypes[n].dwPixelType ) 
      {
        nIndexPixelTypeFound = n;
        bFound = true;
        break;
      }
    }

    if (bFound == false)
    {
      bColor = !bColor;     // toggle color setting in hope to find the pixeltype there
      InitSupportedPixeltypes();
      for ( u32 n=0; n < this->cPixeltypes.size(); n++ )
      {
        if ( pixType == cPixeltypes[n].dwPixelType ) 
        {
          nIndexPixelTypeFound = n;
          bFound = true;
          break;
        }
      }
    
      // if pixeltype is still not found restore old color settings an set the first pixeltype
      if (bFound == false)
      {
        // pixel not found at mono or color pixeltypes => set supported default pixeltype
        bColor = !bColor;
        InitSupportedPixeltypes();
        SetPixelType( this->cPixeltypes[0].dwPixelType );
        UpdatePixelTypes();
        nIndexPixelTypeFound = 0;
        bFound = true;
      }      
    }
  
    // if current value and previous value are different update ImageBuffer size
    if (( this->cPixeltypes[nIndexPixelTypeFound].strPixelType != pixelType ) &&
        ( this->cPixeltypes[nIndexPixelTypeFound].dwPixelType  != StringToPixelType(pixelType) ) )          
    {
        pProp->Set( this->cPixeltypes[nIndexPixelTypeFound].strPixelType.c_str() );
        ResizeImageBuffer();
    }    
  }
  return nRet; 
}

/**
 * Sync internal image buffer size to the chosen property values.
 */
int ABSCamera::ResizeImageBuffer()
{
  unsigned int x, y, sizeX, sizeY;

  // read current ROI
  int nRet = this->GetROI( x, y, sizeX, sizeY );
  if ( nRet != DEVICE_OK ) return nRet;

  // read binning property
  int binSize = GetBinning();
  
  // read current pixel type from hardware
  unsigned long pixelType;
  unsigned long iRC;
  iRC = GET_RC( CamUSB_GetPixelType( &pixelType, this->deviceNumber ), this->deviceNumber );
  if ( !IsNoError(iRC) )
  {    
    this->ShowError(iRC);
    return DEVICE_LOCALLY_DEFINED_ERROR;
  }
  // calculate required bytes
  int byteDepth = GetBpp( pixelType ) / 8;
   
  // resize image buffer
  imageBuffer.Resize( sizeX / binSize, sizeY / binSize, byteDepth);
  

  // if thead don't run make sure the core image buffer is updated as well (if thread is active it is done before/allready)
  if (!IsCapturing())
  {
    // make sure the circular buffer is properly sized => use 2 Buffers
    GetCoreCallback()->InitializeImageBuffer(GetNumberOfComponents(), ABSCAM_CIRCULAR_BUFFER_IMG_COUNT, GetImageWidth(), GetImageHeight(), GetImageBytesPerPixel());
  }

  return DEVICE_OK;
}

// show ABSCamera error with the specified number
void ABSCamera::ShowError( unsigned long errorNumber ) const
{
  char errorMessage[MAX_PATH]; // error string (MAX_PATH is normally around 255)
  char messageCaption[MAX_PATH];      // caption string

  memset(errorMessage, 0, sizeof(errorMessage));
  memset(messageCaption, 0, sizeof(messageCaption));

  CamUSB_GetErrorString( errorMessage, MAX_PATH, errorNumber );

  if ( IsNoError(errorNumber) )
  {
    sprintf( messageCaption, "Warning for ABSCamera device number %i", this->deviceNumber );
  }
  else
  {
    sprintf( messageCaption, "Error for ABSCamera device number %i", this->deviceNumber );
  }

  ((ABSCamera*) this)->SetErrorText(DEVICE_LOCALLY_DEFINED_ERROR, errorMessage);
  LogMessage(messageCaption);
  LogMessage(errorMessage);
}

// read camera caps
void* ABSCamera::GetCameraCap( unsigned __int64 CamFuncID ) const
{
  void* cameraCap = NULL;
  if ( ( this->cameraFunctionMask & CamFuncID ) == CamFuncID )
  {
    unsigned long capSize;
    CamUSB_GetFunctionCaps( CamFuncID, NULL ,&capSize, this->deviceNumber );
    cameraCap = new char[capSize];
    if ( CamUSB_GetFunctionCaps( CamFuncID, cameraCap, &capSize, this->deviceNumber ) == false )
    {
      throw new exception();
    }
  }
  return cameraCap;
}

// read camera parameter from hardware
int ABSCamera::GetCameraFunction( unsigned __int64 CamFuncID, void* functionPara, unsigned long size, void* functionParaOut, unsigned long sizeOut) const
{
  if ( ( this->cameraFunctionMask & CamFuncID ) == CamFuncID )
  {
    unsigned long iRC;
    iRC = GET_RC( CamUSB_GetFunction( CamFuncID, functionPara, &size, functionParaOut, sizeOut, NULL, 0, this->deviceNumber ), this->deviceNumber );
    if ( !IsNoError(iRC) )
    {
      this->ShowError(iRC);
      return DEVICE_LOCALLY_DEFINED_ERROR;
    }
  }
  return DEVICE_OK;
}

// set camera parameter to hardware
int ABSCamera::SetCameraFunction( unsigned __int64 CamFuncID, void* functionPara, unsigned long size )  const
{
  if ( ( this->cameraFunctionMask & CamFuncID ) == CamFuncID )
  {
    unsigned long iRC;
    iRC = GET_RC( CamUSB_SetFunction( CamFuncID, functionPara, size, NULL, NULL, NULL, 0, this->deviceNumber ), this->deviceNumber );
    if ( !IsNoError(iRC) )
    {
      this->ShowError(iRC);
      return DEVICE_LOCALLY_DEFINED_ERROR;
    }
  }
  return DEVICE_OK;
}

int ABSCamera::StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow)
{
  int ret;
  bAbortGetImageCalled = false;
  u16 wFrameRate = (u16) (1000.0 / interval_ms);
  u32 dwRC;

  if (IsCapturing())
    return DEVICE_CAMERA_BUSY_ACQUIRING;

  // remember overflow flag
  stopOnOverflow_ = stopOnOverflow;

  // open the shutter (have not to be done)
  ret = GetCoreCallback()->PrepareForAcq(this);
  if (ret != DEVICE_OK)
         return ret;

  // make sure the circular buffer is properly sized => use 3 Buffers (ABSCAM_CIRCULAR_BUFFER_IMG_COUNT)
  GetCoreCallback()->InitializeImageBuffer(GetNumberOfComponents(), ABSCAM_CIRCULAR_BUFFER_IMG_COUNT, GetImageWidth(), GetImageHeight(), GetImageBytesPerPixel());

  double actualIntervalMs = max(GetExposure(), interval_ms);
  SetProperty(MM::g_Keyword_ActualInterval_ms, CDeviceUtils::ConvertToString(actualIntervalMs)); 

  // unlimited ?
  if (interval_ms == 0.0) wFrameRate = 0;

  // set framerate
  dwRC = GET_RC(CamUSB_SetFramerateLimit(&wFrameRate, this->deviceNumber), this->deviceNumber);
  // switch to coninous mode
  dwRC = GET_RC(CamUSB_SetCaptureMode(MODE_CONTINUOUS, 0, this->deviceNumber), this->deviceNumber);     

  // start thread
  thd_->Start(numImages, interval_ms);
  
  return DEVICE_OK;
}

int ABSCamera::StopSequenceAcquisition()
{
  int nRet;

  bAbortGetImageCalled = true;
  CamUSB_AbortGetImage(this->deviceNumber);

  // call base class
  nRet = CCameraBase<ABSCamera>::StopSequenceAcquisition();

  CamUSB_SetCaptureMode(MODE_TRIGGERED_SW, 1, this->deviceNumber);
  return nRet;
}

int ABSCamera::ThreadRun()
{    
  int ret=DEVICE_ERR;

  // capture image
  ret = SnapImage();
  if (ret != DEVICE_OK)
  {
     return ret;
  }
  // pass image to circular buffer
  ret = InsertImage();
  if (ret != DEVICE_OK)
  {
     return ret;
  }
  return ret;
}

int ABSCamera::PrepareSequenceAcqusition() 
{
  return DEVICE_OK;
}

int ABSCamera::InsertImage()
{
  // insert image into the circular buffer  
  // insert all three channels at once
  int ret = GetCoreCallback()->InsertMultiChannel(this, GetImageBuffer(), GetNumberOfComponents(), GetImageWidth(), GetImageHeight(), GetImageBytesPerPixel());  
  if (!stopOnOverflow_ && ret == DEVICE_BUFFER_OVERFLOW)
  {
    // do not stop on overflow - just reset the buffer
    GetCoreCallback()->ClearImageBuffer(this);
    // repeat the insert
    return GetCoreCallback()->InsertMultiChannel(this, GetImageBuffer(), GetNumberOfComponents(), GetImageWidth(), GetImageHeight(), GetImageBytesPerPixel());
  } 
  else
  {
    return ret;
  }
}

void ClearAllDeviceNames(void)
{	
	// remove all device names
	g_availableDevices.clear( );
}


long ABSCamera::GetFlip()
{
  u32 dwRC;
  S_FLIP_RETVALS sFR = {0};
  u32 dwSize = sizeof(sFR);
  
  dwRC = GET_RC(CamUSB_GetFunction(FUNC_FLIP, &sFR, &dwSize, NULL, 0, NULL, 0, this->deviceNumber), this->deviceNumber);

  return (int)sFR.wFlipMode;
}

void ABSCamera::SetFlip(long nFlip)
{
  u32 dwRC;
  S_FLIP_PARAMS sFP = {0};
  u32 dwSize = sizeof(sFP);
  sFP.wFlipMode = (u16) nFlip;
  
  dwRC = GET_RC(CamUSB_SetFunction(FUNC_FLIP, &sFP, dwSize, NULL, NULL, NULL, 0, this->deviceNumber), this->deviceNumber);
}


int ABSCamera::InitSupportedPixeltypes( void )
{  
  SupportedPixelTypes *pSupportedPixelTypes    = NULL;
  unsigned int         nCntSupportedPixelTypes = 0;
  int                  iMaxUsedBpp = 0;
  int                  iCurrentUsedBpp;
  unsigned int         nMaxSupportedIndex = 0;

  // release the old ones
  this->cPixeltypes.clear();

  // if no caps return
  if ( this->pixelTypeCap == NULL ) return DEVICE_ERR;

  if (bColor) // color pixeltypes
  {
    nCntSupportedPixelTypes = (unsigned int)         CNT_ELEMENTS(g_sSupportedPixelTypesColor);
    pSupportedPixelTypes    = (SupportedPixelTypes*) g_sSupportedPixelTypesColor;
  }
  else // mono pixeltypes
  {
    nCntSupportedPixelTypes = (unsigned int)         CNT_ELEMENTS(g_sSupportedPixelTypesMono);
    pSupportedPixelTypes    = (SupportedPixelTypes*) g_sSupportedPixelTypesMono;
  }


  for ( unsigned int p=0; p < this->pixelTypeCap->dwCount; p++ )
  {
      for ( unsigned int n=0; n < nCntSupportedPixelTypes; n++ )
      {
        if( pixelTypeCap->dwPixelType[p] == pSupportedPixelTypes[n].dwPixelType )
        {
          // add to camera supported list
          this->cPixeltypes.push_back(pSupportedPixelTypes[n]);

          // detect the deepest pixeltype (max used bpp)
          iCurrentUsedBpp = GetUsedBpp(pSupportedPixelTypes[n].dwPixelType);
          if (iCurrentUsedBpp > iMaxUsedBpp)
          {
            iMaxUsedBpp        = iCurrentUsedBpp;
            nMaxSupportedIndex = n;           
          }
        }
      }
    }

    if (iMaxUsedBpp != 0)
    {
      SupportedPixelTypes supportedPixelTypesMax = pSupportedPixelTypes[nMaxSupportedIndex];
      supportedPixelTypesMax.strPixelType = "Max";    // modify name to max
      this->cPixeltypes.push_back(supportedPixelTypesMax);  // add to camera supported list
    }   

    return DEVICE_OK;
}

unsigned int ABSCamera::StringToPixelType( std::string strPixelType )
{
  unsigned int dwPixelType = 0;
  for ( u32 n=0; n < this->cPixeltypes.size(); n++ )
    {
      if ( strPixelType.compare( this->cPixeltypes[n].strPixelType ) == 0 ) 
      {
         dwPixelType = this->cPixeltypes[n].dwPixelType;
         break;
      }
    }
  return dwPixelType;
}

unsigned int ABSCamera::BinValueToCamValue( int iBinning )
{
  unsigned int dwBin;

  switch ( iBinning )
  {
  case 9:   dwBin = XY_BIN_9X; break;
  case 8:   dwBin = XY_BIN_8X; break;
  case 7:   dwBin = XY_BIN_7X; break;
  case 6:   dwBin = XY_BIN_6X; break;
  case 5:   dwBin = XY_BIN_5X; break;
  case 4:   dwBin = XY_BIN_4X; break;
  case 3:   dwBin = XY_BIN_3X; break;
  case 2:   dwBin = XY_BIN_2X; break;
  case 1:
  default:  dwBin = XY_BIN_NONE; break;
  }
  return dwBin;
}

int ABSCamera::CamValueToBinValue( unsigned int dwBin )
{
  int iBinning;

  switch ( dwBin )
  {
  case XY_BIN_9X: iBinning = 9; break;
  case XY_BIN_8X: iBinning = 8; break;
  case XY_BIN_7X: iBinning = 7; break;
  case XY_BIN_6X: iBinning = 6; break;
  case XY_BIN_5X: iBinning = 5; break;
  case XY_BIN_4X: iBinning = 4; break;
  case XY_BIN_3X: iBinning = 3; break;
  case XY_BIN_2X: iBinning = 2; break;
  case XY_BIN_NONE:
  default:        iBinning = 1; break;
  }
  return iBinning;
}
