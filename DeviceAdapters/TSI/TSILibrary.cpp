///////////////////////////////////////////////////////////////////////////////
// FILE:          TSILibrary.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Thorlabs Scientific Imaging device library
//                
// AUTHOR:        Nenad Amodaj, 2012, 2017
// COPYRIGHT:     Thorlabs
//
// DISCLAIMER:    This file is provided WITHOUT ANY WARRANTY;
//                without even the implied warranty of MERCHANTABILITY or
//                FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//

#ifdef WIN32
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
//#include <fcntl.h>
//#include <io.h>
#pragma warning(disable : 4996) // disable warning for deprecated CRT functions on Windows 
#endif

#include <ModuleInterface.h>
#include <tl_camera_sdk.h> // not necessary, but makes intellisense happy

#include "TsiLibrary.h"
#include "TsiCam.h"
#include "Tsi3Cam.h"

#ifdef WIN32
#endif

#ifdef __APPLE__
#endif

#ifdef __linux__
#endif


///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_DeviceTsiCam, MM::CameraDevice, "Thorlabs Scientific Imaging camera");
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;
   
   if (strcmp(deviceName, g_DeviceTsiCam) == 0)
   {
      // first try if old SDK is installed
      if (isTsiSDKAvailable())
		{
         return new TsiCam(); // instantiate old camera
		}
      // then try SDK3
      if (isTsiSDK3Available())
		{
         return new Tsi3Cam(); // instantiate new camera
		}
   }

   return 0;
}

/**
 * Checks whether SDK3 is installed on the system
 *
 * @return bool - true if SDK3 is installed
 */
bool isTsiSDK3Available()
{
	std::string sdkPath = getSDKPath();
	std::string kernelPath(sdkPath);
	kernelPath += "thorlabs_unified_sdk_kernel.dll";
   if (tl_camera_sdk_dll_initialize(kernelPath.c_str()))
   {
      return false;
   }

   if (tl_camera_open_sdk())
   {
		tl_camera_sdk_dll_terminate();
		return false;
   }

   tl_camera_close_sdk();
	tl_camera_sdk_dll_terminate();

   return true;
}

/**
 * Checks whether "old" SDK is installed on the system
 *
 * @return bool - true if SDK is installed
 */
bool isTsiSDKAvailable()
{
	std::string sdkPath = getSDKPath();
	sdkPath += "tsi_sdk.dll";

   HMODULE libHandle = LoadLibrary(sdkPath.c_str());
   if (!libHandle)
      return false;

   // test loading one function
   TSI_CREATE_SDK tsi_create_sdk = (TSI_CREATE_SDK)GetProcAddress(libHandle, "tsi_create_sdk");
	TSI_DESTROY_SDK tsi_destroy_sdk = (TSI_DESTROY_SDK)GetProcAddress(libHandle, "tsi_destroy_sdk");   
	
	if(tsi_create_sdk == 0 || tsi_destroy_sdk == 0)
   {
      FreeLibrary(libHandle);
      return false;
   }

   TsiCam::tsiSdk = tsi_create_sdk();
	if (TsiCam::tsiSdk == 0)
   {
      FreeLibrary(libHandle);
      return false;
   }

   if (!TsiCam::tsiSdk->Open())
	{
		tsi_destroy_sdk(TsiCam::tsiSdk);
		TsiCam::tsiSdk = 0;
		FreeLibrary(libHandle);
		return false;
	}

	bool outcome = true;
	int numCameras = TsiCam::tsiSdk->GetNumberOfCameras();
   if (numCameras == 0)
      outcome = false;

	TsiCam::tsiSdk->Close();	
	tsi_destroy_sdk(TsiCam::tsiSdk);
   TsiCam::tsiSdk = 0;

   FreeLibrary(libHandle);
   return outcome;
}

std::string getSDKPath()
{
	char buffer[MAX_PATH];
	std::string envVar;

#if defined _M_IX86
      envVar = "THORLABS_TSI_SDK_PATH_32_BIT";
#elif defined _M_X64
      envVar = "THORLABS_TSI_SDK_PATH_64_BIT";
#endif

	DWORD ret = GetEnvironmentVariable(envVar.c_str(), buffer, MAX_PATH);
	if (ret == 0 || ret == MAX_PATH)
		return "";
	else
		return buffer;
}
