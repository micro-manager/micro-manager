///////////////////////////////////////////////////////////////////////////////
//FILE:          SetupPanel.java
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
import org.micromanager.asidispim.Data.SpimParams;
import org.micromanager.asidispim.Data.Devices;
import org.micromanager.asidispim.Utils.ListeningJPanel;
import org.micromanager.asidispim.Utils.Labels;
import org.micromanager.asidispim.Utils.PanelUtils;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JRadioButton;
import net.miginfocom.swing.MigLayout;
import org.micromanager.api.ScriptInterface;

/**
 *
 * @author Nico
 */
public class SetupPanel extends ListeningJPanel{
   ScriptInterface gui_;
   Devices devices_;
   SpimParams spimParams_;
   Labels.Sides side_;
   Preferences prefs_;
   
   JComboBox joystickBox_;
   JComboBox rightWheelBox_;
   JComboBox leftWheelBox_;
   
   JLabel imagingPiezoPositionLabel_;
   JLabel illuminationPiezoPositionLabel_;
   
   String imagingPiezo_;
   String illuminationPiezo_;
   
   final String JOYSTICK = Devices.JOYSTICKS.get(Devices.JoystickDevice.JOYSTICK);
   final String RIGHTWHEEL = Devices.JOYSTICKS.get(Devices.JoystickDevice.RIGHT_KNOB);
   final String LEFTWHEEL = Devices.JOYSTICKS.get(Devices.JoystickDevice.LEFT_KNOB);
    
   
   
   public SetupPanel(ScriptInterface gui, Devices devices, 
           SpimParams spimParams, Labels.Sides side) {
      super (new MigLayout(
              "", 
              "[right]8[align center]16[right]8[center]8[center]8[center]",
              "[]16[]"));
       devices_ = devices;
       gui_ = gui;
       side_ = side;
       prefs_ = Preferences.userNodeForPackage(this.getClass());
       
       String joystickPrefName = JOYSTICK + side_.toString();
       String rightWheelPrefName = RIGHTWHEEL + side_.toString();
       String leftWheelPrefName = LEFTWHEEL + side_.toString();
       String jcs = "";
       if (devices_.getTwoAxisTigerStages() != null && 
               devices_.getTwoAxisTigerStages().length > 0) {
          jcs = devices_.getTwoAxisTigerStages()[0];
       }
       String joystickSelection = prefs_.get(joystickPrefName, jcs);
       String ws = "";
       if (devices_.getTigerStages() != null &&  
               devices_.getTigerStages().length > 0) {
          ws = devices_.getTigerStages()[0];
       }
       String rightWheelSelection = prefs_.get(rightWheelPrefName, ws);
       String leftWheelSelection = prefs_.get(leftWheelPrefName, ws);
       
       imagingPiezo_ = Devices.PIEZOA;
       illuminationPiezo_ = Devices.PIEZOB;
       if (side_ == Labels.Sides.B) {
          imagingPiezo_ = Devices.PIEZOB;
          illuminationPiezo_ = Devices.PIEZOA;
       }
       
       PanelUtils pu = new PanelUtils();
       
       add(new JLabel(JOYSTICK + ":"));
       joystickBox_ = pu.makeJoystickSelectionBox(Devices.JoystickDevice.JOYSTICK, 
               devices_.getTwoAxisTigerStages(), joystickSelection, devices_,
               prefs_, joystickPrefName);
       add(joystickBox_);
       add(new JLabel("Imaging piezo:"));
       imagingPiezoPositionLabel_ = new JLabel(Devices.posToDisplayString(
               devices_.getStagePosition(imagingPiezo_)));
       add(imagingPiezoPositionLabel_);
       add(new JButton("Set start"));
       add(new JButton("Set end"), "wrap");
       
       add(new JLabel(RIGHTWHEEL + ":"));
       rightWheelBox_ = pu.makeJoystickSelectionBox(Devices.JoystickDevice.RIGHT_KNOB, 
               devices_.getTigerStages(), rightWheelSelection, devices_, prefs_,
               rightWheelPrefName);
       add(rightWheelBox_);
       add(new JLabel("Illumination Piezo:"));
       illuminationPiezoPositionLabel_ = new JLabel(Devices.posToDisplayString(
               devices_.getStagePosition(illuminationPiezo_)));
       add(illuminationPiezoPositionLabel_);
       add(new JButton("Set position"), "span 2, center, wrap");
       
       add(new JLabel(LEFTWHEEL + ":"));
       leftWheelBox_ = pu.makeJoystickSelectionBox(Devices.JoystickDevice.LEFT_KNOB,
               devices_.getTigerStages(), leftWheelSelection, devices_, prefs_,
               leftWheelPrefName);
       add(leftWheelBox_);
       add(new JLabel("Scan amplitude:"));
       add(new JLabel("Pos"));
       add(pu.makeSlider("scanAmplitude", 0, 8, 4), "span 2, center, wrap");
 
       add(new JLabel("Scan enabled:"));
       add(pu.makeCheckBox("name", Labels.Sides.A), "split 2");
       add(pu.makeCheckBox("name", Labels.Sides.B));
       add(new JLabel("Scan offset:"));
       add(new JLabel("pos"));
       add(pu.makeSlider("scanOffset", -4, 4, 0), "span 2, center, wrap");
       
       add(new JButton("Toggle scan"), "skip 1");
       add(new JLabel("Sheet position:"));
       add(new JLabel("pos"));
       add(new JButton("Set start"));
       add(new JButton("Set end"), "wrap");
       
       add(new JButton("Live"), "span, split 3, center");
       JRadioButton dualButton = new JRadioButton("Dual Camera");
       JRadioButton singleButton = new JRadioButton("Single Camera");
       ButtonGroup singleDualGroup = new ButtonGroup();
       singleDualGroup.add(dualButton);
       singleDualGroup.add(singleButton);
       add(singleButton, "center");
       add(dualButton, "center");  
       
   
       
   }
   
   @Override
   public void saveSettings() {
      prefs_.put(JOYSTICK + side_.toString(), 
              (String) joystickBox_.getSelectedItem());
      prefs_.put(RIGHTWHEEL + side_.toString(), 
              (String) rightWheelBox_.getSelectedItem());
      prefs_.put(LEFTWHEEL + side_.toString(), 
              (String) leftWheelBox_.getSelectedItem());
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
   
   @Override
   public void updateStagePositions() {
      imagingPiezoPositionLabel_.setText(Devices.posToDisplayString(
               devices_.getStagePosition(imagingPiezo_)));
      illuminationPiezoPositionLabel_.setText(Devices.posToDisplayString(
              devices_.getStagePosition(illuminationPiezo_)));
   }

}
