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
//

#ifndef _DEVICE_BASE_H_
#define _DEVICE_BASE_H_



#include "MMDevice.h"
#include "MMDeviceConstants.h"
#include "Property.h"
#include "DeviceUtils.h"
#include "ModuleInterface.h"
#include "DeviceThreads.h"

#include <math.h>
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
const char* const g_Msg_DEVICE_COMM_HUB_MISSING= "Parent module (Hub) is not available or defined for this device!";
const char* const g_Msg_DEVICE_DUPLICATE_LIBRARY="Duplicate Device Library Name";
const char* const g_Msg_DEVICE_PROPERTY_NOT_SEQUENCEABLE="This property is not sequenceable";
const char* const g_Msg_DEVICE_SEQUENCE_TOO_LARGE="Sequence is too large for this device";
const char* const g_Msg_DEVICE_NOT_YET_IMPLEMENTED="This command has not yet been implemented for this devce.";

inline long nint( double value )
{
   return (long)floor( 0.5 + value);
};

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
   virtual HDEVMODULE GetModuleHandle() const {return module_;}

   /**
   * Assigns a name for the module (for use only by the calling code).
   */
   virtual void SetModuleName(const char* name)
   {
      moduleName_ = name;
   }

   /**
   * Returns the module name (for use only by the calling code).
   */
   virtual void GetModuleName(char* name) const
   {
      CDeviceUtils::CopyLimitedString(name, moduleName_.c_str());
   }
   
   /**
   * Assigns description string for a device (for use only by the calling code).
   */
   virtual void SetDescription(const char* descr)
   {
      description_ = descr;
   }

   /**
   * Returns device description (for use only by the calling code).
   */
   virtual void GetDescription(char* name) const
   {
      CDeviceUtils::CopyLimitedString(name, description_.c_str());
   }
   
   /**
   * Sets the library handle (for use only by the calling code).
   */
   virtual void SetModuleHandle(HDEVMODULE hModule) {module_ = hModule;}

   /**
   * Sets the device label (for use only by the calling code).
   * Labels are usually manipulated by the parent application and used
   * for high-level programming.
   */
   virtual void SetLabel(const char* label)
   {
      label_ = label;
   }

   /**
   * Returns the device label (for use only by the calling code).
   * Labels are usually manipulated by the parent application and used
   * for high-level programming.
   */
   virtual void GetLabel(char* name) const
   {
      CDeviceUtils::CopyLimitedString(name, label_.c_str());
   }

   /**
   * Returns device delay used for synchronization by the calling code.
   * Delay of 0 means that the device should be synchronized by polling with the
   * Busy() method.
   */
   virtual double GetDelayMs() const {return delayMs_;}

   /**
   * Returns the device delay used for synchronization by the calling code.
   * Delay of 0 means that the device should be synchronized by polling with the
   * Busy() method.
   */
   virtual void SetDelayMs(double delay) {delayMs_ = delay;}

   /**
   * Sets the callback for accessing parent functionality (used only by the calling code).
   */
   virtual void SetCallback(MM::Core* cbk) {callback_ = cbk;}

   /**
   * Signals if the device responds to different delay settings.
   * Default device behavior is to ignore delays and use busy signals instead.
   */
   virtual bool UsesDelay() {return usesDelay_;}

   /**
   * Returns the number of properties.
   */
   virtual unsigned GetNumberOfProperties() const {return (unsigned)properties_.GetSize();}

   /**
   * Obtains the value of the property.
   * @param name - property identifier (name)
   * @param value - the value of the property
   */
   virtual int GetProperty(const char* name, char* value) const
   {
      std::string strVal;
      // additional information for reporting invalid properties.
      SetMorePropertyErrorInfo(name);
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
   bool IsPropertyEqualTo(const char* name, const char* val) const
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
   virtual int GetPropertyReadOnly(const char* name, bool& readOnly) const
   {
      MM::Property* pProp = properties_.Find(name);
      if (!pProp)
      {
         // additional information for reporting invalid properties.
         SetMorePropertyErrorInfo(name);
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
   virtual int GetPropertyInitStatus(const char* name, bool& preInit) const
   {
      MM::Property* pProp = properties_.Find(name);
      if (!pProp)
      {
         // additional information for reporting invalid properties.
         SetMorePropertyErrorInfo(name);
         return DEVICE_INVALID_PROPERTY;
      }
      preInit = pProp->GetInitStatus();

      return DEVICE_OK;
   }

   virtual int HasPropertyLimits(const char* name, bool& hasLimits) const
   {
      MM::Property* pProp = properties_.Find(name);
      if (!pProp)
      {
         // additional information for reporting invalid properties.
         SetMorePropertyErrorInfo(name);
         return DEVICE_INVALID_PROPERTY;
      }
      hasLimits = pProp->HasLimits();
      return DEVICE_OK;
   }

   /**
    * Provides lower limit for a property that has property limits
    * @param name - property identifier (name)
    * @param lowLimit - returns lower limit
    */
   virtual int GetPropertyLowerLimit(const char* name, double& lowLimit) const
   {
      MM::Property* pProp = properties_.Find(name);
      if (!pProp)
      {
         // additional information for reporting invalid properties.
         SetMorePropertyErrorInfo(name);
         return DEVICE_INVALID_PROPERTY;
      }
      lowLimit = pProp->GetLowerLimit();
      return DEVICE_OK;
   }

   /**
    * Provides upper limit for a property that has property limits
    * @param name - property identifier (name)
    * @param hiLimit - returns upper limit
    */
   virtual int GetPropertyUpperLimit(const char* name, double& hiLimit) const
   {
      MM::Property* pProp = properties_.Find(name);
      if (!pProp)
      {
         // additional information for reporting invalid properties.
         SetMorePropertyErrorInfo(name);
         return DEVICE_INVALID_PROPERTY;
      }
      hiLimit = pProp->GetUpperLimit();
      return DEVICE_OK;
   }

   /**
   * Checks whether the property can be run in a sequence
   * @param name - property identifier (name)
   * @param sequenceable - sequenceable or not
   */
   virtual int IsPropertySequenceable(const char* name, bool& sequenceable) const
   {
      MM::Property* pProp = properties_.Find(name);
      if (!pProp)
      {
         // additional information for reporting invalid properties.
         SetMorePropertyErrorInfo(name);
         return DEVICE_INVALID_PROPERTY;
      }
      sequenceable = pProp->IsSequenceable();

      return DEVICE_OK;
   }

   /**
   * Provides the maximum number of events that can be executed by this sequenceable property
   * @param name - property identifier (name)
   * @param nrEvents - maximum number of events that can be handles by the device
   */
   virtual int GetPropertySequenceMaxLength(const char* name, long& nrEvents) const
   {
      MM::Property* pProp = properties_.Find(name);
      if (!pProp)
      {
         // additional information for reporting invalid properties.
         SetMorePropertyErrorInfo(name);
         return DEVICE_INVALID_PROPERTY;
      }
      bool sequenceable;
      int ret = IsPropertySequenceable(name, sequenceable);
      if (ret != DEVICE_OK)
         return ret;
      if (!sequenceable) {
         SetMorePropertyErrorInfo(name);
         return DEVICE_PROPERTY_NOT_SEQUENCEABLE;
      }

      nrEvents = pProp->GetSequenceMaxSize();

      return DEVICE_OK;
   }
   
   /**
    * Starts a (TTL-triggered) sequence for the given property
    * Should be overridden by the device adapter (when a sequence is implemented)
    * @param  name - property for which the sequence should be started
    */
   virtual int StartPropertySequence(const char* name)
   {
      MM::Property* pProp = properties_.Find(name);
      if (!pProp)
      {
         // additional information for reporting invalid properties.
         SetMorePropertyErrorInfo(name);
         return DEVICE_INVALID_PROPERTY;
      }
      bool sequenceable;
      int ret = IsPropertySequenceable(name, sequenceable);
      if (ret != DEVICE_OK)
         return ret;
      if (!sequenceable) {
         SetMorePropertyErrorInfo(name);
         return DEVICE_PROPERTY_NOT_SEQUENCEABLE;
      }

      return pProp->StartSequence();
   }

   /**
    * Stops a (TTL-triggered) sequence for the given property
    * Should be overridden by the device adapter (when a sequence is implemented)
    * @param  name - property for which the sequence should be started
    */
   virtual int StopPropertySequence(const char* name)
   {
      MM::Property* pProp = properties_.Find(name);
      if (!pProp)
      {
         SetMorePropertyErrorInfo(name);
         return DEVICE_INVALID_PROPERTY;
      }
      bool sequenceable;
      int ret = IsPropertySequenceable(name, sequenceable);
      if (ret != DEVICE_OK)
         return ret;
      if (!sequenceable) {
         SetMorePropertyErrorInfo(name);
         return DEVICE_PROPERTY_NOT_SEQUENCEABLE;
      }

      return pProp->StopSequence();
   }

   /**
    * This function is used by the Core to communicate a sequence to the device
    * @param name - name of the sequenceable property
    */
   virtual int ClearPropertySequence(const char* name)
   {
      MM::Property* pProp;
      int ret = GetSequenceableProperty(&pProp, name);
      if (ret != DEVICE_OK)
         return ret;

      return pProp->ClearSequence();
   }

   /**
    * This function is used by the Core to communicate a sequence to the device
    * @param name - name of the sequenceable property
    */
   virtual int AddToPropertySequence(const char* name, const char* value)
   {
      MM::Property* pProp;
      int ret = GetSequenceableProperty(&pProp, name);
      if (ret != DEVICE_OK)
         return ret;

      return pProp->AddToSequence(value);
   }

   /**
    * This function is used by the Core to communicate a sequence to the device
    * Sends the sequence to the device by calling the properties functor 
    * @param name - name of the sequenceable property
    */
   virtual int SendPropertySequence(const char* name)
   {
      MM::Property* pProp;
      int ret = GetSequenceableProperty(&pProp, name);
      if (ret != DEVICE_OK)
         return ret;

      return pProp->SendSequence();
   }

   /**
   * Obtains the property name given the index.
   * Can be used for enumerating properties.
   * @param idx - property index
   * @param name - property name
   */
   virtual bool GetPropertyName(unsigned uIdx, char* name) const
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
   virtual int GetPropertyType(const char* name, MM::PropertyType& pt) const
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
   virtual int SetProperty(const char* name, const char* value)
   {
      int ret = properties_.Set(name, value);
      if( DEVICE_OK != ret)
      {
         // additional information for reporting invalid properties.
         SetMorePropertyErrorInfo(name);

      }
      return ret;
   }

   /**
   * Checks if device supports a given property.
   */
   virtual bool HasProperty(const char* name) const
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
   virtual unsigned GetNumberOfPropertyValues(const char* propertyName) const
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
   virtual bool GetPropertyValueAt(const char* propertyName, unsigned index, char* value) const
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
   * @param isPreInitProperty - whether to create a "pre-init" property, whose
   * value will be available before Initialize() is called
   */
   int CreateProperty(const char* name, const char* value, MM::PropertyType eType, bool readOnly, MM::ActionFunctor* pAct=0, bool isPreInitProperty=false)
   {
      return properties_.CreateProperty(name, value, eType, readOnly, pAct, isPreInitProperty);
   }

   /**
   * Creates a new property for the device.
   * @param name - property name
   * @param value - initial value
   * @param eType - property type (string, integer or float)
   * @param readOnly - is the property read-only or not
   * @param memberFunction - Function pointer to the device object "OnProperty" member function, e.g. &MyDevice::OnState
   * @param isPreInitProperty - whether to create a "pre-init" property, whose
   * value will be available before Initialize() is called
   */
   int CreatePropertyWithHandler(const char* name, const char* value, MM::PropertyType eType, bool readOnly,
                                 int(U::*memberFunction)(MM::PropertyBase* pProp, MM::ActionType eAct), bool isPreInitProperty=false) {
      CPropertyAction* pAct = new CPropertyAction((U*) this, memberFunction);
      return CreateProperty(name, value, eType, readOnly, pAct, isPreInitProperty);
   }

   /**
    * Create an integer-valued property for the device
    */
   int CreateIntegerProperty(const char* name, long value, bool readOnly, MM::ActionFunctor* pAct = 0, bool isPreInitProperty = false)
   {
      // Note: in theory, we can avoid converting to string and back. At this
      // moment, it is not worth the trouble.
      std::ostringstream oss;
      oss << value;
      return CreateProperty(name, oss.str().c_str(), MM::Integer, readOnly, pAct, isPreInitProperty);
   }

   /**
    * Create a float-valued property for the device
    */
   int CreateFloatProperty(const char* name, double value, bool readOnly, MM::ActionFunctor* pAct = 0, bool isPreInitProperty = false)
   {
      // Note: in theory, we can avoid converting to string and back. At this
      // moment, it is not worth the trouble.
      //
      // However, note the following assumtion being made here: the default
      // settings of std::ostream will return strings with a decimal precision
      // of 6 digits. When this eventually gets passed to
      // MM::FloatProperty::Set(double), it gets truncated to 4 digits before
      // being stored. Thus, we do not loose any information.
      std::ostringstream oss;
      oss << value;
      return CreateProperty(name, oss.str().c_str(), MM::Float, readOnly, pAct, isPreInitProperty);
   }

   /**
    * Create a string-valued property for the device
    */
   int CreateStringProperty(const char* name, const char* value, bool readOnly, MM::ActionFunctor* pAct = 0, bool isPreInitProperty = false)
   {
      return CreateProperty(name, value, MM::String, readOnly, pAct, isPreInitProperty);
   }

   /**
   * Define limits for properties with continous range of values
   */
   int SetPropertyLimits(const char* name, double low, double high)
   {
      MM::Property* pProp = properties_.Find(name);
      if (!pProp)
      {
         SetMorePropertyErrorInfo(name);
         return DEVICE_INVALID_PROPERTY;
      }
      if (pProp->SetLimits(low, high))
         return DEVICE_OK;
      else {
         std::ostringstream os;
         os << "Device adapter requests invalid values ( " << low << ", ";
         os << high << ") for property: " << name;
         LogMessage(os.str().c_str(), false); 
         return DEVICE_INVALID_PROPERTY_LIMTS;
      }
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
      int ret = properties_.GetPropertyData(name, value, data);
      if( DEVICE_OK != ret)
        // additional information for reporting invalid properties.
         SetMorePropertyErrorInfo(name);

      return ret;
   }

   /**
   * Obtains data field associated with the currently appplied property value.
   */
   int GetCurrentPropertyData(const char* name, long& data)
   {
      int ret = properties_.GetCurrentPropertyData(name, data);
      if( DEVICE_OK != ret)
        // additional information for reporting invalid properties.
         SetMorePropertyErrorInfo(name);

      return ret;
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
   virtual bool GetErrorText(int errorCode, char* text) const
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
         std::ostringstream stringStreamMessage;
         stringStreamMessage << it->second.c_str();
         // add the additional 'property' error info.
         if( 2<=errorCode && errorCode<=5 )
         {
            stringStreamMessage << ": " << GetMorePropertyErrorInfo();
         }
         SetMorePropertyErrorInfo("");
         // native message
         CDeviceUtils::CopyLimitedString(text, stringStreamMessage.str().c_str());
         return true; // mesage found
      }
   }

   // acq context api
   // NOTE: experimental feature, do not count on these methods
   virtual int AcqBefore() {return DEVICE_OK;}
   virtual int AcqAfter() {return DEVICE_OK;}
   virtual int AcqBeforeFrame() {return DEVICE_OK;}
   virtual int AcqAfterFrame() {return DEVICE_OK;}
   virtual int AcqBeforeStack() {return DEVICE_OK;}
   virtual int AcqAfterStack() {return DEVICE_OK;}

   // device discovery (auto-configuration)
   virtual MM::DeviceDetectionStatus DetectDevice(void){ 
      return  MM::Unimplemented;
   };

   // hub - peripheral relationship
   virtual void SetParentID(const char* parentId)
   {
      parentID_ = parentId;

      // truncate if necessary
      if (parentID_.size() >= (unsigned) MM::MaxStrLength)
         parentID_ = parentID_.substr(MM::MaxStrLength-1);

      if (this->HasProperty(MM::g_Keyword_HubID))
      {
         this->SetProperty(MM::g_Keyword_HubID, parentID_.c_str());
      }
   }
   
   virtual void GetParentID(char* parentID) const
   {
      CDeviceUtils::CopyLimitedString(parentID, parentID_.c_str());
   }
   
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

   const char* GetMorePropertyErrorInfo(void) const
   {
      return morePropertyErrorInfo_.c_str();
   }

   void SetMorePropertyErrorInfo( const char* ptext) const
   {
      morePropertyErrorInfo_ = ptext;
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
   int LogMessage(const std::string& msg, bool debugOnly = false) const
   {
      if (callback_)
         return callback_->LogMessage(this, msg.c_str(), debugOnly);
      return DEVICE_NO_CALLBACK_REGISTERED;
   }

   /**
   * Output the  text message of specified code to the log stream.
   * @param errorCode - error code
   * @param debugOnly - if true the meassage will be sent only in the log-debug mode
   */
   int LogMessageCode(const int errorCode, bool debugOnly = false) const
   {
      if (callback_)
      {
         char text[MM::MaxStrLength];
         GetErrorText(errorCode, text);
         return callback_->LogMessage(this, text, debugOnly);
      }
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
   int LogTimeDiff(MM::MMTime start, MM::MMTime end, const std::string& message, bool debugOnly = false) const
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
      SetErrorText(DEVICE_COMM_HUB_MISSING, g_Msg_DEVICE_COMM_HUB_MISSING);
      SetErrorText(DEVICE_DUPLICATE_LIBRARY, g_Msg_DEVICE_DUPLICATE_LIBRARY);
      SetErrorText(DEVICE_PROPERTY_NOT_SEQUENCEABLE, g_Msg_DEVICE_PROPERTY_NOT_SEQUENCEABLE);
      SetErrorText(DEVICE_SEQUENCE_TOO_LARGE, g_Msg_DEVICE_SEQUENCE_TOO_LARGE);
      SetErrorText(DEVICE_NOT_YET_IMPLEMENTED, g_Msg_DEVICE_NOT_YET_IMPLEMENTED);
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
   // Microsoft compiler has trouble generating code to transport stl objects across DLL boundary
   // so we use char*. Other compilers could conceivably have similar trouble, if for example,
   // a dynamic library is linked with a different CRT than its client.
   void GetLoadedDeviceOfType(MM::DeviceType devType, char* deviceName, const unsigned int deviceIterator )
   {
      deviceName[0] = 0;
      if (callback_)
      {
         callback_->GetLoadedDeviceOfType( this, devType, deviceName, deviceIterator);
      }
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
   * Not to be confused with MM::PortType MM::Serial::GetPortType() const.
   */
   MM::PortType GetSerialPortType(const char* portLabel)
   {
      if (callback_)
         return callback_->GetSerialPortType(portLabel);
      return MM::InvalidPort;
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

   /**
    * Signals to the core that a property value has changed.
    */
   int OnPropertyChanged(const char* propName, const char* propValue)
   {
      if (callback_)
         return callback_->OnPropertyChanged(this, propName, propValue);
      return DEVICE_NO_CALLBACK_REGISTERED;
   }

   /* 
    * Signals that the stage has arrived at a new position
   */
   int OnStagePositionChanged(double pos)
   {
      if (callback_)
         return callback_->OnStagePositionChanged(this, pos);
      return DEVICE_NO_CALLBACK_REGISTERED;
   }

   /* 
   */
   int OnXYStagePositionChanged(double xPos, double yPos)
   {
      if (callback_)
         return callback_->OnXYStagePositionChanged(this, xPos, yPos);
      return DEVICE_NO_CALLBACK_REGISTERED;
   }

   /*
    */
   int OnExposureChanged(double exposure)
   {
      if (callback_)
         return callback_->OnExposureChanged(this, exposure);
      return DEVICE_NO_CALLBACK_REGISTERED;
   }

   /*
    */
   int OnSLMExposureChanged(double exposure)
   {
      if (callback_)
         return callback_->OnSLMExposureChanged(this, exposure);
      return DEVICE_NO_CALLBACK_REGISTERED;
   }

   /**
    */
   int OnMagnifierChanged()
   {
      if (callback_)
         return callback_->OnMagnifierChanged(this);
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
   bool IsCallbackRegistered() const
   {
      return callback_ == 0 ? false : true;
   }

   /**
   * Get the callback object.
   */
   MM::Core* GetCoreCallback() const
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

   /**
    * Utility method to create read-only property displaying parentID (hub label).
    * By looking at this HubID property we can see which hub this peripheral belongs to.
    * Can be called anywhere in the device code, but the most logical place is the constructor.
    * Use is optional, to provide useful info.
    */
   void CreateHubIDProperty()
   {
      char pid[MM::MaxStrLength];
      this->GetParentID(pid);
      this->CreateProperty(MM::g_Keyword_HubID, pid, MM::String, false);
   }

   /**
    * Returns the parent Hub device pointer, or null if there isn't any.
    * GetParentHub() call Makes sure that the hub pointer belongs to a class from the same
    * module (device library). This is to avoid using dynamic_cast<> which
    * won't work for Linux.
    */
   MM::Hub* GetParentHub() const
   {
      if (IsCallbackRegistered())
         return GetCoreCallback()->GetParentHub(this);
      
      return 0;
   }

   /**
    * Returns the parent Hub device pointer, or null if there isn't any.
    * Makes sure the Parent ID has been assigned.
    */
   template<class T_HUB>
   T_HUB* AssignToHub() {
      T_HUB* hub = static_cast<T_HUB*>(GetParentHub());
      if (hub == NULL) {
         LogMessage("Parent hub not defined.");
      } else {
         char hubLabel[MM::MaxStrLength];
         hub->GetLabel(hubLabel);
         SetParentID(hubLabel); // for backward comp.
      }
      return hub;
   }

private:
   bool PropertyDefined(const char* propName) const
   {
      return properties_.Find(propName) != 0;
   }

   /**
    * Finds a property by name and determines whether it is a sequenceable property
    * @param pProp - pointer to pointer used to return the property if found
    * @param name - name of the property we are looking for
    * @return - DEVICE_OK, DEVICE_INVALID_PROPERTY (if not found), 
    *             or DEVICE_PROPERTY_NOT_SEQUENCEABLE
    */
   int GetSequenceableProperty(MM::Property** pProp, const char* name) const
   {
      *pProp = properties_.Find(name);
      if (!*pProp)
      {
         SetMorePropertyErrorInfo(name);
         return DEVICE_INVALID_PROPERTY;
      }

      bool sequenceable = (*pProp)->IsSequenceable();
      if (!sequenceable) {
         SetMorePropertyErrorInfo(name);
         return DEVICE_PROPERTY_NOT_SEQUENCEABLE;
      }
      return DEVICE_OK;
   }


   MM::PropertyCollection properties_;
   HDEVMODULE module_;
   std::string label_;
   std::string moduleName_;
   std::string description_;
   std::map<int, std::string> messages_;
   double delayMs_;
   bool usesDelay_;
   MM::Core* callback_;
   // specific information about the errant property, etc.
   mutable std::string morePropertyErrorInfo_;
   std::string parentID_;
};

// Forbid instantiation of CDeviceBase<MM::Device, U>
// (It was abused in the past.)
template <class U>
class CDeviceBase<MM::Device, U>
{
   CDeviceBase(); // private; construction disallowed
};

/**
* Base class for creating generic devices.
*/
template <class U>
class CGenericBase : public CDeviceBase<MM::Generic, U>
{
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
   using CDeviceBase<MM::Camera, U>::SetProperty;
   using CDeviceBase<MM::Camera, U>::LogMessage;
   virtual const unsigned char* GetImageBuffer() = 0;
   virtual unsigned GetImageWidth() const = 0;
   virtual unsigned GetImageHeight() const = 0;
   virtual unsigned GetImageBytesPerPixel() const = 0;
   virtual int SnapImage() = 0;

   CCameraBase() : busy_(false), stopWhenCBOverflows_(false), thd_(0)
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
   * Default implementation of the pixel size scaling.
   */
   virtual double GetPixelSizeUm() const {return GetBinning();}

   virtual unsigned GetNumberOfComponents() const 
   {
      return 1;
   }

   virtual int GetComponentName(unsigned channel, char* name)
   {
      if (channel > 0)
         return DEVICE_NONEXISTENT_CHANNEL;

      CDeviceUtils::CopyLimitedString(name, "Grayscale");
      return DEVICE_OK;
   }

   /**
    * Multi-Channel cameras use this function to indicate how many channels they 
    * provide.  Single channel cameras do not need to override this
    */
   virtual unsigned GetNumberOfChannels() const 
   {
      return 1;
   }

   /**
    * Multi-channel cameras should provide names for their channels
    * Single cahnnel cameras do not need to override this default implementation
    */
   virtual int GetChannelName(unsigned /* channel */, char* name)
   {
      CDeviceUtils::CopyLimitedString(name, "");
      return DEVICE_OK;
   }

   /**
    * Version of GetImageBuffer for multi-channel cameras
    * Single channel (standard) cameras do not need to override this
    */
   virtual const unsigned char* GetImageBuffer(unsigned /* channelNr */)
   {
      if (GetNumberOfChannels() == 1)
         return GetImageBuffer();
      return 0;
   }

   virtual const unsigned int* GetImageBufferAsRGB32()
   {
      return 0;
   }

   /*
    * Fills serializedMetadata with the device's metadata tags.
    */
   virtual void GetTags(char* serializedMetadata)
   {
      std::string data = metadata_.Serialize();
      data.copy(serializedMetadata, data.size(), 0);
   }

   // temporary debug methods
   virtual int PrepareSequenceAcqusition() {return DEVICE_OK;}

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
      stopWhenCBOverflows_ = stopOnOverflow;
      return DEVICE_OK;
   }

   virtual int GetExposureSequenceMaxLength(long& /*nrEvents*/) const 
   {
      return DEVICE_UNSUPPORTED_COMMAND;
   }

   virtual int StartExposureSequence()  
   {
      return DEVICE_UNSUPPORTED_COMMAND;
   }

   virtual int StopExposureSequence() 
   {
      return DEVICE_UNSUPPORTED_COMMAND;
   }

   virtual int ClearExposureSequence() 
   {
      return DEVICE_UNSUPPORTED_COMMAND;
   }

   virtual int AddToExposureSequence(double /*exposureTime_ms*/) 
   {
      return DEVICE_UNSUPPORTED_COMMAND;
   }

   virtual int SendExposureSequence() const 
   {
      return DEVICE_UNSUPPORTED_COMMAND;
   }

   virtual bool IsCapturing(){return !thd_->IsStopped();}
   
   virtual void AddTag(const char* key, const char* deviceLabel, const char* value)
   {
      metadata_.PutTag(key, deviceLabel, value);
   }


   virtual void RemoveTag(const char* key)
   {
      metadata_.RemoveTag(key);
   }

protected:
   /////////////////////////////////////////////
   // utility methods for use by derived classes
   // //////////////////////////////////////////

   virtual std::vector<std::string> GetTagKeys()
   {
      return metadata_.GetKeys();
   }

   virtual std::string GetTagValue(const char* key)
   {
      return metadata_.GetSingleTag(key).GetValue();
   }

   // Do actual capturing
   // Called from inside the thread
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

   virtual int InsertImage()
   {
      char label[MM::MaxStrLength];
      this->GetLabel(label);
      Metadata md;
      md.put("Camera", label);
      int ret = GetCoreCallback()->InsertImage(this, GetImageBuffer(), GetImageWidth(),
         GetImageHeight(), GetImageBytesPerPixel(),
         md.Serialize().c_str());
      if (!stopWhenCBOverflows_ && ret == DEVICE_BUFFER_OVERFLOW)
      {
         // do not stop on overflow - just reset the buffer
         GetCoreCallback()->ClearImageBuffer(this);
         return GetCoreCallback()->InsertImage(this, GetImageBuffer(), GetImageWidth(),
            GetImageHeight(), GetImageBytesPerPixel(),
            md.Serialize().c_str());
      } else
         return ret;
   }

   virtual double GetIntervalMs() {return thd_->GetIntervalMs();}
   virtual long GetImageCounter() {return thd_->GetImageCounter();}
   virtual long GetNumberOfImages() {return thd_->GetNumberOfImages();}

   // called from the thread function before exit 
   virtual void OnThreadExiting() throw()
   {
      try
      {
         LogMessage(g_Msg_SEQUENCE_ACQUISITION_THREAD_EXITING);
         GetCoreCallback()?GetCoreCallback()->AcqFinished(this,0):DEVICE_OK;
      }
      catch(...)
      {
         LogMessage(g_Msg_EXCEPTION_IN_ON_THREAD_EXITING, false);
      }
   }

   virtual bool isStopOnOverflow() {return stopWhenCBOverflows_;}
   virtual void setStopOnOverflow(bool stop) {stopWhenCBOverflows_ = stop;}

   ////////////////////////////////////////////////////////////////////////////
   // Helper Class
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
   ////////////////////////////////////////////////////////////////////////////

   // Nested class for live streaming
   ////////////////////////////////////////////////////////////////////////////
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
         MMThreadGuard g(this->stopLock_);
         stop_=true;
      }

      void Start(long numImages, double intervalMs)
      {
         MMThreadGuard g1(this->stopLock_);
         MMThreadGuard g2(this->suspendLock_);
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
         MMThreadGuard g(this->stopLock_);
         return stop_;
      }
      void Suspend() {
         MMThreadGuard g(this->suspendLock_);
         suspend_ = true;
      }
      bool IsSuspended() {
         MMThreadGuard g(this->suspendLock_);
         return suspend_;
      }
      void Resume() {
         MMThreadGuard g(this->suspendLock_);
         suspend_ = false;
      }
      double GetIntervalMs(){return intervalMs_;}
      void SetLength(long images) {numImages_ = images;}
      //long GetLength() const {return numImages_;}

      long GetImageCounter(){return imageCounter_;}
      MM::MMTime GetStartTime(){return startTime_;}
      MM::MMTime GetActualDuration(){return actualDuration_;}

      CCameraBase* GetCamera() {return camera_;}
      long GetNumberOfImages() {return numImages_;}

      void UpdateActualDuration() {actualDuration_ = camera_->GetCurrentMMTime() - startTime_;}

   private:
      virtual int svc(void) throw()
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

         }catch(...){
            camera_->LogMessage(g_Msg_EXCEPTION_IN_THREAD, false);
         }
         stop_=true;
         UpdateActualDuration();
         camera_->OnThreadExiting();
         return ret;
      }
   private:
      double intervalMs_;
      long numImages_;
      long imageCounter_;
      bool stop_;
      bool suspend_;
      CCameraBase* camera_;
      MM::MMTime startTime_;
      MM::MMTime actualDuration_;
      MM::MMTime lastFrameTime_;
      MMThreadLock stopLock_;
      MMThreadLock suspendLock_;
   };
   //////////////////////////////////////////////////////////////////////////


private:

   bool busy_;
   bool stopWhenCBOverflows_;
   Metadata metadata_;

   BaseSequenceThread * thd_;
   friend class BaseSequenceThread;
};


/**
* Base class for creating single axis stage adapters.
*/
template <class U>
class CStageBase : public CDeviceBase<MM::Stage, U>
{
   virtual int GetPositionUm(double& pos) = 0;
   virtual int SetPositionUm(double pos) = 0;

   /**
   * Default implementation for relative motion
   */
   virtual int SetRelativePositionUm(double d)
   {
      double pos;
      int ret = GetPositionUm(pos);
      if (ret != DEVICE_OK)
         return ret;
      return SetPositionUm(pos + d);
   }

   virtual int SetAdapterOriginUm(double /*d*/)
   {
      return DEVICE_UNSUPPORTED_COMMAND;
   }

   virtual int Move(double /*velocity*/)
   {
      return DEVICE_UNSUPPORTED_COMMAND;
   }

   virtual int Stop()
   {
      // Historycally, Move() has been in this interface longer than Stop(), so
      // there is a chance that a stage implements Move() but not Stop(). In
      // which case zero velocity is the best thing to do.
      return Move(0.0);
   }

   virtual int Home()
   {
      return DEVICE_UNSUPPORTED_COMMAND;
   }

   virtual int GetFocusDirection(MM::FocusDirection& direction)
   {
      // FocusDirectionUnknown is a safe default for all stages. Override this
      // only if direction is known for sure (i.e. does not depend on how the
      // hardware is installed).
      direction = MM::FocusDirectionUnknown;
      return DEVICE_OK;
   }

   virtual int GetStageSequenceMaxLength(long& /*nrEvents*/) const 
   {
      return DEVICE_UNSUPPORTED_COMMAND;
   }

   virtual int StartStageSequence() 
   {
      return DEVICE_UNSUPPORTED_COMMAND;
   }

   virtual int StopStageSequence()
   {
      return DEVICE_UNSUPPORTED_COMMAND;
   }

   virtual int ClearStageSequence()  
   {
      return DEVICE_UNSUPPORTED_COMMAND;
   }

   virtual int AddToStageSequence(double /*position*/) 
   {
      return DEVICE_UNSUPPORTED_COMMAND;
   }

   virtual int SendStageSequence()
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
public:
   CXYStageBase() : originXSteps_(0), originYSteps_(0), xPos_(0), yPos_(0)
   {
      // set-up directionality properties
      this->CreateProperty(MM::g_Keyword_Transpose_MirrorX, "0", MM::Integer, false);
      this->AddAllowedValue(MM::g_Keyword_Transpose_MirrorX, "0");
      this->AddAllowedValue(MM::g_Keyword_Transpose_MirrorX, "1");

      this->CreateProperty(MM::g_Keyword_Transpose_MirrorY, "0", MM::Integer, false);
      this->AddAllowedValue(MM::g_Keyword_Transpose_MirrorY, "0");
      this->AddAllowedValue(MM::g_Keyword_Transpose_MirrorY, "1");
   }


   virtual int SetPositionUm(double x, double y)
   {
      bool mirrorX, mirrorY;
      GetOrientation(mirrorX, mirrorY);
      double xPos = x;
      double yPos = y;

      long xSteps = 0;
      long ySteps = 0;

      if (mirrorX)
         xSteps = originXSteps_ - nint (x / this->GetStepSizeXUm());
      else
         xSteps = originXSteps_ + nint (x / this->GetStepSizeXUm());
      if (mirrorY)
         ySteps = originYSteps_ - nint (y / this->GetStepSizeYUm());
      else
         ySteps = originYSteps_ + nint (y / this->GetStepSizeYUm());

      int ret = this->SetPositionSteps(xSteps, ySteps);
      if (ret == DEVICE_OK) {
         xPos_ = xPos;
         yPos_ = yPos;
         this->OnXYStagePositionChanged(xPos_, yPos_);
      }
      return ret;
   }

   /**
   * Default implementation for relative motion
   */
   virtual int SetRelativePositionUm(double dx, double dy)
   {
      bool mirrorX, mirrorY;
      GetOrientation(mirrorX, mirrorY);
      double xPos = xPos_ + dx;
      double yPos = yPos_ + dy;

      if (mirrorX)
         dx = -dx;
      if (mirrorY)
         dy = -dy;

      int ret = SetRelativePositionSteps(nint(dx / this->GetStepSizeXUm()), nint(dy / this->GetStepSizeYUm()));
      if (ret == DEVICE_OK) {
         xPos_ = xPos;
         yPos_ = yPos;
         this->OnXYStagePositionChanged(xPos_, yPos_);
      }
      return ret;
   }

   /**
    * Alter the software coordinate translation between micrometers and steps,
    * such that the current position becomes the given coordinates.
    *
    * \param newXUm the new coordinate to assign to the current X position
    * \param newYUm the new coordinate to assign to the current Y position
   */
   virtual int SetAdapterOriginUm(double newXUm, double newYUm)
   {
      bool mirrorX, mirrorY;
      GetOrientation(mirrorX, mirrorY);

      long xStep, yStep;
      int ret = this->GetPositionSteps(xStep, yStep);
      if (ret != DEVICE_OK)
         return ret;

      if (mirrorX)
         originXSteps_ = xStep + nint(newXUm / this->GetStepSizeXUm());
      else
         originXSteps_ = xStep - nint(newXUm / this->GetStepSizeXUm());
      if (mirrorY)
         originYSteps_ = yStep + nint(newYUm / this->GetStepSizeYUm());
      else
         originYSteps_ = yStep - nint(newYUm / this->GetStepSizeYUm());

      return DEVICE_OK;
   }

   virtual int GetPositionUm(double& x, double& y)
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

      xPos_ = x;
      yPos_ = y;

      return DEVICE_OK;
   }

   /**
   * Default implementation.
   * The actual stage adapter should override it with the more
   * efficient implementation
   */ 
   virtual int SetRelativePositionSteps(long x, long y)
   {
      long xSteps, ySteps;
      int ret = this->GetPositionSteps(xSteps, ySteps);
      if (ret != DEVICE_OK)
         return ret;

      return this->SetPositionSteps(xSteps+x, ySteps+y);
   }

   virtual int Move(double /*vx*/, double /*vy*/)
   {
      return DEVICE_UNSUPPORTED_COMMAND;
   }

   virtual int SetXOrigin()
   {
      return DEVICE_UNSUPPORTED_COMMAND;
   }

   virtual int SetYOrigin()
   {
      return DEVICE_UNSUPPORTED_COMMAND;
   }

   virtual int GetXYStageSequenceMaxLength(long& /*nrEvents*/) const 
   {
      return DEVICE_UNSUPPORTED_COMMAND;
   }

   virtual int StartXYStageSequence() 
   {
      return DEVICE_UNSUPPORTED_COMMAND;
   }

   virtual int StopXYStageSequence()
   {
      return DEVICE_UNSUPPORTED_COMMAND;
   }

   virtual int ClearXYStageSequence()
   {
      return DEVICE_UNSUPPORTED_COMMAND;
   }

   virtual int AddToXYStageSequence(double /*positionX*/, double /*positionY*/) 
   {
      return DEVICE_UNSUPPORTED_COMMAND;
   }

   virtual int SendXYStageSequence()
   {
      return DEVICE_UNSUPPORTED_COMMAND;
   }

protected:

   /**
    * GetCachedXUm() and GetCachedUm() allow the device adapter to implent X and Y positions as properties
    * more efficiently than using GetPositionUm(). Because of transpose and origin functionality the base
    * class always keeps track of current x,y position in user-coordinate system
    * Use of this method is optional and the best way to take advantage of it is to implement
    * "get" property with cached values instead of querying the stage
    */
   double GetCachedXUm() {return xPos_;}
   double GetCachedYUm() {return yPos_;}

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
   double xPos_;
   double yPos_;
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
   virtual int GetDASequenceMaxLength(long& /*nrEvents*/) const 
   {
      return DEVICE_UNSUPPORTED_COMMAND;
   }

   virtual int StartDASequence() 
   {
      return DEVICE_UNSUPPORTED_COMMAND;
   }

   virtual int StopDASequence() {
      return DEVICE_UNSUPPORTED_COMMAND;
   }

   virtual int ClearDASequence() {
      return DEVICE_UNSUPPORTED_COMMAND;
   }

   virtual int AddToDASequence(double /*voltage*/) 
   {
      return DEVICE_UNSUPPORTED_COMMAND;
   }

   virtual int SendDASequence() {
      return DEVICE_UNSUPPORTED_COMMAND;
   }
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
   virtual int GetSLMSequenceMaxLength(long& /*nrEvents*/) const 
   {
      return DEVICE_UNSUPPORTED_COMMAND;
   }

   virtual int StartSLMSequence() 
   {
      return DEVICE_UNSUPPORTED_COMMAND;
   }

   virtual int StopSLMSequence() {
      return DEVICE_UNSUPPORTED_COMMAND;
   }

   virtual int ClearSLMSequence() {
      return DEVICE_UNSUPPORTED_COMMAND;
   }

   virtual int AddToSLMSequence(const unsigned char * const /*image*/) 
   {
      return DEVICE_UNSUPPORTED_COMMAND;
   }

   virtual int AddToSLMSequence(const unsigned int * const /*image*/) 
   {
      return DEVICE_UNSUPPORTED_COMMAND;
   }

   virtual int SendSLMSequence() {
      return DEVICE_UNSUPPORTED_COMMAND;
   }
};

/**
* Base class for creating Galvo devices.
*/
template <class U>
class CGalvoBase : public CDeviceBase<MM::Galvo, U>
{

};

/**
* Base class for creating special HUB devices for managing device libraries.
*/
template <class U>
class HubBase : public CDeviceBase<MM::Hub, U>
{
public:
   HubBase() {}
   virtual ~HubBase() {}

   /**
   * To provide automatic child device discovery,
   * override this method with code to instantiate child devices
   * and use them to populate "installedDevices" list.
   * If this method is not overridden, it will do nothing
   * and return DEVICE_OK.
   */
   virtual int DetectInstalledDevices() {return DEVICE_OK;}

   /**
   * Returns the number of child devices after DetectInstalledDevices was called.
   * (Don't override this method.)
   */
   virtual unsigned GetNumberOfInstalledDevices() {return (unsigned)installedDevices.size();}

   /**
   * Returns a pointer to the device with index devIdx. 0 <= devIdx < GetNumberOfInstalledDevices().
   * (Don't override this method.)
   */
   virtual MM::Device* GetInstalledDevice(int devIdx) {return installedDevices[devIdx];}

   /**
   * Removes all installed devices that were created by DetectInstalledDevices()
   * (Don't override this method.)
   */
   virtual void ClearInstalledDevices()
   {
      for (unsigned i=0; i<installedDevices.size(); i++)
         delete installedDevices[i];
      installedDevices.clear();
   }

protected:
   void AddInstalledDevice(MM::Device* pdev) {installedDevices.push_back(pdev);}

private:
   std::vector<MM::Device*> installedDevices;

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
   virtual int SetPosition(long pos)
   {
      return this->SetProperty(MM::g_Keyword_State, CDeviceUtils::ConvertToString(pos));
   }

   /**
   * Sets the state (position) of the device based on the state label.
   * Assumes that "State" property is implemented for the device.
   */
   virtual int SetPosition(const char* label)
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
   virtual int SetGateOpen(bool open)
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

   virtual int GetGateOpen(bool& open)
   {
      open = gateOpen_; 
      return DEVICE_OK;
   }

   /**
   * Obtains the state (position) index of the device.
   * Assumes that "State" property is implemented for the device.
   */
   virtual int GetPosition(long& pos) const
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
   virtual int GetPosition(char* label) const
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
   virtual int GetPositionLabel(long pos, char* label) const
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
   virtual int SetPositionLabel(long pos, const char* label)
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
   virtual int GetLabelPosition(const char* label, long& pos) const 
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
      else if (eAct == MM::IsSequenceable)
      {
         assert(this->HasProperty(MM::g_Keyword_State));
         bool sequenceable;
         int ret = this->IsPropertySequenceable(MM::g_Keyword_State, sequenceable);
         if (ret != DEVICE_OK)
            return ret;
         if (sequenceable) {
            long nrEvents;
            ret =  this->GetPropertySequenceMaxLength(MM::g_Keyword_State, nrEvents);
            if (ret != DEVICE_OK)
               return ret;
            pProp->SetSequenceable(nrEvents);
         }
      }
      else if (eAct == MM::AfterLoadSequence) {
         assert(this->HasProperty(MM::g_Keyword_State));
         std::vector<std::string> sequence = pProp->GetSequence();
         for (std::vector<std::string>::iterator it = sequence.begin(); it != sequence.end(); ++it) {
            long pos;
            int ret = GetLabelPosition((*it).c_str(), pos);
            if (ret != DEVICE_OK)
               return ret;
            std::stringstream s;
            s << pos;
            s >> *it;
         }

         int ret = this->ClearPropertySequence(MM::g_Keyword_State);
         if (ret != DEVICE_OK)
            return ret;

         std::vector<std::string>::iterator it;
         for ( it=sequence.begin() ; it < sequence.end(); it++ )
         {
            ret = this->AddToPropertySequence(MM::g_Keyword_State, (*it).c_str());
            if (ret != DEVICE_OK)                                               
               return ret;
         }
                                                                             
         ret = this->SendPropertySequence(MM::g_Keyword_State);
         if (ret != DEVICE_OK)                                                  
            return ret;
          
         //this->LoadPropertySequence(MM::g_Keyword_State, sequence);
      }
      else if (eAct == MM::StartSequence) {
         assert(this->HasProperty(MM::g_Keyword_State));
         return this->StartPropertySequence(MM::g_Keyword_State);
      }
      else if (eAct == MM::StopSequence) {
         assert(this->HasProperty(MM::g_Keyword_State));
         return this->StopPropertySequence(MM::g_Keyword_State);
      }

      return DEVICE_OK;
   }

   /**
    * Signals to the core that the state has changed, so that
    * both "State" and "Label" properties should be updated.
    */
   int OnStateChanged(long position) {
      int ret;   
      ret = this->OnPropertyChanged(MM::g_Keyword_State,CDeviceUtils::ConvertToString(position));
      if (ret != DEVICE_OK) {
         return ret;
      }

      char label[MM::MaxStrLength];
      GetPositionLabel(position, label);
      ret = this->OnPropertyChanged(MM::g_Keyword_Label, label);
      return ret;
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

