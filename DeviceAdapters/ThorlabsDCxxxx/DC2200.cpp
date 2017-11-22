#include "DC2200.h"
#include "include/TLDC2200.h"

const char* const g_Keyword_Device_Selection = "Device Selection";

/****************************************************************************

 class: 		DC2200
 description:	The class DC2200 is derived from a shutter base interface and
				can be used for DC2200 devices.

****************************************************************************/
/*---------------------------------------------------------------------------
 Default constructor.
---------------------------------------------------------------------------*/
DC2200::DC2200(const char* deviceName) :
	m_devName(deviceName),
	m_name("Undefined"),
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
	m_handle(VI_NULL)
{
	InitializeDefaultErrorMessages();
	SetErrorText(ERR_PORT_CHANGE_FORBIDDEN, "You can't change the port after device has been initialized.");
	SetErrorText(ERR_INVALID_DEVICE, "The selected plugin does not fit for the device.");

	// Name
	CreateProperty(MM::g_Keyword_Name, deviceName, MM::String, true);

	// Description
	CreateProperty(MM::g_Keyword_Description, "Thorlabs DC2200", MM::String, true);

	// Device Selection
	CPropertyAction* pAct = new CPropertyAction (this, &DC2200::OnPort);
	CreateProperty(g_Keyword_Device_Selection, "<Enter Serial Number>", MM::String, false, pAct, true);
}

/*---------------------------------------------------------------------------
 Destructor.
---------------------------------------------------------------------------*/
DC2200::~DC2200()
{
	Shutdown();
}

/*---------------------------------------------------------------------------
 This function returns the device name ("Thorlabs DC2200").
---------------------------------------------------------------------------*/
void DC2200::GetName(char* Name) const
{
	CDeviceUtils::CopyLimitedString(Name, m_devName);
}

/*---------------------------------------------------------------------------
 This function initialize a DC2xxx device and creates the actions.
---------------------------------------------------------------------------*/
int DC2200::Initialize()
{
	// validate
	int nRet = ValidateDevice();
	if (DEVICE_OK != nRet)	return nRet;

	// Initialize the maximum current action
	CPropertyAction* pAct = new CPropertyAction (this, &DC2200::OnMaximumCurrent);
	nRet = CreateProperty("Maximum Current", "2000", MM::Integer, true, pAct);
	if (DEVICE_OK != nRet)	return nRet;

	// Initialize the limit current action
	pAct = new CPropertyAction (this, &DC2200::OnLimitCurrent);
	nRet = CreateProperty("Limit Current", "2000", MM::Integer, false, pAct);
	SetPropertyLimits("Limit Current", 0, 2000);
	if (DEVICE_OK != nRet)	return nRet;

	// Initialize the constant current action
	pAct = new CPropertyAction (this, &DC2200::OnConstantCurrent);
	nRet = CreateProperty("Constant Current", "0", MM::Integer, false, pAct);
	SetPropertyLimits("Constant Current", 0, 2000);
	if (DEVICE_OK != nRet)	return nRet;

	// Initialize the PWM current action
	pAct = new CPropertyAction (this, &DC2200::OnPWMCurrent);
	nRet = CreateProperty("PWM Current", "0", MM::Integer, false, pAct);
	SetPropertyLimits("PWM Current", 0, 2000);
	if (DEVICE_OK != nRet)	return nRet;

	// Initialize the PWM frequency action
	pAct = new CPropertyAction (this, &DC2200::OnPWMFrequency);
	nRet = CreateProperty("PWM Frequency", "1", MM::Integer, false, pAct);
	SetPropertyLimits("PWM Frequency", 1, 10000);
	if (DEVICE_OK != nRet)	return nRet;

	// Initialize the PWM duty cycle action
	pAct = new CPropertyAction (this, &DC2200::OnPWMDutyCycle);
	nRet = CreateProperty("PWM Duty Cycle", "50", MM::Integer, false, pAct);
	SetPropertyLimits("PWM Duty Cycle", 1, 100);
	if (DEVICE_OK != nRet)	return nRet;

	// Initialize the PWM counts action
	pAct = new CPropertyAction (this, &DC2200::OnPWMCounts);
	nRet = CreateProperty("PWM Counts", "0", MM::Integer, false, pAct);
	SetPropertyLimits("PWM Counts", 0, 100);
	if (DEVICE_OK != nRet)	return nRet;

	// Initialize the mode selection action
	pAct = new CPropertyAction (this, &DC2200::OnOperationMode);
	nRet = CreateProperty("Operation Mode", "Constant Current", MM::String, false, pAct);
	std::vector<std::string> commands2;
	commands2.push_back("Constant Current");
	commands2.push_back("PWM");
	commands2.push_back("External Control");
	SetAllowedValues("Operation Mode", commands2);
	if (DEVICE_OK != nRet)	return nRet;

	// Initialize the status query action
	pAct = new CPropertyAction (this, &DC2200::OnStatus);
	nRet = CreateProperty("Status", "No Fault", MM::String, true, pAct);
	if (DEVICE_OK != nRet)	return nRet;

	CreateStaticReadOnlyProperties();

	// for safety
	LEDOnOff(false);

	// init message
	log << "DC2200 - initializied " << "S/N: " << m_serialNumber << " Rev: " << m_firmwareRev;
	LogMessage(log.str().c_str());

	return DEVICE_OK;
}

/*---------------------------------------------------------------------------
 This function checks if the device is valid.
---------------------------------------------------------------------------*/
int DC2200::ValidateDevice(void)
{
	int nRet = DEVICE_OK;
	ViUInt32	devCnt;
	ViChar		resName[256];
	ViReal32	limit;
	ViReal64	minimumVoltage;

	if(m_handle)
	{
		// check if the device is still available
		return TLDC2200_getLedOnOff(m_handle, VI_NULL);
	}

	// get the list of available devices
	nRet = TLDC2200_get_device_count(VI_NULL, &devCnt);
	if (nRet ==  0xBFFF0011 || devCnt == 0)
		nRet = ERR_NO_DEVICE_CONNECTED;

	if (VI_SUCCESS != nRet)
		return nRet;

	// initialize the first device
	nRet = TLDC2200_get_device_info(VI_NULL, 0, VI_NULL, VI_NULL, VI_NULL, VI_NULL, resName);
	if (VI_SUCCESS != nRet)
		return nRet;

	nRet = TLDC2200_init(resName, VI_TRUE, VI_TRUE, (ViPSession)&m_handle);
	if (VI_SUCCESS != nRet)
		return nRet;

	// get some device parameter
	nRet = TLDC2200_get_output_terminal(m_handle,  &m_terminal);
	if (VI_SUCCESS != nRet)
		return nRet;

	nRet = TLDC2200_getLimitCurrent(m_handle,  &limit);
	if (VI_SUCCESS != nRet)
		return nRet;

	nRet = TLDC2200_get_limit_voltage_range(m_handle,  m_terminal, limit, &minimumVoltage, &m_maximumVoltage);
	if (VI_SUCCESS != nRet)
		return nRet;

	return nRet;
}

/*---------------------------------------------------------------------------
 This function sets the LED output to off in case the DC2xxx was initialized.
---------------------------------------------------------------------------*/
int DC2200::Shutdown()
{
   if (m_handle)
   {
		LEDOnOff(false);
		TLDC2200_close(m_handle);
		m_handle = VI_NULL;
   }

   return DEVICE_OK;
}

/*---------------------------------------------------------------------------
 This function returns true in case device is busy.
---------------------------------------------------------------------------*/
bool DC2200::Busy()
{
	return false;
}

/*---------------------------------------------------------------------------
 This function sets the LED output.
---------------------------------------------------------------------------*/
int DC2200::SetOpen(bool open)
{
	int val = 0;

	(open) ? val = 1 : val = 0;
	return LEDOnOff(val);
}

/*---------------------------------------------------------------------------
 This function returns the LED output.
---------------------------------------------------------------------------*/
int DC2200::GetOpen(bool &open)
{	
	int nRet = DEVICE_OK;
	ViBoolean onOff;
	m_LEDOn = "Undefined";

	nRet = TLDC2200_getLedOnOff(m_handle, &onOff);
	if(VI_SUCCESS != nRet)
		return nRet;

	m_LEDOn = onOff == VI_TRUE ? "On" : "Off";
	open = (onOff == VI_TRUE);

	return nRet;
}

/*---------------------------------------------------------------------------
 This function sets the LED output on or off.
---------------------------------------------------------------------------*/
int DC2200::LEDOnOff(int onoff)
{
	int nRet = DEVICE_OK;
	nRet = TLDC2200_setLedOnOff(m_handle, (ViBoolean)onoff);
	if (VI_SUCCESS != nRet)
		return nRet;

	if (onoff == 0)
	{
		 m_LEDOn = "Off";
	}
	else
	{
		 m_LEDOn = "On";
	}

	return nRet;
}

/*---------------------------------------------------------------------------
 This function does nothing but is recommended by the shutter API.
---------------------------------------------------------------------------*/
int DC2200::Fire(double)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////
/*---------------------------------------------------------------------------
 This function reacts on port changes.
---------------------------------------------------------------------------*/
int DC2200::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		pProp->Set(m_serialNumber.c_str());
	}
	else if (eAct == MM::AfterSet)
	{
		if (m_handle)
		{
			// revert
			pProp->Set(m_serialNumber.c_str());
			return ERR_PORT_CHANGE_FORBIDDEN;
		}

		pProp->Get(m_serialNumber);
	}

	return DEVICE_OK;
}

/*---------------------------------------------------------------------------
 This function is the callback function for the "Limit Current" property.
---------------------------------------------------------------------------*/
int DC2200::OnLimitCurrent(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int nRet = DEVICE_OK;
	ViReal32 currentLimit;

	if (eAct == MM::BeforeGet)
	{
		nRet = TLDC2200_getLimitCurrent(m_handle, &currentLimit);

		if(VI_SUCCESS != nRet)
			return nRet;

		m_limitCurrent = (long)(currentLimit * 1000.0f);
		pProp->Set(m_limitCurrent);
	}
	else if (eAct == MM::AfterSet)
	{
		// get property
		pProp->Get(m_limitCurrent);
		currentLimit = ((ViReal32)m_limitCurrent) / 1000.0f;

		nRet = TLDC2200_setLimitCurrent(m_handle, currentLimit);
		if(VI_SUCCESS != nRet)
			return nRet;
	}

	// set the limits
	SetPropertyLimits("Constant Current", 0, m_limitCurrent);
	SetPropertyLimits("PWM Current", 0, m_limitCurrent);

	return nRet;
}

/*---------------------------------------------------------------------------
 This function is the callback function for the "Maximum Current" query.
---------------------------------------------------------------------------*/
int DC2200::OnMaximumCurrent(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int nRet = DEVICE_OK;
	ViReal64 minimumCurrent;
	ViReal64 maximumCurrent;

	if (eAct == MM::BeforeGet)
	{
		nRet = TLDC2200_get_limit_current_range(m_handle, m_terminal, m_maximumVoltage, &minimumCurrent, &maximumCurrent);
		if(VI_SUCCESS != nRet)
			return nRet;

		m_maximumCurrent = (long)(maximumCurrent * 1000.0f);
		pProp->Set(m_maximumCurrent);
	}

	// set the limits
	SetPropertyLimits("Limit Current", 0, m_maximumCurrent);

	return nRet;
}

/*---------------------------------------------------------------------------
 This function is the callback function for the "Constant Current" property.
---------------------------------------------------------------------------*/
int DC2200::OnConstantCurrent(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int nRet = DEVICE_OK;
	ViReal32 current;

	if (eAct == MM::BeforeGet)
	{
		nRet = TLDC2200_getConstCurrent(m_handle, &current);
		if(VI_SUCCESS != nRet)
			return nRet;

		m_constCurrent = (long)(current * 1000.0f);
		pProp->Set(m_constCurrent);
	}
	else if (eAct == MM::AfterSet)
	{
		// get property
		pProp->Get(m_constCurrent);
		current = ((ViReal32)m_constCurrent) / 1000.0f;

		nRet = TLDC2200_setConstCurrent(m_handle, current);
		if(VI_SUCCESS != nRet)
			return nRet;
	}

	return nRet;
}

/*---------------------------------------------------------------------------
 This function is the callback function for the "PWM Current" property.
---------------------------------------------------------------------------*/
int DC2200::OnPWMCurrent(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int nRet = DEVICE_OK;
	ViReal32 current;

	if (eAct == MM::BeforeGet)
	{
		nRet = TLDC2200_getPWMCurrent(m_handle, &current);
		if(VI_SUCCESS != nRet)
			return nRet;

		m_pwmCurrent = (long)(current * 1000.0f);
		pProp->Set(m_pwmCurrent);
	}
	else if (eAct == MM::AfterSet)
	{
		// get property
		pProp->Get(m_pwmCurrent);
		current = ((ViReal32)m_pwmCurrent) / 1000.0f;

		nRet = TLDC2200_setPWMCurrent(m_handle, current);
		if(VI_SUCCESS != nRet)
			return nRet;
	}

	return nRet;
}

/*---------------------------------------------------------------------------
 This function is the callback function for the "PWM Frequency" property.
---------------------------------------------------------------------------*/
int DC2200::OnPWMFrequency(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int nRet = DEVICE_OK;
	ViReal64 frequency;

	if (eAct == MM::BeforeGet)
	{
		nRet = TLDC2200_getPWMFrequency(m_handle, &frequency);
		if(VI_SUCCESS != nRet)
			return nRet;

		m_pwmFrequency = (long)frequency;
		pProp->Set(m_pwmFrequency);
	}
	else if (eAct == MM::AfterSet)
	{
		// get property
		pProp->Get(m_pwmFrequency);
		frequency = (ViReal64)m_pwmFrequency;

		nRet = TLDC2200_setPWMFrequency(m_handle, frequency);
		if(VI_SUCCESS != nRet)
			return nRet;
	}

	return nRet;
}

/*---------------------------------------------------------------------------
 This function is the callback function for the "PWM Duty Cycle" property.
---------------------------------------------------------------------------*/
int DC2200::OnPWMDutyCycle(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int nRet = DEVICE_OK;
	ViInt32 dutyCycle;

	if (eAct == MM::BeforeGet)
	{
		nRet = TLDC2200_getPWMDutyCycle(m_handle, &dutyCycle);
		if(VI_SUCCESS != nRet)
			return nRet;

		m_pwmDutyCycle = (long)dutyCycle;
		pProp->Set(m_pwmDutyCycle);
	}
	else if (eAct == MM::AfterSet)
	{
		// get property
		pProp->Get(m_pwmDutyCycle);

		dutyCycle = (ViInt32)m_pwmDutyCycle;

		nRet = TLDC2200_setPWMDutyCycle(m_handle, dutyCycle);
		if(VI_SUCCESS != nRet)
			return nRet;
	}

	return nRet;
}


/*---------------------------------------------------------------------------
 This function is the callback function for the "PWM Counts" property.
---------------------------------------------------------------------------*/
int DC2200::OnPWMCounts(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int nRet = DEVICE_OK;
	ViInt32 pwmCnts;
	
	if (eAct == MM::BeforeGet)
	{
		nRet = TLDC2200_getPWMCounts(m_handle, &pwmCnts);
		if(VI_SUCCESS != nRet)
			return nRet;

		m_pwmCounts = (long)pwmCnts;
		pProp->Set(m_pwmCounts);
	}
	else if (eAct == MM::AfterSet)
	{
		// get property
		pProp->Get(m_pwmCounts);

		pwmCnts = (ViInt32)m_pwmCounts;

		nRet = TLDC2200_setPWMCounts(m_handle, pwmCnts);
		if(VI_SUCCESS != nRet)
			return nRet;
	}

	return nRet;
}

/*---------------------------------------------------------------------------
 This function is the callback function for the "operation mode" property.
---------------------------------------------------------------------------*/
int DC2200::OnOperationMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int nRet = DEVICE_OK;
	ViInt32 operationMode;
	std::string opModeString;	

	if (eAct == MM::BeforeGet)
	{
		nRet = TLDC2200_getOperationMode(m_handle, &operationMode);
		if(VI_SUCCESS != nRet)
			return nRet;

		switch(operationMode)
		{
		case MODUS_CONST_CURRENT:
			m_mode = "Constant Current";
			break;
		case MODUS_PWM:
			m_mode = "PWM";
			break;
		case MODUS_EXTERNAL_CONTROL:
			m_mode = "External Control";
			break;
		default:
			m_mode = "Undefined";
			break;
		}			

		pProp->Set(m_mode.c_str());
	}
	else if (eAct == MM::AfterSet)
	{
		LEDOnOff(0);

		pProp->Get(opModeString);
		if (opModeString == "Constant Current")
		{
			operationMode = MODUS_CONST_CURRENT;
			m_mode = "Constant Current";
		}
		else if (opModeString == "PWM")
		{
			operationMode =  MODUS_PWM;
			m_mode = "PWM";
		}
		else
		{
			operationMode =  MODUS_EXTERNAL_CONTROL;
			m_mode = "External Control";
		}

		nRet = TLDC2200_setOperationMode(m_handle, operationMode);
		if(VI_SUCCESS != nRet)
			return nRet;
	}

	return nRet;
}


/*---------------------------------------------------------------------------
 This function is the callback function for the "status" query.
---------------------------------------------------------------------------*/
int DC2200::OnStatus(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int nRet = DEVICE_OK;
	ViInt32 statusRegister;

	if (eAct == MM::BeforeGet)
	{
		nRet = TLDC2200_getStatusRegister(m_handle, &statusRegister);
		if(VI_SUCCESS != nRet)
			return nRet;

		// clear string
		m_status.clear();

		if (statusRegister & STAT_NO_LED1)		m_status += "No LED ";
		if (statusRegister & STAT_LED_OPEN1)	m_status += "LED open ";
		if (statusRegister & STAT_LED_LIMIT1)	m_status += "Limit";
		if (!statusRegister)					m_status = "No Fault";

		pProp->Set(m_status.c_str());
	}

	return nRet;
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
bool DC2200::dynErrlist_free(void)
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
bool DC2200::dynErrlist_lookup(int err, std::string* descr)
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
bool DC2200::dynErrlist_add(int err, std::string descr)
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
 This function requests the last error from the DC2200.
---------------------------------------------------------------------------*/
bool DC2200::getLastError(int* err)
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
	//SendSerialCommand(m_port.c_str(), errRequest.str().c_str(), "\r\n");
	//// receive the answer
	//GetSerialAnswer(m_port.c_str(), "\r\n", answer);
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
int DC2200::CreateStaticReadOnlyProperties(void)
{
	int nRet = DEVICE_OK;
	ViReal32 wavelength;
	ViStatus res;
	ViChar serialNumber[256];
	ViChar instrRevision[256];
	ViChar headSerialNumber[256];
	ViReal32 forwardBias;

	// wavelength information
	res = TLDC2200_getWavelength(m_handle, &wavelength);
	if (VI_SUCCESS != res)
		return res;

	m_wavelength = wavelength;

	nRet = CreateProperty("LED Wavelength", m_wavelength.c_str(), MM::String, true);
	if (DEVICE_OK != nRet)	
		return nRet;

	// serial number information
	nRet = TLDC2200_get_device_info(VI_NULL, 0, VI_NULL, VI_NULL, serialNumber, VI_NULL, VI_NULL);
	if (VI_SUCCESS != nRet)
		return nRet;

	m_serialNumber = serialNumber;

	nRet = CreateProperty("Serial Number", serialNumber, MM::String, true);
	if (DEVICE_OK != nRet)	
		return nRet;

	// firmware version information
	nRet = TLDC2200_revision_query(m_handle,  VI_NULL, instrRevision);
	if (VI_SUCCESS != nRet)
		return nRet;

	m_firmwareRev = instrRevision;

	nRet = CreateProperty("Firmware Revision", instrRevision, MM::String, true);
	if (DEVICE_OK != nRet)	
		return nRet;
	
	// head serial number information
	nRet = TLDC2200_get_head_info(m_handle, headSerialNumber, VI_NULL, VI_NULL);
	if (VI_SUCCESS != nRet)
		return nRet;

	m_headSerialNo = headSerialNumber;

	nRet = CreateProperty("LED Serial Number", headSerialNumber, MM::String, true);
	if (DEVICE_OK != nRet)	
		return nRet;

	// forward bias information
	nRet = TLDC2200_getForwardBias(m_handle, &forwardBias);
	if (VI_SUCCESS != nRet)
		return nRet;

	m_forwardBias = forwardBias;

	nRet = CreateProperty("LED Forward Bias", m_forwardBias.c_str(), MM::String, true);
	
	return nRet;
}




