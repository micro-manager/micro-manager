///////////////////////////////////////////////////////////////////////////////
// FILE:          XYStage.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Thorlabs device adapters: BBD102 Controller
//
// COPYRIGHT:     Thorlabs Inc, 2011
//
// LICENSE:       This file is distributed under the BSD license.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 2011
//                http://nenad.amodaj.com
//

#ifndef _XYSTAGE_H_
#define _XYSTAGE_H_

#include <MMDevice.h>
#include <DeviceBase.h>
#include "Thorlabs.h"
#include "MotorStage.h"

//////////////////////////////////////////////////////////////////////////////
// XYStage class
// (device adapter)
//////////////////////////////////////////////////////////////////////////////
class ThorlabsStage;

class XYStage : public CXYStageBase<XYStage>
{
public:
   XYStage();
   ~XYStage();

   // Device API
   // ----------
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy();

   // XYStage API
   // -----------
   int SetPositionSteps(long x, long y);
   int SetRelativePositionSteps(long x, long y);
   int GetPositionSteps(long& x, long& y);
   int Home();
   int Stop();
   int SetOrigin();
   int GetLimitsUm(double& xMin, double& xMax, double& yMin, double& yMax);
   int GetStepLimits(long& xMin, long& xMax, long& yMin, long& yMax);
   double GetStepSizeXUm();
   double GetStepSizeYUm();
   int IsXYStageSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}

   // action interface
   // ----------------
   int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStepSizeX(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStepSizeY(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnMaxVelocity(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnAcceleration(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnMoveTimeout(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   
   enum Axis {X, Y};

   int MoveBlocking(long x, long y, bool relative = false);
   int SetCommand(const unsigned char* command, unsigned cmdLength);
   int GetCommand(unsigned char* answer, unsigned answerLength, double TimeoutMs);
   int SetVelocityProfile(const MOTVELPARAMS& params, Axis a);
   int GetVelocityProfile(MOTVELPARAMS& params, Axis a);
   int ParseVelocityProfile(const unsigned char* buf, int bufLen, MOTVELPARAMS& params);
   int GetStatus(DCMOTSTATUS& stat, Axis a);

   class CommandThread;

   bool initialized_;            // true if the device is intitalized
   bool home_;                   // true if stage is homed
   std::string port_;            // com port name
   double answerTimeoutMs_;      // max wait for the device to answer
   double moveTimeoutMs_;        // max wait for stage to finish moving
   MotorStage *xstage_;          // x-axis stage device
   MotorStage *ystage_;          // y-axis stage device
   HWINFO info_;                 // hardware information
   CommandThread* cmdThread_;    // thread used to execute move commands
};

#endif //_XYSTAGE_H_
