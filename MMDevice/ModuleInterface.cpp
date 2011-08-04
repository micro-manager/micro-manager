///////////////////////////////////////////////////////////////////////////////
// FILE:          ModuleInterface.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMDevice - Device adapter kit
//-----------------------------------------------------------------------------
// DESCRIPTION:   The implemenation for the common plugin functions
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
// CVS:           $Id$
//
#define _CRT_SECURE_NO_DEPRECATE
#include "ModuleInterface.h"
#include <vector>
#include <string>

typedef std::pair<std::string, std::string> DeviceInfo; 
std::vector<DeviceInfo> g_availableDevices;

int FindDeviceIndex(const char* deviceName)
{
   for (unsigned i=0; i<g_availableDevices.size(); i++)
      if (g_availableDevices[i].first.compare(deviceName) == 0)
         return i;

   return -1;
}

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
   return (unsigned) g_availableDevices.size();
}

MODULE_API bool GetDeviceName(unsigned deviceIndex, char* name, unsigned bufLen)
{
   if (deviceIndex >= g_availableDevices.size())
      return false;
   
   if (g_availableDevices[deviceIndex].first.length() >= bufLen)
      return false; // buffer too small, can't truncate the name

   strcpy(name, g_availableDevices[deviceIndex].first.c_str());
   return true;
}

MODULE_API bool GetDeviceDescription(const char* deviceName, char* description, unsigned bufLen)
{
   int idx = FindDeviceIndex(deviceName);
   if (idx < 0)
      return false; // device name not found
   else
      strncpy(description, g_availableDevices[idx].second.c_str(), bufLen-1);

   return true;
}

///////////////////////////////////////////////////////////////////////////////
// Functions for internal use (inside the Module)
//
void AddAvailableDeviceName(const char* name, const char* descr)
{
   std::vector<DeviceInfo>::const_iterator it;
   for (it=g_availableDevices.begin(); it!=g_availableDevices.end(); ++it)
      if (it->first.compare(name) == 0)
         return; // already there

   // add to the list
   g_availableDevices.push_back(std::make_pair(name, descr));   
}

unsigned GetNumberOfDevicesInternal()
{
   return (unsigned) g_availableDevices.size();
}