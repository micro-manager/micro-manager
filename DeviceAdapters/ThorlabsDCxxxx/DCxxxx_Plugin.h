///////////////////////////////////////////////////////////////////////////////
// FILE:          DCxxxx.h
//-----------------------------------------------------------------------------
// DESCRIPTION:   Controls the Thorlabs DCxxxx LED driver series through a
//						serial port. These devices are implemented as shutter devices,
//						although they are illumination devices. This makes the
//						synchronisation easier. So "Open" and "Close" means "On" or
//						"Off". "Fire" does nothing at all. All other commands are
//						realized as properties and differ from device to device.
//						Supported devices are:
//							+ DC2010 - universal LED driver	\
//							+ DC2100 - high power LED driver / both uses the DC2xxx class
//							+ DC3100 - FLIM LED driver
//							+ DC4100 - four channel LED driver
//
// COPYRIGHT:     Thorlabs GmbH
// LICENSE:       LGPL
// VERSION:			1.1.0
// DATE:				06-Oct-2009
// AUTHOR:        Olaf Wohlmann, owohlmann@thorlabs.com
//

#ifndef _DCxxxx_PLUGIN_H_
#define _DCxxxx_PLUGIN_H_


#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"

#include <string>
#include <map>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_PORT_CHANGE_FORBIDDEN	101
#define ERR_INVALID_DEVICE				102
#define ERR_DCxxxx_OFFSET				120


/****************************************************************************
 class: 			DynError
 description:	This class represents one dynamic error which can be read from
 					the device and can be stored in an error vector.
****************************************************************************/
class DynError
{
public:
	DynError() {err = 0;};					// nothing to construct
	~DynError(){};								// nothing to destroy
	DynError(const DynError& oDynError) // copy constructor
	{
		err = oDynError.err;
		descr = oDynError.descr;
   }

public:
	int 			err;				// error number
	std::string descr;			// error description
};


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
	int Shutdown();							// sets the LED output to off in case the DC2xxx was initialized

	// public functions
	// ------------
	void GetName(char* pszName) const;	// returns the device name -> DC2xxx
	bool Busy();								// returns true in case device is busy

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
	long				m_limitCurrent;
	long				m_maximumCurrent;
	long 				m_constCurrent;
	long 				m_pwmCurrent;
	long 				m_pwmFrequency;
	long 				m_pwmDutyCycle;
	long 				m_pwmCounts;
	//long				m_displayBrightness;
	bool 				m_busy;
	bool 				m_initialized;


	// dynamic error list
	std::vector<DynError*>	m_dynErrlist;
};


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
	bool 				m_busy;
	bool 				m_initialized;

	// dynamic error list
	std::vector<DynError*>	m_dynErrlist;
};


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
	bool 				m_busy;
	bool 				m_initialized;


	// dynamic error list
	std::vector<DynError*>	m_dynErrlist;
};


#endif	// _DCxxxx_PLUGIN_H_