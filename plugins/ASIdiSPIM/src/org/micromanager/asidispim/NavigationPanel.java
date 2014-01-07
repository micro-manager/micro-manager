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

import org.micromanager.MMStudioMainFrame;
import org.micromanager.asidispim.Data.Devices;
import org.micromanager.asidispim.Data.Joystick;
import org.micromanager.asidispim.Data.Positions;
import org.micromanager.asidispim.Utils.ListeningJPanel;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JCheckBox;

import mmcorej.CMMCore;
import net.miginfocom.swing.MigLayout;

import org.micromanager.asidispim.Utils.StagePositionUpdater;
import org.micromanager.utils.ReportingUtils;


/**
 *
 * @author nico
 * @author Jon
 */
@SuppressWarnings("serial")
public class NavigationPanel extends ListeningJPanel {
   private Devices devices_;
   private Joystick joystick_;
   private Positions positions_;
   private Preferences prefs_;
   private CMMCore core_;
   
   private JPanel joystickPanel_;
   
   private JLabel xPositionLabel_;
   private JLabel yPositionLabel_;
   private JLabel lowerZPositionLabel_;
   private JLabel upperZPositionLabel_;
   private JLabel piezoAPositionLabel_;
   private JLabel piezoBPositionLabel_;
   private JLabel galvoAxPositionLabel_;
   private JLabel galvoAyPositionLabel_;
   private JLabel galvoBxPositionLabel_;
   private JLabel galvoByPositionLabel_;
   
   private static final String PREF_ENABLEUPDATES = "EnablePositionUpdates";
   
   /**
    * Navigation panel constructor.
    */
   public NavigationPanel(Devices devices, Joystick joystick, Positions positions,
           final StagePositionUpdater stagePosUpdater) {    
      super (new MigLayout(
              "", 
              "[right]8[align center]16[right]8[center]8[center]8[center]",
              "[]12[]"));
      devices_ = devices;
      joystick_ = joystick;
      positions_ = positions;
      prefs_ = Preferences.userNodeForPackage(this.getClass());
      core_ = MMStudioMainFrame.getInstance().getCore();
      
      joystickPanel_ = new JoystickPanel(joystick_, devices_, "Navigation");
      add(joystickPanel_, "span 2 4");  // make artificially tall to keep stage positions in line with each other
      
      add(new JLabel(devices_.getDeviceDisplayWithAxis1D(Devices.Keys.XYSTAGE, Joystick.Directions.X) + ":"));
      xPositionLabel_ = new JLabel("");
      add(xPositionLabel_);
      add(makeHomeButton(Devices.Keys.XYSTAGE, Joystick.Directions.X));
      add(makeZeroButton(Devices.Keys.XYSTAGE, Joystick.Directions.X), "wrap");
      
      add(new JLabel(devices_.getDeviceDisplayWithAxis1D(Devices.Keys.XYSTAGE, Joystick.Directions.Y) + ":"));
      yPositionLabel_ = new JLabel("");
      add(yPositionLabel_);
      add(makeHomeButton(Devices.Keys.XYSTAGE, Joystick.Directions.Y));
      add(makeZeroButton(Devices.Keys.XYSTAGE, Joystick.Directions.Y), "wrap");
      
      add(new JLabel(devices_.getDeviceDisplayWithAxis(Devices.Keys.LOWERZDRIVE) + ":"));
      lowerZPositionLabel_ = new JLabel("");
      add(lowerZPositionLabel_);
      add(makeHomeButton(Devices.Keys.LOWERZDRIVE, Joystick.Directions.NONE));
      add(makeZeroButton(Devices.Keys.LOWERZDRIVE, Joystick.Directions.NONE), "wrap");
      
      add(new JLabel(devices_.getDeviceDisplayWithAxis(Devices.Keys.UPPERZDRIVE) + ":"));
      upperZPositionLabel_ = new JLabel("");
      add(upperZPositionLabel_);
      add(makeHomeButton(Devices.Keys.UPPERZDRIVE, Joystick.Directions.NONE));
      add(makeZeroButton(Devices.Keys.UPPERZDRIVE, Joystick.Directions.NONE), "wrap");
      
      final JCheckBox activeTimerCheckBox = new JCheckBox("Enable position update");
      ActionListener ae = new ActionListener() {
         public void actionPerformed(ActionEvent e) { 
            if (activeTimerCheckBox.isSelected()) {
               stagePosUpdater.start(1000);
            } else {
              stagePosUpdater.stop();
            }
            prefs_.putBoolean(PREF_ENABLEUPDATES, activeTimerCheckBox.isSelected());
         }
      }; 
      activeTimerCheckBox.addActionListener(ae);
      activeTimerCheckBox.setSelected(prefs_.getBoolean(PREF_ENABLEUPDATES, true));
      ae.actionPerformed(null);
      add(activeTimerCheckBox, "align left, span 2");
      
      add(new JLabel(devices_.getDeviceDisplayWithAxis(Devices.Keys.PIEZOA) + ":"));
      piezoAPositionLabel_ = new JLabel("");
      add(piezoAPositionLabel_);
      add(makeHomeButton(Devices.Keys.PIEZOA, Joystick.Directions.NONE));
      add(makeZeroButton(Devices.Keys.PIEZOA, Joystick.Directions.NONE), "wrap");
      
      add(new JLabel(devices_.getDeviceDisplayWithAxis(Devices.Keys.PIEZOB) + ":"), "span 3");
      piezoBPositionLabel_ = new JLabel("");
      add(piezoBPositionLabel_);
      add(makeHomeButton(Devices.Keys.PIEZOB, Joystick.Directions.NONE));
      add(makeZeroButton(Devices.Keys.PIEZOB, Joystick.Directions.NONE), "wrap");
      
      add(new JLabel(devices_.getDeviceDisplayWithAxis1D(Devices.Keys.GALVOA, Joystick.Directions.X) + " (in sheet plane):"), "span 3");
      galvoAxPositionLabel_ = new JLabel("");
      add(galvoAxPositionLabel_, "wrap");
      
      add(new JLabel(devices_.getDeviceDisplayWithAxis1D(Devices.Keys.GALVOA, Joystick.Directions.Y) + " (slice position):"), "span 3");
      galvoAyPositionLabel_ = new JLabel("");
      add(galvoAyPositionLabel_, "wrap");
      
      add(new JLabel(devices_.getDeviceDisplayWithAxis1D(Devices.Keys.GALVOB, Joystick.Directions.X) + " (in sheet plane):"), "span 3");
      galvoBxPositionLabel_ = new JLabel("");
      add(galvoBxPositionLabel_, "wrap");
      
      add(new JLabel(devices_.getDeviceDisplayWithAxis1D(Devices.Keys.GALVOB, Joystick.Directions.Y) + " (slice position):"), "span 3");
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

   private JButton makeHomeButton(Devices.Keys key, Joystick.Directions dir) {
      class homeButtonActionListener implements ActionListener {
         private Devices.Keys key_;
         private Joystick.Directions dir_;
         
         public void actionPerformed(ActionEvent e) {
            try {
               switch (dir_) {
               case X:
                  double ypos = core_.getYPosition(devices_.getMMDeviceException(Devices.Keys.XYSTAGE));
                  core_.setXYPosition(devices_.getMMDeviceException(Devices.Keys.XYSTAGE), 0.0, ypos);
                  break;
               case Y:
                  double xpos = core_.getXPosition(devices_.getMMDeviceException(key_));
                  core_.setXYPosition(devices_.getMMDeviceException(key_), xpos, 0.0);
                  break;
               case NONE:
               default:
                  core_.setPosition(devices_.getMMDeviceException(key_), 0.0);
                  break;
               }
            } catch (Exception ex) {
               ReportingUtils.showError(ex);
            }
         }

         private homeButtonActionListener(Devices.Keys key, Joystick.Directions dir) {
            key_ = key;
            dir_ = dir;
         }
      }
      
      JButton jb = new JButton("Home");
      ActionListener l = new homeButtonActionListener(key, dir);
      jb.addActionListener(l);
      return jb;
   }
   
   private JButton makeZeroButton(Devices.Keys key, Joystick.Directions dir) {
      class zeroButtonActionListener implements ActionListener {
         private Devices.Keys key_;
         private Joystick.Directions dir_;
         
         public void actionPerformed(ActionEvent e) {
            try {
               switch (dir_) {
               case X:
                  double ypos = core_.getYPosition(devices_.getMMDeviceException(Devices.Keys.XYSTAGE));
                  core_.setAdapterOriginXY(devices_.getMMDeviceException(Devices.Keys.XYSTAGE), 0.0, ypos);
                  break;
               case Y:
                  double xpos = core_.getXPosition(devices_.getMMDeviceException(key_));
                  core_.setAdapterOriginXY(devices_.getMMDeviceException(key_), xpos, 0.0);
                  break;
               case NONE:
               default:
                  core_.setOrigin(devices_.getMMDeviceException(key_));
                  break;
               }
            } catch (Exception ex) {
               ReportingUtils.showError(ex);
            }
         }

         private zeroButtonActionListener(Devices.Keys key, Joystick.Directions dir) {
            key_ = key;
            dir_ = dir;
         }
      }
      
      JButton jb = new JButton("Zero");
      ActionListener l = new zeroButtonActionListener(key, dir);
      jb.addActionListener(l);
      return jb;
   }
   
}
