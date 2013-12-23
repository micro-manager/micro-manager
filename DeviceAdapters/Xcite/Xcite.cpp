///////////////////////////////////////////////////////////////////////////////
// FILE:          Xcite.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   EXFO X-Cite 120 PC controller adaptor 
// COPYRIGHT:     INRIA Rocquencourt, France
//                University Paris Diderot, France, 2010 
// LICENSE:       This file is distributed under the BSD license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
// AUTHOR:        Jannis Uhlendorf (jannis.uhlendorf@inria.fr) 2010
//                This code is based on the Vincent Uniblitz controller adapter
//                by Nico Stuurman, 2006


#ifdef WIN32
#include <windows.h>
#define snprintf _snprintf 
#endif

#include "Xcite.h"
#include <string>
#include <math.h>
#include <time.h>
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>
#include <iostream>
#include <cstdio>


const char* g_Xcite120PCName                = "X-Cite120PC";

const char* g_XciteCmdConnect               = "tt";
const char* g_XciteCmdLockFrontPanel        = "ll";
const char* g_XciteCmdUnlockFrontPanel      = "nn";
const char* g_XciteCmdClearAlarm            = "aa";
const char* g_XciteCmdRunTimedExposure      = "oo";
const char* g_XciteCmdOpenShutter           = "mm";
const char* g_XciteCmdCloseShutter          = "zz";
const char* g_XciteCmdTurnLampOn            = "bb";
const char* g_XciteCmdTurnLampOff           = "ss";

const char* g_XciteCmdGetSoftwareVersion    = "vv";
const char* g_XciteCmdGetLampHours          = "hh";
const char* g_XciteCmdGetUnitStatus         = "uu";
const char* g_XciteCmdGetIntensityLevel     = "ii";
const char* g_XciteCmdSetIntensityLevel     = "i";
const char* g_XciteCmdGetExposureTime       = "cc";
const char* g_XciteCmdSetExposureTime       = "c";

const char* g_XciteRetOK                    = "";
const char* g_XciteRetERR                   = "e";




MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_Xcite120PCName, MM::ShutterDevice, "EXFO X-Cite 120 PC");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_Xcite120PCName) == 0)
   {
      Xcite120PC* p = new Xcite120PC();
      return p;
   }
   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
  delete pDevice;
}


Xcite120PC::Xcite120PC() :
   initialized_(false),
   port_("Undefined"),
   is_open_(false),
   is_locked_("False"),
   lamp_intensity_("100"),
   lamp_state_("On"),
   exposure_time_s_(0.2)
{
  InitializeDefaultErrorMessages();
  CreateProperty(MM::g_Keyword_Name, g_Xcite120PCName, MM::String, true);

  CPropertyAction* pAct = new CPropertyAction (this, &Xcite120PC::OnPort);
  CreateProperty(MM::g_Keyword_Port, "Unefined", MM::String, false, pAct, true);
}

Xcite120PC::~Xcite120PC()
{
  Shutdown();
}

int Xcite120PC::Initialize()
{
  /* Create the properties */
  CPropertyAction *pAct = new CPropertyAction (this, &Xcite120PC::OnIntensity);
  CreateProperty("LampIntensity", "100", MM::Integer, false, pAct);
  std::vector<std::string> allowed_intensities;
  allowed_intensities.push_back("0");
  allowed_intensities.push_back("12");
  allowed_intensities.push_back("25");
  allowed_intensities.push_back("50");
  allowed_intensities.push_back("100");
  SetAllowedValues("LampIntensity", allowed_intensities);
  
  pAct = new CPropertyAction (this, &Xcite120PC::OnPanelLock);
  CreateProperty("LockFrontPanel", "False", MM::String, false, pAct);
  std::vector<std::string> allowed_boolean;
  allowed_boolean.push_back("True");
  allowed_boolean.push_back("False");
  SetAllowedValues("LockFrontPanel", allowed_boolean);

  pAct = new CPropertyAction(this, &Xcite120PC::OnShutterState );
  CreateProperty( "Shutter-State", "Closed", MM::String, false, pAct);
  std::vector<std::string> allowed_state;
  allowed_state.push_back("Closed");
  allowed_state.push_back("Open");
  SetAllowedValues("Shutter-State", allowed_state);

  pAct = new CPropertyAction(this, &Xcite120PC::OnExposureTime );
  CreateProperty( "Exposure-Time [s]", "A", MM::Float, false, pAct);
  SetPropertyLimits( "Exposure-Time [s]", 0.2, 999.9 );

  pAct = new CPropertyAction(this, &Xcite120PC::OnTrigger );
  CreateProperty( "Trigger", "Off", MM::String, false, pAct);
  std::vector<std::string> trigger_state;
  trigger_state.push_back("On");
  trigger_state.push_back("Off");
  SetAllowedValues("Trigger", trigger_state);
 
  // This seems to cause problems
  //pAct = new CPropertyAction (this, &Xcite120PC::OnLampState);
  //CreateProperty("LampState", "On", MM::String, false, pAct);
  //std::vector<std::string> lamp_state;
  //lamp_state.push_back("On");
  //lamp_state.push_back("Off");
  //SetAllowedValues("LampState", lamp_state);

  
  /* Connect */
  int s = ExecuteCommand( g_XciteCmdConnect );
  if (s!=DEVICE_OK)
    return s;
  s = ExecuteCommand( g_XciteCmdClearAlarm );
  if (s!=DEVICE_OK)
    return s;


  /* initialize machine to default values */
  SetOpen(is_open_);
  SetProperty("LampIntensity", lamp_intensity_.c_str());
  SetProperty("LockFrontPanel", is_locked_.c_str());
  //SetProperty( "Exposure-Time [s]", "30");


  /* Create properties which are not modifiable */
  std::string answer;
  s = ExecuteCommand( g_XciteCmdGetSoftwareVersion, NULL, 0, &answer );
  if (s!=DEVICE_OK)
    return s;
  CreateProperty("ShutterSoftwareVersion", answer.c_str(), MM::String, true);

  s = ExecuteCommand( g_XciteCmdGetLampHours, NULL, 0, &answer );
  if (s!=DEVICE_OK)
    return s;
  CreateProperty("LampHours", answer.c_str(), MM::String, true);

  initialized_ = true;
  return DEVICE_OK;
}

int Xcite120PC::Shutdown()
{
  initialized_ = false;
  return DEVICE_OK;
}

void Xcite120PC::GetName(char* Name) const
{
  CDeviceUtils::CopyLimitedString(Name, g_Xcite120PCName);
}

bool Xcite120PC::Busy()
{
  /* All commands wait for a response, so we should never be buisy */
  return false;
}

int Xcite120PC::SetOpen(bool open)
{
  if (open)
  {
    is_open_=true;
    return ExecuteCommand(g_XciteCmdOpenShutter);
  }
  else
  {
    is_open_=false;
    return ExecuteCommand(g_XciteCmdCloseShutter);
  }
}

int Xcite120PC::GetOpen(bool& open)
{
  open = is_open_;
  return DEVICE_OK;
}

int Xcite120PC::Fire(double /*deltaT*/)
{
  // not yet supported

  //clock_t end;
  //SetOpen(true);
  //end = clock() + deltaT/1000 * CLOCKS_PER_SEC;
  //while (clock() < end ) {}
  //SetOpen(false);
  //return DEVICE_OK;

  return DEVICE_UNSUPPORTED_COMMAND;
}

int Xcite120PC::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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
  
int Xcite120PC::OnIntensity(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  if (eAct==MM::BeforeGet)
  {
    pProp->Set(lamp_intensity_.c_str());
  }
  else if (eAct==MM::AfterSet)
  {
    pProp->Get(lamp_intensity_);
    char intensity_code[] = "x";
    if (!lamp_intensity_.compare("0"))
      intensity_code[0] = '0';
    else if (!lamp_intensity_.compare("12"))
      intensity_code[0] = '1';
    else if (!lamp_intensity_.compare("25"))
      intensity_code[0] = '2';
    else if (!lamp_intensity_.compare("50"))
      intensity_code[0] = '3';
    else if (!lamp_intensity_.compare("100"))
      intensity_code[0] = '4';
    return ExecuteCommand( g_XciteCmdSetIntensityLevel, intensity_code, 1 );
  }
  return DEVICE_OK;
}

int Xcite120PC::OnPanelLock(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  if (eAct==MM::BeforeGet)
  {
    pProp->Set(is_locked_.c_str());
  }
  else if (eAct==MM::AfterSet)
  {
    pProp->Get(is_locked_);
    if (!is_locked_.compare("True"))
      return ExecuteCommand(g_XciteCmdLockFrontPanel);
    else if (!is_locked_.compare("False"))
      return ExecuteCommand(g_XciteCmdUnlockFrontPanel);
  }
  return DEVICE_OK;
}

int Xcite120PC::OnLampState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  if (eAct==MM::BeforeGet)
  {
    pProp->Set(lamp_state_.c_str());
  }
  else if (eAct==MM::AfterSet)
  {
    pProp->Get(lamp_state_);
    if (!lamp_state_.compare("On"))
      return ExecuteCommand(g_XciteCmdTurnLampOn);
    else if (!lamp_state_.compare("Off"))
      return ExecuteCommand(g_XciteCmdTurnLampOff);
   }
  return DEVICE_OK;  
}

int Xcite120PC::OnShutterState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  if (eAct==MM::BeforeGet)
  {
    if (is_open_)
      pProp->Set("Open");
    else
      pProp->Set("Closed");
  }
  else if (eAct==MM::AfterSet)
  {
    std::string buff;
    pProp->Get(buff);
    bool open = (buff=="Open");
    SetOpen(open);   
  }
  return DEVICE_OK;
}

int Xcite120PC::OnExposureTime(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  if (eAct==MM::BeforeGet)
  {
    pProp->Set(exposure_time_s_);
  }
  else if (eAct==MM::AfterSet)
  {
    pProp->Get(exposure_time_s_);
    int exp_time = (int) (exposure_time_s_*10);
    char input[5];
    sprintf( input, "%04d",  exp_time );
    int s = ExecuteCommand( g_XciteCmdSetExposureTime, input, 4 );
	if (s!=DEVICE_OK)
		return s;
  }
  return DEVICE_OK;
}

int Xcite120PC::OnTrigger(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  if (eAct==MM::AfterSet)
  {
    std::string buff;
    pProp->Get(buff);
    if (buff=="On")
    {
      int s = ExecuteCommand( g_XciteCmdRunTimedExposure );
      if (s!=DEVICE_OK)
		  return s;
	  //Fire(100);
    }  
  }
  return DEVICE_OK;
}


int Xcite120PC::ExecuteCommand( const std::string& cmd, char* input, int inputLen, std::string* ret)
// Exedute a command, input, inputlen and ret are 0 by default
// if a pointer to input and a value for inputlen is given, this input is sent to the device
// if a pointer to ret is given, the return value of the device it returned in ret
{
  char* cmd_i;
  
  if (input==NULL) // no input
  {
    cmd_i = new char[cmd.size()+1];
    strcpy(cmd_i,cmd.c_str());
  }
  else  // command with input
  {
    cmd_i = new char[cmd.size() + inputLen + 1];
    strcpy( cmd_i, cmd.c_str() );
    strncat( cmd_i, input, inputLen );
  }
  
  // clear comport
  int s = PurgeComPort( port_.c_str() );
  if (s!=DEVICE_OK)
    return s;

  // send command
  s = SendSerialCommand( port_.c_str(), cmd_i, "\r" );
  if (s!=DEVICE_OK)
    return s;

  delete [] cmd_i;

  // get status
  std::string buff;
  s = GetSerialAnswer( port_.c_str(), "\r", buff );
  if (s!=DEVICE_OK)
    return s;
  if (!buff.compare(g_XciteRetERR))
    return DEVICE_ERR;
  if (buff.compare(g_XciteRetOK) && ret==NULL)
    return DEVICE_NOT_CONNECTED;

  if (ret!=NULL) // read return value
  {
    *ret = buff;
  }
  return DEVICE_OK;
}
