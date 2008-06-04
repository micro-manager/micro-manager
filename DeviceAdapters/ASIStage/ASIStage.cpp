///////////////////////////////////////////////////////////////////////////////
// FILE:          ASIStage.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   ASIStage adapter
// COPYRIGHT:     University of California, San Francisco, 2007
//                All rights reserved
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
//
// AUTHOR:        Jizhen Zhao (j.zhao@andor.com) based on code by Nenad Amodaj, April 2007, modified by Nico Stuurman, 12/2007
// MAINTAINER     Maintained by Nico Stuurman (nico@cmp.ucsf.edu)
//

#ifdef WIN32
   //#include <windows.h>
   #define snprintf _snprintf 
#endif

#include "ASIStage.h"
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include "../../MMDevice/DeviceUtils.h"
#include <sstream>


#include <iostream>
using namespace std;

const char* g_XYStageDeviceName = "XYStage";
const char* g_ZStageDeviceName = "ZStage";
const char* g_CRIFDeviceName = "CRIF";
const char* g_AZ100TurretName = "AZ100 Turret";

// CRIF states
const char* g_CRIFState = "CRIF State";
const char* g_CRIF_I = "Unlock (Laser Off)";
const char* g_CRIF_L = "Laser On";
const char* g_CRIF_Cal = "Calibrate";
const char* g_CRIF_G = "Calibration Succeeded";
const char* g_CRIF_B = "Calibration Failed";
const char* g_CRIF_k = "Locking";
const char* g_CRIF_K = "Lock";
const char* g_CRIF_E = "Error";
const char* g_CRIF_O = "Laser Off";

using namespace std;

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   AddAvailableDeviceName(g_ZStageDeviceName, "Add-on Z-stage");
   AddAvailableDeviceName(g_XYStageDeviceName, "XY Stage");
   AddAvailableDeviceName(g_CRIFDeviceName, "CRIF");
   AddAvailableDeviceName(g_AZ100TurretName, "AZ100 Turret");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_ZStageDeviceName) == 0)
   {
      ZStage* s = new ZStage();
      return s;
   }
   else if (strcmp(deviceName, g_XYStageDeviceName) == 0)
   {
      XYStage* s = new XYStage();
      return s;
   }
   else if (strcmp(deviceName, g_CRIFDeviceName) == 0)
   {
      return  new CRIF();
   }
   else if (strcmp(deviceName, g_AZ100TurretName) == 0)
   {
      return  new AZ100Turret();
   }

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

// General utility function:
int ClearPort(MM::Device& device, MM::Core& core, std::string port)
{
   // Clear contents of serial port 
   const int bufSize = 255;
   unsigned char clear[bufSize];                                                        
   unsigned long read = bufSize;                                               
   int ret;                                                                    
   while ((int) read == bufSize)                                                     
   {                                                                           
      ret = core.ReadFromSerial(&device, port.c_str(), clear, bufSize, read); 
      if (ret != DEVICE_OK)                                                    
         return ret;                                                           
   }                                                                           
   return DEVICE_OK;                                                           
} 


///////////////////////////////////////////////////////////////////////////////
// XYStage
//
XYStage::XYStage() :
   initialized_(false), 
   port_("Undefined"), 
   stepSizeXUm_(0.0), 
   stepSizeYUm_(0.0), 
   motorOn_(true),
   nrMoveRepetitions_(0),
   answerTimeoutMs_(1000)
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
   // empty the Rx serial buffer before sending command
   ClearPort(*this, *GetCoreCallback(), port_.c_str()); 

   // TODO: add version command (V) and the  CCA Y=n property when firmware > 7.3
   CPropertyAction* pAct = new CPropertyAction (this, &XYStage::OnVersion);
   CreateProperty("Version", "", MM::String, true, pAct);

   // check status first
   const char* command = "/"; // check STATUS
   // send command
   int ret = SendSerialCommand(port_.c_str(), command, "\r");
   if (ret != DEVICE_OK)
      return ret;

   string answer;
   ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
   if (ret != DEVICE_OK)
      return false;

   // Most ASIStages have the origin in the top right corner, the following reverses direction of the X-axis:
   ret = SetAxisDirection();
   if (ret != DEVICE_OK)
      return ret;

   // set stage step size and resolution
   double resX, resY;
   // default values
   resX = 0.1;
   resY = 0.1;
   // if find a function can get the step size in the future, can fit it here

   stepSizeXUm_ = resX;
   stepSizeYUm_ = resY;

   // Step size
   pAct = new CPropertyAction (this, &XYStage::OnStepSizeX);
   CreateProperty("StepSizeX_um", "0.0", MM::Float, true, pAct);
   pAct = new CPropertyAction (this, &XYStage::OnStepSizeY);
   CreateProperty("StepSizeY_um", "0.0", MM::Float, true, pAct);

   // Wait cycles
   if (hasCommand("WT X?")) {
      pAct = new CPropertyAction (this, &XYStage::OnWait);
      CreateProperty("Wait_Cycles", "5", MM::Integer, false, pAct);
      SetPropertyLimits("Wait_Cycles", 0, 255);
   }

   // Speed (sets both x and y)
   if (hasCommand("S X?")) {
      pAct = new CPropertyAction (this, &XYStage::OnSpeed);
      CreateProperty("Speed-S", "1", MM::Float, false, pAct);
   }

   // Backlash (sets both x and y)
   if (hasCommand("B X?")) {
      pAct = new CPropertyAction (this, &XYStage::OnBacklash);
      CreateProperty("Backlash-B", "0", MM::Float, false, pAct);
   }

   // Error (sets both x and y)
   if (hasCommand("E X?")) {
      pAct = new CPropertyAction (this, &XYStage::OnError);
      CreateProperty("Error-E(nm)", "0", MM::Float, false, pAct);
   }

   // Finish Error (sets both x and y)
   if (hasCommand("PC X?")) {
      pAct = new CPropertyAction (this, &XYStage::OnFinishError);
      CreateProperty("FinishError-PCROS(nm)", "0", MM::Float, false, pAct);
   }

   // OverShoot (sets both x and y)
   if (hasCommand("OS X?")) {
      pAct = new CPropertyAction (this, &XYStage::OnOverShoot);
      CreateProperty("OverShoot(um)", "0", MM::Float, false, pAct);
   }

   // MotorCtrl, (works on both x and y)
   pAct = new CPropertyAction (this, &XYStage::OnMotorCtrl);
   CreateProperty("MotorOnOff", "On", MM::String, false, pAct);
   AddAllowedValue("MotorOnOff", "On");
   AddAllowedValue("MotorOnOff", "Off");

   // Number of times stage approaches a new position (+1)
   if (hasCommand("CCA Y=?")) {
      pAct = new CPropertyAction(this, &XYStage::OnNrMoveRepetitions);
      CreateProperty("NrMoveRepetitions", "0", MM::Integer, false, pAct);
      SetPropertyLimits("NrMoveRepetitions", 0, 10);
   }

   ret = UpdateStatus();  
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
   // empty the Rx serial buffer before sending command
   ClearPort(*this, *GetCoreCallback(), port_.c_str()); 

   const char* command = "/";
   // send command
   int ret = SendSerialCommand(port_.c_str(), command, "\r");
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
   // empty the Rx serial buffer before sending command
   ClearPort(*this, *GetCoreCallback(), port_.c_str()); 

   ostringstream command;
   command << "M X=" << x/stepSizeXUm_ << " Y=" << y/stepSizeYUm_; // in 10th of micros

   // send command
   int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
   if (ret != DEVICE_OK)
      return ret;

   // block/wait for acknowledge, or until we time out;
   string answer;
   ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
   if (ret != DEVICE_OK)
      return ret;

   if ( (answer.substr(0,2).compare(":A") == 0) || (answer.substr(1,2).compare(":A") == 0) )
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

int XYStage::SetRelativePositionUm(double x, double y)
{
   // empty the Rx serial buffer before sending command
   ClearPort(*this, *GetCoreCallback(), port_.c_str()); 

   ostringstream command;
   command << "R X=" << x/stepSizeXUm_ << " Y=" << y/stepSizeYUm_; // in 10th of micros

   // send command
   int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
   if (ret != DEVICE_OK)
      return ret;

   // block/wait for acknowledge, or until we time out;
   string answer;
   ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
   if (ret != DEVICE_OK)
      return ret;

   if ( (answer.substr(0,2).compare(":A") == 0) || (answer.substr(1,2).compare(":A") == 0) )
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
   // empty the Rx serial buffer before sending command
   ClearPort(*this, *GetCoreCallback(), port_.c_str()); 

   ostringstream command;
   command << "W X Y";

   // send command
   int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
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
  
int XYStage::SetPositionSteps(long /*x*/, long /*y*/)
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
 
int XYStage::GetPositionSteps(long& /*x*/, long& /*y*/)
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
   ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
   if (ret != DEVICE_OK)
      return ret;

   if (answer.substr(0,2).compare(":A") == 0 || answer.substr(1,2).compare(":A") == 0)
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
   // empty the Rx serial buffer before sending command
   ClearPort(*this, *GetCoreCallback(), port_.c_str()); 

   //if (stopSignal_) return DEVICE_OK;
   bool busy=true;
   const char* command = "/";
   // send command
   int ret = SendSerialCommand(port_.c_str(), command, "\r");
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

   int intervalMs = 100;
   int totaltime=0;
   while ( busy ) {
		//if (stopSignal_) return DEVICE_OK;
		//Sleep(intervalMs);
		totaltime += intervalMs;

		ret = SendSerialCommand(port_.c_str(), command, "\r");
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

int XYStage::Home()
{
   // empty the Rx serial buffer before sending command
   ClearPort(*this, *GetCoreCallback(), port_.c_str()); 
	
	// do home command
   int ret = SendSerialCommand(port_.c_str(), "! X Y", "\r"); // use command HOME
    if (ret != DEVICE_OK)
      return ret;

   // block/wait for acknowledge, or until we time out;
   string answer;
   ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
   if (ret != DEVICE_OK)
      return ret;

   if (answer.substr(0,2).compare(":A") == 0 || answer.substr(1,2).compare(":A") == 0)
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
   ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
   if (ret != DEVICE_OK)
      return ret;

   if (answer.substr(0,2).compare(":A") == 0  || answer.substr(1,2).compare(":A") == 0)
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

int XYStage::Stop() 
{
   // empty the Rx serial buffer before sending command
   ClearPort(*this, *GetCoreCallback(), port_.c_str()); 

   stopSignal_ = true;
   int ret = SendSerialCommand(port_.c_str(), "HALT", "\r"); // use command HALT "\"
   if (ret != DEVICE_OK)
      return ret;

   // block/wait for acknowledge, or until we time out;
   string answer;
   ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
   if (ret != DEVICE_OK)
      return ret;

   if (answer.substr(0,2).compare(":A") == 0 || answer.substr(1,2).compare(":A") == 0)
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
 
int XYStage::GetLimits(double& /*xMin*/, double& /*xMax*/, double& /*yMin*/, double& /*yMax*/)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

bool XYStage::hasCommand(std::string command) {
   // send command
   int ret = SendSerialCommand(port_.c_str(), command.c_str(), "\r");
   if (ret != DEVICE_OK)
      return false;

   // block/wait for acknowledge, or until we time out;
   string answer;
   ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
   if (ret != DEVICE_OK)
      return false;

   if (answer.substr(0,2).compare(":A") == 0)
      return true;
   if (answer.substr(0,4).compare(":N-1") == 0)
      return false;

   // if we do not get an answer, or any other answer, this is probably OK
   return true;
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

// Get the version of this controller
int XYStage::OnVersion(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      ostringstream command;
      command << "V";
      // send command
      int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
      if (ret != DEVICE_OK)
         return ret;

      // block/wait for acknowledge, or until we time out;
      string answer;
      ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
      if (ret != DEVICE_OK)
         return ret;

      if (answer.substr(0,2).compare(":A") == 0)
      {
         pProp->Set(answer.substr(3).c_str());
         return DEVICE_OK;
      }
      // deal with error later
      else if (answer.substr(0, 2).compare(":N") == 0 && answer.length() > 2)
      {
         int errNo = atoi(answer.substr(3).c_str());
         return ERR_OFFSET + errNo;
      }
      return ERR_UNRECOGNIZED_ANSWER;  
   }

   return DEVICE_OK;
}


// This sets how often the stage will approach the same position (0 = 1!!)
int XYStage::OnNrMoveRepetitions(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // some controllers will return this, the current ones do not, so cache
      pProp->Set(nrMoveRepetitions_);
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(nrMoveRepetitions_);
      if (nrMoveRepetitions_ < 0)
         nrMoveRepetitions_ = 0;
      ostringstream command;
      command << "CCA Y=" << nrMoveRepetitions_;
      // send command
      int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
      if (ret != DEVICE_OK)
         return ret;

      // some controller do not answer, so do not check answer
      /*
      // block/wait for acknowledge, or until we time out;
      string answer;
      ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
      if (ret != DEVICE_OK)
         return ret;

      if (answer.substr(0,2).compare(":A") == 0)
         return DEVICE_OK;
      else if (answer.substr(0, 2).compare(":N") == 0 && answer.length() > 2)
      {
         int errNo = atoi(answer.substr(3).c_str());
         return ERR_OFFSET + errNo;
      }
      return ERR_UNRECOGNIZED_ANSWER;
      */
   }
   return DEVICE_OK;
}

// This sets the number of waitcycles
int XYStage::OnWait(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // To simplify our life we only read out waitcycles for the X axis, but set for both
      ostringstream command;
      command << "WT X?";
      // send command
      int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
      if (ret != DEVICE_OK)
         return ret;

      // block/wait for acknowledge, or until we time out;
      string answer;
      ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
      if (ret != DEVICE_OK)
         return ret;

      if (answer.substr(0,2).compare(":X") == 0)
      {
         long waitCycles = atol(answer.substr(3).c_str());
         pProp->Set(waitCycles);
         return DEVICE_OK;
      }
      // deal with error later
      else if (answer.substr(0, 2).compare(":N") == 0 && answer.length() > 2)
      {
         int errNo = atoi(answer.substr(3).c_str());
         return ERR_OFFSET + errNo;
      }
      return ERR_UNRECOGNIZED_ANSWER;  
   }
   else if (eAct == MM::AfterSet) {
      long waitCycles;
      pProp->Get(waitCycles);
      if (waitCycles < 0)
         waitCycles = 0;
      else if (waitCycles > 255)
         waitCycles = 255;
      ostringstream command;
      command << "WT X=" << waitCycles << " Y=" << waitCycles;
      // send command
      int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
      if (ret != DEVICE_OK)
         return ret;

      // block/wait for acknowledge, or until we time out;
      string answer;
      ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
      if (ret != DEVICE_OK)
         return ret;

      if (answer.substr(0,2).compare(":A") == 0)
         return DEVICE_OK;
      else if (answer.substr(0, 2).compare(":N") == 0 && answer.length() > 2)
      {
         int errNo = atoi(answer.substr(3).c_str());
         return ERR_OFFSET + errNo;
      }
      return ERR_UNRECOGNIZED_ANSWER;
   }
   return DEVICE_OK;
}


int XYStage::OnBacklash(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // To simplify our life we only read out waitcycles for the X axis, but set for both
      ostringstream command;
      command << "B X?";
      // send command
      int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
      if (ret != DEVICE_OK)
         return ret;

      // block/wait for acknowledge, or until we time out;
      string answer;
      ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
      if (ret != DEVICE_OK)
         return ret;

      if (answer.substr(0,2).compare(":X") == 0)
      {
         double speed = atof(answer.substr(3, 8).c_str());
         pProp->Set(speed);
         return DEVICE_OK;
      }
      // deal with error later
      else if (answer.substr(0, 2).compare(":N") == 0 && answer.length() > 2)
      {
         int errNo = atoi(answer.substr(3).c_str());
         return ERR_OFFSET + errNo;
      }
      return ERR_UNRECOGNIZED_ANSWER;  
   }
   else if (eAct == MM::AfterSet) {
      double backlash;
      pProp->Get(backlash);
      if (backlash < 0.0)
         backlash = 0.0;
      ostringstream command;
      command << "B X=" << backlash << " Y=" << backlash;
      // send command
      int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
      if (ret != DEVICE_OK)
         return ret;

      // block/wait for acknowledge, or until we time out;
      string answer;
      ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
      if (ret != DEVICE_OK)
         return ret;

      if (answer.substr(0,2).compare(":A") == 0)
         return DEVICE_OK;
      else if (answer.substr(0, 2).compare(":N") == 0 && answer.length() > 2)
      {
         int errNo = atoi(answer.substr(3).c_str());
         return ERR_OFFSET + errNo;
      }
      return ERR_UNRECOGNIZED_ANSWER;
   }
   return DEVICE_OK;
}

int XYStage::OnFinishError(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // To simplify our life we only read out waitcycles for the X axis, but set for both
      ostringstream command;
      command << "PC X?";
      // send command
      int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
      if (ret != DEVICE_OK)
         return ret;

      // block/wait for acknowledge, or until we time out;
      string answer;
      ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
      if (ret != DEVICE_OK)
         return ret;

      if (answer.substr(0,2).compare(":X") == 0)
      {
         double fError = atof(answer.substr(3, 8).c_str());
         pProp->Set(1000000* fError);
         return DEVICE_OK;
      }
      if (answer.substr(0,2).compare(":A") == 0)
      {
         // Answer is of the form :A X=0.00003
         double fError = atof(answer.substr(5, 8).c_str());
         pProp->Set(1000000* fError);
         return DEVICE_OK;
      }
      // deal with error later
      else if (answer.substr(0, 2).compare(":N") == 0 && answer.length() > 2)
      {
         int errNo = atoi(answer.substr(3).c_str());
         return ERR_OFFSET + errNo;
      }
      return ERR_UNRECOGNIZED_ANSWER;  
   }
   else if (eAct == MM::AfterSet) {
      double error;
      pProp->Get(error);
      if (error < 0.0)
         error = 0.0;
      error = error/1000000;
      ostringstream command;
      command << "PC X=" << error << " Y=" << error;
      // send command
      int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
      if (ret != DEVICE_OK)
         return ret;

      // block/wait for acknowledge, or until we time out;
      string answer;
      ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
      if (ret != DEVICE_OK)
         return ret;

      if (answer.substr(0,2).compare(":A") == 0)
         return DEVICE_OK;
      else if (answer.substr(0, 2).compare(":N") == 0 && answer.length() > 2)
      {
         int errNo = atoi(answer.substr(3).c_str());
         return ERR_OFFSET + errNo;
      }
      return ERR_UNRECOGNIZED_ANSWER;
   }
   return DEVICE_OK;
}

int XYStage::OnOverShoot(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // To simplify our life we only read out waitcycles for the X axis, but set for both
      ostringstream command;
      command << "OS X?";
      // send command
      int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
      if (ret != DEVICE_OK)
         return ret;

      // block/wait for acknowledge, or until we time out;
      string answer;
      ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
      if (ret != DEVICE_OK)
         return ret;

      if (answer.substr(0,2).compare(":A") == 0)
      {
         double overShoot = atof(answer.substr(5, 8).c_str());
         pProp->Set(overShoot * 1000.0);
         return DEVICE_OK;
      }
      // deal with error later
      else if (answer.substr(0, 2).compare(":N") == 0 && answer.length() > 2)
      {
         int errNo = atoi(answer.substr(3).c_str());
         return ERR_OFFSET + errNo;
      }
      return ERR_UNRECOGNIZED_ANSWER;  
   }
   else if (eAct == MM::AfterSet) {
      double overShoot;
      pProp->Get(overShoot);
      if (overShoot < 0.0)
         overShoot = 0.0;
      overShoot = overShoot / 1000.0;
      ostringstream command;
      command << "OS X=" << overShoot << " Y=" << overShoot;
      // send command
      int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
      if (ret != DEVICE_OK)
         return ret;

      // block/wait for acknowledge, or until we time out;
      string answer;
      ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
      if (ret != DEVICE_OK)
         return ret;

      if (answer.substr(0,2).compare(":A") == 0)
         return DEVICE_OK;
      else if (answer.substr(0, 2).compare(":N") == 0 && answer.length() > 2)
      {
         int errNo = atoi(answer.substr(3).c_str());
         return ERR_OFFSET + errNo;
      }
      return ERR_UNRECOGNIZED_ANSWER;
   }
   return DEVICE_OK;
}

int XYStage::OnError(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // To simplify our life we only read out waitcycles for the X axis, but set for both
      ostringstream command;
      command << "E X?";
      // send command
      int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
      if (ret != DEVICE_OK)
         return ret;

      // block/wait for acknowledge, or until we time out;
      string answer;
      ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
      if (ret != DEVICE_OK)
         return ret;

      if (answer.substr(0,2).compare(":X") == 0)
      {
         double fError = atof(answer.substr(3, 8).c_str());
         pProp->Set(fError * 1000000.0);
         return DEVICE_OK;
      }
      // deal with error later
      else if (answer.substr(0, 2).compare(":N") == 0 && answer.length() > 2)
      {
         int errNo = atoi(answer.substr(3).c_str());
         return ERR_OFFSET + errNo;
      }
      return ERR_UNRECOGNIZED_ANSWER;  
   }
   else if (eAct == MM::AfterSet) {
      double error;
      pProp->Get(error);
      if (error < 0.0)
         error = 0.0;
      error = error / 1000000.0;
      ostringstream command;
      command << "E X=" << error << " Y=" << error;
      // send command
      int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
      if (ret != DEVICE_OK)
         return ret;

      // block/wait for acknowledge, or until we time out;
      string answer;
      ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
      if (ret != DEVICE_OK)
         return ret;

      if (answer.substr(0,2).compare(":A") == 0)
         return DEVICE_OK;
      else if (answer.substr(0, 2).compare(":N") == 0 && answer.length() > 2)
      {
         int errNo = atoi(answer.substr(3).c_str());
         return ERR_OFFSET + errNo;
      }
      return ERR_UNRECOGNIZED_ANSWER;
   }
   return DEVICE_OK;
}

int XYStage::OnSpeed(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // To simplify our life we only read out waitcycles for the X axis, but set for both
      ostringstream command;
      command << "S X?";
      // send command
      int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
      if (ret != DEVICE_OK)
         return ret;

      // block/wait for acknowledge, or until we time out;
      string answer;
      ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
      if (ret != DEVICE_OK)
         return ret;

      if (answer.substr(0,2).compare(":A") == 0)
      {
         double speed = atof(answer.substr(5).c_str());
         pProp->Set(speed);
         return DEVICE_OK;
      }
      // deal with error later
      else if (answer.substr(0, 2).compare(":N") == 0 && answer.length() > 2)
      {
         int errNo = atoi(answer.substr(3).c_str());
         return ERR_OFFSET + errNo;
      }
      return ERR_UNRECOGNIZED_ANSWER;  
   }
   else if (eAct == MM::AfterSet) {
      double speed;
      pProp->Get(speed);
      if (speed < 0.0)
         speed = 0.0;
      // Note, max speed may differ depending on pitch screw
      else if (speed > 7.5)
         speed = 7.5;
      ostringstream command;
      command << "S X=" << speed << " Y=" << speed;
      // send command
      int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
      if (ret != DEVICE_OK)
         return ret;

      // block/wait for acknowledge, or until we time out;
      string answer;
      ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
      if (ret != DEVICE_OK)
         return ret;

      if (answer.substr(0,2).compare(":A") == 0)
         return DEVICE_OK;
      else if (answer.substr(0, 2).compare(":N") == 0 && answer.length() > 2)
      {
         int errNo = atoi(answer.substr(3).c_str());
         return ERR_OFFSET + errNo;
      }
      return ERR_UNRECOGNIZED_ANSWER;
   }
   return DEVICE_OK;
}

int XYStage::OnMotorCtrl(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // The controller can not report whether or not the motors are on.  Cache the value
      if (motorOn_)
         pProp->Set("On");
      else
         pProp->Set("Off");

      return DEVICE_OK;
   }
   else if (eAct == MM::AfterSet) {
      string motorOn;
      string value;
      pProp->Get(motorOn);
      if (motorOn == "On") {
         motorOn_ = true;
         value = "+";
      } else {
         motorOn_ = false;
         value = "-";
      }
      ostringstream command;
      command << "MC X" << value << " Y" << value;
      // send command
      int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
      if (ret != DEVICE_OK)
         return ret;

      // block/wait for acknowledge, or until we time out;
      string answer;
      ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
      if (ret != DEVICE_OK)
         return ret;

      if (answer.substr(0,2).compare(":A") == 0)
         return DEVICE_OK;
      else if (answer.substr(0, 2).compare(":N") == 0 && answer.length() > 2)
      {
         int errNo = atoi(answer.substr(3).c_str());
         return ERR_OFFSET + errNo;
      }
      return ERR_UNRECOGNIZED_ANSWER;
   }
   return DEVICE_OK;
}

// XYStage utility functions
int XYStage::GetResolution(double& /*resX*/, double& /*resY*/)
{
   return 0; // will remove it if need this function
}

int XYStage::GetDblParameter(const char* /*command*/, double& /*param*/)
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

int XYStage::GetPositionStepsSingle(char /*axis*/, long& /*steps*/)
{
   return ERR_UNRECOGNIZED_ANSWER;
}

int XYStage::SetAxisDirection()
{
   ostringstream command;
   command << "UM X=-10000 Y=10000";
   // send command
   int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
   if (ret != DEVICE_OK)
      return ret;

   // block/wait for acknowledge, or until we time out;
   string answer;
   ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
   if (ret != DEVICE_OK)
      return ret;

   if (answer.substr(0,2).compare(":A") == 0)
      return DEVICE_OK;
   else if (answer.substr(0, 2).compare(":N") == 0 && answer.length() > 2)
   {
      int errNo = atoi(answer.substr(3).c_str());
      return ERR_OFFSET + errNo;
   }

   return ERR_UNRECOGNIZED_ANSWER;
}



///////////////////////////////////////////////////////////////////////////////
// ZStage

ZStage::ZStage() :
   initialized_(false),
   port_("Undefined"),
   axis_("Z"),
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
 
   // Axis
   pAct = new CPropertyAction (this, &ZStage::OnAxis);
   CreateProperty("Axis", "Z", MM::String, false, pAct, true);
   AddAllowedValue("Axis", "Z");
   AddAllowedValue("Axis", "F");
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
   // empty the Rx serial buffer before sending command
   ClearPort(*this, *GetCoreCallback(), port_.c_str()); 

   const char* command = "/";
   // send command
   int ret = SendSerialCommand(port_.c_str(), command, "\r");
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
   // empty the Rx serial buffer before sending command
   ClearPort(*this, *GetCoreCallback(), port_.c_str()); 

   ostringstream command;
   command << "M " << axis_ << "=" << pos / stepSizeUm_; // in 10th of micros

   // send command
   int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
   if (ret != DEVICE_OK)
      return ret;

   // block/wait for acknowledge, or until we time out;
   string answer;
   ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
   if (ret != DEVICE_OK)
      return ret;

   if (answer.substr(0,2).compare(":A") == 0 || answer.substr(1,2).compare(":A") == 0)
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
   // empty the Rx serial buffer before sending command
   ClearPort(*this, *GetCoreCallback(), port_.c_str()); 

   ostringstream command;
   command << "W " << axis_;

   // send command
   int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
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
   ostringstream command;
   command << "M " << axis_ << "=" << pos; // in 10th of micros

   // send command
   int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
   if (ret != DEVICE_OK)
      return ret;

   // block/wait for acknowledge, or until we time out;
   string answer;
   ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
   if (ret != DEVICE_OK)
      return ret;

   if (answer.substr(0,2).compare(":A") == 0 || answer.substr(1,2).compare(":A") == 0)
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
   // empty the Rx serial buffer before sending command
   ClearPort(*this, *GetCoreCallback(), port_.c_str()); 

   ostringstream command;
   command << "W " << axis_;

   // send command
   int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
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
   // empty the Rx serial buffer before sending command
   ClearPort(*this, *GetCoreCallback(), port_.c_str()); 

   // send command
   ostringstream os;
   os << "H " << axis_;
   int ret = SendSerialCommand(port_.c_str(), os.str().c_str(), "\r"); // use command HERE, zero (z) zero all x,y,z
   if (ret != DEVICE_OK)
      return ret;

   // block/wait for acknowledge, or until we time out;
   string answer;
   ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
   if (ret != DEVICE_OK)
      return ret;

   if (answer.substr(0,2).compare(":A") == 0 || answer.substr(1,2).compare(":A") == 0)
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

int ZStage::GetLimits(double& /*min*/, double& /*max*/)
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

int ZStage::OnAxis(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(axis_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(axis_);
   }

   return DEVICE_OK;
}

//////////////////////////////////////////////////////////////////////
// CRIF reflection-based autofocussing unit (Nico, May 2007)
////
CRIF::CRIF() :
initialized_(false), 
justCalibrated_(false),
port_("Undefined")
{
   InitializeDefaultErrorMessages();

   SetErrorText(ERR_NOT_CALIBRATED, "CRIF is not calibrated.  Try focusing close to a coverslip and selecting 'Calibrate'");
   SetErrorText(ERR_UNRECOGNIZED_ANSWER, "The ASI controller said something incomprehensible");

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, g_CRIFDeviceName, MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "ASI CRIF Autofocus adapter", MM::String, true);

   // Port
   CPropertyAction* pAct = new CPropertyAction (this, &CRIF::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);
}

CRIF::~CRIF()
{
   initialized_ = false;
}


int CRIF::Initialize()
{
   if (initialized_)
      return DEVICE_OK;

   CPropertyAction* pAct = new CPropertyAction(this, &CRIF::OnFocus);
   CreateProperty (g_CRIFState, "Undefined", MM::String, false, pAct);

   // Add values (TODO: check manual)
   AddAllowedValue(g_CRIFState, g_CRIF_I);
   AddAllowedValue(g_CRIFState, g_CRIF_L);
   AddAllowedValue(g_CRIFState, g_CRIF_Cal);
   AddAllowedValue(g_CRIFState, g_CRIF_G);
   AddAllowedValue(g_CRIFState, g_CRIF_B);
   AddAllowedValue(g_CRIFState, g_CRIF_k);
   AddAllowedValue(g_CRIFState, g_CRIF_K);
   //AddAllowedValue(g_CRIFState, g_CRIF_E);
   AddAllowedValue(g_CRIFState, g_CRIF_O);

   int ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;
   return DEVICE_OK;
}

int CRIF::Shutdown()
{
   initialized_ = false;
   return DEVICE_OK;
}

bool CRIF::Busy()
{
   //TODO implement
   return false;
}

void CRIF::GetName(char* pszName) const
{
   CDeviceUtils::CopyLimitedString(pszName, g_CRIFDeviceName);
}

int CRIF::GetFocusState(std::string& focusState)
{
   // empty the Rx serial buffer before sending command
   ClearPort(*this, *GetCoreCallback(), port_.c_str()); 

   const char* command = "LOCK X?"; // Requests single char lock state description
   // send command
   int ret = SendSerialCommand(port_.c_str(), command, "\r");
   if (ret != DEVICE_OK)
      return ret;

   string answer;
   ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
   if (ret != DEVICE_OK)
      return ERR_UNRECOGNIZED_ANSWER;

   // translate response to one of our globals (see page 6 of CRIF manual)
   char test = answer.c_str()[3];
   switch (test) {
      case 'I': 
         focusState = g_CRIF_I;
         break;
      case 'L': 
         focusState = g_CRIF_L;
         break;
      case '1': 
      case '2': 
      case '3': 
         focusState = g_CRIF_Cal;
         break;
      case 'G': 
         focusState = g_CRIF_G;
         break;
      case 'B': 
         focusState = g_CRIF_B;
         break;
      case 'k': 
         focusState = g_CRIF_K;
         break;
      case 'K': 
         focusState = g_CRIF_K;
         break;
      case 'E': 
         focusState = g_CRIF_E;
         break;
      case 'O': 
         focusState = g_CRIF_O;
         break;
      default:
         return ERR_UNRECOGNIZED_ANSWER;
   }

   return DEVICE_OK;
}

int CRIF::SetFocusState(std::string focusState)
{
   std::string currentState;
   int ret = GetFocusState(currentState);
   if (ret != DEVICE_OK)
      return ret;

   if (focusState == g_CRIF_I)
   {
      // Unlock and switch off laser:
      ret = SetContinuousFocusing(false);
      if (ret != DEVICE_OK)
         return ret;
   }
   else if (focusState == g_CRIF_O) // we want the laser off and discard calibration (start anew)
   {
      if (currentState == g_CRIF_K)
      { // unlock first
         ret = SetContinuousFocusing(false);
         if (ret != DEVICE_OK)
            return ret;
      }
      if (currentState == g_CRIF_G || currentState == g_CRIF_B || currentState == g_CRIF_L)
      {
         const char* command = "LK Z";
         // send command
         int ret = SendSerialCommand(port_.c_str(), command, "\r");
         if (ret != DEVICE_OK)
            return ret;
      }
      if (currentState == g_CRIF_L) // we need to advance the state once more.  Wait a bit for calibration to finish)
      {
         CDeviceUtils::SleepMs(1000); // ms
         const char* command = "LK Z";
         int ret = SendSerialCommand(port_.c_str(), command, "\r");
         if (ret != DEVICE_OK)
            return ret;
      }
   }

   else if (focusState == g_CRIF_L)
   {
      if ( (currentState == g_CRIF_I) || currentState == g_CRIF_O)
      {
         const char* command = "LK Z";
         // send command
         int ret = SendSerialCommand(port_.c_str(), command, "\r");
         if (ret != DEVICE_OK)
            return ret;
      }
   }
   else if ( (focusState == g_CRIF_Cal) || (focusState == g_CRIF_G) || (focusState == g_CRIF_B)) // looks like we want to calibrate the CRIF
   {
      const char* command = "LK Z";
      if (currentState == g_CRIF_I) // Idle, first switch on laser
      {
         // send command twice, wait a bit in between
         ret = SendSerialCommand(port_.c_str(), command, "\r");
         if (ret != DEVICE_OK)
            return ret;
         CDeviceUtils::SleepMs(10); // ms
         int ret = SendSerialCommand(port_.c_str(), command, "\r");
         if (ret != DEVICE_OK)
            return ret;
      }
      else if (currentState == g_CRIF_L)
      {
         int ret = SendSerialCommand(port_.c_str(), command, "\r");
         if (ret != DEVICE_OK)
            return ret;
      }
      justCalibrated_ = true; // we need this to know whether this is the first time we lock
   }
   else if ( (focusState == g_CRIF_K) || (focusState == g_CRIF_k) )
   {
      // only try a lock when we are good
      if ( (currentState == g_CRIF_G) || (currentState == g_CRIF_O) ) 
      {
         int ret = SetContinuousFocusing(true);
         if (ret != DEVICE_OK)
            return ret;
      }
      else if (! ( (currentState == g_CRIF_k) || currentState == g_CRIF_K) ) 
      {
         // tell the user that we first need to calibrate before starting a lock
         return ERR_NOT_CALIBRATED;
      }
   }
   return DEVICE_OK;
}

bool CRIF::IsContinuousFocusLocked()
{
   // TODO: implement

   std::string focusState;
   int ret = GetFocusState(focusState);
   if (ret != DEVICE_OK)
      return false;

   if (focusState == g_CRIF_K)
      return true;
   else
      return false;
}


int CRIF::SetContinuousFocusing(bool state)
{
   // empty the Rx serial buffer before sending command
   ClearPort(*this, *GetCoreCallback(), port_.c_str()); 

   string command;
   if (state)
   {
      // TODO: check that the system has been calibrated and can be locked!
      if (justCalibrated_)
         command = "LK";
      else
         command = "RL"; // Turns on laser and initiated lock state using previously saved reference
   }
   else
   {
      command = "UL X"; // Turns off laser and unlocks
   }
   // send command
   int ret = SendSerialCommand(port_.c_str(), command.c_str(), "\r");
   if (ret != DEVICE_OK)
      return ret;

   string answer;
   ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
   if (ret != DEVICE_OK)
      return ERR_UNRECOGNIZED_ANSWER;

   // The controller only acknowledges receipt of the command
   if (answer.substr(0,2) != ":A")
      return ERR_UNRECOGNIZED_ANSWER;

   justCalibrated_ = false;

   return DEVICE_OK;
}


int CRIF::GetContinuousFocusing(bool& state)
{
   std::string focusState;
   int ret = GetFocusState(focusState);
   if (ret != DEVICE_OK)
      return ret;

   if (focusState == g_CRIF_K)
      state = true;
   else
      state =false;
   
   return DEVICE_OK;
}

int CRIF::Focus()
{
   return SetContinuousFocusing(true);
}

int CRIF::GetFocusScore(double& score)
{
   // empty the Rx serial buffer before sending command
   ClearPort(*this, *GetCoreCallback(), port_.c_str()); 

   score = 0;
   // This might be useful in locked and unlocked mode..
   /*
   bool locked;
   int ret = GetContinuousFocusing(locked);
   if (!locked)
      return ERR_NOT_LOCKED;
      */

   const char* command = "LOCK Y?"; // Requests present value of the PSD signal as shown on LCD panel
   // send command
   int ret = SendSerialCommand(port_.c_str(), command, "\r");
   if (ret != DEVICE_OK)
      return ret;

   string answer;
   ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
   if (ret != DEVICE_OK)
   return DEVICE_OK;

   score = atof (answer.substr(2).c_str());
   if (score == 0)
      return ERR_UNRECOGNIZED_ANSWER;

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int CRIF::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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


int CRIF::OnFocus(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      int ret = GetFocusState(focusState_);
      if (ret != DEVICE_OK)
         return ret;
      pProp->Set(focusState_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(focusState_);
      int ret = SetFocusState(focusState_);
      if (ret != DEVICE_OK)
         return ret;
   }

   return DEVICE_OK;
}


AZ100Turret::AZ100Turret() :
   numPos_(4),
   initialized_(false),
   port_("Undefined"),
   position_ (0)
{
   InitializeDefaultErrorMessages();

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, g_AZ100TurretName, MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "ASI AZ100 Turret Controller", MM::String, true);

   // Port
   CPropertyAction* pAct = new CPropertyAction (this, &AZ100Turret::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);
 
}

AZ100Turret::~AZ100Turret()
{
   Shutdown();
}

void AZ100Turret::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_AZ100TurretName);
}

int AZ100Turret::Initialize()
{
   // state
   CPropertyAction* pAct = new CPropertyAction (this, &AZ100Turret::OnState);
   int ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   AddAllowedValue(MM::g_Keyword_State, "0");
   AddAllowedValue(MM::g_Keyword_State, "1");
   AddAllowedValue(MM::g_Keyword_State, "2");
   AddAllowedValue(MM::g_Keyword_State, "3");

   // Label
   // -----
   pAct = new CPropertyAction (this, &CStateBase::OnLabel);
   ret = CreateProperty(MM::g_Keyword_Label, "", MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   SetPositionLabel(0, "Position-1");
   SetPositionLabel(1, "Position-2");
   SetPositionLabel(2, "Position-3");
   SetPositionLabel(3, "Position-4");

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;
   return DEVICE_OK;
}

int AZ100Turret::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

bool AZ100Turret::Busy()
{
   // empty the Rx serial buffer before sending command
   ClearPort(*this, *GetCoreCallback(), port_.c_str()); 

   const char* command = "RS F";
   // send command
   int ret = SendSerialCommand(port_.c_str(), command, "\r");
   if (ret != DEVICE_OK)
      return false;

   // block/wait for acknowledge, or until we time out;
   string answer;
   ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
   if (ret != DEVICE_OK)
      return false;

   if (answer.length() >= 1)
   {
      int status = atoi(answer.substr(2).c_str());
      if (status & 1)
         return true;
	   return false;
   }
   return false;
}

int AZ100Turret::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int AZ100Turret::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(position_);
   }
   else if (eAct == MM::AfterSet)
   {
      long position;
      pProp->Get(position);

      ostringstream os;
      os << "MTUR X=" << position + 1;

      // send command
      int ret = SendSerialCommand(port_.c_str(), os.str().c_str(), "\r");
      if (ret != DEVICE_OK)
         return ret;

      string answer;
      ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
      if (ret != DEVICE_OK)
      return DEVICE_OK;

      if (answer.substr(0, 2).compare(":N") == 0 && answer.length() > 2)
      {
         int errNo = atoi(answer.substr(2,4).c_str());
         return ERR_OFFSET + errNo;
      }

      if (answer.substr(0,2) == ":A") {
         position_ = position;
      }
      else
         return ERR_UNRECOGNIZED_ANSWER;
   }

   return DEVICE_OK;
}

