///////////////////////////////////////////////////////////////////////////////
//FILE:          Devices.java
//PROJECT:       Micro-Manager 
//SUBSYSTEM:     ASIdiSPIM plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman, Jon Daniels
//
// COPYRIGHT:    University of California, San Francisco, & ASI, 2013
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

package org.micromanager.asidispim;

import java.util.HashMap;
import java.util.prefs.Preferences;

/**
 * Class that holds information about the selected devices
 * This should always be the only source of device information
 * The GUI should update this class and use this information
 * 
 * @author nico
 */
public class Devices {
   private HashMap<String, String> deviceInfo_;
   private Preferences prefs_;
   
   public static final String CAMERAA = "CameraA";
   public static final String CAMERAB = "CameraB";
   public static final String PIEZOA = "PiezoA";
   public static final String PIEZOB = "PiezoB";
   
   private static final String[] DEVICES = {
      CAMERAA, CAMERAB, PIEZOA, PIEZOB};
   
   
   public Devices () {
      prefs_ = Preferences.userNodeForPackage(this.getClass());
      
      deviceInfo_ = new HashMap<String, String>();
      
      for  (String device : DEVICES) {
         deviceInfo_.put(device, prefs_.get(device, ""));
      }
            
   }
   
   /**
    * TODO: throw exception when non-existent key is provided
    * 
    * @param key - devicetype
    * @param value - devicename
    */
   public synchronized void putInfo(String key, String value) {
      if (deviceInfo_.containsKey(key)) {
         deviceInfo_.put(key, value);
      }
   }
   
   public synchronized String getInfo(String deviceType) {
      return deviceInfo_.get(deviceType);
   }
   
   /**
    * Writes deviceInfo_ back to Preferences
    */
   public synchronized void saveSettings() {
      for (String device : DEVICES) {
         prefs_.put(device, deviceInfo_.get(device));
      }
      
   }
}
