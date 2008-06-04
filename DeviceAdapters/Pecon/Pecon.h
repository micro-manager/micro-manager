
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

#endif //_PECON_H_
