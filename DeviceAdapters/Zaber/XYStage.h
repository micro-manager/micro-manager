///////////////////////////////////////////////////////////////////////////////
// FILE:          XYStage.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   XYStage Device Adapter
//                
// AUTHOR:        David Goosen (david.goosen@zaber.com) & Athabasca Witschi (athabasca.witschi@zaber.com)
//                
// COPYRIGHT:     Zaber Technologies, 2014
//
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

extern const char* g_XYStageName;
extern const char* g_XYStageDescription;

class XYStage : public CXYStageBase<XYStage>, public ZaberBase
{
public:
	XYStage();
	~XYStage();

	// Device API
	// ----------
	int Initialize();
	int Shutdown();
	void GetName(char* name) const;
	bool Busy();

	// XYStage API
	// -----------
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
	int OnPort          (MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnAxisX         (MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnAxisY         (MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnMotorStepsX   (MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnMotorStepsY   (MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnLinearMotionX (MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnLinearMotionY (MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSpeedX        (MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSpeedY        (MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnAccelX        (MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnAccelY        (MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnDeviceNum     (MM::PropertyBase* pProp, MM::ActionType eAct);

private:
	int SendXYMoveCommand(std::string type, long x, long y) const;
	int OnSpeed(long axis, MM::PropertyBase* pProp, MM::ActionType eAct) const;
	int OnAccel(long axis, MM::PropertyBase* pProp, MM::ActionType eAct) const;
	void GetOrientation(bool& mirrorX, bool& mirrorY);

	long deviceNum_;
	bool rangeMeasured_;
	int homingTimeoutMs_;
	double stepSizeXUm_;
	double stepSizeYUm_;
	double convFactor_; // not very informative name
	long axisX_;
	long axisY_;
	std::string cmdPrefix_;
	long resolutionX_;
	long resolutionY_;
	long motorStepsX_;
	long motorStepsY_;
	double linearMotionX_;
	double linearMotionY_;
};

#endif //_XYSTAGE_H_
