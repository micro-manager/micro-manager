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
import java.util.List;

import mmcorej.CMMCore;

import org.micromanager.api.ScriptInterface;
import org.micromanager.asidispim.ASIdiSPIM;
import org.micromanager.asidispim.Utils.UpdateFromPropertyListenerInterface;
import org.micromanager.utils.NumberUtils;


/**
 * Contains data and methods related to getting and setting device properties.
 * Ideally this is the only place where MM properties are read and set.
 * One instance of this class exists in the top-level class.
 * 
 * Currently the property "reads" default to ignoring errors due to missing
 *  device or property, but property "writes" default to reporting errors
 *  due to missing device or property
 *  
 *  For the special case of the "PLUGIN" device which doesn't have properties
 *   we store the values using preferences.
 * 
 * @author Jon
 * @author nico
 */
public class Properties {
   
   private ScriptInterface gui_;
   private Devices devices_;
   private CMMCore core_;
   private List<UpdateFromPropertyListenerInterface> listeners_;
   private final Prefs prefs_;
   
   public static String PLUGIN_PREF_NODE = "Plugin";
   
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
      SPIM_NUMREPEATS("SPIMNumRepeats"),
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
      INPUT_MODE("InputMode"),
      PIEZO_MODE("PiezoMode"),
      TRIGGER_SOURCE("TRIGGER SOURCE"),   // for Hamamatsu
      TRIGGER_POLARITY("TriggerPolarity"),// for Hamamatsu
      TRIGGER_ACTIVE("TRIGGER ACTIVE"),   // for Hamamatsu
      READOUTTIME("ReadoutTime"),         // for Hamamatsu
      SENSOR_MODE("SENSOR MODE"),         // for Hamamatsu
      SCAN_MODE("ScanMode"),              // for Hamamatsu, 1 = slow scan, 2 = fast scan
      TRIGGER_MODE_PCO("Triggermode"),        // for PCO
      PIXEL_RATE("PixelRate"),                 // for PCO
      CAMERA_TYPE("CameraType"),               // for PCO
      TRIGGER_MODE("TriggerMode"),             // for Andor Zyla
      CAMERA_NAME("CameraName"),               // for Andor Zyla
      PIXEL_READOUT_RATE("PixelReadoutRate"),  // for Andor Zyla
      ANDOR_OVERLAP("Overlap"),                // for Andor Zyla
      FIRMWARE_VERSION("FirmwareVersion"),
      CAMERA("Camera"),
      PLUGIN_POSITION_REFRESH_INTERVAL("PositionRefreshInterval(s)"),
      PLUGIN_NUM_SIDES("NumberOfSides"),
      PLUGIN_FIRST_SIDE("FirstSide"),
      PLUGIN_NUM_SLICES("NumSlices"),
      PLUGIN_NUM_ACQUISITIONS("NumberOfAcquisitions"),
      PLUGIN_ACQUISITION_INTERVAL("AcquisitionPeriod"),
      PLUGIN_DIRECTORY_ROOT("DirectoryRoot"),
      PLUGIN_NAME_PREFIX("NamePrefix"),
      PLUGIN_SAVE_WHILE_ACQUIRING("SaveWhileAcquiring"),
      PLUGIN_HIDE_WHILE_ACQUIRING("HideWhileAcquiring"),
      PLUGIN_SEPARATE_VIEWERS_FOR_TIMEPOINTS("SeparateViewersForTimePoints"),
      PLUGIN_USE_NAVIGATION_JOYSTICKS("UseNavigationJoysticks"),
      PLUGIN_PIEZO_SHEET_INCREMENT("PiezoSheetIncrement"),  // piezo increment for moving piezo and galvo together
      PLUGIN_OFFSET_PIEZO_SHEET("OffsetPiezoSheet"),  // Offset in piezo/sheet relation
      PLUGIN_RATE_PIEZO_SHEET ("RatePiezoSheet"),     // Rate in piezo/sheet 
      PLUGIN_SHEET_START_POS ("SheetStartPosition"),  // Sheet start position for internal use
      PLUGIN_SHEET_END_POS ("SheetEndPosition"),      // Sheet end position for internal use
      PLUGIN_PIEZO_START_POS ("PiezoStartPosition"),  // Piezo start position for internal use
      PLUGIN_PIEZO_END_POS ("PiezoEndPosition"),      // Piezo end position for internal use
      PLUGIN_PIEZO_CENTER_POS ("PiezoCenterPosition"),  // Piezo center position for acquisition
      PLUGIN_EXPORT_MIPAV_DATA_DIR ("ExportMipavDataDirectory"), 
                                                      // Place data are saved in mipav format
      PLUGIN_EXPORT_MIPAV_TRANSFORM_OPTION("ExportMipavTransformOption"),
                                                      // Transform to be applied while saving in mipav format
      PLUGIN_ADVANCED_SLICE_TIMING("AdvancedSliceTiming"),
      PLUGIN_SLICE_STEP_SIZE("SliceStepSize"),
      PLUGIN_DESIRED_EXPOSURE("DesiredExposure"),
      PLUGIN_DESIRED_SLICE_PERIOD("DesiredSlicePeriod"),
      PLUGIN_MINIMIZE_SLICE_PERIOD("MinimizeSlicePeriod"),
      PLUGIN_ACQUSITION_MODE("AcquisitionMode"),
      PLUGIN_CAMERA_MODE("CameraMode"),
      PLUGIN_ENABLE_POSITION_UPDATES("EnablePositionUpdates"),
      PLUGIN_ENABLE_ILLUM_PIEZO_HOME("EnableIllumPiezoHome"),
      PLUGIN_SCAN_OPPOSITE_DIRECTIONS("ScanOppositeDirections"),
      ;
      private final String text;
      private final boolean hasPattern;  // true if string has substitution pattern
      Keys(String text) {
         this.text = text;
         this.hasPattern = false;
      }
      Keys(String text, boolean hasPattern) {
         this.text = text;
         this.hasPattern = hasPattern;
      }
      @Override
      public String toString() {
         return text;
      }
      public String toString(String substitute) {
         if (!hasPattern || substitute == null) {
            return toString();
         } else {
            return text.replace("<string>", substitute);
         }
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
      SAM_RAMP("0 - Ramp"),
      SAM_TRIANGLE("1 - Triangle"),
      DO_IT("Do it"),
      DO_SSZ("Z - save settings to card (partial)"),
      INTERNAL("INTERNAL"),
      EXTERNAL("EXTERNAL"),
      INTERNAL_LC("Internal"),
      EXTERNAL_LC("External"),
      LEVEL_PCO("External Exp. Ctrl."),
      INTERNAL_ANDOR("Internal (Recommended for fast acquisitions)"),
      LEVEL_ANDOR("External Exposure"),
      POSITIVE("POSITIVE"),
      NEGATIVE("NEGATIVE"),
      AREA("AREA"),                // for Hamamatsu's SENSOR MODE
      PROGRESSIVE("PROGRESSIVE"),  // for Hamamatsu's SENSOR MODE, the "lightsheet" mode
      SYNCREADOUT("SYNCREADOUT"),  // for Hamamatsu's TRIGGER ACTIVE
      LEVEL("LEVEL"),              // for Hamamatsu's TRIGGER ACTIVE
      EDGE("EDGE"),                // for Hamamatsu's TRIGGER ACTIVE
      ON("On"),
      OFF("Off"),
      INTERNAL_INPUT("internal input"),
      INTERNAL_CLOSEDLOOP_INPUT("0 - internal input closed-loop")
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
   
  
   
   /**
    * Constructor.
    * @param devices
    * @author Jon
    */
   public Properties (ScriptInterface gui, Devices devices, Prefs prefs) {
      gui_ = gui;
      core_ = gui_.getMMCore();
      devices_ = devices;
      prefs_ = prefs;
      listeners_ = new ArrayList<UpdateFromPropertyListenerInterface>();
   }
   
   /**
    * sees if property exists in given device
    * @param device enum key for device 
    * @param name enum key for property 
    * @param ignoreError false (default) will do error checking, true means ignores non-existing property
    * @param propNameSubstitute string to substitute for pattern in property name, or null if not used
    * @return
    */
   public boolean hasProperty(Devices.Keys device, Properties.Keys name,
         boolean ignoreError, String propNameSubstitute) {
      if (device == Devices.Keys.PLUGIN) {
         return prefs_.keyExists(PLUGIN_PREF_NODE, name);
      } else {
         String mmDevice = null;
         if (ignoreError) {
            try {
               mmDevice = devices_.getMMDevice(device);
               return ((mmDevice!=null) &&  core_.hasProperty(mmDevice, name.toString(propNameSubstitute)));
            } catch (Exception ex){
               // do nothing
            }
         } else {
            try {
               mmDevice = devices_.getMMDeviceException(device);
               return core_.hasProperty(mmDevice, name.toString(propNameSubstitute));
            } catch (Exception ex) {
               gui_.showError(ex, "Couldn't find property " + 
                     name.toString(propNameSubstitute) + " in device " + mmDevice,
                     ASIdiSPIM.getFrame());
            }
         }
      }
      return false;
   }
   
   /**
    * sees if property exists in given device, with error checking
    * @param device enum key for device 
    * @param name enum key for property 
    * @return
    */
   public boolean hasProperty(Devices.Keys device, Properties.Keys name) {
      return hasProperty(device, name, false, null);
   }
   
   /**
    * sees if property exists in given device
    * @param device enum key for device 
    * @param name enum key for property 
    * @param ignoreError false (default) will do error checking, true means ignores non-existing property
    * @return
    */
   public boolean hasProperty(Devices.Keys device, Properties.Keys name,
         boolean ignoreError) {
      return hasProperty(device, name, ignoreError, null);
   }
   
   /**
    * writes string property value to the device adapter using a core call
    * @param device enum key for device 
    * @param name enum key for property 
    * @param strVal value in string form, sent to core using setProperty()
    * @param ignoreError false (default) will do error checking, true means ignores non-existing property
    * @param propNameSubstitute string to substitute for pattern in property name, or null if not used
    */
   public void setPropValue(Devices.Keys device, Properties.Keys name, String strVal,
         boolean ignoreError, String propNameSubstitute) {
      if (device == Devices.Keys.PLUGIN) {
         prefs_.putString(PLUGIN_PREF_NODE, name, strVal);
      }
      else {
         String mmDevice = null;
         if (ignoreError) {
            mmDevice = devices_.getMMDevice(device);
            if (mmDevice != null) {
               try {
                  core_.setProperty(mmDevice, name.toString(propNameSubstitute), strVal);
               } catch (Exception ex) {
                  // log to file but nothing else
                  gui_.logMessage("Device " + mmDevice + 
                        " does not have property: " + name.toString(propNameSubstitute));
               }
            }
         } else {
            try {
               mmDevice = devices_.getMMDeviceException(device);
               core_.setProperty(mmDevice, name.toString(propNameSubstitute), strVal);
            } catch (Exception ex) {
               gui_.showError(ex, "Error setting string property " + 
                     name.toString(propNameSubstitute) + " to " + strVal + " in device "
                     + mmDevice, ASIdiSPIM.getFrame());
            }
         }
      }
   }
   
   /**
    * writes string property value to the device adapter using a core call
    * @param device enum key for device 
    * @param name enum key for property 
    * @param strVal value in string form, sent to core using setProperty()
    * @param ignoreError false (default) will do error checking, true means ignores non-existing property
    */
   public void setPropValue(Devices.Keys device, Properties.Keys name, String strVal,
         boolean ignoreError) {
      setPropValue(device, name, strVal, ignoreError, null);
   }
   
   /**
    * writes string property value to the device adapter using a core call, with error checking
    * @param device enum key for device 
    * @param name enum key for property 
    * @param strVal value in string form, sent to core using setProperty()
    */
   public void setPropValue(Devices.Keys device, Properties.Keys name, String strVal) {
      setPropValue(device, name, strVal, false, null);
   }
   
   /**
    * writes string property value to multiple device adapters using a core call, with error checking
    * @param devices array of enum keys for device 
    * @param name enum key for property 
    * @param strVal value in string form, sent to core using setProperty()
    */
   public void setPropValue(Devices.Keys [] devices, Properties.Keys name, String strVal) {
      for (Devices.Keys device : devices) {
         setPropValue(device, name, strVal, false, null);
      }
   }
   
   /**
    * writes string property value to the device adapter using a core call
    * @param device enum key for device 
    * @param name enum key for property 
    * @param val value in Properties.Values enum form, sent to core using setProperty() after toString() call
    * @param ignoreError false (default) will do error checking, true means ignores non-existing property
    * @param propNameSubstitute string to substitute for pattern in property name, or null if not used
    */
   public void setPropValue(Devices.Keys device, Properties.Keys name, Properties.Values val,
         boolean ignoreError, String propNameSubstitute) {
      if (device == Devices.Keys.PLUGIN) {
         prefs_.putString(PLUGIN_PREF_NODE, name, val.toString());
      }
      else {
         String mmDevice = null;
         if (ignoreError) {
            mmDevice = devices_.getMMDevice(device);
            if (mmDevice != null) {
               try {
                  core_.setProperty(mmDevice, name.toString(propNameSubstitute), val.toString());
               } catch (Exception ex) {
                  // do nothing
               }
            }
         } else {
            try {
               mmDevice = devices_.getMMDeviceException(device);
               core_.setProperty(mmDevice, name.toString(propNameSubstitute), val.toString());
            } catch (Exception ex) {
               gui_.showError(ex, "Error setting string property " + 
                     name.toString(propNameSubstitute) + " to " + val.toString() + " in device " + 
                     mmDevice, ASIdiSPIM.getFrame());
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
   public void setPropValue(Devices.Keys device, Properties.Keys name, Properties.Values val,
         boolean ignoreError) {
      setPropValue(device, name, val, ignoreError, null);
   }
   
   /**
    * writes string property value to the device adapter using a core call, with error checking
    * @param device enum key for device 
    * @param name enum key for property 
    * @param val value in Properties.Values enum form, sent to core using setProperty() after toString() call
    */
   public void setPropValue(Devices.Keys device, Properties.Keys name, Properties.Values val) {
      setPropValue(device, name, val.toString(), false, null);
   }
   
   /**
    * writes string property value to multiple device adapters using a core call, with error checking
    * @param devices array of enum key for device 
    * @param name enum key for property 
    * @param val value in Properties.Values enum form, sent to core using setProperty() after toString() call
    */
   public void setPropValue(Devices.Keys [] devices, Properties.Keys name, Properties.Values val) {
      for (Devices.Keys device : devices) {
         setPropValue(device, name, val.toString(), false, null);
      }
   }
 
   /**
    * writes integer property value to the device adapter using a core call
    * @param device enum key for device 
    * @param name enum key for property 
    * @param intVal value in integer form, sent to core using setProperty()
    * @param ignoreError false (default) will do error checking, true means ignores non-existing property
    * @param propNameSubstitute string to substitute for pattern in property name, or null if not used
    */
   public void setPropValue(Devices.Keys device, Properties.Keys name, int intVal,
         boolean ignoreError, String propNameSubstitute) {
      if (device == Devices.Keys.PLUGIN) {
         prefs_.putInt(PLUGIN_PREF_NODE, name, intVal);
      }
      else {
         String mmDevice = null;
         if (ignoreError) {
            mmDevice = devices_.getMMDevice(device);
            if (mmDevice != null) {
               try {
                  core_.setProperty(mmDevice, name.toString(propNameSubstitute), intVal);
               } catch (Exception ex) {
                  // do nothing
               }
            }
         } else {
            try {
               mmDevice = devices_.getMMDeviceException(device);
               core_.setProperty(mmDevice, name.toString(propNameSubstitute), intVal);
            } catch (Exception ex) {
               gui_.showError(ex, "Error setting int property " + 
                     name.toString(propNameSubstitute) + " in device " + mmDevice,
                     ASIdiSPIM.getFrame());
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
      setPropValue(device, name, intVal, false, null);
   }
   
   /**
    * writes integer property value to the device adapter using a core call
    * @param device enum key for device 
    * @param name enum key for property 
    * @param intVal value in integer form, sent to core using setProperty()
    * @param ignoreError false (default) will do error checking, true means ignores non-existing property
    */
   public void setPropValue(Devices.Keys device, Properties.Keys name, int intVal,
         boolean ignoreError) {
      setPropValue(device, name, intVal, ignoreError, null);
   }
   
   /**
    * writes integer property value to several device adapters using a core call, with error checking
    * @param devices array of enum key for device 
    * @param name enum key for property 
    * @param intVal value in integer form, sent to core using setProperty()
    */
   public void setPropValue(Devices.Keys [] devices, Properties.Keys name, int intVal) {
      for (Devices.Keys device : devices) {
         setPropValue(device, name, intVal, false, null);
      }
   }

   /**
    * writes float property value to the device adapter using a core call
    * @param device enum key for device 
    * @param name enum key for property 
    * @param intVal value in integer form, sent to core using setProperty()
    * @param ignoreError false (default) will do error checking, true means ignores non-existing property
    * @param propNameSubstitute string to substitute for pattern in property name, or null if not used
    */
   public void setPropValue(Devices.Keys device, Properties.Keys name, float floatVal,
         boolean ignoreError, String propNameSubstitute) {
      if (device == Devices.Keys.PLUGIN) {
         prefs_.putFloat(PLUGIN_PREF_NODE, name, floatVal);
      }
      else {
         String mmDevice = null;
         if (ignoreError) {
            mmDevice = devices_.getMMDevice(device);
            if (mmDevice != null) {
               try {
                  core_.setProperty(mmDevice, name.toString(propNameSubstitute), floatVal);
               } catch (Exception ex) {
                  // do nothing
               }
            }
         } else {
            try {
               mmDevice = devices_.getMMDeviceException(device);
               core_.setProperty(mmDevice, name.toString(propNameSubstitute), floatVal);
            } catch (Exception ex) {
               gui_.showError(ex, "Error setting float property " + 
                     name.toString(propNameSubstitute) + " in device " + mmDevice,
                     ASIdiSPIM.getFrame());
            }
         }
      }
   }
   
   public void setPropValue(Devices.Keys device, Properties.Keys name, float floatVal,
         boolean ignoreError) {
      setPropValue(device, name, floatVal, ignoreError, null);
   }
   
   /**
    * writes float property value to the device adapter using a core call, with error checking
    * @param device enum key for device 
    * @param name enum key for property 
    * @param intVal value in integer form, sent to core using setProperty()
    */
   public void setPropValue(Devices.Keys device, Properties.Keys name, float floatVal) {
      setPropValue(device, name, floatVal, false, null);
   }
   
   /**
    * writes float property value to several device adapters using a core call, with error checking
    * @param devices array of enum key for device 
    * @param name enum key for property 
    * @param intVal value in integer form, sent to core using setProperty()
    */
   public void setPropValue(Devices.Keys [] devices, Properties.Keys name, float floatVal) {
      for (Devices.Keys device : devices) {
         setPropValue(device, name, floatVal, false, null);
      }
   }

   /**
    * reads the property value from the device adapter using a core call, 
    * or empty string if it can't find property.
    * @param device enum key for device 
    * @param name enum key for property 
    * @param propNameSubstitute string to substitute for pattern in property name, or null if not used
    * @return value in string form, returned from core call to getProperty()
    */
   private String getPropValue(Devices.Keys device, Properties.Keys name,
         boolean ignoreError, String propNameSubstitute) {
      String val = "";
      if (device == Devices.Keys.PLUGIN) {
         if (!ignoreError & !prefs_.keyExists(PLUGIN_PREF_NODE, name)) {
            gui_.showError("Could not get property " + 
                  name.toString(propNameSubstitute) + " from special plugin \"device\"",
                  ASIdiSPIM.getFrame());
         }
         val = prefs_.getString(PLUGIN_PREF_NODE, name, "");
      } else {
         String mmDevice = null;
         if (ignoreError) {
            mmDevice = devices_.getMMDevice(device);
            val = "";  // set to be empty string to avoid null pointer exceptions
            if (mmDevice != null) {
               try {
                  val = core_.getProperty(mmDevice, name.toString(propNameSubstitute));
               } catch (Exception ex) {
                  // do nothing, just let empty string stay
               }
            }
         } else {
            try {
               mmDevice = devices_.getMMDeviceException(device);
               val = core_.getProperty(mmDevice, name.toString(propNameSubstitute));
            } catch (Exception ex) {
               gui_.showError(ex, "Could not get property " + 
                     name.toString(propNameSubstitute) + " from device " + mmDevice,
                     ASIdiSPIM.getFrame());
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
    */
   public String getPropValueString(Devices.Keys device, Properties.Keys name) {
      return getPropValue(device, name, true, null);
   }
   
   /**
    * returns a string value for the specified property (assumes the caller knows the property contains an string)
    * If device property isn't found, returns empty string.  If ignoreError then user is warned too.
    * @param device enum key for device 
    * @param name enum key for property 
    * @param ignoreError false (default) will do error checking, true means ignores non-existing device property
    * @return
    */
   public String getPropValueString(Devices.Keys device, Properties.Keys name, 
           boolean ignoreError) {
      return getPropValue(device, name, ignoreError, null);
   }
   
   /**
    * returns an integer value for the specified property (assumes the caller knows the property contains an integer).
    * Ignores missing device or property, returning 0.
    * @param device enum key for device 
    * @param name enum key for property 
    * @return
    */
   public int getPropValueInteger(Devices.Keys device, Properties.Keys name) {
      return getPropValueInteger(device, name, true, null);
   }
   
   /**
    * returns an integer value for the specified property (assumes the caller knows the property contains an integer).
    * If property isn't found, returns 0.  If ignoreError then user is warned too. 
    * @param device enum key for device 
    * @param name enum key for property 
    * @param ignoreError false (default) will do error checking, true means ignores non-existing property
    * @return
    */
   public int getPropValueInteger(Devices.Keys device, Properties.Keys name,
         boolean ignoreError) {
      return getPropValueInteger(device, name, ignoreError, null);
   }


   /**
    * returns an integer value for the specified property (assumes the caller knows the property contains an integer).
    * If property isn't found, returns 0.  If ignoreError then user is warned too. 
    * @param device enum key for device 
    * @param name enum key for property 
    * @param ignoreError false (default) will do error checking, true means ignores non-existing property
    * @param propNameSubstitute string to substitute for pattern in property name, or null if not used
    * @return
    */
   public int getPropValueInteger(Devices.Keys device, Properties.Keys name,
         boolean ignoreError, String propNameSubstitute) {
      int val = 0;
      if (device == Devices.Keys.PLUGIN) {
         if (!ignoreError & !prefs_.keyExists(PLUGIN_PREF_NODE, name)) {
            gui_.showError("Could not get property " + 
                  name.toString(propNameSubstitute) + " from special plugin \"device\"",
                  ASIdiSPIM.getFrame());
         }
         val = prefs_.getInt(PLUGIN_PREF_NODE, name, 0);
      }
      else {
         String strVal = null;
         try {
            strVal = getPropValue(device, name, ignoreError, propNameSubstitute);
            if (!ignoreError || !strVal.equals("")) {
               val = NumberUtils.coreStringToInt(strVal);
            }
         } catch (ParseException ex) {
            gui_.showError(ex, "Could not parse int value of " + 
                  strVal + " for " + name.toString(propNameSubstitute) + " in device " + 
                  device.toString(), ASIdiSPIM.getFrame());
         } catch (NullPointerException ex) {
            gui_.showError(ex, "Null Pointer error in function getPropValueInteger",
                  ASIdiSPIM.getFrame());
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
      return getPropValueFloat(device, name, true, null);
   }
   
   /**
    * returns an float value for the specified property (assumes the caller knows the property contains a float).
    * If property isn't found, returns 0.  If ignoreError then user is warned too.
    * @param device enum key for device 
    * @param name enum key for property
    * @param ignoreError true to ignore error (usually unassigned device) 
    * @return
    */
    public float getPropValueFloat(Devices.Keys device, Properties.Keys name,
          boolean ignoreError) {
       return getPropValueFloat(device, name, ignoreError, null);
    }
   
   /**
   * returns an float value for the specified property (assumes the caller knows the property contains a float).
   * If property isn't found, returns 0.  If ignoreError then user is warned too.
   * @param device enum key for device 
   * @param name enum key for property
   * @param ignoreError true to ignore error (usually unassigned device) 
   * @param propNameSubstitute string to substitute for pattern in property name, or null if not used
   * @return
   */
  public float getPropValueFloat(Devices.Keys device, Properties.Keys name,
        boolean ignoreError, String propNameSubstitute) {
     float val = 0;
     if (device == Devices.Keys.PLUGIN) {
        if (!ignoreError & !prefs_.keyExists(PLUGIN_PREF_NODE, name)) {
           gui_.showError("Could not get property " + 
                 name.toString(propNameSubstitute) + " from special plugin \"device\"",
                 ASIdiSPIM.getFrame());
        }
        val = prefs_.getFloat(PLUGIN_PREF_NODE, name, 0);
     }
     else {
        String strVal = null;
        try {
           strVal = getPropValue(device, name, ignoreError, propNameSubstitute);
           if (!ignoreError || !strVal.equals("")) {
              val = (float)NumberUtils.coreStringToDouble(strVal);
           }
        } catch (ParseException ex) {
           gui_.showError(ex, "Could not parse int value of " + 
                 strVal + " for " + name.toString(propNameSubstitute) + " in device " + 
                 device.toString(), ASIdiSPIM.getFrame());
        } catch (NullPointerException ex) {
           gui_.showError(ex, "Null Pointer error in function getPropValueFLoat",
                 ASIdiSPIM.getFrame());
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
