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

#include <boost/make_shared.hpp>
#include <boost/shared_ptr.hpp>
#include <string>


class InterDevice;
class SettingLogger;
class CountDownSetting;


// A "setting" in this device adapter is a property or a property-like entity
// (e.g. camera exposure, stage position).
class LoggedSetting
{
   SettingLogger* logger_;
   InterDevice* device_;
   const std::string name_;

   boost::shared_ptr<CountDownSetting> busySetting_;

protected:
   SettingLogger* GetLogger() { return logger_; }
   const SettingLogger* GetLogger() const { return logger_; }
   InterDevice* GetDevice() { return device_; }
   const InterDevice* GetDevice() const { return device_; }
   std::string GetName() const { return name_; }

public:
   LoggedSetting(SettingLogger* logger, InterDevice* device,
         const std::string& name);

   void SetBusySetting(boost::shared_ptr<CountDownSetting> setting)
   { busySetting_ = setting; }
   void MarkBusy();
};


class BoolSetting : public LoggedSetting
{
   typedef BoolSetting Self;

public:
   typedef boost::shared_ptr<Self> Ptr;

   BoolSetting(SettingLogger* logger, InterDevice* device,
         const std::string& name, bool initialValue);

   static Ptr New(SettingLogger* logger, InterDevice* device,
         const std::string& name, bool initialValue)
   { return boost::make_shared<Self>(logger, device, name, initialValue); }

   int Set(bool newValue);
   int Get(bool& value) const;
   bool Get() const;

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

   typedef IntegerSetting Self;

public:
   typedef boost::shared_ptr<Self> Ptr;

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
   MM::ActionFunctor* NewPropertyAction();
};


class FloatSetting : public LoggedSetting
{
   bool hasMinMax_;
   double min_;
   double max_;

   typedef FloatSetting Self;

public:
   typedef boost::shared_ptr<Self> Ptr;

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
   MM::ActionFunctor* NewPropertyAction();
};


// This is only for free-form string settings. For "allowed values"-style
// enumerated settings, use dedicated class.
class StringSetting : public LoggedSetting
{
   typedef StringSetting Self;

public:
   typedef boost::shared_ptr<Self> Ptr;

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
