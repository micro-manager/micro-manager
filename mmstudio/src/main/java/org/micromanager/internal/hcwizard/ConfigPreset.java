///////////////////////////////////////////////////////////////////////////////
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
// CVS:          $Id: ConfigPreset.java 1281 2008-06-04 20:59:50Z nenad $
//

package org.micromanager.internal.hcwizard;

import java.util.ArrayList;
import mmcorej.DoubleVector;

/**
 * Encapsulation of the preset data for use in the Configuration Wizard. 
 *
 */
public final class ConfigPreset {
   private String name_;
   private final ArrayList<Setting> settings_;
   
   // these fields are only used when the configuration preset
   // belongs to the pixelSize group
   private double pixelSizeUm_ = 0.0;
   private DoubleVector affineTransform_;
   
   public ConfigPreset() {
      name_ = "Undefined";
      settings_ = new ArrayList<>();
   }
   
   public ConfigPreset(String name) {
      name_ = name;
      settings_ = new ArrayList<>();
   }
   
   public String getName() {
      return name_;
   }
   
   public boolean addSetting(Setting s) {
      for (int i = 0; i < settings_.size(); i++) {
         if (getSetting(i).isEqualTo(s)) {
            return false;
         }
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
      for (int i = 0; i < settings_.size(); i++) {
         if (getSetting(i).matches(s)) {
            return true;
         }
      }
      return false;
   }
   
   public boolean removeSetting(Setting s) {
      for (int i = 0; i < settings_.size(); i++) {
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
   
   @Override
   public String toString() {
      return "Preset: " + name_;
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
   
   public void setAffineTransform(DoubleVector aft) {
      affineTransform_ = aft;
   }
   
   public DoubleVector getAffineTransform() {
      return affineTransform_;
   }
   
}
