/*
 * Project: ASI Ring TIRF Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2022, Applied Scientific Instrumentation
 */

package com.asiimaging.tirf.model.devices;

import java.util.Objects;

import com.asiimaging.tirf.model.data.CameraName;
import com.asiimaging.tirf.model.data.CameraLibrary;
import mmcorej.CMMCore;
import org.micromanager.Studio;

public class Camera {

   private final Studio studio;
   private final CMMCore core;

   private String deviceName;
   private CameraLibrary cameraLibrary;
   private CameraName cameraName;


   public static class Properties {

      public static class Keys {
         public static String TRIGGER_MODE = "";
      }

      public static class Values {
         public static String EXTERNAL_TRIGGER = "";
         public static String INTERNAL_TRIGGER = "";
      }
   }

   public Camera(final Studio studio) {
      this.studio = Objects.requireNonNull(studio);
      core = studio.core();
      cameraLibrary = CameraLibrary.NOT_SUPPORTED;
      cameraName = CameraName.NOT_SUPPORTED;
      deviceName = "";
   }

   public String getDeviceName() {
      return deviceName;
   }

   public CameraName getCameraName() {
      return cameraName;
   }

   public boolean isSupported() {
      return cameraName != CameraName.NOT_SUPPORTED && cameraName != CameraName.DEMOCAM;
   }

   public CameraLibrary getDeviceLibrary() {
      try {
         return CameraLibrary.fromString(core.getDeviceLibrary(deviceName));
      } catch (Exception e) {
         studio.logs().logError("could not get the device library for the camera.");
         return CameraLibrary.NOT_SUPPORTED;
      }
   }

   /**
    * Returns true if the camera is supported.
    *
    * <p>This method sets up the camera device properties.
    *
    * @return true if the camera is supported
    */
   public boolean setupProperties() {
      boolean result = false;
      deviceName = core.getCameraDevice();
      cameraLibrary = getDeviceLibrary();
      cameraName = CameraName.fromString(getCameraNameString());
      switch (cameraLibrary) {
         case DEMOCAMERA:
            result = setupDevicePropertiesDemocam();
            break;
         case HAMAMATSUHAM:
            result = setupDevicePropertiesHamamatsu();
            break;
         case PVCAM:
            result = setupDevicePropertiesPVCAM();
            break;
         default:
            break;
      }
      return result;
   }

   /**
    * Returns the name of the camera.
    *
    * <p>This method should only be called after the CameraLibrary has been detected
    *
    * @return the name of the camera
    */
   public String getCameraNameString() {
      String result = "";
      try {
         switch (cameraLibrary) {
            case DEMOCAMERA: // uses "CamaraName" property
            case HAMAMATSUHAM:
               result = core.getProperty(deviceName, "CameraName");
               break;
            case PVCAM:
               result = core.getProperty(deviceName, "ChipName");
               break;
            default:
               break;
         }
      } catch (Exception e) {
         studio.logs().showError("could not get the camera name for deviceName: " + deviceName);
      }
      return result;
   }

   private boolean setupDevicePropertiesDemocam() {
      return cameraName == CameraName.DEMOCAM;
   }

   private boolean setupDevicePropertiesHamamatsu() {
      boolean result = false;
      switch (cameraName) {
         case HAMAMATSU_FUSION:
            Properties.Keys.TRIGGER_MODE = "TRIGGER SOURCE";
            Properties.Values.EXTERNAL_TRIGGER = "EXTERNAL";
            Properties.Values.INTERNAL_TRIGGER = "INTERNAL";
            setScanModeFast();
            setTriggerPolarity();
            result = true;
            break;
         default:
            // camera not supported
            break;
      }
      return result;
   }

   private boolean setupDevicePropertiesPVCAM() {
      boolean result = false;
      switch (cameraName) {
         case PRIME_95B:
            Properties.Keys.TRIGGER_MODE = "TriggerMode";
            Properties.Values.EXTERNAL_TRIGGER = "Edge Trigger";
            Properties.Values.INTERNAL_TRIGGER = "Internal Trigger";
            result = true;
            break;
         default:
            // camera not supported
            break;
      }
      return result;
   }

   public void setTriggerModeExternal() {
      try {
         core.setProperty(deviceName, Properties.Keys.TRIGGER_MODE,
               Properties.Values.EXTERNAL_TRIGGER);
      } catch (Exception e) {
         studio.logs().showMessage("could not set the trigger mode to external.");
      }
   }

   public void setTriggerModeInternal() {
      try {
         core.setProperty(deviceName, Properties.Keys.TRIGGER_MODE,
               Properties.Values.INTERNAL_TRIGGER);
      } catch (Exception e) {
         studio.logs().showMessage("could not set the trigger mode to internal.");
      }
   }

   public boolean isTriggerModeExternal() {
      String result = "";
      try {
         result = core.getProperty(deviceName, Properties.Keys.TRIGGER_MODE);
      } catch (Exception e) {
         studio.logs().showError("could not get the camera trigger mode.");
      }
      return result.equals(Properties.Values.EXTERNAL_TRIGGER);
   }

   /////// Hamamatsu - these properties are specific to Hamamatsu cameras
   public void setScanModeFast() {
      try {
         core.setProperty(deviceName, "ScanMode", "3");
      } catch (Exception e) {
         studio.logs().showError("could not set the \"ScanMode\" property for the camera.");
      }
   }

   public void setTriggerPolarity() {
      try {
         core.setProperty(deviceName, "TriggerPolarity", "POSITIVE");
      } catch (Exception e) {
         studio.logs().showError("could not set the \"TriggerPolarity\" property for the camera.");
      }
   }
   ///////

   public double getExposure() {
      double exposure = 0.0;
      try {
         exposure = core.getExposure();
      } catch (Exception e) {
         studio.logs().showError("could not get exposure!");
      }
      return exposure;
   }

   // Burst Acquisition

   public boolean isSequenceRunning() {
      boolean running = false;
      try {
         running = core.isSequenceRunning(deviceName);
      } catch (Exception e) {
         studio.logs().showError("could not determine if sequence is running!");
      }
      return running;
   }

   public void startSequenceAcquisition(final int numImages) {
      try {
         core.startSequenceAcquisition(deviceName, numImages, 0, true);
      } catch (Exception e) {
         studio.logs().showError("could not start sequence acquisition!");
      }
   }

   public void stopSequenceAcquisition() {
      try {
         if (isSequenceRunning()) {
            core.stopSequenceAcquisition(deviceName);
         }
      } catch (Exception e) {
         studio.logs().showError("could not stop sequence acquisition!");
      }
   }

}