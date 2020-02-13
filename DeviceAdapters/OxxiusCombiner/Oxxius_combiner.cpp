
///////////////////////////////////////////////////////////////////////////////
// FILE:          OxxiusCombiner.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Controls Oxxius lasers and combiners through a serial port
// COPYRIGHT:     Oxxius SA, 2013-2019
// LICENSE:       LGPL
// AUTHORS:       Tristan Martinez, Pierre Bretagne
//


#include "Oxxius_combiner.h"
#include <cstdio>
#include <cstdlib>
#include <string>
#include <map>
#include "../../MMDevice/ModuleInterface.h"
using namespace std;

//
#define	MAX_NUMBER_OF_SLOTS	6
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

const char* g_ObisLaserDeviceName = "Obis laser source";
const char* g_ObisLaser1DeviceName = "Obis laser Source 1";
const char* g_ObisLaser2DeviceName = "Obis laser Source 2";
const char* g_ObisLaser3DeviceName = "Obis laser Source 3";
const char* g_ObisLaser4DeviceName = "Obis laser Source 4";
const char* g_ObisLaser5DeviceName = "Obis laser Source 5";
const char* g_ObisLaser6DeviceName = "Obis laser Source 6";

const char* g_OxxiusShutterDeviceName = "Shutter";
const char* g_OxxiusShutter1DeviceName = "Shutter 1";
const char* g_OxxiusShutter2DeviceName = "Shutter 2";
const char* g_OxxiusMDualDeviceName = "MDual";
const char* g_OxxiusMDualADeviceName = "MDual A";
const char* g_OxxiusMDualBDeviceName = "MDual B";
const char* g_OxxiusMDualCDeviceName = "MDual C";
const char* g_OxxiusFlipMirrorDeviceName = "Flip-Mirror";
const char* g_OxxiusFlipMirror1DeviceName = "Flip-Mirror 1";
const char* g_OxxiusFlipMirror2DeviceName = "Flip-Mirror 2";




const char* g_slotPrefix[7] = {"","L1 ","L2 ","L3 ","L4 ","L5 ","L6 "};

const char* convertable[3] = { "A", "B", "C" };

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
	RegisterDevice(g_OxxiusCombinerDeviceName, MM::HubDevice, "Oxxius laser combiner controlled through serial interface");
	RegisterDevice(g_OxxiusLaserBoxx1DeviceName, MM::ShutterDevice, "LaserBoxx on slot 1");
	RegisterDevice(g_OxxiusLaserBoxx2DeviceName, MM::ShutterDevice, "LaserBoxx on slot 2");
	RegisterDevice(g_OxxiusLaserBoxx3DeviceName, MM::ShutterDevice, "LaserBoxx on slot 3");
	RegisterDevice(g_OxxiusLaserBoxx4DeviceName, MM::ShutterDevice, "LaserBoxx on slot 4");
	RegisterDevice(g_OxxiusLaserBoxx5DeviceName, MM::ShutterDevice, "LaserBoxx on slot 5");
	RegisterDevice(g_OxxiusLaserBoxx6DeviceName, MM::ShutterDevice, "LaserBoxx on slot 6");

	RegisterDevice(g_ObisLaser1DeviceName, MM::ShutterDevice, "Obis Laser on slot 1");
	RegisterDevice(g_ObisLaser2DeviceName, MM::ShutterDevice, "Obis Laser on slot 2");
	RegisterDevice(g_ObisLaser3DeviceName, MM::ShutterDevice, "Obis Laser on slot 3");
	RegisterDevice(g_ObisLaser4DeviceName, MM::ShutterDevice, "Obis Laser on slot 4");
	RegisterDevice(g_ObisLaser5DeviceName, MM::ShutterDevice, "Obis Laser on slot 5");
	RegisterDevice(g_ObisLaser6DeviceName, MM::ShutterDevice, "Obis Laser on slot 6");

	RegisterDevice(g_OxxiusShutter1DeviceName, MM::ShutterDevice, "E-m shutter on channel 1");
	RegisterDevice(g_OxxiusShutter2DeviceName, MM::ShutterDevice, "E-m shutter on channel 2");
	RegisterDevice(g_OxxiusMDualADeviceName, MM::GenericDevice, "M-Dual on channel A");
	RegisterDevice(g_OxxiusMDualBDeviceName, MM::GenericDevice, "M-Dual on channel B");
	RegisterDevice(g_OxxiusMDualCDeviceName, MM::GenericDevice, "M-Dual on channel C");
	RegisterDevice(g_OxxiusFlipMirror1DeviceName, MM::GenericDevice, "Flip-Mirror on slot 1");
	RegisterDevice(g_OxxiusFlipMirror2DeviceName, MM::GenericDevice, "Flip-Mirror on slot 2");

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
	} else if (deviceNameAndSlot.compare(0, strlen(g_OxxiusMDualDeviceName), g_OxxiusMDualDeviceName) == 0) {
		return new OxxiusMDual(deviceNameChar);
	} else if (deviceNameAndSlot.compare(0, strlen(g_OxxiusFlipMirrorDeviceName), g_OxxiusFlipMirrorDeviceName) == 0) {
		return new OxxiusFlipMirror(deviceNameChar);
	} else if (deviceNameAndSlot.compare(0, strlen(g_ObisLaserDeviceName), g_ObisLaserDeviceName) == 0) {
//		return new OxxiusObisSupport(deviceNameChar);
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
	
		// Enumerates the installed AOMs and their position
		bool AOM1en = false, AOM2en = false;
		unsigned int ver = 0;

		RETURN_ON_MM_ERROR(QueryCommand(this, GetCoreCallback(), NO_SLOT, "AOM1 EN", false));
		ParseforBoolean(AOM1en);

		RETURN_ON_MM_ERROR(QueryCommand(this, GetCoreCallback(), NO_SLOT, "AOM2 EN", false));
		ParseforBoolean(AOM2en);

		RETURN_ON_MM_ERROR(QueryCommand(this, GetCoreCallback(), NO_SLOT, "SV?", false));
		ParseforVersion(ver);

		// A position equal to "0" stands for an absence of modulator
		if (AOM1en) {
			bool adcom = false;
			string command = "";

			if (ver < 1016) { //version check 
				adcom = true;
				command = "AOM1 PO";
			}
			else {
				adcom = false;
				command = "AOM1 POS";
			}
			RETURN_ON_MM_ERROR(QueryCommand(this, GetCoreCallback(), NO_SLOT, command.c_str(), adcom));
			ParseforInteger(AOM1pos_);
		}
		if (AOM2en) {
			bool adcom = false;
			string command = "";

			if (ver < 1016) { //version check 
				adcom = true;
				command = "AOM2 PO";
			}
			else {
				adcom = false;
				command = "AOM2 POS";
			}
			RETURN_ON_MM_ERROR(QueryCommand(this, GetCoreCallback(), NO_SLOT, command.c_str(), adcom));
			ParseforInteger(AOM1pos_);
		}


		//Mpa position retreive
		for (unsigned int i = 1; i <= MAX_NUMBER_OF_SLOTS; i++) {
			string command = "IP";
			std::stringstream ss;
			ss << i;
			command += ss.str();

			RETURN_ON_MM_ERROR(QueryCommand(this, GetCoreCallback(), NO_SLOT, command.c_str(), true));
			if (serialAnswer_ != "????") {
				mpa[i] = 1;
			}
		}

		

		RETURN_ON_MM_ERROR( UpdateStatus() );

		initialized_ = true;

		// RETURN_ON_MM_ERROR( DetectInstalledDevices() );
	}
	return DEVICE_OK;
}



int OxxiusCombinerHub::DetectInstalledDevices()
{
	if (initialized_) {

		// Enumerates the lasers (or devices) present on the combiner
		unsigned int masque = 1;
		unsigned int repartition = 0;
		
		//sending command ?CL
		RETURN_ON_MM_ERROR( QueryCommand(this, GetCoreCallback(), NO_SLOT, "?CL", false) );
		ParseforInteger(repartition);

		for(unsigned int querySlot=1; querySlot<=MAX_NUMBER_OF_SLOTS; querySlot++)	{
			if ((repartition & masque) != 0) {
				string answer;
				// A laser source is listed, now querying for detailed information (model, etc)

				std::string detailedInfo, serialNumber;

				//send command to get devices information
				RETURN_ON_MM_ERROR(QueryCommand(this, GetCoreCallback(), querySlot, "INF?", false));
				ParseforString(detailedInfo);

				if (detailedInfo != "timeout") {
					std::ostringstream nameSlotModel;
					nameSlotModel << g_OxxiusLaserBoxxDeviceName << " " << querySlot;

					MM::Device* pDev = ::CreateDevice(nameSlotModel.str().c_str());
					if (pDev) {
						AddInstalledDevice(pDev);
						installedDevices_++;
					}
				}
			}
			masque <<= 1;		// Left-shift the bit mask and repeat
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
		/*unsigned int FM1type = 0, FM2type = 0;
		RETURN_ON_MM_ERROR(QueryCommand(this, GetCoreCallback(), NO_SLOT, "FM1C", false));
		ParseforInteger(FM1type);
		RETURN_ON_MM_ERROR(QueryCommand(this, GetCoreCallback(), NO_SLOT, "FM2C", false));
		ParseforInteger(FM2type);*/


		//Mdual module creation 
		for (unsigned int j = 0; j <= 2; j++) {
			std::string MDSlot;
			std::ostringstream com;
			com << "IP" << convertable[j];

			std::ostringstream InfoMessage4;   /////test in hub
			InfoMessage4 << "test" << com;
			LogError(DEVICE_OK, this, GetCoreCallback(), InfoMessage4.str().c_str());

			RETURN_ON_MM_ERROR(QueryCommand(this, GetCoreCallback(), NO_SLOT, com.str().c_str(), true));
			ParseforString(MDSlot);

			com.str("");
			com.clear();
			com << g_OxxiusMDualDeviceName << " " << convertable[j];
			
			if (MDSlot != "????") {
				MM::Device* pDev = ::CreateDevice(com.str().c_str());
				if (pDev) {
					AddInstalledDevice(pDev);
					installedDevices_++;
				}

			}

		}

		//Flip mirror module creation 

		for (unsigned int j = 1; j <= 4; j++) {
			unsigned int FMSlot;
			std::ostringstream com;
			com << "FM" << j << "C";

			RETURN_ON_MM_ERROR(QueryCommand(this, GetCoreCallback(), NO_SLOT, com.str().c_str(), false));
			ParseforInteger(FMSlot);

			com.str("");
			com.clear();
			com << g_OxxiusFlipMirrorDeviceName << " " << j;

			if (FMSlot == 1) {

				std::ostringstream InfoMessage4;   /////test
				InfoMessage4 << "test " << com.str().c_str();
				LogError(DEVICE_OK, this, GetCoreCallback(), InfoMessage4.str().c_str());

				MM::Device* pDev = ::CreateDevice(com.str().c_str());
				if (pDev) {
					AddInstalledDevice(pDev);
					installedDevices_++;
				}

			}

		}


		//Laser OBIS creation
		RETURN_ON_MM_ERROR(QueryCommand(this, GetCoreCallback(), NO_SLOT, "OB", true));
		ParseforInteger(obPos_);

		if (obPos_ != 0 && obPos_ != -1) {
			std::ostringstream nameSlotModel;
			nameSlotModel << g_ObisLaserDeviceName << " " << obPos_;

			MM::Device* pDev = ::CreateDevice(nameSlotModel.str().c_str());
			if (pDev) {
				AddInstalledDevice(pDev);
				installedDevices_++;
			}
		}

		/*switch (FM1type) {
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
		}*/
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
		QueryCommand(this, GetCoreCallback(), NO_SLOT, "HID?", false);
		ParseforString(serialNumber_);
		pProp->Set(serialNumber_.c_str());
	}
     
	return DEVICE_OK;
}


int OxxiusCombinerHub::OnInterlock(MM::PropertyBase* pProp, MM::ActionType pAct)
{
	if (pAct == MM::BeforeGet) {
		QueryCommand(this, GetCoreCallback(), NO_SLOT, "INT?", false);
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
		QueryCommand(this, GetCoreCallback(), NO_SLOT, "KEY?", false);
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

void OxxiusCombinerHub::LogError(int id, MM::Device* device, MM::Core* core, const char* functionName) //prinnt log messages
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
int OxxiusCombinerHub::QueryCommand(MM::Device* device, MM::Core* core, const unsigned int destinationSlot, const char* command, bool adco)
{
	// First check: if the command string is empty, do nothing and return "DEVICE_OK"
	if( strcmp(command, "") == 0) return DEVICE_OK;

	char rcvBuf_[RCV_BUF_LENGTH];
	// Compose the command to be sent to the combiner
	std::string strCommand, strHZIn, strHZOut;
	strCommand.assign(g_slotPrefix[destinationSlot]);
	strCommand.append(command);
	strHZIn.assign(g_slotPrefix[destinationSlot]);
	strHZIn.append("HZ 9876");
	strHZOut.assign(g_slotPrefix[destinationSlot]);
	strHZOut.append("HZ 0");

/*
	std::ostringstream InfoMessage;
	InfoMessage << "Now sending command :";
	InfoMessage << string(strCommand.c_str());
	LogError(DEVICE_OK, device, core, InfoMessage.str().c_str());
*/
	std::ostringstream InfoMessage2;
	InfoMessage2 << "Send: " << command << " Received: ";
	
	// Preambule for specific commands
	if (adco) {
		int ret = core->SetSerialCommand(device, port_.c_str(), strHZIn.c_str(), "\r\n");
		ret = core->GetSerialAnswer(device, port_.c_str(), RCV_BUF_LENGTH, rcvBuf_, "\r\n");
		if (ret != DEVICE_OK) {
			LogError(ret, device, core, "QueryCommand-SetSerialCommand - preambule");
			return ret;
		}
	}

	// Send command through the serial interface
	int ret = core->SetSerialCommand(device, port_.c_str(), strCommand.c_str(), "\r\n");
	if (ret != DEVICE_OK) {
		LogError(ret, device, core, "QueryCommand-SetSerialCommand");
		return ret;
	}
  
	// Get a response
	ret = core->GetSerialAnswer(device, port_.c_str(), RCV_BUF_LENGTH, rcvBuf_, "\r\n");

	InfoMessage2 << rcvBuf_ ;
	/* DEBUG ONLY */
	// LogError(DEVICE_OK, device, core, InfoMessage2.str().c_str());

	if (ret != DEVICE_OK) {
		LogError(ret, device, core, "QueryCommand-GetSerialAnswer");

		// Keep on trying until we either get our answer, or 3 seconds have passed
		int maxTimeMs = 3000;
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
	ret = core->PurgeSerial(device, port_.c_str());

/*	if( strcmp(serialAnswer_, "timeout") == 0)	{
		std::ostringstream syntaxErrorMessage;
		syntaxErrorMessage << "Time out received against sent command '";
		syntaxErrorMessage << string(strCommand.c_str());
		syntaxErrorMessage << "'";

		LogError(DEVICE_SERIAL_TIMEOUT, device, core, syntaxErrorMessage.str().c_str());
		return DEVICE_SERIAL_TIMEOUT;
	}
*/
		// Epilogue for specific commands
	if (adco) {
		int ret = core->SetSerialCommand(device, port_.c_str(), strHZOut.c_str(), "\r\n");
		ret = core->GetSerialAnswer(device, port_.c_str(), RCV_BUF_LENGTH, rcvBuf_, "\r\n");
		if (ret != DEVICE_OK) {
			LogError(ret, device, core, "QueryCommand-SetSerialCommand - Epilogue");
			return ret;
		}
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


int OxxiusCombinerHub::ParseforFloat(float &Dval)
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


int OxxiusCombinerHub::ParseforVersion(unsigned int &Vval) //cast the string into a comparable int
{
	std::string temp1;
	std::string temp2(serialAnswer_);

	for (unsigned int i = 0; i <= (temp2.length())-1; i++) {
		if (temp2.at(i) != '.') {
			temp1 += temp2.at(i);
		}
	}

	stringstream s(temp1);
	s >> Vval;
	serialAnswer_.clear();

	return DEVICE_OK;
}


int OxxiusCombinerHub::ParseforPercent(double &Pval) //cast the string into a comparable int
{
	std::string percentage;
	std::size_t found;

	percentage.assign(serialAnswer_);

	found = percentage.find("%"); 
	if (found != std::string::npos) {
		Pval = atof(percentage.substr(0, found).c_str());
	}

	return DEVICE_OK;
}



int OxxiusCombinerHub::ParseforChar(char* Nval) 
{
	strcpy(Nval,serialAnswer_.c_str());
	serialAnswer_.clear();

	return DEVICE_OK;
}

bool OxxiusCombinerHub::GetAOMpos1(unsigned int slot) 
{
	bool res = false;

	if (slot == AOM1pos_) {
		res = true;
	}
	
	return res;
}

bool OxxiusCombinerHub::GetAOMpos2(unsigned int slot)
{
	bool res = false;
	
	if (slot == AOM2pos_) {
		res = true;
	}

	return res;
}

bool OxxiusCombinerHub::GetMPA(unsigned int slot) { 
	bool res = false;

	if (mpa[slot] == 1) {
		res = true;
	}

	return res;
}

int OxxiusCombinerHub::GetObPos() {
	return obPos_;
}


///////////////////////////////////////////////////////////////////////////////
//
// Oxxius generic LaserBoxx implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
//
///////////////////////////////////////////////////////////////////////////////

/*std::ostringstream InfoMessageB;  //////////// test out of hub
  InfoMessageB << "testB" << "-" << slot_;
  LogMessage(InfoMessageB.str().c_str(), false);*/

OxxiusLaserBoxx::OxxiusLaserBoxx(const char* nameAndSlot) : initialized_(false)
{
	std::string tSlot = string(nameAndSlot);

	name_.assign(tSlot);// set laser name
	tSlot = tSlot.substr(tSlot.length()-1, 1);
	slot_ = (unsigned int)atoi(tSlot.c_str());// set laser slot

	parentHub_ ;
	busy_ = false;
	laserOn_ = false;
	alarm_ = "";
	state_ = "";
	digitalMod_ = "";
	analogMod_ = "";
	controlMode_ = "";

	// powerSetPoint_ = 0.0;
	maxRelPower_ = 0.0;
	nominalPower_ = 0.0;
	maxCurrent_ = 125.0;

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

		std::size_t found;
		std::string strSlot;
		std::string spa;


		parentHub_ = static_cast<OxxiusCombinerHub*>(GetParentHub());
		if (!parentHub_ ) {
			return DEVICE_COMM_HUB_MISSING;
		}
		char hubLabel[MM::MaxStrLength];
		parentHub_->GetLabel(hubLabel);
		SetParentID(hubLabel); // for backward compatibility

		// Set property list
		// -----------------
		// Name (read only)
		RETURN_ON_MM_ERROR( CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true) );

		// Description (read only)
		std::ostringstream descriPt1;
		char sourceSerialNumber[] = "LAS-XXXXXXXXXX";
		parentHub_->QueryCommand(this, GetCoreCallback(), slot_, "HID?", false);
		parentHub_->ParseforChar(sourceSerialNumber);

		parentHub_->QueryCommand(this, GetCoreCallback(), slot_, "IP", true);
		parentHub_->ParseforString(spa);

		parentHub_->QueryCommand(this, GetCoreCallback(), slot_, "INF?", false);
		parentHub_->ParseforString(strSlot);

		// Retrieves and define the laser's type
		found = strSlot.find("-"); 
		if (found != std::string::npos) {
			type = strSlot.substr(0, found);
		}
		strSlot.erase(0, found + 1);

		// Retrieves and define the laser's wavelength
		found = strSlot.find("-"); 
		if (found != std::string::npos) {
			waveLength = (unsigned int)atoi(strSlot.substr(0, found).c_str());
			
		}

		// Retrieves and define the nominal power
		strSlot.erase(0, found + 1);
		nominalPower_ = (float) atof(strSlot.substr(0, found).c_str());

		//set laser AOM if needed
		//	model[0]	model[1]
		//	(major)		(minor)
		//		1			0		-> standard LBX
		//		1			1n		-> LBX linked to mpa number n
		//		2			0		-> standard LCX
		//		2			1		-> LCX linked to a AOM number 1
		//		2			2		-> LCX linked to a AOM number 2
		//		2			5		-> LCX with power adjustment
		//		2			1n		-> LCX linked to mpa number n


		if (parentHub_->GetMPA(slot_)) {
			model_[1] = 10 + slot_;
		} else {
			model_[1] = 0;
		}

		if (type.compare("LBX") == 0) { // The source is a LBX
			model_[0] = 1;
		}
		else if (type.compare("LCX") == 0) { // The source is a LCX
			model_[0] = 2;
			if (parentHub_->GetAOMpos1(slot_)) { //laser has AMO1
				model_[1] = 1;
			}
			if (parentHub_->GetAOMpos2(slot_)){ //laser has AMO2
				model_[1] = 2;
			}
			else if (spa != "????") { //self modulating lcx
				model_[1] = 5;
			}
		}
		else { // Should not happen: unkown type
			model_[0] = 9;
			model_[1] = 9;
		}

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



		// Retrieves and define the laser's maximal power
		std::string maxpowCmd = "DL SPM";
		switch (model_[0]) {
			case 1:		// LBX model
				switch (model_[1]) {
					case 0:				// Standard LBX
						parentHub_->QueryCommand(this, GetCoreCallback(), slot_, maxpowCmd.c_str(), true);
						parentHub_->ParseforFloat(maxRelPower_);
						maxRelPower_ = 100 * maxRelPower_ / nominalPower_;
						break;
					default:			// LBX + MPA
						maxRelPower_ = 100.0;
						break;
				}
				break;
			case 2:		// LCX model
				maxRelPower_ = 100.0;
				break;
			default:	// Should not happen
				maxRelPower_ = 0.0;
				break;
		}


		// Retrieves and define the laser's maximal current
		maxCurrent_ = 125.0;


		// Power set point (write/read)
		pAct = new CPropertyAction (this, &OxxiusLaserBoxx::OnPowerSetPoint);
		RETURN_ON_MM_ERROR( CreateProperty("Power set point", "0", MM::Float, false, pAct) );
		SetPropertyLimits("Power set point", 0, maxRelPower_);	

		// Power set point (write/read)
		pAct = new CPropertyAction (this, &OxxiusLaserBoxx::OnCurrentSetPoint);
		RETURN_ON_MM_ERROR( CreateProperty("Current set point", "0", MM::Float, false, pAct) );
		SetPropertyLimits("Current set point", 0, maxCurrent_);	

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


int OxxiusLaserBoxx::SetOpen(bool openCommand)
{
	laserOn_ = openCommand;
	return DEVICE_OK;
}


int OxxiusLaserBoxx::GetOpen(bool& isOpen)
{
	isOpen = laserOn_;
	return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int OxxiusLaserBoxx::OnAlarm(MM::PropertyBase* pProp, MM::ActionType)
{
	unsigned int alarmInt = 99;
	RETURN_ON_MM_ERROR( parentHub_->QueryCommand(this, GetCoreCallback(), slot_, "?F", false) );

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
	RETURN_ON_MM_ERROR( parentHub_->QueryCommand(this, GetCoreCallback(), slot_, "?STA", false) );

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

		RETURN_ON_MM_ERROR( parentHub_->QueryCommand(this, GetCoreCallback(), NO_SLOT, query.str().c_str(), false) );
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

		RETURN_ON_MM_ERROR( parentHub_->QueryCommand(this, GetCoreCallback(), slot_, newCommand.c_str(), false) );
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

		RETURN_ON_MM_ERROR( parentHub_->QueryCommand(this, GetCoreCallback(), querySlot, query.c_str(), false) );
		
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

		RETURN_ON_MM_ERROR( parentHub_->QueryCommand(this, GetCoreCallback(), querySlot, newCommand.c_str(), false) );
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

		RETURN_ON_MM_ERROR( parentHub_->QueryCommand(this, GetCoreCallback(), querySlot, query.c_str(), false) );
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

		RETURN_ON_MM_ERROR( parentHub_->QueryCommand(this, GetCoreCallback(), querySlot, newCommand.str().c_str(), false) );
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

				RETURN_ON_MM_ERROR( parentHub_->QueryCommand(this, GetCoreCallback(), slot_, command.c_str(), false));
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
				
				RETURN_ON_MM_ERROR( parentHub_->QueryCommand(this, GetCoreCallback(), slot_, newCommand.c_str(), false) );

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
		float absSetPoint_;
	
		if ((10 < model_[1]) && (model_[1] < 17)) {
			thisSlot = NO_SLOT;
			command = "?PL";
			stringstream s;
			s << (model_[1] - 10);
			command += s.str();
		}
		else if (model_[0] == 2) {
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
		
		RETURN_ON_MM_ERROR( parentHub_->QueryCommand(this, GetCoreCallback(), thisSlot, command.c_str(), false) );
		parentHub_->ParseforFloat(absSetPoint_);

		pProp->Set( (100 * absSetPoint_) / nominalPower_ );
	}
	else if (eAct == MM::AfterSet) {
		
		double GUISetPoint = 0.0;
		pProp->Get(GUISetPoint);

		if( (GUISetPoint >= 0.0)||(GUISetPoint <= maxRelPower_) ) {
			std::string command = "P";
			unsigned int thisSlot = slot_;

			std::ostringstream newCommand;
			char * powerSPString = new char[20];
			strcpy(powerSPString , CDeviceUtils::ConvertToString( (GUISetPoint * nominalPower_) / 100 ));

			if ((10 < model_[1]) && (model_[1] < 17)) {
				thisSlot = NO_SLOT;
				command = "IP";
				command += CDeviceUtils::ConvertToString((int)(model_[1] - 10));
				
				strcpy(powerSPString , CDeviceUtils::ConvertToString(GUISetPoint) );
			}
			else if (model_[0] == 2) {
				switch (model_[1]) {
					case 1:		// LCX on AOM1
						thisSlot = NO_SLOT;
						command = "P";
						break;
					case 2:		// LCX on AOM2
						thisSlot = NO_SLOT;
						command = "P2";
						break;
					case 5:		// LCX with power adjustment
						command = "IP";
						strcpy(powerSPString , CDeviceUtils::ConvertToString(GUISetPoint) );	
						break;
					default:
						break;
				}
			}
			newCommand << command << " " << powerSPString;
			RETURN_ON_MM_ERROR( parentHub_->QueryCommand(this, GetCoreCallback(), thisSlot, newCommand.str().c_str(), false) );
		} else {
			// If the value entered through the GUI is not valid, read the machine value
			OnPowerSetPoint(pProp,MM::BeforeGet);
		}
	}
	return DEVICE_OK;
}



int OxxiusLaserBoxx::OnCurrentSetPoint(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		float machineSetPoint = 0.0;
		std::string command = "?SC";
		unsigned int thisSlot = slot_;
	
		if (model_[0] == 1) {		// Current modification only allowed on LBX models
			RETURN_ON_MM_ERROR( parentHub_->QueryCommand(this, GetCoreCallback(), thisSlot, command.c_str(), false) );
			parentHub_->ParseforFloat(machineSetPoint);
		}

		pProp->Set( machineSetPoint );
	}

	else if (eAct == MM::AfterSet) {
		
		double GUISetPoint = 0.0;
		pProp->Get(GUISetPoint);

		if( (GUISetPoint >= 0.0)||(GUISetPoint <= maxCurrent_) ) {

			std::ostringstream newCommand;
			std::string command = "C";
			unsigned int thisSlot = slot_;

			char * currentSPString = new char[20];
			strcpy(currentSPString , CDeviceUtils::ConvertToString(GUISetPoint) );

			if (model_[0] == 1) {		// Current modification only allowed on LBX models
				newCommand << command << " " << currentSPString;
				RETURN_ON_MM_ERROR( parentHub_->QueryCommand(this, GetCoreCallback(), thisSlot, newCommand.str().c_str(), false) );
			}
		}
	}
	return DEVICE_OK;
}




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

		RETURN_ON_MM_ERROR( parentHub_->QueryCommand(this, GetCoreCallback(), NO_SLOT, newCommand.str().c_str(), false));
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
	parentHub_ = 0;
	core_ = GetCoreCallback();

	std::string tSlot = string(nameAndSlot);
	name_.assign(tSlot); // sets MDual name

	slot_ = tSlot.substr(tSlot.length() - 1, 1);

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
		CPropertyAction* pAct = new CPropertyAction(this, &OxxiusMDual::OnSetRatio);//setting the possible positions
		RETURN_ON_MM_ERROR(CreateProperty("Split ratio", "0", MM::Float, false, pAct));
		SetPropertyLimits("Split ratio", 0.0, 100.0);

		// Set property list
		// -----------------
		// State
		/*CPropertyAction* pAct = new CPropertyAction (this, &OxxiusMDual::OnState);
		RETURN_ON_MM_ERROR( CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct) );
		SetPropertyLimits("Set Position", 0, 100);*/

		/*char pos[3];
		for (unsigned int i=0; i<numPos_; i++) {
			sprintf(pos, "%d", i);
			AddAllowedValue(MM::g_Keyword_State, pos);
		}*/
		
		// Label
		/*pAct = new CPropertyAction (this, &CStateBase::OnLabel);
		RETURN_ON_MM_ERROR( CreateProperty(MM::g_Keyword_Label, "", MM::String, false, pAct) );

		char state[20];
		for (unsigned int i=0; i<numPos_; i++) {
			sprintf(state, "Position-%d", i);
			SetPositionLabel(i,state);
		}*/

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


/*int OxxiusMDual::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		unsigned int currentPos = 0;
		std::ostringstream command;
		command << "IP" << slot_;

		RETURN_ON_MM_ERROR( parentHub_->QueryCommand(this, GetCoreCallback(), NO_SLOT, command.str().c_str()) );
		parentHub_->ParseforInteger(currentPos);

		//SetPosition(currentPos);
		pProp->Set((long)currentPos);
	}
	else if (eAct == MM::AfterSet) {
		long newPosition = 0;
		
		//GetPosition(newPosition);
		pProp->Get(newPosition);

		std::ostringstream newCommand;
		newCommand << "IP" << slot_ << " " << newPosition;

		RETURN_ON_MM_ERROR( parentHub_->QueryCommand(this, GetCoreCallback(), NO_SLOT, newCommand.str().c_str()) );
	} 
	return DEVICE_OK;
}*/


int OxxiusMDual::OnSetRatio(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		double currentRatio = 0.0;
		std::ostringstream command;
		command << "IP" << slot_;

		RETURN_ON_MM_ERROR(parentHub_->QueryCommand(this, GetCoreCallback(), NO_SLOT, command.str().c_str(), true));
		parentHub_->ParseforPercent(currentRatio);

		pProp->Set(currentRatio);
	}

	else if (eAct == MM::AfterSet) {
		double newRatio = 0.0;

		pProp->Get(newRatio);
		if( (newRatio >= 0.0) || (newRatio <= 100.0) ) {
			std::ostringstream newCommand;
			newCommand << "IP" << slot_ << " " << newRatio;

			RETURN_ON_MM_ERROR(parentHub_->QueryCommand(this, GetCoreCallback(), NO_SLOT, newCommand.str().c_str(), true));
		}
	}
	return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
//
// Oxxius Flip-Mirror implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
//
///////////////////////////////////////////////////////////////////////////////

OxxiusFlipMirror::OxxiusFlipMirror(const char* nameAndSlot) : initialized_(false)
{
	parentHub_ = 0;
	core_ = GetCoreCallback();

	std::string fSlot = string(nameAndSlot);
	nameF_.assign(fSlot);// set laser name
	fSlot = fSlot.substr(fSlot.length() - 1, 1);
	slot_ = (unsigned int)atoi(fSlot.c_str());// set laser slot

	// Set property list ///////////////////////////////////////////////////////////////////////////////////////////////////// NOT WORKING? (duplicate property name Name(4))
	// -----------------
	// Name (read only)
	/*CreateProperty(MM::g_Keyword_Name, nameF_.c_str(), MM::String, true);

	CreateProperty(MM::g_Keyword_Description, "Flip-Mirror module", MM::String, true);

	InitializeDefaultErrorMessages();
	SetErrorText(ERR_NO_PORT_SET, "Hub Device not found.  The Laser combiner is needed to create this device");

	// parent ID display
	CreateHubIDProperty();*/
}


OxxiusFlipMirror::~OxxiusFlipMirror()
{
	Shutdown();
}


int OxxiusFlipMirror::Initialize()
{
	if (!initialized_) {
		parentHub_ = static_cast<OxxiusCombinerHub*>(GetParentHub());
		if (!parentHub_) {
			return DEVICE_COMM_HUB_MISSING;
		}
		char hubLabel[MM::MaxStrLength];
		parentHub_->GetLabel(hubLabel);
		SetParentID(hubLabel); // for backward compatibility

		CPropertyAction* pAct = new CPropertyAction(this, &OxxiusFlipMirror::OnSwitchPos);//setting the possible positions
		RETURN_ON_MM_ERROR(CreateProperty("Switch Position", "0", MM::Integer, false, pAct));
		SetPropertyLimits("Switch Position", 0, 1);

		std::ostringstream descriPt2;
		descriPt2 << "";
		RETURN_ON_MM_ERROR(CreateProperty(MM::g_Keyword_Description, descriPt2.str().c_str(), MM::String, true));

		// Gate, or "closed" position
//		RETURN_ON_MM_ERROR( CreateProperty(MM::g_Keyword_Closed_Position, "0", MM::String, false) );

//		isOpen_ = false;		// MDual closed posisiton is

		RETURN_ON_MM_ERROR(UpdateStatus());

		initialized_ = true;
	}

	return DEVICE_OK;
}


int OxxiusFlipMirror::Shutdown()
{
	initialized_ = false;
	return DEVICE_OK;
}


void OxxiusFlipMirror::GetName(char* Name) const
{
	CDeviceUtils::CopyLimitedString(Name, nameF_.c_str());
}


bool OxxiusFlipMirror::Busy()
{
	return false;
}


int OxxiusFlipMirror::OnSwitchPos(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		unsigned int currentPos = 0;
		std::ostringstream command;
		command << "FM" << slot_;

		RETURN_ON_MM_ERROR(parentHub_->QueryCommand(this, GetCoreCallback(), NO_SLOT, command.str().c_str(), false));
		parentHub_->ParseforInteger(currentPos);

		//SetPosition(currentPos);
		pProp->Set((long)currentPos);
	}
	else if (eAct == MM::AfterSet) {
		long newPosition = 0;

		//GetPosition(newPosition);
		pProp->Get(newPosition);

		std::ostringstream newCommand;
		newCommand << "FM" << slot_ << " " << newPosition;

		RETURN_ON_MM_ERROR(parentHub_->QueryCommand(this, GetCoreCallback(), NO_SLOT, newCommand.str().c_str(), false));
	}
	return DEVICE_OK;
}
