///////////////////////////////////////////////////////////////////////////////
//// FILE:       ZeissCAN.cpp
//// PROJECT:    MicroManage
//// SUBSYSTEM:  DeviceAdapters
////-----------------------------------------------------------------------------
//// DESCRIPTION:
//// Zeiss CAN bus controller, see Zeiss CAN bus documentation
////                
//// AUTHOR: Nico Stuurman, 1/16/2006
////
//

#ifdef WIN32
   #include <windows.h>
   #define snprintf _snprintf 
#endif

#include "ZeissCAN.h"
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>


///////////////////////////////////////////////////////////////////////////////
// Devices in this adapter.  
// The device name needs to be a class name in this file

// Zeiss scope
const char* g_ZeissDeviceName = "ZeissScope";
///////////////////////////////////////////////////////////////////////////////

MODULE_API void InitializeModuleData()
{
      AddAvailableDeviceName(g_ZeissDeviceName,"Zeiss Axiovert 200m controlled through serial interface");
}
using namespace std;

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////

MODULE_API MM::Device* CreateDevice(const char* deviceName)                  
{                                                                            
   if (deviceName == 0)                                                      
       return 0;

   if (strcmp(deviceName, g_ZeissDeviceName) == 0)
   {
        ZeissScope* pZeissScope = new ZeissScope();
        return pZeissScope;
   }

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)                            
{                                                                            
   delete pDevice;                                                           
}


///////////////////////////////////////////////////////////////////////////////
// ZeissScope
//
ZeissScope::ZeissScope() :
   initialized_(false),
   port_("Undefined"),                                                       
   answerTimeoutMs_(1000)
{
   InitializeDefaultErrorMessages();

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, g_ZeissDeviceName, MM::String, true);
   
   // Description                                                            
   CreateProperty(MM::g_Keyword_Description, "Zeiss microscope CAN bus adapter", MM::String, true);
 
   // Port                                                                   
   CPropertyAction* pAct = new CPropertyAction (this, &ZeissScope::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);
}

ZeissScope::~ZeissScope() 
{
   Shutdown();
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int ZeissScope::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int ZeissScope::Initialize() 
{
   return 0;
}

int ZeissScope::Shutdown() 
{
   return 0;
}

void ZeissScope::GetName (char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_ZeissDeviceName);
}

bool ZeissScope::Busy() 
{
   return true;
}
