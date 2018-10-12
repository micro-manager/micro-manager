///////////////////////////////////////////////////////////////////////////////
// FILE:          SmarActHCU-3D.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   SmarAct HCU 3D stage, need special firmware 
//
// AUTHOR:        Joran Deschamps, EMBL, 2014 
//				  joran.deschamps@embl.de 
//
// LICENSE:       LGPL
//


#ifndef _SMARACT_H_
#define _SMARACT_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include <string>
#include <map>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//

#define ERR_PORT_CHANGE_FORBIDDEN 99
#define ERR_IDENTIFICATION_FAIL 1000
#define ERR_PARSING 1001
#define ERR_UNKNWON_COMMAND 1002
#define ERR_INVALID_CHANNEL 1003
#define ERR_INVALID_MODE 1004
#define ERR_SYNTAX 1013
#define ERR_OVERFLOW 1015
#define ERR_INVALID_PARAMETER 1017
#define ERR_MISSING_PARAMETER 1018
#define ERR_NO_SENSOR_PRESENT 1019
#define ERR_WRONG_SENSOR_TYPE 1020
#define ERR_UNKNOWN_ERROR 1021

class XYStage : public CXYStageBase<XYStage>
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
	int IsXYStageSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}
	bool IsContinuousFocusDrive() const {return false;}
	int Home();
	int Stop();

	// Stage API
	// ---------

	// setters
	int SetPositionUm(double x, double y);
	int SetOrigin();
	int SetPositionSteps(long x, long y);
	int SetFrequency(int x);
	int SetRelativePositionUm(double x, double y);
	int SetErrorReporting(boolean reporting);

	// getters
	int GetPositionSteps(long& x, long& y);
	int GetLimitsUm(double& xMin, double& xMax, double& yMin, double& yMax);
	int GetPositionUm(double& x, double& y);
	double GetStepSizeXUm();
	double GetStepSizeYUm();
	int GetController(std::string* controller);
	int GetID(std::string* id);
	int GetStepLimits(long &xMin, long &xMax, long &yMin, long &yMax);

	// action interface
	// ----------------
	int OnLimit(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnFrequency(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnHold(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);


private:
	std::string port_;
	bool initialized_;
	double curPos_x_;
	double curPos_y_;
	double answerTimeoutMs_;
	int reverseX_;
	int reverseY_;
	int freqXY_;
	int channelX_;
	int channelY_;
	int holdtime_;
	std::string id_;
	std::string controller_;
};

class ZStage : public CStageBase<ZStage>
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

	int IsStageSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}
	bool IsContinuousFocusDrive() const {return false;}

	// Stage API
	// ---------

	// setters
	int SetPositionUm(double pos);
	int SetRelativePositionUm(double pos);
	int SetPositionSteps(long steps);
	int SetOrigin();
	int SetFrequency(int x);
	int SetErrorReporting(boolean reporting);

	// getters
	int GetPositionUm(double& pos);
	int GetPositionSteps(long& steps);
	int GetLimits(double& min, double& max);
	int GetController(std::string* controller);
	int GetID(std::string* id);

	// action interface
	// ----------------
	int OnLimit(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnFrequency(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
	std::string port_;
	bool initialized_;
	int channelZ;
	double answerTimeoutMs_;
	int reverseZ_;
	int freqZ_;
	int channelZ_;
	int holdtime_;
	std::string id_;
	std::string controller_;
};

#endif //_Smaract_H_