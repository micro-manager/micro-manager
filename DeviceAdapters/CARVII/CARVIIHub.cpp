///////////////////////////////////////////////////////////////////////////////
// FILE:       CARVIIHub.cpp
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


#define _CRT_SECURE_NO_DEPRECATE

#include "CARVIIHub.h"
#include "assert.h"
#include <memory.h>
#include <sstream>
#include <iostream>

#ifdef WIN32
#include <windows.h>
#define usleep(us) Sleep(us/1000) 
#endif

using namespace std;

CARVIIHub::CARVIIHub() {
    expireTimeUs_ = 5000000; // each command will finish within 5sec

    // CARVII does not confirm successful device movement; wait time
    // in microsec to ensure movement has ended
    deviceWait_ = 20000;

    ClearRcvBuf();
}

CARVIIHub::~CARVIIHub() {
}


///////////////////////////////////////////////////////////////////////////////
// Device commands
///////////////////////////////////////////////////////////////////////////////

/* Shutter
 * 0 = closed
 * 1 = open
 */

int CARVIIHub::SetShutterPosition(MM::Device& device, MM::Core& core, int pos) {
    ostringstream os;
    os << "S" << pos;
    return ExecuteCommand(device, core, os.str().c_str());
}

int CARVIIHub::GetShutterPosition(MM::Device& device, MM::Core& core, int pos) {
    ClearAllRcvBuf(device, core);
    int ret = ExecuteCommand(device, core, "rS");
    // analyze what comes back:
    ret = core.GetSerialAnswer(&device, port_.c_str(), RCV_BUF_LENGTH, rcvBuf_, "\r");
    if (ret != DEVICE_OK)
        return ret;
    //CARVII echoes command, shutter position is in bit 3
    pos = rcvBuf_[2];

    return DEVICE_OK;
}

int CARVIIHub::SetExFilterPosition(MM::Device& device, MM::Core& core, int pos) {
    int posCommand = pos + 1; //MM positions start at 0, CARVII pos start at 1
    ostringstream os;
    os << "A" << posCommand;
    bool succeeded = false;
    int counter = 0;
    int ret;

    // try up to 10 times
    while (!succeeded && counter < 10) {
        ret = ExecuteCommand(device, core, os.str().c_str());

        //to ensure wheel finishes movement, CARVII does not signal movement succeeded
        usleep(deviceWait_);

        if (ret != DEVICE_OK)
            counter++;
        else
            succeeded = true;
    }
    if (!succeeded)
        return ret;

    return DEVICE_OK;
}

int CARVIIHub::GetExFilterPosition(MM::Device& device, MM::Core& core, int pos) {
    ClearAllRcvBuf(device, core);
    int ret = ExecuteCommand(device, core, "rA");
    ret = core.GetSerialAnswer(&device, port_.c_str(), RCV_BUF_LENGTH, rcvBuf_, "\r");
    if (ret != DEVICE_OK)
        return ret;

    // CARVII echoes command, filter position is in bit 3
    pos = rcvBuf_[2];

    return DEVICE_OK;
}

int CARVIIHub::SetEmFilterPosition(MM::Device& device, MM::Core& core, int pos) {
    int posCommand = pos + 1; //MM positions start at 0, CARVII pos start at 1
    ostringstream os;
    os << "B" << posCommand;
    bool succeeded = false;
    int counter = 0;
    int ret;

    // try up to 10 times
    while (!succeeded && counter < 10) {
        ret = ExecuteCommand(device, core, os.str().c_str());

        //to ensure wheel finishes movement, CARVII does not signal movement succeeded
        usleep(deviceWait_);

        if (ret != DEVICE_OK)
            counter++;
        else
            succeeded = true;
    }
    if (!succeeded)
        return ret;

    return DEVICE_OK;
}

int CARVIIHub::GetEmFilterPosition(MM::Device& device, MM::Core& core, int pos) {
    ClearAllRcvBuf(device, core);
    int ret = ExecuteCommand(device, core, "rB");
    ret = core.GetSerialAnswer(&device, port_.c_str(), RCV_BUF_LENGTH, rcvBuf_, "\r");
    if (ret != DEVICE_OK)
        return ret;

    // CARVII echoes command, filter position is in bit 3
    pos = rcvBuf_[2];

    return DEVICE_OK;
}

int CARVIIHub::SetDichroicPosition(MM::Device& device, MM::Core& core, int pos) {
    int posCommand = pos + 1; //MM positions start at 0, CARVII pos start at 1
    ostringstream os;
    os << "C" << posCommand;
    bool succeeded = false;
    int counter = 0;
    int ret;

    // try up to 10 times
    while (!succeeded && counter < 10) {
        ret = ExecuteCommand(device, core, os.str().c_str());

        //to ensure wheel finishes movement, CARVII does not signal movement succeeded
        usleep(deviceWait_);

        if (ret != DEVICE_OK)
            counter++;
        else
            succeeded = true;
    }
    if (!succeeded)
        return ret;

    return DEVICE_OK;
}

int CARVIIHub::GetDichroicPosition(MM::Device& device, MM::Core& core, int pos) {
    ClearAllRcvBuf(device, core);
    int ret = ExecuteCommand(device, core, "rC");
    ret = core.GetSerialAnswer(&device, port_.c_str(), RCV_BUF_LENGTH, rcvBuf_, "\r");
    if (ret != DEVICE_OK)
        return ret;

    // CARVII echoes command, dichroic position is in bit 3
    pos = rcvBuf_[2];

    return DEVICE_OK;
}

int CARVIIHub::SetFRAPIrisPosition(MM::Device& device, MM::Core& core, int pos) {
    ostringstream os;
    os << "I" << pos;
    bool succeeded = false;
    int counter = 0;

    int ret;

    // try up to 10 times
    while (!succeeded && counter < 10) {
        ret = ExecuteCommand(device, core, os.str().c_str());

        //to ensure wheel finishes movement, CARVII does not signal movement succeeded
        usleep(deviceWait_);

        if (ret != DEVICE_OK)
            counter++;
        else
            succeeded = true;
    }
    if (!succeeded)
        return ret;

    return DEVICE_OK;
}

int CARVIIHub::GetFRAPIrisPosition(MM::Device& device, MM::Core& core, int pos) {
    ClearAllRcvBuf(device, core);
    int ret = ExecuteCommand(device, core, "rI");
    ret = core.GetSerialAnswer(&device, port_.c_str(), RCV_BUF_LENGTH, rcvBuf_, "\r");
    if (ret != DEVICE_OK)
        return ret;

    // CARVII echoes command, iris position is in bits 3-6
    pos = rcvBuf_[3]*100 + rcvBuf_[4]*10 + rcvBuf_[5];

    // bit 3 is blank for positions <1000, equals 1 for 1000 and above
    if (rcvBuf_[2] == 1)
        pos += 1000;

    return DEVICE_OK;
}

int CARVIIHub::SetIntensityIrisPosition(MM::Device& device, MM::Core& core, int pos) {
    ostringstream os;
    os << "V" << pos;
    bool succeeded = false;
    int counter = 0;

    int ret;

    // try up to 10 times
    while (!succeeded && counter < 10) {
        ret = ExecuteCommand(device, core, os.str().c_str());

        //to ensure wheel finishes movement, CARVII does not signal movement succeeded
        usleep(deviceWait_);

        if (ret != DEVICE_OK)
            counter++;
        else
            succeeded = true;
    }
    if (!succeeded)
        return ret;

    return DEVICE_OK;
}

int CARVIIHub::GetIntensityIrisPosition(MM::Device& device, MM::Core& core, int pos) {
    ClearAllRcvBuf(device, core);
    int ret = ExecuteCommand(device, core, "rV");
    ret = core.GetSerialAnswer(&device, port_.c_str(), RCV_BUF_LENGTH, rcvBuf_, "\r");
    if (ret != DEVICE_OK)
        return ret;

    // CARVII echoes command, iris position is in bits 3-6
    pos = rcvBuf_[3]*100 + rcvBuf_[4]*10 + rcvBuf_[5];

    // bit 3 is blank for positions <1000, equals 1 for 1000 and above
    if (rcvBuf_[2] == 1)
        pos += 1000;

    return DEVICE_OK;
}

int CARVIIHub::SetSpinMotorState(MM::Device& device, MM::Core& core, int state) {
    ostringstream os;
    os << "N" << state;
    bool succeeded = false;
    int counter = 0;

    int ret;

    // try up to 10 times
    while (!succeeded && counter < 10) {
        ret = ExecuteCommand(device, core, os.str().c_str());

        //to ensure wheel finishes movement, CARVII does not signal movement succeeded
        usleep(deviceWait_);

        if (ret != DEVICE_OK)
            counter++;
        else
            succeeded = true;
    }
    if (!succeeded)
        return ret;

    return DEVICE_OK;
}

int CARVIIHub::GetSpinMotorState(MM::Device& device, MM::Core& core, int state) {
    ClearAllRcvBuf(device, core);
    int ret = ExecuteCommand(device, core, "rN");
    ret = core.GetSerialAnswer(&device, port_.c_str(), RCV_BUF_LENGTH, rcvBuf_, "\r");
    if (ret != DEVICE_OK)
        return ret;

    // CARVII echoes command, motor state is in bit 3
    state = rcvBuf_[2];

    return DEVICE_OK;
}

int CARVIIHub::SetTouchScreenState(MM::Device& device, MM::Core& core, int state) {
    ostringstream os;
    os << "M" << state;
    bool succeeded = false;
    int counter = 0;

    int ret;

    // try up to 10 times, wait 50 ms in between tries
    while (!succeeded && counter < 10) {
        ret = ExecuteCommand(device, core, os.str().c_str());
        if (ret != DEVICE_OK) {
            usleep(50000);
            counter++;
        } else
            succeeded = true;
    }
    if (!succeeded)
        return ret;
    else return DEVICE_OK;
}

int CARVIIHub::GetTouchScreenState(MM::Device& device, MM::Core& core, int state) {
    ClearAllRcvBuf(device, core);
    int ret = ExecuteCommand(device, core, "rM");
    ret = core.GetSerialAnswer(&device, port_.c_str(), RCV_BUF_LENGTH, rcvBuf_, "\r");
    if (ret != DEVICE_OK)
        return ret;

    // CARVII echoes command, motor state is in bit 3
    state = rcvBuf_[2];

    return DEVICE_OK;
}

int CARVIIHub::SetDiskSliderPosition(MM::Device& device, MM::Core& core, int pos) {
    ostringstream os;
    os << "D" << pos;
    bool succeeded = false;
    int counter = 0;

    int ret;

    // try up to 10 times
    while (!succeeded && counter < 10) {
        ret = ExecuteCommand(device, core, os.str().c_str());

        //to ensure wheel finishes movement, CARVII does not signal movement succeeded
        usleep(deviceWait_);

        if (ret != DEVICE_OK)
            counter++;
        else
            succeeded = true;
    }
    if (!succeeded)
        return ret;

    return DEVICE_OK;
}

int CARVIIHub::GetDiskSliderPosition(MM::Device& device, MM::Core& core, int pos) {
    ClearAllRcvBuf(device, core);
    int ret = ExecuteCommand(device, core, "rD");
    ret = core.GetSerialAnswer(&device, port_.c_str(), RCV_BUF_LENGTH, rcvBuf_, "\r");
    if (ret != DEVICE_OK)
        return ret;

    // CARVII echoes command, motor state is in bit 3
    pos = rcvBuf_[2];

    return DEVICE_OK;
}

int CARVIIHub::SetPrismSliderPosition(MM::Device& device, MM::Core& core, int pos) {
    ostringstream os;
    os << "P" << pos;
    bool succeeded = false;
    int counter = 0;

    int ret;

    // try up to 10 times
    while (!succeeded && counter < 10) {
        ret = ExecuteCommand(device, core, os.str().c_str());

        //to ensure wheel finishes movement, CARVII does not signal movement succeeded
        usleep(deviceWait_);

        if (ret != DEVICE_OK)
            counter++;
        else
            succeeded = true;
    }
    if (!succeeded)
        return ret;

    return DEVICE_OK;
}

int CARVIIHub::GetPrismSliderPosition(MM::Device& device, MM::Core& core, int pos) {
    ClearAllRcvBuf(device, core);
    int ret = ExecuteCommand(device, core, "rP");
    ret = core.GetSerialAnswer(&device, port_.c_str(), RCV_BUF_LENGTH, rcvBuf_, "\r");
    if (ret != DEVICE_OK)
        return ret;

    // CARVII echoes command, motor state is in bit 3
    pos = rcvBuf_[2];

    return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// HUB generic methods
///////////////////////////////////////////////////////////////////////////////

/**
 * Clears the serial receive buffer.
 */
void CARVIIHub::ClearAllRcvBuf(MM::Device& device, MM::Core& core) {
    // Read whatever has been received so far:
    unsigned long read;
    core.ReadFromSerial(&device, port_.c_str(), (unsigned char*) rcvBuf_, RCV_BUF_LENGTH, read);
    // Delete it all:
    memset(rcvBuf_, 0, RCV_BUF_LENGTH);
}

void CARVIIHub::ClearRcvBuf() {
    memset(rcvBuf_, 0, RCV_BUF_LENGTH);
}

/**
 * Buys flag. True if the command processing is in progress.
 */
bool CARVIIHub::IsBusy() {
    return waitingCommands_.size() != 0;
}

int CARVIIHub::StartSpinningDisk(MM::Device& device, MM::Core& core) {
    //D1 slides disk into light path, N1 starts motor spinning
    return ExecuteCommand(device, core, "D1N1");
}

int CARVIIHub::LockoutTouchscreen(MM::Device& device, MM::Core& core) {
    //Freezes touch screen to prevent catastrophic crash by simultaneous serial
    //and screen control
    return ExecuteCommand(device, core, "M1");
}

int CARVIIHub::ActivateTouchscreen(MM::Device& device, MM::Core& core) {
    //Activates touchscreen control
    return ExecuteCommand(device, core, "M0");
}

/**
 * Sends serial command to the MMCore virtual serial port.
 */
int CARVIIHub::ExecuteCommand(MM::Device& device, MM::Core& core, const char* command) {
    // empty the Rx serial buffer before sending command
    //FetchSerialData(device, core);

    ClearAllRcvBuf(device, core);

    // send command
    return core.SetSerialCommand(&device, port_.c_str(), command, "\r");

}