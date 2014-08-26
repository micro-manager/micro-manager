// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCore
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

#pragma once

#include "DeviceInstance.h"

#include "../CoreUtils.h"
#include "../Error.h"
#include "../LoadableModules/LoadedDeviceAdapter.h"


// Common member function implementations for concrete DeviceInstance
// subclasses.
template <typename TMMDevice>
class DeviceInstanceBase : public DeviceInstance
{
public:
   typedef TMMDevice RawDeviceClass;

   // It would be nice to get rid of the need for raw pointers, but for now we
   // need it for the few CoreCallback methods that return a device pointer.
   RawDeviceClass* GetRawPtr() const { return GetImpl(); }

protected:
   DeviceInstanceBase(CMMCore* core,
         boost::shared_ptr<LoadedDeviceAdapter> adapter,
         const std::string& name,
         MM::Device* pDevice,
         DeleteDeviceFunction deleteFunction,
         const std::string& label,
         mm::logging::Logger deviceLogger,
         mm::logging::Logger coreLogger) :
      DeviceInstance(core, adapter, name, pDevice, deleteFunction,
            label, deviceLogger, coreLogger)
   {
      MM::DeviceType actualType = GetType();
      if (actualType != RawDeviceClass::Type)
         throw CMMError("Device " + ToQuotedString(name) +
               " of device adapter " + ToQuotedString(adapter->GetName()) +
               " was expected to be type " + ToString(RawDeviceClass::Type) +
               " but turned out to be type " + ToString(actualType));
   }

protected:
   RawDeviceClass* GetImpl() const /* final */ { return static_cast<RawDeviceClass*>(pImpl_); }
};
