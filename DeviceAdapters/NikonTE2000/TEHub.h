///////////////////////////////////////////////////////////////////////////////
// FILE:          TEHub.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Nikon TE2000 hub module. Required for operation of all 
//                TE2000 microscope devices
//                
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 05/03/2006
// COPYRIGHT:     University of California San Francisco
// LICENSE:       This file is distributed under the BSD license.
//                License text is included with the source distribution.
// CVS:           $Id$
//
#ifndef _TEHUB_H_
#define _TEHUB_H_

#include <string>
#include <deque>
#include <map>
#include "../../MMDevice/DeviceBase.h"

// PFS status constants
const int PFS_WAIT = 1;
const int PFS_LED_OVER = 2;
const int PFS_LED_UNDER = 3;
const int PFS_RUNNING = 4;
const int PFS_SEARCHING = 5;
const int PFS_SEARCHING_2 = 10;
const int PFS_JUST_PINT = 50;
const int PFS_DISABLED = 90;

//lamp control target constants
const int LAMP_TARGET_MICROSCOPE = 0;
const int LAMP_TARGET_PAD = 1;

enum CommandMode
{
   Sync = 0,
   Async
};

class TEHub
{
public:
   TEHub();
   ~TEHub();

   void SetPort(const char* port) {port_ = port;}
   std::string GetPort() {return port_;}
   int GetVersion(MM::Device& device, MM::Core& core, std::string& ver);
   int GetModelType(MM::Device& device, MM::Core& core, int& type);
   bool IsBusy();
   CommandMode GetCommandMode() {return commandMode_.compare("c") == 0 ? Sync : Async;}
   void SetCommandMode(CommandMode cm) {cm == Sync ? commandMode_ = "c" : commandMode_ = "f";}

   bool IsComponentMounted(MM::Device& device, MM::Core& core, char* deviceCode);
   bool DetectPerfectFocus(MM::Device& device, MM::Core& core);

   int SetNosepiecePosition(MM::Device& device, MM::Core& core, int pos);
   int GetNosepiecePosition(MM::Device& device, MM::Core& core, int& pos);
   int GetNosepieceMountingStatus(MM::Device& device, MM::Core& core, int& status);
   bool IsNosepieceBusy(MM::Device& device, MM::Core& core);

   int SetFocusPosition(MM::Device& device, MM::Core& core, int pos);
   int GetFocusPosition(MM::Device& device, MM::Core& core, int& pos);
   int SetFocusStepSize(MM::Device& device, MM::Core& core, int stepSize);
   int GetFocusStepSize(MM::Device& device, MM::Core& core, int& stepSize);
   bool IsFocusBusy(MM::Device& device, MM::Core& core);

   int SetFilterBlockPosition(MM::Device& device, MM::Core& core, int pos);
   int GetFilterBlockPosition(MM::Device& device, MM::Core& core, int& pos);
   bool IsExcitationFilterBlockBusy(MM::Device& device, MM::Core& core);

   int SetExcitationFilterBlockPosition(MM::Device& device, MM::Core& core, int pos);
   int GetExcitationFilterBlockPosition(MM::Device& device, MM::Core& core, int &pos);
   bool IsFilterBlockBusy(MM::Device& device, MM::Core& core);

   int SetOpticalPathPosition(MM::Device& device, MM::Core& core, int pos);
   int GetOpticalPathPosition(MM::Device& device, MM::Core& core, int& pos);
   bool IsOpticalPathBusy(MM::Device& device, MM::Core& core);

   int SetAnalyzerPosition(MM::Device& device, MM::Core& core, int pos);
   int GetAnalyzerPosition(MM::Device& device, MM::Core& core, int& pos);
   bool IsAnalyzerBusy(MM::Device& device, MM::Core& core);

   int SetLampOnOff(MM::Device& device, MM::Core& core, int status);
   int GetLampOnOff(MM::Device& device, MM::Core& core, int& status);
   int SetLampVoltage(MM::Device& device, MM::Core& core, double voltage);
   int GetLampVoltage(MM::Device& device, MM::Core& core, double& voltage);
   bool IsLampBusy(MM::Device& device, MM::Core& core);
   int SetLampControlTarget(MM::Device& device, MM::Core& core, int target);
   int GetLampControlTarget(MM::Device& device, MM::Core& core, int& target);

   int SetEpiShutterStatus(MM::Device& device, MM::Core& core, int status);
   int GetEpiShutterStatus(MM::Device& device, MM::Core& core, int& pos);

   int SetUniblitzStatus(MM::Device& device, MM::Core& core, int shutterNumber, int status);

   int SetPFocusOn(MM::Device& device, MM::Core& core);
   int SetPFocusOff(MM::Device& device, MM::Core& core);
   int GetPFocusStatus(MM::Device& device, MM::Core& core, int& status);
   int GetPFocusVersion(MM::Device& device, MM::Core& core, std::string& version);

   int SetPFocusPosition(MM::Device& device, MM::Core& core, int pos);
   int SetRelativePFocusPosition(MM::Device& device, MM::Core& core, int pos);
   int GetPFocusPosition(MM::Device& device, MM::Core& core, int& pos);

private:
   int ExecuteCommand(MM::Device& device, MM::Core& core, const char* type, const char* command);
   int ParseResponse(MM::Device& device, MM::Core& core, const char* cmdId, std::string& value);
   void FetchSerialData(MM::Device& device, MM::Core& core);
   bool IsCommandWaiting(const char* command, MM::Device& device, MM::Core& core);
   void LogError(int id, MM::Device& device, MM::Core& core, const char* functionName);

   static const int RCV_BUF_LENGTH = 1024;
   char rcvBuf_[RCV_BUF_LENGTH];
   char asynchRcvBuf_[RCV_BUF_LENGTH];
   void ClearRcvBuf();
   std::string port_;
   std::vector<char> answerBuf_;
   std::multimap<std::string, long> waitingCommands_;
   long expireTimeUs_;
   std::string commandMode_;
};

#endif // _TEHUB_H_
