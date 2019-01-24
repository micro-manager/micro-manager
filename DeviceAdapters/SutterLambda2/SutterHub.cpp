///////////////////////////////////////////////////////////////////////////////
// FILE:          SutterHub.cpp
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


extern const char* g_HubName;
extern const char* g_WheelAName;
extern const char* g_WheelBName;
extern const char* g_WheelCName;
extern const char* g_ShutterAName;
extern const char* g_ShutterBName;
extern const char* g_LambdaVF5Name;

//Based heavily on the Arduino Hub.

SutterHub::SutterHub(const char* name): busy_(false), initialized_(false), name_(name)
{
	InitializeDefaultErrorMessages();
	SetErrorText(DEVICE_SERIAL_TIMEOUT, "Serial port timed out without receiving a response.");

	CPropertyAction* pAct = new CPropertyAction(this, &SutterHub::OnPort);
	CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);
	

	/*
	// Answertimeout
	pAct = new CPropertyAction(this, &SutterHub::OnAnswerTimeout);
	CreateProperty("Timeout(ms)", "500", MM::Integer, false, pAct, true);
	*/
	//Motors Enabled
	pAct = new CPropertyAction(this, &SutterHub::OnMotorsEnabled);
	CreateProperty("Motors Enabled", "True", MM::String, false, pAct, false);
	AddAllowedValue("Motors Enabled", "True");
	AddAllowedValue("Motors Enabled", "False");

	// Name
	CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);

	// Description
	CreateProperty(MM::g_Keyword_Description, "Sutter Lambda Controller", MM::String, true);
}

SutterHub::~SutterHub()
{
	Shutdown();
}

int SutterHub::Initialize() {

	MMThreadGuard myLock(GetLock());	//We are creating an object named MyLock. the constructor locks access to lock_. when myLock is destroyed it is released.
	PurgeComPort(port_.c_str());
	int ret = GoOnline(); //Check that we're connected
	if (ret != DEVICE_OK) { return ret; }

	initialized_ = true;
	return DEVICE_OK;
}

int SutterHub::Shutdown() {
	initialized_ = false;
	return DEVICE_OK;
}

void SutterHub::GetName(char* name) const
{
	assert(name_.length() < CDeviceUtils::GetMaxStringLength());
	CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

MM::DeviceDetectionStatus SutterHub::DetectDevice() {
	if (initialized_) {
		return MM::CanCommunicate;
	}
	else {
		MM::DeviceDetectionStatus result = MM::Misconfigured;
		try {
			// convert into lower case to detect invalid port names:
			std::string test = port_;
			for (std::string::iterator its = test.begin(); its != test.end(); ++its) {
				*its = (char)tolower(*its);
			}
			// ensure we have been provided with a valid serial port device name
			if (0 < test.length() && 0 != test.compare("undefined") && 0 != test.compare("unknown"))
			{
				// the port property seems correct, so give it a try
				result = MM::CanNotCommunicate;
				// device specific default communication parameters
				GetCoreCallback()->SetSerialProperties(port_.c_str(),
														  "50.0",
														  "128000",
														  "0.0",
														  "Off",
														  "None",
														  "1");

				MM::Device* pS = GetCoreCallback()->GetDevice(this, port_.c_str()); //No idea what this does. Taken from shutter class
				if (DEVICE_OK == pS->Initialize())
				{
					int status = GoOnline();
					if (DEVICE_OK == status) {result = MM::CanCommunicate;}
					pS->Shutdown();
				}
				// but for operation, we'll need a longer timeout
				GetCoreCallback()->SetDeviceProperty(port_.c_str(), "AnswerTimeout", "2000.0");
			}
		}
		catch (...)
		{
			LogMessage("Exception in SutterHub DetectDevice");
		}
		return result;
	}
}

int SutterHub::DetectInstalledDevices() {
	if (DetectDevice() == MM::CanCommunicate) {
		std::vector<std::string> peripherals;
		peripherals.clear();
		peripherals.push_back(g_WheelAName);
		peripherals.push_back(g_WheelBName);
		peripherals.push_back(g_WheelCName);
		peripherals.push_back(g_ShutterAName);
		peripherals.push_back(g_ShutterBName);
		peripherals.push_back(g_LambdaVF5Name);
		for (size_t i=0; i<peripherals.size(); i++) {
			MM::Device* pDev = CreateDevice(peripherals[i].c_str());
			if (pDev) {
				AddInstalledDevice(pDev);
			}
		}
	}
	return DEVICE_OK;
}

bool SutterHub::Busy() {
	return busy_;
}

int SutterHub::OnPort(MM::PropertyBase* pProp, MM::ActionType pAct) {
	if (pAct == MM::BeforeGet) {
		pProp->Set(port_.c_str());
	}
	else if (pAct == MM::AfterSet) {
		pProp->Get(port_);
	}
	return DEVICE_OK;
}

int SutterHub::GoOnline() {
	// Transfer to On Line
	std::vector<unsigned char> cmd;
	cmd.push_back(238); //0xEE
	return SetCommand(cmd);
}

int SutterHub::GetControllerType(std::string& type, std::string& id) {
	PurgeComPort(port_.c_str());
	int ret = DEVICE_OK;
	std::vector<unsigned char> ans;
	std::vector<unsigned char> emptyv;
	std::vector<unsigned char> command;
	command.push_back((unsigned char)253); //0xFD

	ret = SetCommand(command, emptyv, ans);
	if (ret != DEVICE_OK) {return ret;}

	std::string ans2(ans.begin(), ans.end());

	if (ret != DEVICE_OK) {
		std::ostringstream errOss;
		errOss << "Could not get answer from 253 command (GetSerialAnswer returned " << ret << "). Assuming a 10-2";
		LogMessage(errOss.str().c_str(), true);
		type = "10-2";
		id = "10-2";
	}
	else if (ans2.length() == 0) {
		LogMessage("Answer from 253 command was empty. Assuming a 10-2", true);
		type = "10-2";
		id = "10-2";
	}
	else {
		if (ans2.substr(0, 2) == "SC") {
			type = "SC";
		}
		else if (ans2.substr(0, 4) == "10-3") {
			type = "10-3";
		}
		id = ans2.substr(0, ans2.length() - 2);
		LogMessage(("Controller type is " + std::string(type)).c_str(), true);
	}
	return DEVICE_OK;
}

/**
* Queries the controller for its status
* Meaning of status depends on controller type
* status should be allocated by the caller and at least be 21 bytes long
* status will be the answer returned by the controller, stripped from the first byte (which echos the command)
*/
int SutterHub::GetStatus(std::vector<unsigned char>& status) {
	std::vector<unsigned char> msg;
	msg.push_back(204);	//0xCC
	// send command
	int ret = SetCommand(msg,msg,status);
	return ret;
}

int SutterHub::OnMotorsEnabled(MM::PropertyBase* pProp, MM::ActionType eAct) {
	if (eAct == MM::AfterSet) {
		std::string mEnabled;
		pProp->Get(mEnabled);
		std::vector<unsigned char> cmd;
		std::vector<unsigned char> response;
		unsigned char val = 0xCE;
		if (mEnabled=="True") {
			val |= 0x01;
			mEnabled_ = true;
		} else {
			mEnabled_ = false;
		}
		cmd.push_back(val);
		int ret = SetCommand(cmd);
		if (ret!=DEVICE_OK){return ret;}
	}
	else if (eAct == MM::BeforeGet){
		std::string enabled;
		enabled = mEnabled_? "True" : "False";
		pProp->Set(enabled.c_str());
	}
	return DEVICE_OK;
}

// lock the port for access,
// write 1, 2, or 3 char. command to equipment
// ensure the command completed by waiting for \r
// pass response back in argument
int SutterHub::SetCommand(const std::vector<unsigned char> command, const std::vector<unsigned char> alternateEcho, std::vector<unsigned char>& Response) {
	busy_ = true;
	MMThreadGuard myLock(GetLock());
	PurgeComPort(port_.c_str());
	// start time of entire transaction
	MM::MMTime commandStartTime = GetCurrentMMTime();
	// write command to the port
	int ret = WriteToComPort(port_.c_str(), &command[0], (unsigned long)command.size());
	if (ret != DEVICE_OK) {return ret;}

	// now ensure that command is echoed from controller
	MM::MMTime responseStartTime = GetCurrentMMTime();
	char timeoutChar[1024];
	ret = GetCoreCallback()->GetDeviceProperty(port_.c_str(), "AnswerTimeout", timeoutChar);
	if (ret != DEVICE_OK) {return ret;}
	long timeout = atoi(timeoutChar) * 1000; //Microseconds
	int read = 0;
	unsigned char response[1024];
	while (true) {
		unsigned long readThisTime;
		unsigned char tempResponse[1024];
		ret = ReadFromComPort(port_.c_str(), tempResponse, 1, readThisTime);
		if (ret != DEVICE_OK) {return ret;}
		for (int i=0; i<readThisTime; i++) {
			response[read+i] = tempResponse[i];
		}
		read += readThisTime;
		if (read >= command.size()){
			break;
		}
		MM::MMTime delta = GetCurrentMMTime() - responseStartTime;
		if (timeout < delta.getUsec()) {
			return DEVICE_SERIAL_TIMEOUT;
		}
	}
	for (int i=0; i<command.size(); i++) {
		if (response[i] == command.at(i)) { //We have a match so far.
			// int a = 1; //This is just here so there's something to debug.
		}
		else if (response[i] == alternateEcho.at(i)) {
			LogMessage(("command " + CDeviceUtils::HexRep(command) +
				" was echoed as alternate "), true);
		}
		else {
			//We got an unexpected response
			std::ostringstream bufff;
			bufff.flags(std::ios::hex | std::ios::showbase);
			bufff << (unsigned int)response[i];
			LogMessage((std::string("unexpected response: ") + bufff.str()).c_str(), false);
			return DEVICE_ERR;
		}
		continue;
	} // the command was echoed  entirely...
	read = 0;
	bool commandTerminated=false;
	while (true) {
		unsigned long readThisTime;
		unsigned char tempResponse[1024];
		ret = ReadFromComPort(port_.c_str(), tempResponse, 100, readThisTime);
		if (ret != DEVICE_OK) {return ret;}
		for (int i=0; i<readThisTime; i++) {
			if (tempResponse[i] == '\r') {
				commandTerminated = true;
				break;
			}
			response[read] = tempResponse[i];
			read++;
		}
		if (commandTerminated) {
			break;
		}
		MM::MMTime delta = GetCurrentMMTime() - responseStartTime;
		if (timeout < delta.getUsec()) {
			return DEVICE_SERIAL_TIMEOUT;
		}
	}
	for (int i=0; i<read; i++) {
		Response.push_back(response[i]);
	}
	busy_ = false;
	return DEVICE_OK;
}

int SutterHub::SetCommand(const std::vector<unsigned char> command, const std::vector<unsigned char> altEcho){
	std::vector<unsigned char> response;
	return SetCommand(command, altEcho, response);
}

int SutterHub::SetCommand(const std::vector<unsigned char> command) {
	return SetCommand(command,command);
}
