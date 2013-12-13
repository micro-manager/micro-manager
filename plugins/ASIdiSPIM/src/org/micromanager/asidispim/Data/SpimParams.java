///////////////////////////////////////////////////////////////////////////////
//FILE:          SpimParams.java
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

import org.micromanager.asidispim.Utils.Labels.PropTypes;
import org.micromanager.asidispim.Utils.Labels.Property;
import org.micromanager.asidispim.Utils.SpimParamsListenerInterface;
import org.micromanager.asidispim.Utils.DevicesListenerInterface;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.prefs.Preferences;

import mmcorej.CMMCore;

import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.NumberUtils;
import org.micromanager.utils.ReportingUtils;


/**
 *
 * @author nico
 * @author Jon
 */
public class SpimParams implements DevicesListenerInterface {
   private Devices devices_;
   private ScriptInterface gui_;
   private CMMCore core_;
   
   private final HashMap<String, Integer> integerInfo_;
   private final HashMap<String, String> sidesInfo_;
   private final HashMap<String, Float> floatInfo_;
   private final HashMap<String, Property> propInfo_;
   private final List<SpimParamsListenerInterface> listeners_;
   private Preferences prefs_;
   
   public static final String NR_SIDES = "NSides";
   public static final String NR_REPEATS = "NRepeats";
   public static final String NR_SLICES = "NSlices";
   public static final String NR_LINESCANS_PER_SLICE = "NLinesScansPerSlice";
   public static final String LINE_SCAN_PERIOD_A = "LineScanPeriodA";
   public static final String LINE_SCAN_PERIOD_B = "LineScanPeriodB";
   public static final String DELAY_BEFORE_SLICE_A = "DelayBeforeSliceA";
   public static final String DELAY_BEFORE_SLICE_B = "DelayBeforeSliceB";
   public static final String DELAY_BEFORE_SIDE_A = "DelayBeforeSideA";
   public static final String DELAY_BEFORE_SIDE_B = "DelayBeforeSideB";
   private static final String[] INTS = {
         NR_SIDES, NR_REPEATS, NR_SLICES, NR_LINESCANS_PER_SLICE, 
         LINE_SCAN_PERIOD_A, LINE_SCAN_PERIOD_B,
         DELAY_BEFORE_SLICE_A, DELAY_BEFORE_SLICE_B, DELAY_BEFORE_SIDE_A, 
         DELAY_BEFORE_SIDE_B};

   public static final String FIRSTSIDE = "FirstSide";
   public static final String A = "A";
   public static final String B = "B";  
   

   public SpimParams (ScriptInterface gui, Devices devices) {
      gui_ = gui;
      core_ = gui_.getMMCore();
      devices_ = devices;
      listeners_ = new ArrayList<SpimParamsListenerInterface>();
      
      prefs_ = Preferences.userNodeForPackage(this.getClass());
      
      integerInfo_ = new HashMap<String, Integer>();
      sidesInfo_ = new HashMap<String, String>();
      floatInfo_ = new HashMap<String, Float>();
      
      propInfo_ = new HashMap<String, Property>();
      
      // todo if there are two different cards for A and B then have 2 separate values for numSides, etc.
      
      Property prop;
      prop = new Property(NR_SIDES, "SPIMNumSides", Devices.GALVOA, PropTypes.INTEGER);
      propInfo_.put(prop.pluginName, prop);
      prop = new Property(NR_REPEATS, "SPIMNumRepeats", Devices.GALVOA, PropTypes.INTEGER);
      propInfo_.put(prop.pluginName, prop);
      prop = new Property(NR_SLICES, "SPIMNumSlices", Devices.GALVOA, PropTypes.INTEGER);
      propInfo_.put(prop.pluginName, prop);
      prop = new Property(NR_LINESCANS_PER_SLICE, "SPIMNumScansPerSlice", Devices.GALVOA, PropTypes.INTEGER);
      propInfo_.put(prop.pluginName, prop);
      prop = new Property(LINE_SCAN_PERIOD_A, "SingleAxisXPeriod(ms)", Devices.GALVOA, PropTypes.INTEGER);
      propInfo_.put(prop.pluginName, prop);
      prop = new Property(LINE_SCAN_PERIOD_B, "SingleAxisXPeriod(ms)", Devices.GALVOB, PropTypes.INTEGER);
      propInfo_.put(prop.pluginName, prop);
      prop = new Property(DELAY_BEFORE_SLICE_A, "SPIMDelayBeforeSlice(ms)", Devices.GALVOA, PropTypes.INTEGER);
      propInfo_.put(prop.pluginName, prop);
      prop = new Property(DELAY_BEFORE_SLICE_B, "SPIMDelayBeforeSlice(ms)", Devices.GALVOB, PropTypes.INTEGER);
      propInfo_.put(prop.pluginName, prop);
      prop = new Property(DELAY_BEFORE_SIDE_A, "SPIMDelayBeforeSide(ms)", Devices.GALVOA, PropTypes.INTEGER);
      propInfo_.put(prop.pluginName, prop);
      prop = new Property(DELAY_BEFORE_SIDE_B, "SPIMDelayBeforeSide(ms)", Devices.GALVOB, PropTypes.INTEGER);
      propInfo_.put(prop.pluginName, prop);
         
      updateInfo();
   }
   
   /**
    * Updates SpimParams information.  Reads it first from Preferences,
    * then reads it from data (where possible)
    */
   private void updateInfo() {
      for (String iInfo : INTS) {
         integerInfo_.put(iInfo, prefs_.getInt(iInfo, 1));
         
         if (iInfo.equals(LINE_SCAN_PERIOD_A)) {
            getLineScanProp(Devices.GALVOA, iInfo,
                    devices_.getAxisDirInfo(Devices.FASTAXISADIR));
         }
         if (iInfo.equals(LINE_SCAN_PERIOD_B)) {
            getLineScanProp(Devices.GALVOB, iInfo,
                    devices_.getAxisDirInfo(Devices.FASTAXISBDIR));
         }
      }
      sidesInfo_.put(FIRSTSIDE, prefs_.get(FIRSTSIDE, "A"));
      
   }
   
   public void putIntInfo(String key, int val) {
      synchronized (integerInfo_) {
         if (integerInfo_.containsKey(key)) {
            String mma = null;
            String propName = null;
            try {
               if (key.equals(LINE_SCAN_PERIOD_A)) {
                  mma = devices_.getMMDevice(Devices.GALVOA);
                  propName = "SingleAxis"
                          + devices_.getAxisDirInfo(Devices.FASTAXISADIR) + "Period(ms)";
                  core_.setProperty(mma, propName, val);
               }
               if (key.equals(LINE_SCAN_PERIOD_B)) {
                  mma = devices_.getMMDevice(Devices.GALVOB);
                  propName = "SingleAxis"
                          + devices_.getAxisDirInfo(Devices.FASTAXISBDIR) + "Period(ms)";
                  core_.setProperty(mma, propName, val);
               }
               integerInfo_.put(key, val);
            } catch (Exception ex) {
               ReportingUtils.showError("Error setting property " + propName + 
                       "in device" + mma);
            }
         }
      }
      
      synchronized (propInfo_) {
    	  if (propInfo_.containsKey(key)) {
    		  Property prop = propInfo_.get(key);
    		  String mmDevice = null;
    		  try {
    			  mmDevice = devices_.getMMDevice(prop.pluginDevice);
    			  core_.setProperty(mmDevice, prop.adapterName, val);
    		  } catch (Exception ex) {
    			  ReportingUtils.showError("Error setting property " + prop.adapterName + "in device" + mmDevice);
    		  }
    		  
    	  }
      }
   }

   /**
    * gets the property data
    * @param pluginName property name in Java; key to property hashhap
    * @return the associative array with property info
    */
   private Property getPropEntry(String pluginName) {
	   return propInfo_.get(pluginName);
   }
   
   /**
    * reads the property value from the device adapter
    * @param pluginName property name in Java; key to property hashhap
    * @return value in string form, returned from core call to getProperty()
    */
   private String getPropValue(String pluginName) {
	   String val = null;
	   Property prop = propInfo_.get(pluginName);
	   boolean throwException = true;
	   if ((prop==null)) {
		   if (throwException) {
			   ReportingUtils.showError("Could not get property for " + pluginName);
		   }
		   return val;
	   }
	   String mmDevice = devices_.getMMDevice(prop.pluginDevice);
	   if (mmDevice==null || mmDevice.equals("")) {
		   if (throwException) {
			   ReportingUtils.showError("Could not get device for property " + pluginName + " with " + prop.pluginDevice);
		   }
	   }
	   else
	   {
		   try {
			   val = core_.getProperty(mmDevice, prop.adapterName);
		   } catch (Exception ex) {
			   ReportingUtils.showError("Could not get property " + prop.adapterName + " from device " + mmDevice);
		   }
	   }
	   return val;
   }

   /**
    * returns an integer value for the specified property (assumes the caller knows the property contains an integer)
    * @param pluginName property name in Java; key to property hashmap
    * @return
    * @throws ParseException
    */
   public int getPropValueInteger(String pluginName) throws ParseException {
	   return NumberUtils.coreStringToInt(getPropValue(pluginName));
   }
   
   /**
    * returns an float value for the specified property (assumes the caller knows the property contains a float)
    * @param pluginName property name in Java; key to property hashmap
    * @return
    * @throws ParseException
    */
   public float getPropValueFloat(String pluginName) throws ParseException {
	   return (float)NumberUtils.coreStringToDouble(getPropValue(pluginName));
   }
   
   /**
    * returns a string value for the specified property
    * @param pluginName property name in Java; key to property hashmap
    * @return
    */
   public String getPropValueString(String pluginName) {
	   return getPropValue(pluginName);
   }
   
   public PropTypes getPropType(String pluginName) {
	   return getPropEntry(pluginName).propType;
   }
   
   private void getLineScanProp(String deviceName, String iInfo, String fastAxis) {
      String mm = devices_.getMMDevice(deviceName);
      if (mm!= null && !mm.equals("")) {
         try {
            String propName = "SingleAxis"
                    + fastAxis + "Period(ms)";
            String result = core_.getProperty(mm, propName);
            integerInfo_.put(iInfo, NumberUtils.coreStringToInt(result));
         } catch (Exception ex) {
            ReportingUtils.showError("Problem communicating with device: "
                    + devices_.getMMDevice(mm));
         }
      }
   }
   
  
   public int getIntInfo(String key) {
      synchronized (integerInfo_) {
         return integerInfo_.get(key);
      }
   }
   
   public void putSidesInfo(String key, String val) {
      synchronized (sidesInfo_) {
         if (val.equals(A) || val.equals(B) && sidesInfo_.containsKey(key)) {
            sidesInfo_.put(key, val);
         }
      }
   }

   public String getSidesInfo(String key) {
      synchronized (sidesInfo_) {
         return sidesInfo_.get(key);
      }
   }
   
   public void putFloatInfo(String key, float val) {
      synchronized (floatInfo_) {
         if (floatInfo_.containsKey(key)) {
            floatInfo_.put(key, val);
         }
      }
   }
   
   public float getFloatInfo(String key) {
      synchronized(floatInfo_) {
         return floatInfo_.get(key);
      }
   }
   
  /**
    * Writes info_ back to Preferences
    */
   public void saveSettings() {
      synchronized (integerInfo_) {
         for (String myI : INTS) {
            prefs_.putInt(myI, integerInfo_.get(myI));
         }
      }
      prefs_.put(FIRSTSIDE, sidesInfo_.get(FIRSTSIDE));
   }

   @Override
   public void devicesChangedAlert() {
      updateInfo();
      callListeners();
   }

   public void addListener(SpimParamsListenerInterface listener) {
      listeners_.add(listener);
   }
   
   public void removeListener(SpimParamsListenerInterface listener) {
      listeners_.remove(listener);
   }
   
   private void callListeners() {
      for (SpimParamsListenerInterface listener: listeners_) {
         listener.spimParamsChangedAlert();
      }
   }
   
}
