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

#ifdef WIN32
// disable exception scpecification warnings in MSVC
#pragma warning( disable : 4290 )
#endif

#include <string>
#include <cstring>
#include <vector>
#include <map>
#include "../MMDevice/MMDeviceConstants.h"
#include "../MMDevice/MMDevice.h"
#include "ErrorCodes.h"
#include "Error.h"

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
   MM::Device* GetDevice(const char* label) const throw (CMMError);
   std::string GetDeviceLabel(const MM::Device& device) const;
   std::vector<std::string> GetDeviceList(MM::DeviceType t = MM::AnyType) const;
   std::vector<std::string> GetLoadedPeripherals(const char* hubLabel) const;
   MM::Hub* GetParentDevice(const MM::Device& dev) const;

   // device browsing support
   static void AddSearchPath(std::string path);
   static std::vector<std::string> GetModules();
   static std::vector<std::string> GetAvailableDevices(const char* moduleName) throw (CMMError);
   static std::vector<std::string> GetAvailableDeviceDescriptions(const char* moduleName) throw (CMMError);
   static std::vector<long> GetAvailableDeviceTypes(const char* moduleName) throw (CMMError);

   // persistence
   static void SetPersistentData(HDEVMODULE hLib, const char* moduleName);
   std::string Serialize();
   void Restore(const std::string& data);
  
private:
   static void GetModules(std::vector<std::string> &modules, const char *path);
   static void GetSystemError(std::string& errorText);
   static void ReleasePluginLibrary(HDEVMODULE libHandle);
   static HDEVMODULE LoadPluginLibrary(const char* libName);
   static void* GetModuleFunction(HDEVMODULE hLib, const char* funcName);
   static void CheckVersion(HDEVMODULE libHandle);
   static std::string FindInSearchPath(std::string filename);

   typedef std::map<std::string, HDEVMODULE> CModuleMap;
   typedef std::map<std::string, MM::Device*> CDeviceMap;
   typedef std::vector<MM::Device*> DeviceArray;

   typedef std::vector<std::string>  CPersistentData;
   typedef std::map<std::string, CPersistentData> CPersistentDataMap;
   static CPersistentDataMap persistentDataMap;
   // searchPaths_ is static so that the static methods can use them
   static std::vector<std::string> searchPaths_;
   CDeviceMap devices_;
   DeviceArray devArray_;
};

#endif //_PLUGIN_MANAGER_H_
