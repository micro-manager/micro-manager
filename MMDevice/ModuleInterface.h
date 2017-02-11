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
//                Do not change it unless as a part of the MMCore module
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

#ifndef _MODULE_INTERFACE_H_
#define _MODULE_INTERFACE_H_

#include "MMDevice.h"

#ifdef WIN32
   #ifdef MODULE_EXPORTS
      #define MODULE_API __declspec(dllexport)
   #else
      #define MODULE_API __declspec(dllimport)
   #endif
#else
   #define MODULE_API
#endif


/// Module interface version.
/**
 * The Core ensures that any loaded device adapter modules have a matching
 * version, to ensure ABI compatibility.
 */
// If any of the exported module API calls (below) changes, the interface
// version must be incremented. Note that the signature and name of
// GetModuleVersion() must never change.
#define MODULE_INTERFACE_VERSION 10


/*
 * Exported module interface
 */
extern "C" {
   /// Initialize the device adapter module.
   /**
    * Device adapter modules must provide an implementation of this function.
    *
    * The implementation of this function should call RegisterDevice() to
    * indicate the set of available devices in the module. The body of the
    * function normally should not do any other processing.
    *
    * This function may be called multiple times and therefore must be
    * idempotent (since RegisterDevice() is idempotent, nothing special has to
    * be done in most cases).
    *
    * \see RegisterDevice()
    */
   MODULE_API void InitializeModuleData();

   /// Instantiate the named device.
   /**
    * Device adapter modules must provide an implementation of this function.
    *
    * The implementation of this function should create an instance of the
    * device and return a raw pointer to it.
    *
    * \see DeleteDevice()
    */
   MODULE_API MM::Device* CreateDevice(const char* name);

   /// Destroy a device instance.
   /**
    * Device adapter modules must provide an implementation of this function.
    *
    * The implementation of this function should deallocate (delete) the given
    * device instance (which is guaranteed to be one that was previously
    * returned by CreateDevice()).
    *
    * \see CreateDevice()
    */
   MODULE_API void DeleteDevice(MM::Device* pDevice);

   // A common implementation is provided for the following functions.
   // Individual device adapters need not concern themselves with these
   // details.
   MODULE_API long GetModuleVersion();
   MODULE_API long GetDeviceInterfaceVersion();
   MODULE_API unsigned GetNumberOfDevices();
   MODULE_API bool GetDeviceName(unsigned deviceIndex, char* name, unsigned bufferLength);
   MODULE_API bool GetDeviceType(const char* deviceName, int* type);
   MODULE_API bool GetDeviceDescription(const char* deviceName, char* name, unsigned bufferLength);

   // Function pointer types for module interface functions
   // (Not for use by device adapters)
#ifndef MODULE_EXPORTS
   typedef void (*fnInitializeModuleData)();
   typedef MM::Device* (*fnCreateDevice)(const char*);
   typedef void (*fnDeleteDevice)(MM::Device*);
   typedef long (*fnGetModuleVersion)();
   typedef long (*fnGetDeviceInterfaceVersion) ();
   typedef unsigned (*fnGetNumberOfDevices)();
   typedef bool (*fnGetDeviceName)(unsigned, char*, unsigned);
   typedef bool (*fnGetDeviceType)(const char*, int*);
   typedef bool (*fnGetDeviceDescription)(const char*, char*, unsigned);
#endif
}


/*
 * Functions for use by the device adapter module
 */

/// Register a device class provided by the device adapter library.
/**
 * To be called in the device adapter module's implementation of
 * InitializeModuleData().
 *
 * Calling this function indicates that the module provides a device with the
 * given name and type, and provides a user-visible description string.
 *
 * \see InitializeModuleData()
 */
void RegisterDevice(const char* deviceName, MM::DeviceType deviceType, const char* description);


#endif //_MODULE_INTERFACE_H_
