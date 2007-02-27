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
#include <vector>
#include <map>

#pragma warning(disable : 4996) // disable warning for deperecated CRT functions on Windows 

namespace MM {

/**
 * Base API for all device properties.
 * This interface is used by action functors.
 */
class PropertyBase
{
public:
   // property type
   virtual PropertyType GetType() = 0;
   
   // setting and getting values
   virtual bool Set(double dVal) = 0;
   virtual bool Set(long lVal) = 0;
   virtual bool Set(const char* Val) = 0;

   virtual bool Get(double& dVal) const = 0;
   virtual bool Get(long& lVal) const = 0;
   virtual bool Get(std::string& strVal) const = 0;
};

/**
 * Abstract interface to invoke specific action in the device.
 */
class ActionFunctor
{
public:
   virtual int Execute(PropertyBase* pProp, ActionType eAct)=0;        // call using function
};

/**
 * Device action implementation.
 */
template <class T>
class Action : public ActionFunctor
{
private:
   int (T::*fpt_)(PropertyBase* pProp, ActionType eAct);
   T* pObj_;

public:
   Action(T* pObj, int(T::*fpt)(PropertyBase* pProp, ActionType eAct) ) :
      pObj_(pObj), fpt_(fpt) {}

      int Execute(PropertyBase* pProp, ActionType eAct)
      {return (*pObj_.*fpt_)(pProp, eAct);};
};

/**
 * Porperty API with most of the Property mechanism implemented.
 */
class Property : public PropertyBase
{
public:
   Property() : readOnly_(false), fpAction_(0), cached_(false), hasData_(false), initStatus_(true) {}      
   virtual ~Property(){delete fpAction_;}
            
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
   void ClearAllowedValues() {values_.clear();}
   void AddAllowedValue(const char* value);
   void AddAllowedValue(const char* value, long data);
   bool IsAllowed(const char* value) const;
   bool GetData(const char* value, long& data) const;

   // virtual API
   // ~~~~~~~~~~~
   virtual Property* Clone() const = 0;

   // operators
   // ~~~~~~~~~
   Property& operator=(const Property& rhs);

protected:
   bool readOnly_;
   std::map<std::string, long> values_; // allowed values
   bool hasData_;
   ActionFunctor* fpAction_;
   bool cached_;
   bool initStatus_;
};

/**
 * String property class.
 */
class StringProperty : public Property
{
public:
   StringProperty() : Property() {}      
   virtual ~StringProperty(){};
            
   PropertyType GetType() {return String;}
   
   // setting and getting values
   bool Set(double val);
   bool Set(long val);
   bool Set(const char* val);

   bool Get(double& val) const;
   bool Get(long& val) const ;
   bool Get(std::string& val) const;

   Property* Clone() const;

   // operators
   StringProperty& operator=(const StringProperty& rhs);

private:
   std::string value_;
};

/**
 * Floating point property class (uses double type for value representation).
 */
class FloatProperty : public Property
{
public:
   FloatProperty(): Property(), value_(0.0) {}      
   virtual ~FloatProperty() {};
            
   PropertyType GetType() {return Float;}
   
   // setting and getting values
   bool Set(double val);
   bool Set(long val);
   bool Set(const char* val);

   bool Get(double& val) const;
   bool Get(long& val) const ;
   bool Get(std::string& val) const;

   Property* Clone() const;

   // operators
   FloatProperty& operator=(const FloatProperty& rhs);

private:
   double value_;
};

/**
 * Integer property class.
 */
class IntegerProperty : public Property
{
public:
   IntegerProperty(): Property(), value_(0) {}      
   ~IntegerProperty() {};
            
   PropertyType GetType() {return Integer;}
   
   // setting and getting values
   bool Set(double val);
   bool Set(long val);
   bool Set(const char* val);

   bool Get(double& val) const;
   bool Get(long& val) const ;
   bool Get(std::string& val) const;

   Property* Clone() const;

   // operators
   IntegerProperty& operator=(const IntegerProperty& rhs);

private:
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

   int CreateProperty(const char* name, const char* value, PropertyType eType, bool bReadOnly, ActionFunctor* pAct=0, bool initStatus=false);
   int RegisterAction(const char* name, ActionFunctor* fpAct);
   int SetAllowedValues(const char* name, std::vector<std::string>& values);
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
   /*
   template <typename T>
   int GetVal(const char* PropName, T &val);
   template <typename T>
   int SetVal(const char* PropName, const T val);
   */
   typedef std::map<std::string, Property*> CPropArray;
   CPropArray properties_;
};

/*
template <typename T>
inline int CMMPropertyCollection::SetVal(const char* PropName, const T &val)
{
   Property* pProp = Find(PropName);
   if (!pProp)
      return DEVICE_INVALID_PROPERTY; // name not found

   if (pProp->IsAllowed(Value))
   {
      pProp->Set(val);
      return pProp->Apply();
   }
   else
      return DEVICE_INVALID_PROPERTY_VALUE;
}

template <typename T>
int CMMPropertyCollection::Get(const char* PropName, T& val) const
{
   Property* pProp = Find(PropName);
   if (!pProp)
      return DEVICE_INVALID_PROPERTY; // name not found

   if (!pProp->GetCached())
   {
      int nRet = pProp->Update();
      if (nRet != DEVICE_OK)
         return nRet;
   }
   pProp->Get(strValue);
   return DEVICE_OK;
}
*/

} // namespace MM

#endif //_MMPROPERTY_H_
