#ifndef _MICRODRIVEZSTAGE_H_
#define _MICRODRIVEZSTAGE_H_

// MCL headers
#include "MicroDrive.h"
#include "MCL_MicroDrive.h"

// MM headers
#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/ImgBuffer.h"
#include "../../MMDevice/ModuleInterface.h"

// List/heap headers
#include "device_list.h"
#include "handle_list_if.h"
#include "HandleListType.h"
#include "heap.h"

#include <string>
#include <map>

#define ERR_UNKNOWN_MODE         102
#define ERR_UNKNOWN_POSITION     103
#define ERR_NOT_VALID_INPUT      104


class MCL_MicroDrive_ZStage : public CStageBase<MCL_MicroDrive_ZStage>
{
public:

	MCL_MicroDrive_ZStage();
	~MCL_MicroDrive_ZStage();

	bool Busy();
	void GetName(char* pszName) const;

	int Initialize();
	int Shutdown();

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
	virtual int GetStageSequenceMaxLength(long& nrEvents) const;
	virtual int StartStageSequence() const;
	virtual int StopStageSequence() const;
	virtual int LoadStageSequence(std::vector<double> positions) const;
	virtual bool IsContinuousFocusDrive() const;
	virtual int ClearStageSequence();
	virtual int AddToStageSequence(double position);
	virtual int SendStageSequence() const; 

	int getHandle(){ return MCLhandle_;}

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


private:
	int CreateZStageProperties();

	// Set/Get positions
	int SetPositionMm(double z);
	int GetPositionMm(double& z);
	int SetRelativePositionMm(double z);

	// Calibration & origin methods
	int Calibrate();
	int MoveToForwardLimit();
	int ReturnToOrigin();

	// Pause devices
	void PauseDevice();

	double stepSize_mm_;
    bool busy_;
    bool initialized_;
    int MCLhandle_;
    int serialNumber_;
    int settlingTimeZ_ms_;
	double encoderResolution_; 
	double maxVelocity_;
	double minVelocity_;
	double velocity_;
	int rounding_;
	bool encoded_;
	double lastZ_;
    int axis_;
	bool isMD1_;

	bool iterativeMoves_;
	int imRetry_;
	double imToleranceUm_;

};

#endif