///////////////////////////////////////////////////////////////////////////////
// FILE:          LStepOld.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Marzhauser LStep version 1.2
// COPYRIGHT:     Jannis Uhlendorf
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
// AUTHOR:        Jannis Uhlendorf (jannis.uhlendorf@gmail.com) 2015

#ifndef _LSTEPOLD_H_
#define _LSTEPOLD_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include <string>
#include <map>

//////////////////////////////////////////////////////////////////////////////
// Error codes
#define ERR_PORT_CHANGE_FORBIDDEN    10004

class LStepOld : public CXYStageBase<LStepOld>
{
public:
   LStepOld();
   ~LStepOld();

   //MMDevice API
   int Initialize();
   int Shutdown();
   void GetName(char* name) const;
   bool Busy();

   //XYStage API
   int SetPositionUm(double x, double y);
   int GetLimitsUm(double& xMin, double& xMax, double& yMin, double& yMax);
   int SetPositionSteps(long x, long y);
   int GetPositionSteps(long& x, long& y);
   int Home();
   int Stop();
   int SetOrigin();
   int GetStepLimits(long& xMin, long& xMax, long& yMin, long& yMax);
   double GetStepSizeXUm();
   double GetStepSizeYUm();
   int IsXYStageSequenceable(bool& isSequenceable) const;

   // action interface
   int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSpeed(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnJoystick(MM::PropertyBase* pProp, MM::ActionType eAct);

   bool initialized_;
   std::string port_;    // MMCore name of serial port
   double answerTimeoutMs_;
   double motor_speed_;
   std::string joystick_command_;

private:
   int ExecuteCommand(const std::string& cmd, char* input=NULL, int input_len=0, std::string* ret=NULL);
   int send_msg(char* msg);
};

#endif //_LStepOld_H_
