#ifndef _89_NORTH_LDI_H
#define _89_NORTH_LDI_H

#include <DeviceBase.h>
#include <vector>
#include <sstream>
#include <array>

class LDI : public CShutterBase<LDI> 
{
public:
	LDI();
	//~LDI();

	virtual int Initialize() override;
	virtual int Shutdown() override;

	virtual void GetName(char * name) const override;

	virtual int SetOpen(bool open = true) override;
	virtual int GetOpen(bool & open) override;

	virtual bool Busy() override { return m_busy; }
	virtual int Fire(double deltaT) override { return DEVICE_UNSUPPORTED_COMMAND; }


	int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnIntensityControl(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnShutterControl(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnDespeckler(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSleepTimer(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnFault(MM::PropertyBase* pProp, MM::ActionType eAct);

	int OnPower(MM::PropertyBase* pProp, MM::ActionType eAct, long wavelength);
	int OnShutter(MM::PropertyBase* pProp, MM::ActionType eAct, long wavelength);
	int OnTTLHighShutterPos(MM::PropertyBase* pProp, MM::ActionType eAct, long wavelength);

	int OnShutterWavelength(MM::PropertyBase* pProp, MM::ActionType eAct, long n);

private:
	int Ret_LDI_Error(const std::string& message);
	int Ret_LDI_Error(const std::stringstream& message);

	template<class T>
	int OnProp(MM::PropertyBase * pProp, MM::ActionType eAct,
		const std::string& command, const std::string& HR_name, int wavelength = 0)
	{
		std::stringstream ss = std::stringstream();
		if (eAct == MM::BeforeGet)
		{
			ss << command << "?";
			if (wavelength) ss << wavelength;

			std::string resp;
			auto success = sendCOMMessage(ss.str(), resp);

			auto eqPos = resp.find("=");
			if (!success || eqPos == std::string::npos || resp.substr(0, 3) == "ERR")
			{
				ss.str(std::string());
				ss << "Failed to get current " << HR_name;
				if (wavelength) ss << " (" << wavelength << "nm)";
				ss << " setting from LDI!" << std::endl;
				ss << resp;

				return Ret_LDI_Error(ss);
			}

			resp = resp.substr(eqPos + 1);

			pProp->Set(resp.c_str());
		}
		else if (eAct == MM::AfterSet)
		{
			T mode;
			pProp->Get(mode);

			ss << command;
			if (wavelength) ss << ":" << wavelength;
			ss << "=" << mode;

			std::string resp;
			auto success = sendCOMMessage(ss.str(), resp);

			if (!success || resp.substr(0, 3) == "ERR")
			{
				ss.str(std::string());
				ss << "Failed to set " << HR_name;
				if (wavelength) ss << " (" << wavelength << "nm)";
				ss << " to" << mode << "!" << std::endl;
				ss << resp;

				return Ret_LDI_Error(ss);
			}
		}

		return DEVICE_OK;
	}

	bool sendCOMMessage(const std::string& message, std::string& response);
	bool getAvailableWavelengths();

	std::string m_port;

	std::vector<int> m_availableWavelengths;

	std::array<std::string, 4> m_autoShutterWavelengths;

	bool m_busy;
	bool m_initialized;
	bool m_autoShutterOpen;
};

#endif // !_89_NORTH_LDI_H

