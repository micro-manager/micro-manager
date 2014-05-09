///////////////////////////////////////////////////////////////////////////////
// FILE:          LStep.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Marzhauser L-Step Controller Driver
//                XY Stage
//                Z  Stage
//
// AUTHORS:        Original Marzhauser Tango adapter code by Falk Dettmar, falk.dettmar@marzhauser-st.de, 09/04/2009
//					Modifications for Marzhauser L-Step controller by Gilles Courtand, gilles.courtand@u-bordeaux.fr, 
//					and Brice Bonheur brice.bonheur@u-bordeaux.fr 08/03/2012
// COPYRIGHT:     Marzhauser SensoTech GmbH, Wetzlar, 2009
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

#ifndef _LSTEP_H_
#define _LSTEP_H_


#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include <string>
#include <map>

//////////////////////////////////////////////////////////////////////////////


#define ERR_PORT_CHANGE_FORBIDDEN    10004


class LStepBase;

class LStepDeviceBase : public CDeviceBase<MM::Device, LStepDeviceBase>
{
public:
   LStepDeviceBase() { }
   ~LStepDeviceBase() { }

   friend class LStepBase;
};

class LStepBase
{
public:
   LStepBase(MM::Device *device);
   ~LStepBase();

   int ClearPort(void);
   int CheckDeviceStatus(void);
   int SendCommand(const char *command) const;
   int QueryCommandACK(const char *command);
   int QueryCommand(const char *command, std::string &answer) const;

protected:
   MM::Core *core_;
   bool initialized_;
   int  Configuration_;
   std::string port_;
   LStepDeviceBase *device_;
};



class XYStage : public CXYStageBase<XYStage>, public LStepBase
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
	int SetPositionUm(double x, double y);
	int SetRelativePositionUm(double dx, double dy);
	int SetAdapterOriginUm(double x, double y);
	int GetPositionUm(double& x, double& y);
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

private:
   bool range_measured_;
   double stepSizeXUm_;
   double stepSizeYUm_;
   double speedX_;
   double velocityXmm_;//G
   double velocityYmm_;//G
   double speedY_;
   double accelX_;
   double accelY_;
   double originX_;
   double originY_;
   double pitchX_;
   double pitchY_;
};


class ZStage : public CStageBase<ZStage>, public LStepBase
{
public:
   ZStage();
   ~ZStage();
  
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
   int SetAdapterOriginUm(double d);

   int GetPositionUm(double& pos);
   int SetPositionSteps(long steps);
   int GetPositionSteps(long& steps);
   int SetOrigin();
   int SetAdapterOrigin();
   int Stop();
   int GetLimits(double& min, double& max);

   int IsStageSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}

   bool IsContinuousFocusDrive() const {return false;}

   // action interface
   // ----------------
   int OnPort     (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStepSize (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSpeed    (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnAccel    (MM::PropertyBase* pProp, MM::ActionType eAct);


private:
   bool range_measured_;
   double stepSizeUm_;
   double speedZ_;
   double accelZ_;
   double originZ_;
   double pitchZ_;
   double velocityZmm_;

};


#endif //_LSTEP_H_
