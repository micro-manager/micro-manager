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
import java.awt.event.ItemEvent;
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
import org.micromanager.utils.MMScriptException;
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
   private HashMap<String, Integer> channelIndex_;
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

   private static final Color[] COLORS = {Color.RED, Color.GREEN, Color.BLUE, Color.MAGENTA, Color.YELLOW, Color.PINK};

   private boolean liveRunning_ = false;
   private static final String GUILIVEMODE = "Gui";
   private static final String PLUGINLIVEMODE = "Plugin";
   private static final String NOLIVEMODE = "None";
   private String liveMode_ = GUILIVEMODE;

   private LiveImagingThread th_;

   private synchronized boolean getLiveRunning() {
      return liveRunning_;
   }

   private synchronized void setLiveRunning(boolean state) {
      liveRunning_ = state;
   }

   private class LiveImagingThread extends Thread
   {
      String lastCamera_;
      int nrCameras_;

      private LiveImagingThread(String lastCamera, int nrCameras) {
         lastCamera_ = lastCamera;
         nrCameras_ = nrCameras;
      }

      @Override
      public void run() {
         int imgcounter = 0;
         int cutoff = 2 * nrCameras_;
         while (core_.isSequenceRunning() && getLiveRunning()) {
            if (core_.getRemainingImageCount() > 0) {
               try {
                  TaggedImage img = core_.getLastTaggedImage();
                  if (img != null) {
                     JSONObject md = img.tags;
                     MDUtils.setFrameIndex(md, 0);
                     MDUtils.setSliceIndex(md, 0);
                     MDUtils.setPositionIndex(md, 0);
                     String cName = (String) md.get("Camera");
                     MDUtils.setChannelIndex(md, channelIndex_.get(cName));
                     if (imgcounter > cutoff) {
                        // only update the image when we get an image from the last camera
                        if (cName.equals(lastCamera_))
                           gui_.addImage(ACQNAME, img, true);
                        else
                           gui_.addImage(ACQNAME, img, false);
                     } else
                        gui_.addImage(ACQNAME, img, true);
                     imgcounter++;
                  }
               } catch (Exception ex) {
                     ReportingUtils.showError(ex);
                     return;
               }
            }
         }
      }
   }

   private LiveImagingThread liveImagingThread_;

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
          cameraSelectComboBox.setEnabled(false);
       } else {
          cameraSelectComboBox.removeAllItems();

          for (String camera: cameras_) {
             cameraSelectComboBox.addItem(camera);
          }

          for (int i = 0; i < cameras_.length; i++) {
             String item = cameras_[i];
             for (int j = i + 1; j< cameras_.length; j++) {
                cameraSelectComboBox.addItem(item + SEPERATOR + cameras_[i+j]);
             }
          }
       }
       cameraSelectComboBox.setSelectedItem(currentCamera);
       updateCameraList();

       exposureTextField.setText(getExposure());

       updateItems(binningComboBox, MMCoreJ.getG_Keyword_Binning());

       if (!core_.hasProperty(currentCamera, MODE)) {
          jLabel5.setEnabled(false);
          modeComboBox.setEnabled(false);
       } else {
          modeComboBox.removeAllItems();
          modeComboBox.addItem(MIXED);
          modeComboBox.addItem(MODECONV16);
          modeComboBox.addItem(MODEEM14);
          modeComboBox.addItem(MODEEM16);
          modeComboBox.setSelectedItem(getMode());
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
          gainComboBox.setEnabled(false);
       } else {
          updateItems(gainComboBox, AMPGAIN);
       }

       // Readout speed
       if (!core_.hasProperty(currentCamera, SPEED)) {
          SpeedLabel.setEnabled(false);
          speedComboBox.setEnabled(false);
       } else {
          updateItems(speedComboBox, SPEED);
       }

       // Frame Transfer
       if (!core_.hasProperty(currentCamera, FRAMETRANSFER)) {
          FrameTransferLabel.setEnabled(false);
          frameTransferComboBox.setEnabled(false);
       } else {
          updateItems(frameTransferComboBox, FRAMETRANSFER);
       }

       // Trigger
       if (!core_.hasProperty(currentCamera, TRIGGER)) {
          TriggerLabel.setEnabled(false);
          triggerComboBox.setEnabled(false);
       } else {
          updateItems(triggerComboBox, TRIGGER);
       }

       updateTemp();

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
      binningComboBox = new javax.swing.JComboBox();
      exposureTextField = new javax.swing.JTextField();
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
      exposureButton = new javax.swing.JButton();
      snapButton = new javax.swing.JButton();
      liveButton = new javax.swing.JButton();

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

      binningComboBox.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      binningComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "1", "2", "4", "8" }));
      binningComboBox.addItemListener(new java.awt.event.ItemListener() {
         public void itemStateChanged(java.awt.event.ItemEvent evt) {
            binningComboBoxItemStateChanged(evt);
         }
      });

      exposureTextField.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      exposureTextField.setText("10");
      exposureTextField.setToolTipText("Exposure time in ms");
      exposureTextField.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            exposureTextFieldActionPerformed(evt);
         }
      });
      exposureTextField.addKeyListener(new java.awt.event.KeyAdapter() {
         public void keyTyped(java.awt.event.KeyEvent evt) {
            exposureTextFieldKeyTyped(evt);
         }
      });

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

      jLabel4.setFont(new java.awt.Font("Lucida Grande", 0, 10));
      jLabel4.setText("EM Gain");

      jLabel5.setFont(new java.awt.Font("Lucida Grande", 0, 10));
      jLabel5.setText("Mode");

      GainLabel.setFont(new java.awt.Font("Lucida Grande", 0, 10));
      GainLabel.setText("Gain");

      speedComboBox.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      speedComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "1MHz", "3MHz", "5MHz", "10MHz" }));
      speedComboBox.addItemListener(new java.awt.event.ItemListener() {
         public void itemStateChanged(java.awt.event.ItemEvent evt) {
            speedComboBoxItemStateChanged(evt);
         }
      });

      SpeedLabel.setFont(new java.awt.Font("Lucida Grande", 0, 10));
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

      FrameTransferLabel.setFont(new java.awt.Font("Lucida Grande", 0, 10));
      FrameTransferLabel.setText("FrameTransfer");

      frameTransferComboBox.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
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

      exposureButton.setFont(new java.awt.Font("Tahoma", 0, 10)); // NOI18N
      exposureButton.setText("Exposure [ms]");
      exposureButton.setMargin(new java.awt.Insets(2, 5, 2, 5));
      exposureButton.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            exposureButtonActionPerformed(evt);
         }
      });

      snapButton.setFont(new java.awt.Font("Tahoma", 0, 10)); // NOI18N
      snapButton.setText("Snap");
      snapButton.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            snapButtonActionPerformed(evt);
         }
      });

      liveButton.setFont(new java.awt.Font("Tahoma", 0, 10)); // NOI18N
      liveButton.setText("Live");
      liveButton.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            liveButtonActionPerformed(evt);
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
                     .add(tempButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 63, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                     .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                        .add(jLabel9)
                        .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                           .add(exposureButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 105, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
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
                     .add(binningComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 80, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                     .add(layout.createSequentialGroup()
                        .add(exposureTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 70, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .add(60, 60, 60)
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                           .add(jLabel1)
                           .add(layout.createSequentialGroup()
                              .add(ROISetButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                              .add(8, 8, 8)
                              .add(ROIUnsetButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))))
                     .add(cameraSelectComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 191, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                     .add(tempLabel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 202, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
               .add(layout.createSequentialGroup()
                  .add(62, 62, 62)
                  .add(snapButton)
                  .add(68, 68, 68)
                  .add(liveButton)))
            .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
      );
      layout.setVerticalGroup(
         layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
         .add(layout.createSequentialGroup()
            .add(10, 10, 10)
            .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
               .add(jLabel9)
               .add(cameraSelectComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 23, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
            .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
            .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
               .add(exposureTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
               .add(jLabel1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 10, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
               .add(exposureButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 20, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
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
                        .add(frameTransferComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))))
               .add(binningComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 23, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
            .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
            .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
               .add(tempButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 17, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
               .add(tempLabel))
            .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
            .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
               .add(snapButton)
               .add(liveButton))
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
    private int getEMGain() {
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

    private void setEMGain() {
       if (!initialized(false, false))
          return;
       boolean liveRunning = getLiveMode();
       int val = EMGainSlider.getValue();
       try {
          enableLiveMode(false);
          for (String camera: cameras_) {
             if (!camera.equals("") && selectedCameras_.get(camera)) {
                core_.setProperty(camera, EMGAIN, NumberUtils.intToCoreString(val));
             }
          }
          enableLiveMode(liveRunning);
          dGui_.updateGUI(false);
       } catch(Exception ex) {
          ReportingUtils.showError(ex, MultiCameraFrame.class.getName() + " encountered an error.");
       }
       EMGainTextField.setText(NumberUtils.intToDisplayString(val));
    }

   /*
    * Signals whether the first selected camera has its EM switch on or off
    */
   private boolean getEMSwitch() {
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

   private String getExposure() {
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

   private void setExposure() {
       boolean liveRunning = getLiveMode();
       String currentCamera = "";
       try {
          double exposure = NumberUtils.displayStringToDouble(exposureTextField.getText());
          currentCamera =  core_.getCameraDevice();
          enableLiveMode(false);
          for(String camera: cameras_) {
             if (!camera.equals("") && selectedCameras_.get(camera)) {
                core_.setCameraDevice(camera);
                core_.setExposure(exposure);
             }
          }
          exposureTextField.setText(NumberUtils.doubleToDisplayString(exposure));

         dGui_.updateGUI(false);
         enableLiveMode(liveRunning);
      } catch (Exception ex) {
          ReportingUtils.showError(ex, MultiCameraFrame.class.getName() + " encountered an error.");
      }
   }

    private void exposureTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exposureTextFieldActionPerformed
       setExposure();
    }//GEN-LAST:event_exposureTextFieldActionPerformed

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
       boolean liveRunning = getLiveMode();
       boolean on = EMCheckBox.isSelected();
       String command = "Off";
       if (on)
          command = "On";
       try {
          enableLiveMode(false);
          for (String camera: cameras_ ) {
             if (!camera.equals("") && selectedCameras_.get(camera)) {
                core_.setProperty(camera, EMSWITCH, command);
             }
          }
          enableLiveMode(liveRunning);
       } catch (Exception ex) {
          ReportingUtils.showError(ex, MultiCameraFrame.class.getName() + " encountered an error.");
       }
    }//GEN-LAST:event_EMCheckBoxActionPerformed

    private String getMode() {
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
       setEMGain();
       dGui_.updateGUI(false);
    }//GEN-LAST:event_EMGainSliderMouseReleased


    private void tempButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_tempButtonActionPerformed
       boolean liveRunning = getLiveMode();
       enableLiveMode(false);
       updateTemp();
       enableLiveMode(liveRunning);
    }//GEN-LAST:event_tempButtonActionPerformed

    private void exposureTextFieldKeyTyped(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_exposureTextFieldKeyTyped

    }//GEN-LAST:event_exposureTextFieldKeyTyped

    private void exposureButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exposureButtonActionPerformed
       setExposure();
    }//GEN-LAST:event_exposureButtonActionPerformed

    private void binningComboBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_binningComboBoxItemStateChanged
       if (! (evt.getStateChange() == ItemEvent.SELECTED) )
          return;
       setComboSelection(binningComboBox, MMCoreJ.getG_Keyword_Binning());
       dGui_.updateGUI(false);
    }//GEN-LAST:event_binningComboBoxItemStateChanged

    private void modeComboBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_modeComboBoxItemStateChanged
       // Combo box selecting readout mode (EM/standard)
       if (! (evt.getStateChange() == ItemEvent.SELECTED) )
          return;
       if (!initialized(false, false))
          return;

       Object item = modeComboBox.getSelectedItem();
       if (item.equals(MIXED))
          return;

       boolean liveRunning = getLiveMode();
       String mode = item.toString();
       try {
          enableLiveMode(false);
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

          updateItems(gainComboBox, AMPGAIN);
          updateItems(speedComboBox, SPEED);

          enableLiveMode(liveRunning);
          dGui_.updateGUI(false);
       } catch (Exception ex) {
          ReportingUtils.showError(ex, MultiCameraFrame.class.getName() + " encountered an error.");
       }
    }//GEN-LAST:event_modeComboBoxItemStateChanged

    private void gainComboBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_gainComboBoxItemStateChanged
       if (! (evt.getStateChange() == ItemEvent.SELECTED) )
          return;
       setComboSelection(gainComboBox, AMPGAIN);
       dGui_.updateGUI(false);
    }//GEN-LAST:event_gainComboBoxItemStateChanged

    private void speedComboBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_speedComboBoxItemStateChanged
       if (! (evt.getStateChange() == ItemEvent.SELECTED) )
          return;
       setComboSelection(speedComboBox, SPEED);
       dGui_.updateGUI(false);
    }//GEN-LAST:event_speedComboBoxItemStateChanged

    private void frameTransferComboBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_frameTransferComboBoxItemStateChanged
       if (! (evt.getStateChange() == ItemEvent.SELECTED) )
          return;
       setComboSelection(frameTransferComboBox, FRAMETRANSFER);
       dGui_.updateGUI(false);
    }//GEN-LAST:event_frameTransferComboBoxItemStateChanged

    private void triggerComboBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_triggerComboBoxItemStateChanged
       if (! (evt.getStateChange() == ItemEvent.SELECTED) )
          return;
       setComboSelection(triggerComboBox, TRIGGER);
       dGui_.updateGUI(false);
    }//GEN-LAST:event_triggerComboBoxItemStateChanged

    private void cameraSelectComboBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_cameraSelectComboBoxItemStateChanged
       if (! (evt.getStateChange() == ItemEvent.SELECTED) )
          return;
       if (!initialized(false, false))
          return;

       boolean liveRunning = getLiveMode();
       enableLiveMode(false);
       updateCameraList();
       try {
          // Use the initialize flag to prevent pushing settings back to the hardware
          initialized(true, false);
          String fsCamera = firstSelectedCamera();
          core_.setProperty("Core", "Camera", fsCamera);
          exposureTextField.setText(getExposure());
          getComboSelection(binningComboBox, MMCoreJ.getG_Keyword_Binning());

          if (core_.hasProperty(fsCamera, MODE))
            modeComboBox.setSelectedItem(getMode());
          if (core_.hasProperty(fsCamera, EMGAIN)) {
             int gain = getEMGain();
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
             EMCheckBox.setSelected(getEMSwitch());
          if (core_.hasProperty(fsCamera, AMPGAIN))
             getComboSelection(gainComboBox, AMPGAIN);
          if (core_.hasProperty(fsCamera, SPEED))
             getComboSelection(speedComboBox, SPEED);
          if (core_.hasProperty(fsCamera, FRAMETRANSFER))
             getComboSelection(frameTransferComboBox, FRAMETRANSFER);
          if (core_.hasProperty(fsCamera, TRIGGER))
             getComboSelection(triggerComboBox, TRIGGER);
          updateTemp();
          initialized(true, true);

          if (nrSelectedCameras() > 1)
             liveMode_ = PLUGINLIVEMODE;
          else
             liveMode_ = GUILIVEMODE;
          enableLiveMode(liveRunning);
       } catch (Exception ex) {
          ReportingUtils.showError(ex, MultiCameraFrame.class.getName() + " encountered an error.");
       }

       dGui_.updateGUI(false);
    }//GEN-LAST:event_cameraSelectComboBoxItemStateChanged

    private void snapButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_snapButtonActionPerformed
       int nrSelectedCameras = nrSelectedCameras();
       if (nrSelectedCameras == 1)
          gui_.snapSingleImage();
       else if (nrSelectedCameras > 1) {
          liveMode_ = PLUGINLIVEMODE;
          try {
             initializeSequence(nrSelectedCameras);
             // delete previous content of circular buffer
             core_.initializeCircularBuffer();

             channelIndex_ = new HashMap<String, Integer>();
             int i = 0;
             for (String camera : cameras_) {
                if (selectedCameras_.get(camera)) {
                   channelIndex_.put(camera, i);
                   i++;
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
                     MDUtils.setChannelIndex(md, channelIndex_.get(cName));
                     // MDUtils.setChannelIndex(md, imgcounter);
                     gui_.addImage(ACQNAME, img, true);
                     imgcounter++;
                  }
               }
             }
          } catch (Exception ex) {
             ReportingUtils.showError(ex);
             enableLiveMode(false);
          }
       }
    }//GEN-LAST:event_snapButtonActionPerformed

    private void liveButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_liveButtonActionPerformed
       if (!getLiveMode()) {
          enableLiveMode(true);
          liveButton.setText("Stop Live");
      } else {
          enableLiveMode(false);
          liveButton.setText("Live");
      }
    }//GEN-LAST:event_liveButtonActionPerformed

    private void enableLiveMode(boolean start)  {
       int nrSelectedCameras = nrSelectedCameras();
       if (liveMode_.equals(GUILIVEMODE)) {
           liveMode_ = GUILIVEMODE;
           dGui_.enableLiveMode(start);
           return;
       }
         
       if (start) {
          try {
             liveMode_ = PLUGINLIVEMODE;
             initializeSequence(nrSelectedCameras);
             // delete previous content of circular buffer
             core_.initializeCircularBuffer();

             channelIndex_ = new HashMap<String, Integer>();
             Integer i = 0;
             String lastSelectedCamera = "";
             for (String camera : cameras_) {
                if (selectedCameras_.get(camera)) {
                   channelIndex_.put(camera, i);
                   i++;
                   core_.setProperty("Core", "Camera", camera);
                   core_.prepareSequenceAcquisition(camera);
                   core_.startContinuousSequenceAcquisition(0);
                   lastSelectedCamera = camera;
                }
             }
             liveRunning_ = true;
             LiveImagingThread th = new LiveImagingThread(lastSelectedCamera,
                     nrSelectedCameras);
             th.start();
          } catch (Exception ex) {
             ReportingUtils.showError(ex);
          }
       } else  { // (start == false)
           for (String camera : cameras_) {
              if (selectedCameras_.get(camera)) {
                 try {
                    core_.stopSequenceAcquisition(camera);
                 } catch (Exception ex) {
                     enableLiveMode(false);
                     ReportingUtils.showError(ex);
                 }
              }
           }
           setLiveRunning(false);
         try {
            if (th_ != null)
               th_.join(1000);
         } catch (Exception ex) {
            ReportingUtils.logError(ex);
         }
       }
   }


    private void updateTemp() {
       String tempText = "";
       try {
          for (String camera : cameras_) {
             if ( selectedCameras_.get(camera)) {
                 if (core_.hasProperty(camera, TEMP)) {
                    tempText += core_.getProperty(camera, TEMP) + "\u00b0" + "C   ";
                 }
             }
          }
       } catch(Exception ex) {
          ReportingUtils.showError(ex, MultiCameraFrame.class.getName() + " encountered an error.");
       }
       tempLabel.setText(tempText);
    }
    private void updateItems(javax.swing.JComboBox comboBox, String property) {
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
          getComboSelection(comboBox, property);
       }
    }

    private void getComboSelection(javax.swing.JComboBox comboBox, String property) {
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

    private void setComboSelection(javax.swing.JComboBox comboBox, String property) {
       if (!initialized(false, false))
          return;
       boolean liveRunning = getLiveMode();
       enableLiveMode(false);
       String val = (String) comboBox.getSelectedItem();
       if (val.equals(MIXED)) {
          getComboSelection(comboBox, property);
          return;
       }
       try {
          for (String camera: cameras_) {
             if (!camera.equals("") && selectedCameras_.get(camera)) {
                core_.setProperty(camera, property, val);
             }
          }
          enableLiveMode(liveRunning);
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
   private javax.swing.JButton ROISetButton;
   private javax.swing.JButton ROIUnsetButton;
   private javax.swing.JLabel SpeedLabel;
   private javax.swing.JLabel TriggerLabel;
   private javax.swing.JComboBox binningComboBox;
   private javax.swing.JComboBox cameraSelectComboBox;
   private javax.swing.JButton exposureButton;
   private javax.swing.JTextField exposureTextField;
   private javax.swing.JComboBox frameTransferComboBox;
   private javax.swing.JComboBox gainComboBox;
   private javax.swing.JCheckBox jCheckBox1;
   private javax.swing.JLabel jLabel1;
   private javax.swing.JLabel jLabel3;
   private javax.swing.JLabel jLabel4;
   private javax.swing.JLabel jLabel5;
   private javax.swing.JLabel jLabel9;
   private javax.swing.JButton liveButton;
   private javax.swing.JComboBox modeComboBox;
   private javax.swing.JButton snapButton;
   private javax.swing.JComboBox speedComboBox;
   private javax.swing.JButton tempButton;
   private javax.swing.JLabel tempLabel;
   private javax.swing.JComboBox triggerComboBox;
   // End of variables declaration//GEN-END:variables


   private void updateCameraList() {
      for (String camera : cameras_) {
         selectedCameras_.put(camera, false);
      }

      String item = (String) cameraSelectComboBox.getSelectedItem();
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
      boolean liveRunning = getLiveMode();
      String currentCamera = "";
      try {
         currentCamera =  core_.getCameraDevice();
         enableLiveMode(false);
         for(String camera: cameras_) {
            if (selectedCameras_.get(camera)) {
               core_.setCameraDevice(camera);
               core_.setROI(roi.x, roi.y, roi.width, roi.height);
            }
         }
         core_.setCameraDevice(currentCamera);
         enableLiveMode(liveRunning);
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

   private void initializeSequence(int nrSelectedCameras) throws MMScriptException {
       int w = (int) core_.getImageWidth();
       int h = (int) core_.getImageHeight();
       int d = (int) core_.getBytesPerPixel();

       if (gui_.acquisitionExists(ACQNAME)) {
          if (w != gui_.getAcquisitionImageWidth(ACQNAME) ||
              h != gui_.getAcquisitionImageHeight(ACQNAME) ||
              d != gui_.getAcquisitionImageByteDepth(ACQNAME) )
             gui_.closeAcquisition(ACQNAME);
       }

       if (!gui_.acquisitionExists(ACQNAME)) {
          gui_.openAcquisition(ACQNAME, "", 1, nrSelectedCameras, 1, true, false);
          gui_.initializeAcquisition(ACQNAME, w, h, d);
       }
       for (int i=0; i< nrSelectedCameras; i++) {
          if (i < COLORS.length)
             gui_.setChannelColor(ACQNAME, i, COLORS[i]);
          else
             gui_.setChannelColor(ACQNAME, i, Color.WHITE);
       }

       int i = 0;
       for (String camera : cameras_) {
          if (selectedCameras_.get(camera)) {
             gui_.setChannelName(ACQNAME, i, camera);
             i++;
          }
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

   private boolean getLiveMode() {
      if (liveRunning_) {
         liveMode_ = PLUGINLIVEMODE;
         return true;
      } else if (dGui_.getLiveMode()) {
         liveMode_ = GUILIVEMODE;
         return true;
      } 
      return false;
   }


   public void propertiesChangedAlert() {
      updateItems(modeComboBox, MODE);
      updateItems(speedComboBox, SPEED);
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
