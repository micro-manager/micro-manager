///////////////////////////////////////////////////////////////////////////////
// FILE:          Oxxius.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Controls Oxxius lasers through a serial port
// COPYRIGHT:     Oxxius SA, 2013
// LICENSE:       LGPL
// AUTHOR:        Julien Beaurepaire
//

#ifndef _OXXIUS_H_
#define _OXXIUS_H_
#endif

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/ModuleInterface.h"

#include <string>
#include <map>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//

#define ERR_PORT_CHANGE_FORBIDDEN    101


class OxxLBXDPSS: public CGenericBase<OxxLBXDPSS>
{
public:
    OxxLBXDPSS();
    ~OxxLBXDPSS();

    // MMDevice API
    // ------------
    int Initialize();
    int Shutdown();

    void GetName(char* pszName) const;
    bool Busy();
    int LaserOnOff(int);

    // action interface
    // ----------------
    int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSerialNumber(MM::PropertyBase* pProp, MM::ActionType eAct);
	
	int OnVersion(MM::PropertyBase* pProp, MM::ActionType eAct);  
	int OnPower(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnPowerSP(MM::PropertyBase* pProp, MM::ActionType eAct, long index);
	int OnLaserOnOff(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnHours(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnFault(MM::PropertyBase* pProp, MM::ActionType eAct);
    
	int OnWaveLength(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnBaseTemp(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnControllerTemp(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnLaserStatus(MM::PropertyBase* pProp, MM::ActionType eAct);
	

private:
	std::string name_;
    std::string port_;
    bool initialized_;
    bool busy_;
	double powerSP_;

    std::string laserOn_;
    std::string laserStatus_;
    std::string fault_;
    std::string serialNumber_;
    std::string softVersion_;
	std::string waveLength_;
};
class OxxLBXLD: public CGenericBase<OxxLBXLD>
{
public:
    OxxLBXLD();
    ~OxxLBXLD();

    // MMDevice API
    // ------------
    int Initialize();
    int Shutdown();

    void GetName(char* pszName) const;
    bool Busy();
    int LaserOnOff(int);

    // action interface
    // ----------------
    int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnVersion(MM::PropertyBase* pProp, MM::ActionType eAct);  
	int OnPower(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnPowerSP(MM::PropertyBase* pProp, MM::ActionType eAct, long index);
	int OnCurrent(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnCurrentSP(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnLaserOnOff(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnHours(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnInterlock(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnFault(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnSerialNumber(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnWaveLength(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnMaxPower(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnMaxCurrent(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnBaseTemp(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnControlMode(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnAnalogMod(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnDigitalMod(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnLaserStatus(MM::PropertyBase* pProp, MM::ActionType eAct);
   

private:
    std::string name_;
    std::string port_;
    bool initialized_;
    bool busy_;
	double powerSP_;
	double currentSP_;
	double maxCurrent_;
	double maxPower_;

    std::string laserOn_;
    std::string laserStatus_;
    std::string interlock_;  
    std::string fault_;
    std::string serialNumber_;
    std::string softVersion_;
	std::string controlMode_;
	std::string waveLength_;
	std::string analogMod_;
	std::string digitalMod_;
    
};
