/*
 * DualAndorFrame.java
 *
 * Created on Oct 29, 2010, 11:32:29 AM
 *
 * Copyright UCSF, 2010
 *
 * Author: Nico Stuurman: nico at cmp.ucsf.edu
 *
 */
package org.micromanager.multicamera;

import java.text.ParseException;
import java.util.prefs.Preferences;

import javax.swing.DefaultComboBoxModel;

import java.awt.event.ItemEvent;
import java.util.ArrayList;

import mmcorej.CMMCore;
import mmcorej.DeviceType;
import mmcorej.StrVector;

import org.micromanager.api.ScriptInterface;
import org.micromanager.api.MMListenerInterface;
import org.micromanager.utils.NumberUtils;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author Nico Stuurman
 */
public class MultiCameraFrame extends javax.swing.JFrame implements MMListenerInterface {
   private static final long serialVersionUID = 1L;
   private final ScriptInterface gui_;
   private final CMMCore core_;
   final private Preferences prefs_;
   private int frameXPos_ = 100;
   private int frameYPos_ = 100;
   private int EMGainMin_ = 4;
   private int EMGainMax_ = 1000;
   private final String[] cameras_;
   private ArrayList<String> camerasInUse_;
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
   private static final String PHYSCAM4 = "Physical Camera 4";

   /** Creates new form MultiCameraFrame
     * @param gui - handle to instance of the ScriptInterface
     * @throws java.lang.Exception 
     */
   public MultiCameraFrame(ScriptInterface gui) throws Exception {
      gui_ = gui;
      core_ = gui_.getMMCore();
      prefs_ = Preferences.userNodeForPackage(this.getClass());

      mmcorej.StrVector cameras = core_.getLoadedDevicesOfType(DeviceType.CameraDevice);
      cameras_ = cameras.toArray();

      if (cameras_.length < 1) {
         gui_.showError("This plugin needs at least one camera");
         throw new IllegalArgumentException("This plugin needs at least one camera");
      }

      String currentCamera = core_.getCameraDevice();
      updateCamerasInUse(currentCamera);

      core_.getImageWidth();
      core_.getImageHeight();

      if (cameras_.length < 1) {
         gui_.showError("This plugin needs at least one camera");
         throw new IllegalArgumentException("This plugin needs at least one camera");
      }

      // find the first non-multi-channel camera to test properties
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

      adjustUIToCamera(testCamera);

      updateTemp();

      initialized(true, true);

      core_.setCameraDevice(currentCamera);
   }

   public void safePrefs() {
      prefs_.putInt(FRAMEXPOS, this.getX());
      prefs_.putInt(FRAMEYPOS, this.getY());

   }
   
   private void adjustUIToCamera(String testCamera) throws Exception {
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
   }

   private synchronized boolean initialized(boolean set, boolean value) {
      if (set) {
         initialized_ = value;
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
      EMGainTextField.addFocusListener(new java.awt.event.FocusAdapter() {
         public void focusLost(java.awt.event.FocusEvent evt) {
            EMGainTextFieldFocusLost(evt);
         }
      });
      EMGainTextField.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            EMGainTextFieldActionPerformed(evt);
         }
      });
      EMGainTextField.addKeyListener(new java.awt.event.KeyAdapter() {
         public void keyReleased(java.awt.event.KeyEvent evt) {
            EMGainTextFieldKeyReleased(evt);
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

      jLabel4.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      jLabel4.setText("EM Gain");

      jLabel5.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      jLabel5.setText("Mode");

      GainLabel.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      GainLabel.setText("Gain");

      speedComboBox.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      speedComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "1MHz", "3MHz", "5MHz", "10MHz" }));
      speedComboBox.addItemListener(new java.awt.event.ItemListener() {
         public void itemStateChanged(java.awt.event.ItemEvent evt) {
            speedComboBoxItemStateChanged(evt);
         }
      });

      SpeedLabel.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      SpeedLabel.setText("Speed");

      EMCheckBox.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      EMCheckBox.setText("Use");
      EMCheckBox.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            EMCheckBoxActionPerformed(evt);
         }
      });

      gainComboBox.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      gainComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "1", "2", "3", "4", "5" }));
      gainComboBox.addItemListener(new java.awt.event.ItemListener() {
         public void itemStateChanged(java.awt.event.ItemEvent evt) {
            gainComboBoxItemStateChanged(evt);
         }
      });

      FrameTransferLabel.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      FrameTransferLabel.setText("OverlapMode");

      frameTransferComboBox.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      frameTransferComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "On", "Off" }));
      frameTransferComboBox.addItemListener(new java.awt.event.ItemListener() {
         public void itemStateChanged(java.awt.event.ItemEvent evt) {
            frameTransferComboBoxItemStateChanged(evt);
         }
      });

      jLabel9.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      jLabel9.setText("Active Camera");

      cameraSelectComboBox.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      cameraSelectComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "1", "2", "4", "8" }));
      cameraSelectComboBox.addItemListener(new java.awt.event.ItemListener() {
         public void itemStateChanged(java.awt.event.ItemEvent evt) {
            cameraSelectComboBoxItemStateChanged(evt);
         }
      });

      TriggerLabel.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      TriggerLabel.setText("Trigger");

      triggerComboBox.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      triggerComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "On", "Off" }));
      triggerComboBox.addItemListener(new java.awt.event.ItemListener() {
         public void itemStateChanged(java.awt.event.ItemEvent evt) {
            triggerComboBoxItemStateChanged(evt);
         }
      });

      tempButton.setFont(new java.awt.Font("Tahoma", 0, 10)); // NOI18N
      tempButton.setText("Temp");
      tempButton.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            tempButtonActionPerformed(evt);
         }
      });

      tempLabel.setText("jLabel11");

      jLabel1.setFont(new java.awt.Font("Lucida Sans", 0, 10)); // NOI18N
      jLabel1.setText("v 1.1");

      javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
      getContentPane().setLayout(layout);
      layout.setHorizontalGroup(
         layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(layout.createSequentialGroup()
            .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
               .addGroup(layout.createSequentialGroup()
                  .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                     .addComponent(tempButton, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                     .addComponent(jLabel5)
                     .addComponent(jLabel4)
                     .addComponent(GainLabel)
                     .addComponent(SpeedLabel)
                     .addComponent(FrameTransferLabel)
                     .addComponent(TriggerLabel))
                  .addGap(48, 48, 48)
                  .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                     .addComponent(speedComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 105, javax.swing.GroupLayout.PREFERRED_SIZE)
                     .addGroup(layout.createSequentialGroup()
                        .addGap(50, 50, 50)
                        .addComponent(EMGainSlider, javax.swing.GroupLayout.PREFERRED_SIZE, 180, javax.swing.GroupLayout.PREFERRED_SIZE))
                     .addComponent(triggerComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 105, javax.swing.GroupLayout.PREFERRED_SIZE)
                     .addComponent(modeComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 105, javax.swing.GroupLayout.PREFERRED_SIZE)
                     .addComponent(gainComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 105, javax.swing.GroupLayout.PREFERRED_SIZE)
                     .addComponent(EMCheckBox)
                     .addComponent(EMGainTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 49, javax.swing.GroupLayout.PREFERRED_SIZE)
                     .addComponent(frameTransferComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 105, javax.swing.GroupLayout.PREFERRED_SIZE)
                     .addGroup(layout.createSequentialGroup()
                        .addComponent(tempLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 202, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(jLabel1))))
               .addGroup(layout.createSequentialGroup()
                  .addComponent(jLabel9)
                  .addGap(7, 7, 7)
                  .addComponent(cameraSelectComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 191, javax.swing.GroupLayout.PREFERRED_SIZE)))
            .addContainerGap())
      );
      layout.setVerticalGroup(
         layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(layout.createSequentialGroup()
            .addGap(10, 10, 10)
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
               .addComponent(jLabel9)
               .addComponent(cameraSelectComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE))
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
               .addGroup(layout.createSequentialGroup()
                  .addGap(6, 6, 6)
                  .addComponent(jLabel5)
                  .addGap(22, 22, 22)
                  .addComponent(jLabel4)
                  .addGap(16, 16, 16)
                  .addComponent(GainLabel)
                  .addGap(12, 12, 12)
                  .addComponent(SpeedLabel)
                  .addGap(12, 12, 12)
                  .addComponent(FrameTransferLabel)
                  .addGap(12, 12, 12)
                  .addComponent(TriggerLabel))
               .addGroup(layout.createSequentialGroup()
                  .addGap(90, 90, 90)
                  .addComponent(speedComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
               .addGroup(layout.createSequentialGroup()
                  .addGap(25, 25, 25)
                  .addComponent(EMGainSlider, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
               .addGroup(layout.createSequentialGroup()
                  .addGap(140, 140, 140)
                  .addComponent(triggerComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
               .addComponent(modeComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
               .addGroup(layout.createSequentialGroup()
                  .addGap(65, 65, 65)
                  .addComponent(gainComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
               .addGroup(layout.createSequentialGroup()
                  .addGap(45, 45, 45)
                  .addComponent(EMCheckBox))
               .addGroup(layout.createSequentialGroup()
                  .addGap(25, 25, 25)
                  .addComponent(EMGainTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
               .addGroup(layout.createSequentialGroup()
                  .addGap(115, 115, 115)
                  .addComponent(frameTransferComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
               .addComponent(tempButton, javax.swing.GroupLayout.PREFERRED_SIZE, 17, javax.swing.GroupLayout.PREFERRED_SIZE)
               .addComponent(tempLabel)
               .addComponent(jLabel1))
            .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
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
            setPropertyIfPossible(camera, EMGAIN, NumberUtils.intToCoreString(val));
         }
         gui_.enableLiveMode(liveRunning);
         gui_.refreshGUI();
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

   private void handleEMGainTextFieldEvent() {
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
   }
   
    private void EMGainTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_EMGainTextFieldActionPerformed
       handleEMGainTextFieldEvent();
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
             setPropertyIfPossible(camera, EMSWITCH, command);
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
       gui_.refreshGUI();
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
          gui_.refreshGUI();
       } catch (Exception ex) {
          ReportingUtils.showError(ex, MultiCameraFrame.class.getName() + " encountered an error.");
       }
    }//GEN-LAST:event_modeComboBoxItemStateChanged

    private void gainComboBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_gainComboBoxItemStateChanged
       if (!(evt.getStateChange() == ItemEvent.SELECTED)) {
          return;
       }
       setComboSelection(gainComboBox, AMPGAIN);
       gui_.refreshGUI();
    }//GEN-LAST:event_gainComboBoxItemStateChanged

    private void speedComboBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_speedComboBoxItemStateChanged
       if (!(evt.getStateChange() == ItemEvent.SELECTED)) {
          return;
       }
       setComboSelection(speedComboBox, SPEED);
       gui_.refreshGUI();
    }//GEN-LAST:event_speedComboBoxItemStateChanged

    private void frameTransferComboBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_frameTransferComboBoxItemStateChanged
       if (!(evt.getStateChange() == ItemEvent.SELECTED)) {
          return;
       }
       setComboSelection(frameTransferComboBox, FRAMETRANSFER);
       gui_.refreshGUI();
    }//GEN-LAST:event_frameTransferComboBoxItemStateChanged

    private void triggerComboBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_triggerComboBoxItemStateChanged
       if (!(evt.getStateChange() == ItemEvent.SELECTED)) {
          return;
       }
       setComboSelection(triggerComboBox, TRIGGER);
       gui_.refreshGUI();
    }//GEN-LAST:event_triggerComboBoxItemStateChanged

   private void updateCamerasInUse(String camera) {
      camerasInUse_ = new ArrayList<String>();
      if (core_.getNumberOfCameraChannels() == 1) {
         camerasInUse_.add(camera);
      } else if (core_.getNumberOfCameraChannels() > 1) {
         try {
            if (core_.hasProperty(camera, PHYSCAM1)) {
               for (String prop : new String[]{PHYSCAM1, PHYSCAM2, PHYSCAM3, PHYSCAM4}) {
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
          
          adjustUIToCamera(fsCamera);
/*
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
        */
          updateTemp();
          initialized(true, true);

          gui_.enableLiveMode(liveRunning);
       } catch (Exception ex) {
          ReportingUtils.showError(ex, MultiCameraFrame.class.getName() + " encountered an error.");
       }

       gui_.refreshGUI();
    }//GEN-LAST:event_cameraSelectComboBoxItemStateChanged

    private void modeComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_modeComboBoxActionPerformed

    }//GEN-LAST:event_modeComboBoxActionPerformed

    private void EMGainTextFieldKeyReleased(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_EMGainTextFieldKeyReleased
    
    }//GEN-LAST:event_EMGainTextFieldKeyReleased

   private void EMGainTextFieldFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_EMGainTextFieldFocusLost
       handleEMGainTextFieldEvent();
   }//GEN-LAST:event_EMGainTextFieldFocusLost

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
   public void systemConfigurationLoaded() {
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
   
   @Override
   public void exposureChanged(String string, double d) { 
   }

   @Override
   public void slmExposureChanged(String cameraName, double newExposureTime) {
   }
   
   private void setPropertyIfPossible(String device, String property, String value) {
      try {
         if (core_.hasProperty(device, property)) {
            core_.setProperty(device, property, value);
         }
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
   }

}
