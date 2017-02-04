/*
File:		MCL_NanoDrive_XYStage.h
Copyright:	Mad City Labs Inc., 2008
License:	Distributed under the BSD license.
*/
#ifndef _MCL_NANODRIVE_XYSTAGE_H_
#define _MCL_NANODRIVE_XYSTAGE_H_

#pragma once 

// MCL headers
#include "Madlib.h"
#include "MCL_NanoDrive.h"

// MM headers
#include "../../MMDevice/ModuleInterface.h"
#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"

// List/heap headers
#include "device_list.h"
#include "handle_list_if.h"
#include "HandleListType.h"
#include "heap.h"

class MCL_NanoDrive_XYStage : public CXYStageBase<MCL_NanoDrive_XYStage>
{
public:
	
	MCL_NanoDrive_XYStage();
	~MCL_NanoDrive_XYStage();

	bool Busy();
	void GetName(char* name) const;

	int Initialize();
	int Shutdown();

	// XY Stage API
    virtual double GetStepSize();
    virtual int SetPositionSteps(long x, long y);
    virtual int GetPositionSteps(long &x, long &y);
	virtual int SetRelativePositionUm(double dx, double dy);
    virtual int Home();
    virtual int Stop();
    virtual int SetOrigin();
    virtual int GetLimits(double& lower, double& upper);
    virtual int GetLimitsUm(double& xMin, double& xMax, double& yMin, double& yMax);
	virtual int GetStepLimits(long& xMin, long& xMax, long& yMin, long& yMax);
	virtual double GetStepSizeXUm();
	virtual double GetStepSizeYUm();
	virtual int IsXYStageSequenceable(bool& isSequenceable) const;
	int getHandle(){  return MCLhandle_;}


	// Action interface
	int OnPositionXUm(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnPositionYUm(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSettlingTimeXMs(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSettlingTimeYMs(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSetOrigin(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnCommandChangedX(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnCommandChangedY(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
	int SetDeviceProperties();
	int SetPositionXUm(double x);
	int SetPositionYUm(double y);
	void PauseDevice(int axis);
	int SetPositionUm(double x, double y);
    int GetPositionUm(double& x, double& y);

	bool busy_;
	bool initialized_;
	bool is20Bit_;

	int MCLhandle_;

	double stepSizeX_um_;
	double stepSizeY_um_;

	double xMin_;
	double xMax_;
	double yMin_;
	double yMax_;
	
	int serialNumber_;
	int settlingTimeX_ms_;
	int settlingTimeY_ms_;

	double curXpos_;
	double curYpos_;

	bool firstWriteX_;
	bool firstWriteY_;

	double commandedX_;
	double commandedY_;
};

#endif // _MCL_NANODRIVE_XYSTAGE_H_