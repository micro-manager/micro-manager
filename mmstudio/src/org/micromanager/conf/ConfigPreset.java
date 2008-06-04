///////////////////////////////////////////////////////////////////////////////
//FILE:          ConfigPreset.java
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
// CVS:          $Id$
//

package org.micromanager.conf;

import java.util.ArrayList;

/**
 * Encapsulation of the preset data for use in the Configuration Wizard. 
 *
 */
public class ConfigPreset {
   private String name_;
   private ArrayList<Setting> settings_;
   
   // this field is used onluy in case the configuration preset
   // belongs to the pixelSize group
   private double pixelSizeUm_ = 0.0;
   
   public ConfigPreset() {
      name_ = new String("Undefined");
      settings_ = new ArrayList<Setting>();
   }
   
   public ConfigPreset(String name) {
      name_ = name;
      settings_ = new ArrayList<Setting>();
   }
   
   public String getName() {
      return name_;
   }
   
   public boolean addSetting(Setting s) {
      for (int i=0; i<settings_.size(); i++) {
         if (getSetting(i).isEqualTo(s))
            return false;
         if (getSetting(i).matches(s)) {
            // replace existing
            settings_.set(i, s);
            return true;
         }
      }
      // add new
      settings_.add(s);
      return true;
   }
   
   public boolean matchSetting(Setting s) {
      for (int i=0; i<settings_.size(); i++) {
         if (getSetting(i).matches(s)) {
            return true;
         }
      }
      return false;
   }
   
   public boolean removeSetting(Setting s) {
      for (int i=0; i<settings_.size(); i++) {
         if (getSetting(i).isEqualTo(s)) {
            settings_.remove(i);
            return true;
         }
      }
      return false;
   }
   
   public int getNumberOfSettings() {
      return settings_.size();
   }
   
   public Setting getSetting(int i) {
      return settings_.get(i);
   }
   
   public String toString() {
      return new String("Preset: " + name_);
   }

   public void setName(String name) {
      name_ = name;
   }

   public void setPixelSizeUm(double ps) {
      pixelSizeUm_ = ps;
   }
   
   public double getPixelSize() {
      return pixelSizeUm_;
   }
}
