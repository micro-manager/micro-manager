///////////////////////////////////////////////////////////////////////////////
// FILE:          Vincent.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Vincent Uniblitz controller adapter
// COPYRIGHT:     University of California, San Francisco, 2006
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
// AUTHOR:        Nico Stuurman, 02/27/2006
//

#ifndef _VINCENT_H_
#define _VINCENT_H_

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

class VincentD1 : public CShutterBase<VincentD1>
{
public:
   VincentD1();
   ~VincentD1();
  
   // Device API
   // ----------
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy();
   //MM::DeviceType GetType() const {return MM::GenericDevice;}

   // Shutter API
   int SetOpen(bool open = true);
   int GetOpen(bool& open);
   int Fire(double deltaT);

   // action interface
   // ----------------
   int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnCommand(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnAddress(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnOpeningTime(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnClosingTime(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnShutterName(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   int ExecuteCommand(const std::string& cmd);

   bool initialized_;
   // MMCore name of serial port
   std::string port_;
   // Command exchange with MMCore
   std::string command_;
   // Last command sent to the controller
   std::string lastCommand_;
   // address of controller on serial chain (x, 0-7)
   std::string address_;
   // Time it takes after issuing Close command to close the shutter
   double closingTimeMs_;
   // Time it takes after issuing Open command to open the shutter
   double openingTimeMs_;
   // Are we operating shutter A or shutter B?
   std::string shutterName_;
   // Time that last command was sent to shutter
   MM::MMTime changedTime_;
};


class VincentD3 : public CShutterBase<VincentD3>
{
public:
   VincentD3();
   ~VincentD3();
  
   // Device API
   // ----------
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy();
   //MM::DeviceType GetType() const {return MM::GenericDevice;}

   // Shutter API
   int SetOpen(bool open = true);
   int GetOpen(bool& open);
   int Fire(double deltaT);

   // action interface
   // ----------------
   int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnCommand(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnAddress(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnOpeningTime(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnClosingTime(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnShutterName(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   int ExecuteCommand(const std::string& cmd);

   bool initialized_;
   // MMCore name of serial port
   std::string port_;
   // Command exchange with MMCore
   std::string command_;
   // Last command sent to the controller
   std::string lastCommand_;
   // address of controller on serial chain (x, 0-7)
   std::string address_;
   // Time it takes after issuing Close command to close the shutter
   double closingTimeMs_;
   // Time it takes after issuing Open command to open the shutter
   double openingTimeMs_;
   // Ch1-3
   std::string shutterName_;
   // Time that last command was sent to shutter
   MM::MMTime changedTime_;
};
#endif //_VINCENT_H_
