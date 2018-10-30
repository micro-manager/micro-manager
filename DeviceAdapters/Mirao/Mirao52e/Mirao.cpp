///////////////////////////////////////////////////////////////////////////////
//// FILE:       Mirao.cpp
//// PROJECT:    MicroManager
//// SUBSYSTEM:  DeviceAdapters
////-----------------------------------------------------------------------------
//// DESCRIPTION:
//// The basic functions of deformable mirror Mirao52e
////                
//// AUTHOR: Nikita Vladimirov 2018/10/30
////
//

#ifdef WIN32
   #include <windows.h>
#endif

#include "Mirao.h"
#include <string>
#include <math.h>
#include "../MMDevice/ModuleInterface.h"
#include <sstream>
#include "../../3rdparty/x64/mirao52e.h"


///////////////////////////////////////////////////////////////////////////////
// Devices in this adapter.  
// The device name needs to be a class name in this file

// device
const char* g_DefMirrorDeviceName = "DefMirrorDevice";
///////////////////////////////////////////////////////////////////////////////

using namespace std;

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////

MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_DefMirrorDeviceName, MM::GenericDevice, "DefMirror");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)                  
{                                                                            
   if (deviceName == 0)                                                      
       return 0;

   if (strcmp(deviceName, g_DefMirrorDeviceName) == 0)
   {
	   DefMirrorDevice* pDefMirrorDevice = new DefMirrorDevice();
        return pDefMirrorDevice;
   }

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)                            
{                                                                            
   delete pDevice;                                                           
}


///////////////////////////////////////////////////////////////////////////////
// DefMirrorDevice
//
DefMirrorDevice::DefMirrorDevice() :
   initialized_(false),                                                    
   answerTimeoutMs_(1000)
{
   InitializeDefaultErrorMessages();

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, g_DefMirrorDeviceName, MM::String, true);
   
   // Description                                                            
   CreateProperty(MM::g_Keyword_Description, "Deformable mirror Mirao52e", MM::String, true);
 
}

DefMirrorDevice::~DefMirrorDevice()
{
   Shutdown();
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////


int DefMirrorDevice::Initialize()
{
	int status;
	MroBoolean ret;
	ret = mro_open(&status);
	assert(ret == MRO_TRUE);
	return DEVICE_OK;
}

int DefMirrorDevice::Shutdown()
{
	int status;
	MroBoolean ret;
	ret = mro_close(&status);
	assert(ret == MRO_TRUE);
	return DEVICE_OK;
}

void DefMirrorDevice::GetName (char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_DefMirrorDeviceName);
}

bool DefMirrorDevice::Busy()
{
   return false;
}
