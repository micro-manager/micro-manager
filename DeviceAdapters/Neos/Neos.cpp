//////////////////////////////////////////////////////////////////////////////
// FILE:          Neos.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Adapter for Neos controllers with USB interface
// COPYRIGHT:     University of California, San Francisco, 2009
// LICENSE:       This file is distributed under the LGPLD license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
// AUTHOR:        Nico Stuurman, 10/03/2009

#ifdef WIN32
#include <windows.h>
#define snprintf _snprintf 
#endif

#include "Neos.h"
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>
#include <iostream>

const char* g_NeosName = "Neos";
const char* g_Close = "Close";
const char* g_Open = "Open";
const char* g_Modulate = "Modulate";


///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_NeosName, MM::ShutterDevice, "Neos controller with USB" );
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_NeosName) == 0)
   {
      Neos* pNeos = new Neos();
      return pNeos;
   }

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}


///////////////////////////////////////////////////////////////////////////////
// Neos device
///////////////////////////////////////////////////////////////////////////////

Neos::Neos() :
   initialized_(false),
   open_(false),
   amplitudeMax_(1024),
   amplitude_(200),
   port_("Undefined"),
   changedTime_(0.0),
   channel_(4)
{
   InitializeDefaultErrorMessages();

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, g_NeosName, MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "Neos controller", MM::String, true);

   // Port
   CPropertyAction* pAct = new CPropertyAction (this, &Neos::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

   // Maximum amplitude for this channel
   pAct = new CPropertyAction(this, &Neos::OnAmplitudeMax);
   CreateProperty("Optimal amplitude", "1024", MM::Integer, false, pAct, true);

   pAct = new CPropertyAction(this, &Neos::OnChannel);
   CreateProperty("Channel", "4", MM::Integer, false, pAct, true);
   AddAllowedValue("Channel", "1");
   AddAllowedValue("Channel", "2");
   AddAllowedValue("Channel", "3");
   AddAllowedValue("Channel", "4");
   AddAllowedValue("Channel", "5");
   AddAllowedValue("Channel", "6");
   AddAllowedValue("Channel", "7");
   AddAllowedValue("Channel", "8");

}

Neos::~Neos()
{
   Shutdown();
}

void Neos::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_NeosName);
}

int Neos::Initialize()
{
   EnableDelay(true);
 
   changedTime_ = GetCurrentMMTime();

   SetOpen(false); // initialize to closed shutter, note that we can not get data from the controller

   CPropertyAction* pAct = new CPropertyAction(this, &Neos::OnAmplitude);
   CreateProperty("Amplitude", "200", MM::Integer, false, pAct);
   SetPropertyLimits("Amplitude", 0, amplitudeMax_);

   initialized_ = true;
   return DEVICE_OK;
}

int Neos::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

/*
 * This device does not give any feedback
 * So, start timer after the last command and check whether a predetermined time has elapsed since - NS
 */
bool Neos::Busy()
{
   MM::MMTime interval = GetCurrentMMTime() - changedTime_;

   if (interval < (1000.0 * GetDelayMs()))                                                               
       return true;

   return false;
}


int Neos::ExecuteCommand(std::string cmd)
{
   // send command
   PurgeComPort(port_.c_str());
   int ret = SendSerialCommand(port_.c_str(), cmd.c_str(), "\r");
   if (ret != DEVICE_OK)
      return DEVICE_SERIAL_COMMAND_FAILED;

   // Remember when this command was issued
   changedTime_ = GetCurrentMMTime();

   // This device does not answer, so at this point we are done

   // Even though we have no clue, we'll say all is fine
   return DEVICE_OK;
}


int Neos::SetOpen(bool open)
{
   std::ostringstream os;
   os << "CH " << channel_;
   int ret = ExecuteCommand(os.str().c_str());
   if (ret != DEVICE_OK)
      return ret;

   open_ = open;

   std::string command;
   if (open) 
      command = "ON";
   else
      command = "OFF";

   return ExecuteCommand(command);
}

int Neos::GetOpen(bool& open)
{
   open = open_;

   return DEVICE_OK;
}

int Neos::Fire(double /*deltaT*/)
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
int Neos::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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


int Neos::OnCommand(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(command_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(command_);
      return ExecuteCommand(command_);
   }

   return DEVICE_OK;
}

/**
 * Sets the "Active" Channel"
 */
int Neos::OnChannel(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(channel_);
   }
   else if (eAct == MM::AfterSet)
   {
      if (open_)
         SetOpen(open_);
      pProp->Get(channel_);
      if (open_)
         SetOpen(open_);
      
   }

   return DEVICE_OK;
}


/**
 * Sets the Optimum amplitude
 */
int Neos::OnAmplitudeMax(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set((long) amplitudeMax_);
   }
   else if (eAct == MM::AfterSet)
   {
      long amplitudeMax;
      pProp->Get(amplitudeMax);
      amplitudeMax_ = (int) amplitudeMax;
   }

   return DEVICE_OK;
}


/**
 * Sets the amplitude
 */
int Neos::OnAmplitude(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set((long) amplitude_);
   }
   else if (eAct == MM::AfterSet)
   {
      long amplitude;
      pProp->Get(amplitude);
      amplitude_ = (int) amplitude;

      std::ostringstream os;
      os << "CH " << channel_;
      int ret = ExecuteCommand(os.str().c_str());
      if (ret != DEVICE_OK)
         return ret;

      std::ostringstream osam;
      osam << "AM " << amplitude_;
      return ExecuteCommand(osam.str().c_str());

   }

   return DEVICE_OK;
}
