///////////////////////////////////////////////////////////////////////////////
// FILE:          ModuleInterface.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMDevice - Device adapter kit
//-----------------------------------------------------------------------------
// DESCRIPTION:   The interface to the Micro-Manager plugin modules - (device
//                adapters)
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 08/08/2005
//
// NOTE:          This file is also used in the main control module MMCore.
//                Do not change it undless as a part of the MMCore module
//                revision. Discrepancy between this file and the one used to
//                build MMCore will cause malfunction and likely crash.
//
// COPYRIGHT:     University of California, San Francisco, 2006
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
// CVS:           $Id$
//

#ifndef _MODULE_INTERFACE_H_
#define _MODULE_INTERFACE_H_

#ifdef WIN32
   #ifdef MODULE_EXPORTS
      #define MODULE_API __declspec(dllexport)
   #else
      #define MODULE_API __declspec(dllimport)
   #endif

#else
   #define MODULE_API
#endif

#define MM_MODULE_ERR_OK 1000
#define MM_MODULE_ERR_WRONG_INDEX   1001
#define MM_MODULE_ERR_BUFFER_TOO_SMALL 1002

///////////////////////////////////////////////////////////////////////////////
// header version
// NOTE: If any of the exported module API calls changes, the interface version
// must be incremented
// new version 5 supports device discoverability
#define MODULE_INTERFACE_VERSION 7

#include "MMDevice.h"

///////////////////////////////////////////////////////////////////////////////
// Exported module interface
///////////////////////////////////////////////////////////////////////////////
extern "C" {
   MODULE_API MM::Device* CreateDevice(const char* name);
   MODULE_API void DeleteDevice(MM::Device* pDevice);
   MODULE_API long GetModuleVersion();
   MODULE_API long GetDeviceInterfaceVersion();
   MODULE_API unsigned GetNumberOfDevices();
   MODULE_API bool GetDeviceName(unsigned deviceIndex, char* name, unsigned bufferLength);
   MODULE_API bool GetDeviceDescription(const char* deviceName, char* name, unsigned bufferLength);
   /**
    * Intializes the list of available devices and perhaps other global initialization tasks.
    * The method may be called any number of times during the uManager session.
    */
   MODULE_API void InitializeModuleData();
}

// corresponding function pointers
typedef void (*fnDeleteDevice)(MM::Device*);
typedef MM::Device* (*fnCreateDevice)(const char*);
typedef long (*fnGetModuleVersion)();
typedef long (*fnGetDeviceInterfaceVersion) ();
typedef unsigned (*fnGetNumberOfDevices)();
typedef bool (*fnGetDeviceName)(unsigned, char*, unsigned);
typedef bool (*fnGetDeviceDescription)(const char*, char*, unsigned);
typedef void (*fnInitializeModuleData)();

// functions for internal use within the module
void AddAvailableDeviceName(const char* deviceName, const char* description = "Description N/A");

#endif //_MODULE_INTERFACE_H_
