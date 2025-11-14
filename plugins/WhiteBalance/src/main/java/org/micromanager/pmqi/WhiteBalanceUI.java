///////////////////////////////////////////////////////////////////////////////
//FILE:          AutoWB.java
//PROJECT:       PMQI_WhiteBalance
//-----------------------------------------------------------------------------
//AUTHOR:        Andrej Bencur, abencur@photometrics.com, April 14, 2015
//COPYRIGHT:     QImaging, Surrey, BC, 2015
//LICENSE:       This file is distributed under the BSD license.
//               License text is included with the source distribution.
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.pmqi;

import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.LayoutStyle;
import javax.swing.WindowConstants;
import mmcorej.CMMCore;
import mmcorej.MMCoreJ;
import org.micromanager.Studio;
import org.micromanager.events.PropertyChangedEvent;

/**
 * @author Andrej
 */
public class WhiteBalanceUI extends JFrame {

   /**
    * Creates new form WhiteBalance_UI
    */

   private static final int WB_FAILED_TOO_MANY_ITERATIONS = 0;
   private static final int WB_FAILED_TOO_MANY_ITERATIONS_TOO_BRIGHT = 1;
   private static final int WB_FAILED_TOO_MANY_ITERATIONS_TOO_DARK = 2;
   private static final int WB_FAILED_SATURATED_FROM_START = 3;
   private static final int WB_FAILED_TOO_DARK_FROM_START = 4;
   private static final int WB_FAILED_EXP_TOO_SHORT = 5;
   private static final int WB_FAILED_EXP_TOO_LONG = 6;
   private static final int WB_FAILED_EXCEPTION = 7;
   private static final int WB_SUCCESS = 8;

   private static final int MIN_EXPOSURE = 2;
   private static final int MAX_EXPOSURE = 2000;

   private static final int WB_EXP_ITERATIONS_MAX = 20;
   private static final double WB_SUCCESS_SNAP_FACTOR = 1.75;

   // CFA_* values serve also as index for combo box
   private static final int CFA_RGGB = 0;
   private static final int CFA_BGGR = 1;
   private static final int CFA_GRBG = 2;
   private static final int CFA_GBRG = 3;

   private static final int DEPTH8BIT = 8;
   private static final int DEPTH8BIT_MEAN_MIN = 100;
   private static final int DEPTH8BIT_MEAN_MAX = 200;
   private static final int DEPTH10BIT = 10;
   private static final int DEPTH10BIT_MEAN_MIN = 420;
   private static final int DEPTH10BIT_MEAN_MAX = 600;
   private static final int DEPTH12BIT = 12;
   private static final int DEPTH12BIT_MEAN_MIN = 1700;
   private static final int DEPTH12BIT_MEAN_MAX = 2300;
   private static final int DEPTH14BIT = 14;
   private static final int DEPTH14BIT_MEAN_MIN = 6600;
   private static final int DEPTH14BIT_MEAN_MAX = 8500;
   private static final int DEPTH16BIT = 16;
   private static final int DEPTH16BIT_MEAN_MIN = 27000;
   private static final int DEPTH16BIT_MEAN_MAX = 33000;

   private static final int EXP_1MS = 1;
   private static final int EXP_100MS = 100;

   private static final int ADU_BIAS_16BIT = 500;
   private static final int ADU_BIAS_LESS16BIT = 150;

   // Keywords must match the values in DeviceAdapters/PVCAM/PVCAMUniversal.cpp
   // The same values with similar names are also in DeviceAdapters/QCam/QICamera.cpp
   // g_Keyword_ChipName (PVCAM only)
   private static final String KEYWORD_CHIP_NAME = "ChipName";
   private static final String KEYWORD_COLOR = "Color"; // g_Keyword_Color
   // g_Keyword_RedScale
   private static final String KEYWORD_RED_SCALE = "Color - Red scale";
   // g_Keyword_BlueScale
   private static final String KEYWORD_BLUE_SCALE = "Color - Blue scale";
   // g_Keyword_GreenScale
   private static final String KEYWORD_GREEN_SCALE = "Color - Green scale";
   // g_Keyword_AlgorithmCFA
   private static final String KEYWORD_ALGORITHM_CFA = "Color - Algorithm CFA";
   private static final String KEYWORD_RGGB = "R-G-G-B"; // g_Keyword_RGGB
   private static final String KEYWORD_BGGR = "B-G-G-R"; // g_Keyword_BGGR
   private static final String KEYWORD_GRBG = "G-R-B-G"; // g_Keyword_GRBG
   private static final String KEYWORD_GBRG = "G-B-R-G"; // g_Keyword_GBRG
   private static final String KEYWORD_ON = "ON"; // g_Keyword_ON
   private static final String KEYWORD_OFF = "OFF"; // g_Keyword_OFF

   private int cfaPattern_; // Updated before every run, depends on ROI
   private int cameraBitDepth_; // Updated before every run

   private ShortProcessor capturedImageShort_;

   private int wbMeanMin_;
   private int wbMeanMax_;
   private int wbResult_;
   private double wbExposure_;

   private double rMean_;
   private double gMean_;
   private double bMean_;

   private double redScale_;
   private double greenScale_;
   private double blueScale_;

   private final Studio gui_;
   private final CMMCore core_;
   private final String camera_;

   public WhiteBalanceUI(Studio gui) throws Exception {
      gui_ = gui;
      try {
         core_ = gui_.getCMMCore();
      } catch (Exception ex) {
         throw new Exception("WB plugin could not get MMCore");
      }
      try {
         camera_ = core_.getCameraDevice();
      } catch (Exception ex) {
         throw new Exception("WB plugin could not get camera device from Micro-Manager.");
      }

      initComponents();

      if (!isColorCamera()) {
         throw new Exception("This is not a color camera.");
      }

      //enable color mode in MM before properties update
      try {
         core_.setProperty(camera_, KEYWORD_COLOR, KEYWORD_ON);
      } catch (Exception ex) {
         throw new Exception("Failed to turn on color mode.");
      }

      updateCameraModel();
      updateCFAPattern();
      updateBitDepth();

      wbExposure_ = 0.0;

      btnRunWB.setEnabled(true);

      gui_.app().refreshGUI();
   }

   private boolean isColorCamera() {
      boolean isColor = false;
      try {
         if (core_.hasProperty(camera_, KEYWORD_ALGORITHM_CFA)) {
            isColor = true;
         }
      } catch (Exception ex) {
         gui_.logs().logError(ex);
      }
      return isColor;
   }

   private void updateCameraModel() {
      String model = "Unknown";
      try {
         model = core_.getProperty(camera_, KEYWORD_CHIP_NAME);
      } catch (Exception ex) {
         // Not PVCAM camera
      }
      try {
         model = core_.getProperty(camera_, MMCoreJ.getG_Keyword_CameraName());
      } catch (Exception ex) {
         // Not Qcam camera
      }
      lblCameraModel.setText(model);
   }

   private void updateCFAPattern() {
      // Requires Color property to be ON to get correct value
      String pattern;
      try {
         pattern = core_.getProperty(camera_, KEYWORD_ALGORITHM_CFA);
      } catch (Exception ex) {
         pattern = KEYWORD_RGGB;
         Logger.getLogger(WhiteBalanceUI.class.getName()).log(Level.SEVERE, null, ex);
         JOptionPane.showMessageDialog(this,
               "Failed to retrieve color mask pattern, using " + pattern,
               "Error", JOptionPane.ERROR_MESSAGE);
      }

      int index;
      if (pattern.contains(KEYWORD_RGGB)) {
         index = CFA_RGGB;
      } else if (pattern.contains(KEYWORD_BGGR)) {
         index = CFA_BGGR;
      } else if (pattern.contains(KEYWORD_GRBG)) {
         index = CFA_GRBG;
      } else if (pattern.contains(KEYWORD_GBRG)) {
         index = CFA_GBRG;
      } else {
         JOptionPane.showMessageDialog(this,
               "Unsupported color mask pattern " + pattern,
               "Error", JOptionPane.ERROR_MESSAGE);
         return;
      }
      cbxCFAPattern.setSelectedIndex(index);

      onCbxCFAPatternChanged();
   }

   private void onCbxCFAPatternChanged() {
      int index = cbxCFAPattern.getSelectedIndex();
      switch (index) {
         case CFA_RGGB:
         case CFA_BGGR:
         case CFA_GRBG:
         case CFA_GBRG:
            cfaPattern_ = index;
            break;
         default:
            JOptionPane.showMessageDialog(this,
                  "Unsupported color mask pattern index: " + String.valueOf(index),
                  "Error", JOptionPane.ERROR_MESSAGE);
            return;
      }
   }

   private void updateBitDepth() {
      String bitDepth;
      try {
         bitDepth = core_.getProperty(camera_, MMCoreJ.getG_Keyword_PixelType());
      } catch (Exception ex) {
         bitDepth = "16bit";
         Logger.getLogger(WhiteBalanceUI.class.getName()).log(Level.SEVERE, null, ex);
         JOptionPane.showMessageDialog(this,
               "Failed to retrieve camera bit depth, using " + bitDepth,
               "Error", JOptionPane.ERROR_MESSAGE);
      }

      int index;
      if (bitDepth.contains("8bit")) {
         index = 0;
      } else if (bitDepth.contains("10bit")) {
         index = 1;
      } else if (bitDepth.contains("12bit")) {
         index = 2;
      } else if (bitDepth.contains("14bit")) {
         index = 3;
      } else if (bitDepth.contains("16bit")) {
         index = 4;
      } else {
         JOptionPane.showMessageDialog(this,
               "Unsupported camera bit depth: " + bitDepth,
               "Error", JOptionPane.ERROR_MESSAGE);
         return;
      }
      cbxBitDepth.setSelectedIndex(index);

      onCbxBitDepthChanged();
   }

   private void onCbxBitDepthChanged() {
      int index = cbxBitDepth.getSelectedIndex();
      switch (index) {
         case 0:
            cameraBitDepth_ = DEPTH8BIT;
            wbMeanMin_ = DEPTH8BIT_MEAN_MIN;
            wbMeanMax_ = DEPTH8BIT_MEAN_MAX;
            break;
         case 1:
            cameraBitDepth_ = DEPTH10BIT;
            wbMeanMin_ = DEPTH10BIT_MEAN_MIN;
            wbMeanMax_ = DEPTH10BIT_MEAN_MAX;
            break;
         case 2:
            cameraBitDepth_ = DEPTH12BIT;
            wbMeanMin_ = DEPTH12BIT_MEAN_MIN;
            wbMeanMax_ = DEPTH12BIT_MEAN_MAX;
            break;
         case 3:
            cameraBitDepth_ = DEPTH14BIT;
            wbMeanMin_ = DEPTH14BIT_MEAN_MIN;
            wbMeanMax_ = DEPTH14BIT_MEAN_MAX;
            break;
         case 4:
            cameraBitDepth_ = DEPTH16BIT;
            wbMeanMin_ = DEPTH16BIT_MEAN_MIN;
            wbMeanMax_ = DEPTH16BIT_MEAN_MAX;
            break;
         default:
            JOptionPane.showMessageDialog(this,
                  "Unsupported bit depth index: " + String.valueOf(index),
                  "Error", JOptionPane.ERROR_MESSAGE);
            return;
      }

      lblMeanTarget.setText(
            "<" + String.valueOf(wbMeanMin_) + "-" + String.valueOf(wbMeanMax_) + ">");
   }

   /**
    * This method is called from within the constructor to initialize the form.
    * WARNING: Do NOT modify this code. The content of this method is always
    * regenerated by the Form Editor.
    */
   @SuppressWarnings("unchecked")
   // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
   private void initComponents() {

      jpnlResults = new JPanel();
      jLabel10 = new JLabel();
      jLabel11 = new JLabel();
      lblWBExposure = new JLabel();
      jLabel1 = new JLabel();
      lblMean = new JLabel();
      jLabel3 = new JLabel();
      jLabel4 = new JLabel();
      jLabel5 = new JLabel();
      lblRedMean = new JLabel();
      lblGreenMean = new JLabel();
      lblBlueMean = new JLabel();
      jLabel12 = new JLabel();
      jLabel13 = new JLabel();
      jLabel14 = new JLabel();
      lblRedScale = new JLabel();
      lblGreenScale = new JLabel();
      lblBlueScale = new JLabel();
      jPanel1 = new JPanel();
      jLabel2 = new JLabel();
      jLabel9 = new JLabel();
      jLabel15 = new JLabel();
      jPanel2 = new JPanel();
      jLabel6 = new JLabel();
      btnRunWB = new JButton();
      cbxBitDepth = new JComboBox();
      jLabel8 = new JLabel();
      cbxCFAPattern = new JComboBox();
      lblCameraModel = new JLabel();
      jLabel7 = new JLabel();
      lblMeanTarget = new JLabel();
      jLabel16 = new JLabel();

      setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
      setTitle("PM/QI Automatic White Balance utility");
      setResizable(false);

      jpnlResults.setBorder(
            BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));

      jLabel10.setText("Results");

      jLabel11.setText("Exposure:");

      lblWBExposure.setText("0 ms");

      jLabel1.setText("Mean:");

      lblMean.setText("0");

      jLabel3.setText("Red Mean:");

      jLabel4.setText("Green Mean:");

      jLabel5.setText("Blue Mean:");

      lblRedMean.setText("0");

      lblGreenMean.setText("0");

      lblBlueMean.setText("0");

      jLabel12.setText("Red scale:");

      jLabel13.setText("Green scale:");

      jLabel14.setText("Blue scale:");

      lblRedScale.setText("0");

      lblGreenScale.setText("0");

      lblBlueScale.setText("0");

      GroupLayout jpnlResultsLayout = new GroupLayout(jpnlResults);
      jpnlResults.setLayout(jpnlResultsLayout);
      jpnlResultsLayout.setHorizontalGroup(
            jpnlResultsLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                  .addGroup(jpnlResultsLayout.createSequentialGroup()
                        .addGap(20, 20, 20)
                        .addGroup(jpnlResultsLayout.createParallelGroup(
                                    GroupLayout.Alignment.LEADING)
                              .addComponent(jLabel11)
                              .addComponent(jLabel1)
                              .addComponent(jLabel3)
                              .addComponent(jLabel4)
                              .addComponent(jLabel5)
                              .addComponent(jLabel12)
                              .addComponent(jLabel13)
                              .addComponent(jLabel14))
                        .addGap(18, 18, 18)
                        .addGroup(jpnlResultsLayout.createParallelGroup(
                                    GroupLayout.Alignment.LEADING)
                              .addComponent(lblGreenScale, GroupLayout.DEFAULT_SIZE,
                                    GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                              .addComponent(lblRedScale, GroupLayout.DEFAULT_SIZE,
                                    GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                              .addComponent(lblBlueScale, GroupLayout.DEFAULT_SIZE,
                                    GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                              .addComponent(lblWBExposure, GroupLayout.DEFAULT_SIZE, 50,
                                    Short.MAX_VALUE)
                              .addGroup(jpnlResultsLayout.createSequentialGroup()
                                    .addGroup(jpnlResultsLayout.createParallelGroup(
                                                GroupLayout.Alignment.LEADING)
                                          .addComponent(lblRedMean)
                                          .addComponent(lblGreenMean)
                                          .addComponent(lblBlueMean))
                                    .addGap(0, 0, Short.MAX_VALUE))
                              .addComponent(lblMean, GroupLayout.DEFAULT_SIZE,
                                    GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addContainerGap())
                  .addGroup(jpnlResultsLayout.createSequentialGroup()
                        .addGap(59, 59, 59)
                        .addComponent(jLabel10)
                        .addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
      );
      jpnlResultsLayout.setVerticalGroup(
            jpnlResultsLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                  .addGroup(jpnlResultsLayout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(jLabel10, GroupLayout.PREFERRED_SIZE, 14,
                              GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(jpnlResultsLayout.createParallelGroup(
                                    GroupLayout.Alignment.BASELINE)
                              .addComponent(jLabel11)
                              .addComponent(lblWBExposure))
                        .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(jpnlResultsLayout.createParallelGroup(
                                    GroupLayout.Alignment.LEADING)
                              .addComponent(jLabel1, GroupLayout.Alignment.TRAILING)
                              .addComponent(lblMean))
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jpnlResultsLayout.createParallelGroup(
                                    GroupLayout.Alignment.LEADING)
                              .addComponent(lblRedMean)
                              .addComponent(jLabel3))
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jpnlResultsLayout.createParallelGroup(
                                    GroupLayout.Alignment.BASELINE)
                              .addComponent(jLabel4)
                              .addComponent(lblGreenMean))
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jpnlResultsLayout.createParallelGroup(
                                    GroupLayout.Alignment.BASELINE)
                              .addComponent(jLabel5)
                              .addComponent(lblBlueMean))
                        .addGap(18, 18, 18)
                        .addGroup(jpnlResultsLayout.createParallelGroup(
                                    GroupLayout.Alignment.BASELINE)
                              .addComponent(jLabel12, GroupLayout.DEFAULT_SIZE,
                                    GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                              .addComponent(lblRedScale, GroupLayout.DEFAULT_SIZE,
                                    GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jpnlResultsLayout.createParallelGroup(
                                    GroupLayout.Alignment.LEADING)
                              .addComponent(lblGreenScale, GroupLayout.DEFAULT_SIZE,
                                    GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                              .addComponent(jLabel13, GroupLayout.Alignment.TRAILING,
                                    GroupLayout.DEFAULT_SIZE,
                                    GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jpnlResultsLayout.createParallelGroup(
                                    GroupLayout.Alignment.LEADING)
                              .addComponent(lblBlueScale)
                              .addComponent(jLabel14))
                        .addContainerGap())
      );

      jPanel1.setBorder(BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));

      jLabel2.setFont(new java.awt.Font("Tahoma", 2, 11)); // NOI18N
      jLabel2.setText("Position the field of view to a white or grey area.  After the WB scales");

      jLabel9.setFont(new java.awt.Font("Tahoma", 2, 11)); // NOI18N
      jLabel9.setText(
            "are found switch off \"Autostretch\" and click \"Full\"  button on each of ");

      jLabel15.setFont(new java.awt.Font("Tahoma", 2, 11)); // NOI18N
      jLabel15.setText("the RGB channels in Micro-Manager.");

      GroupLayout jPanel1Layout = new GroupLayout(jPanel1);
      jPanel1.setLayout(jPanel1Layout);
      jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                  .addGroup(jPanel1Layout.createSequentialGroup()
                        .addContainerGap()
                        .addGroup(jPanel1Layout.createParallelGroup(
                                    GroupLayout.Alignment.LEADING)
                              .addComponent(jLabel15)
                              .addGroup(jPanel1Layout.createParallelGroup(
                                          GroupLayout.Alignment.LEADING, false)
                                    .addComponent(jLabel2, GroupLayout.DEFAULT_SIZE,
                                          GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addComponent(jLabel9, GroupLayout.PREFERRED_SIZE,
                                          336, GroupLayout.PREFERRED_SIZE)))
                        .addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
      );
      jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                  .addGroup(jPanel1Layout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(jLabel2)
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel9, GroupLayout.PREFERRED_SIZE, 14,
                              GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel15)
                        .addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
      );

      jPanel2.setBorder(BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));

      jLabel6.setText("Bit-depth:");

      btnRunWB.setText("Run WB");
      btnRunWB.setName(""); // NOI18N
      btnRunWB.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            btnRunWBActionPerformed(evt);
         }
      });

      cbxBitDepth.setModel(new DefaultComboBoxModel(
            new String[] {"8-bit", "10-bit", "12-bit", "14-bit", "16-bit"}));
      cbxBitDepth.setSelectedIndex(4);
      cbxBitDepth.setEnabled(false);
      cbxBitDepth.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            cbxBitDepthActionPerformed(evt);
         }
      });

      jLabel8.setText("CFA pattern:");

      cbxCFAPattern.setModel(
            new DefaultComboBoxModel(new String[] {"RGGB", "BGGR", "GRBG", "GBRG"}));
      cbxCFAPattern.setSelectedIndex(2);
      cbxCFAPattern.setEnabled(false);
      cbxCFAPattern.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            cbxCFAPatternActionPerformed(evt);
         }
      });

      lblCameraModel.setText("N/A");

      jLabel7.setText("Camera model:");

      lblMeanTarget.setText("<27000-32000>");

      jLabel16.setText("Mean target:");

      GroupLayout jPanel2Layout = new GroupLayout(jPanel2);
      jPanel2.setLayout(jPanel2Layout);
      jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                  .addGroup(GroupLayout.Alignment.TRAILING,
                        jPanel2Layout.createSequentialGroup()
                              .addContainerGap(GroupLayout.DEFAULT_SIZE,
                                    Short.MAX_VALUE)
                              .addGroup(jPanel2Layout.createParallelGroup(
                                          GroupLayout.Alignment.TRAILING)
                                    .addGroup(jPanel2Layout.createSequentialGroup()
                                          .addGroup(jPanel2Layout.createParallelGroup(
                                                      GroupLayout.Alignment.LEADING)
                                                .addComponent(jLabel7)
                                                .addComponent(jLabel8)
                                                .addComponent(jLabel6)
                                                .addComponent(jLabel16))
                                          .addGap(11, 11, 11)
                                          .addGroup(jPanel2Layout.createParallelGroup(
                                                      GroupLayout.Alignment.LEADING)
                                                .addComponent(lblCameraModel)
                                                .addComponent(cbxBitDepth,
                                                      GroupLayout.PREFERRED_SIZE,
                                                      GroupLayout.DEFAULT_SIZE,
                                                      GroupLayout.PREFERRED_SIZE)
                                                .addComponent(cbxCFAPattern,
                                                      GroupLayout.PREFERRED_SIZE, 55,
                                                      GroupLayout.PREFERRED_SIZE)
                                                .addComponent(lblMeanTarget)))
                                    .addGroup(jPanel2Layout.createSequentialGroup()
                                          .addComponent(btnRunWB,
                                                GroupLayout.PREFERRED_SIZE, 110,
                                                GroupLayout.PREFERRED_SIZE)
                                          .addGap(25, 25, 25)))
                              .addGap(148, 148, 148))
      );
      jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                  .addGroup(jPanel2Layout.createSequentialGroup()
                        .addGap(22, 22, 22)
                        .addGroup(jPanel2Layout.createParallelGroup(
                                    GroupLayout.Alignment.BASELINE)
                              .addComponent(jLabel7)
                              .addComponent(lblCameraModel, GroupLayout.PREFERRED_SIZE,
                                    14, GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel2Layout.createParallelGroup(
                                    GroupLayout.Alignment.BASELINE)
                              .addComponent(jLabel6)
                              .addComponent(cbxBitDepth, GroupLayout.PREFERRED_SIZE,
                                    GroupLayout.DEFAULT_SIZE,
                                    GroupLayout.PREFERRED_SIZE))
                        .addGap(5, 5, 5)
                        .addGroup(jPanel2Layout.createParallelGroup(
                                    GroupLayout.Alignment.BASELINE)
                              .addComponent(jLabel16)
                              .addComponent(lblMeanTarget))
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel2Layout.createParallelGroup(
                                    GroupLayout.Alignment.BASELINE)
                              .addComponent(cbxCFAPattern, GroupLayout.PREFERRED_SIZE,
                                    GroupLayout.DEFAULT_SIZE,
                                    GroupLayout.PREFERRED_SIZE)
                              .addComponent(jLabel8))
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED,
                              GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(btnRunWB)
                        .addGap(30, 30, 30))
      );

      GroupLayout layout = new GroupLayout(getContentPane());
      getContentPane().setLayout(layout);
      layout.setHorizontalGroup(
            layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                  .addGroup(layout.createSequentialGroup()
                        .addContainerGap()
                        .addGroup(
                              layout.createParallelGroup(GroupLayout.Alignment.TRAILING)
                                    .addComponent(jPanel1, GroupLayout.PREFERRED_SIZE,
                                          GroupLayout.DEFAULT_SIZE,
                                          GroupLayout.PREFERRED_SIZE)
                                    .addGroup(layout.createSequentialGroup()
                                          .addComponent(jPanel2,
                                                GroupLayout.PREFERRED_SIZE, 189,
                                                GroupLayout.PREFERRED_SIZE)
                                          .addPreferredGap(
                                                LayoutStyle.ComponentPlacement.RELATED)
                                          .addComponent(jpnlResults,
                                                GroupLayout.PREFERRED_SIZE,
                                                GroupLayout.DEFAULT_SIZE,
                                                GroupLayout.PREFERRED_SIZE)))
                        .addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
      );
      layout.setVerticalGroup(
            layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                  .addGroup(layout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(jPanel1, GroupLayout.PREFERRED_SIZE,
                              GroupLayout.DEFAULT_SIZE,
                              GroupLayout.PREFERRED_SIZE)
                        .addGap(11, 11, 11)
                        .addGroup(
                              layout.createParallelGroup(GroupLayout.Alignment.LEADING,
                                          false)
                                    .addComponent(jpnlResults, GroupLayout.DEFAULT_SIZE,
                                          GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addComponent(jPanel2, GroupLayout.DEFAULT_SIZE,
                                          GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
      );

      pack();
   }

   private double findExposureForWB() {
      double exposure = 5.0;
      int iterations = 0;
      double meanAt1ms;
      double meanAt100ms;
      double maxADUval = Math.pow(2, cameraBitDepth_) - 1;

      try {
         //first check if the image is not saturated by doing a short exposure
         snapImage(EXP_1MS);
         meanAt1ms = capturedImageShort_.getStatistics().mean;
         if (meanAt1ms > (maxADUval - maxADUval / 7.0)) {
            wbResult_ = WB_FAILED_SATURATED_FROM_START;
            lblMean.setText(String.valueOf(
                  new BigDecimal(meanAt1ms).setScale(1, BigDecimal.ROUND_HALF_EVEN)));
            lblMean.paintImmediately(lblMean.getVisibleRect());
            JOptionPane.showMessageDialog(this,
                  "Image too bright, please, decrease light levels and try again.",
                  "White Balance Failure", JOptionPane.ERROR_MESSAGE);
            return EXP_1MS;
         }

         //then with a longer exposure check if the image is not too dark
         snapImage(EXP_100MS);
         meanAt100ms = capturedImageShort_.getStatistics().mean;
         if ((meanAt100ms < ADU_BIAS_16BIT && cameraBitDepth_ == DEPTH16BIT)
               || (meanAt100ms < ADU_BIAS_LESS16BIT && cameraBitDepth_ < DEPTH16BIT)) {
            wbResult_ = WB_FAILED_TOO_DARK_FROM_START;
            lblMean.setText(String.valueOf(
                  new BigDecimal(meanAt100ms).setScale(1, BigDecimal.ROUND_HALF_EVEN)));
            lblMean.paintImmediately(lblMean.getVisibleRect());
            JOptionPane.showMessageDialog(this,
                  "Image too dark, please, increase light levels and try again.",
                  "White Balance Failure", JOptionPane.ERROR_MESSAGE);
            return EXP_100MS;
         }

         //start searching for exposre time with mean around the middle of the camera ADU range
         //later this value will be used for grabbing an image and calculating white balanc scales
         snapImage(exposure);
         exposure = exposure * wbMeanMin_ / capturedImageShort_.getStatistics().mean;

         //keep snapping images and adjusting exposure time until desired mean value is reached
         while (iterations < WB_EXP_ITERATIONS_MAX && exposure < MAX_EXPOSURE) {
            snapImage(exposure);

            lblWBExposure.setText(
                  String.valueOf(new BigDecimal(exposure).setScale(0, RoundingMode.HALF_EVEN))
                        + " ms");
            lblWBExposure.paintImmediately(lblWBExposure.getVisibleRect());
            iterations++;

            if (capturedImageShort_.getStatistics().mean < wbMeanMin_) {
               exposure = exposure * wbMeanMin_ / capturedImageShort_.getStatistics().mean;
            } else if (capturedImageShort_.getStatistics().mean > wbMeanMax_) {
               exposure = exposure * wbMeanMin_ / capturedImageShort_.getStatistics().mean;
            } else if (capturedImageShort_.getStatistics().mean > wbMeanMin_
                  && capturedImageShort_.getStatistics().mean < wbMeanMax_) {
               break;
            }
            lblMean.setText(String.valueOf(
                  new BigDecimal(capturedImageShort_.getStatistics().mean).setScale(1,
                        BigDecimal.ROUND_HALF_EVEN)));
            lblMean.paintImmediately(lblMean.getVisibleRect());
         }
      } catch (Exception ex) {
         wbResult_ = WB_FAILED_EXCEPTION;
         lblMean.setText(String.valueOf(
               new BigDecimal(capturedImageShort_.getStatistics().mean).setScale(1,
                     BigDecimal.ROUND_HALF_EVEN)));
         lblMean.paintImmediately(lblMean.getVisibleRect());
         JOptionPane.showMessageDialog(this, "Acquisition error occurred", "White Balance Failure",
               JOptionPane.ERROR_MESSAGE);
         return exposure;
      }

      //check if the exposure search did not end due to algorithm failure - too many iterations,
      //image too dark, image too bright etc.
      if (iterations >= WB_EXP_ITERATIONS_MAX && exposure >= MAX_EXPOSURE) {
         wbResult_ = WB_FAILED_TOO_MANY_ITERATIONS_TOO_DARK;
         lblMean.setText(String.valueOf(
               new BigDecimal(capturedImageShort_.getStatistics().mean).setScale(1,
                     BigDecimal.ROUND_HALF_EVEN)));
         lblMean.paintImmediately(lblMean.getVisibleRect());
         JOptionPane.showMessageDialog(this,
               "Exceeded number of allowed iterations, image too dark.", "White Balance Failure",
               JOptionPane.ERROR_MESSAGE);
         return exposure;
      } else if (iterations >= WB_EXP_ITERATIONS_MAX && exposure < MIN_EXPOSURE) {
         wbResult_ = WB_FAILED_TOO_MANY_ITERATIONS_TOO_BRIGHT;
         lblMean.setText(String.valueOf(
               new BigDecimal(capturedImageShort_.getStatistics().mean).setScale(1,
                     BigDecimal.ROUND_HALF_EVEN)));
         lblMean.paintImmediately(lblMean.getVisibleRect());
         JOptionPane.showMessageDialog(this,
               "Exceeded number of allowed iterations, image too bright.", "White Balance Failure",
               JOptionPane.ERROR_MESSAGE);
         return exposure;
      } else if (iterations >= WB_EXP_ITERATIONS_MAX) {
         wbResult_ = WB_FAILED_TOO_MANY_ITERATIONS;
         lblMean.setText(String.valueOf(
               new BigDecimal(capturedImageShort_.getStatistics().mean).setScale(1,
                     BigDecimal.ROUND_HALF_EVEN)));
         lblMean.paintImmediately(lblMean.getVisibleRect());
         JOptionPane.showMessageDialog(this,
               "Exceeded number of allowed iterations, adjust your light level and try again",
               "White Balance Failure", JOptionPane.ERROR_MESSAGE);
         return exposure;
      } else if (exposure > MAX_EXPOSURE) {
         wbResult_ = WB_FAILED_EXP_TOO_LONG;
         lblMean.setText(String.valueOf(
               new BigDecimal(capturedImageShort_.getStatistics().mean).setScale(1,
                     BigDecimal.ROUND_HALF_EVEN)));
         lblMean.paintImmediately(lblMean.getVisibleRect());
         JOptionPane.showMessageDialog(this,
               "Light level seems to be too low, adjust your light level and try again",
               "White Balance Failure", JOptionPane.ERROR_MESSAGE);
         return exposure;
      } else {
         wbResult_ = WB_SUCCESS;
      }

      return exposure;
   }

   //snap a single image
   private void snapImage(double exposure) throws Exception {
      try {
         core_.setExposure(Math.round(exposure));
         core_.snapImage();
         Object newImage = core_.getImage();
         capturedImageShort_ =
               new ShortProcessor((int) core_.getImageWidth(), (int) core_.getImageHeight(),
                     (short[]) newImage, null);
      } catch (Exception ex) {
         Logger.getLogger(WhiteBalanceUI.class.getName()).log(Level.SEVERE, null, ex);
         throw new Exception("Acquisition failed");
      }
   }

   //use Nearest Neighbor replication algorithm to debayer the image and obtain
   //red, blue and green interpolated pixels and their mean values
   private void debayerImage(ShortProcessor imgToProcess) {
      int one;
      int height = capturedImageShort_.getHeight();
      int width = capturedImageShort_.getWidth();

      ImageProcessor r = new ShortProcessor(width, height);
      ImageProcessor g = new ShortProcessor(width, height);
      ImageProcessor b = new ShortProcessor(width, height);
      ImageProcessor ip = imgToProcess;

      if (cfaPattern_ == CFA_GRBG || cfaPattern_ == CFA_GBRG) {
         for (int y = 1; y < height; y += 2) {
            for (int x = 0; x < width; x += 2) {
               one = ip.getPixel(x, y);
               b.putPixel(x, y, one);
               b.putPixel(x + 1, y, one);
               b.putPixel(x, y + 1, one);
               b.putPixel(x + 1, y + 1, one);
            }
         }
         for (int x = 0; x < width; x += 2) {
            one = ip.getPixel(x, 1);
            b.putPixel(x, 0, one);
            b.putPixel(x + 1, 0, one);
         }

         for (int y = 0; y < height; y += 2) {
            for (int x = 1; x < width; x += 2) {
               one = ip.getPixel(x, y);
               r.putPixel(x, y, one);
               r.putPixel(x + 1, y, one);
               r.putPixel(x, y + 1, one);
               r.putPixel(x + 1, y + 1, one);
            }
         }
         for (int y = 0; y < height; y += 2) {
            one = ip.getPixel(1, y);
            r.putPixel(0, y, one);
            r.putPixel(0, y + 1, one);
         }

         for (int y = 0; y < height; y += 2) {
            for (int x = 0; x < width; x += 2) {
               one = ip.getPixel(x, y);
               g.putPixel(x, y, one);
               g.putPixel(x + 1, y, one);
            }
         }

         for (int y = 1; y < height; y += 2) {
            for (int x = 1; x < width; x += 2) {
               one = ip.getPixel(x, y);
               g.putPixel(x, y, one);
               g.putPixel(x + 1, y, one);
            }
         }
         for (int y = 1; y < height; y += 2) {
            one = ip.getPixel(1, y);
            g.putPixel(0, y, one);
         }

         if (cfaPattern_ == CFA_GRBG) {
            rMean_ = b.getStatistics().mean;
            gMean_ = g.getStatistics().mean;
            bMean_ = r.getStatistics().mean;
         } else if (cfaPattern_ == CFA_GBRG) {
            rMean_ = r.getStatistics().mean;
            gMean_ = g.getStatistics().mean;
            bMean_ = b.getStatistics().mean;
         }

         lblRedMean.setText(
               String.valueOf(new BigDecimal(rMean_).setScale(1, RoundingMode.HALF_EVEN)));
         lblGreenMean.setText(
               String.valueOf(new BigDecimal(gMean_).setScale(1, RoundingMode.HALF_EVEN)));
         lblBlueMean.setText(
               String.valueOf(new BigDecimal(bMean_).setScale(1, RoundingMode.HALF_EVEN)));

      } else if (cfaPattern_ == CFA_RGGB || cfaPattern_ == CFA_BGGR) {
         for (int y = 0; y < height; y += 2) {
            for (int x = 0; x < width; x += 2) {
               one = ip.getPixel(x, y);
               b.putPixel(x, y, one);
               b.putPixel(x + 1, y, one);
               b.putPixel(x, y + 1, one);
               b.putPixel(x + 1, y + 1, one);
            }
         }

         for (int y = 1; y < height; y += 2) {
            for (int x = 1; x < width; x += 2) {
               one = ip.getPixel(x, y);
               r.putPixel(x, y, one);
               r.putPixel(x + 1, y, one);
               r.putPixel(x, y + 1, one);
               r.putPixel(x + 1, y + 1, one);
            }
         }
         for (int y = 1; y < height; y += 2) {
            one = ip.getPixel(1, y);
            r.putPixel(0, y, one);
            r.putPixel(0, y + 1, one);
         }
         for (int x = 1; x < width; x += 2) {
            one = ip.getPixel(x, 1);
            r.putPixel(x, 0, one);
            r.putPixel(x + 1, 0, one);
         }
         one = ip.getPixel(1, 1);
         r.putPixel(0, 0, one);

         for (int y = 0; y < height; y += 2) {
            for (int x = 1; x < width; x += 2) {
               one = ip.getPixel(x, y);
               g.putPixel(x, y, one);
               g.putPixel(x + 1, y, one);
            }
         }
         for (int y = 0; y < height; y += 2) {
            one = ip.getPixel(1, y);
            g.putPixel(0, y, one);
         }

         for (int y = 1; y < height; y += 2) {
            for (int x = 0; x < width; x += 2) {
               one = ip.getPixel(x, y);
               g.putPixel(x, y, one);
               g.putPixel(x + 1, y, one);
            }
         }

         if (cfaPattern_ == CFA_RGGB) {
            rMean_ = b.getStatistics().mean;
            gMean_ = g.getStatistics().mean;
            bMean_ = r.getStatistics().mean;
         } else if (cfaPattern_ == CFA_BGGR) {
            rMean_ = r.getStatistics().mean;
            gMean_ = g.getStatistics().mean;
            bMean_ = b.getStatistics().mean;
         }

         //display mean values of each red, green and blue channel
         lblRedMean.setText(
               String.valueOf(new BigDecimal(rMean_).setScale(1, RoundingMode.HALF_EVEN)));
         lblGreenMean.setText(
               String.valueOf(new BigDecimal(gMean_).setScale(1, RoundingMode.HALF_EVEN)));
         lblBlueMean.setText(
               String.valueOf(new BigDecimal(bMean_).setScale(1, RoundingMode.HALF_EVEN)));
      }
   }

   //calculate the RGB scales based on red, green and blue channels' means
   private void getScales() {
      redScale_ = 1;
      blueScale_ = rMean_ / bMean_;
      greenScale_ = rMean_ / gMean_;

      if ((gMean_ > rMean_) || (bMean_ > rMean_)) {
         if (gMean_ > bMean_) {
            greenScale_ = 1;
            blueScale_ = gMean_ / bMean_;
            redScale_ = gMean_ / rMean_;
         } else {
            blueScale_ = 1;
            redScale_ = bMean_ / rMean_;
            greenScale_ = bMean_ / gMean_;
         }
      }

      //limit the scales to values hardcoded previously in Micro-Manager
      if (redScale_ > 20.0) {
         JOptionPane.showMessageDialog(this, "Red scale greater than 20.0, limiting to 20.",
               "Warning", JOptionPane.WARNING_MESSAGE);
         Logger.getLogger(WhiteBalanceUI.class.getName())
               .log(Level.WARNING, "Red scale greater than 20.0, limiting to 20.");
         redScale_ = 20;
      }

      if (blueScale_ > 20.0) {
         JOptionPane.showMessageDialog(this, "Blue scale greater than 20.0, limiting to 20.",
               "Warning", JOptionPane.WARNING_MESSAGE);
         Logger.getLogger(WhiteBalanceUI.class.getName())
               .log(Level.WARNING, "Blue scale greater than 20.0, limiting to 20.");
         blueScale_ = 20;
      }

      if (greenScale_ > 20.0) {
         JOptionPane.showMessageDialog(this, "Green scale greater than 20.0, limiting to 20.",
               "Warning", JOptionPane.WARNING_MESSAGE);
         Logger.getLogger(WhiteBalanceUI.class.getName())
               .log(Level.WARNING, "Green scale greater than 20.0, limiting to 20.");
         greenScale_ = 20;
      }

      lblRedScale.setText(
            String.valueOf(new BigDecimal(redScale_).setScale(4, RoundingMode.HALF_EVEN)));
      lblGreenScale.setText(
            String.valueOf(new BigDecimal(greenScale_).setScale(4, RoundingMode.HALF_EVEN)));
      lblBlueScale.setText(
            String.valueOf(new BigDecimal(blueScale_).setScale(4, RoundingMode.HALF_EVEN)));
   }

   //start the WB algorithm
   private void btnRunWBActionPerformed(java.awt.event.ActionEvent evt) {
      //enable color mode in MM before properties update
      try {
         core_.setProperty(camera_, KEYWORD_COLOR, KEYWORD_ON);
      } catch (Exception ex) {
         JOptionPane.showMessageDialog(this, "Failed to turn on color mode.",
               "Error", JOptionPane.ERROR_MESSAGE);
         return;
      }

      updateCFAPattern();
      cbxCFAPattern.paintImmediately(cbxCFAPattern.getVisibleRect());

      updateBitDepth();
      cbxBitDepth.paintImmediately(cbxBitDepth.getVisibleRect());
      lblMeanTarget.paintImmediately(lblMeanTarget.getVisibleRect());

      if (cameraBitDepth_ <= DEPTH8BIT || cameraBitDepth_ > DEPTH16BIT) {
         JOptionPane.showMessageDialog(this,
               "This plugin supports 16-bit pixels only.\n"
               + "Current bit depth is " + String.valueOf(cameraBitDepth_) + ".",
               "Error", JOptionPane.ERROR_MESSAGE);
         return;
      }

      lblMean.setText("0");
      lblMean.paintImmediately(lblMean.getVisibleRect());
      lblRedMean.setText("0");
      lblRedMean.paintImmediately(lblRedMean.getVisibleRect());
      lblGreenMean.setText("0");
      lblGreenMean.paintImmediately(lblGreenMean.getVisibleRect());
      lblBlueMean.setText("0");
      lblBlueMean.paintImmediately(lblBlueMean.getVisibleRect());
      lblRedScale.setText("0");
      lblRedScale.paintImmediately(lblRedScale.getVisibleRect());
      lblGreenScale.setText("0");
      lblGreenScale.paintImmediately(lblGreenScale.getVisibleRect());
      lblBlueScale.setText("0");
      lblBlueScale.paintImmediately(lblBlueScale.getVisibleRect());
      btnRunWB.setText("In progress...");
      btnRunWB.setEnabled(false);
      btnRunWB.paintImmediately(btnRunWB.getVisibleRect());

      //stop Live mode in MM if it is currently running
      gui_.live().setSuspended(true);

      //disable color mode in MM before the WB algorithm
      try {
         core_.setProperty(camera_, KEYWORD_COLOR, KEYWORD_OFF);
         gui_.app().refreshGUI();
         wbExposure_ = findExposureForWB();
      } catch (Exception ex) {
         Logger.getLogger(WhiteBalanceUI.class.getName()).log(Level.SEVERE, null, ex);
         btnRunWB.setText("Run WB");
         btnRunWB.setEnabled(true);
         btnRunWB.paintImmediately(btnRunWB.getVisibleRect());
         JOptionPane.showMessageDialog(this,
               "Finding exposure for White Balance algorithm failed.",
               "Error", JOptionPane.ERROR_MESSAGE);
         return;
      }

      lblWBExposure.setText(String.valueOf(
            new BigDecimal(wbExposure_).setScale(0, RoundingMode.HALF_EVEN)) + " ms");

      lblMean.setText(String.valueOf(
            new BigDecimal(capturedImageShort_.getStatistics().mean).setScale(1,
                  BigDecimal.ROUND_HALF_EVEN)));
      lblMean.paintImmediately(lblMean.getVisibleRect());

      //if the correct exposure time has been found snap an image in MM and
      //calculate the RGB scales to balance the image
      try {
         if (wbResult_ == WB_SUCCESS) {
            snapImage(wbExposure_);
            debayerImage(capturedImageShort_);
            getScales();
            core_.setProperty(camera_, KEYWORD_RED_SCALE, redScale_);
            core_.setProperty(camera_, KEYWORD_GREEN_SCALE, greenScale_);
            core_.setProperty(camera_, KEYWORD_BLUE_SCALE, blueScale_);
            core_.setProperty(camera_, KEYWORD_COLOR, KEYWORD_ON);
            gui_.app().refreshGUI();
            gui_.live().snap(true);
         } else {
            core_.setProperty(camera_, KEYWORD_RED_SCALE, 1.0);
            core_.setProperty(camera_, KEYWORD_GREEN_SCALE, 1.0);
            core_.setProperty(camera_, KEYWORD_BLUE_SCALE, 1.0);
            core_.setProperty(camera_, KEYWORD_COLOR, KEYWORD_ON);
            gui_.app().refreshGUI();
         }
      } catch (Exception ex) {
         Logger.getLogger(WhiteBalanceUI.class.getName()).log(Level.SEVERE, null, ex);
         btnRunWB.setText("Run WB");
         btnRunWB.setEnabled(true);
         btnRunWB.paintImmediately(btnRunWB.getVisibleRect());
         JOptionPane.showMessageDialog(this,
               "Could not set White Balance scales as device properties.", "Error",
               JOptionPane.ERROR_MESSAGE);
      }
      btnRunWB.setText("Run WB");
      btnRunWB.setEnabled(true);
      btnRunWB.paintImmediately(btnRunWB.getVisibleRect());

      gui_.live().setSuspended(false);
   }

   private void cbxBitDepthActionPerformed(java.awt.event.ActionEvent evt) {
      onCbxBitDepthChanged();
   }

   private void cbxCFAPatternActionPerformed(java.awt.event.ActionEvent evt) {
      onCbxCFAPatternChanged();
   }

   // Variables declaration - do not modify//GEN-BEGIN:variables
   private JButton btnRunWB;
   private JComboBox cbxBitDepth;
   private JComboBox cbxCFAPattern;
   private JLabel jLabel1;
   private JLabel jLabel10;
   private JLabel jLabel11;
   private JLabel jLabel12;
   private JLabel jLabel13;
   private JLabel jLabel14;
   private JLabel jLabel15;
   private JLabel jLabel16;
   private JLabel jLabel2;
   private JLabel jLabel3;
   private JLabel jLabel4;
   private JLabel jLabel5;
   private JLabel jLabel6;
   private JLabel jLabel7;
   private JLabel jLabel8;
   private JLabel jLabel9;
   private JPanel jPanel1;
   private JPanel jPanel2;
   private JPanel jpnlResults;
   private JLabel lblBlueMean;
   private JLabel lblBlueScale;
   private JLabel lblCameraModel;
   private JLabel lblGreenMean;
   private JLabel lblGreenScale;
   private JLabel lblMean;
   private JLabel lblMeanTarget;
   private JLabel lblRedMean;
   private JLabel lblRedScale;
   private JLabel lblWBExposure;
   // End of variables declaration//GEN-END:variables
}
