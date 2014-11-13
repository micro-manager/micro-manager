///////////////////////////////////////////////////////////////////////////////
// FILE:          Stage.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Stage
//
// AUTHOR:        Athabasca Witschi, athabasca@zaber.com

// COPYRIGHT:     Zaber Technologies, 2014

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

#ifndef _STAGE_H_
#define _STAGE_H_

#include "Zaber.h"

extern const char* g_StageName;
extern const char* g_StageDescription;

class Stage : public CStageBase<Stage>, public ZaberBase
{
public:
	Stage();
	~Stage();

	// Device API
	// ----------
	int Initialize();
	int Shutdown();
	void GetName(char* name) const;
	bool Busy();

	// Stage API
	// ---------
	int GetPositionUm(double& pos);
	int GetPositionSteps(long& steps);
	int SetPositionUm(double pos);
	int SetRelativePositionUm(double d);
	int SetPositionSteps(long steps);
	int SetRelativePositionSteps(long steps);
	int Move(double velocity);
	int Stop();
	int Home();
	int SetAdapterOriginUm(double d);
	int SetOrigin();
	int GetLimits(double& lower, double& upper);

	int IsStageSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}
	bool IsContinuousFocusDrive() const {return false;}

	// action interface
	// ----------------
	int OnPort         (MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnDeviceNum    (MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnAxisNum      (MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnMotorSteps   (MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnLinearMotion (MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSpeed        (MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnAccel        (MM::PropertyBase* pProp, MM::ActionType eAct);

private:
	long deviceNum_;
	long axisNum_;
	int homingTimeoutMs_;
	double stepSizeUm_;
	double convFactor_; // not very informative name
	std::string cmdPrefix_;
	long resolution_;
	long motorSteps_;
	double linearMotion_;
};

#endif //_STAGE_H_
