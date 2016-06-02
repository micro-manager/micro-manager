// DESCRIPTION:   Drive multiple analog outputs on NI DAQ
// AUTHOR:        Mark Tsuchida, 2015
// COPYRIGHT:     2015-2016, Open Imaging, Inc.
// LICENSE:       This library is free software; you can redistribute it and/or
//                modify it under the terms of the GNU Lesser General Public
//                License as published by the Free Software Foundation; either
//                version 2.1 of the License, or (at your option) any later
//                version.
//
//                This library is distributed in the hope that it will be
//                useful, but WITHOUT ANY WARRANTY; without even the implied
//                warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
//                PURPOSE.  See the GNU Lesser General Public License for more
//                details.
//
//                You should have received a copy of the GNU Lesser General
//                Public License along with this library; if not, write to the
//                Free Software Foundation, Inc., 51 Franklin Street, Fifth
//                Floor, Boston, MA  02110-1301  USA

#include "NIMultiAnalog.h"

#include "ModuleInterface.h"

#include <boost/algorithm/string/classification.hpp>
#include <boost/algorithm/string/split.hpp>
#include <boost/math/common_factor_rt.hpp>
#include <boost/scoped_array.hpp>


const char* g_DeviceNameMultiAnalogOutHub = "NIMultiAnalogOutHub";
const char* g_DeviceNameMultiAnalogOutPortPrefix = "NIMultiAnalogOut-";

const char* g_On = "On";
const char* g_Off = "Off";

const char* g_Never = "Never";
const char* g_UseHubSetting = "Use hub setting";


const int ERR_SEQUENCE_RUNNING = 2001;
const int ERR_SEQUENCE_TOO_LONG = 2002;
const int ERR_SEQUENCE_ZERO_LENGTH = 2003;
const int ERR_VOLTAGE_OUT_OF_RANGE = 2004;
const int ERR_NONUNIFORM_CHANNEL_VOLTAGE_RANGES = 2005;
const int ERR_VOLTAGE_RANGE_EXCEEDS_DEVICE_LIMITS = 2006;


MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_DeviceNameMultiAnalogOutHub, MM::HubDevice, "Multi-channel analog output");
}


MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_DeviceNameMultiAnalogOutHub) == 0)
   {
      return new MultiAnalogOutHub;
   }
   else if (std::string(deviceName).
      substr(0, strlen(g_DeviceNameMultiAnalogOutPortPrefix)) ==
      g_DeviceNameMultiAnalogOutPortPrefix)
   {
      return new MultiAnalogOutPort(std::string(deviceName).
         substr(strlen(g_DeviceNameMultiAnalogOutPortPrefix)));
   }

   return 0;
}


MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}


//
// Utility functions
//

static int
GetVoltageRangeForDevice(const std::string& device,
   double& minVolts, double& maxVolts)
{
   const int MAX_PORTS = 64;
   float64 ranges[2 * MAX_PORTS];
   for (int i = 0; i < MAX_PORTS; ++i)
   {
      ranges[2 * i] = 0.0;
      ranges[2 * i + 1] = 0.0;
   }

   int32 ret = DAQmxGetDevAOVoltageRngs(device.c_str(), ranges,
      sizeof(ranges) / sizeof(float64));
   if (ret != 0)
      return ret;

   // Find the common min and max.
   double commonMin = ranges[0];
   double commonMax = ranges[1];
   for (int i = 0; i < MAX_PORTS; ++i)
   {
      if (ranges[2 * i] == 0.0 && ranges[2 * i + 1] == 0.0)
         break;

      if (ranges[2 * i] != commonMin)
         return ERR_NONUNIFORM_CHANNEL_VOLTAGE_RANGES;
      if (ranges[2 * i + 1] != commonMax)
         return ERR_NONUNIFORM_CHANNEL_VOLTAGE_RANGES;
   }

   minVolts = commonMin;
   maxVolts = commonMax;

   return DEVICE_OK;
}

static std::vector<std::string>
GetTriggerPortsForDevice(const std::string& device)
{
   std::vector<std::string> result;

   char ports[4096];
   int32 ret = DAQmxGetDevTerminals(device.c_str(), ports, sizeof(ports));
   if (ret == 0)
   {
      std::vector<std::string> terminals;
      boost::split(terminals, ports, boost::is_any_of(", "),
         boost::token_compress_on);

      // Only return the PFI terminals.
      for (std::vector<std::string>::const_iterator
         it = terminals.begin(), end = terminals.end();
         it != end; ++it)
      {
         if (it->find("PFI") != std::string::npos)
         {
            result.push_back(*it);
         }
      }
   }

   return result;
}


static std::vector<std::string>
GetAnalogPortsForDevice(const std::string& device)
{
   std::vector<std::string> result;

   char ports[4096];
   int32 ret = DAQmxGetDevAOPhysicalChans(device.c_str(), ports, sizeof(ports));
   if (ret == 0)
   {
      boost::split(result, ports, boost::is_any_of(", "),
         boost::token_compress_on);
   }

   return result;
}


//
// MultiAnalogOutHub
//

MultiAnalogOutHub::MultiAnalogOutHub() :
   initialized_(false),
   maxSequenceLength_(1024),
   sequencingEnabled_(false),
   minVolts_(0.0),
   maxVolts_(5.0),
   sampleRateHz_(10000.0),
   task_(0)
{
   CPropertyAction* pAct = new CPropertyAction(this, &MultiAnalogOutHub::OnDevice);
   int ret = CreateStringProperty("Device", "", false, pAct, true);

   pAct = new CPropertyAction(this, &MultiAnalogOutHub::OnMaxSequenceLength);
   ret = CreateIntegerProperty("MaxSequenceLength",
      static_cast<long>(maxSequenceLength_), false, pAct, true);
}


MultiAnalogOutHub::~MultiAnalogOutHub()
{
   Shutdown();
}


int MultiAnalogOutHub::Initialize()
{
   if (initialized_)
      return DEVICE_OK;

   if (!GetParentHub())
      return DEVICE_ERR;

   // Determine the possible voltage range
   int ret = GetVoltageRangeForDevice(niDeviceName_, minVolts_, maxVolts_);
   if (ret != DEVICE_OK)
      return ret;

   CPropertyAction* pAct = new CPropertyAction(this, &MultiAnalogOutHub::OnSequencingEnabled);
   ret = CreateStringProperty("Sequence", sequencingEnabled_ ? g_On : g_Off,
      false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   AddAllowedValue("Sequence", g_On);
   AddAllowedValue("Sequence", g_Off);

   std::vector<std::string> triggerPorts = GetTriggerPortsForDevice(niDeviceName_);
   if (!triggerPorts.empty())
   {
      niTriggerPort_ = triggerPorts[0];
      pAct = new CPropertyAction(this, &MultiAnalogOutHub::OnTriggerInputPort);
      ret = CreateStringProperty("TriggerInputPort", niTriggerPort_.c_str(), false, pAct);
      if (ret != DEVICE_OK)
         return ret;
      for (std::vector<std::string>::const_iterator it = triggerPorts.begin(),
            end = triggerPorts.end();
            it != end; ++it)
      {
         AddAllowedValue("TriggerInputPort", it->c_str());
      }

      pAct = new CPropertyAction(this, &MultiAnalogOutHub::OnSampleRate);
      ret = CreateFloatProperty("SampleRateHz", sampleRateHz_, false, pAct);
      if (ret != DEVICE_OK)
         return ret;
   }

   initialized_ = true;
   return DEVICE_OK;
}


int MultiAnalogOutHub::Shutdown()
{
   if (!initialized_)
      return DEVICE_OK;

   int ret = StopTask();

   physicalChannels_.clear();
   channelSequences_.clear();

   initialized_ = false;
   return ret;
}


void MultiAnalogOutHub::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_DeviceNameMultiAnalogOutHub);
}


int MultiAnalogOutHub::DetectInstalledDevices()
{
   std::vector<std::string> aoPorts =
      GetAnalogPortsForDevice(niDeviceName_);

   for (std::vector<std::string>::const_iterator it = aoPorts.begin(), end = aoPorts.end();
      it != end; ++it)
   {
      MM::Device* pDevice =
         ::CreateDevice((g_DeviceNameMultiAnalogOutPortPrefix + *it).c_str());
      if (pDevice)
      {
         AddInstalledDevice(pDevice);
      }
   }
   return DEVICE_OK;
}


int MultiAnalogOutHub::GetVoltageLimits(double& minVolts, double& maxVolts)
{
   minVolts = minVolts_;
   maxVolts = maxVolts_;
   return DEVICE_OK;
}


int MultiAnalogOutHub::StartSequenceForPort(const std::string& port,
   const std::vector<double> sequence)
{
   int ret = StopTask();
   if (ret != DEVICE_OK)
      return ret;

   ret = AddPortToSequencing(port, sequence);
   if (ret != DEVICE_OK)
      return ret;

   ret = StartSequencingTask();
   if (ret != DEVICE_OK)
      return ret;
   // We don't restart the task without this port on failure.
   // There is little point in doing so.

   return DEVICE_OK;
}


int MultiAnalogOutHub::StopSequenceForPort(const std::string& port)
{
   int ret = StopTask();
   if (ret != DEVICE_OK)
      return ret;
   RemovePortFromSequencing(port);
   // We do not restart sequencing for the remaining ports,
   // since it is meaningless (we can't preserve their state).
   return DEVICE_OK;
}


int MultiAnalogOutHub::AddPortToSequencing(const std::string& port,
   const std::vector<double> sequence)
{
   if (sequence.size() > maxSequenceLength_)
      return ERR_SEQUENCE_TOO_LONG;

   RemovePortFromSequencing(port);

   physicalChannels_.push_back(port);
   channelSequences_.push_back(sequence);
   return DEVICE_OK;
}


void MultiAnalogOutHub::RemovePortFromSequencing(const std::string& port)
{
   // We assume a given port appears at most once in physicalChannels_
   size_t n = physicalChannels_.size();
   for (size_t i = 0; i < n; ++i)
   {
      if (physicalChannels_[i] == port) {
         physicalChannels_.erase(physicalChannels_.begin() + i);
         channelSequences_.erase(channelSequences_.begin() + i);
         break;
      }
   }
}


int MultiAnalogOutHub::IsSequencingEnabled(bool& flag) const
{
   flag = sequencingEnabled_;
   return DEVICE_OK;
}


int MultiAnalogOutHub::GetSequenceMaxLength(long& maxLength) const
{
   maxLength = static_cast<long>(maxSequenceLength_);
   return DEVICE_OK;
}


std::string MultiAnalogOutHub::GetPhysicalChannelListForSequencing() const
{
   std::string ret;
   for (std::vector<std::string>::const_iterator begin = physicalChannels_.begin(),
      end = physicalChannels_.end(), it = begin;
      it != end; ++it)
   {
      if (it != begin)
         ret += ", ";
      ret += *it;
   }
   return ret;
}


int MultiAnalogOutHub::GetLCMSamplesPerChannel(size_t& seqLen) const
{
   // Use an arbitrary but reasonable limit to prevent
   // overflow or excessive memory consumption.
   const uint64_t factorLimit = 2 << 14;

   uint64_t len = 1;
   for (int i = 0; i < channelSequences_.size(); ++i)
   {
      uint64_t channelSeqLen = channelSequences_[i].size();
      if (channelSeqLen > factorLimit)
      {
         return ERR_SEQUENCE_TOO_LONG;
      }
      if (channelSeqLen == 0)
      {
         return ERR_SEQUENCE_ZERO_LENGTH;
      }
      len = boost::math::lcm(len, channelSeqLen);
      if (len > factorLimit)
      {
         return ERR_SEQUENCE_TOO_LONG;
      }
   }
   seqLen = len;
   return DEVICE_OK;
}


void MultiAnalogOutHub::GetLCMSequence(double* buffer) const
{
   size_t seqLen;
   if (GetLCMSamplesPerChannel(seqLen) != DEVICE_OK)
      return;

   for (int i = 0; i < channelSequences_.size(); ++i)
   {
      size_t chanOffset = seqLen * i;
      size_t chanSeqLen = channelSequences_[i].size();
      for (int j = 0; j < seqLen; ++j)
      {
         buffer[chanOffset + j] =
            channelSequences_[i][j % chanSeqLen];
      }
   }
}


int MultiAnalogOutHub::StartSequencingTask()
{
   if (task_)
   {
      int err = StopTask();
      if (err != DEVICE_OK)
         return err;
   }

   LogMessage("Starting sequencing task", true);

   boost::scoped_array<float64> samples;

   size_t numChans = physicalChannels_.size();
   size_t samplesPerChan;
   int err = GetLCMSamplesPerChannel(samplesPerChan);
   if (err != DEVICE_OK)
      return err;

   LogMessage(boost::lexical_cast<std::string>(numChans) + " channels", true);
   LogMessage("LCM sequence length = " +
      boost::lexical_cast<std::string>(samplesPerChan), true);

   int32 ret = DAQmxCreateTask("AOSeqTask", &task_);
   if (ret != 0)
      return ret;
   LogMessage("Created task", true);

   const std::string chanList = GetPhysicalChannelListForSequencing();
   ret = DAQmxCreateAOVoltageChan(task_, chanList.c_str(),
      "AOSeqChan", minVolts_, maxVolts_, DAQmx_Val_Volts,
      NULL);
   if (ret != 0)
      goto error;
   LogMessage(("Created AO voltage channel for: " + chanList).c_str(), true);

   ret = DAQmxCfgSampClkTiming(task_, niTriggerPort_.c_str(),
      sampleRateHz_, DAQmx_Val_Rising,
      DAQmx_Val_ContSamps, samplesPerChan);
   if (ret != 0)
      goto error;
   LogMessage("Configured sample clock timing to use " + niTriggerPort_, true);

   samples.reset(new float64[samplesPerChan * numChans]);
   GetLCMSequence(samples.get());

   int32 numWritten = 0;
   ret = DAQmxWriteAnalogF64(task_, static_cast<int32>(samplesPerChan),
      false, DAQmx_Val_WaitInfinitely, DAQmx_Val_GroupByChannel,
      samples.get(), &numWritten, NULL);
   if (ret != 0)
      goto error;
   if (numWritten != samplesPerChan)
   {
      LogMessage("Failed to write complete sequence");
      ret = DEVICE_ERR; // This is presumably unlikely
      goto error;
   }
   LogMessage("Wrote samples", true);

   ret = DAQmxStartTask(task_);
   if (ret != 0)
      goto error;
   LogMessage("Started task", true);

   return DEVICE_OK;

error:
   DAQmxClearTask(task_);
   task_ = 0;
   LogMessage(("Failed with error " + boost::lexical_cast<std::string>(ret) +
         "; task cleared").c_str(), true);
   return ret;
}


int MultiAnalogOutHub::StopTask()
{
   if (!task_)
      return DEVICE_OK;

   int32 ret = DAQmxClearTask(task_);
   if (ret != 0)
      return ret;
   task_ = 0;
   LogMessage("Stopped task", true);

   return DEVICE_OK;
}


int MultiAnalogOutHub::OnDevice(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(niDeviceName_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      std::string deviceName;
      pProp->Get(deviceName);
      niDeviceName_ = deviceName;
   }
   return DEVICE_OK;
}


int MultiAnalogOutHub::OnMaxSequenceLength(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(static_cast<long>(maxSequenceLength_));
   }
   else if (eAct == MM::AfterSet)
   {
      long maxLength;
      pProp->Get(maxLength);
      if (maxLength < 0)
      {
         maxLength = 0;
         pProp->Set(maxLength);
      }
      maxSequenceLength_ = maxLength;
   }
   return DEVICE_OK;
}


int MultiAnalogOutHub::OnSequencingEnabled(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(sequencingEnabled_ ? g_On : g_Off);
   }
   else if (eAct == MM::AfterSet)
   {
      std::string sw;
      pProp->Get(sw);
      sequencingEnabled_ = (sw == g_On);
   }
   return DEVICE_OK;
}


int MultiAnalogOutHub::OnTriggerInputPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(niTriggerPort_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      if (task_)
         return ERR_SEQUENCE_RUNNING;

      std::string port;
      pProp->Get(port);
      niTriggerPort_ = port;
   }
   return DEVICE_OK;
}


int MultiAnalogOutHub::OnSampleRate(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(sampleRateHz_);
   }
   else if (eAct == MM::AfterSet)
   {
      if (task_)
         return ERR_SEQUENCE_RUNNING;

      double rateHz;
      pProp->Get(rateHz);
      if (rateHz <= 0.0)
      {
         rateHz = 1.0;
         pProp->Set(rateHz);
      }
      sampleRateHz_ = rateHz;
   }
   return DEVICE_OK;
}


//
// MultiAnalogOutPort
//

MultiAnalogOutPort::MultiAnalogOutPort(const std::string& port) :
   niPort_(port),
   initialized_(false),
   gateOpen_(true),
   gatedVoltage_(0.0),
   sequenceRunning_(false),
   minVolts_(0.0),
   maxVolts_(5.0),
   neverSequenceable_(false),
   task_(0)
{
   CPropertyAction* pAct = new CPropertyAction(this, &MultiAnalogOutPort::OnMinVolts);
   CreateFloatProperty("MinVolts", minVolts_, false, pAct, true);
   pAct = new CPropertyAction(this, &MultiAnalogOutPort::OnMaxVolts);
   CreateFloatProperty("MaxVolts", maxVolts_, false, pAct, true);

   pAct = new CPropertyAction(this, &MultiAnalogOutPort::OnSequenceable);
   CreateStringProperty("Sequencing", g_UseHubSetting, false, pAct, true);
   AddAllowedValue("Sequencing", g_UseHubSetting);
   AddAllowedValue("Sequencing", g_Never);
}


MultiAnalogOutPort::~MultiAnalogOutPort()
{
   Shutdown();
}


int MultiAnalogOutPort::Initialize()
{
   if (initialized_)
      return DEVICE_OK;

   // Check that the voltage range is allowed (since we cannot
   // enforce this when creating pre-init properties)
   double minVolts, maxVolts;
   int ret = GetAOHub()->GetVoltageLimits(minVolts, maxVolts);
   if (ret != DEVICE_OK)
      return ret;
   LogMessage("Device voltage limits: " + boost::lexical_cast<std::string>(minVolts) +
      " to " + boost::lexical_cast<std::string>(maxVolts), true);
   if (minVolts_ < minVolts || maxVolts_ > maxVolts)
      return ERR_VOLTAGE_RANGE_EXCEEDS_DEVICE_LIMITS;

   CPropertyAction* pAct = new CPropertyAction(this, &MultiAnalogOutPort::OnVoltage);
   ret = CreateFloatProperty("Voltage", gatedVoltage_, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   ret = SetPropertyLimits("Voltage", minVolts_, maxVolts_);
   if (ret != DEVICE_OK)
      return ret;

   ret = StartOnDemandTask(gateOpen_ ? gatedVoltage_ : 0.0);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}


int MultiAnalogOutPort::Shutdown()
{
   if (!initialized_)
      return DEVICE_OK;

   int ret = StopTask();

   unsentSequence_.clear();
   sentSequence_.clear();

   initialized_ = false;
   return ret;
}


void MultiAnalogOutPort::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name,
      (g_DeviceNameMultiAnalogOutPortPrefix + niPort_).c_str());
}


int MultiAnalogOutPort::SetGateOpen(bool open)
{
   if (open && !gateOpen_)
   {
      int ret = StartOnDemandTask(gatedVoltage_);
      if (ret != DEVICE_OK)
         return ret;
   }
   else if (!open && gateOpen_)
   {
      int ret = StartOnDemandTask(0.0);
      if (ret != DEVICE_OK)
         return ret;
   }

   gateOpen_ = open;
   return DEVICE_OK;
}


int MultiAnalogOutPort::GetGateOpen(bool& open)
{
   open = gateOpen_;
   return DEVICE_OK;
}


int MultiAnalogOutPort::SetSignal(double volts)
{
   if (volts < minVolts_ || volts > maxVolts_)
      return ERR_VOLTAGE_OUT_OF_RANGE;

   gatedVoltage_ = volts;
   if (gateOpen_)
   {
      int ret = StartOnDemandTask(gatedVoltage_);
      if (ret != DEVICE_OK)
         return ret;
   }
   return DEVICE_OK;
}


int MultiAnalogOutPort::GetLimits(double& minVolts, double& maxVolts)
{
   minVolts = minVolts_;
   maxVolts = maxVolts_;
   return DEVICE_OK;
}


int MultiAnalogOutPort::IsDASequenceable(bool& isSequenceable) const
{
   if (neverSequenceable_)
      return false;

   return GetAOHub()->IsSequencingEnabled(isSequenceable);
}


int MultiAnalogOutPort::GetDASequenceMaxLength(long& maxLength) const
{
   return GetAOHub()->GetSequenceMaxLength(maxLength);
}


int MultiAnalogOutPort::StartDASequence()
{
   if (task_)
      StopTask();

   sequenceRunning_ = true;

   int ret = GetAOHub()->StartSequenceForPort(niPort_, sentSequence_);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}


int MultiAnalogOutPort::StopDASequence()
{
   int ret = GetAOHub()->StopSequenceForPort(niPort_);
   if (ret != DEVICE_OK)
      return ret;

   sequenceRunning_ = false;

   // Recover voltage from before sequencing started, so that we
   // are back in sync
   ret = StartOnDemandTask(gateOpen_ ? gatedVoltage_ : 0.0);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}


int MultiAnalogOutPort::ClearDASequence()
{
   unsentSequence_.clear();
   return DEVICE_OK;
}


int MultiAnalogOutPort::AddToDASequence(double voltage)
{
   if (voltage < minVolts_ || voltage > maxVolts_)
      return ERR_VOLTAGE_OUT_OF_RANGE;

   unsentSequence_.push_back(voltage);
   return DEVICE_OK;
}


int MultiAnalogOutPort::SendDASequence()
{
   if (sequenceRunning_)
      return ERR_SEQUENCE_RUNNING;

   sentSequence_ = unsentSequence_;
   // We don't actually "write" the sequence here, because writing
   // needs to take place once the correct task has been set up for
   // all of the AO channels.
   return DEVICE_OK;
}


int MultiAnalogOutPort::OnMinVolts(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(minVolts_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(minVolts_);
   }
   return DEVICE_OK;
}


int MultiAnalogOutPort::OnMaxVolts(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(maxVolts_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(maxVolts_);
   }
   return DEVICE_OK;
}


int MultiAnalogOutPort::OnSequenceable(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(neverSequenceable_ ? g_Never : g_UseHubSetting);
   }
   else if (eAct == MM::AfterSet)
   {
      std::string s;
      pProp->Get(s);
      neverSequenceable_ = (s == g_Never);
   }
   return DEVICE_OK;
}


int MultiAnalogOutPort::OnVoltage(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(gatedVoltage_);
   }
   else if (eAct == MM::AfterSet)
   {
      double voltage;
      pProp->Get(voltage);
      int ret = SetSignal(voltage);
      if (ret != DEVICE_OK)
         return ret;
   }
   return DEVICE_OK;
}


int MultiAnalogOutPort::StartOnDemandTask(double voltage)
{
   if (sequenceRunning_)
      return ERR_SEQUENCE_RUNNING;

   if (task_)
   {
      int err = StopTask();
      if (err != DEVICE_OK)
         return err;
   }

   LogMessage("Starting on-demand task", true);

   int32 ret = DAQmxCreateTask(NULL, &task_);
   if (ret != 0)
      return ret;
   LogMessage("Created task", true);

   ret = DAQmxCreateAOVoltageChan(task_,
      niPort_.c_str(), NULL, minVolts_, maxVolts_,
      DAQmx_Val_Volts, NULL);
   if (ret != 0)
      goto error;
   LogMessage("Created AO voltage channel", true);

   float64 samples[1];
   samples[0] = voltage;
   int32 numWritten = 0;
   ret = DAQmxWriteAnalogF64(task_, 1,
      true, DAQmx_Val_WaitInfinitely, DAQmx_Val_GroupByChannel,
      samples, &numWritten, NULL);
   if (ret != 0)
      goto error;
   if (numWritten != 1)
   {
      LogMessage("Failed to write voltage");
      ret = DEVICE_ERR; // This is presumably unlikely
      goto error;
   }
   LogMessage(("Wrote voltage with task autostart: " +
         boost::lexical_cast<std::string>(voltage) + " V").c_str(), true);

   return DEVICE_OK;

error:
   DAQmxClearTask(task_);
   task_ = 0;
   LogMessage(("Failed with error " + boost::lexical_cast<std::string>(ret) +
         "; task cleared").c_str(), true);
   return ret;
}


int MultiAnalogOutPort::StopTask()
{
   if (!task_)
      return DEVICE_OK;

   int32 ret = DAQmxClearTask(task_);
   if (ret != 0)
      return ret;
   task_ = 0;
   LogMessage("Stopped task", true);

   return DEVICE_OK;
}
