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

import java.awt.geom.Point2D;
import java.util.prefs.Preferences;
import javax.swing.JComboBox;
import org.micromanager.asidispim.Data.Properties;
import org.micromanager.asidispim.Data.Devices;
import org.micromanager.asidispim.Utils.ListeningJPanel;
import org.micromanager.asidispim.Utils.PanelUtils;
import javax.swing.JLabel;
import net.miginfocom.swing.MigLayout;
import org.micromanager.asidispim.Utils.Labels;

/**
 *
 * @author nico
 */
public class NavigationPanel extends ListeningJPanel {
   Devices devices_;
   Properties props_;
   Preferences prefs_;
   
   JComboBox joystickBox_;
   JComboBox rightWheelBox_;
   JComboBox leftWheelBox_;
   
   JLabel xPositionLabel_;
   JLabel yPositionLabel_;
   JLabel upperZPositionLabel_;
   JLabel lowerZPositionLabel_;
   
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
   public NavigationPanel(Properties props, Devices devices) {    
      super (new MigLayout(
              "", 
              "[right]8[align center]16[right]8[center]8[center]8[center]",
              "[]16[]"));
      devices_ = devices;
      props_ = props;
       
      PanelUtils pu = new PanelUtils();
      
      prefs_ = Preferences.userNodeForPackage(this.getClass());
       
      String jcs = "";
       if (devices_.getTwoAxisTigerStages() != null && 
               devices_.getTwoAxisTigerStages().length > 0) {
          jcs = devices_.getTwoAxisTigerStages()[0];
       }
      String joystickSelection = prefs_.get(JOYSTICK,jcs);
       String ws = "";
       if (devices_.getTigerStages() != null &&  
               devices_.getTigerStages().length > 0) {
          ws = devices_.getTigerStages()[0];
       }
      String rightWheelSelection = prefs_.get(RIGHTWHEEL, ws);
      String leftWheelSelection = prefs_.get(LEFTWHEEL,ws);
       
      add(new JLabel(JOYSTICK + ":"));
      joystickBox_ = pu.makeJoystickSelectionBox(Devices.JoystickDevice.JOYSTICK, 
               devices_.getTwoAxisTigerStages(), joystickSelection, devices_,
               prefs_, JOYSTICK);
      add(joystickBox_);
      add(new JLabel("X:"));
      xPositionLabel_ = new JLabel(getTwoAxisStagePosition(Devices.XYSTAGE, 
              Labels.Directions.X));
      add(xPositionLabel_, "wrap");
       
      add(new JLabel(RIGHTWHEEL + ":"));
      rightWheelBox_ = pu.makeJoystickSelectionBox(Devices.JoystickDevice.RIGHT_KNOB, 
               devices_.getTigerStages(), rightWheelSelection, devices_, prefs_,
               RIGHTWHEEL);
      add(rightWheelBox_);
      add(new JLabel("Y:"));
      yPositionLabel_ = new JLabel(getTwoAxisStagePosition(Devices.XYSTAGE, 
              Labels.Directions.Y));
      add(yPositionLabel_, "wrap");
       
      add(new JLabel(LEFTWHEEL + ":"));
      leftWheelBox_ = pu.makeJoystickSelectionBox(Devices.JoystickDevice.LEFT_KNOB,
               devices_.getTigerStages(), leftWheelSelection, devices_, prefs_,
               LEFTWHEEL);
      add(leftWheelBox_);
      add(new JLabel("Lower Z Stage:"));
      lowerZPositionLabel_ = new JLabel(getStagePosition(Devices.LOWERZDRIVE));
      add(lowerZPositionLabel_, "wrap");
      
      add(new JLabel("Upper Z Stage:"), "skip 2");
      upperZPositionLabel_ = new JLabel(getStagePosition(Devices.UPPERZDRIVE));
      add(upperZPositionLabel_, "wrap");
      
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
      devices_.clearJoystickBindings();
      joystickBox_.setSelectedItem(joystickBox_.getSelectedItem());
      rightWheelBox_.setSelectedItem(rightWheelBox_.getSelectedItem());      
      leftWheelBox_.setSelectedItem(leftWheelBox_.getSelectedItem());
   }
   
   private String getTwoAxisStagePosition(String stage, Labels.Directions dir) {
      Point2D.Double xyPos = devices_.getTwoAxisStagePosition(stage);
      if (xyPos != null) {
         if (dir == Labels.Directions.X) {
            return Devices.posToDisplayString(xyPos.x);
         } else {
            return Devices.posToDisplayString(xyPos.y);
         }
      }
      return "       ";
   }
   
   private String getStagePosition(String stage) {
      Double pos = devices_.getStagePosition(stage);
      if (pos != null) {
         return Devices.posToDisplayString(pos);
      }
      return "       ";
   }

   @Override
   public void updateStagePositions() {
      xPositionLabel_.setText(getTwoAxisStagePosition(Devices.XYSTAGE, 
              Labels.Directions.X));   
      yPositionLabel_.setText(getTwoAxisStagePosition(Devices.XYSTAGE, 
              Labels.Directions.Y));
      upperZPositionLabel_.setText(getStagePosition(Devices.UPPERZDRIVE));
      lowerZPositionLabel_.setText(getStagePosition(Devices.LOWERZDRIVE));

   }
   
}
