///////////////////////////////////////////////////////////////////////////////
// FILE:          Mightex.cpp
//-----------------------------------------------------------------------------
// DESCRIPTION:   Controls the Mightex Sirius SLC LED Driver series through a
//						serial or USB port. These devices are implemented as shutter devices,
//						although they are illumination devices. This makes the
//						synchronisation easier. So "Open" and "Close" means "On" or
//						"Off". "Fire" does nothing at all. All other commands are
//						realized as properties and differ from device to device.
//						Supported devices are:
//							+ Mightex Sirius SLC LED Driver(USB)
//							+ Mightex Sirius SLC LED Driver(RS-232) (currently not supported)
//
// COPYRIGHT:     Mightex
// LICENSE:       LGPL
// VERSION:			1.0.0
// DATE:		2012-02-17
// AUTHOR:        Yihui wu
//


#include <windows.h>
#include "Mightex.h"
#include "HIDDLL.h"

///////////////////////////////////////////////////////////////////////////////
//
// GLOBALS
///////////////////////////////////////////////////////////////////////////////
//
const char* g_DeviceSiriusSLCUSBName = "Mightex_SLC(USB)";
//const char* g_DeviceSiriusSLCRS232Name = "Mightex Sirius SLC(RS-232)";

///////////////////////////////////////////////////////////////////////////////
//
// Exported MMDevice API
//
///////////////////////////////////////////////////////////////////////////////
/*---------------------------------------------------------------------------
 Initialize module data. It publishes the Mightex Sirius SLC series names to Manager.
---------------------------------------------------------------------------*/
MODULE_API void InitializeModuleData()
{
	AddAvailableDeviceName(g_DeviceSiriusSLCUSBName, "Mightex Sirius SLC LED Driver(USB)");
//	AddAvailableDeviceName(g_DeviceSiriusSLCRS232Name, "Mightex Sirius SLC LED Driver(RS-232)");
}


/*---------------------------------------------------------------------------
 Creates and returns a device specified by the device name.
---------------------------------------------------------------------------*/
MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
	// no name, no device
	if(deviceName == 0)	return 0;

	if(strcmp(deviceName, g_DeviceSiriusSLCUSBName) == 0)
	{
		return new Mightex_Sirius_SLC_USB;
	}
//	else if(strcmp(deviceName, g_DeviceSiriusSLCRS232Name) == 0)
//	{
//		return new Mightex_Sirius_SLC_RS232;
//	}

	return 0;
}


/*---------------------------------------------------------------------------
 Deletes a device pointed by pDevice.
---------------------------------------------------------------------------*/
MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}



/****************************************************************************

 class: 			Mightex_Sirius_SLC_USB
 description:	The class Mightex_Sirius_SLC_USB is derived from a shutter base interface and
					can be used for Mightex Sirius SLC USB devices.

****************************************************************************/
/*---------------------------------------------------------------------------
 Default constructor.
---------------------------------------------------------------------------*/
Mightex_Sirius_SLC_USB::Mightex_Sirius_SLC_USB() :
	dev_num(0),
	cur_dev(0),
	devHandle(-1),
	channels(-1),
	devModuleType(MODULE_AA),
	mode(DISABLE_MODE),
	m_ratio(50),
	m_period(1),
	m_channel(1),
	m_name(""),
    m_LEDOn("Off"),
	m_mode("DISABLE"),
   m_status("No Fault"),
	m_serialNumber("n/a"),
   m_busy(false),
   m_initialized(false)
{
	InitializeDefaultErrorMessages();
	SetErrorText(ERR_PORT_CHANGE_FORBIDDEN, "You can't change the port after device has been initialized.");
	SetErrorText(ERR_INVALID_DEVICE, "The selected plugin does not fit for the device.");


	// Name
	CreateProperty(MM::g_Keyword_Name, g_DeviceSiriusSLCUSBName, MM::String, true);

	// Description
	CreateProperty(MM::g_Keyword_Description, "Mightex Sirius SLC LED Driver(USB)", MM::String, true);

	CPropertyAction* pAct = new CPropertyAction (this, &Mightex_Sirius_SLC_USB::OnDevices);
	CreateProperty("Devices", "", MM::String, false, pAct, true);

	AddAllowedValue( "Devices", ""); // no device yet

	HidInit();

	char ledName[64];
	char devName[32];
	char serialNum[32];
	int dev_Handle;
	std::string s_devName;
	dev_num = MTUSB_LEDDriverInitDevices();
	for(int i = 0; i < dev_num; i++)
	{
		dev_Handle = MTUSB_LEDDriverOpenDevice(i);
		if(dev_Handle >= 0)
			if(HidGetDeviceName(dev_Handle, devName, sizeof(devName)) > 0)
				if(MTUSB_LEDDriverSerialNumber(dev_Handle, serialNum, sizeof(serialNum)) > 0)
				{
					sprintf(ledName, "%s:%s", devName, serialNum);
					AddAllowedValue( "Devices", ledName);
					s_devName = ledName;
					devNameList.push_back(s_devName);
				}
		MTUSB_LEDDriverCloseDevice(dev_Handle);
	}
}


/*---------------------------------------------------------------------------
 Destructor.
---------------------------------------------------------------------------*/
Mightex_Sirius_SLC_USB::~Mightex_Sirius_SLC_USB()
{
	Shutdown();

	HidUnInit();
}


/*---------------------------------------------------------------------------
 This function returns the device name ("Mightex Sirius SLC(USB)").
---------------------------------------------------------------------------*/
void Mightex_Sirius_SLC_USB::GetName(char* Name) const
{
	CDeviceUtils::CopyLimitedString(Name, g_DeviceSiriusSLCUSBName);
}


/*---------------------------------------------------------------------------
 This function initialize a Mightex_Sirius_SLC_USB device and creates the actions.
---------------------------------------------------------------------------*/
int Mightex_Sirius_SLC_USB::Initialize()
{

	int nRet;

	if(dev_num > 0)
	{
		devHandle = MTUSB_LEDDriverOpenDevice(cur_dev);
		if(devHandle >= 0)
		{
			channels = MTUSB_LEDDriverDeviceChannels(devHandle);
			devModuleType = MTUSB_LEDDriverDeviceModuleType(devHandle);
		}
	}

	// Initialize the Mode action
	CPropertyAction* pAct = new CPropertyAction (this, &Mightex_Sirius_SLC_USB::OnMode);
	nRet = CreateProperty("mode", "DISABLE", MM::String, false, pAct);
	if (DEVICE_OK != nRet)	return nRet;
	AddAllowedValue( "mode", "DISABLE");
	AddAllowedValue( "mode", "NORMAL");
	AddAllowedValue( "mode", "STROBE");
	if((devModuleType != MODULE_MA) && (devModuleType != MODULE_CA))
		AddAllowedValue( "mode", "TRIGGER");

	pAct = new CPropertyAction (this, &Mightex_Sirius_SLC_USB::OnChannel);
	nRet = CreateProperty("channel", "1", MM::Integer, false, pAct);
	if (DEVICE_OK != nRet)	return nRet;
	if(channels > -1)
		SetPropertyLimits("channel", 1, channels);

	ledChannelData.Normal_CurrentMax = 20;
	ledChannelData.Normal_CurrentSet = 10;
	ledChannelData.Strobe_CurrentMax = 20;
	ledChannelData.Trigger_CurrentMax = 20;
	ledChannelData.Strobe_RepeatCnt = 1;
	ledChannelData.Trigger_Polarity = 1;
	ledChannelData.Strobe_Profile[0][0] = 0;
	ledChannelData.Strobe_Profile[0][1] = 500000;
	ledChannelData.Strobe_Profile[1][0] = 10;
	ledChannelData.Strobe_Profile[1][1] = 500000;
	ledChannelData.Strobe_Profile[2][0] = 0;
	ledChannelData.Strobe_Profile[2][1] = 0;
	ledChannelData.Trigger_Profile[0][0] = 0;
	ledChannelData.Trigger_Profile[0][1] = 500000;
	ledChannelData.Trigger_Profile[1][0] = 10;
	ledChannelData.Trigger_Profile[1][1] = 500000;
	ledChannelData.Trigger_Profile[2][0] = 0;
	ledChannelData.Trigger_Profile[2][1] = 0;

	pAct = new CPropertyAction (this, &Mightex_Sirius_SLC_USB::OnSetImax);
	nRet = CreateProperty("iMax", "20", MM::Integer, false, pAct);
	if (DEVICE_OK != nRet)	return nRet;

	pAct = new CPropertyAction (this, &Mightex_Sirius_SLC_USB::OnSetNormalCurrent);
	nRet = CreateProperty("normal_CurrentSet", "10", MM::Integer, false, pAct);
	if (DEVICE_OK != nRet)	return nRet;
	SetPropertyLimits("normal_CurrentSet", 0, 20);

	pAct = new CPropertyAction (this, &Mightex_Sirius_SLC_USB::OnSetRatio);
	nRet = CreateProperty("ratio", "50", MM::Integer, false, pAct);
	if (DEVICE_OK != nRet)	return nRet;
	SetPropertyLimits("ratio", 0, 100);

	pAct = new CPropertyAction (this, &Mightex_Sirius_SLC_USB::OnSetPeriod);
	nRet = CreateProperty("frequency", "1", MM::Integer, false, pAct);
	if (DEVICE_OK != nRet)	return nRet;

	pAct = new CPropertyAction (this, &Mightex_Sirius_SLC_USB::OnSetI1);
	nRet = CreateProperty("i1", "0", MM::Integer, false, pAct);
	if (DEVICE_OK != nRet)	return nRet;
	SetPropertyLimits("i1", 0, 20);

	pAct = new CPropertyAction (this, &Mightex_Sirius_SLC_USB::OnSetI2);
	nRet = CreateProperty("i2", "10", MM::Integer, false, pAct);
	if (DEVICE_OK != nRet)	return nRet;
	SetPropertyLimits("i2", 0, 20);

	pAct = new CPropertyAction (this, &Mightex_Sirius_SLC_USB::OnSetRepeatCnt);
	nRet = CreateProperty("repeatCnt", "1", MM::Integer, false, pAct);
	if (DEVICE_OK != nRet)	return nRet;

	// Initialize the status query action
	pAct = new CPropertyAction (this, &Mightex_Sirius_SLC_USB::OnStatus);
	nRet = CreateProperty("Status", "No Fault", MM::String, true, pAct);
	if (DEVICE_OK != nRet)	return nRet;

	CreateStaticReadOnlyProperties();

	m_initialized = true;

	// init message
	std::ostringstream log;
	log << "Mightex Sirius SLC(USB) - initializied " << "S/N: " << m_serialNumber;
	LogMessage(log.str().c_str());

	return DEVICE_OK;
}


/*---------------------------------------------------------------------------
 This function sets the LED output to off in case the Mightex_Sirius_SLC_USB was initialized.
---------------------------------------------------------------------------*/
int Mightex_Sirius_SLC_USB::Shutdown()
{
   if (m_initialized)
   {
      LEDOnOff(false);
		if(devHandle >= 0)
			MTUSB_LEDDriverCloseDevice(devHandle);
		channels = -1;
		devHandle = -1;
	 	m_initialized = false;
   }

   return DEVICE_OK;
}


/*---------------------------------------------------------------------------
 This function returns true in case device is busy.
---------------------------------------------------------------------------*/
bool Mightex_Sirius_SLC_USB::Busy()
{
	return false;
}


/*---------------------------------------------------------------------------
 This function sets the LED output.
---------------------------------------------------------------------------*/
int Mightex_Sirius_SLC_USB::SetOpen(bool open)
{
	int val = 0;

	(open) ? val = 1 : val = 0;
   return LEDOnOff(val);
}


/*---------------------------------------------------------------------------
 This function returns the LED output.
---------------------------------------------------------------------------*/
int Mightex_Sirius_SLC_USB::GetOpen(bool &open)
{
	m_LEDOn = "Undefined";
	int ret = DEVICE_OK;

	if (m_LEDOn == "On")
	{
		open = false;
	}
	else
	{
		open = true;
	}

	return ret;
}


/*---------------------------------------------------------------------------
 This function does nothing but is recommended by the shutter API.
---------------------------------------------------------------------------*/
int Mightex_Sirius_SLC_USB::Fire(double)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}


/*---------------------------------------------------------------------------
 This function sets the LED output on or off.
---------------------------------------------------------------------------*/
int Mightex_Sirius_SLC_USB::LEDOnOff(int onoff)
{
	int ret = DEVICE_OK;

	if (onoff == 0)
	{
		m_LEDOn = "Off";
		if(devHandle >= 0)
			MTUSB_LEDDriverSetMode(devHandle, m_channel, 0); //mode is "DISABLE"
	}
	else
	{
		m_LEDOn = "On";
		if(devHandle >= 0)
		{
			if(mode == STROBE_MODE || mode == TRIGGER_MODE)
			{
				m_period = (m_period==0)?1:m_period;
				long t1, t2;
				long t = 1000000/m_period;
				t = t/20 * 20;
				t = (t<40)?40:t;
				if((devModuleType == MODULE_MA) || (devModuleType == MODULE_CA))
				{
					t = t/100 * 100;
					t = (t<2000)?2000:t;
				}
				t2 = t * m_ratio /100;
				t2 = t2/20 * 20;
				t2 = (t2==0)?20:t2;
				t1 = t - t2;
				if(t1 == 0)
				{
					t1 = 20;
					t2 -= 20;
				}
				ledChannelData.Strobe_Profile[0][1] = t1;
				ledChannelData.Strobe_Profile[1][1] = t2;
				ledChannelData.Trigger_Profile[0][1] = t1;
				ledChannelData.Trigger_Profile[1][1] = t2;
			}

			if(mode == STROBE_MODE)
				MTUSB_LEDDriverSetStrobePara(devHandle, m_channel, &ledChannelData);
			else if(mode == TRIGGER_MODE)
				MTUSB_LEDDriverSetTriggerPara(devHandle, m_channel, &ledChannelData);
			MTUSB_LEDDriverSetMode(devHandle, m_channel, mode);
		}
	}


	if (ret != DEVICE_OK) return ret;
	// error handling
	getLastError(&ret);

	return ret;
}


///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////
/*---------------------------------------------------------------------------
 This function reacts on device changes.
---------------------------------------------------------------------------*/

int Mightex_Sirius_SLC_USB::OnDevices(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	std::string temp;
	if (eAct == MM::BeforeGet)
	{
		pProp->Set(m_name.c_str());
	}
	else if (eAct == MM::AfterSet)
	{
		if (m_initialized)
		{
			// revert
			pProp->Set(m_name.c_str());
			return ERR_PORT_CHANGE_FORBIDDEN;
		}

		pProp->Get(temp);
		if(temp == m_name || temp == "")
			return DEVICE_OK;
		else
		{
			int devIndex = 0;
			std::vector<std::string>::iterator ii;
			for( ii = devNameList.begin(); devNameList.end()!=ii; ++ii)
			{
				if(temp == *ii)
				{
					m_name = temp;
					cur_dev = devIndex;
				}
				devIndex++;
			}
		}

	}

	return DEVICE_OK;
}

int Mightex_Sirius_SLC_USB::OnMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		pProp->Set(m_mode.c_str());
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(m_mode);
		if(m_mode == "DISABLE")
			mode = DISABLE_MODE;
		else if(m_mode == "NORMAL")
			mode = NORMAL_MODE;
		else if(m_mode == "STROBE")
			mode = STROBE_MODE;
		else if(m_mode == "TRIGGER")
			mode = TRIGGER_MODE;
		else
			mode = 0;
		if(devHandle >= 0)
			if(mode < STROBE_MODE)
				MTUSB_LEDDriverSetMode(devHandle, m_channel, mode);
	}

	return DEVICE_OK;
}

int Mightex_Sirius_SLC_USB::OnChannel(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		pProp->Set(m_channel);
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(m_channel);
	}

	return DEVICE_OK;
}

int Mightex_Sirius_SLC_USB::OnSetImax(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	long temp;
	if (eAct == MM::BeforeGet)
	{
		temp = ledChannelData.Normal_CurrentMax;
		pProp->Set(temp);
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(temp);
		int temp_data = ledChannelData.Normal_CurrentMax;
		ledChannelData.Normal_CurrentMax = temp;
		if(ledChannelData.Normal_CurrentSet > ledChannelData.Normal_CurrentMax)
			ledChannelData.Normal_CurrentSet = ledChannelData.Normal_CurrentMax;
		if(devHandle >= 0)
		{
			if(MTUSB_LEDDriverSetNormalPara(devHandle, m_channel, &ledChannelData) != 0)
				ledChannelData.Normal_CurrentMax = temp_data;
			MTUSB_LEDDriverSetNormalCurrent(devHandle, m_channel, ledChannelData.Normal_CurrentSet);
		}
		SetPropertyLimits("normal_CurrentSet", 0, ledChannelData.Normal_CurrentMax);
		SetPropertyLimits("i1", 0, ledChannelData.Normal_CurrentMax);
		SetPropertyLimits("i2", 0, ledChannelData.Normal_CurrentMax);
	}

	return DEVICE_OK;
}

int Mightex_Sirius_SLC_USB::OnSetNormalCurrent(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	long temp;
	if (eAct == MM::BeforeGet)
	{
		temp = ledChannelData.Normal_CurrentSet;
		pProp->Set(temp);
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(temp);
		if(devHandle >= 0)
			if(MTUSB_LEDDriverSetNormalCurrent(devHandle, m_channel, temp) == 0)
				ledChannelData.Normal_CurrentSet = temp;
	}

	return DEVICE_OK;
}

int Mightex_Sirius_SLC_USB::OnSetRepeatCnt(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	long temp;
	if (eAct == MM::BeforeGet)
	{
		temp = ledChannelData.Strobe_RepeatCnt;
		pProp->Set(temp);
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(temp);
		ledChannelData.Strobe_RepeatCnt = temp;
	}

	return DEVICE_OK;
}

int Mightex_Sirius_SLC_USB::OnSetRatio(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		pProp->Set(m_ratio);
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(m_ratio);
	}

	return DEVICE_OK;
}

int Mightex_Sirius_SLC_USB::OnSetPeriod(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		pProp->Set(m_period);
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(m_period);
	}

	return DEVICE_OK;
}

int Mightex_Sirius_SLC_USB::OnSetI1(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	long temp;
	if (eAct == MM::BeforeGet)
	{
		temp = ledChannelData.Strobe_Profile[0][0];
		pProp->Set(temp);
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(temp);
		ledChannelData.Strobe_Profile[0][0] = temp;
		ledChannelData.Trigger_Profile[0][0] = temp;
	}

	return DEVICE_OK;
}

int Mightex_Sirius_SLC_USB::OnSetI2(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	long temp;
	if (eAct == MM::BeforeGet)
	{
		temp = ledChannelData.Strobe_Profile[1][0];
		pProp->Set(temp);
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(temp);
		ledChannelData.Strobe_Profile[1][0] = temp;
		ledChannelData.Trigger_Profile[1][0] = temp;
	}

	return DEVICE_OK;
}

/*---------------------------------------------------------------------------
 This function is the callback function for the "status" query.
---------------------------------------------------------------------------*/
int Mightex_Sirius_SLC_USB::OnStatus(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_OK;

	if (eAct == MM::BeforeGet)
	{
		// clear string
		m_status.clear();
		if (m_LEDOn == "On")
			m_status = "LED open ";
		else
			m_status = "LED close ";

		pProp->Set(m_status.c_str());
	}

	return ret;
}

/*---------------------------------------------------------------------------
 This function clears the dynamic error list.
---------------------------------------------------------------------------*/
bool Mightex_Sirius_SLC_USB::dynErrlist_free(void)
{
	for(unsigned int i = 0; i < m_dynErrlist.size(); i++)
	{
		delete m_dynErrlist[i];
	}

	m_dynErrlist.clear();

	return true;
}


/*---------------------------------------------------------------------------
 This function searches for specified error code within the dynamic error
 list. It returns TRUE(1) and the error string in case the error code was
 found. FALSE(0) otherwise.
---------------------------------------------------------------------------*/
bool Mightex_Sirius_SLC_USB::dynErrlist_lookup(int err, std::string* descr)
{
	for(unsigned int i = 0; i < m_dynErrlist.size(); i++)
	{
		if(m_dynErrlist[i]->err == err)
		{
			if(descr)	*descr = m_dynErrlist[i]->descr;
			return true;
		}
	}

	return false;
}


/*---------------------------------------------------------------------------
 This function adds an errror and its description to list. In case the error
 is already known, it returns true.
---------------------------------------------------------------------------*/
bool Mightex_Sirius_SLC_USB::dynErrlist_add(int err, std::string descr)
{
	// when the error is already in list return true
	if((dynErrlist_lookup(err, NULL)) == true)	return true;

	// add error to list
	DynError* dynError = new DynError();
	dynError->err = err;
	dynError->descr = descr;
	m_dynErrlist.push_back(dynError);

	// publish the new error to Manager
	SetErrorText((int)(dynError->err), descr.c_str());

	return true;
}


/*---------------------------------------------------------------------------
 This function requests the last error from the Mightex Sirius SLC USB device.
---------------------------------------------------------------------------*/
bool Mightex_Sirius_SLC_USB::getLastError(int* err)
{
	std::string errDescr = "";
	int errCode = 0;

	// preset
	if(err) *err = 0;
	// check if there is no error
	if(errCode == 0)
	{
		if(err) *err = 0;
		return true;
	}
	// add error to list
	dynErrlist_add(errCode + ERR_Mightex_Sirius_SLC_OFFSET, errDescr);
	// publish the error code
	if(err) *err = errCode + ERR_Mightex_Sirius_SLC_OFFSET;
	// return
	return true;
}


/*---------------------------------------------------------------------------
 This function creates the static read only properties.
---------------------------------------------------------------------------*/
int Mightex_Sirius_SLC_USB::CreateStaticReadOnlyProperties(void)
{
	int nRet = DEVICE_OK;
	char serialNum[32] = "";

	nRet = CreateProperty("Device Name", m_name.c_str(), MM::String, true);
	if (DEVICE_OK != nRet)	return nRet;

	if(devHandle >= 0)
		MTUSB_LEDDriverSerialNumber(devHandle, serialNum, sizeof(serialNum));
	m_serialNumber = serialNum;
	nRet = CreateProperty("Serial Number", m_serialNumber.c_str(), MM::String, true);
	if (DEVICE_OK != nRet)	return nRet;

	return nRet;
}


/****************************************************************************

 class: 			Mightex_Sirius_SLC_RS232
 description:	The class Mightex_Sirius_SLC_RS232 is derived from a shutter base interface and
					can be used for Mightex Sirius SLC RS232 devices.

****************************************************************************/
