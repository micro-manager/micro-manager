///////////////////////////////////////////////////////////////////////////////
// FILE:          ModbusModule.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// see ModbusModule.cpp

#ifndef _MODBUS_MODULE_H_
#define _MODBUS_MODULE_H_

#include "../../MMDevice/DeviceBase.h"
#include <string>
#include <sstream>
#include <map>
#include <vector>
#include <algorithm>

#include <errno.h>

#include <modbus.h>

using namespace std;

inline pair<string, string> split(string input, string sep) {
    pair<string, string> result;
    size_t position = input.find(sep);
    if(position == string::npos) {
        result.first = input;
    } else {
        result.first = input.substr(0, position);
        if(position != input.size()) {
            result.second = input.substr(position + sep.size());
        }
    }
    return result;
}

struct URI {
    inline URI(string input) {
        pair<string, string> splitter, innersplitter;
        splitter = split(input, "://");
        protocol = splitter.first;

        splitter = split(splitter.second, "?");

        input = splitter.first;
                
        while((splitter=split(splitter.second, "&")).first.size() > 0) {
            innersplitter = split(splitter.first, "=");
            parameters[innersplitter.first] = innersplitter.second;
        }

        if(input[0] == '[') {
            splitter = split(input.substr(1), "]:");
        } else {
            splitter = split(input, ":");
        }

        port = splitter.second;
        hostandaddress = splitter.first;
    }

    string protocol, hostandaddress, port;
    map<string, string> parameters;
};


typedef enum {
	MD_COILS = 0,
	MD_REGISTER
} ModbusDeviceType;


struct ModbusDevice {
	string name;
	int read_address;
	int write_address;
	int write_only;
	int read_only;
	ModbusDeviceType type;
	string typeStr;
	int count;

	string valueCache;

	inline ModbusDevice() : read_address(0), write_address(0), type(MD_COILS), count(0), write_only(0), read_only(0) {};
};

class CModbusDevice: public CGenericBase<CModbusDevice>
{
public:
	CModbusDevice();
	~CModbusDevice();

	int Initialize();
	int Shutdown();

	void GetName(char* pszName) const;
	bool Busy();

	int OnConnectionURI(MM::PropertyBase *, MM::ActionType eAct);
	int OnDeviceString(MM::PropertyBase *, MM::ActionType eAct);
	int OnDebugFlag(MM::PropertyBase *pProp, MM::ActionType eAct);

	int OnDeviceValue(MM::PropertyBase *pProp, MM::ActionType eAct, long data);

private:

	int checkContextAndContinue();

	modbus_t *ctx;

	string connectionURI;
	string deviceString;

	
	vector<ModbusDevice> deviceConfiguration;

	int ready;

	long debugFlag;

	int retriesLeft;
	long retries;

	int largestCount;
	unsigned char *coilBuffer;
	unsigned short *registerBuffer;

};


typedef MM::ActionEx<CModbusDevice> DeviceActionEx;
typedef MM::Action<CModbusDevice> DeviceAction;

#endif //_MODBUS_MODULE_H_
