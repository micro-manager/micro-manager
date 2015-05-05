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

#include "LoggedSetting.h"

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
   // Guard against multiple calls
   if (InterDevice::GetHub())
      return DEVICE_OK;

   MM::Hub* pHub = Super::GetParentHub();
   if (pHub == 0)
   {
      Super::LogMessage("Failing initialization due to missing hub");
      return DEVICE_ERR;
   }
   InterDevice::SetHub(static_cast<TesterHub*>(pHub)->GetSharedPtr());

   return CommonHubPeripheralInitialize();
}


template <template <class> class TDeviceBase, class UConcreteDevice>
int
TesterBase<TDeviceBase, UConcreteDevice>::CommonHubPeripheralInitialize()
{
   Super::CreateStringProperty(MM::g_Keyword_Name,
         GetDeviceName().c_str(), true);

   int err = GetHub()->RegisterDevice(GetDeviceName(), shared_from_this());
   if (err != DEVICE_OK)
      return err;

   // Devices are initially "busy"
   busySetting_ = CountDownSetting::New(GetLogger(), this, "Busy", 1);
   return DEVICE_OK;
}


template <template <class> class TDeviceBase, class UConcreteDevice>
int
TesterBase<TDeviceBase, UConcreteDevice>::Shutdown()
{
   // Shutdown may be called more than once, in which case GetHub() may return
   // a null pointer.
   if (!GetHub())
      return DEVICE_OK;

   TesterHub::Guard g(GetHub()->LockGlobalMutex());

   CommonHubPeripheralShutdown();
   InterDevice::SetHub(boost::shared_ptr<TesterHub>());
   return DEVICE_OK;
}


template <template <class> class TDeviceBase, class UConcreteDevice>
void
TesterBase<TDeviceBase, UConcreteDevice>::CommonHubPeripheralShutdown()
{
   GetHub()->UnregisterDevice(GetDeviceName());
}


template <template <class> class TDeviceBase, class UConcreteDevice>
bool
TesterBase<TDeviceBase, UConcreteDevice>::Busy()
{
   TesterHub::Guard g(GetHub()->LockGlobalMutex());
   return GetBusySetting()->Get() > 0;
}


template <template <class> class TDeviceBase, class UConcreteDevice>
void
TesterBase<TDeviceBase, UConcreteDevice>::
CreateOnOffProperty(const std::string& name, BoolSetting::Ptr setting)
{
   Super::CreateStringProperty(name.c_str(),
         setting->Get() ? "On" : "Off", false,
         setting->NewPropertyAction(BoolSetting::ON_OFF));
   Super::AddAllowedValue(name.c_str(), "Off");
   Super::AddAllowedValue(name.c_str(), "On");
}


template <template <class> class TDeviceBase, class UConcreteDevice>
void
TesterBase<TDeviceBase, UConcreteDevice>::
CreateYesNoProperty(const std::string& name, BoolSetting::Ptr setting)
{
   Super::CreateStringProperty(name.c_str(),
         setting->Get() ? "Yes" : "No", false,
         setting->NewPropertyAction(BoolSetting::YES_NO));
   Super::AddAllowedValue(name.c_str(), "No");
   Super::AddAllowedValue(name.c_str(), "Yes");
}


template <template <class> class TDeviceBase, class UConcreteDevice>
void
TesterBase<TDeviceBase, UConcreteDevice>::
CreateOneZeroProperty(const std::string& name, BoolSetting::Ptr setting)
{
   Super::CreateIntegerProperty(name.c_str(),
         setting->Get() ? 1 : 0, false,
         setting->NewPropertyAction(BoolSetting::ONE_ZERO));
   Super::SetPropertyLimits(name.c_str(), 0, 1);
}


template <template <class> class TDeviceBase, class UConcreteDevice>
void
TesterBase<TDeviceBase, UConcreteDevice>::
CreateIntegerProperty(const std::string& name, IntegerSetting::Ptr setting)
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
CreateFloatProperty(const std::string& name, FloatSetting::Ptr setting)
{
   Super::CreateFloatProperty(name.c_str(), setting->Get(), false,
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
CreateStringProperty(const std::string& name, StringSetting::Ptr setting)
{
   Super::CreateStringProperty(name.c_str(), setting->Get().c_str(), false,
         setting->NewPropertyAction());
}


template <class TConcreteStage, long UStepsPerMicrometer>
int
Tester1DStageBase<TConcreteStage, UStepsPerMicrometer>::Initialize()
{
   // Guard against multiple calls
   if (Super::GetHub())
      return DEVICE_OK;

   int err = Super::Initialize();
   if (err != DEVICE_OK)
      return err;

   TesterHub::Guard g(Super::GetHub()->LockGlobalMutex());

   zPositionUm_ = FloatSetting::New(Super::GetLogger(), This(),
         "ZPositionUm", 0.0, false);
   zPositionUm_->SetBusySetting(Super::GetBusySetting());
   home_ = OneShotSetting::New(Super::GetLogger(), This(), "Home");
   home_->SetBusySetting(Super::GetBusySetting());
   stop_ = OneShotSetting::New(Super::GetLogger(), This(), "Stop");
   stop_->SetBusySetting(Super::GetBusySetting());
   originSet_ = OneShotSetting::New(Super::GetLogger(), This(), "OriginSet");
   originSet_->SetBusySetting(Super::GetBusySetting());

   return DEVICE_OK;
}


template <class TConcreteStage, long UStepsPerMicrometer>
int
Tester1DStageBase<TConcreteStage, UStepsPerMicrometer>::SetPositionUm(double pos)
{
   TesterHub::Guard g(Super::GetHub()->LockGlobalMutex());
   zPositionUm_->MarkBusy();
   return zPositionUm_->Set(pos);
}


template <class TConcreteStage, long UStepsPerMicrometer>
int
Tester1DStageBase<TConcreteStage, UStepsPerMicrometer>::GetPositionUm(double& pos)
{
   TesterHub::Guard g(Super::GetHub()->LockGlobalMutex());
   return zPositionUm_->Get(pos);
}


template <class TConcreteStage, long UStepsPerMicrometer>
int
Tester1DStageBase<TConcreteStage, UStepsPerMicrometer>::SetPositionSteps(long steps)
{
   TesterHub::Guard g(Super::GetHub()->LockGlobalMutex());
   zPositionUm_->MarkBusy();
   return zPositionUm_->Set(0.1 * steps);
}


template <class TConcreteStage, long UStepsPerMicrometer>
int
Tester1DStageBase<TConcreteStage, UStepsPerMicrometer>::GetPositionSteps(long& steps)
{
   TesterHub::Guard g(Super::GetHub()->LockGlobalMutex());
   double um;
   int err = zPositionUm_->Get(um);
   if (err != DEVICE_OK)
      return err;
   steps = static_cast<long>(10.0 * um + 0.5);
   return DEVICE_OK;
}


template <class TConcreteStage, long UStepsPerMicrometer>
int
Tester1DStageBase<TConcreteStage, UStepsPerMicrometer>::Home()
{
   TesterHub::Guard g(Super::GetHub()->LockGlobalMutex());
   home_->MarkBusy();
   return home_->Set();
}


template <class TConcreteStage, long UStepsPerMicrometer>
int
Tester1DStageBase<TConcreteStage, UStepsPerMicrometer>::Stop()
{
   TesterHub::Guard g(Super::GetHub()->LockGlobalMutex());
   stop_->MarkBusy();
   return stop_->Set();
}


template <class TConcreteStage, long UStepsPerMicrometer>
int
Tester1DStageBase<TConcreteStage, UStepsPerMicrometer>::SetOrigin()
{
   TesterHub::Guard g(Super::GetHub()->LockGlobalMutex());
   originSet_->MarkBusy();
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
