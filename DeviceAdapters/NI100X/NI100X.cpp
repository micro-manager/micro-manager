///////////////////////////////////////////////////////////////////////////////
// FILE:          NI100X.cpp
// PROJECT:       100X micro-manager extensions
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   NI control with multiple devices
//                
// AUTHOR:        Nenad Amodaj, January 2010
//
// COPYRIGHT:     100X Imaging Inc, 2010
//                

#ifdef WIN32
   #define WIN32_LEAN_AND_MEAN
   #include <windows.h>
   #define snprintf _snprintf 
#endif

#include <sstream>
#include "NI100X.h"
#include "ModuleInterface.h"

const char* g_DeviceNameDigitalIO = "DigitalIO";
const char* g_DeviceNameShutter = "Shutter";
const char* g_DeviceNameAnalogIO = "AnalogIO";

const char* g_PropertyVolts = "Volts";
const char* g_PropertyMinVolts = "MinVolts";
const char* g_PropertyMaxVolts = "MaxVolts";
const char* g_PropertyChannel = "IOChannel";
//used for disabling EOMs temporariliy for laser switching
const char* g_PropertyDisable = "Block voltage";

const char* g_PropertyDemo = "Demo";

const char* g_Yes = "Yes";
const char* g_No = "No";

// Global state of the digital IO to enable simulation of the shutter device.
// The virtual shutter device uses this global variable to restore state of the switch
unsigned g_digitalState = 0;
unsigned g_shutterState = 0;

using namespace std;


///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_DeviceNameDigitalIO, MM::StateDevice, "NI digital IO");
   RegisterDevice(g_DeviceNameAnalogIO, MM::SignalIODevice, "NI analog IO");
   //RegisterDevice(g_DeviceNameShutter, MM::ShutterDevice, "");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_DeviceNameDigitalIO) == 0)
   {
      return new DigitalIO;
   }
   else if (strcmp(deviceName, g_DeviceNameAnalogIO) == 0)
   {
      return new AnalogIO;
   }
   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
// DigitalIO implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~

DigitalIO::DigitalIO() : numPos_(16), busy_(false), channel_("undef"), task_(0), open_(false), state_(0)
{
   InitializeDefaultErrorMessages();

   // add custom error messages
   SetErrorText(ERR_UNKNOWN_POSITION, "Invalid position (state) specified");
   SetErrorText(ERR_INITIALIZE_FAILED, "Initialization of the device failed");
   SetErrorText(ERR_WRITE_FAILED, "Failed to write data to the device");
   SetErrorText(ERR_CLOSE_FAILED, "Failed closing the device");

   CPropertyAction* pAct = new CPropertyAction (this, &DigitalIO::OnChannel);
   CreateProperty(g_PropertyChannel, "devname", MM::String, false, pAct, true);
   assert(nRet == DEVICE_OK);

}

DigitalIO::~DigitalIO()
{
   Shutdown();
}

void DigitalIO::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_DeviceNameDigitalIO);
}


int DigitalIO::Initialize()
{
   // set property list
   // -----------------
   
   // Name
   int nRet = CreateProperty(MM::g_Keyword_Name, g_DeviceNameDigitalIO, MM::String, true);
   if (DEVICE_OK != nRet)
      return nRet;

   // Description
   nRet = CreateProperty(MM::g_Keyword_Description, "Digital IO adapter", MM::String, true);
   if (DEVICE_OK != nRet)
      return nRet;

   // create positions and labels
   const int bufSize = 1024;
   char buf[bufSize];
   for (long i=0; i<numPos_; i++)
   {
      snprintf(buf, bufSize, "%d", (unsigned)i);
      SetPositionLabel(i, buf);
   }

   // State
   // -----
   CPropertyAction* pAct = new CPropertyAction (this, &DigitalIO::OnState);
   nRet = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
   if (nRet != DEVICE_OK)
      return nRet;

   // Gate Closed Position
   nRet = CreateProperty(MM::g_Keyword_Closed_Position, "0", MM::Integer, false);
   if (nRet != DEVICE_OK)
      return nRet;

   // set up task
   // -----------
   long niRet = DAQmxCreateTask("", &task_);
   if (niRet != DAQmxSuccess)
      return (int)niRet;

   niRet = DAQmxCreateDOChan(task_, channel_.c_str(), "", DAQmx_Val_ChanForAllLines);
   if (niRet != DAQmxSuccess)
      return (int)niRet;

   niRet = DAQmxStartTask(task_);

   GetGateOpen(open_);

   nRet = UpdateStatus();
   if (nRet != DEVICE_OK)
      return nRet;

   initialized_ = true;

   return DEVICE_OK;
}

int DigitalIO::Shutdown()
{
   DAQmxStopTask(task_);
	DAQmxClearTask(task_);
   
   initialized_ = false;
   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int DigitalIO::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set((long)state_);
   }
   else if (eAct == MM::AfterSet)
   {
      bool gateOpen;
      GetGateOpen(gateOpen);

      long state;
      pProp->Get(state);

      if ((state == state_) && (open_ == gateOpen))
         return DEVICE_OK;

      if (gateOpen) {
         uInt32 data;
         int32 written;
         data = state;
         long niRet = DAQmxWriteDigitalU32(task_, 1, 1, 10.0, DAQmx_Val_GroupByChannel, &data, &written, NULL);
         if (niRet != DAQmxSuccess)
            return (int)niRet;
      }
      else {
         long closed_state;
         GetProperty(MM::g_Keyword_Closed_Position, closed_state);

         uInt32 data;
         int32 written;
         data = closed_state;
         long niRet = DAQmxWriteDigitalU32(task_, 1, 1, 10.0, DAQmx_Val_GroupByChannel, &data, &written, NULL);
         if (niRet != DAQmxSuccess)
            return (int)niRet;
      }

      state_ = (int)state;
      open_ = gateOpen;
      pProp->Set((long)state_);
   }

   return DEVICE_OK;
}

int DigitalIO::OnChannel(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(channel_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(channel_);
   }

   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// AnalogIO implementation
// ~~~~~~~~~~~~~~~~~~~~~~~

AnalogIO::AnalogIO() :
      busy_(false), disable_(false), minV_(0.0), maxV_(5.0), volts_(0.0), gatedVolts_(0.0), encoding_(0),
      resolution_(0), channel_("undef"), gateOpen_(true), task_(0), demo_(false)
{
   InitializeDefaultErrorMessages();

   // add custom error messages
   SetErrorText(ERR_UNKNOWN_POSITION, "Invalid position (state) specified");
   SetErrorText(ERR_INITIALIZE_FAILED, "Initialization of the device failed");
   SetErrorText(ERR_WRITE_FAILED, "Failed to write data to the device");
   SetErrorText(ERR_CLOSE_FAILED, "Failed closing the device");

   // channel
   CPropertyAction* pAct = new CPropertyAction (this, &AnalogIO::OnChannel);
   int nRet = CreateProperty(g_PropertyChannel, "devname", MM::String, false, pAct, true);
   assert(nRet == DEVICE_OK);

   // demo
   pAct = new CPropertyAction (this, &AnalogIO::OnDemo);
   nRet = CreateProperty(g_PropertyDemo, g_No, MM::String, false, pAct, true);
   assert(nRet == DEVICE_OK);
   AddAllowedValue(g_PropertyDemo, g_No);
   AddAllowedValue(g_PropertyDemo, g_Yes);

   // MinVolts
   // --------
   pAct = new CPropertyAction (this, &AnalogIO::OnMinVolts);
   nRet = CreateProperty(g_PropertyMinVolts, "0.0", MM::Float, false, pAct, true);
   assert(nRet == DEVICE_OK);

   // MaxVolts
   // --------
   pAct = new CPropertyAction (this, &AnalogIO::OnMaxVolts);
   nRet = CreateProperty(g_PropertyMaxVolts, "5.0", MM::Float, false, pAct, true);
   assert(nRet == DEVICE_OK);
}

AnalogIO::~AnalogIO()
{
   Shutdown();
}

void AnalogIO::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_DeviceNameAnalogIO);
}


int AnalogIO::Initialize()
{
   // set property list
   // -----------------
   char label[MM::MaxStrLength];
   GetLabel(label);
   
   // Name
   int nRet = CreateProperty(MM::g_Keyword_Name, g_DeviceNameAnalogIO, MM::String, true);
   if (DEVICE_OK != nRet)
      return nRet;

   // Description
   nRet = CreateProperty(MM::g_Keyword_Description, "NI DAC", MM::String, true);
   if (DEVICE_OK != nRet)
      return nRet;

   // Volts
   // -----
   CPropertyAction* pAct = new CPropertyAction (this, &AnalogIO::OnVolts);
   nRet = CreateProperty(g_PropertyVolts, "0.0", MM::Float, false, pAct);
   if (nRet != DEVICE_OK)
      return nRet;

   nRet = SetPropertyLimits(g_PropertyVolts, minV_, maxV_);
   assert(nRet == DEVICE_OK);


    //Disable
    pAct = new CPropertyAction (this, &AnalogIO::OnDisable);
	nRet = CreateProperty(g_PropertyDisable, "false", MM::String, false, pAct);
	AddAllowedValue(g_PropertyDisable, "false");
    AddAllowedValue(g_PropertyDisable, "true");
    assert(nRet == DEVICE_OK);


   // set up task
   // -----------

   if (!demo_)
   {
      long niRet = DAQmxCreateTask("", &task_);
      if (niRet != DAQmxSuccess)
         return (int)niRet;

      niRet = DAQmxCreateAOVoltageChan(task_, channel_.c_str(), "", minV_, maxV_, DAQmx_Val_Volts, "");
      if (niRet != DAQmxSuccess)
         return (int)niRet;

	   niRet = DAQmxStartTask(task_);
   }

   nRet = UpdateStatus();
   if (nRet != DEVICE_OK)
      return nRet;

   initialized_ = true;

   return DEVICE_OK;
}

int AnalogIO::Shutdown()
{
   if (!demo_)
   {
      DAQmxStopTask(task_);
	   DAQmxClearTask(task_);
   }

   initialized_ = false;
   return DEVICE_OK;
}

int AnalogIO::SetSignal(double volts)
{
   ostringstream txt;
   txt << "2P >>>> AnalogIO::SetVoltage() = " << volts; 
   LogMessage(txt.str());
   return SetProperty(g_PropertyVolts, CDeviceUtils::ConvertToString(volts));
}

int AnalogIO::SetGateOpen(bool open)
{
   int ret(DEVICE_OK);

   if (open)
   {
      // opening gate: restore voltage
      if (!gateOpen_)
         ret = ApplyVoltage(gatedVolts_);
   }
   else
   {
      // closing gate: set voltage to 0
      if (gateOpen_)
      {
         ret = ApplyVoltage(0.0);
      }
   }

   if (ret != DEVICE_OK)
      return ret;

   gateOpen_ = open;
   return DEVICE_OK;
}

int AnalogIO::GetGateOpen(bool& open)
{
   open = gateOpen_;
   return DEVICE_OK;
}

int AnalogIO::ApplyVoltage(double v)
{
   if (!demo_)
   {
      float64 data[1];
      data[0] = v;
	  if (disable_)	
	  {
		data[0] = 0;
	  }
      long niRet = DAQmxWriteAnalogF64(task_, 1, 1, 10.0, DAQmx_Val_GroupByChannel, data, NULL, NULL);
      if (niRet != DAQmxSuccess)
      {
         ostringstream os;
         os << "Error setting voltage on the NI card: v=" << v << " V, error=" << niRet;
         LogMessage(os.str());
         return (int)niRet;
      }
   }
   volts_ = v;
   return DEVICE_OK;
}





///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int AnalogIO::OnVolts(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(gatedVolts_);
   }
   else if (eAct == MM::AfterSet)
   {
      double v;
      pProp->Get(v);
      gatedVolts_ = v;

      if (gateOpen_)
         return ApplyVoltage(v);
   }

   return DEVICE_OK;
}

int AnalogIO::OnMinVolts(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(minV_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(minV_);
   }

   return DEVICE_OK;
}

int AnalogIO::OnMaxVolts(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(maxV_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(maxV_);
   }

   return DEVICE_OK;
}

int AnalogIO::OnDisable(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		pProp->Set( (disable_ ? "true" : "false"));
	}
	else if (eAct == MM::AfterSet)
	{
		string temp;
		pProp->Get(temp);
		disable_ = (temp == "true");
		ApplyVoltage(gatedVolts_);
	}

	return DEVICE_OK;
}

int AnalogIO::OnChannel(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(channel_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(channel_);
   }

   return DEVICE_OK;
}

int AnalogIO::OnDemo(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(demo_ ? g_Yes : g_No);
   }
   else if (eAct == MM::AfterSet)
   {
      string val;
      pProp->Get(val);
      if (val.compare(g_Yes) == 0)
         demo_ = true;
      else
         demo_ = false;
   }

   return DEVICE_OK;
}
