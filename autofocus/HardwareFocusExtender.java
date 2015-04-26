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


import ij.process.ImageProcessor;

import java.text.ParseException;
import mmcorej.CMMCore;
import mmcorej.DeviceType;
import org.micromanager.MMStudio;

import org.micromanager.api.ScriptInterface;

import org.micromanager.utils.AutofocusBase;
import org.micromanager.utils.MMException;
import org.micromanager.utils.NumberUtils;
import org.micromanager.utils.ReportingUtils;



/**
 *
 * @author nico
 */
public class HardwareFocusExtender  extends AutofocusBase implements org.micromanager.api.Autofocus{

   private static final String AF_DEVICE_NAME = "HardwareFocusExtender";
   private static final String HARDWARE_AUTOFOCUS = "HardwareFocusDevice";
   private static final String ZDRIVE = "ZDrive";
   private static final String STEP_SIZE = "StepSize (um)";
   private static final String LOWER_LIMIT = "Lower limit (relative, um)";
   private static final String UPPER_LIMIT = "Upper limit (relative, um)";
   
   private ScriptInterface gui_;
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
      } catch (MMException e) {
         ReportingUtils.logError(e);
      } catch (ParseException ex) {
         ReportingUtils.logError(ex);
      }
   }

   @Override
   public void setApp(ScriptInterface app) {
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
    * @throws MMException
    */
   @Override
   public double fullFocus() throws MMException {
      CMMCore core = gui_.getMMCore();
      try {
         core.setAutoFocusDevice(hardwareFocusDevice_);
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
      boolean success = testFocus();
      for (int i = 0; i < lowerLimit_ / stepSize_ && !success; i++) {
         try {
            core.setPosition(zDrive_, stepSize_ * -i);
         } catch (Exception ex) {
            ReportingUtils.showError(ex, "Failed to set Z position");
         }
         success = testFocus();
      }
      for (int i = 0; i < upperLimit_ / stepSize_ && !success; i++) {
                  try {
            core.setPosition(zDrive_, stepSize_ * i);
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
      try {
            gui_.getMMCore().fullFocus();
         } catch (Exception ex) {
            // focus failed
            return false;
         }
      return true;
   }
   
   @Override
   public double incrementalFocus() throws MMException {
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
   public String getDeviceName() {
      return AF_DEVICE_NAME;
   }

   @Override
   public double computeScore(ImageProcessor impro) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   @Override
   public void focus(double coarseStep, int numCoarse, double fineStep, int numFine) throws MMException {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }
   
}
