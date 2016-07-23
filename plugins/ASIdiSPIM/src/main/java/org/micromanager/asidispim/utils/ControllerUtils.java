///////////////////////////////////////////////////////////////////////////////
//FILE:          ControllerUtils.java
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

package org.micromanager.asidispim.utils;

import java.awt.geom.Point2D;
import mmcorej.CMMCore;
import mmcorej.Configuration;
import org.micromanager.asidispim.ASIdiSPIM;
import org.micromanager.Studio;
import org.micromanager.asidispim.data.AcquisitionSettings;
import org.micromanager.asidispim.data.AcquisitionModes;
import org.micromanager.asidispim.data.CameraModes;
import org.micromanager.asidispim.data.ChannelSpec;
import org.micromanager.asidispim.data.Devices;
import org.micromanager.asidispim.data.Joystick;
import org.micromanager.asidispim.data.MultichannelModes;
import org.micromanager.asidispim.data.MyStrings;
import org.micromanager.asidispim.data.Positions;
import org.micromanager.asidispim.data.Prefs;
import org.micromanager.asidispim.data.Properties;

/**
 * @author Nico & Jon
 */
public class ControllerUtils {  
   final Studio gui_;
   final Properties props_;
   final Prefs prefs_;
   final Devices devices_;
   final Positions positions_;
   final CMMCore core_;
   
   public ControllerUtils(Studio gui, final Properties props, 
           final Prefs prefs, final Devices devices, final Positions positions) {
      gui_ = gui;
      props_ = props;
      prefs_ = prefs;
      devices_ = devices;
      positions_ = positions;
      core_ = gui_.core();
   }
   
   /**
   * Sets all the controller's properties according to volume settings
   * and otherwise gets controller all ready for acquisition
   * (except for final trigger).
   * 
   * @param settings
   * 
   * @return false if there was some error that should abort acquisition
   */
   public boolean prepareControllerForAquisition(final AcquisitionSettings settings) 
   {
      // turn off beam and scan on both sides (they are turned off by SPIM state machine anyway)
      // also ensures that properties match reality at end of acquisition
      // SPIM state machine restores position of beam at end of SPIM state machine, now it
      // will be restored to blanking position
      props_.setPropValue(Devices.Keys.GALVOA, Properties.Keys.BEAM_ENABLED,
            Properties.Values.NO, true);
      props_.setPropValue(Devices.Keys.GALVOB, Properties.Keys.BEAM_ENABLED,
            Properties.Values.NO, true);
      props_.setPropValue(Devices.Keys.GALVOA, Properties.Keys.SA_MODE_X,
            Properties.Values.SAM_DISABLED, true);
      props_.setPropValue(Devices.Keys.GALVOB, Properties.Keys.SA_MODE_X,
            Properties.Values.SAM_DISABLED, true);

      
      // set up controller with appropriate SPIM parameters for each active side
      // some of these things only need to be done once if the same micro-mirror
      //   card is used (as is typical) but keeping code universal to handle
      //   case where MM devices reside on different controller cards
      if ((settings.numSides > 1) || settings.firstSideIsA) {
         boolean success = prepareControllerForAquisition_Side(
            Devices.Sides.A, settings);
         if (!success) {
            return false;
         }
      }
      
      if ((settings.numSides > 1) || !settings.firstSideIsA) {
         boolean success = prepareControllerForAquisition_Side(
               Devices.Sides.B, settings);
            if (!success) {
               return false;
            }
      }
      
      if (settings.spimMode == AcquisitionModes.Keys.STAGE_SCAN_INTERLEAVED) {
            if (settings.numSides != 2) {
               MyDialogUtils.showError("Interleaved stage scan only possible for 2-sided acquisition.");
               return false;
            }
            CameraModes.Keys cameraMode = CameraModes.getKeyFromPrefCode(
                  prefs_.getInt(MyStrings.PanelNames.SETTINGS.toString(),
                        Properties.Keys.PLUGIN_CAMERA_MODE, 0));
            if (cameraMode == CameraModes.Keys.OVERLAP) {
               MyDialogUtils.showError("Interleaved stage scan not compatible with overlap camera mode");
               return false;
            }
      }
      
      // set up stage scan parameters if necessary
      if (settings.spimMode == AcquisitionModes.Keys.STAGE_SCAN ||
            settings.spimMode == AcquisitionModes.Keys.STAGE_SCAN_INTERLEAVED) {
         // algorithm is as follows:
         // use the # of slices and slice spacing that the user specifies
         // because the XY stage is 45 degrees from the objectives have to move it sqrt(2) * slice step size
         // for now use the current X position as the start of acquisition and always start in positive X direction
         // for now always do serpentine scan with 2 passes at the same Y location, one pass each direction over the sample
         // => total scan distance = # slices * slice step size * sqrt(2)
         //    scan start position = current X position
         //    scan stop position = scan start position + total distance
         //    slow axis start = current Y position
         //    slow axis stop = current Y position
         //    X motor speed = slice step size * sqrt(2) / slice duration
         //    number of scans = number of sides (1 or 2)
         //    scan mode = serpentine for 2-sided non-interleaved, raster otherwise (need to revisit for 2D stage scanning)
         //    X acceleration time = use whatever current setting is
         //    scan settling time = delay before side
         final boolean isInterleaved = (settings.spimMode == AcquisitionModes.Keys.STAGE_SCAN_INTERLEAVED);
         final Devices.Keys xyDevice = Devices.Keys.XYSTAGE;
         double sliceDuration = settings.sliceTiming.sliceDuration;
         if (isInterleaved) {
            // pretend like our slice takes twice as long so that we move the correct speed
            // this has the effect of halving the motor speed
            // but keeping the scan distance the same
            sliceDuration *= 2;
         }
         
         final double requestedMotorSpeed = settings.stepSizeUm * Math.sqrt(2.) / sliceDuration / settings.numChannels;
         props_.setPropValue(xyDevice, Properties.Keys.STAGESCAN_MOTOR_SPEED, (float)requestedMotorSpeed);
         
         // ask for the actual speed and calculate the actual step size
         final double actualMotorSpeed = props_.getPropValueFloat(xyDevice, Properties.Keys.STAGESCAN_MOTOR_SPEED);
         final double actualStepSizeUm = actualMotorSpeed / Math.sqrt(2.) * sliceDuration * settings.numChannels;  
         
         final double scanDistance = settings.numSlices * actualStepSizeUm * Math.sqrt(2.);
         Point2D.Double posUm;
         try {
            posUm = core_.getXYStagePosition(devices_.getMMDevice(xyDevice));
         } catch (Exception ex) {
            MyDialogUtils.showError("Could not get XY stage position for stage scan initialization");
            return false;
         }
         
         props_.setPropValue(xyDevice, Properties.Keys.STAGESCAN_FAST_START,
               (float)((posUm.x - scanDistance/2) / 1000d));
         props_.setPropValue(xyDevice, Properties.Keys.STAGESCAN_FAST_STOP,
               (float)((posUm.x + scanDistance/2) / 1000d));
         props_.setPropValue(xyDevice, Properties.Keys.STAGESCAN_SLOW_START,
               (float)(posUm.y / 1000d));
         props_.setPropValue(xyDevice, Properties.Keys.STAGESCAN_SLOW_STOP,
               (float)(posUm.y / 1000d));
         props_.setPropValue(xyDevice, Properties.Keys.STAGESCAN_NUMLINES, 
               (isInterleaved ? 1 : settings.numSides));  // assume can't have 1 side interleaved
         props_.setPropValue(xyDevice, Properties.Keys.STAGESCAN_PATTERN,
               (!isInterleaved && (settings.numSides == 2) ? Properties.Values.SERPENTINE : Properties.Values.RASTER));
         props_.setPropValue(xyDevice, Properties.Keys.STAGESCAN_SETTLING_TIME, settings.delayBeforeSide);
         
         // TODO handle other multichannel modes with stage scanning (what does this mean??)
      }
      
      // sets PLogic "acquisition running" flag
      props_.setPropValue(Devices.Keys.PLOGIC, Properties.Keys.PLOGIC_PRESET, 
            Properties.Values.PLOGIC_PRESET_3, true);
      
      return true;
   }
   
   private boolean getSkipScannerWarnings(Devices.Keys galvoDevice) {
      boolean ignoreMissingScanner = prefs_.getBoolean(
            MyStrings.PanelNames.SETTINGS.toString(),  
            Properties.Keys.PREFS_IGNORE_MISSING_SCANNER, false);
      boolean haveMissingScanner = !devices_.isValidMMDevice(galvoDevice);
      
      // checks to prevent hard-to-diagnose other errors
      if (!ignoreMissingScanner && haveMissingScanner) {
         MyDialogUtils.showError("Scanner device required; please check Devices tab.");
            return false;
      }
      
      return ignoreMissingScanner && haveMissingScanner;
   }
   
    /**
    * Sets all the controller's properties according to volume settings
    * and otherwise gets controller all ready for acquisition
    * (except for final trigger).
    * 
    * @param side A, B, or none
    * @param settings
    * 
    * @return false if there was some error that should abort acquisition
    */
   private boolean prepareControllerForAquisition_Side(
         final Devices.Sides side, 
         final AcquisitionSettings settings
         ) {

      Devices.Keys galvoDevice = Devices.getSideSpecificKey(Devices.Keys.GALVOA, side);
      Devices.Keys piezoDevice = Devices.getSideSpecificKey(Devices.Keys.PIEZOA, side);
      Devices.Keys cameraDevice = Devices.getSideSpecificKey(Devices.Keys.CAMERAA, side);
      
      boolean skipScannerWarnings = getSkipScannerWarnings(galvoDevice);
      
      // if we are changing color slice by slice then set controller to do multiple slices per piezo move
      // otherwise just set to 1 slice per piezo move
      int numSlicesPerPiezo = 1;
      if (settings.useChannels && settings.channelMode == MultichannelModes.Keys.SLICE_HW) {
         numSlicesPerPiezo = settings.numChannels;
      }
      props_.setPropValue(galvoDevice, Properties.Keys.SPIM_NUM_SLICES_PER_PIEZO,
            numSlicesPerPiezo, skipScannerWarnings);
      
      // set controller to do multiple volumes per start trigger if we are doing
      //   multiple channels with  hardware switching of channel volume by volume
      // otherwise (no channels, software switching, slice by slice HW switching)
      //   just do one volume per start trigger
      int numVolumesPerTrigger = 1;
      if (settings.useChannels && settings.channelMode == MultichannelModes.Keys.VOLUME_HW) {
         numVolumesPerTrigger = settings.numChannels;
      }
      // can either trigger controller once for all the timepoints and
      //  have the number of repeats pre-programmed (hardware timing)
      //  or let plugin send trigger for each time point (software timing)
      float delayRepeats = 0f;
      if (settings.hardwareTimepoints && settings.useTimepoints) {
         float volumeDurationMs = (float) ASIdiSPIM.getFrame().getAcquisitionPanel().computeActualVolumeDuration(settings);
         float volumeIntervalMs = (float) settings.timepointInterval * 1000f;
         delayRepeats = volumeIntervalMs - volumeDurationMs;
         numVolumesPerTrigger = settings.numTimepoints;
      }
      props_.setPropValue(galvoDevice, Properties.Keys.SPIM_DELAY_REPEATS, delayRepeats, skipScannerWarnings);
      props_.setPropValue(galvoDevice, Properties.Keys.SPIM_NUM_REPEATS, numVolumesPerTrigger, skipScannerWarnings);
      
      // figure out the piezo parameters
      float piezoCenter;
      if (settings.centerAtCurrentZ) {
         piezoCenter = (float) positions_.getUpdatedPosition(piezoDevice, Joystick.Directions.NONE);
      } else {
         piezoCenter = prefs_.getFloat(
            MyStrings.PanelNames.SETUP.toString() + side.toString(), 
            Properties.Keys.PLUGIN_PIEZO_CENTER_POS, 0);
      }
      
      // if we set piezoAmplitude to 0 here then sliceAmplitude will also be 0
      float piezoAmplitude;
      switch (settings.spimMode) {
      case NO_SCAN:
      case STAGE_SCAN:
      case STAGE_SCAN_INTERLEAVED:
         piezoAmplitude = 0.0f;
         break;
      default:
         piezoAmplitude = (settings.numSlices - 1) * settings.stepSizeUm;
      }
      
      // use this instead of settings.numSlices from here on out because
      // we modify it if we are taking "extra slice" for synchronous/overlap
      int numSlices = settings.numSlices;
      
      // tweak the parameters if we are using synchronous/overlap mode
      // object is to get exact same piezo/scanner positions in first
      // N frames (piezo/scanner will move to N+1st position but no image taken)
      CameraModes.Keys cameraMode = CameraModes.getKeyFromPrefCode(
            prefs_.getInt(MyStrings.PanelNames.SETTINGS.toString(),
                  Properties.Keys.PLUGIN_CAMERA_MODE, 0));
      if (cameraMode == CameraModes.Keys.OVERLAP) {
         piezoAmplitude *= ((float)numSlices)/((float)numSlices-1f);
         piezoCenter += piezoAmplitude/(2*numSlices);
         numSlices += 1;
      }
      
      float sliceRate = prefs_.getFloat(
            MyStrings.PanelNames.SETUP.toString() + side.toString(), 
            Properties.Keys.PLUGIN_RATE_PIEZO_SHEET, 100);
      if (MyNumberUtils.floatsEqual(sliceRate, 0.0f)) {
         MyDialogUtils.showError("Calibration slope for side " + side.toString() + 
               " cannot be zero. Re-do calibration on Setup tab.");
         return false;
      }
      float sliceOffset = prefs_.getFloat(
            MyStrings.PanelNames.SETUP.toString() + side.toString(), 
            Properties.Keys.PLUGIN_OFFSET_PIEZO_SHEET, 0);
      float sliceAmplitude = piezoAmplitude / sliceRate;
      float sliceCenter =  (piezoCenter - sliceOffset) / sliceRate;

      // get the micro-mirror card ready
      // SA_AMPLITUDE_X_DEG and SA_OFFSET_X_DEG done by setup tabs
      if (settings.spimMode.equals(AcquisitionModes.Keys.PIEZO_SCAN_ONLY)) {
         // if we artificially shifted centers due to extra trigger and only moving piezo
         // then move galvo center back to where it would have been
         if (cameraMode == CameraModes.Keys.OVERLAP) {
            float actualPiezoCenter = piezoCenter - piezoAmplitude/(2*(numSlices-1));
            sliceCenter = (actualPiezoCenter - sliceOffset) / sliceRate;
         }
         sliceAmplitude = 0.0f;
      }
      // round to nearest 0.0001 degrees, which is approximately the DAC resolution
      sliceAmplitude = MyNumberUtils.roundFloatToPlace(sliceAmplitude, 4);
      sliceCenter = MyNumberUtils.roundFloatToPlace(sliceCenter, 4);
      
      // only do alternating scan directions if the user is using advanced timing
      //    and user has option enabled on the advanced timing panel
      final boolean oppositeDirections = prefs_.getBoolean(
            MyStrings.PanelNames.ACQUSITION.toString(),
            Properties.Keys.PREFS_ADVANCED_SLICE_TIMING, false)
            && prefs_.getBoolean(
            MyStrings.PanelNames.ACQUSITION.toString(),  
            Properties.Keys.PREFS_SCAN_OPPOSITE_DIRECTIONS, false);
      if (oppositeDirections) {
         props_.setPropValue(galvoDevice, Properties.Keys.SPIM_ALTERTATE_DIRECTIONS, 
               Properties.Values.YES, skipScannerWarnings);
      } else {
         props_.setPropValue(galvoDevice, Properties.Keys.SPIM_ALTERTATE_DIRECTIONS, 
               Properties.Values.NO, skipScannerWarnings);
      }
      props_.setPropValue(galvoDevice, Properties.Keys.SPIM_DURATION_SCAN,
            settings.sliceTiming.scanPeriod, skipScannerWarnings);
      props_.setPropValue(galvoDevice, Properties.Keys.SA_AMPLITUDE_Y_DEG,
            sliceAmplitude, skipScannerWarnings);
      props_.setPropValue(galvoDevice, Properties.Keys.SA_OFFSET_Y_DEG,
            sliceCenter, skipScannerWarnings);
      props_.setPropValue(galvoDevice, Properties.Keys.SPIM_NUM_SLICES,
            numSlices, skipScannerWarnings);
      props_.setPropValue(galvoDevice, Properties.Keys.SPIM_NUM_SIDES,
            settings.numSides, skipScannerWarnings);
      props_.setPropValue(galvoDevice, Properties.Keys.SPIM_FIRSTSIDE,
            settings.firstSideIsA ? "A" : "B", skipScannerWarnings);
      
      final boolean autoSheet = prefs_.getBoolean(
            MyStrings.PanelNames.SETUP.toString() + side.toString(), 
            Properties.Keys.PREFS_AUTO_SHEET_WIDTH, false);
      if (autoSheet) {
         ASIdiSPIM.getFrame().getSetupPanel(side).updateSheetWidthROI(cameraDevice);
      }
      // if not autoSheet, then we just use the existing SAA settings
      
      // get the piezo card ready; skip if no piezo specified
      if (devices_.isValidMMDevice(piezoDevice)) {
         // if mode SLICE_SCAN_ONLY we have computed slice movement as if we
         //   were moving the piezo but now make piezo stay still
         if (settings.spimMode.equals(AcquisitionModes.Keys.SLICE_SCAN_ONLY)) {
            // if we artificially shifted centers due to extra trigger and only moving piezo
            // then move galvo center back to where it would have been
            if (cameraMode == CameraModes.Keys.OVERLAP) {
               piezoCenter -= piezoAmplitude/(2*(numSlices-1));
            }
            piezoAmplitude = 0.0f;
         }
         
         float piezoMin = props_.getPropValueFloat(piezoDevice, Properties.Keys.LOWER_LIMIT)*1000;
         float piezoMax = props_.getPropValueFloat(piezoDevice, Properties.Keys.UPPER_LIMIT)*1000;
         
         if (MyNumberUtils.outsideRange(piezoCenter - piezoAmplitude/2, piezoMin, piezoMax) ||
               MyNumberUtils.outsideRange(piezoCenter + piezoAmplitude/2, piezoMin, piezoMax)) {
            MyDialogUtils.showError("Imaging piezo for side " + side.toString() + 
                  " would travel outside the piezo limits during acquisition.");
            return false;
         }
         
         // round to nearest 0.001 micron, which is approximately the DAC resolution
         piezoAmplitude = MyNumberUtils.roundFloatToPlace(piezoAmplitude, 3);
         piezoCenter = MyNumberUtils.roundFloatToPlace(piezoCenter, 3);
         props_.setPropValue(piezoDevice,
               Properties.Keys.SA_AMPLITUDE, piezoAmplitude);
         props_.setPropValue(piezoDevice,
               Properties.Keys.SA_OFFSET, piezoCenter);
         props_.setPropValue(piezoDevice,
               Properties.Keys.SPIM_NUM_SLICES, numSlices);
         props_.setPropValue(piezoDevice,
               Properties.Keys.SPIM_STATE, Properties.Values.SPIM_ARMED);
      }
      
      // set up stage scan parameters if necessary
      if (settings.spimMode == AcquisitionModes.Keys.STAGE_SCAN ||
            settings.spimMode == AcquisitionModes.Keys.STAGE_SCAN_INTERLEAVED) {
         if (settings.useChannels && settings.channelMode == MultichannelModes.Keys.SLICE_HW) {
            // TODO understand/document what this is doing, eliminate if possible
            // will take one slice from each channel before switching sides
            props_.setPropValue(galvoDevice, Properties.Keys.SPIM_NUM_SLICES_PER_PIEZO,
                  settings.numChannels, skipScannerWarnings);
         }
      }
      if (settings.spimMode == AcquisitionModes.Keys.STAGE_SCAN_INTERLEAVED) {
         props_.setPropValue(galvoDevice, Properties.Keys.SPIM_PIEZO_HOME_DISABLE,
               Properties.Values.YES, skipScannerWarnings);
      } else {
         props_.setPropValue(galvoDevice, Properties.Keys.SPIM_PIEZO_HOME_DISABLE,
               Properties.Values.NO, skipScannerWarnings);
      }
      
      // set interleaved sides flag low unless we are doing interleaved stage scan
      if (settings.spimMode == AcquisitionModes.Keys.STAGE_SCAN_INTERLEAVED) {
         props_.setPropValue(galvoDevice, Properties.Keys.SPIM_INTERLEAVE_SIDES,
               Properties.Values.YES, skipScannerWarnings); // make sure to check for errors
      } else {
         props_.setPropValue(galvoDevice, Properties.Keys.SPIM_INTERLEAVE_SIDES,
               Properties.Values.NO, true);  // ignore errors b/c older firmware won't have it
      }
      
      if (settings.spimMode == AcquisitionModes.Keys.STAGE_SCAN_INTERLEAVED) {
         props_.setPropValue(galvoDevice, Properties.Keys.SPIM_INTERLEAVE_SIDES,
               Properties.Values.YES, skipScannerWarnings); // make sure to check for errors
      } else {
         props_.setPropValue(galvoDevice, Properties.Keys.SPIM_INTERLEAVE_SIDES,
               Properties.Values.NO, true);  // ignore errors b/c older firmware won't have it
      }
      
      return true;
   }
   
   
   /**
    * Returns the controller to "normal" state after an acquisition
    *
    * @param numSides number of Sides from which we take data (diSPIM: 1 or 2)
    * @param firstSideIsA firstSide to take data from (A or B)
    * @param centerPiezos true to move piezos to center position
    * @return false if there is a fatal error, true if successful
    */
   public boolean cleanUpControllerAfterAcquisition(
         final int numSides,
         final boolean firstSideIsA,
         final boolean centerPiezos
         ) {
      
      // this only happens after images have all been received (or timeout occurred)

      // clear "acquisition running" flag on PLC
      props_.setPropValue(Devices.Keys.PLOGIC, Properties.Keys.PLOGIC_PRESET, 
            Properties.Values.PLOGIC_PRESET_2, true);
      
      if ((numSides > 1) || firstSideIsA) {
         boolean success = cleanUpControllerAfterAcquisition_Side(
               Devices.Sides.A, centerPiezos, 0.0f);
         if (!success) {
            return false;
         }
      }
      if ((numSides > 1) || !firstSideIsA) {
         boolean success = cleanUpControllerAfterAcquisition_Side(
               Devices.Sides.B, centerPiezos, 0.0f);
         if (!success) {
            return false;
         }
      }
      
      return true;
   }
   
   /**
    * Returns the controller to "normal" state after an acquisition
    * 
    * @param side A, B, or none
    * @param movePiezo true if we are to move the piezo
    * @param piezoPosition position to move the piezo to, only matters if movePiezo is true
    * @return false if there is a fatal error, true if successful
    */
   private boolean cleanUpControllerAfterAcquisition_Side(
         final Devices.Sides side,
         final boolean movePiezo,
         final float piezoPosition
         ) {
      
      Devices.Keys piezoDevice = Devices.getSideSpecificKey(Devices.Keys.PIEZOA, side);
      Devices.Keys galvoDevice = Devices.getSideSpecificKey(Devices.Keys.GALVOA, side);
      
      // move piezo back to desired position
      if (movePiezo) {
         positions_.setPosition(piezoDevice, piezoPosition, true); 
      }
      
      // make sure to stop the SPIM state machine in case the acquisition was cancelled
      // even if the acquisition wasn't cancelled make sure the Micro-Manager properties are updated
      props_.setPropValue(galvoDevice, Properties.Keys.SPIM_STATE,
            Properties.Values.SPIM_IDLE, true);
      
      return true;
   }
   
   
   /**
    * Programs the PLogic card for hardware channel switching
    * according to the selections in the Multichannel subpanel
<<<<<<< HEAD
    * @param settings
=======
    * @param isMultiChannel
    * @param numChannels
    * @param channels
    * @param channelGroup
    * @param firstSideA
>>>>>>> mm2diSPIM
    * @return false if there is a fatal error, true if successful
    */
   public boolean setupHardwareChannelSwitching(final AcquisitionSettings settings) {
      
      final int counterLSBAddress = 3;
      final int counterMSBAddress = 4;
      final int laserTriggerAddress = 10;  // this should be set to (42 || 8) = (TTL1 || manual laser on)
      final int invertAddress = 64;
      
      if (!devices_.isValidMMDevice(Devices.Keys.PLOGIC)) {
         MyDialogUtils.showError("PLogic card required for hardware switching");
         return false;
      }
      
      MultichannelModes.Keys channelMode = settings.channelMode;
      
      if ((settings.numChannels > 4) &&
            ((channelMode == MultichannelModes.Keys.SLICE_HW) || 
            (channelMode == MultichannelModes.Keys.VOLUME_HW)) ) {
         MyDialogUtils.showError("PLogic card cannot handle more than 4 channels for hardware switching.");
         return false;
      }
      
      // set up clock for counters
      switch (channelMode) {
      case SLICE_HW:
         props_.setPropValue(Devices.Keys.PLOGIC, Properties.Keys.PLOGIC_PRESET,
               Properties.Values.PLOGIC_PRESET_CLOCK_LASER);
         break;
      case VOLUME_HW:
         if (settings.firstSideIsA) {
            props_.setPropValue(Devices.Keys.PLOGIC, Properties.Keys.PLOGIC_PRESET,
                  Properties.Values.PLOGIC_PRESET_CLOCK_SIDE_AFIRST);
         } else {
            props_.setPropValue(Devices.Keys.PLOGIC, Properties.Keys.PLOGIC_PRESET,
                  Properties.Values.PLOGIC_PRESET_CLOCK_SIDE_BFIRST);
         }
         break;
      default:
         MyDialogUtils.showError("Unknown multichannel mode for hardware switching");
         return false;
      }
      
      // set up hardware counter
      switch (settings.numChannels) {
      case 1:
         props_.setPropValue(Devices.Keys.PLOGIC, Properties.Keys.PLOGIC_PRESET,
               Properties.Values.PLOGIC_PRESET_COUNT_1);
      break;
      case 2:
         props_.setPropValue(Devices.Keys.PLOGIC, Properties.Keys.PLOGIC_PRESET,
               Properties.Values.PLOGIC_PRESET_COUNT_2);
         break;
      case 3:
         props_.setPropValue(Devices.Keys.PLOGIC, Properties.Keys.PLOGIC_PRESET,
               Properties.Values.PLOGIC_PRESET_COUNT_3);
         break;
      case 4:
         props_.setPropValue(Devices.Keys.PLOGIC, Properties.Keys.PLOGIC_PRESET,
               Properties.Values.PLOGIC_PRESET_COUNT_4);
         break;
      default:
         MyDialogUtils.showError("Hardware channel switching only supports 1-4 channels");
         return false;
      }
      
      // speed things up by turning off updates, will restore value later
      String editCellUpdates = props_.getPropValueString(Devices.Keys.PLOGIC, Properties.Keys.PLOGIC_EDIT_CELL_UPDATES);
      if (!editCellUpdates.equals(Properties.Values.NO)) {
         props_.setPropValue(Devices.Keys.PLOGIC, Properties.Keys.PLOGIC_EDIT_CELL_UPDATES, Properties.Values.NO);
      }
      
      // initialize cells 13-16 which control BNCs 5-8
      for (int cellNum=13; cellNum<=16; cellNum++) {
         props_.setPropValue(Devices.Keys.PLOGIC, Properties.Keys.PLOGIC_POINTER_POSITION, cellNum);
         props_.setPropValue(Devices.Keys.PLOGIC, Properties.Keys.PLOGIC_EDIT_CELL_TYPE, Properties.Values.PLOGIC_AND4);
         props_.setPropValue(Devices.Keys.PLOGIC, Properties.Keys.PLOGIC_EDIT_CELL_INPUT_2, laserTriggerAddress);
         // note that PLC diSPIM assumes "laser + side" output mode is selected for micro-mirror card
      }
      
      // identify BNC from the preset and set counter inputs for 13-16 appropriately 
      boolean[] hardwareChannelUsed = new boolean[4]; // initialized to all false
      for (int channelNum = 0; channelNum < settings.numChannels; channelNum++) {
         // we already know there are between 1 and 4 channels
         int outputNum = getPLogicOutputFromChannel(settings.channels[channelNum], settings.channelGroup);
         if (outputNum<5) {  // check for error in getPLogicOutputFromChannel()
            // restore update setting
            props_.setPropValue(Devices.Keys.PLOGIC, Properties.Keys.PLOGIC_EDIT_CELL_UPDATES, editCellUpdates);
            return false;  // already displayed error
         }
         // make sure we don't have multiple Micro-Manager channels using same hardware channel
         if (hardwareChannelUsed[outputNum-5]) {
            // restore update setting
            props_.setPropValue(Devices.Keys.PLOGIC, Properties.Keys.PLOGIC_EDIT_CELL_UPDATES, editCellUpdates);
            MyDialogUtils.showError("Multiple channels cannot use same laser for PLogic triggering");
            return false;
         } else {
            hardwareChannelUsed[outputNum-5] = true;
         }
         props_.setPropValue(Devices.Keys.PLOGIC, Properties.Keys.PLOGIC_POINTER_POSITION, outputNum + 8);
         props_.setPropValue(Devices.Keys.PLOGIC, Properties.Keys.PLOGIC_EDIT_CELL_INPUT_1, invertAddress);  // enable this AND4
         // if we are doing per-volume switching with side B first then counter will start at 1 instead of 0
         // the following lines account for this by incrementing the channel number "match" by 1 in this special case 
         int adjustedChannelNum = channelNum;
         if (channelMode == MultichannelModes.Keys.VOLUME_HW && !settings.firstSideIsA) {
            adjustedChannelNum = (channelNum+1) % settings.numChannels;
         }
         // map the channel number to the equivalent addresses for the AND4
         // inputs should be either 3 (for LSB high) or 67 (for LSB low)
         //                     and 4 (for MSB high) or 68 (for MSB low)
         final int in3 = (adjustedChannelNum & 0x01) > 0 ? counterLSBAddress : counterLSBAddress + invertAddress;
         final int in4 = (adjustedChannelNum & 0x02) > 0 ? counterMSBAddress : counterMSBAddress + invertAddress; 
         props_.setPropValue(Devices.Keys.PLOGIC, Properties.Keys.PLOGIC_EDIT_CELL_INPUT_3, in3);
         props_.setPropValue(Devices.Keys.PLOGIC, Properties.Keys.PLOGIC_EDIT_CELL_INPUT_4, in4);
      }
      
      // make sure cells 13-16 are controlling BNCs 5-8
      props_.setPropValue(Devices.Keys.PLOGIC, Properties.Keys.PLOGIC_PRESET,
            Properties.Values.PLOGIC_PRESET_BNC5_8_ON_13_16);
      
      // restore update setting
      if (!editCellUpdates.equals(Properties.Values.NO)) {
         props_.setPropValue(Devices.Keys.PLOGIC, Properties.Keys.PLOGIC_EDIT_CELL_UPDATES, editCellUpdates);
      }
      
      return true;
   }
   
   /**
    * Triggers the Tiger controller
    * @param spimMode
    * @param isFirstSideA
    * @return false only if there is a problem
    */
   public boolean triggerControllerStartAcquisition(
           final AcquisitionModes.Keys spimMode, final boolean isFirstSideA) {
      switch (spimMode) {
      case STAGE_SCAN:
      case STAGE_SCAN_INTERLEAVED:
         // for stage scan we send trigger to stage card, which sends
         //    hardware trigger to the micro-mirror card
         props_.setPropValue(Devices.Keys.GALVOA, Properties.Keys.SPIM_STATE,
               Properties.Values.SPIM_ARMED);
         props_.setPropValue(Devices.Keys.XYSTAGE, Properties.Keys.STAGESCAN_STATE,
               Properties.Values.SPIM_RUNNING);
         break;
      case PIEZO_SLICE_SCAN:
      case SLICE_SCAN_ONLY:
      case PIEZO_SCAN_ONLY:
      case NO_SCAN:
         // in actuality only matters which device we trigger if there are
         //   two micro-mirror cards, which hasn't ever been done in practice yet
         Devices.Keys galvoDevice = isFirstSideA ? Devices.Keys.GALVOA : Devices.Keys.GALVOB;
         props_.setPropValue(galvoDevice, Properties.Keys.SPIM_STATE,
               Properties.Values.SPIM_RUNNING, getSkipScannerWarnings(galvoDevice));
         break;
      default:
         MyDialogUtils.showError("Unknown acquisition mode");
         return false;
      }
      return true;
   }
  
   /**
    * Gets the associated PLogic BNC from the channel (containing preset name) 
    * @param channel
    * @return value 5, 6, 7, or 8; returns 0 if there is an error
    */
   private int getPLogicOutputFromChannel(ChannelSpec channel, String channelGroup) {
      try {
         Configuration configData = core_.getConfigData(
                 channelGroup, channel.config_);
         if (!configData.isPropertyIncluded(devices_.getMMDevice(Devices.Keys.PLOGIC), Properties.Keys.PLOGIC_OUTPUT_CHANNEL.toString())) {
            MyDialogUtils.showError("Must include PLogic \"OutputChannel\" in preset for hardware switching");
            return 0;
         }
         String setting = configData.getSetting(devices_.getMMDevice(Devices.Keys.PLOGIC), Properties.Keys.PLOGIC_OUTPUT_CHANNEL.toString()).getPropertyValue();
         if (setting.equals(Properties.Values.PLOGIC_CHANNEL_BNC5.toString())) {
            return 5;
         } else if (setting.equals(Properties.Values.PLOGIC_CHANNEL_BNC6.toString())) {
            return 6;
         } else if (setting.equals(Properties.Values.PLOGIC_CHANNEL_BNC7.toString())) {
            return 7;
         } else if (setting.equals(Properties.Values.PLOGIC_CHANNEL_BNC8.toString())) {
            return 8;
         } else {
            MyDialogUtils.showError("Channel preset setting must use PLogic \"OutputChannel\" and be set to one of outputs 5-8 only");
            return 0;
         }
      } catch (Exception e) {
         MyDialogUtils.showError(e, "Could not get PLogic output from channel");
         return 0;
      }
   }
   
}
