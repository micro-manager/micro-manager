// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCore
//
// COPYRIGHT:     University of California, San Francisco, 2014,
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
// AUTHOR:        Mark Tsuchida

#pragma once

#include "../../MMDevice/MMDeviceConstants.h"
#include "../Error.h"
#include "../Logging/Logging.h"

#include <string>
#include <vector>
#include <boost/function.hpp>
#include <boost/shared_ptr.hpp>
#include <boost/utility.hpp>

class CMMCore;
class HubInstance;
class LoadedDeviceAdapter;
namespace MM
{
   class Core;
   class Device;
}

typedef boost::function<void (MM::Device*)> DeleteDeviceFunction;


/// Device instance wrapper class
/**
 * DeviceInstance and its concrete subclasses (one for each type of device) are
 * wrappers for the raw device objects (subclasses of MM::Device, defined and
 * instantiated by device adapter modules). By hiding the raw MM::Device
 * pointer inside DeviceInstance, the Core can ensure that uniform error
 * handling is performed when calling the device methods, attach additional
 * information to the device instance (such as the device label and its module
 * of origin), and add higher-level methods (e.g. to provide an idiomatic C++
 * interface to the raw device interface).
 *
 * The DeviceInstance class is the common base class, allowing the use of
 * shared_ptr<DeviceInstance> to handle all instances.
 *
 * DeviceInstance is an RAII class: the raw device instance is created in the
 * constructor, and destroyed in the destructor.
 */
class DeviceInstance : boost::noncopyable
{
protected:
   MM::Device* pImpl_;

private:
   CMMCore* core_; // Weak reference
   boost::shared_ptr<LoadedDeviceAdapter> adapter_;
   const std::string label_;
   std::string description_;
   DeleteDeviceFunction deleteFunction_;
   boost::shared_ptr<mm::logging::Logger> deviceLogger_;
   boost::shared_ptr<mm::logging::Logger> coreLogger_;

public:
   boost::shared_ptr<LoadedDeviceAdapter> GetAdapterModule() const /* final */ { return adapter_; }
   std::string GetLabel() const /* final */ { return label_; }
   std::string GetDescription() const /* final */ { return description_; }
   void SetDescription(const std::string& description) /* final */ { description_ = description; }

   // It would be nice to get rid of the need for raw pointers, but for now we
   // need it for the few CoreCallback methods that return a device pointer.
   MM::Device* GetRawPtr() const /* final */ { return pImpl_; }

   // Callback API
   int LogMessage(const char* msg, bool debugOnly);

protected:
   // The DeviceInstance object owns the raw device pointer (pDevice) as soon
   // as the constructor is called, even if the constructor throws.
   DeviceInstance(CMMCore* core,
         boost::shared_ptr<LoadedDeviceAdapter> adapter,
         const std::string& name,
         MM::Device* pDevice,
         DeleteDeviceFunction deleteFunction,
         const std::string& label,
         boost::shared_ptr<mm::logging::Logger> deviceLogger,
         boost::shared_ptr<mm::logging::Logger> coreLogger);

   virtual ~DeviceInstance();

   CMMCore* GetCore() const /* final */ { return core_; }

   boost::shared_ptr<mm::logging::Logger> Logger() const
   { return coreLogger_; }

   CMMError MakeException() const;
   CMMError MakeExceptionForCode(int code) const;
   void ThrowError(const std::string& message) const;
   void ThrowIfError(int code) const;
   void ThrowIfError(int code, const std::string& message) const;

   /// Utility class for getting fixed-length strings from the device interface.
   /**
    * This class should be used in all places where a device member function
    * takes a char* into which it returns a string of length at most
    * MM::MaxStrLength. It initializes an appropriate buffer and checks for
    * buffer overrun (not that we can necessarily recover from an overrun...).
    *
    * For usage, see, e.g., the definition for DeviceInstance::GetName().
    */
   class DeviceStringBuffer : boost::noncopyable
   {
      char buf_[MM::MaxStrLength + 1];
      const DeviceInstance* instance_;
      const std::string& funcName_;

   public:
      /**
       * instance and functionName must stay in scope during the lifetime of
       * the DeviceStringBuffer.
       */
      DeviceStringBuffer(const DeviceInstance* instance, const std::string& functionName) :
         instance_(instance), funcName_(functionName)
      { memset(buf_, 0, sizeof(buf_)); }

      char* GetBuffer() { return buf_; }
      std::string Get() const { Check(); return buf_; }
      bool IsEmpty() const { Check(); return (buf_[0] == '\0'); }

   private:
      void Check() const { if (buf_[sizeof(buf_) - 1] != '\0') ThrowBufferOverflowError(); }
      void ThrowBufferOverflowError() const;
   };

public:
   /*
    * High-level interface to MM::Device methods.
    */
   std::vector<std::string> GetPropertyNames() const;

   /*
    * Wrappers for MM::Device member functions.
    *
    * These are thin wrappers that correspond 1:1 to MM::Device member
    * functions. They perform only error handling and type conversion (e.g.
    * between char* and std::string).
    *
    * Some are public and some private (if higher-level methods are provided
    * above). The order of the declarations should be kept colinear with
    * MM::Device.
    *
    * Note that, at least for now, these are all non-virtual, because there is
    * no case under which they will be overridden by derived classes. It may
    * become necessary to make these virtual if the need arrises to e.g. handle
    * synchronization with methods specific to derived classes.
    *
    * TODO Error handling
    * TODO Type conversion (char* <-> std::string) (need to update client code)
    */
private:
   // Exposed through GetPropertyNames() only
   unsigned GetNumberOfProperties() const;
public:
   std::string GetProperty(const std::string& name) const;
   void SetProperty(const std::string& name, const std::string& value) const;
   bool HasProperty(const std::string& name) const;
private:
   // Exposed through GetPropertyNames() only
   std::string GetPropertyName(size_t idx) const;
public:
   int GetPropertyReadOnly(const char* name, bool& readOnly) const;
   int GetPropertyInitStatus(const char* name, bool& preInit) const;
   int HasPropertyLimits(const char* name, bool& hasLimits) const;
   int GetPropertyLowerLimit(const char* name, double& lowLimit) const;
   int GetPropertyUpperLimit(const char* name, double& hiLimit) const;
   int GetPropertyType(const char* name, MM::PropertyType& pt) const;
   unsigned GetNumberOfPropertyValues(const char* propertyName) const;
   std::string GetPropertyValueAt(const std::string& propertyName, unsigned index) const;
   int IsPropertySequenceable(const char* name, bool& isSequenceable) const;
   int GetPropertySequenceMaxLength(const char* propertyName, long& nrEvents) const;
   int StartPropertySequence(const char* propertyName);
   int StopPropertySequence(const char* propertyName);
   int ClearPropertySequence(const char* propertyName);
   int AddToPropertySequence(const char* propertyName, const char* value);
   int SendPropertySequence(const char* propertyName);
   std::string GetErrorText(int code) const;
   bool Busy();
   double GetDelayMs() const;
   void SetDelayMs(double delay);
   bool UsesDelay();
   int Initialize();
   int Shutdown();
   MM::DeviceType GetType() const; // TODO Make private (can use RTTI)
   std::string GetName() const;
   void SetCallback(MM::Core* callback);
   int AcqBefore();
   int AcqAfter();
   int AcqBeforeFrame();
   int AcqAfterFrame();
   int AcqBeforeStack();
   int AcqAfterStack();
   MM::DeviceDetectionStatus DetectDevice();
   void SetParentID(const char* parentId); // TODO Remove
   std::string GetParentID() const; // TODO Remove
};
