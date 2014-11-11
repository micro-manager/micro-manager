///////////////////////////////////////////////////////////////////////////////
// FILE:          Property.cpp
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

#include "Property.h"

#include <cstdio>
#include <math.h>

using namespace std;

#if WIN32
   #define snprintf _snprintf
#endif

const int BUFSIZE = 60; // For number-to-string conversion


vector<string> MM::Property::GetAllowedValues() const
{
   vector<string> vals;
   map<string, long>::const_iterator it;
   for (it=values_.begin(); it != values_.end(); it++)
      vals.push_back(it->first);
   return vals;
}

void MM::Property::AddAllowedValue(const char* value)
{
   values_.insert(make_pair(value, 0L));
   limits_ = false;
}

void MM::Property::AddAllowedValue(const char* value, long data)
{
   values_.insert(make_pair(value, data));
   hasData_ = true;
   limits_ = false;
}


bool MM::Property::IsAllowed(const char* value) const
{
   if (values_.size() == 0)
      return true; // any value is allowed

   map<string, long>::const_iterator it = values_.find(value);
   if (it == values_.end())
      return false; // not found
   else
      return true;
}

bool MM::Property::GetData(const char* value, long& data) const
{
   if (!hasData_)
      return false;

   map<string, long>::const_iterator it = values_.find(value);
   if (it == values_.end())
      return false; // not found
   else
   {
      data = it->second;
      return true;
   }
}

void MM::Property::SetSequenceable(long sequenceMaxSize)
{
   sequenceable_ = (sequenceMaxSize != 0);
   sequenceMaxSize_ = sequenceMaxSize;
}


///////////////////////////////////////////////////////////////////////////////
// MM::StringProperty
// ~~~~~~~~~~~~~~~~~
//
bool MM::StringProperty::Set(double val)
{
   char buf[BUFSIZE];
   snprintf(buf, BUFSIZE, "%.2g", val); 
   value_ = buf;
   return true;
}

bool MM::StringProperty::Set(long val)
{
   char buf[BUFSIZE];
   snprintf(buf, BUFSIZE, "%ld", val); 
   value_ = buf;
   return true;
}

bool MM::StringProperty::Set(const char* val)
{
   value_ = val;
   return true;
}

bool MM::StringProperty::Get(double& val) const
{
   val = atof(value_.c_str());
   return true;
}

bool MM::StringProperty::Get(long& val) const
{
   val = atol(value_.c_str());
   return true;
}

bool MM::StringProperty::Get(std::string& strVal) const
{
   strVal = value_;
   return true;
}

///////////////////////////////////////////////////////////////////////////////
// MM::FloatProperty
// ~~~~~~~~~~~~~~~~
//

double MM::FloatProperty::Truncate(double dVal)
{
   if (dVal >= 0)
      return floor(dVal * reciprocalMinimalStep_ + 0.5) / reciprocalMinimalStep_;
   else
      return ceil(dVal * reciprocalMinimalStep_ - 0.5) / reciprocalMinimalStep_;
}

double MM::FloatProperty::TruncateDown(double dVal)
{
   return floor(dVal * reciprocalMinimalStep_) / reciprocalMinimalStep_;
}

double MM::FloatProperty::TruncateUp(double dVal)
{
   return ceil(dVal * reciprocalMinimalStep_) / reciprocalMinimalStep_;
}

bool MM::FloatProperty::Set(double dVal)
{
   double val = Truncate(dVal);
   if (limits_)
   {
      if (val < lowerLimit_ || val > upperLimit_)
         return false;
   }
   value_ = val;
   return true;
}

bool MM::FloatProperty::Set(long lVal)
{
   return Set((double)lVal);
}

bool MM::FloatProperty::Set(const char* pszVal)
{
   return Set(atof(pszVal));
}

bool MM::FloatProperty::Get(double& dVal) const
{
   dVal = value_;
   return true;
}

bool MM::FloatProperty::Get(long& lVal) const
{
   lVal = (long)value_;
   return true;
}

bool MM::FloatProperty::Get(std::string& strVal) const
{
   char fmtStr[20];
   char buf[BUFSIZE];
   sprintf(fmtStr, "%%.%df", decimalPlaces_);
   snprintf(buf, BUFSIZE, fmtStr, value_);
   strVal = buf;
   return true;
}

bool MM::FloatProperty::SetLimits(double lowerLimit, double upperLimit)
{
   return MM::Property::SetLimits(TruncateUp(lowerLimit), TruncateDown(upperLimit));
}

///////////////////////////////////////////////////////////////////////////////
// MM::IntegerProperty
// ~~~~~~~~~~~~~~~~~~
//

bool MM::IntegerProperty::Set(double dVal)
{
   return Set((long)dVal);
}

bool MM::IntegerProperty::Set(long lVal)
{
   if (limits_)
   {
      if (lVal < lowerLimit_ || lVal > upperLimit_)
         return false;
   }
   value_ = lVal;
   return true;
}

bool MM::IntegerProperty::Set(const char* pszVal)
{
   return Set(atol(pszVal));
}

bool MM::IntegerProperty::Get(double& dVal) const
{
   dVal = (double)value_;
   return true;
}

bool MM::IntegerProperty::Get(long& lVal) const
{
   lVal = value_;
   return true;
}

bool MM::IntegerProperty::Get(std::string& strVal) const
{
   char pszBuf[BUFSIZE];
   snprintf(pszBuf, BUFSIZE, "%ld", value_); 
   strVal = pszBuf;
   return true;
}

///////////////////////////////////////////////////////////////////////////////
// MM::PropertyCollection
// ~~~~~~~~~~~~~~~~~~~~~
//
MM::PropertyCollection::PropertyCollection()
{
}

MM::PropertyCollection::~PropertyCollection()
{
   CPropArray::const_iterator it;
   for (it = properties_.begin(); it != properties_.end(); it++)
      delete it->second;
}

int MM::PropertyCollection::Set(const char* pszPropName, const char* pszValue)
{
   MM::Property* pProp = Find(pszPropName);
   if (!pProp)
      return DEVICE_INVALID_PROPERTY; // name not found

   if (pProp->GetReadOnly())
      return DEVICE_OK;
   // NOTE: if the property is read-only we silently refuse to change the data
   // and return OK code. Should this be an an error?

   if (pProp->IsAllowed(pszValue))
   {
      // check property limits
      if (!pProp->Set(pszValue))
         return DEVICE_INVALID_PROPERTY_VALUE;

      return pProp->Apply();
   }
   else
      return DEVICE_INVALID_PROPERTY_VALUE;
}

int MM::PropertyCollection::Get(const char* pszPropName, string& strValue) const
{
   MM::Property* pProp = Find(pszPropName);
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

MM::Property* MM::PropertyCollection::Find(const char* pszName) const
{
   CPropArray::const_iterator it = properties_.find(pszName);
   if (it == properties_.end())
      return 0; // not found
   return it->second;
}

vector<string> MM::PropertyCollection::GetNames() const
{
   vector<string> nameList;

   CPropArray::const_iterator it;
   for (it = properties_.begin(); it != properties_.end(); it++)
      nameList.push_back(it->first);

   return nameList;
}
 
unsigned MM::PropertyCollection::GetSize() const
{
   return (unsigned) properties_.size();
}

int MM::PropertyCollection::CreateProperty(const char* pszName, const char* pszValue, MM::PropertyType eType, bool bReadOnly, MM::ActionFunctor* pAct, bool isPreInitProperty)
{
   // check if the name allready exists
   if (Find(pszName))
      return DEVICE_DUPLICATE_PROPERTY;

   MM::Property* pProp=0;

   switch(eType)
   {
   case MM::String:
         pProp = new MM::StringProperty();
      break;
      
      case MM::Integer:
         pProp = new MM::IntegerProperty();
      break;

      case MM::Float:
         pProp = new MM::FloatProperty();
      break;

      default:
         return DEVICE_INVALID_PROPERTY_TYPE; // unsupported type
      break;
   }

   if (!pProp->Set(pszValue))
      return false;
   pProp->SetReadOnly(bReadOnly);
   pProp->SetInitStatus(isPreInitProperty);
   properties_[pszName] = pProp;

   // assign action functor
   pProp->RegisterAction(pAct);
   return DEVICE_OK;
}

int MM::PropertyCollection::SetAllowedValues(const char* pszName, vector<string>& values)
{
   MM::Property* pProp = Find(pszName);
   if (!pProp)
      return DEVICE_INVALID_PROPERTY; // name not found

   pProp->ClearAllowedValues();
   for (unsigned i=0; i<values.size(); i++)
      pProp->AddAllowedValue(values[i].c_str());

   return DEVICE_OK;
}

int MM::PropertyCollection::ClearAllowedValues(const char* pszName)
{
   MM::Property* pProp = Find(pszName);
   if (!pProp)
      return DEVICE_INVALID_PROPERTY; // name not found

   pProp->ClearAllowedValues();
   return DEVICE_OK;
}

int MM::PropertyCollection::AddAllowedValue(const char* pszName, const char* value, long data)
{
   MM::Property* pProp = Find(pszName);
   if (!pProp)
      return DEVICE_INVALID_PROPERTY; // name not found

   pProp->AddAllowedValue(value, data);
   return DEVICE_OK;
}

int MM::PropertyCollection::AddAllowedValue(const char* pszName, const char* value)
{
   MM::Property* pProp = Find(pszName);
   if (!pProp)
      return DEVICE_INVALID_PROPERTY; // name not found

   pProp->AddAllowedValue(value);
   return DEVICE_OK;
}


int MM::PropertyCollection::GetPropertyData(const char* name, const char* value, long& data)
{
   MM::Property* pProp = Find(name);
   if (!pProp)
      return DEVICE_INVALID_PROPERTY; // name not found

   if (!pProp->GetData(value, data))
      return DEVICE_NO_PROPERTY_DATA;

   return DEVICE_OK;
}

int MM::PropertyCollection::GetCurrentPropertyData(const char* name, long& data)
{
   MM::Property* pProp = Find(name);
   if (!pProp)
      return DEVICE_INVALID_PROPERTY; // name not found

   string value;
   pProp->Get(value);
   if (!pProp->GetData(value.c_str(), data))
      return DEVICE_NO_PROPERTY_DATA;

   return DEVICE_OK;
}

bool MM::PropertyCollection::GetName(unsigned uIdx, string& strName) const
{
   if (uIdx >= properties_.size())
      return false; // unknown index

   CPropArray::const_iterator it = properties_.begin();
   for (unsigned i=0; i<uIdx; i++)
      it++;
   strName = it->first;
   return true;
}

int MM::PropertyCollection::RegisterAction(const char* pszName, MM::ActionFunctor* fpAct)
{
   MM::Property* pProp = Find(pszName);
   if (!pProp)
      return DEVICE_INVALID_PROPERTY; // name not found

   pProp->RegisterAction(fpAct);

   return true;
}

int MM::PropertyCollection::UpdateAll()
{
   CPropArray::const_iterator it;
   for (it=properties_.begin(); it!=properties_.end(); it++)
   {
      int nRet;
      nRet = it->second->Update();
      if (nRet != DEVICE_OK)
         return nRet;
   }
   return DEVICE_OK;
}

int MM::PropertyCollection::ApplyAll()
{
   CPropArray::const_iterator it;
   for (it=properties_.begin(); it!=properties_.end(); it++)
   {
      int nRet;
      nRet = it->second->Apply();
      if (nRet != DEVICE_OK)
         return nRet;
   }
   return DEVICE_OK;
}

int MM::PropertyCollection::Update(const char* pszName)
{
   MM::Property* pProp = Find(pszName);
   if (!pProp)
      return DEVICE_INVALID_PROPERTY;

   return pProp->Update();
}

int MM::PropertyCollection::Apply(const char* pszName)
{
   MM::Property* pProp = Find(pszName);
   if (!pProp)
      return DEVICE_INVALID_PROPERTY;

      return pProp->Apply();
}
