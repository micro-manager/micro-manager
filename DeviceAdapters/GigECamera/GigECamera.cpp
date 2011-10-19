///////////////////////////////////////////////////////////////////////////////
// FILE:          GigECamera.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   An adapter for Gigbit-Ethernet cameras using an
//				  SDK from JAI, Inc.  Users and developers will 
//				  need to download and install the JAI SDK and control tool.
//                
// AUTHOR:        David Marshburn, UNC-CH, marshbur@cs.unc.edu, Jan. 2011
//

#include "GigECamera.h"
#include <cstdio>
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include "../../MMCore/Error.h"
#include <sstream>
#include <algorithm>

#include "boost/lexical_cast.hpp"

#include <Jai_Factory.h>


double g_IntensityFactor_ = 1.0;

// External names used used by the rest of the system
// to load particular device from the "GigECamera.dll" library
const char* g_CameraDeviceName = "GigE camera adapter";

// constants for naming pixel types (allowed values of the "PixelType" property)
// these are the names used in the umanager interface.  (from the GenICam spec)
const char* g_PixelType_8bit = "Mono8";
const char* g_PixelType_8bitSigned = "Mono8Signed";
const char* g_PixelType_10bit = "Mono10";
const char* g_PixelType_10bitPacked = "Mono10Packed";
const char* g_PixelType_12bit = "Mono12";
const char* g_PixelType_12bitPacked = "Mono12Packed";
const char* g_PixelType_14bit = "Mono14";
const char* g_PixelType_16bit = "Mono16";



// windows DLL entry code
#ifdef WIN32
BOOL APIENTRY DllMain( HANDLE /*hModule*/, 
					  DWORD  ul_reason_for_call, 
					  LPVOID /*lpReserved*/
					  )
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

/**
* List all suppoerted hardware devices here
* Do not discover devices at runtime.  To avoid warnings about missing DLLs, Micro-Manager
* maintains a list of supported device (MMDeviceList.txt).  This list is generated using 
* information supplied by this function, so runtime discovery will create problems.
*/
MODULE_API void InitializeModuleData()
{
	AddAvailableDeviceName(g_CameraDeviceName, "GigE camera");

}


MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
	if (deviceName == 0)
		return 0;

	// decide which device class to create based on the deviceName parameter
	if (strcmp(deviceName, g_CameraDeviceName) == 0)
	{
		// create camera
		return new CGigECamera();
	}
	// ...supplied name not recognized
	return 0;
}


MODULE_API void DeleteDevice(MM::Device* pDevice)
{
	delete pDevice;
}


///////////////////////////////////////////////////////////////////////////////
// CGigECamera implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~

/**
* CGigECamera constructor.
* Setup default all variables and create device properties required to exist
* before intialization. In this case, no such properties were required. All
* properties will be created in the Initialize() method.
*
* As a general guideline Micro-Manager devices do not access hardware in the
* the constructor. We should do as little as possible in the constructor and
* perform most of the initialization in the Initialize() method.
*/
CGigECamera::CGigECamera() :
CCameraBase<CGigECamera> (),
readoutUs_(0.0),
scanMode_(1),
bitDepth_(8),
roiX_(0),
roiY_(0),
cameraOpened(false),
cameraInitialized(false),
snapImageDone( false ),
snapOneImageOnly( false ),
doContinuousAcquisition( false ),
stopContinuousAcquisition( false ),
continuousAcquisitionDone( false ),
cameraNameMap( ),
frameRateMap( ),
pixelFormatMap( ),
nodes( NULL ),
buffer( NULL ),
bufferSizeBytes( 0 )
{
	// call the base class method to set-up default error codes/messages
	InitializeDefaultErrorMessages();
	readoutStartTime_ = GetCurrentMMTime();
}


/**
* CGigECamera destructor.
* If this device used as intended within the Micro-Manager system,
* Shutdown() will be always called before the destructor. But in any case
* we need to make sure that all resources are properly released even if
* Shutdown() was not called.
*/
CGigECamera::~CGigECamera()
{
	if( nodes != NULL )
		delete nodes;
	if( buffer != NULL )
		delete buffer;
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
int CGigECamera::Initialize()
{
	if (cameraInitialized)
		return DEVICE_OK;
	CPropertyAction *pAct;
	J_STATUS_TYPE retval;
	int nRet;

	retval = J_Factory_Open("" , &hFactory);
	if( retval != J_ST_SUCCESS )
	{
		LogMessage( "JAI GigE factory failed", false );
		return DEVICE_NATIVE_MODULE_FAILED;
	}

	int8_t sVersion[J_FACTORY_INFO_SIZE];
	uint32_t size = sizeof(sVersion);
	retval = J_Factory_GetInfo(FAC_INFO_VERSION, sVersion, &size);
	if( retval != J_ST_SUCCESS )
	{
		LogMessage( "JAI GigE factory (info) failed", false );
		return DEVICE_NATIVE_MODULE_FAILED;
	}
	LogMessage( (std::string) "Using JAI Factory v." + sVersion, true );

	uint32_t nCameras;
	retval = J_Factory_GetNumOfCameras(hFactory, &nCameras); 
	if( retval != J_ST_SUCCESS )
	{
		LogMessage( "camera discovery failed", false );
		return DEVICE_NATIVE_MODULE_FAILED;
	}

	// Run through the list of found cameras
	int8_t sCameraID[J_CAMERA_ID_SIZE];
	std::string selectedCamera;
	if( nCameras == 0 )
	{
		snprintf( sCameraID, J_CAMERA_ID_SIZE, "(no camera)" );
		selectedCamera = sCameraID;
	}
	else
	{
		std::vector<std::string> availableCameras;
		for( int index = 0; index <= (int) nCameras - 1; index++ )
		{
			size = sizeof(sCameraID);
			retval = J_Factory_GetCameraIDByIndex(hFactory, index, sCameraID, &size); 
			if( retval != J_ST_SUCCESS )
			{
				LogMessage( (std::string) "camera ID failed (" + CDeviceUtils::ConvertToString( index ) + ")", false );
				return DEVICE_NATIVE_MODULE_FAILED;
			}
			LogMessage( (std::string) "found camera:  " + sCameraID, true );
			availableCameras.push_back( sCameraID );
		}

		// since the camera names sometimes have forbidden characters, create a map
		// between the umanager-acceptable names and the real names from the JAI library
		std::vector<std::string> correctedCameras;
		for( std::vector<std::string>::iterator i = availableCameras.begin();  i != availableCameras.end(); i++ )
		{
			std::string s = (*i);
			for( int j = 0; j <= (int) strlen( MM::g_FieldDelimiters ) - 1; j++ )
			{
				size_t pos = s.find_first_of( MM::g_FieldDelimiters[j] );
				while( pos != std::string::npos  )
				{
					s.replace( pos, 1, "~" );
					pos = s.find_first_of( MM::g_FieldDelimiters[j] );
				}
			}
			cameraNameMap.insert( std::pair<std::string,std::string>( s, *i ) );
			cameraNameMap.insert( std::pair<std::string,std::string>( *i, s ) );
			correctedCameras.push_back( s );
		}

		// try to pick some camera using the filter driver ("FD")
		// the filter-driver interface is apparently immune to windows' firewall
		for( std::vector<std::string>::iterator i = correctedCameras.begin(); i != correctedCameras.end(); i++ )
		{
			if( strstr( (*i).c_str(), "INT=>FD") != NULL ) 
			{
				selectedCamera = *i;
			}
		}
		if( selectedCamera.empty() ) // no FD camera?  just pick one
			selectedCamera = correctedCameras[0];

		// available cameras
		pAct = new CPropertyAction (this, &CGigECamera::OnCameraChoice);
		nRet = CreateProperty(MM::g_Keyword_Camera_Choice, selectedCamera.c_str(), MM::String, false, pAct);
		if (nRet != DEVICE_OK)
			return nRet;
		nRet = SetAllowedValues(MM::g_Keyword_Camera_Choice, correctedCameras);
		if (nRet != DEVICE_OK)
			return nRet;

	}

	// open a camera
	std::map<std::string,std::string>::iterator i = cameraNameMap.find( selectedCamera );
	if( i == cameraNameMap.end() )
		return DEVICE_NATIVE_MODULE_FAILED;
	this->cameraName = (*i).second;
	LogMessage( (std::string) "Opening camera:  " + this->cameraName );
	retval = J_Camera_Open( hFactory, const_cast<char*>(this->cameraName.c_str()), &hCamera );
	if( retval != J_ST_SUCCESS )
	{
			LogMessage( (std::string) "camera open failed (" + this->cameraName + ")", false );
			return DEVICE_NATIVE_MODULE_FAILED;
	}
	cameraOpened = true;
	this->nodes = new GigENodes( hCamera );

	// make sure the exposure mode is set to "Timed", if possible.
	// not an error if we can't set this, since it's only a recommended parameter.
	retval = J_Camera_SetValueString( hCamera, "ExposureMode", "Timed" );
	if( retval != J_ST_SUCCESS )
	{
		// some older cameras (from JAI?) use "ShutterMode" instead of "ExposureMode"
		retval = J_Camera_SetValueString( hCamera, "ShutterMode", "ExposureTimeAbs" );
		if( retval != J_ST_SUCCESS )
		{
			LogMessage( (std::string) "setupImaging failed to set ExposureMode to Timed and ShutterMode to ExposureTimeAbs" );
		}
	}
	
	// set property list
	// -----------------
	std::string s;

	// Name
	nRet = CreateProperty(MM::g_Keyword_Name, g_CameraDeviceName, MM::String, true);
	if( nRet != DEVICE_OK )
		return nRet;

	// Vendor
	if( nodes->get( s, DEVICE_VENDOR_NAME ) )
	{
		nRet = CreateProperty( "Camera Vendor", s.c_str(), MM::String, !nodes->isWritable( DEVICE_VENDOR_NAME ) );
		if( nRet != DEVICE_OK )
			return nRet;
	}

	// Vendor info
	if( nodes->get( s, DEVICE_MANUFACTURER_INFO ) )
	{
		nRet = CreateProperty( "Camera Vendor Info", s.c_str(), MM::String, !nodes->isWritable( DEVICE_MANUFACTURER_INFO ) );
		if( nRet != DEVICE_OK )
			return nRet;
	}

	// Model
	if( nodes->get( s, DEVICE_MODEL_NAME ) )
	{
		nRet = CreateProperty( "Camera Model", s.c_str(), MM::String, !nodes->isWritable( DEVICE_MODEL_NAME ) );
		if( nRet != DEVICE_OK )
			return nRet;
	}

	// Version
	if( nodes->get( s, DEVICE_VERSION ) )
	{
		nRet = CreateProperty( "Camera Version", s.c_str(), MM::String, !nodes->isWritable( DEVICE_VERSION ) );
		if( nRet != DEVICE_OK )
			return nRet;
	}

	// CameraID
	if( nodes->get( s, DEVICE_ID ) )
	{
		nRet = CreateProperty( MM::g_Keyword_CameraID, s.c_str(), MM::String, !nodes->isWritable( DEVICE_ID ) );
		if( nRet != DEVICE_OK )
			return nRet;
	}

	// Camera Firmware version
	if( nodes->get( s, DEVICE_FIRMWARE_VERSION ) )
	{
		nRet = CreateProperty( "Camera Firmware Version", s.c_str(), MM::String, !nodes->isWritable( DEVICE_FIRMWARE_VERSION ) );
		if( nRet != DEVICE_OK )
			return nRet;
	}

	// GigE Vision spec version
	int64_t v;
	if( nodes->get( v, GEV_VERSION_MAJOR ) )
	{
		nRet = CreateProperty( "GigE Vision Major Version Number", boost::lexical_cast<std::string>( v ).c_str(), MM::Integer, 
								!nodes->isWritable( GEV_VERSION_MAJOR ) );
		if( nRet != DEVICE_OK )
			return nRet;
	}
	
	if( nodes->get( v, GEV_VERSION_MINOR ) )
	{
		nRet = CreateProperty( "GigE Vision Minor Version Number", boost::lexical_cast<std::string>( v ).c_str(), MM::Integer, 
								!nodes->isWritable( GEV_VERSION_MINOR ) );
		if( nRet != DEVICE_OK )
			return nRet;
	}
	

	// initialize width and height to something reasonable
	int64_t dim;
	if( nodes->get( dim, WIDTH ) )
	{
		pAct = new CPropertyAction( this, &CGigECamera::OnImageWidth );
		nRet = CreateProperty( MM::g_Keyword_Image_Width, CDeviceUtils::ConvertToString( (long) dim ), MM::Integer, !nodes->isWritable( WIDTH ), pAct );
		if( nRet != DEVICE_OK )
			return nRet;
		int64_t low = nodes->getMin( WIDTH );
		int64_t high = nodes->getMax( WIDTH );
		nRet = SetPropertyLimits( MM::g_Keyword_Image_Width, (double) low, (double) high );
		if( nRet != DEVICE_OK )
			return nRet;
	}

	if( nodes->get( dim, HEIGHT ) )
	{
		pAct = new CPropertyAction( this, &CGigECamera::OnImageHeight );
		nRet = CreateProperty( MM::g_Keyword_Image_Height, CDeviceUtils::ConvertToString( (long) dim ), MM::Integer, !nodes->isWritable( HEIGHT ), pAct );
		if( nRet != DEVICE_OK )
			return nRet;
		int64_t low = nodes->getMin( HEIGHT );
		int64_t high = nodes->getMax( HEIGHT );
		nRet = SetPropertyLimits( MM::g_Keyword_Image_Height, (double) low, (double) high );
		if( nRet != DEVICE_OK )
			return nRet;
	}

	// width max and height max
	if( nodes->isAvailable( WIDTH_MAX ) )
	{
		nodes->get( dim, WIDTH_MAX );
		pAct = new CPropertyAction( this, &CGigECamera::OnImageWidthMax );
		nRet = CreateProperty( MM::g_Keyword_Image_Width_Max, CDeviceUtils::ConvertToString( (long) dim ), MM::Integer, !nodes->isWritable( WIDTH_MAX ), pAct );
		if( nRet != DEVICE_OK )
			return nRet;
	}

	if( nodes->isAvailable( HEIGHT_MAX ) )
	{
		nodes->get( dim, HEIGHT_MAX );
		pAct = new CPropertyAction( this, &CGigECamera::OnImageHeightMax );
		nRet = CreateProperty( MM::g_Keyword_Image_Height_Max, CDeviceUtils::ConvertToString( (long) dim ), MM::Integer, !nodes->isWritable( HEIGHT_MAX ), pAct );
		if( nRet != DEVICE_OK )
			return nRet;
	}

	// sensor width and sensor height
	if( nodes->isAvailable( SENSOR_WIDTH ) )
	{
		nodes->get( dim, SENSOR_WIDTH );
		nRet = CreateProperty( MM::g_Keyword_Sensor_Width, CDeviceUtils::ConvertToString( (long) dim ), MM::Integer, !nodes->isWritable( SENSOR_WIDTH ) );
		if( nRet != DEVICE_OK )
			return nRet;
	}

	if( nodes->isAvailable( SENSOR_HEIGHT ) )
	{
		nodes->get( dim, SENSOR_HEIGHT );
		nRet = CreateProperty( MM::g_Keyword_Sensor_Height, CDeviceUtils::ConvertToString( (long) dim ), MM::Integer, !nodes->isWritable( SENSOR_HEIGHT ) );
		if( nRet != DEVICE_OK )
			return nRet;
	}


	// binning.  
	// note that the GenICam spec separates vertical and horizontal binning and does
	// not provide a single, unified binning property.  the various OnBinning methods
	// will do their best to provide this illusion of a unified binning when possible.
	pAct = new CPropertyAction( this, &CGigECamera::OnBinning );
	nRet = CreateProperty( MM::g_Keyword_Binning, "1", MM::Integer, false, pAct );
	if (DEVICE_OK != nRet)
		return nRet;

	int64_t bin;
	if( nodes->isAvailable( BINNING_VERTICAL ) )
	{
		nodes->get( bin, BINNING_VERTICAL );
		pAct = new CPropertyAction( this, &CGigECamera::OnBinningV );
		nRet = CreateProperty( MM::g_Keyword_Binning_Vertical, CDeviceUtils::ConvertToString( (long) bin ), MM::Integer, !nodes->isWritable( BINNING_VERTICAL ), pAct );
		if (DEVICE_OK != nRet)
			return nRet;
	}

	if( nodes->isAvailable( BINNING_HORIZONTAL ) )
	{
		nodes->get( bin, BINNING_HORIZONTAL );
		pAct = new CPropertyAction( this, &CGigECamera::OnBinningH );
		nRet = CreateProperty( MM::g_Keyword_Binning_Horizontal, CDeviceUtils::ConvertToString( (long) bin ), MM::Integer, !nodes->isWritable( BINNING_HORIZONTAL ), pAct );
		if (DEVICE_OK != nRet)
			return nRet;
	}

	nRet = SetAllowedBinning();
	if (nRet != DEVICE_OK)
		return nRet;

	// pixel type
	// note that, in the GenICam standard, pixel format and bit depth are rolled into one
	std::vector<std::string> pixelTypeValues;
	for( uint32_t i = 0; i <= nodes->getNumEnumEntries( PIXEL_FORMAT ) - 1; i++ )
	{
		std::string entry, displayName;
		nodes->getEnumEntry( entry, i, PIXEL_FORMAT );
		nodes->getEnumDisplayName( displayName, i, PIXEL_FORMAT );

		// this excludes a number of formats that aren't yet handled
		if( entry.find( "Packed" ) == std::string::npos 
			&& entry.find( "Bayer" ) == std::string::npos
			&& entry.find( "Planar" ) == std::string::npos
			&& entry.find( "Device-specific" ) == std::string::npos )
		{
			pixelFormatMap.insert( std::pair<std::string, std::string>( entry, displayName ) );
			pixelFormatMap.insert( std::pair<std::string, std::string>( displayName, entry ) );
			pixelTypeValues.push_back( displayName );
		}
	}

	pAct = new CPropertyAction (this, &CGigECamera::OnPixelType);
	std::string px, dn;
	nodes->get( px, PIXEL_FORMAT );
	std::map<std::string, std::string>::iterator it = pixelFormatMap.find( px );
	if( it == pixelFormatMap.end() )
		dn = px;
	else
		dn = it->second;
	nRet = CreateProperty( MM::g_Keyword_PixelType, dn.c_str(), MM::String, !nodes->isWritable( PIXEL_FORMAT ), pAct );
	if (nRet != DEVICE_OK)
		return nRet;

	nRet = SetAllowedValues(MM::g_Keyword_PixelType, pixelTypeValues);
	if (nRet != DEVICE_OK)
		return nRet;

	// exposure
	// note that exposure in GenICam has units of us; umanager has units of ms
	if( nodes->isAvailable( EXPOSURE_TIME ) )
	{
		pAct = new CPropertyAction( this, &CGigECamera::OnExposure );
		double e;
		nodes->get( e, EXPOSURE_TIME );
		nRet = CreateProperty( MM::g_Keyword_Exposure, CDeviceUtils::ConvertToString( e / 1000.0 ), MM::Float, !nodes->isWritable( EXPOSURE_TIME ), pAct );
		if (nRet != DEVICE_OK)
			return nRet;
		double low, high;
		low = nodes->getMin( EXPOSURE_TIME ) / 1000.0;
		high = nodes->getMax( EXPOSURE_TIME ) / 1000.0;
		SetPropertyLimits( MM::g_Keyword_Exposure, low, high );
	}
	else if( nodes->isAvailable( EXPOSURE_TIME_ABS ) )
	{
		pAct = new CPropertyAction( this, &CGigECamera::OnExposure );
		double e;
		nodes->get( e, EXPOSURE_TIME_ABS );
		nRet = CreateProperty( MM::g_Keyword_Exposure, CDeviceUtils::ConvertToString( (double) e / 1000.0 ), MM::Float, !nodes->isWritable( EXPOSURE_TIME_ABS ), pAct );
		if (nRet != DEVICE_OK)
			return nRet;
		double low, high;
		low = nodes->getMin( EXPOSURE_TIME_ABS ) / 1000.0;
		high = nodes->getMax( EXPOSURE_TIME_ABS ) / 1000.0;
		SetPropertyLimits( MM::g_Keyword_Exposure, low, high );
	}
	else if( nodes->isAvailable( EXPOSURE_TIME_ABS_INT ) )
	{
		pAct = new CPropertyAction( this, &CGigECamera::OnExposure );
		int64_t e;
		nodes->get( e, EXPOSURE_TIME_ABS_INT );
		// create this as a float variable since GenICam has units of us
		nRet = CreateProperty( MM::g_Keyword_Exposure, CDeviceUtils::ConvertToString( (double) e / 1000.0 ), MM::Float, !nodes->isWritable( EXPOSURE_TIME_ABS_INT ), pAct );
		if (nRet != DEVICE_OK)
			return nRet;
		double low, high;
		low = nodes->getMin( EXPOSURE_TIME_ABS_INT ) / 1000.0;
		high = nodes->getMax( EXPOSURE_TIME_ABS_INT ) / 1000.0;
		SetPropertyLimits( MM::g_Keyword_Exposure, low, high );
	}

	// camera gain
	if( nodes->isAvailable( GAIN ) )
	{
		pAct = new CPropertyAction( this, &CGigECamera::OnGain );
		double d = 0;
		nodes->get( d, GAIN );
		nRet = CreateProperty( MM::g_Keyword_Gain,  CDeviceUtils::ConvertToString( d ), MM::Float, !nodes->isWritable( GAIN ), pAct );
		if (nRet != DEVICE_OK)
			return nRet;
		double low, high;
		low = nodes->getMin( GAIN );
		high = nodes->getMax( GAIN );
		SetPropertyLimits( MM::g_Keyword_Gain, low, high );
	}
	else if( nodes->isAvailable( GAIN_RAW ) )
	{
		pAct = new CPropertyAction( this, &CGigECamera::OnGain );
		int64_t d = 0;
		nodes->get( d, GAIN_RAW );
		nRet = CreateProperty( MM::g_Keyword_Gain,  CDeviceUtils::ConvertToString( (long) d ), MM::Integer, !nodes->isWritable( GAIN_RAW ), pAct );
		if (nRet != DEVICE_OK)
			return nRet;
		int64_t low, high;
		low = nodes->getMin( GAIN_RAW );
		high = nodes->getMax( GAIN_RAW );
		SetPropertyLimits( MM::g_Keyword_Gain, (double) low, (double) high );
	}

	// camera temperature
	if( nodes->isAvailable( TEMPERATURE ) )
	{
		pAct = new CPropertyAction( this, &CGigECamera::OnTemperature );
		double d = 0;
		nodes->get( d, TEMPERATURE );
		nRet = CreateProperty( MM::g_Keyword_CCDTemperature, CDeviceUtils::ConvertToString( d ), MM::Float, !nodes->isWritable( TEMPERATURE ), pAct );
		if (nRet != DEVICE_OK)
			return nRet;
		double low, high;
		low = nodes->getMin( TEMPERATURE );
		high = nodes->getMax( TEMPERATURE );
		SetPropertyLimits( MM::g_Keyword_CCDTemperature, low, high );
	}

	// acquisition frame rate
	if( nodes->isAvailable( ACQUISITION_FRAME_RATE ) )
	{
		pAct = new CPropertyAction( this, &CGigECamera::OnFrameRate );
		double d = 0;
		nodes->get( d, ACQUISITION_FRAME_RATE );
		nRet = CreateProperty( MM::g_Keyword_Frame_Rate, boost::lexical_cast<std::string>( d ).c_str(), MM::Float, 
								!nodes->isWritable( ACQUISITION_FRAME_RATE ), pAct );
		if( nRet != DEVICE_OK )
			return nRet;
		double low, high;
		low = nodes->getMin( ACQUISITION_FRAME_RATE );
		high = nodes->getMax( ACQUISITION_FRAME_RATE );
		SetPropertyLimits( MM::g_Keyword_Frame_Rate, low, high );
	}
	else if( nodes->isAvailable( ACQUISITION_FRAME_RATE_STR ) )
	{
		pAct = new CPropertyAction( this, &CGigECamera::OnFrameRate );
		std::string d;
		nodes->get( d, ACQUISITION_FRAME_RATE_STR );
		nRet = CreateProperty( MM::g_Keyword_Frame_Rate, d.c_str(), MM::String, 
								!nodes->isWritable( ACQUISITION_FRAME_RATE_STR ), pAct );
		if( nRet != DEVICE_OK )
			return nRet;

		std::vector<std::string> frameRateValues;
		for( uint32_t i = 0; i <= nodes->getNumEnumEntries( ACQUISITION_FRAME_RATE_STR ) - 1; i++ )
		{
			std::string entry, displayName;
			nodes->getEnumEntry( entry, i, ACQUISITION_FRAME_RATE_STR );
			nodes->getEnumDisplayName( displayName, i, ACQUISITION_FRAME_RATE_STR );
			frameRateMap.insert( std::pair<std::string, std::string>( entry, displayName ) );
			frameRateMap.insert( std::pair<std::string, std::string>( displayName, entry ) );
			frameRateValues.push_back( displayName );
		}
		nRet = SetAllowedValues( MM::g_Keyword_Frame_Rate, frameRateValues );
		if( nRet != DEVICE_OK )
			return nRet;
	}

	// camera offset
	nRet = CreateProperty(MM::g_Keyword_Offset, "0", MM::Integer, false);
	assert(nRet == DEVICE_OK);

	// synchronize all properties
	// --------------------------
	nRet = UpdateStatus();
	if (nRet != DEVICE_OK)
		return nRet;

	// setup the buffer
	// ----------------
	nRet = ResizeImageBuffer();
	if (nRet != DEVICE_OK)
		return nRet;

	cameraInitialized = true;
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
int CGigECamera::Shutdown()
{
	int retval = DEVICE_OK;
	if( cameraOpened )
	{
		J_STATUS_TYPE rc = J_Camera_Close( hCamera );
		cameraOpened = false;
		if( rc != J_ST_SUCCESS )
		{
			LogMessage( (std::string) "camera close failed", false );
			retval = DEVICE_NATIVE_MODULE_FAILED;
		}
		J_Factory_Close( hFactory );
	}
	cameraInitialized = false;
	return retval;
}

/**
* Returns pixel data.
* Required by the MM::Camera API.
* The calling program will assume the size of the buffer based on the values
* obtained from GetImageBufferSize(), which in turn should be consistent with
* values returned by GetImageWidth(), GetImageHight() and GetImageBytesPerPixel().
* The calling program also assumes that camera never changes the size of
* the pixel buffer on its own. In other words, the buffer can change only if
* appropriate properties are set (such as binning, pixel type, etc.)
*/
const unsigned char* CGigECamera::GetImageBuffer()
{
	MM::MMTime readoutTime(readoutUs_);
	while (readoutTime > (GetCurrentMMTime() - readoutStartTime_)) {}
	return img_.GetPixels();
}


/**
* Sets the camera Region Of Interest.
* Required by the MM::Camera API.
* This command will change the dimensions of the image.
* Depending on the hardware capabilities the camera may not be able to configure the
* exact dimensions requested - but should try do as close as possible.
* If the hardware does not have this capability the software should simulate the ROI by
* appropriately cropping each frame.
* This GigE implementation ignores the position coordinates and just crops the buffer.
* @param x - top-left corner coordinate
* @param y - top-left corner coordinate
* @param xSize - width
* @param ySize - height
*/
int CGigECamera::SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize)
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
int CGigECamera::GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize)
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
int CGigECamera::ClearROI()
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
double CGigECamera::GetExposure() const
{
	char buf[MM::MaxStrLength];
	int ret = GetProperty(MM::g_Keyword_Exposure, buf);
	if (ret != DEVICE_OK)
		return 0.0;
	return atof(buf);
}


/**
* Returns the current binning factor.
* Required by the MM::Camera API.
*/
int CGigECamera::GetBinning() const
{
	char buf[MM::MaxStrLength];
	int ret = GetProperty(MM::g_Keyword_Binning, buf);
	if (ret != DEVICE_OK)
		return 1;
	return atoi(buf);
}




///////////////////////////////////////////////////////////////////////////////
// Private CGigECamera methods
///////////////////////////////////////////////////////////////////////////////

int CGigECamera::SetAllowedBinning() 
{
	int64_t min, max, inc;
	std::vector<std::string> vValues, hValues, binValues;

	// vertical binning
	if( nodes->isAvailable( BINNING_VERTICAL ) )
	{
		min = nodes->getMin( BINNING_VERTICAL );
		max = nodes->getMax( BINNING_VERTICAL );
		inc = nodes->getIncrement( BINNING_VERTICAL );
		for( int64_t i = min; i <= max; i += inc )
			vValues.push_back( boost::lexical_cast<std::string>( i ) );
		SetAllowedValues( MM::g_Keyword_Binning_Vertical, vValues );
	}

	// horizontal binning
	if( nodes->isAvailable( BINNING_HORIZONTAL ) )
	{
		min = nodes->getMin( BINNING_HORIZONTAL );
		max = nodes->getMax( BINNING_HORIZONTAL );
		inc = nodes->getIncrement( BINNING_HORIZONTAL );
		for( int64_t i = min; i <= max; i += inc )
			hValues.push_back( boost::lexical_cast<std::string>( i ) );
		SetAllowedValues( MM::g_Keyword_Binning_Horizontal, hValues );
	}

	if( vValues.empty() && hValues.empty() )
		binValues.push_back( "1" );
	else if( vValues.empty() )
		binValues = hValues;
	else if( hValues.empty() )
		binValues = vValues;
	else
		std::set_union( vValues.begin(), vValues.begin() + vValues.size(), 
						hValues.begin(), hValues.begin() + hValues.size(), binValues.begin() );
	return SetAllowedValues( MM::g_Keyword_Binning, binValues );
}


/**
* Sync internal image buffer size to the chosen property values.
*/
int CGigECamera::ResizeImageBuffer()
{
	int64_t h, w;
	nodes->get( w, WIDTH );
	nodes->get( h, HEIGHT );

	char buf[MM::MaxStrLength];
	int ret;
	ret = GetProperty(MM::g_Keyword_PixelType, buf);
	if (ret != DEVICE_OK)
		return ret;

	int byteDepth = 1;
	if (strcmp(buf, g_PixelType_16bit) == 0)
		byteDepth = 2;

	img_.Resize( (unsigned int) w, (unsigned int) h, byteDepth);

	if( buffer != NULL )
		delete buffer;
	bufferSizeBytes = (size_t) ( w * h * LARGEST_PIXEL_IN_BYTES );
	buffer = new unsigned char[ bufferSizeBytes ];
	return DEVICE_OK;
}


void CGigECamera::enumerateAllNodesToLog()
{
	uint32_t nNodes;
	J_STATUS_TYPE retval;
	NODE_HANDLE hNode;
	int8_t sNodeName[256];
	uint32_t size;
	J_NODE_ACCESSMODE access;
	// Get the number of nodes
	retval = J_Camera_GetNumOfNodes(hCamera, &nNodes);
	if (retval == J_ST_SUCCESS)
	{
		LogMessage( (std::string) "All nodes:" + CDeviceUtils::ConvertToString( (long) nNodes ) + " nodes found" );

		// Run through the list of nodes and print out the names
		for (uint32_t index = 0; index < nNodes; ++index)
		{
			// Get node handle
			retval = J_Camera_GetNodeByIndex(hCamera, index, &hNode);
			if (retval == J_ST_SUCCESS)
			{
				// Get node name
				size = sizeof(sNodeName);
				retval = J_Node_GetName(hNode, sNodeName, &size, 0);
				J_Node_GetAccessMode( hNode, &access );
				if (retval == J_ST_SUCCESS)
				{
					// Print out the name
					LogMessage( (std::string) "-- (node " + boost::lexical_cast<std::string>( index ) + ") " + (std::string) sNodeName 
								+ " access:  " + boost::lexical_cast<std::string>( access ) );
				}
			}
		}
	}
}


