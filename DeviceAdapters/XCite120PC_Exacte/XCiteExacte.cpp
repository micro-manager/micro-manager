///////////////////////////////////////////////////////////////////////////////
// FILE:         XCiteExacte.cpp
// PROJECT:      Micro-Manager
// SUBSYSTEM:    DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:  This is the Micro-Manager device adapter for the X-Cite Exacte
//            
// AUTHOR:       Mark Allen Neil, markallenneil@yahoo.com
//               This code reuses work done by Jannis Uhlendorf, 2010
//
//				 Modified by Lon Chu (lonchu@yahoo.com) on September 26, 2013
//				 add protection from shutter close-open sequence, shutter will be
//			     dwell an interval after cloased and before opening again
//				
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

#include "XCiteExacte.h"
#include "../../MMDevice/ModuleInterface.h"

#include <string>
#include <math.h>
#include <time.h>
#include <algorithm>
#include <sstream>
#include <iostream>
#include <stdio.h>

using namespace std;

// Commands
const char* XCiteExacte::cmdConnect                  = "tt";
const char* XCiteExacte::cmdLockFrontPanel           = "ll";
const char* XCiteExacte::cmdUnlockFrontPanel         = "nn";
const char* XCiteExacte::cmdClearAlarm               = "aa";
const char* XCiteExacte::cmdOpenShutter              = "mm";
const char* XCiteExacte::cmdCloseShutter             = "zz";
const char* XCiteExacte::cmdTurnLampOn               = "bb";
const char* XCiteExacte::cmdTurnLampOff              = "ss";
const char* XCiteExacte::cmdGetSoftwareVersion       = "vv";
const char* XCiteExacte::cmdGetLampHours             = "hh";   
const char* XCiteExacte::cmdGetUnitStatus            = "uu";
const char* XCiteExacte::cmdGetIntensityLevel        = "dd";
const char* XCiteExacte::cmdSetIntensityLevel        = "d";
const char* XCiteExacte::cmdEnableExtendedCommands   = "jj";
const char* XCiteExacte::cmdEnableShutterControl     = "cc";
const char* XCiteExacte::cmdDisableShutterControl    = "yy";
const char* XCiteExacte::cmdGetSerialNumber          = "GSN";
const char* XCiteExacte::cmdIncrementIris            = "++";
const char* XCiteExacte::cmdDecrementIris            = "--";
const char* XCiteExacte::cmdChangePowerMode          = "qq";
const char* XCiteExacte::cmdEnableCLF                = "kk";
const char* XCiteExacte::cmdDisableCLF               = "gg";
const char* XCiteExacte::cmdClearCalib               = "ff";
const char* XCiteExacte::cmdGetCalibTime             = "ee";
const char* XCiteExacte::cmdSetOutputPower           = "p";
const char* XCiteExacte::cmdGetOutputPower           = "pp";
const char* XCiteExacte::cmdGetPowerFactor           = "??";

// Return codes
const char* XCiteExacte::retOk                       = "";
const char* XCiteExacte::retError                    = "e";

XCiteExacte::XCiteExacte(const char* name) :
   initialized_(false),
   deviceName_(name),
   serialPort_("Undefined"),
   shutterOpen_(false),
   frontPanelLocked_("False"),
   lampIntensity_(0),
   lampState_("On"),
   pcShutterControl_("True"),
   irisControl_("Increment"),
   powerMode_("Intensity"),
   clfMode_("Off"),
   outputPower_(0.0),
   powerFactor_("100"),
   shutterDwellTime_(0),	// shutter close setteling time initialized to 0
   timeShutterClosed_(0)	// shutter closed time
{
  InitializeDefaultErrorMessages();

  CreateProperty(MM::g_Keyword_Name, deviceName_.c_str(), MM::String, true);

  CPropertyAction* pAct = new CPropertyAction(this, &XCiteExacte::OnPort);
  CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);
}

XCiteExacte::~XCiteExacte()
{
  Shutdown();
}

int XCiteExacte::Initialize()
{
   int status;
   string response;
   char cBuff[6];
   vector<string> allowedValues;

   LogMessage("XCiteExacte: Initialization");

   // Connect to hardware
   status = ExecuteCommand(cmdConnect);
   if (status != DEVICE_OK)
      return status;

   // Clear alarm
   status = ExecuteCommand(cmdClearAlarm);
   if (status != DEVICE_OK)
      return status;

   // Enable Exacte extended commands
   status = ExecuteCommand(cmdEnableExtendedCommands);
   if (status != DEVICE_OK)
      return status;

   // Lamp intensity
   CPropertyAction *pAct = new CPropertyAction(this, &XCiteExacte::OnIntensity);
   CreateProperty("Lamp-Intensity", "0", MM::Integer, false, pAct);
   SetPropertyLimits("Lamp-Intensity", 0, 100);

   // Initialize intensity from existing state
   status = ExecuteCommand(cmdGetIntensityLevel, NULL, 0, &response);
   if (status != DEVICE_OK)
      return status;
   lampIntensity_ = (long) atoi(response.c_str());
   sprintf(cBuff, "%03d", (int) lampIntensity_);
   SetProperty("Lamp-Intensity", cBuff);

   // Shutter state
   pAct = new CPropertyAction(this, &XCiteExacte::OnShutterState);
   CreateProperty("Shutter-State", "Closed", MM::String, false, pAct);
   allowedValues.clear();
   allowedValues.push_back("Closed");
   allowedValues.push_back("Open");
   SetAllowedValues("Shutter-State", allowedValues);

   // Shutter dwell time by Lon Chu added on 9-26-2013
   pAct = new CPropertyAction(this, &XCiteExacte::OnShutterDwellTime);
   CreateProperty("Shutter-Dwell-Time", "0", MM::Integer, false, pAct);
   SetPropertyLimits("Shutter-Dwell-Time", 0, 5000);
  
   // Front panel state
   pAct = new CPropertyAction(this, &XCiteExacte::OnPanelLock);
   CreateProperty("Front-Panel-Lock", "False", MM::String, false, pAct);
   allowedValues.clear();
   allowedValues.push_back("True");
   allowedValues.push_back("False");
   SetAllowedValues("Front-Panel-Lock", allowedValues);

   // Lamp state
   pAct = new CPropertyAction(this, &XCiteExacte::OnLampState);
   CreateProperty("Lamp-State", "On", MM::String, false, pAct);
   allowedValues.clear();
   allowedValues.push_back("On");
   allowedValues.push_back("Off");
   SetAllowedValues("Lamp-State", allowedValues);

   // Alarm state ("button")
   pAct = new CPropertyAction(this, &XCiteExacte::OnClearAlarm);
   CreateProperty("Alarm-Clear", "Clear", MM::String, false, pAct);
   allowedValues.clear();
   allowedValues.push_back("Clear");
   SetAllowedValues("Alarm-Clear", allowedValues);

   // PC shutter control
   pAct = new CPropertyAction(this, &XCiteExacte::OnPcShutterControl);
   CreateProperty("PC-Shutter-Control", "True", MM::String, false, pAct);
   allowedValues.clear();
   allowedValues.push_back("True");
   allowedValues.push_back("False");
   SetAllowedValues("PC-Shutter-Control", allowedValues);

   // Iris control
   pAct = new CPropertyAction(this, &XCiteExacte::OnIrisControl);
   CreateProperty("Iris-Control", "Increment", MM::String, false, pAct);
   allowedValues.clear();
   allowedValues.push_back("Increment");
   allowedValues.push_back("Decrement");
   SetAllowedValues("Iris-Control", allowedValues);

   // Power mode
   pAct = new CPropertyAction(this, &XCiteExacte::OnPowerMode);
   CreateProperty("Power-Mode", "Intensity", MM::String, false, pAct);
   allowedValues.clear();
   allowedValues.push_back("Intensity");
   allowedValues.push_back("Power");
   SetAllowedValues("Power-Mode", allowedValues);

   // CLF mode
   pAct = new CPropertyAction(this, &XCiteExacte::OnClfMode);
   CreateProperty("CLF-Mode", "Off", MM::String, false, pAct);
   allowedValues.clear();
   allowedValues.push_back("Off");
   allowedValues.push_back("On");
   SetAllowedValues("CLF-Mode", allowedValues);

   // Clear calibration ("button")
   pAct = new CPropertyAction(this, &XCiteExacte::OnClearCalib);
   CreateProperty("Clear-Calibration", "Clear", MM::String, false, pAct);
   allowedValues.clear();
   allowedValues.push_back("Clear");
   SetAllowedValues("Clear-Calibration", allowedValues);

   // Calibration time ("field")
   pAct = new CPropertyAction(this, &XCiteExacte::OnGetCalibTime);
   CreateProperty("Calibration-Time", "Unknown", MM::Integer, true, pAct);

   // Power factor ("field")
   pAct = new CPropertyAction(this, &XCiteExacte::OnGetPowerFactor);
   CreateProperty("Power-Factor", "Unknown", MM::String, true, pAct);

   // Ensure that the power factor is read from the device before we determine
   // the limits for the Output-Power property.
   char tmp[MM::MaxStrLength];
   GetProperty("Power-Factor", tmp);

   // Output power ("slider")
   pAct = new CPropertyAction(this, &XCiteExacte::OnOutputPower);
   CreateProperty("Output-Power", "0", MM::Float, false, pAct);
   //----------------------------------------------------------------------------------
   // The limits on the output power slider depend on the power factor per the formula
   //   xxxxx = desired_power_in_watts * power_factor * 100 (maximum = 99999)
   // Power factor can be 100, 1000 or 10000
   // Therefore, when power factor =
   //    100      desired_power_in_watts range is [0 .. 9.9999]
   //    1000     desired_power_in_watts range is [0 .. 0.99999]
   //    10000    desired_power_in_watts range is [0 .. 0.099999]
   // Given these ranges...
   //    When PF = 100, the slider will display W
   //    When PF > 100, the slider will display mW
   //----------------------------------------------------------------------------------
   if ("100" == powerFactor_)
      SetPropertyLimits("Output-Power", 0, 9.9999);
   else if ("1000" == powerFactor_)
      SetPropertyLimits("Output-Power", 0, 999.99);
   else
      SetPropertyLimits("Output-Power", 0, 99.999);
    
   // Unlock front panel
   SetProperty("Front-Panel-Lock", frontPanelLocked_.c_str());

   // Enable PC control of shutter
   status = ExecuteCommand(cmdEnableShutterControl);
   if (status != DEVICE_OK)
      return status;

   // Software version ("field")
   status = ExecuteCommand(cmdGetSoftwareVersion, NULL, 0, &response);
   if (status != DEVICE_OK)
      return status;
   CreateProperty("Software-Version", response.c_str(), MM::String, true);

   // Serial number ("field")
   status = ExecuteCommand(cmdGetSerialNumber, NULL, 0, &response);
   if (status != DEVICE_OK)
      return status;
   CreateProperty("Serial-Number", response.c_str(), MM::String, true);

   // Lamp hours ("field")
   pAct = new CPropertyAction(this, &XCiteExacte::OnGetLampHours);
   CreateProperty("Lamp-Hours", "Unknown", MM::String, true, pAct);

   // Unit status: Alarm State ("field")
   pAct = new CPropertyAction(this, &XCiteExacte::OnUnitStatusAlarmState);
   CreateProperty("Unit-Status-Alarm-State", "Unknown", MM::String, true, pAct);
   
   // Unit status: Lamp State ("field")
   pAct = new CPropertyAction(this, &XCiteExacte::OnUnitStatusLampState);
   CreateProperty("Unit-Status-Lamp-State", "Unknown", MM::String, true, pAct);
   
   // Unit status: Shutter State ("field")
   pAct = new CPropertyAction(this, &XCiteExacte::OnUnitStatusShutterState);
   CreateProperty("Unit-Status-Shutter-State", "Unknown", MM::String, true, pAct);

   // Unit status: Home State ("field")
   pAct = new CPropertyAction(this, &XCiteExacte::OnUnitStatusHome);
   CreateProperty("Unit-Status-Home", "Unknown", MM::String, true, pAct);

   // Unit status: Lamp Ready ("field")
   pAct = new CPropertyAction(this, &XCiteExacte::OnUnitStatusLampReady);
   CreateProperty("Unit-Status-Lamp-Ready", "Unknown", MM::String, true, pAct);
   
   // Unit status: Front Panel ("field")
   pAct = new CPropertyAction(this, &XCiteExacte::OnUnitStatusFrontPanel);
   CreateProperty("Unit-Status-Front-Panel", "Unknown", MM::String, true, pAct);

   // Unit status: Power Mode ("field")
   pAct = new CPropertyAction(this, &XCiteExacte::OnUnitStatusPowerMode);
   CreateProperty("Unit-Status-Power-Mode", "Unknown", MM::String, true, pAct);
   
   // Unit status: Exacte Mode ("field")
   pAct = new CPropertyAction(this, &XCiteExacte::OnUnitStatusExacteMode);
   CreateProperty("Unit-Status-Exacte-Mode", "Unknown", MM::String, true, pAct);
   
   // Unit status: Light Guide Inserted ("field")
   pAct = new CPropertyAction(this, &XCiteExacte::OnUnitStatusLightGuideInserted);
   CreateProperty("Unit-Status-Light-Guide-Inserted", "Unknown", MM::String, true, pAct);

   // Unit status: Closed Loop Feedback (CLF) Mode ("field")
   pAct = new CPropertyAction(this, &XCiteExacte::OnUnitStatusCLFMode);
   CreateProperty("Unit-Status-CLF-Mode", "Unknown", MM::String, true, pAct);
   
   // Unit status: Iris Moving ("field")
   pAct = new CPropertyAction(this, &XCiteExacte::OnUnitStatusIrisMoving);
   CreateProperty("Unit-Status-Iris-Moving", "Unknown", MM::String, true, pAct);

   // Update state based on existing status
   status = ExecuteCommand(cmdGetUnitStatus, NULL, 0, &response);
   if (status != DEVICE_OK)
      return status;
   status = atoi(response.c_str());
   shutterOpen_ = 0 != (status & 4);
   SetProperty("Shutter-State", shutterOpen_ ? "Open" : "Closed");
   //SetProperty("Shutter-Dwell-Time", shutterDwellTime_);
   lampState_ = 0 != (status & 2) ? "On" : "Off";
   SetProperty("Lamp-State", lampState_.c_str());
   frontPanelLocked_ = 0 != (status & 32) ? "True" : "False";
   SetProperty("Front-Panel-Lock", frontPanelLocked_.c_str());
   powerMode_ = 0 != (status & 128) ? "Power" : "Intensity";
   SetProperty("Power-Mode", powerMode_.c_str());
   clfMode_ = 0 != (status & 16384) ? "On" : "Off";
   SetProperty("CLF-Mode", clfMode_.c_str());

   // initialize the shutter closed time to current time
   timeShutterClosed_ = GetCurrentMMTime();

   initialized_ = true;
   return DEVICE_OK;
}

int XCiteExacte::Shutdown()
{
   if (initialized_) 
   {
      int status;

      // Unlock front panel
      status = ExecuteCommand(cmdUnlockFrontPanel);
      if (status != DEVICE_OK)
         return status;

      // Disable PC control of shutter
      status = ExecuteCommand(cmdDisableShutterControl);
      if (status != DEVICE_OK)
         return status;

      initialized_ = false;
   }
   return DEVICE_OK;
}

void XCiteExacte::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, deviceName_.c_str());
}

bool XCiteExacte::Busy()
{
   // All commands wait for a response, so we should never be busy
   return false;
}

int XCiteExacte::SetOpen(bool open)
{

   shutterOpen_ = open;

   char cBuff[20];

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
			 timeElapsed = (long) (GetCurrentMMTime()-timeShutterClosed_).getMsec();
			 memset(cBuff, 0, 20);
			 sprintf(cBuff, "[%.2f]", timeElapsed), 
			 LogMessage("XCite120PC: Waiting for shuttle dwell before the shutter is reopending..." + string(cBuff));
		  } while ( timeElapsed < (double)shutterDwellTime_);
	  }

      return ExecuteCommand(cmdOpenShutter);
   }
   else
   {
      LogMessage("XCite120PC: Close Shutter");
	  timeShutterClosed_ = this->GetCurrentMMTime();
	  memset(cBuff, 0, 20);
	  sprintf(cBuff, "[%.2f]", timeShutterClosed_.getMsec()), 
	  LogMessage("XCite120PC: Shutter Closed Time..." + string(cBuff));

      return ExecuteCommand(cmdCloseShutter);
   }
}

int XCiteExacte::GetOpen(bool& open)
{
  open = shutterOpen_;
  return DEVICE_OK;
}

int XCiteExacte::Fire(double /* deltaT */)
{
   // Not supported
   return DEVICE_UNSUPPORTED_COMMAND;
}

int XCiteExacte::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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
      LogMessage("XCiteExacte: Using Port: " + serialPort_);
   }
   return DEVICE_OK;
}
  
int XCiteExacte::OnIntensity(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
      pProp->Set(lampIntensity_);
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(lampIntensity_);
      char cBuff[4];
      sprintf(cBuff, "%03d", (int) lampIntensity_);
      LogMessage("XCiteExacte: Set Intensity: " + string(cBuff));
      ExecuteCommand(cmdSetIntensityLevel, cBuff, 3);
   }
   return DEVICE_OK;
}

int XCiteExacte::OnPanelLock(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
      pProp->Set(frontPanelLocked_.c_str());
   else if (eAct==MM::AfterSet)
   {
      pProp->Get(frontPanelLocked_);
      LogMessage("XCiteExacte: Front Panel Lock: " + frontPanelLocked_);
      if (0 == frontPanelLocked_.compare("True"))
         return ExecuteCommand(cmdLockFrontPanel);
      else if (0 == frontPanelLocked_.compare("False"))
         return ExecuteCommand(cmdUnlockFrontPanel);
   }
   return DEVICE_OK;
}

int XCiteExacte::OnLampState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
      pProp->Set(lampState_.c_str());
   else if (eAct==MM::AfterSet)
   {
      pProp->Get(lampState_);
      LogMessage("XCiteExacte: Lamp State: " + lampState_);
      if (0 == lampState_.compare("On"))
         return ExecuteCommand(cmdTurnLampOn);
      else if (0 == lampState_.compare("Off"))
         return ExecuteCommand(cmdTurnLampOff);
   }
   return DEVICE_OK;  
}

int XCiteExacte::OnShutterState(MM::PropertyBase* pProp, MM::ActionType eAct)
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
int XCiteExacte::OnShutterDwellTime(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
      pProp->Set(shutterDwellTime_);
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(shutterDwellTime_);
      char cBuffer[20];
	  memset(cBuffer, 0, 20);
      sprintf(cBuffer, "%ld", shutterDwellTime_);
      LogMessage("XCiteExacte: Shutter Dwell Time: " + string(cBuffer));
   }
   return DEVICE_OK;
}

int XCiteExacte::OnClearAlarm(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
      pProp->Set("Clear");
   else if (eAct == MM::AfterSet)
   {
      LogMessage("XCiteExacte: Alarm Cleared");
      return ExecuteCommand(cmdClearAlarm);      
   }
   return DEVICE_OK;
}

int XCiteExacte::OnPcShutterControl(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
      pProp->Set(pcShutterControl_.c_str());
   else if (eAct==MM::AfterSet)
   {
      pProp->Get(pcShutterControl_);
      LogMessage("XCiteExacte: PC Shutter Control: " + pcShutterControl_);
      if (0 == pcShutterControl_.compare("True"))
         return ExecuteCommand(cmdEnableShutterControl);
      else if (0 == pcShutterControl_.compare("False"))
         return ExecuteCommand(cmdDisableShutterControl);
   }
   return DEVICE_OK;
}

int XCiteExacte::OnIrisControl(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
      pProp->Set(irisControl_.c_str());
   else if (eAct==MM::AfterSet)
   {
      pProp->Get(irisControl_);
      LogMessage("XCiteExacte: Iris: " + irisControl_);
      if (0 == irisControl_.compare("Increment"))
         return ExecuteCommand(cmdIncrementIris);
      else if (0 == irisControl_.compare("Decrement"))
         return ExecuteCommand(cmdDecrementIris);
   }
   return DEVICE_OK;
}

int XCiteExacte::OnPowerMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
      pProp->Set(powerMode_.c_str());
   else if (eAct==MM::AfterSet)
   {
      pProp->Get(powerMode_);
      LogMessage("XCiteExacte: Power Mode: " + powerMode_);
      if (0 == powerMode_.compare("Power"))
         return ExecuteCommand(cmdChangePowerMode);
      else if (0 == powerMode_.compare("Intensity"))
         return ExecuteCommand(cmdChangePowerMode);
   }
   return DEVICE_OK;
}

int XCiteExacte::OnClfMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
      pProp->Set(clfMode_.c_str());
   else if (eAct==MM::AfterSet)
   {
      pProp->Get(clfMode_);
      LogMessage("XCiteExacte: CLF Mode: " + clfMode_);
      if (0 == clfMode_.compare("Off"))
         return ExecuteCommand(cmdDisableCLF);
      else if (0 == clfMode_.compare("On"))
         return ExecuteCommand(cmdEnableCLF);
   }
   return DEVICE_OK;
}

int XCiteExacte::OnClearCalib(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
      pProp->Set("Clear");
   else if (eAct==MM::AfterSet)
   {
      string buff;
      pProp->Get(buff);
      LogMessage("XCiteExacte: Calib Control: " + buff);
      if (0 == buff.compare("Clear"))
         return ExecuteCommand(cmdClearCalib);
   }
   return DEVICE_OK;
}

int XCiteExacte::OnGetCalibTime(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct==MM::BeforeGet)
   {
      string buff;
      ExecuteCommand(cmdGetCalibTime, NULL, 0, &buff);
      pProp->Set((long) atoi(buff.c_str()));
      LogMessage("XCiteExacte: Get Calib Time: " + buff);
   }
   return DEVICE_OK;
}

int XCiteExacte::OnOutputPower(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
      pProp->Set(outputPower_);
   else if (eAct==MM::AfterSet)
   {
      pProp->Get(outputPower_);
      char cBuff[7];
      // Convert output power to correct units and format as a 5-digit integer
      //   per the formula xxxxx = desired_power_in_watts * power_factor * 100
      // Also recall the decision that when ...
      //    power factor = 100, output power unit is W
      //    power factor > 100, output power unit is mW
      if ("100" == powerFactor_)
         sprintf(cBuff, "%05d", (int) (outputPower_ * 100 * 100));
      else if ("1000" == powerFactor_)
         sprintf(cBuff, "%05d", (int) ((outputPower_ / 1000) * 1000 * 100));
      else
         sprintf(cBuff, "%05d", (int) ((outputPower_ / 1000) * 10000 * 100));
      LogMessage("XCiteExacte: Set Output Power: " + string(cBuff));
      ExecuteCommand(cmdSetOutputPower, cBuff, 5);
   }
   return DEVICE_OK;
}

int XCiteExacte::OnGetPowerFactor(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      string buff;
      ExecuteCommand(cmdGetPowerFactor, NULL, 0, &buff);
      // Verify the returned power factor is a valid number (older units fail)
      if (0 != atoi(buff.c_str()))
         powerFactor_ = buff;
      LogMessage("XCiteExacte: Get Power Factor: " + powerFactor_);
      SetProperty("Power-Factor", powerFactor_.c_str());
      pProp->Set(powerFactor_.c_str());
   }
   return DEVICE_OK;
}

int XCiteExacte::OnGetLampHours(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int XCiteExacte::OnUnitStatusAlarmState(MM::PropertyBase* pProp, MM::ActionType eAct)
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
         LogMessage("XCiteExacte: Unit Status: Alarm State: " + buff);
      }
   }
   return DEVICE_OK;
}

int XCiteExacte::OnUnitStatusLampState(MM::PropertyBase* pProp, MM::ActionType eAct)
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
         LogMessage("XCiteExacte: Unit Status: Lamp State: " + buff);
      }
   }
   return DEVICE_OK;
}

int XCiteExacte::OnUnitStatusShutterState(MM::PropertyBase* pProp, MM::ActionType eAct)
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
         LogMessage("XCiteExacte: Unit Status: Shutter State: " + buff);
      }
   }
   return DEVICE_OK;
}

int XCiteExacte::OnUnitStatusHome(MM::PropertyBase* pProp, MM::ActionType eAct)
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
         LogMessage("XCiteExacte: Unit Status: Home State: " + buff);
      }
   }
   return DEVICE_OK;
}

int XCiteExacte::OnUnitStatusLampReady(MM::PropertyBase* pProp, MM::ActionType eAct)
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
         LogMessage("XCiteExacte: Unit Status: Lamp Ready State: " + buff);
      }
   }
   return DEVICE_OK;
}

int XCiteExacte::OnUnitStatusFrontPanel(MM::PropertyBase* pProp, MM::ActionType eAct)
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
         LogMessage("XCiteExacte: Unit Status: Front Panel State: " + buff);
      }
   }
   return DEVICE_OK;
}

int XCiteExacte::OnUnitStatusPowerMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      string buff;
      int status = ExecuteCommand(cmdGetUnitStatus, NULL, 0, &buff);
      if (status != DEVICE_OK)
         return status;
      status = GetDeviceStatus(128,  &buff);
      if (status == DEVICE_OK)
      {
         pProp->Set(buff.c_str());
         LogMessage("XCiteExacte: Unit Status: Power Mode State: " + buff);
      }
   }
   return DEVICE_OK;
}

int XCiteExacte::OnUnitStatusExacteMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      string buff;
      int status = ExecuteCommand(cmdGetUnitStatus, NULL, 0, &buff);
      if (status != DEVICE_OK)
         return status;
      status = GetDeviceStatus(256,  &buff);
      if (status == DEVICE_OK)
      {
         pProp->Set(buff.c_str());
         LogMessage("XCiteExacte: Unit Status: Exacte Mode State: " + buff);
      }
   }
   return DEVICE_OK;
}

int XCiteExacte::OnUnitStatusLightGuideInserted(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      string buff;
      int status = ExecuteCommand(cmdGetUnitStatus, NULL, 0, &buff);
      if (status != DEVICE_OK)
         return status;
      status = GetDeviceStatus(1024,  &buff);
      if (status == DEVICE_OK)
      {
         pProp->Set(buff.c_str());
         LogMessage("XCiteExacte: Unit Status: Light Guide Inserted State: " + buff);
      }
   }
   return DEVICE_OK;
}

int XCiteExacte::OnUnitStatusCLFMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      string buff;
      int status = ExecuteCommand(cmdGetUnitStatus, NULL, 0, &buff);
      if (status != DEVICE_OK)
         return status;
      status = GetDeviceStatus(16384,  &buff);
      if (status == DEVICE_OK)
      {
         pProp->Set(buff.c_str());
         LogMessage("XCiteExacte: Unit Status: CLF Mode State: " + buff);
      }
   }
   return DEVICE_OK;
}

int XCiteExacte::OnUnitStatusIrisMoving(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      string buff;
      int status = ExecuteCommand(cmdGetUnitStatus, NULL, 0, &buff);
      if (status != DEVICE_OK)
         return status;
      status = GetDeviceStatus(32768,  &buff);
      if (status == DEVICE_OK)
      {
         pProp->Set(buff.c_str());
         LogMessage("XCiteExacte: Unit Status: Iris Moving State: " + buff);
      }
   }
   return DEVICE_OK;
}

int XCiteExacte::GetDeviceStatus(int statusBit,  string* retStatus)
{
      string buff;
      int status = ExecuteCommand(cmdGetUnitStatus, NULL, 0, &buff);
      if (status != DEVICE_OK)
         return status;
      int statusReg = atoi(buff.c_str());
      switch (statusBit) {
         case 1:        // Alarm State
         case 2:        // Lamp State
         case 256:      // Exacte Mode
         case 16384:    // CLF Mode
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
         case 128:      // Power Mode
            *retStatus = (0 != (statusReg & statusBit) ? "POWER" : "INTENSITY");
            break;
         case 1024:     // Light Guide Inserted
            *retStatus = (0 != (statusReg & statusBit) ? "TRUE" : "FALSE");
            break;
         case 32768:    // Iris Moving
            // The following is the opposite of what the manual says... determined by observation
            *retStatus = (0 != (statusReg & statusBit) ? "TRUE" : "FALSE");
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
int XCiteExacte::ExecuteCommand(const string& cmd, const char* input, int inputLen, string* retVal)
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

