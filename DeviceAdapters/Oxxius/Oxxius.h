///////////////////////////////////////////////////////////////////////////////
// FILE:          Oxxius.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Controls Oxxius lasers through a serial port
// COPYRIGHT:     Oxxius S.A., 2013-2019
// LICENSE:       LGPL
// AUTHOR:        Julien Beaurepaire, Tristan Martinez
//

#ifndef _OXXIUS_H_
#define _OXXIUS_H_


#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/ModuleInterface.h"

#include <string>
#include <map>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//

#define ERR_PORT_CHANGE_FORBIDDEN    101



class LaserBoxx: public CShutterBase<LaserBoxx>
{
public:
    LaserBoxx();
    ~LaserBoxx();

    // MMDevice API
    // ------------
    int Initialize();
    int Shutdown();

    void GetName(char* pszName) const;
    bool Busy();
    int EmissionOnOff(int);

	// Shutter API
    // ----------------
    int SetOpen(bool open = true);
    int GetOpen(bool& open);
    int Fire(double deltaT);

    // Action interface
    // ----------------
	// 1) Identification
    int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSerialNumber(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSWVersion(MM::PropertyBase* pProp, MM::ActionType eAct);  
	int OnModel(MM::PropertyBase* pProp, MM::ActionType eAct);
	
	// 2) Status
	int OnLaserStatus(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnEmissionOnOff(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnAlarm(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnInterlocked(MM::PropertyBase* pProp, MM::ActionType eAct);

	// 3) Power control and monitoring
	int OnControlMode(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnPower(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnPowerSP(MM::PropertyBase* pProp, MM::ActionType eAct, long powerSP);
	int OnCurrent(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnCurrentSP(MM::PropertyBase* pProp, MM::ActionType eAct, long currentSP);
	int OnSleep(MM::PropertyBase* pProp, MM::ActionType eAct);

	// 4) Modulation
	int OnAnalogMod(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnDigitalMod(MM::PropertyBase* pProp, MM::ActionType eAct);
	
	// 5) General monitoring
    int OnHours(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnBaseTemp(MM::PropertyBase* pProp, MM::ActionType eAct);
	

private:
	std::string name_;
    std::string port_;
    bool initialized_;
    bool busy_;

	double powerSP_, nominalPower_;
	double currentSP_;

	std::string model_;
	std::string emissionStatus_;
    std::string alarm_;
	std::string modeControl_;
    std::string serialNumber_;
    std::string softVersion_;

	int GetStatus(int& status);
	int SendAndReceive(std::string& command, std::string& answer);
};

#endif // _OXXIUS_H_