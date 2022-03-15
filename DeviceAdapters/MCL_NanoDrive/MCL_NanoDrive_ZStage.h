/*
File:		MCL_NanoDrive_ZStage.h
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

#include <vector>

class MCL_NanoDrive_ZStage : public CStageBase<MCL_NanoDrive_ZStage>
{
public:

	MCL_NanoDrive_ZStage();
	~MCL_NanoDrive_ZStage();

	bool Busy() { return false; }
	void GetName(char* pszName) const;

	int Initialize();
	int Shutdown();
     
	// Stage API
	virtual int SetPositionUm(double pos);
	virtual int GetPositionUm(double& pos);
	virtual int SetRelativePositionUm(double d); 
	virtual double GetStepSize();
	virtual int SetPositionSteps(long steps);
	virtual int GetPositionSteps(long& steps);
	virtual int SetOrigin();
	virtual int GetLimits(double& lower, double& upper);
	virtual int IsStageSequenceable(bool& isSequenceable) const;
	virtual int GetStageSequenceMaxLength(long& nrEvents) const;
	virtual int StartStageSequence();
	virtual int StopStageSequence();
	virtual int ClearStageSequence();
	virtual int AddToStageSequence(double position);
	virtual int SendStageSequence();
	virtual bool IsContinuousFocusDrive() const;

	// Action interface
	int OnLowerLimit(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnUpperLimit(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnPositionUm(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSettlingTimeZMs(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSetOrigin(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnCommandChanged(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTLC(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSetSequence(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSetShiftSequence(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSetTirfLock(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSetTirfBlockedAction(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
	int InitDeviceAdapter();
	int CreateZStageProperties();
	double GetLastCommandedPosition();

	int handle_;
	int serialNumber_;
	int axis_;
	double calibration_;
	int dacBits_;
	int seqMaxSize_;

	double stepSize_um_;
	double lowerLimit_;
	double upperLimit_;

	int settlingTimeZ_ms_;
	double commandedZ_;

	std::vector<double> sequence_;

	bool initialized_;
	bool firstWrite_; 
	bool supportsLastCommanded_;
	bool canSupportSeq_;
	bool supportsSeq_;
	bool shiftSequence_;
	bool axisUsedForTirfControl_;
	bool ignoreZMovesInTirfLock_;
};