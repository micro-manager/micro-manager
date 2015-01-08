
#ifdef WIN32
#include <windows.h>
#define snprintf _snprintf
#endif

#include "ZeissCan.h"
using namespace std;

extern ZeissHub g_hub;
extern const char* g_PhotoModule;

///////////////////////////////////////////////////////////////////////////////
// AxioPhot2 PhotoModule 
///////////////////////////////////////////////////////////////////////////////
PhotoModule::PhotoModule():
   initialized_ (false),
   name_ (g_PhotoModule),
   pos_(1),
   numPos_(3)
{
   InitializeDefaultErrorMessages();

   // TODO provide error messages
   SetErrorText(ERR_SCOPE_NOT_ACTIVE, "Zeiss Scope is not initialized.  It is needed for the Zeiss Reflector Turret to work");
   SetErrorText(ERR_INVALID_TURRET_POSITION, "A position was requested that is not available on this turret");
   SetErrorText(ERR_MODULE_NOT_FOUND, "This module was not found in the Zeiss microscope");

   // Create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "Zeiss AxioPhot2 PhotoModule", MM::String, true);

   UpdateStatus();
}

PhotoModule::~PhotoModule()
{
   Shutdown();
}

void PhotoModule::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int PhotoModule::Initialize()
{
   if (!g_hub.initialized_)
      return ERR_SCOPE_NOT_ACTIVE;

   // check if this turret exists:
   //bool present;
   //int ret = g_turret.GetPresence(*this, *GetCoreCallback(), turretId_, present);
   //if (ret != DEVICE_OK)
   //   return ret;
   //if (!present)
   //   return ERR_MODULE_NOT_FOUND;

   // set property list
   // ----------------

   // State
   // -----
   CPropertyAction* pAct = new CPropertyAction(this, &PhotoModule::OnState);
   int ret = CreateProperty(MM::g_Keyword_State, "1", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;


   // Label
   // -----
   pAct = new CPropertyAction(this, &CStateBase::OnLabel);
   ret = CreateProperty(MM::g_Keyword_Label, "left", MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   SetPositionLabel(0, "eyepiece");
   SetPositionLabel(1, "camera");
   SetPositionLabel(2, "50-50 eye-camera");

   ret = UpdateStatus();
   if (ret!= DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

int PhotoModule::Shutdown()
{
   if (initialized_) initialized_ = false;
   return DEVICE_OK;
}

bool PhotoModule::Busy()
{
   // not sure, but it looks like this device is blocking
   return false;
}

int PhotoModule::SetTurretPosition(int position)
{
   ostringstream cmd;
   cmd << "EPPU" << position;
   int ret = g_hub.ExecuteCommand(*this, *GetCoreCallback(), cmd.str().c_str());
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

int PhotoModule::GetTurretPosition(int& position)
{
   string cmd = "EPPo";
   int ret = g_hub.ExecuteCommand(*this, *GetCoreCallback(), cmd.c_str());
   if (ret != DEVICE_OK)
      return ret;

   string answer;
   ret = g_hub.GetAnswer(*this, *GetCoreCallback(), answer);
   if (ret != DEVICE_OK)
      return ret;

   // I have no idea how the answer will be formatted.  For now, just log:
   ostringstream log;
   log << "Answer from scope: " << answer;
   this->LogMessage(log.str().c_str());

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers                                                           
///////////////////////////////////////////////////////////////////////////////

int PhotoModule::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      //int pos;
      //int ret = GetTurretPosition(pos);
      //if (ret != DEVICE_OK)
      //   return ret;
      //pos_ = pos -1;
      pProp->Set(pos_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(pos_);
      int pos = pos_ + 1;
      if ((pos > 0) && (pos <= numPos_))
         return SetTurretPosition(pos);
   }
   return DEVICE_OK;
}
