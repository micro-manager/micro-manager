///////////////////////////////////////////////////////////////////////////////
// FILE:          Sapphire.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Sapphire controller adapter
// COPYRIGHT:     University of California, San Francisco, 2009
//
// AUTHOR:        Karl Hoover, UCSF + Paul Wiggins, UWash 
//
// LICENSE:       This file is distributed under the BSD license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
// CVS:           
//



#ifdef WIN32
   #include <windows.h>
   #define snprintf _snprintf 
#endif


#include "../../MMDevice/MMDevice.h"
#include "Sapphire.h"
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include "../../MMDevice/DeviceUtils.h"
#include <sstream>

// Controller
const char* g_ControllerName = "Sapphire";
const char* g_Keyword_PowerSetpoint = "PowerSetpoint";
const char* g_Keyword_PowerReadback = "PowerReadback";

// Commands can be terminated with CR or LF, but not both
const char * COMMAND_TERM = "\r";
// Responses are always terminated with CRLF
const char * RESPONSE_TERM = "\r\n";




///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_ControllerName, MM::ShutterDevice, "Sapphire Laser");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_ControllerName) == 0)
   {
      // create Controller
      Sapphire* pSapphire = new Sapphire(g_ControllerName);
      return pSapphire;
   }

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
// Controller implementation
// ~~~~~~~~~~~~~~~~~~~~

Sapphire::Sapphire(const char* name) :
   state_(0),
   error_(0),
   initialized_(false), 
   name_(name), 
   busy_(false),
   changedTime_(0.0),
	queryToken_("?"),
	powerSetpointToken_("P"),
	powerReadbackToken_("P"),
	//CDRHToken_("CDRH"),  // if this is on, laser delays 5 SEC before turning on
   //CWToken_("CW"),
	laserOnToken_("L"),
	TECServoToken_("T"),
	headSerialNoToken_("HID"),
	headUsageHoursToken_("HH")
	//wavelengthToken_("WAVE"),
	//externalPowerControlToken_("EXT")



{
	//pDevImpl = new DevImpl(*this);
   assert(strlen(name) < (unsigned int) MM::MaxStrLength);

   InitializeDefaultErrorMessages();
   SetErrorText(ERR_DEVICE_NOT_FOUND, "No answer received.  Is the Coherent Sapphire connected to this serial port?");
   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "Coherent Sapphire Laser", MM::String, true);

   // Port
   CPropertyAction* pAct = new CPropertyAction (this, &Sapphire::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

   EnableDelay(); // signals that the delay setting will be used
   UpdateStatus();
}

Sapphire::~Sapphire()
{
	//delete pDevImpl;
   Shutdown();
}

bool Sapphire::Busy()
{
   MM::MMTime interval = GetCurrentMMTime() - changedTime_;
   MM::MMTime delay(GetDelayMs()*1000.0);
   if (interval < delay)
      return true;
   else
      return false;
}

void Sapphire::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}


int Sapphire::Initialize()
{

   LogMessage("Sapphire::Initialize()");

   GeneratePowerProperties();
   GeneratePropertyState();
   GenerateReadOnlyIDProperties();
	std::stringstream msg;


	ReadGreeting();

	//disable echo from the controller
	msg << "E" << "=" << 0;
   Send(msg.str());
   if (ReceiveOneLine() != DEVICE_OK)
      return ERR_DEVICE_NOT_FOUND;


	//disable command prompt from controller
	msg.str("");
	msg << ">" << "=" << 0;
   Send(msg.str());
   ReceiveOneLine();

	//msg.str("");
	//msg << CDRHToken_ << "=" << 0;
  // Send(msg.str());
  // ReceiveOneLine();

	// enable control of the laser diode
	msg.str("");
	msg << TECServoToken_ << "=" << 1;
   Send(msg.str());
   ReceiveOneLine();

	//disable external 'analogue' control of the laser

	// enable control of the laser diode
	//msg.str("");
	//msg << externalPowerControlToken_ << "=" << 0;
   //Send(msg.str());
   //ReceiveOneLine();

	// query laser for power limits
	this->initLimits();

	double llimit = this->minlp();
	double ulimit = this->maxlp();
	//if ( 1. > ulimit)
	//	ulimit = 100.; // for off-line test

	// set the limits as interrogated from the laser controller.
   SetPropertyLimits(g_Keyword_PowerSetpoint, llimit, ulimit);  // milliWatts
   
   initialized_ = true;



   return HandleErrors();

}

void Sapphire::ReadGreeting()
{
   do {
      ReceiveOneLine();
   } while (! buf_string_.empty());
}




/////////////////////////////////////////////
// Property Generators
/////////////////////////////////////////////

void Sapphire::GeneratePropertyState()
{
   
	CPropertyAction* pAct = new CPropertyAction (this, &Sapphire::OnState);
   CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
   AddAllowedValue(MM::g_Keyword_State, "0");
   AddAllowedValue(MM::g_Keyword_State, "1");
}


void Sapphire::GeneratePowerProperties()
{
   string powerName;

	// Power Setpoint
   CPropertyActionEx* pActEx = new CPropertyActionEx(this, &Sapphire::OnPowerSetpoint, 0);
   powerName = g_Keyword_PowerSetpoint;
   CreateProperty(powerName.c_str(), "0", MM::Float, false, pActEx);
	
	// Power Setpoint
   pActEx = new CPropertyActionEx(this, &Sapphire::OnPowerReadback, 0);
   powerName = g_Keyword_PowerReadback;
   CreateProperty(powerName.c_str(), "0", MM::Float, true, pActEx);

	// External Laser Power Control ( if EXT = 1, then an 'analogue' trigger line will control power on
   //CPropertyAction* pAct = new CPropertyAction(this, &Sapphire::OnExternalLaserPowerControl);
   //powerName = "ExternalLaserPowerControl";
	//CreateProperty(powerName.c_str(), "0", MM::Integer, false, pAct);
	//AddAllowedValue("ExternalLaserPowerControl", "0");
   //AddAllowedValue("ExternalLaserPowerControl", "1");
  
   // CW (1) or pulsed (0) mode 
   //pAct = new CPropertyAction(this, &Sapphire::OnCWMode);
   //powerName = "CWMode";
	//CreateProperty(powerName.c_str(), "0", MM::Integer, false, pAct);
	//AddAllowedValue(powerName.c_str(), "0");
   //AddAllowedValue(powerName.c_str(), "1");
}


void Sapphire::GenerateReadOnlyIDProperties()
{
	CPropertyAction* pAct; 
 

   pAct = new CPropertyAction(this, &Sapphire::OnMinimumLaserPower);
   CreateProperty("Minimum Laser Power", "", MM::Float, false, pAct);
   
	pAct = new CPropertyAction(this, &Sapphire::OnMaximumLaserPower);
   CreateProperty("Maximum Laser Power", "", MM::Float, false, pAct);

	pAct = new CPropertyAction(this, &Sapphire::OnWaveLength);
   CreateProperty("Wavelength", "", MM::Float, false, pAct);


}

int Sapphire::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return HandleErrors();
}


///////////////////////////////////////////////////////////////////////////////
// String utilities
///////////////////////////////////////////////////////////////////////////////


void Sapphire::StripString(string& StringToModify)
{
   if(StringToModify.empty()) return;

   size_t startIndex = StringToModify.find_first_not_of(" ");
   size_t endIndex = StringToModify.find_last_not_of(" ");
   string tempString = StringToModify;
   StringToModify.erase();

   StringToModify = tempString.substr(startIndex, (endIndex-startIndex+ 1) );
}

void Sapphire::Tokenize(const string& str, vector<string>& tokens, const string& delimiters)
{
    tokens.clear();
     // Borrowed from http://oopweb.com/CPP/Documents/CPPHOWTO/Volume/C++Programming-HOWTO-7.html
    // Skip delimiters at beginning.
    string::size_type lastPos = str.find_first_not_of(delimiters, 0);
    // Find first "non-delimiter".
    string::size_type pos     = str.find_first_of(delimiters, lastPos);

    while (string::npos != pos || string::npos != lastPos)
    {
        // Found a token, add it to the vector.
        tokens.push_back(str.substr(lastPos, pos - lastPos));
        // Skip delimiters.  Note the "not_of"
        lastPos = str.find_first_not_of(delimiters, pos);
        // Find next "non-delimiter"
        pos = str.find_first_of(delimiters, lastPos);
    }
}




///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////


int Sapphire::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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

   return HandleErrors();
}


int Sapphire::OnPowerReadback(MM::PropertyBase* pProp, MM::ActionType eAct, long /*index*/)
{

   double powerReadback;
   if (eAct == MM::BeforeGet)
   {
      GetPowerReadback(powerReadback);
      pProp->Set(powerReadback);
   }
   else if (eAct == MM::AfterSet)
   {
			// never do anything!!
   }
   return HandleErrors();
}

int Sapphire::OnPowerSetpoint(MM::PropertyBase* pProp, MM::ActionType eAct, long  /*index*/)
{

   double powerSetpoint;
   if (eAct == MM::BeforeGet)
   {
      GetPowerSetpoint(powerSetpoint);
      pProp->Set(powerSetpoint);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(powerSetpoint);
		double achievedSetpoint;
      SetPowerSetpoint(powerSetpoint, achievedSetpoint);
		if( 0. != powerSetpoint)
		{
			double fractionError = fabs(achievedSetpoint - powerSetpoint) / powerSetpoint;
			if (( 0.05 < fractionError ) && (fractionError  < 0.10))
				pProp->Set(achievedSetpoint);
		}
   }
   return HandleErrors();
}


int Sapphire::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      GetState(state_);
      pProp->Set(state_);
   }
   else if (eAct == MM::AfterSet)
   {
		long requestedState;
      pProp->Get(requestedState);
      SetState(requestedState);
		if (state_ != requestedState)
		{
			//error_ = DEVICE_CAN_NOT_SET_PROPERTY;
		}
   }
   
   return HandleErrors();
}


int Sapphire::OnHeadID(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
		pProp->Set((this->queryLaser(headSerialNoToken_)).c_str());
   }
   else if (eAct == MM::AfterSet)
   {
			// never do anything!!
   }
   return HandleErrors();
}


int Sapphire::OnHeadUsageHours(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
		std::string svalue = this->queryLaser(headUsageHoursToken_);
		double dvalue = atof(svalue.c_str());
		pProp->Set(dvalue);
   }
   else if (eAct == MM::AfterSet)
   {
			// never do anything!!
   }
   return HandleErrors();
}


int Sapphire::OnMinimumLaserPower(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
		pProp->Set(minlp_);
   }
   else if (eAct == MM::AfterSet)
   {
	        pProp->Get(minlp_);

			SetPropertyLimits(g_Keyword_PowerSetpoint, minlp_, maxlp_);
   }
   return HandleErrors();
}



int Sapphire::OnMaximumLaserPower(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
		pProp->Set(maxlp_);
   }
   else if (eAct == MM::AfterSet)
   {
	        pProp->Get(maxlp_);

			SetPropertyLimits(g_Keyword_PowerSetpoint, minlp_, maxlp_);
   }
   return HandleErrors();
}

int Sapphire::OnExternalLaserPowerControl(MM::PropertyBase* pProp, MM::ActionType eAct)
{

   long externalLaserPowerControl;
	int itmp;
   if (eAct == MM::BeforeGet)
   {
      GetExternalLaserPowerControl(itmp);
		externalLaserPowerControl = itmp;
      pProp->Set(externalLaserPowerControl);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(externalLaserPowerControl);
		itmp = externalLaserPowerControl;
      SetExternalLaserPowerControl(itmp);
   }
   return HandleErrors();
}

int Sapphire::OnCWMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   long CWMode;
   if (eAct == MM::BeforeGet)
   {
	   string ans = this->queryLaser(CWToken_);
      CWMode = atol(ans.c_str());
      pProp->Set(CWMode);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(CWMode);
	   std::ostringstream atoken;
	   atoken << CWMode;
	   this->setLaser( CWToken_, atoken.str());
   }
   return HandleErrors();
}

int Sapphire::OnWaveLength(MM::PropertyBase* pProp, MM::ActionType eAct /* , long */)
{
   if (eAct == MM::BeforeGet)
   {
		pProp->Set(wave_);
   }
   else if (eAct == MM::AfterSet)
   {
	        pProp->Get(wave_);
   }
   return HandleErrors();
}


void Sapphire::GetPowerReadback(double& value)
{
   string ans = this->queryLaser(powerReadbackToken_);
	value = atof(ans.c_str());
}

void Sapphire::SetPowerSetpoint(double requestedPowerSetpoint, double& achievedPowerSetpoint)
{
	std::string result;
	std::ostringstream setpointString;
	// number like 100.00
	setpointString << setprecision(5) << requestedPowerSetpoint;
	result = this->setLaser("P", setpointString.str());
	//compare quantized setpoint to requested setpoint
	// the difference can be rather large

	//if( 0. < requestedPowerSetpoint)
	//{
		achievedPowerSetpoint = atof( result.c_str());

		// if device echos a setpoint more the 10% of full scale from requested setpoint, log a warning message
		if ( this->maxlp()/10. < fabs( achievedPowerSetpoint-requestedPowerSetpoint))
		{
			std::ostringstream messs;
			messs << "requested setpoint: " << requestedPowerSetpoint << " but echo setpoint is: " << achievedPowerSetpoint;
			LogMessage(messs.str().c_str());
		}
	//}

}

void Sapphire::GetPowerSetpoint(double& value)
{
	string ans = this->queryLaser(powerSetpointToken_);
	value = atof(ans.c_str());
}

void Sapphire::SetState(long state)
{
	std::ostringstream atoken;
	atoken << state;
	this->setLaser( laserOnToken_, atoken.str());
   // Set timer for the Busy signal
   changedTime_ = GetCurrentMMTime();
}

void Sapphire::GetState(long &value)
{
   string ans = this->queryLaser(laserOnToken_);
	value = atol(ans.c_str());
}

void Sapphire::SetExternalLaserPowerControl(int value)

{
	std::ostringstream atoken;
	atoken << value;
	//this->setLaser( externalPowerControlToken_, atoken.str());
}

void Sapphire::GetExternalLaserPowerControl(int& value)
{
	string ans = this->queryLaser(externalPowerControlToken_);
	value = atol(ans.c_str());
}

int Sapphire::HandleErrors()
{
   int lastError = error_;
   error_ = 0;
   return lastError;
}



/////////////////////////////////////
//  Communications
/////////////////////////////////////


void Sapphire::Send(string cmd)
{
   int ret = SendSerialCommand(port_.c_str(), cmd.c_str(), COMMAND_TERM);
   if (ret!=DEVICE_OK)
      error_ = DEVICE_SERIAL_COMMAND_FAILED;
}


int Sapphire::ReceiveOneLine()
{
   buf_string_.clear();
   int ret = GetSerialAnswer(port_.c_str(), RESPONSE_TERM, buf_string_);
   if (ret != DEVICE_OK)
      return ret;
   return DEVICE_OK;
}

void Sapphire::Purge()
{
   int ret = PurgeComPort(port_.c_str());
   if (ret!=0)
      error_ = DEVICE_SERIAL_COMMAND_FAILED;
}

//********************
// Shutter API
//********************

int Sapphire::SetOpen(bool open)
{
   SetState((long) open);
   return HandleErrors();
}

int Sapphire::GetOpen(bool& open)
{
   long state;
   GetState(state);
   if (state==1)
      open = true;
   else if (state==0)
      open = false;
   else
      error_ = DEVICE_UNKNOWN_POSITION;

   return HandleErrors();
}

// ON for deltaT milliseconds
// other implementations of Shutter don't implement this
// is this perhaps because this blocking call is not appropriate
int Sapphire::Fire(double deltaT)
{
	SetOpen(true);
	CDeviceUtils::SleepMs((long)(deltaT+.5));
	SetOpen(false);
   return HandleErrors();
}
