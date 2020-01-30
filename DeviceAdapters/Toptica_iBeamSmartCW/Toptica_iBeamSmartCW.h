//-----------------------------------------------------------------------------
// FILE:          Toptica_iBeamSmartCW.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Controls iBeam smart laser series from Toptica through serial port
//				  using the hidden CW mode
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
#define LASER_WARNING    102
#define LASER_ERROR    103
#define LASER_FATAL_ERROR    104
#define ADAPTER_POWER_OUTSIDE_RANGE    105
#define ADAPTER_PERC_OUTSIDE_RANGE    106
#define ADAPTER_ERROR_DATA_NOT_FOUND    107
#define ADAPTER_CANNOT_CHANGE_CH2_EXT_ON    108
#define LASER_CLIP_FAIL   109
#define ADAPTER_UNEXPECTED_ANSWER   110
#define ADAPTER_ERROR_PASSWORD   111
#define ADAPTER_ERROR_SAVE_DATA   112

//-----------------------------------------------------------------------------

class iBeamSmartCW: public CGenericBase<iBeamSmartCW>
{
public:
    iBeamSmartCW();
    ~iBeamSmartCW();

    // MMDevice API
    int Initialize();
    int Shutdown();

    void GetName(char* pszName) const;
    bool Busy();

	// getters
	int getPower(double* power);
	int getFineStatus(bool* status);
	int getFinePercentage(char fine, double* value);
	int getExtStatus(bool* status);
	int getLaserStatus(bool* status);
	int getSerial(std::string* serial);
	int getFirmwareVersion(std::string* version);
	int getClipStatus(std::string* status);

	// setters
	int setLaserOnOff(bool b);
	int setPower(double pow);
	int setFineA(double perc);
	int setFineB(double perc);
	int enableExt(bool b);
	int enableFine(bool b);
	int setPrompt(bool b);
	int setTalkUsual();
	int setTalkGabby();

    // action properties
	int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnLaserOnOff(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnPower(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnEnableExt(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnEnableFine(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnFineA(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnFineB(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnClip(MM::PropertyBase* pProp, MM::ActionType eAct);
	
	// convenience function 
	int setCWmode(bool* isExtPossible, int* maxPower);
	int setNormalMode();
	bool isError(std::string answer);
	bool isOk(std::string answer);
	int getError(std::string error);
	int publishError(std::string error);
	std::string to_string(double x);

private:
	std::string port_;
	std::string serial_;
	std::string clip_;    
	bool initialized_;
	bool busy_;
	bool laserOn_;
	bool fineOn_;
	bool extOn_;
	int maxpower_;
	double power_;
	double finea_;
	double fineb_;
};

class iBeamSmartNormal: public CGenericBase<iBeamSmartNormal>
{
public:
    iBeamSmartNormal();
    ~iBeamSmartNormal();

    // MMDevice API
    int Initialize();
    int Shutdown();

    void GetName(char* pszName) const;
    bool Busy();

	// getters
	int getSerial(std::string* serial);

	// setters
	int setPrompt(bool prompt);
	int setTalkUsual();
	int setTalkGabby();

    // action properties
	int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
	
	// convenience function 
	int setNormalMode();
	bool isError(std::string answer);
	bool isOk(std::string answer);
	int getError(std::string error);
	int publishError(std::string error);
	std::string to_string(double x);

private:
	std::string port_;
	std::string serial_; 
	bool initialized_;
	bool busy_;
};