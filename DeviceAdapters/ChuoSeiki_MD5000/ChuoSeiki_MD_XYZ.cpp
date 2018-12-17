//////////////////////////////////////////////////////////////////////////////////////
// FILE:          ChuoSeiki_MD_XYZ.cpp												//
// PROJECT:       Micro-Manager														//
// SUBSYSTEM:     DeviceAdapters													//
//----------------------------------------------------------------------------------//
// DESCRIPTION:   ChuoSeiki_MD5000 device adapter.									//
//																					//
// AUTHOR:        Duong Quang Anh, duong-anh@chuo.co.jp, 2018,						//
//																					//
// COPYRIGHT:     CHUO Precision Industrial Co. Ltd., Tokyo, Japan, 2018			//
//																					//
// LICENSE:       This library is free software; you can redistribute it and/or		//
//                modify it under the terms of the GNU Lesser General Public		//
//                License as published by the Free Software Foundation.				//
//																					//
//                You should have received a copy of the GNU Lesser General Public	//
//                License along with the source distribution; if not, write to		//
//                the Free Software Foundation, Inc., 59 Temple Place, Suite 330,	//
//                Boston, MA  02111-1307  USA										//
//																					//
//                This file is distributed in the hope that it will be useful,		//
//                but WITHOUT ANY WARRANTY; without even the implied warranty		//
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.			//
//																					//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR							//
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,					//
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.			//
//////////////////////////////////////////////////////////////////////////////////////

#ifdef WIN32
#include <windows.h>
#define snprintf _snprintf
#endif

#include "ChuoSeiki_MD_XYZ.h"

#include <cstdio>
#include <string>
#include <math.h>
#include <sstream>
#include <algorithm>

const char* g_ChuoSeikiTwoStagesDeviceName = "ChuoSeiki_MD 2-Axis";
const char* g_ChuoSeikiSingleStageDeviceName = "ChuoSeiki_MD 1-Axis";

const char* g_ChuoSeiki_Controller_Axis = "Controller Axis";
const char* g_ChuoSeiki_Accelration_Time_Pattern = "AccelrationTime Pattern";
const char* g_ChuoSeiki_Accelration_Time_PatternX = "AccelrationTime Pattern X";
const char* g_ChuoSeiki_Accelration_Time_PatternY = "AccelrationTime Pattern Y";
using namespace std;


//-----------------------------------------------------------------------------
//........... Class API: Register controller..................................
//-----------------------------------------------------------------------------


MODULE_API void InitializeModuleData()
{
	RegisterDevice(g_ChuoSeikiTwoStagesDeviceName, MM::XYStageDevice, "XY Stages");

	RegisterDevice(g_ChuoSeikiSingleStageDeviceName, MM::StageDevice, "Z Stage");
}                                                                            
                                                                             
MODULE_API MM::Device* CreateDevice(const char* deviceName)        // createdevice
{
	if (deviceName == 0)
		 return 0;

	if (strcmp(deviceName, g_ChuoSeikiTwoStagesDeviceName) == 0)                         
	{                                                                         
		MD_TwoStages* xystage = new MD_TwoStages();     
		 return xystage;                                                    
	}

	if (strcmp(deviceName, g_ChuoSeikiSingleStageDeviceName) == 0)
	{
		 MD_SingleStage* zstage = new MD_SingleStage();
		return zstage;
	}
	
   return 0;
}
                                                                             

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

int clearPort(MM::Device& device, MM::Core& core, const char* port)
{
   // Clear contents of serial port
   const unsigned int bufSize = 255;		//255
   unsigned char clear[bufSize];
   unsigned long read = bufSize;
   int status;
   while (read == bufSize)
   {
	  status = core.ReadFromSerial(&device, port, clear, bufSize, read);
      if (status != DEVICE_OK)
         return status;
   }
   return DEVICE_OK;
}


//-----------------------------------------------------------------------------
//........... Class1: Single stage controller..................................
//-----------------------------------------------------------------------------

MD_SingleStage::MD_SingleStage():

transmissionDelay_		(10), 
initializationStatus_	(false),
stepSize_um_			(1),
speed_step_				(1000),	//pps
accelTime_pattern_		(2),
answerTimeoutMs_		(20)		// answer time out is 100ms

{
	InitializeDefaultErrorMessages();

	// custom error messages:
	SetErrorText(ERR_DEBUG, "debug");		// for debug

	SetErrorText(ERR_NO_ANSWER,				"No answer from the controller.  Is it connected?");
	SetErrorText(ERR_PORT_CHANGE_FORBIDDEN, "Can't change port");
	SetErrorText(ERR_UNRECOGNIZED_ANSWER,	"Can't recognize answer");
	SetErrorText(ERR_NO_CONTROLLER,			"No controller");
	SetErrorText(ERR_HOMING,				"Home search failed");
	SetErrorText(ERR_TIMEOUT,				"Time out");

	SetErrorText(ERR_02_OPERATION_REFUSED_BY_PROGRAM_STOP,		"The operation is refused due to program stop");
	SetErrorText(ERR_03_NOT_ACCEPT_COMMAND,						"The command execution cannot be accepted");
	SetErrorText(ERR_04_OPERATION_REFUSED_BY_MOTOR_ROTATION,	"The operation is refused due to motor rotation");
	SetErrorText(ERR_06_PARAMETER_ERROR,						"Parameter error");
	SetErrorText(ERR_07_OPERATION_REFUSED_BY_MOTOR_STOP,		"The operation is refused due to motor stop");
	SetErrorText(ERR_08_OPERATION_REFUSED_BY_PROGRAM_RUNNING,	"The operation is refused due to program running");
	SetErrorText(ERR_0B_FAIL_TO_READ_DATA,						"Failed to read Data");
	SetErrorText(ERR_0C_CANT_FIND_REGISTERED_PROGRAM,			"The registered program cannot be found");
	SetErrorText(ERR_0D_NO_RESPONSE,							"No response");
	SetErrorText(ERR_0E_SPEED_CAN_NOT_SET_DURING_ACCEL,			"Speed cannot be set during motor rotation in S-curve accel/decel");
	SetErrorText(ERR_0F_MOTOR_EXCITATION_OFF,					"Motor excitation off");
	SetErrorText(ERR_50_STEP_OUT_ERROR,							"Step out error occurs");
	SetErrorText(ERR_51_STOP_SIGNAL_INPUT,						"Stop signal is being inputted");
	SetErrorText(ERR_52_STOP_SIGNAL_INPUT,						"Stop signal is being inputted");
	SetErrorText(ERR_53_NOT_CONSTANT_MODE_IN_SPEED_SETTING,		"Mode is not Constant in Speed setting in interpolation driving");

	// Name, read-only (RO)
	CreateProperty(MM::g_Keyword_Name, g_ChuoSeikiSingleStageDeviceName, MM::String, true);

	// Description, RO
	CreateProperty(MM::g_Keyword_Description, "ChuoSeiki single stage driver", MM::String, true);

	// select Port:
	CPropertyAction* pActType = new CPropertyAction(this, &MD_SingleStage::OnSelectPort_1S);
	CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pActType, true);

	// Select axis name to control, default is X, if using MD5230D it can be X or Y
	controllerAxis_ = "X";

	AddAllowedValue(g_ChuoSeiki_Controller_Axis, "X");
	AddAllowedValue(g_ChuoSeiki_Controller_Axis, "Y");

	AddAllowedValue(g_ChuoSeiki_Accelration_Time_Pattern, "1");
	AddAllowedValue(g_ChuoSeiki_Accelration_Time_Pattern, "2");
	AddAllowedValue(g_ChuoSeiki_Accelration_Time_Pattern, "3");
	AddAllowedValue(g_ChuoSeiki_Accelration_Time_Pattern, "4");
}

MD_SingleStage::~MD_SingleStage()
{
  if (initializationStatus_)
      initializationStatus_ = false;
	Shutdown();
}


void MD_SingleStage::GetName(char* Name) const
{
	CDeviceUtils::CopyLimitedString(Name, g_ChuoSeikiSingleStageDeviceName);
}

bool MD_SingleStage::SupportsDeviceDetection(void)
{
   return true;
}

MM::DeviceDetectionStatus MD_SingleStage::DetectDevice(void)
{
	// all conditions must be satisfied...
	MM::DeviceDetectionStatus result = MM::Misconfigured;

	try	
	{
		std::string lowercase = portName_1S;
		for( std::string::iterator i = lowercase.begin(); i != lowercase.end(); ++i) 
		{
			*i = (char)tolower(*i);
		}
		if( lowercase.length() > 0 &&  lowercase.compare("undefined") !=0  && lowercase.compare("unknown") !=0 )	
		{
			// the port property seems correct, so give it a try
			result = MM::CanNotCommunicate;

			// device specific default communication parameters
			GetCoreCallback()->SetDeviceProperty(portName_1S.c_str(), MM::g_Keyword_BaudRate,	"115200");
			GetCoreCallback()->SetDeviceProperty(portName_1S.c_str(), MM::g_Keyword_StopBits,	"1"		);
			GetCoreCallback()->SetDeviceProperty(portName_1S.c_str(), "AnswerTimeout",			"20.0"	);
			GetCoreCallback()->SetDeviceProperty(portName_1S.c_str(), "DelayBetweenCharsMs",	"0.0"	);

			// get portname for 1 stage controller
			MM::Device* device = GetCoreCallback()->GetDevice(this, portName_1S.c_str());
			// set port parameters
			device->Initialize();
			// check version
			int qvStatus = this->ConfirmVersion();
			if( DEVICE_OK != qvStatus ) 
				LogMessageCode(qvStatus,true);
			else
				 // to succeed must reach here....
				result = MM::CanCommunicate;
			// device shutdown, return default answertimeout	
			device->Shutdown();
			 // always restore the AnswerTimeout to the default
			GetCoreCallback()->SetDeviceProperty(portName_1S.c_str(), "AnswerTimeout", "2000.0");
		}
	}
	catch(...) 
	{
		LogMessage("Exception in DetectDevice!",false);
	}
	return result;
}

int MD_SingleStage::ConfirmVersion()
{
	int status;
	// clear comport
	PurgeComPort(portName_1S.c_str());
	std::string version;
	// send command to check controller version
	status= ExecuteCommand("RVR");
	if (status!= DEVICE_OK) 
		return status;
	// read serial com, read in 20ms, end when timeouts
	status= ReadMessage(version);
	if (status != DEVICE_OK) 
		return status;
	
	if (version.substr(0,3) != "RVR" ||version == "" ) 
		return ERR_UNRECOGNIZED_ANSWER;

	return DEVICE_OK;
}

int MD_SingleStage::Initialize()
{	
	ostringstream command;
	string answer;
	int status = 0;

// Create new properties when select group or preset

	// Step size
   CPropertyAction* pAct = new CPropertyAction (this, &MD_SingleStage::OnStepSize);
   status = CreateProperty("Step Size", "1.0", MM::Float, false, pAct);
   if (status != DEVICE_OK)
      return status;

   // Speed
   pAct = new CPropertyAction (this, &MD_SingleStage::OnSpeed);
   status = CreateProperty("Speed", "1000.0", MM::Float, false, pAct);
   if (status != DEVICE_OK)
      return status;

   // Accellaration time
   pAct = new CPropertyAction (this, &MD_SingleStage::OnAccelTime);
   status = CreateProperty("Accel. pattern", "2", MM::Float, false, pAct);
   if (status != DEVICE_OK)
      return status;
   
   status = UpdateStatus();
   if (status != DEVICE_OK)
      return status;
	
// check version
   status = ConfirmVersion();
      if (status != DEVICE_OK)
      return status;

	initializationStatus_ = true;
	return DEVICE_OK;
}

int MD_SingleStage::Shutdown()
{
	if (initializationStatus_)
	{
		initializationStatus_ = false;
	}
	return DEVICE_OK;
}

void MD_SingleStage::QueryPeripheralInventory()
{
	discoverableDevices_.clear();
	inventoryDeviceAddresses_.clear();
	inventoryDeviceIDs_.clear();

	ConfirmVersion();
}


bool MD_SingleStage::Busy()
{
	ostringstream command;
	string answer;
	int status = 0;

	command << "RDR";
	status = ExecuteCommand(command.str().c_str());
	if (status != DEVICE_OK)	
		return true;

	status = ReadMessage (answer);
	if (status != DEVICE_OK)		
		return true;	

	if(answer.substr(0, 3) == "RDR")
	{
		if( (answer.substr(6, 1) == "1") || (answer.substr(22, 1) == "1") )
		return true;
	}
	return false;
}


int MD_SingleStage::WaitForBusy(long waitTime)
{
	long time = waitTime;

	MM::TimeoutMs timeout(GetCurrentMMTime(), time);

	while( true == Busy() ) 
	{
		if (timeout.expired(GetCurrentMMTime())) 
			return ERR_TIMEOUT;
	}
	return DEVICE_OK;
}


int MD_SingleStage::ConfirmAnswer(std::string answer)				// check controller error code, there are 3 type of error code format
{
	std::string errorCode ("00");							
	if (answer.length() == 8)					//		eg: "ERS X 00"
		errorCode = answer.substr(6,2);	

	if (answer.length() == 16)					//		eg: "ERS X 00ERS Y 00"
	{
		errorCode = answer.substr(6,2);
		if (errorCode == "00")	errorCode = answer.substr(14,2);
	}
	if (answer.length()==6)						//			eg: "ABB 00"
		errorCode = answer.substr(4,2);

		if (errorCode == "00") return DEVICE_OK;
		if (errorCode == "02") return ERR_02_OPERATION_REFUSED_BY_PROGRAM_STOP;
		if (errorCode == "03") return ERR_03_NOT_ACCEPT_COMMAND;
		if (errorCode == "04") return ERR_04_OPERATION_REFUSED_BY_MOTOR_ROTATION;
		if (errorCode == "06") return ERR_06_PARAMETER_ERROR;
		if (errorCode == "07") return ERR_07_OPERATION_REFUSED_BY_MOTOR_STOP;
		if (errorCode == "08") return ERR_08_OPERATION_REFUSED_BY_PROGRAM_RUNNING;
		if (errorCode == "0B") return ERR_0B_FAIL_TO_READ_DATA;
		if (errorCode == "0C") return ERR_0C_CANT_FIND_REGISTERED_PROGRAM;
		if (errorCode == "0D") return ERR_0D_NO_RESPONSE;
		if (errorCode == "0E") return ERR_0E_SPEED_CAN_NOT_SET_DURING_ACCEL;
		if (errorCode == "0F") return ERR_0F_MOTOR_EXCITATION_OFF;
		if (errorCode == "50") return ERR_50_STEP_OUT_ERROR;
		if (errorCode == "51") return ERR_51_STOP_SIGNAL_INPUT;
		if (errorCode == "52") return ERR_52_STOP_SIGNAL_INPUT;
		if (errorCode == "53") return ERR_53_NOT_CONSTANT_MODE_IN_SPEED_SETTING;

	return DEVICE_OK;
}

int MD_SingleStage::SetPositionUm(double positionUm)
{
	long steps = (long) (positionUm / stepSize_um_) ;
	return SetPositionSteps( steps );
}

int MD_SingleStage::GetPositionUm(double& positionUm)
{
	long steps;
	int status;
	status = GetPositionSteps(steps);
	if (status != DEVICE_OK)	
		return status;

	positionUm = steps * stepSize_um_;

	return DEVICE_OK;

}
  
// Absolute move 
int MD_SingleStage::SetPositionSteps(long steps)
{
	int status = 0;
	ostringstream command;
	string answer;

	command << "ABA "<< controllerAxis_ << " " << steps;
	status = ExecuteCommand(command.str().c_str());
	if (status != DEVICE_OK)		return status;	

	status = ReadMessage (answer);
	if (status != DEVICE_OK)		return status;	

	status = ConfirmAnswer(answer);
	if (status != DEVICE_OK)		return status;

	double newPosition = steps * stepSize_um_;
	double distance_um = fabs(newPosition - position_um_);

	long timeOut	= (long) (distance_um / speed_mm_);

	WaitForBusy(timeOut);
	
	status = OnStagePositionChanged(newPosition);
	if (status != DEVICE_OK)	
		return status;	

   return DEVICE_OK;
}

// relative move
int MD_SingleStage::SetRelativePositionSteps(long steps)
{
	int status = 0;
	ostringstream command;
	string answer;

	command << "ICA "<< controllerAxis_ << " " << steps;
	
	status = ExecuteCommand(command.str().c_str());
	if (status != DEVICE_OK)		return status;

	status = ReadMessage (answer);
	if (status != DEVICE_OK)		return status;	

	status = ConfirmAnswer(answer);
	if (status != DEVICE_OK)		return status;

	double newPosition = steps * stepSize_um_;
	double distance_um = fabs(newPosition - position_um_);

	long timeOut	= (long) (distance_um / speed_mm_);

	WaitForBusy(timeOut);
	
	status = OnStagePositionChanged(newPosition);
	if (status != DEVICE_OK)	
		return status;	

	return DEVICE_OK;
}

// read position (logical position)
int MD_SingleStage::GetPositionSteps(long& steps)
{
	int status = 0;
	ostringstream command;
	string answer;
	
	command << "RLP X";

	status = ExecuteCommand(command.str().c_str());
	if (status != DEVICE_OK)		return status;

	status = ReadMessage (answer);
	if (status != DEVICE_OK)		return status;	

	std::string RLP;
	std::string xTerm;

	std::istringstream streamAnswer(answer);
	streamAnswer >> RLP >> xTerm >> steps;

	position_um_ = steps * stepSize_um_;

	return DEVICE_OK;
}

// set this position to be origin
int MD_SingleStage::SetOrigin()
{
	return DEVICE_UNSUPPORTED_COMMAND;
}

// private: send command
int MD_SingleStage::ExecuteCommand(const string& cmd)
{
	// send command
	int status;
	PurgeComPort(portName_1S.c_str());
 
	status = SendSerialCommand(portName_1S.c_str(), cmd.c_str(), "");
	if (status!= DEVICE_OK) 
		return status; 
	
	return DEVICE_OK;
}

// private: read response from serial port, reading end when timeout
int MD_SingleStage::ReadMessage(std::string& sMessage)
{
    // block/wait for acknowledge, or until we time out;
    unsigned int bufferLength = 80;
    unsigned char answer[80];
    memset(answer, 0, bufferLength);
    unsigned long read = 0;
    unsigned long startTime = GetClockTicksUs();	// return in micro second

    ostringstream osMessage;
    int status = DEVICE_OK;
    bool bRead = false;
    bool bTimeout = false; 
	unsigned long byteToRead;

    while (!bRead && !bTimeout && status == DEVICE_OK )
    {
        status = ReadFromComPort(portName_1S.c_str(), (unsigned char *)&answer[read], (unsigned int)(bufferLength-read), byteToRead);
       
    //    for (unsigned long index=0; index < byteToRead; index++)   { bRead = bRead || answer[read+index] == Term;  }		// if using termimator

        if (status == DEVICE_OK && byteToRead > 0)
        {
            // bRead = strchr((char*)&answer[read], Term) != NULL;			// if using terminator
            read += byteToRead; 
            if (bRead) break;
        }

		bTimeout = ((GetClockTicksUs() - startTime) / 1000) > answerTimeoutMs_;		// timeout condition
		// delay 1ms
		if (!bTimeout) CDeviceUtils::SleepMs(1);
    }

	sMessage = (char*) answer;
/*/
    for (unsigned long index = 0; index < read; index++)
    {
        sMessage[index] = answer[index];
		if (sAnswer[index] == Term) break;		// if using terminator
    }
/*/
    return DEVICE_OK;
}

/*/		// Autofocus funtions, MD500 does not support this function.
int MD_SingleStage::Autofocus(long param)
{
	// format the command
	ostringstream cmd;
//	cmd << "AF Z=";			command for auto focus stage??
	cmd << param ;

	string answer;
	int status = ExecuteCommand(cmd.str(), answer);
	if (status != DEVICE_OK)
		return status;

	istringstream is(answer);
	string outcome;
	is >> outcome;

//	if (outcome.compare(":A") == 0)			//command for auto focus ??
		return DEVICE_OK; // success!

	// figure out the error code
	int code;
	is >> code;
	return code;
}
/*/


//////////////////
// User interface: select and config comport

int MD_SingleStage::OnSelectPort_1S(MM::PropertyBase* pPropBase, MM::ActionType pActType)
{
	if (pActType == MM::BeforeGet)
	{
		pPropBase->Set(portName_1S.c_str());
	}
	else if (pActType == MM::AfterSet)
	{
		if (initializationStatus_)
		{
			pPropBase->Set(portName_1S.c_str());
			return ERR_PORT_CHANGE_FORBIDDEN;
		}
		pPropBase->Get(portName_1S);
	}
	return DEVICE_OK;
}

int MD_SingleStage::OnConfigPort_1S(MM::PropertyBase* pProp, MM::ActionType pAct)
{
   if (pAct == MM::BeforeGet)
   {
      pProp->Set("Operate");
   }
   else if (pAct == MM::AfterSet)
   {
      string request;
      pProp->Get(request);
      if (request == "GetInfo")
      {
         // Get Info and write to debug output:
      }
   }
   return DEVICE_OK;
}


// Issue a hard reset of the Ludl Controller and installed controller cards
int MD_SingleStage::OnResetPort_1S(MM::PropertyBase* pProp, MM::ActionType pAct)
{
   if (pAct == MM::BeforeGet)
   {
      pProp->Set("Operate");
   }
   else if (pAct == MM::AfterSet)
   {
      string request;
      pProp->Get(request);
      if (request == "Reset")
      {
         // Send the Reset Command
         const char* cmd = "RST";
         int status = SendSerialCommand(portName_1S.c_str(), cmd, "");
         CDeviceUtils::SleepMs (10);
		 if (status !=DEVICE_OK)
            return status;
         // TODO: Do we need to wait until the reset is completed?
      }
   }
   return DEVICE_OK;
}

// User interface: set control parameter

int MD_SingleStage::OnStepSize(MM::PropertyBase* pPropBase, MM::ActionType eActType)
{
	if (eActType == MM::BeforeGet)
	{
		pPropBase->Set(stepSize_um_);
	}
	else if (eActType == MM::AfterSet)
	{
		pPropBase->Get(stepSize_um_);
	}
	return DEVICE_OK;
}

int MD_SingleStage::OnSpeed(MM::PropertyBase* pPropBase, MM::ActionType eActType)
{
	if (eActType == MM::BeforeGet)
	{
		pPropBase->Set(speed_step_);
	}
	else if (eActType == MM::AfterSet)
	{
		pPropBase->Get(speed_step_);
	}
	// set moving speed
	ostringstream command;
	string answer;
	int status = 0;

	speed_mm_ = speed_step_ *stepSize_um_ /1000;

	if (speed_step_ > 0 && speed_step_ < 20000)
	{
		command << "SPD " << controllerAxis_ << " " << speed_step_;
		status = ExecuteCommand(command.str().c_str());
		if (status != DEVICE_OK)		return status;	
	
		status = ReadMessage (answer);
		if (status != DEVICE_OK)		return status;	

		status = ConfirmAnswer(answer);
		if (status != DEVICE_OK)		return status;	
	
		command.str("");
      CDeviceUtils::SleepMs (10);
		return DEVICE_OK;
	}
	else return ERR_06_PARAMETER_ERROR;
}

int MD_SingleStage::OnAccelTime(MM::PropertyBase* pPropBase, MM::ActionType eActType)
{
	if (eActType == MM::BeforeGet)
	{
		pPropBase->Set(accelTime_pattern_);
	}
	else if (eActType == MM::AfterSet)
	{
		pPropBase->Get(accelTime_pattern_);
	}
// set accelaration time
	ostringstream command;
	string answer;
	int status = 0;
	
	command << "SAP " << controllerAxis_ << " " << accelTime_pattern_;
	status = ExecuteCommand(command.str().c_str());
	if (status != DEVICE_OK)			return status;

	status = ReadMessage (answer);
	if (status != DEVICE_OK)		return status;	

	status = ConfirmAnswer(answer);
	if (status != DEVICE_OK)			return status;

	command.str("");
   CDeviceUtils::SleepMs (10);
	return DEVICE_OK;
}

int MD_SingleStage::OnPosition(MM::PropertyBase* pPropBase, MM::ActionType eActType)
{
   	if (eActType == MM::BeforeGet)
	{
		pPropBase->Set(position_um_);
	}
	else if (eActType == MM::AfterSet)
	{
		pPropBase->Get(position_um_);
	}
	return DEVICE_OK;
	
}


int MD_SingleStage::OnControllerAxis(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		pProp->Set(controllerAxis_.c_str());
	}
	else if (eAct == MM::AfterSet)
	{
		string axis;
		pProp->Get(axis);
		if (axis == "X" || axis == "Y")
			controllerAxis_ = axis;
	}
	return DEVICE_OK;
}

/*/
int MD_SingleStage::OnAutofocus(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{

	}
	else if (eAct == MM::AfterSet)
	{
		long param;
		pProp->Get(param);

		return Autofocus(param);
	}

	return DEVICE_OK;
}
/*/

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

//-----------------------------------------------------------------------------
//........... Class2: Two stages controller....................................
//-----------------------------------------------------------------------------
MD_TwoStages::MD_TwoStages() :

initializationStatus_	(false),
stepSize_umX_			(1),
stepSize_umY_			(1),
speed_stepX_			(1000),	//pps
speed_stepY_			(1000),	//pps
accelTime_patternX_		(2),	// pattern 1-4
accelTime_patternY_		(2),	// pattern 1-4
answerTimeoutMs_		(20)	// answer timeout is 100ms

{
	InitializeDefaultErrorMessages();

	// custom error messages:
	SetErrorText(ERR_DEBUG, "debug");		// for debug

	SetErrorText(ERR_UNRECOGNIZED_ANSWER,	"Can't recognize answer");
	SetErrorText(ERR_HOMING,				"Home search failed");
	SetErrorText(ERR_TIMEOUT,				"Time out");

	SetErrorText(ERR_02_OPERATION_REFUSED_BY_PROGRAM_STOP,		"The operation is refused due to program stop");
	SetErrorText(ERR_03_NOT_ACCEPT_COMMAND,						"The command execution cannot be accepted");
	SetErrorText(ERR_04_OPERATION_REFUSED_BY_MOTOR_ROTATION,	"The operation is refused due to motor rotation");
	SetErrorText(ERR_06_PARAMETER_ERROR,						"Parameter error");
	SetErrorText(ERR_07_OPERATION_REFUSED_BY_MOTOR_STOP,		"The operation is refused due to motor stop");
	SetErrorText(ERR_08_OPERATION_REFUSED_BY_PROGRAM_RUNNING,	"The operation is refused due to program running");
	SetErrorText(ERR_0B_FAIL_TO_READ_DATA,						"Failed to read Data");
	SetErrorText(ERR_0C_CANT_FIND_REGISTERED_PROGRAM,			"The registered program cannot be found");
	SetErrorText(ERR_0D_NO_RESPONSE,							"No response");
	SetErrorText(ERR_0E_SPEED_CAN_NOT_SET_DURING_ACCEL,			"Speed cannot be set during motor rotation in S-curve accel/decel");
	SetErrorText(ERR_0F_MOTOR_EXCITATION_OFF,					"Motor excitation off");
	SetErrorText(ERR_50_STEP_OUT_ERROR,							"Step out error occurs");
	SetErrorText(ERR_51_STOP_SIGNAL_INPUT,						"Stop signal is being inputted");
	SetErrorText(ERR_52_STOP_SIGNAL_INPUT,						"Stop signal is being inputted");
	SetErrorText(ERR_53_NOT_CONSTANT_MODE_IN_SPEED_SETTING,		"Mode is not Constant in Speed setting in interpolation driving");

	// Name, read-only (RO)
	CreateProperty(MM::g_Keyword_Name, g_ChuoSeikiTwoStagesDeviceName, MM::String, true);

	// Description, RO
	CreateProperty(MM::g_Keyword_Description, "ChuoSeiki MD5000 2Axes driver adapter", MM::String, true);

	// select Port:
	CPropertyAction* pActType = new CPropertyAction(this, &MD_TwoStages::OnSelectPort_2S);
	CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pActType, true);

	AddAllowedValue(g_ChuoSeiki_Accelration_Time_PatternX, "1");
	AddAllowedValue(g_ChuoSeiki_Accelration_Time_PatternX, "2");
	AddAllowedValue(g_ChuoSeiki_Accelration_Time_PatternX, "3");
	AddAllowedValue(g_ChuoSeiki_Accelration_Time_PatternX, "4");

	AddAllowedValue(g_ChuoSeiki_Accelration_Time_PatternY, "1");
	AddAllowedValue(g_ChuoSeiki_Accelration_Time_PatternY, "2");
	AddAllowedValue(g_ChuoSeiki_Accelration_Time_PatternY, "3");
	AddAllowedValue(g_ChuoSeiki_Accelration_Time_PatternY, "4");

	controllerAxisX_ = "X";
	controllerAxisY_ = "Y";

}

MD_TwoStages::~MD_TwoStages()
{
 if (initializationStatus_)
 {
      initializationStatus_ = false;
 }
	Shutdown();
}


void MD_TwoStages::GetName(char* Name) const
{
	CDeviceUtils::CopyLimitedString(Name, g_ChuoSeikiTwoStagesDeviceName);
}

int MD_TwoStages::ConfirmVersion()
{
	int status;
	PurgeComPort(portName_2S.c_str());
	std::string version;

	status= ExecuteCommand("RVR");
	if (status!= DEVICE_OK) 
		return status;
	
	status= ReadMessage(version);
	if (status != DEVICE_OK) 
		return status;
	
	if (version.substr(0,3) != "RVR" ||version == "" ) 
		return ERR_UNRECOGNIZED_ANSWER;

	return DEVICE_OK;
}

int MD_TwoStages::Initialize()
{
	int status = 0;

// Create new properties when select group or preset

	// Step size X
	CPropertyAction* pAct = new CPropertyAction (this, &MD_TwoStages::OnStepSizeX);
	status = CreateProperty("X Step Size", "1.0", MM::Float, false, pAct);
	if (status != DEVICE_OK)
		return status;
    
   // Speed X
	pAct = new CPropertyAction (this, &MD_TwoStages::OnSpeedX);
	status = CreateProperty("X Speed", "1000.0", MM::Float, false, pAct);
	if (status != DEVICE_OK)
		return status;

   // Accellaration time X
	pAct = new CPropertyAction (this, &MD_TwoStages::OnAccelTimeX);
	status = CreateProperty("X Accel. pattern", "2", MM::Float, false, pAct);
	if (status != DEVICE_OK)
		return status;
   
	// Step size Y
	pAct = new CPropertyAction (this, &MD_TwoStages::OnStepSizeY);
	status = CreateProperty("Y Step Size", "1.0", MM::Float, false, pAct);
	if (status != DEVICE_OK)
		return status;

   // Speed Y
	pAct = new CPropertyAction (this, &MD_TwoStages::OnSpeedY);
	status = CreateProperty("Y Speed", "1000.0", MM::Float, false, pAct);
	if (status != DEVICE_OK)
		return status;

   // Accellaration time Y
	pAct = new CPropertyAction (this, &MD_TwoStages::OnAccelTimeY);
	status = CreateProperty("Y Accel. pattern", "2", MM::Float, false, pAct);
	if (status != DEVICE_OK)
		return status;

	status = UpdateStatus();
	if (status != DEVICE_OK)
		return status;

// confirm version

	status = ConfirmVersion();
	if (status != DEVICE_OK)
		return status;

	initializationStatus_ = true;
	return DEVICE_OK;
}

int MD_TwoStages::Shutdown()
{
	if (initializationStatus_)
	{
		initializationStatus_ = false;
	}

	return DEVICE_OK;
}

bool MD_TwoStages::Busy()
{
	ostringstream command;
	string answer;
	int status = 0;

	command << "RDR";
	status = ExecuteCommand(command.str().c_str());

	status = ReadMessage(answer);
	if (status != DEVICE_OK)		
		return false;
	status = ConfirmAnswer(answer);
	if (status != DEVICE_OK)		
		return false;

	if(answer.substr(0, 3) == "RDR")
	{
		if( (answer.substr(6, 1) == "1") || (answer.substr(22, 1) == "1") )
			return true;
	}
	command.str("");
	return false;
}


int MD_TwoStages::WaitForBusy(long waitTime)
{
	long time = waitTime;

	MM::TimeoutMs timeout(GetCurrentMMTime(), time);

	while( true == Busy() ) 
	{
		if (timeout.expired(GetCurrentMMTime())) 
			return ERR_TIMEOUT;
	}
	return DEVICE_OK;
}


int MD_TwoStages::ConfirmAnswer(std::string answer)		// check controller error code, there are 3 type of error code format
{
	std::string errorCode ("00");						
	if (answer.length() == 8)							//		eg: "ERS X 00"
		errorCode = answer.substr(6,2);	

	if (answer.length() == 16)							//		eg: "ERS X 00ERS Y 00"
	{
		errorCode = answer.substr(6,2);
		if (errorCode == "00")	errorCode = answer.substr(14,2);
	}
	if (answer.length()==6)								//			eg: "ABB 00"
		errorCode = answer.substr(4,2);

		if (errorCode == "00") return DEVICE_OK;
		if (errorCode == "02") return ERR_02_OPERATION_REFUSED_BY_PROGRAM_STOP;
		if (errorCode == "03") return ERR_03_NOT_ACCEPT_COMMAND;
		if (errorCode == "04") return ERR_04_OPERATION_REFUSED_BY_MOTOR_ROTATION;
		if (errorCode == "06") return ERR_06_PARAMETER_ERROR;
		if (errorCode == "07") return ERR_07_OPERATION_REFUSED_BY_MOTOR_STOP;
		if (errorCode == "08") return ERR_08_OPERATION_REFUSED_BY_PROGRAM_RUNNING;
		if (errorCode == "0B") return ERR_0B_FAIL_TO_READ_DATA;
		if (errorCode == "0C") return ERR_0C_CANT_FIND_REGISTERED_PROGRAM;
		if (errorCode == "0D") return ERR_0D_NO_RESPONSE;
		if (errorCode == "0E") return ERR_0E_SPEED_CAN_NOT_SET_DURING_ACCEL;
		if (errorCode == "0F") return ERR_0F_MOTOR_EXCITATION_OFF;
		if (errorCode == "50") return ERR_50_STEP_OUT_ERROR;
		if (errorCode == "51") return ERR_51_STOP_SIGNAL_INPUT;
		if (errorCode == "52") return ERR_52_STOP_SIGNAL_INPUT;
		if (errorCode == "53") return ERR_53_NOT_CONSTANT_MODE_IN_SPEED_SETTING;

	return DEVICE_OK;
}


int MD_TwoStages::SetPositionSteps(long x, long y)
{
	int status = 0;
	ostringstream command;
	string answer;

	if(bOnHome == true)
	{
		bOnHome = false;
		x=0;
		y=0;
		return DEVICE_OK;
	}
	else
	{
		command << "ABA "<< controllerAxisX_ << " " << x << "," << controllerAxisY_ << " " << y;
		status = ExecuteCommand(command.str().c_str());

		status = ReadMessage(answer);
		if (status != DEVICE_OK)		return status;
		status = ConfirmAnswer(answer);
		if (status != DEVICE_OK)		return status;

		command.str("");
	}

	double newPositionX = x * stepSize_umX_;
	double newPositionY = y * stepSize_umX_;
	double distance_umX = fabs(newPositionX - position_umX_);
	double distance_umY = fabs(newPositionY - position_umY_);

	long timeX	= (long) (distance_umX / speed_mmX_);
	long timeY	= (long) (distance_umY / speed_mmY_);

	long timeOut = timeX;
	if (timeX < timeY) {timeOut = timeY;}

	WaitForBusy(timeOut);
	
	position_umX_ = x * stepSize_umX_;
	position_umY_ = y * stepSize_umY_;

	status = OnXYStagePositionChanged(position_umX_ , position_umY_ );
	if (status != DEVICE_OK)	
		return status;	


   return DEVICE_OK;
}


int MD_TwoStages::SetRelativePositionSteps(long x, long y)
{
	ostringstream command;
	int status;
	string answer;

	position_umX_ = x * stepSize_umX_;
	position_umY_ = y * stepSize_umY_;

	PurgeComPort(portName_2S.c_str());

	//Move Command
	command << "ICA "<< controllerAxisX_ << " " << x << "," << controllerAxisY_ << " " << y;
	status = ExecuteCommand(command.str().c_str());

	status = ReadMessage(answer);
	if (status != DEVICE_OK)		return status;
	status = ConfirmAnswer(answer);
	if (status != DEVICE_OK)		return status;

	command.str("");
	
	status = OnXYStagePositionChanged(position_umX_, position_umY_);
	if (status != DEVICE_OK)
		return status;

	return DEVICE_OK;
}


int MD_TwoStages::GetPositionSteps(long& xValue, long& yValue)
{
	ostringstream command;
	int status = 0;
	std::string answer;

	command << "RLP";
	status = ExecuteCommand(command.str().c_str());
	if (status != DEVICE_OK)		return status;	
	status = ReadMessage(answer);
	if (status != DEVICE_OK)		return status;

	// if (answer.length()<1) return ERR_0B_FAIL_TO_READ_DATA;
	// return position format: "RLP X -1234,Y 5678"

	std::istringstream streamAnswer(answer.c_str());

	std::string RLP;		
	std::string xTerm;
	std::string yTerm;

	streamAnswer >> RLP >> xTerm >> xValue >> yTerm >> yValue;			// cut the answer into parts, take x, y value
	
	position_umX_ = xValue * stepSize_umX_;
	position_umY_ = yValue * stepSize_umY_;

	return DEVICE_OK;
}


int MD_TwoStages::SetOrigin()
{
	return DEVICE_OK;
}


int MD_TwoStages::Home()
{
	ostringstream command;
	int status = 0;
	string answer("");

	command << "HMB X,Y";				// 2 axes home search
	
	status = ExecuteCommand(command.str().c_str());
	if (status != DEVICE_OK)		return status;


//	while (answer.length()<1)	{			// wait until there is response

	status = ReadMessage(answer);
//      CDeviceUtils::SleepMs(1);
//	}

	if (status != DEVICE_OK)		
		return status;

	status = ConfirmAnswer(answer);
	if (status != DEVICE_OK)		return status;

	command.str("");

	position_umX_ = 0;
	position_umY_ = 0;

	bOnHome = true;

	return DEVICE_OK;
}


int MD_TwoStages::Stop()
{
	ostringstream command;
	int status;
	string answer;

	command << "SST X,Y";				// 2 axes decelation stop
	
	status = ExecuteCommand(command.str().c_str());
	if (status != DEVICE_OK)		return status;
	
	status = ReadMessage(answer);
	if (status != DEVICE_OK)		return status;
	status = ConfirmAnswer(answer);
	if (status != DEVICE_OK)		return status;

	command.str("");

	return DEVICE_OK;
}

int MD_TwoStages::ExecuteCommand(const string& cmd)
{
	// send command
	int status;
	PurgeComPort(portName_2S.c_str());
 
	status = SendSerialCommand(portName_2S.c_str(), cmd.c_str(), "");
//	Sleep (10);
	if (status!= DEVICE_OK) 
		return status; 
	
	return DEVICE_OK;
}

int MD_TwoStages::ReadMessage(std::string& sMessage)
{
    // block/wait for acknowledge, or until we time out;
    unsigned int bufferLength = 80;
    unsigned char answer[80];
    memset(answer, 0, bufferLength);
    unsigned long read = 0;
    unsigned long startTime = GetClockTicksUs();	// return in micro second

    ostringstream osMessage;
    int status = DEVICE_OK;
    bool bRead = false;
    bool bTimeout = false; 
	unsigned long byteToRead;

    while (!bRead && !bTimeout && status == DEVICE_OK )
    {
        status = ReadFromComPort(portName_2S.c_str(), (unsigned char *)&answer[read], (unsigned int)(bufferLength-read), byteToRead);   
//    for (unsigned long index=0; index < byteToRead; index++)   { bRead = bRead || answer[read+index] == Term;  }		// if using term

        if (status == DEVICE_OK && byteToRead > 0)
        {// bRead = strchr((char*)&sAnswer[read], Term) != NULL; // if use term
            read += byteToRead;
            if (bRead) break;
        }
		bTimeout = ((GetClockTicksUs() - startTime) / 1000) > answerTimeoutMs_;
		// delay 1ms = time to read 1 char, total timeout is 20ms -> can read upto 20 char
		if (!bTimeout) CDeviceUtils::SleepMs (1);  // if you want to sleep with smaller time, use select() and timeval
    }

	sMessage = (char*) answer;

//    for (unsigned long index = 0; index < read; index++)    {
//        sMessage[index] = answer[index];
//     if (sAnswer[index] == Term) break;		// if using term    }

    return DEVICE_OK;
}


///////////////////
// Action handlers
int MD_TwoStages::OnSelectPort_2S(MM::PropertyBase* pPropBase, MM::ActionType pActType)
{
	if (pActType == MM::BeforeGet)
		pPropBase->Set(portName_2S.c_str());

	else if (pActType == MM::AfterSet)
	{
		if (initializationStatus_)
		{
			pPropBase->Set(portName_2S.c_str());
			return ERR_PORT_CHANGE_FORBIDDEN;
		}
		pPropBase->Get(portName_2S);
	}
	return DEVICE_OK;
}

int MD_TwoStages::OnConfigPort_2S(MM::PropertyBase* pProp, MM::ActionType pAct)
{
   if (pAct == MM::BeforeGet)
      pProp->Set("Operate");

   else if (pAct == MM::AfterSet)
   {
      string request;
      pProp->Get(request);
      if (request == "GetInfo")
      {
         // Get Info and write to debug output:
      }
   }
   return DEVICE_OK;
}


// Issue a hard reset of the Ludl Controller and installed controller cards
int MD_TwoStages::OnResetPort_2S(MM::PropertyBase* pProp, MM::ActionType pAct)
{
   if (pAct == MM::BeforeGet)
      pProp->Set("Operate");

   else if (pAct == MM::AfterSet)
   {
      string request;
      pProp->Get(request);
      if (request == "Reset")
      {
         // Send the Reset Command
         const char* cmd = "RST";
         int status = SendSerialCommand(portName_2S.c_str(), cmd, "");
         CDeviceUtils::SleepMs (10);
		 if (status !=DEVICE_OK)
            return status;
         // TODO: Do we need to wait until the reset is completed?
      }
   }
   return DEVICE_OK;
}

int MD_TwoStages::OnStepSizeX(MM::PropertyBase* pPropBase, MM::ActionType eActType)
{
	if (eActType == MM::BeforeGet)
		pPropBase->Set(stepSize_umX_);

	else if (eActType == MM::AfterSet)
		pPropBase->Get(stepSize_umX_);

	return DEVICE_OK;
}


int MD_TwoStages::OnStepSizeY(MM::PropertyBase* pPropBase, MM::ActionType eActType)
{
	if (eActType == MM::BeforeGet)
		pPropBase->Set(stepSize_umY_);

	else if (eActType == MM::AfterSet)
		pPropBase->Get(stepSize_umY_);

	return DEVICE_OK;

}


int MD_TwoStages::OnSpeedX(MM::PropertyBase* pPropBase, MM::ActionType eActType)
{
	ostringstream command;
	string answer;
	int status = 0;
	
	if (eActType == MM::BeforeGet)
		pPropBase->Set(speed_stepX_);

	else if (eActType == MM::AfterSet)
		pPropBase->Get(speed_stepX_);

	// Setup control speed X
	if (speed_stepX_ > 0 && speed_stepX_ < 20000)
	{
		speed_mmX_ = speed_stepX_ *stepSize_umX_ /1000;
		command << "SPD X "<< speed_stepX_;
		status = ExecuteCommand(command.str().c_str());
		if (status != DEVICE_OK)		return status;
		status = ReadMessage(answer);
		if (status != DEVICE_OK)		return status;
		status = ConfirmAnswer(answer);
		if (status != DEVICE_OK)		return status;
	
		return DEVICE_OK;
	}
	else return ERR_06_PARAMETER_ERROR;
}


int MD_TwoStages::OnAccelTimeX(MM::PropertyBase* pPropBase, MM::ActionType eActType)
{
	ostringstream command;
	string answer;
	int status = 0;

	if (eActType == MM::BeforeGet)
		pPropBase->Set(accelTime_patternX_);

	else if (eActType == MM::AfterSet)
		pPropBase->Get(accelTime_patternX_);
	
	command << "SAP X " << accelTime_patternX_;
	status = ExecuteCommand(command.str().c_str());
	
	status = ReadMessage(answer);
	if (status != DEVICE_OK)		return status;
	status = ConfirmAnswer(answer);
	if (status != DEVICE_OK)		return status;
	
	return DEVICE_OK;
}

int MD_TwoStages::OnSpeedY(MM::PropertyBase* pPropBase, MM::ActionType eActType)
{
	
	ostringstream command;
	string answer;
	int status = 0;

	if (eActType == MM::BeforeGet)
		pPropBase->Set(speed_stepY_);

	else if (eActType == MM::AfterSet)
		pPropBase->Get(speed_stepY_);

	if (speed_stepY_ > 0 && speed_stepY_ < 20000)
	{
		speed_mmX_ = speed_stepY_ *stepSize_umY_ /1000;
		command << "SPD Y "<< speed_stepY_;
		status = ExecuteCommand(command.str().c_str());
		if (status != DEVICE_OK)		return status;
		status = ReadMessage(answer);
		if (status != DEVICE_OK)		return status;
		status = ConfirmAnswer(answer);
		if (status != DEVICE_OK)		return status;
	
		return DEVICE_OK;
	}
	else return ERR_06_PARAMETER_ERROR;
}



int MD_TwoStages::OnAccelTimeY(MM::PropertyBase* pPropBase, MM::ActionType eActType)
{
	ostringstream command;
	string answer;
	int status = 0;

	if (eActType == MM::BeforeGet)
		pPropBase->Set(accelTime_patternY_);

	else if (eActType == MM::AfterSet)
		pPropBase->Get(accelTime_patternY_);

	command << "SAP Y " << accelTime_patternY_;
	status = ExecuteCommand(command.str().c_str());
	
	status = ReadMessage(answer);
	if (status != DEVICE_OK)		return status;
	status = ConfirmAnswer(answer);
	if (status != DEVICE_OK)		return status;
	

	return DEVICE_OK;
}

