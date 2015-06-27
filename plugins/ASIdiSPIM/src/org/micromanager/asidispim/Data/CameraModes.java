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

import org.micromanager.asidispim.Utils.DevicesListenerInterface;
import org.micromanager.asidispim.Utils.MyDialogUtils;


/**
 * Class that holds utilities camera readout mode
 * 
 * @author Jon
 */
public class CameraModes {
   
   private Devices devices_;   // object holding information about selected/available devices
   private Properties props_;  // object handling all property read/writes
   private Prefs prefs_;
   
   /**
    * Enum to store the acquisition mode along with associated preference code.
    * Wanted to store integer code instead of string so that the mode descriptions
    * can be easily changed. 
    */
   public static enum Keys { 
      EDGE("Edge trigger", 1),
      OVERLAP("Overlap/synchronous", 2),
      LEVEL("Level trigger (not yet implemented)", 3),
      PSEUDO_OVERLAP("Pseudo Overlap", 4),
      INTERNAL("Internal", 0);
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
   
   public CameraModes(Devices devices, Properties props, Prefs prefs) {
      devices_ = devices;
      props_ = props;
      prefs_ = prefs;
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
            // if existing selection isn't valid now then write new selection to prefs
            MyDialogUtils.showError("For preference " + Properties.Keys.PLUGIN_CAMERA_MODE.toString()
                  + " the previous selection \""
                  + getKeyFromPrefCode(origCode) + "\" is not valid.  Changing to default.");
            if (jcb_.getSelectedItem() != null) {
               prefs_.putInt(MyStrings.PanelNames.SETTINGS.toString(),
                  Properties.Keys.PLUGIN_CAMERA_MODE, ((Keys) jcb_.getSelectedItem()).getPrefCode());
            }
         }
      }//updateSelections
      
      
      /**
       * Does camera support overlap/synchronous mode?
       * @param devKey
       * @return
       */
      private boolean cameraSupportsOverlap(Devices.Keys devKey) {
         Devices.Libraries devLib = devices_.getMMDeviceLibrary(devKey);
         return (devLib == Devices.Libraries.HAMCAM ||
               devLib == Devices.Libraries.ANDORCAM ||
               devLib == Devices.Libraries.DEMOCAM);
      }
      
      /**
       * Does camera support pseudo overlap/synchronous mode?
       * This is just PCO for the moment.
       * @param devKey
       * @return
       */
      private boolean cameraSupportsPseudoOverlap(Devices.Keys devKey) {
         Devices.Libraries devLib = devices_.getMMDeviceLibrary(devKey);
         return (devLib == Devices.Libraries.PCOCAM);
      }
      
      private boolean cameraInvalid(Devices.Keys devKey) {
         Devices.Libraries devLib = devices_.getMMDeviceLibrary(devKey);
         return (devLib == Devices.Libraries.UNKNOWN ||
               devLib == Devices.Libraries.NODEVICE);
      }
      
      /**
       * Returns whatever acquisition modes are available based on devices
       * and installed firmware.  Can be expanded in the future
       * (will use devices_ and props_)
       * @return
       */
      private List<Keys> getValidModeKeys() {
         // TODO add level ("bulb") triggering
         List<Keys> keyList = new ArrayList<Keys>();
         boolean twoSided = 
               (props_.getPropValueInteger(Devices.Keys.PLUGIN,
            Properties.Keys.PLUGIN_NUM_SIDES)) != 1;
               //true; // ASIdiSPIM.getFrame().getAcquisitionPanel().isTwoSided();
         boolean sideAFirst = 
               (props_.getPropValueString(Devices.Keys.PLUGIN,
                     Properties.Keys.PLUGIN_FIRST_SIDE).equals("A"));
                     //true; // ASIdiSPIM.getFrame().getAcquisitionPanel().isFirstSideA();
         
         if (twoSided) {
            if (cameraInvalid(Devices.Keys.CAMERAA) ||
                  cameraInvalid(Devices.Keys.CAMERAB)) {
               return keyList;
            }
            keyList.add(Keys.EDGE);
//          keyList.add(Keys.LEVEL);
            if (cameraSupportsOverlap(Devices.Keys.CAMERAA) &&
                  cameraSupportsOverlap(Devices.Keys.CAMERAB)) {
               keyList.add(Keys.OVERLAP);
            } else if (cameraSupportsPseudoOverlap(Devices.Keys.CAMERAA) &&
                  cameraSupportsPseudoOverlap(Devices.Keys.CAMERAB)) {
               keyList.add(Keys.PSEUDO_OVERLAP);
            }
         } else {
            Devices.Keys camKey = sideAFirst ? Devices.Keys.CAMERAA : Devices.Keys.CAMERAB;
            if (cameraInvalid(camKey)) {
               return keyList;
            }
            keyList.add(Keys.EDGE);
//          keyList.add(Keys.LEVEL);
            if (cameraSupportsOverlap(camKey)) {
               keyList.add(Keys.OVERLAP);
            } else if (cameraSupportsPseudoOverlap(camKey)) {
               keyList.add(Keys.PSEUDO_OVERLAP);
            }
         }

         return keyList;
      }

   } // end CameraModeComboBoxListener

}
