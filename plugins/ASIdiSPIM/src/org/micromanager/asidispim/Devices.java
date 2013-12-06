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

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;
import mmcorej.CMMCore;
import org.micromanager.MMStudioMainFrame;
import org.micromanager.utils.ReportingUtils;

/**
 * Class that holds information about the selected devices
 * This should always be the only source of device information
 * The GUI should update this class and use this information
 * 
 * This could be implemented more elegantly using templates
 * 
 * @author nico
 */
public class Devices {
   // useful static constants 

   public static enum JoystickDevice {
      JOYSTICK, RIGHT_KNOB, LEFT_KNOB
   };
   public static final Map<JoystickDevice, String> JOYSTICKMAP =
           new EnumMap<JoystickDevice, String>(JoystickDevice.class);
   static {
      JOYSTICKMAP.put(JoystickDevice.JOYSTICK, "Joystick");
      JOYSTICKMAP.put(JoystickDevice.RIGHT_KNOB, "Right Knob");
      JOYSTICKMAP.put(JoystickDevice.LEFT_KNOB, "Left Know");
   }
   public static final Map<String, JoystickDevice> REVJOYSTICKMAP =
           new HashMap<String, JoystickDevice>();
   static {
      Iterator<JoystickDevice> it = JOYSTICKMAP.keySet().iterator();
      while (it.hasNext()) {
         JoystickDevice term = it.next();
         REVJOYSTICKMAP.put(JOYSTICKMAP.get(term), term);
      }
   }
   
   /*
    public static enum AbstractDevices {
    CAMERAA, CAMERAB, DUALCAMERA, PIEZOA, PIEZOB, GALVOA, GALVOB, GALVOC,
    GALVOD, XYSTAGE, LOWERZDRIVE, UPPERZDRIVE };
    public static final Map<AbstractDevices, String> DEVICEMAP = 
    new EnumMap<AbstractDevices, String>(AbstractDevices.class);
    static {
    DEVICEMAP.put(AbstractDevices.CAMERAA,"CameraA");
    }
    * */
   // TODO: change to enums:
   public static final String CAMERAA = "CameraA";
   public static final String CAMERAB = "CameraB";
   public static final String DUALCAMERA = "Dual Camera";
   public static final String PIEZOA = "PiezoA";
   public static final String PIEZOB = "PiezoB";
   public static final String GALVOA = "GalvoA";
   public static final String GALVOB = "GalvoB";
   public static final String GALVOC = "GalvoC";
   public static final String GALVOD = "GalvoD";
   public static final String XYSTAGE = "XY Stage";
   public static final String LOWERZDRIVE = "Lower Z Drive";
   public static final String UPPERZDRIVE = "Upper Z Drive";
   public static final String[] DEVICES = {
      CAMERAA, CAMERAB, DUALCAMERA, PIEZOA, PIEZOB, GALVOA, GALVOB, GALVOC,
      GALVOD, XYSTAGE, LOWERZDRIVE, UPPERZDRIVE};
   public static final String[] TIGERDEVICES = {PIEZOA, PIEZOB, GALVOA,
      GALVOB, GALVOC, GALVOD, XYSTAGE, LOWERZDRIVE, UPPERZDRIVE};
   public static final String[] TWOAXISTIGERDEVICES = {GALVOA, GALVOB, GALVOC,
      GALVOD, XYSTAGE};
   public static final String[] ONEAXISTIGERDEVICES = {PIEZOA, PIEZOB,
      LOWERZDRIVE, UPPERZDRIVE};
   
   public static final String FASTAXISAREV = "FastAxisARev";
   public static final String FASTAXISBREV = "FastAxisBRev";
   private static final String[] FASTAXISREVS = {FASTAXISAREV, FASTAXISBREV};
   public static final String FASTAXISADIR = "FastAxisADir";
   public static final String FASTAXISBDIR = "FastAxisBDir";
   public static final String FASTAXISCDIR = "FastAxisCDir";
   public static final String FASTAXISDDIR = "FastAxisDDir";
   private static final String[] FASTAXISDIRS = {FASTAXISADIR, FASTAXISBDIR,
      FASTAXISCDIR, FASTAXISDDIR};
   public static final String X = "X";
   public static final String Y = "Y";
   
   
   // Non-static variables
   private HashMap<String, String> deviceInfo_;
   private HashMap<String, Boolean> axisRevs_;
   private HashMap<String, String> axisDirs_;
   private Map<JoystickDevice, DirectionalDevice[]> controllerMap_;
   private List<DevicesListenerInterface> listeners_;
   private Preferences prefs_;
   private CMMCore core_;

   public Devices() {
      prefs_ = Preferences.userNodeForPackage(this.getClass());
      core_ = MMStudioMainFrame.getInstance().getCore();

      deviceInfo_ = new HashMap<String, String>();
      axisRevs_ = new HashMap<String, Boolean>();
      axisDirs_ = new HashMap<String, String>();
      controllerMap_ = new EnumMap<JoystickDevice, DirectionalDevice[]>(JoystickDevice.class);
      listeners_ = new ArrayList<DevicesListenerInterface>();

      for (String device : DEVICES) {
         deviceInfo_.put(device, prefs_.get(device, ""));
      }

      for (String axisRev : FASTAXISREVS) {
         axisRevs_.put(axisRev, prefs_.getBoolean(axisRev, false));
      }

      for (String axisDir : FASTAXISDIRS) {
         axisDirs_.put(axisDir, prefs_.get(axisDir, X));
      }

   }

   /**
    * TODO: throw exception when non-existent key is provided
    *
    * @param key - devicetype
    * @param value - devicename
    */
   public synchronized void putDeviceInfo(String key, String value) {
      if (deviceInfo_.containsKey(key)) {
         deviceInfo_.put(key, value);
         callListeners();
      }
   }

   /**
    * Gets the name under which Micro-Manager knows the device
    *
    * @param deviceType - DeviceType as defined in this class
    * @return Micro-Manager deviceName, or null when not found
    */
   public synchronized String getMMDevice(String deviceType) {
      return deviceInfo_.get(deviceType);
   }

   /**
    * Given an input array of abstract device names, returns the corresponding
    * set of names under which Micro-Manager knows these devices
    * Does not include null for devices not known as Micro-Manager devices
    *
    * @param devices array with abstract device names
    * @return array with Micro-Manager device names
    */
   public synchronized String[] getMMDevices(String[] devices) {
      List<String> tigerDevices = new ArrayList<String>();
      for (String device : devices) {
         String d = deviceInfo_.get(device);
         if (d != null) {
            tigerDevices.add(deviceInfo_.get(d));
         }
      }
      return (String[]) tigerDevices.toArray();
   }
   
   /**
    * Creates and array with the abstract names of the drives in the 
    * tiger controller
    * @return 
    */
   public String[] getTigerDrives() {
      List<String> res = new ArrayList<String>();
      for (String dev : Devices.ONEAXISTIGERDEVICES) {
         if (getMMDevice(dev) != null) {
            res.add(dev);
         }
      }
      for (String dev : Devices.TWOAXISTIGERDEVICES) {
         if (getMMDevice(dev) != null) {
            res.add(dev + "-X");
            res.add(dev + "-Y");
         }
      }
      return (String[]) res.toArray(new String[0]);
   }
   
   public String[] getTwoAxisTigerDrives() {
      List<String> res = new ArrayList<String>();
      for (String dev : Devices.TWOAXISTIGERDEVICES) {
         if (getMMDevice(dev) != null) {
            res.add(dev);
         }
      }
      return (String[]) res.toArray(new String[0]);
   }

   /**
    * TODO: throw exception when key is not found
    *
    * @param key - AxisName
    * @param value Axisdirection (true == Reverse)
    */
   public synchronized void putFastAxisRevInfo(String key, boolean value) {
      if (axisRevs_.containsKey(key)) {
         axisRevs_.put(key, value);
         callListeners();
      }
   }

   /**
    * Gets the direction of the specified fast axis
    *
    * TODO: throw exception when axisDir is not found
    *
    * @param axisRev - FASTAXISADIR or FASTAXISBDIR
    * @return axis direction (true == Reverse)
    */
   public synchronized boolean getFastAxisRevInfo(String axisRev) {
      return axisRevs_.get(axisRev);
   }

   /**
    * TODO: throw exception when non-existent key is provided, or value other
    * than X or Y
    *
    * @param key - Axis Type (
    * @param value - X or Y
    */
   public synchronized void putAxisDirInfo(String key, String value) {
      if (value.equals(X) || value.equals(Y)) {
         if (axisDirs_.containsKey(key)) {
            axisDirs_.put(key, value);
            callListeners();
         }
      }
   }

   /**
    * Gets the name under which Micro-Manager knows the device
    *
    * @param axisDir - FASTAXISADIR, FASTAXISBDIR, etc.
    * @return Micro-Manager deviceName, or null when not found
    */
   public synchronized String getAxisDirInfo(String axisDir) {
      return axisDirs_.get(axisDir);
   }

   /**
    * Associates the given input device (knob or joystick) to the desired device
    * Each device specifies its direction (usually X or Y) as a
    * DirectionalDevice Two devices can be associated with a Joystick (first one
    * will be linked to the Joystick X, second one to the Joystick Y) A knob can
    * have only one directional device associated
    *
    * @param control Joystick, left or right knob
    * @param devices A combination of a Tiger device and direction
    */
   public void setJoystickOutput(JoystickDevice joystickDevice,
           DirectionalDevice[] devices) {
      // first clear whatever associations are present
      String[] oneAxisTigerDevices = getMMDevices(ONEAXISTIGERDEVICES);
      String propName = "JoystickInput";
      String noInput = "0 - none";
      for (String device : oneAxisTigerDevices) {
         try {
            core_.setProperty(device, propName, noInput);
         } catch (Exception ex) {
            ReportingUtils.showError("Failed to communicate with Tiger controller");
         }
      }
      String[] twoAxisTigerDevices = getMMDevices(TWOAXISTIGERDEVICES);
      for (String device : twoAxisTigerDevices) {
         try {
            core_.setProperty(device, propName + "X", noInput);
            core_.setProperty(device, propName + "Y", noInput);
         } catch (Exception ex) {
            ReportingUtils.showError("Failed to communicate with tiger controller");
         }
      }

      try {
         // default to joystick device
         String setting = "2 - joystick X";
         if (joystickDevice.equals(JoystickDevice.LEFT_KNOB)) {
            setting = "23 - left wheel";
         } else if (joystickDevice.equals(JoystickDevice.RIGHT_KNOB)) {
            setting = "22 - right wheel";
         } 
         
         String mmDevice = getMMDevice(devices[0].getName());
         if (devices[0].getNrOfAxis() == DirectionalDevice.NumberOfAxis.ONE) {
            core_.setProperty(mmDevice, propName, setting);
         } else if (devices[0].getNrOfAxis() == DirectionalDevice.NumberOfAxis.TWO) {
            core_.setProperty(mmDevice, propName + devices[0].getDir().toString(), 
                    propName);
         }
      } catch (Exception ex) {
            ReportingUtils.showError("Failed to communiocate with tiger controller");
         }
      controllerMap_.put(joystickDevice, devices);
      // TODO: write code talking to controller  
   }

   public DirectionalDevice[] getControlledDevice(JoystickDevice control) {
      return controllerMap_.get(control);
   }

   /**
    * Writes deviceInfo_ back to Preferences
    */
   public synchronized void saveSettings() {
      for (String device : DEVICES) {
         prefs_.put(device, deviceInfo_.get(device));
      }
      for (String axisRev : FASTAXISREVS) {
         prefs_.putBoolean(axisRev, axisRevs_.get(axisRev));
      }

      for (String axisDir : FASTAXISDIRS) {
         prefs_.put(axisDir, axisDirs_.get(axisDir));
      }
   }

   public void addListener(DevicesListenerInterface listener) {
      listeners_.add(listener);
   }

   public void removeListener(DevicesListenerInterface listener) {
      listeners_.remove(listener);
   }

   private void callListeners() {
      for (DevicesListenerInterface listener : listeners_) {
         listener.devicesChangedAlert();
      }
   }
}
