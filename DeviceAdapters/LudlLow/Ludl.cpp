///////////////////////////////////////////////////////////////////////////////
// FILE:          Ludl.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Ludl device adapter.
//                
// AUTHOR:        Nico Stuurman, nico@cmp.ucsf.edu 4/12/2007, XYStage and ZStage by Nenad Amdoaj 
//
// COPYRIGHT:     University of California, San Francisco, 2007
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

#include "Ludl.h"
#include "../../MMDevice/ModuleInterface.h"
#include "../../MMDevice/DeviceBase.h"
#include <cstdio>
#include <math.h>

// XYStage
const char* g_XYStageDeviceName = "XYStage";

// single axis stage
const char* g_StageDeviceName = "Stage";

const char* g_Controller = "LudlController";
const char* g_Shutter = "LudlShutter";
const char* g_Wheel = "LudlWheel";
const char* g_Ludl_Reset = "Reset";
const char* g_Ludl_Version = "Version";
const char* g_Ludl_TransmissionDelay = "TransmissionDelay";
const char* g_Ludl_Axis_Id = "LudlSingleAxisName";
const char* g_CommandLevelHigh = "High";
const char* g_CommandLevelLow = "Low";
const char* g_Device_Number_Shutter = "LudlDeviceNumberShutter";
const char* g_Device_Address_Wheel = "LudlDeviceNumberWheel";
const char* g_Shutter_Number = "LudlShutterNumber";
const char* g_Wheel_Number = "LudlWheelNumber";
const char* g_Device_ID = "ID";

MM::MMTime g_AnswerTimeOut(2000000);

using namespace std;

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_Controller, MM::GenericDevice, "Ludl Controller");
   RegisterDevice(g_Shutter, MM::ShutterDevice, "Ludl Shutter");
   RegisterDevice(g_Wheel, MM::StateDevice, "Ludl Filter Wheel");
   //RegisterDevice(g_XYStageDeviceName, MM::XYStageDevice, "XY Stage");
   //RegisterDevice(g_StageDeviceName, MM::StageDevice, "Single Axis Stage");
}                                                                            
                                                                             
MODULE_API MM::Device* CreateDevice(const char* deviceName)                  
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_Controller) == 0)
   {
      return new Hub();                           
   }
   else if (strcmp(deviceName, g_Shutter) == 0 )
   {
      return new Shutter();
   }
   else if (strcmp(deviceName, g_Wheel) == 0)
   {
      return new Wheel();
   }
   /*
   if (strcmp(deviceName, g_XYStageDeviceName) == 0)                         
   {                                                                         
      XYStage* pXYStage = new XYStage();                                     
      return pXYStage;                                                       
   }
   else if (strcmp(deviceName, g_StageDeviceName) == 0)
   {
      return new Stage();
   }
   */

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

// TODO: fold the following globals into a class

/*
 * Global Utility function for communication with the controller
 */
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

/*
 * Change command level, 65 = high command set, 66 = low command set
 */
int changeCommandLevel(MM::Device& device, MM::Core& core, const char* commandLevel)
{
   unsigned char level;
   if (strcmp(commandLevel, g_CommandLevelHigh) == 0)
      level = 65;
   else if (strcmp(commandLevel, g_CommandLevelLow) == 0)
      level = 66;
   else 
      return ERR_INVALID_COMMAND_LEVEL;
   
   const unsigned cmdLen = 2;
   unsigned char cmd[cmdLen];
   cmd[0] = 255;
   cmd[1] = level;
   int ret = core.WriteToSerial(&device, port_.c_str(), cmd, cmdLen);
   if (ret !=DEVICE_OK)
      return ret;

   return DEVICE_OK;
}
  
///////////////////////////////////////////////////////////////////////////////
// Ludl Hub
///////////////////////////////////////////////////////////////////////////////

Hub::Hub() :
   transmissionDelay_(10),
   initialized_(false)
{
   InitializeDefaultErrorMessages();

   // custom error messages:
   SetErrorText(ERR_INVALID_COMMAND_LEVEL, "Internal error: command level is either 65 (high) or 66(low)");
   SetErrorText(ERR_NO_ANSWER, "No answer from the controller.  Is it connected?");
   
   // pre-initialization properties

   // Port:
   CPropertyAction* pAct = new CPropertyAction(this, &Hub::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

}

Hub::~Hub()
{
   Shutdown();
}

void Hub::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_Controller);
}

bool Hub::Busy()
{
   return false;
}

int Hub::Initialize()
{
   // We get the version of the controller and the RConfig output in high level command mode and then switch to low level
   clearPort(*this, *GetCoreCallback(), port_.c_str());

   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, g_Controller, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "Ludl Controller (Mac 2000/5000)", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   ret = changeCommandLevel(*this,  *GetCoreCallback(), g_CommandLevelHigh);
   if (DEVICE_OK != ret)
      return ret;

   // Version of the controller:
   const char* cm = "VER";
   ret = SendSerialCommand(port_.c_str(), cm, "\r");
   if (ret != DEVICE_OK) 
      return ret;

   // Read out result
   string result;
   ret = GetSerialAnswer(port_.c_str(), "\n", result);
   if (ret != DEVICE_OK) 
      return ret;
   if (result.length() < 2) {
      // First wait 2 seconds (we assume we are in low level mode and have to purge the buffer)
      CDeviceUtils::SleepMs(2000);
      // if we get no answer, try setting the controller to high command level and retry
      ret = changeCommandLevel(*this,  *GetCoreCallback(), g_CommandLevelHigh);
      if (ret != DEVICE_OK)
         return ret;
      // try getting version again (TODO: refactor this!)
      // Version of the controller:
      const char* cm = "VER";
      ret = SendSerialCommand(port_.c_str(), cm, "\r");
      if (ret != DEVICE_OK) 
         return ret;

      ret = GetSerialAnswer(port_.c_str(), "\n", result);
      if (ret != DEVICE_OK) 
         return ret;
      if (result.length() < 2) {
         // no answer, 
         return ERR_NO_ANSWER;
      }
   }

   // Create read-only property with version info
   ret = CreateProperty(g_Ludl_Version, result.c_str(), MM::String, true);
   if (ret != DEVICE_OK) 
      return ret;

   // there is still a :A in the buffer that we should read away:
   ret = GetSerialAnswer(port_.c_str(), "\n", result);
   if (ret != DEVICE_OK) 
      return ret;

   // Interface to a hard reset of the controller
   CPropertyAction* pAct = new CPropertyAction(this, &Hub::OnReset);
   ret = CreateProperty(g_Ludl_Reset, "Operate", MM::String, false, pAct, true);
   if (ret != DEVICE_OK)
      return ret;
   AddAllowedValue(g_Ludl_Reset, "Operate");
   AddAllowedValue(g_Ludl_Reset, "Reset");
   
   // Transmission Delay, Delay between chars send by controller (number * 0.5 msec)
   pAct = new CPropertyAction(this, &Hub::OnTransmissionDelay);
   ret = CreateProperty(g_Ludl_TransmissionDelay, "0", MM::Integer, false, pAct, true);
   if (ret != DEVICE_OK)
      return ret;

   // Get description of attached modules
   const char* cmd = "Rconfig";
   ret = SendSerialCommand(port_.c_str(), cmd, "\r");
   if (ret != DEVICE_OK) 
      return ret;

   // Read out result
   result = "";
   ret = GetSerialAnswer(port_.c_str(), ":", result);
   if (ret != DEVICE_OK) 
      return ret;
   if (result.length() < 1)
      return ERR_NO_ANSWER;
   // Looks like there are controllers that start with ':'
   if (result.length() == 1) 
   {
      result = "";
      ret = GetSerialAnswer(port_.c_str(), ":", result);
      if (ret != DEVICE_OK) 
         return ret;
      if (result.length() < 1)
         return ERR_NO_ANSWER;
   }

   LogMessage (result.c_str());

   result = "A module with this ID was not found.  Use any of the following Dev. Addresses: \n" + result;
   SetErrorText(ERR_MODULE_NOT_FOUND, result.c_str());

   // There is still a line ending that should be read away:
   ret = GetSerialAnswer(port_.c_str(), "\n", result);
   if (ret != DEVICE_OK) 
      return ret;
   
   // switch controller back to low level command mode
   ret = changeCommandLevel(*this,  *GetCoreCallback(), g_CommandLevelLow);
   if (ret != DEVICE_OK)
      return ret;
   // TODO: check that we can talk to the controller in low level mode!

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

int Hub::Shutdown()
{
   if (initialized_)
      initialized_ = false;

   return DEVICE_OK;
}


//////////////// Action Handlers (Hub) /////////////////

int Hub::OnPort(MM::PropertyBase* pProp, MM::ActionType pAct)
{
   if (pAct == MM::BeforeGet)
   {
      pProp->Set(port_.c_str());
   }
   else if (pAct == MM::AfterSet)
   {
      if (initialized_)
      {
         pProp->Set(port_.c_str());
         return ERR_PORT_CHANGE_FORBIDDEN;
      }
      pProp->Get(port_);
   }
   return DEVICE_OK;
}

int Hub::OnConfig(MM::PropertyBase* pProp, MM::ActionType pAct)
{
   if (pAct == MM::BeforeGet)
   {
      pProp->Set("Operate");
   }
   else if (pAct == MM::AfterSet)
   {
      // TODO check that we were initialized
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
int Hub::OnReset(MM::PropertyBase* pProp, MM::ActionType pAct)
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
         unsigned char cmd[2];
         cmd[0]=255; cmd[1]=82;
         int ret = WriteToComPort(port_.c_str(),  cmd, 2);
         if (ret !=DEVICE_OK)
            return ret;
         // TODO: Do we need to wait until the reset is completed?
      }
   }
   return DEVICE_OK;
}
         
// Sets the transmission delay that th econtroller uses in between bytes
int Hub::OnTransmissionDelay(MM::PropertyBase* pProp, MM::ActionType pAct)
{
   if (pAct == MM::BeforeGet)
   {
      /*
      string cmd = "TRXDEL";
      int ret = SendSerialCommand(port_.c_str(), cmd.c_str(), "\r");
      if (ret !=DEVICE_OK)
         return ret;

      string result;
      ret = GetSerialAnswer(port_.c_str(), "\n", result);
      if (ret != DEVICE_OK) 
         return ret;
      if (result[1] != 'A') 
         return ERR_UNRECOGNIZED_ANSWER;

      // tokenize on space
      stringstream ss(result);
      string buf;
      vector<string> tokens;
      while (ss >> buf)
         tokens.push_back(buf);
      if (tokens.size() > 1)
         transmissionDelay_ = atoi(tokens.at(1).c_str());
      // TODO: check that this value makes sense...
      // */
      // Not clear if we can get the transmissiondelay_ in low level command mode
      pProp->Set((double)transmissionDelay_);
   }
   else if (pAct == MM::AfterSet)
   {
      double tmp;
      pProp->Get(tmp);
      if (tmp < 1) 
         tmp = 1;
      if (tmp > 255)
         tmp = 255;
      unsigned char cmd[3];
      cmd[0]=255; cmd[1]=82; cmd[2]=(unsigned char)tmp;
      int ret = WriteToComPort(port_.c_str(),  cmd, 3);
      if (ret !=DEVICE_OK)
         return ret;
      transmissionDelay_ = (int) tmp;

      /*
      // Read away the confirmation. 
      string resp;
      ret = GetSerialAnswer(port_.c_str(), "\n", resp);
      if (ret != DEVICE_OK) 
         return ret;
      if (resp.substr(0,2) != ":A")
         return ERR_COMMAND_FAILED;
      */
   }
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Wheel 
/////// 

Wheel::Wheel() : 
   name_(g_Wheel), 
   initialized_(false), 
   deviceNumber_(1),
   moduleId_(17),
   wheelNumber_(1),
   numPos_(6)
{
   InitializeDefaultErrorMessages();
   SetErrorText(ERR_UNRECOGNIZED_ANSWER, "Unrecognized answer recived from the device");
   SetErrorText(ERR_INVALID_ID, "Ludl Module ID is a number between 0 and 20");
   SetErrorText(ERR_INVALID_WHEEL_NUMBER, "There can be 1 or 2 filterwheels in each Ludl module");
   SetErrorText(ERR_INVALID_WHEEL_POSITION, "Invalid filter position requested.  Positions 1 through 6 are valid");
   SetErrorText(ERR_UNKNOWN_POSITION, "Position out of range");
   SetErrorText(ERR_SET_POSITION_FAILED, "Set position failed.");
   SetErrorText(ERR_WHEEL_HOME_FAILED, "Failed to Home the filter wheel.");
   SetErrorText(ERR_MODULE_NOT_FOUND, "The Ludl controller does not have a module with the specified ID.  Check the debug output for module IDs present");

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "Ludl filter wheel adapter", MM::String, true);

   // Wheel Number
   // -----
   CPropertyAction* pAct = new CPropertyAction (this, &Wheel::OnWheelNumber);
   CreateProperty(g_Wheel_Number, "1", MM::Integer, false, pAct, true);
   AddAllowedValue(g_Wheel_Number, "1");
   AddAllowedValue(g_Wheel_Number, "2");

   // device ID for the EFILS
   pAct = new CPropertyAction (this, &Wheel::OnID);
   CreateProperty(g_Device_ID, "17", MM::Integer, false, pAct, true);
   AddAllowedValue(g_Device_ID, "17");
   AddAllowedValue(g_Device_ID, "18");
   AddAllowedValue(g_Device_ID, "19");
   AddAllowedValue(g_Device_ID, "20");
   AddAllowedValue(g_Device_ID, "21");

}

Wheel::~Wheel()
{
   wheelsUsed[moduleId_ - 17][wheelNumber_ - 1] = false;
   Shutdown();
}

void Wheel::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int Wheel::Initialize()
{
   // Make sure that the combination of Shutter# and Device# is unique
   if (wheelsUsed[moduleId_ - 17][wheelNumber_ - 1])
      return ERR_WHEEL_USED;
   else
      wheelsUsed[moduleId_ - 17][wheelNumber_ - 1] = true;

   // Make sure that a valid number was used in the constructor:
   if (wheelNumber_ < 1 || wheelNumber_ > 2)
      return ERR_INVALID_WHEEL_NUMBER;

   // Make sure the controller is in low level mode
   int ret = changeCommandLevel(*this,  *GetCoreCallback(), g_CommandLevelLow);
   if (ret != DEVICE_OK)
      return ret;

   // set property list
   // -----------------
   
   // State
   // -----
   CPropertyAction* pAct = new CPropertyAction (this, &Wheel::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "1", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // Label
   // -----
   pAct = new CPropertyAction (this, &CStateBase::OnLabel);
   ret = CreateProperty(MM::g_Keyword_Label, "Undefined", MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // create default positions and labels
   const int bufSize = 64;
   char buf[bufSize];
   for (int i=0; i< numPos_; i++)
   {
      snprintf(buf, bufSize, "Filter-%d", i + 1);
      SetPositionLabel(i, buf);
   }
   
   if (Busy())
      return ERR_MODULE_NOT_FOUND;

   if (HomeWheel() != DEVICE_OK)
      return ret;

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

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

bool Wheel::Busy()
{
   clearPort(*this, *GetCoreCallback(), port_.c_str());
   // This checks all device in the module, not only our Wheel
   const unsigned char cmd[3] = {moduleId_, 63, 58}; 
   int ret = WriteToComPort(port_.c_str(), cmd, 3);
   if (ret != DEVICE_OK) // Bad, but what else can we do?
      return false;

   unsigned long read = 0;
   unsigned char reply;
   MM::MMTime startTime = GetCurrentMMTime();
   do {
      ret = ReadFromComPort(port_.c_str(), &reply, 1, read);
      if (ret != DEVICE_OK)
         return true;
   }
   while(read==0 && ((GetCurrentMMTime() - startTime) < g_AnswerTimeOut));

   if (read != 1) // Error detected: we are probably busy
      return true;

   if (reply == 'b')
      return false;
   else if (reply == 'B')
      return true;
   
   // error
   return true;
}

/**
 * Homes the wheel
 */
int Wheel::HomeWheel()
{
   unsigned char cmd[4];
   cmd[0] = moduleId_; cmd[2] = 0; cmd[3] = 58;
   if (wheelNumber_ == 1)
      cmd[1] = 72; 
   else if (wheelNumber_ ==2)
      cmd[1] = 71; 
   int ret = WriteToComPort(port_.c_str(), cmd, 4);

   if (ret !=DEVICE_OK)
      return ret;

   // Give some time for the homing to complete:
   CDeviceUtils::SleepMs(1500);
   printf ("Done homing\n");

   return DEVICE_OK;
}
   

/**
 * Sets wheel position
 */
int Wheel::SetWheelPosition(int position)
{
   unsigned char cmd[4];
   cmd[0] = moduleId_; cmd[2] = 0; cmd[3] = 58;
   if (wheelNumber_ == 1) {
      switch (position) {
         case 0: cmd[1] = 49; break;
         case 1: cmd[1] = 50; break;
         case 2: cmd[1] = 51; break;
         case 3: cmd[1] = 52; break;
         case 4: cmd[1] = 53; break;
         case 5: cmd[1] = 54; break;
      }
   } else if (wheelNumber_ == 1) {
      switch (position) {
         case 0: cmd[1] = 33; break;
         case 1: cmd[1] = 64; break;
         case 2: cmd[1] = 35; break;
         case 3: cmd[1] = 36; break;
         case 4: cmd[1] = 37; break;
         case 5: cmd[1] = 94; break;
      }
   }
   int ret = WriteToComPort(port_.c_str(), cmd, 4);
   if (ret !=DEVICE_OK)
      return ret;

   // pos_ = position;
   
   return DEVICE_OK;
}

/**
 * Ask controller about filter position
 */
int Wheel::GetWheelPosition(int& position)
{
   unsigned char cmd[4];
   cmd[0] = moduleId_; cmd[2] = 1; cmd[3] = 58;
   if (wheelNumber_ == 1)
      cmd[1] = 97; 
   else if (wheelNumber_ ==2)
      cmd[1] = 98; 
   int ret = WriteToComPort(port_.c_str(), cmd, 4);
   if (ret !=DEVICE_OK)
      return ret;

   // For now, we use a cached value
   unsigned long read = 0;
   unsigned char reply;
   MM::MMTime startTime = GetCurrentMMTime();
   do {
      ret = ReadFromComPort(port_.c_str(), &reply, 1, read);
      if (ret != DEVICE_OK)
         return ret;
   }
   while(read==0 && ((GetCurrentMMTime() - startTime) < g_AnswerTimeOut));

   if (read != 1) // Error: most likely no answer from the port
      return DEVICE_SERIAL_INVALID_RESPONSE;

   // We use a zero-based state:
   position = reply - 1;

   printf ("Wheel GetPos: %lu %d %d \n", read, reply, position);

   return DEVICE_OK;
}


//////////////// Action Handlers (Wheel) /////////////////
//
int Wheel::OnID(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set((long)moduleId_);
   }
   else if (eAct == MM::AfterSet)
   {
      long id;
      pProp->Get(id);
      if (id >= 17 && id <= 21)
         moduleId_ = (unsigned char) id;
      else 
         return ERR_INVALID_ID;

   }
   return DEVICE_OK;
}

int Wheel::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      int pos;
      int ret = GetWheelPosition(pos);
      if (ret != DEVICE_OK)
         return ret;
      pProp->Set((long)pos);
   }
   else if (eAct == MM::AfterSet)
   {
      long pos;
      pProp->Get(pos);
      if ((pos >= 0) && (pos<numPos_))
         return SetWheelPosition(pos);
      else
         return ERR_INVALID_WHEEL_POSITION;
   }
   return DEVICE_OK;
}


int Wheel::OnDeviceAddress(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set((double)deviceNumber_);
   }
   else if (eAct == MM::AfterSet)
   {
      if (initialized_)
      {
         // revert
         pProp->Set((double)deviceNumber_);
         return ERR_DEVICE_CHANGE_NOT_ALLOWED;
      }
      long tmp;
      pProp->Get(tmp);
      // There are between a and 5 shutter/filterwheel controllers in a Mac5000
      if (tmp > 0  && tmp <= 5)
      {
         deviceNumber_ = tmp;
      }
      else
      {
         // return
         pProp->Set((long)deviceNumber_);
         return ERR_INVALID_DEVICE_NUMBER;
      }
   }

   return DEVICE_OK;
}

int Wheel::OnWheelNumber(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set((long)wheelNumber_);
   }
   else if (eAct == MM::AfterSet)
   {
      long tmp;
      pProp->Get(tmp);
      wheelNumber_ = tmp;
   }
   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// Shutter 
// ~~~~~~~

Shutter::Shutter() : 
   name_(g_Shutter), 
   shutterNumber_(1), 
   initialized_(false), 
   moduleId_(17)
{
   InitializeDefaultErrorMessages();
   SetErrorText(ERR_UNRECOGNIZED_ANSWER, "Unrecognized answer received from the device");
   SetErrorText(ERR_INVALID_ID, "Ludl Module ID is a number between 0 and 20");
   SetErrorText(ERR_INVALID_SHUTTER_STATE, "Shutter state can only be 0 or 1");
   SetErrorText(ERR_INVALID_SHUTTER_NUMBER, "There can be between 1 and 3 shutters in every Ludl module");
   SetErrorText(ERR_INVALID_DEVICE_NUMBER, "There can be between 1 and 5 shutter/filter wheel controllers in a MAC5000");
   SetErrorText(ERR_SHUTTER_COMMAND_FAILED, "Error while sending a command to the shutter");
   SetErrorText(ERR_DEVICE_CHANGE_NOT_ALLOWED, "Device number can not be changed");
   SetErrorText(ERR_SHUTTER_USED, "A shutter with this device and shutter number is already in use");
   SetErrorText(ERR_MODULE_NOT_FOUND, "The Ludl controller does not have a module with the specified ID.  Check the debug output for module IDs present");

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "Ludl shutter adapter", MM::String, true);

   // device ID for the EFILS
   CPropertyAction* pAct = new CPropertyAction (this, &Shutter::OnID);
   CreateProperty(g_Device_ID, "17", MM::Integer, false, pAct, true);
   AddAllowedValue(g_Device_ID, "17");
   AddAllowedValue(g_Device_ID, "18");
   AddAllowedValue(g_Device_ID, "19");
   AddAllowedValue(g_Device_ID, "20");
   AddAllowedValue(g_Device_ID, "21");

   // Shutter Number
   // -----
   pAct = new CPropertyAction (this, &Shutter::OnShutterNumber);
   CreateProperty(g_Shutter_Number, "1", MM::Integer, false, pAct, true);
   AddAllowedValue(g_Shutter_Number, "1");
   AddAllowedValue(g_Shutter_Number, "2");
   AddAllowedValue(g_Shutter_Number, "3");

   // UpdateStatus();

}

Shutter::~Shutter()
{
   shuttersUsed[moduleId_ - 17][shutterNumber_ - 1] = false;
   Shutdown();
}

void Shutter::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int Shutter::Initialize()
{
   // Make sure that the combination of Shutter# and Device# is unique
   if (shuttersUsed[moduleId_ - 17][shutterNumber_ - 1])
      return ERR_SHUTTER_USED;
   else
      shuttersUsed[moduleId_ - 17][shutterNumber_ - 1] = true;

   // Make sure that a valid number was used in the constructor:
   if (shutterNumber_ < 1 || shutterNumber_ > 3)
      return ERR_INVALID_SHUTTER_NUMBER;

   // Make sure the controller in in low levele mode
   int ret = changeCommandLevel(*this,  *GetCoreCallback(), g_CommandLevelLow);
   if (ret != DEVICE_OK)
      return ret;

   // set property list
   // -----------------
   
   // State
   // -----
   CPropertyAction* pAct = new CPropertyAction (this, &Shutter::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   AddAllowedValue(MM::g_Keyword_State, "0");
   AddAllowedValue(MM::g_Keyword_State, "1");

   /*
   // TODO: Figure out why Labels can not be added:
    
   // Label
   pAct = new CPropertyAction(this, &CStateBase::OnLabel);
   ret = CreateProperty(MM::g_Keyword_Label, "Undefined", MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   SetPositionLabel(0, "Closed");
   SetPositionLabel(1, "Open");

   // Delay
   // -----
   pAct = new CPropertyAction (this, &Shutter::OnDelay);
   ret = CreateProperty(MM::g_Keyword_Delay, "0.0", MM::Float, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   */

   if (Busy())
      return ERR_MODULE_NOT_FOUND;

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

//   openTimeUs_ = GetClockTicksUs();
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
   clearPort(*this, *GetCoreCallback(), port_.c_str());
   // This checks all device in the module, not only our Shutter
   const unsigned char cmd[3] = {moduleId_, 63, 58}; 
   int ret = WriteToComPort(port_.c_str(), cmd, 3);
   if (ret != DEVICE_OK) // This is bad, but what else can we do?
      return false;

   unsigned long read = 0;
   unsigned char reply;
   MM::MMTime startTime = GetCurrentMMTime();
   do {
      if (DEVICE_OK != ReadFromComPort(port_.c_str(), &reply, 1, read))
         return false;
   }
   while(read==0 && ((GetCurrentMMTime() - startTime) < g_AnswerTimeOut));

   if (ret != DEVICE_OK || (read != 1) ) // Error detected: we are probably busy
      return true;

   if (reply == 'b')
      return false;
   else if (reply == 'B')
      return true;
   
   // error
   return true;
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
   unsigned char cmd[4];
   cmd[0] = moduleId_; cmd[2] = 0; cmd[3] = 58;
   if (state) {
      switch (shutterNumber_) {
         case 1: cmd[1] = 74; break;
         case 2: cmd[1] = 76; break;
         case 3: cmd[1] = 79; break;
      }
   } else { 
      switch (shutterNumber_) {
         case 1: cmd[1] = 75; break;
         case 2: cmd[1] = 77; break;
         case 3: cmd[1] = 80; break;
      }
   }

   int ret = WriteToComPort(port_.c_str(), cmd, 4);
   if (ret !=DEVICE_OK)
      return ret;

   return DEVICE_OK;
}


/**
 * Check the state of the shutter.
 */
int Shutter::GetShutterPosition(bool& state)
{
   clearPort(*this, *GetCoreCallback(), port_.c_str());
   
   // request shutter status
   unsigned char cmd[4];
   cmd[0] = moduleId_; cmd[1] = 115; cmd[2] = 1; cmd[3] = 58;
   int ret = WriteToComPort(port_.c_str(), cmd, 4);
   if (ret !=DEVICE_OK)
      return ret;

   unsigned long read = 0;
   unsigned char reply;
   MM::MMTime startTime = GetCurrentMMTime();
   do {
      ret = ReadFromComPort(port_.c_str(), &reply, 1, read);
      if (ret != DEVICE_OK)
         return ret;
   }
   while(read==0 && ((GetCurrentMMTime() - startTime) < g_AnswerTimeOut));

   if (read != 1) // Error detected: we are probably busy
      return DEVICE_SERIAL_INVALID_RESPONSE;

   if (shutterNumber_ == 1)
      state = (reply & 4) != 0;
   else if (shutterNumber_ == 2)
      state = (reply & 8) != 0;
   else if (shutterNumber_ == 3)
      state = (reply & 16) != 0;

   return DEVICE_OK;
}

//////////////// Action Handlers (Shutter) /////////////////
//
int Shutter::OnID(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set((long)moduleId_);
   }
   else if (eAct == MM::AfterSet)
   {
      long id;
      pProp->Get(id);
      if (id >= 0 && id <= 20)
         moduleId_ = (unsigned char) id;
      else 
         return ERR_INVALID_ID;

      // To check that there is a controller with this ID, check whether the busy check works
      // Only do this check when we have an port
      /*
      if (port_.length() > 0) {
         ChangeCommandLevel(g_CommandLevelLow);
         clearPort(*this, *GetCoreCallback(), port_.c_str());
         const unsigned cmdLen = 3;
         char cmd[cmdLen];
         cmd[0] = moduleId_;
         cmd[1] = 63; // status
         cmd[2] = 58; // :
         int ret = WriteToComPort(port_.c_str(), cmd, cmdLen);
         //int ret = SendSerialCommand(port_.c_str(), reinterpret_cast<char*>(cmd), ":");
         if (ret != DEVICE_OK)
            return ret;

         string result;
         ret = GetSerialAnswer(port_.c_str(), ":", result);
         if (ret != DEVICE_OK) 
            return ret;

         if (result.size() < 1) 
            return ERR_MODULE_NOT_FOUND;

         if (!(result.c_str()[0] == 66 || result.c_str()[0] == 98))
            return ERR_MODULE_NOT_FOUND;
      }
      */
   }

   return DEVICE_OK;
}

int Shutter::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      bool open;
      int ret = GetShutterPosition(open);
      if (ret != DEVICE_OK)
         return ret;
      if (open)
         pProp->Set((long)1);
      else
         pProp->Set((long)0);
   }
   else if (eAct == MM::AfterSet)
   {
      long state;
      pProp->Get(state);
      if (state == 1)
         return SetShutterPosition(true);
      else if (state == 0)
         return SetShutterPosition(false);
      else
         return ERR_INVALID_SHUTTER_STATE;
   }
   return DEVICE_OK;
}

int Shutter::OnDeviceNumber(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set((long)deviceNumber_);
   }
   else if (eAct == MM::AfterSet)
   {
      if (initialized_)
      {
         // revert
         pProp->Set((long)deviceNumber_);
         return ERR_DEVICE_CHANGE_NOT_ALLOWED;
      }
      long tmp;
      pProp->Get(tmp);
      // There are between a and 5 shutter/filterwheel controllers in a Mac5000
      if (tmp > 0  && tmp <= 5)
      {
         deviceNumber_ = (unsigned int)tmp;
      }
      else
      {
         // return
         pProp->Set((long)deviceNumber_);
         return ERR_INVALID_DEVICE_NUMBER;
      }
   }

   return DEVICE_OK;
}

int Shutter::OnShutterNumber(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set((double)shutterNumber_);
   }
   else if (eAct == MM::AfterSet)
   {
      double shutterNumber;
      pProp->Get(shutterNumber);
      shutterNumber_ = (int) shutterNumber;
   }
   return DEVICE_OK;
}

      

///////////////////////////////////////////////////////////////////////////////
// XYStage
//

/**
 * XYStage - two axis stage device.
 */
XYStage::XYStage() :
   initialized_(false), 
   stepSizeUm_(0.1), 
   speed_(2500.0),
   startSpeed_(500.0),
   accel_(75),
   idX_(1), 
   idY_(2)
{
   InitializeDefaultErrorMessages();

   // create pre-initialization properties
   // ------------------------------------
   // NOTE: pre-initialization properties contain parameters which must be defined fo
   // proper startup

   // Name, read-only (RO)
   CreateProperty(MM::g_Keyword_Name, g_StageDeviceName, MM::String, true);

   // Description, RO
   CreateProperty(MM::g_Keyword_Description, "Ludl XY stage driver adapter", MM::String, true);

}

XYStage::~XYStage()
{
   Shutdown();
}

///////////////////////////////////////////////////////////////////////////////
// XYStage methods required by the API
///////////////////////////////////////////////////////////////////////////////

void XYStage::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_StageDeviceName);
}

/**
 * Performs device initialization.
 * Additional properties can be defined here too.
 */
int XYStage::Initialize()
{
   // Step size
   // ---------
   CPropertyAction* pAct = new CPropertyAction (this, &XYStage::OnStepSize);
   int ret = CreateProperty("StepSize", "1.0", MM::Float, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // Speed (in um/sec)
   // -----
   pAct = new CPropertyAction (this, &XYStage::OnSpeed);
   // TODO: get current speed from the controller
   ret = CreateProperty("Speed", "2500.0", MM::Float, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // Initial Speed (in um/sec) (i.e. lowest speed
   // -----
   pAct = new CPropertyAction (this, &XYStage::OnStartSpeed);
   // TODO: get current speed from the controller
   ret = CreateProperty("StartSpeed", "500.0", MM::Float, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // Accel (Acceleration is a number between 1 and 255, lower numbers give shorter ramp time, i.e. faster acceleration
   // -----
   pAct = new CPropertyAction (this, &XYStage::OnAccel);
   // TOD: get current Aceeleration from the controller
   ret = CreateProperty("Acceleration", "100", MM::Float, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   
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

/**
 * Returns true if either of the axis (X or Y) are still moving.
 */
bool XYStage::Busy()
{
   
   clearPort(*this, *GetCoreCallback(), port_.c_str());
   // format the command
   const char* cmd = "STATUS X Y";

   // send command
   // int ret = SendSerialCommand(port_.c_str(), cmd.str().c_str(), "\r");
   // 
   int ret = SendSerialCommand(port_.c_str(), cmd, "\r");
   if (ret != DEVICE_OK)
   {
      ostringstream os;
      os << "SendSerialCommand failed in XYStage::Busy, error code:" << ret;
      this->LogMessage(os.str().c_str(), false);
      // return false; // can't write, continue just so that we can read an answer in case write succeeded even though we received an error
   }

   // Poll for the response to the Busy status request
   unsigned char status=0;
   unsigned long read=0;
   int numTries=0, maxTries=400;
   long pollIntervalMs = 5;
   this->LogMessage("Starting read in XY-Stage Busy\n", true);
   do {
      ret = ReadFromComPort(port_.c_str(), &status, 1, read);
      if (ret != DEVICE_OK)
      {
         ostringstream os;
         os << "ReadFromComPort failed in XYStage::Busy, error code:" << ret;
         this->LogMessage(os.str().c_str(), false);
         return false; // Error, let's pretend all is fine
      }
      numTries++;
      CDeviceUtils::SleepMs(pollIntervalMs);
   }
   while(read==0 && numTries < maxTries); // keep trying up to 2 sec
   ostringstream os;
   os << "Tried reading "<< numTries << " times, and finally read " << read << " chars";
   this->LogMessage(os.str().c_str(), true);

   if (status == 'B')
      return true;
   else
      return false;
}

/**
 * Sets position in um.
 */
int XYStage::SetPositionUm(double x, double y)
{
   long xSteps = (long) (x / stepSizeUm_ + 0.5);
   long ySteps = (long) (y / stepSizeUm_ + 0.5);
   
   return SetPositionSteps(xSteps, ySteps);
}

/**
 * Gets current position in um.
 */
int XYStage::GetPositionUm(double& x, double& y)
{
   long xSteps, ySteps;
   int ret = GetPositionSteps(xSteps, ySteps);
   if (ret != DEVICE_OK)
      return ret;

   x = xSteps * stepSizeUm_;
   y = ySteps * stepSizeUm_;

   return DEVICE_OK;
}

/**
 * Sets position in steps.
 */
int XYStage::SetPositionSteps(long x, long y)
{
   // format the command
   ostringstream cmd;
   cmd << "MOVE ";
   cmd << "X=" << x << " ";
   cmd << "Y=" << y ;

   // TODO: what if we are busy???

   int ret = SendSerialCommand(port_.c_str(), cmd.str().c_str(), "\r");
   if (ret != DEVICE_OK)
      return ret;
  
   string resp;
   ret = GetSerialAnswer(port_.c_str(), "\n", resp);
   if (ret != DEVICE_OK) 
      return ret;
   if (resp.length() < 1)
      return ERR_NO_ANSWER;

   // parse the answer and return the proper error code
   // TODO...

   istringstream is(resp);
   string outcome;
   is >> outcome;

   if (outcome.compare(":A") == 0)
      return DEVICE_OK; // success!

   // figure out the error code
   int code;
   is >> code;
   return code;
}
  
/**
 * Returns current position in steps.
 */
int XYStage::GetPositionSteps(long& x, long& y)
{
   PurgeComPort(port_.c_str());
   
   // format the command
   const char* cmd = "WHERE X Y";

   // TODO: what if we are busy???
   int ret = SendSerialCommand(port_.c_str(), cmd, "\r");
   if (ret != DEVICE_OK)
      return ret;
  
   string resp;
   ret = GetSerialAnswer(port_.c_str(), "\n", resp);
   if (ret != DEVICE_OK) 
      return ret;
   if (resp.length() < 1)
      return ERR_NO_ANSWER;

   // parse the answer and return the proper error code
   // TODO...

   istringstream is(resp);
   string outcome;
   is >> outcome;

   if (outcome.compare(":A") == 0)
   {
      is >> x;
      is >> y;
      return DEVICE_OK; // success!
   }

   // figure out the error code
   int code;
   is >> code;
   return code;
}

/**
 * Defines current position as origin (0,0) coordinate.
 * TODO: implement!!!
 */
int XYStage::SetOrigin()
{
   PurgeComPort(port_.c_str());

   // format the command
   const char* cmd = "HERE X=0 Y=0";

   // TODO: what if we are busy???
   int ret = SendSerialCommand(port_.c_str(), cmd, "\r");
   if (ret != DEVICE_OK)
      return ret;
  
   //TODO parse the answer and return error code
   string resp;
   ret = GetSerialAnswer(port_.c_str(), "\n", resp);

   return DEVICE_OK;
}


int XYStage::Home()
{
   PurgeComPort(port_.c_str());

   // format the command
   const char* cmd = "HOME X Y";

   // TODO: what if we are busy???
   int ret = SendSerialCommand(port_.c_str(), cmd, "\r");
   if (ret != DEVICE_OK)
      return ret;
  
   //TODO:  set a separate time-out since it can take a long time for the home command to complete
   unsigned char status=0;
   unsigned long read=0;
   int numTries=0, maxTries=200;
   long pollIntervalMs = 50;
   this->LogMessage("Starting read in XY-Stage HOME\n", true);
   do {
      ret = ReadFromComPort(port_.c_str(), &status, 1, read);
      if (ret != DEVICE_OK)
      {
         ostringstream os;
         os << "ReadFromComPort failed in XYStage::Busy, error code:" << ret;
         this->LogMessage(os.str().c_str(), false);
         return false; // Error, let's pretend all is fine
      }
      numTries++;
      CDeviceUtils::SleepMs(pollIntervalMs);
   }
   while(read==0 && numTries < maxTries); // keep trying up to 2 sec
   ostringstream os;
   os << "Tried reading "<< numTries << " times, and finally read " << read << " chars";
   this->LogMessage(os.str().c_str());

   //TODO parse the answer and return error code
   string resp;
   ret = GetSerialAnswer(port_.c_str(), "\n", resp);
   
   return DEVICE_OK;
}


int XYStage::Stop()
{
   PurgeComPort(port_.c_str());
   
   // format the command
   const char* cmd = "HALT";

   // TODO: what if we are busy???
   int ret = SendSerialCommand(port_.c_str(), cmd, "\r");
   if (ret != DEVICE_OK)
      return ret;
  
   string resp;
   ret = GetSerialAnswer(port_.c_str(), "\n", resp);
   //TODO parse the answer and return error code
   
   return DEVICE_OK;
}


/**
 * Returns the stage position limits in um.
 * TODO: implement!!!
 */
int XYStage::GetLimits(double& /*xMin*/, double& /*xMax*/, double& /*yMin*/, double& /*yMax*/)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

///////////////////////////////////////////////////////////////////////////////
// Internal, helper methods
///////////////////////////////////////////////////////////////////////////////

/**
 * Sends a specified command to the controller
 */
int XYStage::ExecuteCommand(const string& cmd, string& response)
{
   // send command
  // PurgeComPort(port_.c_str());
   clearPort(*this, *GetCoreCallback(), port_.c_str());
   if (DEVICE_OK != SendSerialCommand(port_.c_str(), cmd.c_str(), "\r"))
      return DEVICE_SERIAL_COMMAND_FAILED;

   int ret = GetSerialAnswer(port_.c_str(), "\n", response);
   if (ret != DEVICE_OK) 
      return ret;
   if (response.length() < 1)
      return ERR_NO_ANSWER;
   /*
   //
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
   while(!pLF && (GetClockTicksUs() - startTime) / 1000.0 < answerTimeOutMs_);

   if (!pLF)
      return DEVICE_SERIAL_TIMEOUT;

   response = answer;
   */
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
// Handle changes and updates to property values.
///////////////////////////////////////////////////////////////////////////////

int XYStage::OnStepSize(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // TODO: modify this method to query the step size
      // from the controller
      pProp->Set(stepSizeUm_);
   }
   else if (eAct == MM::AfterSet)
   {
      double stepSize;
      pProp->Get(stepSize);
      if (stepSize <=0.0)
      {
         pProp->Set(stepSizeUm_);
         return ERR_INVALID_STEP_SIZE;
      }
      stepSizeUm_ = stepSize;
   }

   return DEVICE_OK;
}

/*
 * Speed as returned by device is in pulses per second
 * We convert that to um per second using the factor stepSizeUm)
 */
int XYStage::OnSpeed(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      string resp;
      int ret = ExecuteCommand("SPEED X Y", resp);
      if (ret != DEVICE_OK)
         return ret;
      if (resp.substr(0,2) != ":A")
         return ERR_COMMAND_FAILED;
      // tokenize on space:
      stringstream ss(resp);
      string buf;
      vector<string> tokens;
      while (ss >> buf)
         tokens.push_back(buf);
      long speedX = atol(tokens.at(1).c_str());
      // long speedY = atol(tokens.at(2).c_str());
      // TODO, deal with situation when these two are not the same
      speed_ = speedX * stepSizeUm_;
      pProp->Set(speed_);
   }
   else if (eAct == MM::AfterSet)
   {
      double uiSpeed; // Start Speed in um/sec
      long speed; // Speed in rotations per sec
      pProp->Get(uiSpeed);
      speed = (long)(uiSpeed / stepSizeUm_);
      if (speed < 85 ) speed = 85;
      if (speed > 2764800) speed = 276480;
      string resp;
      ostringstream os;
      os << "SPEED X=" << speed << " Y=" << speed;
      int ret = ExecuteCommand(os.str().c_str(), resp);
      if (ret != DEVICE_OK)
         return ret;
      if (resp.substr(0,2) != ":A")
         return ERR_COMMAND_FAILED;
      speed_ = speed * stepSizeUm_;
   }
   return DEVICE_OK;
}

int XYStage::OnStartSpeed(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      string resp;
      int ret = ExecuteCommand("STSPEED X Y", resp);
      if (ret != DEVICE_OK)
         return ret;
      if (resp.substr(0,2) != ":A")
         return ERR_COMMAND_FAILED;
      // tokenize on space:
      stringstream ss(resp);
      string buf;
      vector<string> tokens;
      while (ss >> buf)
         tokens.push_back(buf);
      long speedX = atol(tokens.at(1).c_str());
      // long speedY = atol(tokens.at(2).c_str());
      // TODO, deal with situation when these two are not the same
      startSpeed_ = speedX * stepSizeUm_;
      pProp->Set(startSpeed_);
   }
   else if (eAct == MM::AfterSet)
   {
      double uiStartSpeed; // this is the Start Speed in um/sec
      long startSpeed; // startSpeed in rotations per sec
      pProp->Get(uiStartSpeed);
      startSpeed = (long)(uiStartSpeed / stepSizeUm_);
      if (startSpeed < 1000 ) startSpeed = 1000;
      if (startSpeed > 2764800) startSpeed = 276480;
      string resp;
      ostringstream os;
      os << "STSPEED X=" << startSpeed << " Y=" << startSpeed;
      int ret = ExecuteCommand(os.str().c_str(), resp);
      if (ret != DEVICE_OK)
         return ret;
      if (resp.substr(0,2) != ":A")
         return ERR_COMMAND_FAILED;
      startSpeed_ = startSpeed * stepSizeUm_;
   }
   return DEVICE_OK;
}

int XYStage::OnAccel(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      string resp;
      int ret = ExecuteCommand("ACCEL X Y", resp);
      if (ret != DEVICE_OK)
         return ret;
      if (resp.substr(0,2) != ":A")
         return ERR_COMMAND_FAILED;
      // tokenize on space:
      stringstream ss(resp);
      string buf;
      vector<string> tokens;
      while (ss >> buf)
         tokens.push_back(buf);
      long accelX = atol(tokens.at(1).c_str());
      // long accelY = atol(tokens.at(2).c_str());
      // TODO, deal with situation when these two are not the same
      accel_ = accelX;
      pProp->Set(accel_);
   }
   else if (eAct == MM::AfterSet)
   {
      long accel;
      pProp->Get(accel);
      if (accel < 1 ) accel = 1;
      if (accel > 255) accel = 255;
      string resp;
      ostringstream os;
      os << "ACCEL X=" << accel << " Y=" << accel;
      int ret = ExecuteCommand(os.str().c_str(), resp);
      if (ret != DEVICE_OK)
         return ret;
      if (resp.substr(0,2) != ":A")
         return ERR_COMMAND_FAILED;
      accel_ = accel;
   }
   return DEVICE_OK;
}


int XYStage::OnIDX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set((long)idX_);
   }
   else if (eAct == MM::AfterSet)
   {
      long id;
      pProp->Get(id);
      idX_ = (unsigned) id;
   }

   return DEVICE_OK;
}

int XYStage::OnIDY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set((long)idY_);
   }
   else if (eAct == MM::AfterSet)
   {
      long id;
      pProp->Get(id);
      idY_ = (unsigned) id;
   }

   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// Stage
///////////////////////////////////////////////////////////////////////////////

/**
 * Single axis stage.
 */
Stage::Stage() :
   initialized_(false),
   stepSizeUm_(0.1)
   //port_("Undefined"),
{
   InitializeDefaultErrorMessages();

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, g_StageDeviceName, MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "Ludl stage driver adapter", MM::String, true);

   // Axis ID
   id_ = "Z";
   CPropertyAction* pAct = new CPropertyAction(this, &Stage::OnID);
   CreateProperty(g_Ludl_Axis_Id, id_.c_str(), MM::String, false, pAct, true); 
   AddAllowedValue(g_Ludl_Axis_Id, "X");
   AddAllowedValue(g_Ludl_Axis_Id, "Y");
   AddAllowedValue(g_Ludl_Axis_Id, "Z");
   AddAllowedValue(g_Ludl_Axis_Id, "R");
   AddAllowedValue(g_Ludl_Axis_Id, "T");
   AddAllowedValue(g_Ludl_Axis_Id, "F");
   AddAllowedValue(g_Ludl_Axis_Id, "A");
   AddAllowedValue(g_Ludl_Axis_Id, "B");
   AddAllowedValue(g_Ludl_Axis_Id, "C");
}

Stage::~Stage()
{
   Shutdown();
}

///////////////////////////////////////////////////////////////////////////////
// Stage methods required by the API
///////////////////////////////////////////////////////////////////////////////

void Stage::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_StageDeviceName);
}

int Stage::Initialize()
{
   // set property list
   // -----------------
   
   // Position
   // --------
   CPropertyAction* pAct = new CPropertyAction (this, &Stage::OnStepSize);
   int ret = CreateProperty("StepSize", "1.0", MM::Float, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // Autofocus
   // ---------
   if (id_ == "Z")
   {
      // this property only makes sense to the ASI Z stage
      pAct = new CPropertyAction (this, &Stage::OnAutofocus);
      ret = CreateProperty("Autofocus", "5", MM::Integer, false, pAct);
      if (ret != DEVICE_OK)
         return ret;
   }

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;
   return DEVICE_OK;
}

int Stage::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

bool Stage::Busy()
{
   ostringstream os;
   os << " STATUS " << id_;

   int ret = SendSerialCommand(port_.c_str(), os.str().c_str(), "\r");
   if (ret != DEVICE_OK)
   {
      ostringstream os;
      os << "SendSerialCommand failed in Stage::Busy, error code:" << ret;
      this->LogMessage(os.str().c_str(), false);
      // return false; // can't write, continue just so that we can read an answer in case write succeeded even though we received an error
   }

   // Poll for the response to the Busy status request
   unsigned char status=0;
   unsigned long read=0;
   int numTries=0, maxTries=400;
   long pollIntervalMs = 5;
   this->LogMessage("Starting read in Stage Busy\n", true);
   do {
      ret = ReadFromComPort(port_.c_str(), &status, 1, read);
      if (ret != DEVICE_OK)
      {
         ostringstream os;
         os << "ReadFromComPort failed in Stage::Busy, error code:" << ret;
         this->LogMessage(os.str().c_str(), false);
         return false; // Error, let's pretend all is fine
      }
      numTries++;
      CDeviceUtils::SleepMs(pollIntervalMs);
   }
   while(read==0 && numTries < maxTries); // keep trying up to 2 sec
   ostringstream ds;
   ds << "Tried reading "<< numTries << " times, and finally read " << read << " chars";
   this->LogMessage(ds.str().c_str(), true);

   if (status == 'B')
      return true;
   else
      return false;
}

int Stage::SetPositionUm(double pos)
{
   long steps = (long) (pos / stepSizeUm_ + 0.5);
   
   ostringstream os;
   os << id_ << " SetPosition() " << pos;
   this->LogMessage(os.str().c_str());

   return SetPositionSteps(steps);
}

int Stage::GetPositionUm(double& pos)
{
   long steps;
   int ret = GetPositionSteps(steps);
   if (ret != DEVICE_OK)
      return ret;
   pos = steps * stepSizeUm_;

   ostringstream os;
   os << id_ << " GetPosition() " << pos;
   this->LogMessage(os.str().c_str());
   return DEVICE_OK;
}
  
int Stage::SetPositionSteps(long pos)
{
   // format the command
   ostringstream cmd;
   cmd << "MOVE ";
   cmd << id_ << "=" << pos;

   // TODO: what if we are busy???
   int ret = SendSerialCommand(port_.c_str(), cmd.str().c_str(), "\r");
   if (ret != DEVICE_OK)
      return ret;
  
   string resp;
   ret = GetSerialAnswer(port_.c_str(), "\n", resp);
   if (ret != DEVICE_OK) 
      return ret;
   if (resp.length() < 1)
      return ERR_NO_ANSWER;

   // parse the answer and return the proper error code
   // TODO...

   istringstream is(resp);
   string answer;
   is >> answer;

   if (answer.compare(":A") == 0)
      return DEVICE_OK; // success!

   // figure out the error code
   this->LogMessage(answer.c_str());
   istringstream os(resp.substr(2));
   int code;
   os >> code;
   return code;
}
  
int Stage::GetPositionSteps(long& steps)
{
   // format the command
   ostringstream cmd;
   cmd << "WHERE " << id_;

   // TODO: what if we are busy???
   int ret = SendSerialCommand(port_.c_str(), cmd.str().c_str(), "\r");
   if (ret != DEVICE_OK)
      return ret;

   string resp;
   ret = GetSerialAnswer(port_.c_str(), "\n", resp);
   if (ret != DEVICE_OK) 
      return ret;

   if (resp.length() < 1)
      return ERR_NO_ANSWER;

   // parse the answer and return the proper error code
   // TODO...

   istringstream is(resp);
   string outcome;
   is >> outcome;

   if (outcome.compare(":A") == 0)
   {
      is >> steps;
      return DEVICE_OK; // success!
   }

   // figure out the error code
   int code;
   is >> code;

   return code;
}
  
int Stage::SetOrigin()
{
   return DEVICE_UNSUPPORTED_COMMAND;
}
 
int Stage::GetLimits(double& /*min*/, double& /*max*/)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

///////////////////////////////////////////////////////////////////////////////
// Helper, internal methods
///////////////////////////////////////////////////////////////////////////////

int Stage::ExecuteCommand(const string& cmd, string& response)
{
   // send command
   PurgeComPort(port_.c_str());
   //if (DEVICE_OK != WriteToComPort(port_.c_str(), cmd.c_str(), (unsigned) cmd.length()))
   if (DEVICE_OK != SendSerialCommand(port_.c_str(), cmd.c_str(), "\r"))
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
      if (DEVICE_OK != ReadFromComPort(port_.c_str(), reinterpret_cast<unsigned char *> (answer + curIdx), bufLen - curIdx, read))
         return DEVICE_SERIAL_COMMAND_FAILED;
      curIdx += read;

      // look for the LF
      pLF = strstr(answer, "\n");
      if (pLF)
         *pLF = 0; // terminate the string
   }
   while(!pLF && (GetClockTicksUs() - startTime) / 1000.0 < 1000);

   if (!pLF)
      return DEVICE_SERIAL_TIMEOUT;

   response = answer;
   return DEVICE_OK;
}

/**
 * This may be problematic since it makes sense and works only for a Z-stage.
 * WARNING: it explicitly uses Z stage name and may not be what the user intended to do.
 */
int Stage::Autofocus(long param)
{
   // format the command
   ostringstream cmd;
   cmd << "AF Z=";
   cmd << param ;

   string resp;
   int ret = ExecuteCommand(cmd.str(), resp);
   if (ret != DEVICE_OK)
      return ret;

   istringstream is(resp);
   string outcome;
   is >> outcome;

   if (outcome.compare(":A") == 0)
      return DEVICE_OK; // success!

   // figure out the error code
   int code;
   is >> code;
   return code;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int Stage::OnStepSize(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(stepSizeUm_);
   }
   else if (eAct == MM::AfterSet)
   {
      double stepSize;
      pProp->Get(stepSize);
      if (stepSize <=0.0)
      {
         pProp->Set(stepSizeUm_);
         return ERR_INVALID_STEP_SIZE;
      }
      stepSizeUm_ = stepSize;
   }

   return DEVICE_OK;
}

int Stage::OnID(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      //const char* id = id_;
      pProp->Set(id_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      string id;
      pProp->Get(id_);
      // Only allow axis that we know:
      if (id == "X" || id == "Y" || id == "Z" || id == "R" || id == "T" || id == "F")
         id_ = id;
   }

   return DEVICE_OK;
}

int Stage::OnAutofocus(MM::PropertyBase* pProp, MM::ActionType eAct)
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

