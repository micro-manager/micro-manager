//-------------------------------------------------------------------------------------------------------------------
// XIMEA Application Programming Interface Object Oriented Approach
//-------------------------------------------------------------------------------------------------------------------

#pragma once

#include "xiApi.h"
#include <stdio.h>
#include <string>
// debug support

using namespace std;

#define xiAPIPlusDP(x) {if (is_debug_enabled) {printf x;}}
#define CheckCamHandle(place) {if (!camera_handle) CheckResult(XI_INVALID_HANDLE, place);}

// -----------------------------------------------
// xiAPIplus
// -----------------------------------------------

class xiAPIplus
{
public:
	unsigned long GetNumberOfConnectedCameras();
};

class xiAPIplus_Exception
{
public:
	xiAPIplus_Exception(XI_RETURN code, string desc)
	{
		description = desc;
		error_code = code;
	}
	XI_RETURN GetErrorNumber() {return error_code;}
	string GetDescription(){ return description; }
	void PrintError();
private:
	string    description;
	XI_RETURN error_code;
};

// -----------------------------------------------
// class xiAPIplus - Image
// -----------------------------------------------

class xiAPIplus_Image
{
public:
	xiAPIplus_Image();

	// functions
	XI_IMG_FORMAT GetDataFormat() {return image.frm;}
	unsigned char* GetPixels() {return (unsigned char*)image.bp;}
	int GetWidth() {return image.width;}
	int GetHeight() {return image.height;}
	int GetExpTime() { return image.exposure_time_us; };
	float GetGain() { return image.gain_db; };
	int GetPadding_X();
	XI_IMG* GetXI_IMG();
	int GetBytesPerPixel();
	int GetTotalPixelValues();
	int GetFrameNumber(){ return image.nframe;};
	int GetPixelsArraySize() {return ((image.width + GetPadding_X()) * image.height * GetBytesPerPixel());}
	int GetBitCount();
	int GetTimeStampSec();
	int GetTimeStampUSec();
	void ClearImage();

private:
	XI_IMG image;
};

// -----------------------------------------------
// class xiAPIplus - Camera
// -----------------------------------------------

class xiAPIplus_Camera
{
public:
	xiAPIplus_Camera();
	~xiAPIplus_Camera();

	// API
	unsigned long GetNumberOfConnectedCameras();

	// open/close
	void OpenFirst();
	void OpenByID(unsigned long id);
	void OpenBySN(const char* serial_number);
	void OpenByPath(const char* device_path);
	void OpenByUserID(const char* user_id);
	void OpenByLocation(const char* location);
	void Close();

	// acquisition
	void StartAcquisition();
	void StopAcquisition();
	bool IsAcquisitionActive();

	// image
	void SetNextImageTimeout_ms(int timeout_ms);
	int GetNextImageTimeout_ms();
	xiAPIplus_Image* GetNextImage(xiAPIplus_Image* app_image);
	xiAPIplus_Image* GetLastImage();
	HANDLE GetCameraHandle() {return camera_handle;}
	void SetCameraHandle(HANDLE handle) {camera_handle = handle;}

	void GetXIAPIParamString(const char* xiapi_param_name, char* value, int value_max_size);
	string GetParamString(string xiapi_param_name);
	void SetXIAPIParamString(const char* xiapi_param_name, const char* value, unsigned int value_size);

	void GetXIAPIParamInt(const char* xiapi_param_name, int* value);
	int  GetXIAPIParamInt(string xiapi_param_name);
	void SetXIAPIParamInt(const char* xiapi_param_name, int value);

	void GetXIAPIParamFloat(const char* xiapi_param_name, float* value);
	float GetXIAPIParamFloat(string xiapi_param_name);
	void SetXIAPIParamFloat(const char* xiapi_param_name, float value);

	void GetXIAPIParam(char* xiapi_param_name, void* value, size_t * value_size, XI_PRM_TYPE * type);
	void SetXIAPIParam(char* xiapi_param_name, void* value, size_t value_size, XI_PRM_TYPE type);

	void LoadCameraManifest();
	void FreeCameraManifest();
	char* GetCameraManifest(){ return manifest_buffer; };

private:
	// internal
	void CheckResult(XI_RETURN res,const char* location);
	void CheckResultParam(XI_RETURN res, const char* location, const char* param);

	HANDLE          camera_handle;
	int             image_timeout_ms;
	xiAPIplus_Image last_image;
	bool            acquisition_active;
	char*           manifest_buffer;
};