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

#include "SequenceTester.h"

#include "DeviceUtils.h"

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
LoggedSetting<TDevice>::~LoggedSetting()
{
}


template <class TDevice>
LoggedIntegerSetting<TDevice>::LoggedIntegerSetting(SettingLogger* logger,
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
LoggedIntegerSetting<TDevice>::Set(long newValue)
{
   Super::GetLogger()->SetInteger(Super::GetDevice()->GetName(),
         Super::GetName(), newValue);
   return DEVICE_OK;
}


template <class TDevice>
int
LoggedIntegerSetting<TDevice>::Get(long& value) const
{
   value = Get();
   return DEVICE_OK;
}


template <class TDevice>
long
LoggedIntegerSetting<TDevice>::Get() const
{
   return Super::GetLogger()->GetInteger(Super::GetDevice()->GetName(),
         Super::GetName());
}


template <class TDevice>
MM::ActionFunctor*
LoggedIntegerSetting<TDevice>::NewPropertyAction()
{
   class Functor : public MM::ActionFunctor, boost::noncopyable
   {
      LoggedIntegerSetting<TDevice>& setting_;

   public:
      Functor(LoggedIntegerSetting<TDevice>& setting) : setting_(setting) {}

      virtual int Execute(MM::PropertyBase* pProp, MM::ActionType eAct)
      {
         if (eAct == MM::BeforeGet)
         {
            pProp->Set(setting_.Get());
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
LoggedFloatSetting<TDevice>::LoggedFloatSetting(SettingLogger* logger,
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
LoggedFloatSetting<TDevice>::Set(double newValue)
{
   Super::GetLogger()->SetFloat(Super::GetDevice()->GetName(),
         Super::GetName(), newValue);
   return DEVICE_OK;
}


template <class TDevice>
int
LoggedFloatSetting<TDevice>::Get(double& value) const
{
   value = Get();
   return DEVICE_OK;
}


template <class TDevice>
double
LoggedFloatSetting<TDevice>::Get() const
{
   return Super::GetLogger()->GetFloat(Super::GetDevice()->GetName(),
         Super::GetName());
}


template <class TDevice>
MM::ActionFunctor*
LoggedFloatSetting<TDevice>::NewPropertyAction()
{
   class Functor : public MM::ActionFunctor, boost::noncopyable
   {
      LoggedFloatSetting<TDevice>& setting_;

   public:
      Functor(LoggedFloatSetting<TDevice>& setting) : setting_(setting) {}

      virtual int Execute(MM::PropertyBase* pProp, MM::ActionType eAct)
      {
         if (eAct == MM::BeforeGet)
         {
            pProp->Set(setting_.Get());
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


template <template <class> class TDeviceBase, class UConcreteDevice>
TesterBase<TDeviceBase, UConcreteDevice>::TesterBase(const std::string& name) :
   name_(name)
{
}


template <template <class> class TDeviceBase, class UConcreteDevice>
TesterBase<TDeviceBase, UConcreteDevice>::~TesterBase()
{
}


template <template <class> class TDeviceBase, class UConcreteDevice>
void
TesterBase<TDeviceBase, UConcreteDevice>::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}


template <template <class> class TDeviceBase, class UConcreteDevice>
int
TesterBase<TDeviceBase, UConcreteDevice>::Initialize()
{
   return DEVICE_OK;
}


template <template <class> class TDeviceBase, class UConcreteDevice>
int
TesterBase<TDeviceBase, UConcreteDevice>::Shutdown()
{
   return DEVICE_OK;
}


template <template <class> class TDeviceBase, class UConcreteDevice>
bool
TesterBase<TDeviceBase, UConcreteDevice>::Busy()
{
   return GetLogger()->IsBusy(GetName());
}


template <template <class> class TDeviceBase, class UConcreteDevice>
TesterHub*
TesterBase<TDeviceBase, UConcreteDevice>::GetHub()
{
   MM::Hub* hub = Super::GetCoreCallback()->GetParentHub(this);
   if (!hub)
   {
      // It is too much trouble to make this test adapter check for the
      // presence of the hub (and hence the SettingLogger) on every operation.
      // But leave a hint for debugging.
      Super::LogMessage("Hub is missing. Will crash!");
   }
   return static_cast<TesterHub*>(hub);
}


template <template <class> class TDeviceBase, class UConcreteDevice>
void
TesterBase<TDeviceBase, UConcreteDevice>::
CreateIntegerProperty(const std::string& name,
      boost::shared_ptr< LoggedIntegerSetting<UConcreteDevice> > setting)
{
   Super::CreateIntegerProperty(name.c_str(), setting->Get(), false,
         setting->NewPropertyAction());
   if (setting->HasMinMax())
   {
      Super::SetPropertyLimits(name.c_str(),
            setting->GetMin(), setting->GetMax());
   }
}


template <template <class> class TDeviceBase, class UConcreteDevice>
void
TesterBase<TDeviceBase, UConcreteDevice>::
CreateFloatProperty(const std::string& name,
      boost::shared_ptr< LoggedFloatSetting<UConcreteDevice> > setting)
{
   Super::CreateFloatProperty(name.c_str(), setting->Get(), false,
         setting->NewPropertyAction());
   if (setting->HasMinMax())
   {
      Super::SetPropertyLimits(name.c_str(),
            setting->GetMin(), setting->GetMax());
   }
}
