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
import org.micromanager.asidispim.Utils.MyDialogUtils;
import org.micromanager.asidispim.Utils.UpdateFromPropertyListenerInterface;
import org.micromanager.utils.NumberUtils;
import org.micromanager.utils.ReportingUtils;


/**
 * Contains data and methods related to getting and setting device properties.
 * Ideally this is the only place where MM properties are read and set.
 * One instance of this class exists in the top-level class.
 * 
 * Property "reads" ignore errors due to missing device or property (instead
 * they return empty string or zero); original functionality to catch those 
 * errors was unused and removed Jan 2015.
 * 
 * Property "writes" default to reporting errors due to missing device or property,
 * but can be called (and occasionally are) such as to ignore errors.
 *  
 *  For the special case of the "PLUGIN" device which doesn't have properties
 *   we store the values using preferences.
 * 
 * @author Jon
 * @author nico
 */
public class Properties {
   
   private final Devices devices_;
   private final CMMCore core_;
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
      SPIM_NUM_SLICES_PER_PIEZO("SPIMNumSlicesPerPiezo"),
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
      MOVE_TO_HOME("MoveToHome"),
      SET_HOME_HERE("SetHomeToCurrentPosition"),
      PLOGIC_MODE("PLogicMode"),
      PLOGIC_PRESET("SetCardPreset"),
      PLOGIC_TRIGGER_SOURCE("TriggerSource"),
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
      PREFS_SAVE_WHILE_ACQUIRING("SaveWhileAcquiring"),
      PREFS_HIDE_WHILE_ACQUIRING("HideWhileAcquiring"),
      PREFS_SEPARATE_VIEWERS_FOR_TIMEPOINTS("SeparateViewersForTimePoints"),
      PLUGIN_USE_NAVIGATION_JOYSTICKS("UseNavigationJoysticks"),
      PLUGIN_PIEZO_SHEET_INCREMENT("PiezoSheetIncrement"),  // piezo increment for moving piezo and galvo together
      PLUGIN_OFFSET_PIEZO_SHEET("OffsetPiezoSheet"),  // Offset in piezo/sheet relation
      PLUGIN_RATE_PIEZO_SHEET ("RatePiezoSheet"),     // Rate in piezo/sheet 
      PLUGIN_SHEET_START_POS ("SheetStartPosition"),  // Sheet start position for internal use
      PLUGIN_SHEET_END_POS ("SheetEndPosition"),      // Sheet end position for internal use
      PLUGIN_PIEZO_START_POS ("PiezoStartPosition"),  // Piezo start position for internal use
      PLUGIN_PIEZO_END_POS ("PiezoEndPosition"),      // Piezo end position for internal use
      PLUGIN_PIEZO_CENTER_POS ("PiezoCenterPosition"),  // Piezo center position for acquisition
      PLUGIN_EXPORT_DATA_DIR ("ExportDataDirectory"),  // Place data are saved in mipav/multiview format 
      PLUGIN_EXPORT_TRANSFORM_OPTION("ExportTransformOption"), // Transform to be applied when exporting data
      PLUGIN_EXPORT_FORMAT("ExportFormatOption"), // Output format of export pane
      PREFS_ADVANCED_SLICE_TIMING("AdvancedSliceTiming"),
      PLUGIN_SLICE_STEP_SIZE("SliceStepSize"),
      PLUGIN_DESIRED_EXPOSURE("DesiredExposure"),
      PLUGIN_DESIRED_SLICE_PERIOD("DesiredSlicePeriod"),
      PREFS_MINIMIZE_SLICE_PERIOD("MinimizeSlicePeriod"),
      PLUGIN_ACQUSITION_MODE("AcquisitionMode"),
      PLUGIN_CAMERA_MODE("CameraMode"),
      PREFS_ENABLE_POSITION_UPDATES("EnablePositionUpdates"),
      PREFS_ENABLE_ILLUM_PIEZO_HOME("EnableIllumPiezoHome"),
      PREFS_SCAN_OPPOSITE_DIRECTIONS("ScanOppositeDirections"),
      PREFS_IGNORE_MISSING_SCANNER("IgnoreMissingScanner"),
      PREFS_USE_MULTICHANNEL("UseMultiChannel"),
      PLUGIN_MULTICHANNEL_GROUP("ChannelGroup"),
      PLUGIN_MULTICHANNEL_MODE("MultiChannelMode"),
      PREFS_USE_MULTIPOSITION("MultiPositionMode"),
      PREFS_USE_TIMEPOINTS("UseTimePoints")
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
      DISPIM_SHUTTER("diSPIM Shutter"),
      PLOGIC_PRESET_1("1 - original SPIM TTL card"),
      PLOGIC_TRIGGER_MMIRROR("1 - Micro-mirror card"),
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
    * @param gui
    * @param devices
    * @author Jon
    * @param prefs
    */
   public Properties (ScriptInterface gui, Devices devices, Prefs prefs) {
      core_ = gui.getMMCore();
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
         String propNameSubstitute) {
      if (device == Devices.Keys.PLUGIN) {
         return prefs_.keyExists(PLUGIN_PREF_NODE, name);
      } else {
         String mmDevice = null;
         try {
            mmDevice = devices_.getMMDeviceException(device);
            return core_.hasProperty(mmDevice, name.toString(propNameSubstitute));
         } catch (Exception ex) {
            MyDialogUtils.showError(ex, "Couldn't find property " + 
                  name.toString(propNameSubstitute) + " in device " + mmDevice);
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
      return hasProperty(device, name, null);
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
                  ReportingUtils.logMessage("Device " + mmDevice + 
                        " does not have property: " + name.toString(propNameSubstitute));
               }
            }
         } else {
            try {
               mmDevice = devices_.getMMDeviceException(device);
               core_.setProperty(mmDevice, name.toString(propNameSubstitute), strVal);
            } catch (Exception ex) {
               MyDialogUtils.showError(ex, "Error setting string property " + 
                     name.toString(propNameSubstitute) + " to " + strVal + " in device "
                     + mmDevice);
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
               MyDialogUtils.showError(ex, "Error setting string property " + 
                     name.toString(propNameSubstitute) + " to " + val.toString() + " in device " + 
                     mmDevice);
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
               MyDialogUtils.showError(ex, "Error setting int property " + 
                     name.toString(propNameSubstitute) + " in device " + mmDevice);
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
    * @param floatVal value in float form, sent to core using setProperty()
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
               MyDialogUtils.showError(ex, "Error setting float property " + 
                     name.toString(propNameSubstitute) + " in device " + mmDevice);
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
    * @param floatVal value in float form, sent to core using setProperty()
    */
   public void setPropValue(Devices.Keys device, Properties.Keys name, float floatVal) {
      setPropValue(device, name, floatVal, false, null);
   }
   
   /**
    * writes float property value to several device adapters using a core call, with error checking
    * @param devices array of enum key for device 
    * @param name enum key for property 
    * @param floatVal value in float form, sent to core using setProperty()
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
         String propNameSubstitute) {
      String val = "";
      if (device == Devices.Keys.PLUGIN) {
         val = prefs_.getString(PLUGIN_PREF_NODE, name, "");
      } else {
         String mmDevice = null;
         mmDevice = devices_.getMMDevice(device);
         val = "";  // set to be empty string to avoid null pointer exceptions
         if (mmDevice != null) {
            try {
               val = core_.getProperty(mmDevice, name.toString(propNameSubstitute));
            } catch (Exception ex) {
               // do nothing, just let empty string stay
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
      return getPropValue(device, name, null);
   }
   
   /**
    * returns an integer value for the specified property (assumes the caller knows the property contains an integer).
    * Ignores missing device or property, returning 0.
    * @param device enum key for device 
    * @param name enum key for property 
    * @return
    */
   public int getPropValueInteger(Devices.Keys device, Properties.Keys name) {
      return getPropValueInteger(device, name, null);
   }
   
   /**
    * returns an integer value for the specified property (assumes the caller knows the property contains an integer).
    * If property isn't found, returns 0.
    * @param device enum key for device 
    * @param name enum key for property 
    * @param propNameSubstitute string to substitute for pattern in property name, or null if not used
    * @return
    */
   public int getPropValueInteger(Devices.Keys device, Properties.Keys name,
         String propNameSubstitute) {
      int val = 0;
      if (device == Devices.Keys.PLUGIN) {
         val = prefs_.getInt(PLUGIN_PREF_NODE, name, 0);
      }
      else {
         String strVal = null;
         try {
            strVal = getPropValue(device, name, propNameSubstitute);
            if (!strVal.equals("")) {
               val = NumberUtils.coreStringToInt(strVal);
            }
         } catch (ParseException ex) {
            MyDialogUtils.showError(ex, "Could not parse int value of " + 
                  strVal + " for " + name.toString(propNameSubstitute) + " in device " + 
                  device.toString());
         } catch (NullPointerException ex) {
            MyDialogUtils.showError(ex, "Null Pointer error in function getPropValueInteger");
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
      return getPropValueFloat(device, name, null);
   }
   
   /**
   * returns an float value for the specified property (assumes the caller knows the property contains a float).
   * If property isn't found, returns 0.
   * @param device enum key for device 
   * @param name enum key for property
   * @param propNameSubstitute string to substitute for pattern in property name, or null if not used
   * @return
   */
  public float getPropValueFloat(Devices.Keys device, Properties.Keys name,
        String propNameSubstitute) {
     float val = 0;
     if (device == Devices.Keys.PLUGIN) {
        val = prefs_.getFloat(PLUGIN_PREF_NODE, name, 0);
     }
     else {
        String strVal = null;
        try {
           strVal = getPropValue(device, name, propNameSubstitute);
           if (!strVal.equals("")) {
              val = (float)NumberUtils.coreStringToDouble(strVal);
           }
        } catch (ParseException ex) {
           MyDialogUtils.showError(ex, "Could not parse int value of " + 
                 strVal + " for " + name.toString(propNameSubstitute) + " in device " + 
                 device.toString());
        } catch (NullPointerException ex) {
           MyDialogUtils.showError(ex, "Null Pointer error in function getPropValueFLoat");
        }
     }
     return val;
  }
  
  /**
   * Used to add classes implementing DeviceListenerInterface as listeners
    * @param listener
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
