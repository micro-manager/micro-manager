///////////////////////////////////////////////////////////////////////////////
//FILE:          SetupPanel.java
//PROJECT:       Micro-Manager 
//SUBSYSTEM:     ASIdiSPIM plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman, Jon Daniels
//
// COPYRIGHT:    University of California, San Francisco, & ASI, 2013, 2014
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

import com.swtdesigner.SwingResourceManager;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

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
import org.micromanager.asidispim.Utils.StoredFloatLabel;

import javax.swing.*;

import net.miginfocom.swing.MigLayout;

import org.micromanager.MMStudio;
import org.micromanager.api.ScriptInterface;
import org.micromanager.asidispim.Utils.AutofocusUtils;
import org.micromanager.internalinterfaces.LiveModeListener;
import org.micromanager.utils.MMFrame;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author Nico
 * @author Jon
 */
@SuppressWarnings("serial")
public final class SetupPanel extends ListeningJPanel implements LiveModeListener {

   private final Devices devices_;
   private final Properties props_;
   private final Joystick joystick_;
   private final Devices.Sides side_;
   private final Positions positions_;
   private final Cameras cameras_;
   private final AutofocusUtils autofocus_;
   private final Prefs prefs_;
   private final StagePositionUpdater posUpdater_;
   private final ScriptInterface gui_;
   private final JoystickSubPanel joystickPanel_;
   private final CameraSubPanel cameraPanel_;
   private final BeamSubPanel beamPanel_;
   private final MMFrame slopeCalibrationFrame_;
   // TODO rearrange these variables
   // used to store the start/stop positions of the single-axis moves for imaging piezo and micromirror sheet move axis
   private final StoredFloatLabel sheetStartPositionLabel_;
   private final StoredFloatLabel sheetStopPositionLabel_;
   private final StoredFloatLabel imagingPiezoStartPositionLabel_;
   private final StoredFloatLabel imagingPiezoStopPositionLabel_;
   private double imagingPiezoStartPos_;
   private double imagingPiezoStopPos_;
   private double imagingCenterPos_;
   private double sliceStartPos_;
   private double sliceStopPos_;
   private final JCheckBox illumPiezoHomeEnable_;
   private final JFormattedTextField piezoDeltaField_;
   private final JFormattedTextField offsetField_;
   private final JFormattedTextField rateField_;
   // device keys, get assigned in constructor based on side
   private Devices.Keys piezoImagingDeviceKey_;
   private Devices.Keys piezoIlluminationDeviceKey_;
   private Devices.Keys micromirrorDeviceKey_;
   private final StoredFloatLabel imagingCenterPosLabel_;
   private final JLabel imagingPiezoPositionLabel_;
   private final JLabel illuminationPiezoPositionLabel_;
   private final JLabel sheetPositionLabel_;



   public SetupPanel(ScriptInterface gui, 
           Devices devices, 
           Properties props, 
           Joystick joystick, 
           Devices.Sides side, 
           Positions positions, 
           Cameras cameras, 
           Prefs prefs, 
           StagePositionUpdater posUpdater,
           AutofocusUtils autofocus) {
      super(MyStrings.PanelNames.SETUP.toString() + side.toString(),
              new MigLayout(
              "",
              "[center]8[center]",
              "[]16[]16[]"));
      
      devices_ = devices;
      props_ = props;
      joystick_ = joystick;
      side_ = side;
      positions_ = positions;
      cameras_ = cameras;
      autofocus_ = autofocus;
      prefs_ = prefs;
      posUpdater_ = posUpdater;
      gui_ = gui;
      PanelUtils pu = new PanelUtils(prefs_, props_, devices);
      final SetupPanel setupPanel = this;

      piezoImagingDeviceKey_ = Devices.getSideSpecificKey(Devices.Keys.PIEZOA, side_);
      piezoIlluminationDeviceKey_ = Devices.getSideSpecificKey(Devices.Keys.PIEZOA, Devices.getOppositeSide(side_));
      micromirrorDeviceKey_ = Devices.getSideSpecificKey(Devices.Keys.GALVOA, side_);

      sheetStartPositionLabel_ = new StoredFloatLabel(panelName_, 
              Properties.Keys.PLUGIN_SHEET_START_POS.toString(), -0.5f,
              prefs_, " \u00B0");
      sliceStartPos_ = sheetStartPositionLabel_.getFloat();
      sheetStopPositionLabel_ = new StoredFloatLabel(panelName_, 
              Properties.Keys.PLUGIN_SHEET_END_POS.toString(), 0.5f,
              prefs_, " \u00B0");
      sliceStopPos_ = sheetStopPositionLabel_.getFloat();
      imagingPiezoStartPositionLabel_ = new StoredFloatLabel(panelName_, 
              Properties.Keys.PLUGIN_PIEZO_START_POS.toString(), -50f,
              prefs_, " \u00B5" + "m");
      imagingPiezoStartPos_ = imagingPiezoStartPositionLabel_.getFloat();
      imagingPiezoStopPositionLabel_ = new StoredFloatLabel(panelName_, 
              Properties.Keys.PLUGIN_PIEZO_END_POS.toString(), 50f,
              prefs_, " \u00B5" + "m");
      imagingPiezoStopPos_ = imagingPiezoStopPositionLabel_.getFloat();
      
      JButton tmp_but;
      
      JPanel calibrationPanel = new JPanel(new MigLayout(
            "",
            "[right]2[center]2[right]4[left]8[center]8[center]8[center]",
            "[]8[]"));
      
      offsetField_ = pu.makeFloatEntryField(panelName_, 
            Properties.Keys.PLUGIN_OFFSET_PIEZO_SHEET.toString(), 0, 5);  
      rateField_ = pu.makeFloatEntryField(panelName_, 
            Properties.Keys.PLUGIN_RATE_PIEZO_SHEET.toString(), 100, 5);
      piezoDeltaField_ = pu.makeFloatEntryField(panelName_, 
            Properties.Keys.PLUGIN_PIEZO_SHEET_INCREMENT.toString(), 5, 3);
      piezoDeltaField_.setToolTipText("Piezo increment used by up/down arrow buttons");
      
      JButton upButton = new JButton();
      upButton.setIcon(SwingResourceManager.getIcon(MMStudio.class, "icons/arrow_up.png"));
      upButton.setText("");
      upButton.setToolTipText("Move slice and piezo up together");
      upButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            stepPiezoAndGalvo(1.);
         }
      });
      
      JButton downButton = new JButton();
      downButton.setIcon(SwingResourceManager.getIcon(MMStudio.class, "icons/arrow_down.png"));
      downButton.setText("");
      downButton.setToolTipText("Move slice and piezo down together");
      downButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            stepPiezoAndGalvo(-1.);
         }
      });
      
      calibrationPanel.add(new JLabel("Piezo/Slice Calibration"), "span 5, center");
      calibrationPanel.add(new JSeparator(SwingConstants.VERTICAL), "span 1 3, growy, shrinkx, center");
      calibrationPanel.add(new JLabel("Step"), "wrap");
      
      calibrationPanel.add(new JLabel("Slope: "));
      calibrationPanel.add(rateField_, "span 2, right");
      // TODO make calibration be in degrees instead of um
      // calibrationPanel.add(new JLabel("\u00B0/\u00B5m"));
      calibrationPanel.add(new JLabel("\u00B5m/\u00B0"));
      tmp_but = new JButton("2-point");
      tmp_but.setMargin(new Insets(4,8,4,8));
      tmp_but.setToolTipText("Computes piezo vs. slice slope and offset from start and end positions");
      tmp_but.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            updateCalibrationSlopeAndOffset();
         }
      });
      tmp_but.setBackground(Color.green);
      calibrationPanel.add(tmp_but);

      calibrationPanel.add(upButton, "wrap");
      
      calibrationPanel.add(new JLabel("Offset: "));
      calibrationPanel.add(offsetField_, "span 2, right");
      // calibrationPanel.add(new JLabel("\u00B0"));
      calibrationPanel.add(new JLabel("\u00B5m"));
      tmp_but = new JButton("Update");
      tmp_but.setMargin(new Insets(4,8,4,8));
      tmp_but.setToolTipText("Adjusts piezo vs. slice offset from current position");
      tmp_but.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            updateCalibrationOffset();
         }
      });
      tmp_but.setBackground(Color.green);
      calibrationPanel.add(tmp_but);
      
      calibrationPanel.add(downButton, "wrap");
      
      calibrationPanel.add(new JLabel("Step size: "), "span 2, left");
      calibrationPanel.add(piezoDeltaField_);
      calibrationPanel.add(new JLabel("\u00B5m"));
      
      tmp_but = new JButton("Run Autofocus");
      tmp_but.setMargin(new Insets(4,8,4,8));
      tmp_but.setToolTipText("Autofocus at current piezo position");
      tmp_but.setBackground(Color.green);
      tmp_but.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            autofocus_.runFocus(setupPanel, side_, true,
                    ASIdiSPIM.getFrame().getAcquisitionPanel().getSliceTiming(),
                    true, true);
         }
      });
      calibrationPanel.add(tmp_but, "center, span 3");
      
      // start 2-point calibration frame
      // this frame is separate from main plugin window
      
      slopeCalibrationFrame_ = new MMFrame();
      slopeCalibrationFrame_.setTitle("Slope and Offset Calibration");
      slopeCalibrationFrame_.loadPosition(100, 100);
      
      JPanel slopeCalibrationPanel = new JPanel(new MigLayout(
            "",
            "[center]8[center]8[center]8[center]8[center]",
            "[]8[]"));
      
      // TODO improve interface with multi-page UI and forward/back buttons
      // e.g. \mmstudio\src\org\micromanager\conf2\ConfiguratorDlg2.java
      
      slopeCalibrationPanel.add(new JLabel("Calibration Start Position"), "span 3, center");
      slopeCalibrationPanel.add(new JLabel("Calibration End Position"), "span 3, center, wrap");

      slopeCalibrationPanel.add(sheetStartPositionLabel_);
      
      // Go to start button
      tmp_but = new JButton("Go to");
      tmp_but.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            try {
               positions_.setPosition(micromirrorDeviceKey_, 
                       Directions.Y, sliceStartPos_, true);
               positions_.setPosition(piezoImagingDeviceKey_,
                     imagingPiezoStartPos_, true);       
            } catch (Exception ex) {
               MyDialogUtils.showError(ex);
            }
         }
      });
      slopeCalibrationPanel.add(tmp_but, "");   
      slopeCalibrationPanel.add(new JSeparator(SwingConstants.VERTICAL), "spany 2, growy");
     
      slopeCalibrationPanel.add(sheetStopPositionLabel_);

      // go to end button
      tmp_but = new JButton("Go to");
      tmp_but.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            try {
               positions_.setPosition(micromirrorDeviceKey_, 
                       Directions.Y, sliceStopPos_, true);
               positions_.setPosition(piezoImagingDeviceKey_, 
                       imagingPiezoStopPos_, true);
            } catch (Exception ex) {
               MyDialogUtils.showError(ex);
            }
         }
      });
      slopeCalibrationPanel.add(tmp_but, "wrap");
      
      slopeCalibrationPanel.add(imagingPiezoStartPositionLabel_);
      
      tmp_but = new JButton("Set");
      tmp_but.setToolTipText("Saves calibration start position for imaging piezo and scanner slice (should be focused)");
      tmp_but.setBackground(Color.red);
      tmp_but.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            try {
               // bypass cached positions in positions_ in case they aren't current
               sliceStartPos_ = positions_.getUpdatedPosition(micromirrorDeviceKey_,
                     Directions.Y);
               sheetStartPositionLabel_.setFloat((float)sliceStartPos_);
               imagingPiezoStartPos_ = positions_.getUpdatedPosition(piezoImagingDeviceKey_); 
               imagingPiezoStartPositionLabel_.setFloat((float)imagingPiezoStartPos_);
            } catch (Exception ex) {
               MyDialogUtils.showError(ex);
            }
         }
      });
      slopeCalibrationPanel.add(tmp_but);
      
      slopeCalibrationPanel.add(imagingPiezoStopPositionLabel_);
      
      tmp_but = new JButton("Set");
      tmp_but.setToolTipText("Saves calibration end position for imaging piezo and scanner slice (should be focused)");
      tmp_but.setBackground(Color.red);
      tmp_but.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            try {
               // bypass cached positions in positions_ in case they aren't current
               sliceStopPos_ = positions_.getUpdatedPosition(micromirrorDeviceKey_,
                     Directions.Y);
               sheetStopPositionLabel_.setFloat((float)sliceStopPos_);
               imagingPiezoStopPos_ = positions_.getUpdatedPosition(piezoImagingDeviceKey_);
               imagingPiezoStopPositionLabel_.setFloat((float)imagingPiezoStopPos_);
            } catch (Exception ex) {
               MyDialogUtils.showError(ex);
            }
         }
      });
      slopeCalibrationPanel.add(tmp_but, "wrap");
      
      slopeCalibrationPanel.add(new JSeparator(SwingConstants.HORIZONTAL), "span 5, growx, shrinky, wrap");
      
      tmp_but = new JButton("Use these!");
      tmp_but.setBackground(Color.green);
      tmp_but.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            try {
               double rate = (imagingPiezoStopPos_ - imagingPiezoStartPos_)/(sliceStopPos_ - sliceStartPos_);
               rateField_.setValue((Double)rate);
               double offset = (imagingPiezoStopPos_ + imagingPiezoStartPos_) / 2 - 
                       (rate * ( (sliceStopPos_ + sliceStartPos_) / 2) );
               offsetField_.setValue((Double) offset);
            } catch (Exception ex) {
               MyDialogUtils.showError(ex);
            }
            slopeCalibrationFrame_.setVisible(false);   
         }
      });
      slopeCalibrationPanel.add(tmp_but, "span 5, split 3");
      
      tmp_but = new JButton("Run Autofocus");
      tmp_but.setToolTipText("Autofocus at current piezo position");
      tmp_but.setBackground(Color.green);
      tmp_but.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            autofocus_.runFocus(setupPanel, side_, true,
                    ASIdiSPIM.getFrame().getAcquisitionPanel().getSliceTiming(),
                    true, true);
         }
      });
      slopeCalibrationPanel.add(tmp_but);
      
      tmp_but = new JButton("Cancel");
      tmp_but.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            slopeCalibrationFrame_.setVisible(false);   
         }
      });
      slopeCalibrationPanel.add(tmp_but, "wrap");
      
      slopeCalibrationFrame_.add(slopeCalibrationPanel);
      slopeCalibrationFrame_.pack();
      slopeCalibrationFrame_.setResizable(false);   
      
      final int positionWidth = 50;
      final int labelWidth = 80;
      
      JPanel slicePanel = new JPanel(new MigLayout(
            "",
            "[" + labelWidth + "px!,right]8[" + positionWidth + "px!,center]8[center]8[center]",
            "[]8[]"));
      
      JLabel tmp_lbl = new JLabel("Imaging center: ", JLabel.RIGHT);
      tmp_lbl.setMaximumSize(new Dimension(labelWidth, 20));
      tmp_lbl.setMinimumSize(new Dimension(labelWidth, 20));
      slicePanel.add(tmp_lbl);
      imagingCenterPosLabel_ = new StoredFloatLabel(panelName_, 
            Properties.Keys.PLUGIN_PIEZO_CENTER_POS.toString(), 0,
            prefs_, " \u00B5" + "m");
      imagingCenterPosLabel_.setMaximumSize(new Dimension(positionWidth, 20));
      imagingCenterPosLabel_.setMinimumSize(new Dimension(positionWidth, 20));
      slicePanel.add(imagingCenterPosLabel_);
      
      // initialize the center position variable
      imagingCenterPos_ = prefs_.getFloat(
            MyStrings.PanelNames.SETUP.toString() + side_.toString(), 
            Properties.Keys.PLUGIN_PIEZO_CENTER_POS, 0);
      
      
      tmp_but = new JButton("Go");
      tmp_but.setToolTipText("Moves piezo to specified center and also slice");
      tmp_but.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            centerPiezoAndGalvo();
         }
      } );
      slicePanel.add(tmp_but);
      
      tmp_but = new JButton("Set");
      tmp_but.setToolTipText("Sets piezo center position for acquisition");
      tmp_but.setBackground(Color.red);
      tmp_but.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            try {
               imagingCenterPos_ = positions_.getUpdatedPosition(piezoImagingDeviceKey_); 
               imagingCenterPosLabel_.setFloat((float)imagingCenterPos_);
            } catch (Exception ex) {
               MyDialogUtils.showError(ex);
            }
         }
      });
      slicePanel.add(tmp_but, "wrap");
      
      slicePanel.add(new JSeparator(SwingConstants.HORIZONTAL), "span 5, growx, shrinky, wrap");
      
      slicePanel.add(new JLabel("Slice position:"));
      sheetPositionLabel_ = new JLabel("");
      slicePanel.add(sheetPositionLabel_);
      slicePanel.add(pu.makeSetPositionField(micromirrorDeviceKey_, Directions.Y, positions_));
      
      tmp_but = new JButton("Go to 0");
      tmp_but.setMargin(new Insets(4,4,4,4));
      tmp_but.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            positions_.setPosition(micromirrorDeviceKey_, Directions.Y, 0.0, true);
         }
      } );
      slicePanel.add(tmp_but, "wrap");
      
      slicePanel.add(new JLabel("Imaging piezo:"));
      imagingPiezoPositionLabel_ = new JLabel("");
      slicePanel.add(imagingPiezoPositionLabel_);
      slicePanel.add(pu.makeSetPositionField(piezoImagingDeviceKey_, 
              Directions.NONE, positions_));
      tmp_but = new JButton("Go to 0");
      tmp_but.setMargin(new Insets(4,4,4,4));      
      tmp_but.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            positions_.setPosition(piezoImagingDeviceKey_, 0.0, true);
         }
      } );
      slicePanel.add(tmp_but);

      
      // Create sheet controls
      JPanel sheetPanel = new JPanel(new MigLayout(
            "",
            "[" + labelWidth + "px!,right]8[" + positionWidth + "px!,center]8[center]8[center]",
            "[]8[]8[]"));
      
      tmp_lbl = new JLabel("Illum. piezo:", JLabel.RIGHT);
      tmp_lbl.setMaximumSize(new Dimension(labelWidth, 20));
      tmp_lbl.setMinimumSize(new Dimension(labelWidth, 20));
      sheetPanel.add(tmp_lbl, "center");
      illuminationPiezoPositionLabel_ = new JLabel("");
      illuminationPiezoPositionLabel_.setMaximumSize(new Dimension(positionWidth, 20));
      illuminationPiezoPositionLabel_.setMinimumSize(new Dimension(positionWidth, 20));
      sheetPanel.add(illuminationPiezoPositionLabel_);
      sheetPanel.add(pu.makeSetPositionField(piezoIlluminationDeviceKey_,
            Directions.NONE, positions_));

      tmp_but = new JButton("Set home");
      tmp_but.setMargin(new Insets(4,4,4,4));
      tmp_but.setToolTipText("During SPIM, illumination piezo is moved to home position");
      tmp_but.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            if (devices_.isValidMMDevice(piezoIlluminationDeviceKey_)) {
               props_.setPropValue(piezoIlluminationDeviceKey_,
                     Properties.Keys.SET_HOME_HERE, Properties.Values.DO_IT);
            }
         }
      });
      sheetPanel.add(tmp_but);

      tmp_but = new JButton("Go home");
      tmp_but.setMargin(new Insets(4,4,4,4));
      tmp_but.setToolTipText("During SPIM, illumination piezo is moved to home position");
      tmp_but.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            if (devices_.isValidMMDevice(piezoIlluminationDeviceKey_)) {
               props_.setPropValue(piezoIlluminationDeviceKey_,
                     Properties.Keys.MOVE_TO_HOME, Properties.Values.DO_IT);
            }
         }
      });
      sheetPanel.add(tmp_but);

      illumPiezoHomeEnable_ = pu.makeCheckBox("Go home on tab activate",
            Properties.Keys.PREFS_ENABLE_ILLUM_PIEZO_HOME, panelName_, false); 
      sheetPanel.add(illumPiezoHomeEnable_, "span 3, wrap");


      sheetPanel.add(new JLabel("Sheet width:"));
      sheetPanel.add(new JLabel(""), "span 2");   // TODO update this label with current value and/or allow user to directly enter value
      sheetPanel.add(makeIncrementButton(micromirrorDeviceKey_,
            Properties.Keys.SA_AMPLITUDE_X_DEG, "-", (float)-0.01),
            "split 2");
      sheetPanel.add(makeIncrementButton(micromirrorDeviceKey_,
            Properties.Keys.SA_AMPLITUDE_X_DEG, "+", (float)0.01));
      JSlider tmp_sl = pu.makeSlider(0, // 0 is min amplitude
              props_.getPropValueFloat(micromirrorDeviceKey_,Properties.Keys.MAX_DEFLECTION_X) - 
              props_.getPropValueFloat(micromirrorDeviceKey_, Properties.Keys.MIN_DEFLECTION_X), // compute max amplitude
              1000, // the scale factor between internal integer representation and float representation
              micromirrorDeviceKey_, Properties.Keys.SA_AMPLITUDE_X_DEG);
      sheetPanel.add(tmp_sl, "span 5, growx, center, wrap");


      sheetPanel.add(new JLabel("Sheet offset:"));
      sheetPanel.add(new JLabel(""), "span 2");   // TODO update this label with current value and/or allow user to directly enter value
      sheetPanel.add(makeIncrementButton(micromirrorDeviceKey_,
            Properties.Keys.SA_OFFSET_X_DEG, "-", (float)-0.01),
            "split 2");
      sheetPanel.add(makeIncrementButton(micromirrorDeviceKey_,
            Properties.Keys.SA_OFFSET_X_DEG, "+", (float)0.01));
      tmp_but = new JButton("Center");
      tmp_but.setMargin(new Insets(4,8,4,8));
      tmp_but.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            props_.setPropValue(micromirrorDeviceKey_, Properties.Keys.SA_OFFSET_X_DEG, 0f, true);
            props_.callListeners();  // update plot
         }
      });
      sheetPanel.add(tmp_but);
      tmp_sl = pu.makeSlider(
              props.getPropValueFloat(micromirrorDeviceKey_, Properties.Keys.MIN_DEFLECTION_X)/4, // min value
              props.getPropValueFloat(micromirrorDeviceKey_, Properties.Keys.MAX_DEFLECTION_X)/4, // max value
              1000, // the scale factor between internal integer representation and float representation
              micromirrorDeviceKey_, Properties.Keys.SA_OFFSET_X_DEG);
      sheetPanel.add(tmp_sl, "span 4, growx, center, wrap");

      
      // Create larger panel with slice, sheet, and calibration panels
      JPanel superPanel = new JPanel(new MigLayout(
            "",
            "[]8[]",
            "[]8[]"));
      superPanel.setBorder(BorderFactory.createLineBorder(ASIdiSPIM.borderColor));
      
      superPanel.add(slicePanel);
      superPanel.add(new JSeparator(SwingConstants.VERTICAL), "growy, shrinkx, center");
      superPanel.add(calibrationPanel, "wrap");
      superPanel.add(new JSeparator(SwingConstants.HORIZONTAL), "span 3, growx, shrinky, wrap");
      superPanel.add(sheetPanel, "span 3");

      // Layout of the SetupPanel
      joystickPanel_ = new JoystickSubPanel(joystick_, devices_, panelName_, side_, prefs_);
      add(joystickPanel_, "center");

      add(superPanel, "center, aligny top, span 1 3, wrap");

      beamPanel_ = new BeamSubPanel(gui_, devices_, panelName_, side_, prefs_, props_);
      add(beamPanel_, "center, wrap");

      cameraPanel_ = new CameraSubPanel(gui_, cameras_, devices_, panelName_, side_, prefs_, true);
      add(cameraPanel_, "center");

   }// end of SetupPanel constructor

   /**
    * Performs "1-point" calibration updating the offset
    * (but not the slope) based on current piezo/galvo positions.
    */
   private void updateCalibrationOffset() {
      try {
         double rate = (Double) rateField_.getValue();
         // bypass cached positions in positions_ in case they aren't current
         double currentScanner = positions_.getUpdatedPosition(micromirrorDeviceKey_,
               Directions.Y);
         double currentPiezo = positions_.getUpdatedPosition(piezoImagingDeviceKey_);
         double newOffset = currentPiezo - rate * currentScanner;
         offsetField_.setValue((Double) newOffset);
      } catch (Exception ex) {
         MyDialogUtils.showError(ex);
      }
   }
   
   /**
    * Give autofocus and acquisition the opportunity to update the offset.
    * Only happens if the focus is deemed a "success" by autofocus
    * (currently means R^2 value is sufficiently high and best focus
    * position is in the middle 80% of the range sampled)
    * @param newValue - new value for the piezo/slice calibration offset
    */
   public void updateCalibrationOffset(AutofocusUtils.FocusResult score) {
      if (!score.getFocusSuccess()) {
         ReportingUtils.logMessage("autofocus offset for side " + side_ + " not updated because focus not successful");
         return;
      }
      double rate = (Double) rateField_.getValue();
      double newOffset = score.getPiezoPosition() - rate * score.getGalvoPosition();         
      offsetField_.setValue((Double) newOffset);
      ReportingUtils.logMessage("autofocus updated offset for side " + side_ + "; new value is " + newOffset);
   }

   
   

   /**
    * Performs "2-point" calibration updating the offset and slope.
    * Pops up a sub-window.
    */
   private void updateCalibrationSlopeAndOffset() {
      slopeCalibrationFrame_.setVisible(true);
   }
   
   /**
    * Moves piezo and slice together. Specify the factor by which the step 
    * size is multiplied by (e.g. +/- 1).
    * @param factor
    */
   private void stepPiezoAndGalvo(double factor) {
      try {
         double piezoPos = positions_.getUpdatedPosition(piezoImagingDeviceKey_); 
         piezoPos += (factor * (Double) piezoDeltaField_.getValue());
         positions_.setPosition(piezoImagingDeviceKey_, piezoPos, true);
         double galvoPos = computeGalvoFromPiezo(piezoPos);
         positions_.setPosition(micromirrorDeviceKey_,
               Directions.Y, galvoPos, true);
      } catch (Exception ex) {
         MyDialogUtils.showError(ex);
      }
   }
   
   /**
    * Uses computed offset/rate to get galvo position for
    *  specified piezo position
    * @param pizeoPos
    * @return
    */
   private double computeGalvoFromPiezo(double piezoPos) {
      double offset = (Double) offsetField_.getValue();
      double rate = (Double) rateField_.getValue();
      return ((piezoPos - offset)/rate);
   }
   
  /**
   * Centers the piezo and micro-mirror.  Doesn't do anything if the devices
   * aren't assigned to prevent spurious exceptions.
   * @throws Exception 
   */
   private void centerPiezoAndGalvo() {
      boolean success = positions_.setPosition(piezoImagingDeviceKey_, imagingCenterPos_, true);
      if (success) {
         positions_.setPosition(micromirrorDeviceKey_, Directions.Y,
            computeGalvoFromPiezo(imagingCenterPos_));
      }
   }
   
   private JButton makeIncrementButton(Devices.Keys devKey, Properties.Keys propKey, 
         String label, float incrementAmount) {
      class incrementButtonActionListener implements ActionListener {
         private final Devices.Keys devKey_;
         private final Properties.Keys propKey_;
         private final float incrementAmount_;
         
         @Override
         public void actionPerformed(ActionEvent e) {
            try {
               props_.setPropValue(devKey_, propKey_, 
                     incrementAmount_ + props_.getPropValueFloat(devKey_, propKey_), true);
               props_.callListeners();  // update GUI
            } catch (Exception ex) {
               MyDialogUtils.showError(ex);
            }
         }

         private incrementButtonActionListener(Devices.Keys devKey, Properties.Keys propKey, 
               float incrementAmount) {
            devKey_ = devKey;
            propKey_ = propKey;
            incrementAmount_ = incrementAmount;
         }
      }
      
      JButton jb = new JButton(label);
      jb.setMargin(new Insets(4,8,4,8));
      ActionListener l = new incrementButtonActionListener(devKey, propKey, incrementAmount);
      jb.addActionListener(l);
      return jb;
   }

   @Override
   public void saveSettings() {
      beamPanel_.saveSettings();
      // all other prefs are updated on button press instead of here
   }

   /**
    * Called whenever position updater has refreshed positions
    */
   @Override
   public void updateStagePositions() {
      imagingPiezoPositionLabel_.setText(
            positions_.getPositionString(piezoImagingDeviceKey_));
      illuminationPiezoPositionLabel_.setText(
            positions_.getPositionString(piezoIlluminationDeviceKey_));
      sheetPositionLabel_.setText(
            positions_.getPositionString(micromirrorDeviceKey_, Directions.Y));
   }
   
   /**
    * Called whenever position updater stops
    */
   @Override
   public final void stoppedStagePositions() {
      imagingPiezoPositionLabel_.setText("");
      illuminationPiezoPositionLabel_.setText("");
      sheetPositionLabel_.setText("");
   }

   /**
    * required by LiveModeListener interface; just pass call along to camera
    * panel
    * @param enable - signals whether or not live mode is enabled
    */
   @Override
   public void liveModeEnabled(boolean enable) {
      cameraPanel_.liveModeEnabled(enable);
   }

   /**
    * Gets called when this tab gets focus. Uses the ActionListeners of the UI
    * components
    */
   @Override
   public void gotSelected() {
      posUpdater_.pauseUpdates(true);
      joystickPanel_.gotSelected();
      cameraPanel_.gotSelected();
      beamPanel_.gotSelected();
      props_.callListeners();

      // moves illumination piezo to home
      if (illumPiezoHomeEnable_.isSelected() && 
            devices_.isValidMMDevice(piezoIlluminationDeviceKey_)) {
         props_.setPropValue(piezoIlluminationDeviceKey_,
               Properties.Keys.MOVE_TO_HOME, Properties.Values.DO_IT);
      }
      
      // set scan waveform to be triangle
      // SPIM use can change, but for alignment avoid sharp edges
      props_.setPropValue(micromirrorDeviceKey_, Properties.Keys.SA_PATTERN_X, 
              Properties.Values.SAM_TRIANGLE, true);
      
      // move piezo and scanner to "center" position
      centerPiezoAndGalvo();
      
      posUpdater_.pauseUpdates(false);
   }
   
   @Override
   public void gotDeSelected() {
      joystickPanel_.gotDeSelected();
      slopeCalibrationFrame_.savePosition();
      slopeCalibrationFrame_.setVisible(false);
   }
   
   @Override
   public void refreshSelected() {  // called after autofocus
      cameraPanel_.gotSelected();
      beamPanel_.gotSelected();
   }
   
   @Override
   public void windowClosing() {
      slopeCalibrationFrame_.savePosition();
      slopeCalibrationFrame_.dispose();
   }
}
