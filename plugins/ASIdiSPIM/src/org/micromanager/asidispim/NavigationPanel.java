///////////////////////////////////////////////////////////////////////////////
//FILE:          NavigationPanel.java
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

import java.util.prefs.Preferences;
import javax.swing.JComboBox;
import org.micromanager.asidispim.Data.Devices;
import org.micromanager.asidispim.Utils.ListeningJPanel;
import org.micromanager.asidispim.Utils.PanelUtils;
import javax.swing.JLabel;
import net.miginfocom.swing.MigLayout;

/**
 *
 * @author nico
 */
public class NavigationPanel extends ListeningJPanel {
   Devices devices_;
   Preferences prefs_;
   
   JComboBox joystickBox_;
   JComboBox rightWheelBox_;
   JComboBox leftWheelBox_;
   
   final String JOYSTICK = Devices.JOYSTICKS.get(Devices.JoystickDevice.JOYSTICK);
   final String RIGHTWHEEL = Devices.JOYSTICKS.get(Devices.JoystickDevice.RIGHT_KNOB);
   final String LEFTWHEEL = Devices.JOYSTICKS.get(Devices.JoystickDevice.LEFT_KNOB);
    
   /**
    * Panel displaying just the attachment of joystick components to actual 
    * devices. 
    * Some of this code is shared with SetupPanel.java.  
    * This could/should be factored out
    * @param devices the (single) instance of the Devices class
    */
   public NavigationPanel(Devices devices) {    
      super (new MigLayout(
              "", 
              "[right]8[align center]16[right]8[center]8[center]8[center]",
              "[]16[]"));
      devices_ = devices;
       
      PanelUtils pu = new PanelUtils();
      
      prefs_ = Preferences.userNodeForPackage(this.getClass());
       
      
      String joystickSelection = prefs_.get(JOYSTICK, 
              devices_.getTwoAxisTigerDrives()[0]);
      String rightWheelSelection = prefs_.get(RIGHTWHEEL, 
              devices_.getTigerDrives()[0]);
      String leftWheelSelection = prefs_.get(LEFTWHEEL, 
              devices_.getTigerDrives()[0]);
       
      add(new JLabel(JOYSTICK + ":"));
      joystickBox_ = pu.makeJoystickSelectionBox(Devices.JoystickDevice.JOYSTICK, 
               devices_.getTwoAxisTigerDrives(), joystickSelection, devices_,
               prefs_, JOYSTICK);
      add(joystickBox_, "wrap");
       
      add(new JLabel(RIGHTWHEEL + ":"));
      rightWheelBox_ = pu.makeJoystickSelectionBox(Devices.JoystickDevice.RIGHT_KNOB, 
               devices_.getTigerDrives(), rightWheelSelection, devices_, prefs_,
               RIGHTWHEEL);
      add(rightWheelBox_, "wrap");
       
      add(new JLabel(LEFTWHEEL + ":"));
      leftWheelBox_ = pu.makeJoystickSelectionBox(Devices.JoystickDevice.LEFT_KNOB,
               devices_.getTigerDrives(), leftWheelSelection, devices_, prefs_,
               LEFTWHEEL);
      add(leftWheelBox_, "wrap");
      
   }
   
   @Override
   public void saveSettings() {
      prefs_.put(JOYSTICK, (String) joystickBox_.getSelectedItem());
      prefs_.put(RIGHTWHEEL, (String) rightWheelBox_.getSelectedItem());
      prefs_.put(LEFTWHEEL, (String) leftWheelBox_.getSelectedItem());
   }
   
    /**
    * Gets called when this tab gets focus.  Sets the physical UI in the Tiger
    * controller to what was selected in this pane
    */
   @Override
   public void gotSelected() {
      joystickBox_.setSelectedItem(joystickBox_.getSelectedItem());
      rightWheelBox_.setSelectedItem(joystickBox_.getSelectedItem());      
      leftWheelBox_.setSelectedItem(joystickBox_.getSelectedItem());
   }
   
}
