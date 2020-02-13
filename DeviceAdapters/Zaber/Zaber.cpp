///////////////////////////////////////////////////////////////////////////////
// FILE:          Zaber.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Zaber Controller Driver
//                
// AUTHOR:        David Goosen & Athabasca Witschi (contact@zaber.com)
//                
// COPYRIGHT:     Zaber Technologies Inc., 2014
//
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
#pragma warning(disable: 4355)
#endif
#include "FixSnprintf.h"

#include "Zaber.h"
#include "XYStage.h"
#include "Stage.h"
#include "FilterWheel.h"
#include "FilterCubeTurret.h"
#include "Illuminator.h"

using namespace std;

const char* g_Msg_PORT_CHANGE_FORBIDDEN = "The port cannot be changed once the device is initialized.";
const char* g_Msg_DRIVER_DISABLED = "The driver has disabled itself due to overheating.";
const char* g_Msg_BUSY_TIMEOUT = "Timed out while waiting for device to finish executing a command.";
const char* g_Msg_AXIS_COUNT = "Dual-axis controller required.";
const char* g_Msg_COMMAND_REJECTED = "The device rejected the command.";
const char* g_Msg_NO_REFERENCE_POS = "The device has not had a reference position established.";
const char* g_Msg_SETTING_FAILED = "The property could not be set. Is the value in the valid range?";
const char* g_Msg_INVALID_DEVICE_NUM = "Device numbers must be in the range of 1 to 99.";
const char* g_Msg_LAMP_DISCONNECTED= "Some of the illuminator lamps are disconnected.";
const char* g_Msg_LAMP_OVERHEATED = "Some of the illuminator lamps are overheated.";


//////////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
//////////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
	RegisterDevice(g_XYStageName, MM::XYStageDevice, g_XYStageDescription);
	RegisterDevice(g_StageName, MM::StageDevice, g_StageDescription);
	RegisterDevice(g_FilterWheelName, MM::StateDevice, g_FilterWheelDescription);
	RegisterDevice(g_FilterTurretName, MM::StateDevice, g_FilterTurretDescription);
	RegisterDevice(g_IlluminatorName, MM::ShutterDevice, g_IlluminatorDescription);
}                                                            


MODULE_API MM::Device* CreateDevice(const char* deviceName)                  
{
	if (strcmp(deviceName, g_XYStageName) == 0)
	{
		return new XYStage();
	}
	else if (strcmp(deviceName, g_StageName) == 0)
	{	
		return new Stage();
	}
	else if (strcmp(deviceName, g_FilterWheelName) == 0)
	{	
		return new FilterWheel();
	}
	else if (strcmp(deviceName, g_FilterTurretName) == 0)
	{	
		return new FilterCubeTurret();
	}
	else if (strcmp(deviceName, g_IlluminatorName) == 0)
	{	
		return new Illuminator();
	}
	else
	{	
		return 0;
	}
}


MODULE_API void DeleteDevice(MM::Device* pDevice)
{
	delete pDevice;
}


///////////////////////////////////////////////////////////////////////////////
// ZaberBase (convenience parent class)
///////////////////////////////////////////////////////////////////////////////

ZaberBase::ZaberBase(MM::Device *device) :
	initialized_(false),
	port_("Undefined"),
	device_(device),
	core_(0),
	cmdPrefix_("/")
{
}


ZaberBase::~ZaberBase()
{
}


// COMMUNICATION "clear buffer" utility function:
int ZaberBase::ClearPort() const
{
	core_->LogMessage(device_, "ZaberBase::ClearPort\n", true);

	const int bufSize = 255;
	unsigned char clear[bufSize];
	unsigned long read = bufSize;
	int ret;

	while ((int) read == bufSize)
	{
		ret = core_->ReadFromSerial(device_, port_.c_str(), clear, bufSize, read);
		if (ret != DEVICE_OK) 
		{
			return ret;
		}
	}

	return DEVICE_OK;      
}


// COMMUNICATION "send" utility function:
int ZaberBase::SendCommand(const string command) const
{
	core_->LogMessage(device_, "ZaberBase::SendCommand\n", true);

	const char* msgFooter = "\n"; // required by Zaber ASCII protocol
	string baseCommand = "";
	baseCommand += command;
	return core_->SetSerialCommand(device_, port_.c_str(), baseCommand.c_str(), msgFooter);
}


int ZaberBase::SendCommand(long device, long axis, const string command) const
{
	core_->LogMessage(device_, "ZaberBase::SendCommand(device,axis)\n", true);

	ostringstream cmd;
	cmd << cmdPrefix_ << device << " " << axis << " " << command;
	vector<string> resp;
	return QueryCommand(cmd.str().c_str(), resp);
}


// COMMUNICATION "send & receive" utility function:
int ZaberBase::QueryCommand(const string command, vector<string>& reply) const
{
	core_->LogMessage(device_, "ZaberBase::QueryCommand\n", true);

	const char* msgFooter = "\r\n"; // required by Zaber ASCII protocol

	const size_t BUFSIZE = 2048;
	char buf[BUFSIZE] = {'\0'};

	int ret = SendCommand(command);
	if (ret != DEVICE_OK) 
	{
		return ret;
	}

	ret = core_->GetSerialAnswer(device_, port_.c_str(), BUFSIZE, buf, msgFooter);
	if (ret != DEVICE_OK) 
	{
		return ret;
	}

	string resp = buf;
	if (resp.length() < 1)
	{
		return  DEVICE_SERIAL_INVALID_RESPONSE;
	}

	// remove checksum before parsing
	int thirdLast = int(resp.length() - 3);
	if (resp[thirdLast] == ':')
	{
		resp.erase(thirdLast, string::npos);
	}

	CDeviceUtils::Tokenize(resp, reply, " ");
	/* reply[0] = message type and device address, reply[1] = axis number,
	 * reply[2] = reply flags, reply[3] = device status, reply[4] = warning flags,
	 * reply[5] (and possibly reply[6]) = response data, if there is data
	 */
	if (reply.size() < 5)
	{
		return DEVICE_SERIAL_INVALID_RESPONSE;
	}

	if (reply[4] == "FD")
	{
		return ERR_DRIVER_DISABLED;
	}

	if (reply[2] == "RJ")
	{
		return ERR_COMMAND_REJECTED;
	}

	return DEVICE_OK;
}


int ZaberBase::GetSetting(long device, long axis, string setting, long& data) const
{
	core_->LogMessage(device_, "ZaberBase::GetSetting(long)\n", true);

	ostringstream cmd;
	cmd << cmdPrefix_ << device << " " << axis << " get " << setting;
	vector<string> resp;

	int ret = QueryCommand(cmd.str().c_str(), resp);
	if (ret != DEVICE_OK) 
	{
		return ret;
	}

	// extract data
	string dataString = resp[5];
	stringstream(dataString) >> data;
	return DEVICE_OK;
}


int ZaberBase::GetSetting(long device, long axis, string setting, double& data) const
{
	core_->LogMessage(device_, "ZaberBase::GetSetting(double)\n", true);

	ostringstream cmd;
	cmd << cmdPrefix_ << device << " " << axis << " get " << setting;
	vector<string> resp;

	int ret = QueryCommand(cmd.str().c_str(), resp);
	if (ret != DEVICE_OK) 
	{
		return ret;
	}

	// extract data
	string dataString = resp[5];
	stringstream(dataString) >> data;
	return DEVICE_OK;
}


int ZaberBase::SetSetting(long device, long axis, string setting, long data) const
{
	core_->LogMessage(device_, "ZaberBase::SetSetting(long)\n", true);

	ostringstream cmd; 
	cmd << cmdPrefix_ << device << " " << axis << " set " << setting << " " << data;
	vector<string> resp;

	int ret = QueryCommand(cmd.str().c_str(), resp);
	if (ret != DEVICE_OK)
	{
		return ERR_SETTING_FAILED;
	}

	return DEVICE_OK;
}


int ZaberBase::SetSetting(long device, long axis, string setting, double data, int decimalPlaces) const
{
	core_->LogMessage(device_, "ZaberBase::SetSetting(double)\n", true);

	ostringstream cmd; 
	cmd.precision(decimalPlaces);
	cmd << cmdPrefix_ << device << " " << axis << " set " << setting << " " << fixed << data;
	vector<string> resp;

	int ret = QueryCommand(cmd.str().c_str(), resp);
	if (ret != DEVICE_OK)
	{
		return ERR_SETTING_FAILED;
	}

	return DEVICE_OK;
}


bool ZaberBase::IsBusy(long device) const
{
	core_->LogMessage(device_, "ZaberBase::IsBusy\n", true);

	ostringstream cmd;
	cmd << cmdPrefix_ << device;
	vector<string> resp;

	int ret = QueryCommand(cmd.str().c_str(), resp);
	if (ret != DEVICE_OK)
	{
		ostringstream os;
		os << "SendSerialCommand failed in ZaberBase::IsBusy, error code: " << ret;
		core_->LogMessage(device_, os.str().c_str(), false);
		return false;
	}

	return (resp[3] == ("BUSY"));
}


int ZaberBase::Stop(long device) const
{
	core_->LogMessage(device_, "ZaberBase::Stop\n", true);

	ostringstream cmd;
	cmd << cmdPrefix_ << device << " stop";
	vector<string> resp;
	return QueryCommand(cmd.str().c_str(), resp);
}


int ZaberBase::GetLimits(long device, long axis, long& min, long& max) const
{
	core_->LogMessage(device_, "ZaberBase::GetLimits\n", true);

	int ret = GetSetting(device, axis, "limit.min", min);
	if (ret != DEVICE_OK) 
	{
		return ret;
	}

	return GetSetting(device, axis, "limit.max", max);
}


int ZaberBase::SendMoveCommand(long device, long axis, std::string type, long data) const
{
	core_->LogMessage(device_, "ZaberBase::SendMoveCommand\n", true);

	ostringstream cmd;
	cmd << cmdPrefix_ << device << " " << axis << " move " << type << " " << data;
	vector<string> resp;
	return QueryCommand(cmd.str().c_str(), resp);
}


int ZaberBase::SendAndPollUntilIdle(long device, long axis, string command, int timeoutMs) const
{
	core_->LogMessage(device_, "ZaberBase::SendAndPollUntilIdle\n", true);

	ostringstream cmd;
	cmd << cmdPrefix_ << device << " " << axis << " " << command;
	vector<string> resp;

	int ret = QueryCommand(cmd.str().c_str(), resp);
	if (ret != DEVICE_OK) 
	{
		return ret;
	}

	int numTries = 0, pollIntervalMs = 100;
	do
	{
		numTries++;
		CDeviceUtils::SleepMs(pollIntervalMs);
	}
	while (IsBusy(device) && (numTries*pollIntervalMs < timeoutMs));
	
	if (numTries*pollIntervalMs >= timeoutMs)
	{
		return ERR_BUSY_TIMEOUT;
	}

	ostringstream os;
	os << "Completed after " << (numTries*pollIntervalMs/1000.0) << " seconds.";
	core_->LogMessage(device_, os.str().c_str(), true);
	return DEVICE_OK;
}


int ZaberBase::GetRotaryIndexedDeviceInfo(long device, long axis, long& numIndices, long& currentIndex) const
{
	core_->LogMessage(device_, "ZaberBase::GetRotaryIndexedDeviceInfo\n", true);

   // Get the size of a full circle in microsteps.
   long cycleSize = -1;
   int ret = GetSetting(device, axis, "limit.cycle.dist", cycleSize);
   if (ret != DEVICE_OK) 
   {
      core_->LogMessage(device_, "Attempt to detect rotary cycle distance failed.\n", true);
      return ret;
   }

   if ((cycleSize < 1) || (cycleSize > 1000000000))
   {
      core_->LogMessage(device_, "Device cycle distance is out of range or was not returned.\n", true);
      return DEVICE_SERIAL_INVALID_RESPONSE;
   }


   // Get the size of a filter increment in microsteps.
   long indexSize = -1;
   ret = GetSetting(device, axis, "motion.index.dist", indexSize);
   if (ret != DEVICE_OK) 
   {
      core_->LogMessage(device_, "Attempt to detect index spacing failed.\n", true);
      return ret;
   }

   if ((indexSize < 1) || (indexSize > 1000000000) || (indexSize > cycleSize))
   {
      core_->LogMessage(device_, "Device index distance is out of range or was not returned.\n", true);
      return DEVICE_SERIAL_INVALID_RESPONSE;
   }

   numIndices = cycleSize / indexSize;

   long index = -1;
   ret = GetSetting(device, axis, "motion.index.num", index);
   if (ret != DEVICE_OK) 
   {
      core_->LogMessage(device_, "Attempt to detect current index position failed.\n", true);
      return ret;
   }

   if ((index < 0) || (index > 1000000000))
   {
      core_->LogMessage(device_, "Device current index is out of range or was not returned.\n", true);
      return DEVICE_SERIAL_INVALID_RESPONSE;
   }

   currentIndex = index;

   return ret;
}

