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

import com.google.common.eventbus.Subscribe;
import java.awt.Toolkit;
import java.awt.event.ItemEvent;
import java.text.ParseException;
import java.util.ArrayList;
import javax.swing.DefaultComboBoxModel;
import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.LayoutStyle;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import mmcorej.CMMCore;
import mmcorej.DeviceType;
import mmcorej.StrVector;
import org.micromanager.Studio;
import org.micromanager.events.PropertiesChangedEvent;
import org.micromanager.events.PropertyChangedEvent;
import org.micromanager.internal.utils.NumberUtils;
import org.micromanager.internal.utils.WindowPositioning;

/**
 * @author Nico Stuurman
 */
public class MultiCameraFrame extends JFrame {
   private static final long serialVersionUID = 1L;
   private final Studio gui_;
   private final CMMCore core_;
   private int emGainMin_ = 4;
   private int emGainMax_ = 1000;
   private final String[] cameras_;
   private ArrayList<String> camerasInUse_;
   private String coreCamera_;
   private boolean initialized_ = false;
   private volatile boolean externalCameraChange_ = false;
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

   /**
    * Creates new form MultiCameraFrame
    *
    * @param gui - handle to instance of the Studio
    * @throws java.lang.Exception
    */
   public MultiCameraFrame(Studio gui) throws Exception {
      gui_ = gui;
      core_ = gui_.getCMMCore();

      mmcorej.StrVector cameras = core_.getLoadedDevicesOfType(DeviceType.CameraDevice);
      cameras_ = cameras.toArray();

      if (cameras_.length < 1) {
         gui_.logs().showError("This plugin needs at least one camera");
         throw new IllegalArgumentException("This plugin needs at least one camera");
      }

      String currentCamera = core_.getCameraDevice();
      updateCamerasInUse(currentCamera);

      core_.getImageWidth();
      core_.getImageHeight();

      if (cameras_.length < 1) {
         gui_.logs().showError("This plugin needs at least one camera");
         throw new IllegalArgumentException("This plugin needs at least one camera");
      }

      // find the first non-multi-channel camera to test properties
      final String testCamera = camerasInUse_.get(0);

      initComponents();

      super.setIconImage(Toolkit.getDefaultToolkit().getImage(
            getClass().getResource("/org/micromanager/icons/microscope.gif")));
      super.setLocation(100, 100);
      WindowPositioning.setUpLocationMemory(this, this.getClass(), null);

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
         emGainTextField.setEnabled(false);
         emGainSlider.setEnabled(false);
      } else {
         emGainMin_ = (int) core_.getPropertyLowerLimit(testCamera, EMGAIN);
         emGainMax_ = (int) core_.getPropertyUpperLimit(testCamera, EMGAIN);
         emGainSlider.setMinimum(emGainMin_);
         emGainSlider.setMaximum(emGainMax_);
         int gain = NumberUtils.coreStringToInt(core_.getProperty(testCamera, EMGAIN));
         emGainSlider.setValue(gain);
         emGainTextField.setText(NumberUtils.intToDisplayString(gain));

         if (!core_.hasProperty(testCamera, EMSWITCH)) {
            emCheckBox.setEnabled(false);
         } else {
            String val = core_.getProperty(testCamera, EMSWITCH);
            if (val.equals("On")) {
               emCheckBox.setSelected(true);
            }
         }
      }

      // Pre-amp Gain
      if (!core_.hasProperty(testCamera, AMPGAIN)) {
         gainLabel.setEnabled(false);
         gainComboBox.setEnabled(false);
      } else {
         updateItems(gainComboBox, AMPGAIN);
      }

      // Readout speed
      if (!core_.hasProperty(testCamera, SPEED)) {
         speedLabel.setEnabled(false);
         speedComboBox.setEnabled(false);
      } else {
         updateItems(speedComboBox, SPEED);
      }

      // Frame Transfer
      if (!core_.hasProperty(testCamera, FRAMETRANSFER)) {
         frameTransferLabel.setEnabled(false);
         frameTransferComboBox.setEnabled(false);
      } else {
         updateItems(frameTransferComboBox, FRAMETRANSFER);
      }

      // Trigger
      if (!core_.hasProperty(testCamera, TRIGGER)) {
         triggerLabel.setEnabled(false);
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

   /**
    * This method is called from within the constructor to
    * initialize the form.
    * WARNING: Do NOT modify this code. The content of this method is
    * always regenerated by the Form Editor.
    */
   @SuppressWarnings("unchecked")
   // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
   private void initComponents() {

      jCheckBox1 = new JCheckBox();
      emGainSlider = new JSlider();
      emGainTextField = new JTextField();
      modeComboBox = new JComboBox();
      jLabel4 = new JLabel();
      jLabel5 = new JLabel();
      gainLabel = new JLabel();
      speedComboBox = new JComboBox();
      speedLabel = new JLabel();
      emCheckBox = new JCheckBox();
      gainComboBox = new JComboBox();
      frameTransferLabel = new JLabel();
      frameTransferComboBox = new JComboBox();
      jLabel9 = new JLabel();
      cameraSelectComboBox = new JComboBox();
      triggerLabel = new JLabel();
      triggerComboBox = new JComboBox();
      tempButton = new JButton();
      tempLabel = new JLabel();
      jLabel1 = new JLabel();

      jCheckBox1.setText("jCheckBox1");

      setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
      setTitle("Andor Control");
      setResizable(false);

      emGainSlider.addMouseListener(new java.awt.event.MouseAdapter() {
         public void mouseReleased(java.awt.event.MouseEvent evt) {
            emGainSliderMouseReleased(evt);
         }
      });

      emGainTextField.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      emGainTextField.setText("4");
      emGainTextField.addFocusListener(new java.awt.event.FocusAdapter() {
         public void focusLost(java.awt.event.FocusEvent evt) {
            emGainTextFieldFocusLost(evt);
         }
      });
      emGainTextField.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            emGainTextFieldActionPerformed(evt);
         }
      });
      emGainTextField.addKeyListener(new java.awt.event.KeyAdapter() {
         public void keyReleased(java.awt.event.KeyEvent evt) {
            emGainTextFieldKeyReleased(evt);
         }
      });

      modeComboBox.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      modeComboBox.setModel(
            new DefaultComboBoxModel(new String[] {"EM", "Conventional"}));
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

      gainLabel.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      gainLabel.setText("Gain");

      speedComboBox.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      speedComboBox.setModel(
            new DefaultComboBoxModel(new String[] {"1MHz", "3MHz", "5MHz", "10MHz"}));
      speedComboBox.addItemListener(new java.awt.event.ItemListener() {
         public void itemStateChanged(java.awt.event.ItemEvent evt) {
            speedComboBoxItemStateChanged(evt);
         }
      });

      speedLabel.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      speedLabel.setText("Speed");

      emCheckBox.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      emCheckBox.setText("Use");
      emCheckBox.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            emCheckBoxActionPerformed(evt);
         }
      });

      gainComboBox.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      gainComboBox.setModel(
            new DefaultComboBoxModel(new String[] {"1", "2", "3", "4", "5"}));
      gainComboBox.addItemListener(new java.awt.event.ItemListener() {
         public void itemStateChanged(java.awt.event.ItemEvent evt) {
            gainComboBoxItemStateChanged(evt);
         }
      });

      frameTransferLabel.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      frameTransferLabel.setText("OverlapMode");

      frameTransferComboBox.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      frameTransferComboBox.setModel(
            new DefaultComboBoxModel(new String[] {"On", "Off"}));
      frameTransferComboBox.addItemListener(new java.awt.event.ItemListener() {
         public void itemStateChanged(java.awt.event.ItemEvent evt) {
            frameTransferComboBoxItemStateChanged(evt);
         }
      });

      jLabel9.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      jLabel9.setText("Active Camera");

      cameraSelectComboBox.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      cameraSelectComboBox.setModel(
            new DefaultComboBoxModel(new String[] {"1", "2", "4", "8"}));
      cameraSelectComboBox.addItemListener(new java.awt.event.ItemListener() {
         public void itemStateChanged(java.awt.event.ItemEvent evt) {
            cameraSelectComboBoxItemStateChanged(evt);
         }
      });

      triggerLabel.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      triggerLabel.setText("Trigger");

      triggerComboBox.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      triggerComboBox.setModel(new DefaultComboBoxModel(new String[] {"On", "Off"}));
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

      GroupLayout layout = new GroupLayout(getContentPane());
      getContentPane().setLayout(layout);
      layout.setHorizontalGroup(
            layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                  .addGroup(layout.createSequentialGroup()
                        .addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGroup(
                              layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                    .addGroup(layout.createSequentialGroup()
                                          .addGroup(layout.createParallelGroup(
                                                      GroupLayout.Alignment.LEADING)
                                                .addComponent(tempButton,
                                                      GroupLayout.PREFERRED_SIZE, 63,
                                                      GroupLayout.PREFERRED_SIZE)
                                                .addComponent(jLabel5)
                                                .addComponent(jLabel4)
                                                .addComponent(gainLabel)
                                                .addComponent(speedLabel)
                                                .addComponent(frameTransferLabel)
                                                .addComponent(triggerLabel))
                                          .addGap(48, 48, 48)
                                          .addGroup(layout.createParallelGroup(
                                                      GroupLayout.Alignment.LEADING, false)
                                                .addComponent(speedComboBox,
                                                      GroupLayout.PREFERRED_SIZE, 105,
                                                      GroupLayout.PREFERRED_SIZE)
                                                .addGroup(layout.createSequentialGroup()
                                                      .addGap(50, 50, 50)
                                                      .addComponent(emGainSlider,
                                                            GroupLayout.PREFERRED_SIZE,
                                                            180,
                                                            GroupLayout.PREFERRED_SIZE))
                                                .addComponent(triggerComboBox,
                                                      GroupLayout.PREFERRED_SIZE, 105,
                                                      GroupLayout.PREFERRED_SIZE)
                                                .addComponent(modeComboBox,
                                                      GroupLayout.PREFERRED_SIZE, 105,
                                                      GroupLayout.PREFERRED_SIZE)
                                                .addComponent(gainComboBox,
                                                      GroupLayout.PREFERRED_SIZE, 105,
                                                      GroupLayout.PREFERRED_SIZE)
                                                .addComponent(emCheckBox)
                                                .addComponent(emGainTextField,
                                                      GroupLayout.PREFERRED_SIZE, 49,
                                                      GroupLayout.PREFERRED_SIZE)
                                                .addComponent(frameTransferComboBox,
                                                      GroupLayout.PREFERRED_SIZE, 105,
                                                      GroupLayout.PREFERRED_SIZE)
                                                .addGroup(layout.createSequentialGroup()
                                                      .addComponent(tempLabel,
                                                            GroupLayout.PREFERRED_SIZE,
                                                            202,
                                                            GroupLayout.PREFERRED_SIZE)
                                                      .addPreferredGap(
                                                            LayoutStyle.ComponentPlacement.RELATED,
                                                            GroupLayout.DEFAULT_SIZE,
                                                            Short.MAX_VALUE)
                                                      .addComponent(jLabel1))))
                                    .addGroup(layout.createSequentialGroup()
                                          .addComponent(jLabel9)
                                          .addGap(7, 7, 7)
                                          .addComponent(cameraSelectComboBox,
                                                GroupLayout.PREFERRED_SIZE, 191,
                                                GroupLayout.PREFERRED_SIZE)))
                        .addContainerGap())
      );
      layout.setVerticalGroup(
            layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                  .addGroup(layout.createSequentialGroup()
                        .addGap(10, 10, 10)
                        .addGroup(
                              layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                    .addComponent(jLabel9)
                                    .addComponent(cameraSelectComboBox,
                                          GroupLayout.PREFERRED_SIZE, 23,
                                          GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(
                              layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                    .addGroup(layout.createSequentialGroup()
                                          .addGap(6, 6, 6)
                                          .addComponent(jLabel5)
                                          .addGap(22, 22, 22)
                                          .addComponent(jLabel4)
                                          .addGap(16, 16, 16)
                                          .addComponent(gainLabel)
                                          .addGap(12, 12, 12)
                                          .addComponent(speedLabel)
                                          .addGap(12, 12, 12)
                                          .addComponent(frameTransferLabel)
                                          .addGap(12, 12, 12)
                                          .addComponent(triggerLabel))
                                    .addGroup(layout.createSequentialGroup()
                                          .addGap(90, 90, 90)
                                          .addComponent(speedComboBox,
                                                GroupLayout.PREFERRED_SIZE,
                                                GroupLayout.DEFAULT_SIZE,
                                                GroupLayout.PREFERRED_SIZE))
                                    .addGroup(layout.createSequentialGroup()
                                          .addGap(25, 25, 25)
                                          .addComponent(emGainSlider,
                                                GroupLayout.PREFERRED_SIZE,
                                                GroupLayout.DEFAULT_SIZE,
                                                GroupLayout.PREFERRED_SIZE))
                                    .addGroup(layout.createSequentialGroup()
                                          .addGap(140, 140, 140)
                                          .addComponent(triggerComboBox,
                                                GroupLayout.PREFERRED_SIZE,
                                                GroupLayout.DEFAULT_SIZE,
                                                GroupLayout.PREFERRED_SIZE))
                                    .addComponent(modeComboBox,
                                          GroupLayout.PREFERRED_SIZE,
                                          GroupLayout.DEFAULT_SIZE,
                                          GroupLayout.PREFERRED_SIZE)
                                    .addGroup(layout.createSequentialGroup()
                                          .addGap(65, 65, 65)
                                          .addComponent(gainComboBox,
                                                GroupLayout.PREFERRED_SIZE,
                                                GroupLayout.DEFAULT_SIZE,
                                                GroupLayout.PREFERRED_SIZE))
                                    .addGroup(layout.createSequentialGroup()
                                          .addGap(45, 45, 45)
                                          .addComponent(emCheckBox))
                                    .addGroup(layout.createSequentialGroup()
                                          .addGap(25, 25, 25)
                                          .addComponent(emGainTextField,
                                                GroupLayout.PREFERRED_SIZE,
                                                GroupLayout.DEFAULT_SIZE,
                                                GroupLayout.PREFERRED_SIZE))
                                    .addGroup(layout.createSequentialGroup()
                                          .addGap(115, 115, 115)
                                          .addComponent(frameTransferComboBox,
                                                GroupLayout.PREFERRED_SIZE,
                                                GroupLayout.DEFAULT_SIZE,
                                                GroupLayout.PREFERRED_SIZE)))
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(
                              layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                    .addComponent(tempButton,
                                          GroupLayout.PREFERRED_SIZE, 17,
                                          GroupLayout.PREFERRED_SIZE)
                                    .addComponent(tempLabel)
                                    .addComponent(jLabel1))
                        .addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
      );

      pack();
   }

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
         gui_.logs().showError(ex, MultiCameraFrame.class.getName() + " encountered an error.");
      }
      return gain;
   }

   private void setEMGain() {
      if (!initialized(false, false)) {
         return;
      }
      gui_.live().setSuspended(true);
      int val = emGainSlider.getValue();
      try {
         for (String camera : camerasInUse_) {
            setPropertyIfPossible(camera, EMGAIN, NumberUtils.intToCoreString(val));
         }
         gui_.app().refreshGUI();
      } catch (Exception ex) {
         gui_.logs().showError(ex, MultiCameraFrame.class.getName() + " encountered an error.");
      } finally {
         gui_.live().setSuspended(false);
      }
      emGainTextField.setText(NumberUtils.intToDisplayString(val));
   }

   /*
    * Signals whether the first selected camera has its EM switch on or off
    */
   private boolean getEMSwitch() {
      try {
         for (String camera : camerasInUse_) {
            if (core_.hasProperty(camera, EMSWITCH)) {
               String onOff = core_.getProperty(camera, EMSWITCH);
               if (onOff.equals("On")) {
                  return true;
               }
            } else {
               return false;
            }

         }
      } catch (Exception ex) {
         gui_.logs().showError(ex, MultiCameraFrame.class.getName() + " encountered an error.");
      }
      return false;
   }

   private void handleEMGainTextFieldEvent() {
      if (!initialized(false, false)) {
         return;
      }
      try {

         int val = NumberUtils.displayStringToInt(emGainTextField.getText());
         if (val > emGainMax_) {
            val = emGainMax_;
         }
         if (val < emGainMin_) {
            val = emGainMin_;
         }
         emGainSlider.setEnabled(true);
         emGainSlider.setValue(val);
         setEMGain();

      } catch (ParseException e) {
         // ignore if the user types garbage
      }
   }

   private void emGainTextFieldActionPerformed(
         java.awt.event.ActionEvent evt) {
      handleEMGainTextFieldEvent();
   }

   private void emCheckBoxActionPerformed(
         java.awt.event.ActionEvent evt) {
      // EM enable
      if (!initialized(false, false)) {
         return;
      }
      gui_.live().setSuspended(true);
      boolean on = emCheckBox.isSelected();
      String command = "Off";
      if (on) {
         command = "On";
      }
      try {
         for (String camera : camerasInUse_) {
            setPropertyIfPossible(camera, EMSWITCH, command);
         }
      } catch (Exception ex) {
         gui_.logs().showError(ex, MultiCameraFrame.class.getName() + " encountered an error.");
      } finally {
         gui_.live().setSuspended(false);
      }
   }

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
         gui_.logs().showError(ex, MultiCameraFrame.class.getName() + " encountered an error.");
      }
      return mode;
   }

   private void emGainSliderMouseReleased(
         java.awt.event.MouseEvent evt) {
      setEMGain();
      gui_.app().refreshGUI();
   }

   private void tempButtonActionPerformed(
         java.awt.event.ActionEvent evt) {
      gui_.live().setSuspended(true);
      updateTemp();
      gui_.live().setSuspended(false);
   }

   private void modeComboBoxItemStateChanged(
         java.awt.event.ItemEvent evt) {
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

      gui_.live().setSuspended(true);
      String mode = item.toString();
      try {
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

         gui_.app().refreshGUI();
      } catch (Exception ex) {
         gui_.logs().showError(ex, MultiCameraFrame.class.getName() + " encountered an error.");
      } finally {
         gui_.live().setSuspended(false);
      }
   }

   private void gainComboBoxItemStateChanged(
         java.awt.event.ItemEvent evt) {
      if (!(evt.getStateChange() == ItemEvent.SELECTED)) {
         return;
      }
      setComboSelection(gainComboBox, AMPGAIN);
      gui_.app().refreshGUI();
   }

   private void speedComboBoxItemStateChanged(
         java.awt.event.ItemEvent evt) {
      if (!(evt.getStateChange() == ItemEvent.SELECTED)) {
         return;
      }
      setComboSelection(speedComboBox, SPEED);
      gui_.app().refreshGUI();
   }

   private void frameTransferComboBoxItemStateChanged(
         java.awt.event.ItemEvent evt) {
      if (!(evt.getStateChange() == ItemEvent.SELECTED)) {
         return;
      }
      setComboSelection(frameTransferComboBox, FRAMETRANSFER);
      gui_.app().refreshGUI();
   }

   private void triggerComboBoxItemStateChanged(
         java.awt.event.ItemEvent evt) {
      if (!(evt.getStateChange() == ItemEvent.SELECTED)) {
         return;
      }
      setComboSelection(triggerComboBox, TRIGGER);
      gui_.app().refreshGUI();
   }

   private void updateCamerasInUse(String camera) {
      camerasInUse_ = new ArrayList<String>();
      if (core_.getNumberOfCameraChannels() == 1) {
         camerasInUse_.add(camera);
      } else if (core_.getNumberOfCameraChannels() > 1) {
         try {
            if (core_.hasProperty(camera, PHYSCAM1)) {
               for (String prop : new String[] {PHYSCAM1, PHYSCAM2, PHYSCAM3, PHYSCAM4}) {
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
            gui_.logs().logError(ex);
         }
      }
   }

   private void cameraSelectComboBoxItemStateChanged(
         java.awt.event.ItemEvent evt) {
      if (!(evt.getStateChange() == ItemEvent.SELECTED)) {
         return;
      }
      if (!initialized(false, false)) {
         return;
      }

      gui_.live().setSuspended(true);
      // since the core call to set the camera will result in a call
      // back into our code that will set the externalCameraChange_ flag
      // we need to record its value now
      boolean externalCameraChange = externalCameraChange_;
      externalCameraChange_ = false;
      try {
         // Use the initialize flag to prevent pushing settings back to the hardware
         initialized(true, false);

         coreCamera_ = (String) cameraSelectComboBox.getSelectedItem();
         String currentCamera = core_.getProperty("Core", "Camera");

         if (!currentCamera.equals(coreCamera_)) {
            core_.setProperty("Core", "Camera", coreCamera_);
         }
         updateCamerasInUse(coreCamera_);

         String fsCamera = camerasInUse_.get(0);

         adjustUIToCamera(fsCamera);
         updateTemp();
         initialized(true, true);

      } catch (Exception ex) {
         gui_.logs().showError(ex, MultiCameraFrame.class.getName() + " encountered an error.");
      } finally {
         gui_.live().setSuspended(false);
      }
      if (!externalCameraChange) {
         gui_.app().refreshGUI();
      }
   }

   private void modeComboBoxActionPerformed(
         java.awt.event.ActionEvent evt) {
   }

   private void emGainTextFieldKeyReleased(
         java.awt.event.KeyEvent evt) {

   }

   private void emGainTextFieldFocusLost(
         java.awt.event.FocusEvent evt) {
      handleEMGainTextFieldEvent();
   }

   private void updateTemp() {
      String tempText = "";
      try {
         for (String camera : camerasInUse_) {
            if (core_.hasProperty(camera, TEMP)) {
               tempText += core_.getProperty(camera, TEMP) + "\u00b0" + "C   "; // U+00B0 DEGREE SIGN
            }

         }
      } catch (Exception ex) {
         gui_.logs().showError(ex, MultiCameraFrame.class.getName() + " encountered an error.");
      }
      tempLabel.setText(tempText);
   }

   private void updateItems(JComboBox comboBox, String property) {
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
            gui_.logs().showError(ex, MultiCameraFrame.class.getName() + " encountered an error.");
         }
         getComboSelection(comboBox, property);
      }
   }

   private void getComboSelection(JComboBox comboBox, String property) {
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
         gui_.logs().showError(ex, MultiCameraFrame.class.getName() + " encountered an error.");
      }
   }

   private void setComboSelection(JComboBox comboBox, String property) {
      if (!initialized(false, false)) {
         return;
      }
      gui_.live().setSuspended(true);
      String val = (String) comboBox.getSelectedItem();
      if (val.equals(MIXED)) {
         getComboSelection(comboBox, property);
         return;
      }
      try {
         for (String camera : camerasInUse_) {
            core_.setProperty(camera, property, val);
         }
      } catch (Exception ex) {
         gui_.logs().showError(ex, MultiCameraFrame.class.getName() + " encountered an error.");
      } finally {
         gui_.live().setSuspended(false);
      }
   }

   // Variables declaration - do not modify//GEN-BEGIN:variables
   private JCheckBox emCheckBox;
   private JSlider emGainSlider;
   private JTextField emGainTextField;
   private JLabel frameTransferLabel;
   private JLabel gainLabel;
   private JLabel speedLabel;
   private JLabel triggerLabel;
   private JComboBox cameraSelectComboBox;
   private JComboBox frameTransferComboBox;
   private JComboBox gainComboBox;
   private JCheckBox jCheckBox1;
   private JLabel jLabel1;
   private JLabel jLabel4;
   private JLabel jLabel5;
   private JLabel jLabel9;
   private JComboBox modeComboBox;
   private JComboBox speedComboBox;
   private JButton tempButton;
   private JLabel tempLabel;
   private JComboBox triggerComboBox;
   // End of variables declaration//GEN-END:variables


   @Subscribe
   public void onPropertiesChanged(PropertiesChangedEvent event) {
      updateItems(speedComboBox, SPEED);
   }

   @Subscribe
   public void onPropertyChanged(PropertyChangedEvent event) {
      final String device = event.getDevice();
      final String property = event.getProperty();
      final String value = event.getValue();
      try {
         if (device.equals("Core") && property.equals("Camera")
               && !value.equals(cameraSelectComboBox.getSelectedItem())) {
            SwingUtilities.invokeLater(new Runnable() {
               @Override
               public void run() {
                  externalCameraChange_ = true;
                  cameraSelectComboBox.setSelectedItem(value);
               }
            });
         }
      } catch (Exception ex) {
         gui_.logs().logError(ex);
      }
   }

   private void setPropertyIfPossible(String device, String property, String value) {
      try {
         if (core_.hasProperty(device, property)) {
            core_.setProperty(device, property, value);
         }
      } catch (Exception ex) {
         gui_.logs().logError(ex);
      }
   }


}
