///////////////////////////////////////////////////////////////////////////////
// FILE:          LeicaDMSTCHub.h
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

#ifndef _LeicaDMSTCHUB_H_
#define _LeicaDMSTCHUB_H_

#include "../../MMDevice/MMDevice.h"


class LeicaDMSTCHub
{
public:
   LeicaDMSTCHub();
   ~LeicaDMSTCHub();

   void SetPort(const char* port) {port_ = port;}
   int Initialize(MM::Device& device, MM::Core& core);
   int DeInitialize() {initialized_ = false; return DEVICE_OK;};
   bool Initialized() {return initialized_;};
   std::string Version() {return version_;};
   std::string Microscope() {return microscope_;};

   int SetXYAbs(MM::Device& device, MM::Core& core, long positionX, long positionY);
   int GetXYAbs(MM::Device& device, MM::Core& core, long& positionX, long& positionY);

   int SetXYRel(MM::Device& device, MM::Core& core, long positionX, long positionY);

   int GetXYUpperThreshold(MM::Device& device, MM::Core& core, long& positionX, long& positionY);
   int GetXYLowerThreshold(MM::Device& device, MM::Core& core, long& positionX, long& positionY);

   int SetSpeedX(MM::Device& device, MM::Core& core, int speed);
   int GetSpeedX(MM::Device& device, MM::Core& core, int& speed);

   int SetSpeedY(MM::Device& device, MM::Core& core, int speed);
   int GetSpeedY(MM::Device& device, MM::Core& core, int& speed);
   
   int SetAcceleration(MM::Device& device, MM::Core& core, int acc);
   int GetAcceleration(MM::Device& device, MM::Core& core, int& acc);

   double GetStepSize(MM::Device& device, MM::Core& core);

   //int MoveXYConst(MM::Device& device, MM::Core& core, int speedX, int speedY);
   int StopXY(MM::Device& device, MM::Core& core);

   int SetAcknowledge(MM::Device& device, MM::Core& core, int ack);

   int ResetStage(MM::Device& device, MM::Core& core);
   int InitRange(MM::Device& device, MM::Core& core);

private:
   int GetVersion(MM::Device& device, MM::Core& core, std::string& version);
   int GetMicroscope(MM::Device& device, MM::Core& core, std::string& microscope);

   void ClearRcvBuf();
   void ClearAllRcvBuf(MM::Device& device, MM::Core& core);

   int GetCommand(MM::Device& device, MM::Core& core, int deviceId, int command, std::string& answer);
   int GetCommand(MM::Device& device, MM::Core& core, int deviceId, int command, int& answer1, int& answer2, int& answer3, int& answer4, int& answer5);
   int GetCommand(MM::Device& device, MM::Core& core, int deviceId, int command, int& answer1, int& answer2, int& answer3, int& answer4);
   int GetCommand(MM::Device& device, MM::Core& core, int deviceId, int command, int& answer);
   int SetCommand(MM::Device& device, MM::Core& core, int deviceId, int command, int dataX, int dataY);
   int SetCommand(MM::Device& device, MM::Core& core, int deviceId, int command, int data);
   int SetCommand(MM::Device& device, MM::Core& core, int deviceId, int command);

   // IDs of devices in the microscope
   // static const int gMic_ = 50;
   // static const int zDrive_ = 60;
   static const int xyStage_ = 10;

   static const int RCV_BUF_LENGTH = 1024;
   char rcvBuf_[RCV_BUF_LENGTH];

   std::string port_;
   std::string version_;
   std::string microscope_;
   bool initialized_;
   long expireTimeUs_;
};

#endif // _LeicaDMSTCHUB_H_
