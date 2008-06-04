///////////////////////////////////////////////////////////////////////////////
// FILE:          Vincent.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Vincent Uniblitz controller adapter
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
// AUTHOR:        Nico Stuurman, 02/27/2006
//
// NOTE:          Based on Ludl controller adpater by Nenad Amodaj
//

#ifdef WIN32
#include <windows.h>
#define snprintf _snprintf 
#endif

#include "Vincent.h"
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>
#include <iostream>

const char* g_VincentD1Name = "Vincent-D1";
const char* g_VincentD3Name = "Vincent-D3";
const char* g_VMMClose = "Close";
const char* g_VMMOpen = "Open";
const char* g_VMMTrigger = "Trigger";
const char* g_VMMReset = "Reset";
const char* g_Ch1 = "Ch. #1";
const char* g_Ch2 = "Ch. #2";
const char* g_Ch3 = "Ch. #3";
const char* g_All = "All";

using namespace std;


///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   AddAvailableDeviceName(g_VincentD1Name, "Vincent controller VMM-D1/T1 and D122" );
   AddAvailableDeviceName(g_VincentD3Name, "Vincent controller VMM-D3/D4" );
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_VincentD1Name) == 0)
   {
      VincentD1* pVMM1 = new VincentD1();
      return pVMM1;
   }

   if (strcmp(deviceName, g_VincentD3Name) == 0)
   {
      VincentD3* pVMM3 = new VincentD3();
      return pVMM3;
   }
   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}


///////////////////////////////////////////////////////////////////////////////
// VincentD1 device
///////////////////////////////////////////////////////////////////////////////

VincentD1::VincentD1() :
   initialized_(false),
   port_("Undefined"),
   lastCommand_("Undefined"),
   address_("x"),
   closingTimeMs_(35.0),
   openingTimeMs_(35.0),
   shutterName_("A"),
   changedTime_(0.0)
{
   InitializeDefaultErrorMessages();

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, g_VincentD1Name, MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "Vincent D1 controller adapter", MM::String, true);

   // Port
   CPropertyAction* pAct = new CPropertyAction (this, &VincentD1::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

   // Address
   pAct = new CPropertyAction (this, &VincentD1::OnAddress);
   CreateProperty("Address", "x", MM::String, false, pAct, true);
   vector<string> addresses;
   addresses.push_back("x");
   addresses.push_back("0");
   addresses.push_back("1");
   addresses.push_back("2");
   addresses.push_back("3");
   addresses.push_back("4");
   addresses.push_back("5");
   addresses.push_back("6");
   addresses.push_back("7");
   int nRet = SetAllowedValues("Address", addresses);
   //assert(nRet != 0);

   // Options are: x (all), 0-7
   //              64       128 + #*16 

   // Add Properties (and set/get methods) for shutterName_, closingTime_, and openingTime_
   pAct = new CPropertyAction (this, &VincentD1::OnShutterName);
   nRet=CreateProperty("Shutter A or B", "A", MM::String, false, pAct, true);
   vector<string> shutternames;
   shutternames.push_back("A");
   shutternames.push_back("B");
   nRet = SetAllowedValues("Shutter A or B",shutternames);
  // assert(nRet != 0);

   pAct = new CPropertyAction (this, &VincentD1::OnClosingTime);
   nRet=CreateProperty("Time to close (ms)", "A", MM::Float, false, pAct, true);
 //  assert(nRet != 0);
   
   pAct = new CPropertyAction (this, &VincentD1::OnOpeningTime);
   nRet=CreateProperty("Time to open (ms)", "A", MM::Float, false, pAct, true);
//   assert(nRet != 0);

}

VincentD1::~VincentD1()
{
   Shutdown();
}

void VincentD1::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_VincentD1Name);
}

int VincentD1::Initialize()
{
   // set property list
   // -----------------
   
   // command
   CPropertyAction *pAct = new CPropertyAction (this, &VincentD1::OnCommand);
   int nRet=CreateProperty("Command", g_VMMClose, MM::String, false, pAct);

   vector<string> commands;
   commands.push_back(g_VMMClose);
   commands.push_back(g_VMMOpen);
   commands.push_back(g_VMMTrigger);
   commands.push_back(g_VMMReset);
   nRet = SetAllowedValues("Command", commands);
   if (nRet != DEVICE_OK)
      return nRet;
   // Options are: Open, Close, Trigger, Reset, Open B, Close B
   //               0     1       2       3      4         5
   
   // Set timer for the Busy signal
    changedTime_ = GetCurrentMMTime();

   int ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   SetOpen(false); // initialize to closed shutter

   initialized_ = true;
   return DEVICE_OK;
}

int VincentD1::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

/*
 * This device does not give any feedback
 * So, start timer after the last command and check whether a predetermined time has elapsed since - NS
 */
bool VincentD1::Busy()
{
    MM::MMTime interval = GetCurrentMMTime() - changedTime_;

   // check whether the shutter had enough time to open or close after the command was issued
   if (lastCommand_ == g_VMMOpen) {
      if (interval > (MM::MMTime)(openingTimeMs_*1000) ) 
         return false;
      else
         return true;
   } else if (lastCommand_ == g_VMMClose) {
      if (interval > (MM::MMTime)(closingTimeMs_) ) 
         return false;
      else
         return true;
   }
   // we should never be here, better not to block
   return false;
}


int VincentD1::ExecuteCommand(const string& cmd)
{
   // calculate what the command should be (See Vincent docs):
   int command_Dec=0;
   if (*address_.c_str()=='x') {
      command_Dec=64;
   } else {
      int numerator=(int)*address_.c_str() - 48;
      assert(numerator>=0);
      assert(numerator<256);
      command_Dec=128 + (numerator * 16);
   }
   if (cmd==g_VMMOpen) {
      if (shutterName_=="A")
         command_Dec+=0;
      else if (shutterName_=="B")
         command_Dec+=4;
   } else if (cmd == g_VMMClose) {
      if (shutterName_=="A")
         command_Dec+=1;
      else if (shutterName_=="B")
         command_Dec+=5;
   } else if (cmd==g_VMMTrigger) {
      command_Dec+=2;
   } else if (cmd==g_VMMReset) {
      command_Dec+=3;
   }

   char c = static_cast<char>(command_Dec);
   const char *charCmd = &c;

   // send command
   PurgeComPort(port_.c_str());
   if (DEVICE_OK != WriteToComPort(port_.c_str(), (unsigned char*)charCmd, 1))
      return DEVICE_SERIAL_COMMAND_FAILED;

   // Remember when this command was issued, and what it was:
   changedTime_ = GetCurrentMMTime();
   lastCommand_ = cmd;

   // This device does not answer, so at this point we are done

   // Even though we have no clue, we'll say all is fine
   return DEVICE_OK;
}


int VincentD1::SetOpen(bool open)
{
   string pos;
   if (open)
      pos = g_VMMOpen;
   else
      pos = g_VMMClose;
   return SetProperty("Command", pos.c_str());
}

int VincentD1::GetOpen(bool& open)
{
   char buf[MM::MaxStrLength];
   int ret = GetProperty("Command", buf);
   if (ret != DEVICE_OK)
      return ret;
   if (strcmp(buf, g_VMMOpen) == 0) {
      open = true;
   } else {
      open = false;
   }

   return DEVICE_OK;
}

int VincentD1::Fire(double /*deltaT*/)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////
/*
 * Sets the Serial Port to be used.
 * Should be called before initialization
 */
int VincentD1::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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


int VincentD1::OnCommand(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(command_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(command_);
      return ExecuteCommand(command_);
   }

   return DEVICE_OK;
}

/**
 * Determines which address (x, 0-7) this Vincent controller uses
 */
int VincentD1::OnAddress(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(address_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      // How do we check whether this value is OK?  Can pProp set a private variable? - NS
      pProp->Get(address_);
   }

   return DEVICE_OK;
}

/**
 * Determines which shutter (A or B) to address
 */
int VincentD1::OnShutterName(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(shutterName_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      // How do we check whether this value is OK?  Can pProp set a private variable? - NS
      pProp->Get(shutterName_);
   }
   return DEVICE_OK;
}


/**
 * Sets and gets the time needed for the shutter to open after the serial command is sent
 */
int VincentD1::OnOpeningTime(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(openingTimeMs_);
   }
   else if (eAct == MM::AfterSet)
   {
      // How do we check whether this value is OK?  Can pProp set a private variable? - NS
      pProp->Get(openingTimeMs_);
   }
   return DEVICE_OK;
}


/**
 * Sets and gets the time needed for the shutter to close after the serial command is sent
 */
int VincentD1::OnClosingTime(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(closingTimeMs_);
   }
   else if (eAct == MM::AfterSet)
   {
      // How do we check whether this value is OK?  Can pProp set a private variable? - NS
      pProp->Get(closingTimeMs_);
   }
   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// VincentD3 device
///////////////////////////////////////////////////////////////////////////////

VincentD3::VincentD3() :
   initialized_(false),
   port_("Undefined"),
   lastCommand_("Undefined"),
   address_("x"),
   closingTimeMs_(35.0),
   openingTimeMs_(35.0),
   shutterName_(g_Ch1),
   changedTime_(0.0)
{
   InitializeDefaultErrorMessages();

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, g_VincentD3Name, MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "Vincent D3 controller adapter", MM::String, true);

   // Port
   CPropertyAction* pAct = new CPropertyAction (this, &VincentD3::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

   // Address
   pAct = new CPropertyAction (this, &VincentD3::OnAddress);
   CreateProperty("Address", "x", MM::String, false, pAct, true);
   vector<string> addresses;
   addresses.push_back("x");
   addresses.push_back("0");
   addresses.push_back("1");
   addresses.push_back("2");
   addresses.push_back("3");
   addresses.push_back("4");
   addresses.push_back("5");
   addresses.push_back("6");
   addresses.push_back("7");
   int nRet = SetAllowedValues("Address", addresses);
   //assert(nRet != 0);

   // Options are: x (all), 0-7
   //              64       128 + #*16 

   // Add Properties (and set/get methods) for shutterName_, closingTime_, and openingTime_
   pAct = new CPropertyAction (this, &VincentD3::OnShutterName);
   nRet=CreateProperty("Channel #", g_Ch1, MM::String, false, pAct, true);
   vector<string> shutternames;
   shutternames.push_back(g_Ch1);
   shutternames.push_back(g_Ch2);
   shutternames.push_back(g_Ch3);
   shutternames.push_back(g_All);
   nRet = SetAllowedValues("Channel #",shutternames);
   //assert(nRet != 0);
   pAct = new CPropertyAction (this, &VincentD3::OnClosingTime);
   nRet=CreateProperty("Time to close (ms)", "35.0", MM::Float, false, pAct, true);
   //assert(nRet != 0);
   
   pAct = new CPropertyAction (this, &VincentD3::OnOpeningTime);
   nRet=CreateProperty("Time to open (ms)", "35.0", MM::Float, false, pAct, true);
   //assert(nRet != 0);

}

VincentD3::~VincentD3()
{
   Shutdown();
}

void VincentD3::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_VincentD3Name);
}

int VincentD3::Initialize()
{
   // set property list
   // -----------------
   
   // command
   CPropertyAction *pAct = new CPropertyAction (this, &VincentD3::OnCommand);
   int nRet=CreateProperty("Command", g_VMMClose, MM::String, false, pAct);

   vector<string> commands;
   commands.push_back(g_VMMClose);
   commands.push_back(g_VMMOpen);
   nRet = SetAllowedValues("Command", commands);
   if (nRet != DEVICE_OK)
      return nRet;
   // Options are: Open, Close
   //               0     1  
   
   int ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   // Set timer for the Busy signal
   changedTime_ = GetCurrentMMTime();

   initialized_ = true;
   return DEVICE_OK;
}

int VincentD3::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

/*
 * This device does not give any feedback
 * So, start timer after the last command and check whether a predetermined time has elapsed since - NS
 */
bool VincentD3::Busy()
{
   MM::MMTime interval = GetCurrentMMTime() - changedTime_;

   // check whether the shutter had enough time to open or close after the command was issued
   if (lastCommand_ == g_VMMOpen) {
      if (interval > (MM::MMTime)(openingTimeMs_*1000) ) 
         return false;
      else
         return true;
   } else if (lastCommand_ == g_VMMClose) {
      if (interval > (MM::MMTime)(closingTimeMs_) ) 
         return false;
      else
         return true;
   }
   // we should never be here, better not to block
   return false;
}


int VincentD3::ExecuteCommand(const string& cmd)
{
   // calculate what the command should be (See Vincent docs):
   int command_Dec=0;
   if (*address_.c_str()=='x') {
      command_Dec=64;
   } else {
      int numerator=(int)*address_.c_str() - 48;
      assert(numerator>=0);
      assert(numerator<256);
      command_Dec=128 + (numerator * 16);
   }
   if (cmd==g_VMMOpen) {
      if (shutterName_==g_Ch1)
         command_Dec+=0;
      else if (shutterName_==g_Ch2)
         command_Dec+=2;
      else if (shutterName_==g_Ch3)
         command_Dec+=4;
      else if (shutterName_==g_All)
         command_Dec+=6;
   } else if (cmd == g_VMMClose) {
      if (shutterName_==g_Ch1)
         command_Dec+=1;
      else if (shutterName_==g_Ch2)
         command_Dec+=3;
      else if (shutterName_==g_Ch3)
         command_Dec+=5;
      else if (shutterName_==g_All)
         command_Dec+=7;
   }

   char c = static_cast<char>(command_Dec);
   const char *charCmd = &c;

   // send command
   PurgeComPort(port_.c_str());
   if (DEVICE_OK != WriteToComPort(port_.c_str(), (unsigned char*)charCmd, 1))
      return DEVICE_SERIAL_COMMAND_FAILED;

   // Remember when this command was issued, and what it was:
   changedTime_ = GetCurrentMMTime();
   lastCommand_ = cmd;

   // This device does not answer, so at this point we are done

   // Even though we have no clue, we'll say all is fine
   return DEVICE_OK;
}


int VincentD3::SetOpen(bool open)
{
   string pos;
   if (open)
      pos = g_VMMOpen;
   else
      pos = g_VMMClose;
   return SetProperty("Command", pos.c_str());
}

int VincentD3::GetOpen(bool& open)
{
   char buf[MM::MaxStrLength];
   int ret = GetProperty("Command", buf);
   if (ret != DEVICE_OK)
      return ret;
   if (strcmp(buf,g_VMMOpen) == 0) {
      open = true;
   } else {
      open = false;
   }

   return DEVICE_OK;
}

int VincentD3::Fire(double /*deltaT*/)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////
/*
 * Sets the Serial Port to be used.
 * Should be called before initialization
 */
int VincentD3::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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


int VincentD3::OnCommand(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(command_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(command_);
      return ExecuteCommand(command_);
   }

   return DEVICE_OK;
}

/**
 * Determines which address (x, 0-7) this Vincent controller uses
 */
int VincentD3::OnAddress(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(address_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      // How do we check whether this value is OK?  Can pProp set a private variable? - NS
      pProp->Get(address_);
   }

   return DEVICE_OK;
}

/**
 * Determines which shutter (1-3, All) to address
 */
int VincentD3::OnShutterName(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(shutterName_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      // How do we check whether this value is OK?  Can pProp set a private variable? - NS
      pProp->Get(shutterName_);
   }
   return DEVICE_OK;
}


/**
 * Sets and gets the time needed for the shutter to open after the serial command is sent
 */
int VincentD3::OnOpeningTime(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(openingTimeMs_);
   }
   else if (eAct == MM::AfterSet)
   {
      // How do we check whether this value is OK?  Can pProp set a private variable? - NS
      pProp->Get(openingTimeMs_);
   }
   return DEVICE_OK;
}


/**
 * Sets and gets the time needed for the shutter to close after the serial command is sent
 */
int VincentD3::OnClosingTime(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(closingTimeMs_);
   }
   else if (eAct == MM::AfterSet)
   {
      // How do we check whether this value is OK?  Can pProp set a private variable? - NS
      pProp->Get(closingTimeMs_);
   }
   return DEVICE_OK;
}
