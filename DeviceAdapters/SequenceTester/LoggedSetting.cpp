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
#include <string>


LoggedSetting::LoggedSetting(SettingLogger* logger,
      InterDevice* device, const std::string& name) :
   logger_(logger),
   device_(device),
   name_(name)
{
}


void
LoggedSetting::MarkDeviceBusy()
{
   logger_->MarkBusy(device_->GetDeviceName());
}


BoolSetting::BoolSetting(SettingLogger* logger,
      InterDevice* device, const std::string& name,
      bool initialValue) :
   LoggedSetting(logger, device, name)
{
   Super::GetLogger()->SetBool(Super::GetDevice()->GetDeviceName(),
         Super::GetName(), initialValue, false);
}


int
BoolSetting::Set(bool newValue)
{
   Super::GetLogger()->SetBool(Super::GetDevice()->GetDeviceName(),
         Super::GetName(), newValue);
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
   return Super::GetLogger()->GetBool(Super::GetDevice()->GetDeviceName(),
         Super::GetName());
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
            setting_.MarkDeviceBusy();
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
   Super::GetLogger()->SetInteger(Super::GetDevice()->GetDeviceName(),
         Super::GetName(), initialValue, false);
}


int
IntegerSetting::Set(long newValue)
{
   Super::GetLogger()->SetInteger(Super::GetDevice()->GetDeviceName(),
         Super::GetName(), newValue);
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
   return Super::GetLogger()->GetInteger(Super::GetDevice()->GetDeviceName(),
         Super::GetName());
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
            setting_.MarkDeviceBusy();
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
   Super::GetLogger()->SetFloat(Super::GetDevice()->GetDeviceName(),
         Super::GetName(), initialValue, false);
}


int
FloatSetting::Set(double newValue)
{
   Super::GetLogger()->SetFloat(Super::GetDevice()->GetDeviceName(),
         Super::GetName(), newValue);
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
   return Super::GetLogger()->GetFloat(Super::GetDevice()->GetDeviceName(),
         Super::GetName());
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
            setting_.MarkDeviceBusy();
            double v;
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
   Super::GetLogger()->FireOneShot(Super::GetDevice()->GetDeviceName(),
         Super::GetName(), false);
}


int
OneShotSetting::Set()
{
   Super::GetLogger()->FireOneShot(Super::GetDevice()->GetDeviceName(),
         Super::GetName());
   return DEVICE_OK;
}
