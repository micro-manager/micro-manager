///////////////////////////////////////////////////////////////////////////////
//FILE:           HardwareFocusExtender.java
//PROJECT:        Micro-Manager
//SUBSYSTEM:      Autofocus plugin for Micro-Manager to assist hardware autofocus
//                to find focus
//-----------------------------------------------------------------------------
//
//AUTHOR:         Nico Stuurman, July 2015
//
//COPYRIGHT:       University of California San Francisco
//                
//LICENSE:        This file is distributed under the BSD license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//

package org.micromanager.autofocus;

import ij.process.ImageProcessor;
import mmcorej.CMMCore;
import mmcorej.DeviceType;
import org.micromanager.AutofocusPlugin;
import org.micromanager.Studio;
import org.micromanager.internal.utils.AutofocusBase;
import org.micromanager.internal.utils.NumberUtils;
import org.micromanager.internal.utils.PropertyItem;
import org.micromanager.internal.utils.ReportingUtils;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

/**
 *
 * @author nico
 */
@Plugin(type = AutofocusPlugin.class)
public class HardwareFocusExtender extends AutofocusBase implements AutofocusPlugin, SciJavaPlugin {

   private static final String AF_DEVICE_NAME = "HardwareFocusExtender";
   
   //Settings property names
   private static final String HARDWARE_AUTOFOCUS = "HardwareFocusDevice";
   private static final String ZDRIVE = "ZDrive";
   private static final String STEP_SIZE = "StepSize (um)";
   private static final String LOWER_LIMIT = "Lower limit (relative, um)";
   private static final String UPPER_LIMIT = "Upper limit (relative, um)";
   
   private Studio gui_;
   
   //These variables store current settings for the plugin
   private String hardwareFocusDevice_;
   private String zDrive_;
   private double stepSize_ = 5.0;
   private double lowerLimit_ = 300.0;
   private double upperLimit_ = 100.0;

   public HardwareFocusExtender() {
      super.createProperty(HARDWARE_AUTOFOCUS, "");
      super.createProperty(ZDRIVE, "");
      super.createProperty(STEP_SIZE, NumberUtils.doubleToDisplayString(stepSize_));
      super.createProperty(LOWER_LIMIT, NumberUtils.doubleToDisplayString(lowerLimit_));
      super.createProperty(UPPER_LIMIT, NumberUtils.doubleToDisplayString(upperLimit_));
   }

   @Override
   public PropertyItem[] getProperties() {
      CMMCore core = gui_.getCMMCore();
      String[] autofocusDevices = null;
      String[] zDrives = null;
      try {
         autofocusDevices = core.getLoadedDevicesOfType(
                 DeviceType.AutoFocusDevice).toArray();
         zDrives = core.getLoadedDevicesOfType(
                 DeviceType.StageDevice).toArray();
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }

      String[] allowedAFDevices = new String[autofocusDevices.length + 1];
      allowedAFDevices[0] = "";
      String[] allowedZDrives = new String[zDrives.length + 1];
      allowedZDrives[0] = "";

      try {
         PropertyItem p = getProperty(HARDWARE_AUTOFOCUS);
         boolean found = false;
         for (int i = 0; i < autofocusDevices.length; ++i) {
            allowedAFDevices[i + 1] = autofocusDevices[i];
            if (p.value.equals(autofocusDevices[i])) {
               found = true;
            }
         }
         p.allowed = allowedAFDevices;
         if (!found) {
            p.value = allowedAFDevices[0];
         }
         setProperty(p);
      } catch (Exception e) {
         ReportingUtils.logError(e);
      }

      try {
         PropertyItem p = getProperty(ZDRIVE);
         boolean found = false;
         for (int i = 0; i < zDrives.length; ++i) {
            allowedZDrives[i + 1] = zDrives[i];
            if (p.value.equals(zDrives[i])) {
               found = true;
            }
         }
         p.allowed = allowedZDrives;
         if (!found) {
            p.value = allowedZDrives[0];
         }
         setProperty(p);
      } catch (Exception e) {
         ReportingUtils.logError(e);
      }

      return super.getProperties();
   }

   @Override
   public final void applySettings() {
      try {
         hardwareFocusDevice_ = getPropertyValue(HARDWARE_AUTOFOCUS);
         zDrive_ = getPropertyValue(ZDRIVE);
         stepSize_ = NumberUtils.displayStringToDouble(
                 getPropertyValue(STEP_SIZE));
         lowerLimit_ = NumberUtils.displayStringToDouble(
                 getPropertyValue(LOWER_LIMIT));
         upperLimit_ = NumberUtils.displayStringToDouble(
                 getPropertyValue(UPPER_LIMIT));
      } catch (Exception e) {
         ReportingUtils.logError(e);
      }
   }

   @Override
   public void setContext(Studio app) {
      gui_ = app;
      // We subscribe to the AutofocusPluginShouldInitialize event.
      gui_.events().registerForEvents(this);
   }

   /**
    *
    * @return z position for in focus image. Returns 0 if no focused position was found.
    * @throws Exception thrown by hardware
    */
   @Override
   public double fullFocus() throws Exception {
      applySettings();
      gui_.logs().logDebugMessage("HardwareFocusExtender: Beginning fullFocus.");
      if (hardwareFocusDevice_ == null || zDrive_ == null) {
         gui_.logs().showError("HardwareFocusExtender: Autofocus, and/or ZDrive have not been set");
         return 0.0;
      }
      CMMCore core = gui_.getCMMCore();
      try {
         core.getDeviceType(hardwareFocusDevice_);
         core.getDeviceType(zDrive_);
      } catch (Exception ex) {
         gui_.logs().showError(
               "HardwareFocusExtender: Hardware focus device and/or ZDrive were not set");
         return 0.0;
      }
      double pos = 0.0;
      try {
         core.setAutoFocusDevice(hardwareFocusDevice_);
         pos = core.getPosition(zDrive_);
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
      boolean success = testFocus();
      //Search from 0 to `lowerLimit` for a focus lock.
      for (int i = 0; i < lowerLimit_ / stepSize_ && !success; i++) {
         double z = pos + (stepSize_ * -i);
         try {
            core.setPosition(zDrive_, z);
            core.waitForDevice(zDrive_);
         } catch (Exception ex) {
            gui_.logs().showError(ex, "Failed to set Z position");
         }
         gui_.logs().logDebugMessage(String.format(
               "HardwareFocusExtender: Checking hardware focus at %.2f.", z));
         success = testFocus();
      }
      //If searching to `lowerLimit` failed try searching from 0 to `upperLimit`
      for (int i = 0; i < upperLimit_ / stepSize_ && !success; i++) {
         double z = pos + (stepSize_ * i);
         try {
            core.setPosition(zDrive_, z);
            core.waitForDevice(zDrive_);
         } catch (Exception ex) {
            gui_.logs().showError(ex, "HardwareFocusExtender: Failed to set Z position");
         }
         gui_.logs().logDebugMessage(String.format(
               "HardwareFocusExtender: Checking hardware focus at %.2f.", z));
         success = testFocus();
      }
      if (success) {
         try {
            double currentZ = core.getPosition(zDrive_);
            gui_.logs().logDebugMessage(String.format(
                  "HardwareFocusExtender: Successfully found focus at %.2f.", currentZ));
            return currentZ;
         } catch (Exception ex) {
            ReportingUtils.logError(ex);
         }
      }
      gui_.logs().logDebugMessage("HardwareFocusExtender: Failed to find focus. Returning 0.");
      return 0.0; //No focus was found.
   }


   /**
    * Activates the autofocus devices.
    *
    * @return true when the device locks, false otherwise
    */
   private boolean testFocus() {
      CMMCore core = gui_.getCMMCore();
      try {
         // specific for Nikon TI 1 PFS. If the TI reports that it is out of search range
         // there is no point trying fullFocus.
         if (core.getDeviceLibrary(hardwareFocusDevice_).equals("NikonTI")
               && core.hasProperty(hardwareFocusDevice_, "Status")) {
            String result = core.getProperty(hardwareFocusDevice_, "Status");
            if (result.equals("Out of focus search range")) { 
               return false;
            }
         }
         core.fullFocus();
         core.waitForDevice(hardwareFocusDevice_);
      } catch (Exception ex) {
         // focus failed
         return false;
      }
      return true;
   }
   
   @Override
   public double incrementalFocus() throws Exception {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   @Override
   public int getNumberOfImages() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   @Override
   public String getVerboseStatus() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   @Override
   public double getCurrentFocusScore() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   @Override
   public double computeScore(ImageProcessor impro) {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   @Override
   public String getName() {
      return AF_DEVICE_NAME;
   }

   @Override
   public String getHelpText() {
      return AF_DEVICE_NAME;
   }

   @Override
   public String getVersion() {
      return "1.0";
   }

   @Override
   public String getCopyright() {
      return "University of California, 2015";
   }
}
