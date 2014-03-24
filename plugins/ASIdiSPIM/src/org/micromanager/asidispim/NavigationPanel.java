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
import javax.swing.BorderFactory;

import org.micromanager.MMStudioMainFrame;
import org.micromanager.asidispim.Data.Cameras;
import org.micromanager.asidispim.Data.Devices;
import org.micromanager.asidispim.Data.Joystick;
import org.micromanager.asidispim.Data.Positions;
import org.micromanager.asidispim.Data.Prefs;
import org.micromanager.asidispim.Data.Properties;
import org.micromanager.asidispim.Utils.ListeningJPanel;
import org.micromanager.asidispim.Utils.PanelUtils;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JCheckBox;

import mmcorej.CMMCore;
import net.miginfocom.swing.MigLayout;

import org.micromanager.asidispim.Utils.StagePositionUpdater;
import org.micromanager.internalinterfaces.LiveModeListener;
import org.micromanager.utils.ReportingUtils;


/**
 *
 * @author nico
 * @author Jon
 */
@SuppressWarnings("serial")
public class NavigationPanel extends ListeningJPanel implements LiveModeListener {
   private final Devices devices_;
   private final Properties props_;
   private final Joystick joystick_;
   private final Positions positions_;
   private final Prefs prefs_;
   private final Cameras cameras_;
   private final CMMCore core_;
   //private final String panelName_;
   
   private final JoystickSubPanel joystickPanel_;
   private final CameraSubPanel cameraPanel_;
   private final BeamSubPanel beamPanel_;
   
   public final StagePositionUpdater stagePosUpdater_;
   
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
   
   /**
    * Navigation panel constructor.
    */
   public NavigationPanel(Devices devices, Properties props, Joystick joystick, Positions positions,
           StagePositionUpdater stagePosUpdater, Prefs prefs, Cameras cameras) {    
      super ("Navigation",
            new MigLayout(
              "", 
              "[right]8[align center]16[right]8[60px,center]8[center]8[center]8[center]8[center]8[center]",
              "[]6[]"));
      devices_ = devices;
      props_ = props;
      joystick_ = joystick;
      positions_ = positions;
      stagePosUpdater_ = stagePosUpdater;
      prefs_ = prefs;
      cameras_ = cameras;
      //panelName_ = super.panelName_;
      core_ = MMStudioMainFrame.getInstance().getCore();
      PanelUtils pu = new PanelUtils();
      
      joystickPanel_ = new JoystickSubPanel(joystick_, devices_, panelName_, Devices.Sides.NONE, prefs_);
      add(joystickPanel_, "span 2 4");  // make artificially tall to keep stage positions in line with each other
      
      add(new JLabel(devices_.getDeviceDisplayVerbose(Devices.Keys.XYSTAGE, Joystick.Directions.X) + ":"));
      xPositionLabel_ = new JLabel("");
      add(xPositionLabel_);
      add(pu.makeSetPositionField(Devices.Keys.XYSTAGE, Joystick.Directions.X, positions_));
      add(makeIncrementButton(Devices.Keys.XYSTAGE, Joystick.Directions.X, -10, "-"), "split 2");
      add(makeIncrementButton(Devices.Keys.XYSTAGE, Joystick.Directions.X, 10, "+"));
      add(makeMoveToOriginButton(Devices.Keys.XYSTAGE, Joystick.Directions.X));
      add(makeSetOriginHereButton(Devices.Keys.XYSTAGE, Joystick.Directions.X), "wrap");
      
      add(new JLabel(devices_.getDeviceDisplayVerbose(Devices.Keys.XYSTAGE, Joystick.Directions.Y) + ":"));
      yPositionLabel_ = new JLabel("");
      add(yPositionLabel_);
      add(pu.makeSetPositionField(Devices.Keys.XYSTAGE, Joystick.Directions.Y, positions_));
      add(makeIncrementButton(Devices.Keys.XYSTAGE, Joystick.Directions.Y, -10, "-"), "split 2");
      add(makeIncrementButton(Devices.Keys.XYSTAGE, Joystick.Directions.Y, 10, "+"));
      add(makeMoveToOriginButton(Devices.Keys.XYSTAGE, Joystick.Directions.Y));
      add(makeSetOriginHereButton(Devices.Keys.XYSTAGE, Joystick.Directions.Y), "wrap");
      
      add(new JLabel(devices_.getDeviceDisplayVerbose(Devices.Keys.LOWERZDRIVE) + ":"));
      lowerZPositionLabel_ = new JLabel("");
      add(lowerZPositionLabel_);
      add(pu.makeSetPositionField(Devices.Keys.LOWERZDRIVE, Joystick.Directions.NONE, positions_));
      add(makeIncrementButton(Devices.Keys.LOWERZDRIVE, Joystick.Directions.NONE, -10, "-"), "split 2");
      add(makeIncrementButton(Devices.Keys.LOWERZDRIVE, Joystick.Directions.NONE, 10, "+"));
      add(makeMoveToOriginButton(Devices.Keys.LOWERZDRIVE, Joystick.Directions.NONE));
      add(makeSetOriginHereButton(Devices.Keys.LOWERZDRIVE, Joystick.Directions.NONE), "wrap");
      
      add(new JLabel(devices_.getDeviceDisplayVerbose(Devices.Keys.UPPERZDRIVE) + ":"));
      upperZPositionLabel_ = new JLabel("");
      add(upperZPositionLabel_);
      add(pu.makeSetPositionField(Devices.Keys.UPPERZDRIVE, Joystick.Directions.NONE, positions_));
      add(makeIncrementButton(Devices.Keys.UPPERZDRIVE, Joystick.Directions.NONE, -10, "-"), "split 2");
      add(makeIncrementButton(Devices.Keys.UPPERZDRIVE, Joystick.Directions.NONE, 10, "+"));
      add(makeMoveToOriginButton(Devices.Keys.UPPERZDRIVE, Joystick.Directions.NONE));
      add(makeSetOriginHereButton(Devices.Keys.UPPERZDRIVE, Joystick.Directions.NONE), "wrap");   
      
      beamPanel_ = new BeamSubPanel(devices_, panelName_, Devices.Sides.NONE, prefs_, props_);
      beamPanel_.setBorder(BorderFactory.createLineBorder(ASIdiSPIM.borderColor));
      add(beamPanel_, "center, span 2 3");
      
      add(new JLabel(devices_.getDeviceDisplayVerbose(Devices.Keys.PIEZOA) + ":"));
      piezoAPositionLabel_ = new JLabel("");
      add(piezoAPositionLabel_);
      add(pu.makeSetPositionField(Devices.Keys.PIEZOA, Joystick.Directions.NONE, positions_));
      add(makeIncrementButton(Devices.Keys.PIEZOA, Joystick.Directions.NONE, -5, "-"), "split 2");
      add(makeIncrementButton(Devices.Keys.PIEZOA, Joystick.Directions.NONE, 5, "+"));
      add(makeMoveToOriginButton(Devices.Keys.PIEZOA, Joystick.Directions.NONE), "wrap");
      
      add(new JLabel(devices_.getDeviceDisplayVerbose(Devices.Keys.PIEZOB) + ":"));
      piezoBPositionLabel_ = new JLabel("");
      add(piezoBPositionLabel_);
      add(pu.makeSetPositionField(Devices.Keys.PIEZOB, Joystick.Directions.NONE, positions_));
      add(makeIncrementButton(Devices.Keys.PIEZOB, Joystick.Directions.NONE, -5, "-"), "split 2");
      add(makeIncrementButton(Devices.Keys.PIEZOB, Joystick.Directions.NONE, 5, "+"));
      add(makeMoveToOriginButton(Devices.Keys.PIEZOB, Joystick.Directions.NONE), "wrap");

      add(new JLabel(devices_.getDeviceDisplayVerbose(Devices.Keys.GALVOA, Joystick.Directions.X) + ":"));
      galvoAxPositionLabel_ = new JLabel("");
      add(galvoAxPositionLabel_);
      add(pu.makeSetPositionField(Devices.Keys.GALVOA, Joystick.Directions.X, positions_));
      add(makeIncrementButton(Devices.Keys.GALVOA, Joystick.Directions.X, -0.2, "-"), "split 2");
      add(makeIncrementButton(Devices.Keys.GALVOA, Joystick.Directions.X, 0.2, "+"));
      add(makeMoveToOriginButton(Devices.Keys.GALVOA, Joystick.Directions.X), "wrap");
      
      cameraPanel_ = new CameraSubPanel(cameras_, devices_, panelName_, Devices.Sides.NONE, prefs_, true);
      cameraPanel_.setBorder(BorderFactory.createLineBorder(ASIdiSPIM.borderColor)); 
      add(cameraPanel_, "center, span 2 2");
      
      add(new JLabel(devices_.getDeviceDisplayVerbose(Devices.Keys.GALVOA, Joystick.Directions.Y) + ":"));
      galvoAyPositionLabel_ = new JLabel("");
      add(galvoAyPositionLabel_);
      add(pu.makeSetPositionField(Devices.Keys.GALVOA, Joystick.Directions.Y, positions_));
      add(makeIncrementButton(Devices.Keys.GALVOA, Joystick.Directions.Y, -0.2, "-"), "split 2");
      add(makeIncrementButton(Devices.Keys.GALVOA, Joystick.Directions.Y, 0.2, "+"));
      add(makeMoveToOriginButton(Devices.Keys.GALVOA, Joystick.Directions.Y), "wrap");
      
      add(new JLabel(devices_.getDeviceDisplayVerbose(Devices.Keys.GALVOB, Joystick.Directions.X) + ":"));
      galvoBxPositionLabel_ = new JLabel("");
      add(galvoBxPositionLabel_);
      add(pu.makeSetPositionField(Devices.Keys.GALVOB, Joystick.Directions.X, positions_));
      add(makeIncrementButton(Devices.Keys.GALVOB, Joystick.Directions.X, -0.2, "-"), "split 2");
      add(makeIncrementButton(Devices.Keys.GALVOB, Joystick.Directions.X, 0.2, "+"));
      add(makeMoveToOriginButton(Devices.Keys.GALVOB, Joystick.Directions.X), "wrap");
      
      final JCheckBox activeTimerCheckBox = new JCheckBox("Update positions continually");
      ActionListener ae = new ActionListener() {
         public void actionPerformed(ActionEvent e) { 
            if (activeTimerCheckBox.isSelected()) {
               stagePosUpdater_.start();
            } else {
               stagePosUpdater_.stop();
            }
            prefs_.putBoolean(panelName_, Prefs.Keys.ENABLE_POSITION_UPDATES, activeTimerCheckBox.isSelected());
         }
      }; 
      activeTimerCheckBox.addActionListener(ae);
      activeTimerCheckBox.setSelected(prefs_.getBoolean(panelName_, Prefs.Keys.ENABLE_POSITION_UPDATES, true));
      // programmatically click twice to make sure the action handler is called;
      //   it is not called by setSelected unless there is a change in the value
      activeTimerCheckBox.doClick();
      activeTimerCheckBox.doClick();
      add(activeTimerCheckBox, "center, span 2");
      
      add(new JLabel(devices_.getDeviceDisplayVerbose(Devices.Keys.GALVOB, Joystick.Directions.Y) + ":"));
      galvoByPositionLabel_ = new JLabel("");
      add(galvoByPositionLabel_);
      add(pu.makeSetPositionField(Devices.Keys.GALVOB, Joystick.Directions.Y, positions_));
      add(makeIncrementButton(Devices.Keys.GALVOB, Joystick.Directions.Y, -0.2, "-"), "split 2");
      add(makeIncrementButton(Devices.Keys.GALVOB, Joystick.Directions.Y, 0.2, "+"));
      add(makeMoveToOriginButton(Devices.Keys.GALVOB, Joystick.Directions.Y), "wrap");
      
//      JButton buttonUpdate = new JButton("Update once");
//      buttonUpdate.addActionListener(new ActionListener() {
//         @Override
//         public void actionPerformed(ActionEvent e) {
//            stagePosUpdater.oneTimeUpdate();
//         }
//      });
//      add(buttonUpdate, "center");
      
      JButton buttonHalt = new JButton("Halt!");
      buttonHalt.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            try {
               String mmDevice = devices_.getMMDevice(Devices.Keys.UPPERZDRIVE);
               if (mmDevice == null) {
                  mmDevice = devices_.getMMDevice(Devices.Keys.XYSTAGE);
               }
               if (mmDevice == null) {
                  mmDevice = devices_.getMMDevice(Devices.Keys.LOWERZDRIVE);
               }
               if (mmDevice != null) {
                  String hubname = core_.getParentLabel(mmDevice);
                  String port = core_.getProperty(hubname, Properties.Keys.SERIAL_COM_PORT.toString());
                  core_.setSerialPortCommand(port, "\\",  "\r");
               }
            } catch (Exception ex) {
               ReportingUtils.showError("could not halt motion");
            }
         }
      });
      add(buttonHalt, "cell 11 0, span 1 10, growy");
      
      // fill the labels with position values (the positions_ data structure
      //   has been filled via the main plugin frame's call to stagePosUpdater.oneTimeUpdate()
      updateStagePositions();
      
   }

   
   /**
    * creates a button to go to origin "home" position.
    * @param key
    * @param dir
    * @return
    */
   private JButton makeMoveToOriginButton(Devices.Keys key, Joystick.Directions dir) {
      class homeButtonActionListener implements ActionListener {
         private final Devices.Keys key_;
         private final Joystick.Directions dir_;
         
         public void actionPerformed(ActionEvent e) {
            try {
               positions_.setPosition(key_, dir_, 0.0);
            } catch (Exception ex) {
               ReportingUtils.showError(ex);
            }
         }

         private homeButtonActionListener(Devices.Keys key, Joystick.Directions dir) {
            key_ = key;
            dir_ = dir;
         }
      }
      
      JButton jb = new JButton("Go to 0");
      jb.setToolTipText("Similar to pressing \"HOME\" button on joystick case, but only for this axis");
      ActionListener l = new homeButtonActionListener(key, dir);
      jb.addActionListener(l);
      return jb;
   }
   
   /**
    * Creates a button which set the origin to the current position in specified axis.
    * Somewhat inefficient implementation because actionPerformed()
    * handles all the cases every call instead of having the constructor
    * sort through cases and attaching variants of the actionPerformed() listener
    * @param key
    * @param dir
    * @return
    */
   private JButton makeSetOriginHereButton(Devices.Keys key, Joystick.Directions dir) {
      class zeroButtonActionListener implements ActionListener {
         private final Devices.Keys key_;
         private final Joystick.Directions dir_;
         
         public void actionPerformed(ActionEvent e) {
            int dialogResult = JOptionPane.showConfirmDialog(null,
                  "This will change the coordinate system.  Are you sure you want to proceed?",
                  "Warning",
                  JOptionPane.OK_CANCEL_OPTION);
            if (dialogResult == JOptionPane.OK_OPTION) {
               positions_.setOrigin(key_, dir_);
            }
         }

         private zeroButtonActionListener(Devices.Keys key, Joystick.Directions dir) {
            key_ = key;
            dir_ = dir;
         }
      }
      
      JButton jb = new JButton("Set 0");
      jb.setToolTipText("Similar to pressing \"ZERO\" button on joystick, but only for this axis");
      ActionListener l = new zeroButtonActionListener(key, dir);
      jb.addActionListener(l);
      return jb;
   }
   
   private JButton makeIncrementButton(Devices.Keys key, Joystick.Directions dir, double delta, String label) {
      class incrementButtonActionListener implements ActionListener {
         private final Devices.Keys key_;
         private final Joystick.Directions dir_;
         private final double delta_;
         
         public void actionPerformed(ActionEvent e) {
            try {
               positions_.setPositionRelative(key_, dir_, delta_);
            } catch (Exception ex) {
               ReportingUtils.showError(ex);
            }
         }

         private incrementButtonActionListener(Devices.Keys key, Joystick.Directions dir, double delta) {
            key_ = key;
            dir_ = dir;
            delta_ = delta;
         }
      }
      
      JButton jb = new JButton(label);
      ActionListener l = new incrementButtonActionListener(key, dir, delta);
      jb.addActionListener(l);
      return jb;
   }

   /**
    * required by LiveModeListener interface; just pass call along to camera panel
    */
   public void liveModeEnabled(boolean enable) { 
      cameraPanel_.liveModeEnabled(enable);
   } 
   
   @Override
   public void saveSettings() {
      beamPanel_.saveSettings();
      // all other prefs are updated on button press instead of here
   }
   
   /**
    * Gets called when this tab gets focus.  Sets the physical UI in the Tiger
    * controller to what was selected in this pane
    */
   @Override
   public void gotSelected() {
      joystickPanel_.gotSelected();
      cameraPanel_.gotSelected();
      beamPanel_.gotSelected();
//      props_.callListeners();  // not used yet, only for SPIM Params
   }
   
   @Override
   public final void updateStagePositions() {
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
