///////////////////////////////////////////////////////////////////////////////
// FILE:          PiezoZStage.h
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

#ifndef _PIEZOZSTAGE_H_
#define _PIEZOZSTAGE_H_

#include <MMDevice.h>
#include <DeviceBase.h>

//////////////////////////////////////////////////////////////////////////////
// PiezoZStage class
// (device adapter)
//////////////////////////////////////////////////////////////////////////////
class PiezoZStage : public CStageBase<PiezoZStage>
{
public:
   PiezoZStage();
   ~PiezoZStage();
  
   // Device API
   // ----------
   int Initialize();
   int Shutdown();
  
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
   int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct);


private:
   int SetCommand(const unsigned char* command, unsigned length);
   int GetCommand(unsigned char* response, unsigned length, double timeoutMs);
   bool GetValue(std::string& sMessage, double& pos);
   int SetMaxTravel();
   double GetTravelTimeMs(long steps);
   int SetZero();
   bool IsZeroed();

   std::string port_;
   double stepSizeUm_;
   bool initialized_;
   double answerTimeoutMs_;
   double maxTravelUm_;
   double curPosUm_; // cached current position
   bool zeroed_;
   bool zeroInProgress_;
};

#endif //_PIEZOZSTAGE_H_
