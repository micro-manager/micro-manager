//-----------------------------------------------------------------------------
// FILE:          Thorlabs_ElliptecSlider.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Controls the Elliptec sliders ELL6 and ELL9 from Thorlabs 
// COPYRIGHT:     EMBL
// LICENSE:       LGPL
// AUTHOR:        Joran Deschamps and Anindita Dasgupta, EMBL 2018
//-----------------------------------------------------------------------------


#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/ModuleInterface.h"

#include <string>

//////////////////////////////////////////////////////////////////////////////
// device adapter error codes
#define ERR_PORT_CHANGE_FORBIDDEN 101
#define ERR_UNEXPECTED_ANSWER 102
#define ERR_WRONG_DEVICE 103
#define ERR_FORBIDDEN_POSITION_REQUESTED 104
#define ERR_UNKNOWN_STATE 105

// device specific errors
#define ERR_COMMUNICATION_TIME_OUT 201
#define ERR_MECHANICAL_TIME_OUT 202
#define ERR_COMMAND_ERROR_OR_NOT_SUPPORTED 203
#define ERR_VALUE_OUT_OF_RANGE 204
#define ERR_MODULE_ISOLATED 205
#define ERR_MODULE_OUT_OF_ISOLATION 206
#define ERR_INITIALIZING_ERROR 207
#define ERR_THERMAL_ERROR 208
#define ERR_BUSY 209
#define ERR_SENSOR_ERROR 210
#define ERR_MOTOR_ERROR 211
#define ERR_OUT_OF_RANGE 212
#define ERR_OVER_CURRENT_ERROR 213
#define ERR_UNKNOWN_ERROR 214

class ELL9 : public CStateDeviceBase<ELL9>
{
public:
	ELL9();
	~ELL9();

	// MMDevice API
	// ------------
	int Initialize();
	int Shutdown();

	void GetName(char* pszName) const;
	bool Busy();
	unsigned long GetNumberOfPositions()const {return numPos_;}

	int getID(std::string* id);
	int setState(int state);
	int getState(int* state);

	// convenience functions
	bool isError(std::string);
	int getErrorCode(std::string message);
	std::string removeLineFeed(std::string answer);
	std::string removeCommandFlag(std::string message);

	// action interface
	// ----------------
	int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnChannel(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
	std::string port_;
	long numPos_;
	std::string channel_;
	bool initialized_;
	bool busy_;
};

class ELL6 : public CStateDeviceBase<ELL6>
{
public:
	ELL6();
	~ELL6();

	// MMDevice API
	// ------------
	int Initialize();
	int Shutdown();

	void GetName(char* pszName) const;
	bool Busy();
	unsigned long GetNumberOfPositions()const {return numPos_;}

	int getID(std::string* id);
	int setState(int state);
	int getState(int* state);

	// convenience functions
	bool isError(std::string);
	int getErrorCode(std::string message);
	std::string removeLineFeed(std::string answer);
	std::string removeCommandFlag(std::string message);

	// action interface
	// ----------------
	int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnChannel(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
	std::string port_;
	long numPos_;
	std::string channel_;
	bool initialized_;
	bool busy_;
};