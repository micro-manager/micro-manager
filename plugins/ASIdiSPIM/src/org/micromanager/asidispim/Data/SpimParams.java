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

   private final HashMap<String, Property> propInfo_;  // contains all the information about the corresponding property
   private final List<SpimParamsListenerInterface> listeners_;

   // a list of strings used as keys to the propInfo_ hashmap
   public static final String NR_REPEATS = "NRepeats";
   public static final String NR_SIDES = "NSides";
   public static final String NR_SLICES = "NSlices";
   public static final String NR_LINESCANS_PER_SLICE = "NLinesScansPerSlice";
   public static final String LINE_SCAN_PERIOD = "LineScanPeriodA";
   public static final String LINE_SCAN_PERIOD_B = "LineScanPeriodB";
   public static final String DELAY_BEFORE_SLICE = "DelayBeforeSliceA";
   public static final String DELAY_BEFORE_SIDE = "DelayBeforeSideA";
   public static final String FIRSTSIDE = "FirstSide";
   public static final String FIRSTSIDE_A_VAL = "A";
   public static final String FIRSTSIDE_B_VAL = "B";  


   public SpimParams (ScriptInterface gui, Devices devices) {
      gui_ = gui;
      core_ = gui_.getMMCore();
      devices_ = devices;
      listeners_ = new ArrayList<SpimParamsListenerInterface>();

      // populate the map the property keys from Java with the associated information stored in Property type
      // this includes the device adapter's name for the property, the device adapter (via the Devices class), and integer/string/float
      // TODO if there are two different cards for A and B then have 2 separate values for numSides, etc.
      propInfo_ = new HashMap<String, Property>();
      Property prop;
      prop = new Property(FIRSTSIDE, "SPIMFirstSide", Devices.GALVOA, PropTypes.STRING);
      propInfo_.put(prop.pluginName, prop);
      prop = new Property(NR_REPEATS, "SPIMNumRepeats", Devices.GALVOA, PropTypes.INTEGER);
      propInfo_.put(prop.pluginName, prop);
      prop = new Property(NR_SIDES, "SPIMNumSides", Devices.GALVOA, PropTypes.INTEGER);
      propInfo_.put(prop.pluginName, prop);
      prop = new Property(NR_REPEATS, "SPIMNumRepeats", Devices.GALVOA, PropTypes.INTEGER);
      propInfo_.put(prop.pluginName, prop);
      prop = new Property(NR_SLICES, "SPIMNumSlices", Devices.GALVOA, PropTypes.INTEGER);
      propInfo_.put(prop.pluginName, prop);
      prop = new Property(NR_LINESCANS_PER_SLICE, "SPIMNumScansPerSlice", Devices.GALVOA, PropTypes.INTEGER);
      propInfo_.put(prop.pluginName, prop);
      prop = new Property(LINE_SCAN_PERIOD, "SingleAxisXPeriod(ms)", Devices.GALVOA, PropTypes.INTEGER);
      propInfo_.put(prop.pluginName, prop);
      prop = new Property(LINE_SCAN_PERIOD_B, "SingleAxisXPeriod(ms)", Devices.GALVOB, PropTypes.INTEGER);
      propInfo_.put(prop.pluginName, prop);
      prop = new Property(DELAY_BEFORE_SLICE, "SPIMDelayBeforeSlice(ms)", Devices.GALVOA, PropTypes.INTEGER);
      propInfo_.put(prop.pluginName, prop);
      prop = new Property(DELAY_BEFORE_SIDE, "SPIMDelayBeforeSide(ms)", Devices.GALVOA, PropTypes.INTEGER);
      propInfo_.put(prop.pluginName, prop);
   }

   /**
    * writes string property value to the device adapter using a core call
    * @param key property name in Java; key to property hashmap
    * @param strVal value in string form, sent to core using setProperty()
    */
   public void setPropValue(String key, String strVal) {
      synchronized (propInfo_) {
         if (propInfo_.containsKey(key)) {
            Property prop = propInfo_.get(key);
            String mmDevice = null;
            try {
               mmDevice = devices_.getMMDevice(prop.pluginDevice);
               core_.setProperty(mmDevice, prop.adapterName, strVal);
            } catch (Exception ex) {
               ReportingUtils.showError("Error setting string property "+ prop.adapterName + " to " + strVal + " in device " + mmDevice);
            }

         }
      }
   }

   /**
    * writes integer property value to the device adapter using a core call
    * @param key property name in Java; key to property hashmap
    * @param intVal value in integer form, sent to core using setProperty()
    */
   public void setPropValue(String key, int intVal) {
      synchronized (propInfo_) {
         if (propInfo_.containsKey(key)) {
            Property prop = propInfo_.get(key);
            String mmDevice = null;
            try {
               mmDevice = devices_.getMMDevice(prop.pluginDevice);
               core_.setProperty(mmDevice, prop.adapterName, intVal);
            } catch (Exception ex) {
               ReportingUtils.showError("Error setting int property " + prop.adapterName + " in device " + mmDevice);
            }

         }
      }
   }

   /**
    * writes float property value to the device adapter using a core call
    * @param key property name in Java; key to property hashmap
    * @param intVal value in integer form, sent to core using setProperty()
    */
   public void setPropValue(String key, float floatVal) {
      synchronized (propInfo_) {
         if (propInfo_.containsKey(key)) {
            Property prop = propInfo_.get(key);
            String mmDevice = null;
            try {
               mmDevice = devices_.getMMDevice(prop.pluginDevice);
               core_.setProperty(mmDevice, prop.adapterName, floatVal);
            } catch (Exception ex) {
               ReportingUtils.showError("Error setting float property " + prop.adapterName + " in device " + mmDevice);
            }

         }
      }
   }



   /**
    * gets the property hashmap data
    * @param key property name in Java; key to property hashmap
    * @return the associative array with property info
    */
   private Property getPropEntry(String key) {
      return propInfo_.get(key);
   }

   /**
    * reads the property value from the device adapter using a core call
    * @param key property name in Java; key to property hashmap
    * @return value in string form, returned from core call to getProperty()
    */
   private String getPropValue(String key) {
      // TODO see if this needs synchronize statement
      String val = null;
      Property prop = propInfo_.get(key);
      boolean throwException = true;
      if ((prop==null)) {
         if (throwException) {
            ReportingUtils.showError("Could not get property for " + key);
         }
         return val;
      }
      String mmDevice = devices_.getMMDevice(prop.pluginDevice);
      if (mmDevice==null || mmDevice.equals("")) {
         if (throwException) {
            ReportingUtils.showError("Could not get device for property " + key + " with " + prop.pluginDevice);
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
    * @param key property name in Java; key to property hashmap
    * @return
    * @throws ParseException
    */
   public int getPropValueInteger(String key) {
      int val = 0;
      try {
         val = NumberUtils.coreStringToInt(getPropValue(key));
      } catch (ParseException ex) {
         ReportingUtils.showError("Could not parse int value of " + key);
      }
      catch (Exception ex) {
         ReportingUtils.showError("Could not get int value of property " + key);
      }
      return val;
   }

   /**
    * returns an float value for the specified property (assumes the caller knows the property contains a float)
    * @param key property name in Java; key to property hashmap
    * @return
    * @throws ParseException
    */
   public float getPropValueFloat(String key) {
      float val = 0;
      try {
         val = (float)NumberUtils.coreStringToDouble(getPropValue(key));
      } catch (ParseException ex) {
         ReportingUtils.showError("Could not parse float value of " + key);
      }
      catch (Exception ex) {
         ReportingUtils.showError("Could not get float value of property " + key);
      }
      return val;
   }

   /**
    * returns a string value for the specified property
    * @param key property name in Java; key to property hashmap
    * @return
    */
   public String getPropValueString(String key) {
      return getPropValue(key);
   }

   public PropTypes getPropType(String key) {
      return getPropEntry(key).propType;
   }

   @Override
   public void devicesChangedAlert() {
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
