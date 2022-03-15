/*
File:		MCL_NanoDrive_XYStage.h
Copyright:	Mad City Labs Inc., 2019
License:	Distributed under the BSD license.
*/
#pragma once 

// MCL headers
#include "Madlib.h"
#include "MCL_NanoDrive.h"

// MM headers
#include "../../MMDevice/ModuleInterface.h"
#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"

// List/heap headers
#include "handle_list_if.h"
#include "HandleListType.h"

class MCL_NanoDrive_XYStage : public CXYStageBase<MCL_NanoDrive_XYStage>
{
public:
	
	MCL_NanoDrive_XYStage();
	~MCL_NanoDrive_XYStage();

	bool Busy() { return false; }
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

	// Action interface
	int OnLowerLimitX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnUpperLimitX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnLowerLimitY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnUpperLimitY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnPositionXUm(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnPositionYUm(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSettlingTimeXMs(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSettlingTimeYMs(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSetOrigin(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnCommandChangedX(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnCommandChangedY(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
	int InitDeviceAdapter();
	int CreateDeviceProperties();

	int SetPositionXUm(double x, bool pauseForSettingTime);
	int SetPositionYUm(double y, bool pauseForSettingTime);
	int SetPositionUm(double x, double y);
    int GetPositionUm(double& x, double& y);
	void GetLastCommandedPosition(double& x, double& y);

	int handle_;
	int serialNumber_;
	int axisX_;
	int axisY_;
	double calibrationX_;
	double calibrationY_;

	double stepSizeX_um_;
	double stepSizeY_um_;
	double lowerLimitX_;
	double upperLimitX_;
	double lowerLimitY_;
	double upperLimitY_;

	int settlingTimeX_ms_;
	int settlingTimeY_ms_;
	double commandedX_;
	double commandedY_;

	bool initialized_;
	bool is20Bit_;
	bool firstWriteX_;
	bool firstWriteY_;
	bool supportsLastCommanded_;
};