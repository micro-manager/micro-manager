///////////////////////////////////////////////////////////////////////////////
// FILE:          CoreProperty.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCore
//-----------------------------------------------------------------------------
// DESCRIPTION:   Implements the "core property" mechanism. The MMCore exposes
//                some of its own settings as a virtual device.
//              
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 10/23/2005
//
// COPYRIGHT:     University of California, San Francisco, 2006
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
// CVS:           $Id$
//
#ifndef _CORE_PROPERTY_H
#define _CORE_PROPERTY_H

#include <string>
#include <set>
#include <vector>
#include <map>
#include "MMEventCallback.h"

class CMMCore;
class MMEventCallback;

class CoreProperty
{
public:
   CoreProperty() : value_("Undefined"), readOnly_(false) {}
   CoreProperty(const char* v, bool ro) : value_(v), readOnly_(ro) {} 
   ~CoreProperty(){}

   bool IsReadOnly()const {return readOnly_;}

   bool Set(const char* value);
   std::string Get() const;

   // discrete set of allowed values
   std::vector<std::string> GetAllowedValues() const;
   void AddAllowedValue(const char* value);
   void AddAllowedValue(const char* value, long data);
   bool IsAllowed(const char* value) const;
   void ClearAllowedValues() {values_.clear();}

protected:
   std::string value_;
   bool readOnly_;
   std::set<std::string> values_; // allowed values
};

class CorePropertyCollection
{
public:
   CorePropertyCollection(CMMCore* core) : core_(core) {}
   ~CorePropertyCollection() {}

   std::string Get(const char* PropName) const;
   bool Has(const char* name) const;
   std::vector<std::string> GetAllowedValues(const char* propName) const;
   void ClearAllowedValues(const char* propName);
   void AddAllowedValue(const char* propName, const char* value);
   bool IsReadOnly(const char* propName) const;
   std::vector<std::string> GetNames() const;
   void Add(const char* name, CoreProperty& p) {properties_[name] = p;}
   void Refresh();
   void Execute(const char* PropName, const char* Value);
   void Set(const char* PropName, const char* Value);
   void Clear() {properties_.clear();}

private:
   CMMCore* core_;
   std::map<std::string, CoreProperty> properties_;
};

#endif

