///////////////////////////////////////////////////////////////////////////////
// FILE:          Aquinas.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Interfaces with Aquinas MicroFluidics Controller
// COPYRIGHT:     UCSF, 2011
// LICENSE:       LGPL
// AUTHOR:        Nico Stuurman, nico@cmp.ucsf.edu



#include "Aquinas.h"
#include <sstream>

const char* g_DeviceAquinasName = "Aquinas Controller";

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   AddAvailableDeviceName(g_DeviceAquinasName, g_DeviceAquinasName);
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)   
{
    if (deviceName == 0)
      return 0;

    if (strcmp(deviceName, g_DeviceAquinasName) == 0)
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
                                                              
AqController::AqController() :                                 
   port_("Undefined"),                                        
   initialized_(false),                                       
   busy_(false),                                              
   pressureSetPoint_(0.0),
   valveState_(0),
   id_("A")
{
   InitializeDefaultErrorMessages();
   SetErrorText(ERR_PORT_CHANGE_FORBIDDEN, "You can't change the port after device has been initialized.");

   // Name
   CreateProperty(MM::g_Keyword_Name, g_DeviceAquinasName, MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "Aquinas MicroFluidics Controller", MM::String, true);
                                                           
   // Port                                                  
   CPropertyAction* pAct = new CPropertyAction (this, &AqController::OnPort);   
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

   // Device ID
   pAct = new CPropertyAction(this, &AqController::OnID);
   CreateProperty("Device ID", id_.c_str(), MM::String, false, pAct, true);
   // TODO: fix
   for (char c = 65; c < 80; c++) 
   {
      std::stringstream s;
      s << c;
      AddAllowedValue("Device ID", s.str().c_str());
   }
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
   CPropertyAction* pAct = new CPropertyAction(this, &AqController::OnSetPressure);
   CreateProperty("Pressure Set Point", "0", MM::Float, false, pAct);
   SetPropertyLimits("Pressure Set Point", 0, 76);

   pAct = new CPropertyAction(this, &AqController::OnValveState);
   CreateProperty("Valve State", "0", MM::Integer, false, pAct);
   SetPropertyLimits("Valve State", 0, 255);

   for (long i = 0; i < 8; i++)
   {
      CPropertyActionEx* pActEx = new CPropertyActionEx(this, &AqController::OnValveOnOff, i);
      std::ostringstream os;
      os << "Valve nr. " << i + 1;
      CreateProperty(os.str().c_str(), "0", MM::Integer, false, pActEx, false);
      SetPropertyLimits(os.str().c_str(), 0, 1);
   }

   initialized_ = true;

   return DEVICE_OK;
}

int AqController::Shutdown()
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

int AqController::OnID(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(id_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(id_);
   }
   return DEVICE_OK;
}

int AqController::OnSetPressure(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(pressureSetPoint_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(pressureSetPoint_);
      std::ostringstream os;
      os << id_ << "s";
      os << std::fixed << std::setfill('0') << std::setw(8) << pressureSetPoint_;

      int ret = SendSerialCommand(port_.c_str(), os.str().c_str(), "");
      if (ret != DEVICE_OK)
         return ret;

      // TODO: read back the asnwer - ugly since it does not have a terminator
   }
   return DEVICE_OK;
}

int AqController::OnValveState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set((long int) valveState_);
   }
   else if (eAct == MM::AfterSet)
   {
      long vS;
      pProp->Get(vS);
      valveState_ = (unsigned char) vS;

      return SetValveState();
   }
   return DEVICE_OK;
}


int AqController::OnValveOnOff(MM::PropertyBase* pProp, MM::ActionType eAct, long valveNr)
{
   if (eAct == MM::BeforeGet)
   {
      unsigned char t = valveState_;
      for (unsigned int i = 0; i < (unsigned int) valveNr; i++)
         t = t >> 1;
      long val = 0;
      if (t & 1)
         val = 1;
      pProp->Set(val);
   }
   else if (eAct == MM::AfterSet)
   {
      long val = 0;
      pProp->Get(val);
      if (val == 1)
      {
         valveState_ |= (1 << valveNr);
      } else if (val == 0)
      {
         valveState_ &= ~(1 << valveNr);
      }

      return SetValveState();

   }
   return DEVICE_OK;

}

int AqController::SetValveState()
{
   std::ostringstream os;
   os << id_ << "v";
   unsigned char t = valveState_;
   for (int i = 0; i < 8; i++) 
   {
      if (t & 1)
         os << "1";
      else 
         os << "0";
      t = t >> 1;
   }
   int ret = SendSerialCommand(port_.c_str(), os.str().c_str(), "");
   if (ret != DEVICE_OK)
      return ret;

   // TODO: read back the asnwer - ugly since it does not have a terminator

      
   return DEVICE_OK;
}
