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

#pragma once

#include "DeviceBase.h"

#include "NIDAQmx.h"

#include <boost/lexical_cast.hpp>
#include <boost/utility.hpp>

#include <string>
#include <vector>


// A hub-peripheral device set for driving multiple analog output ports,
// possibly with hardware-triggered sequencing using a shared trigger input.
class MultiAnalogOutHub : public HubBase<MultiAnalogOutHub>,
   boost::noncopyable
{
public:
   MultiAnalogOutHub();
   virtual ~MultiAnalogOutHub();

   virtual int Initialize();
   virtual int Shutdown();

   virtual void GetName(char* name) const;
   virtual bool Busy() { return false; }

   virtual int DetectInstalledDevices();

public:
   virtual int GetVoltageLimits(double& minVolts, double& maxVolts);

   virtual int StartSequenceForPort(const std::string& port,
      const std::vector<double> sequence);
   virtual int StopSequenceForPort(const std::string& port);

   virtual int AddPortToSequencing(const std::string& port,
      const std::vector<double> sequence);
   virtual void RemovePortFromSequencing(const std::string& port);

   virtual int IsSequencingEnabled(bool& flag) const;
   virtual int GetSequenceMaxLength(long& maxLength) const;

private:
   std::string GetPhysicalChannelListForSequencing() const;
   int GetLCMSamplesPerChannel(size_t& seqLen) const;
   void GetLCMSequence(double* buffer) const;

   int StartSequencingTask();
   int StopTask();

private:
   // Action handlers
   int OnDevice(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnMaxSequenceLength(MM::PropertyBase* pProp, MM::ActionType eAct);

   int OnSequencingEnabled(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTriggerInputPort(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSampleRate(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   bool initialized_;
   size_t maxSequenceLength_;
   bool sequencingEnabled_;

   std::string niDeviceName_;
   std::string niTriggerPort_;
   std::vector<std::string> niAnalogOutputPorts_;

   double minVolts_; // Min possible for device
   double maxVolts_; // Max possible for device
   double sampleRateHz_;

   TaskHandle task_;

   // "Loaded" sequences for each channel
   // Invariant: physicalChannels_.size() == channelSequences_.size()
   std::vector<std::string> physicalChannels_; // Invariant: all unique
   std::vector< std::vector<double> > channelSequences_;
};


class MultiAnalogOutPort : public CSignalIOBase<MultiAnalogOutPort>,
   boost::noncopyable
{
public:
   MultiAnalogOutPort(const std::string& port);
   virtual ~MultiAnalogOutPort();

   virtual int Initialize();
   virtual int Shutdown();

   virtual void GetName(char* name) const;
   virtual bool Busy() { return false; }

   virtual int SetGateOpen(bool open);
   virtual int GetGateOpen(bool& open);
   virtual int SetSignal(double volts);
   virtual int GetSignal(double& /* volts */) { return DEVICE_UNSUPPORTED_COMMAND; }
   virtual int GetLimits(double& minVolts, double& maxVolts);

   virtual int IsDASequenceable(bool& isSequenceable) const;
   virtual int GetDASequenceMaxLength(long& maxLength) const;
   virtual int StartDASequence();
   virtual int StopDASequence();
   virtual int ClearDASequence();
   virtual int AddToDASequence(double);
   virtual int SendDASequence();

private:
   // Pre-init property action handlers
   int OnMinVolts(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnMaxVolts(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSequenceable(MM::PropertyBase* pProp, MM::ActionType eAct);

   // Post-init property action handlers
   int OnVoltage(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   MultiAnalogOutHub* GetAOHub() const
   { return static_cast<MultiAnalogOutHub*>(GetParentHub()); }
   int StartOnDemandTask(double voltage);
   int StopTask();

private:
   const std::string niPort_;

   bool initialized_;

   bool gateOpen_;
   double gatedVoltage_;
   bool sequenceRunning_;

   double minVolts_; // User-selected for this port
   double maxVolts_; // User-selected for this port
   bool neverSequenceable_;

   TaskHandle task_;

   std::vector<double> unsentSequence_;
   std::vector<double> sentSequence_; // Pretend "sent" to device
};
