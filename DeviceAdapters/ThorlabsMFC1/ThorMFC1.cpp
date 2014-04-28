///////////////////////////////////////////////////////////////////////////////
// FILE:          ThorMFC1.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   device adapter for ThorLabs MFC1 motor focus controller
//				  Assumes controller connected to COARSE focus knob (as of 3/28/14)
//                
// AUTHOR:        Thomas Zangle, UCLA, 1/30/14
//
// COPYRIGHT:     University of California, Los Angeles, 2014
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

#define WIN32_LEAN_AND_MEAN
#include <Windows.h>

#include <TMCLWrapperRS232.h>
#include "ThorMFC1.h"
#include <cstdio>
#include <string>
#include <math.h>
#include "ModuleInterface.h"
#include <sstream>
#include <algorithm>
#include <iostream>



using namespace std;

// External names used used by the rest of the system
// to load particular device from the "ThorMFC1.dll" library
const char* g_StageDeviceName = "ThorZFocus";

// windows DLL entry code
#ifdef WIN32
BOOL APIENTRY DllMain( HANDLE /*hModule*/, 
                      DWORD  ul_reason_for_call, 
                      LPVOID /*lpReserved*/
                      )
{
   switch (ul_reason_for_call)
   {
   case DLL_PROCESS_ATTACH:
   case DLL_THREAD_ATTACH:
   case DLL_THREAD_DETACH:
   case DLL_PROCESS_DETACH:
      break;
   }
   return TRUE;
}
#endif


///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////

/**
 * List all suppoerted hardware devices here
 * Do not discover devices at runtime.  To avoid warnings about missing DLLs, Micro-Manager
 * maintains a list of supported device (MMDeviceList.txt).  This list is generated using 
 * information supplied by this function, so runtime discovery will create problems.
 */
MODULE_API void InitializeModuleData()
{
   //AddAvailableDeviceName(g_StageDeviceName, "Thorlabs MFC1 focus controller");
	RegisterDevice(g_StageDeviceName, MM::StageDevice, "Thorlabs MFC1 focus controller");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   // decide which device class to create based on the deviceName parameter
   if (strcmp(deviceName, g_StageDeviceName) == 0)
   {
      // create stage
      return new ThorZStage();
   }
   // ...supplied name not recognized
   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
//ThorZStage implementation for ThorLabs MFC1 motorized focus controller
// ~~~~~~~~~~~~~~~~~~~~~~~~~

ThorZStage::ThorZStage() : 
	Status(0),
	Motor(0),
	CommdAddress(1),
	ReplyAddress(2),
	Value(0),
	//ComName("COM3"),
	Baudrate(57600),
	Handle(),
	CurrPos(0),
	mmPerRot(2), //changed 3/28/14 to 2 mm per rev (change to 0.2 for fine focus knob) TODO: make this an option on initial setup
	minMM(-4),
	maxMM(4),
	countsPerRot(66320), //measured 16580 for microstep resolution of 16, current microstep resolution set to 64
	comport_("COM3"),
	sequenceable_(false),
	initialized_(false),
	countsPerUm(1),
	busy_internal(false)
{
   InitializeDefaultErrorMessages();

   SetErrorText(ERR_PORT_CHANGE_FORBIDDEN, "Serial port can't be changed at run-time."
                                           " Use configuration utility or modify configuration file manually.");
   SetErrorText(ERR_INVALID_POSITION, "Requested position not available in this device");

   // Port
   CPropertyAction* pAct = new CPropertyAction (this, &ThorZStage::OnComPort);
   CreateProperty("COM Port", "Undefined", MM::String, false, pAct, true);

}

ThorZStage::~ThorZStage()
{
   Shutdown();
}

int ThorZStage::Initialize()
{
   
   if (initialized_)
      return DEVICE_OK;

   // set property list
   // -----------------

   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, g_StageDeviceName, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "ThorLabs MFC1 Focus Controller", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Port
   //CPropertyAction* pAct = new CPropertyAction (this, &ThorZStage::OnPort);
   //CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

   // Position
   // --------
   CPropertyAction* pAct = new CPropertyAction (this, &ThorZStage::OnPosition);
   ret = CreateProperty(MM::g_Keyword_Position, "0", MM::Float, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // Sequenceability
   // --------
   pAct = new CPropertyAction (this, &ThorZStage::OnSequence);
   ret = CreateProperty("UseSequences", "No", MM::String, false, pAct);
   AddAllowedValue("UseSequences", "No");
   AddAllowedValue("UseSequences", "Yes");
   if (ret != DEVICE_OK)
      return ret;

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   //initialize connection to hardware
   //ComName = port_.c_str(); //conver to char, as needed for OpenRS232 function
   const char * ComName = comport_.c_str();
   Handle = OpenRS232(ComName, Baudrate);
   TMCLSetAxisParameter(Handle, CommdAddress, 140, Motor, 6); //set microstep resolution to 64 (4 insted of 6 would set it to 16)
   GetResult(Handle, &ReplyAddress, &Status, &Value); //get result from register
   TMCLSetAxisParameter(Handle, CommdAddress, 210, Motor, 6400); //set prescaler to suggested value of 0.7125
   GetResult(Handle, &ReplyAddress, &Status, &Value); //get result from register

   countsPerUm = countsPerRot/(mmPerRot*1000); //conversion factor for um to counts

   //set current position as origin
   //SetOrigin();

   //test motor control functions and demonstrate the motor is working at startup, commented out 2/4/14
   //SetPositionSteps(1000);
   //long steptest = 0;
   //GetPositionSteps(steptest);
   //double umtest = 0;
   //GetPositionUm(umtest);
   //SetPositionUm(0);

   initialized_ = true;

   return DEVICE_OK;
}

int ThorZStage::Shutdown()
{
   //close RS232 connection
   CloseRS232(Handle);

   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

void ThorZStage::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_StageDeviceName);
}

bool ThorZStage::Busy()
{
   /*if (busy_internal==false)
   {
		TMCLGetAxisParameter(Handle, CommdAddress, 8, Motor); //check if target position reached, true if actual = target
		GetResult(Handle, &ReplyAddress, &Status, &Value);
		return(Value==0); //get result from register, busy if not at target position yet
   }
   else
   {
	   return(1);
   }*/
	return busy_internal;
}


int ThorZStage::SetPositionSteps(long steps)
{
	//function to set position, and check that the actual position is reached before returning
	//initiates move, then loops until actual position matches set position
	DWORD delay = 10; //ms, delay before polling device
	long ActualPos = 0;
	int Pos = (int) steps;
	
	busy_internal = true;

	//tell motor to move
	TMCLMoveToPosition(Handle, CommdAddress, 0, Motor, Pos); //Type = 0, meaning absolute position

	//clear Result address cache, we are about to need it below
	do{
		GetResult(Handle, &ReplyAddress, &Status, &Value); //get result from register
		//Beep(523,500); //debugging noise
	}while (Value!=Pos);

	//Use built in WAIT function of the TMCL controller
	SendCmd(Handle, CommdAddress, 27, 1, Motor, 0);
	GetResult(Handle, &ReplyAddress, &Status, &Value); //get result from register

	//loop until acutal position matches desired position
	do{
		Sleep(delay);
		GetPositionSteps(ActualPos);
		//printf("Actual:%d\n", ActualPos);
		//Beep(523,500);
	}while (ActualPos != steps);

	CurrPos = steps;
	
	busy_internal = false;

	return DEVICE_OK;
}

int ThorZStage::GetPositionSteps(long& steps)
{
	//TODO: add code to clear ReplyAddress
	//to clear ReplyAddress loop until Value does not change by more than 10
	int OldValue = Value + 11;
	DWORD delay = 10; //ms, delay before polling device
	do{
		Sleep(delay);
		OldValue = Value;
		TMCLGetAxisParameter(Handle, CommdAddress, 1, Motor); //command to get actual position
		GetResult(Handle, &ReplyAddress, &Status, &Value);
	}while (abs(OldValue-Value) > 10);

	steps = (long) Value;
	CurrPos = steps; //update current position
	return DEVICE_OK;
}

int ThorZStage::SetPositionUm(double pos_um) 
{
    long StepVal = (long) (pos_um*countsPerUm);
    return SetPositionSteps(StepVal);
}

int ThorZStage::GetPositionUm(double& pos_um)
{
	long StepVal = 0;
	int RetVal = GetPositionSteps(StepVal);
	pos_um = ((double) StepVal)/countsPerUm;
	return RetVal;
}

int ThorZStage::SetRelativePositionSteps(long d_steps)
{
	long StepVal = d_steps + CurrPos;
	return SetPositionSteps(StepVal);
}
int ThorZStage::SetRelativePositionUm(double d_um)
{
	double UmVal = d_um + ((double) CurrPos)/countsPerUm;
	return SetPositionUm(UmVal);
}

int ThorZStage::SetOrigin()
{	
	//TODO: implement new method for this. I don't think the below function works as advertised in the reference guide
	//update 2/3/14, this method is not supported in device firmware. could implement it by setting a tracker variable for offset from 0
	// and modifying this value here.. if necessary
	/*TMCLSetAxisParameter(Handle, CommdAddress, 1, Motor, 0); //set current position to 0
	GetResult(Handle, &ReplyAddress, &Status, &Value); //get result from register
	return GetPositionSteps(CurrPos); //update current position*/
	return DEVICE_UNSUPPORTED_COMMAND;
}

int ThorZStage::GetLimits(double& min, double& max)
{
	min = minMM*1000;
	max = maxMM*1000;

	return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int ThorZStage::OnComPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(comport_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      if (initialized_)
      {
         // revert
         pProp->Set(comport_.c_str());
         return ERR_PORT_CHANGE_FORBIDDEN;
      }

      // initialize port properties to be correct by default
//      GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_BaudRate, CDeviceUtils::ConvertToString(115200));

      pProp->Get(comport_);
   }

   return DEVICE_OK;
}

int ThorZStage::OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct)
{

   if (eAct == MM::BeforeGet)
   {
      std::stringstream s;
      s << pos_um_;
      pProp->Set(s.str().c_str());
   }
   else if (eAct == MM::AfterSet)
   {
	   double pos;
      pProp->Get(pos);
      if (pos > maxMM*1000 || minMM*1000 > pos)
      {
         pProp->Set(pos_um_); // revert
         return ERR_INVALID_POSITION;
      }
      SetPositionUm(pos);
      pos_um_ = pos;
   }

   return DEVICE_OK;
}

int ThorZStage::OnSequence(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      std::string answer = "No";
      if (sequenceable_)
         answer = "Yes";
      pProp->Set(answer.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      std::string answer;
      pProp->Get(answer);
      if (answer == "Yes")
         sequenceable_ = true;
      else
         sequenceable_ = false;
   }
   return DEVICE_OK;
}
