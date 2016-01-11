#include "DC3100.h"

const char* g_DeviceDC3100Name = "Thorlabs DC3100";

/****************************************************************************

 class: 			DC3100
 description:	The class DC3100 is derived from a shutter base interface and
					can be used for DC3100 devices.

****************************************************************************/
/*---------------------------------------------------------------------------
 Default constructor.
---------------------------------------------------------------------------*/
DC3100::DC3100() :
	m_name("Undefined"),
   m_port("Undefined"),
   m_LEDOn("On"),
	m_mode("Constant Current"),
   m_status("No Fault"),
	m_serialNumber("n/a"),
   m_firmwareRev("n/a"),
   m_headSerialNo("n/a"),
   m_limitCurrent(0),
   m_maximumCurrent(0),
   m_maximumFrequency(0.0),
	m_constCurrent(0),
	m_moduCurrent(0),
	m_moduFrequency(10.0),
	m_moduDepth(0),
   m_initialized(false)
{
	InitializeDefaultErrorMessages();
	SetErrorText(ERR_PORT_CHANGE_FORBIDDEN, "You can't change the port after device has been initialized.");
	SetErrorText(ERR_INVALID_DEVICE, "The selected plugin does not fit for the device.");

	// Name
	CreateProperty(MM::g_Keyword_Name, g_DeviceDC3100Name, MM::String, true);

	// Description
	CreateProperty(MM::g_Keyword_Description, "Thorlabs DC3100", MM::String, true);

	// Port
	CPropertyAction* pAct = new CPropertyAction (this, &DC3100::OnPort);
	CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);
}


/*---------------------------------------------------------------------------
 Destructor.
---------------------------------------------------------------------------*/
DC3100::~DC3100()
{
	Shutdown();
}


/*---------------------------------------------------------------------------
 This function returns the device name ("DC3100").
---------------------------------------------------------------------------*/
void DC3100::GetName(char* Name) const
{
	CDeviceUtils::CopyLimitedString(Name, g_DeviceDC3100Name);
}

const char* DC3100::DeviceName()
{
	return g_DeviceDC3100Name;
}


/*---------------------------------------------------------------------------
 This function initialize a DC3100 device and creates the actions.
---------------------------------------------------------------------------*/
int DC3100::Initialize()
{
	// validate
	int nRet = ValidateDevice();
	if (DEVICE_OK != nRet)	return nRet;

	// Initialize the maximum current action
	CPropertyAction* pAct = new CPropertyAction (this, &DC3100::OnMaximumCurrent);
	nRet = CreateProperty("Maximum Current", "1000", MM::Integer, true, pAct);
	if (DEVICE_OK != nRet)	return nRet;

	// Initialize the maximum current action
	pAct = new CPropertyAction (this, &DC3100::OnMaximumFrequency);
	nRet = CreateProperty("Maximum Frequency", "1000", MM::Float, true, pAct);
	if (DEVICE_OK != nRet)	return nRet;

	// Initialize the limit current action
	pAct = new CPropertyAction (this, &DC3100::OnLimitCurrent);
	nRet = CreateProperty("Limit Current", "1000", MM::Integer, false, pAct);
	SetPropertyLimits("Limit Current", 0, 1000);
	if (DEVICE_OK != nRet)	return nRet;

	// Initialize the constant current action
	pAct = new CPropertyAction (this, &DC3100::OnConstantCurrent);
	nRet = CreateProperty("Constant Current", "0", MM::Integer, false, pAct);
	SetPropertyLimits("Constant Current", 0, 1000);
	if (DEVICE_OK != nRet)	return nRet;

	// Initialize the modulation current action
	pAct = new CPropertyAction (this, &DC3100::OnModulationCurrent);
	nRet = CreateProperty("Modulation Current", "0", MM::Integer, false, pAct);
	SetPropertyLimits("Modulation Current", 0, 1000);
	if (DEVICE_OK != nRet)	return nRet;

	// Initialize the modulation frequency action
	pAct = new CPropertyAction (this, &DC3100::OnModulationFrequency);
	nRet = CreateProperty("Modulation Frequency", "1", MM::Float, false, pAct);
	SetPropertyLimits("Modulation Frequency", 10, 100);
	if (DEVICE_OK != nRet)	return nRet;

	// Initialize the modulation depth action
	pAct = new CPropertyAction (this, &DC3100::OnModulationDepth);
	nRet = CreateProperty("Modulation Depth", "100", MM::Integer, false, pAct);
	SetPropertyLimits("Modulation Depth", 0, 100);
	if (DEVICE_OK != nRet)	return nRet;

	// Initialize the mode selection action
	pAct = new CPropertyAction (this, &DC3100::OnOperationMode);
	nRet = CreateProperty("Operation Mode", "Constant Current", MM::String, false, pAct);
	std::vector<std::string> commands2;
	commands2.push_back("Constant Current");
	commands2.push_back("Internal Modulation");
	commands2.push_back("External Control");
	SetAllowedValues("Operation Mode", commands2);
	if (DEVICE_OK != nRet)	return nRet;

	// Initialize the status query action
	pAct = new CPropertyAction (this, &DC3100::OnStatus);
	nRet = CreateProperty("Status", "No Fault", MM::String, true, pAct);
	if (DEVICE_OK != nRet)	return nRet;

	CreateStaticReadOnlyProperties();

	// for safety
	LEDOnOff(false);

	m_initialized = true;

	// init message
	std::ostringstream log;
	log << "DC3100 - initializied " << "S/N: " << m_serialNumber << " Rev: " << m_firmwareRev;
	LogMessage(log.str().c_str());

	return DEVICE_OK;
}


/*---------------------------------------------------------------------------
 This function sets the LED output to off in case the DC3100 was initialized.
---------------------------------------------------------------------------*/
int DC3100::Shutdown()
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
bool DC3100::Busy()
{
	return false;
}


/*---------------------------------------------------------------------------
 This function sets the LED output.
---------------------------------------------------------------------------*/
int DC3100::SetOpen(bool open)
{
	int val = 0;

	(open) ? val = 1 : val = 0;
   return LEDOnOff(val);
}


/*---------------------------------------------------------------------------
 This function returns the LED output.
---------------------------------------------------------------------------*/
int DC3100::GetOpen(bool &open)
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
int DC3100::Fire(double)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}


/*---------------------------------------------------------------------------
 This function sets the LED output on or off.
---------------------------------------------------------------------------*/
int DC3100::LEDOnOff(int onoff)
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
int DC3100::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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
int DC3100::OnLimitCurrent(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_OK;
	std::string answer;
	std::ostringstream command;
	float tmp = 0;

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
		iss >> tmp;
		m_limitCurrent = (int)(tmp * 1000.0);
		pProp->Set(m_limitCurrent);
	}
	else if (eAct == MM::AfterSet)
	{
		// get property
		pProp->Get(m_limitCurrent);
		// prepare command
		command << "l " << (float)m_limitCurrent/1000.0;
		// send command
		ret = SendSerialCommand(m_port.c_str(), command.str().c_str(), "\r\n");
		if (ret != DEVICE_OK) return ret;
		// error handling
		getLastError(&ret);
	}

	// set the limits
	SetPropertyLimits("Constant Current", 0, m_limitCurrent);
	SetPropertyLimits("Modulation Current", 0, m_limitCurrent);

	return ret;
}


/*---------------------------------------------------------------------------
 This function is the callback function for the "Maximum Current" query.
---------------------------------------------------------------------------*/
int DC3100::OnMaximumCurrent(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_OK;
	std::string answer;
	std::ostringstream command;
	float tmp;

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
		iss >> tmp;
		m_maximumCurrent = (int)(tmp * 1000.0);
		pProp->Set(m_maximumCurrent);
	}

	// set the limits
	SetPropertyLimits("Limit Current", 0, m_maximumCurrent);

	return ret;
}


/*---------------------------------------------------------------------------
 This function is the callback function for the "Maximum Frequency" query.
---------------------------------------------------------------------------*/
int DC3100::OnMaximumFrequency(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_OK;
	std::string answer;
	std::ostringstream command;

	if (eAct == MM::BeforeGet)
	{
		command << "mf?";
		ret = SendSerialCommand(m_port.c_str(), command.str().c_str(), "\r\n");
		if (ret != DEVICE_OK) return ret;
		ret = GetSerialAnswer(m_port.c_str(), "\r\n", answer);
		if (ret != DEVICE_OK) return ret;
		// error handling
		getLastError(&ret);

		std::istringstream iss(answer);
		iss >> m_maximumFrequency;
		pProp->Set(m_maximumFrequency);
	}

	// set the limits
	SetPropertyLimits("Modulation Frequency", 0, m_maximumFrequency);

	return ret;
}


/*---------------------------------------------------------------------------
 This function is the callback function for the "Constant Current" property.
---------------------------------------------------------------------------*/
int DC3100::OnConstantCurrent(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_OK;
	std::string answer;
	std::ostringstream command;
	float tmp;

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
		iss >> tmp;
		m_constCurrent = (int)(tmp * 1000.0);
		pProp->Set(m_constCurrent);
	}
	else if (eAct == MM::AfterSet)
	{
		// get property
		pProp->Get(m_constCurrent);
		// prepare command
		command << "cc " << (float)m_constCurrent/1000.0;
		// send command
		ret = SendSerialCommand(m_port.c_str(), command.str().c_str(), "\r\n");
		if (ret != DEVICE_OK) return ret;
		// error handling
		getLastError(&ret);
	}

	return ret;
}


/*---------------------------------------------------------------------------
 This function is the callback function for the "Modulation Current" property.
---------------------------------------------------------------------------*/
int DC3100::OnModulationCurrent(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_OK;
	std::string answer;
	std::ostringstream command;
	float tmp;

	if (eAct == MM::BeforeGet)
	{
		command << "cm?";
		ret = SendSerialCommand(m_port.c_str(), command.str().c_str(), "\r\n");
		if (ret != DEVICE_OK) return ret;
		ret = GetSerialAnswer(m_port.c_str(), "\r\n", answer);
		if (ret != DEVICE_OK) return ret;
		// error handling
		getLastError(&ret);

		std::istringstream iss(answer);
		iss >> tmp;
		m_moduCurrent = (int)(tmp * 1000.0);
		pProp->Set(m_moduCurrent);
	}
	else if (eAct == MM::AfterSet)
	{
		// get property
		pProp->Get(m_moduCurrent);
		// prepare command
		command << "cm " << (float)m_moduCurrent/1000.0;
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
int DC3100::OnModulationFrequency(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_OK;
	std::string answer;
	std::ostringstream command;
	double tmp = 0.0;

	if (eAct == MM::BeforeGet)
	{
		command << "f?";
		ret = SendSerialCommand(m_port.c_str(), command.str().c_str(), "\r\n");
		if (ret != DEVICE_OK) return ret;
		ret = GetSerialAnswer(m_port.c_str(), "\r\n", answer);
		if (ret != DEVICE_OK) return ret;
		// error handling
		getLastError(&ret);

		std::istringstream iss(answer);
		iss >> m_moduFrequency;
		pProp->Set(m_moduFrequency);
	}
	else if (eAct == MM::AfterSet)
	{
		// get property
		pProp->Get(tmp);
		m_moduFrequency = (float)tmp;
		// prepare command
		command << "f " << m_moduFrequency;
		// send command
		ret = SendSerialCommand(m_port.c_str(), command.str().c_str(), "\r\n");
		if (ret != DEVICE_OK) return ret;
		// error handling
		getLastError(&ret);
	}

	return ret;
}


/*---------------------------------------------------------------------------
 This function is the callback function for the "Modulation Depth" property.
---------------------------------------------------------------------------*/
int DC3100::OnModulationDepth(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_OK;
	std::string answer;
	std::ostringstream command;

	if (eAct == MM::BeforeGet)
	{
		command << "d?";
		ret = SendSerialCommand(m_port.c_str(), command.str().c_str(), "\r\n");
		if (ret != DEVICE_OK) return ret;
		ret = GetSerialAnswer(m_port.c_str(), "\r\n", answer);
		if (ret != DEVICE_OK) return ret;
		// error handling
		getLastError(&ret);

		std::istringstream iss(answer);
		iss >> m_moduDepth;
		pProp->Set(m_moduDepth);
	}
	else if (eAct == MM::AfterSet)
	{
		// get property
		pProp->Get(m_moduDepth);
		// prepare command
		command << "d " << (int)m_moduDepth;
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
int DC3100::OnOperationMode(MM::PropertyBase* pProp, MM::ActionType eAct)
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
			m_mode = "Internal Modulation";
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
		else if (answer == "Internal Modulation")
		{
			command << "m 1";
			m_mode = "Internal Modulation";
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
int DC3100::OnStatus(MM::PropertyBase* pProp, MM::ActionType eAct)
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

		if (tmp & 0x0002)	m_status += "No LED ";
		if (tmp & 0x0008)	m_status += "VCC Fail ";
		if (tmp & 0x0020)	m_status += "OTP ";
		if (tmp & 0x0080)	m_status += "LED open ";
		if (tmp & 0x0200)	m_status += "Limit ";
		if (tmp & 0x0800)	m_status += "OTP head ";
		if (!tmp)			m_status = "No Fault";

		pProp->Set(m_status.c_str());
	}

	return ret;
}


/*---------------------------------------------------------------------------
 This function clears the dynamic error list.
---------------------------------------------------------------------------*/
bool DC3100::dynErrlist_free(void)
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
bool DC3100::dynErrlist_lookup(int err, std::string* descr)
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
bool DC3100::dynErrlist_add(int err, std::string descr)
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
bool DC3100::getLastError(int* err)
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
int DC3100::CreateStaticReadOnlyProperties(void)
{
	int nRet = DEVICE_OK;
	std::ostringstream cmd1;
	std::ostringstream cmd2;
	std::ostringstream cmd3;

	// head serial number information
	cmd1 << "hs?";
	nRet = SendSerialCommand(m_port.c_str(), cmd1.str().c_str(), "\r\n");
	if (nRet != DEVICE_OK) return nRet;
	nRet = GetSerialAnswer(m_port.c_str(), "\r\n", m_headSerialNo);
	if (nRet != DEVICE_OK) return nRet;
	getLastError(&nRet);
	if (nRet != DEVICE_OK) return nRet;
	nRet = CreateProperty("LED Serial Number", m_headSerialNo.c_str(), MM::String, true);
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


	return nRet;
}


/*---------------------------------------------------------------------------
 This function checks if the device is valid.
---------------------------------------------------------------------------*/
int DC3100::ValidateDevice(void)
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

	if((devName.find("DC3100")) == std::string::npos) nRet = ERR_INVALID_DEVICE;

	return nRet;
}


