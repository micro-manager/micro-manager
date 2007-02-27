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
#include <assert.h>

#include <string>
#include <vector>
#include <iomanip>
#include <map>
#include <sstream>

// common error messages
const char* const g_Msg_ERR = "Name";
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
    * Returns device delay used for synhcronization by the calling code.
    * Delay of 0 means that the device should be synchronized by polling with the
    * Busy() method.
    */
   double GetDelayMs() const {return delayMs_;}

   /**
    * Returns the device delay used for synhcronization by the calling code.
    * Delay of 0 means that the device should be synchronized by polling with the
    * Busy() method.
    */
   void SetDelayMs(double delay) {delayMs_ = delay;}

   /**
    * Sets the callback for accessing parent functionality (used only by the calling code).
    */
   void SetCallback(MM::Core* cbk) {callback_ = cbk;}

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
    * Sets the property value.
    * @param name - property name
    * @param value - propery value
    */
   int SetProperty(const char* name, const char* value)
   {
      return properties_.Set(name, value);
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
    * Sets an entire array of allowed values.
    */
   int SetAllowedValues(const char* name, std::vector<std::string>& values)
   {
      return properties_.SetAllowedValues(name, values);
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

////////////////////////////////////////////////////////////////////////////
// Protected methods, for internal use by the device adapters 
////////////////////////////////////////////////////////////////////////////

protected:

   CDeviceBase() : module_(0), delayMs_(0), callback_(0)
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
   int LogMessage(const char* msg, bool debugOnly = false)
   {
      if (callback_)
         return callback_->LogMessage(this, msg, debugOnly);
      return DEVICE_NO_CALLBACK_REGISTERED;
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
    * Sends an aray of bytes to the com port.
    */
   int WriteToComPort(const char* portLabel, const char* buf, unsigned bufLength)
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
   int ReadFromComPort(const char* portLabel, char* buf, unsigned bufLength, unsigned long& read)
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
    * 
    */
   int OnStatusChanged()
   {
      if (callback_)
         return callback_->OnStatusChanged(this);
      return DEVICE_NO_CALLBACK_REGISTERED;
   }
   
   int OnFinished()
   {
      if (callback_)
         return callback_->OnFinished(this);
      return DEVICE_NO_CALLBACK_REGISTERED;
   }

   /**
    * Gets the system tecks in microseconds.
    */
   long GetClockTicksUs()
   {
      if (callback_)
         return callback_->GetClockTicksUs(this);

      return 0;
   }

   //void Sleep(double intervalMs)
   //{
   //   if (callback_)
   //      return callback_->Sleep(this, intervalMs);
   //}

   /**
    * Check if we have callback mecahnism set up.
    */
   bool IsCallbackRegistered()
   {
      return callback_ == 0 ? false : true;
   }

   /**
    * Get the callback obect.
    */
   MM::Core* GetCoreCallback()
   {
      return callback_;
   }
   
private:
   bool PropertyDefined(const char* propName) const
	   {return properties_.Find(propName) != 0;}

   MM::PropertyCollection properties_;
   HDEVMODULE module_;
   std::string label_;
   std::string moduleName_;
   std::map<int, std::string> messages_;
   double delayMs_;
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
 */
template <class U>
class CCameraBase : public CDeviceBase<MM::Camera, U>
{
};

/**
 * Base class for creating single axis stage adapters.
 */
template <class U>
class CStageBase : public CDeviceBase<MM::Stage, U>
{
};

/**
 * Base class for creating dual axis stage adapters.
 */
template <class U>
class CXYStageBase : public CDeviceBase<MM::XYStage, U>
{
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
 * Base class for creating state device adapters such as
 * filter wheels, objective, turrets, etc.
 */
template <class U>
class CStateDeviceBase : public CDeviceBase<MM::State, U>
{
public:

   typedef CStateDeviceBase<U> CStateBase;

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
    * Obtains the state (position) index of the device.
    * Assumes that "State" property is implemented for the device.
    */
   int GetPosition(long& pos) const
   {
      char buf[MM::MaxStrLength];
      // !!! we expect that State property is defined for this device, but
      // what if it isn't?
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
   std::map<std::string, long> labels_;
};

#endif //_DEVICE_BASE_H_
