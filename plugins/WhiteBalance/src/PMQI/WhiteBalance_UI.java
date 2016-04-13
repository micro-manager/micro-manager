///////////////////////////////////////////////////////////////////////////////
//FILE:          AutoWB.java
//PROJECT:       PMQI_WhiteBalance
//-----------------------------------------------------------------------------
//AUTHOR:        Andrej Bencur, abencur@photometrics.com, August 28, 2015
//COPYRIGHT:     QImaging, Surrey, BC, 2015
//LICENSE:       This file is distributed under the BSD license.
//               License text is included with the source distribution.
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
package PMQI;

import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import mmcorej.CMMCore;
import org.micromanager.api.MMListenerInterface;
import org.micromanager.api.ScriptInterface;
//import 

/**
 *
 * @author Andrej
 */
public class WhiteBalance_UI extends javax.swing.JFrame implements MMListenerInterface {

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
    private static final int CFA_RGGB = 0;
    private static final int CFA_BGGR = 1;
    private static final int CFA_GRBG = 2;
    private static final int CFA_GBRG = 3;
    private static final int DEPTH8BIT = 8;
    private static final int DEPTH10BIT = 10;
    private static final int DEPTH12BIT = 12;
    private static final int DEPTH14BIT = 14;
    private static final int DEPTH16BIT = 16;
    private static final int DEPTH16BIT_MEAN_MIN = 27000;
    private static final int DEPTH16BIT_MEAN_MAX = 33000;
    private static final int DEPTH14BIT_MEAN_MIN = 6600;
    private static final int DEPTH14BIT_MEAN_MAX = 8500;
    private static final int DEPTH12BIT_MEAN_MIN = 1450;
    private static final int DEPTH12BIT_MEAN_MAX = 1900;
    private static final int DEPTH10BIT_MEAN_MIN = 420;
    private static final int DEPTH10BIT_MEAN_MAX = 600;
    private static final int DEPTH8BIT_MEAN_MIN = 85;
    private static final int DEPTH8BIT_MEAN_MAX = 130;
    private static final int EXP_1MS = 1;
    private static final int EXP_100MS = 100;
    private static final int ADU_BIAS_16BIT = 500;
    private static final int ADU_BIAS_10TO16BIT = 150;
    private static final int ADU_BIAS_QI8BIT = 20;

    private int WbMeanMin;
    private int WbMeanMax;
    private int WbResult;

    private static final String RED_SCALE_LABEL = "Color - Red scale";
    private static final String GREEN_SCALE_LABEL = "Color - Green scale";
    private static final String BLUE_SCALE_LABEL = "Color - Blue scale";

    private int CameraBitDepth;
    private int CFApattern;
    private double WBexposure;
    private String CameraModel;
    private boolean IsColorCamera;
    private boolean IsPVCAMCamera = false;
    private boolean IsQCamCamera = false;

    private ImageProcessor CapturedImage;
    private double Rmean;
    private double Gmean;
    private double Bmean;
    private double RedScale;
    private double GreenScale;
    private double BlueScale;
    private final String CameraLabel;
    private final ScriptInterface gui_;
    private final CMMCore core_;

    public WhiteBalance_UI(ScriptInterface gui) throws Exception {

        gui_ = gui;
        try {
            core_ = gui_.getMMCore();
        } catch (Exception ex) {
            throw new Exception("WB plugin could not get MMCore");
        }

        initComponents();

        WBexposure = 0.0;

        try {
            CameraLabel = core_.getCameraDevice();

        } catch (Exception ex) {
            throw new Exception("WB plugin could not get camera device from Micro-Manager.");
        }

        GetCameraModel();

        CheckIsColorCamera();
        if (!IsColorCamera) {
            throw new Exception("This is not a color camera.");
        }

        GetBitDepth();
        GetCFAPattern();

    }

    //private void CheckIsCameraConnected()
    private void CheckIsColorCamera() {
        IsColorCamera = true;
        try {
            if (!core_.hasProperty(CameraLabel, "Color - Red scale")) {
                IsColorCamera = false;
            }
        } catch (Exception ex) {
            Logger.getLogger(WhiteBalance_UI.class.getName()).log(Level.SEVERE, null, ex);
            //JOptionPane.showMessageDialog(this, "Failed to detect camera color type", "Error", JOptionPane.ERROR_MESSAGE);
            IsColorCamera = false;
            return;
        }
        btnRunWB.setEnabled(IsColorCamera);
    }

    private void GetCameraModel() throws Exception {
        String chipName;
        String cameraName;
        String pvcamVersion = "0";
        String qcamVersion = "0";
        try {
            pvcamVersion = core_.getProperty(CameraLabel, "PVCAM Version");
            if (!"0".equals(pvcamVersion)) {
                IsPVCAMCamera = true;
            }
        } catch (Exception ex) {
            try {
                qcamVersion = core_.getProperty(CameraLabel, "QCam Version");
                if (!"0".equals(qcamVersion)) {
                    IsQCamCamera = true;
        }
            } catch (Exception ex2) {
                if ("0".equals(pvcamVersion) && "0".equals(qcamVersion)) {
                    throw new Exception("Failed to read camera model. A PVCAM or QCam compatible camera is required.");
                }
            }
        }

        if (IsPVCAMCamera) {
            chipName = core_.getProperty(CameraLabel, "ChipName");
        if (chipName.contains("QI_Retiga6000C")) {
            CameraModel = "QI Retiga 6000C";
        } else if (chipName.contains("QI_Retiga3000C")) {
            CameraModel = "QI Retiga 3000C";
        } else if (chipName.contains("QI_OptiMOS_M1")) {
            CameraModel = "QI optiMOS color";
        } else {
            CameraModel = chipName;
        }
        } else if (IsQCamCamera) {
            cameraName = core_.getProperty(CameraLabel, "CameraName");
            CameraModel = cameraName;
        }

        lblCameraModel.setText(CameraModel);
    }

    private void GetCFAPattern() {
        String cfaPattern;
        try {
            cfaPattern = core_.getProperty(CameraLabel, "Color - Algorithm CFA");
        } catch (Exception ex) {
            cfaPattern = "R-G-G-B";
            Logger
                    .getLogger(WhiteBalance_UI.class
                            .getName()).log(Level.SEVERE, null, ex);
            JOptionPane.showMessageDialog(this,
                    "Failed to retrieve sensor mask pattern", "Error", JOptionPane.ERROR_MESSAGE);
        }
        SetCFAPattern(cfaPattern);
    }

    private void SetCFAPattern(String pattern) {
        if (pattern.contains("R-G-G-B")) {
            CFApattern = CFA_RGGB;
            cbxCFAPattern.setSelectedIndex(CFA_RGGB);
        } else if (pattern.contains("B-G-G-R")) {
            CFApattern = CFA_BGGR;
            cbxCFAPattern.setSelectedIndex(CFA_BGGR);
        } else if (pattern.contains("G-R-B-G")) {
            CFApattern = CFA_GRBG;
            cbxCFAPattern.setSelectedIndex(CFA_GRBG);
        } else if (pattern.contains("G-B-R-G")) {
            CFApattern = CFA_GBRG;
            cbxCFAPattern.setSelectedIndex(CFA_GBRG);
        } else {
            CFApattern = CFA_RGGB;
            cbxCFAPattern.setSelectedIndex(CFA_RGGB);
        }
    }

    private void GetBitDepth() {
        String bitDepth;
        try {
            bitDepth = core_.getProperty(CameraLabel, "PixelType");
        } catch (Exception ex) {
            bitDepth = "N/A";
            Logger
                    .getLogger(WhiteBalance_UI.class
                            .getName()).log(Level.SEVERE, null, ex);
            JOptionPane.showMessageDialog(this,
                    "Failed to retrieve camera bit depth", "Error", JOptionPane.ERROR_MESSAGE);
        }

        if (bitDepth.contains("8bit")) {
            CameraBitDepth = DEPTH8BIT;
            WbMeanMin = DEPTH8BIT_MEAN_MIN;
            WbMeanMax = DEPTH8BIT_MEAN_MAX;
            cbxBitDepth.setSelectedIndex(0);
        } else if (bitDepth.contains("10bit")) {
            CameraBitDepth = DEPTH10BIT;
            WbMeanMin = DEPTH10BIT_MEAN_MIN;
            WbMeanMax = DEPTH10BIT_MEAN_MAX;
            cbxBitDepth.setSelectedIndex(1);
        } else if (bitDepth.contains("12bit")) {
            CameraBitDepth = DEPTH12BIT;
            WbMeanMin = DEPTH12BIT_MEAN_MIN;
            WbMeanMax = DEPTH12BIT_MEAN_MAX;
            cbxBitDepth.setSelectedIndex(2);
        } else if (bitDepth.contains("14bit")) {
            CameraBitDepth = DEPTH14BIT;
            WbMeanMin = DEPTH14BIT_MEAN_MIN;
            WbMeanMax = DEPTH14BIT_MEAN_MAX;
            cbxBitDepth.setSelectedIndex(3);
        } else if (bitDepth.contains("16bit")) {
            CameraBitDepth = DEPTH16BIT;
            WbMeanMin = DEPTH16BIT_MEAN_MIN;
            WbMeanMax = DEPTH16BIT_MEAN_MAX;
            cbxBitDepth.setSelectedIndex(4);
        } else {
            CameraBitDepth = DEPTH16BIT;
            WbMeanMin = DEPTH16BIT_MEAN_MIN;
            WbMeanMax = DEPTH16BIT_MEAN_MAX;
            cbxBitDepth.setSelectedIndex(4);
        }
        lblMeanTarget.setText("<" + String.valueOf(WbMeanMin) + "-" + String.valueOf(WbMeanMax) + ">");

    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jpnlResults = new javax.swing.JPanel();
        jLabel10 = new javax.swing.JLabel();
        jLabel11 = new javax.swing.JLabel();
        lblWBExposure = new javax.swing.JLabel();
        jLabel1 = new javax.swing.JLabel();
        lblMean = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        jLabel5 = new javax.swing.JLabel();
        lblRedMean = new javax.swing.JLabel();
        lblGreenMean = new javax.swing.JLabel();
        lblBlueMean = new javax.swing.JLabel();
        jLabel12 = new javax.swing.JLabel();
        jLabel13 = new javax.swing.JLabel();
        jLabel14 = new javax.swing.JLabel();
        lblRedScale = new javax.swing.JLabel();
        lblGreenScale = new javax.swing.JLabel();
        lblBlueScale = new javax.swing.JLabel();
        jPanel1 = new javax.swing.JPanel();
        jLabel2 = new javax.swing.JLabel();
        jLabel9 = new javax.swing.JLabel();
        jLabel15 = new javax.swing.JLabel();
        jPanel2 = new javax.swing.JPanel();
        jLabel6 = new javax.swing.JLabel();
        btnRunWB = new javax.swing.JButton();
        cbxBitDepth = new javax.swing.JComboBox();
        jLabel8 = new javax.swing.JLabel();
        cbxCFAPattern = new javax.swing.JComboBox();
        lblCameraModel = new javax.swing.JLabel();
        jLabel7 = new javax.swing.JLabel();
        lblMeanTarget = new javax.swing.JLabel();
        jLabel16 = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("PM/QI Automatic White Balance utility");
        setResizable(false);

        jpnlResults.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));

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

        javax.swing.GroupLayout jpnlResultsLayout = new javax.swing.GroupLayout(jpnlResults);
        jpnlResults.setLayout(jpnlResultsLayout);
        jpnlResultsLayout.setHorizontalGroup(
            jpnlResultsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jpnlResultsLayout.createSequentialGroup()
                .addGap(20, 20, 20)
                .addGroup(jpnlResultsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel11)
                    .addComponent(jLabel1)
                    .addComponent(jLabel3)
                    .addComponent(jLabel4)
                    .addComponent(jLabel5)
                    .addComponent(jLabel12)
                    .addComponent(jLabel13)
                    .addComponent(jLabel14))
                .addGap(18, 18, 18)
                .addGroup(jpnlResultsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(lblGreenScale, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(lblRedScale, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(lblBlueScale, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(lblWBExposure, javax.swing.GroupLayout.DEFAULT_SIZE, 50, Short.MAX_VALUE)
                    .addGroup(jpnlResultsLayout.createSequentialGroup()
                        .addGroup(jpnlResultsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(lblRedMean)
                            .addComponent(lblGreenMean)
                            .addComponent(lblBlueMean))
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addComponent(lblMean, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
            .addGroup(jpnlResultsLayout.createSequentialGroup()
                .addGap(59, 59, 59)
                .addComponent(jLabel10)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jpnlResultsLayout.setVerticalGroup(
            jpnlResultsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jpnlResultsLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel10, javax.swing.GroupLayout.PREFERRED_SIZE, 14, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jpnlResultsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel11)
                    .addComponent(lblWBExposure))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jpnlResultsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel1, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(lblMean))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jpnlResultsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(lblRedMean)
                    .addComponent(jLabel3))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jpnlResultsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel4)
                    .addComponent(lblGreenMean))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jpnlResultsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel5)
                    .addComponent(lblBlueMean))
                .addGap(18, 18, 18)
                .addGroup(jpnlResultsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel12, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(lblRedScale, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jpnlResultsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(lblGreenScale, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jLabel13, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jpnlResultsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(lblBlueScale)
                    .addComponent(jLabel14))
                .addContainerGap())
        );

        jPanel1.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));

        jLabel2.setFont(new java.awt.Font("Tahoma", 2, 11)); // NOI18N
        jLabel2.setText("Position the field of view to a white or grey area.  After the WB scales");

        jLabel9.setFont(new java.awt.Font("Tahoma", 2, 11)); // NOI18N
        jLabel9.setText("are found switch off \"Autostretch\" and click \"Full\"  button on each of ");

        jLabel15.setFont(new java.awt.Font("Tahoma", 2, 11)); // NOI18N
        jLabel15.setText("the RGB channels in Micro-Manager.");

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel15)
                    .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                        .addComponent(jLabel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(jLabel9, javax.swing.GroupLayout.PREFERRED_SIZE, 336, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel2)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel9, javax.swing.GroupLayout.PREFERRED_SIZE, 14, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel15)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jPanel2.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));

        jLabel6.setText("Bit-depth:");

        btnRunWB.setText("Run WB");
        btnRunWB.setName(""); // NOI18N
        btnRunWB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnRunWBActionPerformed(evt);
            }
        });

        cbxBitDepth.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "8-bit", "10-bit", "12-bit", "14-bit", "16-bit" }));
        cbxBitDepth.setSelectedIndex(4);
        cbxBitDepth.setEnabled(false);
        cbxBitDepth.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cbxBitDepthActionPerformed(evt);
            }
        });

        jLabel8.setText("CFA pattern:");

        cbxCFAPattern.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "RGGB", "BGGR", "GRBG", "GBRG" }));
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

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel2Layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel7)
                            .addComponent(jLabel8)
                            .addComponent(jLabel6)
                            .addComponent(jLabel16))
                        .addGap(11, 11, 11)
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(lblCameraModel)
                            .addComponent(cbxBitDepth, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(cbxCFAPattern, javax.swing.GroupLayout.PREFERRED_SIZE, 55, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(lblMeanTarget)))
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addComponent(btnRunWB, javax.swing.GroupLayout.PREFERRED_SIZE, 110, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(25, 25, 25)))
                .addGap(148, 148, 148))
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addGap(22, 22, 22)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel7)
                    .addComponent(lblCameraModel, javax.swing.GroupLayout.PREFERRED_SIZE, 14, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel6)
                    .addComponent(cbxBitDepth, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(5, 5, 5)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel16)
                    .addComponent(lblMeanTarget))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(cbxCFAPattern, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel8))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(btnRunWB)
                .addGap(30, 30, 30))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, 189, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jpnlResults, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(11, 11, 11)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jpnlResults, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void cbxCFAPatternActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cbxCFAPatternActionPerformed
        CFApattern = cbxCFAPattern.getSelectedIndex();
    }//GEN-LAST:event_cbxCFAPatternActionPerformed

    private void cbxBitDepthActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cbxBitDepthActionPerformed
        switch (cbxBitDepth.getSelectedIndex()) {
            case 0:
                CameraBitDepth = DEPTH8BIT;
                break;
            case 1:
                CameraBitDepth = DEPTH10BIT;
                break;
            case 2:
                CameraBitDepth = DEPTH12BIT;
                break;
            case 3:
                CameraBitDepth = DEPTH14BIT;
                break;
            case 4:
            default:
                CameraBitDepth = DEPTH16BIT;
                break;
        }
    }//GEN-LAST:event_cbxBitDepthActionPerformed

    //start the WB algorithm
    private void btnRunWBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnRunWBActionPerformed

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
        if (gui_.isLiveModeOn()) {
            try {
                gui_.enableLiveMode(false);
                int slpCnt = 0;
                while (slpCnt < 50 && gui_.isLiveModeOn()) {
                    slpCnt++;
                    Thread.sleep(10);
                }
                Thread.sleep(50);

            } catch (InterruptedException ex) {
                Logger.getLogger(WhiteBalance_UI.class
                        .getName()).log(Level.SEVERE, null, ex);
                JOptionPane.showMessageDialog(this,
                        "Live mode could not be stopped.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        //disable color mode in MM before the WB algorithm
        try {
            core_.setProperty(CameraLabel, "Color", "OFF");
            gui_.refreshGUI();
            WBexposure = FindExposureForWB();

        } catch (Exception ex) {
            Logger.getLogger(WhiteBalance_UI.class
                    .getName()).log(Level.SEVERE, null, ex);
            btnRunWB.setText(
                    "Run WB");
            btnRunWB.setEnabled(
                    true);
            btnRunWB.paintImmediately(btnRunWB.getVisibleRect());
            JOptionPane.showMessageDialog(this,
                    "Finding exposure for White Balance algorithm failed.", "Error", JOptionPane.ERROR_MESSAGE);

            return;
        }

        lblWBExposure.setText(String.valueOf(new BigDecimal(WBexposure).setScale(0, RoundingMode.HALF_EVEN)) + " ms");

        lblMean.setText(String.valueOf(new BigDecimal(CapturedImage.getStatistics().mean).setScale(1, BigDecimal.ROUND_HALF_EVEN)));
        lblMean.paintImmediately(lblMean.getVisibleRect());

        //if the correct exposure time has been found snap an image in MM and
        //calculate the RGB scales to balance the image
        try {
            if (WbResult == WB_SUCCESS) {
                SnapImage(WBexposure);
                DebayerImage(CapturedImage);
                GetScales();
                core_.setProperty(CameraLabel, RED_SCALE_LABEL, RedScale);
                core_.setProperty(CameraLabel, GREEN_SCALE_LABEL, GreenScale);
                core_.setProperty(CameraLabel, BLUE_SCALE_LABEL, BlueScale);
                core_.setProperty(CameraLabel, "Color", "ON");
                gui_.refreshGUI();
                gui_.snapSingleImage();
            } else {
                core_.setProperty(CameraLabel, RED_SCALE_LABEL, 1.0);
                core_.setProperty(CameraLabel, GREEN_SCALE_LABEL, 1.0);
                core_.setProperty(CameraLabel, BLUE_SCALE_LABEL, 1.0);
                core_.setProperty(CameraLabel, "Color", "ON");
                gui_.refreshGUI();

            }
        } catch (Exception ex) {
            Logger.getLogger(WhiteBalance_UI.class
                    .getName()).log(Level.SEVERE, null, ex);
            btnRunWB.setText(
                    "Run WB");
            btnRunWB.setEnabled(
                    true);
            btnRunWB.paintImmediately(btnRunWB.getVisibleRect());
            JOptionPane.showMessageDialog(this,
                    "Could not set White Balance scales as device properties.", "Error", JOptionPane.ERROR_MESSAGE);
        }
        btnRunWB.setText("Run WB");
        btnRunWB.setEnabled(true);
        btnRunWB.paintImmediately(btnRunWB.getVisibleRect());
    }//GEN-LAST:event_btnRunWBActionPerformed

    private double FindExposureForWB() {
        double exposure = 5.0;
        int iterations = 0;
        double meanAt1ms;
        double meanAt100ms;
        double maxADUval = Math.pow(2, CameraBitDepth);
        try {

            //first check if the image is not saturated by doing a short exposure
            SnapImage(EXP_1MS);
            meanAt1ms = CapturedImage.getStatistics().mean;
            if (meanAt1ms > (maxADUval - maxADUval / 7.0)) {
                WbResult = WB_FAILED_SATURATED_FROM_START;
                lblMean.setText(String.valueOf(new BigDecimal(meanAt1ms).setScale(1, BigDecimal.ROUND_HALF_EVEN)));
                lblMean.paintImmediately(lblMean.getVisibleRect());
                JOptionPane.showMessageDialog(this, "Image too bright, please, decrease light levels and try again.", "White Balance Failure", JOptionPane.ERROR_MESSAGE);
                return EXP_1MS;
            }

            //then with a longer exposure check if the image is not too dark
            SnapImage(EXP_100MS);
            meanAt100ms = CapturedImage.getStatistics().mean;
            if ((meanAt100ms < ADU_BIAS_16BIT && CameraBitDepth == DEPTH16BIT) || (meanAt100ms < ADU_BIAS_10TO16BIT && (CameraBitDepth < DEPTH16BIT && CameraBitDepth > DEPTH8BIT))
                    || (meanAt100ms < ADU_BIAS_QI8BIT && CameraBitDepth == DEPTH8BIT)) {
                WbResult = WB_FAILED_TOO_DARK_FROM_START;
                lblMean.setText(String.valueOf(new BigDecimal(meanAt100ms).setScale(1, BigDecimal.ROUND_HALF_EVEN)));
                lblMean.paintImmediately(lblMean.getVisibleRect());
                JOptionPane.showMessageDialog(this, "Image too dark, please, increase light levels and try again.", "White Balance Failure", JOptionPane.ERROR_MESSAGE);
                return EXP_100MS;
            }

            //start searching for exposre time with mean around the middle of the camera ADU range
            //later this value will be used for grabbing an image and calculating white balanc scales
            SnapImage(exposure);
            exposure = exposure * WbMeanMin / CapturedImage.getStatistics().mean;

            //keep snapping images and adjusting exposure time until desired mean value is reached
            while (iterations < WB_EXP_ITERATIONS_MAX && exposure < MAX_EXPOSURE) {
                SnapImage(exposure);

                lblWBExposure.setText(String.valueOf(new BigDecimal(exposure).setScale(0, RoundingMode.HALF_EVEN)) + " ms");
                lblWBExposure.paintImmediately(lblWBExposure.getVisibleRect());
                iterations++;

                if (CapturedImage.getStatistics().mean < WbMeanMin) {
                    exposure = exposure * WbMeanMin / CapturedImage.getStatistics().mean;
                } else if (CapturedImage.getStatistics().mean > WbMeanMax) {
                    exposure = exposure * WbMeanMin / CapturedImage.getStatistics().mean;
                } else if (CapturedImage.getStatistics().mean > WbMeanMin
                        && CapturedImage.getStatistics().mean < WbMeanMax) {
                    break;
                }
                lblMean.setText(String.valueOf(new BigDecimal(CapturedImage.getStatistics().mean).setScale(1, BigDecimal.ROUND_HALF_EVEN)));
                lblMean.paintImmediately(lblMean.getVisibleRect());
            }
        } catch (Exception ex) {
            WbResult = WB_FAILED_EXCEPTION;
            lblMean.setText(String.valueOf(new BigDecimal(CapturedImage.getStatistics().mean).setScale(1, BigDecimal.ROUND_HALF_EVEN)));
            lblMean.paintImmediately(lblMean.getVisibleRect());
            JOptionPane.showMessageDialog(this, "Acquisition error occurred", "White Balance Failure", JOptionPane.ERROR_MESSAGE);
            return exposure;
        }

        //check if the exposure search did not end due to algorithm failure - too many iterations,
        //image too dark, image too bright etc. 
        if (iterations >= WB_EXP_ITERATIONS_MAX && exposure >= MAX_EXPOSURE) {
            WbResult = WB_FAILED_TOO_MANY_ITERATIONS_TOO_DARK;
            lblMean.setText(String.valueOf(new BigDecimal(CapturedImage.getStatistics().mean).setScale(1, BigDecimal.ROUND_HALF_EVEN)));
            lblMean.paintImmediately(lblMean.getVisibleRect());
            JOptionPane.showMessageDialog(this, "Exceeded number of allowed iterations, image too dark.", "White Balance Failure", JOptionPane.ERROR_MESSAGE);
            return exposure;
        } else if (iterations >= WB_EXP_ITERATIONS_MAX && exposure < MIN_EXPOSURE) {
            WbResult = WB_FAILED_TOO_MANY_ITERATIONS_TOO_BRIGHT;
            lblMean.setText(String.valueOf(new BigDecimal(CapturedImage.getStatistics().mean).setScale(1, BigDecimal.ROUND_HALF_EVEN)));
            lblMean.paintImmediately(lblMean.getVisibleRect());
            JOptionPane.showMessageDialog(this, "Exceeded number of allowed iterations, image too bright.", "White Balance Failure", JOptionPane.ERROR_MESSAGE);
            return exposure;
        } else if (iterations >= WB_EXP_ITERATIONS_MAX) {
            WbResult = WB_FAILED_TOO_MANY_ITERATIONS;
            lblMean.setText(String.valueOf(new BigDecimal(CapturedImage.getStatistics().mean).setScale(1, BigDecimal.ROUND_HALF_EVEN)));
            lblMean.paintImmediately(lblMean.getVisibleRect());
            JOptionPane.showMessageDialog(this, "Exceeded number of allowed iterations, adjust your light level and try again", "White Balance Failure", JOptionPane.ERROR_MESSAGE);
            return exposure;
        } else if (exposure > MAX_EXPOSURE) {
            WbResult = WB_FAILED_EXP_TOO_LONG;
            lblMean.setText(String.valueOf(new BigDecimal(CapturedImage.getStatistics().mean).setScale(1, BigDecimal.ROUND_HALF_EVEN)));
            lblMean.paintImmediately(lblMean.getVisibleRect());
            JOptionPane.showMessageDialog(this, "Light level seems to be too low, adjust your light level and try again", "White Balance Failure", JOptionPane.ERROR_MESSAGE);
            return exposure;
        } else {
            WbResult = WB_SUCCESS;
        }

        return exposure;
    }

    //snap a single image
    private void SnapImage(double exposure) throws Exception {
        try {
            core_.setExposure(Math.round(exposure));
            core_.snapImage();
            Object newImage = core_.getImage();
            if (CameraBitDepth == DEPTH8BIT) {
                CapturedImage = new ByteProcessor((int) core_.getImageWidth(), (int) core_.getImageHeight(), (byte[]) newImage, null);
            } else {
                CapturedImage = new ShortProcessor((int) core_.getImageWidth(), (int) core_.getImageHeight(), (short[]) newImage, null);

            }

        } catch (Exception ex) {
            Logger.getLogger(WhiteBalance_UI.class
                    .getName()).log(Level.SEVERE, null, ex);
            throw new Exception(
                    "Acquisition failed");
        }
    }

    //use Nearest Neighbor replication algorithm to debayer the image and obtain 
    //red, blue and green interpolated pixels and their mean values
    private void DebayerImage(ImageProcessor imgToProcess) {
        ImageProcessor r = null, g = null, b = null;
        if (CameraBitDepth == DEPTH8BIT) {
            r = new ByteProcessor(CapturedImage.getWidth(), CapturedImage.getHeight());
            g = new ByteProcessor(CapturedImage.getWidth(), CapturedImage.getHeight());
            b = new ByteProcessor(CapturedImage.getWidth(), CapturedImage.getHeight());
        } else { //(CameraBitDepth >= DEPTH8BIT) 
            r = new ShortProcessor(CapturedImage.getWidth(), CapturedImage.getHeight());
            g = new ShortProcessor(CapturedImage.getWidth(), CapturedImage.getHeight());
            b = new ShortProcessor(CapturedImage.getWidth(), CapturedImage.getHeight());
        }
        ImageProcessor ip = imgToProcess;

        int height = (int) CapturedImage.getHeight();
        int width = (int) CapturedImage.getWidth();
        int one;

        if (CFApattern == CFA_GRBG || CFApattern == CFA_GBRG) {
            for (int y = 1; y < height; y += 2) {
                for (int x = 0; x < width; x += 2) {
                    one = ip.getPixel(x, y);
                    b.putPixel(x, y, one);
                    b.putPixel(x + 1, y, one);
                    b.putPixel(x, y + 1, one);
                    b.putPixel(x + 1, y + 1, one);
                }
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

            if (CFApattern == CFA_GRBG) {
                Rmean = b.getStatistics().mean;
                Gmean = g.getStatistics().mean;
                Bmean = r.getStatistics().mean;
            } else if (CFApattern == CFA_GBRG) {
                Rmean = r.getStatistics().mean;
                Gmean = g.getStatistics().mean;
                Bmean = b.getStatistics().mean;
            }

            lblRedMean.setText(String.valueOf(new BigDecimal(Rmean).setScale(1, RoundingMode.HALF_EVEN)));
            lblGreenMean.setText(String.valueOf(new BigDecimal(Gmean).setScale(1, RoundingMode.HALF_EVEN)));
            lblBlueMean.setText(String.valueOf(new BigDecimal(Bmean).setScale(1, RoundingMode.HALF_EVEN)));

        } else if (CFApattern == CFA_RGGB || CFApattern == CFA_BGGR) {
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

            for (int y = 0; y < height; y += 2) {
                for (int x = 1; x < width; x += 2) {
                    one = ip.getPixel(x, y);
                    g.putPixel(x, y, one);
                    g.putPixel(x + 1, y, one);
                }
            }

            for (int y = 1; y < height; y += 2) {
                for (int x = 0; x < width; x += 2) {
                    one = ip.getPixel(x, y);
                    g.putPixel(x, y, one);
                    g.putPixel(x + 1, y, one);
                }
            }
            if (CFApattern == CFA_RGGB) {
                Rmean = b.getStatistics().mean;
                Gmean = g.getStatistics().mean;
                Bmean = r.getStatistics().mean;
            } else if (CFApattern == CFA_BGGR) {
                Rmean = r.getStatistics().mean;
                Gmean = g.getStatistics().mean;
                Bmean = b.getStatistics().mean;
            }

            //display mean values of each red, green and blue channel
            lblRedMean.setText(String.valueOf(new BigDecimal(Rmean).setScale(1, RoundingMode.HALF_EVEN)));
            lblGreenMean.setText(String.valueOf(new BigDecimal(Gmean).setScale(1, RoundingMode.HALF_EVEN)));
            lblBlueMean.setText(String.valueOf(new BigDecimal(Bmean).setScale(1, RoundingMode.HALF_EVEN)));
        }
    }

    //calculate the RGB scales based on red, green and blue channels' means
    private void GetScales() {
        RedScale = 1;
        BlueScale = Rmean / Bmean;
        GreenScale = Rmean / Gmean;

        if ((Gmean > Rmean) || (Bmean > Rmean)) {
            if (Gmean > Bmean) {
                GreenScale = 1;
                BlueScale = Gmean / Bmean;
                RedScale = Gmean / Rmean;
            } else {
                BlueScale = 1;
                RedScale = Bmean / Rmean;
                GreenScale = Bmean / Gmean;
            }
        }
        
        //limit the scales to values hardcoded previously in Micro-Manager
        if (RedScale > 20.0) {
            JOptionPane.showMessageDialog(this, "Red scale greater than 20.0, limiting to 20.", "Warning", JOptionPane.WARNING_MESSAGE);
            Logger
                    .getLogger(WhiteBalance_UI.class
                            .getName()).log(Level.WARNING, "Red scale greater than 20.0, limiting to 20.");
            RedScale = 20;        
        }
        
        if (BlueScale > 20.0) {
            JOptionPane.showMessageDialog(this, "Blue scale greater than 20.0, limiting to 20.", "Warning", JOptionPane.WARNING_MESSAGE);
            Logger
                    .getLogger(WhiteBalance_UI.class
                            .getName()).log(Level.WARNING, "Blue scale greater than 20.0, limiting to 20.");
            BlueScale = 20;        
        }
        
        if (GreenScale > 20.0) {
            JOptionPane.showMessageDialog(this, "Green scale greater than 20.0, limiting to 20.", "Warning", JOptionPane.WARNING_MESSAGE);
            Logger
                    .getLogger(WhiteBalance_UI.class
                            .getName()).log(Level.WARNING, "Green scale greater than 20.0, limiting to 20.");
            GreenScale = 20;        
        }
            
        lblRedScale.setText(String.valueOf(new BigDecimal(RedScale).setScale(4, RoundingMode.HALF_EVEN)));
        lblGreenScale.setText(String.valueOf(new BigDecimal(GreenScale).setScale(4, RoundingMode.HALF_EVEN)));
        lblBlueScale.setText(String.valueOf(new BigDecimal(BlueScale).setScale(4, RoundingMode.HALF_EVEN)));
    }

//    /**
//     * @param args the command line arguments
//     */
//    public static void main(String args[]) {
//        /* Set the Nimbus look and feel */
//        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
//        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
//         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
//         */
//        try {
//            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
//                if ("Nimbus".equals(info.getName())) {
//                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
//                    break;
//                }
//            }
//        } catch (ClassNotFoundException ex) {
//            java.util.logging.Logger.getLogger(WhiteBalance_UI.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
//        } catch (InstantiationException ex) {
//            java.util.logging.Logger.getLogger(WhiteBalance_UI.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
//        } catch (IllegalAccessException ex) {
//            java.util.logging.Logger.getLogger(WhiteBalance_UI.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
//        } catch (javax.swing.UnsupportedLookAndFeelException ex) {
//            java.util.logging.Logger.getLogger(WhiteBalance_UI.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
//        }
//        //</editor-fold>
//
//        /* Create and display the form */
//        java.awt.EventQueue.invokeLater(new Runnable() {
//            @Override
//            public void run() {
//                new WhiteBalance_UI().setVisible(true);
//            }
//        });
//    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnRunWB;
    private javax.swing.JComboBox cbxBitDepth;
    private javax.swing.JComboBox cbxCFAPattern;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel12;
    private javax.swing.JLabel jLabel13;
    private javax.swing.JLabel jLabel14;
    private javax.swing.JLabel jLabel15;
    private javax.swing.JLabel jLabel16;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jpnlResults;
    private javax.swing.JLabel lblBlueMean;
    private javax.swing.JLabel lblBlueScale;
    private javax.swing.JLabel lblCameraModel;
    private javax.swing.JLabel lblGreenMean;
    private javax.swing.JLabel lblGreenScale;
    private javax.swing.JLabel lblMean;
    private javax.swing.JLabel lblMeanTarget;
    private javax.swing.JLabel lblRedMean;
    private javax.swing.JLabel lblRedScale;
    private javax.swing.JLabel lblWBExposure;
    // End of variables declaration//GEN-END:variables

    @Override
    public void propertiesChangedAlert() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    //if user changes the CFA pattern in the MM UI select the same pattern also in the 
    //WB plugin
    @Override
    public void propertyChangedAlert(String string, String string1, String string2) {
        if (string1.equals("Color - Algorithm CFA")) {
            SetCFAPattern(string2);
        } else if (string1.equals("PixelType")) {
            GetBitDepth();
        }
    }

    @Override
    public void configGroupChangedAlert(String string, String string1) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void systemConfigurationLoaded() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void pixelSizeChangedAlert(double d) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void stagePositionChangedAlert(String string, double d) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void xyStagePositionChanged(String string, double d, double d1) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void exposureChanged(String string, double d) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void slmExposureChanged(String string, double d) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
}
