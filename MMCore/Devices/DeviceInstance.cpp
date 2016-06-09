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
#include "../Logging/Logger.h"
#include "../MMCore.h"


int
DeviceInstance::LogMessage(const char* msg, bool debugOnly)
{
   deviceLogger_(debugOnly ? mm::logging::LogLevelDebug :
         mm::logging::LogLevelInfo, msg);
   return DEVICE_OK;
}


DeviceInstance::DeviceInstance(CMMCore* core,
      boost::shared_ptr<LoadedDeviceAdapter> adapter,
      const std::string& name,
      MM::Device* pDevice,
      DeleteDeviceFunction deleteFunction,
      const std::string& label,
      mm::logging::Logger deviceLogger,
      mm::logging::Logger coreLogger) :
   pImpl_(pDevice),
   core_(core),
   adapter_(adapter),
   label_(label),
   deleteFunction_(deleteFunction),
   deviceLogger_(deviceLogger),
   coreLogger_(coreLogger)
{
   const std::string actualName = GetName();
   if (actualName != name)
   {
      LOG_WARNING(Logger()) << "Requested device named \"" << name <<
         "\" but the actual device is named \"" << actualName << "\"";

      // TODO This should ideally be an error, but currently it breaks some
      // device adapters. Probably best to remove GetName() from MM::Device
      // entirely and handle it solely in the Core.
   }

   pImpl_->SetLabel(label_.c_str());
}

DeviceInstance::~DeviceInstance()
{
   // TODO Should we call Shutdown here? Or check that we have done so?
   deleteFunction_(pImpl_);
}

CMMError
DeviceInstance::MakeException() const
{
   return CMMError("Error in device " + ToQuotedString(GetLabel()));
}

CMMError
DeviceInstance::MakeExceptionForCode(int code) const
{
   return CMMError("Error in device " + ToQuotedString(GetLabel()) + ": " +
         GetErrorText(code) + " (" + ToString(code) + ")");
}

void
DeviceInstance::ThrowError(const std::string& message) const
{
   CMMError e = CMMError(message, MakeException());
   LOG_ERROR(Logger()) << e.getFullMsg();
   throw e;
}

void
DeviceInstance::ThrowIfError(int code) const
{
   if (code == DEVICE_OK)
   {
      return;
   }

   CMMError e = MakeExceptionForCode(code);
   LOG_ERROR(Logger()) << e.getFullMsg();
   throw e;
}

void
DeviceInstance::ThrowIfError(int code, const std::string& message) const
{
   if (code == DEVICE_OK)
   {
      return;
   }

   CMMError e = CMMError(message, MakeExceptionForCode(code));
   LOG_ERROR(Logger()) << e.getFullMsg();
   throw e;
}

void
DeviceInstance::DeviceStringBuffer::ThrowBufferOverflowError() const
{
   std::string label(instance_ ? instance_->GetLabel() : "<unknown>");
   throw CMMError("Buffer overflow in device " + ToQuotedString(label) +
         " while calling " + funcName_ + "(); "
         "this is most likely a bug in the device adapter");
}

std::vector<std::string>
DeviceInstance::GetPropertyNames() const
{
   std::vector<std::string> result;
   size_t nrProperties = GetNumberOfProperties();
   result.reserve(nrProperties);
   for (size_t i = 0; i < nrProperties; ++i)
      result.push_back(GetPropertyName(i));
   return result;
}

unsigned
DeviceInstance::GetNumberOfProperties() const
{ return pImpl_->GetNumberOfProperties(); }

std::string
DeviceInstance::GetProperty(const std::string& name) const
{
   DeviceStringBuffer valueBuf(this, "GetProperty");
   int err = pImpl_->GetProperty(name.c_str(), valueBuf.GetBuffer());
   ThrowIfError(err, "Cannot get value of property " +
         ToQuotedString(name));
   return valueBuf.Get();
}

void
DeviceInstance::SetProperty(const std::string& name,
      const std::string& value) const
{
   LOG_DEBUG(Logger()) << "Will set property \"" << name << "\" to \"" <<
      value << "\"";

   int err = pImpl_->SetProperty(name.c_str(), value.c_str());

   ThrowIfError(err, "Cannot set property " + ToQuotedString(name) +
         " to " + ToQuotedString(value));

   LOG_DEBUG(Logger()) << "Did set property \"" << name << "\" to \"" <<
      value << "\"";
}

bool
DeviceInstance::HasProperty(const std::string& name) const
{ return pImpl_->HasProperty(name.c_str()); }

std::string
DeviceInstance::GetPropertyName(size_t idx) const
{
   DeviceStringBuffer nameBuf(this, "GetPropertyName");
   bool ok = pImpl_->GetPropertyName(static_cast<unsigned>(idx), nameBuf.GetBuffer());
   if (!ok)
      ThrowError("Cannot get property name at index " + ToString(idx));
   return nameBuf.Get();
}

bool
DeviceInstance::GetPropertyReadOnly(const char* name) const
{
   bool readOnly;
   ThrowIfError(pImpl_->GetPropertyReadOnly(name, readOnly));
   return readOnly;
}

bool
DeviceInstance::GetPropertyInitStatus(const char* name) const
{
   bool isPreInit;
   ThrowIfError(pImpl_->GetPropertyInitStatus(name, isPreInit));
   return isPreInit;
}

bool
DeviceInstance::HasPropertyLimits(const char* name) const
{
   bool hasLimits;
   ThrowIfError(pImpl_->HasPropertyLimits(name, hasLimits));
   return hasLimits;
}

double
DeviceInstance::GetPropertyLowerLimit(const char* name) const
{
   double lowLimit;
   ThrowIfError(pImpl_->GetPropertyLowerLimit(name, lowLimit));
   return lowLimit;
}

double
DeviceInstance::GetPropertyUpperLimit(const char* name) const
{
   double highLimit;
   ThrowIfError(pImpl_->GetPropertyUpperLimit(name, highLimit));
   return highLimit;
}

MM::PropertyType
DeviceInstance::GetPropertyType(const char* name) const
{
   MM::PropertyType propType;
   ThrowIfError(pImpl_->GetPropertyType(name, propType));
   return propType;
}

unsigned
DeviceInstance::GetNumberOfPropertyValues(const char* propertyName) const
{ return pImpl_->GetNumberOfPropertyValues(propertyName); }

std::string
DeviceInstance::GetPropertyValueAt(const std::string& propertyName, unsigned index) const
{
   DeviceStringBuffer valueBuf(this, "GetPropertyValueAt");
   bool ok = pImpl_->GetPropertyValueAt(propertyName.c_str(), index,
         valueBuf.GetBuffer());
   if (!ok)
   {
      throw CMMError("Device " + ToQuotedString(GetLabel()) +
            ": cannot get allowed value at index " +
            boost::lexical_cast<std::string>(index) + " of property " +
            ToQuotedString(propertyName));
   }
   return valueBuf.Get();
}

bool
DeviceInstance::IsPropertySequenceable(const char* name) const
{
   bool isSequenceable;
   ThrowIfError(pImpl_->IsPropertySequenceable(name, isSequenceable));
   return isSequenceable;
}

long
DeviceInstance::GetPropertySequenceMaxLength(const char* propertyName) const
{
   long nrEvents;
   ThrowIfError(pImpl_->GetPropertySequenceMaxLength(propertyName, nrEvents));
   return nrEvents;
}

void
DeviceInstance::StartPropertySequence(const char* propertyName)
{
   ThrowIfError(pImpl_->StartPropertySequence(propertyName));
}

void
DeviceInstance::StopPropertySequence(const char* propertyName)
{
   ThrowIfError(pImpl_->StopPropertySequence(propertyName));
}

void
DeviceInstance::ClearPropertySequence(const char* propertyName)
{
   ThrowIfError(pImpl_->ClearPropertySequence(propertyName));
}

void
DeviceInstance::AddToPropertySequence(const char* propertyName, const char* value)
{
   ThrowIfError(pImpl_->AddToPropertySequence(propertyName, value));
}

void
DeviceInstance::SendPropertySequence(const char* propertyName)
{
   ThrowIfError(pImpl_->SendPropertySequence(propertyName));
}

std::string
DeviceInstance::GetErrorText(int code) const
{
   DeviceStringBuffer msgBuf(this, "GetErrorText");
   bool ok = pImpl_->GetErrorText(code, msgBuf.GetBuffer());
   if (ok)
   {
      std::string msg = msgBuf.Get();
      if (!msg.empty())
         return msg;
   }
   return "(Error message unavailable)";
}

bool
DeviceInstance::Busy()
{ return pImpl_->Busy(); }

double
DeviceInstance::GetDelayMs() const
{ return pImpl_->GetDelayMs(); }

void
DeviceInstance::SetDelayMs(double delay)
{ pImpl_->SetDelayMs(delay); }

bool
DeviceInstance::UsesDelay()
{ return pImpl_->UsesDelay(); }

void
DeviceInstance::Initialize()
{
   ThrowIfError(pImpl_->Initialize());
}

void
DeviceInstance::Shutdown()
{
   ThrowIfError(pImpl_->Shutdown());
}

MM::DeviceType
DeviceInstance::GetType() const
{ return pImpl_->GetType(); }

std::string
DeviceInstance::GetName() const
{
   DeviceStringBuffer nameBuf(this, "GetName");
   pImpl_->GetName(nameBuf.GetBuffer());
   return nameBuf.Get();
}

void
DeviceInstance::SetCallback(MM::Core* callback)
{ pImpl_->SetCallback(callback); }

void
DeviceInstance::AcqBefore()
{
   ThrowIfError(pImpl_->AcqBefore());
}

void
DeviceInstance::AcqAfter()
{
   ThrowIfError(pImpl_->AcqAfter());
}

void
DeviceInstance::AcqBeforeFrame()
{
   ThrowIfError(pImpl_->AcqBeforeFrame());
}

void
DeviceInstance::AcqAfterFrame()
{
   ThrowIfError(pImpl_->AcqAfterFrame());
}

void
DeviceInstance::AcqBeforeStack()
{
   ThrowIfError(pImpl_->AcqBeforeStack());
}

void
DeviceInstance::AcqAfterStack()
{
   ThrowIfError(pImpl_->AcqAfterStack());
}

bool
DeviceInstance::SupportsDeviceDetection()
{
    return pImpl_->SupportsDeviceDetection();
}

MM::DeviceDetectionStatus
DeviceInstance::DetectDevice()
{ return pImpl_->DetectDevice(); }

void
DeviceInstance::SetParentID(const char* parentId)
{ pImpl_->SetParentID(parentId); }

std::string
DeviceInstance::GetParentID() const
{
   DeviceStringBuffer nameBuf(this, "GetParentID");
   pImpl_->GetParentID(nameBuf.GetBuffer());
   return nameBuf.Get();
}
