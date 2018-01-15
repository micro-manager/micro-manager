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
   RegisterDevice(g_DeviceTsi3Cam, MM::CameraDevice, "Thorlabs TSI3 SDK camera");
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
      return new TsiCam();

   if (strcmp(deviceName, g_DeviceTsi3Cam) == 0)
      return new Tsi3Cam();

   return 0;
}
