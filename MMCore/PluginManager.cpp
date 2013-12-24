///////////////////////////////////////////////////////////////////////////////
// FILE:          PluginManager.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCore
//-----------------------------------------------------------------------------
// DESCRIPTION:   The interface to the MM core services
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

#ifdef WIN32
   #include <windows.h>
   #include <io.h>
#else
   #include <sys/types.h>
   #include <dirent.h>
#endif // WIN32

#include "PluginManager.h"

#include "../MMDevice/ModuleInterface.h"
#include "CoreUtils.h"
#include "Error.h"

#include <boost/make_shared.hpp>

#include <sstream>
#include <iostream>
#include <fstream>
#include <algorithm>
using namespace std;

#include <boost/algorithm/string.hpp>

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

CPluginManager::CPluginManager() 
{
}

CPluginManager::~CPluginManager()
{
   UnloadAllDevices();
   DeleteModuleLocks();
}


std::vector<std::string> CPluginManager::searchPaths_;
std::map< std::string, boost::shared_ptr<LoadedModule> > CPluginManager::moduleMap_;
std::map<std::string, MMThreadLock*> CPluginManager::moduleLocks_;

/**
 * Add search path.
 *
 * @param path the search path to be added.
 */
void CPluginManager::AddSearchPath(string path)
{
   searchPaths_.push_back(path);
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
   // look in search paths, if there are any
   if (searchPaths_.size() == 0)
      return filename;

   vector<string>::const_iterator it;
   for (it = searchPaths_.begin(); it != searchPaths_.end(); it++) {
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
boost::shared_ptr<LoadedModule>
CPluginManager::LoadPluginLibrary(const char* shortName)
{
   std::map< std::string, boost::shared_ptr<LoadedModule> >::iterator it =
      moduleMap_.find(shortName);
   if (it != moduleMap_.end())
      return it->second;

   string name(LIB_NAME_PREFIX);
   name += shortName;
   name += LIB_NAME_SUFFIX;
   name = FindInSearchPath(name);

   boost::shared_ptr<LoadedModule> mod;
   try
   {
      mod = boost::make_shared<LoadedModule>(name);
   }
   catch (const CMMError& e)
   {
      throw CMMError("Cannot load device adapter " + ToQuotedString(shortName), e);
   }

   moduleMap_[shortName] = mod;
   CreateModuleLock(shortName);
   return mod;
}

/** 
 * Unload a module.
 */
void CPluginManager::UnloadPluginLibrary(const char* moduleName)
{
   std::map< std::string, boost::shared_ptr<LoadedModule> >::iterator it =
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
 * Verifies that plugin interface/device version matches the core version expectations.
 * Throws if there is a mismatch or if the version info is not available.
*/
void CPluginManager::CheckVersion(boost::shared_ptr<LoadedModule> module)
{
   // module version
   fnGetModuleVersion hGetModuleVersionFunc = (fnGetModuleVersion) module->GetFunction("GetModuleVersion");

   long moduleVersion = hGetModuleVersionFunc();
   if (moduleVersion != MODULE_INTERFACE_VERSION)
   {
      ostringstream errTxt;
      errTxt << "Module interface version: core=" << MODULE_INTERFACE_VERSION << ", library=" << moduleVersion;  
      throw CMMError(errTxt.str().c_str(), MMERR_ModuleVersionMismatch);
   }

   // device version
   fnGetDeviceInterfaceVersion hGetDeviceInterfaceVersionFunc = (fnGetDeviceInterfaceVersion) module->GetFunction("GetDeviceInterfaceVersion");
   long deviceVersion = hGetDeviceInterfaceVersionFunc();
   if (deviceVersion != DEVICE_INTERFACE_VERSION)
   {
      ostringstream errTxt;
      errTxt << "Device interface version: core=" << DEVICE_INTERFACE_VERSION << ", library=" << deviceVersion;  
      throw CMMError(errTxt.str().c_str(), MMERR_DeviceVersionMismatch);
   }
}

/**
 * Unloads the specified device from the core.
 * @param pDevice pointer to the device to unload
 */
void CPluginManager::UnloadDevice(MM::Device* pDevice)
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

   boost::shared_ptr<LoadedModule> module = deviceModules_[pDevice];

   // obtain handle to the DeleteDevice method
   fnDeleteDevice hDeleteDeviceFunc = (fnDeleteDevice) module->GetFunction("DeleteDevice");

   // release device resources
   pDevice->Shutdown();

   // delete device
   hDeleteDeviceFunc(pDevice);

   deviceModules_.erase(pDevice);
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
MM::Device* CPluginManager::LoadDevice(const char* label, const char* moduleName, const char* deviceName)
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
   
   boost::shared_ptr<LoadedModule> module = LoadPluginLibrary(moduleName);

   fnCreateDevice hCreateDeviceFunc(0);
   fnDeleteDevice hDeleteDeviceFunc(0);
   fnGetDeviceType hGetDeviceType(0);
   fnGetDeviceDescription hGetDeviceDescription(0);
   try
   {
      CheckVersion(module);
      hCreateDeviceFunc = (fnCreateDevice) module->GetFunction("CreateDevice");
      hDeleteDeviceFunc = (fnDeleteDevice) module->GetFunction("DeleteDevice");
      hGetDeviceType = (fnGetDeviceType) module->GetFunction("GetDeviceType");
      hGetDeviceDescription = (fnGetDeviceDescription) module->GetFunction("GetDeviceDescription");
   }
   catch (const CMMError& e)
   {
      throw CMMError("Cannot load device " + ToQuotedString(deviceName) +
            " as " + ToQuotedString(label), e);
   }

   // instantiate the new device
   MM::Device* pDevice = hCreateDeviceFunc(deviceName);
   if (pDevice == 0)
      throw CMMError("CreateDevice() failed for device " + ToQuotedString(deviceName),
            MMERR_CreateFailed);

   // Since it is up to the module to advertise the device with the correct
   // device type, we check that the created device is in fact of the expected
   // type.
   int typeInt = MM::UnknownType;
   hGetDeviceType(deviceName, &typeInt);
   if (typeInt != MM::UnknownType)
   {
      MM::DeviceType actualType = pDevice->GetType();
      if (actualType != static_cast<MM::DeviceType>(typeInt))
      {
         hDeleteDeviceFunc(pDevice);
         throw CMMError("Tried to load device " + ToQuotedString(deviceName) +
               " of type " + ToString(typeInt) +
               " from module " + ToQuotedString(moduleName) +
               " but got a device of type " + ToString(actualType));
      }
   }

   char descr[MM::MaxStrLength] = "N/A";
   hGetDeviceDescription(deviceName, descr, MM::MaxStrLength);

   // make sure that each device carries a reference to the module it belongs to!!!
   deviceModules_[pDevice] = module;
   pDevice->SetLabel(label);
   pDevice->SetModuleName(moduleName);
   pDevice->SetDescription(descr);

   // assign label
   devices_[label] = pDevice;
   devVector_.push_back(pDevice);

   return pDevice;
}

/**
 * Obtains the device corresponding to the label
 * @param label device label
 * @return pointer to the device or 0 if the label is not recognized
 */
MM::Device* CPluginManager::GetDevice(const char* label) const throw (CMMError)
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
      if (it->second == &device)
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
MM::Hub* CPluginManager::GetParentDevice(const MM::Device& dev) const
{
   char parentLabel[MM::MaxStrLength];
   dev.GetParentID(parentLabel);
   char module[MM::MaxStrLength] = "";
   dev.GetModuleName(module);

   if (strlen(parentLabel) == 0)
   {
      // no parent specified, but we will try to infer one anyway
      vector<string> hubList = GetDeviceList(MM::HubDevice);
      MM::Hub* parentHub = 0;
      for (vector<string>::size_type i = 0; i<hubList.size(); i++)
      {
         MM::Hub* pHub = static_cast<MM::Hub*>(devices_.find(hubList[i])->second);
         char hubModule[MM::MaxStrLength];
         pHub->GetModuleName(hubModule);
         if (strlen(module) > 0 && strncmp(module, hubModule, MM::MaxStrLength) == 0)
         {
            if (parentHub == 0)
               parentHub = pHub;
            else
               return 0; // more than one hub matches, so we can't really tell
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
         return 0; // no such device
      else
      {
         // make sure that hub belongs to the same library
         // (this is to avoid using dynamic_cast<> in device code)
         MM::Hub* pHub = static_cast<MM::Hub*>(it->second);
         char hubModule[MM::MaxStrLength] = "";
         pHub->GetModuleName(hubModule);
         if (strlen(module) > 0 && strncmp(module, hubModule, MM::MaxStrLength) == 0)
            return pHub;
         else
            return 0; // the hub is from a different library, so it won;t work
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
   MM::Device* pDev = 0;
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
 *
 * Duplicates may be included if they are in different directories.
 */
vector<string> CPluginManager::GetModules()
{
   vector<string> modules;
   vector<string>::const_iterator it;

   for (it = searchPaths_.begin(); it != searchPaths_.end(); it++)
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

/**
 * List all available devices in the specified module.
 */
vector<string> CPluginManager::GetAvailableDevices(const char* moduleName) throw (CMMError)
{
   vector<string> devices;
   boost::shared_ptr<LoadedModule> module = LoadPluginLibrary(moduleName);
   CheckVersion(module);

   fnGetNumberOfDevices hGetNumberOfDevices(0);
   fnGetDeviceName hGetDeviceName(0);
   fnInitializeModuleData hInitializeModuleData(0);

   try
   {
      // initalize module data
      hInitializeModuleData = (fnInitializeModuleData) module->GetFunction("InitializeModuleData");
      hInitializeModuleData();

      hGetNumberOfDevices = (fnGetNumberOfDevices) module->GetFunction("GetNumberOfDevices");
      hGetDeviceName = (fnGetDeviceName) module->GetFunction("GetDeviceName");

      unsigned numDev = hGetNumberOfDevices();
      for (unsigned i=0; i<numDev; i++)
      {
         char deviceName[MM::MaxStrLength];
         if (hGetDeviceName(i, deviceName, MM::MaxStrLength))
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
   boost::shared_ptr<LoadedModule> module = LoadPluginLibrary(moduleName);
   CheckVersion(module);

   fnGetNumberOfDevices hGetNumberOfDevices(0);
   fnGetDeviceDescription hGetDeviceDescription(0);
   fnInitializeModuleData hInitializeModuleData(0);
   fnGetDeviceName hGetDeviceName(0);

   try
   {
      hInitializeModuleData = (fnInitializeModuleData) module->GetFunction("InitializeModuleData");
      hInitializeModuleData();

      hGetNumberOfDevices = (fnGetNumberOfDevices) module->GetFunction("GetNumberOfDevices");
      hGetDeviceDescription = (fnGetDeviceDescription) module->GetFunction("GetDeviceDescription");
      hGetDeviceName = (fnGetDeviceName) module->GetFunction("GetDeviceName");

      unsigned numDev = hGetNumberOfDevices();
      for (unsigned i=0; i<numDev; i++)
      {
         char deviceName[MM::MaxStrLength];
         if (hGetDeviceName(i, deviceName, MM::MaxStrLength))
         {
            char deviceDescr[MM::MaxStrLength];
            if (hGetDeviceDescription(deviceName, deviceDescr, MM::MaxStrLength))
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
   boost::shared_ptr<LoadedModule> module = LoadPluginLibrary(moduleName);
   CheckVersion(module);

   fnGetNumberOfDevices hGetNumberOfDevices(0);
   fnInitializeModuleData hInitializeModuleData(0);

   try
   {
      hInitializeModuleData = (fnInitializeModuleData) module->GetFunction("InitializeModuleData");
      hInitializeModuleData();

      hGetNumberOfDevices = (fnGetNumberOfDevices) module->GetFunction("GetNumberOfDevices");

      fnDeleteDevice hDeleteDeviceFunc = (fnDeleteDevice) module->GetFunction("DeleteDevice");
      fnCreateDevice hCreateDeviceFunc = (fnCreateDevice) module->GetFunction("CreateDevice");
      fnGetDeviceName hGetDeviceName = (fnGetDeviceName) module->GetFunction("GetDeviceName");
      fnGetDeviceType hGetDeviceType = (fnGetDeviceType) module->GetFunction("GetDeviceType");
      
      unsigned numDev = hGetNumberOfDevices();

      for (unsigned i=0; i<numDev; i++)
      {
         char deviceName[MM::MaxStrLength];
         if (!hGetDeviceName(i, deviceName, MM::MaxStrLength))
         {
            types.push_back((long)MM::AnyType);
            continue;
         }

         int typeInt = static_cast<int>(MM::UnknownType);
         if (hGetDeviceType(deviceName, &typeInt) &&
               static_cast<MM::DeviceType>(typeInt) != MM::UnknownType)
         {
            types.push_back(typeInt);
            continue;
         }

         // instantiate the device
         MM::Device* pDevice = hCreateDeviceFunc(deviceName);
         if (pDevice == 0)
            types.push_back((long)MM::AnyType);
         else
         {
            types.push_back((long)pDevice->GetType());


            // release device resources
            //pDevice->Shutdown();
            
            // delete device
            hDeleteDeviceFunc(pDevice);
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
* Creates a thread lock for a particular module.
*/
void CPluginManager::CreateModuleLock(const char* moduleName)
{
	CModuleLockMap::iterator it2 = moduleLocks_.find(moduleName);
	if (it2 == moduleLocks_.end())
	{
		moduleLocks_[moduleName] = new MMThreadLock;
	}
}


/**
 * Deletes all module locks.
 * Should be called only on exit, when all devices are inactive
 */
void CPluginManager::DeleteModuleLocks()
{
   // delete all module locks
   for (CModuleLockMap::iterator it = moduleLocks_.begin(); it != moduleLocks_.end(); it++)
      delete(it->second);

   moduleLocks_.clear();
}

/**
 * Returns appropriate module-level lock for a given device.
 * If a lock does not exist for a particular device, returns 0
 */
MMThreadLock* CPluginManager::getModuleLock(const MM::Device* pDev)
{
   if (pDev == 0)
      return 0;

   char moduleName[MM::MaxStrLength];
   pDev->GetModuleName(moduleName);
   CModuleLockMap::iterator it = moduleLocks_.find(moduleName);
   if (it == moduleLocks_.end())
      return 0;

   return it->second;
}

/**
 * Removes module lock if it exists.
 * This method is used by devices to defeat module thread locking in MMCore.
 * If device implements its own thread safety mechanism, then it makes sense to
 * defeat the system level locking.
 */
bool CPluginManager::removeModuleLock(const char* moduleName)
{
   CModuleLockMap::iterator it = moduleLocks_.find(moduleName);
   if (it == moduleLocks_.end())
      return false;

   delete moduleLocks_[moduleName];
   moduleLocks_.erase(it);
   return true;
}
