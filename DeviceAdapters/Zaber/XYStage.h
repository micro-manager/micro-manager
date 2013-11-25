///////////////////////////////////////////////////////////////////////////////
// FILE:          XYStage.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   XY Stage
//
// AUTHOR:        David Goosen, david.goosen@zaber.com
// COPYRIGHT:     Zaber Technologies, 2013
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

#ifndef _XYSTAGE_H_
#define _XYSTAGE_H_

#include "Zaber.h"
#include <string>
#include <MMDevice.h>
#include <DeviceBase.h>

extern const char *XYStageName;

class XYStage : public CXYStageBase<XYStage>, public ZaberBase
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
      //int SetPositionUm(double x, double y);
      //int SetRelativePositionUm(double dx, double dy);
      //int SetAdapterOriginUm(double x, double y);
      //int GetPositionUm(double& x, double& y);
      int GetLimitsUm(double& xMin, double& xMax, double& yMin, double& yMax);
      int Move(double vx, double vy);
      int SetPositionSteps(long x, long y);
      int GetPositionSteps(long& x, long& y);
      int SetRelativePositionSteps(long x, long y);
      int Home();
      int Stop();
      int SetOrigin();
      int SetAdapterOrigin();
      int GetStepLimits(long& xMin, long& xMax, long& yMin, long& yMax);
      double GetStepSizeXUm() {return stepSizeXUm_;}
      double GetStepSizeYUm() {return stepSizeYUm_;}

      int IsXYStageSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}

      // action interface
      // ----------------
      int OnPort      (MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnStepSizeX (MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnStepSizeY (MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnSpeedX    (MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnSpeedY    (MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnAccelX    (MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnAccelY    (MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnBacklashX (MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnBacklashY (MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnDeviceNum (MM::PropertyBase* pProp, MM::ActionType eAct);

   private:
      long deviceNum_;
      bool range_measured_;
      double answerTimeoutMs_;
      double stepSizeXUm_;
      double stepSizeYUm_;
      double speedX_;
      double speedY_;
      double accelX_;
      double accelY_;
      double originX_;
      double originY_;
      std::string cmdPrefix_;
};

#endif //_XYSTAGE_H_
