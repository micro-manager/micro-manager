/*
 * DualAndorFrame.java
 *
 * Created on Oct 29, 2010, 11:32:29 AM
 *
 * Copyright UCSF, 2010
 *
 Author: Nico Stuurman: nico at cmp.ucsf.edu
 *
 */

package org.micromanager.multicamera;


import java.awt.Rectangle;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.prefs.Preferences;
import java.util.logging.Level;
import java.util.logging.Logger;

import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.Roi;

import mmcorej.CMMCore;
import mmcorej.DeviceType;
import mmcorej.MMCoreJ;
import mmcorej.StrVector;

import org.micromanager.api.ScriptInterface;
import org.micromanager.api.DeviceControlGUI;
import org.micromanager.utils.NumberUtils;
import org.micromanager.utils.ReportingUtils;


/**
 *
 * @author Nico Stuurman
 */
public class MultiCameraFrame extends javax.swing.JFrame {
   private final ScriptInterface gui_;
   private final DeviceControlGUI dGui_;
   private final CMMCore core_;
   private Preferences prefs_;
   private NumberFormat nf_;

   private int frameXPos_ = 100;
   private int frameYPos_ = 100;
   
   private long imageWidth_ = 512;
   private long imageHeight_ = 512;
   private int EMGainMin_ = 4;
   private int EMGainMax_ = 1000;
   private String[] cameras_;
   private boolean initialized_ = false;

   private static final String FRAMEXPOS = "FRAMEXPOS";
   private static final String FRAMEYPOS = "FRAMEYPOS";
   private static final String MODE = "Output Amplifier";
   private static final String EMMODE = "Standard EMCCD gain register";
   private static final String NORMALMODE = "Conventional CCD register";
   private static final String EMGAIN = "Gain";
   private static final String EMSWITCH = "EMSwitch";
   private static final String AMPGAIN = "Pre-Amp-Gain";
   // Retarted, but Andor calls this readout mode
   private static final String SPEED = "ReadoutMode";

    /** Creates new form MultiCameraFrame */
    public MultiCameraFrame(ScriptInterface gui) throws Exception {
       gui_ = gui;
       dGui_ = (DeviceControlGUI) gui_;
       core_ = gui_.getMMCore();
       nf_ = NumberFormat.getInstance();
       prefs_ = Preferences.userNodeForPackage(this.getClass());


       mmcorej.StrVector cameras = core_.getLoadedDevicesOfType(DeviceType.CameraDevice);
       cameras_ = cameras.toArray();

       if (cameras_.length <= 1) {
          gui_.showError("This plugin needs at least two cameras");
          throw new IllegalArgumentException("This plugin needs at least two cameras");
       }

       String currentCamera = core_.getCameraDevice();
       imageWidth_ = core_.getImageWidth();
       imageHeight_ = core_.getImageHeight();
       for (String camera : cameras_) {
          if (!camera.equals(currentCamera)) {
             core_.setCameraDevice(camera);
             if (imageWidth_ != core_.getImageWidth() ||
                 imageHeight_ != core_.getImageHeight()) {
                throw new IllegalArgumentException("Plugin failed to load since the attached cameras differ in image size");
             }
          }
       }

       frameXPos_ = prefs_.getInt(FRAMEXPOS, frameXPos_);
       frameYPos_ = prefs_.getInt(FRAMEYPOS, frameYPos_);

       initComponents();

       setLocation(frameXPos_, frameYPos_);

       setBackground(gui_.getBackgroundColor());

       if (!core_.hasProperty(currentCamera, MODE)) {
          jLabel5.setEnabled(false);
          ModeComboBox.setEnabled(false);
       } else {
          ModeComboBox.removeAllItems();
          ModeComboBox.addItem(EMMODE);
          ModeComboBox.addItem(NORMALMODE);
       }

       if (!core_.hasProperty(currentCamera, EMGAIN)) {
          jLabel4.setEnabled(false);
          EMGainTextField.setEnabled(false);
          EMGainSlider.setEnabled(false);
          jCheckBox2.setEnabled(false);
       } else {
          EMGainMin_ = (int) core_.getPropertyLowerLimit(currentCamera, EMGAIN);
          EMGainMax_ = (int) core_.getPropertyUpperLimit(currentCamera, EMGAIN);
          EMGainSlider.setMinimum(EMGainMin_);
          EMGainSlider.setMaximum(EMGainMax_);
          int gain = NumberUtils.coreStringToInt(core_.getProperty(currentCamera, EMGAIN));
          EMGainSlider.setValue(gain);
          EMGainTextField.setText(NumberUtils.intToDisplayString(gain));

          if (core_.hasProperty(currentCamera, EMSWITCH)) {
             String val = core_.getProperty(currentCamera, EMSWITCH);
             if (val.equals("On")) {
                jCheckBox2.setSelected(true);
             } else {
                jCheckBox2.setSelected(false);
             }
          } else
             jCheckBox2.setEnabled(false);
       }

       // Pre-amp Gain
       if (!core_.hasProperty(currentCamera, AMPGAIN)) {
          GainComboBox.setEnabled(false);
       } else {
          UpdateItems(GainComboBox, AMPGAIN);
       }

       // Readout speed
       if (!core_.hasProperty(currentCamera, SPEED)) {
          SpeedComboBox.setEnabled(false);
       } else {
          UpdateItems(SpeedComboBox, SPEED);
       }

       // synchronize this variable?
       initialized(true, true);
    }

    public void safePrefs() {
       prefs_.putInt(FRAMEXPOS, this.getX());
       prefs_.putInt(FRAMEYPOS, this.getY());
       
    }
    private synchronized boolean initialized(boolean set, boolean value) {
       if (set)
          initialized_ = value;
       else
          value = initialized_;
       return initialized_;
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
   // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
   private void initComponents() {

      jCheckBox1 = new javax.swing.JCheckBox();
      jLabel1 = new javax.swing.JLabel();
      jButton2 = new javax.swing.JButton();
      jButton3 = new javax.swing.JButton();
      jLabel2 = new javax.swing.JLabel();
      jLabel3 = new javax.swing.JLabel();
      BinningComboBox = new javax.swing.JComboBox();
      ExposureTextField = new javax.swing.JTextField();
      EMGainSlider = new javax.swing.JSlider();
      EMGainTextField = new javax.swing.JTextField();
      ModeComboBox = new javax.swing.JComboBox();
      jLabel4 = new javax.swing.JLabel();
      jLabel5 = new javax.swing.JLabel();
      jLabel6 = new javax.swing.JLabel();
      SpeedComboBox = new javax.swing.JComboBox();
      jLabel7 = new javax.swing.JLabel();
      jCheckBox2 = new javax.swing.JCheckBox();
      GainComboBox = new javax.swing.JComboBox();

      jCheckBox1.setText("jCheckBox1");

      setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
      setTitle("Multi-Camera Control");
      setResizable(false);
      getContentPane().setLayout(new org.netbeans.lib.awtextra.AbsoluteLayout());

      jLabel1.setFont(new java.awt.Font("Lucida Grande", 1, 12));
      jLabel1.setText("ROI");
      jLabel1.setMaximumSize(null);
      jLabel1.setMinimumSize(null);
      jLabel1.setPreferredSize(null);
      getContentPane().add(jLabel1, new org.netbeans.lib.awtextra.AbsoluteConstraints(230, 20, -1, 10));

      jButton2.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/icons/shape_handles.png"))); // NOI18N
      jButton2.setToolTipText("Set Region of Interest");
      jButton2.setMaximumSize(null);
      jButton2.setMinimumSize(null);
      jButton2.setPreferredSize(null);
      jButton2.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            jButton2ActionPerformed(evt);
         }
      });
      getContentPane().add(jButton2, new org.netbeans.lib.awtextra.AbsoluteConstraints(230, 40, -1, 20));

      jButton3.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/icons/arrow_out.png"))); // NOI18N
      jButton3.setToolTipText("Set Region of Interest");
      jButton3.setMaximumSize(null);
      jButton3.setMinimumSize(null);
      jButton3.setPreferredSize(null);
      jButton3.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            jButton3ActionPerformed(evt);
         }
      });
      getContentPane().add(jButton3, new org.netbeans.lib.awtextra.AbsoluteConstraints(270, 40, -1, 20));

      jLabel2.setFont(new java.awt.Font("Lucida Grande", 0, 12));
      jLabel2.setText("Exposure [ms]");
      getContentPane().add(jLabel2, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 20, -1, -1));

      jLabel3.setFont(new java.awt.Font("Lucida Grande", 0, 12));
      jLabel3.setText("Binning");
      getContentPane().add(jLabel3, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 50, -1, -1));

      BinningComboBox.setFont(new java.awt.Font("Lucida Grande", 0, 12)); // NOI18N
      BinningComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "1", "2", "4", "8" }));
      BinningComboBox.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            BinningComboBoxActionPerformed(evt);
         }
      });
      getContentPane().add(BinningComboBox, new org.netbeans.lib.awtextra.AbsoluteConstraints(100, 40, 80, 23));

      ExposureTextField.setFont(new java.awt.Font("Lucida Grande", 0, 12)); // NOI18N
      ExposureTextField.setText("10");
      ExposureTextField.setToolTipText("Exposure time in ms");
      ExposureTextField.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            ExposureTextFieldActionPerformed(evt);
         }
      });
      getContentPane().add(ExposureTextField, new org.netbeans.lib.awtextra.AbsoluteConstraints(100, 10, 70, -1));

      EMGainSlider.addChangeListener(new javax.swing.event.ChangeListener() {
         public void stateChanged(javax.swing.event.ChangeEvent evt) {
            EMGainSliderStateChanged(evt);
         }
      });
      getContentPane().add(EMGainSlider, new org.netbeans.lib.awtextra.AbsoluteConstraints(150, 100, 180, -1));

      EMGainTextField.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      EMGainTextField.setText("4");
      EMGainTextField.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            EMGainTextFieldActionPerformed(evt);
         }
      });
      getContentPane().add(EMGainTextField, new org.netbeans.lib.awtextra.AbsoluteConstraints(100, 100, 49, -1));

      ModeComboBox.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      ModeComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "EM", "Conventional" }));
      ModeComboBox.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            ModeComboBoxActionPerformed(evt);
         }
      });
      getContentPane().add(ModeComboBox, new org.netbeans.lib.awtextra.AbsoluteConstraints(100, 70, 105, -1));

      jLabel4.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      jLabel4.setText("EM Gain");
      getContentPane().add(jLabel4, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 120, -1, -1));

      jLabel5.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      jLabel5.setText("Mode");
      getContentPane().add(jLabel5, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 80, -1, -1));

      jLabel6.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      jLabel6.setText("Gain");
      getContentPane().add(jLabel6, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 160, -1, -1));

      SpeedComboBox.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      SpeedComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "1MHz", "3MHz", "5MHz", "10MHz" }));
      SpeedComboBox.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            SpeedComboBoxActionPerformed(evt);
         }
      });
      getContentPane().add(SpeedComboBox, new org.netbeans.lib.awtextra.AbsoluteConstraints(100, 180, 105, -1));

      jLabel7.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      jLabel7.setText("Speed");
      getContentPane().add(jLabel7, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 190, -1, -1));

      jCheckBox2.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      jCheckBox2.setText("Use");
      jCheckBox2.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            jCheckBox2ActionPerformed(evt);
         }
      });
      getContentPane().add(jCheckBox2, new org.netbeans.lib.awtextra.AbsoluteConstraints(100, 120, -1, -1));

      GainComboBox.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      GainComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "1", "2", "3", "4", "5" }));
      GainComboBox.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            GainComboBoxActionPerformed(evt);
         }
      });
      getContentPane().add(GainComboBox, new org.netbeans.lib.awtextra.AbsoluteConstraints(100, 150, 105, -1));

      pack();
   }// </editor-fold>//GEN-END:initComponents

    private void jButton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton2ActionPerformed
       setRoi();
    }//GEN-LAST:event_jButton2ActionPerformed

    private void jButton3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton3ActionPerformed
       setRoi(new Rectangle(0,0,(int)imageWidth_, (int)imageHeight_));
    }//GEN-LAST:event_jButton3ActionPerformed

    private void ExposureTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ExposureTextFieldActionPerformed
       boolean liveRunning = dGui_.getLiveMode();
       String currentCamera = "";
       try {
          double exposure = NumberUtils.displayStringToDouble(ExposureTextField.getText());
          currentCamera =  core_.getCameraDevice();
          dGui_.enableLiveMode(false);
          for(String camera: cameras_) {
             core_.setCameraDevice(camera);
             core_.setExposure(exposure);
          }
          ExposureTextField.setText(NumberUtils.doubleToDisplayString(exposure));

         dGui_.updateGUI(false);
         dGui_.enableLiveMode(liveRunning);
      } catch (Exception ex) {
          ReportingUtils.showError(ex, MultiCameraFrame.class.getName() + " encountered an error.");
      }
    }//GEN-LAST:event_ExposureTextFieldActionPerformed

    private void BinningComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_BinningComboBoxActionPerformed
       if (!initialized(false, false))
          return;
       boolean liveRunning = dGui_.getLiveMode();
       Object item = BinningComboBox.getSelectedItem();
       String binning = item.toString();
       try {
          dGui_.enableLiveMode(false);
          for(String camera: cameras_) {
             if (!camera.equals("")) {
                core_.setProperty(camera, MMCoreJ.getG_Keyword_Binning(), binning);
             }
          }
          dGui_.enableLiveMode(liveRunning);
          dGui_.updateGUI(false);
       } catch (Exception ex) {
          ReportingUtils.showError(ex, MultiCameraFrame.class.getName() + " encountered an error.");
       }
    }//GEN-LAST:event_BinningComboBoxActionPerformed

    private void EMGainTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_EMGainTextFieldActionPerformed
       if (!initialized(false, false))
          return;
       try {
         int val = NumberUtils.displayStringToInt(EMGainTextField.getText());
         if (val > EMGainMax_)
            val = EMGainMax_;
         if (val < EMGainMin_)
            val = EMGainMin_;
         EMGainSlider.setValue(val);
       } catch (ParseException e) {
          // ignore if the user types garbage
       }
    }//GEN-LAST:event_EMGainTextFieldActionPerformed

    private void jCheckBox2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jCheckBox2ActionPerformed
       // EM enable
       if (!initialized(false, false))
          return;
       // Should we check if live is running?
       boolean on = jCheckBox2.isSelected();
       String command = "Off";
       if (on)
          command = "On";
       try {
          for (String camera: cameras_) {
             if (!camera.equals("")) {
                core_.setProperty(camera, EMSWITCH, command);
             }
          }
       } catch (Exception ex) {
          ReportingUtils.showError(ex, MultiCameraFrame.class.getName() + " encountered an error.");
       }
    }//GEN-LAST:event_jCheckBox2ActionPerformed

    private void ModeComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ModeComboBoxActionPerformed
       // Combo box selecting readout mode (EM/standard)
       if (!initialized(false, false))
          return;
       boolean liveRunning = dGui_.getLiveMode();
       Object item = ModeComboBox.getSelectedItem();
       String mode = item.toString();
       try {
          dGui_.enableLiveMode(false);
          for(String camera: cameras_) {
             if (!camera.equals("")) {
                core_.setProperty(camera, MODE, mode);
             }
          }
          UpdateItems(GainComboBox, AMPGAIN);
          UpdateItems(SpeedComboBox, SPEED);
          
          dGui_.enableLiveMode(liveRunning);
          dGui_.updateGUI(false);
       } catch (Exception ex) {
          ReportingUtils.showError(ex, MultiCameraFrame.class.getName() + " encountered an error.");
       }
    }//GEN-LAST:event_ModeComboBoxActionPerformed

    private void EMGainSliderStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_EMGainSliderStateChanged
       if (!initialized(false, false))
          return;
       int val = EMGainSlider.getValue();
       // Stop live mode for EMGAin change?
       try {
          for (String camera: cameras_) {
             if (!camera.equals("")) {
                core_.setProperty(camera, EMGAIN, NumberUtils.intToCoreString(val));
             }
          }
       } catch(Exception ex) {
          ReportingUtils.showError(ex, MultiCameraFrame.class.getName() + " encountered an error.");
       }
       EMGainTextField.setText(NumberUtils.intToDisplayString(val));
    }//GEN-LAST:event_EMGainSliderStateChanged

    private void GainComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_GainComboBoxActionPerformed
       if (!initialized(false, false))
          return;

       // Stop live mode for gain change?
       try {
          for (String camera: cameras_) {
             core_.setProperty(camera, AMPGAIN, (String) GainComboBox.getSelectedItem());
          }
       } catch (Exception ex) {
           ReportingUtils.showError(ex, MultiCameraFrame.class.getName() + " encountered an error.");
       }
   
    }//GEN-LAST:event_GainComboBoxActionPerformed

    private void SpeedComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_SpeedComboBoxActionPerformed
       if (!initialized(false, false))
          return;

       // Stop live mode for speed change?
       try {
          for (String camera: cameras_) {
             core_.setProperty(camera, SPEED, (String) SpeedComboBox.getSelectedItem());
          }
       } catch (Exception ex) {
          ReportingUtils.showError(ex, MultiCameraFrame.class.getName() + " encountered an error.");
       }

    }//GEN-LAST:event_SpeedComboBoxActionPerformed

    private void UpdateItems(javax.swing.JComboBox comboBox, String property) {
       try {
          comboBox.removeAllItems();
          StrVector vals = core_.getAllowedPropertyValues(cameras_[0], property);
          for (String val : vals) {
             comboBox.addItem(val);
          }
       } catch (Exception ex) {
          ReportingUtils.showError(ex, MultiCameraFrame.class.getName() + " encountered an error.");
       }
    }

   // Variables declaration - do not modify//GEN-BEGIN:variables
   private javax.swing.JComboBox BinningComboBox;
   private javax.swing.JSlider EMGainSlider;
   private javax.swing.JTextField EMGainTextField;
   private javax.swing.JTextField ExposureTextField;
   private javax.swing.JComboBox GainComboBox;
   private javax.swing.JComboBox ModeComboBox;
   private javax.swing.JComboBox SpeedComboBox;
   private javax.swing.JButton jButton2;
   private javax.swing.JButton jButton3;
   private javax.swing.JCheckBox jCheckBox1;
   private javax.swing.JCheckBox jCheckBox2;
   private javax.swing.JLabel jLabel1;
   private javax.swing.JLabel jLabel2;
   private javax.swing.JLabel jLabel3;
   private javax.swing.JLabel jLabel4;
   private javax.swing.JLabel jLabel5;
   private javax.swing.JLabel jLabel6;
   private javax.swing.JLabel jLabel7;
   // End of variables declaration//GEN-END:variables


   private void setRoi (Rectangle roi) {
      boolean liveRunning = dGui_.getLiveMode();
      String currentCamera = "";
      try {
         currentCamera =  core_.getCameraDevice();
         dGui_.enableLiveMode(false);
         for(String camera: cameras_) {
            core_.setCameraDevice(camera);
            core_.setROI(roi.x, roi.y, roi.width, roi.height);
         }
         core_.setCameraDevice(currentCamera);
         dGui_.enableLiveMode(liveRunning);
      } catch (Exception ex) {
         Logger.getLogger(MultiCameraFrame.class.getName()).log(Level.SEVERE, null, ex);
      }
   }

   private void setRoi() {
      ImagePlus curImage = WindowManager.getCurrentImage();
      if (curImage == null) {
         return;
      }

      Roi roi = curImage.getRoi();

      try {
         if (roi == null) {
            // if there is no ROI, create one
            Rectangle r = curImage.getProcessor().getRoi();
            int iWidth = r.width;
            int iHeight = r.height;
            int iXROI = r.x;
            int iYROI = r.y;
            if (roi == null) {
               iWidth /= 2;
               iHeight /= 2;
               iXROI += iWidth / 2;
               iYROI += iHeight / 2;
            }

            curImage.setRoi(iXROI, iYROI, iWidth, iHeight);
            roi = curImage.getRoi();
         }

         if (roi.getType() != Roi.RECTANGLE) {
            gui_.showError("ROI must be a rectangle.\nUse the ImageJ rectangle tool to draw the ROI.");
            return;
         }

         Rectangle r = roi.getBounds();

         // Stop (and restart) live mode if it is running
         setRoi(r);

      } catch (Exception e) {
         ReportingUtils.showError(e);
      }
   }
}
