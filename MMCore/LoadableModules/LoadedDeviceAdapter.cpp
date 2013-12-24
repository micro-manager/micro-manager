// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCore
//
// DESCRIPTION:   Device adapter module
//
// COPYRIGHT:     University of California, San Francisco, 2013,
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
// AUTHOR:        Mark Tsuchida,
//                based on parts of CPluginManager by Nenad Amodaj

#include "LoadedDeviceAdapter.h"

#include "../CoreUtils.h"
#include "../Error.h"

#include <boost/make_shared.hpp>


LoadedDeviceAdapter::LoadedDeviceAdapter(const std::string& label, const std::string& filename) :
   label_(label),
   useLock_(true),
   InitializeModuleData_(0),
   CreateDevice_(0),
   DeleteDevice_(0),
   GetModuleVersion_(0),
   GetDeviceInterfaceVersion_(0),
   GetNumberOfDevices_(0),
   GetDeviceName_(0),
   GetDeviceType_(0),
   GetDeviceDescription_(0)
{
   try
   {
      module_ = boost::make_shared<LoadedModule>(filename);
   }
   catch (const CMMError& e)
   {
      module_.reset();
      throw CMMError("Failed to load device adapter " + ToQuotedString(label_), e);
   }

   try
   {
      CheckInterfaceVersion();
   }
   catch (const CMMError& e)
   {
      module_.reset();
      throw CMMError("Failed to load device adapter " + ToQuotedString(label_) +
            " from " + ToQuotedString(filename), e);
   }

   InitializeModuleData();
}


MMThreadLock*
LoadedDeviceAdapter::GetLock()
{
   if (useLock_)
      return &lock_;
   return 0;
}


void
LoadedDeviceAdapter::RemoveLock()
{
   useLock_ = false;
}


void
LoadedDeviceAdapter::CheckInterfaceVersion()
{
   long moduleInterfaceVersion, deviceInterfaceVersion;
   try
   {
      moduleInterfaceVersion = GetModuleVersion();
      deviceInterfaceVersion = GetDeviceInterfaceVersion();
   }
   catch (const CMMError& e)
   {
      throw CMMError("Cannot verify interface compatibility of device adapter", e);
   }

   if (moduleInterfaceVersion != MODULE_INTERFACE_VERSION)
      throw CMMError("Incompatible module interface version (required = " +
            ToString(MODULE_INTERFACE_VERSION) +
            "; found = " + ToString(moduleInterfaceVersion) + ")");

   if (deviceInterfaceVersion != DEVICE_INTERFACE_VERSION)
      throw CMMError("Incompatible device interface version (required = " +
            ToString(DEVICE_INTERFACE_VERSION) +
            "; found = " + ToString(deviceInterfaceVersion) + ")");
}


void
LoadedDeviceAdapter::InitializeModuleData()
{
   if (!InitializeModuleData_)
      InitializeModuleData_ = reinterpret_cast<fnInitializeModuleData>
         (module_->GetFunction("InitializeModuleData"));
   InitializeModuleData_();
}


MM::Device*
LoadedDeviceAdapter::CreateDevice(const char* deviceName)
{
   if (!CreateDevice_)
      CreateDevice_ = reinterpret_cast<fnCreateDevice>
         (module_->GetFunction("CreateDevice"));
   return CreateDevice_(deviceName);
}


void
LoadedDeviceAdapter::DeleteDevice(MM::Device* device)
{
   if (!DeleteDevice_)
      DeleteDevice_ = reinterpret_cast<fnDeleteDevice>
         (module_->GetFunction("DeleteDevice"));
   DeleteDevice_(device);
}


long
LoadedDeviceAdapter::GetModuleVersion()
{
   if (!GetModuleVersion_)
      GetModuleVersion_ = reinterpret_cast<fnGetModuleVersion>
         (module_->GetFunction("GetModuleVersion"));
   return GetModuleVersion_();
}


long
LoadedDeviceAdapter::GetDeviceInterfaceVersion()
{
   if (!GetDeviceInterfaceVersion_)
      GetDeviceInterfaceVersion_ = reinterpret_cast<fnGetDeviceInterfaceVersion>
         (module_->GetFunction("GetDeviceInterfaceVersion"));
   return GetDeviceInterfaceVersion_();
}


unsigned
LoadedDeviceAdapter::GetNumberOfDevices()
{
   if (!GetNumberOfDevices_)
      GetNumberOfDevices_ = reinterpret_cast<fnGetNumberOfDevices>
         (module_->GetFunction("GetNumberOfDevices"));
   return GetNumberOfDevices_();
}


bool
LoadedDeviceAdapter::GetDeviceName(unsigned index, char* buf, unsigned bufLen)
{
   if (!GetDeviceName_)
      GetDeviceName_ = reinterpret_cast<fnGetDeviceName>
         (module_->GetFunction("GetDeviceName"));
   return GetDeviceName_(index, buf, bufLen);
}


bool
LoadedDeviceAdapter::GetDeviceType(const char* deviceName, int* type)
{
   if (!GetDeviceType_)
      GetDeviceType_ = reinterpret_cast<fnGetDeviceType>
         (module_->GetFunction("GetDeviceType"));
   return GetDeviceType_(deviceName, type);
}


bool
LoadedDeviceAdapter::GetDeviceDescription(const char* deviceName, char* buf, unsigned bufLen)
{
   if (!GetDeviceDescription_)
      GetDeviceDescription_ = reinterpret_cast<fnGetDeviceDescription>
         (module_->GetFunction("GetDeviceDescription"));
   return GetDeviceDescription_(deviceName, buf, bufLen);
}
