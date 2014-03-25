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

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Point2D;

import org.micromanager.asidispim.Data.Cameras;
import org.micromanager.asidispim.Data.Devices;
import org.micromanager.asidispim.Data.Joystick;
import org.micromanager.asidispim.Data.Positions;
import org.micromanager.asidispim.Data.Prefs;
import org.micromanager.asidispim.Data.Properties;
import org.micromanager.asidispim.Utils.ListeningJPanel;
import org.micromanager.asidispim.Utils.PanelUtils;

import mmcorej.CMMCore;

import javax.swing.*;

import net.miginfocom.swing.MigLayout;

import org.micromanager.MMStudioMainFrame;
import org.micromanager.internalinterfaces.LiveModeListener;
import org.micromanager.utils.NumberUtils;
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
   private final Positions positions_;
   private final Cameras cameras_;
   private final Prefs prefs_;
   // private final String panelName_;
   private final CMMCore core_;
   private String port_;  // needed to send serial commands directly
   private final JoystickSubPanel joystickPanel_;
   private final CameraSubPanel cameraPanel_;
   private final BeamSubPanel beamPanel_;
   // used to store the start/stop positions of the single-axis moves for imaging piezo and micromirror sheet move axis
   private double imagingStartPos_;
   private double imagingStopPos_;
   private double sheetStartPos_;
   private double sheetStopPos_;
   private boolean illumPiezoHomeEnable_;
   // device keys, get assigned in constructor based on side
   private Devices.Keys piezoImagingDeviceKey_;
   private Devices.Keys piezoIlluminationDeviceKey_;
   private Devices.Keys micromirrorDeviceKey_;
   private JLabel imagingPiezoPositionLabel_;
   private JLabel illuminationPiezoPositionLabel_;
   private JLabel sheetPositionLabel_;
   private final JLabel sheetStartPosLabel_;
   private final JLabel sheetEndPositionLabel_;
   private final JLabel piezoStartPositionLabel_;
   final JLabel piezoEndPositionLabel_;

   public SetupPanel(Devices devices, Properties props, Joystick joystick, Devices.Sides side,
           Positions positions, Cameras cameras, Prefs prefs) {
      super("Setup Path " + side.toString(),
              new MigLayout(
              "",
              "[center]8[align center]",
              "[]16[]16[]"));
      
      devices_ = devices;
      props_ = props;
      joystick_ = joystick;
      positions_ = positions;
      cameras_ = cameras;
      prefs_ = prefs;
      core_ = MMStudioMainFrame.getInstance().getCore();
      PanelUtils pu = new PanelUtils();

      piezoImagingDeviceKey_ = Devices.getSideSpecificKey(Devices.Keys.PIEZOA, side);
      piezoIlluminationDeviceKey_ = Devices.getSideSpecificKey(Devices.Keys.PIEZOA, Devices.getOppositeSide(side));
      micromirrorDeviceKey_ = Devices.getSideSpecificKey(Devices.Keys.GALVOA, side);

      port_ = null;
      updatePort();

      // These labels will be updated in the updateStartStopPositions function
      sheetStartPosLabel_ = new JLabel("");
      sheetEndPositionLabel_ = new JLabel("");
      piezoStartPositionLabel_ = new JLabel("");
      piezoEndPositionLabel_ = new JLabel("");
      
      updateStartStopPositions();


      // Create sheet Panel with sheet and piezo controls
      MigLayout ml = new MigLayout(
              "",
              "[right]8[align center]8[right]8[60px,center]8[center]8[center]8[center]8[center]",
              "[]-2[]-2[]16[]20[]20[]20[]");
      JPanel sheetPanel = new JPanel(ml);

      sheetPanel.add(new JLabel("Sheet/slice position:"));
      sheetPositionLabel_ = new JLabel("");
      sheetPanel.add(sheetPositionLabel_);
      sheetPanel.add(pu.makeSetPositionField(micromirrorDeviceKey_, Joystick.Directions.Y, positions_));

      sheetPanel.add(sheetStartPosLabel_);

      JButton tmp_but = new JButton("Go start");
      tmp_but.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            try {
               positions_.setPosition(
                       micromirrorDeviceKey_, Joystick.Directions.Y, sheetStartPos_);
            } catch (Exception ex) {
               ReportingUtils.showError(ex);
            }
         }
      });
      sheetPanel.add(tmp_but, "");    
      sheetPanel.add(sheetEndPositionLabel_);

      tmp_but = new JButton("Go end");
      tmp_but.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            try {
               positions_.setPosition(micromirrorDeviceKey_, Joystick.Directions.Y, sheetStopPos_);
            } catch (Exception ex) {
               ReportingUtils.showError(ex);
            }
         }
      });
      sheetPanel.add(tmp_but, "wrap");


      tmp_but = new JButton("Set start");
      tmp_but.setToolTipText("Use with imaging piezo position \"Set start\" when focused");
      tmp_but.setOpaque(true);
      tmp_but.setBackground(Color.red);
      tmp_but.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            try {
               // TODO use positions_ for this instead of core call
               Point2D.Double pt = core_.getGalvoPosition(
                       devices_.getMMDeviceException(micromirrorDeviceKey_));
               sheetStartPos_ = pt.y;
               updateSheetSAParams();
               imagingStartPos_ = core_.getPosition(
                       devices_.getMMDeviceException(piezoImagingDeviceKey_));
               updateImagingSAParams();
               updateStartStopPositions();
            } catch (Exception ex) {
               ReportingUtils.showError(ex);
            }
         }
      });
      sheetPanel.add(tmp_but, "skip 3");


      tmp_but = new JButton("Set end");
      tmp_but.setToolTipText("Use with imaging piezo position \"Set end\" when focused");
      tmp_but.setContentAreaFilled(false);
      tmp_but.setOpaque(true);
      tmp_but.setBackground(Color.red);
      tmp_but.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            try {
               Point2D.Double pt = core_.getGalvoPosition(
                       devices_.getMMDeviceException(micromirrorDeviceKey_));
               sheetStopPos_ = pt.y;
               updateSheetSAParams();
               imagingStopPos_ = core_.getPosition(
                       devices_.getMMDeviceException(piezoImagingDeviceKey_));
              
               updateImagingSAParams();
               updateStartStopPositions();
            } catch (Exception ex) {
               ReportingUtils.showError(ex);
            }
         }
      });
      sheetPanel.add(tmp_but, "skip 1, wrap");

      sheetPanel.add(new JLabel("Imaging piezo:"));
      imagingPiezoPositionLabel_ = new JLabel("");
      sheetPanel.add(imagingPiezoPositionLabel_);
      sheetPanel.add(pu.makeSetPositionField(piezoImagingDeviceKey_, Joystick.Directions.NONE, positions_));


      sheetPanel.add(piezoStartPositionLabel_);

      tmp_but = new JButton("Go start");
      tmp_but.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            try {
               positions_.setPosition(piezoImagingDeviceKey_, Joystick.Directions.NONE, imagingStartPos_);
            } catch (Exception ex) {
               ReportingUtils.showError(ex);
            }
         }
      });
      sheetPanel.add(tmp_but, "");


      sheetPanel.add(piezoEndPositionLabel_);


      tmp_but = new JButton("Go end");
      tmp_but.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            try {
               positions_.setPosition(piezoImagingDeviceKey_, Joystick.Directions.NONE, imagingStopPos_);
            } catch (Exception ex) {
               ReportingUtils.showError(ex);
            }
         }
      });
      sheetPanel.add(tmp_but, "wrap");

      sheetPanel.add(new JLabel("Illumination Piezo:"));
      illuminationPiezoPositionLabel_ = new JLabel("");
      sheetPanel.add(illuminationPiezoPositionLabel_);
      sheetPanel.add(pu.makeSetPositionField(piezoIlluminationDeviceKey_, Joystick.Directions.NONE, positions_));

      tmp_but = new JButton("Set home");
      tmp_but.setToolTipText("During SPIM, illumination piezo is moved to home position");
      tmp_but.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            String letter = "";
            updatePort();
            if (port_ == null) {
               return;
            }
            try {
               letter = props_.getPropValueString(piezoIlluminationDeviceKey_, Properties.Keys.AXIS_LETTER);
               core_.setSerialPortCommand(port_, "HM " + letter + "+", "\r");
            } catch (Exception ex) {
               ReportingUtils.showError("could not execute core function set home here for axis " + letter);
            }
         }
      });
      sheetPanel.add(tmp_but);

      tmp_but = new JButton("Go home");
      tmp_but.setToolTipText("During SPIM, illumination piezo is moved to home position");
      tmp_but.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            String letter = "";
            updatePort();
            if (port_ == null) {
               return;
            }
            try {
               letter = props_.getPropValueString(piezoIlluminationDeviceKey_, Properties.Keys.AXIS_LETTER);
               core_.setSerialPortCommand(port_, "! " + letter, "\r");
            } catch (Exception ex) {
               ReportingUtils.showError("could not execute core function move to home for axis " + letter);
            }
         }
      });
      sheetPanel.add(tmp_but);

      final JCheckBox illumPiezoHomeEnable = new JCheckBox("Go home on tab activate");
      ActionListener ae = new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            illumPiezoHomeEnable_ = illumPiezoHomeEnable.isSelected();
            prefs_.putBoolean(panelName_, Prefs.Keys.ENABLE_ILLUM_PIEZO_HOME, illumPiezoHomeEnable.isSelected());
         }
      };
      illumPiezoHomeEnable.addActionListener(ae);
      illumPiezoHomeEnable.setSelected(prefs_.getBoolean(panelName_, Prefs.Keys.ENABLE_ILLUM_PIEZO_HOME, true));
      ae.actionPerformed(null);
      sheetPanel.add(illumPiezoHomeEnable, "span 2, wrap");


      sheetPanel.add(new JLabel("Sheet width:"));
      sheetPanel.add(new JLabel(""), "span 2");   // TODO update this label with current value
      JSlider tmp_sl = pu.makeSlider(0, // 0 is min amplitude
              props_.getPropValueFloat(micromirrorDeviceKey_, Properties.Keys.MAX_DEFLECTION_X) - props_.getPropValueFloat(micromirrorDeviceKey_, Properties.Keys.MIN_DEFLECTION_X), // compute max amplitude
              1000, // the scale factor between internal integer representation and float representation
              props_, devices_, micromirrorDeviceKey_, Properties.Keys.SA_AMPLITUDE_X_DEG);
      sheetPanel.add(tmp_sl, "span 4, growx, center, wrap");


      sheetPanel.add(new JLabel("Sheet offset:"));
      sheetPanel.add(new JLabel(""), "span 2");   // TODO update this label with current value
      tmp_sl = pu.makeSlider(
              props.getPropValueFloat(micromirrorDeviceKey_, Properties.Keys.MIN_DEFLECTION_X), // min value
              props.getPropValueFloat(micromirrorDeviceKey_, Properties.Keys.MAX_DEFLECTION_X), // max value
              1000, // the scale factor between internal integer representation and float representation
              props_, devices_, micromirrorDeviceKey_, Properties.Keys.SA_OFFSET_X_DEG);
      sheetPanel.add(tmp_sl, "span 4, growx, center, wrap");


      // Layout of the SetupPanel
      joystickPanel_ = new JoystickSubPanel(joystick_, devices_, panelName_, side, prefs_);
      add(joystickPanel_, "center");

      sheetPanel.setBorder(BorderFactory.createLineBorder(ASIdiSPIM.borderColor));
      add(sheetPanel, "center, aligny top, span 1 3, wrap");

      beamPanel_ = new BeamSubPanel(devices_, panelName_, side, prefs_, props_);
      beamPanel_.setBorder(BorderFactory.createLineBorder(ASIdiSPIM.borderColor));
      add(beamPanel_, "center, wrap");


      cameraPanel_ = new CameraSubPanel(cameras_, devices_, panelName_, side, prefs_, true);
      cameraPanel_.setBorder(BorderFactory.createLineBorder(ASIdiSPIM.borderColor));
      add(cameraPanel_, "center");

      // set scan waveform to be triangle, just like SPIM is
      props_.setPropValue(micromirrorDeviceKey_, Properties.Keys.SA_PATTERN_X, Properties.Values.SAM_TRIANGLE, true);

   }// end of SetupPanel constructor

   /**
    * updates single-axis parameters for stepped piezos according to
    * sheetStartPos_ and sheetEndPos_
    */
   public void updateImagingSAParams() {
      if (devices_.getMMDevice(piezoImagingDeviceKey_) == null) {
         return;
      }
      float amplitude = (float) (imagingStopPos_ - imagingStartPos_);
      float offset = (float) (imagingStartPos_ + imagingStopPos_) / 2;
      props_.setPropValue(piezoImagingDeviceKey_, 
              Properties.Keys.SA_AMPLITUDE, amplitude);
      props_.setPropValue(piezoImagingDeviceKey_, 
              Properties.Keys.SA_OFFSET, offset);
   }

   /**
    * updates single-axis parameters for slice positions of micromirrors
    * according to sheetStartPos_ and sheetEndPos_
    */
   public void updateSheetSAParams() {
      if (devices_.getMMDevice(micromirrorDeviceKey_) == null) {
         return;
      }
      float amplitude = (float) (sheetStopPos_ - sheetStartPos_);
      float offset = (float) (sheetStartPos_ + sheetStopPos_) / 2;
      props_.setPropValue(micromirrorDeviceKey_, 
              Properties.Keys.SA_AMPLITUDE_Y_DEG, amplitude);
      props_.setPropValue(micromirrorDeviceKey_, 
              Properties.Keys.SA_OFFSET_Y_DEG, offset);
   }

   /**
    * updates start/stop positions from the present values of properties i'm
    * undecided if this should be called when tab is selected if yes, then
    * start/end settings are clobbered when you change tabs if no, then changes
    * to start/end settings made elsewhere (notably using joystick with scan
    * enabled) will be clobbered
    */
   public void updateStartStopPositions() {
      if (devices_.getMMDevice(piezoImagingDeviceKey_) == null) {
         return;
      }
      // compute initial start/stop positions from properties
      double amplitude = (double) props_.getPropValueFloat(
              piezoImagingDeviceKey_, Properties.Keys.SA_AMPLITUDE);
      double offset = (double) props_.getPropValueFloat(
              piezoImagingDeviceKey_, Properties.Keys.SA_OFFSET);
      imagingStartPos_ = offset - amplitude / 2;
      piezoStartPositionLabel_.setText(
              NumberUtils.doubleToDisplayString(imagingStartPos_));
      imagingStopPos_ = offset + amplitude / 2;
      piezoEndPositionLabel_.setText(
              NumberUtils.doubleToDisplayString(imagingStopPos_));
      amplitude = props_.getPropValueFloat(
              micromirrorDeviceKey_, Properties.Keys.SA_AMPLITUDE_Y_DEG);
      offset = props_.getPropValueFloat(
              micromirrorDeviceKey_, Properties.Keys.SA_OFFSET_Y_DEG);
      sheetStartPos_ = offset - amplitude / 2;
      sheetStartPosLabel_.setText(
              NumberUtils.doubleToDisplayString(sheetStartPos_));
      sheetStopPos_ = offset + amplitude / 2;
      sheetEndPositionLabel_.setText(
              NumberUtils.doubleToDisplayString(sheetStopPos_));
   }

   /**
    * finds the appropriate COM port, because we have to send "home" (!) and
    * "sethome" (HM) commands "manually" over serial since the right API calls
    * don't yet exist // TODO pester Nico et al. for API
    *
    * @return true if port is valid, false if not
    */
   private void updatePort() {
      if (port_ != null) {  // if we've already found it then skip
         return;
      }
      try {
         String mmDevice = devices_.getMMDevice(piezoIlluminationDeviceKey_);
         if (mmDevice == null) {
            return;
         }
         String hubname = core_.getParentLabel(mmDevice);
         port_ = core_.getProperty(hubname, Properties.Keys.SERIAL_COM_PORT.toString());
      } catch (Exception ex) {
         ReportingUtils.showError("Could not get COM port in SetupPanel constructor.");
      }
   }

   @Override
   public void saveSettings() {
      beamPanel_.saveSettings();
      // all other prefs are updated on button press instead of here
   }

   @Override
   public void updateStagePositions() {
      imagingPiezoPositionLabel_.setText(positions_.getPositionString(piezoImagingDeviceKey_));
      illuminationPiezoPositionLabel_.setText(positions_.getPositionString(piezoIlluminationDeviceKey_));
      sheetPositionLabel_.setText(positions_.getPositionString(micromirrorDeviceKey_, Joystick.Directions.Y));
   }

   /**
    * required by LiveModeListener interface; just pass call along to camera
    * panel
    */
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
//      props_.callListeners();  // not used yet, only for SPIM Params
      updateStartStopPositions();  // I'm undecided if this is wise or not, see updateStartStopPositions() JavaDoc

      // moves illumination piezo to home
      // TODO do this more elegantly (ideally MM API would add Home() function)
      String letter = "";
      updatePort();
      if (port_ == null) {
         return;
      }
      if (illumPiezoHomeEnable_) {
         try {
            letter = props_.getPropValueString(piezoIlluminationDeviceKey_, Properties.Keys.AXIS_LETTER);
            core_.setSerialPortCommand(port_, "! " + letter, "\r");
            // we need to read the answer or we can get in trouble later on
            // It would be nice to check the answer
            core_.getSerialPortAnswer(port_, "\r\n");
         } catch (Exception ex) {
            ReportingUtils.showError("could not execute core function move to home for axis " + letter);
         }
      }

   }
}
