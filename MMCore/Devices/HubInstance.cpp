// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCore
//
// DESCRIPTION:   Hub device instance wrapper
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

#include "HubInstance.h"

#include "../Error.h"
#include "../MMCore.h"


// Raw pointers to peripherals are used here (at least for now), because we are
// dealing with devices that are not "loaded" as far as the core is concerned.
// In other words, such peripherals have not been assigned labels.


std::vector<std::string>
HubInstance::GetInstalledPeripheralNames()
{
   std::vector<MM::Device*> peripherals = GetInstalledPeripherals();

   std::vector<std::string> names;
   names.reserve(peripherals.size());

   for (std::vector<MM::Device*>::iterator it = peripherals.begin(), end = peripherals.end();
         it != end; ++it)
   {
      DeviceStringBuffer nameBuf(0, "GetName");
      (*it)->GetName(nameBuf.GetBuffer());
      if (!nameBuf.IsEmpty())
      {
         names.push_back(nameBuf.Get());
      }
   }

   return names;
}

std::string
HubInstance::GetInstalledPeripheralDescription(const std::string& peripheralName)
{
   std::vector<MM::Device*> peripherals = GetInstalledPeripherals();
   for (std::vector<MM::Device*>::iterator it = peripherals.begin(), end = peripherals.end();
         it != end; ++it)
   {
      DeviceStringBuffer nameBuf(0, "GetName");
      (*it)->GetName(nameBuf.GetBuffer());
      if (nameBuf.Get() == peripheralName)
      {
         DeviceStringBuffer descBuf(0, "GetDescription");
         (*it)->GetDescription(descBuf.GetBuffer());
         return descBuf.Get();
      }
   }

   throw CMMError("No peripheral with name " + ToQuotedString(peripheralName) +
         " installed in hub " + ToQuotedString(GetLabel()));
}

std::vector<MM::Device*>
HubInstance::GetInstalledPeripherals()
{
   DetectInstalledDevices();

   unsigned nPeripherals = GetNumberOfInstalledDevices();
   std::vector<MM::Device*> peripherals;
   peripherals.reserve(nPeripherals);

   for (unsigned i = 0; i < nPeripherals; ++i)
      peripherals.push_back(GetInstalledDevice(i));
   return peripherals;
}

void HubInstance::DetectInstalledDevices()
{
   // This wrapper is idempotent.

   if (!hasDetectedInstalledDevices_)
   {
      detectInstalledDevicesStatus_ = GetImpl()->DetectInstalledDevices();
      hasDetectedInstalledDevices_ = true;
   }
   ThrowIfError(detectInstalledDevicesStatus_,
         "Failed to detect installed peripheral devices");
}

unsigned HubInstance::GetNumberOfInstalledDevices() { return GetImpl()->GetNumberOfInstalledDevices(); }

MM::Device* HubInstance::GetInstalledDevice(int devIdx)
{
   MM::Device* peripheral = GetImpl()->GetInstalledDevice(devIdx);
   if (!peripheral)
      throw CMMError("Hub " + ToQuotedString(GetLabel()) +
            " returned a null peripheral at index " + ToString(devIdx));
   return peripheral;
}
