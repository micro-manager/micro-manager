#include "LDI.h"

#include <ctime>

const char* g_LDI_name = "89 North Laser Diode Illuminator";
const char* g_LDI_description = "Multi-line, Solid-State Laser Illuminator";
#define LDI_ERROR 108901
#define LDI_PORT_CHANGE_FORBIDDEN 108902

MODULE_API void InitializeModuleData()
{
	RegisterDevice(g_LDI_name, MM::ShutterDevice, g_LDI_description);
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_LDI_name) == 0)
   {
      LDI* s = new LDI();
      return s;
   }

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}


LDI::LDI()
	: m_port("Undefined"),
	m_initialized(false),
	m_busy(false),
	m_availableWavelengths(),
	m_autoShutterWavelengths(std::array<std::string, 4>()),
	m_autoShutterOpen(false)
{
	InitializeDefaultErrorMessages();
	SetErrorText(LDI_PORT_CHANGE_FORBIDDEN, "Port change is forbidden after initialization");

	CreateProperty(MM::g_Keyword_Name, g_LDI_name, MM::String, true);
	CreateProperty(MM::g_Keyword_Description, g_LDI_description, MM::String, true);

	CPropertyAction* pActPort = new CPropertyAction (this, &LDI::OnPort);
	CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pActPort, true);

	for (int i = 0; i < 4; i++)
		m_autoShutterWavelengths[i] = "None";
}

int LDI::Initialize()
{
	
	PurgeComPort(m_port.c_str());

	if (!getAvailableWavelengths())
		return Ret_LDI_Error("Failed to query available wavelengths");

	CPropertyAction* pAct = new CPropertyAction (this, &LDI::OnState);
	CreateProperty("Functional Mode", "IDLE", MM::String, false, pAct);
	AddAllowedValue("Functional Mode", "IDLE"); AddAllowedValue("Functional Mode", "RUN");

	pAct = new CPropertyAction(this, &LDI::OnIntensityControl);
	CreateProperty("Intensity Control", "PC", MM::String, false, pAct);
	AddAllowedValue("Intensity Control", "PC"); AddAllowedValue("Intensity Control", "EXT");

	pAct = new CPropertyAction(this, &LDI::OnShutterControl);
	CreateProperty("Shutter Control", "PC", MM::String, false, pAct);
	AddAllowedValue("Shutter Control", "PC"); AddAllowedValue("Shutter Control", "EXT");

	pAct = new CPropertyAction(this, &LDI::OnDespeckler);
	CreateProperty("Despeckler", "ON", MM::String, false, pAct);
	AddAllowedValue("Despeckler", "ON"); AddAllowedValue("Despeckler", "OFF");

	pAct = new CPropertyAction(this, &LDI::OnSleepTimer);
	CreateProperty("Sleep Timer (Minutes)", "30", MM::Integer, false, pAct);
	SetPropertyLimits("Sleep Timer (Minutes)", 0.0, 99.0);

	pAct = new CPropertyAction(this, &LDI::OnFault);
	CreateProperty("Fault", "NONE", MM::String, false, pAct);
	AddAllowedValue("Fault", "CLEAR");

	for (int i = 0; i < m_availableWavelengths.size(); i++)
	{
		// Intensity Control
		std::stringstream ss = std::stringstream();
		ss << m_availableWavelengths[i] << " Intensity";

		CPropertyActionEx* pActEx = new CPropertyActionEx(this, &LDI::OnPower, (long)m_availableWavelengths[i]);
		CreateProperty(ss.str().c_str(), "0.0", MM::Float, false, pActEx);
		SetPropertyLimits(ss.str().c_str(), 0.0, 100.0);
		
		// Shutter Control
		ss.str(std::string());
		ss << m_availableWavelengths[i] << " Shutter";

		pActEx = new CPropertyActionEx(this, &LDI::OnShutter, (long)m_availableWavelengths[i]);
		CreateProperty(ss.str().c_str(), "CLOSED", MM::String, false, pActEx);
		AddAllowedValue(ss.str().c_str(), "CLOSED"); AddAllowedValue(ss.str().c_str(), "OPEN");

		// TTL inverted control
		ss.str(std::string());
		ss << m_availableWavelengths[i] << " TTL Inverted";

		pActEx = new CPropertyActionEx(this, &LDI::OnTTLHighShutterPos, (long)m_availableWavelengths[i]);
		CreateProperty(ss.str().c_str(), "OFF", MM::String, false, pActEx);
		AddAllowedValue(ss.str().c_str(), "OFF"); AddAllowedValue(ss.str().c_str(), "ON");
	}

	for (int i = 1; i < 5; i++)
	{
		std::stringstream ss = std::stringstream();
		ss << "Auto Shutter Wavelength " << i;

		CPropertyActionEx* pActEx = new CPropertyActionEx(this, &LDI::OnShutterWavelength, (long)(i-1));
		CreateProperty(ss.str().c_str(), "None", MM::String, false, pActEx);
		AddAllowedValue(ss.str().c_str(), "None");
		
		for (int j = 0; j < m_availableWavelengths.size(); j++)
			AddAllowedValue(ss.str().c_str(), std::to_string((long long)m_availableWavelengths[j]).c_str());
	}

	m_initialized = true;
	return DEVICE_OK;
}

int LDI::Shutdown()
{
	return DEVICE_OK;
}

void LDI::GetName(char * name) const
{
	CDeviceUtils::CopyLimitedString(name, g_LDI_name);
}

int LDI::SetOpen(bool open)
{
	std::string str_open = open ? "OPEN" : "CLOSED";

	std::stringstream ss = std::stringstream();
	ss << "SHUTTER:";

	int n_lasers = 0;
	for (int i = 0; i < 4; i++)
	{
		if (m_autoShutterWavelengths[i] == "None") continue;
		
		if (n_lasers++ != 0)
			ss << ",";

		ss << m_autoShutterWavelengths[i] << "=" << open;
	}

	m_autoShutterOpen = true;
	if (!n_lasers) 
		return DEVICE_OK;

	std::string resp;
	auto success = sendCOMMessage(ss.str(), resp);

	if (!success || resp.substr(0, 3) == "ERR")
	{
		ss.str(std::string());
		ss << "Failed to open shutters!" << std::endl;
		ss << resp;

		return Ret_LDI_Error(ss);
	}	
	
	m_autoShutterOpen = open;
	return DEVICE_OK;
}

int LDI::GetOpen(bool & open)
{
	open = m_autoShutterOpen;
	return DEVICE_OK;
}

int LDI::OnPort(MM::PropertyBase * pProp, MM::ActionType eAct)
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
			return LDI_PORT_CHANGE_FORBIDDEN;
		}
		pProp->Get(m_port);
	}

	return DEVICE_OK;
}

int LDI::OnState(MM::PropertyBase * pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		std::string resp;
		auto success = sendCOMMessage("F_MODE?", resp);

		if (!success || resp.substr(0, 7) != "F_MODE=")
			return Ret_LDI_Error("Failed to get current functional mode from LDI!");

		resp = resp.substr(7);

		pProp->Set(resp.c_str());
	} 
	else if (eAct == MM::AfterSet)
	{
		std::string mode;
		pProp->Get(mode);

		std::string resp;
		sendCOMMessage(mode, resp);
		if (mode == "RUN")
		{
			sendCOMMessage("RUN", resp);
		}
		else if (mode == "IDLE")
		{
			sendCOMMessage("IDLE", resp);
		}
		else 
		{
			return Ret_LDI_Error("Unknown functional mode " + mode);
		}

		if (resp.substr(0, 3) == "ERR")
		{
			return Ret_LDI_Error(resp.substr(3));
		}
	}

	return DEVICE_OK;
}

int LDI::OnFault(MM::PropertyBase * pProp, MM::ActionType eAct) 
{
	if (eAct == MM::BeforeGet)
	{
		std::string resp;
		auto success = sendCOMMessage("FAULT?", resp);

		if (success) {
			if (resp == "ok")
			{
				pProp->Set("NONE");
			}
			else if (resp.substr(0, 6) == "FAULT:")
			{
				resp = resp.substr(6);
				pProp->Set(resp.c_str());
			}
			else
			{
				return Ret_LDI_Error("Failed to get current faults from LDI! Unknown response: " + resp);
			}
		}
		else
		{
			return Ret_LDI_Error("Failed to get current faults from LDI! Failed to communicate with LDI");
		}
	}
	else
	{
		std::string resp;
		auto success = sendCOMMessage("CLEAR", resp);
		
		Sleep(500);
		PurgeComPort(m_port.c_str());

		if (!success)
			return Ret_LDI_Error("Failed to send clear message to LDI!");

		if (resp.substr(0, 3) == "ERR")
			return Ret_LDI_Error(resp.substr(3));
	}

	return DEVICE_OK;
}

int LDI::OnIntensityControl(MM::PropertyBase * pProp, MM::ActionType eAct)
{
	return OnProp<std::string>(pProp, eAct, "INT_MODE", "intensity control");
}

int LDI::OnShutterControl(MM::PropertyBase * pProp, MM::ActionType eAct)
{
	return OnProp<std::string>(pProp, eAct, "SH_MODE", "shutter control");
}

int LDI::OnDespeckler(MM::PropertyBase * pProp, MM::ActionType eAct)
{
	return OnProp<std::string>(pProp, eAct, "SPECKLE", "despeckler");
}

int LDI::OnSleepTimer(MM::PropertyBase * pProp, MM::ActionType eAct)
{
	return OnProp<long>(pProp, eAct, "SLEEP", "sleep time (minutes)");
}

int LDI::OnPower(MM::PropertyBase * pProp, MM::ActionType eAct, long wavelength)
{
	return OnProp<double>(pProp, eAct, "SET", "intensity", wavelength);
}

int LDI::OnShutter(MM::PropertyBase * pProp, MM::ActionType eAct, long wavelength)
{
	return OnProp<std::string>(pProp, eAct, "SHUTTER", "shutter", wavelength);
}

int LDI::OnTTLHighShutterPos(MM::PropertyBase * pProp, MM::ActionType eAct, long wavelength)
{
	return OnProp<std::string>(pProp, eAct, "TTL_INVERT", "TTL invert", wavelength);
}

int LDI::OnShutterWavelength(MM::PropertyBase * pProp, MM::ActionType eAct, long n)
{
	if (eAct == MM::BeforeGet)
	{
		pProp->Set(m_autoShutterWavelengths[n].c_str());
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(m_autoShutterWavelengths[n]);
	}

	return DEVICE_OK;
}

bool LDI::sendCOMMessage(const std::string & message, std::string& response)
{
	LogMessage("SENDING: " + message);
	std::stringstream ss = std::stringstream();

	ss << message << '\n';

	LogMessage(ss.str());

	if (SendSerialCommand(m_port.c_str(), ss.str().c_str(), "") != DEVICE_OK)
	{
		LogMessage("Failed to communicate with LDI");
		response = "Failed to communicate with LDI";
		return false;
	}

	
	int mmResp = DEVICE_OK;
	clock_t start = clock();
	while (mmResp == DEVICE_OK)
	{
		if ((clock() - start) / CLOCKS_PER_SEC >= 10)
		{
			LogMessage("LDI did not respond!");
			response = "LDI did not respond!";
			return false;
		}

		GetSerialAnswer(m_port.c_str(), "\n", response);
		if (response != "")
			break;
	}

	LogMessage("RECIEVED: " + response);
	return true;
}

bool LDI::getAvailableWavelengths()
{
	std::string COMResponse;
	bool success = sendCOMMessage("CONFIG?", COMResponse);

	if (!success)
	{
		LogMessage("Send COM message fail");
		return false;
	}

	if (COMResponse.substr(0, 3) == "ERR")
	{ 
		LogMessage("Err returned");
		return false;
	}

	std::istringstream iss(COMResponse);
	std::string token;

	std::getline(iss, token, ':');

	while (std::getline(iss, token, ','))
	{
		try {
			auto wavelength = std::stoi(token);
			if (wavelength < 9990)
				m_availableWavelengths.push_back(wavelength);
		} catch (std::exception& ex) { }
	}
}

int LDI::Ret_LDI_Error(const std::string& err)
{
	SetErrorText(LDI_ERROR, err.c_str());
	return LDI_ERROR;
}

int LDI::Ret_LDI_Error(const std::stringstream& err)
{
	SetErrorText(LDI_ERROR, err.str().c_str());
	return LDI_ERROR;
}
