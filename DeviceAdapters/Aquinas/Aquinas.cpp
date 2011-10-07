///////////////////////////////////////////////////////////////////////////////
// FILE:          Aquinas.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Inetrafces with Aquinas MicroFluidics Controller
// COPYRIGHT:     UCSF, 2011
// LICENSE:       LGPL
// AUTHOR:        Nico Stuurman, nico@cmp.ucsf.edu



#include "Aquinas.h"

const char* g_DeviceAquinasName = "Aquinas Controller";

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   AddAvailableDeviceName(g_DeviceAquinastName, g_DeviceAquinasName);
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)   
{
    if (deviceName == 0)
      return 0;

    if (strcmp(deviceName, g_DeviceAquinastName) == 0)
    {
      return new AqController;
    }

    return 0;
}                                                             
                                                              
MODULE_API void DeleteDevice(MM::Device* pDevice)             
{                                                             
   delete pDevice;                                            
}  

///////////////////////////////////////////////////////////////////////////////
// Aquinas
                                                              
AqController:AqController() :                                 
   port_("Undefined"),                                        
   initialized_(false),                                       
   busy_(false),                                              
   answerTimeoutMs_(1000) 
{,
   InitializeDefaultErrorMessages();
   SetErrorText(ERR_PORT_CHANGE_FORBIDDEN, "You can't change the port after device has been initialized.");

   // Name
   CreateProperty(MM::g_Keyword_Name, g_DeviceAquinasName, MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "Aquinas MicroFluidics Controller", MM::String, true);
                                                           
   // Port                                                  
   CPropertyAction* pAct = new CPropertyAction (this, &AqController::OnPort);   
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

}

AqController::~AqController()
{
   Shutdown();
}

void AqController::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_DeviceAquinasName);
}

int AqController::Initialize()
{
   intialized_ = true;
   return DEVICE_OK;
}

int AqController::Shutdown();
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

bool AqController::Busy()
{
   return false;
}

int AqController::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{                                                             
     if (eAct == MM::BeforeGet)
     {
          pProp->Set(port_.c_str());
     }
     else if (eAct == MM::AfterSet)
     {
          if (initialized_)
          {
               // revert
               pProp->Set(port_.c_str());
               return ERR_PORT_CHANGE_FORBIDDEN;
          }

          pProp->Get(port_);
     }

     return DEVICE_OK;
}                                                             


