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
import org.micromanager.asidispim.Utils.MyNumberUtils;
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
      UPPER_LIMIT("UpperLim(mm)"),
      LOWER_LIMIT("LowerLim(mm)"),
      JOYSTICK_ENABLED("JoystickEnabled"),
      JOYSTICK_INPUT("JoystickInput", false),
      JOYSTICK_INPUT_X("JoystickInputX", false),
      JOYSTICK_INPUT_Y("JoystickInputY", false),
      SPIM_NUM_SIDES("SPIMNumSides"),
      SPIM_NUM_SLICES("SPIMNumSlices"),
      SPIM_NUM_REPEATS("SPIMNumRepeats"),
      SPIM_DELAY_REPEATS("SPIMDelayBeforeRepeat(ms)"),
      SPIM_NUM_SCANSPERSLICE("SPIMNumScansPerSlice"),
      SPIM_INTERLEAVE_SIDES("SPIMInterleaveSidesEnable"),
      SPIM_PIEZO_HOME_DISABLE("SPIMPiezoHomeDisable"),
      SPIM_ALTERTATE_DIRECTIONS("SPIMAlternateDirectionsEnable"),
      SPIM_NUM_SLICES_PER_PIEZO("SPIMNumSlicesPerPiezo"),
      SPIM_DELAY_SIDE("SPIMDelayBeforeSide(ms)"),
      SPIM_DELAY_SCAN("SPIMDelayBeforeScan(ms)"),
      SPIM_DELAY_LASER("SPIMDelayBeforeLaser(ms)"),
      SPIM_DURATION_SCAN("SPIMScanDuration(ms)"),
      SPIM_DURATION_LASER("SPIMLaserDuration(ms)"),
      SPIM_DELAY_CAMERA("SPIMDelayBeforeCamera(ms)"),
      SPIM_DURATION_CAMERA("SPIMCameraDuration(ms)"),
      SPIM_FIRSTSIDE("SPIMFirstSide"),
      SPIM_STATE("SPIMState"),
      SPIM_NUMREPEATS("SPIMNumRepeats"),
      SA_AMPLITUDE("SingleAxisAmplitude(um)", false),
      SA_OFFSET("SingleAxisOffset(um)", false),
      SA_AMPLITUDE_X_DEG("SingleAxisXAmplitude(deg)", false),
      SA_OFFSET_X_DEG("SingleAxisXOffset(deg)", false),
      SA_OFFSET_X("SingleAxisXOffset(um)", false),
      SA_MODE_X("SingleAxisXMode", false),
      SA_PATTERN_X("SingleAxisXPattern", false),
      SA_PERIOD_X("SingleAxisXPeriod(ms)", false),
      SA_AMPLITUDE_Y_DEG("SingleAxisYAmplitude(deg)", false),
      SA_OFFSET_Y_DEG("SingleAxisYOffset(deg)", false),
      SA_OFFSET_Y("SingleAxisYOffset(um)", false),
      SCANNER_FILTER_X("FilterFreqX(kHz)"),
      SCANNER_FILTER_Y("FilterFreqY(kHz)"),
      LASER_OUTPUT_MODE("LaserOutputMode"),
      AXIS_LETTER("AxisLetter"),
      SERIAL_ONLY_ON_CHANGE("OnlySendSerialCommandOnChange"),
      SERIAL_COMMAND("SerialCommand"),
      SERIAL_COM_PORT("SerialComPort"),
      MAX_DEFLECTION_X("MaxDeflectionX(deg)"),
      MIN_DEFLECTION_X("MinDeflectionX(deg)"),
      BEAM_ENABLED("BeamEnabled", false),
      SAVE_CARD_SETTINGS("SaveCardSettings"),
      INPUT_MODE("InputMode"),
      PIEZO_MODE("PiezoMode"),
      SET_HOME_HERE("SetHomeToCurrentPosition"),
      HOME_POSITION("HomePosition(mm)"),
      AUTO_SLEEP_DELAY("AutoSleepDelay(min)"),
      PLOGIC_MODE("PLogicMode"),
      PLOGIC_PRESET("SetCardPreset"),
      PLOGIC_TRIGGER_SOURCE("TriggerSource", false),
      PLOGIC_POINTER_POSITION("PointerPosition"),
      PLOGIC_EDIT_CELL_TYPE("EditCellCellType"),
      PLOGIC_EDIT_CELL_CONFIG("EditCellConfig"),
      PLOGIC_EDIT_CELL_INPUT_1("EditCellInput1"),
      PLOGIC_EDIT_CELL_INPUT_2("EditCellInput2"),
      PLOGIC_EDIT_CELL_INPUT_3("EditCellInput3"),
      PLOGIC_EDIT_CELL_INPUT_4("EditCellInput4"),
      PLOGIC_EDIT_CELL_UPDATES("EditCellUpdateAutomatically"),
      PLOGIC_OUTPUT_CHANNEL("OutputChannel", false),
      STAGESCAN_NUMLINES("ScanNumLines", false),
      STAGESCAN_STATE("ScanState"),
      STAGESCAN_PATTERN("ScanPattern", false),
      STAGESCAN_FAST_START("ScanFastAxisStartPosition(mm)", false),
      STAGESCAN_FAST_STOP("ScanFastAxisStopPosition(mm)", false),
      STAGESCAN_SLOW_START("ScanSlowAxisStartPosition(mm)", false),
      STAGESCAN_SLOW_STOP("ScanSlowAxisStopPosition(mm)", false),
      STAGESCAN_SETTLING_TIME("ScanSettlingTime(ms)", false),
      STAGESCAN_MOTOR_SPEED("MotorSpeedX-S(mm/s)", false),
      STAGESCAN_MAX_MOTOR_SPEED("MotorSpeedMaximumX(mm/s)", false),
      STAGESCAN_MOTOR_ACCEL("AccelerationX-AC(ms)", false),
      STAGESCAN_OVERSHOOT_DIST("ScanOvershootDistance(um)", false),
      BINNING("Binning"),
      TRIGGER_SOURCE("TRIGGER SOURCE"),   // for Hamamatsu
      TRIGGER_POLARITY("TriggerPolarity"),// for Hamamatsu
      TRIGGER_ACTIVE("TRIGGER ACTIVE"),   // for Hamamatsu
      READOUTTIME("ReadoutTime"),         // for Hamamatsu
      SENSOR_MODE("SENSOR MODE"),         // for Hamamatsu
      SCAN_MODE("ScanMode"),              // for Hamamatsu, 1 = slow scan, 2 = fast scan
      CAMERA_BUS("Camera Bus"),           // for Hamamatsu interface type, USB3 or ??
      TRIGGER_MODE_PCO("Triggermode"),         // for PCO
      PIXEL_RATE("PixelRate"),                 // for PCO
      CAMERA_TYPE("CameraType"),               // for PCO
      LINE_TIME("Line Time [us]"),             // for PCO
      TRIGGER_MODE("TriggerMode"),             // for Andor Zyla, PVCAM
      CAMERA_NAME("CameraName"),               // for Andor Zyla
      PIXEL_READOUT_RATE("PixelReadoutRate"),  // for Andor Zyla
      ANDOR_OVERLAP("Overlap"),                // for Andor Zyla
      SENSOR_READOUT_MODE("LightScanPlus-SensorReadoutMode"), // for Andor Zyla
      PIXEL_TYPE("PixelType"),            // for DemoCam
      CAMERA_SIZE_X("OnCameraCCDXSize"),  // for DemoCam
      CAMERA_SIZE_Y("OnCameraCCDYSize"),  // for DemoCam
      CAMERA_X_DIMENSION("X-dimension"),  // for PVCAM
      CAMERA_Y_DIMENSION("Y-dimension"),  // for PVCAM
      PVCAM_CLEARING_MODE("ClearMode"),       // for PVCAM
      PVCAM_CLEARING_TIME("Timing-ClearingTimeNs"),  // for PVCAM
      PVCAM_EXPOSURE_TIME("Timing-ExposureTimeNs"),  // for PVCAM
      PVCAM_READOUT_TIME("Timing-ReadoutTimeNs"),    // for PVCAM
      PVCAM_POST_TIME("Timing-PostTriggerDelayNs"),  // for PVCAM
      PVCAM_PRE_TIME("Timing-PreTriggerDelayNs"),    // for PVCAM
      PVCAM_CHIPNAME("ChipName"),                    // for PVCAM
      FIRMWARE_VERSION("FirmwareVersion"),
      CAMERA("Camera"),
      CORE_TIMEOUT_MS("TimeoutMs"),
      PLUGIN_POSITION_REFRESH_INTERVAL("PositionRefreshInterval(s)"),
      PLUGIN_NUM_SIDES("NumberOfSides"),
      PLUGIN_FIRST_SIDE("FirstSide"),
      PLUGIN_NUM_SLICES("NumSlices"),
      PLUGIN_DELAY_BEFORE_SIDE("DelayBeforeSide"),
      PLUGIN_NUM_ACQUISITIONS("NumberOfAcquisitions"),
      PLUGIN_ACQUISITION_INTERVAL("AcquisitionPeriod"),
      PLUGIN_DIRECTORY_ROOT("DirectoryRoot"),
      PLUGIN_NAME_PREFIX("NamePrefix"),
      PLUGIN_TESTACQ_PATH("TestAcquisitionPath"),
      PLUGIN_TESTACQ_SAVE("TestAcquisitionSave"),
      PLUGIN_WRITE_ACQ_SETTINGS_FILE("WriteAcquisitionSettingsFile"),
      PLUGIN_ACQUIRE_BOTH_CAMERAS_SIMULT("AcquireBothCamerasSimult"),
      PLUGIN_USE_TOOLSET("UseImageJToolset"),
      PREFS_SAVE_WHILE_ACQUIRING("SaveWhileAcquiring"),
      PREFS_HIDE_WHILE_ACQUIRING("HideWhileAcquiring"),
      PREFS_SEPARATE_VIEWERS_FOR_TIMEPOINTS("SeparateViewersForTimePoints"),
      PREFS_USE_X_GRID("UseXGrid"),
      PREFS_USE_Y_GRID("UseYGrid"),
      PREFS_USE_Z_GRID("UseZGrid"),
      PREFS_CLEAR_YZ_GRID("ClearYZGrid"),
      PLUGIN_USE_NAVIGATION_JOYSTICKS("UseNavigationJoysticks"),
      PLUGIN_PIEZO_SHEET_INCREMENT("PiezoSheetIncrement"),  // piezo increment for moving piezo and galvo together
      PLUGIN_OFFSET_PIEZO_SHEET("OffsetPiezoSheet"),  // Offset in piezo/sheet calibration
      PLUGIN_RATE_PIEZO_SHEET ("RatePiezoSheet"),     // Rate in piezo/sheet calibration, really should be named "slice"
      PLUGIN_SHEET_START_POS ("SheetStartPosition"),  // Sheet start position for internal use
      PLUGIN_SHEET_END_POS ("SheetEndPosition"),      // Sheet end position for internal use
      PLUGIN_PIEZO_START_POS ("PiezoStartPosition"),  // Piezo start position for internal use
      PLUGIN_PIEZO_END_POS ("PiezoEndPosition"),      // Piezo end position for internal use
      PLUGIN_PIEZO_CENTER_POS ("PiezoCenterPosition"), // Piezo center position for acquisition
      PLUGIN_SLOPE_SHEET_WIDTH ("SlopeSheetWidth"),    // Rate in sheet generating axis in degrees/1000px
      PLUGIN_EXPORT_DATA_DIR ("ExportDataDirectory"),  // Place data are saved in mipav/multiview format 
      PLUGIN_EXPORT_TRANSFORM_OPTION("ExportTransformOption"), // Transform to be applied when exporting data
      PLUGIN_EXPORT_FORMAT("ExportFormatOption"), // Output format of export pane
      PLUGIN_POSITION_DELAY("PositionDelay"),
      PREFS_ADVANCED_SLICE_TIMING("AdvancedSliceTiming"),
      PLUGIN_SLICE_STEP_SIZE("SliceStepSize"),
      PLUGIN_DESIRED_EXPOSURE("DesiredExposure"),
      PLUGIN_DESIRED_SLICE_PERIOD("DesiredSlicePeriod"),
      PREFS_MINIMIZE_SLICE_PERIOD("MinimizeSlicePeriod"),
      PLUGIN_ACQUSITION_MODE("AcquisitionMode"),
      AUTOFOCUS_ACQUSITION_MODE("AutofocusAcquisitionMode"),
      AUTOFOCUS_SCORING_ALGORITHM("AutofocusScoringAlgorithm"),
      PLUGIN_ACQUSITION_USE_AUTOFOCUS("UseAutofocusInAcquisition"),
      PLUGIN_ACQUSITION_USE_MOVEMENT_CORRECTION("UseMovementCorrectionInAcquisition"),
      PLUGIN_CAMERA_MODE("CameraMode"),
      PLUGIN_CAMERA_LIVE_EXPOSURE_FIRST("CameraLiveExposureMs_First"),  // used internally to save/restore live exposure time
      PLUGIN_CAMERA_LIVE_EXPOSURE_SECOND("CameraLiveExposureMs_Second"),  // used internally to save/restore live exposure time
      PLUGIN_SHEET_WIDTH_A("SheetWidthOrig_A"),  // used internally to save/restore sheet width when acquisition changes it
      PLUGIN_SHEET_WIDTH_B("SheetWidthOrig_B"),  // used internally to save/restore sheet width when acquisition changes it
      PLUGIN_SHEET_OFFSET_A("SheetOffsetOrig_A"),  // used internally to save/restore sheet offset when acquisition changes it
      PLUGIN_SHEET_OFFSET_B("SheetOffsetOrig_B"),  // used internally to save/restore sheet offset when acquisition changes it
      PLUGIN_CAMERA_LIVE_SCAN("CameraLiveScanMs"),
      PREFS_ENABLE_POSITION_UPDATES("EnablePositionUpdates"),
      PREFS_AUTO_SHEET_WIDTH("AutomaticSheetWidth"),
      PREFS_ENABLE_ILLUM_PIEZO_HOME("EnableIllumPiezoHome"),
      PREFS_SCAN_OPPOSITE_DIRECTIONS("ScanOppositeDirections"),
      PREFS_IGNORE_MISSING_SCANNER("IgnoreMissingScanner"),
      PREFS_USE_MULTICHANNEL("UseMultiChannel"),
      PLUGIN_MULTICHANNEL_GROUP("ChannelGroup"),
      PLUGIN_MULTICHANNEL_MODE("MultiChannelMode"),
      PREFS_USE_MULTIPOSITION("MultiPositionMode"),
      PREFS_USE_TIMEPOINTS("UseTimePoints"),
      PLUGIN_AUTOFOCUS_SHOWIMAGES("AutofocusShowImages"),
      PLUGIN_AUTOFOCUS_SHOWPLOT("AutofocusShowPlot"),
      PLUGIN_AUTOFOCUS_NRIMAGES("AutofocusNrImages"),
      PLUGIN_AUTOFOCUS_STEPSIZE("AutofocusStepSize"),
      PLUGIN_AUTOFOCUS_WINDOWPOSX("AutofocusWindowPosx"),
      PLUGIN_AUTOFOCUS_WINDOWPOSY("AutofocusWindowPosy"),
      PLUGIN_AUTOFOCUS_WINDOW_WIDTH("AutofocusWindowWidth"),
      PLUGIN_AUTOFOCUS_WINDOW_HEIGHT("AutofocusWIndowHeight"),
      PLUGIN_AUTOFOCUS_ACQBEFORESTART("AutofocusDoBeforeACQStart"),
      PLUGIN_AUTOFOCUS_EACHNIMAGES("AutofocusEachNTimePoints"),
      PLUGIN_AUTOFOCUS_MAXOFFSETCHANGE("AutofocusMaxOffsetChange"),  // during acquisition
      PLUGIN_AUTOFOCUS_MAXOFFSETCHANGE_SETUP("AutofocusMaxOffsetChangeSetup"),
      PLUGIN_AUTOFOCUS_AUTOUPDATE_OFFSET("AutofocusAutoUpdateOffset"),
      PLUGIN_AUTOFOCUS_CHANNEL("AutofocusChannel"),
      PLUGIN_AUTOFOCUS_MINIMUMR2("AutofocusMinimumR2"),
      PLUGIN_AUTOFOCUS_CORRECT_MOVEMENT("AutofocusCorrectMovement"),
      PLUGIN_AUTOFOCUS_CORRECTMOVEMENT_EACHNIMAGES("AutofocusCorrectMovementEachNImages"),
      PLUGIN_AUTOFOCUS_CORRECTMOVEMENT_CHANNEL("AutofocusCorrectMovementChannel"),
      PLUGIN_AUTOFOCUS_CORRECTMOVEMENT_MAXCHANGE("AutofcousCorrectMovementMaxChange"),
      PLUGIN_AUTOFOCUS_CORRECTMOVEMENT_MINCHANGE("AutofcousCorrectMovementMinChange"),
      PLUGIN_ADVANCED_CAMERA_EXPOSURE("AdvancedCameraExposure"),
      PLUGIN_DESKEW_FACTOR("DeskewFactor"),
      PLUGIN_DESKEW_INVERT("DeskewInvert"),
      PLUGIN_DESKEW_INTERPOLATE("DeskewInterpolate"),
      PLUGIN_DESKEW_AUTO_TEST("DeskewAutoTest"),
      PLUGIN_STAGESCAN_ACCEL_FACTOR("StageScanAccelerationFactor"),
      PLUGIN_LS_SCAN_RESET("LightSheetScanReset"),
      PLUGIN_LS_SCAN_SETTLE("LightSheetScanSettle"),
      PLUGIN_LS_SHUTTER_WIDTH("LightSheetShutterWidth"),
//      PLUGIN_LS_SHUTTER_SPEED("LightSheetSpeedFactor"),
      PLUGIN_SHEET_WIDTH_EDGE_A("SheetWidthEdgeSideA"),  // hack to have separate properties for different panels when used as a property and is exposing flaws in the bipartate property/pref scheme, maybe would make sense to just use preferences directly but rest of infrastructure isn't set up for that at the moment
      PLUGIN_SHEET_WIDTH_EDGE_B("SheetWidthEdgeSideB"),
      PLUGIN_SHEET_OFFSET_EDGE_A("SheetOffsetEdgeSideA"),
      PLUGIN_SHEET_OFFSET_EDGE_B("SheetOffsetEdgeSideB"),
      PLUGIN_LIGHTSHEET_SLOPE("LightSheetSlope"),
      PLUGIN_LIGHTSHEET_OFFSET("LightSheetOffset"),
      PLUGIN_GRID_OVERLAP_PERCENT("GridOverlapPercent"),
      ;
      private final String text;
      private final boolean forceSet;
      Keys(String text) {
         this.text = text;
         this.forceSet = true;
      }
      Keys(String text, boolean forceSet) {
         this.text = text;
         this.forceSet = forceSet;
      }
      @Override
      public String toString() {
         return text;
      }
      public boolean doForceSet() {
         return forceSet;
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
      SPIM_RUNNING("Running"),  // also used for stage scan
      SPIM_IDLE("Idle"),        // also used for stage scan
      SAM_DISABLED("0 - Disabled"),
      SAM_ENABLED("1 - Enabled"),
      SAM_RAMP("0 - Ramp"),
      SAM_TRIANGLE("1 - Triangle"),
      LASER_SHUTTER_SIDE("shutter + side"),
      DO_IT("Do it"),
      DO_SSZ("Z - save settings to card (partial)"),
      DISPIM_SHUTTER("diSPIM Shutter"),
      PLOGIC_TRIGGER_MMIRROR("1 - Micro-mirror card"),
      PLOGIC_PRESET_2("2 - cell 1 low"),
      PLOGIC_PRESET_3("3 - cell 1 high"),
      PLOGIC_PRESET_14("14 - diSPIM TTL"),
      PLOGIC_PRESET_COUNT_1("22 - no counter"),
      PLOGIC_PRESET_COUNT_2("21 - mod2 counter"),
      PLOGIC_PRESET_COUNT_3("16 - mod3 counter"),
      PLOGIC_PRESET_COUNT_4("15 - mod4 counter"),
      PLOGIC_PRESET_CLOCK_LASER("17 - counter clock = falling TTL1"),
      PLOGIC_PRESET_CLOCK_SIDE_AFIRST("18 - counter clock = falling TTL3"),
      PLOGIC_PRESET_CLOCK_SIDE_BFIRST("26 - counter clock = rising TTL3"),
      PLOGIC_PRESET_BNC5_8_ON_13_16("20 - cells 13-16 on BNC5-8"),
      PLOGIC_CHANNEL_BNC5("output 5 only"),
      PLOGIC_CHANNEL_BNC6("output 6 only"),
      PLOGIC_CHANNEL_BNC7("output 7 only"),
      PLOGIC_CHANNEL_BNC8("output 8 only"),
      PLOGIC_AND4("10 - 4-input AND"),
      RASTER("Raster"),
      SERPENTINE("Serpentine"),
      INTERNAL("INTERNAL"),
      EXTERNAL("EXTERNAL"),
      INTERNAL_LC("Internal"),
      EXTERNAL_LC("External"),
      LEVEL_PCO("External Exp. Ctrl."),
      INTERNAL_ANDOR("Internal (Recommended for fast acquisitions)"),
      LEVEL_ANDOR("External Exposure"),
      CENTER_OUT_ANDOR("Centre Out Simultaneous"),
      BOTTOM_UP_ANDOR("Bottom Up Sequential"),
      BOTTOM_UP_SIM_ANDOR("Bottom Up Simultaneous"),
      INTERNAL_TRIGGER("Internal Trigger"),  // for PVCam
      EDGE_TRIGGER("Edge Trigger"),          // for PVCam
      NEVER("Never"),                        // for PVCam
      PRE_EXPOSURE("Pre-Exposure"),          // for PVCam
      PRE_SEQUENCE("Pre-Sequence"),          // for PVCam
      PRIME_CHIPNAME("CIS2020F"),            // for PVCam, original Prime
      PRIME_95B_CHIPNAME("GS144BSI"),        // for PVCam, Prime 95B
      POSITIVE("POSITIVE"),
      NEGATIVE("NEGATIVE"),
      SIXTEENBIT("16bit"),
      USB3("USB3"),
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
    * @return
    */
   public boolean hasProperty(Devices.Keys device, Properties.Keys name) {
      if (device == Devices.Keys.PLUGIN) {
         return prefs_.keyExists(PLUGIN_PREF_NODE, name);
      } else {
         String mmDevice = null;
         try {
            mmDevice = devices_.getMMDeviceException(device);
            return core_.hasProperty(mmDevice, name.toString());
         } catch (Exception ex) {
            MyDialogUtils.showError(ex, "Couldn't find property " + 
                  name.toString() + " in device " + mmDevice);
         }
      }
      return false;
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
      if (device == Devices.Keys.PLUGIN) {
         prefs_.putString(PLUGIN_PREF_NODE, name, strVal);
      }
      else {
         String mmDevice = null;
         try {
            mmDevice = devices_.getMMDeviceException(device);
            if (name.doForceSet() 
                  || !core_.getProperty(mmDevice, name.toString()).equals(strVal)) {
               core_.setProperty(mmDevice, name.toString(), strVal);
            }
         } catch (Exception ex) {
            if (ignoreError) {
               if (mmDevice != null) {
                  // log to file but nothing else
                  ReportingUtils.logMessage("Device " + mmDevice + 
                        " does not have property: " + name.toString());
               }
               // do nothing if ignoreError set and we didn't find the device at all
            }
            else {
               MyDialogUtils.showError(ex, "Error setting string property " + 
                     name.toString() + " to " + strVal + " in device "
                     + mmDevice);
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
    * writes string property value to multiple device adapters using a core call, with error checking
    * @param devices array of enum keys for device 
    * @param name enum key for property 
    * @param strVal value in string form, sent to core using setProperty()
    */
   public void setPropValue(Devices.Keys [] devices, Properties.Keys name, String strVal) {
      for (Devices.Keys device : devices) {
         setPropValue(device, name, strVal, false);
      }
   }
   
   /**
    * writes string property value to multiple device adapters using a core call, with error checking
    * @param devices array of enum keys for device 
    * @param name enum key for property 
    * @param strVal value in string form, sent to core using setProperty()
    * @param ignoreError false (default) will do error checking, true means ignores non-existing property
    */
   public void setPropValue(Devices.Keys [] devices, Properties.Keys name, String strVal, boolean ignoreError) {
      for (Devices.Keys device : devices) {
         setPropValue(device, name, strVal, ignoreError);
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
      setPropValue(device, name, val.toString(), ignoreError);
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
    * writes string property value to multiple device adapters using a core call, with error checking
    * @param devices array of enum key for device 
    * @param name enum key for property 
    * @param val value in Properties.Values enum form, sent to core using setProperty() after toString() call
    */
   public void setPropValue(Devices.Keys [] devices, Properties.Keys name, Properties.Values val) {
      for (Devices.Keys device : devices) {
         setPropValue(device, name, val.toString(), false);
      }
   }
   
   /**
    * writes string property value to multiple device adapters using a core call, with error checking
    * @param devices array of enum key for device 
    * @param name enum key for property 
    * @param val value in Properties.Values enum form, sent to core using setProperty() after toString() call
    * @param ignoreError false (default) will do error checking, true means ignores non-existing property
    */
   public void setPropValue(Devices.Keys [] devices, Properties.Keys name, Properties.Values val, boolean ignoreError) {
      for (Devices.Keys device : devices) {
         setPropValue(device, name, val.toString(), ignoreError);
      }
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
      if (device == Devices.Keys.PLUGIN) {
         prefs_.putInt(PLUGIN_PREF_NODE, name, intVal);
      }
      else {
         String mmDevice = null;
         try {
            mmDevice = devices_.getMMDeviceException(device);
            if (name.doForceSet()
                  || intVal != NumberUtils.coreStringToInt(
                        core_.getProperty(mmDevice, name.toString()))) {
               core_.setProperty(mmDevice, name.toString(), intVal);
            }
         } catch (Exception ex) {
            if (ignoreError) {
               if (mmDevice != null) {
                  // log to file but nothing else
                  ReportingUtils.logMessage("Device " + mmDevice + 
                        " does not have property: " + name.toString());
               }
               // do nothing if ignoreError set and we didn't find the device at all
            } else {
               MyDialogUtils.showError(ex, "Error setting int property " + 
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
    * writes integer property value to several device adapters using a core call, with error checking
    * @param devices array of enum key for device 
    * @param name enum key for property 
    * @param intVal value in integer form, sent to core using setProperty()
    */
   public void setPropValue(Devices.Keys [] devices, Properties.Keys name, int intVal) {
      for (Devices.Keys device : devices) {
         setPropValue(device, name, intVal, false);
      }
   }
   
   /**
    * writes integer property value to several device adapters using a core call, with error checking
    * @param devices array of enum key for device 
    * @param name enum key for property 
    * @param intVal value in integer form, sent to core using setProperty()
    * @param ignoreError false (default) will do error checking, true means ignores non-existing property
    */
   public void setPropValue(Devices.Keys [] devices, Properties.Keys name, int intVal, boolean ignoreError) {
      for (Devices.Keys device : devices) {
         setPropValue(device, name, intVal, ignoreError);
      }
   }

   /**
    * writes float property value to the device adapter using a core call
    * @param device enum key for device 
    * @param name enum key for property 
    * @param floatVal value in float form, sent to core using setProperty()
    * @param ignoreError false (default) will do error checking, true means ignores non-existing property
    */
   public void setPropValue(Devices.Keys device, Properties.Keys name, float floatVal,
         boolean ignoreError) {
      if (device == Devices.Keys.PLUGIN) {
         prefs_.putFloat(PLUGIN_PREF_NODE, name, floatVal);
      }
      else {
         String mmDevice = null;
         try {
            mmDevice = devices_.getMMDeviceException(device);
            if (name.forceSet
                  || !MyNumberUtils.floatsEqual(floatVal, (float)NumberUtils.coreStringToDouble(
                        core_.getProperty(mmDevice, name.toString())))) {
               core_.setProperty(mmDevice, name.toString(), floatVal);
            }
         } catch (Exception ex) {
            if (ignoreError) {
               if (mmDevice != null) {
                  // log to file but nothing else
                  ReportingUtils.logMessage("Device " + mmDevice + 
                        " does not have property: " + name.toString());
               }
               // do nothing if ignoreError set and we didn't find the device at all
            } else {
               MyDialogUtils.showError(ex, "Error setting float property " + 
                     name.toString() + " in device " + mmDevice);
            }
         }
      }
   }
         
   /**
    * writes float property value to the device adapter using a core call, with error checking
    * @param device enum key for device 
    * @param name enum key for property 
    * @param floatVal value in float form, sent to core using setProperty()
    */
   public void setPropValue(Devices.Keys device, Properties.Keys name, float floatVal) {
      setPropValue(device, name, floatVal, false);
   }
   
   /**
    * writes float property value to several device adapters using a core call, with error checking
    * @param devices array of enum key for device 
    * @param name enum key for property 
    * @param floatVal value in float form, sent to core using setProperty()
    */
   public void setPropValue(Devices.Keys [] devices, Properties.Keys name, float floatVal) {
      for (Devices.Keys device : devices) {
         setPropValue(device, name, floatVal, false);
      }
   }
   
   /**
    * writes float property value to several device adapters using a core call, with error checking
    * @param devices array of enum key for device 
    * @param name enum key for property 
    * @param floatVal value in float form, sent to core using setProperty()
    * @param ignoreError false (default) will do error checking, true means ignores non-existing property
    */
   public void setPropValue(Devices.Keys [] devices, Properties.Keys name, float floatVal, boolean ignoreError) {
      for (Devices.Keys device : devices) {
         setPropValue(device, name, floatVal, ignoreError);
      }
   }

   /**
    * reads the property value from the device adapter using a core call, 
    * or empty string if it can't find property.
    * @param device enum key for device 
    * @param name enum key for property 
    * @return value in string form, returned from core call to getProperty()
    */
   private String getPropValue(Devices.Keys device, Properties.Keys name) {
      String val;
      if (device == Devices.Keys.PLUGIN) {
         val = prefs_.getString(PLUGIN_PREF_NODE, name, "");
      } else {
         String mmDevice = devices_.getMMDevice(device);
         val = "";  // set to be empty string to avoid null pointer exceptions
         if (mmDevice != null) {
            try {
               val = core_.getProperty(mmDevice, name.toString());
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
      return getPropValue(device, name);
   }
   
   /**
    * returns an integer value for the specified property (assumes the caller knows the property contains an integer).
    * If property isn't found, returns 0.
    * @param device enum key for device 
    * @param name enum key for property 
    * @return
    */
   public int getPropValueInteger(Devices.Keys device, Properties.Keys name) {
      int val = 0;
      if (device == Devices.Keys.PLUGIN) {
         val = prefs_.getInt(PLUGIN_PREF_NODE, name, 0);
      }
      else {
         String strVal = null;
         try {
            strVal = getPropValue(device, name);
            if (!strVal.equals("")) {
               val = NumberUtils.coreStringToInt(strVal);
            }
         } catch (ParseException ex) {
            MyDialogUtils.showError(ex, "Could not parse int value of " + 
                  strVal + " for " + name.toString() + " in device " + 
                  device.toString());
         } catch (NullPointerException ex) {
            MyDialogUtils.showError(ex, "Null Pointer error in function getPropValueInteger");
         }
      }
      return val;
   }

   /**
   * returns an float value for the specified property (assumes the caller knows the property contains a float).
   * If property isn't found, returns 0.
   * @param device enum key for device 
   * @param name enum key for property
   * @return
   */
  public float getPropValueFloat(Devices.Keys device, Properties.Keys name) {
     float val = 0;
     if (device == Devices.Keys.PLUGIN) {
        val = prefs_.getFloat(PLUGIN_PREF_NODE, name, 0);
     }
     else {
        String strVal = null;
        try {
           strVal = getPropValue(device, name);
           if (!strVal.equals("")) {
              val = (float)NumberUtils.coreStringToDouble(strVal);
           }
        } catch (ParseException ex) {
           MyDialogUtils.showError(ex, "Could not parse int value of " + 
                 strVal + " for " + name.toString() + " in device " + 
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
