// Olympus MT20 Device Adapter
//
// Copyright 2010 
// Michael Mitchell
// mich.r.mitchell@gmail.com
//
// Last modified 27.7.10
//
//
// This file is part of the Olympus MT20 Device Adapter.
//
// This device adapter requires the Real-Time Controller board that came in the original
// Cell^R/Scan^R/Cell^M/etc. computer to work. It uses TinyXML ( http://www.grinninglizard.com/tinyxml/ )
// to parse XML messages from the device.
//
// The Olympus MT20 Device Adapter is free software: you can redistribute it and/or modify
// it under the terms of the GNU Lesser General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// any later version.
//
// The Olympus MT20 Device Adapter is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public License
// along with the Olympus MT20 Device Adapter.  If not, see <http://www.gnu.org/licenses/>.

#include "MT20.h"
#include "../../MMDevice/ModuleInterface.h"
#include "../../MMCore/Error.h"

const char* g_MT20HUB = "MT20-HUB";
const char* g_MT20Burner = "MT20-Burner";
const char* g_MT20Shutter = "MT20-Shutter";
const char* g_MT20Filterwheel = "MT20-Filterwheel";
const char* g_MT20Attenuator = "MT20-Attenuator";

#ifdef WIN32
	#define snprintf _snprintf

	BOOL APIENTRY DllMain( HANDLE /*hModule*/,
							DWORD ul_reason_for_call,
							LPVOID /*lpReserved*/
							)
	{
		switch (ul_reason_for_call)
		{
		case DLL_PROCESS_ATTACH:
		case DLL_THREAD_ATTACH:
		case DLL_THREAD_DETACH:
		case DLL_PROCESS_DETACH:
			break;
		}
		return TRUE;
	}
#endif

// instantiate MT20hub object, which will handle communication with device
MT20hub mt20;

/****************************************************************************
		ModuleInterface API
****************************************************************************/

MODULE_API void InitializeModuleData()
{
	AddAvailableDeviceName(g_MT20HUB, "Olympus MT20 hub device");
	AddAvailableDeviceName(g_MT20Burner, "Olympus MT20 burner");
	AddAvailableDeviceName(g_MT20Shutter, "Olympus MT20 shutter");
	AddAvailableDeviceName(g_MT20Filterwheel, "Olympus MT20 filterwheel");
	AddAvailableDeviceName(g_MT20Attenuator, "Olympus MT20 attenuator");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
	if(deviceName == 0)
	{
		return 0;
	}

	if(strcmp(deviceName, g_MT20HUB) == 0)
	{
		return new MT20HUB();
	}
	else if(strcmp(deviceName, g_MT20Burner) == 0)
	{
		return new MT20Burner();
	}
	else if(strcmp(deviceName, g_MT20Shutter) == 0)
	{
		return new MT20Shutter();
	}
	else if(strcmp(deviceName, g_MT20Filterwheel) == 0)
	{
		return new MT20Filterwheel();
	}
	else if(strcmp(deviceName, g_MT20Attenuator) == 0)
	{
		return new MT20Attenuator();
	}

	return 0;
}

MODULE_API void DeleteDevice(MM::Device* device)
{
	delete device;
}

/****************************************************************************
		MT20 Hub
		Handles general device initialization, i.e., calls MT20hub's
			initialize and shutdown functions
****************************************************************************/

MT20HUB::MT20HUB() : 
	initialized_(false),
	busy_(false)
{
	InitializeDefaultErrorMessages();

	SetErrorText(ERR_UNABLE_TO_CONNECT, "Unable to connect to the MT20");
	SetErrorText(ERR_EXECUTING_CMD, "MT20 failed to execute requested operation (MT20 shutdown)");
	SetErrorText(ERR_REPLACE_BURNER_SOON, "MT20 burner has exceeded 1000 hours of use and should be replaced soon.");
}

MT20HUB::~MT20HUB()
{
	Shutdown();
}

int MT20HUB::Initialize()
{
	busy_ = true;
	std::string init_ret = mt20.initialize();
	busy_ = false;
	if(init_ret.size() > 0)
	{
		LogMessage(init_ret, false);
		return ERR_UNABLE_TO_CONNECT;
	}
	initialized_ = true;
	return DEVICE_OK;
}

int MT20HUB::Shutdown()
{
	if(initialized_)
	{
		busy_ = true;
		std::string ret = mt20.shutdown();
		busy_ = false;
		if(ret.size() > 0)
		{
			char error_msg[4096];
			sprintf(error_msg, "MT20hub::shutdown() returns error closing MT20 connection during MT20HUB::Shutdown()\n");
			ret.append(error_msg);
			LogMessage(ret, false);
			return ERR_EXECUTING_CMD;
		}
		initialized_ = false;
	}
	return DEVICE_OK;
}

void MT20HUB::GetName(char* pszName) const
{
	CDeviceUtils::CopyLimitedString(pszName, g_MT20HUB);
}

bool MT20HUB::Busy()
{
	return busy_;
}

/****************************************************************************
		MT20 Burner
		Turn burner on and off
****************************************************************************/

MT20Burner::MT20Burner() :
	numStates_(2),
	busy_(false),
	initialized_(false),
	state_(0)
{
	InitializeDefaultErrorMessages();

	SetErrorText(ERR_INVALID_STATE, "Invalid MT20-Burner state requested");
	SetErrorText(ERR_EXECUTING_CMD, "MT20-Burner failed to execute requested operation");
	SetErrorText(ERR_SET_FAILED, "MT20-Burner failed to successfuly set the requested state");
	SetErrorText(ERR_REPLACE_BURNER_SOON, "MT20-Burner reports burner near end of life. Replace burner soon");
}

MT20Burner::~MT20Burner()
{
	Shutdown();
}

int MT20Burner::Initialize()
{
	if(initialized_) return DEVICE_OK;

	// set property list

	// Name
	int ret = CreateProperty(MM::g_Keyword_Name, g_MT20Burner, MM::String, true);
	if(ret != DEVICE_OK) return ret;

	// Description
	ret = CreateProperty(MM::g_Keyword_Description, "Olympus MT20 Burner", MM::String, true);
	if(ret != DEVICE_OK) return ret;

	std::string ret2 = mt20.GetBurnerHours(&hours_);
	if(ret2.size() > 0)
	{
		ret2.append(std::string("mt20.GetBurnerHours() returns error in MT20Burner::Initialize().\n"));
		LogMessage(ret2, false);
		return ERR_EXECUTING_CMD;
	}

	// Burner Hours
	std::ostringstream temp;
	temp<<hours_;
	CPropertyAction* pAct = new CPropertyAction(this, &MT20Burner::OnHours);
	ret = CreateProperty(g_BurnerHours, temp.str().c_str(), MM::Integer, true, pAct);
	if(ret != DEVICE_OK) return ret;

	// State
	pAct = new CPropertyAction (this, &MT20Burner::OnState);
	ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
	if(ret != DEVICE_OK) return ret;

	SetPositionLabel((long)0, "Off");
	SetPositionLabel((long)1, "On");

	ret = AddAllowedValue(MM::g_Keyword_State, "0");	// Off
	if(ret != DEVICE_OK) return ret;
	ret = AddAllowedValue(MM::g_Keyword_State, "1");	// On
	if(ret != DEVICE_OK) return ret;

	state_ = 0;

	busy_ = true;
	ret = UpdateStatus();
	busy_ = false;
	if(ret != DEVICE_OK) return ret;

	initialized_ = true;

	return DEVICE_OK;
}

int MT20Burner::Shutdown()
{
	if(initialized_) initialized_ = false;
	return DEVICE_OK;
}

void MT20Burner::GetName(char* pszName) const
{
	CDeviceUtils::CopyLimitedString(pszName, g_MT20Burner);
}

bool MT20Burner::Busy()
{
	return busy_;
}

int MT20Burner::OnHours(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	std::string ret;
	// char* ret_msg;

	if(eAct == MM::BeforeGet)
	{
		busy_ = true;
		ret = mt20.GetBurnerHours(&hours_);
		busy_ = false;
		if(ret.size() > 0)
		{
			ret.append("MT20hub::GetBurnerHours() returns error in MT20Burner::OnHours() before get.\n");
			LogMessage(ret, false);
			return ERR_EXECUTING_CMD;
		}
		pProp->Set(hours_);
	}

	if(eAct == MM::AfterSet)
	{
		return DEVICE_UNSUPPORTED_COMMAND;
	}

	return DEVICE_OK;
}

int MT20Burner::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	std::string ret;
	char ret_msg[4096];

	if(eAct == MM::BeforeGet)
	{
		busy_ = true;
		ret = mt20.GetBurnerStatus(&state_);
		busy_ = false;
		if(ret.size() > 0)
		{
			ret.append("MT20hub::GetBurnerStatus() returns error in MT20Burner::OnState() before get\n");
			LogMessage(ret, false);
			return ERR_EXECUTING_CMD;
		}
		pProp->Set(state_);
	}

	if(eAct == MM::AfterSet)
	{
		long pos;
		pProp->Get(pos);
		if(!(pos ==0 || pos == 1))
		{
			sprintf(ret_msg, "Invalid state passed to MT20Burner: %l\n", pos);
			LogMessage(ret_msg, false);
			pProp->Set(state_);
			return ERR_INVALID_STATE;
		}

		busy_ = true;
		ret = mt20.SetBurnerStatus(pos);
		busy_ = false;

		if(ret.size() > 0)
		{
			if(ret.find(std::string("Replace burner soon!")) != std::string::npos)
			{
				LogMessage("MT20 reports burner near end of life. Replace burner soon.", false);
				return ERR_REPLACE_BURNER_SOON;
			}
			else
			{
				ret.append("MT20hub::SetBurnerStatus() returns error in MT20Burner::OnState()\n");
				LogMessage(ret, false);
				return ERR_EXECUTING_CMD;
			}
		}

		busy_ = true;
		ret = mt20.GetBurnerStatus(&state_);
		busy_ = false;

		if(ret.size() > 0)
		{
			ret.append("MT20hub::GetBurnerStatus() returns error in MT20Burner::OnState() after set\n");
			LogMessage(ret, false);
			return ERR_EXECUTING_CMD;
		}
		if(state_ != pos)
		{
			sprintf(ret_msg, "Failed to successfully set MT20-Burner state to %l in MT20Burner::OnState(); current state is %l", pos, state_);
			LogMessage(ret_msg, false);
			pProp->Set(state_);
			return ERR_SET_FAILED;
		}
	}
	
	return DEVICE_OK;
}

/****************************************************************************
		MT20 Shutter
		Open and close shutter
***************************************************************************/

MT20Shutter::MT20Shutter() :
	state_(false),
	initialized_(false),
	busy_(false)
{
	InitializeDefaultErrorMessages();

	SetErrorText(ERR_INVALID_STATE, "Invalid MT20-Shutter state requested");
	SetErrorText(ERR_EXECUTING_CMD, "MT20-Shutter failed to execute requested operation");
	SetErrorText(ERR_SET_FAILED, "MT20-Shutter failed to successfuly set the requested state");
}

MT20Shutter::~MT20Shutter()
{
	Shutdown();
}

/////////////////////////////////////////////////////////
// MMDevice API
int MT20Shutter::Initialize()
{
	if(initialized_) return DEVICE_OK;

	// set property list

	// Name
	int ret = CreateProperty(MM::g_Keyword_Name, g_MT20Shutter, MM::String, true);
	if(ret != DEVICE_OK) return ret;

	// Description

	ret = CreateProperty(MM::g_Keyword_Description, "Olympus MT20 shutter", MM::String, true);
	if(ret != DEVICE_OK) return ret;

	// State
	CPropertyAction* pAct = new CPropertyAction(this, &MT20Shutter::OnState);
	ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
	if(ret != DEVICE_OK) return ret;

	AddAllowedValue(MM::g_Keyword_State, "0");	// Closed
	AddAllowedValue(MM::g_Keyword_State, "1");	// Open

	state_ = false;

	busy_ = true;
	ret = UpdateStatus();
	busy_ = false;
	if(ret != DEVICE_OK) return ret;

	initialized_ = true;

	return DEVICE_OK;
}

int MT20Shutter::Shutdown()
{
	if(initialized_) initialized_ = false;
	return DEVICE_OK;
}

void MT20Shutter::GetName(char* pszName) const
{
	CDeviceUtils::CopyLimitedString(pszName, g_MT20Shutter);
}

bool MT20Shutter::Busy()
{
	return busy_;
}

/////////////////////////////////////////////////////////
// shutter API
int MT20Shutter::SetOpen(bool open)
{
	if(open) return SetProperty(MM::g_Keyword_State, "1");

	else return SetProperty(MM::g_Keyword_State, "0");
}

int MT20Shutter::GetOpen(bool& open)
{
	char buf[MM::MaxStrLength];
	int ret = GetProperty(MM::g_Keyword_State, buf);
	if(ret != DEVICE_OK) return ret;

	long pos = atol(buf);
	pos == 0 ? open = false : open = true;

	return DEVICE_OK;
}

/////////////////////////////////////////////////////////
// action interface
int MT20Shutter::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	std::string ret;
	char ret_msg[4096];

	if(eAct == MM::BeforeGet)
	{
		long pos;
		busy_ = true;
		ret = mt20.GetShutterState(&pos);
		busy_ = false;
		if(ret.size() > 0)
		{
			sprintf(ret_msg, "MT20hub::GetShutterState() returns error during MT20Shutter::OnState before get\n");
			LogMessage(ret.append(std::string(ret_msg)), false);
			return ERR_EXECUTING_CMD;
		}
		if(pos == 0) state_ = false;
		else state_ = true;
		pProp->Set(pos);
	}

	if(eAct == MM::AfterSet)
	{
		long pos;
		pProp->Get(pos);
		if(pos > 1 || pos < 0)
		{
			sprintf(ret_msg, "Invalid state %l requested of MT20-Shutter in MT20Shutter::OnState() after set\n", pos);
			LogMessage(ret_msg, false);
			pProp->Set((long)(state_ ? 1 : 0));
			return ERR_INVALID_STATE;
		}

		busy_ = true;
		ret = mt20.SetShutterState(pos);
		busy_ = false;
		
		if(ret.size() > 0)
		{
			sprintf(ret_msg, "MT20hub::SetShutterState() returns error during MT20Shutter::OnState() after set");
			LogMessage(ret.append(std::string(ret_msg)), false);
			return ERR_EXECUTING_CMD;
		}
		
		busy_ = true;
		long state;
		ret = mt20.GetShutterState(&state);
		busy_ = false;
		
		if(ret.size() > 0)
		{
			sprintf(ret_msg, "MT20hub::GetShutterState() returns error during MT20Shutter::OnState() after set");
			LogMessage(ret.append(std::string(ret_msg)), false);
			return ERR_EXECUTING_CMD;
		}
		
		if(state == 0) state_ = false;
		else state_ = true;
		
		if(state != pos)
		{
			sprintf(ret_msg, "Failed to successfully set MT20-Shutter state to %l in MT20Burner::OnState(); current state is %l", pos, state);
			LogMessage(ret_msg, false);
			pProp->Set(state);
			return ERR_SET_FAILED;
		}
	}

	return DEVICE_OK;
}
	

/****************************************************************************
		MT20 Filterwheel
		Reposition filterwheel
****************************************************************************/

MT20Filterwheel::MT20Filterwheel() :
	numPos_(8),
	busy_(false),
	initialized_(false),
	position_(0)
{
	InitializeDefaultErrorMessages();
	
	SetErrorText(ERR_INVALID_POSITION, "Invalid MT20-Filterwheel position requested");
	SetErrorText(ERR_EXECUTING_CMD, "MT20-Filterwheel failed to execute requested operation");
	SetErrorText(ERR_SET_FAILED, "MT20-Filterwheel failed to successfuly set the requested position");
}

MT20Filterwheel::~MT20Filterwheel()
{
	Shutdown();
}

int MT20Filterwheel::Initialize()
{
	if(initialized_) return DEVICE_OK;

	// set property list

	// Name
	int ret = CreateProperty(MM::g_Keyword_Name, g_MT20Filterwheel, MM::String, true);
	if(ret != DEVICE_OK) return ret;

	// Description
	ret = CreateProperty(MM::g_Keyword_Description, "Olympus MT20 filterwheel", MM::String, true);
	if(ret != DEVICE_OK) return ret;

	// create default positions and labels
	const int bufSize = 1024;
	char buf[bufSize];
	for (unsigned long i=0; i < numPos_; ++i)
	{
		snprintf(buf, bufSize, "State-%ld", i);
		SetPositionLabel(i, buf);
		snprintf(buf, bufSize, "%ld", i);
		AddAllowedValue(MM::g_Keyword_Closed_Position, buf);
	}

	// State
	CPropertyAction* act = new CPropertyAction (this, &MT20Filterwheel::OnState);
	ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, act);
	if(ret != DEVICE_OK) return ret;

	// Label
	act = new CPropertyAction (this, &CStateBase::OnLabel);
	ret = CreateProperty(MM::g_Keyword_Label, "", MM::String, false, act);
	if(ret != DEVICE_OK) return ret;

	busy_ = true;
	ret = UpdateStatus();
	busy_ = false;

	if(ret != DEVICE_OK) return ret;

	initialized_ = true;

	return DEVICE_OK;
}

int MT20Filterwheel::Shutdown()
{
	if(initialized_) initialized_ = false;

	return DEVICE_OK;
}

void MT20Filterwheel::GetName(char* pszName) const
{
	CDeviceUtils::CopyLimitedString(pszName, g_MT20Filterwheel);
}

bool MT20Filterwheel::Busy()
{
	return busy_;
}

int MT20Filterwheel::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	std::string ret;
	char ret_msg[4096];

	if(eAct == MM::BeforeGet)
	{
		busy_ = true;
		ret = mt20.GetFilterwheelPosition(&position_);
		busy_ = false;

		if(ret.size() > 0)
		{
			sprintf(ret_msg, "MT20hub::GetFilterwheelPosition() returns error in MT20Filterwheel::OnState() before get\n");
			LogMessage(ret.append(std::string(ret_msg)), false);
			return ERR_EXECUTING_CMD;
		}
		pProp->Set(position_);
	}

	if(eAct == MM::AfterSet)
	{
		long pos;
		pProp->Get(pos);
		if(pos >= (long) numPos_ || pos < 0)
		{
			sprintf(ret_msg, "Invalid position %l requested of MT20-Filterwheel in MT20Filterwheel::OnState() after set\n", pos);
			LogMessage(ret_msg, false);
			pProp->Set(position_);
			return ERR_INVALID_POSITION;
		}

		busy_ = true;
		ret = mt20.SetFilterwheelPosition(pos);
		busy_ = false;

		if(ret.size() > 0)
		{
			sprintf(ret_msg, "MT20hub::SetFilterwheelPosition() returns error in MT20Filterwheel::OnState() after set\n");
			LogMessage(ret.append(std::string(ret_msg)), false);
			return ERR_EXECUTING_CMD;
		}

		busy_ = true;
		ret = mt20.GetFilterwheelPosition(&position_);
		busy_ = false;

		if(ret.size() > 0)
		{
			sprintf(ret_msg, "MT20hub::GetFilterwheelPosition() returns error in MT20Filterwheel::OnState() after set\n");
			LogMessage(ret.append(std::string(ret_msg)), false);
			return ERR_EXECUTING_CMD;
		}
		if(position_ != pos)
		{
			sprintf(ret_msg, "Failed to successfully set MT20-Filterwheel position to %l in MT20Filterwheel::OnState(); current position is %l", pos, position_);
			LogMessage(ret_msg, false);
			pProp->Set(position_);
			return ERR_SET_FAILED;
		}
	}

	return DEVICE_OK;
}


/****************************************************************************
		MT20 Attenuator
		Set attenuator
****************************************************************************/

MT20Attenuator::MT20Attenuator() :
	numPos_(14),
	busy_(false),
	initialized_(false),
	position_(0)
{
	InitializeDefaultErrorMessages();
	
	SetErrorText(ERR_INVALID_STATE, "Invalid MT20-Attenuator state requested");
	SetErrorText(ERR_EXECUTING_CMD, "MT20-Attenuator failed to execute requested operation");
	SetErrorText(ERR_SET_FAILED, "MT20-Attenuator failed to successfuly set the requested position");
}

MT20Attenuator::~MT20Attenuator()
{
	Shutdown();
}

int MT20Attenuator::Initialize()
{
	if(initialized_) return DEVICE_OK;

	// set property list

	// Name
	int ret = CreateProperty(MM::g_Keyword_Name, g_MT20Attenuator, MM::String, true);
	if(ret != DEVICE_OK) return ret;

	// Description
	ret = CreateProperty(MM::g_Keyword_Description, "Olympus MT20 attenuator", MM::String, true);
	if(ret != DEVICE_OK) return ret;

	// create default positions and labels
	const int bufSize = 1024;
	char buf[bufSize];
	for (unsigned long i=0; i < numPos_; ++i)
	{
		snprintf(buf, bufSize, "State-%ld", i);
		SetPositionLabel(i, buf);
		snprintf(buf, bufSize, "%ld", i);
		AddAllowedValue(MM::g_Keyword_Closed_Position, buf);
	}

	// State
	CPropertyAction* act = new CPropertyAction (this, &MT20Attenuator::OnState);
	ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, act);
	if(ret != DEVICE_OK) return ret;

	// Label
	act = new CPropertyAction (this, &CStateBase::OnLabel);
	ret = CreateProperty(MM::g_Keyword_Label, "", MM::String, false, act);
	if(ret != DEVICE_OK) return ret;

	busy_ = true;
	ret = UpdateStatus();
	busy_ = false;

	if(ret != DEVICE_OK) return ret;

	initialized_ = true;

	return DEVICE_OK;
}

int MT20Attenuator::Shutdown()
{
	if(initialized_) initialized_ = false;

	return DEVICE_OK;
}

void MT20Attenuator::GetName(char* pszName) const
{
	CDeviceUtils::CopyLimitedString(pszName, g_MT20Attenuator);
}

bool MT20Attenuator::Busy()
{
	return busy_;
}

int MT20Attenuator::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	std::string ret;
	char ret_msg[4096];
	
	if(eAct == MM::BeforeGet)
	{
		busy_ = true;
		ret = mt20.GetAttenuatorState(&position_);
		busy_ = false;
		if(ret.size() > 0)
		{
			sprintf(ret_msg, "MT20hub::GetAttenuatorState() returns error in MT20Attenuator::OnState() before get\n");
			LogMessage(ret.append(std::string(ret_msg)), false);
			return ERR_EXECUTING_CMD;
		}
		pProp->Set(position_);
	}

	if(eAct == MM::AfterSet)
	{
		long pos;
		pProp->Get(pos);
		if(pos >= (long) numPos_ || pos < 0)
		{
			sprintf(ret_msg, "Invalid state %l requested of MT20-Attenuator in MT20Attenuator::OnState() after set\n", pos);
			LogMessage(ret_msg, false);
			pProp->Set(position_);
			return ERR_INVALID_STATE;
		}

		busy_ = true;
		ret = mt20.SetAttenuatorState(pos);
		busy_ = false;

		if(ret.size() > 0)
		{
			sprintf(ret_msg, "MT20hub::SetAttenuatorState() returns error in MT20Attenuator::OnState() after set\n");
			LogMessage(ret.append(std::string(ret_msg)), false);
			return ERR_EXECUTING_CMD;
		}

		busy_ = true;
		ret = mt20.GetAttenuatorState(&position_);
		busy_ = false;

		if(ret.size() > 0)
		{
			sprintf(ret_msg, "MT20hub::GetAttenuatorState() returns error in MT20Attenuator::OnState() after set\n");
			LogMessage(ret.append(std::string(ret_msg)), false);
			return ERR_EXECUTING_CMD;
		}
		
		if(position_ != pos)
		{
			sprintf(ret_msg, "Failed to successfully set MT20-Attenuator state to %l in MT20Attenuator::OnState(); current state is %l", pos, position_);
			LogMessage(ret_msg, false);
			pProp->Set(position_);
			return ERR_SET_FAILED;
		}
	}

	return DEVICE_OK;
}