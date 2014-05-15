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
      char name[MM::MaxStrLength];
      memset(name, 0, sizeof(name));
      (*it)->GetName(name);
      if (name[MM::MaxStrLength - 1] != '\0')
      {
         // TODO Device corrupted our stack!
      }
      if (name[0] != '\0')
         names.push_back(name);
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
      char name[MM::MaxStrLength];
      memset(name, 0, sizeof(name));
      (*it)->GetName(name);
      if (name[MM::MaxStrLength - 1] != '\0')
      {
         // TODO Device corrupted our stack!
      }
      if (name == peripheralName)
      {
         char description[MM::MaxStrLength];
         memset(description, 0, sizeof(description));
         (*it)->GetDescription(description);
         if (description[MM::MaxStrLength - 1] != '\0')
         {
            // TODO Device corrupted our stack!
         }
         return description;
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
   if (detectInstalledDevicesStatus_ != DEVICE_OK) // TODO Retrieve error text
      throw CMMError("Device " + ToQuotedString(GetLabel()) + " error: "
            "DetectInstalledDevices() returned error code " +
            ToString(detectInstalledDevicesStatus_));
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
