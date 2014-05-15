// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCore
//
// DESCRIPTION:   Device adapter module
//
// COPYRIGHT:     University of California, San Francisco, 2013-2014
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
// AUTHOR:        Mark Tsuchida

#pragma once

#include "LoadedModule.h"

#include "../../MMDevice/DeviceThreads.h"
#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/ModuleInterface.h"

#include <boost/shared_ptr.hpp>
#include <boost/utility.hpp>


class LoadedDeviceAdapter /* final */ : boost::noncopyable
{
public:
   LoadedDeviceAdapter(const std::string& label, const std::string& filename);

   // TODO Unload() should mark the instance invalid (or require instance
   // deletion to unload)
   void Unload() { module_->Unload(); } // For developer use only

   // The "module lock", used to synchronize _most_ access to the device
   // adapter.
   MMThreadLock* GetLock();
   void RemoveLock(); // XXX I'm not sure it is a good idea to expose this.

   boost::shared_ptr<MM::Device> CreateDevice(const char* deviceName);

   // TODO Make these private and provide higher-level interface
   unsigned GetNumberOfDevices();
   bool GetDeviceName(unsigned index, char* buf, unsigned bufLen);
   bool GetDeviceType(const char* deviceName, int* type);
   bool GetDeviceDescription(const char* deviceName, char* buf, unsigned bufLen);

private:
   void CheckInterfaceVersion();

   // Wrappers around raw module interface functions
   void InitializeModuleData();
   long GetModuleVersion();
   long GetDeviceInterfaceVersion();
   void DeleteDevice(MM::Device* device);

   const std::string label_;
   boost::shared_ptr<LoadedModule> module_;

   MMThreadLock lock_;
   bool useLock_;

   // Shared pointer deleter
   class DeviceDeleter {
      LoadedDeviceAdapter* self_;
   public:
      DeviceDeleter(LoadedDeviceAdapter* self) : self_(self) {}
      void operator()(MM::Device* pDevice) { self_->DeleteDevice(pDevice); }
   };

   // Cached function pointers
   fnInitializeModuleData InitializeModuleData_;
   fnCreateDevice CreateDevice_;
   fnDeleteDevice DeleteDevice_;
   fnGetModuleVersion GetModuleVersion_;
   fnGetDeviceInterfaceVersion GetDeviceInterfaceVersion_;
   fnGetNumberOfDevices GetNumberOfDevices_;
   fnGetDeviceName GetDeviceName_;
   fnGetDeviceType GetDeviceType_;
   fnGetDeviceDescription GetDeviceDescription_;
};
