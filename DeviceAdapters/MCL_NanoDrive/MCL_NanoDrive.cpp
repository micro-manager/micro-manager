/*
File:		MCL_NanoDrive.cpp
Copyright:	Mad City Labs Inc., 2008
License:	Distributed under the BSD license.
*/
#define WIN32_LEAN_AND_MEAN
#include <windows.h>

// Utilities
#include "handle_list_if.h"
#include "heap.h"

// MM
#include "../../MMDevice/ModuleInterface.h"

// Devices
#include "MCL_NanoDrive_XYStage.h"
#include "MCL_NanoDrive_ZStage.h"

extern HANDLE gHeap;
  
BOOL APIENTRY DllMain( HANDLE /*hModule*/, 
                       unsigned int  ul_reason_for_call, 
                       LPVOID /*lpReserved*/
					 )
{
	switch (ul_reason_for_call)
	{
		case DLL_PROCESS_ATTACH:	
			if(!GlobalHeapInit())
				return false;

			if(!MCL_InitLibrary(::gHeap))
				return false;

			if(!HandleListCreate())
				return false;

			break;

		case DLL_THREAD_ATTACH:
			break;

		case DLL_THREAD_DETACH:
			break;

		case DLL_PROCESS_DETACH:
			
			HandleListDestroy();

			MCL_ReleaseLibrary();

			GlobalHeapDestroy();

			break;
    }

    return true;
}

MODULE_API void InitializeModuleData()
{
	AddAvailableDeviceName(g_StageDeviceName, "Z positioning");
	AddAvailableDeviceName(g_XYStageDeviceName, "XY positioning");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
	if (deviceName == NULL)
		return 0;

	if (strcmp(deviceName, g_StageDeviceName) == 0)
	{
		return new MCL_NanoDrive_ZStage();
	}
	
	else if (strcmp(deviceName, g_XYStageDeviceName) == 0)
	{
		return new MCL_NanoDrive_XYStage();
	}
  
    // ...supplied name not recognized
	return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
    delete pDevice;
}