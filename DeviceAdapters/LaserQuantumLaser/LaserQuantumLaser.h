//-----------------------------------------------------------------------------
// FILE:          LaserQuantumLaser.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Controls gem/ventus/opus/axiom series from LaserQuantum 		  
// COPYRIGHT:     EMBL
// LICENSE:       LGPL
// AUTHOR:        Joran Deschamps, 2018
//-----------------------------------------------------------------------------


#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/ModuleInterface.h"

#include <string>
#include <map>
#include <iomanip>
#include <iostream>

//-----------------------------------------------------------------------------
// Error code
//-----------------------------------------------------------------------------

#define ERR_PORT_CHANGE_FORBIDDEN    101
#define ERR_CURRENT_CONTROL_UNSUPPORTED    4001
#define ERR_NOT_IN_CURRENT_CONTROL_MODE   4002
#define ERR_NOT_IN_CURRENT_POWER_MODE    4003
#define ERR_UNEXPECTED_ANSWER    4004
#define ERR_ERROR_ANSWER    4005
#define ERR_ERROR_67    4006
//-----------------------------------------------------------------------------

class LaserQuantumLaser: public CGenericBase<LaserQuantumLaser>
{
public:
    LaserQuantumLaser();
    ~LaserQuantumLaser();

    // MMDevice API
    int Initialize();
    int Shutdown();

    void GetName(char* pszName) const;
    bool Busy();
	int supportsCurrentControl(bool* supports);
	std::string to_string(double x);
	bool string_contains(std::string s1, std::string s2);

	// get
	int getVersion(std::string* version);
	int getStatus(bool* status);
	int getControlMode(bool* mode);
	int getCurrent(double* current);
	int getPower(double* power);
	int getLaserTemperature(double* temp);
	int getPSUTemperature(double* temp);
	int getTimers(double* psutimer, double* lasertimer, double* operationtimer);

	// set
	int setLaserOnOff(bool status);
	int setCurrent(double current);
	int setControlMode(bool mode);
	int setPower(double power);

    // action properties
	// read only
	int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnEnableCurrentControl(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnLaserTemperature(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnPSUTemperature(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTimers(MM::PropertyBase* pProp, MM::ActionType eAct, long timer);

	int OnLaserOnOff(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnControlMode(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnCurrent(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnPower(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
	std::string port_;
	std::string version_;    
	bool initialized_;
	bool busy_;
	bool controlmode_;
	bool enabledCurrentControl_;
	double power_;
	double maxpower_;
	double current_;
	bool startupstatus_;
	double apccalibpower_;
	double lasertemperature_;
	double psutemperature_;
	bool status_;
	double psutime_;
	double laserenabledtime_;
	double laseroperationtime_;
};
