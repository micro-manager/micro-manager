//-----------------------------------------------------------------------------
// FILE:          Thorlabs_ElliptecSlider.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Controls the Elliptec sliders ELL6, ELL9 and ELL17_20
// COPYRIGHT:     EMBL
// LICENSE:       LGPL
// AUTHOR:        Joran Deschamps and Anindita Dasgupta, EMBL
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


class ELL17_20 : public CStageBase<ELL17_20>
{
public:
    ELL17_20();
    ~ELL17_20();
	
	// convenience functions
	bool isError(std::string);
	int getErrorCode(std::string message);
	std::string removeLineFeed(std::string answer);
	std::string removeCommandFlag(std::string message);
	int getID(std::string* id, int* travelRange, double* pulsesPerMU);

	std::string positionFromValue(int pos);
	int positionFromHex(std::string pos);

    // Device API
    // ----------
    int Initialize();
    int Shutdown();

    void GetName(char* pszName) const;
    bool Busy();

    // Stage API
    // ---------
    int SetPositionUm(double posUm);
    int GetPositionUm(double& pos);

	// non available or implemented here
    int SetPositionUmContinuous(double posUm) {return SetPositionUm(posUm);};
	int GetLimits(double& min, double& max) {min = 0.; max = 63500.; return DEVICE_OK;};
    
	int SetPositionSteps(long steps) {return DEVICE_UNSUPPORTED_COMMAND;};
    int GetPositionSteps(long& steps) {return DEVICE_UNSUPPORTED_COMMAND;};
    
	int SetOrigin() {return DEVICE_UNSUPPORTED_COMMAND;};
	int SetLimits(double min, double max) {return DEVICE_UNSUPPORTED_COMMAND;};

    int IsStageSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}
    bool IsContinuousFocusDrive() const {return false;}

    // action interface
    // ----------------
	int OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnChannel(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
    //Private variables
	std::string port_;
	std::string channel_;
	bool initialized_;
	bool busy_;

	int travelRange_;
	double pulsesPerMU_;
};

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


class ELL6_shutter : public CShutterBase<ELL6_shutter>
{
public:
	ELL6_shutter();
	~ELL6_shutter();

	// MMDevice API
	// ------------
	int Initialize();
	int Shutdown();

	void GetName(char* pszName) const;
	bool Busy();
	unsigned long GetNumberOfPositions()const {return numPos_;}
	int getID(std::string* id);

	// Shutter API
    int SetOpen(bool open = true);
    int GetOpen(bool& open);
	int Fire(double d) { return DEVICE_UNSUPPORTED_COMMAND; };

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