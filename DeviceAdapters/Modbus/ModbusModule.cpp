///////////////////////////////////////////////////////////////////////////////
// FILE:          ModbusModule.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Modbus Adapter using libmodbus
//                
// AUTHOR:        Christian Sachs <c.sachs@fz-juelich.de>
//
// COPYRIGHT:     Forschungszentrum Jülich
// LICENSE:       BSD (2-clause/FreeBSD license)

#define _WINSOCK2API_
#define _WINSOCKAPI_
#include "ModbusModule.h"

#include <string>
#include <iostream>
#include "../../MMDevice/ModuleInterface.h"

using namespace std;

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////

const char* g_ModbusDeviceName = "Modbus";

MODULE_API void InitializeModuleData()
{
	RegisterDevice(g_ModbusDeviceName, MM::GenericDevice, g_ModbusDeviceName);
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_ModbusDeviceName) == 0)
   {
	  return new CModbusDevice();
   }

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

//

void CModbusDevice::GetName(char* pszName) const {
	CDeviceUtils::CopyLimitedString(pszName, g_ModbusDeviceName);
}

bool CModbusDevice::Busy() {
	return false;
}

//

CModbusDevice::CModbusDevice() {

	
	coilBuffer = NULL;
	registerBuffer = NULL;

	ready = false;
	debugFlag = 0;

	connectionURI = "tcp://127.0.0.1:502";

	deviceString = "name=device type=coils write_address=0 read_address=0 count=8";

	CreateProperty("ConnectionURI", "", MM::String, false, new DeviceAction(this, &CModbusDevice::OnConnectionURI), true);
	CreateProperty("Devices", "", MM::String, false, new DeviceAction(this, &CModbusDevice::OnDeviceString), true);
	CreateProperty("DebugFlag", "0", MM::Integer, false, new DeviceAction(this, &CModbusDevice::OnDebugFlag), true);
	AddAllowedValue("DebugFlag", "0");
	AddAllowedValue("DebugFlag", "1");

}

CModbusDevice::~CModbusDevice() {
	// nop
}


int CModbusDevice::OnConnectionURI(MM::PropertyBase *pProp, MM::ActionType eAct) {
	if(eAct == MM::BeforeGet)
	{
		pProp->Set(connectionURI.c_str());
	}
	else if(eAct == MM::AfterSet)
	{
		pProp->Get(connectionURI);
	}

	return DEVICE_OK;
}

int CModbusDevice::OnDeviceString(MM::PropertyBase *pProp, MM::ActionType eAct) {
	if(eAct == MM::BeforeGet)
	{
		pProp->Set(deviceString.c_str());
	}
	else if(eAct == MM::AfterSet)
	{
		pProp->Get(deviceString);
	}

	return DEVICE_OK;
}

int CModbusDevice::OnDebugFlag(MM::PropertyBase *pProp, MM::ActionType eAct) {
	if(eAct == MM::BeforeGet)
	{
		pProp->Set(debugFlag);
	}
	else if(eAct == MM::AfterSet)
	{
		pProp->Get(debugFlag);
	}

	return DEVICE_OK;
}

inline int CModbusDevice::checkContextAndContinue() {
	if(ctx == NULL)
		return DEVICE_ERR;

	modbus_set_debug(ctx, (int)debugFlag);

	if(modbus_connect(ctx) == -1) {
		LogMessage(string("libmodbus returned the following error: ") + modbus_strerror(errno)); // WHY is errno a global variable? ...
		modbus_free(ctx);
		return DEVICE_ERR;
	}

	return DEVICE_OK;
}

int CModbusDevice::Initialize() {

	URI conuri(connectionURI);

	if(conuri.protocol == "tcp") {
		int port = MODBUS_TCP_DEFAULT_PORT;
		if(conuri.port != "")
			port = atoi(conuri.port.c_str());

		LogMessage(string("Trying to perform a tcp connection to ") + conuri.hostandaddress + ", port: " + CDeviceUtils::ConvertToString(port));

		ctx = modbus_new_tcp(conuri.hostandaddress.c_str(), port);
	} else if(conuri.protocol == "tcp+pi") {
		if(conuri.port == "")
			conuri.port = "1502";

		LogMessage(string("Trying to perform a tcp+pi connection to ") + conuri.hostandaddress + ", port: " + conuri.port);
		ctx = modbus_new_tcp_pi(conuri.hostandaddress.c_str(), conuri.port.c_str());
	} else if(conuri.protocol == "rtu") {
		int baud = 115200;
		char parity = 'N';
		int data_bit = 8;
		int stop_bit = 1;

		map<string, string>::iterator it;

		if((it = conuri.parameters.find("baud")) != conuri.parameters.end())
			baud = atoi(it->second.c_str());

		if((it = conuri.parameters.find("data_bit")) != conuri.parameters.end())
			data_bit = atoi(it->second.c_str());

		if((it = conuri.parameters.find("stop_bit")) != conuri.parameters.end())
			stop_bit = atoi(it->second.c_str());

		if((it = conuri.parameters.find("parity")) != conuri.parameters.end())
			parity = it->second[0];

      char parityChars[2] = {0, 0};
      parityChars[0] = parity;

		LogMessage(string("Trying to perform a rtu connection via ") + conuri.hostandaddress + " baud: " + CDeviceUtils::ConvertToString(baud) + " parity: " + parityChars + " data_bit: " + CDeviceUtils::ConvertToString(data_bit) + " stop_bit: " + CDeviceUtils::ConvertToString(stop_bit));

		ctx = modbus_new_rtu(conuri.hostandaddress.c_str(), baud, parity, data_bit, stop_bit);

		
	} else {
		LogMessage("Unsupported protocol part passed as connectionURI (supported is tcp, tcp+pi, rtu)");
		return DEVICE_ERR;
	}

	if(checkContextAndContinue() == DEVICE_ERR) {
		return DEVICE_ERR;
	}

	LogMessage("Modbus connected. Adding devices ...");

	pair<string, string> splits, innerSplits, kv;
	splits.second = deviceString;


	//name=valves type=coils write_address=0 read_address=512 count=8



	while((splits=split(splits.second, ";")).first != "") {
		ModbusDevice d;

		innerSplits.second = splits.first;

		while((innerSplits=split(innerSplits.second, " ")).first != "") {
			kv = split(innerSplits.first, "=");
			if(kv.first == "name") {
				d.name = kv.second;
			} else if(kv.first == "type") {
				d.typeStr = kv.second;
				if(kv.second == "coils") {
					d.type = MD_COILS;
				} else if(kv.second == "registers") { 
					LogMessage("Not yet implemented.");
					return DEVICE_ERR;
				} else {
					LogMessage("Unsupported type passed.");
				}
			} else if(kv.first == "read_address") {
				d.read_address = atoi(kv.second.c_str());
			} else if(kv.first == "write_address") {
				d.write_address = atoi(kv.second.c_str());
			} else if(kv.first == "count") {
				d.count = atoi(kv.second.c_str());
			}
		}

		if(d.name != "") {
			deviceConfiguration.push_back(d);
		} else {
			LogMessage("Device without name encountered?! Ignoring.");
		}
	}
	
	CreateProperty("general-connURI", connectionURI.c_str(), MM::String, true, NULL, false);
	CreateProperty("general-libmodbus-version", LIBMODBUS_VERSION_STRING, MM::String, true, NULL, false);


	string devicesToAdd = "";

	for(vector<ModbusDevice>::iterator i = deviceConfiguration.begin(); i != deviceConfiguration.end(); i++) {
		devicesToAdd += "," + i->name;
	}

	devicesToAdd = devicesToAdd.substr(1);
	CreateProperty("general-devices", devicesToAdd.c_str(), MM::String, true, NULL, false);


	int currentNumber = 0;

	largestCount = 0;

	for(vector<ModbusDevice>::iterator i = deviceConfiguration.begin(); i != deviceConfiguration.end(); i++) {
		largestCount = largestCount > i->count ? largestCount : i->count;

		string prefix = "";
		prefix += i->name;

		CreateProperty((prefix + "-name").c_str(), i->name.c_str(), MM::String, true, NULL, false);
		CreateProperty((prefix + "-type").c_str(), i->typeStr.c_str(), MM::String, true, NULL, false);
		CreateProperty((prefix + "-read-address").c_str(), CDeviceUtils::ConvertToString(i->read_address), MM::Integer, true, NULL, false);
		CreateProperty((prefix + "-write-address").c_str(), CDeviceUtils::ConvertToString(i->write_address), MM::Integer, true, NULL, false);
		CreateProperty((prefix + "-count").c_str(), CDeviceUtils::ConvertToString(i->count), MM::Integer, true, NULL, false);

		CreateProperty((prefix).c_str(), "", MM::String, false, new DeviceActionEx(this, &CModbusDevice::OnDeviceValue, currentNumber), false);

		currentNumber ++;
	}

	coilBuffer = new unsigned char[largestCount];
	registerBuffer = new unsigned short[largestCount];

	return DEVICE_OK;
}



int CModbusDevice::OnDeviceValue(MM::PropertyBase *pProp, MM::ActionType eAct, long data) {
	string helper;

	ModbusDevice d = deviceConfiguration[data];

	if(d.type == MD_COILS) {
		memset(coilBuffer, 0, sizeof(unsigned char) * (largestCount));

		if(eAct == MM::BeforeGet)
		{
			int result;
			result = modbus_read_bits(ctx, d.read_address, d.count, coilBuffer);
			for(int i = 0; i < d.count; i++) {
				if(coilBuffer[i]) {
					helper += "1";
				} else {
					helper += "0";
				}
			}
			//
			pProp->Set(helper.c_str());
		}
		else if(eAct == MM::AfterSet)
		{
			pProp->Get(helper);

			if(helper == "off") {
				for(int i = 0; i < d.count; i++) coilBuffer[i] = 0;
			} else if(helper == "on") {
				for(int i = 0; i < d.count; i++) coilBuffer[i] = 1;
			} else {
				if(helper.size() != (unsigned) d.count) {
					LogMessage("ERROR. Malfomed Value set! Rereading from device.");
					return OnDeviceValue(pProp, MM::BeforeGet, data);
				} 

				for(int i = 0; i < d.count; i++) {
					if(helper[i] == '1') {
						coilBuffer[i] = 1;
					} else if(helper[i] == '0') {
						coilBuffer[i] = 0;
					} else {
						LogMessage("ERROR. Malfomed Value set! Rereading from device.");
						return OnDeviceValue(pProp, MM::BeforeGet, data);
					}
				}
			}

			modbus_write_bits(ctx, d.write_address, d.count, coilBuffer);

		}

		return DEVICE_OK;
	} else {
		return DEVICE_ERR;
	}
}

int CModbusDevice::Shutdown() {
	ready = false;

	if(coilBuffer != NULL)
		delete coilBuffer;
	if(registerBuffer != NULL)
		delete registerBuffer;

	return DEVICE_OK;
}
