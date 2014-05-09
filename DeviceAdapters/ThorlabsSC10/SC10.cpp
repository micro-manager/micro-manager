///////////////////////////////////////////////////////////////////////////////
// FILE:          SC10.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Thorlabs SC10 shutter controller adapter
// COPYRIGHT:     University of California, San Francisco, 2009
// LICENSE:       LGPL
// AUTHOR:        Nico Stuurman, 03/20/2009
//
//

#ifdef WIN32
#include <windows.h>
#define snprintf _snprintf 
#endif

#include "SC10.h"
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>
#include <iostream>

const char* g_SC10Name = "SC10";



///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_SC10Name, MM::ShutterDevice, "ThorLabs SC10 shutter controller");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_SC10Name) == 0)
   {
      return new SC10();
   }

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}


///////////////////////////////////////////////////////////////////////////////
// SC10 controller
// Serial Port needs to be set at 9600 baud, 8 data bits, no parity, 1 stop bit, no flow control
///////////////////////////////////////////////////////////////////////////////

SC10::SC10() :
   initialized_(false),
   deviceInfo_(""),
   port_("Undefined"),
   lastCommand_("Undefined")
{
   InitializeDefaultErrorMessages();

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, g_SC10Name, MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "SC10 D1 controller adapter", MM::String, true);

   // Port
   CPropertyAction* pAct = new CPropertyAction (this, &SC10::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);
}

SC10::~SC10()
{
   Shutdown();
}


void SC10::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_SC10Name);
}


int SC10::Initialize()
{
   // Get info about device. 
   std::string deviceInfo;
   int ret = ExecuteCommand("*idn?", deviceInfo);
   if (ret != DEVICE_OK)
      // With some ports, the first command fails.  In that case, repeat once
      ret = ExecuteCommand("*idn?", deviceInfo);
      if (ret != DEVICE_OK)
         return ret;
   CreateProperty("Device Info", deviceInfo.c_str(), MM::String, true);
   LogMessage(deviceInfo.c_str());

   // Set device to manual mode (1) needed for normal operation
   std::string answer;
   ret = ExecuteCommand("mode=1", answer);
   if (ret != DEVICE_OK)
      return ret;
   
   CPropertyAction* pAct = new CPropertyAction(this, &SC10::OnCommand);
   ret = CreateProperty("SC10 Command:", "", MM::String, false, pAct);
         
   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;
   return DEVICE_OK;
}


int SC10::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}


/*
 * Since we wait for an answer from the controller after opening or closing
 * the shutter is most likely already engaged (controoler has 1 ms response time)
 */
bool SC10::Busy()
{
   return false;
}

/**
 * This function not only sends the command but also reads back an answer from teh device
 * Note that the SC10 echoes the command.  We strip the echo here from the answer 
 * in order to facilitate downstream parsing
 */
int SC10::ExecuteCommand(const std::string& cmd, std::string& answer)
{
   // send command
   PurgeComPort(port_.c_str());
   if (DEVICE_OK != SendSerialCommand(port_.c_str(), cmd.c_str(), "\r"))
      return DEVICE_SERIAL_COMMAND_FAILED;

   // Get answer from the device.  It will always end with a new prompt
   int ret = GetSerialAnswer(port_.c_str(), ">", answer);
   if (ret != DEVICE_OK)
      return ret;

   // Check if the command was echoed and strip it out
   if (answer.compare(0, cmd.length(), cmd) == 0) {
      answer = answer.substr(cmd.length() + 1);
   // I really don't understand this, but on the Mac I get a space as the first character
   // This could be a problem in the serial adapter!
   } else if (answer.compare(1, cmd.length(), cmd) == 0) {
      answer = answer.substr(cmd.length() + 2);
   } else
      return DEVICE_SERIAL_COMMAND_FAILED;

   return DEVICE_OK;
}


int SC10::SetOpen(bool open)
{
   bool currentOpen;

   int ret = GetOpen(currentOpen);
   if (ret != DEVICE_OK)
      return ret;

   if (currentOpen != open) {
      std::string answer;
      ret = ExecuteCommand("ens", answer);
      if (ret != DEVICE_OK)
         return ret;
   }

   return DEVICE_OK;
}


int SC10::GetOpen(bool& open)
{
   std::string answer;
   int ret = ExecuteCommand("ens?", answer);
   if (ret != DEVICE_OK)
      return ret;

LogMessage(answer.c_str());

   if (atoi(answer.c_str()) == 0)
      open = false;
   else
      open = true;

   return DEVICE_OK;
}

int SC10::Fire(double /*deltaT*/)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////
/*
 * Sets the Serial Port to be used.
 * Should be called before initialization
 */
int SC10::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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


int SC10::OnCommand(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // let caller use cached version
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(command_);
      std::string answer;
      int ret = ExecuteCommand(command_, answer);
      if (ret != DEVICE_OK)
         return ret;
      pProp->Set(answer.c_str());
   }

   return DEVICE_OK;
}

