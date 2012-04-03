///////////////////////////////////////////////////////////////////////////////
// FILE:          Mightex.h
//-----------------------------------------------------------------------------
// DESCRIPTION:   Controls the Mightex Sirius LED Driver series through a
//						serial or USB port. These devices are implemented as shutter devices,
//						although they are illumination devices. This makes the
//						synchronisation easier. So "Open" and "Close" means "On" or
//						"Off". "Fire" does nothing at all. All other commands are
//						realized as properties and differ from device to device.
//						Supported devices are:
//							+ Mightex Sirius SLC LED Driver(USB)
//							+ Mightex Sirius SLC LED Driver(RS-232) (currently not supported)
//							+ Mightex Sirius BLS Control Module(USB)
//							+ Mightex Sirius BLS Control Module(RS-232) (currently not supported)
//
// COPYRIGHT:     Mightex
// LICENSE:       LGPL
// VERSION:			1.0.1
// DATE:		2012-03-30
// AUTHOR:        Yihui wu
//

#ifndef _Mightex_Sirius_LED_PLUGIN_H_
#define _Mightex_Sirius_LED_PLUGIN_H_


#include "../../MMDevice/DeviceBase.h"
#include "Mightex_LEDDriver_SDK.h"
#include "Mightex_BLSDriver_SDK.h"

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_PORT_CHANGE_FORBIDDEN	101
#define ERR_INVALID_DEVICE				102
#define ERR_Mightex_Sirius_LED_OFFSET				120

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
 class: 			Mightex_Sirius_SLC_USB
 description:	The class Mightex_Sirius_SLC_USB is derived from a shutter base interface and
					can be used for Mightex Sirius SLC USB devices.
****************************************************************************/
class Mightex_Sirius_SLC_USB: public CShutterBase<Mightex_Sirius_SLC_USB>
{
public:

	// constructor and destructor
	// ------------
	Mightex_Sirius_SLC_USB();									// constructs a Mightex_Sirius_SLC_USB device
	~Mightex_Sirius_SLC_USB();									// destroys a Mightex_Sirius_SLC_USB device

	// MMDevice API
	// ------------
	int Initialize();							// initialize a Mightex_Sirius_SLC_USB device and creates the actions
	int Shutdown();							// sets the LED output to off in case the Mightex_Sirius_SLC_USB was initialized

	// public functions
	// ------------
	void GetName(char* pszName) const;	// returns the device name
	bool Busy();								// returns true in case device is busy

	// Shutter API
	// ------------
	int SetOpen (bool open = true);
	int GetOpen(bool& open);
	int Fire(double deltaT);


	// action interface (work like callback/event functions)
	// -----------------------------------------------------
	int OnDevices(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnMode(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnChannel(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSetImax(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSetNormalCurrent(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSetRepeatCnt(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSetRatio(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSetPeriod(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSetI1(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSetI2(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnStatus(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
	int LEDOnOff(int);								// sets the LED output on(1) or off(0)
	int CreateStaticReadOnlyProperties(void);

private:
	bool dynErrlist_free		(void);
	bool dynErrlist_lookup	(int err, std::string* descr);
	bool dynErrlist_add		(int err, std::string descr);
	bool getLastError			(int* err);


private:
	std::string 	m_name;
	std::string 	m_LEDOn;
	std::string 	m_mode;
	std::string 	m_status;
	std::string 	m_serialNumber;
	bool 				m_busy;
	bool 				m_initialized;

	long m_channel;
	long m_ratio;
	long m_period;

	int dev_num;
	int cur_dev;
	int devHandle;
	int devModuleType;
	long channels;
	long mode;

	TLedChannelData ledChannelData;
	std::vector<std::string> devNameList;

	// dynamic error list
	std::vector<DynError*>	m_dynErrlist;
};

/****************************************************************************
 class: 			Mightex_Sirius_SLC_RS232
 description:	The class Mightex_Sirius_SLC_RS232 is derived from a shutter base interface and
					can be used for Mightex Sirius SLC RS232 devices.
****************************************************************************/
//class Mightex_Sirius_SLC_RS232;


/****************************************************************************
 class: 			Mightex_Sirius_BLS_USB
 description:	The class Mightex_Sirius_BLS_USB is derived from a shutter base interface and
					can be used for Mightex Sirius BLS USB devices.
****************************************************************************/
class Mightex_Sirius_BLS_USB: public CShutterBase<Mightex_Sirius_BLS_USB>
{
public:

	// constructor and destructor
	// ------------
	Mightex_Sirius_BLS_USB();									// constructs a Mightex_Sirius_BLS_USB device
	~Mightex_Sirius_BLS_USB();									// destroys a Mightex_Sirius_BLS_USB device

	// MMDevice API
	// ------------
	int Initialize();							// initialize a Mightex_Sirius_BLS_USB device and creates the actions
	int Shutdown();							// sets the LED output to off in case the Mightex_Sirius_BLS_USB was initialized

	// public functions
	// ------------
	void GetName(char* pszName) const;	// returns the device name
	bool Busy();								// returns true in case device is busy

	// Shutter API
	// ------------
	int SetOpen (bool open = true);
	int GetOpen(bool& open);
	int Fire(double deltaT);


	// action interface (work like callback/event functions)
	// -----------------------------------------------------
	int OnDevices(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnMode(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnChannel(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSetNormalCurrent(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSetRepeatCnt(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSetI1(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSetI2(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSetI3(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSetT1(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSetT2(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSetT3(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSetPulse_Follow_Mode(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSetION(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSetIOFF(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSetSoftStart(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnStatus(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
	int LEDOnOff(int);								// sets the LED output on(1) or off(0)
	int CreateStaticReadOnlyProperties(void);

private:
	bool dynErrlist_free		(void);
	bool dynErrlist_lookup	(int err, std::string* descr);
	bool dynErrlist_add		(int err, std::string descr);
	bool getLastError			(int* err);


private:
	std::string 	m_name;
	std::string 	m_LEDOn;
	std::string 	m_mode;
	std::string 	m_status;
	std::string 	m_serialNumber;
	bool 				m_busy;
	bool 				m_initialized;

	long m_channel;
	long m_repeatCnt;
	long m_normal_CurrentSet;
	long m_pulse_follow_mode;
	long m_iON;
	long m_iOFF;
	long m_isSoftStart;
	struct pulse m_pulse;

	int dev_num;
	int cur_dev;
	int devHandle;
	int devModuleType;
	long channels;
	long mode;

	std::vector<std::string> devNameList;

	// dynamic error list
	std::vector<DynError*>	m_dynErrlist;
};

/****************************************************************************
 class: 			Mightex_Sirius_BLS_RS232
 description:	The class Mightex_Sirius_BLS_RS232 is derived from a shutter base interface and
					can be used for Mightex Sirius BLS RS232 devices.
****************************************************************************/
//class Mightex_Sirius_BLS_RS232;

#endif	// _Mightex_Sirius_LED_PLUGIN_H_