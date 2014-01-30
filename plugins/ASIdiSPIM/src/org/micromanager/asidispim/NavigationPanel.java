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
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.prefs.Preferences;

import org.micromanager.MMStudioMainFrame;
import org.micromanager.asidispim.Data.Devices;
import org.micromanager.asidispim.Data.Joystick;
import org.micromanager.asidispim.Data.Positions;
import org.micromanager.asidispim.Data.Properties;
import org.micromanager.asidispim.Utils.ListeningJPanel;

import javax.swing.JButton;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
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
              "[]6[]"));
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
      add(makeSetPositionField(Devices.Keys.XYSTAGE, Joystick.Directions.X));
      add(makeMoveToOriginButton(Devices.Keys.XYSTAGE, Joystick.Directions.X));
      add(makeSetOriginHereButton(Devices.Keys.XYSTAGE, Joystick.Directions.X), "wrap");
      
      add(new JLabel(devices_.getDeviceDisplayWithAxis1D(Devices.Keys.XYSTAGE, Joystick.Directions.Y) + ":"));
      yPositionLabel_ = new JLabel("");
      add(yPositionLabel_);
      add(makeSetPositionField(Devices.Keys.XYSTAGE, Joystick.Directions.Y));
      add(makeMoveToOriginButton(Devices.Keys.XYSTAGE, Joystick.Directions.Y));
      add(makeSetOriginHereButton(Devices.Keys.XYSTAGE, Joystick.Directions.Y), "wrap");
      
      add(new JLabel(devices_.getDeviceDisplayWithAxis(Devices.Keys.LOWERZDRIVE) + ":"));
      lowerZPositionLabel_ = new JLabel("");
      add(lowerZPositionLabel_);
      add(makeSetPositionField(Devices.Keys.LOWERZDRIVE, Joystick.Directions.NONE));
      add(makeMoveToOriginButton(Devices.Keys.LOWERZDRIVE, Joystick.Directions.NONE));
      add(makeSetOriginHereButton(Devices.Keys.LOWERZDRIVE, Joystick.Directions.NONE), "wrap");
      
      add(new JLabel(devices_.getDeviceDisplayWithAxis(Devices.Keys.UPPERZDRIVE) + ":"));
      upperZPositionLabel_ = new JLabel("");
      add(upperZPositionLabel_);
      add(makeSetPositionField(Devices.Keys.UPPERZDRIVE, Joystick.Directions.NONE));
      add(makeMoveToOriginButton(Devices.Keys.UPPERZDRIVE, Joystick.Directions.NONE));
      add(makeSetOriginHereButton(Devices.Keys.UPPERZDRIVE, Joystick.Directions.NONE), "wrap");
      
      final JCheckBox activeTimerCheckBox = new JCheckBox("Update positions continually");
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
      add(makeSetPositionField(Devices.Keys.PIEZOA, Joystick.Directions.NONE));
      add(makeMoveToOriginButton(Devices.Keys.PIEZOA, Joystick.Directions.NONE), "wrap");
      
      JButton buttonUpdate = new JButton("Update positions once");
      buttonUpdate.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            stagePosUpdater.oneTimeUpdate();
         }
      });
      add(buttonUpdate, "align left, span 2");
      
      add(new JLabel(devices_.getDeviceDisplayWithAxis(Devices.Keys.PIEZOB) + ":"));
      piezoBPositionLabel_ = new JLabel("");
      add(piezoBPositionLabel_);
      add(makeSetPositionField(Devices.Keys.PIEZOB, Joystick.Directions.NONE));
      add(makeMoveToOriginButton(Devices.Keys.PIEZOB, Joystick.Directions.NONE), "wrap");
      
      add(new JLabel(devices_.getDeviceDisplayWithAxis1D(Devices.Keys.GALVOA, Joystick.Directions.X)
            + " (in sheet plane):"), "span 3");
      galvoAxPositionLabel_ = new JLabel("");
      add(galvoAxPositionLabel_);
      add(makeSetPositionField(Devices.Keys.GALVOA, Joystick.Directions.X));
      add(makeMoveToOriginButton(Devices.Keys.GALVOA, Joystick.Directions.X), "wrap");
      
      add(new JLabel(devices_.getDeviceDisplayWithAxis1D(Devices.Keys.GALVOA, Joystick.Directions.Y)
            + " (slice position):"), "span 3");
      galvoAyPositionLabel_ = new JLabel("");
      add(galvoAyPositionLabel_);
      add(makeSetPositionField(Devices.Keys.GALVOA, Joystick.Directions.Y));
      add(makeMoveToOriginButton(Devices.Keys.GALVOA, Joystick.Directions.Y), "wrap");
      
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
      add(buttonHalt, "align left, span 1 2, growy");
      
      add(new JLabel(devices_.getDeviceDisplayWithAxis1D(Devices.Keys.GALVOB, Joystick.Directions.X)
            + " (in sheet plane):"), "span 2, align right");
      galvoBxPositionLabel_ = new JLabel("");
      add(galvoBxPositionLabel_);
      add(makeSetPositionField(Devices.Keys.GALVOB, Joystick.Directions.X));
      add(makeMoveToOriginButton(Devices.Keys.GALVOB, Joystick.Directions.X), "wrap");
      
      add(new JLabel(devices_.getDeviceDisplayWithAxis1D(Devices.Keys.GALVOB, Joystick.Directions.Y)
            + " (slice position):"), "span 2, align right");
      galvoByPositionLabel_ = new JLabel("");
      add(galvoByPositionLabel_);
      add(makeSetPositionField(Devices.Keys.GALVOB, Joystick.Directions.Y));
      add(makeMoveToOriginButton(Devices.Keys.GALVOB, Joystick.Directions.Y), "wrap");
      
      // fill the labels with position values (the positions_ data structure
      //   has been filled via the main plugin frame's call to stagePosUpdater.oneTimeUpdate()
      updateStagePositions();
      
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
      jb.setToolTipText("Similar to pressing \"HOME\" button on joystick, but only for this axis");
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
   
   private JFormattedTextField makeSetPositionField(Devices.Keys key, Joystick.Directions dir) {

      class setPositionListener implements PropertyChangeListener { 
         private final Devices.Keys key_;
         private final Joystick.Directions dir_;

         public void propertyChange(PropertyChangeEvent evt) {
            try {
               positions_.setPosition(key_, dir_, ((Number)evt.getNewValue()).doubleValue());
            } catch (Exception e) {
               ReportingUtils.showError(e);
            }
         }

         setPositionListener(Devices.Keys key, Joystick.Directions dir) {
            key_ = key;
            dir_ = dir;
         }
      }

      JFormattedTextField tf = new JFormattedTextField();
      tf.setValue(new Double(positions_.getPosition(key, dir)));
      tf.setColumns(5);
      PropertyChangeListener pc = new setPositionListener(key, dir);
      tf.addPropertyChangeListener("value", pc);
      return tf;
   }
}
