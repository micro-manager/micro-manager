///////////////////////////////////////////////////////////////////////////////
// FILE:          PVCAMAdapter.cpp
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
// NOTE:          This file is obsolete. For extensions and new development
//                use PVCAMUniversal.cpp. N.A. 01/17/2007
//
#include "PVCAMAdapter.h"

// MMDevice
#include "ModuleInterface.h"

#include <cstring>


// global constants
const char* g_DeviceUniversal_1 = "Camera-1";
const char* g_DeviceUniversal_2 = "Camera-2";
const char* g_DeviceUniversal_3 = "Camera-3";
const char* g_DeviceUniversal_4 = "Camera-4";


///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
    RegisterDevice(g_DeviceUniversal_1, MM::CameraDevice, "Universal PVCAM interface - camera slot 1");
    RegisterDevice(g_DeviceUniversal_2, MM::CameraDevice, "Universal PVCAM interface - camera slot 2");
    RegisterDevice(g_DeviceUniversal_3, MM::CameraDevice, "Universal PVCAM interface - camera slot 3");
    RegisterDevice(g_DeviceUniversal_4, MM::CameraDevice, "Universal PVCAM interface - camera slot 4");
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
    delete pDevice;
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
    if (deviceName == 0)
        return NULL;

    if (strcmp(deviceName, g_DeviceUniversal_1) == 0)
        return new Universal(0, g_DeviceUniversal_1);
    else if (strcmp(deviceName, g_DeviceUniversal_2) == 0)
        return new Universal(1, g_DeviceUniversal_2);
    else if (strcmp(deviceName, g_DeviceUniversal_3) == 0)
        return new Universal(2, g_DeviceUniversal_3);
    else if (strcmp(deviceName, g_DeviceUniversal_4) == 0)
        return new Universal(3, g_DeviceUniversal_4);

    return NULL;
}
