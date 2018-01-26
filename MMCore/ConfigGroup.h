///////////////////////////////////////////////////////////////////////////////
// FILE:          ConfigGroup.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCore
//-----------------------------------------------------------------------------
// DESCRIPTION:   Groups of configurations and container for the groups
//
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 23/01/2006
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
#ifndef _CONFIG_GROUP_H_
#define _CONFIG_GROUP_H_

#include "Configuration.h"
#include "Error.h"
#include <string>
#include <vector>

/**
 * Encapsulates a collection (map) of user-defined presets.
 */
template <class T>
class ConfigGroupBase {

public:

   /**
    * Defines a new preset.
    */
   void Define(const char* configName)
   {
      configs_[configName];
   }

	/**
    * Defines a new preset.
    */
   void Define(const char* configName, const char* deviceLabel, const char* propName, const char* value)
   {
      PropertySetting setting(deviceLabel, propName, value);
      configs_[configName].addSetting(setting);
	}

   /**
    * Finds preset by name.
    */
   T* Find(const char* configName)
   {
      typename std::map<std::string,T>::iterator it = configs_.find(configName);
      if (it == configs_.end())
         return 0;
      else
         return &(it->second);
   }

    /**
    * Renames a preset (addressed by old name).
    */
   bool Rename(const char* oldConfigName, const char* newConfigName)
   {
      // tolerate empty configuration names
      if (strlen(oldConfigName) == 0)
         return true;

      typename std::map<std::string, T>::const_iterator it = configs_.find(oldConfigName);
      if (it == configs_.end())
         return false;
	  
	  configs_[newConfigName] = it->second;
      configs_.erase(it->first);
      return true;
   }

    /**
    * Deletes a preset by name.
    */
   bool Delete(const char* configName)
   {
      // tolerate empty configuration names
      if (strlen(configName) == 0)
         return true;

      typename std::map<std::string, T>::const_iterator it = configs_.find(configName);
      if (it == configs_.end())
         return false;
      configs_.erase(configName);
      return true;
   }

   /**
    * Deletes a preset property by name.
    */
   bool Delete(const char* configName, const char* deviceLabel, const char* propName)
   {
      // tolerate empty configuration names
      if (strlen(configName) == 0)
         return true;

	  // Check if configuration with configName exists:
	  typename std::map<std::string, T>::const_iterator it = configs_.find(configName);
	  if (it == configs_.end())
		  return false;
	  
	  // Delete the specified property
      configs_[configName].deleteSetting(deviceLabel,propName);
	  return true;
   }

   /**
    * Returns a list of available configurations.
    */
   std::vector<std::string> GetAvailable() const
   {
      std::vector<std::string> configList;
      typename std::map<std::string, T>::const_iterator it = configs_.begin();
      while(it != configs_.end())
         configList.push_back(it++->first);

      return configList;
   }

   bool IsEmpty()
   {
      return configs_.size() == 0;
   }

protected:
   ConfigGroupBase() {}
   virtual ~ConfigGroupBase() {}

   std::map<std::string, T> configs_;
};


/**
 * Encapsulates a collection (map) of user-defined presets.
 */
class ConfigGroup : public ConfigGroupBase<Configuration>
{
};

/**
 * Encapsulates a collection of preset groups.
 */
class ConfigGroupCollection {
public:
   ConfigGroupCollection() {}
   ~ConfigGroupCollection() {}

   /**
    * Define a configuration.
    */
   void Define(const char* groupName, const char* configName)
   {
      groups_[groupName].Define(configName);
   }

   /**
    * Define a configuration property value.
    */
   void Define(const char* groupName, const char* configName, const char* deviceLabel, const char* propName, const char* value)
   {
      groups_[groupName].Define(configName, deviceLabel, propName, value);
   }

   /**
    * Define a new empty group.
    */
   bool Define(const char* groupName)
   {
      std::map<std::string, ConfigGroup>::iterator it = groups_.find(groupName);
      if (it == groups_.end())
      {
         groups_[groupName]; // effectively inserts an empty group
         return true;
      }
      else
         return false; // group name already in use
   }

   /**
    * Finds preset (configuration) based on the group and preset names.
    */
   Configuration* Find(const char* groupName, const char* configName)
   {
      std::map<std::string, ConfigGroup>::iterator it = groups_.find(groupName);
      if (it == groups_.end())
         return 0;
      else
         return it->second.Find(configName);
   }

   /**
    * Checks if group exists.
    */
   bool isDefined(const char* groupName)
   {
      std::map<std::string, ConfigGroup>::iterator it = groups_.find(groupName);
      if (it == groups_.end())
         return false;
      else
         return true;
   }

   /**
    * Rename a configuration preset within a group
    */
   bool RenameConfig(const char* groupName, const char* oldConfigName, const char* newConfigName)
   {
      if (0 != strcmp(oldConfigName, newConfigName))
      {
         // tolerate empty group names
         if (strlen(groupName) == 0)
            return true;

         std::map<std::string, ConfigGroup>::iterator it = groups_.find(groupName);
         if (it == groups_.end())
            return false; // group not found
         if (it->second.Rename(oldConfigName, newConfigName))
         {
            // NOTE: changed to not remove empty groups, N.A. 1.31.2006
            // check if the config group is empty, and if so remove it
            //if (it->second.IsEmpty())
            //groups_.erase(it->first);
            return true;
         }
         else
            return false; // config not found within a group
      } else {
         return true;
      }
   }

   /**
    * Delete a property from a configuration in the specified group
    */
   bool Delete(const char* groupName, const char* configName, const char* deviceLabel, const char* propName)
   {
      // tolerate empty group names
      if (strlen(groupName) == 0)
         return true;

      std::map<std::string, ConfigGroup>::iterator it = groups_.find(groupName);
      if (it == groups_.end())
         return false; // group not found
      if (it->second.Delete(configName, deviceLabel, propName))
      {
         return true;
      }
      else
         return false; // config not found within a group
   }


   /**
    * Delete a configuration from group
    */
   bool Delete(const char* groupName, const char* configName)
   {
      // tolerate empty group names
      if (strlen(groupName) == 0)
         return true;

      std::map<std::string, ConfigGroup>::iterator it = groups_.find(groupName);
      if (it == groups_.end())
         return false; // group not found
      if (it->second.Delete(configName))
      {
         // NOTE: changed to not remove empty groups, N.A. 1.31.2006
         // check if the config group is empty, and if so remove it
         //if (it->second.IsEmpty())
            //groups_.erase(it->first);
         return true;
      }
      else
         return false; // config not found within a group
   }

   /**
    * Delete an entire group.
    */
   bool Delete(const char* groupName)
   {
      // tolerate empty group names
      if (strlen(groupName) == 0)
         return true;

      std::map<std::string, ConfigGroup>::iterator it = groups_.find(groupName);
      if (it != groups_.end())
      {
         groups_.erase(it->first);
         return true;
      }
      return false; //not found
   }

    /**
    * Rename a configuration group.
    */
   bool RenameGroup(const char* oldGroupName, const char* newGroupName)
   {
      if (0 != strcmp(oldGroupName, newGroupName))
      {
         // tolerate empty group names
         if (strlen(oldGroupName) == 0 || strlen(newGroupName)==0)
            return true;

         std::map<std::string, ConfigGroup>::iterator it = groups_.find(oldGroupName);
         if (it != groups_.end())
         {
            groups_[newGroupName] = it->second;
            groups_.erase(it->first);
            return true;
         }
         return false; //not found
      }
      else
      {
         return true;
      }
   }

   /**
    * Returns a list of groups names.
    */
   std::vector<std::string> GetAvailableGroups() const
   {
      std::vector<std::string> groupList;
      groupList.clear();
      std::map<std::string, ConfigGroup>::const_iterator it = groups_.begin();
      while(it != groups_.end())
         groupList.push_back(it++->first);

      return groupList;
   }

   /**
    * Returns a list of preset names.
    */
   std::vector<std::string> GetAvailableConfigs(const char* groupName) const
   {
      std::vector<std::string> confList;
      confList.clear();
      std::map<std::string, ConfigGroup>::const_iterator it = groups_.find(groupName);
      if (it != groups_.end())
      {
         confList = it->second.GetAvailable();
      }
      return confList;
   }

   void Clear()
   {
      groups_.clear();
   }


private:
   std::map<std::string, ConfigGroup> groups_;
};

/**
 * Specialized form of configuration designed to detect pixel size
 * from current settings.
 */
class PixelSizeConfiguration : public Configuration
{
public:
   PixelSizeConfiguration() : pixelSizeUm_(0.0)  
   {
      affineMatrix_.push_back(1.0);
      affineMatrix_.push_back(0.0);
      affineMatrix_.push_back(0.0);
      affineMatrix_.push_back(0.0);
      affineMatrix_.push_back(1.0);
      affineMatrix_.push_back(0.0);
   }
   ~PixelSizeConfiguration() {}

   void setPixelSizeUm(double pixSize) {pixelSizeUm_ = pixSize;}
   double getPixelSizeUm() const {return pixelSizeUm_;}
   void setPixelConfigAffineMatrix(std::vector<double> &affineMatrix) throw (CMMError)
   { 
      if (affineMatrix.size() != 6) 
      {
         throw new CMMError("PixelConfig affineMatrix has to have 6 elements");
      }
      for (int i=0; i < affineMatrix.size(); i++) 
      {
         affineMatrix_.at(i) = affineMatrix.at(i);
      }
   }

   std::vector<double> getPixelConfigAffineMatrix() {return affineMatrix_;}

private:
   double pixelSizeUm_;
   std::vector<double> affineMatrix_;
};

/**
 * Encapsulates a collection (map) of user-defined pixel size presets.
 */
class PixelSizeConfigGroup : public ConfigGroupBase<PixelSizeConfiguration>
{
public:
   /**
    * Defines a new preset with pixel size.
    */
   bool DefinePixelSize(const char* resolutionID, const char* deviceLabel, const char* propName, const char* value, double pixSizeUm)
   {
      PropertySetting setting(deviceLabel, propName, value);
      configs_[resolutionID].addSetting(setting);
      if (configs_[resolutionID].getPixelSizeUm() == 0.0)
      {
         // this is the first setting, so it is OK to set pixel size
         configs_[resolutionID].setPixelSizeUm(pixSizeUm);
         return true;
      }
      else
      {
         // pixel size already set and we won't allow the change
         // - this signifies a conflict in the configuration
         return false;
      }
   }
};

#endif

