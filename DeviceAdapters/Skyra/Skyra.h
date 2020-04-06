///////////////////////////////////////////////////////////////////////////////
// FILE:          Skyra.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Controls Cobolt and Skyra lasers through a serial port
// COPYRIGHT:     University of Massachusetts Medical School, 2019
// LICENSE:       LGPL
// LICENSE:       https://www.gnu.org/licenses/lgpl-3.0.txt
// AUTHOR:        Karl Bellve, Karl.Bellve@umassmed.edu, Karl.Bellve@gmail.com
//

#ifndef _SKYRA_H_
#define _SKYRA_H_
#endif

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/ModuleInterface.h"


#include <iostream>
#include <string>
#include <map>

#define MODULATION_STATUS 1
#define MODULATION_ANALOG 2
#define MODULATION_DIGITAL 3
#define MODULATION_INTERNAL 4

#define SERIAL_BUFFER 1024

//////////////////////////////////////////////////////////////////////////////
// Struct
//

//struct Lasers {
//	bool bAnalogModulation;
//	bool bDigitalModulation;
//	bool bInternalModulation;
//	long laserNumber; // int version of LaserID
//	long powerOn;
//	std::string powerStatus;
//	std::string currentOn;
//	std::string currentStatus;
//	std::string currentMaximum;
//	std::string currentMinimum;
//	std::string controlMode;
//	std::string active;
//	std::string laserType;
//	std::string laserID;  
//	std::string waveLength;
//	std::string photoDiode;
//	
//	// Reverse engineering the following commands #ra? and #rasp?
//	// Must use terminal software, and not the cobolt software since it swallows the answer.
//	std::string read_all_set_points; 
//	// 3 or 5 numbers with the string format: int float float int float
//	// "unknown mA mW" 
//	// DLP has 5 numbers
//	//  "unknown mA mW unknown mA(warm up current)" 
//	
//	std::string read_all; 
//	// 6 numbers with the string format: 
//	// "mA mW on(1 = on, 0 = off) mode(0 = ci, 1 = cp,2 = em) unknown Modulation"
//	//
//	// 7 numbers with the string format: 
//	// "Photodiode(V) Current(mA) Power(mW) on(1 = on, 0 = off) Mode(0 = ci, 1 = cp,2 = em) unknown Modulation"
//	//   
//	// Mode: is set to 0 if autostart is disabled, and if autostart is re-enabled, it will be set to 1 (cp)
//	// Modulation: Digital = 1, Analog = 2, both = 3
//	//
//	// Last unknown might be fault?
//};

//////////////////////////////////////////////////////////////////////////////
// Strings
//

const char * g_DeviceVendorName = "HÜBNER Photonics";
const char * g_DeviceSkyraName = "Skyra";
const char * g_DeviceSkyraDescription = "Skyra/Cobolt Controller by Karl Bellvé";


const char * g_SendTerm = "\r";
const char * g_RecvTerm = "\r\n";

const char * g_PropertySkyraAutostart = "Autostart";
const char * g_PropertySkyraAutostartStatus = "Autostart Status";
const char * g_PropertySkyraAutostartHelp = "Autostart needs to be disabled if a non-modulatable laser is used as a shutter";

const char * g_PropertySkyraAutostartHelp1 = "Off->On: If Autostart is enabled, the start-up sequence will Restart";
const char * g_PropertySkyraAutostartHelp2 = "Off->On: If Autostart is disabled, laser(s) will go directly to On";
const char * g_PropertySkyraAutostartHelp3 = "On->Off: If Autostart is enabled, the start-up sequence will Abort";
const char * g_PropertySkyraAutostartHelp4 = "On->Off: If Autostart is disabled, laser(s) will go directly to Off state";

const char * g_PropertySkyraCurrentModulationHelpMinimum = "Current is set to this when the laser is Off in either Modulation or Shutter Modes";
const char * g_PropertySkyraCurrentModulationHelpMaximum = "Current is set to this when the laser is On in either Modulation or Shutter Modes";
const char * g_PropertySkyraCurrentHelpOn = "Current is set to this when the laser is On, in Constant Current Mode";
const char * g_PropertySkyraCurrentHelp =  "mA - milliamps ";

const char * g_PropertySkyraPowerHelpOn = "Power is set to this when the laser is On, in Constant Power Mode";
const char * g_PropertySkyraPowerHelp =  "mW - milliwatts";

const char * gPropertySkyraControlMode = "Control Mode";
const char * gPropertySkyraControlModeConstant = "Constant Current";
const char * gPropertySkyraControlModePower = "Constant Power";
const char * gPropertySkyraControlModeModulation = "Modulation";

const char * g_PropertySkyraAnalogImpedance = "Analog Impedance";
const char * g_PropertySkyraAnalogImpedanceStatus = "Analog Impedance: Status";

const char * g_PropertySkyraCurrent = "Current";
const char * g_PropertySkyraCurrentOn = "Current On";
const char * g_PropertySkyraCurrentMaximum = "Current Maximum";
const char * g_PropertySkyraCurrentModulationMinimum = "Current Modulation Minimum";
const char * g_PropertySkyraCurrentModulationMaximum = "Current Modulation Maximum";
const char * g_PropertySkyraCurrentStatus = "Current Status";


const char * g_PropertySkyraPower =  "Power";
const char * g_PropertySkyraPowerStatus =  "Power Status";
const char * g_PropertySkyraPowerOn =  "Power On";
const char * g_PropertySkyraPowerMaximum = "Power Maximum";

const char * g_PropertySkyraActive = "Active";
const char * g_PropertySkyraActiveStatus = "Active Status";

const char * g_PropertySkyraModulationStatus = "Modulation";
const char * g_PropertySkyraAnalogModulation = "Modulation Analog";
const char * g_PropertySkyraDigitalModulation = "Modulation Digital ";
const char * g_PropertySkyraInternalModulation = "Modulation Internal";

const char * g_PropertySkyraInternalModulationPeriod = "Modulation Internal Period Time";
const char * g_PropertySkyraInternalModulationDelay = "Modulation Internal Delay Time";
const char * g_PropertySkyraInternalModulationOn = "Modulation Internal On Time";
const char * g_PropertySkyraInternalModulationHelp =  "Modulation Internal Units";
const char * g_PropertySkyraInternalModulationHelpUnits =  "ms - milliseconds";

const char * g_PropertySkyraWavelength = "Wavelength";
const char * g_PropertySkyraLaserType = "Laser Type";

const char * g_PropertySkyraAllLaser = "All Lasers";
const char * g_PropertySkyraLaser = "Laser";
const char * g_PropertySkyraLaserStatus = "Laser Status";

const char * g_PropertyActive = "Active";
const char * g_PropertyInactive = "Inactive";

const char * g_PropertyOn = "On";
const char * g_PropertyOff = "Off";

const char * g_PropertyEnabled = "Enabled";
const char * g_PropertyDisabled = "Disabled";

const char * g_Default_Empty = "";
const char * g_Default_String = "Unknown";
const char * g_Default_Integer = "0";
const char * g_Default_Float = "0.0";

const char * g_Default_ControlMode = gPropertySkyraControlModePower;

//////////////////////////////////////////////////////////////////////////////
// Error codes
//

#define ERR_PORT_CHANGE_FORBIDDEN	101 

#define ERR_DEVICE_NOT_FOUND		10000


class Skyra: public CShutterBase<Skyra>
{
public:
    Skyra();
    ~Skyra();

    // MMDevice API
    // ------------
    int Initialize();
    int Shutdown();

    void GetName(char* pszName) const;
    bool Busy();
    int AllLasersOn(int);

	// Automatic Serial Port Detection
	bool SupportsDeviceDetection(void);
	MM::DeviceDetectionStatus DetectDevice(void);
	int DetectInstalledDevices();
 
    // action interface
    // ----------------
    int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
   
	int OnControlMode(MM::PropertyBase* pProp, MM::ActionType eAct);

	int OnWaveLength(MM::PropertyBase* pProp, MM::ActionType eAct);

	int OnPower(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnPowerStatus(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnPowerSetPoint(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnPowerMaximum(MM::PropertyBase* pProp, MM::ActionType eAct);
	//int OnPowerSetMaximum(MM::PropertyBase* pProp, MM::ActionType  eAct);
		
	int OnCurrent(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnCurrentStatus(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnCurrentSetPoint(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnCurrentMaximum(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnCurrentModulationMinimum(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnCurrentModulationMaximum(MM::PropertyBase* pProp, MM::ActionType eAct);

	int OnAutoStart(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnAutoStartStatus(MM::PropertyBase* pProp, MM::ActionType eAct);

	// There is a global autostart commands. Despite the manual, I don't think there are laser specific autostart commands.
	int OnSkyraAutoStart(MM::PropertyBase* pProp, MM::ActionType eAct); 
	int OnSkyraAutoStartStatus(MM::PropertyBase* pProp, MM::ActionType eAct);
	
	int OnActive(MM::PropertyBase* pProp, MM::ActionType eAct);

	//int OnModulationStatus(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnDigitalModulation(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnAnalogModulation(MM::PropertyBase* pProp, MM::ActionType eAct);	
	
	int OnInternalModulation(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnInternalModulationOn(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnInternalModulationDelay(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnInternalModulationPeriod(MM::PropertyBase* pProp, MM::ActionType eAct);

	int OnAllLasers(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnLaserHelp1(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnLaserHelp2(MM::PropertyBase* pProp, MM::ActionType eAct);

	int OnLaser(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnLaserStatus(MM::PropertyBase* pProp, MM::ActionType eAct);

	int OnAnalogImpedance(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnAnalogImpedanceStatus(MM::PropertyBase* pProp, MM::ActionType eAct);

	int OnHours(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnKeyStatus(MM::PropertyBase* pProp, MM::ActionType eAct);
    
    int OnInterlock(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnFault(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnOperatingStatus(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnSerialNumber(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnVersion(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnModel(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnLaserType(MM::PropertyBase* pProp, MM::ActionType eAct);
    
    // base functions
	std::string AutostartStatus();

// Despite what the cobolt software shows, individual lasers in a Skyra doesn't have their own autostart setting
//	std::string AutostartStatusSkyra();

	// bool GetModulation(int modulation=0); This is now handled by GetReadAll() 
	std::string SetModulation(int modulation=0, bool value = false);
	std::string GetReadAll(std::string ID);
	std::string GetRASP(std::string ID);
	std::string AnalogImpedanceStatus();
	std::string UpdateWaveLength(std::string ID);
	
	std::string SetPower(std::string power, std::string laserid);
	//std::string GetPowerStatus(long &value, std::string laserid);
	//std::string GetPowerOn(long& value, std::string laserid);
   
	std::string SetCurrent(std::string);

	// Shutter API
    // ----------------
    int SetOpen(bool open = true);
    int GetOpen(bool& open);
    int Fire(double deltaT);

private:
    bool bInitialized_;
    bool bBusy_;
	bool bOn_;
	bool bImpedance_;
	bool bModulation_;
	//bool bModulationOn_;
	bool bAnalogModulation_;
	bool bDigitalModulation_;
	bool bInternalModulation_;
	long nInternalModulationOnTime_;
	long nInternalModulationPeriodTime_;
	long nInternalModulationPeriod_;
	long nSkyra_;


	std::string RA_;
	std::string RASP_;
	// string buffer for serial reads
	char tempstr_[SERIAL_BUFFER];

	std::string photoDiode_;
	std::string name_;
    std::string hours_;
	std::string keyStatus_;
    std::string laserStatus_;
    std::string interlock_;  
    std::string fault_;
	std::string operatingStatus_;
	std::string identity_;
    std::string serialNumber_;
    std::string version_;
	
	std::string model_;
	std::string autostartStatus_;
	std::string autostartStatusSKyra_;
	std::string impedanceStatus_;
	
	//global, but should be set to current laser if a Skyra
	std::string Power_;
	std::string powerSetPoint_;
	std::string powerMaximum_;
	std::string powerStatus_;
	std::string Current_;
	std::string currentStatus_;
	std::string currentMaximum_;
	std::string currentModulationMinimum_;
	std::string currentModulationMaximum_;
	std::string currentSetPoint_;
	std::string ID_;
	std::string Type_;
	std::string waveLength_;
	std::string controlMode_;

	// need this vector from micromanager property drowndown
	std::vector<std::string> waveLengths_;
	std::vector<std::string> IDs_;
	
	//struct Lasers *Laser_;
    
	int ConfirmIdentity();
    int GetState(int &value);

	// Serial Port
	std::string port_;
	int ClearPort(void);
	std::string GetPort();
	std::string SerialCommand (std::string serialCommand);

};
