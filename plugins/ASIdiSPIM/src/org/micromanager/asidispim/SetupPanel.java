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

import com.swtdesigner.SwingResourceManager;
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

import org.micromanager.api.ScriptInterface;
import org.micromanager.asidispim.Utils.StoredFloatLabel;
import org.micromanager.internalinterfaces.LiveModeListener;
import org.micromanager.utils.NumberUtils;

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
   private String port_;  // needed to send serial commands directly
   private final JoystickSubPanel joystickPanel_;
   private final CameraSubPanel cameraPanel_;
   private final BeamSubPanel beamPanel_;
   // used to store the start/stop positions of the single-axis moves for imaging piezo and micromirror sheet move axis
   private double imagingPiezoStartPos_;
   private double imagingPiezoStopPos_;
   private double imagingCenterPos_;
   private double sheetStartPos_;
   private double sheetStopPos_;
   private double sheetCenterPos_;
   private boolean illumPiezoHomeEnable_;
   // device keys, get assigned in constructor based on side
   private Devices.Keys piezoImagingDeviceKey_;
   private Devices.Keys piezoIlluminationDeviceKey_;
   private Devices.Keys micromirrorDeviceKey_;
   private JLabel imagingPiezoPositionLabel_;
   private JLabel illuminationPiezoPositionLabel_;
   private JLabel sheetPositionLabel_;
   private final StoredFloatLabel sheetStartPositionLabel_;
   private final StoredFloatLabel sheetStopPositionLabel_;
   private final StoredFloatLabel imagingPiezoStartPositionLabel_;
   private final StoredFloatLabel imagingPiezoStopPositionLabel_;

   public SetupPanel(ScriptInterface gui, Devices devices, Properties props, 
           Joystick joystick, Devices.Sides side, Positions positions, 
           Cameras cameras, Prefs prefs) {
      super(Properties.Keys.PLUGIN_SETUP_PANEL_NAME.toString() + side.toString(),
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
      gui_ = gui;
      core_ = gui_.getMMCore();
      PanelUtils pu = new PanelUtils(gui_, prefs_);

      piezoImagingDeviceKey_ = Devices.getSideSpecificKey(Devices.Keys.PIEZOA, side);
      piezoIlluminationDeviceKey_ = Devices.getSideSpecificKey(Devices.Keys.PIEZOA, Devices.getOppositeSide(side));
      micromirrorDeviceKey_ = Devices.getSideSpecificKey(Devices.Keys.GALVOA, side);

      port_ = null;
      updatePort();

      sheetStartPositionLabel_ = new StoredFloatLabel(panelName_, 
              Properties.Keys.PLUGIN_SHEET_START_POS.toString(), -1, prefs_, gui_);
      sheetStartPos_ = sheetStartPositionLabel_.getFloat();
      sheetStopPositionLabel_ = new StoredFloatLabel(panelName_, 
              Properties.Keys.PLUGIN_SHEET_END_POS.toString(), 1, prefs_, gui_);
      sheetStopPos_ = sheetStopPositionLabel_.getFloat();
      imagingPiezoStartPositionLabel_ = new StoredFloatLabel(panelName_, 
              Properties.Keys.PLUGIN_PIEZO_START_POS.toString(), -40, prefs_, gui_);
      imagingPiezoStartPos_ = imagingPiezoStartPositionLabel_.getFloat();
      imagingPiezoStopPositionLabel_ = new StoredFloatLabel(panelName_, 
              Properties.Keys.PLUGIN_PIEZO_END_POS.toString(), 40, prefs_, gui_);
      imagingPiezoStopPos_ = imagingPiezoStopPositionLabel_.getFloat();
      
      
      //updateStartStopPositions();


      // Create sheet Panel with sheet and piezo controls
      MigLayout ml = new MigLayout(
              "",
              "[right]8[align center]8[right]8[]8[center]8[center]8[center]8[center]8[center]",
              "[]6[]6[]10[]6[]6[]10[]10[]10[]");
      JPanel sheetPanel = new JPanel(ml);
      
      final JFormattedTextField offsetField = pu.makeFloatEntryField(panelName_, 
              Properties.Keys.PLUGIN_OFFSET_PIEZO_SHEET.toString(), 0.1, 8);  

      final JFormattedTextField rateField = pu.makeFloatEntryField(panelName_, 
              Properties.Keys.PLUGIN_RATE_PIEZO_SHEET.toString(), -80, 8);

      sheetPanel.add(new JLabel("Acquisition Middle"));
      
      JButton goToMiddleButton = new JButton("Go to");
      goToMiddleButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            try {
               sheetCenterPos_ = props_.getPropValueFloat(micromirrorDeviceKey_,
                       Properties.Keys.SA_OFFSET_Y_DEG);
               core_.setGalvoPosition(
                       devices_.getMMDeviceException(micromirrorDeviceKey_),
                       0, sheetCenterPos_);
               imagingCenterPos_ = props_.getPropValueFloat(piezoImagingDeviceKey_,
                       Properties.Keys.SA_OFFSET);
               core_.setPosition(devices_.getMMDeviceException(piezoImagingDeviceKey_), 
                       imagingCenterPos_);
            } catch (Exception ex) {
               gui_.showError(ex);
            }
         }
      } );
      sheetPanel.add(goToMiddleButton, "span 3, center");
      
      JButton setMiddleButton = new JButton("Set");
      setMiddleButton.setContentAreaFilled(false);
      setMiddleButton.setOpaque(true);
      setMiddleButton.setBackground(Color.red);
      setMiddleButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            try {
               Point2D.Double pt = core_.getGalvoPosition(
                    devices_.getMMDeviceException(micromirrorDeviceKey_));
               sheetCenterPos_ = pt.y;
               props_.setPropValue(micromirrorDeviceKey_, 
                     Properties.Keys.SA_OFFSET_Y_DEG, (float) sheetCenterPos_);
               imagingCenterPos_ = core_.getPosition(
                    devices_.getMMDeviceException(piezoImagingDeviceKey_));
               props_.setPropValue(piezoImagingDeviceKey_, 
                        Properties.Keys.SA_OFFSET, (float) imagingCenterPos_);
            } catch (Exception ex) {
               gui_.showError(ex);
            }
         }
      });
      sheetPanel.add(setMiddleButton, "span 3, center");
      
      // TODO: let the user choose galvodelta
      final double galvoDelta = 0.05;
      
      JButton upButton = new JButton();
      upButton.setIcon(SwingResourceManager.getIcon(MMStudioMainFrame.class, "icons/arrow_up.png"));
      upButton.setText("");
      upButton.setToolTipText("Move sheet and piezo together");
      upButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            try {
               Point2D.Double pt = core_.getGalvoPosition(
                    devices_.getMMDeviceException(micromirrorDeviceKey_));
               double galvoPos = pt.y;
               setGalvoAndPiezo(galvoPos + galvoDelta, 
                       (Double) offsetField.getValue(),(Double)rateField.getValue() );
            } catch (Exception ex) {
               gui_.showError(ex);
            }
            }
      });
      
      JButton downButton = new JButton();
      downButton.setIcon(SwingResourceManager.getIcon(MMStudioMainFrame.class, "icons/arrow_down.png"));
      downButton.setText("");
      downButton.setToolTipText("Move sheet and piezo together");
      downButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            try {
               Point2D.Double pt = core_.getGalvoPosition(
                    devices_.getMMDeviceException(micromirrorDeviceKey_));
               double galvoPos = pt.y;
               setGalvoAndPiezo(galvoPos - galvoDelta, 
                       (Double) offsetField.getValue(),(Double)rateField.getValue() );
            } catch (Exception ex) {
               gui_.showError(ex);
            }
         }
      });
      
      sheetPanel.add(upButton);
      sheetPanel.add(downButton, "wrap");
           
      sheetPanel.add(new JSeparator(SwingConstants.HORIZONTAL), "span 9, growx, wrap");
      
      sheetPanel.add(new JLabel("Piezo Position = "));
      sheetPanel.add(offsetField);
      sheetPanel.add(new JLabel(" + "), "center");
      sheetPanel.add(rateField, "skip 1");
      sheetPanel.add(new JLabel (" * Sheet Position"));
      
      JButton tmp_but = new JButton("Compute");
      tmp_but.setToolTipText("Computes ratio from start and end positions");
      tmp_but.setContentAreaFilled(false);
      tmp_but.setOpaque(true);
      tmp_but.setBackground(Color.green);
      tmp_but.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            try {
               double rate = (imagingPiezoStopPos_ - imagingPiezoStartPos_)/(sheetStopPos_ - sheetStartPos_);
               rateField.setValue((Double)rate);
               double offset = (imagingPiezoStopPos_ + imagingPiezoStartPos_) / 2 - 
                       (rate * ( (sheetStopPos_ + sheetStartPos_) / 2) );
               offsetField.setValue((Double) offset);
            } catch (Exception ex) {
               gui_.showError(ex);
            }
         }
      });
      sheetPanel.add(tmp_but, "skip 2, wrap");
      
      sheetPanel.add(new JSeparator(SwingConstants.HORIZONTAL), "span 9, growx, wrap");
      
      sheetPanel.add(new JLabel("Start"), "skip 4, span2, center");
      sheetPanel.add(new JLabel("End"), "skip 1, span 2, center, wrap");
      
      sheetPanel.add(new JLabel("Sheet/slice position:"));
      sheetPositionLabel_ = new JLabel("");
      sheetPanel.add(sheetPositionLabel_);
      sheetPanel.add(pu.makeSetPositionField(micromirrorDeviceKey_, Joystick.Directions.Y, positions_));
      
      sheetPanel.add(new JSeparator(SwingConstants.VERTICAL), "spany 2, growy, shrinkx");
      sheetPanel.add(sheetStartPositionLabel_);

      // Go to start button
      tmp_but = new JButton("Go to");
      tmp_but.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            try {
               positions_.setPosition(micromirrorDeviceKey_, 
                       Joystick.Directions.Y, sheetStartPos_);
               positions_.setPosition(piezoImagingDeviceKey_, 
                       Joystick.Directions.NONE, imagingPiezoStartPos_);       
            } catch (Exception ex) {
               gui_.showError(ex);
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
                       Joystick.Directions.Y, sheetStopPos_);
               positions_.setPosition(piezoImagingDeviceKey_, 
                       Joystick.Directions.NONE, imagingPiezoStopPos_);
            } catch (Exception ex) {
               gui_.showError(ex);
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
      tmp_but.setToolTipText("Saves start position for imaging piezo and scanner slice (should be focused)");
      tmp_but.setContentAreaFilled(false);
      tmp_but.setOpaque(true);
      tmp_but.setBackground(Color.red);
      tmp_but.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            try {
               // bypass cached positions in positions_ in case they aren't current
               Point2D.Double pt = core_.getGalvoPosition(
                       devices_.getMMDeviceException(micromirrorDeviceKey_));
               sheetStartPos_ = pt.y;
               sheetStartPositionLabel_.setText(
                       NumberUtils.doubleToDisplayString(sheetStartPos_));
               imagingPiezoStartPos_ = core_.getPosition(
                       devices_.getMMDeviceException(piezoImagingDeviceKey_));
               imagingPiezoStartPositionLabel_.setText(
                       NumberUtils.doubleToDisplayString(imagingPiezoStartPos_));
            } catch (Exception ex) {
               gui_.showError(ex);
            }
         }
      });
      sheetPanel.add(tmp_but);

      sheetPanel.add(imagingPiezoStopPositionLabel_);
      
      tmp_but = new JButton("Set");
      tmp_but.setToolTipText("Saves end position for imaging piezo and scanner slice (should be focused)");
      tmp_but.setContentAreaFilled(false);
      tmp_but.setOpaque(true);
      tmp_but.setBackground(Color.red);
      tmp_but.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            try {
               // bypass cached positions in positions_ in case they aren't current
               Point2D.Double pt = core_.getGalvoPosition(
                       devices_.getMMDeviceException(micromirrorDeviceKey_));
               sheetStopPos_ = pt.y;
               sheetStopPositionLabel_.setText(
                       NumberUtils.doubleToDisplayString(sheetStopPos_));
               // updateSheetSAParams();
               imagingPiezoStopPos_ = core_.getPosition(
                       devices_.getMMDeviceException(piezoImagingDeviceKey_));
               imagingPiezoStopPositionLabel_.setText(
                       NumberUtils.doubleToDisplayString(imagingPiezoStopPos_));
               // updateImagingSAParams();
               // updateStartStopPositions();
            } catch (Exception ex) {
               gui_.showError(ex);
            }
         }
      });
      sheetPanel.add(tmp_but, "wrap");

      sheetPanel.add(new JSeparator(SwingConstants.HORIZONTAL), "span 9, growx, wrap");


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
               gui_.showError("could not execute core function set home here for axis " + letter);
            }
         }
      });
      sheetPanel.add(tmp_but, "skip 1");

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
               gui_.showError("could not execute core function move to home for axis " + letter);
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
      sheetPanel.add(illumPiezoHomeEnable, "skip 1, span 2, wrap");


      sheetPanel.add(new JLabel("Sheet width:"));
      sheetPanel.add(new JLabel(""), "span 2");   // TODO update this label with current value
      JSlider tmp_sl = pu.makeSlider(0, // 0 is min amplitude
              props_.getPropValueFloat(micromirrorDeviceKey_,Properties.Keys.MAX_DEFLECTION_X) - props_.getPropValueFloat(micromirrorDeviceKey_, Properties.Keys.MIN_DEFLECTION_X), // compute max amplitude
              1000, // the scale factor between internal integer representation and float representation
              props_, devices_, micromirrorDeviceKey_, Properties.Keys.SA_AMPLITUDE_X_DEG);
      sheetPanel.add(tmp_sl, "skip 1, span 5, growx, center, wrap");


      sheetPanel.add(new JLabel("Sheet offset:"));
      sheetPanel.add(new JLabel(""), "span 2");   // TODO update this label with current value
      tmp_sl = pu.makeSlider(
              props.getPropValueFloat(micromirrorDeviceKey_, Properties.Keys.MIN_DEFLECTION_X), // min value
              props.getPropValueFloat(micromirrorDeviceKey_, Properties.Keys.MAX_DEFLECTION_X), // max value
              1000, // the scale factor between internal integer representation and float representation
              props_, devices_, micromirrorDeviceKey_, Properties.Keys.SA_OFFSET_X_DEG);
      sheetPanel.add(tmp_sl, "skip 1, span 5, growx, center, wrap");


      // Layout of the SetupPanel
      joystickPanel_ = new JoystickSubPanel(joystick_, devices_, panelName_, side, 
              prefs_);
      add(joystickPanel_, "center");

      sheetPanel.setBorder(BorderFactory.createLineBorder(ASIdiSPIM.borderColor));
      add(sheetPanel, "center, aligny top, span 1 3, wrap");

      beamPanel_ = new BeamSubPanel(devices_, panelName_, side, prefs_, props_);
      beamPanel_.setBorder(BorderFactory.createLineBorder(ASIdiSPIM.borderColor));
      add(beamPanel_, "center, wrap");


      cameraPanel_ = new CameraSubPanel(gui_, cameras_, devices_, panelName_, 
              side, prefs_, true);
      cameraPanel_.setBorder(BorderFactory.createLineBorder(ASIdiSPIM.borderColor));
      add(cameraPanel_, "center");

      // set scan waveform to be triangle, just like SPIM is
      props_.setPropValue(micromirrorDeviceKey_, Properties.Keys.SA_PATTERN_X, 
              Properties.Values.SAM_TRIANGLE, true);

   }// end of SetupPanel constructor

   
   /**
    * Utility function that moves the galvo and piezo together
    */
   public void setGalvoAndPiezo(double newGalvoPos, double offset, double rate) {
       positions_.setPosition(micromirrorDeviceKey_, 
                       Joystick.Directions.Y, newGalvoPos);
       positions_.setPosition(piezoImagingDeviceKey_, 
                       Joystick.Directions.NONE, offset + rate * newGalvoPos);   
   } 
                       
   /**
    * updates single-axis parameters for stepped piezos according to
    * sheetStartPos_ and sheetEndPos_
    */
   public void updateImagingSAParams() {
      if (devices_.getMMDevice(piezoImagingDeviceKey_) == null) {
         return;
      }
      float amplitude = (float) (imagingPiezoStopPos_ - imagingPiezoStartPos_);
      float offset = (float) (imagingPiezoStartPos_ + imagingPiezoStopPos_) / 2;
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
   // TODO remove this, should not be needed any more because we aren't setting
   //    up single axis values in this tab anymore but just calculating the ratio
   public void updateStartStopPositions() {
      if (devices_.getMMDevice(piezoImagingDeviceKey_) == null) {
         return;
      }
      // compute initial start/stop positions from properties
      /*
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
      */
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
         gui_.showError("Could not get COM port in SetupPanel constructor.");
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
            gui_.showError("could not execute core function move to home for axis " + letter);
         }
      }

   }
}
