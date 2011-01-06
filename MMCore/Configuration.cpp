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
#include <cstring>
#include <fstream>

using namespace std;

string PropertySetting::generateKey(const char* device, const char* prop)
{
   string key(device);
   key += "-";
   key += prop;
   return key;
}

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
      // strip potential windowsdos CR
      istringstream il(line);
      il.getline(line, 3 * MM::MaxStrLength, '\r');

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

bool Configuration::isPropertyIncluded(const char* device, const char* prop)
{
   map<string, int>::iterator it = index_.find(PropertySetting::generateKey(device, prop));
   if (it != index_.end())
      return true;
   else
      return false;
}

/**
  * Get the setting with specified device name and property name.
  */

PropertySetting Configuration::getSetting(const char* device, const char* prop)
{
   map<string, int>::iterator it = index_.find(PropertySetting::generateKey(device, prop));
   if (it == index_.end())
   {
      std::ostringstream errTxt;
      errTxt << "Property " << prop << " not found in device " << device << ".";
      throw CMMError(errTxt.str().c_str(), MMERR_DEVICE_GENERIC);
   }
   if (((unsigned int) it->second) >= settings_.size()) {
      std::ostringstream errTxt;
      errTxt << "Internal Error locating Property " << prop << " in device " << device << ".";
      throw CMMError(errTxt.str().c_str(), MMERR_DEVICE_GENERIC);
   }

   return settings_[it->second];
}

/**
  * Checks whether the setting is included in the  configuration.
  */

bool Configuration::isSettingIncluded(const PropertySetting& ps)
{
   map<string, int>::iterator it = index_.find(ps.getKey());
   if (it != index_.end() && settings_[it->second].getPropertyValue().compare(ps.getPropertyValue()) == 0)
      return true;
   else
      return false;
}

/**
  * Checks whether a configuration is included.
  * Included means that all devices from the operand configuration are
  * included and that settings match
  */

bool Configuration::isConfigurationIncluded(const Configuration& cfg)
{
   vector<PropertySetting>::const_iterator it;
   for (it=cfg.settings_.begin(); it!=cfg.settings_.end(); ++it)
      if (!isSettingIncluded(*it))
         return false;
   
   return true;
}

/**
 * Adds new property setting to the existing contents.
 */
void Configuration::addSetting(const PropertySetting& setting)
{
   map<string, int>::iterator it = index_.find(setting.getKey());
   if (it != index_.end())
   {
      // replace
      settings_[it->second] = setting;
   }
   else
   {
      // add new
      index_[setting.getKey()] = (int)settings_.size();
      settings_.push_back(setting);
   }
}

/**
 * Removes property setting, specified by device and property names, from the configuration.
 */
void Configuration::deleteSetting(const char* device, const char* prop)
{
   map<string, int>::iterator it = index_.find(PropertySetting::generateKey(device, prop));
   if (it == index_.end())
   {
      std::ostringstream errTxt;
      errTxt << "Property " << prop << " not found in device " << device << ".";
      throw CMMError(errTxt.str().c_str(), MMERR_DEVICE_GENERIC);
   }

   settings_.erase(settings_.begin() + it->second); // The argument of erase produces an iterator at the desired position.

   // Re-index 
   index_.clear();
   for (unsigned int i = 0; i < settings_.size(); i++) 
   {
      index_[settings_[i].getKey()] = i;
   }

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

