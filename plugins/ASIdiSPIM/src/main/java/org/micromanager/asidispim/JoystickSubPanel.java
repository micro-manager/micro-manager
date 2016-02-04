///////////////////////////////////////////////////////////////////////////////
//FILE:          JoystickSubPanel.java
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

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;


import org.micromanager.asidispim.data.Devices;
import org.micromanager.asidispim.data.Joystick;
import org.micromanager.asidispim.data.MyStrings;
import org.micromanager.asidispim.data.Prefs;
import org.micromanager.asidispim.utils.DevicesListenerInterface;
import org.micromanager.asidispim.utils.ListeningJPanel;

import net.miginfocom.swing.MigLayout;
import org.micromanager.asidispim.data.Joystick.Directions;
import org.micromanager.asidispim.data.Joystick.JSAxisData;
import org.micromanager.asidispim.utils.MyDialogUtils;

// known issue: quirky way of handling case where user removes the device that a joystick is currently associated with
//   without the plugin knowing the device, the joystick can't actually be disabled so will continue to function with that physical device
//   until the plugin knows about the device and can thus set it to 

/**
 *
 * @author Jon
 */
@SuppressWarnings("serial")
public final class JoystickSubPanel extends ListeningJPanel {
   
   private final Joystick joystick_;
   private final Devices devices_;
   private final Devices.Sides side_;
   private final Prefs prefs_;
   private final String instanceLabel_;
   private final JComboBox joystickBox_;
   private final JComboBox rightWheelBox_;
   private final JComboBox leftWheelBox_;
     
   /**
    * 
    * @param joystick instance of Joystick object
    * @param devices the (single) instance of the Devices class
    * @param instanceLabel name of calling panel
    * @param side A, B, or none
    * @param prefs
    */
   public JoystickSubPanel(Joystick joystick, Devices devices, String instanceLabel,
         Devices.Sides side, Prefs prefs) {    
      super (MyStrings.PanelNames.JOYSTICK_SUBPANEL.toString() + instanceLabel,
            new MigLayout(
              "", 
              "[right]8[align center]",
              "[]16[]"));
      setBorder(BorderFactory.createLineBorder(ASIdiSPIM.borderColor));
      
      joystick_ = joystick;
      devices_ = devices;
      prefs_ = prefs;
      side_ = side;
      instanceLabel_ = instanceLabel;
      
      // TODO actually use side specifier in code below
      
      add(new JLabel(Joystick.Keys.JOYSTICK.toString() + ":"));
      joystickBox_ = makeJoystickSelectionBox(Joystick.Keys.JOYSTICK);
      add(joystickBox_, "wrap");
      
      add(new JLabel(Joystick.Keys.LEFT_WHEEL.toString() + ":"));
      leftWheelBox_ = makeJoystickSelectionBox(Joystick.Keys.LEFT_WHEEL);
      add(leftWheelBox_, "wrap");
      
      add(new JLabel(Joystick.Keys.RIGHT_WHEEL.toString() + ":"));
      rightWheelBox_ = makeJoystickSelectionBox(Joystick.Keys.RIGHT_WHEEL);
      add(rightWheelBox_);
   }
   
   private JComboBox makeJoystickSelectionBox(Joystick.Keys jkey) {
      JComboBox jcb = new JComboBox();
      StageSelectionBoxListener ssbl = new StageSelectionBoxListener(jkey , jcb);
      jcb.addActionListener(ssbl);
      jcb.addItemListener(ssbl);
      devices_.addListener(ssbl);
      // intentionally set from prefs after adding listeners so programmatic change from prefs will be handled like user change
      // we store string in prefs but have associative object as combo box items
      // could simplify the equals method of JSAxisData to only look at the display string but afraid of unintended consequences
      // so we look through combo box strings and select a match
      String selectedItem = prefs_.getString(instanceLabel_, jkey.toString(), Joystick.Keys.NONE.toString());
      for (int index = 0; index < jcb.getItemCount(); index++) {
         if (jcb.getItemAt(index).toString().equals(selectedItem)) {
            jcb.setSelectedIndex(index);
            break;
         }
      }
      return jcb;
   }
   
   /**
    * Listener for Selection boxes that attach joysticks to drives
    */
   private class StageSelectionBoxListener implements ItemListener,
   ActionListener, DevicesListenerInterface {
      Joystick.Keys jkey_;
      JComboBox cb_;
      boolean updatingList_;

      public StageSelectionBoxListener(Joystick.Keys jkey, JComboBox jc) {
         jkey_ = jkey;
         cb_ = jc;
         this.updateStageSelections();  // do initial rendering
      }    

      /**
       * This will be called whenever the user selects a new item, but also when
       * the tab to which this selectionbox belongs is selected (via gotSelected() method)
       * Save the selection to preferences every time (probably inefficient)
       *
       * @param ae - ActionEvent that generated this callback
       */
      @Override
      public void actionPerformed(ActionEvent ae) {
         if (updatingList_ == true) {
            return;  // don't go through this if we are rebuilding selections
         }
         Joystick.JSAxisData axisData = (JSAxisData) cb_.getSelectedItem();
         if (axisData == null) {
            MyDialogUtils.showError("Problem getting joystick value for joystick combobox.");
            return;
         }
         if (axisData.deviceKey != Devices.Keys.NONE) {
            joystick_.setJoystick(jkey_, axisData);
         }
         else {
            joystick_.unsetJoystick(jkey_, axisData);
         }
         prefs_.putString(instanceLabel_, jkey_.toString(), ((JSAxisData) cb_.getSelectedItem()).toString());
      }
      
      // have both actionlistener and itemlistener because need to do deselect operation
      @Override
      public void itemStateChanged(ItemEvent e) {
         if (updatingList_ == true) {
            return;  // don't go through this if we are rebuilding selections
         }
         if (e.getStateChange() == ItemEvent.DESELECTED) {  // clear the old association
            Joystick.JSAxisData axisData = (JSAxisData) e.getItem();  // gets deselected item
            joystick_.unsetJoystick(jkey_, axisData);
         }
      }
      
      /**
       * called whenever one of the devices is changed in the "Devices" tab
       */
      @Override
      public void devicesChangedAlert() {
         updateStageSelections();
      }
      
      /**
       * Resets the items in the combo box based on current contents of device tab.
       * Besides being called on original combo box creation, it is called whenever something in the devices tab is changed
       */
      private void updateStageSelections() {
         // save the existing selection if it exists
         // if we are making combobox for first time itemOrig will be null; we set
         // it later from the preference value in makeJoystickSelectionBox()
         JSAxisData itemOrig = (JSAxisData) cb_.getSelectedItem();
         if (itemOrig == null) {
            itemOrig = new JSAxisData(devices_.getDeviceDisplayVerbose(Devices.Keys.NONE), 
                    Devices.Keys.NONE, Directions.NONE);
         }
         joystick_.unsetAllDevicesFromJoystick(jkey_);
         
         // get the appropriate list of strings (in form of JSAxisData array) depending on joystick device type
         // already includes "None" at the top of each list
         Joystick.JSAxisData[] JSAxisDataItems;
         if (jkey_==Joystick.Keys.LEFT_WHEEL || jkey_==Joystick.Keys.RIGHT_WHEEL) {
            JSAxisDataItems = joystick_.getWheelJSAxisData(side_);
         }
         else if (jkey_==Joystick.Keys.JOYSTICK) {
            JSAxisDataItems = joystick_.getStickJSAxisData(side_);
         }
         else {
            MyDialogUtils.showError("Unknown joystick device type.");
            return;
         }
         
         // repopulate the combo box with items
         updatingList_ = true;  // make sure itemStateChanged isn't fired until we rebuild list
         boolean itemInNew = false;
         cb_.removeAllItems();
         for (Joystick.JSAxisData item : JSAxisDataItems) {
            cb_.addItem(item);
            if (item.equals(itemOrig)) {
               itemInNew = true;
            }
         }
         updatingList_ = false;
         
         // restore the original selection if it's still present
         if (itemInNew) {
            cb_.setSelectedItem(itemOrig);
         }
         else {
            cb_.setSelectedItem(new JSAxisData(devices_.getDeviceDisplayVerbose(Devices.Keys.NONE),
                  Devices.Keys.NONE, Directions.NONE));
         }
      }//updateStageSelections

   }//class StageSelectionBoxListener


   /**
    * Should be called when enclosing panel gets focus.
    */
   @Override
   public void gotSelected() {
      joystickBox_.setSelectedItem((JSAxisData) joystickBox_.getSelectedItem());
      rightWheelBox_.setSelectedItem((JSAxisData) rightWheelBox_.getSelectedItem());      
      leftWheelBox_.setSelectedItem((JSAxisData) leftWheelBox_.getSelectedItem());
   }

   /**
    * Should be called when enclosing panel loses focus.
    */
   @Override
   public void gotDeSelected() {
      joystick_.unsetJoystick(Joystick.Keys.JOYSTICK, (JSAxisData) joystickBox_.getSelectedItem());
      joystick_.unsetJoystick(Joystick.Keys.RIGHT_WHEEL, (JSAxisData) rightWheelBox_.getSelectedItem());
      joystick_.unsetJoystick(Joystick.Keys.LEFT_WHEEL, (JSAxisData) leftWheelBox_.getSelectedItem());
   }

}
