///////////////////////////////////////////////////////////////////////////////
// FILE:          Standa.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Standa device adapters: 8SMC1-USBhF Microstep Driver
//
// COPYRIGHT:     Leslie Lab, McGill University, Montreal, 2013
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
// AUTHOR:        Clarence Leung, clarence.leung@mail.mcgill.ca, 2013
//

#ifndef _STANDA_H_
#define _STANDA_H_

// Standa headers
#include "USMCDLL.h"

// MM headers
#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/ModuleInterface.h"

//////////////////////////////////////////////////////////////////////////////
// StandaZStage class
// (device adapter)
//////////////////////////////////////////////////////////////////////////////
class StandaZStage : public CStageBase<StandaZStage>
{
public:
   StandaZStage();
   ~StandaZStage();
  
   // Device API
   // ----------
   int Initialize();
   int Shutdown();
  
   static const char* DeviceName_;
   void GetName(char* pszName) const;
   bool Busy();

   // Stage API
   // ---------
   int SetPositionUm(double pos);
   int GetPositionUm(double& pos);
   int SetPositionSteps(long steps);
   int GetPositionSteps(long& steps);
   int SetOrigin();
   int GetLimits(double& min, double& max);

   int IsStageSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}
   bool IsContinuousFocusDrive() const {return false;}

   // action interface
   // ----------------
   int OnAxisLimit(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnDeviceNumber(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   bool initialized_;
   long curSteps_;
   double stepSizeUm_;
   double axisLimitUm_;
   BYTE stepDivisor_;
   float stageSpeed_;
   MM::TimeoutMs* timeOutTimer_;
   std::string deviceString_;
   DWORD deviceNumber_;
   USMC_State currentState_;
   USMC_StartParameters startParameters_;
};

#endif //_STANDA_H_