///////////////////////////////////////////////////////////////////////////////
//FILE:           AutofocusDuo.java
//PROJECT:        Micro-Manager
//SUBSYSTEM:      Autofocus plugin for Micro-Manager that allows combining two autofocus methods
//-----------------------------------------------------------------------------
//
//AUTHOR:         Nico Stuurman, July 2009
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

import ij.IJ;
import ij.process.ImageProcessor;
import java.util.prefs.Preferences;

import mmcorej.CMMCore;

import org.micromanager.api.Autofocus;
import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.AutofocusBase;
import org.micromanager.utils.MMException;
import org.micromanager.utils.PropertyItem;
import org.micromanager.utils.ReportingUtils;

/**
 * ImageJ plugin wrapper for uManager.
 */

/* This plugin take a stack of snapshots and computes their sharpness

 */
public class AutofocusDuo extends AutofocusBase implements Autofocus  {

   //private static final String AF_SETTINGS_NODE = "micro-manager/extensions/autofocus";
   private static final String KEY_AUTOFOCUS1 = "AutoFocus-1";
   private static final String KEY_AUTOFOCUS2 = "AutoFocus-2";
   
   private static final String AF_DEVICE_NAME = "Duo";

   private ScriptInterface app_;
   private CMMCore core_;

   private boolean verbose_ = true; // displaying debug info or not

   private Preferences prefs_;

   private String autoFocus1_;
   private String autoFocus2_;

   public AutofocusDuo(){ //constructor!!!
      super();
      
      // set-up properties
      createProperty(KEY_AUTOFOCUS1, autoFocus1_);
      createProperty(KEY_AUTOFOCUS2, autoFocus2_);
      
      loadSettings();

      applySettings();
   }
   
   @Override
   public final void applySettings() {
      try {
         autoFocus1_ = getPropertyValue(KEY_AUTOFOCUS1);
         autoFocus2_ = getPropertyValue(KEY_AUTOFOCUS2);
      } catch (MMException e) {
         ReportingUtils.logError(e);
      }
      
   }

   public void run(String arg) {

      verbose_ = arg.compareTo("silent") != 0;

      if (arg.compareTo("options") == 0){
         app_.getAutofocusManager().showOptionsDialog();
      }  

      if (core_ == null) {
         // if core object is not set attempt to get its global handle
         core_ = app_.getMMCore();
      }

      if (core_ == null) {
         IJ.error("Unable to get Micro-Manager Core API handle.\n" +
         "If this module is used as ImageJ plugin, Micro-Manager Studio must be running first!");
         return;
      }
      
      applySettings();


      try{
         if (autoFocus1_ != null) {
            app_.getAutofocusManager().selectDevice(autoFocus1_);
            app_.getAutofocusManager().getDevice().fullFocus();
         }
         if (autoFocus2_ != null) {
            app_.getAutofocusManager().selectDevice(autoFocus2_);
            app_.getAutofocusManager().getDevice().fullFocus();
         }
         app_.getAutofocusManager().selectDevice(AF_DEVICE_NAME);
      }
      catch(Exception e)
      {
         e.printStackTrace();
         IJ.error(e.getMessage());
      }     
   }


   @Override
   public double fullFocus() {
      run("silent");
      return 0;
   }

   @Override
   public String getVerboseStatus() {
      return "OK";
   }

   @Override
   public double incrementalFocus() {
      run("silent");
      return 0;
   }

   @Override
   public void focus(double coarseStep, int numCoarse, double fineStep, int numFine) {
      run("silent");
   }

   @Override
   public PropertyItem[] getProperties() {
      // use default dialog
            
      String afDevices[] = app_.getAutofocusManager().getAfDevices();
      String allowedAfDevices[] = new String[afDevices.length - 1];

      try {
         PropertyItem p1 = getProperty(KEY_AUTOFOCUS1);
         PropertyItem p2 = getProperty(KEY_AUTOFOCUS2);
         boolean found1 = false;
         boolean found2 = false;
         int j =0;
         for (String afDevice : afDevices) {
            if (!afDevice.equals(AF_DEVICE_NAME)) {
               allowedAfDevices[j] = afDevice;
               j++;
               if (afDevice.equals(autoFocus1_)) {
                  found1 = true;
               }
               if (afDevice.equals(autoFocus2_)) {
                  found2 = true;
               }
            }
         }
         p1.allowed = allowedAfDevices;
         p2.allowed = allowedAfDevices;
         if (!found1)
            p1.value = allowedAfDevices[0];
         setProperty(p1);
         if (!found2)
            p2.value = allowedAfDevices[0];
         setProperty(p2);
      } catch (MMException e1) {
         // TODO Auto-generated catch block
         e1.printStackTrace();
      }

      return super.getProperties();
   }
      
   @Override
   public double computeScore(final ImageProcessor ip) {
      return 0.0;
   }
   
   @Override
   public double getCurrentFocusScore() {
      // TODO Auto-generated method stub
      return 0;
   }

   @Override
   public int getNumberOfImages() {
      // TODO Auto-generated method stub
      return 0;
   }

   @Override
   public String getDeviceName() {
      return AF_DEVICE_NAME;
   }
   
   @Override
   public void setApp(ScriptInterface app) {
      app_ = app;
      core_ = app.getMMCore();
   }

}   
