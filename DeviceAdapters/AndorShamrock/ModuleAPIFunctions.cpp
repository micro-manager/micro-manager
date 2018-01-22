#include "../../MMDevice/ModuleInterface.h"
#include "ModuleAPIFunctions.h"


MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_SpectrographName, MM::GenericDevice, g_DeviceDescription);
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0) {
      return 0;
   }

   string strName(deviceName);

   if (strcmp(deviceName, g_SpectrographName) == 0) {
      MM::Device * device = new AndorShamrock();
      return device;
   }

   return 0;
}

MODULE_API void DeleteDevice(MM::Device * pDevice)
{
   delete pDevice;
}