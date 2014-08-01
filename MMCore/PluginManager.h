///////////////////////////////////////////////////////////////////////////////
// FILE:          PluginManager.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCore
//-----------------------------------------------------------------------------
// DESCRIPTION:   Loading/unloading of device adapter modules
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


#include "../MMDevice/DeviceThreads.h"

#include <boost/shared_ptr.hpp>
#include <boost/weak_ptr.hpp>

#include <map>
#include <string>
#include <vector>

class LoadedDeviceAdapter;


class CPluginManager /* final */
{
public:
   CPluginManager();
   ~CPluginManager();

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

   /**
    * Return a device adapter module, loading it if necessary
    */
   boost::shared_ptr<LoadedDeviceAdapter>
   GetDeviceAdapter(const std::string& moduleName);
   boost::shared_ptr<LoadedDeviceAdapter>
   GetDeviceAdapter(const char* moduleName);

private:
   static std::vector<std::string> GetDefaultSearchPaths();
   std::vector<std::string> GetActualSearchPaths() const;
   static void GetModules(std::vector<std::string> &modules, const char *path);
   std::string FindInSearchPath(std::string filename);

   std::vector<std::string> preferredSearchPaths_;
   static std::vector<std::string> fallbackSearchPaths_;

   std::map< std::string, boost::shared_ptr<LoadedDeviceAdapter> > moduleMap_;
};

#endif //_PLUGIN_MANAGER_H_
