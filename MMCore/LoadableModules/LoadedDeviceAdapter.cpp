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
// AUTHOR:        Mark Tsuchida,
//                based on parts of CPluginManager by Nenad Amodaj

#include "LoadedDeviceAdapter.h"

#include "../Devices/DeviceInstances.h"
#include "../CoreUtils.h"
#include "../Error.h"

#include <boost/bind.hpp>
#include <boost/make_shared.hpp>


LoadedDeviceAdapter::LoadedDeviceAdapter(const std::string& name, const std::string& filename) :
   name_(name),
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
      throw CMMError("Failed to load device adapter " + ToQuotedString(name_), e);
   }

   try
   {
      CheckInterfaceVersion();
   }
   catch (const CMMError& e)
   {
      module_.reset();
      throw CMMError("Failed to load device adapter " + ToQuotedString(name_) +
            " from " + ToQuotedString(filename), e);
   }

   InitializeModuleData();
}


MMThreadLock*
LoadedDeviceAdapter::GetLock()
{
   return &lock_;
}


std::vector<std::string>
LoadedDeviceAdapter::GetAvailableDeviceNames() const
{
   unsigned deviceCount = GetNumberOfDevices();
   std::vector<std::string> deviceNames;
   deviceNames.reserve(deviceCount);
   for (unsigned i = 0; i < deviceCount; ++i)
   {
      ModuleStringBuffer nameBuf(this, "GetDeviceName");
      bool ok = GetDeviceName(i, nameBuf.GetBuffer(), (unsigned int) nameBuf.GetMaxStrLen());
      if (!ok)
      {
         throw CMMError("Cannot get device name at index " + ToString(i) +
               " from device adapter module " + ToQuotedString(name_));
      }
      deviceNames.push_back(nameBuf.Get());
   }
   return deviceNames;
}


std::string
LoadedDeviceAdapter::GetDeviceDescription(const std::string& deviceName) const
{
   ModuleStringBuffer descBuf(this, "GetDeviceDescription");
   bool ok = GetDeviceDescription(deviceName.c_str(), descBuf.GetBuffer(),
        (unsigned int) descBuf.GetMaxStrLen());
   if (!ok)
   {
      throw CMMError("Cannot get description for device " +
            ToQuotedString(deviceName) + " of device adapter module " +
            ToQuotedString(name_));
   }
   return descBuf.Get();
}


MM::DeviceType
LoadedDeviceAdapter::GetAdvertisedDeviceType(const std::string& deviceName) const
{
   int typeInt = MM::UnknownType;
   bool ok = GetDeviceType(deviceName.c_str(), &typeInt);
   if (!ok || typeInt == MM::UnknownType)
   {
      throw CMMError("Cannot get type of device " +
            ToQuotedString(deviceName) + " of device adapter module " +
            ToQuotedString(name_));
   }
   return static_cast<MM::DeviceType>(typeInt);
}


boost::shared_ptr<DeviceInstance>
LoadedDeviceAdapter::LoadDevice(CMMCore* core, const std::string& name,
      const std::string& label,
      mm::logging::Logger deviceLogger,
      mm::logging::Logger coreLogger)
{
   MM::Device* pDevice = CreateDevice(name.c_str());
   if (!pDevice)
      throw CMMError("Device adapter " + ToQuotedString(GetName()) +
            " failed to instantiate device " + ToQuotedString(name));

   MM::DeviceType expectedType;
   try
   {
      expectedType = GetAdvertisedDeviceType(name);
   }
   catch (const CMMError&)
   {
      // The type of a device that was not explicitly registered (e.g. a
      // peripheral device or a device provided only for backward
      // compatibility) will not be available.
      expectedType = MM::UnknownType;
   }
   MM::DeviceType actualType = pDevice->GetType();
   if (expectedType == MM::UnknownType)
      expectedType = actualType;

   boost::shared_ptr<LoadedDeviceAdapter> shared_this(shared_from_this());
   DeleteDeviceFunction deleter = boost::bind(&LoadedDeviceAdapter::DeleteDevice, this, _1);

   switch (expectedType)
   {
      case MM::CameraDevice:
         return boost::make_shared<CameraInstance>(core, shared_this, name, pDevice, deleter, label, deviceLogger, coreLogger);
      case MM::ShutterDevice:
         return boost::make_shared<ShutterInstance>(core, shared_this, name, pDevice, deleter, label, deviceLogger, coreLogger);
      case MM::StageDevice:
         return boost::make_shared<StageInstance>(core, shared_this, name, pDevice, deleter, label, deviceLogger, coreLogger);
      case MM::XYStageDevice:
         return boost::make_shared<XYStageInstance>(core, shared_this, name, pDevice, deleter, label, deviceLogger, coreLogger);
      case MM::StateDevice:
         return boost::make_shared<StateInstance>(core, shared_this, name, pDevice, deleter, label, deviceLogger, coreLogger);
      case MM::SerialDevice:
         return boost::make_shared<SerialInstance>(core, shared_this, name, pDevice, deleter, label, deviceLogger, coreLogger);
      case MM::GenericDevice:
         return boost::make_shared<GenericInstance>(core, shared_this, name, pDevice, deleter, label, deviceLogger, coreLogger);
      case MM::AutoFocusDevice:
         return boost::make_shared<AutoFocusInstance>(core, shared_this, name, pDevice, deleter, label, deviceLogger, coreLogger);
      case MM::ImageProcessorDevice:
         return boost::make_shared<ImageProcessorInstance>(core, shared_this, name, pDevice, deleter, label, deviceLogger, coreLogger);
      case MM::SignalIODevice:
         return boost::make_shared<SignalIOInstance>(core, shared_this, name, pDevice, deleter, label, deviceLogger, coreLogger);
      case MM::MagnifierDevice:
         return boost::make_shared<MagnifierInstance>(core, shared_this, name, pDevice, deleter, label, deviceLogger, coreLogger);
      case MM::SLMDevice:
         return boost::make_shared<SLMInstance>(core, shared_this, name, pDevice, deleter, label, deviceLogger, coreLogger);
      case MM::GalvoDevice:
         return boost::make_shared<GalvoInstance>(core, shared_this, name, pDevice, deleter, label, deviceLogger, coreLogger);
      case MM::HubDevice:
         return boost::make_shared<HubInstance>(core, shared_this, name, pDevice, deleter, label, deviceLogger, coreLogger);
      default:
         deleter(pDevice);
         throw CMMError("Device " + ToQuotedString(name) +
               " of device adapter " + ToQuotedString(GetName()) +
               " has invalid or unknown type (" + ToQuotedString(actualType) + ")");
   }
}


void
LoadedDeviceAdapter::ModuleStringBuffer::ThrowBufferOverflowError() const
{
   std::string name(module_ ? module_->GetName() : "<unknown>");
   throw CMMError("Buffer overflow in device adapter module " +
         ToQuotedString(name) + " while calling " + funcName_ + "(); "
         "this is most likely a bug in the device adapter");
}


void
LoadedDeviceAdapter::CheckInterfaceVersion() const
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
LoadedDeviceAdapter::GetModuleVersion() const
{
   if (!GetModuleVersion_)
      GetModuleVersion_ = reinterpret_cast<fnGetModuleVersion>
         (module_->GetFunction("GetModuleVersion"));
   return GetModuleVersion_();
}


long
LoadedDeviceAdapter::GetDeviceInterfaceVersion() const
{
   if (!GetDeviceInterfaceVersion_)
      GetDeviceInterfaceVersion_ = reinterpret_cast<fnGetDeviceInterfaceVersion>
         (module_->GetFunction("GetDeviceInterfaceVersion"));
   return GetDeviceInterfaceVersion_();
}


unsigned
LoadedDeviceAdapter::GetNumberOfDevices() const
{
   if (!GetNumberOfDevices_)
      GetNumberOfDevices_ = reinterpret_cast<fnGetNumberOfDevices>
         (module_->GetFunction("GetNumberOfDevices"));
   return GetNumberOfDevices_();
}


bool
LoadedDeviceAdapter::GetDeviceName(unsigned index, char* buf, unsigned bufLen) const
{
   if (!GetDeviceName_)
      GetDeviceName_ = reinterpret_cast<fnGetDeviceName>
         (module_->GetFunction("GetDeviceName"));
   return GetDeviceName_(index, buf, bufLen);
}


bool
LoadedDeviceAdapter::GetDeviceType(const char* deviceName, int* type) const
{
   if (!GetDeviceType_)
      GetDeviceType_ = reinterpret_cast<fnGetDeviceType>
         (module_->GetFunction("GetDeviceType"));
   return GetDeviceType_(deviceName, type);
}


bool
LoadedDeviceAdapter::GetDeviceDescription(const char* deviceName, char* buf, unsigned bufLen) const
{
   if (!GetDeviceDescription_)
      GetDeviceDescription_ = reinterpret_cast<fnGetDeviceDescription>
         (module_->GetFunction("GetDeviceDescription"));
   return GetDeviceDescription_(deviceName, buf, bufLen);
}
