///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Arthur Edelstin, 2010
//               Nico Stuurman, 2018
//
// COPYRIGHT:    Regents of the University of California, 2010-2018
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


package org.micromanager.internal.pixelcalibrator;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.AffineTransform;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import org.apache.commons.math.util.MathUtils;
import org.micromanager.Studio;
import org.micromanager.internal.dialogs.PixelSizeProvider;
import org.micromanager.internal.utils.GUIUtils;
import org.micromanager.internal.utils.MMFrame;
import org.micromanager.internal.utils.ReportingUtils;

/**
 *
 * @author arthur
 */
public class PixelCalibratorDialog extends MMFrame {
   private final Studio studio_;
   private final PixelSizeProvider pixelSizeProvider_;
   private CalibrationThread calibrationThread_;

   private JProgressBar calibrationProgressBar_;
   private JLabel explanationLabel_;
   private JLabel jLabel1_;
   private JComboBox safeTravelRadiusComboBox_;
   private JButton startButton_;
   private JButton stopButton_;
   
   private double safeTravelRadiusUm_;


    /** 
     * The  PixelCalibratorDialog executes an automated calibration of 
     * pixel size and the relation between stage movement and camera resulting
     * in an affine transform.  Data are returned to the PixelSizeProvider
     * 
     * @param studio - Current studio instance
     * @param psp - PixelSizeProvider that is requesting our services
     */
   public PixelCalibratorDialog(Studio studio, PixelSizeProvider psp) {
      this.safeTravelRadiusUm_ = 1000;
      studio_ = studio;
      pixelSizeProvider_ = psp;
      initComponents();
      super.loadPosition(200, 200);
      super.setVisible(true);
   }

   private void initComponents() {

      explanationLabel_ = new JLabel();
      calibrationProgressBar_ = new JProgressBar();
      startButton_ = new JButton();
      stopButton_ = new JButton();
      jLabel1_ = new JLabel();
      safeTravelRadiusComboBox_ = new JComboBox();

      setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
      setTitle("Pixel Calibrator");
      setResizable(false);
      addWindowListener(new java.awt.event.WindowAdapter() {
         @Override
         public void windowClosing(java.awt.event.WindowEvent evt) {
            stopCalibration();
            setVisible(false);
            dispose();
         }
      });

      explanationLabel_.setText("<html>This plugin automatically measures size " +
              "of the default camera's pixels at the sample plane.<br><br>" +
              "To calibrate:<br><ol><li>Make sure you are using a correctly " +
              "calibrated motorized xy stage.</li><li>Choose a nonperiodic " +
              "specimen (e.g., a cell) and adjust your illumination and focus " +
              "until you obtain crisp, high-contrast images. " +
              "<li>Press Start (below).</li></ol></html>");

      calibrationProgressBar_.setForeground(new java.awt.Color(255, 0, 51));

      startButton_.setText("Start");
      startButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent evt) {
            startCalibration();
         }
      });

      stopButton_.setText("Stop");
      stopButton_.setEnabled(false);
      stopButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent evt) {
            stopCalibration();
         }
      });

      jLabel1_.setText("Safe travel radius, um:");

      safeTravelRadiusComboBox_.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "1000", "10000", "100000" }));
      safeTravelRadiusComboBox_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent evt) {
            safeTravelRadiusUm_ = Double.parseDouble(
                    safeTravelRadiusComboBox_.getSelectedItem().toString());
    
         }
      });

      javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
      getContentPane().setLayout(layout);
      layout.setHorizontalGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(layout.createSequentialGroup()
            .addContainerGap()
            .addComponent(explanationLabel_, javax.swing.GroupLayout.PREFERRED_SIZE, 363, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addContainerGap(31, Short.MAX_VALUE))
         .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
            .addContainerGap(24, Short.MAX_VALUE)
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
               .addGroup(layout.createSequentialGroup()
                  .addGap(70, 70, 70)
                  .addComponent(stopButton_))
               .addComponent(startButton_)
               .addComponent(jLabel1_, javax.swing.GroupLayout.PREFERRED_SIZE, 159, javax.swing.GroupLayout.PREFERRED_SIZE))
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
               .addComponent(calibrationProgressBar_, javax.swing.GroupLayout.PREFERRED_SIZE, 190, javax.swing.GroupLayout.PREFERRED_SIZE)
               .addComponent(safeTravelRadiusComboBox_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
            .addGap(31, 31, 31))
      );
      layout.setVerticalGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
            .addContainerGap()
            .addComponent(explanationLabel_, javax.swing.GroupLayout.DEFAULT_SIZE, 183, Short.MAX_VALUE)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
               .addComponent(jLabel1_)
               .addComponent(safeTravelRadiusComboBox_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
            .addGap(18, 18, 18)
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
               .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                  .addComponent(stopButton_)
                  .addComponent(startButton_))
               .addComponent(calibrationProgressBar_, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE))
            .addContainerGap())
      );

      setSize(new java.awt.Dimension(414, 328));
      setLocationRelativeTo(null);
   }


   public void updateStatus(final boolean running, final double progress) {

      GUIUtils.invokeLater(new Runnable() {
         @Override
         public void run() {
            if (!running) {
               startButton_.setEnabled(true);
               stopButton_.setEnabled(false);
               calibrationProgressBar_.setEnabled(false);
               safeTravelRadiusComboBox_.setEnabled(true);
            } else {
               toFront();
               startButton_.setEnabled(false);
               stopButton_.setEnabled(true);
               calibrationProgressBar_.setEnabled(true);
               safeTravelRadiusComboBox_.setEnabled(false);
            }
            calibrationProgressBar_.setValue((int) (progress * 100));
         }
      });

   }


   @Override
   public void dispose() {
      super.dispose();
   }
   
   private void startCalibration() {
      calibrationThread_ = new CalibrationThread(studio_, this);
      if (!calibrationThread_.isAlive()) {
         calibrationThread_.start();
      }
      updateStatus(true, 0);
   }

   
   private void stopCalibration() {
      if (calibrationThread_ != null && calibrationThread_.isAlive()) {
         calibrationThread_.interrupt();
         try {
            calibrationThread_.join();
         } catch (InterruptedException ex) {
            ReportingUtils.logError(ex);
         }
      }
   }
   
   
   private double getPixelSize(AffineTransform cameraToStage) {
      return Math.sqrt(Math.abs(cameraToStage.getDeterminant()));
   }
   
   private void promptToSaveResult() {
      AffineTransform result = calibrationThread_.getResult();
      if (result == null) {
         int res = JOptionPane.showConfirmDialog(this, 
                 "Calibration failed. Please improve the contrast by adjusting the\n" +
                  "sample, focus and illumination. Also make sure the specimen is\n" +
                  "securely immobilized on the stage. Try again?" ,
                 "Calibration failed", 
                 JOptionPane.YES_NO_OPTION );
         if (res == JOptionPane.YES_OPTION) {
            startCalibration();
            return;
         } else {
            dispose();
            return;
         }
      }

      double pixelSize = MathUtils.round(getPixelSize(result), 4);

      int response = JOptionPane.showConfirmDialog(this,
            String.format("Affine transform parameters: XScale=%.2f YScale=%.2f XShear=%.4f YShear=%.4f\n", result.getScaleX(), result.getScaleY(), result.getShearX(), result.getShearY()) + 
            "<html>The Pixel Calibrator plugin measured a pixel size of " + pixelSize + " &#956;m.<br>" + "Do you wish to copy these to your pixel calibration settings?</html>",
            "Calibration succeeded!",
            JOptionPane.YES_NO_OPTION);

      if (response == JOptionPane.YES_OPTION) {
         if (pixelSizeProvider_ != null) {
            pixelSizeProvider_.setPixelSize(pixelSize);
            pixelSizeProvider_.setAffineTransform(result);
         }
      } 
      dispose();
   }
   
   private void showFailureMessage() {
      ReportingUtils.showMessage("Calibration failed. Please improve the contrast by adjusting the\n" +
            "sample, focus and illumination. Also make sure the specimen is\n" +
            "securely immobilized on the stage. When you are ready, press\n" +
            "Start to try again.");
   }

   
   public void calibrationDone() {
      updateStatus(false, 1.);
      promptToSaveResult();
   }

   public void calibrationFailed(boolean canceled) {
      this.updateStatus(false, 0.);
      if (!canceled) {
         showFailureMessage();
      }
   }
   
   public double safeTravelRadius() {
      return safeTravelRadiusUm_;
   }
   
   public void update() {
      calibrationThread_.getProgress();
      final double progress = calibrationThread_.getProgress() / 24.;
      SwingUtilities.invokeLater(new Runnable() {
         @Override
         public void run() {
            updateStatus(calibrationThread_.isAlive(), progress);
         }
      });

   }


}