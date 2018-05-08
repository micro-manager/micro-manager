//-------------------------------------------------------------------------------------------------------------------
// xiAPIplus implementation file
// XIMEA Application Programming Interface Object Oriented Approach
//-------------------------------------------------------------------------------------------------------------------

#include <stdio.h>
#include "xiapiplus.h"

// forward declarations

void ReportException(XI_RETURN res, const char* location);

// -----------------------------------------------
// Error codes xiApi
// -----------------------------------------------

struct xiapi_errorcode_t
{
	XI_RETURN   code;
	const char* descr;
};

xiapi_errorcode_t xiAPIplus_errorcodes_list[]=
{
	XI_OK                             ,  "Function call succeeded",
	XI_INVALID_HANDLE                 ,  "Invalid handle",
	XI_READREG                        ,  "Register read error",
	XI_WRITEREG                       ,  "Register write error",
	XI_FREE_RESOURCES                 ,  "Freeing resources error",
	XI_FREE_CHANNEL                   ,  "Freeing channel error",
	XI_FREE_BANDWIDTH                 ,  "Freeing bandwith error",
	XI_READBLK                        ,  "Read block error",
	XI_WRITEBLK                       ,  "Write block error",
	XI_NO_IMAGE                       ,  "No image",
	XI_TIMEOUT                        ,  "Timeout",
	XI_INVALID_ARG                    ,  "Invalid arguments supplied",
	XI_NOT_SUPPORTED                  ,  "Not supported",
	XI_ISOCH_ATTACH_BUFFERS           ,  "Attach buffers error",
	XI_GET_OVERLAPPED_RESULT          ,  "Overlapped result",
	XI_MEMORY_ALLOCATION              ,  "Memory allocation error",
	XI_DLLCONTEXTISNULL               ,  "DLL context is NULL",
	XI_DLLCONTEXTISNONZERO            ,  "DLL context is non zero",
	XI_DLLCONTEXTEXIST                ,  "DLL context exists",
	XI_TOOMANYDEVICES                 ,  "Too many devices connected",
	XI_ERRORCAMCONTEXT                ,  "Camera context error",
	XI_UNKNOWN_HARDWARE               ,  "Unknown hardware",
	XI_INVALID_TM_FILE                ,  "Invalid TM file",
	XI_INVALID_TM_TAG                 ,  "Invalid TM tag",
	XI_INCOMPLETE_TM                  ,  "Incomplete TM",
	XI_BUS_RESET_FAILED               ,  "Bus reset error",
	XI_NOT_IMPLEMENTED                ,  "Not implemented",
	XI_SHADING_TOOBRIGHT              ,  "Shading is too bright",
	XI_SHADING_TOODARK                ,  "Shading is too dark",
	XI_TOO_LOW_GAIN                   ,  "Gain is too low",
	XI_INVALID_BPL                    ,  "Invalid sensor defect correction list",
	XI_BPL_REALLOC                    ,  "Error while sensor defect correction list reallocation",
	XI_INVALID_PIXEL_LIST             ,  "Invalid pixel list",
	XI_INVALID_FFS                    ,  "Invalid Flash File System",
	XI_INVALID_PROFILE                ,  "Invalid profile",
	XI_INVALID_CALIBRATION            ,  "Invalid calibration",
	XI_INVALID_BUFFER                 ,  "Invalid buffer",
	XI_INVALID_DATA                   ,  "Invalid data",
	XI_TGBUSY                         ,  "Timing generator is busy",
	XI_IO_WRONG                       ,  "Wrong operation open/write/read/close",
	XI_ACQUISITION_ALREADY_UP         ,  "Acquisition already started",
	XI_OLD_DRIVER_VERSION             ,  "Old version of device driver installed to the system.",
	XI_GET_LAST_ERROR                 ,  "To get error code please call GetLastError function.",
	XI_CANT_PROCESS                   ,  "Data cannot be processed",
	XI_ACQUISITION_STOPED             ,  "Acquisition is stopped. It needs to be started to perform operation.",
	XI_ACQUISITION_STOPED_WERR        ,  "Acquisition has been stopped with an error.",
	XI_INVALID_INPUT_ICC_PROFILE      ,  "Input ICC profile missing or corrupted",
	XI_INVALID_OUTPUT_ICC_PROFILE     ,  "Output ICC profile missing or corrupted",
	XI_DEVICE_NOT_READY               ,  "Device not ready to operate",
	XI_SHADING_TOOCONTRAST            ,  "Shading is too contrast",
	XI_ALREADY_INITIALIZED            ,  "Module already initialized",
	XI_NOT_ENOUGH_PRIVILEGES          ,  "Application does not have enough privileges (one or more app)",
	XI_NOT_COMPATIBLE_DRIVER          ,  "Installed driver is not compatible with current software",
	XI_TM_INVALID_RESOURCE            ,  "TM file was not loaded successfully from resources",
	XI_DEVICE_HAS_BEEN_RESETED        ,  "Device has been reset, abnormal initial state",
	XI_NO_DEVICES_FOUND               ,  "No Devices Found",
	XI_RESOURCE_OR_FUNCTION_LOCKED    ,  "Resource (device) or function locked by mutex",
	XI_BUFFER_SIZE_TOO_SMALL          ,  "Buffer provided by user is too small",
	XI_COULDNT_INIT_PROCESSOR         ,  "Couldnt initialize processor.",
	XI_NOT_INITIALIZED                ,  "The object/module/procedure/process being referred to has not been started.",
	XI_RESOURCE_NOT_FOUND             ,  "Resource not found(could be processor, file, item...).",
	XI_UNKNOWN_PARAM                  ,  "Unknown parameter",
	XI_WRONG_PARAM_VALUE              ,  "Wrong parameter value",
	XI_WRONG_PARAM_TYPE               ,  "Wrong parameter type",
	XI_WRONG_PARAM_SIZE               ,  "Wrong parameter size",
	XI_BUFFER_TOO_SMALL               ,  "Input buffer is too small",
	XI_NOT_SUPPORTED_PARAM            ,  "Parameter is not supported",
	XI_NOT_SUPPORTED_PARAM_INFO       ,  "Parameter info not supported",
	XI_NOT_SUPPORTED_DATA_FORMAT      ,  "Data format is not supported",
	XI_READ_ONLY_PARAM                ,  "Read only parameter",
	XI_BANDWIDTH_NOT_SUPPORTED        ,  "This camera does not support currently available bandwidth",
	XI_INVALID_FFS_FILE_NAME          ,  "FFS file selector is invalid or NULL",
	XI_FFS_FILE_NOT_FOUND             ,  "FFS file not found",
	XI_PARAM_NOT_SETTABLE             ,  "Parameter value cannot be set (might be out of range or invalid).",
	XI_SAFE_POLICY_NOT_SUPPORTED      ,  "Safe buffer policy is not supported. E.g. when transport target is set to GPU (GPUDirect).",
	XI_GPUDIRECT_NOT_AVAILABLE        ,  "GPUDirect is not available. E.g. platform isn't supported or CUDA toolkit isn't installed.",
	XI_PROC_OTHER_ERROR               ,  "Processing error - other",
	XI_PROC_PROCESSING_ERROR          ,  "Error while image processing.",
	XI_PROC_INPUT_FORMAT_UNSUPPORTED  ,  "Input format is not supported for processing.",
	XI_PROC_OUTPUT_FORMAT_UNSUPPORTED ,  "Output format is not supported for processing.",
	XI_OUT_OF_RANGE                   ,  "Parameter value is out of range",
};

// -----------------------------------------------
// xiAPIplus
// -----------------------------------------------

unsigned long xiAPIplus::GetNumberOfConnectedCameras()
{
	DWORD count=0;
	XI_RETURN res=xiGetNumberDevices(&count);
	if (res) ReportException(res,"GetNumberOfConnectedCameras");
	return count;
}

// exception handling
void ReportException(XI_RETURN res, const char* location)
{
	// generate exception
	char descr[200]="(missing_error_description)";
	int total_errors = sizeof(xiAPIplus_errorcodes_list) / sizeof(xiapi_errorcode_t);
	// find description
	for(int i=0;i<total_errors;i++)
	{
		if (xiAPIplus_errorcodes_list[i].code == res)
		{
			// found
			sprintf_s(descr,sizeof(descr),"%s (xiAPIplus_Camera::%s)",xiAPIplus_errorcodes_list[i].descr,location);
			break;
		}
	}
	throw xiAPIplus_Exception(res, descr);
}

void xiAPIplus_Camera::CheckResult(XI_RETURN res, const char* location)
{
	if (res != XI_OK)
	{
		ReportException(res, location);
	}
}

void xiAPIplus_Camera::CheckResultParam(XI_RETURN res, const char* location, const char* param)
{
	if (res != XI_OK)
	{
		char buff[MAX_PATH] = "";
		sprintf(buff, "%s - %s", location, param);
		ReportException(res, buff);
	}
}

void xiAPIplus_Exception::PrintError()
{
	printf("xiAPIplus-Error %d:%s\n", GetErrorNumber(), description.c_str());
}

unsigned long xiAPIplus_Camera::GetNumberOfConnectedCameras()
{
	xiAPIplus api;
	return api.GetNumberOfConnectedCameras();
}

// -----------------------------------------------
// class xiAPIplus - Camera
// -----------------------------------------------

xiAPIplus_Camera::xiAPIplus_Camera()
{
	camera_handle = NULL;
	image_timeout_ms = 1000;
	acquisition_active = false;
	manifest_buffer = NULL;
}

xiAPIplus_Camera::~xiAPIplus_Camera()
{
	Close();
}

void xiAPIplus_Camera::OpenFirst()
{
	OpenByID(0);
}

void xiAPIplus_Camera::OpenByID(unsigned long id)
{
	XI_RETURN res=xiOpenDevice(id, &camera_handle);
	CheckResult(res,"OpenByID");

	if(!camera_handle) res = XI_INVALID_HANDLE;
	CheckResult(res,"OpenByID");
}
void xiAPIplus_Camera::OpenBySN(const char* serial_number)
{
	if (!serial_number)  ReportException(XI_WRONG_PARAM_VALUE, "xiAPIplus_Camera::OpenBySN()");

	XI_RETURN res = xiOpenDeviceBy(XI_OPEN_BY_SN, serial_number, &camera_handle);
	CheckResult(res, "OpenBySN");

	if (!camera_handle) res = XI_INVALID_HANDLE;
	CheckResult(res, "OpenBySN");
}

void xiAPIplus_Camera::OpenByPath(const char* device_path)
{
	if(!device_path)  ReportException(XI_WRONG_PARAM_VALUE, "xiAPIplus_Camera::OpenByPath()");

	XI_RETURN res=xiOpenDeviceBy(XI_OPEN_BY_INST_PATH, device_path, &camera_handle);
	CheckResult(res,"OpenByPath");

	if(!camera_handle) res = XI_INVALID_HANDLE;
	CheckResult(res,"OpenByPath");
}

void xiAPIplus_Camera::OpenByUserID(const char* user_id)
{
	if(!user_id)  ReportException(XI_WRONG_PARAM_VALUE, "xiAPIplus_Camera::OpenByUserID()");

	XI_RETURN res=xiOpenDeviceBy(XI_OPEN_BY_USER_ID, user_id, &camera_handle);
	CheckResult(res,"OpenByUserID");

	if(!camera_handle) res = XI_INVALID_HANDLE;
	CheckResult(res,"OpenByUserID");
}

void xiAPIplus_Camera::OpenByLocation(const char* location)
{
	if(!location)  ReportException(XI_WRONG_PARAM_VALUE, "xiAPIplus_Camera::OpenByLocation()");

	XI_RETURN res=xiOpenDeviceBy(XI_OPEN_BY_LOC_PATH, location, &camera_handle);
	CheckResult(res,"OpenByLocation");

	if(!camera_handle) res = XI_INVALID_HANDLE;
	CheckResult(res,"OpenByLocation");
}

// ---------------------------------------------------------------------
// camera close

void xiAPIplus_Camera::Close()
{
	if (camera_handle)
	{
		FreeCameraManifest();
		XI_RETURN res=xiCloseDevice(camera_handle);
		CheckResult(res,"Close");
	}
	camera_handle=NULL;
}

// ---------------------------------------------------------------------
// acquisition
void xiAPIplus_Camera::StartAcquisition()
{
	CheckCamHandle("StartAcquisition()");
	XI_RETURN res=xiStartAcquisition(camera_handle);
	CheckResult(res,"StartAcquisition");
	acquisition_active=true;
}

void xiAPIplus_Camera::StopAcquisition()
{
	CheckCamHandle("StopAcquisition()");
	XI_RETURN res=xiStopAcquisition(camera_handle);
	CheckResult(res,"StopAcquisition");
	acquisition_active=false;
}

bool xiAPIplus_Camera::IsAcquisitionActive()
{
	return acquisition_active;
}

// ---------------------------------------------------------------------
// get next image
// receiving next image from xiAPI
// if (app_image is defined) storing result to app_image
// else storing result to last_image, also returing pointer to it

xiAPIplus_Image* xiAPIplus_Camera::GetNextImage(xiAPIplus_Image* app_image)
{
	xiAPIplus_Image* image=&last_image;
	if (app_image)
	{
		image = app_image;
	}

	image->ClearImage();

	XI_RETURN res = XI_OK;
	CheckCamHandle("GetNextImage()");
	res = xiGetImage(camera_handle, image_timeout_ms, image->GetXI_IMG());
	CheckResult(res,"GetNextImage");
	return image;
}

// ---------------------------------------------------------------------
// get last received image
// returns last received image (by function GetNextImage)

xiAPIplus_Image* xiAPIplus_Camera::GetLastImage()
{
	return &last_image;
}

// ---------------------------------------------------------------------
// set next image timeout in milliseconds

void xiAPIplus_Camera::SetNextImageTimeout_ms(int timeout_ms)
{
	image_timeout_ms=timeout_ms;
}

// ---------------------------------------------------------------------
// get next image timeout in milliseconds

int xiAPIplus_Camera::GetNextImageTimeout_ms()
{
	return image_timeout_ms;
}

// ---------------------------------------------------------------------
// return xiAPI parameter by name

void xiAPIplus_Camera::GetXIAPIParamString(const char* xiapi_param_name, char* value, int value_max_size)
{
	CheckCamHandle("GetXIAPIParamString");
	XI_RETURN res=xiGetParamString(camera_handle, xiapi_param_name, value, value_max_size);
	CheckResultParam(res,"GetXIAPIParamStr", xiapi_param_name);
}

string xiAPIplus_Camera::GetParamString(string xiapi_param_name)
{
	char value[256] = "";
	GetXIAPIParamString(xiapi_param_name.c_str(), value, 256);
	return string(value);
}

void xiAPIplus_Camera::SetXIAPIParamString(const char* xiapi_param_name, const char* value, unsigned int value_max_size)
{
	CheckCamHandle("SetXIAPIParamString");
	XI_RETURN res=xiSetParamString(camera_handle, xiapi_param_name, (char*)value, value_max_size);
	CheckResultParam(res,"SetXIAPIParamStr", xiapi_param_name);
}

void xiAPIplus_Camera::GetXIAPIParamInt(const char* xiapi_param_name, int* value)
{
	CheckCamHandle("GetXIAPIParamInt");
	XI_RETURN res=xiGetParamInt(camera_handle, xiapi_param_name, value);
	CheckResultParam(res,"GetXIAPIParamInt", xiapi_param_name);
}

int xiAPIplus_Camera::GetXIAPIParamInt(string xiapi_param_name)
{
	int val = 0;
	GetXIAPIParamInt(xiapi_param_name.c_str(), &val);
	return val;
}

void xiAPIplus_Camera::SetXIAPIParamInt(const char* xiapi_param_name, int value)
{
	CheckCamHandle("SetXIAPIParamInt");
	XI_RETURN res=xiSetParamInt(camera_handle, xiapi_param_name, value);
	CheckResultParam(res,"SetXIAPIParamInt", xiapi_param_name);
}

void xiAPIplus_Camera::GetXIAPIParamFloat(const char* xiapi_param_name, float* value)
{
	CheckCamHandle("GetXIAPIParamFloat");
	XI_RETURN res=xiGetParamFloat(camera_handle, xiapi_param_name, value);
	CheckResultParam(res,"GetXIAPIParamFloat", xiapi_param_name);
}

float xiAPIplus_Camera::GetXIAPIParamFloat(string xiapi_param_name)
{
	float val = 0;
	GetXIAPIParamFloat(xiapi_param_name.c_str(), &val);
	return val;
}

void xiAPIplus_Camera::SetXIAPIParamFloat(const char* xiapi_param_name, float value)
{
	CheckCamHandle("SetXIAPIParamFloat");
	XI_RETURN res=xiSetParamFloat(camera_handle, xiapi_param_name, value);
	CheckResultParam(res,"SetXIAPIParamFloat", xiapi_param_name);
}

void xiAPIplus_Camera::SetXIAPIParam(char* xiapi_param_name, void* value, size_t value_size, XI_PRM_TYPE type)
{
	CheckCamHandle("SetXIAPIParam");
	XI_RETURN res=xiSetParam(camera_handle, xiapi_param_name, value, (DWORD)value_size,type);
	CheckResultParam(res,"SetXIAPIParam", xiapi_param_name);
}

void xiAPIplus_Camera::GetXIAPIParam(char* xiapi_param_name, void* value, size_t * value_size, XI_PRM_TYPE * type)
{
	CheckCamHandle("GetXIAPIParam");
	XI_RETURN res=xiGetParam(camera_handle, xiapi_param_name, value, (DWORD*)value_size,type);
	CheckResultParam(res,"GetXIAPIParam", xiapi_param_name);
}

void xiAPIplus_Camera::LoadCameraManifest()
{
	// to read size for manifest buffer
	int manifest_size = 0;
	XI_RETURN res = xiGetParamInt(camera_handle, XI_PRM_DEVICE_MANIFEST XI_PRMM_REQ_VAL_BUFFER_SIZE, &manifest_size);
	CheckResultParam(res, "GetXIAPIParam", XI_PRM_DEVICE_MANIFEST XI_PRMM_REQ_VAL_BUFFER_SIZE);

	// allocate buffer for camera manifest
	manifest_size += 1000;
	manifest_buffer = (char*)malloc(manifest_size);
	if (manifest_buffer == NULL)
	{
		throw xiAPIplus_Exception(XI_MEMORY_ALLOCATION, "LoadCameraManifest() buffer allocation failed");
	}
	else
	{
		// clear buffert
		memset(manifest_buffer, 0, manifest_size);

		// now read the camera manifest
		res = xiGetParamString(camera_handle, XI_PRM_DEVICE_MANIFEST, manifest_buffer, manifest_size);
		CheckResultParam(res, "GetXIAPIParam", XI_PRM_DEVICE_MANIFEST);
	}
}

void xiAPIplus_Camera::FreeCameraManifest()
{
	// release memory for camera manifest
	if(manifest_buffer)
	{
		free(manifest_buffer);
		manifest_buffer = NULL;
	}
}

// -----------------------------------------------
// class xiAPIplus - Image
// -----------------------------------------------

xiAPIplus_Image::xiAPIplus_Image()
{
	ClearImage();
}

// ---------------------------------------------------------------------
// get bytes per pixel

int xiAPIplus_Image::GetBytesPerPixel()
{
	int bpp=1;
	switch (image.frm)
	{
	case XI_MONO8:
	case XI_RAW8:
		bpp=1;
		break;
	case XI_MONO16:
	case XI_RAW16:
		bpp=2;
		break;
	case XI_RGB24:
		bpp=3;
		break;
	case XI_RGB32:
		bpp=4;
		break;
	case XI_RGB_PLANAR:
		bpp=3;
		break;
	default:
		ReportException(XI_NOT_SUPPORTED_DATA_FORMAT, "GetBytesPerPixel");
		break;
	}
	return bpp;
}

// ---------------------------------------------------------------------
// get bytes per pixel

int xiAPIplus_Image::GetTotalPixelValues()
{
	int vals=1;
	switch (image.frm)
	{
	case XI_MONO8:
	case XI_RAW8:
	case XI_RGB24:
	case XI_RGB32:
	case XI_RGB_PLANAR:
		vals=0x100;
		break;
	case XI_MONO16:
	case XI_RAW16:
		vals=0x1000; // 4096
		break;
	default:
		ReportException(XI_NOT_SUPPORTED_DATA_FORMAT, "GetTotalPixelValues");
		break;
	}
	return vals;
}

// ---------------------------------------------------------------------

int xiAPIplus_Image::GetBitCount()
{
	int vals=1;
	switch (image.frm)
	{
	case XI_MONO8:
	case XI_RAW8:       vals=8; break;
	case XI_RGB24:
	case XI_RGB_PLANAR: vals=24; break;
	case XI_RGB32:	    vals=32; break;
	case XI_MONO16:
	case XI_RAW16:      vals=16; break;
	default:
		ReportException(XI_NOT_SUPPORTED_DATA_FORMAT, "GetBitCount");
		break;
	}
	return vals;
}

// ---------------------------------------------------------------------

int xiAPIplus_Image::GetTimeStampSec()
{
	return image.tsSec;
}

// ---------------------------------------------------------------------

int xiAPIplus_Image::GetTimeStampUSec()
{
	return image.tsUSec;
}

// ---------------------------------------------------------------------

void xiAPIplus_Image::ClearImage()
{
	memset(&image, 0, sizeof(XI_IMG));
	image.size = sizeof(XI_IMG);
}

// ---------------------------------------------------------------------
// return core xiAPI image

XI_IMG* xiAPIplus_Image::GetXI_IMG()
{
	return &image;
}

// ---------------------------------------------------------------------

int xiAPIplus_Image::GetPadding_X()
{
	switch(image.frm)
	{
	case XI_RAW16:
	case XI_MONO16:
		return image.padding_x/2;
	default:
		return image.padding_x;
	}
}

// ---------------------------------------------------------------------