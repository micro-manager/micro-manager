///////////////////////////////////////////////////////////////////////////////
// FILE:          PluginManager.cpp
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

#ifdef WIN32
   #include <windows.h>
   #include <io.h>
#else
   #include <sys/types.h>
   #include <dirent.h>
#endif // WIN32

#include "../MMDevice/ModuleInterface.h"
#include "CoreUtils.h"
#include "Devices/DeviceInstance.h"
#include "Devices/HubInstance.h"
#include "Error.h"
#include "LibraryInfo/LibraryPaths.h"
#include "LoadableModules/LoadedDeviceAdapter.h"
#include "PluginManager.h"

#include <boost/algorithm/string.hpp>
#include <boost/make_shared.hpp>

#include <cstring>
#include <fstream>
#include <set>
#include <string>
#include <vector>


#ifdef WIN32
const char* const LIB_NAME_PREFIX = "mmgr_dal_";
#else
const char* const LIB_NAME_PREFIX = "libmmgr_dal_";
#endif

#ifdef linux
const char* const LIB_NAME_SUFFIX = ".so.0";
#else
const char* const LIB_NAME_SUFFIX = "";
#endif

///////////////////////////////////////////////////////////////////////////////
// CPluginManager class
// --------------------

std::vector<std::string> CPluginManager::fallbackSearchPaths_;

CPluginManager::CPluginManager()
{
   const std::vector<std::string> paths = GetDefaultSearchPaths();
   SetSearchPaths(paths.begin(), paths.end());
}

CPluginManager::~CPluginManager()
{
}


/**
 * Search for a library and return its absolute path.
 *
 * If no match is found, the filename is returned as is.
 *
 * @param filename the name of the file to look up.
 */
std::string
CPluginManager::FindInSearchPath(std::string filename)
{
   std::vector<std::string> searchPaths = GetActualSearchPaths();

   // look in search paths, if there are any
   if (searchPaths.size() == 0)
      return filename;

   std::vector<std::string>::const_iterator it;
   for (it = searchPaths.begin(); it != searchPaths.end(); it++) {
      std::string path(*it);
      #ifdef WIN32
      path += "\\" + filename + ".dll";
      #else
      path += "/" + filename;
      #endif

      // test whether it exists
      std::ifstream in(path.c_str(), std::ifstream::in);
      in.close();

      if (!in.fail())
         // we found it!
         return path;
   }

   // not found!
   return filename;
}

/** 
 * Load a plugin library.
 *
 * Since we want to have a consistent plugin discovery/loading mechanism,
 * the search path -- if specified explicitly -- is traversed.
 *
 * This has to be done so that users do not have to make sure that
 * DYLD_LIBRARY_PATH, LD_LIBRARY_PATH or PATH and java.library.path are in
 * sync and include the correct paths.
 *
 * However, the OS-dependent default search path is still searched in the case
 * where the library is not found in our list of search paths. (XXX This should
 * perhaps change, to avoid surprises.)
 *
 * @param moduleName Simple module name without path, prefix, or suffix.
 */
boost::shared_ptr<LoadedDeviceAdapter>
CPluginManager::GetDeviceAdapter(const std::string& moduleName)
{
   if (moduleName.empty())
   {
      throw CMMError("Empty device adapter module name");
   }

   std::map< std::string, boost::shared_ptr<LoadedDeviceAdapter> >::iterator it =
      moduleMap_.find(moduleName);
   if (it != moduleMap_.end())
   {
      return it->second;
   }

   std::string filename(LIB_NAME_PREFIX);
   filename += moduleName;
   filename += LIB_NAME_SUFFIX;
   filename = FindInSearchPath(filename);

   boost::shared_ptr<LoadedDeviceAdapter> module =
      boost::make_shared<LoadedDeviceAdapter>(moduleName, filename);
   moduleMap_[moduleName] = module;
   return module;
}

boost::shared_ptr<LoadedDeviceAdapter>
CPluginManager::GetDeviceAdapter(const char* moduleName)
{
   if (!moduleName)
   {
      throw CMMError("Null device adapter module name");
   }
   return GetDeviceAdapter(std::string(moduleName));
}

/** 
 * Unload a module.
 */
void
CPluginManager::UnloadPluginLibrary(const char* moduleName)
{
   std::map< std::string, boost::shared_ptr<LoadedDeviceAdapter> >::iterator it =
      moduleMap_.find(moduleName);
   if (it == moduleMap_.end())
      throw CMMError("No device adapter named " + ToQuotedString(moduleName));

   try
   {
      it->second->Unload();
   }
   catch (const CMMError& e)
   {
      throw CMMError("Cannot unload device adapter " + ToQuotedString(moduleName), e);
   }
}


void
CPluginManager::AddLegacyFallbackSearchPath(const std::string& path)
{
   // TODO Should normalize slashes and cases (depending on platform) before
   // comparing.

   // When this function is used, the instance search path
   // (preferredSearchPaths_) remains equal to the default. Do not add
   // duplicate paths.
   std::vector<std::string> defaultPaths(GetDefaultSearchPaths());
   if (std::find(defaultPaths.begin(), defaultPaths.end(), path) !=
         defaultPaths.end())
      return;

   // Again, do not add duplicate paths.
   if (std::find(fallbackSearchPaths_.begin(), fallbackSearchPaths_.end(), path) !=
         fallbackSearchPaths_.end())
      return;

   fallbackSearchPaths_.push_back(path);
}


// TODO Use Boost.Filesystem instead of this.
// This stop-gap implementation makes the assumption that the argument is in
// the format that could be returned from MMCorePrivate::GetPathOfThisModule()
// (e.g. no trailing slashes; real filename present).
static std::string
GetDirName(const std::string& path)
{
#ifdef WIN32
   const char* pathSep = "\\/";
#else
   const char* pathSep = "/";
#endif

   size_t slashPos = path.find_last_of(pathSep);
   if (slashPos == std::string::npos)
   {
      // No slash in path, but we assume it is a real filename
      return ".";
   }
   if (slashPos == 0 && path[0] == '/') // Unix root dir
      return "/";
   return path.substr(0, slashPos);
}


std::vector<std::string>
CPluginManager::GetDefaultSearchPaths()
{
   static std::vector<std::string> paths;
   static bool initialized = false;
   if (!initialized)
   {
      try
      {
         std::string coreModulePath = MMCorePrivate::GetPathOfThisModule();
         std::string coreModuleDir = GetDirName(coreModulePath);
         paths.push_back(coreModuleDir);
      }
      catch (const CMMError&)
      {
         // TODO Log warning.
      }

      initialized = true;
   }
   return paths;
}


std::vector<std::string>
CPluginManager::GetActualSearchPaths() const
{
   std::vector<std::string> paths(preferredSearchPaths_);
   paths.insert(paths.end(), fallbackSearchPaths_.begin(), fallbackSearchPaths_.end());
   return paths;
}


/**
 * List all modules (device libraries) at a given path.
 */
void
CPluginManager::GetModules(std::vector<std::string> &modules, const char* searchPath)
{

#ifdef WIN32
   std::string path = searchPath;
   path += "\\";
   path += LIB_NAME_PREFIX;
   path += "*.dll";

   // Use _findfirst(), _findnext(), and _findclose() from Microsoft C library
   struct _finddata_t moduleFile;
   intptr_t hSearch;
   hSearch = _findfirst(path.c_str(), &moduleFile);
   if (hSearch != -1L) // Match found
   {
      do {
         // remove prefix and suffix
         std::string strippedName = std::string(moduleFile.name).substr(strlen(LIB_NAME_PREFIX));
         strippedName = strippedName.substr(0, strippedName.find_first_of("."));
         modules.push_back(strippedName);
      } while (_findnext(hSearch, &moduleFile) == 0);

      _findclose(hSearch);
   }
#else // UNIX
   DIR *dp;
   struct dirent *dirp;
   if ((dp = opendir(searchPath)) != NULL)
   {
      while ((dirp = readdir(dp)) != NULL)
      {
         const char* dir_name = dirp->d_name;
         if (strncmp(dir_name, LIB_NAME_PREFIX, strlen(LIB_NAME_PREFIX)) == 0
#ifdef linux
             && strncmp(&dir_name[strlen(dir_name) - strlen(LIB_NAME_SUFFIX)], LIB_NAME_SUFFIX, strlen(LIB_NAME_SUFFIX)) == 0)
#else // OS X
             && strchr(&dir_name[strlen(dir_name) - strlen(LIB_NAME_SUFFIX)], '.') == NULL)
#endif
         {
            // remove prefix and suffix
            std::string strippedName = std::string(dir_name).substr(strlen(LIB_NAME_PREFIX));
            strippedName = strippedName.substr(0, strippedName.length() - strlen(LIB_NAME_SUFFIX));
            modules.push_back(strippedName);
         }
      }
      closedir(dp);
   }
#endif // UNIX
}


/**
 * List all modules (device libraries) in all search paths.
 */
std::vector<std::string>
CPluginManager::GetAvailableDeviceAdapters()
{
   std::vector<std::string> searchPaths = GetActualSearchPaths();

   std::vector<std::string> modules;

   for (std::vector<std::string>::const_iterator it = searchPaths.begin(), end = searchPaths.end(); it != end; ++it)
      GetModules(modules, it->c_str());

   // Check for duplicates
   // XXX Is this the right place to be doing this checking? Shouldn't it be an
   // error to have duplicates even if we're not listing all libraries?
   std::set<std::string> moduleSet;
   for (std::vector<std::string>::const_iterator it = modules.begin(), end = modules.end(); it != end; ++it) {
      if (moduleSet.count(*it)) {
         std::string msg("Duplicate libraries found with name \"" + *it + "\"");
         throw CMMError(msg.c_str(), DEVICE_DUPLICATE_LIBRARY);
      }
   }

   return modules;
}


std::vector<std::string>
CPluginManager::GetModulesInLegacyFallbackSearchPaths()
{
   // Search in default search paths and any that were added to the legacy path
   // list.
   std::vector<std::string> paths(GetDefaultSearchPaths());
   for (std::vector<std::string>::const_iterator it = fallbackSearchPaths_.begin(),
         end = fallbackSearchPaths_.end();
         it != end; ++it)
   {
      if (std::find(paths.begin(), paths.end(), *it) == paths.end())
         paths.push_back(*it);
   }

   std::vector<std::string> modules;
   for (std::vector<std::string>::const_iterator it = paths.begin(), end = paths.end();
         it != end; ++it)
      GetModules(modules, it->c_str());

   // Check for duplicates
   // XXX Is this the right place to be doing this checking? Shouldn't it be an
   // error to have duplicates even if we're not listing all libraries?
   std::set<std::string> moduleSet;
   for (std::vector<std::string>::const_iterator it = modules.begin(), end = modules.end(); it != end; ++it) {
      if (moduleSet.count(*it)) {
         std::string msg("Duplicate libraries found with name \"" + *it + "\"");
         throw CMMError(msg.c_str(), DEVICE_DUPLICATE_LIBRARY);
      }
   }

   return modules;
}
