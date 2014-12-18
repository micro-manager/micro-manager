///////////////////////////////////////////////////////////////////////////////
// FILE:          MCCDAQ.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Measurement Computing DAQ Adapter
// COPYRIGHT:     University of California, Berkeley, 2013
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
// AUTHOR:        Matthew Bakalar, matthew.bakalar@gmail.com, 08/22/2013
//
//

#include "MCCDAQ.h"
#include "../../MMDevice/ModuleInterface.h"
#include "cbw.h"
#include <sstream>


const int BOARDREADY = 100;

/* MCCDaq board handle */

typedef struct tag_board {
   int initialized;		/* board has been initialized == BOARDREADY */
   int board_num;         /* board number            */
   int analog_range;	/* voltage range for analog output */
   int status;       /* board error status       */
} MCCBoard;

static MCCBoard board;

const int NUM_CHANNELS = 8;

inline std::string DeviceNameMCCShutter(int channel)
{
   std::ostringstream chanStrm;
   chanStrm << channel;
   return "MCC-Shutter-" + chanStrm.str();
}

inline std::string DeviceNameMCCDA(int channel)
{
   std::ostringstream chanStrm;
   chanStrm << channel;
   return "MCC-DAC-" + chanStrm.str();
}

const char* g_volts = "Volts";
const char* g_PropertyMin = "MinV";
const char* g_PropertyMax = "MaxV";

// Global state of the shutter to enable simulation of the shutter device.
// The virtual shutter device uses this global variable to restore state of the switch
unsigned g_shutterState = 0;

using namespace std;


/*
 * Initilize the global board handle
 * voltMode should be e.g. UNI5VOLTS, UNI10VOLTS, etc.
 */
int InitializeTheBoard(int voltMode)
{
   if (board.initialized == BOARDREADY)
      return DEVICE_OK;

   // initialize the board. Use board number 0
   // #TODO Load board number, voltage range as a parameter
   board.board_num = 0;
   board.analog_range = voltMode;

   board.initialized = BOARDREADY;

   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   for (int i = 0; i < NUM_CHANNELS; i++)
   {
      std::ostringstream chanStrm;
      chanStrm << i;

      RegisterDevice(DeviceNameMCCShutter(i).c_str(), MM::ShutterDevice,
            ("MCC DAQ Shutter " + chanStrm.str()).c_str());
      RegisterDevice(DeviceNameMCCDA(i).c_str(), MM::SignalIODevice,
            ("MCC DAQ DA " + chanStrm.str()).c_str());
   }
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   for (int i = 0; i < NUM_CHANNELS; i++)
   {
      if (deviceName == DeviceNameMCCShutter(i))
         return new MCCDaqShutter(i, deviceName);
      if (deviceName == DeviceNameMCCDA(i))
         return new MCCDaqDA(i, deviceName);
   }

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}


///////////////////////////////////////////////////////////////////////////////
// MCCDaqDA implementation
// ~~~~~~~~~~~~~~~~~~~~~~

MCCDaqDA::MCCDaqDA(unsigned channel, const char* name) :
      busy_(false), minV_(0.0), maxV_(0.0), volts_(0.0), gatedVolts_(0.0), encoding_(0), resolution_(0), channel_(channel), name_(name), gateOpen_(true)
{
   InitializeDefaultErrorMessages();

   // add custom error messages
   SetErrorText(ERR_UNKNOWN_POSITION, "Invalid position (state) specified");
   SetErrorText(ERR_INITIALIZE_FAILED, "Initialization of the device failed");
   SetErrorText(ERR_WRITE_FAILED, "Failed to write data to the device");
   SetErrorText(ERR_CLOSE_FAILED, "Failed closing the device");

   SetErrorText(BADBOARD, "MCC bad board error");
   SetErrorText(BADPORTNUM, "MCC bad port num error");
   SetErrorText(BADADCHAN, "Bad AD channel error");

   CreateProperty(g_PropertyMin, "0.0", MM::Float, false, 0, true);
   CreateProperty(g_PropertyMax, "5.0", MM::Float, false, 0, true);
      
}

MCCDaqDA::~MCCDaqDA()
{
   Shutdown();
}

void MCCDaqDA::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}


int MCCDaqDA::Initialize()
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
   nRet = CreateProperty(MM::g_Keyword_Description, "MCC DAC driver", MM::String, true);
   if (DEVICE_OK != nRet)
      return nRet;

   // Voltage
   // -----
   CPropertyAction* pAct = new CPropertyAction (this, &MCCDaqDA::OnVolts);
   nRet = CreateProperty(g_volts, "0.0", MM::Float, false, pAct);
   if (nRet != DEVICE_OK)
      return nRet;

   nRet = GetProperty(g_PropertyMin, minV_);
   assert (nRet == DEVICE_OK);

   nRet = GetProperty(g_PropertyMax, maxV_);
   assert (nRet == DEVICE_OK);

   nRet = SetPropertyLimits(g_volts, minV_, maxV_);
   if (nRet != DEVICE_OK)
      return nRet;

   // There are a ton of supported voltage modes, and we can't use a switch
   // statement because of the floating point nature. This approach involves
   // a lot of redundant assignments but is straightforward.
   int voltMode = UNIPT01VOLTS;
   if (maxV_ > .01) {
      voltMode = UNIPT02VOLTS;
   }
   if (maxV_ > .02) {
      voltMode = UNIPT05VOLTS;
   }
   if (maxV_ > .05) {
      voltMode = UNIPT1VOLTS;
   }
   if (maxV_ > .1) {
      voltMode = UNIPT2VOLTS;
   }
   if (maxV_ > .2) {
      voltMode = UNIPT25VOLTS;
   }
   if (maxV_ > .25) {
      voltMode = UNIPT5VOLTS;
   }
   if (maxV_ > .5) {
      voltMode = UNI1VOLTS;
   }
   if (maxV_ > 1) {
      voltMode = UNI1PT25VOLTS;
   }
   if (maxV_ > 1.25) {
      voltMode = UNI1PT67VOLTS;
   }
   if (maxV_ > 1.67) {
      voltMode = UNI2VOLTS;
   }
   if (maxV_ > 2) {
      voltMode = UNI2PT5VOLTS;
   }
   if (maxV_ > 2.5) {
      voltMode = UNI4VOLTS;
   }
   if (maxV_ > 4) {
      voltMode = UNI5VOLTS;
   }
   if (maxV_ > 5) {
      voltMode = UNI10VOLTS;
   }

   int ret = InitializeTheBoard(voltMode);
   if (ret != DEVICE_OK)
      return ret;

   nRet = UpdateStatus();
   if (nRet != DEVICE_OK)
      return nRet;

   initialized_ = true;

   return DEVICE_OK;
}

int MCCDaqDA::Shutdown()
{
   initialized_ = false;
   return DEVICE_OK;
}

int MCCDaqDA::SetGateOpen(bool open)
{
   gateOpen_ = open;
   SetVolts(volts_);
   return DEVICE_OK;
}

int MCCDaqDA::SetSignal(double volts)
{
   return SetProperty(g_volts, CDeviceUtils::ConvertToString(volts));
}

int MCCDaqDA::WriteToPort(unsigned short value)
{
   int ret = cbAOut(board.board_num, channel_, board.analog_range, value);
   if (ret != NOERRORS)
      return ret;

   return DEVICE_OK;
}

int MCCDaqDA::SetVolts(double volts)
{
   if (volts > maxV_ || volts < minV_)
      return DEVICE_RANGE_EXCEEDED;

   volts_ = volts;

   unsigned short value = (unsigned short) ( (volts/maxV_) * 65535 );
   if (!gateOpen_)
      value = 0;

   return WriteToPort(value);
}

int MCCDaqDA::GetLimits(double& minVolts, double& maxVolts)
{
   int nRet = GetProperty(g_PropertyMin, minVolts);
   if (nRet == DEVICE_OK)
      return nRet;

   nRet = GetProperty(g_PropertyMax, maxVolts);
   if (nRet == DEVICE_OK)
      return nRet;

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int MCCDaqDA::OnVolts(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // nothing to do, let the caller to use cached property
   }
   else if (eAct == MM::AfterSet)
   {
      double volts;
      pProp->Get(volts);
      return SetVolts(volts);
   }

   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// MCCDaq implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~~

MCCDaqShutter::MCCDaqShutter(unsigned channel, const char* name) : initialized_(false), channel_(channel), name_(name), openTimeUs_(0), ports_()
{
   InitializeDefaultErrorMessages();
   EnableDelay();

   SetErrorText(BADBOARD, "MCC bad board error");
   SetErrorText(BADPORTNUM, "MCC bad port num error");
   SetErrorText(BADADCHAN, "Bad AD channel error");
}

MCCDaqShutter::~MCCDaqShutter()
{
   Shutdown();
}

void MCCDaqShutter::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

bool MCCDaqShutter::Busy()
{
   long interval = GetClockTicksUs() - openTimeUs_;

   if (interval/1000.0 < GetDelayMs() && interval > 0)
   {
      return true;
   }
   else
   {
       return false;
   }
}

int MCCDaqShutter::Initialize()
{
   int ret = InitializeTheBoard(UNI5VOLTS);
   if (ret != DEVICE_OK)
      return ret;

   // Define ports for this shutter
   // #TODO Load port from parameter
   // ports_.push_back(FIRSTPORTA);
   ports_.push_back(FIRSTPORTB);

   // initialize the digital ports as output ports
   // #TODO Only initialize the needed ports
   for(unsigned int i=0; i < ports_.size(); i++)
   {
	   int ret = cbDConfigPort(board.board_num, ports_[i], DIGITALOUT);
	   if (ret != NOERRORS)
	   	   return ret;
   }

   // set property list
   // -----------------
   
   // Name
   ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "MCC shutter driver", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // OnOff
   // ------
   CPropertyAction* pAct = new CPropertyAction (this, &MCCDaqShutter::OnOnOff);
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

   initialized_ = true;
   openTimeUs_ = GetClockTicksUs();

   return DEVICE_OK;
}

int MCCDaqShutter::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

int MCCDaqShutter::SetOpen(bool open)
{
   if (open)
      return SetProperty("OnOff", "1");
   else
      return SetProperty("OnOff", "0");
}

int MCCDaqShutter::GetOpen(bool& open)
{
   char buf[MM::MaxStrLength];
   int ret = GetProperty("OnOff", buf);
   if (ret != DEVICE_OK)
      return ret;
   long pos = atol(buf);
   pos > 0 ? open = true : open = false;

   return DEVICE_OK;
}

int MCCDaqShutter::Fire(double /*deltaT*/)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

int MCCDaqShutter::WriteToPort(unsigned short value)
{
   for(unsigned int i=0; i < ports_.size(); i++)
   {
	   int ret = cbDOut(board.board_num, ports_[i], value);
	   if (ret != NOERRORS)
	       return ret;
   }
   
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int MCCDaqShutter::OnOnOff(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // use cached state
   }
   else if (eAct == MM::AfterSet)
   {
      long pos;
      pProp->Get(pos);
      int ret;
      if (pos == 0)
         ret = WriteToPort(0); // turn everything off
      else
         // ret = WriteToPort(g_switchState); // restore old setting
		 ret = WriteToPort(1 << channel_);
      if (ret != DEVICE_OK)
         return ret;
      g_shutterState = pos;
      openTimeUs_ = GetClockTicksUs();
   }

   return DEVICE_OK;
}
