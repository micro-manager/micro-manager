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
    * Internal use only, take care of low-level property setting to make
    * internal or external depending on camera type (via the DeviceLibrary)
    * currently HamamatsuHam, PCO_Camera, and Andor sCMOS are supported
    * 
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
                     : Properties.Values.EXTERNAL), true);
         switch (mode) {
         case EDGE:
            props_.setPropValue(devKey, Properties.Keys.TRIGGER_ACTIVE,
                  Properties.Values.EDGE);
            break;
         case LEVEL:
            props_.setPropValue(devKey, Properties.Keys.TRIGGER_ACTIVE,
                  Properties.Values.LEVEL);
            break;
         case OVERLAP:
            props_.setPropValue(devKey, Properties.Keys.TRIGGER_ACTIVE,
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
                  Properties.Values.EXTERNAL_LC, true);
            break;
         case LEVEL:
            props_.setPropValue(devKey,
                  Properties.Keys.TRIGGER_MODE_PCO,
                  Properties.Values.LEVEL_PCO, true);
            break;
         case INTERNAL:
            props_.setPropValue(devKey,
                  Properties.Keys.TRIGGER_MODE_PCO,
                  Properties.Values.INTERNAL_LC, true);
            break;
         default:
               break;
         }
         break;
      case ANDORCAM:
         switch (mode) {
         case EDGE:
            props_.setPropValue(devKey,
                  Properties.Keys.TRIGGER_MODE,
                  Properties.Values.EXTERNAL_LC, true);
            props_.setPropValue(devKey,
                  Properties.Keys.ANDOR_OVERLAP,
                  Properties.Values.OFF, true);
            break;
         case INTERNAL:
            props_.setPropValue(devKey,
                  Properties.Keys.TRIGGER_MODE,
                  Properties.Values.INTERNAL_ANDOR, true);
            break;
         case LEVEL:
            props_.setPropValue(devKey,
                  Properties.Keys.TRIGGER_MODE,
                  Properties.Values.LEVEL_ANDOR, true);
            props_.setPropValue(devKey,
                  Properties.Keys.ANDOR_OVERLAP,
                  Properties.Values.OFF, true);
            break;
         case OVERLAP:
            props_.setPropValue(devKey,
                  Properties.Keys.TRIGGER_MODE,
                  Properties.Values.LEVEL_ANDOR, true);
            props_.setPropValue(devKey,
                  Properties.Keys.ANDOR_OVERLAP,
                  Properties.Values.ON, true);
            break;
         default:
            break;
         }
         break;
      case PVCAM:
         switch (mode) {
         case EDGE:
            props_.setPropValue(devKey,
                  Properties.Keys.TRIGGER_MODE,
                  Properties.Values.EDGE_TRIGGER, true);
            break;
         case INTERNAL:
            props_.setPropValue(devKey,
                  Properties.Keys.TRIGGER_MODE,
                  Properties.Values.INTERNAL_TRIGGER, true);
            break;
         default:
            break;
         }
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
    * Gets the per-row readout time of the camera in ms.
    * Assumes fast readout mode (should include slow readout too).
    * 
    * @param camKey
    * @return
    */
   private double getRowReadoutTime(Devices.Keys camKey) {
      switch(devices_.getMMDeviceLibrary(camKey)) {
      case HAMCAM:
         if (isSlowReadout(camKey)) {
            return (2592 / 266e3 * (10./3)); 
         } else {
            return (2592 / 266e3);
         }
      case PCOCAM:
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
         // TODO get this correct; currently just draft for testing
         return 20e-3;
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
    * as the camera readout time in edge-trigger mode so we utilize that.
    * 
    * @param camKey
    * @return
    */
   public float computeCameraResetTime(Devices.Keys camKey) {
      float resetTimeMs = 10;
      double rowReadoutTime = getRowReadoutTime(camKey);
      float camReadoutTime = computeCameraReadoutTime(camKey, CameraModes.Keys.EDGE);
      int numRowsOverhead;
      switch (devices_.getMMDeviceLibrary(camKey)) {
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
      case PVCAM:
         // TODO get this correct; currently just draft for testing
         numRowsOverhead = 1;
         resetTimeMs = camReadoutTime + (float) (numRowsOverhead * rowReadoutTime);
         break;
      case DEMOCAM:
         resetTimeMs = camReadoutTime;
         break;
      default:
         break;
      }
      ReportingUtils.logDebugMessage("camera reset time computed as " + resetTimeMs + 
            " for camera " + devices_.getMMDevice(camKey));
      return resetTimeMs;  // assume 10ms readout if not otherwise possible to calculate
   }
   
   /**
    * Gets an estimate of a specific camera's readout time based on ROI and 
    * other settings.  Returns 0 for overlap mode and 0.25ms pseudo-overlap mode.
    * 
    * @param camKey device key for camera in question
    * @param camMode camera mode
    * @return readout time in ms
    */
   public float computeCameraReadoutTime(Devices.Keys camKey, CameraModes.Keys camMode) {

      // TODO restructure code so that we don't keep calling this function over and over
      //      (e.g. could cache some values or something)
      
      float readoutTimeMs = 10f;
      
      if (camMode == CameraModes.Keys.OVERLAP) {
         readoutTimeMs = 0.0f;
      } else if (camMode == CameraModes.Keys.PSEUDO_OVERLAP) {
         readoutTimeMs = 0.25f;
      } else {
         
         // below code only applies to non-overlap
         double rowReadoutTime = getRowReadoutTime(camKey);
         int numReadoutRows;

         Rectangle roi = getCameraROI(camKey);
         Rectangle sensorSize = getSensorSize(camKey);

         switch (devices_.getMMDeviceLibrary(camKey)) {
         case HAMCAM:
            // device adapter provides readout time rounded to nearest 0.1ms; we
            // calculate it ourselves instead
            // note that Flash4's ROI is always set in increments of 4 pixels
            if (props_.getPropValueString(camKey, Properties.Keys.SENSOR_MODE)
                  .equals(Properties.Values.PROGRESSIVE.toString())) {
               numReadoutRows = roi.height;
            } else {
               numReadoutRows = roiReadoutRowsSplitReadout(roi, sensorSize);
            }
            readoutTimeMs = ((float) (numReadoutRows * rowReadoutTime));
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
            // TODO get this correct; currently just draft for testing
            numReadoutRows = roi.height;
            readoutTimeMs = ((float) (numReadoutRows * rowReadoutTime));
            break;
         case DEMOCAM:
            numReadoutRows = roiReadoutRowsSplitReadout(roi, sensorSize);
            readoutTimeMs = ((float) (numReadoutRows * rowReadoutTime));
            break;
         default:
            break;
         }//switch
      }//else
      
      ReportingUtils.logDebugMessage("camera readout time computed as " + readoutTimeMs + 
            " for camera " + devices_.getMMDevice(camKey));
      return readoutTimeMs;  // assume 10ms readout if not otherwise possible to calculate
   }
   
   /**
    * Sets the specified camera for acquisition if acq is true or for live mode if acq is false
    * @param camKey
    * @param acq
    */
   public void setCameraForAcquisition(Devices.Keys camKey, boolean acq) {
      if (acq) {
         CameraModes.Keys cameraMode = CameraModes.getKeyFromPrefCode(
               // could also get from props_ with PLUGIN device
               prefs_.getInt(MyStrings.PanelNames.SETTINGS.toString(),
                     Properties.Keys.PLUGIN_CAMERA_MODE, 0));
         setCameraTriggerMode(camKey, cameraMode);
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
   

}
