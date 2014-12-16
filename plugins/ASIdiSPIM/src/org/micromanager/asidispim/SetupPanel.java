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
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Point2D;

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
import org.micromanager.asidispim.Utils.StoredFloatLabel;

import mmcorej.CMMCore;

import javax.swing.*;

import net.miginfocom.swing.MigLayout;

import org.micromanager.MMStudio;
import org.micromanager.api.ScriptInterface;
import org.micromanager.internalinterfaces.LiveModeListener;

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
   private final Positions positions_;
   private final Cameras cameras_;
   private final Prefs prefs_;
   private final ScriptInterface gui_;
   private final CMMCore core_;
   private final JoystickSubPanel joystickPanel_;
   private final CameraSubPanel cameraPanel_;
   private final BeamSubPanel beamPanel_;
   // used to store the start/stop positions of the single-axis moves for imaging piezo and micromirror sheet move axis
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
   private final StoredFloatLabel sheetStartPositionLabel_;
   private final StoredFloatLabel sheetStopPositionLabel_;
   private final StoredFloatLabel imagingPiezoStartPositionLabel_;
   private final StoredFloatLabel imagingPiezoStopPositionLabel_;

   public SetupPanel(ScriptInterface gui, Devices devices, Properties props, 
           Joystick joystick, Devices.Sides side, Positions positions, 
           Cameras cameras, Prefs prefs) {
      super(MyStrings.PanelNames.SETUP.toString() + side.toString(),
              new MigLayout(
              "",
              "[center]8[center]",
              "[]16[]16[]"));
      
      devices_ = devices;
      props_ = props;
      joystick_ = joystick;
      positions_ = positions;
      cameras_ = cameras;
      prefs_ = prefs;
      gui_ = gui;
      core_ = gui_.getMMCore();
      PanelUtils pu = new PanelUtils(prefs_, props_, devices);

      piezoImagingDeviceKey_ = Devices.getSideSpecificKey(Devices.Keys.PIEZOA, side);
      piezoIlluminationDeviceKey_ = Devices.getSideSpecificKey(Devices.Keys.PIEZOA, Devices.getOppositeSide(side));
      micromirrorDeviceKey_ = Devices.getSideSpecificKey(Devices.Keys.GALVOA, side);

      sheetStartPositionLabel_ = new StoredFloatLabel(panelName_, 
              Properties.Keys.PLUGIN_SHEET_START_POS.toString(), -1,
              prefs_, " \u00B0");
      sliceStartPos_ = sheetStartPositionLabel_.getFloat();
      sheetStopPositionLabel_ = new StoredFloatLabel(panelName_, 
              Properties.Keys.PLUGIN_SHEET_END_POS.toString(), 1,
              prefs_, " \u00B0");
      sliceStopPos_ = sheetStopPositionLabel_.getFloat();
      imagingPiezoStartPositionLabel_ = new StoredFloatLabel(panelName_, 
              Properties.Keys.PLUGIN_PIEZO_START_POS.toString(), -80,
              prefs_, " \u00B5" + "m");
      imagingPiezoStartPos_ = imagingPiezoStartPositionLabel_.getFloat();
      imagingPiezoStopPositionLabel_ = new StoredFloatLabel(panelName_, 
              Properties.Keys.PLUGIN_PIEZO_END_POS.toString(), 80,
              prefs_, " \u00B5" + "m");
      imagingPiezoStopPos_ = imagingPiezoStopPositionLabel_.getFloat();
      
      // Create sheet Panel with sheet and piezo controls
      JPanel sheetPanel = new JPanel(new MigLayout(
            "",
            "[right]8[align center]8[right]8[]8[center]8[center]8[center]8[center]8[center]",
            "[]8[]8[]8[]4[]8[]8[]8[]8[]8[]"));
      sheetPanel.setBorder(BorderFactory.createLineBorder(ASIdiSPIM.borderColor));
      
      offsetField_ = pu.makeFloatEntryField(panelName_, 
              Properties.Keys.PLUGIN_OFFSET_PIEZO_SHEET.toString(), 0, 6);  

      rateField_ = pu.makeFloatEntryField(panelName_, 
              Properties.Keys.PLUGIN_RATE_PIEZO_SHEET.toString(), 80, 6);

      sheetPanel.add(new JLabel("Imaging center: "));
      imagingCenterPosLabel_ = new StoredFloatLabel(panelName_, 
            Properties.Keys.PLUGIN_PIEZO_CENTER_POS.toString(), 0,
            prefs_, " \u00B5" + "m");
      sheetPanel.add(imagingCenterPosLabel_);
      
      JButton goToCenterButton = new JButton("Go to");
      goToCenterButton.setToolTipText("Moves piezo to specified center and also slice");
      goToCenterButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            try {
               imagingCenterPos_ = imagingCenterPosLabel_.getFloat();
               core_.setPosition(devices_.getMMDeviceException(piezoImagingDeviceKey_), 
                       imagingCenterPos_);
               double sliceCenterPos = computeGalvoFromPiezo(imagingCenterPos_);
               core_.setGalvoPosition(
                     devices_.getMMDeviceException(micromirrorDeviceKey_),
                     0, sliceCenterPos);
            } catch (Exception ex) {
               MyDialogUtils.showError(ex);
            }
         }
      } );
      sheetPanel.add(goToCenterButton, "span 2, center");
      
      JButton setCenterButton = new JButton("Set");
      goToCenterButton.setToolTipText("Sets piezo center position for acquisition");
      setCenterButton.setBackground(Color.red);
      setCenterButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            try {
               imagingCenterPos_ = core_.getPosition(
                    devices_.getMMDeviceException(piezoImagingDeviceKey_));
               imagingCenterPosLabel_.setFloat((float)imagingCenterPos_);
            } catch (Exception ex) {
               MyDialogUtils.showError(ex);
            }
         }
      });
      sheetPanel.add(setCenterButton);
      
      piezoDeltaField_ = pu.makeFloatEntryField(panelName_, 
            Properties.Keys.PLUGIN_PIEZO_SHEET_INCREMENT.toString(), 10, 2);
      piezoDeltaField_.setToolTipText("Piezo increment used by up/down arrow buttons");
      
      sheetPanel.add(new JLabel("\u0394"+"="), "split 2, right");
      sheetPanel.add(piezoDeltaField_, "right");
      sheetPanel.add(new JLabel("\u00B5"+"m"), "left");
      
      JButton upButton = new JButton();
      upButton.setIcon(SwingResourceManager.getIcon(MMStudio.class, "icons/arrow_up.png"));
      upButton.setText("");
      upButton.setToolTipText("Move slice and piezo together");
      upButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            try {
               double piezoPos = core_.getPosition(
                     devices_.getMMDeviceException(piezoImagingDeviceKey_));
               piezoPos += (Double) piezoDeltaField_.getValue();
               positions_.setPosition(piezoImagingDeviceKey_, 
                     Joystick.Directions.NONE, piezoPos);
               double galvoPos = computeGalvoFromPiezo(piezoPos);
               positions_.setPosition(micromirrorDeviceKey_, 
                     Joystick.Directions.Y, galvoPos);
            } catch (Exception ex) {
               MyDialogUtils.showError(ex);
            }
            }
      });
      
      JButton downButton = new JButton();
      downButton.setIcon(SwingResourceManager.getIcon(MMStudio.class, "icons/arrow_down.png"));
      downButton.setText("");
      downButton.setToolTipText("Move slice and piezo together");
      downButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            try {
               double piezoPos = core_.getPosition(
                     devices_.getMMDeviceException(piezoImagingDeviceKey_));
               piezoPos -= (Double) piezoDeltaField_.getValue();
               positions_.setPosition(piezoImagingDeviceKey_, 
                     Joystick.Directions.NONE, piezoPos);
               double galvoPos = computeGalvoFromPiezo(piezoPos);
               positions_.setPosition(micromirrorDeviceKey_, 
                     Joystick.Directions.Y, galvoPos);
            } catch (Exception ex) {
               MyDialogUtils.showError(ex);
            }
         }
      });
      
      sheetPanel.add(upButton, "");
      sheetPanel.add(downButton, "wrap");
           
      sheetPanel.add(new JLabel("Piezo ="));
      sheetPanel.add(offsetField_);
      sheetPanel.add(new JLabel("\u00B5"+"m" + " + Slice *"), "span 2");
      sheetPanel.add(rateField_);
      
      JButton tmp_but = new JButton("Compute piezo vs. slice calibration");
      tmp_but.setToolTipText("Computes piezo vs. slice position from start and end positions");
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
         }
      });
      sheetPanel.add(tmp_but, "span 4, center, wrap");
      
      sheetPanel.add(new JSeparator(SwingConstants.HORIZONTAL), "span 9, growx, wrap");
      
      sheetPanel.add(new JLabel("Calibration Start Position"), "skip 3, span 3, center");
      sheetPanel.add(new JLabel("Calibration End Position"), "span 3, center, wrap");
      
      sheetPanel.add(new JLabel("Slice position:"));
      sheetPositionLabel_ = new JLabel("");
      sheetPanel.add(sheetPositionLabel_);
      sheetPanel.add(pu.makeSetPositionField(micromirrorDeviceKey_, Joystick.Directions.Y, positions_));
      
      sheetPanel.add(new JSeparator(SwingConstants.VERTICAL), "spany 2, growy, shrinkx, center");
      sheetPanel.add(sheetStartPositionLabel_);

      // Go to start button
      tmp_but = new JButton("Go to");
      tmp_but.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            try {
               positions_.setPosition(micromirrorDeviceKey_, 
                       Joystick.Directions.Y, sliceStartPos_);
               positions_.setPosition(piezoImagingDeviceKey_, 
                       Joystick.Directions.NONE, imagingPiezoStartPos_);       
            } catch (Exception ex) {
               MyDialogUtils.showError(ex);
            }
         }
      });
      sheetPanel.add(tmp_but, "");   
      sheetPanel.add(new JSeparator(SwingConstants.VERTICAL), "spany 2, growy");
     
      sheetPanel.add(sheetStopPositionLabel_);

      // go to end button
      tmp_but = new JButton("Go to");
      tmp_but.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            try {
               positions_.setPosition(micromirrorDeviceKey_, 
                       Joystick.Directions.Y, sliceStopPos_);
               positions_.setPosition(piezoImagingDeviceKey_, 
                       Joystick.Directions.NONE, imagingPiezoStopPos_);
            } catch (Exception ex) {
               MyDialogUtils.showError(ex);
            }
         }
      });
      sheetPanel.add(tmp_but, "wrap");
      
     
      sheetPanel.add(new JLabel("Imaging piezo:"));
      imagingPiezoPositionLabel_ = new JLabel("");
      sheetPanel.add(imagingPiezoPositionLabel_);
      sheetPanel.add(pu.makeSetPositionField(piezoImagingDeviceKey_, 
              Joystick.Directions.NONE, positions_));

      sheetPanel.add(imagingPiezoStartPositionLabel_);
            
      tmp_but = new JButton("Set");
      tmp_but.setToolTipText("Saves calibration start position for imaging piezo and scanner slice (should be focused)");
      tmp_but.setBackground(Color.red);
      tmp_but.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            try {
               // bypass cached positions in positions_ in case they aren't current
               Point2D.Double pt = core_.getGalvoPosition(
                       devices_.getMMDeviceException(micromirrorDeviceKey_));
               sliceStartPos_ = pt.y;
               sheetStartPositionLabel_.setFloat((float)sliceStartPos_);
               imagingPiezoStartPos_ = core_.getPosition(
                       devices_.getMMDeviceException(piezoImagingDeviceKey_));
               imagingPiezoStartPositionLabel_.setFloat((float)imagingPiezoStartPos_);
            } catch (Exception ex) {
               MyDialogUtils.showError(ex);
            }
         }
      });
      sheetPanel.add(tmp_but);

      sheetPanel.add(imagingPiezoStopPositionLabel_);
      
      tmp_but = new JButton("Set");
      tmp_but.setToolTipText("Saves calibration end position for imaging piezo and scanner slice (should be focused)");
      tmp_but.setBackground(Color.red);
      tmp_but.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            try {
               // bypass cached positions in positions_ in case they aren't current
               Point2D.Double pt = core_.getGalvoPosition(
                       devices_.getMMDeviceException(micromirrorDeviceKey_));
               sliceStopPos_ = pt.y;
               sheetStopPositionLabel_.setFloat((float)sliceStopPos_);
               imagingPiezoStopPos_ = core_.getPosition(
                       devices_.getMMDeviceException(piezoImagingDeviceKey_));
               imagingPiezoStopPositionLabel_.setFloat((float)imagingPiezoStopPos_);
            } catch (Exception ex) {
               MyDialogUtils.showError(ex);
            }
         }
      });
      sheetPanel.add(tmp_but, "wrap");

      sheetPanel.add(new JSeparator(SwingConstants.HORIZONTAL), "span 9, growx, wrap");


      sheetPanel.add(new JLabel("Illumination piezo:"));
      illuminationPiezoPositionLabel_ = new JLabel("");
      sheetPanel.add(illuminationPiezoPositionLabel_);
      sheetPanel.add(pu.makeSetPositionField(piezoIlluminationDeviceKey_,
            Joystick.Directions.NONE, positions_));

      tmp_but = new JButton("Set home");
      tmp_but.setMargin(new Insets(4,8,4,8));
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
      sheetPanel.add(tmp_but, "skip 1");

      tmp_but = new JButton("Go home");
      tmp_but.setMargin(new Insets(4,8,4,8));
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
            Properties.Keys.PLUGIN_ENABLE_ILLUM_PIEZO_HOME, panelName_, false); 
      sheetPanel.add(illumPiezoHomeEnable_, "span 3, wrap");


      sheetPanel.add(new JLabel("Sheet width:"));
      sheetPanel.add(new JLabel(""), "span 2");   // TODO update this label with current value and/or allow user to directly enter value
      sheetPanel.add(makeIncrementButton(micromirrorDeviceKey_,
            Properties.Keys.SA_AMPLITUDE_X_DEG, "-", (float)-0.01),
            "skip 1, split 2");
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
            "skip 1, split 2");
      sheetPanel.add(makeIncrementButton(micromirrorDeviceKey_,
            Properties.Keys.SA_OFFSET_X_DEG, "+", (float)0.01));
      tmp_sl = pu.makeSlider(
              props.getPropValueFloat(micromirrorDeviceKey_, Properties.Keys.MIN_DEFLECTION_X), // min value
              props.getPropValueFloat(micromirrorDeviceKey_, Properties.Keys.MAX_DEFLECTION_X), // max value
              1000, // the scale factor between internal integer representation and float representation
              micromirrorDeviceKey_, Properties.Keys.SA_OFFSET_X_DEG);
      sheetPanel.add(tmp_sl, "span 5, growx, center, wrap");


      // Layout of the SetupPanel
      joystickPanel_ = new JoystickSubPanel(joystick_, devices_, panelName_, side, 
              prefs_);
      add(joystickPanel_, "center");

      add(sheetPanel, "center, aligny top, span 1 3, wrap");

      beamPanel_ = new BeamSubPanel(gui_, devices_, panelName_, side, prefs_, props_);
      add(beamPanel_, "center, wrap");


      cameraPanel_ = new CameraSubPanel(gui_, cameras_, devices_, panelName_, 
              side, prefs_, true);
      add(cameraPanel_, "center");

   }// end of SetupPanel constructor

   
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
            positions_.getPositionString(micromirrorDeviceKey_, Joystick.Directions.Y));
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

   }
}
