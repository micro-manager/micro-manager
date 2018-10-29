///////////////////////////////////////////////////////////////////////////////
// FILE:          OxxiusCombiner.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Controls Oxxius lasers and combiners through a serial port
// COPYRIGHT:     Oxxius SA, 2013-2018
// LICENSE:       LGPL
// AUTHORS:       Tristan Martinez
//


#include "Oxxius_combiner.h"
#include <cstdio>
#include <cstdlib>
#include <string>
#include <map>
#include "../../MMDevice/ModuleInterface.h"
#include "OnBoardHW.h"

using namespace std;

//
#define	MAX_NUMBER_OF_SLOTS	6
#define	MDUAL_POSITIONS	11
#define	RCV_BUF_LENGTH 256
#define	NO_SLOT 0

// Oxxius devices
const char* g_OxxiusCombinerDeviceName = "Combiner";
const char* g_OxxiusLaserBoxxDeviceName = "LaserBoxx source";
const char* g_OxxiusLaserBoxx1DeviceName = "LaserBoxx source 1";
const char* g_OxxiusLaserBoxx2DeviceName = "LaserBoxx source 2";
const char* g_OxxiusLaserBoxx3DeviceName = "LaserBoxx source 3";
const char* g_OxxiusLaserBoxx4DeviceName = "LaserBoxx source 4";
const char* g_OxxiusLaserBoxx5DeviceName = "LaserBoxx source 5";
const char* g_OxxiusLaserBoxx6DeviceName = "LaserBoxx source 6";
const char* g_OxxiusShutterDeviceName = "Shutter";
const char* g_OxxiusShutter1DeviceName = "Shutter 1";
const char* g_OxxiusShutter2DeviceName = "Shutter 2";
const char* g_OxxiusMDualDeviceName = "MDual";

const char* g_slotPrefix[7] = {"","L1 ","L2 ","L3 ","L4 ","L5 ","L6 "};

OnBoardHW sixSourceCombiner(MAX_NUMBER_OF_SLOTS);

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
	RegisterDevice(g_OxxiusCombinerDeviceName, MM::HubDevice, "Oxxius laser combiner controlled through serial interface");
	RegisterDevice(g_OxxiusLaserBoxx1DeviceName, MM::GenericDevice, "LaserBoxx on slot 1");
	RegisterDevice(g_OxxiusLaserBoxx2DeviceName, MM::GenericDevice, "LaserBoxx on slot 2");
	RegisterDevice(g_OxxiusLaserBoxx3DeviceName, MM::GenericDevice, "LaserBoxx on slot 3");
	RegisterDevice(g_OxxiusLaserBoxx4DeviceName, MM::GenericDevice, "LaserBoxx on slot 4");
	RegisterDevice(g_OxxiusLaserBoxx5DeviceName, MM::GenericDevice, "LaserBoxx on slot 5");
	RegisterDevice(g_OxxiusLaserBoxx6DeviceName, MM::GenericDevice, "LaserBoxx on slot 6");
	RegisterDevice(g_OxxiusShutter1DeviceName, MM::ShutterDevice, "E-m shutter on channel 1");
	RegisterDevice(g_OxxiusShutter2DeviceName, MM::ShutterDevice, "E-m shutter on channel 2");
	RegisterDevice(g_OxxiusMDualDeviceName, MM::StateDevice, "M-Dual splitter");
}

MODULE_API MM::Device* CreateDevice(const char* deviceNameChar)
{
	if (deviceNameChar == 0)
		return 0;

	std::string deviceNameAndSlot = string(deviceNameChar);

	if (strcmp(deviceNameChar,g_OxxiusCombinerDeviceName) == 0) {
		return new OxxiusCombinerHub();
	} else if ( deviceNameAndSlot.compare(0, strlen(g_OxxiusLaserBoxxDeviceName), g_OxxiusLaserBoxxDeviceName) == 0 ) {
		return new OxxiusLaserBoxx(deviceNameChar);
	} else if ( deviceNameAndSlot.compare(0, strlen(g_OxxiusShutterDeviceName), g_OxxiusShutterDeviceName) == 0 ) {
		return new OxxiusShutter(deviceNameChar);
	} else if ( deviceNameAndSlot.compare(0, strlen(g_OxxiusMDualDeviceName), g_OxxiusMDualDeviceName) == 0 ) {
		return new OxxiusMDual(deviceNameChar);
	}
	return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
//
// Oxxius combiner implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
//
///////////////////////////////////////////////////////////////////////////////

OxxiusCombinerHub::OxxiusCombinerHub() : initialized_(false)
{
	// Initializing private variables
	serialNumber_ = "";
	installedDevices_ = 0;
	serialAnswer_ = "";
	interlockClosed_ = false;
	keyActivated_ = false;
	
    InitializeDefaultErrorMessages();
	SetErrorText(ERR_COMBINER_NOT_FOUND, "Hub Device not found.  The peer device is expected to be a Oxxius combiner");

	
	// Create pre-initialization properties
	// ------------------------------------

	// Communication port
	CPropertyAction* pAct = new CPropertyAction(this, &OxxiusCombinerHub::OnPort);
	CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);
}

OxxiusCombinerHub::~OxxiusCombinerHub()
{
	Shutdown();
}

void OxxiusCombinerHub::GetName(char* name) const
{
    CDeviceUtils::CopyLimitedString(name, g_OxxiusCombinerDeviceName);
}

bool OxxiusCombinerHub::Busy()
{
   return false;
}


int OxxiusCombinerHub::Initialize()
{
   if(!initialized_)   { 

	   // Set proprety list
		// - - - - - - - - -

		// Name and description of the combiner:
		RETURN_ON_MM_ERROR( CreateProperty(MM::g_Keyword_Name, g_OxxiusCombinerDeviceName, MM::String, true) );
		RETURN_ON_MM_ERROR( CreateProperty(MM::g_Keyword_Description, "Oxxius L6Cc/L4Cc combiner", MM::String, true) );

		// Serial number of the combiner:
		CPropertyAction* pAct = new CPropertyAction (this, &OxxiusCombinerHub::OnSerialNumber);
		RETURN_ON_MM_ERROR( CreateProperty(MM::g_Keyword_HubID, serialNumber_.c_str(), MM::String, true, pAct) );

		// Interlock circuit:
		pAct = new CPropertyAction (this, &OxxiusCombinerHub::OnInterlock);
		RETURN_ON_MM_ERROR( CreateProperty("Interlock circuit", "", MM::String, true, pAct) );

		// Emission key:
		pAct = new CPropertyAction (this, &OxxiusCombinerHub::OnEmissionKey);
		RETURN_ON_MM_ERROR( CreateProperty("EmissionKey", "", MM::String, true, pAct) );

		if (!IsCallbackRegistered())
			return DEVICE_NO_CALLBACK_REGISTERED;
		
		RETURN_ON_MM_ERROR( UpdateStatus() );

		initialized_ = true;

		// RETURN_ON_MM_ERROR( DetectInstalledDevices() );
	}
	return DEVICE_OK;
}



int OxxiusCombinerHub::DetectInstalledDevices()
{
	if (initialized_) {

		// Enumerates the installed AOMs and their position
		bool AOM1en = false, AOM2en = false;
		unsigned int AOM1pos = 0, AOM2pos = 0;
		
//		RETURN_ON_MM_ERROR( QueryCommand(this, GetCoreCallback(), NO_SLOT, "hz 9876") );

		RETURN_ON_MM_ERROR( QueryCommand(this, GetCoreCallback(), NO_SLOT, "AOM1 EN") );
		ParseforBoolean(AOM1en);
		RETURN_ON_MM_ERROR( QueryCommand(this, GetCoreCallback(), NO_SLOT, "AOM2 EN") );
		ParseforBoolean(AOM2en);

		if (AOM1en) {
			RETURN_ON_MM_ERROR( QueryCommand(this, GetCoreCallback(), NO_SLOT, "AOM1 PO") );
			ParseforInteger(AOM1pos);
		}
		if (AOM2en) {
			RETURN_ON_MM_ERROR( QueryCommand(this, GetCoreCallback(), NO_SLOT, "AOM2 PO") );
			ParseforInteger(AOM2pos);
		}
		
//		RETURN_ON_MM_ERROR( QueryCommand(this, GetCoreCallback(), NO_SLOT, "hz 0") );

		// A position equal to "0" stands for an absence of modulator
		sixSourceCombiner.SetAOMPos(AOM1pos,AOM2pos);

		// Enumerates the lasers (or devices) present on the combiner
		unsigned int masque = 1;
		unsigned int repartition = 0;
		
		RETURN_ON_MM_ERROR( QueryCommand(this, GetCoreCallback(), NO_SLOT, "?CL") );
		ParseforInteger(repartition);

		for(unsigned int querySlot=1; querySlot<=MAX_NUMBER_OF_SLOTS; querySlot++)	{

			if ((repartition & masque) != 0)  {

				// A laser source is listed, now querying for detailed information (model, etc)
				std::string detailedInfo, serialNumber;

				RETURN_ON_MM_ERROR( QueryCommand(this, GetCoreCallback(), querySlot, "INF?") );
				ParseforString(detailedInfo);
				sixSourceCombiner.SetType(querySlot, detailedInfo.c_str());

				RETURN_ON_MM_ERROR( QueryCommand(this, GetCoreCallback(), querySlot, "HID?") );
				ParseforString(serialNumber);
				sixSourceCombiner.SetSerialNumber(querySlot, serialNumber.c_str());
			}

			masque <<= 1;		// Left-shift the bit mask and repeat
		}

		// Creating Devices for the laser sources detected:
		for(unsigned int s=1; s<=MAX_NUMBER_OF_SLOTS; s++)	{
			if( sixSourceCombiner.GetType(s) != 0) {
				// If a laser is polled, then a corresponding Adapter Devive is created
				std::ostringstream nameSlotModel;
				nameSlotModel << g_OxxiusLaserBoxxDeviceName << " " << s;

				MM::Device* pDev = ::CreateDevice(nameSlotModel.str().c_str());
				if (pDev) {
					AddInstalledDevice(pDev);
					installedDevices_++;
				}
			}
		}

		// Creating Devices for the two electro-mechanical shutters:
		for(unsigned int channel=1; channel<=2; channel++)	{
			std::ostringstream nameModelChannel;
			nameModelChannel << g_OxxiusShutterDeviceName << " " << channel;

			MM::Device* pDev = ::CreateDevice(nameModelChannel.str().c_str());
			if (pDev) {
				AddInstalledDevice(pDev);
				installedDevices_++;
			}
		}

		// Creating Devices for the "Flip mirror" or MDUAL modules:
		unsigned int FM1type = 0, FM2type = 0;

		RETURN_ON_MM_ERROR( QueryCommand(this, GetCoreCallback(), NO_SLOT, "FM1C") );
		ParseforInteger(FM1type);
		RETURN_ON_MM_ERROR( QueryCommand(this, GetCoreCallback(), NO_SLOT, "FM2C") );
		ParseforInteger(FM2type);

		switch (FM1type) {
			case 4:	{		// MDual detected
				MM::Device* pDev = ::CreateDevice(g_OxxiusMDualDeviceName);
				if (pDev) {
					AddInstalledDevice(pDev);
					installedDevices_++;
				}
				break;
			}
			case 0:
			default:
				// nop
				break;
		}
	}

	return DEVICE_OK;
}


int OxxiusCombinerHub::Shutdown()
{
   initialized_ = false;
   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int OxxiusCombinerHub::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		pProp->Set(port_.c_str()); 
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(port_);
		pProp->Set(port_.c_str());
	}
	return DEVICE_OK;
}


int OxxiusCombinerHub::OnSerialNumber(MM::PropertyBase* pProp, MM::ActionType pAct)
{
	if (pAct == MM::BeforeGet) {
		QueryCommand(this, GetCoreCallback(), NO_SLOT, "HID?");
		ParseforString(serialNumber_);
		pProp->Set(serialNumber_.c_str());
	}
     
	return DEVICE_OK;
}


int OxxiusCombinerHub::OnInterlock(MM::PropertyBase* pProp, MM::ActionType pAct)
{
	if (pAct == MM::BeforeGet) {
		QueryCommand(this, GetCoreCallback(), NO_SLOT, "INT?");
		ParseforBoolean(interlockClosed_);

		if( interlockClosed_) {
			pProp->Set("Closed");
		} else {
			pProp->Set("Open");
		}
	}
     
	return DEVICE_OK;
}


int OxxiusCombinerHub::OnEmissionKey(MM::PropertyBase* pProp, MM::ActionType pAct)
{
	if (pAct == MM::BeforeGet) {
		QueryCommand(this, GetCoreCallback(), NO_SLOT, "KEY?");
		ParseforBoolean(keyActivated_);

		if( keyActivated_) {
			pProp->Set("Armed");
		} else {
			pProp->Set("Disarmed");
		}
	}
     
	return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// Generic methods
///////////////////////////////////////////////////////////////////////////////

void OxxiusCombinerHub::LogError(int id, MM::Device* device, MM::Core* core, const char* functionName)
{
   std::ostringstream os;
   char deviceName[MM::MaxStrLength];
   device->GetName(deviceName);
   os << "Error " << id << ", " << deviceName << ", " << functionName << endl;
   core->LogMessage(device, os.str().c_str(), false);
}


/**
 * Sends a serial command to a given slot, then stores the result in the receive buffer.
 */
int OxxiusCombinerHub::QueryCommand(MM::Device* device, MM::Core* core, const unsigned int destinationSlot, const char* command)
{
	// First check: if the command string is empty, do nothing and return "DEVICE_OK"
	if( strcmp(command, "") == 0) return DEVICE_OK;

	// Compose the command to be sent to the combiner
	std::string strCommand;
	strCommand.assign(g_slotPrefix[destinationSlot]);
	strCommand.append(command);

	/*	
	std::ostringstream InfoMessage;
	InfoMessage << "Now sending command :";
	InfoMessage << string(strCommand.c_str());
	LogError(DEVICE_OK, device, core, InfoMessage.str().c_str());
	*/
	
	// Send command through the serial interface
	int ret = core->SetSerialCommand(device, port_.c_str(), strCommand.c_str(), "\r\n");
	if (ret != DEVICE_OK) {
		LogError(ret, device, core, "QueryCommand-SetSerialCommand");
		return ret;
	}
  
	// Get a response
	char rcvBuf_[RCV_BUF_LENGTH];
	ret = core->GetSerialAnswer(device, port_.c_str(), RCV_BUF_LENGTH, rcvBuf_, "\r\n");
	
	if (ret != DEVICE_OK) {
		LogError(ret, device, core, "QueryCommand-GetSerialAnswer");

		// Keep on trying until we either get our answer, or 5 seconds have passed
		int maxTimeMs = 5000;
		// Wait for a (increasing) delay between each try
		int delayMs = 10;
		// Keep track of how often we tried
		int counter = 1;
		bool done = false;
		MM::MMTime startTime (core->GetCurrentMMTime()); // Let's keep in mind that MMTime is counted in microseconds
		
		while (!done) {
			counter++;
			ret = core->GetSerialAnswer(device, port_.c_str(), RCV_BUF_LENGTH, rcvBuf_, "\r\n");
			if ( (ret == DEVICE_OK) ||  ( (core->GetCurrentMMTime() - startTime) > (maxTimeMs*1000.0) ) )
				done = true;
			else {
				CDeviceUtils::SleepMs(delayMs);
				delayMs *= 2;
			}
		}
		ostringstream os;
		if (ret == DEVICE_OK)
			os << "QueryCommand-GetSerialAnswer: Succeeded reading from serial port after trying " << counter << "times.";
		else
			os << "QueryCommand-GetSerialAnswer: Failed reading from serial port after trying " << counter << "times.";

		core->LogMessage(device, os.str().c_str(), true);

		serialAnswer_.assign(rcvBuf_);
		return ret;
	}
	serialAnswer_.assign(rcvBuf_);

	// Checking that the query has been acknowledged
	// The combiner's answer to unkonwn queries is "????"
	const char *queryErrorMsg="????";
	const char *queryTimeoutMsg="timeout";

	if( strcmp(rcvBuf_, queryErrorMsg) == 0)	{
		std::ostringstream syntaxErrorMessage;
		syntaxErrorMessage << "Syntax error received against sent command '";
		syntaxErrorMessage << string(strCommand.c_str());
		syntaxErrorMessage << "'";

		LogError(DEVICE_UNSUPPORTED_COMMAND, device, core, syntaxErrorMessage.str().c_str());
		// return DEVICE_UNSUPPORTED_COMMAND;
		return DEVICE_OK;
	}

		if( strcmp(rcvBuf_, queryTimeoutMsg) == 0)	{
		std::ostringstream syntaxErrorMessage;
		syntaxErrorMessage << "Time out received against sent command '";
		syntaxErrorMessage << string(strCommand.c_str());
		syntaxErrorMessage << "'";

		LogError(DEVICE_SERIAL_TIMEOUT, device, core, syntaxErrorMessage.str().c_str());
		return DEVICE_SERIAL_TIMEOUT;
	}
	return DEVICE_OK;
}


int OxxiusCombinerHub::ParseforBoolean(bool &Bval)
{
	unsigned int intAnswer = (unsigned int)atoi(serialAnswer_.c_str());
	Bval = (intAnswer == 1);

	serialAnswer_.clear();

	return DEVICE_OK;
}


int OxxiusCombinerHub::ParseforDouble(double &Dval)
{
	Dval = (float) atof(serialAnswer_.c_str());

	serialAnswer_.clear();

	return DEVICE_OK;
}


int OxxiusCombinerHub::ParseforInteger(unsigned int &Ival)
{
	Ival = (unsigned int)atoi(serialAnswer_.c_str());

	serialAnswer_.clear();

	return DEVICE_OK;
}


int OxxiusCombinerHub::ParseforString(std::string &Sval)
{
	Sval.assign(serialAnswer_);

	serialAnswer_.clear();

	return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
//
// Oxxius generic LaserBoxx implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
//
///////////////////////////////////////////////////////////////////////////////

OxxiusLaserBoxx::OxxiusLaserBoxx(const char* nameAndSlot) : initialized_(false)
{
	name_.assign(nameAndSlot);

	std::string strSlot = string(nameAndSlot);
	strSlot = strSlot.substr(strSlot.length()-1, 1);
	slot_ = (unsigned int) atoi(strSlot.c_str());

	std::div_t dv;
	dv = std::div(sixSourceCombiner.GetType(slot_), 10);
	model_[0] = dv.quot;
	model_[1] = dv.rem;

	parentHub_ = 0;
	busy_ = false;
	laserOn_ = false;
	alarm_ = "";
	state_ = "";
	digitalMod_ = "";
	analogMod_ = "";
	controlMode_ = "";

	powerSetPoint_ = 0.0;
	currentSetPoint_ = 0.0;
	maxPower_ = 0;

	InitializeDefaultErrorMessages();
	SetErrorText(ERR_NO_PORT_SET, "Hub Device not found.  The Laser combiner is needed to create this device");

	// parent ID display
	CreateHubIDProperty();
}


OxxiusLaserBoxx::~OxxiusLaserBoxx()
{
     Shutdown();
}


void OxxiusLaserBoxx::GetName(char* Name) const
{
     CDeviceUtils::CopyLimitedString(Name, name_.c_str());
}


int OxxiusLaserBoxx::Initialize()
{
	if (!initialized_) {
		parentHub_ = static_cast<OxxiusCombinerHub*>(GetParentHub());
		if (!parentHub_ ) {
			return DEVICE_COMM_HUB_MISSING;
		}
		char hubLabel[MM::MaxStrLength];
		parentHub_->GetLabel(hubLabel);
		SetParentID(hubLabel); // for backward compatibility

		/*
		std::ostringstream debugMessage1;
		debugMessage1 << "Name: ";
		debugMessage1 << name_;
		GetCoreCallback()->LogMessage(this, debugMessage1.str().c_str(), false);

		std::ostringstream debugMessage2;
		debugMessage2 << "Slot: ";
		debugMessage2 << slot_;
		GetCoreCallback()->LogMessage(this, debugMessage2.str().c_str(), false);

		std::ostringstream debugMessage3;
		debugMessage3 << " Model " << model_[0] << model_[1];
		GetCoreCallback()->LogMessage(this, debugMessage3.str().c_str(), false);
		*/

		// Set property list
		// -----------------
		// Name (read only)
		RETURN_ON_MM_ERROR( CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true) );

		// Description (read only)
		std::ostringstream descriPt1;
		char sourceSerialNumber[] = "LAS-XXXXXXXXXX";

		sixSourceCombiner.GetSerialNumber(slot_, sourceSerialNumber);

		switch (model_[0]) {
			case 1:		// LBX model
				descriPt1 << "LBX";
				break;
			case 2:		// LCX model
				descriPt1 << "LCX";
				break;
			default:		// Should not happen
				descriPt1 << "Unknown";
				break;
		}
		descriPt1 << " source on slot " << slot_;
		descriPt1 << ", " << sourceSerialNumber;

		RETURN_ON_MM_ERROR( CreateProperty(MM::g_Keyword_Description, descriPt1.str().c_str(), MM::String, true) );

		// Alarm (read only)
		CPropertyAction* pAct = new CPropertyAction (this, &OxxiusLaserBoxx::OnAlarm);
		RETURN_ON_MM_ERROR( CreateProperty("Alarm", "None", MM::String, true, pAct) );

		// Status (read only)
		pAct = new CPropertyAction (this, &OxxiusLaserBoxx::OnState);
		RETURN_ON_MM_ERROR( CreateProperty("State", "", MM::String, true, pAct) );

		// Emission selector (write/read)
		pAct = new CPropertyAction (this, &OxxiusLaserBoxx::OnEmissionOnOff);
		RETURN_ON_MM_ERROR( CreateProperty("Emission", "", MM::String, false, pAct) );
		AddAllowedValue("Emission", "ON");
		AddAllowedValue("Emission", "OFF");

		// Digital modulation selector (write/read)
		pAct = new CPropertyAction (this, &OxxiusLaserBoxx::OnDigitalMod);
		RETURN_ON_MM_ERROR( CreateProperty("Digital Modulation", "", MM::String, false, pAct) );
		AddAllowedValue("Digital Modulation", "ON");
		AddAllowedValue("Digital Modulation", "OFF");

		// Analog modulation selector (write/read)
		pAct = new CPropertyAction (this, &OxxiusLaserBoxx::OnAnalogMod);
		RETURN_ON_MM_ERROR( CreateProperty("Analog Modulation", "", MM::String, false, pAct) );
		AddAllowedValue("Analog Modulation", "ON");
		AddAllowedValue("Analog Modulation", "OFF");

		// Control mode selector (= APC or ACC) (write/read)
		pAct = new CPropertyAction (this, &OxxiusLaserBoxx::OnControlMode);
		RETURN_ON_MM_ERROR( CreateProperty("Control mode", "", MM::String, false, pAct) );
		AddAllowedValue("Control mode", "ACC");
		AddAllowedValue("Control mode", "APC");

		// Power set point (write/read)
		pAct = new CPropertyAction (this, &OxxiusLaserBoxx::OnPowerSetPoint);
		RETURN_ON_MM_ERROR( CreateProperty("Power set point", "0", MM::Float, false, pAct) );
		sixSourceCombiner.GetNominalPower(slot_, maxPower_);// Not exactly a max power -> To be improved
		SetPropertyLimits("Power set point", 0, (float) maxPower_);	

		RETURN_ON_MM_ERROR( UpdateStatus() );
 
		initialized_ = true;
	}

	return DEVICE_OK;
}



int OxxiusLaserBoxx::Shutdown()
{
	initialized_ = false;
	return DEVICE_OK;
}


bool OxxiusLaserBoxx::Busy()
{
   return busy_;
}



///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int OxxiusLaserBoxx::OnAlarm(MM::PropertyBase* pProp, MM::ActionType)
{
	unsigned int alarmInt = 99;
	RETURN_ON_MM_ERROR( parentHub_->QueryCommand(this, GetCoreCallback(), slot_, "?F") );

	parentHub_->ParseforInteger(alarmInt);

	switch (alarmInt) {
		case 0:
			alarm_ = "No Alarm";
			break;
		case 1:
			alarm_ = "Out-of-bounds diode current";
			break;
		case 2:
			alarm_ = "Unexpected laser power value";
			break;
		case 3:
			alarm_ = "Out-of-bounds supply voltage";
			break;
		case 4:
			alarm_ = "Out-of-bounds internal temperature";
			break;
		case 5:
			alarm_ = "Out-of-bounds baseplate temperature";
			break;
		case 7:
			alarm_ = "Interlock circuit open";
			break;
		case 8:
			alarm_ = "Soft reset";
			break;
		default:
			alarm_ = "Other alarm";
	}

	pProp->Set(alarm_.c_str());

	return DEVICE_OK;
}


int OxxiusLaserBoxx::OnState(MM::PropertyBase* pProp, MM::ActionType)
{
	unsigned int stateInt = 99;
	RETURN_ON_MM_ERROR( parentHub_->QueryCommand(this, GetCoreCallback(), slot_, "?STA") );

	parentHub_->ParseforInteger(stateInt);

	switch (stateInt) {
		case 1:
			state_ = "Warm-up phase";
			break;
		case 2:
			state_ = "Stand-by state";
			break;
		case 3:
			state_ = "Emission on";
			break;
		case 4:
			state_ = "Internal error";
			break;
		case 5:
			state_ = "Alarm";
			break;
		case 6:
			state_ = "Sleep state";
			break;
		default:
			state_ = "Other state";
	}

	pProp->Set(alarm_.c_str());

	return DEVICE_OK;
}


int OxxiusLaserBoxx::OnEmissionOnOff(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		unsigned int status = 0;
		std::ostringstream query;

		query << "?CS " << slot_;

		RETURN_ON_MM_ERROR( parentHub_->QueryCommand(this, GetCoreCallback(), NO_SLOT, query.str().c_str()) );
		parentHub_->ParseforInteger(status);

		switch (status) {
			case 0:		// LBX model: Emission off
			case 2:		// LCX model: shutter closed
				laserOn_ = false;
				break;
			case 1:		// LBX model: Emission on
			case 3:		// LCX model: shutter open
				laserOn_ = true;
				break;
			default:
				laserOn_ = true;
		}

		if (laserOn_) {
			pProp->Set("ON");
		} else {
			pProp->Set("OFF");
		}
	}
	else if (eAct == MM::AfterSet) {
		std::string newEmissionStatus, newCommand = "";

		pProp->Get(newEmissionStatus);

		if( newEmissionStatus.compare("ON") == 0 ) {
			newCommand = "DL 1";
			laserOn_ = true;
		} else if ( newEmissionStatus.compare("OFF") == 0 ) {
			newCommand = "DL 0";
			laserOn_ = false;
		}

		RETURN_ON_MM_ERROR( parentHub_->QueryCommand(this, GetCoreCallback(), slot_, newCommand.c_str()) );
	}
	return DEVICE_OK;
}


int OxxiusLaserBoxx::OnDigitalMod(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	unsigned int querySlot = NO_SLOT;

	if (eAct == MM::BeforeGet) {
		std::string query;

		// The slot and command depend on the model
		switch (model_[0]) {
			case 1:		// LBX model: retreiving the modulation status 
				querySlot = slot_;
				query = "?TTL";
				break;
			case 2:		// LCX model: retreiving the modulation status 
				querySlot = NO_SLOT;
				switch (model_[1]) {
					case 1:
						query = "AOM1 TTL";
						break;
					case 2:
						query = "AOM2 TTL";
						break;
					default:
						query = "";
				}
				break;
			default:		// Should not happen
				return DEVICE_OK;
		}

		RETURN_ON_MM_ERROR( parentHub_->QueryCommand(this, GetCoreCallback(), querySlot, query.c_str()) );
		
		bool digiM;
		parentHub_->ParseforBoolean(digiM);

		if (digiM)
			digitalMod_.assign("ON");
		else
			digitalMod_.assign("OFF");
		
		pProp->Set(digitalMod_.c_str());

	} else if (eAct == MM::AfterSet) {
		std::string newModSet, newCommand;

		pProp->Get(newModSet);
		digitalMod_.assign(newModSet);

		if (digitalMod_ == "ON") {
			switch (model_[0]) {
				case 1:		// LBX model: setting the modulation on 
					querySlot = slot_;
					newCommand.assign("TTL 1");
					break;
				case 2:		// LCX model: setting the modulation on
					querySlot = NO_SLOT;

					switch (model_[1]) {
					case 1:
						newCommand.assign("AOM1 TTL 1");
						break;
					case 2:
						newCommand.assign("AOM2 TTL 1");
						break;
					default:
						newCommand.assign("");
					}
					break;
				default:		// Should not happen
					return DEVICE_OK;
			}
			
		} else if (digitalMod_ == "OFF") {
			switch (model_[0]) {
				case 1:		// LBX model: setting the modulation off
					querySlot = slot_;
					newCommand.assign("TTL 0");
					break;
				case 2:		// LCX model: setting the modulation off
					querySlot = NO_SLOT;
					switch (model_[1]) {
					case 1:
						newCommand.assign("AOM1 TTL 0");
						break;
					case 2:
						newCommand.assign("AOM2 TTL 0");
						break;
					default:
						newCommand.assign("");
					}
					break;
				default:		// Should not happen
					return DEVICE_OK;
			}
		}

		RETURN_ON_MM_ERROR( parentHub_->QueryCommand(this, GetCoreCallback(), querySlot, newCommand.c_str()) );
	}
	return DEVICE_OK;
}


int OxxiusLaserBoxx::OnAnalogMod(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	unsigned int querySlot = NO_SLOT;

	if (eAct == MM::BeforeGet) {
		std::string query;

		// The slot and command depend on the model
		switch (model_[0]) {
			case 1:		// LBX model: retreiving the modulation state
				querySlot = slot_;
				query = "?AM";
				break;
			case 2:		// LCX model: retreiving the modulation state 
				querySlot = NO_SLOT;
				switch (model_[1]) {
					case 1:
						query = "AOM1 AM";
						break;
					case 2:
						query = "AOM2 AM";
						break;
					default:
						query = "";
				}
				break;
			default:		// Should not happen
				return DEVICE_OK;
		}

		RETURN_ON_MM_ERROR( parentHub_->QueryCommand(this, GetCoreCallback(), querySlot, query.c_str()) );
		bool digiM;
		parentHub_->ParseforBoolean(digiM);

		if (digiM) {
			digitalMod_.assign("ON");
		} else {
			digitalMod_.assign("OFF");
		}
		pProp->Set(digitalMod_.c_str());

	} else if (eAct == MM::AfterSet) {
		std::ostringstream newCommand;



		switch (model_[0]) {
			case 1:		// LBX model: setting the modulation on 
				querySlot = slot_;
				newCommand << "AM ";
				break;
			case 2:		// LCX model: setting the modulation on
				querySlot = NO_SLOT;
				switch (model_[1]) {
					case 1:
					case 2:
						newCommand << "AOM" << model_[1] << " AM ";
						break;
					default:	// No modulation without a modulator
						return DEVICE_OK;
				}
				break;
			default:		// Should not happen
				return DEVICE_OK;
		}

		pProp->Get(digitalMod_);
		
		if (digitalMod_ == "OFF")
			newCommand << "0";
		else
			newCommand << "1";

		RETURN_ON_MM_ERROR( parentHub_->QueryCommand(this, GetCoreCallback(), querySlot, newCommand.str().c_str()) );
	}
	return DEVICE_OK;
}


int OxxiusLaserBoxx::OnControlMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		unsigned int ctrlM = 1;
		std::string command;

		// The slot and command depend on the model
		switch (model_[0]) {
			case 1:		// LBX model
				command = "?APC";

				RETURN_ON_MM_ERROR( parentHub_->QueryCommand(this, GetCoreCallback(), slot_, command.c_str()) );
				parentHub_->ParseforInteger(ctrlM);

				break;

			case 2:		// LCX model -> always in APC
				ctrlM = 1;
				break;

			default:;		// Should not happen
		}

		if (ctrlM == 1) {
			controlMode_.assign("APC");
		} else if (ctrlM == 0) {
			controlMode_.assign("ACC");
		}

		pProp->Set(controlMode_.c_str());
	}
	else if (eAct == MM::AfterSet) {
		std::string newCommand;

		pProp->Get(controlMode_);

		switch (model_[0]) {
			case 1:		// LBX model
				if (controlMode_ == "ACC") {
					newCommand.assign("APC 0");
				} else if (controlMode_ == "APC") {
					newCommand.assign("APC 1");
				}
				
				RETURN_ON_MM_ERROR( parentHub_->QueryCommand(this, GetCoreCallback(), slot_, newCommand.c_str()) );

				break;

			case 2:		// LCX model -> always in APC
				controlMode_ = "APC";
				pProp->Set(controlMode_.c_str());
				break;

			default:;		// Should not happen
		}
	}
	return DEVICE_OK;
}


int OxxiusLaserBoxx::OnPowerSetPoint(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		std::string command = "?SP";
		unsigned int thisSlot = slot_;

		if (model_[0] == 2) {
			switch (model_[1]) {
				case 1:		// LCX on AOM1
					thisSlot = NO_SLOT;
					command = "?SP1";
					break;
				case 2:		// LCX on AOM2
					thisSlot = NO_SLOT;
					command = "?SP2";
					break;
				default:	// LCX without AOM: identical to LBX polling
					break;
			}
		}

		RETURN_ON_MM_ERROR( parentHub_->QueryCommand(this, GetCoreCallback(), thisSlot, command.c_str()) );
		parentHub_->ParseforDouble(powerSetPoint_);

		pProp->Set( (double)powerSetPoint_ );
	}
	else if (eAct == MM::AfterSet) {
		std::string command = "P";
		unsigned int thisSlot = slot_;
		
		double temporarySetPoint = 0.0;
		pProp->Get(temporarySetPoint);

		if( (temporarySetPoint >= 0.0)||(temporarySetPoint <= (double) maxPower_) ) {
			powerSetPoint_ = temporarySetPoint;

			std::ostringstream newCommand;
			char * powerSPString = new char[20];
			strcpy(powerSPString , CDeviceUtils::ConvertToString(powerSetPoint_) );

			if (model_[0] == 2) {
				switch (model_[1]) {
					case 1:		// LCX on AOM1
						thisSlot = NO_SLOT;
						command = "P";
						break;
					case 2:		// LCX on AOM2
						thisSlot = NO_SLOT;
						command = "P2";
						break;
					default:	// LCX without AOM: identical to LBX polling
						break;
				}
			}

			newCommand << command << " " << powerSPString;
			RETURN_ON_MM_ERROR( parentHub_->QueryCommand(this, GetCoreCallback(), thisSlot, newCommand.str().c_str()) );
		} else {
			pProp->Set(powerSetPoint_);
		}
	}
	return DEVICE_OK;
}


/*
int OxxiusLaserBoxx::OnCurrentSetPoint(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		unsigned int currentSP = 0;
		std::string command = "?SP";

		RETURN_ON_MM_ERROR( parentHub_->QueryCommand(this, GetCoreCallback(), slot_, command.c_str()) );
		parentHub_->ParseforInteger(powerSP);

/// A CHANGER
		powerSetPoint_ = (float)(powerSP/100);
		pProp->Set(powerSetPoint_);
	}
	else if (eAct == MM::AfterSet) {
		std::string newCommand = "P ", newSetPoint = "0";

		pProp->Get(powerSetPoint_);
		newSetPoint = to_string((int) (powerSetPoint_*100));
		newCommand.append(newSetPoint);

		RETURN_ON_MM_ERROR( parentHub_->QueryCommand(this, GetCoreCallback(), slot_, newCommand.c_str()) );
	}
	return DEVICE_OK;
}
*/

///////////////////////////////////////////////////////////////////////////////
//
// Oxxius shutter implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
//
///////////////////////////////////////////////////////////////////////////////

OxxiusShutter::OxxiusShutter(const char* nameAndSlot) : initialized_(false)
{
	isOpen_ = false;
	parentHub_ = 0;

	name_.assign(nameAndSlot);

	std::string strChnl = string(nameAndSlot);
	strChnl = strChnl.substr(strChnl.length()-1, 1);
	channel_ = (unsigned int) atoi(strChnl.c_str());

	// Set property list
	// -----------------
	// Name (read only)
	CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);

	std::ostringstream shutterDesc;
	shutterDesc << "Electro-mechanical shutter on channel " << channel_ << ".";
	CreateProperty(MM::g_Keyword_Description, shutterDesc.str().c_str(), MM::String, true);

	InitializeDefaultErrorMessages();
	SetErrorText(ERR_NO_PORT_SET, "Hub Device not found.  The Laser combiner is needed to create this device");

	// parent ID display
	CreateHubIDProperty();
}


OxxiusShutter::~OxxiusShutter()
{
	Shutdown();
}


int OxxiusShutter::Initialize()
{
	if (!initialized_) {
		parentHub_ = static_cast<OxxiusCombinerHub*>(GetParentHub());
		if (!parentHub_ ) {
			return DEVICE_COMM_HUB_MISSING;
		}
		char hubLabel[MM::MaxStrLength];
		parentHub_->GetLabel(hubLabel);
		SetParentID(hubLabel); // for backward compatibility

		// Set property list
		// -----------------

		// Open/Close selector (write/read)
		CPropertyAction* pAct = new CPropertyAction (this, &OxxiusShutter::OnState);
		RETURN_ON_MM_ERROR( CreateProperty(MM::g_Keyword_State, "", MM::String, false, pAct) );
		AddAllowedValue(MM::g_Keyword_State, "Open");
		AddAllowedValue(MM::g_Keyword_State, "Closed");

		// Closing the shutter on Initialization
		RETURN_ON_MM_ERROR( SetProperty(MM::g_Keyword_State, "Closed") );

		RETURN_ON_MM_ERROR( UpdateStatus() );

		initialized_ = true;
	}

	return DEVICE_OK;		
}


int OxxiusShutter::Shutdown()
{
	initialized_ = false;
	return DEVICE_OK;
}


void OxxiusShutter::GetName(char* Name) const
{
     CDeviceUtils::CopyLimitedString(Name, name_.c_str());
}


bool OxxiusShutter::Busy()
{
   return false;
}


int OxxiusShutter::SetOpen(bool openCommand)
{
	if (openCommand)
		return SetProperty(MM::g_Keyword_State, "Open");
	else
		return SetProperty(MM::g_Keyword_State, "Closed");
}


int OxxiusShutter::GetOpen(bool& isOpen)
{
	isOpen = isOpen_;
	return DEVICE_OK;
}


int OxxiusShutter::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		if (isOpen_) {
			pProp->Set("Open");
		} else {
			pProp->Set("Closed");
		}
	}
	else if (eAct == MM::AfterSet) {

		std::string newState = "";
		pProp->Get(newState);

		std::ostringstream newCommand;
		newCommand << "SH" << channel_ << " ";

		if( newState.compare("Open") == 0 ) {
			newCommand << "1";
			isOpen_ = true;
		} else if ( newState.compare("Closed") == 0 ) {
			newCommand << "0";
			isOpen_ = false;
		}

		RETURN_ON_MM_ERROR( parentHub_->QueryCommand(this, GetCoreCallback(), NO_SLOT, newCommand.str().c_str()) );
	}
	return DEVICE_OK;
}



///////////////////////////////////////////////////////////////////////////////
//
// Oxxius M-Dual implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
//
///////////////////////////////////////////////////////////////////////////////

OxxiusMDual::OxxiusMDual(const char* nameAndSlot) : initialized_(false)
{
	numPos_ = MDUAL_POSITIONS;
	parentHub_ = 0;
	core_ = GetCoreCallback();

	name_.assign(nameAndSlot);

	// Set property list
	// -----------------
	// Name (read only)
	CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);

	CreateProperty(MM::g_Keyword_Description, "M-Dual module", MM::String, true);

	InitializeDefaultErrorMessages();
	SetErrorText(ERR_NO_PORT_SET, "Hub Device not found.  The Laser combiner is needed to create this device");

	// parent ID display
	CreateHubIDProperty();
}


OxxiusMDual::~OxxiusMDual()
{
	Shutdown();
}


int OxxiusMDual::Initialize()
{
	if (!initialized_) {
		parentHub_ = static_cast<OxxiusCombinerHub*>(GetParentHub());
		if (!parentHub_ ) {
			return DEVICE_COMM_HUB_MISSING;
		}
		char hubLabel[MM::MaxStrLength];
		parentHub_->GetLabel(hubLabel);
		SetParentID(hubLabel); // for backward compatibility

		// Set property list
		// -----------------
		// State
		CPropertyAction* pAct = new CPropertyAction (this, &OxxiusMDual::OnState);
		RETURN_ON_MM_ERROR( CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct) );

		char pos[3];
		for (unsigned int i=0; i<numPos_; i++) {
			sprintf(pos, "%d", i);
			AddAllowedValue(MM::g_Keyword_State, pos);
		}

		// Label
		pAct = new CPropertyAction (this, &CStateBase::OnLabel);
		RETURN_ON_MM_ERROR( CreateProperty(MM::g_Keyword_Label, "", MM::String, false, pAct) );

		char state[20];
		for (unsigned int i=0; i<numPos_; i++) {
			sprintf(state, "Position-%d", i);
			SetPositionLabel(i,state);
		}

		// Gate, or "closed" position
//		RETURN_ON_MM_ERROR( CreateProperty(MM::g_Keyword_Closed_Position, "0", MM::String, false) );

//		isOpen_ = false;		// MDual closed posisiton is

		RETURN_ON_MM_ERROR( UpdateStatus() );

		initialized_ = true;
	}

	return DEVICE_OK;
}


int OxxiusMDual::Shutdown()
{
	initialized_ = false;
	return DEVICE_OK;
}


void OxxiusMDual::GetName(char* Name) const
{
     CDeviceUtils::CopyLimitedString(Name, name_.c_str());
}


bool OxxiusMDual::Busy()
{
   return false;
}


int OxxiusMDual::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		unsigned int currentPos = 0;
		std::string command = "?MD1";

		RETURN_ON_MM_ERROR( parentHub_->QueryCommand(this, GetCoreCallback(), NO_SLOT, command.c_str()) );
		parentHub_->ParseforInteger(currentPos);

		//SetPosition(currentPos);
		pProp->Set((long)currentPos);
	}
	else if (eAct == MM::AfterSet) {
		long newPosition = 0;
		
		//GetPosition(newPosition);
		pProp->Get(newPosition);

		std::ostringstream newCommand;
		newCommand << "MD1 " << newPosition;

		RETURN_ON_MM_ERROR( parentHub_->QueryCommand(this, GetCoreCallback(), NO_SLOT, newCommand.str().c_str()) );
	} 
	return DEVICE_OK;
}