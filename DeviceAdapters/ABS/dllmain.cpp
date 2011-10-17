// dllmain.cpp : Definiert den Einstiegspunkt für die DLL-Anwendung.

#include "MMDevice.h"
#include "ModuleInterface.h"
#include "MMDeviceConstants.h"
#include "ABSCamera.h"

HDEVMODULE g_hLibraryHandle = 0;

// TODO: linux entry code

// windows DLL entry code
#ifdef WIN32
BOOL APIENTRY DllMain( HMODULE hModule,
                       DWORD  ul_reason_for_call,
                       LPVOID lpReserved
					 )
{
	switch (ul_reason_for_call)
	{
	case DLL_PROCESS_ATTACH:
		g_hLibraryHandle = hModule; // remeber module handle
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
extern const char* g_CameraName;
/**
 * List all supported hardware devices here
 */
MODULE_API void InitializeModuleData()
{
   AddAvailableDeviceName(g_CameraName, "ABS camera");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   // decide which device class to create based on the deviceName parameter
   if (strcmp(deviceName, g_CameraName) == 0)
   {
      // create camera	  
      return new CABSCamera();;	  
   }

   // ...supplied name not recognized
   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   if (0 != pDevice)
	   delete pDevice;
}