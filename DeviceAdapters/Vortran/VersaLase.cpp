/*&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&
// FILE:          VersaLase.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Controls Vortran Stradus VersaLase
// COPYRIGHT:     Vortran Laser Technology, 2013, All rights reserved.
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

#include "VersaLase.h"
#ifdef WIN32
   #include <windows.h>
   #define snprintf _snprintf
#endif

#include "../../MMDevice/MMDevice.h"
#include <cstdio>
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>

const char* DEVICE_NAME = "VLT_VersaLase";
int lasersPresent[MAX_LASERS];
int shutterState[MAX_LASERS];


//Required Micro-Manager API Functions&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&
MODULE_API void InitializeModuleData()
{
   RegisterDevice(DEVICE_NAME, MM::ShutterDevice, "VLT_VersaLase");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
    if (deviceName == 0)
	   return 0;

    if (strcmp(deviceName, DEVICE_NAME) == 0)
    {
	   return new VersaLase();
    }
    return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

//&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&
// VersaLase Object Initialization
VersaLase::VersaLase() :
   port_("Undefined"),
   initialized_(false),
   busy_(false),
   shutterA_("Undefined"),
   shutterB_("Undefined"),
   shutterC_("Undefined"),
   shutterD_("Undefined"),
   powerA_(1.00),
   powerB_(1.00),
   powerC_(1.00),
   powerD_(1.00),
   pulPwrA_(1),
   pulPwrB_(1),
   pulPwrC_(1),
   pulPwrD_(1),
   laserOnA_("Undefined"),
   laserOnB_("Undefined"),
   laserOnC_("Undefined"),
   laserOnD_("Undefined"),
   epcA_("Undefined"),
   epcB_("Undefined"),
   epcC_("Undefined"),
   epcD_("Undefined"),
   digModA_("Undefined"),
   digModB_("Undefined"),
   digModC_("Undefined"),
   digModD_("Undefined"),
   emissionStatusA_("Undefined"),
   emissionStatusB_("Undefined"),
   emissionStatusC_("Undefined"),
   emissionStatusD_("Undefined"),
   interlock_ ("Undefined"),
   faultA_("Undefined"),
   faultB_("Undefined"),
   faultC_("Undefined"),
   faultD_("Undefined"),
   faultCodeA_("Undefined"),
   faultCodeB_("Undefined"),
   faultCodeC_("Undefined"),
   faultCodeD_("Undefined"),
   serialNumberA_("Undefined"),
   serialNumberB_("Undefined"),
   serialNumberC_("Undefined"),
   serialNumberD_("Undefined"),
   version_("Undefined")
{
     InitializeDefaultErrorMessages();

     SetErrorText(ERR_PORT_CHANGE, "Cannot update COM port after intialization.");

     // Name
     CreateProperty(MM::g_Keyword_Name, DEVICE_NAME, MM::String, true);

     // Description
     CreateProperty(MM::g_Keyword_Description, "VORTRAN Stradus VersaLase", MM::String, true);

     // Port
     CPropertyAction* pAct = new CPropertyAction (this, &VersaLase::OnPort);
     CreateProperty(MM::g_Keyword_Port, "No Port", MM::String, false, pAct, true);

}
//&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&

VersaLase::~VersaLase()
{
     Shutdown();
}

void VersaLase::GetName(char* Name) const
{
     CDeviceUtils::CopyLimitedString(Name, DEVICE_NAME);
}

int VersaLase::Initialize()
{
	int nRet=0;
	std::string answer;
	CPropertyAction* pAct;
	std::ostringstream command;
	std::vector<std::string> gui1Tokens;
	std::string gui1Delims="=*";
	int ret;

    //Disable Shutters
	shutterState[LASER_A]=0;
	shutterState[LASER_B]=0;
	shutterState[LASER_C]=0;
	shutterState[LASER_D]=0;

	command << "?gui1"; //?GUI1=1*1*1*1*FV*PV => 7 tokens
	ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
	if (ret != DEVICE_OK) return ret;
	CDeviceUtils::SleepMs(50);
	ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
	ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
	PurgeComPort(port_.c_str());
	if (ret != DEVICE_OK) return ret;

	VersaLase::Tokenize(answer, gui1Tokens, gui1Delims);
	if ( 7 == gui1Tokens.size())
	{
		lasersPresent[LASER_A]=atoi(gui1Tokens.at(1).c_str());
		lasersPresent[LASER_B]=atoi(gui1Tokens.at(2).c_str());
		lasersPresent[LASER_C]=atoi(gui1Tokens.at(3).c_str());
		lasersPresent[LASER_D]=atoi(gui1Tokens.at(4).c_str());
	}

	if(lasersPresent[LASER_A]==1)
	{
        //LASER A
	    pAct = new CPropertyAction (this, &VersaLase::OnShutterA);
		CreateProperty("LASER_A_Shutter", "OFF", MM::String, false, pAct);
        if (DEVICE_OK != nRet)
			return nRet;
        std::vector<std::string> shutterStateA;
		shutterStateA.push_back("OFF");
		shutterStateA.push_back("ON");
		SetAllowedValues("LASER_A_Shutter", shutterStateA);

		pAct = new CPropertyAction (this, &VersaLase::OnPulPwrA);
		nRet = CreateProperty("LASER_A_DigitalPeakPowerSetting", "0.00", MM::Float, false, pAct);
		if (DEVICE_OK != nRet)
			return nRet;
		pAct = new CPropertyAction (this, &VersaLase::OnPowerA);
		nRet = CreateProperty("LASER_A_PowerSetting", "0.00", MM::Float, false, pAct);
		if (DEVICE_OK != nRet)
			return nRet;
		pAct = new CPropertyAction (this, &VersaLase::OnLaserOnOffA);
		CreateProperty("LASER_A_LaserEmission", "OFF", MM::String, false, pAct);

		std::vector<std::string> commandsA;
		commandsA.push_back("OFF");
		commandsA.push_back("ON");
		SetAllowedValues("LASER_A_LaserEmission", commandsA);

		pAct = new CPropertyAction (this, &VersaLase::OnHoursA);
		nRet = CreateProperty("LASER_A_Hours", "0.00", MM::String, true, pAct);
		if (DEVICE_OK != nRet)
			return nRet;
		pAct = new CPropertyAction (this, &VersaLase::OnFaultCodeA);
		nRet = CreateProperty("LASER_A_FaultCode", "0.00", MM::String, true, pAct);
		if (DEVICE_OK != nRet)
			return nRet;
		pAct = new CPropertyAction (this, &VersaLase::OnCurrentA);
		nRet = CreateProperty("LASER_A_Current", "0.00", MM::String, true, pAct);
		if (DEVICE_OK != nRet)
			return nRet;
		pAct = new CPropertyAction (this, &VersaLase::OnFaultA);
		nRet = CreateProperty("LASER_A_OperatingCondition", "No Fault", MM::String, true, pAct);
		if (DEVICE_OK != nRet)
			return nRet;
		pAct = new CPropertyAction (this, &VersaLase::OnSerialNumberA);
		nRet = CreateProperty("LASER_A_LaserID", "0", MM::String, true, pAct);
		if (DEVICE_OK != nRet)
			return nRet;
		pAct = new CPropertyAction (this, &VersaLase::OnPowerStatusA);
		nRet = CreateProperty("LASER_A_Power", "0.00", MM::String, true, pAct);
		if (DEVICE_OK != nRet)
			return nRet;
		pAct = new CPropertyAction (this, &VersaLase::OnPulPwrStatusA);
		nRet = CreateProperty("LASER_A_DigitalPeakPower", "0.00", MM::String, true, pAct);
		if (DEVICE_OK != nRet)
			return nRet;
		pAct = new CPropertyAction (this, &VersaLase::OnDigModA);
		nRet = CreateProperty("LASER_A_DigitalModulation", "OFF", MM::String, false, pAct);
		std::vector<std::string> digModValA;
		digModValA.push_back("OFF");
		digModValA.push_back("ON");
		SetAllowedValues("LASER_A_DigitalModulation", digModValA);

		pAct = new CPropertyAction (this, &VersaLase::OnEPCA);
		nRet = CreateProperty("LASER_A_AnalogModulation", "OFF", MM::String, false, pAct);
		std::vector<std::string> epcValA;
		epcValA.push_back("OFF");
		epcValA.push_back("ON");
		SetAllowedValues("LASER_A_AnalogModulation", epcValA);
	}

	if(lasersPresent[LASER_B]==1)
	{
		//LASER B
		pAct = new CPropertyAction (this, &VersaLase::OnShutterB);
		CreateProperty("LASER_B_Shutter", "OFF", MM::String, false, pAct);
        if (DEVICE_OK != nRet)
			return nRet;
        std::vector<std::string> shutterStateB;
		shutterStateB.push_back("OFF");
		shutterStateB.push_back("ON");
		SetAllowedValues("LASER_B_Shutter", shutterStateB);

		pAct = new CPropertyAction (this, &VersaLase::OnPulPwrB);
		nRet = CreateProperty("LASER_B_DigitalPeakPowerSetting", "0.00", MM::Float, false, pAct);
		if (DEVICE_OK != nRet)
			return nRet;

		pAct = new CPropertyAction (this, &VersaLase::OnPowerB);
		nRet = CreateProperty("LASER_B_PowerSetting", "0.00", MM::Float, false, pAct);
		if (DEVICE_OK != nRet)
			return nRet;

		pAct = new CPropertyAction (this, &VersaLase::OnLaserOnOffB);
		CreateProperty("LASER_B_LaserEmission", "OFF", MM::String, false, pAct);

		std::vector<std::string> commandsB;
		commandsB.push_back("OFF");
		commandsB.push_back("ON");
		SetAllowedValues("LASER_B_LaserEmission", commandsB);

		pAct = new CPropertyAction (this, &VersaLase::OnHoursB);
		nRet = CreateProperty("LASER_B_Hours", "0.00", MM::String, true, pAct);
		if (DEVICE_OK != nRet)
			return nRet;
		pAct = new CPropertyAction (this, &VersaLase::OnFaultCodeB);
		nRet = CreateProperty("LASER_B_FaultCode", "0.00", MM::String, true, pAct);
		if (DEVICE_OK != nRet)
			return nRet;
		pAct = new CPropertyAction (this, &VersaLase::OnCurrentB);
		nRet = CreateProperty("LASER_B_Current", "0.00", MM::String, true, pAct);
		if (DEVICE_OK != nRet)
			return nRet;
		pAct = new CPropertyAction (this, &VersaLase::OnFaultB);
		nRet = CreateProperty("LASER_B_OperatingCondition", "No Fault", MM::String, true, pAct);
		if (DEVICE_OK != nRet)
			return nRet;
		pAct = new CPropertyAction (this, &VersaLase::OnSerialNumberB);
		nRet = CreateProperty("LASER_B_LaserID", "0", MM::String, true, pAct);
		if (DEVICE_OK != nRet)
			return nRet;
		pAct = new CPropertyAction (this, &VersaLase::OnPowerStatusB);
		nRet = CreateProperty("LASER_B_Power", "0.00", MM::String, true, pAct);
		if (DEVICE_OK != nRet)
			return nRet;

		pAct = new CPropertyAction (this, &VersaLase::OnPulPwrStatusB);
		nRet = CreateProperty("LASER_B_DigitalPeakPower", "0.00", MM::String, true, pAct);
		if (DEVICE_OK != nRet)
			return nRet;

		pAct = new CPropertyAction (this, &VersaLase::OnEPCB);
		nRet = CreateProperty("LASER_B_AnalogModulation", "OFF", MM::String, false, pAct);
		std::vector<std::string> epcValB;
		epcValB.push_back("OFF");
		epcValB.push_back("ON");
		SetAllowedValues("LASER_B_AnalogModulation", epcValB);

		pAct = new CPropertyAction (this, &VersaLase::OnDigModB);
		nRet = CreateProperty("LASER_B_DigitalModulation", "OFF", MM::String, false, pAct);
		std::vector<std::string> digModValB;
		digModValB.push_back("OFF");
		digModValB.push_back("ON");
		SetAllowedValues("LASER_B_DigitalModulation", digModValB);
	}

	if(lasersPresent[LASER_C]==1)
	{
		//Laser C
		pAct = new CPropertyAction (this, &VersaLase::OnShutterC);
		CreateProperty("LASER_C_Shutter", "OFF", MM::String, false, pAct);
        if (DEVICE_OK != nRet)
			return nRet;
        std::vector<std::string> shutterStateC;
		shutterStateC.push_back("OFF");
		shutterStateC.push_back("ON");
		SetAllowedValues("LASER_C_Shutter", shutterStateC);

		pAct = new CPropertyAction (this, &VersaLase::OnPulPwrC);
		nRet = CreateProperty("LASER_C_DigitalPeakPowerSetting", "0.00", MM::Float, false, pAct);
		if (DEVICE_OK != nRet)
			return nRet;

		pAct = new CPropertyAction (this, &VersaLase::OnPowerC);
		nRet = CreateProperty("LASER_C_PowerSetting", "0.00", MM::Float, false, pAct);
		if (DEVICE_OK != nRet)
			return nRet;
		pAct = new CPropertyAction (this, &VersaLase::OnLaserOnOffC);
		CreateProperty("LASER_C_LaserEmission", "OFF", MM::String, false, pAct);
		std::vector<std::string> commandsC;
		commandsC.push_back("OFF");
		commandsC.push_back("ON");
		SetAllowedValues("LASER_C_LaserEmission", commandsC);
		pAct = new CPropertyAction (this, &VersaLase::OnHoursC);
		nRet = CreateProperty("LASER_C_Hours", "0.00", MM::String, true, pAct);
		if (DEVICE_OK != nRet)
			return nRet;
		pAct = new CPropertyAction (this, &VersaLase::OnFaultCodeC);
		nRet = CreateProperty("LASER_C_FaultCode", "0.00", MM::String, true, pAct);
		if (DEVICE_OK != nRet)
			return nRet;
		pAct = new CPropertyAction (this, &VersaLase::OnCurrentC);
		nRet = CreateProperty("LASER_C_Current", "0.00", MM::String, true, pAct);
		if (DEVICE_OK != nRet)
			return nRet;
		pAct = new CPropertyAction (this, &VersaLase::OnFaultC);
		nRet = CreateProperty("LASER_C_OperatingCondition", "No Fault", MM::String, true, pAct);
		if (DEVICE_OK != nRet)
			return nRet;
		pAct = new CPropertyAction (this, &VersaLase::OnSerialNumberC);
		nRet = CreateProperty("LASER_C_LaserID", "0", MM::String, true, pAct);
		if (DEVICE_OK != nRet)
			return nRet;
		pAct = new CPropertyAction (this, &VersaLase::OnPowerStatusC);
		nRet = CreateProperty("LASER_C_Power", "0.00", MM::String, true, pAct);
		if (DEVICE_OK != nRet)
			return nRet;
		pAct = new CPropertyAction (this, &VersaLase::OnPulPwrStatusC);
		nRet = CreateProperty("LASER_C_DigitalPeakPower", "0.00", MM::String, true, pAct);
		if (DEVICE_OK != nRet)
			return nRet;
		pAct = new CPropertyAction (this, &VersaLase::OnDigModC);
		nRet = CreateProperty("LASER_C_DigitalModulation", "OFF", MM::String, false, pAct);
		std::vector<std::string> digModValC;
		digModValC.push_back("OFF");
		digModValC.push_back("ON");
		SetAllowedValues("LASER_C_DigitalModulation", digModValC);

		pAct = new CPropertyAction (this, &VersaLase::OnEPCC);
		nRet = CreateProperty("LASER_C_AnalogModulation", "OFF", MM::String, false, pAct);
		std::vector<std::string> epcValC;
		epcValC.push_back("OFF");
		epcValC.push_back("ON");
		SetAllowedValues("LASER_C_AnalogModulation", epcValC);
	}

	if(lasersPresent[LASER_D]==1)
	{
		//Laser D
		pAct = new CPropertyAction (this, &VersaLase::OnShutterD);
		CreateProperty("LASER_D_Shutter", "OFF", MM::String, false, pAct);
        if (DEVICE_OK != nRet)
			return nRet;
        std::vector<std::string> shutterStateD;
		shutterStateD.push_back("OFF");
		shutterStateD.push_back("ON");
		SetAllowedValues("LASER_D_Shutter", shutterStateD);

		pAct = new CPropertyAction (this, &VersaLase::OnPulPwrD);
		nRet = CreateProperty("LASER_D_DigitalPeakPowerSetting", "0.00", MM::Float, false, pAct);
		if (DEVICE_OK != nRet)
			return nRet;
		pAct = new CPropertyAction (this, &VersaLase::OnPowerD);
		nRet = CreateProperty("LASER_D_PowerSetting", "0.00", MM::Float, false, pAct);
		if (DEVICE_OK != nRet)
			return nRet;
		pAct = new CPropertyAction (this, &VersaLase::OnLaserOnOffD);
		CreateProperty("LASER_D_LaserEmission", "OFF", MM::String, false, pAct);

		std::vector<std::string> commandsD;
		commandsD.push_back("OFF");
		commandsD.push_back("ON");
		SetAllowedValues("LASER_D_LaserEmission", commandsD);

		pAct = new CPropertyAction (this, &VersaLase::OnHoursD);
		nRet = CreateProperty("LASER_D_Hours", "0.00", MM::String, true, pAct);
		if (DEVICE_OK != nRet)
			return nRet;
		pAct = new CPropertyAction (this, &VersaLase::OnFaultCodeD);
		nRet = CreateProperty("LASER_D_FaultCode", "0.00", MM::String, true, pAct);
		if (DEVICE_OK != nRet)
			return nRet;

		pAct = new CPropertyAction (this, &VersaLase::OnCurrentD);
		nRet = CreateProperty("LASER_D_Current", "0.00", MM::String, true, pAct);
		if (DEVICE_OK != nRet)
			return nRet;

		pAct = new CPropertyAction (this, &VersaLase::OnInterlock);
		nRet = CreateProperty("Interlock", "Interlock Open", MM::String, true, pAct);
		if (DEVICE_OK != nRet)
			return nRet;

		pAct = new CPropertyAction (this, &VersaLase::OnFaultD);
		nRet = CreateProperty("LASER_D_OperatingCondition", "No Fault", MM::String, true, pAct);
		if (DEVICE_OK != nRet)
			return nRet;

		pAct = new CPropertyAction (this, &VersaLase::OnSerialNumberD);
		nRet = CreateProperty("LASER_D_LaserID", "0", MM::String, true, pAct);
		if (DEVICE_OK != nRet)
			return nRet;

		pAct = new CPropertyAction (this, &VersaLase::OnPowerStatusD);
		nRet = CreateProperty("LASER_D_Power", "0.00", MM::String, true, pAct);
		if (DEVICE_OK != nRet)
			return nRet;

		pAct = new CPropertyAction (this, &VersaLase::OnPulPwrStatusD);
		nRet = CreateProperty("LASER_D_DigitalPeakPower", "0.00", MM::String, true, pAct);
		if (DEVICE_OK != nRet)
			return nRet;

		pAct = new CPropertyAction (this, &VersaLase::OnDigModD);
		nRet = CreateProperty("LASER_D_DigitalModulation", "OFF", MM::String, false, pAct);
		std::vector<std::string> digModValD;
		digModValD.push_back("OFF");
		digModValD.push_back("ON");
		SetAllowedValues("LASER_D_DigitalModulation", digModValD);

		pAct = new CPropertyAction (this, &VersaLase::OnEPCD);
		nRet = CreateProperty("LASER_D_AnalogModulation", "OFF", MM::String, false, pAct);
		std::vector<std::string> epcValD;
		epcValD.push_back("OFF");
		epcValD.push_back("ON");
		SetAllowedValues("LASER_D_AnalogModulation", epcValD);
	}

	pAct = new CPropertyAction (this, &VersaLase::OnBaseT);
	nRet = CreateProperty("BaseplateTemp", "0.00", MM::String, true, pAct);
	if (DEVICE_OK != nRet)
		return nRet;

	pAct = new CPropertyAction (this, &VersaLase::OnVersion);
	nRet = CreateProperty("FirmwareVersion", "0", MM::String, true, pAct);
	if (DEVICE_OK != nRet)
		return nRet;

	nRet = UpdateStatus(); //Executes the BeforeGet function for each property
	if (nRet != DEVICE_OK)
		return nRet;

	initialized_ = true;
	return DEVICE_OK;
}


int VersaLase::Shutdown()
{
   if (initialized_)
   {
	   if(lasersPresent[LASER_A]==1)
			LaserOnOffA(false);
	   if(lasersPresent[LASER_B]==1)
			LaserOnOffB(false);
	   if(lasersPresent[LASER_C]==1)
			LaserOnOffC(false);
	   if(lasersPresent[LASER_D]==1)
			LaserOnOffD(false);
	  initialized_ = false;
   }
   return DEVICE_OK;
}

bool VersaLase::Busy()
{
   return busy_;
}

int VersaLase::SetOpen(bool open)
{
	if(lasersPresent[LASER_A]==1)
        if(shutterState[LASER_A]==1)
            LaserOnOffA((long)open);
	if(lasersPresent[LASER_B]==1)
        if(shutterState[LASER_B]==1)
            LaserOnOffB((long)open);
	if(lasersPresent[LASER_C]==1)
        if(shutterState[LASER_C]==1)
            LaserOnOffC((long)open);
	if(lasersPresent[LASER_D]==1)
        if(shutterState[LASER_D]==1)
            LaserOnOffD((long)open);

	return DEVICE_OK;
}

int VersaLase::GetOpen(bool& open)
{
     long state;
     std::ostringstream command;
     std::string answer;
     std::vector<std::string> tokens;
     std::string delims="=";
     int ret;

	 if(lasersPresent[LASER_A]==1)
		command << "a.?le";
	 else
		 if(lasersPresent[LASER_B]==1)
			 command << "b.?le";
		 else
			 if(lasersPresent[LASER_C]==1)
				 command << "c.?le";
			 else
				 if(lasersPresent[LASER_D]==1)
					 command << "d.?le";
				 else
					 return DEVICE_OK;

	 ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
	 if (ret != DEVICE_OK) return ret;
	 CDeviceUtils::SleepMs(50);
	 ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
	 ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
	 PurgeComPort(port_.c_str());
	 if (ret != DEVICE_OK) return ret;

	 VersaLase::Tokenize(answer, tokens, delims);
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

int VersaLase::Fire(double /*deltaT*/)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

//Command routines &&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&
int VersaLase::epcOnOffA(int onoff)
{
     std::string answer;
     std::ostringstream command;

     if (onoff == 0)
     {
          command << "a.epc=0";
          epcA_ = "OFF";
     }
     else if (onoff == 1)
     {
          command << "a.epc=1";
          epcA_ = "ON";
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

int VersaLase::epcOnOffB(int onoff)
{
     std::string answer;
     std::ostringstream command;

     if (onoff == 0)
     {
          command << "b.epc=0";
          epcB_ = "OFF";
     }
     else if (onoff == 1)
     {
          command << "b.epc=1";
          epcB_ = "ON";
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

int VersaLase::epcOnOffC(int onoff)
{
     std::string answer;
     std::ostringstream command;

     if (onoff == 0)
     {
          command << "c.epc=0";
          epcC_ = "OFF";
     }
     else if (onoff == 1)
     {
          command << "c.epc=1";
          epcC_ = "ON";
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

int VersaLase::epcOnOffD(int onoff)
{
     std::string answer;
     std::ostringstream command;

     if (onoff == 0)
     {
          command << "d.epc=0";
          epcD_ = "OFF";
     }
     else if (onoff == 1)
     {
          command << "d.epc=1";
          epcD_ = "ON";
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

int VersaLase::digModOnOffA(int onoff)
{
     std::string answer;
     std::ostringstream command;

     if (onoff == 0)
     {
          command << "a.pul=0";
          digModA_ = "OFF";
     }
     else if (onoff == 1)
     {
          command << "a.pul=1";
          digModA_ = "ON";
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

int VersaLase::digModOnOffB(int onoff)
{
     std::string answer;
     std::ostringstream command;

     if (onoff == 0)
     {
          command << "b.pul=0";
          digModB_ = "OFF";
     }
     else if (onoff == 1)
     {
          command << "b.pul=1";
          digModB_ = "ON";
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

int VersaLase::digModOnOffC(int onoff)
{
     std::string answer;
     std::ostringstream command;

     if (onoff == 0)
     {
          command << "c.pul=0";
          digModC_ = "OFF";
     }
     else if (onoff == 1)
     {
          command << "c.pul=1";
          digModC_ = "ON";
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

int VersaLase::digModOnOffD(int onoff)
{
     std::string answer;
     std::ostringstream command;

     if (onoff == 0)
     {
          command << "d.pul=0";
          digModD_ = "OFF";
     }
     else if (onoff == 1)
     {
          command << "d.pul=1";
          digModD_ = "ON";
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

int VersaLase::LaserOnOffA(int onoff)
{
     std::string answer;
     std::ostringstream command;

     if (onoff == 0)
     {
          command << "a.le=0";
          laserOnA_ = "OFF";
     }
     else if (onoff == 1)
     {
          command << "a.le=1";
          laserOnA_ = "ON";
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

int VersaLase::LaserOnOffB(int onoff)
{
     std::string answer;
     std::ostringstream command;

     if (onoff == 0)
     {
          command << "b.le=0";
          laserOnB_ = "OFF";
     }
     else if (onoff == 1)
     {
          command << "b.le=1";
          laserOnB_ = "ON";
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

int VersaLase::LaserOnOffC(int onoff)
{
     std::string answer;
     std::ostringstream command;

     if (onoff == 0)
     {
          command << "c.le=0";
          laserOnC_ = "OFF";
     }
     else if (onoff == 1)
     {
          command << "c.le=1";
          laserOnC_ = "ON";
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

int VersaLase::LaserOnOffD(int onoff)
{
     std::string answer;
     std::ostringstream command;

     if (onoff == 0)
     {
          command << "d.le=0";
          laserOnD_ = "OFF";
     }
     else if (onoff == 1)
     {
          command << "d.le=1";
          laserOnD_ = "ON";
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
int VersaLase::OnBaseT(MM::PropertyBase* pProp, MM::ActionType eAct)
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

        VersaLase::Tokenize(baseT_, tokens, delims);
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
int VersaLase::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int VersaLase::OnShutterA(MM::PropertyBase* pProp, MM::ActionType eAct)
{
     std::string answer;

     if (eAct == MM::BeforeGet)
     {
	      if (shutterState[LASER_A] == 0)
               shutterA_ = "OFF";
          else if (shutterState[LASER_A] == 1)
               shutterA_ = "ON";

          pProp->Set(shutterA_.c_str());
     }
     else
        if (eAct == MM::AfterSet)
        {
          pProp->Get(answer);
          if (answer == "OFF")
          {
               shutterState[LASER_A]=0;
          }
          else
            if(answer == "ON")
            {
               shutterState[LASER_A]=1;
            }
        }
     return DEVICE_OK;
}

int VersaLase::OnShutterB(MM::PropertyBase* pProp, MM::ActionType eAct)
{
     std::string answer;

     if (eAct == MM::BeforeGet)
     {
	      if (shutterState[LASER_B] == 0)
               shutterB_ = "OFF";
          else if (shutterState[LASER_B] == 1)
               shutterB_ = "ON";

          pProp->Set(shutterB_.c_str());
     }
     else
        if (eAct == MM::AfterSet)
        {
          pProp->Get(answer);
          if (answer == "OFF")
          {
               shutterState[LASER_B]=0;
          }
          else
            if(answer == "ON")
            {
               shutterState[LASER_B]=1;
            }
        }
     return DEVICE_OK;
}

int VersaLase::OnShutterC(MM::PropertyBase* pProp, MM::ActionType eAct)
{
     std::string answer;

     if (eAct == MM::BeforeGet)
     {
	      if (shutterState[LASER_C] == 0)
               shutterC_ = "OFF";
          else if (shutterState[LASER_C] == 1)
               shutterC_ = "ON";

          pProp->Set(shutterC_.c_str());
     }
     else
        if (eAct == MM::AfterSet)
        {
          pProp->Get(answer);
          if (answer == "OFF")
          {
               shutterState[LASER_C]=0;
          }
          else
            if(answer == "ON")
            {
               shutterState[LASER_C]=1;
            }
        }
     return DEVICE_OK;
}

int VersaLase::OnShutterD(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    std::string answer;

     if (eAct == MM::BeforeGet)
     {
	      if (shutterState[LASER_D] == 0)
               shutterD_ = "OFF";
          else if (shutterState[LASER_D] == 1)
               shutterD_ = "ON";

          pProp->Set(shutterD_.c_str());
     }
     else
        if (eAct == MM::AfterSet)
        {
          pProp->Get(answer);
          if (answer == "OFF")
          {
               shutterState[LASER_D]=0;
          }
          else
            if(answer == "ON")
            {
               shutterState[LASER_D]=1;
            }
        }
     return DEVICE_OK;
}
//Digital Modulation On/Off
int VersaLase::OnDigModA(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    std::ostringstream command;
    std::string answer;
    std::vector<std::string> tokens;
    std::string delims="=";
	int answerNum;

    if (eAct == MM::BeforeGet)
    {
          command << "a.?pul";
          int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
          if (ret != DEVICE_OK) return ret;
          CDeviceUtils::SleepMs(50);
          ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
          ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
          PurgeComPort(port_.c_str());
          if (ret != DEVICE_OK) return ret;

		  VersaLase::Tokenize(answer, tokens, delims);

		  if ( 2 == tokens.size())
		  {
		  	   answer=tokens.at(1).c_str();
		  }

		  answerNum=atol(answer.c_str());

	      if (answerNum == 0)
               digModA_ = "OFF";
          else if (answerNum == 1)
               digModA_ = "ON";

          pProp->Set(digModA_.c_str());
     }
     else
     {
        if (eAct == MM::AfterSet)
        {
          pProp->Get(answer);
          if (answer == "OFF")
          {
               digModOnOffA(false); //Turn laser off
          }
          else
            if(answer == "ON")
            {
               digModOnOffA(true); //Turn laser on
            }
        }
     }
     return DEVICE_OK;
}

int VersaLase::OnDigModB(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    std::ostringstream command;
    std::string answer;
    std::vector<std::string> tokens;
    std::string delims="=";
	int answerNum;

    if (eAct == MM::BeforeGet)
    {
          command << "b.?pul";
          int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
          if (ret != DEVICE_OK) return ret;
          CDeviceUtils::SleepMs(50);
          ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
          ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
          PurgeComPort(port_.c_str());
          if (ret != DEVICE_OK) return ret;

		  VersaLase::Tokenize(answer, tokens, delims);

		  if ( 2 == tokens.size())
		  {
		  	   answer=tokens.at(1).c_str();
		  }

		  answerNum=atol(answer.c_str());

	      if (answerNum == 0)
               digModB_ = "OFF";
          else if (answerNum == 1)
               digModB_ = "ON";

          pProp->Set(digModB_.c_str());
     }
     else
     {
        if (eAct == MM::AfterSet)
        {
          pProp->Get(answer);
          if (answer == "OFF")
          {
               digModOnOffB(false); //Turn laser off
          }
          else
            if(answer == "ON")
            {
               digModOnOffB(true); //Turn laser on
            }
        }
     }
     return DEVICE_OK;
}

int VersaLase::OnDigModC(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    std::ostringstream command;
    std::string answer;
    std::vector<std::string> tokens;
    std::string delims="=";
	int answerNum;

    if (eAct == MM::BeforeGet)
    {
          command << "c.?pul";
          int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
          if (ret != DEVICE_OK) return ret;
          CDeviceUtils::SleepMs(50);
          ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
          ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
          PurgeComPort(port_.c_str());
          if (ret != DEVICE_OK) return ret;

		  VersaLase::Tokenize(answer, tokens, delims);

		  if ( 2 == tokens.size())
		  {
		  	   answer=tokens.at(1).c_str();
		  }

		  answerNum=atol(answer.c_str());

	      if (answerNum == 0)
               digModC_ = "OFF";
          else if (answerNum == 1)
               digModC_ = "ON";

          pProp->Set(digModC_.c_str());
     }
     else
     {
        if (eAct == MM::AfterSet)
        {
          pProp->Get(answer);
          if (answer == "OFF")
          {
               digModOnOffC(false); //Turn laser off
          }
          else
            if(answer == "ON")
            {
               digModOnOffC(true); //Turn laser on
            }
        }
     }
     return DEVICE_OK;
}

int VersaLase::OnDigModD(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    std::ostringstream command;
    std::string answer;
    std::vector<std::string> tokens;
    std::string delims="=";
	int answerNum;

    if (eAct == MM::BeforeGet)
    {
          command << "d.?pul";
          int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
          if (ret != DEVICE_OK) return ret;
          CDeviceUtils::SleepMs(50);
          ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
          ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
          PurgeComPort(port_.c_str());
          if (ret != DEVICE_OK) return ret;

		  VersaLase::Tokenize(answer, tokens, delims);

		  if ( 2 == tokens.size())
		  {
		  	   answer=tokens.at(1).c_str();
		  }

		  answerNum=atol(answer.c_str());

	      if (answerNum == 0)
               digModD_ = "OFF";
          else if (answerNum == 1)
               digModD_ = "ON";

          pProp->Set(digModD_.c_str());
     }
     else
     {
        if (eAct == MM::AfterSet)
        {
          pProp->Get(answer);
          if (answer == "OFF")
          {
               digModOnOffD(false); //Turn laser off
          }
          else
            if(answer == "ON")
            {
               digModOnOffD(true); //Turn laser on
            }
        }
     }
     return DEVICE_OK;
}
//External Power Control
int VersaLase::OnEPCA(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    std::ostringstream command;
    std::string answer;
	std::string outString;
	int answerNum;

    if (eAct == MM::BeforeGet)
     {
          command << "a.?epc";
          int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
          if (ret != DEVICE_OK) return ret;
          CDeviceUtils::SleepMs(50);
          ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
          ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
          PurgeComPort(port_.c_str());
          if (ret != DEVICE_OK) return ret;

          std::vector<std::string> tokens;
          std::string delims="=";

          VersaLase::Tokenize(answer, tokens, delims);

          if ( 2 == tokens.size())
		  {
			   answer=tokens.at(1).c_str();
		  }

		  answerNum=atol(answer.c_str());

	      if (answerNum == 0)
               epcA_ = "OFF";
          else if (answerNum == 1)
               epcA_ = "ON";

          pProp->Set(epcA_.c_str());
     }
     else
          if (eAct == MM::AfterSet)
          {
            pProp->Get(answer);
            if (answer == "OFF")
            {
               epcOnOffA(false); //Turn EPC off
            }
            else
                if(answer=="ON")
                {
                    epcOnOffA(true); //Turn EPC on
                }
          }


     return DEVICE_OK;
}

int VersaLase::OnEPCB(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    std::ostringstream command;
    std::string answer;
	int answerNum;

    if (eAct == MM::BeforeGet)
     {
          command << "b.?epc";
          int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
          if (ret != DEVICE_OK) return ret;
          CDeviceUtils::SleepMs(50);
          ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
          ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
          PurgeComPort(port_.c_str());
          if (ret != DEVICE_OK) return ret;

          std::vector<std::string> tokens;
          std::string delims="=";

          VersaLase::Tokenize(answer, tokens, delims);

          if ( 2 == tokens.size())
		  {
			   answer=tokens.at(1).c_str();
		  }

	      answerNum=atol(answer.c_str());

	      if (answerNum == 0)
               epcB_ = "OFF";
          else if (answerNum == 1)
               epcB_ = "ON";

          pProp->Set(epcB_.c_str());
     }
     else
          if (eAct == MM::AfterSet)
          {
            pProp->Get(answer);
            if (answer == "OFF")
            {
               epcOnOffB(false); //Turn EPC off
            }
            else
                if(answer=="ON")
                {
                    epcOnOffB(true); //Turn EPC on
                }
          }


     return DEVICE_OK;
}

int VersaLase::OnEPCC(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    std::ostringstream command;
    std::string answer;
	int answerNum;

    if (eAct == MM::BeforeGet)
     {
          command << "c.?epc";
          int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
          if (ret != DEVICE_OK) return ret;
          CDeviceUtils::SleepMs(50);
          ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
          ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
          PurgeComPort(port_.c_str());
          if (ret != DEVICE_OK) return ret;

          std::vector<std::string> tokens;
          std::string delims="=";

          VersaLase::Tokenize(answer, tokens, delims);

          if ( 2 == tokens.size())
		  {
			   answer=tokens.at(1).c_str();
		  }

	      answerNum=atol(answer.c_str());

	      if (answerNum == 0)
               epcC_ = "OFF";
          else if (answerNum == 1)
               epcC_ = "ON";

          pProp->Set(epcC_.c_str());
     }
     else
          if (eAct == MM::AfterSet)
          {
            pProp->Get(answer);
            if (answer == "OFF")
            {
               epcOnOffC(false); //Turn EPC off
            }
            else
                if(answer=="ON")
                {
                    epcOnOffC(true); //Turn EPC on
                }
          }


     return DEVICE_OK;
}

int VersaLase::OnEPCD(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    std::ostringstream command;
    std::string answer;
	int answerNum;

    if (eAct == MM::BeforeGet)
     {
          command << "d.?epc";
          int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
          if (ret != DEVICE_OK) return ret;
          CDeviceUtils::SleepMs(50);
          ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
          ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
          PurgeComPort(port_.c_str());
          if (ret != DEVICE_OK) return ret;

          std::vector<std::string> tokens;
          std::string delims="=";

          VersaLase::Tokenize(answer, tokens, delims);

          if ( 2 == tokens.size())
		  {
			   answer=tokens.at(1).c_str();
		  }

	      answerNum=atol(answer.c_str());

	      if (answerNum == 0)
               epcD_ = "OFF";
          else if (answerNum == 1)
               epcD_ = "ON";

          pProp->Set(epcD_.c_str());
     }
     else
          if (eAct == MM::AfterSet)
          {
            pProp->Get(answer);
            if (answer == "OFF")
            {
               epcOnOffD(false); //Turn EPC off
            }
            else
                if(answer=="ON")
                {
                    epcOnOffD(true); //Turn EPC on
                }
          }


     return DEVICE_OK;
}
//Peak Power Status (Digital Modulation Only)
int VersaLase::OnPulPwrStatusA(MM::PropertyBase* pProp, MM::ActionType eAct)
{
     std::string answer;
     std::ostringstream command;
     std::vector<std::string> tokens;
     std::string delims="=";

     if(eAct==MM::BeforeGet)
     {

        command << "a.?pp";
        int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
        if (ret != DEVICE_OK) return ret;
        CDeviceUtils::SleepMs(50);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
        PurgeComPort(port_.c_str());
        if (ret != DEVICE_OK) return ret;


        VersaLase::Tokenize(answer, tokens, delims);
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

//Peak Power Status (Digital Modulation Only)
int VersaLase::OnPulPwrStatusB(MM::PropertyBase* pProp, MM::ActionType eAct)
{
     std::string answer;
     std::ostringstream command;
     std::vector<std::string> tokens;
     std::string delims="=";

     if(eAct==MM::BeforeGet)
     {

        command << "b.?pp";
        int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
        if (ret != DEVICE_OK) return ret;
        CDeviceUtils::SleepMs(50);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
        PurgeComPort(port_.c_str());
        if (ret != DEVICE_OK) return ret;


        VersaLase::Tokenize(answer, tokens, delims);
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

//Peak Power Status (Digital Modulation Only)
int VersaLase::OnPulPwrStatusC(MM::PropertyBase* pProp, MM::ActionType eAct)
{
     std::string answer;
     std::ostringstream command;
     std::vector<std::string> tokens;
     std::string delims="=";

     if(eAct==MM::BeforeGet)
     {

        command << "c.?pp";
        int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
        if (ret != DEVICE_OK) return ret;
        CDeviceUtils::SleepMs(50);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
        PurgeComPort(port_.c_str());
        if (ret != DEVICE_OK) return ret;


        VersaLase::Tokenize(answer, tokens, delims);
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

//Peak Power Status (Digital Modulation Only)
int VersaLase::OnPulPwrStatusD(MM::PropertyBase* pProp, MM::ActionType eAct)
{
     std::string answer;
     std::ostringstream command;
     std::vector<std::string> tokens;
     std::string delims="=";

     if(eAct==MM::BeforeGet)
     {

        command << "d.?pp";
        int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
        if (ret != DEVICE_OK) return ret;
        CDeviceUtils::SleepMs(50);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
        PurgeComPort(port_.c_str());
        if (ret != DEVICE_OK) return ret;


        VersaLase::Tokenize(answer, tokens, delims);
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
int VersaLase::OnPulPwrA(MM::PropertyBase* pProp, MM::ActionType eAct)
{
     std::string answer;
     std::ostringstream command;

     if (eAct == MM::BeforeGet)
     {
          command << "a.?pp";
          int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
          if (ret != DEVICE_OK) return ret;
          CDeviceUtils::SleepMs(50);
          ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
          ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
          PurgeComPort(port_.c_str());
          if (ret != DEVICE_OK) return ret;

          std::vector<std::string> tokens;
          std::string delims="=";

          VersaLase::Tokenize(answer, tokens, delims);

          if ( 2 == tokens.size())
		  {
			   answer=tokens.at(1).c_str();
		  }

          pProp->Set(answer.c_str());
     }
     else if (eAct == MM::AfterSet)
     {
          pProp->Get((long&)pulPwrA_);
          command << "a.pp=" << pulPwrA_;
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

//Peak Power Set/Get (Digital Modulation Only)
int VersaLase::OnPulPwrB(MM::PropertyBase* pProp, MM::ActionType eAct)
{
     std::string answer;
     std::ostringstream command;

     if (eAct == MM::BeforeGet)
     {
          command << "b.?pp";
          int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
          if (ret != DEVICE_OK) return ret;
          CDeviceUtils::SleepMs(50);
          ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
          ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
          PurgeComPort(port_.c_str());
          if (ret != DEVICE_OK) return ret;

          std::vector<std::string> tokens;
          std::string delims="=";

          VersaLase::Tokenize(answer, tokens, delims);

          if ( 2 == tokens.size())
		  {
			   answer=tokens.at(1).c_str();
		  }

          pProp->Set(answer.c_str());
     }
     else if (eAct == MM::AfterSet)
     {
          pProp->Get((long&)pulPwrB_);
          command << "b.pp=" << pulPwrB_;
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

//Peak Power Set/Get (Digital Modulation Only)
int VersaLase::OnPulPwrC(MM::PropertyBase* pProp, MM::ActionType eAct)
{
     std::string answer;
     std::ostringstream command;

     if (eAct == MM::BeforeGet)
     {
          command << "c.?pp";
          int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
          if (ret != DEVICE_OK) return ret;
          CDeviceUtils::SleepMs(50);
          ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
          ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
          PurgeComPort(port_.c_str());
          if (ret != DEVICE_OK) return ret;

          std::vector<std::string> tokens;
          std::string delims="=";

          VersaLase::Tokenize(answer, tokens, delims);

          if ( 2 == tokens.size())
		  {
			   answer=tokens.at(1).c_str();
		  }

          pProp->Set(answer.c_str());
     }
     else if (eAct == MM::AfterSet)
     {
          pProp->Get((long&)pulPwrC_);
          command << "c.pp=" << pulPwrC_;
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

//Peak Power Set/Get (Digital Modulation Only)
int VersaLase::OnPulPwrD(MM::PropertyBase* pProp, MM::ActionType eAct)
{
     std::string answer;
     std::ostringstream command;

     if (eAct == MM::BeforeGet)
     {
          command << "d.?pp";
          int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
          if (ret != DEVICE_OK) return ret;
          CDeviceUtils::SleepMs(50);
          ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
          ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
          PurgeComPort(port_.c_str());
          if (ret != DEVICE_OK) return ret;

          std::vector<std::string> tokens;
          std::string delims="=";

          VersaLase::Tokenize(answer, tokens, delims);

          if ( 2 == tokens.size())
		  {
			   answer=tokens.at(1).c_str();
		  }

          pProp->Set(answer.c_str());
     }
     else if (eAct == MM::AfterSet)
     {
          pProp->Get((long&)pulPwrD_);
          command << "d.pp=" << pulPwrD_;
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
int VersaLase::OnPowerA(MM::PropertyBase* pProp, MM::ActionType eAct)
{
     std::string answer;
     std::ostringstream command;

     if (eAct == MM::BeforeGet)
     {
          command << "a.?lps";
          int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
          if (ret != DEVICE_OK) return ret;
          CDeviceUtils::SleepMs(50);
          ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
          ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
          PurgeComPort(port_.c_str());
          if (ret != DEVICE_OK) return ret;

          std::vector<std::string> tokens;
          std::string delims="=";

          VersaLase::Tokenize(answer, tokens, delims);

          if ( 2 == tokens.size())
		  {
			   answer=tokens.at(1).c_str();
		  }

          pProp->Set(answer.c_str());
     }
     else if (eAct == MM::AfterSet)
     {
          pProp->Get(powerA_);
          command << "a.lp=" << powerA_;
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

//Laser Power Set/Get
int VersaLase::OnPowerB(MM::PropertyBase* pProp, MM::ActionType eAct)
{
     std::string answer;
     std::ostringstream command;

     if (eAct == MM::BeforeGet)
     {
          command << "b.?lps";
          int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
          if (ret != DEVICE_OK) return ret;
          CDeviceUtils::SleepMs(50);
          ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
          ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
          PurgeComPort(port_.c_str());
          if (ret != DEVICE_OK) return ret;

          std::vector<std::string> tokens;
          std::string delims="=";

          VersaLase::Tokenize(answer, tokens, delims);

          if ( 2 == tokens.size())
		  {
			   answer=tokens.at(1).c_str();
		  }

          pProp->Set(answer.c_str());
     }
     else if (eAct == MM::AfterSet)
     {
          pProp->Get(powerB_);
          command << "b.lp=" << powerB_;
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

//Laser Power Set/Get
int VersaLase::OnPowerC(MM::PropertyBase* pProp, MM::ActionType eAct)
{
     std::string answer;
     std::ostringstream command;

     if (eAct == MM::BeforeGet)
     {
          command << "c.?lps";
          int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
          if (ret != DEVICE_OK) return ret;
          CDeviceUtils::SleepMs(50);
          ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
          ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
          PurgeComPort(port_.c_str());
          if (ret != DEVICE_OK) return ret;

          std::vector<std::string> tokens;
          std::string delims="=";

          VersaLase::Tokenize(answer, tokens, delims);

          if ( 2 == tokens.size())
		  {
			   answer=tokens.at(1).c_str();
		  }

          pProp->Set(answer.c_str());
     }
     else if (eAct == MM::AfterSet)
     {
          pProp->Get(powerC_);
          command << "c.lp=" << powerC_;
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

//Laser Power Set/Get
int VersaLase::OnPowerD(MM::PropertyBase* pProp, MM::ActionType eAct)
{
     std::string answer;
     std::ostringstream command;

     if (eAct == MM::BeforeGet)
     {
          command << "d.?lps";
          int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
          if (ret != DEVICE_OK) return ret;
          CDeviceUtils::SleepMs(50);
          ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
          ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
          PurgeComPort(port_.c_str());
          if (ret != DEVICE_OK) return ret;

          std::vector<std::string> tokens;
          std::string delims="=";

          VersaLase::Tokenize(answer, tokens, delims);

          if ( 2 == tokens.size())
		  {
			   answer=tokens.at(1).c_str();
		  }

          pProp->Set(answer.c_str());
     }
     else if (eAct == MM::AfterSet)
     {
          pProp->Get(powerD_);
          command << "d.lp=" << powerD_;
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
int VersaLase::OnPowerStatusA(MM::PropertyBase* pProp, MM::ActionType eAct)
{
     std::string answer;
     std::ostringstream command;
     std::vector<std::string> tokens;
     std::string delims="=";

     if(eAct==MM::BeforeGet)
     {
        command << "a.?lp";
        int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
        if (ret != DEVICE_OK) return ret;
        CDeviceUtils::SleepMs(50);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
        PurgeComPort(port_.c_str());
        if (ret != DEVICE_OK) return ret;

        VersaLase::Tokenize(answer, tokens, delims);
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
//Laser Power Get
int VersaLase::OnPowerStatusB(MM::PropertyBase* pProp, MM::ActionType eAct)
{
     std::string answer;
     std::ostringstream command;
     std::vector<std::string> tokens;
     std::string delims="=";

     if(eAct==MM::BeforeGet)
     {
        command << "b.?lp";
        int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
        if (ret != DEVICE_OK) return ret;
        CDeviceUtils::SleepMs(50);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
        PurgeComPort(port_.c_str());
        if (ret != DEVICE_OK) return ret;

        VersaLase::Tokenize(answer, tokens, delims);
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

//Laser Power Get
int VersaLase::OnPowerStatusC(MM::PropertyBase* pProp, MM::ActionType eAct)
{
     std::string answer;
     std::ostringstream command;
     std::vector<std::string> tokens;
     std::string delims="=";

     if(eAct==MM::BeforeGet)
     {
        command << "c.?lp";
        int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
        if (ret != DEVICE_OK) return ret;
        CDeviceUtils::SleepMs(50);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
        PurgeComPort(port_.c_str());
        if (ret != DEVICE_OK) return ret;

        VersaLase::Tokenize(answer, tokens, delims);
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

//Laser Power Get
int VersaLase::OnPowerStatusD(MM::PropertyBase* pProp, MM::ActionType eAct)
{
     std::string answer;
     std::ostringstream command;
     std::vector<std::string> tokens;
     std::string delims="=";

     if(eAct==MM::BeforeGet)
     {
        command << "d.?lp";
        int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
        if (ret != DEVICE_OK) return ret;
        CDeviceUtils::SleepMs(50);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
        PurgeComPort(port_.c_str());
        if (ret != DEVICE_OK) return ret;

        VersaLase::Tokenize(answer, tokens, delims);
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
int VersaLase::OnHoursA(MM::PropertyBase* pProp, MM::ActionType eAct)
{
     std::ostringstream command;
     std::vector<std::string> tokens;
     std::string delims="=";

     if(eAct==MM::BeforeGet)
     {

        command << "a.?lh";
        int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
        if (ret != DEVICE_OK) return ret;
        CDeviceUtils::SleepMs(50);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", hoursA_);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", hoursA_);
        PurgeComPort(port_.c_str());
        if (ret != DEVICE_OK) return ret;

        VersaLase::Tokenize(hoursA_, tokens, delims);
        if ( 2 == tokens.size())
        {
		    hoursA_=tokens.at(1).c_str();
        }
        pProp->Set(hoursA_.c_str());
     }
     else
         if(eAct==MM::AfterSet)
         {
             //Read Only, Do Nothing
         }

     return DEVICE_OK;
}

//Laser Diode Operating Time
int VersaLase::OnHoursB(MM::PropertyBase* pProp, MM::ActionType eAct)
{
     std::ostringstream command;
     std::vector<std::string> tokens;
     std::string delims="=";

     if(eAct==MM::BeforeGet)
     {

        command << "b.?lh";
        int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
        if (ret != DEVICE_OK) return ret;
        CDeviceUtils::SleepMs(50);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", hoursB_);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", hoursB_);
        PurgeComPort(port_.c_str());
        if (ret != DEVICE_OK) return ret;

        VersaLase::Tokenize(hoursB_, tokens, delims);
        if ( 2 == tokens.size())
        {
		    hoursB_=tokens.at(1).c_str();
        }
        pProp->Set(hoursB_.c_str());
     }
     else
         if(eAct==MM::AfterSet)
         {
             //Read Only, Do Nothing
         }

     return DEVICE_OK;
}

//Laser Diode Operating Time
int VersaLase::OnHoursC(MM::PropertyBase* pProp, MM::ActionType eAct)
{
     std::ostringstream command;
     std::vector<std::string> tokens;
     std::string delims="=";

     if(eAct==MM::BeforeGet)
     {

        command << "c.?lh";
        int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
        if (ret != DEVICE_OK) return ret;
        CDeviceUtils::SleepMs(50);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", hoursC_);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", hoursC_);
        PurgeComPort(port_.c_str());
        if (ret != DEVICE_OK) return ret;

        VersaLase::Tokenize(hoursC_, tokens, delims);
        if ( 2 == tokens.size())
        {
		    hoursC_=tokens.at(1).c_str();
        }
        pProp->Set(hoursC_.c_str());
     }
     else
         if(eAct==MM::AfterSet)
         {
             //Read Only, Do Nothing
         }

     return DEVICE_OK;
}

//Laser Diode Operating Time
int VersaLase::OnHoursD(MM::PropertyBase* pProp, MM::ActionType eAct)
{
     std::ostringstream command;
     std::vector<std::string> tokens;
     std::string delims="=";

     if(eAct==MM::BeforeGet)
     {

        command << "d.?lh";
        int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
        if (ret != DEVICE_OK) return ret;
        CDeviceUtils::SleepMs(50);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", hoursD_);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", hoursD_);
        PurgeComPort(port_.c_str());
        if (ret != DEVICE_OK) return ret;

        VersaLase::Tokenize(hoursD_, tokens, delims);
        if ( 2 == tokens.size())
        {
		    hoursD_=tokens.at(1).c_str();
        }
        pProp->Set(hoursD_.c_str());
     }
     else
         if(eAct==MM::AfterSet)
         {
             //Read Only, Do Nothing
         }

     return DEVICE_OK;
}
int VersaLase::OnFaultCodeA(MM::PropertyBase* pProp, MM::ActionType eAct)
{
     std::ostringstream command;
     std::vector<std::string> tokens;
     std::string delims="=";

     if(eAct==MM::BeforeGet)
     {

        command << "a.?fc";
        int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
        if (ret != DEVICE_OK) return ret;
        CDeviceUtils::SleepMs(50);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", faultCodeA_);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", faultCodeA_);
        PurgeComPort(port_.c_str());
        if (ret != DEVICE_OK) return ret;

        VersaLase::Tokenize(faultCodeA_, tokens, delims);
        if ( 2 == tokens.size())
        {
		    faultCodeA_=tokens.at(1).c_str();
        }
        pProp->Set(faultCodeA_.c_str());

     }
     else
        if(eAct==MM::AfterSet)
        {
            //Read Only, Do Nothing
        }

     return DEVICE_OK;

}

int VersaLase::OnFaultCodeB(MM::PropertyBase* pProp, MM::ActionType eAct)
{
     std::ostringstream command;
     std::vector<std::string> tokens;
     std::string delims="=";

     if(eAct==MM::BeforeGet)
     {

        command << "b.?fc";
        int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
        if (ret != DEVICE_OK) return ret;
        CDeviceUtils::SleepMs(50);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", faultCodeB_);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", faultCodeB_);
        PurgeComPort(port_.c_str());
        if (ret != DEVICE_OK) return ret;

        VersaLase::Tokenize(faultCodeB_, tokens, delims);
        if ( 2 == tokens.size())
        {
		    faultCodeB_=tokens.at(1).c_str();
        }
        pProp->Set(faultCodeB_.c_str());

     }
     else
        if(eAct==MM::AfterSet)
        {
            //Read Only, Do Nothing
        }

     return DEVICE_OK;

}

int VersaLase::OnFaultCodeC(MM::PropertyBase* pProp, MM::ActionType eAct)
{
     std::ostringstream command;
     std::vector<std::string> tokens;
     std::string delims="=";

     if(eAct==MM::BeforeGet)
     {

        command << "c.?fc";
        int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
        if (ret != DEVICE_OK) return ret;
        CDeviceUtils::SleepMs(50);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", faultCodeC_);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", faultCodeC_);
        PurgeComPort(port_.c_str());
        if (ret != DEVICE_OK) return ret;

        VersaLase::Tokenize(faultCodeC_, tokens, delims);
        if ( 2 == tokens.size())
        {
		    faultCodeC_=tokens.at(1).c_str();
        }
        pProp->Set(faultCodeC_.c_str());

     }
     else
        if(eAct==MM::AfterSet)
        {
            //Read Only, Do Nothing
        }

     return DEVICE_OK;

}

int VersaLase::OnFaultCodeD(MM::PropertyBase* pProp, MM::ActionType eAct)
{
     std::ostringstream command;
     std::vector<std::string> tokens;
     std::string delims="=";

     if(eAct==MM::BeforeGet)
     {

        command << "d.?fc";
        int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
        if (ret != DEVICE_OK) return ret;
        CDeviceUtils::SleepMs(50);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", faultCodeD_);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", faultCodeD_);
        PurgeComPort(port_.c_str());
        if (ret != DEVICE_OK) return ret;

        VersaLase::Tokenize(faultCodeD_, tokens, delims);
        if ( 2 == tokens.size())
        {
		    faultCodeD_=tokens.at(1).c_str();
        }
        pProp->Set(faultCodeD_.c_str());

     }
     else
        if(eAct==MM::AfterSet)
        {
            //Read Only, Do Nothing
        }

     return DEVICE_OK;

}
//Laser Serial Number, Part Number, Lambda, Nom. Power, Circular or Elliptical Beam
int VersaLase::OnSerialNumberA(MM::PropertyBase* pProp, MM::ActionType eAct)
{
     std::ostringstream command,parsed;
     std::vector<std::string> tokens;
     std::string delims="=";

     if(eAct==MM::BeforeGet)
     {

        command << "a.?li";
        int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
        if (ret != DEVICE_OK) return ret;
        CDeviceUtils::SleepMs(50);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", serialNumberA_);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", serialNumberA_);
        PurgeComPort(port_.c_str());
        if (ret != DEVICE_OK) return ret;

        VersaLase::Tokenize(serialNumberA_, tokens, delims);
        if ( 2 == tokens.size())
        {
		    serialNumberA_ = tokens.at(1);
        }
        pProp->Set(serialNumberA_.c_str());
     }
     else
        if(eAct==MM::AfterSet)
        {
            //Read Only, Do Nothing
        }

     return DEVICE_OK;
}

//Laser Serial Number, Part Number, Lambda, Nom. Power, Circular or Elliptical Beam
int VersaLase::OnSerialNumberB(MM::PropertyBase* pProp, MM::ActionType eAct)
{
     std::ostringstream command,parsed;
     std::vector<std::string> tokens;
     std::string delims="=";

     if(eAct==MM::BeforeGet)
     {

        command << "b.?li";
        int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
        if (ret != DEVICE_OK) return ret;
        CDeviceUtils::SleepMs(50);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", serialNumberB_);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", serialNumberB_);
        PurgeComPort(port_.c_str());
        if (ret != DEVICE_OK) return ret;

        VersaLase::Tokenize(serialNumberB_, tokens, delims);
        if ( 2 == tokens.size())
        {
		    serialNumberB_ = tokens.at(1);
        }
        pProp->Set(serialNumberB_.c_str());
     }
     else
        if(eAct==MM::AfterSet)
        {
            //Read Only, Do Nothing
        }

     return DEVICE_OK;
}

//Laser Serial Number, Part Number, Lambda, Nom. Power, Circular or Elliptical Beam
int VersaLase::OnSerialNumberC(MM::PropertyBase* pProp, MM::ActionType eAct)
{
     std::ostringstream command,parsed;
     std::vector<std::string> tokens;
     std::string delims="=";

     if(eAct==MM::BeforeGet)
     {

        command << "c.?li";
        int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
        if (ret != DEVICE_OK) return ret;
        CDeviceUtils::SleepMs(50);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", serialNumberC_);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", serialNumberC_);
        PurgeComPort(port_.c_str());
        if (ret != DEVICE_OK) return ret;

        VersaLase::Tokenize(serialNumberC_, tokens, delims);
        if ( 2 == tokens.size())
        {
		    serialNumberC_ = tokens.at(1);
        }
        pProp->Set(serialNumberC_.c_str());
     }
     else
        if(eAct==MM::AfterSet)
        {
            //Read Only, Do Nothing
        }

     return DEVICE_OK;
}

//Laser Serial Number, Part Number, Lambda, Nom. Power, Circular or Elliptical Beam
int VersaLase::OnSerialNumberD(MM::PropertyBase* pProp, MM::ActionType eAct)
{
     std::ostringstream command,parsed;
     std::vector<std::string> tokens;
     std::string delims="=";

     if(eAct==MM::BeforeGet)
     {

        command << "d.?li";
        int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
        if (ret != DEVICE_OK) return ret;
        CDeviceUtils::SleepMs(50);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", serialNumberD_);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", serialNumberD_);
        PurgeComPort(port_.c_str());
        if (ret != DEVICE_OK) return ret;

        VersaLase::Tokenize(serialNumberD_, tokens, delims);
        if ( 2 == tokens.size())
        {
		    serialNumberD_ = tokens.at(1);
        }
        pProp->Set(serialNumberD_.c_str());
     }
     else
        if(eAct==MM::AfterSet)
        {
            //Read Only, Do Nothing
        }

     return DEVICE_OK;
}
//Firmware Version
int VersaLase::OnVersion(MM::PropertyBase* pProp, MM::ActionType eAct)
{
     std::ostringstream command;
     std::vector<std::string> tokens;
     std::string delims="=";

     if(eAct==MM::BeforeGet)
     {

        command << "?sfv";
        int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
        if (ret != DEVICE_OK) return ret;
        CDeviceUtils::SleepMs(50);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", version_);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", version_);
        PurgeComPort(port_.c_str());
        if (ret != DEVICE_OK) return ret;

        VersaLase::Tokenize(version_, tokens, delims);
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
int VersaLase::OnCurrentA(MM::PropertyBase* pProp, MM::ActionType eAct)
{
     std::ostringstream command;
     std::vector<std::string> tokens;
     std::string delims="=";

     if(eAct==MM::BeforeGet)
     {
        command << "a.?lc";
        int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
        if (ret != DEVICE_OK) return ret;
        CDeviceUtils::SleepMs(50);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", currentA_);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", currentA_);
        PurgeComPort(port_.c_str());
        if (ret != DEVICE_OK) return ret;

        VersaLase::Tokenize(currentA_, tokens, delims);

        if ( 2 == tokens.size())
        {
		    currentA_=tokens.at(1).c_str();
        }

        pProp->Set(currentA_.c_str());
     }
     else
        if(eAct==MM::AfterSet)
        {
            //Read Only, Do Nothing
        }

     return DEVICE_OK;
}

//Laser Diode Current Get
int VersaLase::OnCurrentB(MM::PropertyBase* pProp, MM::ActionType eAct)
{
     std::ostringstream command;
     std::vector<std::string> tokens;
     std::string delims="=";

     if(eAct==MM::BeforeGet)
     {
        command << "b.?lc";
        int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
        if (ret != DEVICE_OK) return ret;
        CDeviceUtils::SleepMs(50);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", currentB_);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", currentB_);
        PurgeComPort(port_.c_str());
        if (ret != DEVICE_OK) return ret;

        VersaLase::Tokenize(currentB_, tokens, delims);

        if ( 2 == tokens.size())
        {
		    currentB_=tokens.at(1).c_str();
        }

        pProp->Set(currentB_.c_str());
     }
     else
        if(eAct==MM::AfterSet)
        {
            //Read Only, Do Nothing
        }

     return DEVICE_OK;
}

//Laser Diode Current Get
int VersaLase::OnCurrentC(MM::PropertyBase* pProp, MM::ActionType eAct)
{
     std::ostringstream command;
     std::vector<std::string> tokens;
     std::string delims="=";

     if(eAct==MM::BeforeGet)
     {
        command << "c.?lc";
        int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
        if (ret != DEVICE_OK) return ret;
        CDeviceUtils::SleepMs(50);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", currentC_);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", currentC_);
        PurgeComPort(port_.c_str());
        if (ret != DEVICE_OK) return ret;

        VersaLase::Tokenize(currentC_, tokens, delims);

        if ( 2 == tokens.size())
        {
		    currentC_=tokens.at(1).c_str();
        }

        pProp->Set(currentC_.c_str());
     }
     else
        if(eAct==MM::AfterSet)
        {
            //Read Only, Do Nothing
        }

     return DEVICE_OK;
}

//Laser Diode Current Get
int VersaLase::OnCurrentD(MM::PropertyBase* pProp, MM::ActionType eAct)
{
     std::ostringstream command;
     std::vector<std::string> tokens;
     std::string delims="=";

     if(eAct==MM::BeforeGet)
     {
        command << "d.?lc";
        int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
        if (ret != DEVICE_OK) return ret;
        CDeviceUtils::SleepMs(50);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", currentD_);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", currentD_);
        PurgeComPort(port_.c_str());
        if (ret != DEVICE_OK) return ret;

        VersaLase::Tokenize(currentD_, tokens, delims);

        if ( 2 == tokens.size())
        {
		    currentD_=tokens.at(1).c_str();
        }

        pProp->Set(currentD_.c_str());
     }
     else
        if(eAct==MM::AfterSet)
        {
            //Read Only, Do Nothing
        }

     return DEVICE_OK;
}
//Interlock Status
int VersaLase::OnInterlock(MM::PropertyBase* pProp, MM::ActionType eAct)
{
     std::ostringstream command;
     std::string answer;
     std::vector<std::string> tokens;
     std::string delims="=";
	 int answerNum;

     if(eAct==MM::BeforeGet)
     {
        command << "?il";
        int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
        if (ret != DEVICE_OK) return ret;
        CDeviceUtils::SleepMs(100);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
        PurgeComPort(port_.c_str());
        if (ret != DEVICE_OK) return ret;

        VersaLase::Tokenize(answer, tokens, delims);
        if ( 2 == tokens.size())
        {
		    answer=tokens.at(1).c_str();
        }

		answerNum=atol(answer.c_str());

	    if (answerNum == 0)
             interlock_ = "INTERLOCK OPEN!";
        else if (answerNum == 1)
             interlock_ = "OK";

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
int VersaLase::OnFaultA(MM::PropertyBase* pProp, MM::ActionType eAct)
{
     std::ostringstream command;
     std::string answer;
     std::vector<std::string> tokens;
     std::string delims="=";

     if(eAct==MM::BeforeGet)
     {
        command << "a.?fd";
        int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
        if (ret != DEVICE_OK) return ret;
        CDeviceUtils::SleepMs(50);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
        PurgeComPort(port_.c_str());
        if (ret != DEVICE_OK) return ret;

        VersaLase::Tokenize(answer, tokens, delims);
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

//System Faults or Operating Condition
int VersaLase::OnFaultB(MM::PropertyBase* pProp, MM::ActionType eAct)
{
     std::ostringstream command;
     std::string answer;
     std::vector<std::string> tokens;
     std::string delims="=";

     if(eAct==MM::BeforeGet)
     {
        command << "b.?fd";
        int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
        if (ret != DEVICE_OK) return ret;
        CDeviceUtils::SleepMs(50);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
        PurgeComPort(port_.c_str());
        if (ret != DEVICE_OK) return ret;

        VersaLase::Tokenize(answer, tokens, delims);
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

//System Faults or Operating Condition
int VersaLase::OnFaultC(MM::PropertyBase* pProp, MM::ActionType eAct)
{
     std::ostringstream command;
     std::string answer;
     std::vector<std::string> tokens;
     std::string delims="=";

     if(eAct==MM::BeforeGet)
     {
        command << "c.?fd";
        int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
        if (ret != DEVICE_OK) return ret;
        CDeviceUtils::SleepMs(50);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
        PurgeComPort(port_.c_str());
        if (ret != DEVICE_OK) return ret;

        VersaLase::Tokenize(answer, tokens, delims);
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

//System Faults or Operating Condition
int VersaLase::OnFaultD(MM::PropertyBase* pProp, MM::ActionType eAct)
{
     std::ostringstream command;
     std::string answer;
     std::vector<std::string> tokens;
     std::string delims="=";

     if(eAct==MM::BeforeGet)
     {
        command << "d.?fd";
        int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
        if (ret != DEVICE_OK) return ret;
        CDeviceUtils::SleepMs(50);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
        ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
        PurgeComPort(port_.c_str());
        if (ret != DEVICE_OK) return ret;

        VersaLase::Tokenize(answer, tokens, delims);
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
int VersaLase::OnLaserOnOffA(MM::PropertyBase* pProp, MM::ActionType eAct)
{
     std::ostringstream command;
     std::string answer;
     std::vector<std::string> tokens;
     std::string delims="=";
	 int answerNum;

     if (eAct == MM::BeforeGet)
     {
          command << "a.?le";
          int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
          if (ret != DEVICE_OK) return ret;
          CDeviceUtils::SleepMs(50);
          ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
          ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
          PurgeComPort(port_.c_str());
          if (ret != DEVICE_OK) return ret;

          VersaLase::Tokenize(answer, tokens, delims);
          if ( 2 == tokens.size())
          {
		     answer=tokens.at(1).c_str();
          }

		  answerNum=atol(answer.c_str());

	      if (answerNum == 0)
               laserOnA_ = "OFF";
          else if (answerNum == 1)
               laserOnA_ = "ON";

          pProp->Set(laserOnA_.c_str());
     }
     else
        if (eAct == MM::AfterSet)
        {
          pProp->Get(answer);
          if (answer == "OFF")
          {
               LaserOnOffA(false); //Turn laser off
          }
          else
            if(answer == "ON")
            {
               LaserOnOffA(true); //Turn laser on
            }
        }

     return DEVICE_OK;
}

//Laser Emission Status
int VersaLase::OnLaserOnOffB(MM::PropertyBase* pProp, MM::ActionType eAct)
{
     std::ostringstream command;
     std::string answer;
     std::vector<std::string> tokens;
     std::string delims="=";
	 int answerNum;

     if (eAct == MM::BeforeGet)
     {
          command << "b.?le";
          int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
          if (ret != DEVICE_OK) return ret;
          CDeviceUtils::SleepMs(50);
          ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
          ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
          PurgeComPort(port_.c_str());
          if (ret != DEVICE_OK) return ret;

          VersaLase::Tokenize(answer, tokens, delims);
          if ( 2 == tokens.size())
          {
		     answer=tokens.at(1).c_str();
          }

	      answerNum=atol(answer.c_str());

	      if (answerNum == 0)
               laserOnB_ = "OFF";
          else if (answerNum == 1)
               laserOnB_ = "ON";

          pProp->Set(laserOnB_.c_str());
     }
     else
        if (eAct == MM::AfterSet)
        {
          pProp->Get(answer);
          if (answer == "OFF")
          {
               LaserOnOffB(false); //Turn laser off
          }
          else
            if(answer == "ON")
            {
               LaserOnOffB(true); //Turn laser on
            }
        }

     return DEVICE_OK;
}

//Laser Emission Status
int VersaLase::OnLaserOnOffC(MM::PropertyBase* pProp, MM::ActionType eAct)
{
     std::ostringstream command;
     std::string answer;
     std::vector<std::string> tokens;
     std::string delims="=";
	 int answerNum;

     if (eAct == MM::BeforeGet)
     {
          command << "c.?le";
          int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
          if (ret != DEVICE_OK) return ret;
          CDeviceUtils::SleepMs(50);
          ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
          ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
          PurgeComPort(port_.c_str());
          if (ret != DEVICE_OK) return ret;

          VersaLase::Tokenize(answer, tokens, delims);
          if ( 2 == tokens.size())
          {
		     answer=tokens.at(1).c_str();
          }

	      answerNum=atol(answer.c_str());

	      if (answerNum == 0)
               laserOnC_ = "OFF";
          else if (answerNum == 1)
               laserOnC_ = "ON";

          pProp->Set(laserOnC_.c_str());
     }
     else
        if (eAct == MM::AfterSet)
        {
          pProp->Get(answer);
          if (answer == "OFF")
          {
               LaserOnOffC(false); //Turn laser off
          }
          else
            if(answer == "ON")
            {
               LaserOnOffC(true); //Turn laser on
            }
        }

     return DEVICE_OK;
}

//Laser Emission Status
int VersaLase::OnLaserOnOffD(MM::PropertyBase* pProp, MM::ActionType eAct)
{
     std::ostringstream command;
     std::string answer;
     std::vector<std::string> tokens;
     std::string delims="=";
	 int answerNum;

     if (eAct == MM::BeforeGet)
     {
          command << "d.?le";
          int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
          if (ret != DEVICE_OK) return ret;
          CDeviceUtils::SleepMs(50);
          ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
          ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
          PurgeComPort(port_.c_str());
          if (ret != DEVICE_OK) return ret;

          VersaLase::Tokenize(answer, tokens, delims);
          if ( 2 == tokens.size())
          {
		     answer=tokens.at(1).c_str();
          }

	      answerNum=atol(answer.c_str());

	      if (answerNum == 0)
               laserOnD_ = "OFF";
          else if (answerNum == 1)
               laserOnD_ = "ON";

          pProp->Set(laserOnD_.c_str());
     }
     else
        if (eAct == MM::AfterSet)
        {
          pProp->Get(answer);
          if (answer == "OFF")
          {
               LaserOnOffD(false); //Turn laser off
          }
          else
            if(answer == "ON")
            {
               LaserOnOffD(true); //Turn laser on
            }
        }

     return DEVICE_OK;
}

//C++ string parsing utility
void VersaLase::Tokenize(const std::string& str, std::vector<std::string>& tokens, const std::string& delimiters)
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
