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


import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.prefs.Preferences;

import org.micromanager.asidispim.Data.Devices;
import org.micromanager.asidispim.Data.Joystick;
import org.micromanager.asidispim.Data.Positions;
import org.micromanager.asidispim.Utils.ListeningJPanel;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JCheckBox;

import net.miginfocom.swing.MigLayout;


/**
 *
 * @author nico
 * @author Jon
 */
@SuppressWarnings("serial")
public class NavigationPanel extends ListeningJPanel {
   Devices devices_;
   Joystick joystick_;
   Positions positions_;
   Preferences prefs_;
   
   JPanel joystickPanel_;
   
   JLabel xPositionLabel_;
   JLabel yPositionLabel_;
   JLabel lowerZPositionLabel_;
   JLabel upperZPositionLabel_;
   JLabel piezoAPositionLabel_;
   JLabel piezoBPositionLabel_;
   JLabel galvoAxPositionLabel_;
   JLabel galvoAyPositionLabel_;
   JLabel galvoBxPositionLabel_;
   JLabel galvoByPositionLabel_;
   
   private static final String PREF_ENABLEUPDATES = "EnablePositionUpdates";
   
   /**
    * Navigation panel constructor.
    */
   public NavigationPanel(Devices devices, Joystick joystick, Positions positions,
           final ASIdiSPIMFrame parentFrame) {    
      super (new MigLayout(
              "", 
              "[right]8[align center]16[right]8[center]8[center]8[center]",
              "[]12[]"));
      devices_ = devices;
      joystick_ = joystick;
      positions_ = positions;
      prefs_ = Preferences.userNodeForPackage(this.getClass());
      
      joystickPanel_ = new JoystickPanel(joystick_, devices_, "Navigation");
      add(joystickPanel_, "span 2 4");  // make artificially tall to keep stage positions in line with each other
      
      add(new JLabel(devices_.getDeviceDisplay(Devices.Keys.XYSTAGE) + " " + Joystick.Directions.X.toString() + ":"));
      xPositionLabel_ = new JLabel("");
      add(xPositionLabel_, "wrap");
      
      add(new JLabel(devices_.getDeviceDisplay(Devices.Keys.XYSTAGE) + " " + Joystick.Directions.Y.toString() + ":"));
      yPositionLabel_ = new JLabel("");
      add(yPositionLabel_, "wrap");
      
      add(new JLabel(devices_.getDeviceDisplay(Devices.Keys.LOWERZDRIVE) + ":"));
      lowerZPositionLabel_ = new JLabel("");
      add(lowerZPositionLabel_, "wrap");
      
      add(new JLabel(devices_.getDeviceDisplay(Devices.Keys.UPPERZDRIVE) + ":"));
      upperZPositionLabel_ = new JLabel("");
      add(upperZPositionLabel_, "wrap");
      
      final JCheckBox activeTimerCheckBox = new JCheckBox("Enable position update");
      activeTimerCheckBox.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) { 
            if (activeTimerCheckBox.isSelected()) {
               parentFrame.startStagePosTimer();
            } else {
              parentFrame.stopStagePosTimer();
            }
            prefs_.putBoolean(PREF_ENABLEUPDATES, activeTimerCheckBox.isSelected());
         }
      } 
      );
      activeTimerCheckBox.setSelected(prefs_.getBoolean(PREF_ENABLEUPDATES, true));
      add(activeTimerCheckBox, "align left, span 2");
      
      add(new JLabel(devices_.getDeviceDisplay(Devices.Keys.PIEZOA) + ":"));
      piezoAPositionLabel_ = new JLabel("");
      add(piezoAPositionLabel_, "wrap");
      
      add(new JLabel(devices_.getDeviceDisplay(Devices.Keys.PIEZOB) + ":"), "span 3");
      piezoBPositionLabel_ = new JLabel("");
      add(piezoBPositionLabel_, "wrap");
      
      add(new JLabel(devices_.getDeviceDisplay(Devices.Keys.GALVOA) + " " + Joystick.Directions.X.toString() + " (in sheet plane):"), "span 3");
      galvoAxPositionLabel_ = new JLabel("");
      add(galvoAxPositionLabel_, "wrap");
      
      add(new JLabel(devices_.getDeviceDisplay(Devices.Keys.GALVOA) + " " + Joystick.Directions.Y.toString() + " (slice position):"), "span 3");
      galvoAyPositionLabel_ = new JLabel("");
      add(galvoAyPositionLabel_, "wrap");
      
      add(new JLabel(devices_.getDeviceDisplay(Devices.Keys.GALVOB) + " " + Joystick.Directions.X.toString() + " (in sheet plane):"), "span 3");
      galvoBxPositionLabel_ = new JLabel("");
      add(galvoBxPositionLabel_, "wrap");
      
      add(new JLabel(devices_.getDeviceDisplay(Devices.Keys.GALVOB) + " " + Joystick.Directions.Y.toString() + " (slice position):"), "span 3");
      galvoByPositionLabel_ = new JLabel("");
      add(galvoByPositionLabel_, "wrap");
      
   }
   

   
   /**
    * Gets called when this tab gets focus.  Sets the physical UI in the Tiger
    * controller to what was selected in this pane
    */
   @Override
   public void gotSelected() {
      ((ListeningJPanel) joystickPanel_).gotSelected();
   }
   
   @Override
   public void updateStagePositions() {
      xPositionLabel_.setText(positions_.getPositionString(Devices.Keys.XYSTAGE, Joystick.Directions.X));   
      yPositionLabel_.setText(positions_.getPositionString(Devices.Keys.XYSTAGE, Joystick.Directions.Y));
      lowerZPositionLabel_.setText(positions_.getPositionString(Devices.Keys.LOWERZDRIVE));
      upperZPositionLabel_.setText(positions_.getPositionString(Devices.Keys.UPPERZDRIVE));
      piezoAPositionLabel_.setText(positions_.getPositionString(Devices.Keys.PIEZOA));
      piezoBPositionLabel_.setText(positions_.getPositionString(Devices.Keys.PIEZOB));
      galvoAxPositionLabel_.setText(positions_.getPositionString(Devices.Keys.GALVOA, Joystick.Directions.X));
      galvoAyPositionLabel_.setText(positions_.getPositionString(Devices.Keys.GALVOA, Joystick.Directions.Y));
      galvoBxPositionLabel_.setText(positions_.getPositionString(Devices.Keys.GALVOB, Joystick.Directions.X));
      galvoByPositionLabel_.setText(positions_.getPositionString(Devices.Keys.GALVOB, Joystick.Directions.Y)); 
   }
      


   
}
