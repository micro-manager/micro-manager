#ifndef _OMICRONXDRIVER_H_
#define _OMICRONXDRIVER_H_
#endif

#ifdef _WINDOWS
#define OMICRON_XDEVICES
#endif

#include "OmicronxXDevices.h"
#include <string>
#include <vector>
struct xXDevice
{
	TDeviceID devID;
	std::string name;
	bool available;
};
	bool openDriver();
	void closeDriver();
	bool LoadOmicronxXDevicesDLL(void);
	void UnloadOmicronxXDevicesDLL(void);
	bool driverOpened(void);
	void checkforDevices();

	int getAvailableDevices(std::vector<std::string> *devicenames);
	int getDeviceIndexbyName(std::string devname);
	bool blockDevice(int Index);
	bool unlockDevice(int Index);
	std::string getDevName(TDeviceID* dev);


	//OMI API Wrapper 
	TxX_Error OMIGetDeviceComState(int Index, TDeviceComState *DeviceComState);
	TxX_Error OMIGetChannels(int Index, int *channelMask);
	TxX_Error OMIGetParameterDetails(int Index,int channelID, TxX_Parameter parameter, TParameterDetails *ParameterDetails);
	TxX_Error OMIGetEnumText(int Index, int channelID, TxX_Parameter parameter, int EnumID, wchar_t *EnumText);
	TxX_Error OMISetInt(int Index, int channelID, TxX_Parameter parameter, int value);
	TxX_Error OMIGetInt(int Index, int channelID, TxX_Parameter parameter, int *value);
	TxX_Error OMISetFloat(int Index, int channelID, TxX_Parameter parameter, double value);
	TxX_Error OMIGetFloat(int Index, int channelID, TxX_Parameter parameter, double *value);
	TxX_Error OMISetBool(int Index, int channelID, TxX_Parameter parameter, bool state);
	TxX_Error OMIGetBool(int Index, int channelID, TxX_Parameter parameter, bool *state);
	TxX_Error OMIGetString(int Index, int channelID, TxX_Parameter parameter, char * *StringParameter);



	

	
