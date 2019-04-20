///////////////////////////////////////////////////////////////////////////////
// FILE:          Oxxius.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Controls Oxxius lasers through a serial port
// COPYRIGHT:     Oxxius S.A., 2013-2019
// LICENSE:       LGPL
// AUTHOR:        Julien Beaurepaire, Tristan Martinez
//

#include "Oxxius.h"

// Alias code used to simplify the usual function call and susequent error code checking.
#define RETURN_ON_MM_ERROR( result ) do { \
   int return_value = (result); \
   if (return_value != DEVICE_OK) { \
      return return_value; \
   } \
} while (0)



const char* g_DeviceLaserBoxxName = "Oxxius LaserBoxx LBX or LMX or LCX";



///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
	RegisterDevice(g_DeviceLaserBoxxName, MM::ShutterDevice, "LaserBoxx laser source");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
	if (deviceName == 0)
		return 0;
	if(strcmp(deviceName, g_DeviceLaserBoxxName) == 0)
		return new LaserBoxx;

    return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
// Oxxius LBX DPSS
///////////////////////////////////////////////////////////////////////////////
LaserBoxx::LaserBoxx() :
	port_("Undefined"),
	initialized_(false),
	busy_(false)
{
	InitializeDefaultErrorMessages();
	SetErrorText(ERR_PORT_CHANGE_FORBIDDEN, "Port cannot be changed once after device has been initialized.");

	// Name
	CreateProperty(MM::g_Keyword_Name, g_DeviceLaserBoxxName, MM::String, true);

	// Description
	CreateProperty(MM::g_Keyword_Description, "Oxxius LaserBoxx Controller", MM::String, true);

	// Port
	CPropertyAction* pAct = new CPropertyAction (this, &LaserBoxx::OnPort);
	CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

	// Initialize private data
	powerSP_ = 0.0;
	currentSP_ = 0.0;
    emissionStatus_  = "";
    alarm_ = "";
	modeControl_ = "";
    serialNumber_ = "";
    softVersion_ = "";
	model_ = "";
}

LaserBoxx::~LaserBoxx()
{
     Shutdown();
}


void LaserBoxx::GetName(char* Name) const
{
     CDeviceUtils::CopyLimitedString(Name, g_DeviceLaserBoxxName);
}


int LaserBoxx::Initialize()
{
	if (!initialized_) {
		// Determines the model, between LBX, LCX and LMX
		std::string command,answer;
		command = "inf?";

		RETURN_ON_MM_ERROR( SendAndReceive(command,answer) );

		std::string::size_type Pos = answer.find_first_of("-", 0);
		model_ = answer.substr( 0,Pos);
		Pos = answer.find_first_of("-", Pos+1);
		nominalPower_ = atof( answer.substr( Pos+1,std::string::npos).c_str() );

//		GetCoreCallback()->LogMessage(this, model_.c_str(), false);


		CPropertyAction* pAct = new CPropertyAction (this, &LaserBoxx::OnSerialNumber);
		RETURN_ON_MM_ERROR( CreateProperty("Serial number", "0", MM::String, true, pAct) );
	 
		pAct = new CPropertyAction (this, &LaserBoxx::OnSWVersion);
		RETURN_ON_MM_ERROR( CreateProperty("Software version", "0", MM::String, true, pAct) );
     
		pAct = new CPropertyAction (this, &LaserBoxx::OnModel);
		RETURN_ON_MM_ERROR( CreateProperty("Model", "L.X-000", MM::String, true, pAct) );

		pAct = new CPropertyAction (this, &LaserBoxx::OnLaserStatus);
		RETURN_ON_MM_ERROR( CreateProperty("Laser status", "Off", MM::String, true, pAct) );

		pAct = new CPropertyAction (this, &LaserBoxx::OnEmissionOnOff);
		RETURN_ON_MM_ERROR( CreateProperty("Emission", "Off", MM::String, false, pAct) );
		std::vector<std::string> allowedValuesOffOn;
		allowedValuesOffOn.push_back("Off");
		allowedValuesOffOn.push_back("On");
		SetAllowedValues("Emission", allowedValuesOffOn);
  
		pAct = new CPropertyAction (this, &LaserBoxx::OnAlarm);
		RETURN_ON_MM_ERROR( CreateProperty("Alarm", "No Alarm", MM::String, true, pAct) );
	 
		pAct = new CPropertyAction (this, &LaserBoxx::OnInterlocked);
		RETURN_ON_MM_ERROR( CreateProperty("OnInterlock", "Open", MM::String, true, pAct) );
	 
		pAct = new CPropertyAction (this, &LaserBoxx::OnControlMode);
		RETURN_ON_MM_ERROR( CreateProperty("Control mode", "APC", MM::String, false, pAct) );
		std::vector<std::string> allowedValuesCont;
		allowedValuesCont.push_back("APC");
		allowedValuesCont.push_back("ACC");
		SetAllowedValues("Control mode", allowedValuesCont);

		pAct = new CPropertyAction (this, &LaserBoxx::OnPower);
		RETURN_ON_MM_ERROR( CreateProperty("Monitored power (mW)", "0.00", MM::Float, true, pAct) );

		CPropertyActionEx* pActExP = new CPropertyActionEx (this, &LaserBoxx::OnPowerSP, 0);
		RETURN_ON_MM_ERROR( CreateProperty("Power set point (%)", "0.00", MM::Float, false, pActExP) );
		SetPropertyLimits("Power set point (%)", 0, 110);

		pAct = new CPropertyAction (this, &LaserBoxx::OnCurrent);
		RETURN_ON_MM_ERROR( CreateProperty("Monitored current (mA)", "0.00", MM::Float, true, pAct) );

		CPropertyActionEx* pActExC = new CPropertyActionEx (this, &LaserBoxx::OnCurrentSP, 0);
		RETURN_ON_MM_ERROR( CreateProperty("Current set point (%)", "0.00", MM::Float, false, pActExC) );
		SetPropertyLimits("Current set point (%)", 0, 125);

		pAct = new CPropertyAction (this, &LaserBoxx::OnSleep);
		RETURN_ON_MM_ERROR( CreateProperty("Sleep mode", "Off", MM::String, false, pAct) );
		std::vector<std::string> allowedValuesSle;
		allowedValuesSle.push_back("Sleep");
		allowedValuesSle.push_back("Ready");
		SetAllowedValues("Sleep mode", allowedValuesSle);
 	
		pAct = new CPropertyAction (this, &LaserBoxx::OnAnalogMod);
		RETURN_ON_MM_ERROR( CreateProperty("Analog modulation", "Off", MM::String, false, pAct) );
		pAct = new CPropertyAction (this, &LaserBoxx::OnDigitalMod);
		RETURN_ON_MM_ERROR( CreateProperty("Digital modulation", "Off", MM::String, false, pAct) );
		SetAllowedValues("Analog modulation", allowedValuesOffOn);
		SetAllowedValues("Digital modulation", allowedValuesOffOn);
  
		pAct = new CPropertyAction (this, &LaserBoxx::OnHours);
		RETURN_ON_MM_ERROR( CreateProperty("Emission time (hours)", "0.00", MM::Float, true, pAct) );

		pAct = new CPropertyAction (this, &LaserBoxx::OnBaseTemp);
		RETURN_ON_MM_ERROR( CreateProperty("Base temperature", "0.00", MM::Float, true, pAct) );

/*		pAct = new CPropertyAction (this, &LaserBoxx::OnControllerTemp);
		RETURN_ON_MM_ERROR( CreateProperty("Controller temperature", "0.00", MM::Float, true, pAct) );
*/	 
		RETURN_ON_MM_ERROR( UpdateStatus() );
     
		initialized_ = true;
	}
	return DEVICE_OK;
}


int LaserBoxx::Shutdown()
{
	if (initialized_) {
		EmissionOnOff(0);		// Turns off the emssion on "Shutdown"
		initialized_ = false;
	}
	return DEVICE_OK;
}


bool LaserBoxx::Busy()
{
	return busy_;
}


int LaserBoxx::EmissionOnOff(int onoff)
{
	std::string command, answer;

	if (onoff == 0) {
		command = "dl 0";
		emissionStatus_ = "Off";
	}
	else if (onoff == 1) {
		command = "dl 1";
		emissionStatus_ = "On";
	}
	RETURN_ON_MM_ERROR( SendAndReceive(command,answer) );

	return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////


int LaserBoxx::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		pProp->Set(port_.c_str());
	} else if (eAct == MM::AfterSet) {
		if (initialized_) {
			// revert
			pProp->Set(port_.c_str());
			return ERR_PORT_CHANGE_FORBIDDEN;
		}
		pProp->Get(port_);
	}
	return DEVICE_OK;
}


int LaserBoxx::OnSerialNumber(MM::PropertyBase* pProp, MM::ActionType)
{
	std::string command, answer;
	command = "hid?";

	RETURN_ON_MM_ERROR( SendAndReceive(command,answer) );
	pProp->Set(answer.c_str());
     
	return DEVICE_OK;
}


int LaserBoxx::OnSWVersion(MM::PropertyBase* pProp, MM::ActionType)
{
	std::string command;
	command = "?sv";

	RETURN_ON_MM_ERROR( SendAndReceive(command,softVersion_) );
	pProp->Set(softVersion_.c_str());
     
	return DEVICE_OK;
}


int LaserBoxx::OnModel(MM::PropertyBase* pProp, MM::ActionType)
{
 	std::string command, answer;
	command = "inf?";

	RETURN_ON_MM_ERROR( SendAndReceive(command,answer) );

	std::string::size_type Pos = answer.find_first_of("-", 0) + 1;
	Pos = answer.find_first_of("-", Pos);

	pProp->Set(answer.substr(0,Pos).c_str());

	return DEVICE_OK;
}


int LaserBoxx::OnLaserStatus(MM::PropertyBase* pProp, MM::ActionType )
{
	int currentStatus = 0;
	std::string laserStatus = "";
	
	RETURN_ON_MM_ERROR( GetStatus(currentStatus) );

	switch(currentStatus) {
		case 1 : laserStatus = "Warm-up phase";
			break;
		case 2 : laserStatus = "Stand-by for emission";
			break;
		case 3 : laserStatus = "Emission is on";
			break;
		case 4 : laserStatus = "Alarm raised";
			break;
		case 5 : laserStatus = "Internal error raised";
			break;
		case 6 : laserStatus = "Sleep mode";
			break;
		case 7 : laserStatus = "Searching for SLM point";
			break;
		default	 : return DEVICE_UNKNOWN_POSITION;
	}
	pProp->Set(laserStatus.c_str());

	return DEVICE_OK;
}


int LaserBoxx::OnEmissionOnOff(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	std::string command, answer; 
	emissionStatus_ = "Undefined";

	if (eAct == MM::BeforeGet) {
		int currentStatus = 0;
		RETURN_ON_MM_ERROR( GetStatus(currentStatus) );

		if ( (currentStatus == 1) || (currentStatus == 3) || (currentStatus == 7) )
			emissionStatus_ = "On";
		else
			emissionStatus_ = "Off";
          
		pProp->Set(emissionStatus_.c_str());

	} else if (eAct == MM::AfterSet) { 
		pProp->Get(answer);
		
		if (answer == "Off") 
			EmissionOnOff(false);
		else 
			EmissionOnOff(true);
	
		CDeviceUtils::SleepMs(500);
	}
	return DEVICE_OK;
}



int LaserBoxx::OnAlarm(MM::PropertyBase* pProp, MM::ActionType )
{
	std::string command, answer;
	int currentAlarm = 0;
     
	command = "?f";
    RETURN_ON_MM_ERROR( SendAndReceive(command,answer) );

	currentAlarm = atoi(answer.c_str());

	switch(currentAlarm) {
		case 0 : alarm_ = "No alarm";
			break;
		case 1 : alarm_ = "Out-of-bounds current";
			break;
		case 2 : alarm_ = "Out-of-bounds power";
			break;
		case 3 : alarm_ = "Out-of-bounds supply voltage";
			break;
		case 4 : alarm_ = "Out-of-bounds inner temperature";
			break;
		case 5 : alarm_ = "Out-of-bounds laser head temperature";
			break;
		case 7 : alarm_ = "Interlock circuit open";
			break;
		case 8 : alarm_ = "Manual reset";
			break;
		default	 : return DEVICE_UNKNOWN_POSITION;
	}


	pProp->Set(alarm_.c_str());

	return DEVICE_OK;
}



int LaserBoxx::OnInterlocked(MM::PropertyBase* pProp, MM::ActionType )
{
	std::string command, answer, interlockStatus;
     
	command = "?int";
    RETURN_ON_MM_ERROR( SendAndReceive(command,answer) );

	if( answer.compare("1") == 0)
				interlockStatus = "Closed";
			else
				interlockStatus = "Open";

	pProp->Set(interlockStatus.c_str());

	return DEVICE_OK;
}


int LaserBoxx::OnControlMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	std::string command, query, answer, newMode;
	std::ostringstream newCommand;

	if (eAct == MM::BeforeGet) {
		if( model_.compare("LBX") == 0) {
			command = "?acc";
			RETURN_ON_MM_ERROR( SendAndReceive(command,answer) );

			if( answer.compare("1") == 0)
				modeControl_ = "ACC";
			else
				modeControl_ = "APC";
		} else {
			modeControl_ = "APC";
		}
		pProp->Set(modeControl_.c_str());
	}
	else if (eAct == MM::AfterSet) {
		
		pProp->Get(newMode);
		if( (model_.compare("LBX") == 0) && (modeControl_.compare(newMode) != 0) ) {

			EmissionOnOff(0);		// Turns the emission off before changing the control mode

			if( newMode.compare("ACC") == 0)
				query = "1";
			else
				query = "0";
			newCommand << "acc " << query;
			command = newCommand.str();

			RETURN_ON_MM_ERROR( SendAndReceive(command,answer) );
		}
	}
	return DEVICE_OK;
}



int LaserBoxx::OnPower(MM::PropertyBase* pProp, MM::ActionType )
{
	std::string command, answer;
               
	command = "?p";
	RETURN_ON_MM_ERROR( SendAndReceive(command,answer) );
	pProp->Set(atof(answer.c_str()));
     
	return DEVICE_OK;
}


int LaserBoxx::OnPowerSP(MM::PropertyBase* pProp, MM::ActionType eAct, long )
{
	std::string command, answer;
	std::ostringstream newCommand;

	if (eAct == MM::BeforeGet) {
		if( model_.compare("LBX") == 0) {
			command = "?sp";
			RETURN_ON_MM_ERROR( SendAndReceive(command,answer) );
			pProp->Set( 100 * atof(answer.c_str()) / nominalPower_ );
		}
		else {  // assuming "LCX"
			command = "ip";
			RETURN_ON_MM_ERROR( SendAndReceive(command,answer) );
			answer = answer.substr( 0, std::string::npos-1);
			pProp->Set( atof(answer.c_str()) );
		}
	}
	else if (eAct == MM::AfterSet) {
          
		pProp->Get(powerSP_);
		if( model_.compare("LBX") == 0)
			newCommand << "p " << ( nominalPower_ * powerSP_ / 100 );
		else
			newCommand << "ip " << powerSP_ ;

		command = newCommand.str();

		RETURN_ON_MM_ERROR( SendAndReceive(command,answer) );
	}

	return DEVICE_OK;
}



int LaserBoxx::OnCurrent(MM::PropertyBase* pProp, MM::ActionType )
{
	if( model_.compare("LMX") != 0) {
		std::string command, answer;
               
		command = "?c";
		RETURN_ON_MM_ERROR( SendAndReceive(command,answer) );

		pProp->Set(atof(answer.c_str()));
	}
	return DEVICE_OK;
}



int LaserBoxx::OnCurrentSP(MM::PropertyBase* pProp, MM::ActionType eAct, long )
{
	std::string command, answer;
	std::ostringstream newCommand;

	if (eAct == MM::BeforeGet) {
		if( model_.compare("LBX") == 0) {
			command = "?sc";
			RETURN_ON_MM_ERROR( SendAndReceive(command,answer) );

			pProp->Set(atof(answer.c_str()));
		} else {
			pProp->Set(0.0);
		}
	}
	else if (eAct == MM::AfterSet) {
		if( model_.compare("LBX") == 0) {
			pProp->Get(currentSP_);
			newCommand << "c " << currentSP_;
			command = newCommand.str();

			RETURN_ON_MM_ERROR( SendAndReceive(command,answer) );
		}
	}
	return DEVICE_OK;
}


int LaserBoxx::OnSleep(MM::PropertyBase* pProp, MM::ActionType eAct )
{
	std::string command, query, answer, Smod;
	std::ostringstream newCommand;

	if (eAct == MM::BeforeGet) {
		if( model_.compare("LMX") != 0) {
			command = "?t";
			RETURN_ON_MM_ERROR( SendAndReceive(command,answer) );

			if( answer.compare("0") == 0)
				pProp->Set("Sleep");
			else
				pProp->Set("Ready");
		} else {
			pProp->Set("Ready");
		}
	}
	else if (eAct == MM::AfterSet) {
		if( model_.compare("LMX") != 0) {

			pProp->Get(Smod);
			if( Smod.compare("Sleep") == 0)
				query = "0";
			else
				query = "1";
			newCommand << "t " << query;
			command = newCommand.str();

			RETURN_ON_MM_ERROR( SendAndReceive(command,answer) );
		} else {
			// do nothing for LMX
		}
	}
	return DEVICE_OK;
}


int LaserBoxx::OnAnalogMod(MM::PropertyBase* pProp, MM::ActionType eAct )
{
	std::string command, query, answer, Amod;
	std::ostringstream newCommand;

	if (eAct == MM::BeforeGet) {
		if( model_.compare("LBX") == 0) {
			command = "?am";
			RETURN_ON_MM_ERROR( SendAndReceive(command,answer) );
	
			if( answer.compare("1") == 0)
				pProp->Set("On");
			else
				pProp->Set("Off");
		} else {
			pProp->Set("Off");
		}
	}
	else if (eAct == MM::AfterSet) {
		if( model_.compare("LBX") == 0) {

			pProp->Get(Amod);
			if( Amod.compare("On") == 0)
				query = "1";
			else
				query = "0";
			newCommand << "am " << query;
			command = newCommand.str();

			RETURN_ON_MM_ERROR( SendAndReceive(command,answer) );
		} else {
			// do nothing for LMX or LCX
		}
	}
	return DEVICE_OK;
}


int LaserBoxx::OnDigitalMod(MM::PropertyBase* pProp, MM::ActionType eAct )
{
	std::string command, query, answer, Dmod;
	std::ostringstream newCommand;

	if (eAct == MM::BeforeGet) {
		if( model_.compare("LBX") == 0) {
			command = "?ttl";
			RETURN_ON_MM_ERROR( SendAndReceive(command,answer) );

			if( answer.compare("1") == 0)
				pProp->Set("On");
			else
				pProp->Set("Off");
		} else {
			pProp->Set("Off");
		}
	}
	else if (eAct == MM::AfterSet) {
		if( model_.compare("LBX") == 0) {

			pProp->Get(Dmod);
			if( Dmod.compare("On") == 0)
				query = "1";
			else
				query = "0";
			newCommand << "ttl " << query;
			command = newCommand.str();

			RETURN_ON_MM_ERROR( SendAndReceive(command,answer) );
		} else {
			// do nothing for LMX or LCX
		}
	}
	return DEVICE_OK;
}


int LaserBoxx::OnHours(MM::PropertyBase* pProp, MM::ActionType )
{
	std::string command, answer;
               
	command = "?hh";
	RETURN_ON_MM_ERROR( SendAndReceive(command,answer) );

	pProp->Set( atof(answer.c_str()) );

	return DEVICE_OK;
}


int LaserBoxx::OnBaseTemp(MM::PropertyBase* pProp, MM::ActionType )
{
	std::string command, answer;
               
	command = "?bt";
	RETURN_ON_MM_ERROR( SendAndReceive(command,answer) );

	pProp->Set( atof(answer.c_str()) );
     
	return DEVICE_OK;
}

//********************
// Shutter API
//********************

int LaserBoxx::SetOpen(bool open)
{
    return EmissionOnOff((int) open);
}

int LaserBoxx::GetOpen(bool& open)
{
    int state;
    RETURN_ON_MM_ERROR( GetStatus(state) );
    
    if ( (state==1) || (state==3) || (state==7) )
        open = true;
    else
		open = false;
    
    return DEVICE_OK;
}

// Laser is ON for deltaT milliseconds
int LaserBoxx::Fire(double deltaT)
{
	SetOpen(true);
	CDeviceUtils::SleepMs((long)(deltaT));
	SetOpen(false);
    return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// Generic--purpose, private methods
///////////////////////////////////////////////////////////////////////////////

int LaserBoxx::GetStatus(int &status)
{
	std::string command,answer;
               
	command = "?sta";
	RETURN_ON_MM_ERROR( SendAndReceive(command,answer) );

	status = atoi(answer.c_str());
     
	return DEVICE_OK;
}


int LaserBoxx::SendAndReceive(std::string& command, std::string& answer)
{
	RETURN_ON_MM_ERROR( SendSerialCommand(port_.c_str(), command.c_str(), "\n") );
	RETURN_ON_MM_ERROR( GetSerialAnswer(port_.c_str(), "\r\n", answer) );
     
	return DEVICE_OK;
}