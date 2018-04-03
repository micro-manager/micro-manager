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


import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.EnumSet;
import java.util.Set;

import javax.swing.BorderFactory;

import org.micromanager.MMStudio;
import org.micromanager.asidispim.Data.Cameras;
import org.micromanager.asidispim.Data.Devices;
import org.micromanager.asidispim.Data.Joystick;
import org.micromanager.asidispim.Data.Joystick.Directions;
import org.micromanager.asidispim.Data.MyStrings;
import org.micromanager.asidispim.Data.Positions;
import org.micromanager.asidispim.Data.Prefs;
import org.micromanager.asidispim.Data.Properties;
import org.micromanager.asidispim.Utils.ListeningJPanel;
import org.micromanager.asidispim.Utils.MyDialogUtils;
import org.micromanager.asidispim.Utils.PanelUtils;
import org.micromanager.asidispim.Utils.StagePositionUpdater;

import javax.swing.JButton;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import mmcorej.CMMCore;
import net.miginfocom.swing.MigLayout;

import org.micromanager.api.ScriptInterface;
import org.micromanager.internalinterfaces.LiveModeListener;

import com.swtdesigner.SwingResourceManager;


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
   private final StagePositionUpdater posUpdater_;
   private final ScriptInterface gui_;
   private final CMMCore core_;
   
   private final JoystickSubPanel joystickPanel_;
   private final CameraSubPanel cameraPanel_;
   private final BeamSubPanel beamPanel_;
   
   private final JLabel xPositionLabel_;
   private final JLabel yPositionLabel_;
   private final JLabel lowerZPositionLabel_;
   private final JLabel upperZPositionLabel_;
   private final JLabel upperHPositionLabel_;
   private final JLabel piezoAPositionLabel_;
   private final JLabel piezoBPositionLabel_;
   private final JLabel galvoAxPositionLabel_;
   private final JLabel galvoAyPositionLabel_;
   private final JLabel galvoBxPositionLabel_;
   private final JLabel galvoByPositionLabel_;
   
   private final JFormattedTextField headUpPosition_;
   private final JFormattedTextField headDownPosition_;
   
   /**
    * Navigation panel constructor.
    * @param gui Micro-Manager script interface
    * @param devices our  Devices object
    * @param props
    * @param joystick
    * @param positions
    * @param prefs
    * @param cameras
    * @param posUpdater Class that will continuously update the stage positions
    */
   public NavigationPanel(ScriptInterface gui, Devices devices, Properties props, 
           Joystick joystick, Positions positions, Prefs prefs, Cameras cameras,
           StagePositionUpdater posUpdater) {    
      super (MyStrings.PanelNames.NAVIGATION.toString(),
            new MigLayout(
              "", 
              "[center]8[center]",
              "[]8[]8[]"));
      devices_ = devices;
      props_ = props;
      joystick_ = joystick;
      positions_ = positions;
      prefs_ = prefs;
      cameras_ = cameras;
      posUpdater_ = posUpdater;
      gui_ = gui;
      core_ = gui_.getMMCore();
      PanelUtils pu = new PanelUtils(prefs_, props_, devices_);
      
      JPanel loadPanel = new JPanel(new MigLayout(
            "",
            "[center]",
            "[]4[]4[]10[]4[]4[]"));
      
      loadPanel.setBorder(BorderFactory.createLineBorder(ASIdiSPIM.borderColor));

      // buttons to move the head to top/bottom for easy sample loading
      JButton headUpGo = new JButton();
      headUpGo.setIcon(SwingResourceManager.getIcon(MMStudio.class, "icons/arrow_up.png"));
      headUpGo.setText("");
      headUpGo.setToolTipText("Move SPIM head to set height for sample loading");
      headUpGo.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            raiseSPIMHead();
         }
      });
      JButton headDownGo = new JButton();
      headDownGo.setIcon(SwingResourceManager.getIcon(MMStudio.class, "icons/arrow_down.png"));
      headDownGo.setText("");
      headDownGo.setToolTipText("Move SPIM head to set height after sample loading");
      headDownGo.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            lowerSPIMHead();
         }
      });

      loadPanel.add(new JLabel("Load Sample"), "wrap");
      loadPanel.add(headUpGo, "wrap");
      headUpPosition_ = pu.makeFloatEntryField(panelName_, "HeadUpPosition", 25000, 5);
      headDownPosition_ = pu.makeFloatEntryField(panelName_, "HeadDownPosition", 1000, 5);
      loadPanel.add(headUpPosition_, "wrap");
      loadPanel.add(headDownPosition_, "wrap");
      loadPanel.add(headDownGo, "wrap");
      loadPanel.add(new JLabel("Start Hunting"));
      
      final int positionWidth = 60;
      
      // panel for stage positions / virtual joysticks
      JPanel navPanel = new JPanel(new MigLayout(
            "",
            "[right]8[" + positionWidth + "px!,left]8[center]8[center]2[center]2[center]8[center]8[center]8[center]8[center]",
            "[]4[]"));
      navPanel.setBorder(BorderFactory.createLineBorder(ASIdiSPIM.borderColor));
      
      navPanel.add(new JLabel(devices_.getDeviceDisplayVerbose(Devices.Keys.XYSTAGE, Directions.X) + ":"));
      xPositionLabel_ = new JLabel("");
      navPanel.add(xPositionLabel_);
      navPanel.add(pu.makeSetPositionField(Devices.Keys.XYSTAGE, Directions.X, positions_));
      JFormattedTextField deltaXField = pu.makeFloatEntryField(panelName_, "DeltaX", 10.0, 3);
      navPanel.add(makeIncrementButton(Devices.Keys.XYSTAGE, Directions.X, deltaXField, "-", -1));
      navPanel.add(deltaXField);
      navPanel.add(makeIncrementButton(Devices.Keys.XYSTAGE, Directions.X, deltaXField, "+", 1));
      navPanel.add(makeMoveToOriginButton(Devices.Keys.XYSTAGE, Directions.X));
      navPanel.add(makeSetOriginHereButton(Devices.Keys.XYSTAGE, Directions.X), "skip 1, wrap");
      
      navPanel.add(new JLabel(devices_.getDeviceDisplayVerbose(Devices.Keys.XYSTAGE, Directions.Y) + ":"));
      yPositionLabel_ = new JLabel("");
      navPanel.add(yPositionLabel_);
      navPanel.add(pu.makeSetPositionField(Devices.Keys.XYSTAGE, Directions.Y, positions_));
      JFormattedTextField deltaYField = pu.makeFloatEntryField(panelName_, "DeltaY", 10.0, 3);
      navPanel.add(makeIncrementButton(Devices.Keys.XYSTAGE, Directions.Y, deltaYField, "-", -1));
      navPanel.add(deltaYField);
      navPanel.add(makeIncrementButton(Devices.Keys.XYSTAGE, Directions.Y, deltaYField, "+", 1));
      navPanel.add(makeMoveToOriginButton(Devices.Keys.XYSTAGE, Directions.Y));
      navPanel.add(makeSetOriginHereButton(Devices.Keys.XYSTAGE, Directions.Y), "skip 1, wrap");
      
      navPanel.add(new JLabel(devices_.getDeviceDisplayVerbose(Devices.Keys.LOWERZDRIVE) + ":"));
      lowerZPositionLabel_ = new JLabel("");
      navPanel.add(lowerZPositionLabel_);
      navPanel.add(pu.makeSetPositionField(Devices.Keys.LOWERZDRIVE, Directions.NONE, positions_));
      JFormattedTextField deltaZField = pu.makeFloatEntryField(panelName_, "DeltaZ", 10.0, 3);
      navPanel.add(makeIncrementButton(Devices.Keys.LOWERZDRIVE, Directions.NONE, deltaZField, "-", -1));
      navPanel.add(deltaZField);
      navPanel.add(makeIncrementButton(Devices.Keys.LOWERZDRIVE, Directions.NONE, deltaZField, "+", 1));
      navPanel.add(makeMoveToOriginButton(Devices.Keys.LOWERZDRIVE, Directions.NONE));
      JButton syncZtoF = new JButton("Sync");
      syncZtoF.setMargin(new Insets(4,8,4,8));
      syncZtoF.setToolTipText("Move lower Z position to match SPIM head height");
      syncZtoF.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            double upperPosition = positions_.getUpdatedPosition(Devices.Keys.UPPERZDRIVE);
            positions_.setPosition(Devices.Keys.LOWERZDRIVE, -upperPosition);
         }
      });
      if (ASIdiSPIM.oSPIM) {
         navPanel.add(new JLabel(""));
      } else {
         navPanel.add(syncZtoF);
      }
      navPanel.add(makeSetOriginHereButton(Devices.Keys.LOWERZDRIVE, Directions.NONE), "wrap");
      
      navPanel.add(new JLabel(devices_.getDeviceDisplayVerbose(Devices.Keys.UPPERZDRIVE) + ":"));
      upperZPositionLabel_ = new JLabel("");
      navPanel.add(upperZPositionLabel_);
      navPanel.add(pu.makeSetPositionField(Devices.Keys.UPPERZDRIVE, Directions.NONE, positions_));
      JFormattedTextField deltaFField = pu.makeFloatEntryField(panelName_, "DeltaF", 10.0, 3);
      navPanel.add(makeIncrementButton(Devices.Keys.UPPERZDRIVE, Directions.NONE, deltaFField, "-", -1));
      navPanel.add(deltaFField);
      navPanel.add(makeIncrementButton(Devices.Keys.UPPERZDRIVE, Directions.NONE, deltaFField, "+", 1));
      navPanel.add(makeMoveToOriginButton(Devices.Keys.UPPERZDRIVE, Directions.NONE));
      JButton syncFtoZ = new JButton("Sync");
      syncFtoZ.setMargin(new Insets(4,8,4,8));
      syncFtoZ.setToolTipText("Move SPIM head height to match lower Z position");
      syncFtoZ.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            double lowerPosition = positions_.getUpdatedPosition(Devices.Keys.LOWERZDRIVE);
            positions_.setPosition(Devices.Keys.UPPERZDRIVE, -lowerPosition);
         }
      });
      if (ASIdiSPIM.oSPIM) {
         navPanel.add(new JLabel(""));
      } else {
         navPanel.add(syncFtoZ);
      }
      navPanel.add(makeSetOriginHereButton(Devices.Keys.UPPERZDRIVE, Directions.NONE), "wrap");
      
      upperHPositionLabel_ = new JLabel("");
      if (!ASIdiSPIM.oSPIM) {
      } else {
         navPanel.add(new JLabel(devices_.getDeviceDisplayVerbose(Devices.Keys.UPPERHDRIVE) + ":"));
         navPanel.add(upperHPositionLabel_);
         navPanel.add(pu.makeSetPositionField(Devices.Keys.UPPERHDRIVE, Directions.NONE, positions_));
         JFormattedTextField deltaGField = pu.makeFloatEntryField(panelName_, "DeltaG", -5, 3);
         navPanel.add(makeIncrementButton(Devices.Keys.UPPERHDRIVE, Directions.NONE, deltaGField, "-", -1));
         navPanel.add(deltaGField);
         navPanel.add(makeIncrementButton(Devices.Keys.UPPERHDRIVE, Directions.NONE, deltaGField, "+", 1));
         navPanel.add(makeMoveToOriginButton(Devices.Keys.UPPERHDRIVE, Directions.NONE));
         navPanel.add(new JLabel(""));  // occupies sync space
         navPanel.add(makeSetOriginHereButton(Devices.Keys.UPPERHDRIVE, Directions.NONE), "wrap");
      }
      
      piezoAPositionLabel_ = new JLabel("");
      navPanel.add(new JLabel(devices_.getDeviceDisplayVerbose(Devices.Keys.PIEZOA) + ":"));
      navPanel.add(piezoAPositionLabel_);
      JFormattedTextField deltaPField = pu.makeFloatEntryField(panelName_, "DeltaP", 5.0, 3);
      navPanel.add(pu.makeSetPositionField(Devices.Keys.PIEZOA, Directions.NONE, positions_));
      navPanel.add(makeIncrementButton(Devices.Keys.PIEZOA, Directions.NONE, deltaPField, "-", -1));
      navPanel.add(deltaPField);
      navPanel.add(makeIncrementButton(Devices.Keys.PIEZOA, Directions.NONE, deltaPField, "+", 1));
      navPanel.add(makeMoveToOriginButton(Devices.Keys.PIEZOA, Directions.NONE), "wrap");
      
      piezoBPositionLabel_ = new JLabel("");
      if (!ASIdiSPIM.oSPIM) {
         navPanel.add(new JLabel(devices_.getDeviceDisplayVerbose(Devices.Keys.PIEZOB) + ":"));
         navPanel.add(piezoBPositionLabel_);
         navPanel.add(pu.makeSetPositionField(Devices.Keys.PIEZOB, Directions.NONE, positions_));
         JFormattedTextField deltaQField = pu.makeFloatEntryField(panelName_, "DeltaQ", 5.0, 3);
         navPanel.add(makeIncrementButton(Devices.Keys.PIEZOB, Directions.NONE, deltaQField, "-", -1));
         navPanel.add(deltaQField);
         navPanel.add(makeIncrementButton(Devices.Keys.PIEZOB, Directions.NONE, deltaQField, "+", 1));
         navPanel.add(makeMoveToOriginButton(Devices.Keys.PIEZOB, Directions.NONE), "wrap");
      }

      galvoAxPositionLabel_ = new JLabel("");
      galvoAyPositionLabel_ = new JLabel("");
      galvoBxPositionLabel_ = new JLabel("");
      galvoByPositionLabel_ = new JLabel("");
      
      navPanel.add(new JLabel(devices_.getDeviceDisplayVerbose(Devices.Keys.GALVOA, Directions.X) + ":"));
      navPanel.add(galvoAxPositionLabel_);
      navPanel.add(pu.makeSetPositionField(Devices.Keys.GALVOA, Directions.X, positions_));
      JFormattedTextField deltaAField = pu.makeFloatEntryField(panelName_, "DeltaA", 0.2, 3);
      navPanel.add(makeIncrementButton(Devices.Keys.GALVOA, Directions.X, deltaAField, "-", -1));
      navPanel.add(deltaAField);
      navPanel.add(makeIncrementButton(Devices.Keys.GALVOA, Directions.X, deltaAField, "+", 1));
      navPanel.add(makeMoveToOriginButton(Devices.Keys.GALVOA, Directions.X), "wrap");

      navPanel.add(new JLabel(devices_.getDeviceDisplayVerbose(Devices.Keys.GALVOA, Directions.Y) + ":"));
      navPanel.add(galvoAyPositionLabel_);
      navPanel.add(pu.makeSetPositionField(Devices.Keys.GALVOA, Directions.Y, positions_));
      JFormattedTextField deltaBField = pu.makeFloatEntryField(panelName_, "DeltaB", 0.2, 3);
      navPanel.add(makeIncrementButton(Devices.Keys.GALVOA, Directions.Y, deltaBField, "-", -1));
      navPanel.add(deltaBField);
      navPanel.add(makeIncrementButton(Devices.Keys.GALVOA, Directions.Y, deltaBField, "+", 1));
      navPanel.add(makeMoveToOriginButton(Devices.Keys.GALVOA, Directions.Y), "wrap");
      
      if (!ASIdiSPIM.oSPIM) {
         navPanel.add(new JLabel(devices_.getDeviceDisplayVerbose(Devices.Keys.GALVOB, Directions.X) + ":"));
         navPanel.add(galvoBxPositionLabel_);
         navPanel.add(pu.makeSetPositionField(Devices.Keys.GALVOB, Directions.X, positions_));
         JFormattedTextField deltaCField = pu.makeFloatEntryField(panelName_, "DeltaC", 0.2, 3);
         navPanel.add(makeIncrementButton(Devices.Keys.GALVOB, Directions.X, deltaCField, "-", -1));
         navPanel.add(deltaCField);
         navPanel.add(makeIncrementButton(Devices.Keys.GALVOB, Directions.X, deltaCField, "+", 1));
         navPanel.add(makeMoveToOriginButton(Devices.Keys.GALVOB, Directions.X), "wrap");

         navPanel.add(new JLabel(devices_.getDeviceDisplayVerbose(Devices.Keys.GALVOB, Directions.Y) + ":"));
         navPanel.add(galvoByPositionLabel_);
         navPanel.add(pu.makeSetPositionField(Devices.Keys.GALVOB, Directions.Y, positions_));
         JFormattedTextField deltaDField = pu.makeFloatEntryField(panelName_, "DeltaD", 0.2, 3);
         navPanel.add(makeIncrementButton(Devices.Keys.GALVOB, Directions.Y, deltaDField, "-", -1));
         navPanel.add(deltaDField);
         navPanel.add(makeIncrementButton(Devices.Keys.GALVOB, Directions.Y, deltaDField, "+", 1));
         navPanel.add(makeMoveToOriginButton(Devices.Keys.GALVOB, Directions.Y), "wrap");
      }
      
      if (!ASIdiSPIM.oSPIM) {
         navPanel.add(loadPanel, "cell 7 4, span 2 6, center, wrap");
      } else {
         navPanel.add(loadPanel, "cell 7 5, span 2 6, center, wrap");
      }
      
      JButton buttonHalt = new JButton("Halt!");
      buttonHalt.setMargin(new Insets(4,8,4,8));
      buttonHalt.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            haltAllMotion();
         }
      });
      navPanel.add(buttonHalt, "cell 12 0, span 1 10, growy, wrap");
      
      joystickPanel_ = new JoystickSubPanel(joystick_, devices_, panelName_, Devices.Sides.NONE, prefs_);
      this.add(joystickPanel_);
      
      this.add(navPanel, "aligny top, span 1 3, wrap");
      
      beamPanel_ = new BeamSubPanel(gui_, devices_, panelName_, Devices.Sides.NONE, prefs_, props_);
      this.add(beamPanel_, "wrap");

      cameraPanel_ = new CameraSubPanel(gui_, cameras_, devices_, panelName_, 
            Devices.Sides.NONE, prefs_);
      this.add(cameraPanel_);
      
      xPositionLabel_.setMaximumSize(new Dimension(positionWidth, 20));
      yPositionLabel_.setMaximumSize(new Dimension(positionWidth, 20));
      lowerZPositionLabel_.setMaximumSize(new Dimension(positionWidth, 20));
      upperZPositionLabel_.setMaximumSize(new Dimension(positionWidth, 20));
      piezoAPositionLabel_.setMaximumSize(new Dimension(positionWidth, 20));
      piezoBPositionLabel_.setMaximumSize(new Dimension(positionWidth, 20));
      galvoAxPositionLabel_.setMaximumSize(new Dimension(positionWidth, 20));
      galvoAyPositionLabel_.setMaximumSize(new Dimension(positionWidth, 20));
      galvoBxPositionLabel_.setMaximumSize(new Dimension(positionWidth, 20));
      galvoByPositionLabel_.setMaximumSize(new Dimension(positionWidth, 20));
      
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
      posUpdater_.pauseUpdates(true);
      joystickPanel_.gotSelected();
      cameraPanel_.gotSelected();
      beamPanel_.gotSelected();
//      props_.callListeners();  // not used yet, only for SPIM Params
      posUpdater_.pauseUpdates(false);
   }
   
   @Override
   public void gotDeSelected() {
      joystickPanel_.gotDeSelected();
   }
   
   /**
    * Called whenever position updater has refreshed positions
    */
   @Override
   public final void updateStagePositions() {
      xPositionLabel_.setText(positions_.getPositionString(Devices.Keys.XYSTAGE, Directions.X));   
      yPositionLabel_.setText(positions_.getPositionString(Devices.Keys.XYSTAGE, Directions.Y));
      lowerZPositionLabel_.setText(positions_.getPositionString(Devices.Keys.LOWERZDRIVE));
      upperZPositionLabel_.setText(positions_.getPositionString(Devices.Keys.UPPERZDRIVE));
      upperHPositionLabel_.setText(positions_.getPositionString(Devices.Keys.UPPERHDRIVE));
      piezoAPositionLabel_.setText(positions_.getPositionString(Devices.Keys.PIEZOA));
      piezoBPositionLabel_.setText(positions_.getPositionString(Devices.Keys.PIEZOB));
      galvoAxPositionLabel_.setText(positions_.getPositionString(Devices.Keys.GALVOA, Directions.X));
      galvoAyPositionLabel_.setText(positions_.getPositionString(Devices.Keys.GALVOA, Directions.Y));
      galvoBxPositionLabel_.setText(positions_.getPositionString(Devices.Keys.GALVOB, Directions.X));
      galvoByPositionLabel_.setText(positions_.getPositionString(Devices.Keys.GALVOB, Directions.Y));
   }
   
   /**
    * Called whenever position updater stops
    */
   @Override
   public final void stoppedStagePositions() {
      xPositionLabel_.setText("");
      yPositionLabel_.setText("");
      lowerZPositionLabel_.setText("");
      upperZPositionLabel_.setText("");
      upperHPositionLabel_.setText("");
      piezoAPositionLabel_.setText("");
      piezoBPositionLabel_.setText("");
      galvoAxPositionLabel_.setText("");
      galvoAyPositionLabel_.setText("");
      galvoBxPositionLabel_.setText("");
      galvoByPositionLabel_.setText("");
   }
   
   /**
    * created so that Navigation panel's joystick settings could be invoked from elsewhere
    */
   public void doJoystickSettings(boolean selected) {
      if (selected) {
         joystickPanel_.gotSelected();
      } else {
         joystickPanel_.gotDeSelected();
      }
   }

   public void raiseSPIMHead() {
      positions_.setPosition(Devices.Keys.UPPERZDRIVE, ((Number)headUpPosition_.getValue()).doubleValue());
   }
   
   public void lowerSPIMHead() {
      positions_.setPosition(Devices.Keys.UPPERZDRIVE, ((Number)headDownPosition_.getValue()).doubleValue());
   }


   public void setSPIMHeadRaisedPosition(double raised) {
      headUpPosition_.setValue(raised);
   }
   
   public double getSPIMHeadRaisedPosition() {
      return ((Number)headUpPosition_.getValue()).doubleValue();
   }
   
   public void setSPIMHeadLoweredPosition(double lowered) {
      headDownPosition_.setValue(lowered);
   }
   
   public double getSPIMHeadLoweredPosition() {
      return ((Number)headDownPosition_.getValue()).doubleValue();
   }


   public void haltAllMotion() {
      try {
         final Set<Devices.Keys> TigerDevices = EnumSet.of(
               Devices.Keys.UPPERZDRIVE, Devices.Keys.XYSTAGE, Devices.Keys.LOWERZDRIVE);
         for (Devices.Keys dev : TigerDevices) {
            if (devices_.isValidMMDevice(dev)
                  && devices_.getMMDeviceLibrary(dev) == Devices.Libraries.ASITIGER) {
               core_.stop(devices_.getMMDevice(dev));
            }
         }
      } catch (Exception ex) {
         MyDialogUtils.showError(ex, "could not halt motion");
      }
   }
}
