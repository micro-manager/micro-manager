/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.plugins.magellan.coordinates;

import ij.IJ;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.util.prefs.Preferences;
import org.micromanager.plugins.magellan.main.Magellan;
import org.micromanager.plugins.magellan.misc.JavaUtils;
import org.micromanager.MMStudio;

/**
 * 
 * @author Henry
 */
public class AffineGUI extends javax.swing.JFrame {

   private AffineCalibrator affineCalibrator_;
   private String pixelSizeConfig_;
   private double pixelSize_;
   
   /**
    * Creates new form AffineGUI
    */
   public AffineGUI()  {
      initComponents();
      this.setLocationRelativeTo(null);
       try {
           pixelSizeConfig_ = Magellan.getCore().getCurrentPixelSizeConfig();
       } catch (Exception ex) {
           IJ.log("No pixel size found for current config!");
           throw new RuntimeException();
       }
      pixelCalLabel_.setText("Pixel size Calibration: " + pixelSizeConfig_);
      pixelSize_ = Magellan.getCore().getPixelSizeUm();
      setVisible(true);
       try {
           populateValues(AffineUtils.getAffineTransform(pixelSizeConfig_, 0, 0));
       } catch (NoninvertibleTransformException ex) {
           IJ.log("Couldn't populate current values due to invalid transform");
       }
   }
   
   //decompose affine, see http://math.stackexchange.com/questions/612006/decomposing-an-affine-transformation
   private void populateValues(AffineTransform transform) throws NoninvertibleTransformException {
       //[T] = [R][Sc][Sh]
       pixSizeLabel_.setText( pixelSize_ + " um");
       //{ m00 m10 m01 m11 m02 m12 }
       double[] matrix = new double[6];
       transform.getMatrix(matrix);
       double angle = Math.atan(matrix[1] / matrix[0]); //radians
       System.out.println(angle / Math.PI * 180);
       //figure out which quadrant 
       //sin && cos
       if (matrix[1] > 0 && matrix[0] >= 0) {
           //first quadrant, make sure angle is positive (in case ts exactly 90 degrees
          angle = Math.abs(angle);
       } else if (matrix[1] > 0 && matrix[0] < 0) {
           //second quadrant
           angle = Math.abs(angle - 2*(Math.PI / 2 + angle));            
       } else if (matrix[1] <= 0 && matrix[0] >= 0) {
           //fourth quadrant, do nothing
       } else {
           //third quadrant, subtract 90 degrees
           angle += 2*(Math.PI / 2 - angle);
           angle *= -1; //make sure angle is negative
       }
       
       //get shear by reversing the rotation, then reversing the scaling
       AffineTransform at = AffineTransform.getRotateInstance(angle).createInverse();
       at.concatenate(transform); //take out the rotations
       //get scales
       double[] newMat = new double[6];
       at.getMatrix(newMat);
       double xScale = Math.sqrt(newMat[0] * newMat[0] + newMat[1] * newMat[1]) * (newMat[0] > 0 ? 1.0 : -1.0);
       double yScale = Math.sqrt(newMat[2] * newMat[2] + newMat[3] * newMat[3]) * (newMat[3] > 0 ? 1.0 : -1.0);
       AffineTransform at2 = AffineTransform.getScaleInstance(xScale, yScale).createInverse();
       at2.concatenate(at); // take out the scale
       //should now be left with shear transform;
       double shear = at2.getShearX();
       rotationSpinner_.setValue(angle / Math.PI * 180.0);
       shearSpinner_.setValue(shear);
       xScaleSpinner_1.setValue(xScale);
       yScaleSpinner_1.setValue(yScale);

   }
   
   private void applyValues() {      
       //[T] = [R][Sc][Sh]
       double xScale = ((Number)xScaleSpinner_1.getValue()).doubleValue() ;
       double yScale = ((Number)yScaleSpinner_1.getValue()).doubleValue() ;
       double angle = ((Number)rotationSpinner_.getValue()).doubleValue() /180.0 * Math.PI;
       double shear = ((Number)shearSpinner_.getValue()).doubleValue();
       //scale shear and rotate to genrate affine
       AffineTransform scaleAT = AffineTransform.getScaleInstance(xScale, yScale);
       AffineTransform rotAT = AffineTransform.getRotateInstance(angle);
       AffineTransform shearAT = AffineTransform.getShearInstance(shear, 0);
       
       scaleAT.concatenate(shearAT);
       rotAT.concatenate(scaleAT);
       
       try {
           populateValues(rotAT);
       } catch (NoninvertibleTransformException e) {
           IJ.log("Invalid affine parameters");
           return;
       }
        //store affine
        Preferences prefs = Preferences.userNodeForPackage(MMStudio.class);
        JavaUtils.putObjectInPrefs(prefs, "affine_transform_" + pixelSizeConfig_, rotAT);
        //mark as updated
        AffineUtils.transformUpdated(pixelSizeConfig_, rotAT);
    }

    public void calibrationFinished() {
        captureButton_.setEnabled(false);
      calibrateButton_.setText("Start");
      affineCalibrator_ = null;
   }

   /**
    * This method is called from within the constructor to initialize the form.
    * WARNING: Do NOT modify this code. The content of this method is always
    * regenerated by the Form Editor.
    */
   @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        calibrateButton_ = new javax.swing.JButton();
        captureButton_ = new javax.swing.JButton();
        pixelCalLabel_ = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        jLabel5 = new javax.swing.JLabel();
        jLabel6 = new javax.swing.JLabel();
        pixSizeLabel_ = new javax.swing.JLabel();
        rotationSpinner_ = new javax.swing.JSpinner();
        xScaleSpinner_1 = new javax.swing.JSpinner();
        shearSpinner_ = new javax.swing.JSpinner();
        applyButton_ = new javax.swing.JButton();
        jPanel1 = new javax.swing.JPanel();
        jLabel2 = new javax.swing.JLabel();
        jPanel2 = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        yScaleSpinner_1 = new javax.swing.JSpinner();
        jLabel7 = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("Affine transform calibrator");

        calibrateButton_.setText("Start");
        calibrateButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                calibrateButton_ActionPerformed(evt);
            }
        });

        captureButton_.setText("Capture");
        captureButton_.setEnabled(false);
        captureButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                captureButton_ActionPerformed(evt);
            }
        });

        pixelCalLabel_.setFont(new java.awt.Font("Lucida Grande", 1, 13)); // NOI18N
        pixelCalLabel_.setText("Current pixel size config: ");

        jLabel3.setText("Pixel size");

        jLabel4.setText("Rotation (degrees)");

        jLabel5.setText("X Scale");

        jLabel6.setText("Shear");

        pixSizeLabel_.setText("jLabel7");

        rotationSpinner_.setModel(new javax.swing.SpinnerNumberModel(-1.0d, -180.0d, 180.0d, 1.0d));

        xScaleSpinner_1.setModel(new javax.swing.SpinnerNumberModel(Double.valueOf(1.0d), null, null, Double.valueOf(0.01d)));

        shearSpinner_.setModel(new javax.swing.SpinnerNumberModel(0.0d, -1.0d, 1.0d, 0.01d));

        applyButton_.setText("Apply");
        applyButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                applyButton_ActionPerformed(evt);
            }
        });

        jPanel1.setBackground(new java.awt.Color(200, 255, 200));

        jLabel2.setText("<html>Manual:<br>Supply the roation, scale, and shear when translating from stage coordiantes to pixel coordinates<br>It can be helpful to run 2x2 regions over a a contiguous sample feature while fine tuning this process");

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jLabel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jPanel2.setBackground(new java.awt.Color(200, 255, 200));

        jLabel1.setText("<html>Automatic:<br>Translate a distintive feature around the microscopes's field of view,<br>and Magellan will attempt to estimate these parameters");

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, 450, javax.swing.GroupLayout.PREFERRED_SIZE))
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, 57, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        yScaleSpinner_1.setModel(new javax.swing.SpinnerNumberModel(Double.valueOf(1.0d), null, null, Double.valueOf(0.01d)));

        jLabel7.setText("Y Scale");

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addContainerGap()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(pixSizeLabel_)
                                .addGap(27, 27, 27)
                                .addComponent(rotationSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, 67, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(jLabel3)
                                .addGap(18, 18, 18)
                                .addComponent(jLabel4)))
                        .addGap(23, 23, 23)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(xScaleSpinner_1, javax.swing.GroupLayout.PREFERRED_SIZE, 55, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGroup(layout.createSequentialGroup()
                                .addGap(12, 12, 12)
                                .addComponent(jLabel5)))
                        .addGap(18, 18, 18)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel7)
                            .addComponent(yScaleSpinner_1, javax.swing.GroupLayout.PREFERRED_SIZE, 55, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(36, 36, 36)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(shearSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, 60, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addComponent(applyButton_))
                            .addComponent(jLabel6)))
                    .addGroup(layout.createSequentialGroup()
                        .addGap(222, 222, 222)
                        .addComponent(calibrateButton_)
                        .addGap(42, 42, 42)
                        .addComponent(captureButton_))
                    .addGroup(layout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(133, 133, 133)
                        .addComponent(pixelCalLabel_))
                    .addGroup(layout.createSequentialGroup()
                        .addGap(94, 94, 94)
                        .addComponent(jPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addGap(0, 0, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(pixelCalLabel_)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel3)
                    .addComponent(jLabel4)
                    .addComponent(jLabel5)
                    .addComponent(jLabel6)
                    .addComponent(jLabel7))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(pixSizeLabel_)
                    .addComponent(rotationSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(xScaleSpinner_1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(shearSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(applyButton_)
                    .addComponent(yScaleSpinner_1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(captureButton_)
                    .addComponent(calibrateButton_))
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

   private void calibrateButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_calibrateButton_ActionPerformed
      if (affineCalibrator_ == null) {
         affineCalibrator_ = new AffineCalibrator(this);
         captureButton_.setEnabled(true);
         calibrateButton_.setText("Cancel");
      } else {
         affineCalibrator_.abort();
      }
   }//GEN-LAST:event_calibrateButton_ActionPerformed

   private void captureButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_captureButton_ActionPerformed
      affineCalibrator_.readyForNextImage();
   }//GEN-LAST:event_captureButton_ActionPerformed

    private void applyButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_applyButton_ActionPerformed
        applyValues();
//        populateValues();
    }//GEN-LAST:event_applyButton_ActionPerformed

   /**
    * @param args the command line arguments
    */
   public static void main(String args[]) {
      /*
       * Set the Nimbus look and feel
       */
      //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /*
       * If Nimbus (introduced in Java SE 6) is not available, stay with the
       * default look and feel. For details see
       * http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html
       */
      try {
         for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
            if ("Nimbus".equals(info.getName())) {
               javax.swing.UIManager.setLookAndFeel(info.getClassName());
               break;
            }
         }
      } catch (ClassNotFoundException ex) {
         java.util.logging.Logger.getLogger(AffineGUI.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
      } catch (InstantiationException ex) {
         java.util.logging.Logger.getLogger(AffineGUI.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
      } catch (IllegalAccessException ex) {
         java.util.logging.Logger.getLogger(AffineGUI.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
      } catch (javax.swing.UnsupportedLookAndFeelException ex) {
         java.util.logging.Logger.getLogger(AffineGUI.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
      }
      //</editor-fold>

      /*
       * Create and display the form
       */
      java.awt.EventQueue.invokeLater(new Runnable() {

         public void run() {
             try {
            new AffineGUI().setVisible(true);
             }catch (Exception e) {
                 IJ.log("Couldnt find current pixel size config");
             }
         }
      });
   }
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton applyButton_;
    private javax.swing.JButton calibrateButton_;
    private javax.swing.JButton captureButton_;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JLabel pixSizeLabel_;
    private javax.swing.JLabel pixelCalLabel_;
    private javax.swing.JSpinner rotationSpinner_;
    private javax.swing.JSpinner shearSpinner_;
    private javax.swing.JSpinner xScaleSpinner_1;
    private javax.swing.JSpinner yScaleSpinner_1;
    // End of variables declaration//GEN-END:variables
}
