//////////////////////////////////////////////////////////////////////////////////////
//
//
//	ScionCam -	mm_manager device adapter for scion 1394 cameras
//
//	Version	1.3
//
//	Copyright 2004-2009 Scion Corporation  		(Win XP/Vista, OS/X Platforms)
//
//	Implemented using Micro-Manager DemoCamera module as a baseline
//	Micro-Manager is copyright of University of California, San Francisco.
//
//////////////////////////////////////////////////////////////////////////////////////

///////////////////////////////////////////////////////////////////////////////
// FILE:          ScionCamera.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Scion Firewire Camera Device Adapter 
//                
// AUTHOR:        Scion Corporation, 2009
//
// COPYRIGHT:     Scion Corporation, 2004-2009
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
// CVS:           $Id: ScionCamera.c,v 1.33 2009/08/19 22:40:57 nenad Exp $
//


#ifdef WIN32
   #define WIN32_LEAN_AND_MEAN
   #include <windows.h>
   #define snprintf _snprintf 
#endif

#include "ScionCamera.h"
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>
using namespace std;

extern	void	sLogMessage(char msg[]);
extern	void	sLogReset(void);

// External names used used by the rest of the system
// to load particular device from the "DemoCamera.dll" library
const char* g_CameraDeviceName = "ScionCam";

const char* g_CameraCFW1308M = "Scion_CFW1308M";
const char* g_CameraCFW1308C = "Scion_CFW1308C";
const char* g_CameraCFW1310M = "Scion_CFW1310M";
const char* g_CameraCFW1310C = "Scion_CFW1310C";
const char* g_CameraCFW1312M = "Scion_CFW1312M";
const char* g_CameraCFW1312C = "Scion_CFW1312C";

const char* g_CameraCFW1608M = "Scion_CFW1608M";
const char* g_CameraCFW1608C = "Scion_CFW1608C";
const char* g_CameraCFW1610M = "Scion_CFW1610M";
const char* g_CameraCFW1610C = "Scion_CFW1610C";
const char* g_CameraCFW1612M = "Scion_CFW1612M";
const char* g_CameraCFW1612C = "Scion_CFW1612C";

const char* g_CameraCFW1008M = "Scion_CFW1008M";
const char* g_CameraCFW1008C = "Scion_CFW1008C";
const char* g_CameraCFW1010M = "Scion_CFW1010M";
const char* g_CameraCFW1010C = "Scion_CFW1010C";
const char* g_CameraCFW1012M = "Scion_CFW1012M";
const char* g_CameraCFW1012C = "Scion_CFW1012C";

const char* g_Keyword_CameraSerialNo = "CameraSerialNo";

const char*	g_Keyword_StreamMode = "StreamMode";
const char* g_StreamMode_Stream = "Stream";
const char* g_StreamMode_NoStream = "NoStream";

const char*	g_Keyword_BlackLevel = "BlackLevel";
const char*	g_Keyword_Contrast = "Contrast";

const char*	g_Keyword_RedBoost = "RedBoost";
const char* g_Keyword_BlueBoost = "BlueBoost";
const char* g_Boost_On = "On";
const char* g_Boost_Off = "Off";

const char*	g_Keyword_RedGain = "RedGain";
const char* g_Keyword_BlueGain = "BlueGain";
const char*	g_Keyword_GreenGain = "GreenGain";

const char*	g_Keyword_TestMode = "TestMode";
const char* g_TestMode_On = "On";
const char* g_TestMode_Off = "Off";

const char*	g_Keyword_PreviewMode = "PreviewMode";
const char* g_PreviewMode_On = "On";
const char* g_PreviewMode_Off = "Off";

const char*	g_Keyword_BinMode = "BinMode";
const char* g_BinMode_On = "On";
const char* g_BinMode_Off = "Off";

const char*	g_Keyword_GammaMode = "GammaMode";
const char* g_GammaMode_On = "On";
const char* g_GammaMode_Off = "Off";

const char*	g_Keyword_Gamma = "Gamma";

const char*	g_Keyword_ReadoutSpeed = "ReadoutSpeed";
const char* g_ReadoutSpeed_28Mhz = "28Mhz";
const char* g_ReadoutSpeed_14Mhz = "14Mhz";
const char* g_ReadoutSpeed_7Mhz = "7Mhz";


// constants for naming pixel types (allowed values of the "PixelType" property)
const char* g_PixelType_8bit = "8bit";
const char* g_PixelType_10bit = "10bit";
const char* g_PixelType_12bit = "12bit";
const char* g_PixelType_16bit = "16bit";


// TODO: linux entry code

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

MODULE_API void InitializeModuleData()
{
	// generic open for scion cameras
	AddAvailableDeviceName(g_CameraDeviceName, "Scion 1394 Camera");

	// get list of available cameras - then advertise the interface
   /*
	AddAvailableDeviceName(g_CameraCFW1308M, "Scion CFW-1308 1394 Grayscale Camera");
	AddAvailableDeviceName(g_CameraCFW1308C, "Scion CFW-1308 1394 Color Camera");
	AddAvailableDeviceName(g_CameraCFW1310M, "Scion CFW-1310 1394 Grayscale Camera");
	AddAvailableDeviceName(g_CameraCFW1310C, "Scion CFW-1310 1394 Color Camera");
	AddAvailableDeviceName(g_CameraCFW1312M, "Scion CFW-1312 1394 Grayscale Camera");
	AddAvailableDeviceName(g_CameraCFW1312C, "Scion CFW-1312 1394 Color Camera");

	AddAvailableDeviceName(g_CameraCFW1608M, "Scion CFW-1608 1394 Grayscale Camera");
	AddAvailableDeviceName(g_CameraCFW1608C, "Scion CFW-1608 1394 Color Camera");
	AddAvailableDeviceName(g_CameraCFW1610M, "Scion CFW-1610 1394 Grayscale Camera");
	AddAvailableDeviceName(g_CameraCFW1610C, "Scion CFW-1610 1394 Color Camera");
	AddAvailableDeviceName(g_CameraCFW1612M, "Scion CFW-1612 1394 Grayscale Camera");
	AddAvailableDeviceName(g_CameraCFW1612C, "Scion CFW-1612 1394 Color Camera");

	AddAvailableDeviceName(g_CameraCFW1008M, "Scion CFW-1008 1394 Grayscale Camera");
	AddAvailableDeviceName(g_CameraCFW1008C, "Scion CFW-1008 1394 Color Camera");
	AddAvailableDeviceName(g_CameraCFW1010M, "Scion CFW-1010 1394 Grayscale Camera");
	AddAvailableDeviceName(g_CameraCFW1010C, "Scion CFW-1010 1394 Color Camera");
	AddAvailableDeviceName(g_CameraCFW1012M, "Scion CFW-1012 1394 Grayscale Camera");
	AddAvailableDeviceName(g_CameraCFW1012C, "Scion CFW-1012 1394 Color Camera");
   */
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

	// decide which device class to create based on the deviceName parameter
	if (strcmp(deviceName, g_CameraDeviceName) == 0)
		{return new CScionCamera();}

   // Retain the following for backwards compatibility:
	else if (strcmp(deviceName, g_CameraCFW1308M) == 0)
		{return new CScionCamera(SFW_CFW1308M);}
	else if (strcmp(deviceName, g_CameraCFW1308C) == 0)
		{return new CScionCamera(SFW_CFW1308C);}
	else if (strcmp(deviceName, g_CameraCFW1310M) == 0)
		{return new CScionCamera(SFW_CFW1310M);}
	else if (strcmp(deviceName, g_CameraCFW1310C) == 0)
		{return new CScionCamera(SFW_CFW1310C);}
	else if (strcmp(deviceName, g_CameraCFW1312M) == 0)
		{return new CScionCamera(SFW_CFW1312M);}
	else if (strcmp(deviceName, g_CameraCFW1312C) == 0)
		{return new CScionCamera(SFW_CFW1312C);}
	
	else if (strcmp(deviceName, g_CameraCFW1608M) == 0)
		{return new CScionCamera(SFW_CFW1608M);}
	else if (strcmp(deviceName, g_CameraCFW1608C) == 0)
		{return new CScionCamera(SFW_CFW1608C);}
	else if (strcmp(deviceName, g_CameraCFW1610M) == 0)
		{return new CScionCamera(SFW_CFW1610M);}
	else if (strcmp(deviceName, g_CameraCFW1610C) == 0)
		{return new CScionCamera(SFW_CFW1610C);}
	else if (strcmp(deviceName, g_CameraCFW1612M) == 0)
		{return new CScionCamera(SFW_CFW1612M);}
	else if (strcmp(deviceName, g_CameraCFW1612C) == 0)
		{return new CScionCamera(SFW_CFW1612C);}

	else if (strcmp(deviceName, g_CameraCFW1008M) == 0)
		{return new CScionCamera(SFW_CFW1008M);}
	else if (strcmp(deviceName, g_CameraCFW1008C) == 0)
		{return new CScionCamera(SFW_CFW1008C);}
	else if (strcmp(deviceName, g_CameraCFW1010M) == 0)
		{return new CScionCamera(SFW_CFW1010M);}
	else if (strcmp(deviceName, g_CameraCFW1010C) == 0)
		{return new CScionCamera(SFW_CFW1010C);}
	else if (strcmp(deviceName, g_CameraCFW1012M) == 0)
		{return new CScionCamera(SFW_CFW1012M);}
	else if (strcmp(deviceName, g_CameraCFW1012C) == 0)
		{return new CScionCamera(SFW_CFW1012C);}
 

   // ...supplied name not recognized
   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
// CScionCamera implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~

CScionObjects::CScionObjects() :
			cam_interface(NULL),
			camera(NULL),
			capture(NULL),
			cam_config(NULL),
			image_info(NULL),
			b_image(NULL)
{
// construct sfwcore objects using interface

cam_interface = Create_sfw_interface();
camera = Create_device();
capture = Create_capture();
cam_config = Create_cam_settings();
image_info = Create_image_binfo();
b_image = Create_image();
}

// destruct sfwcore objects
CScionObjects::~CScionObjects()
{
if(b_image)			{Delete_image(b_image);}
if(image_info)		{Delete_image_binfo(image_info);}
if(cam_config)		{Delete_cam_settings(cam_config);}
if(capture)			{Delete_capture(capture);}
if(camera)			{Delete_device(camera);}
if(cam_interface)	{Delete_sfw_interface(cam_interface);}
}


/**
 * CScionCamera constructor.
 * Setup default all variables and create device properties required to exist
 * before intialization. In this case, no such properties were required. All
 * properties will be created in the Initialize() method.
 *
 * As a general guideline Micro-Manager devices do not access hardware in the
 * the constructor. We should do as little as possible in the constructor and
 * perform most of the initialization in the Initialize() method.
 */
 
CScionCamera::CScionCamera() : 
   initialized_(false),
   busy_(false),
   ctp(0),
   sequenceRunning_(false),
   stopOnOverflow_(false),
   snap_in_progress(false),
   reload_config(false),
   size_modified(false),
   image_counter_(0),
   sequenceLength_(0),
   interval_ms_(0.0),
   type(0),
   typeName(g_CameraDeviceName),
   index(0),
   stream_mode(0),
   restart_stream(0),
   frame_period_28mhz(1),
   frame_period_14mhz(1),
   frame_period_7mhz(1),
   d_gain(0.0),
   d_max_gain(0.0),
   d_min_gain(0.0),
   d_bl(0.0),
   d_red_gain(0.0),
   d_green_gain(0.0),
   d_blue_gain(0.0),
   d_gamma(0.0),
   d_exposure(0.0),
   pview_width(0),
   pview_height(0),
   max_width(0),
   max_height(0),
   image_width(0),
   image_height(0),
   image_rowbytes(0),
   image_depth(0),
   image_components(0),
   image_component_size(0),
   image_pixel_size(0),
   roi_width(0),
   roi_height(0),
   roix(0),
   roiy(0),
   roi_showing(0),
   image_valid(0)
{
roi.left = 0;
roi.right = 0;
roi.top = 0;
roi.bottom = 0;

ii.image_valid = 0;
ii.bp = NULL;
ii.buffer_no = 0;
ii.depth = 0;
ii.format = 0;
ii.image_mode = 0;
ii.pi = NULL;

const char* cameraType = "Camera Type";
CPropertyAction* pAct = new CPropertyAction (this, &CScionCamera::OnCameraType);
CreateProperty(cameraType, g_CameraDeviceName, MM::String, false, pAct, true);

AddAllowedValue(cameraType,  g_CameraDeviceName);

AddAllowedValue(cameraType,  g_CameraCFW1308M);
AddAllowedValue(cameraType,  g_CameraCFW1308C);
AddAllowedValue(cameraType,  g_CameraCFW1310M);
AddAllowedValue(cameraType,  g_CameraCFW1310C);
AddAllowedValue(cameraType,  g_CameraCFW1312M);
AddAllowedValue(cameraType,  g_CameraCFW1312C);

AddAllowedValue(cameraType,  g_CameraCFW1608M);
AddAllowedValue(cameraType,  g_CameraCFW1608C);
AddAllowedValue(cameraType,  g_CameraCFW1610M);
AddAllowedValue(cameraType,  g_CameraCFW1610C);
AddAllowedValue(cameraType,  g_CameraCFW1612M);
AddAllowedValue(cameraType,  g_CameraCFW1612C);

AddAllowedValue(cameraType,  g_CameraCFW1008M);
AddAllowedValue(cameraType,  g_CameraCFW1008C);
AddAllowedValue(cameraType,  g_CameraCFW1010M);
AddAllowedValue(cameraType,  g_CameraCFW1010C);
AddAllowedValue(cameraType,  g_CameraCFW1012M);
AddAllowedValue(cameraType,  g_CameraCFW1012C);
 
// call the base class method to set-up default error codes/messages
InitializeDefaultErrorMessages();

// create thread for sequence capture
ctp = new SequenceThread(this);
}


/**
 * CScionCamera constructor.
 */
CScionCamera::CScionCamera(unsigned int camera_id) : 
   initialized_(false),
   busy_(false),
   ctp(0),
   sequenceRunning_(false),
   stopOnOverflow_(false),
   snap_in_progress(false),
   reload_config(false),
   size_modified(false),
   image_counter_(0),
   sequenceLength_(0),
   interval_ms_(0.0),
   type(camera_id),
   index(0),
   stream_mode(0),
   restart_stream(0),
   d_gain(0.0),
   d_max_gain(0.0),
   d_min_gain(0.0),
   d_bl(0.0),
   d_red_gain(0.0),
   d_green_gain(0.0),
   d_blue_gain(0.0),
   d_gamma(0.0),
   d_exposure(0.0),
   pview_width(0),
   pview_height(0),
   max_width(0),
   max_height(0),
   image_width(0),
   image_height(0),
   image_rowbytes(0),
   image_depth(0),
   image_components(0),
   image_component_size(0),
   image_pixel_size(0),
   roi_width(0),
   roi_height(0),
   roix(0),
   roiy(0),
   roi_showing(0),
   image_valid(0)
{
roi.left = 0;
roi.right = 0;
roi.top = 0;
roi.bottom = 0;

ii.image_valid = 0;
ii.bp = NULL;
ii.buffer_no = 0;
ii.depth = 0;
ii.format = 0;
ii.image_mode = 0;
ii.pi = NULL;

// call the base class method to set-up default error codes/messages
InitializeDefaultErrorMessages();

// create thread for sequence capture
ctp = new SequenceThread(this);
}


/**
 * CScionCamera destructor.
 * If this device used as intended within the Micro-Manager system,
 * Shutdown() will be always called before the destructor. But in any case
 * we need to make sure that all resources are properly released even if
 * Shutdown() was not called.
 */
CScionCamera::~CScionCamera()
{
// must make sure resources are released

if(initialized_)
	{
	Shutdown();
	}
}

/**
 * Obtains device name.
 * Required by the MM::Device API.
 */
void CScionCamera::GetName(char* name) const
{
// We just return the name we use for referring to this
// device adapter.
CDeviceUtils::CopyLimitedString(name, g_CameraDeviceName);
}

/**
 * Tells us if device is still processing asynchronous command.
 * Required by the MM:Device API.
 */
bool CScionCamera::Busy()
{
return busy_;
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
int CScionCamera::Initialize()
{
#ifdef	LOG_ENABLED
sLogMessage("initialize\r\n");
#endif

if (initialized_)
	{return DEVICE_OK;}

// intialize camera control data

roi.left = 0;
roi.right = 0;
roi.top = 0;
roi.bottom = 0;

image_valid = 0;

ii.image_valid = 0;
ii.bp = NULL;
ii.buffer_no = 0;
ii.depth = 0;
ii.format = 0;
ii.image_mode = 0;
ii.pi = NULL;


// open camera

if(so.cam_interface->open() != SFW_OK)
	{
#ifdef	LOG_ENABLED
	sLogMessage("could not open camera driver interface\r\n");
#endif
	return DEVICE_ERR;
	}

if(type != 0)
	{
	// open specified camera type

	index = 0;
	if(so.camera->open(type, index) != SFW_OK)
		{
		// could not find camera or open

#ifdef	LOG_ENABLED
		sLogMessage("could not open specified\r\n");
#endif

		return DEVICE_ERR;
		}
	}
else
	{
	// open first camera found

	index = 0;
	if(so.camera->open_any(type, index) != SFW_OK)
		{
		// could not find camera or open

#ifdef	LOG_ENABLED
		sLogMessage("could not open any\r\n");
#endif
		return DEVICE_ERR;
		}
	}

#ifdef	LOG_ENABLED
sLogMessage("camera open\r\n");
LogMessage("camera is open\r\n");
#endif

// 
// get user preference information and load into data structures
//

// initalize camera settings

// load default values for camera
so.cam_config->init(so.camera->get_camera_handle());

so.cam_config->set_invert_image(0);		// do not invert image

d_gain = so.cam_config->get_gain_value();
d_max_gain = so.cam_config->get_max_gain_value();			
d_min_gain = so.cam_config->get_min_gain_value();			
d_bl = so.cam_config->get_bl_value();				
d_red_gain = so.cam_config->get_red_gain_value();
d_green_gain = so.cam_config->get_green_gain_value();
d_blue_gain = so.cam_config->get_blue_gain_value();		
d_gamma = so.cam_config->get_gamma();
d_exposure = so.cam_config->get_expsoure_value();


//
// initialize capture control
//

if(!so.capture->init(so.camera, so.cam_config))
	{
	// error on initialization of firewire camera info

#ifdef	LOG_ENABLED
	sLogMessage("could not perform local camera initialization\r\n");
#endif
	
	so.camera->close();		// close camera
	return DEVICE_ERR;
	}

so.capture->set_no_retries(5);

// prepare for capture based on current settings
// turn off multi-frame option and turn off live option
so.capture->setup(so.cam_config, 0, 0);
reload_config = false;


// set image and window metrics based on hardware & default modes

size_modified = false;
so.camera->get_info(SFW_CCD_WIDTH, &max_width);
so.camera->get_info(SFW_CCD_HEIGHT, &max_height);
so.camera->get_info(SFW_PREVIEW_WIDTH, &pview_width);
so.camera->get_info(SFW_PREVIEW_HEIGHT, &pview_height);

if(so.cam_config->get_bin_mode())
	{
	image_width = max_width / 2;
	image_height = max_height / 2;
	}
else if(so.cam_config->get_preview_mode())
	{
	image_width = pview_width;
	image_height = pview_height;
	}
else
	{
	image_width = max_width;
	image_height = max_height;
	}


if(max_width == 1600)
{
   frame_period_28mhz = 92;
   frame_period_14mhz = 183;
   frame_period_7mhz = 367;
}
else if(max_width == 1024)
{
   frame_period_28mhz = 38;
   frame_period_14mhz = 75;
   frame_period_7mhz = 150;
}
else
{
   frame_period_28mhz = 67;
   frame_period_14mhz = 133;
   frame_period_7mhz = 266;
}



//
// get buffers for internal use
//

// get image buffer for roi implementation
// allocate image buffer (since micro-manager is grayscale only force this
//							to use grayscale image)

if(so.b_image->create_image(0, 1, so.camera->get_max_depth(),
		bgr_order, so.camera->get_max_width(), so.camera->get_max_height()) != 1)
	{
	// could not create image buffer

	so.camera->close();
	return DEVICE_ERR;
	}


// set property list
// -----------------

int nRet = SetCameraPropertyList();
if (DEVICE_OK != nRet)
	{
	so.camera->close();
	return nRet;
	}


// initialize capture

// prepare for capture based on current settings
// turn off multi-frame option and turn off live option
so.capture->setup(so.cam_config, 0, 0);
reload_config = false;
restart_stream = 1;

initialized_ = true;

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
int CScionCamera::Shutdown()
{
#ifdef	LOG_ENABLED
sLogMessage("shutdown\r\n");
#endif

if(initialized_)
	{
	// free rsources and close camera

	if(so.b_image->get_bp() != NULL)
		{
		so.b_image->close_image();
		}

	so.camera->close();
	}

initialized_ = false;
return DEVICE_OK;
}

/**
 * Performs exposure and grabs a single image.
 * Required by the MM::Camera API.
 */
int CScionCamera::SnapImage()
{
int		cc;

if(reload_config || size_modified)
	{
	reload_config = false;
	so.capture->abort_frame();
	so.capture->setup(so.cam_config, 0, 0);

	if(size_modified)
		{
		size_modified = false;
		}

	restart_stream = 1;
	}

if(stream_mode)
	{
	// double buffer captures

	if(restart_stream)
		{
		// restart the stream

#ifdef	LOG_ENABLED
		sLogMessage("restart stream\r\n");
#endif
		so.capture->abort_frame();

		restart_stream = 0;
		cc = start_frame();
		
		cc = complete_frame();
		if(cc == 0)
			{
			// got frame

			return DEVICE_OK;
			}
		else if(cc == 1)
			{
			// abort requested

			}
		}
	else
		{
		if(IsCapturing())	{cc = complete_frame();}
		else				{cc = available_frame();}

		if(cc == 0)
			{
			// got frame

			return DEVICE_OK;
			}
		else if(cc == 1)
			{
			// abort requested

			}
		}
	}
else
	{
	// start a capture - block for exposure time and then return
	cc = start_snap();

   // Wait until a full frame has been exposed plus the differential between exposure time and a full frame/
   CDeviceUtils::SleepMs(GetWaitTime());

	if(cc == 0)
		{
		// got frame
		return DEVICE_OK;
		}
	else if(cc == 1)					
		{
		// abort requested

		}
	}

#ifdef	LOG_ENABLED
sLogMessage("error doing snap\r\n");
#endif

restart_stream = 1;
return DEVICE_ERR;
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
const unsigned char* CScionCamera::GetImageBuffer()
{
unsigned int	uu_no_components;
unsigned int	dformat;
RECT			drect;

unsigned int	i_width;
unsigned int	i_height;
unsigned int	i_component_size;

unsigned int	cc;

// finish image capture
if(snap_in_progress)
	{
	snap_in_progress = false;

	cc = complete_snap();
	if(cc != 0)
		{
#ifdef	LOG_ENABLED
		sLogMessage("error completing capture\r\n");
#endif	
		return(0);			// return no image
		}
	}


// got image - prep image for micro-manager

uu_no_components = 1;		// umanager only supports grayscale at this time!

#ifdef	LOG_ENABLED
//	sLogMessage("get image buffer cmd\r\n");
#endif

if(ii.image_valid && (ii.bp != NULL))
	{
	if(roi_showing)
		{
		// roi is seleted - build roi buffer (grayscale or color image) 

		image_rowbytes = roi_width * ii.pi->get_component_size() * uu_no_components;
		SetRect(&drect, 0, 0, roi_width, roi_height);

		if(ii.depth == 12)		{dformat = SFWF_GRAY16_12;}
		else if(ii.depth == 10)	{dformat = SFWF_GRAY16;}
		else					{dformat = SFWF_GRAY8;}

		so.b_image->modify_image(dformat, roi_width, roi_height);

		// copy image region, convert to grayscale as necessary
		if(so.camera->copy_roi(ii.buffer_no, roi, dformat,
				(unsigned char *)so.b_image->get_bp(), 
				drect, image_rowbytes) != SFW_OK)
			{
			// error building roi image

#ifdef	LOG_ENABLED
			sLogMessage("error building roi image\r\n");
#endif
			}

#ifdef	LOG_ENABLED
		sLogMessage("roi image built\r\n");
#endif
		return ((unsigned char *)so.b_image->get_bp());
		}
	else if (ii.image_mode == 1)
		{
		// color image - no roi

		i_width = ii.pi->get_width();
		i_height = ii.pi->get_height();
		i_component_size = ii.pi->get_component_size();

		image_rowbytes = i_width * i_component_size * uu_no_components;
		SetRect(&drect, 0, 0, i_width, i_height);

		if(ii.depth == 12)		{dformat = SFWF_GRAY16_12;}
		else if(ii.depth == 10)	{dformat = SFWF_GRAY16;}
		else					{dformat = SFWF_GRAY8;}

		so.b_image->modify_image(dformat, i_width, i_height);

#ifdef	LOG_ENABLED
		{
			char msg[256];
			wsprintf(msg, "w = %d, h  = %d, cs = %d, rb = %d, format = %d, bp %x\r\n",
						i_width, i_height, i_component_size, image_rowbytes,
						dformat, so.b_image->get_bp());
			sLogMessage(msg);
		}
#endif

		// copy image region, convert to grayscale as necessary
		if(so.camera->copy_frame(ii.buffer_no, dformat,
				(unsigned char *)so.b_image->get_bp(),
				drect, image_rowbytes) != SFW_OK)
			{
			// error building grayscale image

#ifdef	LOG_ENABLED
			sLogMessage("error building grayscale image\r\n");
#endif
			}

#ifdef	LOG_ENABLED
		sLogMessage("grayscale image built\r\n");
#endif
		return ((unsigned char *)so.b_image->get_bp());
		}
	else
		{
		// return buffer address

		return (ii.bp);
		}
	}
else
	{
	// no image

	return 0;
	}
}

/**
 * Calculates time we have to wait for a fresh image
 */
long CScionCamera::GetWaitTime() const
{
   long frame_period = 0;

   if(so.cam_config->get_rate() == SFW_FR_28MHZ)
   {
      frame_period = frame_period_28mhz;
   }
   else if(so.cam_config->get_rate() == SFW_FR_14MHZ)
   {
      frame_period = frame_period_14mhz;
   }
   else
   {
      frame_period = frame_period_7mhz;
   }

   long  exposure = (long)d_exposure;
   long  duration = 0;
   long  exp_start = frame_period - (exposure % frame_period);

   duration = frame_period + exp_start;

   return duration;
}





/**
 * Returns image buffer X-size in pixels.
 * Required by the MM::Camera API.
 */
unsigned CScionCamera::GetImageWidth() const
{
if(ii.bp != NULL)
	{
#ifdef	LOG_ENABLED
//	{
//	char msg[256];
//	sprintf(msg, "width = %d\r\n", ii.pi->get_width());
//	sLogMessage(msg);
//	}
#endif

	if(roi_showing)
		{
		// roi is seleted

		return (roi_width);
		}
	else
		{
		return (ii.pi->get_width());
		}
	}
else
	{
	return so.camera->get_width();
	}
}

/**
 * Returns image buffer Y-size in pixels.
 * Required by the MM::Camera API.
 */
unsigned CScionCamera::GetImageHeight() const
{
if(ii.bp != NULL)
	{
#ifdef	LOG_ENABLED
//	{
//	char msg[256];
//	sprintf(msg, "height = %d\r\n", ii.pi->get_height());
//	sLogMessage(msg);
//	}
#endif

	if(roi_showing)
		{
		// roi is seleted

		return (roi_height);
		}
	else
		{
		return (ii.pi->get_height());
		}
	}
else
	{
	return so.camera->get_height();
	}
}

/**
 * Returns image buffer pixel depth in bytes.
 * Required by the MM::Camera API.
 */
unsigned CScionCamera::GetImageBytesPerPixel() const
{
unsigned int	component_size;
unsigned int	uu_no_components;

uu_no_components = 1;			// uu manager only supports grayscale at this time!

if(ii.bp != NULL)
	{
	return (ii.pi->get_component_size() * uu_no_components);
	}

if(so.cam_config->get_component_depth() > 8)
	{
	component_size = 2;
	}
else
	{
	component_size = 1;
	}

return (component_size * uu_no_components);
} 

/**
 * Returns the bit depth (dynamic range) of the pixel.
 * This does not affect the buffer size, it just gives the client application
 * a guideline on how to interpret pixel values.
 * Required by the MM::Camera API.
 */
unsigned CScionCamera::GetBitDepth() const
{
if(ii.bp != NULL)
	{		
	return (ii.pi->get_component_depth());
	}
else
	{
	return (so.cam_config->get_component_depth());
	}
}


/**
 * Returns the size in bytes of the image buffer.
 * Required by the MM::Camera API.
 */
long CScionCamera::GetImageBufferSize() const
{
unsigned int	uu_no_components;
unsigned int	dpixel_size;
unsigned int	drowbytes;

uu_no_components = 1;			// uu manager only supports grayscale at this time!

if(ii.image_valid && (ii.bp != NULL))
	{
#ifdef	LOG_ENABLED
//		{
//		char msg[256];
//		sprintf(msg, "buffer size = %d, %d\r\n", ii.pos->size, ii.pos->bsize);
//		sLogMessage(msg);
//		}
#endif

	// destination will be grayscale - calc destination pixel size
	dpixel_size = ii.pi->get_component_size() * uu_no_components;

	if(roi_showing)
		{
		drowbytes = roi_width * dpixel_size;
		return (roi_height * drowbytes);
		}
	else
		{
		drowbytes = ii.pi->get_width() * dpixel_size;
		return(ii.pi->get_height() * drowbytes);
		}
	}
else
	{
	if(roi_showing)
		{
		return image_height * image_rowbytes;
		}
	else
		{
		return image_height * image_rowbytes;
		}
	}
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
int CScionCamera::SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize)
{
if (xSize == 0 && ySize == 0)
	{ 
	// effectively clear ROI

	roi_showing = 0;
	}
else
	{
	// apply ROI

	roix = x;
	roiy = y;
	roi_width = xSize;
	roi_height = ySize;

	roi.left = x;
	roi.right = x + xSize;
	roi.top = y;
	roi.bottom = y + ySize;

	roi_showing = 1;
	}

return DEVICE_OK;
}

/**
 * Returns the actual dimensions of the current ROI.
 * Required by the MM::Camera API.
 */
int CScionCamera::GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize)
{
x = 0;
y = 0;

xSize = roi.right - roi.left;
ySize = roi.bottom - roi.top;

return DEVICE_OK;
}

/**
 * Resets the Region of Interest to full frame.
 * Required by the MM::Camera API.
 */
int CScionCamera::ClearROI()
{
roi.left = 0;
roi.right = image_width;
roi.top = 0;
roi.bottom = image_height;

roi_showing = 0;
return DEVICE_OK;
}

/**
 * Returns the current exposure setting in milliseconds.
 * Required by the MM::Camera API.
 */
double CScionCamera::GetExposure() const
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
void CScionCamera::SetExposure(double exp)
{
if(exp < 10.0)	{exp = 10.0;}
else if(exp > 100000.0)	{exp = 100000;}

SetProperty(MM::g_Keyword_Exposure, CDeviceUtils::ConvertToString(exp));
}

/**
 * Returns the current binning factor.
 * Required by the MM::Camera API.
 */
int CScionCamera::GetBinning() const
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
int CScionCamera::SetBinning(int binFactor)
{
return SetProperty(MM::g_Keyword_Binning, CDeviceUtils::ConvertToString(binFactor));
}


///////////////////////////////////////////////////////////////////////////////
// CScionCamera Action handlers
///////////////////////////////////////////////////////////////////////////////

/**
 * Handles "CameraType" pre-initialization property.
 */
int CScionCamera::OnCameraType(MM::PropertyBase* pProp, MM::ActionType eAct)
{
if (eAct == MM::BeforeGet)
	{
	pProp->Set(typeName.c_str());
	}
else if (eAct == MM::AfterSet)
	{
      pProp->Get(typeName);
      if (strcmp(typeName.c_str(), g_CameraDeviceName) == 0)
         type = 0;

      else if (strcmp(typeName.c_str(), g_CameraCFW1308M) == 0)
         type = SFW_CFW1308M;
      else if (strcmp(typeName.c_str(), g_CameraCFW1308C) == 0)
         type = SFW_CFW1308C;
      else if (strcmp(typeName.c_str(), g_CameraCFW1310M) == 0)
         type = SFW_CFW1310M;
      else if (strcmp(typeName.c_str(), g_CameraCFW1310C) == 0)
         type = SFW_CFW1310C;
      else if (strcmp(typeName.c_str(), g_CameraCFW1312M) == 0)
         type = SFW_CFW1312M;
      else if (strcmp(typeName.c_str(), g_CameraCFW1312C) == 0)
         type = SFW_CFW1312C;

      else if (strcmp(typeName.c_str(), g_CameraCFW1608M) == 0)
         type = SFW_CFW1608M;
      else if (strcmp(typeName.c_str(), g_CameraCFW1608C) == 0)
         type = SFW_CFW1608C;
      else if (strcmp(typeName.c_str(), g_CameraCFW1610M) == 0)
         type = SFW_CFW1610M;
      else if (strcmp(typeName.c_str(), g_CameraCFW1610C) == 0)
         type = SFW_CFW1610C;
      else if (strcmp(typeName.c_str(), g_CameraCFW1612M) == 0)
         type = SFW_CFW1612M;
      else if (strcmp(typeName.c_str(), g_CameraCFW1612C) == 0)
         type = SFW_CFW1612C;
      
      else if (strcmp(typeName.c_str(), g_CameraCFW1008M) == 0)
         type = SFW_CFW1008M;
      else if (strcmp(typeName.c_str(), g_CameraCFW1008C) == 0)
         type = SFW_CFW1008C;
      else if (strcmp(typeName.c_str(), g_CameraCFW1010M) == 0)
         type = SFW_CFW1010M;
      else if (strcmp(typeName.c_str(), g_CameraCFW1010C) == 0)
         type = SFW_CFW1010C;
      else if (strcmp(typeName.c_str(), g_CameraCFW1012M) == 0)
         type = SFW_CFW1012M;
      else if (strcmp(typeName.c_str(), g_CameraCFW1012C) == 0)
         type = SFW_CFW1012C;
   }
   return DEVICE_OK;
}
      


/**
 * Handles "Exposure" property.
 */
int CScionCamera::OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct)
{
if (eAct == MM::BeforeGet)
	{
	pProp->Set(d_exposure);
	}

else if (eAct == MM::AfterSet)
	{
	string pvalue;
	pProp->Get(pvalue);

	double	fvalue;
	pProp->Get(fvalue);

	if(fvalue < 10.0)
		{
		fvalue = 10.0;
		pProp->Set(fvalue);
		}
	else if(fvalue > 100000.0)
		{
		fvalue = 100000.0;
		pProp->Set(fvalue);
		}

	if(d_exposure != fvalue)
		{
		// new eposure value

		d_exposure = fvalue;
		so.cam_config->set_exposure_value((DWORD)d_exposure);

		reload_config = true;
		}

	UpdateProperty(MM::g_Keyword_Exposure);
	}

return DEVICE_OK;
}


/**
 * Handles "Gain" property.
 */
int CScionCamera::OnGain(MM::PropertyBase* pProp, MM::ActionType eAct)
{
double			dvalue;

if (eAct == MM::BeforeGet)
	{
	pProp->Set(d_gain);
	}

else if (eAct == MM::AfterSet)
	{
	string pvalue;
	pProp->Get(pvalue);

	pProp->Get(dvalue);

	if(dvalue < d_min_gain)
		{
		dvalue = d_min_gain;
		pProp->Set(dvalue);
		}
	else if(dvalue > d_max_gain)
		{
		dvalue = d_max_gain;
		pProp->Set(dvalue);
		}

	if(d_gain != dvalue)
		{
		// set gain

#ifdef	LOG_ENABLED
		{
		char msg[256];
		sprintf(msg, "gain = %f\r\n", dvalue);
		sLogMessage(msg);
		}
#endif

		d_gain = dvalue;
		so.cam_config->set_gain_value(d_gain);
		so.capture->set_gain(so.cam_config->get_gain());

		UpdateProperty(MM::g_Keyword_Gain);
		}
	}

return DEVICE_OK;
}


/**
 * Handles "Contrast" property.
 */
int CScionCamera::OnContrast(MM::PropertyBase* pProp, MM::ActionType eAct)
{
long	ivalue;

if (eAct == MM::BeforeGet)
	{
	ivalue = so.cam_config->get_contrast_value();
	pProp->Set(ivalue);
	}

else if (eAct == MM::AfterSet)
	{
	string pvalue;
	pProp->Get(pvalue);

	pProp->Get(ivalue);

	if(ivalue < -127)
		{
		ivalue = -127;
		pProp->Set(ivalue);
		}
	else if(ivalue > 128)
		{
		ivalue = 128;
		pProp->Set(ivalue);
		}

	ivalue += 127;
	if(so.cam_config->get_contrast() != (unsigned long)ivalue)
		{
		// set contrast

		so.cam_config->set_contrast(ivalue);

		if(so.cam_config->get_contrast() == 127)
			{
			so.cam_config->set_contrast_mode(0);
			}
		else
			{
			so.cam_config->set_contrast_mode(1);
			}

		reload_config = true;
		}
	}

return DEVICE_OK;
}


/**
 * Handles "BlackLevel" property.
 */
int CScionCamera::OnBlackLevel(MM::PropertyBase* pProp, MM::ActionType eAct)
{
unsigned int	bl;

if (eAct == MM::BeforeGet)
	{
	pProp->Set(d_bl);
	}
else if (eAct == MM::AfterSet)
	{
	string pvalue;
	pProp->Get(pvalue);

	double	fvalue;
	pProp->Get(fvalue);

	if(fvalue < 0.0)
		{
		fvalue = 0.0;
		pProp->Set(fvalue);
		}
	else if(fvalue > 0.62)
		{
		fvalue = 0.62;
		pProp->Set(fvalue);
		}

	bl = so.cam_config->get_bl();
	so.camera->bl_to_index(fvalue, &bl);

	if(so.cam_config->get_bl() != bl)
		{
		// set black level

		so.cam_config->set_bl(bl);
		d_bl = fvalue;
		so.capture->set_bl(so.cam_config->get_bl());
		}
	}

return DEVICE_OK;
}


/**
 * Handles "StreamMode" property.
 */
int CScionCamera::OnStreamMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
if (eAct == MM::AfterSet)
	{
	string streamMode;
	pProp->Get(streamMode);

	if (streamMode.compare(g_StreamMode_Stream) == 0)
		{
		if(stream_mode == 0)
			{
			// turn streaming on

			stream_mode = 1;
			reload_config = true;
			}
		}
	else if(streamMode.compare(g_StreamMode_NoStream) == 0)
		{
		if(stream_mode != 0)
			{
			// turn streaming off

			stream_mode = 0;
			reload_config = true;
			}
		}
	else
		{
		// on error switch to default readout speed

		if(stream_mode != 0)
			{
			// turn streaming off

			stream_mode = 0;
			reload_config = true;
			}

		pProp->Set(g_StreamMode_NoStream);
		return ERR_UNKNOWN_MODE;
		}
}

return DEVICE_OK;
}

/**
 * Handles "TestMode" property.
 */
int CScionCamera::OnTestMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
if (eAct == MM::AfterSet)
	{
	string pvalue;
	pProp->Get(pvalue);

	if (pvalue.compare(g_TestMode_On) == 0)
		{
		if(so.cam_config->get_test_mode() == 0)
			{
			// turn test mode on

			so.cam_config->set_test_mode(1);
			so.capture->set_test_mode(1);
			}
		}
	else if(pvalue.compare(g_TestMode_Off) == 0)
		{
		if(so.cam_config->get_test_mode() != 0)
			{
			// turn off test mode

			so.cam_config->set_test_mode(0);
			so.capture->set_test_mode(0);
			}
		}
	else
		{
		// on error switch to default

		if(so.cam_config->get_test_mode() != 0)
			{
			// turn off test mode

			so.cam_config->set_test_mode(0);
			so.capture->set_test_mode(0);
			}

		pProp->Set(g_TestMode_Off);
		return ERR_UNKNOWN_MODE;
		}
}

return DEVICE_OK;
}


/**
 * Handles "PreviewMode" property.
 */
int CScionCamera::OnPreviewMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
#ifdef	ENABLE_SEQUENCE_ACQ
bool acquiring = IsCapturing();
#endif

if (eAct == MM::AfterSet)
	{
	string pvalue;
	pProp->Get(pvalue);

	if (so.cam_config->preview_mode_allowed())
		{
		// preview mode supported

		if (pvalue.compare(g_PreviewMode_On) == 0)
			{
			if(so.cam_config->get_bin_mode() == 1)
				{
				// must turn bin mode off to do preview

				so.cam_config->set_bin_mode(0);

				SetProperty(MM::g_Keyword_Binning, "1");
				UpdateProperty(MM::g_Keyword_Binning);
				}

			if(so.cam_config->get_preview_mode() == 0)
				{
				// turn preview mode on

#ifdef	ENABLE_SEQUENCE_ACQ
				so.cam_config->set_preview_mode(1);

				if(acquiring)	{StopSequenceAcquisition();}
				so.capture->abort_frame();
				so.capture->setup(so.cam_config, 0, 0);
				ResizeImageBuffer();
				reload_config = true;
				if(acquiring)	{RestartSequenceAcquisition();}
#else
				so.capture->abort_frame();
				so.cam_config->set_preview_mode(1);
				so.capture->setup(so.cam_config, 0, 0);
				ResizeImageBuffer();
				restart_stream = 1;
#endif
				}
			}
		else if(pvalue.compare(g_PreviewMode_Off) == 0)
			{
			if(so.cam_config->get_preview_mode() != 0)
				{
				// turn preview mode OFF

#ifdef	ENABLE_SEQUENCE_ACQ
				so.cam_config->set_preview_mode(0);

				if(acquiring)	{StopSequenceAcquisition();}
				so.capture->abort_frame();
				so.capture->setup(so.cam_config, 0, 0);
				ResizeImageBuffer();
				reload_config = true;
				if(acquiring)	{RestartSequenceAcquisition();}
#else
				so.capture->abort_frame();
				so.cam_config->set_preview_mode(0);
				so.capture->setup(so.cam_config, 0, 0);
				ResizeImageBuffer();
				restart_stream = 1;
#endif
				}
			}
		else
			{
			// on error switch to default

			if(so.cam_config->get_preview_mode() != 0)
				{
				// turn preview mode OFF

#ifdef	ENABLE_SEQUENCE_ACQ
				so.cam_config->set_preview_mode(0);

				if(acquiring)	{StopSequenceAcquisition();}
				so.capture->abort_frame();
				so.capture->setup(so.cam_config, 0, 0);
				ResizeImageBuffer();
				reload_config = true;
				if(acquiring)	{RestartSequenceAcquisition();}
#else
				so.capture->abort_frame();
				so.cam_config->set_preview_mode(0);
				so.capture->setup(so.cam_config, 0, 0);
				ResizeImageBuffer();
				restart_stream = 1;
#endif
				}

			pProp->Set(g_PreviewMode_Off);
			return ERR_UNKNOWN_MODE;
			}
		}
	else
		{
		if (pvalue.compare(g_PreviewMode_On) == 0)
			{
			pProp->Set(g_PreviewMode_Off);
			return ERR_UNKNOWN_MODE;
			}
		}
 }

return DEVICE_OK;
}


/**
 * Handles "ReadoutSpeed" property.
 */
int CScionCamera::OnReadoutSpeed(MM::PropertyBase* pProp, MM::ActionType eAct)
{
if (eAct == MM::AfterSet)
	{
	string readoutSpeed;
	pProp->Get(readoutSpeed);

	if (readoutSpeed.compare(g_ReadoutSpeed_28Mhz) == 0)
		{
		if(so.cam_config->get_rate() != SFW_FR_28MHZ)
			{
			so.cam_config->set_rate(SFW_FR_28MHZ);
			reload_config = true;
			}
		}
	else if(readoutSpeed.compare(g_ReadoutSpeed_14Mhz) == 0)
		{
		if(so.cam_config->get_rate() != SFW_FR_14MHZ)
			{
			so.cam_config->set_rate(SFW_FR_14MHZ);
			reload_config = true;
			}

		}
	else if (readoutSpeed.compare(g_ReadoutSpeed_7Mhz) == 0)
		{
		if(so.cam_config->get_rate() != SFW_FR_7MHZ)
			{
			so.cam_config->set_rate(SFW_FR_7MHZ);
			reload_config = true;
			}
		}
	else
		{
		// on error switch to default readout speed

		if(so.cam_config->get_rate() != SFW_FR_14MHZ)
			{
			so.cam_config->set_rate(SFW_FR_14MHZ);
			reload_config = true;
			}

		pProp->Set(g_ReadoutSpeed_14Mhz);
		return ERR_UNKNOWN_MODE;
		}

}

return DEVICE_OK;
}


/**
 * Handles "RedBoost" property.
 */
int CScionCamera::OnRedBoost(MM::PropertyBase* pProp, MM::ActionType eAct)
{
if (eAct == MM::AfterSet)
	{
	string pvalue;
	pProp->Get(pvalue);

	if (pvalue.compare(g_Boost_On) == 0)
		{
		so.cam_config->set_red_boost(1);
		so.capture->set_red_boost(so.cam_config->get_red_boost());
		}
	else if(pvalue.compare(g_Boost_Off) == 0)
		{
		so.cam_config->set_red_boost(0);
		so.capture->set_red_boost(so.cam_config->get_red_boost());
		}
	else
		{
		// on error switch to default

		so.cam_config->set_red_boost(0);
		so.capture->set_red_boost(so.cam_config->get_red_boost());
		pProp->Set(g_Boost_Off);
		return ERR_UNKNOWN_MODE;
		}
}

return DEVICE_OK;
}


/**
 * Handles "BlueBoost" property.
 */
int CScionCamera::OnBlueBoost(MM::PropertyBase* pProp, MM::ActionType eAct)
{
if (eAct == MM::AfterSet)
	{
	string pvalue;
	pProp->Get(pvalue);

	if (pvalue.compare(g_Boost_On) == 0)
		{
		so.cam_config->set_blue_boost(1);
		so.capture->set_blue_boost(so.cam_config->get_blue_boost());
		}
	else if(pvalue.compare(g_Boost_Off) == 0)
		{
		so.cam_config->set_blue_boost(0);
		so.capture->set_blue_boost(so.cam_config->get_blue_boost());
		}
	else
		{
		// on error switch to default

		so.cam_config->set_blue_boost(0);
		so.capture->set_blue_boost(so.cam_config->get_blue_boost());
		pProp->Set(g_Boost_Off);
		return ERR_UNKNOWN_MODE;
		}
}

return DEVICE_OK;
}

/**
 * Handles "RedGain" property.
 */
int CScionCamera::OnRedGain(MM::PropertyBase* pProp, MM::ActionType eAct)
{
unsigned int	gain;

if (eAct == MM::BeforeGet)
	{
	pProp->Set(d_red_gain);
	}
else if (eAct == MM::AfterSet)
	{
	string pvalue;
	pProp->Get(pvalue);

	double	fvalue;
	pProp->Get(fvalue);

	if(fvalue < -2.0)
		{
		fvalue = -2.0;
		pProp->Set(fvalue);
		}
	else if(fvalue > 10.2)
		{
		fvalue = 10.2;
		pProp->Set(fvalue);
		}

	so.camera->wb_gain_to_index(fvalue, &gain);

	if(so.cam_config->get_red_gain() != gain)
		{
		// set gain

		so.cam_config->set_red_gain(gain);
		d_red_gain = fvalue;
		so.capture->set_red_gain(so.cam_config->get_red_gain());
		}
	}

return DEVICE_OK;
}


/**
 * Handles "BlueGain" property.
 */
int CScionCamera::OnBlueGain(MM::PropertyBase* pProp, MM::ActionType eAct)
{
unsigned int	gain;

if (eAct == MM::BeforeGet)
	{
	pProp->Set(d_blue_gain);
	}
else if (eAct == MM::AfterSet)
	{
	string pvalue;
	pProp->Get(pvalue);

	double	fvalue;
	pProp->Get(fvalue);

	if(fvalue < -2.0)
		{
		fvalue = -2.0;
		pProp->Set(fvalue);
		}
	else if(fvalue > 10.2)
		{
		fvalue = 10.2;
		pProp->Set(fvalue);
		}

	so.camera->wb_gain_to_index(fvalue, &gain);

	if(so.cam_config->get_blue_gain() != gain)
		{
		// set gain

		so.cam_config->set_blue_gain(gain);
		d_blue_gain = fvalue;
		so.capture->set_blue_gain(so.cam_config->get_blue_gain());
		}
	}

return DEVICE_OK;
}


/**
 * Handles "GreenGain" property.
 */
int CScionCamera::OnGreenGain(MM::PropertyBase* pProp, MM::ActionType eAct)
{
unsigned int	gain;

if (eAct == MM::BeforeGet)
	{
	pProp->Set(d_green_gain);
	}
else if (eAct == MM::AfterSet)
	{
	string pvalue;
	pProp->Get(pvalue);

	double	fvalue;
	pProp->Get(fvalue);

	if(fvalue < -2.0)
		{
		fvalue = -2.0;
		pProp->Set(fvalue);
		}
	else if(fvalue > 10.2)
		{
		fvalue = 10.2;
		pProp->Set(fvalue);
		}

	so.camera->wb_gain_to_index(fvalue, &gain);

	if(so.cam_config->get_green_gain() != gain)
		{
		// set gain

		so.cam_config->set_green_gain(gain);
		d_green_gain = fvalue;
		so.capture->set_green_gain(so.cam_config->get_green_gain());
		}
	}

return DEVICE_OK;
}


/**
 * Handles "GammaMode" property.
 */
int CScionCamera::OnGammaMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
if (eAct == MM::AfterSet)
	{
	string pvalue;
	pProp->Get(pvalue);

	if (pvalue.compare(g_GammaMode_On) == 0)
		{
		if(so.cam_config->get_gamma_mode() == 0)
			{
			// turn gamma mode on

			so.cam_config->set_gamma_mode(1);
			reload_config = true;
			}
		}
	else if(pvalue.compare(g_GammaMode_Off) == 0)
		{
		if(so.cam_config->get_gamma_mode() != 0)
			{
			// turn gamma mode off

			so.cam_config->set_gamma_mode(0);
			reload_config = true;
			}
		}
	else
		{
		// on error switch to default

		if(so.cam_config->get_gamma_mode() != 0)
			{
			// turn gamma mode off

			so.cam_config->set_gamma_mode(0);
			reload_config = true;
			}

		pProp->Set(g_GammaMode_Off);
		return ERR_UNKNOWN_MODE;
		}
}

return DEVICE_OK;
}


/**
 * Handles "Gamma" property.
 */
int CScionCamera::OnGamma(MM::PropertyBase* pProp, MM::ActionType eAct)
{
if (eAct == MM::BeforeGet)
	{
	pProp->Set(d_gamma);
	}
else if (eAct == MM::AfterSet)
	{
	string pvalue;
	pProp->Get(pvalue);

	double	fvalue;
	pProp->Get(fvalue);

	if(fvalue < 0.200)
		{
		fvalue = 0.2;
		pProp->Set(fvalue);
		}
	else if(fvalue > 5.0)
		{
		fvalue = 5.0;
		pProp->Set(fvalue);
		}

	if(d_gamma != fvalue)
		{
		// new gamma value

		d_gamma = fvalue;
		so.cam_config->set_gamma(fvalue);

		if(so.cam_config->get_gamma_mode() != 0)
			{
			// gamma mode on - update camera values

			reload_config = true;
			}
		}
	}

return DEVICE_OK;
}


/**
 * Handles "Binning" property.
 */
int CScionCamera::OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct)
{
#ifdef	ENABLE_SEQUENCE_ACQ
bool acquiring = IsCapturing();
#endif

if (eAct == MM::AfterSet)
   {
      // the user just set the new value for the property, so we have to
      // apply this value to the 'hardware'.
      long binFactor;
      pProp->Get(binFactor);

#ifdef	LOG_ENABLED
	  sLogMessage("OnBinning\r\n");
#endif

      if (binFactor > 0 && binFactor < 10)
		{
		//  binning on (we only support binning of 2 x 2)

		if(binFactor == 2)
			{
			// bin 2

			if(so.cam_config->get_preview_mode() == 1)
				{
				// must turn preview mode off to do binning

#ifdef	ENABLE_SEQUENCE_ACQ
				so.cam_config->set_preview_mode(0);

				if(acquiring)	{StopSequenceAcquisition();}
				so.capture->abort_frame();
				so.capture->setup(so.cam_config, 0, 0);
				ResizeImageBuffer();
				reload_config = true;
				if(acquiring)	{RestartSequenceAcquisition();}
#else
				so.cam_config->set_preview_mode(0);
				ResizeImageBuffer();
#endif

				SetProperty(g_Keyword_PreviewMode, g_PreviewMode_Off);
				UpdateProperty(g_Keyword_PreviewMode);
				}

			if(so.cam_config->get_bin_mode() == 0)
				{
#ifdef	ENABLE_SEQUENCE_ACQ
				so.cam_config->set_bin_mode(1);

				if(acquiring)	{StopSequenceAcquisition();}
				so.capture->abort_frame();
				so.capture->setup(so.cam_config, 0, 0);
				ResizeImageBuffer();
				reload_config = true;
				if(acquiring)	{RestartSequenceAcquisition();}
#else
				so.cam_config->set_bin_mode(1);
				so.capture->abort_frame();
				so.capture->setup(so.cam_config, 0, 0);
				ResizeImageBuffer();
				restart_stream = 1;
#endif
				}
			}
		else
			{
			// bin 1 (no binning)

			if(so.cam_config->get_bin_mode() != 0)
				{
#ifdef	ENABLE_SEQUENCE_ACQ
				so.cam_config->set_bin_mode(0);

				if(acquiring)	{StopSequenceAcquisition();}
				so.capture->abort_frame();
				so.capture->setup(so.cam_config, 0, 0);
				ResizeImageBuffer();
				reload_config = true;
				if(acquiring)	{RestartSequenceAcquisition();}
#else
				so.cam_config->set_bin_mode(0);
				so.capture->abort_frame();
				so.capture->setup(so.cam_config, 0, 0);
				ResizeImageBuffer();
				restart_stream = 1;
#endif
				}
			}
		}
      else
		{
         // on failure reset default binning of 1

		if(so.cam_config->get_bin_mode() != 0)
			{
#ifdef	ENABLE_SEQUENCE_ACQ
			so.cam_config->set_bin_mode(0);

			if(acquiring)	{StopSequenceAcquisition();}
			so.capture->abort_frame();
			so.capture->setup(so.cam_config, 0, 0);
			ResizeImageBuffer();
			reload_config = true;
			if(acquiring)	{RestartSequenceAcquisition();}
#else
			so.cam_config->set_bin_mode(0);
			so.capture->abort_frame();
			so.capture->setup(so.cam_config, 0, 0);
			ResizeImageBuffer();
			restart_stream = 1;
#endif
			}

         pProp->Set(1L);
         return ERR_UNKNOWN_MODE;
		}
	}
else if (eAct == MM::BeforeGet)
	{
	// the user is requesting the current value for the property, so
	// either ask the 'hardware' or let the system return the value
	// cached in the property.
	}

return DEVICE_OK; 
}

/**
 * Handles "PixelType" property.
 */
int CScionCamera::OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct)
{
#ifdef	ENABLE_SEQUENCE_ACQ
bool acquiring = IsCapturing();
#endif

if (eAct == MM::AfterSet)
	{
	string pixelType;
	pProp->Get(pixelType);

	if (pixelType.compare(g_PixelType_8bit) == 0)
		{
#ifdef	ENABLE_SEQUENCE_ACQ
		so.cam_config->set_depth_select(0);

		if(acquiring)	{StopSequenceAcquisition();}
		so.capture->abort_frame();
		so.capture->setup(so.cam_config, 0, 0);
		ResizeImageBuffer();
		reload_config = true;
		if(acquiring)	{RestartSequenceAcquisition();}
#else
		so.cam_config->set_depth_select(0);
		so.capture->abort_frame();
		so.capture->setup(so.cam_config, 0, 0);
		ResizeImageBuffer();
		restart_stream = 1;
#endif
		}
	else if(pixelType.compare(g_PixelType_10bit) == 0)
		{
#ifdef	ENABLE_SEQUENCE_ACQ
		so.cam_config->set_depth_select(1);

		if(acquiring)	{StopSequenceAcquisition();}
		so.capture->abort_frame();
		so.capture->setup(so.cam_config, 0, 0);
		ResizeImageBuffer();
		reload_config = true;
		if(acquiring)	{RestartSequenceAcquisition();}
#else
		so.cam_config->set_depth_select(1);
		so.capture->abort_frame();
		so.capture->setup(so.cam_config, 0, 0);
		ResizeImageBuffer();
		restart_stream = 1;
#endif
		}
	else if(pixelType.compare(g_PixelType_12bit) == 0)
		{
#ifdef	ENABLE_SEQUENCE_ACQ
		so.cam_config->set_depth_select(2);

		if(acquiring)	{StopSequenceAcquisition();}
		so.capture->abort_frame();
		so.capture->setup(so.cam_config, 0, 0);
		ResizeImageBuffer();
		reload_config = true;
		if(acquiring)	{RestartSequenceAcquisition();}
#else
		so.cam_config->set_depth_select(2);
		so.capture->abort_frame();
		so.capture->setup(so.cam_config, 0, 0);
		ResizeImageBuffer();
		restart_stream = 1;
#endif
		}
	else
		{
		// on error switch to default pixel type

#ifdef	ENABLE_SEQUENCE_ACQ
		so.cam_config->set_depth_select(0);

		if(acquiring)	{StopSequenceAcquisition();}
		so.capture->abort_frame();
		so.capture->setup(so.cam_config, 0, 0);
		ResizeImageBuffer();
		reload_config = true;
		if(acquiring)	{RestartSequenceAcquisition();}
#else
		so.cam_config->set_depth_select(0);
		so.capture->abort_frame();
		so.capture->setup(so.cam_config, 0, 0);
		ResizeImageBuffer();
		restart_stream = 1;
#endif

		pProp->Set(g_PixelType_8bit);
		return ERR_UNKNOWN_MODE;
		}
}

return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Private CScionCamera methods
///////////////////////////////////////////////////////////////////////////////

/**
 * Set property list for camera
 */
int CScionCamera::SetCameraPropertyList()
{
int		nRet;
long	ivalue;
CPropertyAction *pAct;

// Stream Mode
pAct = new CPropertyAction (this, &CScionCamera::OnStreamMode);

if(stream_mode)
	{
	nRet = CreateProperty(g_Keyword_StreamMode, g_StreamMode_Stream, 
				MM::String, false, pAct);
	assert(nRet == DEVICE_OK);
	}
else
	{
	nRet = CreateProperty(g_Keyword_StreamMode, g_StreamMode_NoStream, 
				MM::String, false, pAct);
	assert(nRet == DEVICE_OK);
	}

vector<string> streamModeValues;
streamModeValues.push_back(g_StreamMode_Stream);
streamModeValues.push_back(g_StreamMode_NoStream);
nRet = SetAllowedValues(g_Keyword_StreamMode, streamModeValues);
if (DEVICE_OK != nRet)	{return nRet;}


// test mode
pAct = new CPropertyAction (this, &CScionCamera::OnTestMode);

if(so.cam_config->get_test_mode())
	{
	nRet = CreateProperty(g_Keyword_TestMode, g_TestMode_On, 
		MM::String, false, pAct);
	assert(nRet == DEVICE_OK);
	}
else
	{
	nRet = CreateProperty(g_Keyword_TestMode, g_TestMode_Off, 
		MM::String, false, pAct);
	assert(nRet == DEVICE_OK);
	}

vector<string> testModeValues;
testModeValues.push_back(g_TestMode_On);
testModeValues.push_back(g_TestMode_Off);

nRet = SetAllowedValues(g_Keyword_TestMode, testModeValues);
if (DEVICE_OK != nRet)	{return nRet;}


// preview mode
pAct = new CPropertyAction (this, &CScionCamera::OnPreviewMode);

if(so.cam_config->get_preview_mode())
	{
	nRet = CreateProperty(g_Keyword_PreviewMode, g_PreviewMode_On, 
		MM::String, false, pAct);
	assert(nRet == DEVICE_OK);
	}
else
	{
	nRet = CreateProperty(g_Keyword_PreviewMode, g_PreviewMode_Off, 
		MM::String, false, pAct);
	assert(nRet == DEVICE_OK);
	}

vector<string> previewModeValues;
if (so.cam_config->preview_mode_allowed())
	{previewModeValues.push_back(g_PreviewMode_On);}
previewModeValues.push_back(g_PreviewMode_Off);

nRet = SetAllowedValues(g_Keyword_PreviewMode, previewModeValues);
if (DEVICE_OK != nRet)	{return nRet;}


// Readout Speed
pAct = new CPropertyAction (this, &CScionCamera::OnReadoutSpeed);
nRet = CreateProperty(g_Keyword_ReadoutSpeed, g_ReadoutSpeed_14Mhz, 
				MM::String, false, pAct);
assert(nRet == DEVICE_OK);

vector<string> readoutSpeedValues;
readoutSpeedValues.push_back(g_ReadoutSpeed_28Mhz);
readoutSpeedValues.push_back(g_ReadoutSpeed_14Mhz);
readoutSpeedValues.push_back(g_ReadoutSpeed_7Mhz);
nRet = SetAllowedValues(g_Keyword_ReadoutSpeed, readoutSpeedValues);
if (DEVICE_OK != nRet)	{return nRet;}

// Name
nRet = CreateProperty(MM::g_Keyword_Name, g_CameraDeviceName, MM::String, true);
if (DEVICE_OK != nRet)	{return nRet;}

// Description
nRet = CreateProperty(MM::g_Keyword_Description, "Scion Camera Device Adapter", MM::String, true);
if (DEVICE_OK != nRet)	{return nRet;}


// CameraName
char	vendor_desc[16];
char	fw_desc[40];
char	fw_name[128];
so.camera->camera_vendor_prefix(vendor_desc, sizeof(vendor_desc));
so.camera->camera_product_desc(fw_desc, sizeof(fw_desc));
sprintf(fw_name, "%s %s", vendor_desc, fw_desc);

nRet = CreateProperty(MM::g_Keyword_CameraName, fw_name, MM::String, true);
assert(nRet == DEVICE_OK);

// CameraID (use version number?)
char	camera_prefix[40];
so.camera->camera_product_prefix(camera_prefix, sizeof(camera_prefix));
nRet = CreateProperty(MM::g_Keyword_CameraID, camera_prefix, MM::String, true);
assert(nRet == DEVICE_OK);

// CameraSerialNo
long	camera_sn = so.camera->get_serial_no();
nRet = CreateProperty(g_Keyword_CameraSerialNo, 
	CDeviceUtils::ConvertToString(camera_sn), MM::Integer, true);
assert(nRet == DEVICE_OK);

// binning
ivalue = so.cam_config->get_bin_mode() + 1;
pAct = new CPropertyAction (this, &CScionCamera::OnBinning);
nRet = CreateProperty(MM::g_Keyword_Binning, 
	CDeviceUtils::ConvertToString(ivalue), MM::Integer, false, pAct);
assert(nRet == DEVICE_OK);

vector<string> binValues;
binValues.push_back("1");
binValues.push_back("2");
nRet = SetAllowedValues(MM::g_Keyword_Binning, binValues);
if (DEVICE_OK != nRet)	{return nRet;}

// pixel type
pAct = new CPropertyAction (this, &CScionCamera::OnPixelType);

if(so.cam_config->get_depth_select() == 2 && so.camera->get_camera_depths() & SFWC_BIT_DEPTH_12)
	{
	nRet = CreateProperty(MM::g_Keyword_PixelType, 
		g_PixelType_12bit, MM::String, false, pAct);
	assert(nRet == DEVICE_OK);
	}
else if(so.cam_config->get_depth_select() == 1 && so.camera->get_camera_depths() & SFWC_BIT_DEPTH_10)
	{
	nRet = CreateProperty(MM::g_Keyword_PixelType, 
		g_PixelType_10bit, MM::String, false, pAct);
	assert(nRet == DEVICE_OK);
	}
else
	{
	nRet = CreateProperty(MM::g_Keyword_PixelType, 
		g_PixelType_8bit, MM::String, false, pAct);
	assert(nRet == DEVICE_OK);
	}

vector<string> pixelTypeValues;
pixelTypeValues.push_back(g_PixelType_8bit);
if(so.camera->get_camera_depths() & SFWC_BIT_DEPTH_10)
	{pixelTypeValues.push_back(g_PixelType_10bit);}
if(so.camera->get_camera_depths() & SFWC_BIT_DEPTH_12)
	{pixelTypeValues.push_back(g_PixelType_12bit);}

nRet = SetAllowedValues(MM::g_Keyword_PixelType, pixelTypeValues);
if (DEVICE_OK != nRet)	{return nRet;}

// exposure
pAct = new CPropertyAction (this, &CScionCamera::OnExposure);
nRet = CreateProperty(MM::g_Keyword_Exposure, 
	CDeviceUtils::ConvertToString(d_exposure), MM::Float, false, pAct);
assert(nRet == DEVICE_OK);

// camera gain
pAct = new CPropertyAction (this, &CScionCamera::OnGain);
nRet = CreateProperty(MM::g_Keyword_Gain,
	CDeviceUtils::ConvertToString(d_gain), MM::Float, false, pAct);
assert(nRet == DEVICE_OK);

// contrast
ivalue = so.cam_config->get_contrast() - 127;
pAct = new CPropertyAction (this, &CScionCamera::OnContrast);
nRet = CreateProperty(g_Keyword_Contrast, 
	CDeviceUtils::ConvertToString(ivalue), MM::Integer, false, pAct);
assert(nRet == DEVICE_OK);

// black level
pAct = new CPropertyAction (this, &CScionCamera::OnBlackLevel);
nRet = CreateProperty(g_Keyword_BlackLevel, 
	CDeviceUtils::ConvertToString(d_bl), MM::Float, false, pAct);
assert(nRet == DEVICE_OK);

// gamma mode
pAct = new CPropertyAction (this, &CScionCamera::OnGammaMode);

if(so.cam_config->get_gamma_mode())
	{
	nRet = CreateProperty(g_Keyword_GammaMode, g_GammaMode_On, 
			MM::String, false, pAct);
	assert(nRet == DEVICE_OK);
	}
else
	{
	nRet = CreateProperty(g_Keyword_GammaMode, g_GammaMode_Off, 
			MM::String, false, pAct);
	assert(nRet == DEVICE_OK);
	}

vector<string> gammaModeValues;
gammaModeValues.push_back(g_GammaMode_On);
gammaModeValues.push_back(g_GammaMode_Off);

nRet = SetAllowedValues(g_Keyword_GammaMode, gammaModeValues);
if (DEVICE_OK != nRet)	{return nRet;}

// gamma
pAct = new CPropertyAction (this, &CScionCamera::OnGamma);
nRet = CreateProperty(g_Keyword_Gamma, 
	CDeviceUtils::ConvertToString(d_gamma), MM::Float, false, pAct);
assert(nRet == DEVICE_OK);

//
// color camera properties!!!
//
if(so.camera->get_camera_type() == 1)
	{
	// red boost type
	pAct = new CPropertyAction (this, &CScionCamera::OnRedBoost);

	if(so.cam_config->get_red_boost())
		{
		nRet = CreateProperty(g_Keyword_RedBoost, g_Boost_On, 
				MM::String, false, pAct);
		assert(nRet == DEVICE_OK);
		}
	else
		{
		nRet = CreateProperty(g_Keyword_RedBoost, g_Boost_Off, 
				MM::String, false, pAct);
		assert(nRet == DEVICE_OK);
		}

	vector<string> boostValues;
	boostValues.push_back(g_Boost_On);
	boostValues.push_back(g_Boost_Off);

	nRet = SetAllowedValues(g_Keyword_RedBoost, boostValues);
	if (DEVICE_OK != nRet)	{return nRet;}

	// blue boost type
	pAct = new CPropertyAction (this, &CScionCamera::OnBlueBoost);

	if(so.cam_config->get_blue_boost())
		{
		nRet = CreateProperty(g_Keyword_BlueBoost, g_Boost_On, 
				MM::String, false, pAct);
		assert(nRet == DEVICE_OK);
		}
	else
		{
		nRet = CreateProperty(g_Keyword_BlueBoost, g_Boost_Off, 
				MM::String, false, pAct);
		assert(nRet == DEVICE_OK);
		}		

	vector<string> blueBoostValues;
	blueBoostValues.push_back(g_Boost_On);
	blueBoostValues.push_back(g_Boost_Off);

	nRet = SetAllowedValues(g_Keyword_BlueBoost, blueBoostValues);
	if (DEVICE_OK != nRet)	{return nRet;}

	// red gain
	pAct = new CPropertyAction (this, &CScionCamera::OnRedGain);
	nRet = CreateProperty(g_Keyword_RedGain, 
		CDeviceUtils::ConvertToString(d_red_gain), 
		MM::Float, false, pAct);
	assert(nRet == DEVICE_OK);

	// blue gain
	pAct = new CPropertyAction (this, &CScionCamera::OnBlueGain);
	nRet = CreateProperty(g_Keyword_BlueGain, 
		CDeviceUtils::ConvertToString(d_blue_gain), 
		MM::Float, false, pAct);
	assert(nRet == DEVICE_OK);

	// green gain
	pAct = new CPropertyAction (this, &CScionCamera::OnGreenGain);
	nRet = CreateProperty(g_Keyword_GreenGain, 
		CDeviceUtils::ConvertToString(d_green_gain), 
		MM::Float, false, pAct);
	assert(nRet == DEVICE_OK);
	}

// synchronize all properties
// --------------------------
nRet = UpdateStatus();
if (DEVICE_OK != nRet)	{return nRet;}

return DEVICE_OK;
}


/**
 * Sync internal image buffer size to the chosen property values.
 */
int CScionCamera::ResizeImageBuffer()
{
image_components = 1;
image_component_size = so.cam_config->get_component_size();
image_depth = so.cam_config->get_component_depth();
image_pixel_size = image_component_size * image_components;

image_width = so.camera->get_width();
image_height = so.camera->get_height();

if(so.cam_config->get_bin_mode())
	{
	// bin mode

	image_width = so.camera->get_bin_width();
	image_height = so.camera->get_bin_height();
	}
else if(so.cam_config->get_preview_mode())
	{
	// preview mode

	image_width = so.camera->get_preview_width();
	image_height = so.camera->get_preview_height();
	}
else
	{
	// normal mode

	image_width = so.camera->get_max_width();
	image_height = so.camera->get_max_height();
	}

image_rowbytes = image_width * image_pixel_size;

return DEVICE_OK;
}
