///////////////////////////////////////////////////////////////////////////////
// FILE:          SC10.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Thorlabs SC10 controller (controls SH05 beam shutter) adapter
// COPYRIGHT:     University of California, San Francisco, 2009
// LICENSE:       LGPL
// AUTHOR:        Nico Stuurman, 03/20/2009
//

#ifndef _SC10_H_
#define _SC10_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include <string>
#include <map>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_UNKNOWN_POSITION         10002
#define ERR_INVALID_SPEED            10003
#define ERR_PORT_CHANGE_FORBIDDEN    10004
#define ERR_SET_POSITION_FAILED      10005
#define ERR_INVALID_STEP_SIZE        10006
#define ERR_LOW_LEVEL_MODE_FAILED    10007
#define ERR_INVALID_MODE             10008

class SC10 : public CShutterBase<SC10>
{
public:
   SC10();
   ~SC10();
  
   // Device API
   // ----------
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy();

   // Shutter API
   int SetOpen(bool open = true);
   int GetOpen(bool& open);
   int Fire(double deltaT);

   // action interface
   // ----------------
   int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnCommand(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   int ExecuteCommand(const std::string& cmd, std::string& answer);

   bool initialized_;
   std::string deviceInfo_;

   // MMCore name of serial port
   std::string port_;
   
   // Command exchange with MMCore
   std::string command_;
   
   // Last command sent to the controller
   std::string lastCommand_;

   // Delay between issuing command and shutter opening/closing
   double actionDelay_;
};


#endif //_SC10_H_
