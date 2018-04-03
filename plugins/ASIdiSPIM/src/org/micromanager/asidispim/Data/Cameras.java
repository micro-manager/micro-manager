///////////////////////////////////////////////////////////////////////////////
//FILE:          Cameras.java
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

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import mmcorej.CMMCore;

import org.micromanager.api.ScriptInterface;
import org.micromanager.asidispim.ASIdiSPIM;
import org.micromanager.asidispim.CameraPanel;
import org.micromanager.asidispim.Utils.MyDialogUtils;
import org.micromanager.utils.ReportingUtils;

/**
 * Holds utility functions for cameras
 * 
 * @author Jon
 */
public class Cameras {

   private final Devices devices_; // object holding information about
                                   // selected/available devices
   private final Properties props_; // object handling all property read/writes
   private final Prefs prefs_;
   private final ScriptInterface gui_;
   private final CMMCore core_;
   private Devices.Keys currentCameraKey_;


   public Cameras(ScriptInterface gui, Devices devices, Properties props, Prefs prefs) {
      devices_ = devices;
      props_ = props;
      prefs_ = prefs;
      gui_ = gui;
      core_ = gui_.getMMCore();
      
      // try to initialize currentCameraKey_ with camera selected in main UI
      currentCameraKey_ = getCurrentCamera();
      
   }// constructor

   /**
    * associative class to store information for camera combo boxes. Contains
    * string shown in combo box, key of corresponding device
    */
   public static class CameraData {
      public String displayString;
      public Devices.Keys deviceKey;
      public Devices.Sides side;

      /**
       * @param displayString string used in camera drop-down
       * @param deviceKey enum from Devices.Keys for the device
       * @param side A, B, or none
       */
      public CameraData(String displayString, Devices.Keys deviceKey,
            Devices.Sides side) {
         this.displayString = displayString;
         this.deviceKey = deviceKey;
         this.side = side;
      }

      public boolean equals(CameraData a) {
         return (this.displayString.equals(a.displayString)
               && this.deviceKey == a.deviceKey && this.side == a.side);
      }

      @Override
      public int hashCode() {
         return (this.displayString.hashCode()
               + this.deviceKey.toString().hashCode() + this.side.hashCode());
      }

      @Override
      public boolean equals(Object obj) {
         if (obj == null) {
            return false;
         }
         if (getClass() != obj.getClass()) {
            return false;
         }
         final CameraData other = (CameraData) obj;
         if ((this.displayString == null) ? (other.displayString != null)
               : !this.displayString.equals(other.displayString)) {
            return false;
         }
         if (this.deviceKey != other.deviceKey) {
            return false;
         }
         return this.side == other.side;
      }
   }

   /**
    * used to generate selection list for cameras
    * 
    * @return array with with CameraData structures
    */
   public CameraData[] getCameraData() {
      List<CameraData> list = new ArrayList<CameraData>();
      list.add(new CameraData(devices_.getDeviceDisplay(Devices.Keys.CAMERAPREVIOUS),
            Devices.Keys.CAMERAPREVIOUS, Devices.Sides.NONE));
      for (Devices.Keys devKey : Devices.CAMERAS) {
         if (devices_.getMMDevice(devKey) != null) {
            String dispKey = devices_.getMMDevice(devKey); // getDeviceDisplay(devKey);
            list.add(new CameraData(dispKey, devKey, 
                  Devices.getSideFromKey(devKey)));
         }
      }
      List<CameraData> noduplicates = new ArrayList<CameraData>(
            new LinkedHashSet<CameraData>(list));
      return noduplicates.toArray(new CameraData[0]);
   }

   private void setShutterForCamera(Devices.Keys camera) {
      Devices.Keys shutter = null;
      if (camera == Devices.Keys.CAMERAA
            || camera == Devices.Keys.CAMERAB
            || camera == Devices.Keys.MULTICAMERA) {
         shutter = Devices.Keys.PLOGIC;
      }
      if (camera == Devices.Keys.CAMERALOWER) {
         shutter = Devices.Keys.SHUTTERLOWER;
      }
      if (shutter != null) {
         try {
            gui_.getMMCore().setShutterDevice(devices_.getMMDevice(shutter));
         } catch (Exception ex) {
            // do nothing
         }
      }  
   }
   
   /**
    * Switches the active camera to the desired one. Takes care of possible side
    * effects.
    * @param key camera device to switch to
    */
   public void setCamera(Devices.Keys key) {
      if (!Devices.CAMERAS.contains(key) ||
            key == Devices.Keys.CAMERAPREVIOUS) {
         return;
      }
      String mmDevice = devices_.getMMDevice(key);
      if (mmDevice != null) {
         try {
            final boolean liveEnabled = gui_.isLiveModeOn();
            if (liveEnabled) {
               gui_.enableLiveMode(false);
            }
            currentCameraKey_ = key;
            core_.setCameraDevice(mmDevice);
            setShutterForCamera(key);
            gui_.refreshGUIFromCache();
            if (liveEnabled) {
               gui_.enableLiveMode(true);
            }
         } catch (Exception ex) {
            MyDialogUtils.showError("Failed to set Core Camera property");
         }
      }
   }

   /**
    * Get the device key of the selected camera after making sure the plugin's
    *   representation of the current camera matches the core (core is master)
    * @return device key, e.g. CAMERAA or CAMERALOWER
    */
   public Devices.Keys getCurrentCamera() {
      String camera = core_.getCameraDevice();
      // if we haven't initialized it yet or there is a mismatch
      // then try to find the key (based on core's settings)
      // un-initialize if not found
      if (currentCameraKey_ == null ||
            !camera.equals(devices_.getMMDevice(currentCameraKey_))) {
         currentCameraKey_ = null;
         for (Devices.Keys camKey : Devices.CAMERAS) {
            if (devices_.isValidMMDevice(camKey) &&
                  devices_.getMMDevice(camKey).equals(camera)) {
               setCamera(camKey);  // updates currentCameraKey_
               break;
            }
         }
      }
      return currentCameraKey_;
   }
   
   /**
    * @return false if and only if a camera is not set (checks this class, not
    *         Core-Camera)
    */
   public boolean isCurrentCameraValid() {
      return !((currentCameraKey_ == null) || (currentCameraKey_ == Devices.Keys.NONE));
   }

   /**
    * Take care of low-level property setting to make internal trigger
    * or appropriate external mode depending on camera type (via the DeviceLibrary)
    * currently HamamatsuHam, PCO_Camera, and Andor sCMOS are supported
    * @param devKey
    * @param mode enum from this class
    */
   private void setCameraTriggerMode(Devices.Keys devKey, CameraModes.Keys mode) {
      Devices.Libraries camLibrary = devices_.getMMDeviceLibrary(devKey);
      switch (camLibrary) {
      case HAMCAM:
         props_.setPropValue(devKey,
               Properties.Keys.TRIGGER_SOURCE,
               ((mode == CameraModes.Keys.INTERNAL) 
                     ? Properties.Values.INTERNAL
                     : Properties.Values.EXTERNAL));
         props_.setPropValue(devKey, Properties.Keys.SENSOR_MODE,
               ((mode == CameraModes.Keys.LIGHT_SHEET)
                     ? Properties.Values.PROGRESSIVE
                     : Properties.Values.AREA));
         switch (mode) {
         case EDGE:
         case LIGHT_SHEET:
            props_.setPropValue(devKey,
                  Properties.Keys.TRIGGER_ACTIVE,
                  Properties.Values.EDGE);
            break;
         case LEVEL:
            props_.setPropValue(devKey,
                  Properties.Keys.TRIGGER_ACTIVE,
                  Properties.Values.LEVEL);
            break;
         case OVERLAP:
            props_.setPropValue(devKey,
                  Properties.Keys.TRIGGER_ACTIVE,
                  Properties.Values.SYNCREADOUT);
            break;
         default:
            break;
         }
         break;
      case PCOCAM:
         switch (mode) {
         case EDGE:
         case PSEUDO_OVERLAP:
            props_.setPropValue(devKey,
                  Properties.Keys.TRIGGER_MODE_PCO,
                  Properties.Values.EXTERNAL_LC);
            break;
         case LEVEL:
            props_.setPropValue(devKey,
                  Properties.Keys.TRIGGER_MODE_PCO,
                  Properties.Values.LEVEL_PCO);
            break;
         case INTERNAL:
            props_.setPropValue(devKey,
                  Properties.Keys.TRIGGER_MODE_PCO,
                  Properties.Values.INTERNAL_LC);
            break;
         default:
               break;
         }
         break;
      case ANDORCAM:
         // work-around to bug in SDK3 device adapter, can't switch from light sheet mode
         //  to "normal" center out simultaneous but works if we always go through the in-between mode
         if (props_.hasProperty(devKey, Properties.Keys.SENSOR_READOUT_MODE)) {  // skip step if property is missing
            props_.setPropValue(devKey,
                  Properties.Keys.SENSOR_READOUT_MODE,
                  Properties.Values.BOTTOM_UP_SIM_ANDOR);
            props_.setPropValue(devKey,
                  Properties.Keys.SENSOR_READOUT_MODE,
                  (mode == CameraModes.Keys.LIGHT_SHEET
                  ? Properties.Values.BOTTOM_UP_ANDOR
                        : Properties.Values.CENTER_OUT_ANDOR));
         }
         switch (mode) {
         case EDGE:
         case LIGHT_SHEET:
            props_.setPropValue(devKey,
                  Properties.Keys.TRIGGER_MODE,
                  Properties.Values.EXTERNAL_LC);
            props_.setPropValue(devKey,
                  Properties.Keys.ANDOR_OVERLAP,
                  Properties.Values.OFF);
            break;
         case INTERNAL:
            props_.setPropValue(devKey,
                  Properties.Keys.TRIGGER_MODE,
                  Properties.Values.INTERNAL_ANDOR);
            break;
         case LEVEL:
            props_.setPropValue(devKey,
                  Properties.Keys.TRIGGER_MODE,
                  Properties.Values.LEVEL_ANDOR);
            props_.setPropValue(devKey,
                  Properties.Keys.ANDOR_OVERLAP,
                  Properties.Values.OFF);
            break;
         case OVERLAP:
            props_.setPropValue(devKey,
                  Properties.Keys.TRIGGER_MODE,
                  Properties.Values.LEVEL_ANDOR);
            props_.setPropValue(devKey,
                  Properties.Keys.ANDOR_OVERLAP,
                  Properties.Values.ON);
            break;
         default:
            break;
         }
         break;
      case PVCAM:
         switch (mode) {
         case EDGE:
         case PSEUDO_OVERLAP:
         case LIGHT_SHEET:
            props_.setPropValue(devKey,
                  Properties.Keys.TRIGGER_MODE,
                  Properties.Values.EDGE_TRIGGER);
            break;
         case INTERNAL:
            props_.setPropValue(devKey,
                  Properties.Keys.TRIGGER_MODE,
                  Properties.Values.INTERNAL_TRIGGER);
            break;
         default:
            break;
         }
         break;
      case DEMOCAM:
         // do nothing
         break;
      default:
         break;
      }
   }
   
   /**
    * Utility: calculates the ROI offset from centered on the vertical axis.
    * @param roi
    * @param sensor
    * @return
    */
   private int roiVerticalOffset(Rectangle roi, Rectangle sensor) {
      return (roi.y + roi.height / 2) - (sensor.height / 2);
   }
   
   /**
    * Utility: calculates the number of rows that need to be read out
    * for camera sensor split horizontally across the middle
    * @param roi
    * @param sensor
    * @return
    */
   private int roiReadoutRowsSplitReadout(Rectangle roi, Rectangle sensor) {
      return Math.min(
            Math.abs(roiVerticalOffset(roi, sensor)) + roi.height / 2,  // if ROI overlaps sensor mid-line
            roi.height);                                                // if ROI does not overlap mid-line
   }
   
   /**
    * Returns true if the camera is a Zyla 5.5
    */
   private boolean isZyla55(Devices.Keys camKey) {
      return props_.getPropValueString(camKey, Properties.Keys.CAMERA_NAME)
            .substring(0, 8).equals("Zyla 5.5");
   }
   
   /**
    * Returns true if the camera is a Edge 5.5
    */
   private boolean isEdge55(Devices.Keys camKey) {
      return props_.getPropValueString(camKey, Properties.Keys.CAMERA_TYPE)
            .contains(" 5.5");
   }
   
   /**
    * Goes to properties and sees if this camera has slow readout enabled
    * (which affects row transfer speed and thus reset/readout time).
    * @param camKey
    * @return
    */
   private boolean isSlowReadout(Devices.Keys camKey) {
      switch(devices_.getMMDeviceLibrary(camKey)) {
      case HAMCAM:
         return props_.getPropValueString(camKey, Properties.Keys.SCAN_MODE).equals("1");
      case PCOCAM:
         return props_.getPropValueString(camKey, Properties.Keys.PIXEL_RATE).equals("slow scan");
      case ANDORCAM:
         if (isZyla55(camKey)) {
            return props_.getPropValueString(camKey, Properties.Keys.PIXEL_READOUT_RATE)
                  .substring(0, 3).equals("200");
         } else {
            return props_.getPropValueString(camKey, Properties.Keys.PIXEL_READOUT_RATE)
                  .substring(0, 3).equals("216");
         }
      case DEMOCAM:
         break;
      default:
         break;
      }
      return false;
   }
   
   /**
    * Tries to figure out binning by looking at first character of the camera's binning property
    * of the camera.  There is not a uniform binning representation but hopefully this works.
    * @return binning of the selected camera (usually 1, 2, or 4)
    */
   private int getBinningFactor(Devices.Keys camKey) {
      String propVal = props_.getPropValueString(camKey, Properties.Keys.BINNING);
      int factor = Integer.parseInt(propVal.substring(0, 1));
      if (factor < 1) {
         MyDialogUtils.showError("Was not able to get camera binning factor");
         return 1;
      }
      return factor;
   }
   
   /**
    * @return dimension/resolution of sensor.  If binning is enabled then it is
    *         not reflected here because reset/readout time don't depend on binning.
    */
   private Rectangle getSensorSize(Devices.Keys camKey) {
      int x = 0;
      int y = 0;
      switch(devices_.getMMDeviceLibrary(camKey)) {
      case HAMCAM:
         x = 2048;
         y = 2048;
         break;
      case PCOCAM:
         if (isEdge55(camKey)) {
            x = 2560;
            y = 2160;
         } else { // 4.2
            x = 2060;
            y = 2048;
         }
         break;
      case ANDORCAM:
         if (isZyla55(camKey)) {
            x = 2560;
            y = 2160;
         } else { // 4.2
            x = 2048;
            y = 2048;
         }
         break;
      case PVCAM:
         x = props_.getPropValueInteger(camKey, Properties.Keys.CAMERA_X_DIMENSION);
         y = props_.getPropValueInteger(camKey, Properties.Keys.CAMERA_Y_DIMENSION);
         break;
      case DEMOCAM:
         x = props_.getPropValueInteger(camKey, Properties.Keys.CAMERA_SIZE_X);
         y = props_.getPropValueInteger(camKey, Properties.Keys.CAMERA_SIZE_Y);
         break;
      default:
         break;
      }
      if (x==0 || y==0){
         MyDialogUtils.showError(
               "Was not able to get sensor size of camera " 
                     + devices_.getMMDevice(camKey));
      }
      return new Rectangle(0, 0, x, y);
   }
   
   /**
    * @param camKey
    * @return the per-row readout time of the camera in ms
    */
   public double getRowReadoutTime(Devices.Keys camKey) {
      switch(devices_.getMMDeviceLibrary(camKey)) {
      case HAMCAM:
         if (isSlowReadout(camKey)) {
            return (2592 / 266e3 * (10./3)); 
         } else {
            return (2592 / 266e3);
         }
      case PCOCAM:
         if (props_.hasProperty(camKey, Properties.Keys.LINE_TIME)) {  // should be present as of 20170926 nightly build
            return ((double) props_.getPropValueFloat(camKey, Properties.Keys.LINE_TIME))/1000d;
         } else {  // assumes CameraLink interface
            if (isEdge55(camKey)) {
               if (isSlowReadout(camKey)) {
                  return 0.02752;
               } else {
                  return 0.00917;
               }       
            } else {  // 4.2
               if (isSlowReadout(camKey)) {
                  return 0.0276;
               } else {
                  return 0.00965;
               }            
            }
         }
      case ANDORCAM:
         if (isZyla55(camKey)) {
            if (isSlowReadout(camKey)) {
               return (2624 * 2 / 206.54e3);
            } else {
               return (2624 * 2 / 568e3);
            }
         } else {  // 4.2
            if (isSlowReadout(camKey)) {
               return (2592 * 2 / 216e3);
            } else {
               return (2592 * 2 / 540e3);
            }
         }
      case PVCAM:
         Rectangle roi = getCameraROI(camKey);
         if (props_.getPropValueString(camKey, Properties.Keys.PVCAM_CHIPNAME).equals(Properties.Values.PRIME_95B_CHIPNAME)) {
            float readoutTimeMs = (float) props_.getPropValueInteger(camKey, Properties.Keys.PVCAM_READOUT_TIME) / 1e6f;
            return (readoutTimeMs / roi.height);
         } else {
            return 0.01;  // TODO get more accurate value
         }
      case DEMOCAM:
         return(10e-3);  // dummy 10us row time
      default:
         break;
      }
      MyDialogUtils.showError(
            "Was not able to get per-row readout time of camera " 
            + devices_.getMMDevice(camKey));
      return 1;
   }
   
   /**
    * Gets an estimate of a specific camera's time between trigger and global
    * exposure, i.e. how long it takes for reset. Will depend on whether we
    * have/use global reset, the ROI size, etc.  To first order this is the same
    * as the camera readout time in edge-trigger mode so we utilize that (exception
    * is for light sheet mode, when reset time is 0).
    * @param camKey device key for camera in question
    * @param camMode camera mode
    * @return reset time in ms
    */
   public float computeCameraResetTime(Devices.Keys camKey, CameraModes.Keys camMode) {
      float resetTimeMs = 10f;
      if (camMode == CameraModes.Keys.LIGHT_SHEET) {
         return resetTimeMs = 0f;
      } else {
         Devices.Libraries camLibrary = devices_.getMMDeviceLibrary(camKey);
         
         // Photometrics Prime 95B is very different from other cameras so handle it as special case
         if (camLibrary == Devices.Libraries.PVCAM 
               && props_.getPropValueString(camKey, Properties.Keys.PVCAM_CHIPNAME).equals(Properties.Values.PRIME_95B_CHIPNAME)) {
            int trigToGlobal = props_.getPropValueInteger(camKey, Properties.Keys.PVCAM_POST_TIME)
                  + props_.getPropValueInteger(camKey, Properties.Keys.PVCAM_READOUT_TIME);
            // it appears as of end-May 2017 that the clearing time is actually rolled into the post-trigger
            //    time despite Photometrics documentation to the contrary
            //String clearMode = props_.getPropValueString(camKey, Properties.Keys.PVCAM_CLEARING_MODE);
            //if (clearMode.equals(Properties.Values.PRE_EXPOSURE.toString())) {
            //   trigToGlobal += props_.getPropValueInteger(camKey, Properties.Keys.PVCAM_CLEARING_TIME);
            //}
            resetTimeMs = (float) trigToGlobal / 1e6f;
         } else {
            // all other cameras
            double rowReadoutTime = getRowReadoutTime(camKey);
            float camReadoutTime = computeCameraReadoutTime(camKey, CameraModes.Keys.EDGE);
            int numRowsOverhead;
            switch (camLibrary) {
            case HAMCAM:
               // global reset mode not yet exposed in Micro-manager
               // it will be 17+1 rows of overhead but nothing else
               if (props_.getPropValueString(camKey, Properties.Keys.TRIGGER_ACTIVE)
                     .equals(Properties.Values.SYNCREADOUT.toString())) {
                  numRowsOverhead = 18; // overhead of 17 rows plus jitter of 1 row
               } else { // for EDGE and LEVEL trigger modes
                  numRowsOverhead = 10; // overhead of 9 rows plus jitter of 1 row
               }
               resetTimeMs = camReadoutTime + (float) (numRowsOverhead * rowReadoutTime);
               break;
            case PCOCAM:
               numRowsOverhead = 1;
               resetTimeMs = camReadoutTime + (float) (numRowsOverhead * rowReadoutTime);
               break;
            case ANDORCAM:
               numRowsOverhead = 1;
               resetTimeMs = camReadoutTime + (float) (numRowsOverhead * rowReadoutTime);
               break;
            case DEMOCAM:
               resetTimeMs = camReadoutTime;
               break;
            default:
               break;
            }
         }//else
         
         ReportingUtils.logDebugMessage("camera reset time computed as " + resetTimeMs + 
               " for camera " + devices_.getMMDevice(camKey));
         return resetTimeMs;  // assume 10ms readout if not otherwise possible to calculate
      }
   }
   
   /**
    * Gets an estimate of a specific camera's readout time based on ROI and 
    * other settings.  Returns 0 for overlap mode and 0.25ms pseudo-overlap mode
    * (or camera-specific time for Photometrics)
    * @param camKey device key for camera in question
    * @param camMode camera mode
    * @return readout time in ms
    */
   public float computeCameraReadoutTime(Devices.Keys camKey, CameraModes.Keys camMode) {

      // TODO restructure code so that we don't keep calling this function over and over
      //      (e.g. could cache some values or something)
      
      float readoutTimeMs = 10f;
      Devices.Libraries camLibrary = devices_.getMMDeviceLibrary(camKey);
      
      switch (camMode) {
      case OVERLAP:
         readoutTimeMs = 0.0f;
         break;
      case PSEUDO_OVERLAP:
         switch (camLibrary) {
         case PCOCAM:
            readoutTimeMs = 0.25f;
            break;
         case PVCAM:
            if (props_.getPropValueString(camKey, Properties.Keys.PVCAM_CHIPNAME).equals(Properties.Values.PRIME_95B_CHIPNAME)) {
               int preTime = props_.getPropValueInteger(camKey, Properties.Keys.PVCAM_PRE_TIME);
               readoutTimeMs = (float) preTime / 1e6f;
               // for safety we make sure to wait at least a quarter millisecond to trigger
               //   (may have hidden assumptions in other code about at least one tic wait)
               if (readoutTimeMs < 0.249f) {
                  readoutTimeMs = 0.25f;
               }
            } else {  // original Prime
               readoutTimeMs = 0.25f;
            }
            break;
         default:
            break;
         }
         break;
      case LIGHT_SHEET:
         if (camLibrary == Devices.Libraries.HAMCAM && props_.getPropValueString(camKey, Properties.Keys.CAMERA_BUS).equals(Properties.Values.USB3)) {
            readoutTimeMs = 10000;  // absurdly large, light sheet mode over USB3 isn't supported by Flash4 but we are set up to decide available modes by device library and not a property
         } else if (camLibrary == Devices.Libraries.PVCAM) {
            readoutTimeMs = (float) props_.getPropValueInteger(camKey, Properties.Keys.PVCAM_READOUT_TIME) / 1e6f;
         } else {
            Rectangle roi = getCameraROI(camKey);
            final double rowReadoutTime = getRowReadoutTime(camKey);
            int speedFactor = 1; // props_.getPropValueInteger(Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_LS_SHUTTER_SPEED);
            if (speedFactor < 1) {
               speedFactor = 1;
            }
            readoutTimeMs = (float) rowReadoutTime * roi.height * speedFactor;
         }
         break;
      case EDGE:
      case LEVEL:
         double rowReadoutTime = getRowReadoutTime(camKey);
         int numReadoutRows;

         Rectangle roi = getCameraROI(camKey);
         Rectangle sensorSize = getSensorSize(camKey);

         switch (camLibrary) {
         case HAMCAM:
            if (camLibrary == Devices.Libraries.HAMCAM && props_.getPropValueString(camKey, Properties.Keys.CAMERA_BUS).equals(Properties.Values.USB3)) {
               // trust the device adapter's calculation for USB3
               readoutTimeMs = props_.getPropValueFloat(camKey, Properties.Keys.READOUTTIME)*1000f;
            } else {  // Camera Link interface, original implementation
               // device adapter provides readout time rounded to nearest 0.1ms; we calculate it ourselves instead
               // note that Flash4's ROI is always set in increments of 4 pixels
               if (props_.getPropValueString(camKey, Properties.Keys.SENSOR_MODE)
                     .equals(Properties.Values.PROGRESSIVE.toString())) {
                  numReadoutRows = roi.height;
               } else {
                  numReadoutRows = roiReadoutRowsSplitReadout(roi, sensorSize);
               }
               readoutTimeMs = ((float) (numReadoutRows * rowReadoutTime));
            }
            break;
         case PCOCAM:
            numReadoutRows = roiReadoutRowsSplitReadout(roi, sensorSize);
            if (isEdge55(camKey)) {
               numReadoutRows = numReadoutRows + 2; // 2 rows overhead for 5.5, none for 4.2
            }
            readoutTimeMs = ((float) (numReadoutRows * rowReadoutTime));
            break;
         case ANDORCAM:
            numReadoutRows = roiReadoutRowsSplitReadout(roi, sensorSize);
            readoutTimeMs = ((float) (numReadoutRows * rowReadoutTime));
            break;
         case PVCAM:
            int endGlobalToTrig = props_.getPropValueInteger(camKey, Properties.Keys.PVCAM_PRE_TIME)
               + props_.getPropValueInteger(camKey, Properties.Keys.PVCAM_READOUT_TIME);
            readoutTimeMs = (float) endGlobalToTrig / 1e6f;
            break;
         case DEMOCAM:
            numReadoutRows = roiReadoutRowsSplitReadout(roi, sensorSize);
            readoutTimeMs = ((float) (numReadoutRows * rowReadoutTime));
            break;
         default:
            break;
         }//end switch
         break;
      default:
         break;  // assume 10ms readout if not otherwise possible to calculate
      }
      
      ReportingUtils.logDebugMessage("camera readout time computed as " + readoutTimeMs + 
            " for camera " + devices_.getMMDevice(camKey));
      return readoutTimeMs;
   }
   
   /**
    * Sets the specified camera for acquisition if acq is true or for live mode if acq is false
    * @param camKey
    * @param acq
    */
   public void setCameraForAcquisition(Devices.Keys camKey, boolean acq) {
      if (acq) {
         CameraModes.Keys cameraMode = ASIdiSPIM.getFrame().getCameraPanel().getSPIMCameraMode();
         setCameraTriggerMode(camKey, cameraMode);
//         if (cameraMode == CameraModes.Keys.LIGHT_SHEET) {
//            int shutterSpeed = props_.getPropValueInteger(Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_LS_SHUTTER_SPEED);
//            
//         }
         // exposure time set by acquisition setup code
      } else { // for Live mode
         setCameraTriggerMode(camKey, CameraModes.Keys.INTERNAL);
      }
   }

   /**
    * Gets the camera ROI in actual pixels used (i.e. if binning is 4x with 2k sensor return 2k instead of 512).
    * This is because reset and readout times depend on actual pixels and only data transfer time (usually less
    * then readout time) is affected by binning (at least for Flash4v2 CameraLink and Zyla 4.2 CameraLink)
    * @param camKey
    * @return
    */
   public Rectangle getCameraROI(Devices.Keys camKey) {
      Rectangle roi = new Rectangle();
      int binning;
      try {
         roi = core_.getROI(devices_.getMMDevice(camKey));
         binning = getBinningFactor(camKey);
         if (binning > 1) {
        	 return new Rectangle(
        			 (roi.x < 0 ? 0 : roi.x*binning),  // make sure isn't negative, some cameras seem to do this
        			 (roi.y < 0 ? 0 : roi.y*binning),  // make sure isn't negative, some cameras seem to do this
        			 roi.width*binning, roi.height*binning);
         } else {
        	 return roi;
         }
      } catch (Exception e) {
         MyDialogUtils.showError(e);
      }
      return roi;  // only should reach here if there was exception
   }
   
   /**
    * Sets the ROI of the specified camera to the provided rectangle
    * @param camKey
    * @param roi
    */
   public void setCameraROI(Devices.Keys camKey, Rectangle roi) {
      try {
         final boolean liveEnabled = gui_.isLiveModeOn();
         if (liveEnabled) {
            gui_.enableLiveMode(false);
         }
         core_.setROI(devices_.getMMDevice(camKey), roi.x, roi.y, roi.width, roi.height);
         if (liveEnabled) {
            gui_.enableLiveMode(true);
         }
      }
      catch (Exception ex) {
         MyDialogUtils.showError(ex, "Could not set camera ROI to " + roi.toString());
      }
   }
   
   /**
    * calculate a new rectangle smaller by the factor scale and centered in the old one 
    * @param r
    * @param scale
    * @return
    */
   private Rectangle calculateCenterRectangle(Rectangle r, int scale) {
      // two sanity checks, maybe not strictly necessary but assumed in calculations below
      if (scale < 1) {
         return r;
      }
      if (r.x != 0 || r.y != 0) {
         return r;
      }
      final int width = r.width / scale;
      final int height = r.height / scale;
      final int x = (r.width - width) / 2;
      final int y = (r.height - height) / 2;
      return new Rectangle(x, y, width, height);
   }
   
   /**
    * Sets the ROI
    * @param camKey
    * @param roi
    */
   public void setCameraROI(Devices.Keys camKey, CameraPanel.RoiPresets roi) {
      try {
         if (devices_.isValidMMDevice(camKey)) {
            Rectangle size = getSensorSize(camKey);
            switch (roi) {
            case FULL:
               setCameraROI(camKey, size);
               break;
            case HALF:
               setCameraROI(camKey, calculateCenterRectangle(size, 2));
               break;
            case QUARTER:
               setCameraROI(camKey, calculateCenterRectangle(size, 4));
               break;
            case EIGHTH:
               setCameraROI(camKey, calculateCenterRectangle(size, 8));
               break;
            case CUSTOM:
               final int x = prefs_.getInt(MyStrings.PanelNames.CAMERAS.toString(), "OffsetX", 0);
               final int y = prefs_.getInt(MyStrings.PanelNames.CAMERAS.toString(), "OffsetY", 0);
               final int height = prefs_.getInt(MyStrings.PanelNames.CAMERAS.toString(), "Height", 0);
               final int width = prefs_.getInt(MyStrings.PanelNames.CAMERAS.toString(), "Width", 0);
               setCameraROI(camKey, new Rectangle(x, y, width, height));
               break;
            case UNCHANGED:
               break;
            }
         }
      }
      catch (Exception ex) {
         MyDialogUtils.showError(ex, "Could not set camera ROI to value " + roi.toString());
      }
   }

}
