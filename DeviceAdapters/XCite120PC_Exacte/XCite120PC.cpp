///////////////////////////////////////////////////////////////////////////////
// FILE:         XCite120PC.cpp
// PROJECT:      Micro-Manager
// SUBSYSTEM:    DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:  This is the Micro-Manager device adapter for the X-Cite 120PC
//
// AUTHOR:       Mark Allen Neil, markallenneil@yahoo.com
//               This code reuses work done by Jannis Uhlendorf, 2010
//
//				 Modified by Lon Chu (lonchu@yahoo.com) on September 26, 2013
//				 add protection from shutter close-open sequence, shutter will be
//			     dwell an interval after cloased and before opening again
//
// COPYRIGHT:    Mission Bay Imaging, 2010-2011
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

#ifdef WIN32
   #include <windows.h>
   #define snprintf _snprintf 
#endif

#include "XCite120PC.h"
#include "../../MMDevice/ModuleInterface.h"

#include <string>
#include <math.h>
#include <time.h>
#include <algorithm>
#include <sstream>
#include <iostream>

using namespace std;

// Commands
const char* XCite120PC::cmdConnect               = "tt";
const char* XCite120PC::cmdLockFrontPanel        = "ll";
const char* XCite120PC::cmdUnlockFrontPanel      = "nn";
const char* XCite120PC::cmdClearAlarm            = "aa";
const char* XCite120PC::cmdOpenShutter           = "mm";
const char* XCite120PC::cmdCloseShutter          = "zz";
const char* XCite120PC::cmdTurnLampOn            = "bb";
const char* XCite120PC::cmdTurnLampOff           = "ss";
const char* XCite120PC::cmdGetSoftwareVersion    = "vv";
const char* XCite120PC::cmdGetLampHours          = "hh";   
const char* XCite120PC::cmdGetUnitStatus         = "uu";
const char* XCite120PC::cmdGetIntensityLevel     = "ii";
const char* XCite120PC::cmdSetIntensityLevel     = "i";

// Return codes
const char* XCite120PC::retOk                    = "";
const char* XCite120PC::retError                 = "e";

XCite120PC::XCite120PC(const char* name) :
   initialized_(false),
   deviceName_(name),
   serialPort_("Undefined"),
   shutterOpen_(false),
   frontPanelLocked_("False"),
   lampIntensity_("0"),
   lampState_("On"),
   shutterDwellTime_(0),	// shutter close setteling time initialized to 0
   timeShutterClosed_(0),	// shutter closed time initialized to 0
   lastShutterTime_(0)
{
  InitializeDefaultErrorMessages();

  CreateProperty(MM::g_Keyword_Name, deviceName_.c_str(), MM::String, true);

  CPropertyAction* pAct = new CPropertyAction(this, &XCite120PC::OnPort);
  CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

  EnableDelay();
}

XCite120PC::~XCite120PC()
{
  Shutdown();
}

int XCite120PC::Initialize()
{
   int status;
   string response;
   vector<string> allowedValues;

   LogMessage("XCite120PC: Initialization");

   // Connect to hardware
   status = ExecuteCommand(cmdConnect);
   if (status != DEVICE_OK)
      return status;

   // Clear alarm
   status = ExecuteCommand(cmdClearAlarm);
   if (status != DEVICE_OK)
      return status;

   // Lamp intensity
   CPropertyAction *pAct = new CPropertyAction(this, &XCite120PC::OnIntensity);
   CreateProperty("Lamp-Intensity", "100", MM::Integer, false, pAct);
   allowedValues.clear();
   allowedValues.push_back("0");
   allowedValues.push_back("12");
   allowedValues.push_back("25");
   allowedValues.push_back("50");
   allowedValues.push_back("100");
   SetAllowedValues("Lamp-Intensity", allowedValues);
  
   // Shutter state
   pAct = new CPropertyAction(this, &XCite120PC::OnShutterState);
   CreateProperty("Shutter-State", "Closed", MM::String, false, pAct);
   allowedValues.clear();
   allowedValues.push_back("Closed");
   allowedValues.push_back("Open");
   SetAllowedValues("Shutter-State", allowedValues);

   // Shutter dwell time by Lon Chu added on 9-26-2013
   pAct = new CPropertyAction(this, &XCite120PC::OnShutterDwellTime);
   CreateProperty("Shutter-Dwell-Time", "0",  MM::Float, false, pAct);
   SetPropertyLimits("Shutter-Dwell-Time", 0, 5000);

   // Front panel state
   pAct = new CPropertyAction(this, &XCite120PC::OnPanelLock);
   CreateProperty("Front-Panel-Lock", "False", MM::String, false, pAct);
   allowedValues.clear();
   allowedValues.push_back("True");
   allowedValues.push_back("False");
   SetAllowedValues("Front-Panel-Lock", allowedValues);
 
   // Lamp state
   pAct = new CPropertyAction(this, &XCite120PC::OnLampState);
   CreateProperty("Lamp-State", "On", MM::String, false, pAct);
   allowedValues.clear();
   allowedValues.push_back("On");
   allowedValues.push_back("Off");
   SetAllowedValues("Lamp-State", allowedValues);

   // Alarm state ("button")
   pAct = new CPropertyAction(this, &XCite120PC::OnClearAlarm);
   CreateProperty("Alarm-Clear", "Clear", MM::String, false, pAct);
   allowedValues.clear();
   allowedValues.push_back("Clear");
   SetAllowedValues("Alarm-Clear", allowedValues);
     
   // Software version
   status = ExecuteCommand(cmdGetSoftwareVersion, NULL, 0, &response);
   if (status != DEVICE_OK)
      return status;
   CreateProperty("Software-Version", response.c_str(), MM::String, true);
   
   // Lamp hours ("field")
   pAct = new CPropertyAction(this, &XCite120PC::OnGetLampHours);
   CreateProperty("Lamp-Hours", "Unknown", MM::String, true, pAct);

   // Unit status: Alarm State ("field")
   pAct = new CPropertyAction(this, &XCite120PC::OnUnitStatusAlarmState);
   CreateProperty("Unit-Status-Alarm-State", "Unknown", MM::String, true, pAct);
   
   // Unit status: Lamp State ("field")
   pAct = new CPropertyAction(this, &XCite120PC::OnUnitStatusLampState);
   CreateProperty("Unit-Status-Lamp-State", "Unknown", MM::String, true, pAct);
   
   // Unit status: Shutter State ("field")
   pAct = new CPropertyAction(this, &XCite120PC::OnUnitStatusShutterState);
   CreateProperty("Unit-Status-Shutter-State", "Unknown", MM::String, true, pAct);

   // Unit status: Home State ("field")
   pAct = new CPropertyAction(this, &XCite120PC::OnUnitStatusHome);
   CreateProperty("Unit-Status-Home", "Unknown", MM::String, true, pAct);

   // Unit status: Lamp Ready ("field")
   pAct = new CPropertyAction(this, &XCite120PC::OnUnitStatusLampReady);
   CreateProperty("Unit-Status-Lamp-Ready", "Unknown", MM::String, true, pAct);
   
   // Unit status: Front Panel ("field")
   pAct = new CPropertyAction(this, &XCite120PC::OnUnitStatusFrontPanel);
   CreateProperty("Unit-Status-Front-Panel", "Unknown", MM::String, true, pAct);


   // Update state based on existing status
   status = ExecuteCommand(cmdGetUnitStatus, NULL, 0, &response);
   if (status != DEVICE_OK)
      return status;
   status = atoi(response.c_str());
   shutterOpen_ = 0 != (status & 4);
   SetProperty("Shutter-State", shutterOpen_ ? "Open" : "Closed");
   lampState_ = 0 != (status & 2) ? "On" : "Off";
   SetProperty("Lamp-State", lampState_.c_str());
   frontPanelLocked_ = 0 != (status & 32) ? "True" : "False";
   SetProperty("Front-Panel-Lock", frontPanelLocked_.c_str());

   // Initialize intensity from existing state
   status = ExecuteCommand(cmdGetIntensityLevel, NULL, 0, &response);
   if (status != DEVICE_OK)
      return status;
   if (0 == response.compare("0"))
      lampIntensity_ = "0";
   else if (0 == response.compare("1"))
      lampIntensity_ = "12";
   else if (0 == response.compare("2"))
      lampIntensity_ = "25";
   else if (0 == response.compare("3"))
      lampIntensity_ = "50";
   else if (0 == response.compare("4"))
      lampIntensity_ = "100";

   // initialize the shutter closed time to current time
   timeShutterClosed_ = GetCurrentMMTime();
   lastShutterTime_ = GetCurrentMMTime();

   initialized_ = true;
   return DEVICE_OK;
}

int XCite120PC::Shutdown()
{
   initialized_ = false;
   return DEVICE_OK;
}

void XCite120PC::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, deviceName_.c_str());
}

bool XCite120PC::Busy()
{
   double elapsedMs = (GetCurrentMMTime() - lastShutterTime_).getMsec();
   if (elapsedMs < GetDelayMs())
   {
      return true;
   }
   return false;
}

int XCite120PC::SetOpen(bool open)
{
   if (open == shutterOpen_)
      return DEVICE_OK;

   shutterOpen_ = open;

   char cBuff[20];
   int ret = DEVICE_ERR;

   if (open)
   {
      LogMessage("XCite120PC: Open Shutter");

      // before opening the shutter
      // add these codes to make sure shutter is closed and is dwell
      // by Lon Chu

	  if (shutterDwellTime_ > 0)
	  {
		  // check if shutter is exceeding dwell time 
		  double timeElapsed = 0.0;
		  do
		  {
			 timeElapsed = (GetCurrentMMTime()-timeShutterClosed_).getMsec();
			 memset(cBuff, 0, 20);
			 sprintf(cBuff, "[%.2f]", timeElapsed), 
			 LogMessage("XCite120PC: Waiting for shuttle dwell before the shutter is reopening..." + string(cBuff));
		  } while ( timeElapsed < (double)shutterDwellTime_);
	  }

      ret = ExecuteCommand(cmdOpenShutter);
   }
   else
   {
      LogMessage("XCite120PC: Close Shutter");
	  timeShutterClosed_ = this->GetCurrentMMTime();
	  memset(cBuff, 0, 20);
	  sprintf(cBuff, "[%.2f]", timeShutterClosed_.getMsec()), 
	  LogMessage("XCite120PC: Shutter Closed Time..." + string(cBuff));

      ret = ExecuteCommand(cmdCloseShutter);
   }

   lastShutterTime_ = GetCurrentMMTime();
   return ret;
}

int XCite120PC::GetOpen(bool& open)
{
  open = shutterOpen_;
  return DEVICE_OK;
}

int XCite120PC::Fire(double /* deltaT */)
{
   // Not supported
   return DEVICE_UNSUPPORTED_COMMAND;
}

int XCite120PC::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
      pProp->Set(serialPort_.c_str());
   else if (eAct == MM::AfterSet)
   {
      if (initialized_)
      {
         // Revert
         pProp->Set(serialPort_.c_str());
         return ERR_PORT_CHANGE_FORBIDDEN;
      }
      pProp->Get(serialPort_);
      LogMessage("XCite120PC: Using Port: " + serialPort_);
   }
   return DEVICE_OK;
}
  
int XCite120PC::OnIntensity(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
      pProp->Set(lampIntensity_.c_str());
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(lampIntensity_);
      LogMessage("XCite120PC: Set Intensity: " + lampIntensity_);
      char intensity_code[] = "0";
      if (0 == lampIntensity_.compare("0"))
         intensity_code[0] = '0';
      else if (0 == lampIntensity_.compare("12"))
         intensity_code[0] = '1';
      else if (0 == lampIntensity_.compare("25"))
         intensity_code[0] = '2';
      else if (0 == lampIntensity_.compare("50"))
         intensity_code[0] = '3';
      else if (0 == lampIntensity_.compare("100"))
         intensity_code[0] = '4';
      return ExecuteCommand(cmdSetIntensityLevel, intensity_code, 1);
   }
   return DEVICE_OK;
}

int XCite120PC::OnPanelLock(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
      pProp->Set(frontPanelLocked_.c_str());
   else if (eAct==MM::AfterSet)
   {
      pProp->Get(frontPanelLocked_);
      LogMessage("XCite120PC: Front Panel Lock: " + frontPanelLocked_);
      if (0 == frontPanelLocked_.compare("True"))
         return ExecuteCommand(cmdLockFrontPanel);
      else if (0 == frontPanelLocked_.compare("False"))
         return ExecuteCommand(cmdUnlockFrontPanel);
   }
   return DEVICE_OK;
}

int XCite120PC::OnLampState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
      pProp->Set(lampState_.c_str());
   else if (eAct==MM::AfterSet)
   {
      pProp->Get(lampState_);
      LogMessage("XCite120PC: Lamp State: " + lampState_);
      if (0 == lampState_.compare("On"))
         return ExecuteCommand(cmdTurnLampOn);
      else if (0 == lampState_.compare("Off"))
         return ExecuteCommand(cmdTurnLampOff);
   }
   return DEVICE_OK;  
}

int XCite120PC::OnShutterState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
      pProp->Set(shutterOpen_ ? "Open" : "Closed");
   else if (eAct == MM::AfterSet)
   {
      string buff;
      pProp->Get(buff);
      SetOpen(0 == buff.compare("Open"));
   }
   return DEVICE_OK;
}

//
// action handler for shutter setteling time property  (Lon Chu)
//
int XCite120PC::OnShutterDwellTime(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
      pProp->Set(shutterDwellTime_);
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(shutterDwellTime_);
      char cBuffer[20];
	  memset(cBuffer, 0, 20);
      sprintf(cBuffer, "%ld", shutterDwellTime_);
      LogMessage("XCiteExacte: Shutter Setteling Time: " + string(cBuffer));
   }
   return DEVICE_OK;
}

int XCite120PC::OnClearAlarm(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
      pProp->Set("Clear");
   else if (eAct == MM::AfterSet)
   {
      LogMessage("XCite120PC: Alarm Cleared");
      return ExecuteCommand(cmdClearAlarm);      
   }
   return DEVICE_OK;
}

int XCite120PC::OnGetLampHours(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      string buff;
      ExecuteCommand(cmdGetLampHours, NULL, 0, &buff);
      pProp->Set(buff.c_str());
      SetProperty("Lamp-Hours", buff.c_str());
      LogMessage("XCiteExacte: Get Lamp Hours: " + buff);
   }
   return DEVICE_OK;
}

int XCite120PC::OnUnitStatusAlarmState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      string buff;
      int status = ExecuteCommand(cmdGetUnitStatus, NULL, 0, &buff);
      if (status != DEVICE_OK)
         return status;
      status = GetDeviceStatus(1,  &buff);
      if (status == DEVICE_OK)
      {
         pProp->Set(buff.c_str());
         LogMessage("XCite120PC: Unit Status: Alarm State: " + buff);
      }
   }
   return DEVICE_OK;
}

int XCite120PC::OnUnitStatusLampState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      string buff;
      int status = ExecuteCommand(cmdGetUnitStatus, NULL, 0, &buff);
      if (status != DEVICE_OK)
         return status;
      status = GetDeviceStatus(2,  &buff);
      if (status == DEVICE_OK)
      {
         pProp->Set(buff.c_str());
         LogMessage("XCite120PC: Unit Status: Lamp State: " + buff);
      }
   }
   return DEVICE_OK;
}

int XCite120PC::OnUnitStatusShutterState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      string buff;
      int status = ExecuteCommand(cmdGetUnitStatus, NULL, 0, &buff);
      if (status != DEVICE_OK)
         return status;
      status = GetDeviceStatus(4,  &buff);
      if (status == DEVICE_OK)
      {
         pProp->Set(buff.c_str());
         LogMessage("XCite120PC: Unit Status: Shutter State: " + buff);
      }
   }
   return DEVICE_OK;
}

int XCite120PC::OnUnitStatusHome(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      string buff;
      int status = ExecuteCommand(cmdGetUnitStatus, NULL, 0, &buff);
      if (status != DEVICE_OK)
         return status;
      status = GetDeviceStatus(8,  &buff);
      if (status == DEVICE_OK)
      {
         pProp->Set(buff.c_str());
         LogMessage("XCite120PC: Unit Status: Home State: " + buff);
      }
   }
   return DEVICE_OK;
}

int XCite120PC::OnUnitStatusLampReady(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      string buff;
      int status = ExecuteCommand(cmdGetUnitStatus, NULL, 0, &buff);
      if (status != DEVICE_OK)
         return status;
      status = GetDeviceStatus(16,  &buff);
      if (status == DEVICE_OK)
      {
         pProp->Set(buff.c_str());
         LogMessage("XCite120PC: Unit Status: Lamp Ready State: " + buff);
      }
   }
   return DEVICE_OK;
}

int XCite120PC::OnUnitStatusFrontPanel(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      string buff;
      int status = ExecuteCommand(cmdGetUnitStatus, NULL, 0, &buff);
      if (status != DEVICE_OK)
         return status;
      status = GetDeviceStatus(32,  &buff);
      if (status == DEVICE_OK)
      {
         pProp->Set(buff.c_str());
         LogMessage("XCite120PC: Unit Status: Front Panel State: " + buff);
      }
   }
   return DEVICE_OK;
}

int XCite120PC::GetDeviceStatus(int statusBit,  string* retStatus)
{
      string buff;
      int status = ExecuteCommand(cmdGetUnitStatus, NULL, 0, &buff);
      if (status != DEVICE_OK)
         return status;
      int statusReg = atoi(buff.c_str());
      switch (statusBit) {
         case 1:        // Alarm State
         case 2:        // Lamp State
            *retStatus = (0 != (statusReg & statusBit) ? "ON" : "OFF");
            break;
         case 4:        // Shutter State
            *retStatus = (0 != (statusReg & statusBit) ? "OPEN" : "CLOSED");
            break;
         case 8:        // Status Home
            *retStatus = (0 != (statusReg & statusBit) ? "FAULT" : "PASS");
            break;
         case 16:       // Lamp Ready
            *retStatus = (0 != (statusReg & statusBit) ? "READY" : "NOT READY");
            break;
         case 32:       // Front Panel
            *retStatus = (0 != (statusReg & statusBit) ? "LOCKED" : "NOT LOCKED");
            break;
         default:
            *retStatus = "Unknown";
            break;
      }
      return DEVICE_OK;
}

// Exedute a command, input, inputlen and ret are 0 by default
//   if a pointer to input and a value for inputlen is given, this input is sent to the device
//   if a pointer to ret is given, the return value of the device it returned in retVal
int XCite120PC::ExecuteCommand(const string& cmd, const char* input, int inputLen, string* retVal)
{
   char* cmd_i;

   if (input == NULL) // No input
   {
      cmd_i = new char[cmd.size()+1];
      strcpy(cmd_i,cmd.c_str());
   }
   else  // Command with input
   {
      cmd_i = new char[cmd.size() + inputLen + 1];
      strcpy(cmd_i, cmd.c_str());
      strncat(cmd_i, input, inputLen);
   }
  
   // Clear comport
   int status = PurgeComPort(serialPort_.c_str());
   if (status != DEVICE_OK)
      return status;

   // Send command
   status = SendSerialCommand(serialPort_.c_str(), cmd_i, "\r");
   if (status != DEVICE_OK)
      return status;

   delete [] cmd_i;

   // Get status
   string buff;
   status = GetSerialAnswer(serialPort_.c_str(), "\r", buff);
   if (status != DEVICE_OK)
      return status;

   if (0 == buff.compare(retError))
      return DEVICE_ERR;

   if (0 != buff.compare(retOk) && retVal == NULL)
      return DEVICE_NOT_CONNECTED;

   if (retVal != NULL) // Return value
      *retVal = buff;
   
   return DEVICE_OK;
}

