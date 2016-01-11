#pragma once

#include "../../MMDevice/DeviceBase.h"
#include "DynError.h"

/****************************************************************************
 class: 			DC4100
 description:	The class DC4100 is derived from a shutter base interface and
					can be used for DC4100 devices.
****************************************************************************/
class DC4100: public CShutterBase<DC4100>
{
public:

	// constructor and destructor
	// ------------
	DC4100();									// constructs a DC4100 device
	~DC4100();									// destroys a DC4100 device

	// MMDevice API
	// ------------
	int Initialize();							// initialize a DC4100 device and creates the actions
	int Shutdown();							// sets the LED output to off in case the DC4100 was initialized

	// public functions
	// ------------
	void GetName(char* pszName) const;	// returns the device name -> DC4100
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
	int OnOperationMode(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnStatus(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnConstantCurrent(MM::PropertyBase* pProp, MM::ActionType eAct, long index);
	int OnPercentalBrightness(MM::PropertyBase* pProp, MM::ActionType eAct, long index);
	int OnLimitCurrent(MM::PropertyBase* pProp, MM::ActionType eAct, long index);
	int OnMaximumCurrent(MM::PropertyBase* pProp, MM::ActionType eAct, long index);

private:
	int LEDOnOff(int);
	int SwitchToMultiSelection(void);
	int CreateStaticReadOnlyProperties(void);
	int ValidateDevice(void);
	int GetStatus(int* status);

private:
	bool dynErrlist_free		(void);
	bool dynErrlist_lookup	(int err, std::string* descr);
	bool dynErrlist_add		(int err, std::string descr);
	bool getLastError			(int* err);


private:
	static int const NUM_LEDS = 4;
	std::string 	m_name;
	std::string 	m_port;
	std::string 	m_LEDOn;
	std::string 	m_mode;
	std::string 	m_status;
	std::string 	m_serialNumber;
	std::string 	m_firmwareRev;
	std::string		m_wavelength[NUM_LEDS];
	std::string		m_forwardBias[NUM_LEDS];
	std::string		m_headSerialNo[NUM_LEDS];
	long				m_limitCurrent[NUM_LEDS];
	long				m_maximumCurrent[NUM_LEDS];
	long 				m_constCurrent[NUM_LEDS];
	long 		      m_percBrightness[NUM_LEDS];
	long				m_channelAvailable[NUM_LEDS];
	bool 				m_initialized;


	// dynamic error list
	std::vector<DynError*>	m_dynErrlist;
};

