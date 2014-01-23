///////////////////////////////////////////////////////////////////////////////
// FILE:       XLIGHTHub.cpp
// PROJECT:    Micro-Manager
// SUBSYSTEM:  DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   CrEST XLight adapter
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


#define _CRT_SECURE_NO_DEPRECATE

#include "XLIGHTHub.h"
#include "assert.h"
#include <memory.h>
#include <sstream>
#include <iostream>

#include "../../MMDevice/DeviceUtils.h"

using namespace std;

XLIGHTHub::XLIGHTHub() {
    // CARVII does not confirm successful device movement; wait time
    // in millisec to ensure movement has ended
    deviceWaitMs_ = 20;

    ClearRcvBuf();
}

XLIGHTHub::~XLIGHTHub() {
}


///////////////////////////////////////////////////////////////////////////////
// Device commands
///////////////////////////////////////////////////////////////////////////////

/* SpeckleReducer
 * 0 = closed
 * 1 = open
 */

int XLIGHTHub::SetSpeckleReducerPosition(MM::Device& device, MM::Core& core, int pos) {
    ostringstream os;
    os << "L" << pos;
	bool succeeded = false;
    int counter = 0;
    int ret = DEVICE_OK;
    //return ExecuteCommand(device, core, os.str().c_str());
	// try up to 10 times
    while (!succeeded && counter < 10) {
        ret = ExecuteCommand(device, core, os.str().c_str());

        //to ensure wheel finishes movement, CARVII does not signal movement succeeded
        CDeviceUtils::SleepMs(deviceWaitMs_);

        if (ret != DEVICE_OK)
            counter++;
        else
            succeeded = true;
    }
    if (!succeeded)
        return ret;

    return DEVICE_OK;
}

int XLIGHTHub::GetSpeckleReducerPosition(MM::Device& device, MM::Core& core, int pos) {
    ClearAllRcvBuf(device, core);
    int ret = ExecuteCommand(device, core, "rL");
    // analyze what comes back:
    ret = core.GetSerialAnswer(&device, port_.c_str(), RCV_BUF_LENGTH, rcvBuf_, "\r");
    if (ret != DEVICE_OK)
        return ret;
    //CARVII echoes command, SpeckleReducer position is in bit 3
    pos = rcvBuf_[2];

    return DEVICE_OK;
}

 
 
int XLIGHTHub::SetDichroicPosition(MM::Device& device, MM::Core& core, int pos) {
    int posCommand = pos + 1; //MM positions start at 0, CARVII pos start at 1
    ostringstream os;
    os << "C" << posCommand;
    bool succeeded = false;
    int counter = 0;
    int ret = DEVICE_OK;

    // try up to 10 times
    while (!succeeded && counter < 10) {
        ret = ExecuteCommand(device, core, os.str().c_str());

        //to ensure wheel finishes movement, CARVII does not signal movement succeeded
        CDeviceUtils::SleepMs(deviceWaitMs_);

        if (ret != DEVICE_OK)
            counter++;
        else
            succeeded = true;
    }
    if (!succeeded)
        return ret;

    return DEVICE_OK;
}

int XLIGHTHub::GetDichroicPosition(MM::Device& device, MM::Core& core, int pos) {
    ClearAllRcvBuf(device, core);
    int ret = ExecuteCommand(device, core, "rC");
    ret = core.GetSerialAnswer(&device, port_.c_str(), RCV_BUF_LENGTH, rcvBuf_, "\r");
    if (ret != DEVICE_OK)
        return ret;

    // CARVII echoes command, dichroic position is in bit 3
    pos = rcvBuf_[2];

    return DEVICE_OK;
}

int XLIGHTHub::SetEmissionWheelPosition(MM::Device& device, MM::Core& core, int pos) {
    int posCommand = pos + 1; //MM positions start at 0, CARVII pos start at 1
    ostringstream os;
    os << "E" << posCommand;
    bool succeeded = false;
    int counter = 0;
    int ret = DEVICE_OK;

    // try up to 10 times
    while (!succeeded && counter < 10) {
        ret = ExecuteCommand(device, core, os.str().c_str());

        //to ensure wheel finishes movement, CARVII does not signal movement succeeded
        CDeviceUtils::SleepMs(deviceWaitMs_);

        if (ret != DEVICE_OK)
            counter++;
        else
            succeeded = true;
    }
    if (!succeeded)
        return ret;

    return DEVICE_OK;
}

int XLIGHTHub::GetEmissionWheelPosition(MM::Device& device, MM::Core& core, int pos) {
    ClearAllRcvBuf(device, core);
    int ret = ExecuteCommand(device, core, "rE");
    ret = core.GetSerialAnswer(&device, port_.c_str(), RCV_BUF_LENGTH, rcvBuf_, "\r");
    if (ret != DEVICE_OK)
        return ret;

    // CARVII echoes command, dichroic position is in bit 3
    pos = rcvBuf_[2];

    return DEVICE_OK;
}
 
int XLIGHTHub::SetSpinMotorState(MM::Device& device, MM::Core& core, int state) {
    ostringstream os;
    os << "N" << state;
    bool succeeded = false;
    int counter = 0;

    int ret = DEVICE_OK;

    // try up to 10 times
    while (!succeeded && counter < 10) {
        ret = ExecuteCommand(device, core, os.str().c_str());

        //to ensure wheel finishes movement, CARVII does not signal movement succeeded
        CDeviceUtils::SleepMs(deviceWaitMs_);

        if (ret != DEVICE_OK)
            counter++;
        else
            succeeded = true;
    }
    if (!succeeded)
        return ret;

    return DEVICE_OK;
}

int XLIGHTHub::GetSpinMotorState(MM::Device& device, MM::Core& core, int state) {
    ClearAllRcvBuf(device, core);
    int ret = ExecuteCommand(device, core, "rN");
    ret = core.GetSerialAnswer(&device, port_.c_str(), RCV_BUF_LENGTH, rcvBuf_, "\r");
    if (ret != DEVICE_OK)
        return ret;

    // CARVII echoes command, motor state is in bit 3
    state = rcvBuf_[2];

    return DEVICE_OK;
}

int XLIGHTHub::SetTouchScreenState(MM::Device& device, MM::Core& core, int state) {
    ostringstream os;
    os << "M" << state;
    bool succeeded = false;
    int counter = 0;

    int ret = DEVICE_OK;

    // try up to 10 times, wait 50 ms in between tries
    while (!succeeded && counter < 10) {
        ret = ExecuteCommand(device, core, os.str().c_str());
        if (ret != DEVICE_OK) {
            CDeviceUtils::SleepMs(50);
            counter++;
        } else
            succeeded = true;
    }
    if (!succeeded)
        return ret;
    else return DEVICE_OK;
}

int XLIGHTHub::GetTouchScreenState(MM::Device& device, MM::Core& core, int state) {
    ClearAllRcvBuf(device, core);
    int ret = ExecuteCommand(device, core, "rM");
    ret = core.GetSerialAnswer(&device, port_.c_str(), RCV_BUF_LENGTH, rcvBuf_, "\r");
    if (ret != DEVICE_OK)
        return ret;

    // CARVII echoes command, motor state is in bit 3
    state = rcvBuf_[2];

    return DEVICE_OK;
}

int XLIGHTHub::SetDiskSliderPosition(MM::Device& device, MM::Core& core, int pos) {
    ostringstream os;
    os << "D" << pos;
    bool succeeded = false;
    int counter = 0;

    int ret = DEVICE_OK;

    // try up to 10 times
    while (!succeeded && counter < 10) {
        ret = ExecuteCommand(device, core, os.str().c_str());

        //to ensure wheel finishes movement, CARVII does not signal movement succeeded
        CDeviceUtils::SleepMs(deviceWaitMs_);

        if (ret != DEVICE_OK)
            counter++;
        else 
            succeeded = true;
    }
    if (!succeeded)
        return ret;

    return DEVICE_OK;
}

int XLIGHTHub::GetDiskSliderPosition(MM::Device& device, MM::Core& core, int pos) {
    ClearAllRcvBuf(device, core);
    int ret = ExecuteCommand(device, core, "rD");
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
void XLIGHTHub::ClearAllRcvBuf(MM::Device& device, MM::Core& core) {
    // Read whatever has been received so far:
    unsigned long read;
    core.ReadFromSerial(&device, port_.c_str(), (unsigned char*) rcvBuf_, RCV_BUF_LENGTH, read);
    // Delete it all:
    memset(rcvBuf_, 0, RCV_BUF_LENGTH);
}

void XLIGHTHub::ClearRcvBuf() {
    memset(rcvBuf_, 0, RCV_BUF_LENGTH);
}

/**
 * Buys flag. True if the command processing is in progress.
 */
bool XLIGHTHub::IsBusy() {
    return waitingCommands_.size() != 0;
}

int XLIGHTHub::StartSpinningDisk(MM::Device& device, MM::Core& core) {
    //D1 slides disk into light path, N1 starts motor spinning
    return ExecuteCommand(device, core, "D1N1");
}

int XLIGHTHub::LockoutTouchscreen(MM::Device& device, MM::Core& core) {
    //Freezes touch screen to prevent catastrophic crash by simultaneous serial
    //and screen control
    return ExecuteCommand(device, core, "M1");
}

int XLIGHTHub::ActivateTouchscreen(MM::Device& device, MM::Core& core) {
    //Activates touchscreen control
    return ExecuteCommand(device, core, "M0");
}

/**
 * Sends serial command to the MMCore virtual serial port.
 */
int XLIGHTHub::ExecuteCommand(MM::Device& device, MM::Core& core, const char* command) {
    // empty the Rx serial buffer before sending command
    //FetchSerialData(device, core);

    ClearAllRcvBuf(device, core);

    // send command
    return core.SetSerialCommand(&device, port_.c_str(), command, "\r");
 
}