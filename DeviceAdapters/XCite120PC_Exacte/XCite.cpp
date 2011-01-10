///////////////////////////////////////////////////////////////////////////////
// FILE:         XCite.cpp
// PROJECT:      Micro-Manager
// SUBSYSTEM:    DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:  This is the root class for Micro-Manager device adapters for 
//               the X-Cite Illuminators
//            
// AUTHOR:       Mark Allen Neil, markallenneil@yahoo.com, Dec-2010
//
// COPYRIGHT:    Mission Bay Imaging, 2010
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

#ifdef WIN32
   #include <windows.h>
   #define snprintf _snprintf
#endif

#include "XCite120PC.h"
#include "XCiteExacte.h"
#include "../../MMDevice/ModuleInterface.h"

#include <string>
#include <math.h>
#include <sstream>
#include <iostream>

using namespace std;

// External names used used by the rest of the system to load devices
const char* g_XCite120PC_Name = "XCite-120PC";
const char* g_XCiteExacte_Name = "XCite-Exacte";

// Device descriptions
const char* g_XCite120PC_Desc = "X-Cite 120PC";
const char* g_XCiteExacte_Desc = "X-Cite Exacte";

// Other string constants
const char* g_Undefined = "Undefined";

// Windows DLL entry code
#ifdef WIN32
BOOL APIENTRY DllMain( HANDLE, // hModule 
                  DWORD  ul_reason_for_call, 
                  LPVOID  // lpReserved
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
   AddAvailableDeviceName(g_XCite120PC_Name, g_XCite120PC_Desc);
   AddAvailableDeviceName(g_XCiteExacte_Name, g_XCiteExacte_Desc);
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;
   else if (strcmp(deviceName, g_XCite120PC_Name) == 0)
      return new XCite120PC(g_XCite120PC_Name);
   else if (strcmp(deviceName, g_XCiteExacte_Name) == 0)
      return new XCiteExacte(g_XCiteExacte_Name);
   else   
      return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}
