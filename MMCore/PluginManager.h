///////////////////////////////////////////////////////////////////////////////
// FILE:          PluginManager.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCore
//-----------------------------------------------------------------------------
// DESCRIPTION:   Loading/unloading of plugins(module libraries) and creation
//                of devices.
//              
// COPYRIGHT:     University of California, San Francisco, 2006,
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
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 08/10/2005
// 
// REVISIONS:     
//
// CVS:           $Id$
//

#ifndef _PLUGIN_MANAGER_H_
#define _PLUGIN_MANAGER_H_

#include <string>
#include <vector>
#include <map>
#include "../MMDevice/MMDeviceConstants.h"
#include "../MMDevice/MMDevice.h"
#include "ErrorCodes.h"

/**
 * Manages the device collection. Responsible for handling plugin libraries
 * and device construction and destruction
 */
class CPluginManager
{
public:

	CPluginManager();
	virtual ~CPluginManager();
   
   MM::Device* LoadDevice(const char* label, const char* moduleName, const char* deviceName);
   void UnloadDevice(MM::Device* device);
   void UnloadAllDevices();
   MM::Device* GetDevice(const char* label) const;
   std::string GetDeviceLabel(const MM::Device& device) const;
   std::vector<std::string> GetDeviceList(MM::DeviceType t = MM::AnyType) const;

   // device browsing support
   static std::vector<std::string> GetModules(const char* searchPath);
   static std::vector<std::string> GetAvailableDevices(const char* moduleName);
   static std::vector<std::string> GetAvailableDeviceDescriptions(const char* moduleName);
   static std::vector<int> GetAvailableDeviceTypes(const char* moduleName);

   // persistence
   std::string Serialize();
   void Restore(const std::string& data);
  
private:
   static void GetSystemError(std::string& errorText);
   static void ReleasePluginLibrary(HDEVMODULE libHandle);
   static HDEVMODULE LoadPluginLibrary(const char* libName);
   static void* GetModuleFunction(HDEVMODULE hLib, const char* funcName);
   static void CheckVersion(HDEVMODULE libHandle);

   typedef std::map<std::string, HDEVMODULE> CModuleMap;
   typedef std::map<std::string, MM::Device*> CDeviceMap;
   //CModuleMap modules_;
   CDeviceMap devices_;
};

#endif //_PLUGIN_MANAGER_H_
