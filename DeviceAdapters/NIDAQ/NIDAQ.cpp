///////////////////////////////////////////////////////////////////////////////
// FILE:          NIDAQ.cpp
// PROJECT:       Micro-Manager
// SUBSYTEM:      DevcieAdapters
//
// DESCRIPTION:   Adapter for National Instruments IO devices
// COPYRIGHT:     University of California, San Francisco, 2008
// LICENSE:       LGPL
//
// AUTHOR:        Nico Stuurman, nico@cmp.ucsf.edu, 10/04/2008
//
//


#include "NIDAQ.h"
#include "../../../3rdParty/trunk/NationalInstruments/NI-DAQmxBase/includes/NIDAQmxBase.h"
#include "../../MMDevice/ModuleInterface.h"


#ifdef WIN32
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#define snprintf _snprintf
#endif

const char* g_DeviceNameNIDAQDO = "NI-DAQ-DO";
const char* g_DeviceNameNIDAQAO = "NI-DAQ-AO";
const char* g_DeviceNameNIDAQDPattern = "NI-DAQ-Digital-Pattern";

MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_DeviceNameNIDAQDO, MM::ShutterDevice, "NI-DAQ DIO");
   RegisterDevice(g_DeviceNameNIDAQAO, MM::SignalIODevice, "NI-DAQ AO");
   RegisterDevice(g_DeviceNameNIDAQDPattern, MM::GenericDevice, "NI-DAQ Triggered digital patterns");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   else if (strcmp(deviceName,g_DeviceNameNIDAQDO) == 0)
      return new NIDAQDO;

   else if (strcmp(deviceName,g_DeviceNameNIDAQAO) == 0)
      return new NIDAQAO;

   else if (strcmp(deviceName,g_DeviceNameNIDAQDPattern) == 0)
      return new NIDAQDPattern;

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

/*
 * NIDAQDO: Digital out.  
 */
NIDAQDO::NIDAQDO() : 
   taskHandle_(0),
   state_(0),
   deviceName_("/Dev1/Port0"),
   inverted_(false),
   open_(false),
   initialized_ (false)
{
   InitializeDefaultErrorMessages();

   SetErrorText(ERR_OPEN_DEVICE_FAILED, "Failed communicating with National Instruments Device");

   CPropertyAction* pAct = new CPropertyAction(this, &NIDAQDO::OnDevice);
   CreateProperty("DeviceName", deviceName_.c_str(), MM::String, false, pAct, true);
}

NIDAQDO::~NIDAQDO()
{
   Shutdown();
}

void NIDAQDO::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_DeviceNameNIDAQDO);
}

int NIDAQDO::Initialize()
{
   int ret = CreateProperty(MM::g_Keyword_Name, g_DeviceNameNIDAQDO, MM::String, true);
   if (ret != DEVICE_OK)
      return ret;

   CPropertyAction* pAct = new CPropertyAction(this, &NIDAQDO::OnLogic);
   ret = CreateProperty("Logic", "Normal", MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   AddAllowedValue("Logic", "Normal");
   AddAllowedValue("Logic", "Inverted");

   pAct = new CPropertyAction(this, &NIDAQDO::OnState);
   ret = CreateProperty("State", "0", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   // This needs to be changed for devices with more than 8 outputs
   SetPropertyLimits("State", 0, 255);

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;
 
   initialized_ = true;

   return DEVICE_OK;
} 

int NIDAQDO::Shutdown()
{
   if (taskHandle_ != 0) {
      DAQmxBaseStopTask (taskHandle_);
      DAQmxBaseClearTask (taskHandle_);
   }                                                                         
   initialized_ = false;
   return DEVICE_OK;
}

bool NIDAQDO::Busy()
{
   // TODO: timeouts
   return false;
}

int NIDAQDO::SetOpen(bool open/* = true*/)
{
   open_ = open;
   if (open) {
      return WriteToDevice(state_);
   } else {
      return WriteToDevice(0);
   }
   return DEVICE_OK;
}



int NIDAQDO::HandleDAQError(int ret, TaskHandle taskHandle)
{
   char errBuff[2048];
   DAQmxBaseGetExtendedErrorInfo (errBuff, 2048);
   SetErrorText(ret, errBuff);

   if (taskHandle != 0) {
      DAQmxBaseStopTask (taskHandle);
      DAQmxBaseClearTask (taskHandle);
   }                                                                         

   std::ostringstream os;
   os << "DAQmxBase Error: " << ret << "-" << errBuff;
   LogMessage(os.str().c_str());

   return ret;
}

int NIDAQDO::WriteToDevice(long lnValue) 
{
   // Note: the following will fail whith everything but 8-bit values
   if (inverted_)
      lnValue = (255 & ~lnValue);

   uInt32 w_data[1];
   int32 written;
   w_data[0] = lnValue;

   int ret = DAQmxBaseWriteDigitalU32(taskHandle_,1,1,10.0,DAQmx_Val_GroupByChannel,w_data,&written,NULL);
   if (DAQmxFailed(ret))
      HandleDAQError(ret, taskHandle_);

   return DEVICE_OK;
}

int NIDAQDO::InitializeDevice()
{
   // Create task
   int ret = DAQmxBaseCreateTask("", &taskHandle_);
   if (DAQmxFailed(ret))
      return HandleDAQError(ret, taskHandle_);

   ret = DAQmxBaseCreateDOChan(taskHandle_,deviceName_.c_str(),"",DAQmx_Val_ChanForAllLines);
   if (DAQmxFailed(ret))
      return HandleDAQError(ret, taskHandle_);

   return DEVICE_OK;
}


////////////////////////////
// Action Handlers
///////////////////////////

int NIDAQDO::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      // Nothing to do, let the caller use cached property
   } else if (eAct ==MM::AfterSet) {
      long state;
      pProp->Get(state);
      state_ = state;
      SetOpen(state_);
   }

   return DEVICE_OK;
}

int NIDAQDO::OnLogic(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      // Nothing to do, let the caller use cached property
   } else if (eAct ==MM::AfterSet) {
      std::string logic;
      pProp->Get(logic);
      if (logic ==  "Inverted")
         inverted_ = true;
      else
         inverted_ = false;
      SetOpen(open_);
   }

   return DEVICE_OK;
}

int NIDAQDO::OnDevice(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      pProp->Set(deviceName_.c_str());
   } else if (eAct ==MM::AfterSet) {
      pProp->Get(deviceName_);
      LogMessage(deviceName_.c_str());
      return InitializeDevice();
   }

   return DEVICE_OK;
}

/*
 * NIDAQAO: Analog output device
 */
NIDAQAO::NIDAQAO() : 
   taskHandle_(0),
   deviceName_("/Dev1/ao0"),
   initialized_ (false),
   busy_(false),
   volts_ (0.0),
   minVolt_(0.0),
   maxVolt_(5.0),
   gateOpen_(true)
{
   InitializeDefaultErrorMessages();

   SetErrorText(ERR_OPEN_DEVICE_FAILED, "Failed communicating with National Instruments Device");

   CPropertyAction* pAct = new CPropertyAction(this, &NIDAQAO::OnDevice);
   CreateProperty("DeviceName", deviceName_.c_str(), MM::String, false, pAct, true);
   
   pAct = new CPropertyAction(this, &NIDAQAO::OnMinVolt);
   CreateProperty("Minimum Voltage", "0.0", MM::Float, false, pAct, true);
   
   pAct = new CPropertyAction(this, &NIDAQAO::OnMaxVolt);
   CreateProperty("Maximum Voltage", "5.0", MM::Float, false, pAct, true);
}

NIDAQAO::~NIDAQAO()
{
   Shutdown();
}

void NIDAQAO::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_DeviceNameNIDAQAO);
}

int NIDAQAO::Initialize()
{
   int ret = CreateProperty(MM::g_Keyword_Name, g_DeviceNameNIDAQAO, MM::String, true);
   if (ret != DEVICE_OK)
      return ret;

   CPropertyAction* pAct = new CPropertyAction(this, &NIDAQAO::OnVolt);
   ret = CreateProperty("Volt", "0.0", MM::Float, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   SetPropertyLimits("Volt", minVolt_, maxVolt_);

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;
 
   initialized_ = true;

   return DEVICE_OK;
} 

int NIDAQAO::Shutdown()
{
   if (taskHandle_ != 0) {
      DAQmxBaseStopTask (taskHandle_);
      DAQmxBaseClearTask (taskHandle_);
   }                                                                         
   initialized_ = false;
   return DEVICE_OK;
}

bool NIDAQAO::Busy()
{
   // TODO: timeouts
   return false;
}

int NIDAQAO::SetGateOpen(bool open/* = true*/)
{
   gateOpen_ = open;
   if (open) {
      return WriteToDevice(volts_);
   } else {
      return WriteToDevice(0);
   }
   return DEVICE_OK;
}

int NIDAQAO::SetSignal(double volts)
{
   volts_ = volts;
   if (gateOpen_)
      return WriteToDevice(volts_);

   return DEVICE_OK;
}


int NIDAQAO::HandleDAQError(int ret, TaskHandle taskHandle)
{
   char errBuff[2048];
   DAQmxBaseGetExtendedErrorInfo (errBuff, 2048);
   SetErrorText(ret, errBuff);

   if (taskHandle_ != 0) {
      DAQmxBaseStopTask (taskHandle);
      DAQmxBaseClearTask (taskHandle);
   }                                                                         

   std::ostringstream os;
   os << "DAQmxBase Error: " << ret << "-" << errBuff;
   LogMessage(os.str().c_str());

   return ret;
}

int NIDAQAO::WriteToDevice(double volt) 
{
   uInt64 samplesPerChan = 1;

   float64 data = volt;
   int32 pointsWritten;
   float64 timeout = 10.0;

   int ret = DAQmxBaseWriteAnalogF64(taskHandle_,samplesPerChan, 0, timeout, DAQmx_Val_GroupByChannel, &data, &pointsWritten, NULL);
   if (DAQmxFailed(ret))
      HandleDAQError(ret, taskHandle_);

   return DEVICE_OK;
}

int NIDAQAO::InitializeDevice()
{
   // Create task
   int ret = DAQmxBaseCreateTask("", &taskHandle_);
   if (DAQmxFailed(ret))
      return HandleDAQError(ret, taskHandle_);

   ret = DAQmxBaseCreateAOVoltageChan(taskHandle_,deviceName_.c_str(),"",minVolt_, maxVolt_, DAQmx_Val_Volts, NULL);
   if (DAQmxFailed(ret))
      return HandleDAQError(ret, taskHandle_);

   ret = DAQmxBaseStartTask(taskHandle_);
   if (DAQmxFailed(ret))
      return HandleDAQError(ret, taskHandle_);

   return WriteToDevice(volts_);
}


////////////////////////////
// Action Handlers
///////////////////////////

int NIDAQAO::OnVolt(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      pProp->Set(volts_);
      // Nothing to do, let the caller use cached property
   } else if (eAct ==MM::AfterSet) {
      pProp->Get(volts_);
      if (gateOpen_)
         WriteToDevice(volts_);
   }

   return DEVICE_OK;
}

int NIDAQAO::OnMinVolt(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      pProp->Set(minVolt_);
      // Nothing to do, let the caller use cached property
   } else if (eAct ==MM::AfterSet) {
      pProp->Get(minVolt_);
   }

   return DEVICE_OK;
}

int NIDAQAO::OnMaxVolt(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      pProp->Set(maxVolt_);
      // Nothing to do, let the caller use cached property
   } else if (eAct ==MM::AfterSet) {
      pProp->Get(maxVolt_);
   }
   return DEVICE_OK;
}


int NIDAQAO::OnDevice(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      pProp->Set(deviceName_.c_str());
   } else if (eAct ==MM::AfterSet) {
      pProp->Get(deviceName_);
      LogMessage(deviceName_.c_str());
      if (taskHandle_ != 0) {
         DAQmxBaseStopTask(taskHandle_);
         DAQmxBaseClearTask(taskHandle_);
      }
      return InitializeDevice();
   }

   return DEVICE_OK;
}


/**
 * NIDAQDPattern
 * Generic device that generates a digital pattern triggered by an external clock
 */

NIDAQDPattern::NIDAQDPattern() :
   digitalPattern_("0 0"),
   edge_("Rising"),
   status_("Idle"),
   nrRepeats_ (1),
   samples_ (2),
   taskHandle_(0),
   deviceName_("/Dev1/port0"),
   externalClockPort_ ("/Dev1/PFI0"),
   initialized_ (false)
{
   InitializeDefaultErrorMessages();

   SetErrorText(ERR_OPEN_DEVICE_FAILED, "Failed communicating with National Instruments Device");
   SetErrorText(ERR_INVALID_DIGITAL_PATTERN, "Digital Pattern should be up to 8 numbers separated by commas, e.g.: 2,4,6");
   SetErrorText(ERR_INVALID_REPEAT_NR, "Nr. of Repeats should be an integer number greater than 0");
   SetErrorText(ERR_DP_NOT_INITIALIZED, "Digital Pattern not initialized (maybe not all parameters were entered yet?");

   CPropertyAction* pAct = new CPropertyAction(this, &NIDAQDPattern::OnDevice);
   CreateProperty("DeviceName", deviceName_.c_str(), MM::String, false, pAct, true);

   pAct = new CPropertyAction(this, &NIDAQDPattern::OnExternalClockPort);
   CreateProperty("External Clock Port", externalClockPort_.c_str(), MM::String, false, pAct, true);
}

NIDAQDPattern::~NIDAQDPattern()
{
   Shutdown();
}

int NIDAQDPattern::Shutdown()
{
   // Don't return errors, just log them.  We are shutting down after all...
   bool32 isTaskDone;
   int ret = DAQmxBaseIsTaskDone(taskHandle_, &isTaskDone);
   if (DAQmxFailed(ret))
      HandleDAQError(ret, taskHandle_);

   if (isTaskDone) {
      int ret = DAQmxBaseStopTask (taskHandle_);
      if (DAQmxFailed(ret))
         HandleDAQError(ret, taskHandle_);
   }
   ret = DAQmxBaseClearTask(taskHandle_);
   if (DAQmxFailed(ret))
      HandleDAQError(ret, taskHandle_);

   return DEVICE_OK;
}

void NIDAQDPattern::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_DeviceNameNIDAQDPattern);
}

int NIDAQDPattern::Initialize()
{
   int ret = CreateProperty(MM::g_Keyword_Name, g_DeviceNameNIDAQDPattern, MM::String, true);
   if (ret != DEVICE_OK)
      return ret;

   CPropertyAction* pAct = new CPropertyAction(this, &NIDAQDPattern::OnEdge);
   ret = CreateProperty("Edge", "Rising", MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   AddAllowedValue("Edge", "Rising");
   AddAllowedValue("Edge", "Falling");
   
   pAct = new CPropertyAction(this, &NIDAQDPattern::OnDigitalPattern);
   ret = CreateProperty("Digital Pattern", digitalPattern_.c_str(), MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   pAct = new CPropertyAction(this, &NIDAQDPattern::OnRepeat);
   ret = CreateProperty("Repeat", "1", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   pAct = new CPropertyAction(this, &NIDAQDPattern::OnStatus);
   ret = CreateProperty("Status", status_.c_str(), MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   AddAllowedValue("Status", "Start");
   AddAllowedValue("Status", "Stop");
   AddAllowedValue("Status", "Idle");
   AddAllowedValue("Status", "Busy");

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;
 
   initialized_ = true;

   return DEVICE_OK;
} 

int NIDAQDPattern::HandleDAQError(int ret, TaskHandle taskHandle)
{
   char errBuff[2048];
   DAQmxBaseGetExtendedErrorInfo (errBuff, 2048);
   SetErrorText(ret, errBuff);

   if (taskHandle_ != 0) {
      DAQmxBaseStopTask (taskHandle);
      DAQmxBaseClearTask (taskHandle);
   }                                                                         

   std::ostringstream os;
   os << "DAQmxBase Error: " << ret << "-" << errBuff;
   LogMessage(os.str().c_str());

   return ret;
}


bool NIDAQDPattern::Busy()
{
   return false;
}
      
int NIDAQDPattern::InitializeDevice()
{
   // Create task
   int ret = DAQmxBaseCreateTask("DPattern", &taskHandle_);
   if (DAQmxFailed(ret))
      return HandleDAQError(ret, taskHandle_);

   ret = DAQmxBaseCreateDOChan(taskHandle_,deviceName_.c_str(),"", DAQmx_Val_ChanForAllLines);
   if (DAQmxFailed(ret))
      return HandleDAQError(ret, taskHandle_);

   int32 edge = DAQmx_Val_Falling;
   if (edge_ != "Falling")
      edge = DAQmx_Val_Rising;
   // note: parameter frequency now fixed at 10000 (0.1 msec).  Could be exposed
    ret = DAQmxBaseCfgSampClkTiming(taskHandle_,externalClockPort_.c_str(),10000, edge, DAQmx_Val_ContSamps, (uInt64) ((uInt64)nrRepeats_ * (uInt64) samples_));
   if (DAQmxFailed(ret))
      return HandleDAQError(ret, taskHandle_);

   ret = DAQmxBaseWriteDigitalU32(taskHandle_, samples_ * nrRepeats_, 0, 60, DAQmx_Val_GroupByChannel, data_, NULL, NULL);
   if (DAQmxFailed(ret))
      return HandleDAQError(ret, taskHandle_);

   return DEVICE_OK;
}

//////////////////////////////////
// NIDAQDPattern Action interfaces
//////////////////////////////////

int NIDAQDPattern::OnDevice(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      pProp->Set(deviceName_.c_str());
   } else if (eAct ==MM::AfterSet) {
      pProp->Get(deviceName_);
      LogMessage(deviceName_.c_str(), true);
      if (taskHandle_ != 0) {
         DAQmxBaseStopTask(taskHandle_);
         DAQmxBaseClearTask(taskHandle_);
      }
      // return InitializeDevice();
   }

   return DEVICE_OK;
}

int NIDAQDPattern::OnExternalClockPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      pProp->Set(externalClockPort_.c_str());
   } else if (eAct ==MM::AfterSet) {
      pProp->Get(externalClockPort_);
      LogMessage(externalClockPort_.c_str(), true);
      if (taskHandle_ != 0) {
         DAQmxBaseStopTask(taskHandle_);
         DAQmxBaseClearTask(taskHandle_);
      }
      // return InitializeDevice();
   }

   return DEVICE_OK;
}

int NIDAQDPattern::OnEdge(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      pProp->Set(edge_.c_str());
   } else if (eAct ==MM::AfterSet) {
      pProp->Get(edge_);
      LogMessage(edge_.c_str(), true);
      if (taskHandle_ != 0) {
         DAQmxBaseStopTask(taskHandle_);
         DAQmxBaseClearTask(taskHandle_);
      }
      // return InitializeDevice();
   }

   return DEVICE_OK;
}

int NIDAQDPattern::OnDigitalPattern(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      pProp->Set(digitalPattern_.c_str());
   } else if (eAct ==MM::AfterSet) {
      std::string pattern;
      pProp->Get(pattern);
      // tokenize pattern 
      const char* seperator = " ";
      std::string::size_type lastPos = pattern.find_first_not_of(seperator, 0);
      std::string::size_type pos = pattern.find_first_of(seperator, lastPos);
      int counter = 0;
      while ( (pos != std::string::npos || lastPos != std::string::npos) && counter < maxNrSamples_) {
         std::istringstream is(pattern.substr(lastPos, pos - lastPos));
         if (! (is >> data_[counter]))
           return ERR_INVALID_DIGITAL_PATTERN;
         lastPos = pattern.find_first_not_of(seperator, pos);
         pos = pattern.find_first_of(seperator, lastPos);
         counter++;
      }
      samples_ = counter;
      digitalPattern_ = pattern;

      if (taskHandle_ != 0) {
         DAQmxBaseStopTask(taskHandle_);
         DAQmxBaseClearTask(taskHandle_);
      }
   }

   return DEVICE_OK;
}

int NIDAQDPattern::OnRepeat(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      std::ostringstream os;
      os << nrRepeats_;
      pProp->Set(os.str().c_str());
   } else if (eAct ==MM::AfterSet) {
      std::string repeat;
      pProp->Get(repeat);
      std::istringstream is(repeat);
      if (!(is >> nrRepeats_) || nrRepeats_ < 1)
         return ERR_INVALID_REPEAT_NR;

      // return InitializeDevice();
   }

   return DEVICE_OK;
}


int NIDAQDPattern::OnStatus(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      bool32 isTaskDone;
      int ret = DAQmxBaseIsTaskDone(taskHandle_, &isTaskDone);
      if (DAQmxFailed(ret))
         HandleDAQError(ret, taskHandle_);
      if (!isTaskDone)
         pProp->Set("Busy");
      else
         pProp->Set("Idle");
   } else if (eAct ==MM::AfterSet) {
      std::string status;
      pProp->Get(status);
      if (status == "Start") {
         if (taskHandle_ != 0) {
            int ret = DAQmxBaseStopTask(taskHandle_); 
            if (DAQmxFailed(ret))
               return HandleDAQError(ret, taskHandle_);
            ret = DAQmxBaseClearTask(taskHandle_);
            if (DAQmxFailed(ret))
               return HandleDAQError(ret, taskHandle_);
         }
         InitializeDevice();
         // return ERR_DP_NOT_INITIALIZED;
         int ret = DAQmxBaseStartTask(taskHandle_);
         if (DAQmxFailed(ret))
            return HandleDAQError(ret, taskHandle_);
      } else if (status == "Stop") {
         if (taskHandle_ != 0) {
            int ret = DAQmxBaseStopTask(taskHandle_); 
            if (DAQmxFailed(ret))
               return HandleDAQError(ret, taskHandle_);
            ret = DAQmxBaseClearTask(taskHandle_);
            if (DAQmxFailed(ret))
               return HandleDAQError(ret, taskHandle_);
         }
      }
   }

   return DEVICE_OK;
}



