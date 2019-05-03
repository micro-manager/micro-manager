//////////////////////////////////////////////////////////////////////////////
// FILE:          ServoZ.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Adapter for Arduino (Teensy) controlled servo motor z focus drive
// COPYRIGHT:     Regents of the University of California
// LICENSE:       LGPL
//
// AUTHOR:        Henry Pinkard, hbp@berkeley.edu, 12/13/2016
// AUTHOR:        Zack Phillips, zkphil@berkeley.edu, 3/1/2019
//
//////////////////////////////////////////////////////////////////////////////

#include "ServoZ.h"
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>
#include <cstdio>
#include <cstring>
#include <string>
#include <algorithm>
#include <math.h>

#ifdef WIN32
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#define snprintf _snprintf 
#endif


///////////////////////////////////////////////////////////////////////////////
// ServoZ implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~~

ServoZ::ServoZ() : initialized_(false), name_(g_Keyword_DeviceName), positionSteps_(0), acceleration_(1.0), speed_(1.0), umPerStep_(1.0)
{
	portAvailable_ = false;

	// Initialize default error messages
	InitializeDefaultErrorMessages();

	////pre initialization property: port name
	CPropertyAction* pAct = new CPropertyAction(this, &ServoZ::OnPort);
	CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);
}


///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
	RegisterDevice(g_Keyword_DeviceName, MM::StageDevice, "Servo Z drive");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
	if (deviceName == 0)
		return 0;

	if (strcmp(deviceName, g_Keyword_DeviceName) == 0)
	{
		return new ServoZ;
	}
	return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
	delete pDevice;
}


int ServoZ::Initialize()
{
	if (initialized_)
		return DEVICE_OK;

	// Name
	int ret = CreateProperty(MM::g_Keyword_Name, g_Keyword_DeviceName, MM::String, false);
	if (DEVICE_OK != ret)
		return ret;

	// Description
	ret = CreateProperty(MM::g_Keyword_Description, "Z drive implemented with Teensy (Arduino) atached to servo motor", MM::String, false);
	assert(DEVICE_OK == ret);

	// Most Recent Serial Response
	ret = CreateProperty(g_Keyword_Response, "", MM::String, false);
	assert(DEVICE_OK == ret);

	// Reset
	CPropertyAction* pActreset = new CPropertyAction(this, &ServoZ::OnReset);
	ret = CreateProperty(g_Keyword_Reset, "0", MM::String, false, pActreset);
	assert(DEVICE_OK == ret);
	AddAllowedValue(g_Keyword_Reset, "0");
	AddAllowedValue(g_Keyword_Reset, "1");

	// Manual Command Interface
	CPropertyAction* pCommand = new CPropertyAction(this, &ServoZ::OnCommand);
	ret = CreateProperty(g_Keyword_Command, "", MM::String, false, pCommand);
	assert(DEVICE_OK == ret);

	CPropertyAction* pSpeed = new CPropertyAction(this, &ServoZ::OnSpeed);
	ret = CreateProperty(g_Keyword_Speed, "1", MM::Integer, false, pSpeed);
	assert(DEVICE_OK == ret);

	CPropertyAction* pAcceleration = new CPropertyAction(this, &ServoZ::OnAcceleration);
	ret = CreateProperty(g_Keyword_Acceleration, "1", MM::Integer, false, pAcceleration);
	assert(DEVICE_OK == ret);
	
	CPropertyAction* pCal = new CPropertyAction(this, &ServoZ::OnCalibration);
	ret = CreateProperty(g_Keyword_Calibration, "1.0", MM::Float, false, pCal);
	assert(DEVICE_OK == ret);


	// Check that we have a controller:
	PurgeComPort(port_.c_str());

	// Set the device to machine-readable mode
	SetMachineMode(true);

	// Sync Current Parameters
	SyncState();

	Reset();

	ret = UpdateStatus();
	if (ret != DEVICE_OK)
		return ret;
	initialized_ = true;

	return DEVICE_OK;
}

int ServoZ::SendCommand(const char * command, bool get_response)
{
	// Purge COM port
	PurgeComPort(port_.c_str());

	// Convert command to std::string
	std::string _command(command);

	// Send command to device
	_command += "\n";
	WriteToComPort(port_.c_str(), &((unsigned char *)_command.c_str())[0], (unsigned int)_command.length());

	// Impose a small delay to prevent overloading buffer
	Sleep(SERIAL_DELAY_MS);

	// Get/check response if desired
	if (get_response)
		return GetResponse();
	else
		return DEVICE_OK;


}

int ServoZ::GetResponse()
{
	// Get answer
	GetSerialAnswer(port_.c_str(), "-==-", _serial_answer);

	// Set property
	SetProperty(g_Keyword_Response, _serial_answer.c_str());

	// Search for error
	std::string error_flag("ERROR");
	if (_serial_answer.find(error_flag) != std::string::npos)
		return DEVICE_ERR;
	else
		return DEVICE_OK;
}

int ServoZ::SyncState()
{

	//// Get current NA
	//SendCommand("na", true);
	//std::string na_str("NA.");
	//numerical_aperture = (float)atoi(_serial_answer.substr(_serial_answer.find(na_str) + na_str.length(), _serial_answer.length() - na_str.length()).c_str()) / 100.0;

	//// Get current array distance
	//SendCommand("sad", true);
	//std::string sad_str("DZ.");
	//array_distance_z = (float)atoi(_serial_answer.substr(_serial_answer.find(sad_str) + sad_str.length(), _serial_answer.length() - sad_str.length()).c_str());

	//// Set brightness:
	//SetProperty(g_Keyword_Brightness, std::to_string((long long)brightness).c_str());

	//// Set Numerical Aperture:
	//SetProperty(g_Keyword_NumericalAperture, std::to_string((long double)numerical_aperture).c_str());

	////Set Array Dist:
	//SetProperty(g_Keyword_SetArrayDistanceMM, std::to_string((long double)array_distance_z).c_str());

	return DEVICE_OK;
}

int ServoZ::SetMachineMode(bool mode)
{
	// Check that we have a controller:
	PurgeComPort(port_.c_str());

	if (mode)
	{
		// Send command to device
		unsigned char myString[] = "machine\n";
		WriteToComPort(port_.c_str(), &myString[0], 8);
	}
	else {
		// Send command to device
		unsigned char myString[] = "human\n";
		WriteToComPort(port_.c_str(), &myString[0], 7);
	}

	std::string answer;
	GetSerialAnswer(port_.c_str(), "-==-", answer);

	// Set property
	SetProperty(g_Keyword_Response, answer.c_str());

	return DEVICE_OK;
}

int ServoZ::GetPositionUm(double& pos)
{
	pos = positionSteps_ * umPerStep_;
	return DEVICE_OK;
}

int ServoZ::SetPositionUm(double pos)
{
	positionSteps_ = (long) (pos / umPerStep_);
	return SetPosition();
}

ServoZ::~ServoZ()
{
	Shutdown();
}

bool ServoZ::Busy()
{
	// Get current array distance
	SendCommand("im", true);
	bool ismoving = (bool)atoi(_serial_answer.substr(0, 1).c_str());
	return ismoving;
}

void ServoZ::GetName(char* name) const
{
	CDeviceUtils::CopyLimitedString(name, g_Keyword_DeviceName);
}

int ServoZ::Shutdown()
{
	if (initialized_)
	{
		initialized_ = false;
	}
	return DEVICE_OK;
}


int ServoZ::Reset()
{
	// Send reset command
	SendCommand("reset", true);

	// Return
	return DEVICE_OK;
}

int ServoZ::SetAccleration()
{
	// Set Numerical Aperture
	std::string command("sa.");
	command += std::to_string((long long)(acceleration_));

	// Send Command
	SendCommand(command.c_str(), true);

	// Return
	return DEVICE_OK;
}

int ServoZ::SetSpeed()
{
	// Set Numerical Aperture
	std::string command("ss.");
	command += std::to_string((long long)(speed_));

	// Send Command
	return SendCommand(command.c_str(), true);

	// Return
	return DEVICE_OK;
}

int ServoZ::SetPosition()
{
	std::string command("sp.");
	command += std::to_string((long long)(positionSteps_));
	return SendCommand(command.c_str(), true);
}

int ServoZ::Clear()
{
	// Send Command
	SendCommand("x", true);

	// Return
	return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int ServoZ::OnPort(MM::PropertyBase* pProp, MM::ActionType pAct)
{
	if (pAct == MM::BeforeGet)
	{
		pProp->Set(port_.c_str());
	}
	else if (pAct == MM::AfterSet)
	{
		pProp->Get(port_);
		portAvailable_ = true;
	}
	return DEVICE_OK;
}

int ServoZ::OnReset(MM::PropertyBase* pProp, MM::ActionType pAct)
{
	if (pAct == MM::BeforeGet)
	{
		pProp->Set("0");
	}
	else if (pAct == MM::AfterSet)
	{
		Reset();
	}
	return DEVICE_OK;
}

int ServoZ::OnAcceleration(MM::PropertyBase* pProp, MM::ActionType pAct)
{
	if (pAct == MM::BeforeGet)
	{
		pProp->Set(acceleration_);
	}
	else if (pAct == MM::AfterSet)
	{
		pProp->Get(acceleration_);
		SetAccleration();
	}
	return DEVICE_OK;
}

int ServoZ::OnSpeed(MM::PropertyBase* pProp, MM::ActionType pAct)
{
	if (pAct == MM::BeforeGet)
	{
		pProp->Set(speed_);
	}
	else if (pAct == MM::AfterSet)
	{
		pProp->Get(speed_);
		SetSpeed();
	}
	return DEVICE_OK;
}

int ServoZ::OnCalibration(MM::PropertyBase* pProp, MM::ActionType pAct)
{
	if (pAct == MM::BeforeGet)
	{
		pProp->Set(umPerStep_);
	}
	else if (pAct == MM::AfterSet)
	{
		pProp->Get(umPerStep_);
	}
	return DEVICE_OK;
}

int ServoZ::OnCommand(MM::PropertyBase* pProp, MM::ActionType pAct)
{
	if (pAct == MM::BeforeGet)
	{
		pProp->Set(_command.c_str());
	}
	else if (pAct == MM::AfterSet)
	{
		// Get command string
		pProp->Get(_command);

		// Append terminator
		_command += "\n";

		// Purge COM Port
		PurgeComPort(port_.c_str());

		// Send command
		WriteToComPort(port_.c_str(), (unsigned char *)_command.c_str(), (unsigned int)_command.length());

		// Get Answer
		std::string answer;
		GetSerialAnswer(port_.c_str(), "-==-", answer);

		// Set property
		SetProperty(g_Keyword_Response, answer.c_str());
		//SetProperty(g_Keyword_Response, std::to_string((long long)answer.length()).c_str());
	}

	// Return
	return DEVICE_OK;
}

