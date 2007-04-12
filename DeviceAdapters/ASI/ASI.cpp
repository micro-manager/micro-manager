///////////////////////////////////////////////////////////////////////////////
// FILE:          ASI.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   ASI ProScan controller adapter
// COPYRIGHT:     University of California, San Francisco, 2006
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
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 06/01/2006
//
// CVS:           $Id: ASI.cpp,v 1.1 2007/02/27 23:33:14 nenad Exp $
//

#ifdef WIN32
   //#include <windows.h>
   #define snprintf _snprintf 
#endif

#include "ASI.h"
#include "ASIFW1000Hub.h"
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>

#include <iostream>
using namespace std;

//from prior
const char* g_XYStageDeviceName = "XYStage";
const char* g_ZStageDeviceName = "ZStage";
//const char* g_BasicControllerName = "BasicController";
//const char* g_Shutter1Name="Shutter-1";
//const char* g_Shutter2Name="Shutter-2";
//const char* g_Shutter3Name="Shutter-3";
//const char* g_Wheel1Name = "Wheel-1";
//const char* g_Wheel2Name = "Wheel-2";
//const char* g_Wheel3Name = "Wheel-3";
// eof from prior

const char* g_ASIFW1000Hub = "Controller";
const char* g_ASIFW1000FilterWheel = "Filter Wheel";
const char* g_ASIFW1000FilterWheelNr = "Filter Wheel Nr";
const char* g_ASIFW1000Shutter = "Shutter";
const char* g_ASIFW1000ShutterNr = "Shutter Nr";

using namespace std;

ASIFW1000Hub g_hub;

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   // from prior
   //AddAvailableDeviceName(g_Shutter1Name, "Pro Scan shutter 1");
   //AddAvailableDeviceName(g_Shutter2Name, "Pro Scan shutter 2");
   //AddAvailableDeviceName(g_Shutter3Name, "Pro Scan shutter 3");
   //AddAvailableDeviceName(g_Wheel1Name, "Pro Scan filter wheel 1");
   //AddAvailableDeviceName(g_Wheel2Name, "Pro Scan filter wheel 2");
   //AddAvailableDeviceName(g_Wheel3Name, "Pro Scan filter wheel 3");
   AddAvailableDeviceName(g_ZStageDeviceName, "Add-on Z-stage");
   AddAvailableDeviceName(g_XYStageDeviceName, "XY Stage");
   // eof from prior

   AddAvailableDeviceName(g_ASIFW1000Hub,"ASIFW1000 Controller");
   AddAvailableDeviceName(g_ASIFW1000FilterWheel,"FilterWheel)");   
   AddAvailableDeviceName(g_ASIFW1000Shutter,"Shutter"); 
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_ASIFW1000Hub) == 0)
   {
      return new Hub();
   }
   else if (strcmp(deviceName, g_ASIFW1000FilterWheel) == 0 )
   {
      return new FilterWheel();
   }
   else if (strcmp(deviceName, g_ASIFW1000Shutter) == 0 )
   {
      return new Shutter();
   }
   else if (strcmp(deviceName, g_ZStageDeviceName) == 0)
   {
      ZStage* s = new ZStage();
      return s;
   }
   else if (strcmp(deviceName, g_XYStageDeviceName) == 0)
   {
      XYStage* s = new XYStage();
      return s;
   }

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
// ASIFW1000 Hub
///////////////////////////////////////////////////////////////////////////////

Hub::Hub() :
   initialized_(false),
   port_("Undefined")
{
   InitializeDefaultErrorMessages();

   // custom error messages
   SetErrorText(ERR_COMMAND_CANNOT_EXECUTE, "Command cannot be executed");

   // create pre-initialization properties
   // ------------------------------------

   // Port
   CPropertyAction* pAct = new CPropertyAction (this, &Hub::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

}

Hub::~Hub()
{
   Shutdown();
}

void Hub::GetName(char* name) const
{
   //assert(name_.length() < CDeviceUtils::GetMaxStringLength());   
   CDeviceUtils::CopyLimitedString(name, g_ASIFW1000Hub);
}

bool Hub::Busy()
{
   return false;
}

int Hub::Initialize()
{
   // set property list
   // -----------------
   
   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, g_ASIFW1000Hub, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "ASIFW1000 controller", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Version
   char version[256];
   ret = g_hub.GetVersion(*this, *GetCoreCallback(), version);
   if (DEVICE_OK != ret)
      return ret;
   ret = CreateProperty("Firmware version", version, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;
   
   return DEVICE_OK;
}

int Hub::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////
/*
 * Sets the Serial Port to be used.
 * Should be called before initialization
 */
int Hub::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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
      g_hub.SetPort(port_.c_str());
   }
   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// ASIFW1000 FilterWheel
///////////////////////////////////////////////////////////////////////////////
FilterWheel::FilterWheel () :
   initialized_ (false),
   name_ (g_ASIFW1000FilterWheel),
   pos_ (1),
   wheelNr_ (0),
   numPos_ (6)
{
   InitializeDefaultErrorMessages();

   // Todo: Add custom messages
   //
   // create pre-initialization properties
   // ------------------------------------

   // FilterWheel Nr (0 or 1)
   CPropertyAction* pAct = new CPropertyAction (this, &FilterWheel::OnWheelNr);
   CreateProperty(g_ASIFW1000FilterWheelNr, "0", MM::Integer, false, pAct, true);

   AddAllowedValue(g_ASIFW1000FilterWheelNr, "0"); 
   AddAllowedValue(g_ASIFW1000FilterWheelNr, "1");

}

FilterWheel::~FilterWheel ()
{
   Shutdown();
}

void FilterWheel::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int FilterWheel::Initialize()
{
   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "ASIFW1000 ND Filter", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // State
   CPropertyAction* pAct = new CPropertyAction (this, &FilterWheel::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct); 
   if (ret != DEVICE_OK) 
      return ret; 
   // Get the number of filters in this wheel and add these as allowed values
   ret = g_hub.GetNumberOfPositions(*this, *GetCoreCallback(), wheelNr_, numPos_);
   if (ret != DEVICE_OK) 
      return ret; 
   char pos[3];
   for (int i=0; i<numPos_; i++) 
   {
      sprintf(pos, "%d", i);
      AddAllowedValue(MM::g_Keyword_State, pos);
   }

   // Label
   pAct = new CPropertyAction (this, &CStateBase::OnLabel);
   ret = CreateProperty(MM::g_Keyword_Label, "Undefined", MM::String, false, pAct);
   if (ret != DEVICE_OK) 
      return ret;

   // create default positions and labels
   char state[8];
   for (int i=0; i<numPos_; i++) 
   {
      sprintf(state, "State-%d", i);
      SetPositionLabel(i,state);
   }

   ret = UpdateStatus();
   if (ret != DEVICE_OK) 
      return ret; 

   initialized_ = true;

   return DEVICE_OK;
}

bool FilterWheel::Busy()
{
   bool busy;
   g_hub.FilterWheelBusy(*this, *GetCoreCallback(), busy);
   return busy;
}

int FilterWheel::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers                                                           
///////////////////////////////////////////////////////////////////////////////


int FilterWheel::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // return pos as we know it
      pProp->Set(pos_);
   }
   else if (eAct == MM::AfterSet)
   {
      long pos;
      pProp->Get(pos);
      if (pos == pos_)
         return DEVICE_OK;
      if (pos < 0)
         pos = 0;
      else if (pos > 1)
         pos = 1;
      int ret = g_hub.SetFilterWheelPosition(*this, *GetCoreCallback(), wheelNr_, pos);
      if (ret == DEVICE_OK) {
         pos_ = pos;
         pProp->Set(pos_);
         return DEVICE_OK;
      }
      else
         return  ret;
   }
   return DEVICE_OK;
}

int FilterWheel::OnWheelNr(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // return wheel nr
      pProp->Set((long)wheelNr_);
   }
   else if (eAct == MM::AfterSet)
   {
      if (initialized_)
         return ERR_CANNOT_CHANGE_PROPERTY;
      long pos;
      pProp->Get(pos);
      if (pos==0 || pos==1)
         wheelNr_ = pos;
   }
   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// ASIFW1000 Shutter
///////////////////////////////////////////////////////////////////////////////
Shutter::Shutter () :
   initialized_ (false),
   name_ (g_ASIFW1000Shutter),
   shutterNr_ (0),
   state_ (1)
{
   InitializeDefaultErrorMessages();

   // Todo: Add custom messages
   //
   // Shutter Nr (0 or 1)
   CPropertyAction* pAct = new CPropertyAction (this, &Shutter::OnShutterNr);
   CreateProperty(g_ASIFW1000ShutterNr, "0", MM::Integer, false, pAct, true);

   AddAllowedValue(g_ASIFW1000ShutterNr, "0"); 
   AddAllowedValue(g_ASIFW1000ShutterNr, "1");
}

Shutter::~Shutter ()
{
   Shutdown();
}

void Shutter::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int Shutter::Initialize()
{
   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "ASIFW1000 Shutter", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Check current state of shutter:
   ret = g_hub.GetShutterPosition(*this, *GetCoreCallback(), shutterNr_, state_);
   if (DEVICE_OK != ret)
      return ret;

   // State
   CPropertyAction* pAct = new CPropertyAction (this, &Shutter::OnState);
   if (state_)
      ret = CreateProperty(MM::g_Keyword_State, "1", MM::Integer, false, pAct); 
   else
      ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct); 

   if (ret != DEVICE_OK) 
      return ret; 

   AddAllowedValue(MM::g_Keyword_State, "0"); // Closed
   AddAllowedValue(MM::g_Keyword_State, "1"); // Open

   //Label

   ret = UpdateStatus();
   if (ret != DEVICE_OK) 
      return ret; 

   initialized_ = true;

   return DEVICE_OK;
}

bool Shutter::Busy()
{
   // Who knows?
   return false;
}

int Shutter::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

int Shutter::SetOpen(bool open)
{
   if (open)
   {
      int ret = g_hub.OpenShutter(*this, *GetCoreCallback(), shutterNr_);
      if (ret != DEVICE_OK)
         return ret;
      state_ =  true;
   } else
   {
      int ret = g_hub.CloseShutter(*this, *GetCoreCallback(), shutterNr_);
      if (ret != DEVICE_OK)
         return ret;
      state_ =  false;
   }
   return DEVICE_OK;
}

int Shutter::GetOpen(bool &open)
{

   // Check current state of shutter: (this might not be necessary, since we cash ourselves)
   int ret = g_hub.GetShutterPosition(*this, *GetCoreCallback(), shutterNr_, state_);
   if (DEVICE_OK != ret)
      return ret;
   open = state_;

   return DEVICE_OK;
}

int Shutter::Fire(double deltaT)
{
   return DEVICE_UNSUPPORTED_COMMAND;  
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers                                                           
///////////////////////////////////////////////////////////////////////////////

int Shutter::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // return pos as we know it
      GetOpen(state_);
      if (state_)
         pProp->Set("Open");
      else
         pProp->Set("Closed");
   }
   else if (eAct == MM::AfterSet)
   {
      std::string state;
      pProp->Get(state);
      if (state == "Open")
      {
         pProp->Set("Open");
         return this->SetOpen(true);
      }
      else
      {
         pProp->Set("Closed");
         return this->SetOpen(false);
      }
   }
   return DEVICE_OK;
}

int Shutter::OnShutterNr(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // return shutter nr
      pProp->Set((long)shutterNr_);
   }
   else if (eAct == MM::AfterSet)
   {
      if (initialized_)
         return ERR_CANNOT_CHANGE_PROPERTY;
      long pos;
      pProp->Get(pos);
      if (pos==0 || pos==1)
         shutterNr_ = pos;
   }
   return DEVICE_OK;
}




///////////////////////////////////////////////////////////////////////////////
// XYStage
//
XYStage::XYStage() :
   initialized_(false), port_("Undefined"), stepSizeXUm_(0.0), stepSizeYUm_(0.0), answerTimeoutMs_(1000)
{
   InitializeDefaultErrorMessages();

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, g_XYStageDeviceName, MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "ASI XY stage driver adapter", MM::String, true);

   // Port
   CPropertyAction* pAct = new CPropertyAction (this, &XYStage::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);
   stopSignal_ = false;
}

XYStage::~XYStage()
{
   Shutdown();
}

void XYStage::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_XYStageDeviceName);
}

int XYStage::Initialize()
{

   // check status first
   const char* command = "/"; // check STATUS
   // send command
   int ret = SendSerialCommand(port_.c_str(), command, "\r\n");
   if (ret != DEVICE_OK)
      return ret;

   string answer;
   ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
   if (ret != DEVICE_OK)
      return false;

   // set stage step size and resolution
   double resX, resY;
   // default values
   resX = 0.1;
   resY = 0.1;
   // if find a function can get the step size in the future, can fit it here

   stepSizeXUm_ = resX;
   stepSizeYUm_ = resY;

   // Step size
   CPropertyAction* pAct = new CPropertyAction (this, &XYStage::OnStepSizeX);
   CreateProperty("StepSizeX_um", "0.0", MM::Float, true, pAct);
   pAct = new CPropertyAction (this, &XYStage::OnStepSizeY);
   CreateProperty("StepSizeY_um", "0.0", MM::Float, true, pAct);

   ret = UpdateStatus(); // maynot need this too!! 
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;
   return DEVICE_OK;
}

int XYStage::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

bool XYStage::Busy()
{
   const char* command = "/";
   // send command
   int ret = SendSerialCommand(port_.c_str(), command, "\r\n");
   if (ret != DEVICE_OK)
      return false;

   // block/wait for acknowledge, or until we time out;
   string answer;
   ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
   if (ret != DEVICE_OK)
      return false;

   if (answer.length() >= 1)
   {
	  if (answer.substr(0,1) == "B") return true;
	  else if (answer.substr(0,1) == "N") return false;
	  else return false;
   }

   return false;
}



int XYStage::SetPositionUm(double x, double y)
{

   // for ASI

   ostringstream command;
   //command << "M X=" << x*10 << " Y=" << y*10; // in 10th of micros
   command << "M X=" << x/stepSizeXUm_ << " Y=" << y/stepSizeYUm_; // in 10th of micros

   // send command
   int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
   if (ret != DEVICE_OK)
      return ret;

   // block/wait for acknowledge, or until we time out;
   string answer;
   ret = GetSerialAnswer(port_.c_str(), "\r", answer);
   if (ret != DEVICE_OK)
      return ret;

   if (answer.substr(0,2).compare(":A") == 0)
   {
      return DEVICE_OK;
   }
   // deal with error later
   else if (answer.substr(0, 2).compare(":N") == 0 && answer.length() > 2)
   {
      int errNo = atoi(answer.substr(4).c_str());
      return ERR_OFFSET + errNo;
   }

   return ERR_UNRECOGNIZED_ANSWER;  
}

int XYStage::GetPositionUm(double& x, double& y)
{
   ostringstream command;
   command << "W X Y";

   // send command
   int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r\n");
   if (ret != DEVICE_OK)
      return ret;

   // block/wait for acknowledge, or until we time out;
   string answer;
   ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
   if (ret != DEVICE_OK)
      return ret;

   if (answer.length() > 2 && answer.substr(0, 2).compare(":N") == 0)
   {
      int errNo = atoi(answer.substr(2).c_str());
      return ERR_OFFSET + errNo;
   }
   else if (answer.length() > 0)
   {
      char head[64];
	  float xx, yy;
	  char iBuf[256];
	  strcpy(iBuf,answer.c_str());
	  sscanf(iBuf, "%s %f %f\r\n", head, &xx, &yy);
	  //x = xx/10;
	  //y = yy/10;
	  x = xx*stepSizeXUm_;
	  y = yy*stepSizeXUm_;

      return DEVICE_OK;
   }

   return ERR_UNRECOGNIZED_ANSWER;
}
  
int XYStage::SetPositionSteps(long x, long y)
{
   // for prior, may not need it for ASI

   //ostringstream command;
   //command << "G," << x << "," << y;

   //// send command
   //int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
   //if (ret != DEVICE_OK)
   //   return ret;

   //// block/wait for acknowledge, or until we time out;
   //string answer;
   //ret = GetSerialAnswer(port_.c_str(), "\r", answer);
   //if (ret != DEVICE_OK)
   //   return ret;

   //if (answer.substr(0,1).compare("R") == 0)
   //{
   //   return DEVICE_OK;
   //}
   //else if (answer.substr(0, 1).compare("E") == 0 && answer.length() > 2)
   //{
   //   int errNo = atoi(answer.substr(2).c_str());
   //   return ERR_OFFSET + errNo;
   //}

   return ERR_UNRECOGNIZED_ANSWER;   
}
 
int XYStage::GetPositionSteps(long& x, long& y)
{
   //// for prior
   //int ret = GetPositionStepsSingle('X', x);
   //if (ret != DEVICE_OK)
   //   return ret;

   //return GetPositionStepsSingle('Y', y);

   return 0; // remove it if need this function later
}

int XYStage::SetOrigin()
{
   // send command
   int ret = SendSerialCommand(port_.c_str(), "H X Y", "\r"); // use command HERE, zero (z) zero all x,y,z
   if (ret != DEVICE_OK)
      return ret;

   // block/wait for acknowledge, or until we time out;
   string answer;
   ret = GetSerialAnswer(port_.c_str(), "\r", answer);
   if (ret != DEVICE_OK)
      return ret;

   if (answer.substr(0,2).compare(":A") == 0)
   {
      return DEVICE_OK;
   }
   else if (answer.substr(0, 2).compare(":N") == 0 && answer.length() > 2)
   {
      int errNo = atoi(answer.substr(2,4).c_str());
      return ERR_OFFSET + errNo;
   }

   return ERR_UNRECOGNIZED_ANSWER;   
};

//bool XYStage::XyIsBusy(){
//
//   const char* command = "/";
//   // send command
//   int ret = SendSerialCommand(port_.c_str(), command, "\r\n");
//   if (ret != DEVICE_OK)
//      return true;
//
//   // block/wait for acknowledge, or until we time out;
//   string answer;
//   ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
//   if (ret != DEVICE_OK)
//      return true;
//
//   if (answer.length() >= 1)
//   {
//	  if (answer.substr(0,1) == "B") return true;
//	  else if (answer.substr(0,1) == "N") return false;
//	  else return true;
//   }
//
//   return true;
//
//}

void XYStage::Wait()
{

   //if (stopSignal_) return DEVICE_OK;
   bool busy=true;
   const char* command = "/";
   // send command
   int ret = SendSerialCommand(port_.c_str(), command, "\r\n");
   //if (ret != DEVICE_OK)
   //   return ret;
   // get answer
   string answer="";
   ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
   //if (ret != DEVICE_OK)
   //   return ret;

   // block/wait for acknowledge, or until we time out;
   
   if (answer.substr(0,1) == "B") busy = true;
   else if (answer.substr(0,1) == "N") busy = false;
   else busy = true;

   //if (stopSignal_) return DEVICE_OK;

   int timeout = 10000; // 10 sec
   int intervalMs = 100;
   int totaltime=0;
   while ( busy ) {
		//if (stopSignal_) return DEVICE_OK;
		//Sleep(intervalMs);
		totaltime += intervalMs;

		ret = SendSerialCommand(port_.c_str(), command, "\r\n");
		//if (ret != DEVICE_OK)
		//  return ret;
		ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
		//if (ret != DEVICE_OK)
		//  return ret;

 	    if (answer.substr(0,1) == "B") busy = true;
		else if (answer.substr(0,1) == "N") busy = false;
		else busy = true;

		if (!busy) break;
		//if (totaltime > timeout ) break;

   }

   //return DEVICE_OK;
}

int XYStage::Home(){
	
	// do home command
   int ret = SendSerialCommand(port_.c_str(), "! X Y", "\r"); // use command HOME
    if (ret != DEVICE_OK)
      return ret;

   // block/wait for acknowledge, or until we time out;
   string answer;
   ret = GetSerialAnswer(port_.c_str(), "\r", answer);
   if (ret != DEVICE_OK)
      return ret;

   if (answer.substr(0,2).compare(":A") == 0)
   {
      //do nothing;
   }
   else if (answer.substr(0, 2).compare(":N") == 0 && answer.length() > 2)
   {
      int errNo = atoi(answer.substr(2,4).c_str());
      return ERR_OFFSET + errNo;
   }

   return DEVICE_OK;

};

int XYStage::Calibrate(){
	
	if (stopSignal_) return DEVICE_OK;

	double x1, y1;
	int ret = GetPositionUm(x1, y1);
    if (ret != DEVICE_OK)
      return ret;

	Wait();
	//ret = Wait();
 //   if (ret != DEVICE_OK)
 //     return ret;
	if (stopSignal_) return DEVICE_OK;

	//

	// do home command
	ret = SendSerialCommand(port_.c_str(), "! X Y", "\r"); // use command HOME
    if (ret != DEVICE_OK)
      return ret;

   // block/wait for acknowledge, or until we time out;
   string answer;
   ret = GetSerialAnswer(port_.c_str(), "\r", answer);
   if (ret != DEVICE_OK)
      return ret;

   if (answer.substr(0,2).compare(":A") == 0)
   {
      //do nothing;
   }
   else if (answer.substr(0, 2).compare(":N") == 0 && answer.length() > 2)
   {
      int errNo = atoi(answer.substr(2,4).c_str());
      return ERR_OFFSET + errNo;
   }

	//Wait();
	//if (stopSignal_) return DEVICE_OK;
	////

	//double x2, y2;
	//ret = GetPositionUm(x2, y2);
 //   if (ret != DEVICE_OK)
 //     return ret;

	//Wait();
	////ret = Wait();
 ////   if (ret != DEVICE_OK)
 ////     return ret;
	//if (stopSignal_) return DEVICE_OK;

	//ret = SetOrigin();
	//if (ret != DEVICE_OK)
 //     return ret;

	//Wait();
	////ret = Wait();
 ////   if (ret != DEVICE_OK)
 ////     return ret;
	//if (stopSignal_) return DEVICE_OK;

	////
	//double x = x1-x2;
	//double y = y1-y2;
	//ret = SetPositionUm(x, y);
	//if (ret != DEVICE_OK)
 //     return ret;
	//
	//Wait();
	////ret = Wait();
 ////   if (ret != DEVICE_OK)
 ////     return ret;
	//if (stopSignal_) return DEVICE_OK;

	return DEVICE_OK;

}

int XYStage::Calibrate1() {
	int ret = Calibrate();
	stopSignal_ = false;
	return ret;
}

int XYStage::Stop() {

   stopSignal_ = true;
   int ret = SendSerialCommand(port_.c_str(), "HALT", "\r"); // use command HALT "\"
   if (ret != DEVICE_OK)
      return ret;

   // block/wait for acknowledge, or until we time out;
   string answer;
   ret = GetSerialAnswer(port_.c_str(), "\r", answer);
   if (ret != DEVICE_OK)
      return ret;

   if (answer.substr(0,2).compare(":A") == 0)
   {
      return DEVICE_OK;
   }
   else if (answer.substr(0, 2).compare(":N") == 0 && answer.length() > 2)
   {
      int errNo = atoi(answer.substr(2,4).c_str());
	  if (errNo == -21) return DEVICE_OK;
      else return errNo; //ERR_OFFSET + errNo;
   }

   return DEVICE_OK;
}
 
int XYStage::GetLimits(double& xMin, double& xMax, double& yMin, double& yMax)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int XYStage::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int XYStage::OnStepSizeX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(stepSizeXUm_);
   }

   return DEVICE_OK;
}
int XYStage::OnStepSizeY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(stepSizeYUm_);
   }

   return DEVICE_OK;
}

// XYStage utility functions
int XYStage::GetResolution(double& resX, double& resY)
{
   //const char* commandX="RES,X";
   //const char* commandY="RES,Y";

   //int ret = GetDblParameter(commandX, resX);
   //if (ret != DEVICE_OK)
   //   return ret;

   //ret = GetDblParameter(commandY, resY);
   //if (ret != DEVICE_OK)
   //   return ret;

   //return ret;
   return 0; // will remove it if need this function
}

int XYStage::GetDblParameter(const char* command, double& param)
{
   //// send command
   //int ret = SendSerialCommand(port_.c_str(), command, "\r");
   //if (ret != DEVICE_OK)
   //   return ret;

   //// block/wait for acknowledge, or until we time out;
   //string answer;
   //ret = GetSerialAnswer(port_.c_str(), "\r", answer);
   //if (ret != DEVICE_OK)
   //   return ret;

   //if (answer.length() > 2 && answer.substr(0, 1).compare("E") == 0)
   //{
   //   int errNo = atoi(answer.substr(2).c_str());
   //   return ERR_OFFSET + errNo;
   //}
   //else if (answer.length() > 0)
   //{
   //   param = atof(answer.c_str());
   //   return DEVICE_OK;
   //}

   return ERR_UNRECOGNIZED_ANSWER;
}

int XYStage::GetPositionStepsSingle(char axis, long& steps)
{
   //ostringstream command;
   //command << "P" << axis;

   //// send command
   //int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
   //if (ret != DEVICE_OK)
   //   return ret;

   //// block/wait for acknowledge, or until we time out;
   //string answer;
   //ret = GetSerialAnswer(port_.c_str(), "\r", answer);
   //if (ret != DEVICE_OK)
   //   return ret;

   //if (answer.length() > 2 && answer.substr(0, 1).compare("E") == 0)
   //{
   //   int errNo = atoi(answer.substr(2).c_str());
   //   return ERR_OFFSET + errNo;
   //}
   //else if (answer.length() > 0)
   //{
   //   steps = atol(answer.c_str());
   //   return DEVICE_OK;
   //}

   return ERR_UNRECOGNIZED_ANSWER;
}



///////////////////////////////////////////////////////////////////////////////
// ZStage

ZStage::ZStage() :
   initialized_(false),
   port_("Undefined"),
   stepSizeUm_(0.1),
   answerTimeoutMs_(1000)
{
   InitializeDefaultErrorMessages();

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, g_ZStageDeviceName, MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "ASI Z-stage driver adapter", MM::String, true);

   // Port
   CPropertyAction* pAct = new CPropertyAction (this, &ZStage::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);
}

ZStage::~ZStage()
{
   Shutdown();
}

void ZStage::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_ZStageDeviceName);
}

int ZStage::Initialize()
{
   // set stage step size and resolution
   //double res;
   //int ret = GetResolution(res);
   //if (ret != DEVICE_OK)
   //   return ret;

   //if (res <= 0.0)
   //   return ERR_INVALID_STEP_SIZE;

   stepSizeUm_ = 0.1; //res;

   int ret = GetPositionSteps(curSteps_);
   if (ret != DEVICE_OK)
      return ret;

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;
   return DEVICE_OK;
}

int ZStage::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

bool ZStage::Busy()
{
   const char* command = "/";
   // send command
   int ret = SendSerialCommand(port_.c_str(), command, "\r\n");
   if (ret != DEVICE_OK)
      return false;

   // block/wait for acknowledge, or until we time out;
   string answer;
   ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
   if (ret != DEVICE_OK)
      return false;

   if (answer.length() >= 1)
   {
	  if (answer.substr(0,1) == "B") return true;
	  else if (answer.substr(0,1) == "N") return false;
	  else return false;
   }

   return false;
}

int ZStage::SetPositionUm(double pos)
{
   // for ASI

   ostringstream command;
   command << "M Z=" << pos / stepSizeUm_; // in 10th of micros

   // send command
   int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
   if (ret != DEVICE_OK)
      return ret;

   // block/wait for acknowledge, or until we time out;
   string answer;
   ret = GetSerialAnswer(port_.c_str(), "\r", answer);
   if (ret != DEVICE_OK)
      return ret;

   if (answer.substr(0,2).compare(":A") == 0)
   {
      return DEVICE_OK;
   }
   // deal with error later
   else if (answer.substr(0, 2).compare(":N") == 0 && answer.length() > 2)
   {
      int errNo = atoi(answer.substr(4).c_str());
      return ERR_OFFSET + errNo;
   }

   return ERR_UNRECOGNIZED_ANSWER; 
}

int ZStage::GetPositionUm(double& pos)
{

   ostringstream command;
   command << "W Z";

   // send command
   int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r\n");
   if (ret != DEVICE_OK)
      return ret;

   // block/wait for acknowledge, or until we time out;
   string answer;
   ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
   if (ret != DEVICE_OK)
      return ret;

   if (answer.length() > 2 && answer.substr(0, 2).compare(":N") == 0)
   {
      int errNo = atoi(answer.substr(2).c_str());
      return ERR_OFFSET + errNo;
   }
   else if (answer.length() > 0)
   {
      char head[64];
	  float zz;
	  char iBuf[256];
	  strcpy(iBuf,answer.c_str());
	  sscanf(iBuf, "%s %f\r\n", head, &zz);
	  
	  pos = zz * stepSizeUm_;
	  curSteps_ = (long)zz;

      return DEVICE_OK;
   }

   return ERR_UNRECOGNIZED_ANSWER;
}
  
int ZStage::SetPositionSteps(long pos)
{

   // for ASI

   ostringstream command;
   command << "M Z=" << pos; // in 10th of micros

   // send command
   int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
   if (ret != DEVICE_OK)
      return ret;

   // block/wait for acknowledge, or until we time out;
   string answer;
   ret = GetSerialAnswer(port_.c_str(), "\r", answer);
   if (ret != DEVICE_OK)
      return ret;

   if (answer.substr(0,2).compare(":A") == 0)
   {
      return DEVICE_OK;
   }
   // deal with error later
   else if (answer.substr(0, 2).compare(":N") == 0 && answer.length() > 2)
   {
      int errNo = atoi(answer.substr(4).c_str());
      return ERR_OFFSET + errNo;
   }

   return ERR_UNRECOGNIZED_ANSWER; 
 
}
  
int ZStage::GetPositionSteps(long& steps)
{
   ostringstream command;
   command << "W Z";

   // send command
   int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r\n");
   if (ret != DEVICE_OK)
      return ret;

   // block/wait for acknowledge, or until we time out;
   string answer;
   ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
   if (ret != DEVICE_OK)
      return ret;

   if (answer.length() > 2 && answer.substr(0, 2).compare(":N") == 0)
   {
      int errNo = atoi(answer.substr(2).c_str());
      return ERR_OFFSET + errNo;
   }
   else if (answer.length() > 0)
   {
      char head[64];
	  float zz;
	  char iBuf[256];
	  strcpy(iBuf,answer.c_str());
	  sscanf(iBuf, "%s %f\r\n", head, &zz);
	  
	  steps = (long) zz;
	  curSteps_ = (long)steps;

      return DEVICE_OK;
   }

   return ERR_UNRECOGNIZED_ANSWER;
}

//int ZStage::GetResolution(double& res)
//{
//   const char* command="RES,Z";
//
//   // send command
//   int ret = SendSerialCommand(port_.c_str(), command, "\r");
//   if (ret != DEVICE_OK)
//      return ret;
//
//   // block/wait for acknowledge, or until we time out;
//   string answer;
//   ret = GetSerialAnswer(port_.c_str(), "\r", answer);
//   if (ret != DEVICE_OK)
//      return ret;
//
//   if (answer.length() > 2 && answer.substr(0, 1).compare("E") == 0)
//   {
//      int errNo = atoi(answer.substr(2).c_str());
//      return ERR_OFFSET + errNo;
//   }
//   else if (answer.length() > 0)
//   {
//      res = atof(answer.c_str());
//      return DEVICE_OK;
//   }
//
//   return ERR_UNRECOGNIZED_ANSWER;
//}

int ZStage::SetOrigin()
{
   // send command
   int ret = SendSerialCommand(port_.c_str(), "H Z", "\r"); // use command HERE, zero (z) zero all x,y,z
   if (ret != DEVICE_OK)
      return ret;

   // block/wait for acknowledge, or until we time out;
   string answer;
   ret = GetSerialAnswer(port_.c_str(), "\r", answer);
   if (ret != DEVICE_OK)
      return ret;

   if (answer.substr(0,2).compare(":A") == 0)
   {
      return DEVICE_OK;
   }
   else if (answer.substr(0, 2).compare(":N") == 0 && answer.length() > 2)
   {
      int errNo = atoi(answer.substr(2,4).c_str());
      return ERR_OFFSET + errNo;
   }

   return ERR_UNRECOGNIZED_ANSWER;  
}

int ZStage::Calibrate(){

	return DEVICE_OK;;
}

int ZStage::GetLimits(double& min, double& max)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int ZStage::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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

