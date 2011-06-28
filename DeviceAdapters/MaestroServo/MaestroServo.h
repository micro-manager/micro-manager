///////////////////////////////////////////////////////////////////////////////
// FILE:          MaestroServo.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Adapter for MaestroServo controller with USB interface
// COPYRIGHT:     University of California, San Francisco, 2010
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

#ifndef _MAESTROSERVO_H_
#define _MAESTROSERVO_H_

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

class MaestroServo : public CGenericBase<MaestroServo>
{
public:
   MaestroServo();
   ~MaestroServo();
  
   // Device API
   // ----------
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy();

   // action interface
   // ---------------- 
   int OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct);  
   int OnMinPosition(MM::PropertyBase* pProp, MM::ActionType eAct);  
   int OnMaxPosition(MM::PropertyBase* pProp, MM::ActionType eAct);  
   int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnServoNr(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSpeed(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnAcceleration(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   bool initialized_;
   bool moving_;
   long minPos_;
   long maxPos_;
   long servoNr_;
   long speed_;
   long acceleration_;
   // MMCore name of serial port
   std::string port_;
   // Command exchange with MMCore
   std::string command_;
   // Time that last command was sent to device
   MM::MMTime changedTime_;
   double position_;
};

class MaestroShutter : public CShutterBase<MaestroShutter>
{
public:
   MaestroShutter();
   ~MaestroShutter();
  
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
   int OnOpenPosition(MM::PropertyBase* pProp, MM::ActionType eAct);  
   int OnClosedPosition(MM::PropertyBase* pProp, MM::ActionType eAct); 
   int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnServoNr(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSpeed(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnAcceleration(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   int SetPosition(long position);

   bool initialized_;
   bool moving_;
   long openPos_;
   long closedPos_;
   bool open_;
   long servoNr_;
   long speed_;
   long acceleration_;
   // MMCore name of serial port
   std::string port_;
   // Command exchange with MMCore
   std::string command_;
   // Time that last command was sent to device
   MM::MMTime changedTime_;
   double position_;
};

#endif //_MaestroServo_H_
