///////////////////////////////////////////////////////////////////////////////
// FILE:          Newport.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Newport Controller Driver
//
// AUTHOR:        Liisa Hirvonen, 03/17/2009
// AUTHOR:        Nico Stuurman 08/18/2005, added velocity, multiple addresses, 
// enabling multiple controllers, relative position, easier busy check and multiple
//  fixes for annoying behavior, see repository logs for complete list
// COPYRIGHT:     University of Melbourne, Australia, 2009-2013
// COPYRIGHT:     Regents of the University of California, 2015
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

#ifdef WIN32
	#include <windows.h>
	#define snprintf _snprintf
#endif

#include "Newport.h"
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>

const char* g_Newport_ZStageDeviceName = "NewportZStage";

using namespace std;

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
	RegisterDevice(g_Newport_ZStageDeviceName, MM::StageDevice, "Newport SMC100CC controller");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
	if (deviceName == 0)
		return 0;

	if (strcmp(deviceName, g_Newport_ZStageDeviceName) == 0)
	{
		NewportZStage* s = new NewportZStage();
		return s;
	}

	return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
	delete pDevice;
}


///////////////////////////////////////////////////////////////////////////////
// NewportZStage

NewportZStage::NewportZStage() :
	//port_("Undefined"),	;HCA 2013-02-15
	port_("COM5"),			//need to recompile with COM5 listed.
	stepSizeUm_(1),
   conversionFactor_(1000.0),    // divide request by this number
                 // to accomodate for units in mm rather than microns
   cAddress_(1),
	initialized_(false),
	lowerLimit_(0),  // limit in native coordinates
	upperLimit_(25), // limit in native coordinates
   velocity_(5.0),
   velocityLowerLimit_(0.000001),
   velocityUpperLimit_(100000000000.0)
{
	InitializeDefaultErrorMessages();

   SetErrorText(ERR_POSITION_BEYOND_LIMITS, "Requested position is beyond the limits of this stage");
   SetErrorText(ERR_TIMEOUT, "Timed out waiting for command to complete.  Try increasing the Core-TimeoutMs property if this was premature");

	// create properties
	// ------------------------------------

	// Name
	CreateProperty(MM::g_Keyword_Name, g_Newport_ZStageDeviceName, MM::String, true);

	// Description
	CreateProperty(MM::g_Keyword_Description, "Newport SMC100CC controller adapter", MM::String, true);

	// Port
	CPropertyAction* pAct = new CPropertyAction (this, &NewportZStage::OnPort);
	CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

	// Conversion factor
	pAct = new CPropertyAction (this, &NewportZStage::OnConversionFactor);
	CreateFloatProperty("Conversion Factor", conversionFactor_, false, pAct, true);

	// Controller address
	pAct = new CPropertyAction (this, &NewportZStage::OnControllerAddress);
	CreateIntegerProperty("Controller Address", cAddress_, false, pAct, true);
   SetPropertyLimits("Controller Address", 1, 31);
}


NewportZStage::~NewportZStage()
{
	Shutdown();
}

void NewportZStage::GetName(char* Name) const
{
	CDeviceUtils::CopyLimitedString(Name, g_Newport_ZStageDeviceName);
}

int NewportZStage::Initialize()
{
	const char* command;

   // Ask for errors and controller state.  This will also clear errors from the
   // controller, needed for my controller before anything is possible
	command = MakeCommand("TS").c_str();
	int ret = SendSerialCommand(port_.c_str(), command, "\r\n");
	if (ret != DEVICE_OK)
		return ret;
   std::string answer;
	ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
	if (ret != DEVICE_OK)
		return ret;
   LogMessage(answer.c_str(), true);

	// Send the "homing" command to init stage
   ret = SetOrigin();
   if (ret != DEVICE_OK)
      return ret;

   // check for errors, just to clear possible errors in the controller
   bool error;
   std::string errorCode;
   ret = GetError(error, errorCode);
   // do not return an error if we get one, since the user will never be able
   // to get out of the error if there is no other software

   // get the lower software limit
   ret = GetValue("SL?", lowerLimit_);
   if (ret != DEVICE_OK)
      return ret;

   // get the upper software limit
   ret = GetValue("SR?", upperLimit_);
   if (ret != DEVICE_OK)
      return ret;

	// Position property
	CPropertyAction* pAct = new CPropertyAction (this, &NewportZStage::OnPosition);
	CreateProperty(MM::g_Keyword_Position, "0", MM::Float, false, pAct);

   // Velocity property
	pAct = new CPropertyAction (this, &NewportZStage::OnVelocity);
   const char* velPropName = "Velocity in mm per sec";
	CreateProperty(velPropName, "0", MM::Float, false, pAct);
   SetPropertyLimits(velPropName, velocityLowerLimit_, velocityUpperLimit_);


   // Ask for controller version and report in read-only property
	command = MakeCommand("VE").c_str();
   ret = SendSerialCommand(port_.c_str(), command, "\r\n");
	if (ret != DEVICE_OK)
		return ret;
	ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
	if (ret != DEVICE_OK)
		return ret;

   CreateProperty("Controller version", answer.substr(strlen(command)).c_str(),
         MM::String, true);

	ret = UpdateStatus();
	if (ret != DEVICE_OK)
		return ret;

	initialized_ = true;
	return DEVICE_OK;
}

int NewportZStage::Shutdown()
{
	if (initialized_)
	{
		initialized_ = false;
	}
	return DEVICE_OK;
}

bool NewportZStage::Busy()
{
   // use the TS command to see if the controller is still moving
   std::string cmd = MakeCommand("TS");
   int ret = SendSerialCommand(port_.c_str(), cmd.c_str(), "\r\n");
   if (ret != DEVICE_OK)
      return ret;

	string answer;
	ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
	if (ret != DEVICE_OK)
		return false;
	// long tsResult = atol(answer.substr(cmd.size*()).c_str());
	int ef = atoi(answer.substr(cmd.size() + 4).c_str());

   std::ostringstream os;
   os << answer << ", " << ef;
   LogMessage(os.str().c_str());

   if (ef == 28)
      return true;

   return false;
}

int NewportZStage::SetPositionSteps(long steps)
{
	double pos = steps * stepSizeUm_;
	return SetPositionUm(pos);
}

int NewportZStage::GetPositionSteps(long& steps)
{
	double pos;
	int ret = GetPositionUm(pos);
	if (ret != DEVICE_OK)
		return ret;
	steps = (long) (pos / stepSizeUm_);
	return DEVICE_OK;
}

int NewportZStage::SetPositionUm(double pos)
{
   WaitForBusy();

   // convert from micron to mm:
   pos /= conversionFactor_;

   // compare position to limits (in native units)
	if (pos > upperLimit_ || lowerLimit_ > pos)
   {
      return ERR_POSITION_BEYOND_LIMITS;
   }

	// Send the "move" command
	ostringstream command;
	string answer;
	command << MakeCommand("PA") << pos;
	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r\n");
	if (ret != DEVICE_OK)
      return ret;

   bool error;
   std::string errorCode;
   ret = GetError(error, errorCode);
   if (ret != DEVICE_OK)
      return ret;
   if (error) 
   {
      std::string report = "Controller reported error code: " + errorCode;
      LogMessage(report.c_str(), false);
      return DEVICE_ERR;
   }

	return DEVICE_OK;
}

int NewportZStage::SetRelativePositionUm(double pos)
{
   WaitForBusy();

   // convert from micron to mm:
   pos /= conversionFactor_;

	// Send the "relative move" command
	ostringstream command;
	string answer;
	command << MakeCommand("PR") << pos;
	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r\n");
	if (ret != DEVICE_OK)
      return ret;

   bool error;
   std::string errorCode;
   ret = GetError(error, errorCode);
   if (ret != DEVICE_OK)
      return ret;
   if (error) 
   {
      std::string report = "Controller reported error code: " + errorCode;
      LogMessage(report.c_str(), false);
      return DEVICE_ERR;
   }

	return DEVICE_OK;
}

int NewportZStage::GetPositionUm(double& pos)
{
	const char* command;
	string answer;

	// Ask position
	command = MakeCommand("TP").c_str();
	int ret = SendSerialCommand(port_.c_str(), command, "\r\n");
	if (ret != DEVICE_OK)
		return ret;

	// Receive answer
	ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
	if (ret != DEVICE_OK)
		return ret;

	// Get the value from the reply string
	pos = atof(answer.substr(strlen(command),15).c_str());

   // convert from mm to microns:
   pos *= conversionFactor_;

	return DEVICE_OK;
}

int NewportZStage::SetOrigin()
{
	// Send the "homing" command to init stage
	const char* command = MakeCommand("OR").c_str();
	int ret = SendSerialCommand(port_.c_str(), command, "\r\n");
	if (ret != DEVICE_OK)
		return ret;

   return WaitForBusy();
}

int NewportZStage::GetLimits(double& lowerLimit, double& upperLimit)
{
   lowerLimit = lowerLimit_ * conversionFactor_;
   upperLimit = upperLimit_ * conversionFactor_;

	return DEVICE_OK;
}

int NewportZStage::GetError(bool& error, std::string& errorCode) 
{
	// Ask for error message
   std::string cmd = MakeCommand("TE");
   LogMessage(cmd);

	int ret = SendSerialCommand(port_.c_str(), cmd.c_str(), "\r\n");
	if (ret != DEVICE_OK)
		return ret;

	// Receive error message
   std::string answer;
	ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
	if (ret != DEVICE_OK)
		return ret;

	// Check that there is no error
   ostringstream dbg;
   errorCode = answer.substr(cmd.size(), 1);

   error = true;
	if (answer.substr(cmd.size(), 1) == "@")
   {
      error = false;
      return DEVICE_OK;
   }

   std::string msg = "Device return error code: " + errorCode;
   LogMessage(msg, true);

   SetErrorText(CONTROLLER_ERROR, msg.c_str());
   return CONTROLLER_ERROR;
}

int NewportZStage::WaitForBusy()
{
   char coreTimeout[MM::MaxStrLength];
   GetCoreCallback()->GetDeviceProperty("Core", "TimeoutMs", coreTimeout);
   long to = atoi(coreTimeout);
   MM::TimeoutMs timeout(GetCurrentMMTime(), to);
   while( Busy() ) {
      if (timeout.expired(GetCurrentMMTime())) 
      {
         return ERR_TIMEOUT;
      }
   }
   return DEVICE_OK;
}

/**
 * Utility function to read values that are returned with the command
 * such as the software limits
 */
int NewportZStage::GetValue(const char* cmd, double& val)
{
   const char* command = MakeCommand(cmd).c_str();
   int ret = SendSerialCommand(port_.c_str(), command, "\r\n");
   if (ret != DEVICE_OK)
      return ret;

   // Receive answer
   std::string answer;
   ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
   if (ret != DEVICE_OK)
   	return ret;

	// Get the value from the reply string
	val = atof(answer.substr(strlen(command) - 1).c_str());

   return DEVICE_OK;
}


/**
 * Sets the velocity of this stage
 * Uses device native values (i.e. mm/sec)
 */
int NewportZStage::SetVelocity(double velocity)
{
   std::ostringstream os;
   os << MakeCommand("VA") << velocity;

	// Set Velocity
	return SendSerialCommand(port_.c_str(), os.str().c_str(), "\r\n");
}

/**
 * Enquires the device about the current value of its velocity property
 * Uses device native values (i.e. mm/sec)
 */
int NewportZStage::GetVelocity(double& velocity)
{
	// Ask about velocity
   std::string cmd = MakeCommand("VA?");
	int ret = SendSerialCommand(port_.c_str(), cmd.c_str(), "\r\n");
	if (ret != DEVICE_OK)
		return ret;

	// Receive answer
	string answer;
	ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
	if (ret != DEVICE_OK)
      return DEVICE_OK;

	velocity = atof( answer.substr(cmd.length() - 1).c_str() );

   std::ostringstream os;
   os << answer << ", " << velocity;
   LogMessage(os.str().c_str());

   return DEVICE_OK;
}


/**
 * Utility function that prepends the command with the 
 * current device Address (set as a pre-init property, 1-31)
 */
std::string NewportZStage::MakeCommand(const char* cmd)
{
   std::ostringstream os;
   os << cAddress_ << cmd;
   return os.str();
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int NewportZStage::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int NewportZStage::OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
      double pos;
      int ret = GetPositionUm(pos);
      if (ret != DEVICE_OK)
         return ret;
      pProp->Set(pos);
	}
	else if (eAct == MM::AfterSet)
	{
		double pos;
		pProp->Get(pos);
		int ret = SetPositionUm(pos);
		if (ret != DEVICE_OK)
      {
			pProp->Set(pos); // revert
			return ret;
      }
   }

   return DEVICE_OK;
}

int NewportZStage::OnConversionFactor(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
      pProp->Set(conversionFactor_);
	}
	else if (eAct == MM::AfterSet)
	{
		double pos;
		pProp->Get(pos);
      conversionFactor_ = pos;
   }

   return DEVICE_OK;
}

int NewportZStage::OnControllerAddress(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
      pProp->Set((long) cAddress_);
	}
	else if (eAct == MM::AfterSet)
	{
		long pos;
		pProp->Get(pos);
      cAddress_ = (int) pos;
   }

   return DEVICE_OK;
}

int NewportZStage::OnVelocity(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
      int ret = GetVelocity(velocity_);
      if (ret != DEVICE_OK)
         return DEVICE_OK;
      pProp->Set((double) velocity_);
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(velocity_);
      return SetVelocity(velocity_);
   }

   return DEVICE_OK;
}
