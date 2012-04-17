///////////////////////////////////////////////////////////////////////////////
// FILE:          VirtualShutter.cpp
// PROJECT:       100X micro-manager extensions
// SUBSYSTEM:     DeviceAdapters : TwoPhoton
//-----------------------------------------------------------------------------
// DESCRIPTION:   Dual shutter for two D/A channels
//                
// AUTHOR:        Nenad Amodaj, February 2010
//
// COPYRIGHT:     100X Imaging Inc, 2010
//                

#include "TwoPhoton.h"
#include <sstream>
#include "DeviceUtils.h"

using namespace std;

const char* g_VShutterDeviceName = "VDualShuuter";
const char* g_DemoLaserDeviceName = "DemoLaser";

const char* g_PropertyOnOff = "OnOff";
const char* g_PropertyPower = "Power";
const char* g_PropertyWL = "Wavelength";
const char* g_PropertyShutter = "Shutter";
const char* g_PropertyWarmedup = "Warmedup";

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

   // OnOff
   CPropertyAction *pAct = new CPropertyAction (this, &MaiTai::OnOnOff);
   ret = CreateProperty(g_PropertyOnOff, g_OFF, MM::String, false, pAct);
   assert(ret == DEVICE_OK);

   vector<string> onOffValues;
   onOffValues.push_back(g_OFF);
   onOffValues.push_back(g_ON);
   ret = SetAllowedValues(g_PropertyOnOff, onOffValues);
   if (ret != DEVICE_OK)
      return ret;

   // Shutter
   pAct = new CPropertyAction (this, &MaiTai::OnShutter);
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

   // Power
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
   if (initialized_)
      SetProperty(g_PropertyOnOff, g_OFF);

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

