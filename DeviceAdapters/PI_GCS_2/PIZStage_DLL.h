///////////////////////////////////////////////////////////////////////////////
// FILE:          PIZStage_DLL.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   PI GCS DLL ZStage
//
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 08/28/2006
//                Steffen Rau, s.rau@pi.ws, 28/03/2008
// COPYRIGHT:     University of California, San Francisco, 2006
//                Physik Instrumente (PI) GmbH & Co. KG, 2008
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
// CVS:           $Id: PIZStage_DLL.h,v 1.7, 2014-05-27 07:22:43Z, Steffen Rau$
//

#ifndef _PI_ZSTAGE_DLL_H_
#define _PI_ZSTAGE_DLL_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"

class PIController;

class PIZStage : public CStageBase<PIZStage>
{
public:
   PIZStage();
   ~PIZStage();
  
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

   int IsStageSequenceable(bool& isSequenceable) const { isSequenceable = false; return DEVICE_OK; }
   bool IsContinuousFocusDrive() const { return false; }

   // action interface
   // ----------------
   int OnControllerName(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStepSizeUm(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnAxisName(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnAxisLimit(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnAxisTravelRange(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStageType(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnHoming(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnVelocity(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   std::string axisName_;
   double stepSizeUm_;
   bool initialized_;
   double axisLimitUm_;
   bool invertTravelRange_;
   std::string stageType_;
   std::string controllerName_;
   PIController* ctrl_;
};


#endif //_PI_ZSTAGE_DLL_H_
