///////////////////////////////////////////////////////////////////////////////
// FILE:          CPiperMmAdapter.cpp
// PROJECT:       Piper Micro-Manager Camera Adapter
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   This is the DLL interface file. It provides an interface
//                with the OS when the DLL is loaded and unloaded and an
//                interface to Micro-Manager to discover the available
//                device adapters and methods to create and destroy instances
//                of those adapters.
//                
// AUTHOR:        Terry L. Sprout, Terry.Sprout@Agile-Automation.com
//
// COPYRIGHT:     (c) 2009, AgileAutomation, Inc, All rights reserved
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

#include "stdafx.h"
#include "CameraAdapter.h"

// The following data segment is a shared segment for all instances of this DLL.
// It contains a global instance counter that tracks the number of instances
// currently loaded. Some devices only allow a single instance since they control
// hardware that can only be controlled from a single client. The problem is
// a DLL can only recognize when multiple instances of the device are created
// within that instance of the DLL. This instance counter allows other instances
// of the DLL to know when there are instances of the DLL already loaded. The
// current rule is only one instance of this DLL can be used at a time. No devices
// will be created from a second instance of this DLL.
#pragma data_seg( ".sDalShared" )
extern LONG gs_nInstanceCount = -1;
#pragma data_seg()

// External names used by the rest of the system
// to load particular device from the "CameraAdapter.dll" library
static LPCTSTR sc_pszCameraDeviceName = "SPI Cameras";

static AFX_EXTENSION_MODULE PiperMmAdapterDLL = { NULL, NULL };

extern "C" int APIENTRY
DllMain(HINSTANCE hInstance, DWORD dwReason, LPVOID lpReserved)
{
	// Remove this if you use lpReserved
	UNREFERENCED_PARAMETER(lpReserved);

	if (dwReason == DLL_PROCESS_ATTACH)
	{
		TRACE0("PiperMmAdapter.DLL Initializing!\n");

		// Extension DLL one-time initialization
		if (!AfxInitExtensionModule(PiperMmAdapterDLL, hInstance))
			return 0;

		new CDynLinkLibrary(PiperMmAdapterDLL);

      // Increment the instance count for this DLL
      InterlockedIncrement( &gs_nInstanceCount );
	}
	else if (dwReason == DLL_PROCESS_DETACH)
	{
		TRACE0("PiperMmAdapter.DLL Terminating!\n");

      // We do not want to release the PIL until this DLL is being unloaded.
      // The release function does nothing if the PIL was never connected.
      pilReleaseLib();

		// Terminate the library before destructors are called
		AfxTermExtensionModule(PiperMmAdapterDLL);

      // Decrement the instance count for this DLL
      InterlockedDecrement( &gs_nInstanceCount );
	}
	return 1;   // ok
}

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////

MODULE_API void InitializeModuleData()
{
   AddAvailableDeviceName( sc_pszCameraDeviceName, "Stanford Photonics cameras" );
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   // decide which device class to create based on the deviceName parameter
   if (strcmp(deviceName, sc_pszCameraDeviceName) == 0)
   {
      // create camera
      return new CCameraAdapter( sc_pszCameraDeviceName );
   }

   // ...supplied name not recognized
   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}
