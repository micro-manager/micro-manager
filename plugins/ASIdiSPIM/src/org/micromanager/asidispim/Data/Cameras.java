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

import java.awt.Component;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import mmcorej.CMMCore;

import org.micromanager.api.ScriptInterface;
import org.micromanager.asidispim.ASIdiSPIM;
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
         if (this.side != other.side) {
            return false;
         }
         return true;
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

   /**
    * Switches the active camera to the desired one. Takes care of possible side
    * effects.
    */
   public void setCamera(Devices.Keys key) {
      if (!Devices.CAMERAS.contains(key) ||
            key == Devices.Keys.CAMERAPREVIOUS) {
         return;
      }
      String mmDevice = devices_.getMMDevice(key);
      if (mmDevice != null) {
         try {
            boolean liveEnabled = gui_.isLiveModeOn();
            if (liveEnabled) {
               enableLiveMode(false);
            }
            currentCameraKey_ = key;
            core_.setCameraDevice(mmDevice);
            gui_.refreshGUIFromCache();
            if (liveEnabled) {
               enableLiveMode(true);
            }
         } catch (Exception ex) {
            gui_.showError("Failed to set Core Camera property", ASIdiSPIM.getFrame());
         }
      }
   }

   /**
    * @return device key, e.g. CAMERAA or CAMERALOWER
    */
   public Devices.Keys getCurrentCamera() {
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
    * Turns live mode on or off via core call
    */
   public void enableLiveMode(boolean enable) {
      if (enable) {
         setSPIMCamerasForAcquisition(false);
      }
      gui_.enableLiveMode(enable);
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
         // TODO add level-sensitive trigger
         props_.setPropValue(devKey,
               Properties.Keys.TRIGGER_MODE_PCO,
               ((mode == CameraModes.Keys.EDGE) 
                     ? Properties.Values.EXTERNAL_LC
                     : Properties.Values.INTERNAL_LC), true);
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
      return (roi.y + roi.height / 2) - (sensor.y + sensor.height / 2);
   }
   
   /**
    * Utility: calculates the number of rows that need to be read out
    * for camera sensor split horizontally across the middle
    * @param roi
    * @param sensor
    * @return
    */
   private int roiReadoutRowsSplitReadout(Rectangle roi, Rectangle sensor) {
      return Math.abs(roiVerticalOffset(roi, sensor)) + roi.height / 2;
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
   
   private Rectangle getSensorSize(Devices.Keys camKey) {
      switch(devices_.getMMDeviceLibrary(camKey)) {
      case HAMCAM:
         return new Rectangle(0, 0, 2048, 2048);
      case PCOCAM:
         if (isEdge55(camKey)) {
            return new Rectangle(0, 0, 2560, 2160);
         } else {
            return new Rectangle(0, 0, 2060, 2048);
         }
      case ANDORCAM:
         if (isZyla55(camKey)) {
            return new Rectangle(0, 0, 2560, 2160);
         } else {
            return new Rectangle(0, 0, 2048, 2048);
         }
      case DEMOCAM:
         return new Rectangle(0, 0, 512, 512);
      default:
         break;
      }
      ReportingUtils.showError(
            "Was not able to get sensor size of camera " 
            + devices_.getMMDevice(camKey), ASIdiSPIM.getFrame());
      return new Rectangle(0, 0, 0, 0);
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
         // TODO replace these numbers with more exact calculations
         // TODO see if 4.2 and 5.5 are different
         if (isSlowReadout(camKey)) {
            return 0.02752;
         } else {
            return 0.00917;
         }
      case ANDORCAM:
         if (isZyla55(camKey)) {
            if (isSlowReadout(camKey)) {
               return (2624 * 2 / 206.54e3);
            } else {
               return (2624 * 2 / 568e3);
            }
         } else {
            if (isSlowReadout(camKey)) {
               return (2592 * 2 / 216e3);
            } else {
               return (2592 * 2 / 540e3);
            }
         }
      case DEMOCAM:
         return(10e-3);  // dummy 10us row time
      default:
         break;
      }
      ReportingUtils.showError(
            "Was not able to get per-row readout time of camera " 
            + devices_.getMMDevice(camKey), ASIdiSPIM.getFrame());
      return 1;
   }
   
   /**
    * Gets an estimate of a specific camera's time between trigger and global
    * exposure, i.e. how long it takes for reset. Will depend on whether we
    * have/use global reset, the ROI size, etc.
    * 
    * @param camKey
    * @return
    */
   public float computeCameraResetTime(Devices.Keys camKey) {
      float resetTimeMs = 10;
      double rowReadoutTime = getRowReadoutTime(camKey);
      int numRowsOverhead;      
      switch (devices_.getMMDeviceLibrary(camKey)) {
      case HAMCAM:
         // global reset mode not yet exposed in Micro-manager
         // it will be 17+1 rows of overhead but nothing else
         if (props_.getPropValueString(camKey, Properties.Keys.TRIGGER_ACTIVE,
               true).equals(Properties.Values.SYNCREADOUT.toString())) {
            numRowsOverhead = 18; // overhead of 17 rows plus jitter of 1 row
         } else { // for EDGE and LEVEL trigger modes
            numRowsOverhead = 10; // overhead of 9 rows plus jitter of 1 row
         }
         resetTimeMs = computeCameraReadoutTime(camKey) + (float) (numRowsOverhead * rowReadoutTime);
         break;
      case PCOCAM:
         // TODO get the actual numbers from PCO
         numRowsOverhead = 10;  
         resetTimeMs = computeCameraReadoutTime(camKey) + (float) (numRowsOverhead * rowReadoutTime);
         break;
      case ANDORCAM:
         numRowsOverhead = 1;  // TODO make sure this is accurate; don't have sufficient documentation yet
         resetTimeMs = computeCameraReadoutTime(camKey) + (float) (numRowsOverhead * rowReadoutTime);
         break;
      case DEMOCAM:
         resetTimeMs = computeCameraReadoutTime(camKey);
         break;
      default:
         break;
      }
      core_.logMessage("camera reset time computed as " + resetTimeMs + 
            " for camera " + devices_.getMMDevice(camKey), true);
      return resetTimeMs;  // assume 10ms readout if not otherwise possible to calculate
   }
   
   /**
    * Gets an estimate of a specific camera's readout time based on ROI and 
    * other settings.
    * 
    * @param camKey device key for camera in question
    * @return readout time in ms
    */
   public float computeCameraReadoutTime(Devices.Keys camKey) {
      float readoutTimeMs = 10;
      Rectangle roi = new Rectangle();
      try {
         roi = core_.getROI(devices_.getMMDevice(camKey));
      } catch (Exception e) {
         gui_.showError(e, (Component) ASIdiSPIM.getFrame());
      }
     
      double rowReadoutTime = getRowReadoutTime(camKey);
      int numReadoutRows;
      switch (devices_.getMMDeviceLibrary(camKey)) {
      case HAMCAM:
         // device adapter provides readout time rounded to nearest 0.1ms; we
         // calculate it ourselves instead
         // note that Flash4's ROI is always set in increments of 4 pixels
         if (props_.getPropValueString(camKey, Properties.Keys.SENSOR_MODE,
               true).equals(Properties.Values.PROGRESSIVE.toString())) {
            numReadoutRows = roi.height;
         } else {
            numReadoutRows = roiReadoutRowsSplitReadout(roi, getSensorSize(camKey));
         }
         readoutTimeMs = ((float) (numReadoutRows * rowReadoutTime));
         break;
      case PCOCAM:
         numReadoutRows = roiReadoutRowsSplitReadout(roi, getSensorSize(camKey));
         readoutTimeMs = ((float) (numReadoutRows * rowReadoutTime));
         break;
      case ANDORCAM:
         numReadoutRows = roiReadoutRowsSplitReadout(roi, getSensorSize(camKey));
         readoutTimeMs = ((float) (numReadoutRows * rowReadoutTime));
         break;
      case DEMOCAM:
         numReadoutRows = roi.height;
         readoutTimeMs = ((float) (numReadoutRows * rowReadoutTime));
         break;
      default:
         break;
      }
      core_.logMessage("camera readout time computed as " + readoutTimeMs + 
            " for camera " + devices_.getMMDevice(camKey), true);
      return readoutTimeMs;  // assume 10ms readout if not otherwise possible to calculate
   }
   
   /**
    * private utility/shortcut function to set both SPIM cameras
    * @param trigMode
    */
   private void setSPIMTriggerMode(CameraModes.Keys trigMode) {
      // TODO look at whether both sides are active before setting cameras up
      setCameraTriggerMode(Devices.Keys.CAMERAA, trigMode);
      setCameraTriggerMode(Devices.Keys.CAMERAB, trigMode);
   }

   /**
    * Sets up SPIM cameras in correct mode for acquisition when called with true.
    * Uses the camera mode setting to see which external trigger mode.
    * @param acq true if setting for acquisition, false if setting for live
    */
   public void setSPIMCamerasForAcquisition(boolean acq) {
      if (acq) {
         CameraModes.Keys cameraMode = CameraModes.getKeyFromPrefCode(
               prefs_.getInt(MyStrings.PanelNames.SETTINGS.toString(),
                     Properties.Keys.PLUGIN_CAMERA_MODE, 0));
         setSPIMTriggerMode(cameraMode);
      } else { // for Live mode
         setSPIMTriggerMode(CameraModes.Keys.INTERNAL);
      }
   }
   

}
