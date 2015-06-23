///////////////////////////////////////////////////////////////////////////////
// FILE:          StandaStage.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// AUTHOR:        Ed Simmons - ESImaging www.esimagingsolutions.com
//
// COPYRIGHT:     Ed Simmons 2013
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

#include "StandaStage.h"
#include <cstdio>
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>
#include <algorithm>
#include <iostream>




using namespace std;

// External names used used by the rest of the system
// to load particular device from the "DemoCamera.dll" library

const char* g_StageDeviceName = "StandaZStage";
const char* g_XYStageDeviceName = "StandaXYStage";
const char* g_HubDeviceName = "StandaStageHubDriver";

const char* g_PropertyMinUm = "Stage Low Position(um)";
const char* g_PropertyMaxUm = "Stage High Position(um)";
const char* g_PropertyXMinUm = "X Stage Min Position(um)";
const char* g_PropertyXMaxUm = "X Stage Max Position(um)";
const char* g_PropertyYMinUm = "Y Stage Min Position(um)";
const char* g_PropertyYMaxUm = "Y Stage Max Position(um)";

// the structures used by stage driver
USMC_Devices DVS;
// the rest of these now belong to the stage classes

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
   RegisterDevice(g_StageDeviceName, MM::StageDevice, "Standa Z Stage");
   RegisterDevice(g_XYStageDeviceName, MM::XYStageDevice, "Standa XY Stage");
   RegisterDevice(g_HubDeviceName,  MM::HubDevice, "Standa Stage driver hub (required)");
   
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_StageDeviceName) == 0)
   {
      // create stage
      return new CStandaStage();
   }
   else if (strcmp(deviceName, g_XYStageDeviceName) == 0)
   {
      // create stage
      return new CStandaXYStage();
   }
   else if (strcmp(deviceName, g_HubDeviceName) == 0)
   {
      // create stage
      return new CStandaHub();
   }
   // ...supplied name not recognized
   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}


///////////////////////////////////////////////////////////////////////////////
// CStandaStage implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~

CStandaStage::CStandaStage() : 
stepSizeUm_(0.025),
pos_um_(0.0),
busy_(false),
initialized_(false),
lowerLimit_(0.0),
upperLimit_(20000.0),
stepsPerSecond_(2000),
divisor_(8),
driveID_(0)
{
   InitializeDefaultErrorMessages();

   // parent ID display
   CreateHubIDProperty();
   std::string driveID = "Drive Id";
   CPropertyAction* pAct = new CPropertyAction (this, &CStandaStage::OnDriveSelected);
   CreateProperty(driveID.c_str(), "0", MM::String, false, pAct, true);
   
   pAct = new CPropertyAction (this, &CStandaStage::OnStepSize);
   CreateProperty("Step size (um)", "0", MM::Float, false, pAct, true);
   SetPropertyLimits("Step size (um)", 0, 100);

   pAct = new CPropertyAction (this, &CStandaStage::OnMicrostepMultiplier);
   CreateProperty("Microstep Divisor", "1", MM::Integer, false, pAct);

   vector<string> MicrostepMultipliers;
   MicrostepMultipliers.push_back("1");
   MicrostepMultipliers.push_back("2");
   MicrostepMultipliers.push_back("4");
   MicrostepMultipliers.push_back("8");

   SetAllowedValues("Microstep Divisor", MicrostepMultipliers);

   pAct = new CPropertyAction (this, &CStandaStage::OnStageMinPos); 
   CreateProperty(g_PropertyMinUm, "0", MM::Float, false, pAct, true); 

   pAct = new CPropertyAction (this, &CStandaStage::OnStageMaxPos);      
   CreateProperty(g_PropertyMaxUm, "200", MM::Float, false, pAct, true);   

}

CStandaStage::~CStandaStage()
{
   Shutdown();
}

void CStandaStage::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_StageDeviceName);
}

int CStandaStage::Initialize()
{
   CStandaHub* pHub = static_cast<CStandaHub*>(GetParentHub());
   if (pHub)
   {
      char hubLabel[MM::MaxStrLength];
      pHub->GetLabel(hubLabel);
      SetParentID(hubLabel); // for backward comp.
   }
   else
      LogMessage(NoHubError);

   if (initialized_)
      return DEVICE_OK;

   
   initAxis((DWORD)driveID_, StPrms);

   

   // set property list
   // -----------------

   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, g_StageDeviceName, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "Standa stage driver", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   drives = pHub->GetDriveNames();
   /*
   // selectable drives
   std::string driveID = "Drive Id";
   CPropertyAction* pAct = new CPropertyAction (this, &CStandaStage::OnDriveSelected);
   ret = CreateProperty(driveID.c_str(), "0", MM::String, false, pAct);
   if (DEVICE_OK != ret)
      return ret;
   for(unsigned int i = 0; i< drives.size();i++){
      std::stringstream s;
      s << i;
      AddAllowedValue(driveID.c_str(), s.str().c_str());
   }
   */
   /*
   ret = CreateProperty("Drive Name", "No drive selected", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;
   */

   // Position
   // --------
   CPropertyAction* pAct = new CPropertyAction (this, &CStandaStage::OnPosition);
   ret = CreateProperty(MM::g_Keyword_Position, "0", MM::Float, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // steps per second
   pAct = new CPropertyAction (this, &CStandaStage::OnStepsPerSecond);
   ret = CreateProperty("Steps per second", "0", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   SetPropertyLimits("Steps per second", 2, 5000); 

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

bool CStandaStage::setPower(DWORD Dev,bool state){
   Mode.ResetD = state;

   if( USMC_SetMode(Dev, Mode) )
	   return TRUE; // fail
   return false;// success
}

bool CStandaStage::initAxis(DWORD Dev, USMC_StartParameters StPrms){

   // update the drive info

   if( USMC_GetMode(Dev, Mode) )
		return DEVICE_ERR;

   if( USMC_GetState(Dev, State) )
		return DEVICE_ERR;

   if( USMC_GetStartParameters(Dev, StPrms) )
		return DEVICE_ERR;
   
   // power on this drive 
   if(setPower(Dev,false)) return DEVICE_ERR;


   // test block of start parameters:
   // anti-backlash operation direction:
   StPrms.DefDir = true;
   // Force automatic antibacklash operation
   StPrms.ForceLoft = false;
   // enable automatic backlash operation
   StPrms.LoftEn = false;
   
   
   // slow start, only if steps divisor is 1
   StPrms.SlStart = false;
   // sync counter reset?
   StPrms.SyncOUTR = false;
   // wait for sync ssignal before moving?
   StPrms.WSyncIN = false;


   pos_um_ = State.CurPos * stepSizeUm_;
   //if( USMC_SetCurrentPosition(Dev, 0) ) return DEVICE_ERR;

   // set the limit switches as active
   /*
   Mode.Tr1En = true;// enabled.
   Mode.Tr2En = true;
   Mode.Tr1T = true;// active high, possibly not right here..
   Mode.Tr2T = true;
   */
   if( USMC_SetMode(Dev, Mode))
        return DEVICE_ERR;

   return false;// success
}

int CStandaStage::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
      if(setPower((DWORD)driveID_,false)) return DEVICE_ERR;
   }
   return DEVICE_OK;
}

int CStandaStage::GetPositionSteps(long& steps) {
   if (USMC_GetState((DWORD)driveID_, State))
   {
      return DEVICE_ERR;
   }

   steps = State.CurPos;

   stringstream ss;
   ss << "GetPositionSteps: steps :=" << steps;
   LogMessage(ss.str(), true);

   return DEVICE_OK;
}

int CStandaStage::GetPositionUm(double &pos)
{
   long steps;
   int ret = GetPositionSteps(steps);

   if (ret != DEVICE_OK)
   {
      return ret;
   }

   pos = steps * stepSizeUm_;

   stringstream ss;
   ss << "GetPositionUm: pos :=" << pos << " Steps :=" << steps << " stepSizeUm_ :=" << stepSizeUm_;
   LogMessage(ss.str(), true);

   return DEVICE_OK;
}

int CStandaStage::SetPositionUm(double pos)
{
   long steps = (long) (pos / stepSizeUm_ /*+ 0.5*/);

   stringstream ss;
   ss << "SetPositionUm: pos :=" << pos << " Steps :=" << steps << " stepSizeUm_ :=" << stepSizeUm_;
   LogMessage(ss.str(), true);

   int ret = SetPositionSteps(steps);

   if (ret != DEVICE_OK)
   {
      return ret;
   }

   return OnStagePositionChanged(pos);
}

int CStandaStage::SetPositionSteps(long steps) 
{
   if (USMC_GetStartParameters((DWORD)driveID_, StPrms)) return DEVICE_ERR;
   
   // Set the step divisor to our desired step divisor
   StPrms.SDivisor = (byte) divisor_;
   pos_um_ = steps * stepSizeUm_;

   stringstream ss;
   ss << "SetPositionSteps: Steps :=" << steps << " Divisor :=" << divisor_ << " pos_um_ :=" << pos_um_;
   LogMessage(ss.str(), true);


   // Start the movement of the stepper motor
   if (USMC_Start((DWORD)driveID_, (int) steps, stepsPerSecond_, StPrms))
   {
      return DEVICE_ERR;
   }

   return DEVICE_OK;
}

bool CStandaStage::Busy(){
   if (USMC_GetState((DWORD)driveID_, State))
   {
      stringstream ss;
      ss << "Error in USMC_GetState: driveID_ :=" << driveID_<< ". No way to return an error to MM!";
      LogMessage(ss.str(), true);
      //return DEVICE_ERR;
   }
   stringstream s;
   s << "Busy:=" << State.RUN ;
   LogMessage(s.str(), true);
   if(State.RUN){ // pacify stupid compiler warning
      return true;
   } else {
      return false;
   }
}


///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int CStandaStage::OnDriveSelected(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   //CStandaHub* pHub = static_cast<CStandaHub*>(GetParentHub());
   
   if (eAct == MM::BeforeGet)
   {
      std::stringstream s;
      s << driveID_;
      pProp->Set(s.str().c_str());

   }
   else if (eAct == MM::AfterSet)
   {
      string id;
      pProp->Get(id);
      unsigned int val = 0;
      val = atoi(id.c_str());

      driveID_ = val; // update our driveId
      initAxis((DWORD)driveID_, StPrms);


      //SetProperty("Drive Name", drives[driveID_].c_str());

      return DEVICE_OK;
      
   }
   return DEVICE_OK;
}
/*
int CStandaStage::OnTestProperty(MM::PropertyBase* pProp, MM::ActionType eAct)
{
      CStandaHub* pHub = static_cast<CStandaHub*>(GetParentHub());
   
   if (eAct == MM::BeforeGet)
   {
      std::stringstream s;
      s << Dev;
      pProp->Set(s.str().c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      long id;
      pProp->Get(id);
      
      Dev = (DWORD) id;
      initAxis(Dev, StPrms);
   }

   return DEVICE_OK;
}
*/
int CStandaStage::OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   int ret = DEVICE_ERR;
   // check this - error on beforeget
   if (eAct == MM::BeforeGet)
   {
      std::stringstream s;
      s << pos_um_;
      pProp->Set(s.str().c_str());

      
      ret = DEVICE_OK;
   }
   else if (eAct == MM::AfterSet)
   {
      double pos;
      pProp->Get(pos);
      if (pos > upperLimit_ || lowerLimit_ > pos)
      {
         pProp->Set(pos_um_); // revert
         return ERR_UNKNOWN_POSITION;
      }
      
      SetPositionUm(pos);
      ret = DEVICE_OK;
   }

   return ret;
}

int CStandaStage::OnStepsPerSecond(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   int ret = DEVICE_ERR;
   if (eAct == MM::BeforeGet)
   {
      std::stringstream s;
      s << stepsPerSecond_;
      pProp->Set(s.str().c_str());
      ret = DEVICE_OK;
   }
   else if (eAct == MM::AfterSet)
   {
      double sps;
      pProp->Get(sps);
      stepsPerSecond_ = (float) sps;

      ret = DEVICE_OK;
   }

   return ret;
}

int CStandaStage::OnStepSize(MM::PropertyBase* pProp, MM::ActionType eAct)
{

   // check this - error on beforeget
   int ret = DEVICE_ERR;
   if (eAct == MM::BeforeGet)
   {
      std::stringstream s;
      s << stepSizeUm_;
      pProp->Set(s.str().c_str());
      ret = DEVICE_OK;
   }
   else if (eAct == MM::AfterSet)
   {
      double stepSize;
      pProp->Get(stepSize);
      stepSizeUm_ = stepSize;

      ret = DEVICE_OK;
   }

   return ret;
}

int CStandaStage::OnMicrostepMultiplier(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   int ret = DEVICE_ERR;
   if (eAct == MM::BeforeGet)
   {
      pProp->Set((long)divisor_);
      ret = DEVICE_OK;
   }
   else if (eAct == MM::AfterSet)
   {
      long div;
      pProp->Get(div);

      divisor_ = div;
      
      ret = DEVICE_OK;
   }

   return ret;
}

int CStandaStage::OnStageMinPos(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   int ret = DEVICE_ERR;
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(lowerLimit_);
      ret = DEVICE_OK;
   }
   else if (eAct == MM::AfterSet)
   {
      double limit;
      pProp->Get(limit);

      lowerLimit_ = limit;
      
      ret = DEVICE_OK;
   }

   return ret;
}

int CStandaStage::OnStageMaxPos(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   int ret = DEVICE_ERR;
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(upperLimit_);
      ret = DEVICE_OK;
   }
   else if (eAct == MM::AfterSet)
   {
      double limit;
      pProp->Get(limit);

      upperLimit_ = limit;
      
      ret = DEVICE_OK;
   }

   return ret;
}

///////////////////////////////////////////////////////////////////////////////
// CStandaXYStage implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~

CStandaXYStage::CStandaXYStage() : 
CXYStageBase<CStandaXYStage>(),
stepSize_X_um_(0.015),
stepSize_Y_um_(0.015),
stepsPerSecond_(2000.0),
posX_um_(0.0),
posY_um_(0.0),
driveID_X_(0),
driveID_Y_(0),
busy_(false),
initialized_(false),
lowerLimitX_(0.0),
upperLimitX_(20000.0),
lowerLimitY_(0.0),
upperLimitY_(20000.0)
{
   InitializeDefaultErrorMessages();

   // parent ID display
   CreateHubIDProperty();

   // drive IDs
   std::string XdriveID = "X Drive Id";
   std::string YdriveID = "Y Drive Id";
   CPropertyAction* pAct = new CPropertyAction (this, &CStandaXYStage::OnXDriveSelected);
   CreateProperty(XdriveID.c_str(), "0", MM::String, false, pAct, true);

   
   pAct = new CPropertyAction (this, &CStandaXYStage::OnYDriveSelected);
   CreateProperty(YdriveID.c_str(), "0", MM::String, false, pAct, true);

      // step size
   pAct = new CPropertyAction (this, &CStandaXYStage::OnXStepSize);
   CreateProperty("X Step size (um)", "0", MM::Float, false, pAct, true);
   
   SetPropertyLimits("X Step size (um)", 0, 100);

   pAct = new CPropertyAction (this, &CStandaXYStage::OnYStepSize);
   CreateProperty("Y Step size (um)", "0", MM::Float, false, pAct, true);
   
   SetPropertyLimits("Y Step size (um)", 0, 100);

   pAct = new CPropertyAction (this, &CStandaXYStage::OnXStageMinPos); 
   CreateProperty(g_PropertyXMinUm, "0", MM::Float, false, pAct, true); 

   pAct = new CPropertyAction (this, &CStandaXYStage::OnXStageMaxPos);      
   CreateProperty(g_PropertyXMaxUm, "200", MM::Float, false, pAct, true);

   pAct = new CPropertyAction (this, &CStandaXYStage::OnYStageMinPos); 
   CreateProperty(g_PropertyYMinUm, "0", MM::Float, false, pAct, true); 

   pAct = new CPropertyAction (this, &CStandaXYStage::OnYStageMaxPos);      
   CreateProperty(g_PropertyYMaxUm, "200", MM::Float, false, pAct, true);

   // micro-step divisor

   pAct = new CPropertyAction (this, &CStandaXYStage::OnMicrostepMultiplier);
   CreateProperty("Microstep Divisor", "1", MM::Integer, false, pAct, true);
   
   vector<string> MicrostepMultipliers;
   MicrostepMultipliers.push_back("1");
   MicrostepMultipliers.push_back("2");
   MicrostepMultipliers.push_back("4");
   MicrostepMultipliers.push_back("8");

   SetAllowedValues("Microstep Divisor", MicrostepMultipliers);

}

CStandaXYStage::~CStandaXYStage()
{
   Shutdown();
}

void CStandaXYStage::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_XYStageDeviceName);
}

int CStandaXYStage::Initialize()
{
   CStandaHub* pHub = static_cast<CStandaHub*>(GetParentHub());
   if (pHub)
   {
      char hubLabel[MM::MaxStrLength];
      pHub->GetLabel(hubLabel);
      SetParentID(hubLabel); // for backward comp.
   }
   else
      LogMessage(NoHubError);

   if (initialized_)
      return DEVICE_OK;

   // set property list
   // -----------------

   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, g_XYStageDeviceName, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "Standa XY stage driver", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;


      // steps per second
   CPropertyAction* pAct = new CPropertyAction (this, &CStandaXYStage::OnStepsPerSecond);
   ret = CreateProperty("Steps per second", "0", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   SetPropertyLimits("Steps per second", 2, 5000);  


   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

int CStandaXYStage::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

bool CStandaXYStage::Busy(){
   if (USMC_GetState((DWORD)driveID_X_, X_State))
   {
      stringstream ss;
      ss << "Error in USMC_GetState: driveID_ :=" << driveID_X_<< ". No way to return an error to MM!";
      LogMessage(ss.str(), true);
      //return DEVICE_ERR;
   }
   if (USMC_GetState((DWORD)driveID_Y_, Y_State))
   {
      /*
      stringstream ss;
      ss << "Error in USMC_GetState: driveID_ :=" << driveID_Y_<< ". No way to return an error to MM!";
      LogMessage(ss.str(), true);
      */
      //return DEVICE_ERR;
   }
   stringstream s;
   s << "Busy: X running :=" << X_State.RUN << " Y running :=" << Y_State.RUN;
   LogMessage(s.str(), true);
   if(X_State.RUN || Y_State.RUN ){
      return true;
   } else {
      return false;
   }
}

bool CStandaXYStage::setPower(DWORD Dev,bool state, USMC_Mode Mode){
   Mode.ResetD = state;

   if( USMC_SetMode(Dev, Mode) )
	   return TRUE; // fail
   return false;// success
}

bool CStandaXYStage::initAxis(DWORD Dev, USMC_StartParameters StPrms, USMC_Mode Mode, USMC_State State){

   // update the drive info

   if( USMC_GetMode(Dev, Mode) )
		return DEVICE_ERR;

   if( USMC_GetState(Dev, State) )
		return DEVICE_ERR;

   if( USMC_GetStartParameters(Dev, StPrms) )
		return DEVICE_ERR;
   
   // power on this drive 
   if(setPower(Dev,false, Mode)) return DEVICE_ERR;


   // test block of start parameters:
   // anti-backlash operation direction:
   StPrms.DefDir = true;
   // Force automatic antibacklash operation
   StPrms.ForceLoft = false;
   // enable automatic backlash operation
   StPrms.LoftEn = false;
   
   
   // slow start, only if steps divisor is 1
   StPrms.SlStart = false;
   // sync counter reset?
   StPrms.SyncOUTR = false;
   // wait for sync ssignal before moving?
   StPrms.WSyncIN = false;

   // set the limit switches as active
   /*
   Mode.Tr1En = true;// enabled.
   Mode.Tr2En = true;
   Mode.Tr1T = true;// active high, possibly not right here..
   Mode.Tr2T = true;
   */
   if( USMC_SetMode(Dev, Mode))
        return DEVICE_ERR;
        

   return false;// success
}

int CStandaXYStage::GetPositionSteps(long& x, long& y){
   if (USMC_GetState((DWORD)driveID_X_, X_State))
   {
      return DEVICE_ERR;
   }
   if (USMC_GetState((DWORD)driveID_Y_, Y_State))
   {
      return DEVICE_ERR;
   }

   x = X_State.CurPos;
   y = Y_State.CurPos;

   stringstream ss;
   ss << "GetPositionSteps :=" << x << "," << y;
   LogMessage(ss.str(), true);
   return DEVICE_OK;
}

 int CStandaXYStage::SetPositionSteps(long x, long y)
   {
      posX_um_ = x * stepSize_X_um_;
      posY_um_ = y * stepSize_Y_um_;

      X_StPrms.DefDir = false;
      X_StPrms.LoftEn = false;
      X_StPrms.WSyncIN = false;
      X_StPrms.SlStart = true;

      Y_StPrms.DefDir = false;
      Y_StPrms.LoftEn = false;
      Y_StPrms.WSyncIN = false;
      Y_StPrms.SlStart = true;

      stringstream ss;
      ss << "Current position = " << X_State.CurPos << "," << Y_State.CurPos << " \n Commanded position = " << x << "," << y;
      LogMessage(ss.str(), true);

      if(USMC_Start((DWORD)driveID_X_,(int)x,stepsPerSecond_,X_StPrms)) {
         return DEVICE_ERR;
      }
      if(USMC_Start((DWORD)driveID_Y_,(int)y,stepsPerSecond_,Y_StPrms)) {
         return DEVICE_ERR;
      }

      return OnXYStagePositionChanged(posX_um_, posY_um_);
   }

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////
int CStandaXYStage::OnXDriveSelected(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   //CStandaHub* pHub = static_cast<CStandaHub*>(GetParentHub());
   
   if (eAct == MM::BeforeGet)
   {
      std::stringstream s;
      s << driveID_X_;
      pProp->Set(s.str().c_str());

   }
   else if (eAct == MM::AfterSet)
   {
      string id;
      pProp->Get(id);
      unsigned int val = 0;
      val = atoi(id.c_str());

      driveID_X_ = val; // update our driveId
      initAxis((DWORD)driveID_X_, X_StPrms, X_Mode, X_State);

      return DEVICE_OK;
      
   }
   return DEVICE_OK;
}

int CStandaXYStage::OnYDriveSelected(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   //CStandaHub* pHub = static_cast<CStandaHub*>(GetParentHub());
   
   if (eAct == MM::BeforeGet)
   {
      std::stringstream s;
      s << driveID_Y_;
      pProp->Set(s.str().c_str());

   }
   else if (eAct == MM::AfterSet)
   {
      string id;
      pProp->Get(id);
      unsigned int val = 0;
      val = atoi(id.c_str());

      driveID_Y_ = val; // update our driveId
      initAxis((DWORD)driveID_Y_, Y_StPrms, Y_Mode, Y_State);

      return DEVICE_OK; 
   }
   return DEVICE_OK;
}

int CStandaXYStage::OnStepsPerSecond(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   int ret = DEVICE_ERR;
   if (eAct == MM::BeforeGet)
   {
      std::stringstream s;
      s << stepsPerSecond_;
      pProp->Set(s.str().c_str());
      ret = DEVICE_OK;
   }
   else if (eAct == MM::AfterSet)
   {
      double sps;
      pProp->Get(sps);
      stepsPerSecond_ = (float) sps;

      ret = DEVICE_OK;
   }

   return ret;
}

int CStandaXYStage::OnXStepSize(MM::PropertyBase* pProp, MM::ActionType eAct)
{

   int ret = DEVICE_ERR;
   if (eAct == MM::BeforeGet)
   {
      std::stringstream s;
      s << stepSize_X_um_;
      pProp->Set(s.str().c_str());
      ret = DEVICE_OK;
   }
   else if (eAct == MM::AfterSet)
   {
      double stepSize;
      pProp->Get(stepSize);
      stepSize_X_um_ = stepSize;

      ret = DEVICE_OK;
   }

   return ret;
}

int CStandaXYStage::OnYStepSize(MM::PropertyBase* pProp, MM::ActionType eAct)
{

   int ret = DEVICE_ERR;
   if (eAct == MM::BeforeGet)
   {
      std::stringstream s;
      s << stepSize_Y_um_;
      pProp->Set(s.str().c_str());
      ret = DEVICE_OK;
   }
   else if (eAct == MM::AfterSet)
   {
      double stepSize;
      pProp->Get(stepSize);
      stepSize_Y_um_ = stepSize;

      ret = DEVICE_OK;
   }

   return ret;
}


int CStandaXYStage::OnMicrostepMultiplier(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   int ret = DEVICE_ERR;
   if (eAct == MM::BeforeGet)
   {
      unsigned int div = X_StPrms.SDivisor;
      pProp->Set((long)div);
      ret = DEVICE_OK;
   }
   else if (eAct == MM::AfterSet)
   {
      long mult;
      pProp->Get(mult);
      X_StPrms.SDivisor = (byte) mult;
      Y_StPrms.SDivisor = (byte) mult;

      ret = DEVICE_OK;
   }

   return ret;
}


int CStandaXYStage::OnXStageMinPos(MM::PropertyBase* pProp, MM::ActionType eAct){
   int ret = DEVICE_ERR;
   if (eAct == MM::BeforeGet)
   {
      
      pProp->Set(lowerLimitX_);
      ret = DEVICE_OK;
   }
   else if (eAct == MM::AfterSet)
   {
      double limit;
      pProp->Get(limit);
      lowerLimitX_ = limit;

      ret = DEVICE_OK;
   }

   return ret;
}
   
int CStandaXYStage::OnXStageMaxPos(MM::PropertyBase* pProp, MM::ActionType eAct){
   int ret = DEVICE_ERR;
   if (eAct == MM::BeforeGet)
   {
      
      pProp->Set(upperLimitX_);
      ret = DEVICE_OK;
   }
   else if (eAct == MM::AfterSet)
   {
      double limit;
      pProp->Get(limit);
      upperLimitX_ = limit;

      ret = DEVICE_OK;
   }

   return ret;
}
   
int CStandaXYStage::OnYStageMinPos(MM::PropertyBase* pProp, MM::ActionType eAct){
   int ret = DEVICE_ERR;
   if (eAct == MM::BeforeGet)
   {
      
      pProp->Set(lowerLimitY_);
      ret = DEVICE_OK;
   }
   else if (eAct == MM::AfterSet)
   {
      double limit;
      pProp->Get(limit);
      lowerLimitY_ = limit;

      ret = DEVICE_OK;
   }

   return ret;
}

int CStandaXYStage::OnYStageMaxPos(MM::PropertyBase* pProp, MM::ActionType eAct){
   int ret = DEVICE_ERR;
   if (eAct == MM::BeforeGet)
   {
      
      pProp->Set(upperLimitY_);
      ret = DEVICE_OK;
   }
   else if (eAct == MM::AfterSet)
   {
      double limit;
      pProp->Get(limit);
      upperLimitY_ = limit;

      ret = DEVICE_OK;
   }

   return ret;
}


// hub land 


CStandaHub::CStandaHub():
   initialized_(false), 
   busy_(false)
{
   SetErrorText(DEVICE_NOT_CONNECTED, "There are no Standa Stepper drives detected");
} 


int CStandaHub::Initialize()
{
if(!initialized_){
   if(USMC_Init(DVS) ) return DEVICE_NOT_CONNECTED; // try to find any standa devices attached to the system
   std::ostringstream os2;
   numberOfDevices_ = DVS.NOD;
   os2<<numberOfDevices_;

   CreateProperty("Number of Drives" , os2.str().c_str(), MM::String, true);

   for(DWORD i = 0; i < DVS.NOD; i++)
   {
      std::ostringstream oss, oss2;
      oss <<"Standa Device " << i; // create a name that's vaguely meaningful
      
      oss2 << "Serial Number " << DVS.Serial[i] << " Version - " << DVS.Version[i] ; // and assemble some detials about the found device
      // now add a read only property to show this info.
      CreateProperty(oss.str().c_str(), oss2.str().c_str(), MM::String, true);

      oss << oss2.str().c_str();
      // add these devices to a list here, later to be used for keeping track of which device is a particular axis
      // the peripheral devices can then access these to choose a drive for a given axis
      driveNames_.push_back(oss.str());
      LogMessage(oss.str().c_str());
   }

   initialized_ = true;
}
	return DEVICE_OK;
}

std::vector<std::string> CStandaHub::GetDriveNames()
{
   return driveNames_;
}

int CStandaHub::DetectInstalledDevices()
{  
   ClearInstalledDevices();

   // make sure this method is called before we look for available devices
   InitializeModuleData();

   char hubName[MM::MaxStrLength];
   GetName(hubName); // this device name
   for (unsigned i=0; i<GetNumberOfDevices(); i++)
   { 
      char deviceName[MM::MaxStrLength];
      bool success = GetDeviceName(i, deviceName, MM::MaxStrLength);
      if (success && (strcmp(hubName, deviceName) != 0))
      {
         MM::Device* pDev = CreateDevice(deviceName);
         AddInstalledDevice(pDev);
      }
   }
   return DEVICE_OK; 
}

MM::Device* CStandaHub::CreatePeripheralDevice(const char* adapterName)
{
   for (unsigned i=0; i<GetNumberOfInstalledDevices(); i++)
   {
      MM::Device* d = GetInstalledDevice(i);
      char name[MM::MaxStrLength];
      d->GetName(name);
      if (strcmp(adapterName, name) == 0)
         return CreateDevice(adapterName);

   }
   return 0; // adapter name not found
}



void CStandaHub::GetName(char* pName) const
{
   CDeviceUtils::CopyLimitedString(pName, g_HubDeviceName);
}
