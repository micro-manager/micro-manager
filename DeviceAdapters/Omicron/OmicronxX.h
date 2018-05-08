#ifndef _OMICRONX_H_
#define _OMICRONX_H_
#endif

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/ModuleInterface.h"
#include <string>

//-----------------------------------------------------------------------------
// Error code
//-----------------------------------------------------------------------------

#define ERR_PORT_CHANGE_FORBIDDEN    101
#define ERR_NO_DEVICE_SELECTED    102
#define ERR_DRIVER_NOT_FOUND    103
#define ERR_NO_DEVICE_AVAILABLE    104

//-----------------------------------------------------------------------------

//class OmicronDevice : public CGenericBase<OmicronDevice>
class OmicronDevice : public CShutterBase<OmicronDevice>
{
public:
	OmicronDevice();
	~OmicronDevice();

	int Initialize();
	int Shutdown();

	void GetName(char* pszName) const;
	bool Busy();

	// Shutter API
	// ---------
	int SetOpen(bool open = true);
	int GetOpen(bool& open);
	int Fire(double /*interval*/) { return DEVICE_UNSUPPORTED_COMMAND; }

	//Properties
	int OnDevices(MM::PropertyBase* pProp, MM::ActionType eAct);

	int OnLaserOnOff(MM::PropertyBase* pProp, MM::ActionType eAct, long ch);
	int OnPowerSetpoint(MM::PropertyBase* pProp, MM::ActionType eAct, long ch);
	int OnOperatingmode(MM::PropertyBase* pProp, MM::ActionType eAct, long ch);

	int OnShutter(MM::PropertyBase* pProp, MM::ActionType eAct);

	int OnChannelSelect(MM::PropertyBase* pProp, MM::ActionType eAct, long ch);
	int OnChannelPreset(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
	int Index;
	std::string devname;
	//std::vector<std::string> channels;
	std::map<int,std::string> channelNrToName;
	int shuttermask;
	bool hasModShutter;
	bool initialized;
};


