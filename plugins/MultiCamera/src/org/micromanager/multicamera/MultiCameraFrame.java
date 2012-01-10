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

import java.text.NumberFormat;
import java.text.ParseException;
import java.util.prefs.Preferences;

import javax.swing.DefaultComboBoxModel;

import java.awt.event.ItemEvent;

import java.util.Vector;
import mmcorej.CMMCore;
import mmcorej.DeviceType;
import mmcorej.StrVector;

import org.micromanager.api.ScriptInterface;
import org.micromanager.api.DeviceControlGUI;
import org.micromanager.api.MMListenerInterface;
import org.micromanager.utils.NumberUtils;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author Nico Stuurman
 */
public class MultiCameraFrame extends javax.swing.JFrame implements MMListenerInterface {

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
   private Vector<String> camerasInUse_;
   private String coreCamera_;
   private boolean initialized_ = false;
   private static final String MIXED = "";
   private static final int MIXEDINT = -1;
   private static final String FRAMEXPOS = "FRAMEXPOS";
   private static final String FRAMEYPOS = "FRAMEYPOS";
   private static final String MODE = "Output_Amplifier";
   private static final String MODECONV16 = "Conventional-16bit";
   private static final String MODEEM14 = "EM-14bit";
   private static final String MODEEM16 = "EM-16bit";
   private static final String MODECONV = "Conventional";
   private static final String MODEEM = "EM";
   private static final String EMMODE = "Electron Multiplying";
   private static final String NORMALMODE = "Conventional";
   private static final String ADCONVERTER = "AD_Converter";
   private static final String AD14BIT = "1. 14bit";
   private static final String AD16BIT = "2. 16bit";
   private static final String EMGAIN = "Gain";
   private static final String EMSWITCH = "EMSwitch";
   private static final String AMPGAIN = "Pre-Amp-Gain";
   private static final String FRAMETRANSFER = "FrameTransfer";
   // Andor calls this readout mode
   private static final String SPEED = "ReadoutMode";
   private static final String TRIGGER = "Trigger";
   private static final String TEMP = "CCDTemperature";
   private static final String PHYSCAM1 = "Physical Camera 1";
   private static final String PHYSCAM2 = "Physical Camera 2";
   private static final String PHYSCAM3 = "Physical Camera 3";

   /** Creates new form MultiCameraFrame */
   public MultiCameraFrame(ScriptInterface gui) throws Exception {
      gui_ = gui;
      dGui_ = (DeviceControlGUI) gui_;
      core_ = gui_.getMMCore();
      nf_ = NumberFormat.getInstance();
      prefs_ = Preferences.userNodeForPackage(this.getClass());

      mmcorej.StrVector cameras = core_.getLoadedDevicesOfType(DeviceType.CameraDevice);
      cameras_ = cameras.toArray();

      if (cameras_.length < 1) {
         gui_.showError("This plugin needs at least one camera");
         throw new IllegalArgumentException("This plugin needs at least one camera");
      }

      String currentCamera = core_.getCameraDevice();
      updateCamerasInUse(currentCamera);

      imageWidth_ = core_.getImageWidth();
      imageHeight_ = core_.getImageHeight();

      if (cameras_.length < 1) {
         gui_.showError("This plugin needs at least one camera");
         throw new IllegalArgumentException("This plugin needs at least one camera");
      }

      // find the first non-multi-channel camera to test properties
      // TODO: all non-multi-channel cameras and the cameras contained in a multi-
      // channel camera should be tested
      String testCamera = camerasInUse_.get(0);

      frameXPos_ = prefs_.getInt(FRAMEXPOS, frameXPos_);
      frameYPos_ = prefs_.getInt(FRAMEYPOS, frameYPos_);

      initComponents();

      setLocation(frameXPos_, frameYPos_);

      setBackground(gui_.getBackgroundColor());

      cameraSelectComboBox.removeAllItems();

      for (String camera : cameras_) {
         cameraSelectComboBox.addItem(camera);
      }

      cameraSelectComboBox.setSelectedItem(currentCamera);
      if (cameras_.length <= 1) {
         cameraSelectComboBox.setEnabled(false);
      }

      if (!core_.hasProperty(testCamera, MODE)) {
         jLabel5.setEnabled(false);
         modeComboBox.setEnabled(false);
      } else {
         modeComboBox.removeAllItems();
         modeComboBox.addItem(MIXED);
         if (core_.hasProperty(testCamera, ADCONVERTER)) {
            modeComboBox.addItem(MODECONV16);
            modeComboBox.addItem(MODEEM14);
            modeComboBox.addItem(MODEEM16);
         } else {
            modeComboBox.addItem(MODECONV);
            modeComboBox.addItem(MODEEM);
         }
         modeComboBox.setSelectedItem(getMode());
      }

      if (!core_.hasProperty(testCamera, EMGAIN)) {
         jLabel4.setEnabled(false);
         EMGainTextField.setEnabled(false);
         EMGainSlider.setEnabled(false);
      } else {
         EMGainMin_ = (int) core_.getPropertyLowerLimit(testCamera, EMGAIN);
         EMGainMax_ = (int) core_.getPropertyUpperLimit(testCamera, EMGAIN);
         EMGainSlider.setMinimum(EMGainMin_);
         EMGainSlider.setMaximum(EMGainMax_);
         int gain = NumberUtils.coreStringToInt(core_.getProperty(testCamera, EMGAIN));
         EMGainSlider.setValue(gain);
         EMGainTextField.setText(NumberUtils.intToDisplayString(gain));

         if (!core_.hasProperty(testCamera, EMSWITCH)) {
            EMCheckBox.setEnabled(false);
         } else {
            String val = core_.getProperty(testCamera, EMSWITCH);
            if (val.equals("On")) {
               EMCheckBox.setSelected(true);
            }
         }
      }

      // Pre-amp Gain
      if (!core_.hasProperty(testCamera, AMPGAIN)) {
         GainLabel.setEnabled(false);
         gainComboBox.setEnabled(false);
      } else {
         updateItems(gainComboBox, AMPGAIN);
      }

      // Readout speed
      if (!core_.hasProperty(testCamera, SPEED)) {
         SpeedLabel.setEnabled(false);
         speedComboBox.setEnabled(false);
      } else {
         updateItems(speedComboBox, SPEED);
      }

      // Frame Transfer
      if (!core_.hasProperty(testCamera, FRAMETRANSFER)) {
         FrameTransferLabel.setEnabled(false);
         frameTransferComboBox.setEnabled(false);
      } else {
         updateItems(frameTransferComboBox, FRAMETRANSFER);
      }

      // Trigger
      if (!core_.hasProperty(testCamera, TRIGGER)) {
         TriggerLabel.setEnabled(false);
         triggerComboBox.setEnabled(false);
      } else {
         updateItems(triggerComboBox, TRIGGER);
      }

      updateTemp();

      initialized(true, true);
      
      gui_.addMMListener(this);

      core_.setCameraDevice(currentCamera);
   }

   public void safePrefs() {
      prefs_.putInt(FRAMEXPOS, this.getX());
      prefs_.putInt(FRAMEYPOS, this.getY());

   }

   private synchronized boolean initialized(boolean set, boolean value) {
      if (set) {
         initialized_ = value;
      } else {
         value = initialized_;
      }
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
        EMGainSlider = new javax.swing.JSlider();
        EMGainTextField = new javax.swing.JTextField();
        modeComboBox = new javax.swing.JComboBox();
        jLabel4 = new javax.swing.JLabel();
        jLabel5 = new javax.swing.JLabel();
        GainLabel = new javax.swing.JLabel();
        speedComboBox = new javax.swing.JComboBox();
        SpeedLabel = new javax.swing.JLabel();
        EMCheckBox = new javax.swing.JCheckBox();
        gainComboBox = new javax.swing.JComboBox();
        FrameTransferLabel = new javax.swing.JLabel();
        frameTransferComboBox = new javax.swing.JComboBox();
        jLabel9 = new javax.swing.JLabel();
        cameraSelectComboBox = new javax.swing.JComboBox();
        TriggerLabel = new javax.swing.JLabel();
        triggerComboBox = new javax.swing.JComboBox();
        tempButton = new javax.swing.JButton();
        tempLabel = new javax.swing.JLabel();
        jLabel1 = new javax.swing.JLabel();

        jCheckBox1.setText("jCheckBox1");

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("Andor Control");
        setResizable(false);

        EMGainSlider.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                EMGainSliderMouseReleased(evt);
            }
        });

        EMGainTextField.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
        EMGainTextField.setText("4");
        EMGainTextField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                EMGainTextFieldActionPerformed(evt);
            }
        });

        modeComboBox.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
        modeComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "EM", "Conventional" }));
        modeComboBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                modeComboBoxItemStateChanged(evt);
            }
        });
        modeComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                modeComboBoxActionPerformed(evt);
            }
        });

        jLabel4.setFont(new java.awt.Font("Lucida Grande", 0, 10));
        jLabel4.setText("EM Gain");

        jLabel5.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
        jLabel5.setText("Mode");

        GainLabel.setFont(new java.awt.Font("Lucida Grande", 0, 10));
        GainLabel.setText("Gain");

        speedComboBox.setFont(new java.awt.Font("Lucida Grande", 0, 10));
        speedComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "1MHz", "3MHz", "5MHz", "10MHz" }));
        speedComboBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                speedComboBoxItemStateChanged(evt);
            }
        });

        SpeedLabel.setFont(new java.awt.Font("Lucida Grande", 0, 10));
        SpeedLabel.setText("Speed");

        EMCheckBox.setFont(new java.awt.Font("Lucida Grande", 0, 10));
        EMCheckBox.setText("Use");
        EMCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                EMCheckBoxActionPerformed(evt);
            }
        });

        gainComboBox.setFont(new java.awt.Font("Lucida Grande", 0, 10));
        gainComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "1", "2", "3", "4", "5" }));
        gainComboBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                gainComboBoxItemStateChanged(evt);
            }
        });

        FrameTransferLabel.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
        FrameTransferLabel.setText("FrameTransfer");

        frameTransferComboBox.setFont(new java.awt.Font("Lucida Grande", 0, 10));
        frameTransferComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "On", "Off" }));
        frameTransferComboBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                frameTransferComboBoxItemStateChanged(evt);
            }
        });

        jLabel9.setFont(new java.awt.Font("Lucida Grande", 0, 10));
        jLabel9.setText("Active Camera");

        cameraSelectComboBox.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
        cameraSelectComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "1", "2", "4", "8" }));
        cameraSelectComboBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                cameraSelectComboBoxItemStateChanged(evt);
            }
        });

        TriggerLabel.setFont(new java.awt.Font("Lucida Grande", 0, 10));
        TriggerLabel.setText("Trigger");

        triggerComboBox.setFont(new java.awt.Font("Lucida Grande", 0, 10));
        triggerComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "On", "Off" }));
        triggerComboBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                triggerComboBoxItemStateChanged(evt);
            }
        });

        tempButton.setFont(new java.awt.Font("Tahoma", 0, 10));
        tempButton.setText("Temp");
        tempButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                tempButtonActionPerformed(evt);
            }
        });

        tempLabel.setText("jLabel11");

        jLabel1.setFont(new java.awt.Font("Lucida Sans", 0, 10)); // NOI18N
        jLabel1.setText("v 1.0");

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(layout.createSequentialGroup()
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(tempButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 63, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                            .add(jLabel5)
                            .add(jLabel4)
                            .add(GainLabel)
                            .add(SpeedLabel)
                            .add(FrameTransferLabel)
                            .add(TriggerLabel))
                        .add(48, 48, 48)
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING, false)
                            .add(speedComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 105, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                            .add(layout.createSequentialGroup()
                                .add(50, 50, 50)
                                .add(EMGainSlider, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 180, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                            .add(triggerComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 105, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                            .add(modeComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 105, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                            .add(gainComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 105, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                            .add(EMCheckBox)
                            .add(EMGainTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 49, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                            .add(frameTransferComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 105, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                            .add(layout.createSequentialGroup()
                                .add(tempLabel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 202, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .add(jLabel1))))
                    .add(layout.createSequentialGroup()
                        .add(jLabel9)
                        .add(7, 7, 7)
                        .add(cameraSelectComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 191, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .add(10, 10, 10)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel9)
                    .add(cameraSelectComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 23, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(layout.createSequentialGroup()
                        .add(6, 6, 6)
                        .add(jLabel5)
                        .add(22, 22, 22)
                        .add(jLabel4)
                        .add(16, 16, 16)
                        .add(GainLabel)
                        .add(12, 12, 12)
                        .add(SpeedLabel)
                        .add(12, 12, 12)
                        .add(FrameTransferLabel)
                        .add(12, 12, 12)
                        .add(TriggerLabel))
                    .add(layout.createSequentialGroup()
                        .add(90, 90, 90)
                        .add(speedComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                    .add(layout.createSequentialGroup()
                        .add(25, 25, 25)
                        .add(EMGainSlider, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                    .add(layout.createSequentialGroup()
                        .add(140, 140, 140)
                        .add(triggerComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                    .add(modeComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(layout.createSequentialGroup()
                        .add(65, 65, 65)
                        .add(gainComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                    .add(layout.createSequentialGroup()
                        .add(45, 45, 45)
                        .add(EMCheckBox))
                    .add(layout.createSequentialGroup()
                        .add(25, 25, 25)
                        .add(EMGainTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                    .add(layout.createSequentialGroup()
                        .add(115, 115, 115)
                        .add(frameTransferComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(tempButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 17, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(tempLabel)
                    .add(jLabel1))
                .addContainerGap(12, Short.MAX_VALUE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

   /*
    * If all selected cameras have the same EM Gain value, return EM Gain value
    * otherwise return -1
    */
   private int getEMGain() {
      int gain = MIXEDINT;
      try {
         for (String camera : camerasInUse_) {
            int tmpGain = NumberUtils.coreStringToInt(core_.getProperty(camera, EMGAIN));
            if (gain == -1) {
               gain = tmpGain;
            }
            if (tmpGain != gain) {
               return MIXEDINT;
            }
         }
      } catch (Exception ex) {
         ReportingUtils.showError(ex, MultiCameraFrame.class.getName() + " encountered an error.");
      }
      return gain;
   }

   private void setEMGain() {
      if (!initialized(false, false)) {
         return;
      }
      boolean liveRunning = gui_.isLiveModeOn();
      int val = EMGainSlider.getValue();
      try {
         gui_.enableLiveMode(false);
         for (String camera : camerasInUse_) {
            core_.setProperty(camera, EMGAIN, NumberUtils.intToCoreString(val));
         }
         gui_.enableLiveMode(liveRunning);
         dGui_.updateGUI(false);
      } catch (Exception ex) {
         ReportingUtils.showError(ex, MultiCameraFrame.class.getName() + " encountered an error.");
      }
      EMGainTextField.setText(NumberUtils.intToDisplayString(val));
   }

   /*
    * Signals whether the first selected camera has its EM switch on or off
    */
   private boolean getEMSwitch() {
      try {
         for (String camera : camerasInUse_) {
            if (core_.hasProperty(camera, EMSWITCH)) {
               String OnOff = core_.getProperty(camera, EMSWITCH);
               if (OnOff.equals("On")) {
                  return true;
               }
            } else {
               return false;
            }

         }
      } catch (Exception ex) {
         ReportingUtils.showError(ex, MultiCameraFrame.class.getName() + " encountered an error.");
      }
      return false;
   }

    private void EMGainTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_EMGainTextFieldActionPerformed
       if (!initialized(false, false)) {
          return;
       }
       try {
          int val = NumberUtils.displayStringToInt(EMGainTextField.getText());
          if (val > EMGainMax_) {
             val = EMGainMax_;
          }
          if (val < EMGainMin_) {
             val = EMGainMin_;
          }
          EMGainSlider.setEnabled(true);
          EMGainSlider.setValue(val);
          setEMGain();
       } catch (ParseException e) {
          // ignore if the user types garbage
       }
    }//GEN-LAST:event_EMGainTextFieldActionPerformed

    private void EMCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_EMCheckBoxActionPerformed
       // EM enable
       if (!initialized(false, false)) {
          return;
       }
       boolean liveRunning = gui_.isLiveModeOn();
       boolean on = EMCheckBox.isSelected();
       String command = "Off";
       if (on) {
          command = "On";
       }
       try {
          gui_.enableLiveMode(false);
          for (String camera : camerasInUse_) {
             core_.setProperty(camera, EMSWITCH, command);
          }
          gui_.enableLiveMode(liveRunning);
       } catch (Exception ex) {
          ReportingUtils.showError(ex, MultiCameraFrame.class.getName() + " encountered an error.");
       }
    }//GEN-LAST:event_EMCheckBoxActionPerformed

   private String getMode() {
      String mode = "";
      try {
         String fs = camerasInUse_.get(0);
         String modeProp = core_.getProperty(fs, MODE);
         if (core_.hasProperty(fs, ADCONVERTER)) {
            String adProp = core_.getProperty(fs, ADCONVERTER);
            if (modeProp.equals(EMMODE)) {
               if (adProp.equals(AD14BIT)) {
                  mode = MODEEM14;
               } else if (adProp.equals(AD16BIT)) {
                  mode = MODEEM16;
               }
            } else if (modeProp.equals(NORMALMODE)) {
               if (adProp.equals(AD16BIT)) {
                  mode = MODECONV16;
               }
            }
            for (String camera : camerasInUse_) {
               String mP = core_.getProperty(camera, MODE);
               String aP = core_.getProperty(camera, ADCONVERTER);
               if (!mP.equals(modeProp)) {
                  mode = MIXED;
               }
               if (!aP.equals(adProp)) {
                  mode = MIXED;
               }

            }
         } else {  // No AD Converter
            if (modeProp.equals(EMMODE)) {
               mode = MODEEM;
            } else {
               mode = MODECONV;
            }
            for (String camera : camerasInUse_) {
               String mP = core_.getProperty(camera, MODE);
               if (!mP.equals(modeProp)) {
                  mode = MIXED;
               }

            }
         }
      } catch (Exception ex) {
         ReportingUtils.showError(ex, MultiCameraFrame.class.getName() + " encountered an error.");
      }
      return mode;
   }

    private void EMGainSliderMouseReleased(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_EMGainSliderMouseReleased
       setEMGain();
       dGui_.updateGUI(false);
    }//GEN-LAST:event_EMGainSliderMouseReleased

    private void tempButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_tempButtonActionPerformed
       boolean liveRunning = gui_.isLiveModeOn();
       gui_.enableLiveMode(false);
       updateTemp();
       gui_.enableLiveMode(liveRunning);
    }//GEN-LAST:event_tempButtonActionPerformed

    private void modeComboBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_modeComboBoxItemStateChanged
       // Combo box selecting readout mode (EM/standard)
       if (!(evt.getStateChange() == ItemEvent.SELECTED)) {
          return;
       }
       if (!initialized(false, false)) {
          return;
       }

       Object item = modeComboBox.getSelectedItem();
       if (item.equals(MIXED)) {
          return;
       }

       boolean liveRunning = gui_.isLiveModeOn();
       String mode = item.toString();
       try {
          gui_.enableLiveMode(false);
          for (String camera : camerasInUse_) {
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

          updateItems(gainComboBox, AMPGAIN);
          updateItems(speedComboBox, SPEED);

          gui_.enableLiveMode(liveRunning);
          dGui_.updateGUI(false);
       } catch (Exception ex) {
          ReportingUtils.showError(ex, MultiCameraFrame.class.getName() + " encountered an error.");
       }
    }//GEN-LAST:event_modeComboBoxItemStateChanged

    private void gainComboBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_gainComboBoxItemStateChanged
       if (!(evt.getStateChange() == ItemEvent.SELECTED)) {
          return;
       }
       setComboSelection(gainComboBox, AMPGAIN);
       dGui_.updateGUI(false);
    }//GEN-LAST:event_gainComboBoxItemStateChanged

    private void speedComboBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_speedComboBoxItemStateChanged
       if (!(evt.getStateChange() == ItemEvent.SELECTED)) {
          return;
       }
       setComboSelection(speedComboBox, SPEED);
       dGui_.updateGUI(false);
    }//GEN-LAST:event_speedComboBoxItemStateChanged

    private void frameTransferComboBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_frameTransferComboBoxItemStateChanged
       if (!(evt.getStateChange() == ItemEvent.SELECTED)) {
          return;
       }
       setComboSelection(frameTransferComboBox, FRAMETRANSFER);
       dGui_.updateGUI(false);
    }//GEN-LAST:event_frameTransferComboBoxItemStateChanged

    private void triggerComboBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_triggerComboBoxItemStateChanged
       if (!(evt.getStateChange() == ItemEvent.SELECTED)) {
          return;
       }
       setComboSelection(triggerComboBox, TRIGGER);
       dGui_.updateGUI(false);
    }//GEN-LAST:event_triggerComboBoxItemStateChanged

   private void updateCamerasInUse(String camera) {
      camerasInUse_ = new Vector<String>();
      if (core_.getNumberOfCameraChannels() == 1) {
         camerasInUse_.add(camera);
      } else if (core_.getNumberOfCameraChannels() > 1) {
         try {
            if (core_.hasProperty(camera, PHYSCAM1)) {
               for (String prop : new String[]{PHYSCAM1, PHYSCAM2, PHYSCAM3}) {
                  if (core_.hasProperty(camera, prop)) {
                     String cam = core_.getProperty(camera, prop);
                     if (!cam.equals("Undefined")) {
                        camerasInUse_.add(cam);
                     }
                  }
               }
            } else {
               camerasInUse_.add(coreCamera_);
            }
         } catch (Exception ex) {
         }
      }
   }

    private void cameraSelectComboBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_cameraSelectComboBoxItemStateChanged
       if (!(evt.getStateChange() == ItemEvent.SELECTED)) {
          return;
       }
       if (!initialized(false, false)) {
          return;
       }

       boolean liveRunning = gui_.isLiveModeOn();
       gui_.enableLiveMode(false);

       try {
          // Use the initialize flag to prevent pushing settings back to the hardware
          initialized(true, false);

          coreCamera_ = (String) cameraSelectComboBox.getSelectedItem();
          core_.setProperty("Core", "Camera", coreCamera_);
          updateCamerasInUse(coreCamera_);

          String fsCamera = camerasInUse_.get(0);

          if (core_.hasProperty(fsCamera, MODE)) {
             modeComboBox.setSelectedItem(getMode());
          }
          if (core_.hasProperty(fsCamera, EMGAIN)) {
             int gain = getEMGain();
             EMGainSlider.setValue(gain);
             if (gain == MIXEDINT) {
                EMGainTextField.setText(MIXED);
                EMGainSlider.setEnabled(false);
             } else {
                EMGainTextField.setText(NumberUtils.intToDisplayString(gain));
                EMGainSlider.setEnabled(true);
                EMGainSlider.setValue(gain);
             }
          }
          if (core_.hasProperty(fsCamera, EMSWITCH)) {
             EMCheckBox.setSelected(getEMSwitch());
          }
          if (core_.hasProperty(fsCamera, AMPGAIN)) {
             getComboSelection(gainComboBox, AMPGAIN);
          }
          if (core_.hasProperty(fsCamera, SPEED)) {
             getComboSelection(speedComboBox, SPEED);
          }
          if (core_.hasProperty(fsCamera, FRAMETRANSFER)) {
             getComboSelection(frameTransferComboBox, FRAMETRANSFER);
          }
          if (core_.hasProperty(fsCamera, TRIGGER)) {
             getComboSelection(triggerComboBox, TRIGGER);
          }
          updateTemp();
          initialized(true, true);

          gui_.enableLiveMode(liveRunning);
       } catch (Exception ex) {
          ReportingUtils.showError(ex, MultiCameraFrame.class.getName() + " encountered an error.");
       }

       dGui_.updateGUI(false);
    }//GEN-LAST:event_cameraSelectComboBoxItemStateChanged

    private void modeComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_modeComboBoxActionPerformed
       // TODO add your handling code here:
    }//GEN-LAST:event_modeComboBoxActionPerformed

   private void updateTemp() {
      String tempText = "";
      try {
         for (String camera : camerasInUse_) {
            if (core_.hasProperty(camera, TEMP)) {
               tempText += core_.getProperty(camera, TEMP) + "\u00b0" + "C   ";
            }

         }
      } catch (Exception ex) {
         ReportingUtils.showError(ex, MultiCameraFrame.class.getName() + " encountered an error.");
      }
      tempLabel.setText(tempText);
   }

   private void updateItems(javax.swing.JComboBox comboBox, String property) {
      if (comboBox != null) {
         try {
            String camera = camerasInUse_.get(0);
            StrVector vals = core_.getAllowedPropertyValues(camera, property);

            String[] newVals = new String[(int) vals.size() + 1];
            newVals[0] = MIXED;
            for (int i = 0; i < vals.size(); i++) {
               newVals[i + 1] = vals.get(i);
            }
            comboBox.setModel(new DefaultComboBoxModel(newVals));

         } catch (Exception ex) {
            ReportingUtils.showError(ex, MultiCameraFrame.class.getName() + " encountered an error.");
         }
         getComboSelection(comboBox, property);
      }
   }

   private void getComboSelection(javax.swing.JComboBox comboBox, String property) {
      if (comboBox == null || !comboBox.isEnabled()) {
         return;
      }
      try {
         String fs = camerasInUse_.get(0);
         String val = core_.getProperty(fs, property);
         for (String camera : camerasInUse_) {
            String tVal = core_.getProperty(camera, property);
            if (!tVal.equals(val)) {
               comboBox.setSelectedItem(MIXED);
               return;
            }

         }
         comboBox.setSelectedItem(val);
      } catch (Exception ex) {
         ReportingUtils.showError(ex, MultiCameraFrame.class.getName() + " encountered an error.");
      }
   }

   private void setComboSelection(javax.swing.JComboBox comboBox, String property) {
      if (!initialized(false, false)) {
         return;
      }
      boolean liveRunning = gui_.isLiveModeOn();
      gui_.enableLiveMode(false);
      String val = (String) comboBox.getSelectedItem();
      if (val.equals(MIXED)) {
         getComboSelection(comboBox, property);
         return;
      }
      try {
         for (String camera : camerasInUse_) {
            core_.setProperty(camera, property, val);
         }

         gui_.enableLiveMode(liveRunning);
      } catch (Exception ex) {
         ReportingUtils.showError(ex, MultiCameraFrame.class.getName() + " encountered an error.");
      }
   }
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JCheckBox EMCheckBox;
    private javax.swing.JSlider EMGainSlider;
    private javax.swing.JTextField EMGainTextField;
    private javax.swing.JLabel FrameTransferLabel;
    private javax.swing.JLabel GainLabel;
    private javax.swing.JLabel SpeedLabel;
    private javax.swing.JLabel TriggerLabel;
    private javax.swing.JComboBox cameraSelectComboBox;
    private javax.swing.JComboBox frameTransferComboBox;
    private javax.swing.JComboBox gainComboBox;
    private javax.swing.JCheckBox jCheckBox1;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JComboBox modeComboBox;
    private javax.swing.JComboBox speedComboBox;
    private javax.swing.JButton tempButton;
    private javax.swing.JLabel tempLabel;
    private javax.swing.JComboBox triggerComboBox;
    // End of variables declaration//GEN-END:variables

 
   @Override
   public void propertiesChangedAlert() {
      // updateItems(modeComboBox, MODE);
      updateItems(speedComboBox, SPEED);
   }

   @Override
   public void propertyChangedAlert(String device, String property, String value) {
      try {
         if (device.equals("Core") && property.equals("Camera") )
            cameraSelectComboBox.setSelectedItem(value);
      } catch (Exception ex) {
      }
   }

   @Override
   public void configGroupChangedAlert(String groupName, String newConfig) {
   }

   @Override
   public void pixelSizeChangedAlert(double newPixelSizeUm) {
   }

   @Override
   public void stagePositionChangedAlert(String deviceName, double pos) {
      
   }

   @Override
   public void xyStagePositionChanged(String deviceName, double xPos, double yPos) {
   }
}
