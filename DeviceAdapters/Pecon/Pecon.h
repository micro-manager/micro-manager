
#ifndef _PECON_H_
#define _PECON_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include <string>
#include <map>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_UNKNOWN_POSITION         10002
#define ERR_PORT_CHANGE_FORBIDDEN    10004
#define ERR_SET_POSITION_FAILED      10005
#define ERR_INVALID_STEP_SIZE        10006
#define ERR_INVALID_MODE             10008
#define ERR_UNRECOGNIZED_ANSWER      10009
#define ERR_UNSPECIFIED_ERROR        10010
#define ERR_COMMAND_ERROR            10201
#define ERR_PARAMETER_ERROR          10202
#define ERR_RECEIVE_BUFFER_OVERFLOW  10204
#define ERR_COMMAND_OVERFLOW         10206
#define ERR_PROCESSING_INHIBIT       10207
#define ERR_PROCESSING_STOP_ERROR    10208

#define ERR_OFFSET 10100
#define ERR_TIRFSHUTTER_OFFSET 10200
#define ERR_INTENSILIGHTSHUTTER_OFFSET 10300

//////////////////////////////////////////////////////////////////////////////
// T e m p    C o n t r o l    D e v i c e    A d a p t e r
//////////////////////////////////////////////////////////////////////////////

class TempControl: public CGenericBase<TempControl>
{
public:
	TempControl();
	~TempControl();
	// Device API
	// ----------
	int Initialize();
	int Shutdown();

	void GetName(char* pszName) const;
	bool Busy();
	// Incubation API
	// ---------
	int SetTemp(int channel, double temp = 37.0);
	int GetTemp(int channel, double& temp);
	int SetHeating(int channel, int status = 0);
	int GetHeating(int channel, int& status);
	bool WakeUp();
	bool IsCorrectDevice(int address);

	// action interface
	// ----------------
	int OnHeating1C(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnHeating2C(MM::PropertyBase* pProp, MM::ActionType eAct);

	int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnChannel(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnVersion(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnGetTemp1C(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnGetTemp2C(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSetTemp1C(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSetTemp2C(MM::PropertyBase* pProp, MM::ActionType eAct);

	int GetSerialAnswer(const char* portName, std::string& ans, unsigned int count);
private:
	int GetVersion();

	std::string port_;
	std::string address_;
	std::string command_;           
	int state_;
	bool initialized_;
	int heating1C_;
	int heating2C_;
	double realTemp1C_;
	double realTemp2C_;
	double nominalTemp1C_;
	double nominalTemp2C_;


	std::string activeChannel_;
	// version string returned by device
	std::string version_;
	

};

//////////////////////////////////////////////////////////////////////////////
// C T I    C o n t r o l    D e v i c e    A d a p t e r
//////////////////////////////////////////////////////////////////////////////

class CTIControl: public CGenericBase<CTIControl>
{
public:
	CTIControl();
	~CTIControl();
	int Initialize();
	int Shutdown();

	void GetName(char* pszName) const;
	bool Busy();
	bool WakeUp();
	bool IsCorrectDevice(int address);
    
	int GetCO2Actual(double &val);
	int GetCO2Nominal(double &val);
	int SetCO2Nominal(double val);
	int GetHeatingIntensity(int &val);
	int SetHeatingIntensity(int val);
	int GetCO2ControlStatus(int &val);
	int SetCO2ControlStatus(int val);
	int GetVentilationSpeed(int &speed);
	int SetVentialtionSpeed(int speed);
	int GetDisplayStatus(int &status);
	int SetDisplayStatus(int status);
	int GetOverheatStatus(int &status);
	int SetOverheatStatus(int status);

	// action interface
	int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnChannel(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnVersion(MM::PropertyBase* pProp, MM::ActionType eAct);

	int OnCO2Actual(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnCO2Nominal(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnHeatingIntensity(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnCO2ControlStatus(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnVentilationSpeed(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnDisplayStatus(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnOverheatStatus(MM::PropertyBase* pProp, MM::ActionType eAct);

	int GetSerialAnswer(const char* portName, std::string& ans, unsigned int count);

private:

	int GetVersion();
	std::string port_;

	std::string command_;           
	int state_;

	// device address 00x
	std::string address_;
	
	bool initialized_;

	// version string returned by device
	std::string version_;

	double CO2Actual_;
	double CO2Nominal_;
	int Ventilation_;
	int Overheat_;
	int CO2ControlStatus_;

};

//////////////////////////////////////////////////////////////////////////////
// C O 2    C o n t r o l    D e v i c e    A d a p t e r
//////////////////////////////////////////////////////////////////////////////

class CO2Control: public CGenericBase<CO2Control>
{
public:
	CO2Control();
	~CO2Control();
	int Initialize();
	int Shutdown();

	void GetName(char* pszName) const;
	bool Busy();
	bool WakeUp();
	bool IsCorrectDevice(int address);

	int GetCO2Actual(double &val);
	int GetCO2Nominal(double &val);
	int SetCO2Nominal(double val);
	int GetCO2ControlStatus(int &val);
	int SetCO2ControlStatus(int val);
	int GetVentilationSpeed(int &speed);
	int SetVentialtionSpeed(int speed);
	int GetDisplayStatus(int &status);
	int SetDisplayStatus(int status);

	// action interface
	int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnChannel(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnVersion(MM::PropertyBase* pProp, MM::ActionType eAct);

	int OnCO2Actual(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnCO2Nominal(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnCO2ControlStatus(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnVentilationSpeed(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnDisplayStatus(MM::PropertyBase* pProp, MM::ActionType eAct);

	int GetSerialAnswer(const char* portName, std::string& ans, unsigned int count);


private:

	int GetVersion();
	std::string port_;

	std::string command_;           
	int state_;
	bool initialized_;

	// device address 00x
	std::string address_;

	// version string returned by device
	std::string version_;

};




#endif //_PECON_H_
