///////////////////////////////////////////////////////////////////////////////
//FILE:          Properties.java
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

import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import mmcorej.CMMCore;

import org.micromanager.MMStudioMainFrame;
import org.micromanager.asidispim.Utils.UpdateFromPropertyListenerInterface;
import org.micromanager.utils.NumberUtils;
import org.micromanager.utils.ReportingUtils;


/**
 * Contains data and methods related to getting and setting device properties.
 * Ideally this is the only place where properties are read and set.
 * One instance of this class exists in the top-level class.
 * 
 * Currently the property "reads" default to ignoring errors due to missing
 *  device or property, but property "writes" default to reporting errors
 *  due to missing device or property
 * 
 * @author Jon
 * @author nico
 */
public class Properties {

   /**
    * List of all device adapter properties used.  The enum value (all caps) 
    * is used in the Java code.  The corresponding string value (in quotes) is 
    * the value used by the device adapter.
    */
   public static enum Keys {
      JOYSTICK_ENABLED("JoystickEnabled"),
      JOYSTICK_INPUT("JoystickInput"),
      JOYSTICK_INPUT_X("JoystickInputX"),
      JOYSTICK_INPUT_Y("JoystickInputY"),
      SPIM_NUM_SIDES("SPIMNumSides"),
      SPIM_NUM_SLICES("SPIMNumSlices"),
      SPIM_NUM_REPEATS("SPIMNumRepeats"),
      SPIM_NUM_SCANSPERSLICE("SPIMNumScansPerSlice"),
      SPIM_LINESCAN_PERIOD("SingleAxisXPeriod(ms)"),
      SPIM_DELAY_SIDE("SPIMDelayBeforeSide(ms)"),
      SPIM_DELAY_SCAN("SPIMDelayBeforeScan(ms)"),
      SPIM_DELAY_LASER("SPIMDelayBeforeLaser(ms)"),
      SPIM_DURATION_LASER("SPIMLaserDuration(ms)"),
      SPIM_DELAY_CAMERA("SPIMDelayBeforeCamera(ms)"),
      SPIM_DURATION_CAMERA("SPIMCameraDuration(ms)"),
      SPIM_FIRSTSIDE("SPIMFirstSide"),
      SPIM_STATE("SPIMState"),
      SA_AMPLITUDE("SingleAxisAmplitude(um)"),
      SA_OFFSET("SingleAxisOffset(um)"),
      SA_AMPLITUDE_X_DEG("SingleAxisXAmplitude(deg)"),
      SA_OFFSET_X_DEG("SingleAxisXOffset(deg)"),
      SA_OFFSET_X("SingleAxisXOffset(um)"),
      SA_MODE_X("SingleAxisXMode"),
      SA_PATTERN_X("SingleAxisXPattern"),
      SA_AMPLITUDE_Y_DEG("SingleAxisYAmplitude(deg)"),
      SA_OFFSET_Y_DEG("SingleAxisYOffset(deg)"),
      SA_OFFSET_Y("SingleAxisYOffset(um)"),
      SCANNER_FILTER_X("FilterFreqX(kHz)"),
      SCANNER_FILTER_Y("FilterFreqY(kHz)"),
      AXIS_LETTER("AxisLetter"),
      SERIAL_ONLY_ON_CHANGE("OnlySendSerialCommandOnChange"),
      SERIAL_COMMAND("SerialCommand"),
      SERIAL_COM_PORT("SerialComPort"),
      MAX_DEFLECTION_X("MaxDeflectionX(deg)"),
      MIN_DEFLECTION_X("MinDeflectionX(deg)"),
      BEAM_ENABLED("BeamEnabled"),
      SAVE_CARD_SETTINGS("SaveCardSettings"),
      TRIGGER_SOURCE("TRIGGER SOURCE"), // for Hamamatsu
      TRIGGER_MODE("Triggermode"),      // for PCO
      TRIGGER_MODE_ANDOR("TriggerMode"),// for Andor sCMOS
      FIRMWARE_VERSION("FirmwareVersion"),
      CAMERA("Camera"),
      PLUGIN_POSITION_REFRESH_INTERVAL("PositionRefreshInterval(s)"),
      PLUGIN_NUM_ACQUISITIONS("NumberOfAcquisitions"),
      PLUGIN_ACQUISITION_INTERVAL("AcquisitionPeriod"),
      ;
      private final String text;
      Keys(String text) {
         this.text = text;
      }
      @Override
      public String toString() {
         return text;
      }
   }
   
   // values for properties
   public static enum Values {
      YES("Yes"),
      NO("No"),
      JS_NONE("0 - none"),
      JS_X("2 - joystick X"),
      JS_Y("3 - joystick Y"),
      JS_RIGHT_WHEEL("22 - right wheel"),
      JS_LEFT_WHEEL("23 - left wheel"),
      SPIM_ARMED("Armed"),
      SPIM_RUNNING("Running"),
      SPIM_IDLE("Idle"),
      SAM_DISABLED("0 - Disabled"),
      SAM_ENABLED("1 - Enabled"),
      SAM_TRIANGLE("1 - Triangle"),
      DO_IT("Do it"),
      DO_SSZ("Z - save settings to card (partial)"),
      INTERNAL("INTERNAL"),
      EXTERNAL("EXTERNAL"),
      INTERNAL_LC("Internal"),
      EXTERNAL_LC("External"),
      INTERNAL_ANDOR("Internal (Recommended for fast acquisitions)"),
      ;
      private final String text;
      Values(String text) {
         this.text = text;
      }
      @Override
      public String toString() {
         return text;
      }
   }
   
   // variables
   private Devices devices_;
   private CMMCore core_;
   private List<UpdateFromPropertyListenerInterface> listeners_;
   private HashMap<Keys, Float> pluginFloats_;
   private HashMap<Keys, Integer> pluginInts_;
   private HashMap<Keys, String> pluginStrings_;
   
   /**
    * Constructor.
    * @param devices
    * @author Jon
    */
   public Properties (Devices devices) {
      core_ = MMStudioMainFrame.getInstance().getCore();
      devices_ = devices;
      listeners_ = new ArrayList<UpdateFromPropertyListenerInterface>();
      
      pluginFloats_ = new HashMap<Keys, Float>();
      pluginInts_ = new HashMap<Keys, Integer>();
      pluginStrings_ = new HashMap<Keys, String>();
   }

   /**
    * sees if property exists in given device
    * @param device enum key for device 
    * @param name enum key for property 
    * @param ignoreError false (default) will do error checking, true means ignores non-existing property
    * @return
    */
   public boolean hasProperty(Devices.Keys device, Properties.Keys name, boolean ignoreError) {
      if (device == Devices.Keys.PLUGIN) {
         return pluginStrings_.containsKey(name) 
               || pluginFloats_.containsKey(name) 
               || pluginInts_.containsKey(name);
      } else {
         String mmDevice = null;
         if (ignoreError) {
            try {
               mmDevice = devices_.getMMDevice(device);
               return ((mmDevice!=null) &&  core_.hasProperty(mmDevice, name.toString()));
            } catch (Exception ex){
               // do nothing
            }
         } else {
            try {
               mmDevice = devices_.getMMDeviceException(device);
               return core_.hasProperty(mmDevice, name.toString());
            } catch (Exception ex) {
               ReportingUtils.showError(ex, "Couldn't find property " + 
                     name.toString() + " in device " + mmDevice);
            }
         }
         return false;
      }
   }
   
   /**
    * sees if property exists in given device, with error checking
    * @param device enum key for device 
    * @param name enum key for property 
    * @return
    */
   public boolean hasProperty(Devices.Keys device, Properties.Keys name) {
      return hasProperty(device, name, false);
   }
   
   /**
    * writes string property value to the device adapter using a core call
    * @param device enum key for device 
    * @param name enum key for property 
    * @param strVal value in string form, sent to core using setProperty()
    * @param ignoreError false (default) will do error checking, true means ignores non-existing property
    */
   public void setPropValue(Devices.Keys device, Properties.Keys name, String strVal, boolean ignoreError) {
      if (device == Devices.Keys.PLUGIN) {
         pluginStrings_.put(name, strVal);
      } else {
         String mmDevice = null;
         if (ignoreError) {
            mmDevice = devices_.getMMDevice(device);
            if (mmDevice != null) {
               try {
                  core_.setProperty(mmDevice, name.toString(), strVal);
               } catch (Exception ex) {
                  // log to file but nothing else
                  ReportingUtils.logMessage("Device " + mmDevice + 
                        " does not have property: " + name.toString());
               }
            }
         } else {
            try {
               mmDevice = devices_.getMMDeviceException(device);
               core_.setProperty(mmDevice, name.toString(), strVal);
            } catch (Exception ex) {
               ReportingUtils.showError(ex, "Error setting string property " + 
                    name.toString() + " to " + strVal + " in device " + mmDevice);
            }
         }
      }
   }
   
   /**
    * writes string property value to the device adapter using a core call
    * @param device enum key for device 
    * @param name enum key for property 
    * @param val value in Properties.Values enum form, sent to core using setProperty() after toString() call
    * @param ignoreError false (default) will do error checking, true means ignores non-existing property
    */
   public void setPropValue(Devices.Keys device, Properties.Keys name, Properties.Values val, boolean ignoreError) {
      if (device == Devices.Keys.PLUGIN) {
         pluginStrings_.put(name, val.toString());
      } else {
         String mmDevice = null;
         if (ignoreError) {
            mmDevice = devices_.getMMDevice(device);
            if (mmDevice != null) {
               try {
                  core_.setProperty(mmDevice, name.toString(), val.toString());
               } catch (Exception ex) {
                  // do nothing
               }
            }
         } else {
            try {
               mmDevice = devices_.getMMDeviceException(device);
               core_.setProperty(mmDevice, name.toString(), val.toString());
            } catch (Exception ex) {
               ReportingUtils.showError(ex, "Error setting string property " + 
                    name.toString() + " to " + val.toString() + " in device " + 
                    mmDevice);
            }
         }
      }
   }
   
   /**
    * writes string property value to the device adapter using a core call, with error checking
    * @param device enum key for device 
    * @param name enum key for property 
    * @param strVal value in string form, sent to core using setProperty()
    */
   public void setPropValue(Devices.Keys device, Properties.Keys name, String strVal) {
      setPropValue(device, name, strVal, false);
   }
   
   /**
    * writes string property value to the device adapter using a core call, with error checking
    * @param device enum key for device 
    * @param name enum key for property 
    * @param val value in Properties.Values enum form, sent to core using setProperty() after toString() call
    */
   public void setPropValue(Devices.Keys device, Properties.Keys name, Properties.Values val) {
      setPropValue(device, name, val.toString(), false);
   }
 
   /**
    * writes integer property value to the device adapter using a core call
    * @param device enum key for device 
    * @param name enum key for property 
    * @param intVal value in integer form, sent to core using setProperty()
    * @param ignoreError false (default) will do error checking, true means ignores non-existing property
    */
   public void setPropValue(Devices.Keys device, Properties.Keys name, int intVal, boolean ignoreError) {
      if (device == Devices.Keys.PLUGIN) {
         pluginInts_.put(name, (Integer)intVal);
      } else {
         String mmDevice = null;
         if (ignoreError) {
            mmDevice = devices_.getMMDevice(device);
            if (mmDevice != null) {
               try {
                  core_.setProperty(mmDevice, name.toString(), intVal);
               } catch (Exception ex) {
                  // do nothing
               }
            }
         } else {
            try {
               mmDevice = devices_.getMMDeviceException(device);
               core_.setProperty(mmDevice, name.toString(), intVal);
            } catch (Exception ex) {
               ReportingUtils.showError(ex, "Error setting int property " + 
                    name.toString() + " in device " + mmDevice);
            }
         }
      }
   }
   
   /**
    * writes integer property value to the device adapter using a core call, with error checking
    * @param device enum key for device 
    * @param name enum key for property 
    * @param intVal value in integer form, sent to core using setProperty()
    */
   public void setPropValue(Devices.Keys device, Properties.Keys name, int intVal) {
      setPropValue(device, name, intVal, false);
   }

   /**
    * writes float property value to the device adapter using a core call
    * @param device enum key for device 
    * @param name enum key for property 
    * @param intVal value in integer form, sent to core using setProperty()
    * @param ignoreError false (default) will do error checking, true means ignores non-existing property
    */
   public void setPropValue(Devices.Keys device, Properties.Keys name, float floatVal, boolean ignoreError) {
      if (device == Devices.Keys.PLUGIN) {
         pluginFloats_.put(name, (Float)floatVal);
      } else {
         String mmDevice = null;
         if (ignoreError) {
            mmDevice = devices_.getMMDevice(device);
            if (mmDevice != null) {
               try {
                  core_.setProperty(mmDevice, name.toString(), floatVal);
               } catch (Exception ex) {
                  // do nothing
               }
            }
         } else {
            try {
               mmDevice = devices_.getMMDeviceException(device);
               core_.setProperty(mmDevice, name.toString(), floatVal);
            } catch (Exception ex) {
               ReportingUtils.showError(ex, "Error setting float property " + 
                    name.toString() + " in device " + mmDevice);
            }
         }
      }
   }
   
   /**
    * writes float property value to the device adapter using a core call, with error checking
    * @param device enum key for device 
    * @param name enum key for property 
    * @param intVal value in integer form, sent to core using setProperty()
    */
   public void setPropValue(Devices.Keys device, Properties.Keys name, float floatVal) {
      setPropValue(device, name, floatVal, false);
   }

   /**
    * reads the property value from the device adapter using a core call, 
    * or empty string if it can't find property.
    * @param device enum key for device 
    * @param name enum key for property 
    * @return value in string form, returned from core call to getProperty()
    */
   private String getPropValue(Devices.Keys device, Properties.Keys name, boolean ignoreError) {
      String val = "";
      if (device == Devices.Keys.PLUGIN) {
         if (pluginStrings_.containsKey(name)) {
            val = pluginStrings_.get(name);
         }
      } else {
         String mmDevice = null;
         if (ignoreError) {
            mmDevice = devices_.getMMDevice(device);
            val = "";  // set to be empty string to avoid null pointer exceptions
            if (mmDevice != null) {
               try {
                  val = core_.getProperty(mmDevice, name.toString());
               } catch (Exception ex) {
                  // do nothing, just let empty string stay
               }
            }
         } else {
            try {
               mmDevice = devices_.getMMDeviceException(device);
               val = core_.getProperty(mmDevice, name.toString());
            } catch (Exception ex) {
               ReportingUtils.showError(ex, "Could not get property " + 
                       name.toString() + " from device " + mmDevice);
            }
         }
      }
      return val;
   }

   /**
    * returns a string value for the specified property (assumes the caller knows the property contains an string)
    * Ignores missing device or property, returning empty string.
    * @param device enum key for device 
    * @param name enum key for property 
    * @return
    * @throws ParseException
    */
   public String getPropValueString(Devices.Keys device, Properties.Keys name) {
      return getPropValue(device, name, true);
   }
   
   /**
    * returns a string value for the specified property (assumes the caller knows the property contains an string)
    * If device property isn't found, returns empty string.  If ignoreError then user is warned too.
    * @param device enum key for device 
    * @param name enum key for property 
    * @param ignoreError false (default) will do error checking, true means ignores non-existing device property
    * @return
    * @throws ParseException
    */
   public String getPropValueString(Devices.Keys device, Properties.Keys name, 
           boolean ignoreError) {
      return getPropValue(device, name, ignoreError);
   }
   
   /**
    * returns an integer value for the specified property (assumes the caller knows the property contains an integer).
    * Ignores missing device or property, returning 0.
    * @param device enum key for device 
    * @param name enum key for property 
    * @return
    * @throws ParseException
    */
   public int getPropValueInteger(Devices.Keys device, Properties.Keys name) {
      return getPropValueInteger(device, name, true);
   }
   

   /**
    * returns an integer value for the specified property (assumes the caller knows the property contains an integer).
    * If property isn't found, returns 0.  If ignoreError then user is warned too. 
    * @param device enum key for device 
    * @param name enum key for property 
    * @param ignoreError false (default) will do error checking, true means ignores non-existing property
    * @return
    * @throws ParseException
    */
   public int getPropValueInteger(Devices.Keys device, Properties.Keys name, boolean ignoreError) {
      int val = 0;
      if (device == Devices.Keys.PLUGIN) {
         if (pluginInts_.containsKey(name)) {
            val = pluginInts_.get(name).intValue();
         }
      } else {
         String strVal = null;
         try {
            strVal = getPropValue(device, name, ignoreError);
            if (!ignoreError || !strVal.equals("")) {
               val = NumberUtils.coreStringToInt(strVal);
            }
         } catch (ParseException ex) {
            ReportingUtils.showError(ex, "Could not parse int value of " + 
                    strVal + " for " + name.toString() + " in device " + 
                    device.toString());
         } catch (NullPointerException ex) {
            ReportingUtils.showError(ex, "Null Pointer error in function getPropValueInteger");
         }
      }
      return val;
   }

   /**
    * returns an float value for the specified property (assumes the caller knows the property contains a float).
    * Ignores missing device or property, returning 0.
    * @param device enum key for device 
    * @param name enum key for property 
    * @return
    */
   public float getPropValueFloat(Devices.Keys device, Properties.Keys name) {
      return getPropValueFloat(device, name, true);
   }
   
   /**
   * returns an float value for the specified property (assumes the caller knows the property contains a float).
   * If property isn't found, returns 0.  If ignoreError then user is warned too.
   * @param device enum key for device 
   * @param name enum key for property
   * @param ignoreError true to ignore error (usually unassigned device) 
   * @return
   */
  public float getPropValueFloat(Devices.Keys device, Properties.Keys name, boolean ignoreError) {
     float val = 0;
     if (device == Devices.Keys.PLUGIN) {
        if (pluginFloats_.containsKey(name)) {
           val = pluginFloats_.get(name).floatValue();
        }
     } else {
        String strVal = null;
        try {
           strVal = getPropValue(device, name, ignoreError);
           if (!ignoreError || !strVal.equals("")) {
              val = (float)NumberUtils.coreStringToDouble(strVal);
           }
        } catch (ParseException ex) {
           ReportingUtils.showError(ex, "Could not parse int value of " + 
                   strVal + " for " + name.toString() + " in device " + 
                   device.toString());
        } catch (NullPointerException ex) {
           ReportingUtils.showError(ex, "Null Pointer error in function getPropValueFLoat");
        }
     }
     return val;
  }
 
  
  /**
   * Used to add classes implementing DeviceListenerInterface as listeners
   */
  public void addListener(UpdateFromPropertyListenerInterface listener) {
     listeners_.add(listener);
  }

  /**
   * Remove classes implementing the DeviceListener interface from the listers
   *
   * @param listener
   */
  public void removeListener(UpdateFromPropertyListenerInterface listener) {
     listeners_.remove(listener);
  }
  
  /**
   * Call each listener in succession to alert them that something changed
   */
  public void callListeners() {
     for (UpdateFromPropertyListenerInterface listener : listeners_) {
        listener.updateFromProperty();
     }
  }
   

}
