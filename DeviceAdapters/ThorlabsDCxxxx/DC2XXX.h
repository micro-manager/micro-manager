#pragma once

#include "../../MMDevice/DeviceBase.h"
#include "DynError.h"

/****************************************************************************
 class: 			DC2xxx
 description:	The class DC2xxx is derived from a shutter base interface and
					can be used for DC2010 and for DC2100 devices.
****************************************************************************/
class DC2xxx: public CShutterBase<DC2xxx>
{
public:

	// constructor and destructor
	// ------------
	DC2xxx();									// constructs a DC2xxx device
	~DC2xxx();									// destroys a DC2xxx device

	// MMDevice API
	// ------------
	int Initialize();							// initialize a DC2xxx device and creates the actions
	int Shutdown();								// sets the LED output to off in case the DC2xxx was initialized

	// public functions
	// ------------
	void GetName(char* pszName) const;			// returns the device name -> DC2xxx
	bool Busy();								// returns true in case device is busy
	static const char* DeviceName();

	// Shutter API
	// ------------
	int SetOpen (bool open = true);
	int GetOpen(bool& open);
	int Fire(double deltaT);

	// action interface (work like callback/event functions)
	// -----------------------------------------------------
	int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnLimitCurrent(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnMaximumCurrent(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnOperationMode(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnConstantCurrent(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnStatus(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnPWMCurrent(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnPWMFrequency(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnPWMDutyCycle(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnPWMCounts(MM::PropertyBase* pProp, MM::ActionType eAct);
	//int OnDisplayBrightness(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
	int LEDOnOff(int);								// sets the LED output on(1) or off(0)
	int CreateStaticReadOnlyProperties(void);
	int ValidateDevice(void);

private:
	bool dynErrlist_free		(void);
	bool dynErrlist_lookup	(int err, std::string* descr);
	bool dynErrlist_add		(int err, std::string descr);
	bool getLastError			(int* err);

private:
	std::string 	m_name;
	std::string 	m_port;
	std::string 	m_LEDOn;
	std::string 	m_mode;
	std::string 	m_status;
	std::string 	m_serialNumber;
	std::string 	m_firmwareRev;
	std::string		m_wavelength;
	std::string		m_forwardBias;
	std::string		m_headSerialNo;
	long			m_limitCurrent;
	long			m_maximumCurrent;
	long 			m_constCurrent;
	long 			m_pwmCurrent;
	long 			m_pwmFrequency;
	long 			m_pwmDutyCycle;
	long 			m_pwmCounts;
	bool 			m_initialized;

	// dynamic error list
	std::vector<DynError*>	m_dynErrlist;
};

