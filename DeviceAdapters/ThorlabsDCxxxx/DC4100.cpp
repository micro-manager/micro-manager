#include "DC4100.h"

/****************************************************************************

 class: 			DC4100
 description:	The class DC4100 is derived from a shutter base interface and
					can be used for DC4100 devices.

****************************************************************************/
/*---------------------------------------------------------------------------
 Default constructor.
---------------------------------------------------------------------------*/
DC4100::DC4100(const char* deviceName) :
m_devName(deviceName),
	m_name("Undefined"),
   m_port("Undefined"),
   m_LEDOn("On"),
	m_mode("Constant Current"),
   m_status("No Fault"),
	m_serialNumber("n/a"),
   m_firmwareRev("n/a"),
   m_initialized(false)
{
	InitializeDefaultErrorMessages();
	SetErrorText(ERR_PORT_CHANGE_FORBIDDEN, "You can't change the port after device has been initialized.");
	SetErrorText(ERR_INVALID_DEVICE, "The attached device is not a DC4100 or LEDD4.");

	// preset
	for(int i = 0; i < NUM_LEDS; i++)
	{
		m_limitCurrent[i] = 0;
		m_maximumCurrent[i] = 0;
		m_constCurrent[i] = 0;
		m_percBrightness[i] = 0;
		m_channelAvailable[i] = 0;
	}

	// Name
	CreateProperty(MM::g_Keyword_Name, deviceName, MM::String, true);

	// Description
	CreateProperty(MM::g_Keyword_Description, "Thorlabs DC4100", MM::String, true);

	// Port
	CPropertyAction* pAct = new CPropertyAction (this, &DC4100::OnPort);
	CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);
}


/*---------------------------------------------------------------------------
 Destructor.
---------------------------------------------------------------------------*/
DC4100::~DC4100()
{
	Shutdown();
}


/*---------------------------------------------------------------------------
 This function returns the device name ("DC4100").
---------------------------------------------------------------------------*/
void DC4100::GetName(char* Name) const
{
	CDeviceUtils::CopyLimitedString(Name, m_devName);
}


/*---------------------------------------------------------------------------
 This function initialize a DC4100 device and creates the actions.
---------------------------------------------------------------------------*/
int DC4100::Initialize()
{
	int status = 0;

	// validate
	int nRet = ValidateDevice();
	if (DEVICE_OK != nRet)	return nRet;

	// Initialize the mode selection action
	CPropertyAction* pAct = new CPropertyAction (this, &DC4100::OnOperationMode);
	nRet = CreateProperty("Operation Mode", "Constant Current", MM::String, false, pAct);
	std::vector<std::string> commands2;
	commands2.push_back("Constant Current");
	commands2.push_back("Brightness Mode");
	commands2.push_back("External Control");
	SetAllowedValues("Operation Mode", commands2);
	if (DEVICE_OK != nRet)	return nRet;

	// Initialize the status query action
	pAct = new CPropertyAction (this, &DC4100::OnStatus);
	nRet = CreateProperty("Status", "No Fault", MM::String, true, pAct);
	if (DEVICE_OK != nRet)	return nRet;

	// Check the status word to see which channels are valid
	nRet = GetStatus(&status);
	if (DEVICE_OK != nRet)	return nRet;
	if (!(status & 0x00000020))	m_channelAvailable[0] = 1;
	if (!(status & 0x00000080))	m_channelAvailable[1] = 1;
	if (!(status & 0x00000200))	m_channelAvailable[2] = 1;
	if (!(status & 0x00000800))	m_channelAvailable[3] = 1;

	CPropertyActionEx* pActEx;
   for (long i = 0; i < NUM_LEDS; i++)
	{
		// skip channels which are not available
		if(!m_channelAvailable[i])	continue;

		// constant current
		pActEx = new CPropertyActionEx(this, &DC4100::OnConstantCurrent, i);
		std::ostringstream os1;
		os1 << "Constant Current LED-" << i+1;
		nRet = CreateProperty(os1.str().c_str(), "0", MM::Integer, false, pActEx);
		if (nRet != DEVICE_OK) return nRet;
		SetPropertyLimits(os1.str().c_str(), 0, 1000);

		// percental brightness
		pActEx = new CPropertyActionEx(this, &DC4100::OnPercentalBrightness, i);
		std::ostringstream os2;
		os2 << "Percental Brightness LED-" << i+1;
		nRet = CreateProperty(os2.str().c_str(), "0", MM::Integer, false, pActEx);
		if (nRet != DEVICE_OK) return nRet;
		SetPropertyLimits(os2.str().c_str(), 0, 100);

		// limit current
		pActEx = new CPropertyActionEx(this, &DC4100::OnLimitCurrent, i);
		std::ostringstream os3;
		os3 << "Limit Current LED-" << i+1;
		nRet = CreateProperty(os3.str().c_str(), "1000", MM::Integer, false, pActEx);
		if (nRet != DEVICE_OK) return nRet;
		SetPropertyLimits(os3.str().c_str(), 0, 1000);

		// maximum current
		pActEx = new CPropertyActionEx(this, &DC4100::OnMaximumCurrent, i);
		std::ostringstream os4;
		os4 << "Maximum Current LED-" << i+1;
		nRet = CreateProperty(os4.str().c_str(), "1000", MM::Integer, true, pActEx);
		if (nRet != DEVICE_OK) return nRet;
		SetPropertyLimits(os4.str().c_str(), 0, 1000);
   }

	CreateStaticReadOnlyProperties();

	// for safety
	LEDOnOff(false);

	m_initialized = true;

	// init message
	std::ostringstream log;
	log << "DC4100 - initializied " << "S/N: " << m_serialNumber << " Rev: " << m_firmwareRev;
	LogMessage(log.str().c_str());

	return DEVICE_OK;
}


/*---------------------------------------------------------------------------
 This function sets the LED output to off in case the DC3100 was initialized.
---------------------------------------------------------------------------*/
int DC4100::Shutdown()
{
   if (m_initialized)
   {
      LEDOnOff(false);
	 	m_initialized = false;
   }

   return DEVICE_OK;
}


/*---------------------------------------------------------------------------
 This function returns true in case device is busy.
---------------------------------------------------------------------------*/
bool DC4100::Busy()
{
	return false;
}


/*---------------------------------------------------------------------------
 This function sets the LED output.
---------------------------------------------------------------------------*/
int DC4100::SetOpen(bool open)
{
	int val = 0;

	(open) ? val = 1 : val = 0;
   return LEDOnOff(val);
}


/*---------------------------------------------------------------------------
 This function returns the LED output.
---------------------------------------------------------------------------*/
int DC4100::GetOpen(bool &open)
{
   std::ostringstream command[NUM_LEDS];
	std::string answer[NUM_LEDS];
	m_LEDOn = "Undefined";
	int ret = DEVICE_OK;

	SwitchToMultiSelection();

	for(int i = 0; i < NUM_LEDS; i++)
	{
		// skip channels which are not available
		if(!m_channelAvailable[i])	continue;

		command[i] << "o? " << i;
		ret = SendSerialCommand(m_port.c_str(), command[i].str().c_str(), "\r\n");
		if (ret != DEVICE_OK) return ret;
		ret = GetSerialAnswer(m_port.c_str(), "\r\n", answer[i]);
		if (ret != DEVICE_OK) return ret;
		// error handling
		getLastError(&ret);
		if (answer[i].at(0) == '0')
		{
			m_LEDOn = "Off";
			open = false;
			return ret;
		}
	}

	m_LEDOn = "On";
	open = true;

	return ret;
}


/*---------------------------------------------------------------------------
 This function does nothing but is recommended by the shutter API.
---------------------------------------------------------------------------*/
int DC4100::Fire(double)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}


/*---------------------------------------------------------------------------
 This function sets the LED output on or off.
---------------------------------------------------------------------------*/
int DC4100::LEDOnOff(int onoff)
{
	int ret = DEVICE_OK;
	std::string answer;
	std::ostringstream command;

	// check for multi selection mode
	ret = SwitchToMultiSelection();
	if (ret != DEVICE_OK) return ret;

	if (onoff == 0)
	{
		 command << "o -1 0";
		 m_LEDOn = "Off";
	}
	else
	{
		 command << "o -1 1";
		 m_LEDOn = "On";
	}

	ret = SendSerialCommand(m_port.c_str(), command.str().c_str(), "\r\n");
	if (ret != DEVICE_OK) return ret;
	// error handling
	getLastError(&ret);

	return ret;
}


/*---------------------------------------------------------------------------
 This function guarantees that the device is set do multi selection mode.
---------------------------------------------------------------------------*/
int DC4100::SwitchToMultiSelection(void)
{
	int ret = DEVICE_OK;
	std::string answer;
	std::ostringstream command;
	std::ostringstream command2;

	// check for multi selection mode
	command << "sm?";
	ret = SendSerialCommand(m_port.c_str(), command.str().c_str(), "\r\n");
	if (ret != DEVICE_OK) return ret;
	ret = GetSerialAnswer(m_port.c_str(), "\r\n", answer);
	if (ret != DEVICE_OK) return ret;
	// error handling
	getLastError(&ret);
	if (ret != DEVICE_OK) return ret;

	if (answer.at(0) != '0')
	{
		// set multi selection mode
		command2 << "sm 0";
		ret = SendSerialCommand(m_port.c_str(), command2.str().c_str(), "\r\n");
		if (ret != DEVICE_OK) return ret;
		getLastError(&ret);
		if (ret != DEVICE_OK) return ret;
	}

	return ret;
}


///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////
/*---------------------------------------------------------------------------
 This function reacts on port changes.
---------------------------------------------------------------------------*/
int DC4100::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		pProp->Set(m_port.c_str());
	}
	else if (eAct == MM::AfterSet)
	{
		if (m_initialized)
		{
			// revert
			pProp->Set(m_port.c_str());
			return ERR_PORT_CHANGE_FORBIDDEN;
		}

		pProp->Get(m_port);
	}

	return DEVICE_OK;
}


/*---------------------------------------------------------------------------
 This function clears the dynamic error list.
---------------------------------------------------------------------------*/
bool DC4100::dynErrlist_free(void)
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
bool DC4100::dynErrlist_lookup(int err, std::string* descr)
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
bool DC4100::dynErrlist_add(int err, std::string descr)
{
	// when the error is already in list return true
	if((dynErrlist_lookup(err, NULL)) == true)	return true;

	// add error to list
	DynError* dynError = new DynError();
	dynError->err = err;
	dynError->descr = descr;
	m_dynErrlist.push_back(dynError);

	// publish the new error to µManager
	SetErrorText((int)(dynError->err), descr.c_str());

	return true;
}


/*---------------------------------------------------------------------------
 This function request the last error from the DC2010.
---------------------------------------------------------------------------*/
bool DC4100::getLastError(int* err)
{
	std::ostringstream errRequest;
	std::string answer;
	std::string errDescr;
	int errCode;

	// preset
	if(err) *err = 0;
	// prepare error request
	errRequest << "e?";
	// send error request
	SendSerialCommand(m_port.c_str(), errRequest.str().c_str(), "\r\n");
	// receive the answer
	GetSerialAnswer(m_port.c_str(), "\r\n", answer);
	// parsing out the information
	std::string tmp;
	std::istringstream iss(answer);
	std::getline(iss, tmp, ' ');
	iss >> errCode;
	std::getline(iss, tmp, ':');
	std::getline(iss, errDescr);
	// check if there is no error
	if(errCode == 0)
	{
		if(err) *err = 0;
		return true;
	}
	// add error to list
	dynErrlist_add(errCode + ERR_DCxxxx_OFFSET, errDescr);
	// publish the error code
	if(err) *err = errCode + ERR_DCxxxx_OFFSET;
	// return
	return true;
}

/*---------------------------------------------------------------------------
 This function is the callback function for the "operation mode" property.
---------------------------------------------------------------------------*/
int DC4100::OnOperationMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	std::ostringstream command;
	std::string answer;
	m_mode = "Undefined";
	int ret = DEVICE_OK;

	if (eAct == MM::BeforeGet)
	{
		command << "m?";
		int ret = SendSerialCommand(m_port.c_str(), command.str().c_str(), "\r\n");
		if (ret != DEVICE_OK) return ret;
		ret = GetSerialAnswer(m_port.c_str(), "\r\n", answer);
		if (ret != DEVICE_OK) return ret;
		// error handling
		getLastError(&ret);

		if (answer.at(0) == '0')
			m_mode = "Constant Current";
		else if (answer.at(0) == '1')
			m_mode = "Brightness Mode";
		else if (answer.at(0) == '2')
			m_mode = "External Control";

		pProp->Set(m_mode.c_str());
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(answer);
		if (answer == "Constant Current")
		{
			command << "m 0";
			m_mode = "Constant Current";
		}
		else if (answer == "Brightness Mode")
		{
			command << "m 1";
			m_mode = "Internal Modulation";
		}
		else
		{
			command << "m 2";
			m_mode = "External Control";
		}

		LEDOnOff(0);

		int ret = SendSerialCommand(m_port.c_str(), command.str().c_str(), "\r\n");
		if (ret != DEVICE_OK) return ret;
		// error handling
		getLastError(&ret);
	}

	return ret;
}


/*---------------------------------------------------------------------------
 This function is the callback function for the "status" query.
---------------------------------------------------------------------------*/
int DC4100::OnStatus(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_OK;
	std::ostringstream command;
	std::string answer;
	int tmp = 0;

	if (eAct == MM::BeforeGet)
	{
		ret = GetStatus(&tmp);
		if (ret != DEVICE_OK) return ret;

		// clear string
		m_status.clear();

		if (tmp & 0x00000002)	m_status += "VCC Fail ";
		if (tmp & 0x00000008)	m_status += "OTP ";
		if (tmp & 0x00000020)	m_status += "No LED1 ";
		if (tmp & 0x00000080)	m_status += "No LED2 ";
		if (tmp & 0x00000200)	m_status += "No LED3 ";
		if (tmp & 0x00000800)	m_status += "No LED4 ";
		if (tmp & 0x00002000)	m_status += "LED1 open ";
		if (tmp & 0x00008000)	m_status += "LED2 open ";
		if (tmp & 0x00020000)	m_status += "LED3 open ";
		if (tmp & 0x00080000)	m_status += "LED4 open ";
		if (tmp & 0x00200000)	m_status += "Limit LED1 ";
		if (tmp & 0x00800000)	m_status += "Limit LED2 ";
		if (tmp & 0x02000000)	m_status += "Limit LED3 ";
		if (tmp & 0x08000000)	m_status += "Limit LED4 ";
		if (!tmp)			m_status = "No Fault";

		pProp->Set(m_status.c_str());
	}

	return ret;
}


/*---------------------------------------------------------------------------
 This function is the callback function for the "Constant Current" property.
---------------------------------------------------------------------------*/
int DC4100::OnConstantCurrent(MM::PropertyBase* pProp, MM::ActionType eAct, long index)
{
	int ret = DEVICE_OK;
	std::string answer;
	std::ostringstream command;

	if (eAct == MM::BeforeGet)
	{
		command << "cc? " << index;
		ret = SendSerialCommand(m_port.c_str(), command.str().c_str(), "\r\n");
		if (ret != DEVICE_OK) return ret;
		ret = GetSerialAnswer(m_port.c_str(), "\r\n", answer);
		if (ret != DEVICE_OK) return ret;
		// error handling
		getLastError(&ret);

		std::istringstream iss(answer);
		iss >> m_constCurrent[index];
		pProp->Set(m_constCurrent[index]);
	}
	else if (eAct == MM::AfterSet)
	{
		// get property
		pProp->Get(m_constCurrent[index]);
		// prepare command
		command << "cc " << index << " " << m_constCurrent[index];
		// send command
		ret = SendSerialCommand(m_port.c_str(), command.str().c_str(), "\r\n");
		if (ret != DEVICE_OK) return ret;
		// error handling
		getLastError(&ret);
	}

	return ret;
}


/*---------------------------------------------------------------------------
 This function is the callback function for the "Percental Brightness" property.
---------------------------------------------------------------------------*/
int DC4100::OnPercentalBrightness(MM::PropertyBase* pProp, MM::ActionType eAct, long index)
{
	int ret = DEVICE_OK;
	std::string answer;
	std::ostringstream command;

	if (eAct == MM::BeforeGet)
	{
		command << "bp? " << index;
		ret = SendSerialCommand(m_port.c_str(), command.str().c_str(), "\r\n");
		if (ret != DEVICE_OK) return ret;
		ret = GetSerialAnswer(m_port.c_str(), "\r\n", answer);
		if (ret != DEVICE_OK) return ret;
		// error handling
		getLastError(&ret);

		std::istringstream iss(answer);
      double d;
      iss >> d;
      m_percBrightness[index] = (int) d;
		pProp->Set(m_percBrightness[index]);
	}
	else if (eAct == MM::AfterSet)
	{
		// get property
		pProp->Get(m_percBrightness[index]);
		// prepare command
		command << "bp " << index << " " << m_percBrightness[index];
		// send command
		ret = SendSerialCommand(m_port.c_str(), command.str().c_str(), "\r\n");
		if (ret != DEVICE_OK) return ret;
		// error handling
		getLastError(&ret);
	}

	return ret;
}


/*---------------------------------------------------------------------------
 This function is the callback function for the "Limit current" property.
---------------------------------------------------------------------------*/
int DC4100::OnLimitCurrent(MM::PropertyBase* pProp, MM::ActionType eAct, long index)
{
	int ret = DEVICE_OK;
	std::string answer;
	std::ostringstream command;

	if (eAct == MM::BeforeGet)
	{
		command << "l? " << index;
		ret = SendSerialCommand(m_port.c_str(), command.str().c_str(), "\r\n");
		if (ret != DEVICE_OK) return ret;
		ret = GetSerialAnswer(m_port.c_str(), "\r\n", answer);
		if (ret != DEVICE_OK) return ret;
		// error handling
		getLastError(&ret);

		std::istringstream iss(answer);
		iss >> m_limitCurrent[index];
		pProp->Set(m_limitCurrent[index]);
	}
	else if (eAct == MM::AfterSet)
	{
		// get property
		pProp->Get(m_limitCurrent[index]);
		// prepare command
		command << "l " << index << " " << m_limitCurrent[index];
		// send command
		ret = SendSerialCommand(m_port.c_str(), command.str().c_str(), "\r\n");
		if (ret != DEVICE_OK) return ret;
		// error handling
		getLastError(&ret);
	}

	// set limits
	std::ostringstream os;
	os << "Constant Current LED-" << index+1;
	SetPropertyLimits(os.str().c_str(), 0, m_limitCurrent[index]);

	return ret;
}


/*---------------------------------------------------------------------------
 This function is the callback function for the "Maximum current" property.
---------------------------------------------------------------------------*/
int DC4100::OnMaximumCurrent(MM::PropertyBase* pProp, MM::ActionType eAct, long index)
{
	int ret = DEVICE_OK;
	std::string answer;
	std::ostringstream command;

	if (eAct == MM::BeforeGet)
	{
		command << "ml? " << index;
		ret = SendSerialCommand(m_port.c_str(), command.str().c_str(), "\r\n");
		if (ret != DEVICE_OK) return ret;
		ret = GetSerialAnswer(m_port.c_str(), "\r\n", answer);
		if (ret != DEVICE_OK) return ret;
		// error handling
		getLastError(&ret);

		std::istringstream iss(answer);
		iss >> m_maximumCurrent[index];
		pProp->Set(m_maximumCurrent[index]);
	}

	// set limits
	std::ostringstream os;
	os << "Limit Current LED-" << index+1;
	SetPropertyLimits(os.str().c_str(), 0, m_maximumCurrent[index]);

	return ret;
}


/*---------------------------------------------------------------------------
 This function creates the static read only properties.
---------------------------------------------------------------------------*/
int DC4100::CreateStaticReadOnlyProperties(void)
{
	int nRet = DEVICE_OK;
	std::ostringstream cmd1;
	std::ostringstream cmd2;
	std::ostringstream wlcmd[NUM_LEDS];
	std::ostringstream wlstr[NUM_LEDS];
	std::ostringstream fbcmd[NUM_LEDS];
	std::ostringstream fbstr[NUM_LEDS];
	std::ostringstream hscmd[NUM_LEDS];
	std::ostringstream hsstr[NUM_LEDS];

	// serial number information
	cmd1 << "s?";
	nRet = SendSerialCommand(m_port.c_str(), cmd1.str().c_str(), "\r\n");
	if (nRet != DEVICE_OK) return nRet;
	nRet = GetSerialAnswer(m_port.c_str(), "\r\n", m_serialNumber);
	if (nRet != DEVICE_OK) return nRet;
	getLastError(&nRet);
	if (nRet != DEVICE_OK) return nRet;
	nRet = CreateProperty("Serial Number", m_serialNumber.c_str(), MM::String, true);
	if (DEVICE_OK != nRet)	return nRet;

	// firmware version information
	cmd2 << "v?";
	nRet = SendSerialCommand(m_port.c_str(), cmd2.str().c_str(), "\r\n");
	if (nRet != DEVICE_OK) return nRet;
	nRet = GetSerialAnswer(m_port.c_str(), "\r\n", m_firmwareRev);
	if (nRet != DEVICE_OK) return nRet;
	getLastError(&nRet);
	if (nRet != DEVICE_OK) return nRet;
	nRet = CreateProperty("Firmware Revision", m_firmwareRev.c_str(), MM::String, true);
	if (DEVICE_OK != nRet)	return nRet;

	for (long i = 0; i < NUM_LEDS; i++)
	{
		// skip channels which are not available
		if(!m_channelAvailable[i])	continue;

		// wavelength
		wlcmd[i] << "wl? " << i;
		nRet = SendSerialCommand(m_port.c_str(), wlcmd[i].str().c_str(), "\r\n");
		if (nRet != DEVICE_OK) return nRet;
		nRet = GetSerialAnswer(m_port.c_str(), "\r\n", m_wavelength[i]);
		if (nRet != DEVICE_OK) return nRet;
		getLastError(&nRet);
		if (nRet != DEVICE_OK) return nRet;
		wlstr[i] << "Wavelength LED-" << i+1;
		nRet = CreateProperty(wlstr[i].str().c_str(), m_wavelength[i].c_str(), MM::String, true);
		if (DEVICE_OK != nRet)	return nRet;

		// forward bias
		fbcmd[i] << "fb? " << i;
		nRet = SendSerialCommand(m_port.c_str(), fbcmd[i].str().c_str(), "\r\n");
		if (nRet != DEVICE_OK) return nRet;
		nRet = GetSerialAnswer(m_port.c_str(), "\r\n", m_forwardBias[i]);
		if (nRet != DEVICE_OK) return nRet;
		getLastError(&nRet);
		if (nRet != DEVICE_OK) return nRet;
		fbstr[i] << "Forward Bias LED-" << i+1;
		nRet = CreateProperty(fbstr[i].str().c_str(), m_forwardBias[i].c_str(), MM::String, true);
		if (DEVICE_OK != nRet)	return nRet;

		// head serial number
		hscmd[i] << "hs? " << i;
		nRet = SendSerialCommand(m_port.c_str(), hscmd[i].str().c_str(), "\r\n");
		if (nRet != DEVICE_OK) return nRet;
		nRet = GetSerialAnswer(m_port.c_str(), "\r\n", m_headSerialNo[i]);
		if (nRet != DEVICE_OK) return nRet;
		getLastError(&nRet);
		if (nRet != DEVICE_OK) return nRet;
		hsstr[i] << "Serial Number LED-" << i+1;
		nRet = CreateProperty(hsstr[i].str().c_str(), m_headSerialNo[i].c_str(), MM::String, true);
		if (DEVICE_OK != nRet)	return nRet;
	}

	return nRet;
}


/*---------------------------------------------------------------------------
 This function checks if the device is valid.
---------------------------------------------------------------------------*/
int DC4100::ValidateDevice(void)
{
	std::string devName;
	std::ostringstream cmd;
	int nRet = DEVICE_OK;

	cmd << "n?";
	nRet = SendSerialCommand(m_port.c_str(), cmd.str().c_str(), "\r\n");
	if (nRet != DEVICE_OK) return nRet;
	nRet = GetSerialAnswer(m_port.c_str(), "\r\n", devName);
	if (nRet != DEVICE_OK) return nRet;
	getLastError(&nRet);
	if (nRet != DEVICE_OK) return nRet;

   if( (devName.find("DC4100")) == std::string::npos && 
         (devName.find("DC4104")) == std::string::npos &&
         (devName.find("LEDD4"))  == std::string::npos
         )
       nRet = ERR_INVALID_DEVICE;

	return nRet;
}


/*---------------------------------------------------------------------------
 This requests the device status information.
---------------------------------------------------------------------------*/
int DC4100::GetStatus(int* status)
{
	int ret = DEVICE_OK;
	std::ostringstream command;
	std::string answer;
	int tmp = 0;

	command << "r?";
	ret = SendSerialCommand(m_port.c_str(), command.str().c_str(), "\n");
	if (ret != DEVICE_OK) return ret;
	ret = GetSerialAnswer(m_port.c_str(), "\n", answer);
	if (ret != DEVICE_OK) return ret;
	// error handling
	getLastError(&ret);

	// parsing status byte
	std::istringstream iss(answer);
	iss >> tmp;

	// return the status
	if(status)	*status = tmp;

	return ret;
}
