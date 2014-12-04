/*&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&
// FILE:          VersaLase.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Controls Vortran VersaLase Diode Laser Modules
// COPYRIGHT:     Vortran Laser Technology, 2013, All rights reserved.
//                http://www.vortranlaser.com
// AUTHOR:        David Sweeney
// LICENSE:       This file is distributed under the LGPL license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&
*/

#ifndef _VERSALASE_H_
#define _VERSALASE_H_
#endif

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/ModuleInterface.h"

#include <string>
#include <map>
#include <vector>

#define ERR_PORT_CHANGE    102

#define MAX_LASERS 4

typedef enum {
	LASER_A,
	LASER_B,
	LASER_C,
	LASER_D} LASER_SLOT;

class VersaLase: public CShutterBase<VersaLase>
{
public:
    VersaLase();
    ~VersaLase();

    // MMDevice API
    // ------------
    int Initialize();
    int Shutdown();

    void GetName(char* pszName) const;
    bool Busy();
    int LaserOnOffA(int);
    int LaserOnOffB(int);
    int LaserOnOffC(int);
    int LaserOnOffD(int);
    int epcOnOffA(int);
    int epcOnOffB(int);
    int epcOnOffC(int);
    int epcOnOffD(int);
    int digModOnOffA(int);
    int digModOnOffB(int);
    int digModOnOffC(int);
    int digModOnOffD(int);

	// Shutter API
    int SetOpen(bool open = true);
    int GetOpen(bool& open);
    int Fire(double deltaT);

    // action interface
    // ----------------
    int OnBaseT(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnShutterA(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnShutterB(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnShutterC(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnShutterD(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnPowerA(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnPowerB(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnPowerC(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnPowerD(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnPowerStatusA(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnPowerStatusB(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnPowerStatusC(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnPowerStatusD(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnPulPwrA(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnPulPwrB(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnPulPwrC(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnPulPwrD(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnPulPwrStatusA(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnPulPwrStatusB(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnPulPwrStatusC(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnPulPwrStatusD(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnHoursA(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnHoursB(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnHoursC(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnHoursD(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnLaserOnOffA(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnLaserOnOffB(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnLaserOnOffC(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnLaserOnOffD(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnDigModA(MM::PropertyBase* pProp,MM::ActionType eAct);
    int OnDigModB(MM::PropertyBase* pProp,MM::ActionType eAct);
    int OnDigModC(MM::PropertyBase* pProp,MM::ActionType eAct);
    int OnDigModD(MM::PropertyBase* pProp,MM::ActionType eAct);
    int OnEPCA(MM::PropertyBase* pProp,MM::ActionType eAct);
    int OnEPCB(MM::PropertyBase* pProp,MM::ActionType eAct);
    int OnEPCC(MM::PropertyBase* pProp,MM::ActionType eAct);
    int OnEPCD(MM::PropertyBase* pProp,MM::ActionType eAct);
    int OnCurrentA(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnCurrentB(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnCurrentC(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnCurrentD(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnInterlock(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnFaultA(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnFaultB(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnFaultC(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnFaultD(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnSerialNumberA(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnSerialNumberB(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnSerialNumberC(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnSerialNumberD(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnVersion(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnFaultCodeA(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnFaultCodeB(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnFaultCodeC(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnFaultCodeD(MM::PropertyBase* pProp, MM::ActionType eAct);
    void Tokenize(const std::string& str, std::vector<std::string>& tokens, const std::string& delimiters);
	int SendVersalaseCommand(std::ostringstream cmd);
	int GetVersalaseAnswer(void);
	void SetLaserAsInvalid(LASER_SLOT myLsr);


private:
    std::string port_;
    bool initialized_;
    bool busy_;
    std::string  shutterA_;
    std::string  shutterB_;
    std::string  shutterC_;
    std::string  shutterD_;
    double powerA_;
    double powerB_;
    double powerC_;
    double powerD_;
    int pulPwrA_;
    int pulPwrB_;
    int pulPwrC_;
    int pulPwrD_;
    std::string laserOnA_;
    std::string laserOnB_;
    std::string laserOnC_;
    std::string laserOnD_;
    std::string epcA_;
    std::string epcB_;
    std::string epcC_;
    std::string epcD_;
    std::string digModA_;
    std::string digModB_;
    std::string digModC_;
    std::string digModD_;
    std::string emissionStatusA_;
    std::string emissionStatusB_;
    std::string emissionStatusC_;
    std::string emissionStatusD_;
    std::string interlock_;
    std::string faultA_;
    std::string faultB_;
    std::string faultC_;
    std::string faultD_;
    std::string faultCodeA_;
    std::string faultCodeB_;
    std::string faultCodeC_;
    std::string faultCodeD_;
    std::string serialNumberA_;
    std::string serialNumberB_;
    std::string serialNumberC_;
    std::string serialNumberD_;
    std::string version_;
    std::string name_;
    std::string baseT_;
    std::string hoursA_;
    std::string hoursB_;
    std::string hoursC_;
    std::string hoursD_;
    std::string currentA_;
    std::string currentB_;
    std::string currentC_;
    std::string currentD_;
};
