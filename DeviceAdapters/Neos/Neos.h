///////////////////////////////////////////////////////////////////////////////
// FILE:          Neos.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Adapter for Neos controller with USB interface
// COPYRIGHT:     University of California, San Francisco, 2009
// LICENSE:       This file is distributed under the LGPS license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
// AUTHOR:        Nico Stuurman, 10/03/2009
//

#ifndef _NEOS_H_
#define _NEOS_H_

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

class Neos : public CShutterBase<Neos>
{
public:
   Neos();
   ~Neos();
  
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
   int OnChannel(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnAmplitude(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnAmplitudeMax(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnCommand(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   int ExecuteCommand(std::string cmd);

   bool initialized_;
   bool open_;
   int amplitudeMax_;
   int amplitude_;
   // MMCore name of serial port
   std::string port_;
   // Command exchange with MMCore
   std::string command_;
   // Time that last command was sent to shutter
   MM::MMTime changedTime_;
   long channel_;
};


#endif //_NEOS_H_
