//-----------------------------------------------------------------------------
// FILE:          Toptica_iBeamSmartCW.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Controls iBeam smart laser series from Toptica through serial port
//				  using the hidden CW mode
// COPYRIGHT:     EMBL
// LICENSE:       LGPL
// AUTHOR:        Joran Deschamps, 2018
//-----------------------------------------------------------------------------

#include "Toptica_iBeamSmartCW.h"

#ifdef WIN32
#include "winuser.h"
#endif

const char* g_DeviceiBeamSmartName = "iBeamSmartCW";
const char* g_DeviceiBeamSmartNameNormal = "Normal mode";

//-----------------------------------------------------------------------------
// MMDevice API
//-----------------------------------------------------------------------------

MODULE_API void InitializeModuleData()
{
	RegisterDevice(g_DeviceiBeamSmartName, MM::GenericDevice, "Toptica iBeam smart laser in CW mode.");
	RegisterDevice(g_DeviceiBeamSmartNameNormal, MM::GenericDevice, "Restore Toptica iBeam smart to normal mode.");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
	if (deviceName == 0)
		return 0;

	if (strcmp(deviceName, g_DeviceiBeamSmartName) == 0){
		return new iBeamSmartCW;
	} else if (strcmp(deviceName, g_DeviceiBeamSmartNameNormal) == 0){
		return new iBeamSmartNormal;
	}

	return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
	delete pDevice;
}

//-----------------------------------------------------------------------------
// iBeam smart device adapter
//-----------------------------------------------------------------------------

iBeamSmartCW::iBeamSmartCW():
	port_("Undefined"),
	serial_("Undefined"),
	clip_("Undefined"),
	initialized_(false),
	busy_(false),
	power_(0.00),
	finea_(0),
	fineb_(10),
	laserOn_(false),
	fineOn_(false),
	extOn_(false),
	maxpower_(125)
{
	InitializeDefaultErrorMessages();
	SetErrorText(ERR_PORT_CHANGE_FORBIDDEN, "You can't change the port after device has been initialized.");
	SetErrorText(LASER_WARNING, "The laser has emitted a warning error, please refer to the CoreLog for the warning code.");
	SetErrorText(LASER_ERROR, "The laser has emitted an error, please refer to the CoreLog for the error code.");
	SetErrorText(LASER_FATAL_ERROR, "The laser has emitted a fatal error, please refer to the CoreLog for the error code.");
	SetErrorText(ADAPTER_POWER_OUTSIDE_RANGE, "The specified power is outside the range (0<=power<= max power).");
	SetErrorText(ADAPTER_PERC_OUTSIDE_RANGE, "The specified percentage is outside the range (0<=percentage<=100).");
	SetErrorText(ADAPTER_ERROR_DATA_NOT_FOUND, "Some data could not be extracted, consult the CoreLog.");
	SetErrorText(ADAPTER_UNEXPECTED_ANSWER, "Unexpected answer from the laser.");
	SetErrorText(LASER_CLIP_FAIL, "Clip needs to be reset (clip status is failed).");
	SetErrorText(ADAPTER_ERROR_PASSWORD, "Password to service level failed, the device adapter will not function properly.");
	SetErrorText(ADAPTER_ERROR_SAVE_DATA, "Error when saving system data in the laser.");

	// Description
	CreateProperty(MM::g_Keyword_Description, "iBeam smart Laser Controller", MM::String, true, 0, true);

	// Port
	CPropertyAction* pAct = new CPropertyAction (this, &iBeamSmartCW::OnPort);
	CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);
}

iBeamSmartCW::~iBeamSmartCW()
{
	Shutdown();
}

void iBeamSmartCW::GetName(char* Name) const
{
	CDeviceUtils::CopyLimitedString(Name, g_DeviceiBeamSmartName);
}

int iBeamSmartCW::Initialize()
{
	// Make sure prompting ("CMD>") is off (so we get [OK] or error for every answer) 
	// and "talk" is set to usual.
	// Otherwise we will get infinite loops (because we are looking for "[OK]")
	// and some of the data will not be found (e.g. enable EXT from CMD>sh data)
	int ret = setPrompt(false);
	if (DEVICE_OK != ret)
		return ret;
	
	// Set the laser to cw mode
	bool isExtPossible = false;
	ret = setCWmode(&isExtPossible, &maxpower_);
	if (DEVICE_OK != ret)
		return ret;

	//////////////////////////////////////////////
	// Read only properties

	// Serial number
	ret = getSerial(&serial_); 
	if (DEVICE_OK != ret)
		return ret;

	ret = CreateProperty("Serial ID", serial_.c_str(), MM::String, true);
	if (DEVICE_OK != ret)
		return ret;


	ret = CreateProperty("Maximum power (mW)", to_string(maxpower_).c_str(), MM::String, true);
	if (DEVICE_OK != ret)
		return ret;

	// Firmware version
	std::string version;
	ret = getFirmwareVersion(&version);
	if (DEVICE_OK != ret)
		return ret;
	
	ret = CreateProperty("Firmware version", version.c_str(), MM::String, true);
	if (DEVICE_OK != ret)
		return ret;

	// Clipping status
	ret = getClipStatus(&clip_);
	if (DEVICE_OK != ret)
		return ret;

	CPropertyAction*  pAct = new CPropertyAction (this, &iBeamSmartCW::OnClip);
	ret = CreateProperty("Clipping status", clip_.c_str(), MM::String, true, pAct);
	if (DEVICE_OK != ret)
		return ret;


	//////////////////////////////////////////////
	// Properties
	// Laser On/Off
	ret = getLaserStatus(&laserOn_);
	if (DEVICE_OK != ret)
		return ret;

	std::vector<std::string> commandsOnOff;
	commandsOnOff.push_back("Off");
	commandsOnOff.push_back("On");

	pAct = new CPropertyAction (this, &iBeamSmartCW::OnLaserOnOff);
	if(laserOn_){
		ret = CreateProperty("Laser Operation", "On", MM::String, false, pAct);
		SetAllowedValues("Laser Operation", commandsOnOff);
		if (DEVICE_OK != ret)
			return ret;
	} else {
		ret = CreateProperty("Laser Operation", "Off", MM::String, false, pAct);
		SetAllowedValues("Laser Operation", commandsOnOff);
		if (DEVICE_OK != ret)
			return ret;
	}

	// Power channel 1
	ret = getPower(&power_);
	if (DEVICE_OK != ret)
		return ret;

	pAct = new CPropertyAction (this, &iBeamSmartCW::OnPower);
	ret = CreateProperty("Power (mW)", to_string(power_).c_str(), MM::Float, false, pAct);
	SetPropertyLimits("Power (mW)", 0, maxpower_);
	if (DEVICE_OK != ret)
		return ret;

	// External propery
	if(isExtPossible){
		ret = getExtStatus(&extOn_);
		if (DEVICE_OK != ret)
			return ret;

		pAct = new CPropertyAction (this, &iBeamSmartCW::OnEnableExt);
		if(extOn_){
			ret = CreateProperty("Enable ext trigger", "On", MM::String, false, pAct);
			SetAllowedValues("Enable ext trigger", commandsOnOff);
			if (DEVICE_OK != ret)
				return ret;
		} else{
			ret = CreateProperty("Enable ext trigger", "Off", MM::String, false, pAct);
			SetAllowedValues("Enable ext trigger", commandsOnOff);
			if (DEVICE_OK != ret)
				return ret;
		}
	} else {
		extOn_ = false;
	}

	// Fine
	ret = getFineStatus(&fineOn_);
	if (DEVICE_OK != ret)
		return ret;

	pAct = new CPropertyAction (this, &iBeamSmartCW::OnEnableFine);
	if(fineOn_){
		ret = CreateProperty("Enable Fine", "On", MM::String, false, pAct);
		SetAllowedValues("Enable Fine", commandsOnOff);
		if (DEVICE_OK != ret)
			return ret;
	} else{
		ret = CreateProperty("Enable Fine", "Off", MM::String, false, pAct);
		SetAllowedValues("Enable Fine", commandsOnOff);
		if (DEVICE_OK != ret)
			return ret;
	}

	// Fine a percentage
	ret = getFinePercentage('a',&finea_);
	if (DEVICE_OK != ret)
		return ret;

	pAct = new CPropertyAction (this, &iBeamSmartCW::OnFineA);
	ret = CreateProperty("Fine A (%)", to_string(finea_).c_str(), MM::Float, false, pAct);
	SetPropertyLimits("Fine A (%)", 0, 100);
	if (DEVICE_OK != ret)
		return ret;

	// Fine b percentage
	ret = getFinePercentage('b', &fineb_);
	if (DEVICE_OK != ret)
		return ret;

	pAct = new CPropertyAction (this, &iBeamSmartCW::OnFineB);
	ret = CreateProperty("Fine B (%)", to_string(fineb_).c_str(), MM::Float, false, pAct);
	SetPropertyLimits("Fine B (%)", 0, 100);
	if (DEVICE_OK != ret)
		return ret;

	initialized_ = true;
	return DEVICE_OK;
}

int iBeamSmartCW::Shutdown()
{
	if (initialized_)
	{
		setLaserOnOff(false); // The ibeamSmart software doesn't turn off the laser when stopping, I prefer to do it
		setNormalMode(); // Go back to normal mode in order to prevent issues when using the Toptica software (channel 1 should NOT be enabled manually in cw mode)
		setPrompt(true); // Reset prompt to on, otherwise the Topas software will give a timeout error (< v1.4) 
		initialized_ = false;	 
	}
	return DEVICE_OK;
}

bool iBeamSmartCW::Busy()
{
	return busy_;
}


//---------------------------------------------------------------------------
// Conveniance functions:
//---------------------------------------------------------------------------

int iBeamSmartCW::setCWmode(bool* isExtPossible, int* maxPower){
	std::ostringstream command;
	command << "pass service";

	std::string answer;
	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
	if (ret != DEVICE_OK) 
		return ret;
	
	LogMessage("Sent password service", false);
	// the loop should end when PSW> is found
	while(answer.find("\n") == std::string::npos){
		ret = GetSerialAnswer(port_.c_str(), "\r", answer);
		if (ret != DEVICE_OK)
			return ret;

		LogMessage(answer, false);

		// if the laser has an error
		if(isError(answer)){
			return publishError(answer);
		}
	}
	LogMessage("Done sending", false);
	LogMessage(answer, false);

	// Send password
	answer = ""; 
	std::ostringstream pass;
	pass << "TuiOptics";

	ret = SendSerialCommand(port_.c_str(), pass.str().c_str(), "\r");
	if (ret != DEVICE_OK) 
		return ret;

	bool correct_password = false;
	while(!isOk(answer)){
		ret = GetSerialAnswer(port_.c_str(), "\r", answer);
		if (ret != DEVICE_OK)
			return ret;

		// if the line contains %SYS-I-046, then the password was correct
		if (answer.find("%SYS-I-046") != std::string::npos){	
			correct_password = true;
		}

		// if the laser has an error
		if(isError(answer)){
			return publishError(answer);
		}
	}

	// if the password was not correct
	if(!correct_password){
		return ADAPTER_ERROR_PASSWORD;
	}	
	
	// Set talk level to gabby
	ret  = setTalkGabby();
	if (DEVICE_OK != ret)
		return ret;


	////////////////////////////////////////////////
	/// Now we can switch to cw mode

	// first extract some information
	answer = ""; 
	std::ostringstream sh_sys;
	sh_sys << "sh sys";

	ret = SendSerialCommand(port_.c_str(), sh_sys.str().c_str(), "\r");
	if (ret != DEVICE_OK) 
		return ret;

	bool isCW = false;
	bool foundPulse = false;
	bool foundCW = false;
	while(!isOk(answer)){
		ret = GetSerialAnswer(port_.c_str(), "\r", answer);
		if (ret != DEVICE_OK)
			return ret;

		// Check if the pulse option is available in order to create properties accordingly
		if(!foundPulse){
			// if the line contains "pulse board", extract information about pulse option
			if (answer.find("pulse board")!=std::string::npos){	
				if(answer.find("yes")!=std::string::npos){ // pulse option available
					*isExtPossible = true;
				} else {
					*isExtPossible = false;
				}
				foundPulse = true;
			}
		}
		
		// Check if the cw mode is already enabled
		if(!foundCW){
			// if the line contains "SPP modes", check if already in CW mode
			if (answer.find("SPP modes")!=std::string::npos){	
				if(answer.find("CW (On2)")!=std::string::npos){ // The laser is in cw mode
					isCW = true;
				} else { 
					isCW = false;
				}
				foundCW = true;
			}
		}

		// if the laser has an error
		if(isError(answer)){
			return publishError(answer);
		}
	}

	// In case the relevant informations could not be extracted
	if(!foundPulse){
		LogMessage("Could not extract pulse board information.",false);
		return ADAPTER_ERROR_DATA_NOT_FOUND;
	}
	if(!foundCW){
		LogMessage("Could not extract CW mode information.",false);
		return ADAPTER_ERROR_DATA_NOT_FOUND;
	}

	// now we need to know the max power and channel 2 level	
	answer = ""; 
	std::ostringstream sh_sat;
	sh_sat << "sh sat";

	ret = SendSerialCommand(port_.c_str(), sh_sat.str().c_str(), "\r");
	if (ret != DEVICE_OK) 
		return ret;
	
	double ch2Power = 0; 
	bool foundMaxPower = false;
	bool foundCh2Power = false;
	while(!isOk(answer)){
		ret = GetSerialAnswer(port_.c_str(), "\r", answer);
		if (ret != DEVICE_OK)
			return ret;

		// if the Pmax line has not been found yet
		if(!foundMaxPower){
			std::size_t found = answer.find("Pmax:"); 
			if (found!=std::string::npos){ // if Pmax: is found in the answer
				std::size_t found2 = answer.find(" mW");
				std::string s = answer.substr(found+5,found2-found-5);
				std::stringstream streamval(s);

				int pow; // what if double?
				streamval >> pow;
				*maxPower = pow;

				foundMaxPower = true;
			}
		} 

		// if the Ch2 stp line has not been found yet
		if(!foundCh2Power){
			std::size_t found = answer.find("CH2 setp:"); 
			if (found!=std::string::npos){ // if Pmax: is found in the answer
				std::size_t found2 = answer.find(" mW");
				std::string s = answer.substr(found+9,found2-found-9);
				std::stringstream streamval(s);

				streamval >> ch2Power;

				foundCh2Power = true;
			}
		} 

		// if the laser has an error
		if(isError(answer)){
			return publishError(answer);
		}
	}

	if(!foundMaxPower){
		LogMessage("Could not extract max power information.",false);
		return ADAPTER_ERROR_DATA_NOT_FOUND;
	}
	if(!foundCh2Power){
		LogMessage("Could not extract ch2 power information.",false);
		return ADAPTER_ERROR_DATA_NOT_FOUND;
	}

	// Next, if the laser is not in cw mode, then enable it
	if(!isCW){
		// set first channel to off in the configuration
		answer = ""; 
		std::ostringstream ch1_off;
		ch1_off << "config ch 1 off";
		
		ret = SendSerialCommand(port_.c_str(), ch1_off.str().c_str(), "\r");
		if (ret != DEVICE_OK) 
			return ret;

		// check that the laser doesn't report an error
		while(!isOk(answer)){
			ret = GetSerialAnswer(port_.c_str(), "\r", answer);
			if (ret != DEVICE_OK)
				return ret;

			// if the laser has an error
			if(isError(answer)){
				return publishError(answer);
			}
		}
		
		answer = ""; 
		std::ostringstream cw_on;
		cw_on << "power mode cw";
		
		ret = SendSerialCommand(port_.c_str(), cw_on.str().c_str(), "\r");
		if (ret != DEVICE_OK) 
			return ret;

		// check that the laser doesn't report an error
		while(!isOk(answer)){
			ret = GetSerialAnswer(port_.c_str(), "\r", answer);
			if (ret != DEVICE_OK)
				return ret;

			// if the laser has an error
			if(isError(answer)){
				return publishError(answer);
			}
		}	
		
		// Next, we set the power to the previously extracted value
		answer = ""; 
		std::ostringstream set_pow;
		set_pow << "set power " << ch2Power;
		
		ret = SendSerialCommand(port_.c_str(), set_pow.str().c_str(), "\r");
		if (ret != DEVICE_OK) 
			return ret;

		// check that the laser doesn't report an error
		while(!isOk(answer)){
			ret = GetSerialAnswer(port_.c_str(), "\r", answer);
			if (ret != DEVICE_OK)
				return ret;

			// if the laser has an error
			if(isError(answer)){
				return publishError(answer);
			}
		}	

		// Finally, we save the data
		answer = ""; 
		std::ostringstream save_data;
		save_data << "save data";
		
		ret = SendSerialCommand(port_.c_str(), save_data.str().c_str(), "\r");
		if (ret != DEVICE_OK) 
			return ret;

		// check that the laser doesn't report an error and that everything get saved
		bool savesys = false;
		bool savesat = false;
		while(!isOk(answer)){
			ret = GetSerialAnswer(port_.c_str(), "\r", answer);
			if (ret != DEVICE_OK)
				return ret;

			if (answer.find("SAVE:SYS") != std::string::npos){
				savesys = true;
			}	

			if (answer.find("SAVE:SAT") != std::string::npos){
				savesat = true;
			}			
			
			// if the laser has an error
			if(isError(answer)){
				return publishError(answer);
			}
		}

		if(!savesys || !savesat){
			return ADAPTER_ERROR_SAVE_DATA;
		}
		LogMessage("Entered in CW mode.", false);
	} else {
		LogMessage("Already in CW mode.", false);
	}

	ret = setTalkUsual();
	if (DEVICE_OK != ret)
		return ret;

	return DEVICE_OK;
}

int iBeamSmartCW::setNormalMode(){
	std::string answer; 
	std::ostringstream cw_off;
	cw_off << "power mode normal";

	int ret = SendSerialCommand(port_.c_str(), cw_off.str().c_str(), "\r");
	if (ret != DEVICE_OK) 
		return ret;

	// check that the laser doesn't report an error
	while(!isOk(answer)){
		ret = GetSerialAnswer(port_.c_str(), "\r", answer);
		if (ret != DEVICE_OK)
			return ret;

		// if the laser has an error
		if(isError(answer)){
			return publishError(answer);
		}
	}	

	// Finally, we save the data
	answer = ""; 
	std::ostringstream save_data;
	save_data << "save data";

	ret = SendSerialCommand(port_.c_str(), save_data.str().c_str(), "\r");
	if (ret != DEVICE_OK) 
		return ret;

	// check that the laser doesn't report an error and that everything get saved
	bool savesys = false;
	bool savesat = false;
	while(!isOk(answer)){
		ret = GetSerialAnswer(port_.c_str(), "\r", answer);
		if (ret != DEVICE_OK)
			return ret;

		if (answer.find("SAVE:SYS") != std::string::npos){
			savesys = true;
		}	

		if (answer.find("SAVE:SAT") != std::string::npos){
			savesat = true;
		}			

		// if the laser has an error
		if(isError(answer)){
			return publishError(answer);
		}
	}

	if(!savesys || !savesat){
		return ADAPTER_ERROR_SAVE_DATA;
	}

	return DEVICE_OK;
}


bool iBeamSmartCW::isOk(std::string answer){
	if(answer.empty()){
		return false;
	}

	// checks that the laser is ready to receive a new command
	if(answer.find("[OK]")  != std::string::npos){
		return true;
	}
	return false;
}

bool iBeamSmartCW::isError(std::string answer){
	// check if starts with %SYS but is not an information (as opposed to errors, warnings and fatal errors)
	if(answer.substr(0,4).compare("%SYS") == 0 && answer.find("I") == std::string::npos){ 
		return true;
	}
	return false;
}

int iBeamSmartCW::getError(std::string error){
	std::string s = error.substr(5,1);
	if(s.compare("W") == 0){ // warning
		return LASER_WARNING;
	} else if(s.compare("E") == 0) { // error
		return LASER_ERROR;
	} else if(s.compare("F") == 0) { // fatal error
		return LASER_FATAL_ERROR;
	}
	return DEVICE_OK;
}

int iBeamSmartCW::publishError(std::string error){
	std::stringstream log;
	log << "iBeamSmart error: " << error;
	LogMessage(log.str(), false);

	// Make sure that in case of an error, a [OK] prompt 
	// is not interferring with the next command (to be tested)
	PurgeComPort(port_.c_str());

	return getError(error);
}

std::string iBeamSmartCW::to_string(double x) {
	std::ostringstream x_convert;
	x_convert << x;
	return x_convert.str();
}


//---------------------------------------------------------------------------
// Getters:
//---------------------------------------------------------------------------

int iBeamSmartCW::getSerial(std::string* serial){
	std::ostringstream command;
	command << "id";

	std::string answer;
	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
	if (ret != DEVICE_OK) 
		return ret;

	// the loop should end when the laser is ready to receive a new command
	// i.e. when it answers "[OK]"
	while(!isOk(answer)){
		ret = GetSerialAnswer(port_.c_str(), "\r", answer);
		if (ret != DEVICE_OK)
			return ret;

		// if the line contains iBEAM, then extract the serial
		std::size_t found = answer.find("iBEAM");
		if (found!=std::string::npos){	
			*serial = answer.substr(found);
		}

		// if the laser has an error
		if(isError(answer)){
			return publishError(answer);
		}
	}

	return DEVICE_OK;
}

int iBeamSmartCW::getClipStatus(std::string* status){
	std::ostringstream command;
	command << "sta clip";

	std::string answer;
	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
	if (ret != DEVICE_OK) 
		return ret;

	while(!isOk(answer)){
		ret = GetSerialAnswer(port_.c_str(), "\r", answer);
		if (ret != DEVICE_OK){ 
			return ret;
		}

		// The answer is not just space or [OK]. In theory the only possible answers are FAIL, PASS and GOOD.
		if(answer.compare("\n") != 0 && answer.compare(" ") != 0 && !isOk(answer)){ 
			if(answer.find("FAIL") != std::string::npos){ // if FAIL
				if(clip_.compare("FAIL") == 0){ // if the FAIL status has already been observed
					*status = answer;
				} else { // if observe for the first time, this should prevent prompting several times the error
					return LASER_CLIP_FAIL;
				}
			} else if(answer.find("PASS") != std::string::npos || answer.find("GOOD") != std::string::npos){ // if PASS or GOOD
				*status = answer;
			}
		}

		// if the laser has an error
		if(isError(answer)){
			return publishError(answer);
		}


	}
	return DEVICE_OK;
}

int iBeamSmartCW::getPower(double* power){
	std::ostringstream command;
	std::string answer;

	command << "sh level pow";

	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
	if (ret != DEVICE_OK) 
		return ret;

	// Tag we are loking for
	std::ostringstream tag;
	tag << "CH" << 2 <<", PWR:";

	while(!isOk(answer)){
		ret = GetSerialAnswer(port_.c_str(), "\r", answer);
		if (ret != DEVICE_OK){ 
			return ret;
		}

		// test if the tag is found in the answer
		std::size_t found = answer.find(tag.str());
		if(found!=std::string::npos){	
			std::size_t found2 = answer.find(" mW");

			std::string s = answer.substr(found+9,found2-found-9); // length of the tag = 9
			std::stringstream streamval(s);
			double pow;
			streamval >> pow;

			*power = pow;
		}

		// if the laser has an error
		if(isError(answer)){
			return publishError(answer);
		}
	}

	return DEVICE_OK;
}

int iBeamSmartCW::getFineStatus(bool* status){
	std::ostringstream command;
	std::string answer;

	command << "sta fine";

	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
	if (ret != DEVICE_OK) 
		return ret;

	while(!isOk(answer)){
		ret = GetSerialAnswer(port_.c_str(), "\r", answer);
		if (ret != DEVICE_OK){ 
			return ret;
		}

		if (answer.find("ON") != std::string::npos){	
			*status = true;
		} else if (answer.find("OFF") != std::string::npos){	
			*status = false;
		}

		// if the laser has an error
		if(isError(answer)){
			return publishError(answer);
		}
	}

	return DEVICE_OK;
}

int iBeamSmartCW::getFinePercentage(char fine, double* percentage){
	std::ostringstream command;
	std::string answer;

	command << "sh data";

	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
	if (ret != DEVICE_OK) 
		return ret;

	// Tag we are looking for
	std::ostringstream tag;
	tag << "fine " << fine;

	bool foundline = false;
	while(!isOk(answer)){
		ret = GetSerialAnswer(port_.c_str(), "\r", answer);
		if (ret != DEVICE_OK){ 
			return ret;
		}

		if(!foundline){
			std::size_t found = answer.find(tag.str());
			if (found!=std::string::npos){	
				std::size_t found1 = answer.find("-> "); // length = 3
				std::size_t found2 = answer.find(" %");
				std::string s = answer.substr(found1+3,found2-found1-3);
				std::stringstream streamval(s);

				double perc;
				streamval >> perc;

				*percentage = perc;

				foundline = true;
			}
		}

		// if the laser has an error
		if(isError(answer)){
			return publishError(answer);
		}
	}
		
	if(!foundline){
		LogMessage("Could not extract fine percentage from CMD>sh data",false);
		return ADAPTER_ERROR_DATA_NOT_FOUND;
	}

	return DEVICE_OK;
}

int iBeamSmartCW::getExtStatus(bool* status){
	std::ostringstream command;
	std::string answer;

	command << "sta ext"; // this command doesn't appear in the manual but is available from "help" comand

	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
	if (ret != DEVICE_OK) 
		return ret;

	while(!isOk(answer)){
		ret = GetSerialAnswer(port_.c_str(), "\r", answer);
		if (ret != DEVICE_OK){ 
			return ret;
		}

		if (answer.find("ON") != std::string::npos){	
			*status = true;
		} else if (answer.find("OFF") != std::string::npos){	
			*status = false;
		}

		// if the laser has an error
		if(isError(answer)){
			return publishError(answer);
		}
	}

	return DEVICE_OK;
}

int iBeamSmartCW::getLaserStatus(bool* status){
	std::ostringstream command;
	std::string answer;

	command << "sta la";

	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
	if (ret != DEVICE_OK) 
		return ret;

	while(!isOk(answer)){
		ret = GetSerialAnswer(port_.c_str(), "\r", answer);
		if (ret != DEVICE_OK){ 
			return ret;
		}

		if (answer.find("ON") != std::string::npos){	
			*status = true;
		} else if (answer.find("OFF") != std::string::npos){	
			*status = false;
		}

		// if the laser has an error
		if(isError(answer)){
			return publishError(answer);
		}
	}

	return DEVICE_OK;
}

int iBeamSmartCW::getFirmwareVersion(std::string* version){
	std::ostringstream command;
	std::string answer;

	command << "ver";

	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
	if (ret != DEVICE_OK) 
		return ret;

	while(!isOk(answer)){
		ret = GetSerialAnswer(port_.c_str(), "\r", answer);
		if (ret != DEVICE_OK){ 
			return ret;
		}

		if(answer.find("iB") != std::string::npos){	
			*version = answer;
		}

		// if the laser has an error
		if(isError(answer)){
			return publishError(answer);
		}
	}

	return DEVICE_OK;
}


//---------------------------------------------------------------------------
// Setters:
//---------------------------------------------------------------------------

int iBeamSmartCW::setLaserOnOff(bool b){
	std::ostringstream command;
	std::string answer;

	if(b){
		command << "la on";
	} else {
		command << "la off";
	}

	// send command
	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");		
	if (ret != DEVICE_OK){ 
		return ret;
	}

	// get answer until [OK]
	while(!isOk(answer)){
		ret = GetSerialAnswer(port_.c_str(), "\r", answer);
		if (ret != DEVICE_OK){ 
			return ret;
		}

		// if the laser has an error
		if(isError(answer)){
			return publishError(answer);
		}
	}
	
	return DEVICE_OK;
}

int iBeamSmartCW::setPrompt(bool prompt){
	std::ostringstream command;
	std::string answer;

	if(!prompt){
		command << "prom off";

		// send command
		int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");		
		if (ret != DEVICE_OK){ 
			return ret;
		}

		// get answer until [OK]
		while(!isOk(answer)){
			ret = GetSerialAnswer(port_.c_str(), "\r", answer);
			if (ret != DEVICE_OK){ 
				return ret;
			}

			// if the laser has an error
			if(isError(answer)){
				return publishError(answer);
			}
		}
	} else {
		command << "prom on";	
		
		// send command
		int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");		
		if (ret != DEVICE_OK){ 
			return ret;
		}
	}
	
	return DEVICE_OK;
}

int iBeamSmartCW::setTalkUsual(){
	std::ostringstream command;
	std::string answer;

	command << "talk usual";

	// send command
	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");		
	if (ret != DEVICE_OK){ 
		return ret;
	}

	// get answer until [OK]
	while(!isOk(answer)){
		ret = GetSerialAnswer(port_.c_str(), "\r", answer);
		if (ret != DEVICE_OK){ 
			return ret;
		}

		// if the laser has an error
		if(isError(answer)){
			return publishError(answer);
		}
	}

	return DEVICE_OK;
}

int iBeamSmartCW::setTalkGabby(){
	std::ostringstream command;
	std::string answer;

	command << "talk gabby";

	// send command
	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");		
	if (ret != DEVICE_OK){ 
		return ret;
	}

	// get answer until [OK]
	while(!isOk(answer)){
		ret = GetSerialAnswer(port_.c_str(), "\r", answer);
		if (ret != DEVICE_OK){ 
			return ret;
		}

		// if the laser has an error
		if(isError(answer)){
			return publishError(answer);
		}
	}

	return DEVICE_OK;
}

int iBeamSmartCW::setPower(double pow){
	std::ostringstream command;
	std::string answer;

	if(pow<0 || pow>maxpower_){
		return ADAPTER_POWER_OUTSIDE_RANGE;
	}

	command << "set pow "<< pow;

	// send command
	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");		
	if (ret != DEVICE_OK){ 
		return ret;
	}

	// get answer until [OK]
	while(!isOk(answer)){
		ret = GetSerialAnswer(port_.c_str(), "\r", answer);
		if (ret != DEVICE_OK){ 
			return ret;
		}

		// if the laser has an error
		if(isError(answer)){
			return publishError(answer);
		}
	}

	return DEVICE_OK;
}

int iBeamSmartCW::setFineA(double perc){
	std::ostringstream command;
	std::string answer;

	if(perc<0 || perc>100){
		return ADAPTER_PERC_OUTSIDE_RANGE;
	}

	command << "fine a " << perc;
	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
	if (ret != DEVICE_OK){ 
		return ret;
	}

	// get answer until [OK]
	while(!isOk(answer)){
		ret = GetSerialAnswer(port_.c_str(), "\r", answer);
		if (ret != DEVICE_OK){ 
			return ret;
		}

		// if the laser has an error
		if(isError(answer)){
			return publishError(answer);
		}
	}

	return DEVICE_OK;
}

int iBeamSmartCW::setFineB(double perc){
	std::ostringstream command;
	std::string answer;

	if(perc<0 || perc>100){
		return ADAPTER_PERC_OUTSIDE_RANGE;
	}

	command << "fine b " << perc;
	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
	if (ret != DEVICE_OK){ 
		return ret;
	}

	// get answer until [OK]
	while(!isOk(answer)){
		ret = GetSerialAnswer(port_.c_str(), "\r", answer);
		if (ret != DEVICE_OK){ 
			return ret;
		}

		// if the laser has an error
		if(isError(answer)){
			return publishError(answer);
		}
	}

	return DEVICE_OK;
}

int iBeamSmartCW::enableExt(bool b){
	std::ostringstream command;
	std::string answer;

	if(b){
		command << "en x";
	} else {
		command << "di x";
	}

	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
	if (ret != DEVICE_OK){ 
		return ret;
	}

	// get answer until [OK]
	while(!isOk(answer)){
		ret = GetSerialAnswer(port_.c_str(), "\r", answer);
		if (ret != DEVICE_OK){ 
			return ret;
		}

		// if the laser has an error
		if(isError(answer)){
			return publishError(answer);
		}
	}

	return DEVICE_OK;
}

int iBeamSmartCW::enableFine(bool b){
	std::ostringstream command;
	std::string answer;

	if(b){
		command << "fine on";
	} else {
		command << "fine off";
	}

	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
	if (ret != DEVICE_OK){ 
		return ret;
	}

	// get answer until [OK]
	while(!isOk(answer)){
		ret = GetSerialAnswer(port_.c_str(), "\r", answer);
		if (ret != DEVICE_OK){ 
			return ret;
		}

		// if the laser has an error
		if(isError(answer)){
			return publishError(answer);
		}
	}

	return DEVICE_OK;
}

//---------------------------------------------------------------------------
// Initial or read only properties
//---------------------------------------------------------------------------

int iBeamSmartCW::OnPort(MM::PropertyBase* pProp , MM::ActionType eAct)
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

int iBeamSmartCW::OnClip(MM::PropertyBase* pProp , MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		int ret = getClipStatus(&clip_);
		if(ret != DEVICE_OK)
			return ret;
		pProp->Set(clip_.c_str());
	}

	return DEVICE_OK;
}


//---------------------------------------------------------------------------
// Action handlers
//---------------------------------------------------------------------------

int iBeamSmartCW::OnLaserOnOff(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet){ 
		int ret = getLaserStatus(&laserOn_);
		if(ret != DEVICE_OK)
			return ret;

		if(laserOn_){
			pProp->Set("On");
		} else {
			pProp->Set("Off");
		}
	} else if (eAct == MM::AfterSet){
		std::string status;
		pProp->Get(status);

		if(status.compare("On") == 0){
			laserOn_ = true;
		} else {
			laserOn_ = false;
		}

		int ret = setLaserOnOff(laserOn_);
		if(ret != DEVICE_OK)
			return ret;
	}
	return DEVICE_OK;
}

int iBeamSmartCW::OnPower(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet){ 
		int ret = getPower(&power_);
		if(ret != DEVICE_OK)
			return ret;

		pProp->Set(power_);
	} else if (eAct == MM::AfterSet){
		double pow;
		pProp->Get(pow);

		power_ = pow;
		int ret = setPower(power_);
		if(ret != DEVICE_OK)
			return ret;
	}

	return DEVICE_OK;
}

int iBeamSmartCW::OnEnableExt(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet){ 
		int ret = getExtStatus(&extOn_);
		if(ret != DEVICE_OK)
			return ret;

		if(extOn_){
			pProp->Set("On");
		} else {
			pProp->Set("Off");
		}
	} else if (eAct == MM::AfterSet){
		std::string status;
		pProp->Get(status);

		if(status.compare("On") == 0){
			extOn_ = true;
		} else {
			extOn_ = false;
		}

		int ret = enableExt(extOn_);
		if(ret != DEVICE_OK)
			return ret;
	}

	return DEVICE_OK;
}

int iBeamSmartCW::OnEnableFine(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet){ 
		int ret = getFineStatus(&fineOn_);
		if(ret != DEVICE_OK)
			return ret;

		if(fineOn_){
			pProp->Set("On");
		} else {
			pProp->Set("Off");
		}
	} else if (eAct == MM::AfterSet){
		std::string status;
		pProp->Get(status);

		if(status.compare("On") == 0){
			fineOn_ = true;
		} else {
			fineOn_ = false;
		}

		int ret = enableFine(fineOn_);
		if(ret != DEVICE_OK)
			return ret; ;
	}

	return DEVICE_OK;
}

int iBeamSmartCW::OnFineA(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet){
		int ret = getFinePercentage('a', &finea_);
		if(ret != DEVICE_OK)
			return ret;

		pProp->Set(finea_);
	} else if (eAct == MM::AfterSet){
		double perc;
		pProp->Get(perc);

		finea_ = perc;
		int ret = setFineA(finea_);
		if(ret != DEVICE_OK)
			return ret;
	}

	return DEVICE_OK;
}

int iBeamSmartCW::OnFineB(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet){ 
		int ret = getFinePercentage('b', &finea_);
		if(ret != DEVICE_OK)
			return ret;

		pProp->Set(fineb_);
	} else if (eAct == MM::AfterSet){
		double perc;
		pProp->Get(perc);

		fineb_ = perc;
		int ret = setFineB(fineb_);
		if(ret != DEVICE_OK)
			return ret;
	}

	return DEVICE_OK;
}


//-----------------------------------------------------------------------------
// iBeam smart device adapter
//-----------------------------------------------------------------------------

iBeamSmartNormal::iBeamSmartNormal():
	port_("Undefined"),
	initialized_(false),
	busy_(false)
{
	InitializeDefaultErrorMessages();
	SetErrorText(ERR_PORT_CHANGE_FORBIDDEN, "You can't change the port after device has been initialized.");
	SetErrorText(LASER_WARNING, "The laser has emitted a warning error, please refer to the CoreLog for the warning code.");
	SetErrorText(LASER_ERROR, "The laser has emitted an error, please refer to the CoreLog for the error code.");
	SetErrorText(LASER_FATAL_ERROR, "The laser has emitted a fatal error, please refer to the CoreLog for the error code.");
	SetErrorText(ADAPTER_ERROR_PASSWORD, "Password to service level failed, the device adapter will not function properly.");
	SetErrorText(ADAPTER_ERROR_SAVE_DATA, "Error when saving system data in the laser.");

	// Description
	CreateProperty(MM::g_Keyword_Description, "Restore normal mode", MM::String, true, 0, true);

	// Port
	CPropertyAction* pAct = new CPropertyAction (this, &iBeamSmartNormal::OnPort);
	CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);
}

iBeamSmartNormal::~iBeamSmartNormal()
{
	Shutdown();
}

void iBeamSmartNormal::GetName(char* Name) const
{
	CDeviceUtils::CopyLimitedString(Name, g_DeviceiBeamSmartNameNormal);
}

int iBeamSmartNormal::Initialize()
{
	// Make sure prompting ("CMD>") is off (so we get [OK] or error for every answer) 
	// and "talk" is set to usual.
	// Otherwise we will get infinite loops (because we are looking for "[OK]")
	// and some of the data will not be found (e.g. enable EXT from CMD>sh data)
	int ret = setPrompt(false);
	if (DEVICE_OK != ret)
		return ret;
	
	// Set the laser to normal mode
	ret = setNormalMode();
	if (DEVICE_OK != ret)
		return ret;

	//////////////////////////////////////////////
	// Read only properties

	// Serial number
	ret = getSerial(&serial_); 
	if (DEVICE_OK != ret)
		return ret;

	ret = CreateProperty("Serial ID", serial_.c_str(), MM::String, true);
	if (DEVICE_OK != ret)
		return ret;
	
	// There should be no communication after that, so turn prompt on again
	// otherwise the Topas software might give a time out error after quitting
	// micro-manager
	setPrompt(true);

	initialized_ = true;
	return DEVICE_OK;
}

int iBeamSmartNormal::Shutdown()
{
	if (initialized_)
	{
		initialized_ = false;	 
	}
	return DEVICE_OK;
}

bool iBeamSmartNormal::Busy()
{
	return busy_;
}


//---------------------------------------------------------------------------
// Conveniance functions:
//---------------------------------------------------------------------------

int iBeamSmartNormal::setNormalMode(){
	setTalkGabby();

	std::string answer; 
	std::ostringstream cw_off;
	cw_off << "power mode normal";

	int ret = SendSerialCommand(port_.c_str(), cw_off.str().c_str(), "\r");
	if (ret != DEVICE_OK) 
		return ret;

	// check that the laser doesn't report an error
	while(!isOk(answer)){
		ret = GetSerialAnswer(port_.c_str(), "\r", answer);
		if (ret != DEVICE_OK)
			return ret;

		// if the laser has an error
		if(isError(answer)){
			return publishError(answer);
		}
	}	

	// Finally, we save the data
	answer = ""; 
	std::ostringstream save_data;
	save_data << "save data";

	ret = SendSerialCommand(port_.c_str(), save_data.str().c_str(), "\r");
	if (ret != DEVICE_OK) 
		return ret;

	// check that the laser doesn't report an error and that everything get saved
	bool savesys = false;
	bool savesat = false;
	while(!isOk(answer)){
		ret = GetSerialAnswer(port_.c_str(), "\r", answer);
		if (ret != DEVICE_OK)
			return ret;

		if (answer.find("SAVE:SYS") != std::string::npos){
			savesys = true;
		}	

		if (answer.find("SAVE:SAT") != std::string::npos){
			savesat = true;
		}			

		// if the laser has an error
		if(isError(answer)){
			return publishError(answer);
		}
	}

	if(!savesys || !savesat){
		return ADAPTER_ERROR_SAVE_DATA;
	}

	setTalkUsual();

	return DEVICE_OK;
}

int iBeamSmartNormal::setTalkUsual(){
	std::ostringstream command;
	std::string answer;

	command << "talk usual";

	// send command
	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");		
	if (ret != DEVICE_OK){ 
		return ret;
	}

	// get answer until [OK]
	while(!isOk(answer)){
		ret = GetSerialAnswer(port_.c_str(), "\r", answer);
		if (ret != DEVICE_OK){ 
			return ret;
		}

		// if the laser has an error
		if(isError(answer)){
			return publishError(answer);
		}
	}

	return DEVICE_OK;
}

int iBeamSmartNormal::setTalkGabby(){
	std::ostringstream command;
	std::string answer;

	command << "talk gabby";

	// send command
	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");		
	if (ret != DEVICE_OK){ 
		return ret;
	}

	// get answer until [OK]
	while(!isOk(answer)){
		ret = GetSerialAnswer(port_.c_str(), "\r", answer);
		if (ret != DEVICE_OK){ 
			return ret;
		}

		// if the laser has an error
		if(isError(answer)){
			return publishError(answer);
		}
	}

	return DEVICE_OK;
}


bool iBeamSmartNormal::isOk(std::string answer){
	if(answer.empty()){
		return false;
	}

	// checks that the laser is ready to receive a new command
	if(answer.find("[OK]")  != std::string::npos){
		return true;
	}
	return false;
}

bool iBeamSmartNormal::isError(std::string answer){
	// check if starts with %SYS but is not an information (as opposed to errors, warnings and fatal errors)
	if(answer.substr(0,4).compare("%SYS") == 0 && answer.find("I") == std::string::npos){ 
		return true;
	}
	return false;
}

int iBeamSmartNormal::getError(std::string error){
	std::string s = error.substr(5,1);
	if(s.compare("W") == 0){ // warning
		return LASER_WARNING;
	} else if(s.compare("E") == 0) { // error
		return LASER_ERROR;
	} else if(s.compare("F") == 0) { // fatal error
		return LASER_FATAL_ERROR;
	}
	return DEVICE_OK;
}

int iBeamSmartNormal::publishError(std::string error){
	std::stringstream log;
	log << "iBeamSmart error: " << error;
	LogMessage(log.str(), false);

	// Make sure that in case of an error, a [OK] prompt 
	// is not interferring with the next command (to be tested)
	PurgeComPort(port_.c_str());

	return getError(error);
}

std::string iBeamSmartNormal::to_string(double x) {
	std::ostringstream x_convert;
	x_convert << x;
	return x_convert.str();
}


//---------------------------------------------------------------------------
// Getters:
//---------------------------------------------------------------------------

int iBeamSmartNormal::getSerial(std::string* serial){
	std::ostringstream command;
	command << "id";

	std::string answer;
	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
	if (ret != DEVICE_OK) 
		return ret;

	// the loop should end when the laser is ready to receive a new command
	// i.e. when it answers "[OK]"
	while(!isOk(answer)){
		ret = GetSerialAnswer(port_.c_str(), "\r", answer);
		if (ret != DEVICE_OK)
			return ret;

		// if the line contains iBEAM, then extract the serial
		std::size_t found = answer.find("iBEAM");
		if (found!=std::string::npos){	
			*serial = answer.substr(found);
		}

		// if the laser has an error
		if(isError(answer)){
			return publishError(answer);
		}
	}

	return DEVICE_OK;
}

//---------------------------------------------------------------------------
// Setters:
//---------------------------------------------------------------------------

int iBeamSmartNormal::setPrompt(bool prompt){
	std::ostringstream command;
	std::string answer;

	if(!prompt){
		command << "prom off";

		// send command
		int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");		
		if (ret != DEVICE_OK){ 
			return ret;
		}

		// get answer until [OK]
		while(!isOk(answer)){
			ret = GetSerialAnswer(port_.c_str(), "\r", answer);
			if (ret != DEVICE_OK){ 
				return ret;
			}

			// if the laser has an error
			if(isError(answer)){
				return publishError(answer);
			}
		}
	} else {
		command << "prom on";

		// send command
		int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");		
		if (ret != DEVICE_OK){ 
			return ret;
		}
	}
	
	return DEVICE_OK;
}

//---------------------------------------------------------------------------
// Initial or read only properties
//---------------------------------------------------------------------------

int iBeamSmartNormal::OnPort(MM::PropertyBase* pProp , MM::ActionType eAct)
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
