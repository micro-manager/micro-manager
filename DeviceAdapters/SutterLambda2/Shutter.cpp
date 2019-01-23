///////////////////////////////////////////////////////////////////////////////
// FILE:          Shutter.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Sutter Lambda controller adapter
// COPYRIGHT:     Northwestern University, 2018
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
// AUTHOR:        Nick Anthony, Oct. 2018
//
// CVS:           $Id$
//

#include "SutterHub.h"
#include "SutterShutter.h"

const char* g_ShutterModeProperty = "Mode";
const char* g_FastMode = "Fast";
const char* g_SoftMode = "Soft";
const char* g_NDMode = "ND";
const char* g_ControllerID = "Controller Info";

///////////////////////////////////////////////////////////////////////////////
// Shutter implementation
// ~~~~~~~~~~~~~~~~~~~~~~~

Shutter::Shutter(const char* name, int id) :
	initialized_(false),
	id_(id),
	name_(name),
	nd_(1),
	controllerType_("10-2"),
	controllerId_(""),
	answerTimeoutMs_(500),
	curMode_(g_FastMode)
{
	InitializeDefaultErrorMessages();
	SetErrorText(ERR_NO_ANSWER, "No Sutter Controller found.  Is it switched on and connected to the specified comunication port?");

	// create pre-initialization properties
	// ------------------------------------

	// Name
	CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);

	// Description
	CreateProperty(MM::g_Keyword_Description, "Sutter Lambda shutter adapter", MM::String, true);

	EnableDelay();
}

Shutter::~Shutter()
{
	Shutdown();
}

void Shutter::GetName(char* name) const
{
	CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int Shutter::Initialize()
{
	if (initialized_)
		return DEVICE_OK;

	hub_ = dynamic_cast<SutterHub*>(GetParentHub());

	// set property list
	// -----------------

	// State
	// -----
	CPropertyAction* pAct = new CPropertyAction(this, &Shutter::OnState);
	int ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
	if (ret != DEVICE_OK)
		return ret;

	AddAllowedValue(MM::g_Keyword_State, "0");
	AddAllowedValue(MM::g_Keyword_State, "1");

	ret = hub_->GetControllerType(controllerType_, controllerId_);
	if (ret != DEVICE_OK)
		return ret;

	CreateProperty(g_ControllerID, controllerId_.c_str(), MM::String, true);
	std::string msg = "Controller reported ID " + controllerId_;
	LogMessage(msg.c_str());

	// Shutter mode
	// ------------
	if (controllerType_ == "SC" || controllerType_ == "10-3") {
		pAct = new CPropertyAction(this, &Shutter::OnMode);
		std::vector<std::string> modes;
		modes.push_back(g_FastMode);
		modes.push_back(g_SoftMode);
		modes.push_back(g_NDMode);

		CreateProperty(g_ShutterModeProperty, g_FastMode, MM::String, false, pAct);
		SetAllowedValues(g_ShutterModeProperty, modes);
		// set initial value
		//SetProperty(g_ShutterModeProperty, curMode_.c_str());

		// Neutral Density-mode Shutter (will not work with 10-2 controller)
		pAct = new CPropertyAction(this, &Shutter::OnND);
		CreateProperty("NDSetting", "1", MM::Integer, false, pAct);
		SetPropertyLimits("NDSetting", 1, 144);
	}

	std::vector<unsigned char> status;
	ret = hub_->GetStatus(status);
	// note: some controllers will not know this command and return an error, 
	// so do not balk if we do not get an answer

	// status meaning is different on different controllers:
	if (controllerType_ == "10-3") {
		if (id_ == 0) {
			if (status[4] == 170)
				SetOpen(true);
			if (status[4] == 172)
				SetOpen(false);
			if (status[6] == 0xDC)
				curMode_ = g_FastMode;
			if (status[6] == 0xDD)
				curMode_ = g_SoftMode;
			if (status[6] == 0xDE)
				curMode_ = g_NDMode;
			if (curMode_ == g_NDMode)
				nd_ = (unsigned int)status[7];
		}
		else if (id_ == 1) {
			if (status[5] == 186)
				SetOpen(true);
			if (status[5] == 188)
				SetOpen(false);
			int offset = 0;
			if (status[6] == 0xDE)
				offset = 1;
			if (status[7 + offset] == 0xDC)
				curMode_ = g_FastMode;
			if (status[7 + offset] == 0xDD)
				curMode_ = g_SoftMode;
			if (status[7 + offset] == 0xDE)
				curMode_ = g_NDMode;
			if (curMode_ == g_NDMode)
				nd_ = (unsigned int)status[8 + offset];
		}
	}
	else if (controllerType_ == "SC") {
		if (status[0] == 170)
			SetOpen(true);
		if (status[0] == 172)
			SetOpen(false);
		if (status[1] == 0xDC)
			curMode_ = g_FastMode;
		if (status[1] == 0xDD)
			curMode_ = g_SoftMode;
		if (status[1] == 0xDE)
			curMode_ = g_NDMode;
		if (curMode_ == g_NDMode)
			nd_ = (unsigned int)status[2];
	}

	// Needed for Busy flag
	changedTime_ = GetCurrentMMTime();

	initialized_ = true;

	return DEVICE_OK;
}

int Shutter::Shutdown()
{
	initialized_ = false;
	return DEVICE_OK;
}

int Shutter::SetOpen(bool open)
{
	long pos;
	if (open)
		pos = 1;
	else
		pos = 0;
	return SetProperty(MM::g_Keyword_State, CDeviceUtils::ConvertToString(pos));
}

int Shutter::GetOpen(bool& open)
{
	char buf[MM::MaxStrLength];
	int ret = GetProperty(MM::g_Keyword_State, buf);
	if (ret != DEVICE_OK)
		return ret;
	long pos = atol(buf);
	pos == 1 ? open = true : open = false;

	return DEVICE_OK;
}
int Shutter::Fire(double /*deltaT*/)
{
	return DEVICE_UNSUPPORTED_COMMAND;
}

/**
* Sends a command to Lambda through the serial port.
*/
bool Shutter::SetShutterPosition(bool state)
{
	std::vector<unsigned char> command;
	std::vector<unsigned char> alternateEcho;

	// the Lambda intermittently SC echoes inverted commands!

	if (id_ == 0)
	{
		// shutter A
		command.push_back(state ? 170 : 172);
		alternateEcho.push_back(state ? 172 : 170);
	}
	else
	{
		// shutter B
		command.push_back(state ? 186 : 188);
		alternateEcho.push_back(state ? 188 : 186);
	}

	std::vector<unsigned char> _;
	int ret = hub_->SetCommand(command, alternateEcho);

	// Start timer for Busy flag
	changedTime_ = GetCurrentMMTime();

	return (DEVICE_OK == ret) ? true : false;
}

bool Shutter::Busy()
{
	if (GetDelayMs() > 0.0) {
		MM::MMTime interval = GetCurrentMMTime() - changedTime_;
		MM::MMTime delay(GetDelayMs()*1000.0);
		if (interval < delay) {
			return true;
		}
	}
	return false;
}

bool Shutter::SetShutterMode(const char* mode)
{
	if (strcmp(mode, g_NDMode) == 0)
		return SetND(nd_);

	std::vector<unsigned char> command;
	std::vector<unsigned char> alternateEcho;

	if (0 == strcmp(mode, g_FastMode))
		command.push_back((unsigned char)220);
	else if (0 == strcmp(mode, g_SoftMode))
		command.push_back((unsigned char)221);

	if ("SC" != controllerType_)
		command.push_back((unsigned char)(id_ + 1));

	std::vector<unsigned char> _;
	int ret = hub_->SetCommand(command, alternateEcho);
	return (DEVICE_OK == ret) ? true : false;
}



bool Shutter::SetND(unsigned int nd)
{
	std::vector<unsigned char> command;
	std::vector<unsigned char> alternateEcho;

	command.push_back((unsigned char)222);

	if ("SC" == controllerType_)
		command.push_back((unsigned char)nd);
	else
	{
		command.push_back((unsigned char)(id_ + 1));
		command.push_back((unsigned char)(nd));
	}
	std::vector<unsigned char> _;
	int ret = hub_->SetCommand(command, alternateEcho);
	return (DEVICE_OK == ret) ? true : false;

}


///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int Shutter::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::AfterSet) {
		long pos;
		pProp->Get(pos);

		// apply the value
		SetShutterPosition(pos == 0 ? false : true);
	}
	return DEVICE_OK;
}

int Shutter::OnMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		pProp->Set(curMode_.c_str());
	}
	else if (eAct == MM::AfterSet)
	{
		std::string mode;
		pProp->Get(mode);

		if (SetShutterMode(mode.c_str())) {
		curMode_ = mode;
		}
		else {
			return ERR_UNKNOWN_SHUTTER_MODE;
		}
	}
	return DEVICE_OK;
}

int Shutter::OnND(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet){
		pProp->Set((long)nd_);
	}
	else if (eAct == MM::AfterSet){
		std::string ts;
		pProp->Get(ts);
		std::istringstream os(ts);
		os >> nd_;
		if (curMode_ == g_NDMode) {
			SetND(nd_);
		}
	}
	return DEVICE_OK;
}
