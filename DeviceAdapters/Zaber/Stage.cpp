///////////////////////////////////////////////////////////////////////////////
// FILE:          Stage.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Stage
//
// AUTHOR:        Athabasca Witschi, athabasca@zaber.com

// COPYRIGHT:     Zaber Technologies, 2014

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
#define snprintf _snprintf 
#pragma warning(disable: 4355)
#endif

#include "Stage.h"

const char* g_StageName = "Stage";
const char* g_StageDescription = "Zaber Stage";

using namespace std;

Stage::Stage() :
	ZaberBase(this),
	deviceAddress_(1),
	axisNumber_(1),
	homingTimeoutMs_(20000),
	stepSizeUm_(0.15625),
	convFactor_(1.6384), // not very informative name
	cmdPrefix_("/"),
	resolution_(64),
	motorSteps_(200),
	linearMotion_(2.0)
{
	this->LogMessage("Stage::Stage\n", true);

	InitializeDefaultErrorMessages();
	SetErrorText(ERR_PORT_CHANGE_FORBIDDEN, g_Msg_PORT_CHANGE_FORBIDDEN);
	SetErrorText(ERR_DRIVER_DISABLED, g_Msg_DRIVER_DISABLED);
	SetErrorText(ERR_BUSY_TIMEOUT, g_Msg_BUSY_TIMEOUT);
	SetErrorText(ERR_COMMAND_REJECTED, g_Msg_COMMAND_REJECTED);
	SetErrorText(ERR_SETTING_FAILED, g_Msg_SETTING_FAILED);

	// Pre-initialization properties
	CreateProperty(MM::g_Keyword_Name, g_StageName, MM::String, true);

	CreateProperty(MM::g_Keyword_Description, "Zaber stage driver adapter", MM::String, true);

	CPropertyAction* pAct = new CPropertyAction (this, &Stage::OnPort);
	CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

	pAct = new CPropertyAction (this, &Stage::OnDeviceAddress);
	CreateIntegerProperty("Controller Device Number", deviceAddress_, false, pAct, true);
	SetPropertyLimits("Controller Device Number", 1, 99);

	pAct = new CPropertyAction(this, &Stage::OnAxisNumber);
	CreateIntegerProperty("Axis Number", axisNumber_, false, pAct, true);
	SetPropertyLimits("Axis Number", 1, 9);

	pAct = new CPropertyAction(this, &Stage::OnMotorSteps);
	CreateIntegerProperty("Motor Steps Per Rev", motorSteps_, false, pAct, true);

	pAct = new CPropertyAction(this, &Stage::OnLinearMotion);
	CreateFloatProperty("Linear Motion Per Motor Rev [mm]", linearMotion_, false, pAct, true);
}

Stage::~Stage()
{
	this->LogMessage("Stage::~Stage\n", true);
	Shutdown();
}

///////////////////////////////////////////////////////////////////////////////
// Stage & Device API methods
///////////////////////////////////////////////////////////////////////////////

void Stage::GetName(char* name) const
{
	CDeviceUtils::CopyLimitedString(name, g_StageName);
}

int Stage::Initialize()
{
	if (initialized_) return DEVICE_OK;

	core_ = GetCoreCallback();
	
	this->LogMessage("Stage::Initialize\n", true);

	int ret = ClearPort();
	if (ret != DEVICE_OK) 
	{
		return ret;
	}

	// Disable alert messages.
	ret = SetSetting(deviceAddress_, 0, "comm.alert", 0);
	if (ret != DEVICE_OK) 
	{
		return ret;
	}

	// Calculate step size.
	ret = GetSetting(deviceAddress_, axisNumber_, "resolution", resolution_);
	if (ret != DEVICE_OK) 
	{
		return ret;
	}
	stepSizeUm_ = ((double)linearMotion_/(double)motorSteps_)*(1/(double)resolution_)*1000;

	CPropertyAction* pAct;
	// Initialize Speed (in mm/s)
	pAct = new CPropertyAction (this, &Stage::OnSpeed);
	ret = CreateFloatProperty("Speed [mm/s]", 0.0, false, pAct);
	if (ret != DEVICE_OK) 
	{
		return ret;
	}

	// Initialize Acceleration (in m/s²)
	pAct = new CPropertyAction (this, &Stage::OnAccel);
	ret = CreateFloatProperty("Acceleration [m/s^2]", 0.0, false, pAct);
	if (ret != DEVICE_OK) 
	{
		return ret;
	}

	ret = UpdateStatus();
	if (ret != DEVICE_OK) 
	{
		return ret;
	}

	initialized_ = true;
	return DEVICE_OK;
}

int Stage::Shutdown()
{
	this->LogMessage("Stage::Shutdown\n", true);
	if (initialized_)
	{
		initialized_ = false;
	}
	return DEVICE_OK;
}

bool Stage::Busy()
{
	this->LogMessage("Stage::Busy\n", true);
	return IsBusy(deviceAddress_);
}

int Stage::GetPositionUm(double& pos)
{
	this->LogMessage("Stage::GetPositionUm\n", true);
	
	long steps;
	int ret =  GetSetting(deviceAddress_, axisNumber_, "pos", steps);
	if (ret != DEVICE_OK) 
	{
		return ret;
	}
	pos = steps * stepSizeUm_;
	return DEVICE_OK;
}

int Stage::GetPositionSteps(long& steps)
{
	this->LogMessage("Stage::GetPositionSteps\n", true);
	return GetSetting(deviceAddress_, axisNumber_, "pos", steps);
}

int Stage::SetPositionUm(double pos)
{
	this->LogMessage("Stage::SetPositionUm\n", true);
	long steps = nint(pos/stepSizeUm_);
	return SetPositionSteps(steps);
}

int Stage::SetRelativePositionUm(double d)
{
	this->LogMessage("Stage::SetRelativePositionUm\n", true);
	long steps = nint(d/stepSizeUm_);
	return SetRelativePositionSteps(steps);
}

int Stage::SetPositionSteps(long steps)
{
	this->LogMessage("Stage::SetPositionSteps\n", true);
	return SendMoveCommand(deviceAddress_, axisNumber_, "abs", steps);
}

int Stage::SetRelativePositionSteps(long steps)
{
	this->LogMessage("Stage::SetRelativePositionSteps\n", true);
	return SendMoveCommand(deviceAddress_, axisNumber_, "rel", steps);
}

int Stage::Move(double velocity)
{
	this->LogMessage("Stage::Move\n", true);
	// convert velocity from mm/s to Zaber data value
	long velData = nint(velocity*convFactor_*1000/stepSizeUm_);
	return SendMoveCommand(deviceAddress_, axisNumber_, "vel", velData);
}

int Stage::Stop()
{
	this->LogMessage("Stage::Stop\n", true);
	return ZaberBase::Stop(deviceAddress_);
}

int Stage::Home()
{
	this->LogMessage("Stage::Home\n", true);
	//TODO try tools findrange first?
	ostringstream cmd;
	cmd << cmdPrefix_ << "home";
	return SendAndPollUntilIdle(deviceAddress_, axisNumber_, cmd.str().c_str(), homingTimeoutMs_);
}

int Stage::SetAdapterOriginUm(double /*d*/)
{
	this->LogMessage("Stage::SetAdapterOriginUm\n", true);
	return DEVICE_UNSUPPORTED_COMMAND;
}

int Stage::SetOrigin()
{
	this->LogMessage("Stage::SetOrigin\n", true);
	return DEVICE_UNSUPPORTED_COMMAND;
}

int Stage::GetLimits(double& lower, double& upper)
{
	this->LogMessage("Stage::GetLimits\n", true);

	long min, max;
	int ret = ZaberBase::GetLimits(deviceAddress_, axisNumber_, min, max);
	if (ret != DEVICE_OK)
	{
		return ret;
	}
	lower = (double)min;
	upper = (double)max;
	return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
// Handle changes and updates to property values.
///////////////////////////////////////////////////////////////////////////////

int Stage::OnPort (MM::PropertyBase* pProp, MM::ActionType eAct)
{
	ostringstream os;
	os << "Stage::OnPort(" << pProp << ", " << eAct << ")\n";
	this->LogMessage(os.str().c_str(), false);

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

int Stage::OnDeviceAddress (MM::PropertyBase* pProp, MM::ActionType eAct)
{
	this->LogMessage("Stage::OnDeviceAddress\n", true);

	if (eAct == MM::AfterSet)
	{
		pProp->Get(deviceAddress_);

		ostringstream cmdPrefix;
		cmdPrefix << "/" << deviceAddress_ << " ";
		cmdPrefix_ = cmdPrefix.str();
	}
	else if (eAct == MM::BeforeGet)
	{
		pProp->Set(deviceAddress_);
	}
	return DEVICE_OK;
}

int Stage::OnAxisNumber (MM::PropertyBase* pProp, MM::ActionType eAct)
{
	this->LogMessage("Stage::OnAxisNumber\n", true);

	if (eAct == MM::BeforeGet)
	{
		pProp->Set(axisNumber_);
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(axisNumber_);
	}
	return DEVICE_OK;
}

int Stage::OnSpeed (MM::PropertyBase* pProp, MM::ActionType eAct)
{
	this->LogMessage("Stage::OnSpeed\n", true);

	if (eAct == MM::BeforeGet)
	{
		long speedData;
		int ret = GetSetting(deviceAddress_, axisNumber_, "maxspeed", speedData);
		if (ret != DEVICE_OK) 
		{
			return ret;
		}

		// convert to mm/s
		double speed = (speedData/convFactor_)*stepSizeUm_/1000;
		pProp->Set(speed);
	}
	else if (eAct == MM::AfterSet)
	{
		double speed;
		pProp->Get(speed);

		// convert to data
		long speedData = nint(speed*convFactor_*1000/stepSizeUm_);
		if (speedData == 0 && speed != 0) speedData = 1; // Avoid clipping to 0.

		int ret = SetSetting(deviceAddress_, axisNumber_, "maxspeed", speedData);
		if (ret != DEVICE_OK) 
		{
			return ret;
		}
	}
	return DEVICE_OK;
}

int Stage::OnAccel (MM::PropertyBase* pProp, MM::ActionType eAct)
{
	this->LogMessage("Stage::OnAccel\n", true);

	if (eAct == MM::BeforeGet)
	{
		long accelData;
		int ret = GetSetting(deviceAddress_, axisNumber_, "accel", accelData);
		if (ret != DEVICE_OK) 
		{
			return ret;
		}

		// convert to m/s²
		double accel = (accelData*10/convFactor_)*stepSizeUm_/1000;
		pProp->Set(accel);
	}
	else if (eAct == MM::AfterSet)
	{
		double accel;
		pProp->Get(accel);

		// convert to data
		long accelData = nint(accel*convFactor_*100/(stepSizeUm_));
		if (accelData == 0 && accel != 0) accelData = 1; // Only set accel to 0 if user intended it.

		int ret = SetSetting(deviceAddress_, axisNumber_, "accel", accelData);
		if (ret != DEVICE_OK) 
		{
			return ret;
		}
	}
	return DEVICE_OK;
}

int Stage::OnMotorSteps(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	this->LogMessage("Stage::OnMotorSteps\n", true);

	if (eAct == MM::BeforeGet)
	{
		pProp->Set(motorSteps_);
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(motorSteps_);
	}
	return DEVICE_OK;
}

int Stage::OnLinearMotion(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	this->LogMessage("Stage::OnLinearMotion\n", true);

	if (eAct == MM::BeforeGet)
	{
		pProp->Set(linearMotion_);
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(linearMotion_);
	}
	return DEVICE_OK;
}