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

#ifndef _PLUGIN_MANAGER_H_
#define _PLUGIN_MANAGER_H_

#ifdef WIN32
// disable exception scpecification warnings in MSVC
#pragma warning( disable : 4290 )
#endif

#include "LoadableModules/LoadedDeviceAdapter.h"

#include "../MMDevice/MMDeviceConstants.h"
#include "../MMDevice/MMDevice.h"
#include "../MMDevice/DeviceThreads.h"
#include "ErrorCodes.h"
#include "Error.h"

#include <map>
#include <string>
#include <vector>

/**
 * Manages the device collection. Responsible for handling plugin libraries
 * and device construction and destruction
 */
class CPluginManager /* final */
{
public:
   CPluginManager();
   ~CPluginManager();
   
   MM::Device* LoadDevice(const char* label, const char* moduleName, const char* deviceName);
   void UnloadDevice(MM::Device* device);
   void UnloadAllDevices();

   MM::Device* GetDevice(const std::string& label) const throw (CMMError);
   std::string GetDeviceLabel(const MM::Device& device) const;
   std::vector<std::string> GetDeviceList(MM::DeviceType t = MM::AnyType) const;

   std::vector<std::string> GetLoadedPeripherals(const char* hubLabel) const;
   MM::Hub* GetParentDevice(const MM::Device& dev) const;

   void UnloadPluginLibrary(const char* moduleName);

   // Device adapter search paths (there are two sets of search paths; see
   // CMMCore method documentation)
   template <typename TStringIter>
   void SetSearchPaths(TStringIter begin, TStringIter end)
   { preferredSearchPaths_.assign(begin, end); }
   std::vector<std::string> GetSearchPaths() const { return preferredSearchPaths_; }
   std::vector<std::string> GetAvailableDeviceAdapters();

   // Legacy search path support
   static void AddLegacyFallbackSearchPath(const std::string& path);
   static std::vector<std::string> GetModulesInLegacyFallbackSearchPaths();

   std::vector<std::string> GetAvailableDevices(const char* moduleName) throw (CMMError);
   std::vector<std::string> GetAvailableDeviceDescriptions(const char* moduleName) throw (CMMError);
   std::vector<long> GetAvailableDeviceTypes(const char* moduleName) throw (CMMError);

   // module level thread locking
   MMThreadLock* getModuleLock(const MM::Device* pDev);
   bool removeModuleLock(const char* moduleName);

private:
   static std::vector<std::string> GetDefaultSearchPaths();
   std::vector<std::string> GetActualSearchPaths() const;
   static void GetModules(std::vector<std::string> &modules, const char *path);
   boost::shared_ptr<LoadedDeviceAdapter> LoadPluginLibrary(const char* libName);
   std::string FindInSearchPath(std::string filename);

   typedef std::map<std::string, MM::Device*> CDeviceMap;
   typedef std::vector<MM::Device*> DeviceVector;

   std::vector<std::string> preferredSearchPaths_;
   static std::vector<std::string> fallbackSearchPaths_;

   CDeviceMap devices_;
   DeviceVector devVector_;
   std::map< std::string, boost::shared_ptr<LoadedDeviceAdapter> > moduleMap_;

   // This is a temporary kludge. I've factored out LoadedDeviceAdapter from
   // PluginManager, but can't store a shared_ptr in MM::Device, so I need a
   // way to get the module from the device ptr, until we have a wrapper class
   // for attached ("loaded") devices. - Mark
   std::map< const MM::Device*, boost::shared_ptr<LoadedDeviceAdapter> > deviceModules_;
};

#endif //_PLUGIN_MANAGER_H_
