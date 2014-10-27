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

#include "LoggedSetting.h"

#include <boost/utility.hpp>
#include <string>


template <class TDevice>
LoggedSetting<TDevice>::LoggedSetting(SettingLogger* logger,
      TDevice* device, const std::string& name) :
   logger_(logger),
   device_(device),
   name_(name)
{
}


template <class TDevice>
BoolSetting<TDevice>::BoolSetting(SettingLogger* logger,
      TDevice* device, const std::string& name,
      bool initialValue) :
   LoggedSetting<TDevice>(logger, device, name)
{
   Super::GetLogger()->SetBool(Super::GetDevice()->GetName(),
         Super::GetName(), initialValue, false);
}


template <class TDevice>
int
BoolSetting<TDevice>::Set(bool newValue)
{
   Super::GetLogger()->SetBool(Super::GetDevice()->GetName(),
         Super::GetName(), newValue);
   return DEVICE_OK;
}


template <class TDevice>
int
BoolSetting<TDevice>::Get(bool& value) const
{
   value = Get();
   return DEVICE_OK;
}


template <class TDevice>
bool
BoolSetting<TDevice>::Get() const
{
   return Super::GetLogger()->GetBool(Super::GetDevice()->GetName(),
         Super::GetName());
}


template <class TDevice>
MM::ActionFunctor*
BoolSetting<TDevice>::NewPropertyAction(PropertyDisplay displayMode)
{
   class Functor : public MM::ActionFunctor, boost::noncopyable
   {
      BoolSetting<TDevice>& setting_;
      PropertyDisplay displayMode_;

   public:
      Functor(BoolSetting<TDevice>& setting, PropertyDisplay displayMode) :
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


template <class TDevice>
IntegerSetting<TDevice>::IntegerSetting(SettingLogger* logger,
      TDevice* device, const std::string& name,
      long initialValue, bool hasMinMax, long minimum, long maximum) :
   LoggedSetting<TDevice>(logger, device, name),
   hasMinMax_(hasMinMax),
   min_(minimum),
   max_(maximum)
{
   Super::GetLogger()->SetInteger(Super::GetDevice()->GetName(),
         Super::GetName(), initialValue, false);
}


template <class TDevice>
int
IntegerSetting<TDevice>::Set(long newValue)
{
   Super::GetLogger()->SetInteger(Super::GetDevice()->GetName(),
         Super::GetName(), newValue);
   return DEVICE_OK;
}


template <class TDevice>
int
IntegerSetting<TDevice>::Get(long& value) const
{
   value = Get();
   return DEVICE_OK;
}


template <class TDevice>
long
IntegerSetting<TDevice>::Get() const
{
   return Super::GetLogger()->GetInteger(Super::GetDevice()->GetName(),
         Super::GetName());
}


template <class TDevice>
MM::ActionFunctor*
IntegerSetting<TDevice>::NewPropertyAction()
{
   class Functor : public MM::ActionFunctor, boost::noncopyable
   {
      IntegerSetting<TDevice>& setting_;

   public:
      Functor(IntegerSetting<TDevice>& setting) : setting_(setting) {}

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
            long v;
            pProp->Get(v);
            return setting_.Set(v);
         }
         return DEVICE_OK;
      }
   };

   return new Functor(*this);
}


template <class TDevice>
FloatSetting<TDevice>::FloatSetting(SettingLogger* logger,
      TDevice* device, const std::string& name,
      double initialValue, bool hasMinMax, double minimum, double maximum) :
   LoggedSetting<TDevice>(logger, device, name),
   hasMinMax_(hasMinMax),
   min_(minimum),
   max_(maximum)
{
   Super::GetLogger()->SetFloat(Super::GetDevice()->GetName(),
         Super::GetName(), initialValue, false);
}


template <class TDevice>
int
FloatSetting<TDevice>::Set(double newValue)
{
   Super::GetLogger()->SetFloat(Super::GetDevice()->GetName(),
         Super::GetName(), newValue);
   return DEVICE_OK;
}


template <class TDevice>
int
FloatSetting<TDevice>::Get(double& value) const
{
   value = Get();
   return DEVICE_OK;
}


template <class TDevice>
double
FloatSetting<TDevice>::Get() const
{
   return Super::GetLogger()->GetFloat(Super::GetDevice()->GetName(),
         Super::GetName());
}


template <class TDevice>
MM::ActionFunctor*
FloatSetting<TDevice>::NewPropertyAction()
{
   class Functor : public MM::ActionFunctor, boost::noncopyable
   {
      FloatSetting<TDevice>& setting_;

   public:
      Functor(FloatSetting<TDevice>& setting) : setting_(setting) {}

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
            double v;
            pProp->Get(v);
            return setting_.Set(v);
         }
         return DEVICE_OK;
      }
   };

   return new Functor(*this);
}


template <class TDevice>
OneShotSetting<TDevice>::OneShotSetting(SettingLogger* logger,
      TDevice* device, const std::string& name) :
   LoggedSetting<TDevice>(logger, device, name)
{
   Super::GetLogger()->FireOneShot(Super::GetDevice()->GetName(),
         Super::GetName(), false);
}


template <class TDevice>
int
OneShotSetting<TDevice>::Set()
{
   Super::GetLogger()->FireOneShot(Super::GetDevice()->GetName(),
         Super::GetName());
   return DEVICE_OK;
}
