///////////////////////////////////////////////////////////////////////////////
// FILE:          Zaber.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Zaber Controller Driver
//                
// AUTHOR:        David Goosen & Athabasca Witschi (contact@zaber.com)
//                
// COPYRIGHT:     Zaber Technologies, 2014
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

#ifndef _ZABER_H_
#define _ZABER_H_

#include <MMDevice.h>
#include <DeviceBase.h>
#include <ModuleInterface.h>
#include <sstream>
#include <string>

//////////////////////////////////////////////////////////////////////////////
// Various constants: error codes, error messages
//////////////////////////////////////////////////////////////////////////////

#define ERR_PORT_CHANGE_FORBIDDEN    10002
#define ERR_DRIVER_DISABLED          10004
#define ERR_BUSY_TIMEOUT             10008
#define ERR_AXIS_COUNT               10016
#define ERR_COMMAND_REJECTED         10032
#define	ERR_NO_REFERENCE_POS         10064
#define	ERR_SETTING_FAILED           10128

extern const char* g_Msg_PORT_CHANGE_FORBIDDEN;
extern const char* g_Msg_DRIVER_DISABLED;
extern const char* g_Msg_BUSY_TIMEOUT;
extern const char* g_Msg_AXIS_COUNT;
extern const char* g_Msg_COMMAND_REJECTED;
extern const char* g_Msg_NO_REFERENCE_POS;
extern const char* g_Msg_SETTING_FAILED;

// N.B. Concrete device classes deriving ZaberBase must set core_ in
// Initialize().
class ZaberBase
{
public:
	ZaberBase(MM::Device *device);
	virtual ~ZaberBase();

protected:
	int ClearPort() const;
	int SendCommand(const std::string command) const;
	int QueryCommand(const std::string command, std::vector<std::string>& reply) const;
	int GetSetting(long device, long axis, std::string setting, long& data) const;
	int SetSetting(long device, long axis, std::string setting, long data) const;
	bool IsBusy(long device) const;
	int Stop(long device) const;
	int GetLimits(long device, long axis, long& min, long& max) const;
	int SendMoveCommand(long device, long axis, std::string type, long data) const;
	int SendAndPollUntilIdle(long device, long axis, std::string command, int timeoutMs) const;

	bool initialized_;
	std::string port_;
	MM::Device *device_;
	MM::Core *core_;
	std::string cmdPrefix_;
};

#endif //_ZABER_H_
