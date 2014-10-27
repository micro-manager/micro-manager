// Mock device adapter for testing of device sequencing
//
// Copyright (C) 2014 University of California, San Francisco.
//
// This library is free software; you can redistribute it and/or modify it
// under the terms of the GNU Lesser General Public License as published by the
// Free Software Foundation.
//
// This library is distributed in the hope that it will be useful, but WITHOUT
// ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
// FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
// for more details.
//
// IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY
// DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
// You should have received a copy of the GNU Lesser General Public License
// along with this library; if not, write to the Free Software Foundation,
// Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
//
//
// Author: Mark Tsuchida

#pragma once

#include "DeviceBase.h"

#include <boost/enable_shared_from_this.hpp>
#include <boost/make_shared.hpp>
#include <boost/shared_ptr.hpp>
#include <boost/signals2.hpp>
#include <string>
#include <vector>


class InterDevice;
class SettingLogger;

class CountDownSetting;
class IntegerSetting;


// Trigger signals can be received by connected (subscribed) settings.
// The signal is typically owned and invoked (published) by devices (such as
// cameras). I like the "publish"-"subscribe" terminology better, but since
// we're using the Boost.Signals2 library, we'll standardize to "signals" and
// "slots" terminology.
typedef boost::signals2::signal<void ()> EdgeTriggerSignal;
// We could add level-trigger support in the future, but it is not very
// important for current tests as we do not typically control it from software.


// A "setting" in this device adapter is a property or a property-like entity
// (e.g. camera exposure, stage position).
class LoggedSetting : public boost::enable_shared_from_this<LoggedSetting>
{
   typedef LoggedSetting Self;

   SettingLogger* logger_;
   InterDevice* device_;
   const std::string name_;

   boost::shared_ptr<CountDownSetting> busySetting_;

   boost::signals2::signal<void ()> postSetSignal_;

   // Zero is interpreted as triggering disabled
   boost::shared_ptr<IntegerSetting> sequenceMaxLengthSetting_;

   boost::signals2::connection edgeTriggerConnection_;

   void ReceiveEdgeTrigger();

protected:
   SettingLogger* GetLogger() { return logger_; }
   const SettingLogger* GetLogger() const { return logger_; }
   InterDevice* GetDevice() { return device_; }
   const InterDevice* GetDevice() const { return device_; }
   std::string GetName() const { return name_; }

   void FirePostSetSignal() { postSetSignal_(); }

public:
   typedef boost::shared_ptr<Self> Ptr;
   typedef boost::shared_ptr<const Self> ConstPtr;

   LoggedSetting(SettingLogger* logger, InterDevice* device,
         const std::string& name);
   virtual ~LoggedSetting() {}

   void SetBusySetting(boost::shared_ptr<CountDownSetting> setting)
   { busySetting_ = setting; }
   void MarkBusy();

   typedef boost::signals2::signal<void ()> PostSetSignal;
   PostSetSignal& GetPostSetSignal() { return postSetSignal_; }

   void SetSequenceMaxLengthSetting(boost::shared_ptr<IntegerSetting> setting)
   { sequenceMaxLengthSetting_ = setting; }
   int GetSequenceMaxLength(long& len) const;
   long GetSequenceMaxLength() const;

   // Virtual hardware connections
   void ConnectToEdgeTriggerSource(EdgeTriggerSignal& source);
   void DisconnectEdgeTriggerSource();

   virtual int StartTriggerSequence() { return DEVICE_UNSUPPORTED_COMMAND; }
   virtual int StopTriggerSequence() { return DEVICE_UNSUPPORTED_COMMAND; }

   // Called when the trigger is received _and_ triggering is enabled (sequence
   // max length setting is > 0), but regardless of whether a trigger sequence
   // is running.
   virtual void HandleEdgeTrigger() {}
};


class BoolSetting : public LoggedSetting
{
   typedef BoolSetting Self;

   std::vector<uint8_t> triggerSequence_;
   bool sequenceRunning_;
   size_t nextTriggerIndex_;

public:
   typedef boost::shared_ptr<Self> Ptr;
   typedef boost::shared_ptr<const Self> ConstPtr;

   BoolSetting(SettingLogger* logger, InterDevice* device,
         const std::string& name, bool initialValue);

   static Ptr New(SettingLogger* logger, InterDevice* device,
         const std::string& name, bool initialValue)
   { return boost::make_shared<Self>(logger, device, name, initialValue); }

   int Set(bool newValue);
   int Get(bool& value) const;
   bool Get() const;

   int SetTriggerSequence(const std::vector<uint8_t>& sequence);
   virtual int StartTriggerSequence();
   virtual int StopTriggerSequence();
   virtual void HandleEdgeTrigger();

   enum PropertyDisplay
   {
      ON_OFF,
      YES_NO,
      ONE_ZERO, // Not nice, but used e.g. for shutter state
   };
   MM::ActionFunctor* NewPropertyAction(PropertyDisplay displayMode);
};


class IntegerSetting : public LoggedSetting
{
   bool hasMinMax_;
   long min_;
   long max_;

   std::vector<long> triggerSequence_;
   bool sequenceRunning_;
   size_t nextTriggerIndex_;

   typedef IntegerSetting Self;

public:
   typedef boost::shared_ptr<Self> Ptr;
   typedef boost::shared_ptr<const Self> ConstPtr;

   IntegerSetting(SettingLogger* logger, InterDevice* device,
         const std::string& name, long initialValue,
         bool hasMinMax, long minimum, long maximum);

   static Ptr New(SettingLogger* logger, InterDevice* device,
         const std::string& name, long initialValue,
         bool hasMinMax, long minimum = 0, long maximum = 0)
   {
      return boost::make_shared<Self>(logger, device, name, initialValue,
         hasMinMax, minimum, maximum);
   }

   bool HasMinMax() const { return hasMinMax_; }
   long GetMin() const { return min_; }
   long GetMax() const { return max_; }

   int Set(long newValue);
   int Get(long& value) const;
   long Get() const;

   int SetTriggerSequence(const std::vector<long>& sequence);
   virtual int StartTriggerSequence();
   virtual int StopTriggerSequence();
   virtual void HandleEdgeTrigger();

   MM::ActionFunctor* NewPropertyAction();
};


class FloatSetting : public LoggedSetting
{
   bool hasMinMax_;
   double min_;
   double max_;

   std::vector<double> triggerSequence_;
   bool sequenceRunning_;
   size_t nextTriggerIndex_;

   typedef FloatSetting Self;

public:
   typedef boost::shared_ptr<Self> Ptr;
   typedef boost::shared_ptr<const Self> ConstPtr;

   FloatSetting(SettingLogger* logger, InterDevice* device,
         const std::string& name, double initialValue,
         bool hasMinMax, double minimum, double maximum);

   static Ptr New(SettingLogger* logger, InterDevice* device,
         const std::string& name, double initialValue,
         bool hasMinMax, double minimum = 0.0, double maximum = 0.0)
   {
      return boost::make_shared<Self>(logger, device, name, initialValue,
            hasMinMax, minimum, maximum);
   }


   bool HasMinMax() const { return hasMinMax_; }
   double GetMin() const { return min_; }
   double GetMax() const { return max_; }

   int Set(double newValue);
   int Get(double& value) const;
   double Get() const;

   int SetTriggerSequence(const std::vector<double>& sequence);
   virtual int StartTriggerSequence();
   virtual int StopTriggerSequence();
   virtual void HandleEdgeTrigger();

   MM::ActionFunctor* NewPropertyAction();
};


// This is only for free-form string settings. For "allowed values"-style
// enumerated settings, use dedicated class.
class StringSetting : public LoggedSetting
{
   typedef StringSetting Self;

public:
   typedef boost::shared_ptr<Self> Ptr;
   typedef boost::shared_ptr<const Self> ConstPtr;

   StringSetting(SettingLogger* logger, InterDevice* device,
         const std::string& name, const std::string& initialValue);

   static Ptr New(SettingLogger* logger, InterDevice* device,
         const std::string& name, const std::string& initialValue = "")
   { return boost::make_shared<Self>(logger, device, name, initialValue); }

   int Set(const std::string& newValue);
   int Get(std::string& value) const;
   std::string Get() const;

   MM::ActionFunctor* NewPropertyAction();
};


class OneShotSetting : public LoggedSetting
{
   typedef OneShotSetting Self;

public:
   typedef boost::shared_ptr<Self> Ptr;
   typedef boost::shared_ptr<const Self> ConstPtr;

   OneShotSetting(SettingLogger* logger, InterDevice* device,
         const std::string& name);

   static Ptr New(SettingLogger* logger, InterDevice* device,
         const std::string& name)
   { return boost::make_shared<Self>(logger, device, name); }


   int Set();
};


// A setting that stays true until queried the second (or n-th) time.
// Used to simulate Busy() and similar attributes.
class CountDownSetting : public LoggedSetting
{
   typedef CountDownSetting Self;

   long defaultIncrement_;

public:
   typedef boost::shared_ptr<Self> Ptr;
   typedef boost::shared_ptr<const Self> ConstPtr;

   CountDownSetting(SettingLogger* logger, InterDevice* device,
         const std::string& name, long initialCount,
         long defaultIncrement);

   static Ptr New(SettingLogger* logger, InterDevice* device,
         const std::string& name, long initialCount,
         long defaultIncrement = 1)
   {
      return boost::make_shared<Self>(logger, device, name,
            initialCount, defaultIncrement);
   }

   int Set() { return Set(defaultIncrement_); }
   int Set(long increment);
   int Get(long& count);
   long Get();
};
