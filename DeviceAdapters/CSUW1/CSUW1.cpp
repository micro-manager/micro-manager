///////////////////////////////////////////////////////////////////////////////
// FILE:       CSUW1.cpp
// PROJECT:    MicroManage
// SUBSYSTEM:  DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:
// Yokogawa CSUW1 controller adapter
//
// COPYRIGHT:     University of California, San Francisco, 2006
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
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.  

// Based on CSUX1 adapter by Nico Stuurman
//
// Based on NikonTE2000 controller adapter by Nenad Amodaj
//

#ifdef WIN32
#include <windows.h>
#define snprintf _snprintf 
#endif

#include "CSUW1.h"
#include "CSUW1Hub.h"
#include <cstdio>
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>
#include <iostream>

const char* g_CSUW1Hub = "CSUW1-Hub";
const char* g_CSUW1FilterWheel = "CSUW1-Filter Wheel";
const char* g_CSUW1Dichroic = "CSUW1-Dichroic Mirror";
const char* g_CSUW1Shutter = "CSUW1-Shutter";
const char* g_CSUW1DriveSpeed= "CSUW1-Drive Speed";
const char* g_CSUW1BrightField = "CSUW1-Bright Field";
const char* g_CSUW1Disk = "CSUW1-Disk";
const char* g_CSUW1Port = "CSUW1-Port";
const char* g_CSUW1Aperture = "CSUW1-Aperture";
const char* g_CSUW1Frap = "CSUW1-FRAP";
const char* g_CSUW1Magnifier = "CSUW1-Magnifier";
const char* g_CSUW1NIRShutter = "CSUW1-NIR Shutter";

using namespace std;

CSUW1Hub g_hub;

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_CSUW1Hub, MM::GenericDevice, "CSUW1 Hub (Needed for CSUW1. 115200bps)");
   RegisterDevice(g_CSUW1FilterWheel, MM::StateDevice, "Filter Wheel");
   RegisterDevice(g_CSUW1Dichroic, MM::StateDevice, "Dichroic Mirror");
   RegisterDevice(g_CSUW1Shutter, MM::ShutterDevice, "Shutter");
   RegisterDevice(g_CSUW1DriveSpeed, MM::GenericDevice, "Drive Speed");
   RegisterDevice(g_CSUW1BrightField, MM::StateDevice, "Bright Field");
   RegisterDevice(g_CSUW1Disk, MM::StateDevice, "Disk");
   RegisterDevice(g_CSUW1Port, MM::StateDevice, "Port");
   RegisterDevice(g_CSUW1Aperture, MM::StateDevice, "Aperture");
   RegisterDevice(g_CSUW1Frap, MM::StateDevice, "FRAP");
   RegisterDevice(g_CSUW1Magnifier, MM::StateDevice, "Magnifier");
   RegisterDevice(g_CSUW1NIRShutter, MM::ShutterDevice, "NIR Shutter");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_CSUW1Hub) == 0)
   {
      return new Hub();
   }
   else if (strcmp(deviceName, g_CSUW1FilterWheel) == 0 )
   {
      return new FilterWheel();
   }
   else if (strcmp(deviceName, g_CSUW1Dichroic) == 0 )
   {
      return new Dichroic();
   }
   else if (strcmp(deviceName, g_CSUW1Shutter) == 0 )
   {
      return new Shutter();
   }
   else if (strcmp(deviceName, g_CSUW1DriveSpeed) == 0 )
   {
      return new DriveSpeed();
   }
   else if (strcmp(deviceName, g_CSUW1BrightField) == 0 )
   {
      return new BrightField();
   }
   else if (strcmp(deviceName, g_CSUW1Disk) == 0 )
   {
      return new Disk();
   }
   else if (strcmp(deviceName, g_CSUW1Port) == 0 )
   {
      return new Port();
   }
   else if (strcmp(deviceName, g_CSUW1Aperture) == 0 )
   {
      return new Aperture();
   }
   else if (strcmp(deviceName, g_CSUW1Frap) == 0 )
   {
      return new Frap();
   }
   else if (strcmp(deviceName, g_CSUW1Magnifier) == 0 )
   {
      return new Magnifier();
   }
   else if (strcmp(deviceName, g_CSUW1NIRShutter) == 0 )
   {
      return new NIRShutter();
   }
   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}


///////////////////////////////////////////////////////////////////////////////
// CSUW1 Hub
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
   CDeviceUtils::CopyLimitedString(name, g_CSUW1Hub);
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
   int ret = CreateProperty(MM::g_Keyword_Name, g_CSUW1Hub, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "Yokogawa CSUW1 hub", MM::String, true);
   if (DEVICE_OK != ret)
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
// CSUW1 Filter Wheel
///////////////////////////////////////////////////////////////////////////////
FilterWheel::FilterWheel () :
   posMoved_ (0),
   wheelNr_ (1),
   initialized_ (false),
   numPos_ (10),
   pos_ (1),
   name_ (g_CSUW1FilterWheel)
{
   InitializeDefaultErrorMessages();

   lastMoveTime_ = GetCurrentMMTime();

   // Wheel Number
   CPropertyAction* pAct = new CPropertyAction (this, &FilterWheel::OnWheelNr);
   CreateProperty("WheelNumber", "1", MM::Integer, false, pAct, true);
   AddAllowedValue("WheelNumber", "1");
   AddAllowedValue("WheelNumber", "2");
   // Todo: Add custom messages
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
   ret = CreateProperty(MM::g_Keyword_Description, g_CSUW1FilterWheel, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // State
   CPropertyAction* pAct = new CPropertyAction (this, &FilterWheel::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct); 
   if (ret != DEVICE_OK) 
      return ret; 

   // create default positions and labels
   pAct = new CPropertyAction (this, &CStateBase::OnLabel);                  
   ret = CreateProperty(MM::g_Keyword_Label, "Undefined", MM::String, false, pAct);
   if (ret != DEVICE_OK)                                                     
      return ret;                                                            
   SetPositionLabel(0, "Filter-1");
   SetPositionLabel(1, "Filter-2");
   SetPositionLabel(2, "Filter-3");
   SetPositionLabel(3, "Filter-4");
   SetPositionLabel(4, "Filter-5");
   SetPositionLabel(5, "Filter-6");
   SetPositionLabel(6, "Filter-7");
   SetPositionLabel(7, "Filter-8");
   SetPositionLabel(8, "Filter-9");
   SetPositionLabel(9, "Filter-10");

   // Speed
   pAct = new CPropertyAction (this, &FilterWheel::OnSpeed);
   ret = CreateProperty("Speed", "2", MM::Integer, false, pAct); 
   if (ret != DEVICE_OK) 
      return ret; 
   AddAllowedValue("Speed", "0");
   AddAllowedValue("Speed", "1");
   AddAllowedValue("Speed", "2");
   AddAllowedValue("Speed", "3");

   // Although we compute an appropriate delay, we allow the user to set an
   // extra delay to wait for vibrations to sibside.
   EnableDelay(true);

   ret = UpdateStatus();
   if (ret != DEVICE_OK) 
      return ret; 

   initialized_ = true;

   return DEVICE_OK;
}

bool FilterWheel::Busy()
{
   MM::MMTime elapsed = GetCurrentMMTime() - lastMoveTime_;
   long msPerPosition;
   switch (speed_) {
   case 3:
      msPerPosition = 40;
      break;
   case 2:
      msPerPosition = 66;
      break;
   case 1:
      msPerPosition = 100;
      break;
   case 0:
   default:
      msPerPosition = 400;
      break;
   }
   long waitTimeMs = (long) (posMoved_ * msPerPosition + GetDelayMs());
   return elapsed.getMsec() < waitTimeMs;
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
      long pos;
      int ret = g_hub.GetFilterWheelPosition(*this, *GetCoreCallback(), wheelNr_, pos);
      if (ret != DEVICE_OK)
         return ret;
      pProp->Set(pos);
      pos_ = pos;
   }
   else if (eAct == MM::AfterSet)
   {
      long pos;
      pProp->Get(pos);
      int ret = g_hub.SetFilterWheelPosition(*this, *GetCoreCallback(), wheelNr_, pos);
      if (ret == DEVICE_OK) {
         lastMoveTime_ = GetCurrentMMTime();
         // lousy logic to calculate how many positions we move to calculate busy time
         posMoved_ = abs(pos_ - pos);
         if (posMoved_ > 5)
            posMoved_ = 10 - posMoved_;
         pos_ = pos;
         pProp->Set(pos_);
         return DEVICE_OK;
      }
      else
         return  ret;
   }
   return DEVICE_OK;
}

int FilterWheel::OnSpeed(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   long speed;
   if (eAct == MM::BeforeGet)
   {
      int ret = g_hub.GetFilterWheelSpeed(*this, *GetCoreCallback(), wheelNr_, speed);
      if (ret != DEVICE_OK)
         return ret;
      pProp->Set(speed);
      speed_ = speed;
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(speed);
      int ret = g_hub.SetFilterWheelSpeed(*this, *GetCoreCallback(), wheelNr_, speed);
      if (ret == DEVICE_OK) {
         speed_ = speed;
         pProp->Set(speed_);
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
      pProp->Set(wheelNr_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(wheelNr_);
   }
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// CSUW1 Dichroic
///////////////////////////////////////////////////////////////////////////////
Dichroic::Dichroic () :
   initialized_ (false),
   pos_ (0),
   name_ (g_CSUW1Dichroic),
   numPos_ (3)
{
   InitializeDefaultErrorMessages();

   // Todo: Add custom messages
   SetErrorText(ERR_NEGATIVE_ANSWER, "Negative answer received from the confocal controller");
}

Dichroic::~Dichroic ()
{
   Shutdown();
}

void Dichroic::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int Dichroic::Initialize()
{
   printf("Initializing Filter\n");
   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "CSUW1 Dichroics", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // State
   CPropertyAction* pAct = new CPropertyAction (this, &Dichroic::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct); 
   if (ret != DEVICE_OK) 
      return ret; 

   AddAllowedValue(MM::g_Keyword_State, "0");
   AddAllowedValue(MM::g_Keyword_State, "1");
   AddAllowedValue(MM::g_Keyword_State, "2");

   // Label                                                                  
   pAct = new CPropertyAction (this, &CStateBase::OnLabel);                  
   ret = CreateProperty(MM::g_Keyword_Label, "Undefined", MM::String, false, pAct);        
   if (ret != DEVICE_OK)                                                     
      return ret;                                                            
                                                                             
   // TODO: Obtain labels from the scanhead
   // create default positions and labels
   SetPositionLabel(0, "Dichroic-1");
   SetPositionLabel(1, "Dichroic-2");
   SetPositionLabel(2, "Dichroic-3");

   ret = UpdateStatus();
   if (ret != DEVICE_OK) 
      return ret; 

   initialized_ = true;

   return DEVICE_OK;
}


bool Dichroic::Busy()
{
   return false;
}

int Dichroic::Shutdown()
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

int Dichroic::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      long csuPos;
      int ret = g_hub.GetDichroicPosition(*this, *GetCoreCallback(), csuPos);
      if (ret != DEVICE_OK)
         return ret;
      pos_ = csuPos;
      pProp->Set(pos_);
   }
   else if (eAct == MM::AfterSet)
   {
      long pos; 
      pProp->Get(pos);
      if (pos == pos_)
         return DEVICE_OK;

      int ret = g_hub.SetDichroicPosition(*this, *GetCoreCallback(), pos);
      if (ret != DEVICE_OK) {
         return  ret;
      }
      pos_ = pos;
   }
   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// CSUW1 Shutter
///////////////////////////////////////////////////////////////////////////////
Shutter::Shutter () :
   initialized_ (false),
   name_ (g_CSUW1Shutter),
   isOpen_ (false)
{
   InitializeDefaultErrorMessages();
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
   ret = CreateProperty(MM::g_Keyword_Description, "CSUW1 Shutter", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Check current state of shutter:
   ret = g_hub.GetShutterPosition(*this, *GetCoreCallback(), isOpen_);
   if (DEVICE_OK != ret)
      return ret;

   // State
   CPropertyAction* pAct = new CPropertyAction (this, &Shutter::OnState);
   ret = CreateProperty(MM::g_Keyword_State, isOpen_ ? "Open" : "Closed", MM::String, false, pAct);
   if (ret != DEVICE_OK) 
      return ret; 

   AddAllowedValue(MM::g_Keyword_State, "Closed");
   AddAllowedValue(MM::g_Keyword_State, "Open");

   EnableDelay();

   changedTime_ = GetCurrentMMTime();

   ret = UpdateStatus();
   if (ret != DEVICE_OK) 
      return ret; 

   initialized_ = true;

   return DEVICE_OK;
}

bool Shutter::Busy()
{
   MM::MMTime interval = GetCurrentMMTime() - changedTime_;
   if (interval < (1000.0 * GetDelayMs() ))
      return true;

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
   changedTime_ = GetCurrentMMTime();
   int ret = g_hub.SetShutterPosition(*this, *GetCoreCallback(), open);
   if (ret != DEVICE_OK)
      return ret;
   isOpen_ = open;
   return DEVICE_OK;
}

int Shutter::GetOpen(bool &open)
{

   // Check current state of shutter: (this might not be necessary, since we cash ourselves)
   int ret = g_hub.GetShutterPosition(*this, *GetCoreCallback(), isOpen_);
   if (DEVICE_OK != ret)
      return ret;
   open = isOpen_;
   return DEVICE_OK;
}

int Shutter::Fire(double /*deltaT*/)
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
      pProp->Set(isOpen_ ? "Open" : "Closed");
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

///////////////////////////////////////////////////////////////////////////////
// CSUW1 DriveSpeed
///////////////////////////////////////////////////////////////////////////////
DriveSpeed::DriveSpeed () :
   initialized_ (false),
   name_ (g_CSUW1DriveSpeed)
{
   InitializeDefaultErrorMessages();
}

DriveSpeed::~DriveSpeed ()
{
   Shutdown();
}

void DriveSpeed::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int DriveSpeed::Initialize()
{
   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "CSUW1 DriveSpeed", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Speed
   CPropertyAction* pAct = new CPropertyAction (this, &DriveSpeed::OnSpeed);
   ret = CreateProperty(MM::g_Keyword_State, "4000", MM::Integer, false, pAct); 
   if (ret != DEVICE_OK) 
      return ret; 
   SetPropertyLimits(MM::g_Keyword_State, 1500, 4000);

   // Disk On/Off
   pAct = new CPropertyAction(this, &DriveSpeed::OnRun);
   ret = CreateProperty("Run", "On", MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   AddAllowedValue("Run", "On");
   AddAllowedValue("Run", "Off");

   ret = UpdateStatus();
   if (ret != DEVICE_OK) 
      return ret; 

   initialized_ = true;

   return DEVICE_OK;
}


bool DriveSpeed::Busy()
{
   return false;
}

int DriveSpeed::Shutdown()
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

int DriveSpeed::OnSpeed(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      return DEVICE_OK;
   }
   else if (eAct == MM::AfterSet)
   {
      long speed;
      pProp->Get(speed);
      int ret = g_hub.SetDriveSpeed(*this, *GetCoreCallback(), speed);
      if (ret != DEVICE_OK) {
         return ret;
      }
   }
   return DEVICE_OK;
}

/*
 * Switch Drive On and Off
 */
int DriveSpeed::OnRun(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // We can not query the device, so use our cashed value
      if (running_)
         pProp->Set("On");
      else
         pProp->Set("Off");
   }
   else if (eAct == MM::AfterSet)
   {
      std::string running;
      pProp->Get(running);
      if (running == "Off")
         running_ = false;
      else
         running_ = true;
      int ret = g_hub.RunDisk(*this, *GetCoreCallback(), running_);
      if (ret != DEVICE_OK)
         return ret;
   }
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// CSUW1 Bright Field
///////////////////////////////////////////////////////////////////////////////
BrightField::BrightField () :
   initialized_ (false),
   name_ (g_CSUW1BrightField),
   numPos_ (2)
{
   InitializeDefaultErrorMessages();
}

BrightField::~BrightField ()
{
   Shutdown();
}

void BrightField::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int BrightField::Initialize()
{
   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "CSUW1 Bright Field", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // State
   CPropertyAction* pAct = new CPropertyAction (this, &BrightField::OnState);
   ret = CreateProperty("BrightFieldPort", "Confocal", MM::String, false, pAct); 
   if (ret != DEVICE_OK) 
      return ret; 
   AddAllowedValue("BrightFieldPort", "Confocal");
   AddAllowedValue("BrightFieldPort", "Bright Field");

   SetPositionLabel(0, "Confocal");
   SetPositionLabel(1, "Bright Field");

   ret = UpdateStatus();
   if (ret != DEVICE_OK) 
      return ret; 

   initialized_ = true;

   return DEVICE_OK;
}


bool BrightField::Busy()
{
   return false;
}

int BrightField::Shutdown()
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

int BrightField::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      int pos;
      int ret = g_hub.GetBrightFieldPosition(*this, *GetCoreCallback(), pos);
      if (ret != DEVICE_OK)
         return ret;
      if (pos == 0)
         pProp->Set("Confocal");
      else
         pProp->Set("Bright Field");
   }
   else if (eAct == MM::AfterSet)
   {
      std::string setting;
      pProp->Get(setting);
      int pos = 1;
      if (setting == "Confocal")
         pos = 0;
      return g_hub.SetBrightFieldPosition(*this, *GetCoreCallback(), pos);
   }

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// CSUW1 Disk
///////////////////////////////////////////////////////////////////////////////
Disk::Disk () :
   initialized_ (false),
   name_ (g_CSUW1Disk),
   numPos_ (2)
{
   InitializeDefaultErrorMessages();
}

Disk::~Disk ()
{
   Shutdown();
}

void Disk::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int Disk::Initialize()
{
   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "CSUW1 Disk", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // State
   CPropertyAction* pAct = new CPropertyAction (this, &Disk::OnState);
   ret = CreateIntegerProperty(MM::g_Keyword_State, 0, false, pAct); 
   if (ret != DEVICE_OK) 
      return ret; 

   // create default positions and labels
   pAct = new CPropertyAction (this, &CStateBase::OnLabel);
   ret = CreateProperty(MM::g_Keyword_Label, "Disk 1", MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   SetPositionLabel(0, "Disk 1");
   SetPositionLabel(1, "Disk 2");
  // SetPositionLabel(2, "BrightField");

   ret = UpdateStatus();
   if (ret != DEVICE_OK) 
      return ret; 

   initialized_ = true;

   return DEVICE_OK;
}


bool Disk::Busy()
{
   return false;
}

int Disk::Shutdown()
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
// Hub disk positions:
// -1 - BrightField (state 2)
//  0 - Disk 1 (state 0)
//  1 - Disk 2 (state 1)
int Disk::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      int pos = 1;
      int ret = g_hub.GetDiskPosition(*this, *GetCoreCallback(), pos);
      if (ret != DEVICE_OK)
         return ret;
      pProp->Set( (long) pos);
   }
   else if (eAct == MM::AfterSet)
   {
      long state;
      pProp->Get(state);
      return g_hub.SetDiskPosition(*this, *GetCoreCallback(), state);
   }

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// CSUW1 Port
///////////////////////////////////////////////////////////////////////////////
Port::Port () :
   initialized_ (false),
   name_ (g_CSUW1Port),
   numPos_ (3)
{
   InitializeDefaultErrorMessages();
}

Port::~Port ()
{
   Shutdown();
}

void Port::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int Port::Initialize()
{
   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "CSUW1 Port", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // State
   CPropertyAction* pAct = new CPropertyAction (this, &Port::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct); 
   if (ret != DEVICE_OK) 
      return ret; 

   // create default positions and labels
   pAct = new CPropertyAction (this, &CStateBase::OnLabel);                  
   ret = CreateProperty(MM::g_Keyword_Label, "Undefined", MM::String, false, pAct);
   if (ret != DEVICE_OK)                                                     
      return ret;                                                            
   SetPositionLabel(0, "Port-1");
   SetPositionLabel(1, "Port-2");
   SetPositionLabel(2, "Port-3");

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret; 

   initialized_ = true;

   return DEVICE_OK;
}


bool Port::Busy()
{
   return false;
}

int Port::Shutdown()
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

int Port::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      int pos;
      int ret = g_hub.GetPortPosition(*this, *GetCoreCallback(), pos);
      if (ret != DEVICE_OK)
         return ret;
	  pProp->Set((long)pos);
   }
   else if (eAct == MM::AfterSet)
   {
      long pos;
      pProp->Get(pos);
	  return g_hub.SetPortPosition(*this, *GetCoreCallback(), pos);
   }

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// CSUW1 Aperture
///////////////////////////////////////////////////////////////////////////////
Aperture::Aperture () :
   initialized_ (false),
   name_ (g_CSUW1Aperture),
   numPos_ (10)
{
   InitializeDefaultErrorMessages();
}

Aperture::~Aperture ()
{
   Shutdown();
}

void Aperture::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int Aperture::Initialize()
{
   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "CSUW1 Aperture", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // State
   CPropertyAction* pAct = new CPropertyAction (this, &Aperture::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "9", MM::Integer, false, pAct); 
   if (ret != DEVICE_OK) 
      return ret; 

   SetPropertyLimits(MM::g_Keyword_State, 0, 9);

   ret = UpdateStatus();
   if (ret != DEVICE_OK) 
      return ret; 

   initialized_ = true;

   return DEVICE_OK;
}


bool Aperture::Busy()
{
   return false;
}

int Aperture::Shutdown()
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

int Aperture::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      int pos;
      int ret = g_hub.GetAperturePosition(*this, *GetCoreCallback(), pos);
      if (ret != DEVICE_OK)
         return ret;
	  pProp->Set((long)pos);
   }
   else if (eAct == MM::AfterSet)
   {
      long pos;
      pProp->Get(pos);
	  return g_hub.SetAperturePosition(*this, *GetCoreCallback(), pos);
   }
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// CSUW1 FRAP
///////////////////////////////////////////////////////////////////////////////
Frap::Frap () :
   initialized_ (false),
   name_ (g_CSUW1Frap),
   numPos_ (3)
{
   InitializeDefaultErrorMessages();
}

Frap::~Frap ()
{
   Shutdown();
}

void Frap::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int Frap::Initialize()
{
   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "CSUW1 FRAP", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // State
   CPropertyAction* pAct = new CPropertyAction (this, &Frap::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct); 
   if (ret != DEVICE_OK) 
      return ret; 

   // create default positions and labels
   pAct = new CPropertyAction (this, &CStateBase::OnLabel);                  
   ret = CreateProperty(MM::g_Keyword_Label, "Undefined", MM::String, false, pAct);
   if (ret != DEVICE_OK)                                                     
      return ret;                                                            
   SetPositionLabel(0, "Position-1");
   SetPositionLabel(1, "Position-2");
   SetPositionLabel(2, "Position-3");

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret; 

   initialized_ = true;

   return DEVICE_OK;
}


bool Frap::Busy()
{
   return false;
}

int Frap::Shutdown()
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

int Frap::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      int pos;
      int ret = g_hub.GetFrapPosition(*this, *GetCoreCallback(), pos);
      if (ret != DEVICE_OK)
         return ret;
	  pProp->Set((long)pos);
   }
   else if (eAct == MM::AfterSet)
   {
      long pos;
      pProp->Get(pos);
	  return g_hub.SetFrapPosition(*this, *GetCoreCallback(), pos);
   }

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// CSUW1 Magnifier
///////////////////////////////////////////////////////////////////////////////
Magnifier::Magnifier () :
   initialized_ (false),
   nr_ (1),
   name_ (g_CSUW1Magnifier),
   numPos_ (2)
{
   InitializeDefaultErrorMessages();

   // Magnifier Number
   CPropertyAction* pAct = new CPropertyAction (this, &Magnifier::OnMagnifierNr);
   CreateProperty("MagnifierNumber", "1", MM::Integer, false, pAct, true);
   AddAllowedValue("MagnifierNumber", "1");
   AddAllowedValue("MagnifierNumber", "2");
}

Magnifier::~Magnifier ()
{
   Shutdown();
}

void Magnifier::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int Magnifier::Initialize()
{
   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "CSUW1 Magnifier", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // State
   CPropertyAction* pAct = new CPropertyAction (this, &Magnifier::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct); 
   if (ret != DEVICE_OK) 
      return ret; 

   // create default positions and labels
   pAct = new CPropertyAction (this, &CStateBase::OnLabel);                  
   ret = CreateProperty(MM::g_Keyword_Label, "Undefined", MM::String, false, pAct);
   if (ret != DEVICE_OK)                                                     
      return ret;                                                            
   SetPositionLabel(0, "Magnifier-1");
   SetPositionLabel(1, "Magnifier-2");

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret; 

   initialized_ = true;

   return DEVICE_OK;
}


bool Magnifier::Busy()
{
   return false;
}

int Magnifier::Shutdown()
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

int Magnifier::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      int pos;
      int ret = g_hub.GetMagnifierPosition(*this, *GetCoreCallback(), nr_, pos);
      if (ret != DEVICE_OK)
         return ret;
	  pProp->Set((long)pos);
   }
   else if (eAct == MM::AfterSet)
   {
      long pos;
      pProp->Get(pos);
	  return g_hub.SetMagnifierPosition(*this, *GetCoreCallback(), nr_, pos);
   }

   return DEVICE_OK;
}

int Magnifier::OnMagnifierNr(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(nr_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(nr_);
   }
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// CSUW1 NIR Shutter
///////////////////////////////////////////////////////////////////////////////
NIRShutter::NIRShutter () :
   initialized_ (false),
   name_ (g_CSUW1NIRShutter),
   isOpen_ (false)
{
   InitializeDefaultErrorMessages();
}

NIRShutter::~NIRShutter ()
{
   Shutdown();
}

void NIRShutter::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int NIRShutter::Initialize()
{
   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "CSUW1 NIR Shutter", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Check current state of shutter:
   ret = g_hub.GetShutterPosition(*this, *GetCoreCallback(), isOpen_);
   if (DEVICE_OK != ret)
      return ret;

   // State
   CPropertyAction* pAct = new CPropertyAction (this, &NIRShutter::OnState);
   ret = CreateProperty(MM::g_Keyword_State, isOpen_ ? "Open" : "Closed", MM::String, false, pAct);
   if (ret != DEVICE_OK) 
      return ret; 

   AddAllowedValue(MM::g_Keyword_State, "Closed");
   AddAllowedValue(MM::g_Keyword_State, "Open");

   EnableDelay();

   changedTime_ = GetCurrentMMTime();

   ret = UpdateStatus();
   if (ret != DEVICE_OK) 
      return ret; 

   initialized_ = true;

   return DEVICE_OK;
}

bool NIRShutter::Busy()
{
   MM::MMTime interval = GetCurrentMMTime() - changedTime_;
   if (interval < (1000.0 * GetDelayMs() ))
      return true;

   return false;
}

int NIRShutter::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

int NIRShutter::SetOpen(bool open)
{
   changedTime_ = GetCurrentMMTime();
   int ret = g_hub.SetNIRShutterPosition(*this, *GetCoreCallback(), open);
   if (ret != DEVICE_OK)
      return ret;
   isOpen_ = open;
   return DEVICE_OK;
}

int NIRShutter::GetOpen(bool &open)
{

   // Check current state of shutter: (this might not be necessary, since we cash ourselves)
   int ret = g_hub.GetNIRShutterPosition(*this, *GetCoreCallback(), isOpen_);
   if (DEVICE_OK != ret)
      return ret;
   open = isOpen_;
   return DEVICE_OK;
}

int NIRShutter::Fire(double /*deltaT*/)
{
   return DEVICE_UNSUPPORTED_COMMAND;  
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers                                                           
///////////////////////////////////////////////////////////////////////////////

int NIRShutter::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(isOpen_ ? "Open" : "Closed");
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
