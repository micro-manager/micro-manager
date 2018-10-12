//-----------------------------------------------------------------------------
// FILE:          LaserQuantumLaser.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Controls gem/ventus/opus/axiom series from LaserQuantum 
// COPYRIGHT:     EMBL
// LICENSE:       LGPL
// AUTHOR:        Joran Deschamps, 2018
//-----------------------------------------------------------------------------

#include "LaserQuantumLaser.h"
#include <iostream>
#include <fstream>

#ifdef WIN32
#include "winuser.h"
#endif

const char* g_DeviceName = "Laser";

const char* ENABLED = "Enabled";
const char* DISABLED = "Disabled";
const char* POWER = "Power";
const char* CURRENT = "Current";
const char* ON = "On";
const char* OFF = "Off";
const char* STR_ERROR = "Error";

//-----------------------------------------------------------------------------
// MMDevice API
//-----------------------------------------------------------------------------

MODULE_API void InitializeModuleData()
{
	RegisterDevice(g_DeviceName, MM::GenericDevice, "LaserQuantum laser");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
	if (deviceName == 0)
		return 0;

	if (strcmp(deviceName, g_DeviceName) == 0)
	{
		return new LaserQuantumLaser;
	}

	return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
	delete pDevice;
}

//-----------------------------------------------------------------------------
// LaserQuantum Gem device adapter
//-----------------------------------------------------------------------------

LaserQuantumLaser::LaserQuantumLaser():
	port_("Undefined"),
	version_("Undefined"),
	initialized_(false),
	busy_(false),
	controlmode_(true),
	enabledCurrentControl_(true),
	power_(0.00),
	current_(0),
	startupstatus_(true),
	apccalibpower_(0),
	lasertemperature_(0),
	psutemperature_(0),
	status_(true),
	psutime_(0),
	laserenabledtime_(0),
	laseroperationtime_(0)
{
	InitializeDefaultErrorMessages();
	SetErrorText(ERR_PORT_CHANGE_FORBIDDEN, "You can't change the port after device has been initialized.");
	SetErrorText(ERR_CURRENT_CONTROL_UNSUPPORTED, "The laser does not support current control.");
	SetErrorText(ERR_NOT_IN_CURRENT_CONTROL_MODE, "The current percentage cannot be change because the laser is in power mode.");
	SetErrorText(ERR_NOT_IN_CURRENT_POWER_MODE, "The power level cannot because the laser is in current mode.");
	SetErrorText(ERR_UNEXPECTED_ANSWER, "The laser gave an unexpected answer.");
	SetErrorText(ERR_ERROR_ANSWER, "The laser returned an error.");
	SetErrorText(ERR_ERROR_67, "The laser returned an error 67.");

	// Description
	CreateProperty(MM::g_Keyword_Description, "LaserQuantum gem Controller", MM::String, true);

	// Port
	CPropertyAction* pAct = new CPropertyAction (this, &LaserQuantumLaser::OnPort);
	CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

	// Maximum power
	CreateProperty("Maximum power (mW)", "500", MM::Float, false, 0, true);
}

LaserQuantumLaser::~LaserQuantumLaser()
{
	Shutdown();
}

void LaserQuantumLaser::GetName(char* Name) const
{
	CDeviceUtils::CopyLimitedString(Name, g_DeviceName);
}

int LaserQuantumLaser::Initialize()
{
	// Get maximum power
	char charbuff[MM::MaxStrLength];
	int ret = GetProperty("Maximum power (mW)", charbuff);
	if (ret != DEVICE_OK)
		return ret;
	maxpower_ = atoi(charbuff); 

	std::vector<std::string> commandsOnOff;
	commandsOnOff.push_back(OFF);
	commandsOnOff.push_back(ON);

	std::vector<std::string> controlModes;
	controlModes.push_back(POWER);
	controlModes.push_back(CURRENT);

	std::vector<std::string> startupStatus;
	startupStatus.push_back(ENABLED);
	startupStatus.push_back(DISABLED);

	// Version number
	ret = getVersion(&version_); 
	if (DEVICE_OK != ret)
		return ret;

	ret = CreateProperty("Version", version_.c_str(), MM::String, true);
	if (DEVICE_OK != ret)
		return ret;

	// Laser On/Off
	CPropertyAction* pAct = new CPropertyAction (this, &LaserQuantumLaser::OnLaserOnOff);
	ret = CreateProperty("Laser Operation", OFF, MM::String, false, pAct);
	if (DEVICE_OK != ret)
		return ret;

	SetAllowedValues("Laser Operation", commandsOnOff);

	// Enable current control
	ret = supportsCurrentControl(&enabledCurrentControl_);
	if (DEVICE_OK != ret)
		return ret;

	if(enabledCurrentControl_){ // if the laser supports current control
		CreateProperty("Current control", ENABLED, MM::String, true);
	} else {
		CreateProperty("Current control", DISABLED, MM::String, true);
	}

	// Control mode
	if(!enabledCurrentControl_){ // if the current control mode is not enabled
		ret = CreateProperty("Control mode", POWER, MM::String, true);
		if (DEVICE_OK != ret)
			return ret;

		// no current property (should not change?)

	} else { // if current control enabled
		ret = getControlMode(&controlmode_);
		if (DEVICE_OK != ret)
			return ret;

		std::string mode;
		if(controlmode_){
			mode = POWER;
		} else {
			mode = CURRENT;
		}

		pAct = new CPropertyAction (this, &LaserQuantumLaser::OnControlMode);
		ret = CreateProperty("Control mode", mode.c_str(), MM::String, false, pAct);
		SetAllowedValues("Control mode", controlModes);
		if (DEVICE_OK != ret)
			return ret;

		// Current property
		getCurrent(&current_);
		pAct = new CPropertyAction (this, &LaserQuantumLaser::OnCurrent);
		ret = CreateProperty("Current (%)", to_string(current_).c_str(), MM::Integer, false, pAct);
		SetPropertyLimits("Current (%)", 0, 100);
		if (DEVICE_OK != ret)
			return ret;
	}

	// Power
	getPower(&power_);

	if(power_ > maxpower_){ // if power higher than set by user
		setPower(maxpower_);
		power_ = maxpower_;
	}
	pAct = new CPropertyAction (this, &LaserQuantumLaser::OnPower);
	ret = CreateProperty("Power (mW)", to_string(power_).c_str(), MM::Float, false, pAct);
	SetPropertyLimits("Power (mW)", 0, maxpower_);
	if (DEVICE_OK != ret)
		return ret;

	/////////////////// read only
	// Laser temperature
	getLaserTemperature(&lasertemperature_);

	pAct = new CPropertyAction (this, &LaserQuantumLaser::OnLaserTemperature);
	ret = CreateProperty("Temperature laser (C)", to_string(lasertemperature_).c_str(), MM::Float, true, pAct);
	if (DEVICE_OK != ret)
		return ret;

	// PSU temperature
	getPSUTemperature(&psutemperature_);

	pAct = new CPropertyAction (this, &LaserQuantumLaser::OnPSUTemperature);
	ret = CreateProperty("Temperature PSU (C)", to_string(psutemperature_).c_str(), MM::Float, true, pAct);
	if (DEVICE_OK != ret)
		return ret;

	//////////////// timers
	getTimers(&psutime_, &laserenabledtime_, &laseroperationtime_);

	// PSU Timer
	CPropertyActionEx* pActex = new CPropertyActionEx(this, &LaserQuantumLaser::OnTimers,0);
	ret = CreateProperty("Time PSU (h)", to_string(psutime_).c_str(), MM::Float, true, pActex);
	if (DEVICE_OK != ret)
		return ret;

	// Laser enabled timer
	pActex = new CPropertyActionEx(this, &LaserQuantumLaser::OnTimers,1);
	ret = CreateProperty("Time enabled (h)", to_string(laserenabledtime_).c_str(), MM::Float, true, pActex);
	if (DEVICE_OK != ret)
		return ret;

	// Laser operation timer
	pActex = new CPropertyActionEx(this, &LaserQuantumLaser::OnTimers,2);
	ret = CreateProperty("Time operation (h)", to_string(laseroperationtime_).c_str(), MM::Float, true, pActex);
	if (DEVICE_OK != ret)
		return ret;


	initialized_ = true;
	return DEVICE_OK;
}

int LaserQuantumLaser::Shutdown()
{
	if (initialized_){
		setLaserOnOff(false);
		initialized_ = false;	 
	}
	return DEVICE_OK;
}

bool LaserQuantumLaser::Busy(){
	return busy_;
}


//---------------------------------------------------------------------------
// Conveniance functions
//---------------------------------------------------------------------------


std::string LaserQuantumLaser::to_string(double x){
	std::ostringstream x_convert;
	x_convert << x;
	return x_convert.str();
}

bool LaserQuantumLaser::string_contains(std::string s1, std::string s2){
	if (s1.find(s2) != std::string::npos){
		return true;
	} else {
		return false;
	}
}

int LaserQuantumLaser::supportsCurrentControl(bool* supportsCurrent){  
	std::ostringstream command, command2, command3;
	std::string answer, answer2;

	*supportsCurrent = true;

	// get control mode
	bool controlmode;
	int ret = getControlMode(&controlmode);
	if (ret != DEVICE_OK) // propagates error
		return ret;

	// set to current control if not already
	if(controlmode_){
		ret = setControlMode(false);
		if (ret != DEVICE_OK) // propagates error
			return ret;
	}

	// Get current value
	ret = getCurrent(&current_);
	if (ret != DEVICE_OK){ // propagates error
		return ret;
	}

	// Try to set current to value
	ret = setCurrent(current_);

	if (ret == ERR_ERROR_67){ // error that should appear if current control is not supported

		ret = setControlMode(true); // returns to power control
		*supportsCurrent = false;

		if (ret != DEVICE_OK) // if error, propagates error
			return ret;

		// otherwise returns ok
		return DEVICE_OK;
	} else if(ret != DEVICE_OK){
		return ret; // propagates error
	} else {

		// no error
		*supportsCurrent = true;

		// goes back to previous mode if necessary 
		if(controlmode){
			ret = setControlMode(controlmode);
			if (ret != DEVICE_OK) // propagates error
				return ret;
		}
	}
	return DEVICE_OK;
}

////////////////////////////// getters

int LaserQuantumLaser::getVersion(std::string* version){
	std::ostringstream command;
	std::string answer;

	command << "VERSION?";
	int ret =SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
	if (ret != DEVICE_OK) 
		return ret;

	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK) 
		return ret;

	// check sanity of answer
	if(string_contains(answer,"SMD12")){
		*version = answer;
		return DEVICE_OK;
	} else if(string_contains(answer,STR_ERROR)){	
		std::stringstream log;
		log << "LaserQuantumLaser get version error: " << answer;
		LogMessage(log.str(), true);
		return ERR_ERROR_ANSWER;
	}

	return ERR_UNEXPECTED_ANSWER;
}


int LaserQuantumLaser::getStatus(bool* status){
	std::ostringstream command;
	std::string answer;

	command << "STATUS?";
	int ret =SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
	if (ret != DEVICE_OK) 
		return ret;

	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK) 
		return ret;

	if (string_contains(answer,"ENABLED")){
		*status = true;
		return DEVICE_OK;
	} else if (string_contains(answer,"DISABLED")){
		*status = false;
		return DEVICE_OK;
	} else if (string_contains(answer,STR_ERROR)){
		std::stringstream log;
		log << "LaserQuantumLaser get status error: " << answer;
		LogMessage(log.str(), true);
		return ERR_ERROR_ANSWER;
	}

	return ERR_UNEXPECTED_ANSWER;
}


int LaserQuantumLaser::getControlMode(bool* mode){
	std::ostringstream command;
	std::string answer;

	command << "CONTROL?";
	int ret =SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
	if (ret != DEVICE_OK) 
		return ret;

	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK) 
		return ret;

	if (string_contains(answer,"POWER")){
		*mode = true;
		return DEVICE_OK;
	} else if (string_contains(answer,"CURRENT")){
		*mode = false;
		return DEVICE_OK;
	} else if (string_contains(answer,STR_ERROR)){
		std::stringstream log;
		log << "LaserQuantumLaser get control mode error: " << answer;
		LogMessage(log.str(), true);
		return ERR_ERROR_ANSWER;
	}

	return ERR_UNEXPECTED_ANSWER;
}

int LaserQuantumLaser::getCurrent(double* current){
	std::ostringstream command;
	std::string answer;

	command << "CURRENT?";
	int ret =SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
	if (ret != DEVICE_OK) 
		return ret;

	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK) 
		return ret;

	if (string_contains(answer,"%")){
		double curr = 0;
		std::string s = answer.substr(0,s.length()-1);
		std::stringstream streamval(s);
		streamval >> curr;	

		// sanity check
		if(curr < 0 || curr > 100){
			return ERR_UNEXPECTED_ANSWER;
		}

		*current = curr;
		return DEVICE_OK;
	} else if (string_contains(answer,STR_ERROR)){
		std::stringstream log;
		log << "LaserQuantumLaser get current error: " << answer;
		LogMessage(log.str(), true);
		return ERR_ERROR_ANSWER;
	}

	return ERR_UNEXPECTED_ANSWER;
}

int LaserQuantumLaser::getPower(double* power){
	std::ostringstream command;
	std::string answer;

	command << "POWER?";
	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
	if (ret != DEVICE_OK) 
		return ret;

	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK) 
		return ret;

	if (string_contains(answer,"mW")){
		double pow = 0;

		std::string s = answer.substr(0,answer.length()-2);
		std::stringstream streamval(s);
		streamval >> pow;

		*power = pow;
		return DEVICE_OK;
	} else if (string_contains(answer,STR_ERROR)){
		std::stringstream log;
		log << "LaserQuantumLaser get power error: " << answer;
		LogMessage(log.str(), true);
		return ERR_ERROR_ANSWER;
	}

	return ERR_UNEXPECTED_ANSWER;
}

int LaserQuantumLaser::getLaserTemperature(double* temperature){
	std::ostringstream command;
	std::string answer;

	command << "LASTEMP?";
	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
	if (ret != DEVICE_OK) 
		return ret;

	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK) 
		return ret;

	if (string_contains(answer,"C")){
		double temp = 0;

		std::string s = answer.substr(0,answer.length()-1);
		std::stringstream streamval(s);
		streamval >> temp;	

		*temperature = temp;
		return DEVICE_OK;
	} else if (string_contains(answer,STR_ERROR)){
		std::stringstream log;
		log << "LaserQuantumLaser get laser temperature error: " << answer;
		LogMessage(log.str(), true);
		return ERR_ERROR_ANSWER;
	}

	return ERR_UNEXPECTED_ANSWER;
}

int LaserQuantumLaser::getPSUTemperature(double* temperature){
	std::ostringstream command;
	std::string answer;

	command << "PSUTEMP?";
	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
	if (ret != DEVICE_OK) 
		return ret;

	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK) 
		return ret;

	if (string_contains(answer,"C")){
		double temp = 0;

		std::string s = answer.substr(0,answer.length()-1);
		std::stringstream streamval(s);
		streamval >> temp;	

		*temperature = temp;
		return DEVICE_OK;
	} else if (string_contains(answer,STR_ERROR)){
		std::stringstream log;
		log << "LaserQuantumLaser get PSU temperature error: " << answer;
		LogMessage(log.str(), true);
		return ERR_ERROR_ANSWER;
	}

	return ERR_UNEXPECTED_ANSWER;
}

int LaserQuantumLaser::getTimers(double* psutime, double* laserenabletime, double* laseroperationtime){
	std::ostringstream command;
	std::string answer, answer1, answer2, answer3;

	command << "TIMERS?";
	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
	if (ret != DEVICE_OK) 
		return ret;

	// PSU
	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK) 
		return ret;

	double temp = 0;
	double temp1 = 0;
	double temp2 = 0;

	std::size_t tag = answer.find("PSU Time");
	if (tag!=std::string::npos){
		std::size_t found = answer.find(" = ");
		std::size_t found2 = answer.find(" Hours");
		std::string s = answer.substr(found+3,found2-found-3);
		std::stringstream streamval(s);
		streamval >> temp;

		*psutime = temp;
	} else if (string_contains(answer,STR_ERROR)){
		std::stringstream log;
		log << "LaserQuantumLaser get timers error: " << answer;
		LogMessage(log.str(), true);
		return ERR_ERROR_ANSWER;
	} else {
		return ERR_UNEXPECTED_ANSWER;
	}

	// Laser enabled
	ret = GetSerialAnswer(port_.c_str(), "\r", answer1);
	if (ret != DEVICE_OK) 
		return ret;

	tag = answer1.find("Laser Enabled Time");
	if (tag!=std::string::npos){
		std::size_t found = answer1.find(" = ");
		std::size_t found2 = answer1.find(" Hours");
		std::string s = answer1.substr(found+3,found2-found-3);
		std::stringstream streamval(s);
		streamval >> temp1;

		*laserenabletime = temp1;
	} else if (string_contains(answer1,STR_ERROR)){
		std::stringstream log;
		log << "LaserQuantumLaser get timers error: " << answer;
		LogMessage(log.str(), true);
		return ERR_ERROR_ANSWER;
	} else {
		return ERR_UNEXPECTED_ANSWER;
	}

	// Operation time
	ret = GetSerialAnswer(port_.c_str(), "\r", answer2);
	if (ret != DEVICE_OK) 
		return ret;

	tag = answer2.find("Laser Operation Time");
	if (tag!=std::string::npos){
		std::size_t found = answer2.find(" = ");
		std::size_t found2 = answer2.find(" Hours");
		std::string s = answer2.substr(found+3,found2-found-3);
		std::stringstream streamval(s);
		streamval >> temp2;

		*laseroperationtime = temp2;
	} else if (string_contains(answer2,STR_ERROR)){
		std::stringstream log;
		log << "LaserQuantumLaser get timers error: " << answer;
		LogMessage(log.str(), true);
		return ERR_ERROR_ANSWER;
	} else {
		return ERR_UNEXPECTED_ANSWER;
	}

	ret = GetSerialAnswer(port_.c_str(), "\r", answer3); // empty line
	if (ret != DEVICE_OK) 
		return ret;

	return DEVICE_OK;
}


//////////// Setters

int LaserQuantumLaser::setLaserOnOff(bool b){
	std::ostringstream command;
	std::string answer;

	if(b){
		command << "ON";
		int ret =SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
		if (ret != DEVICE_OK) 
			return ret;

		ret = GetSerialAnswer(port_.c_str(), "\r", answer);
		if (ret != DEVICE_OK) 
			return ret;


		if(string_contains(answer, STR_ERROR)){
			std::stringstream log;
			log << "LaserQuantumLaser set on error: " << answer;
			LogMessage(log.str(), true);
			return ERR_ERROR_ANSWER;
		} 
	} else {

		command << "OFF";
		int ret =SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
		if (ret != DEVICE_OK) 
			return ret;


		ret = GetSerialAnswer(port_.c_str(), "\r", answer);
		if (ret != DEVICE_OK) 
			return ret;

		if(string_contains(answer, STR_ERROR)){
			std::stringstream log;
			log << "LaserQuantumLaser set off error: " << answer;
			LogMessage(log.str(), true);
			return ERR_ERROR_ANSWER;
		}
	}

	return DEVICE_OK;
}

int LaserQuantumLaser::setControlMode(bool mode){
	std::ostringstream command;
	std::string answer;

	if(mode){
		command << "CONTROL=POWER";
		int ret =SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
		if (ret != DEVICE_OK) 
			return ret;

		ret = GetSerialAnswer(port_.c_str(), "\r", answer);
		if (ret != DEVICE_OK) 
			return ret;

		if(string_contains(answer, STR_ERROR)){
			std::stringstream log;
			log << "LaserQuantumLaser set power mode error: " << answer;
			LogMessage(log.str(), true);
			return ERR_ERROR_ANSWER;
		}
	} else {

		command << "CONTROL=CURRENT";
		int ret =SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
		if (ret != DEVICE_OK){
			return ret;
		}

		ret = GetSerialAnswer(port_.c_str(), "\r", answer);
		if (ret != DEVICE_OK)
			return ret;

		if(string_contains(answer, STR_ERROR)){
			std::stringstream log;
			log << "LaserQuantumLaser set current mode error: " << answer;
			LogMessage(log.str(), true);
			return ERR_ERROR_ANSWER;
		}
	}

	return DEVICE_OK;
}

int LaserQuantumLaser::setCurrent(double current){
	getControlMode(&controlmode_);

	if(!controlmode_){ // if can current control and in current control mode

		if(current >= 0 && current<=100){ // sanity check
			std::ostringstream command;
			std::string answer;

			command << "CURRENT=" << current;
			int ret =SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");		
			if (ret != DEVICE_OK) 
				return ret;

			ret = GetSerialAnswer(port_.c_str(), "\r", answer); // empty answer
			if (ret != DEVICE_OK) 
				return ret;

			if(string_contains(answer,"67")){ // expected error number if current control not available
				std::stringstream log;
				log << "LaserQuantumLaser error 67 when setting current: " << answer;
				LogMessage(log.str(), true);
				return ERR_ERROR_67;
			} else if(string_contains(answer,STR_ERROR)){ 
				std::stringstream log;
				log << "LaserQuantumLaser set current error: " << answer;
				LogMessage(log.str(), true);
				return ERR_ERROR_ANSWER;
			}
		}
	} else {
		return ERR_NOT_IN_CURRENT_CONTROL_MODE;
	}

	return DEVICE_OK;
}

int LaserQuantumLaser::setPower(double power){
	getControlMode(&controlmode_);

	if(controlmode_){ // if in power control mode

		if(power >= 0 && power<=maxpower_){ // sanity check
			std::ostringstream command;
			std::string answer;

			command << "POWER=" << power;
			int ret =SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");		
			if (ret != DEVICE_OK) 
				return ret;

			ret = GetSerialAnswer(port_.c_str(), "\r", answer); // empty answer
			if (ret != DEVICE_OK) 
				return ret;

			if(string_contains(answer,STR_ERROR)){
				std::stringstream log;
				log << "LaserQuantumLaser set power error: " << answer;
				LogMessage(log.str(), true); 
				return ERR_ERROR_ANSWER;
			}
		}
	} else {
		return ERR_NOT_IN_CURRENT_POWER_MODE;
	}

	return DEVICE_OK;
}

//---------------------------------------------------------------------------
// Read only properties
//---------------------------------------------------------------------------

int LaserQuantumLaser::OnPort(MM::PropertyBase* pProp , MM::ActionType eAct){
	if (eAct == MM::BeforeGet){
		pProp->Set(port_.c_str());
	}
	else if (eAct == MM::AfterSet) {
		if (initialized_)
		{
			// revert
			pProp->Set(port_.c_str());
			return ERR_PORT_CHANGE_FORBIDDEN;
		}

		pProp->Get(port_);
	}
	return DEVICE_OK;
}

int LaserQuantumLaser::OnLaserTemperature(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet){
		int ret = getLaserTemperature(&lasertemperature_);
		if (ret != DEVICE_OK) 
			return ret;

		pProp->Set(lasertemperature_);
	}
	return DEVICE_OK;
}

int LaserQuantumLaser::OnPSUTemperature(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet){
		int ret = getPSUTemperature(&psutemperature_);
		if (ret != DEVICE_OK) 
			return ret;

		pProp->Set(psutemperature_);
	}
	return DEVICE_OK;
}

int LaserQuantumLaser::OnTimers(MM::PropertyBase* pProp, MM::ActionType eAct, long timer){
	if (eAct == MM::BeforeGet){
		getTimers(&psutime_, &laserenabledtime_, &laseroperationtime_);

		if(timer == 0){ // PSU time
			pProp->Set(psutime_);
		} else if (timer == 1){ // Laser enabled time
			pProp->Set(laserenabledtime_);
		} else { // Laser operation time
			pProp->Set(laseroperationtime_);
		}
	}
	return DEVICE_OK;
}

//---------------------------------------------------------------------------
// Action handlers
//---------------------------------------------------------------------------

int LaserQuantumLaser::OnLaserOnOff(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet){
		int ret = getStatus(&status_);
		if (ret != DEVICE_OK) 
			return ret;

		if(status_){
			pProp->Set(ON);
		} else {
			pProp->Set(OFF);
		}
	} else if (eAct == MM::AfterSet){
		std::string status;
		pProp->Get(status);

		if(status.compare(ON) == 0){
			status_ = true;
		} else {
			status_ = false;
		}

		int ret = setLaserOnOff(status_);
		if (ret != DEVICE_OK) 
			return ret;
	}

	return DEVICE_OK;
}

int LaserQuantumLaser::OnControlMode(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet){
		int ret = getControlMode(&controlmode_);
		if (ret != DEVICE_OK) 
			return ret;

		if(controlmode_){
			pProp->Set(POWER);
		} else {
			pProp->Set(CURRENT);
		}
	} else if (eAct == MM::AfterSet){
		std::string mode;
		pProp->Get(mode);

		if(mode.compare(POWER) == 0){
			controlmode_ = true;
		} else {
			controlmode_ = false;
		}

		int ret = setControlMode(controlmode_);
		if (ret != DEVICE_OK) 
			return ret;
	}

	return DEVICE_OK;
}

int LaserQuantumLaser::OnCurrent(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet){
		int ret = getCurrent(&current_);
		if (ret != DEVICE_OK) 
			return ret;

		pProp->Set(current_);

	} else if (eAct == MM::AfterSet){

		getControlMode(&controlmode_);
		if(!controlmode_){ // if in current control mode

			double current;
			pProp->Get(current);

			if(current>=0 && current<=100){ // sanity check
				current_ = current;
				setCurrent(current_);
			}
		}

	}

	return DEVICE_OK;
}

int LaserQuantumLaser::OnPower(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet){
		int ret = getPower(&power_);
		if (ret != DEVICE_OK) 
			return ret;

		pProp->Set(power_);

	} else if (eAct == MM::AfterSet){

		getControlMode(&controlmode_);
		if(controlmode_){ // if in power control mode

			double power;
			pProp->Get(power);

			if(power>=0 && power<=maxpower_){ // sanity check
				power_ = power;
				setPower(power_);
			}
		}

	}

	return DEVICE_OK;
}