///////////////////////////////////////////////////////////////////////////////
//FILE:          AcquisitionModes.java
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


/**
 * Class that holds utilities related to SPIM acquisition mode
 * 
 * @author Jon
 */
public class AcquisitionModes {
   
   private final Devices devices_;   // object holding information about selected/available devices
   private final Properties props_;  // object handling all property read/writes
   private final Prefs prefs_;
   
   /**
    * Enum to store the acquisition mode along with associated preference code.
    * Wanted to store integer code instead of string so that the mode descriptions
    * can be easily changed. 
    */
   public static enum Keys { 
      PIEZO_SLICE_SCAN("Synchronous piezo/slice scan", 1),
      SLICE_SCAN_ONLY( "Slice scan only (beam thickness)", 2),
      NO_SCAN(         "No scan (vibration)", 3),
      STAGE_SCAN(      "Stage scan", 4),
      STAGE_SCAN_INTERLEAVED("Stage scan interleaved", 5),  // TODO implement
      NONE(            "None", 0);
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
   
   public AcquisitionModes(Devices devices, Properties props, Prefs prefs) {
      devices_ = devices;
      props_ = props;
      prefs_ = prefs;
   }
   
   /**
    * @param prefCode
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
      ActionListener l = new ComboBoxListener(jcb);
      jcb.addActionListener(l);
      // when devices are changed we want to regenerate the list
      devices_.addListener((DevicesListenerInterface) l);
      return jcb;
   }
   
   private class ComboBoxListener implements ActionListener, DevicesListenerInterface {
      
      private final JComboBox jcb_;
      
      public ComboBoxListener(JComboBox jcb) {
         jcb_ = jcb;
         updateSelections();  // do initial rendering
      }
      
      @Override
      public void actionPerformed(ActionEvent ae) {
         prefs_.putInt(MyStrings.PanelNames.ACQUSITION.toString(),
               Properties.Keys.PLUGIN_ACQUSITION_MODE,
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
         int origCode = prefs_.getInt(MyStrings.PanelNames.ACQUSITION.toString(),
               Properties.Keys.PLUGIN_ACQUSITION_MODE, 0);
         
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
         }
      }//updateSelections


      /**
       * Returns whatever acquisition modes are available based on devices
       * and installed firmware.  Will need to be expanded in the future
       * (will use devices_ and props_)
       * @return
       */
      private List<Keys> getValidModeKeys() {
         List<Keys> keyList = new ArrayList<Keys>();
         keyList.add(Keys.PIEZO_SLICE_SCAN);
         keyList.add(Keys.SLICE_SCAN_ONLY);
         keyList.add(Keys.NO_SCAN);
         if (devices_.isTigerDevice(Devices.Keys.XYSTAGE)
               && props_.hasProperty(Devices.Keys.XYSTAGE, Properties.Keys.STAGESCAN_NUMLINES)) {
            keyList.add(Keys.STAGE_SCAN);
            keyList.add(Keys.STAGE_SCAN_INTERLEAVED);
         }
         return keyList;
      }

      
   }

}
