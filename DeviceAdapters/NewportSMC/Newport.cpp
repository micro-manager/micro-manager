///////////////////////////////////////////////////////////////////////////////
// FILE:          Newport.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Newport Controller Driver
//
// AUTHOR:        Liisa Hirvonen, 03/17/2009
// COPYRIGHT:     University of Melbourne, Australia, 2009-2013
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
//

#ifdef WIN32
	#include <windows.h>
	#define snprintf _snprintf
#endif

#include "Newport.h"
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>

const char* g_Newport_ZStageDeviceName = "NewportZStage";

using namespace std;

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
	RegisterDevice(g_Newport_ZStageDeviceName, MM::StageDevice, "Newport SMC100CC controller");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
	if (deviceName == 0)
		return 0;

	if (strcmp(deviceName, g_Newport_ZStageDeviceName) == 0)
	{
		NewportZStage* s = new NewportZStage();
		return s;
	}

	return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
	delete pDevice;
}


///////////////////////////////////////////////////////////////////////////////
// NewportZStage

NewportZStage::NewportZStage() :
	//port_("Undefined"),	;HCA 2013-02-15
	port_("COM5"),			//need to recompile with COM5 listed.
	stepSizeUm_(1),
	initialized_(false),
	lowerLimit_(0),
	upperLimit_(25)
{
	InitializeDefaultErrorMessages();

	// create properties
	// ------------------------------------

	// Name
	CreateProperty(MM::g_Keyword_Name, g_Newport_ZStageDeviceName, MM::String, true);

	// Description
	CreateProperty(MM::g_Keyword_Description, "Newport SMC100CC controller adapter", MM::String, true);

	// Port
	CPropertyAction* pAct = new CPropertyAction (this, &NewportZStage::OnPort);
	CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

	// Position
	pAct = new CPropertyAction (this, &NewportZStage::OnPosition);
	CreateProperty(MM::g_Keyword_Position, "0", MM::Float, false, pAct);
}

NewportZStage::~NewportZStage()
{
	Shutdown();
}

void NewportZStage::GetName(char* Name) const
{
	CDeviceUtils::CopyLimitedString(Name, g_Newport_ZStageDeviceName);
}

int NewportZStage::Initialize()
{
	const char* command;

	// Not sure why we need to send the terminator before the
	// first command but otherwise we get an error
	SendSerialCommand(port_.c_str(), "\n", "\n");

	// Send the "homing" command to init stage
	command = "\n1OR";
	int ret = SendSerialCommand(port_.c_str(), command, "\n");
	if (ret != DEVICE_OK)
		return ret;

	// Not sure what is wrong with the flow control
	// but we need to wait 10 ms and then send the terminator
	// to make the stage execute commands
    CDeviceUtils::SleepMs(10);
	SendSerialCommand(port_.c_str(), "\n", "\n");

	ret = UpdateStatus();
	if (ret != DEVICE_OK)
		return ret;

	initialized_ = true;
	return DEVICE_OK;
}

int NewportZStage::Shutdown()
{
	if (initialized_)
	{
		// Move home to avoid time-out with next initialisation
		const char* command = "\n1PA0";
		int ret = SendSerialCommand(port_.c_str(), command, "\n");
		if (ret != DEVICE_OK)
		  return ret;
		CDeviceUtils::SleepMs(10);
		SendSerialCommand(port_.c_str(), "\n", "\n");
		CDeviceUtils::SleepMs(10);

		// Wait for the device to stop moving
		while(Busy()) {}

		// Send the reset command
		command = "\n1RS";
		ret = SendSerialCommand(port_.c_str(), command, "\n");
		if (ret != DEVICE_OK)
			return ret;

	    CDeviceUtils::SleepMs(10);
		ret = SendSerialCommand(port_.c_str(), "\n", "\n");
		if (ret != DEVICE_OK)
			return ret;

		initialized_ = false;
	}
	return DEVICE_OK;
}

bool NewportZStage::Busy()
{
	const char* command;
	string answer;
	double setPos;
	double curPos;
	int ret;

	// Ask for set point position
	command = "\n1TH";
	SendSerialCommand(port_.c_str(), command, "\n");
    CDeviceUtils::SleepMs(10);
	SendSerialCommand(port_.c_str(), "\n", "\n");
    CDeviceUtils::SleepMs(10);
	ret = GetSerialAnswer(port_.c_str(), "\n", answer);
	if (ret != DEVICE_OK)
		return false;
	setPos = atof(answer.substr(3,15).c_str());

	// Ask for current position
	command = "\n1TP";
	SendSerialCommand(port_.c_str(), command, "\n");
	CDeviceUtils::SleepMs(10);
	SendSerialCommand(port_.c_str(), "\n", "\n");
    CDeviceUtils::SleepMs(10);
	GetSerialAnswer(port_.c_str(), "\n", answer);
	if (ret != DEVICE_OK)
		return false;
	curPos = atof(answer.substr(3,15).c_str());

	// Still moving if the positions are not equal
	if (setPos != curPos)
		return true;

	return false;
}

int NewportZStage::SetPositionSteps(long steps)
{
	double pos = steps * stepSizeUm_;
	return SetPositionUm(pos);
}

int NewportZStage::GetPositionSteps(long& steps)
{
	double pos;
	int ret = GetPositionUm(pos);
	if (ret != DEVICE_OK)
		return ret;
	steps = (long) (pos / stepSizeUm_);
	return DEVICE_OK;
}

int NewportZStage::SetPositionUm(double pos)
{
	ostringstream command;
	string answer;

	// Send the "move" command
	command << "\n1PA" << pos;
	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\n");
	if (ret != DEVICE_OK)
      return ret;
    CDeviceUtils::SleepMs(10);
	SendSerialCommand(port_.c_str(), "\n", "\n");
    CDeviceUtils::SleepMs(10);

	// Ask for error message
	ret = SendSerialCommand(port_.c_str(), "\n1TE", "\n");
	if (ret != DEVICE_OK)
		return ret;
	CDeviceUtils::SleepMs(10);
	SendSerialCommand(port_.c_str(), "\n", "\n");
    CDeviceUtils::SleepMs(10);

	// Receive error message
	ret = GetSerialAnswer(port_.c_str(), "\n", answer);
	if (ret != DEVICE_OK)
		return ret;

	// Check that there is no error
	if (answer.substr(3).compare("@") == 1)
		return DEVICE_OK;

	return ERR_UNRECOGNIZED_ANSWER;
}

int NewportZStage::GetPositionUm(double& pos)
{
	const char* command;
	string answer;

	// Ask position
	command = "\n1TP";
	int ret = SendSerialCommand(port_.c_str(), command, "\n");
	if (ret != DEVICE_OK)
		return ret;
    CDeviceUtils::SleepMs(10);
	SendSerialCommand(port_.c_str(), "\n", "\n");
    CDeviceUtils::SleepMs(10);

	// Receive answer
	ret = GetSerialAnswer(port_.c_str(), "\n", answer);
	if (ret != DEVICE_OK)
		return ret;

	// Get the value from the reply string
	pos = atof(answer.substr(3,15).c_str());

	return DEVICE_OK;
}

int NewportZStage::SetOrigin()
{
	return DEVICE_UNSUPPORTED_COMMAND;
}

int NewportZStage::GetLimits(double&, double&)
{
	return DEVICE_UNSUPPORTED_COMMAND;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int NewportZStage::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int NewportZStage::OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		// nothing to do, let the caller use cached property
	}
	else if (eAct == MM::AfterSet)
	{
		double pos;
		pProp->Get(pos);
		if (pos > upperLimit_ || lowerLimit_ > pos)
		{
			pProp->Set(pos); // revert
			return ERR_UNKNOWN_POSITION;
		}
		int ret = SetPositionUm(pos);
		if (ret != DEVICE_OK)
			return ret;
   }

   return DEVICE_OK;
}
