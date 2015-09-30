///////////////////////////////////////////////////////////////////////////////
// FILE:       CSUX.cpp
// PROJECT:    MicroManage
// SUBSYSTEM:  DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:
// Yokogawa CSUX controller adapter
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

// AUTHOR: Nico Stuurman, 02/02/2007
//
// Based on NikonTE2000 controller adapter by Nenad Amodaj
//

#ifdef WIN32
#include <windows.h>
#define snprintf _snprintf 
#endif

#include "CSUX.h"
#include "CSUXHub.h"
#include <cstdio>
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>
#include <iostream>

const char* g_CSUXHub = "CSUX-Hub";
const char* g_CSUXFilterWheel = "CSUX-Filter Wheel";
const char* g_CSUXDichroic = "CSUX-Dichroic Mirror";
const char* g_CSUXShutter = "CSUX-Shutter";
const char* g_CSUXDriveSpeed= "CSUX-DriveSpeed";

using namespace std;

CSUXHub g_hub;

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_CSUXHub, MM::GenericDevice, "CSUX Hub (Needed for CSUX)");
   RegisterDevice(g_CSUXFilterWheel, MM::StateDevice, "Filter Wheel");
   RegisterDevice(g_CSUXDichroic, MM::StateDevice, "Dichroic Mirror");
   RegisterDevice(g_CSUXShutter, MM::ShutterDevice, "Shutter");
   RegisterDevice(g_CSUXDriveSpeed, MM::GenericDevice, "DriveSpeed");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_CSUXHub) == 0)
   {
      return new Hub();
   }
   else if (strcmp(deviceName, g_CSUXFilterWheel) == 0 )
   {
      return new FilterWheel();
   }
   else if (strcmp(deviceName, g_CSUXDichroic) == 0 )
   {
      return new Dichroic();
   }
   else if (strcmp(deviceName, g_CSUXShutter) == 0 )
   {
      return new Shutter();
   }
   else if (strcmp(deviceName, g_CSUXDriveSpeed) == 0 )
   {
      return new DriveSpeed();
   }
   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}


///////////////////////////////////////////////////////////////////////////////
// CSUX Hub
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
   CDeviceUtils::CopyLimitedString(name, g_CSUXHub);
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
   int ret = CreateProperty(MM::g_Keyword_Name, g_CSUXHub, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "Yokogawa CSUX hub", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Bright field port
   int pos = 0;
   ret = g_hub.GetBrightFieldPort(*this, *GetCoreCallback(), pos);
   if (ret == DEVICE_OK)
   {
      CPropertyAction* pAct = new CPropertyAction (this, &Hub::OnBrightFieldPort);
      CreateProperty("BrightFieldPort", "Confocal", MM::String, false, pAct);
      AddAllowedValue("BrightFieldPort", "Confocal");
      AddAllowedValue("BrightFieldPort", "BrightField");
   }

   /*
   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;
   */

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

int Hub::OnBrightFieldPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      int pos;
      int ret = g_hub.GetBrightFieldPort(*this, *GetCoreCallback(), pos);
      if (ret != DEVICE_OK)
         return ret;
      if (pos == 0)
         pProp->Set("Confocal");
      else
         pProp->Set("BrightField");
   }
   else if (eAct == MM::AfterSet)
   {
      std::string setting;
      pProp->Get(setting);
      int pos = 1;
      if (setting == "Confocal")
         pos = 0;
      return g_hub.SetBrightFieldPort(*this, *GetCoreCallback(), pos);
   }

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// CSUX ND Filters
///////////////////////////////////////////////////////////////////////////////
FilterWheel::FilterWheel () :
   posMoved_ (0),
   wheelNr_ (1),
   initialized_ (false),
   numPos_ (6),
   pos_ (1),
   name_ (g_CSUXFilterWheel)
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
   ret = CreateProperty(MM::g_Keyword_Description, g_CSUXFilterWheel, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // State
   CPropertyAction* pAct = new CPropertyAction (this, &FilterWheel::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "1", MM::Integer, false, pAct); 
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

   // Speed
   pAct = new CPropertyAction (this, &FilterWheel::OnSpeed);
   ret = CreateProperty("Speed", "2", MM::Integer, false, pAct); 
   if (ret != DEVICE_OK) 
      return ret; 
   AddAllowedValue("Speed", "0");
   AddAllowedValue("Speed", "1");
   AddAllowedValue("Speed", "2");
   AddAllowedValue("Speed", "3");

   ret = UpdateStatus();
   if (ret != DEVICE_OK) 
      return ret; 

   initialized_ = true;

   return DEVICE_OK;
}

bool FilterWheel::Busy()
{
   // TODO: figure out how speed commadn affects Busy
   MM::MMTime now = GetCurrentMMTime();
   // each position moved takes 33 msec
   if ((now - lastMoveTime_) < (posMoved_ * 33))
      return true;
 
   return false;
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
      pProp->Set(pos-1);
      // cache, don't know why...
      pos_ = pos-1;
   }
   else if (eAct == MM::AfterSet)
   {
      long pos;
      pProp->Get(pos);
      int ret = g_hub.SetFilterWheelPosition(*this, *GetCoreCallback(), wheelNr_, pos+1);
      if (ret == DEVICE_OK) {
         lastMoveTime_ = GetCurrentMMTime();
         // lousy logic to calculate how many positions we move to calculate busy time
         posMoved_ = abs(pos_ - pos+1);
         if (posMoved_ > 3)
            posMoved_ = 3;
         pos_ = pos+1;
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
      // cache, don't know why...
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
// CSUX Dichroic
///////////////////////////////////////////////////////////////////////////////
Dichroic::Dichroic () :
   initialized_ (false),
   pos_ (1),
   name_ (g_CSUXDichroic),
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
   ret = CreateProperty(MM::g_Keyword_Description, "CSUX Dichroics", MM::String, true);
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
   // Who knows?
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
      pos_ = csuPos - 1;
      pProp->Set(pos_);
   }
   else if (eAct == MM::AfterSet)
   {
      long pos; 
      pProp->Get(pos);
      if (pos == pos_)
         return DEVICE_OK;

      int ret = g_hub.SetDichroicPosition(*this, *GetCoreCallback(), pos + 1);
      if (ret != DEVICE_OK)
         return  ret;
      pos_ = pos;
   }
   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// CSUX Shutter
///////////////////////////////////////////////////////////////////////////////
Shutter::Shutter () :
   initialized_ (false),
   name_ (g_CSUXShutter),
   state_ (1)
{
   InitializeDefaultErrorMessages();

   // Todo: Add custom messages
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
   ret = CreateProperty(MM::g_Keyword_Description, "CSUX Shutter", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Check current state of shutter:
   ret = g_hub.GetShutterPosition(*this, *GetCoreCallback(), state_);
   if (DEVICE_OK != ret)
      return ret;

   // State
   CPropertyAction* pAct = new CPropertyAction (this, &Shutter::OnState);
   if (state_ == 1)
      ret = CreateProperty(MM::g_Keyword_State, "Closed", MM::String, false, pAct); 
   else
      ret = CreateProperty(MM::g_Keyword_State, "Open", MM::String, false, pAct); 
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
   if (open)
   {
      int ret = g_hub.SetShutterPosition(*this, *GetCoreCallback(), 0);
      if (ret != DEVICE_OK)
         return ret;
      state_ =  0;
   } else
   {
      int ret = g_hub.SetShutterPosition(*this, *GetCoreCallback(), 1);
      if (ret != DEVICE_OK)
         return ret;
      state_ =  1;
   }
   return DEVICE_OK;
}

int Shutter::GetOpen(bool &open)
{

   // Check current state of shutter: (this might not be necessary, since we cash ourselves)
   int ret = g_hub.GetShutterPosition(*this, *GetCoreCallback(), state_);
   if (DEVICE_OK != ret)
      return ret;
   if (state_ == 0)
      open = true;
   else 
      open = false;
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
      // return pos as we know it
      if (state_ == 0)
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



///////////////////////////////////////////////////////////////////////////////
// CSUX DriveSpeed
///////////////////////////////////////////////////////////////////////////////
DriveSpeed::DriveSpeed () :
   initialized_ (false),
   name_ (g_CSUXDriveSpeed),
   min_ (0),
   max_ (5000),
   autoAdjustMs_ (0.0),
   current_ (1500)
{
   InitializeDefaultErrorMessages();

   // Todo: Add custom messages
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
   ret = CreateProperty(MM::g_Keyword_Description, "CSUX Drive Speed", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Check current speed of the disk
   ret = g_hub.GetDriveSpeed(*this, *GetCoreCallback(), current_);
   if (DEVICE_OK != ret)
      return ret;
   ret = g_hub.GetMaxDriveSpeed(*this, *GetCoreCallback(), max_);

   // Speed
   CPropertyAction* pAct = new CPropertyAction (this, &DriveSpeed::OnSpeed);
   ret = CreateProperty(MM::g_Keyword_State, "1500", MM::Integer, false, pAct); 
   if (ret != DEVICE_OK) 
      return ret; 
   SetPropertyLimits(MM::g_Keyword_State, min_, max_);

   // Disk On/Off
   pAct = new CPropertyAction(this, &DriveSpeed::OnRun);
   ret = CreateProperty("Run", "On", MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   AddAllowedValue("Run", "On");
   AddAllowedValue("Run", "Off");

   // Auto Adjust
   pAct = new CPropertyAction(this, &DriveSpeed::OnAutoAdjust);
   ret = CreateProperty("AutoAdjust", "0", MM::Float, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   ret = UpdateStatus();
   if (ret != DEVICE_OK) 
      return ret; 

   // figure out if the disk is running
   if (current_ > 0)
      running_ = true;

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

/*
 * Speed is read back from the device
 */
int DriveSpeed::OnSpeed(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // Check current speed of the disk
      int ret = g_hub.GetDriveSpeed(*this, *GetCoreCallback(), current_);
      if (DEVICE_OK != ret)
         return ret;
      // return speed as we know it
      pProp->Set((long)current_);
   }
   else if (eAct == MM::AfterSet)
   {
      long speed;
      pProp->Get(speed);
      if (speed == current_)
         return DEVICE_OK;
      if (speed < min_)
         speed = min_;
      else if (speed > max_)
         speed = max_;
      int ret = g_hub.SetDriveSpeed(*this, *GetCoreCallback(), speed);
      if (ret == DEVICE_OK) {
         current_ = speed;
         autoAdjustMs_ = 0;
         return DEVICE_OK;
      }
      else
         return  ret;
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

/*
 * Adjust disk speed to exposure time
 */
int DriveSpeed::OnAutoAdjust(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // We can not query the device, and do not keep track of changes
      pProp->Set(autoAdjustMs_);
   }
   else if (eAct == MM::AfterSet)
   {
      double autoAdjustMs;
      pProp->Get(autoAdjustMs);
      int ret = g_hub.SetAutoAdjustDriveSpeed(*this, *GetCoreCallback(), autoAdjustMs);
      if (ret != DEVICE_OK)
         return ret;
      autoAdjustMs_ = autoAdjustMs;
   }
   return DEVICE_OK;
}
