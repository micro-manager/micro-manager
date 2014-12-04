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


import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;

import org.micromanager.asidispim.Data.Cameras;
import org.micromanager.asidispim.Data.Devices;
import org.micromanager.asidispim.Data.Joystick;
import org.micromanager.asidispim.Data.MyStrings;
import org.micromanager.asidispim.Data.Positions;
import org.micromanager.asidispim.Data.Prefs;
import org.micromanager.asidispim.Data.Properties;
import org.micromanager.asidispim.Utils.ListeningJPanel;
import org.micromanager.asidispim.Utils.MyDialogUtils;
import org.micromanager.asidispim.Utils.PanelUtils;

import javax.swing.JButton;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JOptionPane;

import mmcorej.CMMCore;
import net.miginfocom.swing.MigLayout;

import org.micromanager.api.ScriptInterface;
import org.micromanager.internalinterfaces.LiveModeListener;


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
   private final ScriptInterface gui_;
   private final CMMCore core_;
   
   private final JoystickSubPanel joystickPanel_;
   private final CameraSubPanel cameraPanel_;
   private final BeamSubPanel beamPanel_;
   
   private final JLabel xPositionLabel_;
   private final JLabel yPositionLabel_;
   private final JLabel lowerZPositionLabel_;
   private final JLabel upperZPositionLabel_;
   private final JLabel piezoAPositionLabel_;
   private final JLabel piezoBPositionLabel_;
   private final JLabel galvoAxPositionLabel_;
   private final JLabel galvoAyPositionLabel_;
   private final JLabel galvoBxPositionLabel_;
   private final JLabel galvoByPositionLabel_;
   
   /**
    * Navigation panel constructor.
    */
   public NavigationPanel(ScriptInterface gui, Devices devices, Properties props, 
           Joystick joystick, Positions positions, Prefs prefs, Cameras cameras) {    
      super (MyStrings.PanelNames.NAVIGATION.toString(),
            new MigLayout(
              "", 
              "[right]8[align center]16[right]8[60px,center]8[center]8[center]2[center]2[center]8[center]8[center]8[center]",
              "[]6[]"));
      devices_ = devices;
      props_ = props;
      joystick_ = joystick;
      positions_ = positions;
      prefs_ = prefs;
      cameras_ = cameras;
      gui_ = gui;
      core_ = gui_.getMMCore();
      PanelUtils pu = new PanelUtils(prefs_, props_, devices_);
      
      joystickPanel_ = new JoystickSubPanel(joystick_, devices_, panelName_, Devices.Sides.NONE, prefs_);
      add(joystickPanel_, "span 2 4");  // make artificially tall to keep stage positions in line with each other
      
      add(new JLabel(devices_.getDeviceDisplayVerbose(Devices.Keys.XYSTAGE, Joystick.Directions.X) + ":"));
      xPositionLabel_ = new JLabel("");
      add(xPositionLabel_);
      add(pu.makeSetPositionField(Devices.Keys.XYSTAGE, Joystick.Directions.X, positions_));
      JFormattedTextField deltaXField = pu.makeFloatEntryField(panelName_, "DeltaX", 10.0, 3);
      add(makeIncrementButton(Devices.Keys.XYSTAGE, Joystick.Directions.X, deltaXField, "-", -1));
      add(deltaXField);
      add(makeIncrementButton(Devices.Keys.XYSTAGE, Joystick.Directions.X, deltaXField, "+", 1));
      add(makeMoveToOriginButton(Devices.Keys.XYSTAGE, Joystick.Directions.X));
      add(makeSetOriginHereButton(Devices.Keys.XYSTAGE, Joystick.Directions.X), "wrap");
      
      add(new JLabel(devices_.getDeviceDisplayVerbose(Devices.Keys.XYSTAGE, Joystick.Directions.Y) + ":"));
      yPositionLabel_ = new JLabel("");
      add(yPositionLabel_);
      add(pu.makeSetPositionField(Devices.Keys.XYSTAGE, Joystick.Directions.Y, positions_));
      JFormattedTextField deltaYField = pu.makeFloatEntryField(panelName_, "DeltaY", 10.0, 3);
      add(makeIncrementButton(Devices.Keys.XYSTAGE, Joystick.Directions.Y, deltaYField, "-", -1));
      add(deltaYField);
      add(makeIncrementButton(Devices.Keys.XYSTAGE, Joystick.Directions.Y, deltaYField, "+", 1));
      add(makeMoveToOriginButton(Devices.Keys.XYSTAGE, Joystick.Directions.Y));
      add(makeSetOriginHereButton(Devices.Keys.XYSTAGE, Joystick.Directions.Y), "wrap");
      
      add(new JLabel(devices_.getDeviceDisplayVerbose(Devices.Keys.LOWERZDRIVE) + ":"));
      lowerZPositionLabel_ = new JLabel("");
      add(lowerZPositionLabel_);
      add(pu.makeSetPositionField(Devices.Keys.LOWERZDRIVE, Joystick.Directions.NONE, positions_));
      JFormattedTextField deltaZField = pu.makeFloatEntryField(panelName_, "DeltaZ", 10.0, 3);
      add(makeIncrementButton(Devices.Keys.LOWERZDRIVE, Joystick.Directions.NONE, deltaZField, "-", -1));
      add(deltaZField);
      add(makeIncrementButton(Devices.Keys.LOWERZDRIVE, Joystick.Directions.NONE, deltaZField, "+", 1));
      add(makeMoveToOriginButton(Devices.Keys.LOWERZDRIVE, Joystick.Directions.NONE));
      add(makeSetOriginHereButton(Devices.Keys.LOWERZDRIVE, Joystick.Directions.NONE), "wrap");
      
      add(new JLabel(devices_.getDeviceDisplayVerbose(Devices.Keys.UPPERZDRIVE) + ":"));
      upperZPositionLabel_ = new JLabel("");
      add(upperZPositionLabel_);
      add(pu.makeSetPositionField(Devices.Keys.UPPERZDRIVE, Joystick.Directions.NONE, positions_));
      JFormattedTextField deltaFField = pu.makeFloatEntryField(panelName_, "DeltaF", 10.0, 3);
      add(makeIncrementButton(Devices.Keys.UPPERZDRIVE, Joystick.Directions.NONE, deltaFField, "-", -1));
      add(deltaFField);
      add(makeIncrementButton(Devices.Keys.UPPERZDRIVE, Joystick.Directions.NONE, deltaFField, "+", 1));
      add(makeMoveToOriginButton(Devices.Keys.UPPERZDRIVE, Joystick.Directions.NONE));
      add(makeSetOriginHereButton(Devices.Keys.UPPERZDRIVE, Joystick.Directions.NONE), "wrap");   
      
      beamPanel_ = new BeamSubPanel(gui_, devices_, panelName_, Devices.Sides.NONE, prefs_, props_);
      beamPanel_.setBorder(BorderFactory.createLineBorder(ASIdiSPIM.borderColor));
      add(beamPanel_, "center, span 2 3");
      
      add(new JLabel(devices_.getDeviceDisplayVerbose(Devices.Keys.PIEZOA) + ":"));
      piezoAPositionLabel_ = new JLabel("");
      add(piezoAPositionLabel_);
      JFormattedTextField deltaPField = pu.makeFloatEntryField(panelName_, "DeltaP", 5.0, 3);
      add(pu.makeSetPositionField(Devices.Keys.PIEZOA, Joystick.Directions.NONE, positions_));
      add(makeIncrementButton(Devices.Keys.PIEZOA, Joystick.Directions.NONE, deltaPField, "-", -1));
      add(deltaPField);
      add(makeIncrementButton(Devices.Keys.PIEZOA, Joystick.Directions.NONE, deltaPField, "+", 1));
      add(makeMoveToOriginButton(Devices.Keys.PIEZOA, Joystick.Directions.NONE), "wrap");
      
      add(new JLabel(devices_.getDeviceDisplayVerbose(Devices.Keys.PIEZOB) + ":"));
      piezoBPositionLabel_ = new JLabel("");
      add(piezoBPositionLabel_);
      add(pu.makeSetPositionField(Devices.Keys.PIEZOB, Joystick.Directions.NONE, positions_));
      JFormattedTextField deltaQField = pu.makeFloatEntryField(panelName_, "DeltaQ", 5.0, 3);
      add(makeIncrementButton(Devices.Keys.PIEZOB, Joystick.Directions.NONE, deltaQField, "-", -1));
      add(deltaQField);
      add(makeIncrementButton(Devices.Keys.PIEZOB, Joystick.Directions.NONE, deltaQField, "+", 1));
      add(makeMoveToOriginButton(Devices.Keys.PIEZOB, Joystick.Directions.NONE), "wrap");

      add(new JLabel(devices_.getDeviceDisplayVerbose(Devices.Keys.GALVOA, Joystick.Directions.X) + ":"));
      galvoAxPositionLabel_ = new JLabel("");
      add(galvoAxPositionLabel_);
      add(pu.makeSetPositionField(Devices.Keys.GALVOA, Joystick.Directions.X, positions_));
      JFormattedTextField deltaAField = pu.makeFloatEntryField(panelName_, "DeltaA", 0.2, 3);
      add(makeIncrementButton(Devices.Keys.GALVOA, Joystick.Directions.X, deltaAField, "-", -1));
      add(deltaAField);
      add(makeIncrementButton(Devices.Keys.GALVOA, Joystick.Directions.X, deltaAField, "+", 1));
      add(makeMoveToOriginButton(Devices.Keys.GALVOA, Joystick.Directions.X), "wrap");
      
      cameraPanel_ = new CameraSubPanel(gui_, cameras_, devices_, panelName_, 
              Devices.Sides.NONE, prefs_, true);
      cameraPanel_.setBorder(BorderFactory.createLineBorder(ASIdiSPIM.borderColor)); 
      add(cameraPanel_, "center, span 2 2");
      
      add(new JLabel(devices_.getDeviceDisplayVerbose(Devices.Keys.GALVOA, Joystick.Directions.Y) + ":"));
      galvoAyPositionLabel_ = new JLabel("");
      add(galvoAyPositionLabel_);
      add(pu.makeSetPositionField(Devices.Keys.GALVOA, Joystick.Directions.Y, positions_));
      JFormattedTextField deltaBField = pu.makeFloatEntryField(panelName_, "DeltaB", 0.2, 3);
      add(makeIncrementButton(Devices.Keys.GALVOA, Joystick.Directions.Y, deltaBField, "-", -1));
      add(deltaBField);
      add(makeIncrementButton(Devices.Keys.GALVOA, Joystick.Directions.Y, deltaBField, "+", 1));
      add(makeMoveToOriginButton(Devices.Keys.GALVOA, Joystick.Directions.Y), "wrap");
      
      add(new JLabel(devices_.getDeviceDisplayVerbose(Devices.Keys.GALVOB, Joystick.Directions.X) + ":"));
      galvoBxPositionLabel_ = new JLabel("");
      add(galvoBxPositionLabel_);
      add(pu.makeSetPositionField(Devices.Keys.GALVOB, Joystick.Directions.X, positions_));
      JFormattedTextField deltaCField = pu.makeFloatEntryField(panelName_, "DeltaC", 0.2, 3);
      add(makeIncrementButton(Devices.Keys.GALVOB, Joystick.Directions.X, deltaCField, "-", -1));
      add(deltaCField);
      add(makeIncrementButton(Devices.Keys.GALVOB, Joystick.Directions.X, deltaCField, "+", 1));
      add(makeMoveToOriginButton(Devices.Keys.GALVOB, Joystick.Directions.X), "wrap");
      
      add(new JLabel(devices_.getDeviceDisplayVerbose(Devices.Keys.GALVOB, Joystick.Directions.Y) + ":"), "skip 2");
      galvoByPositionLabel_ = new JLabel("");
      add(galvoByPositionLabel_);
      add(pu.makeSetPositionField(Devices.Keys.GALVOB, Joystick.Directions.Y, positions_));
      JFormattedTextField deltaDField = pu.makeFloatEntryField(panelName_, "DeltaD", 0.2, 3);
      add(makeIncrementButton(Devices.Keys.GALVOB, Joystick.Directions.Y, deltaDField, "-", -1));
      add(deltaDField);
      add(makeIncrementButton(Devices.Keys.GALVOB, Joystick.Directions.Y, deltaDField, "+", 1));
      add(makeMoveToOriginButton(Devices.Keys.GALVOB, Joystick.Directions.Y), "wrap");
      
      JButton buttonHalt = new JButton("Halt!");
      buttonHalt.setMargin(new Insets(4,8,4,8));
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
               MyDialogUtils.showError("could not halt motion");
            }
         }
      });
      add(buttonHalt, "cell 11 0, span 1 10, growy");
      
      // fill the labels with position values (the positions_ data structure
      //   has been filled via the main plugin frame's call to stagePosUpdater.oneTimeUpdate()
      updateStagePositions();
      
   }// constructor

   
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
         
         @Override
         public void actionPerformed(ActionEvent e) {
            try {
               positions_.setPosition(key_, dir_, 0.0);
            } catch (Exception ex) {
               MyDialogUtils.showError(ex);
            }
         }

         private homeButtonActionListener(Devices.Keys key, Joystick.Directions dir) {
            key_ = key;
            dir_ = dir;
         }
      }
      
      JButton jb = new JButton("Go to 0");
      jb.setMargin(new Insets(4,8,4,8));
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
   private JButton makeSetOriginHereButton(Devices.Keys key, 
           Joystick.Directions dir) {
      class zeroButtonActionListener implements ActionListener {
         private final Devices.Keys key_;
         private final Joystick.Directions dir_;
         
         @Override
         public void actionPerformed(ActionEvent e) {
            if (MyDialogUtils.getConfirmDialogResult(
                  "This will change the coordinate system.  Are you sure you want to proceed?",
                  JOptionPane.OK_CANCEL_OPTION)) {
               positions_.setOrigin(key_, dir_);
            }
         }

         private zeroButtonActionListener(Devices.Keys key, 
                 Joystick.Directions dir) {
            key_ = key;
            dir_ = dir;
         }
      }
      
      JButton jb = new JButton("Set 0");
      jb.setMargin(new Insets(4,8,4,8));
      jb.setToolTipText("Similar to pressing \"ZERO\" button on joystick, but only for this axis");
      ActionListener l = new zeroButtonActionListener(key, dir);
      jb.addActionListener(l);
      return jb;
   }
   
   private JButton makeIncrementButton(Devices.Keys key, Joystick.Directions dir, 
         JFormattedTextField field, String label, double scaleFactor) {
      class incrementButtonActionListener implements ActionListener {
         private final Devices.Keys key_;
         private final Joystick.Directions dir_;
         private final JFormattedTextField field_;
         private final double scaleFactor_;
         
         @Override
         public void actionPerformed(ActionEvent e) {
            try {
               positions_.setPositionRelative(key_, dir_, 
                       ((Double)field_.getValue()) * scaleFactor_);
            } catch (Exception ex) {
               MyDialogUtils.showError(ex);
            }
         }

         private incrementButtonActionListener(Devices.Keys key, Joystick.Directions dir, 
               JFormattedTextField field, double scaleFactor) {
            key_ = key;
            dir_ = dir;
            field_ = field;
            scaleFactor_ = scaleFactor;
         }
      }
      
      JButton jb = new JButton(label);
      jb.setMargin(new Insets(4,8,4,8));
      ActionListener l = new incrementButtonActionListener(key, dir, field, scaleFactor);
      jb.addActionListener(l);
      return jb;
   }
   
   /**
    * required by LiveModeListener interface; just pass call along to camera panel
    * @param enable
    */
   @Override
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
   
   /**
    * created so that Navigation panel's joystick settings could be invoked from elsewhere
    */
   public void doJoystickSettings() {
      joystickPanel_.gotSelected();
   }
}
