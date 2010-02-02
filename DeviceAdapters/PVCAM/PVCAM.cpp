///////////////////////////////////////////////////////////////////////////////
// FILE:          PVCAM.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   PVCAM camera module
//                
// AUTHOR:        Nico Stuurman, Nenad Amodaj nenad@amodaj.com, 09/13/2005
// COPYRIGHT:     University of California, San Francisco, 2006
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
// NOTE;          This file is obsolete. For extensions and new development
//                use PVCAMUniversal.cpp. N.A. 01/17/2007
//
// CVS:           $Id$

#ifdef WIN32
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#pragma warning(disable : 4996) // disable warning for deperecated CRT functions on Windows 
#endif

#include "../../MMDevice/ModuleInterface.h"
#include "PVCAM.h"

#ifdef WIN32
#include "Headers/master.h"
#include "Headers/pvcam.h"
#endif

#ifdef __APPLE__
#define __mac_os_x
#include <PVCAM/master.h>
#include <PVCAM/pvcam.h>
#endif

#ifdef linux
#include <pvcam/master.h>
#include <pvcam/pvcam.h>
#endif

#include <string>
#include <sstream>
#include <iomanip>


using namespace std;

// global constants
const char* g_DeviceCascade = "Cascade";
const char* g_DeviceUniversal_1 = "Camera-1";
const char* g_DeviceUniversal_2 = "Camera-2";

const char* g_PixelType_8bit = "8bit";
const char* g_PixelType_10bit = "10bit";
const char* g_PixelType_12bit = "12bit";
const char* g_PixelType_14bit = "14bit";
const char* g_PixelType_16bit = "16bit";
const char* g_ReadoutRate = "ReadoutRate";
const char* g_ReadoutRate_Slow = "Slow";
const char* g_ReadoutRate_Fast = "Fast";
const char* g_ReadoutPort = "Port";
const char* g_ReadoutPort_Normal = "Normal";
const char* g_ReadoutPort_Multiplier = "EM";
const char* g_ReadoutPort_LowNoise = "LowNoise";
const char* g_ReadoutPort_HighCap = "HighCap";

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
      break;
  	   case DLL_THREAD_ATTACH:
      break;
   	case DLL_THREAD_DETACH:
      break;
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
   AddAvailableDeviceName(g_DeviceUniversal_1, "Universal PVCAM interface - camera slot 1");
   AddAvailableDeviceName(g_DeviceUniversal_2, "Universal PVCAM interface - camera slot 2");
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;
   
   if (strcmp(deviceName, g_DeviceUniversal_1) == 0)
      return new Universal(0);
   else if (strcmp(deviceName, g_DeviceUniversal_2) == 0)
      return new Universal(1);
   
   return 0;
}
