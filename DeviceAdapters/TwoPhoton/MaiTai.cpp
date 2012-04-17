///////////////////////////////////////////////////////////////////////////////
// FILE:          MaiTai.cpp
// PROJECT:       100X micro-manager extensions
// SUBSYSTEM:     DeviceAdapters : TwoPhoton
//-----------------------------------------------------------------------------
// DESCRIPTION:   Control device for MaiTaiLasers
//
// COPYRIGHT:     Nenad Amodaj 2011, 100X Imaging Inc 2009
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
//                
// AUTHOR:        Nenad Amodaj
//                

#include "TwoPhoton.h"
#include <sstream>
#include "DeviceUtils.h"

using namespace std;

const char* g_MaiTaiDeviceName = "MaiTai";
const char* g_DemoLaserDeviceName = "DemoLaser";
const char* g_VShutterDeviceName = "V2Shutter";

const char* g_PropertyOnOff = "OnOff";
const char* g_PropertyPower = "Power";
const char* g_PropertyWL = "Wavelength";
const char* g_PropertyShutter = "Shutter";
const char* g_PropertyWarmedup = "Warmedup";

const char* g_PropertyDev1 = "DAC1";
const char* g_PropertyDev2 = "DAC2";

const char* g_ON = "ON";
const char* g_OFF = "OFF";

const char* g_cmd_warmedup = "READ:PCTWARMEDUP?";
const char* g_cmd_power = "READ:POWER?";
const char* g_cmd_wl_query = "READ:WAVELENGTH?";
const char* g_cmd_wl = "WAVELENGTH";
const char* g_cmd_wl_min = "WAVELENGTH:MIN?";
const char* g_cmd_wl_max = "WAVELENGTH:MAX?";
const char* g_cmd_ON = "ON";
const char* g_cmd_OFF = "OFF";
const char* g_cmd_shutter_query = "SHUTTER?";
const char* g_cmd_shutter = "SHUTTER";


///////////////////////////////////////////////////////////////////////////////
// MaiTai control implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

/**
 * Constructor.
 */
MaiTai::MaiTai() :
   initialized_(false)
{
   // call the base class method to set-up default error codes/messages
   InitializeDefaultErrorMessages();

   // set custom error messages
   SetErrorText(ERR_NOT_WARMED_UP, "Laser not warmed up yet.");

   // Port
   CPropertyAction* pAct = new CPropertyAction (this, &MaiTai::OnComPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);
}

MaiTai::~MaiTai()
{
   Shutdown();
}

/**
 * Obtains device name.
 */
void MaiTai::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_MaiTaiDeviceName);
}

/**
 * Intializes the hardware.
 */
int MaiTai::Initialize()
{
   if (initialized_)
      return DEVICE_OK;


   // set property list
   // -----------------

   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, g_MaiTaiDeviceName, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "MaiTai lser control", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Shutter
   CPropertyAction *pAct = new CPropertyAction (this, &MaiTai::OnShutter);
   ret = CreateProperty(g_PropertyShutter, "0", MM::Integer, false, pAct);
   assert(ret == DEVICE_OK);

   vector<string> shutterValues;
   shutterValues.push_back("0");
   shutterValues.push_back("1");
   ret = SetAllowedValues(g_PropertyShutter, shutterValues);
   if (ret != DEVICE_OK)
      return ret;

   // Wavelength
   pAct = new CPropertyAction (this, &MaiTai::OnWavelength);
   ret = CreateProperty(g_PropertyWL, "0", MM::Integer, false, pAct);
   assert(ret == DEVICE_OK);

   SetPropertyLimits(g_PropertyWL, 400, 1000);

   // Power
   pAct = new CPropertyAction (this, &MaiTai::OnPower);
   ret = CreateProperty(g_PropertyPower, "0", MM::Float, true, pAct);
   assert(ret == DEVICE_OK);

   // Warmed up
   pAct = new CPropertyAction (this, &MaiTai::OnWarmedup);
   ret = CreateProperty(g_PropertyWarmedup, "0", MM::String, true, pAct);
   assert(ret == DEVICE_OK);

   // check warmed-up status
   ret = SendSerialCommand(port_.c_str(), g_cmd_warmedup, "\n");
   if (ret != DEVICE_OK)
      return ret;

   string answer;
   ret = GetSerialAnswer(port_.c_str(), "\n", answer);
   if (ret != DEVICE_OK)
      return false;
/*
   pAct = new CPropertyAction (this, &MaiTai::OnWarmedup);
   ret = CreateProperty(g_PropertyWarmedup, "0%", MM::String, true, pAct);
   assert(ret == DEVICE_OK);
*/
   
   // determine wl limits
   ret = SendSerialCommand(port_.c_str(), g_cmd_wl_min, "\n");
   if (ret != DEVICE_OK)
      return ret;

   string ansWlMin;
   ret = GetSerialAnswer(port_.c_str(), "\n", ansWlMin);
   if (ret != DEVICE_OK)
      return false;

   ret = SendSerialCommand(port_.c_str(), g_cmd_wl_max, "\n");
   if (ret != DEVICE_OK)
      return ret;

   string ansWlMax;
   ret = GetSerialAnswer(port_.c_str(), "\n", ansWlMax);
   if (ret != DEVICE_OK)
      return false;

   int wlMin = atoi(ansWlMin.c_str());
   int wlMax = atoi(ansWlMax.c_str());
   SetPropertyLimits(g_PropertyWL, wlMin, wlMax);

   char val[MM::MaxStrLength];
   ret = GetProperty(g_PropertyPower, val);
   bool powerOn (false);
   if (ret == DEVICE_OK)
   {
      double power = atof(val);
      if (power > 0.1)
         powerOn = true;
   }

   // OnOff
   pAct = new CPropertyAction (this, &MaiTai::OnOnOff);
   ret = CreateProperty(g_PropertyOnOff, powerOn ? g_ON : g_OFF, MM::String, false, pAct);
   assert(ret == DEVICE_OK);

   vector<string> onOffValues;
   onOffValues.push_back(g_OFF);
   onOffValues.push_back(g_ON);
   ret = SetAllowedValues(g_PropertyOnOff, onOffValues);
   if (ret != DEVICE_OK)
      return ret;

   // synchronize all properties
   // --------------------------
   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;
   return DEVICE_OK;
}

/**
 * Shuts down (unloads) the device.
 */
int MaiTai::Shutdown()
{
   //if (initialized_)
   //   SetProperty(g_PropertyOnOff, g_OFF);

   initialized_ = false;

   return DEVICE_OK;
}

int MaiTai::OnPower(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
   }
   else if (eAct == MM::BeforeGet)
   {
      // query power
      int ret = SendSerialCommand(port_.c_str(), g_cmd_power, "\n");
      if (ret != DEVICE_OK)
         return ret;

      string answer;
      ret = GetSerialAnswer(port_.c_str(), "\n", answer);
      if (ret != DEVICE_OK)
         return false;
      
      pProp->Set(answer.c_str());
   }

   return DEVICE_OK;
}

int MaiTai::OnWarmedup(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
   }
   else if (eAct == MM::BeforeGet)
   {
      // query power
      int ret = SendSerialCommand(port_.c_str(), g_cmd_warmedup, "\n");
      if (ret != DEVICE_OK)
         return ret;

      string answer;
      ret = GetSerialAnswer(port_.c_str(), "\n", answer);
      if (ret != DEVICE_OK)
         return false;
      
      pProp->Set(answer.c_str());
   }

   return DEVICE_OK;
}
int MaiTai::OnWavelength(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      string wl;
      pProp->Get(wl);

      // set wavelength
      ostringstream cmd;
      cmd << g_cmd_wl << " " << wl;
      int ret = SendSerialCommand(port_.c_str(), cmd.str().c_str(), "\n");
      if (ret != DEVICE_OK)
         return ret;

   }
   else if (eAct == MM::BeforeGet)
   {
      // query wavelength
      int ret = SendSerialCommand(port_.c_str(), g_cmd_wl_query, "\n");
      if (ret != DEVICE_OK)
         return ret;

      string answer;
      ret = GetSerialAnswer(port_.c_str(), "\n", answer);
      if (ret != DEVICE_OK)
         return false;
      
      pProp->Set(answer.c_str());
   }
   return DEVICE_OK;
}

int MaiTai::OnShutter(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      long s;
      pProp->Get(s);

      // set shutter
      ostringstream cmd;
      cmd << g_cmd_shutter << " " << s;
      int ret = SendSerialCommand(port_.c_str(), cmd.str().c_str(), "\n");
      if (ret != DEVICE_OK)
         return ret;

   }
   else if (eAct == MM::BeforeGet)
   {
      // query wavelength
      int ret = SendSerialCommand(port_.c_str(), g_cmd_shutter_query, "\n");
      if (ret != DEVICE_OK)
         return ret;

      string answer;
      ret = GetSerialAnswer(port_.c_str(), "\n", answer);
      if (ret != DEVICE_OK)
         return false;
      
      pProp->Set(answer.c_str());
   }
   return DEVICE_OK;
}

int MaiTai::OnOnOff(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      string val;
      pProp->Get(val);
      if (val.compare(g_ON) == 0)
      {
         // check the warmup status
         int ret = SendSerialCommand(port_.c_str(), g_cmd_warmedup, "\n");
         if (ret != DEVICE_OK)
            return ret;

         string answer;
         ret = GetSerialAnswer(port_.c_str(), "\n", answer);
         if (ret != DEVICE_OK)
            return false;


         // send ON command
         ret = SendSerialCommand(port_.c_str(), g_cmd_ON, "\n");
         if (ret != DEVICE_OK)
            return ret;
      }
      else
      {
          // send OFF command
         int ret = SendSerialCommand(port_.c_str(), g_cmd_OFF, "\n");
         if (ret != DEVICE_OK)
            return ret;
     }
   }
   else if (eAct == MM::BeforeGet)
   {
   }
   return DEVICE_OK;
}

int MaiTai::OnComPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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
// DemoLaser control implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

/**
 * Constructor.
 */
DemoLaser::DemoLaser() :
   initialized_(false)
{
   // call the base class method to set-up default error codes/messages
   InitializeDefaultErrorMessages();

}

DemoLaser::~DemoLaser()
{
   Shutdown();
}

/**
 * Obtains device name.
 */
void DemoLaser::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_DemoLaserDeviceName);
}

/**
 * Intializes the hardware.
 */
int DemoLaser::Initialize()
{
   if (initialized_)
      return DEVICE_OK;


   // set property list
   // -----------------

   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, g_MaiTaiDeviceName, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "Demo laser control", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // OnOff
   ret = CreateProperty(g_PropertyOnOff, g_OFF, MM::String, false);
   assert(ret == DEVICE_OK);

   vector<string> onOffValues;
   onOffValues.push_back(g_OFF);
   onOffValues.push_back(g_ON);
   ret = SetAllowedValues(g_PropertyOnOff, onOffValues);
   if (ret != DEVICE_OK)
      return ret;

   // Wavelength
   ret = CreateProperty(g_PropertyWL, "0", MM::Integer, false);
   assert(ret == DEVICE_OK);

   SetPropertyLimits(g_PropertyWL, 400, 1000);

   // Power
   ret = CreateProperty(g_PropertyPower, "0", MM::Integer, true);
   assert(ret == DEVICE_OK);

   // Shutter
   ret = CreateProperty(g_PropertyShutter, "0", MM::String, true);
   assert(ret == DEVICE_OK);

   // Warmed-up
   ret = CreateProperty(g_PropertyWarmedup, "0", MM::String, true);
   assert(ret == DEVICE_OK);

   // synchronize all properties
   // --------------------------
   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;
   return DEVICE_OK;
}

/**
 * Shuts down (unloads) the device.
 */
int DemoLaser::Shutdown()
{
   initialized_ = false;

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// VShutter control implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

/**
 * Constructor.
 */
VirtualShutter::VirtualShutter() :
   initialized_(false)
{
   // call the base class method to set-up default error codes/messages
   InitializeDefaultErrorMessages();

}

VirtualShutter::~VirtualShutter()
{
   Shutdown();
}

/**
 * Obtains device name.
 */
void VirtualShutter::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_VShutterDeviceName);
}

/**
 * Intializes the hardware.
 */
int VirtualShutter::Initialize()
{
   if (initialized_)
      return DEVICE_OK;


   // set property list
   // -----------------

   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, g_VShutterDeviceName, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "Virtual dual shutter for D/A channels", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // device 1
   ret = CreateProperty(g_PropertyDev1, "", MM::String, false);
   assert(ret == DEVICE_OK);

   // device 2
   ret = CreateProperty(g_PropertyDev2, "", MM::String, false);
   assert(ret == DEVICE_OK);

   CPropertyAction* pAct = new CPropertyAction (this, &VirtualShutter::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct); 
   if (ret != DEVICE_OK) 
      return ret; 

   AddAllowedValue(MM::g_Keyword_State, "0"); // Closed
   AddAllowedValue(MM::g_Keyword_State, "1"); // Open

   // synchronize all properties
   // --------------------------
   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;
   return DEVICE_OK;
}

int VirtualShutter::SetOpen(bool open)
{
   char dev1[MM::MaxStrLength];
   GetProperty(g_PropertyDev1, dev1);

   char dev2[MM::MaxStrLength];
   GetProperty(g_PropertyDev2, dev2);

   if (strlen(dev1) > 0)
   {
      MM::SignalIO* pDev1 = GetCoreCallback()->GetSignalIODevice(this, dev1);
      int ret = pDev1->SetGateOpen(open);
      if (ret != DEVICE_OK)
         return ret;
   }

   if (strlen(dev2) > 0)
   {
      MM::SignalIO* pDev2 = GetCoreCallback()->GetSignalIODevice(this, dev2);
      int ret = pDev2->SetGateOpen(open);
      if (ret != DEVICE_OK)
         return ret;
   }

   return DEVICE_OK;
}

int VirtualShutter::GetOpen(bool& open)
{
   char dev1[MM::MaxStrLength];
   GetProperty(g_PropertyDev1, dev1);

   char dev2[MM::MaxStrLength];
   GetProperty(g_PropertyDev2, dev2);

   bool open1(false);

   if (strlen(dev1) > 0)
   {
      MM::SignalIO* pDev1 = GetCoreCallback()->GetSignalIODevice(this, dev1);
      if (pDev1)
      {
         int ret = pDev1->GetGateOpen(open1);
         if (ret != DEVICE_OK)
            return ret;
      }
      else
         return ERR_UNKNOWN_DA_DEVICE;
   }

   bool open2;

   if (strlen(dev2) > 0)
   {
      MM::SignalIO* pDev2 = GetCoreCallback()->GetSignalIODevice(this, dev2);
      if (pDev2)
      {
         int ret = pDev2->GetGateOpen(open2);
         if (ret != DEVICE_OK)
            return ret;
      }
      else
         return ERR_UNKNOWN_DA_DEVICE;
   }
   else
      open2 = open1;


   assert(open1 == open2);
   open = open1;

   return DEVICE_OK;
}


int VirtualShutter::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      bool open;
      int ret = GetOpen(open);
      if (ret != DEVICE_OK)
         return ret;

      pProp->Set(open ? "1" : "0");
   }
   else if (eAct == MM::AfterSet)
   {
      string val;
      pProp->Get(val);
      if (val.compare("1") == 0)
         return SetOpen(true);
      else
         return SetOpen(false);
   }

   return DEVICE_OK;
}
