///////////////////////////////////////////////////////////////////////////////
// FILE:          Property.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMDevice - Device adapter kit
//-----------------------------------------------------------------------------
// DESCRIPTION:   This class implements the basic property mechanism in 
//                Micro-Manager devices.
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 08/05/2005
// COPYRIGHT:     University of California, San Francisco, 2006
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
// CVS:           $Id$
//

#ifndef _MMPROPERTY_H_
#define _MMPROPERTY_H_

#include "MMDeviceConstants.h"
#include <string>
#include <cstring>
#include <cstdlib>
#include <vector>
#include <map>

namespace MM {

/**
 * Base API for all device properties.
 * This interface is used by action functors.
 */
class PropertyBase
{
public:
   virtual ~PropertyBase() {}

   // property type
   virtual PropertyType GetType() = 0;
   
   // setting and getting values
   virtual bool Set(double dVal) = 0;
   virtual bool Set(long lVal) = 0;
   virtual bool Set(const char* Val) = 0;

   virtual bool Get(double& dVal) const = 0;
   virtual bool Get(long& lVal) const = 0;
   virtual bool Get(std::string& strVal) const = 0;

   // Limits
   virtual bool HasLimits() const = 0;
   virtual double GetLowerLimit() const = 0;
   virtual double GetUpperLimit() const = 0;
   virtual bool SetLimits(double lowerLimit, double upperLimit) = 0;

   // Sequence
   virtual void SetSequenceable(long sequenceSize) = 0;
   virtual  long GetSequenceMaxSize() const = 0;
   virtual std::vector<std::string> GetSequence() const = 0;
   virtual int ClearSequence() = 0;
   virtual int AddToSequence(const char* value) = 0;
   virtual int SendSequence() = 0;

   virtual std::string GetName() const = 0;
};

/**
 * Abstract interface to invoke specific action in the device.
 */
class ActionFunctor
{
public:
   virtual ~ActionFunctor() {}
   virtual int Execute(PropertyBase* pProp, ActionType eAct) = 0;
};

/**
 * Device action implementation.
 */
template <class T>
class Action : public ActionFunctor
{
private:
   T* pObj_;
   int (T::*fpt_)(PropertyBase* pProp, ActionType eAct);

public:
   Action(T* pObj, int(T::*fpt)(PropertyBase* pProp, ActionType eAct) ) :
      pObj_(pObj), fpt_(fpt) {}
      ~Action() {}

   int Execute(PropertyBase* pProp, ActionType eAct)
      { return (*pObj_.*fpt_)(pProp, eAct);};
};

/** 
 * Extended device action implementation.
 * It takes one additional long parameter whic can be used as
 * a command identifier inside the command handler
 */
template <class T>
class ActionEx : public ActionFunctor
{
private:
	T* pObj_;
   int (T::*fpt_)(PropertyBase* pProp, ActionType eAct, long param);
   long param_;

public:
	ActionEx(T* pObj, int(T::*fpt)(PropertyBase* pProp, ActionType eAct, long data), long data) :
      pObj_(pObj), fpt_(fpt), param_(data) {}; 
   ~ActionEx() {}
	int Execute(MM::PropertyBase* pProp, MM::ActionType eAct)
      { return (*pObj_.*fpt_)(pProp, eAct, param_);};
};

/**
 * Property API with most of the Property mechanism implemented.
 */
class Property : public PropertyBase
{
public:
   Property(const char* name) :
      readOnly_(false),
      fpAction_(0),
      cached_(false),
      hasData_(false),
      initStatus_(true),
      limits_(false),
      sequenceable_(false),
      sequenceMaxSize_(0),
      sequenceEvents_(),
      lowerLimit_(0.0),
      upperLimit_(0.0),
      name_(name)
   {}

   virtual ~Property()
   {
      delete fpAction_;
   }

   bool GetCached()const {return cached_;}
   void SetCached(bool bState=true) {cached_ = bState;}

   bool GetReadOnly()const {return readOnly_;}
   void SetReadOnly(bool bState=true) {readOnly_ = bState;}

   bool GetInitStatus() const {return initStatus_;}
   void SetInitStatus(bool init) {initStatus_ = init;}

   void RegisterAction(ActionFunctor* fpAction) {delete fpAction_; fpAction_ = fpAction;}
   int Update()
   {
      if (fpAction_)
         return fpAction_->Execute(this, BeforeGet);
      else
         return DEVICE_OK;
   }
   int Apply()
   {
      if (fpAction_)
         return fpAction_->Execute(this, AfterSet);
      else
         return DEVICE_OK;
   }

   // discrete set of allowed values
   std::vector<std::string> GetAllowedValues() const;

   void ClearAllowedValues() 
   {
      values_.clear();
   }

   void AddAllowedValue(const char* value);
   void AddAllowedValue(const char* value, long data);
   bool IsAllowed(const char* value) const;
   bool GetData(const char* value, long& data) const;

   bool HasLimits() const 
   {
      return limits_;
   }

   double GetLowerLimit() const 
   {
      return limits_ ? lowerLimit_ : 0.0;
   }

   double GetUpperLimit() const 
   {
      return limits_ ? upperLimit_ : 0.0;
   }

   bool SetLimits(double lowerLimit, double upperLimit)
   {
      limits_ = true;
      // do not allow limits for properties with discrete values defined
      if (values_.size() > 0)
         limits_ = false;

      if (lowerLimit >= upperLimit)
         limits_ = false;

      lowerLimit_ = lowerLimit;
      upperLimit_ = upperLimit;

      return limits_;
   }

   bool IsSequenceable() 
   {
      if (fpAction_)
         fpAction_->Execute(this, MM::IsSequenceable);
      return sequenceable_;
   }

   void SetSequenceable(long sequenceMaxSize);

   long GetSequenceMaxSize() const 
   {
      return sequenceMaxSize_;
   }

   int ClearSequence() 
   {
      try
      {
         if (sequenceEvents_.size() > 0){
            sequenceEvents_.clear();
         }
      } catch (...)
      {
         return MM_CODE_ERR;
      }

      return DEVICE_OK;
   }

   int AddToSequence(const char* value) 
   {
      try
      {
         sequenceEvents_.push_back(value);
         if (sequenceEvents_.size() > (unsigned) GetSequenceMaxSize())           
            return DEVICE_SEQUENCE_TOO_LARGE;
      } catch (...)
      {
         return MM_CODE_ERR;
      }

      return DEVICE_OK;
   }

   int SendSequence() 
   {
      if (fpAction_)
         return fpAction_->Execute(this, AfterLoadSequence);

      return DEVICE_OK; // Return an error instead???
   }

   std::string GetName() const
   {
      return name_;
   }

   std::vector<std::string> GetSequence() const
   {
      return sequenceEvents_;
   }

   int StartSequence() 
   {
      if (fpAction_)
         return fpAction_->Execute(this, MM::StartSequence);
      return DEVICE_OK;  // Return an error instead???
   }
 
   int StopSequence() 
   {
      if (fpAction_)
         return fpAction_->Execute(this, MM::StopSequence);
      return DEVICE_OK;  // Return an error instead???
   }

protected:
   bool readOnly_;
   ActionFunctor* fpAction_;
   bool cached_;
   bool hasData_;
   bool initStatus_;
   bool limits_;
   bool sequenceable_;
   long sequenceMaxSize_;
   std::vector<std::string> sequenceEvents_;
   double lowerLimit_;
   double upperLimit_;
   std::map<std::string, long> values_; // allowed values
   const std::string name_;

private:
   Property& operator=(const Property&);
};

/**
 * String property class.
 */
class StringProperty : public Property
{
public:
   StringProperty(const char* name) :
      Property(name)
   {}
   virtual ~StringProperty(){};
            
   PropertyType GetType() {return String;}
   
   // setting and getting values
   bool Set(double val);
   bool Set(long val);
   bool Set(const char* val);

   bool Get(double& val) const;
   bool Get(long& val) const ;
   bool Get(std::string& val) const;

   bool SetLimits(double /*lowerLimit*/, double /*upperLimit*/) {return false;}

private:
   StringProperty& operator=(const StringProperty&);

   std::string value_;
};

/**
 * Floating point property class (uses double type for value representation).
 */
class FloatProperty : public Property
{
public:
   FloatProperty(const char* name) :
      Property(name),
      value_(0.0),
      decimalPlaces_(4)
   {
      int rms = 1;
      for (int i = 0; i < decimalPlaces_; ++i)
         rms *= 10;
      reciprocalMinimalStep_ = rms;
   }

   virtual ~FloatProperty() {}
            
   PropertyType GetType() {return Float;}
   
   // setting and getting values
   bool Set(double val);
   bool Set(long val);
   bool Set(const char* val);

   bool Get(double& val) const;
   bool Get(long& val) const ;
   bool Get(std::string& val) const;

   bool SetLimits(double lowerLimit, double upperLimit);

private:
   FloatProperty& operator=(const FloatProperty&);

   double Truncate(double dVal);
   double TruncateDown(double dVal);
   double TruncateUp(double dVal);
   double value_;
   int decimalPlaces_;
   double reciprocalMinimalStep_;
};

/**
 * Integer property class.
 */
class IntegerProperty : public Property
{
public:
   IntegerProperty(const char* name) :
      Property(name),
      value_(0)
   {}
   ~IntegerProperty() {};
            
   PropertyType GetType() {return Integer;}
   
   // setting and getting values
   bool Set(double val);
   bool Set(long val);
   bool Set(const char* val);

   bool Get(double& val) const;
   bool Get(long& val) const ;
   bool Get(std::string& val) const;

private:
   IntegerProperty& operator=(const IntegerProperty&);

   long value_;
};

/**
 * An array of properties supported by a device.
 */
class PropertyCollection
{
public:
   PropertyCollection();
   ~PropertyCollection();

   int CreateProperty(const char* name, const char* value, PropertyType eType, bool bReadOnly, ActionFunctor* pAct=0, bool isPreInitProperty=false);
   int RegisterAction(const char* name, ActionFunctor* fpAct);
   int SetAllowedValues(const char* name, std::vector<std::string>& values);
   int ClearAllowedValues(const char* name);
   int AddAllowedValue(const char* name, const char* value, long data);
   int AddAllowedValue(const char* name, const char* value);
   int GetPropertyData(const char* name, const char* value, long& data);
   int GetCurrentPropertyData(const char* name, long& data);
   int Set(const char* propName, const char* Value);
   int Get(const char* propName, std::string& val) const;
   Property* Find(const char* name) const;
   std::vector<std::string> GetNames() const;
   unsigned GetSize() const;
   bool GetName(unsigned uIdx, std::string& strName) const;
   int UpdateAll();
   int ApplyAll();
   int Update(const char* Name);
   int Apply(const char* Name);

private:
   typedef std::map<std::string, Property*> CPropArray;
   CPropArray properties_;
};


} // namespace MM
#endif //_MMPROPERTY_H_
