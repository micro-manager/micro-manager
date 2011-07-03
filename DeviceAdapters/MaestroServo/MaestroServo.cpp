//////////////////////////////////////////////////////////////////////////////
// FILE:          MaestroServo.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Adapter for MaestroServo controller (http://www.pololu.com/docs/0J40/all) 
// COPYRIGHT:     University of California, San Francisco, 2010
// LICENSE:       This file is distributed under the LGPLD license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
// AUTHOR:        Nico Stuurman, 10/03/2009

#ifdef WIN32
#include <windows.h>
#define snprintf _snprintf 
#endif

#include "MaestroServo.h"
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include "../../MMDevice/DeviceUtils.h"
#include <sstream>
#include <iostream>

const char* g_MaestroServoName = "MaestroServo";
const char* g_MaestroShutterName = "MaestroServoAsShutter";

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   AddAvailableDeviceName(g_MaestroServoName, "Pololu MaestroServo controller" );
   AddAvailableDeviceName(g_MaestroShutterName, "Pololu MaestroServo as shutter" );
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_MaestroServoName) == 0)
   {
      MaestroServo* pMaestroServo = new MaestroServo();
      return pMaestroServo;
   }

   if (strcmp(deviceName, g_MaestroShutterName) == 0)
   {
      MaestroShutter* pMaestroShutter = new MaestroShutter();
      return pMaestroShutter;
   }

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}


///////////////////////////////////////////////////////////////////////////////
// MaestroServo device
///////////////////////////////////////////////////////////////////////////////

MaestroServo::MaestroServo() :
   initialized_(false),
   moving_(false),
   servoNr_(0),
   minPos_(990),
   maxPos_(2000),
   port_("Undefined"),
   changedTime_(0.0)
{
   InitializeDefaultErrorMessages();

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, g_MaestroServoName, MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "MaestroServo controller", MM::String, true);

   // Port
   CPropertyAction* pAct = new CPropertyAction (this, &MaestroServo::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

   // Select Servo
   pAct = new CPropertyAction(this, &MaestroServo::OnServoNr);
   CreateProperty("ServoNr", "0", MM::Integer, false, pAct, true);
   SetPropertyLimits("ServoNr", 0, 23);

   // Minimum Position
   pAct = new CPropertyAction(this, &MaestroServo::OnMinPosition);
   CreateProperty("Minimum Position", "990", MM::Integer, false, pAct, true);

   // Maximum Position
   pAct = new CPropertyAction(this, &MaestroServo::OnMaxPosition);
   CreateProperty("Maximum Position", "2000", MM::Integer, false, pAct, true);


   EnableDelay(true);
}

MaestroServo::~MaestroServo()
{
   Shutdown();
}

void MaestroServo::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_MaestroServoName);
}

int MaestroServo::Initialize()
{
   changedTime_ = GetCurrentMMTime();

   CPropertyAction* pAct = new CPropertyAction(this, &MaestroServo::OnPosition);
   CreateProperty("Position", "1000", MM::Float, false, pAct);
   SetPropertyLimits("Position", minPos_, maxPos_);

   // Speed
   pAct = new CPropertyAction(this, &MaestroServo::OnSpeed);
   CreateProperty("Speed", "0", MM::Integer, false, pAct);
   SetPropertyLimits("Speed", 0, 0xef);

   // Acceleration
   pAct = new CPropertyAction(this, &MaestroServo::OnAcceleration);
   CreateProperty("Acceleration", "0", MM::Integer, false, pAct);
   SetPropertyLimits("Acceleration", 0, 255);

   initialized_ = true;
   return DEVICE_OK;
}

int MaestroServo::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

/*
 */
bool MaestroServo::Busy()
{
   // Pololu does not really signal when it is done, rather when it is done sending pulses,
   // so wait for it to be done and then add a user-specified delay
   if (moving_) {
      unsigned char buf[1];
      buf[0] = 0x93;
      int ret = WriteToComPort(port_.c_str(), buf, 1);

      MM::MMTime start = GetCurrentMMTime();
      MM::MMTime t(1,0);
      bool timeout = false;
      unsigned long nRead = 0;
      while (!timeout && moving_) {
         ret = ReadFromComPort (port_.c_str(), buf, 1, nRead);
         if (nRead == 1) {
            if (buf[0] == 1)
               return true;
            else {
               moving_ = false;
               changedTime_ = GetCurrentMMTime();
            }
         }
         CDeviceUtils::SleepMs(15);
         timeout = (GetCurrentMMTime() - start) > t;
      }
   }

   MM::MMTime delay(GetDelayMs() * 1000);
   if (GetCurrentMMTime() - changedTime_ > delay)
      return false;

   return true;
}



///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////
/*
 * Sets the Serial Port to be used.
 * Should be called before initialization
 */
int MaestroServo::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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


/**
 * Sets the "Active" Servo"
 */
int MaestroServo::OnServoNr(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(servoNr_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(servoNr_);
   }

   return DEVICE_OK;
}


/**
 * Minimum value for Position
 */
int MaestroServo::OnMinPosition(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(minPos_);
   }
   else if (eAct == MM::AfterSet)
   {
      long pos;
      pProp->Get(pos);
      if (pos < 0)
         pos = 0;
      if (pos >= maxPos_)
         pos = maxPos_ -1;
      minPos_ = pos;
   }

   return DEVICE_OK;
}


/**
 * Maximum value for Position
 */
int MaestroServo::OnMaxPosition(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(maxPos_);
   }
   else if (eAct == MM::AfterSet)
   {
      long pos;
      pProp->Get(pos);
      if (pos <= minPos_)
         pos = minPos_ + 1;
      maxPos_ = pos;
   }

   return DEVICE_OK;
}

/**
 * Sets the amplitude
 */
int MaestroServo::OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // TODO: Read position from Servo
      pProp->Set(position_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(position_);

      const int bufSize = 4;
      unsigned char buf[bufSize];
      int pos = (int) position_ * 4;
      buf[0] = 0x84;
      buf[1] = (unsigned char) servoNr_;
      buf[2] = ( pos & 0x7F);
      buf[3] = ( pos >> 7) & 0x7F;

      int ret = WriteToComPort(port_.c_str(), buf, bufSize);
      if (ret != DEVICE_OK)
         return ret;
      moving_ = true;
   }

   return DEVICE_OK;
}


/**
 * Sets Speed.  A value of 0 corresponds to max speed.  Units are (0.25us)/(10ms)
 */
int MaestroServo::OnSpeed(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // TODO: Read position from Servo
      pProp->Set(speed_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(speed_);

      const int bufSize = 4;
      unsigned char buf[bufSize];
      int pos = (int) speed_;
      buf[0] = 0x87;
      buf[1] = (unsigned char) servoNr_;
      buf[2] = ( pos & 0x7F);
      buf[3] = ( pos >> 7) & 0x7F;

      int ret = WriteToComPort(port_.c_str(), buf, bufSize);
      if (ret != DEVICE_OK)
         return ret;
   }

   return DEVICE_OK;
}


/**
 * Sets Acceleration.  A value of 0 corresponds to max speed.  Units are (0.25us)/(10ms)
 */
int MaestroServo::OnAcceleration(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // TODO: Read position from Servo
      pProp->Set(acceleration_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(acceleration_);

      const int bufSize = 4;
      unsigned char buf[bufSize];
      unsigned int pos = (unsigned int) acceleration_;
      buf[0] = 0x89;
      buf[1] = (unsigned char) servoNr_;
      buf[2] = ( pos & 0x7F);
      buf[3] = ( pos >> 7) & 0x7F;

      int ret = WriteToComPort(port_.c_str(), buf, bufSize);
      if (ret != DEVICE_OK)
         return ret;
   }

   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// MaestroServo Servo As Shutter
///////////////////////////////////////////////////////////////////////////////

MaestroShutter::MaestroShutter()  :
   initialized_(false),
   moving_(false),
   openPos_(1200),
   closedPos_(1300),
   open_(false),
   servoNr_(0),
   port_("Undefined"),
   changedTime_(0.0)
{
   InitializeDefaultErrorMessages();

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, g_MaestroServoName, MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "MaestroServo controller", MM::String, true);

   // Port
   CPropertyAction* pAct = new CPropertyAction (this, &MaestroShutter::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

   // Select Servo
   pAct = new CPropertyAction(this, &MaestroShutter::OnServoNr);
   CreateProperty("ServoNr", "0", MM::Integer, false, pAct, true);
   SetPropertyLimits("ServoNr", 0, 23);

   EnableDelay(true);
}

MaestroShutter::~MaestroShutter() 
{
   Shutdown();
}
  
// Device API
// ----------
int MaestroShutter::Initialize()
{
   changedTime_ = GetCurrentMMTime();

   // Speed
   CPropertyAction* pAct = new CPropertyAction(this, &MaestroShutter::OnSpeed);
   CreateProperty("Speed", "0", MM::Integer, false, pAct);
   SetPropertyLimits("Speed", 0, 0xef);

   // Acceleration
   pAct = new CPropertyAction(this, &MaestroShutter::OnAcceleration);
   CreateProperty("Acceleration", "0", MM::Integer, false, pAct);
   SetPropertyLimits("Acceleration", 0, 255);

   // Open Position
   pAct = new CPropertyAction(this, &MaestroShutter::OnOpenPosition);
   CreateProperty("Open Position", "1200", MM::Integer, false, pAct);

   // Closed Position
   pAct = new CPropertyAction(this, &MaestroShutter::OnClosedPosition);
   CreateProperty("Closed Position", "1300", MM::Integer, false, pAct);

   int ret = SetPosition(closedPos_);
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

int MaestroShutter::Shutdown()
{
   return DEVICE_OK;
}
  
void MaestroShutter::GetName(char* pszName) const
{
   CDeviceUtils::CopyLimitedString(pszName, g_MaestroShutterName);
}

// Shutter API
int MaestroShutter::SetOpen(bool open)
{
   long pos = closedPos_;
   if (open)
      pos = openPos_;

   int ret = SetPosition(pos);
   if (ret != DEVICE_OK)
      return ret;
   open_ = open;

   return DEVICE_OK;
}

int MaestroShutter::GetOpen(bool& open)
{
   open = open_;
   return DEVICE_OK;
}

int MaestroShutter::Fire(double /*deltaT*/)
{
   return DEVICE_OK;
}


bool MaestroShutter::Busy()
{
   // Pololu does not really signal when it is done, rather when it is done sending pulses,
   // so wait for it to be done and then add a user-specified delay
   if (moving_) {
      unsigned char buf[1];
      buf[0] = 0x93;
      int ret = WriteToComPort(port_.c_str(), buf, 1);

      MM::MMTime start = GetCurrentMMTime();
      MM::MMTime t(1,0);
      bool timeout = false;
      unsigned long nRead = 0;
      while (!timeout && moving_) {
         ret = ReadFromComPort (port_.c_str(), buf, 1, nRead);
         if (nRead == 1) {
            if (buf[0] == 1)
               return true;
            else {
               moving_ = false;
               changedTime_ = GetCurrentMMTime();
            }
         }
         CDeviceUtils::SleepMs(15);
         timeout = (GetCurrentMMTime() - start) > t;
      }
   }

   MM::MMTime delay(GetDelayMs() * 1000);
   if (GetCurrentMMTime() - changedTime_ > delay)
      return false;

   return true;
}

int MaestroShutter::SetPosition(long position)
{
   const int bufSize = 4;
   unsigned char buf[bufSize];
   int pos = (int) position * 4;

   buf[0] = 0x84;
   buf[1] = (unsigned char) servoNr_;
   buf[2] = ( pos & 0x7F);
   buf[3] = ( pos >> 7) & 0x7F;

   int ret = WriteToComPort(port_.c_str(), buf, bufSize);
   if (ret != DEVICE_OK)
      return ret;
   moving_ = true;

   return DEVICE_OK;
}

///////////////////////////
// Shutter Action Handlers


/*
 * Sets the Serial Port to be used.
 * Should be called before initialization
 */
int MaestroShutter::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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

/**
 * Sets the "Active" Servo"
 */
int MaestroShutter::OnServoNr(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(servoNr_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(servoNr_);
   }

   return DEVICE_OK;
}

/**
 * Sets Speed.  A value of 0 corresponds to max speed.  Units are (0.25us)/(10ms)
 */
int MaestroShutter::OnSpeed(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // TODO: Read position from Servo
      pProp->Set(speed_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(speed_);

      const int bufSize = 4;
      unsigned char buf[bufSize];
      int pos = (int) speed_;
      buf[0] = 0x87;
      buf[1] = (unsigned char) servoNr_;
      buf[2] = ( pos & 0x7F);
      buf[3] = ( pos >> 7) & 0x7F;

      int ret = WriteToComPort(port_.c_str(), buf, bufSize);
      if (ret != DEVICE_OK)
         return ret;
   }

   return DEVICE_OK;
}


/**
 * Sets Acceleration.  A value of 0 corresponds to max speed.  Units are (0.25us)/(10ms)
 */
int MaestroShutter::OnAcceleration(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // TODO: Read position from Servo
      pProp->Set(acceleration_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(acceleration_);

      const int bufSize = 4;
      unsigned char buf[bufSize];
      unsigned int pos = (unsigned int) acceleration_;
      buf[0] = 0x89;
      buf[1] = (unsigned char) servoNr_;
      buf[2] = ( pos & 0x7F);
      buf[3] = ( pos >> 7) & 0x7F;

      int ret = WriteToComPort(port_.c_str(), buf, bufSize);
      if (ret != DEVICE_OK)
         return ret;
   }

   return DEVICE_OK;
}

int MaestroShutter::OnOpenPosition(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(openPos_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(openPos_);
      if (open_)
         SetPosition(openPos_);
   }
   return DEVICE_OK;
}

int MaestroShutter::OnClosedPosition(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(closedPos_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(closedPos_);
      if (!open_)
         SetPosition(closedPos_);
   }
   return DEVICE_OK;
}
