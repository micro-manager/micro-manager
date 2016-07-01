///////////////////////////////////////////////////////////////////////////////
// FILE:          AgilentLaserCombiner.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Agilent AgilentLaserCombiner adapter 
// COPYRIGHT:     100xImaging, Inc. 2010
//
//
// #define LASERSDK_EXPORTS


#ifdef WIN32
   #define WIN32_LEAN_AND_MEAN
   #include <windows.h>
   #define snprintf _snprintf 
#endif

#include "AgilentLaserCombiner.h"
#include "LaserCombinerSDK.h"
#include "ErrorCodes.h"
#include "../../MMDevice/ModuleInterface.h"
#include <cstdio>


const char* g_DeviceNameLCShutter = "LC-Shutter";
const char* g_DeviceNameLCSafetyShutter = "LC-SafetyShutter";
const char* g_DeviceNameLCDA = "LC-DAC";
const char* g_fiber_1 = "Fiber 1";
const char* g_fiber_2 = "Fiber 2";


// Global state of the LC switch to enable simulation of the shutter device.
// The virtual shutter device uses this global variable to restore state of the switch


///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_DeviceNameLCShutter, MM::ShutterDevice, "Shutter");
   RegisterDevice(g_DeviceNameLCSafetyShutter, MM::ShutterDevice, "SafetyShutter");
   RegisterDevice(g_DeviceNameLCDA, MM::SignalIODevice, "DA");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_DeviceNameLCShutter) == 0)
   {
      return new LCShutter();
   }
   else if (strcmp(deviceName, g_DeviceNameLCSafetyShutter) == 0)
   {
      return new LCSafetyShutter();
   }
   else if (strcmp(deviceName, g_DeviceNameLCDA) == 0)
   {
      return new LCDA();
   }


   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}



///////////////////////////////////////////////////////////////////////////////
// LCDA implementation
// ~~~~~~~~~~~~~~~~~~~~~~

LCDA::LCDA() :
      busy_(false), 
      minV_(0.0), 
      maxV_(10.0), 
      bitDepth_(14),
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
   SetErrorText(ERR_BOARD_NOT_FOUND,GS_ERR_BOARD_NOT_FOUND);
   SetErrorText(ERR_UNKNOWN_POSITION, "Invalid position (state) specified");
   SetErrorText(ERR_INITIALIZE_FAILED, "Initialization of the device failed");
   SetErrorText(ERR_WRITE_FAILED, "Failed to write data to the device");
   SetErrorText(ERR_CLOSE_FAILED, "Failed closing the device");

   CPropertyAction* pAct = new CPropertyAction(this, &LCDA::OnChannel);
   CreateProperty("Channel", "1", MM::Integer, false, pAct, true);
   // TODO: The SDK can give us the number of channels, but only after the board is open
   for (int i=1; i< 5; i++){
      std::ostringstream os;
      os << i;
      AddAllowedValue("Channel", os.str().c_str());
   }

}

LCDA::~LCDA()
{
   Shutdown();
}

void LCDA::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}


int LCDA::Initialize()
{
   if (!LaserBoardIsOpen()) {
      int ret = LaserBoardOpen();
      if (ret != NO_ERR)
         return ret;
   }
   
   char version[64];
   int ret = LaserBoardFirmwareVersion(version);
   if (ret != NO_ERR)
      return ret;
   
   ret = LaserBoardGetAOInfo(channel_ - 1, &minV_, &maxV_, &bitDepth_);
   if (ret != NO_ERR)
      return ret;


   // set property list
   // -----------------
   
   // Name
   int nRet = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != nRet)
      return nRet;

   // Description
   nRet = CreateProperty(MM::g_Keyword_Description, "LC DAC driver", MM::String, true);
   if (DEVICE_OK != nRet)
      return nRet;

   // Volts
   // -----
   CPropertyAction* pAct = new CPropertyAction (this, &LCDA::OnVolts);
   nRet = CreateProperty("Volts", "0.0", MM::Float, false, pAct);
   if (nRet != DEVICE_OK)
      return nRet;
   SetPropertyLimits("Volts", minV_, maxV_);

   initialized_ = true;

   return DEVICE_OK;
}

int LCDA::Shutdown()
{
   initialized_ = false;
   return DEVICE_OK;
}


int LCDA::SetSignal(double volts)
{
   volts_ = (float) volts;
   if (gateOpen_) {
      gatedVolts_ = volts_;
      return LaserBoardSetAOV(channel_ - 1, volts_);
   } else {
      gatedVolts_ = 0;
   }

   return DEVICE_OK;
}

int LCDA::SetGateOpen(bool open)
{
   if (open) {
      gateOpen_ = true;
      gatedVolts_ = volts_;
      return LaserBoardSetAOV(channel_ - 1, volts_);
   } 
   // !open
   gateOpen_ = false;
   gatedVolts_ = 0;
   return LaserBoardSetAOV(channel_ - 1, 0.0);
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int LCDA::OnVolts(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      float volts;
      int ret = LaserBoardGetAOV(channel_ - 1, &volts);
      if (ret != DEVICE_OK)
         return ret;
      pProp->Set((double)volts);
   }
   else if (eAct == MM::AfterSet)
   {
      double volts;
      pProp->Get(volts);
      return SetSignal((double) volts);
   }

   return DEVICE_OK;
}


int LCDA::OnChannel(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set((long int)channel_);
   }
   else if (eAct == MM::AfterSet)
   {
      long channel;
      pProp->Get(channel);
      if (channel >=1 && channel <=4)
         channel_ = channel;
   }
   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// LCShutter implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~~

LCShutter::LCShutter() : 
   initialized_(false), 
   nrLines_(4),
   state_(0),
   name_(g_DeviceNameLCShutter),
   sequenceOn_(true),
   sequenceIsRunning_(false),
   maxSequenceSize_(255)
{
   InitializeDefaultErrorMessages();
   
   // custom error messages
   SetErrorText(ERR_BOARD_NOT_FOUND,GS_ERR_BOARD_NOT_FOUND);
   EnableDelay();
}

LCShutter::~LCShutter()
{
   Shutdown();
}

void LCShutter::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

bool LCShutter::Busy()
{
    return false;
}

int LCShutter::Initialize()
{
   if (!LaserBoardIsOpen()) {
      int ret = LaserBoardOpen();
      if (ret != NO_ERR)
         return ret;
   }

   int ret = LaserBoardGetState(&state_);
   if (ret != DEVICE_OK)
      return ret;

   int result;
   ret = LaserBoardGetBlank(&result);
   blank_ = result != 0;
   if (ret != DEVICE_OK)
      return ret;

   char buf[64];
   ret = LaserBoardDriverVersion(buf);
   if (ret != NO_ERR)
      return ret;

   driverVersion_ = buf;

   std::stringstream s;
   s << driverVersion_;
   s >> driverVersionNum_;

   ret = LaserBoardFirmwareVersion(buf);
   if (ret != NO_ERR)
      return ret;
   firmwareVersion_ = buf;


   // set property list
   // -----------------
   
   // Name
   ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "LC shutter driver", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Serial 
   char serial[64];
   ret = LaserBoardGetSerial(serial);
   if (DEVICE_OK != ret)
      return ret;
   ret = CreateProperty("Serial", serial, MM::String, true);

   // Firmware Version
   ret = CreateProperty("Firmware Version", firmwareVersion_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Driver Version
   ret = CreateProperty("Driver Version", driverVersion_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   ret = LaserBoardGetNrLines(&nrLines_);
   if (ret != NO_ERR)
      return ret;

   std::vector<unsigned int> nm;
   std::vector<float> mw;
   for (unsigned int i=0; i < nrLines_; i++) {
      unsigned int nmi, bd;
      float maxV, maxMW;
      LaserBoardGetLineInfo(i, &nmi, &maxV, &maxMW, &bd);
      nm.push_back(nmi);
      mw.push_back(maxMW);
   }
 
   // Power per line in mW
   for (unsigned int i=0; i<nrLines_; i++) {
      std::string propName;
      std::ostringstream os;
      os << nm.at(i) << "nm mW";
      // cheap way of guaranteeing that the propname is unique
      for (unsigned int j=0; j<i; j++)
         os << " ";
      propName = os.str();
      CPropertyActionEx* pActEx = new CPropertyActionEx(this, &LCShutter::OnPower, (long) i);
      ret = CreateProperty(propName.c_str(), "0", MM::Float, false, pActEx, false);
      if (ret != DEVICE_OK)
         return ret;
      SetPropertyLimits(propName.c_str(), 0.0, mw.at(i));
   }

   // State of each line
   for (unsigned int i=0; i<nrLines_; i++) {
      std::ostringstream os;
      os << "LaserLineState " << nm.at(i) << "nm";
      // cheap way of guaranteeing that the propname is unique
      for (unsigned int j=0; j<i; j++)
         os << " ";
      CPropertyActionEx* pActEx = new CPropertyActionEx(this, &LCShutter::OnLineState, (long) i);
      ret = CreateProperty(os.str().c_str(), "0", MM::Integer, false, pActEx, false);
      if (ret != DEVICE_OK)
         return ret;
      SetPropertyLimits(os.str().c_str(), 0, 1);
   }

	// State of all lines together.  
   // This is a binary number in which each bit sets the state of a laser line
   CPropertyAction* pAct = new CPropertyAction(this, &LCShutter::OnState);
   ret = CreateProperty("LaserLineState", "0", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   SetPropertyLimits("LaserLineState", 0, 1 << nrLines_);

   // Blank
   pAct = new CPropertyAction(this, &LCShutter::OnBlank);
   ret = CreateProperty("Blank", "Off", MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   AddAllowedValue("Blank", "Off");
   AddAllowedValue("Blank", "On");

   // External control
   pAct = new CPropertyAction(this, &LCShutter::OnExternalControl);
   ret = CreateProperty("ExternalControl", "Off", MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   AddAllowedValue("ExternalControl", "Off");
   AddAllowedValue("ExternalControl", "On");

   // Sync
   pAct = new CPropertyAction(this, &LCShutter::OnSync);
   ret = CreateProperty("External Trigger", "Off", MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   AddAllowedValue("External Trigger", "Off");
   AddAllowedValue("External Trigger", "On");

   // Sequence switch
   pAct = new CPropertyAction(this, &LCShutter::OnSequence);
   ret = CreateProperty("Sequence", "On", MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   AddAllowedValue("Sequence", "On");
   AddAllowedValue("Sequence", "Off");

   // Functions that are only in newer Driver versions
   if (driverVersionNum_ >= 0.2) 
   {
      // Output (Galvo)
      int position;
      ret = LaserBoardGetGalvo(&position);
      if (ret == NO_ERR)
      {
         pAct = new CPropertyAction(this, &LCShutter::OnOutput);
         ret = CreateProperty("Output", g_fiber_1, MM::String, false, pAct);
         if (ret != DEVICE_OK)
            return ret;
         AddAllowedValue("Output", g_fiber_1);
         AddAllowedValue("Output", g_fiber_2);
      }

      // ND filters
      int on;
      ret = LaserBoardGetNDOnOff(0, &on);
      if (ret == NO_ERR)
      {
         for (unsigned char i = 0; i < nrLines_; i++) 
         {
            CPropertyActionEx* pActEx = new CPropertyActionEx(this, &LCShutter::OnND, i);
            std::stringstream propName;
            propName << "ND " << nm.at(i) << "nm";
            ret = CreateProperty(propName.str().c_str(), "Off", MM::String, false, pActEx);
            if (ret != DEVICE_OK)
               return ret;
            AddAllowedValue(propName.str().c_str(), "On");
            AddAllowedValue(propName.str().c_str(), "Off");
         }
      }
   }

   // set shutter into the off state
   LaserBoardSetState(0);

   initialized_ = true;

   return DEVICE_OK;
}

int LCShutter::Shutdown()
{
   if (initialized_)
   {
      LaserBoardClose();
      initialized_ = false;
   }
   return DEVICE_OK;
}

int LCShutter::SetOpen(bool open)
{
   if (open)
      return LaserBoardSetState(state_);
 
   return LaserBoardSetState(0);
}

int LCShutter::GetOpen(bool& open)
{
   unsigned char state;
   int ret = LaserBoardGetState(&state);
   if (ret != NO_ERR)
      return ret;

   open = false;
   if (state > 0)
      open = true;

   return DEVICE_OK;
}

int LCShutter::Fire(double /*deltaT*/)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}


///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int LCShutter::OnState(MM::PropertyBase *pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) 
   {
      pProp->Set((long)state_);
   }
   else if (eAct == MM::AfterSet)
   {
      long state;
      pProp->Get(state);
      state_ = (unsigned char) state;

      bool open;
      GetOpen(open);
      if (open)
         SetOpen(open);
   }
   else if (eAct == MM::IsSequenceable)
   {
      if (sequenceOn_)
         pProp->SetSequenceable(maxSequenceSize_);
      else
         pProp->SetSequenceable(0);
   }
   else if (eAct == MM::AfterLoadSequence)
   {
      std::vector<std::string> sequence = pProp->GetSequence();
      if (sequence.size() > (unsigned long) maxSequenceSize_)
         return DEVICE_SEQUENCE_TOO_LARGE;

      unsigned char* load = new (std::nothrow) unsigned char[sequence.size()];
      if (load == 0)
      {
         return DEVICE_OUT_OF_MEMORY;
      }
      for (unsigned int i=0; i < sequence.size(); i++) 
      {
         unsigned int seq;
         std::stringstream os;
         os << sequence[i];
         os >> seq;
         load[i] = (unsigned char) seq;
      }
      int ret = LaserBoardSetSequenceState(static_cast<unsigned int>(sequence.size()), load);
      delete(load);
      return ret;
   }
   else if (eAct == MM::StartSequence)
   {
      // TODO: what is we think that a sequence is already running?
      unsigned char mode = 0;  // modes > 0 also run DA outputs
      int ret = LaserBoardStartSequence(mode);
      if (ret != DEVICE_OK)
         return ret;
      sequenceIsRunning_ = true;
   }
   else if (eAct == MM::StopSequence)
   {
      if (!sequenceIsRunning_) 
         return DEVICE_OK;
      unsigned int nr;
      int ret =  LaserBoardStopSequence(&nr);
      if (ret != DEVICE_OK)
         return ret;
      sequenceIsRunning_ = false;
      std::ostringstream os;
      os << "MLC Sequence ran for " << nr << " of states";
      LogMessage(os.str().c_str(), false);
   }


   return DEVICE_OK;
}

int LCShutter::OnBlank(MM::PropertyBase *pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) 
   {
      if (blank_)
         pProp->Set("On");
      else
         pProp->Set("Off");
   }
   else if (eAct == MM::AfterSet)
   {
      std::string blank;
      pProp->Get(blank);
      if (blank == "On")
         blank_ = true;
      else
         blank_ = false;
      return LaserBoardSetBlank(blank_);
   }
   return DEVICE_OK;
}

int LCShutter::OnExternalControl(MM::PropertyBase* pProp, MM::ActionType eAct)
{   
   if (eAct == MM::BeforeGet) 
   {
      int external;
      int ret = LaserBoardGetExternalControl(&external);
      if (ret != DEVICE_OK)
         return ret;
      if (external != 0)
         pProp->Set("On");
      else
         pProp->Set("Off");
   }
   else if (eAct == MM::AfterSet)
   {
      std::string ext;
      bool external = false;
      pProp->Get(ext);
      if (ext == "On")
         external = true;
      else
         external = false;
      return LaserBoardSetExternalControl(external);
   }
   return DEVICE_OK;
}

int LCShutter::OnSync(MM::PropertyBase* pProp, MM::ActionType eAct)
{   
   if (eAct == MM::BeforeGet) 
   {
      int sync;
      int ret = LaserBoardGetSync(&sync);
      if (ret != DEVICE_OK)
         return ret;
      if (sync != 0)
         pProp->Set("On");
      else
         pProp->Set("Off");
   }
   else if (eAct == MM::AfterSet)
   {
      std::string trigger;
      bool sync = false;
      pProp->Get(trigger);
      if (trigger == "On")
         sync = true;
      else
         sync = false;
      return LaserBoardSetSync(sync);
   }
   return DEVICE_OK;
}

int LCShutter::OnLineState(MM::PropertyBase* pProp, MM::ActionType eAct, long laserLine)
{
   if (eAct == MM::BeforeGet)
   {
      long state = state_ & (1 << laserLine); 
      state = state >> laserLine;

      pProp->Set(state);
   } 
   else if (eAct == MM::AfterSet)
   {
      long state;
      pProp->Get(state);
	   if (state == 1)
         state_ |= (1 << laserLine);
      if (state == 0)
        state_ &= ~(1 << laserLine);

      bool open;
      GetOpen(open);
      if (open)
         SetOpen(open);
   }
   

   return DEVICE_OK;
}


int LCShutter::OnPower(MM::PropertyBase* pProp, MM::ActionType eAct, long laserLine)
{
   if (eAct == MM::BeforeGet)
   {
      float mW;
      int ret = LaserBoardGetPower((unsigned int) laserLine, &mW);
      if (ret != NO_ERR)
         return ret;
      pProp->Set((double) mW);
   } 
   else if (eAct == MM::AfterSet)
   {
      double mW;
      pProp->Get(mW);
      return LaserBoardSetPower( (unsigned int) laserLine, (float) mW);
   }

   return DEVICE_OK;
}

int LCShutter::OnSequence(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      if (sequenceOn_)
         pProp->Set("On");
      else
         pProp->Set("Off");
   }
   else if (eAct == MM::AfterSet)
   {
      std::string state;
      pProp->Get(state);
      if (state == "On")
         sequenceOn_ = true;
      else
         sequenceOn_ = false;
   }
   return DEVICE_OK;
}

int LCShutter::OnND(MM::PropertyBase* pProp, MM::ActionType eAct, long laserLine)
{
   if (eAct == MM::BeforeGet)
   {  
      int open;
      int ret = LaserBoardGetNDOnOff((unsigned int) laserLine, &open);
      if (ret != NO_ERR)
         return ret;
      if (open == 0)
         pProp->Set("Off");
      else
         pProp->Set("On");
   } 
   else if (eAct == MM::AfterSet)
   {
      std::string state;
      pProp->Get(state);
      int open = 0;
      if (state == "On")
         open = 1;
      return LaserBoardSetNDOnOff( (unsigned int) laserLine, open);
   }
   return DEVICE_OK;
}


int LCShutter::OnOutput(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      int position;
      std::string fiber = g_fiber_1;
      int ret = LaserBoardGetGalvo(&position);
      if (ret != DEVICE_OK)
         return ret;
      if (position == 1)
         fiber = g_fiber_2;
      pProp->Set(fiber.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      std::string fiber;
      pProp->Get(fiber);
      int position = 0;
      if (fiber == g_fiber_1)
         position = 0;
      else
         position = 1;
      return LaserBoardSetGalvo(position);
   }
   return DEVICE_OK;
}

/********
 * Device Safety Shutter
 * It's sole function is to control the safety shutter present in some 
 * models of the LaserCombiner
 */


LCSafetyShutter::LCSafetyShutter() :
   changedTime_(0),
   initialized_(false),
   state_(0),
   name_(g_DeviceNameLCShutter)
{
   InitializeDefaultErrorMessages();
   
   // custom error messages
   SetErrorText(ERR_BOARD_NOT_FOUND,GS_ERR_BOARD_NOT_FOUND);
   SetErrorText(ERR_NOT_IN_THIS_FIRMWARE, GS_NOT_IN_THIS_FIRMWARE);
   EnableDelay();
}
   
LCSafetyShutter::~LCSafetyShutter()
{
   Shutdown();
}
  

int LCSafetyShutter::Initialize()
{
   int ret = DEVICE_OK;

   if (!LaserBoardIsOpen()) {
      ret = LaserBoardOpen();
      if (ret != NO_ERR)
         return ret;
   }

   char buf[64];
   ret = LaserBoardDriverVersion(buf);
   if (ret != NO_ERR)
      return ret;

   std::string driverVersion = buf;
   float driverVersionNum = 0.0;
   std::stringstream s;
   s << driverVersion;
   s >> driverVersionNum;

   if (driverVersionNum < 0.2)
      return ERR_NOT_IN_THIS_FIRMWARE;

   // Name
   ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "LC Safety Shutter", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   CPropertyAction* pAct = new CPropertyAction(this, &LCSafetyShutter::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
   if (DEVICE_OK != ret)
      return ret;
   AddAllowedValue(MM::g_Keyword_State, "0");  
   AddAllowedValue(MM::g_Keyword_State, "1");

   return DEVICE_OK;
}

int LCSafetyShutter::Shutdown()
{
   if (initialized_)
   {
      if (LaserBoardIsOpen())
         LaserBoardClose();
      initialized_ = false;
   }
   return DEVICE_OK;
}
  
void LCSafetyShutter::GetName(char* pszName) const
{
   CDeviceUtils::CopyLimitedString(pszName, name_.c_str());
}

bool LCSafetyShutter::Busy()
{
   // TODO: check timeouts
   return false;
}
   
  
int LCSafetyShutter::SetOpen(bool open)
{
   return LaserBoardSetShutter(open);
}

int LCSafetyShutter::GetOpen(bool& open)
{
   int state;
   int ret = LaserBoardGetShutter(&state);
   if (ret != DEVICE_OK)
      return ret;
   if (state == 0)
      open = false;
   else
      open = true;

   return DEVICE_OK;
}

int LCSafetyShutter::Fire(double /*deltaT*/)
{
   return DEVICE_OK;
}


int LCSafetyShutter::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      bool open;
      int ret = GetOpen(open);
      if (ret != DEVICE_OK)
         return ret;
      std::string state = "0";
      if (open)
         state = "1";
      pProp->Set(state.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      std::string state;
      pProp->Get(state);
      bool open = false;
      if (state == "1")
         open = true;
      return SetOpen(open);
   }

   return DEVICE_OK;
}



