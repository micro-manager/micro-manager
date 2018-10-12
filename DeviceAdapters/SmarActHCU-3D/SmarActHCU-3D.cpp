///////////////////////////////////////////////////////////////////////////////
// FILE:          SmarActHCU-3D.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   SmarAct HCU 3D stage, need special firmware 
//
// AUTHOR:        Joran Deschamps, EMBL, 2014 
//				  joran.deschamps@embl.de 
//
// LICENSE:       LGPL
//

#ifdef WIN32
#include <windows.h>
#define snprintf _snprintf 
#endif

#include "SmarActHCU-3D.h"
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>
#include <iostream>
#include <fstream>

const char* g_XYStageDeviceName = "SmaractXY";
const char* g_ZStageDeviceName = "SmaractZ";

int busy_count = 0;

using namespace std;

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
	RegisterDevice(g_ZStageDeviceName, MM::StageDevice, "Smaract Z stage");
	RegisterDevice(g_XYStageDeviceName, MM::XYStageDevice, "Smaract XY stage");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
	if (deviceName == 0)
		return 0;

	if (strcmp(deviceName, g_XYStageDeviceName) == 0)
	{
		XYStage* s = new XYStage();
		return s;
	}
	if (strcmp(deviceName, g_ZStageDeviceName) == 0)
	{
		ZStage* s = new ZStage();
		return s;
	}
	return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
	delete pDevice;
}

bool isError(std::string answer, int* error){
	if(answer.compare(":E0") == 0){ // if no error, then return false
		return false;
	}

	if(answer.substr(0,2).compare(":E") == 0){
		std::string s = answer.substr(2); // should check if it is integer (for robustness)
		*error = std::stoi(s);
		return true;
	} else {
		*error = 0;
		return false;
	}
}

int GetErrorStatus(int error){
	switch(error){
	case 0:
		return DEVICE_OK;
	case 1:
		return ERR_PARSING;
	case 2:
		return ERR_UNKNWON_COMMAND;
	case 3:
		return ERR_INVALID_CHANNEL;
	case 4:
		return ERR_INVALID_MODE;
	case 13:
		return ERR_SYNTAX;
	case 15:
		return ERR_OVERFLOW;
	case 17:
		return ERR_INVALID_PARAMETER;
	case 18:
		return ERR_MISSING_PARAMETER;
	case 19:
		return ERR_NO_SENSOR_PRESENT;
	case 20:
		return ERR_WRONG_SENSOR_TYPE;
	}
	return ERR_UNKNOWN_ERROR;
}


///////////////////////////////////////////////////////////////////////////////
/////////////////////////////// XYStage ///////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////

XYStage::XYStage() :
port_("Undefined"),
	initialized_(false),
	reverseX_(1),
	reverseY_(1),
	freqXY_(5000),
	channelX_(0),
	channelY_(1),
	holdtime_(10),
	id_(""),
	controller_(""),
	answerTimeoutMs_(1000)
{
	InitializeDefaultErrorMessages();

	SetErrorText(ERR_PORT_CHANGE_FORBIDDEN, "Port change forbidden.");
	SetErrorText(ERR_IDENTIFICATION_FAIL, "Fail to communicate with a SmarAct stage.");
	SetErrorText(ERR_PARSING, "The command could not be processed due to a parsing error.");
	SetErrorText(ERR_UNKNWON_COMMAND, "Unknown command.");
	SetErrorText(ERR_INVALID_CHANNEL, "The channel index is invalid and the command could not be processed.");
	SetErrorText(ERR_INVALID_MODE, "The parameter that defines the mode for automatic error reporting is not valid.");
	SetErrorText(ERR_SYNTAX, "The command could not be processed due to a syntax error.");
	SetErrorText(ERR_OVERFLOW, "A number value given was too large to be processed.");
	SetErrorText(ERR_INVALID_PARAMETER, "A parameter that was given with the command was invalid.");
	SetErrorText(ERR_MISSING_PARAMETER, "A parameter was omitted where it was required.");
	SetErrorText(ERR_NO_SENSOR_PRESENT, "Wrong positioner adress: no sensor present.");
	SetErrorText(ERR_WRONG_SENSOR_TYPE, "Wrong sensor required for this command.");
	SetErrorText(ERR_UNKNOWN_ERROR, "Unknown error.");

	// create pre-initialization properties
	// ------------------------------------
	CreateProperty("X channel", "0", MM::Integer, false, 0, true);
	AddAllowedValue("X channel", "0");
	AddAllowedValue("X channel", "1");
	AddAllowedValue("X channel", "2");

	CreateProperty("X direction", "1", MM::Integer, false, 0, true);
	AddAllowedValue("X direction", "-1");
	AddAllowedValue("X direction", "1");

	CreateProperty("Y channel", "1", MM::Integer, false, 0, true);
	AddAllowedValue("Y channel", "0");
	AddAllowedValue("Y channel", "1");
	AddAllowedValue("Y channel", "2");

	CreateProperty("Y direction", "1", MM::Integer, false, 0, true);
	AddAllowedValue("Y direction", "-1");
	AddAllowedValue("Y direction", "1");

	// Name
	CreateProperty(MM::g_Keyword_Name, g_XYStageDeviceName, MM::String, true);

	// Description
	CreateProperty(MM::g_Keyword_Description, "Smaract XYStage", MM::String, true);

	// Port
	CPropertyAction* pAct = new CPropertyAction (this, &XYStage::OnPort);
	CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);
}

XYStage::~XYStage()
{
	Shutdown();
}

void XYStage::GetName(char* Name) const
{
	CDeviceUtils::CopyLimitedString(Name, g_XYStageDeviceName);
}

int XYStage::Initialize()
{
	// Make sure we are in error reporting mode
	int ret = SetErrorReporting(true);
	if (ret != DEVICE_OK)
		return ret;

	//////////////////////////////////////////////////////////////////
	// Define channel and direction
	char charbuff[MM::MaxStrLength];
	ret = GetProperty("X direction", charbuff);
	if (ret != DEVICE_OK)
		return ret;

	reverseX_ = atoi(charbuff); 
	ret = GetProperty("X channel", charbuff);
	if (ret != DEVICE_OK)
		return ret;
	channelX_ = atoi(charbuff); 

	ret = GetProperty("Y direction", charbuff);
	if (ret != DEVICE_OK)
		return ret;

	reverseY_ = atoi(charbuff); 
	ret = GetProperty("Y channel", charbuff);
	if (ret != DEVICE_OK)
		return ret;
	channelY_ = atoi(charbuff); 

	//////////////////////////////////////////////////////////////////
	// Hold time property
	CPropertyAction* pAct = new CPropertyAction (this, &XYStage::OnHold);
	CreateProperty("Hold time (ms)", "0", MM::Integer, false, pAct);
	SetPropertyLimits("Hold time (ms)", 1, 60000);

	// Frequency
	pAct = new CPropertyAction (this, &XYStage::OnFrequency);
	CreateProperty("Frequency", "5000", MM::Integer, false, pAct);
	SetPropertyLimits("Frequency", 1, 18500);

	/////////////////////////////////////////////////////////////////
	// Controller type
	ret = GetController(&controller_);
	if (ret != DEVICE_OK)
		return ret;

	CreateProperty("Controller", controller_.c_str(), MM::String, true);

	// Get ID
	ret = GetID(&id_);
	if (ret != DEVICE_OK)
		return ret;

	CreateProperty("ID", id_.c_str(), MM::String, true);


	initialized_ = true;
	return DEVICE_OK;
}

int XYStage::Shutdown()
{
	if (initialized_)
	{
		initialized_ = false;
	}
	return DEVICE_OK;
}

bool XYStage::Busy()
{
	string answer;
	std::stringstream command;
	command << ":M" << channelX_;
	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\n");									
	ret = GetSerialAnswer(port_.c_str(), "\n", answer);
	if (ret != DEVICE_OK){
		return true;
	}

	if(strcmp(answer.substr(3).c_str(),"S") != 0){
		return true;
	}

	std::stringstream command2;
	command2 << ":M" << channelY_;
	ret = SendSerialCommand(port_.c_str(), command2.str().c_str(), "\n");									
	ret = GetSerialAnswer(port_.c_str(), "\n", answer);
	if (ret != DEVICE_OK){
		return true;
	}

	if(strcmp(answer.substr(3).c_str(),"S") != 0){  
		return true;
	}

	return false;
}

//////////////////////////////////////////////////
/// setters

int XYStage::SetErrorReporting(bool reporting){
	string answer;
	std::stringstream command;
	if(reporting){
		command << ":E1";
	} else {
		command << ":E0";
	}

	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\n");	
	ret = GetSerialAnswer(port_.c_str(), "\n", answer);
	if (ret != DEVICE_OK)
		return ret;

	// check for error
	int error; 
	if(isError(answer, &error)){
		return GetErrorStatus(error);
	}

	return DEVICE_OK;
}

int XYStage::SetRelativePositionUm(double x, double y){
	int ret = 0;
	if(x != 0){ // if non null relative position in first channel

		// need to round off to first decimal otherwise the stage 
		// cannot process the position
		double xpos = ceil(x*10)/10;

		// send command
		std::stringstream command;
		command << ":MPR" << channelX_ << "P" << xpos*reverseX_ << "H" << holdtime_;
		ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\n");

		// check for answer
		string answer;
		ret = GetSerialAnswer(port_.c_str(), "\n", answer);
		if (ret != DEVICE_OK)
			return ret;

		// is error?
		int error; 
		if(isError(answer, &error)){
			return GetErrorStatus(error);
		}
	}

	if(y != 0){ // if non null relative position in second channel
		double ypos = ceil(y*10)/10;

		// send command
		std::stringstream command2;
		command2 << ":MPR" << channelY_ << "P" << ypos*reverseY_ << "H" << holdtime_;
		ret = SendSerialCommand(port_.c_str(), command2.str().c_str(), "\n");
		
		// check for answer
		string answer;
		ret = GetSerialAnswer(port_.c_str(), "\n", answer);
		if (ret != DEVICE_OK)
			return ret;

		// check for error
		int error; 
		if(isError(answer, &error)){
			return GetErrorStatus(error);
		}
	}


	return DEVICE_OK;
}

int XYStage::SetPositionUm(double x, double y){
	// round to first decimal, otherwise the stage cannot
	// process the value
	double xpos = ceil(x*10)/10;
	double ypos = ceil(y*10)/10;

	// set position of the first channel
	std::stringstream command;
	command << ":MPA" << channelX_ << "P" << xpos*reverseX_ << "H" << holdtime_;
	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\n");
	if (ret != DEVICE_OK){
		return ret;
	}

	// check for answer
	string answer;
	ret = GetSerialAnswer(port_.c_str(), "\n", answer);
	if (ret != DEVICE_OK)
		return ret;

	// is it an error?
	int error; 
	if(isError(answer, &error)){
		return GetErrorStatus(error);
	}

	// set position of the second channel
	std::stringstream command2;
	command2 << ":MPA" << channelY_ << "P" << ypos*reverseY_ << "H" << holdtime_;
	ret = SendSerialCommand(port_.c_str(), command2.str().c_str(), "\n");
	if (ret != DEVICE_OK){
		return ret;
	}

	// chekc for answer
	ret = GetSerialAnswer(port_.c_str(), "\n", answer);
	if (ret != DEVICE_OK)
		return ret;

	// is it an error?
	if(isError(answer, &error)){
		return GetErrorStatus(error);
	}

	return DEVICE_OK;
}


int XYStage::SetFrequency(int freq)
{
	// set first channel's frequency
	std::stringstream command;
	command << ":SCLF" << channelX_ << "F" << freq;
	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\n");

	// check for answer
	string answer;
	ret = GetSerialAnswer(port_.c_str(), "\n", answer);
	if (ret != DEVICE_OK)
		return ret;

	// is it an error?
	int error; 
	if(isError(answer, &error)){
		return GetErrorStatus(error);
	}

	// set second channel's frequency
	std::stringstream command2;
	command2 << ":SCLF" << channelY_ << "F" << freq;
	ret = SendSerialCommand(port_.c_str(), command2.str().c_str(), "\n");
	
	// check for answer
	ret = GetSerialAnswer(port_.c_str(), "\n", answer);
	if (ret != DEVICE_OK)
		return ret;

	// is it an error?
	if(isError(answer, &error)){
		return GetErrorStatus(error);
	}

	return DEVICE_OK;
}

int XYStage::SetOrigin()											
{
	// set first channel's origin
	std::stringstream command;
	command << ":SZ" << channelX_;
	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\n");
	
	string answer;
	ret = GetSerialAnswer(port_.c_str(), "\n", answer);
	if (ret != DEVICE_OK)
		return ret;

	// check for error
	int error; 
	if(isError(answer, &error)){
		return GetErrorStatus(error);
	}
	
	// set second channel's origin
	std::stringstream command2;
	command2 << ":SZ" << channelY_;
	ret = SendSerialCommand(port_.c_str(), command2.str().c_str(), "\n");
	
	ret = GetSerialAnswer(port_.c_str(), "\n", answer);
	if (ret != DEVICE_OK)
		return ret;
	
	// check for error 
	if(isError(answer, &error)){
		return GetErrorStatus(error);
	}

	return DEVICE_OK;
}

//////////////////////////////////////////////////
/// getters

int XYStage::GetPositionUm(double& x, double& y)
{	
	string answer;
	std::stringstream command;
	command << ":GP" << channelX_;

	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\n");			
	ret = GetSerialAnswer(port_.c_str(), "\n", answer);
	if (ret != DEVICE_OK){
		return ret;
	}

	// check for error
	int error; 
	if(isError(answer, &error)){
		return GetErrorStatus(error);
	}

	// if not error, then extract position
	curPos_x_=atof(answer.substr(4).c_str())*reverseX_;

	std::stringstream command2;
	command2 << ":GP" << channelY_;
	ret = SendSerialCommand(port_.c_str(), command2.str().c_str(), "\n");	
	ret = GetSerialAnswer(port_.c_str(), "\n", answer);
	if (ret != DEVICE_OK){
		return ret;
	}
	
	// check for error
	if(isError(answer, &error)){
		return GetErrorStatus(error);
	}

	// extract position
	curPos_y_=atof(answer.substr(4).c_str())*reverseY_;

	x =  curPos_x_;
	y =  curPos_y_;

	return DEVICE_OK;
}

int XYStage::GetController(std::string* controller)
{
	string answer;
	std::stringstream command;
	command << ":I";

	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\n");	
	ret = GetSerialAnswer(port_.c_str(), "\n", answer);
	if (ret != DEVICE_OK)
		return ret;

	// check for error
	int error; 
	if(isError(answer, &error)){
		return GetErrorStatus(error);
	}

	// if not an error, then try to read out controller
	if(answer.find("SmarAct HCU-3D") != std::string::npos){
		*controller = "SmarAct HCU-3D";
	} else if(answer.find("SmarAct CU-3D") != std::string::npos){
		*controller = "SmarAct CU-3D";
	} else if(answer.find("SmarAct SCU-3D") != std::string::npos){
		*controller = "SmarAct SCU-3D";
	} else {
		return ERR_IDENTIFICATION_FAIL; 
	}

	return DEVICE_OK;
}

int XYStage::GetID(std::string* id)
{
	string answer;
	std::stringstream command;
	command << ":GID";

	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\n");	
	ret = GetSerialAnswer(port_.c_str(), "\n", answer);
	if (ret != DEVICE_OK)
		return ret;
	
	// check for error
	int error; 
	if(isError(answer, &error)){
		return GetErrorStatus(error);
	}

	// if not, get id
	*id = answer.substr(3);
	
	return DEVICE_OK;
}

/////////////////////////////////////////////////////////////////
/////////////// Unsupported commands ////////////////////////////

int XYStage::SetPositionSteps(long x, long y)
{

	return DEVICE_UNSUPPORTED_COMMAND;
}

int XYStage::GetPositionSteps(long& x, long& y)
{

	return DEVICE_UNSUPPORTED_COMMAND;
}

int XYStage::GetLimitsUm(double& xMin, double& xMax, double& yMin, double& yMax)			
{
	return DEVICE_UNSUPPORTED_COMMAND;
}

int XYStage::Home()
{
	return DEVICE_UNSUPPORTED_COMMAND;
}

int XYStage::Stop()
{
	return DEVICE_UNSUPPORTED_COMMAND;
}

int XYStage::GetStepLimits(long& xMin, long& xMax, long& yMin, long& yMax)
{
	return DEVICE_UNSUPPORTED_COMMAND;
}

double XYStage::GetStepSizeXUm()
{
	return DEVICE_UNSUPPORTED_COMMAND;
}

double XYStage::GetStepSizeYUm()
{
	return DEVICE_UNSUPPORTED_COMMAND;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int XYStage::OnFrequency(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{ 
		pProp->Set((long)freqXY_);
	}
	else if (eAct == MM::AfterSet)
	{
		long pos;
		pProp->Get(pos);
		freqXY_ = pos;
		SetFrequency(freqXY_);
	}

	return DEVICE_OK;
}

int XYStage::OnHold(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		pProp->Set((long)holdtime_);
	}
	else if (eAct == MM::AfterSet)
	{
		long pos;
		pProp->Get(pos);
		holdtime_ = pos;
	}

	return DEVICE_OK;
}

int XYStage::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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

///////////////////////////////////////////////////////////////////////////////
//////////////////////////////// ZStage ///////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////

ZStage::ZStage() :
port_("Undefined"),
	initialized_(false),
	reverseZ_(1),
	freqZ_(5000),
	channelZ_(2),
	holdtime_(10),
	id_(""),
	controller_(""),
	answerTimeoutMs_(1000)
{
	InitializeDefaultErrorMessages();

	SetErrorText(ERR_PORT_CHANGE_FORBIDDEN, "Port change forbidden.");
	SetErrorText(ERR_IDENTIFICATION_FAIL, "Fail to communicate with a SmarAct stage.");
	SetErrorText(ERR_PARSING, "The command could not be processed due to a parsing error.");
	SetErrorText(ERR_UNKNWON_COMMAND, "Unknown command.");
	SetErrorText(ERR_INVALID_CHANNEL, "The channel index is invalid and the command could not be processed.");
	SetErrorText(ERR_INVALID_MODE, "The parameter that defines the mode for automatic error reporting is not valid.");
	SetErrorText(ERR_SYNTAX, "The command could not be processed due to a syntax error.");
	SetErrorText(ERR_OVERFLOW, "A number value given was too large to be processed.");
	SetErrorText(ERR_INVALID_PARAMETER, "A parameter that was given with the command was invalid.");
	SetErrorText(ERR_MISSING_PARAMETER, "A parameter was omitted where it was required.");
	SetErrorText(ERR_NO_SENSOR_PRESENT, "Wrong positioner adress: no sensor present.");
	SetErrorText(ERR_WRONG_SENSOR_TYPE, "Wrong sensor required for this command.");
	SetErrorText(ERR_UNKNOWN_ERROR, "Unknown error.");

	// create pre-initialization properties
	// ------------------------------------
	CreateProperty("Z channel", "2", MM::Integer, false, 0, true);
	AddAllowedValue("Z channel", "0");
	AddAllowedValue("Z channel", "1");
	AddAllowedValue("Z channel", "2");

	CreateProperty("Z direction", "1", MM::Integer, false, 0, true);
	AddAllowedValue("Z direction", "-1");
	AddAllowedValue("Z direction", "1");

	// Name
	CreateProperty(MM::g_Keyword_Name, g_ZStageDeviceName, MM::String, true);

	// Description
	CreateProperty(MM::g_Keyword_Description, "Smaract ZStage", MM::String, true);

	// Port
	CPropertyAction* pAct = new CPropertyAction (this, &ZStage::OnPort);
	CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);
}

ZStage::~ZStage()
{
	Shutdown();
}

void ZStage::GetName(char* Name) const
{
	CDeviceUtils::CopyLimitedString(Name, g_ZStageDeviceName);
}

int ZStage::Initialize()
{	
	// Make sure we are in error reporting mode
	int ret = SetErrorReporting(true);
	if (ret != DEVICE_OK)
		return ret;
	
	//////////////////////////////////////////////////////////////////
	// Define channel and direction
	char charbuff[MM::MaxStrLength];
	ret = GetProperty("Z direction", charbuff);
	if (ret != DEVICE_OK)
		return ret;
	reverseZ_ = atoi(charbuff); 
	ret = GetProperty("Z channel", charbuff);
	if (ret != DEVICE_OK){
		return ret;
	}
	channelZ_ = atoi(charbuff); 

	// Frequency
	CPropertyAction* pAct = new CPropertyAction (this, &ZStage::OnFrequency);
	CreateProperty("Frequency", "5000", MM::Integer, false, pAct);
	SetPropertyLimits("Frequency", 1, 18500);

	// Controller type
	ret = GetController(&controller_);
	if (ret != DEVICE_OK){
		return ret;
	}
	CreateProperty("Controller", controller_.c_str(), MM::String, true);

	// Create ID
	ret = GetID(&id_);
	if (ret != DEVICE_OK){
		return ret;
	}

	CreateProperty("ID", id_.c_str(), MM::String, true);
	
	initialized_ = true;

	return DEVICE_OK;
}

int ZStage::Shutdown()
{
	if (initialized_)
	{
		initialized_ = false;
	}
	return DEVICE_OK;
}

bool ZStage::Busy()
{
	string answer;   
	std::stringstream command;

	command << ":M" << channelZ_;

	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\n");		
	ret = GetSerialAnswer(port_.c_str(), "\n", answer);
	if (ret != DEVICE_OK){
		return true;
	}

	if(strcmp(answer.substr(3).c_str(),"S") != 0){
		return true;
	}

	return false;
}

//////// Setters
int ZStage::SetErrorReporting(bool reporting){
	string answer;
	std::stringstream command;
	if(reporting){
		command << ":E1";
	} else {
		command << ":E0";
	}

	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\n");	
	ret = GetSerialAnswer(port_.c_str(), "\n", answer);
	if (ret != DEVICE_OK)
		return ret;

	// check for error
	int error; 
	if(isError(answer, &error)){
		return GetErrorStatus(error);
	}

	return DEVICE_OK;
}

int ZStage::SetPositionUm(double pos)
{
	// round to first decimal	
	double npos = ceil(pos*10)/10;

	std::stringstream command;
	command << ":MPA" << channelZ_ << "P" << npos*reverseZ_ << "H" << holdtime_;
	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\n");
	if (ret != DEVICE_OK){
		return ret;
	}
	
	// check for answer
	string answer;
	ret = GetSerialAnswer(port_.c_str(), "\n", answer);
	if (ret != DEVICE_OK)
		return ret;

	// is it an error?
	int error; 
	if(isError(answer, &error)){
		return GetErrorStatus(error);
	}

	return DEVICE_OK;
}

int ZStage::SetRelativePositionUm(double pos)
{
	// round to first decimal	
	double npos = ceil(pos*10)/10;

	std::stringstream command;
	command << ":MPR" << channelZ_ << "P" << npos*reverseZ_ << "H" << holdtime_;
	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\n");
	if (ret != DEVICE_OK){
		return ret;
	}

	// check for answer
	string answer;
	ret = GetSerialAnswer(port_.c_str(), "\n", answer);
	if (ret != DEVICE_OK)
		return ret;

	// is it an error?
	int error; 
	if(isError(answer, &error)){
		return GetErrorStatus(error);
	}

	return DEVICE_OK;
}

int ZStage::SetOrigin()
{
	std::stringstream command;
	command << ":SZ" << channelZ_;
	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\n");
	if (ret != DEVICE_OK)
		return ret;
	return DEVICE_OK;
}

int ZStage::SetFrequency(int freq)
{
	std::stringstream command;
	command << ":SCLF" << channelZ_ << "F" << freq;
	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\n");
	if (ret != DEVICE_OK)
		return ret;

	return DEVICE_OK;
}

//////// Getters

int ZStage::GetPositionUm(double& pos)
{
	string answer;
	std::stringstream command;
	command << ":GP" << channelZ_;

	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\n");	
	ret = GetSerialAnswer(port_.c_str(), "\n", answer);
	if (ret != DEVICE_OK){	
		return ret;
	}

	// is the answer an error?
	int error; 
	if(isError(answer, &error)){
		return GetErrorStatus(error);
	}

	// else, extract position
	pos = atof(answer.substr(4).c_str());

	return DEVICE_OK;
}

int ZStage::GetController(std::string* controller)
{
	string answer;
	std::stringstream command;
	command << ":I";

	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\n");	
	ret = GetSerialAnswer(port_.c_str(), "\n", answer);
	if (ret != DEVICE_OK){	
		return ret;
	}

	// is the answer an error?
	int error; 
	if(isError(answer, &error)){
		return GetErrorStatus(error);
	}

	if(answer.find("SmarAct HCU-3D") != std::string::npos){
		*controller = "SmarAct HCU-3D";
	} else if(answer.find("SmarAct CU-3D") != std::string::npos){
		*controller = "SmarAct CU-3D";
	} else if(answer.find("SmarAct SCU-3D") != std::string::npos){
		*controller = "SmarAct CU-3D";
	} else {
		return ERR_IDENTIFICATION_FAIL; 
	}

	return DEVICE_OK;
}

int ZStage::GetID(std::string* id)
{
	string answer;
	std::stringstream command;
	command << ":GID";

	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\n");	
	ret = GetSerialAnswer(port_.c_str(), "\n", answer);
	if (ret != DEVICE_OK){	
		return ret;
	}

	// is the answer an error?
	int error; 
	if(isError(answer, &error)){
		return GetErrorStatus(error);
	}

	*id = answer.substr(3);

	return DEVICE_OK;
}

/////////////////////////////////////////////////////////////////
/////////////// Unsupported commands ////////////////////////////

int ZStage::SetPositionSteps(long pos)
{

	return DEVICE_UNSUPPORTED_COMMAND;   
}

int ZStage::GetPositionSteps(long& steps)
{

	return DEVICE_UNSUPPORTED_COMMAND;
}

int ZStage::GetLimits(double& /*min*/, double& /*max*/)
{
	return DEVICE_UNSUPPORTED_COMMAND;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////
int ZStage::OnFrequency(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{  
		pProp->Set((long)freqZ_);
	}
	else if (eAct == MM::AfterSet)
	{
		long pos;
		pProp->Get(pos);
		freqZ_ = pos;
		SetFrequency(freqZ_);
	}

	return DEVICE_OK;
}

int ZStage::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)								
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
