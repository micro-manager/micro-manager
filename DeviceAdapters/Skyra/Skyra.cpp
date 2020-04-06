///////////////////////////////////////////////////////////////////////////////
// FILE:          Skyra.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Controls Cobolt and Skyra lasers through a serial port
// COPYRIGHT:     University of Massachusetts Medical School, 2019
// LICENSE:       LGPL
// LICENSE:       https://www.gnu.org/licenses/lgpl-3.0.txt
// AUTHOR:        Karl Bellve, Karl.Bellve@umassmed.edu, Karl.Bellve@gmail.com
//               
//

#include "Skyra.h"


///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
	RegisterDevice(g_DeviceSkyraName, MM::ShutterDevice, "Skyra Laser Controller");
}
MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
	if (deviceName == 0)
		return 0;

	if (strcmp(deviceName, g_DeviceSkyraName) == 0)
	{
		return new Skyra;
	}

	return 0;
}
MODULE_API void DeleteDevice(MM::Device* pDevice)
{
	delete pDevice;
}
///////////////////////////////////////////////////////////////////////////////
// Skyra
///////////////////////////////////////////////////////////////////////////////
Skyra::Skyra() :
bInitialized_(false),
	bBusy_(false),
	bModulation_(true),
	bAnalogModulation_(false),
	bDigitalModulation_(false),
	bInternalModulation_(false),
	nSkyra_(0),
	serialNumber_(g_Default_Integer),
	version_(g_Default_Integer),
	hours_(g_Default_Integer),
	keyStatus_(g_PropertyOff),
	Current_(g_Default_Float),
	currentSetPoint_(g_Default_Float),
	currentStatus_(g_Default_Float),
	currentModulationMinimum_(g_Default_Float),
	Power_(g_Default_Float),
	powerSetPoint_(g_Default_Float),
	powerStatus_(g_Default_Float),
	controlMode_(g_Default_ControlMode),
	ID_(g_Default_Empty),
	laserStatus_(g_Default_String),
	Type_(g_Default_String),
	autostartStatus_(g_Default_String),
	interlock_ (g_Default_String),
	fault_(g_Default_String),
	identity_(g_Default_String),
	port_(g_Default_String)
{
	InitializeDefaultErrorMessages();
	SetErrorText(ERR_PORT_CHANGE_FORBIDDEN, "You can't change the port after device has been initialized.");

	// Name
	CreateProperty(MM::g_Keyword_Name, g_DeviceSkyraName, MM::String, true); 

	// Description
	CreateProperty(MM::g_Keyword_Description, g_DeviceSkyraDescription, MM::String, true);

	// Port
	CPropertyAction* pAct = new CPropertyAction (this, &Skyra::OnPort);
	CreateProperty(MM::g_Keyword_Port, g_Default_String, MM::String, false, pAct, true);

	// Company Name
	CreateProperty("Vendor", g_DeviceVendorName, MM::String, true);
}
Skyra::~Skyra()
{
	Shutdown();
}
void Skyra::GetName(char* Name) const
{
	CDeviceUtils::CopyLimitedString(Name, g_DeviceSkyraName);
}
bool Skyra::SupportsDeviceDetection(void)
{
	return true;
}
MM::DeviceDetectionStatus Skyra::DetectDevice(void)
{
	// Code modified from Nico's Arduino Device Adapter

	if (bInitialized_)
		return MM::CanCommunicate;

	// all conditions must be satisfied...
	MM::DeviceDetectionStatus result = MM::Misconfigured;
	char answerTO[MM::MaxStrLength];

	try
	{
		std::string portLowerCase = GetPort();
		for( std::string::iterator its = portLowerCase.begin(); its != portLowerCase.end(); ++its)
		{
			*its = (char)tolower(*its);
		}
		if( 0< portLowerCase.length() &&  0 != portLowerCase.compare("undefined")  && 0 != portLowerCase.compare("unknown") )
		{
			result = MM::CanNotCommunicate;
			// record the default answer time out
			GetCoreCallback()->GetDeviceProperty(GetPort().c_str(), "AnswerTimeout", answerTO);
			CDeviceUtils::SleepMs(2000);
			GetCoreCallback()->SetDeviceProperty(GetPort().c_str(), MM::g_Keyword_Handshaking, g_PropertyOff);
			GetCoreCallback()->SetDeviceProperty(GetPort().c_str(), MM::g_Keyword_StopBits, "1");
			GetCoreCallback()->SetDeviceProperty(GetPort().c_str(), "AnswerTimeout", "500.0");
			GetCoreCallback()->SetDeviceProperty(GetPort().c_str(), "DelayBetweenCharsMs", g_Default_Integer);
			MM::Device* pS = GetCoreCallback()->GetDevice(this, GetPort().c_str());

			// This next Function Block is adapted from Jon Daniels ASISStage Device Adapter
			std::vector<std::string> possibleBauds;
			possibleBauds.push_back("115200");
			possibleBauds.push_back("19200");
			for( std::vector< std::string>::iterator bit = possibleBauds.begin(); bit!= possibleBauds.end(); ++bit )
			{
				GetCoreCallback()->SetDeviceProperty(GetPort().c_str(), MM::g_Keyword_BaudRate, (*bit).c_str());
				pS->Initialize();
				PurgeComPort(GetPort().c_str());
				// First check if the Cobolt/Skyra can communicate at 115,200 baud.
				if (ConfirmIdentity() == DEVICE_OK) {
					result = MM::CanCommunicate;
				}
				pS->Shutdown();
				if (MM::CanCommunicate == result) break;
				else CDeviceUtils::SleepMs(10);
			}

			// always restore the AnswerTimeout to the default
			GetCoreCallback()->SetDeviceProperty(GetPort().c_str(), "AnswerTimeout", answerTO);
		}
	}
	catch(...)
	{
		//LogMessage("Exception in DetectDevice!",false);
	}

	return result;
}
int Skyra::ConfirmIdentity()
{

	std::string answer;

	answer = SerialCommand("@cob0");
	if (answer == "OK") {
		answer = SerialCommand("l0");
		if (answer == "OK") {
			return DEVICE_OK;
		}
		else DEVICE_SERIAL_INVALID_RESPONSE;
	}
	else return DEVICE_SERIAL_INVALID_RESPONSE;

	return DEVICE_ERR;
}
int Skyra::DetectInstalledDevices()
{
	// Code modified from Nico's Arduino Device Adapter
	if (MM::CanCommunicate == DetectDevice())
	{
		std::vector<std::string> peripherals;
		peripherals.clear();
		peripherals.push_back(g_DeviceSkyraName);
		for (size_t i=0; i < peripherals.size(); i++)
		{
			MM::Device* pDev = ::CreateDevice(peripherals[i].c_str());
			if (pDev)
			{
				//AddInstalledDevice(pDev);
			}
		}
	}

	return DEVICE_OK;
}
int Skyra::Initialize()
{   
	CPropertyAction* pAct;

	//AllLasersOn(true);
	pAct = new CPropertyAction (this, &Skyra::OnAllLasers);
	int nRet = CreateProperty(g_PropertySkyraAllLaser, laserStatus_.c_str(), MM::String, false, pAct);
	if (DEVICE_OK != nRet)
		return nRet;

	std::vector<std::string> commands;
	commands.clear();
	commands.push_back(g_PropertyOff);
	commands.push_back(g_PropertyOn);
	SetAllowedValues(g_PropertySkyraAllLaser, commands);

	pAct = new CPropertyAction (this, &Skyra::OnLaserHelp1);
	nRet = CreateProperty("All Lasers Help #1", g_PropertySkyraAutostartHelp1, MM::String, true, pAct);
	if (DEVICE_OK != nRet)
		return nRet;

	pAct = new CPropertyAction (this, &Skyra::OnLaserHelp2);
	nRet = CreateProperty("All Lasers Help #2", g_PropertySkyraAutostartHelp2, MM::String, true, pAct);
	if (DEVICE_OK != nRet)
		return nRet;

	pAct = new CPropertyAction (this, &Skyra::OnHours);
	nRet = CreateProperty("Hours", "0.00", MM::String, true, pAct);
	if (DEVICE_OK != nRet)
		return nRet;

	pAct = new CPropertyAction (this, &Skyra::OnKeyStatus);
	nRet = CreateProperty("Key On/Off", "Off", MM::String, true, pAct);
	if (DEVICE_OK != nRet)
		return nRet;

	pAct = new CPropertyAction (this, &Skyra::OnInterlock);
	nRet = CreateProperty("Interlock", "Interlock Open", MM::String, true, pAct);
	if (DEVICE_OK != nRet)
		return nRet;

	pAct = new CPropertyAction (this, &Skyra::OnFault);
	nRet = CreateProperty("Fault", "No Fault", MM::String, true, pAct);
	if (DEVICE_OK != nRet)
		return nRet;

	pAct = new CPropertyAction (this, &Skyra::OnOperatingStatus);
	nRet = CreateProperty("Operating Status", g_Default_String, MM::String, true, pAct);
	if (DEVICE_OK != nRet)
		return nRet;

	// The following should never change, so read once and remember
	serialNumber_ = SerialCommand("sn?");
	pAct = new CPropertyAction (this, &Skyra::OnSerialNumber);
	nRet = CreateProperty("Serial Number", serialNumber_.c_str(), MM::String, true, pAct);
	if (DEVICE_OK != nRet)
		return nRet;

	model_ = SerialCommand("glm?");
	pAct = new CPropertyAction (this, &Skyra::OnModel);
	nRet = CreateProperty("Model", model_.c_str(), MM::String, true, pAct);
	if (DEVICE_OK != nRet)
		return nRet;

	version_ = SerialCommand("ver?");
	pAct = new CPropertyAction (this, &Skyra::OnVersion);
	nRet = CreateProperty("Firmware Version", version_.c_str(), MM::String, true, pAct);
	if (DEVICE_OK != nRet)
		return nRet;

	// even though this should be only set by OEM, users requested it. - kdb
	// Also, individual lasers in a Skyra doesn't appear to support Autostart, despite documentation saying otherwise :(
	// But make autostart available for those who can't modulate their lasers.
	AutostartStatus();
	pAct = new CPropertyAction (this, &Skyra::OnAutoStart);
	nRet = CreateProperty(g_PropertySkyraAutostart, autostartStatus_.c_str(), MM::String, false, pAct);
	if (nRet != DEVICE_OK)
		return nRet;

	SetAllowedValues(g_PropertySkyraAutostart, commands);
	pAct = new CPropertyAction (this, &Skyra::OnAutoStartStatus);
	nRet = CreateProperty(g_PropertySkyraAutostartStatus, autostartStatus_.c_str(), MM::String, true, pAct);
	if (DEVICE_OK != nRet)
		return nRet;

	CreateProperty("Autostart Help", g_PropertySkyraAutostartHelp, MM::String, true);
	commands.clear();
	commands.push_back(g_PropertyEnabled);
	commands.push_back(g_PropertyDisabled);
	SetAllowedValues(g_PropertySkyraAutostart, commands);


	// This is where check if this is an actual Skyra, with multipe lasers.
	std::string answer;

	std::stringstream command;

	// get default type if not Skyra
	Type_ = SerialCommand("glm?");

	for (int x = 1; x < 5; x++) 
	{
		command.str("");
		command << x;
		ID_ = command.str();
		command << "glm?";
		answer = SerialCommand(command.str());

		// We do this because there might be IDs available, but without an actual laser installed. 
		// The only way to tell if it is installed is to look at the model number.
		if (answer.compare(g_Default_Integer) != 0){
			command.str("");
			command << x << "glw?";
			answer = SerialCommand(command.str());
			if ((answer).compare(g_Msg_UNSUPPORTED_COMMAND) != 0) {
				nSkyra_++;
				waveLength_ = answer;
				waveLengths_.push_back(waveLength_);
				IDs_.push_back(ID_);
			} 
		}
	}


	if (nSkyra_) 
	{
		// Set Default Laser to the first ID
		waveLength_ = waveLengths_.front();
		ID_ = IDs_.front();

		pAct = new CPropertyAction (this, &Skyra::OnLaserStatus);
		nRet = CreateProperty(g_PropertySkyraLaserStatus, g_Default_String, MM::String, true, pAct);

		pAct = new CPropertyAction (this, &Skyra::OnLaser);
		nRet = CreateProperty(g_PropertySkyraLaser, g_Default_String, MM::String, false, pAct);

		pAct = new CPropertyAction (this, &Skyra::OnWaveLength);
		nRet = CreateProperty(g_PropertySkyraWavelength, waveLength_.c_str(), MM::String, false, pAct);
		SetAllowedValues(g_PropertySkyraWavelength, waveLengths_);

		pAct = new CPropertyAction (this, &Skyra::OnActive);
		nRet = CreateProperty(g_PropertySkyraActive, g_Default_String, MM::String, false, pAct);

		pAct = new CPropertyAction (this, &Skyra::OnLaserType);
		nRet = CreateProperty(g_PropertySkyraLaserType,  Type_.c_str(), MM::String, true, pAct);

		commands.clear();
		commands.push_back(g_PropertyActive);
		commands.push_back(g_PropertyInactive);
		SetAllowedValues(g_PropertySkyraActive, commands);
		SetAllowedValues(g_PropertySkyraActiveStatus, commands);

		commands.clear();
		commands.push_back(g_PropertyOn);
		commands.push_back(g_PropertyOff);
		SetAllowedValues(g_PropertySkyraLaser, commands);
	} 
	else
	{
		//check if Single Laser supports supports 'em' command, modulation mode
		if (SerialCommand ("em").compare(g_Msg_UNSUPPORTED_COMMAND) == 0) bModulation_ = false;
	}

	// POWER
	// current setpoint power, not current output power
	pAct = new CPropertyAction (this, &Skyra::OnPower);
	nRet = CreateProperty(g_PropertySkyraPower, g_Default_Float, MM::Float, false, pAct);
	if (DEVICE_OK != nRet)
		return nRet;

	// output, not setpoint power
	pAct = new CPropertyAction (this, &Skyra::OnPowerStatus);
	nRet = CreateProperty(g_PropertySkyraPowerStatus, powerStatus_.c_str(), MM::Float, true, pAct);
	if (DEVICE_OK != nRet)
		return nRet;

	// setpoint power, not current output
	pAct = new CPropertyAction (this, &Skyra::OnPowerSetPoint);
	nRet = CreateProperty(g_PropertySkyraPowerOn, powerSetPoint_.c_str(),  MM::Float, true, pAct);
	if (DEVICE_OK != nRet)
		return nRet;

	// Power Maximum
	powerMaximum_ = SerialCommand (ID_ + "gmlp?");
	pAct = new CPropertyAction (this, &Skyra::OnPowerMaximum);
	nRet = CreateProperty(g_PropertySkyraPowerMaximum, powerMaximum_.c_str(), MM::Float, true, pAct);
	if (DEVICE_OK != nRet)
		return nRet;
	
	// Set Property Limits for Power
	double powerMaximum = std::stof(powerMaximum_);
	SetPropertyLimits(g_PropertySkyraPower,0,powerMaximum);

	CreateProperty("Power Units", g_PropertySkyraPowerHelp, MM::String, true);
	CreateProperty("Power On Help", g_PropertySkyraPowerHelpOn, MM::String, true);

	/// CURRENT
	pAct = new CPropertyAction (this, &Skyra::OnCurrent);
	nRet = CreateProperty(g_PropertySkyraCurrent, g_Default_Float, MM::Float, false, pAct);
	if (DEVICE_OK != nRet)
		return nRet;

	pAct = new CPropertyAction (this, &Skyra::OnCurrentStatus);
	nRet = CreateProperty(g_PropertySkyraCurrentStatus, g_Default_Float, MM::String, true, pAct);
	if (DEVICE_OK != nRet)
		return nRet;
	
	pAct = new CPropertyAction (this, &Skyra::OnCurrentModulationMinimum);
	nRet = CreateProperty(g_PropertySkyraCurrentModulationMinimum, g_Default_Float, MM::Float, false, pAct);
	if (DEVICE_OK != nRet)
		return nRet;

	pAct = new CPropertyAction (this, &Skyra::OnCurrentModulationMaximum);
	nRet = CreateProperty(g_PropertySkyraCurrentModulationMaximum, g_Default_Float, MM::Float, false, pAct);
	if (DEVICE_OK != nRet)
		return nRet;

	pAct = new CPropertyAction (this, &Skyra::OnCurrentMaximum);
	nRet = CreateProperty(g_PropertySkyraCurrentMaximum, g_Default_Float, MM::Float, true, pAct);
	if (DEVICE_OK != nRet)
		return nRet;

	pAct = new CPropertyAction (this, &Skyra::OnCurrentSetPoint);
	nRet = CreateProperty(g_PropertySkyraCurrentOn, g_Default_Float, MM::String, true, pAct);
	if (DEVICE_OK != nRet)
		return nRet;

	// Set Property Limits for Current
	currentMaximum_ = SerialCommand (ID_ + "gmlc?");
	double currentMaximum = std::stof(currentMaximum_);
	SetPropertyLimits(g_PropertySkyraCurrent,0,currentMaximum);
	SetPropertyLimits(g_PropertySkyraCurrentModulationMinimum,0,currentMaximum);
	SetPropertyLimits(g_PropertySkyraCurrentModulationMaximum,0,currentMaximum);

	CreateProperty("Current Units", g_PropertySkyraCurrentHelp, MM::String, true);
	CreateProperty("Current Modulation Minimum Help", g_PropertySkyraCurrentModulationHelpMinimum, MM::String, true);
	CreateProperty("Current Modulation Maximum Help", g_PropertySkyraCurrentModulationHelpMaximum, MM::String, true);
	CreateProperty("Current On Help", g_PropertySkyraCurrentHelpOn, MM::String, true);
		

	// check if Laser supports supports analog impedance
	AnalogImpedanceStatus();	
	if (bImpedance_ == true) {
		pAct = new CPropertyAction (this, &Skyra::OnAnalogImpedance);
		nRet = CreateProperty(g_PropertySkyraAnalogImpedance, impedanceStatus_.c_str(), MM::String, false, pAct);
		if (DEVICE_OK != nRet)
			return nRet;

		commands.clear();
		commands.push_back(g_PropertyEnabled);
		commands.push_back(g_PropertyDisabled);
		SetAllowedValues(g_PropertySkyraAnalogImpedance, commands);

		pAct = new CPropertyAction (this, &Skyra::OnAnalogImpedanceStatus);
		nRet = CreateProperty(g_PropertySkyraAnalogImpedanceStatus, impedanceStatus_.c_str(), MM::String, true, pAct);
		if (DEVICE_OK != nRet)
			return nRet;
	}


	// Constant Power (Default) or Constant Current or Modulation Mode
	pAct = new CPropertyAction (this, &Skyra::OnControlMode);
	nRet = CreateProperty(gPropertySkyraControlMode, controlMode_.c_str(), MM::String, false, pAct);
	if (nRet != DEVICE_OK)
		return nRet;

	commands.clear();
	commands.push_back(gPropertySkyraControlModePower);
	commands.push_back(gPropertySkyraControlModeConstant);
	if (bModulation_ == true)  commands.push_back(gPropertySkyraControlModeModulation);
	SetAllowedValues(gPropertySkyraControlMode, commands);  

	// Modulation
	if (bModulation_ == true) {

		pAct = new CPropertyAction (this, &Skyra::OnAnalogModulation);
		CreateProperty(g_PropertySkyraAnalogModulation, g_PropertyDisabled, MM::String, false, pAct);

		pAct = new CPropertyAction (this, &Skyra::OnDigitalModulation);
		CreateProperty(g_PropertySkyraDigitalModulation, g_PropertyDisabled, MM::String, false, pAct);

		commands.clear();
		commands.push_back(g_PropertyEnabled);
		commands.push_back(g_PropertyDisabled);

		SetAllowedValues(g_PropertySkyraDigitalModulation, commands);
		SetAllowedValues(g_PropertySkyraAnalogModulation, commands);

		if (nSkyra_) {
			pAct = new CPropertyAction (this, &Skyra::OnInternalModulation);
			CreateProperty(g_PropertySkyraInternalModulation, g_PropertyDisabled, MM::String, false, pAct);
			SetAllowedValues(g_PropertySkyraInternalModulation, commands);

			CreateProperty(g_PropertySkyraInternalModulationHelp, g_PropertySkyraInternalModulationHelpUnits, MM::String, true);

			pAct = new CPropertyAction (this, &Skyra::OnInternalModulationOn);
			CreateProperty(g_PropertySkyraInternalModulationOn, g_PropertyOff, MM::Integer, false, pAct);
			CreateProperty("Modulation Internal On Time Help", "On Time needs to be less or equal to Period Time", MM::String, true);

			pAct = new CPropertyAction (this, &Skyra::OnInternalModulationPeriod);
			CreateProperty(g_PropertySkyraInternalModulationPeriod, g_Default_Integer, MM::Integer, false, pAct);
			CreateProperty("Modulation Internal Period Time Help", "Period Time needs to be greater or equal to On Time", MM::String, true);

			pAct = new CPropertyAction (this, &Skyra::OnInternalModulationDelay);
			CreateProperty(g_PropertySkyraInternalModulationDelay, g_Default_Integer, MM::Integer, false, pAct);
		}
	} 

	// All GUI elements have now been created, lets update them from the laser.
	if (nSkyra_) UpdateWaveLength(ID_);

	nRet = UpdateStatus();
	if (nRet != DEVICE_OK)
		return nRet;

	bInitialized_ = true;

	//LogMessage("Skyra::Initialize: Success", true);

	return DEVICE_OK;
}
int Skyra::Shutdown()
{
	if (bInitialized_)
	{
		//AllLasersOnOff(false);
		bInitialized_ = false;

	}
	return DEVICE_OK;
}
bool Skyra::Busy()
{
	return bBusy_;
}
int Skyra::AllLasersOn(int onoff)
{

	if (onoff == 0)
	{
		SerialCommand("l0");
		laserStatus_ = "Off";
	}
	else if (onoff == 1)
	{
		SerialCommand("l1");
		laserStatus_ = "On";
	}

	return DEVICE_OK;
}
int Skyra::GetState(int &value )
{

	value = 0;
	return DEVICE_OK;

}
///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int Skyra::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		pProp->Set(port_.c_str());
	}
	else if (eAct == MM::AfterSet)
	{
		if (bInitialized_)
		{
			// revert
			pProp->Set(port_.c_str());
			return ERR_PORT_CHANGE_FORBIDDEN;
		}

		pProp->Get(port_);
	}

	return DEVICE_OK;
}
int Skyra::OnPowerMaximum(MM::PropertyBase* pProp , MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		powerMaximum_ = SerialCommand (ID_ + "gmlp?");
		pProp->Set(powerMaximum_.c_str());
	} 
	return DEVICE_OK;
}
int Skyra::OnControlMode(MM::PropertyBase* pProp, MM::ActionType  eAct)
{
	std::string answer;

	if (eAct == MM::BeforeGet)
	{
		pProp->Set(controlMode_.c_str());  
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(answer);
		if (controlMode_.compare(answer) != 0) {
			controlMode_ = answer;
			if (controlMode_.compare(gPropertySkyraControlModePower) == 0) {
				SerialCommand(ID_ + "cp");
				SetProperty(g_PropertySkyraModulationStatus, g_PropertyDisabled);
			}
			else if (controlMode_.compare(gPropertySkyraControlModeConstant) == 0) {
				SerialCommand(ID_ + "ci");
				SetProperty(g_PropertySkyraModulationStatus, g_PropertyDisabled);
			}
			if (controlMode_.compare(gPropertySkyraControlModeModulation) == 0) {
				SerialCommand(ID_ + "em");
				SetProperty(g_PropertySkyraModulationStatus, g_PropertyEnabled);
			}
		}
	}

	return DEVICE_OK;
}
int Skyra::OnAutoStart(MM::PropertyBase* pProp, MM::ActionType  eAct)
{
	if (eAct == MM::BeforeGet)
	{

	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(autostartStatus_);

		if (autostartStatus_.compare("Enabled") == 0) {
			SerialCommand("@cobas 1");
		}
		else if (autostartStatus_.compare("Disabled") == 0) {
			SerialCommand("@cobas 0");
		}
	}

	return DEVICE_OK;
}
int Skyra::OnAutoStartStatus(MM::PropertyBase* pProp, MM::ActionType  /* eAct */)
{
	AutostartStatus();

	pProp->Set(autostartStatus_.c_str());

	return DEVICE_OK;
}
int Skyra::OnActive(MM::PropertyBase* pProp, MM::ActionType  eAct)
{

	// This property is only shown if an actual Skyra

	std::string answer;

	if (eAct == MM::BeforeGet)
	{	
		answer = SerialCommand(ID_ + "gla? ");
		if (answer.compare("0") == 0) pProp->Set(g_PropertyInactive);
		if (answer.compare("1") == 0) pProp->Set(g_PropertyActive);
		//LogMessage("Skyra::OnActive 1 " + answer);
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(answer);
		if (answer.compare(g_PropertyActive) == 0) SerialCommand(ID_ + "sla 1");
		if (answer.compare(g_PropertyInactive) == 0) SerialCommand(ID_ + "sla 0");
		//LogMessage("Skyra::OnActive 2" + answer);

	}

	return DEVICE_OK;
}
int Skyra::OnPower(MM::PropertyBase* pProp, MM::ActionType  eAct)
{
	if (eAct == MM::BeforeGet)
	{
		pProp->Set(Power_.c_str());
	}
	else if (eAct == MM::AfterSet)
	{
		double power; 
		pProp->Get(power);
		if (power >= 0) {
			if (power <= std::stof(powerMaximum_)) {
				Power_ = std::to_string((long double)power);
				if (power > 0) {
					Power_ = std::to_string((long double)power);
					powerSetPoint_ = Power_; // remember high power value
				}
			}

			// Switch to constant power mode if not already set that way
			if (controlMode_.compare(gPropertySkyraControlModePower)  != 0) 
				SetProperty(gPropertySkyraControlMode,gPropertySkyraControlModePower);

			SetPower(Power_,ID_);
		}
	}
	return DEVICE_OK;
}
int Skyra::OnPowerSetPoint(MM::PropertyBase* pProp, MM::ActionType eAct )
{
	if (eAct == MM::BeforeGet)
	{
		std::string answer;
		double value = std::stof(SerialCommand(ID_ + "p?")) * 1000; 
		powerSetPoint_ = std::to_string((long double)value); 
		pProp->Set(powerSetPoint_.c_str());
	} 

	return DEVICE_OK;
}
int Skyra::OnPowerStatus(MM::PropertyBase* pProp, MM::ActionType  eAct)
{
	if (eAct == MM::BeforeGet)
	{
		if (nSkyra_) powerStatus_ = SerialCommand(ID_ + "glp?");
		else  {
			double value = std::stof(SerialCommand(ID_ + "pa?")) * 1000; 
			powerStatus_ = std::to_string((long double)value); 
		}
		pProp->Set(powerStatus_.c_str());
	}
	return DEVICE_OK;
}
int Skyra::OnHours(MM::PropertyBase* pProp, MM::ActionType /* eAct */)
{

	hours_= SerialCommand ("hrs?");

	pProp->Set(hours_.c_str());

	return DEVICE_OK;
}
int Skyra::OnKeyStatus(MM::PropertyBase* pProp, MM::ActionType /* eAct */)
{
	std::ostringstream command;
	std::string answer;

	answer = SerialCommand("@cobasks?");

	if (answer.at(0) == '0')
		keyStatus_ = "Off";
	else if (answer.at(0) == '1')
		keyStatus_ = "On";

	pProp->Set(keyStatus_.c_str());

	return DEVICE_OK;
}
int Skyra::OnSerialNumber(MM::PropertyBase* pProp, MM::ActionType /* eAct */)
{

	pProp->Set(serialNumber_.c_str());

	return DEVICE_OK;
}
int Skyra::OnModel(MM::PropertyBase* pProp, MM::ActionType /* eAct */)
{  

	pProp->Set(model_.c_str());

	return DEVICE_OK;
}
int Skyra::OnLaserType(MM::PropertyBase* pProp, MM::ActionType /* eAct */)
{
	pProp->Set(Type_.c_str());

	return DEVICE_OK;
}
int Skyra::OnVersion(MM::PropertyBase* pProp, MM::ActionType /* eAct */)
{

	pProp->Set(version_.c_str());

	return DEVICE_OK;
}
int Skyra::OnCurrentStatus(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	std::string answer;

	if (eAct == MM::BeforeGet)
	{
		answer = SerialCommand(ID_ + "i? ");
		pProp->Set(answer.c_str());
	}
	return DEVICE_OK;
}
int Skyra::OnCurrent(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		pProp->Set(Current_.c_str());

	} else 
	{
		if (eAct == MM::AfterSet)
		{
			double current;
			pProp->Get(current);
			
			if (current >= 0) {
				if (current <= std::stof(currentMaximum_)) {
					Current_ = std::to_string((long double) current);
					if (current > 0) {
						currentSetPoint_ = Current_;
					}
					// Switch to Current regulated mode and then set Current Level
					SetProperty(gPropertySkyraControlMode,gPropertySkyraControlModeConstant);
					SetProperty(g_PropertySkyraModulationStatus, g_PropertyDisabled);
					SerialCommand (ID_ + "slc " + Current_);
				}
			}
		}
	}
	return DEVICE_OK;
}
int Skyra::OnCurrentSetPoint(MM::PropertyBase*  pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		// not sure of this command works on a non Skyra
		if (nSkyra_) currentSetPoint_ = SerialCommand (ID_ + "glc?");
		pProp->Set(currentSetPoint_.c_str());
	} 
	return DEVICE_OK;
}
int Skyra::OnCurrentMaximum(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		currentMaximum_ = SerialCommand (ID_ + "gmlc?");
		pProp->Set(currentMaximum_.c_str());
	} 
	return DEVICE_OK;
}
int Skyra::OnCurrentModulationMaximum(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		currentModulationMaximum_ = SerialCommand (ID_ + "gmc?");
		pProp->Set(currentModulationMaximum_.c_str());
	} else 
	{
		if (eAct == MM::AfterSet)
		{
			pProp->Get(currentModulationMaximum_);
			SerialCommand (ID_ + "smc " + currentModulationMaximum_);
			SetPropertyLimits(g_PropertySkyraCurrentModulationMinimum,0,std::stof(currentModulationMaximum_));
		}
	}
	return DEVICE_OK;
}
int Skyra::OnCurrentModulationMinimum(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{

		MM::Property* pChildProperty = (MM::Property*)pProp;
		if (Type_.compare(0,3,"DPL") == 0) {
			pChildProperty->SetReadOnly(false);
			currentModulationMinimum_ = SerialCommand (ID_ + "glth?");
		}
		else {
			pChildProperty->SetReadOnly(true);
			currentModulationMinimum_ = "0";
		}
		pProp->Set(currentModulationMinimum_.c_str());
	} else 
		if (eAct == MM::AfterSet)
		{
			pProp->Get(currentModulationMinimum_);
			SerialCommand (ID_ + "slth " + currentModulationMinimum_);
			SetPropertyLimits(g_PropertySkyraCurrentModulationMaximum, std::stof(currentModulationMinimum_),std::stof(currentMaximum_));
		}
		return DEVICE_OK;
}
int Skyra::OnAnalogImpedance(MM::PropertyBase* pProp, MM::ActionType eAct)
{

	std::string answer;

	if (eAct == MM::BeforeGet)
	{
	}   
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(answer);

		if (answer.compare("Enabled") == 0)
			SerialCommand("salis 1");
		if (answer.compare("Disabled") == 0)
			SerialCommand("salis 0");
	}

	return DEVICE_OK;
}
int Skyra::OnAnalogImpedanceStatus(MM::PropertyBase* pProp, MM::ActionType /* eAct */)
{

	AnalogImpedanceStatus();
	pProp->Set(impedanceStatus_.c_str());

	return DEVICE_OK;
}
int Skyra::OnInterlock(MM::PropertyBase* pProp, MM::ActionType /* eAct */)
{

	std::string answer;

	answer = SerialCommand ("ilk?");

	if (answer.at(0) == '0')
		interlock_ = "Closed";
	else if (answer.at(0) == '1')
		interlock_ = "Open";

	pProp->Set(interlock_.c_str());

	return DEVICE_OK;
}
int Skyra::OnOperatingStatus(MM::PropertyBase* pProp, MM::ActionType /* eAct */)
{

	std::string answer;

	answer = SerialCommand ("gom?");

	if (answer.at(0) == '0')
		operatingStatus_ = "Off";
	else if (answer.at(0) == '1')
		operatingStatus_ = "Waiting for temperature";
	else if (answer.at(0) == '5')
		operatingStatus_ = "Fault";
	else if (answer.at(0) == '6')
		operatingStatus_ = "Aborted";
	else { 
		if (nSkyra_ > 0 ) {
			if (answer.at(0) == '2')
				operatingStatus_ = "Waiting for key";
			if (answer.at(0) == '3')
				operatingStatus_ = "Warm-up";
			else if (answer.at(0) == '4')
				operatingStatus_ = "Completed";
		} else {
			if (answer.at(0) == '2')
				operatingStatus_ = "Continuous";
			if (answer.at(0) == '3')
				operatingStatus_ = "On/Off Modulation";
			else if (answer.at(0) == '4') {
				operatingStatus_ = gPropertySkyraControlModeModulation;
			}
		}
	}



	pProp->Set(operatingStatus_.c_str());

	return DEVICE_OK;
}
int Skyra::OnFault(MM::PropertyBase* pProp, MM::ActionType /* eAct */)
{

	std::string answer;

	answer = SerialCommand ("f?");

	if (answer.at(0) == '0')
		fault_ = "No Fault";
	else if (answer.at(0) == '1')
		fault_ = "Temperature Fault";
	else if (answer.at(0) == '3')
		fault_ = "Open Interlock";
	else if (answer.at(0) == '4')
		fault_ = "Constant Power Fault";

	pProp->Set(fault_.c_str());

	return DEVICE_OK;
}
int Skyra::OnWaveLength(MM::PropertyBase* pProp, MM::ActionType  eAct)
{

	if (eAct == MM::BeforeGet)
	{
		pProp->Set(waveLength_.c_str()); 
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(waveLength_);

		for (int x = 0; x < waveLengths_.size(); x++) {
			if (waveLengths_[x].compare(waveLength_) == 0) {
				ID_ = IDs_[x]; 
				UpdateWaveLength(ID_);
				break;
			}
		}
		
	}
	return DEVICE_OK;
}
int Skyra::OnAnalogModulation(MM::PropertyBase* pProp, MM::ActionType  eAct)
{ 
	std::string answer;

	if (eAct == MM::BeforeGet)
	{
		MM::Property* pChildProperty = (MM::Property*)pProp;
		if (bInternalModulation_ == true) {
			pChildProperty->SetReadOnly(true);
			pProp->Set(g_PropertyDisabled);
		} else {
			pChildProperty->SetReadOnly(false);
			if (bAnalogModulation_) pProp->Set(g_PropertyEnabled);
			else pProp->Set(g_PropertyDisabled);
		}
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(answer);

		if (answer.compare(g_PropertyEnabled) == 0) SetModulation(MODULATION_ANALOG, true);
		else SetModulation(MODULATION_ANALOG, false);
	}

	return DEVICE_OK;
}
int Skyra::OnDigitalModulation(MM::PropertyBase* pProp, MM::ActionType  eAct)
{ 
	std::string answer;

	if (eAct == MM::BeforeGet)
	{
		MM::Property* pChildProperty = (MM::Property*)pProp;
		if (bInternalModulation_ == true) {
			pChildProperty->SetReadOnly(true);
			pProp->Set(g_PropertyDisabled);
		} else {
			pChildProperty->SetReadOnly(false);
			if (bDigitalModulation_) pProp->Set(g_PropertyEnabled);
			else pProp->Set(g_PropertyDisabled);
		}
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(answer);
		if (answer.compare(g_PropertyEnabled) == 0) SetModulation(MODULATION_DIGITAL, true);
		else SetModulation(MODULATION_DIGITAL, false);
	}

	return DEVICE_OK;
}
int Skyra::OnInternalModulationPeriod(MM::PropertyBase* pProp, MM::ActionType  eAct)
{ 
	

	if (eAct == MM::BeforeGet)
	{
		nInternalModulationPeriodTime_ = std::stoi(SerialCommand(ID_ + "gswmp?"));
		pProp->Set(nInternalModulationPeriodTime_);
	}
	else if (eAct == MM::AfterSet)
	{	
		std::string answer;

		pProp->Get(answer);
		nInternalModulationPeriodTime_ = std::stoi(answer);
		// Limited to INT_MAX and >= On Time
		if (nInternalModulationPeriodTime_ >= 0 && nInternalModulationPeriodTime_ <= INT_MAX) {
			if (nInternalModulationPeriodTime_ < nInternalModulationOnTime_) {
				nInternalModulationOnTime_ = nInternalModulationPeriodTime_;
				SerialCommand(ID_ + "sswmo " + answer);
			}
			SerialCommand(ID_ + "sswmp " + answer);
		}
	}

	return DEVICE_OK;
}
int Skyra::OnInternalModulationOn(MM::PropertyBase* pProp, MM::ActionType  eAct)
{ 

	if (eAct == MM::BeforeGet)
	{
		nInternalModulationOnTime_ = std::stoi(SerialCommand(ID_ + "gswmo?"));
		pProp->Set(nInternalModulationOnTime_);
	}
	else if (eAct == MM::AfterSet)
	{
		std::string answer;
		// Limited to INT_MAX or OnInternalModulationPeriod, whichever is less
		pProp->Get(answer);

		nInternalModulationOnTime_ = std::stoi(answer);
		if (nInternalModulationOnTime_ >= 0 && nInternalModulationOnTime_ <= INT_MAX) {
			if (nInternalModulationOnTime_ > nInternalModulationPeriodTime_) {
				nInternalModulationPeriodTime_ = nInternalModulationOnTime_;
				SerialCommand(ID_ + "sswmp " + answer);
			}
			SerialCommand(ID_ + "sswmo " + answer);
		}
	}

	return DEVICE_OK;
}
int Skyra::OnInternalModulationDelay(MM::PropertyBase* pProp, MM::ActionType  eAct)
{ 
	std::string answer;

	if (eAct == MM::BeforeGet)
	{
		pProp->Set(SerialCommand(ID_ + "gswmod?").c_str());
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(answer);
		int value = std::stoi(answer);
		if (value >= 0) if (value <= INT_MAX) SerialCommand(ID_ + "sswmod " + answer);
	}

	return DEVICE_OK;
}

int Skyra::OnInternalModulation(MM::PropertyBase* pProp, MM::ActionType  eAct)
{ 
	std::string answer;

	if (eAct == MM::BeforeGet)
	{
		MM::Property* pChildProperty = (MM::Property*)pProp;
		if (bAnalogModulation_ == true || bDigitalModulation_ == true) {
			pChildProperty->SetReadOnly(true);
			pProp->Set(g_PropertyDisabled);
		}
		else {
			pChildProperty->SetReadOnly(false);
			if (bInternalModulation_) pProp->Set(g_PropertyEnabled);
			else pProp->Set(g_PropertyDisabled);
		}
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(answer);
		if (answer.compare(g_PropertyEnabled) == 0) SetModulation(MODULATION_INTERNAL, true);
		else SetModulation(MODULATION_INTERNAL, false);
	}

	return DEVICE_OK;
}
int Skyra::OnLaserHelp1(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		if (laserStatus_.compare("On") == 0) pProp->Set(g_PropertySkyraAutostartHelp3); 
		else if (laserStatus_.compare("Off") == 0) pProp->Set(g_PropertySkyraAutostartHelp1); 
	}

	return DEVICE_OK;
}
int Skyra::OnLaserHelp2(MM::PropertyBase* pProp , MM::ActionType eAct)
{	
	if (eAct == MM::BeforeGet)
	{
		if (laserStatus_.compare("On") == 0) pProp->Set(g_PropertySkyraAutostartHelp4); 
		else if (laserStatus_.compare("Off") == 0) pProp->Set(g_PropertySkyraAutostartHelp2); 
	}

	return DEVICE_OK;
}
int Skyra::OnAllLasers(MM::PropertyBase* pProp, MM::ActionType eAct)
{

	std::string answer;

	if (eAct == MM::BeforeGet) {
		answer = SerialCommand(ID_ + "l?");
		if (answer.compare("1") == 0) pProp->Set(g_PropertyOn);
		else pProp->Set(g_PropertyOff);
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(answer);
		if (answer.compare("On") == 0)
		{
			AllLasersOn(true);
		}
		else
		{
			AllLasersOn(false);
		}

	}
	return DEVICE_OK;
} 
int Skyra::OnLaser(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	// This property is only shown if an actual Skyra
	std::string answer;
	if (eAct == MM::BeforeGet)
	{
		answer = SerialCommand(ID_ + "l?");  
		if (answer.compare("1") == 0)
		{
			pProp->Set(g_PropertyOn);
			bOn_ = true;
		}
		else
		{
			pProp->Set(g_PropertyOff);
			bOn_ = false;
		}
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(answer);
		if (answer.compare(g_PropertyOn) == 0)
		{
			SerialCommand(ID_ + "l1");
			bOn_ = true;
		}
		else
		{
			SerialCommand(ID_ + "l0"); 
			bOn_ = false;
		}
	}
	return DEVICE_OK;
} 
int Skyra::OnLaserStatus(MM::PropertyBase* pProp, MM::ActionType eAct)
{

	std::string answer;

	if (eAct == MM::BeforeGet)
	{
		answer = SerialCommand(ID_ + "l?"); 

		if (answer.compare("1") == 0)
		{
			pProp->Set(g_PropertyOn);
		}
		else
		{
			if (answer.compare("0") == 0) pProp->Set(g_PropertyOff);
		}
	}

	return DEVICE_OK;
} 
///////////////////////////////////////////////////////////////////////////////
// Base Calls  
///////////////////////////////////////////////////////////////////////////////
std::string Skyra::SetPower(std::string requestedPowerSetpoint, std::string laserid = "")
{
	std::string answer;

	double dPower = std::stof(requestedPowerSetpoint,NULL)/1000;

	answer = SerialCommand (ID_ + "p " + std::to_string((long double)dPower));

	//LogMessage("SetPower: " + ID_ + std::to_string((long double)dPower));

	return answer;
}
std::string Skyra::AutostartStatus() {

	std::string answer;

	answer = SerialCommand("@cobas?");

	if (answer.at(0) == '0')
		autostartStatus_ = g_PropertyDisabled;
	else if (answer.at(0) == '1')
		autostartStatus_ = g_PropertyEnabled;

	return answer;
}
std::string Skyra::SetModulation(int modulation, bool value) 
{
	std::string answer;

	// Analog and Digital can be active at the same time, but Internal must only be activated by itself
	if (modulation == MODULATION_ANALOG) {
		bAnalogModulation_ = value;
		if (bAnalogModulation_) {
			SerialCommand (ID_ + "eswm 0");
			answer = SerialCommand (ID_ + "sames 1");
			
		}
		else answer = SerialCommand (ID_ + "sames 0");
	}

	if (modulation == MODULATION_DIGITAL) {
		bDigitalModulation_ = value;
		if (bDigitalModulation_) {
			SerialCommand (ID_ + "eswm 0");
			answer = SerialCommand (ID_ + "sdmes 1");
		}
		else  answer = SerialCommand (ID_ + "sdmes 0");
	}

	if (modulation == MODULATION_INTERNAL) {
		bInternalModulation_ = value;
		if (bInternalModulation_) {
			SerialCommand (ID_ + "sames 0");
			SerialCommand (ID_ + "sdmes 0");
			answer = SerialCommand (ID_ + "eswm 1");
		}
		else answer = SerialCommand (ID_ + "eswm 0");

	}

	return answer;
}
std::string Skyra::UpdateWaveLength(std::string id) 
{
	std::string value;
	double dValue;
	
	RASP_ = GetRASP(id);
	RA_ = GetReadAll(id);

	//LogMessage("Skyra::UpdateWaveLength " + id, true);

	// update Current Values
	// RA
	SetProperty(g_PropertySkyraCurrentStatus,currentStatus_.c_str());

	// RASP
	SetProperty(g_PropertySkyraCurrentOn,currentSetPoint_.c_str());

	currentMaximum_ = SerialCommand (ID_ + "gmlc?");
	dValue = std::stof(currentMaximum_);
	// Reset Limits when switching wavelengths
	SetPropertyLimits(g_PropertySkyraCurrent,0,dValue);
	SetPropertyLimits(g_PropertySkyraCurrentModulationMinimum,0,dValue);
	SetPropertyLimits(g_PropertySkyraCurrentModulationMaximum,0,dValue);

	// Current Maximum
	SetProperty(g_PropertySkyraCurrentMaximum,currentModulationMaximum_.c_str());
	
	// Current Modulation Maximum
	currentModulationMaximum_ = SerialCommand (ID_ + "gmc?");
	SetProperty(g_PropertySkyraCurrentModulationMaximum,currentModulationMaximum_.c_str());

	// Current Modulation Minimum
	currentModulationMinimum_ = SerialCommand (ID_ + "glth?");
	SetProperty(g_PropertySkyraCurrentModulationMinimum,currentModulationMinimum_.c_str());
			 
	// update Power Output -- RA
	SetProperty(g_PropertySkyraPowerStatus,powerStatus_.c_str());

	// update Power Setting -- RASP
	SetProperty(g_PropertySkyraPowerOn,powerSetPoint_.c_str());

	// Power Maximum
	powerMaximum_ = SerialCommand (ID_ + "gmlp?");
	// Set Property Limits for Power
	dValue = std::stof(powerMaximum_);
	SetPropertyLimits(g_PropertySkyraPower,0,dValue);
	
	SetProperty(g_PropertySkyraCurrentMaximum,powerMaximum_.c_str());

	// Laser Type
	// get default type if not Skyra
	Type_ = SerialCommand(id + "glm?");
	if (Type_.length() > 3) Type_.resize(3);

	SetProperty(g_PropertySkyraLaserType,Type_.c_str());

	// update active status
	value = SerialCommand(id + "gla? ");
	if (value.compare("0") == 0) SetProperty(g_PropertySkyraActiveStatus, g_PropertyInactive);
	else if (value.compare("1") == 0) SetProperty(g_PropertySkyraActiveStatus, g_PropertyActive);
	
	// Control Mode
	SetProperty(gPropertySkyraControlMode, controlMode_.c_str());

	// update modulation status
	if (bAnalogModulation_) SetProperty(g_PropertySkyraAnalogModulation, g_PropertyEnabled);
	else SetProperty(g_PropertySkyraAnalogModulation, g_PropertyDisabled);

	if (bDigitalModulation_) SetProperty(g_PropertySkyraDigitalModulation, g_PropertyEnabled);
	else  SetProperty(g_PropertySkyraDigitalModulation, g_PropertyDisabled);

	if (bInternalModulation_) SetProperty(g_PropertySkyraInternalModulation, g_PropertyEnabled);
	else SetProperty(g_PropertySkyraInternalModulation, g_PropertyDisabled);

	return value;

}
std::string Skyra::GetRASP(std::string laserID) {

	char * pch = NULL;
	
	if (laserID.empty() == true) return ("Skyra::GetReadAll:Empty ID");
	
	RASP_ = SerialCommand (laserID + "rasp?");

	if (!RASP_.empty()) {	
		strncpy(tempstr_, RASP_.c_str(),SERIAL_BUFFER);
			
		//
		// Position 1: Unknown
		// 
		pch = strtok (tempstr_, " ");
		if (pch != NULL) //LogMessage("Skyra::GetReadAll: Unknown " + *pch, true);
		//}

		//
		// Position 2: Current
		//
		pch = strtok (NULL, " ");
		if (pch != NULL) {
			currentSetPoint_ = pch;
			//LogMessage("Skyra::GetReadAll: Current Set " + *pch, true);
		}

		//
		// Position 3: Powwer
		//
		pch = strtok (NULL, " ");
		if (pch != NULL) {
			powerSetPoint_ = pch;
			//LogMessage("Skyra::GetReadAll: Power Set " + *pch, true);
		}
	}

	return RASP_;

}
std::string Skyra::GetReadAll(std::string laserID) {

	// #ra?
	// 6 or 7 numbers with the string format: 
	//
	// "mA mW on(1 = on, 0 = off) mode(0 = ci, 1 = cp,2 = em) unknown Modulation"
	//
	// "Photodiode(V) Current(mA) Power(mW) on(1 = on, 0 = off) Mode(0 = ci, 1 = cp,2 = em) unknown Modulation"
	//   
	// Mode: is set to 0 if autostart is disabled, and if autostart is re-enabled, it will be set to 1 (cp)
	// Modulation: Digital = 1, Analog = 2, both = 3

	// #rasp?
	
	
	char * pch = NULL;
	std::string answer;
	
	if (laserID.empty() == true) return ("Skyra::GetReadAll:Empty ID");

	RA_ = SerialCommand (laserID + "ra?");
	if (!RA_.empty()) {	
		
		strncpy(tempstr_, RA_.c_str(),SERIAL_BUFFER);
			
		pch = strtok (tempstr_, " ");

		//
		// DLP Position 1: Photodiode Voltage
		//
		// 
		if (pch != NULL) {
			Type_ = SerialCommand (ID_ + "glm?");
			if (Type_.length() > 3) Type_.resize(3);
			if (Type_.compare("DPL") == 0) { 
				photoDiode_ = pch;
				pch = strtok (NULL, " ");
				//LogMessage("Skyra::GetReadAll: PV " + *pch, true);
			}
		}
		
		//
		//
		// Position 1: Current in mA
		//
		if (pch != NULL) {
			currentStatus_ = *pch;
			//LogMessage("Skyra::GetReadAll: Current " + *pch, true);
		}
		
		//
		// Position 2: Power in mW
		//
		pch = strtok (NULL, " ");
		if (pch != NULL) {
			powerStatus_ = pch;
			//LogMessage("Skyra::GetReadAll: Power " + *pch, true); 
		}

		//
		// Position 3: Laser On?
		//
		pch = strtok (NULL, " ");
		if (pch != NULL) {
			if (strcmp(pch,"1") == 0) bOn_ = true;
			else bOn_ = false;
			//LogMessage("Skyra::GetReadAll: On " + *pch, true);
		}
		
		//
		// Position 4: Control Mode?
		//
		pch = strtok (NULL, " ");
		if (pch != NULL) {
			if (pch[0] == '0') controlMode_ = gPropertySkyraControlModeConstant;
			if (pch[0] == '1')  controlMode_= gPropertySkyraControlModePower;
			if (pch[0] == '2')  controlMode_ = gPropertySkyraControlModeModulation;
			//LogMessage("Skyra::GetReadAll: Control Mode " + *pch, true);
		} 

		//
		// Position 5:   Control Mode?
		//
		pch = strtok (NULL, " ");
		if (pch != NULL) {
			//LogMessage("Skyra::GetReadAll: Unknown " + *pch, true);
		}
		
		//
		// Position 6: Modulation Mode
		//
		pch = strtok (NULL, " ");
		if (pch != NULL) {
			bDigitalModulation_ = bAnalogModulation_ = false;
			if (strcmp(pch,"1") == 0) bDigitalModulation_ = true;
			if (strcmp(pch,"2") == 0) bAnalogModulation_ = true;
			if (strcmp(pch,"3") == 0) bDigitalModulation_ = bAnalogModulation_ = true;
			//LogMessage("Skyra::GetReadAll: Modulation Mode " + *pch, true);
		} 
	}

	// get Internal, even though it isn't convered by ra?
	answer = SerialCommand (ID_ + "gswm?");
	if (answer.at(0) == '1')  bInternalModulation_ = true;
	else bInternalModulation_ = false;

	return RA_;
}
std::string Skyra::AnalogImpedanceStatus() {

	std::string answer;

	answer = SerialCommand ("galis?");   
	if (answer.at(0) == '0') impedanceStatus_ = g_PropertyDisabled;
	else if (answer.at(0) == '1') impedanceStatus_ = g_PropertyEnabled;

	if (answer.compare(g_Msg_UNSUPPORTED_COMMAND) == 0 ) bImpedance_ = false;
	else bImpedance_ = true;

	return answer;
}
//********************
// Shutter API
//********************

int Skyra::SetOpen(bool open)
{
	//We should have different modes that turn on/off lasers
	std::string answer;

	if (bModulation_) {
		// if we are using a shutter and we have a laser that can be modulated, use Constant Current
		if (controlMode_.compare(gPropertySkyraControlModeConstant) !=0) { 
			SerialCommand(ID_ + "ci");
			controlMode_ = gPropertySkyraControlModeConstant;
		}

		if (open) {
			// return to last Current Setting
			//answer = SerialCommand(ID_ + "slc " + currentSetPoint_);
			answer = SerialCommand(ID_ + "slc " + currentModulationMaximum_);
		}
		else answer = SerialCommand(ID_ + "slc " + currentModulationMinimum_);

		return DEVICE_OK;
	}
	else return AllLasersOn((int) open);
}
int Skyra::GetOpen(bool& open)
{
	int state;
	int ret = GetState(state);
	if (ret != DEVICE_OK) return ret;

	if (state==1)
		open = true;
	else if (state==0)
		open = false;
	else
		return  DEVICE_UNKNOWN_POSITION;

	return DEVICE_OK;
}
// ON for deltaT milliseconds
// other implementations of Shutter don't implement this
// is this perhaps because this blocking call is not appropriate

int Skyra::Fire(double deltaT)
{
	SetOpen(true);
	CDeviceUtils::SleepMs((long)(deltaT+.5));
	SetOpen(false);
	return DEVICE_OK;
}
std::string Skyra::SerialCommand (std::string serialCommand) {

	std::ostringstream command;
	std::string answer;

	command << serialCommand;

	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), g_SendTerm);
	if (ret != DEVICE_OK) return "Sending Serial Command Failed";
	ret = GetSerialAnswer(port_.c_str(), g_RecvTerm, answer);
	if (ret != DEVICE_OK) return  "Receiving Serial Command Failed";

	if (answer.compare ("Syntax error: illegal command") == 0) answer = g_Msg_UNSUPPORTED_COMMAND;

	return answer;
}
std::string Skyra::GetPort()
{
	std::string port;
	//MMThreadGuard guard(mutex_);
	port = port_;

	return port;
}