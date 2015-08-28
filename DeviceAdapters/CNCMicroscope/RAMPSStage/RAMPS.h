/*
Copyright 2015 Google Inc. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

#ifndef _RAMPS_H_
#define _RAMPS_H_

#include "DeviceBase.h"
#include "DeviceThreads.h"
#include <string>
#include <map>
#include <algorithm>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
// #define ERR_UNKNOWN_MODE         102
// #define ERR_UNKNOWN_POSITION     103
// #define ERR_IN_SEQUENCE          104
// #define ERR_SEQUENCE_INACTIVE    105
#define ERR_STAGE_MOVING         110

#define ERR_UNKNOWN_POSITION 101
#define ERR_INITIALIZE_FAILED 102
#define ERR_WRITE_FAILED 103
#define ERR_CLOSE_FAILED 104
#define ERR_BOARD_NOT_FOUND 105
#define ERR_PORT_OPEN_FAILED 106
#define ERR_COMMUNICATION 107
#define ERR_NO_PORT_SET 108
#define ERR_VERSION_MISMATCH 109


////////////////////////
// RAMPSHub
//////////////////////

class RAMPSHub : public HubBase<RAMPSHub>
{
 public:
  RAMPSHub();
  ~RAMPSHub();

  // Device API
  // ---------
  int Initialize();
  int Shutdown();
  void GetName(char* pName) const; 
  bool Busy();

  // property handlers
  int OnVersion(MM::PropertyBase* pProp, MM::ActionType pAct);
  int OnPort(MM::PropertyBase* pPropt, MM::ActionType eAct);
  int OnCommand(MM::PropertyBase* pProp, MM::ActionType pAct);
  int OnSettleTime(MM::PropertyBase* pProp, MM::ActionType eAct);
  int SetVelocity(double velocity);
  int SetAcceleration(double acceleration);
  int OnVelocity(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnAcceleration(MM::PropertyBase* pProp, MM::ActionType eAct);

  // HUB api
  int DetectInstalledDevices();

  int SendCommand(std::string command, std::string terminator="\r");
  int ReadResponse(std::string& returnString, float timeout=300.);
  int SetAnswerTimeoutMs(double timout);
  MM::DeviceDetectionStatus DetectDevice(void);
  int PurgeComPortH();
  int WriteToComPortH(const unsigned char* command, unsigned len);
  int ReadFromComPortH(unsigned char* answer, unsigned maxLen, unsigned long& bytesRead);
  int SetCommandComPortH(const char* command, const char* term);
  int GetSerialAnswerComPortH (std::string& ans,  const char* term);
  int GetStatus();
  int GetXYPosition(double *x, double *y);
  std::string GetState();
  int GetControllerVersion(std::string& version);

 private:
  void GetPeripheralInventory();
  std::vector<std::string> peripherals_;
  bool initialized_;
  bool sent_busy_;
  std::string version_;
  MMThreadLock lock_;
  MMThreadLock executeLock_;
  std::string port_;
  bool portAvailable_;
  std::string commandResult_;
  double MPos[3];
  double WPos[3];
  std::string status_;
  MM::TimeoutMs* timeOutTimer_;
  long settle_time_;
  double velocity_;
  double acceleration_;
};


#endif //_RAMPS_H_
