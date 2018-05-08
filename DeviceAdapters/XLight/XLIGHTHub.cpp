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
	enableDeviceWaitByDelay_ = false;
	commandExecutionTimeoutMs_ = defaultCmdExecutionTimeout;

    ClearRcvBuf();
}

XLIGHTHub::~XLIGHTHub() {
}


///////////////////////////////////////////////////////////////////////////////
// Device commands
///////////////////////////////////////////////////////////////////////////////

#if 0 
int XLIGHTHub::SetDichroicPosition(MM::Device& device, MM::Core& core, int pos, int delay) {
    int posCommand = pos + 1; //MM positions start at 0, CARVII pos start at 1
    ostringstream os;
    os << "C" << posCommand;
    bool succeeded = false;
    //int counter = 0;
    int ret = DEVICE_OK;
	deviceWaitMs_ = delay;

#if 0 
	 while (!succeeded ) {
        ret = ExecuteCommand(device, core, os.str().c_str());

        //to ensure wheel finishes movement, query the serial line waiting for echoed 
		// command executed which means XLight accomplished the movement
        
		//time_t now = time(0);
		//char* dt = ctime(&now);
		GetXlightCommandEcho(device,core);
		
       if (ret != DEVICE_OK)
            continue;
        else {
		 
			CDeviceUtils::SleepMs(deviceWaitMs_);
			succeeded = true;
		}
    }
    if (!succeeded)
        return ret;
#endif

	ret = ExecuteCommandEx(device, core, os.str().c_str());

    return ret;
}
#endif


#if 0
int XLIGHTHub::GetDichroicPositionEcho(MM::Device& device, MM::Core& core, int pos) {
    ClearAllRcvBuf(device, core);
    int ret = ExecuteCommand(device, core, "rC");
    ret = core.GetSerialAnswer(&device, port_.c_str(), RCV_BUF_LENGTH, rcvBuf_, "\r");
    if (ret != DEVICE_OK)
        return ret;

     pos = rcvBuf_[2];

    return DEVICE_OK;
}
#endif

#if 0
int XLIGHTHub::SetEmissionWheelPosition(MM::Device& device, MM::Core& core, int pos) {
    int posCommand = pos + 1; //MM positions start at 0, CARVII pos start at 1
    ostringstream os;
    os << "B" << posCommand;
    bool succeeded = false;
    //int counter = 0;  
    int ret = DEVICE_OK;

#if 0
    // try up to 10 times
    while (!succeeded) {
        ret = ExecuteCommand(device, core, os.str().c_str());

        //to ensure wheel finishes movement, CARVII does not signal movement succeeded
       GetXlightCommandEcho(device,core);
		

        if (ret != DEVICE_OK)
            continue;
        else{
			CDeviceUtils::SleepMs(deviceWaitMs_);
			succeeded = true;}
    }
    if (!succeeded)
        return ret;
#endif

	ret = ExecuteCommandEx(device, core, os.str().c_str());

    return ret;
}
#endif


#if 0
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
#endif


int XLIGHTHub::SetSpinMotorState(MM::Device& device, MM::Core& core, int state) {
    ostringstream os;
    os << "N" << state;
    bool succeeded = false;
    int counter = 0;
    int ret = DEVICE_OK;
	deviceWaitMs_ = 15;
#if 0
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
#endif
	ret = ExecuteCommandEx(device, core, os.str().c_str());
    return ret;
}

#if 0
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
#endif


int XLIGHTHub::SetTouchScreenState(MM::Device& device, MM::Core& core, int state) {
    ostringstream os;
    os << "M" << state;
    bool succeeded = false;
    int counter = 0;

    int ret = DEVICE_OK;
#if 0
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
#endif

	ret = ExecuteCommandEx(device, core, os.str().c_str());
    return ret;
}
#if 0
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
#endif


#if 0
int XLIGHTHub::SetDiskSliderPosition(MM::Device& device, MM::Core& core, int pos, int delay) {
	
    deviceWaitMs_ = delay;
	ostringstream os;
    os << "D" << pos;
    bool succeeded = false;
 
    int ret = DEVICE_OK;
      // try up to 10 times
 	 while (!succeeded ) {
        ret = ExecuteCommand(device, core, os.str().c_str());

        //to ensure wheel finishes movement, query the serial line waiting for echoed 
		// command executed which means XLight accomplished the movement
        
		//time_t now = time(0);
		//char* dt = ctime(&now);
		GetXlightCommandEcho(device,core);
		
       if (ret != DEVICE_OK)
            continue;
        else {
		 
			CDeviceUtils::SleepMs(deviceWaitMs_);
			succeeded = true;
		}
    }
    if (!succeeded)
        return ret;
	 
	return DEVICE_OK;
}
#endif
#if 0
int XLIGHTHub::GetXlightCommandEcho(MM::Device& device, MM::Core& core) {
     ClearAllRcvBuf(device, core);
	 int ret = 0;
   

    // XLight echoes command, motor state starting from bit 0
    
	 
	while (rcvBuf_[0] == 0)
	{
		ret = core.GetSerialAnswer(&device, port_.c_str(), RCV_BUF_LENGTH, rcvBuf_, "\r");
	 
	}
	 if (rcvBuf_[0] == 68 || rcvBuf_[0] == 67){
		 //time_t after = time(0);
		//char* dt = ctime(&after);
		 return DEVICE_OK;}
	 else 
		 return ret;
}
#endif

 
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
    //return ExecuteCommand(device, core, "D1N1"); // DEPRECATED
	int ret = DEVICE_OK;
	ret = ExecuteCommandEx(device, core, "D1");
	if (DEVICE_OK != ret)
		return ret;
	ret = ExecuteCommandEx(device, core, "N1");
	return ret;
}

int XLIGHTHub::LockoutTouchscreen(MM::Device& device, MM::Core& core) {
    //Freezes touch screen to prevent catastrophic crash by simultaneous serial
    //and screen control
    //return ExecuteCommand(device, core, "M1");
	return ExecuteCommandEx(device, core, "M1");
}

int XLIGHTHub::ActivateTouchscreen(MM::Device& device, MM::Core& core) {
    //Activates touchscreen control
    //return ExecuteCommand(device, core, "M0");
	return ExecuteCommandEx(device, core, "M0");
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

int XLIGHTHub::GetDevicePosition(void)
{
	return (rcvBuf_[2]-'0');
}

/**
 * Sends serial command to the MMCore virtual serial port and waits for device echo.
 */
int XLIGHTHub::ExecuteCommandEx(MM::Device& device, MM::Core& core, const char* command) 
{
	int ret = DEVICE_OK;
	bool succeeded = false;

    // empty the Rx serial buffer before sending command
    ClearAllRcvBuf(device, core);


	// try up to 10 times -- TBD
	while ( !succeeded ) 
	{
		// send command
		rcvBuf_[0] = '\0';
		ret = core.SetSerialCommand(&device, port_.c_str(), command, "\r");
		if (DEVICE_OK != ret)
			return ret;

		//to ensure wheel finishes movement, query the serial line waiting for echoed 
		// command executed which means XLight accomplished the movement
        
		MM::MMTime startTime = core.GetCurrentMMTime();
		//unsigned long bytesRead = 0;
		//GetXlightCommandEcho(device,core);
		while (('\0' == rcvBuf_[0]) && /* (bytesRead < 1) && */ ( (core.GetCurrentMMTime() - startTime).getMsec() < commandExecutionTimeoutMs_))
		{
			ret = core.GetSerialAnswer(&device, port_.c_str(), RCV_BUF_LENGTH, rcvBuf_, "\r");
			if (DEVICE_OK != ret)
				return ret;
		}
		if ('\0' != rcvBuf_[0])
		{
			if (strncmp(rcvBuf_, command, strlen(command)) == 0)
			{
				succeeded = true;
				ret = DEVICE_OK;
			}
			else
			{
				return ERR_COMMAND_EXECUTION_ERROR;
			}
		}
		else // timeout
		{
			return ERR_COMMUNICATION_TIMEOUT;
		}

		if (enableDeviceWaitByDelay_)
		{
			CDeviceUtils::SleepMs(deviceWaitMs_);
			succeeded = true;
		}
	}
		
	return DEVICE_OK;
}

#if 0
   hub->PurgeComPortH();

   unsigned char command[2];
   command[0] = 1;
   command[1] = (unsigned char) value;
   int ret = hub->WriteToComPortH((const unsigned char*) command, 2);
   if (ret != DEVICE_OK)
      return ret;

   MM::MMTime startTime = GetCurrentMMTime();
   unsigned long bytesRead = 0;
   unsigned char answer[1];
   while ((bytesRead < 1) && ( (GetCurrentMMTime() - startTime).getMsec() < 250)) {
      ret = hub->ReadFromComPortH(answer, 1, bytesRead);
      if (ret != DEVICE_OK)
         return ret;
   }
   if (answer[0] != 1)
      return ERR_COMMUNICATION;

#endif
