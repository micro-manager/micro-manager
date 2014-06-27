///////////////////////////////////////////////////////////////////////////////
// FILE:          PicardStage.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   The drivers for the Picard Industries USB stages
//                Based on the CDemoStage and CDemoXYStage classes
//
// AUTHORS:       Johannes Schindelin, Luke Stuyvenberg, 2011 - 2014
//
// COPYRIGHT:     Board of Regents of the University of Wisconsin -- Madison,
//					Copyright (C) 2011 - 2014
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

#ifndef _PICARDSTAGE_H_
#define _PICARDSTAGE_H_

#include "../../MMDevice/DeviceBase.h"

//////////////////////////////////////////////////////////////////////////////
// CPiTwister class
//////////////////////////////////////////////////////////////////////////////

class CPiTwister: public CStageBase<CPiTwister>
{
public:
	CPiTwister();
	~CPiTwister();

	bool Busy();
	int Initialize();
	int Shutdown();
	void GetName(char* name) const;

	int SetPositionUm(double pos);
	int GetPositionUm(double& pos);
	int SetPositionSteps(long steps);
	int GetPositionSteps(long& steps);
	int SetOrigin();
	int GetLimits(double& lower, double& upper);
	int GetStepLimits(long& lower, long& upper);
	int IsStageSequenceable(bool& isSequenceable) const;
	bool IsContinuousFocusDrive() const;

	double GetStepSizeUm();

private:
	int OnSerialNumber(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnVelocity(MM::PropertyBase* pProp, MM::ActionType eAct);

	int serial_;
	int velocity_;
	void *handle_;
};

//////////////////////////////////////////////////////////////////////////////
// CPiStage class
//////////////////////////////////////////////////////////////////////////////

class CPiStage : public CStageBase<CPiStage>
{
public:
	CPiStage();
	~CPiStage();

	bool Busy();
	int Initialize();
	int Shutdown();
	void GetName(char* name) const;

	int SetPositionUm(double pos);
	int GetPositionUm(double& pos);
	int SetPositionSteps(long steps);
	int GetPositionSteps(long& steps);
	int SetOrigin();
	int GetLimits(double& lower, double& upper);
	int GetStepLimits(long& lower, long& upper);
	int IsStageSequenceable(bool& isSequenceable) const;
	bool IsContinuousFocusDrive() const;

	double GetStepSizeUm();

private:
	int OnSerialNumber(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnVelocity(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnGoHomeProp(MM::PropertyBase* pProp, MM::ActionType eAct);

	int serial_;
	int velocity_;
	void *handle_;
};

//////////////////////////////////////////////////////////////////////////////
// CPiXYStage class
//////////////////////////////////////////////////////////////////////////////

class CPiXYStage : public CXYStageBase<CPiXYStage>
{
public:
	CPiXYStage();
	~CPiXYStage();

	bool Busy();
	int Initialize();
	int Shutdown();
	void GetName(char* name) const;

	int SetPositionUm(double x, double y);
	int GetPositionUm(double& x, double& y);
	int SetPositionSteps(long x, long y);
	int GetPositionSteps(long& x, long& y);
	int Home();
	int Stop();
	int SetOrigin();
	int GetLimitsUm(double& xMin, double& xMax, double& yMin, double& yMax);
	int GetStepLimits(long& xMin, long& xMax, long& yMin, long& yMax);
	int IsXYStageSequenceable(bool& isSequenceable) const;

	double GetStepSizeXUm();
	double GetStepSizeYUm();

protected:
	int InitStage(void** handleptr, int serial);
	void ShutdownStage(void** handleptr);

private:
	int OnSerialNumberX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSerialNumberY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnVelocityX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnVelocityY(MM::PropertyBase* pProp, MM::ActionType eAct);

	int serialX_, serialY_;
	int velocityX_, velocityY_;
	void *handleX_, *handleY_;
};

#endif //_PICARDSTAGE_H_
