///////////////////////////////////////////////////////////////////////////////
// FILE:          pE4000.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   pE-4000 light source controller adapter
// COPYRIGHT:     CoolLED Ltd, UK, 2017
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
// AUTHOR:        Jinting Guo, jinting.guo@coolled.com, 12/07/2017

#ifdef WIN32
#include <windows.h>
#define snprintf _snprintf 
#endif


#include "../../MMDevice/MMDevice.h"
#include "pE4000.h"
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include "../../MMDevice/DeviceUtils.h"
#include <sstream>

// Controller
const char* g_ControllerName = "pE4000";
const char* g_Keyword_Intensity = "Intensity";
const char* g_Keyword_Selection = "Selection";
const char* g_Keyword_Global_State = "Global State";
const char* g_Keyword_ChannelLabel = "Channel";
const char* g_Keyword_PodLock = "Lock Pod";
const char * carriage_return = "\r";
const char * line_feed = "\n";
const long wavelengthLabels_[] = {
	365, 385, 405, 435,
	460, 470, 490, 500,
	525, 550, 580, 595,
	635, 660, 740, 770,
};
// static lock
MMThreadLock Controller::lock_;



///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
	RegisterDevice(g_ControllerName, MM::ShutterDevice, "pE4000 LED illuminator");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
	if (deviceName == 0)
		return 0;

	if (strcmp(deviceName, g_ControllerName) == 0)
	{
		// create Controller
		Controller* pController = new Controller(g_ControllerName);
		return pController;
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

Controller::Controller(const char* name) :
	initialized_(false),
	name_(name),
	busy_(false),
	error_(0),
	changedTime_(0.0),
	mThread_(0)
{
	assert(strlen(name) < (unsigned int)MM::MaxStrLength);

	InitializeDefaultErrorMessages();

	// create pre-initialization properties
	// ------------------------------------

	// Name
	CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);

	// Port
	CPropertyAction* pAct = new CPropertyAction(this, &Controller::OnPort);
	CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

	EnableDelay(); // signals that the delay setting will be used
	UpdateStatus();
}

Controller::~Controller()
{
	Shutdown();
}

bool Controller::Busy()
{
	MM::MMTime interval = GetCurrentMMTime() - changedTime_;
	MM::MMTime delay(GetDelayMs()*1000.0);
	if (interval < delay)
		return true;
	else
		return false;
}

void Controller::GetName(char* name) const
{
	assert(name_.length() < CDeviceUtils::GetMaxStringLength());
	CDeviceUtils::CopyLimitedString(name, name_.c_str());
}


int Controller::Initialize()
{
	string strError;

	ReadGreeting();

	Purge();
	Send("XMODEL");
	ReceiveOneLine();//Mainboard version returned

	int rlt = (int)buf_string_.find("pE-4000");
	if (rlt < 0) {
		strError = "\r\n\r\nIncompatible device driver!\r\n";
		strError += "The driver loaded: CoolLED-pE4000\r\n";
		strError += "The device found: ";
		strError += buf_string_.substr(7);
		strError += "\r\n\r\n";
		strError += "Internal Error Reference: ";
		SetErrorText(DEVICE_NOT_SUPPORTED, strError.c_str());
		return DEVICE_NOT_SUPPORTED;
	}

	for (int i = 0; i < 4; i++) {
		channelIntensities_[i] = 0;
		channelSelection_[i] = 0;
		channelWave_[i] = "";
		intensityUpdated_[i] = false;
		selectionUpdated_[i] = false;
		waveUpdated_[i] = false;
	}

	GenerateDescription();
	GenerateChannelState();
	GenerateChannelSelector();
	GeneratePropertyIntensity();
	GeneratePropertyState();
	GeneratePropertyLockPod();

	GetUpdate();

	mThread_ = new PollingThread(*this);
	mThread_->Start();

	initialized_ = true;
	return HandleErrors();

}

void Controller::ReadGreeting()
{
	MMThreadGuard myLock(lock_);
	ReceiveOneLine();
	ReceiveOneLine();
}

void Controller::GetUpdate()
{
	MMThreadGuard myLock(lock_);
	{
		string propName;

		Purge();
		Send("CSS?");
		do {
			ReceiveOneLine();
		} while (0 != buf_string_.compare(0, 3, "CSS", 0, 3));

		globalState_ = false;

		//Record intensities and first LED on
		for (unsigned int i = 0; i < 4; i++) {
			//Read the intensity
			channelIntensities_[i] = atol(buf_string_.substr(6 + i * 6, 3).c_str());
			string t = buf_string_.substr(4 + i * 6, 1);
			channelSelection_[i] = buf_string_.substr(4 + i * 6, 1) == "S" ? 1 : 0;
			propName = g_Keyword_Intensity;
			propName.push_back('A' + (char)i);
			intensityUpdated_[i] = true;;
			UpdateProperty(propName.c_str());

			propName = g_Keyword_Selection;
			propName.push_back('A' + (char)i);
			selectionUpdated_[i] = true;
			UpdateProperty(propName.c_str());

			globalState_ |= buf_string_.substr(5 + i * 6, 1) == "N";
			globalStateUpdated_ = true;
		}
		Purge();
		Send("LAMS");
		for (int i = 0; i < 4; i++) {
			ReceiveOneLine();
			if (buf_string_.substr(0, 3).compare("LAM") == 0) {
				channelWave_[i] = buf_string_.substr(6);
				propName = g_Keyword_ChannelLabel;
				propName.push_back('A' + (char)i);
				waveUpdated_[i] = true;
				UpdateProperty(propName.c_str());
			}
		}
	}
}

/////////////////////////////////////////////
// Property Generators
/////////////////////////////////////////////
void Controller::GeneratePropertyLockPod()
{
	CPropertyAction* pAct = new CPropertyAction(this, &Controller::OnLockPod);
	CreateProperty(g_Keyword_PodLock, "0", MM::Integer, false, pAct);
	AddAllowedValue(g_Keyword_PodLock, "0");
	AddAllowedValue(g_Keyword_PodLock, "1");

	MMThreadGuard myLock(lock_);
	Purge();
	Send("PORT:P=ON");
	ReceiveOneLine();
}

void Controller::GeneratePropertyState()
{
	CPropertyAction* pAct = new CPropertyAction(this, &Controller::OnState);
	CreateProperty(g_Keyword_Global_State, "0", MM::Integer, false, pAct);
	AddAllowedValue(g_Keyword_Global_State, "0");
	AddAllowedValue(g_Keyword_Global_State, "1");
}

void Controller::GenerateChannelSelector()
{
	CPropertyActionEx* pAct;
	char buf[16];

	for (int channel = 0; channel < 4; channel++)
	{
		string channelName = g_Keyword_ChannelLabel;
		channelName.push_back('A' + (char)channel);
		pAct = new CPropertyActionEx(this, &Controller::OnChannelWave, long(channel));
		snprintf(buf, 16, "%d", wavelengthLabels_[channel * 4]);
		CreateProperty(channelName.c_str(), buf, MM::Integer, false, pAct);

		vector<string> channelWavelengths;
		for (int wavelength = 0; wavelength < 4; wavelength++)
		{
			snprintf(buf, 16, "%d", wavelengthLabels_[wavelength + channel * 4]);
			channelWavelengths.push_back(buf);
			AddAllowedValue(channelName.c_str(), buf);
		}
		SetAllowedValues(channelName.c_str(), channelWavelengths);
	}
}

void Controller::GenerateDescription()
{
	string str = "CoolLED pE4000.";

	Purge();
	Send("XVER");
	ReceiveOneLine();//Mainboard version returned
	str += " Mainboard: v" + buf_string_.substr(8);
	ReceiveOneLine();//Skip hardware version
	ReceiveOneLine();//Skip data version
	ReceiveOneLine();//Pod version returned
	str += " Pod: v" + buf_string_.substr(8);
	ReceiveOneLine();//Backend A version returned
	str += " Backend: v" + buf_string_.substr(10);
	ReceiveOneLine();//Skip Backend B version
	ReceiveOneLine();//Skip Backend C version
	ReceiveOneLine();//Skip Backend D version

	CreateProperty(MM::g_Keyword_Description, str.c_str(), MM::String, true);
}

void Controller::GenerateChannelState()
{
	string selectionName;
	CPropertyActionEx* pAct;
	for (int channel = 0; channel < 4; channel++)
	{
		pAct = new CPropertyActionEx(this, &Controller::OnChannelState, long(channel));
		selectionName = g_Keyword_Selection;
		selectionName.push_back('A' + (char)channel);
		CreateProperty(selectionName.c_str(), "0", MM::Integer, false, pAct);
		AddAllowedValue(selectionName.c_str(), "0");
		AddAllowedValue(selectionName.c_str(), "1");
	}
}

void Controller::GeneratePropertyIntensity()
{
	string intensityName;
	CPropertyActionEx* pAct;
	for (unsigned i = 0; i < 4; i++)
	{
		pAct = new CPropertyActionEx(this, &Controller::OnIntensity, i);
		intensityName = g_Keyword_Intensity;
		intensityName.push_back('A' + (char)i);
		CreateProperty(intensityName.c_str(), "0", MM::Integer, false, pAct);
		SetPropertyLimits(intensityName.c_str(), 0, 100);
	}
}

int Controller::Shutdown()
{
	if (initialized_)
	{
		initialized_ = false;
		delete(mThread_);
	}
	return HandleErrors();
}

///////////////////////////////////////////////////////////////////////////////
// String utilities
///////////////////////////////////////////////////////////////////////////////


void Controller::StripString(string& StringToModify)
{
	if (StringToModify.empty()) return;

	const char* spaces = " \f\n\r\t\v";
	size_t startIndex = StringToModify.find_first_not_of(spaces);
	size_t endIndex = StringToModify.find_last_not_of(spaces);
	string tempString = StringToModify;
	StringToModify.erase();

	StringToModify = tempString.substr(startIndex, (endIndex - startIndex + 1));
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////


int Controller::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int Controller::OnIntensity(MM::PropertyBase* pProp, MM::ActionType eAct, long index)
{
	long intensity;
	if (eAct == MM::BeforeGet && intensityUpdated_[index]) {
		pProp->Set(channelIntensities_[index]);
		intensityUpdated_[index] = false;
	}
	else if (eAct == MM::AfterSet) {
		pProp->Get(intensity);
		SetIntensity(intensity, index);
	}

	return HandleErrors();
}

int Controller::OnChannelWave(MM::PropertyBase* pProp, MM::ActionType eAct, long channel)
{
	string wavelength;

	if (eAct == MM::BeforeGet && waveUpdated_[channel]) {
		pProp->Set(channelWave_[channel].c_str());
		waveUpdated_[channel] = false;
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(wavelength);
		//LOAD:<wavelength> loads that particular wavelength in the channel
		stringstream msg;
		msg << "LOAD:" << wavelength;

		MMThreadGuard myLock(lock_);
		Purge();
		Send(msg.str());
		do {
			ReceiveOneLine();
		} while (buf_string_.size() == 0);
	}

	return HandleErrors();
}

int Controller::OnChannelState(MM::PropertyBase* pProp, MM::ActionType eAct, long channel)
{
	long channelState;

	if (eAct == MM::BeforeGet && selectionUpdated_[channel]) {
		pProp->Set(channelSelection_[channel]);
		selectionUpdated_[channel] = false;
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(channelState);

		stringstream msg;
		msg << "C" << string(1, 'A' + (char)channel) << (channelState == 1 ? "S" : "X");
		MMThreadGuard myLock(lock_);
		Purge();
		Send(msg.str());
	}
	return HandleErrors();
}

int Controller::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet && globalStateUpdated_) {
		pProp->Set(globalState_ ? (long)1 : (long)0);
		globalStateUpdated_ = false;
	}
	else if (eAct == MM::AfterSet)
	{
		long state;

		pProp->Get(state);
		globalState_ = state == 1;

		MMThreadGuard myLock(lock_);
		Purge();
		Send(state == 1 ? "CSN" : "CSF");
		ReceiveOneLine();
	}

	return HandleErrors();
}

int Controller::OnLockPod(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::AfterSet)
	{
		long lockPod;

		pProp->Get(lockPod);

		MMThreadGuard myLock(lock_);
		Purge();
		Send(lockPod == 1 ? "PORT:P=OFF" : "PORT:P=ON");
		ReceiveOneLine();
	}

	return HandleErrors();
}

///////////////////////////////////////////////////////////////////////////////
// Utility methods
///////////////////////////////////////////////////////////////////////////////

void Controller::SetIntensity(long intensity, long index)
{
	stringstream msg;
	msg << "C" << string(1, 'A' + (char)index) << "I" << intensity;

	{
		MMThreadGuard myLock(lock_);
		Purge();
		Send(msg.str());
		ReceiveOneLine();
	}
}

void Controller::GetIntensity(long& intensity, long index)
{
	stringstream msg;
	string ans;
	msg << "C" << string(1, 'A' + (char)index) << "?";

	{
		MMThreadGuard myLock(lock_);
		Purge();
		Send(msg.str());
		ReceiveOneLine();
	}

	if (!buf_string_.empty())
		if (0 == buf_string_.compare(0, 2, msg.str(), 0, 2))
		{
			intensity = atol(buf_string_.substr(2, 3).c_str());
		}

}

int Controller::HandleErrors()
{
	int lastError = error_;
	error_ = 0;
	return lastError;
}



/////////////////////////////////////
//  Communications
/////////////////////////////////////


void Controller::Send(string cmd)
{
	int ret = SendSerialCommand(port_.c_str(), cmd.c_str(), carriage_return);
	if (ret != DEVICE_OK)
		error_ = DEVICE_SERIAL_COMMAND_FAILED;
}


void Controller::ReceiveOneLine()
{
	buf_string_ = "";
	GetSerialAnswer(port_.c_str(), line_feed, buf_string_);
}

void Controller::Purge()
{
	int ret = PurgeComPort(port_.c_str());
	if (ret != 0)
		error_ = DEVICE_SERIAL_COMMAND_FAILED;
}

//********************
// Shutter API
//********************

int Controller::SetOpen(bool open)
{
	MMThreadGuard myLock(lock_);
	SetProperty(g_Keyword_Global_State, open ? "1" : "0");
	return HandleErrors();
}

int Controller::GetOpen(bool& open)
{
	long state;
	GetProperty(g_Keyword_Global_State, state);

	if (state == 1)
		open = true;
	else if (state == 0)
		open = false;
	else
		error_ = DEVICE_UNKNOWN_POSITION;

	return HandleErrors();
}

int Controller::Fire(double deltaT)
{
	deltaT = 0; // Suppress warning
	error_ = DEVICE_UNSUPPORTED_COMMAND;
	return HandleErrors();
}

PollingThread::PollingThread(Controller& aController) :
	state_(0),
	aController_(aController)
{
}

PollingThread::~PollingThread()
{
	Stop();
	wait();
}

int PollingThread::svc()
{
	while (!stop_)
	{
		aController_.GetUpdate();
		CDeviceUtils::SleepMs(100);
	}
	return DEVICE_OK;
}


void PollingThread::Start()
{
	stop_ = false;
	activate();
}

