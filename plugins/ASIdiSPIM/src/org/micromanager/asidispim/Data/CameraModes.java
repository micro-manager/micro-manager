///////////////////////////////////////////////////////////////////////////////
//FILE:          CameraModes.java
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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;

import org.micromanager.asidispim.ASIdiSPIM;
import org.micromanager.asidispim.Utils.DevicesListenerInterface;
import org.micromanager.asidispim.Utils.ListeningJPanel;
import org.micromanager.asidispim.Utils.MyDialogUtils;


/**
 * Class that holds utilities camera readout mode
 * 
 * @author Jon
 */
public class CameraModes {
   
   private Devices devices_;   // object holding information about selected/available devices
   private Prefs prefs_;
   
   /**
    * Enum to store the acquisition mode along with associated preference code.
    * Wanted to store integer code instead of string so that the mode descriptions
    * can be easily changed. 
    */
   public static enum Keys { 
      EDGE("Edge trigger", 1),
      OVERLAP("Overlap/synchronous", 2),
      LEVEL("Level trigger", 3),
      PSEUDO_OVERLAP("Pseudo Overlap", 4),
      LIGHT_SHEET("Light sheet", 5),
      INTERNAL("Internal", 0),
      ;
      private final String text;
      private final int prefCode;
      Keys(String text, int prefCode) {
         this.text = text;
         this.prefCode = prefCode;
      }
      @Override
      public String toString() {
         return text;
      }
      int getPrefCode() {
         return prefCode;
      }
   };
   
   public CameraModes(Devices devices, Prefs prefs) {
      devices_ = devices;
      prefs_ = prefs;
   }
   
   public boolean equals(CameraModes a) {
      return (this.toString().equals(a.toString()));
   }
   
   /**
    * @return null if prefCode not found, or the Key if it is
    */
   public static Keys getKeyFromPrefCode(int prefCode) {
      for (Keys k : Keys.values()) {
         if (k.getPrefCode() == prefCode) {
            return k;
         }
      }
      return null;
   }
   
   /**
    * Does camera support overlap/synchronous mode?
    * @param devKey
    * @return
    */
   private static boolean cameraSupportsOverlap(Devices.Libraries camLib) {
      return (camLib == Devices.Libraries.HAMCAM ||
            camLib == Devices.Libraries.ANDORCAM ||
            camLib == Devices.Libraries.DEMOCAM);
   }
   
   /**
    * Does camera support pseudo overlap/synchronous mode?
    * Both PCO and Photometrics 95B do
    * @param devKey
    * @return
    */
   private static boolean cameraSupportsPseudoOverlap(Devices.Libraries camLib) {
      return (camLib == Devices.Libraries.PCOCAM ||
            camLib == Devices.Libraries.PVCAM);
   }
   
   /**
    * Does camera support level trigger mode?
    * @param devKey
    * @return
    */
   private static boolean cameraSupportsLevelTrigger(Devices.Libraries camLib) {
      return (camLib == Devices.Libraries.HAMCAM ||
            camLib == Devices.Libraries.ANDORCAM ||
            camLib == Devices.Libraries.PCOCAM
            );
   }
   
   /**
    * Does camera support light sheet mode?
    * @param devKey
    * @return
    */
   private static boolean cameraSupportsLightSheetTrigger(Devices.Libraries camLib) {
      return (camLib == Devices.Libraries.HAMCAM ||
            camLib == Devices.Libraries.ANDORCAM ||
            camLib == Devices.Libraries.PVCAM || // not sure about this
            camLib == Devices.Libraries.DEMOCAM  // for testing only
            );
   }
   
   /**
    * Does the camera exist and is it from a known camera library? 
    * @param devKey
    * @return
    */
   private static boolean cameraInvalid(Devices.Libraries camLib) {
      return (camLib == Devices.Libraries.UNKNOWN ||
            camLib == Devices.Libraries.NODEVICE);
   }
   
   /**
    * get list of all valid camera triggering modes for specified camera library
    * (can't do with Device key because needs to be static for reference elsewhere)
    * @param camLib
    * @return
    */
   public static List<Keys> getValidModeKeys(Devices.Libraries camLib) {
      List<Keys> keyList = new ArrayList<Keys>();
      if (!cameraInvalid(camLib)) {
         keyList.add(Keys.EDGE);
         if (cameraSupportsLevelTrigger(camLib)) {
            keyList.add(Keys.LEVEL);
         }
         if (cameraSupportsOverlap(camLib)) {
            keyList.add(Keys.OVERLAP);
         }
         if (cameraSupportsPseudoOverlap(camLib)) {
            keyList.add(Keys.PSEUDO_OVERLAP);
         }
         if (cameraSupportsLightSheetTrigger(camLib)) {
            keyList.add(Keys.LIGHT_SHEET);
         }
      }
      return keyList;
   }
   
   public JComboBox getComboBox() {
      JComboBox jcb = new JComboBox();
      ActionListener l = new CameraModeComboBoxListener(jcb);
      jcb.addActionListener(l);
      // when devices are changed we want to regenerate the list
      devices_.addListener((DevicesListenerInterface) l);
      return jcb;
   }
   
   private class CameraModeComboBoxListener implements ActionListener,
      DevicesListenerInterface {
      
      private final JComboBox jcb_;
      
      public CameraModeComboBoxListener(JComboBox jcb) {
         jcb_ = jcb;
         updateSelections();  // do initial rendering
      }
      
      @Override
      public void actionPerformed(ActionEvent ae) {
         prefs_.putInt(MyStrings.PanelNames.SETTINGS.toString(),
               Properties.Keys.PLUGIN_CAMERA_MODE,
               ((Keys) jcb_.getSelectedItem()).getPrefCode());
         // notify listeners of this change, at present just the two setup panels
         try {
            ListeningJPanel p = ASIdiSPIM.getFrame().getSetupPanel(Devices.Sides.A);
            p.cameraModeChange();
            p =  ASIdiSPIM.getFrame().getSetupPanel(Devices.Sides.B);
            p.cameraModeChange();
            p = ASIdiSPIM.getFrame().getAcquisitionPanel();
            p.cameraModeChange();
         } catch (Exception ex) {
            // do nothing
         }
      }
      
      /**
       * called whenever one of the devices is changed in the "Devices" tab
       */
      @Override
      public void devicesChangedAlert() {
         updateSelections();
      }
      
      /**
       * Resets the items in the combo box based on devices available.
       * Besides being called on original combo box creation, it is called 
       * whenever something in the devices tab is changed
       */
      private void updateSelections() {
         // save the existing selection if it exists
         // could use props_ with PLUGIN device too
         int origCode = prefs_.getInt(MyStrings.PanelNames.SETTINGS.toString(),
               Properties.Keys.PLUGIN_CAMERA_MODE, 0);
         
         DefaultComboBoxModel cbModel = new DefaultComboBoxModel();
         
         List<Keys> validModeKeys = getValidModeKeys();
         Keys origItem = null;
         for (Keys k : validModeKeys) {
            cbModel.addElement(k);
            if (k.getPrefCode() == origCode) {
               origItem = k;
            }
         }
         jcb_.setModel(cbModel);
         if (origItem != null) {
            jcb_.setSelectedItem(origItem);
         } else {
            if (jcb_.getSelectedItem() != null) {
               // if existing selection isn't valid now then write new selection to prefs
               MyDialogUtils.showError("For preference " + Properties.Keys.PLUGIN_CAMERA_MODE.toString()
                     + " the previous selection \""
                     + getKeyFromPrefCode(origCode) + "\" is not valid.  Changing to default.");
               prefs_.putInt(MyStrings.PanelNames.SETTINGS.toString(),
                  Properties.Keys.PLUGIN_CAMERA_MODE, ((Keys) jcb_.getSelectedItem()).getPrefCode());
            }
         }
      }//updateSelections
      
      
      /**
       * Returns whatever acquisition modes are available based on devices
       * and installed firmware.  Can be expanded in the future.
       * Currently returns all modes supported by either camera and then we check on acquisition
       *    to make sure the selected camera supports the selected mode.
       * @return
       */
      private List<Keys> getValidModeKeys() {
         List<Keys> keyList = CameraModes.getValidModeKeys(devices_.getMMDeviceLibrary(Devices.Keys.CAMERAA));
         List<Keys> keyListB = CameraModes.getValidModeKeys(devices_.getMMDeviceLibrary(Devices.Keys.CAMERAB));
         for (Keys k : keyListB) {
            if (!keyList.contains(k)) {
               keyList.add(k);
            }
         }
         return keyList;
      }

   } // end CameraModeComboBoxListener

}
