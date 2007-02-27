///////////////////////////////////////////////////////////////////////////////
// FILE:          Prior.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Prior ProScan controller adapter
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
// CVS:           $Id$
//

#ifdef WIN32
   #include <windows.h>
   #define snprintf _snprintf 
#endif

#include "prior.h"
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>

const char* g_XYStageDeviceName = "XYStage";
const char* g_ZStageDeviceName = "ZStage";
const char* g_BasicControllerName = "BasicController";
const char* g_Shutter1Name="Shutter-1";
const char* g_Shutter2Name="Shutter-2";
const char* g_Shutter3Name="Shutter-3";
const char* g_Wheel1Name = "Wheel-1";
const char* g_Wheel2Name = "Wheel-2";
const char* g_Wheel3Name = "Wheel-3";

using namespace std;

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   AddAvailableDeviceName(g_Shutter1Name, "Pro Scan shutter 1");
   AddAvailableDeviceName(g_Shutter2Name, "Pro Scan shutter 2");
   AddAvailableDeviceName(g_Shutter3Name, "Pro Scan shutter 3");
   AddAvailableDeviceName(g_Wheel1Name, "Pro Scan filter wheel 1");
   AddAvailableDeviceName(g_Wheel2Name, "Pro Scan filter wheel 2");
   AddAvailableDeviceName(g_Wheel3Name, "Pro Scan filter wheel 3");
   AddAvailableDeviceName(g_ZStageDeviceName, "Add-on Z-stage");
   AddAvailableDeviceName(g_XYStageDeviceName, "XY Stage");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_Shutter1Name) == 0)
   {
      Shutter* s = new Shutter(g_Shutter1Name, 1);
      return s;
   }
   else if (strcmp(deviceName, g_Shutter2Name) == 0)
   {
      Shutter* s = new Shutter(g_Shutter2Name, 2);
      return s;
   }
   else if (strcmp(deviceName, g_Shutter3Name) == 0)
   {
      Shutter* s = new Shutter(g_Shutter3Name, 3);
      return s;
   }
   else if (strcmp(deviceName, g_Wheel1Name) == 0)
   {
      Wheel* s = new Wheel(g_Wheel1Name, 1);
      return s;
   }
   else if (strcmp(deviceName, g_Wheel2Name) == 0)
   {
      Wheel* s = new Wheel(g_Wheel2Name, 2);
      return s;
   }
   else if (strcmp(deviceName, g_Wheel3Name) == 0)
   {
      Wheel* s = new Wheel(g_Wheel3Name, 3);
      return s;
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
// Shutter 
// ~~~~~~~

Shutter::Shutter(const char* name, int id) : name_(name), initialized_(false), id_(id), openTimeUs_(0)
{
   InitializeDefaultErrorMessages();
   SetErrorText(ERR_UNRECOGNIZED_ANSWER, "Unrecognized answer recived from the device");

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "Prior shutter adapter", MM::String, true);

   // Port
   CPropertyAction* pAct = new CPropertyAction (this, &Shutter::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

   UpdateStatus();
}

Shutter::~Shutter()
{
   Shutdown();
}

void Shutter::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int Shutter::Initialize()
{
   // set property list
   // -----------------
   
   // State
   // -----
   CPropertyAction* pAct = new CPropertyAction (this, &Shutter::OnState);
   int ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   AddAllowedValue(MM::g_Keyword_State, "0");
   AddAllowedValue(MM::g_Keyword_State, "1");

   // Delay
   // -----
   pAct = new CPropertyAction (this, &Shutter::OnDelay);
   ret = CreateProperty(MM::g_Keyword_Delay, "0.0", MM::Float, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   openTimeUs_ = GetClockTicksUs();
   initialized_ = true;

   return DEVICE_OK;
}

int Shutter::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

bool Shutter::Busy()
{
   long interval = GetClockTicksUs() - openTimeUs_;

   // TODO >> DEBUG
 //  if (interval/1000.0 < GetDelayMs() && interval > 0)
   if (interval/1000.0 < GetDelayMs() && interval > 0)
   {
      return true;
   }
   else
   {
       return false;
   }
}

int Shutter::SetOpen(bool open)
{
   long pos;
   if (open)
      pos = 1;
   else
      pos = 0;
   return SetProperty(MM::g_Keyword_State, CDeviceUtils::ConvertToString(pos));
}

int Shutter::GetOpen(bool& open)
{
   char buf[MM::MaxStrLength];
   int ret = GetProperty(MM::g_Keyword_State, buf);
   if (ret != DEVICE_OK)
      return ret;
   long pos = atol(buf);
   pos == 1 ? open = true : open = false;

   return DEVICE_OK;
}
int Shutter::Fire(double /*deltaT*/)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

/**
 * Sends an open/close command through the serial port.
 */
int Shutter::SetShutterPosition(bool state)
{
   ostringstream command;
   command << "8," << id_ << "," << (state ? "0" : "1");

   // send command
   int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
   if (ret != DEVICE_OK)
      return ret;

   // block/wait for acknowledge, or until we time out;
   string answer;
   ret = GetSerialAnswer(port_.c_str(), "\r", answer);
   if (ret != DEVICE_OK)
      return ret;

   openTimeUs_ = GetClockTicksUs();

   if (answer.substr(0,1).compare("R") == 0)
   {
      return DEVICE_OK;
   }
   else if (answer.substr(0, 1).compare("E") == 0 && answer.length() > 2)
   {
      int errNo = atoi(answer.substr(2).c_str());
      return ERR_OFFSET + errNo;
   }

   return ERR_UNRECOGNIZED_ANSWER;
}

/**
 * Check the state of the shutter.
 */
int Shutter::GetShutterPosition(bool& state)
{
   ostringstream command;
   command << "8," << id_;

   // send command
   int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
   if (ret != DEVICE_OK)
      return ret;

   // block/wait for acknowledge, or until we time out;
   string answer;
   ret = GetSerialAnswer(port_.c_str(), "\r", answer);
   if (ret != DEVICE_OK)
      return ret;

   if (answer.substr(0, 1).compare("E") == 0 && answer.length() > 2)
   {
      int errNo = atoi(answer.substr(2).c_str());
      return ERR_OFFSET + errNo;
   }
   else if (answer.length() > 0)
   {
      if (answer.substr(0,1).compare("0") == 0)
         state = true;
      else
         state = false;
      return DEVICE_OK;
   }

   return ERR_UNRECOGNIZED_ANSWER;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int Shutter::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      bool state;
      int ret = GetShutterPosition(state);
      if (ret != DEVICE_OK)
         return ret;
      if (state)
         pProp->Set(1L);
      else
         pProp->Set(0L);
   }
   else if (eAct == MM::AfterSet)
   {
      long pos;
      pProp->Get(pos);

      // apply the value
      return SetShutterPosition(pos == 0 ? false : true);
   }

   return DEVICE_OK;
}

int Shutter::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int Shutter::OnDelay(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(this->GetDelayMs());
   }
   else if (eAct == MM::AfterSet)
   {
      double delay;
      pProp->Get(delay);
      this->SetDelayMs(delay);
   }

   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// Wheel
//

Wheel::Wheel(const char* name, unsigned id) :
      initialized_(false), numPos_(10), name_(name), id_(id), curPos_(0), busy_(false),
      openTimeUs_(0)
{
   assert(strlen(name) < MM::MaxStrLength);

   InitializeDefaultErrorMessages();

   // add custom MTB messages
   SetErrorText(ERR_UNKNOWN_POSITION, "Position out of range");
   SetErrorText(ERR_PORT_CHANGE_FORBIDDEN, "You can't change the port after device has been initialized.");
   SetErrorText(ERR_SET_POSITION_FAILED, "Set position failed.");

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "Prior filter wheel adapter", MM::String, true);

   // Port
   CPropertyAction* pAct = new CPropertyAction (this, &Wheel::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

   UpdateStatus();
}

Wheel::~Wheel()
{
   Shutdown();
}

void Wheel::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

bool Wheel::Busy()
{
   long interval = GetClockTicksUs() - openTimeUs_;

   if (interval/1000.0 < GetDelayMs() && interval > 0)
   {
      return true;
   }
   else
   {
       return false;
   }
}

int Wheel::Initialize()
{

   // set property list
   // -----------------
   
   // State
   // -----
   CPropertyAction* pAct = new CPropertyAction (this, &Wheel::OnState);
   int ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // Speed
   // -----
   pAct = new CPropertyAction (this, &Wheel::OnSpeed);
   ret = CreateProperty(MM::g_Keyword_Speed, "3", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // Label
   // -----
   pAct = new CPropertyAction (this, &CStateBase::OnLabel);
   ret = CreateProperty(MM::g_Keyword_Label, "Undefined", MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // Delay
   // -----
   pAct = new CPropertyAction (this, &Wheel::OnDelay);
   ret = CreateProperty(MM::g_Keyword_Delay, "0.0", MM::Float, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // create default positions and labels
   const int bufSize = 1024;
   char buf[bufSize];
   for (unsigned i=0; i<numPos_; i++)
   {
      snprintf(buf, bufSize, "Filter-%d", i);
      SetPositionLabel(i, buf);
   }

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   //char setSerial = (char)238;
   //ret = WriteToComPort(port_.c_str(), &setSerial, 1);
   ret = SetWheelPosition(1);
   if (DEVICE_OK != ret)
      return ret;

   openTimeUs_ = GetClockTicksUs();
   initialized_ = true;

   return DEVICE_OK;
}

int Wheel::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

int Wheel::SetWheelPosition(unsigned pos)
{
   ostringstream command;
   command << "7," << id_ << "," << pos;

   // send command
   int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
   if (ret != DEVICE_OK)
      return ret;

   // block/wait for acknowledge, or until we time out;
   string answer;
   ret = GetSerialAnswer(port_.c_str(), "\r", answer);
   if (ret != DEVICE_OK)
      return ret;

   openTimeUs_ = GetClockTicksUs(); 
   
   if (answer.substr(0,1).compare("R") == 0)
   {
      curPos_ = pos;
      return DEVICE_OK;
   }
   else if (answer.substr(0, 1).compare("E") == 0 && answer.length() > 2)
   {
      int errNo = atoi(answer.substr(2).c_str());
      return ERR_OFFSET + errNo;
   }

   return ERR_UNRECOGNIZED_ANSWER;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int Wheel::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set((long)curPos_);
   }
   else if (eAct == MM::AfterSet)
   {
      long pos;
      pProp->Get(pos);

      //check if we are already in that state
      if (pos == curPos_)
         return DEVICE_OK;

      if (pos >= (long)numPos_ || pos < 0)
      {
         pProp->Set((long)curPos_); // revert
         return ERR_UNKNOWN_POSITION;
      }

      // try to apply the value
      int ret = SetWheelPosition(pos);
      if (ret != DEVICE_OK)
      {
         pProp->Set((long)curPos_); // revert
         return ret;
      }
      curPos_ = pos;
   }

   return DEVICE_OK;
}

int Wheel::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int Wheel::OnDelay(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(this->GetDelayMs());
   }
   else if (eAct == MM::AfterSet)
   {
      double delay;
      pProp->Get(delay);
      this->SetDelayMs(delay);
   }

   return DEVICE_OK;
}

int Wheel::OnSpeed(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   // TODO
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
   CreateProperty(MM::g_Keyword_Description, "Prior XY stage driver adapter", MM::String, true);

   // Port
   CPropertyAction* pAct = new CPropertyAction (this, &XYStage::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);
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
   // set stage step size and resolution
   double resX, resY;
   int ret = GetResolution(resX, resY);
   if (ret != DEVICE_OK)
      return ret;

   //if (resX <= 0.0 || resY <= 0.0)
   //   return ERR_INVALID_STEP_SIZE;

   // Since XY stage sometimes returns 0 as the resoloution, we are going to force it to
   // haardcoded value
   if (resX <= 0.0 || resY <= 0.0) {
      resX = 0.1;
      resY = 0.1;
   }

   stepSizeXUm_ = resX;
   stepSizeYUm_ = resY;

   // Step size
   CPropertyAction* pAct = new CPropertyAction (this, &XYStage::OnStepSizeX);
   CreateProperty("StepSizeX_um", "0.0", MM::Float, true, pAct);
   pAct = new CPropertyAction (this, &XYStage::OnStepSizeY);
   CreateProperty("StepSizeY_um", "0.0", MM::Float, true, pAct);

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
   const char* command = "$";

   // send command
   int ret = SendSerialCommand(port_.c_str(), command, "\r");
   if (ret != DEVICE_OK)
      return false;

   // block/wait for acknowledge, or until we time out;
   string answer;
   ret = GetSerialAnswer(port_.c_str(), "\r", answer);
   if (ret != DEVICE_OK)
      return false;

   if (answer.length() == 1)
   {
      int status = atoi(answer.c_str());
      return status == 1 ? true : false;
   }

   return false;
}

int XYStage::SetPositionUm(double x, double y)
{
   long xSteps = (long) (x / stepSizeXUm_ + 0.5);
   long ySteps = (long) (y / stepSizeYUm_ + 0.5);
   
   return SetPositionSteps(xSteps, ySteps);
}

int XYStage::GetPositionUm(double& x, double& y)
{
   long xSteps, ySteps;
   int ret = GetPositionSteps(xSteps, ySteps);
   if (ret != DEVICE_OK)
      return ret;

   x = xSteps * stepSizeXUm_;
   y = ySteps * stepSizeYUm_;

   return DEVICE_OK;
}
  
int XYStage::SetPositionSteps(long x, long y)
{
   ostringstream command;
   command << "G," << x << "," << y;

   // send command
   int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
   if (ret != DEVICE_OK)
      return ret;

   // block/wait for acknowledge, or until we time out;
   string answer;
   ret = GetSerialAnswer(port_.c_str(), "\r", answer);
   if (ret != DEVICE_OK)
      return ret;

   if (answer.substr(0,1).compare("R") == 0)
   {
      return DEVICE_OK;
   }
   else if (answer.substr(0, 1).compare("E") == 0 && answer.length() > 2)
   {
      int errNo = atoi(answer.substr(2).c_str());
      return ERR_OFFSET + errNo;
   }

   return ERR_UNRECOGNIZED_ANSWER;   
}
 
int XYStage::GetPositionSteps(long& x, long& y)
{
   int ret = GetPositionStepsSingle('X', x);
   if (ret != DEVICE_OK)
      return ret;

   return GetPositionStepsSingle('Y', y);
}

int XYStage::SetOrigin()
{
   // send command
   int ret = SendSerialCommand(port_.c_str(), "PS,0,0", "\r");
   if (ret != DEVICE_OK)
      return ret;

   // block/wait for acknowledge, or until we time out;
   string answer;
   ret = GetSerialAnswer(port_.c_str(), "\r", answer);
   if (ret != DEVICE_OK)
      return ret;

   if (answer.substr(0,1).compare("0") == 0)
   {
      return DEVICE_OK;
   }
   else if (answer.substr(0, 1).compare("E") == 0 && answer.length() > 2)
   {
      int errNo = atoi(answer.substr(2).c_str());
      return ERR_OFFSET + errNo;
   }

   return ERR_UNRECOGNIZED_ANSWER;   
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
   const char* commandX="RES,X";
   const char* commandY="RES,Y";

   int ret = GetDblParameter(commandX, resX);
   if (ret != DEVICE_OK)
      return ret;

   ret = GetDblParameter(commandY, resY);
   if (ret != DEVICE_OK)
      return ret;

   return ret;
}

int XYStage::GetDblParameter(const char* command, double& param)
{
   // send command
   int ret = SendSerialCommand(port_.c_str(), command, "\r");
   if (ret != DEVICE_OK)
      return ret;

   // block/wait for acknowledge, or until we time out;
   string answer;
   ret = GetSerialAnswer(port_.c_str(), "\r", answer);
   if (ret != DEVICE_OK)
      return ret;

   if (answer.length() > 2 && answer.substr(0, 1).compare("E") == 0)
   {
      int errNo = atoi(answer.substr(2).c_str());
      return ERR_OFFSET + errNo;
   }
   else if (answer.length() > 0)
   {
      param = atof(answer.c_str());
      return DEVICE_OK;
   }

   return ERR_UNRECOGNIZED_ANSWER;
}

int XYStage::GetPositionStepsSingle(char axis, long& steps)
{
   ostringstream command;
   command << "P" << axis;

   // send command
   int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
   if (ret != DEVICE_OK)
      return ret;

   // block/wait for acknowledge, or until we time out;
   string answer;
   ret = GetSerialAnswer(port_.c_str(), "\r", answer);
   if (ret != DEVICE_OK)
      return ret;

   if (answer.length() > 2 && answer.substr(0, 1).compare("E") == 0)
   {
      int errNo = atoi(answer.substr(2).c_str());
      return ERR_OFFSET + errNo;
   }
   else if (answer.length() > 0)
   {
      steps = atol(answer.c_str());
      return DEVICE_OK;
   }

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
   CreateProperty(MM::g_Keyword_Description, "Prior Z-stage driver adapter", MM::String, true);

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
   double res;
   int ret = GetResolution(res);
   if (ret != DEVICE_OK)
      return ret;

   if (res <= 0.0)
      return ERR_INVALID_STEP_SIZE;

   stepSizeUm_ = res;

   ret = GetPositionSteps(curSteps_);
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
   const char* command = "$";

   // send command
   int ret = SendSerialCommand(port_.c_str(), command, "\r");
   if (ret != DEVICE_OK)
      return false;

   // block/wait for acknowledge, or until we time out;
   string answer;
   ret = GetSerialAnswer(port_.c_str(), "\r", answer);
   if (ret != DEVICE_OK)
      return false;

   if (answer.length() == 1)
   {
      int status = atoi(answer.c_str());
      return status == 1 ? true : false;
   }

   return false;
}

int ZStage::SetPositionUm(double pos)
{
   long steps = (long) (pos / stepSizeUm_ + 0.5);
   return SetPositionSteps(steps);
}

int ZStage::GetPositionUm(double& pos)
{
   long steps;
   int ret = GetPositionSteps(steps);
   if (ret != DEVICE_OK)
      return ret;
   pos = steps * stepSizeUm_;
   return DEVICE_OK;
}
  
int ZStage::SetPositionSteps(long pos)
{
   long delta = pos - curSteps_;
   ostringstream command;
   if (delta >= 0)
      command << "U," << delta;
   else
      command << "D," << -delta;

   // send command
   int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
   if (ret != DEVICE_OK)
      return ret;

   // block/wait for acknowledge, or until we time out;
   string answer;
   ret = GetSerialAnswer(port_.c_str(), "\r", answer);
   if (ret != DEVICE_OK)
      return ret;

   if (answer.substr(0,1).compare("R") == 0)
   {
      curSteps_ = pos;
      return DEVICE_OK;
   }
   else if (answer.substr(0, 1).compare("E") == 0 && answer.length() > 2)
   {
      int errNo = atoi(answer.substr(2).c_str());
      return ERR_OFFSET + errNo;
   }

   return ERR_UNRECOGNIZED_ANSWER;   
}
  
int ZStage::GetPositionSteps(long& steps)
{
   const char* command="PZ";

   // send command
   int ret = SendSerialCommand(port_.c_str(), command, "\r");
   if (ret != DEVICE_OK)
      return ret;

   // block/wait for acknowledge, or until we time out;
   string answer;
   ret = GetSerialAnswer(port_.c_str(), "\r", answer);
   if (ret != DEVICE_OK)
      return ret;

   if (answer.length() > 2 && answer.substr(0, 1).compare("E") == 0)
   {
      int errNo = atoi(answer.substr(2).c_str());
      return ERR_OFFSET + errNo;
   }
   else if (answer.length() > 0)
   {
      steps = atol(answer.c_str());
      curSteps_ = steps;
      return DEVICE_OK;
   }

   return ERR_UNRECOGNIZED_ANSWER;
}

int ZStage::GetResolution(double& res)
{
   const char* command="RES,Z";

   // send command
   int ret = SendSerialCommand(port_.c_str(), command, "\r");
   if (ret != DEVICE_OK)
      return ret;

   // block/wait for acknowledge, or until we time out;
   string answer;
   ret = GetSerialAnswer(port_.c_str(), "\r", answer);
   if (ret != DEVICE_OK)
      return ret;

   if (answer.length() > 2 && answer.substr(0, 1).compare("E") == 0)
   {
      int errNo = atoi(answer.substr(2).c_str());
      return ERR_OFFSET + errNo;
   }
   else if (answer.length() > 0)
   {
      res = atof(answer.c_str());
      return DEVICE_OK;
   }

   return ERR_UNRECOGNIZED_ANSWER;
}

int ZStage::SetOrigin()
{
   return DEVICE_UNSUPPORTED_COMMAND;
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


///////////////////////////////////////////////////////////////////////////////
// BasicController device  >> NOT FINISHED
///////////////////////////////////////////////////////////////////////////////

BasicController::BasicController() :
   initialized_(false),
   port_("Undefined"),
   answerTimeoutMs_(1000)
{
   InitializeDefaultErrorMessages();

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, g_BasicControllerName, MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "Prior controller adtapter", MM::String, true);

   // Port
   CPropertyAction* pAct = new CPropertyAction (this, &BasicController::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

}

BasicController::~BasicController()
{
   Shutdown();
}

void BasicController::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_BasicControllerName);
}

int BasicController::Initialize()
{
   // set property list
   // -----------------
   
   // command
   CPropertyAction *pAct = new CPropertyAction (this, &BasicController::OnCommand);
   CreateProperty("Command", "", MM::String, false, pAct);

   pAct = new CPropertyAction (this, &BasicController::OnResponse);
   CreateProperty("Response", "", MM::String, true, pAct);

   int ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   // switch to selected command protocol;
   const unsigned cmdLen = 2;
   char cmd[cmdLen];

   // set-up high level commands
   cmd[0] = (char)0xFF;
   cmd[1] = (char)65;
   ret = WriteToComPort(port_.c_str(), cmd, cmdLen);
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;
   return DEVICE_OK;
}

int BasicController::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

bool BasicController::Busy()
{
   const char* cmd = "STATUS\r";

   // send command
   if (DEVICE_OK != WriteToComPort(port_.c_str(), cmd, (unsigned)strlen(cmd)))
      return false; // can't write so say we're not busy

   char status=0;
   unsigned long read=0;
   int numTries=0;
   do {
      if (DEVICE_OK != ReadFromComPort(port_.c_str(), &status, 1, read))
         return false;
      numTries++;
   } while(read==0 && numTries < 3);

   if (status == 'B')
      return true;
   else
      return false;
}
    
int BasicController::ExecuteCommand(const string& cmd, string& response)
{
   // send command
   PurgeComPort(port_.c_str());
   if (DEVICE_OK != WriteToComPort(port_.c_str(), cmd.c_str(), (unsigned) cmd.length()))
      return DEVICE_SERIAL_COMMAND_FAILED;

   // block/wait for acknowledge, or until we time out;
   const unsigned long bufLen = 80;
   char answer[bufLen];
   unsigned curIdx = 0;
   memset(answer, 0, bufLen);
   unsigned long read;
   unsigned long startTime = GetClockTicksUs();

   char* pLF = 0;
   do {
      if (DEVICE_OK != ReadFromComPort(port_.c_str(), answer + curIdx, bufLen - curIdx, read))
         return DEVICE_SERIAL_COMMAND_FAILED;
      curIdx += read;

      // look for the LF
      pLF = strstr(answer, "\n");
      if (pLF)
         *pLF = 0; // terminate the string
   }
   while(!pLF && (GetClockTicksUs() - startTime) / 1000.0 < answerTimeoutMs_);

   if (!pLF)
      return DEVICE_SERIAL_TIMEOUT;

   response = answer;
   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int BasicController::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int BasicController::OnCommand(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(command_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(command_);
      return ExecuteCommand(command_, response_);
   }

   return DEVICE_OK;
}

int BasicController::OnResponse(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(response_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      assert(!"Read-only property modified");
   }

   return DEVICE_OK;
}

