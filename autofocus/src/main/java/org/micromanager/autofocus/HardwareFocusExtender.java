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

import java.text.ParseException;
import mmcorej.CMMCore;
import mmcorej.DeviceType;
import org.micromanager.internal.MMStudio;

import org.micromanager.Studio;

import org.micromanager.AutofocusPlugin;
import org.micromanager.internal.utils.AutofocusBase;
import org.micromanager.internal.utils.NumberUtils;
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
   private static final String HARDWARE_AUTOFOCUS = "HardwareFocusDevice";
   private static final String ZDRIVE = "ZDrive";
   private static final String STEP_SIZE = "StepSize (um)";
   private static final String LOWER_LIMIT = "Lower limit (relative, um)";
   private static final String UPPER_LIMIT = "Upper limit (relative, um)";
   
   private Studio gui_;
   private boolean settingsLoaded_ = false;
   private boolean hardwareDeviceAvailable_ = true;
   private String hardwareFocusDevice_;
   private String zDrive_;
   private double stepSize_ = 5.0;
   private double lowerLimit_ = 300.0;
   private double upperLimit_ = 100.0;
   
   public HardwareFocusExtender(){ 
      super();
      
      // set-up properties
      CMMCore core = MMStudio.getInstance().getCore();
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
      if (autofocusDevices == null || autofocusDevices.length < 1 ||
              zDrives == null || zDrives.length < 1) {
         // needed hardware not available, bail out
         hardwareDeviceAvailable_ = false;
         return;
      }
      createProperty(HARDWARE_AUTOFOCUS, hardwareFocusDevice_, autofocusDevices);
      createProperty(ZDRIVE, zDrive_, zDrives);
      createProperty(STEP_SIZE, NumberUtils.doubleToDisplayString(stepSize_));
      createProperty(LOWER_LIMIT, NumberUtils.doubleToDisplayString(lowerLimit_));
      createProperty(UPPER_LIMIT, NumberUtils.doubleToDisplayString(upperLimit_));
      loadSettings();

      applySettings();
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
      if (!hardwareDeviceAvailable_) {
         return;
      }
      gui_ = app;
      if (!settingsLoaded_) {
         super.loadSettings();
         settingsLoaded_ = true;
      }
   }

   /**
    *
    * @return z position for in focus image
    * @throws Exception
    */
   @Override
   public double fullFocus() throws Exception {
      if (hardwareFocusDevice_ == null || zDrive_ == null) {
         ReportingUtils.showError("Autofocus, and/or ZDrive have not been set");
         return 0.0;
      }
      CMMCore core = gui_.getCMMCore();
      try {
         core.getDeviceType(hardwareFocusDevice_);
         core.getDeviceType(zDrive_);
      } catch (Exception ex) {
         ReportingUtils.showError("Hardware focus device and/or ZDrive were not set");
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
      for (int i = 0; i < lowerLimit_ / stepSize_ && !success; i++) {
         try {
            core.setPosition(zDrive_, pos + (stepSize_ * -i) );
            core.waitForDevice(zDrive_);
         } catch (Exception ex) {
            ReportingUtils.showError(ex, "Failed to set Z position");
         }
         success = testFocus();
      }
      for (int i = 0; i < upperLimit_ / stepSize_ && !success; i++) {
        try {
            core.setPosition(zDrive_, pos + (stepSize_ * i) );
            core.waitForDevice(zDrive_);
         } catch (Exception ex) {
            ReportingUtils.showError(ex, "Failed to set Z position");
         }
         success = testFocus();
      }
      if (success) {
         try {
            return core.getPosition(zDrive_);
         } catch (Exception ex) {
            ReportingUtils.logError(ex);
         }
      }
      
      return 0.0;
   }

   /**
    * Activates the autofocus devices
    * @return true when the device locks, false otherwise
    */
   private boolean testFocus() {
      CMMCore core = gui_.getCMMCore();
      try {
         // specific for Nikon PFS.  Check if the PFS is within range instead of
         // trying to lock
         if (core.hasProperty(hardwareFocusDevice_, "Status")) {
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
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   @Override
   public int getNumberOfImages() {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   @Override
   public String getVerboseStatus() {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   @Override
   public double getCurrentFocusScore() {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   @Override
   public double computeScore(ImageProcessor impro) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
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
