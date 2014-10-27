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

#include "LoggedSettingImpl.h"

#include "DeviceUtils.h"

#include <string>


template <template <class> class TDeviceBase, class UConcreteDevice>
void
TesterBase<TDeviceBase, UConcreteDevice>::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, GetDeviceName().c_str());
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
   return GetLogger()->IsBusy(GetDeviceName());
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
CreateOnOffProperty(const std::string& name,
      typename BoolSetting<UConcreteDevice>::Ptr setting)
{
   Super::CreateStringProperty(name.c_str(),
         setting->Get() ? "On" : "Off", false,
         setting->NewPropertyAction(BoolSetting<UConcreteDevice>::ON_OFF));
   Super::AddAllowedValue(name.c_str(), "Off");
   Super::AddAllowedValue(name.c_str(), "On");
}


template <template <class> class TDeviceBase, class UConcreteDevice>
void
TesterBase<TDeviceBase, UConcreteDevice>::
CreateYesNoProperty(const std::string& name,
      typename BoolSetting<UConcreteDevice>::Ptr setting)
{
   Super::CreateStringProperty(name.c_str(),
         setting->Get() ? "Yes" : "No", false,
         setting->NewPropertyAction(BoolSetting<UConcreteDevice>::YES_NO));
   Super::AddAllowedValue(name.c_str(), "No");
   Super::AddAllowedValue(name.c_str(), "Yes");
}


template <template <class> class TDeviceBase, class UConcreteDevice>
void
TesterBase<TDeviceBase, UConcreteDevice>::
CreateOneZeroProperty(const std::string& name,
      typename BoolSetting<UConcreteDevice>::Ptr setting)
{
   Super::CreateIntegerProperty(name.c_str(),
         setting->Get() ? 1 : 0, false,
         setting->NewPropertyAction(BoolSetting<UConcreteDevice>::ONE_ZERO));
   Super::SetPropertyLimits(name.c_str(), 0, 1);
}


template <template <class> class TDeviceBase, class UConcreteDevice>
void
TesterBase<TDeviceBase, UConcreteDevice>::
CreateIntegerProperty(const std::string& name,
      typename IntegerSetting<UConcreteDevice>::Ptr setting)
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
      typename FloatSetting<UConcreteDevice>::Ptr setting)
{
   Super::CreateFloatProperty(name.c_str(), setting->Get(), false,
         setting->NewPropertyAction());
   if (setting->HasMinMax())
   {
      Super::SetPropertyLimits(name.c_str(),
            setting->GetMin(), setting->GetMax());
   }
}


template <class TConcreteStage, long UStepsPerMicrometer>
int
Tester1DStageBase<TConcreteStage, UStepsPerMicrometer>::Initialize()
{
   int err;

   err = Super::Initialize();
   if (err != DEVICE_OK)
      return err;

   zPositionUm_ = FloatSetting<TConcreteStage>::New(Super::GetLogger(), This(),
         "ZPositionUm", 0.0, false);
   originSet_ = OneShotSetting<TConcreteStage>::New(Super::GetLogger(), This(),
         "OriginSet");

   return DEVICE_OK;
}


template <class TConcreteStage, long UStepsPerMicrometer>
int
Tester1DStageBase<TConcreteStage, UStepsPerMicrometer>::SetPositionUm(double pos)
{
   SettingLogger::GuardType g = Super::GetLogger()->Guard();
   Super::MarkBusy();
   return zPositionUm_->Set(pos);
}


template <class TConcreteStage, long UStepsPerMicrometer>
int
Tester1DStageBase<TConcreteStage, UStepsPerMicrometer>::GetPositionUm(double& pos)
{
   return zPositionUm_->Get(pos);
}


template <class TConcreteStage, long UStepsPerMicrometer>
int
Tester1DStageBase<TConcreteStage, UStepsPerMicrometer>::SetPositionSteps(long steps)
{
   SettingLogger::GuardType g = Super::GetLogger()->Guard();
   Super::MarkBusy();
   return zPositionUm_->Set(0.1 * steps);
}


template <class TConcreteStage, long UStepsPerMicrometer>
int
Tester1DStageBase<TConcreteStage, UStepsPerMicrometer>::GetPositionSteps(long& steps)
{
   double um;
   int err = zPositionUm_->Get(um);
   if (err != DEVICE_OK)
      return err;
   steps = static_cast<long>(10.0 * um + 0.5);
   return DEVICE_OK;
}


template <class TConcreteStage, long UStepsPerMicrometer>
int
Tester1DStageBase<TConcreteStage, UStepsPerMicrometer>::SetOrigin()
{
   SettingLogger::GuardType g = Super::GetLogger()->Guard();
   Super::MarkBusy();
   return originSet_->Set();
}


template <class TConcreteStage, long UStepsPerMicrometer>
int
Tester1DStageBase<TConcreteStage, UStepsPerMicrometer>::
GetLimits(double& lower, double& upper)
{
   // Not (yet) designed for testing
   lower = -100000.0;
   upper = +100000.0;
   return DEVICE_OK;
}
