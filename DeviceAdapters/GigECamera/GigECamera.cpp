///////////////////////////////////////////////////////////////////////////////
// FILE:          GigECamera.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   An adapter for Gigbit-Ethernet cameras using an
//                SDK from JAI, Inc.  Users and developers will
//                need to download and install the JAI SDK and control tool.
//
// AUTHOR:        David Marshburn, UNC-CH, marshbur@cs.unc.edu, Jan. 2011
//

#include "GigECamera.h"
#include "GigENodes.h"
#include <cstdio>
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>
#include <algorithm>
#include <iterator>

#include <boost/lexical_cast.hpp>


double g_IntensityFactor_ = 1.0;

// External names used used by the rest of the system
// to load particular device from the "GigECamera.dll" library
const char* g_CameraDeviceName = "GigE camera adapter";


///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////

MODULE_API void InitializeModuleData()
{
	RegisterDevice(g_CameraDeviceName, MM::CameraDevice, "GigE camera");
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
color_(false),
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
buffer_( NULL ),
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
	if( buffer_ != NULL )
		delete buffer_;
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

	retval = J_Factory_Open(cstr2cjai(""), &hFactory);
	if( retval != J_ST_SUCCESS )
	{
		LogMessage( "JAI GigE factory failed", false );
		return DEVICE_NATIVE_MODULE_FAILED;
	}

	//Update camera list
	bool8_t bHasChange;
	retval = J_Factory_UpdateCameraList(hFactory, &bHasChange);
	if (retval != J_ST_SUCCESS)
	{
		LogMessage( "Could not update camera list!", false);
		return DEVICE_NATIVE_MODULE_FAILED;
	}

	char sVersion[J_FACTORY_INFO_SIZE];
	uint32_t size = sizeof(sVersion);
	retval = J_Factory_GetInfo(FAC_INFO_VERSION, str2jai(sVersion), &size);
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

	LogMessage( "found " + boost::lexical_cast<std::string>(nCameras) + " cameras", false );

	// Run through the list of found cameras
	char sCameraID[J_CAMERA_ID_SIZE];
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
			size = (uint32_t) sizeof(sCameraID);
			retval = J_Factory_GetCameraIDByIndex(hFactory, index, str2jai(sCameraID), &size);
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
		nRet = CreateProperty(g_Keyword_Camera_Choice, selectedCamera.c_str(), MM::String, false, pAct);
		if (nRet != DEVICE_OK)
			return nRet;
		nRet = SetAllowedValues(g_Keyword_Camera_Choice, correctedCameras);
		if (nRet != DEVICE_OK)
			return nRet;

	}

	// open a camera
	std::map<std::string,std::string>::iterator i = cameraNameMap.find( selectedCamera );
	if( i == cameraNameMap.end() )
		return DEVICE_NATIVE_MODULE_FAILED;
	this->cameraName = (*i).second;
	LogMessage( (std::string) "Opening camera:  " + this->cameraName );
	retval = J_Camera_Open( hFactory, cstr2jai(this->cameraName.c_str()), &hCamera );
	if( retval != J_ST_SUCCESS )
	{
			LogMessage( (std::string) "camera open failed (" + this->cameraName + ")", false );
			return DEVICE_NATIVE_MODULE_FAILED;
	}
	LogMessage( "camera open succeeded", false );
	cameraOpened = true;

   // EnumerateAllNodesToLog();
   EnumerateAllFeaturesToLog();

	struct Logger
	{
		CGigECamera* d;
		Logger( CGigECamera* d ) : d( d ) {}
		void operator()( const std::string& msg ) { d->LogMessage( msg, true ); }
	} logger(this);
	this->nodes = new GigENodes( hCamera, logger );

	// make sure the exposure mode is set to "Timed", if possible.
	// not an error if we can't set this, since it's only a recommended parameter.
	LogMessage( "Setting exposure mode to Timed" );
	retval = J_Camera_SetValueString( hCamera, NODE_NAME_EXPMODE,NODE_NAME_TIMED );
	if( retval != J_ST_SUCCESS )
	{
		LogMessage( "Failed to set exposure mode; trying shutter mode instead" );
		// some older cameras (from JAI?) use "ShutterMode" instead of "ExposureMode"
		retval = J_Camera_SetValueString( hCamera, NODE_NAME_SHUTTERMODE, NODE_NAME_EXPOSURETIMEABS );
		if( retval != J_ST_SUCCESS )
		{
			LogMessage( "Failed to set ExposureMode to Timed and ShutterMode to ExposureTimeAbs" );
		}
		else
		{
			LogMessage( "Successfully set ShutterMode to ExposureTimeAbs" );
		}
	}
	else
	{
		LogMessage( "Successfully set ExposureMode to Timed" );
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
		int64_t low, high;
		if( nodes->getMin( WIDTH, low ) && nodes->getMax( WIDTH, high ) )
		{
			pAct = new CPropertyAction( this, &CGigECamera::OnImageWidth );
			nRet = CreateProperty( g_Keyword_Image_Width, CDeviceUtils::ConvertToString( (long) dim ), MM::Integer, !nodes->isWritable( WIDTH ), pAct );
			if( nRet != DEVICE_OK )
				return nRet;
			if ( low < high )
				nRet = SetPropertyLimits( g_Keyword_Image_Width, (double) low, (double) high );
			else
			{
				ClearAllowedValues( g_Keyword_Image_Width );
				nRet = AddAllowedValue( g_Keyword_Image_Width, boost::lexical_cast<std::string>(low).c_str() );
			}
			if( nRet != DEVICE_OK )
				return nRet;
		}
	}

	if( nodes->get( dim, HEIGHT ) )
	{
		int64_t low, high;
		if( nodes->getMin( HEIGHT, low ) && nodes->getMax( HEIGHT, high ) )
		{
			pAct = new CPropertyAction( this, &CGigECamera::OnImageHeight );
			nRet = CreateProperty( g_Keyword_Image_Height, CDeviceUtils::ConvertToString( (long) dim ), MM::Integer, !nodes->isWritable( HEIGHT ), pAct );
			if( nRet != DEVICE_OK )
				return nRet;
			if ( low < high )
				nRet = SetPropertyLimits( g_Keyword_Image_Height, (double) low, (double) high );
			else
			{
				ClearAllowedValues( g_Keyword_Image_Height );
				nRet = AddAllowedValue( g_Keyword_Image_Height, boost::lexical_cast<std::string>(low).c_str() );
			}
			if( nRet != DEVICE_OK )
				return nRet;
		}
	}

	// width max and height max
	if( nodes->isAvailable( WIDTH_MAX ) )
	{
		nodes->get( dim, WIDTH_MAX );
		pAct = new CPropertyAction( this, &CGigECamera::OnImageWidthMax );
		nRet = CreateProperty( g_Keyword_Image_Width_Max, CDeviceUtils::ConvertToString( (long) dim ), MM::Integer, !nodes->isWritable( WIDTH_MAX ), pAct );
		if( nRet != DEVICE_OK )
			return nRet;
	}

	if( nodes->isAvailable( HEIGHT_MAX ) )
	{
		nodes->get( dim, HEIGHT_MAX );
		pAct = new CPropertyAction( this, &CGigECamera::OnImageHeightMax );
		nRet = CreateProperty( g_Keyword_Image_Height_Max, CDeviceUtils::ConvertToString( (long) dim ), MM::Integer, !nodes->isWritable( HEIGHT_MAX ), pAct );
		if( nRet != DEVICE_OK )
			return nRet;
	}

	// sensor width and sensor height
	if( nodes->isAvailable( SENSOR_WIDTH ) )
	{
		nodes->get( dim, SENSOR_WIDTH );
		nRet = CreateProperty( g_Keyword_Sensor_Width, CDeviceUtils::ConvertToString( (long) dim ), MM::Integer, !nodes->isWritable( SENSOR_WIDTH ) );
		if( nRet != DEVICE_OK )
			return nRet;
	}

	if( nodes->isAvailable( SENSOR_HEIGHT ) )
	{
		nodes->get( dim, SENSOR_HEIGHT );
		nRet = CreateProperty( g_Keyword_Sensor_Height, CDeviceUtils::ConvertToString( (long) dim ), MM::Integer, !nodes->isWritable( SENSOR_HEIGHT ) );
		if( nRet != DEVICE_OK )
			return nRet;
	}


	// binning.  
	nRet = SetUpBinningProperties();
	if (nRet != DEVICE_OK)
		return nRet;

	// pixel type
	// note that, in the GenICam standard, pixel format and bit depth are rolled into one
	// we do not obmit pixel types anymore but we should for 64bit RGB, since mm cant handle them
	LogMessage( (std::string) "Getting all PixelTypeValues" );
	std::vector<std::string> pixelTypeValues;
	for( uint32_t i = 0; i < nodes->getNumEnumEntries( PIXEL_FORMAT ); i++ )
	{
		std::string entry, displayName;
		nodes->getEnumEntry( entry, i, PIXEL_FORMAT );
		nodes->getEnumDisplayName( displayName, i, PIXEL_FORMAT );

		LogMessage( (std::string) "PixelTypeValue:  " + entry + " diplayName: " + displayName);

		pixelFormatMap.insert( std::pair<std::string, std::string>( entry, displayName ) );
		pixelFormatMap.insert( std::pair<std::string, std::string>( displayName, entry ) );
		pixelTypeValues.push_back( displayName );
	}

	pAct = new CPropertyAction (this, &CGigECamera::OnPixelType);
	std::string px, dn;
	nodes->get( px, PIXEL_FORMAT );
	std::map<std::string, std::string>::iterator it = pixelFormatMap.find( px );
	if( it == pixelFormatMap.end() )
		dn = px;
	else
		dn = it->second;
	LogMessage( (std::string) "Setting PixelType to " + dn );
	nRet = CreateProperty( MM::g_Keyword_PixelType, dn.c_str(), MM::String, !nodes->isWritable( PIXEL_FORMAT ), pAct );
	if (nRet != DEVICE_OK)
		return nRet;

	nRet = SetAllowedValues(MM::g_Keyword_PixelType, pixelTypeValues);
	if (nRet != DEVICE_OK)
		return nRet;

	//Acquisition mode
	std::vector<std::string> acquistionmodeValues;
	for( uint32_t i = 0; i <= nodes->getNumEnumEntries( ACQUISITION_MODE ) - 1; i++ )
	{
		std::string entry, displayName;
		nodes->getEnumEntry( entry, i, ACQUISITION_MODE );
		nodes->getEnumDisplayName( displayName, i, ACQUISITION_MODE );
		acqModeMap.insert( std::pair<std::string, std::string>( entry, displayName ) );
		acqModeMap.insert( std::pair<std::string, std::string>( displayName, entry ) );
		acquistionmodeValues.push_back( displayName );
	}

	pAct = new CPropertyAction (this, &CGigECamera::onAcquisitionMode);
	std::string px1, dn1;
	nodes->get( px1, ACQUISITION_MODE );
	it = acqModeMap.find( px1 );
	if( it == acqModeMap.end() )
		dn1 = px1;
	else
		dn1 = it->second;
   nRet = CreateProperty( g_Keyword_Acquisition_Mode, dn1.c_str(), MM::String, !nodes->isWritable( ACQUISITION_MODE ), pAct );
	if (nRet != DEVICE_OK)
		return nRet;

   nRet = SetAllowedValues( g_Keyword_Acquisition_Mode, acquistionmodeValues );
	if (nRet != DEVICE_OK)
		return nRet;

	// exposure
	useExposureTime = useExposureTimeAbs = useExposureTimeAbsInt = false;
	// note that exposure in GenICam has units of us; umanager has units of ms
	double exposureLowUs, exposureHighUs;
	if( nodes->isAvailable( EXPOSURE_TIME ) && nodes->getMin( EXPOSURE_TIME, exposureLowUs ) && nodes->getMax( EXPOSURE_TIME, exposureHighUs ) )
	{
		useExposureTime = true;
		LogMessage( "Using ExposureTime (double) for Exposure", true );
		pAct = new CPropertyAction( this, &CGigECamera::OnExposure );
		double e;
		nodes->get( e, EXPOSURE_TIME );
		nRet = CreateProperty( MM::g_Keyword_Exposure, CDeviceUtils::ConvertToString( e / 1000.0 ), MM::Float, !nodes->isWritable( EXPOSURE_TIME ), pAct );
		if (nRet != DEVICE_OK)
			return nRet;
		SetPropertyLimits( MM::g_Keyword_Exposure, exposureLowUs / 1000.0, exposureHighUs / 1000.0 );
	}
	else if( nodes->isAvailable( EXPOSURE_TIME_ABS ) && nodes->getMin( EXPOSURE_TIME_ABS, exposureLowUs ) && nodes->getMax( EXPOSURE_TIME_ABS, exposureHighUs ) )
	{
		useExposureTimeAbs = true;
		LogMessage( "Using ExposureTimeAbs (double) for Exposure", true );
		pAct = new CPropertyAction( this, &CGigECamera::OnExposure );
		double e;
		nodes->get( e, EXPOSURE_TIME_ABS );
		nRet = CreateProperty( MM::g_Keyword_Exposure, CDeviceUtils::ConvertToString( (double) e / 1000.0 ), MM::Float, !nodes->isWritable( EXPOSURE_TIME_ABS ), pAct );
		if (nRet != DEVICE_OK)
			return nRet;
		SetPropertyLimits( MM::g_Keyword_Exposure, exposureLowUs / 1000.0, exposureHighUs / 1000.0 );
	}
	else if( nodes->isAvailable( EXPOSURE_TIME_ABS_INT ) )
	{
		int64_t exposureLowUs, exposureHighUs;
		if( nodes->getMin( EXPOSURE_TIME_ABS_INT, exposureLowUs ) && nodes->getMax( EXPOSURE_TIME_ABS_INT, exposureHighUs ) )
		{
			useExposureTimeAbsInt = true;
			LogMessage( "Using ExposureTimeAbs (int) for Exposure", true );
			pAct = new CPropertyAction( this, &CGigECamera::OnExposure );
			int64_t e;
			nodes->get( e, EXPOSURE_TIME_ABS_INT );
			// create this as a float variable since GenICam has units of us
			nRet = CreateProperty( MM::g_Keyword_Exposure, CDeviceUtils::ConvertToString( (double) e / 1000.0 ), MM::Float, !nodes->isWritable( EXPOSURE_TIME_ABS_INT ), pAct );
			if (nRet != DEVICE_OK)
				return nRet;
			SetPropertyLimits( MM::g_Keyword_Exposure, static_cast<double>( exposureLowUs ) / 1000.0, static_cast<double>( exposureHighUs ) / 1000.0 );
		}
	}
	if( !HasProperty( MM::g_Keyword_Exposure ) )
	{
		LogMessage( "No known method to set exposure available, cannot create Exposure property." );
	}

	// camera gain
	double gainLow, gainHigh;
	if( nodes->isAvailable( GAIN ) && nodes->getMin( GAIN, gainLow ) && nodes->getMax( GAIN, gainHigh ) )
	{
		pAct = new CPropertyAction( this, &CGigECamera::OnGain );
		double d = 0;
		nodes->get( d, GAIN );
		nRet = CreateProperty( MM::g_Keyword_Gain,  CDeviceUtils::ConvertToString( d ), MM::Float, !nodes->isWritable( GAIN ), pAct );
		if (nRet != DEVICE_OK)
			return nRet;
		SetPropertyLimits( MM::g_Keyword_Gain, gainLow, gainHigh );
	}
	else if( nodes->isAvailable( GAIN_RAW ) )
	{
		int64_t gainLow, gainHigh;
		if( nodes->getMin( GAIN_RAW, gainLow ) && nodes->getMax( GAIN_RAW, gainHigh ) )
		{
			pAct = new CPropertyAction( this, &CGigECamera::OnGain );
			int64_t d = 0;
			nodes->get( d, GAIN_RAW );
			nRet = CreateProperty( MM::g_Keyword_Gain,  CDeviceUtils::ConvertToString( (long) d ), MM::Integer, !nodes->isWritable( GAIN_RAW ), pAct );
			if (nRet != DEVICE_OK)
				return nRet;
			SetPropertyLimits( MM::g_Keyword_Gain, static_cast<double>( gainLow ), static_cast<double>( gainHigh ) );
		}
	}

	// camera temperature
	if( nodes->isAvailable( TEMPERATURE ) )
	{
		double low, high;
		if ( nodes->getMin( TEMPERATURE, low ) && nodes->getMax( TEMPERATURE, high ) )
		{
			pAct = new CPropertyAction( this, &CGigECamera::OnTemperature );
			double d = 0;
			nodes->get( d, TEMPERATURE );
			nRet = CreateProperty( MM::g_Keyword_CCDTemperature, CDeviceUtils::ConvertToString( d ), MM::Float, !nodes->isWritable( TEMPERATURE ), pAct );
			if (nRet != DEVICE_OK)
				return nRet;
			SetPropertyLimits( MM::g_Keyword_CCDTemperature, low, high );
		}
	}

	// acquisition frame rate
	double framerateLow, framerateHigh;
	if( nodes->isAvailable( ACQUISITION_FRAME_RATE ) && nodes->getMin( ACQUISITION_FRAME_RATE, framerateLow ) && nodes->getMax( ACQUISITION_FRAME_RATE, framerateHigh ) )
	{
		pAct = new CPropertyAction( this, &CGigECamera::OnFrameRate );
		double d = 0;
		nodes->get( d, ACQUISITION_FRAME_RATE );
		nRet = CreateProperty( g_Keyword_Frame_Rate, boost::lexical_cast<std::string>( d ).c_str(), MM::Float, 
								!nodes->isWritable( ACQUISITION_FRAME_RATE ), pAct );
		if( nRet != DEVICE_OK )
			return nRet;
		SetPropertyLimits( g_Keyword_Frame_Rate, framerateLow, framerateHigh );
	}


	// camera offset
	nRet = CreateProperty(MM::g_Keyword_Offset, "0", MM::Integer, false);
	assert(nRet == DEVICE_OK);

	// synchronize all properties
	// --------------------------
	LogMessage( "Synchronizing properties", true );
	nRet = UpdateStatus();
	if (nRet != DEVICE_OK)
		return nRet;

	// setup the buffer
	// ----------------
	LogMessage( "Setting up image buffer", true );
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
	while (GetCurrentMMTime() - readoutStartTime_ < MM::MMTime(readoutUs_)) {CDeviceUtils::SleepMs(5);}
	return img_.GetPixels();
}

unsigned CGigECamera::GetBitDepth() const
{
	return color_ ? 8 : bitDepth_;
}

unsigned int CGigECamera::GetNumberOfComponents() const
{
	return color_ ? 4 : 1;
}

 int CGigECamera::GetComponentName(unsigned comp, char* name)
 {
	if ( comp > 4 )
	{
		name = "invalid comp";
		return DEVICE_ERR;
	}

	std::string rgba ("RGBA");
	CDeviceUtils::CopyLimitedString(name, &rgba.at(comp) );

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

int CGigECamera::SetUpBinningProperties()
{
	// note that the GenICam spec separates vertical and horizontal binning and does
	// not provide a single, unified binning property.  the various OnBinning methods
	// will do their best to provide this illusion of a unified binning when possible.

	// We always provide the Binning property, regardless of support for non-unity binnings.
	CPropertyAction *pAct = new CPropertyAction( this, &CGigECamera::OnBinning );
	int nRet = CreateProperty( MM::g_Keyword_Binning, "1", MM::Integer, false, pAct );
	if (DEVICE_OK != nRet)
		return nRet;

	int64_t bin, min, max, inc;
	std::vector<std::string> vValues, hValues, binValues;

	// vertical binning
	if( nodes->isAvailable( BINNING_VERTICAL ) && nodes->getMin( BINNING_VERTICAL, min ) &&
		nodes->getMax( BINNING_VERTICAL, max ) && nodes->getIncrement( BINNING_VERTICAL, inc ) )
	{
		nodes->get( bin, BINNING_VERTICAL );
		pAct = new CPropertyAction( this, &CGigECamera::OnBinningV );
		nRet = CreateProperty( g_Keyword_Binning_Vertical, CDeviceUtils::ConvertToString( (long) bin ), MM::Integer, !nodes->isWritable( BINNING_VERTICAL ), pAct );
		if (DEVICE_OK != nRet)
			return nRet;

		LogMessage("Vertical Binning min = " + boost::lexical_cast<std::string>(min) +
				"; max = " + boost::lexical_cast<std::string>(max) +
				"; increment = " + boost::lexical_cast<std::string>(inc), true);
		for( int64_t i = min; i <= max; i += inc )
			vValues.push_back( boost::lexical_cast<std::string>( i ) );
		SetAllowedValues( g_Keyword_Binning_Vertical, vValues );
	}

	// horizontal binning
	if( nodes->isAvailable( BINNING_HORIZONTAL ) && nodes->getMin( BINNING_HORIZONTAL, min ) &&
		nodes->getMax( BINNING_HORIZONTAL, max ) && nodes->getIncrement( BINNING_HORIZONTAL, inc ) )
	{
		nodes->get( bin, BINNING_HORIZONTAL );
		pAct = new CPropertyAction( this, &CGigECamera::OnBinningH );
		nRet = CreateProperty( g_Keyword_Binning_Horizontal, CDeviceUtils::ConvertToString( (long) bin ), MM::Integer, !nodes->isWritable( BINNING_HORIZONTAL ), pAct );
		if (DEVICE_OK != nRet)
			return nRet;

		LogMessage("Horizontal Binning min = " + boost::lexical_cast<std::string>(min) +
				"; max = " + boost::lexical_cast<std::string>(max) +
				"; increment = " + boost::lexical_cast<std::string>(inc), true);
		for( int64_t i = min; i <= max; i += inc )
			hValues.push_back( boost::lexical_cast<std::string>( i ) );
		SetAllowedValues( g_Keyword_Binning_Horizontal, hValues );
	}

	// Now that we have the vertical and horizontal binning value ranges,
	// we can determine the possible uniform binning values.

	if( vValues.empty() && hValues.empty() )
		binValues.push_back( "1" );
	else if( vValues.empty() )
		binValues = hValues;
	else if( hValues.empty() )
		binValues = vValues;
	else {
		binValues.reserve( vValues.size() + hValues.size() );
		std::set_union( vValues.begin(), vValues.end(),
				hValues.begin(), hValues.end(),
				std::back_inserter(binValues) );
	}
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

	uint32_t byteDepth;
	int ret = testIfPixelFormatResultsInColorImage(byteDepth);
	if (ret != DEVICE_OK)
		return ret;

	// Resize the snap buffer
	img_.Resize( (unsigned int) w, (unsigned int) h, byteDepth);

	// Also resize the sequence acquisition temporary buffer
	if( buffer_ != NULL )
		delete buffer_;
	bufferSizeBytes = (size_t) ( w * h * byteDepth);
	buffer_ = new unsigned char[ bufferSizeBytes ];

	LogMessage("ResizeImageBuffer: bitDepth: " + boost::lexical_cast<std::string>(bitDepth_) +
		" color mode: " + boost::lexical_cast<std::string>(color_) );
	LogMessage("ResizeImageBuffer: byteDepth " + boost::lexical_cast<std::string>(byteDepth) +
		" w " + boost::lexical_cast<std::string>(w)  + " h " + boost::lexical_cast<std::string>(w)  + " bufferSizeBytes " +
		boost::lexical_cast<std::string>(bufferSizeBytes) );

	return DEVICE_OK;
}
int CGigECamera::testIfPixelFormatResultsInColorImage(uint32_t &byteDepth)
{
	J_STATUS_TYPE retval;

	// Get pixelformat from the camera as string
	char s[J_FACTORY_INFO_SIZE];
	uint32_t size = sizeof(s);
	retval = J_Camera_GetValueString(hCamera,NODE_NAME_PIXELFORMAT, str2jai(s) ,&size);
	if( retval != J_ST_SUCCESS )
	{
		LogMessage("cant get pixel_format in string");
		return DEVICE_ERR;
	}
	std::string pt(s);

	// Get pixelformat from the camera to calculate bitdepth_
	int64_t int64Val;
	retval = J_Camera_GetValueInt64(hCamera, NODE_NAME_PIXELFORMAT, &int64Val);
	if (retval != J_ST_SUCCESS)
	{	LogMessage("cant get pixel_format in int");
		return 	DEVICE_ERR;
	}

	// Calculate number of bits (not bytes) per pixel using macro
	uint32_t bpp = J_BitsPerPixel(int64Val);
	color_ = false;
	bitDepth_ = bpp;
	byteDepth = (bpp - 1) / 8 + 1;

	// If we find RGB in the pixel type string, we set byteDepth and bitDepth higher (not necessary correct fe RGB64bit)
	if( pt.find( "RGB" ) != std::string::npos && bpp == 24)
	{
		LogMessage("We found RGB pixel type, setting color mode true");
		color_ = true;
		bitDepth_ = 8;
		byteDepth = 4;
	}
	else if (pt.find( "RGB" ) != std::string::npos && bpp > 24)
	{
		LogMessage("We found RGB pixel type but with more than 24bit. Since mm cant handle this type, we just ship a grayscale image");
		color_ = false;
		bitDepth_ = (bpp - 1) / 3 + 1; // Just in case, take ceiling if not divisible by 3
		byteDepth = (bitDepth_ - 1) / 8 + 1;
	}

	return DEVICE_OK;
}
void CGigECamera::EnumerateAllNodesToLog()
{
	uint32_t nNodes;
	J_STATUS_TYPE retval;
	NODE_HANDLE hNode;
	char sNodeName[256];
	uint32_t size;
	J_NODE_ACCESSMODE access;
	// Get the number of nodes
	retval = J_Camera_GetNumOfNodes(hCamera, &nNodes);
	if (retval == J_ST_SUCCESS)
	{
		LogMessage( (std::string) "All nodes:" + CDeviceUtils::ConvertToString( (long) nNodes ) + " nodes found", true );

		// Run through the list of nodes and print out the names
		for (uint32_t index = 0; index < nNodes; ++index)
		{
			// Get node handle
			retval = J_Camera_GetNodeByIndex(hCamera, index, &hNode);
			if (retval == J_ST_SUCCESS)
			{
				// Get node name
				size = sizeof(sNodeName);
				retval = J_Node_GetName(hNode, str2jai(sNodeName), &size, 0);
				J_Node_GetAccessMode( hNode, &access );
				if (retval == J_ST_SUCCESS)
				{
					// Print out the name
					LogMessage( (std::string) "(" + boost::lexical_cast<std::string>( index ) + ") node " + sNodeName 
								+ ": access = " + boost::lexical_cast<std::string>( access ), true );
				}
			}
		}

      LogMessage( "End of list of all nodes.", true );
	}
}


void CGigECamera::EnumerateAllFeaturesToLog()
{
   LogMessage( "Listing all feature nodes, hierarchically", true );
   EnumerateAllFeaturesToLog( cstr2jai( J_ROOT_NODE ), 0 );
   LogMessage( "End of feature node listing", true );
}


void CGigECamera::EnumerateAllFeaturesToLog( int8_t* parentNodeName, int indentCount )
{
   // Not all GenICam nodes are controllable features. A typical camera may
   // have thousands of nodes, and we are usually only interested in the
   // subset that represent controllable features.

   const std::string indent( indentCount, ' ' );

   uint32_t nSubNodes;
   J_STATUS_TYPE retval = J_Camera_GetNumOfSubFeatures( hCamera, parentNodeName, &nSubNodes );
   if ( retval != J_ST_SUCCESS )
   {
      LogMessage( indent + "Cannot get number of subnodes under node " + cjai2cstr( parentNodeName ), true );
      return;
   }

   LogMessage( indent + "List of feature nodes under node " + cjai2cstr( parentNodeName ) +
      ": " + boost::lexical_cast<std::string>( nSubNodes ) + " nodes found", true );

   for ( uint32_t index = 0; index < nSubNodes; ++index )
   {
      NODE_HANDLE hNode;
      J_STATUS_TYPE retval = J_Camera_GetSubFeatureByIndex( hCamera, parentNodeName, index, &hNode );
      if ( retval != J_ST_SUCCESS )
      {
         LogMessage( indent + "Cannot get node at index " + boost::lexical_cast<std::string>( index ), true );
         continue;
      }

      int8_t nodeName[256];
      uint32_t size = sizeof( nodeName );
      retval = J_Node_GetName( hNode, nodeName, &size, 0 );
      if ( retval != J_ST_SUCCESS )
      {
         LogMessage( indent + "Cannot get name of node at index " + boost::lexical_cast<std::string>( index ), true );
         continue;
      }

      J_NODE_TYPE nodeType;
      retval = J_Node_GetType( hNode, &nodeType );
      if ( retval != J_ST_SUCCESS )
      {
         LogMessage( indent + "Cannot get type of node " + cjai2cstr( nodeName ), true );
         continue;
      }

      J_NODE_ACCESSMODE accessMode;
      retval = J_Node_GetAccessMode( hNode, &accessMode );
      if ( retval != J_ST_SUCCESS )
      {
         LogMessage( indent + "Cannot get access mode of node " + cjai2cstr( nodeName ) + " (type = " +
            StringForNodeType( nodeType ) + ")", true );
      }

      std::string extraInfo;

      uint32_t nProperties;
      retval = J_Node_GetNumOfProperties( hNode, &nProperties );
      if ( retval == J_ST_SUCCESS )
      {
         extraInfo += ", n_properties = " + boost::lexical_cast<std::string>( nProperties );
      }

      uint32_t isSelector;
      retval = J_Node_GetIsSelector( hNode, &isSelector );
      if ( retval == J_ST_SUCCESS )
      {
         extraInfo += ", is_selector = ";
         extraInfo += ( isSelector ? "true" : "false" );
      }

      LogMessage( indent + "(" + boost::lexical_cast<std::string>( index ) + ") node " + cjai2cstr( nodeName ) +
         ": type = " + StringForNodeType( nodeType ) + ", access mode = " + StringForAccessMode( accessMode ) +
         extraInfo, true );

      int8_t nodeDesc[1024];
      size = sizeof( nodeDesc );
      retval = J_Node_GetDescription( hNode, nodeDesc, &size );
      if ( retval == J_ST_SUCCESS )
      {
         LogMessage( indent + "  Description: " + cjai2cstr( nodeDesc ), true );
      }

      if ( nodeType == J_ICategory )
      {
         EnumerateAllFeaturesToLog( nodeName, indentCount + 2 );
      }
   }
}


std::string CGigECamera::StringForNodeType( J_NODE_TYPE nodeType )
{
   switch ( nodeType )
   {
      case J_UnknowNodeType: return "UnknowNodeType";
      case J_INode: return "INode";
      case J_ICategory: return "ICategory";
      case J_IInteger: return "IInteger";
      case J_IEnumeration: return "IEnumeration";
      case J_IEnumEntry: return "IEnumEntry";
      case J_IMaskedIntReg: return "IMaskedIntReg";
      case J_IRegister: return "IRegister";
      case J_IIntReg: return "IIntReg";
      case J_IFloat: return "IFloat";
      case J_IFloatReg: return "IFloatReg";
      case J_ISwissKnife: return "ISwissKnife";
      case J_IIntSwissKnife: return "IIntSwissKnife";
      case J_IIntKey: return "IIntKey";
      case J_ITextDesc: return "ITextDesc";
      case J_IPort: return "IPort";
      case J_IConfRom: return "IConfRom";
      case J_IAdvFeatureLock: return "IAdvFeatureLock";
      case J_ISmartFeature: return "ISmartFeature";
      case J_IStringReg: return "IStringReg";
      case J_IBoolean: return "IBoolean";
      case J_ICommand: return "ICommand";
      case J_IConverter: return "IConverter";
      case J_IIntConverter: return "IIntConverter";
      case J_IChunkPort: return "IChunkPort";
      case J_INodeMap: return "INodeMap";
      case J_INodeMapDyn: return "INodeMapDyn";
      case J_IDeviceInfo: return "IDeviceInfo";
      case J_ISelector: return "ISelector";
      case J_IPortConstruct: return "IPortConstruct";
      default: return "(Unexpected node type " + boost::lexical_cast<std::string>( nodeType ) + ")";
   }
}


std::string CGigECamera::StringForAccessMode( J_NODE_ACCESSMODE accessMode )
{
   switch ( accessMode )
   {
      case NI: return "not implemented";
      case NA: return "not available";
      case WO: return "write only";
      case RO: return "read only";
      case RW: return "read and write";
      default: return "(Unexpected access mode " + boost::lexical_cast<std::string>( accessMode ) + ")";
   }
}
