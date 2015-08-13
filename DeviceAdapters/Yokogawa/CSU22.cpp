///////////////////////////////////////////////////////////////////////////////
// FILE:       CSU22.cpp
// PROJECT:    MicroManage
// SUBSYSTEM:  DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:
// Yokogawa CSU22 controller adapter
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

#include "CSU22.h"
#include "CSU22Hub.h"
#include <cstdio>
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>
#include <iostream>

const char* g_CSU22Hub = "Hub";
const char* g_CSU22NDFilter = "ND Filter";
const char* g_CSU22FilterSet = "Filter Set";
const char* g_CSU22Shutter = "Shutter";
const char* g_CSU22DriveSpeed= "DriveSpeed";

using namespace std;

CSU22Hub g_hub;

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_CSU22Hub, MM::GenericDevice, "CSU22 Hub (Needed for CSU22)");
   RegisterDevice(g_CSU22NDFilter, MM::StateDevice, "Neutral Density Filter");
   RegisterDevice(g_CSU22FilterSet, MM::StateDevice, "Filter Set (Dichroics)");
   RegisterDevice(g_CSU22Shutter, MM::ShutterDevice, "Shutter");
   RegisterDevice(g_CSU22DriveSpeed, MM::GenericDevice, "DriveSpeed");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_CSU22Hub) == 0)
   {
      return new Hub();
   }
   else if (strcmp(deviceName, g_CSU22NDFilter) == 0 )
   {
      return new NDFilter();
   }
   else if (strcmp(deviceName, g_CSU22FilterSet) == 0 )
   {
      return new FilterSet();
   }
   else if (strcmp(deviceName, g_CSU22Shutter) == 0 )
   {
      return new Shutter();
   }
   else if (strcmp(deviceName, g_CSU22DriveSpeed) == 0 )
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
// CSU22 Hub
///////////////////////////////////////////////////////////////////////////////

Hub::Hub() :
   initialized_(false),
   port_("Undefined"),
   minSpeed_(1200),
   maxSpeed_(5000),
   currentSpeed_(5000)
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
   CDeviceUtils::CopyLimitedString(name, g_CSU22Hub);
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
   int ret = CreateProperty(MM::g_Keyword_Name, g_CSU22Hub, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "Yokogawa CSU22 hub", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Drive Speed
   CPropertyAction* pAct = new CPropertyAction (this, &Hub::OnSpeed);
   ret = CreateProperty("DriveSpeed", "1500", MM::Integer, false, pAct); 
   if (ret != DEVICE_OK) 
      return ret; 
   SetPropertyLimits("DriveSpeed", minSpeed_, maxSpeed_);

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

/*
 * Speed is read back from the device
 */
int Hub::OnSpeed(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // Check current speed of the disk
      int ret = g_hub.GetDriveSpeedPosition(*this, *GetCoreCallback(), currentSpeed_);
      if (DEVICE_OK != ret)
         return ret;
      // return speed as we know it
      pProp->Set((long)currentSpeed_);
   }
   else if (eAct == MM::AfterSet)
   {
      long speed;
      pProp->Get(speed);
      if (speed == currentSpeed_)
         return DEVICE_OK;
      int ret = g_hub.SetDriveSpeedPosition(*this, *GetCoreCallback(), speed);
      if (ret == DEVICE_OK) {
         currentSpeed_ = speed;
         return DEVICE_OK;
      }
      else
         return  ret;
   }
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// CSU22 ND Filters
///////////////////////////////////////////////////////////////////////////////
NDFilter::NDFilter () :
   initialized_ (false),
   numPos_ (2),
   pos_ (1),
   name_ (g_CSU22NDFilter)
{
   InitializeDefaultErrorMessages();

   // Todo: Add custom messages
}

NDFilter::~NDFilter ()
{
   Shutdown();
}

void NDFilter::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int NDFilter::Initialize()
{
   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "CSU22 ND Filter", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // State
   CPropertyAction* pAct = new CPropertyAction (this, &NDFilter::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct); 
   if (ret != DEVICE_OK) 
      return ret; 
   AddAllowedValue(MM::g_Keyword_State, "0");
   AddAllowedValue(MM::g_Keyword_State, "1");
                                                                                         
   // Label                                                                  
   pAct = new CPropertyAction (this, &CStateBase::OnLabel);                  
   ret = CreateProperty(MM::g_Keyword_Label, "Undefined", MM::String, false, pAct);        
   if (ret != DEVICE_OK)                                                     
      return ret;                                                            
                                                                             
   // create default positions and labels
   SetPositionLabel(0, "10% Transmission");                               
   SetPositionLabel(1, "100% Transmission");                               

   ret = UpdateStatus();
   if (ret != DEVICE_OK) 
      return ret; 

   initialized_ = true;

   return DEVICE_OK;
}

bool NDFilter::Busy()
{
   // Who knows?
   return false;
}

int NDFilter::Shutdown()
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


int NDFilter::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
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
      int ret = g_hub.SetNDFilterPosition(*this, *GetCoreCallback(), pos);
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


///////////////////////////////////////////////////////////////////////////////
// CSU22 FilterSet
///////////////////////////////////////////////////////////////////////////////
FilterSet::FilterSet () :
   initialized_ (false),
   changedTime_(0),
   pos_ (1),
   name_ (g_CSU22FilterSet),
   numPos_ (5)
{
   InitializeDefaultErrorMessages();

   EnableDelay();
   // Todo: Add custom messages
}

FilterSet::~FilterSet ()
{
   Shutdown();
}

void FilterSet::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int FilterSet::Initialize()
{
   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "CSU22 Filter Set", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // State
   CPropertyAction* pAct = new CPropertyAction (this, &FilterSet::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct); 
   if (ret != DEVICE_OK) 
      return ret; 

   AddAllowedValue(MM::g_Keyword_State, "0");
   AddAllowedValue(MM::g_Keyword_State, "1");
   AddAllowedValue(MM::g_Keyword_State, "2");
   AddAllowedValue(MM::g_Keyword_State, "3");
   AddAllowedValue(MM::g_Keyword_State, "4");

   // Label                                                                  
   pAct = new CPropertyAction (this, &CStateBase::OnLabel);                  
   ret = CreateProperty(MM::g_Keyword_Label, "Undefined", MM::String, false, pAct);        
   if (ret != DEVICE_OK)                                                     
      return ret;                                                            
                                                                             
   // create default positions and labels
   SetPositionLabel(0, "Filter-1");                               
   SetPositionLabel(1, "Filter-2");                               
   SetPositionLabel(2, "Filter-3");                               
   SetPositionLabel(3, "Filter-4");                               
   SetPositionLabel(4, "Filter-5");                               

   ret = UpdateStatus();
   if (ret != DEVICE_OK) 
      return ret; 

   changedTime_ = GetCurrentMMTime();
   initialized_ = true;

   return DEVICE_OK;
}


bool FilterSet::Busy()
{
   MM::MMTime interval = GetCurrentMMTime() - changedTime_;
  
   if (interval < (1000.0 * GetDelayMs() ))
       return true;

   return false;
}

int FilterSet::Shutdown()
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

int FilterSet::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // return pos as we know it
      pProp->Set((long)(pos_ - 1));
   }
   else if (eAct == MM::AfterSet)
   {
      long pos; 
      long dichroic = 1;
      long filter = 1;
      pProp->Get(pos);
      pos +=1;

      if (pos == pos_)
         return DEVICE_OK;
      if (pos < 1)
         pos = 1;
      else if (pos > 5)
         pos = 3;
      if (pos <= 3) {
         dichroic = pos;
         filter = pos;
      } else if (pos == 4) {
         dichroic = 3;
         filter = 2;
      } else if (pos == 5) {
         dichroic = 3;
         filter = 1;
      }

      int ret = g_hub.SetFilterSetPosition(*this, *GetCoreCallback(), filter, dichroic);
      if (ret == DEVICE_OK) {
         pos_ = pos;
         changedTime_ = GetCurrentMMTime();
         return DEVICE_OK;
      }
      else
         return  ret;
   }
   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// CSU22 Shutter
///////////////////////////////////////////////////////////////////////////////
Shutter::Shutter () :
   initialized_ (false),
   name_ (g_CSU22Shutter),
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
   ret = CreateProperty(MM::g_Keyword_Description, "CSU22 Shutter", MM::String, true);
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
// CSU22 DriveSpeed
///////////////////////////////////////////////////////////////////////////////
DriveSpeed::DriveSpeed () :
   initialized_ (false),
   name_ (g_CSU22DriveSpeed),
   min_ (1500),
   max_ (5000),
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
   ret = CreateProperty(MM::g_Keyword_Description, "CSU22 Drive Speed", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Check current speed of the disk
   ret = g_hub.GetDriveSpeedPosition(*this, *GetCoreCallback(), current_);
   if (DEVICE_OK != ret)
      return ret;
   if (current_ < min_)
      current_ = min_;
   if (current_ > max_)
      current_ = max_;
   char speed[5];
   sprintf(speed,"%d", current_);
   // Speed
   CPropertyAction* pAct = new CPropertyAction (this, &DriveSpeed::OnSpeed);
   ret = CreateProperty(MM::g_Keyword_State, speed, MM::Integer, false, pAct); 
   if (ret != DEVICE_OK) 
      return ret; 

   ret = UpdateStatus();
   if (ret != DEVICE_OK) 
      return ret; 

   initialized_ = true;

   return DEVICE_OK;
}

bool DriveSpeed::Busy()
{
   return g_hub.IsDriveSpeedBusy(*this, *GetCoreCallback());
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
      int ret = g_hub.GetDriveSpeedPosition(*this, *GetCoreCallback(), current_);
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
      int ret = g_hub.SetDriveSpeedPosition(*this, *GetCoreCallback(), speed);
      if (ret == DEVICE_OK) {
         current_ = speed;
         return DEVICE_OK;
      }
      else
         return  ret;
   }
   return DEVICE_OK;
}

