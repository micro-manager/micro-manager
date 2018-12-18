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
#include "TsiLibrary.h"
#include "TsiCam.h"
#include "Tsi3Cam.h"

#ifdef WIN32
#endif

#ifdef __APPLE__
#endif

#ifdef linux
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
   if (tl_camera_sdk_dll_initialize())
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
   HMODULE libHandle = LoadLibrary("tsi_sdk.dll");
   if (!libHandle)
      return false;

   // test loading one function
   TSI_CREATE_SDK tsi_create_sdk = (TSI_CREATE_SDK)GetProcAddress(libHandle, "tsi_create_sdk");
   if(tsi_create_sdk == 0)
   {
      FreeLibrary(libHandle);
      return false;
   }

   FreeLibrary(libHandle);
   return true;
}