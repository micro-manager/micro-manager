#pragma once

#include "../../MMDevice/DeviceBase.h"
#include "DynError.h"

/****************************************************************************
 class: 			DC3100
 description:	The class DC3100 is derived from a shutter base interface and
					can be used for DC3100 devices.
****************************************************************************/
class DC3100: public CShutterBase<DC3100>
{
public:

	// constructor and destructor
	// ------------
	DC3100();									// constructs a DC3100 device
	~DC3100();									// destroys a DC3100 device

	// MMDevice API
	// ------------
	int Initialize();							// initialize a DC3100 device and creates the actions
	int Shutdown();							// sets the LED output to off in case the DC3100 was initialized

	// public functions
	// ------------
	void GetName(char* pszName) const;	// returns the device name -> DC3100
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
	int OnMaximumFrequency(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnOperationMode(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnConstantCurrent(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnStatus(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnModulationCurrent(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnModulationFrequency(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnModulationDepth(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
	int LEDOnOff(int);
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
	std::string		m_headSerialNo;
	long				m_limitCurrent;
	long				m_maximumCurrent;
	float				m_maximumFrequency;
	long 				m_constCurrent;
	long 				m_moduCurrent;
	float 			m_moduFrequency;
	long 				m_moduDepth;
	bool 				m_initialized;

	// dynamic error list
	std::vector<DynError*>	m_dynErrlist;
};
