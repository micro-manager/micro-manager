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
   private static final String MODE = "Output_Amplifier";
   private static final String MODECONV16 = "Conventional-16bit";
   private static final String MODEEM14 = "EM-14bit";
   private static final String MODEEM16 = "EM-16bit";
   private static final String EMMODE = "Electron Multiplying";
   private static final String NORMALMODE = "Conventional";
   private static final String ADCONVERTER = "AD_Converter";
   private static final String AD14BIT = "1. 14bit";
   private static final String AD16BIT = "2. 16bit";
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
          ModeComboBox.addItem(MODECONV16);
          ModeComboBox.addItem(MODEEM14);
          ModeComboBox.addItem(MODEEM16);
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

      jLabel1.setFont(new java.awt.Font("Lucida Grande", 1, 12));
      jLabel1.setText("ROI");
      jLabel1.setMaximumSize(null);
      jLabel1.setMinimumSize(null);
      jLabel1.setPreferredSize(null);

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

      jLabel2.setFont(new java.awt.Font("Lucida Grande", 0, 12));
      jLabel2.setText("Exposure [ms]");

      jLabel3.setFont(new java.awt.Font("Lucida Grande", 0, 12));
      jLabel3.setText("Binning");

      BinningComboBox.setFont(new java.awt.Font("Lucida Grande", 0, 12));
      BinningComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "1", "2", "4", "8" }));
      BinningComboBox.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            BinningComboBoxActionPerformed(evt);
         }
      });

      ExposureTextField.setFont(new java.awt.Font("Lucida Grande", 0, 12));
      ExposureTextField.setText("10");
      ExposureTextField.setToolTipText("Exposure time in ms");
      ExposureTextField.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            ExposureTextFieldActionPerformed(evt);
         }
      });

      EMGainSlider.addMouseListener(new java.awt.event.MouseAdapter() {
         public void mouseReleased(java.awt.event.MouseEvent evt) {
            EMGainSliderMouseReleased(evt);
         }
      });
      EMGainSlider.addChangeListener(new javax.swing.event.ChangeListener() {
         public void stateChanged(javax.swing.event.ChangeEvent evt) {
            EMGainSliderStateChanged(evt);
         }
      });

      EMGainTextField.setFont(new java.awt.Font("Lucida Grande", 0, 10));
      EMGainTextField.setText("4");
      EMGainTextField.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            EMGainTextFieldActionPerformed(evt);
         }
      });

      ModeComboBox.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      ModeComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "EM", "Conventional" }));
      ModeComboBox.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            ModeComboBoxActionPerformed(evt);
         }
      });

      jLabel4.setFont(new java.awt.Font("Lucida Grande", 0, 10));
      jLabel4.setText("EM Gain");

      jLabel5.setFont(new java.awt.Font("Lucida Grande", 0, 10));
      jLabel5.setText("Mode");

      jLabel6.setFont(new java.awt.Font("Lucida Grande", 0, 10));
      jLabel6.setText("Gain");

      SpeedComboBox.setFont(new java.awt.Font("Lucida Grande", 0, 10));
      SpeedComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "1MHz", "3MHz", "5MHz", "10MHz" }));
      SpeedComboBox.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            SpeedComboBoxActionPerformed(evt);
         }
      });

      jLabel7.setFont(new java.awt.Font("Lucida Grande", 0, 10));
      jLabel7.setText("Speed");

      jCheckBox2.setFont(new java.awt.Font("Lucida Grande", 0, 10));
      jCheckBox2.setText("Use");
      jCheckBox2.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            jCheckBox2ActionPerformed(evt);
         }
      });

      GainComboBox.setFont(new java.awt.Font("Lucida Grande", 0, 10));
      GainComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "1", "2", "3", "4", "5" }));
      GainComboBox.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            GainComboBoxActionPerformed(evt);
         }
      });

      org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(getContentPane());
      getContentPane().setLayout(layout);
      layout.setHorizontalGroup(
         layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
         .add(layout.createSequentialGroup()
            .add(10, 10, 10)
            .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
               .add(layout.createSequentialGroup()
                  .add(jLabel2)
                  .add(7, 7, 7)
                  .add(ExposureTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 70, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                  .add(60, 60, 60)
                  .add(jLabel1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
               .add(layout.createSequentialGroup()
                  .add(jLabel3)
                  .add(49, 49, 49)
                  .add(BinningComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 80, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                  .add(50, 50, 50)
                  .add(jButton2, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                  .add(8, 8, 8)
                  .add(jButton3, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
               .add(layout.createSequentialGroup()
                  .add(jLabel5)
                  .add(63, 63, 63)
                  .add(ModeComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 105, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
               .add(layout.createSequentialGroup()
                  .add(jLabel4)
                  .add(51, 51, 51)
                  .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                     .add(jCheckBox2)
                     .add(EMGainTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 49, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                  .add(EMGainSlider, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 180, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
               .add(layout.createSequentialGroup()
                  .add(jLabel6)
                  .add(68, 68, 68)
                  .add(GainComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 105, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
               .add(layout.createSequentialGroup()
                  .add(jLabel7)
                  .add(61, 61, 61)
                  .add(SpeedComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 105, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))))
      );
      layout.setVerticalGroup(
         layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
         .add(layout.createSequentialGroup()
            .add(10, 10, 10)
            .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
               .add(layout.createSequentialGroup()
                  .add(10, 10, 10)
                  .add(jLabel2))
               .add(ExposureTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
               .add(layout.createSequentialGroup()
                  .add(10, 10, 10)
                  .add(jLabel1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 10, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
            .add(3, 3, 3)
            .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
               .add(layout.createSequentialGroup()
                  .add(10, 10, 10)
                  .add(jLabel3))
               .add(BinningComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 23, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
               .add(jButton2, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 20, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
               .add(jButton3, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 20, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
            .add(5, 5, 5)
            .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
               .add(layout.createSequentialGroup()
                  .add(10, 10, 10)
                  .add(jLabel5))
               .add(ModeComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
            .add(3, 3, 3)
            .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
               .add(layout.createSequentialGroup()
                  .add(20, 20, 20)
                  .add(jLabel4))
               .add(layout.createSequentialGroup()
                  .add(20, 20, 20)
                  .add(jCheckBox2))
               .add(EMGainTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
               .add(EMGainSlider, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
            .add(7, 7, 7)
            .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
               .add(layout.createSequentialGroup()
                  .add(10, 10, 10)
                  .add(jLabel6))
               .add(GainComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
            .add(3, 3, 3)
            .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
               .add(layout.createSequentialGroup()
                  .add(10, 10, 10)
                  .add(jLabel7))
               .add(SpeedComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
            .addContainerGap(20, Short.MAX_VALUE))
      );

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
                if (mode.equals(MODEEM14)) {
                   core_.setProperty(camera, ADCONVERTER, AD14BIT);
                   core_.setProperty(camera, MODE, EMMODE);
                } else if (mode.equals(MODEEM16)) {
                   core_.setProperty(camera, ADCONVERTER, AD16BIT);
                   core_.setProperty(camera, MODE, EMMODE);
                } else if (mode.equals(MODECONV16)) {
                   core_.setProperty(camera, ADCONVERTER, AD16BIT);
                   core_.setProperty(camera, MODE, NORMALMODE);
                } 
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

    private void EMGainSliderMouseReleased(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_EMGainSliderMouseReleased
       if (!initialized(false, false))
          return;
       boolean liveRunning = dGui_.getLiveMode();
       int val = EMGainSlider.getValue();
       // Stop live mode for EMGAin change?
       try {
          dGui_.enableLiveMode(false);
          for (String camera: cameras_) {
             if (!camera.equals("")) {
                core_.setProperty(camera, EMGAIN, NumberUtils.intToCoreString(val));
             }
          }
          dGui_.enableLiveMode(liveRunning);
          dGui_.updateGUI(false);
       } catch(Exception ex) {
          ReportingUtils.showError(ex, MultiCameraFrame.class.getName() + " encountered an error.");
       }
       EMGainTextField.setText(NumberUtils.intToDisplayString(val));
    }//GEN-LAST:event_EMGainSliderMouseReleased

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
