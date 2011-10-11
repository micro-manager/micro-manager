///////////////////////////////////////////////////////////////////////////////
// FILE:          K8061.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Velleman K8061 adapter 
// COPYRIGHT:     University of California, San Francisco, 2007
// LICENSE:       LGPL
// 
// AUTHOR:        Nico Stuurman, nico@cmp.ucsf.edu 11/02/2007
//
//

#include "K8061.h"
#include "../../MMDevice/ModuleInterface.h"
#include <cstdio>

#ifdef WIN32
   #define WIN32_LEAN_AND_MEAN
   #include <windows.h>
   #define snprintf _snprintf 
#endif

using namespace std;

const char* g_DeviceNameK8061Hub = "K8061-Hub";
const char* g_DeviceNameK8061Switch = "K8061-Switch";
const char* g_DeviceNameK8061Shutter = "K8061-Shutter";
const char* g_DeviceNameK8061DA = "K8061-DAC";
const char* g_DeviceNameK8061Input = "K8061-Input";

K8061Interface g_K8061Interface;

// Global state of the K8061 switch to enable simulation of the shutter device.
// The virtual shutter device uses this global variable to restore state of the switch
unsigned g_switchState_ = 0;
unsigned g_shutterState_ = 0;
bool g_boardInitialized_ = false;
bool g_invertedLogic = false;
const char* g_normalLogicString = "Normal";
const char* g_invertedLogicString = "Inverted";



///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   AddAvailableDeviceName(g_DeviceNameK8061Hub, "Hub");
   AddAvailableDeviceName(g_DeviceNameK8061Switch, "Switch");
   AddAvailableDeviceName(g_DeviceNameK8061Shutter, "Shutter");
   AddAvailableDeviceName(g_DeviceNameK8061DA, "DA");
   AddAvailableDeviceName(g_DeviceNameK8061Input, "Input");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_DeviceNameK8061Hub) == 0)
   {
      return new CK8061Hub;
   }
   else if (strcmp(deviceName, g_DeviceNameK8061Switch) == 0)
   {
      return new CK8061Switch;
   }
   else if (strcmp(deviceName, g_DeviceNameK8061Shutter) == 0)
   {
      return new CK8061Shutter;
   }
   else if (strcmp(deviceName, g_DeviceNameK8061DA) == 0)
   {
      return new CK8061DA();
   }
   else if (strcmp(deviceName, g_DeviceNameK8061Input) == 0)
   {
      return new CK8061Input;
   }


   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
// CK8061HUb implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~
//
CK8061Hub::CK8061Hub() :
initialized_ (false)
{
   InitializeDefaultErrorMessages();

   SetErrorText(ERR_PORT_OPEN_FAILED, "Failed opening Velleman K8061 USB device");

   CPropertyAction* pAct = new CPropertyAction(this, &CK8061Hub::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

   pAct = new CPropertyAction(this, &CK8061Hub::OnLogic);
   CreateProperty("Logic", g_invertedLogicString, MM::String, false, pAct, true);

   AddAllowedValue("Logic", g_invertedLogicString);
   AddAllowedValue("Logic", g_normalLogicString);
}

CK8061Hub::~CK8061Hub()
{
   Shutdown();
}

void CK8061Hub::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_DeviceNameK8061Hub);
}

bool CK8061Hub::Busy()
{
   return false;
}

int CK8061Hub::Initialize()
{
   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, g_DeviceNameK8061Hub, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;
   return DEVICE_OK;
}

int CK8061Hub::Shutdown()
{
   initialized_ = false;
   return DEVICE_OK;
}

int CK8061Hub::OnPort(MM::PropertyBase* pProp, MM::ActionType pAct)
{
   if (pAct == MM::BeforeGet)
   {
      pProp->Set(port_.c_str());
   }
   else if (pAct == MM::AfterSet)
   {
      pProp->Get(port_);
      g_K8061Interface.SetPort(port_);
      g_K8061Interface.initialized_ = true;
      int ret = g_K8061Interface.OpenDevice();
      if (ret != DEVICE_OK)
         return ret;
   }
   return DEVICE_OK;
}

int CK8061Hub::OnLogic(MM::PropertyBase* pProp, MM::ActionType pAct)
{
   if (pAct == MM::BeforeGet)
   {
      if (g_invertedLogic)
         pProp->Set(g_invertedLogicString);
      else
         pProp->Set(g_normalLogicString);
   } else if (pAct == MM::AfterSet)
   {
      std::string logic;
      pProp->Get(logic);
      if (logic.compare(g_invertedLogicString)==0)
         g_invertedLogic = true;
      else g_invertedLogic = false;
   }
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// CK8061Switch implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~

CK8061Switch::CK8061Switch() : numPos_(256), busy_(false)
{
   InitializeDefaultErrorMessages();

   // add custom error messages
   SetErrorText(ERR_UNKNOWN_POSITION, "Invalid position (state) specified");
   SetErrorText(ERR_INITIALIZE_FAILED, "Initialization of the device failed");
   SetErrorText(ERR_WRITE_FAILED, "Failed to write data to the device");
   SetErrorText(ERR_CLOSE_FAILED, "Failed closing the device");
}

CK8061Switch::~CK8061Switch()
{
   Shutdown();
}

void CK8061Switch::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_DeviceNameK8061Switch);
}


int CK8061Switch::Initialize()
{
   if (!g_boardInitialized_) {
      // int ret = InitializeTheBoard();
      // if (ret != DEVICE_OK)
      //  return ret;
   }

   // set property list
   // -----------------
   
   // Name
   int nRet = CreateProperty(MM::g_Keyword_Name, g_DeviceNameK8061Switch, MM::String, true);
   if (DEVICE_OK != nRet)
      return nRet;

   // Description
   nRet = CreateProperty(MM::g_Keyword_Description, "K8061 digital output driver", MM::String, true);
   if (DEVICE_OK != nRet)
      return nRet;

   // create positions and labels
   const int bufSize = 255;
   char buf[bufSize];
   for (long i=0; i<numPos_; i++)
   {
      snprintf(buf, bufSize, "%d", (unsigned)i);
      SetPositionLabel(i, buf);
   }

   // State
   // -----
   CPropertyAction* pAct = new CPropertyAction (this, &CK8061Switch::OnState);
   nRet = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
   if (nRet != DEVICE_OK)
      return nRet;
   SetPropertyLimits(MM::g_Keyword_State, 0, 255);

   // Label
   // -----
   pAct = new CPropertyAction (this, &CStateBase::OnLabel);
   nRet = CreateProperty(MM::g_Keyword_Label, "", MM::String, false, pAct);
   if (nRet != DEVICE_OK)
      return nRet;

   nRet = UpdateStatus();
   if (nRet != DEVICE_OK)
      return nRet;

   initialized_ = true;

   return DEVICE_OK;
}

int CK8061Switch::Shutdown()
{
   initialized_ = false;
   return DEVICE_OK;
}

int CK8061Switch::WriteToPort(long value)
{
   if (g_invertedLogic)
      value = (255 & ~value);
   int ret = g_K8061Interface.OutputAllDigital(*this, *GetCoreCallback(), (unsigned char) value);
   if (ret != DEVICE_OK)
   {
      this->LogMessage("Error reported by libk8055 function WriteAllDigital");
      return ret;
   }

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int CK8061Switch::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // nothing to do, let the caller to use cached property
   }
   else if (eAct == MM::AfterSet)
   {
      long pos;
      pProp->Get(pos);
      g_switchState_ = pos;
      if (g_shutterState_ > 0)
         return WriteToPort(pos);
   }

   return DEVICE_OK;
}



///////////////////////////////////////////////////////////////////////////////
// CK8061DA implementation
// ~~~~~~~~~~~~~~~~~~~~~~

CK8061DA::CK8061DA() :
      busy_(false), 
      minV_(0.0), 
      maxV_(0.0), 
      volts_(0.0),
      gatedVolts_(0.0),
      encoding_(0), 
      resolution_(8), 
      channel_(1), 
      name_(""),
      gateOpen_(true)
{
   InitializeDefaultErrorMessages();

   // add custom error messages
   SetErrorText(ERR_UNKNOWN_POSITION, "Invalid position (state) specified");
   SetErrorText(ERR_INITIALIZE_FAILED, "Initialization of the device failed");
   SetErrorText(ERR_WRITE_FAILED, "Failed to write data to the device");
   SetErrorText(ERR_CLOSE_FAILED, "Failed closing the device");

   CPropertyAction* pAct = new CPropertyAction(this, &CK8061DA::OnChannel);
   CreateProperty("Channel", "1", MM::Integer, false, pAct, true);
   for (int i=1; i< 9; i++){
      ostringstream os;
      os << i;
      AddAllowedValue("Channel", os.str().c_str());
   }

   pAct = new CPropertyAction(this, &CK8061DA::OnMaxVolt);
   CreateProperty("MaxVolt", "5.0", MM::Float, false, pAct, true);
   AddAllowedValue("MaxVolt", "5.0");
   AddAllowedValue("MaxVolt", "10.0");
}

CK8061DA::~CK8061DA()
{
   Shutdown();
}

void CK8061DA::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}


int CK8061DA::Initialize()
{
   // obtain scaling info

   minV_ = 0.0;
   maxV_ = 5.0;

   // set property list
   // -----------------
   
   // Name
   int nRet = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != nRet)
      return nRet;

   // Description
   nRet = CreateProperty(MM::g_Keyword_Description, "K8061 DAC driver", MM::String, true);
   if (DEVICE_OK != nRet)
      return nRet;

   // State
   // -----
   CPropertyAction* pAct = new CPropertyAction (this, &CK8061DA::OnVolts);
   nRet = CreateProperty("Volts", "0.0", MM::Float, false, pAct);
   if (nRet != DEVICE_OK)
      return nRet;
   SetPropertyLimits("Volts", 0.0, 5.0);

   nRet = UpdateStatus();
   if (nRet != DEVICE_OK)
      return nRet;

   initialized_ = true;

   return DEVICE_OK;
}

int CK8061DA::Shutdown()
{
   initialized_ = false;
   return DEVICE_OK;
}

int CK8061DA::WriteToPort(long value)
{
   int ret = g_K8061Interface.OutputAnalogChannel(*this, *GetCoreCallback(), (unsigned char) channel_, (unsigned char) value);
   if (ret != DEVICE_OK)
   {
      this->LogMessage("Error reported by libk8061 function WriteAllDigital");
      return ret;
   }

   return DEVICE_OK;
}

int CK8061DA::WriteSignal(double volts)
{
   long value = (long) ((1L<<resolution_)/((float)maxV_ - (float)minV_) * (volts - (float)minV_));
   value = min((1L<<resolution_)-1,value);

   if (encoding_ != 0) {
      // convert to 2's comp by inverting the sign bit
      long sign = 1L << (resolution_ - 1);
      value ^= sign;
      if (value & sign)           //sign extend
         value |= 0xffffffffL << resolution_;
   }
   return WriteToPort(value);
}

int CK8061DA::SetSignal(double volts)
{
   volts_ = volts;
   if (gateOpen_) {
      gatedVolts_ = volts_;
      return WriteSignal(volts_);
   } else {
      gatedVolts_ = 0;
   }

   return DEVICE_OK;
}

int CK8061DA::SetGateOpen(bool open)
{
   if (open) {
      gateOpen_ = true;
      gatedVolts_ = volts_;
      return WriteSignal(volts_);
   } else {
      gateOpen_ = false;
      gatedVolts_ = 0;
   }

   return WriteSignal(0.0);
   
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int CK8061DA::OnVolts(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // nothing to do, let the caller use cached property
   }
   else if (eAct == MM::AfterSet)
   {
      double volts;
      pProp->Get(volts);
      return SetSignal(volts);
   }

   return DEVICE_OK;
}

int CK8061DA::OnMaxVolt(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(maxV_);
   }
   else if (eAct == MM::AfterSet)
   {
      double maxV;
      pProp->Get(maxV);
      if (maxV == 5.0 || maxV == 10.0)
         maxV_ = maxV;
      if (maxV == 5.0)
         SetPropertyLimits("Volts", 0.0, 5.0);
      else if (maxV == 10.0)
         SetPropertyLimits("Volts", 0.0, 10.0);

   }
   return DEVICE_OK;
}

int CK8061DA::OnChannel(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set((long int)channel_);
   }
   else if (eAct == MM::AfterSet)
   {
      long channel;
      pProp->Get(channel);
      if (channel >=1 && channel <=8)
         channel_ = channel;
   }
   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// CK8061Shutter implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~~

CK8061Shutter::CK8061Shutter() : initialized_(false), name_(g_DeviceNameK8061Shutter)
{
   InitializeDefaultErrorMessages();
   EnableDelay();
}

CK8061Shutter::~CK8061Shutter()
{
   Shutdown();
}

void CK8061Shutter::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

bool CK8061Shutter::Busy()
{
   MM::MMTime interval = GetCurrentMMTime() - changedTime_;

   if (interval < (1000.0 < GetDelayMs() ))
      return true;
   else
       return false;
}

int CK8061Shutter::Initialize()
{
   // set property list
   // -----------------
   
   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "K8061 shutter driver", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // OnOff
   // ------
   CPropertyAction* pAct = new CPropertyAction (this, &CK8061Shutter::OnOnOff);
   ret = CreateProperty("OnOff", "0", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // set shutter into the off state
   WriteToPort(0);

   vector<string> vals;
   vals.push_back("0");
   vals.push_back("1");
   ret = SetAllowedValues("OnOff", vals);
   if (ret != DEVICE_OK)
      return ret;

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   changedTime_ = GetCurrentMMTime();
   initialized_ = true;

   return DEVICE_OK;
}

int CK8061Shutter::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

int CK8061Shutter::SetOpen(bool open)
{
   if (open)
      return SetProperty("OnOff", "1");
   else
      return SetProperty("OnOff", "0");
}

int CK8061Shutter::GetOpen(bool& open)
{
   char buf[MM::MaxStrLength];
   int ret = GetProperty("OnOff", buf);
   if (ret != DEVICE_OK)
      return ret;
   long pos = atol(buf);
   pos > 0 ? open = true : open = false;

   return DEVICE_OK;
}

int CK8061Shutter::Fire(double /*deltaT*/)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

int CK8061Shutter::WriteToPort(long value)
{
   if (g_invertedLogic)
      value = (255 & ~value);
   printf("Shutter is writing %ld\n", value);
   int ret = g_K8061Interface.OutputAllDigital(*this, *GetCoreCallback(), (unsigned char) value);
   if (ret != DEVICE_OK)
   {
      this->LogMessage("Error reported by libk8055 function WriteAllDigital");
      return ret;
   }

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int CK8061Shutter::OnOnOff(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // use cached state
      pProp->Set((long)g_shutterState_);
   }
   else if (eAct == MM::AfterSet)
   {
      long pos;
      pProp->Get(pos);
      int ret;
      if (pos == 0)
         ret = WriteToPort(0); // turn everything off
      else
         ret = WriteToPort(g_switchState_); // restore old setting
      if (ret != DEVICE_OK)
         return ret;
      g_shutterState_ = pos;
      changedTime_ = GetCurrentMMTime();
   }

   return DEVICE_OK;
}


/*
 * Reads digital and analogue input from Velleman board
 * Mainly to test read functions in USBManager
 */

CK8061Input::CK8061Input() :
name_(g_DeviceNameK8061Input)
{
}

CK8061Input::~CK8061Input()
{
   Shutdown();
}

int CK8061Input::Shutdown()
{
   initialized_ = false;
   return DEVICE_OK;
}

int CK8061Input::Initialize()
{
   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "K8061 input", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Digital Input
   CPropertyAction* pAct = new CPropertyAction (this, &CK8061Input::OnDigitalInput);
   ret = CreateProperty("Digital Input", "0", MM::Integer, true, pAct);
   if (ret != DEVICE_OK)
      return ret;

   for (long i=1; i <=8; i++) 
   {
      CPropertyActionEx *pExAct = new CPropertyActionEx(this, &CK8061Input::OnAnalogInput, i);
      ostringstream os;
      os << "Analogue Input A" << i;
      ret = CreateProperty(os.str().c_str(), "0.0", MM::Float, true, pExAct);
      if (ret != DEVICE_OK)
         return ret;
   }

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

void CK8061Input::GetName(char* name) const
{
  CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

bool CK8061Input::Busy()
{
   return false;
}


///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int CK8061Input::OnDigitalInput(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      unsigned char result;
      int ret =  g_K8061Interface.ReadAllDigital(*this, *GetCoreCallback(), result);
      if (ret != DEVICE_OK)
         return ret;
      pProp->Set((long)result);
   }

   return DEVICE_OK;
}

int CK8061Input::OnAnalogInput(MM::PropertyBase* pProp, MM::ActionType eAct, long channel)
{
   if (eAct == MM::BeforeGet)
   {
      long result;
      int ret = g_K8061Interface.ReadAnalogChannel(*this, *GetCoreCallback(), (unsigned char) channel, result);
      if (ret != DEVICE_OK)
         return ret;

      pProp->Set(result);
   }
   return DEVICE_OK;
}

