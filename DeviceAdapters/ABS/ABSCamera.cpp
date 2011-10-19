#include "ABSCamera.h"
#include <cstdio>
#include <string>
#include <math.h>
#include <sstream>
#include <algorithm>
#include <iostream>
#include "../../MMCore/Error.h"
#include "stringtools.h"
using namespace std;



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
// constants for naming pixel types (allowed values of the "PixelType" property)
const char* g_PixelType_8bit = "8bit";
const char* g_PixelType_16bit = "16bit";
const char* g_PixelType_32bitRGB = "32bitRGB";
const char* g_PixelType_64bitRGB = "64bitRGB";
const char* g_PixelType_32bit = "32bit";  // floating point greyscale


const char* g_ScanMode_Continuous  = "Continuous";
const char* g_ScanMode_TriggeredSW = "Triggered SW";
const char* g_ScanMode_TriggeredHW = "Triggered HW";


// ------------------------------ Macros --------------------------------------
//
// return the error code of the function instead of Successfull or Failed!!!
#define GET_RC(_func, _dev)  ((!_func) ? CamUSB_GetLastError((u08)_dev) : retOK)


///////////////////////////////////////////////////////////////////////////////
// CABSCamera implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~

volatile int CABSCamera::staticDeviceNo = NO_CAMERA_DEVICE;
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
   readoutUs_(0.0),
   scanMode_( g_ScanMode_TriggeredSW ),
   bitDepth_(8),
   roiX_(0),
   roiY_(0),
   sequenceStartTime_(0),
	binSize_(1),
	cameraCCDXSize_(512),
	cameraCCDYSize_(512),
   nComponents_(1),
   pDemoResourceLock_(0),
   triggerDevice_(""),
	dropPixels_(false),
	saturatePixels_(false),
	fractionOfPixelsToDropOrSaturate_(0.002)
	,deviceNo_( -1 )
	,colorCamera_( false ) 
{
   memset(testProperty_,0,sizeof(testProperty_));

   // call the base class method to set-up default error codes/messages
   InitializeDefaultErrorMessages();
   readoutStartTime_ = GetCurrentMMTime();
   pDemoResourceLock_ = new MMThreadLock();
   thd_ = new CABSCameraSequenceThread(this);

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
* If this device used as intended within the Micro-Manager system,
* Shutdown() will be always called before the destructor. But in any case
* we need to make sure that all resources are properly released even if
* Shutdown() was not called.
*/
CABSCamera::~CABSCamera()
{
   StopSequenceAcquisition();
   delete thd_;
   delete pDemoResourceLock_;
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
   u08 platformID   = CPID_NONE;
	   
   // if id is set try to select the correct camera
   if ( 0 < cameraDeviceID().size() )
   {
		bool cameraFound = false;
	   // get list of available devices
	   CCameraList cCameraList;
	   getAvailableCameras( cCameraList );
	
	   // check if the selected camera is at the list
	   CCameraList::iterator iter = cCameraList.begin();
	   while ( iter != cCameraList.end() )
	   {
			if ( cameraDeviceID() == buildCameraDeviceID( iter->sVersion.dwSerialNumber, (const char*) iter->sVersion.szDeviceName ).c_str() ) 
			{			 
				serialNumber = iter->sVersion.dwSerialNumber;
				platformID	 = iter->sVersion.bPlatformID;
				cameraFound  = true;
				break;
			}
			iter++;
		}
		
		if ( false == cameraFound )
			return DEVICE_NOT_CONNECTED;
	}
	
	// open camera device
	u32 dwRC;
	int deviceNumber = accquireDeviceNumber();
	// open camera without reboot
	dwRC = GET_RC( CamUSB_InitCameraEx( deviceNumber, serialNumber, 0, 0, platformID ), deviceNumber );

	if ( retNO_FW_RUNNING == dwRC ) // open camera with reboot
		dwRC = GET_RC( CamUSB_InitCameraEx( deviceNumber, serialNumber, 1, 0, platformID ), deviceNumber );

	if ( FALSE == IsNoError( dwRC ) )
	{
		releaseDeviceNumber( deviceNumber );		
		return convertApiErrorCode( dwRC );
	}
	else // remember device number for later access
	{
		setDeviceNo( deviceNumber );
	}

	// camera device is now open do the right things

	// read camera information
	cameraVersion_.dwStructSize = sizeof( S_CAMERA_VERSION );
	dwRC = GET_RC( CamUSB_GetCameraVersion( &cameraVersion_, deviceNo() ), deviceNo() );
	if ( FALSE == IsNoError(dwRC) )
	{ nRet = convertApiErrorCode( dwRC ); goto Initialize_Done; }
	
	// disbale heartbeat
	u32 dwValue = 0;
	dwRC = GET_RC( CamUSB_HeartBeatCfg( 0x80000001, dwValue, deviceNo() ), deviceNo() );
	
	// basic setup
	// check if it is a color camera
	{
		// set default pixeltype
		CamUSB_SetPixelType( PIX_MONO8, deviceNo() );

		S_RESOLUTION_CAPS * resolutionCaps = 0;
		u32 rc = getCap( FUNC_RESOLUTION, (void* &) resolutionCaps );
		if ( IsNoError( rc) )
		{
			setColor( ((resolutionCaps->wSensorType & ST_COLOR) == ST_COLOR) );

			cameraCCDXSize_ = resolutionCaps->wVisibleSizeX;
			binSize_        = 1;
			cameraCCDYSize_ = resolutionCaps->wVisibleSizeY;			

			last16BitDepth  =  CDeviceUtils::ConvertToString( (long) resolutionCaps->wMaxBPP );

			CamUSB_SetCameraResolution(0, 0, resolutionCaps->wVisibleSizeX, resolutionCaps->wVisibleSizeY, 0, 0, 1, deviceNo() );
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
	nRet = CreateProperty(MM::g_Keyword_CameraName, string( (const char*)cameraVersion_.szDeviceName ).c_str() , MM::String, true);
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
	
   // Bit depth
   pAct = new CPropertyAction (this, &CABSCamera::OnBitDepth);
   nRet = CreateProperty("BitDepth", "8", MM::Integer, false, pAct);
   assert(nRet == DEVICE_OK);
	nRet = setAllowedBitDepth();
   if (nRet != DEVICE_OK)
      { goto Initialize_Done; }
  

   // camera exposure
	pAct = new CPropertyAction (this, &CABSCamera::OnExposure);
   nRet = CreateProperty(MM::g_Keyword_Exposure, "10.0", MM::Float, false, pAct);
   assert(nRet == DEVICE_OK);
	nRet = setAllowedExpsoure();
   if (nRet != DEVICE_OK)
      { goto Initialize_Done; }

   // scan mode
   pAct = new CPropertyAction (this, &CABSCamera::OnScanMode);
   nRet = CreateProperty("ScanMode", "Triggered SW", MM::String, false, pAct);
   assert(nRet == DEVICE_OK);
   AddAllowedValue("ScanMode", g_ScanMode_Continuous   );
   AddAllowedValue("ScanMode", g_ScanMode_TriggeredSW );
   AddAllowedValue("ScanMode", g_ScanMode_TriggeredHW );

   // camera gain
	pAct = new CPropertyAction (this, &CABSCamera::OnGain);
   nRet = CreateProperty(MM::g_Keyword_Gain, "1.0", MM::Float, false, pAct);
   assert(nRet == DEVICE_OK);
	nRet = setAllowedGain();
   if (nRet != DEVICE_OK)
      { goto Initialize_Done; }

	// camera temperature	
	{
		temperatureUnit_  =  1.0;
		temperatureIndex_ = -1;

		S_TEMPERATURE_CAPS * temperatureCaps = 0;
		u32 rc = getCap( FUNC_TEMPERATURE, (void* &) temperatureCaps );
		if ( IsNoError( rc) )
		{
			if ( temperatureCaps->dwSensors > 0 )
			{
				temperatureUnit_	= temperatureCaps->sSensor[0].wUnit;
				temperatureIndex_ = 0;

				pAct = new CPropertyAction (this, &CABSCamera::OnTemperature);
				nRet = CreateProperty(MM::g_Keyword_CCDTemperature, "20.0 °C", MM::String, true, pAct);
				assert(nRet == DEVICE_OK);							
			}
		}

		if (temperatureCaps) 
			delete [] temperatureCaps;
	}



	/*
   // camera offset
   nRet = CreateProperty(MM::g_Keyword_Offset, "0", MM::Integer, false);
   assert(nRet == DEVICE_OK);

   // readout time
   pAct = new CPropertyAction (this, &CABSCamera::OnReadoutTime);
   nRet = CreateProperty(MM::g_Keyword_ReadoutTime, "0", MM::Float, false, pAct);
   assert(nRet == DEVICE_OK);

   // CCD size of the camera we are modeling
   pAct = new CPropertyAction (this, &CABSCamera::OnCameraCCDXSize);
   CreateProperty("OnCameraCCDXSize", "512", MM::Integer, false, pAct);
   pAct = new CPropertyAction (this, &CABSCamera::OnCameraCCDYSize);
   CreateProperty("OnCameraCCDYSize", "512", MM::Integer, false, pAct);

   // Trigger device
   pAct = new CPropertyAction (this, &CABSCamera::OnTriggerDevice);
   CreateProperty("TriggerDevice","", MM::String, false, pAct);

   pAct = new CPropertyAction (this, &CABSCamera::OnDropPixels);
	CreateProperty("DropPixels", "0", MM::Integer, false, pAct);
   AddAllowedValue("DropPixels", "0");
   AddAllowedValue("DropPixels", "1");

	pAct = new CPropertyAction (this, &CABSCamera::OnSaturatePixels);
	CreateProperty("SaturatePixels", "0", MM::Integer, false, pAct);
   AddAllowedValue("SaturatePixels", "0");
   AddAllowedValue("SaturatePixels", "1");

   pAct = new CPropertyAction (this, &CABSCamera::OnFractionOfPixelsToDropOrSaturate);
	CreateProperty("FractionOfPixelsToDropOrSaturate", "0.002", MM::Float, false, pAct);
	SetPropertyLimits("FractionOfPixelsToDropOrSaturate", 0., 0.1);

*/

   // synchronize all properties
   // --------------------------
   nRet = UpdateStatus();
   if (nRet != DEVICE_OK)
      { goto Initialize_Done; };


   // setup the buffer
   // ----------------
   nRet = ResizeImageBuffer();
   if (nRet != DEVICE_OK)
      { goto Initialize_Done; }

#ifdef TESTRESOURCELOCKING
   TestResourceLocking(true);
   LogMessage("TestResourceLocking OK",true);
#endif

  
	setInitialized( true );

Initialize_Done:
	if (nRet != DEVICE_OK)
	{
		int deviceNumber = deviceNo();
		setDeviceNo( NO_CAMERA_DEVICE );

		if ( NO_CAMERA_DEVICE != deviceNumber  )
		{
			CamUSB_FreeCamera( deviceNumber );
			releaseDeviceNumber( deviceNumber );		
		}
	}
	else
	{
		// initialize image buffer
		GenerateEmptyImage(img_);		
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
		u32 dwRC;
		dwRC = GET_RC( CamUSB_FreeCamera( (u08) deviceNo() ), deviceNo() );		
		setDeviceNo( NO_CAMERA_DEVICE );
		releaseDeviceNumber( deviceNo() );
	}
	
	setInitialized( false );   
   return DEVICE_OK;
}

/**
* Performs exposure and grabs a single image.
* This function should block during the actual exposure and return immediately afterwards 
* (i.e., before readout).  This behavior is needed for proper synchronization with the shutter.
* Required by the MM::Camera API.
*/
int CABSCamera::SnapImage()
{   
	static int callCounter = 0;
	++callCounter;

   MM::MMTime startTime = GetCurrentMMTime();
   
	u32 rc = getCameraImage( img_ );
   
   readoutStartTime_ = startTime;

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
   return img_.Width() * img_.Height() * GetImageBytesPerPixel();
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
   if (xSize == 0 && ySize == 0)
   {
      // effectively clear ROI
      ResizeImageBuffer();
      roiX_ = 0;
      roiY_ = 0;
   }
   else
   {
      // apply ROI
      img_.Resize(xSize, ySize);
      roiX_ = x;
      roiY_ = y;
   }
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
   ResizeImageBuffer();
   roiX_ = 0;
   roiY_ = 0;
      
   return DEVICE_OK;
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
		return convertApiErrorCode( dwRC );
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
		return convertApiErrorCode( dwRC );
}

int CABSCamera::setAllowedBitDepth( void )
{
	S_RESOLUTION_CAPS * resolutionCaps = 0;
	u32 dwRC = getCap( FUNC_RESOLUTION, (void* &) resolutionCaps );
   
	if ( retOK == dwRC )
	{
		vector<string> bitDepths;
		bitDepths.push_back("8");

		if (resolutionCaps->wMaxBPP >= 10)
			bitDepths.push_back("10");

		if (resolutionCaps->wMaxBPP >= 12)
			bitDepths.push_back("12");

		if (resolutionCaps->wMaxBPP >= 14)
			bitDepths.push_back("14");

		if (resolutionCaps->wMaxBPP >= 16)
			bitDepths.push_back("16");
		
		if (resolutionCaps) 
			delete [] resolutionCaps;

		LogMessage("Setting Allowed BitDepth settings", true);
		return SetAllowedValues("BitDepth", bitDepths);
	}
	else
		return convertApiErrorCode( dwRC );
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
		return SetPropertyLimits(MM::g_Keyword_Exposure, fMinExposure, fMaxExposure);
	}
	else
		return convertApiErrorCode( dwRC );
}

int CABSCamera::setAllowedGain( void )
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
		return SetPropertyLimits(MM::g_Keyword_Gain, fMinGain, fMaxGain);
	}
	else
		return convertApiErrorCode( rc );
}

/**
 * Required by the MM::Camera API
 * Please implement this yourself and do not rely on the base class implementation
 * The Base class implementation is deprecated and will be removed shortly
 */
int CABSCamera::StartSequenceAcquisition(double interval) 
{
   return StartSequenceAcquisition(LONG_MAX, interval, false);            
}

/**                                                                       
* Stop and wait for the Sequence thread finished                                   
*/                                                                        
int CABSCamera::StopSequenceAcquisition()                                     
{                                                                            
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
int CABSCamera::StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow)
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
int CABSCamera::InsertImage()
{
   MM::MMTime timeStamp = this->GetCurrentMMTime();
   char label[MM::MaxStrLength];
   this->GetLabel(label);
 
   // Important:  metadata about the image are generated here:
   Metadata md;
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

   int ret = GetCoreCallback()->InsertImage(this, pI, w, h, b, &md);
   if (!stopOnOverflow_ && ret == DEVICE_BUFFER_OVERFLOW)
   {
      // do not stop on overflow - just reset the buffer
      GetCoreCallback()->ClearImageBuffer(this);
      // don't process this same image again...
//      return GetCoreCallback()->InsertImage(this, pI, w, h, b, &md, false);
      return GetCoreCallback()->InsertImage(this, pI, w, h, b, md.Serialize().c_str(), false);
   } else
      return ret;
}

/*
 * Do actual capturing
 * Called from inside the thread  
 */
int CABSCamera::ThreadRun (void)
{
   int ret=DEVICE_ERR;
   
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

bool CABSCamera::IsCapturing() {
   return !thd_->IsStopped();
}

/*
 * called from the thread function before exit 
 */
void CABSCamera::OnThreadExiting() throw()
{
   try
   {
      LogMessage(g_Msg_SEQUENCE_ACQUISITION_THREAD_EXITING);
      GetCoreCallback()?GetCoreCallback()->AcqFinished(this,0):DEVICE_OK;
   }

   catch( CMMError& e){
      std::ostringstream oss;
      oss << g_Msg_EXCEPTION_IN_ON_THREAD_EXITING << " " << e.getMsg() << " " << e.getCode();
      LogMessage(oss.str().c_str(), false);
   }
   catch(...)
   {
      LogMessage(g_Msg_EXCEPTION_IN_ON_THREAD_EXITING, false);
   }
}


CABSCameraSequenceThread::CABSCameraSequenceThread(CABSCamera* pCam)
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

CABSCameraSequenceThread::~CABSCameraSequenceThread() {};

void CABSCameraSequenceThread::Stop() {
   MMThreadGuard(this->stopLock_);
   stop_=true;
}

void CABSCameraSequenceThread::Start(long numImages, double intervalMs)
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

bool CABSCameraSequenceThread::IsStopped(){
   MMThreadGuard(this->stopLock_);
   return stop_;
}

void CABSCameraSequenceThread::Suspend() {
   MMThreadGuard(this->suspendLock_);
   suspend_ = true;
}

bool CABSCameraSequenceThread::IsSuspended() {
   MMThreadGuard(this->suspendLock_);
   return suspend_;
}

void CABSCameraSequenceThread::Resume() {
   MMThreadGuard(this->suspendLock_);
   suspend_ = false;
}

int CABSCameraSequenceThread::svc(void) throw()
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

   }catch( CMMError& e){
      camera_->LogMessage(e.getMsg(), false);
      ret = e.getCode();
   }catch(...){
      camera_->LogMessage(g_Msg_EXCEPTION_IN_THREAD, false);
   }
   stop_=true;
   actualDuration_ = camera_->GetCurrentMMTime() - startTime_;
   camera_->OnThreadExiting();
   return ret;
}


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
         if(IsCapturing())
            return DEVICE_CAMERA_BUSY_ACQUIRING;

         // the user just set the new value for the property, so we have to
         // apply this value to the 'hardware'.
         long binFactor;
         pProp->Get(binFactor);
			if(binFactor > 0 && binFactor < 10)
			{
				img_.Resize(cameraCCDXSize_/binFactor, cameraCCDYSize_/binFactor);
				binSize_ = binFactor;
            std::ostringstream os;
            os << binSize_;
            OnPropertyChanged("Binning", os.str().c_str());
				ret=DEVICE_OK;
			}
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
         if(IsCapturing())
            return DEVICE_CAMERA_BUSY_ACQUIRING;

         string pixelType;
         pProp->Get(pixelType);
			u32 newPixelType = 0;
         if (pixelType.compare(g_PixelType_8bit) == 0)
         {
            nComponents_ = 1;
            img_.Resize(img_.Width(), img_.Height(), 1);
				if ( 8 != bitDepth_)
					SetProperty("BitDepth", "8");

				newPixelType = PIX_MONO8;
            ret=DEVICE_OK;
         }
         else if (pixelType.compare(g_PixelType_16bit) == 0)
         {
            nComponents_ = 1;				
            img_.Resize(img_.Width(), img_.Height(), 2);
				if ( 8 == bitDepth_)
					SetProperty("BitDepth", last16BitDepth.c_str() );

				switch (bitDepth_)
				{
				case 16: newPixelType = PIX_MONO16; break;
				case 14: newPixelType = PIX_MONO14; break;
				case 12: newPixelType = PIX_MONO12; break;
				case 10: 
				default: newPixelType = PIX_MONO10; break;
				}
            ret=DEVICE_OK;
         }
			else if ( pixelType.compare(g_PixelType_32bitRGB) == 0)
			{
            nComponents_ = 4;
            img_.Resize(img_.Width(), img_.Height(), 4);				
            
				if ( 8 != bitDepth_)
					SetProperty("BitDepth", "8"); 	
				
				newPixelType = PIX_BGRA8_PACKED;

				ret=DEVICE_OK;
			}
			else if ( pixelType.compare(g_PixelType_64bitRGB) == 0)
			{
            nComponents_ = 4;
            img_.Resize(img_.Width(), img_.Height(), 8);            
				if ( 8 == bitDepth_)
					SetProperty("BitDepth", last16BitDepth.c_str() ); 

				switch (bitDepth_)
				{
				//case 16: newPixelType = PIX_BGRA16_PACKED; break;
				case 14: newPixelType = PIX_BGRA14_PACKED; break;
				case 12: newPixelType = PIX_BGRA12_PACKED; break;
				case 10: 
				default: newPixelType = PIX_BGRA10_PACKED; break;
				}
				ret=DEVICE_OK;
			}
		   else
         {
            // on error switch to default pixel type
            nComponents_ = 1;
            img_.Resize(img_.Width(), img_.Height(), 1);
            pProp->Set(g_PixelType_8bit);				
            ret = ERR_UNKNOWN_MODE;
         }

			if ( DEVICE_OK == ret)
			{
				u32 rc = GET_RC( CamUSB_SetPixelType( newPixelType, deviceNo()), deviceNo() );
				ret = convertApiErrorCode( rc );
			}
      } break;
   case MM::BeforeGet:
      {
         long bytesPerPixel = GetImageBytesPerPixel();
         if (bytesPerPixel == 1)
         	pProp->Set(g_PixelType_8bit);
         else if (bytesPerPixel == 2)
         	pProp->Set(g_PixelType_16bit);
         else if (bytesPerPixel == 4)
				pProp->Set(g_PixelType_32bitRGB);                     
         else if (bytesPerPixel == 8) 
				pProp->Set(g_PixelType_64bitRGB);
			else
				pProp->Set(g_PixelType_8bit);
         ret=DEVICE_OK;
      }break;
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
         if(IsCapturing())
            return DEVICE_CAMERA_BUSY_ACQUIRING;

         long bitDepth;
         pProp->Get(bitDepth);

			unsigned int bytesPerComponent;

         switch (bitDepth) {
            case 8:
					bytesPerComponent = 1;
               bitDepth_ = 8;
               ret=DEVICE_OK;
            break;
            case 10:
					bytesPerComponent = 2;
               bitDepth_ = 10;
               ret=DEVICE_OK;
            break;
            case 12:
					bytesPerComponent = 2;
               bitDepth_ = 12;
               ret=DEVICE_OK;
            break;
            case 14:
					bytesPerComponent = 2;
               bitDepth_ = 14;
               ret=DEVICE_OK;
            break;
            case 16:
					bytesPerComponent = 2;
               bitDepth_ = 16;
               ret=DEVICE_OK;
            break;
            case 32:
               bytesPerComponent = 4;
               bitDepth_ = 32; 
               ret=DEVICE_OK;
            break;
            default: 
               // on error switch to default pixel type
					bytesPerComponent = 1;

               pProp->Set((long)8);
               bitDepth_ = 8;
               ret = ERR_UNKNOWN_MODE;
            break;
         }

			if (DEVICE_OK == ret) 
			{
				if ( 8 != bitDepth_)
					pProp->Get( last16BitDepth );
			}

			char buf[MM::MaxStrLength];
			GetProperty(MM::g_Keyword_PixelType, buf);
			std::string pixelType(buf);
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

            }else
				{
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
				   bytesPerPixel = 8;
				}		
			}
			img_.Resize(img_.Width(), img_.Height(), bytesPerPixel);

      } break;
   case MM::BeforeGet:
      {
         pProp->Set((long)bitDepth_);
         ret=DEVICE_OK;
      }break;
   }
   return ret; 
}
/**
* Handles "ReadoutTime" property.
*/
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

/*
* Handles "ScanMode" property.
* Changes allowed Binning values to test whether the UI updates properly
*/
int CABSCamera::OnScanMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{    
   if (eAct == MM::AfterSet) 
	{
      pProp->Get(scanMode_);
   
		u32 rc;
		u08 captureMode, countImages;

		if ( scanMode_ == g_ScanMode_Continuous )
		{
			captureMode = MODE_CONTINUOUS;
			countImages = 0;			
		}		
		else if ( scanMode_ == g_ScanMode_TriggeredHW )
		{
			captureMode = MODE_TRIGGERED_HW;
			countImages = 1;			
		}
		else // handle all other as g_ScanMode_TriggeredSW
		{
			if ( scanMode_ == g_ScanMode_TriggeredSW )
				pProp->Set(g_ScanMode_TriggeredSW);

			captureMode = MODE_TRIGGERED_SW;
			countImages = 1;			
		}

		// set capture mode at camera
		LogMessage("Set property ScanMode", true);
		rc = GET_RC( CamUSB_SetCaptureMode( captureMode, countImages, deviceNo(), 0, 0), deviceNo() );

		return convertApiErrorCode( rc );      
   } 
	else if (eAct == MM::BeforeGet) 
	{
		u32 rc;
		u08 captureMode, countImages;

		LogMessage("Reading property ScanMode", true);
		rc = GET_RC( CamUSB_GetCaptureMode( &captureMode, &countImages, deviceNo() ), deviceNo() );

		if ( !IsNoError( rc ) )
		{
			captureMode = MODE_TRIGGERED_SW;
		}

		switch ( captureMode )
		{
		case MODE_CONTINUOUS:	pProp->Set( g_ScanMode_Continuous );	break;

		case MODE_TRIGGERED_HW:	pProp->Set( g_ScanMode_TriggeredHW );	break;

		case MODE_TRIGGERED_SW:
		default:						pProp->Set( g_ScanMode_TriggeredSW );	break;
		}

		return convertApiErrorCode( rc ); 		
   }
   return DEVICE_OK;
}

int CABSCamera::OnCameraCCDXSize(MM::PropertyBase* pProp , MM::ActionType eAct)
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
			img_.Resize(cameraCCDXSize_/binSize_, cameraCCDYSize_/binSize_);
		}
   }
	return DEVICE_OK;

}

int CABSCamera::OnCameraCCDYSize(MM::PropertyBase* pProp, MM::ActionType eAct)
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
			img_.Resize(cameraCCDXSize_/binSize_, cameraCCDYSize_/binSize_);
		}
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
			pProp->Set( exposureUs / 1000.0 );
		}
		return convertApiErrorCode( rc ); 
   }
   else if (eAct == MM::AfterSet)
   {
      double value;
		u32 rc, exposureUs;

      pProp->Get(value);
		exposureUs = (u32)(value * 1000.0 + 0.5);
		rc = GET_RC( CamUSB_SetExposureTime( &exposureUs, deviceNo() ), deviceNo()); 
		if ( IsNoError( rc ) )
		{
			// update with new settings
			pProp->Set( exposureUs / 1000.0 );
		}
		return convertApiErrorCode( rc ); 
   }
	return DEVICE_OK;
}

int CABSCamera::OnGain(MM::PropertyBase* pProp, MM::ActionType eAct)
{	
	if (eAct == MM::BeforeGet)
   {
		u32 rc, gain;
		u16 gainChannel = ( isColor() ) ? GAIN_GREEN : GAIN_GLOBAL;
		
		rc = GET_RC( CamUSB_GetGain( &gain, gainChannel, deviceNo() ), deviceNo()); 
		if ( IsNoError( rc ) )
		{
			pProp->Set( gain / 1000.0 );
		}
		return convertApiErrorCode( rc ); 
   }
   else if (eAct == MM::AfterSet)
   {
		double value;
		u32 rc, gain;
		u16 gainChannel = ( isColor() ) ? (GAIN_GREEN|GAIN_LOCKED) : GAIN_GLOBAL;

      pProp->Get(value);
		gain = (u32)(value * 1000.0f + 0.5f);
		rc = GET_RC( CamUSB_SetGain( &gain, gainChannel, deviceNo() ), deviceNo()); 
		if ( IsNoError( rc ) )
		{
			// update with new settings
			pProp->Set( gain / 1000.0 );
		}
		return convertApiErrorCode( rc ); 
   }
	return DEVICE_OK;
}

int  CABSCamera::OnTemperature(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
   {
		u32 rc, size;
		S_TEMPERATURE_PARAMS  sTP = {0};
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
		return convertApiErrorCode( rc ); 
   }
   else if (eAct == MM::AfterSet)
   {		
		return convertApiErrorCode( retFUNCSET ); 
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

/**
* Sync internal image buffer size to the chosen property values.
*/
int CABSCamera::ResizeImageBuffer()
{
   char buf[MM::MaxStrLength];
   //int ret = GetProperty(MM::g_Keyword_Binning, buf);
   //if (ret != DEVICE_OK)
   //   return ret;
   //binSize_ = atol(buf);

   int ret = GetProperty(MM::g_Keyword_PixelType, buf);
   if (ret != DEVICE_OK)
      return ret;

	std::string pixelType(buf);
	int byteDepth = 0;

   if (pixelType.compare(g_PixelType_8bit) == 0)
   {
      byteDepth = 1;
   }
   else if (pixelType.compare(g_PixelType_16bit) == 0)
   {
      byteDepth = 2;
   }
	else if ( pixelType.compare(g_PixelType_32bitRGB) == 0)
	{
      byteDepth = 4;
	}
	else if ( pixelType.compare(g_PixelType_32bit) == 0)
	{
      byteDepth = 4;
	}
	else if ( pixelType.compare(g_PixelType_64bitRGB) == 0)
	{
      byteDepth = 8;
	}

   img_.Resize(cameraCCDXSize_/binSize_, cameraCCDYSize_/binSize_, byteDepth);
   return DEVICE_OK;
}

void CABSCamera::GenerateEmptyImage(ImgBuffer& img)
{
   MMThreadGuard g(imgPixelsLock_);
   if (img.Height() == 0 || img.Width() == 0 || img.Depth() == 0)
      return;
   unsigned char* pBuf = const_cast<unsigned char*>(img.GetPixels());
   memset(pBuf, 0, img.Height()*img.Width()*img.Depth());
}

u32 CABSCamera::getCameraImage( ImgBuffer& img )
{
	MMThreadGuard g(imgPixelsLock_);
	
	S_IMAGE_HEADER  imageHeader = {0};
	S_IMAGE_HEADER *imageHeaderPointer = &imageHeader;
	u08* imagePointer = img.GetPixelsRW();
	u32 size	= img.Width() * img.Height() * img.Depth();

	u32 rc = GET_RC( CamUSB_GetImage( &imagePointer, &imageHeaderPointer, size, deviceNo()), deviceNo() );
	if ( retNOIMG == rc )
		rc	= GET_RC( CamUSB_GetImage( &imagePointer, &imageHeaderPointer, size, deviceNo()), deviceNo() );

	return rc;
}
/**
* Generate a spatial sine wave.
*/
void CABSCamera::GenerateSyntheticImage(ImgBuffer& img, double exp)
{ 
  
   MMThreadGuard g(imgPixelsLock_);

	//std::string pixelType;
	char buf[MM::MaxStrLength];
   GetProperty(MM::g_Keyword_PixelType, buf);
	std::string pixelType(buf);

	if (img.Height() == 0 || img.Width() == 0 || img.Depth() == 0)
      return;

   const double cPi = 3.14159265358979;
   long lPeriod = img.Width()/2;
   double dLinePhase = 0.0;
   const double dAmp = exp;
   const double cLinePhaseInc = 2.0 * cPi / 4.0 / img.Height();

   static bool debugRGB = false;
#ifdef TIFFDEMO
	debugRGB = true;
#endif
   static  unsigned char* pDebug  = NULL;
   static unsigned long dbgBufferSize = 0;
   static long iseq = 1;

 

	// for integer images: bitDepth_ is 8, 10, 12, 16 i.e. it is depth per component
   long maxValue = (1L << bitDepth_)-1;

	long pixelsToDrop = 0;
	if( dropPixels_)
		pixelsToDrop = (long)(0.5 + fractionOfPixelsToDropOrSaturate_*img.Height()*img.Width());
	long pixelsToSaturate = 0;
	if( saturatePixels_)
		pixelsToSaturate = (long)(0.5 + fractionOfPixelsToDropOrSaturate_*img.Height()*img.Width());

   unsigned j, k;
   if (pixelType.compare(g_PixelType_8bit) == 0)
   {
      double pedestal = 127 * exp / 100.0 * GetBinning() * GetBinning();
      unsigned char* pBuf = const_cast<unsigned char*>(img.GetPixels());
      for (j=0; j<img.Height(); j++)
      {
         for (k=0; k<img.Width(); k++)
         {
            long lIndex = img.Width()*j + k;
            *(pBuf + lIndex) = (unsigned char) (g_IntensityFactor_ * min(255.0, (pedestal + dAmp * sin(dPhase_ + dLinePhase + (2.0 * cPi * k) / lPeriod))));
         }
         dLinePhase += cLinePhaseInc;
      }
	   for(int snoise = 0; snoise < pixelsToSaturate; ++snoise)
		{
			j = (unsigned)( (double)(img.Height()-1)*(double)rand()/(double)RAND_MAX);
			k = (unsigned)( (double)(img.Width()-1)*(double)rand()/(double)RAND_MAX);
			*(pBuf + img.Width()*j + k) = (unsigned char)maxValue;
		}
		int pnoise;
		for(pnoise = 0; pnoise < pixelsToDrop; ++pnoise)
		{
			j = (unsigned)( (double)(img.Height()-1)*(double)rand()/(double)RAND_MAX);
			k = (unsigned)( (double)(img.Width()-1)*(double)rand()/(double)RAND_MAX);
			*(pBuf + img.Width()*j + k) = 0;
		}

   }
   else if (pixelType.compare(g_PixelType_16bit) == 0)
   {
      double pedestal = maxValue/2 * exp / 100.0 * GetBinning() * GetBinning();
      double dAmp16 = dAmp * maxValue/255.0; // scale to behave like 8-bit
      unsigned short* pBuf = (unsigned short*) const_cast<unsigned char*>(img.GetPixels());
      for (j=0; j<img.Height(); j++)
      {
         for (k=0; k<img.Width(); k++)
         {
            long lIndex = img.Width()*j + k;
            *(pBuf + lIndex) = (unsigned short) (g_IntensityFactor_ * min((double)maxValue, pedestal + dAmp16 * sin(dPhase_ + dLinePhase + (2.0 * cPi * k) / lPeriod)));
         }
         dLinePhase += cLinePhaseInc;
      }         
	   for(int snoise = 0; snoise < pixelsToSaturate; ++snoise)
		{
			j = (unsigned)(0.5 + (double)img.Height()*(double)rand()/(double)RAND_MAX);
			k = (unsigned)(0.5 + (double)img.Width()*(double)rand()/(double)RAND_MAX);
			*(pBuf + img.Width()*j + k) = (unsigned short)maxValue;
		}
		int pnoise;
		for(pnoise = 0; pnoise < pixelsToDrop; ++pnoise)
		{
			j = (unsigned)(0.5 + (double)img.Height()*(double)rand()/(double)RAND_MAX);
			k = (unsigned)(0.5 + (double)img.Width()*(double)rand()/(double)RAND_MAX);
			*(pBuf + img.Width()*j + k) = 0;
		}
	
	}
   else if (pixelType.compare(g_PixelType_32bit) == 0)
   {
      double pedestal = 127 * exp / 100.0 * GetBinning() * GetBinning();
      float* pBuf = (float*) const_cast<unsigned char*>(img.GetPixels());
      float saturatedValue = 255.;
      memset(pBuf, 0, img.Height()*img.Width()*4);
      static unsigned int j2;
      for (j=0; j<img.Height(); j++)
      {
         for (k=0; k<img.Width(); k++)
         {
            long lIndex = img.Width()*j + k;
            double value =  (g_IntensityFactor_ * min(255.0, (pedestal + dAmp * sin(dPhase_ + dLinePhase + (2.0 * cPi * k) / lPeriod))));
            *(pBuf + lIndex) = (float) value;
            if( 0 == lIndex)
            {
               std::ostringstream os;
               os << " first pixel is " << (float)value;
               LogMessage(os.str().c_str(), true);

            }
         }
         dLinePhase += cLinePhaseInc;
      }

	   for(int snoise = 0; snoise < pixelsToSaturate; ++snoise)
		{
			j = (unsigned)(0.5 + (double)img.Height()*(double)rand()/(double)RAND_MAX);
			k = (unsigned)(0.5 + (double)img.Width()*(double)rand()/(double)RAND_MAX);
			*(pBuf + img.Width()*j + k) = saturatedValue;
		}
		int pnoise;
		for(pnoise = 0; pnoise < pixelsToDrop; ++pnoise)
		{
			j = (unsigned)(0.5 + (double)img.Height()*(double)rand()/(double)RAND_MAX);
			k = (unsigned)(0.5 + (double)img.Width()*(double)rand()/(double)RAND_MAX);
			*(pBuf + img.Width()*j + k) = 0;
      }
	
	}
	else if (pixelType.compare(g_PixelType_32bitRGB) == 0)
	{
      double pedestal = 127 * exp / 100.0;
      unsigned int * pBuf = (unsigned int*) img.GetPixelsRW();

      unsigned char* pTmpBuffer = NULL;

      if(debugRGB)
      {
         const unsigned long bfsize = img.Height() * img.Width() * 3;
         if(  bfsize != dbgBufferSize)
         {
            if (NULL != pDebug)
            {
               free(pDebug);
               pDebug = NULL;
            }
            pDebug = (unsigned char*)malloc( bfsize);
            if( NULL != pDebug)
            {
               dbgBufferSize = bfsize;
            }
         }
      }

		// only perform the debug operations if pTmpbuffer is not 0
      pTmpBuffer = pDebug;
      unsigned char* pTmp2 = pTmpBuffer;
      if( NULL!= pTmpBuffer)
			memset( pTmpBuffer, 0, img.Height() * img.Width() * 3);

      for (j=0; j<img.Height(); j++)
      {
         unsigned char theBytes[4];
         for (k=0; k<img.Width(); k++)
         {
            long lIndex = img.Width()*j + k;
            unsigned char value0 =   (unsigned char) min(255.0, (pedestal + dAmp * sin(dPhase_ + dLinePhase + (2.0 * cPi * k) / lPeriod)));
            theBytes[0] = value0;
            if( NULL != pTmpBuffer)
               pTmp2[2] = value0;
            unsigned char value1 =   (unsigned char) min(255.0, (pedestal + dAmp * sin(dPhase_ + dLinePhase*2 + (2.0 * cPi * k) / lPeriod)));
            theBytes[1] = value1;
            if( NULL != pTmpBuffer)
               pTmp2[1] = value1;
            unsigned char value2 = (unsigned char) min(255.0, (pedestal + dAmp * sin(dPhase_ + dLinePhase*4 + (2.0 * cPi * k) / lPeriod)));
            theBytes[2] = value2;

            if( NULL != pTmpBuffer){
               pTmp2[0] = value2;
               pTmp2+=3;
            }
            theBytes[3] = 0;
            unsigned long tvalue = *(unsigned long*)(&theBytes[0]);
            *(pBuf + lIndex) =  tvalue ;  //value0+(value1<<8)+(value2<<16);
         }
         dLinePhase += cLinePhaseInc;
      }


      // ImageJ's AWT images are loaded with a Direct Color processor which expects BGRA, that's why we swapped the Blue and Red components in the generator above.
      if(NULL != pTmpBuffer)
      {
         // write the compact debug image...
			/*
         char ctmp[12];
         snprintf(ctmp,12,"%ld",iseq++);
         int status = writeCompactTiffRGB( img.Width(), img.Height(), pTmpBuffer, ("democamera"+std::string(ctmp)).c_str()
            );
			status = status;
			*/
      }

	}

	// generate an RGB image with bitDepth_ bits in each color
	else if (pixelType.compare(g_PixelType_64bitRGB) == 0)
	{
		double maxPixelValue = (1<<(bitDepth_))-1;
      double pedestal = 127 * exp / 100.0;
      unsigned long long * pBuf = (unsigned long long*) img.GetPixelsRW();
      for (j=0; j<img.Height(); j++)
      {
         for (k=0; k<img.Width(); k++)
         {
            long lIndex = img.Width()*j + k;
            unsigned long long value0 = (unsigned char) min(maxPixelValue, (pedestal + dAmp * sin(dPhase_ + dLinePhase + (2.0 * cPi * k) / lPeriod)));
            unsigned long long value1 = (unsigned char) min(maxPixelValue, (pedestal + dAmp * sin(dPhase_ + dLinePhase*2 + (2.0 * cPi * k) / lPeriod)));
            unsigned long long value2 = (unsigned char) min(maxPixelValue, (pedestal + dAmp * sin(dPhase_ + dLinePhase*4 + (2.0 * cPi * k) / lPeriod)));
            unsigned long long tval = value0+(value1<<16)+(value2<<32);
         *(pBuf + lIndex) = tval;
			}
         dLinePhase += cLinePhaseInc;
      }
	}

   dPhase_ += cPi / 4.;
}
void CABSCamera::TestResourceLocking(const bool recurse)
{
   MMThreadGuard g(*pDemoResourceLock_);
   if(recurse)
      TestResourceLocking(false);
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
		if ( isApiDllAvailable() )
		{
			CCameraList cCameraList;
			getAvailableCameras( cCameraList );
			
			if ( cCameraList.size() > 0)
			{
				CCameraList::iterator iter = cCameraList.begin();
				while ( iter != cCameraList.end() )
				{
					AddAllowedValue( "Camera", buildCameraDeviceID( iter->sVersion.dwSerialNumber, (const char*) iter->sVersion.szDeviceName ).c_str() ); // no camera yet
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
			AddAllowedValue( "Camera",  strInfo.c_str() ); // no API DLL found
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

string CABSCamera::buildCameraDeviceID( unsigned long serialNumber, const char* deviceName )
{
	string cameraDeviceID;
	str::sprintf( cameraDeviceID, "#%05X", serialNumber );
	return (string( deviceName ) + cameraDeviceID);
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

int CABSCamera::deviceNo( void ) const
{
	return deviceNo_;
}

int CABSCamera::accquireDeviceNumber( void )
{
	return ++staticDeviceNo;
}

void CABSCamera::releaseDeviceNumber( int deviceNo )
{
	if (deviceNo != NO_CAMERA_DEVICE)
		if (staticDeviceNo != NO_CAMERA_DEVICE)
			--staticDeviceNo;
}

int CABSCamera::convertApiErrorCode( unsigned long errorNumber )
{
	string errorMessage;
	errorMessage.resize( 260, 0 );
	CamUSB_GetErrorString( (char *) errorMessage.c_str(), (u32)errorMessage.size(), errorNumber );

	str::ResizeByZeroTermination( errorMessage );

	if ( IsNoError( errorNumber ) )
	{
		if ( retOK != errorNumber )
			errorMessage = "API-DLL Warning: " + errorMessage;	 
	}
	else
		errorMessage = "API-DLL Error: " + errorMessage;	 
	
	SetErrorText(ERR_CAMERA_API_BASE + errorNumber, errorMessage.c_str() );
  
	LogMessage(errorMessage);
  
	return ( retOK == errorNumber ) ? (DEVICE_OK) : (ERR_CAMERA_API_BASE + errorNumber);
}

// scan for device
void CABSCamera::getAvailableCameras( CCameraList & cCameraList )
{
	const int iCntElements = 16;
	cCameraList.resize( iCntElements );
	int nCamerasFound = CamUSB_GetCameraListEx( &cCameraList[0], (u32)cCameraList.size() );
	cCameraList.resize( min(nCamerasFound, iCntElements ) );
}


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

/*
if ( isInitialized() ) 
		{
         int ret = OnPropertiesChanged();
         if (ret != DEVICE_OK)
		}      return ret;
		*/

