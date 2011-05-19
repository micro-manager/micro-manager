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
std::map<std::string,bool> g_deviceDiscoverability;

bool discoverabilityEnabled_g;

MODULE_API long GetModuleVersion()
{
   return MODULE_INTERFACE_VERSION;   
}

MODULE_API long GetDeviceInterfaceVersion()
{
   return DEVICE_INTERFACE_VERSION;   
}

void AddAvailableDeviceName(const char* name, const char* descr)
{
   std::vector<DeviceInfo>::const_iterator it;
   for (it=g_availableDevices.begin(); it!=g_availableDevices.end(); ++it)
      if (it->first.compare(name) == 0)
         return; // already there

   // add to the list
   SetDeviceIsDiscoverable(name, false);
   g_availableDevices.push_back(std::make_pair(name, descr));   
}

void SetDeviceIsDiscoverable( const char* pdevice, const bool value)
{
   if(pdevice)
   {
      g_deviceDiscoverability[std::string(pdevice)] = value;
   }

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

MODULE_API bool GetDeviceDescription(unsigned deviceIndex, char* description, unsigned bufLen)
{
   if (deviceIndex >= g_availableDevices.size())
      return false;
   
   strncpy(description, g_availableDevices[deviceIndex].second.c_str(), bufLen-1);
   return true;
}


MODULE_API bool GetDeviceIsDiscoverable(char* pDeviceName, bool* pvalue)
{
   bool ret = false;
   if( pvalue)
   {
      *pvalue = false;
      std::string name(pDeviceName);
      std::map<std::string,bool>::iterator ii = g_deviceDiscoverability.find(name);
      if( ii != g_deviceDiscoverability.end())
      {
         // found it
         *pvalue = (*ii).second;
         ret = true;
      }
   }

   return ret;


}

MODULE_API void EnableDeviceDiscovery(bool enable)
{
   discoverabilityEnabled_g = enable;
}



// 
bool DiscoverabilityTest()
{
   return ::discoverabilityEnabled_g;
   //return CDeviceUtils::CheckEnvironment("DISCOVERABILITYTEST");
}

