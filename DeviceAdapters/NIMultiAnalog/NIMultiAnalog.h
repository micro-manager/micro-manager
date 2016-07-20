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


// Mix-in class for error code handling.
template <typename TDevice>
class ErrorTranslator
{
protected:
   explicit ErrorTranslator(int minCode, int maxCode,
         void (TDevice::*setCodeFunc)(int, const char*)) :
      minErrorCode_(minCode),
      maxErrorCode_(maxCode),
      nextErrorCode_(minCode),
      setCodeFunc_(setCodeFunc)
   {}

   int NewErrorCode(const std::string& msg)
   {
      if (nextErrorCode_ > maxErrorCode_)
         nextErrorCode_ = minErrorCode_;
      int code = nextErrorCode_++;

      (static_cast<TDevice*>(this)->*setCodeFunc_)(code, msg.c_str());
      return code;
   }

   int TranslateNIError(int32 nierr)
   {
      char buf[1024];
      if (DAQmxGetErrorString(nierr, buf, sizeof(buf)))
         return NewErrorCode("[Cannot get DAQmx error message]");
      return NewErrorCode(buf);
   }

private:
   int minErrorCode_, maxErrorCode_;
   int nextErrorCode_;
   void (TDevice::*setCodeFunc_)(int, const char*);
};


class MultiAnalogOutPort;


// A hub-peripheral device set for driving multiple analog output ports,
// possibly with hardware-triggered sequencing using a shared trigger input.
class MultiAnalogOutHub : public HubBase<MultiAnalogOutHub>,
   ErrorTranslator<MultiAnalogOutHub>,
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

public: // Interface for individual ports
   virtual int GetVoltageLimits(double& minVolts, double& maxVolts);

   virtual int RegisterPort(const std::string& port, MultiAnalogOutPort* ptr);
   virtual int UnregisterPort(const std::string& port);

   virtual int StartSequenceForPort(const std::string& port,
      const std::vector<double> sequence);
   virtual int StopSequenceForPort(const std::string& port);

   virtual int IsSequencingEnabled(bool& flag) const;
   virtual int GetSequenceMaxLength(long& maxLength) const;

private:
   int AddPortToSequencing(const std::string& port,
      const std::vector<double> sequence);
   void RemovePortFromSequencing(const std::string& port);

   int GetVoltageRangeForDevice(const std::string& device,
      double& minVolts, double& maxVolts);
   std::vector<std::string> GetTriggerPortsForDevice(
      const std::string& device);
   std::vector<std::string> GetAnalogPortsForDevice(
      const std::string& device);
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

   double minVolts_; // Min possible for device
   double maxVolts_; // Max possible for device
   double sampleRateHz_;

   TaskHandle task_;

   // "Loaded" sequences for each channel
   // Invariant: physicalChannels_.size() == channelSequences_.size()
   std::vector<std::string> physicalChannels_; // Invariant: all unique
   std::vector< std::vector<double> > channelSequences_;

   // All ports (physical channels) managed by this hub
   // Invariant: allPorts_.size() == portPtrs_.size()
   std::vector<std::string> allPorts_;
   std::vector< MultiAnalogOutPort* > portPtrs_;
};


class MultiAnalogOutPort : public CSignalIOBase<MultiAnalogOutPort>,
   ErrorTranslator<MultiAnalogOutPort>,
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

public: // Interface for hub to access
   virtual double GetNonSequencedVoltage();
   virtual int StartOnDemandTask(double voltage);
   virtual int StopTask();

private:
   MultiAnalogOutHub* GetAOHub() const
   { return static_cast<MultiAnalogOutHub*>(GetParentHub()); }
   int TranslateHubError(int err);

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
