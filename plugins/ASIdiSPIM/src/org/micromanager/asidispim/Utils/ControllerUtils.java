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

package org.micromanager.asidispim.Utils;

import java.awt.Rectangle;
import java.awt.geom.Point2D;

import mmcorej.CMMCore;
import mmcorej.Configuration;
import mmcorej.DoubleVector;

import org.micromanager.api.ScriptInterface;
import org.micromanager.asidispim.ASIdiSPIM;
import org.micromanager.asidispim.Data.AcquisitionModes;
import org.micromanager.asidispim.Data.AcquisitionSettings;
import org.micromanager.asidispim.Data.CameraModes;
import org.micromanager.asidispim.Data.Cameras;
import org.micromanager.asidispim.Data.ChannelSpec;
import org.micromanager.asidispim.Data.Devices;
import org.micromanager.asidispim.Data.Joystick;
import org.micromanager.asidispim.Data.MultichannelModes;
import org.micromanager.asidispim.Data.MyStrings;
import org.micromanager.asidispim.Data.Positions;
import org.micromanager.asidispim.Data.Prefs;
import org.micromanager.asidispim.Data.Properties;
import org.micromanager.utils.ReportingUtils;

/**
 * @author Nico
 * @author Jon
 */
public class ControllerUtils {  
   final ScriptInterface gui_;
   final Properties props_;
   final Prefs prefs_;
   final Devices devices_;
   final Positions positions_;
   final Cameras cameras_;
   final CMMCore core_;
   double scanDistance_;   // in microns; cached value from last call to prepareControllerForAquisition()
   double actualStepSizeUm_;  // cached value from last call to prepareControllerForAquisition()
   boolean zSpeedZero_;  // cached value from last call to prepareStageScanForAcquisition()
   String lastDistanceStr_;  // cached value from last call to prepareControllerForAquisition() 
   String lastPosStr_;       // cached value from last call to prepareControllerForAquisition()
   double supOrigSpeed_;
   
   final int triggerStepDurationTics = 10;  // 2.5ms with 0.25ms tics
   final int zeroAddr = 0;
   final int invertAddr = 64;
   final int edgeAddr = 128;
   final int acquisitionFlagAddr = 1;
   final int counterLSBAddr = 3;
   final int counterMSBAddr = 4;
   final int triggerStepEdgeAddr = 6;
   final int triggerStepPulseAddr = 7;
   final int triggerStepOutputAddr = 40;  // BNC #8
   final int triggerInAddr = 35;  // BNC #3
   final int triggerSPIMAddr = 46;  // backplane signal, same as XY card's TTL output for stage scanning
                                    // with external trigger use same address b/c MMSPIM card always uses that backplane line to trigger
   final int laserTriggerAddress = 10;  // this should be set to (42 || 8) = (TTL1 || manual laser on)
   final String MACRO_NAME_STEP = "STEPTRIG";
   final String MACRO_NAME_SCAN = "SCANTRIG";
   
   public ControllerUtils(ScriptInterface gui, final Properties props, 
           final Prefs prefs, final Devices devices, final Positions positions, final Cameras cameras) {
      gui_ = gui;
      props_ = props;
      prefs_ = prefs;
      devices_ = devices;
      positions_ = positions;
      cameras_ = cameras;
      core_ = gui_.getMMCore();
      scanDistance_ = 0;
      actualStepSizeUm_ = 0;
      zSpeedZero_ = true;
      lastDistanceStr_ = "";
      lastPosStr_ = "";
   }
   
   /**
    * Moves supplemental stage to initial position with 0 as center.  Blocking call.
    * This method assumes that prepareControllerForAquisition() has been called already
    *    to initialize scanDistance_.  We always move supplemental X in positive-going direction
    *    so this method moves to negative position.
    */
   public void moveSupplementalToStartPosition() {
      positions_.setPosition(Devices.Keys.SUPPLEMENTAL_X, scanDistance_/-2d);
      try {
         core_.waitForDevice(devices_.getMMDevice(Devices.Keys.SUPPLEMENTAL_X));
      } catch (Exception e) {
         e.printStackTrace();
      }
   }
   
   /**
    * Stage scan needs to be setup at each XY position, so call this method.
    * This method assumes that prepareControllerForAquisition() has been called already
    *    to initialize scanDistance_.  We always scan X in positive-going direction.
    * For supplemental scan this moves stage to starting position too.
    * @param x center x position in um
    * @param y y position in um
    * @return false if there was some error that should abort acquisition 
    */
   public boolean prepareStageScanForAcquisition(double x, double y, AcquisitionModes.Keys spimMode) {
      final boolean scanFromStart = prefs_.getBoolean(MyStrings.PanelNames.SETTINGS.toString(), Properties.Keys.PLUGIN_SCAN_FROM_START_POSITION, false);
      final boolean scanNegative = prefs_.getBoolean(MyStrings.PanelNames.SETTINGS.toString(), Properties.Keys.PLUGIN_SCAN_NEGATIVE_DIRECTION, false);
      props_.setPropValue(Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_STAGESCAN_CENTER_X_POSITION, (float)(x));
      props_.setPropValue(Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_STAGESCAN_Y_POSITION, (float)(y));
      
      if (spimMode == AcquisitionModes.Keys.STAGE_SCAN_SUPPLEMENTAL_UNIDIRECTIONAL) {
         moveSupplementalToStartPosition();
      } else {
         final Devices.Keys xyDevice = Devices.Keys.XYSTAGE;
         final double xStartUm, xStopUm;
         if (scanFromStart) {
            if (scanNegative) {
               xStartUm = x;
               xStopUm = x - scanDistance_;
            } else {
               xStartUm = x;
               xStopUm = x + scanDistance_;
            }
         } else { // centered
            if (scanNegative) {
               xStartUm = x + (scanDistance_/2);
               xStopUm = x - (scanDistance_/2);
            } else {  // the original implementation
               xStartUm = x - (scanDistance_/2);
               xStopUm = x + (scanDistance_/2);
            }
         }
         props_.setPropValue(xyDevice, Properties.Keys.STAGESCAN_FAST_START, (float)(xStartUm/1000d));
         props_.setPropValue(xyDevice, Properties.Keys.STAGESCAN_FAST_STOP, (float)(xStopUm/1000d));
         props_.setPropValue(xyDevice, Properties.Keys.STAGESCAN_SLOW_START, (float)(y/1000d));
         props_.setPropValue(xyDevice, Properties.Keys.STAGESCAN_SLOW_STOP, (float)(y/1000d));
      }

      zSpeedZero_ = true;  // will turn false if we are doing planar correction
      return preparePlanarCorrectionForAcquisition();
   }
   
   /**
    * 
    * @return
    */
   public boolean preparePlanarCorrectionForAcquisition() {
      
      if (!prefs_.getBoolean(MyStrings.PanelNames.ACQUSITION.toString(), Properties.Keys.PLUGIN_PLANAR_ENABLED, false)) {
         return true;  // nothing to do
      }
      
      // handle setting up planar correction if needed
      final float xSpeed = props_.getPropValueFloat(Devices.Keys.XYSTAGE, Properties.Keys.STAGESCAN_MOTOR_SPEED_X_MICRONS)/1000f;  // in mm/sec = controller units
      final float xSlope = prefs_.getFloat(MyStrings.PanelNames.ACQUSITION.toString(), 
            Properties.Keys.PLUGIN_PLANAR_SLOPE_X, 0.0f) / 1000f;  // could be negative!
      final Devices.Keys zDevice = Devices.Keys.UPPERZDRIVE;

      // Z speed is simply ratio of X speed based on slope = partial derivative dZ/dX of plane equation
      // calculate ideal speed, then implement as best we can on the controller
      final float zSpeedIdeal = xSpeed*Math.abs(xSlope);  // in mm/sec = controller units
      final float zSpeedMin = props_.getPropValueFloat(zDevice, Properties.Keys.STAGESCAN_MIN_MOTOR_SPEED_Z)/1000f;  // will return 0 if we don't know min
      final float zSpeedRequested;
      if (zSpeedIdeal < (zSpeedMin/2)) {  // too slow for controller to handle so just make it 0 = no correction
         zSpeedRequested = 0.0f;
      } else {
         zSpeedRequested = zSpeedIdeal;
         zSpeedZero_ = false;
      }

      // compute actual speed and handle case where we are making planar correction move
      final float zSpeedActual;
      if (zSpeedZero_) {
         zSpeedActual = 0.0f;
      } else {
         float origSpeed = props_.getPropValueFloat(zDevice, Properties.Keys.STAGESCAN_MOTOR_SPEED_Z);
         // we actually have a non-zero speed for the Z axis to move during acquisition
         props_.setPropValue(zDevice, Properties.Keys.STAGESCAN_MOTOR_SPEED_Z, zSpeedRequested);
         if (props_.hasProperty(zDevice, Properties.Keys.STAGESCAN_MOTOR_SPEED_MICRONS_Z)) {
            zSpeedActual = props_.getPropValueFloat(zDevice, Properties.Keys.STAGESCAN_MOTOR_SPEED_MICRONS_Z)/1000f;
         } else {
            zSpeedActual = zSpeedRequested;  // if we have older firmware the we can't read back actual speed
         }

         // set Z for start position taking into account the actual speed (so that if the
         //    speed isn't quite right we split the error on both sides)
         final double xCenter = props_.getPropValueFloat(Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_STAGESCAN_CENTER_X_POSITION);
         final double yCenter = props_.getPropValueFloat(Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_STAGESCAN_Y_POSITION);
         final double zCenter = getPlanarZ(xCenter, yCenter);
         final double zOffset = Math.signum(xSlope)*(zSpeedActual / xSpeed * scanDistance_/2);
         final float zStart = (float)(zCenter - zOffset);
         final float zStop = (float)(zCenter + zOffset);

         // move Z to correct position now, even before scan, with original speed
         props_.setPropValue(zDevice, Properties.Keys.STAGESCAN_MOTOR_SPEED_Z, origSpeed);
         positions_.setPosition(zDevice, zStart);
         try {
            core_.waitForDevice(devices_.getMMDevice(zDevice));
         } catch (Exception e1) {
            e1.printStackTrace();
         }
         props_.setPropValue(zDevice, Properties.Keys.STAGESCAN_MOTOR_SPEED_Z, zSpeedRequested);  // results in zSpeedActual
         props_.setPropValue(Devices.Keys.PLUGIN, Properties.Keys.STAGESCAN_Z_START, zStart);

         // load the ring buffer with (only) the end position and configure to be TTL triggered by the XY stage sync
         props_.setPropValue(Devices.Keys.UPPERZDRIVE, Properties.Keys.TTLINPUT_MODE, Properties.Values.TTLINPUT_MODE_NEXT_RB);
         DoubleVector positionSequence = new DoubleVector();
         positionSequence.add(zStop);
         try {
            core_.loadStageSequence(devices_.getMMDevice(Devices.Keys.UPPERZDRIVE), positionSequence);
         } catch (Exception e) {
            e.printStackTrace();
         }

         // configure PLC output #3 to be the sync signal from XY stage (maybe in future this could be wired on backpanel
         //   but for now requires connecting BNC from PLC #3 to TTLin trigger of Z axis card
         if (!devices_.isSingle7ChPLogic()) {
            final Devices.Keys plcDevice = Devices.Keys.PLOGIC;
            final int PLOGIC_OUTPUT_3_ADDR = 35;
            final int PLOGIC_XYSTAGE_SYNC_ADDR = 46;
            props_.setPropValue(plcDevice, Properties.Keys.PLOGIC_POINTER_POSITION, PLOGIC_OUTPUT_3_ADDR);
            props_.setPropValue(plcDevice, Properties.Keys.PLOGIC_EDIT_CELL_CONFIG, PLOGIC_XYSTAGE_SYNC_ADDR);
         }
      }
      
      return true;
   }
   
   /**
    * call special version which will only set the slice offset and not refresh everything else
    * @param settings
    * @param channelOffset
    * @return
    */
   public boolean prepareControllerForAquisitionOffsetOnly(final AcquisitionSettings settings, double channelOffset) {
      if ((settings.numSides > 1) || settings.firstSideIsA) {
         boolean success = prepareControllerForAquisition_Side(
               Devices.Sides.A, settings, channelOffset, true);
            if (!success) {
               return false;
            }
      }
      if ((settings.numSides > 1) || !settings.firstSideIsA) {
         boolean success = prepareControllerForAquisition_Side(
               Devices.Sides.B, settings, channelOffset, true);
            if (!success) {
               return false;
            }
      }
      return true;
   }
   
   /**
   * Sets all the controller's properties according to volume settings
   * and otherwise gets controller all ready for acquisition
   * (except for final trigger).
   * 
   * @param settings
   * @param channelOffset 
   * @return false if there was some error that should abort acquisition
   */
   public boolean prepareControllerForAquisition(final AcquisitionSettings settings, double channelOffset) {
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
            Devices.Sides.A, settings, channelOffset, false);
         if (!success) {
            return false;
         }
      }
      
      if ((settings.numSides > 1) || !settings.firstSideIsA) {
         boolean success = prepareControllerForAquisition_Side(
               Devices.Sides.B, settings, channelOffset, false);
            if (!success) {
               return false;
            }
      }
      
      if (settings.isStageScanning && 
            (settings.spimMode == AcquisitionModes.Keys.STAGE_SCAN_INTERLEAVED)) {
            if (settings.numSides != 2) {
               MyDialogUtils.showError("Interleaved stage scan only possible for 2-sided acquisition.");
               return false;
            }
            if (settings.cameraMode == CameraModes.Keys.OVERLAP) {
               MyDialogUtils.showError("Interleaved stage scan not compatible with overlap camera mode");
               return false;
            }
      }
      
      // make sure set to use TTL signal from backplane in case PLOGIC_LASER is set to PLogicMode different from diSPIM shutter
      props_.setPropValue(new Devices.Keys[]{Devices.Keys.PLOGIC, Devices.Keys.PLOGIC_LASER},
            Properties.Keys.PLOGIC_PRESET, Properties.Values.PLOGIC_PRESET_12, true);
      
      // make sure shutter is set to the PLOGIC_LASER device
      try {
         core_.setShutterDevice(devices_.getMMDevice(Devices.Keys.PLOGIC_LASER));
      } catch (Exception e) {
         e.printStackTrace();
      }

      // set up stage step/scan parameters if necessary
      final String controllerDeviceName = props_.getPropValueString(Devices.Keys.SUPPLEMENTAL_X, Properties.Keys.CONTROLLER_NAME);
      if (settings.isStageStepping) {  // currently only with non-ASI stage
         if (settings.spimMode == AcquisitionModes.Keys.STAGE_STEP_SUPPLEMENTAL_UNIDIRECTIONAL) {
            actualStepSizeUm_ = settings.stepSizeUm;
            DeviceUtils du = new DeviceUtils(gui_, devices_, props_, prefs_);
            final double stepDistance = actualStepSizeUm_ * du.getStageGeometricSpeedFactor(settings.firstSideIsA);
            scanDistance_ = settings.numSlices * stepDistance;
            
            // dynamically generate macro and then send to PI controller
            if (devices_.getMMDeviceLibrary(Devices.Keys.SUPPLEMENTAL_X) == Devices.Libraries.PI_GCS_2) {
               final String distanceStr = Double.toString(stepDistance/1000).substring(0, 10);  // distance specified in mm
               final String[] macroText;
               if (lastDistanceStr_.equals(distanceStr)) {
                  // just start the existing macro if it hasn't changed
                  macroText = new String[] { "MAC START " + MACRO_NAME_STEP };
               } else {
                  // have to send an updated macro
                  lastDistanceStr_ = distanceStr;
                  macroText = new String[] {
                        "MAC BEG " + MACRO_NAME_STEP  ,  // define new macro
                        "WAC DIO? 1 = 1"              ,  // wait for digital input #1 to go high
                        "MVR 1 " + distanceStr        ,  // increment target position by requested distance
                        "WAC DIO? 1 = 0"              ,  // wait for digital input #1 to go low
                        "MAC START " + MACRO_NAME_STEP,  // restart the macro looking for next trigger
                        "MAC END"                     ,  // end definition
                  };
               }
               
               // actually send macro over serial
               try {
                  for (String s : macroText) {
                     props_.setPropValueDirect(controllerDeviceName, Properties.Keys.SEND_COMMAND, s);
                  }
               } catch (Exception e) {
                  MyDialogUtils.showError("Could not send macro to PI controller.");
               }
            } else {
               MyDialogUtils.showError("Supplemental stage " + devices_.getMMDevice(Devices.Keys.SUPPLEMENTAL_X).toString()
                     + " is not supported for stage stepping.");
               return false;
            }
            
            if (devices_.isSingle7ChPLogic()) {
               MyDialogUtils.showError("Supplemental stage step not compatible with 7 channel laser trigger");
               return false;
            }
            
            // cell 2 will be rising edge whenever laser on goes low
            props_.setPropValue(new Devices.Keys[]{Devices.Keys.PLOGIC, Devices.Keys.PLOGIC_LASER},
                  Properties.Keys.PLOGIC_PRESET, Properties.Values.PLOGIC_PRESET_CLOCK_LASER, true);
            
            // cells 6 and 7 used to make a 10ms pulse out whenever counter rolls back to 0
            props_.setPropValue(Devices.Keys.PLOGIC_LASER, Properties.Keys.PLOGIC_POINTER_POSITION, triggerStepEdgeAddr);
            props_.setPropValue(Devices.Keys.PLOGIC_LASER, Properties.Keys.PLOGIC_EDIT_CELL_TYPE, Properties.Values.PLOGIC_AND2);
            props_.setPropValue(Devices.Keys.PLOGIC_LASER, Properties.Keys.PLOGIC_EDIT_CELL_INPUT_1, counterLSBAddr + invertAddr);
            props_.setPropValue(Devices.Keys.PLOGIC_LASER, Properties.Keys.PLOGIC_EDIT_CELL_INPUT_2, counterMSBAddr + invertAddr);
            props_.setPropValue(Devices.Keys.PLOGIC_LASER, Properties.Keys.PLOGIC_POINTER_POSITION, triggerStepPulseAddr);
            props_.setPropValue(Devices.Keys.PLOGIC_LASER, Properties.Keys.PLOGIC_EDIT_CELL_TYPE, Properties.Values.PLOGIC_ONESHOT_NRT);
            props_.setPropValue(Devices.Keys.PLOGIC_LASER, Properties.Keys.PLOGIC_EDIT_CELL_CONFIG, triggerStepDurationTics);
            props_.setPropValue(Devices.Keys.PLOGIC_LASER, Properties.Keys.PLOGIC_EDIT_CELL_INPUT_1, triggerStepEdgeAddr + edgeAddr);
            props_.setPropValue(Devices.Keys.PLOGIC_LASER, Properties.Keys.PLOGIC_EDIT_CELL_INPUT_2, invertAddr);
            
            // set output #8 to be the pulse
            props_.setPropValue(Devices.Keys.PLOGIC_LASER, Properties.Keys.PLOGIC_POINTER_POSITION, triggerStepOutputAddr);
            props_.setPropValue(Devices.Keys.PLOGIC_LASER, Properties.Keys.PLOGIC_EDIT_CELL_CONFIG, triggerStepPulseAddr);
            
            moveSupplementalToStartPosition();
         }
      } else if (settings.isStageScanning) {  
         final double actualMotorSpeed;
         final Devices.Keys xyDevice = Devices.Keys.XYSTAGE;
         
         // figure out the speed we should be going according to slice period, slice spacing, geometry, etc.
         final double requestedMotorSpeed = computeScanSpeed(settings);  // in mm/sec
         
         if (settings.spimMode == AcquisitionModes.Keys.STAGE_SCAN_SUPPLEMENTAL_UNIDIRECTIONAL) {  // scanning with non-ASI stage
            if (devices_.getMMDeviceLibrary(Devices.Keys.SUPPLEMENTAL_X) == Devices.Libraries.PI_GCS_2) {
               final Devices.Keys piDevice = Devices.Keys.SUPPLEMENTAL_X;
               final int rampTimeMs = Math.round(settings.delayBeforeSide);
               
               actualStepSizeUm_ = settings.stepSizeUm;
               DeviceUtils du = new DeviceUtils(gui_, devices_, props_, prefs_);
               final double stepDistance = actualStepSizeUm_ * du.getStageGeometricSpeedFactor(settings.firstSideIsA);
               scanDistance_ = settings.numSlices * stepDistance;
               
               // for this mode only, scanDistance_ will include ramp up/down distance of some startup time hard-coded as rampTimeMs
               final double rampDistanceUm = requestedMotorSpeed*rampTimeMs;
               scanDistance_ = scanDistance_ + 2*rampDistanceUm;
               
               // move to start position before we change velocity to scan value
               // remember original velocity so we can restore it in cleanUpControllerAfterAcquisition()
               supOrigSpeed_ = props_.getPropValueFloat(piDevice, Properties.Keys.VELOCITY);
               moveSupplementalToStartPosition();
               
               // change velocity to scan value
               props_.setPropValue(piDevice, Properties.Keys.VELOCITY, (float)requestedMotorSpeed);
               actualMotorSpeed = props_.getPropValueFloat(piDevice, Properties.Keys.VELOCITY);
               
               // dynamically generate macro and then send it to PI controller
               final String endPosStr = Double.toString(scanDistance_/2/1000.0).substring(0, 10);  // position specified in mm
               final String[] macroText;
               if (!lastPosStr_.equals(endPosStr)) {
                  // only send macro if it has changed
                  lastPosStr_ = endPosStr;
                  macroText = new String[] {
                        "MAC BEG " + MACRO_NAME_SCAN  ,  // define new macro
                        "DIO 2 0"                     ,  // set digital output #2 to go low
                        "MOV 1 " + endPosStr          ,  // initiate move to end position
                        "DEL " + rampTimeMs           ,  // wait for the ramp time we added
                        "DIO 2 1"                     ,  // set digital output #2 to go high
                        "DEL 100"                     ,  // wait 100ms (long for troubleshooting, ASI controller triggered on rising edge)
                        "DIO 2 0"                     ,  // set digital output #2 to go low again
                        "MAC END"                     ,  // end definition
                  };

                  // actually send macro over serial
                  try {
                     for (String s : macroText) {
                        props_.setPropValueDirect(controllerDeviceName, Properties.Keys.SEND_COMMAND, s);
                     }
                  } catch (Exception e) {
                     MyDialogUtils.showError("Could not send macro to PI controller.");
                  }
               }
            } else {
               MyDialogUtils.showError("Supplemental stage " + devices_.getMMDevice(Devices.Keys.SUPPLEMENTAL_X).toString()
                     + " is not supported for stage scanning.");
               return false;
            }
            
            if (devices_.isSingle7ChPLogic()) {
               MyDialogUtils.showError("Supplemental stage scan not compatible with 7 channel laser trigger");
               return false;
            }
            
            // configure input #3 as an input; should be connected in real world to PI digital output #2
            props_.setPropValue(Devices.Keys.PLOGIC, Properties.Keys.PLOGIC_POINTER_POSITION, triggerInAddr);
            props_.setPropValue(Devices.Keys.PLOGIC, Properties.Keys.PLOGIC_EDIT_CELL_TYPE, Properties.Values.PLOGIC_IO_INPUT);
            
            // set backplane signal
            // NB: for proper operation the jumper from the XY card should be removed so it isn't contending for the same wire
            //   (the PLC can be pull-down but the XY card is push-pull)
            // choose to do push-pull here for modicum of safety if the jumper isn't removed
            // just pass through PI TTL signal directly onto backplane; don't do one-shot or debounce or anything
            props_.setPropValue(Devices.Keys.PLOGIC, Properties.Keys.PLOGIC_POINTER_POSITION, triggerSPIMAddr);
            props_.setPropValue(Devices.Keys.PLOGIC, Properties.Keys.PLOGIC_EDIT_CELL_TYPE, Properties.Values.PLOGIC_IO_OUT_OPENDRAIN);
            props_.setPropValue(Devices.Keys.PLOGIC, Properties.Keys.PLOGIC_EDIT_CELL_CONFIG, triggerInAddr);
            
            
         } else {    // scanning with ASI stage
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

            final double maxMotorSpeed = props_.getPropValueFloat(Devices.Keys.XYSTAGE, Properties.Keys.STAGESCAN_MAX_MOTOR_SPEED_X);
            if (requestedMotorSpeed > maxMotorSpeed*0.8) {  // trying to go near max speed smooth scanning will be compromised
               MyDialogUtils.showError("Required stage speed is too fast, please reduce step size or increase sample exposure.");
               return false;
            }
            if (requestedMotorSpeed < maxMotorSpeed/2000) {  // 1/2000 of the max speed is approximate place where smooth scanning breaks down (speed quantum is ~1/12000 max speed); this also prevents setting to 0 which the controller rejects
               MyDialogUtils.showError("Required stage speed is too slow, please increase step size or decrease sample exposure.");
               return false;
            }
            props_.setPropValue(xyDevice, Properties.Keys.STAGESCAN_MOTOR_SPEED_X, (float)requestedMotorSpeed);

            // ask for the actual speed to calculate the actual step size
            actualMotorSpeed = props_.getPropValueFloat(xyDevice, Properties.Keys.STAGESCAN_MOTOR_SPEED_X_MICRONS)/1000;
            
            // set the acceleration to a reasonable value for the (usually very slow) scan speed
            props_.setPropValue(xyDevice, Properties.Keys.STAGESCAN_MOTOR_ACCEL_X, (float)computeScanAcceleration(actualMotorSpeed));
            
            // set the scan pattern and number of scans appropriately
            int numLines = settings.numSides;
            if (isInterleaved) {
               numLines = 1;  // assure in acquisition code that we can't have single-sided interleaved
            }
            numLines *= (settings.numChannels / computeScanChannelsPerPass(settings));
            props_.setPropValue(xyDevice, Properties.Keys.STAGESCAN_NUMLINES, numLines);
            props_.setPropValue(xyDevice, Properties.Keys.STAGESCAN_PATTERN,
                  ((settings.spimMode == AcquisitionModes.Keys.STAGE_SCAN) && (settings.numSides == 2)
                        ? Properties.Values.SERPENTINE : Properties.Values.RASTER));
            props_.setPropValue(xyDevice, Properties.Keys.STAGESCAN_SETTLING_TIME, settings.delayBeforeSide);
            
            if (!props_.getPropValueString(xyDevice, Properties.Keys.XYSTAGE_X_POLARITY).equals(Properties.Values.NORMAL.toString())) {
               MyDialogUtils.showError("Stage scanning requires X axis polarity set to normal");
               return false;
            }
         }

         // cache how far we scan each pass for later use
         DeviceUtils du = new DeviceUtils(gui_, devices_, props_, prefs_);
         actualStepSizeUm_ = settings.stepSizeUm * (actualMotorSpeed / requestedMotorSpeed);
         scanDistance_ = settings.numSlices * actualStepSizeUm_ * du.getStageGeometricSpeedFactor(settings.firstSideIsA);

         if (!settings.useMultiPositions) {
            // use current position as center position for stage scanning
            // multi-position situation is handled in position-switching code instead
            Point2D.Double posUm;
            try {
               posUm = core_.getXYStagePosition(devices_.getMMDevice(xyDevice));
            } catch (Exception ex) {
               MyDialogUtils.showError("Could not get XY stage position for stage scan initialization");
               return false;
            }
            prepareStageScanForAcquisition(posUm.x, posUm.y, settings.spimMode);
         }
         // TODO handle other multichannel modes with stage scanning (what does this mean??)
      } else {
         scanDistance_ = 0;
      }
      
      if (settings.spimMode == AcquisitionModes.Keys.EXT_TRIG_ACQ) {
         
         if (devices_.isSingle7ChPLogic()) {
            MyDialogUtils.showError("Externally-triggered acquisition not compatible with 7 channel laser trigger");
            return false;
         }
         
         // assume external trigger connected to PLC input #3
         // the jumper from the XY card should be removed so it isn't contending for the same wire
         //   (the PLC can be pull-down but the XY card is push-pull)
         // choose to do push-pull here for modicum of safety if the jumper isn't removed
         // just pass through trigger signal directly onto backplane; don't do one-shot or debounce or anything
         
         // configure input #3 as an input
         props_.setPropValue(Devices.Keys.PLOGIC, Properties.Keys.PLOGIC_POINTER_POSITION, triggerInAddr);
         props_.setPropValue(Devices.Keys.PLOGIC, Properties.Keys.PLOGIC_EDIT_CELL_TYPE, Properties.Values.PLOGIC_IO_INPUT);
         
         // set backplane signal
         props_.setPropValue(Devices.Keys.PLOGIC, Properties.Keys.PLOGIC_POINTER_POSITION, triggerSPIMAddr);
         props_.setPropValue(Devices.Keys.PLOGIC, Properties.Keys.PLOGIC_EDIT_CELL_TYPE, Properties.Values.PLOGIC_IO_OUT_OPENDRAIN);
         props_.setPropValue(Devices.Keys.PLOGIC, Properties.Keys.PLOGIC_EDIT_CELL_CONFIG, triggerInAddr);
      }
      
      // sets PLogic "acquisition running" flag in the "main" PLOGIC device
      props_.setPropValue(Devices.Keys.PLOGIC, Properties.Keys.PLOGIC_PRESET, Properties.Values.PLOGIC_PRESET_3, true);
      
      ReportingUtils.logMessage("Finished preparing controller for acquisition with offset " + channelOffset +
            " with mode " + settings.spimMode.toString() + " and settings " + settings.toString());
      
      return true;
   }
   
   /**
    * Compute appropriate motor speed in mm/s for the given stage scanning settings
    * @param settings
    * @return
    */
   public double computeScanSpeed(AcquisitionSettings settings) {
      double sliceDuration = settings.sliceTiming.sliceDuration;
      if (settings.spimMode == AcquisitionModes.Keys.STAGE_SCAN_INTERLEAVED) {
         // pretend like our slice takes twice as long so that we move the correct speed
         // this has the effect of halving the motor speed
         // but keeping the scan distance the same
         sliceDuration *= 2;
      }
      final int channelsPerPass = computeScanChannelsPerPass(settings);
      DeviceUtils du = new DeviceUtils(gui_, devices_, props_, prefs_);
      return settings.stepSizeUm * du.getStageGeometricSpeedFactor(settings.firstSideIsA) / sliceDuration / channelsPerPass;
   }
   
   /**
    * compute how many channels we do in each one-way scan
    * @param settings
    * @return
    */
   private int computeScanChannelsPerPass(AcquisitionSettings settings) {
      return settings.channelMode == MultichannelModes.Keys.SLICE_HW ? settings.numChannels : 1;
   }
   
   /**
    * Compute appropriate acceleration time in ms for the specified motor speed.
    * Set to be 10ms + 0-100ms depending on relative speed to max, all scaled by factor specified on the settings panel
    * @param motorSpeed
    * @return
    */
   public double computeScanAcceleration(double motorSpeed) {
      final double maxMotorSpeed = props_.getPropValueFloat(Devices.Keys.XYSTAGE, Properties.Keys.STAGESCAN_MAX_MOTOR_SPEED_X);
      final double accelFactor = props_.getPropValueFloat(Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_STAGESCAN_ACCEL_FACTOR);
      return (10 + 100 * (motorSpeed / maxMotorSpeed) ) * accelFactor;
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
    * @param channelOffset
    * @param offset only: only set the slice offset and not refresh everything else
    * 
    * @return false if there was some error that should abort acquisition
    */
   private boolean prepareControllerForAquisition_Side(
         final Devices.Sides side, 
         final AcquisitionSettings settings,
         final double channelOffset,
         final boolean offsetOnly
         ) {

      Devices.Keys galvoDevice = Devices.getSideSpecificKey(Devices.Keys.GALVOA, side);
      Devices.Keys piezoDevice = Devices.getSideSpecificKey(Devices.Keys.PIEZOA, side);
      Devices.Keys cameraDevice = Devices.getSideSpecificKey(Devices.Keys.CAMERAA, side);
      
      // if ignore piezo is checked then pretend we don't have a piezo unless acquisition mode requires it 
      if (prefs_.getBoolean(MyStrings.PanelNames.SETTINGS.toString(), 
            Properties.Keys.PLUGIN_PIEZO_IGNORE_STAGE_SLICE_SCAN, false)
            && 
            !(settings.spimMode == AcquisitionModes.Keys.PIEZO_SLICE_SCAN || 
            settings.spimMode == AcquisitionModes.Keys.PIEZO_SCAN_ONLY)) {
         piezoDevice = Devices.Keys.NONE;
      }
      
      boolean skipScannerWarnings = getSkipScannerWarnings(galvoDevice);
      
      if (!offsetOnly) {

         Properties.Keys widthProp = (side == Devices.Sides.A) ?
               Properties.Keys.PLUGIN_SHEET_WIDTH_A : Properties.Keys.PLUGIN_SHEET_WIDTH_B;
         Properties.Keys offsetProp = (side == Devices.Sides.A) ?
               Properties.Keys.PLUGIN_SHEET_OFFSET_A : Properties.Keys.PLUGIN_SHEET_OFFSET_B;

         // save sheet width and offset which may get clobbered
         props_.setPropValue(Devices.Keys.PLUGIN, widthProp, 
               props_.getPropValueFloat(galvoDevice, Properties.Keys.SA_AMPLITUDE_X_DEG), skipScannerWarnings);
         props_.setPropValue(Devices.Keys.PLUGIN, offsetProp, 
               props_.getPropValueFloat(galvoDevice, Properties.Keys.SA_OFFSET_X_DEG), skipScannerWarnings);

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
         if (delayRepeats > 32000) {  // not sure if this is actually the limit, but is conservative and this is really corner case anyway
            MyDialogUtils.showError("Cannot use hardware timepoints with too-large interval.");
            return false;
         }
         props_.setPropValue(galvoDevice, Properties.Keys.SPIM_DELAY_REPEATS, delayRepeats, skipScannerWarnings);
         props_.setPropValue(galvoDevice, Properties.Keys.SPIM_NUM_REPEATS, numVolumesPerTrigger, skipScannerWarnings);

         props_.setPropValue(galvoDevice, Properties.Keys.SPIM_DELAY_SIDE,
               settings.isStageScanning ? 0 : // minimal delay on micro-mirror card for stage scanning (can't actually be less than 2ms but this will get as small as possible)
                  props_.getPropValueFloat(Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_DELAY_BEFORE_SIDE),  // this is usual behavior
                  skipScannerWarnings);
      
      } // end if (!offsetOnly)
      
      // figure out the piezo parameters
      float piezoCenter;
      if (settings.isStageScanning && devices_.isValidMMDevice(piezoDevice)) {
         // for stage scanning we define the piezo position to be the home position (normally 0)
         // this is basically required for interleaved mode (otherwise piezo would be moving every slice)
         //    and by convention we'll do it for all stage scanning
         piezoCenter =  props_.getPropValueFloat(piezoDevice, Properties.Keys.HOME_POSITION)*1000;  // *1000 to convert mm to um
      } else {
         if (settings.centerAtCurrentZ) {
            piezoCenter = (float) positions_.getUpdatedPosition(piezoDevice, Joystick.Directions.NONE);
         } else {
            piezoCenter = prefs_.getFloat(
                  MyStrings.PanelNames.SETUP.toString() + side.toString(), 
                  Properties.Keys.PLUGIN_PIEZO_CENTER_POS, 0.0f);
         }
      }

      // if we set piezoAmplitude to 0 here then sliceAmplitude will also be 0
      float piezoAmplitude;
      if (settings.isStageScanning || (settings.spimMode == AcquisitionModes.Keys.NO_SCAN)) {
         piezoAmplitude = 0.0f;
      } else {
         piezoAmplitude = (settings.numSlices - 1) * settings.stepSizeUm;
      }

      // use this instead of settings.numSlices from here on out because
      // we modify it if we are taking "extra slice" for synchronous/overlap
      int numSlicesHW = settings.numSlices;
      
      // tweak the parameters if we are using synchronous/overlap mode
      // object is to get exact same piezo/scanner positions in first
      // N frames (piezo/scanner will move to N+1st position but no image taken)
      final CameraModes.Keys cameraMode = settings.cameraMode;
      if (cameraMode == CameraModes.Keys.OVERLAP) {
         piezoAmplitude *= ((float)numSlicesHW)/((float)numSlicesHW-1f);
         piezoCenter += piezoAmplitude/(2*numSlicesHW);
         numSlicesHW += 1;
      }
      
      float sliceRate = prefs_.getFloat(
            MyStrings.PanelNames.SETUP.toString() + side.toString(), 
            Properties.Keys.PLUGIN_RATE_PIEZO_SHEET, 100);
      if (MyNumberUtils.floatsEqual(sliceRate, 0.0f)) {
         MyDialogUtils.showError("Calibration slope for side " + side.toString() + 
               " cannot be zero. Re-do calibration on Setup tab.");
         return false;
      }
      float sliceOffset = (float)channelOffset + prefs_.getFloat(
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
            float actualPiezoCenter = piezoCenter - piezoAmplitude/(2*(numSlicesHW-1));
            sliceCenter = (actualPiezoCenter - sliceOffset) / sliceRate;
         }
         sliceAmplitude = 0.0f;
      }
      // round to nearest 0.0001 degrees, which is approximately the DAC resolution
      sliceAmplitude = MyNumberUtils.roundFloatToPlace(sliceAmplitude, 4);
      sliceCenter = MyNumberUtils.roundFloatToPlace(sliceCenter, 4);
      
      final float sliceMax = sliceCenter + ((sliceAmplitude > 0) ? sliceAmplitude/2 : -1*sliceAmplitude/2);
      final float sliceMin = sliceCenter - ((sliceAmplitude > 0) ? sliceAmplitude/2 : -1*sliceAmplitude/2);
      if (sliceMax > props_.getPropValueFloat(galvoDevice, Properties.Keys.SCANNER_MAX_LIMIT_Y)) {
         MyDialogUtils.showError("Scanner will exceed allowed range in positive direction.");
         return false;
      }
      if (sliceMin < props_.getPropValueFloat(galvoDevice, Properties.Keys.SCANNER_MIN_LIMIT_Y)) {
         MyDialogUtils.showError("Scanner will exceed allowed range in negative direction.");
         return false;
      }
      
      if (offsetOnly) {
         if (!ASIdiSPIM.SCOPE) {
            props_.setPropValue(galvoDevice, Properties.Keys.SA_OFFSET_Y_DEG,
                  sliceCenter, skipScannerWarnings);
         }
      } else {  // normal case

         final boolean smoothSlicePlugin = prefs_.getBoolean(MyStrings.PanelNames.SETTINGS.toString(),
               Properties.Keys.PLUGIN_SMOOTH_SLICE_SCAN, false);
         props_.setPropValue(galvoDevice, Properties.Keys.SPIM_SMOOTH_SLICE_ENABLE, 
               smoothSlicePlugin ? Properties.Values.YES : Properties.Values.NO, true); // ignore error
         
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
         if (!ASIdiSPIM.SCOPE) {  // for single-objective we don't want to touch the galvo offset
            props_.setPropValue(galvoDevice, Properties.Keys.SA_OFFSET_Y_DEG,
                  sliceCenter, skipScannerWarnings);
         }
         props_.setPropValue(galvoDevice, Properties.Keys.SPIM_NUM_SLICES,
               numSlicesHW, skipScannerWarnings);
         props_.setPropValue(galvoDevice, Properties.Keys.SPIM_NUM_SIDES,
               settings.numSides, skipScannerWarnings);
         props_.setPropValue(galvoDevice, Properties.Keys.SPIM_FIRSTSIDE,
               settings.firstSideIsA ? "A" : "B", skipScannerWarnings);

         // get the piezo card ready; skip if no piezo specified
         // need to do this for stage scanning too, which makes sure the piezo amplitude is 0
         if (devices_.isValidMMDevice(piezoDevice)) {
            // if mode SLICE_SCAN_ONLY we have computed slice movement as if we
            //   were moving the piezo but now make piezo stay still
            if (settings.spimMode.equals(AcquisitionModes.Keys.SLICE_SCAN_ONLY)) {
               // if we artificially shifted centers due to extra trigger and only moving piezo
               // then move galvo center back to where it would have been
               if (cameraMode == CameraModes.Keys.OVERLAP) {
                  piezoCenter -= piezoAmplitude/(2*(numSlicesHW-1));
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
                  Properties.Keys.SA_AMPLITUDE, piezoAmplitude, false, true);  // force-set value
            props_.setPropValue(piezoDevice,
                  Properties.Keys.SA_OFFSET, piezoCenter, false, true);  // force-set value
            
            if (!settings.isStageScanning) {
               props_.setPropValue(piezoDevice,
                     Properties.Keys.SPIM_NUM_SLICES, numSlicesHW);
               props_.setPropValue(piezoDevice,
                     Properties.Keys.SPIM_STATE, Properties.Values.SPIM_ARMED);
            }
         }

         // TODO figure out what we should do with piezo illumination/center position during stage scan

         // set up stage scan parameters if necessary
         if (settings.isStageScanning && !ASIdiSPIM.SCOPE) {  // for SCOPE leave piezo the way it is
            // TODO update UI to hide image center control for stage scanning
            // for interleaved stage scanning there will never be "home" pulse and for normal stage scanning
            //   the first side piezo will never get moved into position either so do both manually (for
            //   simplicity ignore fact that one of two is unnecessary for two-sided normal stage scan acquisition)
            try {
               if (devices_.isValidMMDevice(piezoDevice)) {
                  core_.home(devices_.getMMDevice(piezoDevice));
               }
            } catch (Exception e) {
               ReportingUtils.showError(e, "Could not move piezo to home");
            }
         }

         final boolean isInterleaved = (settings.isStageScanning && 
               settings.spimMode == AcquisitionModes.Keys.STAGE_SCAN_INTERLEAVED);

         // even though we have moved piezos to home position let's still tell firmware
         //    not to move piezos anywhere (i.e. maybe setting "home disable" to true doesn't have any really effect)
         if (isInterleaved) {
            props_.setPropValue(galvoDevice, Properties.Keys.SPIM_PIEZO_HOME_DISABLE,
                  Properties.Values.YES, skipScannerWarnings);
         } else {
            props_.setPropValue(galvoDevice, Properties.Keys.SPIM_PIEZO_HOME_DISABLE,
                  Properties.Values.NO, skipScannerWarnings);
         }

         // set interleaved sides flag low unless we are doing interleaved stage scan
         if (isInterleaved) {
            props_.setPropValue(galvoDevice, Properties.Keys.SPIM_INTERLEAVE_SIDES,
                  Properties.Values.YES, skipScannerWarnings); // make sure to check for errors
         } else {
            props_.setPropValue(galvoDevice, Properties.Keys.SPIM_INTERLEAVE_SIDES,
                  Properties.Values.NO, true);  // ignore errors b/c older firmware won't have it
         }

         // send sheet width/offset
         float sheetWidth = getSheetWidth(settings.cameraMode, cameraDevice, side);
         float sheetOffset = getSheetOffset(settings.cameraMode, side);
         if (settings.cameraMode == CameraModes.Keys.LIGHT_SHEET) {
            // adjust sheet width and offset to account for settle time where scan is going but we aren't imaging yet
            final float settleTime = props_.getPropValueFloat(Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_LS_SCAN_SETTLE);
            // infer the main scan time (during imaging) from the laser duration
            final float readoutTime = settings.sliceTiming.laserDuration - 0.25f;  // -0.25 is for scanLaserBufferTime
            // offset should be decreased by half of the distance traveled during settle time (instead of re-extracting slope use existing sheetWidth/readoutTime)
            sheetOffset -= (sheetWidth * settleTime/readoutTime)/2;
            // width should be increased by ratio (1 + settle_fraction) 
            sheetWidth += (sheetWidth * settleTime/readoutTime);
         }
         
         if (!ASIdiSPIM.SCOPE) {  // for SCOPE don't want to touch settings for galvo's X which we use for static offset
            props_.setPropValue(galvoDevice, Properties.Keys.SA_AMPLITUDE_X_DEG, sheetWidth);
            props_.setPropValue(galvoDevice, Properties.Keys.SA_OFFSET_X_DEG, sheetOffset);
         }

      } // end else (offsetOnly == true)
      
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
         final AcquisitionSettings settings,
         final boolean centerPiezos
         ) {
      
      // this only happens after images have all been received (or timeout occurred)

      // clear "acquisition running" flag on PLC
      props_.setPropValue(new Devices.Keys[]{Devices.Keys.PLOGIC, Devices.Keys.PLOGIC_LASER},
            Properties.Keys.PLOGIC_PRESET, Properties.Values.PLOGIC_PRESET_2, true);
      
      if ((settings.numSides > 1) || settings.firstSideIsA) {
         boolean success = cleanUpControllerAfterAcquisition_Side(
               Devices.Sides.A, centerPiezos, 0.0f);
         if (!success) {
            return false;
         }
      }
      if ((settings.numSides > 1) || !settings.firstSideIsA) {
         boolean success = cleanUpControllerAfterAcquisition_Side(
               Devices.Sides.B, centerPiezos, 0.0f);
         if (!success) {
            return false;
         }
      }

      // special cleanup for start/stop or stepping mode
      if (settings.isStageStepping) {
         // prevent more pulses on #8 if we are using stepping
         props_.setPropValue(Devices.Keys.PLOGIC_LASER, Properties.Keys.PLOGIC_POINTER_POSITION, triggerStepOutputAddr);
         props_.setPropValue(Devices.Keys.PLOGIC_LASER, Properties.Keys.PLOGIC_EDIT_CELL_CONFIG, zeroAddr);
      }

      // special cleanup for PI stage in scan mode
      if (settings.spimMode == AcquisitionModes.Keys.STAGE_SCAN_SUPPLEMENTAL_UNIDIRECTIONAL
            && devices_.getMMDeviceLibrary(Devices.Keys.SUPPLEMENTAL_X) == Devices.Libraries.PI_GCS_2) {
         // if we co-opted the backplane for triggering for stage scan supplemental, then release it
         props_.setPropValue(Devices.Keys.PLOGIC, Properties.Keys.PLOGIC_POINTER_POSITION, triggerSPIMAddr);
         props_.setPropValue(Devices.Keys.PLOGIC, Properties.Keys.PLOGIC_EDIT_CELL_TYPE, Properties.Values.PLOGIC_IO_INPUT);
         props_.setPropValue(Devices.Keys.PLOGIC, Properties.Keys.PLOGIC_EDIT_CELL_CONFIG, triggerInAddr);
         // also set velocity back to original value
         props_.setPropValue(Devices.Keys.SUPPLEMENTAL_X, Properties.Keys.VELOCITY, (float)supOrigSpeed_);
         // finally go back to start position
         moveSupplementalToStartPosition();
      }
      
      // clean up planar correction if needed
      if (!zSpeedZero_) {
         props_.setPropValue(Devices.Keys.UPPERZDRIVE, Properties.Keys.TTLINPUT_MODE, Properties.Values.TTLINPUT_MODE_NONE);
         zSpeedZero_ = true;
      }
      
      ReportingUtils.logMessage("Finished controller cleanup after acquisition");
      
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
      boolean skipScannerWarnings = getSkipScannerWarnings(galvoDevice);
      
      // make sure SPIM state machine is stopped; device adapter takes care of querying
      props_.setPropValue(galvoDevice, Properties.Keys.SPIM_STATE,
               Properties.Values.SPIM_IDLE, true);
      props_.setPropValue(piezoDevice, Properties.Keys.SPIM_STATE,
            Properties.Values.SPIM_IDLE, true);
      
      Properties.Keys widthProp = (side == Devices.Sides.A) ?
            Properties.Keys.PLUGIN_SHEET_WIDTH_A : Properties.Keys.PLUGIN_SHEET_WIDTH_B;
      Properties.Keys offsetProp = (side == Devices.Sides.A) ?
            Properties.Keys.PLUGIN_SHEET_OFFSET_A : Properties.Keys.PLUGIN_SHEET_OFFSET_B;
      
      // restore sheet width and offset in case they got clobbered by the code implementing light sheet mode
      props_.setPropValue(galvoDevice, Properties.Keys.SA_AMPLITUDE_X_DEG,
            props_.getPropValueFloat(Devices.Keys.PLUGIN, widthProp), skipScannerWarnings);
      props_.setPropValue(Devices.Keys.PLUGIN, offsetProp, 
            props_.getPropValueFloat(galvoDevice, Properties.Keys.SA_OFFSET_X_DEG), skipScannerWarnings);
      
      // move piezo back to desired position
      if (movePiezo && devices_.isValidMMDevice(piezoDevice)) {
         positions_.setPosition(piezoDevice, piezoPosition, true); 
      }

      // make sure we stop SPIM and SCAN state machines every time we trigger controller (in AcquisitionPanel code)
      
      return true;
   }
   
   
   /**
    * Programs the PLogic card for hardware channel switching
    * according to the selections in the Multichannel subpanel
    * @param settings
    * @return false if there is a fatal error, true if successful
    */
   public boolean setupHardwareChannelSwitching(final AcquisitionSettings settings, boolean hideErrors) {
      
      if (!devices_.isValidMMDevice(Devices.Keys.PLOGIC_LASER)) {
         MyDialogUtils.showError("PLogic card required for hardware switching", hideErrors);
         return false;
      }
      
      MultichannelModes.Keys channelMode = settings.channelMode;
      
      if ((settings.numChannels > 4) &&
            ((channelMode == MultichannelModes.Keys.SLICE_HW) || 
            (channelMode == MultichannelModes.Keys.VOLUME_HW)) ) {
         MyDialogUtils.showError("PLogic card cannot handle more than 4 channels for hardware switching.", hideErrors);
         return false;
      }
      
      // set up clock for counters
      switch (channelMode) {
      case SLICE_HW:
         props_.setPropValue(Devices.Keys.PLOGIC_LASER, Properties.Keys.PLOGIC_PRESET,
               Properties.Values.PLOGIC_PRESET_CLOCK_LASER);
         break;
      case VOLUME_HW:
         if (settings.firstSideIsA) {
            props_.setPropValue(Devices.Keys.PLOGIC_LASER, Properties.Keys.PLOGIC_PRESET,
                  Properties.Values.PLOGIC_PRESET_CLOCK_SIDE_AFIRST);
         } else {
            props_.setPropValue(Devices.Keys.PLOGIC_LASER, Properties.Keys.PLOGIC_PRESET,
                  Properties.Values.PLOGIC_PRESET_CLOCK_SIDE_BFIRST);
         }
         break;
      default:
         MyDialogUtils.showError("Unknown multichannel mode for hardware switching", hideErrors);
         return false;
      }
      
      // set up hardware counter
      switch (settings.numChannels) {
      case 1:
         props_.setPropValue(Devices.Keys.PLOGIC_LASER, Properties.Keys.PLOGIC_PRESET,
               Properties.Values.PLOGIC_PRESET_COUNT_1);
         break;
      case 2:
         props_.setPropValue(Devices.Keys.PLOGIC_LASER, Properties.Keys.PLOGIC_PRESET,
               Properties.Values.PLOGIC_PRESET_COUNT_2);
         break;
      case 3:
         props_.setPropValue(Devices.Keys.PLOGIC_LASER, Properties.Keys.PLOGIC_PRESET,
               Properties.Values.PLOGIC_PRESET_COUNT_3);
         break;
      case 4:
         props_.setPropValue(Devices.Keys.PLOGIC_LASER, Properties.Keys.PLOGIC_PRESET,
               Properties.Values.PLOGIC_PRESET_COUNT_4);
         break;
      default:
         MyDialogUtils.showError("Hardware channel switching only supports 1-4 channels", hideErrors);
         return false;
      }
      
      // speed things up by turning off updates, will restore value later
      String editCellUpdates = props_.getPropValueString(Devices.Keys.PLOGIC_LASER, Properties.Keys.PLOGIC_EDIT_CELL_UPDATES);
      if (!editCellUpdates.equals(Properties.Values.NO.toString())) {
         props_.setPropValue(Devices.Keys.PLOGIC_LASER, Properties.Keys.PLOGIC_EDIT_CELL_UPDATES, Properties.Values.NO);
      }
      
      // make sure the counters get reset on the acquisition start flag
      // turns out we can only do this for 2-counter and 4-counter implemented with D-flops
      // TODO figure out alternative for 3-position counter
      if (settings.numChannels != 3) {
         props_.setPropValue(Devices.Keys.PLOGIC_LASER, Properties.Keys.PLOGIC_POINTER_POSITION, counterLSBAddr);
         props_.setPropValue(Devices.Keys.PLOGIC_LASER, Properties.Keys.PLOGIC_EDIT_CELL_INPUT_3, acquisitionFlagAddr + edgeAddr);
         props_.setPropValue(Devices.Keys.PLOGIC_LASER, Properties.Keys.PLOGIC_POINTER_POSITION, counterMSBAddr);
         props_.setPropValue(Devices.Keys.PLOGIC_LASER, Properties.Keys.PLOGIC_EDIT_CELL_INPUT_3, acquisitionFlagAddr + edgeAddr);
      }

      
      // there are 2 separate 7-channel cases different in the property value for PLOGIC_MODE "PLogicMode"
      // 1. (original) with 7-channel laser on own PLogic card, seems to have some odd things that I won't change including only uses 6 lasers
      // 2. (newer) with 7-channel TTL-triggered on PLogic card shared with single camera trigger output (i.e. not dual-view system)
      // however they share some things like using cells 17-24 and building a 3-input LUT which code is just copy/paste right now
      
      final boolean sevenChannelorig = props_.getPropValueString(Devices.Keys.PLOGIC_LASER, Properties.Keys.PLOGIC_MODE).
            equals(Properties.Values.SHUTTER_7CHANNEL.toString());
      final boolean sevenChannelttl = props_.getPropValueString(Devices.Keys.PLOGIC_LASER, Properties.Keys.PLOGIC_MODE).
            equals(Properties.Values.SPIM_7CH_SHUTTER.toString());
      
      if (sevenChannelorig) {  // original special 7-channel case
         
         if(props_.getPropValueInteger(Devices.Keys.PLOGIC_LASER, Properties.Keys.PLOGIC_NUMCLOGICELLS) < 24) {
            // restore update setting
            props_.setPropValue(Devices.Keys.PLOGIC_LASER, Properties.Keys.PLOGIC_EDIT_CELL_UPDATES, editCellUpdates);
            MyDialogUtils.showError("Require 24-cell PLC firmware to use hardware channel swiching with 7-channel shutter", hideErrors);
            return false;
         }
         
         // make sure cells 17-24 are controlling BNCs 1-8
         props_.setPropValue(Devices.Keys.PLOGIC_LASER, Properties.Keys.PLOGIC_PRESET,
               Properties.Values.PLOGIC_PRESET_BNC1_8_ON_17_24);
         
         // now set cells 17-22 so they reflect the counter state used to track state as well as the global laser trigger
         // NB that this only uses 6 lasers (we need 2 free BNCs, BNC#7 for FW trigger and BNC#8 for supplemental X trigger
         for (int laserNum = 1; laserNum < 7; ++laserNum) {
            props_.setPropValue(Devices.Keys.PLOGIC_LASER, Properties.Keys.PLOGIC_POINTER_POSITION, laserNum + 16);
            props_.setPropValue(Devices.Keys.PLOGIC_LASER, Properties.Keys.PLOGIC_EDIT_CELL_TYPE, Properties.Values.PLOGIC_LUT3);
            int lutValue = 0;
            // populate a 3-input lookup table with the combinations of lasers present
            // the LUT "MSB" is the laserTrigger, then the counter MSB, then the counter LSB
            for (int channelNum = 0; channelNum < settings.numChannels; ++channelNum) {
               if (doesPLogicChannelIncludeLaser(laserNum, settings.channels[channelNum], settings.channelGroup)) {
                  lutValue += Math.pow(2, channelNum + 4);  // LUT adds 2^(code in decimal) for each setting, but trigger is MSB of this code
               }
            }
            props_.setPropValue(Devices.Keys.PLOGIC_LASER, Properties.Keys.PLOGIC_EDIT_CELL_CONFIG, lutValue);
            props_.setPropValue(Devices.Keys.PLOGIC_LASER, Properties.Keys.PLOGIC_EDIT_CELL_INPUT_1, counterLSBAddr);
            props_.setPropValue(Devices.Keys.PLOGIC_LASER, Properties.Keys.PLOGIC_EDIT_CELL_INPUT_2, counterMSBAddr);
            props_.setPropValue(Devices.Keys.PLOGIC_LASER, Properties.Keys.PLOGIC_EDIT_CELL_INPUT_3, laserTriggerAddress);
         }

      } else if(sevenChannelttl) {  // new 7-channel case with camera trigger on BNC #8
         
         if(props_.getPropValueInteger(Devices.Keys.PLOGIC_LASER, Properties.Keys.PLOGIC_NUMCLOGICELLS) < 24) {
            // restore update setting
            props_.setPropValue(Devices.Keys.PLOGIC_LASER, Properties.Keys.PLOGIC_EDIT_CELL_UPDATES, editCellUpdates);
            MyDialogUtils.showError("Require 24-cell PLC firmware to use hardware channel swiching with 7-channel shutter", hideErrors);
            return false;
         }
         
         // set cells 17-24 to control BNCs 1-8, but then immediately change BNC8 to reflect camera (firmware ensures all are set to push-pull outputs)
         // note that the device adapter should have already set BNC8 to be the camera so this is just resetting it 
         props_.setPropValue(Devices.Keys.PLOGIC_LASER, Properties.Keys.PLOGIC_PRESET, Properties.Values.PLOGIC_PRESET_BNC1_8_ON_17_24);
         final int addrFrontPanel8 = 40;
         final int addrInternalTTLCameraA = 41;
         props_.setPropValue(Devices.Keys.PLOGIC, Properties.Keys.PLOGIC_POINTER_POSITION, addrFrontPanel8);  // address 40 is front panel #8
         props_.setPropValue(Devices.Keys.PLOGIC_LASER, Properties.Keys.PLOGIC_EDIT_CELL_TYPE, Properties.Values.PLOGIC_IO_OUT_PUSHPULL);
         props_.setPropValue(Devices.Keys.PLOGIC_LASER, Properties.Keys.PLOGIC_EDIT_CELL_CONFIG, addrInternalTTLCameraA);  // address 41 is internal TTL0 signal for CameraA
         
         // now set cells 17-23 so they reflect the counter state used to track state as well as the global laser trigger
         for (int laserNum = 1; laserNum <= 7; ++laserNum) {
            props_.setPropValue(Devices.Keys.PLOGIC_LASER, Properties.Keys.PLOGIC_POINTER_POSITION, laserNum + 16);
            props_.setPropValue(Devices.Keys.PLOGIC_LASER, Properties.Keys.PLOGIC_EDIT_CELL_TYPE, Properties.Values.PLOGIC_LUT3);
            int lutValue = 0;
            // populate a 3-input lookup table with the combinations of lasers present
            // the LUT "MSB" is the laserTrigger, then the counter MSB, then the counter LSB
            for (int channelNum = 0; channelNum < settings.numChannels; ++channelNum) {
               if (doesPLogicChannelIncludeLaser(laserNum, settings.channels[channelNum], settings.channelGroup)) {
                  lutValue += Math.pow(2, channelNum + 4);  // LUT adds 2^(code in decimal) for each setting, but trigger is MSB of this code
               }
            }
            props_.setPropValue(Devices.Keys.PLOGIC_LASER, Properties.Keys.PLOGIC_EDIT_CELL_CONFIG, lutValue);
            props_.setPropValue(Devices.Keys.PLOGIC_LASER, Properties.Keys.PLOGIC_EDIT_CELL_INPUT_1, counterLSBAddr);
            props_.setPropValue(Devices.Keys.PLOGIC_LASER, Properties.Keys.PLOGIC_EDIT_CELL_INPUT_2, counterMSBAddr);
            props_.setPropValue(Devices.Keys.PLOGIC_LASER, Properties.Keys.PLOGIC_EDIT_CELL_INPUT_3, laserTriggerAddress);
         }
         
      } else { // original 4-channel case with camera triggers on 1/2, side select on 4, laser on 5-8

         // initialize cells 13-16 which control BNCs 5-8
         for (int cellNum=13; cellNum<=16; cellNum++) {
            props_.setPropValue(Devices.Keys.PLOGIC_LASER, Properties.Keys.PLOGIC_POINTER_POSITION, cellNum);
            props_.setPropValue(Devices.Keys.PLOGIC_LASER, Properties.Keys.PLOGIC_EDIT_CELL_TYPE, Properties.Values.PLOGIC_AND4);
            props_.setPropValue(Devices.Keys.PLOGIC_LASER, Properties.Keys.PLOGIC_EDIT_CELL_INPUT_2, laserTriggerAddress);
            // note that PLC diSPIM assumes "laser + side" output mode is selected for micro-mirror card
         }

         // identify BNC from the preset and set counter inputs for 13-16 appropriately 
         boolean[] hardwareChannelUsed = new boolean[4]; // initialized to all false
         for (int channelNum = 0; channelNum < settings.numChannels; channelNum++) {
            // we already know there are between 1 and 4 channels
            int outputNum = getPLogicOutputFromChannel(settings.channels[channelNum], settings.channelGroup);
            // TODO handle case where we have multiple simultaneous outputs, e.g. outputs 6/7 together
            if (outputNum<5) {  // check for error in getPLogicOutputFromChannel()
               // restore update setting
               props_.setPropValue(Devices.Keys.PLOGIC_LASER, Properties.Keys.PLOGIC_EDIT_CELL_UPDATES, editCellUpdates);
               return false;  // already displayed error
            }
            // make sure we don't have multiple Micro-Manager channels using same hardware channel
            if (hardwareChannelUsed[outputNum-5]) {
               // restore update setting
               props_.setPropValue(Devices.Keys.PLOGIC_LASER, Properties.Keys.PLOGIC_EDIT_CELL_UPDATES, editCellUpdates);
               MyDialogUtils.showError("Multiple channels cannot use same laser for PLogic triggering", hideErrors);
               return false;
            } else {
               hardwareChannelUsed[outputNum-5] = true;
            }
            props_.setPropValue(Devices.Keys.PLOGIC_LASER, Properties.Keys.PLOGIC_POINTER_POSITION, outputNum + 8);
            props_.setPropValue(Devices.Keys.PLOGIC_LASER, Properties.Keys.PLOGIC_EDIT_CELL_INPUT_1, invertAddr);  // enable this AND4
            // if we are doing per-volume switching with side B first then counter will start at 1 instead of 0
            // the following lines account for this by incrementing the channel number "match" by 1 in this special case 
            int adjustedChannelNum = channelNum;
            if (channelMode == MultichannelModes.Keys.VOLUME_HW && !settings.firstSideIsA) {
               adjustedChannelNum = (channelNum+1) % settings.numChannels;
            }
            // map the channel number to the equivalent addresses for the AND4
            // inputs should be either 3 (for LSB high) or 67 (for LSB low)
            //                     and 4 (for MSB high) or 68 (for MSB low)
            final int in3 = (adjustedChannelNum & 0x01) > 0 ? counterLSBAddr : counterLSBAddr + invertAddr;
            final int in4 = (adjustedChannelNum & 0x02) > 0 ? counterMSBAddr : counterMSBAddr + invertAddr; 
            props_.setPropValue(Devices.Keys.PLOGIC_LASER, Properties.Keys.PLOGIC_EDIT_CELL_INPUT_3, in3);
            props_.setPropValue(Devices.Keys.PLOGIC_LASER, Properties.Keys.PLOGIC_EDIT_CELL_INPUT_4, in4);
         }

         // make sure cells 13-16 are controlling BNCs 5-8
         props_.setPropValue(Devices.Keys.PLOGIC_LASER, Properties.Keys.PLOGIC_PRESET,
               Properties.Values.PLOGIC_PRESET_BNC5_8_ON_13_16);
      }
      
      // restore update setting
      if (!editCellUpdates.equals(Properties.Values.NO.toString())) {
         props_.setPropValue(Devices.Keys.PLOGIC_LASER, Properties.Keys.PLOGIC_EDIT_CELL_UPDATES, editCellUpdates);
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
      final Devices.Keys galvoDevice = isFirstSideA ? Devices.Keys.GALVOA : Devices.Keys.GALVOB;
      switch (spimMode) {
      case STAGE_SCAN:
      case STAGE_SCAN_INTERLEAVED:
      case STAGE_SCAN_UNIDIRECTIONAL:
         // for stage scan we send trigger to stage card, which sends
         //    hardware trigger to the micro-mirror card
         props_.setPropValue(galvoDevice, Properties.Keys.SPIM_STATE, Properties.Values.SPIM_ARMED);
         props_.setPropValue(Devices.Keys.XYSTAGE, Properties.Keys.STAGESCAN_STATE, Properties.Values.SPIM_RUNNING);
         break;
      case STAGE_SCAN_SUPPLEMENTAL_UNIDIRECTIONAL:
         props_.setPropValue(galvoDevice, Properties.Keys.SPIM_STATE, Properties.Values.SPIM_ARMED);
         // initiate macro loaded onto controller in prepareControllerForAquisition()
         final String controllerDeviceName = props_.getPropValueString(Devices.Keys.SUPPLEMENTAL_X, Properties.Keys.CONTROLLER_NAME);
         props_.setPropValueDirect(controllerDeviceName, Properties.Keys.SEND_COMMAND, "MAC START " + MACRO_NAME_SCAN);
         break;
      case STAGE_STEP_SUPPLEMENTAL_UNIDIRECTIONAL:  // not synchronizing this with anything
      case PIEZO_SLICE_SCAN:
      case SLICE_SCAN_ONLY:
      case PIEZO_SCAN_ONLY:
      case NO_SCAN:
         // in actuality only matters which device we trigger if there are
         //   two micro-mirror cards, which hasn't ever been done in practice yet
         props_.setPropValue(galvoDevice, Properties.Keys.SPIM_STATE,
               Properties.Values.SPIM_RUNNING, getSkipScannerWarnings(galvoDevice));
         break;
      case EXT_TRIG_ACQ:
         props_.setPropValue(galvoDevice, Properties.Keys.SPIM_STATE, Properties.Values.SPIM_ARMED);
         break;
      default:
         MyDialogUtils.showError("Unknown acquisition mode");
         return false;
      }
      return true;
   }
  
   
   // TODO modify this so that it works with 7-channel TTL as well
   
   /**
    * Gets the associated PLogic BNC from the channel (containing preset name) 
    * @param channel
    * @return value 5, 6, 7, or 8; returns 0 if there is an error
    */
   private int getPLogicOutputFromChannel(ChannelSpec channel, String channelGroup) {
      try {
         Configuration configData = core_.getConfigData(channelGroup, channel.config_);
         if (!configData.isPropertyIncluded(devices_.getMMDevice(Devices.Keys.PLOGIC_LASER), Properties.Keys.PLOGIC_OUTPUT_CHANNEL.toString())) {
            MyDialogUtils.showError("Must include PLogic \"OutputChannel\" in preset for hardware switching");
            return 0;
         }
         String setting = configData.getSetting(devices_.getMMDevice(Devices.Keys.PLOGIC_LASER), Properties.Keys.PLOGIC_OUTPUT_CHANNEL.toString()).getPropertyValue();
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
   
   // TODO check whether this works after renaming the presets
   /**
    * Checks to see whether the PLogic channel includes the specified laser number
    * Checks based on string contents of channel property value which we hardcode in device adapter to include laser numbers  
    */
   private boolean doesPLogicChannelIncludeLaser(int laserNum, ChannelSpec channel, String channelGroup) {
      try {
         Configuration configData = core_.getConfigData(channelGroup, channel.config_);
         if (!configData.isPropertyIncluded(devices_.getMMDevice(Devices.Keys.PLOGIC_LASER), Properties.Keys.PLOGIC_OUTPUT_CHANNEL.toString())) {
            MyDialogUtils.showError("Must include PLogic \"OutputChannel\" in preset for hardware switching");
            return false;
         }
         String setting = configData.getSetting(devices_.getMMDevice(Devices.Keys.PLOGIC_LASER), Properties.Keys.PLOGIC_OUTPUT_CHANNEL.toString()).getPropertyValue();
         return setting.contains(String.valueOf(laserNum));
      } catch (Exception e) {
         MyDialogUtils.showError(e, "Could not get PLogic output from channel");
         return false;
      }
   }
   
   /**
    * gets the sheet width for the specified settings in units of degrees
    * @param cameraMode
    * @param cameraDevice
    * @param side
    * @return 0 if camera isn't assigned
    */
   public float getSheetWidth(CameraModes.Keys cameraMode, Devices.Keys cameraDevice, Devices.Sides side) {
      float sheetWidth;
      final String cameraName = devices_.getMMDevice(cameraDevice);
      
      // start by assuming the base value, then modify below if needed
      final Properties.Keys widthProp = (side == Devices.Sides.A) ?
            Properties.Keys.PLUGIN_SHEET_WIDTH_EDGE_A : Properties.Keys.PLUGIN_SHEET_WIDTH_EDGE_B;
      sheetWidth = props_.getPropValueFloat(Devices.Keys.PLUGIN, widthProp);
      
      if (cameraName == null || cameraName == "") {
         ReportingUtils.logDebugMessage("Could get sheet width for invalid device " + cameraDevice.toString());
         return sheetWidth;
      }
      
      if (cameraMode == CameraModes.Keys.LIGHT_SHEET) {
         final float sheetSlope = prefs_.getFloat(
               MyStrings.PanelNames.SETUP.toString() + side.toString(), 
               Properties.Keys.PLUGIN_LIGHTSHEET_SLOPE, 2000);
         Rectangle roi = cameras_.getCameraROI(cameraDevice);  // get binning-adjusted ROI so value can stay the same regardless of binning
         if (roi == null || roi.height == 0) {
            ReportingUtils.logDebugMessage("Could not get camera ROI for light sheet mode");
         }
         final float slopePolarity = (side == Devices.Sides.B) ? -1f : 1f;
         sheetWidth = roi.height * sheetSlope * slopePolarity / 1e6f;  // in microdegrees per pixel, convert to degrees
      } else {
         final boolean autoSheet = prefs_.getBoolean(
               MyStrings.PanelNames.SETUP.toString() + side.toString(), 
               Properties.Keys.PREFS_AUTO_SHEET_WIDTH, false);
         if (autoSheet) {
            Rectangle roi = cameras_.getCameraROI(cameraDevice);  // get binning-adjusted ROI so value can stay the same regardless of binning
            if (roi == null || roi.height == 0) {
               ReportingUtils.logDebugMessage("Could not get camera ROI for auto sheet mode");
            }
            final float sheetSlope = prefs_.getFloat(MyStrings.PanelNames.SETUP.toString() + side.toString(),
                  Properties.Keys.PLUGIN_SLOPE_SHEET_WIDTH.toString(), 2);
            sheetWidth = roi.height *  sheetSlope / 1000f;  // in millidegrees per pixel, convert to degrees
            // TODO add extra width to compensate for filter depending on sweep rate and filter freq
            // TODO calculation should account for sample exposure to make sure 0.25ms edges get appropriately compensated for
            sheetWidth *= 1.1f;  // 10% extra width just to be sure
         }
      }
      return sheetWidth;
   }
   
   public float getSheetOffset(CameraModes.Keys cameraMode, Devices.Sides side) {
      float sheetOffset;
      if (cameraMode == CameraModes.Keys.LIGHT_SHEET) {
         sheetOffset = prefs_.getFloat(
               MyStrings.PanelNames.SETUP.toString() + side.toString(), 
               Properties.Keys.PLUGIN_LIGHTSHEET_OFFSET, 0) / 1000f;  // in millidegrees, convert to degrees
      } else {
         final Properties.Keys offsetProp = (side == Devices.Sides.A) ?
               Properties.Keys.PLUGIN_SHEET_OFFSET_EDGE_A : Properties.Keys.PLUGIN_SHEET_OFFSET_EDGE_B;
         sheetOffset = props_.getPropValueFloat(Devices.Keys.PLUGIN, offsetProp); 
      }
      return sheetOffset;
   }
   
   /**
    * Gets the actual step size for stage scanning acquisitions.
    * Only valid after call to prepareControllerForAquisition().
    * @return
    */
   public double getActualStepSizeUm() {
      return actualStepSizeUm_;
   }
   
   /**
    * Sets the side-specific preset from the selected group.  Blocks until all involved devices are not busy.
    * Put in this class for convenience though it isn't necessarily about the controller.
    * @param side
    */
   public void setPathPreset(Devices.Sides side) {
      // set preset requested on Settings tab
      Properties.Keys sideKey = Properties.Keys.PLUGIN_PATH_CONFIG_A;
      switch (side) {
      case A:
         sideKey = Properties.Keys.PLUGIN_PATH_CONFIG_A;
         break;
      case B:
         sideKey = Properties.Keys.PLUGIN_PATH_CONFIG_B;
         break;
      case NONE:
      default:
         ReportingUtils.showError("unknown side when setting up path presets");
         break;
      }
      final String preset = props_.getPropValueString(Devices.Keys.PLUGIN, sideKey);
      final String group = props_.getPropValueString(Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_PATH_GROUP);
      try {
         if (!preset.equals("")) {
            core_.setConfig(group, preset);
            core_.waitForConfig(group, preset);
         }
      } catch (Exception e) {
         ReportingUtils.showError("Couldn't set the path config " + preset + " of group " + group);
      }
   }
   
   /**
    * Calculate the Z position corresponding to the (x, y) point according to the planar correction values.
    * @param x
    * @param y
    * @return
    */
   public double getPlanarZ(double x, double y) {
      float xSlope = prefs_.getFloat(MyStrings.PanelNames.ACQUSITION.toString(), 
            Properties.Keys.PLUGIN_PLANAR_SLOPE_X, 0.0f) / 1000;
      float ySlope = prefs_.getFloat(MyStrings.PanelNames.ACQUSITION.toString(), 
            Properties.Keys.PLUGIN_PLANAR_SLOPE_Y, 0.0f) / 1000;
      float zOffset = prefs_.getFloat(MyStrings.PanelNames.ACQUSITION.toString(), 
            Properties.Keys.PLUGIN_PLANAR_OFFSET_Z, 0.0f);
      return (x*xSlope + y*ySlope + zOffset);
   }
   
   /**
    * Sets the current Z position (SPIM head height) according to the planar correction but only
    * if planar correction is enabled, move is between 1um and 100um.  Uses specified (x,y) coordinate.
    * @param x
    * @param y
    */
   public void setPlanarZ(double x, double y) {
      if (prefs_.getBoolean(MyStrings.PanelNames.ACQUSITION.toString(), Properties.Keys.PLUGIN_PLANAR_ENABLED, false)) {
         final double zPosTarget = getPlanarZ(x, y);
         // if we are more than 0.8um off from where we should be then initiate move to ideal position (this normally includes backlash move)
         final double MAX_PLANAR_CORRECTION_ERROR = 1.0;
         final double MAX_PLANAR_CORRECTION_MOVE = 100.0;
         final double zPosCurrent = positions_.getCachedPosition(Devices.Keys.UPPERZDRIVE, Joystick.Directions.NONE);
         final double distance = Math.abs(zPosTarget-zPosCurrent);
         if ( distance > MAX_PLANAR_CORRECTION_ERROR && distance < MAX_PLANAR_CORRECTION_MOVE ) {
            positions_.setPosition(Devices.Keys.UPPERZDRIVE, zPosTarget);
         }
      }
   }
   
   /**
    * Sets the current Z position (SPIM head height) according to the planar correction but only
    * if planar correction is enabled.  Uses current (x,y) coordinate after querying.
    * @param x
    * @param y
    */
   public void setPlanarZ() {
      positions_.getUpdatedPosition(Devices.Keys.XYSTAGE);
      double xPos = positions_.getCachedPosition(Devices.Keys.XYSTAGE, Joystick.Directions.X);
      double yPos = positions_.getCachedPosition(Devices.Keys.XYSTAGE, Joystick.Directions.Y);
      setPlanarZ(xPos, yPos);
   }

}
