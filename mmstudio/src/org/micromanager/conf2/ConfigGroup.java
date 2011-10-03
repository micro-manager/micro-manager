///////////////////////////////////////////////////////////////////////////////
//FILE:          ConfigGroup.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, November 7, 2006
//
// COPYRIGHT:    University of California, San Francisco, 2006
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
// CVS:          $Id: ConfigGroup.java 1281 2008-06-04 20:59:50Z nenad $
//

package org.micromanager.conf2;

import java.util.Hashtable;

/**
 * Configuration group encapsulation for use in Configuration Wizard. 
 */
public class ConfigGroup {
   String name_;
   Hashtable<String, ConfigPreset> configs_;
   
   public ConfigGroup(String name) {
      name_ = new String(name);
      configs_ = new Hashtable<String, ConfigPreset>();
   }
   
   public void addConfigPreset(ConfigPreset p) {
      configs_.put(p.getName(), p);
   }
   
   public String getName() {
      return name_;
   }
   
   public void addConfigSetting(String presetName, String device, String property, String value) {
      ConfigPreset cp = configs_.get(presetName);
      if (cp == null) {
         cp = new ConfigPreset(presetName);
         configs_.put(presetName, cp);
      }
      
      cp.addSetting(new Setting(device, property, value));
   }
   
   public ConfigPreset[] getConfigPresets() {
      Object objs[] = configs_.values().toArray();
      ConfigPreset[] cps = new ConfigPreset[objs.length];
      for (int i=0; i<objs.length; i++)
         cps[i] = (ConfigPreset)objs[i];
      return cps;
   }
   
   public String toString() {
      return new String("Group: " + name_);
   }

   public void removePreset(String name) {
      configs_.remove(name);
   }

   public ConfigPreset findConfigPreset(String name) {
      return configs_.get(name);
   }

   public void setName(String name) {
      name_ = name;
   }

   public void renamePreset(ConfigPreset prs, String name) {
      configs_.remove(prs.getName());
      prs.setName(name);
      configs_.put(name, prs);
   }
   
   public void clear() {
      configs_.clear();
   }
   
}
