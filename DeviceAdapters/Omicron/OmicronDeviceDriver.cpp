#include <windows.h>
#include "OmicronxXDevices.h"
#include <vector>
#include <map>
#include <sstream>
#include "OmicronDeviceDriver.h"
#include <float.h>

#ifdef _WIN64
const char* OMIDLLname = "OmicronxXDevices64.dll";
#else
const char* OMIDLLname = "OmicronxXDevices.dll";
#endif
//[DllImport(“msvcrt.dll”, CallingConvention = CallingConvention.Cdecl)]


TxXGetDLLVersion		ExXGetDLLVersion = NULL;
TxXInitDLL				ExXInitDLL = NULL;
TxXShutdownDLL			ExXShutdownDLL = NULL;
TxXGetNumberOfDevices	ExXGetNumberOfDevices = NULL;
TxXGetDeviceID			ExXGetDeviceID = NULL;
TxXGetDeviceComState	ExXGetDeviceComState = NULL;
TxXGetChannels			ExXGetChannels = NULL;
TxXGetParameterDetails	ExXGetParameterDetails = NULL;
TxXGetEnumText			ExXGetEnumText = NULL;
TxXSetInt				ExXSetInt = NULL;
TxXGetInt				ExXGetInt = NULL;
TxXSetFloat				ExXSetFloat = NULL;
TxXGetFloat				ExXGetFloat = NULL;
TxXSetBool				ExXSetBool = NULL;
TxXGetBool				ExXGetBool = NULL;
TxXGetString			ExXGetString = NULL;

HMODULE					DLLHandle =	NULL;

bool					loaded = false;
std::vector<xXDevice> alldevices;
std::map<std::string, int> devNameToID;

bool openDriver() {
	unsigned int value = _controlfp(0,0);
	if (LoadOmicronxXDevicesDLL()) {
		if (ExXInitDLL && ExXGetDLLVersion)
		{
			int version = ExXGetDLLVersion();
			if ((version / 100) != 2) {
				UnloadOmicronxXDevicesDLL();
			}
			else
			{
				ExXInitDLL();
			}
		}
		
	}
	value = _controlfp(value, _MCW_EM);
	return false;
}

void closeDriver() {
	if (DLLHandle) {
		alldevices.clear();
		devNameToID.clear();
		try {
			if (ExXShutdownDLL)
				ExXShutdownDLL();
		}
		catch (...) {
			MessageBox(NULL, "Fehler DeleteDevice", "Fehler", MB_OK | MB_TASKMODAL | MB_ICONWARNING);
		}
		UnloadOmicronxXDevicesDLL();
	}
}

bool LoadOmicronxXDevicesDLL(void) {

	DLLHandle = LoadLibraryA(OMIDLLname);
	if (DLLHandle != NULL) {
		ExXGetDLLVersion = (TxXGetDLLVersion)GetProcAddress(DLLHandle,
			"xXGetDLLVersion");
		ExXInitDLL = (TxXInitDLL)GetProcAddress(DLLHandle, "xXInitDLL");
		ExXShutdownDLL = (TxXShutdownDLL)GetProcAddress(DLLHandle,
			"xXShutdownDLL");
		ExXGetNumberOfDevices = (TxXGetNumberOfDevices)GetProcAddress(DLLHandle,
			"xXGetNumberOfDevices");
		ExXGetDeviceID = (TxXGetDeviceID)GetProcAddress(DLLHandle,
			"xXGetDeviceID");
		ExXGetDeviceComState = (TxXGetDeviceComState)GetProcAddress(DLLHandle,
			"xXGetDeviceComState");
		ExXGetChannels = (TxXGetChannels)GetProcAddress(DLLHandle,
			"xXGetChannels");
		ExXGetParameterDetails = (TxXGetParameterDetails)GetProcAddress
			(DLLHandle, "xXGetParameterDetails");
		ExXGetEnumText = (TxXGetEnumText)GetProcAddress(DLLHandle,
			"xXGetEnumText");
		ExXSetInt = (TxXSetInt)GetProcAddress(DLLHandle, "xXSetInt");
		ExXGetInt = (TxXGetInt)GetProcAddress(DLLHandle, "xXGetInt");
		ExXSetFloat = (TxXSetFloat)GetProcAddress(DLLHandle, "xXSetFloat");
		ExXGetFloat = (TxXGetFloat)GetProcAddress(DLLHandle, "xXGetFloat");
		ExXSetBool = (TxXSetBool)GetProcAddress(DLLHandle, "xXSetBool");
		ExXGetBool = (TxXGetBool)GetProcAddress(DLLHandle, "xXGetBool");
		ExXGetString = (TxXGetString)GetProcAddress(DLLHandle, "xXGetString");
		return true;
	}
	else {
		return false;
	}
}

void UnloadOmicronxXDevicesDLL(void) {
	if (DLLHandle) {
		FreeLibrary(DLLHandle);
	}
}

bool driverOpened(void)
{
	if (DLLHandle)
		return true;
	return false;
}

void checkforDevices(void) {
	if (DLLHandle) {
		int nrDevices = 0;
		ExXGetNumberOfDevices(&nrDevices);
		if (nrDevices > alldevices.size()) {
			int oldSize = alldevices.size();
			alldevices.resize(nrDevices);
			for (int i = oldSize; i < nrDevices; i++) {
				TxX_Error res = ExXGetDeviceID(i, &alldevices[i].devID);
				if (res == xXer_OK) {
					TDeviceComState comstate;
					res = ExXGetDeviceComState(i, &comstate);
					while (res == xXer_OK && comstate == xXdcs_Requesting) {
						Sleep(50);
						res = ExXGetDeviceComState(i, &comstate);
					}
					if (res == xXer_OK && comstate == xXdcs_Online) {
						alldevices[i].available = true;
						alldevices[i].name = getDevName(&alldevices[i].devID);
						devNameToID[alldevices[i].name] = i;
					}
					else
						alldevices[i].available = false;
				}
			}
		}
	}
}


int getAvailableDevices(std::vector<std::string> *devices)
{
	int count = 0;
	if (DLLHandle) {
		checkforDevices();
		for (int i = 0; i < alldevices.size(); i++) {
			if (alldevices[i].available) {
				devices->push_back(alldevices[i].name);
				count++;
			}
		}
	}
	return count;
}

int getDeviceIndexbyName(std::string devname)
{
	for (int i = 0; i < alldevices.size(); i++)
		if (alldevices[i].name == devname)
			return i;
	return -1;
}

bool blockDevice(int Index)
{
	if (Index<alldevices.size()) {
		alldevices[Index].available = false;
		return true;
	}
	return false;
}

bool unlockDevice(int Index)
{
	if (Index<alldevices.size()) {
		alldevices[Index].available = true;
		return true;
	}
	return false;
}

std::string getDevName(TDeviceID* dev)
{
	std::stringstream sbuf;
	sbuf << dev->Typ;
	int wl = -1;
	int sp = 0;
	TxX_Error reswl = ExXGetInt(dev->DeviceIndex, xXp_WaveLength, &wl);
	TxX_Error ressp = ExXGetInt(dev->DeviceIndex, xXp_SpecPower, &sp);
	if (reswl == xXer_OK && ressp == xXer_OK && wl != -1 && sp != 0) {
		sbuf << " (" << wl << "nm - " << sp << "mW)";
	}
	else if (reswl == xXer_OK && wl != -1)
	{
		sbuf << " (" << wl << "nm)";
	}
	else if (ressp == xXer_OK && sp != 0)
	{
		sbuf << " (" << sp << "mW)";
	}
	char SN[50];
	char *pntSN = SN;
	TxX_Error ressn = ExXGetString(dev->DeviceIndex, xXp_SerialNumber, &pntSN);
	if (ressn == xXer_OK) {
		sbuf << " [" << SN << "]";
	}
	return sbuf.str();
}

TxX_Error OMIGetDeviceComState(int Index, TDeviceComState * DeviceComState)
{
	if (Index<alldevices.size() && ExXGetDeviceComState) {
		return ExXGetDeviceComState(Index, DeviceComState);
	}
	return xXer_Internal;
}

TxX_Error OMIGetChannels(int Index, int * nrch)
{
	if (Index<alldevices.size() && ExXGetChannels) {
		return ExXGetChannels(Index, nrch);
	}
	return xXer_Internal;
}

TxX_Error OMIGetParameterDetails(int Index, int channelID,TxX_Parameter parameter ,TParameterDetails * ParameterDetails)
{
	if (Index<alldevices.size() && ExXGetParameterDetails) {
		int fullid = (channelID << 16) + Index;
		return ExXGetParameterDetails(fullid,parameter,ParameterDetails);
	}
	return xXer_Internal;
}

TxX_Error OMIGetEnumText(int Index, int channelID, TxX_Parameter parameter, int EnumID, wchar_t * EnumText)
{
	if (Index<alldevices.size() && ExXGetEnumText) {
		int fullid = (channelID << 16) + Index;
		return ExXGetEnumText(fullid, parameter, EnumID, EnumText);
	}
	return xXer_Internal;
}

TxX_Error OMISetInt(int Index, int channelID, TxX_Parameter parameter, int value)
{
	if (Index<alldevices.size() && ExXSetInt) {
		int fullid = (channelID << 16) + Index;
		return ExXSetInt(fullid, parameter, value);
	}
	return xXer_Internal;
}

TxX_Error OMIGetInt(int Index, int channelID, TxX_Parameter parameter, int * value)
{
	if (Index<alldevices.size() && ExXGetInt) {
		int fullid = (channelID << 16) + Index;
		return ExXGetInt(fullid, parameter, value);
	}
	return xXer_Internal;
}

TxX_Error OMISetFloat(int Index, int channelID, TxX_Parameter parameter, double value)
{
	if (Index<alldevices.size() && ExXSetFloat) {
		int fullid = (channelID << 16) + Index;
		return ExXSetFloat(fullid, parameter, value);
	}
	return xXer_Internal;
}

TxX_Error OMIGetFloat(int Index, int channelID, TxX_Parameter parameter, double * value)
{
	if (Index<alldevices.size() && ExXGetFloat) {
		int fullid = (channelID << 16) + Index;
		return ExXGetFloat(fullid, parameter, value);
	}
	return xXer_Internal;
}

TxX_Error OMISetBool(int Index, int channelID, TxX_Parameter parameter, bool state)
{
	if (Index<alldevices.size() && ExXGetBool) {
		int fullid = (channelID << 16) + Index;
		return ExXSetBool(fullid, parameter, state);
	}
	return xXer_Internal;
}

TxX_Error OMIGetBool(int Index, int channelID, TxX_Parameter parameter, bool * state)
{
	if (Index<alldevices.size() && ExXSetBool) {
		int fullid = (channelID << 16) + Index;
		return ExXGetBool(fullid, parameter, state);
	}
	return xXer_Internal;
}

TxX_Error OMIGetString(int Index, int channelID, TxX_Parameter parameter, char ** StringParameter)
{
	if (Index<alldevices.size() && ExXGetString) {
		int fullid = (channelID << 16) + Index;
		return ExXGetString(fullid, parameter, StringParameter);
	}
	return xXer_Internal;
}


