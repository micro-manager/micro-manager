///////////////////////////////////////////////////////////////////////////////
//FILE:          JoystickPanel.java
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
import java.util.HashMap;
import java.util.prefs.Preferences;

import javax.swing.JComboBox;
import javax.swing.JLabel;

import org.micromanager.asidispim.Data.Devices;
import org.micromanager.asidispim.Data.Joystick;
import org.micromanager.asidispim.Data.Joystick.JSAxisData;
import org.micromanager.asidispim.Utils.DevicesListenerInterface;
import org.micromanager.asidispim.Utils.ListeningJPanel;

import net.miginfocom.swing.MigLayout;

// known issue: quirky way of handling case where user removes the device that a joystick is currently associated with
//   without the plugin knowing the device, the joystick can't actually be disabled so will continue to function with that physical device
//   until the plugin knows about the device and can thus set it to 

/**
 *
 * @author Jon
 */
@SuppressWarnings("serial")
public class JoystickPanel extends ListeningJPanel {
   
   private Joystick joystick_;
   private Devices devices_;
   private Preferences prefs_;
   String instanceLabel_;
   JComboBox joystickBox_;
   JComboBox rightWheelBox_;
   JComboBox leftWheelBox_;
     
   /**
    * 
    * @param devices the (single) instance of the Devices class
    */
   public JoystickPanel(Joystick joystick, Devices devices, String instanceLabel) {    
      super (new MigLayout(
              "", 
              "[right]8[align center]",
              "[]16[]"));
     
      joystick_ = joystick;
      devices_ = devices;
      prefs_ = Preferences.userNodeForPackage(this.getClass());
      instanceLabel_ = instanceLabel;
      
      String labelstr = Joystick.Keys.JOYSTICK.toString();
      add(new JLabel(labelstr + ":"));
      joystickBox_ = makeJoystickSelectionBox(
            Joystick.Keys.JOYSTICK, devices_, prefs_, instanceLabel_+"_"+labelstr);
      add(joystickBox_, "wrap");
      
      labelstr = Joystick.Keys.LEFT_WHEEL.toString();
      add(new JLabel(labelstr + ":"));
      leftWheelBox_ = makeJoystickSelectionBox(
            Joystick.Keys.LEFT_WHEEL, devices_, prefs_, instanceLabel_+"_"+labelstr);
      add(leftWheelBox_, "wrap");
      
      labelstr = Joystick.Keys.RIGHT_WHEEL.toString();
      add(new JLabel(labelstr + ":"));
      rightWheelBox_ = makeJoystickSelectionBox(
            Joystick.Keys.RIGHT_WHEEL, devices_, prefs_, instanceLabel_+"_"+labelstr);
      add(rightWheelBox_, "wrap");
   }
   
   private JComboBox makeJoystickSelectionBox(Joystick.Keys jkey, 
         Devices devices_, Preferences prefs_, String prefsKey_) {
      JComboBox jcb = new JComboBox();
      StageSelectionBoxListener ssbl = new StageSelectionBoxListener(
            jkey , jcb, prefs_, prefsKey_);
      jcb.addActionListener(ssbl);
      jcb.addItemListener(ssbl);
      devices_.addListener(ssbl);
      // intentionally set from prefs after adding listeners so programmatic change from prefs will be handled like user change
      String selectedItem = prefs_.get(prefsKey_, Joystick.Keys.NONE.toString());
      jcb.setSelectedItem(selectedItem);

      return jcb;
   }
   
   /**
    * Listener for Selection boxes that attach joysticks to drives
    */
   private class StageSelectionBoxListener implements ItemListener,
   ActionListener, DevicesListenerInterface {
      Joystick.Keys jkey_;
      JComboBox jc_;
      Preferences prefs_;
      String prefsKey_;
      HashMap<String, JSAxisData> JSAxisDataHash_;
      boolean updatingList_;

      
      public StageSelectionBoxListener(Joystick.Keys jkey, 
            JComboBox jc, Preferences prefs, String prefsKey) {
         jkey_ = jkey;
         jc_ = jc;
         prefs_ = prefs;
         prefsKey_ = prefsKey;
         JSAxisDataHash_ = new HashMap<String, JSAxisData>();
         this.updateStageSelections();  // do initial rendering
      }    

      /**
       * This will be called whenever the user selects a new item, but also when
       * the tab to which this selectionbox belongs is selected (via gotSelected() method)
       * Save the selection to preferences every time (probably inefficient)
       *
       * @param ae
       */
      public void actionPerformed(ActionEvent ae) {
         if (updatingList_ == true) {
            return;  // don't go through this if we are rebuilding selections
         }
         JSAxisData axis = JSAxisDataHash_.get( (String) jc_.getSelectedItem());
         if (axis != null) {
            if (axis.deviceKey != Devices.Keys.NONE) {
               joystick_.setJoystick(jkey_, axis);
            }
            else {
               joystick_.unsetJoystick(jkey_, axis);
            }
            prefs_.put(prefsKey_, (String) jc_.getSelectedItem());
         }
      }
      
      public void itemStateChanged(ItemEvent e) {
         if (updatingList_ == true) {
            return;  // don't go through this if we are rebuilding selections
         }
         if (e.getStateChange() == ItemEvent.DESELECTED) {  // clear the old association
            JSAxisData axis = JSAxisDataHash_.get( (String) e.getItem());  // gets deselected item
            if (axis.deviceKey != Devices.Keys.NONE) {
               joystick_.unsetJoystick(jkey_, axis);
            }
         }
      }
      
      /**
       * called whenever one of the devices is changed in the "Devices" tab
       */
      public void devicesChangedAlert() {
         updateStageSelections();
      }
      
      /**
       * Resets the items in the combo box based on current contents of device tab.
       * Besides being called on original combo box creation, it is called whenever something in the devices tab is changed
       */
      private void updateStageSelections() {
         // save the existing selection if it exists
         String itemOrig = (String) jc_.getSelectedItem();
         joystick_.unsetJoystick(jkey_);
         
         // get the appropriate list of strings (in form of JSAxisData array) depending on joystick device type
         JSAxisData[] JSAxisDataItems = null;
         JSAxisDataHash_.clear();
         if (jkey_==Joystick.Keys.LEFT_WHEEL || jkey_==Joystick.Keys.RIGHT_WHEEL) {
            JSAxisDataItems = joystick_.getWheelJSAxisData();
         }
         else if (jkey_==Joystick.Keys.JOYSTICK) {
            JSAxisDataItems = joystick_.getStickJSAxisData();
         }

         // repopulate the combo box with items
         updatingList_ = true;  // make sure itemStateChanged isn't fired until we rebuild list
         boolean itemInNew = false;
         jc_.removeAllItems();
         for (JSAxisData a : JSAxisDataItems) {
            String s = a.displayString;
            jc_.addItem(s);
            JSAxisDataHash_.put(s, a);
            if (s.equals(itemOrig)) {
               itemInNew = true;
            }
         }
         
         // restore the original selection if it's still present
         if (itemInNew) {
            jc_.setSelectedItem(itemOrig);
         }
         else {
            jc_.setSelectedItem(Joystick.Keys.NONE.toString());
         }
         updatingList_ = false;
      }//updateStageSelections
      
   }//class StageSelectionBoxListener
   
   
   /**
   * Gets called when this panel gets focus.
   */
  @Override
  public void gotSelected() {
     joystick_.unsetAllJoysticks();
     joystickBox_.setSelectedItem(joystickBox_.getSelectedItem());
     rightWheelBox_.setSelectedItem(rightWheelBox_.getSelectedItem());      
     leftWheelBox_.setSelectedItem(leftWheelBox_.getSelectedItem());
  }
   
}
