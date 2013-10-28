///////////////////////////////////////////////////////////////////////////////
// FILE:          ASIStage.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   ASIStage adapter
// COPYRIGHT:     University of California, San Francisco, 2007
//                All rights reserved
//
// LICENSE:       This library is free software; you can redistribute it and/or
//                modify it under the terms of the GNU Lesser General Public
//                License as published by the Free Software Foundation.
//                
//                You should have received a copy of the GNU Lesser General Public
//                License along with the source distribution; if not, write to
//                the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
//                Boston, MA  02111-1307  USA
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, 
//
// MAINTAINER     Maintained by Nico Stuurman (nico@cmp.ucsf.edu)
//


#include "FocalPoint.h"
#include <string>
#include "../../MMDevice/ModuleInterface.h"
#include "../../MMDevice/DeviceUtils.h"
#include <sstream>
#include <iostream>


const char* g_FocalPointDeviceName = "FocalPoint";
const char* g_On = "On";
const char* g_Off = "Off";
const char* g_Focus = "Focus";
const char* g_Laser = "Laser";
const char* g_Undefined = "Undefined";
const char* g_DSPengage = "engage";
const char* g_DSPdisengage = "disengage";
const char* g_LaserOn = "lon";
const char* g_LaserOff = "loff";
const char* g_ACK = "  ACK";
const char* g_NOACK = "  NO_ACK";
const char* g_CommandMode = "Command";
const char* g_Local = "Local";
const char* g_Remote = "Remote";
const char* g_LocalCommand = "local";
const char* g_RemoteCommand = "remote";


///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   AddAvailableDeviceName(g_FocalPointDeviceName, "FocalPoint");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_FocalPointDeviceName) == 0)
   {
      FocalPoint* pDevice = new FocalPoint();
      return pDevice;
   }

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}


MM::DeviceDetectionStatus FocalPointCheckSerialPort(MM::Device& device, MM::Core& core, std::string portToCheck, double answerTimeoutMs)
{
   // all conditions must be satisfied...
   MM::DeviceDetectionStatus result = MM::Misconfigured;
   char answerTO[MM::MaxStrLength];

   try
   {
      std::string portLowerCase = portToCheck;
      for( std::string::iterator its = portLowerCase.begin(); its != portLowerCase.end(); ++its)
      {
         *its = (char)tolower(*its);
      }
      if( 0< portLowerCase.length() &&  0 != portLowerCase.compare("undefined")  && 0 != portLowerCase.compare("unknown") )
      {
         result = MM::CanNotCommunicate;
         core.GetDeviceProperty(portToCheck.c_str(), "AnswerTimeout", answerTO);
         // device specific default communication parameters
         // for ASI Stage
         core.SetDeviceProperty(portToCheck.c_str(), MM::g_Keyword_Handshaking, "Off");
         core.SetDeviceProperty(portToCheck.c_str(), MM::g_Keyword_StopBits, "1");
         std::ostringstream too;
         too << answerTimeoutMs;
         core.SetDeviceProperty(portToCheck.c_str(), "AnswerTimeout", too.str().c_str());
         core.SetDeviceProperty(portToCheck.c_str(), "DelayBetweenCharsMs", "0");
         MM::Device* pS = core.GetDevice(&device, portToCheck.c_str());
         std::vector< std::string> possibleBauds;
         possibleBauds.push_back("9600");
         for( std::vector< std::string>::iterator bit = possibleBauds.begin(); bit!= possibleBauds.end(); ++bit )
         {
            core.SetDeviceProperty(portToCheck.c_str(), MM::g_Keyword_BaudRate, (*bit).c_str() );
            pS->Initialize();
            core.PurgeSerial(&device, portToCheck.c_str());
            // check status
            const char* command = "/";
            int ret = core.SetSerialCommand( &device, portToCheck.c_str(), command, "\r");
            if( DEVICE_OK == ret)
            {
               char answer[MM::MaxStrLength];

               ret = core.GetSerialAnswer(&device, portToCheck.c_str(), MM::MaxStrLength, answer, "\r\n");
               if( DEVICE_OK != ret )
               {
                  char text[MM::MaxStrLength];
                  device.GetErrorText(ret, text);
                  core.LogMessage(&device, text, true);
               }
               else
               {
                  // to succeed must reach here....
                  result = MM::CanCommunicate;
               }
            }
            else
            {
               char text[MM::MaxStrLength];
               device.GetErrorText(ret, text);
               core.LogMessage(&device, text, true);
            }
            pS->Shutdown();
            if( MM::CanCommunicate == result)
               break;
            else
               // try to yield to GUI
               CDeviceUtils::SleepMs(10);
         }
         // always restore the AnswerTimeout to the default
         core.SetDeviceProperty(portToCheck.c_str(), "AnswerTimeout", answerTO);
      }
   }
   catch(...)
   {
      core.LogMessage(&device, "Exception in DetectDevice!",false);
   }
   return result;
}

//////////////////////////////////////////////////////////////////////
// FocalPoint reflection-based autofocussing device
////
FocalPoint::FocalPoint() :
   focusState_(g_Off),
   laserState_(g_Off),
   commandState_(g_Local),
   initialized_(false),
   port_(g_Undefined),
   waitAfterLock_(3000)
{
   InitializeDefaultErrorMessages();

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, g_FocalPointDeviceName, MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "FocalPoint Autofocus device adapter", MM::String, true);

   // Port
   CPropertyAction* pAct = new CPropertyAction (this, &FocalPoint::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

}

FocalPoint::~FocalPoint()
{
   initialized_ = false;
}


int FocalPoint::Initialize()
{
   if (initialized_)
      return DEVICE_OK;

   // check status first (test for communication protocol)
   int ret = CheckForDevice();
   if (ret != DEVICE_OK)
      return ret;

   CPropertyAction* pAct = new CPropertyAction(this, &FocalPoint::OnFocus);
   CreateProperty (g_Focus, g_On, MM::String, false, pAct);
   AddAllowedValue(g_Focus, g_On);
   AddAllowedValue(g_Focus, g_Off);

   pAct = new CPropertyAction(this, &FocalPoint::OnCommand);
   CreateProperty (g_CommandMode, g_Remote, MM::String, false, pAct);
   AddAllowedValue(g_CommandMode, g_Local);
   AddAllowedValue(g_CommandMode, g_Remote);

   pAct = new CPropertyAction(this, &FocalPoint::OnWaitAfterLock);
   CreateProperty("Wait ms after Lock", "3000", MM::Integer, false, pAct);

   pAct = new CPropertyAction(this, &FocalPoint::OnLaser);
   CreateProperty (g_Laser, g_On, MM::String, false, pAct);
   AddAllowedValue(g_Laser, g_On);
   AddAllowedValue(g_Laser, g_Off);

   initialized_ = true;
   return DEVICE_OK;
}

int FocalPoint::Shutdown()
{
   initialized_ = false;
   return DEVICE_OK;
}

bool FocalPoint::Busy()
{
   //TODO implement
   return false;
}

// TODO: See if this can be implemented for the FocalPoint
int FocalPoint::GetOffset(double& offset)
{
   offset = 0;
   return DEVICE_OK;
}

// TODO: See if this can be implemented for the FocalPoint
int FocalPoint::SetOffset(double /* offset */)
{
   return DEVICE_OK;
}

void FocalPoint::GetName(char* pszName) const
{
   CDeviceUtils::CopyLimitedString(pszName, g_FocalPointDeviceName);
}


bool FocalPoint::IsContinuousFocusLocked()
{
   if (focusState_ == g_On)
      return true;

   return false;
}


int FocalPoint::SetContinuousFocusing(bool state)
{
   std::string command = g_DSPengage;
   if (!state)
      command = g_DSPdisengage;

   std::string answer;
   int ret = QueryCommand(command.c_str(), answer);
   if (ret != DEVICE_OK)
      return ret;

   if (state)
      focusState_ = g_On;
   else
      focusState_ = g_Off;

   return DEVICE_OK;
}


int FocalPoint::SetLaser(bool state)
{
   std::string command = g_LaserOn;
   if (!state)
      command = g_LaserOff;

   std::string answer;
   int ret = QueryCommand(command.c_str(), answer);
   if (ret != DEVICE_OK)
      return ret;

   if (state)
      laserState_ = g_On;
   else
      laserState_ = g_Off;

   return DEVICE_OK;
}

int FocalPoint::SetCommand(std::string command)
{
   std::string sendCommand = g_RemoteCommand;
   if (command == g_Local)
      sendCommand = g_LocalCommand;

   std::string answer;
   int ret = QueryCommand(sendCommand.c_str(), answer);
   if (ret != DEVICE_OK)
      return ret;

   commandState_ = command;

   return DEVICE_OK;
}

int FocalPoint::CheckForDevice()
{
   return SetCommand(g_Remote);
}

int FocalPoint::GetContinuousFocusing(bool& state)
{
   return DEVICE_OK;
}

int FocalPoint::FullFocus()
{
   return SetContinuousFocusing(false);
}

int FocalPoint::IncrementalFocus()
{
   return FullFocus();
}

int FocalPoint::GetLastFocusScore(double& score)
{
   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int FocalPoint::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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


int FocalPoint::OnFocus(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(focusState_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      std::string focus;
      pProp->Get(focus);
      return SetContinuousFocusing(focus == g_On ? true : false);
   }

   return DEVICE_OK;
}

int FocalPoint::OnLaser(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(laserState_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      std::string laser;
      pProp->Get(laser);
      return SetLaser(laser == g_On ? true : false);
   }

   return DEVICE_OK;
}

int FocalPoint::OnCommand(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(commandState_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      std::string command;
      pProp->Get(command);
      return SetCommand(command == g_Local ? g_Local : g_Remote);
   }

   return DEVICE_OK;
}


int FocalPoint::OnWaitAfterLock(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(waitAfterLock_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(waitAfterLock_);
   }

   return DEVICE_OK;
}

int FocalPoint::QueryCommand(const char* command, std::string answer)
{
   if (port_ == g_Undefined)
   {
      return ERR_SERIAL_PORT_NOT_OPEN;
   }

   int ret = SendSerialCommand(port_.c_str(), command, "\n");
   if (ret != DEVICE_OK)
      return ret;

   ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);

   return ret;

}
