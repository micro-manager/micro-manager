//////////////////////////////////////////////////////////////////////////////
// FILE:          Conex_Axis.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Newport Conex axes Driver
//                X Stage
//                Y Stage
//                Z Stage
//
// AUTHORS:       Jean_Pierre Gaillet JPG Micro-Services
// COPYRIGHT:     JPG Micro-Services Newport 2013
// LICENSE:       This library is free software; you can redistribute it and/or
//                modify it under the terms of the GNU Lesser General Public
//                License as published by the Free Software Foundation.
//                
//                You should have received a copy of the GNU Lesser General Public
//                License along with the source distribution; if not, write to
//                the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
//                Boston, MA  02111-1307  USA
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.  
//

#ifndef _Conex_Axis_H_
#define _Conex_Axis_H_


#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include <string>



//////////////////////////////////////////////////////////////////////////////


#define ERR_PORT_CHANGE_FORBIDDEN    10004


// N.B. Concrete device classes deriving Conex_AxisBase must set core_ in
// Initialize().
class Conex_AxisBase
{
public:
   Conex_AxisBase(MM::Device *device);
   virtual ~Conex_AxisBase();

   int ClearPort(void);
   int CheckDeviceStatus(void);
   int SendCommand(const char *command) const;
   int QueryCommand(const char *command, std::string &answer) const;
   double GetSpeed();
   double GetAcceleration(void);
   double GetPosition(void);
   double GetNegativeLimit(void);
   double GetPositiveLimit(void);
   int SetSpeed(double speed);
   int SetAcceleration(double acceleration);
   int SetPositiveLimit(double limit);
   int SetNegativeLimit(double limit);
   bool Moving();
   bool Referenced();
   bool Ready();
   bool Enabled();
   int Stop();
   int BaseHome();
   int HomeCurrentPosition();
   int MoveRelative(double position);
   int MoveAbsolute( double target);
   int ChangeCoef(double coef);
   int Enable();
   int Disable();
   void test();

protected:
   bool initialized_;
   std::string port_;
   MM::Device *device_;
   MM::Core *core_;
   double speed_;
   double acceleration_;
   double coef_;
};

class X_Axis : public CStageBase<X_Axis>, public Conex_AxisBase
{
public:
   X_Axis();
   ~X_Axis();
  
   // Device API
   // ----------
   int Initialize();
   int Shutdown();
   void GetName(char* pszName) const;
   bool Busy();

   // Stage API
   // ---------
   int SetPositionUm(double pos);
   int SetRelativePositionUm(double d);
   int Move(double velocity);
 //  int SetAdapterOriginUm(double d);

   int GetPositionUm(double& pos);
   int SetPositionSteps(long steps);
   int GetPositionSteps(long& steps);
   int SetOrigin();
//   int SetAdapterOrigin();
   int GetLimits(double& min, double& max);

   int IsStageSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}

   bool IsContinuousFocusDrive() const {return false;}

   // action interface
   // ----------------
   int OnPort     (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSearchHomeNow(MM::PropertyBase* pProp, MM::ActionType eAct);
};


class Y_Axis : public CStageBase<Y_Axis>, public Conex_AxisBase
{
public:
   Y_Axis();
   ~Y_Axis();
  
   // Device API
   // ----------/  
   int Initialize();
   int Shutdown();
   void GetName(char* pszName) const;
   bool Busy();

   // Stage API
   // ---------
   int SetPositionUm(double pos);
   int SetRelativePositionUm(double d);
   int Move(double velocity);

   int GetPositionUm(double& pos);
   int SetPositionSteps(long steps);
   int GetPositionSteps(long& steps);
   int SetOrigin();
  
   int GetLimits(double& min, double& max);

   int IsStageSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}

   bool IsContinuousFocusDrive() const {return false;}

   // action interface
   // ----------------
   int OnPort     (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSearchHomeNow(MM::PropertyBase* pProp, MM::ActionType eAct);
};


class Z_Axis : public CStageBase<Z_Axis>, public Conex_AxisBase
{
public:
   Z_Axis();
   ~Z_Axis();
  
   // Device API
   // ----------
   int Initialize();
   int Shutdown();
   void GetName(char* pszName) const;
   bool Busy();

   // Stage API
   // ---------
   int SetPositionUm(double pos);
   int SetRelativePositionUm(double d);
   int Move(double velocity);

   int GetPositionUm(double& pos);
   int SetPositionSteps(long steps);
   int GetPositionSteps(long& steps);
   int SetOrigin();
   
   int GetLimits(double& min, double& max);

   int IsStageSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}

   bool IsContinuousFocusDrive() const {return false;}

   // action interface
   // ----------------
   int OnPort     (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSearchHomeNow(MM::PropertyBase* pProp, MM::ActionType eAct);
};


#endif //_Conex_Axis_H_
