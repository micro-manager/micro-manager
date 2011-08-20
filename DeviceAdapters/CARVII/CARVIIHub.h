///////////////////////////////////////////////////////////////////////////////
// FILE:       CARVIIHub.h
// PROJECT:    Micro-Manager
// SUBSYSTEM:  DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   BD/CrEST CARVII adapter
//                                                                                     
// AUTHOR:        G. Esteban Fernandez, g.esteban.fernandez@gmail.com, 08/19/2011
//                Based on CSU22 and LeicaDMR adapters by Nico Stuurman.
//
// COPYRIGHT:     2011, Children's Hospital Los Angeles
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


#ifndef _CARVIIHUB_H_
#define _CARVIIHUB_H_

#include <string>
#include <deque>
#include <map>
#include "../../MMDevice/MMDevice.h"

/////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_NOT_CONNECTED           10002
#define ERR_COMMAND_CANNOT_EXECUTE  10003

enum CommandMode {
    Sync = 0,
    Async
};

class CARVIIHub {
public:
    CARVIIHub();
    ~CARVIIHub();

    void SetPort(const char* port) {
        port_ = port;
    }
    bool IsBusy();

    int SetShutterPosition(MM::Device& device, MM::Core& core, int pos);
    int GetShutterPosition(MM::Device& device, MM::Core& core, int pos);

    int SetExFilterPosition(MM::Device& device, MM::Core& core, int pos);
    int GetExFilterPosition(MM::Device& device, MM::Core& core, int pos);

    int SetDichroicPosition(MM::Device& device, MM::Core& core, int pos);
    int GetDichroicPosition(MM::Device& device, MM::Core& core, int pos);

    int SetEmFilterPosition(MM::Device& device, MM::Core& core, int pos);
    int GetEmFilterPosition(MM::Device& device, MM::Core& core, int pos);

    int SetFRAPIrisPosition(MM::Device& device, MM::Core& core, int pos);
    int GetFRAPIrisPosition(MM::Device& device, MM::Core& core, int pos);

    int SetIntensityIrisPosition(MM::Device& device, MM::Core& core, int pos);
    int GetIntensityIrisPosition(MM::Device& device, MM::Core& core, int pos);

    int SetSpinMotorState(MM::Device& device, MM::Core& core, int state);
    int GetSpinMotorState(MM::Device& device, MM::Core& core, int state);

    int SetTouchScreenState(MM::Device& device, MM::Core& core, int state);
    int GetTouchScreenState(MM::Device& device, MM::Core& core, int state);

    int SetDiskSliderPosition(MM::Device& device, MM::Core& core, int pos);
    int GetDiskSliderPosition(MM::Device& device, MM::Core& core, int pos);

    int SetPrismSliderPosition(MM::Device& device, MM::Core& core, int pos);
    int GetPrismSliderPosition(MM::Device& device, MM::Core& core, int pos);

    int StartSpinningDisk(MM::Device& device, MM::Core& core);

    int LockoutTouchscreen(MM::Device& device, MM::Core& core);
    int ActivateTouchscreen(MM::Device& device, MM::Core& core);

private:
    int ExecuteCommand(MM::Device& device, MM::Core& core, const char* command);
    //int GetAcknowledgment(MM::Device& device, MM::Core& core);
    int ParseResponse(const char* cmdId, std::string& value);
    void FetchSerialData(MM::Device& device, MM::Core& core);

    static const int RCV_BUF_LENGTH = 1024;
    char rcvBuf_[RCV_BUF_LENGTH];
    char asynchRcvBuf_[RCV_BUF_LENGTH];
    void ClearRcvBuf();
    void ClearAllRcvBuf(MM::Device& device, MM::Core& core);
    std::string port_;
    std::vector<char> answerBuf_;
    std::multimap<std::string, long> waitingCommands_;
    long expireTimeUs_;
    std::string commandMode_;
    int deviceWait_;
};

#endif // _CARVIIHUB_H_
