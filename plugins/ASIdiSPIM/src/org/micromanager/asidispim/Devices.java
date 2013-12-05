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
import java.util.HashMap;
import java.util.List;
import java.util.prefs.Preferences;

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
   private HashMap<String, String> deviceInfo_;
   private HashMap<String, Boolean> axisRevs_;
   private HashMap<String, String> axisDirs_;
   private List<DevicesListenerInterface> listeners_;
   private Preferences prefs_;
   
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
   private static final String[] DEVICES = {
      CAMERAA, CAMERAB, DUALCAMERA, PIEZOA, PIEZOB, GALVOA, GALVOB, GALVOC, 
      GALVOD, XYSTAGE, LOWERZDRIVE, UPPERZDRIVE};
   
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
   
   
   public Devices () {
      prefs_ = Preferences.userNodeForPackage(this.getClass());
      
      deviceInfo_ = new HashMap<String, String>();
      axisRevs_ = new HashMap<String, Boolean>();
      axisDirs_ = new HashMap<String, String>();
      listeners_ = new ArrayList<DevicesListenerInterface>();
      
      for  (String device : DEVICES) {
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
   public synchronized String getDeviceInfo(String deviceType) {
      return deviceInfo_.get(deviceType);
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
    * TODO: throw exception when non-existent key is provided,
    * or value other than X or Y
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
