///////////////////////////////////////////////////////////////////////////////
// FILE:          K8055.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Velleman K8055 adapter 
// COPYRIGHT:     University of California, San Francisco, 2007
// LICENSE:       LGPL
// 
// AUTHOR:        Nico Stuurman, nico@cmp.ucsf.edu 11/02/2007
//
//

#include "K8055.h"
#include <cstdio>

#ifdef WIN32
   #define WIN32_LEAN_AND_MEAN
   #include <windows.h>
   #define snprintf _snprintf 
#endif

using namespace std;

const char* g_DeviceNameK8055Hub = "K8055-Hub";
const char* g_DeviceNameK8055Switch = "K8055-Switch";
const char* g_DeviceNameK8055Shutter = "K8055-Shutter";
const char* g_DeviceNameK8055DA1 = "K8055-DAC-1";
const char* g_DeviceNameK8055DA2 = "K8055-DAC-2";
const char* g_DeviceNameK8055Input = "K8055-Input";

K8055Interface g_K8055Interface;

// Global state of the K8055 switch to enable simulation of the shutter device.
// The virtual shutter device uses this global variable to restore state of the switch
unsigned g_switchState_ = 0;
unsigned g_shutterState_ = 0;
bool g_boardInitialized_ = false;
bool g_invertedLogic = false;
const char* g_normalLogicString = "Normal";
const char* g_invertedLogicString = "Inverted";

/*
 * Initilize the board
 */
/*
int InitializeTheBoard()
{
   // Get first available K8055 
   int boardAddress = -1;
   while (boardAddress < 4 && !g_boardInitialized_) {
      boardAddress += 1;
      if (OpenDevice(boardAddress) >= 0)
         g_boardInitialized_ = true;
   }

   if (g_boardInitialized_) 
      return DEVICE_OK;
   else
      return ERR_BOARD_NOT_FOUND;
}

*/

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   AddAvailableDeviceName(g_DeviceNameK8055Hub, "Hub");
   AddAvailableDeviceName(g_DeviceNameK8055Switch, "Switch");
   AddAvailableDeviceName(g_DeviceNameK8055Shutter, "Shutter" );
   AddAvailableDeviceName(g_DeviceNameK8055DA1, "DA-1");
   AddAvailableDeviceName(g_DeviceNameK8055DA2, "DA-2");
   AddAvailableDeviceName(g_DeviceNameK8055Input, "Digital Input");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_DeviceNameK8055Hub) == 0)
   {
      return new CK8055Hub;
   }
   else if (strcmp(deviceName, g_DeviceNameK8055Switch) == 0)
   {
      return new CK8055Switch;
   }
   else if (strcmp(deviceName, g_DeviceNameK8055Shutter) == 0)
   {
      return new CK8055Shutter;
   }
   else if (strcmp(deviceName, g_DeviceNameK8055DA1) == 0)
   {
      return new CK8055DA(1, g_DeviceNameK8055DA1);
   }
   else if (strcmp(deviceName, g_DeviceNameK8055DA2) == 0)
   {
      return new CK8055DA(2, g_DeviceNameK8055DA2);
   }
   else if (strcmp(deviceName, g_DeviceNameK8055Input) == 0)
   {
      return new CK8055Input;
   }


   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
// CK8055HUb implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~
//
CK8055Hub::CK8055Hub() :
initialized_ (false)
{
   InitializeDefaultErrorMessages();

   SetErrorText(ERR_PORT_OPEN_FAILED, "Failed opening Velleman K8055 USB device");

   CPropertyAction* pAct = new CPropertyAction(this, &CK8055Hub::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

   pAct = new CPropertyAction(this, &CK8055Hub::OnLogic);
   CreateProperty("Logic", g_invertedLogicString, MM::String, false, pAct, true);

   AddAllowedValue("Logic", g_invertedLogicString);
   AddAllowedValue("Logic", g_normalLogicString);
}

CK8055Hub::~CK8055Hub()
{
   Shutdown();
}

void CK8055Hub::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_DeviceNameK8055Hub);
}

bool CK8055Hub::Busy()
{
   return false;
}

int CK8055Hub::Initialize()
{
   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, g_DeviceNameK8055Hub, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;
   return DEVICE_OK;
}

int CK8055Hub::Shutdown()
{
   initialized_ = false;
   return DEVICE_OK;
}

int CK8055Hub::OnPort(MM::PropertyBase* pProp, MM::ActionType pAct)
{
   if (pAct == MM::BeforeGet)
   {
      pProp->Set(port_.c_str());
   }
   else if (pAct == MM::AfterSet)
   {
      pProp->Get(port_);
      g_K8055Interface.port_ = port_;
      g_K8055Interface.initialized_ = true;
      int ret = g_K8055Interface.OpenDevice();
      if (ret != DEVICE_OK)
         return ret;
   }
   return DEVICE_OK;
}

int CK8055Hub::OnLogic(MM::PropertyBase* pProp, MM::ActionType pAct)
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
// CK8055Switch implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~

CK8055Switch::CK8055Switch() : numPos_(256), busy_(false)
{
   InitializeDefaultErrorMessages();

   // add custom error messages
   SetErrorText(ERR_UNKNOWN_POSITION, "Invalid position (state) specified");
   SetErrorText(ERR_INITIALIZE_FAILED, "Initialization of the device failed");
   SetErrorText(ERR_WRITE_FAILED, "Failed to write data to the device");
   SetErrorText(ERR_CLOSE_FAILED, "Failed closing the device");
}

CK8055Switch::~CK8055Switch()
{
   Shutdown();
}

void CK8055Switch::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_DeviceNameK8055Switch);
}


int CK8055Switch::Initialize()
{
   if (!g_boardInitialized_) {
      // int ret = InitializeTheBoard();
      // if (ret != DEVICE_OK)
      //  return ret;
   }

   // set property list
   // -----------------
   
   // Name
   int nRet = CreateProperty(MM::g_Keyword_Name, g_DeviceNameK8055Switch, MM::String, true);
   if (DEVICE_OK != nRet)
      return nRet;

   // Description
   nRet = CreateProperty(MM::g_Keyword_Description, "K8055 digital output driver", MM::String, true);
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
   CPropertyAction* pAct = new CPropertyAction (this, &CK8055Switch::OnState);
   nRet = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
   if (nRet != DEVICE_OK)
      return nRet;
   SetPropertyLimits(MM::g_Keyword_State, 0, 255);

   // Label
   // -----
   pAct = new CPropertyAction (this, &CStateBase::OnLabel);
	nRet = CreateProperty(MM::g_Keyword_Label, "", MM::Integer , false, pAct);
   if (nRet != DEVICE_OK)
      return nRet;

   nRet = UpdateStatus();
   if (nRet != DEVICE_OK)
      return nRet;

   initialized_ = true;

   return DEVICE_OK;
}

int CK8055Switch::Shutdown()
{
   initialized_ = false;
   return DEVICE_OK;
}

int CK8055Switch::WriteToPort(long value)
{
   if (g_invertedLogic)
      value = (255 & ~value);
   int ret = g_K8055Interface.WriteAllDigital(*this, *GetCoreCallback(), value);
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

int CK8055Switch::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
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
// CK8055DA implementation
// ~~~~~~~~~~~~~~~~~~~~~~

CK8055DA::CK8055DA(unsigned channel, const char* name) :
      busy_(false), 
      minV_(0.0), 
      maxV_(5.0), 
      volts_(0.0),
      gatedVolts_(0.0),
      encoding_(0), 
      resolution_(8), 
      channel_(channel), 
      name_(name),
      gateOpen_(true)
{
   InitializeDefaultErrorMessages();

   // add custom error messages
   SetErrorText(ERR_UNKNOWN_POSITION, "Invalid position (state) specified");
   SetErrorText(ERR_INITIALIZE_FAILED, "Initialization of the device failed");
   SetErrorText(ERR_WRITE_FAILED, "Failed to write data to the device");
   SetErrorText(ERR_CLOSE_FAILED, "Failed closing the device");
}

CK8055DA::~CK8055DA()
{
   Shutdown();
}

void CK8055DA::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}


int CK8055DA::Initialize()
{
   // set property list
   // -----------------
   
   // Name
   int nRet = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != nRet)
      return nRet;

   // Description
   nRet = CreateProperty(MM::g_Keyword_Description, "K8055 DAC driver", MM::String, true);
   if (DEVICE_OK != nRet)
      return nRet;

   // State
   // -----
   CPropertyAction* pAct = new CPropertyAction (this, &CK8055DA::OnVolts);
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

int CK8055DA::Shutdown()
{
   initialized_ = false;
   return DEVICE_OK;
}

int CK8055DA::WriteToPort(long value)
{
   int ret = g_K8055Interface.OutputAnalogChannel(*this, *GetCoreCallback(), channel_, (int) value);
   if (ret != DEVICE_OK)
   {
      this->LogMessage("Error reported by libk8055 function WriteAllDigital");
      return ret;
   }

   return DEVICE_OK;
}

int CK8055DA::WriteSignal(double volts)
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

int CK8055DA::SetSignal(double volts)
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

int CK8055DA::SetGateOpen(bool open)
{
   if (open) {
      gateOpen_ = true;
      gatedVolts_ = volts_;
      return WriteSignal(volts_);
   } else {
      gateOpen_ = false;
      gatedVolts_ = 0;
      return WriteSignal(0.0);
   }
}


///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int CK8055DA::OnVolts(MM::PropertyBase* pProp, MM::ActionType eAct)
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


///////////////////////////////////////////////////////////////////////////////
// CK8055Shutter implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~~

CK8055Shutter::CK8055Shutter() : initialized_(false), name_(g_DeviceNameK8055Shutter)
{
   InitializeDefaultErrorMessages();
   EnableDelay();
}

CK8055Shutter::~CK8055Shutter()
{
   Shutdown();
}

void CK8055Shutter::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

bool CK8055Shutter::Busy()
{
   MM::MMTime interval = GetCurrentMMTime() - changedTime_;

   if (interval < (1000.0 * GetDelayMs() ))
      return true;
   else
       return false;
}

int CK8055Shutter::Initialize()
{
   /*
   if (!g_boardInitialized_) {
      int ret = InitializeTheBoard();
      if (ret != DEVICE_OK)
         return ret;
   }
   */

   // set property list
   // -----------------
   
   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "K8055 shutter driver", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // OnOff
   // ------
   CPropertyAction* pAct = new CPropertyAction (this, &CK8055Shutter::OnOnOff);
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

int CK8055Shutter::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

int CK8055Shutter::SetOpen(bool open)
{
   if (open)
      return SetProperty("OnOff", "1");
   else
      return SetProperty("OnOff", "0");
}

int CK8055Shutter::GetOpen(bool& open)
{
   char buf[MM::MaxStrLength];
   int ret = GetProperty("OnOff", buf);
   if (ret != DEVICE_OK)
      return ret;
   long pos = atol(buf);
   pos > 0 ? open = true : open = false;

   return DEVICE_OK;
}

int CK8055Shutter::Fire(double /*deltaT*/)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

int CK8055Shutter::WriteToPort(long value)
{
   // The logic needs to be inverted...
   if (g_invertedLogic)
      value = (255 & ~value);
   printf("Shutter is writing %ld\n", value);
   int ret = g_K8055Interface.WriteAllDigital(*this, *GetCoreCallback(), value);
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

int CK8055Shutter::OnOnOff(MM::PropertyBase* pProp, MM::ActionType eAct)
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

CK8055Input::CK8055Input() :
name_(g_DeviceNameK8055Input)
{
}

CK8055Input::~CK8055Input()
{
   Shutdown();
}

int CK8055Input::Shutdown()
{
   initialized_ = false;
   return DEVICE_OK;
}

int CK8055Input::Initialize()
{
   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "K8055 input", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Digital Input
   CPropertyAction* pAct = new CPropertyAction (this, &CK8055Input::OnDigitalInput);
   ret = CreateProperty("Digital Input", "0", MM::Integer, true, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // Analogue input Channel 1
   pAct = new CPropertyAction (this, &CK8055Input::OnAnalogInput1);
   ret = CreateProperty("Analogue Input A1", "0.0", MM::Float, true, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // Analogue input Channel 2
   pAct = new CPropertyAction (this, &CK8055Input::OnAnalogInput2);
   ret = CreateProperty("Analogue Input A2", "0.0", MM::Float, true, pAct);
   if (ret != DEVICE_OK)
      return ret;

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

void CK8055Input::GetName(char* name) const
{
  CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

bool CK8055Input::Busy()
{
   return false;
}

/*
int CK8055Input::ReadFromPort(long value)
{
   int ret = g_K8055Interface.WriteAllDigital(*this, *GetCoreCallback(), value);
   if (ret != DEVICE_OK)
   {
      this->LogMessage("Error reported by libk8055 function WriteAllDigital");
      return ret;
   }

   return DEVICE_OK;
}
*/

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int CK8055Input::OnDigitalInput(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      long result = g_K8055Interface.ReadAllDigital(*this, *GetCoreCallback());
      pProp->Set(result);
   }

   return DEVICE_OK;
}

int CK8055Input::OnAnalogInput1(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      long result = g_K8055Interface.ReadAnalogChannel(*this, *GetCoreCallback(), 1);
      pProp->Set(result);
   }

   return DEVICE_OK;
}

int CK8055Input::OnAnalogInput2(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      long result = g_K8055Interface.ReadAnalogChannel(*this, *GetCoreCallback(), 2);
      pProp->Set(result);
   }

   return DEVICE_OK;
}
