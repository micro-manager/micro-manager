// dllmain.cpp : Definiert den Einstiegspunkt für die DLL-Anwendung.

#include "MMDevice.h"
#include "ModuleInterface.h"
#include "MMDeviceConstants.h"
#include "ABSCamera.h"


///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
extern const char* g_CameraName;
/**
 * List all supported hardware devices here
 */
MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_CameraName, MM::CameraDevice, "ABS camera");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   // decide which device class to create based on the deviceName parameter
   if (strcmp(deviceName, g_CameraName) == 0)
   {
      // create camera	  
      return new CABSCamera();
   }

   // ...supplied name not recognized
   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   if (0 != pDevice)
	   delete pDevice;
}
