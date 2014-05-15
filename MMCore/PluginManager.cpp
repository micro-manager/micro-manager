///////////////////////////////////////////////////////////////////////////////
// FILE:          PluginManager.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCore
//-----------------------------------------------------------------------------
// DESCRIPTION:   The interface to the MM core services
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
#include "Error.h"
#include "LibraryInfo/LibraryPaths.h"
#include "PluginManager.h"

#include <boost/algorithm/string.hpp>
#include <boost/make_shared.hpp>

#include <cstring>
#include <fstream>
#include <set>
using namespace std; // TODO Don't do this


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
   UnloadAllDevices();
}


/**
 * Search for a library and return its absolute path.
 *
 * If no match is found, the filename is returned as is.
 *
 * @param filename the name of the file to look up.
 */
string CPluginManager::FindInSearchPath(string filename)
{
   vector<string> searchPaths = GetActualSearchPaths();

   // look in search paths, if there are any
   if (searchPaths.size() == 0)
      return filename;

   vector<string>::const_iterator it;
   for (it = searchPaths.begin(); it != searchPaths.end(); it++) {
      string path(*it);
      #ifdef WIN32
      path += "\\" + filename + ".dll";
      #else
      path += "/" + filename;
      #endif

      // test whether it exists
      ifstream in(path.c_str(), ifstream::in);
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
 * @param shortName Simple module name without path, prefix, or suffix.
 * @return Module handle if successful, throws exception if not
 */
boost::shared_ptr<LoadedDeviceAdapter>
CPluginManager::LoadPluginLibrary(const char* shortName)
{
   if (!shortName)
      throw CMMError("Cannot load device adapter (null name)");
   if (shortName[0] == '\0')
      throw CMMError("Cannot load device adapter (empty name)");

   std::map< std::string, boost::shared_ptr<LoadedDeviceAdapter> >::iterator it =
      moduleMap_.find(shortName);
   if (it != moduleMap_.end())
      return it->second;

   string name(LIB_NAME_PREFIX);
   name += shortName;
   name += LIB_NAME_SUFFIX;
   name = FindInSearchPath(name);

   boost::shared_ptr<LoadedDeviceAdapter> module =
      boost::make_shared<LoadedDeviceAdapter>(shortName, name);
   moduleMap_[shortName] = module;
   return module;
}

/** 
 * Unload a module.
 */
void CPluginManager::UnloadPluginLibrary(const char* moduleName)
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


/**
 * Unloads the specified device from the core.
 * @param pDevice pointer to the device to unload
 */
void CPluginManager::UnloadDevice(boost::shared_ptr<MM::Device> pDevice)
{
   if (pDevice == 0)
      return;

   // Remove all references to the device first, in order to avoid dangling pointers
   // in the case where the device destructor throws.

   {
      // remove the entry from the device map
      CDeviceMap newDeviceMap;
      CDeviceMap::const_iterator it;
      for (it=devices_.begin(); it != devices_.end(); it++)
      {
         if (it->second != pDevice)
         {
            newDeviceMap[it->first] = it->second;
         }
      }
      devices_ = newDeviceMap;
   }

   {
      // remove the entry from the device set
      DeviceVector newDevVector;
      DeviceVector::const_iterator it;
      for (it = devVector_.begin(); it != devVector_.end(); it++)
      {
         if (*it != pDevice)
         {
            newDevVector.push_back(*it);
         }
      }
      devVector_ = newDevVector;
   }

   // release device resources
   pDevice->Shutdown();

   // delete device
   boost::shared_ptr<LoadedDeviceAdapter> module = deviceModules_[pDevice.get()];
   module->DeleteDevice(pDevice.get());

   deviceModules_.erase(pDevice.get());
}

/**
 * Unloads all devices from the core and resets all configuration data.
 */
void CPluginManager::UnloadAllDevices()
{
   DeviceVector unloadingSequence;
   {
      DeviceVector::reverse_iterator it;
      // do a two pass unloading so that USB ports and com ports unload last.
      // We plan unloading, and then carry it out, so as not to iterate
      // over a changing collection. Down with mutable collections.

      // first plan to unload all devices but serial ports
      for (it=devVector_.rbegin(); it != devVector_.rend(); it++)
         if (*it != 0 && (*it)->GetType() != MM::SerialDevice)
            unloadingSequence.push_back(*it);

      // then plan to unload remaining ports
      for (it=devVector_.rbegin(); it != devVector_.rend(); it++)
         if (*it != 0 && (*it)->GetType() == MM::SerialDevice)
            unloadingSequence.push_back(*it);
   }

   {
      DeviceVector::const_iterator it;
      for (it=unloadingSequence.begin(); it != unloadingSequence.end(); it++)
      {
         UnloadDevice(*it);
      }
   }
   devices_.clear();
   devVector_.clear();
}

/**
 * Loads the device specified with the input parameters.
 * @param label device label - string identifier used to access the device in the calling code
 * @param moduleName the name of the plugin library (dll)
 * @param deviceName the name of the device. The name must correspond to one of the names recognized
 *                   by the specific plugin library.
 * @return a pointer to the new device
 */
boost::shared_ptr<MM::Device>
CPluginManager::LoadDevice(const char* label, const char* moduleName, const char* deviceName)
{
   // check if the requested label is already taken
   CDeviceMap::const_iterator it;
   it = devices_.find(label);
   if (it != devices_.end())
   {
      if( NULL != it->second)
         throw CMMError("The specified label " + ToQuotedString(label) + " is already in use",
               MMERR_DuplicateLabel);
   }

   if (strlen(label) == 0)
      throw CMMError("Invalid label (empty string)", MMERR_InvalidLabel);
   
   boost::shared_ptr<LoadedDeviceAdapter> module = LoadPluginLibrary(moduleName);

   // instantiate the new device
   boost::shared_ptr<MM::Device> pDevice(module->CreateDevice(deviceName));
   if (pDevice == 0)
      throw CMMError("CreateDevice() failed for device " + ToQuotedString(deviceName),
            MMERR_CreateFailed);

   // Since it is up to the module to advertise the device with the correct
   // device type, we check that the created device is in fact of the expected
   // type.
   int typeInt = MM::UnknownType;
   module->GetDeviceType(deviceName, &typeInt);
   if (typeInt != MM::UnknownType)
   {
      MM::DeviceType actualType = pDevice->GetType();
      if (actualType != static_cast<MM::DeviceType>(typeInt))
      {
         module->DeleteDevice(pDevice.get());
         throw CMMError("Tried to load device " + ToQuotedString(deviceName) +
               " of type " + ToString(typeInt) +
               " from module " + ToQuotedString(moduleName) +
               " but got a device of type " + ToString(actualType));
      }
   }

   char descr[MM::MaxStrLength] = "N/A";
   module->GetDeviceDescription(deviceName, descr, MM::MaxStrLength);

   // make sure that each device carries a reference to the module it belongs to!!!
   deviceModules_[pDevice.get()] = module;
   pDevice->SetLabel(label);
   pDevice->SetModuleName(moduleName);
   pDevice->SetDescription(descr);

   // assign label
   devices_[label] = pDevice;
   devVector_.push_back(pDevice);
   deviceSharedPtrs_[pDevice.get()] = pDevice;

   return pDevice;
}

/**
 * Obtains the device corresponding to the label
 * @param label device label
 * @return pointer to the device or 0 if the label is not recognized
 */
boost::shared_ptr<MM::Device>
CPluginManager::GetDevice(const std::string& label) const throw (CMMError)
{
   CDeviceMap::const_iterator it;
   it = devices_.find(label);
   if (it == devices_.end() || it->second == 0)
   {
      string msg = string("No device with label \"") + label + "\"";
      throw CMMError(msg.c_str(), MMERR_InvalidLabel);
   }

   return it->second;
}

boost::shared_ptr<MM::Device>
CPluginManager::GetDevice(const MM::Device* rawPtr) const throw (CMMError)
{
   typedef std::map< const MM::Device*, boost::weak_ptr<MM::Device> >::const_iterator Iterator;
   Iterator it = deviceSharedPtrs_.find(rawPtr);
   if (it == deviceSharedPtrs_.end())
      throw CMMError("Invalid device pointer");
   return it->second.lock();
}


/**
 * Obtains the label corresponding to the device pointer
 * @param pDevice device pointer
 * @param label device label
 * @return true on success, false if the device pointer does not correspond
 *         to the currently loaded device
 */
string CPluginManager::GetDeviceLabel(const MM::Device& device) const
{
   CDeviceMap::const_iterator it;
   for (it=devices_.begin(); it != devices_.end(); it++)
   {
      if (it->second.get() == &device)
      {
         return it->first;
      }
   }
   
   throw CMMError("Unexpected device instance encountered", MMERR_UnexpectedDevice);
}

/**
 * Obtains the list of labels for all currently loaded devices of the specific type.
 * Use type MM::AnyDevice to obtain labels for the entire system.
 * @return vector of device labels
 * @param type - device type
 */
vector<string> CPluginManager::GetDeviceList(MM::DeviceType type) const
{
   vector<string> labels;
   DeviceVector::const_iterator it;
   for (it = devVector_.begin(); it != devVector_.end(); ++it)
   {
      char buf[MM::MaxStrLength];
      if (type == MM::AnyType || type == (*it)->GetType())
      {
         (*it)->GetLabel(buf);
         labels.push_back(buf);
      }
   }
   return labels;
}

/**
 * Returns parent Hub device or null if there is none.
 * Makes sure that returned hub belongs to the same module (library)
 * as the device dev.
 */
boost::shared_ptr<MM::Hub>
CPluginManager::GetParentDevice(const MM::Device& dev) const
{
   char parentLabel[MM::MaxStrLength];
   dev.GetParentID(parentLabel);
   char module[MM::MaxStrLength] = "";
   dev.GetModuleName(module);

   if (strlen(parentLabel) == 0)
   {
      // no parent specified, but we will try to infer one anyway
      vector<string> hubList = GetDeviceList(MM::HubDevice);
      boost::shared_ptr<MM::Hub> parentHub;
      for (vector<string>::size_type i = 0; i<hubList.size(); i++)
      {
         boost::shared_ptr<MM::Hub> pHub = boost::static_pointer_cast<MM::Hub>(devices_.find(hubList[i])->second);
         char hubModule[MM::MaxStrLength];
         pHub->GetModuleName(hubModule);
         if (strlen(module) > 0 && strncmp(module, hubModule, MM::MaxStrLength) == 0)
         {
            if (parentHub == 0)
               parentHub = pHub;
            else
               // XXX This should be a throw
               return boost::shared_ptr<MM::Hub>(); // more than one hub matches, so we can't really tell
         }
      }
      return parentHub;
   }
   else
   {
      // parent label is specified, we'll try to get the actual device
      CDeviceMap::const_iterator it;
      it = devices_.find(parentLabel);
      if (it == devices_.end() || it->second == 0 || it->second->GetType() != MM::HubDevice)
         // XXX This should be a throw
         return boost::shared_ptr<MM::Hub>(); // no such device
      else
      {
         // make sure that hub belongs to the same library
         // (this is to avoid using dynamic_cast<> in device code)
         boost::shared_ptr<MM::Hub> pHub = boost::static_pointer_cast<MM::Hub>(it->second);
         char hubModule[MM::MaxStrLength] = "";
         pHub->GetModuleName(hubModule);
         if (strlen(module) > 0 && strncmp(module, hubModule, MM::MaxStrLength) == 0)
            return pHub;
         else
            // XXX This sould be a throw
            return boost::shared_ptr<MM::Hub>(); // the hub is from a different library, so it won;t work
      }
   }
}


/**
 * Obtains the list of labels for all currently loaded devices of the specific type.
 * Use type MM::AnyDevice to obtain labels for the entire system.
 * @return vector of device labels
 * @param type - device type
 */
vector<string> CPluginManager::GetLoadedPeripherals(const char* label) const
{
   vector<string> labels;

   // get hub
   boost::shared_ptr<MM::Device> pDev;
   try
   {
      pDev = GetDevice(label);
      if (pDev->GetType() != MM::HubDevice)
         return labels;
   }
   catch (...)
   {
      return labels;
   }

   DeviceVector::const_iterator it;
   for (it = devVector_.begin(); it != devVector_.end(); it++)
   {
      char parentID[MM::MaxStrLength];
      (*it)->GetParentID(parentID);
      if (strncmp(label, parentID, MM::MaxStrLength) == 0)
      {
         char buf[MM::MaxStrLength];
         (*it)->GetLabel(buf);
         labels.push_back(buf);
      }
   }

   return labels;
}


void CPluginManager::AddLegacyFallbackSearchPath(const std::string& path)
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
static std::string GetDirName(const std::string& path)
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


std::vector<std::string> CPluginManager::GetDefaultSearchPaths()
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


std::vector<std::string> CPluginManager::GetActualSearchPaths() const
{
   std::vector<std::string> paths(preferredSearchPaths_);
   paths.insert(paths.end(), fallbackSearchPaths_.begin(), fallbackSearchPaths_.end());
   return paths;
}


/**
 * List all modules (device libraries) at a given path.
 */
void CPluginManager::GetModules(vector<string> &modules, const char* searchPath)
{

#ifdef WIN32
   string path = searchPath;
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
         string strippedName = std::string(moduleFile.name).substr(strlen(LIB_NAME_PREFIX));
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
            string strippedName = std::string(dir_name).substr(strlen(LIB_NAME_PREFIX));
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
vector<string> CPluginManager::GetAvailableDeviceAdapters()
{
   vector<string> searchPaths = GetActualSearchPaths();

   vector<string> modules;

   for (vector<string>::const_iterator it = searchPaths.begin(), end = searchPaths.end(); it != end; ++it)
      GetModules(modules, it->c_str());

   // Check for duplicates
   // XXX Is this the right place to be doing this checking? Shouldn't it be an
   // error to have duplicates even if we're not listing all libraries?
   set<string> moduleSet;
   for (vector<string>::const_iterator it = modules.begin(), end = modules.end(); it != end; ++it) {
      if (moduleSet.count(*it)) {
         string msg("Duplicate libraries found with name \"" + *it + "\"");
         throw CMMError(msg.c_str(), DEVICE_DUPLICATE_LIBRARY);
      }
   }

   return modules;
}


std::vector<std::string> CPluginManager::GetModulesInLegacyFallbackSearchPaths()
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
   set<string> moduleSet;
   for (vector<string>::const_iterator it = modules.begin(), end = modules.end(); it != end; ++it) {
      if (moduleSet.count(*it)) {
         std::string msg("Duplicate libraries found with name \"" + *it + "\"");
         throw CMMError(msg.c_str(), DEVICE_DUPLICATE_LIBRARY);
      }
   }

   return modules;
}

/**
 * List all available devices in the specified module.
 */
vector<string> CPluginManager::GetAvailableDevices(const char* moduleName) throw (CMMError)
{
   vector<string> devices;
   boost::shared_ptr<LoadedDeviceAdapter> module = LoadPluginLibrary(moduleName);

   try
   {
      unsigned numDev = module->GetNumberOfDevices();
      for (unsigned i=0; i<numDev; i++)
      {
         char deviceName[MM::MaxStrLength];
         if (module->GetDeviceName(i, deviceName, MM::MaxStrLength))
            devices.push_back(deviceName);
      }
   }
   catch (CMMError& err)
   {
      throw CMMError("Cannot get available devices from module " + ToString(moduleName),
            MMERR_GENERIC, err);
   }
   
   return devices;
}

/**
 * List all available devices in the specified module.
 */
vector<string> CPluginManager::GetAvailableDeviceDescriptions(const char* moduleName) throw (CMMError)
{
   vector<string> descriptions;
   boost::shared_ptr<LoadedDeviceAdapter> module = LoadPluginLibrary(moduleName);

   try
   {
      unsigned numDev = module->GetNumberOfDevices();
      for (unsigned i=0; i<numDev; i++)
      {
         char deviceName[MM::MaxStrLength];
         if (module->GetDeviceName(i, deviceName, MM::MaxStrLength))
         {
            char deviceDescr[MM::MaxStrLength];
            if (module->GetDeviceDescription(deviceName, deviceDescr, MM::MaxStrLength))
               descriptions.push_back(deviceDescr);
            else
               descriptions.push_back("N/A");
         }
      }
   }
   catch (CMMError& err)
   {
      throw CMMError("Cannot get device descriptions from module " + ToString(moduleName),
            MMERR_GENERIC, err);
   }
   
   return descriptions;
}

/**
 * List all device types in the specified module.
 */
vector<long> CPluginManager::GetAvailableDeviceTypes(const char* moduleName) throw (CMMError)
{
   vector<long> types;
   boost::shared_ptr<LoadedDeviceAdapter> module = LoadPluginLibrary(moduleName);

   try
   {
      unsigned numDev = module->GetNumberOfDevices();

      for (unsigned i=0; i<numDev; i++)
      {
         char deviceName[MM::MaxStrLength];
         if (!module->GetDeviceName(i, deviceName, MM::MaxStrLength))
         {
            types.push_back((long)MM::AnyType);
            continue;
         }

         int typeInt = static_cast<int>(MM::UnknownType);
         if (module->GetDeviceType(deviceName, &typeInt) &&
               static_cast<MM::DeviceType>(typeInt) != MM::UnknownType)
         {
            types.push_back(typeInt);
            continue;
         }
      }
   }
   catch (CMMError& err)
   {
      throw CMMError("Cannot get device types from module " + ToString(moduleName),
            MMERR_GENERIC, err);
   }
   
   return types;
}


/**
 * Returns appropriate module-level lock for a given device.
 * If a lock does not exist for a particular device, returns 0
 */
MMThreadLock* CPluginManager::getModuleLock(boost::shared_ptr<MM::Device> pDev)
{
   if (pDev == 0)
      return 0;

   std::map< const MM::Device*, boost::shared_ptr<LoadedDeviceAdapter> >::iterator it =
      deviceModules_.find(pDev.get());
   if (it == deviceModules_.end())
      return 0;
   return it->second->GetLock();
}

/**
 * Removes module lock if it exists.
 * This method is used by devices to defeat module thread locking in MMCore.
 * If device implements its own thread safety mechanism, then it makes sense to
 * defeat the system level locking.
 * XXX I don't think this should be exposed. It will couple the device
 * implementations to the core implementation so tightly that we're going to
 * end up with a hard-to-maintain situation. Keeping it for now. - Mark
 */
bool CPluginManager::removeModuleLock(const char* moduleName)
{
   std::map< std::string, boost::shared_ptr<LoadedDeviceAdapter> >::iterator it =
      moduleMap_.find(moduleName);
   if (it == moduleMap_.end())
      return false;

   it->second->RemoveLock();
   return true;
}
