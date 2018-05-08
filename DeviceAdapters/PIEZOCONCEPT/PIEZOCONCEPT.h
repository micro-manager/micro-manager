/*
 * PIEZOCONCEPT Device Adapter
 *
 * Copyright (C) 2008 Regents of the University of California
 *               2018 PIEZOCONCEPT
 *
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 *
 * IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *
 * Based on the Arduino device adapter by Nico Stuurman
 */
#pragma once

#include "DeviceBase.h"

#include <string>


class PiezoConceptHub : public HubBase<PiezoConceptHub>
{
public:
   PiezoConceptHub();
   ~PiezoConceptHub();

   int Initialize();
   int Shutdown();
   void GetName(char* pszName) const;
   bool Busy();

   // serial port scanning
   bool SupportsDeviceDetection() { return true; }
   MM::DeviceDetectionStatus DetectDevice();

   // peripheral discovery
   int DetectInstalledDevices();

   // property handlers
   int OnPort(MM::PropertyBase* pPropt, MM::ActionType eAct);
   int OnLogic(MM::PropertyBase* pPropt, MM::ActionType eAct);
   int OnVersion(MM::PropertyBase* pPropt, MM::ActionType eAct);

   // custom interface for child devices
   bool IsPortAvailable() { return portAvailable_; }

   int WriteToComPortH(const unsigned char* command, unsigned len)
   { return WriteToComPort(port_.c_str(), command, len); }

   int GetAxisInfoH(int axis, double& travel) { return GetAxisInfo(axis, travel); }

   int CheckForError();

private:
   int GetControllerInfo();
   int GetAxisInfo(int, double&);

   std::string port_;
   bool initialized_;
   bool portAvailable_;
   char version_[64];

   bool hasZStage_;
   bool hasXYStage_;
};


class CPiezoConceptStage : public CStageBase<CPiezoConceptStage>
{
public:
   CPiezoConceptStage();
   ~CPiezoConceptStage();

   bool Busy();
   void GetName(char* pszName) const;

   int Initialize();
   int Shutdown();

   // Stage API
   int SetPositionUm(double pos);
   int GetPositionUm(double& pos);
   double GetStepSize() {return stepSizeUm_;}

   int SetPositionSteps(long steps);
   int GetPositionSteps(long& steps);

   int SetOrigin() { return DEVICE_UNSUPPORTED_COMMAND; }

   int GetLimits(double& lower, double& upper)
   {
      lower = lowerLimit_;
      upper = upperLimit_;
      return DEVICE_OK;
   }

   int Move(double /*v*/) { return DEVICE_UNSUPPORTED_COMMAND; }

   bool IsContinuousFocusDrive() const { return false; }

   int OnStageMinPos(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStageMaxPos(MM::PropertyBase* pProp, MM::ActionType eAct);

   int IsStageSequenceable(bool& isSequenceable) const
   { isSequenceable = false; return DEVICE_OK; }
   int GetStageSequenceMaxLength(long& nrEvents) const
   { nrEvents = 0; return DEVICE_OK; }

private:
   double stepSizeUm_;
   double pos_um_;
   bool busy_;
   bool initialized_;
   double lowerLimit_;
   double upperLimit_;

   int MoveZ(double pos);
};


class CPiezoConceptXYStage : public CXYStageBase<CPiezoConceptXYStage>
{
public:
   CPiezoConceptXYStage();
   ~CPiezoConceptXYStage();

   bool Busy();
   void GetName(char* pszName) const;

   int Initialize();
   int Shutdown();

   int GetPositionSteps(long& x, long& y);
   int SetPositionSteps(long x, long y);
   int SetRelativePositionSteps(long, long);

   virtual int Home() { return DEVICE_UNSUPPORTED_COMMAND; }
   virtual int Stop() { return DEVICE_UNSUPPORTED_COMMAND; }
   virtual int SetOrigin() { return DEVICE_UNSUPPORTED_COMMAND; }

   virtual int GetLimits(double& lower, double& upper)
   {
      lower = lowerLimitX_;
      upper = upperLimitY_;
      return DEVICE_OK;
   }

   virtual int GetLimitsUm(double& xMin, double& xMax, double& yMin, double& yMax)
   {
      xMin = lowerLimitX_; xMax = upperLimitX_;
      yMin = lowerLimitY_; yMax = upperLimitY_;
      return DEVICE_OK;
   }

   virtual int GetStepLimits(long& /*xMin*/, long& /*xMax*/, long& /*yMin*/, long& /*yMax*/)
   { return DEVICE_UNSUPPORTED_COMMAND; }

   double GetStepSizeXUm()
   {
      return stepSize_X_um_;
   }

   double GetStepSizeYUm()
   {
      return stepSize_Y_um_;
   }

   int Move(double /*vx*/, double /*vy*/) { return DEVICE_OK; }

   int IsXYStageSequenceable(bool& isSequenceable) const { isSequenceable = false; return DEVICE_OK; }

   int OnXStageMinPos(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnXStageMaxPos(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnYStageMinPos(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnYStageMaxPos(MM::PropertyBase* pProp, MM::ActionType eAct);

   int OnStepsPerSecond(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnMicrostepMultiplier(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   double stepSize_X_um_;
   double stepSize_Y_um_;
   double posX_um_;
   double posY_um_;
   unsigned int driveID_X_;
   unsigned int driveID_Y_;

   bool busy_;
   MM::TimeoutMs* timeOutTimer_;
   double velocity_;
   bool initialized_;
   double lowerLimitX_;
   double upperLimitX_;
   double lowerLimitY_;
   double upperLimitY_;

   int MoveX(double);
   int MoveY(double);
};
