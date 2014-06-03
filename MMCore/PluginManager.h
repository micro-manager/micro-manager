///////////////////////////////////////////////////////////////////////////////
// FILE:          PluginManager.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCore
//-----------------------------------------------------------------------------
// DESCRIPTION:   Loading/unloading of plugins(module libraries) and creation
//                of devices.
//              
// COPYRIGHT:     University of California, San Francisco, 2006-2014
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

#include <boost/shared_ptr.hpp>
#include <boost/weak_ptr.hpp>
#include <map>
#include <string>
#include <vector>

class DeviceInstance;
class HubInstance;
class CMMCore;


/**
 * Manages the device collection. Responsible for handling plugin libraries
 * and device construction and destruction
 */
class CPluginManager /* final */
{
public:
   CPluginManager();
   ~CPluginManager();
   
   boost::shared_ptr<DeviceInstance> LoadDevice(CMMCore* core, const char* label, const char* moduleName, const char* deviceName, boost::shared_ptr<mm::logging::Logger> logger);
   void UnloadDevice(boost::shared_ptr<DeviceInstance> device);
   void UnloadAllDevices();

   boost::shared_ptr<DeviceInstance> GetDevice(const std::string& label) const throw (CMMError);
   boost::shared_ptr<DeviceInstance> GetDevice(const MM::Device* rawPtr) const throw (CMMError);
   std::vector<std::string> GetDeviceList(MM::DeviceType t = MM::AnyType) const;

   std::vector<std::string> GetLoadedPeripherals(const char* hubLabel) const;
   boost::shared_ptr<HubInstance> GetParentDevice(boost::shared_ptr<DeviceInstance> dev) const;

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
   MMThreadLock* getModuleLock(const boost::shared_ptr<DeviceInstance> pDev);
   bool removeModuleLock(const char* moduleName);

private:
   static std::vector<std::string> GetDefaultSearchPaths();
   std::vector<std::string> GetActualSearchPaths() const;
   static void GetModules(std::vector<std::string> &modules, const char *path);
   boost::shared_ptr<LoadedDeviceAdapter> LoadPluginLibrary(const char* libName);
   std::string FindInSearchPath(std::string filename);

   std::vector<std::string> preferredSearchPaths_;
   static std::vector<std::string> fallbackSearchPaths_;

   std::map< std::string, boost::shared_ptr<LoadedDeviceAdapter> > moduleMap_;

   // Store devices in an ordered container. We could use a map or hash map to
   // retrieve by name, but the number of devices is so small that it is not
   // known to be worth it.
   std::vector< std::pair<std::string, boost::shared_ptr<DeviceInstance> > > devices_;
   typedef std::vector< std::pair<std::string, boost::shared_ptr<DeviceInstance> > >::const_iterator
      DeviceConstIterator;
   typedef std::vector< std::pair<std::string, boost::shared_ptr<DeviceInstance> > >::iterator
      DeviceIterator;

   // Map raw device pointers to DeviceInstance objects, for those few places
   // where we need to retrieve device information from raw pointers.
   std::map< const MM::Device*, boost::weak_ptr<DeviceInstance> > deviceRawPtrIndex_;
};

#endif //_PLUGIN_MANAGER_H_
