///////////////////////////////////////////////////////////////////////////////
// FILE:          SpotDevice.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Diagnostic Spot Camera Implementation
// COPYRIGHT:     University of California, San Francisco, 2009
//
// AUTHOR:        Karl Hoover, UCSF
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
// CVS:           
//


#ifdef _WINDOWS
#include <windows.h>
#endif
#include "SpotDevice.h"
//#pragma pack(1)

//#include "spot_defs.h"
//#include "SaveBmpImage.h"

#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/MMDevice.h"


#include "SpotCamera.h"

// these statics are instances per DLL instantiation
// when callback is called from working thread, need to callback again to Camera
SpotCamera* g_Camera;
SpotDevice* g_Device;

static MMThreadLock libraryLock_s;

static bool sequenceMode_s;
static MMThreadLock sequenceModeLock_s;

static bool imageReady_s;
static MMThreadLock imageReadyFlagLock_s;


// construct the device  - this just loads the dll and maps the API functions 
SpotDevice::SpotDevice(SpotCamera* pCamera): 
   pMMCamera_(pCamera), 
   voidStarBuffer_(NULL),
   pbuf_(NULL), 
   sizeofbuf_(0),
   selectedCameraIndexRequested_(-1), 
   nBufSize_(0)
{
	//MMThreadGuard libGuard(libraryLock_s);

	// load the library and setup all the API calls
	bool loaded = static_cast<bool>(DefineAPI());
	if(!loaded) throw SpotBad("can't load Spot .dll");  // todo UI can't handle this quite right

	// query for all attached Spot camera
	SPOT_DEVICE_STRUCT astDevices[SPOT_MAX_DEVICES];
	std::string message;

	// clear any error status
   SpotAPI(SpotClearStatus)();

	if( !ProcessSpotCode( SpotAPI(SpotFindDevices)(astDevices, &nNumCameras_), message))
   {
		// no cameras are powered on

		// can't log messages in camera ctor for some reason
		//if (pMMCamera_)
		//	pMMCamera_->LogMessage(message);
   }
	// otherwise if there is warning message, log it.
	if ( 0 < message.length())
	{
		CodeUtility::DebugOutput( message.c_str());
		//if (pMMCamera_)
		//	pMMCamera_->LogMessage(message);

	}

   for (int ixDevice = 0; ixDevice < nNumCameras_; ixDevice ++)
   {
		availableSpotCameras_.insert( std::pair<int,std::string>(ixDevice,astDevices[ixDevice].szDescription));
   }


}


bool  SpotDevice::DefineAPI()
{

#ifdef _WINDOWS
   HKEY hKey;
   char szPath[MAX_PATH+15];
   DWORD dwSize;

#pragma warning(disable:4996)
   hSpotCamDLL_ = NULL;
   // Look for the Registry entry to tell us where to find SpotCam.dll
   if (RegOpenKeyEx(HKEY_LOCAL_MACHINE, "SOFTWARE\\Diagnostic Instruments, Inc.\\SPOT Camera for Windows", 0, KEY_QUERY_VALUE, &hKey) == ERROR_SUCCESS)
   {
      dwSize = MAX_PATH;
      if (RegQueryValueEx(hKey, "Driver Path", 0, NULL, (BYTE *)szPath, &dwSize) == ERROR_SUCCESS)
      {
         strcat(szPath, "\\SpotCam.dll");
         hSpotCamDLL_ = LoadLibrary(szPath);
      }
      RegCloseKey(hKey);
   }
   if (!hSpotCamDLL_)   // Try loading SpotCam.dll from somewhere in the system PATH
      hSpotCamDLL_ = LoadLibrary("SpotCam.dll");
   if (hSpotCamDLL_)
   { // Get the addresses of the API functions which we plan to call
      pSpotInit = (SPOTEXIT)GetProcAddress(hSpotCamDLL_, "SpotInit");
      pSpotExit = (SPOTEXIT)GetProcAddress(hSpotCamDLL_, "SpotExit");
      pSpotSetValue = (SPOTSETVALUE)GetProcAddress(hSpotCamDLL_, "SpotSetValue");
      pSpotGetValue = (SPOTGETVALUE)GetProcAddress(hSpotCamDLL_, "SpotGetValue");
      pSpotGetCameraAttributes = (SPOTGETCAMERAATTRIBUTES)GetProcAddress(hSpotCamDLL_, "SpotGetCameraAttributes");
      pSpotGetVersionInfo2 = (SPOTGETVERSIONINFO2)GetProcAddress(hSpotCamDLL_, "SpotGetVersionInfo2");
      pSpotComputeExposure2 = (SPOTCOMPUTEEXPOSURE2)GetProcAddress(hSpotCamDLL_, "SpotComputeExposure2");
      pSpotComputeWhiteBalance = (SPOTCOMPUTEWHITEBALANCE)GetProcAddress(hSpotCamDLL_, "SpotComputeWhiteBalance");
      pSpotGetImage = (SPOTGETIMAGE)GetProcAddress(hSpotCamDLL_, "SpotGetImage");
      pSpotGetLiveImages = (SPOTGETLIVEIMAGES)GetProcAddress(hSpotCamDLL_, "SpotGetLiveImages");
      pSpotGetSequentialImages = (SPOTGETSEQUENTIALIMAGES)GetProcAddress(hSpotCamDLL_, "SpotGetSequentialImages");;
      pSpotRetrieveSequentialImage = (SPOTRETRIEVESEQUENTIALIMAGE)GetProcAddress(hSpotCamDLL_, "SpotRetrieveSequentialImage");
      pSpotSetAbortFlag = (SPOTSETABORTFLAG)GetProcAddress(hSpotCamDLL_, "SpotSetAbortFlag");
      pSpotSetCallback = (SPOTSETCALLBACK)GetProcAddress(hSpotCamDLL_, "SpotSetCallback");
      pSpotSetDeviceNotificationCallback = (SPOTSETDEVICENOTIFICATIONCALLBACK)GetProcAddress(hSpotCamDLL_, "SpotSetDeviceNotificationCallback");
      pSpotClearStatus = (SPOTCLEARSTATUS)GetProcAddress(hSpotCamDLL_, "SpotClearStatus");
      pSpotFindDevices = (SPOTFINDDEVICES)GetProcAddress(hSpotCamDLL_, "SpotFindDevices");
      pSpotGetExposureTimestamp = (SPOTGETEXPOSURETIMESTAMP)GetProcAddress(hSpotCamDLL_, "SpotGetExposureTimestamp");
      pSpotGetSensorCurrentTemperature = (SPOTGETSENSORCURRENTTEMPERATURE)GetProcAddress(hSpotCamDLL_, "SpotGetSensorCurrentTemperature");
      pSpotGetSensorExposureTemperature = (SPOTGETSENSOREXPOSURETEMPERATURE)GetProcAddress(hSpotCamDLL_, "SpotGetSensorExposureTemperature");
      pSpotWaitForStatusChange = (SPOTWAITFORSTATUSCHANGE)GetProcAddress(hSpotCamDLL_, "SpotWaitForStatusChange");
      pSpotGetCameraErrorCode = (SPOTGETCAMERAERRORCODE)GetProcAddress(hSpotCamDLL_, "SpotGetCameraErrorCode");

		pSpotQueryStatus = (SPOTQUERYSTATUS)GetProcAddress(hSpotCamDLL_, "SpotQueryStatus");
		pSpotGetActualGain = (SPOTGETACTUALGAIN)GetProcAddress(hSpotCamDLL_,"SpotGetActualGainValue");

   }
   return(NULL != hSpotCamDLL_);
#else

	return true;
#endif
}

void SpotDevice::InitializeCamera()
{

   DWORD dwAttributes;
   SPOT_VERSION_STRUCT2 stVerInfo;
   short asTemp[100];

   //BOOL bTemp;
	
	// clear any error status
   SpotAPI(SpotClearStatus)();

   // Tell the SpotCam driver to use the camera that the user selected
   SpotAPI(SpotSetValue)(SPOT_DRIVERDEVICENUMBER, &selectedCameraIndexRequested_);
   bAbortFlag_ = FALSE;
	std::string message;
	int retry = 0;
	bool succ;

	// first call to SpotInit after abnormal shutdown always fails, so always do a retry

	while( 1)
	{
		succ = ProcessSpotCode(SpotAPI(SpotInit)(), message);
		++retry;
		if( !succ && 0 == message.length())
			message = "unknown error in SpotInit - was program shutdown abnormally? ";
		// if already retried, then throw an error...
		if (! succ && 2 < retry)
		{
			// failed
			selectedCameraIndexRequested_ = -1;
			throw SpotBad(message.c_str());
		}
		if( 0 < message.length())
			CodeUtility::DebugOutput( message.c_str());
		if (succ)
			break;
	}




   SpotAPI(SpotSetAbortFlag)(&bAbortFlag_);  
   SpotAPI(SpotGetCameraAttributes)(&dwAttributes);
   stCameraInfo_.bCanDoColor = (long) (dwAttributes & SPOT_ATTR_COLOR) != 0 ? true : false;
   stCameraInfo_.bHasMosaicSensor = (long) (dwAttributes & SPOT_ATTR_MOSAIC) != 0 ? true : false;
   stCameraInfo_.bDoesMultiShotColor = (long) (dwAttributes & SPOT_ATTR_COLORFILTER) != 0 ? true : false;
   stCameraInfo_.bHasFilterWheel = (long) (dwAttributes & SPOT_ATTR_FILTERWHEEL) != 0 ? true : false;
   stCameraInfo_.bHasSlider = (long) (dwAttributes & SPOT_ATTR_SLIDER) != 0 ? true : false;
   stCameraInfo_.bCanDetectSliderPosition = (long) (dwAttributes & SPOT_ATTR_SLIDERPOSITIONDETECTION) != 0 ? true : false;
   stCameraInfo_.bCanComputeExposure = (long) (dwAttributes & SPOT_ATTR_AUTOEXPOSURE) != 0 ? true : false;
   stCameraInfo_.bCanDoEdgeTrigger = (long) (dwAttributes & SPOT_ATTR_EDGETRIGGER) != 0 ? true : false;
   stCameraInfo_.bCanDoBulbTrigger = (long) (dwAttributes & SPOT_ATTR_BULBTRIGGER) != 0 ? true : false;
   stCameraInfo_.bCanSetTriggerActiveState = (long) (dwAttributes & SPOT_ATTR_TRIGGERACTIVESTATE) != 0 ? true : false;
   stCameraInfo_.bCanReadSensorTemperature = (long) (dwAttributes & SPOT_ATTR_TEMPERATUREREADOUT) != 0 ? true : false;
   stCameraInfo_.bCanRegulateSensorTemperature = (long) (dwAttributes & SPOT_ATTR_TEMPERATUREREGULATION) != 0 ? true : false;
   stCameraInfo_.bCanDoAccurateTTLOutputAndTriggerDelayTiming = (long) (dwAttributes & SPOT_ATTR_ACCURATETTLDELAYTIMING) != 0 ? true : false;
   stCameraInfo_.bCanDoLiveMode = (long) (dwAttributes & SPOT_ATTR_LIVEMODE) != 0 ? true : false;
   stCameraInfo_.bCanShiftImageSensor = (long) (dwAttributes & SPOT_ATTR_SENSORSHIFTING) != 0 ? true : false;
   stCameraInfo_.bIs1394FireWireCamera = (long) (dwAttributes & SPOT_ATTR_1394) != 0 ? true : false;
   stCameraInfo_.bCanDoLiveImageScaling = (long) (dwAttributes & SPOT_ATTR_LIVEHISTOGRAM) != 0 ? true : false;
   SpotAPI(SpotGetVersionInfo2)(&stVerInfo);
#ifdef WIN32
#pragma warning(disable:4996)
#endif
   strcpy(stCameraInfo_.szModelNumber, stVerInfo.szCameraModelNum);
   strcpy(stCameraInfo_.szSerialNumber, stVerInfo.szCameraSerialNum);
   strcpy(stCameraInfo_.szRevisionNumber, stVerInfo.szCameraRevNum);
   // Determine the min and max allowable acquisition area sizes
   if (SpotAPI(SpotGetValue)(SPOT_MAXIMAGERECTSIZE, asTemp) == SPOT_SUCCESS)
   {
      stCameraInfo_.nMaxImageWidth = asTemp[0];
      stCameraInfo_.nMaxImageHeight = asTemp[1];
   }
   if (SpotAPI(SpotGetValue)(SPOT_MINIMAGERECTSIZE, asTemp) == SPOT_SUCCESS)
   {
      stCameraInfo_.nMinImageWidth = asTemp[0];
      stCameraInfo_.nMinImageHeight = asTemp[1];
   }
   if (SpotAPI(SpotGetValue)(SPOT_MAXPIXELRESOLUTIONLEVEL, asTemp) == SPOT_SUCCESS)
      stCameraInfo_.nMaxPixelResolutionLevel = asTemp[0];
   if (stCameraInfo_.bHasMosaicSensor && (SpotAPI(SpotGetValue)(SPOT_MOSAICPATTERN, asTemp) == SPOT_SUCCESS))
      stCameraInfo_.nMosaicPattern = asTemp[0];
   // Find out how many readout circuits this camera has
   if (SpotAPI(SpotGetValue)(SPOT_NUMBERREADOUTCIRCUITS, asTemp) == SPOT_SUCCESS) stCameraInfo_.nNumReadoutCircuits = asTemp[0];
   // Use readout circuit 0
   asTemp[0] = 0;
   SpotAPI(SpotSetValue)(SPOT_READOUTCIRCUIT, asTemp);
   // Find out how many gain ports are available for readout circuit 0
   if (SpotAPI(SpotGetValue)(SPOT_MAXGAINPORTNUMBER, asTemp) == SPOT_SUCCESS) stCameraInfo_.nNumGainPorts0 = asTemp[0] + 1;
   if (stCameraInfo_.nNumGainPorts0 >= 2)
   { // Use gain port 1 if it provides the same type of gain as port 0, but as a continuous range
      if ((SpotAPI(SpotGetValue)(SPOT_PORT1GAINATTRIBUTES, &dwAttributes) == SPOT_SUCCESS) && (dwAttributes & SPOT_GAINATTR_SAMEASPORT0))
      {
         asTemp[0] = (short)1;
         SpotAPI(SpotSetValue)(SPOT_GAINPORTNUMBER, asTemp);
      }
   }
   // Get the rest of the camera's values ...

#ifdef USE_SPOTWAITFORSTATUSCHANGE
   BOOL bTemp = TRUE;
   SpotAPI(SpotSetValue)(SPOT_WAITFORSTATUSCHANGES, &bTemp);  // Enable polling mode
#else
   // Register our callback function
	SpotAPI(SpotSetCallback)((SPOTCALLBACK)(CalledBackfromDriver), 0);
#endif // USE_SPOTWAITFORSTATUSCHANGE



   //if (stCameraInfo_.bCanDoColor)
   //{ // The camera can do color, so we will ask for a color image at 24 bpp
   //   nImageBitDepth_ = 24;
   //   sValue = (short)nImageBitDepth_;
   //   SpotAPI(SpotSetValue)(SPOT_BITDEPTH, &sValue);

   //   if (stCameraInfo_.bDoesMultiShotColor) // todo - will we ever deal with these multi-shot cameras??
   //   { // The camera has a color filter
   //      SPOT_COLOR_ENABLE_STRUCT2 stColorEnable;

   //      stColorEnable.bEnableRed = stColorEnable.bEnableGreen =
   //         stColorEnable.bEnableBlue = TRUE;
   //      stColorEnable.bEnableClear = FALSE;
   //      SpotAPI(SpotSetValue)(SPOT_COLORENABLE2, &stColorEnable);  // Enable red, green, and blue
   //      SpotAPI(SpotSetValue)(SPOT_COLORORDER, "BRG");  // Set the acq order to blue-red-green

			////todo - move this to a property
   //      bTemp = TRUE;      // Enable color enhancements
   //      SpotAPI(SpotSetValue)(SPOT_ENHANCECOLORS, &bTemp);
   //   }
   //}
   //else
   //{ // The camera can only acquire monochrome images
   //   nImageBitDepth_ = 8;             // Ask for 8 bpp images
   //   sValue = (short)nImageBitDepth_;
   //   SpotAPI(SpotSetValue)(SPOT_BITDEPTH, &sValue);
   //}


       void* pNoWarning = const_cast<void *>(reinterpret_cast<const void*>(""));

   SpotAPI(SpotSetValue)(SPOT_BIASFRMSUBTRACT, pNoWarning);    // Disable bias frame subtraction
   SpotAPI(SpotSetValue)(SPOT_FLATFLDCORRECT, pNoWarning);     // Disable flatfield correction
   SpotAPI(SpotSetValue)(SPOT_BKGDIMAGESUBTRACT, pNoWarning); // Disable thermal frame subtraction


	g_Camera = pMMCamera_;
	g_Device = this;

#ifdef WIN32
#pragma warning(disable:4996)
#endif
}








// "end of spot example"


bool SpotDevice::Initialize( std::string cameraName)
{
	//MMThreadGuard libGuard(libraryLock_s);
	 // A camera has not yet been selected
	//selectedCameraIndexRequested_ is selected camera index from configuration
	// open the camera and register the callback
	InitializeCamera();
	unsigned int x,  y,  xSize, ySize;
	GetROI(x,  y, xSize,  ySize, true);
	std::ostringstream messs;
	messs << " max image ROI is " << x << " " << y << " " << xSize << " " << ySize;
	CodeUtility::DebugOutput(messs.str().c_str());
	return ( -1 < selectedCameraIndexRequested_); // was the selected camera actually available and initialized??
}

void SpotDevice::ShutdownCamera( void )
{


	selectedCameraIndexRequested_ = -1;
	std::string message;

	//MMThreadGuard libGuard(libraryLock_s);
	ProcessSpotCode(SpotAPI(SpotExit)(), message);
	if( 0 < message.length())
	{
		CodeUtility::DebugOutput(message);
		// todo
		//CodeUtility::DebugOutput((succ?std::string("Warning: "):std::string("Error: "))+message.c_str());
	}
	
}

// retrieve one image
char* SpotDevice::GetRawSpotImage()
{
	char* ret = 0;
	//MMThreadGuard libGuard(libraryLock_s);
	MMThreadGuard guard(imageReadyFlagLock_s);
#if 0
	if (imageReady_s)
	{

		std::string message;
     //: if( ! ProcessSpotCode(SpotAPI(SpotGetImage)(0, FALSE, 0, voidStarBuffer_, NULL, NULL, NULL), message))
		{
			CodeUtility::DebugOutput(message.c_str());
		}
		else
		{
			ret = (char*)voidStarBuffer_;
		}
		imageReady_s = false;
	}
#endif
	return ret;
}



/*  SpotGetSequentialImages
Parameters:
nNumImages
The number of image to acquire.  If this value is SPOT_INFINITEIMAGES, images will be acquired until the operation is aborted.
nIntervalMSec
The interval to wait from the beginning of one acquisition to the next, in milliseconds.  If this value is 
SPOT_INTERVALSHORTASPOSSIBLE, images will be acquired as quickly as possible.  The value should be zero if the exposures 
need to be synchronized through software with an external process.  This value will be ignored if an external trigger is being used for 
each image.
bAutoExposureOnEach
A flag specifying whether the exposure times and gain level should be computed before acquiring each image.  If auto-exposure is 
disabled, this parameter will be ignored.  If auto-exposure is enabled, and this value is FALSE, the exposure and gain will only be 
computed before the first image, and the same exposure and gain will be used for each subsequent image in the sequence. If this 
value is TRUE, the exposure and gain will be recomputed for each image in the sequence.
bUseTriggerOrSetTTLOuputOnEach
A flag which, if external triggering is enabled, specifies whether or not the acquisition of each image in the sequence should require 
an external trigger pulse, or if TTL output is enabled, specifies whether or not the TTL output should be activated for each image 
acquisition.
bDeferProcessing
A flag specifying that the processing of images should be deferred until all the images in the sequence have been acquired.  This 
option saves processing time between image acquisitions and may be useful if a short interval is required.  If a disk-caching path has 
been specified (with the SPOT_SEQIMAGEDISKCACHEPATH parameter), the raw image data will be cached to disk.  Otherwise, 
it will be cached in memory.
ppImageBuffers
Points to an array of buffers where the pixel data of the acquired images are to be placed by the driver.  Refer to the section on 
SpotGetImage for a description of the image buffers.  This value may be NULL, in which case the application will need to call 
SpotRetrieveSequentialImage once for each image acquired.  If nNumImages is SPOT_INFINITEIMAGES, only the first buffer in 
the array, if any, will be used, and all images will be placed in the same buffer after processing.
lRowBytes
Specifies the number of image data buffers bytes per image row.  (MacOS only)
*/

void SpotDevice::SetupImageSequence( const int nimages , const int interval )
{
	std::string message;
	std::ostringstream timingInfo;

	bool suc;
	double snapStartTime = 0.;
	if( pMMCamera_)
		snapStartTime = pMMCamera_->SnapImageStartTime();

	if ( 0. < snapStartTime)
	{
		timingInfo << "SetupImageSequence entered " << pMMCamera_->GetCurrentMMTime().getMsec() - snapStartTime << "\n";
	}


   short asTemp[2];


	int nNumImages=nimages;

	if( -1 == nNumImages)
		nNumImages = SPOT_INFINITEIMAGES;

	// for just one image, use the SnapImage interface
	{
		MMThreadGuard modeGuard( sequenceModeLock_s);
		sequenceMode_s = ( 1 != nNumImages);
	}
	if ( 0. < snapStartTime)
	{
		timingInfo << "SetupImageSequence set sequenceMode_s: " << pMMCamera_->GetCurrentMMTime().getMsec() - snapStartTime << "\n";
	}

	// clear any error status
   SpotAPI(SpotClearStatus)();

	if ( 0. < snapStartTime)
	{
		timingInfo << "SetupImageSequence cleared status " << pMMCamera_->GetCurrentMMTime().getMsec() - snapStartTime << "\n";
	}

#if 0
   // Set parameters for acquisition.  
   SpotAPI(SpotSetValue)(SPOT_AUTOEXPOSE, &bDoAutoExposure);
   if (bDoAutoExposure)
   { // Auto-exposure
      sValue = (short)SPOT_IMAGEBRIGHTFLD;  // Request bright-field exposure computation
      SpotAPI(SpotSetValue)(SPOT_IMAGETYPE, &sValue);
      sValue = (short)1;        // Limit the computed gain to 1
      SpotAPI(SpotSetValue)(SPOT_AUTOGAINLIMIT, &sValue);
      lTemp = 1000;            // Set the brightness adj to 1.0
      SpotAPI(SpotSetValue)(SPOT_BRIGHTNESSADJX1000, &lTemp);
      sValue = (short)0;       // Restrict the computed exposure time to be at least 10 ms
      SpotAPI(SpotSetValue)(SPOT_MINEXPOSUREMSEC, &sValue);
   }


   bTemp = TRUE;                      // Enable TTL output
   SpotAPI(SpotSetValue)(SPOT_ENABLETTLOUTPUT, &bTemp);
   if (stCameraInfo_.bCanDoAccurateTTLOutputAndTriggerDelayTiming)
      lTemp = 15;      // Set the TTL output delay to 15 microseconds
   else lTemp = 1000;  // The camera can't time TTL output delay to the microsecond
   SpotAPI(SpotSetValue)(SPOT_TTLOUTPUTDELAY, &lTemp);
#endif

	// Now that the parameters have been set, ask the SpotCam driver how big the acquired image will be
	// EF: This is used in ifdef __APPLE__ and should be outside the if 0 statement
	SpotAPI(SpotGetValue)(SPOT_ACQUIREDIMAGESIZE, asTemp);
	nImageWidth_ = nImageHeight_ = 0;
	nImageWidth_ = (int) asTemp[0];
	nImageHeight_ = (int) asTemp[1];

	bAbortFlag_ = FALSE;
	
   SpotAPI(SpotGetValue)(SPOT_BITDEPTH, &asTemp);
	bitDepthOfADC_  = asTemp[0];
	if ( 0. < snapStartTime)
	{
		timingInfo << "SetupImageSequence got bit depth " << pMMCamera_->GetCurrentMMTime().getMsec() - snapStartTime << "\n";
	}
	// todo - SPOT_BITDEPTH can be, for example, 14, but camera still returns RGB components (i.e 42 bit color images)
	switch( bitDepthOfADC_)
	{
	case 8:
		nImageBitDepth_ = 8;
		break;
	case 10:
	case 12:
	case 14:
	case 16:
		nImageBitDepth_ = 16;
		break;
	case 24:
		nImageBitDepth_ = 24;
		break;
	case 36:
	case 30:
	case 42:
		nImageBitDepth_ = 48;
		break;
	default:
		{
			std::ostringstream messs;
			messs << "unsupported ADC bit depth: " << bitDepthOfADC_;		
			throw SpotBad(messs.str());
		}
	}

#ifdef __APPLE__
        int nchars = nImageBitDepth_ / 8;
        if (3==nchars) nchars = 4;
        unsigned long rowBytes = nImageWidth_ * nchars;
#endif


	//SPOT_INTERVALSHORTASPOSSIBLE

#ifdef USE_SPOTWAITFORSTATUSCHANGE
	int nloops = 0;
	bool isIdle = false;
	long status;
	while(!isIdle )
	{
		SpotAPI(SpotQueryStatus)(FALSE, &status);
		if (SPOT_STATUSIDLE == status)
		{
			isIdle = true;
		}
		else
		{
			CDeviceUtils::SleepMs(10);
			if( 5000 < nloops++)
			{
				throw SpotBad("timeout waiting for camera idle ");
			}
		}
	}
#endif

	//SpotAPI(SpotClearStatus)();
	int spotcode = SpotAPI(SpotGetSequentialImages)(nNumImages, interval , FALSE, FALSE, FALSE, NULL
#ifdef __APPLE__
//int WINAPI SpotGetSequentialImages(int nNumImages,int nIntervalMSec,BOOL bAutoExposeOnEach,BOOL bUseTriggerOrSetTTLOuputOnEach, BOOL bDeferProcessing,void **ppImageBuffers,
//	unsigned long lRowBytes );
		,rowBytes

#else
//int WINAPI SpotGetSequentialImages(int nNumImages, int nIntervalMSec, BOOL bAutoExposeOnEach, BOOL bUseTriggerOrSetTTLOuputOnEach, BOOL bDeferProcessing,void **ppImageBuffers);
//

#endif
		);

	if ( 0. < snapStartTime)
	{
		timingInfo << "SetupImageSequence SpotGetSequentialImages " << pMMCamera_->GetCurrentMMTime().getMsec() - snapStartTime << "\n";
	}
	suc = ProcessSpotCode( spotcode, message);
	if ( 0. < snapStartTime)
	{
		timingInfo << "SetupImageSequence ProcessSpotCode " << pMMCamera_->GetCurrentMMTime().getMsec() - snapStartTime << "\n";
	}
	if(!suc)
		throw(SpotBad(message.c_str()));
#ifdef USE_SPOTWAITFORSTATUSCHANGE
	if ( SPOT_RUNNING != spotcode)
		throw(SpotBad(" image sequence was not started "));
#endif


	if( 0 < message.length())
		CodeUtility::DebugOutput(message);


	if ( 0. < snapStartTime)
	{
		timingInfo << "SetupImageSequence exited " << pMMCamera_->GetCurrentMMTime().getMsec() - snapStartTime << "\n";


		CodeUtility::DebugOutput(timingInfo.str().c_str());
	}

}




// setup 'endless' sequence with fastest possible repeat time.

void SpotDevice::SetupNonStopSequence(void)
{
	SetupImageSequence();
}


// retrieve most recent exposure times for the selected camera, return values in millisecond
double SpotDevice::ExposureTime(void)
{
	//MMThreadGuard libGuard(libraryLock_s);

	double totalExposureTime;
	long lExpNanosecPerIncr;
	std::vector<double>  values;
	SPOT_EXPOSURE_STRUCT2 stExposure;
	std::string message;

	// get the time scale for the exposure
	if( !ProcessSpotCode( SpotAPI(SpotGetValue)(SPOT_EXPOSUREINCREMENT, &lExpNanosecPerIncr), message  ))
	{
      throw SpotBad(message.c_str());
   }

	::memset(&stExposure, 0, sizeof(stExposure));

	// exposure is a structure of several values
	if( !ProcessSpotCode( SpotAPI(SpotGetValue)(SPOT_EXPOSURE2, &stExposure), message  ))
	{
      throw SpotBad(message.c_str());
   }

	if (stCameraInfo_.bCanDoColor && stCameraInfo_.bDoesMultiShotColor  )
	{ // get exposure for Multi Shot acquisition
		values.push_back(stExposure.dwRedExpDur * ((double)lExpNanosecPerIncr/1000000.));
		if( (16<BitDepth() ))
		{
			values.push_back(stExposure.dwGreenExpDur * ((double)lExpNanosecPerIncr/1000000.));
			values.push_back(stExposure.dwBlueExpDur * ((double)lExpNanosecPerIncr/1000000.));
		}
	}
	else  // get exposure time for Single Shot acquisition
	{
		values.push_back(stExposure.dwExpDur * ((double)lExpNanosecPerIncr/1000000.));
	}
	totalExposureTime = 0;
	for( std::vector<double>::iterator i = values.begin(); values.end() != i; totalExposureTime+= *(i++));

	return totalExposureTime;
}
	
	
void SpotDevice::ExposureTime(double value)
{ // Manual exposure
	//MMThreadGuard libGuard(libraryLock_s);
	
   long lExpNanosecPerIncr;
	std::vector<double>  values;
	values.push_back(value);

   SPOT_EXPOSURE_STRUCT2 stExposure;
	std::string message;

	// get the time scale for the exposure
	if( !ProcessSpotCode( SpotAPI(SpotGetValue)(SPOT_EXPOSUREINCREMENT, &lExpNanosecPerIncr), message  ))
	{
      throw SpotBad(message.c_str());
   }
   memset(&stExposure, 0, sizeof(stExposure));
	
	// retrieve the current value of the gain
   stExposure.nGain = (short)(0.5 + Gain());

	if (stCameraInfo_.bCanDoColor && stCameraInfo_.bDoesMultiShotColor )
   { // Set exposure for multi Shot acquisition...
		if ( (16<BitDepth()))
		{
		values[0]/=3.;
		while ( values.size() < 3)
		{
			values.push_back(values[values.size()-1]);
		}
      stExposure.dwRedExpDur = (long)( 0.5 + values[0] * (1000000 / lExpNanosecPerIncr));
      stExposure.dwGreenExpDur = (long) (0.5 + values[1] * (1000000 / lExpNanosecPerIncr));
      stExposure.dwBlueExpDur = (long) (0.5 + values[2] * (1000000 / lExpNanosecPerIncr));
		}
		else
		{
			stExposure.dwRedExpDur = (long)( 0.5 + values[0] * (1000000 / lExpNanosecPerIncr));
		}
   }
   else  // Set exposure time for Single Shot acquisition
      stExposure.dwExpDur = (long)(0.5 + values[0] * (1000000 / lExpNanosecPerIncr));

	if( !ProcessSpotCode( SpotAPI(SpotSetValue)(SPOT_EXPOSURE2, &stExposure), message  ))
	{
      throw SpotBad(message.c_str());
   }
}


// retrieve most recent exposure times for the selected camera, return values in millisecond
double SpotDevice::ExposureTimeForMultiShot(long componentIndex)
{
	double returnValue = -1.;
	if (!(stCameraInfo_.bCanDoColor && stCameraInfo_.bDoesMultiShotColor))
		return returnValue;

	//MMThreadGuard libGuard(libraryLock_s);

	long lExpNanosecPerIncr;
	SPOT_EXPOSURE_STRUCT2 stExposure;
	std::string message;

	// get the time scale for the exposure
	if( !ProcessSpotCode( SpotAPI(SpotGetValue)(SPOT_EXPOSUREINCREMENT, &lExpNanosecPerIncr), message  ))
	{
      throw SpotBad(message.c_str());
   }
	::memset(&stExposure, 0, sizeof(stExposure));

	// exposure is a structure of several values
	if( !ProcessSpotCode( SpotAPI(SpotGetValue)(SPOT_EXPOSURE2, &stExposure), message  ))
	{
      throw SpotBad(message.c_str());
   }
	//todo!  handle case where color order is different!!!

	switch( componentIndex)
	{
		case 0:
			returnValue = stExposure.dwRedExpDur * ((double)lExpNanosecPerIncr/1000000.);
			break;
		case 1:
			returnValue = stExposure.dwGreenExpDur * ((double)lExpNanosecPerIncr/1000000.);
			break;
		case 2:
			returnValue = stExposure.dwBlueExpDur * ((double)lExpNanosecPerIncr/1000000.);
			break;
	}
	return returnValue;
}
	
	
void SpotDevice::ExposureTimeForMultiShot(double value, long componentIndex)
{ 
	if (!(stCameraInfo_.bCanDoColor && stCameraInfo_.bDoesMultiShotColor))
		return;
	//MMThreadGuard libGuard(libraryLock_s);
	
   long lExpNanosecPerIncr;

   SPOT_EXPOSURE_STRUCT2 stExposure;
	std::string message;

	// get the time scale for the exposure
	if( !ProcessSpotCode( SpotAPI(SpotGetValue)(SPOT_EXPOSUREINCREMENT, &lExpNanosecPerIncr), message  ))
	{
      throw SpotBad(message.c_str());
   }
   memset(&stExposure, 0, sizeof(stExposure));
	
	// exposure is a structure of several values
	if( !ProcessSpotCode( SpotAPI(SpotGetValue)(SPOT_EXPOSURE2, &stExposure), message  ))
	{
      throw SpotBad(message.c_str());
   }
	switch( componentIndex)
	{
		case 0:
			stExposure.dwRedExpDur = (long)( 0.5 + value * (1000000 / lExpNanosecPerIncr));
 			break;
		case 1:
			stExposure.dwGreenExpDur = (long)( 0.5 + value * (1000000 / lExpNanosecPerIncr));
			break;
		case 2:
			stExposure.dwBlueExpDur = (long)( 0.5 + value * (1000000 / lExpNanosecPerIncr));
			break;
	}
	if( !ProcessSpotCode( SpotAPI(SpotSetValue)(SPOT_EXPOSURE2, &stExposure), message  ))
	{
      throw SpotBad(message.c_str());
   }
}



// retrieve most recently computed or selected gain for the selected camera
short SpotDevice::Gain(void)
{
	//MMThreadGuard libGuard(libraryLock_s);
	short value;
	SPOT_EXPOSURE_STRUCT2 stExposure;
	std::string message;

	// get the current exposure settings, which include the gain.
	if( !ProcessSpotCode( SpotAPI(SpotGetValue)(SPOT_EXPOSURE2, &stExposure), message  ))
	{
      throw SpotBad(message.c_str());
   }
	value = stExposure.nGain;
	return value;
}
	
//set the gain
void SpotDevice::Gain(short value)
{ 
	//MMThreadGuard libGuard(libraryLock_s);
	std::string message;
   SPOT_EXPOSURE_STRUCT2 stExposure;
   memset(&stExposure, 0, sizeof(stExposure));

	// get the current exposure settings, which include the gain.
	if( !ProcessSpotCode( SpotAPI(SpotGetValue)(SPOT_EXPOSURE2, &stExposure), message  ))
	{
      throw SpotBad(message.c_str());
   }
	// put the new gain value in...
   stExposure.nGain =  value;   

	if( !ProcessSpotCode( SpotAPI(SpotSetValue)(SPOT_EXPOSURE2, &stExposure), message  ))
	{
      throw SpotBad(message.c_str());
   }
}

// retrieve min, max exposure in milliseconds
void SpotDevice::ExposureLimits( double& minExp, double& maxExp)
{
	//MMThreadGuard libGuard(libraryLock_s);
	long lExpNanosecPerIncr;
	std::string message;
	long values[2];

	// get the time scale for the exposure
	if( !ProcessSpotCode( SpotAPI(SpotGetValue)(SPOT_EXPOSUREINCREMENT, &lExpNanosecPerIncr), message  ))
	{
      throw SpotBad(message.c_str());
   }

	// get the limits
	if( !ProcessSpotCode( SpotAPI(SpotGetValue)(SPOT_EXPOSURELIMITS2, values), message  ))
	{
      throw SpotBad(message.c_str());
   }

	minExp = values[0] * ((double)lExpNanosecPerIncr/1000000.);
	maxExp = values[1] * ((double)lExpNanosecPerIncr/1000000.);

}

bool  SpotDevice::AutoExposure( void) const
{
	//MMThreadGuard libGuard(libraryLock_s);
	int bDoAutoExposure;
   SpotAPI(SpotGetValue)(SPOT_AUTOEXPOSE, &bDoAutoExposure);
	return ( FALSE != bDoAutoExposure);
}

void  SpotDevice::AutoExposure(const bool v)
{
	//MMThreadGuard libGuard(libraryLock_s);
	BOOL  bDoAutoExposure = stCameraInfo_.bCanComputeExposure;
	// to do - get rid of this throw and simply make the property read-only if
	// the camera doesn't support autoexposure
	if( v && !bDoAutoExposure)
		throw SpotBad(" camera does not support autoexposure ");

	bDoAutoExposure = ( v?TRUE:FALSE);
	std::string message;

	if( !ProcessSpotCode( SpotAPI(SpotSetValue)(SPOT_AUTOEXPOSE, &bDoAutoExposure), message  ))
	{
      throw SpotBad(message.c_str());
   }
}


void SpotDevice::BinSize(const short value)
{
	//MMThreadGuard libGuard(libraryLock_s);
   short sValue = value;        
   ;
	std::string message;
	if( !ProcessSpotCode( SpotAPI(SpotSetValue)(SPOT_BINSIZE, &sValue), message  ))
	{
      throw SpotBad(message.c_str());
   }

}

short SpotDevice::BinSize(void)
{
	//MMThreadGuard libGuard(libraryLock_s);
	short sValue;        
	std::string message;
	if( !ProcessSpotCode( SpotAPI(SpotGetValue)(SPOT_BINSIZE, &sValue), message  ))
	{
      throw SpotBad(message.c_str());
   }
	return sValue;
}


// set ROI values
void SpotDevice::SetROI( const unsigned int x, const unsigned int y,  const unsigned int xSize, const unsigned int ySize)
{
	//MMThreadGuard libGuard(libraryLock_s);
	//todo - disallow setting ROI smaller than smallest possible rect.

	std::string message;
	unsigned long values[4];

	values[0] = x;
	values[1] = y;
	values[2] = x + xSize;
	values[3] = y + ySize;
	if( !ProcessSpotCode(SpotAPI(SpotSetValue)(SPOT_IMAGERECT, values), message  ))
	{
      throw SpotBad(message.c_str());

   }
	else if( 0 < message.length())
		this->pMMCamera_->LogMessage(message);
}


// get Region Of Interest values
void SpotDevice::GetROI(unsigned int& x, unsigned int& y, unsigned int& xSize, unsigned int & ySize, bool getFullSensorSize )
{
	//MMThreadGuard libGuard(libraryLock_s);
	std::string message;
	int retco = 0;
	if (getFullSensorSize)
	{
			unsigned short values[4];
			memset(values, 0, 4*sizeof(values[0]));
			retco = SpotAPI(SpotGetValue)(SPOT_MAXIMAGERECTSIZE, values);
			x = 0;
			y = 0;
			xSize = values[0];
			ySize = values[1];
	}
	else
	{
			unsigned long values[4];
			memset(values, 0, 4*sizeof(values[0]));
			retco = SpotAPI(SpotGetValue)(SPOT_IMAGERECT, values);
			x = values[0];
			y = values[1];
			xSize = values[2]-values[0];
			ySize = values[3]-values[1];
	}

	if( !ProcessSpotCode(retco, message  ))
	{
      throw SpotBad(message.c_str());

   }
	else if( 0 < message.length())
		this->pMMCamera_->LogMessage(message);

}




bool SpotDevice::WaitForStatusChanged(const int completionState)
{
	//MMThreadGuard libGuard(libraryLock_s);
	bool complete = false;
	int status;
	long info;

	if (SpotAPI(SpotWaitForStatusChange)(&status, &info, 250))

	{ // We got a notification
		if( completionState == status)
			complete = true;
		switch (status)
		{
		case SPOT_STATUSERROR:
			throw SpotBad(" error on waiting for image ");
			break; // not reachable
		case SPOT_STATUSIDLE:
			break;
		case SPOT_STATUSABORTED:
			break;

		}
	}

	return complete;

}



char* SpotDevice::GetNextSequentialImage(unsigned int& imheight, unsigned int& imwidth, char& bytesppixel)
{
	//MMThreadGuard libGuard(libraryLock_s);
	std::string message;
	short sValue[2];
	bool suc;
	int spotcode;
	double time0 = pMMCamera_->GetCurrentMMTime().getMsec();
   int nRowBytes = 0;//, nPaletteSize;
	
	// EF: byte order of the color for rearranging to MicroManager's BGRA
	const short color_ARGB [] = {3, 2, 1, 0}; // Mac: ARGB to BGRA
	const short color_BGRA [] = {-1}; // Windows: don't rearrange, this is what we need	
	const short color_NOCHANGE [] = {-1}; // Default for bit-depths other than 24bit
	const short *colorOrder = color_NOCHANGE; // default to NOCHANGE, colorOrder will be one of the above

	int bytesToTransfer = sizeofbuf_;
	if( bytesToTransfer < 1)
		bytesToTransfer = 17000000;

	//todo - calculation depends on 1394 vs USB
	// assume transfer rate of 10 Mb /second = 10000 bytes /ms
	int approxTransferTime = (bytesToTransfer+5000)/10000;


#ifdef USE_SPOTWAITFORSTATUSCHANGE
   int nloops = 0;

	while( !WaitForStatusChanged(SPOT_STATUSSEQIMAGEREADY ))
	{
		
		CDeviceUtils::SleepMs(10);
		if( 5000 < nloops++)
		{
			throw SpotBad("timeout waiting for image ready ");
		}
	}
#else

	{
		MMThreadGuard guard(imageReadyFlagLock_s);

      // rough estimate of milliseconds to wait for the image (extra 10 s)
      double maxWait = ExposureTime() + approxTransferTime + 10000.0;
      while (pMMCamera_->GetCurrentMMTime().getMsec() < time0 + maxWait && !imageReady_s)
      {
			CDeviceUtils::SleepMs(10);
		}
      if (!imageReady_s)
      {
         std::ostringstream stringStreamMessage;
         double elapsed = pMMCamera_->GetCurrentMMTime().getMsec() - time0;
         stringStreamMessage << " invalid acquistion sequence - waited " << (float)elapsed << " ms for image ready";
         throw SpotBad(stringStreamMessage.str().c_str());
      }

		double elapsed = pMMCamera_->GetCurrentMMTime().getMsec() - time0;
		std::ostringstream  mezzz;
		mezzz << "waited " << elapsed << " ms for next image\n";
		CodeUtility::DebugOutput( mezzz.str().c_str());

#endif


	SpotAPI(SpotGetValue)(SPOT_ACQUIREDIMAGESIZE, sValue);
   nImageWidth_ = sValue[0];
   nImageHeight_ = sValue[1];


   SpotAPI(SpotGetValue)(SPOT_BITDEPTH, &sValue);
	bitDepthOfADC_  = sValue[0];


	// todo - SPOT_BITDEPTH can be, for example, 14, but camera still returns RGB components (i.e 42 bit color images)
	switch( bitDepthOfADC_)
	{
	case 8:
		nImageBitDepth_ = 8;
		break;
	case 10:
	case 12:
	case 14:
	case 16:
		nImageBitDepth_ = 16;
		break;
	case 24:
		nImageBitDepth_ = 24;
		break;
	case 36:
	case 30:
	case 42:
		nImageBitDepth_ = 48;
		break;
	default:
		{
			std::ostringstream messs;
			messs << "unsupported ADC bit depth: " << bitDepthOfADC_;		
			throw SpotBad(messs.str());
		}
	}
		
		
#ifdef __APPLE__
        int nchars = nImageBitDepth_ / 8;
        if (3==nchars) nchars = 4;
        unsigned long rowBytes = nImageWidth_ * nchars;
#endif



   switch (nImageBitDepth_)
   {
   case 8:
      nRowBytes = ((nImageWidth_ * 8l + 31) / 32) * 4l;
     // nPaletteSize = 256 * sizeof(RGBQUAD);
      break;
   case 10:
	case 12:
	case 14:
	case 16:
      nRowBytes = ((nImageWidth_ * 16l + 31) / 32) * 4l;
      //nPaletteSize = 256 * sizeof(RGBQUAD);
      break;
   case 24:
      nRowBytes = ((nImageWidth_ * 32l + 31) / 32) * 4l;
     // nPaletteSize = 0;
      break;
	case 48:
		//todo - verify this calculation:
		nRowBytes = ((nImageWidth_ * 48l + 31) / 32) * 4l;
     // nPaletteSize = 0;
      break;
   }



	int currentBufferSize = nRowBytes * nImageHeight_ ;//+ sizeof(BITMAPINFOHEADER) + nPaletteSize;
	if( currentBufferSize != nBufSize_)
	{
		nBufSize_ = currentBufferSize;
	   if( NULL!= voidStarBuffer_) free( voidStarBuffer_);
		// todo - verify malloc is slightly faster than new xyzzy[size]
		voidStarBuffer_ = malloc( nBufSize_);
	}

	if(NULL!=voidStarBuffer_) 	memset(voidStarBuffer_,0,nBufSize_);


//
//#ifdef USE_SPOTWAITFORSTATUSCHANGE
//
//#endif

	spotcode = SpotAPI(SpotRetrieveSequentialImage)(voidStarBuffer_
#ifdef __APPLE__
//
		,rowBytes
#else
// 
#endif
	);

#ifndef USE_SPOTWAITFORSTATUSCHANGE
	imageReady_s = false;


	} // guard(imageReadyFlagLock_s)
#endif

	suc = ProcessSpotCode(spotcode, message);
	if( suc)
	{
#ifdef USE_SPOTWAITFORSTATUSCHANGE
		if( SPOT_RUNNING == spotcode)
		{
			nloops = 0;

			while( !WaitForStatusChanged(SPOT_STATUSIDLE ))
			{
				
				CDeviceUtils::SleepMs(10);
				if( 5000 < nloops++)
				{
					throw SpotBad("timeout waiting for transfer ");
				}
			}
		}
#endif
		
		// todo
		// 1. done
		// 2. pull this out into a generic utility to take point to BMP and return pointer to a microManager image


		imwidth = nImageWidth_;

		imheight = nImageHeight_;
		bytesppixel = (char)(nImageBitDepth_/8);
                int bytes;

#ifdef __APPLE__
		bytes = nRowBytes;

#else
		// bytes per row  - aligned to 4 byte word
		bytes = (nImageBitDepth_*nImageWidth_)>>3;
		while(bytes%4) bytes++;
#endif

		// currently only support 8 bit and 24 bit images
		if((nImageBitDepth_ != 8 ) && (nImageBitDepth_ != 16) && (nImageBitDepth_ != 24))
		{
			std::ostringstream emesss;
			emesss << "unsupported image depth: " << nImageBitDepth_;
			throw SpotBad( emesss.str());
		}


		//timer1.Reset();
		if( imheight*bytes != sizeofbuf_)
		{
			if(NULL!=pbuf_)free(pbuf_);
			sizeofbuf_ = 0;
			pbuf_ = (char *) malloc( imheight*bytes);
			if (NULL!=pbuf_) sizeofbuf_ = imheight*bytes;
		}
		memset(pbuf_, 0, imheight*bytes);

		// EF
		// Find out if the 24bit color image in the camera buffer is RGB or RGBA
		// MM expects BGRA
		if ( nImageBitDepth_ == 24 ) {					

			bytesppixel = 4; //EF: VERY CRITICAL, otherwise the A channel is not taken into account below
						
			// Find the 24bit byte-order with SPOT_24BPPIMAGEBUFFERFORMAT
			// Contrary to the docs (at least on Mac):
			// - This value is read-only, SpotSetValue results into bus error
			// - The value returned is currently 0, not the SPOT_24BPP... id's.
			sValue[0] = 0;
			SpotAPI(SpotGetValue)(SPOT_24BPPIMAGEBUFFERFORMAT, &sValue);
			switch (sValue[0]) {
				case SPOT_24BPPIMAGEBUFFERFORMATBGR: // Win default
				case SPOT_24BPPIMAGEBUFFERFORMATBGRA:
					colorOrder = color_BGRA;
					break;
				case SPOT_24BPPIMAGEBUFFERFORMATARGB: // Mac default (alpha is first!!!)
					colorOrder = color_ARGB;
					break;
				case 0:
					// As of Spot 4.6.21, SPOT_24BPPIMAGEBUFFERFORMAT returns always 0
					// -> hack to assign the default color order to the one defined in the API docs
					// if this ever gets fixed, this code should still work 
					//
					// Possiblity: Find the RGB arrangement and deduce from there?
					// char rgbColorOrder[4]; memset(rgbColorOrder,0, 4*sizeof(rbgColorOrder[0]));
					// SpotAPI(SpotGetValue)(SPOT_COLORORDER, rgbColorOrder);
					// std::cerr << "SPOT_COLORORDER: " << rgbColorOrder << std::endl;
					// 
					// For the time being, hard code it depending on architecture
#ifdef __APPLE__
					colorOrder = color_ARGB;
#else
					colorOrder = color_BGRA;
#endif
					break;
				default:
					std::ostringstream emesss;
					emesss << "unsupported color order: " << nImageBitDepth_;
					throw SpotBad( emesss.str());
					break;
						   
			}
						   
		}
		else 
		{
			colorOrder = color_NOCHANGE;
		}
		
		
		// EF rearrange outside the loop
		//GetImage returns a compact array, so keep it that way
		char* praster = (char *)voidStarBuffer_;
		char* poutput;
		short pmuliplier;
		
		// EF: query image orientation and flip if necessary
		short imageOrient; 
		SpotAPI(SpotGetValue)(SPOT_IMAGEORIENTATION, &sValue);
		imageOrient = sValue[0];
		if ( imageOrient == 1 ) 
		{
			// no top-bottom flip (usually Mac)
			poutput = (char *)pbuf_;
			pmuliplier = 1;
		} 
		else if  ( imageOrient == 1 )
		{			
			// flip top & bottom (usually Win)
			//GetImage returns a compact array, so keep it that way
			poutput = (char *)pbuf_ + (imheight-1)*(imwidth*bytesppixel);
			pmuliplier = -1;
		} 
		else 
		{
			std::ostringstream messs;
			messs << "unsupported image orientation: " << imageOrient;		
			throw SpotBad(messs.str());
		}
	
			
		if ( colorOrder[0] >= 0 ) 
		{
			for(unsigned int yoff = 0; yoff < imheight; ++yoff)
			{
				for (unsigned int xoff = 0; xoff < imwidth*bytesppixel; xoff += bytesppixel) {
					// poutput[xoff] = 255-praster[xoff+colorOrder[0]]; // for Spot Idea USB camera???
					poutput[xoff] = praster[xoff+colorOrder[0]];
					poutput[xoff+1] = praster[xoff+colorOrder[1]];
					poutput[xoff+2] = praster[xoff+colorOrder[2]];
					poutput[xoff+3] = praster[xoff+colorOrder[3]];
				}
				poutput += imwidth*bytesppixel*pmuliplier;
				praster += bytes;
			}
		} else {
			for(unsigned int yoff = 0; yoff < imheight; ++yoff)
			{
				// no rearrangement of color bytes or monochrome
				memcpy(poutput, praster, imwidth*bytesppixel);
				poutput += imwidth*bytesppixel;
				praster += bytes;
			}
		}
	}
	else
	{
		throw SpotBad(message);
	}

	if( 0 < message.length())
		CodeUtility::DebugOutput( message);

	return (char*)pbuf_;

}


std::vector<short> SpotDevice::BinSizes()
{
	//MMThreadGuard libGuard(libraryLock_s);
	short binsz[100];
	std::vector<short> retvals;

	std::string message;
	bool suc = ProcessSpotCode(SpotAPI(SpotGetValue)(SPOT_BINSIZES,binsz), message);
	if (suc)
	{
		for(int ii=1; ii<= binsz[0]; ++ii)
		{
			retvals.push_back( binsz[ii] );
		}
	}
	else
	{
		retvals.push_back(1);
	}
	return retvals;

}

void WINAPI  SpotDevice::CalledBackfromDriver(int iStatus, long lInfo, DWORD/* dwUserData*/)
{
	bool expComplete = false;
	long* pInfoValue = NULL;

	bool waitForSPOT_STATUSIMAGEREADRED = (g_Device->DoesMultiShotColor() && (g_Device->BitDepth()<17));
	bool waitForSPOT_STATUSIMAGEREADBLUE = (g_Device->DoesMultiShotColor() && (16<g_Device->BitDepth()));

	const char *pszStatusMsg=NULL;
	double eventTime = 0.;
	if(g_Camera && ( 0.< g_Camera->SnapImageStartTime()) ) 
		eventTime = g_Camera->GetCurrentMMTime().getMsec() - g_Camera->SnapImageStartTime();
	// Display status
	switch (iStatus)
	{
	case SPOT_STATUSLIVEIMAGEREADY:

		
		pszStatusMsg = "Live Image Ready";
		break;
	case SPOT_STATUSSEQIMAGEREADY:
		{
			MMThreadGuard guard(imageReadyFlagLock_s);
			imageReady_s = true;
		}
		//CodeUtility::DebugOutput(" ready signal recieved !\n");
		{
			MMThreadGuard modeGuard( sequenceModeLock_s);
			if(sequenceMode_s)
			{
				if( g_Camera)
					g_Camera->CallBackToCamera();
			}
		}

		pszStatusMsg = "SPOT_STATUSSEQIMAGEREADY";
		// We can retrieve the image's timestamp here, if we wish
		// SpotGetExposureTimestamp(pstTimestamp);

		break;
	case SPOT_STATUSIDLE:
		pInfoValue = &lInfo;
		pszStatusMsg = "Idle, status = ";
		break;
	case SPOT_STATUSDRVNOTINIT:
		
		pszStatusMsg = "Driver not Initialized";
		break;
	case SPOT_STATUSSHUTTEROPENRED:
		pszStatusMsg = "Exposing (red)";
		break;
	case SPOT_STATUSSHUTTEROPENGREEN:
		pszStatusMsg = "Exposing (green)";
		break;
	case SPOT_STATUSSHUTTEROPENBLUE:
		
		pszStatusMsg = "Exposing (blue)";
		break;
	case SPOT_STATUSSHUTTEROPENCLEAR:
		
		pszStatusMsg = "Exposing (clear)";
		break;
	case SPOT_STATUSSHUTTEROPEN:
		
		pszStatusMsg = "Exposing ";
		break;

	// todo for multi-shot cameras need to wait for all 3 or all 4 of these events, doc not very clear
	case SPOT_STATUSIMAGEREADRED:
				
		if(waitForSPOT_STATUSIMAGEREADRED)
			expComplete = true;

		pszStatusMsg = "Reading Image (red)";
		break;
	case SPOT_STATUSIMAGEREADGREEN:
		
		pszStatusMsg = "Reading Image (green)";
		break;
	case SPOT_STATUSIMAGEREADBLUE:		
		if(waitForSPOT_STATUSIMAGEREADBLUE)
			expComplete = true;

		pszStatusMsg = "Reading Image (blue)";
		break;
	case SPOT_STATUSIMAGEREADCLEAR:
		pszStatusMsg = "Reading Image (clear)";
		break;
	case SPOT_STATUSIMAGEREAD:
		{
			//std::ostringstream o;
			//o << "line " << lInfo << std::endl;
			//CodeUtility::DebugOutput(o.str());
		}
		expComplete = true;
		pszStatusMsg = "Reading Image";

		break;
	case SPOT_STATUSCOMPEXP:
		
		pszStatusMsg = "Computing Exposure";

		break;
	case SPOT_STATUSCOMPWHITEBAL:
		
		pszStatusMsg = "Computing White Balance";
		break;
	case SPOT_STATUSGETIMAGE:
		if(g_Camera)
			g_Camera->AutoEposureCalculationDone(true);
		pInfoValue = &lInfo;
		pszStatusMsg = "acq count: ";
		break;
	case SPOT_STATUSSEQIMAGEWAITING:
		
		pszStatusMsg = "Waiting to Acquire Next Image";
		break;
	case SPOT_STATUSIMAGEPROCESSING:
		
		pszStatusMsg = "Processing Image";
		break;
	case SPOT_STATUSWAITINGFORTRIGGER:
		
		pszStatusMsg = "Waiting for Trigger";
		break;
	case SPOT_STATUSWAITINGFORBLOCKLIGHT:
		
		pszStatusMsg = "Block Light";
		break;
	case SPOT_STATUSWAITINGFORMOVETOBKGD:
		
		pszStatusMsg = "Move to Background";
		break;
	case SPOT_STATUSTTLOUTPUTDELAY:
		
		pszStatusMsg = "TTL Output Delay";
		break;
	case SPOT_STATUSEXTERNALTRIGGERDELAY:
		
		pszStatusMsg = "External Trigger Delay";
		break;
	case SPOT_STATUSWAITINGFORCOLORFILTER:
		
		pszStatusMsg = "Waiting for Color Filter";
		break;
	case SPOT_STATUSABORTED:
		pInfoValue = &lInfo;
		pszStatusMsg = "Aborted, status = ";
		break;
	case SPOT_STATUSERROR:
		pInfoValue = &lInfo;
		pszStatusMsg = "Error, status = ";
		break;

	}
	if(expComplete)
		g_Camera->ExposureComplete(true);

	if (pszStatusMsg) 
	{
		CodeUtility::DebugOutput( pszStatusMsg);
		std::ostringstream osss;
		if( NULL != pInfoValue)
			osss << *pInfoValue << " ";
		if( 0. < eventTime)
		{
			osss << " " << eventTime << " ";
			CodeUtility::DebugOutput( osss.str());
		}

		CodeUtility::DebugOutput("\n");
	}
}

bool SpotDevice::ProcessSpotCode(const int spotCode, std::string& message)
	{
		// warnings are all negative values
		bool success = (( SPOT_SUCCESS == spotCode ) || (spotCode < 0));
#ifdef USE_SPOTWAITFORSTATUSCHANGE
		if (SPOT_RUNNING == spotCode)
			success = true;
#endif
		std::ostringstream messs;
		message.clear();

		switch (spotCode)
		{
		case SPOT_SUCCESS:
			break;
		case SPOT_WARNUNSUPPCAMFEATURES:
			message = "The camera has capabilities not supported by this version of the driver";
			break;
		case SPOT_WARNINVALIDINPUTICC:
			message = "Missing or invalid input ICC profile";
			break;
		case SPOT_WARNINVALIDOUTPUTICC:
			message = "Missing or invalid output ICC profile";
			break;
		case SPOT_ERROUTOFMEMORY:
			message = "Out of Memory";
			break;
		case SPOT_ERREXPTOOSHORT:
			message = "The exposure is too short";
			break;
		case SPOT_ERREXPTOOLONG :
			message = "The exposure is too long";
			break;
		case SPOT_ERRNOCAMERARESP:
			message = "The camera is not responding";
			break;
		case SPOT_ERRVALOUTOFRANGE:
			message = "Parameter out of Range";
			break;
		case SPOT_ERRINVALIDPARAM:
			message = "Invalid Parameter";
			break;
		case SPOT_ERRDRVNOTINIT:
			message = "The SpotCam driver has not been initialized";
			break;
		case SPOT_ERRREGISTRYQUERY:
		case SPOT_ERRREGISTRYSET:
			message = "Registry access error";
			break;
		case SPOT_ERRDEVDRVLOAD:
			message = "Error unloading device driver";
			break;
		case SPOT_ERRCAMERAERROR:
			message = "Camera Error";
			break;
		case SPOT_ERRDRVALREADYINIT:
			message = "The SpotCam driver has already been initialized";
			break;
		case SPOT_ERRDMASETUP:
			message = "Error setting up DMA buffers";
			break;
		case SPOT_ERRREADCAMINFO:
			message = "Error reading camera information";
			break;
		case SPOT_ERRNOTCAPABLE:
			message = "The camera is not capable of the requested action";
			break;
		case SPOT_ERRCOLORFILTERNOTIN:
			message = "The color filter is not in the Color position";
			break;
		case SPOT_ERRCOLORFILTERNOTOUT:
			message = "The color filter is not in the B/W position";
			break;
		case SPOT_ERRCAMERABUSY:
			message = "The camera is currently busy";
			break;
		case SPOT_ERRCAMERANOTSUPPORTED:
			message = "The camera is not supported";
			break;
		case SPOT_ERRFILEOPEN:
			message = "Error opening file";
			break;
		case SPOT_ERRFLATFLDINCOMPATIBLE:
			message = "The flatfield is incompatible with the current camera/settings";
			break;
		case SPOT_ERRNODEVICESFOUND:
			message = "No devices found";
			break;
		case SPOT_ERRBRIGHTNESSCHANGED:
			message = "The brightness changed during exposure computation";
			break;
		case SPOT_ERRCAMANDCARDINCOMPATIBLE:
			message = "The camera is incompatible with the interface card";
			break;
		case SPOT_ERRBIASFRMINCOMPATIBLE:
			message = "The bias frame is incompatible with the current camera/settings";
			break;
		case SPOT_ERRBKGDIMAGEINCOMPATIBLE:
			message = "The background image is incompatible with the current camera/settings";
			break;
		case SPOT_ERRBKGDTOOBRIGHT:
			message = "The background is too bright";
			break;
		case SPOT_ERRINVALIDFILE:
			message = "Invalid file";
			break;
		case SPOT_ERRINSUF1394ISOCBANDWIDTH:
			message = "The required 1394 isochronous bandwidth is unavailable";
			break;
		case SPOT_ERRINSUF1394ISOCRESOURCES:
			message = "There are insufficient 1394 isochronous resources available";
			break;
		case SPOT_ERRNO1394ISOCCHANNEL:
			message = "No 1394 isochronous channel is available";
			break;
		default:
			{
				messs << "Spot status is " << spotCode;
				message = messs.str();
			}

		}
		return success;
	};



void SpotDevice::StopDevice(void)
{
	long info;
	int code;
	code = SpotAPI(SpotQueryStatus)(TRUE, &info);
}

// a string which shows pixel X size by pixel Y size in units (nm)
std::string SpotDevice::PixelSize(void)
{
	//MMThreadGuard libGuard(libraryLock_s);
	std::ostringstream omesss;
	long values[2];
	values[0] = 0;
	values[1] = 0;

	SpotAPI(SpotGetValue)(SPOT_PIXELSIZE, values);

	omesss << values[0] << "x" << values[1] << "nm";

	return omesss.str();
}

// get the bit depths the camera can capture - less than 24 means monochrome acq

std::vector<short> SpotDevice::PossibleBitDepths(void)
{
	//MMThreadGuard libGuard(libraryLock_s);
	std::vector<short> ret;
	short vals[100];
	std::string message;

	bool suc = ProcessSpotCode(SpotAPI(SpotGetValue)(SPOT_BITDEPTHS,vals), message);
	if (suc)
	{
		for (int ii = 1; ii <= vals[0]; ++ii)
		{
			ret.push_back(vals[ii]);
			if (99 == ii) break;
		}
	}
	else
	{
		throw SpotBad( message );
	}

	return ret;

}

//
short SpotDevice::BitDepth(void)
{
	////MMThreadGuard libGuard(libraryLock_s);
	short value;
	std::string message;

	if( !ProcessSpotCode(SpotAPI(SpotGetValue)(SPOT_BITDEPTH, &value), message))
	{
		throw SpotBad(message);
	}
	else if( 0 < message.length())
	{
		CodeUtility::DebugOutput( message.c_str());
	}
	return value;
}



void SpotDevice::BitDepth(short value)
{
	//MMThreadGuard libGuard(libraryLock_s);
	std::string message;

	if( !ProcessSpotCode(SpotAPI(SpotSetValue)(SPOT_BITDEPTH, &value), message))
	{
		throw SpotBad(message);
	}
	else if( 0 < message.length())
	{
		CodeUtility::DebugOutput( message.c_str());
	}
	
}



// possible gain values for 8, 12, or 16 bit acquisition

std::vector<short>  SpotDevice::PossibleIntegerGains( const short bitdepth)
{
	//MMThreadGuard libGuard(libraryLock_s);
	std::vector<short> ret;
	short vals[100];
	std::string message;
	short parameter = 0;
	switch(bitdepth)
	{
	case 8:
		parameter = SPOT_GAINVALS8;
		break;
	case 12:
	case 14:
	case 16:
		parameter = SPOT_GAINVALS16;
		break;
	}

	if ( 0 < parameter)
	{
		bool suc = ProcessSpotCode(SpotAPI(SpotGetValue)(parameter,vals), message);
		if (suc)
		{
			for (int ii = 1; ii <= vals[0]; ++ii)
			{
				ret.push_back(vals[ii]);
				if (99 == ii) break;
			}
		}
		else
		{
			CodeUtility::DebugOutput( message.c_str());
		}
	}
	return ret;
}

void SpotDevice::ExposureComputationImageSetting( const ExposureComputationImageType v)
{
	//MMThreadGuard libGuard(libraryLock_s);
	short svalue;
	std::string message;
	switch(v)
	{
		case BrightField:
			svalue = SPOT_IMAGEBRIGHTFLD;
			break;
		case DarkField:
			svalue = SPOT_IMAGEDARKFLD;
			break;
		default:
			svalue = 0;
			break;
	}

	if( !ProcessSpotCode(SpotAPI(SpotSetValue)(SPOT_IMAGETYPE, &svalue), message  ))
	{
      throw SpotBad(message.c_str());

   }
	else if( 0 < message.length())
		this->pMMCamera_->LogMessage(message);


}
ExposureComputationImageType SpotDevice::ExposureComputationImageSetting(void)
{
	//MMThreadGuard libGuard(libraryLock_s);
	ExposureComputationImageType ret;

	short svalue;
	std::string message;

	if( !ProcessSpotCode(SpotAPI(SpotGetValue)(SPOT_IMAGETYPE, &svalue), message  ))
	{
      throw SpotBad(message.c_str());

   }
	else 
	{
		switch(svalue)
		{
		case SPOT_IMAGEBRIGHTFLD:
			ret = BrightField;
			break;
		case SPOT_IMAGEDARKFLD:
			ret = DarkField;
			break;
		default:
			ret = (ExposureComputationImageType)0;
			break;
		}
	}
	
	if( 0 < message.length())
	{
		this->pMMCamera_->LogMessage(message);
	}

	return ret;
}

double SpotDevice::ActualGain(void)
{
	//MMThreadGuard libGuard(libraryLock_s);
	float ag = 0.;
#ifdef _WINDOWS
	short gain = Gain();
	SpotAPI(SpotGetActualGain)(0, gain, &ag);
#endif // weird - looks like this call not defined on Unix
	return ag;
}

double SpotDevice::SensorTemperature(void)
{
	//MMThreadGuard libGuard(libraryLock_s);
	short deciDegrees = 0;
	BOOL newValue;
	SpotAPI(SpotGetSensorCurrentTemperature)(& deciDegrees, &newValue);
	return 0.1 * (double)deciDegrees;

}

std::pair<double,double> SpotDevice::RegulatedTemperatureLimits(void)
{
	//MMThreadGuard libGuard(libraryLock_s);
	std::pair<double, double> returnValue;
	short values[2]; // lower, upper
	// retrieve limits in deci-degrees
	SpotAPI(SpotGetValue)(SPOT_REGULATEDTEMPERATURELIMITS, values);
	//... convert to degrees C
	returnValue.first = 0.1*values[0];
	returnValue.second = 0.1*values[1];
	return returnValue;
	
}

double SpotDevice::SensorTemperatureSetpoint(void)
{
	//MMThreadGuard libGuard(libraryLock_s);
	short deciDegrees = 0;
	SpotAPI(SpotGetValue)(SPOT_REGULATEDTEMPERATURE, &deciDegrees);
	return 0.1*deciDegrees;
}
void SpotDevice::SensorTemperatureSetpoint(const double value)
{
	//MMThreadGuard libGuard(libraryLock_s);
	short deciDegrees = (short)CodeUtility::nint( 10.* value);
	SpotAPI(SpotSetValue)(SPOT_REGULATEDTEMPERATURE, &deciDegrees);
}

TriggerModes SpotDevice::TriggerMode(void)
{
	//MMThreadGuard libGuard(libraryLock_s);
	short svalue = 0;

	SpotAPI(SpotGetValue)(SPOT_EXTERNALTRIGGERMODE, &svalue);
	return static_cast<TriggerModes>(svalue);
}


void SpotDevice::TriggerMode( const TriggerModes value)
{
	//MMThreadGuard libGuard(libraryLock_s);
	short svalue = static_cast<short>(value);
	SpotAPI(SpotSetValue)(SPOT_EXTERNALTRIGGERMODE, &svalue);
}


bool SpotDevice::ChipDefectCorrection( void) // get current setting
{
	//MMThreadGuard libGuard(libraryLock_s);
	BOOL value;
   SpotAPI(SpotGetValue)(SPOT_CORRECTCHIPDEFECTS, &value);
	return ( FALSE != value);
}

void SpotDevice::ChipDefectCorrection(const bool v) // set
{
	//MMThreadGuard libGuard(libraryLock_s);
	BOOL value = v;
   SpotAPI(SpotSetValue)(SPOT_CORRECTCHIPDEFECTS, &value);
}

// Sensor Clear Modes for Use with SPOT_SENSORCLEARMODE and SPOT_SENSORCLEARMODES
//#define SPOT_SENSORCLEARMODE_CONTINUOUS    0x01   // Continuously clear sensor
//#define SPOT_SENSORCLEARMODE_PREEMPTABLE   0x02   // Allow exposures to pre-emp sensor clearing
//#define SPOT_SENSORCLEARMODE_NEVER         0x04   // Never clear sensor
//enum ClearingModes { Continuous = 1, Preemptable = 3} .., Never = 4};

std::vector<ClearingModes> SpotDevice::PossibleClearingModes(void)
{
	//MMThreadGuard libGuard(libraryLock_s);
	std::vector<ClearingModes> ret;
	unsigned long value[100];
	SpotAPI(SpotGetValue)(SPOT_SENSORCLEARMODES, value);

	for(unsigned long int ii=1; ii<= value[0]; ++ii)
	{
		ret.push_back( static_cast<ClearingModes>(value[ii]) );
	}


	return ret;

}

ClearingModes SpotDevice::ClearingMode(void)
{
	//MMThreadGuard libGuard(libraryLock_s);
	long value;

	SpotAPI(SpotGetValue)(SPOT_CLEARMODE, &value);
	return static_cast<ClearingModes>(value);

}

void SpotDevice::ClearingMode(ClearingModes value)
{
	//MMThreadGuard libGuard(libraryLock_s);
	DWORD dvalue = value;
	SpotAPI(SpotSetValue)(SPOT_CLEARMODE, &dvalue);
}

short SpotDevice::NoiseFilterPercent(void)
{
	//MMThreadGuard libGuard(libraryLock_s);
	short value;            
   SpotAPI(SpotSetValue)(SPOT_NOISEFILTERTHRESPCT, &value);
   return value;
}

void SpotDevice::NoiseFilterPercent(short value)
{
	//MMThreadGuard libGuard(libraryLock_s);
	// Enable noise filtering with specified threshold
   SpotAPI(SpotSetValue)(SPOT_NOISEFILTERTHRESPCT, &value);
}


std::string SpotDevice::ColorOrder(void)
{

	char order[4];
	memset(order, 0, 4*sizeof(order[0]));
	SpotAPI(SpotGetValue)(   SPOT_COLORORDER, order);
	std::string retValue(order);
	return retValue;
}

SpotDevice::~SpotDevice(void)
{
	//MMThreadGuard libGuard(libraryLock_s);
	StopDevice();
	if(NULL!=pbuf_)
		free(pbuf_);
	pbuf_=NULL;
#ifdef _WINDOWS
	if(pSpotExit)
#endif
		SpotAPI(SpotExit)();
#ifdef _WINDOWS
	if (hSpotCamDLL_) 
		FreeLibrary(hSpotCamDLL_);
#endif
}





