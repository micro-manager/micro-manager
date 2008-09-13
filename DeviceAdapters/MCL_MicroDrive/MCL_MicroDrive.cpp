/*
File:		MCL_MicroDrive.cpp
Copyright:	Mad City Labs Inc., 2008
License:	Distributed under the BSD license.
*/
#include "MicroDriveXYStage.h"
#include "../../MMDevice/ModuleInterface.h"

#define WIN32_LEAN_AND_MEAN
#include <windows.h>

BOOL APIENTRY DllMain( HANDLE /*hModule*/, 
                      DWORD  ul_reason_for_call, 
                      LPVOID /*lpReserved*/
	   			 )
{
   	switch (ul_reason_for_call)
   	{
   		case DLL_PROCESS_ATTACH:

			if(!MCL_InitLibrary())
				return false;

			break;
  		case DLL_THREAD_ATTACH:
			break;
   		case DLL_THREAD_DETACH:
			break;
   		case DLL_PROCESS_DETACH:

			MCL_ReleaseLibrary();

   			break;
   	}
    
	return TRUE;
}

MODULE_API void InitializeModuleData()
{
   AddAvailableDeviceName(g_XYStageDeviceName, "XY positioning");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_XYStageDeviceName) == 0)
	   return new MicroDriveXYStage();
  
   // ...supplied name not recognized
   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}
