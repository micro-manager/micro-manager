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

import javax.swing.DefaultComboBoxModel;

import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.Roi;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

import mmcorej.CMMCore;
import mmcorej.DeviceType;
import mmcorej.MMCoreJ;
import mmcorej.StrVector;
import mmcorej.TaggedImage;
import org.json.JSONObject;

import org.micromanager.api.ScriptInterface;
import org.micromanager.api.DeviceControlGUI;
import org.micromanager.api.MMListenerInterface;
import org.micromanager.utils.MDUtils;
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
   private HashMap<String, Boolean> selectedCameras_;
   private String activeCamera_;
   private boolean initialized_ = false;

   private static final String ACQNAME = "MultiColorAcq";

   private static final String SEPERATOR = ", ";
   private static final String MIXED = "";
   private static final int MIXEDINT = -1;

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
   private static final String FRAMETRANSFER = "FrameTransfer";
   // Retarded, but Andor calls this readout mode
   private static final String SPEED = "ReadoutMode";
   private static final String TRIGGER = "Trigger";
   private static final String TEMP = "CCDTemperature";


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
       selectedCameras_ = new HashMap<String, Boolean>();

       String currentCamera = core_.getCameraDevice();
       imageWidth_ = core_.getImageWidth();
       imageHeight_ = core_.getImageHeight();
       for (String camera : cameras_) {
          if (!camera.equals(currentCamera)) {
             core_.setCameraDevice(camera);
             if (imageWidth_ != core_.getImageWidth() ||
                 imageHeight_ != core_.getImageHeight()) {
                throw new IllegalArgumentException("This plugin only works with cameras of identical size");
             }
          }
       }

       frameXPos_ = prefs_.getInt(FRAMEXPOS, frameXPos_);
       frameYPos_ = prefs_.getInt(FRAMEYPOS, frameYPos_);

       initComponents();

       setLocation(frameXPos_, frameYPos_);

       setBackground(gui_.getBackgroundColor());

       if (cameras_.length <= 1) {
          CameraSelectComboBox.setEnabled(false);
       } else {
          CameraSelectComboBox.removeAllItems();

          for (String camera: cameras_) {
             CameraSelectComboBox.addItem(camera);
          }

          for (int i = 0; i < cameras_.length; i++) {
             String item = cameras_[i];
             for (int j = i + 1; j< cameras_.length; j++) {
                CameraSelectComboBox.addItem(item + SEPERATOR + cameras_[i+j]);
             }
          }
       }
       CameraSelectComboBox.setSelectedItem(currentCamera);
       updateCameraList();

       ExposureTextField.setText(GetExposure());

       UpdateItems(BinningComboBox, MMCoreJ.getG_Keyword_Binning());

       if (!core_.hasProperty(currentCamera, MODE)) {
          jLabel5.setEnabled(false);
          ModeComboBox.setEnabled(false);
       } else {
          ModeComboBox.removeAllItems();
          ModeComboBox.addItem(MIXED);
          ModeComboBox.addItem(MODECONV16);
          ModeComboBox.addItem(MODEEM14);
          ModeComboBox.addItem(MODEEM16);
          ModeComboBox.setSelectedItem(GetMode());
       }

       if (!core_.hasProperty(currentCamera, EMGAIN)) {
          jLabel4.setEnabled(false);
          EMGainTextField.setEnabled(false);
          EMGainSlider.setEnabled(false);
       } else {
          EMGainMin_ = (int) core_.getPropertyLowerLimit(currentCamera, EMGAIN);
          EMGainMax_ = (int) core_.getPropertyUpperLimit(currentCamera, EMGAIN);
          EMGainSlider.setMinimum(EMGainMin_);
          EMGainSlider.setMaximum(EMGainMax_);
          int gain = NumberUtils.coreStringToInt(core_.getProperty(currentCamera, EMGAIN));
          EMGainSlider.setValue(gain);
          EMGainTextField.setText(NumberUtils.intToDisplayString(gain));

          if (!core_.hasProperty(currentCamera, EMSWITCH)) {
             EMCheckBox.setEnabled(false);
          } else {
             String val = core_.getProperty(currentCamera, EMSWITCH);
             if (val.equals("On")) {
                EMCheckBox.setSelected(true);
             } 
          }    
       }

       // Pre-amp Gain
       if (!core_.hasProperty(currentCamera, AMPGAIN)) {
          GainLabel.setEnabled(false);
          GainComboBox.setEnabled(false);
       } else {
          UpdateItems(GainComboBox, AMPGAIN);
       }

       // Readout speed
       if (!core_.hasProperty(currentCamera, SPEED)) {
          SpeedLabel.setEnabled(false);
          SpeedComboBox.setEnabled(false);
       } else {
          UpdateItems(SpeedComboBox, SPEED);
       }

       // Frame Transfer
       if (!core_.hasProperty(currentCamera, FRAMETRANSFER)) {
          FrameTransferLabel.setEnabled(false);
          FrameTransferComboBox.setEnabled(false);
       } else {
          UpdateItems(FrameTransferComboBox, FRAMETRANSFER);
       }

       // Trigger
       if (!core_.hasProperty(currentCamera, TRIGGER)) {
          TriggerLabel.setEnabled(false);
          TriggerComboBox.setEnabled(false);
       } else {
          UpdateItems(TriggerComboBox, TRIGGER);
       }

       UpdateTemp();

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
      ROISetButton = new javax.swing.JButton();
      ROIUnsetButton = new javax.swing.JButton();
      jLabel3 = new javax.swing.JLabel();
      BinningComboBox = new javax.swing.JComboBox();
      ExposureTextField = new javax.swing.JTextField();
      EMGainSlider = new javax.swing.JSlider();
      EMGainTextField = new javax.swing.JTextField();
      ModeComboBox = new javax.swing.JComboBox();
      jLabel4 = new javax.swing.JLabel();
      jLabel5 = new javax.swing.JLabel();
      GainLabel = new javax.swing.JLabel();
      SpeedComboBox = new javax.swing.JComboBox();
      SpeedLabel = new javax.swing.JLabel();
      EMCheckBox = new javax.swing.JCheckBox();
      GainComboBox = new javax.swing.JComboBox();
      FrameTransferLabel = new javax.swing.JLabel();
      FrameTransferComboBox = new javax.swing.JComboBox();
      jLabel9 = new javax.swing.JLabel();
      CameraSelectComboBox = new javax.swing.JComboBox();
      TriggerLabel = new javax.swing.JLabel();
      TriggerComboBox = new javax.swing.JComboBox();
      TempButton = new javax.swing.JButton();
      TempLabel = new javax.swing.JLabel();
      ExposureButton = new javax.swing.JButton();
      SnapButton = new javax.swing.JButton();
      LiveButton = new javax.swing.JButton();

      jCheckBox1.setText("jCheckBox1");

      setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
      setTitle("Multi-Camera Control");
      setResizable(false);

      jLabel1.setFont(new java.awt.Font("Lucida Grande", 1, 10));
      jLabel1.setText("ROI");

      ROISetButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/icons/shape_handles.png"))); // NOI18N
      ROISetButton.setToolTipText("Set Region of Interest");
      ROISetButton.setMaximumSize(null);
      ROISetButton.setMinimumSize(null);
      ROISetButton.setPreferredSize(null);
      ROISetButton.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            ROISetButtonActionPerformed(evt);
         }
      });

      ROIUnsetButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/icons/arrow_out.png"))); // NOI18N
      ROIUnsetButton.setToolTipText("Set Region of Interest");
      ROIUnsetButton.setMaximumSize(null);
      ROIUnsetButton.setMinimumSize(null);
      ROIUnsetButton.setPreferredSize(null);
      ROIUnsetButton.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            ROIUnsetButtonActionPerformed(evt);
         }
      });

      jLabel3.setFont(new java.awt.Font("Lucida Grande", 0, 10));
      jLabel3.setText("Binning");

      BinningComboBox.setFont(new java.awt.Font("Lucida Grande", 0, 10));
      BinningComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "1", "2", "4", "8" }));
      BinningComboBox.addItemListener(new java.awt.event.ItemListener() {
         public void itemStateChanged(java.awt.event.ItemEvent evt) {
            BinningComboBoxItemStateChanged(evt);
         }
      });

      ExposureTextField.setFont(new java.awt.Font("Lucida Grande", 0, 10));
      ExposureTextField.setText("10");
      ExposureTextField.setToolTipText("Exposure time in ms");
      ExposureTextField.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            ExposureTextFieldActionPerformed(evt);
         }
      });
      ExposureTextField.addKeyListener(new java.awt.event.KeyAdapter() {
         public void keyTyped(java.awt.event.KeyEvent evt) {
            ExposureTextFieldKeyTyped(evt);
         }
      });

      EMGainSlider.addMouseListener(new java.awt.event.MouseAdapter() {
         public void mouseReleased(java.awt.event.MouseEvent evt) {
            EMGainSliderMouseReleased(evt);
         }
      });

      EMGainTextField.setFont(new java.awt.Font("Lucida Grande", 0, 10));
      EMGainTextField.setText("4");
      EMGainTextField.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            EMGainTextFieldActionPerformed(evt);
         }
      });

      ModeComboBox.setFont(new java.awt.Font("Lucida Grande", 0, 10));
      ModeComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "EM", "Conventional" }));
      ModeComboBox.addItemListener(new java.awt.event.ItemListener() {
         public void itemStateChanged(java.awt.event.ItemEvent evt) {
            ModeComboBoxItemStateChanged(evt);
         }
      });

      jLabel4.setFont(new java.awt.Font("Lucida Grande", 0, 10));
      jLabel4.setText("EM Gain");

      jLabel5.setFont(new java.awt.Font("Lucida Grande", 0, 10));
      jLabel5.setText("Mode");

      GainLabel.setFont(new java.awt.Font("Lucida Grande", 0, 10));
      GainLabel.setText("Gain");

      SpeedComboBox.setFont(new java.awt.Font("Lucida Grande", 0, 10));
      SpeedComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "1MHz", "3MHz", "5MHz", "10MHz" }));
      SpeedComboBox.addItemListener(new java.awt.event.ItemListener() {
         public void itemStateChanged(java.awt.event.ItemEvent evt) {
            SpeedComboBoxItemStateChanged(evt);
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

      GainComboBox.setFont(new java.awt.Font("Lucida Grande", 0, 10));
      GainComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "1", "2", "3", "4", "5" }));
      GainComboBox.addItemListener(new java.awt.event.ItemListener() {
         public void itemStateChanged(java.awt.event.ItemEvent evt) {
            GainComboBoxItemStateChanged(evt);
         }
      });

      FrameTransferLabel.setFont(new java.awt.Font("Lucida Grande", 0, 10));
      FrameTransferLabel.setText("FrameTransfer");

      FrameTransferComboBox.setFont(new java.awt.Font("Lucida Grande", 0, 10));
      FrameTransferComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "On", "Off" }));
      FrameTransferComboBox.addItemListener(new java.awt.event.ItemListener() {
         public void itemStateChanged(java.awt.event.ItemEvent evt) {
            FrameTransferComboBoxItemStateChanged(evt);
         }
      });

      jLabel9.setFont(new java.awt.Font("Lucida Grande", 0, 10));
      jLabel9.setText("Active Camera");

      CameraSelectComboBox.setFont(new java.awt.Font("Lucida Grande", 0, 10));
      CameraSelectComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "1", "2", "4", "8" }));
      CameraSelectComboBox.addItemListener(new java.awt.event.ItemListener() {
         public void itemStateChanged(java.awt.event.ItemEvent evt) {
            CameraSelectComboBoxItemStateChanged(evt);
         }
      });

      TriggerLabel.setFont(new java.awt.Font("Lucida Grande", 0, 10));
      TriggerLabel.setText("Trigger");

      TriggerComboBox.setFont(new java.awt.Font("Lucida Grande", 0, 10));
      TriggerComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "On", "Off" }));
      TriggerComboBox.addItemListener(new java.awt.event.ItemListener() {
         public void itemStateChanged(java.awt.event.ItemEvent evt) {
            TriggerComboBoxItemStateChanged(evt);
         }
      });

      TempButton.setFont(new java.awt.Font("Tahoma", 0, 10));
      TempButton.setText("Temp");
      TempButton.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            TempButtonActionPerformed(evt);
         }
      });

      TempLabel.setText("jLabel11");

      ExposureButton.setFont(new java.awt.Font("Tahoma", 0, 10));
      ExposureButton.setText("Exposure [ms]");
      ExposureButton.setMargin(new java.awt.Insets(2, 5, 2, 5));
      ExposureButton.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            ExposureButtonActionPerformed(evt);
         }
      });

      SnapButton.setFont(new java.awt.Font("Tahoma", 0, 10)); // NOI18N
      SnapButton.setText("Snap");
      SnapButton.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            SnapButtonActionPerformed(evt);
         }
      });

      LiveButton.setFont(new java.awt.Font("Tahoma", 0, 10)); // NOI18N
      LiveButton.setText("Live");
      LiveButton.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            LiveButtonActionPerformed(evt);
         }
      });

      org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(getContentPane());
      getContentPane().setLayout(layout);
      layout.setHorizontalGroup(
         layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
         .add(layout.createSequentialGroup()
            .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
               .add(layout.createSequentialGroup()
                  .addContainerGap()
                  .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                     .add(TempButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 63, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                     .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                        .add(jLabel9)
                        .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                           .add(ExposureButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 105, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                           .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                              .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                                 .add(jLabel3)
                                 .add(jLabel5)
                                 .add(jLabel4)
                                 .add(GainLabel)
                                 .add(SpeedLabel)
                                 .add(FrameTransferLabel)
                                 .add(TriggerLabel))
                              .add(41, 41, 41)))))
                  .add(7, 7, 7)
                  .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                     .add(SpeedComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 105, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                     .add(layout.createSequentialGroup()
                        .add(50, 50, 50)
                        .add(EMGainSlider, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 180, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                     .add(TriggerComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 105, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                     .add(ModeComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 105, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                     .add(GainComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 105, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                     .add(EMCheckBox)
                     .add(EMGainTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 49, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                     .add(FrameTransferComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 105, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                     .add(BinningComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 80, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                     .add(layout.createSequentialGroup()
                        .add(ExposureTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 70, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .add(60, 60, 60)
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                           .add(jLabel1)
                           .add(layout.createSequentialGroup()
                              .add(ROISetButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                              .add(8, 8, 8)
                              .add(ROIUnsetButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))))
                     .add(CameraSelectComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 191, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                     .add(TempLabel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 202, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
               .add(layout.createSequentialGroup()
                  .add(62, 62, 62)
                  .add(SnapButton)
                  .add(68, 68, 68)
                  .add(LiveButton)))
            .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
      );
      layout.setVerticalGroup(
         layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
         .add(layout.createSequentialGroup()
            .add(10, 10, 10)
            .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
               .add(jLabel9)
               .add(CameraSelectComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 23, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
            .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
            .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
               .add(ExposureTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
               .add(jLabel1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 10, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
               .add(ExposureButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 20, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
            .add(8, 8, 8)
            .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
               .add(layout.createSequentialGroup()
                  .add(7, 7, 7)
                  .add(jLabel3)
                  .add(8, 8, 8)
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
                  .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                     .add(ROISetButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 20, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                     .add(ROIUnsetButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 20, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                  .add(2, 2, 2)
                  .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                     .add(layout.createSequentialGroup()
                        .add(90, 90, 90)
                        .add(SpeedComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                     .add(layout.createSequentialGroup()
                        .add(25, 25, 25)
                        .add(EMGainSlider, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                     .add(layout.createSequentialGroup()
                        .add(140, 140, 140)
                        .add(TriggerComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                     .add(ModeComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                     .add(layout.createSequentialGroup()
                        .add(65, 65, 65)
                        .add(GainComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                     .add(layout.createSequentialGroup()
                        .add(45, 45, 45)
                        .add(EMCheckBox))
                     .add(layout.createSequentialGroup()
                        .add(25, 25, 25)
                        .add(EMGainTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                     .add(layout.createSequentialGroup()
                        .add(115, 115, 115)
                        .add(FrameTransferComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))))
               .add(BinningComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 23, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
            .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
            .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
               .add(TempButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 17, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
               .add(TempLabel))
            .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
            .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
               .add(SnapButton)
               .add(LiveButton))
            .addContainerGap(13, Short.MAX_VALUE))
      );

      pack();
   }// </editor-fold>//GEN-END:initComponents

    private void ROISetButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ROISetButtonActionPerformed
       setRoi();
    }//GEN-LAST:event_ROISetButtonActionPerformed

    private void ROIUnsetButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ROIUnsetButtonActionPerformed
       setRoi(new Rectangle(0,0,(int)imageWidth_, (int)imageHeight_));
    }//GEN-LAST:event_ROIUnsetButtonActionPerformed

    /*
     * If all selected cameras have the same EM Gain value, return EM Gain value
     * otherwise return -1
     */
    private int GetEMGain() {
       int gain = MIXEDINT;
       try {
          for (String camera: cameras_) {
             if (selectedCameras_.get(camera)) {
                int tmpGain = NumberUtils.coreStringToInt(core_.getProperty(camera, EMGAIN));
                if (gain == -1)
                   gain = tmpGain;
                if (tmpGain != gain)
                   return MIXEDINT;
             }
          }
       } catch (Exception ex) {
          ReportingUtils.showError(ex, MultiCameraFrame.class.getName() + " encountered an error.");
       }
       return gain;
   }

    private void SetEMGain() {
       if (!initialized(false, false))
          return;
       boolean liveRunning = dGui_.getLiveMode();
       int val = EMGainSlider.getValue();
       try {
          dGui_.enableLiveMode(false);
          for (String camera: cameras_) {
             if (!camera.equals("") && selectedCameras_.get(camera)) {
                core_.setProperty(camera, EMGAIN, NumberUtils.intToCoreString(val));
             }
          }
          dGui_.enableLiveMode(liveRunning);
          dGui_.updateGUI(false);
       } catch(Exception ex) {
          ReportingUtils.showError(ex, MultiCameraFrame.class.getName() + " encountered an error.");
       }
       EMGainTextField.setText(NumberUtils.intToDisplayString(val));
    }

   /*
    * Signals whether the first selected camera has its EM switch on or off
    */
   private boolean GetEMSwitch() {
      try {
         for (String camera: cameras_) {
            if (selectedCameras_.get(camera)) {
               if (core_.hasProperty(camera, EMSWITCH)) {
                  String OnOff = core_.getProperty(camera, EMSWITCH);
                  if (OnOff.equals("On"))
                     return true;
               } else
                  return false;
            }
         }
      } catch (Exception ex) {
         ReportingUtils.showError(ex, MultiCameraFrame.class.getName() + " encountered an error.");
      }
      return false;
   }

   private String GetExposure() {
       String exposure = "-1";
       try {
          String originalCamera = core_.getProperty("Core", "Camera");
          for (String camera : cameras_) {
             if (!camera.equals("") && selectedCameras_.get(camera)) {
                core_.setProperty("Core", "Camera", camera);
                String exp = NumberUtils.doubleToDisplayString(core_.getExposure());
                if (exposure.equals("-1"))
                   exposure = exp;
                if (!exposure.equals(exp))
                   return MIXED;
             }
          }
       } catch (Exception ex) {
          ReportingUtils.showError(ex, MultiCameraFrame.class.getName() + " encountered an error.");
       }
       return exposure;
    }

   private void SetExposure() {
       boolean liveRunning = dGui_.getLiveMode();
       String currentCamera = "";
       try {
          double exposure = NumberUtils.displayStringToDouble(ExposureTextField.getText());
          currentCamera =  core_.getCameraDevice();
          dGui_.enableLiveMode(false);
          for(String camera: cameras_) {
             if (!camera.equals("") && selectedCameras_.get(camera)) {
                core_.setCameraDevice(camera);
                core_.setExposure(exposure);
             }
          }
          ExposureTextField.setText(NumberUtils.doubleToDisplayString(exposure));

         dGui_.updateGUI(false);
         dGui_.enableLiveMode(liveRunning);
      } catch (Exception ex) {
          ReportingUtils.showError(ex, MultiCameraFrame.class.getName() + " encountered an error.");
      }
   }

    private void ExposureTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ExposureTextFieldActionPerformed
       SetExposure();
    }//GEN-LAST:event_ExposureTextFieldActionPerformed

    private void EMGainTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_EMGainTextFieldActionPerformed
       if (!initialized(false, false))
          return;
       try {
         int val = NumberUtils.displayStringToInt(EMGainTextField.getText());
         if (val > EMGainMax_)
            val = EMGainMax_;
         if (val < EMGainMin_)
            val = EMGainMin_;
         EMGainSlider.setEnabled(true);
         EMGainSlider.setValue(val);
       } catch (ParseException e) {
          // ignore if the user types garbage
       }
    }//GEN-LAST:event_EMGainTextFieldActionPerformed

    private void EMCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_EMCheckBoxActionPerformed
       // EM enable
       if (!initialized(false, false))
          return;
       boolean liveRunning = dGui_.getLiveMode();
       boolean on = EMCheckBox.isSelected();
       String command = "Off";
       if (on)
          command = "On";
       try {
          dGui_.enableLiveMode(false);
          for (String camera: cameras_ ) {
             if (!camera.equals("") && selectedCameras_.get(camera)) {
                core_.setProperty(camera, EMSWITCH, command);
             }
          }
          dGui_.enableLiveMode(liveRunning);
       } catch (Exception ex) {
          ReportingUtils.showError(ex, MultiCameraFrame.class.getName() + " encountered an error.");
       }
    }//GEN-LAST:event_EMCheckBoxActionPerformed

    private String GetMode() {
       String mode = "";
       try {
          String fs = firstSelectedCamera();
          String modeProp = core_.getProperty(fs, MODE);
          String adProp = core_.getProperty(fs, ADCONVERTER);
          if (modeProp.equals(EMMODE)) {
             if (adProp.equals(AD14BIT))
                mode = MODEEM14;
             else if (adProp.equals(AD16BIT))
                mode = MODEEM16;
          } else if (modeProp.equals(NORMALMODE)) {
             if (adProp.equals(AD16BIT))
                mode = MODECONV16;
          }
          for (String camera : cameras_) {
             if (!camera.equals(fs) && selectedCameras_.get(camera)) {
                String mP = core_.getProperty(camera, MODE);
                String aP = core_.getProperty(camera, ADCONVERTER);
                if (!mP.equals(modeProp))
                   mode = MIXED;
                if (!aP.equals(adProp))
                   mode = MIXED;
             }
          }
       } catch (Exception ex) {
          ReportingUtils.showError(ex, MultiCameraFrame.class.getName() + " encountered an error.");
       }
       return mode;
    }


    private void EMGainSliderMouseReleased(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_EMGainSliderMouseReleased
       SetEMGain();
       dGui_.updateGUI(false);
    }//GEN-LAST:event_EMGainSliderMouseReleased


    private void TempButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_TempButtonActionPerformed
       boolean liveRunning = dGui_.getLiveMode();
       dGui_.enableLiveMode(false);
       UpdateTemp();
       dGui_.enableLiveMode(liveRunning);
    }//GEN-LAST:event_TempButtonActionPerformed

    private void ExposureTextFieldKeyTyped(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_ExposureTextFieldKeyTyped

    }//GEN-LAST:event_ExposureTextFieldKeyTyped

    private void ExposureButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ExposureButtonActionPerformed
       SetExposure();
    }//GEN-LAST:event_ExposureButtonActionPerformed

    private void BinningComboBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_BinningComboBoxItemStateChanged
       SetComboSelection(BinningComboBox, MMCoreJ.getG_Keyword_Binning());
       dGui_.updateGUI(false);
    }//GEN-LAST:event_BinningComboBoxItemStateChanged

    private void ModeComboBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_ModeComboBoxItemStateChanged
       // Combo box selecting readout mode (EM/standard)
       if (!initialized(false, false))
          return;

       Object item = ModeComboBox.getSelectedItem();
       if (item.equals(MIXED))
          return;

       boolean liveRunning = dGui_.getLiveMode();
       String mode = item.toString();
       try {
          dGui_.enableLiveMode(false);
          for(String camera: cameras_) {
             if (!camera.equals("") && selectedCameras_.get(camera)) {
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
    }//GEN-LAST:event_ModeComboBoxItemStateChanged

    private void GainComboBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_GainComboBoxItemStateChanged
       SetComboSelection(GainComboBox, AMPGAIN);
       dGui_.updateGUI(false);
    }//GEN-LAST:event_GainComboBoxItemStateChanged

    private void SpeedComboBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_SpeedComboBoxItemStateChanged
       SetComboSelection(SpeedComboBox, SPEED);
       dGui_.updateGUI(false);
    }//GEN-LAST:event_SpeedComboBoxItemStateChanged

    private void FrameTransferComboBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_FrameTransferComboBoxItemStateChanged
       SetComboSelection(FrameTransferComboBox, FRAMETRANSFER);
       dGui_.updateGUI(false);
    }//GEN-LAST:event_FrameTransferComboBoxItemStateChanged

    private void TriggerComboBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_TriggerComboBoxItemStateChanged
      SetComboSelection(TriggerComboBox, TRIGGER);
      dGui_.updateGUI(false);
    }//GEN-LAST:event_TriggerComboBoxItemStateChanged

    private void CameraSelectComboBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_CameraSelectComboBoxItemStateChanged
       if (!initialized(false, false))
          return;
       updateCameraList();
       boolean liveRunning = dGui_.getLiveMode();
       try {
          // Use the initialize flag to prevent pushing settings back to the hardware
          initialized(true, false);
          String fsCamera = firstSelectedCamera();
          dGui_.enableLiveMode(false);
          core_.setProperty("Core", "Camera", fsCamera);
          ExposureTextField.setText(GetExposure());
          GetComboSelection(BinningComboBox, MMCoreJ.getG_Keyword_Binning());

          if (core_.hasProperty(fsCamera, MODE))
            ModeComboBox.setSelectedItem(GetMode());
          if (core_.hasProperty(fsCamera, EMGAIN)) {
             int gain = GetEMGain();
             EMGainSlider.setValue(gain);
             if (gain == MIXEDINT) {
                EMGainTextField.setText(MIXED);
                EMGainSlider.setEnabled(false);
             }  else {
                EMGainTextField.setText(NumberUtils.intToDisplayString(gain));
                EMGainSlider.setEnabled(true);
                EMGainSlider.setValue(gain);
             }
          }
          if (core_.hasProperty(fsCamera, EMSWITCH))
             EMCheckBox.setSelected(GetEMSwitch());
          if (core_.hasProperty(fsCamera, AMPGAIN))
             GetComboSelection(GainComboBox, AMPGAIN);
          if (core_.hasProperty(fsCamera, SPEED))
             GetComboSelection(SpeedComboBox, SPEED);
          if (core_.hasProperty(fsCamera, FRAMETRANSFER))
             GetComboSelection(FrameTransferComboBox, FRAMETRANSFER);
          if (core_.hasProperty(fsCamera, TRIGGER))
             GetComboSelection(TriggerComboBox, TRIGGER);
          UpdateTemp();
          initialized(true, true);


          dGui_.enableLiveMode(liveRunning);
       } catch (Exception ex) {
          ReportingUtils.showError(ex, MultiCameraFrame.class.getName() + " encountered an error.");
       }

       dGui_.updateGUI(false);
    }//GEN-LAST:event_CameraSelectComboBoxItemStateChanged

    private void SnapButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_SnapButtonActionPerformed
       int nrSelectedCameras = nrSelectedCameras();
       if (nrSelectedCameras == 1)
          gui_.snapSingleImage();
       else if (nrSelectedCameras > 1) {
          try {
             // TODO: check that an existing acquisition has the same dimensions
             // and can be re-used
             int w = (int) core_.getImageWidth();
             int h = (int) core_.getImageHeight();
             int d = (int) core_.getBytesPerPixel();

             if (!gui_.acquisitionExists(ACQNAME)) {
                gui_.openAcquisition(ACQNAME, "", 1, nrSelectedCameras, 1, true, false);
                gui_.initializeAcquisition(ACQNAME, w, h, d);
             }
             gui_.setChannelColor(ACQNAME, 0, Color.RED);
             gui_.setChannelColor(ACQNAME, 1, Color.GREEN);

             // delete previous content of circular buffer
             core_.initializeCircularBuffer();
             for (String camera : cameras_) {
                if (selectedCameras_.get(camera)) {
                   core_.startSequenceAcquisition(camera, 1, 0, false);
                }
             }
             int imgcounter = 0;
             while (core_.isSequenceRunning() || core_.getRemainingImageCount() > 0) {
               if (core_.getRemainingImageCount() > 0) {
                  TaggedImage img = core_.popNextTaggedImage();
                  if (img != null) {
                     JSONObject md = img.tags;
                     MDUtils.setFrameIndex(md, 0);
                     MDUtils.setSliceIndex(md, 0);
                     MDUtils.setPositionIndex(md, 0);
                     String cName = (String) md.get("Camera");
                     //if (c1.equals(cName))
                     MDUtils.setChannelIndex(md, imgcounter);
                     gui_.addImage(ACQNAME, img);
                     imgcounter++;
                  }
               }
             }
             //gui_.closeAcquisition(ACQNAME);

          } catch (Exception ex) {
                  ReportingUtils.showError(ex);
               }
       }
    }//GEN-LAST:event_SnapButtonActionPerformed

    private void LiveButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_LiveButtonActionPerformed
       if (nrSelectedCameras() == 1) {
          dGui_.enableLiveMode(!dGui_.getLiveMode());
          if (dGui_.getLiveMode()) {
             LiveButton.setText("Stop Live");
          } else {
             LiveButton.setText("Live");
          }
       } 
    }//GEN-LAST:event_LiveButtonActionPerformed

    private void UpdateTemp() {
       String tempText = "";
       try {
          for (String camera : cameras_) {
             if ( selectedCameras_.get(camera)) {
                 if (core_.hasProperty(camera, TEMP)) {
                    tempText += core_.getProperty(camera, TEMP) + "\u00b0" + "C";
                 }
             }
          }
       } catch(Exception ex) {
          ReportingUtils.showError(ex, MultiCameraFrame.class.getName() + " encountered an error.");
       }
       TempLabel.setText(tempText);
    }
    private void UpdateItems(javax.swing.JComboBox comboBox, String property) {
       if (comboBox != null) {
          try {
             String camera = firstSelectedCamera();
             StrVector vals = core_.getAllowedPropertyValues(camera, property);

             String[] newVals = new String[(int)vals.size() + 1];
             newVals[0] = MIXED;
             for (int i=0; i < vals.size(); i++) {
                newVals[i+1] = vals.get(i);
             }
             comboBox.setModel(new DefaultComboBoxModel(newVals));

          } catch (Exception ex) {
             ReportingUtils.showError(ex, MultiCameraFrame.class.getName() + " encountered an error.");
          }
          GetComboSelection(comboBox, property);
       }
    }

    private void GetComboSelection(javax.swing.JComboBox comboBox, String property) {
       if (comboBox == null || !comboBox.isEnabled())
          return;
       try {
          String fs = firstSelectedCamera();
          String val = core_.getProperty(fs, property);
          for (String camera : cameras_) {
             if (!camera.equals(fs) && selectedCameras_.get(camera)) {
                String tVal = core_.getProperty(camera, property);
                if (!tVal.equals(val)) {
                   comboBox.setSelectedItem(MIXED);
                   return;
                }
             }
          }
          comboBox.setSelectedItem(val);
       } catch (Exception ex) {
          ReportingUtils.showError(ex, MultiCameraFrame.class.getName() + " encountered an error.");
       }
    }

    private void SetComboSelection(javax.swing.JComboBox comboBox, String property) {
       if (!initialized(false, false))
          return;
       boolean liveRunning = dGui_.getLiveMode();
       String val = (String) comboBox.getSelectedItem();
       if (val.equals(MIXED)) {
          GetComboSelection(comboBox, property);
          return;
       }
       try {
          dGui_.enableLiveMode(false);
          for (String camera: cameras_) {
             if (!camera.equals("") && selectedCameras_.get(camera)) {
                core_.setProperty(camera, property, val);
             }
          }
          dGui_.enableLiveMode(liveRunning);
       } catch (Exception ex) {
          ReportingUtils.showError(ex, MultiCameraFrame.class.getName() + " encountered an error.");
       }
    }

   // Variables declaration - do not modify//GEN-BEGIN:variables
   private javax.swing.JComboBox BinningComboBox;
   private javax.swing.JComboBox CameraSelectComboBox;
   private javax.swing.JCheckBox EMCheckBox;
   private javax.swing.JSlider EMGainSlider;
   private javax.swing.JTextField EMGainTextField;
   private javax.swing.JButton ExposureButton;
   private javax.swing.JTextField ExposureTextField;
   private javax.swing.JComboBox FrameTransferComboBox;
   private javax.swing.JLabel FrameTransferLabel;
   private javax.swing.JComboBox GainComboBox;
   private javax.swing.JLabel GainLabel;
   private javax.swing.JButton LiveButton;
   private javax.swing.JComboBox ModeComboBox;
   private javax.swing.JButton ROISetButton;
   private javax.swing.JButton ROIUnsetButton;
   private javax.swing.JButton SnapButton;
   private javax.swing.JComboBox SpeedComboBox;
   private javax.swing.JLabel SpeedLabel;
   private javax.swing.JButton TempButton;
   private javax.swing.JLabel TempLabel;
   private javax.swing.JComboBox TriggerComboBox;
   private javax.swing.JLabel TriggerLabel;
   private javax.swing.JCheckBox jCheckBox1;
   private javax.swing.JLabel jLabel1;
   private javax.swing.JLabel jLabel3;
   private javax.swing.JLabel jLabel4;
   private javax.swing.JLabel jLabel5;
   private javax.swing.JLabel jLabel9;
   // End of variables declaration//GEN-END:variables


   private void updateCameraList() {
      for (String camera : cameras_) {
         selectedCameras_.put(camera, false);
      }

      String item = (String) CameraSelectComboBox.getSelectedItem();
      String[] selCameras = item.split(SEPERATOR);
      for(String camera : selCameras) {
         selectedCameras_.put(camera, true);
      }
   }

   private String firstSelectedCamera() throws Exception {
      for (Map.Entry<String, Boolean> entry : selectedCameras_.entrySet()) {
         if (entry.getValue())
            return entry.getKey();
      }
      throw new Exception();
   }

   private void setRoi (Rectangle roi) {
      boolean liveRunning = dGui_.getLiveMode();
      String currentCamera = "";
      try {
         currentCamera =  core_.getCameraDevice();
         dGui_.enableLiveMode(false);
         for(String camera: cameras_) {
            if (selectedCameras_.get(camera)) {
               core_.setCameraDevice(camera);
               core_.setROI(roi.x, roi.y, roi.width, roi.height);
            }
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

   private int nrSelectedCameras () {
      int nr =0;
      for (String camera: cameras_) {
         if (selectedCameras_.get(camera)) {
            nr++;
         }
      }
      return nr;
   }

   public void propertiesChangedAlert() {
      UpdateItems(ModeComboBox, MODE);
      UpdateItems(SpeedComboBox, SPEED);
   };
   public void propertyChangedAlert(String device, String property, String value){
      try {
         if (core_.getDeviceType(device).equals(DeviceType.CameraDevice)) {

         }
      } catch (Exception ex) {
      }
   };
   public void configGroupChangedAlert(String groupName, String newConfig){};
   public void pixelSizeChangedAlert(double newPixelSizeUm){};
   public void stagePositionChangedAlert(String deviceName, double pos){};
   public void xyStagePositionChanged(String deviceName, double xPos, double yPos){};
}
