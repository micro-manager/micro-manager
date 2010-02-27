///////////////////////////////////////////////////////////////////////////////
// FILE:          DeviceBase.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMDevice - Device adapter kit
//-----------------------------------------------------------------------------
// DESCRIPTION:   Generic functionality for implementing device adapters
//
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 08/18/2005
//
// COPYRIGHT:     University of California, San Francisco, 2006
//
// LICENSE:       This file is distributed under the BSD license.
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
// CVS:           $Id$
//

#ifndef _DEVICE_BASE_H_
#define _DEVICE_BASE_H_



#include "MMDevice.h"
#include "MMDeviceConstants.h"
#include "Property.h"
#include "DeviceUtils.h"
#include "ModuleInterface.h"
#include "DeviceThreads.h"
#include "../MMCore/Error.h"
#include <assert.h>

#include <string>
#include <vector>
#include <iomanip>
#include <map>
#include <sstream>

// common error messages
const char* const g_Msg_ERR = "Unknown error in the device";
const char* const g_Msg_INVALID_PROPERTY = "Invalid property name encountered";
const char* const g_Msg_INVALID_PROPERTY_VALUE = "Invalid property value";
const char* const g_Msg_DUPLICATE_PROPERTY = "Duplicate property names are not allowed";
const char* const g_Msg_INVALID_PROPERTY_TYPE = "Invalid property type";
const char* const g_Msg_NATIVE_MODULE_FAILED = "Native module failed to load";
const char* const g_Msg_UNSUPPORTED_DATA_FORMAT = "Unsupported data format encountered";
const char* const g_Msg_INTERNAL_INCONSISTENCY = "Device adapter inconsistent with the actual device";
const char* const g_Msg_NOT_SUPPORTED = "Device not supported by the adapter";
const char* const g_Msg_UNKNOWN_LABEL = "Label not defined";
const char* const g_Msg_UNSUPPORTED_COMMAND = "Unsupported device command";
const char* const g_Msg_UNKNOWN_POSITION = "Invalid state (position) requested";
const char* const g_Msg_DEVICE_DUPLICATE_LABEL = "Position label already in use";
const char* const g_Msg_SERIAL_COMMAND_FAILED = "Serial command failed.  Is the device connected to the serial port?";
const char* const g_Msg_SERIAL_INVALID_RESPONSE = "Unexpected response from serial port. Is the device connected to the correct serial port?";
const char* const g_Msg_DEVICE_NONEXISTENT_CHANNEL = "Requested channel is not defined.";
const char* const g_Msg_DEVICE_INVALID_PROPERTY_LIMTS = "Specified property limits are not valid."
" Either the property already has a set of discrete values, or the range is invalid";
const char* const g_Msg_EXCEPTION_IN_THREAD = "Exception in the thread function.";
const char* const g_Msg_EXCEPTION_IN_ON_THREAD_EXITING = "Exception in the OnThreadExiting function.";
const char* const g_Msg_SEQUENCE_ACQUISITION_THREAD_EXITING="Sequence thread exiting";
const char* const g_Msg_DEVICE_CAMERA_BUSY_ACQUIRING="Camera is busy acquiring images.  Stop camera activity before changing this property";
const char* const g_Msg_DEVICE_CAN_NOT_SET_PROPERTY="The device can not set this property at this moment";
const char* const g_Msg_DEVICE_NOT_CONNECTED="Unable to communicate with the device.";

/**
* Implements functionality common to all devices.
* Typically used as the base class for actual device adapters. In general,
* derived class do not override DeviceBase methods, but rather take advantage
* of using them to simplify development of specific drivers.
*/

template <class T, class U>
class CDeviceBase : public T
{
public:

   typedef MM::Action<U> CPropertyAction;
   typedef MM::ActionEx<U> CPropertyActionEx;

   /**
   * Returns the library handle (for use only by the calling code).
   */
   HDEVMODULE GetModuleHandle() const {return module_;}

   /**
   * Assigns a name for the module (for use only by the calling code).
   */
   void SetModuleName(const char* name)
   {
      moduleName_ = name;
   }

   /**
   * Returns the module name (for use only by the calling code).
   */
   void GetModuleName(char* name) const
   {
      CDeviceUtils::CopyLimitedString(name, moduleName_.c_str());
   }

   /**
   * Sets the library handle (for use only by the calling code).
   */
   void SetModuleHandle(HDEVMODULE hModule) {module_ = hModule;}

   /**
   * Sets the device label (for use only by the calling code).
   * Labels are usually manipulated by the parent application and used
   * for high-level programming.
   */
   void SetLabel(const char* label)
   {
      label_ = label;
   }

   /**
   * Returns the device label (for use only by the calling code).
   * Labels are usually manipulated by the parent application and used
   * for high-level programming.
   */
   void GetLabel(char* name) const
   {
      CDeviceUtils::CopyLimitedString(name, label_.c_str());
   }

   /**
   * Returns device delay used for synchronization by the calling code.
   * Delay of 0 means that the device should be synchronized by polling with the
   * Busy() method.
   */
   double GetDelayMs() const {return delayMs_;}

   /**
   * Returns the device delay used for synchronization by the calling code.
   * Delay of 0 means that the device should be synchronized by polling with the
   * Busy() method.
   */
   void SetDelayMs(double delay) {delayMs_ = delay;}

   /**
   * Sets the callback for accessing parent functionality (used only by the calling code).
   */
   void SetCallback(MM::Core* cbk) {callback_ = cbk;}

   /**
   * Signals if the device responds to different delay settings.
   * Default device behavior is to ignore delays and use busy signals instead.
   */
   bool UsesDelay() {return usesDelay_;}

   /**
   * Returns the number of properties.
   */
   unsigned GetNumberOfProperties() const {return (unsigned)properties_.GetSize();}

   /**
   * Obtains the value of the property.
   * @param name - property identifier (name)
   * @param value - the value of the property
   */
   int GetProperty(const char* name, char* value) const
   {
      std::string strVal;
      int nRet = properties_.Get(name, strVal);
      if (nRet == DEVICE_OK)
         CDeviceUtils::CopyLimitedString(value, strVal.c_str());
      return nRet;
   }

   /**
   * Obtains the value of the property.
   * @param name - property identifier (name)
   * @param value - the value of the property
   */
   int GetProperty(const char* name, double& val)
   {
      std::string strVal;
      int nRet = properties_.Get(name, strVal);
      if (nRet == DEVICE_OK)
         val = atof(strVal.c_str());
      return nRet;
   }

   /**
   * Obtains the value of the property.
   * @param name - property identifier (name)
   * @param value - the value of the property
   */
   int GetProperty(const char* name, long& val)
   {
      std::string strVal;
      int nRet = properties_.Get(name, strVal);
      if (nRet == DEVICE_OK)
         val = atol(strVal.c_str());
      return nRet;
   }

   /**
    * Check if the property value is equal to a specific string
    * @return true only if property exists and is eqaul to, false otherwise
    * @param name - property identifier (name)
    * @param value - the value to compare to
    */
   bool IsPropertyEqualTo(const char* name, const char* val)
   {
      std::string strVal;
      int nRet = properties_.Get(name, strVal);
      if (nRet == DEVICE_OK)
         return strcmp(val, strVal.c_str()) == 0;
      else
         return false;
   }

   /**
   * Checks whether the property is read-only.
   * @param name - property identifier (name)
   * @param readOnly - read-only or not
   */
   int GetPropertyReadOnly(const char* name, bool& readOnly) const
   {
      MM::Property* pProp = properties_.Find(name);
      if (!pProp)
      {
         return DEVICE_INVALID_PROPERTY;
      }
      readOnly = pProp->GetReadOnly();

      return DEVICE_OK;
   }

   /**
   * Checks whether the property is read-only.
   * @param name - property identifier (name)
   * @param readOnly - read-only or not
   */
   int GetPropertyInitStatus(const char* name, bool& preInit) const
   {
      MM::Property* pProp = properties_.Find(name);
      if (!pProp)
      {
         return DEVICE_INVALID_PROPERTY;
      }
      preInit = pProp->GetInitStatus();

      return DEVICE_OK;
   }

   int HasPropertyLimits(const char* name, bool& hasLimits) const
   {
      MM::Property* pProp = properties_.Find(name);
      if (!pProp)
      {
         return DEVICE_INVALID_PROPERTY;
      }
      hasLimits = pProp->HasLimits();
      return DEVICE_OK;
   }

   int GetPropertyLowerLimit(const char* name, double& lowLimit) const
   {
      MM::Property* pProp = properties_.Find(name);
      if (!pProp)
      {
         return DEVICE_INVALID_PROPERTY;
      }
      lowLimit = pProp->GetLowerLimit();
      return DEVICE_OK;
   }

   int GetPropertyUpperLimit(const char* name, double& hiLimit) const
   {
      MM::Property* pProp = properties_.Find(name);
      if (!pProp)
      {
         return DEVICE_INVALID_PROPERTY;
      }
      hiLimit = pProp->GetUpperLimit();
      return DEVICE_OK;
   }

   /**
   * Obtains the property name given the index.
   * Can be used for enumerating properties.
   * @param idx - property index
   * @param name - property name
   */
   bool GetPropertyName(unsigned uIdx, char* name) const
   {
      std::string strName;
      if (!properties_.GetName(uIdx, strName))
         return false;

      CDeviceUtils::CopyLimitedString(name, strName.c_str());
      return true;
   }

   /**
   * Obtain property type (string, float or integer)
   */
   int GetPropertyType(const char* name, MM::PropertyType& pt) const
   {
      MM::Property* pProp = properties_.Find(name);
      if (!pProp)
         return DEVICE_INVALID_PROPERTY;

      pt = pProp->GetType();
      return DEVICE_OK;
   }

   /**
   * Sets the property value.
   * @param name - property name
   * @param value - propery value
   */
   int SetProperty(const char* name, const char* value)
   {
      return properties_.Set(name, value);
   }

   /**
   * Checks if device supports a given property.
   */
   bool HasProperty(const char* name) const
   {
      MM::Property* pProp = properties_.Find(name);
      if (pProp)
         return true;
      else
         return false;
   }

   /**
   * Returns the number of allowed property values.
   * If the set of property values is not defined, not bounded,
   * or property does not exist, the call returns 0.
   */
   unsigned GetNumberOfPropertyValues(const char* propertyName) const
   {
      MM::Property* pProp = properties_.Find(propertyName);
      if (!pProp)
         return 0;

      return (unsigned)pProp->GetAllowedValues().size();
   }

   /**
   * Returns the allowed value of the property, given its index.
   * Intended for enumerating allowed property values.
   * @param propertyName
   * @param index
   * @param value
   */
   bool GetPropertyValueAt(const char* propertyName, unsigned index, char* value) const
   {
      MM::Property* pProp = properties_.Find(propertyName);
      if (!pProp)
         return false;

      std::vector<std::string> values = pProp->GetAllowedValues();
      if (values.size() < index)
         return false;

      CDeviceUtils::CopyLimitedString(value, values[index].c_str()); 
      return true;
   }

   /**
   * Creates a new property for the device.
   * @param name - property name
   * @param value - initial value
   * @param eType - property type (string, integer or float)
   * @param readOnly - is the property read-only or not
   * @param pAct - function object called on the property actions
   * @param initStatus - initialization status of the property. True if the property must exist
   * prior to initialization, false if it doesn't matter. 
   */
   int CreateProperty(const char* name, const char* value, MM::PropertyType eType, bool readOnly, MM::ActionFunctor* pAct=0, bool initStatus=false)
   {
      return properties_.CreateProperty(name, value, eType, readOnly, pAct, initStatus);
   }

   /**
   * Define limits for properties with continous range of values
   */
   int SetPropertyLimits(const char* name, double low, double high)
   {
      MM::Property* pProp = properties_.Find(name);
      if (!pProp)
         return DEVICE_INVALID_PROPERTY;
      if (pProp->SetLimits(low, high))
         return DEVICE_OK;
      else
         return DEVICE_INVALID_PROPERTY_LIMTS;
   }

   /**
   * Sets an entire array of allowed values.
   */
   int SetAllowedValues(const char* name, std::vector<std::string>& values)
   {
      return properties_.SetAllowedValues(name, values);
   }

  /**
   * Clears allowed values, and makes any value valid.
   */
   int ClearAllowedValues(const char* name)
   {
      return properties_.ClearAllowedValues(name);
   }

   /**
   * Add a single allowed value.
   */
   int AddAllowedValue(const char* name, const char* value)
   {
      return properties_.AddAllowedValue(name, value);
   }

   /**
   * Add a single allowed value, plus an additional data.
   */
   int AddAllowedValue(const char* name, const char* value, long data)
   {
      return properties_.AddAllowedValue(name, value, data);
   }

   /**
   * Obtains data field associated with the allowed property value.
   */
   int GetPropertyData(const char* name, const char* value, long& data)
   {
      return properties_.GetPropertyData(name, value, data);
   }

   /**
   * Obtains data field associated with the currently appplied property value.
   */
   int GetCurrentPropertyData(const char* name, long& data)
   {
      return properties_.GetCurrentPropertyData(name, data);
   }

   /**
   * Rrefresh the entire state of the device and synchrnize property values with
   * the actual state of the hardware.
   */
   int UpdateStatus()
   {
      return properties_.UpdateAll();
   }

   /**
   * Update property value from the hardware.
   */
   int UpdateProperty(const char* name)
   {
      return properties_.Update(name);
   }


   /**
   * Apply the current property value to the hardware.
   */
   int ApplyProperty(const char* name)
   {
      return properties_.Apply(name);
   }

   /**
   * Obtains the error text associated with the error code.
   */
   bool GetErrorText(int errorCode, char* text) const
   {
      std::map<int, std::string>::const_iterator it;
      it = messages_.find(errorCode);
      if (it == messages_.end())
      {
         // generic message
         std::ostringstream osTxt;
         osTxt << "Error code " << errorCode << " (" << std::setbase(16) << errorCode << " hex)";
         CDeviceUtils::CopyLimitedString(text, osTxt.str().c_str());
         return false; // message text not found
      }
      else
      {
         // native message
         CDeviceUtils::CopyLimitedString(text, it->second.c_str());
         return true; // mesage found
      }
   }

   // acq context api
   // NOTE: experimental feature, do not count on these methods
   int AcqBefore() {return DEVICE_OK;}
   int AcqAfter() {return DEVICE_OK;}
   int AcqBeforeFrame() {return DEVICE_OK;}
   int AcqAfterFrame() {return DEVICE_OK;}
   int AcqBeforeStack() {return DEVICE_OK;}
   int AcqAfterStack() {return DEVICE_OK;}


   ////////////////////////////////////////////////////////////////////////////
   // Protected methods, for internal use by the device adapters 
   ////////////////////////////////////////////////////////////////////////////

protected:

   CDeviceBase() : module_(0), delayMs_(0), usesDelay_(false), callback_(0)
   {
      InitializeDefaultErrorMessages();
   }
   virtual ~CDeviceBase() {}

   /**
   * Defines the error text associated with the code.
   */
   void SetErrorText(int errorCode, const char* text)
   {
      messages_[errorCode] = text;
   }

   /**
   * Output the specified text message to the log stream.
   * @param msg - message text
   * @param debugOnly - if true the meassage will be sent only in the log-debug mode
   */
   int LogMessage(const char* msg, bool debugOnly = false) const
   {
      if (callback_)
         return callback_->LogMessage(this, msg, debugOnly);
      return DEVICE_NO_CALLBACK_REGISTERED;
   }

   /**
   * Output the specified text message to the log stream.
   * @param msg - message text
   * @param debugOnly - if true the meassage will be sent only in the log-debug mode
   */
   int LogMessage(std::string msg, bool debugOnly = false) const
   {
      if (callback_)
         return callback_->LogMessage(this, msg.c_str(), debugOnly);
      return DEVICE_NO_CALLBACK_REGISTERED;
   }

   /**
   * Outputs time difference between two time stamps.
   * Handy for hardware profiling
   * @param start - Time stamp for start of Process 
   * @param end - Time stamp for endof Process 
   * @param message - message that will be displayed in output
   * @param debugOnly - if true the meassage will be sent only in the log-debug mode
   */
   int LogTimeDiff(MM::MMTime start, MM::MMTime end, std::string message, bool debugOnly = false) const
   {
      std::ostringstream os;
      MM::MMTime t = end-start;
      os << message << t.sec_ << " seconds and " << t.uSec_ / 1000.0 << " msec";
      if (callback_)
         return callback_->LogMessage(this, os.str().c_str(), debugOnly);
      return DEVICE_NO_CALLBACK_REGISTERED;
   }

   /**
   * Outputs time difference between two time stamps.
   * Handy for hardware profiling
   * @param start - Time stamp for start of Process 
   * @param end - Time stamp for endof Process 
   * @param debugOnly - if true the meassage will be sent only in the log-debug mode
   */
   int LogTimeDiff(MM::MMTime start, MM::MMTime end, bool debugOnly = false) const
   {
      return LogTimeDiff(start, end, "Process took: " , debugOnly);
   }

   /**
   * Sets-up the standard set of error codes and error messages.
   */
   void InitializeDefaultErrorMessages()
   {
      // initialize error codes
      SetErrorText(DEVICE_ERR, g_Msg_ERR);
      SetErrorText(DEVICE_INVALID_PROPERTY, g_Msg_INVALID_PROPERTY);
      SetErrorText(DEVICE_INVALID_PROPERTY_VALUE, g_Msg_INVALID_PROPERTY_VALUE);
      SetErrorText(DEVICE_DUPLICATE_PROPERTY, g_Msg_DUPLICATE_PROPERTY);
      SetErrorText(DEVICE_INVALID_PROPERTY_TYPE, g_Msg_INVALID_PROPERTY_TYPE);
      SetErrorText(DEVICE_NATIVE_MODULE_FAILED, g_Msg_NATIVE_MODULE_FAILED);
      SetErrorText(DEVICE_UNSUPPORTED_DATA_FORMAT, g_Msg_UNSUPPORTED_DATA_FORMAT);
      SetErrorText(DEVICE_INTERNAL_INCONSISTENCY, g_Msg_INTERNAL_INCONSISTENCY);
      SetErrorText(DEVICE_NOT_SUPPORTED, g_Msg_NOT_SUPPORTED);
      SetErrorText(DEVICE_UNKNOWN_LABEL, g_Msg_UNKNOWN_LABEL);
      SetErrorText(DEVICE_UNSUPPORTED_COMMAND, g_Msg_UNSUPPORTED_COMMAND);
      SetErrorText(DEVICE_UNKNOWN_POSITION, g_Msg_UNKNOWN_POSITION);
      SetErrorText(DEVICE_DUPLICATE_LABEL, g_Msg_DEVICE_DUPLICATE_LABEL);
      SetErrorText(DEVICE_SERIAL_COMMAND_FAILED, g_Msg_SERIAL_COMMAND_FAILED);
      SetErrorText(DEVICE_SERIAL_INVALID_RESPONSE, g_Msg_SERIAL_INVALID_RESPONSE);
      SetErrorText(DEVICE_NONEXISTENT_CHANNEL, g_Msg_DEVICE_NONEXISTENT_CHANNEL);
      SetErrorText(DEVICE_INVALID_PROPERTY_LIMTS, g_Msg_DEVICE_INVALID_PROPERTY_LIMTS);
      SetErrorText(DEVICE_CAMERA_BUSY_ACQUIRING, g_Msg_DEVICE_CAMERA_BUSY_ACQUIRING);
      SetErrorText(DEVICE_CAN_NOT_SET_PROPERTY, g_Msg_DEVICE_CAN_NOT_SET_PROPERTY);
      SetErrorText(DEVICE_LOCALLY_DEFINED_ERROR, "t.b.d.");
      SetErrorText(DEVICE_NOT_CONNECTED, g_Msg_DEVICE_NOT_CONNECTED);
   }

   /**
   * Gets the handle (pointer) to the specified device label.
   * With this method we can get a handle to other devices loaded in the system,
   * if we know the device name.
   */
   MM::Device* GetDevice(const char* deviceLabel)
   {
      if (callback_)
         return callback_->GetDevice(this, deviceLabel);
      return 0;
   }

   /**
   * Returns a vector with strings listing the devices of the requested types
   */
   std::vector<std::string> GetLoadedDevicesOfType(MM::DeviceType devType)
   {
      if (callback_)
         return callback_->GetLoadedDevicesOfType(this, devType);
      std::vector<std::string> tmp;
      return tmp;
   }

   /**
   * Sends an aray of bytes to the com port.
   */
   int WriteToComPort(const char* portLabel, const unsigned char* buf, unsigned bufLength)
   {
      if (callback_)
         return callback_->WriteToSerial(this, portLabel, buf, bufLength); 

      return DEVICE_NO_CALLBACK_REGISTERED;
   }

   /**
   * Sends an ASCII string withe the specified terminting characters to the serial port.
   * @param portName
   * @param command - command string
   * @param term - terminating string, e.g. CR or CR,LF, or something else
   */
   int SendSerialCommand(const char* portName, const char* command, const char* term)
   {
      if (callback_)
         return callback_->SetSerialCommand(this, portName, command, term); 

      return DEVICE_NO_CALLBACK_REGISTERED;
   }

   /**
   * Gets the received string from the serial port, waiting for the
   * terminating character sequence.
   * @param portName
   * @param term - terminating string, e.g. CR or CR,LF, or something else
   * @param ans - answer string without the terminating characters
   */
   int GetSerialAnswer (const char* portName, const char* term, std::string& ans)
   {
      const unsigned long MAX_BUFLEN = 2000;
      char buf[MAX_BUFLEN];
      if (callback_)
      {
         int ret = callback_->GetSerialAnswer(this, portName, MAX_BUFLEN, buf, term); 
         if (ret != DEVICE_OK)
            return ret;
         ans = buf;
         return DEVICE_OK;
      }

      return DEVICE_NO_CALLBACK_REGISTERED;
   }

   /**
   * Reads the current contents of Rx serial buffer.
   */
   int ReadFromComPort(const char* portLabel, unsigned char* buf, unsigned bufLength, unsigned long& read)
   {
      if (callback_)
         return callback_->ReadFromSerial(this, portLabel, buf, bufLength, read);
      return DEVICE_NO_CALLBACK_REGISTERED;
   }

   /**
   * Clears the serial port buffers
   */
   int PurgeComPort(const char* portLabel)
   {
      if (callback_)
         return callback_->PurgeSerial(this, portLabel);
      return DEVICE_NO_CALLBACK_REGISTERED;
   }

   /**
   * Reads the current contents of Rx serial buffer.
   */
   MM::PortType GetPortType(const char* portLabel)
   {
      if (callback_)
         return callback_->GetSerialPortType(portLabel);
      return MM::InvalidPort;
   }

   /**
   * Device changed status
   * Not implemented in Core and callback
   */
   int OnStatusChanged()
   {
      if (callback_)
         return callback_->OnStatusChanged(this);
      return DEVICE_NO_CALLBACK_REGISTERED;
   }

   /**
   * Something changed in the property structure.
   * Signals the need for GUI update.
   * This function should be called only after the initialize function finished.
   * Calling it in the constructor or in the Initialize function will cause other 
   * device adapters to be called before they are initialized.
   */
   int OnPropertiesChanged()
   {
      if (callback_)
         return callback_->OnPropertiesChanged(this);
      return DEVICE_NO_CALLBACK_REGISTERED;
   }

   /* an instrument actual value, such as an X,Y,Z coordinate, which ought to be updated
   * rapidly in the UI, has changed - percolate notification to UI
   */

   int OnCoordinateUpdate()
   {
      if (callback_)
         return callback_->OnCoordinateUpdate(this);
      return DEVICE_NO_CALLBACK_REGISTERED;
   }

   /**
    * Device finished a task
    * Not implemented in Core and callback
    */
   int OnFinished()
   {
      if (callback_)
         return callback_->OnFinished(this);
      return DEVICE_NO_CALLBACK_REGISTERED;
   }

   /**
   * Gets the system ticks in microseconds.
   * OBSOLETE, use GetCurrentTime()
   */
   unsigned long GetClockTicksUs()
   {
      if (callback_)
         return callback_->GetClockTicksUs(this);

      return 0;
   }

   /**
   * Gets current time.
   */
   MM::MMTime GetCurrentMMTime()
   {
      if (callback_)
         return callback_->GetCurrentMMTime();

      return MM::MMTime(0.0);
   }

   /**
   * Check if we have callback mecahnism set up.
   */
   bool IsCallbackRegistered()
   {
      return callback_ == 0 ? false : true;
   }

   /**
   * Get the callback object.
   */
   MM::Core* GetCoreCallback()
   {
      return callback_;
   }

   /**
   * If this flag is set the device signals to the rest of the system that it will respond to delay settings.
   */
   void EnableDelay(bool state = true)
   {
      usesDelay_ = state;
   }

	MM::MMTime _start_time;
	MM::MMTime _end_time;

private:
   bool PropertyDefined(const char* propName) const
   {return properties_.Find(propName) != 0;}

   MM::PropertyCollection properties_;
   HDEVMODULE module_;
   std::string label_;
   std::string moduleName_;
   std::map<int, std::string> messages_;
   double delayMs_;
   bool usesDelay_;
   MM::Core* callback_;
};

/**
* Base class for creating generic devices.
*/
template <class U>
class CGenericBase : public CDeviceBase<MM::Device, U>
{
   MM::DeviceType GetType() const {return Type;}
   static const MM::DeviceType Type = MM::GenericDevice;
};

/**
* Base class for creating camera device adapters.
* This class has a functional constructor - must be invoked
* from the derived class.
*/
template <class U>
class CCameraBase : public CDeviceBase<MM::Camera, U>
{
public:
   using CDeviceBase<MM::Camera, U>::CreateProperty;
   using CDeviceBase<MM::Camera, U>::SetAllowedValues;
   using CDeviceBase<MM::Camera, U>::GetBinning;
   using CDeviceBase<MM::Camera, U>::GetCoreCallback;
   using CDeviceBase<MM::Camera, U>::GetImageBuffer;
   using CDeviceBase<MM::Camera, U>::GetImageWidth;
   using CDeviceBase<MM::Camera, U>::GetImageHeight;
   using CDeviceBase<MM::Camera, U>::GetImageBytesPerPixel;
   using CDeviceBase<MM::Camera, U>::SnapImage;
   using CDeviceBase<MM::Camera, U>::SetProperty;
   using CDeviceBase<MM::Camera, U>::LogMessage;

   CCameraBase() : busy_(false), thd_(0), stopOnOverflow_(false)
   {
      // create and intialize common transpose properties
      std::vector<std::string> allowedValues;
      allowedValues.push_back("0");
      allowedValues.push_back("1");
      CreateProperty(MM::g_Keyword_Transpose_SwapXY, "0", MM::Integer, false);
      SetAllowedValues(MM::g_Keyword_Transpose_SwapXY, allowedValues);
      CreateProperty(MM::g_Keyword_Transpose_MirrorX, "0", MM::Integer, false);
      SetAllowedValues(MM::g_Keyword_Transpose_MirrorX, allowedValues);
      CreateProperty(MM::g_Keyword_Transpose_MirrorY, "0", MM::Integer, false);
      SetAllowedValues(MM::g_Keyword_Transpose_MirrorY, allowedValues);
      CreateProperty(MM::g_Keyword_Transpose_Correction, "0", MM::Integer, false);
      SetAllowedValues(MM::g_Keyword_Transpose_Correction, allowedValues);

      thd_ = new BaseSequenceThread(this);
   }

   virtual ~CCameraBase()
   {
      if (!thd_->IsStopped()) {
         thd_->Stop();
         thd_->wait();
      }
      delete thd_;
   }

   virtual bool Busy() {return busy_;}

   /**
   * Continuous sequence acquisition.  
   * Default to sequence acquisition with a high number of images
   */
   virtual int StartSequenceAcquisition(double interval)
   {
      return StartSequenceAcquisition(LONG_MAX, interval, false);
   }

   /**
   * Stop and wait for the thread finished
   */
   virtual int StopSequenceAcquisition()
   {
      if (!thd_->IsStopped()) {
         thd_->Stop();
         thd_->wait();
      }

      return DEVICE_OK;
   }

   /**
   * Default implementation of the pixel size cscaling.
   */
   virtual double GetPixelSizeUm() const {return GetBinning();}

   virtual unsigned GetNumberOfComponents() const {return 1;}

   virtual int GetComponentName(unsigned channel, char* name)
   {
      if (channel > 0)
         return DEVICE_NONEXISTENT_CHANNEL;

      CDeviceUtils::CopyLimitedString(name, "Grayscale");
      return DEVICE_OK;
   }

   virtual const unsigned int* GetImageBufferAsRGB32()
   {
      return 0;
   }

   // temporary debug methods
   int PrepareSequenceAcqusition() {return DEVICE_UNSUPPORTED_COMMAND;}

   /**
   * Default implementation.
   */
   virtual int StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow)
   {
      if (IsCapturing())
         return DEVICE_CAMERA_BUSY_ACQUIRING;

      int ret = GetCoreCallback()->PrepareForAcq(this);
      if (ret != DEVICE_OK)
         return ret;
      thd_->Start(numImages,interval_ms);
      stopOnOverflow_ = stopOnOverflow;
      return DEVICE_OK;
   }

   virtual int InsertImage()
   {
      int ret = GetCoreCallback()->InsertImage(this, GetImageBuffer(), GetImageWidth(), GetImageHeight(), GetImageBytesPerPixel());
      if (!stopOnOverflow_ && ret == DEVICE_BUFFER_OVERFLOW)
      {
         // do not stop on overflow - just reset the buffer
         GetCoreCallback()->ClearImageBuffer(this);
         return GetCoreCallback()->InsertImage(this, GetImageBuffer(), GetImageWidth(), GetImageHeight(), GetImageBytesPerPixel());
      } else
         return ret;
   }

   //Do actual capturing
   //Called from inside the thread cicle 
   virtual int ThreadRun (void)
   {
      int ret=DEVICE_ERR;
      ret = SnapImage();
      if (ret != DEVICE_OK)
      {
         return ret;
      }
      ret = InsertImage();
      if (ret != DEVICE_OK)
      {
         return ret;
      }
      return ret;
   };
   virtual bool IsCapturing(){return !thd_->IsStopped();}
   
   class CaptureRestartHelper
   {
      bool restart_;
      CCameraBase* pCam_;
   public:
      CaptureRestartHelper(CCameraBase* pCam)
         :pCam_(pCam)
      {
         restart_=pCam_->IsCapturing();
      }
      operator bool()
      {
         return restart_;
      }
   };

protected:
   // called from the thread function before exit 
   virtual void OnThreadExiting() throw()
   {
      try
      {
         LogMessage(g_Msg_SEQUENCE_ACQUISITION_THREAD_EXITING);
         INVOKE_CALLBACK(AcqFinished(this, 0));
      }

		catch( CMMError& e){
			std::ostringstream oss;
			oss << g_Msg_EXCEPTION_IN_ON_THREAD_EXITING << " " << e.getMsg() << " " << e.getCode();
			LogMessage(oss.str().c_str(), false);
		}
      catch(...)
      {
         LogMessage(g_Msg_EXCEPTION_IN_ON_THREAD_EXITING, false);
      }
   }
protected:
   bool busy_;
   bool stopOnOverflow_;

   class BaseSequenceThread;
   BaseSequenceThread * thd_;
   friend class BaseSequenceThread;
   class BaseSequenceThread : public MMDeviceThreadBase
   {
      friend class CCameraBase;
      enum { default_numImages=1, default_intervalMS = 100 };
   public:
      BaseSequenceThread(CCameraBase* pCam)
         :intervalMs_(default_intervalMS)
         ,numImages_(default_numImages)
         ,imageCounter_(0)
         ,stop_(true)
         ,suspend_(false)
         ,camera_(pCam)
         ,startTime_(0)
         ,actualDuration_(0)
         ,lastFrameTime_(0)
      {};

      ~BaseSequenceThread() {}

      void Stop() {
         MMThreadGuard(this->stopLock_);
         stop_=true;
      }

      void Start(long numImages, double intervalMs)
      {
         MMThreadGuard(this->stopLock_);
         MMThreadGuard(this->suspendLock_);
         numImages_=numImages;
         intervalMs_=intervalMs;
         imageCounter_=0;
         stop_ = false;
         suspend_=false;
         activate();
         actualDuration_ = 0;
         startTime_= camera_->GetCurrentMMTime();
         lastFrameTime_ = 0;
      }
      bool IsStopped(){
         MMThreadGuard(this->stopLock_);
         return stop_;
      }
      void Suspend() {
         MMThreadGuard(this->suspendLock_);
         suspend_ = true;
      }
      bool IsSuspended() {
         MMThreadGuard(this->suspendLock_);
         return suspend_;
      }
      void Resume() {
         MMThreadGuard(this->suspendLock_);
         suspend_ = false;
      }
      double GetIntervalMs(){return intervalMs_;}
      void SetLength(long images) {numImages_ = images;}
		long GetLength() const {return numImages_;}

      long GetImageCounter(){return imageCounter_;}
      MM::MMTime GetStartTime(){return startTime_;}
      MM::MMTime GetActualDuration(){return actualDuration_;}

   private:
      int svc(void) throw()
      {
         int ret=DEVICE_ERR;
         try 
         {
            do
            {  
               ret=camera_->ThreadRun();
            } while (DEVICE_OK == ret && !IsStopped() && imageCounter_++ < numImages_-1);
            if (IsStopped())
               camera_->LogMessage("SeqAcquisition interrupted by the user\n");

			}catch( CMMError& e){
				camera_->LogMessage(e.getMsg(), false);
				ret = e.getCode();
			}catch(...){
            camera_->LogMessage(g_Msg_EXCEPTION_IN_THREAD, false);
         }
         stop_=true;
         actualDuration_ = camera_->GetCurrentMMTime() - startTime_;
         camera_->OnThreadExiting();
         return ret;
      }
   protected:
      CCameraBase* camera_;
      bool stop_;
      bool suspend_;
      long numImages_;
      long imageCounter_;
      double intervalMs_;
      MM::MMTime startTime_;
      MM::MMTime actualDuration_;
      MM::MMTime lastFrameTime_;
      MMThreadLock stopLock_;
      MMThreadLock suspendLock_;
   };
};

/**
* Base class for creating single axis stage adapters.
*/
template <class U>
class CStageBase : public CDeviceBase<MM::Stage, U>
{
   /**
   * Default implementation for the realative motion
   */
   using CDeviceBase<MM::Stage, U>::GetPositionUm;
   using CDeviceBase<MM::Stage, U>::SetPositionUm;
   int SetRelativePositionUm(double d)
   {
      double pos;
      int ret = GetPositionUm(pos);
      if (ret != DEVICE_OK)
         return ret;
      return SetPositionUm(pos + d);
   }

   int SetAdapterOriginUm(double /*d*/)
   {
      return DEVICE_UNSUPPORTED_COMMAND;
   }

   int Move(double /*velocity*/)
   {
      return DEVICE_UNSUPPORTED_COMMAND;
   }

};

/**
* Base class for creating dual axis stage adapters.
* This class has a functional constructor - must be invoked
* from the derived class.
*/
template <class U>
class CXYStageBase : public CDeviceBase<MM::XYStage, U>
{
   using CDeviceBase<MM::XYStage, U>::GetPositionUm;
   using CDeviceBase<MM::XYStage, U>::SetPositionUm;

public:
   CXYStageBase() : originXSteps_(0), originYSteps_(0)
   {
      // set-up directionality properties
      this->CreateProperty(MM::g_Keyword_Transpose_MirrorX, "0", MM::Integer, false);
      this->AddAllowedValue(MM::g_Keyword_Transpose_MirrorX, "0");
      this->AddAllowedValue(MM::g_Keyword_Transpose_MirrorX, "1");

      this->CreateProperty(MM::g_Keyword_Transpose_MirrorY, "0", MM::Integer, false);
      this->AddAllowedValue(MM::g_Keyword_Transpose_MirrorY, "0");
      this->AddAllowedValue(MM::g_Keyword_Transpose_MirrorY, "1");
   }


   int SetPositionUm(double x, double y)
   {
      bool mirrorX, mirrorY;
      GetOrientation(mirrorX, mirrorY);

      long xSteps = 0;
      long ySteps = 0;

      if (mirrorX)
         xSteps = originXSteps_ - (long) (x / this->GetStepSizeXUm() + 0.5);
      else
         xSteps = originXSteps_ + (long) (x / this->GetStepSizeXUm() + 0.5);
      if (mirrorY)
         ySteps = originYSteps_ - (long) (y / this->GetStepSizeYUm() + 0.5);
      else
         ySteps = originYSteps_ + (long) (y / this->GetStepSizeYUm() + 0.5);

      return this->SetPositionSteps(xSteps, ySteps);
   }

   /**
   * Default implementation for relative motion
   */
   int SetRelativePositionUm(double dx, double dy)
   {
      bool mirrorX, mirrorY;
      GetOrientation(mirrorX, mirrorY);

      if (mirrorX)
         dx = -dx;
      if (mirrorY)
         dy = -dy;

      return SetRelativePositionSteps((long)(dx / this->GetStepSizeXUm() + 0.5), (long)(dy / this->GetStepSizeYUm()));
   }

   /**
   * Defines position x,y (relative to current position) as the origin of our coordinate system
   * Get the current (stage-native) XY position
   */
   int SetAdapterOriginUm(double x, double y)
   {
      bool mirrorX, mirrorY;
      GetOrientation(mirrorX, mirrorY);

      long xStep, yStep;
      int ret = this->GetPositionSteps(xStep, yStep);
      if (ret != DEVICE_OK)                                                     
         return ret;                                 

      if (mirrorX)
         originXSteps_ = xStep + (long)(x / this->GetStepSizeXUm() + 0.5);
      else
         originXSteps_ = xStep - (long)(x / this->GetStepSizeXUm() + 0.5);
      if (mirrorY)
         originYSteps_ = yStep + (long)(y / this->GetStepSizeYUm() + 0.5);
      else
         originYSteps_ = yStep - (long)(y / this->GetStepSizeYUm() + 0.5);

      return DEVICE_OK;                                                         
   }                                                                            

   int GetPositionUm(double& x, double& y)
   {
      bool mirrorX, mirrorY;
      GetOrientation(mirrorX, mirrorY);

      long xSteps, ySteps;
      int ret = this->GetPositionSteps(xSteps, ySteps);
      if (ret != DEVICE_OK)
         return ret;

      if (mirrorX)
         x = (originXSteps_ - xSteps) * this->GetStepSizeXUm();
      else 
         x =  - ((originXSteps_ - xSteps) * this->GetStepSizeXUm());

      if (mirrorY)
         y = (originYSteps_ - ySteps) * this->GetStepSizeYUm();
      else 
         y = - ((originYSteps_ - ySteps) * this->GetStepSizeYUm());

      return DEVICE_OK;
   }

   /**
   * Default implementation.
   * The actual stage adapter should override it with the more
   * efficient implementation
   */ 
   int SetRelativePositionSteps(long x, long y)
   {
      long xSteps, ySteps;
      int ret = this->GetPositionSteps(xSteps, ySteps);
      if (ret != DEVICE_OK)
         return ret;

      return this->SetPositionSteps(xSteps+x, ySteps+y);
   }

   int Move(double /*vx*/, double /*vy*/)
   {
      return DEVICE_UNSUPPORTED_COMMAND;
   }

private:

   void GetOrientation(bool& mirrorX, bool& mirrorY) 
   {
      char val[MM::MaxStrLength];
      int ret = this->GetProperty(MM::g_Keyword_Transpose_MirrorX, val);
      assert(ret == DEVICE_OK);
      mirrorX = strcmp(val, "1") == 0 ? true : false;

      ret = this->GetProperty(MM::g_Keyword_Transpose_MirrorY, val);
      assert(ret == DEVICE_OK);
      mirrorY = strcmp(val, "1") == 0 ? true : false;
   }


   // absolute coordinate translation data
   long originXSteps_;
   long originYSteps_;
};

/**
* Base class for creating shutter device adapters.
*/
template <class U>
class CShutterBase : public CDeviceBase<MM::Shutter, U>
{
};

/**
* Base class for creating serial port device adapters.
*/
template <class U>
class CSerialBase : public CDeviceBase<MM::Serial, U>
{
};

/**
* Base class for creating auto-focusing modules.
*/
template <class U>
class CAutoFocusBase : public CDeviceBase<MM::AutoFocus, U>
{
   virtual int AutoSetParameters() {return DEVICE_UNSUPPORTED_COMMAND;}
};

/**
* Base class for creating image processing modules.
*/
template <class U>
class CImageProcessorBase : public CDeviceBase<MM::ImageProcessor, U>
{
};

/**
* Base class for creating ADC/DAC modules.
*/
template <class U>
class CSignalIOBase : public CDeviceBase<MM::SignalIO, U>
{
};

/**
* Base class for creating devices that can change magnification (NS).
*/
template <class U>
class CMagnifierBase : public CDeviceBase<MM::Magnifier, U>
{
};

/**
* Base class for creating SLM devices that can project images.
*/
template <class U>
class CSLMBase : public CDeviceBase<MM::SLM, U>
{

};

/**
* Base class for creating CommandDispatch adapters.
*/
template <class U>
class CommandDispatchBase : public CDeviceBase<MM::CommandDispatch, U>
{

};

/**
* Base class for creating state device adapters such as
* filter wheels, objective, turrets, etc.
*/
template <class U>
class CStateDeviceBase : public CDeviceBase<MM::State, U>
{
public:

   typedef CStateDeviceBase<U> CStateBase;

   CStateDeviceBase(): gateOpen_(true)
   {
      // set-up Position to move to when the state device's gate is closed
      // Allowed values should be set in the state device adapter
      // this->CreateProperty(MM::g_Keyword_Closed_Position, "0", MM::String, false);
   }

   /**
   * Sets the state (position) of the device based on the state index.
   * Assumes that "State" property is implemented for the device.
   */
   int SetPosition(long pos)
   {
      return this->SetProperty(MM::g_Keyword_State, CDeviceUtils::ConvertToString(pos));
   }

   /**
   * Sets the state (position) of the device based on the state label.
   * Assumes that "State" property is implemented for the device.
   */
   int SetPosition(const char* label)
   {
      std::map<std::string, long>::const_iterator it;
      it = labels_.find(label);
      if (it == labels_.end())
         return DEVICE_UNKNOWN_LABEL;

      return SetPosition(it->second);
   }


   /**
   * Implements a gate, i.e. a position where the state device is closed
   * The gate needs to be implemented in the adapter's 'OnState function
   * (which is called through SetPosition)
   */
   int SetGateOpen(bool open)
   {  
      if (gateOpen_ != open) {
         gateOpen_ = open;
         long position;
         int ret = GetPosition(position);
         if  (ret != DEVICE_OK)
            return ret;
         return SetPosition(position);
      }
      return DEVICE_OK;
   }

   int GetGateOpen(bool& open) 
   {
      open = gateOpen_; 
      return DEVICE_OK;
   }

   /**
   * Obtains the state (position) index of the device.
   * Assumes that "State" property is implemented for the device.
   */
   int GetPosition(long& pos) const
   {
      char buf[MM::MaxStrLength];
      assert(this->HasProperty(MM::g_Keyword_State));
      int ret = this->GetProperty(MM::g_Keyword_State, buf);
      if (ret == DEVICE_OK)
      {
         pos = atol(buf);
         return DEVICE_OK;
      }
      else
         return ret;
   }

   /**
   * Obtains the state (position) label of the device.
   * Assumes that "State" property is implemented for the device.
   */
   int GetPosition(char* label) const
   {
      long pos;
      int ret = GetPosition(pos);
      if (ret == DEVICE_OK)
         return GetPositionLabel(pos, label);
      else
         return ret;
   }

   /**
   * Obtains the label associated with the position (state).
   */
   int GetPositionLabel(long pos, char* label) const
   {
      std::map<std::string, long>::const_iterator it;
      for (it=labels_.begin(); it!=labels_.end(); it++)
      {
         //string devLabel = it->first;
         //long devPosition  = it->second;
         if (it->second == pos)
         {
            CDeviceUtils::CopyLimitedString(label, it->first.c_str());
            return DEVICE_OK;
         }
      }

      // label not found
      return DEVICE_UNKNOWN_POSITION;
   }

   /**
   * Creates new label for the given position, or overrides the existing one.
   */
   int SetPositionLabel(long pos, const char* label)
   {
      // first test if the label already exists with different position defined
      std::map<std::string, long>::iterator it;
      it = labels_.find(label);
      if (it != labels_.end() && it->second != pos)
      {
         // remove the existing one
         labels_.erase(it);
      }

      // then test if the given position already has a label
      for (it=labels_.begin(); it!=labels_.end(); it++)
         if (it->second == pos)
         {
            labels_.erase(it);
            break;
         }

         // finally we can add the new label-position mapping
         labels_[label] = pos;

         // attempt to define allowed values for label property (if it exists),
         // and don't make any fuss if the operation fails
         std::string strLabel(label);
         std::vector<std::string> values;
         for (it=labels_.begin(); it!=labels_.end(); it++)
            values.push_back(it->first);
         this->SetAllowedValues(MM::g_Keyword_Label, values);

         return DEVICE_OK;
   }

   /**
   * Obtains the position associated with a label.
   */
   int GetLabelPosition(const char* label, long& pos) const 
   {
      std::map<std::string, long>::const_iterator it;
      it = labels_.find(label);
      if (it == labels_.end())
         return DEVICE_UNKNOWN_LABEL;

      pos = it->second;
      return DEVICE_OK;
   }

   /**
   * Implements the default Label property action.
   */
   int OnLabel(MM::PropertyBase* pProp, MM::ActionType eAct)
   {
      if (eAct == MM::BeforeGet)
      {
         char buf[MM::MaxStrLength];
         int ret = GetPosition(buf);
         if (ret != DEVICE_OK)
            return ret;
         pProp->Set(buf);
      }
      else if (eAct == MM::AfterSet)
      {
         std::string label;
         pProp->Get(label);
         int ret = SetPosition(label.c_str());
         if (ret != DEVICE_OK)
            return ret;
      }
      return DEVICE_OK;
   }

private:
   bool gateOpen_;

private:
   std::map<std::string, long> labels_;
};


// _t, a macro for timing single lines.
// This macros logs the text of the line, x, measures
// the time it takes, and then logs that time.
// Usage example:
// _t( ex = GetExposure(); )


#define _t(x) \
   { \
      LogMessage(#x, true); \
      _start_time = GetCurrentMMTime(); \
      x; \
      _end_time = GetCurrentMMTime(); \
      LogTimeDiff(_start_time,_end_time, true); \
   }

#endif //_DEVICE_BASE_H_

