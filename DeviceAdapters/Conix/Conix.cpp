///////////////////////////////////////////////////////////////////////////////
// FILE:       Conix.cpp
// PROJECT:    MicroManage
// SUBSYSTEM:  DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:
// Conix adapter
//                
// AUTHOR: Nico Stuurman, 02/27/2006
//         Trevor Osborn (ConixXYStage), trevor@conixresearch.com, 02/10/2010
//
// Based on Ludl controller adpater by Nenad Amodaj
//

#ifdef WIN32
#include <windows.h>
#define snprintf _snprintf 
#endif

#include "Conix.h"
#include <cstdio>
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>
#include <iostream>

const char* g_ConixQuadFilterName = "ConixQuadFilter";
const char* g_ConixXYStageName = "ConixXYStage";

using namespace std;



///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
	AddAvailableDeviceName(g_ConixQuadFilterName,"External Filter Cube Switcher");
	AddAvailableDeviceName(g_ConixXYStageName, "Conix XY stage");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
	if (deviceName == 0) {
		return 0;
	}

	if (strcmp(deviceName, g_ConixQuadFilterName) == 0) {
		QuadFluor* pQF = new QuadFluor();
		return pQF;
	} else if (strcmp(deviceName, g_ConixXYStageName) == 0) {
		// create stage
		return new ConixXYStage();
	}

	return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}


///////////////////////////////////////////////////////////////////////////////
// VMMController device
///////////////////////////////////////////////////////////////////////////////

QuadFluor::QuadFluor() :
   initialized_(false),
   numPos_(4),
   port_("Undefined"),
   pendingCommand_(false)
{
   InitializeDefaultErrorMessages();

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, g_ConixQuadFilterName, MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "Conix Motorized Qud-Filter changer for Nikon TE200/300", MM::String, true);

   // Port
   CPropertyAction* pAct = new CPropertyAction (this, &QuadFluor::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

}

QuadFluor::~QuadFluor()
{
   Shutdown();
}

void QuadFluor::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_ConixQuadFilterName);
}

int QuadFluor::Initialize()
{
   // set property list
   // -----------------
   
   // Get and Set State (allowed values 1-4, start at 0 for Hardware Configuration Wizard))
   CPropertyAction *pAct = new CPropertyAction (this, &QuadFluor::OnState);
   int nRet=CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
   if (nRet != DEVICE_OK)
      return nRet;

   vector<string> states;
   states.push_back("0");
   states.push_back("1");
   states.push_back("2");
   states.push_back("3");
   nRet = SetAllowedValues(MM::g_Keyword_State, states);

   // create default positions and labels
   for (long i=0; i < 4; i++)
   {
      std::ostringstream os;
      os << "Position: " << i;
      SetPositionLabel(i, os.str().c_str());
   }


   // Label
   // -----
   pAct = new CPropertyAction (this, &CStateBase::OnLabel);
   nRet = CreateProperty(MM::g_Keyword_Label, "", MM::String, false, pAct);
   if (nRet != DEVICE_OK)                                                     
      return nRet;   

   nRet = UpdateStatus();
   if (nRet != DEVICE_OK)
      return nRet;

   initialized_ = true;
   return DEVICE_OK;
}

int QuadFluor::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

/*
 *
 */
bool QuadFluor::Busy()
{
   // the commands are blocking, so we cannot be busy
   return false;
   /*
   const unsigned long answerLength = 40;
   // If there was no command pending we are not busy
   if (!pendingCommand_)
      return false;

   // Read a line from the port, if first char is 'A' we are OK
   // if first char is 'N' read the error code
   unsigned char answer[answerLength];
   unsigned long charsRead;
   ReadFromComPort(port_.c_str(), answer, answerLength, charsRead);
   if (answer[0] == "A") {
      // this command was finished and is not pending anymore
      pendingCommand_ = false;
      return true;
   }
   else
      return false;
   
   // we should never be here, better not to block
   return false;
   */
}


int QuadFluor::ExecuteCommand(const string& cmd)
{

   // Even though we have no clue, we'll say all is fine
   return DEVICE_OK;
}



///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////
/*
 * Sets the Serial Port to be used.
 * Should be called before initialization
 */
int QuadFluor::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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
         //return ERR_PORT_CHANGE_FORBIDDEN;
      }

      pProp->Get(port_);
   }
   return DEVICE_OK;
}

int QuadFluor::GetPosition(int& position) 
{
   const char* command="Quad ";

   // send command
   int ret = SendSerialCommand(port_.c_str(), command, "\r");
   if (ret != DEVICE_OK)
      return ret;

   // block/wait for acknowledge, or until we time out;
   string answer;
   ret = GetSerialAnswer(port_.c_str(), "\r", answer);
   if (ret != DEVICE_OK)
      return ret;

   if (answer.length() > 2 && answer.substr(0, 2).compare(":N") == 0)
   {
      int errNo = atoi(answer.substr(2).c_str());
      return ERR_OFFSET + errNo;
   }
   else if (answer.length() > 2 && answer.substr(0, 2).compare(":A") == 0)
   {
      position = atoi(answer.substr(2).c_str());
      if (position == 0)
         return ERR_UNRECOGNIZED_ANSWER;

      return DEVICE_OK;
   }

   return ERR_UNRECOGNIZED_ANSWER;
}


int QuadFluor::SetPosition(int position)
{
   ostringstream command;
   command << "Quad " << position;

   // send command
   int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
   if (ret != DEVICE_OK)
      return ret;

   // block/wait for acknowledge, or until we time out;
   string answer;
   ret = GetSerialAnswer(port_.c_str(), "\r", answer);
   if (ret != DEVICE_OK)
      return ret;

   if (answer.substr(0, 2).compare(":A") == 0)
   {
      return DEVICE_OK;
   }
   else if (answer.length() > 2 && answer.substr(0, 2).compare(":N") == 0)
   {
      int errNo = atoi(answer.substr(2).c_str());
      return ERR_OFFSET + errNo;
   }

   return ERR_UNRECOGNIZED_ANSWER;
}




// Needs to be worked on (a lot)
int QuadFluor::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      int position, ret;
      ret = GetPosition(position);
      if (ret != DEVICE_OK)
         return ret;
      pProp->Set((long) position - 1);
   }
   else if (eAct == MM::AfterSet) {
      long position;
      int ret;
      pProp->Get(position);
      ret = SetPosition((int) position + 1);
      if (ret != DEVICE_OK)
         return ret;
   }
   return DEVICE_OK;
}


int QuadFluor::OnCommand(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(command_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(command_);
printf ("%s\n","Executing Command:");
      return ExecuteCommand(command_);
   }

printf ("%s\n","Outof OnCommand");
   return DEVICE_OK;
}



///////////////////////////////////////////////////////////////////////////////
// ConixXYStage implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~

ConixXYStage::ConixXYStage() : 
CXYStageBase<ConixXYStage>(),
stepSize_um_(0.015),
posX_um_(0.0),
posY_um_(0.0),
busy_(false),
initialized_(false),
lowerLimit_(0.0),
upperLimit_(20000.0)
{
	InitializeDefaultErrorMessages();

	// set property list
	// -----------------

	// Name
	CreateProperty(MM::g_Keyword_Name, g_ConixXYStageName, MM::String, true);
	
	// Description
	CreateProperty(MM::g_Keyword_Description, "Conix XY stage driver", MM::String, true);

	// Port
	CPropertyAction* pAct = new CPropertyAction (this, &ConixXYStage::OnPort);
	CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

	UpdateStatus();
}


ConixXYStage::~ConixXYStage()
{
	Shutdown();
}


void ConixXYStage::GetName(char* Name) const
{
	CDeviceUtils::CopyLimitedString(Name, g_ConixXYStageName);
}


int ConixXYStage::Initialize()
{
	if (initialized_) {
		return DEVICE_OK;
	}

	// set the stage to use microns
	int ret = SetComUnits();
	if (ret != DEVICE_OK) {
		return ret;
	}

	initialized_ = true;

	return DEVICE_OK;
}


int ConixXYStage::Shutdown()
{
	if (initialized_) {
		initialized_ = false;
	}
	return DEVICE_OK;
}


bool ConixXYStage::Busy()
{
	unsigned char response[2];
	unsigned long charsRead;
	MM::MMTime timeout(0, 1000000); // wait for 1sec
	MM::MMTime start_time = GetCurrentMMTime();
	MM::MMTime elapsed_time;

	// cmd STATUS (the shortcut is /)
	int ret = SendSerialCommand(port_.c_str(), "/", "\r");
	if (ret != DEVICE_OK) {
		// we have to return false if a serial command fails to
		// prevent infinite looping
		return false;
	}

	// read the response from the status command, it responds with a
	// single character (no CR at the end) so I'm using ReadFromComPort
	// instead of GetSerialAnswer.
	response[0] = '\0';

	while (response[0] != 'B' && response[0] != 'N' && (elapsed_time < timeout)) {
		ReadFromComPort(port_.c_str(), response, 1, charsRead);
		elapsed_time = (GetCurrentMMTime() - start_time);
	}
	if (response[0] == 'B') {
		// only return true if the stage tells us it's busy...
		return true;
	}
	// ...otherwise it is either not busy or not connected,
	// in both cases we want to return false
	return false;
}


int ConixXYStage::GetPositionUm(double& x, double& y)
{
	while (Busy());  // make sure stage is not busy

	// cmd WHERE X Y (the shortcut is W)
	int ret = SendSerialCommand(port_.c_str(), "W X Y", "\r");
	if (ret != DEVICE_OK) {
		return ret;
	}

	string resp;
	ret = GetSerialAnswer(port_.c_str(), "\r", resp);
	if (ret != DEVICE_OK) {
		return ret;
	}
	if (resp.length() < 1) {
		return DEVICE_SERIAL_COMMAND_FAILED;
	}

	istringstream is(resp);
	string outcome;
	is >> outcome;

	// this command seems to want DECIMAL OFF
	if (outcome.compare(":A") == 0) {
		is >> x;
		is >> y;
		return DEVICE_OK; // success!
	}

	// return the error code
	int code;
	is >> code;
	return code;
}


int ConixXYStage::SetPositionUm(double x, double y)
{
	ostringstream cmd;

	// cmd MOVE X=x Y=y (shortcut is M Xx Yy)
	cmd << "M " << "X" << x << " " << "Y" << y;

	while (Busy());  // make sure stage is not busy

	int ret = SendSerialCommand(port_.c_str(), cmd.str().c_str(), "\r");
	if (ret != DEVICE_OK) {
		return ret;
	}

	string resp;
	ret = GetSerialAnswer(port_.c_str(), "\r", resp);
	if (ret != DEVICE_OK) {
		return ret;
	}
	if (resp.length() < 1) {
		return DEVICE_SERIAL_COMMAND_FAILED;
	}

	istringstream is(resp);
	string outcome;
	is >> outcome;

	if (outcome.compare(":A") == 0) { // command recieved correctly
		posX_um_ = x;
		posY_um_ = y;
		return DEVICE_OK; // success!
	}

	// return the error code
	int code;
	is >> code;
	return code;
}


int ConixXYStage::Home()
{
	while (Busy());  // make sure stage is not busy

	// cmd HOME (shortcut !)
	int ret = SendSerialCommand(port_.c_str(), "!", "\r");
	if (ret != DEVICE_OK) {
		return ret;
	}

	string resp;
	ret = GetSerialAnswer(port_.c_str(), "\r", resp);
	if (ret != DEVICE_OK) {
		return ret;
	}
	if (resp.length() < 1) {
		return DEVICE_SERIAL_COMMAND_FAILED;
	}

	istringstream is(resp);
	string outcome;
	is >> outcome;

	if (outcome.compare(":A") == 0) { // command was received correctly
		return DEVICE_OK; // success!
	}

	// return the error code
	int code;
	is >> code;
	return code;
}


int ConixXYStage::Stop()
{
	// cmd HALT (shortcut \)
	int ret = SendSerialCommand(port_.c_str(), "\\", "\r");
	if (ret != DEVICE_OK) {
		return ret;
	}

	string resp;
	ret = GetSerialAnswer(port_.c_str(), "\r", resp);
	if (ret != DEVICE_OK) {
		return ret;
	}
	if (resp.length() < 1) {
		return DEVICE_SERIAL_COMMAND_FAILED;
	}

	istringstream is(resp);
	string outcome;
	is >> outcome;

	if (outcome.compare(":A") == 0) {
		return DEVICE_OK; // success!
	} else if (outcome.compare(":N-21") == 0) {
		return DEVICE_OK; // halt called while stage is in motion,
	}	                  // not sure what to return

	// return the error code
	int code;
	is >> code;
	return code;
}


int ConixXYStage::SetOrigin()
{
	while (Busy());  // make sure stage is not busy

	// cmd HERE (shortcut H)
	int ret = SendSerialCommand(port_.c_str(), "H", "\r");
	if (ret != DEVICE_OK) {
		return ret;
	}

	string resp;
	ret = GetSerialAnswer(port_.c_str(), "\r", resp);
	if (ret != DEVICE_OK) {
		return ret;
	}
	if (resp.length() < 1) {
		return DEVICE_SERIAL_COMMAND_FAILED;
	}

	istringstream is(resp);
	string outcome;
	is >> outcome;

	if (outcome.compare(":A") == 0) {
	  return DEVICE_OK; // success!
	  // FIXME return SetAdapterOrigin?
	}
	return DEVICE_SERIAL_COMMAND_FAILED;
}


int ConixXYStage::SetComUnits(std::string unit_type /*= "UM"*/)
{
	while (Busy());  // make sure stage is not busy

	// make stage use microns if default value is used
	// cmd COMUNITS UM
	string command = "COMUNITS " + unit_type;
	int ret = SendSerialCommand(port_.c_str(), command.c_str(), "\r");
	if (ret != DEVICE_OK) {
		return ret;
	}

	string resp;
	ret = GetSerialAnswer(port_.c_str(), "\r", resp);
	if (ret != DEVICE_OK) {
		return ret;
	}
	if (resp.length() < 1) {
		return DEVICE_SERIAL_COMMAND_FAILED;
	}

	istringstream is(resp);
	string outcome;
	is >> outcome;

	if (outcome.compare(":A") == 0) {
		return DEVICE_OK; // success!
	}
	return DEVICE_SERIAL_COMMAND_FAILED;
}



///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int ConixXYStage::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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
