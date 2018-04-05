//-----------------------------------------------------------------------------
// FILE:          LaserQuantumGem.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Controls gem series from LaserQuantum through serial port
// COPYRIGHT:     EMBL
// LICENSE:       LGPL
// AUTHOR:        Joran Deschamps
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

//-----------------------------------------------------------------------------

class LaserQuantumGem: public CGenericBase<LaserQuantumGem>
{
public:
    LaserQuantumGem();
    ~LaserQuantumGem();

    // MMDevice API
    int Initialize();
    int Shutdown();

    void GetName(char* pszName) const;
    bool Busy();

	int write();
	std::string getVersion();
	
	int getPower(double*);
	int getCurrent(double*);
	int getControlMode(bool*);
	int getLaserTemperature(double*);
	int getPSUTemperature(double*);
	int getStatus(bool*);
	int getTimers(double* psutime, double* laserenabletime, double* laseroperationtime);

	int setLaserOnOff(bool b);
	int setPower(double pow);
	int setCurrent(double current);
	int setControlMode(bool mode);
	int setStartupPower(double pow);
	int setStartupStatus(bool b);
	int setAPCCalibration(double pow);

	std::string to_string(double x){
		std::ostringstream x_convert;
		x_convert << x;
		return x_convert.str();
	}
	double getMaxPower(){return maxpower_;};

    // action properties
	int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnVersion(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnLaserOnOFF(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnControlMode(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnPower(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnMaximumPower(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnCurrent(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnStartUpPower(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnStartUpStatus(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnAPCCalibration(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnLaserTemperature(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnPSUTemperature(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTimers(MM::PropertyBase* pProp, MM::ActionType eAct, long tempnumber);

private:
	std::string port_;
	std::string version_;    
	bool initialized_;
	bool busy_;
	bool controlmode_;
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
