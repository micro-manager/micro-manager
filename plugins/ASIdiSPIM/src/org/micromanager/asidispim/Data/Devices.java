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

import mmcorej.CMMCore;
import mmcorej.DeviceType;
import mmcorej.StrVector;

import org.micromanager.api.ScriptInterface;
import org.micromanager.asidispim.Utils.DevicesListenerInterface;
import org.micromanager.asidispim.Utils.MyDialogUtils;


// TODO implement fast axis reverse checkbox (may need device adapter change and possibly even firmware-level change)

/**
 * Class that holds information about the selected devices. This should always
 * be the only source of device information. The GUI should update this class
 * and use its information.
 * 
 * This could be implemented more elegantly using templates
 * 
 * @author nico
 * @author Jon
 */
public class Devices {

   private List<String> loadedDevices_;
   private Prefs prefs_;
   private CMMCore core_;
   private List<DevicesListenerInterface> listeners_;
   private boolean listenersEnabled_;
   
   /**
    * A, B, or N for not associated with either side specifically. Now sides are
    * labeled as "paths" in GUI to avoid confusion, but code still has "sides"
    * nomenclature
    */
   public static enum Sides {
      A("A"), // side A
      B("B"), // side B
      NONE("N"); // not associated with either side specifically
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
    * Enum containing Device keys, representing role of each device (each key is
    * mapped to a particular Micro-manager device according to the ComboBoxes in
    * DevicesPanel).
    */
   public static enum Keys {
      NONE, CORE, PLUGIN,
      CAMERAA, CAMERAB, MULTICAMERA, CAMERALOWER, CAMERAPREVIOUS, 
      PIEZOA, PIEZOB, GALVOA, GALVOB, XYSTAGE, LOWERZDRIVE, UPPERZDRIVE,
      // SOURCE_SPIM, SOURCE_LOWER,
      PLOGIC,
      // ASGALVOA, ASGALVOB,
      // when adding new devices update Devices constructor, 
      // getDefaultDeviceData(), and Libraries enum
   };
   
   public final static Set<Devices.Keys> STAGES1D = EnumSet.of(
         Devices.Keys.LOWERZDRIVE, Devices.Keys.UPPERZDRIVE,
         Devices.Keys.PIEZOA, Devices.Keys.PIEZOB);
   public final static Set<Devices.Keys> STAGES2D = EnumSet.of(
         Devices.Keys.XYSTAGE, Devices.Keys.GALVOA, Devices.Keys.GALVOB);
   public final static Set<Devices.Keys> GALVOS = EnumSet.of(
         Devices.Keys.GALVOA, Devices.Keys.GALVOB);
   public final static Set<Devices.Keys> CAMERAS = EnumSet.of(
         Devices.Keys.CAMERAA, Devices.Keys.CAMERAB, Devices.Keys.MULTICAMERA,
         Devices.Keys.CAMERALOWER);
//   public final static Set<Devices.Keys> SOURCES = EnumSet.of(
//         Devices.Keys.SOURCE_SPIM, Devices.Keys.SOURCE_LOWER
//         );

   public static enum Libraries {
      NODEVICE("NoDevice"), // if the device doesn't exist in Micro-manager
                            // (e.g. no camera has been selected)
      ASITIGER("ASITiger"), // ASI TG-1000 controller
      HAMCAM("HamamatsuHam"), 
      PCOCAM("PCO_Camera"), 
      ANDORCAM("AndorSDK3"),
      DEMOCAM("DemoCamera"),
      UTILITIES("Utilities"),
      UNKNOWN("Unknown"), // if the device is valid but not one we know about
      ;
      private final String text;

      Libraries(String text) {
         this.text = text;
      }

      @Override
      public String toString() {
         return text;
      }
   }

   private static final String DEVICES_PREF_NODE = "Devices";

   /**
    * associative class to store information about devices
    * 
    * @author Jon
    */
   public static class DeviceData {
      Keys key;
      String mmDevice; // device name in Micro-manager
      String displayName; // how device should be rendered in GUI
      Sides side;
      String axisLetter; // the letter or letters
      mmcorej.DeviceType type;
      Libraries deviceLibrary;
      boolean saveInPref;

      /**
       * @param key device key as given by enum in Devices.Keys
       * @param displayName name shown in the GUI
       * @param side enum A, B, or N
       * @param saveInPref true if we save this value to preferences
       */
      public DeviceData(Keys key, String displayName, Sides side,
            boolean saveInPref) {
         this.key = key;
         this.mmDevice = "";
         this.displayName = displayName;
         this.side = side;
         this.axisLetter = "";
         this.type = mmcorej.DeviceType.UnknownType;
         this.deviceLibrary = Devices.Libraries.NODEVICE;
         this.saveInPref = saveInPref;
      }

      @Override
      public String toString() {
         switch (side) { // break not needed here
         case A:
         case B:
            return displayName + " " + side.toString();
         case NONE:
            return displayName;
         default:
            return "";
         }
      }
   }

   /**
    * Holds the data associated with the device, keyed by Devices.Keys enums
    */
   private final Map<Devices.Keys, DeviceData> deviceInfo_;

   /**
    * Takes a key and side specifier and returns the corresponding side-specific
    * key or else NONE if it cannot be found or doesn't exist. For example,
    * PIEZOA with side B will return PIEZOB
    * 
    * @param genericKey
    * @param side
    * @return
    */
   public static Devices.Keys getSideSpecificKey(Devices.Keys genericKey,
         Devices.Sides side) {
      switch (genericKey) {
      case PIEZOA:
      case PIEZOB:
         switch (side) {
         case A:
            return Devices.Keys.PIEZOA;
         case B:
            return Devices.Keys.PIEZOB;
         default:
            return Devices.Keys.NONE;
         }
      case GALVOA:
      case GALVOB:
         switch (side) {
         case A:
            return Devices.Keys.GALVOA;
         case B:
            return Devices.Keys.GALVOB;
         default:
            return Devices.Keys.NONE;
         }
      case CAMERAA:
      case CAMERAB:
         switch (side) {
         case A:
            return Devices.Keys.CAMERAA;
         case B:
            return Devices.Keys.CAMERAB;
         default:
            return Devices.Keys.NONE;
         }
      default:
         return Devices.Keys.NONE;
      }
   }

   /**
    * Takes a side specifier and returns the opposite side (A->B and B->A).
    * NONE gets mapped to NONE.
    * 
    * @param side
    * @return
    */
   public static Devices.Sides getOppositeSide(Devices.Sides side) {
      switch (side) {
      case A:
         return Devices.Sides.B;
      case B:
         return Devices.Sides.A;
      default:
         return Devices.Sides.NONE;
      }
   }

   public static Devices.Sides getSideFromKey(Devices.Keys key) {
      switch (key) {
      case GALVOA:
      case CAMERAA:
      case PIEZOA:
         return Devices.Sides.A;
      case GALVOB:
      case CAMERAB:
      case PIEZOB:
         return Devices.Sides.B;
      default:
         return Devices.Sides.NONE;
      }
   }

   /**
    * Associates a Micro-manager device name with the Device key. 
    * Changes deviceInfo_.
    * @param key  Device key (enum) as defined in this class
    * @param mmDevice
    */
   public void setMMDevice(Devices.Keys key, String mmDevice) {
      DeviceData d = deviceInfo_.get(key);
      if (d == null) { // do nothing if key doesn't exist
         return;
      }
      if (mmDevice == null || mmDevice.equals("")) { 
         // sometimes from init this will be null instead of empty string
         //   like it should
         // restore to empty default DeviceData structure if no device is
         //   selected or if there is an error
         d = getDefaultDeviceData(key);
      } else {
         // populate the DeviceData structure with actual device information
         d.mmDevice = mmDevice;
         try {
            d.type = core_.getDeviceType(mmDevice);
            String library = core_.getDeviceLibrary(mmDevice);
            d.deviceLibrary = Devices.Libraries.UNKNOWN;
            for (Devices.Libraries lib_test : Devices.Libraries.values()) {
               if (library.equals(lib_test.toString())) {
                  d.deviceLibrary = lib_test;
                  break;
               }
            }
         } catch (Exception e) {
            d.type = mmcorej.DeviceType.UnknownType;
            d.deviceLibrary = Devices.Libraries.UNKNOWN;
         }
         if (d.deviceLibrary == Devices.Libraries.ASITIGER) {
            try {
               // can't use plugin's prop_ object because it hasn't been created
               //  yet => have to resort to core_ calls despite wanting to use
               //  props_ everywhere else
               if (core_.hasProperty(mmDevice, "AxisLetter")) {
                  d.axisLetter = core_.getProperty(mmDevice, "AxisLetter");
               }
               if (core_.hasProperty(mmDevice, "AxisLetterX") && 
                     core_.hasProperty(mmDevice, "AxisLetterY")) {
                  d.axisLetter = core_.getProperty(mmDevice, "AxisLetterX") 
                        + core_.getProperty(mmDevice, "AxisLetterY");
               }
            } catch (Exception ex) {
               MyDialogUtils.showError(ex);
            }
         }
      }
      deviceInfo_.put(key, d);
      callListeners(); // make sure everybody else knows about change
   }
   
   /**
    * Returns true if a device has been assigned
    * @param key
    * @return
    */
   public boolean isValidMMDevice(Devices.Keys key) {
      return (key != Devices.Keys.NONE && getMMDevice(key) != null);
   }

   /**
    * Looks up the Micro-Manager device name currently set for particular Device
    * key.
    * 
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
    * Looks up the Micro-Manager device name currently set for particular Device
    * key.
    * 
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
    * Looks up the Micro-Manager device names currently set for an array of
    * Device keys.
    * 
    * @param keys Array of Device keys (enums) as defined in this class
    * @return array of Micro-Manager deviceNames
    */
   public String[] getMMDevices(Devices.Keys[] keys) {
      List<String> devices = new ArrayList<String>();
      for (Devices.Keys key : keys) {
         String d = getMMDevice(key);
         if (d != null) {
            devices.add(d);
         }
      }
      return devices.toArray(new String[0]);
   }

   /**
    * Looks up the Micro-Manager device type currently set for particular Device
    * key.
    * 
    * @param key Device key (enum) as defined in this class
    * @return Micro-Manager device type, or mmcorej.DeviceType.UnknownType if
    *         not found
    */
   public DeviceType getMMDeviceType(Devices.Keys key) {
      return deviceInfo_.get(key).type;
   }

   /**
    * Looks up the Micro-Manager device library currently set for particular
    * Device key.
    * 
    * @param key Device key (enum) as defined in this class
    * @return Micro-Manager device library, or Devices.Libraries.UNKNOWN
    */
   public Libraries getMMDeviceLibrary(Devices.Keys key) {
      return deviceInfo_.get(key).deviceLibrary;
   }
   
   /**
    * call core's hasProperty, just with our keys as inputs
    * 
    * @param devKey Device key (enum) as defined in this class
    * @param propKey Property key (enum)
    * @return
    */
   public boolean hasProperty(Devices.Keys devKey, Properties.Keys propKey) {
      try {
         return core_.hasProperty(getMMDevice(devKey), propKey.toString());
      } catch (Exception e) {
         return false;
      }
   }
   
   /**
    * returns true iff the device is from ASI Tiger
    * @param devKey
    * @return
    */
   public boolean isTigerDevice(Devices.Keys devKey) {
      return (deviceInfo_.get(devKey).deviceLibrary == Devices.Libraries.ASITIGER);
   }

   /**
    * @param key
    * @return true if XYstage, false otherwise
    */
   public boolean isXYStage(Devices.Keys key) {
      return (deviceInfo_.get(key).type == mmcorej.DeviceType.XYStageDevice);
   }

   /**
    * @param key
    * @return true if 1D stage (piezo or motorized), false otherwise
    */
   public boolean is1DStage(Devices.Keys key) {
      return (deviceInfo_.get(key).type == mmcorej.DeviceType.StageDevice);
   }

   /**
    * @param key
    * @return true if galvo, false otherwise
    */
   public boolean isGalvo(Devices.Keys key) {
      return (deviceInfo_.get(key).type == mmcorej.DeviceType.GalvoDevice);
   }

   /**
    * @param key from Devices.Keys
    * @return string used to describe device, e.g. "MicroMirror A" or "XY Stage"
    */
   public String getDeviceDisplay(Devices.Keys key) {
      return deviceInfo_.get(key).toString();
   }

   /**
    * Returns "generic" descriptor, e.g. without side A designation appended
    * 
    * @param key
    * @return
    */
   public String getDeviceDisplayGeneric(Devices.Keys key) {
      return deviceInfo_.get(key).displayName;
   }

   /**
    * Returns verbose description, including axis role (but not axis letter)
    * @param key
    * @return
    */
   private String getDeviceDisplayVerboseEnding(Devices.Keys key, Joystick.Directions dir) {
      String ret = "";
      switch (key) {
      case GALVOA:
      case GALVOB:
         switch (dir) {
         case X: ret += ", sheet (X)"; break;
         case Y: ret += ", slice (Y)"; break;
         default: break;
         }
         break;
      case XYSTAGE:
         switch (dir) {
         case X: ret += ", X axis"; break;
         case Y: ret += ", Y axis"; break;
         default: break;
         }
         break;
      default: break;
      }
      return ret;
   }
   
   /**
    * Returns verbose description, including axis role (but not axis letter)
    * @param key
    * @return
    */
   public String getDeviceDisplayVerbose(Devices.Keys key, Joystick.Directions dir) {
      return getDeviceDisplay(key) + getDeviceDisplayVerboseEnding(key, dir);
   }
   
   /**
    * returns device description with role for the particular side (used for joystick labels)
    * camera labels use a different mechanicsm, ideally would merge the two
    */
   public String getDeviceDisplayWithRole(Devices.Keys key, Joystick.Directions dir, Devices.Sides side) {
      String ret;
      DeviceData d = deviceInfo_.get(key);
      switch (key) {
      case GALVOA:
      case GALVOB:
        if (side == d.side) {
           ret = "Sheet Beam";
        } else if (side == Devices.getOppositeSide(d.side)) {
           ret = "Epi Beam";
        } else {
           ret = getDeviceDisplay(key);
        }
        ret += getDeviceDisplayVerboseEnding(key, dir);
        break;
      case PIEZOA:
      case PIEZOB:
         if (side == d.side) {
            ret = "Imaging Piezo";
         } else if (side == Devices.getOppositeSide(d.side)) {
            ret = "Illumination Piezo";
         } else {
            ret = getDeviceDisplay(key);
         }
         break;
      default:
         ret = getDeviceDisplay(key);
      }
      return ret;
   }
   
   /**
    * Returns verbose description, including axis role (but not axis letter)
    * @param key
    * @return
    */
   public String getDeviceDisplayVerbose(Devices.Keys key) {
      return getDeviceDisplayVerbose(key, Joystick.Directions.NONE);
   }
   
   /**
    * Returns display string with axis letter included. For 2D device both axes are
    * included.
    * 
    * @param key
    * @return
    */
   public String getDeviceDisplayWithAxisLetter(Devices.Keys key) {
      String ret = getDeviceDisplay(key);
      DeviceData d = deviceInfo_.get(key);
      if (!d.axisLetter.equals("")) {
         ret = ret + " (" + d.axisLetter + ")";
      }
      return ret;
   }

   /**
    * Returns display string with axis letter included, but only specified axis for 2D
    * device.
    * 
    * @param key
    * @return
    */
   public String getDeviceDisplayWithAxisLetter1D(Devices.Keys key,
         Joystick.Directions dir) {
      String ret = getDeviceDisplay(key);
      String axisLetter = deviceInfo_.get(key).axisLetter;
      if (axisLetter.length() < 2) {
         return getDeviceDisplayWithAxisLetter(key);
      }
      switch (dir) {
      // assumes the "X" axis is the first char of two
      case X:
         ret = ret + ", " + Joystick.Directions.X.toString() + " axis ("
               + axisLetter.charAt(0) + ")";
         break;
      case Y:
         ret = ret + ", " + Joystick.Directions.Y.toString() + " axis ("
               + axisLetter.charAt(1) + ")";
         break;
      case NONE:
      default:
         return getDeviceDisplayWithAxisLetter(key);
      }
      return ret;
   }

   /**
    * Saves to preferences the mapping between Device keys and Micro-manager
    * device names
    */
   public void saveSettings() {
      for (Devices.Keys key : deviceInfo_.keySet()) {
         String mmDevice = getMMDevice(key);
         if (mmDevice == null) {
            mmDevice = "";
         }
         // TODO make it so empty devices don't get saved, instead add "None" to the list which is saved
         if (deviceInfo_.get(key).saveInPref) {
            prefs_.putString(DEVICES_PREF_NODE, key.toString(), mmDevice);
         }
      }
   }

   /**
    * Reads mapping between Device keys and Micro-manager device names from
    * preferences. Assumes loadedDevices_ contains all available devices, and
    * sets any no-longer-existing device to null. Changes deviceInfo_.
    */
   public final void restoreSettings() {
      for (Devices.Keys key : Devices.Keys.values()) {
         if (deviceInfo_.get(key).saveInPref) {
            String mmDevice = prefs_.getString(DEVICES_PREF_NODE, key.toString(), "");
            if (!loadedDevices_.contains(mmDevice)) {
               mmDevice = "";
            }
            DeviceData d = deviceInfo_.get(key);
            d.mmDevice = mmDevice;
            deviceInfo_.put(key, d);
         }
      }
   }

   /**
    * Queries core to see what devices are available
    * 
    * @return ArrayList containing the Micro-Manager names of all loaded devices
    */
   private ArrayList<String> GetLoadedDevices() {
      ArrayList<String> list = new ArrayList<String>();
      StrVector strv = core_.getLoadedDevices();
      for (int i = 0; i < strv.size(); i++) {
         list.add(strv.get(i));
      }
      return list;
   }

   /**
    * Gets the initial/default DeviceData structure for the specified device key.
    * Should include all possible devices.
    * 
    * @param key
    * @return
    */
   final DeviceData getDefaultDeviceData(Devices.Keys key) {
      DeviceData d_new;
      switch (key) {
      case CAMERAA:
         return new DeviceData(key, "Camera", Sides.A, true);
      case CAMERAB:
         return new DeviceData(key, "Camera", Sides.B, true);
      case CAMERALOWER:
         return new DeviceData(key, "Lower Camera", Sides.NONE,
               true);
      case MULTICAMERA:
         return new DeviceData(key, "Multi Camera", Sides.NONE,
               true);
      case PIEZOA:
         return new DeviceData(key, "Imaging Piezo", Sides.A, true);
      case PIEZOB:
         return new DeviceData(key, "Imaging Piezo", Sides.B, true);
      case GALVOA:
         return new DeviceData(key, "Scanner", Sides.A, true);
      case GALVOB:
         return new DeviceData(key, "Scanner", Sides.B, true);
      case XYSTAGE:
         return new DeviceData(key, "XY Stage", Sides.NONE, true);
      case LOWERZDRIVE:
         return new DeviceData(key, "Lower Z Drive", Sides.NONE,
               true);
      case UPPERZDRIVE:
         return new DeviceData(key, "Upper (SPIM) Z Drive",
               Sides.NONE, true);
         // case ASGALVOA: return new DeviceData(Keys.ASGALVOA,
         // "Anti-striping Micromirror", Sides.A, true);
         // case ASGALVOB: return new DeviceData(Keys.ASGALVOB,
         // "Anti-striping Micromirror", Sides.B, true);
      // case SOURCE_SPIM:
      //   return new DeviceData(key, "SPIM Light Source", Sides.NONE, true);
      // case SOURCE_LOWER:
      //   return new DeviceData(key, "Lower Light Source", Sides.NONE, true);
      case PLOGIC:
         return new DeviceData(key, "PLogic Card", Sides.NONE, true);
      case CORE: // special case
         d_new = new DeviceData(key, "Core", Sides.NONE, false);
         d_new.mmDevice = "Core";
         d_new.type = mmcorej.DeviceType.CoreDevice;
         return d_new;
      case PLUGIN: // special case
         d_new = new DeviceData(key, "Plugin", Sides.NONE, false);
         return d_new;
      case CAMERAPREVIOUS: // special case
         d_new = new DeviceData(key, "No change", Sides.NONE, false);
         return d_new;
      case NONE:
      default:
         return new DeviceData(key, "None", Sides.NONE, false);
      }
   }


   /**
    * constructor
    * @param gui
    * @param prefs
    */
   public Devices(ScriptInterface gui, Prefs prefs) {
      prefs_ = prefs;
      core_ = gui.getMMCore();

      // create synchronized version of data structure containing Device
      // information and populate it
      // populating must be done dynamically because some elements (e.g.
      // mmDevice field) are dynamic (updated later)
      // must make sure that every key is initialized here
      deviceInfo_ = Collections
            .synchronizedMap(new EnumMap<Devices.Keys, DeviceData>(
                  Devices.Keys.class));
      deviceInfo_.put(Keys.NONE, getDefaultDeviceData(Keys.NONE));
      deviceInfo_.put(Keys.CAMERAA, getDefaultDeviceData(Keys.CAMERAA));
      deviceInfo_.put(Keys.CAMERAB, getDefaultDeviceData(Keys.CAMERAB));
      deviceInfo_.put(Keys.CAMERALOWER, getDefaultDeviceData(Keys.CAMERALOWER));
      deviceInfo_.put(Keys.MULTICAMERA, getDefaultDeviceData(Keys.MULTICAMERA));
      deviceInfo_.put(Keys.PIEZOA, getDefaultDeviceData(Keys.PIEZOA));
      deviceInfo_.put(Keys.PIEZOB, getDefaultDeviceData(Keys.PIEZOB));
      deviceInfo_.put(Keys.GALVOA, getDefaultDeviceData(Keys.GALVOA));
      deviceInfo_.put(Keys.GALVOB, getDefaultDeviceData(Keys.GALVOB));
      deviceInfo_.put(Keys.XYSTAGE, getDefaultDeviceData(Keys.XYSTAGE));
      deviceInfo_.put(Keys.LOWERZDRIVE, getDefaultDeviceData(Keys.LOWERZDRIVE));
      deviceInfo_.put(Keys.UPPERZDRIVE, getDefaultDeviceData(Keys.UPPERZDRIVE));
      deviceInfo_.put(Keys.PLOGIC, getDefaultDeviceData(Keys.PLOGIC));
      // deviceInfo_.put(Keys.SOURCE_SPIM,  getDefaultDeviceData(Keys.SOURCE_SPIM));
      // deviceInfo_.put(Keys.SOURCE_LOWER,  getDefaultDeviceData(Keys.SOURCE_LOWER));
      // deviceInfo_.put(Keys.ASGALVOA, getDefaultDeviceData(Keys.ASGALVOA));
      // deviceInfo_.put(Keys.ASGALVOB, getDefaultDeviceData(Keys.ASGALVOB));

      // special core device
      deviceInfo_.put(Keys.CORE, getDefaultDeviceData(Keys.CORE));
      
      // device for plugin "properties" (not device adapter)
      deviceInfo_.put(Keys.PLUGIN, getDefaultDeviceData(Keys.PLUGIN));
      
      // special device representing "don't change the camera"
      deviceInfo_.put(Keys.CAMERAPREVIOUS, getDefaultDeviceData(Keys.CAMERAPREVIOUS));

      listeners_ = new ArrayList<DevicesListenerInterface>();
      listenersEnabled_ = true;

      loadedDevices_ = GetLoadedDevices();
      restoreSettings(); // look for settings from last time
   }//constructor

   /**
    * Used to add classes implementing DeviceListenerInterface as listeners
    */
   public void addListener(DevicesListenerInterface listener) {
      listeners_.add(listener);
   }

   /**
    * Enable or disable listeners. When switching from disabled to enabled it
    * will perform callListeners()
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