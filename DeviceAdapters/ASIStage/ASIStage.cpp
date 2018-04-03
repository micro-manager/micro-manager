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
// MAINTAINER     Maintained by Nico Stuurman (nico@cmp.ucsf.edu) and Jon Daniels (jon@asiimaging.com)
//

#ifdef WIN32
#pragma warning(disable: 4355)
#endif

#include "ASIStage.h"
#include <cstdio>
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
const char* g_CRISPDeviceName = "CRISP";
const char* g_AZ100TurretName = "AZ100 Turret";
const char* g_StateDeviceName = "State Device";
const char* g_LEDName = "LED";
const char* g_Open = "Open";
const char* g_Closed = "Closed";

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

// CRISP states
const char* g_CRISPState = "CRISP State";
const char* g_CRISP_I = "Idle";
const char* g_CRISP_R = "Ready";
const char* g_CRISP_D = "Dim";
const char* g_CRISP_K = "Lock";
const char* g_CRISP_F = "In Focus";
const char* g_CRISP_N = "Inhibit";
const char* g_CRISP_E = "Error";
const char* g_CRISP_G = "loG_cal";
const char* g_CRISP_SG = "gain_Cal";
const char* g_CRISP_Cal = "Calibrating";
const char* g_CRISP_f = "Dither";
const char* g_CRISP_C = "Curve";
const char* g_CRISP_B = "Balance";
const char* g_CRISP_RFO = "Reset Focus Offset";
const char* g_CRISP_S = "Save to Controller";
const char* const g_CRISPOffsetPropertyName = "Lock Offset";
const char* const g_CRISPSumPropertyName = "Sum";

using namespace std;

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_ZStageDeviceName, MM::StageDevice, "Add-on Z-stage");
   RegisterDevice(g_XYStageDeviceName, MM::XYStageDevice, "XY Stage");
   RegisterDevice(g_CRIFDeviceName, MM::AutoFocusDevice, "CRIF");
   RegisterDevice(g_CRISPDeviceName, MM::AutoFocusDevice, "CRISP");
   RegisterDevice(g_AZ100TurretName, MM::StateDevice, "AZ100 Turret");
   RegisterDevice(g_StateDeviceName, MM::StateDevice, "State Device");
   RegisterDevice(g_LEDName, MM::ShutterDevice, "LED");
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
   else if (strcmp(deviceName, g_CRISPDeviceName) == 0)
   {
      return  new CRISP();
   }
   else if (strcmp(deviceName, g_AZ100TurretName) == 0)
   {
      return  new AZ100Turret();
   }
   else if (strcmp(deviceName, g_StateDeviceName) == 0)
   {
      return  new StateDevice();
   }
   else if (strcmp(deviceName, g_LEDName) == 0)
   {
      return  new LED();
   }

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}


MM::DeviceDetectionStatus ASICheckSerialPort(MM::Device& device, MM::Core& core, std::string portToCheck, double answerTimeoutMs)
{
   // all conditions must be satisfied...
   MM::DeviceDetectionStatus result = MM::Misconfigured;
   char answerTO[MM::MaxStrLength];

   try
   {
      std::string portLowerCase = portToCheck;
      for( std::string::iterator its = portLowerCase.begin(); its != portLowerCase.end(); ++its)
      {
         *its = (char)tolower(*its);
      }
      if( 0< portLowerCase.length() &&  0 != portLowerCase.compare("undefined")  && 0 != portLowerCase.compare("unknown") )
      {
         result = MM::CanNotCommunicate;
         core.GetDeviceProperty(portToCheck.c_str(), "AnswerTimeout", answerTO);
         // device specific default communication parameters
         // for ASI Stage
         core.SetDeviceProperty(portToCheck.c_str(), MM::g_Keyword_Handshaking, "Off");
         core.SetDeviceProperty(portToCheck.c_str(), MM::g_Keyword_StopBits, "1");
         std::ostringstream too;
         too << answerTimeoutMs;
         core.SetDeviceProperty(portToCheck.c_str(), "AnswerTimeout", too.str().c_str());
         core.SetDeviceProperty(portToCheck.c_str(), "DelayBetweenCharsMs", "0");
         MM::Device* pS = core.GetDevice(&device, portToCheck.c_str());
         std::vector< std::string> possibleBauds;
         possibleBauds.push_back("9600");
         possibleBauds.push_back("115200");
         for( std::vector< std::string>::iterator bit = possibleBauds.begin(); bit!= possibleBauds.end(); ++bit )
         {
            core.SetDeviceProperty(portToCheck.c_str(), MM::g_Keyword_BaudRate, (*bit).c_str() );
            pS->Initialize();
            core.PurgeSerial(&device, portToCheck.c_str());
            // check status
            const char* command = "/";
            int ret = core.SetSerialCommand( &device, portToCheck.c_str(), command, "\r");
            if( DEVICE_OK == ret)
            {
               char answer[MM::MaxStrLength];

               ret = core.GetSerialAnswer(&device, portToCheck.c_str(), MM::MaxStrLength, answer, "\r\n");
               if( DEVICE_OK != ret )
               {
                  char text[MM::MaxStrLength];
                  device.GetErrorText(ret, text);
                  core.LogMessage(&device, text, true);
               }
               else
               {
                  // to succeed must reach here....
                  result = MM::CanCommunicate;
               }
            }
            else
            {
               char text[MM::MaxStrLength];
               device.GetErrorText(ret, text);
               core.LogMessage(&device, text, true);
            }
            pS->Shutdown();
            if( MM::CanCommunicate == result)
               break;
            else
               // try to yield to GUI
               CDeviceUtils::SleepMs(10);
         }
         // always restore the AnswerTimeout to the default
         core.SetDeviceProperty(portToCheck.c_str(), "AnswerTimeout", answerTO);
      }
   }
   catch(...)
   {
      core.LogMessage(&device, "Exception in DetectDevice!",false);
   }
   return result;
}


///////////////////////////////////////////////////////////////////////////////
// ASIBase (convenience parent class)
//
ASIBase::ASIBase(MM::Device *device, const char *prefix) :
   oldstage_(false),
   core_(0),
   initialized_(false),
   device_(device),
   oldstagePrefix_(prefix),
   port_("Undefined")
{
}

ASIBase::~ASIBase()
{
}

// Communication "clear buffer" utility function:
int ASIBase::ClearPort(void)
{
   // Clear contents of serial port
   const int bufSize = 255;
   unsigned char clear[bufSize];
   unsigned long read = bufSize;
   int ret;
   while ((int) read == bufSize)
   {
      ret = core_->ReadFromSerial(device_, port_.c_str(), clear, bufSize, read);
      if (ret != DEVICE_OK)
         return ret;
   }
   return DEVICE_OK;                                                           
} 

// Communication "send" utility function:
int ASIBase::SendCommand(const char *command) const
{
   std::string base_command = "";
   int ret;

   if (oldstage_)
      base_command += oldstagePrefix_;
   base_command += command;
   // send command
   ret = core_->SetSerialCommand(device_, port_.c_str(), base_command.c_str(), "\r");
   return ret;
}

// Communication "send & receive" utility function:
int ASIBase::QueryCommand(const char *command, std::string &answer) const
{
   const char *terminator;
   int ret;

   // send command
   ret = SendCommand(command);
   if (ret != DEVICE_OK)
      return ret;
   // block/wait for acknowledge (or until we time out)
   if (oldstage_)
      terminator = "\r\n\3";
   else
      terminator = "\r\n";

   const size_t BUFSIZE = 2048;
   char buf[BUFSIZE] = {'\0'};
   ret = core_->GetSerialAnswer(device_, port_.c_str(), BUFSIZE, buf, terminator);
   answer = buf;

   return ret;
}

// Communication "send, receive, and look for acknowledgement" utility function:
int ASIBase::QueryCommandACK(const char *command)
{
   std::string answer;
   int ret = QueryCommand(command, answer);
   if (ret != DEVICE_OK)
      return ret;

   // The controller only acknowledges receipt of the command
   if (answer.substr(0,2) != ":A")
      return ERR_UNRECOGNIZED_ANSWER;

   return DEVICE_OK;
}

// Communication "test device type" utility function:
int ASIBase::CheckDeviceStatus(void)
{
   const char* command = "/"; // check STATUS
   std::string answer;
   int ret;

   // send status command (test for new protocol)
   oldstage_ = false;
   ret = QueryCommand(command, answer);
   if (ret != DEVICE_OK && !oldstagePrefix_.empty())
   {
      // send status command (test for older LX-4000 protocol)
      oldstage_ = true;
      ret = QueryCommand(command, answer);
   }
   return ret;
}

unsigned int ASIBase::ConvertDay(int year, int month, int day)
{
   return day + 31*(month-1) + 372*(year-2000);
}

unsigned int ASIBase::ExtractCompileDay(const char* compile_date)
{
   const char* months = "anebarprayunulugepctovec";
   if (strlen(compile_date) < 11)
      return 0;
   int year = 0;
   int month = 0;
   int day = 0;
   if (strlen(compile_date) >= 11
         && compile_date[7] == '2'  // must be 20xx for sanity checking
         && compile_date[8] == '0'
         && compile_date[9] <= '9'
         && compile_date[9] >= '0'
         && compile_date[10] <= '9'
         && compile_date[10] >= '0')
   {
      year = 2000 + 10*(compile_date[9]-'0') + (compile_date[10]-'0');
      // look for the year based on the last two characters of the abbreviated month name
      month = 1;
      for (int i=0; i<12; i++)
      {
         if (compile_date[1] == months[2*i] &&
         compile_date[2] == months[2*i+1])
         {
            month = i + 1;
         }
      }
      day = 10*(compile_date[4]-'0') + (compile_date[5]-'0');
      if (day < 1 || day > 31)
         day = 1;
      return ConvertDay(year, month, day);
   }
   return 0;
}


///////////////////////////////////////////////////////////////////////////////
// XYStage
//
XYStage::XYStage() :
   ASIBase(this, "2H"),
   stepSizeXUm_(0.0), 
   stepSizeYUm_(0.0), 
   maxSpeed_ (7.5),
   ASISerialUnit_(10.0),
   motorOn_(true),
   joyStickSpeedFast_(60),
   joyStickSpeedSlow_(5),
   joyStickMirror_(false),
   nrMoveRepetitions_(0),
   answerTimeoutMs_(1000),
   serialOnlySendChanged_(true),
   manualSerialAnswer_(""),
   compileDay_(0),
   advancedPropsEnabled_(false)
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


bool XYStage::SupportsDeviceDetection(void)
{
   return true;
}

MM::DeviceDetectionStatus XYStage::DetectDevice(void)
{

   return ASICheckSerialPort(*this,*GetCoreCallback(), port_, answerTimeoutMs_);
}

int XYStage::Initialize()
{
   core_ = GetCoreCallback();

   // empty the Rx serial buffer before sending command
   ClearPort(); 

   CPropertyAction* pAct = new CPropertyAction (this, &XYStage::OnVersion);
   CreateProperty("Version", "", MM::String, true, pAct);

   // check status first (test for communication protocol)
   int ret = CheckDeviceStatus();
   if (ret != DEVICE_OK)
      return ret;

   pAct = new CPropertyAction (this, &XYStage::OnCompileDate);
   CreateProperty("CompileDate", "", MM::String, true, pAct);
   UpdateProperty("CompileDate");

   // get the date of the firmware
   char compile_date[MM::MaxStrLength];
   if (GetProperty("CompileDate", compile_date) == DEVICE_OK)
      compileDay_ = ExtractCompileDay(compile_date);

   // if really old firmware then don't get build name
   // build name is really just for diagnostic purposes anyway
   // I think it was present before 2010 but this is easy way
   if (compileDay_ >= ConvertDay(2010, 1, 1))
   {
      pAct = new CPropertyAction (this, &XYStage::OnBuildName);
      CreateProperty("BuildName", "", MM::String, true, pAct);
      UpdateProperty("BuildName");
   }

   // Most ASIStages have the origin in the top right corner, the following reverses direction of the X-axis:
   ret = SetAxisDirection();
   if (ret != DEVICE_OK)
      return ret;

   // set stage step size and resolution
   /**
    * NOTE:  ASI return numbers in 10th of microns with an extra decimal place
	* To convert into steps, we multiply by 10 (variable ASISerialUnit_) making the step size 0.01 microns
	*/
   stepSizeXUm_ = 0.01;
   stepSizeYUm_ = 0.01;

   // Step size
   pAct = new CPropertyAction (this, &XYStage::OnStepSizeX);
   CreateProperty("StepSizeX_um", "0.0", MM::Float, true, pAct);
   pAct = new CPropertyAction (this, &XYStage::OnStepSizeY);
   CreateProperty("StepSizeY_um", "0.0", MM::Float, true, pAct);

   // Wait cycles
   if (hasCommand("WT X?")) {
      pAct = new CPropertyAction (this, &XYStage::OnWait);
      CreateProperty("Wait_Cycles", "5", MM::Integer, false, pAct);
//      SetPropertyLimits("Wait_Cycles", 0, 255);  // don't artificially restrict range
   }

   // Speed (sets both x and y)
   if (hasCommand("S X?")) {
      pAct = new CPropertyAction (this, &XYStage::OnSpeed);
      CreateProperty("Speed-S", "1", MM::Float, false, pAct);
      // Maximum Speed that can be set in Speed-S property
      char max_speed[MM::MaxStrLength];
      GetMaxSpeed(max_speed);
      CreateProperty("Maximum Speed (Do Not Change)", max_speed, MM::Float, true);
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

   // acceleration (sets both x and y)
   if (hasCommand("AC X?")) {
      pAct = new CPropertyAction (this, &XYStage::OnAcceleration);
      CreateProperty("Acceleration-AC(ms)", "0", MM::Integer, false, pAct);
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

   // JoyStick MirrorsX 
   // TODO: the following properties should only appear in controllers version 8 and higher
   pAct = new CPropertyAction (this, &XYStage::OnJSMirror);
   CreateProperty("JoyStick Reverse", "Off", MM::String, false, pAct);
   AddAllowedValue("JoyStick Reverse", "On");
   AddAllowedValue("JoyStick Reverse", "Off");

   pAct = new CPropertyAction (this, &XYStage::OnJSFastSpeed);
   CreateProperty("JoyStick Fast Speed", "100", MM::Integer, false, pAct);
   SetPropertyLimits("JoyStick Fast Speed", 1, 100);

   pAct = new CPropertyAction (this, &XYStage::OnJSSlowSpeed);
   CreateProperty("JoyStick Slow Speed", "100", MM::Integer, false, pAct);
   SetPropertyLimits("JoyStick Slow Speed", 1, 100);

   // property to allow sending arbitrary serial commands and receiving response
   pAct = new CPropertyAction (this, &XYStage::OnSerialCommand);
   CreateProperty("SerialCommand", "", MM::String, false, pAct);

   // this is only changed programmatically, never by user
   // contains last response to the OnSerialCommand action
   pAct = new CPropertyAction (this, &XYStage::OnSerialResponse);
   CreateProperty("SerialResponse", "", MM::String, true, pAct);

   // disable sending serial commands unless changed (by default this is enabled)
   pAct = new CPropertyAction (this, &XYStage::OnSerialCommandOnlySendChanged);
   CreateProperty("OnlySendSerialCommandOnChange", "Yes", MM::String, false, pAct);
   AddAllowedValue("OnlySendSerialCommandOnChange", "Yes");
   AddAllowedValue("OnlySendSerialCommandOnChange", "No");

   // generates a set of additional advanced properties that are rarely used
   pAct = new CPropertyAction (this, &XYStage::OnAdvancedProperties);
   CreateProperty("EnableAdvancedProperties", "No", MM::String, false, pAct);
   AddAllowedValue("EnableAdvancedProperties", "No");
   AddAllowedValue("EnableAdvancedProperties", "Yes");

   /* Disabled.  Use the MA command instead
   // Number of times stage approaches a new position (+1)
   if (hasCommand("CCA Y?")) {
      pAct = new CPropertyAction(this, &XYStage::OnNrMoveRepetitions);
      CreateProperty("NrMoveRepetitions", "0", MM::Integer, false, pAct);
      SetPropertyLimits("NrMoveRepetitions", 0, 10);
   }
   */

   /*
   ret = UpdateStatus();  
   if (ret != DEVICE_OK)
      return ret;
   */

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
   ClearPort(); 

   const char* command = "/";
   string answer;
   // query the device
   int ret = QueryCommand(command, answer);
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


int XYStage::SetPositionSteps(long x, long y)
{
   // empty the Rx serial buffer before sending command
   ClearPort();

   ostringstream command;
   command << fixed << "M X=" << x/ASISerialUnit_ << " Y=" << y/ASISerialUnit_; // steps are 10th of micros

   string answer;
   // query the device
   int ret = QueryCommand(command.str().c_str(), answer);
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

int XYStage::SetRelativePositionSteps(long x, long y)
{
   // empty the Rx serial buffer before sending command
   ClearPort();

   ostringstream command;
   if ( (x == 0) && (y != 0) )
   {
      command << fixed << "R Y=" << y/ASISerialUnit_;
   }
   else if ( (x != 0) && (y == 0) )
   {
      command << fixed << "R X=" << x/ASISerialUnit_;
   }
   else
   {
      command << fixed << "R X=" << x/ASISerialUnit_  << " Y=" << y/ASISerialUnit_; // in 10th of microns
   }

   string answer;
   // query the device
   int ret = QueryCommand(command.str().c_str(), answer);
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

int XYStage::GetPositionSteps(long& x, long& y)
{
   // empty the Rx serial buffer before sending command
   ClearPort();

   ostringstream command;
   command << "W X Y";

   string answer;
   // query the device
   int ret = QueryCommand(command.str().c_str(), answer);
   if (ret != DEVICE_OK)
      return ret;

   if (answer.length() > 2 && answer.substr(0, 2).compare(":N") == 0)
   {
      int errNo = atoi(answer.substr(2).c_str());
      return ERR_OFFSET + errNo;
   }
   else if (answer.length() > 0)
   {
	  float xx, yy;
      char head[64];
	  char iBuf[256];
	  strcpy(iBuf,answer.c_str());
	  sscanf(iBuf, "%s %f %f\r\n", head, &xx, &yy);
	  x = (long) (xx * ASISerialUnit_);
	  y = (long) (yy * ASISerialUnit_); 

      return DEVICE_OK;
   }

   return ERR_UNRECOGNIZED_ANSWER;
}
  
int XYStage::SetOrigin()
{
   string answer;
   // query the device
   int ret = QueryCommand("H X=0 Y=0", answer); // use command HERE, zero (z) zero all x,y,z
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


void XYStage::Wait()
{
   // empty the Rx serial buffer before sending command
   ClearPort();

   //if (stopSignal_) return DEVICE_OK;
   bool busy=true;
   const char* command = "/";
   string answer="";
   // query the device
   QueryCommand(command, answer);
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

		// query the device
		QueryCommand(command, answer);
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
   ClearPort();

   string answer;
   // query the device
   int ret = QueryCommand("! X Y", answer); // use command HOME
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
   string answer;
   // query the device
   ret = QueryCommand("! X Y", answer); // use command HOME
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
   ClearPort();

   stopSignal_ = true;
   string answer;
   // query the device
   int ret = QueryCommand("HALT", answer);  // use command HALT "\"
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
 
int XYStage::GetLimitsUm(double& /*xMin*/, double& /*xMax*/, double& /*yMin*/, double& /*yMax*/)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

int XYStage::GetStepLimits(long& /*xMin*/, long& /*xMax*/, long& /*yMin*/, long& /*yMax*/)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

bool XYStage::hasCommand(std::string command) {
   string answer;
   // query the device
   int ret = QueryCommand(command.c_str(), answer);
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
      string answer;
      // query the device
      int ret = QueryCommand(command.str().c_str(), answer);
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

// Get the compile date of this controller
int XYStage::OnCompileDate(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      if (initialized_)
         return DEVICE_OK;

      ostringstream command;
      command << "CD";
      string answer;
      // query the device
      int ret = QueryCommand(command.str().c_str(), answer);
      if (ret != DEVICE_OK)
         return ret;

      pProp->Set(answer.c_str());

   }
   return DEVICE_OK;
}

// Get the build name of this controller
int XYStage::OnBuildName(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      if (initialized_)
         return DEVICE_OK;

      ostringstream command;
      command << "BU";
      string answer;
      // query the device
      int ret = QueryCommand(command.str().c_str(), answer);
      if (ret != DEVICE_OK)
         return ret;

      pProp->Set(answer.c_str());

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
      string answer;
      // some controller do not answer, so do not check answer
      int ret = SendCommand(command.str().c_str());
      if (ret != DEVICE_OK)
         return ret;

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
      string answer;
      // query command
      int ret = QueryCommand(command.str().c_str(), answer);
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

      // enforce positive
      if (waitCycles < 0)
         waitCycles = 0;

      // if firmware date is 2009+  then use msec/int definition of WaitCycles
      // would be better to parse firmware (8.4 and earlier used unsigned char)
      // and that transition occurred ~2008 but not sure exactly when
      if (compileDay_ >= ConvertDay(2009, 1, 1))
      {
         // don't enforce upper limit
      }
      else  // enforce limit for 2008 and earlier firmware or
      {     // if getting compile date wasn't successful
         if (waitCycles > 255)
            waitCycles = 255;
      }

      ostringstream command;
      command << "WT X=" << waitCycles << " Y=" << waitCycles;
      string answer;
      // query command
      int ret = QueryCommand(command.str().c_str(), answer);
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
      string answer;
      // query command
      int ret = QueryCommand(command.str().c_str(), answer);
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
      string answer;
      // query command
      int ret = QueryCommand(command.str().c_str(), answer);
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
      string answer;
      // query command
      int ret = QueryCommand(command.str().c_str(), answer);
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
      string answer;
      // query command
      int ret = QueryCommand(command.str().c_str(), answer);
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

int XYStage::OnAcceleration(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // To simplify our life we only read out acceleration for the X axis, but set for both
      ostringstream command;
      command << "AC X?";
      string answer;
      // query command
      int ret = QueryCommand(command.str().c_str(), answer);
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
      double accel;
      pProp->Get(accel);
      if (accel < 0.0)
         accel = 0.0;
      ostringstream command;
      command << "AC X=" << accel << " Y=" << accel;
      string answer;
      // query command
      int ret = QueryCommand(command.str().c_str(), answer);
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
      string answer;
      // query command
      int ret = QueryCommand(command.str().c_str(), answer);
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
      command << fixed << "OS X=" << overShoot << " Y=" << overShoot;
      string answer;
      // query the device
      int ret = QueryCommand(command.str().c_str(), answer);
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
      string answer;
      // query command
      int ret = QueryCommand(command.str().c_str(), answer);
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
      command << fixed << "E X=" << error << " Y=" << error;
      string answer;
      // query the device
      int ret = QueryCommand(command.str().c_str(), answer);
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

int XYStage::GetMaxSpeed(char * maxSpeedStr)
{
   double origMaxSpeed = maxSpeed_;
   char orig_speed[MM::MaxStrLength];
   int ret = GetProperty("Speed-S", orig_speed);
   if ( ret != DEVICE_OK)
      return ret;
   maxSpeed_ = 10001;
   SetProperty("Speed-S", "10000");
   ret = GetProperty("Speed-S", maxSpeedStr);
   maxSpeed_ = atof(maxSpeedStr);
   if (maxSpeed_ <= 0.1)
      maxSpeed_ = origMaxSpeed;  // restore default if something went wrong in which case atof returns 0.0
   if (ret != DEVICE_OK)
      return ret;
   ret = SetProperty("Speed-S", orig_speed);
   if (ret != DEVICE_OK)
      return ret;
   return DEVICE_OK;
}

int XYStage::OnSpeed(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // To simplify our life we only read out waitcycles for the X axis, but set for both
      ostringstream command;
      command << "S X?";
      string answer;
      // query command
      int ret = QueryCommand(command.str().c_str(), answer);
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
      else if (speed > maxSpeed_)
         speed = maxSpeed_;
      ostringstream command;
      command << fixed << "S X=" << speed << " Y=" << speed;
      string answer;
      // query the device
      int ret = QueryCommand(command.str().c_str(), answer);
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
      string answer;
      // query command
      int ret = QueryCommand(command.str().c_str(), answer);
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

int XYStage::OnJSMirror(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // TODO: read from device, at least on initialization
      if (joyStickMirror_)
         pProp->Set("On");
      else
         pProp->Set("Off");

      return DEVICE_OK;
   }
   else if (eAct == MM::AfterSet) {
      string mirror;
      string value;
      pProp->Get(mirror);
      if (mirror == "On") {
         if (joyStickMirror_)
            return DEVICE_OK;
         joyStickMirror_ = true;
         value = "-";
      } else {
         if (!joyStickMirror_)
            return DEVICE_OK;
         joyStickMirror_ = false;
         value = "";
      }
      ostringstream command;
      command << "JS X=" << value << joyStickSpeedFast_ << " Y=" << value << joyStickSpeedSlow_;
      string answer;
      // query command
      int ret = QueryCommand(command.str().c_str(), answer);
      if (ret != DEVICE_OK)
         return ret;

      if (answer.substr(0,2).compare(":A") == 0)
         return DEVICE_OK;
      else if (answer.substr(answer.length(), 1) == "A")
         return DEVICE_OK;
      else if (answer.substr(0, 2).compare(":N") == 0 && answer.length() > 2)
      {
         int errNo = atoi(answer.substr(3).c_str());
         return ERR_OFFSET + errNo;
      }
      else
         return ERR_UNRECOGNIZED_ANSWER;
   }
   return DEVICE_OK;
}


int XYStage::OnJSSwapXY(MM::PropertyBase* /* pProp*/ , MM::ActionType /* eAct*/)
{
   return DEVICE_NOT_SUPPORTED;
}

int XYStage::OnJSFastSpeed(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // TODO: read from device, at least on initialization
      pProp->Set((long) joyStickSpeedFast_);
      return DEVICE_OK;
   }
   else if (eAct == MM::AfterSet) {
      long speed;
      pProp->Get(speed);
      joyStickSpeedFast_ = (int) speed;

      string value = "";
      if (joyStickMirror_)
         value = "-";

      ostringstream command;
      command << "JS X=" << value << joyStickSpeedFast_ << " Y=" << value << joyStickSpeedSlow_;
      string answer;
      // query command
      int ret = QueryCommand(command.str().c_str(), answer);
      if (ret != DEVICE_OK)
         return ret;

      if (answer.substr(0,2).compare(":A") == 0)
         return DEVICE_OK;
      else if (answer.substr(answer.length(), 1) == "A")
         return DEVICE_OK;
      else if (answer.substr(0, 2).compare(":N") == 0 && answer.length() > 2)
      {
         int errNo = atoi(answer.substr(3).c_str());
         return ERR_OFFSET + errNo;
      }
      else
         return ERR_UNRECOGNIZED_ANSWER;
   }
   
   return DEVICE_OK;
}

int XYStage::OnJSSlowSpeed(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // TODO: read from device, at least on initialization
      pProp->Set((long) joyStickSpeedSlow_);
      return DEVICE_OK;
   }
   else if (eAct == MM::AfterSet) {
      long speed;
      pProp->Get(speed);
      joyStickSpeedSlow_ = (int) speed;

      string value = "";
      if (joyStickMirror_)
         value = "-";

      ostringstream command;
      command << "JS X=" << value << joyStickSpeedFast_ << " Y=" << value << joyStickSpeedSlow_;
      string answer;
      // query command
      int ret = QueryCommand(command.str().c_str(), answer);
      if (ret != DEVICE_OK)
         return ret;

      if (answer.substr(0,2).compare(":A") == 0)
         return DEVICE_OK;
      else if (answer.substr(answer.length(), 1) == "A")
         return DEVICE_OK;
      else if (answer.substr(0, 2).compare(":N") == 0 && answer.length() > 2) {
         int errNo = atoi(answer.substr(3).c_str());
         return ERR_OFFSET + errNo;

      }
      else
         return ERR_UNRECOGNIZED_ANSWER;
   }

   return DEVICE_OK;
}

int XYStage::OnSerialCommand(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // do nothing
   }
   else if (eAct == MM::AfterSet) {
      static string last_command;
      string tmpstr;
      pProp->Get(tmpstr);
      tmpstr =   UnescapeControlCharacters(tmpstr);
      // only send the command if it has been updated, or if the feature has been set to "no"/false then always send
      if (!serialOnlySendChanged_ || (tmpstr.compare(last_command) != 0))
      {
         last_command = tmpstr;
         int ret = QueryCommand(tmpstr.c_str(), manualSerialAnswer_);
         if (ret != DEVICE_OK)
            return ret;
      }
   }
   return DEVICE_OK;
}

int XYStage::OnSerialResponse(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet || eAct == MM::AfterSet)
   {
      // always read
      if (!pProp->Set(EscapeControlCharacters(manualSerialAnswer_).c_str()))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   return DEVICE_OK;
}

int XYStage::OnSerialCommandOnlySendChanged(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   string tmpstr;
   if (eAct == MM::AfterSet) {
      pProp->Get(tmpstr);
      if (tmpstr.compare("Yes") == 0)
         serialOnlySendChanged_ = true;
      else
         serialOnlySendChanged_ = false;
   }
   return DEVICE_OK;
}

int XYStage::OnAdvancedProperties(MM::PropertyBase* pProp, MM::ActionType eAct)
// special property, when set to "yes" it creates a set of little-used properties that can be manipulated thereafter
// these parameters exposed with some hurdle to user: KP, KI, KD, AA
{
   if (eAct == MM::BeforeGet)
   {
      return DEVICE_OK; // do nothing
   }
   else if (eAct == MM::AfterSet) {
      string tmpstr;
      pProp->Get(tmpstr);
      if ((tmpstr.compare("Yes") == 0) && !advancedPropsEnabled_) // after creating advanced properties once no need to repeat
      {
         CPropertyAction* pAct;
         advancedPropsEnabled_ = true;

         // overshoot (OS)  // in Nico's original

         // servo integral term (KI)
         if (hasCommand("KI X?")) {
            pAct = new CPropertyAction (this, &XYStage::OnKIntegral);
            CreateProperty("ServoIntegral-KI", "0", MM::Integer, false, pAct);
         }

         // servo proportional term (KP)
         if (hasCommand("KP X?")) {
            pAct = new CPropertyAction (this, &XYStage::OnKProportional);
            CreateProperty("ServoProportional-KP", "0", MM::Integer, false, pAct);
         }

         // servo derivative term (KD)
         if (hasCommand("KD X?")) {
            pAct = new CPropertyAction (this, &XYStage::OnKDerivative);
            CreateProperty("ServoIntegral-KD", "0", MM::Integer, false, pAct);
         }

         // Align calibration/setting for pot in drive electronics (AA)
         if (hasCommand("AA X?")) {
            pAct = new CPropertyAction (this, &XYStage::OnAAlign);
            CreateProperty("MotorAlign-AA", "0", MM::Integer, false, pAct);
         }

         // Autozero drive electronics (AZ)  // omitting for now, need to do for each axis (see Tiger)

         // number of extra move repetitions  // in Nico's original
      }
   }
   return DEVICE_OK;
}

int XYStage::OnKIntegral(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      command << "KI X?";
      string answer;
      int ret = QueryCommand(command.str().c_str(), answer);
      if (ret != DEVICE_OK)
         return ret;
      if (answer.substr(0,2).compare(":A") == 0)
      {
         tmp = atol(answer.substr(5).c_str());
         if (!pProp->Set(tmp))
            return DEVICE_INVALID_PROPERTY_VALUE;
         else
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
      pProp->Get(tmp);
      command << "KI X =" << tmp << " Y=" << tmp;
      string answer;
      int ret = QueryCommand(command.str().c_str(), answer);
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

int XYStage::OnKProportional(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      command << "KP X?";
      string answer;
      int ret = QueryCommand(command.str().c_str(), answer);
      if (ret != DEVICE_OK)
         return ret;
      if (answer.substr(0,2).compare(":A") == 0)
      {
         tmp = atol(answer.substr(5).c_str());
         if (!pProp->Set(tmp))
            return DEVICE_INVALID_PROPERTY_VALUE;
         else
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
      pProp->Get(tmp);
      command << "KP X =" << tmp << " Y=" << tmp;
      string answer;
      int ret = QueryCommand(command.str().c_str(), answer);
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

int XYStage::OnKDerivative(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      command << "KD X?";
      string answer;
      int ret = QueryCommand(command.str().c_str(), answer);
      if (ret != DEVICE_OK)
         return ret;
      if (answer.substr(0,2).compare(":A") == 0)
      {
         tmp = atol(answer.substr(5).c_str());
         if (!pProp->Set(tmp))
            return DEVICE_INVALID_PROPERTY_VALUE;
         else
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
      pProp->Get(tmp);
      command << "KD X =" << tmp << " Y=" << tmp;
      string answer;
      int ret = QueryCommand(command.str().c_str(), answer);
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

int XYStage::OnAAlign(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      command << "AA X?";
      string answer;
      int ret = QueryCommand(command.str().c_str(), answer);
      if (ret != DEVICE_OK)
         return ret;
      if (answer.substr(0,2).compare(":A") == 0)
      {
         tmp = atol(answer.substr(5).c_str());
         if (!pProp->Set(tmp))
            return DEVICE_INVALID_PROPERTY_VALUE;
         else
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
      pProp->Get(tmp);
      command << "AA X =" << tmp << " Y=" << tmp;
      string answer;
      int ret = QueryCommand(command.str().c_str(), answer);
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


int XYStage::GetPositionStepsSingle(char /*axis*/, long& /*steps*/)
{
   return ERR_UNRECOGNIZED_ANSWER;
}

int XYStage::SetAxisDirection()
{
   ostringstream command;
   command << "UM X=-10000 Y=10000";
   string answer = "";
   // query command
   int ret = QueryCommand(command.str().c_str(), answer);
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

string XYStage::EscapeControlCharacters(const string v)
// based on similar function in FreeSerialPort.cpp
{
   ostringstream mess;  mess.str("");
   for( string::const_iterator ii = v.begin(); ii != v.end(); ++ii)
   {
      if (*ii > 31)
         mess << *ii;
      else if (*ii == 13)
         mess << "\\r";
      else if (*ii == 10)
         mess << "\\n";
      else if (*ii == 9)
         mess << "\\t";
      else
         mess << "\\" << (unsigned int)(*ii);
   }
   return mess.str();
}

string XYStage::UnescapeControlCharacters(const string v0)
// based on similar function in FreeSerialPort.cpp
{
   // the string input from the GUI can contain escaped control characters, currently these are always preceded with \ (0x5C)
   // and always assumed to be decimal or C style, not hex

   string detokenized;
   string v = v0;

   for( string::iterator jj = v.begin(); jj != v.end(); ++jj)
   {
      bool breakNow = false;
      if( '\\' == *jj )
      {
         // the next 1 to 3 characters might be converted into a control character
         ++jj;
         if( v.end() == jj)
         {
            // there was an escape at the very end of the input string so output it literally
            detokenized.push_back('\\');
            break;
         }
         const string::iterator nextAfterEscape = jj;
         std::string thisControlCharacter;
         // take any decimal digits immediately after the escape character and convert to a control character
         while(0x2F < *jj && *jj < 0x3A )
         {
            thisControlCharacter.push_back(*jj++);
            if( v.end() == jj)
            {
               breakNow = true;
               break;
            }
         }
         int code = -1;
         if ( 0 < thisControlCharacter.length())
         {
            istringstream tmp(thisControlCharacter);
            tmp >> code;
         }
         // otherwise, if we are still at the first character after the escape,
         // possibly treat the next character like a 'C' control character
         if( nextAfterEscape == jj)
         {
            switch( *jj)
            {
            case 'r':
               ++jj;
               code = 13; // CR \r
               break;
            case 'n':
               ++jj;
               code = 10; // LF \n
               break;
            case 't':
               ++jj;
               code = 9; // TAB \t
               break;
            case '\\':
               ++jj;
               code = '\\';
               break;
            default:
               code = '\\'; // the '\' wasn't really an escape character....
               break;
            }
            if( v.end() == jj)
               breakNow = true;
         }
         if( -1 < code)
            detokenized.push_back((char)code);
      }
      if( breakNow)
         break;
      detokenized.push_back(*jj);
   }
   return detokenized;
}



///////////////////////////////////////////////////////////////////////////////
// ZStage

ZStage::ZStage() :
   ASIBase(this, "1H"),
   axis_("Z"),
   axisNr_(4),
   stepSizeUm_(0.1),
   answerTimeoutMs_(1000),
   sequenceable_(false),
   runningFastSequence_(false),
   hasRingBuffer_(false),
   nrEvents_(0),
   maxSpeed_(7.5),
   motorOn_(true),
   compileDay_(0)
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
   AddAllowedValue("Axis", "F");
   AddAllowedValue("Axis", "P");
   AddAllowedValue("Axis", "Z");
}

ZStage::~ZStage()
{
   Shutdown();
}

void ZStage::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_ZStageDeviceName);
}

bool ZStage::SupportsDeviceDetection(void)
{
   return true;
}

MM::DeviceDetectionStatus ZStage::DetectDevice(void)
{

   return ASICheckSerialPort(*this,*GetCoreCallback(), port_, answerTimeoutMs_);
}

int ZStage::Initialize()
{
   core_ = GetCoreCallback();

   // empty the Rx serial buffer before sending command
   ClearPort();

   // check status first (test for communication protocol)
   int ret = CheckDeviceStatus();
   if (ret != DEVICE_OK)
       return ret;

   // needs to be called first since it sets hasRingBuffer_
   GetControllerInfo();

   stepSizeUm_ = 0.1; //res;

   ret = GetPositionSteps(curSteps_);
   // if command fails, try one more time,
   // other devices may have send crud to this serial port during device detection
   if (ret != DEVICE_OK)
      ret = GetPositionSteps(curSteps_);

   CPropertyAction* pAct;

   pAct = new CPropertyAction (this, &ZStage::OnCompileDate);
   CreateProperty("CompileDate", "", MM::String, true, pAct);
   UpdateProperty("CompileDate");

   // get the date of the firmware
   char compile_date[MM::MaxStrLength];
   if (GetProperty("CompileDate", compile_date) == DEVICE_OK)
      compileDay_ = ExtractCompileDay(compile_date);

   if (HasRingBuffer() && nrEvents_ == 0)
   {
      // we couldn't detect size of the ring buffer automatically so create property
      //   to allow user to change it
      pAct = new CPropertyAction (this, &ZStage::OnRingBufferSize);
      CreateProperty("RingBufferSize", "50", MM::Integer, false, pAct);
      AddAllowedValue("RingBufferSize", "50");
      AddAllowedValue("RingBufferSize", "250");
      nrEvents_ = 50;  // modified in action handler
   }
   else
   {
      ostringstream tmp;
      tmp.str("");
      tmp << nrEvents_;  // initialized in GetControllerInfo() if we got here
      CreateProperty("RingBufferSize", tmp.str().c_str(), MM::String, true);
   }

   if (HasRingBuffer())
   {
      pAct = new CPropertyAction (this, &ZStage::OnSequence);
      const char* spn = "Use Sequence";
      CreateProperty(spn, "No", MM::String, false, pAct);
      AddAllowedValue(spn, "No");
      AddAllowedValue(spn, "Yes");

      pAct = new CPropertyAction (this, &ZStage::OnFastSequence);
      spn = "Use Fast Sequence";
      CreateProperty(spn, "No", MM::String, false, pAct);
      AddAllowedValue(spn, "No");
      AddAllowedValue(spn, "Armed");
   }

   // Speed (sets both x and y)
   if (hasCommand("S " + axis_ + "?")) {
      pAct = new CPropertyAction (this, &ZStage::OnSpeed);
      CreateProperty("Speed-S", "1", MM::Float, false, pAct);
      // Maximum Speed that can be set in Speed-S property
      char max_speed[MM::MaxStrLength];
      GetMaxSpeed(max_speed);
      CreateProperty("Maximum Speed (Do Not Change)", max_speed, MM::Float, true);
   }

   // Backlash (sets both x and y)
   if (hasCommand("B " + axis_ + "?")) {
      pAct = new CPropertyAction (this, &ZStage::OnBacklash);
      CreateProperty("Backlash-B", "0", MM::Float, false, pAct);
   }

   // Error (sets both x and y)
   if (hasCommand("E " + axis_ + "?")) {
      pAct = new CPropertyAction (this, &ZStage::OnError);
      CreateProperty("Error-E(nm)", "0", MM::Float, false, pAct);
   }

   // acceleration (sets both x and y)
   if (hasCommand("AC " + axis_ + "?")) {
      pAct = new CPropertyAction (this, &ZStage::OnAcceleration);
      CreateProperty("Acceleration-AC(ms)", "0", MM::Integer, false, pAct);
   }

   // Finish Error (sets both x and y)
   if (hasCommand("PC " + axis_ + "?")) {
      pAct = new CPropertyAction (this, &ZStage::OnFinishError);
      CreateProperty("FinishError-PCROS(nm)", "0", MM::Float, false, pAct);
   }

   // OverShoot (sets both x and y)
   if (hasCommand("OS " + axis_ + "?")) {
      pAct = new CPropertyAction (this, &ZStage::OnOverShoot);
      CreateProperty("OverShoot(um)", "0", MM::Float, false, pAct);
   }

   // MotorCtrl, (works on both x and y)
   pAct = new CPropertyAction (this, &ZStage::OnMotorCtrl);
   CreateProperty("MotorOnOff", "On", MM::String, false, pAct);
   AddAllowedValue("MotorOnOff", "On");
   AddAllowedValue("MotorOnOff", "Off");

   // Wait cycles
   if (hasCommand("WT " + axis_ + "?")) {
      pAct = new CPropertyAction (this, &ZStage::OnWait);
      CreateProperty("Wait_Cycles", "5", MM::Integer, false, pAct);
      //      SetPropertyLimits("Wait_Cycles", 0, 255);  // don't artificially restrict range
   }

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
   if (runningFastSequence_)
   {
      return false;
   }

   // empty the Rx serial buffer before sending command
   ClearPort();

   const char* command = "/";
   string answer;
   // query command
   int ret = QueryCommand(command, answer);
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
   ClearPort();

   ostringstream command;
   command << fixed << "M " << axis_ << "=" << pos / stepSizeUm_; // in 10th of micros

   string answer;
   // query the device
   int ret = QueryCommand(command.str().c_str(), answer);
   if (ret != DEVICE_OK)
      return ret;

   if (answer.substr(0,2).compare(":A") == 0 || answer.substr(1,2).compare(":A") == 0)
   {
      this->OnStagePositionChanged(pos);
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
   ClearPort();

   ostringstream command;
   command << "W " << axis_;

   string answer;
   // query command
   int ret = QueryCommand(command.str().c_str(), answer);
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

int ZStage::SetRelativePositionUm(double d)
{
   // empty the Rx serial buffer before sending command
   ClearPort();

   ostringstream command;
   command << fixed << "R " << axis_ << "=" << d / stepSizeUm_; // in 10th of micros

   string answer;
   // query the device
   int ret = QueryCommand(command.str().c_str(), answer);
   if (ret != DEVICE_OK)
      return ret;

   if (answer.substr(0,2).compare(":A") == 0 || answer.substr(1,2).compare(":A") == 0)
   {
      // we don't know the updated position to call this
      //this->OnStagePositionChanged(pos);
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
  
int ZStage::SetPositionSteps(long pos)
{
   ostringstream command;
   command << "M " << axis_ << "=" << pos; // in 10th of micros

   string answer;
   // query command
   int ret = QueryCommand(command.str().c_str(), answer);
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
   ClearPort();

   ostringstream command;
   command << "W " << axis_;

   string answer;
   // query command
   int ret = QueryCommand(command.str().c_str(), answer);
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
//   string answer;
//   // query command
//   int ret = QueryCommand(command, answer);
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
   ClearPort();

   ostringstream os;
   os << "H " << axis_;
   string answer;
   // query command
   int ret = QueryCommand(os.str().c_str(), answer); // use command HERE, zero (z) zero all x,y,z
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

bool ZStage::HasRingBuffer()
{
   return hasRingBuffer_;
}

int ZStage::StartStageSequence()
{
   if (runningFastSequence_)
   {
      return DEVICE_OK;
   }

   string answer;
   
   // ensure that ringbuffer pointer points to first entry and 
   // that we only trigger the desired axis
   ostringstream os;
   os << "RM Y=" << axisNr_ << " Z=0";
 
   int ret = QueryCommand(os.str().c_str(), answer); 
   if (ret != DEVICE_OK)
      return ret;

   if (answer.substr(0,2).compare(":A") == 0 || answer.substr(1,2).compare(":A") == 0)
   {  
      ret = QueryCommand("TTL X=1", answer); // switches on TTL triggering
      if (ret != DEVICE_OK)
         return ret;

      if (answer.substr(0,2).compare(":A") == 0 || answer.substr(1,2).compare(":A") == 0)
         return DEVICE_OK;
   }

   return ERR_UNRECOGNIZED_ANSWER;
}

int ZStage::StopStageSequence() 
{
   if (runningFastSequence_)
   {
      return DEVICE_OK;
   }

   std::string answer;
   int ret = QueryCommand("TTL X=0", answer); // switches off TTL triggering
   if (ret != DEVICE_OK)
      return ret;

   if (answer.substr(0,2).compare(":A") == 0 || answer.substr(1,2).compare(":A") == 0)
         return DEVICE_OK;

   return DEVICE_OK;
}

int ZStage::SendStageSequence()
{
   if (runningFastSequence_)
   {
      return DEVICE_OK;
   }

   // first clear the buffer in the device
   std::string answer;
   int ret = QueryCommand("RM X=0", answer); // clears the ringbuffer
   if (ret != DEVICE_OK)
      return ret;

   if (answer.substr(0,2).compare(":A") == 0 || answer.substr(1,2).compare(":A") == 0)
   {
      for (unsigned i=0; i< sequence_.size(); i++)
      {
         ostringstream os;
         os.precision(0);
         if (compileDay_ >= ConvertDay(2015, 10, 23))
         {
            os << fixed << "LD " << axis_ << "=" << sequence_[i] * 10;  // 10 here is for unit multiplier/1000
            ret = QueryCommand(os.str().c_str(), answer);
            if (ret != DEVICE_OK)
               return ret;
         }
         else
         {
            // For WhizKid the LD reply originally was :A without <CR><LF> so
            //   send extra "empty command"  and get back :N-1 which we ignore
            // basically we are trying to compensate for the controller's faults here
            // but as of 2015-10-23 the firmware to "properly" responds with <CR><LF>
            os << fixed << "LD " << axis_ << "=" << sequence_[i] * 10 << "\r\n";
            ret = QueryCommand(os.str().c_str(), answer);
            if (ret != DEVICE_OK)
               return ret;

            // the answer will also have a :N-1 in it, ignore.
            if (! (answer.substr(0,2).compare(":A") == 0 || answer.substr(1,2).compare(":A") == 0) )
               return ERR_UNRECOGNIZED_ANSWER;
         }
      }
   }

   return DEVICE_OK;
}

int ZStage::ClearStageSequence()
{
   if (runningFastSequence_)
   {
      return DEVICE_OK;
   }

   sequence_.clear();

   // clear the buffer in the device
   std::string answer;
   int ret = QueryCommand("RM X=0", answer); // clears the ringbuffer
   if (ret != DEVICE_OK)
      return ret;

   if (answer.substr(0,2).compare(":A") == 0 || answer.substr(1,2).compare(":A") == 0)
      return DEVICE_OK;
   
   return ERR_UNRECOGNIZED_ANSWER;
}

int ZStage::AddToStageSequence(double position)
{
   if (runningFastSequence_)
   {
      return DEVICE_OK;
   }

   sequence_.push_back(position);

   return DEVICE_OK;
}

/*
 * This function checks what is available in this controller
 * It should really be part of a Hub Device
 */
int ZStage::GetControllerInfo()
{
   std::string answer;
   int ret = QueryCommand("BU X", answer);
   if (ret != DEVICE_OK)
      return ret;

   std::istringstream iss(answer);
   std::string token;
   while (getline(iss, token, '\r'))
   {
      std::string ringBuffer = "RING BUFFER";
      if (0 == token.compare(0, ringBuffer.size(), ringBuffer))
      {
         hasRingBuffer_ = true;
         if (token.size() > ringBuffer.size()) 
         {
            // tries to read ring buffer size, this works since 2013-09-03
            //   change to firmware which prints max size
            int rsize = atoi(token.substr(ringBuffer.size()).c_str());
            if (rsize > 0)
            {
               // only used in GetStageSequenceMaxLength as defined in .h file
               nrEvents_ = rsize;
            }
         }
      }
	   std::string ma = "Motor Axes: ";
	   if (token.substr(0, ma.length()) == ma)
	   {
		   std::istringstream axes(token.substr(ma.length(), std::string::npos));
		   std::string thisAxis;
		   int i = 1;
		   while (getline(axes, thisAxis, ' '))
		   {
            if (thisAxis == axis_)
			   {
               axisNr_ = i;
            }
            i = i << 1;
         }
      }	 
      // TODO: add in tests for other capabilities/devices
   }
 
   LogMessage(answer.c_str(), false);

   return DEVICE_OK;
}

bool ZStage::hasCommand(std::string command) {
   string answer;
   // query the device
   int ret = QueryCommand(command.c_str(), answer);
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

int ZStage::OnSequence(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      if (sequenceable_)
         pProp->Set("Yes");
      else
         pProp->Set("No");
   }
   else if (eAct == MM::AfterSet)
   {
      std::string prop;
      pProp->Get(prop);
      sequenceable_ = false;
      if (prop == "Yes")
         sequenceable_ = true;
   }

   return DEVICE_OK;
}

int ZStage::OnFastSequence(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   int ret;

   if (eAct == MM::BeforeGet)
   {
      if (runningFastSequence_)
         pProp->Set("Armed");
      else
         pProp->Set("No");
   }
   else if (eAct == MM::AfterSet)
   {
      std::string prop;
      pProp->Get(prop);

      // only let user do fast sequence if regular one is enabled
      if (!sequenceable_) {
         pProp->Set("No");
         return DEVICE_OK;
      }

      if (prop.compare("Armed") == 0)
      {
         runningFastSequence_ = false;
         ret = SendStageSequence();
         if (ret) return ret;  // same as RETURN_ON_MM_ERROR
         ret = StartStageSequence();
         if (ret) return ret;  // same as RETURN_ON_MM_ERROR
         runningFastSequence_ = true;
      }
      else
      {
         runningFastSequence_ = false;
         ret = StopStageSequence();
         if (ret) return ret;  // same as RETURN_ON_MM_ERROR
      }
   }


   return DEVICE_OK;
}

int ZStage::OnRingBufferSize(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(nrEvents_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(nrEvents_);
   }

   return DEVICE_OK;
}

// Get the compile date of this controller
int ZStage::OnCompileDate(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      if (initialized_)
         return DEVICE_OK;

      ostringstream command;
      command << "CD";
      string answer;
      // query the device
      int ret = QueryCommand(command.str().c_str(), answer);
      if (ret != DEVICE_OK)
         return ret;

      pProp->Set(answer.c_str());

   }
   return DEVICE_OK;
}

// This sets the number of waitcycles
int ZStage::OnWait(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // To simplify our life we only read out waitcycles for the X axis, but set for both
      ostringstream command;
      command << "WT " << axis_ << "?";
      string answer;
      // query command
      int ret = QueryCommand(command.str().c_str(), answer);
      if (ret != DEVICE_OK)
         return ret;

      if (answer.substr(0,2).compare(":" + axis_) == 0)
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

      // enforce positive
      if (waitCycles < 0)
         waitCycles = 0;

      // if firmware date is 2009+  then use msec/int definition of WaitCycles
      // would be better to parse firmware (8.4 and earlier used unsigned char)
      // and that transition occurred ~2008 but this is easier than trying to
      // parse version strings
      if (compileDay_ >= ConvertDay(2009, 1, 1))
      {
         // don't enforce upper limit
      }
      else  // enforce limit for 2008 and earlier firmware or
      {     // if getting compile date wasn't successful
         if (waitCycles > 255)
            waitCycles = 255;
      }

      ostringstream command;
      command << "WT " << axis_ << "=" << waitCycles;
      string answer;
      // query command
      int ret = QueryCommand(command.str().c_str(), answer);
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


int ZStage::OnBacklash(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // To simplify our life we only read out waitcycles for the X axis, but set for both
      ostringstream command;
      command << "B " << axis_ << "?";
      string answer;
      // query command
      int ret = QueryCommand(command.str().c_str(), answer);
      if (ret != DEVICE_OK)
         return ret;

      if (answer.substr(0,2).compare(":" + axis_) == 0)
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
      command << "B " << axis_ << "=" << backlash;
      string answer;
      // query command
      int ret = QueryCommand(command.str().c_str(), answer);
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

int ZStage::OnFinishError(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // To simplify our life we only read out waitcycles for the X axis, but set for both
      ostringstream command;
      command << "PC " << axis_ << "?";
      string answer;
      // query command
      int ret = QueryCommand(command.str().c_str(), answer);
      if (ret != DEVICE_OK)
         return ret;

      if (answer.substr(0,2).compare(":" + axis_) == 0)
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
      command << "PC " << axis_ << "=" << error;
      string answer;
      // query command
      int ret = QueryCommand(command.str().c_str(), answer);
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

int ZStage::OnAcceleration(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // To simplify our life we only read out acceleration for the X axis, but set for both
      ostringstream command;
      command << "AC " + axis_ + "?";
      string answer;
      // query command
      int ret = QueryCommand(command.str().c_str(), answer);
      if (ret != DEVICE_OK)
         return ret;

      if (answer.substr(0,2).compare(":" + axis_) == 0)
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
      double accel;
      pProp->Get(accel);
      if (accel < 0.0)
         accel = 0.0;
      ostringstream command;
      command << "AC " << axis_ << "=" << accel;
      string answer;
      // query command
      int ret = QueryCommand(command.str().c_str(), answer);
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

int ZStage::OnOverShoot(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // To simplify our life we only read out waitcycles for the X axis, but set for both
      ostringstream command;
      command << "OS " + axis_ + "?";
      string answer;
      // query command
      int ret = QueryCommand(command.str().c_str(), answer);
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
      command << fixed << "OS " << axis_ << "=" << overShoot;
      string answer;
      // query the device
      int ret = QueryCommand(command.str().c_str(), answer);
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

int ZStage::OnError(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // To simplify our life we only read out waitcycles for the X axis, but set for both
      ostringstream command;
      command << "E " + axis_ + "?";
      string answer;
      // query command
      int ret = QueryCommand(command.str().c_str(), answer);
      if (ret != DEVICE_OK)
         return ret;

      if (answer.substr(0,2).compare(":" + axis_) == 0)
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
      command << fixed << "E " << axis_ << "=" << error;
      string answer;
      // query the device
      int ret = QueryCommand(command.str().c_str(), answer);
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

int ZStage::GetMaxSpeed(char * maxSpeedStr)
{
   double origMaxSpeed = maxSpeed_;
   char orig_speed[MM::MaxStrLength];
   int ret = GetProperty("Speed-S", orig_speed);
   if ( ret != DEVICE_OK)
      return ret;
   maxSpeed_ = 10001;
   SetProperty("Speed-S", "10000");
   ret = GetProperty("Speed-S", maxSpeedStr);
   maxSpeed_ = origMaxSpeed;  // restore in case we return early
   if (ret != DEVICE_OK)
      return ret;
   ret = SetProperty("Speed-S", orig_speed);
   if (ret != DEVICE_OK)
      return ret;
   return DEVICE_OK;
}

int ZStage::OnSpeed(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // To simplify our life we only read out waitcycles for the X axis, but set for both
      ostringstream command;
      command << "S " + axis_ + "?";
      string answer;
      // query command
      int ret = QueryCommand(command.str().c_str(), answer);
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
      else if (speed > maxSpeed_)
         speed = maxSpeed_;
      ostringstream command;
      command << fixed << "S " << axis_ << "=" << speed;
      string answer;
      // query the device
      int ret = QueryCommand(command.str().c_str(), answer);
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

int ZStage::OnMotorCtrl(MM::PropertyBase* pProp, MM::ActionType eAct)
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
      command << "MC " << axis_ << "X" << value;
      string answer;
      // query command
      int ret = QueryCommand(command.str().c_str(), answer);
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

//////////////////////////////////////////////////////////////////////
// CRIF reflection-based autofocussing unit (Nico, May 2007)
////
//////////////////////////////////////////////////////////////////////
// CRIF reflection-based autofocussing unit (Nico, May 2007)
////
CRIF::CRIF() :
   ASIBase(this, "" /* LX-4000 Prefix Unknown */),
   justCalibrated_(false),
   axis_("Z"),
   stepSizeUm_(0.1),
   waitAfterLock_(3000)
{
   InitializeDefaultErrorMessages();

   SetErrorText(ERR_NOT_CALIBRATED, "CRIF is not calibrated.  Try focusing close to a coverslip and selecting 'Calibrate'");
   SetErrorText(ERR_UNRECOGNIZED_ANSWER, "The ASI controller said something incomprehensible");
   SetErrorText(ERR_NOT_LOCKED, "The CRIF failed to lock");

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
   core_ = GetCoreCallback();

   if (initialized_)
      return DEVICE_OK;

   // check status first (test for communication protocol)
   int ret = CheckDeviceStatus();
   if (ret != DEVICE_OK)
      return ret;

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
   AddAllowedValue(g_CRIFState, g_CRIF_O);

   pAct = new CPropertyAction(this, &CRIF::OnWaitAfterLock);
   CreateProperty("Wait ms after Lock", "3000", MM::Integer, false, pAct);

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

// TODO: See if this can be implemented for the CRIF
int CRIF::GetOffset(double& offset)
{
   offset = 0;
   return DEVICE_OK;
}

// TODO: See if this can be implemented for the CRIF
int CRIF::SetOffset(double /* offset */)
{
   return DEVICE_OK;
}

void CRIF::GetName(char* pszName) const
{
   CDeviceUtils::CopyLimitedString(pszName, g_CRIFDeviceName);
}

int CRIF::GetFocusState(std::string& focusState)
{
   // empty the Rx serial buffer before sending command
   ClearPort();

   const char* command = "LOCK X?"; // Requests single char lock state description
   string answer;
   // query command
   int ret = QueryCommand(command, answer);
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
         focusState = g_CRIF_k;
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

   if (focusState == g_CRIF_I || focusState == g_CRIF_O)
   {
      // Unlock and switch off laser:
      ret = SetContinuousFocusing(false);
      if (ret != DEVICE_OK)
         return ret;
   }

   /*
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
         // query command and wait for acknowledgement
         int ret = QueryCommandACK(command);
         if (ret != DEVICE_OK)
            return ret;
      }
      if (currentState == g_CRIF_L) // we need to advance the state once more.  Wait a bit for calibration to finish)
      {
         CDeviceUtils::SleepMs(1000); // ms
         const char* command = "LK Z";
         // query command and wait for acknowledgement
         int ret = QueryCommandACK(command);
         if (ret != DEVICE_OK)
            return ret;
      }
   }
   */

   else if (focusState == g_CRIF_L)
   {
      if ( (currentState == g_CRIF_I) || currentState == g_CRIF_O)
      {
         const char* command = "LK Z";
         // query command and wait for acknowledgement
         ret = QueryCommandACK(command);
         if (ret != DEVICE_OK)
            return ret;
      }
   }

   else if (focusState == g_CRIF_Cal) 
   {
      const char* command = "LK Z";
      if (currentState == g_CRIF_B || currentState == g_CRIF_O)
      {
         // query command and wait for acknowledgement
         ret = QueryCommandACK(command);
         if (ret != DEVICE_OK)
            return ret;
         ret = GetFocusState(currentState);
         if (ret != DEVICE_OK)
            return ret;
      }  
      if (currentState == g_CRIF_I) // Idle, first switch on laser
      {
         // query command and wait for acknowledgement
         ret = QueryCommandACK(command);
         if (ret != DEVICE_OK)
            return ret;
         ret = GetFocusState(currentState);
         if (ret != DEVICE_OK)
            return ret;
      }
      if (currentState == g_CRIF_L)
      {
         // query command and wait for acknowledgement
         ret = QueryCommandACK(command);
         if (ret != DEVICE_OK)
            return ret;
      }

      // now wait for the lock to occur
      MM::MMTime startTime = GetCurrentMMTime();
      MM::MMTime wait(3,0);
      bool cont = false;
      std::string finalState;
      do {
         CDeviceUtils::SleepMs(250);
         GetFocusState(finalState);
         cont = (startTime - GetCurrentMMTime()) < wait;
      } while ( finalState != g_CRIF_G && finalState != g_CRIF_B && cont);

      justCalibrated_ = true; // we need this to know whether this is the first time we lock
   }

   else if ( (focusState == g_CRIF_K) || (focusState == g_CRIF_k) )
   {
      // only try a lock when we are good
      if ( (currentState == g_CRIF_G) || (currentState == g_CRIF_O) ) 
      {
         ret = SetContinuousFocusing(true);
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
   std::string focusState;
   int ret = GetFocusState(focusState);
   if (ret != DEVICE_OK)
      return false;

   if (focusState == g_CRIF_K)
      return true;

   return false;
}


int CRIF::SetContinuousFocusing(bool state)
{
   // empty the Rx serial buffer before sending command
   ClearPort();

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
   string answer;
   // query command
   int ret = QueryCommand(command.c_str(), answer);
   if (ret != DEVICE_OK)
      return ret;

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

int CRIF::FullFocus()
{
   double pos;
   int ret = GetPositionUm(pos);
   if (ret != DEVICE_OK)
      return ret;
   ret = SetContinuousFocusing(true);
   if (ret != DEVICE_OK)
      return ret;

   MM::MMTime startTime = GetCurrentMMTime();
   MM::MMTime wait(3, 0);
   while (!IsContinuousFocusLocked() && ( (GetCurrentMMTime() - startTime) < wait) ) {
      CDeviceUtils::SleepMs(25);
   }

   CDeviceUtils::SleepMs(waitAfterLock_);

   if (!IsContinuousFocusLocked()) {
      SetContinuousFocusing(false);
      SetPositionUm(pos);
      return ERR_NOT_LOCKED;
   }

   return SetContinuousFocusing(false);
}

int CRIF::IncrementalFocus()
{
   return FullFocus();
}

int CRIF::GetLastFocusScore(double& score)
{
   // empty the Rx serial buffer before sending command
   ClearPort();

   score = 0;
   const char* command = "LOCK Y?"; // Requests present value of the PSD signal as shown on LCD panel
   string answer;
   // query command
   int ret = QueryCommand(command, answer);
   if (ret != DEVICE_OK)
      return ret;

   score = atof (answer.substr(2).c_str());
   if (score == 0)
      return ERR_UNRECOGNIZED_ANSWER;

   return DEVICE_OK;
}

int CRIF::SetPositionUm(double pos)
{
   // empty the Rx serial buffer before sending command
   ClearPort();

   ostringstream command;
   command << fixed << "M " << axis_ << "=" << pos / stepSizeUm_; // in 10th of micros

   string answer;
   // query the device
   int ret = QueryCommand(command.str().c_str(), answer);
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

int CRIF::GetPositionUm(double& pos)
{
   // empty the Rx serial buffer before sending command
   ClearPort();

   ostringstream command;
   command << "W " << axis_;

   string answer;
   // query command
   int ret = QueryCommand(command.str().c_str(), answer);
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

      return DEVICE_OK;
   }

   return ERR_UNRECOGNIZED_ANSWER;
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

int CRIF::OnWaitAfterLock(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(waitAfterLock_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(waitAfterLock_);
   }

   return DEVICE_OK;
}


//////////////////////////////////////////////////////////////////////
// CRISP reflection-based autofocussing unit (Nico, Nov 2011)
////
CRISP::CRISP() :
   ASIBase(this, "" /* LX-4000 Prefix Unknown */),
   axis_("Z"),
   ledIntensity_(50),
   na_(0.65),
   waitAfterLock_(1000),
   answerTimeoutMs_(1000),
   compileDay_(0)
{
   InitializeDefaultErrorMessages();

   SetErrorText(ERR_NOT_CALIBRATED, "CRISP is not calibrated.  Try focusing close to a coverslip and selecting 'Calibrate'");
   SetErrorText(ERR_UNRECOGNIZED_ANSWER, "The ASI controller said something incomprehensible");
   SetErrorText(ERR_NOT_LOCKED, "The CRISP failed to lock");

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, g_CRISPDeviceName, MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "ASI CRISP Autofocus adapter", MM::String, true);

   // Port
   CPropertyAction* pAct = new CPropertyAction (this, &CRISP::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

   // Axis
   pAct = new CPropertyAction (this, &CRISP::OnAxis);
   CreateProperty("Axis", "Z", MM::String, false, pAct, true);
   AddAllowedValue("Axis", "Z");
   AddAllowedValue("Axis", "P");
   AddAllowedValue("Axis", "F");

}

CRISP::~CRISP()
{
   initialized_ = false;
}

bool CRISP::SupportsDeviceDetection(void)
{
   return true;
}

MM::DeviceDetectionStatus CRISP::DetectDevice(void)
{
   return ASICheckSerialPort(*this,*GetCoreCallback(), port_, answerTimeoutMs_);
}

int CRISP::Initialize()
{
   core_ = GetCoreCallback();

   if (initialized_)
      return DEVICE_OK;

   // check status first (test for communication protocol)
   int ret = CheckDeviceStatus();
   if (ret != DEVICE_OK)
      return ret;

   CPropertyAction* pAct = new CPropertyAction(this, &CRISP::OnFocus);
   CreateProperty (g_CRISPState, "Undefined", MM::String, false, pAct);

   // Add values (TODO: check manual)
   AddAllowedValue(g_CRISPState, g_CRISP_I);
   AddAllowedValue(g_CRISPState, g_CRISP_R);
   AddAllowedValue(g_CRISPState, g_CRISP_D);
   AddAllowedValue(g_CRISPState, g_CRISP_K);
   AddAllowedValue(g_CRISPState, g_CRISP_F);
   AddAllowedValue(g_CRISPState, g_CRISP_N);
   AddAllowedValue(g_CRISPState, g_CRISP_E);
   AddAllowedValue(g_CRISPState, g_CRISP_G);
   AddAllowedValue(g_CRISPState, g_CRISP_f);
   AddAllowedValue(g_CRISPState, g_CRISP_C);
   AddAllowedValue(g_CRISPState, g_CRISP_B);
   AddAllowedValue(g_CRISPState, g_CRISP_SG);
   AddAllowedValue(g_CRISPState, g_CRISP_RFO);
   AddAllowedValue(g_CRISPState, g_CRISP_S);

   pAct = new CPropertyAction (this, &CRISP::OnCompileDate);
   CreateProperty("CompileDate", "", MM::String, true, pAct);
   UpdateProperty("CompileDate");

   // get the date of the firmware
   char compile_date[MM::MaxStrLength];
   if (GetProperty("CompileDate", compile_date) == DEVICE_OK)
      compileDay_ = ExtractCompileDay(compile_date);

   pAct = new CPropertyAction(this, &CRISP::OnWaitAfterLock);
   CreateProperty("Wait ms after Lock", "3000", MM::Integer, false, pAct);

   pAct = new CPropertyAction(this, &CRISP::OnNA);
   CreateProperty("Objective NA", "0.8", MM::Float, false, pAct);
   SetPropertyLimits("Objective NA", 0, 1.65);

   pAct = new CPropertyAction(this, &CRISP::OnLockRange);
   CreateProperty("Max Lock Range(mm)", "0.05", MM::Float, false, pAct);

   pAct = new CPropertyAction(this, &CRISP::OnCalGain);
   CreateProperty("Calibration Gain", "0.05", MM::Integer, false, pAct);

   pAct = new CPropertyAction(this, &CRISP::OnLEDIntensity);
   CreateProperty("LED Intensity", "50", MM::Integer, false, pAct);
   SetPropertyLimits("LED Intensity", 0, 100);

   pAct = new CPropertyAction(this, &CRISP::OnGainMultiplier);
   CreateProperty("GainMultiplier", "10", MM::Integer, false, pAct);
   SetPropertyLimits("GainMultiplier", 1, 100);

   pAct = new CPropertyAction(this, &CRISP::OnNumAvg);
   CreateProperty("Number of Averages", "1", MM::Integer, false, pAct);
   SetPropertyLimits("Number of Averages", 0, 10);

   pAct = new CPropertyAction(this, &CRISP::OnOffset);
   CreateProperty(g_CRISPOffsetPropertyName, "", MM::Integer, true, pAct);
   UpdateProperty(g_CRISPOffsetPropertyName);

   pAct = new CPropertyAction(this, &CRISP::OnSum);
   CreateProperty(g_CRISPSumPropertyName, "", MM::Integer, true, pAct);
   UpdateProperty(g_CRISPSumPropertyName);

   // not sure exactly when Gary made these firmware changes, but they were there by start of 2015
   if (compileDay_ >= ConvertDay(2015, 1, 1))
   {
      pAct = new CPropertyAction(this, &CRISP::OnNumSkips);
      CreateProperty("Number of Skips", "0", MM::Integer, false, pAct);
      SetPropertyLimits("Number of Skips", 0, 100);
      UpdateProperty("Number of Skips");

      pAct = new CPropertyAction(this, &CRISP::OnInFocusRange);
      CreateProperty("In Focus Range(um)", "0.1", MM::Float, false, pAct);
      UpdateProperty("In Focus Range(um)");
   }

   const char* fc = "Obtain Focus Curve";
   pAct = new CPropertyAction(this, &CRISP::OnFocusCurve);
   CreateProperty(fc, " ", MM::String, false, pAct);
   AddAllowedValue(fc, " ");
   AddAllowedValue(fc, "Do it");

   for (long i = 0; i < SIZE_OF_FC_ARRAY; i++)
   {
      std::ostringstream os("");
      os << "Focus Curve Data" << i;
      CPropertyActionEx* pActEx = new CPropertyActionEx(this, &CRISP::OnFocusCurveData, i);
      CreateProperty(os.str().c_str(), "", MM::String, true, pActEx);
   }

   pAct = new CPropertyAction(this, &CRISP::OnSNR);
   CreateProperty("Signal Noise Ratio", "", MM::Float, true, pAct);

   pAct = new CPropertyAction(this, &CRISP::OnDitherError);
   CreateProperty("Dither Error", "", MM::Integer, true, pAct);

   pAct = new CPropertyAction(this, &CRISP::OnLogAmpAGC);
   CreateProperty("LogAmpAGC", "", MM::Integer, true, pAct);


   // Values that only we can change should be cached and enquired here:

   float val;
   ret = GetValue("LR Y?", val);
   if (ret != DEVICE_OK)
      return ret;
   na_ = (double) val;

   sum_=0;
   return DEVICE_OK;
}

int CRISP::Shutdown()
{
   initialized_ = false;
   return DEVICE_OK;
}

bool CRISP::Busy()
{
   //TODO implement
   return false;
}

/**
 * Note that offset is not in um but arbitrary (integer) numbers
 */
int CRISP::GetOffset(double& offset)
{
   float val;
   int ret = GetValue("LK Z?", val);
   if (ret != DEVICE_OK)
      return ret;

   int v = (int) val;

   offset = (double) v;

   return DEVICE_OK;
}

/**
 * Note that offset is not in um but arbitrary (integer) numbers
 */
int CRISP::SetOffset(double  offset)
{
   std::ostringstream os;
   os << "LK Z=" << fixed << (int) offset;
   return SetCommand(os.str().c_str());
}

void CRISP::GetName(char* pszName) const
{
   CDeviceUtils::CopyLimitedString(pszName, g_CRISPDeviceName);
}

int CRISP::GetFocusState(std::string& focusState)
{
   // empty the Rx serial buffer before sending command
   ClearPort();

   const char* command = "LK X?"; // Requests single char lock state description
   string answer;
   // query command
   int ret = QueryCommand(command, answer);
   if (ret != DEVICE_OK)
      return ERR_UNRECOGNIZED_ANSWER;

   // translate response to one of our globals (see CRISP manual)
   char test = answer.c_str()[3];
   switch (test) {
      case 'I': focusState = g_CRISP_I; break;
      case 'R': focusState = g_CRISP_R; break;
      case '1': 
      case '2': 
      case '3': 
      case '4': 
      case '5': 
      case 'g':
      case 'h':
      case 'i':
      case 'j': focusState = g_CRISP_Cal; break;
      case 'D': focusState = g_CRISP_D; break;
      case 'K': focusState = g_CRISP_K; break;
      case 'F': focusState = g_CRISP_F; break;
      case 'N': focusState = g_CRISP_N; break;
      case 'E': focusState = g_CRISP_E; break;
      // TODO: Sometimes the controller spits out extra information when the state is 'G'
      // Figure out what that information is, and how to handle it best.  At the moment
      // it causes problems since it will be read by the next command!
      case 'G': focusState = g_CRISP_G; break;
      case 'f': focusState = g_CRISP_f; break;
      case 'C': focusState = g_CRISP_C; break;
      case 'B': focusState = g_CRISP_B; break;
      case 'l': focusState = g_CRISP_RFO; break;
      default: return ERR_UNRECOGNIZED_ANSWER;
   }
   return DEVICE_OK;
}

int CRISP::SetFocusState(std::string focusState)
{
	std::string currentState;
	int ret = GetFocusState(currentState);
	if (ret != DEVICE_OK)
		return ret;

	if (focusState == currentState)
		return DEVICE_OK;

	return ForceSetFocusState(focusState);
}

int CRISP::ForceSetFocusState(std::string focusState)
{
   std::string currentState;
   int ret = GetFocusState(currentState);
   if (ret != DEVICE_OK)
      return ret;

   if (focusState == currentState)
      return DEVICE_OK;

   if (focusState == g_CRISP_I)
   {
      // Idle (switch off LED)
      const char* command = "LK F=79";
      return SetCommand(command);
   }

   else if (focusState == g_CRISP_R )
   {
      // Unlock
   	const char* command = "LK F=85";
      return SetCommand(command);
   }

   else if (focusState == g_CRISP_K)
   {
      // Lock
      const char* command = "LK F=83";
      return SetCommand(command);
   }

   else if (focusState == g_CRISP_G) 
   {
      // Log-Amp Calibration
      const char* command = "LK F=72"; 
      return SetCommand(command);
   }

   else if (focusState == g_CRISP_SG) 
   {
      // gain_Cal Calibration
      const char* command = "LK F=67"; 
      return SetCommand(command);
   }

   else if (focusState == g_CRISP_f)
   {
      // Dither
      const char* command = "LK F=102";
      return SetCommand(command);
   }

   else if (focusState == g_CRISP_RFO)
   {
      // Reset focus offset
      const char* command = "LK F=111";
      return SetCommand(command);
   }

   else if (focusState == g_CRISP_S)
   {
      // Reset focus offset
      const char* command = "SS Z";
      return SetCommand(command);
   }
   
   return DEVICE_OK;
}

bool CRISP::IsContinuousFocusLocked()
{
   std::string focusState;
   int ret = GetFocusState(focusState);
   if (ret != DEVICE_OK)
      return false;
   return (focusState == g_CRISP_K);
}


int CRISP::SetContinuousFocusing(bool state)
{
   bool focusingOn;
   int ret = GetContinuousFocusing(focusingOn);
   if (ret != DEVICE_OK)
   	return ret;
   if (focusingOn && !state) {
   	// was on, turning off
   	return ForceSetFocusState(g_CRISP_R);
   } else if (!focusingOn && state) {
   	// was off, turning on
   	if (focusState_ == g_CRISP_R ) {
   		return ForceSetFocusState(g_CRISP_K);
   	} else {  // need to move to ready state, then turn on
   		ret = ForceSetFocusState(g_CRISP_R);
   		if (ret != DEVICE_OK)
   			return ret;
   		return ForceSetFocusState(g_CRISP_K);
   	}
   }
   // if was already in state requested we don't need to do anything
   return DEVICE_OK;
}


int CRISP::GetContinuousFocusing(bool& state)
{
   std::string focusState;
   int ret = GetFocusState(focusState);
   if (ret != DEVICE_OK)
      return ret;

   state = ((focusState == g_CRISP_K) || (focusState == g_CRISP_F));
   
   return DEVICE_OK;
}

/**
 * Does a "one-shot" autofocus: locks and then unlocks again
 */
int CRISP::FullFocus()
{
   int ret = SetContinuousFocusing(true);
   if (ret != DEVICE_OK)
      return ret;

   MM::MMTime startTime = GetCurrentMMTime();
   MM::MMTime wait(0, waitAfterLock_ * 1000);
   while (!IsContinuousFocusLocked() && ( (GetCurrentMMTime() - startTime) < wait) ) {
      CDeviceUtils::SleepMs(25);
   }

   CDeviceUtils::SleepMs(waitAfterLock_);

   if (!IsContinuousFocusLocked()) {
      SetContinuousFocusing(false);
      return ERR_NOT_LOCKED;
   }

   return SetContinuousFocusing(false);
}

int CRISP::IncrementalFocus()
{
   return FullFocus();
}

int CRISP::GetLastFocusScore(double& score)
{
   // empty the Rx serial buffer before sending command
   ClearPort();

   score = 0;
   const char* command = "LK Y?"; // Requests present value of the focus error as shown on LCD panel
   string answer;
   // query command
   int ret = QueryCommand(command, answer);
   if (ret != DEVICE_OK)
      return ret;

   score = atof (answer.substr(2).c_str());
   if (score == 0)
      return ERR_UNRECOGNIZED_ANSWER;

   return DEVICE_OK;
}

int CRISP::GetCurrentFocusScore(double& score)
{
   return GetLastFocusScore(score);
}

int CRISP::GetValue(string cmd, float& val)
{
   string answer;
   // query command
   int ret = QueryCommand(cmd.c_str(), answer);
   if (ret != DEVICE_OK)
      return ret;

   if (answer.length() > 2 && answer.substr(0, 2).compare(":N") == 0)
   {
      int errNo = atoi(answer.substr(2).c_str());
      return ERR_OFFSET + errNo;
   }
   else if (answer.length() > 0)
   {
      size_t index = 0;
      while (!isdigit(answer[index]) && (index < answer.length()))
         index++;
      if (index >= answer.length())
         return ERR_UNRECOGNIZED_ANSWER;
      val = (float) atof( (answer.substr(index)).c_str());

      return DEVICE_OK;
   }

   return ERR_UNRECOGNIZED_ANSWER;
}


int CRISP::SetCommand(std::string cmd)
{
   string answer;
   // query command
   int ret = QueryCommand(cmd.c_str(), answer);
   if (ret != DEVICE_OK)
      return ret;

   if (answer.length() > 2 && answer.substr(0, 2).compare(":N") == 0)
   {
      int errNo = atoi(answer.substr(2).c_str());
      return ERR_OFFSET + errNo;
   }

   if (answer.substr(0,2) == ":A") {
      return DEVICE_OK;
   }
 
   return ERR_UNRECOGNIZED_ANSWER;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int CRISP::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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

// Get the compile date of this controller
int CRISP::OnCompileDate(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      if (initialized_)
         return DEVICE_OK;

      ostringstream command;
      command << "CD";
      string answer;
      // query the device
      int ret = QueryCommand(command.str().c_str(), answer);
      if (ret != DEVICE_OK)
         return ret;

      pProp->Set(answer.c_str());

   }
   return DEVICE_OK;
}

int CRISP::OnFocus(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int CRISP::OnWaitAfterLock(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(waitAfterLock_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(waitAfterLock_);
   }

   return DEVICE_OK;
}

int CRISP::OnNA(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(na_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(na_);
      ostringstream command;
      command << fixed << "LR Y=" << na_;
      return SetCommand(command.str().c_str());
   }

   return DEVICE_OK;
}

int CRISP::OnCalGain(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      float calGain;
      int ret = GetValue("LR X?", calGain);
      if (ret != DEVICE_OK)
         return ret;

      pProp->Set(calGain);
   }
   else if (eAct == MM::AfterSet)
   {
      double lr;
      pProp->Get(lr);
      ostringstream command;
      command << fixed << "LR X=" << (int) lr;

      return SetCommand(command.str());
   }

   return DEVICE_OK;
}
int CRISP::OnLockRange(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      float lockRange;
      int ret = GetValue("LR Z?", lockRange);
      if (ret != DEVICE_OK)
         return ret;

      pProp->Set(lockRange);
   }
   else if (eAct == MM::AfterSet)
   {
      double lr;
      pProp->Get(lr);
      ostringstream command;
      command << fixed << "LR Z=" << lr;

      return SetCommand(command.str());
   }

   return DEVICE_OK;
}

int CRISP::OnNumAvg(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      float numAvg;
      int ret = GetValue("RT F?", numAvg);
      if (ret != DEVICE_OK)
         return ret;

      pProp->Set(numAvg);
   }
   else if (eAct == MM::AfterSet)
   {
      long nr;
      pProp->Get(nr);
      ostringstream command;
      command << fixed << "RT F=" << nr;

      return SetCommand(command.str());
   }

   return DEVICE_OK;
}

int CRISP::OnGainMultiplier(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      float gainMultiplier;
      std::string command = "KA " + axis_ + "?";
      int ret = GetValue(command.c_str(), gainMultiplier);
      if (ret != DEVICE_OK)
         return ret;

      pProp->Set(gainMultiplier);
   }
   else if (eAct == MM::AfterSet)
   {
      long nr;
      pProp->Get(nr);
      ostringstream command;
      command << fixed << "KA " << axis_ << "=" << nr;

      return SetCommand(command.str());
   }

   return DEVICE_OK;
}

int CRISP::OnLEDIntensity(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(ledIntensity_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(ledIntensity_);
   }

   return DEVICE_OK;
}

int CRISP::OnFocusCurve(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(" ");
   } 
   else if (eAct == MM::AfterSet)
   {
      std::string val;
      pProp->Get(val);
      if (val == "Do it")
      {
         std::string answer;
         int ret = QueryCommand("LK F=97", answer);
         if (ret != DEVICE_OK)
            return ret;

         // We will time out while getting these data, so do not throw an error
         // Also, the total length will be about 3500 chars, since MM::MaxStrlength is 1024, we
         // need at least 4 strings.
         int index = 0;
         focusCurveData_[index] = "";
         bool done = false;
         // the GetSerialAnswer call will likely take more than 500ms, the likely timeout for the port set by the user
         // instead, wait for a total of ??? seconds
         MM::MMTime startTime = GetCurrentMMTime();
         MM::MMTime wait(10,0);
         bool cont = true;
         while (cont && !done && index < SIZE_OF_FC_ARRAY)
         {
            ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
            if (answer == "end")
               done = true;
            else
            {
               focusCurveData_[index] += answer + "\r\n";
               if (focusCurveData_[index].length() > (MM::MaxStrLength - 40))
               {
                  index++;
                  if (index < SIZE_OF_FC_ARRAY)
                     focusCurveData_[index] = "";
               }
            }
            
            cont = (GetCurrentMMTime() - startTime) < wait;
         }
      }
     
   }
   return DEVICE_OK;
}

int CRISP::OnFocusCurveData(MM::PropertyBase* pProp, MM::ActionType eAct, long index)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(focusCurveData_[index].c_str());
   }
   return DEVICE_OK;
}

int CRISP::OnAxis(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int CRISP::OnSNR(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // HACK: there are still occasionally intervening messages from the controller
      ClearPort();

      std::string command = "EXTRA Y?";
      std::string answer;
      int ret = QueryCommand(command.c_str(), answer);
      if (ret != DEVICE_OK)
         return ret;

      std::stringstream ss(answer);
      double snr;
      ss >> snr;

      pProp->Set(snr);
   }

   return DEVICE_OK;
}

int CRISP::OnDitherError(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      std::string answer;
      int ret = QueryCommand("EXTRA X?", answer);
      if (ret != DEVICE_OK)
         return ret;

     // long val;
      std::istringstream is(answer);
      std::string tok,tok2;
      for (int i=0; i <3; i++)
      {   
		  if(i==1)
		  {
		  is>>tok2; //2nd "is" is sum 
		  }
		  is >> tok; //3rd "is" is error

	  }

     // std::istringstream s(tok);
      //s >> val;
      //pProp->Set(val);

	  pProp->Set(tok.c_str());

	  //std::istringstream s2(tok2);
	 // s2 >> val;
	  //sum_= val;

	  sum_=atol(tok2.c_str());

   }
   return DEVICE_OK;
}

int CRISP::OnLogAmpAGC(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      float val;
      int ret = GetValue("AFLIM X?", val);
      if (ret != DEVICE_OK)
         return ret;
      pProp->Set(val);
   }
   return DEVICE_OK;
}

int CRISP::OnNumSkips(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      float numSkips;
      int ret = GetValue("UL Y?", numSkips);
      if (ret != DEVICE_OK)
         return ret;

      pProp->Set(numSkips);
   }
   else if (eAct == MM::AfterSet)
   {
      long nr;
      pProp->Get(nr);
      ostringstream command;
      command << fixed << "UL Y=" << nr;

      return SetCommand(command.str());
   }

   return DEVICE_OK;
}

int CRISP::OnInFocusRange(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      float focusRange;
      int ret = GetValue("AFLIM Z?", focusRange);
      if (ret != DEVICE_OK)
         return ret;

      pProp->Set(focusRange*1000);
   }
   else if (eAct == MM::AfterSet)
   {
      double lr;
      pProp->Get(lr);
      ostringstream command;
      command << fixed << "AFLIM Z=" << lr/1000;

      return SetCommand(command.str());
   }

   return DEVICE_OK;
}

   
int CRISP::OnSum(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  if (eAct == MM::BeforeGet)
   {
     /*  std::string answer;
      int ret = QueryCommand("EXTRA X?", answer);
      if (ret != DEVICE_OK)
         return ret;

      long val;
      std::istringstream is(answer);
      std::string tok;
      for (int i=0; i <2; i++) //SUM is 2nd last number
         is >> tok;
      std::istringstream s(tok);
      s >> val;
      pProp->Set(val); */
	// more efficient way, sum is retrived same time as dither error
	   pProp->Set((long)sum_);


   }
   return DEVICE_OK;
}

int CRISP::OnOffset(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      double numSkips;
      //int ret = GetValue("LK Z?", numSkips);
      
	  int ret= GetOffset(numSkips);
	  if (ret != DEVICE_OK)
         return ret; 
   
	  if (!pProp->Set(numSkips))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }

   return DEVICE_OK;
}




/***************************************************************************
 *  AZ100 adapter 
 */
AZ100Turret::AZ100Turret() :
   ASIBase(this, "" /* LX-4000 Prefix Unknown */),
   numPos_(4),
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
   core_ = GetCoreCallback();

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
   ClearPort();

   const char* command = "RS F";
   string answer;
   // query command
   int ret = QueryCommand(command, answer);
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

      string answer;
      // query command
      int ret = QueryCommand(os.str().c_str(), answer);
      if (ret != DEVICE_OK)
         return ret;

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

StateDevice::StateDevice() :
   ASIBase(this, "" /* LX-4000 Prefix Unknown */),
   numPos_(4),
   axis_("F"),
   position_(0),
   answerTimeoutMs_(1000)
{
   InitializeDefaultErrorMessages();

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, g_StateDeviceName, MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "ASI State Device", MM::String, true);

   // Port
   CPropertyAction* pAct = new CPropertyAction (this, &StateDevice::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

   // number of positions needs to be specified beforehand (later firmware allows querying)
   pAct = new CPropertyAction (this, &StateDevice::OnNumPositions);
   CreateProperty("NumPositions", "4", MM::Integer, false, pAct, true);

   // Axis
   pAct = new CPropertyAction (this, &StateDevice::OnAxis);
   CreateProperty("Axis", "F", MM::String, false, pAct, true);
   AddAllowedValue("Axis", "F");
   AddAllowedValue("Axis", "T");
   AddAllowedValue("Axis", "Z");

}

StateDevice::~StateDevice()
{
   Shutdown();
}

bool StateDevice::SupportsDeviceDetection(void)
{
   return true;
}

MM::DeviceDetectionStatus StateDevice::DetectDevice(void)
{
   return ASICheckSerialPort(*this,*GetCoreCallback(), port_, answerTimeoutMs_);
}

void StateDevice::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_StateDeviceName);
}

int StateDevice::Initialize()
{
   core_ = GetCoreCallback();

   // state
   CPropertyAction* pAct = new CPropertyAction (this, &StateDevice::OnState);
   int ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
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
   ret = CreateProperty(MM::g_Keyword_Label, "", MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   char state[11];
   for (int i=0; i<numPos_; i++)
   {
      sprintf(state, "Position-%d", i);
      SetPositionLabel(i,state);
   }

   // get current position
   ret = UpdateCurrentPosition();  // updates position_
   if (ret != DEVICE_OK)
      return ret;

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;
   return DEVICE_OK;
}

int StateDevice::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

bool StateDevice::Busy()
{
   // empty the Rx serial buffer before sending command
   ClearPort();

   const char* command = "/";
   string answer;
   // query command
   int ret = QueryCommand(command, answer);
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

int StateDevice::OnNumPositions(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(numPos_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(numPos_);
   }

   return DEVICE_OK;
}

int StateDevice::OnAxis(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int StateDevice::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int StateDevice::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
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
      os << "M " << axis_ << "=" << position + 1;

      string answer;
      // query command
      int ret = QueryCommand(os.str().c_str(), answer);
      if (ret != DEVICE_OK)
         return ret;

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

int StateDevice::UpdateCurrentPosition()
{
   // find out what position we are currently in
   ostringstream os;
   os << "W " << axis_;

   string answer;
   // query command
   int ret = QueryCommand(os.str().c_str(), answer);
   if (ret != DEVICE_OK)
      return ret;

   if (answer.substr(0, 2).compare(":N") == 0 && answer.length() > 2)
   {
      int errNo = atoi(answer.substr(2,4).c_str());
      return ERR_OFFSET + errNo;
   }

   if (answer.substr(0,2) == ":A")
   {
      position_ = (long) atoi(answer.substr(3,2).c_str()) - 1;
   }
   else
   {
      return ERR_UNRECOGNIZED_ANSWER;
   }

   return DEVICE_OK;
}


LED::LED() :
   ASIBase(this, "" /* LX-4000 Prefix Unknown */),
   open_(false),
   intensity_(1),
   name_("LED"),
   answerTimeoutMs_(1000)
{
   InitializeDefaultErrorMessages();

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, g_LEDName, MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "ASI LED controller", MM::String, true);

   // Port
   CPropertyAction* pAct = new CPropertyAction (this, &LED::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);
}

void LED::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_LEDName);
}


bool LED::SupportsDeviceDetection(void)
{
   return true;
}

MM::DeviceDetectionStatus LED::DetectDevice(void)
{
   return ASICheckSerialPort(*this,*GetCoreCallback(), port_, answerTimeoutMs_);
}

LED::~LED()
{
   Shutdown();
}

int LED::Initialize()
{
   core_ = GetCoreCallback();

   // empty the Rx serial buffer before sending command
   ClearPort();

   // check status first (test for communication protocol)
   int ret = CheckDeviceStatus();
   if (ret != DEVICE_OK)
       return ret;

   CPropertyAction* pAct = new CPropertyAction (this, &LED::OnState);
   CreateProperty(MM::g_Keyword_State, g_Closed, MM::String, false, pAct);
   AddAllowedValue(MM::g_Keyword_State, g_Closed);
   AddAllowedValue(MM::g_Keyword_State, g_Open);

   pAct = new CPropertyAction(this, &LED::OnIntensity);
   CreateProperty("Intensity", "1", MM::Integer, false, pAct);
   SetPropertyLimits("Intensity", 1, 100);

   ret = IsOpen(&open_);
   if (ret != DEVICE_OK)
      return ret;

   ret = CurrentIntensity(&intensity_);
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;
   return DEVICE_OK;
}

int LED::Shutdown() 
{
   initialized_ = false;
   return DEVICE_OK;
}

bool LED::Busy()
{
   // The LED should be a whole lot faster than our serial communication
   // so always respond false
   return false;
} 

   // Shutter API                                                                     
// All communication with the LED takes place in this function
int LED::SetOpen (bool open)
{
   // empty the Rx serial buffer before sending command
   ClearPort();

   ostringstream command;
   if (open)
   {
      if (intensity_ == 100)
         command << "TTL Y=1";
      else
         command << fixed << "TTL Y=9 " << intensity_;
   } else
   {
      command << "TTL Y=0";
   }

   string answer;
   // query the device
   int ret = QueryCommand(command.str().c_str(), answer);
   if (ret != DEVICE_OK)
      return ret;

   if ( (answer.substr(0,2).compare(":A") == 0) || (answer.substr(1,2).compare(":A") == 0) )
   {
      open_ = open;
      return DEVICE_OK;
   }
   // deal with error later
   else if (answer.substr(0, 2).compare(":N") == 0 && answer.length() > 2)
   {
      int errNo = atoi(answer.substr(4).c_str());
      return ERR_OFFSET + errNo;
   }

   open_ = open;
   return DEVICE_OK;
}

/**
 * GetOpen returns a cached value.  If ASI ever gives another control to the TTL out
 * other than the serial interface, this will need to be changed to a call to IsOpen
 */
int LED::GetOpen(bool& open)
{
   open = open_;

   return DEVICE_OK;
}

/**
 * IsOpen queries the microscope for the state of the TTL
 */
int LED::IsOpen(bool *open)
{
   *open = true;

   // empty the Rx serial buffer before sending command
   ClearPort();

   ostringstream command;
   command << "TTL Y?";

   string answer;
   // query the device
   int ret = QueryCommand(command.str().c_str(), answer);
   if (ret != DEVICE_OK)
      return ret;

   std::istringstream is(answer);
   std::string tok;
   is >> tok;
   if ( (tok.substr(0,2).compare(":A") == 0) || (tok.substr(1,2).compare(":A") == 0) ) {
      is >> tok;
      if (tok.substr(2,1) == "0")
         *open = false;
   }
   else if (tok.substr(0, 2).compare(":N") == 0 && tok.length() > 2)
   {
      int errNo = atoi(tok.substr(4).c_str());
      return ERR_OFFSET + errNo;
   }
   return DEVICE_OK;
}

/**
 * IsOpen queries the microscope for the state of the TTL
 */
int LED::CurrentIntensity(long* intensity)
{
   *intensity = 1;

   // empty the Rx serial buffer before sending command
   ClearPort();

   ostringstream command;
   command << "LED X?";

   string answer;
   // query the device
   int ret = QueryCommand(command.str().c_str(), answer);
   if (ret != DEVICE_OK)
      return ret;

   std::istringstream is(answer);
   std::string tok;
   std::string tok2;
   is >> tok;
   is >> tok2;
   if ( (tok2.substr(0,2).compare(":A") == 0) || (tok2.substr(1,2).compare(":A") == 0) ) {
      *intensity = atoi(tok.substr(2).c_str());
   }
   else if (tok.substr(0, 2).compare(":N") == 0 && tok.length() > 2)
   {
      int errNo = atoi(tok.substr(4).c_str());
      return ERR_OFFSET + errNo;
   }
   return DEVICE_OK;
}

int LED::Fire(double )
{
   return DEVICE_OK;
}

// action interface

int LED::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      std::string state = g_Open;
      if (!open_)
         state = g_Closed;
      pProp->Set(state.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      bool open = true;
      std::string state;
      pProp->Get(state);
      if (state == g_Closed)
         open = false;
      return SetOpen(open);
   }

   return DEVICE_OK;
}

int LED::OnIntensity(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(intensity_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(intensity_);
      // We could check that 0 < intensity_ < 101, but the system shoudl guarantee that
      if (intensity_ < 100)
      {
         ClearPort();

         ostringstream command;
         command << "LED X=";
         command << intensity_;

         string answer;
         // query the device
         int ret = QueryCommand(command.str().c_str(), answer);
         if (ret != DEVICE_OK)
            return ret;

         std::istringstream is(answer);
         std::string tok;
         is >> tok;
         if (tok.substr(0, 2).compare(":N") == 0 && tok.length() > 2)
         {
            int errNo = atoi(tok.substr(4).c_str());
            return ERR_OFFSET + errNo;
         }

      }
      if (open_)
         return SetOpen(open_);
   }

   return DEVICE_OK;
}

int LED::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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
