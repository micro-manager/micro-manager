//-----------------------------------------------------------------------------
// FILE:          Thorlabs_ElliptecSlider.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Controls the Elliptec sliders ELL6, ELL9 and ELL17_20
// COPYRIGHT:     EMBL
// LICENSE:       LGPL
// AUTHOR:        Joran Deschamps and Anindita Dasgupta, EMBL
//-----------------------------------------------------------------------------

#include "ThorlabsElliptecSlider.h"
#include <iostream>
#include <fstream>
#include <string>
#include <sstream>
#include <cstring>

const char* g_ELL17_20 = "Thorlabs ELL17/ELL20";
const char* g_ELL9 = "Thorlabs ELL9";
const char* g_ELL6 = "Thorlabs ELL6";
const char* g_ELL6_shutter = "Thorlabs ELL6 shutter";
const char* g_pos0 = "00000000";
const char* g_pos1 = "0000001F";
const char* g_pos2 = "0000003E";
const char* g_pos3 = "0000005D";
const char* g_fw = "fw";
const char* g_bw = "bw";

//-----------------------------------------------------------------------------
// MMDevice API
//-----------------------------------------------------------------------------

MODULE_API void InitializeModuleData()
{
	RegisterDevice(g_ELL17_20, MM::StageDevice, g_ELL17_20);
	RegisterDevice(g_ELL9, MM::StateDevice, g_ELL9);
	RegisterDevice(g_ELL6, MM::StateDevice, g_ELL6);
	RegisterDevice(g_ELL6_shutter, MM::ShutterDevice, g_ELL6_shutter);
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
	if (deviceName == 0)
		return 0;

	if (strcmp(deviceName, g_ELL6) == 0){
		return new ELL6();
	} else if (strcmp(deviceName, g_ELL6_shutter) == 0){
		return new ELL6_shutter();
	} else if (strcmp(deviceName, g_ELL9) == 0){
		return new ELL9();
	} else if (strcmp(deviceName, g_ELL17_20) == 0){
		return new ELL17_20();
	}

	return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
	delete pDevice;
}


//-----------------------------------------------------------------------------
// ELL17_20 device adapter
//-----------------------------------------------------------------------------

ELL17_20::ELL17_20():
	port_("Undefined"),
	channel_("0"),
	initialized_(false),
	busy_(false),
	travelRange_(60),
	pulsesPerMU_(1024.)
{
	InitializeDefaultErrorMessages();
	
	SetErrorText(ERR_PORT_CHANGE_FORBIDDEN, "Port change is forbidden.");
	SetErrorText(ERR_UNEXPECTED_ANSWER, "The device returned an unexpected answer.");
	SetErrorText(ERR_WRONG_DEVICE, "The device is not an ELL17 or ELL20.");
	SetErrorText(ERR_FORBIDDEN_POSITION_REQUESTED, "Forbidden position requested (out of range).");
	SetErrorText(ERR_UNKNOWN_STATE, "Unknown state.");
	
	SetErrorText(ERR_COMMUNICATION_TIME_OUT, "Communication time-out. Is the channel set correctly?");
	SetErrorText(ERR_MECHANICAL_TIME_OUT, "Mechanical time-out.");
	SetErrorText(ERR_COMMAND_ERROR_OR_NOT_SUPPORTED, "Unsupported or unknown command.");
	SetErrorText(ERR_VALUE_OUT_OF_RANGE, "Value out of range.");
	SetErrorText(ERR_MODULE_ISOLATED, "Module isolated.");
	SetErrorText(ERR_MODULE_OUT_OF_ISOLATION, "Module out of isolation.");
	SetErrorText(ERR_INITIALIZING_ERROR, "Initializing error.");
	SetErrorText(ERR_THERMAL_ERROR, "Thermal error.");
	SetErrorText(ERR_BUSY, "Busy.");
	SetErrorText(ERR_SENSOR_ERROR, "Sensor error.");
	SetErrorText(ERR_MOTOR_ERROR, "Motor error.");
	SetErrorText(ERR_OUT_OF_RANGE, "Out of range.");
	SetErrorText(ERR_OVER_CURRENT_ERROR, "Over-current error.");
	SetErrorText(ERR_UNKNOWN_ERROR, "Unknown error (error code >13).");
	
	// Description
	CreateProperty(MM::g_Keyword_Description, "Thorlabs Elliptec Linear Stage ELL17/ELL20", MM::String, true);

	// Port
	CPropertyAction* pAct = new CPropertyAction (this, &ELL17_20::OnPort);
	CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

	// Channel
	std::string channels[] = {"0","1","2","3","4","5","6","7","8","9","A","B","C","D","E","F"};
	std::vector<std::string> channels_vec;
	for(int i=0;i<16;i++){
		channels_vec.push_back(channels[i]);
	}

	pAct = new CPropertyAction (this, &ELL17_20::OnChannel);
	CreateProperty("Channel", "0", MM::String, false, pAct, true);
	SetAllowedValues("Channel", channels_vec);
}

ELL17_20::~ELL17_20()
{
	Shutdown();
}

void ELL17_20::GetName(char* Name) const
{
	CDeviceUtils::CopyLimitedString(Name, g_ELL17_20);
}

int ELL17_20::Initialize()
{
	// ID
	std::string id;
	getID(&id, &travelRange_, &pulsesPerMU_);
	int nRet = CreateProperty("ID", id.c_str(), MM::String, true);
	if (nRet != DEVICE_OK)
		return nRet;

	// Position
	CPropertyAction* pAct = new CPropertyAction(this, &ELL17_20::OnPosition);
	nRet = CreateProperty("Position (um)", "0", MM::Integer, false, pAct);
	if (nRet != DEVICE_OK)
		return nRet;
	SetPropertyLimits("Position (um)", 0, 1000*travelRange_);

	initialized_ = true;
	return DEVICE_OK;
}

int ELL17_20::Shutdown()
{
	if (initialized_){
		initialized_ = false;	 
	}
	return DEVICE_OK;
}

bool ELL17_20::Busy(){
	std::ostringstream command;
	command << channel_ << "gs";

	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
	if (ret != DEVICE_OK) 
		return true;

	std::string answer;
	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK)
		return true;
	
	// remove "\n" if start character
	std::string message = removeLineFeed(answer);

	// errors are sent with the "0GS" command, so we can use the same function (channel = 0)
	int code = getErrorCode(message);

	if(code == 0){ // code "0" corresponds to no-error, "9" to busy
		return false;
	} 

	return true;
}

//---------------------------------------------------------------------------
// Setters
//---------------------------------------------------------------------------
int ELL17_20::SetPositionUm(double pos){
	
	std::ostringstream command;
	command << channel_ << "ma";
	
	// convert to mm and multiply by the pulses per mm  (rounding to nearest integer)
	int val = (int) (pulsesPerMU_ * pos / 1000. + 0.5);

	// complete the missing bytes and add to the command
	command << positionFromValue(val);
	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
	if (ret != DEVICE_OK) 
		return ret;

	// get confirmation of the new position
	std::string answer;
	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK)
		return ret;

	// remove "\n" if start character
	std::string message = removeLineFeed(answer);

	// check for error
	if(isError(message)){
		return getErrorCode(message);
	}

	return DEVICE_OK;
}

//---------------------------------------------------------------------------
// Getters
//---------------------------------------------------------------------------
int ELL17_20::getID(std::string* id, int* travelRange, double* pulsesPerMU){
	std::ostringstream command;
	command << channel_ << "in";

	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
	if (ret != DEVICE_OK) 
		return ret;

	std::string answer;
	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK)
		return ret;

	// remove "\n" if start character
	std::string message = removeLineFeed(answer);

	// check if returned an error
	if(isError(message))
		return getErrorCode(message);

	// check if it is the expected answer
	if(message.substr(1,2).compare("IN") != 0)
		return ERR_UNEXPECTED_ANSWER;

	// check if it is neither ELL17 nor ELL20 (hex values)
	if(message.substr(3,2).compare("14") != 0 && message.substr(3,2).compare("11") != 0)
		return ERR_WRONG_DEVICE;

	// retrieve id, travel range and pulse per measurement units
	*id = message.substr(3,15); // module + serial + year + firmware
	*travelRange = positionFromHex(message.substr(21,4));
	*pulsesPerMU = positionFromHex(message.substr(25,8));

	return DEVICE_OK;
}


int ELL17_20::GetPositionUm(double& pos){
	std::ostringstream command;
	command << channel_ << "gp";

	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
	if (ret != DEVICE_OK) 
		return ret;

	std::string answer;
	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK)
		return ret;

	// remove "\n" if start character
	std::string message = removeLineFeed(answer);

	// check for error
	if(isError(message)){
		return getErrorCode(message);
	}

	// check if it is the expected answer
	if(message.substr(1,2).compare("PO") != 0)
		return ERR_UNEXPECTED_ANSWER;

	std::string position = removeCommandFlag(message);

	// convert back to int (rounding to nearest integer)
	int int_pos = (int) (1000. * positionFromHex(position) / pulsesPerMU_ + 0.5);

	// if it is negative, then we zero it
	if(int_pos == 0){
		int_pos = 0;
	}
	pos = (double) int_pos;

	return DEVICE_OK;
}


//---------------------------------------------------------------------------
// Convenience functions
//---------------------------------------------------------------------------
std::string ELL17_20::positionFromValue(int pos){
	char hex_string[8]; // maximum expected size is 5

	// convert to hex
	sprintf(hex_string, "%X", pos);
	
	// get size
	std::string s;
	std::stringstream ss;
	ss << hex_string;
	s = ss.str();
	int size = s.length();

	// add missing characters (need 8 bytes command)
	std::stringstream ss_pos;
	for(int i=0;i<8-size;i++){
		ss_pos << "0";
	}
	ss_pos << s;

	return ss_pos.str();
}

int ELL17_20::positionFromHex(std::string pos){
	int n;

	// convert to int
	sscanf(pos.c_str(), "%x", &n);
	
	return n;
}


std::string ELL17_20::removeLineFeed(std::string answer){
	std::string message;
	if(answer.substr(0,1).compare("\n") == 0){
		message = answer.substr(1,answer.length()-1);
	} else {
		message = answer;
	}

	return message;
}

std::string ELL17_20::removeCommandFlag(std::string message){
	std::string value = message.substr(3,message.length()-3);
	return value;
}

bool ELL17_20::isError(std::string message){
	if(message.substr(1,2).compare("GS") == 0){
		return true;
	}
	return false;
}

int ELL17_20::getErrorCode(std::string message){
	std::string code = removeCommandFlag(message);

	if(code.compare("00") == 0){
		return DEVICE_OK;
	} else if(code.compare("01") == 0){
		return ERR_COMMUNICATION_TIME_OUT;
	} else if(code.compare("02") == 0){
		return ERR_MECHANICAL_TIME_OUT;
	} else if(message.compare("03") == 0){
		return ERR_COMMAND_ERROR_OR_NOT_SUPPORTED;
	} else if(message.compare("04") == 0){
		return ERR_VALUE_OUT_OF_RANGE;
	} else if(message.compare("05") == 0){
		return ERR_MODULE_ISOLATED;
	} else if(message.compare("06") == 0){
		return ERR_MODULE_OUT_OF_ISOLATION;
	} else if(message.compare("07") == 0){
		return ERR_INITIALIZING_ERROR;
	} else if(message.compare("08") == 0){
		return ERR_THERMAL_ERROR;
	} else if(message.compare("09") == 0){
		return ERR_BUSY;
	} else if(message.compare("0A") == 0){
		return ERR_SENSOR_ERROR;
	} else if(message.compare("0B") == 0){
		return ERR_MOTOR_ERROR;
	} else if(message.compare("0C") == 0){
		return ERR_OUT_OF_RANGE;
	} else if(message.compare("0D") == 0){
		return ERR_OVER_CURRENT_ERROR;
	}

	return ERR_UNKNOWN_ERROR;
}

//---------------------------------------------------------------------------
// Action handlers
//---------------------------------------------------------------------------

int ELL17_20::OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet){ 
		double pos;
		int ret = GetPositionUm(pos);
		if(ret != DEVICE_OK)
			return ret;
		
		std::stringstream ss;
		ss << pos;

		pProp->Set(ss.str().c_str());

	} else if (eAct == MM::AfterSet){
		double pos;
		pProp->Get(pos);
		int ret = SetPositionUm(pos);
		if(ret != DEVICE_OK)
			return ret;
	}

	return DEVICE_OK;
}

int ELL17_20::OnPort(MM::PropertyBase* pProp , MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		pProp->Set(port_.c_str());
	}
	else if (eAct == MM::AfterSet)
	{
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

int ELL17_20::OnChannel(MM::PropertyBase* pProp , MM::ActionType eAct)
{
	if (eAct == MM::AfterSet)
	{
		std::string channel;
		pProp->Get(channel);

		channel_ = channel;
	}

	return DEVICE_OK;
}


//-----------------------------------------------------------------------------
// ELL9 device adapter
//-----------------------------------------------------------------------------

ELL9::ELL9():
	port_("Undefined"),
	numPos_(4),
	channel_("0"),
	initialized_(false),
	busy_(false)
{
	InitializeDefaultErrorMessages();
	
	SetErrorText(ERR_PORT_CHANGE_FORBIDDEN, "Port change is forbidden.");
	SetErrorText(ERR_UNEXPECTED_ANSWER, "The device returned an unexpected answer.");
	SetErrorText(ERR_WRONG_DEVICE, "The device is not an ELL9.");
	SetErrorText(ERR_FORBIDDEN_POSITION_REQUESTED, "Forbidden position requested (allowed: 0, 1, 2 and 3).");
	SetErrorText(ERR_UNKNOWN_STATE, "Unknown state.");
	
	SetErrorText(ERR_COMMUNICATION_TIME_OUT, "Communication time-out. Is the channel set correctly?");
	SetErrorText(ERR_MECHANICAL_TIME_OUT, "Mechanical time-out.");
	SetErrorText(ERR_COMMAND_ERROR_OR_NOT_SUPPORTED, "Unsupported or unknown command.");
	SetErrorText(ERR_VALUE_OUT_OF_RANGE, "Value out of range.");
	SetErrorText(ERR_MODULE_ISOLATED, "Module isolated.");
	SetErrorText(ERR_MODULE_OUT_OF_ISOLATION, "Module out of isolation.");
	SetErrorText(ERR_INITIALIZING_ERROR, "Initializing error.");
	SetErrorText(ERR_THERMAL_ERROR, "Thermal error.");
	SetErrorText(ERR_BUSY, "Busy.");
	SetErrorText(ERR_SENSOR_ERROR, "Sensor error.");
	SetErrorText(ERR_MOTOR_ERROR, "Motor error.");
	SetErrorText(ERR_OUT_OF_RANGE, "Out of range.");
	SetErrorText(ERR_OVER_CURRENT_ERROR, "Over-current error.");
	SetErrorText(ERR_UNKNOWN_ERROR, "Unknown error (error code >13).");
	
	// Description
	CreateProperty(MM::g_Keyword_Description, "Thorlabs Elliptec 4-position Slider ELL9", MM::String, true);

	// Port
	CPropertyAction* pAct = new CPropertyAction (this, &ELL9::OnPort);
	CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

	// Channel
	std::string channels[] = {"0","1","2","3","4","5","6","7","8","9","A","B","C","D","E","F"};
	std::vector<std::string> channels_vec;
	for(int i=0;i<16;i++){
		channels_vec.push_back(channels[i]);
	}

	pAct = new CPropertyAction (this, &ELL9::OnChannel);
	CreateProperty("Channel", "0", MM::String, false, pAct, true);
	SetAllowedValues("Channel", channels_vec);
}

ELL9::~ELL9()
{
	Shutdown();
}

void ELL9::GetName(char* Name) const
{
	CDeviceUtils::CopyLimitedString(Name, g_ELL9);
}

int ELL9::Initialize()
{
	// ID
	std::string id;
	getID(&id);
	int nRet = CreateProperty("ID", id.c_str(), MM::String, true);
	if (nRet != DEVICE_OK)
		return nRet;

	// State
	CPropertyAction* pAct = new CPropertyAction(this, &ELL9::OnState);
	nRet = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
	if (nRet != DEVICE_OK)
		return nRet;

	// Label
	pAct = new CPropertyAction (this, &CStateBase::OnLabel);
	nRet = CreateProperty(MM::g_Keyword_Label, "Undefined", MM::String, false, pAct);
	if (nRet != DEVICE_OK)
		return nRet;

	for(int i=0; i<numPos_; i++){
		std::stringstream ss;
		ss << i;
		AddAllowedValue(MM::g_Keyword_State, ss.str().c_str());

		std::ostringstream label;
		label << "Position " << i;
		SetPositionLabel(i,label.str().c_str());
	}

	initialized_ = true;
	return DEVICE_OK;
}

int ELL9::Shutdown()
{
	if (initialized_){
		initialized_ = false;	 
	}
	return DEVICE_OK;
}

bool ELL9::Busy(){
	std::ostringstream command;
	command << channel_ << "gs";

	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
	if (ret != DEVICE_OK) 
		return true;

	std::string answer;
	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK)
		return true;
	
	// remove "\n" if start character
	std::string message = removeLineFeed(answer);

	// errors are sent with the "0GS" command, so we can use the same function (channel = 0)
	int code = getErrorCode(message);

	if(code == 0){ // code "0" corresponds to no-error, "9" to busy
		return false;
	} 

	return true;
}

//---------------------------------------------------------------------------
// Setters
//---------------------------------------------------------------------------
int ELL9::setState(int state){
	
	std::ostringstream command;
	command << channel_ << "ma";
	
	std::string pos;
	switch(state){ // positions ex
	case 0:
		pos = g_pos0;
		break;
	case 1:
		pos = g_pos1;
		break;
	case 2:
		pos = g_pos2;
		break;
	case 3:
		pos = g_pos3;
		break;
	default:
		return ERR_FORBIDDEN_POSITION_REQUESTED;
	}

	command << pos;
	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
	if (ret != DEVICE_OK) 
		return ret;

	// get confirmation of the new position
	std::string answer;
	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK)
		return ret;

	// remove "\n" if start character
	std::string message = removeLineFeed(answer);

	// check for error
	if(isError(message)){
		return getErrorCode(message);
	}

	return DEVICE_OK;
}

//---------------------------------------------------------------------------
// Getters
//---------------------------------------------------------------------------
int ELL9::getID(std::string* id){
	std::ostringstream command;
	command << channel_ << "in";

	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
	if (ret != DEVICE_OK) 
		return ret;

	std::string answer;
	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK)
		return ret;

	// remove "\n" if start character
	std::string message = removeLineFeed(answer);

	// check if returned an error
	if(isError(message))
		return getErrorCode(message);

	// check if it is the expected answer
	if(message.substr(1,2).compare("IN") != 0)
		return ERR_UNEXPECTED_ANSWER;

	// check if ELL9 (hex)
	if(message.substr(3,2).compare("09") != 0)
		return ERR_WRONG_DEVICE;
		
	*id = message.substr(3,15); // module + serial + year + firmware
	
	return DEVICE_OK;
}


int ELL9::getState(int* state){
	std::ostringstream command;
	command << channel_ << "gp";

	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
	if (ret != DEVICE_OK) 
		return ret;

	std::string answer;
	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK)
		return ret;
	
	// remove "\n" if start character
	std::string message = removeLineFeed(answer);

	// check for error
	if(isError(message)){
		return getErrorCode(message);
	}

	// check if it is the expected answer
	if(message.substr(1,2).compare("PO") != 0)
		return ERR_UNEXPECTED_ANSWER;

	std::string position = removeCommandFlag(message);
	if(position.compare(g_pos0) == 0){
		*state = 0;
	} else if(position.compare(g_pos1) == 0){
		*state = 1;
	} else if(position.compare(g_pos2) == 0){
		*state = 2;
	} else if(position.compare(g_pos3) == 0){
		*state = 3;
	} else {
		return ERR_UNKNOWN_STATE;
	}

	return DEVICE_OK;
}


//---------------------------------------------------------------------------
// Convenience function
//---------------------------------------------------------------------------
std::string ELL9::removeLineFeed(std::string answer){
	std::string message;
	if(answer.substr(0,1).compare("\n") == 0){
		message = answer.substr(1,answer.length()-1);
	} else {
		message = answer;
	}

	return message;
}

std::string ELL9::removeCommandFlag(std::string message){
	std::string value = message.substr(3,message.length()-3);
	return value;
}

bool ELL9::isError(std::string message){
	if(message.substr(1,2).compare("GS") == 0){
		return true;
	}
	return false;
}

int ELL9::getErrorCode(std::string message){
	std::string code = removeCommandFlag(message);

	if(code.compare("00") == 0){
		return DEVICE_OK;
	} else if(code.compare("01") == 0){
		return ERR_COMMUNICATION_TIME_OUT;
	} else if(code.compare("02") == 0){
		return ERR_MECHANICAL_TIME_OUT;
	} else if(message.compare("03") == 0){
		return ERR_COMMAND_ERROR_OR_NOT_SUPPORTED;
	} else if(message.compare("04") == 0){
		return ERR_VALUE_OUT_OF_RANGE;
	} else if(message.compare("05") == 0){
		return ERR_MODULE_ISOLATED;
	} else if(message.compare("06") == 0){
		return ERR_MODULE_OUT_OF_ISOLATION;
	} else if(message.compare("07") == 0){
		return ERR_INITIALIZING_ERROR;
	} else if(message.compare("08") == 0){
		return ERR_THERMAL_ERROR;
	} else if(message.compare("09") == 0){
		return ERR_BUSY;
	} else if(message.compare("0A") == 0){
		return ERR_SENSOR_ERROR;
	} else if(message.compare("0B") == 0){
		return ERR_MOTOR_ERROR;
	} else if(message.compare("0C") == 0){
		return ERR_OUT_OF_RANGE;
	} else if(message.compare("0D") == 0){
		return ERR_OVER_CURRENT_ERROR;
	}

	return ERR_UNKNOWN_ERROR;
}

//---------------------------------------------------------------------------
// Action handlers
//---------------------------------------------------------------------------

int ELL9::OnState(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet){ 
		int state;
		int ret = getState(&state);
		if(ret != DEVICE_OK)
			return ret;
		
		std::stringstream ss;
		ss << state;

		pProp->Set(ss.str().c_str());

	} else if (eAct == MM::AfterSet){
		long state;
		pProp->Get(state);
		int ret = setState(state);
		if(ret != DEVICE_OK)
			return ret;
	}

	return DEVICE_OK;
}

int ELL9::OnPort(MM::PropertyBase* pProp , MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		pProp->Set(port_.c_str());
	}
	else if (eAct == MM::AfterSet)
	{
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

int ELL9::OnChannel(MM::PropertyBase* pProp , MM::ActionType eAct)
{
	if (eAct == MM::AfterSet)
	{
		std::string channel;
		pProp->Get(channel);

		channel_ = channel;
	}

	return DEVICE_OK;
}


//-----------------------------------------------------------------------------
// ELL6 device adapter
//-----------------------------------------------------------------------------

ELL6::ELL6():
	port_("Undefined"),
	numPos_(2),
	channel_("0"),
	initialized_(false),
	busy_(false)
{
	InitializeDefaultErrorMessages();
	
	SetErrorText(ERR_PORT_CHANGE_FORBIDDEN, "Port change is forbidden.");
	SetErrorText(ERR_UNEXPECTED_ANSWER, "The device returned an unexpected answer.");
	SetErrorText(ERR_WRONG_DEVICE, "The device is not an ELL6.");
	SetErrorText(ERR_FORBIDDEN_POSITION_REQUESTED, "Forbidden position requested (allowed: 0, 1, 2 and 3).");
	SetErrorText(ERR_UNKNOWN_STATE, "Unknown state.");
	
	SetErrorText(ERR_COMMUNICATION_TIME_OUT, "Communication time-out. Is the channel set correctly?");
	SetErrorText(ERR_MECHANICAL_TIME_OUT, "Mechanical time-out.");
	SetErrorText(ERR_COMMAND_ERROR_OR_NOT_SUPPORTED, "Unsupported or unknown command.");
	SetErrorText(ERR_VALUE_OUT_OF_RANGE, "Value out of range.");
	SetErrorText(ERR_MODULE_ISOLATED, "Module isolated.");
	SetErrorText(ERR_MODULE_OUT_OF_ISOLATION, "Module out of isolation.");
	SetErrorText(ERR_INITIALIZING_ERROR, "Initializing error.");
	SetErrorText(ERR_THERMAL_ERROR, "Thermal error.");
	SetErrorText(ERR_BUSY, "Busy.");
	SetErrorText(ERR_SENSOR_ERROR, "Sensor error.");
	SetErrorText(ERR_MOTOR_ERROR, "Motor error.");
	SetErrorText(ERR_OUT_OF_RANGE, "Out of range.");
	SetErrorText(ERR_OVER_CURRENT_ERROR, "Over-current error.");
	SetErrorText(ERR_UNKNOWN_ERROR, "Unknown error (error code >13).");
	
	// Description
	CreateProperty(MM::g_Keyword_Description, "Thorlabs Elliptec 2-position Slider ELL6", MM::String, true);

	// Port
	CPropertyAction* pAct = new CPropertyAction (this, &ELL6::OnPort);
	CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

	// Channel
	std::string channels[] = {"0","1","2","3","4","5","6","7","8","9","A","B","C","D","E","F"};
	std::vector<std::string> channels_vec;
	for(int i=0;i<16;i++){
		channels_vec.push_back(channels[i]);
	}

	pAct = new CPropertyAction (this, &ELL6::OnChannel);
	CreateProperty("Channel", "0", MM::String, false, pAct, true);
	SetAllowedValues("Channel", channels_vec);
}

ELL6::~ELL6()
{
	Shutdown();
}

void ELL6::GetName(char* Name) const
{
	CDeviceUtils::CopyLimitedString(Name, g_ELL6);
}

int ELL6::Initialize()
{
	// ID
	std::string id;
	getID(&id);
	int nRet = CreateProperty("ID", id.c_str(), MM::String, true);
	if (nRet != DEVICE_OK)
		return nRet;

	// State
	CPropertyAction* pAct = new CPropertyAction(this, &ELL6::OnState);
	nRet = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
	if (nRet != DEVICE_OK)
		return nRet;

	// Label
	pAct = new CPropertyAction (this, &CStateBase::OnLabel);
	nRet = CreateProperty(MM::g_Keyword_Label, "Undefined", MM::String, false, pAct);
	if (nRet != DEVICE_OK)
		return nRet;

	for(int i=0; i<numPos_; i++){
		std::stringstream ss;
		ss << i;
		AddAllowedValue(MM::g_Keyword_State, ss.str().c_str());

		std::ostringstream label;
		label << "Position " << i;
		SetPositionLabel(i,label.str().c_str());
	}

	initialized_ = true;
	return DEVICE_OK;
}

int ELL6::Shutdown()
{
	if (initialized_){
		initialized_ = false;	 
	}
	return DEVICE_OK;
}

bool ELL6::Busy(){
	std::ostringstream command;
	command << channel_ << "gs";

	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
	if (ret != DEVICE_OK) 
		return true;

	std::string answer;
	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK)
		return true;
	
	// remove "\n" if start character
	std::string message = removeLineFeed(answer);

	// errors are sent with the "0GS" command, so we can use the same function (channel = 0)
	int code = getErrorCode(message);

	if(code == 0){ // code "0" corresponds to no-error, "9" to busy
		return false;
	} 

	return true;
}

//---------------------------------------------------------------------------
// Setters
//---------------------------------------------------------------------------
int ELL6::setState(int state){
	
	std::ostringstream command;
	command << channel_;
	
	std::string pos;
	switch(state){ // positions ex
	case 0:
		pos = g_bw;
		break;
	case 1:
		pos = g_fw;
		break;
	default:
		return ERR_FORBIDDEN_POSITION_REQUESTED;
	}

	command << pos;
	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
	if (ret != DEVICE_OK) 
		return ret;

	// get confirmation of the new position
	std::string answer;
	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK)
		return ret;

	// remove "\n" if start character
	std::string message = removeLineFeed(answer);

	// check for error
	if(isError(message)){
		return getErrorCode(message);
	}

	return DEVICE_OK;
}

//---------------------------------------------------------------------------
// Getters
//---------------------------------------------------------------------------
int ELL6::getID(std::string* id){
	std::ostringstream command;
	command << channel_ << "in";

	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
	if (ret != DEVICE_OK) 
		return ret;

	std::string answer;
	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK)
		return ret;

	// remove "\n" if start character
	std::string message = removeLineFeed(answer);

	// check if returned an error
	if(isError(message))
		return getErrorCode(message);

	// check if it is the expected answer
	if(message.substr(1,2).compare("IN") != 0)
		return ERR_UNEXPECTED_ANSWER;

	// check if ELL6 (in hex)
	if(message.substr(3,2).compare("06") != 0)
		return ERR_WRONG_DEVICE;
		
	*id = message.substr(3,15); // module + serial + year + firmware
	
	return DEVICE_OK;
}


int ELL6::getState(int* state){
	std::ostringstream command;
	command << channel_ << "gp";

	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
	if (ret != DEVICE_OK) 
		return ret;

	std::string answer;
	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK)
		return ret;
	
	// remove "\n" if start character
	std::string message = removeLineFeed(answer);

	// check for error
	if(isError(message)){
		return getErrorCode(message);
	}

	// check if it is the expected answer
	if(message.substr(1,2).compare("PO") != 0)
		return ERR_UNEXPECTED_ANSWER;

	std::string position = removeCommandFlag(message);
	if(position.compare(g_pos0) == 0){
		*state = 0;
	} else if(position.compare(g_pos1) == 0){
		*state = 1;
	} else {
		return ERR_UNKNOWN_STATE;
	}

	return DEVICE_OK;
}


//---------------------------------------------------------------------------
// Convenience function
//---------------------------------------------------------------------------
std::string ELL6::removeLineFeed(std::string answer){
	std::string message;
	if(answer.substr(0,1).compare("\n") == 0){
		message = answer.substr(1,answer.length()-1);
	} else {
		message = answer;
	}

	return message;
}

std::string ELL6::removeCommandFlag(std::string message){
	std::string value = message.substr(3,message.length()-3);
	return value;
}

bool ELL6::isError(std::string message){
	if(message.substr(1,2).compare("GS") == 0){
		return true;
	}
	return false;
}

int ELL6::getErrorCode(std::string message){
	std::string code = removeCommandFlag(message);

	if(code.compare("00") == 0){
		return DEVICE_OK;
	} else if(code.compare("01") == 0){
		return ERR_COMMUNICATION_TIME_OUT;
	} else if(code.compare("02") == 0){
		return ERR_MECHANICAL_TIME_OUT;
	} else if(message.compare("03") == 0){
		return ERR_COMMAND_ERROR_OR_NOT_SUPPORTED;
	} else if(message.compare("04") == 0){
		return ERR_VALUE_OUT_OF_RANGE;
	} else if(message.compare("05") == 0){
		return ERR_MODULE_ISOLATED;
	} else if(message.compare("06") == 0){
		return ERR_MODULE_OUT_OF_ISOLATION;
	} else if(message.compare("07") == 0){
		return ERR_INITIALIZING_ERROR;
	} else if(message.compare("08") == 0){
		return ERR_THERMAL_ERROR;
	} else if(message.compare("09") == 0){
		return ERR_BUSY;
	} else if(message.compare("0A") == 0){
		return ERR_SENSOR_ERROR;
	} else if(message.compare("0B") == 0){
		return ERR_MOTOR_ERROR;
	} else if(message.compare("0C") == 0){
		return ERR_OUT_OF_RANGE;
	} else if(message.compare("0D") == 0){
		return ERR_OVER_CURRENT_ERROR;
	}

	return ERR_UNKNOWN_ERROR;
}

//---------------------------------------------------------------------------
// Action handlers
//---------------------------------------------------------------------------

int ELL6::OnState(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet){ 
		int state;
		int ret = getState(&state);
		if(ret != DEVICE_OK)
			return ret;
		
		std::stringstream ss;
		ss << state;

		pProp->Set(ss.str().c_str());

	} else if (eAct == MM::AfterSet){
		long state;
		pProp->Get(state);
		int ret = setState(state);
		if(ret != DEVICE_OK)
			return ret;
	}

	return DEVICE_OK;
}

int ELL6::OnPort(MM::PropertyBase* pProp , MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		pProp->Set(port_.c_str());
	}
	else if (eAct == MM::AfterSet)
	{
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

int ELL6::OnChannel(MM::PropertyBase* pProp , MM::ActionType eAct)
{
	if (eAct == MM::AfterSet)
	{
		std::string channel;
		pProp->Get(channel);

		channel_ = channel;
	}

	return DEVICE_OK;
}


//-----------------------------------------------------------------------------
// ELL6 as shutter
//-----------------------------------------------------------------------------

ELL6_shutter::ELL6_shutter():
	port_("Undefined"),
	numPos_(2),
	channel_("0"),
	initialized_(false),
	busy_(false)
{
	InitializeDefaultErrorMessages();
	
	SetErrorText(ERR_PORT_CHANGE_FORBIDDEN, "Port change is forbidden.");
	SetErrorText(ERR_UNEXPECTED_ANSWER, "The device returned an unexpected answer.");
	SetErrorText(ERR_WRONG_DEVICE, "The device is not an ELL6.");
	SetErrorText(ERR_FORBIDDEN_POSITION_REQUESTED, "Forbidden position requested (allowed: 0, 1, 2 and 3).");
	SetErrorText(ERR_UNKNOWN_STATE, "Unknown state.");
	
	SetErrorText(ERR_COMMUNICATION_TIME_OUT, "Communication time-out. Is the channel set correctly?");
	SetErrorText(ERR_MECHANICAL_TIME_OUT, "Mechanical time-out.");
	SetErrorText(ERR_COMMAND_ERROR_OR_NOT_SUPPORTED, "Unsupported or unknown command.");
	SetErrorText(ERR_VALUE_OUT_OF_RANGE, "Value out of range.");
	SetErrorText(ERR_MODULE_ISOLATED, "Module isolated.");
	SetErrorText(ERR_MODULE_OUT_OF_ISOLATION, "Module out of isolation.");
	SetErrorText(ERR_INITIALIZING_ERROR, "Initializing error.");
	SetErrorText(ERR_THERMAL_ERROR, "Theromal error.");
	SetErrorText(ERR_BUSY, "Busy.");
	SetErrorText(ERR_SENSOR_ERROR, "Sensor error.");
	SetErrorText(ERR_MOTOR_ERROR, "Motor error.");
	SetErrorText(ERR_OUT_OF_RANGE, "Out of range.");
	SetErrorText(ERR_OVER_CURRENT_ERROR, "Over-current error.");
	SetErrorText(ERR_UNKNOWN_ERROR, "Unknown error (error code >13).");
	
	// Description
	CreateProperty(MM::g_Keyword_Description, "Thorlabs Elliptec 2-position Slider ELL6 as shutter", MM::String, true);

	// Port
	CPropertyAction* pAct = new CPropertyAction(this, &ELL6_shutter::OnPort);
	CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

	// Channel
	std::string channels[] = {"0","1","2","3","4","5","6","7","8","9","A","B","C","D","E","F"};
	std::vector<std::string> channels_vec;
	for(int i=0;i<16;i++){
		channels_vec.push_back(channels[i]);
	}

	pAct = new CPropertyAction(this, &ELL6_shutter::OnChannel);
	CreateProperty("Channel", "0", MM::String, false, pAct, true);
	SetAllowedValues("Channel", channels_vec);
}

ELL6_shutter::~ELL6_shutter()
{
	Shutdown();
}

void ELL6_shutter::GetName(char* Name) const
{
	CDeviceUtils::CopyLimitedString(Name, g_ELL6_shutter);
}

int ELL6_shutter::Initialize()
{
	// ID
	std::string id;
	getID(&id);
	int nRet = CreateProperty("ID", id.c_str(), MM::String, true);
	if (nRet != DEVICE_OK)
		return nRet;

	// State
	CPropertyAction* pAct = new CPropertyAction(this, &ELL6_shutter::OnState);
	nRet = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
	if (nRet != DEVICE_OK)
		return nRet;

	for(int i=0; i<numPos_; i++){
		std::stringstream ss;
		ss << i;
		AddAllowedValue(MM::g_Keyword_State, ss.str().c_str());
	}

	initialized_ = true;
	return DEVICE_OK;
}

int ELL6_shutter::Shutdown()
{
	if (initialized_){
		initialized_ = false;	 
	}
	return DEVICE_OK;
}

bool ELL6_shutter::Busy(){
	std::ostringstream command;
	command << channel_ << "gs";

	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
	if (ret != DEVICE_OK) 
		return true;

	std::string answer;
	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK)
		return true;
	
	// remove "\n" if start character
	std::string message = removeLineFeed(answer);

	// errors are sent with the "0GS" command, so we can use the same function (channel = 0)
	int code = getErrorCode(message);

	if(code == 0){ // code "0" corresponds to no-error, "9" to busy
		return false;
	} 

	return true;
}

//---------------------------------------------------------------------------
// Setters
//---------------------------------------------------------------------------
int ELL6_shutter::SetOpen(bool state){
	
	std::ostringstream command;
	command << channel_;
	
	std::string pos;
	if(!state){
		pos = g_bw; 
	} else {
		pos = g_fw; 
	}

	command << pos;
	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
	if (ret != DEVICE_OK) 
		return ret;

	// get confirmation of the new position
	std::string answer;
	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK)
		return ret;

	// remove "\n" if start character
	std::string message = removeLineFeed(answer);

	// check for error
	if(isError(message)){
		return getErrorCode(message);
	}

	return DEVICE_OK;
}

//---------------------------------------------------------------------------
// Getters
//---------------------------------------------------------------------------
int ELL6_shutter::getID(std::string* id){
	std::ostringstream command;
	command << channel_ << "in";

	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
	if (ret != DEVICE_OK) 
		return ret;

	std::string answer;
	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK)
		return ret;

	// remove "\n" if start character
	std::string message = removeLineFeed(answer);

	// check if returned an error
	if(isError(message))
		return getErrorCode(message);

	// check if it is the expected answer
	if(message.substr(1,2).compare("IN") != 0)
		return ERR_UNEXPECTED_ANSWER;

	// check if ELL6 (in hex)
	if(message.substr(3,2).compare("06") != 0)
		return ERR_WRONG_DEVICE;
		
	*id = message.substr(3,15); // module + serial + year + firmware
	
	return DEVICE_OK;
}


int ELL6_shutter::GetOpen(bool &state){
	std::ostringstream command;
	command << channel_ << "gp";

	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
	if (ret != DEVICE_OK) 
		return ret;

	std::string answer;
	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK)
		return ret;
	
	// remove "\n" if start character
	std::string message = removeLineFeed(answer);

	// check for error
	if(isError(message)){
		return getErrorCode(message);
	}

	// check if it is the expected answer
	if(message.substr(1,2).compare("PO") != 0)
		return ERR_UNEXPECTED_ANSWER;

	std::string position = removeCommandFlag(message);
	if(position.compare(g_pos0) == 0){
		state = false;
	} else if(position.compare(g_pos1) == 0){
		state = true;
	} else {
		return ERR_UNKNOWN_STATE;
	}

	return DEVICE_OK;
}


//---------------------------------------------------------------------------
// Convenience function
//---------------------------------------------------------------------------
std::string ELL6_shutter::removeLineFeed(std::string answer){
	std::string message;
	if(answer.substr(0,1).compare("\n") == 0){
		message = answer.substr(1,answer.length()-1);
	} else {
		message = answer;
	}

	return message;
}

std::string ELL6_shutter::removeCommandFlag(std::string message){
	std::string value = message.substr(3,message.length()-3);
	return value;
}

bool ELL6_shutter::isError(std::string message){
	if(message.substr(1,2).compare("GS") == 0){
		return true;
	}
	return false;
}

int ELL6_shutter::getErrorCode(std::string message){
	std::string code = removeCommandFlag(message);

	if(code.compare("00") == 0){
		return DEVICE_OK;
	} else if(code.compare("01") == 0){
		return ERR_COMMUNICATION_TIME_OUT;
	} else if(code.compare("02") == 0){
		return ERR_MECHANICAL_TIME_OUT;
	} else if(message.compare("03") == 0){
		return ERR_COMMAND_ERROR_OR_NOT_SUPPORTED;
	} else if(message.compare("04") == 0){
		return ERR_VALUE_OUT_OF_RANGE;
	} else if(message.compare("05") == 0){
		return ERR_MODULE_ISOLATED;
	} else if(message.compare("06") == 0){
		return ERR_MODULE_OUT_OF_ISOLATION;
	} else if(message.compare("07") == 0){
		return ERR_INITIALIZING_ERROR;
	} else if(message.compare("08") == 0){
		return ERR_THERMAL_ERROR;
	} else if(message.compare("09") == 0){
		return ERR_BUSY;
	} else if(message.compare("0A") == 0){
		return ERR_SENSOR_ERROR;
	} else if(message.compare("0B") == 0){
		return ERR_MOTOR_ERROR;
	} else if(message.compare("0C") == 0){
		return ERR_OUT_OF_RANGE;
	} else if(message.compare("0D") == 0){
		return ERR_OVER_CURRENT_ERROR;
	}

	return ERR_UNKNOWN_ERROR;
}

//---------------------------------------------------------------------------
// Action handlers
//---------------------------------------------------------------------------

int ELL6_shutter::OnState(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet){ 
		bool state;
		int ret = GetOpen(state);
		if(ret != DEVICE_OK)
			return ret;
		
		std::stringstream ss;
		ss << (int) state;

		pProp->Set(ss.str().c_str());

	} else if (eAct == MM::AfterSet){
		long state;
		pProp->Get(state);

		bool state_bool;
		if(state == 1){
			state_bool = true;
		} else {
			state_bool = false;
		}

		int ret = SetOpen(state_bool);
		if(ret != DEVICE_OK)
			return ret;
	}

	return DEVICE_OK;
}

int ELL6_shutter::OnPort(MM::PropertyBase* pProp , MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		pProp->Set(port_.c_str());
	}
	else if (eAct == MM::AfterSet)
	{
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

int ELL6_shutter::OnChannel(MM::PropertyBase* pProp , MM::ActionType eAct)
{
	if (eAct == MM::AfterSet)
	{
		std::string channel;
		pProp->Get(channel);

		channel_ = channel;
	}

	return DEVICE_OK;
}

