///////////////////////////////////////////////////////////////////////////////
// FILE:          LStepOld.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Marzhauser LStep version 1.2
// COPYRIGHT:     Jannis Uhlendorf
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
// AUTHOR:        Jannis Uhlendorf (jannis.uhlendorf@gmail.com) 2015

#ifdef WIN32
#include <windows.h>
#define snprintf _snprintf
#endif

#include "LStepOld.h"
#include <string>
#include <math.h>
#include <time.h>
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>
#include <iostream>
#include <cstdio>

const char* g_StageName = "XYStage";

const char  g_cmd_get_version[]           = {'U', 7, 'b', '\r', 'U', 80, '\0'};
const char* g_cmd_get_motor_speed         = "UI";
const char* g_cmd_set_motor_speed         = "U\t";
const char  g_cmd_activate_joystick[]     = {'U', 7, 'j', '\r', 'U', 80, '\0' };
const char  g_cmd_deactivate_joystick[]   = {'j', '\0'};
const char  g_cmd_get_pos_x[]             = {'U', 67, '\0'};
const char  g_cmd_get_pos_y[]             = {'U', 68, '\0'};
const char  g_cmd_set_pos_x[]             = {'U', 0, '\0' };

const char  g_cmd_set_pos_y[]             = {'U', 1, '\0' };
const char  g_cmd_goto_absolute[]         = {'U', 7, 'r', '\0' };
const char  g_cmd_start[]                 = {'U', 80, '\0'};
const char  g_cmd_stop[]                  = {'a', '\0'};

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////

MODULE_API void InitializeModuleData()
{
   RegisterDevice( g_StageName, MM::XYStageDevice, "LStepOld XY Stage" );
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   // decide which device class to create based on the deviceName parameter
   if (strcmp(deviceName, g_StageName) == 0)
   {
      return new LStepOld();
   }
   // ...supplied name not recognized
   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

LStepOld::LStepOld() : initialized_(false),
   answerTimeoutMs_(5000),
   motor_speed_(5.0),
   joystick_command_("False")
{
   // call the base class method to set-up default error codes/messages
   InitializeDefaultErrorMessages();

   CPropertyAction* pAct = new CPropertyAction (this, &LStepOld::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Unefined", MM::String, false, pAct, true);
}


LStepOld::~LStepOld()
{
}

int LStepOld::Initialize()
{
   std::string answer;
   int s = ExecuteCommand( g_cmd_get_motor_speed, NULL, 0, &answer );
   if (s!=DEVICE_OK)
      return s;
   motor_speed_ = atof( answer.c_str() ) * .1;
   char speed[5];
   sprintf( speed, "%2.1f",  motor_speed_ );
   CPropertyAction* pAct = new CPropertyAction(this, &LStepOld::OnSpeed );
   CreateProperty( "Motor-speed [Hz]", speed, MM::Float, false, pAct);
   SetPropertyLimits( "Motor-speed [Hz]", 0.01, 25 );

   s = ExecuteCommand( g_cmd_get_version, NULL, 0, &answer );
   if (s!=DEVICE_OK)
      return s;
   CreateProperty("Firmware Version", answer.c_str(), MM::String, true);

   pAct = new CPropertyAction( this, &LStepOld::OnJoystick );
   CreateProperty( "Joystick command", "False", MM::String, false, pAct);
   std::vector<std::string> allowed_boolean;
   allowed_boolean.push_back("True");
   allowed_boolean.push_back("False");
   SetAllowedValues("Joystick command", allowed_boolean);

   return DEVICE_OK;
}

int LStepOld::Shutdown()
{
   int s = ExecuteCommand( g_cmd_deactivate_joystick );
   if (s!=DEVICE_OK)
      return s;
   return DEVICE_OK;
}
void LStepOld::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_StageName);
}

bool LStepOld::Busy()
{
   return false;
}


int LStepOld::SetPositionUm(double x, double y)
{
   char posx[16];
   char posy[16];
   sprintf( posx, "%015d", (int) x );
   sprintf( posy, "%015d", (int) y );
   int s = ExecuteCommand( g_cmd_goto_absolute );
   if (s!=DEVICE_OK)
      return s;
   s = ExecuteCommand( g_cmd_set_pos_x, posx, 15 );
   if (s!=DEVICE_OK)
      return s;
   s = ExecuteCommand( g_cmd_set_pos_y, posy, 15 );
   if (s!=DEVICE_OK)
      return s;
   std::string answer;
   s = ExecuteCommand( g_cmd_start, NULL, 0, &answer );
   if (s!=DEVICE_OK)
      return s;
   return DEVICE_OK;
}

int LStepOld::GetLimitsUm(double&, double&, double&, double&)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

int LStepOld::SetPositionSteps(long x, long y)
{
   return SetPositionUm( x, y );
}

int LStepOld::GetPositionSteps(long& x, long& y)
{
   std::string answer;
   int s = ExecuteCommand( g_cmd_get_pos_x, NULL, 0, &answer );
   if (s!=DEVICE_OK)
      return s;
   x = static_cast<long>(atof(answer.c_str()));
   s = ExecuteCommand( g_cmd_get_pos_y, NULL, 0, &answer );
   if (s!=DEVICE_OK)
      return s;
   y = static_cast<long>(atof(answer.c_str()));
   return DEVICE_OK;
}

int LStepOld::Home()
{
   return DEVICE_OK;
}

int LStepOld::Stop()
{
   int s = ExecuteCommand( g_cmd_stop );
   if (s!=DEVICE_OK)
      return s;
   return DEVICE_OK;
}

int LStepOld::SetOrigin()
{
   return DEVICE_OK;
}

int LStepOld::GetStepLimits(long&, long&, long&, long&)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

double LStepOld::GetStepSizeXUm()
{
   return 1;
}

double LStepOld::GetStepSizeYUm()
{
   return 1;
}

int LStepOld::IsXYStageSequenceable(bool& isSequenceable) const
{
   isSequenceable = false;
   return DEVICE_OK;
}



int LStepOld::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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
         return DEVICE_INVALID_INPUT_PARAM;
      }
      pProp->Get(port_);
   }
   return DEVICE_OK;
}


int LStepOld::OnSpeed(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct==MM::BeforeGet)
   {
   }
   else if (eAct==MM::AfterSet)
   {
      pProp->Get(motor_speed_);

      int speed = (int) ((motor_speed_+.05)*10);

      char input[5];
      sprintf( input, "%03d",  speed );
      int s = ExecuteCommand( g_cmd_set_motor_speed, input, 3 );
      if (s!=DEVICE_OK)
         return s;
   }
   return DEVICE_OK;
}


int LStepOld::OnJoystick(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(joystick_command_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(joystick_command_);

      if (!joystick_command_.compare("True"))
         return ExecuteCommand( g_cmd_activate_joystick );
      if (!joystick_command_.compare("False"))
         return ExecuteCommand( g_cmd_deactivate_joystick );
   }
   return DEVICE_OK;
}


int LStepOld::ExecuteCommand( const std::string& cmd, char* input, int inputLen, std::string* ret)
// Exedute a command, input, inputlen and ret are 0 by default
// if a pointer to input and a value for inputlen is given, this input is sent to the device
// if a pointer to ret is given, the return value of the device it returned in ret
{
   if ( (joystick_command_.compare("True")==0) & (cmd.compare(g_cmd_activate_joystick)!=0) & (cmd.compare(g_cmd_deactivate_joystick)!=0) )
   {
      std::string answer;
      int s = ExecuteCommand( g_cmd_deactivate_joystick, NULL, 0, &answer );
      if (s!=DEVICE_OK)
         return s;
   }
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

   // send command
   if (!cmd.compare( g_cmd_set_pos_x)) // special case which needs to be handled speperately (\0 in string)
   {
      int final_size = sizeof(g_cmd_set_pos_x)/sizeof(char) + inputLen;
      unsigned char* cmd_u = new unsigned char[final_size];
      for (int i=0; i<sizeof(g_cmd_set_pos_x)/sizeof(char); i++)
         cmd_u[i] = (unsigned char) g_cmd_set_pos_x[i];
      for (int i=0; i<inputLen; i++ )
         cmd_u[i + sizeof(g_cmd_set_pos_x)/sizeof(char)-1] = (unsigned char) input[i];
      cmd_u[final_size-1] = 13; // '\r' terminator
      int s = WriteToComPort( port_.c_str(), cmd_u, final_size );
      if (s!=DEVICE_OK)
      {return s; }
   }
   else
   {
      int s = SendSerialCommand( port_.c_str(), cmd_i, "\r" );
      if (s!=DEVICE_OK)
      {return s;}
   }
   delete [] cmd_i;

   if (ret!=NULL) // read return value
   {
      std::string buff;
      int s = GetSerialAnswer( port_.c_str(), "\r", buff );
      if (s!=DEVICE_OK)
      { return s; }
      *ret = buff;
   }
   if  ( (joystick_command_.compare("True")==0) & (cmd.compare(g_cmd_activate_joystick)!=0) & (cmd.compare(g_cmd_deactivate_joystick)!=0) )
   {
      std::string answer;
      int s = ExecuteCommand(g_cmd_activate_joystick, NULL); //, 0, &answer);
      if (s!=DEVICE_OK)
         return s;
   }
   return DEVICE_OK;
}
