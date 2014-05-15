// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCore
//
// DESCRIPTION:   Base class for wrapped device objects
//
// COPYRIGHT:     University of California, San Francisco, 2014,
//                All Rights reserved
//
// LICENSE:       This file is distributed under the "Lesser GPL" (LGPL) license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
// AUTHOR:        Mark Tsuchida

#include "DeviceInstance.h"

#include "../../MMDevice/MMDevice.h"
#include "../CoreUtils.h"
#include "../Error.h"
#include "../LoadableModules/LoadedDeviceAdapter.h"
#include "../MMCore.h"


DeviceInstance::DeviceInstance(CMMCore* core,
      boost::shared_ptr<LoadedDeviceAdapter> adapter,
      const std::string& name,
      MM::Device* pDevice,
      DeleteDeviceFunction deleteFunction,
      const std::string& label) :
   core_(core),
   adapter_(adapter),
   label_(label),
   deleteFunction_(deleteFunction),
   pImpl_(pDevice)
{
   const std::string actualName = GetName();
   if (actualName != name)
   {
      deleteFunction_(pImpl_);
      throw CMMError("Requested device " + ToQuotedString(name) +
            " from device adapter " + ToQuotedString(adapter->GetName()) +
            " but got an instance named " + ToQuotedString(actualName));
   }
}

DeviceInstance::~DeviceInstance()
{
   // TODO Should we call Shutdown here? Or check that we have done so?
   deleteFunction_(pImpl_);
}

unsigned
DeviceInstance::GetNumberOfProperties() const
{ return pImpl_->GetNumberOfProperties(); }

int
DeviceInstance::GetProperty(const char* name, char* value) const
{ return pImpl_->GetProperty(name, value); }

int
DeviceInstance::SetProperty(const char* name, const char* value) const
{ return pImpl_->SetProperty(name, value); }

bool
DeviceInstance::HasProperty(const char* name) const
{ return pImpl_->HasProperty(name); }

bool
DeviceInstance::GetPropertyName(unsigned idx, char* name) const
{ return pImpl_->GetPropertyName(idx, name); }

int
DeviceInstance::GetPropertyReadOnly(const char* name, bool& readOnly) const
{ return pImpl_->GetPropertyReadOnly(name, readOnly); }

int
DeviceInstance::GetPropertyInitStatus(const char* name, bool& preInit) const
{ return pImpl_->GetPropertyInitStatus(name, preInit); }

int
DeviceInstance::HasPropertyLimits(const char* name, bool& hasLimits) const
{ return pImpl_->HasPropertyLimits(name, hasLimits); }

int
DeviceInstance::GetPropertyLowerLimit(const char* name, double& lowLimit) const
{ return pImpl_->GetPropertyLowerLimit(name, lowLimit); }

int
DeviceInstance::GetPropertyUpperLimit(const char* name, double& hiLimit) const
{ return pImpl_->GetPropertyUpperLimit(name, hiLimit); }

int
DeviceInstance::GetPropertyType(const char* name, MM::PropertyType& pt) const
{ return pImpl_->GetPropertyType(name, pt); }

unsigned
DeviceInstance::GetNumberOfPropertyValues(const char* propertyName) const
{ return pImpl_->GetNumberOfPropertyValues(propertyName); }

bool
DeviceInstance::GetPropertyValueAt(const char* propertyName, unsigned index, char* value) const
{ return pImpl_->GetPropertyValueAt(propertyName, index, value); }

int
DeviceInstance::IsPropertySequenceable(const char* name, bool& isSequenceable) const
{ return pImpl_->IsPropertySequenceable(name, isSequenceable); }

int
DeviceInstance::GetPropertySequenceMaxLength(const char* propertyName, long& nrEvents) const
{ return pImpl_->GetPropertySequenceMaxLength(propertyName, nrEvents); }

int
DeviceInstance::StartPropertySequence(const char* propertyName)
{ return pImpl_->StartPropertySequence(propertyName); }

int
DeviceInstance::StopPropertySequence(const char* propertyName)
{ return pImpl_->StopPropertySequence(propertyName); }

int
DeviceInstance::ClearPropertySequence(const char* propertyName)
{ return pImpl_->ClearPropertySequence(propertyName); }

int
DeviceInstance::AddToPropertySequence(const char* propertyName, const char* value)
{ return pImpl_->AddToPropertySequence(propertyName, value); }

int
DeviceInstance::SendPropertySequence(const char* propertyName)
{ return pImpl_->SendPropertySequence(propertyName); }

bool
DeviceInstance::GetErrorText(int errorCode, char* errMessage) const
{ return pImpl_->GetErrorText(errorCode, errMessage); }

bool
DeviceInstance::Busy()
{ return pImpl_->Busy(); }

double
DeviceInstance::GetDelayMs() const
{ return pImpl_->GetDelayMs(); }

void
DeviceInstance::SetDelayMs(double delay)
{ return pImpl_->SetDelayMs(delay); }

bool
DeviceInstance::UsesDelay()
{ return pImpl_->UsesDelay(); }

int
DeviceInstance::Initialize()
{ return pImpl_->Initialize(); }

int
DeviceInstance::Shutdown()
{ return pImpl_->Shutdown(); }

MM::DeviceType
DeviceInstance::GetType() const
{ return pImpl_->GetType(); }

std::string
DeviceInstance::GetName() const
{
   char buf[MM::MaxStrLength + 1];
   memset(buf, 0, sizeof(buf));
   pImpl_->GetName(buf);
   return buf;
}

void
DeviceInstance::SetCallback(MM::Core* callback)
{ return pImpl_->SetCallback(callback); }

int
DeviceInstance::AcqBefore()
{ return pImpl_->AcqBefore(); }

int
DeviceInstance::AcqAfter()
{ return pImpl_->AcqAfter(); }

int
DeviceInstance::AcqBeforeFrame()
{ return pImpl_->AcqBeforeFrame(); }

int
DeviceInstance::AcqAfterFrame()
{ return pImpl_->AcqAfterFrame(); }

int
DeviceInstance::AcqBeforeStack()
{ return pImpl_->AcqBeforeStack(); }

int
DeviceInstance::AcqAfterStack()
{ return pImpl_->AcqAfterStack(); }

MM::DeviceDetectionStatus
DeviceInstance::DetectDevice()
{ return pImpl_->DetectDevice(); }

void
DeviceInstance::SetParentID(const char* parentId)
{ return pImpl_->SetParentID(parentId); }

void
DeviceInstance::GetParentID(char* parentID) const
{ return pImpl_->GetParentID(parentID); }
