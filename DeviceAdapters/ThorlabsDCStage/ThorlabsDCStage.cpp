///////////////////////////////////////////////////////////////////////////////
// FILE:          ThorlabsDCStage.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Thorlabs device adapters: TDC001 Controller (version 0.0)
//
// COPYRIGHT:     Emilio J. Gualda,2012
//
// LICENSE:       This file is distributed under the BSD license.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
// AUTHOR:        Emilio J. Gualda, IGC, 2012
//


#ifdef WIN32
   #include <windows.h>
   #define snprintf _snprintf 
#endif

#include "ThorlabsDCStage.h"
#include "APTAPI.h"
#include <cstdio>
#include <string>
#include <math.h>
#include <sstream>
#include <string.h>

const char* g_ThorlabsDCStageDeviceName = "ThorlabsDCStage";
const char* g_PositionProp = "Position";
const char* g_Keyword_Position = "Set position (microns)";
const char* g_Keyword_Velocity = "Velocity (mm/s)";
const char* g_Keyword_Home="Go Home";

const char* g_NumberUnitsProp = "Number of Units";
const char* g_SerialNumberProp = "Serial Number";
const char* g_MaxVelProp = "Maximum Velocity";
const char* g_MaxAccnProp = "Maximum Acceleration";
const char* g_StepSizeProp = "Step Size";

using namespace std;

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////

MODULE_API void InitializeModuleData()
{
   AddAvailableDeviceName(g_ThorlabsDCStageDeviceName, "Thorlabs DC Stage");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_ThorlabsDCStageDeviceName) == 0)
   {
      ThorlabsDCStage* s = new ThorlabsDCStage();
      return s;
   }

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}
///////////////////////////////////////////////////////////////////////////////
// PiezoZStage class
///////////////////////////////////////////////////////////////////////////////

ThorlabsDCStage::ThorlabsDCStage() :
   port_("Undefined"),
   stepSizeUm_(0.1),
   initialized_(false),
   answerTimeoutMs_(1000),
   maxTravelUm_(50000.0),
   curPosUm_(0.0),
   Homed_(false),
   HomeInProgress_(false),
   plNumUnits(-1)

   
{
   InitializeDefaultErrorMessages();

   // set device specific error messages
   SetErrorText(ERR_PORT_CHANGE_FORBIDDEN, "Serial port can't be changed at run-time."
                                           " Use configuration utility or modify configuration file manually.");
   SetErrorText(ERR_UNRECOGNIZED_ANSWER, "Invalid response from the device hola=0");
   SetErrorText(ERR_UNSPECIFIED_ERROR, "Unspecified error occured.");
   SetErrorText(ERR_RESPONSE_TIMEOUT, "Device timed-out: no response received withing expected time interval.");
   SetErrorText(ERR_BUSY, "Device busy.");
   SetErrorText(ERR_STAGE_NOT_ZEROED, "Zero sequence still in progress.\n"
                                      "Wait for few more seconds before trying again."
                                      "Zero sequence executes only once per power cycle");
 

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, g_ThorlabsDCStageDeviceName, MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "Thorlabs DC Stage", MM::String, true);
 

   
   // Port
  /* CPropertyAction* pAct = new CPropertyAction (this, &ThorlabsDCStage::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);*/
}

ThorlabsDCStage::~ThorlabsDCStage()
{
   Shutdown();
}

void ThorlabsDCStage::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_ThorlabsDCStageDeviceName);
}

int ThorlabsDCStage::Initialize()
{
   int ret(DEVICE_OK);
    register int i;
	hola_=APTInit();  //Initialize variuos data structures, initialise USB bus and other start funcions
	printf("initialize: %i\n",hola_);
	
	hola_=GetNumHWUnitsEx(HWTYPE_TDC001,&plNumUnits);
	i = 0;
	hola_=GetHWSerialNumEx(HWTYPE_TDC001,i,plSerialNum+i);
	InitHWDevice(plSerialNum[i]); 
	MOT_GetVelParamLimits(plSerialNum[i], pfMaxAccn+i, pfMaxVel+i);

	if (ret != DEVICE_OK)
      return ret;

	// READ ONLY PROPERTIES

	//Number of Units
	CreateProperty(g_NumberUnitsProp,CDeviceUtils::ConvertToString(plNumUnits),MM::String,true);
	//Serial Number
	CreateProperty(g_SerialNumberProp,CDeviceUtils::ConvertToString(plSerialNum[i]),MM::String,true);
	CreateProperty(g_MaxVelProp,CDeviceUtils::ConvertToString(pfMaxVel),MM::String,true);
	CreateProperty(g_MaxAccnProp,CDeviceUtils::ConvertToString(pfMaxAccn),MM::String,true);


	//Action Properties
	//Change position

   CPropertyAction* pAct = new CPropertyAction (this, &ThorlabsDCStage::OnPosition);
   CPropertyAction* pAct2 = new CPropertyAction (this, &ThorlabsDCStage::OnVelocity);
   CPropertyAction* pAct3 = new CPropertyAction (this, &ThorlabsDCStage::OnHome);
   CreateProperty(g_Keyword_Position, "0.0", MM::Float, false, pAct);
   SetPropertyLimits(g_Keyword_Position, 0.0, maxTravelUm_);

   CreateProperty(g_Keyword_Velocity, CDeviceUtils::ConvertToString(pfMaxVel), MM::Float, false, pAct2);
   CreateProperty(g_Keyword_Home, "0.0", MM::Float, false, pAct3);
    SetPropertyLimits(g_Keyword_Home, 0.0, 1.0);


   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;
   return DEVICE_OK;
}

int ThorlabsDCStage::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
	 	hola_=APTCleanUp();
   }
   return DEVICE_OK;
}

bool ThorlabsDCStage::Busy()
{
   // never busy because all commands block
   return false;
}



  
int ThorlabsDCStage::GetPositionUm(double& posUm)
{
	
	register int i;
	i=0;
	
	MOT_GetPosition(plSerialNum[i], pfPosition + i);
	posUm=pfPosition[i]*1000;
	curPosUm_=(float)posUm;
   
	return DEVICE_OK;
}

int ThorlabsDCStage::SetPositionUm(double posUm)
{
	register int i;
	i=0;

	newPosition[i]=(float)posUm/1000;
	MOT_MoveAbsoluteEx(plSerialNum[i], newPosition[i], 1);
	curPosUm_=newPosition[i]*1000;
    OnStagePositionChanged(curPosUm_);
   return DEVICE_OK;
}



int ThorlabsDCStage::SetOrigin()
{
   return DEVICE_UNSUPPORTED_COMMAND;
}
int ThorlabsDCStage::SetPositionSteps(long steps)
{
	steps=(long)0.1;
   return DEVICE_OK;
}

int ThorlabsDCStage::GetPositionSteps(long& steps)
{
	stepSizeUm_=steps;
   return DEVICE_OK;
}



int ThorlabsDCStage::GetLimits(double& min, double& max)
{
	min;
	max;
   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// private methods
///////////////////////////////////////////////////////////////////////////////
/**
 * Send Home command to the stage
 * If stage was already Homed, this command has no effect.
 * If not, then the zero sequence will be initiated.
 */
int ThorlabsDCStage::SetHome(double home)
{
   if (!IsHomed() && !HomeInProgress_)
   {

	   register int i;
	   i=0;
	   if (home!=0){
       hola_=MOT_MoveHome(plSerialNum[i], 1);
	   Homed_=true;
	   }

      HomeInProgress_ = true;
   }
   return DEVICE_OK;
}

/**
 * Check if the stage is already zeroed or not.
 */
bool ThorlabsDCStage::IsHomed()
{
   if (Homed_)
      return true;

   return Homed_;
}

int ThorlabsDCStage::IsHomed2(double& home)
{
   if (Homed_)
      home=1;

   return DEVICE_OK;
}

int ThorlabsDCStage::GetVelParam(double& vel)
{
	
	register int i;
	i=0;
	
	MOT_GetVelParams(plSerialNum[i], pfMinVel, pfAccn, pfMaxVel);
	
	vel=(double) pfMaxVel[i];
   
	return DEVICE_OK;
}
int ThorlabsDCStage::SetVelParam(double vel)
{
	register int i;
	i=0;

	newVel[i]=(float)vel;
	MOT_SetVelParams(plSerialNum[i], 0.0f, pfAccn[i], static_cast<float>(vel));

   return DEVICE_OK;
}
///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

/*int ThorlabsDCStage::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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
}*/



int ThorlabsDCStage::OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct)
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
         return ret;

   }

   return DEVICE_OK;
}

int ThorlabsDCStage::OnVelocity(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      double vel;
	  
      int ret = GetVelParam(vel);
      if (ret != DEVICE_OK)
         return ret;

      pProp->Set(vel);
   }
   else if (eAct == MM::AfterSet)
   {
      double vel;
      pProp->Get(vel);
      int ret = SetVelParam(vel);
      if (ret != DEVICE_OK)
         return ret;

   }

   return DEVICE_OK;
}

int ThorlabsDCStage::OnHome(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      double home;
      int ret = IsHomed2(home);
      if (ret != DEVICE_OK)
      return ret;

      pProp->Set(home);
   }
   else if (eAct == MM::AfterSet)
   {
      double home;
      pProp->Get(home);
			int ret = SetHome(home);
			if (ret != DEVICE_OK)
			return ret;
	  
   }

   return DEVICE_OK;
}