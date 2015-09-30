///////////////////////////////////////////////////////////////////////////////
//// FILE:       Skeleton.cpp
//// PROJECT:    MicroManage
//// SUBSYSTEM:  DeviceAdapters
////-----------------------------------------------------------------------------
//// DESCRIPTION:
//// The basic ingredients for a device adapter
////                
//// AUTHOR: Nico Stuurman, 1/16/2006
////
//

#ifdef WIN32
   #include <windows.h>
   #define snprintf _snprintf 
#endif

#include "Skeleton.h"
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>


///////////////////////////////////////////////////////////////////////////////
// Devices in this adapter.  
// The device name needs to be a class name in this file

// Skeleton device
const char* g_SkeletonDevicename = "SkeletonDevice";
///////////////////////////////////////////////////////////////////////////////

using namespace std;

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////

MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_SkeletonDeviceName, MM::GenericDevice, "Skeleton");            
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)                  
{                                                                            
   if (deviceName == 0)                                                      
       return 0;

   if (strcmp(deviceName, g_SkeletonDevicename) == 0)
   {
        SkeletonDevice* pSkeletonDevice = new SkeletonDevice();
        return pSkeletonDevice;
   }

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)                            
{                                                                            
   delete pDevice;                                                           
}


///////////////////////////////////////////////////////////////////////////////
// SkeletonDevice
//
SkeletonDevice::SkeletonDevice() :
   initialized_(false),
   port_("Undefined"),                                                       
   answerTimeoutMs_(1000)
{
   InitializeDefaultErrorMessages();

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, g_SkeletonDevicename, MM::String, true);
   
   // Description                                                            
   CreateProperty(MM::g_Keyword_Description, "Tell what your device is", MM::String, true);
 
   // Port                                                                   
   CPropertyAction* pAct = new CPropertyAction (this, &SkeletonDevice::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);
}

SkeletonDevice::~SkeletonDevice() 
{
   Shutdown();
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int SkeletonDevice::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      pProp->Set(port_.c_str());
   } else if (eAct == MM::AfterSet) {
      if (initialized_) {
         // revert
         pProp->Set(port_.c_str());
         return ERR_PORT_CHANGE_FORBIDDEN;
      }

      pProp->Get(port_);
   }

   return DEVICE_OK;
}

int SkeletonDevice::Initialize() 
{
   return 0;
}

int SkeletonDevice::Shutdown() 
{
   return 0;
}

void SkeletonDevice::GetName (char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_SkeletonDevicename);
}

bool SkeletonDevice::Busy() 
{
   return false;
}
