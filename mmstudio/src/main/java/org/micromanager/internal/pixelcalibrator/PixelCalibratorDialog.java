///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Arthur Edelstein, 2010
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

import com.google.common.eventbus.Subscribe;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.AffineTransform;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;
import org.micromanager.events.ShutdownCommencingEvent;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.dialogs.PixelSizeProvider;
import org.micromanager.internal.utils.AffineUtils;
import org.micromanager.internal.utils.GUIUtils;
import org.micromanager.internal.utils.MMFrame;
import org.micromanager.internal.utils.ReportingUtils;

/**
 *
 * @author arthur, Nico
 */
public class PixelCalibratorDialog extends MMFrame {

   private static final long serialVersionUID = 8504268289532100411L;
   
   private static final String METHOD_AUTO = "Automatic";
   private static final String METHOD_MANUAL_SIMPLE = "Manual-Simple";
   private static final String METHOD_MANUAL_PRECISE = "Manual-Precise";
   
   private final Studio studio_;
   private final PixelSizeProvider pixelSizeProvider_;
   private CalibrationThread calibrationThread_;

   private JProgressBar calibrationProgressBar_;
   private JLabel explanationLabel_;
   private JComboBox safeTravelRadiusComboBox_;
   private JComboBox methodComboBox_;
   private JButton startButton_;
   private JButton stopButton_;
   private JCheckBox debug_;

    /** 
     * The  PixelCalibratorDialog executes an automated calibration of 
     * pixel size and the relation between stage movement and camera resulting
     * in an affine transform.  Data are returned to the PixelSizeProvider
     * 
     * @param studio - Current studio instance
     * @param psp - PixelSizeProvider that is requesting our services
     */
   @SuppressWarnings("LeakingThisInConstructor")
   public PixelCalibratorDialog(Studio studio, PixelSizeProvider psp) {
      studio_ = studio;
      pixelSizeProvider_ = psp;
      initComponents();
      super.loadPosition(200, 200);
      super.setVisible(true);
      
      studio_.events().registerForEvents(this);
   }

   private void initComponents() {

      explanationLabel_ = new JLabel();
      calibrationProgressBar_ = new JProgressBar();
      startButton_ = new JButton();
      stopButton_ = new JButton();
      safeTravelRadiusComboBox_ = new JComboBox();
      methodComboBox_ = new JComboBox();

      //setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
      setTitleText("Pixel Calibrator");
      //setResizable(false);
      /*addWindowListener(new java.awt.event.WindowAdapter() {
         @Override
         public void windowClosing(java.awt.event.WindowEvent evt) {
            stopCalibration();
            setVisible(false);
            dispose();
         }
      });*/

      explanationLabel_.setText("<html>This plugin measures the size " +
              "of the current camera's pixels at the sample plane.<br><br>" +
              "To calibrate:<br><ol><li>Make sure you are using a correctly " +
              "calibrated motorized xy stage.</li><li>Choose a nonperiodic " +
              "specimen (e.g., a cell) and adjust <br>your illumination and focus " +
              "until you obtain crisp, high-contrast images. " +
              "<li>Press Start (below).</li></ol></html>");

      JLabel methodLabel = new JLabel("Select method:");
      methodComboBox_.setModel(new DefaultComboBoxModel(
              new String[] {METHOD_AUTO, METHOD_MANUAL_SIMPLE} ) );
      final String mKey = "methodComboxSelection";
      final Class ourClass = this.getClass();
      methodComboBox_.setSelectedItem(studio_.profile().getSettings(ourClass).
              getString(mKey, METHOD_MANUAL_SIMPLE ) );
      methodComboBox_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            studio_.profile().getSettings(ourClass).putString(mKey, 
                    (String) methodComboBox_.getSelectedItem());
         }
      });
      
      JLabel safeTravelLabel = new JLabel("Safe travel radius, um:");
      safeTravelRadiusComboBox_.setModel(new DefaultComboBoxModel(
              new String[] { "1000", "10000", "100000" }));

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
      
      debug_ = new JCheckBox("debug");
      debug_.setSelected(false);

      getContentPane().setLayout(new MigLayout());
      super.add(explanationLabel_, "span 2, wrap");
      super.add(methodLabel);
      super.add(methodComboBox_, "wrap");
      super.add(safeTravelLabel);
      super.add(safeTravelRadiusComboBox_);
      super.add(debug_, "wrap");
      super.add(startButton_, "split 2");
      super.add(stopButton_);
      super.add(calibrationProgressBar_, "wrap");
      
      //super.pack();

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


   /*@Override
   public void dispose() {
      stopCalibration();
      if (studio_ != null) {
         studio_.events().unregisterForEvents(this);
      }
      super.dispose();
   }*/
   
   /**
    * @param event indicating that shutdown is happening
    */
   @Subscribe
   public void onShutdownCommencing(ShutdownCommencingEvent event) {
      //this.dispose();
   }

   
   private void startCalibration() {
      if (METHOD_AUTO.equals(methodComboBox_.getSelectedItem())) {
         calibrationThread_ = new AutomaticCalibrationThread(studio_, this);
         
      } else if (METHOD_MANUAL_SIMPLE.equals(methodComboBox_.getSelectedItem())) {
         calibrationThread_ = new ManualSimpleCalibrationThread(studio_, this);
      } else if (METHOD_MANUAL_PRECISE.equals(methodComboBox_.getSelectedItem())) {
         calibrationThread_ = new ManualPreciseCalibrationThread(studio_, this);
      }
      if (calibrationThread_ != null && !calibrationThread_.isAlive()) {
            calibrationThread_.start();
      }
      updateStatus(true, 0);
      
   }

   
   private void stopCalibration() {
      if (calibrationThread_ != null && calibrationThread_.isAlive()) {
         synchronized(CalibrationThread.class) {
            CalibrationThread.class.notify();
         }
         calibrationThread_.interrupt();
         try {
            calibrationThread_.join();
         } catch (InterruptedException ex) {
            ReportingUtils.logError(ex);
         }
      }
      updateStatus(false, 0);
   }
   
   private void promptToSaveResult() {
      AffineTransform result = calibrationThread_.getResult();
      if (result == null) {
         showFailureMessage();
         return;
      }

      double pixelSize = AffineUtils.deducePixelSize(result);
      double[] measurements = AffineUtils.affineToMeasurements(result);
//      xScale, yScale, rotationDeg, shear
      int response = JOptionPane.showConfirmDialog(MMStudio.getFrame(),
            String.format("Affine transform parameters: XScale=%.4f YScale=%.4f Rotation (degrees)=%.2f Shear=%.4f\n",                     
                    measurements[0], measurements[1], 
                    measurements[2], measurements[3]) + 
            "<html>If this is a correct result, Xscale and YScale should have absoulte value of roughly the number of &#956;m/pixel,</html>\n" +
                    "rotation should give the angle between the corrdinate system of the camera and the stage, and the shear\nvalue shoud be very small.\n\n" +
            "<html>The Pixel Calibrator plugin measured a pixel size of " + 
                    pixelSize + " &#956;m.<br>" + 
                    "Do you wish to copy these to your pixel calibration settings?</html>",
            "Calibration succeeded!",
            JOptionPane.YES_NO_OPTION);

      if (response == JOptionPane.YES_OPTION) {
         if (pixelSizeProvider_ != null) {
            pixelSizeProvider_.setPixelSize(pixelSize);
            pixelSizeProvider_.setAffineTransform(result);
         }
      } 
      //dispose();
   }
   
   private void showFailureMessage() {
      ReportingUtils.showMessage("Calibration failed. Please improve the contrast by adjusting the\n" +
            "sample, focus and illumination. Also make sure the specimen is\n" +
            "securely immobilized on the stage. When you are ready, press\n" +
            "Start to try again.");
   }
   
 
   // The following functions are used by the spawend threads to communicate
   // back to this dialog
    

   public Double getCalibratedPixelSize() {
      return pixelSizeProvider_.getPixelSize();
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
      return Double.parseDouble(
                    safeTravelRadiusComboBox_.getSelectedItem().toString());
   }
   public boolean debugMode() {
      return debug_.isSelected();
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