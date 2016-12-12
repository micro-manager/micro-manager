#include "DC2XXX.h"

/****************************************************************************

 class: 			DC2xxx
 description:	The class DC2xxx is derived from a shutter base interface and
					can be used for DC2010 and for DC2100 devices.

****************************************************************************/
/*---------------------------------------------------------------------------
 Default constructor.
---------------------------------------------------------------------------*/
DC2xxx::DC2xxx(const char* deviceName) :
m_devName(deviceName),
	m_name("Undefined"),
   m_port("Undefined"),
   m_LEDOn("On"),
	m_mode("Constant Current"),
   m_status("No Fault"),
	m_serialNumber("n/a"),
   m_firmwareRev("n/a"),
	m_wavelength("n/a"),
   m_forwardBias("n/a"),
   m_headSerialNo("n/a"),
   m_limitCurrent(0),
   m_maximumCurrent(0),
	m_constCurrent(0),
	m_pwmCurrent(0),
	m_pwmFrequency(0),
	m_pwmDutyCycle(0),
	m_pwmCounts(0),
   m_initialized(false)
{
	InitializeDefaultErrorMessages();
	SetErrorText(ERR_PORT_CHANGE_FORBIDDEN, "You can't change the port after device has been initialized.");
	SetErrorText(ERR_INVALID_DEVICE, "The selected plugin does not fit for the device.");

	// Name
	CreateProperty(MM::g_Keyword_Name, deviceName, MM::String, true);

	// Description
	CreateProperty(MM::g_Keyword_Description, "Thorlabs DC2xxx", MM::String, true);

	// Port
	CPropertyAction* pAct = new CPropertyAction (this, &DC2xxx::OnPort);
	CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);
}


/*---------------------------------------------------------------------------
 Destructor.
---------------------------------------------------------------------------*/
DC2xxx::~DC2xxx()
{
	Shutdown();
}


/*---------------------------------------------------------------------------
 This function returns the device name ("DC2xxx").
---------------------------------------------------------------------------*/
void DC2xxx::GetName(char* Name) const
{
	CDeviceUtils::CopyLimitedString(Name, m_devName);
}


/*---------------------------------------------------------------------------
 This function initialize a DC2xxx device and creates the actions.
---------------------------------------------------------------------------*/
int DC2xxx::Initialize()
{
	// validate
	int nRet = ValidateDevice();
	if (DEVICE_OK != nRet)	return nRet;

	// Initialize the maximum current action
	CPropertyAction* pAct = new CPropertyAction (this, &DC2xxx::OnMaximumCurrent);
	nRet = CreateProperty("Maximum Current", "2000", MM::Integer, true, pAct);
	if (DEVICE_OK != nRet)	return nRet;

	// Initialize the limit current action
	pAct = new CPropertyAction (this, &DC2xxx::OnLimitCurrent);
	nRet = CreateProperty("Limit Current", "2000", MM::Integer, false, pAct);
	SetPropertyLimits("Limit Current", 0, 2000);
	if (DEVICE_OK != nRet)	return nRet;

	// Initialize the constant current action
	pAct = new CPropertyAction (this, &DC2xxx::OnConstantCurrent);
	nRet = CreateProperty("Constant Current", "0", MM::Integer, false, pAct);
	SetPropertyLimits("Constant Current", 0, 2000);
	if (DEVICE_OK != nRet)	return nRet;

	// Initialize the PWM current action
	pAct = new CPropertyAction (this, &DC2xxx::OnPWMCurrent);
	nRet = CreateProperty("PWM Current", "0", MM::Integer, false, pAct);
	SetPropertyLimits("PWM Current", 0, 2000);
	if (DEVICE_OK != nRet)	return nRet;

	// Initialize the PWM frequency action
	pAct = new CPropertyAction (this, &DC2xxx::OnPWMFrequency);
	nRet = CreateProperty("PWM Frequency", "1", MM::Integer, false, pAct);
	SetPropertyLimits("PWM Frequency", 1, 10000);
	if (DEVICE_OK != nRet)	return nRet;

	// Initialize the PWM duty cycle action
	pAct = new CPropertyAction (this, &DC2xxx::OnPWMDutyCycle);
	nRet = CreateProperty("PWM Duty Cycle", "50", MM::Integer, false, pAct);
	SetPropertyLimits("PWM Duty Cycle", 1, 100);
	if (DEVICE_OK != nRet)	return nRet;

	// Initialize the PWM counts action
	pAct = new CPropertyAction (this, &DC2xxx::OnPWMCounts);
	nRet = CreateProperty("PWM Counts", "0", MM::Integer, false, pAct);
	SetPropertyLimits("PWM Counts", 0, 100);
	if (DEVICE_OK != nRet)	return nRet;

	// Initialize the mode selection action
	pAct = new CPropertyAction (this, &DC2xxx::OnOperationMode);
	nRet = CreateProperty("Operation Mode", "Constant Current", MM::String, false, pAct);
	std::vector<std::string> commands2;
	commands2.push_back("Constant Current");
	commands2.push_back("PWM");
	commands2.push_back("External Control");
	SetAllowedValues("Operation Mode", commands2);
	if (DEVICE_OK != nRet)	return nRet;

	// Initialize the status query action
	pAct = new CPropertyAction (this, &DC2xxx::OnStatus);
	nRet = CreateProperty("Status", "No Fault", MM::String, true, pAct);
	if (DEVICE_OK != nRet)	return nRet;

	/*
	pAct = new CPropertyAction (this, &DC2xxx::OnDisplayBrightness);
	nRet = CreateProperty("Display Brightness", "100", MM::Integer, false, pAct);
	SetPropertyLimits("Display Brightness", 0, 100);
	if (DEVICE_OK != nRet)	return nRet;
	*/

	CreateStaticReadOnlyProperties();

	// for safety
	LEDOnOff(false);

	m_initialized = true;

	// init message
	std::ostringstream log;
	log << "DC2xxx - initializied " << "S/N: " << m_serialNumber << " Rev: " << m_firmwareRev;
	LogMessage(log.str().c_str());

	return DEVICE_OK;
}


/*---------------------------------------------------------------------------
 This function sets the LED output to off in case the DC2xxx was initialized.
---------------------------------------------------------------------------*/
int DC2xxx::Shutdown()
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
bool DC2xxx::Busy()
{
	return false;
}


/*---------------------------------------------------------------------------
 This function sets the LED output.
---------------------------------------------------------------------------*/
int DC2xxx::SetOpen(bool open)
{
	int val = 0;

	(open) ? val = 1 : val = 0;
   return LEDOnOff(val);
}


/*---------------------------------------------------------------------------
 This function returns the LED output.
---------------------------------------------------------------------------*/
int DC2xxx::GetOpen(bool &open)
{
   std::ostringstream command;
	std::string answer;
	m_LEDOn = "Undefined";
	int ret = DEVICE_OK;

	command << "o?";
	ret = SendSerialCommand(m_port.c_str(), command.str().c_str(), "\r\n");
	if (ret != DEVICE_OK) return ret;
	ret = GetSerialAnswer(m_port.c_str(), "\r\n", answer);
	if (ret != DEVICE_OK) return ret;
	// error handling
	getLastError(&ret);

	if (answer.at(0) == '0')
	{
		m_LEDOn = "Off";
		open = false;
	}
	else if (answer.at(0) == '1')
	{
		m_LEDOn = "On";
		open = true;
	}

	return ret;
}


/*---------------------------------------------------------------------------
 This function does nothing but is recommended by the shutter API.
---------------------------------------------------------------------------*/
int DC2xxx::Fire(double)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}


/*---------------------------------------------------------------------------
 This function sets the LED output on or off.
---------------------------------------------------------------------------*/
int DC2xxx::LEDOnOff(int onoff)
{
	int ret = DEVICE_OK;
	std::string answer;
	std::ostringstream command;

	if (onoff == 0)
	{
		 command << "o 0";
		 m_LEDOn = "Off";
	}
	else
	{
		 command << "o 1";
		 m_LEDOn = "On";
	}

	ret = SendSerialCommand(m_port.c_str(), command.str().c_str(), "\r\n");
	if (ret != DEVICE_OK) return ret;
	// error handling
	getLastError(&ret);

	return ret;
}


///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////
/*---------------------------------------------------------------------------
 This function reacts on port changes.
---------------------------------------------------------------------------*/
int DC2xxx::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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
 This function is the callback function for the "Limit Current" property.
---------------------------------------------------------------------------*/
int DC2xxx::OnLimitCurrent(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_OK;
	std::string answer;
	std::ostringstream command;

	if (eAct == MM::BeforeGet)
	{
		command << "l?";
		ret = SendSerialCommand(m_port.c_str(), command.str().c_str(), "\r\n");
		if (ret != DEVICE_OK) return ret;
		ret = GetSerialAnswer(m_port.c_str(), "\r\n", answer);
		if (ret != DEVICE_OK) return ret;
		// error handling
		getLastError(&ret);

		std::istringstream iss(answer);
		iss >> m_limitCurrent;
		pProp->Set(m_limitCurrent);
	}
	else if (eAct == MM::AfterSet)
	{
		// get property
		pProp->Get(m_limitCurrent);
		// prepare command
		command << "l " << (int)m_limitCurrent;
		// send command
		ret = SendSerialCommand(m_port.c_str(), command.str().c_str(), "\r\n");
		if (ret != DEVICE_OK) return ret;
		// error handling
		getLastError(&ret);
	}

	// set the limits
	SetPropertyLimits("Constant Current", 0, m_limitCurrent);
	SetPropertyLimits("PWM Current", 0, m_limitCurrent);

	return ret;
}


/*---------------------------------------------------------------------------
 This function is the callback function for the "Maximum Current" query.
---------------------------------------------------------------------------*/
int DC2xxx::OnMaximumCurrent(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_OK;
	std::string answer;
	std::ostringstream command;

	if (eAct == MM::BeforeGet)
	{
		command << "ml?";
		ret = SendSerialCommand(m_port.c_str(), command.str().c_str(), "\r\n");
		if (ret != DEVICE_OK) return ret;
		ret = GetSerialAnswer(m_port.c_str(), "\r\n", answer);
		if (ret != DEVICE_OK) return ret;
		// error handling
		getLastError(&ret);

		std::istringstream iss(answer);
		iss >> m_maximumCurrent;
		pProp->Set(m_maximumCurrent);
	}

	// set the limits
	SetPropertyLimits("Limit Current", 0, m_maximumCurrent);

	return ret;
}


/*---------------------------------------------------------------------------
 This function is the callback function for the "Constant Current" property.
---------------------------------------------------------------------------*/
int DC2xxx::OnConstantCurrent(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_OK;
	std::string answer;
	std::ostringstream command;

	if (eAct == MM::BeforeGet)
	{
		command << "cc?";
		ret = SendSerialCommand(m_port.c_str(), command.str().c_str(), "\r\n");
		if (ret != DEVICE_OK) return ret;
		ret = GetSerialAnswer(m_port.c_str(), "\r\n", answer);
		if (ret != DEVICE_OK) return ret;
		// error handling
		getLastError(&ret);

		std::istringstream iss(answer);
		iss >> m_constCurrent;
		pProp->Set(m_constCurrent);
	}
	else if (eAct == MM::AfterSet)
	{
		// get property
		pProp->Get(m_constCurrent);
		// prepare command
		command << "cc " << (int)m_constCurrent;
		// send command
		ret = SendSerialCommand(m_port.c_str(), command.str().c_str(), "\r\n");
		if (ret != DEVICE_OK) return ret;
		// error handling
		getLastError(&ret);
	}

	return ret;
}


/*---------------------------------------------------------------------------
 This function is the callback function for the "PWM Current" property.
---------------------------------------------------------------------------*/
int DC2xxx::OnPWMCurrent(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_OK;
	std::string answer;
	std::ostringstream command;

	if (eAct == MM::BeforeGet)
	{
		command << "pc?";
		ret = SendSerialCommand(m_port.c_str(), command.str().c_str(), "\r\n");
		if (ret != DEVICE_OK) return ret;
		ret = GetSerialAnswer(m_port.c_str(), "\r\n", answer);
		if (ret != DEVICE_OK) return ret;
		// error handling
		getLastError(&ret);

		std::istringstream iss(answer);
		iss >> m_pwmCurrent;
		pProp->Set(m_pwmCurrent);
	}
	else if (eAct == MM::AfterSet)
	{
		// get property
		pProp->Get(m_pwmCurrent);
		// prepare command
		command << "pc " << (int)m_pwmCurrent;
		// send command
		ret = SendSerialCommand(m_port.c_str(), command.str().c_str(), "\r\n");
		if (ret != DEVICE_OK) return ret;
		// error handling
		getLastError(&ret);
	}

	return ret;
}


/*---------------------------------------------------------------------------
 This function is the callback function for the "PWM Frequency" property.
---------------------------------------------------------------------------*/
int DC2xxx::OnPWMFrequency(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_OK;
	std::string answer;
	std::ostringstream command;

	if (eAct == MM::BeforeGet)
	{
		command << "pf?";
		ret = SendSerialCommand(m_port.c_str(), command.str().c_str(), "\r\n");
		if (ret != DEVICE_OK) return ret;
		ret = GetSerialAnswer(m_port.c_str(), "\r\n", answer);
		if (ret != DEVICE_OK) return ret;
		// error handling
		getLastError(&ret);

		std::istringstream iss(answer);
		iss >> m_pwmFrequency;
		pProp->Set(m_pwmFrequency);
	}
	else if (eAct == MM::AfterSet)
	{
		// get property
		pProp->Get(m_pwmFrequency);
		// prepare command
		command << "pf " << (int)m_pwmFrequency;
		// send command
		ret = SendSerialCommand(m_port.c_str(), command.str().c_str(), "\r\n");
		if (ret != DEVICE_OK) return ret;
		// error handling
		getLastError(&ret);
	}

	return ret;
}


/*---------------------------------------------------------------------------
 This function is the callback function for the "PWM Duty Cycle" property.
---------------------------------------------------------------------------*/
int DC2xxx::OnPWMDutyCycle(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_OK;
	std::string answer;
	std::ostringstream command;

	if (eAct == MM::BeforeGet)
	{
		command << "pd?";
		ret = SendSerialCommand(m_port.c_str(), command.str().c_str(), "\r\n");
		if (ret != DEVICE_OK) return ret;
		ret = GetSerialAnswer(m_port.c_str(), "\r\n", answer);
		if (ret != DEVICE_OK) return ret;
		// error handling
		getLastError(&ret);

		std::istringstream iss(answer);
		iss >> m_pwmDutyCycle;
		pProp->Set(m_pwmDutyCycle);
	}
	else if (eAct == MM::AfterSet)
	{
		// get property
		pProp->Get(m_pwmDutyCycle);
		// prepare command
		command << "pd " << (int)m_pwmDutyCycle;
		// send command
		ret = SendSerialCommand(m_port.c_str(), command.str().c_str(), "\r\n");
		if (ret != DEVICE_OK) return ret;
		// error handling
		getLastError(&ret);
	}

	return ret;
}


/*---------------------------------------------------------------------------
 This function is the callback function for the "PWM Counts" property.
---------------------------------------------------------------------------*/
int DC2xxx::OnPWMCounts(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_OK;
	std::string answer;
	std::ostringstream command;

	if (eAct == MM::BeforeGet)
	{
		command << "pn?";
		ret = SendSerialCommand(m_port.c_str(), command.str().c_str(), "\r\n");
		if (ret != DEVICE_OK) return ret;
		ret = GetSerialAnswer(m_port.c_str(), "\r\n", answer);
		if (ret != DEVICE_OK) return ret;
		// error handling
		getLastError(&ret);

		std::istringstream iss(answer);
		iss >> m_pwmDutyCycle;
		pProp->Set(m_pwmCounts);
	}
	else if (eAct == MM::AfterSet)
	{
		// get property
		pProp->Get(m_pwmCounts);
		// prepare command
		command << "pn " << (int)m_pwmCounts;
		// send command
		ret = SendSerialCommand(m_port.c_str(), command.str().c_str(), "\r\n");
		if (ret != DEVICE_OK) return ret;
		// error handling
		getLastError(&ret);
	}

	return ret;
}


/*---------------------------------------------------------------------------
 This function is the callback function for the "operation mode" property.
---------------------------------------------------------------------------*/
int DC2xxx::OnOperationMode(MM::PropertyBase* pProp, MM::ActionType eAct)
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
			m_mode = "PWM";
		else if (answer.at(0) == '2')
			m_mode = "External Control";

		pProp->Set(m_mode.c_str());
	}
	else if (eAct == MM::AfterSet)
	{
		LEDOnOff(0);

		pProp->Get(answer);
		if (answer == "Constant Current")
		{
			command << "m 0";
			m_mode = "Constant Current";
		}
		else if (answer == "PWM")
		{
			command << "m 1";
			m_mode = "PWM";
		}
		else
		{
			command << "m 2";
			m_mode = "External Control";
		}

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
int DC2xxx::OnStatus(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_OK;
	std::ostringstream command;
	std::string answer;
	int tmp = 0;

	if (eAct == MM::BeforeGet)
	{
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

		// clear string
		m_status.clear();

		if (tmp & 0x02)	m_status += "No LED ";
		if (tmp & 0x08)	m_status += "LED open ";
		if (tmp & 0x20)	m_status += "Limit";
		if (!tmp)			m_status = "No Fault";

		pProp->Set(m_status.c_str());
	}

	return ret;
}


/*
int DC2xxx::OnDisplayBrightness(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_OK;
	std::string answer;
	std::ostringstream command;

	if (eAct == MM::BeforeGet)
	{
		command << "b?";
		ret = SendSerialCommand(m_port.c_str(), command.str().c_str(), "\r\n");
		if (ret != DEVICE_OK) return ret;
		ret = GetSerialAnswer(m_port.c_str(), "\r\n", answer);
		if (ret != DEVICE_OK) return ret;
		// error handling
		getLastError(&ret);

		std::istringstream iss(answer);
		iss >> m_displayBrightness;
		pProp->Set(m_displayBrightness);
	}
	else if (eAct == MM::AfterSet)
	{
		// get property
		pProp->Get(m_displayBrightness);
		// prepare command
		command << "b " << (int)m_displayBrightness;
		// send command
		ret = SendSerialCommand(m_port.c_str(), command.str().c_str(), "\r\n");
		if (ret != DEVICE_OK) return ret;
		// error handling
		getLastError(&ret);
	}

	return ret;
}
*/

/*---------------------------------------------------------------------------
 This function clears the dynamic error list.
---------------------------------------------------------------------------*/
bool DC2xxx::dynErrlist_free(void)
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
bool DC2xxx::dynErrlist_lookup(int err, std::string* descr)
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
bool DC2xxx::dynErrlist_add(int err, std::string descr)
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
 This function requests the last error from the DC2010.
---------------------------------------------------------------------------*/
bool DC2xxx::getLastError(int* err)
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
 This function creates the static read only properties.
---------------------------------------------------------------------------*/
int DC2xxx::CreateStaticReadOnlyProperties(void)
{
	int nRet = DEVICE_OK;
	std::ostringstream cmd1;
	std::ostringstream cmd2;
	std::ostringstream cmd3;
	std::ostringstream cmd4;
	std::ostringstream cmd5;

	// wavelength information
	cmd1 << "wl?";
	nRet = SendSerialCommand(m_port.c_str(), cmd1.str().c_str(), "\r\n");
	if (nRet != DEVICE_OK) return nRet;
	nRet = GetSerialAnswer(m_port.c_str(), "\r\n", m_wavelength);
	if (nRet != DEVICE_OK) return nRet;
	getLastError(&nRet);
	if (nRet != DEVICE_OK) return nRet;
	nRet = CreateProperty("LED Wavelength", m_wavelength.c_str(), MM::String, true);
	if (DEVICE_OK != nRet)	return nRet;

	// serial number information
	cmd2 << "s?";
	nRet = SendSerialCommand(m_port.c_str(), cmd2.str().c_str(), "\r\n");
	if (nRet != DEVICE_OK) return nRet;
	nRet = GetSerialAnswer(m_port.c_str(), "\r\n", m_serialNumber);
	if (nRet != DEVICE_OK) return nRet;
	getLastError(&nRet);
	if (nRet != DEVICE_OK) return nRet;
	nRet = CreateProperty("Serial Number", m_serialNumber.c_str(), MM::String, true);
	if (DEVICE_OK != nRet)	return nRet;

	// firmware version information
	cmd3 << "v?";
	nRet = SendSerialCommand(m_port.c_str(), cmd3.str().c_str(), "\r\n");
	if (nRet != DEVICE_OK) return nRet;
	nRet = GetSerialAnswer(m_port.c_str(), "\r\n", m_firmwareRev);
	if (nRet != DEVICE_OK) return nRet;
	getLastError(&nRet);
	if (nRet != DEVICE_OK) return nRet;
	nRet = CreateProperty("Firmware Revision", m_firmwareRev.c_str(), MM::String, true);
	if (DEVICE_OK != nRet)	return nRet;

	// head serial number information
	cmd4 << "hs?";
	nRet = SendSerialCommand(m_port.c_str(), cmd4.str().c_str(), "\r\n");
	if (nRet != DEVICE_OK) return nRet;
	nRet = GetSerialAnswer(m_port.c_str(), "\r\n", m_headSerialNo);
	if (nRet != DEVICE_OK) return nRet;
	getLastError(&nRet);
	if (nRet != DEVICE_OK) return nRet;
	nRet = CreateProperty("LED Serial Number", m_headSerialNo.c_str(), MM::String, true);
	if (DEVICE_OK != nRet)	return nRet;

	// forward bias information
	cmd5 << "fb?";
	nRet = SendSerialCommand(m_port.c_str(), cmd5.str().c_str(), "\r\n");
	if (nRet != DEVICE_OK) return nRet;
	nRet = GetSerialAnswer(m_port.c_str(), "\r\n", m_forwardBias);
	if (nRet != DEVICE_OK) return nRet;
	getLastError(&nRet);
	if (nRet != DEVICE_OK) return nRet;
	nRet = CreateProperty("LED Forward Bias", m_forwardBias.c_str(), MM::String, true);
	if (DEVICE_OK != nRet)	return nRet;

	return nRet;
}


/*---------------------------------------------------------------------------
 This function checks if the device is valid.
---------------------------------------------------------------------------*/
int DC2xxx::ValidateDevice(void)
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

	if(( (devName.find("DC2010")) == std::string::npos ) && (( (devName.find("DC2100")) == std::string::npos ) ) )	nRet = ERR_INVALID_DEVICE;

	return nRet;
}

