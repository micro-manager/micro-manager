///////////////////////////////////////////////////////////////////////////////
// FILE:          MPBLaser.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Unofficial device adapter for lasers from MPB Communications Inc.
//                
// AUTHOR:        Kyle M. Douglass, http://kmdouglass.github.io
//
// VERSION:       0.0.0
//                Update the changelog.md after every increment of the version.
//
// FIRMWARE:      VFL_MLDDS_SHGTT_VER_2.5.1.0
//				  (Device adapter is known to work with this firmware version.)
//                
// COPYRIGHT:     ECOLE POLYTECHNIQUE FEDERALE DE LAUSANNE, Switzerland
//                Laboratory of Experimental Biophysics (LEB), 2017-2018
//

#include "MPBLaser.h"
#include "ModuleInterface.h"
#include <vector>
#include <sstream>
#include <iterator>

using namespace std;

const char* g_DeviceName = "MPBLaser";

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////

/**
 * List all supported hardware devices here
 */
MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_DeviceName, MM::GenericDevice, "Lasers from MPB Communications Inc.");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   // decide which device class to create based on the deviceName parameter
   if (strcmp(deviceName, g_DeviceName) == 0)
   {
      // create the test device
      return new MPBLaser();
   }

   // ...supplied name not recognized
   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
// MPBLaser implementation
// ~~~~~~~~~~~~~~~~~~~~~~~

/**
* MPBLaser constructor.
* Setup default all variables and create device properties required to exist
* before intialization. In this case, no such properties were required. All
* properties will be created in the Initialize() method.
*
* As a general guideline Micro-Manager devices do not access hardware in the
* the constructor. We should do as little as possible in the constructor and
* perform most of the initialization in the Initialize() method.
*/
MPBLaser::MPBLaser() :
	// Parameter values before hardware synchronization
	laserMode_          (LaserMode::autoPowerControl),
	ldEnable_           (DeviceOnOff::off),
	ldCurrentSetpoint_  (0.0),
	powerSetpoint_      (0.0),
	shgTemp_            (0.0),
	tecTemp_            (0.0),
	tecCurrent_		    (0.0),
	ldCaseTemp_         (0.0),
	ldCurrent_          (0.0),
	opticalOutputPower_ (0.0),
	shgTECTemp_         (0.0),
	shgTECCurrent_      (0.0),
	keyLock_            (DeviceOnOff::off),
    initialized_        (false)
{
   // call the base class method to set-up default error codes/messages
   InitializeDefaultErrorMessages();

   // Port
   CPropertyAction* pAct = new CPropertyAction(this, &MPBLaser::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);
}

/**
* MPBLaser destructor.
* If this device used as intended within the Micro-Manager system,
* Shutdown() will be always called before the destructor. But in any case
* we need to make sure that all resources are properly released even if
* Shutdown() was not called.
*/
MPBLaser::~MPBLaser()
{
   if (initialized_)
      Shutdown();
}

/**
* Obtains device name.
* Required by the MM::Device API.
*/
void MPBLaser::GetName(char* name) const
{
   // We just return the name we use for referring to this
   // device adapter.
   CDeviceUtils::CopyLimitedString(name, g_DeviceName);
}

/**
* Intializes the hardware.
* Typically we access and initialize hardware at this point.
* Device properties are typically created here as well.
* Required by the MM::Device API.
*/
int MPBLaser::Initialize()
{
	if (initialized_)
		return DEVICE_OK;

	// set read-only properties
	// ------------------------
	// Name
	int nRet = CreateStringProperty(MM::g_Keyword_Name, g_DeviceName, true);
	if (DEVICE_OK != nRet)
		return nRet;

	// Description
	nRet = CreateStringProperty(
		       MM::g_Keyword_Description,
		       "Unofficial device adapter for lasers from MPB Communications Inc.",
		        true);
	if (DEVICE_OK != nRet)
		return nRet;

	// set settable properties
	// -----------------------
	GenerateControlledProperties();
	GenerateReadOnlyProperties();

    // synchronize all properties
    // --------------------------
    int ret = UpdateStatus();
    if (ret != DEVICE_OK)
       return ret;

    initialized_ = true;
    return DEVICE_OK;
}

/**
* Shuts down (unloads) the device.
* Ideally this method will completely unload the device and release all resources.
* Shutdown() may be called multiple times in a row.
* Required by the MM::Device API.
*/
int MPBLaser::Shutdown()
{
   initialized_ = false;
   return DEVICE_OK;
}

/////////////////////////////////////////////
// Serial Communications
/////////////////////////////////////////////

/**
 * Performs all duties related to device communication.
 */
std::string MPBLaser::QueryLaser(std::string msg)
{
	std::string result;

	PurgeBuffer();
	SendMsg(msg);
	ReadBuffer();
	result = GetLastMsg();

	return result;
}

const std::string MPBLaser::GetLastMsg()
{
	return buffer_;
}

int MPBLaser::PurgeBuffer()
{
	int ret = PurgeComPort(port_.c_str());
	if (ret != DEVICE_OK)
		return DEVICE_SERIAL_COMMAND_FAILED;

	return DEVICE_OK;
}

int MPBLaser::SendMsg(std::string msg)
{
	int ret = SendSerialCommand(port_.c_str(), msg.c_str(), CMD_TERM_.c_str());
	if (ret != DEVICE_OK)
		return DEVICE_SERIAL_COMMAND_FAILED;

	return DEVICE_OK;
}

int MPBLaser::ReadBuffer()
{
	std::string valid = "";
	int ret;
	
	// Get the data returned by the device.
	ret = GetSerialAnswer(port_.c_str(), ANS_TERM_.c_str(), buffer_);
	if (ret != DEVICE_OK)
		return ret;

	// Check the validity of the command that was sent. This is specific
	// to MPB's serial interface.
	ret = GetSerialAnswer(port_.c_str(), PROMPT_.c_str(), valid);
	if (ret != DEVICE_OK)
		return ret;

	// TODO This will return OK for ALL other values of valid.at(0)! Consider fixing...
	if (valid.at(0) == INVALID_CMD_)
		return DEVICE_SERIAL_INVALID_RESPONSE;
	else
		return DEVICE_OK;
}

/////////////////////////////////////////////
// Property Generators
/////////////////////////////////////////////

void MPBLaser::GenerateControlledProperties()
{
	// Turn on/off the laser diode
	CPropertyAction* pAct = new CPropertyAction(this, &MPBLaser::OnLDEnable);
	CreateProperty("Switch On/Off", laserDiodeOnOffLabels_.at(DeviceOnOff::off), MM::String, false, pAct);
	std::vector<std::string> commands;
	commands.push_back(laserDiodeOnOffLabels_.at(DeviceOnOff::off));
	commands.push_back(laserDiodeOnOffLabels_.at(DeviceOnOff::on));
	SetAllowedValues("Switch On/Off", commands);

	// Set the mode of laser operation (constant current or constant power)
	pAct = new CPropertyAction(this, &MPBLaser::OnLaserMode);
	CreateProperty("Set Laser Mode", laserModeLabels_.at(LaserMode::autoPowerControl), MM::String, false, pAct);
	commands.clear();
	commands.push_back(laserModeLabels_.at(LaserMode::autoPowerControl));
	commands.push_back(laserModeLabels_.at(LaserMode::autoCurrentControl));
	SetAllowedValues("Set Laser Mode", commands);

	// Power setpoint
	std::pair<double, double> limits = this->GetPowerSetPtLim();
	pAct = new CPropertyAction(this, &MPBLaser::OnPowerSetpoint);
	CreateProperty("Power Setpoint", "0.0", MM::Float, false, pAct);
	SetPropertyLimits("Power Setpoint", std::get<0>(limits), std::get<1>(limits));

	// Current setpoint
	int lowerLimit = this->GetMinLDCurrent();
	int upperLimit = this->GetMaxLDCurrent();
	pAct = new CPropertyAction(this, &MPBLaser::OnCurrentSetpoint);
	CreateProperty("Current Setpoint", "0", MM::Integer, false, pAct);
	SetPropertyLimits("Current Setpoint", lowerLimit, upperLimit);
}

/** 
 * Queries the laser for the current operating mode.
 */
void MPBLaser::GetLaserMode(LaserMode &laserMode)
{
	std::string ans = QueryLaser("getpowerenable");
	if (ans == "0")
		laserMode = LaserMode::autoCurrentControl;
	else if (ans == "1")
		laserMode = LaserMode::autoPowerControl;
}

/**
 * Sets the operating mode on the laser.
 */
void MPBLaser::SetLaserMode(LaserMode laserModeIn)
{
	// Send the command only if the device is not already in the desired mode.
	if (laserModeIn == LaserMode::autoCurrentControl && laserMode_ != LaserMode::autoCurrentControl)
		this->QueryLaser("powerenable 0");
	else if (laserModeIn == LaserMode::autoPowerControl && laserMode_ != LaserMode::autoPowerControl)
		this->QueryLaser("powerenable 1");
}

/**
 * Queries the laser for the power setpoint.
 */
void MPBLaser::GetPowerSetpoint(double &value)
{
	std::string ans = QueryLaser("getpower 0");
	value = atof(ans.c_str());
}

/**
 * Sets the value for the device's current power setpoint.
 */
void MPBLaser::SetPowerSetpoint(double setpoint)
{
	// Round setpoint value
	std::ostringstream setpointString;
	setpointString << setprecision(6) << setpoint;
	powerSetpoint_ = std::stod(setpointString.str());

	// Adjust the device's setpoint
	QueryLaser("setpower 0 " + setpointString.str());
}

void MPBLaser::GenerateReadOnlyProperties()
{
	// Laser state
	CPropertyAction* pAct = new CPropertyAction(this, &MPBLaser::OnLaserState);
	CreateStringProperty("State", laserStateLabels_.at(LaserState::off), true, pAct);

	// Status of the keylock
	pAct = new CPropertyAction(this, &MPBLaser::OnKeyLock);
	CreateStringProperty("Key Lock Status", keyLockLabels_.at(DeviceOnOff::off), true, pAct);
}

/**
 * Obtains the laser diode's On/Off setpoint.
 */
void MPBLaser::GetLDEnable(DeviceOnOff &deviceState)
{
	std::string ans = QueryLaser("getldenable");
	if (ans == "0")
		deviceState = DeviceOnOff::off;
	else if (ans == "1")
		deviceState = DeviceOnOff::on;
}

/**
 * Sets the laser diode setpoint to On or Off.
 */
void MPBLaser::SetLDEnable(DeviceOnOff deviceState)
{
	// Send the command only if the device is not already in the desired mode.
	if (deviceState == DeviceOnOff::off && ldEnable_ != DeviceOnOff::off)
	{
		ldEnable_ = DeviceOnOff::off;
		this->QueryLaser("setldenable 0");
	}
	else if (deviceState == DeviceOnOff::on && ldEnable_ != DeviceOnOff::on)
	{
		ldEnable_ = DeviceOnOff::on;
		this->QueryLaser("setldenable 1");
	}
}

/**
 * Returns the maximum current setpoint in ACC mode.
 */
int MPBLaser::GetMaxLDCurrent()
{
	std::string ans = QueryLaser("getacccurmax");

	// TODO: Check whether getacccurmax returns 0.
	// If yes, use the second value from getldlim 1 command.
	return std::stoi(ans);
}

/**
 * Returns the minimum current setpoint in ACC mode.
 */
int MPBLaser::GetMinLDCurrent()
{
	int lowerLimit;
	std::string ans = this->QueryLaser("getldlim 1");

	// Split the limits at the space character.
	std::istringstream buf(ans);
	std::istream_iterator<std::string> beg(buf), end;
	std::vector<std::string> tokens(beg, end);
	lowerLimit = std::stoi(tokens[0]);

	return lowerLimit;
}

/**
 * Queries the laser for the minimum and maximum power setpoint limits.
 */
std::pair<double, double> MPBLaser::GetPowerSetPtLim()
{
	std::pair<double, double> limits;
	std::string ans = this->QueryLaser("getpowersetptlim 0");

	// Split the limits at the space character.
	std::istringstream buf(ans);
	std::istream_iterator<std::string> beg(buf), end;
	std::vector<std::string> tokens(beg, end);

	limits = std::make_pair(std::stod(tokens[0]), std::stod(tokens[1]));
	return limits;

}

/**
 * Queries the laser for its current state and updates the device adapter.
 */
void MPBLaser::GetLaserState(MPBLaser::LaserState &laserStateIn)
{
	std::string ans = this->QueryLaser("getlaserstate");
	laserStateIn = laserStateCodes_.at(std::stoi(ans));
}

/////////////////////////////////////////////
// Action handlers
/////////////////////////////////////////////

int MPBLaser::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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

/**
 * Request the current state of the device.
 */
int MPBLaser::OnLaserState(MM::PropertyBase* pProp, MM::ActionType eAct)
{

	if (eAct == MM::BeforeGet)
	{
		this->GetLaserState(laserState_);
		pProp->Set(laserStateLabels_.at(laserState_));
	}
	else if (eAct == MM::AfterSet)
	{
		// No action. Property is read only.
	}
	return DEVICE_OK;
}

/**
 * Request the key lock state of the laser.
 */
int MPBLaser::OnKeyLock(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	std::string ans = QueryLaser("getinput 2");

	if (eAct == MM::BeforeGet)
	{
		if (ans == "1")
		{
			pProp->Set(keyLockLabels_.at(DeviceOnOff::off));
		}
		else if (ans == "0")
		{
			pProp->Set(keyLockLabels_.at(DeviceOnOff::on));
		}
		else
			return ERR_UNRECOGNIZED_KEYLOCK_STATE;
	}
	else if (eAct == MM::AfterSet)
	{
		// No action. Property is read only.
	}
	return DEVICE_OK;
}

int MPBLaser::OnLDEnable(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		// Set the MM value to match the current device state.
		this->GetLDEnable(ldEnable_);
		pProp->Set(laserDiodeOnOffLabels_.at(ldEnable_));
	}
	else if (eAct == MM::AfterSet)
	{
		// TODO Check that laser is in a settable state
		// Set the device state to match the MM value.
		std::string currMMState;
		pProp->Get(currMMState);
		if (currMMState == laserDiodeOnOffLabels_.at(DeviceOnOff::off))
			this->SetLDEnable(DeviceOnOff::off);
		else if (currMMState == laserDiodeOnOffLabels_.at(DeviceOnOff::on))
			this->SetLDEnable(DeviceOnOff::on);
		else
			return DEVICE_INVALID_INPUT_PARAM;
	}

    return DEVICE_OK;
}

int MPBLaser::OnLaserMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		// Synchronize the device adapter to the laser's current state.
		this->GetLaserMode(laserMode_);
		pProp->Set(laserModeLabels_.at(laserMode_));
	}
	else if (eAct == MM::AfterSet)
	{
		// Update the laser to reflect the device adapter's value.
		std::string currMode;
		pProp->Get(currMode);
		if (currMode == laserModeLabels_.at(LaserMode::autoCurrentControl))
			this->SetLaserMode(LaserMode::autoCurrentControl);
		else if (currMode == laserModeLabels_.at(LaserMode::autoPowerControl))
			this->SetLaserMode(LaserMode::autoPowerControl);
		else
			return DEVICE_INVALID_INPUT_PARAM;
	}

	return DEVICE_OK;
}

int MPBLaser::OnPowerSetpoint(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	// Obtain a pointer to the Property that can be modified.
	MM::Property* pChildProperty = (MM::Property*) pProp;

	// Check whether laser device is in APC mode and change whether its settable.
	this->GetLaserMode(laserMode_);
	if (laserMode_ == LaserMode::autoPowerControl)
		pChildProperty->SetReadOnly(false);
	else
	{
		pChildProperty->SetReadOnly(true);
		return DEVICE_OK;
	}

	if (eAct == MM::BeforeGet)
	{
		// Set the MM value to match the current device state.
		this->GetPowerSetpoint(powerSetpoint_);
		pProp->Set(powerSetpoint_);
	}
	else if (eAct == MM::AfterSet)
	{
		// Set the device state to match the MM value.
		pProp->Get(powerSetpoint_);
		this->SetPowerSetpoint(powerSetpoint_);
	}

	return DEVICE_OK;
}

/**
 * Adjust the current setpoint of the laser.
 */
int MPBLaser::OnCurrentSetpoint(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	// TODO: This method currently does nothing with regards to the current setpoint.
	// Obtain a pointer to the Property that can be modified.
	MM::Property* pChildProperty = (MM::Property*) pProp;

	// Check whether laser device is in ACC mode and change whether its settable.
	this->GetLaserMode(laserMode_);
	if (laserMode_ == LaserMode::autoCurrentControl)
		pChildProperty->SetReadOnly(false);
	else
	{
		pChildProperty->SetReadOnly(true);
		return DEVICE_OK;
	}	
	
	double currLdCurrentSetpoint = ldCurrentSetpoint_;
	if (eAct == MM::BeforeGet)
	{
		pProp->Set(currLdCurrentSetpoint);
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(currLdCurrentSetpoint);
		ldCurrentSetpoint_ = currLdCurrentSetpoint;
	}

	return DEVICE_OK;
}