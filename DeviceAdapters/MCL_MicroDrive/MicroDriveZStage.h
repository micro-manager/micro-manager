/*
File:		MicroDriveZStage.h
Copyright:	Mad City Labs Inc., 2019
License:	Distributed under the BSD license.
*/
#pragma once

// MCL headers
#include "MicroDrive.h"
#include "MCL_MicroDrive.h"

// MM headers
#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/ImgBuffer.h"
#include "../../MMDevice/ModuleInterface.h"

// List
#include "handle_list_if.h"
#include "HandleListType.h"

#define ERR_UNKNOWN_MODE         102
#define ERR_UNKNOWN_POSITION     103
#define ERR_NOT_VALID_INPUT      104

class MCL_MicroDrive_ZStage : public CStageBase<MCL_MicroDrive_ZStage>
{
public:

	MCL_MicroDrive_ZStage();
	~MCL_MicroDrive_ZStage();

	// Device Interface
	int Initialize();
	int Shutdown();
	bool Busy();
	void GetName(char* pszName) const;

	// Stage API
	virtual double GetStepSize();
	virtual int SetPositionUm(double pos);
	virtual int GetPositionUm(double& pos);
	virtual int SetRelativePositionUm(double d);
	virtual int SetPositionSteps(long steps);
    virtual int GetPositionSteps(long& steps);
    virtual int SetOrigin();
	virtual int GetLimits(double& lower, double& upper);
	virtual int IsStageSequenceable(bool& isSequenceable) const;
	virtual bool IsContinuousFocusDrive() const;


	int getHandle(){ return handle_;}

	// Action interface
	int OnPositionMm(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSetOrigin(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnCalibrate(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnReturnToOrigin(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnVelocity(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnEncoded(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnIterativeMove(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnImRetry(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnImToleranceUm(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnIsTirfModule(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnFindEpi(MM::PropertyBase* pProp, MM::ActionType eAct);


private:
	// Initialization
	int CreateZStageProperties();

	// Set/Get positions
	int SetPositionMm(double z);
	int GetPositionMm(double& z);
	int SetRelativePositionMm(double z);

	// Calibration & origin methods
	int Calibrate();
	int MoveToForwardLimit();
	int ReturnToOrigin();
	int FindEpi();

	void PauseDevice();
	int ChooseAvailableStageAxis(unsigned short pid, unsigned char axisBitmap, int handle);

	// Device Information
	int handle_;
	int serialNumber_;
	unsigned short pid_;
	int axis_;
	double stepSize_mm_;
	double encoderResolution_; 
	double maxVelocity_;
	double minVelocity_;
	double velocity_;
	// Device State
	bool busy_;
	bool initialized_;
	bool encoded_;
	double lastZ_;
	// Iterative Move State
	bool iterativeMoves_;
	int imRetry_;
	double imToleranceUm_;
	// Tirf-Module State
	bool deviceHasTirfModuleAxis_;
	bool axisIsTirfModule_;
	double tirfModCalibrationMm_;
};
