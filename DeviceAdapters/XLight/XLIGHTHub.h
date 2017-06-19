///////////////////////////////////////////////////////////////////////////////
// FILE:       XLIGHTHub.h
// PROJECT:    Micro-Manager
// SUBSYSTEM:  DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:  CrEST XLight adapter
//                                                                                     
// AUTHOR:        E. Chiarappa echiarappa@libero.it, 01/20/2014
//                Based on CARVII adapter by  G. Esteban Fernandez.
//
// COPYRIGHT:     2014, Crestoptics s.r.l.
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


#ifndef _XLIGHTHUB_H_
#define _XLIGHTHUB_H_

#include <string>
#include <deque>
#include <map>
#include "../../MMDevice/MMDevice.h"

/////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_NOT_CONNECTED           10002
#define ERR_COMMAND_CANNOT_EXECUTE  10003
#define ERR_COMMUNICATION           11000
#define ERR_COMMUNICATION_TIMEOUT   11001
#define ERR_COMMAND_EXECUTION_ERROR 11003

enum CommandMode {
    Sync = 0,
    Async
};

class XLIGHTHub {
public:
    XLIGHTHub();
    ~XLIGHTHub();

    void SetPort(const char* port) {
        port_ = port;
		 
    }
    bool IsBusy();

    int SetDichroicPosition(MM::Device& device, MM::Core& core, int pos, int delay);
    int GetDichroicPositionEcho(MM::Device& device, MM::Core& core, int pos);

	int SetEmissionWheelPosition(MM::Device& device, MM::Core& core, int pos);
    int GetEmissionWheelPosition(MM::Device& device, MM::Core& core, int pos);
 
    int SetSpinMotorState(MM::Device& device, MM::Core& core, int state);
    int GetSpinMotorState(MM::Device& device, MM::Core& core, int state);

    int SetTouchScreenState(MM::Device& device, MM::Core& core, int state);
    int GetTouchScreenState(MM::Device& device, MM::Core& core, int state);

    int SetDiskSliderPosition(MM::Device& device, MM::Core& core, int pos, int delay);
 	int GetXlightCommandEcho(MM::Device& device, MM::Core& core);
 
    int StartSpinningDisk(MM::Device& device, MM::Core& core);

    int LockoutTouchscreen(MM::Device& device, MM::Core& core);
    int ActivateTouchscreen(MM::Device& device, MM::Core& core);

	int ExecuteCommandEx(MM::Device& device, MM::Core& core, const char* command);

	void SetDeviceWait(int delay) { deviceWaitMs_ = delay; }
	int GetDeviceWait(void) { return deviceWaitMs_; }

	void SetCommandTimeout(int to) { commandExecutionTimeoutMs_ = to; }
	int GetCommandTimeout(void) { return commandExecutionTimeoutMs_; }
	void RestoreCommandTimeout(void) { commandExecutionTimeoutMs_ = defaultCmdExecutionTimeout; }
	int GetDevicePosition(void);

private:
    int ExecuteCommand(MM::Device& device, MM::Core& core, const char* command);
    //int GetAcknowledgment(MM::Device& device, MM::Core& core);
    int ParseResponse(const char* cmdId, std::string& value);
    void FetchSerialData(MM::Device& device, MM::Core& core);

	bool enableDeviceWaitByDelay_;
    static const int RCV_BUF_LENGTH = 1024;
    char rcvBuf_[RCV_BUF_LENGTH];
    char asynchRcvBuf_[RCV_BUF_LENGTH];
    void ClearRcvBuf();
    void ClearAllRcvBuf(MM::Device& device, MM::Core& core);

    std::string port_;
    std::vector<char> answerBuf_;
    std::multimap<std::string, long> waitingCommands_;
    std::string commandMode_;
    int deviceWaitMs_;
	static const int defaultCmdExecutionTimeout = 15000; // 15s -- maximum spinning tray path
	int commandExecutionTimeoutMs_;
};

#endif // _XLIGHTHUB_H_
