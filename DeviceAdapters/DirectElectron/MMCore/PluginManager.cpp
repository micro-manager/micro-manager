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
//
// CVS:           $Id: PluginManager.cpp 3942 2010-02-05 23:20:59Z karlh $
//

//#include "CoreUtils.h"
#ifdef WIN32
   #include <windows.h>
   #include <io.h>
#else
   #include <dlfcn.h>
   #include <sys/types.h>
   #include <dirent.h>
#endif // WIN32

#include "../MMDevice/ModuleInterface.h"
#include "../MMCore/Error.h"
#include "PluginManager.h"

#include <assert.h>
#include <sstream>
#include <iostream>
using namespace std;

///////////////////////////////////////////////////////////////////////////////
// CPluginManager class
// --------------------

CPluginManager::CPersistentDataMap CPluginManager::persistentDataMap;

CPluginManager::CPluginManager() 
{
}

CPluginManager::~CPluginManager()
{
   UnloadAllDevices();
}

// disable MSVC warning about unreferenced variable "ret"
#ifdef WIN32
#pragma warning(disable : 4189)
#endif

/** 
 * Unloads the plugin library 
 * This function is deprecated.  Unloading the plugin libraries disallows them to maintain information
 * between invocations.  Not releasing the libraries does not seem to have bad consequences.  
 * This function can be removed and the code involved can be refactored
 */
void CPluginManager::ReleasePluginLibrary(HDEVMODULE)
{
   #ifdef WIN32
      //BOOL ret = FreeLibrary((HMODULE)hLib);
      //assert(ret);
   #else
   // Note that even though we use the RTLD_NODELETE flag, the library still disappears (at least on the Mac) when dlclose is called...
      //int nRet = dlclose(hLib);
      //assert(nRet == 0);
   #endif
}
#ifdef WIN32
#pragma warning(default : 4189)
#endif

/** 
 * Loads the plugin library. Platform dependent.
 * @param name module name without extension and directory. Each platform has different conventions
 * for resolving the actual path from the library name.
 * @param funcName the name of the function
 * @return module handle if successful, throws exception if not
 */
HDEVMODULE CPluginManager::LoadPluginLibrary(const char* shortName)
{
   // add specific name prefix
   string name(LIB_NAME_PREFIX);
   name += shortName;

   string errorText;
   #ifdef WIN32
      int originalErrorMode = SetErrorMode(SEM_NOOPENFILEERRORBOX | SEM_FAILCRITICALERRORS);
      HMODULE hMod = LoadLibrary(name.c_str());
      SetErrorMode(originalErrorMode);
      if (hMod)
         return (HDEVMODULE) hMod;
   #else
      HDEVMODULE hMod = dlopen(name.c_str(), RTLD_LAZY | RTLD_NOLOAD| RTLD_LOCAL);
      if (hMod)
         return  hMod;
      hMod = dlopen(name.c_str(), RTLD_LAZY | RTLD_NODELETE | RTLD_LOCAL);
      if (hMod)
         return  hMod;
      #ifdef linux
      // Linux-specific code block by Johan Henriksson
      else {
         string name2 = (string) name + (string) ".so.0";
         hMod = dlopen(name2.c_str(), RTLD_LAZY | RTLD_NOLOAD | RTLD_LOCAL);
         if (hMod)
            return hMod;
         hMod = dlopen(name2.c_str(), RTLD_LAZY | RTLD_NODELETE | RTLD_LOCAL);
         if (hMod)
            return hMod;
      }
      #endif // linux
   #endif // WIN32
   GetSystemError (errorText);
   errorText += " ";
   errorText += shortName;
   throw CMMError(errorText.c_str(), MMERR_LoadLibraryFailed); // dll load failed
}

/** 
 * Returns pointer to the function from the plugin library. Platform dependent.
 * @param hLib module (plugin library) handle
 * @param funcName the name of the function
 * @return function pointer if successful, throws exception if not
 */
void* CPluginManager::GetModuleFunction(HDEVMODULE hLib, const char* funcName)
{
   string errorText;
   #ifdef WIN32
      void* procAddr = ::GetProcAddress((HMODULE)hLib, funcName);
      if (procAddr)
         return procAddr;
      else
         GetSystemError(errorText);
   #else
      return dlsym(hLib, funcName);
   #endif
   errorText += " ";
   errorText += funcName;
   throw CMMError(errorText.c_str(), MMERR_LibraryFunctionNotFound);
}

/** Platform dependent system error text */
void CPluginManager::GetSystemError(string& errorText)
{
   #ifdef WIN32
   // obtain error info from the system
   void* pMsgBuf(0);
   if (FormatMessage( 
         FORMAT_MESSAGE_ALLOCATE_BUFFER | 
         FORMAT_MESSAGE_FROM_SYSTEM | 
         FORMAT_MESSAGE_IGNORE_INSERTS,
         NULL,
         GetLastError(),
         MAKELANGID(LANG_NEUTRAL, SUBLANG_DEFAULT), // Default language
         (LPTSTR)&pMsgBuf,
         0,
         NULL ))
   {
      errorText = (LPTSTR)(pMsgBuf);
      LocalFree(pMsgBuf);
   }
   #else
      errorText = dlerror();  
   #endif
}

/**
 * Verifies that plugin interface/device version matches the core version expectations.
 * Throws if there is a mismatch or if the version info is not available.
 * @param hLib handle to the plugin library (module)
*/
void CPluginManager::CheckVersion(HDEVMODULE hLib)
{
   // module version
   fnGetModuleVersion hGetModuleVersionFunc = (fnGetModuleVersion) GetModuleFunction(hLib, "GetModuleVersion");

   long moduleVersion = hGetModuleVersionFunc();
   if (moduleVersion != MODULE_INTERFACE_VERSION)
   {
      ostringstream errTxt;
      errTxt << "Module interface version: core=" << MODULE_INTERFACE_VERSION << ", library=" << moduleVersion;  
      throw CMMError(errTxt.str().c_str(), MMERR_ModuleVersionMismatch);
   }

   // device version
   fnGetDeviceInterfaceVersion hGetDeviceInterfaceVersionFunc = (fnGetDeviceInterfaceVersion) GetModuleFunction(hLib, "GetDeviceInterfaceVersion");
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
   
   // identify currently loaded module
   HDEVMODULE hLib = pDevice->GetModuleHandle();
   if (hLib == 0)
      throw CMMError(MMERR_UnknownModule); // can't get the handle to the device module
   
   // obtain handle to the DeleteDevice method
   // we are assuming here that the current device module is already loaded
   fnDeleteDevice hDeleteDeviceFunc = (fnDeleteDevice) GetModuleFunction(hLib, "DeleteDevice");

   // release device resources
   pDevice->Shutdown(); // perhaps there is no need to do this explicitly???
                        // rely on device destructor to call Shutdown()?

   // delete device
   hDeleteDeviceFunc(pDevice);

   // invalidate the entry in the label-device map
   string label = GetDeviceLabel(*pDevice);
   devices_[label] = 0;

   // remove the entry from the device array
   DeviceArray::iterator it;
   for (it = devArray_.begin(); it != devArray_.end(); it++)
      if (*it == pDevice)
      {
         devArray_.erase(it);
         break;
      }
}

/**
 * Unloads all devices from the core and resets all configuration data.
 */
void CPluginManager::UnloadAllDevices()
{
   // do a two pass unloading so that USB ports and com ports unload last
   CDeviceMap::const_iterator it;

   // first unload all devices but serial ports
   for (it=devices_.begin(); it != devices_.end(); it++)
      if (it->second != 0 && it->second->GetType() != MM::SerialDevice)
         UnloadDevice(it->second);

   // now unload remaining ports
   for (it=devices_.begin(); it != devices_.end(); it++)
      if (it->second != 0)
         UnloadDevice(it->second);

   devices_.clear();
   devArray_.clear();
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
      throw CMMError(label, MMERR_DuplicateLabel);

   if (strlen(label) == 0)
      throw CMMError(label, MMERR_InvalidLabel);
   
   // always attempt to load the plugin module
   // this should work fine even if the same module was previously loaded
   HDEVMODULE hLib = LoadPluginLibrary(moduleName);
   assert(hLib);

   fnCreateDevice hCreateDeviceFunc(0);
   try
   {
      CheckVersion(hLib); // verify that versions match
      hCreateDeviceFunc = (fnCreateDevice) GetModuleFunction(hLib, "CreateDevice");
      assert(hCreateDeviceFunc);
   }
   catch (CMMError& err)
   {
      std::ostringstream o;
      o << label << " module " << moduleName << " device " << deviceName;

      CMMError newErr( o.str().c_str(), err.getCoreMsg().c_str(), err.getCode());
      ReleasePluginLibrary(hLib);
      throw newErr;
   }
   
   // instantiate the new device
   MM::Device* pDevice = hCreateDeviceFunc(deviceName);
   if (pDevice == 0)
      throw CMMError(deviceName, MMERR_CreateFailed);

   // make sure that each device carries a reference to the module it belongs to!!!
   pDevice->SetModuleHandle(hLib);
   pDevice->SetLabel(label);
   pDevice->SetModuleName(moduleName);

   // assign label
   devices_[label] = pDevice;
   devArray_.push_back(pDevice);

   return pDevice;
}
/**
 * Obtains the device corresponding to the label
 * @param label device label
 * @return pointer to the device or 0 if the label is not recognized
 */
MM::Device* CPluginManager::GetDevice(const char* label) const
{
   CDeviceMap::const_iterator it;
   it = devices_.find(label);
   if (it == devices_.end() || it->second == 0)
      throw CMMError(label, MMERR_InvalidLabel);

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
   
   char buf[MM::MaxStrLength];
   device.GetName(buf);
   throw CMMError(buf, MMERR_UnexpectedDevice);
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
   for (size_t i=0; i<devArray_.size(); i++)
   {
      char buf[MM::MaxStrLength];
      if (type == MM::AnyType || type == devArray_[i]->GetType())
      {
         devArray_[i]->GetLabel(buf);
         labels.push_back(buf);
      }
   }
   return labels;
}

/**
 * List all modules (device libraries) in the search path.
 */
vector<string> CPluginManager::GetModules(const char* searchPath)
{
   vector<string> modules;
   string path = searchPath;
   path += "\\";
   path += LIB_NAME_PREFIX;
   path += "*.*";

#ifdef WIN32

   // find the first dll file in the directory
   struct _finddata_t moduleFile;
   intptr_t hFile;
   hFile = _findfirst(path.c_str(), &moduleFile);
   if( hFile != -1L )
   {
      modules.push_back(moduleFile.name);
      while( _findnext( hFile, &moduleFile ) == 0 )
         modules.push_back(moduleFile.name);

      _findclose( hFile );
   }
#else
   DIR *dp;
   struct dirent *dirp;
   if ((dp  = opendir(searchPath)) != NULL)
   {
      while ((dirp = readdir(dp)) != NULL)
      {
         if (strncmp(dirp->d_name,LIB_NAME_PREFIX,strlen(LIB_NAME_PREFIX)) == 0 ) {
           modules.push_back(dirp->d_name);
         }
      }
      closedir(dp);
   }
#endif

   // strip prefixes
   for (unsigned i=0; i < modules.size(); i++)
   {
      // remove prefix
      string strippedName = modules[i].substr(strlen(LIB_NAME_PREFIX));

      // remove suffix
      modules[i] = strippedName.substr(0, strippedName.find_first_of("."));
   }
   return modules;
}

/**
 * List all available devices in the specified module.
 */
vector<string> CPluginManager::GetAvailableDevices(const char* moduleName) throw (CMMError)
{
   vector<string> devices;
   HDEVMODULE hLib = LoadPluginLibrary(moduleName);
   CheckVersion(hLib); // verify that versions match

   fnGetNumberOfDevices hGetNumberOfDevices(0);
   fnGetDeviceName hGetDeviceName(0);
   fnInitializeModuleData hInitializeModuleData(0);
   try
   {
      // initalize module data
      hInitializeModuleData = (fnInitializeModuleData) GetModuleFunction(hLib, "InitializeModuleData");
      assert(hInitializeModuleData);
      hInitializeModuleData();

      hGetNumberOfDevices = (fnGetNumberOfDevices) GetModuleFunction(hLib, "GetNumberOfDevices");
      assert(hGetNumberOfDevices);
      hGetDeviceName = (fnGetDeviceName) GetModuleFunction(hLib, "GetDeviceName");
      assert(hGetDeviceName);

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
      std::ostringstream o;
      o << " module " << moduleName;

      CMMError newErr( o.str().c_str(), err.getCoreMsg().c_str(), err.getCode());
      ReleasePluginLibrary(hLib);
      throw newErr;
   }
   
   ReleasePluginLibrary(hLib);
   return devices;
}


/**
 * List all available devices in the specified module.
 */
vector<string> CPluginManager::GetAvailableDeviceDescriptions(const char* moduleName) throw (CMMError)
{
   vector<string> descriptions;
   HDEVMODULE hLib = LoadPluginLibrary(moduleName);
   CheckVersion(hLib); // verify that versions match

   fnGetNumberOfDevices hGetNumberOfDevices(0);
   fnGetDeviceDescription hGetDeviceDescription(0);
   fnInitializeModuleData hInitializeModuleData(0);
   try
   {
      hInitializeModuleData = (fnInitializeModuleData) GetModuleFunction(hLib, "InitializeModuleData");
      assert(hInitializeModuleData);
      hInitializeModuleData();

      hGetNumberOfDevices = (fnGetNumberOfDevices) GetModuleFunction(hLib, "GetNumberOfDevices");
      assert(hGetNumberOfDevices);
      hGetDeviceDescription = (fnGetDeviceDescription) GetModuleFunction(hLib, "GetDeviceDescription");
      assert(hGetDeviceDescription);

      unsigned numDev = hGetNumberOfDevices();
      for (unsigned i=0; i<numDev; i++)
      {
         char deviceDescr[MM::MaxStrLength];
         if (hGetDeviceDescription(i, deviceDescr, MM::MaxStrLength))
            descriptions.push_back(deviceDescr);
      }
   }
   catch (CMMError& err)
   {
      std::ostringstream o;
      o << " module " << moduleName ;

      CMMError newErr( o.str().c_str(), err.getCoreMsg().c_str(), err.getCode());
      ReleasePluginLibrary(hLib);
      throw newErr;
   }
   
   ReleasePluginLibrary(hLib);
   return descriptions;
}

/**
 * List all device types in the specified module.
 */
vector<long> CPluginManager::GetAvailableDeviceTypes(const char* moduleName) throw (CMMError)
{
   vector<long> types;
   HDEVMODULE hLib = LoadPluginLibrary(moduleName);
   CheckVersion(hLib); // verify that versions match

   fnGetNumberOfDevices hGetNumberOfDevices(0);
   fnInitializeModuleData hInitializeModuleData(0);
   try
   {
      hInitializeModuleData = (fnInitializeModuleData) GetModuleFunction(hLib, "InitializeModuleData");
      assert(hInitializeModuleData);
      hInitializeModuleData();

      hGetNumberOfDevices = (fnGetNumberOfDevices) GetModuleFunction(hLib, "GetNumberOfDevices");
      assert(hGetNumberOfDevices);

      fnDeleteDevice hDeleteDeviceFunc = (fnDeleteDevice) GetModuleFunction(hLib, "DeleteDevice");
      assert(hDeleteDeviceFunc);
      fnCreateDevice hCreateDeviceFunc = (fnCreateDevice) GetModuleFunction(hLib, "CreateDevice");
      assert(hCreateDeviceFunc);
      fnGetDeviceName hGetDeviceName = (fnGetDeviceName) GetModuleFunction(hLib, "GetDeviceName");
      assert(hGetDeviceName);
      
      unsigned numDev = hGetNumberOfDevices();

      for (unsigned i=0; i<numDev; i++)
      {
         char deviceName[MM::MaxStrLength];
         if (!hGetDeviceName(i, deviceName, MM::MaxStrLength))
         {
            types.push_back((long)MM::AnyType);
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
      std::ostringstream o;
      o << " module " << moduleName;

      CMMError newErr( o.str().c_str(), err.getCoreMsg().c_str(), err.getCode());
      ReleasePluginLibrary(hLib);
      throw newErr;
   }
   
   ReleasePluginLibrary(hLib);
   return types;
}

string CPluginManager::Serialize()
{
   ostringstream os;
   CDeviceMap::const_iterator it;
   for (it=devices_.begin(); it != devices_.end(); it++)
   {
      MM::Device* pDev = it->second;
      if (pDev)
      {
         char deviceName[MM::MaxStrLength] = "";
         char moduleName[MM::MaxStrLength] = "";
         pDev->GetName(deviceName);
         pDev->GetModuleName(moduleName);
         os << it->first << " " << moduleName << " " << deviceName << endl; 
      }
   }
   return os.str();
}

void CPluginManager::Restore(const string& data)
{
   UnloadAllDevices();
   istringstream is(data);

   char line[3 * MM::MaxStrLength];
   while(is.getline(line, 3 * MM::MaxStrLength, '\n'))
   {
      string label, moduleName, deviceName;
      istringstream isl(line);
      if (isl)
         isl >> label;
      if (isl)
         isl >> moduleName;
      if (isl)
         isl >> deviceName;
      if (!label.empty() && !moduleName.empty() && !deviceName.empty())
         LoadDevice(label.c_str(), moduleName.c_str(), deviceName.c_str());
   }
}
