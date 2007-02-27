///////////////////////////////////////////////////////////////////////////////
// FILE:       Vincent.cpp
// PROJECT:    MicroManage
// SUBSYSTEM:  DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:
// Vincent VMM controller adapter
//                
// AUTHOR: Nico Stuurman, 02/27/2006
//
// Based on Ludl controller adpater by Nenad Amodaj
//

#ifdef WIN32
#include <windows.h>
#define snprintf _snprintf 
#endif

#include "Conix.h"
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>
#include <iostream>

const char* g_ConixQuadFilterName = "ConixQuadFilter";

using namespace std;


///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
      AddAvailableDeviceName(g_ConixQuadFilterName,"External Filter Cube Switcher");   
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_ConixQuadFilterName) == 0)
   {
      QuadFluor* pQF = new QuadFluor();
      return pQF;
   }

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}


///////////////////////////////////////////////////////////////////////////////
// VMMController device
///////////////////////////////////////////////////////////////////////////////

QuadFluor::QuadFluor() :
   initialized_(false),
   numPos_(4),
   port_("Undefined"),
   pendingCommand_(false)
{
   InitializeDefaultErrorMessages();

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, g_ConixQuadFilterName, MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "Conix Motorized Qud-Filter changer for Nikon TE200/300", MM::String, true);

   // Port
   CPropertyAction* pAct = new CPropertyAction (this, &QuadFluor::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

}

QuadFluor::~QuadFluor()
{
   Shutdown();
}

void QuadFluor::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_ConixQuadFilterName);
}

int QuadFluor::Initialize()
{
   // set property list
   // -----------------
   
   // read out the version and set is as a read-only value
   //char* version = QuadFluor::GetVersion();

   // Get and Set State (allowed values 1-4
   CPropertyAction *pAct = new CPropertyAction (this, &QuadFluor::OnState);
   int nRet=CreateProperty(MM::g_Keyword_State, "1", MM::Integer, false, pAct);
   if (nRet != DEVICE_OK)
      return nRet;

   vector<string> states;
   states.push_back("1");
   states.push_back("2");
   states.push_back("3");
   states.push_back("4");
   nRet = SetAllowedValues(MM::g_Keyword_State, states);


   // ?? What is this for ? - NS
   int ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;
   return DEVICE_OK;
}

int QuadFluor::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

/*
 *
 */
bool QuadFluor::Busy()
{
   const unsigned long answerLength = 40;
   // If there was no command pending we are not busy
   if (!pendingCommand_)
      return false;

   // Read a line from the port, if first char is 'A' we are OK
   // if first char is 'N' read the error code
   char answer[answerLength];
   unsigned long charsRead;
   ReadFromComPort(port_.c_str(), answer, answerLength, charsRead);
   if (&answer[0] == "A") {
      // this command was finished and is not pending anymore
      pendingCommand_ = false;
      return true;
   }
   else
      return false;
   
   // we should never be here, better not to block
   return false;
}


int QuadFluor::ExecuteCommand(const string& cmd)
{

   // Even though we have no clue, we'll say all is fine
   return DEVICE_OK;
}



///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////
/*
 * Sets the Serial Port to be used.
 * Should be called before initialization
 */
int QuadFluor::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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
         //return ERR_PORT_CHANGE_FORBIDDEN;
      }

      pProp->Get(port_);
   }
   return DEVICE_OK;
}


// Needs to be worked on (a lot)
int QuadFluor::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
   }
   else if (eAct == MM::AfterSet) {
      return DEVICE_OK;
   }
}


int QuadFluor::OnCommand(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(command_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(command_);
printf ("%s\n","Executing Command:");
      return ExecuteCommand(command_);
   }

printf ("%s\n","Outof OnCommand");
   return DEVICE_OK;
}

