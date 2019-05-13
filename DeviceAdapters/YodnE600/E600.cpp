///////////////////////////////////////////////////////////////////////////////
// FILE:          E600.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Yodn E600 light source controller adapter
// COPYRIGHT:     
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
// AUTHOR:        BJI MBQ (mbaoqi@outlook.com)
///////////////////////////////////////////////////////////////////////////////

#ifdef WIN32
#include <windows.h>
#endif

#include "E600.h"

// Include micro-manager header files.
#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/ModuleInterface.h"
#include "../../MMDevice/DeviceUtils.h"

// Include standard c/c++ library.
#include <string>
#include <math.h>
#include <sstream>

// Include E600 variables definition file.
#include "E600Defs.h"

// Define a static lock.
MMThreadLock E600Controller::lock_;


///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
	RegisterDevice(g_ControllerName, MM::ShutterDevice, g_ProductName);
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
	if (deviceName == 0)
		return 0;

	if (strcmp(deviceName, g_ControllerName) == 0)
	{
		E600Controller* pController = new E600Controller(g_ControllerName);
		return pController;
	}

	return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
	delete pDevice;
}


///////////////////////////////////////////////////////////////////////////////
// E600 device functions implementation
///////////////////////////////////////////////////////////////////////////////
E600Controller::E600Controller(const char* name) :
	initialized_(false),
	isDisconnect_(false),
	name_(name),
	error_(0),
	changedTime_(0.0),
	mThread_(0)
{
	assert(strlen(name) < (unsigned int)MM::MaxStrLength);

	InitializeDefaultErrorMessages();

	CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
	CPropertyAction* pAct = new CPropertyAction(this, &E600Controller::OnPort);
	CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

	EnableDelay();
	UpdateStatus();
}

E600Controller::~E600Controller()
{
	Shutdown();
}

bool E600Controller::Busy()
{
	MM::MMTime interval = GetCurrentMMTime() - changedTime_;
	MM::MMTime delay(GetDelayMs()*1000.0);
	if (interval < delay)
		return true;
	else
		return false;
}

void E600Controller::GetName(char* name) const
{
	assert(name_.length() < CDeviceUtils::GetMaxStringLength());
	CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int E600Controller::Initialize()
{
	// lock protect.
	MMThreadGuard mylock(lock_);
	
	// First step to send open command.
	unsigned char data_send[1] = { 0x70 };
	SendData(data_send, sizeof(data_send) / sizeof(unsigned char));
	ReadData(data_send, sizeof(data_send) / sizeof(unsigned char));

	// Create and get main version and panel version from device register.
	CreateMainVersionProperty();
	CreatePanelVersionProperty();
	
	// set flag for every channel.
	channelUse_[0] = 0x01;
	channelUse_[1] = 0x02;
	channelUse_[2] = 0x03;

	for (int i = 0; i < num_channel; i++)
	{
		channelIntensitiesUpdate_[i] = true;
		channelUseStateUpdate_[i] = true;
	}
	lampStateUpdate_ = true;

	Update();

	// Create error code property.
	CPropertyAction* pa;
	pa = new CPropertyAction(this, &E600Controller::OnErrorCode);
	CreateProperty(g_Keyword_ErrorCode, "0x00", MM::String, true, pa);

	// Create channel intensity properties and generate sliders.
	CPropertyActionEx* pAct;
	string intensityName;
	for (unsigned i = 0; i < num_channel; i++)
	{
		pAct = new CPropertyActionEx(this, &E600Controller::OnChannelIntensity, i);
		intensityName = g_Keyword_Intensity;
		intensityName = intensityName + " CH" + IToString(i + 1);
		CreateProperty(intensityName.c_str(), "0", MM::Integer, false, pAct);
		SetPropertyLimits(intensityName.c_str(), 0, 100);
	}
	
	// Create lamp status property and initialize option value 0 or 1.
	pa = new CPropertyAction(this, &E600Controller::OnLampSwitch);
	CreateProperty(g_Keyword_Lamp, "0", MM::Integer, false, pa);
	std::vector<std::string> switchValues;
	switchValues.push_back("0");
	switchValues.push_back("1");
	SetAllowedValues(g_Keyword_Lamp, switchValues);

	// Create channel temperature properties and set read only.
	string temperature;
	for (unsigned i = 0; i < num_channel; i++)
	{
		pAct = new CPropertyActionEx(this, &E600Controller::OnChannelTemperature, i);
		temperature = g_Keyword_Temperature;
		temperature = temperature + " CH" + IToString(i + 1) + "(Deg.C)";
		CreateProperty(temperature.c_str(), "0", MM::Integer, true, pAct);
	}

	// Create channel use status properties and initialize option value 0 or 1.
	string use;
	for (unsigned i = 0; i < num_channel; i++)
	{
		pAct = new CPropertyActionEx(this, &E600Controller::OnChannelUse, i);
		use = g_Keyword_Use;
		use = use + " CH" + IToString(i + 1);
		CreateProperty(use.c_str(), "0", MM::Integer, false, pAct);

		std::vector<std::string> switchValues;
		switchValues.push_back("0");
		switchValues.push_back("1");
		SetAllowedValues(use.c_str(), switchValues);
	}

	// Create channel use time properties and set read only.
	string useTime;
	for (unsigned i = 0; i < num_channel; i++)
	{
		pAct = new CPropertyActionEx(this, &E600Controller::OnChannelUseTime, i);
		useTime = g_Keyword_UseTime;
		useTime = useTime + " CH" + IToString(i + 1);
		CreateProperty(useTime.c_str(), "0", MM::Integer, true, pAct);
	}

	// Init done and set this flag true.
	initialized_ = true;
	isDisconnect_ = false;

	// Run another thead to detect values changing.
	mThread_ = new PollingThread(*this);
	mThread_->Start();

	// Return error handle.
	return HandleErrors();
}

int E600Controller::Update()
{
	MMThreadGuard myLock(lock_);
	Purge();

	// Update lamp state.
	unsigned char data[2] = { 0x57, 0x00 };
	unsigned char resLampSate[3] = { 0, 0, 0 };
	SendData(data, sizeof(data) / sizeof(unsigned char));
	ReadData(resLampSate, sizeof(resLampSate) / sizeof(unsigned char));
	lampState_ = resLampSate[2];

	// Update lamp channel inensity values.
	data[0] = 0x56;
	data[1] = 0x01;
	unsigned char result[3] = { 0, 0, 0 };
	for (int i = 0; i < 3; i++)
	{
		Purge();

		data[1] = channelUse_[i];
		int ret = SendData(data, sizeof(data) / sizeof(unsigned char));
		ret = ReadData(result, sizeof(result) / sizeof(unsigned char));
		channelIntensitiesValue_[i] = result[2];
	}

	// Update lamp channel use state values.
	data[0] = 0x57;
	data[1] = 0x01;
	for (int i = 0; i < 3; i++)
	{
		Purge();

		data[1] = channelUse_[i];
		int ret = SendData(data, sizeof(data) / sizeof(unsigned char));
		ret = ReadData(result, sizeof(result) / sizeof(unsigned char));
		channelUseState_[i] = result[2];
	}

	// Update lamp channel temperature values.
	data[0] = 0x55;
	data[1] = 0x01;
	for (int i = 0; i < 3; i++)
	{
		Purge();

		data[1] = channelUse_[i];
		int ret = SendData(data, sizeof(data) / sizeof(unsigned char));
		ret = ReadData(result, sizeof(result) / sizeof(unsigned char));
		channelTemperature_[i] = result[2];
	}

	// Update lamp channel use time.
	data[0] = 0x53;
	data[1] = 0x01;
	unsigned char resHours[4] = { 0, 0, 0, 0 };
	for (int i = 0; i < 3; i++)
	{
		Purge();

		data[1] = channelUse_[i];
		int ret = SendData(data, sizeof(data) / sizeof(unsigned char));
		ret = ReadData(resHours, sizeof(resHours) / sizeof(unsigned char));
		channelUseHours_[i] = resHours[3] + resHours[2] * 256; // calculate hours.
	}

	// Update lamp error code.
	Purge();
	unsigned char dataError[1] = { 0x52 };
	unsigned char resError[2] = { 0, 0 };

	int ret = SendData(dataError, sizeof(dataError) / sizeof(unsigned char));
	ret = ReadData(resError, sizeof(resError) / sizeof(unsigned char));	
	errorCode_ = resError[1];

	// If over heat and error code is 0x01, set lamp off.
	if (errorCode_ == 1)
	{
		lampStateUpdate_ = true;
		lampState_ = 0;

		// Send lamp switch off command.
		unsigned char data[] = { 0x60, 0x00, 0x00 };
		SendData(data, sizeof(data) / sizeof(unsigned char));
	}

	return 0;
}

///////////////////////////////////////////////////////////////////////////////
// Property Generators
///////////////////////////////////////////////////////////////////////////////
int E600Controller::Shutdown()
{
	if (initialized_)
	{
		MMThreadGuard mylock(lock_);
		Purge();

		// When shutdown device, auto send close command.
		unsigned char data[] = { 0x75 };
		SendData(data, sizeof(data) / sizeof(unsigned char));

		// Reset initialize flag to false.
		initialized_ = false;
		delete(mThread_);
	}

	return HandleErrors();
}


///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////
int E600Controller::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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
			return ERR_PORT_CHANGE_FORBIDDEN;
		}

		pProp->Get(port_);
	}

	return HandleErrors();
}

int E600Controller::OnErrorCode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		std::string str;
		SetErrorCodeStr(errorCode_, str);
		pProp->Set(str.c_str());
	}
	else if (eAct == MM::AfterSet)
	{
		// empty, no action.
	}

	return HandleErrors();
}

int E600Controller::OnLampSwitch(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	// If device disconnect, return an error flag.
	if (isDisconnect_)
		return DEVICE_NOT_CONNECTED;

	if (eAct == MM::BeforeGet && lampStateUpdate_)
	{
		pProp->Set((long)lampState_);
		lampStateUpdate_ = false; // After initialization.
	}
	else if (eAct == MM::AfterSet)
	{
		long value;
		pProp->Get(value);

		MMThreadGuard myLock(lock_);
		Purge();
		if (value == 1)
		{
			// Send lamp switch on command.
			unsigned char data[] = { 0x60, 0x00, 0x01 };
			SendData(data, sizeof(data) / sizeof(unsigned char));
		}
		else
		{
			// Send lamp switch off command.
			unsigned char data[] = { 0x60, 0x00, 0x00 };
			SendData(data, sizeof(data) / sizeof(unsigned char));
		}
	}

	return HandleErrors();
}

int E600Controller::OnChannelUse(MM::PropertyBase* pProp, MM::ActionType eAct, long index)
{
	// If device disconnect, return an error flag.
	if (isDisconnect_)
		return DEVICE_NOT_CONNECTED;

	long channelState;

	if (eAct == MM::BeforeGet && channelUseStateUpdate_[index]) 
	{
		pProp->Set((long)channelUseState_[index]);
		channelUseStateUpdate_[index] = false; // After initialization.
	}
	else if (eAct == MM::AfterSet)
	{
		MMThreadGuard myLock(lock_);
		Purge();

		pProp->Get(channelState);
		channelUseState_[index] = (unsigned char)channelState;

		if (channelState == 1)
		{
			// Send channel use state on command.
			unsigned char data[] = { 0x60, channelUse_[index], 0x01 };
			SendData(data, sizeof(data) / sizeof(unsigned char));
		}
		else
		{
			// Send channel use state off command.
			unsigned char data[] = { 0x60, channelUse_[index], 0x00 };
			SendData(data, sizeof(data) / sizeof(unsigned char));
		}
	}

	return HandleErrors();
}

int E600Controller::OnChannelIntensity(MM::PropertyBase* pProp, MM::ActionType eAct, long index)
{
	// If device disconnect, return an error flag.
	if (isDisconnect_)
		return DEVICE_NOT_CONNECTED;

	long intensity;
	if (eAct == MM::BeforeGet && channelIntensitiesUpdate_[index]) 
	{
		pProp->Set((long)channelIntensitiesValue_[index]);
		channelIntensitiesUpdate_[index] = false;
	}
	else if (eAct == MM::AfterSet)
	{
		MMThreadGuard myLock(lock_);
		Purge();
		
		// Set channel intensity value.
		pProp->Get(intensity);
		channelIntensitiesValue_[index] = (unsigned char)intensity;
		SetIntensity(intensity, index);
	}

	return HandleErrors();
}

int E600Controller::OnChannelTemperature(MM::PropertyBase* pProp, MM::ActionType eAct, long index)
{
	if (eAct == MM::BeforeGet) 
	{
		pProp->Set(SetTemperatureTransform((long)channelTemperature_[index]));
	}
	else if (eAct == MM::AfterSet) 
	{
		// empty, no action.
	}

	return HandleErrors();
}

int E600Controller::OnChannelUseTime(MM::PropertyBase* pProp, MM::ActionType eAct, long index)
{
	if (eAct == MM::BeforeGet)
	{
		pProp->Set((long)channelUseHours_[index]);
	}
	else if (eAct == MM::AfterSet)
	{
		// empty, no action.
	}

	return HandleErrors();
}


///////////////////////////////////////////////////////////////////////////////
// Utility functions for detail usage.
///////////////////////////////////////////////////////////////////////////////
int E600Controller::SendData(unsigned char *data, unsigned int size)
{
	return WriteToComPort(port_.c_str(), data, size);
}

int E600Controller::ReadData(unsigned char *data, unsigned int size)
{
	if (isDisconnect_) // If device already disconnected, return.
		return DEVICE_NOT_CONNECTED;

	int ret = DEVICE_OK;
	unsigned long num = 0;
	unsigned long startTime = GetClockTicksUs();
	unsigned int read_count = 0;

	do {
		ret = ReadFromComPort(port_.c_str(), data, size, num);
		if (num == 0)
		{
         CDeviceUtils::SleepMs(1);
			read_count++;
			if (read_count > max_read_count) // Check serial device disconnect or not.
			{
				isDisconnect_ = true;
				ret = DEVICE_NOT_CONNECTED;
				break;
			}
		}
	} while (num == 0 || (GetClockTicksUs() - startTime) / 1000.0 < answerTimeoutMs_);

	return ret;
}

void E600Controller::CreateMainVersionProperty()
{
	MMThreadGuard myLock(lock_);
	Purge();

	// Send serial command to get main version data from device register.
	unsigned char dataSe[] = { 0x50 };
	unsigned char dataRe[128] = { '0' };
	SendData(dataSe, sizeof(dataSe) / sizeof(unsigned char));
	ReadData(dataRe, sizeof(dataRe) / sizeof(unsigned char));
	
	// Create main version property in "Device Property Browser".
	CreateProperty(g_Keyword_MainVersion, (char*)dataRe, MM::String, true, NULL);
}

void E600Controller::CreatePanelVersionProperty()
{
	MMThreadGuard myLock(lock_);
	Purge();

	// Send serial command to get panel version data from device register.
	unsigned char dataSe[] = { 0x51 };
	unsigned char dataRe[128] = { '0' };
	SendData(dataSe, sizeof(dataSe) / sizeof(unsigned char));
	ReadData(dataRe, sizeof(dataRe) / sizeof(unsigned char));

	// Remove the first character of the return char data. For example: "Q".
	std::string panel_version((char*)dataRe);
	panel_version.erase(panel_version.begin());

	// Create panel version property in "Device Property Browser".
	CreateProperty(g_Keyword_PanelVersion, panel_version.c_str(), MM::String, true, NULL);
}

void E600Controller::SetIntensity(long intensity, long index)
{
	unsigned char data[] = { 0x61, channelUse_[index], (unsigned char)intensity };
	SendData(data, sizeof(data) / sizeof(unsigned char));
}

long E600Controller::SetTemperatureTransform(const long in)
{
	long value;
	value = in >= 128 ? (in - 256) : in; // Value transform. Based on hardware. 
	return value;
}

void E600Controller::SetErrorCodeStr(const unsigned char errorCode, std::string &str)
{
	long value = (long)errorCode;
	std::stringstream ss;
	ss << "0x" << std::uppercase << std::setfill('0') << std::setw(2) << std::hex << value;
	str = ss.str();

	if (value == 0)
		str = str + " (No Error)";
	else if (value == 1)
		str = str + " (Over Heat)";
}

int E600Controller::HandleErrors()
{
	int lastError = error_;
	error_ = 0;
	return lastError;
}

void E600Controller::Purge()
{
	int ret = PurgeComPort(port_.c_str());
	if (ret != 0)
		error_ = DEVICE_SERIAL_COMMAND_FAILED;
}

int E600Controller::SetOpen(bool open)
{
	MMThreadGuard myLock(lock_);
	SetProperty(g_Keyword_Lamp, open ? "1" : "0");
	
	return HandleErrors();
}

int E600Controller::GetOpen(bool& open)
{
	long state;
	GetProperty(g_Keyword_Lamp, state);
	
	if (state == 1)
		open = true;
	else if (state == 0)
		open = false;
	else
		error_ = DEVICE_UNKNOWN_POSITION;
	
	return HandleErrors();
}

int E600Controller::Fire(double deltaT)
{
	deltaT = 0;
	error_ = DEVICE_UNSUPPORTED_COMMAND;
	return HandleErrors();
}

std::string E600Controller::IToString(int in)
{
   std::ostringstream stream;
   stream << in;
   return stream.str();
}

PollingThread::PollingThread(E600Controller& aController) :
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
		aController_.Update();
		CDeviceUtils::SleepMs(100);
	}

	return DEVICE_OK;
}

void PollingThread::Start()
{
	stop_ = false;
	activate();
}

