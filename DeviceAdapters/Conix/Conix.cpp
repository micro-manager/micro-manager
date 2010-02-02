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
#include <cstdio>
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
   
   // Get and Set State (allowed values 1-4, start at 0 for Hardware Configuration Wizard))
   CPropertyAction *pAct = new CPropertyAction (this, &QuadFluor::OnState);
   int nRet=CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
   if (nRet != DEVICE_OK)
      return nRet;

   vector<string> states;
   states.push_back("0");
   states.push_back("1");
   states.push_back("2");
   states.push_back("3");
   nRet = SetAllowedValues(MM::g_Keyword_State, states);

   // create default positions and labels
   for (long i=0; i < 4; i++)
   {
      std::ostringstream os;
      os << "Position: " << i;
      SetPositionLabel(i, os.str().c_str());
   }


   // Label
   // -----
   pAct = new CPropertyAction (this, &CStateBase::OnLabel);
   nRet = CreateProperty(MM::g_Keyword_Label, "", MM::String, false, pAct);
   if (nRet != DEVICE_OK)                                                     
      return nRet;   

   nRet = UpdateStatus();
   if (nRet != DEVICE_OK)
      return nRet;

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
   // the commands are blocking, so we cannot be busy
   return false;
   /*
   const unsigned long answerLength = 40;
   // If there was no command pending we are not busy
   if (!pendingCommand_)
      return false;

   // Read a line from the port, if first char is 'A' we are OK
   // if first char is 'N' read the error code
   unsigned char answer[answerLength];
   unsigned long charsRead;
   ReadFromComPort(port_.c_str(), answer, answerLength, charsRead);
   if (answer[0] == "A") {
      // this command was finished and is not pending anymore
      pendingCommand_ = false;
      return true;
   }
   else
      return false;
   
   // we should never be here, better not to block
   return false;
   */
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

int QuadFluor::GetPosition(int& position) 
{
   const char* command="Quad ";

   // send command
   int ret = SendSerialCommand(port_.c_str(), command, "\r");
   if (ret != DEVICE_OK)
      return ret;

   // block/wait for acknowledge, or until we time out;
   string answer;
   ret = GetSerialAnswer(port_.c_str(), "\r", answer);
   if (ret != DEVICE_OK)
      return ret;

   if (answer.length() > 2 && answer.substr(0, 2).compare(":N") == 0)
   {
      int errNo = atoi(answer.substr(2).c_str());
      return ERR_OFFSET + errNo;
   }
   else if (answer.length() > 2 && answer.substr(0, 2).compare(":A") == 0)
   {
      position = atoi(answer.substr(2).c_str());
      if (position == 0)
         return ERR_UNRECOGNIZED_ANSWER;

      return DEVICE_OK;
   }

   return ERR_UNRECOGNIZED_ANSWER;
}


int QuadFluor::SetPosition(int position)
{
   ostringstream command;
   command << "Quad " << position;

   // send command
   int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
   if (ret != DEVICE_OK)
      return ret;

   // block/wait for acknowledge, or until we time out;
   string answer;
   ret = GetSerialAnswer(port_.c_str(), "\r", answer);
   if (ret != DEVICE_OK)
      return ret;

   if (answer.substr(0, 2).compare(":A") == 0)
   {
      return DEVICE_OK;
   }
   else if (answer.length() > 2 && answer.substr(0, 2).compare(":N") == 0)
   {
      int errNo = atoi(answer.substr(2).c_str());
      return ERR_OFFSET + errNo;
   }

   return ERR_UNRECOGNIZED_ANSWER;
}




// Needs to be worked on (a lot)
int QuadFluor::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      int position, ret;
      ret = GetPosition(position);
      if (ret != DEVICE_OK)
         return ret;
      pProp->Set((long) position - 1);
   }
   else if (eAct == MM::AfterSet) {
      long position;
      int ret;
      pProp->Get(position);
      ret = SetPosition((int) position + 1);
      if (ret != DEVICE_OK)
         return ret;
   }
   return DEVICE_OK;
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

