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

#include "LoggedSetting.h"

#include "SequenceTester.h" // For InterDevice; TODO make separate header

#include <boost/utility.hpp>
#include <algorithm>
#include <string>


LoggedSetting::LoggedSetting(SettingLogger* logger,
      InterDevice* device, const std::string& name) :
   logger_(logger),
   device_(device),
   name_(name)
{
}


void
LoggedSetting::MarkBusy()
{
   if (busySetting_)
      busySetting_->Set();
}


BoolSetting::BoolSetting(SettingLogger* logger,
      InterDevice* device, const std::string& name,
      bool initialValue) :
   LoggedSetting(logger, device, name)
{
   GetLogger()->SetBool(GetDevice()->GetDeviceName(), GetName(),
         initialValue, false);
}


int
BoolSetting::Set(bool newValue)
{
   GetLogger()->SetBool(GetDevice()->GetDeviceName(), GetName(), newValue);
   return DEVICE_OK;
}


int
BoolSetting::Get(bool& value) const
{
   value = Get();
   return DEVICE_OK;
}


bool
BoolSetting::Get() const
{
   return GetLogger()->GetBool(GetDevice()->GetDeviceName(), GetName());
}


MM::ActionFunctor*
BoolSetting::NewPropertyAction(PropertyDisplay displayMode)
{
   class Functor : public MM::ActionFunctor, boost::noncopyable
   {
      BoolSetting& setting_;
      PropertyDisplay displayMode_;

   public:
      Functor(BoolSetting& setting, PropertyDisplay displayMode) :
         setting_(setting),
         displayMode_(displayMode)
      {}

      virtual int Execute(MM::PropertyBase* pProp, MM::ActionType eAct)
      {
         if (eAct == MM::BeforeGet)
         {
            bool v;
            int err = setting_.Get(v);
            if (err != DEVICE_OK)
               return err;
            switch (displayMode_)
            {
               case ON_OFF:
                  pProp->Set(v ? "On" : "Off");
                  break;
               case YES_NO:
                  pProp->Set(v ? "Yes" : "No");
                  break;
               case ONE_ZERO:
                  pProp->Set(v ? 1L : 0L);
                  break;
            }
            return DEVICE_OK;
         }
         else if (eAct == MM::AfterSet)
         {
            setting_.MarkBusy();
            std::string strVal;
            long intVal;
            switch (displayMode_)
            {
               case ON_OFF:
                  pProp->Get(strVal);
                  return setting_.Set(strVal == "On");
               case YES_NO:
                  pProp->Get(strVal);
                  return setting_.Set(strVal == "Yes");
               case ONE_ZERO:
                  pProp->Get(intVal);
                  return setting_.Set(intVal != 0);
            }
         }
         return DEVICE_OK;
      }
   };

   return new Functor(*this, displayMode);
}


IntegerSetting::IntegerSetting(SettingLogger* logger,
      InterDevice* device, const std::string& name,
      long initialValue, bool hasMinMax, long minimum, long maximum) :
   LoggedSetting(logger, device, name),
   hasMinMax_(hasMinMax),
   min_(minimum),
   max_(maximum)
{
   GetLogger()->SetInteger(GetDevice()->GetDeviceName(), GetName(),
         initialValue, false);
}


int
IntegerSetting::Set(long newValue)
{
   GetLogger()->SetInteger(GetDevice()->GetDeviceName(), GetName(), newValue);
   return DEVICE_OK;
}


int
IntegerSetting::Get(long& value) const
{
   value = Get();
   return DEVICE_OK;
}


long
IntegerSetting::Get() const
{
   return GetLogger()->GetInteger(GetDevice()->GetDeviceName(), GetName());
}


MM::ActionFunctor*
IntegerSetting::NewPropertyAction()
{
   class Functor : public MM::ActionFunctor, boost::noncopyable
   {
      IntegerSetting& setting_;

   public:
      Functor(IntegerSetting& setting) : setting_(setting) {}

      virtual int Execute(MM::PropertyBase* pProp, MM::ActionType eAct)
      {
         if (eAct == MM::BeforeGet)
         {
            long v;
            int err = setting_.Get(v);
            if (err != DEVICE_OK)
               return err;
            pProp->Set(v);
            return DEVICE_OK;
         }
         else if (eAct == MM::AfterSet)
         {
            setting_.MarkBusy();
            long v;
            pProp->Get(v);
            return setting_.Set(v);
         }
         return DEVICE_OK;
      }
   };

   return new Functor(*this);
}


FloatSetting::FloatSetting(SettingLogger* logger,
      InterDevice* device, const std::string& name,
      double initialValue, bool hasMinMax, double minimum, double maximum) :
   LoggedSetting(logger, device, name),
   hasMinMax_(hasMinMax),
   min_(minimum),
   max_(maximum)
{
   GetLogger()->SetFloat(GetDevice()->GetDeviceName(), GetName(),
         initialValue, false);
}


int
FloatSetting::Set(double newValue)
{
   GetLogger()->SetFloat(GetDevice()->GetDeviceName(), GetName(), newValue);
   return DEVICE_OK;
}


int
FloatSetting::Get(double& value) const
{
   value = Get();
   return DEVICE_OK;
}


double
FloatSetting::Get() const
{
   return GetLogger()->GetFloat(GetDevice()->GetDeviceName(), GetName());
}


MM::ActionFunctor*
FloatSetting::NewPropertyAction()
{
   class Functor : public MM::ActionFunctor, boost::noncopyable
   {
      FloatSetting& setting_;

   public:
      Functor(FloatSetting& setting) : setting_(setting) {}

      virtual int Execute(MM::PropertyBase* pProp, MM::ActionType eAct)
      {
         if (eAct == MM::BeforeGet)
         {
            double v;
            int err = setting_.Get(v);
            if (err != DEVICE_OK)
               return err;
            pProp->Set(v);
            return DEVICE_OK;
         }
         else if (eAct == MM::AfterSet)
         {
            setting_.MarkBusy();
            double v;
            pProp->Get(v);
            return setting_.Set(v);
         }
         return DEVICE_OK;
      }
   };

   return new Functor(*this);
}


StringSetting::StringSetting(SettingLogger* logger, InterDevice* device,
      const std::string& name, const std::string& initialValue) :
   LoggedSetting(logger, device, name)
{
   GetLogger()->SetString(GetDevice()->GetDeviceName(), GetName(),
         initialValue, false);
}


int
StringSetting::Set(const std::string& newValue)
{
   GetLogger()->SetString(GetDevice()->GetDeviceName(), GetName(), newValue);
   return DEVICE_OK;
}


int
StringSetting::Get(std::string& value) const
{
   value = Get();
   return DEVICE_OK;
}


std::string
StringSetting::Get() const
{
   return GetLogger()->GetString(GetDevice()->GetDeviceName(), GetName());
}


MM::ActionFunctor*
StringSetting::NewPropertyAction()
{
   class Functor : public MM::ActionFunctor, boost::noncopyable
   {
      StringSetting& setting_;

   public:
      Functor(StringSetting& setting) : setting_(setting) {}

      virtual int Execute(MM::PropertyBase* pProp, MM::ActionType eAct)
      {
         if (eAct == MM::BeforeGet)
         {
            std::string v;
            int err = setting_.Get(v);
            if (err != DEVICE_OK)
               return err;
            pProp->Set(v.c_str());
            return DEVICE_OK;
         }
         else if (eAct == MM::AfterSet)
         {
            setting_.MarkBusy();
            std::string v;
            pProp->Get(v);
            return setting_.Set(v);
         }
         return DEVICE_OK;
      }
   };

   return new Functor(*this);
}


OneShotSetting::OneShotSetting(SettingLogger* logger,
      InterDevice* device, const std::string& name) :
   LoggedSetting(logger, device, name)
{
   GetLogger()->FireOneShot(GetDevice()->GetDeviceName(), GetName(), false);
}


int
OneShotSetting::Set()
{
   GetLogger()->FireOneShot(GetDevice()->GetDeviceName(), GetName());
   return DEVICE_OK;
}


CountDownSetting::CountDownSetting(SettingLogger* logger,
      InterDevice* device, const std::string& name, long initialCount,
      long defaultIncrement) :
   LoggedSetting(logger, device, name),
   defaultIncrement_(defaultIncrement)
{
   GetLogger()->SetInteger(GetDevice()->GetDeviceName(), GetName(),
         initialCount, false);
}


int
CountDownSetting::Set(long increment)
{
   long oldCount =
      GetLogger()->GetInteger(GetDevice()->GetDeviceName(), GetName());
   long newCount = oldCount + increment;
   GetLogger()->SetInteger(GetDevice()->GetDeviceName(), GetName(), newCount);
   return DEVICE_OK;
}


int
CountDownSetting::Get(long& value)
{
   value = Get();
   return DEVICE_OK;
}


long CountDownSetting::Get()
{
   long count =
      GetLogger()->GetInteger(GetDevice()->GetDeviceName(), GetName());
   if (count > 0)
   {
      GetLogger()->SetInteger(GetDevice()->GetDeviceName(), GetName(),
            count - 1);
   }
   // Return the value _before_ the decrement. Otherwise a unit increment would
   // have no effect.
   return count;
}
