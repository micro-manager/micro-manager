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

package org.micromanager.asidispim.Data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.prefs.Preferences;

import mmcorej.CMMCore;
import mmcorej.StrVector;

import org.micromanager.MMStudioMainFrame;
import org.micromanager.asidispim.Utils.DevicesListenerInterface;

// TODO implement fast axis reverse checkbox (may need device adapter change and possibly even firmware-level change)

/**
 * Class that holds information about the selected devices.
 * This should always be the only source of device information.
 * The GUI should update this class and use its information.
 * 
 * This could be implemented more elegantly using templates
 * 
 * @author nico
 * @author Jon
 */
public class Devices {

   /**
    * A, B, or N for not associated with either side specifically.
    * Now sides are labeled as "paths" in GUI to avoid confusion, but code still has "sides" nomenclature
    */
   public static enum Sides {
      A("A"),  // side A
      B("B"),  // side B
      N("N");   // not associated with either side specifically
      private final String text;
      Sides(String text) {
         this.text = text;
      }
      @Override
      public String toString() {
         return text;
      }
   }
   
   /**
    * Enum containing Device keys, representing role of each device
    * (each key is mapped to a particular Micro-manager device according
    * to the ComboBoxes in DevicesPanel)    
    */
   public static enum Keys { NONE,
      CAMERAA, CAMERAB, MULTICAMERA, 
      PIEZOA, PIEZOB,
      GALVOA, GALVOB,
      XYSTAGE, LOWERZDRIVE, UPPERZDRIVE
//      ASGALVOA, ASGALVOB,
      };
      
   public final static Set<Devices.Keys> STAGES1D = EnumSet.of(
         Devices.Keys.LOWERZDRIVE, 
         Devices.Keys.UPPERZDRIVE, 
         Devices.Keys.PIEZOA, 
         Devices.Keys.PIEZOB
         );
   public final static Set<Devices.Keys> STAGES2D = EnumSet.of(
         Devices.Keys.XYSTAGE,
         Devices.Keys.GALVOA,
         Devices.Keys.GALVOB
         );      
      
   /**
    * associative class to store information about devices
    * @author Jon
    */
   public static class DeviceData {
      Keys key;
      String mmDevice;  // device name in Micro-manager
      String displayName;  // how device should be rendered in GUI
      Sides side; 
      String axisLetter;  // the letter or letters
      
      /**
       * @param key device key as given by enum in Devices.Keys
       * @param displayName name shown in the GUI
       * @param side enum A, B, or N
       */
      public DeviceData(Keys key, String displayName, Sides side) {
         this.key = key;
         this.mmDevice = "";
         this.displayName = displayName;
         this.side = side;
         this.axisLetter = "";
      }
      
      @Override
      public String toString() {
         switch (side) { // break not needed here
         case A: 
         case B: return displayName + " " + side.toString();
         case N: return displayName;
         default: return "";
         }
      }
   }
  
   /**
    * Holds the data associated with the device, keyed by Devices.Keys enums
    */
   private final Map<Devices.Keys, DeviceData> deviceInfo_;
      
   /**
    * Takes a key and side specifier and returns the corresponding side-specific key
    * or else NONE if it cannot be found or doesn't exist.  For example, 
    * PIEZOA with side B will return PIZEOB
    * @param genericKey
    * @param side
    * @return
    */
   public Devices.Keys getSideSpecificKey(Devices.Keys genericKey, Devices.Sides side) {
      Devices.Keys ret = Devices.Keys.NONE;
      switch (genericKey) {
      case PIEZOA:
      case PIEZOB:
         switch (side) {
         case A: ret = Devices.Keys.PIEZOA; break;
         case B: ret = Devices.Keys.PIEZOB; break;
         case N: break;
         default: break;
         }
         break;
      case GALVOA:
      case GALVOB:
         switch (side) {
         case A: ret = Devices.Keys.GALVOA; break;
         case B: ret = Devices.Keys.GALVOB; break;
         case N: break;
         default: break;
         }
         break;
      case CAMERAA:
      case CAMERAB:
         switch (side) {
         case A: ret = Devices.Keys.CAMERAA; break;
         case B: ret = Devices.Keys.CAMERAB; break;
         case N: break;
         default: break;
         }
         break;
      default: break;
      }
      return ret;
   }
   
   /**
    * Takes a side specifier and returns the opposite side (A->B and B->A)
    * @param side
    * @return
    */
   public Devices.Sides getOppositeSide(Devices.Sides side) {
      Devices.Sides ret = Devices.Sides.N;
      switch (side) {
      case A: ret = Devices.Sides.B; break;
      case B: ret = Devices.Sides.A; break;
      default: break;
      }
      return ret;
   }
   
   /**
    * Associates a Micro-manager device name with the Device key.  
    * Changes deviceInfo_.
    * @param key Device key (enum) as defined in this class
    * @param mmDevice 
    */
   public void setMMDevice(Devices.Keys key, String mmDevice) {
      DeviceData d = deviceInfo_.get(key);
      if (d==null) {  // do nothing if key doesn't exist
         return;
      }
      if (mmDevice==null) {  // sometimes from init this will be null instead of empty string like it should
         mmDevice = "";
      }
      d.mmDevice = mmDevice;
      // if a ASITiger device then add axis letters
      int idx1 = mmDevice.indexOf(":");
      if (idx1 > 0) {
         int idx2 = mmDevice.indexOf(":", idx1+1);
         if (idx2 > 0) {
            d.axisLetter = mmDevice.substring(idx1+1, idx2); 
         }
      }
      deviceInfo_.put(key, d);
      callListeners();  // make sure everybody else knows about change
   }

   /**
    * Looks up the Micro-Manager device name currently set for particular Device key.
    * @param key Device key (enum) as defined in this class
    * @return Micro-Manager deviceName, or null when not found
    */
   public String getMMDevice(Devices.Keys key) {
      String mmDevice = deviceInfo_.get(key).mmDevice;
      if (mmDevice == null || mmDevice.equals("")) {
         return null;
      }
      return mmDevice;
   }
   
   /**
    * Looks up the Micro-Manager device name currently set for particular Device key.
    * @param key Device key (enum) as defined in this class
    * @return Micro-Manager deviceName, or null when not found
    * @throws Exception 
    */
   public String getMMDeviceException(Devices.Keys key) throws Exception {
      String mmDevice = getMMDevice(key);
      if (mmDevice == null || mmDevice.equals("")) {
         throw (new Exception("No device set for " + key.toString()));
      }
      return mmDevice;
   }

   /**
    * Looks up the Micro-Manager device names currently set for an array
    * of Device keys.
    * @param keys Array of Device keys (enums) as defined in this class
    * @return array of Micro-Manager deviceNames
    */
   public String[] getMMDevices(Devices.Keys[] keys) {
      List<String> devices = new ArrayList<String>();
      for (Devices.Keys key : keys) {
         String d = getMMDevice(key);
         if (d!=null) {
            devices.add(d);
         }
      }
      return devices.toArray(new String[0]);
   }
   
   /**
    * 
    * @param key from Devices.Keys
    * @return  string used to describe device, e.g. "MicroMirror A" or "XY Stage"
    */
   public String getDeviceDisplay(Devices.Keys key) {
      return deviceInfo_.get(key).toString();
   }
   
   /**
    * Returns "generic" descriptor, e.g. without side A designation appended
    * @param key
    * @return
    */
   public String getDeviceDisplayGeneric(Devices.Keys key) {
      return deviceInfo_.get(key).displayName;
   }
   
   /**
    * Returns display string with axis included.  For 2D device both axes are included.
    * @param key
    * @return
    */
   public String getDeviceDisplayWithAxis(Devices.Keys key) {
      String ret = getDeviceDisplay(key);
      DeviceData d = deviceInfo_.get(key);
      if (!d.axisLetter.equals("")) {
         ret = ret + " (" + d.axisLetter + ")";
      }
      return ret;
   }
   
   /**
    * Returns display string with axis included, but only specified axis for 2D device.
    * @param key
    * @return
    */
   public String getDeviceDisplayWithAxis1D(Devices.Keys key, Joystick.Directions dir) {
      String ret = getDeviceDisplay(key);
      String axisLetter = deviceInfo_.get(key).axisLetter;
      if (axisLetter.length() < 2) {
         return getDeviceDisplayWithAxis(key);
      }
      switch (dir) {
      // assumes the "X" axis is the first char of two
      case X:
         ret = ret + ", " + Joystick.Directions.X.toString() + " axis (" + axisLetter.charAt(0) + ")";
         break;
      case Y:
         ret = ret + ", " + Joystick.Directions.Y.toString() + " axis (" + axisLetter.charAt(1) + ")";
         break;
      case NONE:
      default:
         return getDeviceDisplayWithAxis(key);
      }
      return ret;
   }
   
   /**
    * Saves to preferences the mapping between Device keys and Micro-manager device names
    */
   public void saveSettings() {
      for (Devices.Keys key : deviceInfo_.keySet()) {
         String mmDevice = getMMDevice(key);
         if (mmDevice == null) {
            mmDevice = "";
         }
         prefs_.put(key.toString(), mmDevice); 
      }
   }
   
   /**
    * Reads mapping between Device keys and Micro-manager device names from preferences.
    * Assumes loadedDevices_ contains all available devices, and sets any no-longer-existing device to null.
    * Changes deviceInfo_.
    */
   public final void restoreSettings() {
      for (Devices.Keys key : Devices.Keys.values()) {
         String mmDevice = prefs_.get(key.toString(), "");
         if (!loadedDevices_.contains(mmDevice)) {
            mmDevice = "";
         }
         DeviceData d = deviceInfo_.get(key);
         d.mmDevice = mmDevice;
         deviceInfo_.put(key, d);
      }
   }
   
   /**
    * Queries core to see what devices are available
    * @return ArrayList containing the Micro-Manager names of all loaded devices
    */
   private ArrayList<String> GetLoadedDevices() {
      ArrayList<String> list = new ArrayList<String>();
      StrVector strv = core_.getLoadedDevices();
      for (int i =0; i < strv.size(); i++) {
         list.add(strv.get(i));
      }
      return list;
   }
   

   private List<String>loadedDevices_;
   private Preferences prefs_;
   private CMMCore core_;
   private List<DevicesListenerInterface> listeners_;
   private boolean listenersEnabled_;

   public Devices() {
      prefs_ = Preferences.userNodeForPackage(this.getClass());
      core_ = MMStudioMainFrame.getInstance().getCore();
      
      // create synchronized version of data structure containing Device information and populate it
      // populating must be done dynamically because some elements (e.g. mmDevice field) are dynamic
      // must make sure that every key is initialized here
      deviceInfo_ = Collections.synchronizedMap(new EnumMap<Devices.Keys, DeviceData>(Devices.Keys.class));
      deviceInfo_.put(Keys.NONE, new DeviceData(Keys.NONE, "None", Sides.N));
      deviceInfo_.put(Keys.CAMERAA, new DeviceData(Keys.CAMERAA, "Camera", Sides.A));
      deviceInfo_.put(Keys.CAMERAB, new DeviceData(Keys.CAMERAB, "Camera", Sides.B));
      deviceInfo_.put(Keys.MULTICAMERA, new DeviceData(Keys.MULTICAMERA, "Multi Camera", Sides.N));
      deviceInfo_.put(Keys.PIEZOA, new DeviceData(Keys.PIEZOA, "Imaging Piezo", Sides.A));
      deviceInfo_.put(Keys.PIEZOB, new DeviceData(Keys.PIEZOB, "Imaging Piezo", Sides.B));
      deviceInfo_.put(Keys.GALVOA, new DeviceData(Keys.GALVOA, "Sheet Galvo", Sides.A));
      deviceInfo_.put(Keys.GALVOB, new DeviceData(Keys.GALVOB, "Sheet Galvo", Sides.B));
      deviceInfo_.put(Keys.XYSTAGE, new DeviceData(Keys.XYSTAGE, "XY Stage", Sides.N));
      deviceInfo_.put(Keys.LOWERZDRIVE, new DeviceData(Keys.LOWERZDRIVE, "Lower Z Drive", Sides.N));
      deviceInfo_.put(Keys.UPPERZDRIVE, new DeviceData(Keys.UPPERZDRIVE, "Upper (SPIM) Z Drive", Sides.N));
//      deviceInfo_.put(Keys.ASGALVOA, new DeviceData(Keys.ASGALVOA, "Anti-striping Micromirror", Sides.A));
//      deviceInfo_.put(Keys.ASGALVOB, new DeviceData(Keys.ASGALVOB, "Anti-striping Micromirror", Sides.B));

      listeners_ = new ArrayList<DevicesListenerInterface>();
      listenersEnabled_ = true;
      
      loadedDevices_ = GetLoadedDevices();
      restoreSettings();    // look for settings from last time
   }
   

   /**
    * Used to add classes implementing DeviceListenerInterface as listeners
    */
   public void addListener(DevicesListenerInterface listener) {
      listeners_.add(listener);
   }

   /**
    * Enable or disable listeners.  When switching from disabled to enabled
    * it will perform callListeners()
    */
   public void enableListeners(boolean enabled) {
      if (enabled && !listenersEnabled_) {
         listenersEnabled_ = enabled;
         callListeners();
      }
      listenersEnabled_ = enabled;
   }
   
   
   /**
    * Remove classes implementing the DeviceListener interface from the listers
    *
    * @param listener
    */
   public void removeListener(DevicesListenerInterface listener) {
      listeners_.remove(listener);
   }
   
   /**
    * Call each listener in succession to alert them that something changed
    */
   public void callListeners() {
      if (listenersEnabled_) {
         for (DevicesListenerInterface listener : listeners_) {
            listener.devicesChangedAlert();
         }
      }
   }
   

}