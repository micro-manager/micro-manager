/*&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&
// FILE:          Stradus.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Controls Vortran Stradus Diode Laser Modules
// COPYRIGHT:     Vortran Laser Technology, 2012, All rights reserved.
//                http://www.vortranlaser.com
// AUTHOR:        David Sweeney
// LICENSE:       This file is distributed under the LGPL license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&
*/

#include "Stradus.h"
#ifdef WIN32
   #include <windows.h>
   #define snprintf _snprintf
#endif

#include "../../MMDevice/MMDevice.h"
#include <cstdio>
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include "../../MMCore/Error.h"
#include <sstream>

const char* DEVICE_NAME = "VLTStradus";


//Required Micro-Manager API Functions&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&
MODULE_API void InitializeModuleData()
{
   AddAvailableDeviceName(DEVICE_NAME, "VLTStradus");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
    if (deviceName == 0)
	   return 0;

    if (strcmp(deviceName, DEVICE_NAME) == 0)
    {
	   return new Stradus();
    }
    return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

//&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&
// Stradus Object Initialization
Stradus::Stradus() :
   port_("Undefined"),
   initialized_(false),
   busy_(false),
   answerTimeoutMs_(1000),
   power_(1.00),
   pulPwr_(1),
   laserOn_("Undefined"),
   epc_("Undefined"),
   digMod_("Undefined"),
   emissionStatus_("Undefined"),
   interlock_ ("Undefined"),
   fault_("Undefined"),
   faultCode_("Undefined"),
   serialNumber_("Undefined"),
   version_("Undefined")
{
     InitializeDefaultErrorMessages();

     SetErrorText(ERR_PORT_CHANGE, "Cannot update COM port after intialization.");

     // Name
     CreateProperty(MM::g_Keyword_Name, DEVICE_NAME, MM::String, true);

     // Description
     CreateProperty(MM::g_Keyword_Description, "VORTRAN Stradus Laser", MM::String, true);

     // Port
     CPropertyAction* pAct = new CPropertyAction (this, &Stradus::OnPort);
     CreateProperty(MM::g_Keyword_Port, "No Port", MM::String, false, pAct, true);

}
//&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&

Stradus::~Stradus()
{
     Shutdown();
}

void Stradus::GetName(char* Name) const
{
     CDeviceUtils::CopyLimitedString(Name, DEVICE_NAME);
}

int Stradus::Initialize()
{
     int nRet=0;
     std::string answer;
     
     CPropertyAction* pAct = new CPropertyAction (this, &Stradus::OnPulPwr);
     nRet = CreateProperty("DigitalPeakPowerSetting", "0.00", MM::Float, false, pAct);
     if (DEVICE_OK != nRet)
          return nRet;
          
     pAct = new CPropertyAction (this, &Stradus::OnPower);
     nRet = CreateProperty("PowerSetting", "0.00", MM::Float, false, pAct);
     if (DEVICE_OK != nRet)
          return nRet;

     pAct = new CPropertyAction (this, &Stradus::OnLaserOnOff);
     CreateProperty("LaserEmission", "OFF", MM::String, false, pAct);

     std::vector<std::string> commands;
     commands.push_back("OFF");
     commands.push_back("ON");
     SetAllowedValues("LaserEmission", commands);

     pAct = new CPropertyAction (this, &Stradus::OnHours);
     nRet = CreateProperty("Hours", "0.00", MM::String, true, pAct);
     if (DEVICE_OK != nRet)
          return nRet;
          
     pAct = new CPropertyAction (this, &Stradus::OnFaultCode);
     nRet = CreateProperty("FaultCode", "0.00", MM::String, true, pAct);
     if (DEVICE_OK != nRet)
          return nRet;

     pAct = new CPropertyAction (this, &Stradus::OnBaseT);
     nRet = CreateProperty("BaseplateTemp", "0.00", MM::String, true, pAct);
     if (DEVICE_OK != nRet)
          return nRet;

     pAct = new CPropertyAction (this, &Stradus::OnCurrent);
     nRet = CreateProperty("Current", "0.00", MM::String, true, pAct);
     if (DEVICE_OK != nRet)
          return nRet;

     pAct = new CPropertyAction (this, &Stradus::OnInterlock);
     nRet = CreateProperty("Interlock", "Interlock Open", MM::String, true, pAct);
     if (DEVICE_OK != nRet)
          return nRet;

     pAct = new CPropertyAction (this, &Stradus::OnFault);
     nRet = CreateProperty("OperatingCondition", "No Fault", MM::String, true, pAct);
     if (DEVICE_OK != nRet)
          return nRet;

     pAct = new CPropertyAction (this, &Stradus::OnSerialNumber);
     nRet = CreateProperty("LaserID", "0", MM::String, true, pAct);
     if (DEVICE_OK != nRet)
          return nRet;

     pAct = new CPropertyAction (this, &Stradus::OnVersion);
     nRet = CreateProperty("FirmwareVersion", "0", MM::String, true, pAct);
     if (DEVICE_OK != nRet)
          return nRet;

     pAct = new CPropertyAction (this, &Stradus::OnPowerStatus);
     nRet = CreateProperty("Power", "0.00", MM::String, true, pAct);
     if (DEVICE_OK != nRet)
          return nRet;
     
     pAct = new CPropertyAction (this, &Stradus::OnPulPwrStatus);
     nRet = CreateProperty("DigitalPeakPower", "0.00", MM::String, true, pAct);
     if (DEVICE_OK != nRet)
          return nRet;

     pAct = new CPropertyAction (this, &Stradus::OnDigMod);
     nRet = CreateProperty("DigitalModulation", "OFF", MM::String, false, pAct);
     std::vector<std::string> digModVal;
     digModVal.push_back("OFF");
     digModVal.push_back("ON");
     SetAllowedValues("DigitalModulation", digModVal);

     pAct = new CPropertyAction (this, &Stradus::OnEPC);
     nRet = CreateProperty("AnalogModulation", "OFF", MM::String, false, pAct);
     std::vector<std::string> epcVal;
     epcVal.push_back("OFF");
     epcVal.push_back("ON");
     SetAllowedValues("AnalogModulation", epcVal);

     nRet = UpdateStatus();
     if (nRet != DEVICE_OK)
          return nRet;

     initialized_ = true;
     return DEVICE_OK;
}


int Stradus::Shutdown()
{
   if (initialized_)
   {
      LaserOnOff(false);
	  initialized_ = false;
   }
   return DEVICE_OK;
}

bool Stradus::Busy()
{
   return busy_;
}

int Stradus::SetOpen(bool open)
{
   LaserOnOff((long)open);
   return DEVICE_OK;
}

int Stradus::GetOpen(bool& open)
{
   long state;
   std::ostringstream command;
   std::string answer;
   std::vector<std::string> tokens;
   std::string delims="=";

   command << "?le";
   int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
   if (ret != DEVICE_OK) return ret;
   CDeviceUtils::SleepMs(50);
   ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
   ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
   PurgeComPort(port_.c_str());
   if (ret != DEVICE_OK) return ret;

   Stradus::Tokenize(answer, tokens, delims);
   if ( 2 == tokens.size())
   {
   		answer=tokens.at(1).c_str();
   }

   state=atol(answer.c_str());
   if (state==1)
      open = true;
   else if (state==0)
      open = false;

   return DEVICE_OK;
}

int Stradus::Fire(double /*deltaT*/)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}



//Command routines &&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&
int Stradus::epcOnOff(int onoff)
{
     std::string answer;
     std::ostringstream command;

     if (onoff == 0)
     {
          command << "epc=0";
          epc_ = "OFF";
     }
     else if (onoff == 1)
     {
          command << "epc=1";
          epc_ = "ON";
     }

     int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
     if (ret != DEVICE_OK) return ret;
     CDeviceUtils::SleepMs(100);
     ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
     ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
     PurgeComPort(port_.c_str());
     if (ret != DEVICE_OK) return ret;
	 return DEVICE_OK;
}

int Stradus::digModOnOff(int onoff)
{
     std::string answer;
     std::ostringstream command;

     if (onoff == 0)
     {
          command << "pul=0";
          digMod_ = "OFF";
     }
     else if (onoff == 1)
     {
          command << "pul=1";
          digMod_ = "ON";
     }

     int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
     if (ret != DEVICE_OK) return ret;
     CDeviceUtils::SleepMs(100);
     ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
     ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
     PurgeComPort(port_.c_str());
     if (ret != DEVICE_OK) return ret;
	 return DEVICE_OK;
}

int Stradus::LaserOnOff(int onoff)
{
     std::string answer;
     std::ostringstream command;

     if (onoff == 0)
     {
          command << "le=0";
          laserOn_ = "OFF";
     }
     else if (onoff == 1)
     {
          command << "le=1";
          laserOn_ = "ON";
     }
     int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
     if (ret != DEVICE_OK) return ret;
     CDeviceUtils::SleepMs(100);
     ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
     ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
     PurgeComPort(port_.c_str());
     if (ret != DEVICE_OK) return ret;

	 return DEVICE_OK;
}


// Action Handlers &&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&
//Baseplate Temperature
int Stradus::OnBaseT(MM::PropertyBase* pProp, MM::ActionType eAct)
{
     std::ostringstream command,parsed;
     std::vector<std::string> tokens;
     std::string delims="=";
     
     if(eAct==MM::BeforeGet)
     {
        command << "?bpt";
        int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
        if (ret != DEVICE_OK) return ret;
        CDeviceUtils::SleepMs(50);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", baseT_);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", baseT_);
        PurgeComPort(port_.c_str());
        if (ret != DEVICE_OK) return ret;

        Stradus::Tokenize(baseT_, tokens, delims);
        if ( 2 == tokens.size())
        {
		    baseT_ = tokens.at(1);
        }
        pProp->Set(baseT_.c_str());
     }
     else 
        if(eAct==MM::AfterSet)
        {
            //Read Only, Do nothing
        }

     return DEVICE_OK;

}

//Serial COM Port
int Stradus::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
     if (eAct == MM::BeforeGet)
     {
          pProp->Set(port_.c_str());
     }
     else if (eAct == MM::AfterSet)
     {
          if (initialized_)
          {
               pProp->Set(port_.c_str());
               return ERR_PORT_CHANGE;
          }
          pProp->Get(port_);
     }

     return DEVICE_OK;
}
//Digital Modulation On/Off
int Stradus::OnDigMod(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    std::ostringstream command;
    std::string answer;

    if (eAct == MM::BeforeGet)
    {
          command << "?pul";
          int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
          if (ret != DEVICE_OK) return ret;
          CDeviceUtils::SleepMs(50);
          ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
          ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
          PurgeComPort(port_.c_str());
          if (ret != DEVICE_OK) return ret;


	      if (answer == "?PUL=0")
               digMod_ = "OFF";
          else if (answer == "?PUL=1")
               digMod_ = "ON";
          pProp->Set(digMod_.c_str());
     }
     else
     {
        if (eAct == MM::AfterSet)
        {
          pProp->Get(answer);
          if (answer == "OFF")
          {
               digModOnOff(false); //Turn laser off
          }
          else
            if(answer == "ON")
            {
               digModOnOff(true); //Turn laser on
            }
        }
     }
     return DEVICE_OK;
}
//External Power Control
int Stradus::OnEPC(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    std::ostringstream command;
    std::string answer;

    if (eAct == MM::BeforeGet)
     {
          command << "?epc";
          int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
          if (ret != DEVICE_OK) return ret;
          CDeviceUtils::SleepMs(50);
          ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
          ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
          PurgeComPort(port_.c_str());
          if (ret != DEVICE_OK) return ret;

          std::vector<std::string> tokens;
          std::string delims="=";

          Stradus::Tokenize(answer, tokens, delims);

          if ( 2 == tokens.size())
		  {
			   answer=tokens.at(1).c_str();
		  }

	      if (answer == "0")
               epc_ = "OFF";
          else if (answer == "1")
               epc_ = "ON";

          pProp->Set(epc_.c_str());
     }
     else
          if (eAct == MM::AfterSet)
          {
            pProp->Get(answer);
            if (answer == "OFF")
            {
               epcOnOff(false); //Turn EPC off
            }
            else
                if(answer=="ON")
                {
                    epcOnOff(true); //Turn EPC on
                }
          }


     return DEVICE_OK;
}
//Peak Power Status (Digital Modulation Only)
int Stradus::OnPulPwrStatus(MM::PropertyBase* pProp, MM::ActionType eAct)
{
     std::string answer;
     std::ostringstream command;
     std::vector<std::string> tokens;
     std::string delims="=";
     
     if(eAct==MM::BeforeGet)
     {

        command << "?pp";
        int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
        if (ret != DEVICE_OK) return ret;
        CDeviceUtils::SleepMs(50);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
        PurgeComPort(port_.c_str());
        if (ret != DEVICE_OK) return ret;
     

        Stradus::Tokenize(answer, tokens, delims);
        if ( 2 == tokens.size())
        {
		    answer=tokens.at(1).c_str();
        }
        pProp->Set(atol(answer.c_str()));
     }
     else 
        if(eAct==MM::AfterSet)
        {
            //Read Only, Do Nothing
        }

        return DEVICE_OK;

}

//Peak Power Set/Get (Digital Modulation Only)
int Stradus::OnPulPwr(MM::PropertyBase* pProp, MM::ActionType eAct)
{
     std::string answer;
     std::ostringstream command;

     if (eAct == MM::BeforeGet)
     {
          command << "?pp";
          int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
          if (ret != DEVICE_OK) return ret;
          CDeviceUtils::SleepMs(50);
          ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
          ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
          PurgeComPort(port_.c_str());
          if (ret != DEVICE_OK) return ret;

          std::vector<std::string> tokens;
          std::string delims="=";

          Stradus::Tokenize(answer, tokens, delims);

          if ( 2 == tokens.size())
		  {
			   answer=tokens.at(1).c_str();
		  }

          pProp->Set(answer.c_str());
     }
     else if (eAct == MM::AfterSet)
     {
          pProp->Get((long&)pulPwr_);
          command << "pp=" << pulPwr_;
          int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
          if (ret != DEVICE_OK) return ret;
          CDeviceUtils::SleepMs(1000);
          ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
          ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
          PurgeComPort(port_.c_str());
          if (ret != DEVICE_OK) return ret;
     }

   return DEVICE_OK;
}

//Laser Power Set/Get
int Stradus::OnPower(MM::PropertyBase* pProp, MM::ActionType eAct)
{
     std::string answer;
     std::ostringstream command;

     if (eAct == MM::BeforeGet)
     {
          command << "?lps";
          int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
          if (ret != DEVICE_OK) return ret;
          CDeviceUtils::SleepMs(50);
          ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
          ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
          PurgeComPort(port_.c_str());
          if (ret != DEVICE_OK) return ret;

          std::vector<std::string> tokens;
          std::string delims="=";

          Stradus::Tokenize(answer, tokens, delims);

          if ( 2 == tokens.size())
		  {
			   answer=tokens.at(1).c_str();
		  }

          pProp->Set(answer.c_str());
     }
     else if (eAct == MM::AfterSet)
     {
          pProp->Get(power_);
          command << "lp=" << power_;
          int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
          if (ret != DEVICE_OK) return ret;
          CDeviceUtils::SleepMs(500);
          ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
          ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
          PurgeComPort(port_.c_str());
          if (ret != DEVICE_OK) return ret;
     }

   return DEVICE_OK;
}

//Laser Power Get
int Stradus::OnPowerStatus(MM::PropertyBase* pProp, MM::ActionType eAct)
{
     std::string answer;
     std::ostringstream command;
     std::vector<std::string> tokens;
     std::string delims="=";
     
     if(eAct==MM::BeforeGet)
     {
        command << "?lp";
        int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
        if (ret != DEVICE_OK) return ret;
        CDeviceUtils::SleepMs(50);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
        PurgeComPort(port_.c_str());
        if (ret != DEVICE_OK) return ret;

        Stradus::Tokenize(answer, tokens, delims);
        if ( 2 == tokens.size())
        {
		    answer=tokens.at(1).c_str();
        }
        pProp->Set(answer.c_str());
     }
     else
        if(eAct==MM::AfterSet)
        {
            //Read Only, Do Nothing
        }

     return DEVICE_OK;
}

//Laser Diode Operating Time
int Stradus::OnHours(MM::PropertyBase* pProp, MM::ActionType eAct)
{
     std::ostringstream command;
     std::vector<std::string> tokens;
     std::string delims="=";
     
     if(eAct==MM::BeforeGet)
     {

        command << "?lh";
        int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
        if (ret != DEVICE_OK) return ret;
        CDeviceUtils::SleepMs(50);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", hours_);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", hours_);
        PurgeComPort(port_.c_str());
        if (ret != DEVICE_OK) return ret;

        Stradus::Tokenize(hours_, tokens, delims);
        if ( 2 == tokens.size())
        {
		    hours_=tokens.at(1).c_str();
        }
        pProp->Set(hours_.c_str());
     }
     else
         if(eAct==MM::AfterSet)
         {
             //Read Only, Do Nothing
         }

     return DEVICE_OK;
}
int Stradus::OnFaultCode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
     std::ostringstream command;
     std::vector<std::string> tokens;
     std::string delims="=";
     
     if(eAct==MM::BeforeGet)
     {

        command << "?fc";
        int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
        if (ret != DEVICE_OK) return ret;
        CDeviceUtils::SleepMs(50);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", faultCode_);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", faultCode_);
        PurgeComPort(port_.c_str());
        if (ret != DEVICE_OK) return ret;

        Stradus::Tokenize(faultCode_, tokens, delims);
        if ( 2 == tokens.size())
        {
		    faultCode_=tokens.at(1).c_str();
        }
        pProp->Set(faultCode_.c_str());
     
     }
     else
        if(eAct==MM::AfterSet)
        {
            //Read Only, Do Nothing
        }

     return DEVICE_OK;

}
//Laser Serial Number, Part Number, Lambda, Nom. Power, Circular or Elliptical Beam
int Stradus::OnSerialNumber(MM::PropertyBase* pProp, MM::ActionType eAct)
{
     std::ostringstream command,parsed;
     std::vector<std::string> tokens;
     std::string delims="=";
     
     if(eAct==MM::BeforeGet)
     {

        command << "?li";
        int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
        if (ret != DEVICE_OK) return ret;
        CDeviceUtils::SleepMs(50);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", serialNumber_);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", serialNumber_);
        PurgeComPort(port_.c_str());
        if (ret != DEVICE_OK) return ret;

        Stradus::Tokenize(serialNumber_, tokens, delims);
        if ( 2 == tokens.size())
        {
		    serialNumber_ = tokens.at(1);
        }
        pProp->Set(serialNumber_.c_str());
     }
     else
        if(eAct==MM::AfterSet)
        {
            //Read Only, Do Nothing
        }

     return DEVICE_OK;
}
//Firmware Version
int Stradus::OnVersion(MM::PropertyBase* pProp, MM::ActionType eAct)
{
     std::ostringstream command;
     std::vector<std::string> tokens;
     std::string delims="=";
     
     if(eAct==MM::BeforeGet)
     {

        command << "?fv";
        int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
        if (ret != DEVICE_OK) return ret;
        CDeviceUtils::SleepMs(50);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", version_);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", version_);
        PurgeComPort(port_.c_str());
        if (ret != DEVICE_OK) return ret;

        Stradus::Tokenize(version_, tokens, delims);
        if ( 2 == tokens.size())
        {
		    version_=tokens.at(1).c_str();
        }

        pProp->Set(version_.c_str());
     }
     else 
        if(eAct==MM::AfterSet)
        {
            //Read Only, Do Nothing
        }

     return DEVICE_OK;
}

//Laser Diode Current Get
int Stradus::OnCurrent(MM::PropertyBase* pProp, MM::ActionType eAct)
{
     std::ostringstream command;
     std::vector<std::string> tokens;
     std::string delims="=";
     
     if(eAct==MM::BeforeGet)
     {
        command << "?lc";
        int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
        if (ret != DEVICE_OK) return ret;
        CDeviceUtils::SleepMs(50);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", current_);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", current_);
        PurgeComPort(port_.c_str());
        if (ret != DEVICE_OK) return ret;

        Stradus::Tokenize(current_, tokens, delims);

        if ( 2 == tokens.size())
        {
		    current_=tokens.at(1).c_str();
        }

        pProp->Set(current_.c_str());
     }
     else
        if(eAct==MM::AfterSet)
        {
            //Read Only, Do Nothing
        }

     return DEVICE_OK;
}
//Interlock Status
int Stradus::OnInterlock(MM::PropertyBase* pProp, MM::ActionType eAct)
{
     std::ostringstream command;
     std::string answer;
     std::vector<std::string> tokens;
     std::string delims="=";
     
     if(eAct==MM::BeforeGet)
     {
        command << "?il";
        int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
        if (ret != DEVICE_OK) return ret;
        CDeviceUtils::SleepMs(50);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
        PurgeComPort(port_.c_str());
        if (ret != DEVICE_OK) return ret;

        Stradus::Tokenize(answer, tokens, delims);
        if ( 2 == tokens.size())
        {
		    answer=tokens.at(1).c_str();
        }

        if (answer == "1")
          interlock_ = "OK";
        else if (answer == "0")
          interlock_ = "INTERLOCK OPEN!";

        pProp->Set(interlock_.c_str());
     }
     else
        if(eAct==MM::AfterSet)
        {
            //Read Only, Do Nothing
        }
     return DEVICE_OK;
}
//System Faults or Operating Condition
int Stradus::OnFault(MM::PropertyBase* pProp, MM::ActionType eAct)
{
     std::ostringstream command;
     std::string answer;
     std::vector<std::string> tokens;
     std::string delims="=";
     
     if(eAct==MM::BeforeGet)
     {
        command << "?fd";
        int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
        if (ret != DEVICE_OK) return ret;
        CDeviceUtils::SleepMs(50);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
        PurgeComPort(port_.c_str());
        if (ret != DEVICE_OK) return ret;

        Stradus::Tokenize(answer, tokens, delims);
        if ( 2 == tokens.size())
        {
		    answer=tokens.at(1).c_str();
        }
        pProp->Set(answer.c_str());
     }
     else
        if(eAct==MM::AfterSet)
        {
            //Read Only, Do Nothing
        }

     return DEVICE_OK;

}

//Laser Emission Status
int Stradus::OnLaserOnOff(MM::PropertyBase* pProp, MM::ActionType eAct)
{
     std::ostringstream command;
     std::string answer;
     std::vector<std::string> tokens;
     std::string delims="=";

     if (eAct == MM::BeforeGet)
     {
          command << "?le";
          int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
          if (ret != DEVICE_OK) return ret;
          CDeviceUtils::SleepMs(50);
          ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
          ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
          PurgeComPort(port_.c_str());
          if (ret != DEVICE_OK) return ret;

          Stradus::Tokenize(answer, tokens, delims);
          if ( 2 == tokens.size())
          {
		     answer=tokens.at(1).c_str();
          }

	      if (answer == "0")
               laserOn_ = "OFF";
          else if (answer == "1")
               laserOn_ = "ON";

          pProp->Set(laserOn_.c_str());
     }
     else
        if (eAct == MM::AfterSet)
        {
          pProp->Get(answer);
          if (answer == "OFF")
          {
               LaserOnOff(false); //Turn laser off
          }
          else
            if(answer == "ON")
            {
               LaserOnOff(true); //Turn laser on
            }
        }

     return DEVICE_OK;
}

//C++ string parsing utility
void Stradus::Tokenize(const std::string& str, std::vector<std::string>& tokens, const std::string& delimiters)
{
    tokens.clear();
    // Borrowed from http://oopweb.com/CPP/Documents/CPPHOWTO/Volume/C++Programming-HOWTO-7.html
    // Skip delimiters at beginning.
    std::string::size_type lastPos = str.find_first_not_of(delimiters, 0);
    // Find first "non-delimiter".
    std::string::size_type pos     = str.find_first_of(delimiters, lastPos);

    while (std::string::npos != pos || std::string::npos != lastPos)
    {
        // Found a token, add it to the vector.
        tokens.push_back(str.substr(lastPos, pos - lastPos));
        // Skip delimiters.  Note the "not_of"
        lastPos = str.find_first_not_of(delimiters, pos);
        // Find next "non-delimiter"
        pos = str.find_first_of(delimiters, lastPos);
    }
}








