
////////////////////////////////////////////////////////////////////////////////////
// FILE:          ChuoSeiki_QT_XYZStage.cpp											
// PROJECT:       Micro-Manager														
// SUBSYSTEM:     DeviceAdapters													
//----------------------------------------------------------------------------------
// DESCRIPTION:   ChuoSeiki_QT_Controller device adapter.							
//																					
// AUTHOR:        Okumura Daisuke, d-okumura@chuo.co.jp, 
//									Code for QT commands		
//				  Duong Quang Anh, duong-anh@chuo.co.jp, 
//									Code for Devices registeration, Error checking
//																				
// COPYRIGHT:     CHUO Precision Industrial Co. Ltd., Tokyo, Japan, 2018			
//																					
// LICENSE:       This library is free software; you can redistribute it and/or		
//                modify it under the terms of the GNU Lesser General Public		
//                License as published by the Free Software Foundation.				
//																					
//                You should have received a copy of the GNU Lesser General Public	
//                License along with the source distribution; if not, write to		
//                the Free Software Foundation, Inc., 59 Temple Place, Suite 330,	
//                Boston, MA  02111-1307  USA										
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

#include "ChuoSeiki_QT_XYZ.h"
#include "../../MMDevice/ModuleInterface.h"
#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/DeviceUtils.h"

#include <cstdio>
#include <string>
#include <math.h>
#include <sstream>
#include <algorithm>

// XYStage
const char* g_ChuoSeikiXYStageDeviceName = "ChuoSeiki_QT 2-Axis";

// single axis stage
const char* g_ChuoSeikiZStageDeviceName = "ChuoSeiki_QT 1-Axis";


const char* g_ChuoSeikiController = "ChuoSeiki QT Controller";
const char* g_ChuoSeiki_Controller_Axis = "ChuoSeikiAxisName";

const char* g_Acceleration_TimeX = "X Axis Acceleration time ms";
const char* g_Acceleration_TimeY = "Y Axis Acceleration time ms";
const char* g_Acceleration_Time = "Acceleration time ms";

using namespace std;

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
//	RegisterDevice(g_ChuoSeikiController, MM::GenericDevice, "HUB");
	RegisterDevice(g_ChuoSeikiXYStageDeviceName, MM::XYStageDevice, "XY stages");
	RegisterDevice(g_ChuoSeikiZStageDeviceName, MM::StageDevice, "Z stage");
}                                                                            
                                                                             
MODULE_API MM::Device* CreateDevice(const char* deviceName)                  
{
	if (deviceName == 0)	return 0;

	if (strcmp(deviceName, g_ChuoSeikiXYStageDeviceName) == 0) 
	{
		ChuoSeikiXYStage* xystage = new ChuoSeikiXYStage(); 
		return xystage;
	} 

	if (strcmp(deviceName, g_ChuoSeikiZStageDeviceName) == 0) 
	{
		ChuoSeikiZStage* zstage = new ChuoSeikiZStage(); 
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
	const unsigned int bufSize = 255;
	unsigned char clear[bufSize];
	unsigned long read = bufSize;
	int ret;
	while (read == bufSize) 
	{
		ret = core.ReadFromSerial(&device, port, clear, bufSize, read);
		if (ret != DEVICE_OK)
			return ret;
	}
	return DEVICE_OK;
}

//-----------------------------------------------------------------------------
//........... Class1: XY stage controller..................................
//-----------------------------------------------------------------------------

ChuoSeikiXYStage::ChuoSeikiXYStage() :

initialized_		(false),
stepSize_umX_		(1),
stepSize_umY_		(1),
speedHigh_stepX_	(2000),	//pps
speedLow_stepX_		(500),	//pps
accelTimeX_			(100),
speedHigh_stepY_	(2000),	//pps
speedLow_stepY_		(500),	//pps
accelTimeY_			(100)

{
	InitializeDefaultErrorMessages();  // default error messages
	
	// define custom error messages
	SetErrorText (ERR_PORT_CHANGE_FORBIDDEN,"Can't change serial port!"	);
	SetErrorText (ERR_UNRECOGNIZED_ANSWER,	"Unrecognized answer!");
	SetErrorText (ERR_NO_ANSWER,			"No response!");
	SetErrorText (ERR_NO_CONTROLLER,		"Please add the ChuoSeiki Controller device first!");
	SetErrorText (ERR_HOMING,				"Homing error! Please clear error using control pad!");
	SetErrorText (ERR_TIMEOUT,				"Timeout error!");

	SetErrorText(ERR_CONTROLER_0,	"Not In Command Recive Mode!");
	SetErrorText(ERR_CONTROLER_1,	"Command Syntax Error!");
	SetErrorText(ERR_CONTROLER_2,	"Error: Overflow Value/Read Only Parameter!");
	SetErrorText(ERR_CONTROLER_3,	"Command Set Axis Error/Mode Setting Error!");
	SetErrorText(ERR_CONTROLER_4,	"Command Char Over!");
	SetErrorText(ERR_CONTROLER_5,	"Stop Command Sent To Stopped Stage!");
	SetErrorText(ERR_CONTROLER_6,	"Limit Detected!");
	SetErrorText(ERR_CONTROLER_7,	"Emergency Stop Detected!");
	SetErrorText(ERR_CONTROLER_8,	"Com Error/Driver Error!");
	SetErrorText(ERR_CONTROLER_9,	"Other Errors!");


	// Name, read-only (RO)
	CreateProperty(MM::g_Keyword_Name, g_ChuoSeikiXYStageDeviceName, MM::String, true);

	// Description, RO
	CreateProperty(MM::g_Keyword_Description, "ChuoSeiki 2-stage driver adapter", MM::String, true);

		// select Port:
	CPropertyAction* pActType = new CPropertyAction(this, &ChuoSeikiXYStage::OnSelectPort_XY);
	CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pActType, true);

	controllerAxisX_ = "A";
	controllerAxisY_ = "B";

}

ChuoSeikiXYStage::~ChuoSeikiXYStage()
{
	if (initialized_)
	{
		initialized_ = false;
	}
	Shutdown();
}

MM::DeviceDetectionStatus ChuoSeikiXYStage::DetectDevice(void)
{
	// all conditions must be satisfied...
	MM::DeviceDetectionStatus result = MM::Misconfigured;

	try	
	{
		std::string lowercase = portName_XY;
		for( std::string::iterator i = lowercase.begin(); i != lowercase.end(); ++i) 
		{
			*i = (char)tolower(*i);
		}
		if( lowercase.length() > 0 &&  lowercase.compare("undefined") !=0  && lowercase.compare("unknown") !=0 )	
		{
			// the port property seems correct, so give it a try
			result = MM::CanNotCommunicate;

			// device specific default communication parameters
			GetCoreCallback()->SetDeviceProperty(portName_XY.c_str(), MM::g_Keyword_BaudRate,	"9600"	);
			GetCoreCallback()->SetDeviceProperty(portName_XY.c_str(), MM::g_Keyword_StopBits,	"1"		);
			GetCoreCallback()->SetDeviceProperty(portName_XY.c_str(), "AnswerTimeout",			"600.0"	);
			GetCoreCallback()->SetDeviceProperty(portName_XY.c_str(), "DelayBetweenCharsMs",	"0.0"	);

			// get portname for 1 stage controller
			MM::Device* device = GetCoreCallback()->GetDevice(this, portName_XY.c_str());
			// set port parameters
			device->Initialize();
			// check version
			int qvStatus = this->ConfirmComm();
			if( DEVICE_OK != qvStatus ) 
				LogMessageCode(qvStatus,true);
			else
			 // to succeed must reach here....
				result = MM::CanCommunicate;
			// device shutdown, return default answertimeout	
			device->Shutdown();
			 // always restore the AnswerTimeout to the default
			GetCoreCallback()->SetDeviceProperty(portName_XY.c_str(), "AnswerTimeout", "2000.0");
		}
	}
	catch(...) 
	{
		LogMessage("Exception in DetectDevice!",false);
	}
	return result;
}
// private functions

int ChuoSeikiXYStage::ConfirmComm()
{
	int ret;
	string answer;

	PurgeComPort(portName_XY.c_str());
	ret= SendSerialCommand(portName_XY.c_str(), "?:CHUOSEIKI", "\r\n");		// it will return the phase after "?:"
	if (ret!= DEVICE_OK) 
		return ret;

	ret= GetSerialAnswer(portName_XY.c_str(), "\r\n", answer);
	if (ret != DEVICE_OK) 
		return ret;
	
	if( answer.substr(0, 9).compare("CHUOSEIKI") != 0)
	{
		return ERR_UNRECOGNIZED_ANSWER;
	}

	ret = SendSerialCommand(portName_XY.c_str(), "X:1", "\r\n");  // request feedback "\r\n" after send control command
	if (ret != DEVICE_OK)		
		return ret;

	ret = GetSerialAnswer(portName_XY.c_str(), "\r\n", answer);
	if (ret != DEVICE_OK)		
		return ret;

	return DEVICE_OK;
}

void ChuoSeikiXYStage::GetName(char* Name) const
{
	CDeviceUtils::CopyLimitedString(Name, g_ChuoSeikiXYStageDeviceName);
}


int ChuoSeikiXYStage::Initialize()
{
	int status = 0;

	//Step Size X
	CPropertyAction* pAct = new CPropertyAction(this, &ChuoSeikiXYStage::OnStepSizeX);
	status = CreateProperty("X-Axis StepSize: um", "1", MM::Float, false, pAct);
	if (status != DEVICE_OK)
      return status;

	//Step Size Y
	pAct = new CPropertyAction(this, &ChuoSeikiXYStage::OnStepSizeY);
	status = CreateProperty("Y-Axis StepSize: um", "1", MM::Float, false, pAct);
	if (status != DEVICE_OK)
      return status;

	//Speed High X
	pAct = new CPropertyAction(this, &ChuoSeikiXYStage::OnSpeedXHigh);
	status = CreateProperty("X-Axis HighSpeed: pps", "2000", MM::Float, false, pAct);
	if (status != DEVICE_OK)
      return status;

	//Speed Low X
	pAct = new CPropertyAction(this, &ChuoSeikiXYStage::OnSpeedXLow);
	status = CreateProperty("X-Axis LowSpeed: pps", "500", MM::Float, false, pAct);
	if (status != DEVICE_OK)
      return status;

	//Accel Time X
	pAct = new CPropertyAction(this, &ChuoSeikiXYStage::OnAccelTimeX);
	status = CreateProperty("X-Axis AcceleratingTime: msec", "100", MM::Float, false, pAct);
	if (status != DEVICE_OK)
      return status;

	//Speed High Y
	pAct = new CPropertyAction(this, &ChuoSeikiXYStage::OnSpeedYHigh);
	status = CreateProperty("Y-Axis HighSpeed: pps", "2000", MM::Float, false, pAct);
	if (status != DEVICE_OK)
      return status;

	//Speed Low Y
	pAct = new CPropertyAction(this, &ChuoSeikiXYStage::OnSpeedYLow);
	status = CreateProperty("Y-Axis LowSpeed: pps", "500", MM::Float, false, pAct);
	if (status != DEVICE_OK)
      return status;

	//Accel Time Y
	pAct = new CPropertyAction(this, &ChuoSeikiXYStage::OnAccelTimeY);
	status = CreateProperty("Y-Axis Accelerating Time: msec", "100", MM::Float, false, pAct);
	if (status != DEVICE_OK)
      return status;
	
	bHomeing = false;
	bLimitStop = false;

	status = ConfirmComm();
	if (status != DEVICE_OK)
		return status;

	status = UpdateStatus();
	if (status != DEVICE_OK)
		return status;

	initialized_ = true;
	return DEVICE_OK;
}

int ChuoSeikiXYStage::Shutdown()
{
	if (initialized_)
	{
		initialized_ = false;
	}
	return DEVICE_OK;
}

void ChuoSeikiXYStage::QueryPeripheralInventory()
{
	discoverableDevices_.clear();
	inventoryDeviceAddresses_.clear();
	inventoryDeviceIDs_.clear();

	ConfirmComm();
}

bool ChuoSeikiXYStage::Busy()
{
	ostringstream command;
	string answer;
	int ret = 0;

	command << "Q:" << controllerAxisX_ << "2" << controllerAxisY_ <<"2";		// request state of the stage, return (keyword X)(,)(keyword Y)
	ret = SendSerialCommand(portName_XY.c_str(), command.str().c_str(), "\r\n");
	command.str("");
	if (ret != DEVICE_OK)
		return true;

	ret = GetSerialAnswer(portName_XY.c_str(), "\r\n", answer);
	if (ret != DEVICE_OK)
		return true;

	if (answer.length() == 3)
	{
		if( (answer.substr(0, 1) == "D") || (answer.substr(2, 1) == "D") )		//D : stage is moving
		{
			return true;
		}
	}
	return false;
}


int ChuoSeikiXYStage::WaitForBusy(long Time)
{
	long time = Time;
	MM::TimeoutMs timeout(GetCurrentMMTime(), time);
	while( true == Busy() ) 
	{
		if (timeout.expired(GetCurrentMMTime())) 
		{
			return ERR_TIMEOUT;
		}
	}
	return DEVICE_OK;
}


int ChuoSeikiXYStage::ConfirmAnswer(std::string errorcode)
{
	if (errorcode.length()==2)
	{
		if (errorcode == "!0") return ERR_CONTROLER_0;
		if (errorcode == "!1") return ERR_CONTROLER_1;
		if (errorcode == "!2") return ERR_CONTROLER_2;
		if (errorcode == "!3") return ERR_CONTROLER_3;
		if (errorcode == "!4") return ERR_CONTROLER_4;
		if (errorcode == "!5") return ERR_CONTROLER_5;
		if (errorcode == "!6") return ERR_CONTROLER_6;
		if (errorcode == "!7") return ERR_CONTROLER_7;
		if (errorcode == "!8") return ERR_CONTROLER_8;
		if (errorcode == "!9") return ERR_CONTROLER_9;
		return ERR_UNRECOGNIZED_ANSWER;
	}
	else return DEVICE_OK;
}


int ChuoSeikiXYStage::SetPositionSteps(long x, long y)
{
	int ret = 0;
	ostringstream command;
	string answer;

	if(true == bHomeing)
	{
		bHomeing = false;
		x=0;
		y=0;
		return DEVICE_OK;
	}
	else
	{
   		//Absolute Move Command AGO
		command << "AGO:"<< controllerAxisX_ << x << controllerAxisY_ << y;
		ret = SendSerialCommand(portName_XY.c_str(), command.str().c_str(), "\r\n");
		command.str("");
		if (ret != DEVICE_OK)
			return ret;

		ret = GetSerialAnswer(portName_XY.c_str(), "\r\n", answer);
		if (ret != DEVICE_OK)
			return ret;

		ret = ConfirmAnswer(answer);
		if (ret != DEVICE_OK)
			return ret;
	}

	double newPosX = x * stepSize_umX_;
	double newPosY = y * stepSize_umX_;
	double distance_umX = fabs(newPosX - posX_um_);
	double distance_umY = fabs(newPosY - posY_um_);

	long timeX	= (long) (distance_umX / speedHigh_mmX_);
	long timeY	= (long) (distance_umY / speedHigh_mmY_);

	long timeOut = timeX;
	if(timeX < timeY)	
		timeOut  = timeY;

	WaitForBusy(timeOut);
	
	posX_um_ = x * stepSize_umX_;
	posY_um_ = y * stepSize_umY_;

	ret = OnXYStagePositionChanged(posX_um_ , posY_um_ );
	if (ret != DEVICE_OK)
		return ret;

   return DEVICE_OK;
}


int ChuoSeikiXYStage::SetRelativePositionSteps(long x, long y)
{
	ostringstream command;
	int ret;
	string answer;

	posX_um_ = x * stepSize_umX_;
	posY_um_ = y * stepSize_umY_;

	PurgeComPort(portName_XY.c_str());

	//Relative (step) Move Command MGO
	command << "MGO:"<< controllerAxisX_ << x << controllerAxisY_ << y;
	ret = SendSerialCommand(portName_XY.c_str(), command.str().c_str(), "\r\n");
	command.str("");
	if (ret != DEVICE_OK)
		return ret;

	ret = GetSerialAnswer(portName_XY.c_str(), "\r\n", answer);
	if (ret != DEVICE_OK)
		return ret;

	ret = ConfirmAnswer(answer);
	if (ret != DEVICE_OK)
		return ret;

	ret = OnXYStagePositionChanged(posX_um_, posY_um_);
	if (ret != DEVICE_OK)
		return ret;

	return DEVICE_OK;

}


int ChuoSeikiXYStage::GetPositionSteps(long& x, long& y)
{
	ostringstream command;
	int ret = 0;
	string answer;

	command << "Q:"<< controllerAxisX_ << "0" << controllerAxisY_ << "0";		// request positions and states
	ret = SendSerialCommand(portName_XY.c_str(), command.str().c_str(), "\r\n");
	command.str("");
	if (ret != DEVICE_OK)
			return ret;

	ret = GetSerialAnswer(portName_XY.c_str(), "\r\n", answer);
	if (ret != DEVICE_OK)
			return ret;

	if (answer.length() == 21)					// answer format: (+ or -)(8 digits)(stage A state keyword)(,)(+ or -)(8 digits)(stage B state keyword)
	{
		if( (answer.substr(9, 1) == "D") || (answer.substr(20, 1) == "D") )
		{	
			return GetPositionSteps(x,y);		// if stage is running, repeat
		}
		else if( answer.substr(9, 1) == "H" || answer.substr(20, 1) == "H" )
		{
			return ERR_HOMING;	
		}
		else
		{
			x = std::atol(answer.substr(0,9).c_str());
			y = std::atol(answer.substr(11,9).c_str());
			posX_um_ = x * stepSize_umX_;
			posY_um_ = y * stepSize_umY_;
			return DEVICE_OK;
		}
	}
	else return ERR_UNRECOGNIZED_ANSWER;
	
}


int ChuoSeikiXYStage::SetOrigin()
{
	return DEVICE_OK;
}


int ChuoSeikiXYStage::Home()
{
	ostringstream command;
	int ret = 0;
	string answer;

	PurgeComPort(portName_XY.c_str());

	command << "H:"<< controllerAxisX_ << controllerAxisY_;			// Home command
	ret = SendSerialCommand(portName_XY.c_str(), command.str().c_str(), "\r\n");
	command.str("");
	if (ret != DEVICE_OK) 
		return ret;

	ret = GetSerialAnswer(portName_XY.c_str(), "\r\n", answer);
	if (ret != DEVICE_OK) 
		return ret;


	ret = ConfirmAnswer(answer);
	if (ret != DEVICE_OK)
		return ret;

	posX_um_ = 0;
	posY_um_ = 0;
	bHomeing = true;

	return DEVICE_OK;
}


int ChuoSeikiXYStage::Stop()
{
	int ret;
	string answer;

	PurgeComPort(portName_XY.c_str());

	ret = SendSerialCommand(portName_XY.c_str(), "L:", "\r\n");		// stop all stages
	if (ret != DEVICE_OK) 
		return ret;

	ret = GetSerialAnswer(portName_XY.c_str(), "\r\n", answer);
	if (ret != DEVICE_OK) 
		return ret;

	ret = ConfirmAnswer(answer);
	if (ret != DEVICE_OK)
		return ret;

	return DEVICE_OK;
}



///////////////////////////////////////////////////////////////////////////////
// Action handlers

int ChuoSeikiXYStage::OnSelectPort_XY(MM::PropertyBase* pPropBase, MM::ActionType pActType)
{
	if (pActType == MM::BeforeGet)
	{
		pPropBase->Set(portName_XY.c_str());
	}
	else if (pActType == MM::AfterSet)
	{
		if (initialized_)
		{
			pPropBase->Set(portName_XY.c_str());
			return ERR_PORT_CHANGE_FORBIDDEN;
		}
		pPropBase->Get(portName_XY);
	}
	return DEVICE_OK;
}

int ChuoSeikiXYStage::OnConfigPort_XY(MM::PropertyBase* pProp, MM::ActionType pAct)
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
int ChuoSeikiXYStage::OnResetPort_XY(MM::PropertyBase* pProp, MM::ActionType pAct)
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
         int status = SendSerialCommand(portName_XY.c_str(), "RESTA", "\r\n");		// restart controller
         CDeviceUtils::SleepMs (10);
		 if (status !=DEVICE_OK)
            return status;
         // TODO: Do we need to wait until the reset is completed?
      }
   }
   return DEVICE_OK;
}

int ChuoSeikiXYStage::OnStepSizeX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		pProp->Set(stepSize_umX_);
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(stepSize_umX_);
	}
	return DEVICE_OK;
}


int ChuoSeikiXYStage::OnStepSizeY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		pProp->Set(stepSize_umY_);
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(stepSize_umY_);
	}

	return DEVICE_OK;
}


int ChuoSeikiXYStage::OnSpeedXHigh(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		pProp->Set(speedHigh_stepX_);
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(speedHigh_stepX_);
	}

	ostringstream command;
	int ret = 0;
	string answer;

	speedHigh_mmX_ = speedHigh_stepX_ *stepSize_umX_ /1000;
	speedLow_mmX_ = speedLow_stepX_ *stepSize_umX_ /1000;

	command << "D:" << controllerAxisX_ << speedLow_stepX_ << "P" << speedHigh_stepX_ << "P" << accelTimeX_;		// set speed command
	ret = SendSerialCommand(portName_XY.c_str(), command.str().c_str(), "\r\n");
	command.str("");
	if (ret != DEVICE_OK)
		return ret;

	ret = GetSerialAnswer(portName_XY.c_str(), "\r\n", answer);
	if (ret != DEVICE_OK)
		return ret;

	ret = ConfirmAnswer(answer);
	if (ret != DEVICE_OK)
		return ret;

	return DEVICE_OK;
}

int ChuoSeikiXYStage::OnSpeedXLow(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		pProp->Set(speedLow_stepX_);
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(speedLow_stepX_);
	}

	ostringstream command;
	int ret = 0;
	string answer;

	speedHigh_mmX_ = speedHigh_stepX_ *stepSize_umX_ /1000;
	speedLow_mmX_ = speedLow_stepX_ *stepSize_umX_ /1000;

	command << "D:" << controllerAxisX_ << speedLow_stepX_ << "P" << speedHigh_stepX_ << "P" << accelTimeX_;
	ret = SendSerialCommand(portName_XY.c_str(), command.str().c_str(), "\r\n");
	command.str("");
	if (ret != DEVICE_OK)
		return ret;

	ret = GetSerialAnswer(portName_XY.c_str(), "\r\n", answer);
	if (ret != DEVICE_OK)
		return ret;

	ret = ConfirmAnswer(answer);
	if (ret != DEVICE_OK)
		return ret;

	return DEVICE_OK;
}

int ChuoSeikiXYStage::OnAccelTimeX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		pProp->Set(accelTimeX_);
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(accelTimeX_);
	}

	ostringstream command;
	int ret = 0;
	string answer;

	speedHigh_mmX_ = speedHigh_stepX_ *stepSize_umX_ /1000;
	speedLow_mmX_ = speedLow_stepX_ *stepSize_umX_ /1000;

	command << "D:" << controllerAxisX_ << speedLow_stepX_ << "P" << speedHigh_stepX_ << "P" << accelTimeX_;
	ret = SendSerialCommand(portName_XY.c_str(), command.str().c_str(), "\r\n");
	command.str("");
	if (ret != DEVICE_OK)
		return ret;

	ret = GetSerialAnswer(portName_XY.c_str(), "\r\n", answer);
	if (ret != DEVICE_OK)
		return ret;

	ret = ConfirmAnswer(answer);
	if (ret != DEVICE_OK)
		return ret;

	return DEVICE_OK;
}

int ChuoSeikiXYStage::OnSpeedYHigh(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		pProp->Set(speedHigh_stepY_);
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(speedHigh_stepY_);
	}

	ostringstream command;
	int ret = 0;
	string answer;

	speedHigh_mmY_ = speedHigh_stepY_ *stepSize_umY_ /1000;
	speedLow_mmY_ = speedLow_stepY_ *stepSize_umY_ /1000;

	command << "D:" << controllerAxisY_ << speedLow_stepY_ << "P" << speedHigh_stepY_ << "P" << accelTimeY_;
	ret = SendSerialCommand(portName_XY.c_str(), command.str().c_str(), "\r\n");
	command.str("");
	if (ret != DEVICE_OK)
		return ret;

	ret = GetSerialAnswer(portName_XY.c_str(), "\r\n", answer);
	if (ret != DEVICE_OK)
		return ret;

	ret = ConfirmAnswer(answer);
	if (ret != DEVICE_OK)
		return ret;

	return DEVICE_OK;
}

int ChuoSeikiXYStage::OnSpeedYLow(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		pProp->Set(speedLow_stepY_);
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(speedLow_stepY_);
	}

	ostringstream command;
	int ret = 0;
	string answer;

	speedHigh_mmY_ = speedHigh_stepY_ *stepSize_umY_ /1000;
	speedLow_mmY_ = speedLow_stepY_ *stepSize_umY_ /1000;

	command << "D:" << controllerAxisY_ << speedLow_stepY_ << "P" << speedHigh_stepY_ << "P" << accelTimeY_;
	ret = SendSerialCommand(portName_XY.c_str(), command.str().c_str(), "\r\n");
	command.str("");
	if (ret != DEVICE_OK)
		return ret;

	ret = GetSerialAnswer(portName_XY.c_str(), "\r\n", answer);
	if (ret != DEVICE_OK)
		return ret;

	ret = ConfirmAnswer(answer);
	if (ret != DEVICE_OK)
		return ret;

	return DEVICE_OK;
}

int ChuoSeikiXYStage::OnAccelTimeY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		pProp->Set(accelTimeY_);
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(accelTimeY_);
	}

	ostringstream command;
	int ret = 0;
	string answer;

	speedHigh_mmY_ = speedHigh_stepY_ *stepSize_umY_ /1000;
	speedLow_mmY_ = speedLow_stepY_ *stepSize_umY_ /1000;

	command << "D:" << controllerAxisY_ << speedLow_stepY_ << "P" << speedHigh_stepY_ << "P" << accelTimeY_;
	ret = SendSerialCommand(portName_XY.c_str(), command.str().c_str(), "\r\n");
	command.str("");
	if (ret != DEVICE_OK)
		return ret;

	ret = GetSerialAnswer(portName_XY.c_str(), "\r\n", answer);
	if (ret != DEVICE_OK)
		return ret;

	ret = ConfirmAnswer(answer);
	if (ret != DEVICE_OK)
		return ret;

	return DEVICE_OK;
}



//-----------------------------------------------------------------------------
//........... Class2: Z stage controller..................................
//-----------------------------------------------------------------------------

ChuoSeikiZStage::ChuoSeikiZStage() :

initialized_		(false),
stepSize_umZ_		(1.0),
speedHigh_stepZ_	(2000),	//pps
speedLow_stepZ_		(500),	//pps
accelTimeZ_			(100),
answerTimeoutMs_	(1000)

{
	InitializeDefaultErrorMessages();	// default error messages
	
	// define custom error messages
	SetErrorText (ERR_PORT_CHANGE_FORBIDDEN,"Can't change serial port!"	);
	SetErrorText (ERR_UNRECOGNIZED_ANSWER,	"Unrecognized answer!");
	SetErrorText (ERR_NO_ANSWER,			"No response!");
	SetErrorText (ERR_NO_CONTROLLER,		"Please add the ChuoSeiki Controller device first!");
	SetErrorText (ERR_HOMING,				"Homing error! Please clear error using control pad!");
	SetErrorText (ERR_TIMEOUT,				"Timeout error!");

	SetErrorText(ERR_CONTROLER_0,	"Not In Command Recive Mode!");
	SetErrorText(ERR_CONTROLER_1,	"Command Syntax Error!");
	SetErrorText(ERR_CONTROLER_2,	"Error: Overflow Value/Read Only Parameter!");
	SetErrorText(ERR_CONTROLER_3,	"Command Set Axis Error/Mode Setting Error!");
	SetErrorText(ERR_CONTROLER_4,	"Command Char Over!");
	SetErrorText(ERR_CONTROLER_5,	"Stop Command Sent To Stopped Stage!");
	SetErrorText(ERR_CONTROLER_6,	"Limit Detected!");
	SetErrorText(ERR_CONTROLER_7,	"Emergency Stop Detected!");
	SetErrorText(ERR_CONTROLER_8,	"Com Error/Driver Error!");
	SetErrorText(ERR_CONTROLER_9,	"Other Errors!");

	// Name
	CreateProperty(MM::g_Keyword_Name, g_ChuoSeikiZStageDeviceName, MM::String, true);

	// Description
	CreateProperty(MM::g_Keyword_Description, "ChuoSeiki 1-stage driver", MM::String, true);

	// select Port:
	CPropertyAction* pActType = new CPropertyAction(this, &ChuoSeikiZStage::OnSelectPort_Z);
	CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pActType, true);

	// Controller Axis
	controllerAxisZ_ = "A";
	pActType = new CPropertyAction(this, &ChuoSeikiZStage::OnControllerAxis);
	CreateProperty(g_ChuoSeiki_Controller_Axis, "A", MM::String, false, pActType, true);

	AddAllowedValue(g_ChuoSeiki_Controller_Axis, "A");
	AddAllowedValue(g_ChuoSeiki_Controller_Axis, "B");
	AddAllowedValue(g_ChuoSeiki_Controller_Axis, "C");

}

ChuoSeikiZStage::~ChuoSeikiZStage()
{
   Shutdown();
}

///////////////////////////////////////////////////////////////////////////////
// Stage methods required by the API
///////////////////////////////////////////////////////////////////////////////

void ChuoSeikiZStage::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_ChuoSeikiZStageDeviceName);
}

MM::DeviceDetectionStatus ChuoSeikiZStage::DetectDevice(void)
{
	// all conditions must be satisfied...
	MM::DeviceDetectionStatus result = MM::Misconfigured;

	try	
	{
		std::string lowercase = portName_Z;
		for( std::string::iterator i = lowercase.begin(); i != lowercase.end(); ++i) 
		{
			*i = (char)tolower(*i);
		}
		if( lowercase.length() > 0 &&  lowercase.compare("undefined") !=0  && lowercase.compare("unknown") !=0 )	
		{
			// the port property seems correct, so give it a try
			result = MM::CanNotCommunicate;

			// device specific default communication parameters
			GetCoreCallback()->SetDeviceProperty(portName_Z.c_str(), MM::g_Keyword_BaudRate,	"9600"	);
			GetCoreCallback()->SetDeviceProperty(portName_Z.c_str(), MM::g_Keyword_StopBits,	"1"		);
			GetCoreCallback()->SetDeviceProperty(portName_Z.c_str(), "AnswerTimeout",			"500.0"	);
			GetCoreCallback()->SetDeviceProperty(portName_Z.c_str(), "DelayBetweenCharsMs",		"0.0"	);

			// get portname for 1 stage controller
			MM::Device* device = GetCoreCallback()->GetDevice(this, portName_Z.c_str());
			// set port parameters
			device->Initialize();
			// check version
			int qvStatus = this->ConfirmComm();
			if( DEVICE_OK != qvStatus ) 
				LogMessageCode(qvStatus,true);
			else
				 // to succeed must reach here....
				result = MM::CanCommunicate;
			// device shutdown, return default answertimeout	
			device->Shutdown();
			 // always restore the AnswerTimeout to the default
			GetCoreCallback()->SetDeviceProperty(portName_Z.c_str(), "AnswerTimeout", "2000.0");
		}
	}
	catch(...) 
	{
		LogMessage("Exception in DetectDevice!",false);
	}
	return result;
}

int ChuoSeikiZStage::ConfirmComm()
{
	int ret;
	string answer;

	PurgeComPort(portName_Z.c_str());
	ret= SendSerialCommand(portName_Z.c_str(), "?:CHUOSEIKI", "\r\n");
	if (ret!= DEVICE_OK) 
		return ret;
	ret= GetSerialAnswer(portName_Z.c_str(), "\r\n", answer);
	if (ret != DEVICE_OK) 
		return ret;

	if( answer.substr(0, 9).compare("CHUOSEIKI") != 0)
	{
		return ERR_UNRECOGNIZED_ANSWER;
	}

	ret = SendSerialCommand(portName_Z.c_str(), "X:1", "\r\n");  // request feedback "\r\n" after each command
	if (ret != DEVICE_OK)		
		return ret;

	ret = GetSerialAnswer(portName_Z.c_str(), "\r\n", answer);
	if (ret != DEVICE_OK)		
		return ret;

	return DEVICE_OK;
}

int ChuoSeikiZStage::Initialize()
{
	int ret = 0;

	//Step Size Z
	CPropertyAction* pAct = new CPropertyAction(this, &ChuoSeikiZStage::OnStepSizeZ);
	ret = CreateProperty("Step Size: um", "1.0", MM::Float, false, pAct);
	if (ret != DEVICE_OK)
      return ret;

	//Speed High Z
	pAct = new CPropertyAction(this, &ChuoSeikiZStage::OnSpeedZHigh);
	ret = CreateProperty("High Speed: pps", "2000", MM::Float, false, pAct);
	if (ret != DEVICE_OK)
      return ret;

	//Speed Low Z
	pAct = new CPropertyAction(this, &ChuoSeikiZStage::OnSpeedZLow);
	ret = CreateProperty("Low Speed: pps", "500", MM::Float, false, pAct);
	if (ret != DEVICE_OK)
      return ret;

	//Accel Time Z
	pAct = new CPropertyAction(this, &ChuoSeikiZStage::OnAccelTimeZ);
	ret = CreateProperty("Accelerating Time: msec", "100", MM::Float, false, pAct);
	if (ret != DEVICE_OK)
      return ret;

	bHomeing = false;
	bLimitStop = false;

	ret = ConfirmComm();
	if (ret != DEVICE_OK)
		return ret;

	ret = UpdateStatus();
	if (ret != DEVICE_OK)
		return ret;

	initialized_ = true;

	return DEVICE_OK;

}

int ChuoSeikiZStage::Shutdown()
{
	if (initialized_)
	{
		initialized_ = false;
	}
	return DEVICE_OK;
}

void ChuoSeikiZStage::QueryPeripheralInventory()
{
	discoverableDevices_.clear();
	inventoryDeviceAddresses_.clear();
	inventoryDeviceIDs_.clear();

	ConfirmComm();
}

bool ChuoSeikiZStage::Busy()
{
	ostringstream command;
	string answer;
	int ret = 0;

	command << "Q:"<< controllerAxisZ_ << "2";
	ret = SendSerialCommand(portName_Z.c_str(), command.str().c_str(), "\r\n");
	command.str("");
	if (ret != DEVICE_OK)
		return true;

	ret = GetSerialAnswer(portName_Z.c_str(), "\r\n", answer);
	if (ret != DEVICE_OK)
		return true;

	if (answer.length() == 1)
	{
		if( answer.substr(0, 1) == "D" )
		{
			return true;
		}
	}

	return false;

}

int ChuoSeikiZStage::WaitForBusy(long Time)
{
	long time = Time;

	MM::TimeoutMs timeout(GetCurrentMMTime(), time);
	while( true == Busy() )
	{
		if (timeout.expired(GetCurrentMMTime())) 
		{
			return ERR_TIMEOUT;
		}
	}
	return DEVICE_OK;
}


int ChuoSeikiZStage::ConfirmAnswer(std::string ans)
{
	if (ans.length() == 2)
	{
		if(ans == "!0")			return ERR_CONTROLER_0;
		else if(ans == "!1")	return ERR_CONTROLER_1;
		else if(ans == "!2")	return ERR_CONTROLER_2;
		else if(ans == "!3")	return ERR_CONTROLER_3;
		else if(ans == "!4")	return ERR_CONTROLER_4;
		else if(ans == "!5")	return ERR_CONTROLER_5;
		else if(ans == "!6")	return ERR_CONTROLER_6;
		else if(ans == "!7")	return ERR_CONTROLER_7;
		else if(ans == "!8")	return ERR_CONTROLER_8;
		else if(ans == "!9")	return ERR_CONTROLER_9;
		else			return ERR_UNRECOGNIZED_ANSWER;
	}
	else return DEVICE_OK;
}


int ChuoSeikiZStage::SetPositionUm(double pos)
{
	double dSteps = ( pos / stepSize_umZ_);
	long steps = (long) dSteps;
	return SetPositionSteps( steps );
}

int ChuoSeikiZStage::GetPositionUm(double& pos)
{
	long steps;
	int ret;
	ret = GetPositionSteps(steps);
	if (ret != DEVICE_OK)
		return ret;

	pos = steps * stepSize_umZ_;

	return DEVICE_OK;
}
  
int ChuoSeikiZStage::SetPositionSteps(long z)
{
	int ret = 0;
	ostringstream command;
	string answer;

	command << "AGO:"<< controllerAxisZ_ << z;
	ret = SendSerialCommand(portName_Z.c_str(), command.str().c_str(), "\r\n");
	command.str("");
	if (ret != DEVICE_OK)
			return ret;

	ret = GetSerialAnswer(portName_Z.c_str(), "\r\n", answer);
	if (ret != DEVICE_OK)
			return ret;
	ret = ConfirmAnswer(answer);
	if (ret != DEVICE_OK)
			return ret;

	double newPosZ = z * stepSize_umZ_;
	double distance_umZ = fabs(newPosZ - posZ_um_);

	long timeOut= (long) (distance_umZ / speedHigh_mmZ_);

	WaitForBusy(timeOut);

	ret = OnStagePositionChanged( newPosZ );
	if (ret != DEVICE_OK)	
		return ret;	

	return DEVICE_OK;

}
  
int ChuoSeikiZStage::GetPositionSteps(long& z)
{
	int ret = 0;
	string answer;
	ostringstream command;

	command << "Q:"<< controllerAxisZ_ << "0";
	ret = SendSerialCommand(portName_Z.c_str(), command.str().c_str(), "\r\n");
	command.str("");
	if (ret != DEVICE_OK)		
			return ret;	

	ret = GetSerialAnswer(portName_Z.c_str(), "\r\n", answer);
	if (ret != DEVICE_OK)
			return ret;	
	
	if (answer.length() == 10)				// answer format: (+ or -)(8 digits)(stage state keyword)
	{
		if( answer.substr(9, 1) == "D" )
		{
			return GetPositionSteps(z);		// if stage is running, repeat
		}
		else if( answer.substr(9, 1) == "H" )
		{
			return ERR_HOMING;	
		}
		else
		{
			z = std::atol(answer.substr(0,9).c_str());
			posZ_um_ = z * stepSize_umZ_;
				return DEVICE_OK;
		}
	}
	else return ERR_UNRECOGNIZED_ANSWER;
}


int ChuoSeikiZStage::SetRelativePositionSteps(long z)
{

	ostringstream command;
	int ret = 0;
	string answer;

	posZ_um_ = z * stepSize_umZ_;

	PurgeComPort(portName_Z.c_str());

	command << "MGO:"<< controllerAxisZ_ << z;
	ret = SendSerialCommand(portName_Z.c_str(), command.str().c_str(), "\r\n");
	command.str("");
	if (ret != DEVICE_OK)
		return ret;

	ret = GetSerialAnswer(portName_Z.c_str(), "\r\n", answer);
	if (ret != DEVICE_OK)
		return ret;

	ret = ConfirmAnswer(answer);
	if (ret != DEVICE_OK)
		return ret;

	ret = OnStagePositionChanged(posZ_um_);
	if (ret != DEVICE_OK)
		return ret;

	return DEVICE_OK;
}

  
int ChuoSeikiZStage::SetOrigin()
{
	return DEVICE_UNSUPPORTED_COMMAND;
}

int ChuoSeikiZStage::Autofocus(long param)
{
	PurgeComPort(portName_Z.c_str());

	// format the command
	ostringstream cmd;
	cmd << "AF Z=";
	cmd << param ;
	string answer;
	int ret;

	ret = SendSerialCommand(portName_Z.c_str(), cmd.str().c_str(), "\r\n");
	if (ret != DEVICE_OK)
		return ret;
	ret = GetSerialAnswer(portName_Z.c_str(),"\r\n", answer);
	if (ret != DEVICE_OK)
		return ret;

	istringstream is(answer);
	string outcome;
	int code;
	is >> outcome >> code;

	if (outcome.compare(":A") == 0)
		return DEVICE_OK; // success!

	return code;
}

/* // Example of using ReadFromComPort fuction:

int ChuoSeikiZStage::ExecuteCommand(const string& cmd, string& response)
{
	// send command
	PurgeComPort(portName_Z.c_str());
	if (DEVICE_OK != SendSerialCommand(portName_Z.c_str(), cmd.c_str(), "\r\n"))
		return DEVICE_SERIAL_COMMAND_FAILED;

	const unsigned long bufLen = 80;
	char answer[bufLen];
	unsigned curIdx = 0;
	memset(answer, 0, bufLen);
	unsigned long read;
	unsigned long startTime = GetClockTicksUs();

	char* pLF = 0;
	do 
	{
		if (DEVICE_OK != ReadFromComPort(portName_Z.c_str(), reinterpret_cast<unsigned char*>(answer + curIdx), bufLen - curIdx, read)) 
		{
			return DEVICE_SERIAL_COMMAND_FAILED;
		}
		curIdx += read;

		pLF = strstr(answer, "\r\n");
		if (pLF)
			*pLF = 0; // terminate the string
	}
	while(!pLF && (GetClockTicksUs() - startTime) / 1000.0 < answerTimeoutMs_);

	if (!pLF)
		return DEVICE_SERIAL_TIMEOUT;

	response = answer;
	return DEVICE_OK;
}
/*/


///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int ChuoSeikiZStage::OnSelectPort_Z(MM::PropertyBase* pProp, MM::ActionType pAct)
{
	if (pAct == MM::BeforeGet)
	{
		pProp->Set(portName_Z.c_str());
	}
	else if (pAct == MM::AfterSet)
	{
		if (initialized_)
		{
			pProp->Set(portName_Z.c_str());
			return ERR_PORT_CHANGE_FORBIDDEN;
		}
		pProp->Get(portName_Z);
	}
	return DEVICE_OK;
}

int ChuoSeikiZStage::OnConfigPort_Z(MM::PropertyBase* pProp, MM::ActionType pAct)
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
int ChuoSeikiZStage::OnResetPort_Z(MM::PropertyBase* pProp, MM::ActionType pAct)
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
         int status = SendSerialCommand(portName_Z.c_str(), "RESTA", "\r\n");
         CDeviceUtils::SleepMs (10);
		 if (status !=DEVICE_OK)
            return status;
         // TODO: Do we need to wait until the reset is completed?
      }
   }
   return DEVICE_OK;
}

int ChuoSeikiZStage::OnStepSizeZ(MM::PropertyBase* pProp, MM::ActionType eAct)
{

	if (eAct == MM::BeforeGet)
	{
		pProp->Set(stepSize_umZ_);
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(stepSize_umZ_);
	}

	return DEVICE_OK;
}



int ChuoSeikiZStage::OnSpeedZHigh(MM::PropertyBase* pProp, MM::ActionType eAct)
{

	if (eAct == MM::BeforeGet)
	{
		pProp->Set(speedHigh_stepZ_);
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(speedHigh_stepZ_);
	}

	ostringstream command;
	int ret = 0;
	string answer;
	//speed [mm/s]
	speedHigh_mmZ_ = speedHigh_stepZ_ *stepSize_umZ_ /1000;
	speedLow_mmZ_ = speedLow_stepZ_ *stepSize_umZ_ /1000;

	command << "D:"<< controllerAxisZ_ << speedLow_stepZ_ << "P" << speedHigh_stepZ_ << "P" << accelTimeZ_;
	ret = SendSerialCommand(portName_Z.c_str(), command.str().c_str(), "\r\n");
	command.str("");
	if (ret != DEVICE_OK)
		return ret;

	ret = GetSerialAnswer(portName_Z.c_str(), "\r\n", answer);
	if (ret != DEVICE_OK)
		return ret;

	ret = ConfirmAnswer(answer);
	if (ret != DEVICE_OK)
		return ret;

	return DEVICE_OK;
}


int ChuoSeikiZStage::OnSpeedZLow(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		pProp->Set(speedLow_stepZ_);
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(speedLow_stepZ_);
	}

	ostringstream command;
	int ret = 0;
	string answer;
	//speed [mm/s]
	speedHigh_mmZ_ = speedHigh_stepZ_ *stepSize_umZ_ /1000;
	speedLow_mmZ_ = speedLow_stepZ_ *stepSize_umZ_ /1000;

	command << "D:"<< controllerAxisZ_ << speedLow_stepZ_ << "P" << speedHigh_stepZ_ << "P" << accelTimeZ_;
	ret = SendSerialCommand(portName_Z.c_str(), command.str().c_str(), "\r\n");
	command.str("");
	if (ret != DEVICE_OK)
		return ret;

	ret = GetSerialAnswer(portName_Z.c_str(), "\r\n", answer);
	if (ret != DEVICE_OK)
		return ret;

	ret = ConfirmAnswer(answer);
	if (ret != DEVICE_OK)
		return ret;

	return DEVICE_OK;
}

int ChuoSeikiZStage::OnAccelTimeZ(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		pProp->Set(accelTimeZ_);
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(accelTimeZ_);
	}

	ostringstream command;
	int ret = 0;
	string answer;
	//speed [mm/s]
	speedHigh_mmZ_ = speedHigh_stepZ_ *stepSize_umZ_ /1000;
	speedLow_mmZ_ = speedLow_stepZ_ *stepSize_umZ_ /1000;

	command << "D:"<< controllerAxisZ_ << speedLow_stepZ_ << "P" << speedHigh_stepZ_ << "P" << accelTimeZ_;
	ret = SendSerialCommand(portName_Z.c_str(), command.str().c_str(), "\r\n");
	command.str("");
	if (ret != DEVICE_OK)
		return ret;

	ret = GetSerialAnswer(portName_Z.c_str(), "\r\n", answer);
	if (ret != DEVICE_OK)
		return ret;

	ret = ConfirmAnswer(answer);
	if (ret != DEVICE_OK)
		return ret;

	return DEVICE_OK;
}

/*
int ChuoSeikiZStage::OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   return DEVICE_OK;
}
*/

int ChuoSeikiZStage::OnControllerAxis(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		pProp->Set(controllerAxisZ_.c_str());
	}
	else if (eAct == MM::AfterSet)
	{
		string Z_axis;
		pProp->Get(controllerAxisZ_);
		if (Z_axis == "A" || Z_axis == "B" || Z_axis == "C")
		{
			controllerAxisZ_ = Z_axis;
		}
	}
	return DEVICE_OK;
}


int ChuoSeikiZStage::OnAutofocus(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	long param;
	if (eAct == MM::BeforeGet)
	{
		pProp->Set("AutoFocusParamater");
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(param);
		return Autofocus(param);
	}
	return DEVICE_OK;
}




