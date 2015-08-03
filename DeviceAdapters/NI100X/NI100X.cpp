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

#include <boost/lexical_cast.hpp>
#include <boost/algorithm/string/replace.hpp>
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
const char* g_PropertyCanTrigger = "SupportsTriggering";
const char* g_PropertyTriggeringEnabled = "Sequenceable";
const char* g_PropertySequenceLength = "TriggerSequenceLength";
const char* g_PropertyTriggerInput = "TriggerInputLine";
//used for disabling EOMs temporariliy for laser switching
const char* g_PropertyDisable = "Block voltage";

const char* g_PropertyDemo = "Demo";

const char* g_Yes = "Yes";
const char* g_No = "No";

// Global state of the digital IO to enable simulation of the shutter device.
// The virtual shutter device uses this global variable to restore state of the switch
unsigned int g_digitalState = 0;
unsigned int g_shutterState = 0;

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
// DAQDevice functions
///////////////////////////////////////////////////////////////////////////////

DAQDevice::DAQDevice(): task_(NULL), channel_("undef"), isTriggeringEnabled_(true),
    supportsTriggering_(true), maxSequenceLength_(1000)
{
}

// Provide DAQDevice with access to the Core and Device objects.
void DAQDevice::SetContext(MM::Core* core, MM::Device* device)
{
   core_ = core;
   device_ = device;
}

// Provide a list of available devices
std::vector<std::string> DAQDevice::GetDevices()
{
   std::vector<std::string> result;
   char devNames[2048];
   DAQmxGetSysDevNames(devNames, 2048);
   // Names are comma-separated.
   std::string allNames = devNames;
   size_t index = allNames.find(", ");
   if (index == std::string::npos)
   {
       // Zero or one devices.
       if (allNames.size() != 0)
       {
         result.push_back(allNames);
      }
   }
   else
   {
      result.push_back(allNames.substr(0, index));
      do {
         result.push_back(GetNextEntry(allNames, index));
      } while (index != std::string::npos);
   }
   return result;
}

// Provide a list of all digital ports on the device.
std::vector<std::string> DAQDevice::GetDigitalPortsForDevice(std::string device)
{
   char ports[2048];
   // Provides a comma-separated list of individual lines,
   // e.g. "Dev1/port0/line0, Dev1/port0/line1"
   DAQmxGetDevDOLines(device.c_str(), ports, 2048);
   std::string allPorts = ports;
   size_t index = std::string::npos;
   std::vector<std::string> result;
   do
   {
      result.push_back(GetPort(GetNextEntry(allPorts, index)));
   } while (index != std::string::npos);
   return result;
}

// Provide a list of all analog ports on the device.
std::vector<std::string> DAQDevice::GetAnalogPortsForDevice(std::string device)
{
   char ports[2048];
   // Provides a comma-separated list of analog channels,
   // e.g. "Dev1/ao0, Dev1/ao1"
   DAQmxGetDevAOPhysicalChans(device.c_str(), ports, 2048);
   std::string allPorts = ports;
   size_t index = std::string::npos;
   std::vector<std::string> result;
   do
   {
      result.push_back(GetNextEntry(allPorts, index));
   } while (index != std::string::npos);
   return result;
}

// line is a fully-qualified specific pin; we want to get the
// port the pin is part of. E.g. turn "Dev1/port0/line0" into
// "Dev1/port0";
std::string DAQDevice::GetPort(std::string line)
{
   size_t firstIndex = line.find("/");
   if (firstIndex == std::string::npos)
   {
      // This should never happen. Note LogMessage is
      // not available at this time (still in the constructor)
      printf("Attempted to add invalid line with no slashes in its name: %s\n", line);
      return "";
   }
   size_t secondIndex = line.find("/", firstIndex + 1);
   if (secondIndex == std::string::npos)
   {
      // This probably should never happen either.
      printf("Attempted to add invalid line with only one slash in its name: %s\n", line);
      return "";
   }
   std::string channel = line.substr(0, secondIndex);
   return channel;
}

// Derive the device name from the channel.
void DAQDevice::SetDeviceName()
{
   // Derive device name from channel; it's the bit before the "/".
   size_t slashIndex = channel_.find("/");
   deviceName_ = channel_.substr(0, slashIndex);
}

int DAQDevice::OnTriggeringEnabled(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(isTriggeringEnabled_ ? "1" : "0");
   }
   else if (eAct == MM::AfterSet)
   {
      long tmp;
      pProp->Get(tmp);
      isTriggeringEnabled_ = tmp != 0;
   }
   return DEVICE_OK;
}

int DAQDevice::OnInputTrigger(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(inputTrigger_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      // Update the trigger line, and then check if we can do
      // hardware triggering with our new setup.
      pProp->Get(inputTrigger_);
      int error = TestTriggering();
      supportsTriggering_ = (error != 0);
      device_->SetProperty(g_PropertyCanTrigger,
         boost::lexical_cast<string>(supportsTriggering_).c_str());
   }
   return DEVICE_OK;
}

int DAQDevice::OnSequenceLength(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(boost::lexical_cast<std::string>(maxSequenceLength_).c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(maxSequenceLength_);
   }

   return DEVICE_OK;
}

int DAQDevice::SetupClockInput(int numVals)
{
   // Samples per sec is the "maximum expected rate of the clock",
   // where clock is the input trigger signal
   int error = DAQmxCfgSampClkTiming(task_, inputTrigger_.c_str(),
      SAMPLES_PER_SEC,
      DAQmx_Val_Rising, DAQmx_Val_ContSamps, numVals);
   if (error)
   {
       return LogError(error, "CfgSampClkTiming");
   }
   return DEVICE_OK;
}


// Cancel a running task.
void DAQDevice::CancelTask()
{
    if (core_ != 0) {
        core_->LogMessage(device_, ("Cancelling task for " + channel_).c_str(), false);
    }
   // Note we explicitly do not check for or log errors here
   // as we may try to redundantly stop tasks and/or try to
   // stop tasks that do not exist yet.
   DAQmxStopTask(task_);
   DAQmxClearTask(task_);
}

// Set up a new task, cancelling the current one
int DAQDevice::SetupTask() 
{
    if (core_ != 0) {
        core_->LogMessage(device_, ("Setting up task for " + channel_).c_str(), false);
    }
   CancelTask();
   task_ = 0;
   int niRet = DAQmxCreateTask(
       boost::replace_all_copy(channel_, "/", "_").c_str(), &task_);
   if (niRet)
   {
       return LogError(niRet, "CreateTask");
   }
   return DEVICE_OK;
}

// Log a message pertaining to the associated error.
// Return the passed-in error code.
int DAQDevice::LogError(int error, const char* func)
{
    std::string funcStr = boost::lexical_cast<string>(func);
    // Note this call is only relevant for the most recent error.
    int bufSize = DAQmxGetExtendedErrorInfo(NULL, 0);
    char* message = new char[bufSize];
    int messageError = DAQmxGetExtendedErrorInfo(message, bufSize);
    if (messageError)
   {
        // Recurse
        LogError(messageError, ("Unable to log error for " + funcStr).c_str());
    }
    else
   {
      printf("Error for func %s: %s\n", funcStr, message);
        // Log the message.
        core_->LogMessage(device_, ("Error calling " + funcStr + ": " + boost::lexical_cast<string>(message)).c_str(), false);
    }
    return error;
}

// Helper function: extract the next substring from a comma-separated
// list of values, and update startIndex as we go. Should be started
// with an index of std::string::npos if you want to start from the
// beginning of the string, and will end when the index is
// std::string::npos again.
std::string DAQDevice::GetNextEntry(std::string line, size_t& startIndex)
{
   size_t nextIndex = line.find(", ", startIndex + 1);
   std::string result;
   if (startIndex == std::string::npos &&
      nextIndex == std::string::npos)
   {
      // No commas found in the entire string.
      result = line;
   }
   else
   {
      size_t tmp = startIndex;
      if (startIndex == std::string::npos)
      {
         // Start from the beginning instead.
         tmp = 0;
      }
      else if (startIndex > 0)
      {
         // In the middle of the string, so add 2 to skip the
         // ", " part we expect to have here.
         tmp += 2;
      }
      result = line.substr(tmp, nextIndex - tmp);
      startIndex = nextIndex;
   }
   return result;
}


///////////////////////////////////////////////////////////////////////////////
// DigitalIO implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~

DigitalIO::DigitalIO() : numPos_(16), busy_(false), open_(false), state_(0)
{
   task_ = 0;
   InitializeDefaultErrorMessages();

   // add custom error messages
   SetErrorText(ERR_UNKNOWN_POSITION, "Invalid position (state) specified");
   SetErrorText(ERR_INITIALIZE_FAILED, "Initialization of the device failed");
   SetErrorText(ERR_WRITE_FAILED, "Failed to write data to the device");
   SetErrorText(ERR_CLOSE_FAILED, "Failed closing the device");

   // Output channel (a.k.a. port)
   CPropertyAction* pAct = new CPropertyAction (this, &DigitalIO::OnChannel);
   int nRet = CreateStringProperty(g_PropertyChannel, "devname", false, pAct, true);
   assert(DEVICE_OK == nRet);
   std::vector<std::string> devices = GetDevices();
   if (devices.size() == 0)
   {
      AddAllowedValue(g_PropertyChannel, "No valid devices found");
   }
   for (std::vector<string>::iterator i = devices.begin(); i != devices.end(); ++i) {
      std::vector<string> ports = GetDigitalPortsForDevice(*i);
      for (std::vector<string>::iterator j = ports.begin(); j != ports.end(); ++j) {
         AddAllowedValue(g_PropertyChannel, (*j).c_str());
         if (GetNumberOfPropertyValues(g_PropertyChannel) == 1)
         {
            // First valid channel, so set it as the default value.
            SetProperty(g_PropertyChannel, (*j).c_str());
         }
      }
   }

   pAct = new CPropertyAction(this, &DAQDevice::OnSequenceLength);
   nRet = CreateStringProperty(g_PropertySequenceLength,
      boost::lexical_cast<std::string>(maxSequenceLength_).c_str(), false, pAct, true);

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
   SetContext(GetCoreCallback(), this);
   SetDeviceName();
   // set property list
   // -----------------
   
   // Name
   int nRet = CreateProperty(MM::g_Keyword_Name, g_DeviceNameDigitalIO, MM::String, true);
   if (DEVICE_OK != nRet)
   {
      return nRet;
   }

   // Description
   nRet = CreateProperty(MM::g_Keyword_Description, "Digital IO adapter", MM::String, true);
   if (DEVICE_OK != nRet)
   {
      return nRet;
   }

   // Channel. Note this has a different name from g_PropertyChannel,
   // which is the pre-init property; this version is just so you can
   // see the pre-init property in the Device Property Browser.
   nRet = CreateProperty("OutputChannel", channel_.c_str(), MM::String, true);
   if (DEVICE_OK != nRet)
   {
      return nRet;
   }

   // TODO: this and the following property are copy/pasted in the DigitalIO
   // and AnalogIO classes.
   // Manual triggering override.
   CPropertyAction* pAct = new CPropertyAction(this, &DAQDevice::OnTriggeringEnabled);
   nRet = CreateIntegerProperty(g_PropertyTriggeringEnabled, 1, false, pAct, false);
   if (DEVICE_OK != nRet)
   {
      return nRet;
   }

   // Input trigger line.
   pAct = new CPropertyAction(this, &DAQDevice::OnInputTrigger);
   nRet = CreateProperty(g_PropertyTriggerInput,
      "", MM::String, false, pAct, false);
   if (DEVICE_OK != nRet)
   {
      return nRet;
   }
   char terminalsBuf[2048];
    int error = DAQmxGetDevTerminals(deviceName_.c_str(), terminalsBuf, 2048);
   if (error)
   {
      return LogError(error, "GetDevTerminals");
   }
   std::string terminals = terminalsBuf;
   size_t index = std::string::npos;
   do
   {
      std::string terminal = GetNextEntry(terminals, index);
      AddAllowedValue(g_PropertyTriggerInput, terminal.c_str());
      if (GetNumberOfPropertyValues(g_PropertyTriggerInput) == 1)
      {
         // Make this the default.
         SetProperty(g_PropertyTriggerInput, terminal.c_str());
      }
   } while (index != std::string::npos);

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
   pAct = new CPropertyAction (this, &DigitalIO::OnState);
   nRet = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
   if (nRet != DEVICE_OK)
   {
      return nRet;
   }

   // Gate Closed Position
   nRet = CreateProperty(MM::g_Keyword_Closed_Position, "0", MM::Integer, false);
   if (nRet != DEVICE_OK)
   {
      return nRet;
   }

   SetupTask();
   
   GetGateOpen(open_);

   // Whether or not hardware triggering is available. Determined by
   // attempting to set up a triggering task and erroring if it
   // fails.
   supportsTriggering_ = (TestTriggering() == 0);
   nRet = CreateIntegerProperty(g_PropertyCanTrigger,
      supportsTriggering_, true);
   if (DEVICE_OK != nRet)
   {
      return nRet;
   }

   nRet = UpdateStatus();
   if (nRet != DEVICE_OK)
   {
      return nRet;
   }

   initialized_ = true;
   return DEVICE_OK;
}

int DigitalIO::Shutdown()
{
   CancelTask();
   
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

     // Set up a task for reading/writing digital values
      long niRet = SetupTask();
      if (niRet != DAQmxSuccess)
      {
         return niRet;
      }

      niRet = SetupTask();
      if (niRet != DAQmxSuccess)
      {
          return niRet;
      }
      niRet = DAQmxCreateDOChan(task_, channel_.c_str(), "", DAQmx_Val_ChanForAllLines);
      if (niRet != DAQmxSuccess)
      {
         return LogError(niRet, "CreateDOChan");
      }
      niRet = DAQmxStartTask(task_);

      if (gateOpen) {
         uInt32 data;
         int32 written;
         data = state;
         niRet = DAQmxWriteDigitalU32(task_, 1, 1, 10.0, DAQmx_Val_GroupByChannel, &data, &written, NULL);
         if (niRet != DAQmxSuccess)
         {
            return LogError(niRet, "WriteDigitalU32");
         }
      }
      else {
         long closed_state;
         GetProperty(MM::g_Keyword_Closed_Position, closed_state);

         uInt32 data;
         int32 written;
         data = closed_state;
         niRet = DAQmxWriteDigitalU32(task_, 1, 1, 10.0, DAQmx_Val_GroupByChannel, &data, &written, NULL);
         if (niRet != DAQmxSuccess)
         {
            return LogError(niRet, "WriteDigitalU32");
         }
      }

      state_ = (int)state;
      open_ = gateOpen;
      pProp->Set((long)state_);
   }
   else if (eAct == MM::IsSequenceable)
   {
      // Only if we believe we can trigger, and the user
      // hasn't disabled triggering.
      if (supportsTriggering_ && isTriggeringEnabled_)
      {
         pProp->SetSequenceable(maxSequenceLength_);
      }
      else
      {
         pProp->SetSequenceable(0);
      }
   }
   else if (eAct == MM::AfterLoadSequence)
   {
      // Load the sequence into NI's buffer, but don't start
      // the task.
      std::vector<std::string> sequence = pProp->GetSequence();
      if (sequence.size() > maxSequenceLength_)
      {
         return DEVICE_SEQUENCE_TOO_LARGE;
      }
      uInt32* values = new uInt32[sequence.size()];
      for (long i = 0; i < sequence.size(); ++i)
      {
         values[i] = boost::lexical_cast<uInt32>(sequence[i]);
      }
      int error = SetupDigitalTriggering(values, (long) sequence.size());
      delete values;
      if (error)
      {
         return error;
      }
   }
   else if (eAct == MM::StartSequence)
   {
      // Start the triggering task now.
      int error = DAQmxStartTask(task_);
      if (error)
      {
          return LogError(error, "StartTask");
      }
   }
   else if (eAct == MM::StopSequence)
   {
      CancelTask();
   }

   return DEVICE_OK;
}

// Set up a digital triggering task: create the output channel,
// load the sequence onto NI's buffer, and set up the triggering
// rules.
int DigitalIO::SetupDigitalTriggering(uInt32* sequence, long numVals)
{
   SetupTask();
   int error = DAQmxCreateDOChan(task_, channel_.c_str(), "",
         DAQmx_Val_ChanForAllLines);
   if (error)
   {
       return LogError(error, "CreateDOChan");
   }
   error = SetupClockInput(numVals);
   if (error)
   {
      return error;
   }
   return LoadBuffer(sequence, numVals);
}

// Load a sequence of outputs onto NI's buffer.
int DigitalIO::LoadBuffer(uInt32* sequence, long numVals)
{
   int32 numWritten = 0;
   // Wait 10s for writing to complete (maybe should vary based on length of
   // data being written?)
   int error = DAQmxWriteDigitalU32(task_, numVals, false, 10,
       DAQmx_Val_GroupByScanNumber, sequence, &numWritten, NULL);
   if (error)
   {
       return LogError(error, "WriteDigitalU32");
   }
   if (numWritten != numVals)
   {
       LogMessage(("Didn't write all of sequence; wrote only " + boost::lexical_cast<string>(numWritten)).c_str());
       return 1;
   }
   return 0;
}

// Test whether triggering is possible, by attempting to set up a
// dummy trigger sequence.
int DigitalIO::TestTriggering()
{
   int numSamples = 32;
   uInt32 *sequence = new uInt32[numSamples];
   for (int i = 0; i < numSamples; ++i)
   {
       sequence[i] = i % 16;
   }
   int result = SetupDigitalTriggering(sequence, numSamples);
   delete sequence;
   return result;
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
      resolution_(0), gateOpen_(true), demo_(false)
{
   task_ = 0;
   InitializeDefaultErrorMessages();

   // add custom error messages
   SetErrorText(ERR_UNKNOWN_POSITION, "Invalid position (state) specified");
   SetErrorText(ERR_INITIALIZE_FAILED, "Initialization of the device failed");
   SetErrorText(ERR_WRITE_FAILED, "Failed to write data to the device");
   SetErrorText(ERR_CLOSE_FAILED, "Failed closing the device");

   // Output channel, a.k.a. port.
   CPropertyAction* pAct = new CPropertyAction (this, &AnalogIO::OnChannel);
   int nRet = CreateStringProperty(g_PropertyChannel, "devname", false, pAct, true);
   assert(nRet == DEVICE_OK);
   std::vector<std::string> devices = GetDevices();
   if (devices.size() == 0)
   {
      AddAllowedValue(g_PropertyChannel, "No valid devices found");
   }
   for (std::vector<string>::iterator i = devices.begin(); i != devices.end(); ++i) {
      std::vector<string> ports = GetAnalogPortsForDevice(*i);
      for (std::vector<string>::iterator j = ports.begin(); j != ports.end(); ++j) {
         AddAllowedValue(g_PropertyChannel, (*j).c_str());
         if (GetNumberOfPropertyValues(g_PropertyChannel) == 1)
         {
            // First valid channel, so set it as the default value.
            SetProperty(g_PropertyChannel, (*j).c_str());
         }
      }
   }

   pAct = new CPropertyAction(this, &DAQDevice::OnSequenceLength);
   nRet = CreateStringProperty(g_PropertySequenceLength,
      boost::lexical_cast<std::string>(maxSequenceLength_).c_str(), false, pAct, true);
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
   SetContext(GetCoreCallback(), this);
   SetDeviceName();
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

   // TODO: this and the following property are copy/pasted in the DigitalIO
   // and AnalogIO classes.
   // Manual triggering override.
   CPropertyAction* pAct = new CPropertyAction(this, &DAQDevice::OnTriggeringEnabled);
   nRet = CreateIntegerProperty(g_PropertyTriggeringEnabled, 1, false, pAct, false);
   if (DEVICE_OK != nRet)
   {
      return nRet;
   }

   // Input trigger line.
   pAct = new CPropertyAction(this, &DAQDevice::OnInputTrigger);
   nRet = CreateProperty(g_PropertyTriggerInput,
      "", MM::String, false, pAct, false);
   if (DEVICE_OK != nRet)
   {
      return nRet;
   }
   char terminalsBuf[2048];
    int error = DAQmxGetDevTerminals(deviceName_.c_str(), terminalsBuf, 2048);
   if (error)
   {
      return LogError(error, "GetDevTerminals");
   }
   std::string terminals = terminalsBuf;
   size_t index = std::string::npos;
   do
   {
      std::string terminal = GetNextEntry(terminals, index);
      AddAllowedValue(g_PropertyTriggerInput, terminal.c_str());
      if (GetNumberOfPropertyValues(g_PropertyTriggerInput) == 1)
      {
         // Make this the default.
         SetProperty(g_PropertyTriggerInput, terminal.c_str());
      }
   } while (index != std::string::npos);

   // Whether or not hardware triggering is available. Determined by
   // attempting to set up a triggering task and erroring if it
   // fails.
   supportsTriggering_ = (TestTriggering() == 0);
   nRet = CreateIntegerProperty(g_PropertyCanTrigger,
      supportsTriggering_, true);
   if (DEVICE_OK != nRet)
   {
      return nRet;
   }

   // Volts
   // -----
   pAct = new CPropertyAction (this, &AnalogIO::OnVolts);
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
      int niRet = SetupTask();
      if (niRet)
      {
          return niRet;
      }
      niRet = DAQmxCreateAOVoltageChan(task_, channel_.c_str(), "", minV_, maxV_, DAQmx_Val_Volts, "");
      if (niRet != DAQmxSuccess)
      {
         return LogError(niRet, "CreateAOVoltageChan");
      }

       niRet = DAQmxStartTask(task_);
       if (niRet)
       {
           return LogError(niRet, "StartTask");
       }
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
      long niRet = SetupTask();
      if (niRet)
      {
          return niRet;
      }
      niRet = DAQmxCreateAOVoltageChan(task_,
          channel_.c_str(), "", minV_, maxV_,
          DAQmx_Val_Volts, "");
      if (niRet)
      {
          return LogError(niRet, "CreateAOVoltageChan");
      }
      niRet = DAQmxStartTask(task_);
      if (niRet)
      {
          return LogError(niRet, "StartTask");
      }
      float64 data[1];
      data[0] = v;
      if (disable_)	
      {
         data[0] = 0;
      }
      niRet = DAQmxWriteAnalogF64(task_, 1, 1, 10.0, DAQmx_Val_GroupByChannel, data, NULL, NULL);
      if (niRet != DAQmxSuccess)
      {
         return LogError(niRet, "WriteAnalogF64");
      }
   }
   volts_ = v;
   return DEVICE_OK;
}

int AnalogIO::StartDASequence()
{
   int error = SetupTask();
   if (error)
   {
       return error;
   }
   error = DAQmxCreateAOVoltageChan(task_, channel_.c_str(), "",
      minV_, maxV_, DAQmx_Val_Volts, "");
   if (error)
   {
       return LogError(error, "CreateAOVoltageChan");
   }
   error = SetupClockInput((int) sequence_.size());
   if (error)
   {
      return error;
   }
   error = LoadBuffer();
   if (error)
   {
      return error;
   }
   error = DAQmxStartTask(task_);
   if (error)
   {
      return LogError(error, "StartTask");
   }
   return DEVICE_OK;
}

int AnalogIO::StopDASequence()
{
   CancelTask();
   return DEVICE_OK;
}
int AnalogIO::ClearDASequence()
{
   sequence_.clear();
   return DEVICE_OK;
}
int AnalogIO::AddToDASequence(double val)
{
   sequence_.push_back(val);
   return DEVICE_OK;
}
int AnalogIO::SendDASequence()
{
   return LoadBuffer();
}

// Load our analog sequence onto the NI buffer.
int AnalogIO::LoadBuffer()
{
   float64* values = new float64[sequence_.size()];
   for (int i = 0; i < sequence_.size(); ++i)
   {
      values[i] = sequence_[i];
   }
   int32 numWritten = 0;
   int error = DAQmxWriteAnalogF64(task_, (int32) sequence_.size(), false, -1,
      DAQmx_Val_GroupByChannel, values, &numWritten, NULL);
   delete values;
   if (error)
   {
      return LogError(error, "WriteAnalogF64");
   }
   if (numWritten != sequence_.size())
   {
      LogMessage(("Didn't write complete analog sequence to buffer: wrote " +
         boost::lexical_cast<string>(numWritten) + " of " +
         boost::lexical_cast<string>(sequence_.size()) + " values").c_str());
      return 1;
   }
   return DEVICE_OK;
}

// Attempt to set up and run a triggering task.
int AnalogIO::TestTriggering()
{
   int error = StopDASequence();
   if (error)
   {
      return error;
   }
   error = ClearDASequence();
   if (error)
   {
      return error;
   }
   for (int i = 0; i < 100; ++i)
   {
      error = AddToDASequence(maxV_ / 10.0 * (i % 10));
      if (error)
      {
         return error;
      }
   }
   return StartDASequence();
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
      {
         return ApplyVoltage(v);
      }
   }
   else if (eAct == MM::IsSequenceable)
   {
      // Only if we believe we can trigger, and the user
      // hasn't disabled triggering.
      if (supportsTriggering_ && isTriggeringEnabled_)
      {
         pProp->SetSequenceable(maxSequenceLength_);
      }
      else
      {
         pProp->SetSequenceable(0);
      }
   }
   else if (eAct == MM::AfterLoadSequence)
   {
      // Transfer the sequence into our internal storage.
      ClearDASequence();
      SetupTask();
      std::vector<std::string> sequence = pProp->GetSequence();
      if (sequence.size() > maxSequenceLength_)
      {
         return DEVICE_SEQUENCE_TOO_LARGE;
      }
      for (long i = 0; i < sequence.size(); ++i)
      {
         AddToDASequence(boost::lexical_cast<float64>(sequence[i]));
      }
   }
   else if (eAct == MM::StartSequence)
   {
      return StartDASequence();
   }
   else if (eAct == MM::StopSequence)
   {
      int error = StopDASequence();
      if (error)
      {
         return error;
      }
      error = ClearDASequence();
      if (error)
      {
         return error;
      }
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
