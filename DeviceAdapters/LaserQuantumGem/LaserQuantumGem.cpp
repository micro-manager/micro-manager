//-----------------------------------------------------------------------------
// FILE:          LaserQuantumGem.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Controls LaserQuantum gem laser through serial port
// COPYRIGHT:     EMBL
// LICENSE:       LGPL
// AUTHOR:        Joran Deschamps
//-----------------------------------------------------------------------------

#include "LaserQuantumGem.h"
#include <iostream>
#include <fstream>

#ifdef WIN32
#include "winuser.h"
#endif

const char* g_DeviceGemName = "Gem";
const int maxcount = 10;

//-----------------------------------------------------------------------------
// MMDevice API
//-----------------------------------------------------------------------------

MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_DeviceGemName, MM::GenericDevice, "LaserQuantum gem laser");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
    if (deviceName == 0)
	   return 0;

    if (strcmp(deviceName, g_DeviceGemName) == 0)
    {
	   return new LaserQuantumGem;
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

LaserQuantumGem::LaserQuantumGem():
   port_("Undefined"),
   version_("Undefined"),
   initialized_(false),
   busy_(false),
   controlmode_(true),
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

     // Description
     CreateProperty(MM::g_Keyword_Description, "LaserQuantum gem Controller", MM::String, true);

     // Port
     CPropertyAction* pAct = new CPropertyAction (this, &LaserQuantumGem::OnPort);
     CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

	 // Maximum power
	 pAct = new CPropertyAction (this, &LaserQuantumGem::OnMaximumPower);
     CreateProperty("Maximum power (mW)", "500", MM::Float, false, pAct, true);
}

LaserQuantumGem::~LaserQuantumGem()
{
     Shutdown();
}

void LaserQuantumGem::GetName(char* Name) const
{
     CDeviceUtils::CopyLimitedString(Name, g_DeviceGemName);
}

int LaserQuantumGem::Initialize()
{
	 std::vector<std::string> commandsOnOff;
     commandsOnOff.push_back("Off");
     commandsOnOff.push_back("On");
	 
	 std::vector<std::string> controlModes;
     controlModes.push_back("Power");
     controlModes.push_back("Current");

	 std::vector<std::string> startupStatus;
     controlModes.push_back("Enabled");
     controlModes.push_back("Disabled");

	 std::ofstream log;
	 log.open ("Log_LaserQuantumGem.txt", std::ios::app);
	 log << "----Init -----\n";
	 
	 // Version number
	 version_ = getVersion(); 
	 log << "version: "<< version_ <<"\n";
     CPropertyAction* pAct = new CPropertyAction (this, &LaserQuantumGem::OnVersion);
     int nRet = CreateProperty("Version", version_.c_str(), MM::String, true, pAct);
	 if (DEVICE_OK != nRet)
          return nRet;
	 
	 // Laser On/Off
	 getStatus(&status_);
	 if(status_){
		 pAct = new CPropertyAction (this, &LaserQuantumGem::OnLaserOnOFF);
		 nRet = CreateProperty("Laser Operation", "On", MM::String, false, pAct);
		 SetAllowedValues("Laser Operation", commandsOnOff);
		 if (DEVICE_OK != nRet)
			  return nRet;
	 } else {
		 pAct = new CPropertyAction (this, &LaserQuantumGem::OnLaserOnOFF);
		 nRet = CreateProperty("Laser Operation", "Off", MM::String, false, pAct);
		 SetAllowedValues("Laser Operation", commandsOnOff);
		 if (DEVICE_OK != nRet)
			  return nRet;
	 }

	 // Control mode
	 getControlMode(&controlmode_);
	 if(controlmode_){
		 pAct = new CPropertyAction (this, &LaserQuantumGem::OnControlMode);
		 nRet = CreateProperty("Control mode", "Power", MM::String, false, pAct);
		 SetAllowedValues("Control mode", controlModes);
		 if (DEVICE_OK != nRet)
			  return nRet;
	 } else {
		 pAct = new CPropertyAction (this, &LaserQuantumGem::OnControlMode);
		 nRet = CreateProperty("Control mode", "Current", MM::String, false, pAct);
		 SetAllowedValues("Control mode", controlModes);
		 if (DEVICE_OK != nRet)
			  return nRet;
	 }

	 // Power
	 getPower(&power_);
	 if(power_ > maxpower_){
		 setPower(maxpower_);
		 power_ = maxpower_;
	 }
	 pAct = new CPropertyAction (this, &LaserQuantumGem::OnPower);
     nRet = CreateProperty("Laser power", to_string(power_).c_str(), MM::Float, false, pAct);
	 SetPropertyLimits("Laser power", 0, maxpower_);
     if (DEVICE_OK != nRet)
          return nRet;

	 // Current
	 getCurrent(&current_);
	 pAct = new CPropertyAction (this, &LaserQuantumGem::OnCurrent);
	 nRet = CreateProperty("Laser current (%)", to_string(current_).c_str(), MM::Integer, false, pAct);
	 SetPropertyLimits("Laser current (%)", 0, 100);
     if (DEVICE_OK != nRet)
          return nRet;
	 
	 // Startup power
	 pAct = new CPropertyAction (this, &LaserQuantumGem::OnStartUpPower);
     nRet = CreateProperty("Start-up power", "0", MM::Float, false, pAct);
	 SetPropertyLimits("Start-up power", 0, maxpower_);
     if (DEVICE_OK != nRet)
          return nRet;

	 // Startup status
	 pAct = new CPropertyAction (this, &LaserQuantumGem::OnStartUpStatus);
     nRet = CreateProperty("Start-up status", "Enabled", MM::String, false, pAct);
     SetAllowedValues("Start-up status", startupStatus);
     if (DEVICE_OK != nRet)
          return nRet;

	 // APC calibration
     pAct = new CPropertyAction (this, &LaserQuantumGem::OnAPCCalibration);
	 nRet = CreateProperty("APC calibration", to_string(maxpower_).c_str(), MM::Float, false, pAct);
	 SetPropertyLimits("APC calibration", 0, maxpower_);
     if (DEVICE_OK != nRet)
          return nRet;

	 /////////////////// read only

	 // Laser temperature
	 getLaserTemperature(&lasertemperature_);
     pAct = new CPropertyAction (this, &LaserQuantumGem::OnLaserTemperature);
	 nRet = CreateProperty("Laser temperature", to_string(lasertemperature_).c_str(), MM::Float, true, pAct);
     if (DEVICE_OK != nRet)
          return nRet;

	 // PSU temperature
	 getPSUTemperature(&psutemperature_);
	 pAct = new CPropertyAction (this, &LaserQuantumGem::OnPSUTemperature);
	 nRet = CreateProperty("PSU temperature", to_string(psutemperature_).c_str(), MM::Float, true, pAct);
     if (DEVICE_OK != nRet)
          return nRet;

	 //////////////// timers
	 getTimers(&psutime_, &laserenabledtime_, &laseroperationtime_);

	 // PSU Timer
	 CPropertyActionEx* pActex = new CPropertyActionEx (this, &LaserQuantumGem::OnTimers,0);
	 nRet = CreateProperty("PSU timer", to_string(psutime_).c_str(), MM::Float, true, pAct);
     if (DEVICE_OK != nRet)
          return nRet;

     // Laser enabled timer
	 pActex = new CPropertyActionEx (this, &LaserQuantumGem::OnTimers,1);
     nRet = CreateProperty("Enabled timer", to_string(laserenabledtime_).c_str(), MM::Float, true, pAct);
     if (DEVICE_OK != nRet)
          return nRet;

	 // Laser operation timer
	 pActex = new CPropertyActionEx (this, &LaserQuantumGem::OnTimers,2);
     nRet = CreateProperty("Operation timer", to_string(laseroperationtime_).c_str(), MM::Float, true, pAct);
     if (DEVICE_OK != nRet)
          return nRet;
	 
	 log.close();

     initialized_ = true;
     return DEVICE_OK;
}

int LaserQuantumGem::Shutdown()
{
   if (initialized_)
   {
		setLaserOnOff(false);
		initialized_ = false;	 
   }
   return DEVICE_OK;
}

bool LaserQuantumGem::Busy()
{
   return busy_;
}



//---------------------------------------------------------------------------
// Conveniance functions
//---------------------------------------------------------------------------


std::string LaserQuantumGem::getVersion(){
	std::ostringstream command;
    std::string answer;

    command << "VERSION?";

	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
	if (ret != DEVICE_OK) 
		return "Error";
 
	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK) 
		return "Error";
	
	// check sanity of answer
	if(answer.find("SMD12") != std::string::npos)
		return answer;

	return "Error";
}

int LaserQuantumGem::write(){
	std::ostringstream command;
    std::string answer;

    command << "WRITE";
	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
	if (ret != DEVICE_OK) 
		return ret;

	// get answer from laser, should be empty
	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK) 
		return ret;

	return DEVICE_OK;
}
	
int LaserQuantumGem::getPower(double* power){
	std::ostringstream command;
    std::string answer;

	double pow = 0;

    command << "POWER?";
	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
	if (ret != DEVICE_OK) 
		return ret;

	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK) 
		return ret;

	std::size_t found = answer.find("mW");
	if (found!=std::string::npos){
		std::string s = answer.substr(0,s.length()-2);
		std::stringstream streamval(s);
		streamval >> pow;

		*power = pow;
	}

	return DEVICE_OK;
}

int LaserQuantumGem::getCurrent(double* current){
	std::ostringstream command;
    std::string answer;

	double curr = 0;

    command << "CURRENT?";
	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
	if (ret != DEVICE_OK) 
		return ret;

	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK) 
		return ret;

	std::size_t found = answer.find("%");
	if (found!=std::string::npos){
		std::string s = answer.substr(0,s.length()-1);
		std::stringstream streamval(s);
		streamval >> curr;	

		*current = curr;
	}

	return DEVICE_OK;
}
	
int LaserQuantumGem::getControlMode(bool* mode){
	std::ostringstream command;
    std::string answer;

    command << "CONTROL?";
	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
	if (ret != DEVICE_OK) 
		return ret;

	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK) 
		return ret;

	if (answer.compare("POWER") == 0){
		*mode = true;
		return DEVICE_OK;
	} else if (answer.compare("CURRENT") == 0){
		*mode = false;
		return DEVICE_OK;
	}

	return DEVICE_OK;
}

int LaserQuantumGem::getLaserTemperature(double* temperature){
	std::ostringstream command;
    std::string answer;

	double temp = 0;

    command << "LASTEMP?";
	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
	if (ret != DEVICE_OK) 
		return ret;

	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK) 
		return ret;

	std::size_t found = answer.find("C");
	if (found!=std::string::npos){
		std::string s = answer.substr(0,s.length()-1);
		std::stringstream streamval(s);
		streamval >> temp;	

		*temperature = temp;
	}

	return DEVICE_OK;
}

int LaserQuantumGem::getPSUTemperature(double* temperature){
	std::ostringstream command;
    std::string answer;

	double temp = 0;

    command << "PSUTEMP?";
	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
	if (ret != DEVICE_OK) 
		return ret;

	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK) 
		return ret;

	std::size_t found = answer.find("C");
	if (found!=std::string::npos){
		std::string s = answer.substr(0,s.length()-1);
		std::stringstream streamval(s);
		streamval >> temp;	

		*temperature = temp;
	}

	return DEVICE_OK;
}


int LaserQuantumGem::getStatus(bool* status){
	std::ostringstream command;
    std::string answer;

    command << "STATUS?";
	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
	if (ret != DEVICE_OK) 
		return ret;

	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK) 
		return ret;

	if (answer.compare("ENABLED") == 0){
		*status = true;
		return DEVICE_OK;
	} else if (answer.compare("DISABLED") == 0){
		*status = false;
		return DEVICE_OK;
	}

	return DEVICE_OK;
}

int LaserQuantumGem::getTimers(double* psutime, double* laserenabletime, double* laseroperationtime){
	std::ostringstream command;
    std::string answer;

	double temp = 0;

    command << "TIMERS?";
	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
	if (ret != DEVICE_OK) 
		return ret;

	// PSU
	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK) 
		return ret;

	std::size_t tag = answer.find("PSU Time");
	if (tag!=std::string::npos){
		std::size_t found = answer.find(" = ");
		std::size_t found2 = answer.find(" Hours");
		std::string s = answer.substr(found+3,found2-found-3);
		std::stringstream streamval(s);
		streamval >> temp;

		*psutime = temp;
	}
		
	// Laser enabled
	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK) 
		return ret;

	tag = answer.find("Laser Enabled Time");
	if (tag!=std::string::npos){
		std::size_t found = answer.find(" = ");
		std::size_t found2 = answer.find(" Hours");
		std::string s = answer.substr(found+3,found2-found-3);
		std::stringstream streamval(s);
		streamval >> temp;

		*laserenabletime = temp;
	}

	// Operation time
	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK) 
		return ret;

	tag = answer.find("Laser Operation Time");
	if (tag!=std::string::npos){
		std::size_t found = answer.find(" = ");
		std::size_t found2 = answer.find(" Hours");
		std::string s = answer.substr(found+3,found2-found-3);
		std::stringstream streamval(s);
		streamval >> temp;

		*laseroperationtime = temp;
	}

	// purge port from empty answer
	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK) 
		return ret;

	return ret;
}

//////////// Setters
int LaserQuantumGem::setLaserOnOff(bool b){
	std::ostringstream command;
    std::string answer;

	if(b){
		command << "ON";
		int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
		if (ret != DEVICE_OK) 
			return ret;

		ret = GetSerialAnswer(port_.c_str(), "\r", answer);
		if (ret != DEVICE_OK) 
			return ret;
	} else {
		command << "OFF";
		int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
		if (ret != DEVICE_OK) 
			return ret;

		ret = GetSerialAnswer(port_.c_str(), "\r", answer);
		if (ret != DEVICE_OK) 
			return ret;
	}

	return DEVICE_OK;
}

int LaserQuantumGem::setPower(double pow){
	std::ostringstream command;
    std::string answer;

	if(pow >= 0 && pow<=maxpower_){
		command << "POWER=" << pow;
		int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");		
		if (ret != DEVICE_OK) 
			return ret;

		ret = GetSerialAnswer(port_.c_str(), "\r", answer); // empty answer
		if (ret != DEVICE_OK) 
			return ret;
	}

	return DEVICE_OK;
}

int LaserQuantumGem::setCurrent(double current){
	std::ostringstream command;
    std::string answer;

	if(current >= 0 && current<=100){
		command << "CURRENT=" << current;
		int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");		
		if (ret != DEVICE_OK) 
			return ret;

		ret = GetSerialAnswer(port_.c_str(), "\r", answer); // empty answer
		if (ret != DEVICE_OK) 
			return ret;
	}

	return DEVICE_OK;
}

int LaserQuantumGem::setControlMode(bool mode){
	std::ostringstream command;
    std::string answer;

	if(mode){
		command << "CONTROL=POWER";
		int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
		if (ret != DEVICE_OK) 
			return ret;

		ret = GetSerialAnswer(port_.c_str(), "\r", answer);
		if (ret != DEVICE_OK) 
			return ret;
	} else {
		command << "CONTROL=CURRENT";
		int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
		if (ret != DEVICE_OK) 
			return ret;

		ret = GetSerialAnswer(port_.c_str(), "\r", answer);
		if (ret != DEVICE_OK) 
			return ret;
	}

	return DEVICE_OK;
}
	
int LaserQuantumGem::setStartupPower(double pow){
	std::ostringstream command;
    std::string answer;

	if(pow >= 0 && pow<=maxpower_){
		command << "STPOW=" << pow;
		int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");		
		if (ret != DEVICE_OK) 
			return ret;

		ret = GetSerialAnswer(port_.c_str(), "\r", answer); // empty answer
		if (ret != DEVICE_OK) 
			return ret;

		write(); // write to memory 
	}

	return DEVICE_OK;
}

int LaserQuantumGem::setStartupStatus(bool b){
	std::ostringstream command;
    std::string answer;

	if(b){
		command << "STEN=YES";
		int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
		if (ret != DEVICE_OK) 
			return ret;

		ret = GetSerialAnswer(port_.c_str(), "\r", answer);
		if (ret != DEVICE_OK) 
			return ret;
		
		write();
	} else {
		command << "STEN=NO";
		int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
		if (ret != DEVICE_OK) 
			return ret;

		ret = GetSerialAnswer(port_.c_str(), "\r", answer);
		if (ret != DEVICE_OK) 
			return ret;

		write();
	}

	return DEVICE_OK;
}

int LaserQuantumGem::setAPCCalibration(double pow){
	std::ostringstream command;
    std::string answer;

	if(pow >= 0 && pow<=maxpower_){
		command << "ACTP=" << pow;
		int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");		
		if (ret != DEVICE_OK) 
			return ret;

		ret = GetSerialAnswer(port_.c_str(), "\r", answer); // empty answer
		if (ret != DEVICE_OK) 
			return ret;

		write(); // write to memory 
	}

	return DEVICE_OK;
}

//---------------------------------------------------------------------------
// Read only properties
//---------------------------------------------------------------------------

int LaserQuantumGem::OnPort(MM::PropertyBase* pProp , MM::ActionType eAct)
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

int LaserQuantumGem::OnVersion(MM::PropertyBase* pProp, MM::ActionType eAct)
{
     if (eAct == MM::BeforeGet)
     {
          pProp->Set(version_.c_str());
     }

     return DEVICE_OK;
}


//---------------------------------------------------------------------------
// Action handlers
//---------------------------------------------------------------------------

int LaserQuantumGem::OnLaserOnOFF(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet){
		getStatus(&status_);

		if(status_){
			pProp->Set("On");
		} else {
			pProp->Set("Off");
		}
	} else if (eAct == MM::AfterSet){
		std::string status;
        pProp->Get(status);

		if(status.compare("On") == 0){
			status_ = true;
		} else {
			status_ = false;
		}
		
		setLaserOnOff(status_);
   }

   return DEVICE_OK;
}

int LaserQuantumGem::OnControlMode(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet){
		getControlMode(&controlmode_);

		if(controlmode_){
			pProp->Set("Power");
		} else {
			pProp->Set("Current");
		}
	} else if (eAct == MM::AfterSet){
		std::string mode;
        pProp->Get(mode);

		if(mode.compare("Power") == 0){
			controlmode_ = true;
		} else if(mode.compare("Current") == 0){
			controlmode_ = false;
		}

		setControlMode(controlmode_);
   }

   return DEVICE_OK;
}

int LaserQuantumGem::OnPower(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet){
		getPower(&power_);
		pProp->Set(power_);

	} else if (eAct == MM::AfterSet){
		double pow;
        pProp->Get(pow);

		if(pow>=0 && pow<maxpower_){
			power_ = pow;
			setPower(power_);
		}
   }

   return DEVICE_OK;
}

int LaserQuantumGem::OnMaximumPower(MM::PropertyBase* pProp, MM::ActionType eAct){
   if (eAct == MM::BeforeGet){
      pProp->Set(maxpower_);
   } else if (eAct == MM::AfterSet){
      pProp->Get(maxpower_);
   }
   return DEVICE_OK;
}

int LaserQuantumGem::OnCurrent(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet){
		getCurrent(&current_);
		pProp->Set(current_);

	} else if (eAct == MM::AfterSet){
		double current;
        pProp->Get(current);

		if(current>=0 && current<100){
			current_ = current;
			setCurrent(current_);
		}
   }

   return DEVICE_OK;
}

int LaserQuantumGem::OnStartUpPower(MM::PropertyBase* pProp, MM::ActionType eAct){ // this might need to be changed
	if (eAct == MM::AfterSet){
		double pow;
        pProp->Get(pow);

		if(pow>=0 && pow<maxpower_){
			setStartupPower(pow);
		}
   }

   return DEVICE_OK;
}

int LaserQuantumGem::OnStartUpStatus(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet){ 
		if(startupstatus_){
			pProp->Set("Enabled");
		} else {
			pProp->Set("Disabled");
		}
	} else if (eAct == MM::AfterSet){
		std::string status;
        pProp->Get(status);

		if(status.compare("Enabled") == 0){
			startupstatus_ = true;
		} else if(status.compare("Disabled") == 0){
			startupstatus_ = false;
		}

		setStartupStatus(startupstatus_);
   }

   return DEVICE_OK;
}

int LaserQuantumGem::OnAPCCalibration(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet){
		pProp->Set(apccalibpower_);
	} else if (eAct == MM::AfterSet){
		double pow;
        pProp->Get(pow);

		if(pow>=0 && pow<maxpower_){
			apccalibpower_ = pow;
			setAPCCalibration(apccalibpower_);
		}
   }

   return DEVICE_OK;
}

////// read only
int LaserQuantumGem::OnLaserTemperature(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet){
		getLaserTemperature(&lasertemperature_);
		pProp->Set(lasertemperature_);
	}
    return DEVICE_OK;
}

int LaserQuantumGem::OnPSUTemperature(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet){
		getPSUTemperature(&psutemperature_);
		pProp->Set(psutemperature_);
	}
    return DEVICE_OK;
}

int LaserQuantumGem::OnTimers(MM::PropertyBase* pProp, MM::ActionType eAct, long tempnumber){
	if (eAct == MM::BeforeGet){
		getTimers(&psutime_, &laserenabledtime_, &laseroperationtime_);

		if(tempnumber == 0){
			pProp->Set(psutime_);
		} else if (tempnumber == 1){
			pProp->Set(laserenabledtime_);
		} else {
			pProp->Set(laseroperationtime_);
		}
	}
    return DEVICE_OK;
}
