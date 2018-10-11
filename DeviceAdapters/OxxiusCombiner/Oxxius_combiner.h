///////////////////////////////////////////////////////////////////////////////
// FILE:			OxxiusCombiner.h
// PROJECT:			Micro-Manager
// SUBSYSTEM:		DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:		Controls Oxxius combiners through a serial interface
// COPYRIGHT:		Oxxius SA, 2013-2018
// LICENSE:			LGPL
// AUTHOR:			Tristan Martinez
//

#ifndef _OXXIUS_COMBINER_H_
#define _OXXIUS_COMBINER_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/ModuleInterface.h"
#include <cstdlib>
#include <string>
#include <map>


//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_PORT_CHANGE_FORBIDDEN	101
#define ERR_NO_PORT_SET				102
#define ERR_COMBINER_NOT_FOUND	    201
#define ERR_UNSUPPORTED_VERSION	    202

//////////////////////////////////////////////////////////////////////////////
// Miscellaneous definitions
//
// Use the name 'return_value' that is unlikely to appear within 'result'.
#define RETURN_ON_MM_ERROR( result ) do { \
   int return_value = (result); \
   if (return_value != DEVICE_OK) { \
      return return_value; \
   } \
} while (0)


//////////////////////////////////////////////////////////////////////////////
// Defining device adaptaters
//

class OxxiusCombinerHub: public HubBase<OxxiusCombinerHub>
{
public:
	OxxiusCombinerHub();
	~OxxiusCombinerHub();

   // MMDevice API
   // ------------
	int Initialize();
	int Shutdown();

	void GetName(char* pszName) const;
	bool Busy();
	int DetectInstalledDevices();
	unsigned int GetNumberOfInstalledDevices() {return installedDevices_;};
//	MM::DeviceDetectionStatus DetectDevice(void);
	bool SupportsDeviceDetection(void) {return true;};


	// Property handlers
	int OnPort(MM::PropertyBase* pPropt, MM::ActionType eAct);
	int OnSerialNumber(MM::PropertyBase* pPropt, MM::ActionType eAct);
	int OnInterlock(MM::PropertyBase* pPropt, MM::ActionType eAct);
	int OnEmissionKey(MM::PropertyBase* pPropt, MM::ActionType eAct);

	// Custom interface for child devices
//	bool IsPortAvailable() {return portAvailable_;}
	int QueryCommand(MM::Device* device, MM::Core* core, const unsigned int destinationSlot, const char* command);
	int ParseforBoolean(bool &destBoolean);
	int ParseforDouble(double &destFloat);
	int ParseforInteger(unsigned int &destInteger);
	int ParseforString(std::string &destString);


private:
//	void ClearRcvBuf();
	void LogError(int id, MM::Device* device, MM::Core* core, const char* functionName);

	std::string port_;
	bool initialized_;
	unsigned int installedDevices_;

	std::string serialAnswer_;
	
	std::string serialNumber_;
	bool interlockClosed_;
	bool keyActivated_;
};

//////////////////////////////////////////////////////////////////////////////
//
// Device adaptaters for "LaserBoxx" source
//
//////////////////////////////////////////////////////////////////////////////

class OxxiusLaserBoxx: public CGenericBase<OxxiusLaserBoxx>
{
public:
    OxxiusLaserBoxx(const char* nameAndSlot);
    ~OxxiusLaserBoxx();

    // MMDevice API
    // ------------
    int Initialize();
    int Shutdown();

    void GetName(char* pszName) const;
	bool Busy();
//	int LaserOnOff(int);

    // Action interface
    // ----------------
//	int OnPower(MM::PropertyBase* pProp, MM::ActionType eAct);
//	int OnCurrent(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnPowerSetPoint(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnCurrentSetPoint(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnEmissionOnOff(MM::PropertyBase* pProp, MM::ActionType eAct);

	int OnControlMode(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnAnalogMod(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnDigitalMod(MM::PropertyBase* pProp, MM::ActionType eAct);

	int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnAlarm(MM::PropertyBase* pProp, MM::ActionType eAct);
//	int OnHours(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
	std::string name_;
	unsigned int slot_;
	unsigned int model_[2];
	OxxiusCombinerHub* parentHub_;
    bool initialized_;
	bool busy_;

	double powerSetPoint_;
	double currentSetPoint_;
	unsigned int maxPower_;
//	double maxCurrent_;


	bool laserOn_;
	std::string state_;
	std::string alarm_;
//	std::string serialNumber_;
//	std::string softVersion_;
	std::string controlMode_;
	std::string analogMod_;
	std::string digitalMod_;
};



class OxxiusShutter: public CShutterBase<OxxiusShutter>
{
public:
	OxxiusShutter(const char* nameAndChannel);
	~OxxiusShutter();

	// MMDevice API
	// ------------
	int Initialize();
	int Shutdown();

	void GetName(char* pszName) const;
	bool Busy();

	// Shutter API
	int SetOpen(bool openCommand = true);
	int GetOpen(bool& isOpen);
	int Fire(double /*deltaT*/) { return DEVICE_UNSUPPORTED_COMMAND;  }

	// Action Interface
	// ----------------
	int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
	bool initialized_;
	std::string name_;

	OxxiusCombinerHub* parentHub_;
	bool isOpen_;
	unsigned int channel_;
	
	MM::MMTime changedTime_;
};


class OxxiusMDual: public CStateDeviceBase<OxxiusMDual>
{
public:
	OxxiusMDual(const char* name);
	~OxxiusMDual();

	// MMDevice API
	// ------------
	int Initialize();
	int Shutdown();

	void GetName(char* pszName) const;
	bool Busy();
	unsigned long GetNumberOfPositions()const {return numPos_;}

	// Action Interface
	// ----------------
	int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
	bool initialized_;
	std::string name_;
	MM::Core* core_;

	OxxiusCombinerHub* parentHub_;

	unsigned long numPos_;
};
#endif // _OXXIUS_COMBINER_H_