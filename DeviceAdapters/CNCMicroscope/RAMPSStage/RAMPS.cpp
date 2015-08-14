/*
Copyright 2015 Google Inc. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

#include "RAMPS.h"
#include "XYStage.h"
#include "ZStage.h"
#include <cstdio>
#include <string>
#include <math.h>
#include "ModuleInterface.h"
#include <sstream>
#include <algorithm>
#include <iostream>


using namespace std;

// External names used used by the rest of the system
// to load particular device from the "RAMPS.dll" library
const char* g_XYStageDeviceName = "DXYStage";
const char* g_ZStageDeviceName = "DZStage";
const char* g_HubDeviceName = "DHub";
const char* g_versionProp = "Version";
const char* g_XYVelocityProp = "Maximum Velocity";
const char* g_XYAccelerationProp = "Acceleration";
const char* g_SettleTimeProp = "Settle Time";

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////

MODULE_API void InitializeModuleData()
{
  RegisterDevice(g_XYStageDeviceName, MM::XYStageDevice, "RAMPS XY stage");
  RegisterDevice(g_ZStageDeviceName, MM::StageDevice, "RAMPS Z stage");
  RegisterDevice(g_HubDeviceName, MM::HubDevice, "DHub");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
  if (deviceName == 0)
    return 0;

  if (strcmp(deviceName, g_XYStageDeviceName) == 0)
  {
    // create stage
    return new RAMPSXYStage();
  }
  if (strcmp(deviceName, g_ZStageDeviceName) == 0)
  {
    // create stage
    return new RAMPSZStage();
  }
  else if (strcmp(deviceName, g_HubDeviceName) == 0)
  {
    return new RAMPSHub();
  }

  // ...supplied name not recognized
  return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
  delete pDevice;
}

std::vector<std::string> &split(const std::string &s, char delim, std::vector<std::string> &elems) {
  std::stringstream ss(s);
  std::string item;
  while (std::getline(ss, item, delim)) {
    elems.push_back(item);
  }
  return elems;
}


std::vector<std::string> split(const std::string &s, char delim) {
  std::vector<std::string> elems;
  split(s, delim, elems);
  return elems;
}



RAMPSHub::RAMPSHub():
    initialized_(false),
    busy_(false),
    target_x_(0.),
    target_y_(0.),
    target_z_(0.),
    status_("Idle"),
    velocity_(10000),
    acceleration_(10000),
    settle_time_(250),
    timeOutTimer_(0)
{
  LogMessage("RAMPS Constructor");
  CPropertyAction* pAct  = new CPropertyAction(this, &RAMPSHub::OnPort);
  CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

}

RAMPSHub::~RAMPSHub() { Shutdown();}

int RAMPSHub::Initialize()
{
  LogMessage("RAMPS Initialize");
  /* From EVA's XYStage */
  int ret = DEVICE_ERR;

  // initialize device and get hardware information


  // confirm that the device is supported


  // check if we are already homed

  CPropertyAction* pAct;

  CreateProperty("Status", "", MM::String, false);



  MMThreadGuard myLock(lock_);

  pAct = new CPropertyAction(this, &RAMPSHub::OnCommand);
  ret = CreateProperty("Command","", MM::String, false, pAct);
  if (DEVICE_OK != ret)
     return ret;

  // // turn off verbose serial debug messages
  GetCoreCallback()->SetDeviceProperty(port_.c_str(), "Verbose", "1");
  // synchronize all properties
  // --------------------------

  
  PurgeComPortH();
  std::string expected;
  std::string answer;

  CDeviceUtils::SleepMs(2000);

  while (true) {
    int ret = ReadResponse(answer);
    if (ret != DEVICE_OK)
    {
      LogMessage("Got timeout:");
      LogMessageCode(ret,true);
      break;
    }
  }
  PurgeComPortH();

  LogMessage("Getting Controllr Version.");
  ret = GetControllerVersion(version_);
  if( DEVICE_OK != ret)
    return ret;
  pAct = new CPropertyAction(this, &RAMPSHub::OnVersion);
  std::ostringstream sversion;
  sversion << version_;
  CreateProperty(g_versionProp, sversion.str().c_str(), MM::String, true, pAct);

  PurgeComPortH();

  string command = "G90";
  LogMessage("Writing absolute mode to com port");
  LogMessage(command);
  ret = SendCommand(command);
  if (ret != DEVICE_OK) {
    LogMessage("G90 Send Command failed");
    return ret;
  }
  ret = ReadResponse(answer);
  if (ret != DEVICE_OK) {
    LogMessage("error getting controller version.");
    return ret;
  }
  LogMessage("G90 Response:");
  LogMessage(answer);
  if (answer != "ok") {
    LogMessage("expected ok.");
    return DEVICE_ERR;
  }

  PurgeComPortH();

  LogMessage("Writing current location as origin.");
  LogMessage("M206 X0 Y0 Z0");
  ret = SendCommand("M206 X0 Y0 Z0");
  if (ret != DEVICE_OK) {
    LogMessage("Set Origin Send Command failed");
    return ret;
  }
  LogMessage("Set Origin Response:");
  ret = ReadResponse(answer);
  if (ret != DEVICE_OK) {
    LogMessage("error getting controller version.");
    return ret;
  }
  LogMessage(answer);
  if (answer != "ok") {
    LogMessage("expected ok.");
    return DEVICE_ERR;
  }
  PurgeComPortH();

  SetVelocity(velocity_);
    PurgeComPortH();
  SetAcceleration(acceleration_);
    PurgeComPortH();

  PurgeComPortH();

  ret = GetStatus();
  if (ret != DEVICE_OK)
    return ret;

  ret = UpdateStatus();
  if (ret != DEVICE_OK)
    return ret;



  // Max Speed
  pAct = new CPropertyAction (this, &RAMPSHub::OnVelocity);
  CreateProperty(g_XYVelocityProp, CDeviceUtils::ConvertToString(velocity_), MM::Float, false, pAct);
  SetPropertyLimits(g_XYVelocityProp, 0.0, 10000000.0);

  // Acceleration
  pAct = new CPropertyAction (this, &RAMPSHub::OnAcceleration);
  CreateProperty(g_XYAccelerationProp, CDeviceUtils::ConvertToString(acceleration_), MM::Float, false, pAct);
  SetPropertyLimits(g_XYAccelerationProp, 0.0, 1000000000);
  
  pAct = new CPropertyAction (this, &RAMPSHub::OnSettleTime);
  CreateProperty(g_SettleTimeProp, CDeviceUtils::ConvertToString(settle_time_), MM::Integer, false, pAct);
  SetPropertyLimits(g_SettleTimeProp, 0, 5000);
  

  initialized_ = true;
  return DEVICE_OK;
}

int RAMPSHub::Shutdown() {initialized_ = false; return DEVICE_OK;};
bool RAMPSHub::Busy() {   LogMessage("RAMPS busy");
return busy_;} ;

// private and expects caller to:
// 1. guard the port
// 2. purge the port
int RAMPSHub::GetControllerVersion(string& version)
{
  LogMessage("RAMPS GetControllerVersion");
  int ret = DEVICE_OK;
  version = "";

  std::string answer;
  ret = SendCommand("M115");
  if (ret != DEVICE_OK) {
    LogMessage("error getting controller version.");
    return ret;
  }
  ret = ReadResponse(answer);
  if (ret != DEVICE_OK) {
    LogMessage("error getting controller version.");
    return ret;
  }
  LogMessage("Got answer:");
  LogMessage(answer.c_str());
  version = answer;

  ret = ReadResponse(answer);
  if (ret != DEVICE_OK) {
    LogMessage("error getting controller version.");
    return ret;
  }
  LogMessage("Got OK:");
  LogMessage(answer.c_str());
  version = answer;

  return ret;

}
int RAMPSHub::DetectInstalledDevices()
{
  LogMessage("RAMPS DetectInstalledDevices");
  ClearInstalledDevices();

  // make sure this method is called before we look for available devices
  InitializeModuleData();

  char hubName[MM::MaxStrLength];
  GetName(hubName); // this device name
  for (unsigned i=0; i<GetNumberOfDevices(); i++)
  {
    char deviceName[MM::MaxStrLength];
    LogMessage("Get device");
    bool success = GetDeviceName(i, deviceName, MM::MaxStrLength);
    if (success && (strcmp(hubName, deviceName) != 0))
    {
      LogMessage("Got device");
      LogMessage(deviceName);
      MM::Device* pDev = CreateDevice(deviceName);
      AddInstalledDevice(pDev);
    }
  }
  return DEVICE_OK;
}

void RAMPSHub::GetName(char* pName) const
{
  CDeviceUtils::CopyLimitedString(pName, g_HubDeviceName);
}

int RAMPSHub::OnVersion(MM::PropertyBase* pProp, MM::ActionType pAct)
{
  LogMessage("RAMPS OnVersion");
  if (pAct == MM::BeforeGet)
  {
    pProp->Set(version_.c_str());
  }
  return DEVICE_OK;
}
int RAMPSHub::OnPort(MM::PropertyBase* pProp, MM::ActionType pAct)
{
  LogMessage("RAMPS OnPort");
  if (pAct == MM::BeforeGet)
  {
    pProp->Set(port_.c_str());
  }
  else if (pAct == MM::AfterSet)
  {
    pProp->Get(port_);
    portAvailable_ = true;
  }
  return DEVICE_OK;
}

int RAMPSHub::OnCommand(MM::PropertyBase* pProp, MM::ActionType pAct)
{
  LogMessage("RAMPS OnCommand");
  if (pAct == MM::BeforeGet)
  {
    pProp->Set(commandResult_.c_str());
  }
  else if (pAct == MM::AfterSet)
  {
    std::string cmd;
    pProp->Get(cmd);
    if(cmd.compare(commandResult_) ==0)  // command result still there
      return DEVICE_OK;
    int ret = SendCommand(cmd);
    if(DEVICE_OK != ret){
      commandResult_.assign("Error!");
      return DEVICE_ERR;
    }
	ret = ReadResponse(commandResult_);
    if(DEVICE_OK != ret){
      commandResult_.assign("Error!");
      return DEVICE_ERR;
    }
  }
  return DEVICE_OK;
}

int RAMPSHub::SendCommand(std::string command, std::string terminator) 
{
  LogMessage("RAMPS SendCommand");
  LogMessage("command=" + command);
  if(!portAvailable_)
    return ERR_NO_PORT_SET;
  // needs a lock because the other Thread will also use this function
  MMThreadGuard(this->executeLock_);
  int ret = DEVICE_OK;

  LogMessage("Write command.");
  ret = SetCommandComPortH(command.c_str(), terminator.c_str());
  LogMessage("set command, ret=" + ret);
  if (ret != DEVICE_OK)
  {
    LogMessage("command write fail");
    return ret;
  }
  return ret;
}


int RAMPSHub::ReadResponse(std::string &returnString, float timeout)
{
  SetAnswerTimeoutMs(timeout); //for normal command
  MMThreadGuard(this->executeLock_);

  std::string an;
  try
  {

    int ret = GetSerialAnswerComPortH(an,"\n");
    if (ret != DEVICE_OK)
    {
      LogMessage(std::string("answer get error!_"));
      return ret;
    }
    LogMessage("answer:");
    LogMessage(an);
    returnString = an;
  }
  catch(...)
  {
    LogMessage("Exception in receive response!");
    return DEVICE_ERR;
  }
  return DEVICE_OK;
}


MM::DeviceDetectionStatus RAMPSHub::DetectDevice(void)
{
  LogMessage("RAMPS DetectDevice");
  if (initialized_)
    return MM::CanCommunicate;

  // all conditions must be satisfied...
  MM::DeviceDetectionStatus result = MM::Misconfigured;
  char answerTO[MM::MaxStrLength];

  try
  {
    std::string portLowerCase = port_;
    for( std::string::iterator its = portLowerCase.begin(); its != portLowerCase.end(); ++its)
    {
      *its = (char)tolower(*its);
    }
    if( 0< portLowerCase.length() &&  0 != portLowerCase.compare("undefined")  && 0 != portLowerCase.compare("unknown") )
    {
      result = MM::CanNotCommunicate;
      // record the default answer time out
      GetCoreCallback()->GetDeviceProperty(port_.c_str(), "AnswerTimeout", answerTO);

      // device specific default communication parameters
      // for Arduino Duemilanova
      GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_Handshaking, "Off");
      GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_BaudRate, "115200" );
      GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_StopBits, "1");
      GetCoreCallback()->SetDeviceProperty(port_.c_str(), "AnswerTimeout", "500.0");
      GetCoreCallback()->SetDeviceProperty(port_.c_str(), "DelayBetweenCharsMs", "0");
      MM::Device* pS = GetCoreCallback()->GetDevice(this, port_.c_str());
      pS->Initialize();
      // The first second or so after opening the serial port, the Arduino is waiting for firmwareupgrades.  Simply sleep 2 seconds.
      CDeviceUtils::SleepMs(2000);
      MMThreadGuard myLock(executeLock_);
      string an;

      while (true) {
        int ret = ReadResponse(an);
        if (ret != DEVICE_OK)
        {
          LogMessage("Got timeout:");
          LogMessageCode(ret,true);
          break;
        }
      }
      LogMessage("Checking for status.");
      PurgeComPort(port_.c_str());
      int ret = GetStatus();
      // later, Initialize will explicitly check the version #
      if( DEVICE_OK != ret )
      {
        LogMessage("Got:");
        LogMessageCode(ret,true);
      }
      else
      {
        // to succeed must reach here....
        result = MM::CanCommunicate;
      }
      pS->Shutdown();
      // always restore the AnswerTimeout to the default
      GetCoreCallback()->SetDeviceProperty(port_.c_str(), "AnswerTimeout", answerTO);
    }
  }
  catch(...)
  {
    LogMessage("Exception in DetectDevice!",false);
  }

  return result;
}

int RAMPSHub::SetAnswerTimeoutMs(double timeout)
{
  LogMessage("RAMPS SetAnswerTimeoutMs");
  if(!portAvailable_)
    return ERR_NO_PORT_SET;
  GetCoreCallback()->SetDeviceProperty(port_.c_str(), "AnswerTimeout",  CDeviceUtils::ConvertToString(timeout));
  return DEVICE_OK;
}

template <class Type>
Type stringToNum(const std::string& str)
{
  std::istringstream iss(str);
  Type num;
  iss >> num;
  return num;
}

int RAMPSHub::GetXYPosition(double *x, double *y) {
  GetStatus();
  *x = MPos[0];
  *y = MPos[1];
  return DEVICE_OK;
}

std::string RAMPSHub::GetState() {
  GetStatus();
  return status_;
}

int RAMPSHub::SetTargetXY(double x, double y) {
  status_ = "Running";
  target_x_ = x/1000.;
  target_y_ = y/1000.;
  return DEVICE_OK;
}

int RAMPSHub::SetTargetZ(double z) {
  status_ = "Running";
  target_z_ = z/1000.;
  return DEVICE_OK;
}

int RAMPSHub::GetStatus()
{
  LogMessage("RAMPS GetStatus");
  std::string returnString;

  if(!portAvailable_)
    return ERR_NO_PORT_SET;

  int ret = DEVICE_OK;

  PurgeComPortH();
  LogMessage("Write command.");
  LogMessage("M114");
  ret = SendCommand("M114");
  if (ret != DEVICE_OK)
  {
    LogMessage("command write fail");
    return ret;
  }

  string an;

  LogMessage("Checking for status");
  ret = ReadResponse(an);
  if (ret != DEVICE_OK)
  {
    LogMessage(std::string("answer get error!_"));
    return ret;
  }
  if (an.length() <1) {
    LogMessage("device error.");
    return DEVICE_ERR;
  }
  LogMessage("Got answer:");
  LogMessage(an);
  std::vector<std::string> spl;
  spl = split(an, ' ');
  for (std::vector<std::string>::iterator i = spl.begin(); i != spl.end(); ++i) {
    LogMessage("i=");
    LogMessage(*i);
    if (*i == "Count") break;
    std::vector<std::string> spl2;
    spl2 = split(*i, ':');
    if (spl2[0] == "X") {
      LogMessage("GotStatus X: ");
      LogMessage(spl2[1]);
      MPos[0] = stringToNum<double>(spl2[1]);
    }
    spl2 = split(*i, ':');
    if (spl2[0] == "Y") {
      LogMessage("GotStatus Y: ");
      LogMessage(spl2[1]);
      MPos[1] = stringToNum<double>(spl2[1]);
    }
    spl2 = split(*i, ':');
    if (spl2[0] == "Z") {
      LogMessage("GotStatus Z: ");
      LogMessage(spl2[1]);
      MPos[2] = stringToNum<double>(spl2[1]);
    }
  }
  LogMessage("Checking for ok");
  ret = ReadResponse(an);
  if (ret != DEVICE_OK)
  {
    LogMessage(std::string("answer get error!_"));
    return ret;
  }
  if (an != "ok")
  {
    LogMessage(std::string("answer get error!_"));
    return ret;
  }
  LogMessage("Got OK");

  

  // if (timeOutTimer_ == 0) {
  //   LogMessage("Stage transitioned from moving to stopped.");
  //   LogMessage("Enabling post-stop timer.");
  //   timeOutTimer_ = new MM::TimeoutMs(GetCurrentMMTime(),  settle_time_);
  //   return true;
  // } else if (timeOutTimer_->expired(GetCurrentMMTime())) {
  //   LogMessage("Timer expired. return false.");
  //   delete(timeOutTimer_);
  //   timeOutTimer_ = 0;
  // }

  // TODO(dek): add Z here
  LogMessage("target_x_=");
  LogMessage(CDeviceUtils::ConvertToString(target_x_));
  LogMessage("target_y_=");
  LogMessage(CDeviceUtils::ConvertToString(target_y_));
  LogMessage("target_z_=");
  LogMessage(CDeviceUtils::ConvertToString(target_z_));
  LogMessage("MPos[0]=");
  LogMessage(CDeviceUtils::ConvertToString(MPos[0]));
  LogMessage("MPos[1]_=");
  LogMessage(CDeviceUtils::ConvertToString(MPos[1]));
  LogMessage("MPos[2]=");
  LogMessage(CDeviceUtils::ConvertToString(MPos[2]));
  if (status_ == "Running") {
    LogMessage("Device is running.");
    if (MPos[0] == target_x_ && MPos[1] == target_y_ && MPos[2] == target_z_) {
      LogMessage("Device reached target state.");
      status_ = "Idle";
    }
  }
  return DEVICE_OK;
}

int RAMPSHub::ReadFromComPortH(unsigned char* answer, unsigned maxLen, unsigned long& bytesRead)
{
  LogMessage("RAMPS ReadFromComPortH");
  return ReadFromComPort(port_.c_str(), answer, maxLen, bytesRead);
}
int RAMPSHub::SetCommandComPortH(const char* command, const char* term)
{
  LogMessage("RAMPS SetCommandComPortH");
  return SendSerialCommand(port_.c_str(),command,term);
}
int RAMPSHub::GetSerialAnswerComPortH (std::string& ans,  const char* term)
{
  LogMessage("RAMPS GetSerialAnswerComPortH");
  return GetSerialAnswer(port_.c_str(),term,ans);
}

int RAMPSHub::PurgeComPortH() {  LogMessage("RAMPS PurgeComPortH");
return PurgeComPort(port_.c_str());}
int RAMPSHub::WriteToComPortH(const unsigned char* command, unsigned len) {LogMessage("RAMPS WriteToComPortH"); return WriteToComPort(port_.c_str(), command, len);}

int RAMPSHub::OnSettleTime(MM::PropertyBase* pProp, MM::ActionType eAct)
{
LogMessage("RAMPS XYStage OnSettleTime");
  if (eAct == MM::BeforeGet)
  {
        

    pProp->Set(settle_time_);
  }
  else if (eAct == MM::AfterSet)
  {
    if (initialized_)
    {
      long settle_time;
      pProp->Get(settle_time);
      settle_time_ = settle_time;
    }
  }

   
  LogMessage("Set settle time");

  return DEVICE_OK;
}

int RAMPSHub::SetVelocity(double velocity) {
  RAMPSHub* pHub = static_cast<RAMPSHub*>(GetParentHub());

  std::string velStr = CDeviceUtils::ConvertToString(velocity);
  std::string command = "M203 X" + velStr + " Y" + velStr + " Z" + velStr;
  std::string result;
  PurgeComPortH();
  int ret = pHub->SendCommand(command);
  if (ret != DEVICE_OK) return ret;
  ret = pHub->ReadResponse(result);
  if (ret != DEVICE_OK) return ret;
  if (result != "ok") {
    LogMessage("Expected OK");
  }

  return ret;
}

int RAMPSHub::SetAcceleration(double acceleration) {
  RAMPSHub* pHub = static_cast<RAMPSHub*>(GetParentHub());
	
  std::string accStr = CDeviceUtils::ConvertToString(acceleration);
  std::string command = "M201 X" + accStr + " Y" + accStr + " Z" + accStr;
  std::string result;
  PurgeComPortH();
  int ret = pHub->SendCommand(command);
  if (ret != DEVICE_OK) return ret;
  ret = pHub->ReadResponse(result);
  if (ret != DEVICE_OK) return ret;
  if (result != "ok") {
    LogMessage("Expected OK");
  }

  return ret;
}


// TODO(dek): these should send actual commands to update the device
int RAMPSHub::OnVelocity(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  LogMessage("RAMPS XYStage OnVelocity");
  if (eAct == MM::BeforeGet)
  {
        

    pProp->Set(velocity_);
  }
  else if (eAct == MM::AfterSet)
  {
    if (initialized_)
    {
      double velocity;
      pProp->Get(velocity);
      velocity_ = velocity;
      SetVelocity(velocity);
    }

  }

   
  LogMessage("Set velocity");

  return DEVICE_OK;
}

int RAMPSHub::OnAcceleration(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  LogMessage("RAMPS XYStage OnAcceleration");
  if (eAct == MM::BeforeGet)
  {
        

    pProp->Set(acceleration_);
  }
  else if (eAct == MM::AfterSet)
  {
    if (initialized_)
    {
      double acceleration;
      pProp->Get(acceleration);
      acceleration_ = acceleration;
      SetAcceleration(acceleration);
    }

  }

   
  LogMessage("Set acceleration");

  return DEVICE_OK;
}
