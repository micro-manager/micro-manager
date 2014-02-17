///////////////////////////////////////////////////////////////////////////////
//FILE:          BeamSubPanel.java
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

package org.micromanager.asidispim;

import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

import javax.swing.JCheckBox;
import javax.swing.JLabel;

import org.micromanager.asidispim.Data.Devices;
import org.micromanager.asidispim.Data.Prefs;
import org.micromanager.asidispim.Data.Properties;
import org.micromanager.asidispim.Utils.DevicesListenerInterface;
import org.micromanager.asidispim.Utils.ListeningJPanel;
import org.micromanager.asidispim.Utils.UpdateFromPropertyListenerInterface;

import net.miginfocom.swing.MigLayout;

/**
 *
 * @author Jon
 */
@SuppressWarnings("serial")
public final class BeamSubPanel extends ListeningJPanel {
   
   private final Devices devices_;
   private final Prefs prefs_;
   private final Properties props_;
   private final String instanceLabel_;
   private final Devices.Sides side_;
   private final Devices.Sides otherSide_;
   
   private final JCheckBox beamABox_;
   private final JCheckBox beamBBox_;
   private final JCheckBox sheetABox_;
   private final JCheckBox sheetBBox_;
   private final JCheckBox updateOnTab_;
   private final ItemListener disableSheetA_;
   private final ItemListener disableSheetB_;
   
   /**
    * 
    * @param devices the (single) instance of the Devices class
    */
   public BeamSubPanel(Devices devices, String instanceLabel, 
         Devices.Sides side, Prefs prefs, Properties props) {    
      super ("Beam_"+instanceLabel,
            new MigLayout(
              "", 
              "[right]8[left]",
              "[]4[]"));

      devices_ = devices;
      prefs_ = prefs;
      props_ = props;
      instanceLabel_ = instanceLabel;
      
      // check to see if we are on "neutral" side (NONE)
      // if so act like we are on side A but remember that so we can label accordingly
      boolean noSide = false;
      if (side == Devices.Sides.NONE) {
         noSide = true;
         side = Devices.Sides.A;
      }
      
      side_ = side;
      otherSide_ = Devices.getOppositeSide(side);
         
      // NB: "A" and "B" in names doesn't necessarily mean path A and path B anymore
      // beamABox_ and scanBBox associated with the side passed as a parameter
      beamABox_ = makeCheckBox("Beam",
            Properties.Values.NO, Properties.Values.YES,
            Devices.getSideSpecificKey(Devices.Keys.GALVOA, side_),
            Properties.Keys.BEAM_ENABLED, Prefs.Keys.SHEET_BEAM_ENABLED);
      sheetABox_ = makeCheckBox("Sheet", 
            Properties.Values.SAM_DISABLED, Properties.Values.SAM_ENABLED,
            Devices.getSideSpecificKey(Devices.Keys.GALVOA, side_),
            Properties.Keys.SA_MODE_X, Prefs.Keys.SHEET_SCAN_ENABLED);

      beamBBox_ = makeCheckBox("Beam",
            Properties.Values.NO, Properties.Values.YES,
            Devices.getSideSpecificKey(Devices.Keys.GALVOA, otherSide_),
            Properties.Keys.BEAM_ENABLED, Prefs.Keys.EPI_BEAM_ENABLED);
      sheetBBox_ = makeCheckBox("Sheet", 
            Properties.Values.SAM_DISABLED, Properties.Values.SAM_ENABLED,
            Devices.getSideSpecificKey(Devices.Keys.GALVOA, otherSide_),
            Properties.Keys.SA_MODE_X, Prefs.Keys.EPI_SCAN_ENABLED);
      
      if (noSide) {
         add(new JLabel("Path A:"));
         add(beamABox_);
         add(sheetABox_, "wrap");
         add(new JLabel("Path B:"));
         add(beamBBox_);
         add(sheetBBox_, "wrap");         
      } else {
         add(new JLabel("Sheet side:"));
         add(beamABox_);
         add(sheetABox_, "wrap");
         add(new JLabel("Epi side:"));
         add(beamBBox_);
         add(sheetBBox_, "wrap");   
      }
      
      // disable the sheetA/B boxes when beam is disabled and vice versa
      disableSheetA_ = new ItemListener() {
         // only called when the user selects/deselects from GUI or the value _changes_ programmatically
         public void itemStateChanged(ItemEvent e) {
            if (e.getStateChange() == ItemEvent.SELECTED) {
               sheetABox_.setEnabled(true);
               if (beamABox_.isSelected() && sheetABox_.isSelected()) { // restart sheet if appropriate
                  sheetABox_.setSelected(false);
                  sheetABox_.setSelected(true);
               }
            } else {
               sheetABox_.setEnabled(false);
            }

         }
      }; 
      beamABox_.addItemListener(disableSheetA_);
      
      disableSheetB_ = new ItemListener() {
         // only called when the user selects/deselects from GUI or the value _changes_ programmatically
         public void itemStateChanged(ItemEvent e) {
            if (e.getStateChange() == ItemEvent.SELECTED) {
               sheetBBox_.setEnabled(true);
               if (beamBBox_.isSelected() && sheetBBox_.isSelected()) { // restart sheet if appropriate
                  sheetBBox_.setSelected(false);
                  sheetBBox_.setSelected(true);
               }
            } else {
               sheetBBox_.setEnabled(false);
            }

         }
      }; 
      beamBBox_.addItemListener(disableSheetB_);
      
      
      updateOnTab_ = new JCheckBox("Change settings on tab activate");
      updateOnTab_.setSelected(prefs_.getBoolean(instanceLabel_, Prefs.Keys.ENABLE_BEAM_SETTINGS, true));
      add(updateOnTab_, "center, span 3");
      
      
      
   }//constructor
   
   
   /**
    * Constructs JCheckBox appropriately set up for beam/scan toggle
    * @param label the GUI label
    * @param offValue the value of the property when not checked
    * @param onValue the value of the property when checked
    * @param props
    * @param devs
    * @param devKey
    * @param propKey
    * @return constructed JCheckBox
    */
   public JCheckBox makeCheckBox(String label, Properties.Values offValue, Properties.Values onValue,
         Devices.Keys devKey, Properties.Keys propKey, Prefs.Keys prefKey) {
      
      /**
       * nested inner class 
       * @author Jon
       */
      class checkBoxListener implements ItemListener, UpdateFromPropertyListenerInterface, DevicesListenerInterface {
         JCheckBox jc_;
         Properties.Values offValue_;
         Properties.Values onValue_;
         Devices.Keys devKey_;
         Properties.Keys propKey_;
         
         public checkBoxListener(JCheckBox jc,  Properties.Values offValue, Properties.Values onValue, Devices.Keys devKey, 
               Properties.Keys propKey) {
            jc_ = jc;
            offValue_ = offValue;
            onValue_ = onValue;
            devKey_ = devKey;
            propKey_ = propKey;
         }
         
         // only called when the user selects/deselects from GUI or the value _changes_ programmatically
         public void itemStateChanged(ItemEvent e) {
            if (e.getStateChange() == ItemEvent.SELECTED) {
               props_.setPropValue(devKey_, propKey_, onValue_);
            } else {
               props_.setPropValue(devKey_, propKey_, offValue_);
            }
         }
         
         public void updateFromProperty() {
            jc_.setEnabled(devices_.getMMDevice(devKey_)!=null);
            jc_.setSelected(props_.getPropValueString(devKey_, propKey_, true).equals(onValue_.toString()));
         }
         
         public void devicesChangedAlert() {
            updateFromProperty();
         }
      }
      
      boolean startSelected = prefs_.getBoolean(instanceLabel_, prefKey, false);
      JCheckBox jc = new JCheckBox(label, startSelected);
      checkBoxListener l = new checkBoxListener(jc, offValue, onValue, devKey, propKey);
      jc.addItemListener(l);
      if (devices_.getMMDevice(devKey)==null) {  // if we don't have valid device
         jc.setSelected(false);
         jc.setEnabled(false);
      } else {  // if we have valid device then toggle to force setting to initial value
         jc.setSelected(!startSelected);  // triggers ItemListener
         jc.setSelected(startSelected);  // triggers ItemListener
      }
      devices_.addListener((DevicesListenerInterface) l);
      return jc;
   }
   
   /**
    * Internal function for updating the checkboxes as desired on activation
    * @param box
    * @param devKey
    * @param propKey
    * @param onValue
    */
   private void updateOnSelected(JCheckBox box, Devices.Keys devKey, Properties.Keys propKey, Properties.Values onValue) {
      if (!box.isEnabled()) {
         return;
      }
      boolean boxVal = box.isSelected();
      boolean propVal = props_.getPropValueString(devKey, propKey).equals(onValue.toString());
      if (updateOnTab_.isSelected()) { // update settings when tab got selected
         if (boxVal != propVal) {
            box.setSelected(!boxVal);
            box.setSelected(boxVal);
         }
      } else { // update GUI with present settings
         box.setSelected(!propVal);
         box.setSelected(propVal);
      }
   }
   
   /**
   * Should be called when enclosing panel gets focus (need to call in gotSelected() function of enclosing frame)
   */ 
   @Override
  public void gotSelected() {
      updateOnSelected(beamABox_, Devices.getSideSpecificKey(Devices.Keys.GALVOA, side_), 
               Properties.Keys.BEAM_ENABLED, Properties.Values.YES);
      updateOnSelected(sheetABox_, Devices.getSideSpecificKey(Devices.Keys.GALVOA, side_),
               Properties.Keys.SA_MODE_X, Properties.Values.SAM_ENABLED);
      updateOnSelected(beamBBox_, Devices.getSideSpecificKey(Devices.Keys.GALVOA, otherSide_),
               Properties.Keys.BEAM_ENABLED, Properties.Values.YES);
      updateOnSelected(sheetBBox_, Devices.getSideSpecificKey(Devices.Keys.GALVOA, otherSide_), 
               Properties.Keys.SA_MODE_X, Properties.Values.SAM_ENABLED);
  }
   
   @Override
   public void saveSettings() {
      prefs_.putBoolean(instanceLabel_, Prefs.Keys.SHEET_BEAM_ENABLED, beamABox_.isSelected());
      prefs_.putBoolean(instanceLabel_, Prefs.Keys.EPI_BEAM_ENABLED, beamBBox_.isSelected());
      prefs_.putBoolean(instanceLabel_, Prefs.Keys.SHEET_SCAN_ENABLED, sheetABox_.isSelected());
      prefs_.putBoolean(instanceLabel_, Prefs.Keys.EPI_SCAN_ENABLED, sheetBBox_.isSelected());
      prefs_.putBoolean(instanceLabel_, Prefs.Keys.ENABLE_BEAM_SETTINGS, updateOnTab_.isSelected());
   }
   
   
}
