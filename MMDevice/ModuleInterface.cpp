///////////////////////////////////////////////////////////////////////////////
// FILE:          ModuleInterface.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMDevice - Device adapter kit
//-----------------------------------------------------------------------------
// DESCRIPTION:   The implementation for the common plugin functions
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 08/08/2005
// NOTE:          Change the implementation of module interface methods in
//                this file with caution, since the Micro-Manager plugin
//                mechanism relies on specific functionality as implemented
//                here.
// COPYRIGHT:     University of California, San Francisco, 2006
// LICENSE:       This file is distributed under the BSD license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

#include "ModuleInterface.h"

#include <algorithm>
#include <string>
#include <vector>


namespace {

struct DeviceInfo
{
   std::string name_;
   MM::DeviceType type_;
   std::string description_;

   DeviceInfo(const char* name, MM::DeviceType type, const char* description) :
      name_(name),
      type_(type),
      description_(description)
   {}
};

// Predicate for searching by name
class DeviceNameMatches
{
   std::string name_;
public:
   explicit DeviceNameMatches(const std::string& deviceName) : name_(deviceName) {}
   bool operator()(const DeviceInfo& info) { return info.name_ == name_; }
};

} // anonymous namespace


// Registered devices in this module (device adapter library)
static std::vector<DeviceInfo> g_registeredDevices;


MODULE_API long GetModuleVersion()
{
   return MODULE_INTERFACE_VERSION;   
}

MODULE_API long GetDeviceInterfaceVersion()
{
   return DEVICE_INTERFACE_VERSION;   
}

MODULE_API unsigned GetNumberOfDevices()
{
   return static_cast<unsigned>(g_registeredDevices.size());
}

MODULE_API bool GetDeviceName(unsigned deviceIndex, char* name, unsigned bufLen)
{
   if (deviceIndex >= g_registeredDevices.size())
      return false;

   const std::string& deviceName = g_registeredDevices[deviceIndex].name_;

   if (deviceName.size() >= bufLen)
      return false; // buffer too small, can't truncate the name

   strcpy(name, deviceName.c_str());
   return true;
}

MODULE_API bool GetDeviceType(const char* deviceName, int* type)
{
   std::vector<DeviceInfo>::const_iterator it =
      std::find_if(g_registeredDevices.begin(), g_registeredDevices.end(),
            DeviceNameMatches(deviceName));
   if (it == g_registeredDevices.end())
   {
      *type = MM::UnknownType;
      return false;
   }

   // Prefer int over enum across DLL boundary so that the module ABI does not
   // change (somewhat pedantic, but let's be safe).
   *type = static_cast<int>(it->type_);

   return true;
}

MODULE_API bool GetDeviceDescription(const char* deviceName, char* description, unsigned bufLen)
{
   std::vector<DeviceInfo>::const_iterator it =
      std::find_if(g_registeredDevices.begin(), g_registeredDevices.end(),
            DeviceNameMatches(deviceName));
   if (it == g_registeredDevices.end())
      return false;

   strncpy(description, it->description_.c_str(), bufLen - 1);

   return true;
}

void RegisterDevice(const char* deviceName, MM::DeviceType deviceType, const char* deviceDescription)
{
   if (!deviceName)
      return;

   if (!deviceDescription)
      // This is a bug; let the programmer know by displaying an ugly string
      deviceDescription = "(Null description)";

   if (std::find_if(g_registeredDevices.begin(), g_registeredDevices.end(),
            DeviceNameMatches(deviceName)) != g_registeredDevices.end())
      // Device with this name already registered
      return;

   g_registeredDevices.push_back(DeviceInfo(deviceName, deviceType, deviceDescription));
}
