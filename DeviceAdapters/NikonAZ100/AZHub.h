///////////////////////////////////////////////////////////////////////////////
// FILE:          AZHub.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Nikon AZ100 hub module. Required for operation of all 
//                AZ100 microscope devices
//                
// AUTHOR:        Nico Stuurman, nico@cmp.ucsf.edu, 5/22/2008
// COPYRIGHT:     University of California San Francisco
// LICENSE:       This file is distributed under the BSD license.
//                License text is included with the source distribution.
// CVS:           $Id: AZHub.h 1133 2008-04-25 16:22:56Z nico $
//
#ifndef _AZHUB_H_
#define _AZHUB_H_

#include <string>
#include <deque>
#include <map>
#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"

#define ERR_NEED_COMPUTER_CONTROL  23000;

enum ControlMode {
   manual = 0,
   computer
};

class AZHub
{
public:
   AZHub();
   ~AZHub();

   int Initialize(MM::Device& device, MM::Core& core);
   int Shutdown(MM::Device& device, MM::Core& core);
   void SetPort(const char* port) {port_ = port;}
   std::string GetPort() {return port_;}
   int GetVersion(MM::Device& device, MM::Core& core, std::string& ver);
   int GetModelType(MM::Device& device, MM::Core& core, int& type);
   bool IsBusy();
   //ControlMode GetControlMode();
   int SetControlMode(MM::Device& device, MM::Core& core, ControlMode pos);

   int GetNosepiecePosition(MM::Device& device, MM::Core& core, int& pos);
   int GetNosepieceMountingStatus(MM::Device& device, MM::Core& core, int& status);

   int SetFocusPosition(MM::Device& device, MM::Core& core, int pos);
   int GetFocusPosition(MM::Device& device, MM::Core& core, int& pos);
   int SetFocusStepSize(MM::Device& device, MM::Core& core, int stepSize);
   int GetFocusStepSize(MM::Device& device, MM::Core& core, int& stepSize);
   bool IsFocusBusy(MM::Device& device, MM::Core& core);

   int SetZoomPosition(MM::Device& device, MM::Core& core, double pos);
   int GetZoomPosition(MM::Device& device, MM::Core& core, double& pos);
   bool IsZoomBusy(MM::Device& device, MM::Core& core);

   int GetFilterBlockPosition(MM::Device& device, MM::Core& core, int& pos);
   bool IsFilterBlockBusy(MM::Device& device, MM::Core& core);

private:
   int ExecuteCommand(MM::Device& device, MM::Core& core, const char* type, const char* command);
   int GetAnswer(MM::Device& device, MM::Core& core);
   int ParseResponse(MM::Device& device, MM::Core& core, const char* cmdId, std::string& value);
   void FetchSerialData(MM::Device& device, MM::Core& core);
   void LogError(int id, MM::Device& device, MM::Core& core, const char* functionName);

   static const int RCV_BUF_LENGTH = 1024;
   char rcvBuf_[RCV_BUF_LENGTH];
   char asynchRcvBuf_[RCV_BUF_LENGTH];
   void ClearRcvBuf();
   std::string port_;
   std::vector<char> answerBuf_;
   std::multimap<std::string, long> waitingCommands_;
   std::string commandMode_;
   ControlMode controlMode_;
   bool initialized_;
};

#endif // _AZHUB_H_
