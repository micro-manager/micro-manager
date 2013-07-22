///////////////////////////////////////////////////////////////////////////////
// FILE:          LeicaDMRHub.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   LeicaDMSTC hub module. Required for operation of LeicaDMSTC
//                device.
//                                                                                     
// AUTHOR:        G. Esteban Fernandez, 27-Aug-2012
//                Based on LeicaDMR adapter by Nico Stuurman.
//
// COPYRIGHT:     2012, Children's Hospital Los Angeles
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

#include "assert.h"
#include <memory.h>
#include <cstdlib>
#include <sstream>
#include <iostream>
#include <iomanip>
#include <string>
#include "LeicaDMSTCHub.h"
#include "LeicaDMSTC.h"

#ifdef WIN32
#include <windows.h>
#endif

using namespace std;

LeicaDMSTCHub::LeicaDMSTCHub () :
   port_(""),
   initialized_(false)
{
   ClearRcvBuf();
}

LeicaDMSTCHub::~LeicaDMSTCHub()
{
   initialized_ = false;
}

int LeicaDMSTCHub::Initialize(MM::Device& device, MM::Core& core)
{
   if (initialized_)
      return DEVICE_OK;

   int ret = GetVersion (device, core, version_);
   if (ret != DEVICE_OK) {
      // some serial ports do not open correctly right away, so try once more:
      ret = GetVersion(device, core, version_);
      if (ret != DEVICE_OK)
         return ret;
   }

   ret = GetMicroscope(device, core, microscope_);
   if (ret != DEVICE_OK)
      return ret;

   std::ostringstream os;
   os << "Firmware version: " << version_;
   core.LogMessage(&device, os.str().c_str(), false);
   //os.str("");

   	// PHYSICAL RESET & INITIALIZATION
	//ret = SetCommand(device, core, xyStage_, 24); //resets the stage
	//if (ret != DEVICE_OK)
	//	return ret;
	
	//travel to origin (top left) and define zero point, must be done or READ_POS_X/Y sends three question marks
	//ret = SetCommand(device, core, xyStage_, 11);	
	//if (ret != DEVICE_OK)													
	//	return ret;

   initialized_ = true;

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Device commands
///////////////////////////////////////////////////////////////////////////////

int LeicaDMSTCHub::GetMicroscope(MM::Device& device, MM::Core& core, std::string& microscope)
{
   int ret = GetCommand(device, core, xyStage_, 1, microscope);
   
   if (ret != DEVICE_OK)
		return ret;
   return DEVICE_OK;
}

int LeicaDMSTCHub::GetVersion(MM::Device& device, MM::Core& core, std::string& version)
{
   int ret = GetCommand(device, core, xyStage_, 43, version);
   
   if (ret != DEVICE_OK)
		return ret;
   return DEVICE_OK;
}

// XY Drive
int LeicaDMSTCHub::SetXYAbs(MM::Device& device, MM::Core& core, long positionX, long positionY)
{
   int ret = SetCommand(device, core, xyStage_, 2, (int) positionX, (int) positionY);
   
   if (ret != DEVICE_OK)
		return ret;
   return DEVICE_OK;
}

int LeicaDMSTCHub::SetXYRel(MM::Device& device, MM::Core& core, long positionX, long positionY)
{
   int ret = SetCommand(device, core, xyStage_, 5, (int) positionX, (int) positionY);
   
   if (ret != DEVICE_OK)
		return ret;
   return DEVICE_OK;
}

/*
int LeicaDMSTCHub::MoveXYConst(MM::Device& device, MM::Core& core, int speedX, int speedY)
{
   return SetCommand(device, core, xyStage_, 4, speedX, speedY);
}
*/

int LeicaDMSTCHub::StopXY(MM::Device& device, MM::Core& core)
{
	int ret = SetCommand(device, core, xyStage_, 010);
   
	if (ret != DEVICE_OK)
		return ret;
	return DEVICE_OK;
}

int LeicaDMSTCHub::GetXYAbs(MM::Device& device, MM::Core& core, long& positionX, long& positionY)
{
	int answerX, answerY;
	
	int ret =  GetCommand(device, core, xyStage_, 16, answerX);
	if (ret != DEVICE_OK)
		return ret;
	
	ret =  GetCommand(device, core, xyStage_, 17, answerY);
	if (ret != DEVICE_OK)
		return ret;

	positionX = (long) answerX;
	positionY = (long) answerY;
	
	return DEVICE_OK;
}

int LeicaDMSTCHub::GetXYUpperThreshold(MM::Device& device, MM::Core& core, long& positionX, long& positionY)
{
	int answerX, answerY;
	
	int ret =  GetCommand(device, core, xyStage_, 21, answerX);
	if (ret != DEVICE_OK)
		return ret;
	
	ret =  GetCommand(device, core, xyStage_, 23, answerY);
	if (ret != DEVICE_OK)
		return ret;

	positionX = (long) answerX;
	positionY = (long) answerY;
	
	return DEVICE_OK;
}
int LeicaDMSTCHub::GetXYLowerThreshold(MM::Device& device, MM::Core& core, long& positionX, long& positionY)
{
	int answerX, answerY;
	
	int ret =  GetCommand(device, core, xyStage_, 20, answerX);
	if (ret != DEVICE_OK)
		return ret;
	
	ret =  GetCommand(device, core, xyStage_, 22, answerY);
	if (ret != DEVICE_OK)
		return ret;

	positionX = (long) answerX;
	positionY = (long) answerY;
	
	return DEVICE_OK;
}

int LeicaDMSTCHub::SetAcceleration(MM::Device& device, MM::Core& core, int acc)
{
	int ret = SetCommand(device, core, xyStage_, 32, acc);
   
	if (ret != DEVICE_OK)
		return ret;

	return DEVICE_OK;
}

int LeicaDMSTCHub::GetAcceleration(MM::Device& device, MM::Core& core, int& acc)
{
	int answer;

	int ret = GetCommand(device, core, xyStage_, 33, answer);
   
	if (ret != DEVICE_OK)
		return ret;

	acc = answer;

	return DEVICE_OK;
}

int LeicaDMSTCHub::SetSpeedX(MM::Device& device, MM::Core& core, int speed)
{
	int ret = SetCommand(device, core, xyStage_, 8, speed);

	if (ret != DEVICE_OK)
		return ret;
	return DEVICE_OK;
}

int LeicaDMSTCHub::SetSpeedY(MM::Device& device, MM::Core& core, int speed)
{
	int ret = SetCommand(device, core, xyStage_, 9, speed);

	if (ret != DEVICE_OK)
		return ret;
	return DEVICE_OK;
}

int LeicaDMSTCHub::GetSpeedX(MM::Device& device, MM::Core& core, int& speed)
{
	int answer;
	int ret = GetCommand(device, core, xyStage_, 39, answer);
   
	if (ret != DEVICE_OK)
		return ret;

	speed = answer;

	return DEVICE_OK;
}

int LeicaDMSTCHub::GetSpeedY(MM::Device& device, MM::Core& core, int& speed)
{
	int answer;
	int ret = GetCommand(device, core, xyStage_, 40, answer);
   
	if (ret != DEVICE_OK)
		return ret;

	speed = answer;

	return DEVICE_OK;
}

double LeicaDMSTCHub::GetStepSize(MM::Device& device, MM::Core& core)
{
	std::string answer;
	int ret = GetCommand(device, core, xyStage_, 50, answer);
	if (ret != DEVICE_OK)
		return ret;

	// answer should be 3 bytes:
	// byte 1= 1 or 2 for motor, byte 2= blank as separator, byte 3= 1, 2, or 4 for pitch

	int motor;  //possible 100- or 200-step motor
	int pitch;  //possible 1-, 2-, or 4-mm pitch (mm per step)

	if(answer[0]=='2')  //200-step motor
		motor = 200;
	else
		motor = 100;  //default to 100-step motor
	
	if(answer[2]=='2') //2-mm pitch
		pitch = 2;
	else if(answer[2]=='4')  // 4-mm pitch
		pitch = 4;
	else
		pitch = 1;  //default to 1-mm pitch

   double stepSizeUm = (pitch * 1000) / (motor * 100);

   return stepSizeUm;
}

int LeicaDMSTCHub::SetAcknowledge(MM::Device& device, MM::Core& core, int ack)
{
	int ret = SetCommand(device, core, xyStage_, 35, ack);
	if (ret != DEVICE_OK)
		return ret;

	return DEVICE_OK;
}


int LeicaDMSTCHub::ResetStage(MM::Device& device, MM::Core& core)
{
	int ret=SetCommand(device, core, xyStage_, 11);
	if (ret != DEVICE_OK)
		return ret;

	return DEVICE_OK;
}

int LeicaDMSTCHub::InitRange(MM::Device& device, MM::Core& core)
{
	int ret=SetCommand(device, core, xyStage_, 38);
	if (ret != DEVICE_OK)
		return ret;

	return DEVICE_OK;
}

/*
int LeicaDMSTCHub::ReadEvents1(MM::Device& device, MM::Core& core, int& event1, int& event2, int& event3, int& event4, int& event5)
{
	int byte1, byte2, byte3, byte4, byte5;
	int ret=GetCommand(device, core, xyStage_, 18, byte1, byte2, byte3, byte4, byte5);
	if (ret != DEVICE_OK)
		return ret;

	event1 = byte1;
	event2 = byte2;
	event3 = byte3;
	event4 = byte4;
	event5 = byte5;

	return DEVICE_OK;
}

int LeicaDMSTCHub::ReadEvents2(MM::Device& device, MM::Core& core, int& event1, int& event2, int& event3, int& event4)
{
	int byte1, byte2, byte3, byte4;
	int ret=GetCommand(device, core, xyStage_, 18, byte1, byte2, byte3, byte4);
	if (ret != DEVICE_OK)
		return ret;

	event1 = byte1;
	event2 = byte2;
	event3 = byte3;
	event4 = byte4;
	
	return DEVICE_OK;
}
*/
/**
 * Clears the serial receive buffer.
 */
void LeicaDMSTCHub::ClearAllRcvBuf(MM::Device& device, MM::Core& core)
{
   // Read whatever has been received so far:
   unsigned long read = RCV_BUF_LENGTH;
   while (read == (unsigned long) RCV_BUF_LENGTH)
   {
      core.ReadFromSerial(&device, port_.c_str(), (unsigned char*)rcvBuf_, RCV_BUF_LENGTH,  read);
   }
   // Delete it all:
   memset(rcvBuf_, 0, RCV_BUF_LENGTH);
}

void LeicaDMSTCHub::ClearRcvBuf()
{
   memset(rcvBuf_, 0, RCV_BUF_LENGTH);
}


/**
 * Sends serial command to the MMCore virtual serial port.
 * returns data from the microscope as a string
 */
int LeicaDMSTCHub::GetCommand(MM::Device& device, MM::Core& core,  int deviceId, int command, std::string& answer)
{
   if (port_ == "")
      return ERR_PORT_NOT_SET;

   ClearAllRcvBuf(device, core);
 
   // send command
   std::ostringstream os;
   os << std::setw(2) << std::setfill('0') << deviceId;
   os << std::setw(3) << std::setfill('0') << command;
   int ret = core.SetSerialCommand(&device, port_.c_str(), os.str().c_str(), "\r");
   if (ret != DEVICE_OK)
      return ret;

   char rcvBuf[RCV_BUF_LENGTH];
   ret = core.GetSerialAnswer(&device, port_.c_str(), RCV_BUF_LENGTH, rcvBuf, "\r");
   if (ret != DEVICE_OK)
      return ret;

   std::stringstream s1, s2, s3;
   int commandCheck, deviceIdCheck;
   s1 << rcvBuf[0] << rcvBuf[1];
   s1 >> deviceIdCheck;
   s2 << rcvBuf[2] << rcvBuf[3] << rcvBuf[4];
   s2 >> commandCheck;
   if ( (deviceId != deviceIdCheck) || (command != commandCheck))
      return ERR_UNEXPECTED_ANSWER_GetCommandString;

   if (strlen(rcvBuf) > 5)
      s3 << rcvBuf + 5;

   answer = s3.str();

   return DEVICE_OK;

}

/**
 * Sends serial command to the MMCore virtual serial port.
 * returns data from the microscope as an integer
 */
int LeicaDMSTCHub::GetCommand(MM::Device& device, MM::Core& core,  int deviceId, int command, int& answer)
{
   std::string reply;
   int ret = GetCommand(device, core, deviceId, command, reply);
   if (ret != DEVICE_OK)
      return ret;

   std::stringstream ss;
   ss << reply;
   ss >> answer;

   return DEVICE_OK;
}

/**
 * Sends serial command to the MMCore virtual serial port appended with data.
 */
int LeicaDMSTCHub::SetCommand(MM::Device& device, MM::Core& core,  int deviceId, int command, int data)
{
   if (port_ == "")
      return ERR_PORT_NOT_SET;

   ClearAllRcvBuf(device, core);

   // send command
   std::ostringstream os;
   os << std::setw(2) << std::setfill('0') << deviceId;
   os << std::setw(3) << std::setfill('0') << command << data;
   int ret = core.SetSerialCommand(&device, port_.c_str(), os.str().c_str(), "\r");
   if (ret != DEVICE_OK)
      return ret;

/* // TODO: error checking in the answer
   char rcvBuf[RCV_BUF_LENGTH];
   ret = core.GetSerialAnswer(&device, port_.c_str(), RCV_BUF_LENGTH, rcvBuf, "\r");
   if (ret != DEVICE_OK)
      return ret;

   std::stringstream s1, s2;
   int commandCheck, deviceIdCheck;
   s1 << rcvBuf[0] << rcvBuf[1];
   s1 >> deviceIdCheck;
   s2 << rcvBuf[2] << rcvBuf[3] << rcvBuf[4];
   s2 >> commandCheck;
   if ( (deviceId != deviceIdCheck) || (command != commandCheck))
	   return ERR_UNEXPECTED_ANSWER_SetCommandData;
*/

   return DEVICE_OK;
}


/**
 * Sends serial command to the MMCore virtual serial port appended with two data packets.  Meant for x, y positions.
 */
int LeicaDMSTCHub::SetCommand(MM::Device& device, MM::Core& core,  int deviceId, int command, int dataX, int dataY)
{
   if (port_ == "")
      return ERR_PORT_NOT_SET;

   ClearAllRcvBuf(device, core);

   // send command
   std::ostringstream os;
   os << std::setw(2) << std::setfill('0') << deviceId;
   os << std::setw(3) << std::setfill('0') << command << dataX << " " << dataY;
   int ret = core.SetSerialCommand(&device, port_.c_str(), os.str().c_str(), "\r");
   if (ret != DEVICE_OK)
      return ret;

/* // TODO: error checking in the answer
   char rcvBuf[RCV_BUF_LENGTH];
   ret = core.GetSerialAnswer(&device, port_.c_str(), RCV_BUF_LENGTH, rcvBuf, "\r");
   if (ret != DEVICE_OK)
      return ret;

   std::stringstream s1, s2;
   int commandCheck, deviceIdCheck;
   s1 << rcvBuf[0] << rcvBuf[1];
   s1 >> deviceIdCheck;
   s2 << rcvBuf[2] << rcvBuf[3] << rcvBuf[4];
   s2 >> commandCheck;
   if ( (deviceId != deviceIdCheck) || (command != commandCheck))
      return ERR_UNEXPECTED_ANSWER_SetCommandDataXY;
*/

   return DEVICE_OK;
}

/**
 * Sends serial command to the MMCore virtual serial port.
 */
int LeicaDMSTCHub::SetCommand(MM::Device& device, MM::Core& core,  int deviceId, int command)
{
   if (port_ == "")
      return ERR_PORT_NOT_SET;

   ClearAllRcvBuf(device, core);

   // send command
   std::ostringstream os;
   os << std::setw(2) << std::setfill('0') << deviceId;
   os << std::setw(3) << std::setfill('0') << command;
   int ret = core.SetSerialCommand(&device, port_.c_str(), os.str().c_str(), "\r");
   if (ret != DEVICE_OK)
      return ret;

/* // TODO: error checking in the answer
   char rcvBuf[RCV_BUF_LENGTH];
   ret = core.GetSerialAnswer(&device, port_.c_str(), RCV_BUF_LENGTH, rcvBuf, "\r");
   if (ret != DEVICE_OK)
      return ret;

   std::stringstream s1, s2;
   int commandCheck, deviceIdCheck;
   s1 << rcvBuf[0] << rcvBuf[1];
   s1 >> deviceIdCheck;
   s2 << rcvBuf[2] << rcvBuf[3] << rcvBuf[4];
   s2 >> commandCheck;

   if ( (deviceId != deviceIdCheck) || (command != commandCheck))
      return ERR_UNEXPECTED_ANSWER_SetCommand;
*/

   return DEVICE_OK;
}
