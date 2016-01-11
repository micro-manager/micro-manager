#include "DC2200.h"

// Base driver
#include "TLDC2200.h"

const char* g_DeviceDC2200Name = "Thorlabs DC2200";

template <typename T> std::string tostr(const T& t) { 
  std::ostringstream os; 
   os<<t; 
   return os.str(); 
} 

/****************************************************************************

 class: 		DC2200
 description:	The class DC2200 is derived from a shutter base interface and
				can be used for DC2200 devices.

****************************************************************************/
/*---------------------------------------------------------------------------
 Default constructor.
---------------------------------------------------------------------------*/
DC2200::DC2200() :
	m_handle(VI_NULL),
	m_name("Undefined"),
	m_serialNumber("n/a"),
	m_LEDOn("On"),
	m_mode("Constant Current"),
	m_status("No Fault"),
	
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
	m_terminal(0)
{
	InitializeDefaultErrorMessages();
	SetErrorText(ERR_PORT_CHANGE_FORBIDDEN, "You can't change the port after device has been initialized.");
	SetErrorText(ERR_INVALID_DEVICE, "The selected plugin does not fit for the device.");

	// Name
	CreateProperty(MM::g_Keyword_Name, g_DeviceDC2200Name, MM::String, true);

	// Description
	CreateProperty(MM::g_Keyword_Description, "Thorlabs DC2200", MM::String, true);

	// Port
	CPropertyAction* pAct = new CPropertyAction (this, &DC2200::OnDeviceChanged);
	//CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);
	CreateProperty("Serial Number", "Undefined", MM::String, false, pAct, true);

	// get the serial number of the first device
	GetFirstDevice();
}

/*---------------------------------------------------------------------------
 Destructor.
---------------------------------------------------------------------------*/
DC2200::~DC2200()
{
	Shutdown();
}

/*---------------------------------------------------------------------------
 This functions return the device name ("DC2200").
---------------------------------------------------------------------------*/
void DC2200::GetName(char* Name) const
{
	CDeviceUtils::CopyLimitedString(Name, g_DeviceDC2200Name);
}

const char* DC2200::DeviceName()
{
	return g_DeviceDC2200Name;
}

/*---------------------------------------------------------------------------
 This function initialize a DC2200 device and creates the actions.
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
	commands2.push_back("Brightness");
	commands2.push_back("PWM");
	commands2.push_back("Pulse");
	commands2.push_back("Internal Modulation");
	commands2.push_back("External Modulation");
	commands2.push_back("TTL");
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
	log << "DC2200 - initializied " << "S/N: " << m_serialNumber << " Rev: " << m_firmwareRev << "\r\n";
	LogMessage(log.str().c_str());

	return DEVICE_OK;
}

/*---------------------------------------------------------------------------
 This function sets the LED output to off in case the DC2xxx was initialized.
---------------------------------------------------------------------------*/
int DC2200::Shutdown()
{
   if (VI_NULL != m_handle )
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
	int ret = DEVICE_OK;
	ViBoolean state;

	ret = TLDC2200_getLedOnOff(m_handle, &state);
	if(VI_SUCCESS != ret)
	{
		getErrorDescription(&ret);
		return ret;
	}

	if (VI_OFF == state)
	{
		m_LEDOn = "Off";
		open = false;
	}
	else if (VI_ON == state)
	{
		m_LEDOn = "On";
		open = true;
	}

	return ret;
}

/*---------------------------------------------------------------------------
 This function does nothing but is recommended by the shutter API.
---------------------------------------------------------------------------*/
int DC2200::Fire(double)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

/*---------------------------------------------------------------------------
 This function sets the LED output on or off.
---------------------------------------------------------------------------*/
int DC2200::LEDOnOff(int onoff)
{
	int ret = DEVICE_OK;
	ViBoolean state;

	if (0 == onoff)
	{
		 state = VI_OFF;
		 m_LEDOn = "Off";
	}
	else
	{
		 state = VI_ON;
		 m_LEDOn = "On";
	}

	ret = TLDC2200_setLedOnOff(m_handle, state);
	if(VI_SUCCESS != ret)
	{
		getErrorDescription(&ret);
	}

	return ret;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////
/*---------------------------------------------------------------------------
 This function reacts on port changes.
---------------------------------------------------------------------------*/
int DC2200::OnDeviceChanged(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		pProp->Set(m_serialNumber.c_str());
	}
	else if (eAct == MM::AfterSet)
	{
		if (VI_NULL != m_handle )
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
	int ret = DEVICE_OK;
	ViReal32 limit;
	std::string answer;
	std::ostringstream command;

	if (eAct == MM::BeforeGet)
	{
		ret = TLDC2200_getLimitCurrent(m_handle, &limit);
		if (VI_SUCCESS != ret)
		{
			getErrorDescription(&ret);
			return ret;
		}

		m_limitCurrent = (long)limit;
		pProp->Set(m_limitCurrent);
	}
	else if (eAct == MM::AfterSet)
	{
		// get property
		pProp->Get(m_limitCurrent);

		limit = (ViReal32)m_limitCurrent;
		ret = TLDC2200_setLimitCurrent(m_handle, limit);
		if (VI_SUCCESS != ret)
		{
			getErrorDescription(&ret);
			return ret;
		}
	}

	// set the limits
	SetPropertyLimits("Constant Current", 0, m_limitCurrent);
	SetPropertyLimits("PWM Current", 0, m_limitCurrent);

	return ret;
}

/*---------------------------------------------------------------------------
 This function is the callback function for the "Maximum Current" query.
---------------------------------------------------------------------------*/
int DC2200::OnMaximumCurrent(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_OK;
	ViReal64 voltage;
	ViReal64 currentMaximum;
	ViReal64 currentMinimum;

	if (eAct == MM::BeforeGet)
	{
		ret = TLDC2200_get_limit_voltage(m_handle, &voltage);
		if (VI_SUCCESS != ret)
		{
			getErrorDescription(&ret);
			return ret;
		}

		ret = TLDC2200_get_limit_current_range(m_handle, m_terminal, voltage, &currentMinimum, &currentMaximum);
		if (VI_SUCCESS != ret)
		{
			getErrorDescription(&ret);
			return ret;
		}

		m_maximumCurrent = (long)currentMaximum;
		pProp->Set(m_maximumCurrent);
	}

	// set the limits
	SetPropertyLimits("Limit Current", 0, m_maximumCurrent);

	return ret;
}

/*---------------------------------------------------------------------------
 This function is the callback function for the "Constant Current" property.
---------------------------------------------------------------------------*/
int DC2200::OnConstantCurrent(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_OK;
	ViReal32 current;

	if (eAct == MM::BeforeGet)
	{
		ret = TLDC2200_getConstCurrent(m_handle, &current);
		if (VI_SUCCESS != ret)
		{
			getErrorDescription(&ret);
			return ret;
		} 

		m_constCurrent = (long)current; 
		pProp->Set(m_constCurrent);
	}
	else if (eAct == MM::AfterSet)
	{
		// get property
		pProp->Get(m_constCurrent);

		current = (ViReal32) m_constCurrent;
		ret = TLDC2200_setConstCurrent(m_handle, current);
		if (VI_SUCCESS != ret)
		{
			getErrorDescription(&ret);
			return ret;
		}
	}

	return ret;
}

/*---------------------------------------------------------------------------
 This function is the callback function for the "PWM Current" property.
---------------------------------------------------------------------------*/
int DC2200::OnPWMCurrent(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_OK;
	ViReal32 current;

	if (eAct == MM::BeforeGet)
	{
		ret = TLDC2200_getPWMCurrent(m_handle, &current);
		if (VI_SUCCESS != ret)
		{
			getErrorDescription(&ret);
			return ret;
		}

		m_pwmCurrent = (long)current;
		pProp->Set(m_pwmCurrent);
	}
	else if (eAct == MM::AfterSet)
	{
		// get property
		pProp->Get(m_pwmCurrent);

		current = (ViReal32) m_pwmCurrent;
		ret = TLDC2200_setPWMCurrent(m_handle, current);
		if (VI_SUCCESS != ret)
		{
			getErrorDescription(&ret);
			return ret;
		}
	}

	return ret;
}

/*---------------------------------------------------------------------------
 This function is the callback function for the "PWM Frequency" property.
---------------------------------------------------------------------------*/
int DC2200::OnPWMFrequency(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_OK;
	ViReal64 freq;

	if (eAct == MM::BeforeGet)
	{
		ret = TLDC2200_getPWMFrequency(m_handle, &freq);
		if (VI_SUCCESS != ret)
		{
			getErrorDescription(&ret);
			return ret;
		}

		m_pwmFrequency = (long)freq;
		pProp->Set(m_pwmFrequency);
	}
	else if (eAct == MM::AfterSet)
	{
		// get property
		pProp->Get(m_pwmFrequency);
		
		freq = (ViReal32) m_pwmFrequency;
		ret = TLDC2200_setPWMFrequency(m_handle, freq);
		if (VI_SUCCESS != ret)
		{
			getErrorDescription(&ret);
			return ret;
		}
	}

	return ret;
}

/*---------------------------------------------------------------------------
 This function is the callback function for the "PWM Duty Cycle" property.
---------------------------------------------------------------------------*/
int DC2200::OnPWMDutyCycle(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_OK;
	ViInt32 cycle;

	if (eAct == MM::BeforeGet)
	{
		ret = TLDC2200_getPWMDutyCycle(m_handle, &cycle);
		if (VI_SUCCESS != ret)
		{
			getErrorDescription(&ret);
			return ret;
		}

		m_pwmDutyCycle = (long)cycle;
		pProp->Set(m_pwmDutyCycle);
	}
	else if (eAct == MM::AfterSet)
	{
		// get property
		pProp->Get(m_pwmDutyCycle);
		
		cycle = (ViInt32) m_pwmDutyCycle;
		ret = TLDC2200_setPWMDutyCycle(m_handle, cycle);
		if (VI_SUCCESS != ret)
		{
			getErrorDescription(&ret);
			return ret;
		}
	}

	return ret;
}

/*---------------------------------------------------------------------------
 This function is the callback function for the "PWM Counts" property.
---------------------------------------------------------------------------*/
int DC2200::OnPWMCounts(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_OK;
	ViInt32 counts;

	if (eAct == MM::BeforeGet)
	{
		ret = TLDC2200_getPWMCounts(m_handle, &counts);
		if (VI_SUCCESS != ret)
		{
			getErrorDescription(&ret);
			return ret;
		}

		m_pwmCounts = (long)counts;
		pProp->Set(m_pwmCounts);
	}
	else if (eAct == MM::AfterSet)
	{
		// get property
		pProp->Get(m_pwmCounts);
		
		counts = (ViInt32) m_pwmCounts;
		ret = TLDC2200_setPWMCounts(m_handle, counts);
		if (VI_SUCCESS != ret)
		{
			getErrorDescription(&ret);
			return ret;
		}
	}

	return ret;
}

/*---------------------------------------------------------------------------
 This function is the callback function for the "operation mode" property.
---------------------------------------------------------------------------*/
int DC2200::OnOperationMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_OK;
	ViInt32 operationMode;
	m_mode = "Undefined";

	if (eAct == MM::BeforeGet)
	{
		ret = TLDC2200_getOperationMode(m_handle, &operationMode);
		if (VI_SUCCESS != ret)
		{
			getErrorDescription(&ret);
			return ret;
		}

		switch(operationMode)
		{
		case TLDC2200_LD_OPMODE_CC:
			m_mode = "Constant Current";
			break;
		case TLDC2200_LD_OPMODE_CB:
			m_mode = "Brightness";
			break;
		case TLDC2200_LD_OPMODE_PWM:
			m_mode = "PWM";
			break;
		case TLDC2200_LD_OPMODE_PULS:
			m_mode = "Pulse";
			break;
		case TLDC2200_LD_OPMODE_IMOD:
			m_mode = "Internal Modulation";
			break;
		case TLDC2200_LD_OPMODE_EMOD:
			m_mode = "External Modulation";
			break;
		case TLDC2200_LD_OPMODE_TTL:
			m_mode = "TTL";
			break;
		}

		pProp->Set(m_mode.c_str());
	}
	else if (eAct == MM::AfterSet)
	{
		LEDOnOff(0);

		pProp->Get(m_mode);
		if (m_mode == "Constant Current")
		{
			operationMode = TLDC2200_LD_OPMODE_CC;
		}
		else if (m_mode == "Brightness")
		{
			operationMode = TLDC2200_LD_OPMODE_CB;
		}
		else if (m_mode == "PWM")
		{
			operationMode = TLDC2200_LD_OPMODE_PWM;
		}
		else if (m_mode == "Pulse")
		{
			operationMode = TLDC2200_LD_OPMODE_PULS;
		}
		else if (m_mode == "Internal Modulation")
		{
			operationMode = TLDC2200_LD_OPMODE_IMOD;
		}
		else if (m_mode == "External Modulation")
		{
			operationMode = TLDC2200_LD_OPMODE_EMOD;
		}
		else
		{
			operationMode = TLDC2200_LD_OPMODE_TTL;
		}

		ret = TLDC2200_setOperationMode(m_handle, operationMode);
		if (VI_SUCCESS != ret)
		{
			getErrorDescription(&ret);
			return ret;
		}
	}

	return ret;
}

/*---------------------------------------------------------------------------
 This function is the callback function for the "status" query.
---------------------------------------------------------------------------*/
int DC2200::OnStatus(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_OK;
	ViInt32 statusRegister;

	if (eAct == MM::BeforeGet)
	{
		ret = TLDC2200_getStatusRegister(m_handle, &statusRegister);
		if (VI_SUCCESS != ret)
		{
			getErrorDescription(&ret);
			return ret;
		}

		// clear string
		m_status.clear();

		if (statusRegister & 0x02)	m_status += "No LED ";
		if (statusRegister & 0x08)	m_status += "LED open ";
		if (statusRegister & 0x20)	m_status += "Limit";
		if (!statusRegister)		m_status = "No Fault";

		pProp->Set(m_status.c_str());
	}

	return ret;
}

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
bool DC2200::getErrorDescription(int* err)
{
	std::ostringstream errRequest;
	std::string answer;
	std::string errDescr;
	ViInt32 errCode;
	int ret;
	ViChar errMsg[256];

	// preset
	ret = TLDC2200_error_query(m_handle, &errCode, errMsg);
	if(VI_SUCCESS != ret)
		return false;

	// add error to list
	dynErrlist_add(errCode + ERR_DCxxxx_OFFSET, errMsg);

	// publish the error code
	if(err) 
		*err = errCode + ERR_DCxxxx_OFFSET;

	return true;
}

/*---------------------------------------------------------------------------
 This function creates the static read only properties.
---------------------------------------------------------------------------*/
int DC2200::CreateStaticReadOnlyProperties(void)
{
	int ret = DEVICE_OK;
	ViReal32 wavelength;
	ViChar driverRev[256];
	ViChar instrRev[256];
	ViChar headSerialNumber[256];
	ViChar headName[256];
	ViInt32 headType;
	ViReal32 forwardBias;

	// wavelength information
	ret = TLDC2200_getWavelength(m_handle, &wavelength); 
	if (VI_SUCCESS != ret)
	{
		getErrorDescription(&ret);
		return ret;
	}

	m_wavelength = tostr(wavelength);
	ret = CreateProperty("LED Wavelength", m_wavelength.c_str(), MM::String, true);
	if (DEVICE_OK != ret)	
		return ret;

	// firmware version information
	ret = TLDC2200_revision_query(m_handle, driverRev, instrRev); 
	if (VI_SUCCESS != ret)
	{
		getErrorDescription(&ret);
		return ret;
	}

	m_firmwareRev = instrRev;
	ret = CreateProperty("Firmware Revision", m_firmwareRev.c_str(), MM::String, true);
	if (DEVICE_OK != ret)	
		return ret;

	// head serial number information
	ret = TLDC2200_get_head_info(m_handle, headSerialNumber, headName, &headType); 
	if (VI_SUCCESS != ret)
	{
		getErrorDescription(&ret);
		return ret;
	}

	m_headSerialNo = headSerialNumber;
	ret = CreateProperty("LED Serial Number", m_headSerialNo.c_str(), MM::String, true);
	if (DEVICE_OK != ret)	
		return ret;

	// forward bias information
	ret = TLDC2200_getForwardBias(m_handle, &forwardBias); 
	if (VI_SUCCESS != ret)
	{
		getErrorDescription(&ret);
		return ret;
	}

	m_forwardBias = tostr(forwardBias);
	ret = CreateProperty("LED Forward Bias", m_forwardBias.c_str(), MM::String, true);
	if (DEVICE_OK != ret)	
		return ret;

	// current terminal
	m_terminal = 0;

	return ret;
}

/*---------------------------------------------------------------------------
 This function checks if the device is valid.
---------------------------------------------------------------------------*/
int DC2200::ValidateDevice(void)
{ 
	ViBoolean state;
	int ret;
	
	if (VI_NULL != m_handle )
	{
		log << "Check if the device is still available"<< "\r\n";
		LogMessage(log.str().c_str());

		// check if the device is still active
		ret = TLDC2200_getLedOnOff(m_handle, &state);
		if (VI_SUCCESS != ret)
		{
			getErrorDescription(&ret);
			return ret;
		} 

		return ret;
	}

	ret = GetFirstDevice();
	if (VI_SUCCESS != ret)
	{
		getErrorDescription(&ret);
		return ret;
	}

	// initialize the device
	ret = TLDC2200_init(m_resName, VI_TRUE, VI_FALSE, &m_handle);
	if (VI_SUCCESS != ret)
	{
		getErrorDescription(&ret);
		return ret;
	}

	return ret;
}

int DC2200::GetFirstDevice(void)
{
	ViStatus ret;
	ViUInt32 devCnt;
	ViBoolean devAvailable;
	ViChar modelName[256];
	ViChar serialNumber[256];

	// get a list of connected devices
	ret = TLDC2200_get_device_count(VI_NULL, &devCnt);
	if(VI_SUCCESS != ret)
		return ret;

	// get some global device information
	ret = TLDC2200_get_device_info(VI_NULL, 0, VI_NULL, modelName, serialNumber, &devAvailable, m_resName);
	if(VI_SUCCESS != ret)
		return ret;

	m_serialNumber	= serialNumber;
	m_name			= modelName;

	return ret;
}



