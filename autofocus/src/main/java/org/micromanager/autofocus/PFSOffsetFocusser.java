///////////////////////////////////////////////////////////////////////////////
//FILE:           PFSOffsetFocusser.java
//PROJECT:        Micro-Manager
//SUBSYSTEM:      Autofocus plugin for Micro-Manager to assist hardware autofocus
//                to find focus
//-----------------------------------------------------------------------------
//
//AUTHOR:         Nico Stuurman, July 2023
//
//COPYRIGHT:      Altos Labs
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
import org.micromanager.internal.utils.PropertyItem;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

/**
 *
 * @author nico
 */
@Plugin(type = AutofocusPlugin.class)
public class PFSOffsetFocusser extends AutofocusBase implements AutofocusPlugin, SciJavaPlugin {

   private static final String AF_DEVICE_NAME = "PFSOffsetFocusser";

   //Settings property names
   private static final String SOFTWARE_AUTOFOCUS = "SoftwareFocusMethod";
   private static final String PFS = "PFS";
   private static final String ZDRIVE = "ZDrive";

   private Studio studio_;

   //These variables store current settings for the plugin
   private String softwareFocusMethod_;
   private String zDrive_;
   private String pFS_;

   public PFSOffsetFocusser() {
      super.createProperty(SOFTWARE_AUTOFOCUS, "");
      super.createProperty(ZDRIVE, "");
      super.createProperty(PFS, "");
   }

   @Override
   public PropertyItem[] getProperties() {
      CMMCore core = studio_.getCMMCore();
      String[] autofocusDevices = null;
      String[] zDrives = null;
      String[] pFss = null;
      try {
         autofocusDevices = studio_.getAutofocusManager().getAllAutofocusMethods().toArray(
               new String[0]);
         zDrives = core.getLoadedDevicesOfType(DeviceType.StageDevice).toArray();
         pFss = core.getLoadedDevicesOfType(DeviceType.AutoFocusDevice).toArray();
      } catch (Exception ex) {
         studio_.logs().logError(ex);
      }

      String[] allowedAFDevices = new String[autofocusDevices.length + 1];
      allowedAFDevices[0] = "";
      String[] allowedZDrives = new String[zDrives.length + 1];
      allowedZDrives[0] = "";
      String[] allowedPFSs = new String[pFss.length + 1];
      pFss[0] = "";

      try {
         PropertyItem p = getProperty(SOFTWARE_AUTOFOCUS);
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
         studio_.logs().logError(e);
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
         studio_.logs().logError(e);
      }

      try {
         PropertyItem p = getProperty(PFS);
         boolean found = false;
         for (int i = 0; i < pFss.length; i++) {
            allowedPFSs[i + 1] = pFss[i];
            if (p.value.equals((pFss[i]))) {
               found = true;
            }
         }
         p.allowed = allowedPFSs;
         if (!found) {
            p.value = allowedPFSs[0];
         }
         setProperty(p);
      } catch (Exception e) {
         studio_.logs().logError(e);
      }

      return super.getProperties();
   }

   @Override
   public final void applySettings() {
      try {
         softwareFocusMethod_ = getPropertyValue(SOFTWARE_AUTOFOCUS);
         zDrive_ = getPropertyValue(ZDRIVE);
      } catch (Exception e) {
         studio_.logs().logError(e);
      }
   }

   @Override
   public void setContext(Studio app) {
      studio_ = app;
      studio_.events().registerForEvents(this); //We subscribe to the AutofocusPluginShouldInitialize event.
   }

   /**
    *
    * @return z position for in focus image. Returns 0 if no focused position was found.
    * @throws Exception
    */
   @Override
   public double fullFocus() throws Exception {
      applySettings();
      studio_.logs().logDebugMessage("HardwareFocusExtender: Beginning fullFocus.");
      if (softwareFocusMethod_ == null || zDrive_ == null || pFS_ == null) {
         studio_.logs().showError("PFSFocusser: Autofocus, and/or ZDrive have not been set");
         return 0.0;
      }
      CMMCore core = studio_.getCMMCore();
      try {
         studio_.getAutofocusManager().setAutofocusMethodByName(softwareFocusMethod_);
         core.getDeviceType(zDrive_);
         core.getDeviceName(pFS_);
      } catch (Exception ex) {
         studio_.logs().showError("HardwareFocusExtender: Hardware focus device and/or ZDrive were not set");
         return 0.0;
      }
      double pos = 0.0;
      try {
         String autofocusDevice = core.getAutoFocusDevice();
         boolean continuousFocusOn = core.isContinuousFocusEnabled();
         core.enableContinuousFocus(false);
         String zStage = core.getFocusDevice();
         core.setFocusDevice(zStage);
         String originalAutofocusMethod = studio_.getAutofocusManager().getAutofocusMethod().getName();
         studio_.getAutofocusManager().setAutofocusMethodByName(softwareFocusMethod_);
         studio_.getAutofocusManager().getAutofocusMethod().fullFocus();
         pos = core.getPosition(zDrive_);
         core.setAutoFocusDevice(pFS_);
         core.enableContinuousFocus(true);
         Thread.sleep(1000);
         double pos2 = core.getPosition(zDrive_);

      } catch (Exception ex) {
         studio_.logs().logError(ex);
      }
      //boolean success = testFocus();

      return 0.0; //No focus was found.
   }

   @Override
   public double incrementalFocus() throws Exception {
      return 0;
   }

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
      return "Altos Labs, 2023";
   }
}
