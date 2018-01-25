///////////////////////////////////////////////////////////////////////////////
// FILE:          CoherentScientificRemote.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Coherent Scientific Remote laser controller adapter 
//			      Support for the Coherent Scientific Remote for up to 6 Coherent Obis lasers and the Single Laser Remote 
//    
// COPYRIGHT:     35037 Marburg, Germany
//                Max Planck Institute for Terrestrial Microbiology, 2017 
//
// AUTHOR:        Raimo Hartmann
//                Adapted from CoherentScientificRemote driver written by Forrest Collman
//
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

#ifdef WIN32
   #include <windows.h>
   #define snprintf _snprintf 
#endif

#include "CoherentScientificRemote.h"

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/ModuleInterface.h"
#include "../../MMDevice/DeviceUtils.h"

#include <algorithm>
#include <math.h>
#include <sstream>
#include <string>
#include <cstring>



// Controller
const char* g_ControllerName = "Coherent-Scientific Remote";
const char* g_Keyword_PowerSetpointPercent = "PowerSetpoint (%)";
const char* g_Keyword_PowerReadback = "PowerReadback (mW)";
const char* g_Keyword_TriggerNum = "Shutter Laser";

const char * carriage_return = "\n\r";
const char * line_feed = "\n";

const char* const g_Msg_LASERS_MISSING = "Label not defined";

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_ControllerName, MM::ShutterDevice, "CoherentScientificRemote Laser");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_ControllerName) == 0)
   {
      // create Controller
      CoherentScientificRemote* pCoherentScientificRemote = new CoherentScientificRemote(g_ControllerName);
      return pCoherentScientificRemote;
   }

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
// Controller implementation
// ~~~~~~~~~~~~~~~~~~~~

CoherentScientificRemote::CoherentScientificRemote(const char* name) :
   initialized_(false), 
   state_(0),
   descriptionToken_("*IDN"),
   modulation_("Extern-Digital"),
   name_(name), 
   error_(0),
   changedTime_(0.0),
   queryToken_("?"),
   powerSetpointToken_("SOUR{laserNum}:POW:LEV:IMM:AMPL"),
   powerReadbackToken_("SOUR{laserNum}:POW:LEV:IMM:AMPL"),
   CDRHToken_("CDRH"),  // if this is on, laser delays 5 SEC before turning on
   CWToken_("CW"),
   laserOnToken_("SOUR{laserNum}:AM:STATE"),
   TECServoToken_("T"),
   headSerialNoToken_("SYST{laserNum}:INF:SNUM"),
   headUsageHoursToken_("SYST{laserNum}:DIOD:HOUR"),
   wavelengthToken_("SYST{laserNum}:INF:WAV"),
   modulationReadbackToken_("SOUR{laserNum}:AM:SOUR"),
   modulationSetpointEXTToken_("SOUR{laserNum}:AM:EXT"),
   modulationSetpointINTToken_("SOUR{laserNum}:AM:INT"),
   temperatureDiodeToken_("SOUR{laserNum}:TEMP:DIOD"),
   temperatureInternalToken_("SOUR{laserNum}:TEMP:INT"),
   temperatureBaseToken_("SOUR{laserNum}:TEMP:BAS"),
   externalPowerControlToken_("SOUR{laserNum}:POW:LEV:IMM:AMPL"),
   maxPowerToken_("SOUR{laserNum}:POW:LIM:HIGH"),
   minPowerToken_("SOUR{laserNum}:POW:LIM:LOW"),
   laserHandToken_("SYST{laserNum}:COMM:HAND"),
   laserPromToken_("SYST{laserNum}:COMM:PROM"),
   laserErrorToken_("SYST{laserNum}:ERR:CLE"),
   triggerNum_(0),
   g_LaserStr_("Laser 1:")
   

{
   assert(strlen(name) < (unsigned int) MM::MaxStrLength);

   InitializeDefaultErrorMessages();
   SetErrorText(ERR_DEVICE_NOT_FOUND, "No answer received. Is the Coherent Scientific Remote connected to this serial port?");
   SetErrorText(DEVICE_ERR, "Coherent Scientific Remote found, but without connected lasers... Please turn off the remote, connect a laser and restart everything.");

   // create pre-initialization properties
   // ------------------------------------


   // Port
   CPropertyAction* pAct = new CPropertyAction (this, &CoherentScientificRemote::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

   EnableDelay(); // signals that the delay setting will be used
   UpdateStatus();

}

CoherentScientificRemote::~CoherentScientificRemote()
{
   Shutdown();
}

bool CoherentScientificRemote::Busy()
{
   MM::MMTime interval = GetCurrentMMTime() - changedTime_;
   MM::MMTime delay(GetDelayMs()*1000.0);
   if (interval < delay)
      return true;
   else
      return false;
}

void CoherentScientificRemote::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

std::string CoherentScientificRemote::replaceLaserNum(std::string inputToken, long laserNum)
{
	std::string toReplace;
	std::ostringstream replaceWith;
	toReplace = "{laserNum}";
	replaceWith << laserNum;

    return(inputToken.replace(inputToken.find(toReplace), toReplace.length(), replaceWith.str()));
}

int CoherentScientificRemote::Initialize()
{
   LogMessage("CoherentScientificRemote::Initialize()yes??");
   
   long laserCount;
   std::string init, laserName, laserErr;
   int connectedLasers;

   // Check COM-Port
   init = this->queryLaser("*IDN");
   std::transform(init.begin(), init.end(), init.begin(), ::tolower);
   if (init.find("coherent") != 0) {
	   error_ = ERR_DEVICE_NOT_FOUND;
	   return HandleErrors();
   }
   
   CPropertyAction* pAct = new CPropertyAction (this, &CoherentScientificRemote::OnRemoteDescription);
   CreateProperty(MM::g_Keyword_Description, g_ControllerName, MM::String, true, pAct);
   
   // Add selector for trigger
   pAct = new CPropertyAction(this, &CoherentScientificRemote::OnTriggerNum);
   CreateProperty(g_Keyword_TriggerNum, "None", MM::String, false, pAct);

   //Initialize laser
   //Loop over lasers
   connectedLasers = 0;
   for( laserCount = 1; laserCount <=6; laserCount++)
    {
	   std::stringstream laserCountStr;
	   laserName = this->queryLaser(replaceLaserNum("SYST{laserNum}:INF:MOD", laserCount).c_str());
	   if (laserName.find("ERR") != 0) {
			//
		   setLaser(replaceLaserNum(laserHandToken_, laserCount).c_str(),"On");
		   setLaser(replaceLaserNum(laserPromToken_, laserCount).c_str(),"Off");
		   laserErr = this->queryLaser(replaceLaserNum(laserErrorToken_, laserCount).c_str());

		   g_LaserStr_ = laserName;
		   GeneratePowerProperties(laserCount);
		   GeneratePropertyState(laserCount);
		   GenerateReadOnlyIDProperties(laserCount);
			
		   laserCountStr << laserCount << " (" << g_LaserStr_ << ")";

		   AddAllowedValue(g_Keyword_TriggerNum, laserCountStr.str().c_str());
		   triggerNum_ = (int) laserCount;
		   connectedLasers = connectedLasers + 1;
	   }
    }
   if (connectedLasers == 0)
   {
	   error_ = DEVICE_ERR;
	   return HandleErrors();
   } 

   initialized_ = true;

   return HandleErrors();
}


/////////////////////////////////////////////
// Property Generators
/////////////////////////////////////////////

void CoherentScientificRemote::GeneratePropertyState(long laserNum)
{
   std::stringstream parameterName, laserNumField;

   CPropertyActionEx* pActEx = new CPropertyActionEx(this, &CoherentScientificRemote::OnState, laserNum);
   parameterName.str("");
   parameterName << "Laser " << g_LaserStr_ << " - " << MM::g_Keyword_State;
   CreateProperty(parameterName.str().c_str(), "Off", MM::String, false, pActEx);
   AddAllowedValue(parameterName.str().c_str(), "Off");
   AddAllowedValue(parameterName.str().c_str(), "On");

   pActEx = new CPropertyActionEx(this, &CoherentScientificRemote::OnModulation, laserNum);
   parameterName.str("");
   parameterName << "Laser " << g_LaserStr_ << " - " << "Modulation/Trigger";
   CreateProperty(parameterName.str().c_str(), "CW (constant power)", MM::String, false, pActEx);
   AddAllowedValue(parameterName.str().c_str(), "CW (constant power)");
   AddAllowedValue(parameterName.str().c_str(), "CW (constant current)");
   AddAllowedValue(parameterName.str().c_str(), "External/Digital");
   AddAllowedValue(parameterName.str().c_str(), "External/Analog");
   AddAllowedValue(parameterName.str().c_str(), "External/Mixed");
}


void CoherentScientificRemote::GeneratePowerProperties(long laserNum)
{
   std::stringstream parameterName;

   // Power Setpoint %
   CPropertyActionEx* pActEx = new CPropertyActionEx(this, &CoherentScientificRemote::OnPowerSetpointPercent, laserNum);
   parameterName << "Laser " << g_LaserStr_ << " - " << g_Keyword_PowerSetpointPercent;
   CreateProperty(parameterName.str().c_str(), "0", MM::Float, false, pActEx);
   SetPropertyLimits(parameterName.str().c_str(), 0, 100); 

   // Power Readback
   pActEx = new CPropertyActionEx(this, &CoherentScientificRemote::OnPowerReadback, laserNum);
   parameterName.str("");
   parameterName << "Laser " << g_LaserStr_ << " - " << g_Keyword_PowerReadback;
   CreateProperty(parameterName.str().c_str(), "0", MM::Float, true, pActEx);
}


void CoherentScientificRemote::GenerateReadOnlyIDProperties(long laserNum)
{
   std::stringstream parameterName, laserNumStr;

   CPropertyActionEx* pActEx; 

   pActEx = new CPropertyActionEx(this, &CoherentScientificRemote::OnHeadUsageHours, laserNum);
   parameterName.str("");
   parameterName << "Laser " << g_LaserStr_ << " - " << "Head Usage (h)";
   CreateProperty(parameterName.str().c_str(), "", MM::Float, true, pActEx);

   pActEx = new CPropertyActionEx(this, &CoherentScientificRemote::OnLaserPort, laserNum);
   parameterName.str("");
   parameterName << "Laser " << g_LaserStr_ << " - " << "Port";
   laserNumStr << laserNum;
   CreateProperty(parameterName.str().c_str(), laserNumStr.str().c_str(), MM::String, true, pActEx);

   pActEx = new CPropertyActionEx(this, &CoherentScientificRemote::OnMinimumLaserPower, laserNum);
   parameterName.str("");
   parameterName << "Laser " << g_LaserStr_ << " - " << "Minimum Laser Power (mW)";
   CreateProperty(parameterName.str().c_str(), "", MM::Float, true, pActEx);
   
   pActEx = new CPropertyActionEx(this, &CoherentScientificRemote::OnMaximumLaserPower, laserNum);
   parameterName.str("");
   parameterName << "Laser " << g_LaserStr_ << " - " << "Maximum Laser Power (mW)";
   CreateProperty(parameterName.str().c_str(), "", MM::Float, true, pActEx);

   pActEx = new CPropertyActionEx(this, &CoherentScientificRemote::OnWaveLength, laserNum);
   parameterName.str("");
   parameterName << "Laser " << g_LaserStr_ << " - " << "Wavelength (nm)";
   CreateProperty(parameterName.str().c_str(), "", MM::Float, true, pActEx);

   //pActEx = new CPropertyActionEx(this, &CoherentScientificRemote::OnTemperatureDiode, laserNum);
   //parameterName.str("");
   //parameterName << "Laser " << g_LaserStr_ << " - " << "Temperature Diode (C)";
   //CreateProperty(parameterName.str().c_str(), "", MM::Float, true, pActEx);

   //pActEx = new CPropertyActionEx(this, &CoherentScientificRemote::OnTemperatureInternal, laserNum);
   //parameterName.str("");
   //parameterName << "Laser " << g_LaserStr_ << " - " << "Temperature Internal (C)";
   //CreateProperty(parameterName.str().c_str(), "", MM::Float, true, pActEx);

   pActEx = new CPropertyActionEx(this, &CoherentScientificRemote::OnTemperatureBase, laserNum);
   parameterName.str("");
   parameterName << "Laser " << g_LaserStr_ << " - " << "Temperature Baseplate (C)";
   CreateProperty(parameterName.str().c_str(), "", MM::Float, true, pActEx);
}

int CoherentScientificRemote::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return HandleErrors();
}


///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int CoherentScientificRemote::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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

   return HandleErrors();
}

int CoherentScientificRemote::OnLaserPort(MM::PropertyBase* /*pProp*/, MM::ActionType /*eAct*/, long /*laserNum*/)
{
   return HandleErrors();
}

int CoherentScientificRemote::OnPowerReadback(MM::PropertyBase* pProp, MM::ActionType eAct, long laserNum)
{
   double powerReadback;
   if (eAct == MM::BeforeGet)
   {
      GetPowerReadback(powerReadback, laserNum);
      pProp->Set(powerReadback);
   }
   else if (eAct == MM::AfterSet)
   {
      // never do anything!!
   }
   return HandleErrors();
}

int CoherentScientificRemote::OnPowerSetpointPercent(MM::PropertyBase* pProp, MM::ActionType eAct, long laserNum)
{
   double powerSetpointPercent;
   if (eAct == MM::BeforeGet)
   {
      GetPowerSetpointPercent(powerSetpointPercent, laserNum);
      pProp->Set(powerSetpointPercent);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(powerSetpointPercent);
      double achievedSetpointPercent;
      SetPowerSetpointPercent(powerSetpointPercent, achievedSetpointPercent, laserNum);
      if( 0. != powerSetpointPercent)
      {
         double fractionError = fabs(achievedSetpointPercent - powerSetpointPercent) / powerSetpointPercent;
         if (( 0.05 < fractionError ) && (fractionError  < 0.10))
            pProp->Set(achievedSetpointPercent);
      }
   }
   return HandleErrors();
}

int CoherentScientificRemote::OnState(MM::PropertyBase* pProp, MM::ActionType eAct, long laserNum)
{
   if (eAct == MM::BeforeGet)
   {
      GetState(state_, laserNum);
	  if (state_ == 1) {
		pProp->Set("On");
	  } 
	  if (state_ == 0) {
		pProp->Set("Off");
	  }
	  if (state_ == 2) {
		pProp->Set("Error: no laser connected");
	  }
   }
   else if (eAct == MM::AfterSet)
   {
      long requestedState;
	  std::string requestedStateString;

      pProp->Get(requestedStateString);
	  if (requestedStateString.compare("On") == 0) {
		  requestedState = 1;
	  } else {
		  requestedState = 0;
	  }
      SetState(requestedState, laserNum);
      if (state_ != requestedState)
      {
         // error
      }
   }
   
   return HandleErrors();
}

int CoherentScientificRemote::OnTriggerNum(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
	    std::string laserName;
	    std::stringstream laserCountStr;
		laserName = this->queryLaser(replaceLaserNum("SYST{laserNum}:INF:MOD", (long)triggerNum_).c_str());
		laserCountStr << triggerNum_ << " (" << g_LaserStr_ << ")";

		pProp->Set(laserCountStr.str().c_str());
   }
   else if (eAct == MM::AfterSet)
   {
	  std::string requestedStateString;
      pProp->Get(requestedStateString);

	  if (requestedStateString.compare("None") == 0) {
		  triggerNum_ = 0;
	  } else {
		  triggerNum_ = atoi(requestedStateString.substr(0,1).c_str());
	  }
   }
   
   return HandleErrors();
}

int CoherentScientificRemote::OnModulation(MM::PropertyBase* pProp, MM::ActionType eAct, long laserNum)
{
   if (eAct == MM::BeforeGet)
   {
	   string ans = this->queryLaser(replaceLaserNum(modulationReadbackToken_, laserNum).c_str());
	   std::transform(ans.begin(), ans.end(), ans.begin(), ::tolower);
	   if (ans.find("cwp") == 0) {
		  modulation_ = "CW (constant power)";
	   }
	   else if (ans.find("cwc") == 0) {
		  modulation_ = "CW (constant current)";
	   }
	   else if (ans.find("digital") == 0) {
		  modulation_ = "External/Digital";
	   }
	   else if (ans.find("analog") == 0) {
		  modulation_ = "External/Analog";
	   }
	   else if (ans.find("mixed") == 0) {
		  modulation_ = "External/Mixed";
	   }
	   else{
		  modulation_ = "CW (constant power)";
		  setLaser(replaceLaserNum(modulationSetpointINTToken_, laserNum).c_str(), "CWP");
	   }
		  
	   pProp->Set(modulation_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(modulation_);

	   if (modulation_.compare("CW (constant power)") == 0) {
		  setLaser(replaceLaserNum(modulationSetpointINTToken_, laserNum).c_str(), "CWP");
	   }
	   if (modulation_.compare("CW (constant current)") == 0) {
		  setLaser(replaceLaserNum(modulationSetpointINTToken_, laserNum).c_str(), "CWC");
	   }
	   if (modulation_.compare("External/Digital") == 0) {
		  setLaser(replaceLaserNum(modulationSetpointEXTToken_, laserNum).c_str(), "DIG");
	   }
	   if (modulation_.compare("External/Analog") == 0) {
		  setLaser(replaceLaserNum(modulationSetpointEXTToken_, laserNum).c_str(), "ANAL");
	   }
	   else if (modulation_.compare("External/Mixed") == 0) {
		  setLaser(replaceLaserNum(modulationSetpointEXTToken_, laserNum).c_str(), "MIX");
	   }
   }

	   
   return HandleErrors();
}

int CoherentScientificRemote::OnHeadID(MM::PropertyBase* pProp, MM::ActionType eAct, long laserNum)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set((this->queryLaser(replaceLaserNum(headSerialNoToken_, laserNum).c_str())).c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      // never do anything!!
   }
   return HandleErrors();
}

int CoherentScientificRemote::OnRemoteDescription(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set((this->queryLaser(descriptionToken_)).c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      // never do anything!!
   }
   return HandleErrors();
}

int CoherentScientificRemote::OnHeadUsageHours(MM::PropertyBase* pProp, MM::ActionType eAct, long laserNum)
{
   if (eAct == MM::BeforeGet)
   {
      std::string svalue = this->queryLaser(replaceLaserNum(headUsageHoursToken_, laserNum).c_str());
      double dvalue = atof(svalue.c_str());
      pProp->Set(dvalue);
   }
   else if (eAct == MM::AfterSet)
   {
      // never do anything!!
   }
   return HandleErrors();
}

int CoherentScientificRemote::OnMinimumLaserPower(MM::PropertyBase* pProp, MM::ActionType eAct, long laserNum)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(POWERCONVERSION*atof((this->queryLaser(replaceLaserNum(minPowerToken_, laserNum).c_str())).c_str()));
   }
   else if (eAct == MM::AfterSet)
   {
      // never do anything!!
   }
   return HandleErrors();
}

int CoherentScientificRemote::OnMaximumLaserPower(MM::PropertyBase* pProp, MM::ActionType eAct, long laserNum)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(POWERCONVERSION*atof((this->queryLaser(replaceLaserNum(maxPowerToken_, laserNum).c_str())).c_str()));
   }
   else if (eAct == MM::AfterSet)
   {
      // never do anything!!
   }
   return HandleErrors();
}

int CoherentScientificRemote::OnWaveLength(MM::PropertyBase* pProp, MM::ActionType eAct, long laserNum)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(atof((this->queryLaser(replaceLaserNum(wavelengthToken_, laserNum).c_str())).c_str()));
   }
   else if (eAct == MM::AfterSet)
   {
      // never do anything!!
   }
   return HandleErrors();
}

int CoherentScientificRemote::OnTemperatureDiode(MM::PropertyBase* pProp, MM::ActionType eAct, long laserNum)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(atof((this->queryLaser(replaceLaserNum(temperatureDiodeToken_, laserNum).c_str())).c_str()));
   }
   else if (eAct == MM::AfterSet)
   {
      // never do anything!!
   }
   return HandleErrors();
}

int CoherentScientificRemote::OnTemperatureInternal(MM::PropertyBase* pProp, MM::ActionType eAct, long laserNum)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(atof((this->queryLaser(replaceLaserNum(temperatureInternalToken_, laserNum).c_str())).c_str()));
   }
   else if (eAct == MM::AfterSet)
   {
      // never do anything!!
   }
   return HandleErrors();
}

int CoherentScientificRemote::OnTemperatureBase(MM::PropertyBase* pProp, MM::ActionType eAct, long laserNum)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(atof((this->queryLaser(replaceLaserNum(temperatureBaseToken_, laserNum).c_str())).c_str()));
   }
   else if (eAct == MM::AfterSet)
   {
      // never do anything!!
   }
   return HandleErrors();
}

void CoherentScientificRemote::GetPowerReadback(double& value, long laserNum)
{
   string ans = this->queryLaser(replaceLaserNum(powerReadbackToken_, laserNum).c_str());
   value = POWERCONVERSION*atof(ans.c_str());
}

void CoherentScientificRemote::SetPowerSetpointPercent(double requestedPowerSetpointPercent, double& achievedPowerSetpointPercent, long laserNum)
{
   double llimit = POWERCONVERSION*atof((this->queryLaser(replaceLaserNum(minPowerToken_, laserNum).c_str())).c_str());
   double ulimit = POWERCONVERSION*atof((this->queryLaser(replaceLaserNum(maxPowerToken_, laserNum).c_str())).c_str());
   
   // Calculate percentage
   std::string result;
   std::ostringstream setpointString;
 
   requestedPowerSetpointPercent = requestedPowerSetpointPercent/100*(ulimit-llimit)+llimit;
         
   setpointString << setprecision(6) << requestedPowerSetpointPercent/POWERCONVERSION;
   result = this->setLaser(replaceLaserNum(powerSetpointToken_, laserNum).c_str(), setpointString.str());
   
   //compare quantized setpoint to requested setpoint
   // the difference can be rather large
   achievedPowerSetpointPercent = POWERCONVERSION*atof( result.c_str());

   // if device echos a setpoint more the 10% of full scale from requested setpoint, log a warning message
   if ( ulimit/10. < fabs( achievedPowerSetpointPercent-POWERCONVERSION*requestedPowerSetpointPercent))
   {
      std::ostringstream messs;
      messs << "requested setpoint: " << requestedPowerSetpointPercent << " but echo setpoint is: " << achievedPowerSetpointPercent;
      LogMessage(messs.str().c_str());
   }
   
}

void CoherentScientificRemote::GetPowerSetpointPercent(double& value, long laserNum)
{
   string ans = this->queryLaser(replaceLaserNum(powerSetpointToken_, laserNum).c_str());
   // Calculate the percentage
   double llimit = POWERCONVERSION*atof((this->queryLaser(replaceLaserNum(minPowerToken_, laserNum).c_str())).c_str());
   double ulimit = POWERCONVERSION*atof((this->queryLaser(replaceLaserNum(maxPowerToken_, laserNum).c_str())).c_str());
   value = 100*(POWERCONVERSION*atof(ans.c_str()))/(ulimit-llimit)-llimit;
}

void CoherentScientificRemote::SetState(long state, long laserNum)
{
   std::ostringstream atoken;
   if (state==1){
      atoken << "On";
   }
   else{
      atoken << "Off";
   }
   this->setLaser(replaceLaserNum(laserOnToken_, laserNum).c_str(), atoken.str());
   // Set timer for the Busy signal
   changedTime_ = GetCurrentMMTime();
}

void CoherentScientificRemote::GetState(long &value, long laserNum)
{
   string ans = this->queryLaser(replaceLaserNum(laserOnToken_, laserNum).c_str());
   std::transform(ans.begin(), ans.end(), ans.begin(), ::tolower);
   if (ans.find("on") == 0) {
      value = 1;
   }
   else if (ans.find("off") == 0) {
      value = 0;
   }
   else{
      value = 2;
   }
}

void CoherentScientificRemote::SetExternalLaserPowerControl(int value, long laserNum)
{
   std::ostringstream atoken;
   atoken << value;
   this->setLaser(replaceLaserNum(externalPowerControlToken_, laserNum).c_str(), atoken.str());
}

void CoherentScientificRemote::GetExternalLaserPowerControl(int& value, long laserNum)
{
   string ans = this->queryLaser(replaceLaserNum(externalPowerControlToken_, laserNum).c_str());
   value = atol(ans.c_str());
}

int CoherentScientificRemote::HandleErrors()
{
   int lastError = error_;
   error_ = 0;
   return lastError;
}



/////////////////////////////////////
//  Communications
/////////////////////////////////////


void CoherentScientificRemote::Send(std::string cmd)
{
   std::ostringstream messs;
   messs << "CoherentScientificRemote::Send           " << cmd;
   LogMessage( messs.str().c_str(), true);

   int ret = SendSerialCommand(port_.c_str(), cmd.c_str(), carriage_return);
   if (ret!=DEVICE_OK)
      error_ = DEVICE_SERIAL_COMMAND_FAILED;
}


int CoherentScientificRemote::ReceiveOneLine()
{
   buf_string_ = "";
   int ret = GetSerialAnswer(port_.c_str(), line_feed, buf_string_);
   if (ret != DEVICE_OK)
      return ret;
   std::ostringstream messs;
   messs << "CoherentScientificRemote::ReceiveOneLine " << buf_string_;
   LogMessage( messs.str().c_str(), true);

   return DEVICE_OK;
}

void CoherentScientificRemote::Purge()
{
   int ret = PurgeComPort(port_.c_str());
   if (ret!=0)
      error_ = DEVICE_SERIAL_COMMAND_FAILED;
}

//********************
// Shutter API
//********************

int CoherentScientificRemote::SetOpen(bool open)
{
   SetState((long) open, (int)triggerNum_);
   return HandleErrors();
}

int CoherentScientificRemote::GetOpen(bool& open)
{
	long laserNum;
	laserNum = 1;
   long state;
   GetState(state, (int)triggerNum_);
   if (state==1)
      open = true;
   else if (state==0)
      open = false;
   else
      error_ = DEVICE_UNKNOWN_POSITION;

   return HandleErrors();
}

// ON for deltaT milliseconds
// other implementations of Shutter don't implement this
// is this perhaps because this blocking call is not appropriate
int CoherentScientificRemote::Fire(double deltaT)
{
   SetOpen(true);
   CDeviceUtils::SleepMs((long)(deltaT+.5));
   SetOpen(false);
   return HandleErrors();
}

