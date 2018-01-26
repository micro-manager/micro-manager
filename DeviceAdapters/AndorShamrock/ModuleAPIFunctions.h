#ifndef _MODULEAPI_H_
#define _MODULEAPI_H_

#include "../../MMDevice/ModuleInterface.h"
#include "AndorShamrock.h"
#include "SharedConstants.h"

using namespace std;

MODULE_API void InitializeModuleData();

MODULE_API MM::Device* CreateDevice(const char* deviceName);

MODULE_API void DeleteDevice(MM::Device * pDevice);


#endif _MODULEAPI_H_
