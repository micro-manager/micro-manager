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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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
   
   private final JCheckBox scanABox_;
   private final JCheckBox scanBBox_;
   private final JCheckBox beamABox_;
   private final JCheckBox beamBBox_;
   
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
      scanABox_ = makeCheckBox("Sheet", 
            Properties.Values.SAM_DISABLED, Properties.Values.SAM_ENABLED,
            Devices.getSideSpecificKey(Devices.Keys.GALVOA, side_),
            Properties.Keys.SA_MODE_X, Prefs.Keys.SHEET_SCAN_ENABLED);

      beamBBox_ = makeCheckBox("Beam",
            Properties.Values.NO, Properties.Values.YES,
            Devices.getSideSpecificKey(Devices.Keys.GALVOA, otherSide_),
            Properties.Keys.BEAM_ENABLED, Prefs.Keys.EPI_BEAM_ENABLED);
      scanBBox_ = makeCheckBox("Sheet", 
            Properties.Values.SAM_DISABLED, Properties.Values.SAM_ENABLED,
            Devices.getSideSpecificKey(Devices.Keys.GALVOA, otherSide_),
            Properties.Keys.SA_MODE_X, Prefs.Keys.EPI_SCAN_ENABLED);
      
      if (noSide) {
         add(new JLabel("Path A:"));
         add(beamABox_);
         add(scanABox_, "wrap");
         add(new JLabel("Path B:"));
         add(beamBBox_);
         add(scanBBox_, "wrap");         
      } else {
         add(new JLabel("Sheet side:"));
         add(beamABox_);
         add(scanABox_, "wrap");
         add(new JLabel("Epi side:"));
         add(beamBBox_);
         add(scanBBox_, "wrap");   
      }


      // disable the sheetA/B boxes when beam is disabled and vice versa
      ActionListener alA = new ActionListener() {
         public void actionPerformed(ActionEvent e) { 
            scanABox_.setEnabled(beamABox_.isSelected());
            if (beamABox_.isSelected() && scanABox_.isSelected()) {
               // restart sheet if it was enabled before
               scanABox_.doClick();
               scanABox_.doClick();
            }
         }
      }; 
      alA.actionPerformed(null);
      beamABox_.addActionListener(alA);
      
      ActionListener alB = new ActionListener() {
         public void actionPerformed(ActionEvent e) { 
            scanBBox_.setEnabled(beamBBox_.isSelected());
            if (beamBBox_.isSelected() && scanBBox_.isSelected()) {
               // restart sheet if it was enabled before
               scanBBox_.doClick();
               scanBBox_.doClick();
            }
         }
      };
      alB.actionPerformed(null);
      beamBBox_.addActionListener(alB);
      
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
               props_.setPropValue(devKey_, propKey_, onValue_, true);
            } else {
               props_.setPropValue(devKey_, propKey_, offValue_, true);
            }
         }
         
         public void updateFromProperty() {
            jc_.setSelected(props_.getPropValueString(devKey_, propKey_, true).equals(onValue_.toString()));
         }
         
         public void devicesChangedAlert() {
            updateFromProperty();
         }
      }
      
      boolean startSelected = prefs_.getBoolean(instanceLabel_, prefKey, false);
      JCheckBox jc = new JCheckBox(label, !startSelected);
      checkBoxListener l = new checkBoxListener(jc, offValue, onValue, devKey, propKey);
      jc.addItemListener(l);
      jc.setSelected(startSelected);  // triggers ItemListener because we initialized to inverse of this value
      
      devices_.addListener((DevicesListenerInterface) l);
      return jc;
   }
   
   /**
   * Should be called when enclosing panel gets focus.
   * a kludgey way of doing things!! I would like a "refresh" setting on the checkbox but couldn't figure out how to do it
   *   this isn't like combobox because itemStateChanged isn't always called on setSelected despite what I expect... instead
   *   it is only called when the value changes
   */ 
   @Override
  public void gotSelected() {
      boolean boxVal;
      boolean propVal;
      boxVal = beamABox_.isSelected();
      propVal = props_.getPropValueString(Devices.getSideSpecificKey(Devices.Keys.GALVOA, side_), 
            Properties.Keys.BEAM_ENABLED).equals(Properties.Values.YES.toString());
      if (boxVal != propVal) {
         beamABox_.setSelected(!boxVal);
         beamABox_.setSelected(boxVal);
      }
      boxVal = scanABox_.isSelected();
      propVal = props_.getPropValueString(Devices.getSideSpecificKey(Devices.Keys.GALVOA, side_),
            Properties.Keys.SA_MODE_X).equals(Properties.Values.SAM_ENABLED.toString());
      if (boxVal != propVal) {
         scanABox_.setSelected(!boxVal);
         scanABox_.setSelected(boxVal);
      }
      boxVal = beamBBox_.isSelected();
      propVal = props_.getPropValueString(Devices.getSideSpecificKey(Devices.Keys.GALVOA, otherSide_),
            Properties.Keys.BEAM_ENABLED).equals(Properties.Values.YES.toString());
      if (boxVal != propVal) {
         beamBBox_.setSelected(!boxVal);
         beamBBox_.setSelected(boxVal);
      }
      boxVal = scanBBox_.isSelected();
      propVal = props_.getPropValueString(Devices.getSideSpecificKey(Devices.Keys.GALVOA, otherSide_), 
            Properties.Keys.SA_MODE_X).equals(Properties.Values.SAM_ENABLED.toString());
      if (boxVal != propVal) {
         scanBBox_.setSelected(!boxVal);
         scanBBox_.setSelected(boxVal);
      }
  }
   
   @Override
   public void saveSettings() {
      prefs_.putBoolean(instanceLabel_, Prefs.Keys.SHEET_BEAM_ENABLED, beamABox_.isSelected());
      prefs_.putBoolean(instanceLabel_, Prefs.Keys.EPI_BEAM_ENABLED, beamBBox_.isSelected());
      prefs_.putBoolean(instanceLabel_, Prefs.Keys.SHEET_SCAN_ENABLED, scanABox_.isSelected());
      prefs_.putBoolean(instanceLabel_, Prefs.Keys.EPI_SCAN_ENABLED, scanBBox_.isSelected());
   }
   
   
}
