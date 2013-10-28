///////////////////////////////////////////////////////////////////////////////
// FILE:          FocalPoint`.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Adapter for Focalpoint control through Arduino Due
//
// COPYRIGHT:     University of California, San Francisco
//
// LICENSE:       This file is distributed under the BSD license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER(S) OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
// AUTHOR:        Nico Stuurman, Oct. 2013

#ifndef _FOCALPOINT_H_
#define _FOCALPOINT_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include <string>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//

#define ERR_SERIAL_PORT_NOT_OPEN     10003
#define ERR_PORT_CHANGE_FORBIDDEN    10004
#define ERR_COMMAND_FAILED           10005
#define ERR_UNRECOGNIZED_ANSWER      10009
#define ERR_UNSPECIFIED_ERROR        10010
#define ERR_NOT_LOCKED               10011
#define ERR_NOT_CALIBRATED           10012

MM::DeviceDetectionStatus FocalPointCheckSerialPort(MM::Device& device, MM::Core& core, std::string port, double ato);


class FocalPoint : public CAutoFocusBase<FocalPoint>
{
public:
   FocalPoint();
   ~FocalPoint();

   //MMDevice API
   bool Busy();
   void GetName(char* pszName) const;

   int Initialize();
   int Shutdown();

   // AutoFocus API
   virtual int SetContinuousFocusing(bool state);
   virtual int GetContinuousFocusing(bool& state);
   virtual bool IsContinuousFocusLocked();
   virtual int FullFocus();
   virtual int IncrementalFocus();
   virtual int GetLastFocusScore(double& score);
   virtual int GetCurrentFocusScore(double& /*score*/) {return DEVICE_UNSUPPORTED_COMMAND;}
   virtual int GetOffset(double& offset);
   virtual int SetOffset(double offset);

   // action interface
   // ----------------
   int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnFocus(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnWaitAfterLock(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnLaser(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnCommand(MM::PropertyBase* pProp, MM::ActionType eAct);


private:
   int CheckForDevice(); // make sure we can communicate
   int QueryCommand(const char* command, std::string answer);
   int SetLaser(bool state);
   int SetCommand(std::string state);

   std::string focusState_;
   std::string laserState_;
   std::string commandState_;
   bool initialized_;
   std::string port_;
   long waitAfterLock_;
};

#endif
