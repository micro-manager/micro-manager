//-----------------------------------------------------------------------------
// FILE:          Omicron.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Controls Omicron xX-laserseries modules through serial port
// COPYRIGHT:     Omicron Laserage Laserprodukte GmbH, 2012
// LICENSE:       LGPL
// AUTHOR:        Jan-Erik Herche, Ralf Schlotter
//-----------------------------------------------------------------------------

#ifndef _OMICRON_H_
#define _OMICRON_H_
#endif

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

class Omicron: public CGenericBase<Omicron>
{
public:
    Omicron();
    ~Omicron();

    // MMDevice API
    int Initialize();
    int Shutdown();

    void GetName(char* pszName) const;
    bool Busy();
    int LaserOnOff(int);

    // Properties
    int OnCWSubOperatingmode(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnDevice(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnFault(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnHours(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnLaserOnOff(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnOperatingmode(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnPower1(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnPower2(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnPowerSetpoint1(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnPowerSetpoint2(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnPowerStatus(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnReset(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSerialNumber(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSpecPower(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTemperatureDiode(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTemperatureBaseplate(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnWavelength(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
    std::string port_;    
	std::string hours_;
	std::string wavelength_;
	std::string specpower_;
    std::string laserOn_;
	std::string operatingmode_;
	std::string suboperatingmode_;
    std::string fault_;
    std::string serialNumber_;
    
	double power1_;
	double power2_;
    int specpower;
	int serial_;
	int device_;
    
	bool busy_;
    bool initialized_;
    double answerTimeoutMs_;


	bool PharseAnswerString(std::string &InputBuffer, const std::string &Kommando, std::vector<std::string> &ParameterVec);

};
