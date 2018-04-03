#include "OmicronxX.h"
#include "OmicronDeviceDriver.h"
#include "OmicronxXDevices.h"
#include "Omicron.h"
#include <sstream>
#include "stdlib.h"
#include <algorithm>

#ifdef OMICRON_XDEVICES
#include "OmicronxXDevices.h"
#endif

//#include <cstring>

const char* g_DeviceOmicronName = "Omicron";
const char* g_DeviceOmicronxXName = "Omicron USB";

//#define _NOSPECSTRING

//-----------------------------------------------------------------------------
// MMDevice API
//-----------------------------------------------------------------------------
BOOL APIENTRY DllMain(HANDLE /*hModule*/,
	DWORD ul_reason_for_call,
	LPVOID /*lpReserved*/)
{

#ifdef OMICRON_XDEVICES
	switch (ul_reason_for_call)
	{
	case DLL_PROCESS_ATTACH:
		openDriver();
		break;
	case DLL_PROCESS_DETACH:
		closeDriver();
		break;
	}
#endif // OMICRON_XDEVICES

	
	return TRUE;
}

MODULE_API void InitializeModuleData()
{
	RegisterDevice(g_DeviceOmicronName, MM::GenericDevice, "Omicron Laser Controller");
#ifdef OMICRON_XDEVICES
	RegisterDevice(g_DeviceOmicronxXName, MM::ShutterDevice, "Omicron Device");
#endif // OMICRON_XDEVICES
	
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
	if (deviceName == 0)
		return 0;
	if (strcmp(deviceName, g_DeviceOmicronName) == 0)
	{
		return new Omicron;
	}
#ifdef OMICRON_XDEVICES
	else if (strcmp(deviceName, g_DeviceOmicronxXName) == 0)
	{
		return new OmicronDevice;
	}
#endif // OMICRON_XDEVICES
	
	return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
	try {
	delete pDevice;
	}
	catch (...) {
		MessageBox(NULL, "Fehler DeleteDevice", "Fehler", MB_OK | MB_TASKMODAL | MB_ICONWARNING);
	}
}


#ifdef OMICRON_XDEVICES

OmicronDevice::OmicronDevice():Index(-2), initialized(false),hasModShutter(false),shuttermask(0)
{
	InitializeDefaultErrorMessages();
	SetErrorText(ERR_PORT_CHANGE_FORBIDDEN, "You can't change the device after device has been initialized.");
	SetErrorText(ERR_DRIVER_NOT_FOUND, "Omicron Device Driver couldn't be opened.");
	SetErrorText(ERR_NO_DEVICE_AVAILABLE, "No Omicron Devices were found. Please check if the Device is connected properly and if it is not controlled by any other program.");
	SetErrorText(ERR_NO_DEVICE_SELECTED, "Please select a Device.");
	// Description
	CreateProperty(MM::g_Keyword_Description, "Omicron Device", MM::String, true);

	// Devices

	CPropertyAction* pAct = new CPropertyAction(this, &OmicronDevice::OnDevices);
	CreateProperty("Devices", "", MM::String, false, pAct, true);
	
	//AddAllowedValue("Devices", "");
	if (driverOpened()) {
		std::vector<std::string> devices;
		int nrdev = getAvailableDevices(&devices);
		if (nrdev == 0) {
			AddAllowedValue("Devices", "No Devices available.");
		}
		else {
			for (int i = 0; i < devices.size(); i++) {
				AddAllowedValue("Devices", devices[i].c_str());
			}
		}
	}
	else {
		AddAllowedValue("Devices", "Driver couldn't be opened.");		
	}
}


OmicronDevice::~OmicronDevice()
{
	if(initialized)
		Shutdown();

}

int OmicronDevice::Initialize()
{
	if(Index < 0)
		return DEVICE_ERR;
	TxX_Error res;
	TDeviceComState state;
	res = OMIGetDeviceComState(Index, &state);
	while (state == xXdcs_Requesting)
	{
		Sleep(50);
		res = OMIGetDeviceComState(Index, &state);
	}
	
	if (state == xXdcs_Online) {
		if (blockDevice(Index)) {
			initialized = true;
			CreateProperty("Device Name", devname.c_str(), MM::String, true);

			int channelmask = 0;
			res = OMIGetChannels(Index, &channelmask);
			int i = 0;
			
			bool hasModShutterMask = false;
			TParameterDetails paramodulationmask;
			TxX_Error resmodmask = OMIGetParameterDetails(Index, i, xXp_ModulationMask, &paramodulationmask);
			if (resmodmask == xXer_OK  && paramodulationmask.IsValid )
				hasModShutterMask = true;
			
			TParameterDetails paramodulationshutter;
			TxX_Error resmodshut = OMIGetParameterDetails(Index, i, xXp_ModulationMask, &paramodulationshutter);
			if (resmodshut == xXer_OK  && paramodulationshutter.IsValid)
			{
				hasModShutter = true;
				CPropertyAction* pAct = new CPropertyAction(this, &OmicronDevice::OnShutter);
				CreateProperty("Device Modulation Shutter", "Close", MM::String, false, pAct);
				AddAllowedValue("Device Modulation Shutter", "Close");
				AddAllowedValue("Device Modulation Shutter", "Open");
			}

				

			
			if (channelmask>0 && hasModShutterMask)
			{
				channelNrToName[0] = "None";
				CPropertyAction* pAct = new CPropertyAction(this, &OmicronDevice::OnChannelPreset);
				CreateProperty("Channel Presets", "", MM::String, false, pAct);
				AddAllowedValue("Channel Presets", "None",0);
			}
			
			
			do
			{
				std::stringstream chname;
				if (i > 0 && (channelmask & 1) ==1 ) {
					chname << "Lambda " << i;
				
#ifdef _NOSPECSTRING
					chname << " ";
#else
					int wl = -1;
					int sp = 0;
					TxX_Error reswl = OMIGetInt(Index, i, xXp_WaveLength, &wl);
					TxX_Error ressp = OMIGetInt(Index, i, xXp_SpecPower, &sp);
					if (reswl == xXer_OK && ressp == xXer_OK && wl != -1 && sp != 0) {
						chname << " (" << wl << "nm - " << sp << "mW) ";
					}
					else if (reswl == xXer_OK && wl != -1)
					{
						chname << " (" << wl << "nm) ";
					}
					else if (ressp == xXer_OK && sp != 0)
					{
						chname << " (" << sp << "mW) ";
					}
					else
					{
						chname << " ";
					}
					//Channel names
#endif
					
					
					if (hasModShutterMask)
					{
						std::stringstream paraname;
						paraname << chname.str() << "Channel Active";
						CPropertyActionEx* pAct = new CPropertyActionEx(this, &OmicronDevice::OnChannelSelect, i);
						CreateProperty(paraname.str().c_str(), "", MM::String, false, pAct);
						AddAllowedValue(paraname.str().c_str(), "Off");
						AddAllowedValue(paraname.str().c_str(), "On");

						channelNrToName[i] = chname.str();
						AddAllowedValue("Channel Presets", chname.str().c_str() , i);
					}
				}
				else if (channelmask > 0)
				{
					chname << "Device ";
				}

				TParameterDetails paraonoff;
				res = OMIGetParameterDetails(Index, i, xXp_DeviceOnOff, &paraonoff);
				if (res == xXer_OK && paraonoff.IsValid) {
					std::stringstream paraname;
					paraname << chname.str() << "Power";
					CPropertyActionEx* pAct = new CPropertyActionEx(this, &OmicronDevice::OnLaserOnOff, i);
					CreateProperty(paraname.str().c_str(), "", MM::String, false, pAct);
					AddAllowedValue(paraname.str().c_str(), "Off");
					AddAllowedValue(paraname.str().c_str(), "On");
				}

				TParameterDetails parapowerset;
				res = OMIGetParameterDetails(Index, i, xXp_PowerSetPoint, &parapowerset);
				if (res == xXer_OK && parapowerset.IsValid) {
					std::stringstream paraname;
					paraname << chname.str() << "Power Setpoint";
					CPropertyActionEx* pAct = new CPropertyActionEx(this, &OmicronDevice::OnPowerSetpoint, i);
					CreateProperty(paraname.str().c_str(), "0.0", MM::Float, false, pAct);
					SetPropertyLimits(paraname.str().c_str(), parapowerset.MinValue, parapowerset.MaxValue);
				}

				TParameterDetails paraopmode;
				res = OMIGetParameterDetails(Index, i, xXp_OperatingMode, &paraopmode);
				if (res == xXer_OK && paraopmode.IsValid) {
					std::stringstream paraname;
					paraname << chname.str() << "Operating Mode";
					CPropertyActionEx* pAct = new CPropertyActionEx(this, &OmicronDevice::OnOperatingmode, i);
					CreateProperty(paraname.str().c_str(), "", MM::String, false, pAct);
					for (int j = paraopmode.MinValue; j < paraopmode.MaxValue; j++) {
						char PositionName[100];
						wchar_t posname[100];
						wchar_t* ptrposname = posname;
						res = OMIGetEnumText(Index, i, xXp_OperatingMode, j, ptrposname);
						if (res == xXer_OK) {
							wcstombs(PositionName, posname, 100);
							std::replace(PositionName, PositionName + ::strlen(PositionName), ',', '-');
							AddAllowedValue(paraname.str().c_str(), PositionName, j);
						}

					}
				}

				if(i > 0)
				{
					channelmask = channelmask >> 1;
				}
					
				i++;
			} while (channelmask |= 0);

			if (hasModShutterMask)
			{
				channelNrToName[0xFF] = "";
				AddAllowedValue("Channel Presets", "", 0xFF);
				SetProperty("Channel Presets", "" );
			}	
			

		return DEVICE_OK;
		}
	
	}
	return DEVICE_ERR;
	
}

int OmicronDevice::Shutdown()
{
	if(initialized)
	if (unlockDevice(Index)) {
		initialized = false;
	}
	else {
		return DEVICE_ERR;
	}
	return DEVICE_OK;
}

void OmicronDevice::GetName(char * pszName) const
{
	CDeviceUtils::CopyLimitedString(pszName, g_DeviceOmicronName);
}

bool OmicronDevice::Busy()
{
	return false;
}

int OmicronDevice::SetOpen(bool open)
{
	if (hasModShutter)
	{
		TxX_Error res = xXer_ActionNotPossible;
		res = OMISetBool(Index, 0, xXp_ModulationShutter, open);
		if (res != xXer_OK)
			return DEVICE_ERR;
		return DEVICE_OK;
	}
	else
		return DEVICE_UNSUPPORTED_COMMAND;
}

int OmicronDevice::GetOpen(bool& open)
{
	if (hasModShutter)
	{
		bool state = false;
		TxX_Error res = OMIGetBool(Index, 0, xXp_ModulationShutter, &state);
		if (res == xXer_OK) {
			open = state;
			return DEVICE_OK;
		}
		else
			return DEVICE_ERR;
	}
	else
		return DEVICE_UNSUPPORTED_COMMAND;
}


int OmicronDevice::OnDevices(MM::PropertyBase * pProp, MM::ActionType eAct)
{
		if (eAct == MM::BeforeGet)
		{
			pProp->Set(devname.c_str());
		}
		else if (eAct == MM::AfterSet)
		{
			if (initialized)
			{
				// revert
				pProp->Set(devname.c_str());
				return ERR_PORT_CHANGE_FORBIDDEN;
			}
			std::string tmpname;
			pProp->Get(tmpname);
			if (tmpname == devname)
				return DEVICE_OK;
			else if (tmpname == "") {
				return ERR_NO_DEVICE_SELECTED;
			}
			else if (tmpname == "Driver couldn't be opened.") {
				return ERR_DRIVER_NOT_FOUND;
			}
			else if (tmpname == "No Devices available.") {
				return ERR_NO_DEVICE_AVAILABLE;
			}
			else
			{
				if ((Index = getDeviceIndexbyName(tmpname)) != -1) {
					devname = tmpname;
					return DEVICE_OK;
				}
				else
					return DEVICE_NOT_CONNECTED;
		
			}
		
		}

	return DEVICE_OK;
	
}

int OmicronDevice::OnLaserOnOff(MM::PropertyBase * pProp, MM::ActionType eAct, long ch)
{
	TxX_Error res;
	if (eAct == MM::BeforeGet) {
		bool state = false;
		res=OMIGetBool(Index, ch, xXp_DeviceOnOff, &state);
		if (res == xXer_OK) {
			if (state) {
				pProp->Set("On");
			}
			else {
				pProp->Set("Off");
			}
		}
		else
			return DEVICE_ERR;
	}
	else if (eAct == MM::AfterSet) {
		std::string powerOnOff;
		pProp->Get(powerOnOff);
		res = xXer_ActionNotPossible;
		if (powerOnOff == "On") {
			res = OMISetBool(Index, ch, xXp_DeviceOnOff, true);
		}
		else if (powerOnOff == "Off") {
			res = OMISetBool(Index, ch, xXp_DeviceOnOff, false);
		}
		if (res != xXer_OK)
			return DEVICE_ERR;
	}
	return DEVICE_OK;
}

int OmicronDevice::OnPowerSetpoint(MM::PropertyBase * pProp, MM::ActionType eAct, long ch)
{
	TxX_Error res;
	if (eAct == MM::BeforeGet) {
		double setpoint;
		res = OMIGetFloat(Index, ch, xXp_PowerSetPoint, &setpoint);
		if (res == xXer_OK) {
			pProp->Set(setpoint);
		}
		else
			return DEVICE_ERR;
	}
	else if (eAct == MM::AfterSet) {
		double setpoint;
		pProp->Get(setpoint);
		res = OMISetFloat(Index, ch, xXp_PowerSetPoint, setpoint);
		if (res != xXer_OK)
			return DEVICE_ERR;
	}
	return DEVICE_OK;
}

int OmicronDevice::OnOperatingmode(MM::PropertyBase * pProp, MM::ActionType eAct, long ch)
{
	TxX_Error res;
	if (eAct == MM::BeforeGet) {
		int mode;
		res = OMIGetInt(Index, ch, xXp_OperatingMode, &mode);
		if (res == xXer_OK) {
			char modestr[100];
			wchar_t posname[100];
			res = OMIGetEnumText(Index, ch, xXp_OperatingMode, mode, posname);
			if (res == xXer_OK) {
				wcstombs(modestr, posname, 100);
				std::replace(modestr, modestr + ::strlen(modestr), ',', '-');
				pProp->Set(modestr);
			}
			else
				return DEVICE_ERR;
		}
		else
			return DEVICE_ERR;
	}
	else if (eAct == MM::AfterSet) {
		long mode;
		GetCurrentPropertyData(pProp->GetName().c_str(), mode);
		res = OMISetInt(Index, ch, xXp_OperatingMode, mode);
		if (res != xXer_OK)
			return DEVICE_ERR;
	}
	return DEVICE_OK;
}

int OmicronDevice::OnShutter(MM::PropertyBase * pProp, MM::ActionType eAct)
{
	TxX_Error res;
	if (eAct == MM::BeforeGet) {
		bool state = false;
		res = OMIGetBool(Index, 0, xXp_ModulationShutter, &state);
		if (res == xXer_OK) {
			if (state) {
				pProp->Set("Open");
			}
			else {
				pProp->Set("Close");
			}
		}
		else
			return DEVICE_ERR;
	}
	else if (eAct == MM::AfterSet) {
		std::string shutterOpen;
		pProp->Get(shutterOpen);
		res = xXer_ActionNotPossible;
		if (shutterOpen == "Open") {
			res = OMISetBool(Index, 0, xXp_ModulationShutter, true);
		}
		else if (shutterOpen == "Close") {
			res = OMISetBool(Index, 0, xXp_ModulationShutter, false);
		}
		if (res != xXer_OK)
			return DEVICE_ERR;
	}
	return DEVICE_OK;
}

int OmicronDevice::OnChannelSelect(MM::PropertyBase* pProp, MM::ActionType eAct, long ch)
{
	TxX_Error res;
	if (eAct == MM::BeforeGet) {
		int modMask = 0;
		res = OMIGetInt(Index, 0, xXp_ModulationMask, &modMask);
		if (res == xXer_OK) {
			if ((modMask >> (ch-1) ) & 1) {
				pProp->Set("On");
			}
			else {
				pProp->Set("Off");
			}
		}
		else
			return DEVICE_ERR;
	}
	else if (eAct == MM::AfterSet) {
		std::string channelActive;
		pProp->Get(channelActive);
		res = xXer_ActionNotPossible;

		int modMask = 0;
		res = OMIGetInt(Index, 0, xXp_ModulationMask, &modMask);
		if (channelActive == "On") {
			modMask |= (1 << (ch - 1));
		}
		else if (channelActive == "Off") {
			modMask &= ~(1 << (ch - 1));
		}
		res = OMISetInt(Index, 0, xXp_ModulationMask, modMask);
		if (res != xXer_OK)
			return DEVICE_ERR;
	}
	return DEVICE_OK;
}

int OmicronDevice::OnChannelPreset(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	TxX_Error res;
	if (eAct == MM::BeforeGet) {
		int modMask = 0;
		res = OMIGetInt(Index, 0, xXp_ModulationMask, &modMask);
		if (res == xXer_OK) {
			int found = 0;
			for (int i = 1; i < 32; i++)
			{
				if ((modMask >> (i - 1)) & 1) {
					if (!found)
					{
						found = i;
					}
					else
					{
						found = 0xFF;
						break;
					}
				}
			}
			pProp->Set(channelNrToName[found].c_str());
		}
		else
			return DEVICE_ERR;
	}
	else if (eAct == MM::AfterSet) {
		std::string channelpreset;
		pProp->Get(channelpreset);
		long channelNr;
		GetCurrentPropertyData(pProp->GetName().c_str(), channelNr);

		
		if (channelNr == 0)
		{
			res = OMISetInt(Index, 0, xXp_ModulationMask, 0);
		}
		else if (channelNr < 0xFF)
		{
			res = OMISetInt(Index, 0, xXp_ModulationMask, (1<<(channelNr -1)) );
		}
		else
		{
			res = xXer_OK;
		}
		
		if (res != xXer_OK)
			return DEVICE_ERR;
	}
	return DEVICE_OK;
}


#endif // OMICRON_XDEVICES