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

import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Point2D;
import java.util.prefs.Preferences;

import org.micromanager.asidispim.Data.Devices;
import org.micromanager.asidispim.Data.Setup;
import org.micromanager.asidispim.Utils.ListeningJPanel;
import org.micromanager.asidispim.Utils.Labels;
import org.micromanager.asidispim.Utils.PanelUtils;

import mmcorej.CMMCore;

import javax.swing.*;

import net.miginfocom.swing.MigLayout;

import org.micromanager.MMStudioMainFrame;
import org.micromanager.api.ScriptInterface;
import org.micromanager.internalinterfaces.LiveModeListener;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author Nico
 * @author Jon
 */
@SuppressWarnings("serial")
public class SetupPanel extends ListeningJPanel implements LiveModeListener {

   ScriptInterface gui_;
   CMMCore core_;
   Devices devices_;
   Setup setup_;
   Labels.Sides side_;
   Preferences prefs_;
   JComboBox joystickBox_;
   JComboBox rightWheelBox_;
   JComboBox leftWheelBox_;
   JToggleButton toggleButtonLive_;
   JCheckBox sheetABox_;
   JCheckBox sheetBBox_;
   JLabel imagingPiezoPositionLabel_;
   JLabel illuminationPiezoPositionLabel_;
   JRadioButton singleCameraButton_;
   JRadioButton dualCameraButton_;
   String imagingPiezo_;
   String illuminationPiezo_;
   final String MULTICAMERAPREF = "IsMultiCamera";
   final String JOYSTICK = Devices.JOYSTICKS.get(Devices.JoystickDevice.JOYSTICK);
   final String RIGHTWHEEL = Devices.JOYSTICKS.get(Devices.JoystickDevice.RIGHT_KNOB);
   final String LEFTWHEEL = Devices.JOYSTICKS.get(Devices.JoystickDevice.LEFT_KNOB);
   final String SERIALCOMMAND = "SerialCommand(sent_only_on_change)";
   final String ISMULTICAMERAPREFNAME;
   
   // TODO actually use properties class
   public SetupPanel(Setup setup, ScriptInterface gui, Devices devices, Labels.Sides side) {
      super(new MigLayout(
              "",
              "[right]8[align center]16[right]8[60px,center]8[center]8[center]",
              "[]16[]"));

      devices_ = devices;
      setup_ = setup;
      gui_ = gui;
      core_ = gui_.getMMCore();
      side_ = side;
      prefs_ = Preferences.userNodeForPackage(this.getClass());
      
      
      ISMULTICAMERAPREFNAME= MULTICAMERAPREF + side_.toString();
      String joystickPrefName = JOYSTICK + side_.toString();
      String rightWheelPrefName = RIGHTWHEEL + side_.toString();
      String leftWheelPrefName = LEFTWHEEL + side_.toString();

      String jcs = "";
      if (devices_.getTwoAxisTigerStages() != null
              && devices_.getTwoAxisTigerStages().length > 0) {
         jcs = devices_.getTwoAxisTigerStages()[0];
      }
      String joystickSelection = prefs_.get(joystickPrefName, jcs);
      String ws = "";
      if (devices_.getTigerStages() != null
              && devices_.getTigerStages().length > 0) {
         ws = devices_.getTigerStages()[0];
      }
      String rightWheelSelection = prefs_.get(rightWheelPrefName, ws);
      String leftWheelSelection = prefs_.get(leftWheelPrefName, ws);

      if (side_ == Labels.Sides.B) {
         imagingPiezo_ = Devices.PIEZOB;
         illuminationPiezo_ = Devices.PIEZOA;
      }
      else {
         imagingPiezo_ = Devices.PIEZOA;
         illuminationPiezo_ = Devices.PIEZOB;         
      }

      PanelUtils pu = new PanelUtils();

      add(new JLabel(JOYSTICK + ":"));
      joystickBox_ = pu.makeJoystickSelectionBox(Devices.JoystickDevice.JOYSTICK,
              devices_.getTwoAxisTigerStages(), joystickSelection, devices_,
              prefs_, joystickPrefName);
      add(joystickBox_);
      add(new JLabel("Imaging piezo:"));
      imagingPiezoPositionLabel_ = new JLabel(Devices.posToDisplayString(
              devices_.getStagePosition(imagingPiezo_)));
      add(imagingPiezoPositionLabel_);
      
      JButton tmp_but = new JButton("Set start");
      tmp_but.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            // TODO clean this up, it's ugly and easily broken!!
            // but need better way of accessing devices I think
            String mmDevice = devices_.getMMDevice("Piezo" + side_.toString());
            if (mmDevice == null || "".equals(mmDevice))
               return;
            try {
               setup_.imagingStartPos_ = core_.getPosition(mmDevice);
            } catch (Exception ex) {
               ReportingUtils.showError(ex);
            }
            setup_.updateImagingSAParams(side_.toString());
         }
      });
      add(tmp_but);
      
      tmp_but = new JButton("Set end");
      tmp_but.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            // TODO clean this up, it's ugly and easily broken!!
            // but need better way of accessing devices I think
            String mmDevice = devices_.getMMDevice("Piezo" + side_.toString());
            if (mmDevice == null || "".equals(mmDevice))
               return;
            try {
               setup_.imagingEndPos_ = core_.getPosition(mmDevice);
            } catch (Exception ex) {
               ReportingUtils.showError(ex);
            }
            setup_.updateImagingSAParams(side_.toString());
         }
      });
      add(tmp_but, "wrap");

      add(new JLabel(RIGHTWHEEL + ":"));
      rightWheelBox_ = pu.makeJoystickSelectionBox(Devices.JoystickDevice.RIGHT_KNOB,
              devices_.getTigerStages(), rightWheelSelection, devices_, prefs_,
              rightWheelPrefName);
      add(rightWheelBox_);
      add(new JLabel("Illumination Piezo:"));
      illuminationPiezoPositionLabel_ = new JLabel(Devices.posToDisplayString(
              devices_.getStagePosition(illuminationPiezo_)));
      add(illuminationPiezoPositionLabel_);
      
      tmp_but = new JButton("Set position");
      tmp_but.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            String letter = "";
            try {
               String mmDevice = devices_.getMMDevice(illuminationPiezo_);
               if (mmDevice == null || "".equals(mmDevice)) {
                  return;
               }
               letter = core_.getProperty(mmDevice, "AxisLetter");
               String hubDevice = core_.getParentLabel(mmDevice);
               core_.setProperty(hubDevice, SERIALCOMMAND, "HM " + letter + "+");
            } catch (Exception ex) {
               ReportingUtils.showError("could not execute core function set home for axis " + letter);
            }

         }
      });
      
      add(tmp_but, "span 2, center, wrap");

      add(new JLabel(LEFTWHEEL + ":"));
      leftWheelBox_ = pu.makeJoystickSelectionBox(Devices.JoystickDevice.LEFT_KNOB,
              devices_.getTigerStages(), leftWheelSelection, devices_, prefs_,
              leftWheelPrefName);
      add(leftWheelBox_);
      
      add(new JLabel("Scan amplitude:"));
      add(new JLabel(""));
      
      JSlider tmp_sl = pu.makeSlider(Setup.SHEET_AMPLITUDE+side_.toString(), 0, // 0 is min amplitude
            ASIdiSPIMFrame.props_.getPropValueFloat(Setup.MAX_SHEET_VAL+side_.toString())-ASIdiSPIMFrame.props_.getPropValueFloat(Setup.MIN_SHEET_VAL+side_.toString()),  // max value minus min value is max amplitude
            ASIdiSPIMFrame.props_.getPropValueFloat(Setup.SHEET_AMPLITUDE+side_.toString()),  // initialize to current value
            1000);  // Slider value (int)(1000*property value)
      setup_.addListener(tmp_sl);
      add(tmp_sl, "span 2, center, wrap");

      add(new JLabel("Scan enabled:"));
      sheetABox_ = pu.makeCheckBox(Setup.SHEET_ENABLED_A, "Side A", "0 - Disabled", "1 - Enabled");
      setup_.addListener(sheetABox_);
      add(sheetABox_, "split 2");
      
      sheetBBox_ = pu.makeCheckBox(Setup.SHEET_ENABLED_B, "Side B", "0 - Disabled", "1 - Enabled");
      setup_.addListener(sheetBBox_);
      add(sheetBBox_);
      
      add(new JLabel("Scan offset:"));
      add(new JLabel(""));
      
      tmp_sl = pu.makeSlider(Setup.SHEET_OFFSET+side_.toString(), 
            ASIdiSPIMFrame.props_.getPropValueFloat(Setup.MIN_SHEET_VAL+side_.toString()),  // min value
            ASIdiSPIMFrame.props_.getPropValueFloat(Setup.MAX_SHEET_VAL+side_.toString()),  // max value
            ASIdiSPIMFrame.props_.getPropValueFloat(Setup.SHEET_OFFSET+side_.toString()),   // initialize to current value
            1000);  // Slider value (int)(1000*property value)
      setup_.addListener(tmp_sl);
      add(tmp_sl, "span 2, center, wrap");

      tmp_but = new JButton("Toggle scan");
      tmp_but.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            boolean a = sheetABox_.isSelected();
            boolean b = sheetBBox_.isSelected();
            sheetABox_.setSelected(!a);
            sheetBBox_.setSelected(!b);
         }
      });
      
      add(tmp_but, "skip 1");
      
      
      add(new JLabel("Sheet position:"));
      add(new JLabel(""));
      
      tmp_but = new JButton("Set start");
      tmp_but.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            // TODO clean this up, it's ugly and easily broken!!
            // but need better way of accessing devices I think
            String mmDevice = devices_.getMMDevice("MicroMirror"+side_.toString());
            if (mmDevice == null || "".equals(mmDevice))
               return;
            try {
                Point2D.Double pt = core_.getGalvoPosition(mmDevice);
                setup_.sheetStartPos_ = pt.y;
            } catch (Exception ex) {
               ReportingUtils.showError(ex);
            }
            setup_.updateSheetSAParams(side_.toString());
         }
      });
      add(tmp_but);
      
      tmp_but = new JButton("Set end");
      tmp_but.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            // TODO clean this up, it's ugly and easily broken!!
            // but need better way of accessing devices I think
            String mmDevice = devices_.getMMDevice("MicroMirror"+side_.toString());
            if (mmDevice == null || "".equals(mmDevice))
               return;
            try {
               Point2D.Double pt = core_.getGalvoPosition(mmDevice);
               setup_.sheetEndPos_ = pt.y;
            } catch (Exception ex) {
               ReportingUtils.showError(ex);
            }
            setup_.updateSheetSAParams(side_.toString());
         }
      });
      add(tmp_but, "wrap");

      toggleButtonLive_ = new JToggleButton();
      toggleButtonLive_.setMargin(new Insets(0, 10, 0, 10));
      toggleButtonLive_.setIconTextGap(6);
      toggleButtonLive_.setToolTipText("Continuous live view");
      setLiveButton(gui_.isLiveModeOn());
      toggleButtonLive_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            //setLiveButton(!gui_.isLiveModeOn());
            gui_.enableLiveMode(!gui_.isLiveModeOn());
         }
      });
      add(toggleButtonLive_, "span, split 3, center, width 110px");
      boolean isMultiCamera = false;
      prefs_.getBoolean(ISMULTICAMERAPREFNAME, isMultiCamera);
      dualCameraButton_ = new JRadioButton("Dual Camera");
      singleCameraButton_ = new JRadioButton("Single Camera");
      ButtonGroup singleDualCameraButtonGroup = new ButtonGroup();
      singleDualCameraButtonGroup.add(dualCameraButton_);
      singleDualCameraButtonGroup.add(singleCameraButton_);
      if (isMultiCamera) {
         dualCameraButton_.setSelected(true);
      } else {
         singleCameraButton_.setSelected(true);
      }
      ActionListener radioButtonListener = new ActionListener() {
         public void actionPerformed(ActionEvent ae) {
            JRadioButton myButton = (JRadioButton) ae.getSource();
            handleCameraButton(myButton);          
         }
      };
      dualCameraButton_.addActionListener(radioButtonListener);
      singleCameraButton_.addActionListener(radioButtonListener);
      add(singleCameraButton_, "center");
      add(dualCameraButton_, "center");
      

   }

   /**
    * Changes the looks of the live button
    * @param enable - true: live mode is switched on
    */
   public final void setLiveButton(boolean enable) {
      toggleButtonLive_.setIcon(enable ? SwingResourceManager.getIcon(MMStudioMainFrame.class,
              "/org/micromanager/icons/cancel.png")
              : SwingResourceManager.getIcon(MMStudioMainFrame.class,
              "/org/micromanager/icons/camera_go.png"));
      toggleButtonLive_.setSelected(false);
      toggleButtonLive_.setText(enable ? "Stop Live" : "Live");
   }

   /**
    * Switches the active camera to the desired one Takes care of possible side
    * effects
    *
    * @param mmDevice - name of new camera device
    */
   private void setCameraDevice(String mmDevice) {
      if (mmDevice != null) {
         try {
            boolean liveEnabled = gui_.isLiveModeOn();
            if (liveEnabled) {
               gui_.enableLiveMode(false);
            }
            core_.setProperty(
                    "Core", "Camera", mmDevice);
            gui_.refreshGUIFromCache();
            if (liveEnabled) {
               gui_.enableLiveMode(true);
            }
         } catch (Exception ex) {
            ReportingUtils.showError("Failed to set Core Camera property");
         }
      }
   }

   /**
    * Implements the ActionEventLister for the Camera selection Radio Buttons
    * @param myButton 
    */
   private void handleCameraButton(JRadioButton myButton) {
      if (myButton != null && myButton.isSelected()) {
         // default to dual camera button
         String mmDevice = devices_.getMMDevice(Devices.DUALCAMERA);
         prefs_.putBoolean(ISMULTICAMERAPREFNAME, true);
         if (myButton.equals(singleCameraButton_)) {
            prefs_.putBoolean(ISMULTICAMERAPREFNAME, false);
            if (side_ == Labels.Sides.A) {
               mmDevice = devices_.getMMDevice(Devices.CAMERAA);
            } else {
               if (side_ == Labels.Sides.B) {
                  mmDevice = devices_.getMMDevice(Devices.CAMERAB);
               }
            }
         }
         setCameraDevice(mmDevice);        
      }
   }

   @Override
   public void saveSettings() {
      prefs_.put(JOYSTICK + side_.toString(),
              (String) joystickBox_.getSelectedItem());
      prefs_.put(RIGHTWHEEL + side_.toString(),
              (String) rightWheelBox_.getSelectedItem());
      prefs_.put(LEFTWHEEL + side_.toString(),
              (String) leftWheelBox_.getSelectedItem());
   }

   /**
    * Gets called when this tab gets focus. Sets the physical UI in the Tiger
    * controller to what was selected in this pane.
    * Uses the ActionListeners of the UI components 
    */
   @Override
   public void gotSelected() {
      devices_.clearJoystickBindings();
      joystickBox_.setSelectedItem(joystickBox_.getSelectedItem());
      rightWheelBox_.setSelectedItem(rightWheelBox_.getSelectedItem());
      leftWheelBox_.setSelectedItem(leftWheelBox_.getSelectedItem());
      setup_.callListeners();
      
      JRadioButton jr = dualCameraButton_;
      if (singleCameraButton_.isSelected()) {
         jr = singleCameraButton_;
      }
      handleCameraButton(jr);
      
      // moves illumination piezo to correct place for this side
      // TODO do this more elegantly (ideally MM API would add Home() function)
      String letter = "";
      try {
         String mmDevice = devices_.getMMDevice(illuminationPiezo_);
         if (mmDevice == null || "".equals(mmDevice))
            return;
         letter = core_.getProperty(mmDevice, "AxisLetter");
         String hubDevice = core_.getParentLabel(mmDevice);
         core_.setProperty(hubDevice, SERIALCOMMAND, "! " + letter);
      } catch (Exception ex) {
         ReportingUtils.showError("could not execute core function move to home for axis " + letter);
      }
   }

   @Override
   public void updateStagePositions() {
      imagingPiezoPositionLabel_.setText(Devices.posToDisplayString(
              devices_.getStagePosition(imagingPiezo_)));
      illuminationPiezoPositionLabel_.setText(Devices.posToDisplayString(
              devices_.getStagePosition(illuminationPiezo_)));
   }

   public void liveModeEnabled(boolean enable) {
      setLiveButton(enable);
   }
}
