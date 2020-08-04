///////////////////////////////////////////////////////////////////////////////
// FILE:          PathwayHub.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   BDPathway hub module. Required for operation of all 
//                BDPathway devices
//                
// AUTHOR:        Egor Zindy, ezindy@gmail.com, 01/01/2020
//
//                Based on the Nikon TE2000 adapter by
//                Nenad Amodaj, nenad@amodaj.com, 05/03/2006
// COPYRIGHT:     University of California San Francisco
// LICENSE:       This file is distributed under the BSD license.
//                License text is included with the source distribution.
// CVS:           $Id$
//

#ifndef _PATHWAYHUB_H_
#define _PATHWAYHUB_H_

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

class PathwayHub
{
public:
   PathwayHub();
   ~PathwayHub();

   void SetPort(const char* port) {port_ = port;}
   std::string GetPort() {return port_;}

   int PathwayHub::ExecuteSequence(MM::Device& device, MM::Core& core, const char **sequence);
   int GetVersion(MM::Device& device, MM::Core& core, std::string& ver);
   int GetModelType(MM::Device& device, MM::Core& core, std::string& model);
   bool IsBusy();
   CommandMode GetCommandMode() {return commandMode_.compare("c") == 0 ? Sync : Async;}
   void SetCommandMode(CommandMode cm) {cm == Sync ? commandMode_ = "c" : commandMode_ = "f";}

   bool IsComponentMounted(MM::Device& device, MM::Core& core, const char* deviceCode);


   int SetPosition(MM::Device& device, MM::Core& core, char deviceId, int pos);
   int GetPosition(MM::Device& device, MM::Core& core, char deviceId, int &pos);
   bool IsDeviceBusy(MM::Device& device, MM::Core& core);

   int SetFocusPosition(MM::Device& device, MM::Core& core, int pos);
   int GetFocusPosition(MM::Device& device, MM::Core& core, int& pos);
   int GetFocusStepSizeNm(MM::Device& device, MM::Core& core, long& zstep);
   bool IsFocusBusy(MM::Device& device, MM::Core& core);

   int SetXYPosition(MM::Device& device, MM::Core& core, long xpos, long ypos);
   int GetXYPosition(MM::Device& device, MM::Core& core, long& pos, long& ypos);
   int GetXYStepSizeNm(MM::Device& device, MM::Core& core, long& xstep, long& ystep);
   bool IsXYStageBusy(MM::Device& device, MM::Core& core);
   bool Is855(void) { return is855_; }

private:
   int ExecuteCommand(MM::Device& device, MM::Core& core, const char* command);
   int ExecuteCommand(MM::Device& device, MM::Core& core, const char* command, bool expectResponse);
   int ParseResponse(MM::Device& device, MM::Core& core, std::string& value);
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
   bool is855_;
};

#endif // _PATHWAYHUB_H_
