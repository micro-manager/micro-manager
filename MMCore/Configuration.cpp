///////////////////////////////////////////////////////////////////////////////
// FILE:          Configuration.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     Core
//-----------------------------------------------------------------------------
// DESCRIPTION:   Set of properties defined as a high level command
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 09/08/2005
// COPYRIGHT:     University of California, San Francisco, 2006
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
// CVS:           $Id$
//
#include "Configuration.h"
#include "../MMDevice/MMDevice.h"
#include "Error.h"
#include <assert.h>
#include <sstream>
#include <string>
#include <fstream>

using namespace std;

/**
 * Returns verbose description of the object's contents.
 */
string PropertySetting::getVerbose() const
{
   ostringstream txt;
   txt << deviceLabel_ << ":" << propertyName_ << "=" << value_;
   return txt.str();
}

/**
  * Creates serialized representation of the object.
  */
string PropertySetting::Serialize() const
{
   ostringstream txt;
   txt << deviceLabel_ << " " << propertyName_ << " " << value_;
   return txt.str();
}

/**
  * Restores object contents from the serial data.
  */
void PropertySetting::Restore(const string& data)
{
   istringstream is(data);
   if (is)
      is >> deviceLabel_; 

   if (is)
      is >> propertyName_;

   char val[MM::MaxStrLength];
   is.getline(val, MM::MaxStrLength);
   if (strlen(val) > 1)
      value_ = val+1; // +1 skips the extra space at the beginning 
}

bool PropertySetting::isEqualTo(const PropertySetting& ps)
{
   if (ps.deviceLabel_.compare(deviceLabel_) == 0 &&
      ps.propertyName_.compare(propertyName_) == 0 &&
      ps.value_.compare(value_) == 0)
      return true;
   else
      return false;
}

/**
  * Returns verbose description of the object's contents.
  */
std::string Configuration::getVerbose() const
{
   std::ostringstream txt;
   std::vector<PropertySetting>::const_iterator it;
   txt << "<html>";
   for (it=settings_.begin(); it!=settings_.end(); it++)
      txt << it->getVerbose() << "<br>";
   txt << "</html>";

   return txt.str();
}

   /**
    * Creates serialized representation of the object.
    */

string Configuration::Serialize() const
{
   ostringstream os;
   vector<PropertySetting>::const_iterator it;
   for (it=settings_.begin(); it!=settings_.end(); it++)
      os << it->Serialize() << endl;
   return os.str();
}

/**
  * Restores object contents from the serial data.
  */
void Configuration::Restore(const string& data)
{
   settings_.clear();
   istringstream is(data);

   char line[3 * MM::MaxStrLength];
   while(is.getline(line, 3 * MM::MaxStrLength, '\n'))
   {
      if (strlen(line) > 1)
      {
         PropertySetting s;
         s.Restore(line);
         settings_.push_back(s);
      }
   }
}

/**
 * Returns the setting with specified index.
 */
PropertySetting Configuration::getSetting(size_t index) const throw (CMMError)
{
   if (index >= settings_.size())
   {
      std::ostringstream errTxt;
      errTxt << (unsigned int)index << " - invalid configuration setting index";
      throw CMMError(errTxt.str().c_str(), MMERR_DEVICE_GENERIC);
   }
   return settings_[index];
}

/**
  * Checks whether the property is included in the  configuration.
  */

bool Configuration::isPropertyIncluded(const char* device, const char* property)
{
   vector<PropertySetting>::const_iterator it;
   for (it=settings_.begin(); it!=settings_.end(); ++it)
      if (it->getDeviceLabel().compare(device) == 0)
         if (it->getPropertyName().compare(property) == 0)
            return true;
   
   return false;
}

/**
  * Checks whether the setting is included in the  configuration.
  */

bool Configuration::isSettingIncluded(const PropertySetting& ps)
{
   vector<PropertySetting>::const_iterator it;
   for (it=settings_.begin(); it!=settings_.end(); ++it)
      if (it->getDeviceLabel().compare(ps.getDeviceLabel()) == 0)
         if (it->getPropertyName().compare(ps.getPropertyName()) == 0)
            if (it->getPropertyValue().compare(ps.getPropertyValue()) == 0)
               return true;
   
   return false;
}

/**
 * Returns the property pair with specified index.
 */
PropertyPair PropertyBlock::getPair(size_t index) const throw (CMMError)
{
   std::map<std::string, PropertyPair>::const_iterator it = pairs_.begin();
   if (index >= pairs_.size())
   {
      std::ostringstream errTxt;
      errTxt << (unsigned int)index << " - invalid property pair index";
      throw CMMError(errTxt.str().c_str(), MMERR_DEVICE_GENERIC);
   }

   for (size_t i=0; i<index; i++) it++;
   return it->second;
}

/**
 * Adds a new pair to the current contents.
 */
void PropertyBlock::addPair(const PropertyPair& pair)
{
   pairs_[pair.getPropertyName()] = pair;
}

/**
 * Get value of the specified key (property).
 */
std::string PropertyBlock::getValue(const char* key) const throw (CMMError)
{
   std::map<std::string, PropertyPair>::const_iterator it;
   it = pairs_.find(key);
   if (it != pairs_.end())
      return it->second.getPropertyValue();

   std::ostringstream errTxt;
   errTxt << key << " - invalid property name";
   throw CMMError(errTxt.str().c_str(), MMERR_DEVICE_GENERIC);
}

