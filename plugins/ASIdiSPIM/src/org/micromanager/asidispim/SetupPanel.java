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

import org.micromanager.api.ScriptInterface;
import org.micromanager.asidispim.Data.Devices;
import org.micromanager.asidispim.Data.Joystick;
import org.micromanager.asidispim.Data.Positions;
import org.micromanager.asidispim.Data.Properties;
import org.micromanager.asidispim.Utils.ListeningJPanel;
import org.micromanager.asidispim.Utils.PanelUtils;

import mmcorej.CMMCore;

import javax.swing.*;

import net.miginfocom.swing.MigLayout;

import org.micromanager.MMStudioMainFrame;
import org.micromanager.internalinterfaces.LiveModeListener;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author Nico
 * @author Jon
 */
@SuppressWarnings("serial")
public final class SetupPanel extends ListeningJPanel implements LiveModeListener {

   Devices devices_;
   Devices.Sides side_;
   Properties props_;
   Joystick joystick_;
   Positions positions_;
   Preferences prefs_;
   private CMMCore core_;
   ScriptInterface gui_;
   String port_;  // need to send serial commands directly
   
   JPanel joystickPanel_;
   
   // used to store the start/stop positions of the single-axis moves for imaging piezo and micromirror sheet move axis
   double imagingStartPos_;
   double imagingStopPos_;
   double sheetStartPos_;
   double sheetStopPos_;
   
   private boolean illumPiezoHomeEnable_;
   
   JRadioButton singleCameraButton_; 
   JRadioButton dualCameraButton_;
   final String MULTICAMERAPREF = "IsMultiCamera";
   final String ISMULTICAMERAPREFNAME;
   
   // device keys, get assigned in constructor based on side
   Devices.Keys piezoImagingDeviceKey_;
   Devices.Keys piezoIlluminationDeviceKey_;
   Devices.Keys micromirrorDeviceKey_;
   
   JToggleButton toggleButtonLive_;
   JCheckBox sheetABox_;
   JCheckBox sheetBBox_;
   JCheckBox beamABox_;
   JCheckBox beamBBox_;
   JLabel imagingPiezoPositionLabel_;
   JLabel illuminationPiezoPositionLabel_;
   
   private static final String PREF_ENABLE_ILLUM_HOME = "EnableIllumPiezoHome";

   public SetupPanel(Devices devices, Properties props, Joystick joystick, Devices.Sides side, Positions positions) {
      super(new MigLayout(
              "",
              "[right]8[align center]16[right]8[60px,center]8[center]8[center]",
              "[]16[]"));
      devices_ = devices;
      props_ = props;
      joystick_ = joystick;
      positions_ = positions;
      side_ = side;
      prefs_ = Preferences.userNodeForPackage(this.getClass());
      gui_ = MMStudioMainFrame.getInstance();
      core_ = MMStudioMainFrame.getInstance().getCore();
      
      piezoImagingDeviceKey_ = devices_.getSideSpecificKey(Devices.Keys.PIEZOA, side_);
      piezoIlluminationDeviceKey_ = devices_.getSideSpecificKey(Devices.Keys.PIEZOA, devices.getOppositeSide(side_));
      micromirrorDeviceKey_ = devices_.getSideSpecificKey(Devices.Keys.GALVOA, side_);
      
      port_ = null;
      updatePort();

      ISMULTICAMERAPREFNAME= MULTICAMERAPREF + side_.toString();
      
      updateStartStopPositions();
      
      joystickPanel_ = new JoystickPanel(joystick_, devices_, "Side" + side_.toString());
      add(joystickPanel_, "span 2 3");
      
      add(new JLabel("Imaging piezo:"));
      imagingPiezoPositionLabel_ = new JLabel("");
      add(imagingPiezoPositionLabel_);
      
      JButton tmp_but = new JButton("Set start here");
      tmp_but.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            try {
               imagingStartPos_ = core_.getPosition(devices_.getMMDeviceException(piezoImagingDeviceKey_));
               updateImagingSAParams();
            } catch (Exception ex) {
               ReportingUtils.showError(ex);
            }
         }
      });
      add(tmp_but);

      tmp_but = new JButton("Set end here");
      tmp_but.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            try {
               imagingStopPos_ = core_.getPosition(devices_.getMMDeviceException(piezoImagingDeviceKey_));
               updateImagingSAParams();
            } catch (Exception ex) {
               ReportingUtils.showError(ex);
            }
         }
      });
      add(tmp_but, "wrap");
      
    add(new JLabel("Illumination Piezo:"));
    illuminationPiezoPositionLabel_ = new JLabel("");
    add(illuminationPiezoPositionLabel_);
    
    tmp_but = new JButton("Set to here");
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
             core_.setSerialPortCommand(port_, "HM "+letter+"+", "\r");
          } catch (Exception ex) {
             ReportingUtils.showError("could not execute core function set home for axis " + letter);
          }
       }
    });
    add(tmp_but);
    
    final JCheckBox illumPiezoHomeEnable = new JCheckBox("Move on tab change");
    ActionListener ae = new ActionListener() {
       public void actionPerformed(ActionEvent e) { 
          illumPiezoHomeEnable_ = illumPiezoHomeEnable.isSelected();
          prefs_.putBoolean(PREF_ENABLE_ILLUM_HOME, illumPiezoHomeEnable.isSelected());
       }
    }; 
    illumPiezoHomeEnable.addActionListener(ae);
    illumPiezoHomeEnable.setSelected(prefs_.getBoolean(PREF_ENABLE_ILLUM_HOME, true));
    ae.actionPerformed(null);
    add(illumPiezoHomeEnable, "wrap");
    
    add(new JLabel("Scan amplitude:"));
    add(new JLabel(""));   // TODO update this label with current value
    PanelUtils pu = new PanelUtils();
    JSlider tmp_sl = pu.makeSlider(0, // 0 is min amplitude
          props_.getPropValueFloat(micromirrorDeviceKey_, Properties.Keys.MAX_DEFLECTION_X) - props_.getPropValueFloat(micromirrorDeviceKey_, Properties.Keys.MIN_DEFLECTION_X), // compute max amplitude
          1000,   // the scale factor between internal integer representation and float representation
          props_, devices_, micromirrorDeviceKey_, Properties.Keys.SA_AMPLITUDE_X_DEG);
    add(tmp_sl, "span 2, center, wrap");
    
    add(new JLabel("Beam enabled:"));
    beamABox_ = pu.makeCheckBox("Path A", 
          Properties.Values.NO.toString(), Properties.Values.YES.toString(),
          props_, devices_, 
          Devices.Keys.GALVOA, Properties.Keys.BEAM_ENABLED);
    add(beamABox_, "split 2");
    beamBBox_ = pu.makeCheckBox("Path B", "No", "Yes", props_, devices_, 
          Devices.Keys.GALVOB, Properties.Keys.BEAM_ENABLED);
    add(beamBBox_);
    
    add(new JLabel("Scan offset:"), "span 1 2");
    add(new JLabel(""), "span 1 2");   // TODO update this label with current value

    tmp_sl = pu.makeSlider(
          props.getPropValueFloat(micromirrorDeviceKey_, Properties.Keys.MIN_DEFLECTION_X), // min value
          props.getPropValueFloat(micromirrorDeviceKey_, Properties.Keys.MAX_DEFLECTION_X), // max value
          1000,  // the scale factor between internal integer representation and float representation
          props_, devices_, micromirrorDeviceKey_, Properties.Keys.SA_OFFSET_X_DEG);
    add(tmp_sl, "span 2 2, center, wrap");
    
    add(new JLabel("Scan enabled:"));
    sheetABox_ = pu.makeCheckBox("Path A", 
          Properties.Values.SAM_DISABLED.toString(), Properties.Values.SAM_ENABLED.toString(), 
          props_, devices_, 
          Devices.Keys.GALVOA, Properties.Keys.SA_MODE_X);
    add(sheetABox_, "split 2");
    sheetBBox_ = pu.makeCheckBox("Path B", "0 - Disabled", "1 - Enabled", props_, devices_, 
          Devices.Keys.GALVOB, Properties.Keys.SA_MODE_X);
    add(sheetBBox_, "wrap");

    // disable the sheetA/B boxes when beam is disabled and vice versa
    ActionListener alA = 
    new ActionListener() {
       public void actionPerformed(ActionEvent e) { 
          sheetABox_.setEnabled(beamABox_.isSelected());
          // whether beam is turned off or on, we want scan to be off on toggle
          sheetABox_.setSelected(false);
       }
    }; 
    alA.actionPerformed(null);
    beamABox_.addActionListener(alA);
    
    ActionListener alB = new ActionListener() {
       public void actionPerformed(ActionEvent e) { 
          sheetBBox_.setEnabled(beamBBox_.isSelected());
          // whether beam is turned off or on, we want scan to be off on toggle
          sheetBBox_.setSelected(false);
       }
    };
    alB.actionPerformed(null);
    beamBBox_.addActionListener(alB);
    
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
    
    add(new JLabel("Sheet/slice position:"));
    add(new JLabel(""));

    tmp_but = new JButton("Set start here");
    tmp_but.addActionListener(new ActionListener() {
       @Override
       public void actionPerformed(ActionEvent e) {
          try {
             Point2D.Double pt = core_.getGalvoPosition(devices_.getMMDeviceException(micromirrorDeviceKey_));
             sheetStartPos_ = pt.y;
             updateSheetSAParams();
          } catch (Exception ex) {
             ReportingUtils.showError(ex);
          }
       }
    });
    add(tmp_but);
    
    tmp_but = new JButton("Set end here");
    tmp_but.addActionListener(new ActionListener() {
       @Override
       public void actionPerformed(ActionEvent e) {
          try {
             Point2D.Double pt = core_.getGalvoPosition(devices_.getMMDeviceException(micromirrorDeviceKey_));
             sheetStopPos_ = pt.y;
             updateSheetSAParams();
          } catch (Exception ex) {
             ReportingUtils.showError(ex);
          }
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
          setLiveButton(!gui_.isLiveModeOn());          
          gui_.enableLiveMode(!gui_.isLiveModeOn());
       }
    });


    add(toggleButtonLive_, "span, split 3, center, width 110px");
    String isMultiCameraPrefName = MULTICAMERAPREF + side_.toString();
    boolean isMultiCamera = false;
    prefs_.getBoolean(isMultiCameraPrefName, isMultiCamera);
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

      
   }// end of constructor
   
   /**
    * required by LiveModeListener interface
    */
   public void liveModeEnabled(boolean enable) { 
      setLiveButton(enable); 
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
            core_.setProperty("Core", "Camera", mmDevice); 
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
         String mmDevice;
         if (myButton.equals(singleCameraButton_)) { 
            mmDevice = devices_.getMMDevice(devices_.getSideSpecificKey(Devices.Keys.CAMERAA, side_));
            prefs_.putBoolean(ISMULTICAMERAPREFNAME, false);
         } else {
            // default to dual camera button 
            mmDevice = devices_.getMMDevice(Devices.Keys.MULTICAMERA); 
            prefs_.putBoolean(ISMULTICAMERAPREFNAME, true); 
         }
         setCameraDevice(mmDevice);
      }
   }
   
   /**
    * Gets called when this tab gets focus.
    * Uses the ActionListeners of the UI components
    */
   @Override
   public void gotSelected() {
      ((ListeningJPanel) joystickPanel_).gotSelected();
      props_.callListeners();
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
            core_.setSerialPortCommand(port_, "! "+letter, "\r");
            // we need to read the answer or we can get in trouble later on
            // It would be nice to check the answer
            core_.getSerialPortAnswer(port_, "\r\n");
         } catch (Exception ex) {
            ReportingUtils.showError("could not execute core function move to home for axis " + letter);
         }
      }
     
      sheetABox_.setEnabled(beamABox_.isSelected());
      sheetBBox_.setEnabled(beamBBox_.isSelected());
      
      // handles single/dual camera
      JRadioButton jr = dualCameraButton_; 
      if (singleCameraButton_.isSelected()) { 
         jr = singleCameraButton_; 
      } 
      handleCameraButton(jr); 
   }
   
   /**
    * updates single-axis parameters for stepped piezos
    * according to sheetStartPos_ and sheetEndPos_
    */
   public void updateImagingSAParams() {
      if (devices_.getMMDevice(piezoImagingDeviceKey_) == null) {
         return;
      }
      float amplitude = (float)(imagingStopPos_ - imagingStartPos_);
      float offset = (float)(imagingStartPos_ + imagingStopPos_)/2;
      props_.setPropValue(piezoImagingDeviceKey_, Properties.Keys.SA_AMPLITUDE, amplitude);
      props_.setPropValue(piezoImagingDeviceKey_, Properties.Keys.SA_OFFSET, offset);
   }
   
   /**
    * updates single-axis parameters for slice positions of micromirrors
    * according to sheetStartPos_ and sheetEndPos_
    */
   public void updateSheetSAParams() {
      if (devices_.getMMDevice(micromirrorDeviceKey_) == null) {
         return;
      }
      float amplitude = (float)(sheetStopPos_ - sheetStartPos_);
      float offset = (float)(sheetStartPos_ + sheetStopPos_)/2;
      props_.setPropValue(micromirrorDeviceKey_, Properties.Keys.SA_AMPLITUDE_Y_DEG, amplitude);
      props_.setPropValue(micromirrorDeviceKey_, Properties.Keys.SA_OFFSET_Y_DEG, offset);
   }
   
   /**
    * updates start/stop positions from the present values of properties
    * i'm undecided if this should be called when tab is selected
    * if yes, then start/end settings are clobbered when you change tabs
    * if no, then changes to start/end settings made elsewhere (notably using joystick with scan enabled) will be clobbered
    */
   public void updateStartStopPositions() {
      if (devices_.getMMDevice(piezoImagingDeviceKey_) == null) {
         return;
      }
      // compute initial start/stop positions from properties
      double amplitude = (double)props_.getPropValueFloat(piezoImagingDeviceKey_, Properties.Keys.SA_AMPLITUDE);
      double offset = (double)props_.getPropValueFloat(piezoImagingDeviceKey_, Properties.Keys.SA_OFFSET);
      imagingStartPos_ = offset - amplitude/2;
      imagingStopPos_ = offset + amplitude/2;
      amplitude = props_.getPropValueFloat(micromirrorDeviceKey_, Properties.Keys.SA_AMPLITUDE_Y_DEG);
      offset = props_.getPropValueFloat(micromirrorDeviceKey_, Properties.Keys.SA_OFFSET_Y_DEG);
      sheetStartPos_ = offset - amplitude/2;
      sheetStopPos_ = offset + amplitude/2;
   }
   
   /**
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
      }
      catch (Exception ex) {
         ReportingUtils.showError("Could not get COM port in SetupPanel constructor.");
      }
   }

   @Override
   public void saveSettings() {
      // now all prefs are updated on button press instead of here
   }

   @Override
   public void updateStagePositions() {
      imagingPiezoPositionLabel_.setText(positions_.getPositionString(piezoImagingDeviceKey_));
      illuminationPiezoPositionLabel_.setText(positions_.getPositionString(piezoIlluminationDeviceKey_));
   }
      
}
