// DESCRIPTION:   Loading/unloading and bookkeeping of device instances
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

#pragma once

#include "PluginManager.h"
#include "../MMDevice/MMDevice.h"
#include "../MMDevice/DeviceThreads.h"
#include "Logging/Logging.h"

#include <boost/shared_ptr.hpp>
#include <boost/weak_ptr.hpp>

#include <map>
#include <string>
#include <vector>

class CMMCore;
class DeviceInstance;
class HubInstance;


namespace mm
{

class DeviceManager /* final */
{
   CPluginManager& pluginManager_;

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

public:
   // Temporarily use a reference to the plugin manager.
   DeviceManager(CPluginManager& pluginManager) :
      pluginManager_(pluginManager)
   {}
   ~DeviceManager();

   /**
    * \brief Load the specified device and assign a device label.
    */
   boost::shared_ptr<DeviceInstance> LoadDevice(CMMCore* core,
         const char* label, const char* moduleName, const char* deviceName,
         boost::shared_ptr<mm::logging::Logger> deviceLogger,
         boost::shared_ptr<mm::logging::Logger> coreLogger);

   /**
    * \brief Unload a device.
    */
   void UnloadDevice(boost::shared_ptr<DeviceInstance> device);

   /**
    * \brief Unload all devices.
    */
   void UnloadAllDevices();

   /**
    * \brief Get a device by label.
    */
   boost::shared_ptr<DeviceInstance> GetDevice(const std::string& label) const;

   /**
    * \brief Get a device from a raw pointer to its MMDevice object.
    */
   boost::shared_ptr<DeviceInstance> GetDevice(const MM::Device* rawPtr) const;

   /**
    * \brief Get the labels of all loaded devices of a given type.
    */
   std::vector<std::string> GetDeviceList(MM::DeviceType t = MM::AnyType) const;

   /**
    * \brief Get the labels of all loaded peripherals of a hub device.
    */
   std::vector<std::string> GetLoadedPeripherals(const char* hubLabel) const;
   // TODO GetLoadedPeripherals() should be a HubInstance method.

   /**
    * \brief Get the parent hub device of a peripheral.
    *
    * \param device
    * \return The hub device, or null if device has no parent.
    */
   boost::shared_ptr<HubInstance> GetParentDevice(boost::shared_ptr<DeviceInstance> device) const;
   // TODO GetParentDevice() should be a DeviceInstance method.

   // module level thread locking
   MMThreadLock* getModuleLock(const boost::shared_ptr<DeviceInstance> pDev);
   // TODO getModuleLock() should be a DeviceInstance method.
};

} // namespace mm
