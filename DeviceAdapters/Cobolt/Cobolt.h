///////////////////////////////////////////////////////////////////////////////
// FILE:          Cobolt.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Controls power levels of Cobolt lasers through a serial port
// COPYRIGHT:     University of Massachusetts Medical School, 2009
// LICENSE:       LGPL
// AUTHOR:        Karl Bellve, Karl.Bellve@umassmed.edu
//

#ifndef _COBOLT_H_
#define _COBOLT_H_
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


class Cobolt: public CGenericBase<Cobolt>
{
public:
    Cobolt();
    ~Cobolt();

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
    int OnPower(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnPowerStatus(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnHours(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnLaserOnOff(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnLaserStatus(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnCurrent(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnInterlock(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnFault(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnSerialNumber(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnVersion(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
    std::string name_;
    std::string port_;
    std::string hours_;
    std::string laserOn_;
    std::string laserStatus_;
    std::string current_;
    std::string interlock_;  
    std::string fault_;
    std::string serialNumber_;
    std::string version_;
    double power_;
    int serial_;
    bool busy_;
    bool initialized_;
    double answerTimeoutMs_;
};