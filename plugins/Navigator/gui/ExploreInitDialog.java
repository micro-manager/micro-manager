/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package gui;

import acq.CustomAcqEngine;
import acq.ExploreAcqSettings;
import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.swing.JFileChooser;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import main.Navigator;
import mmcorej.CMMCore;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.ReportingUtils;
/**
 *
 * @author Henry
 */
public class ExploreInitDialog extends javax.swing.JFrame {

   
   private String dir_, name_;
   private CustomAcqEngine eng_;

   
   /**
    * Creates new form ExploreInitDialog
    */
   public ExploreInitDialog(CustomAcqEngine eng, String dir, String name) {
      initComponents();
      initializeValues();
      eng_ = eng;
      dir_ = dir;
      name_ = name;
      this.setLocationRelativeTo(null);
      this.setVisible(true);
   }

   /**
    * This method is called from within the constructor to initialize the form.
    * WARNING: Do NOT modify this code. The content of this method is always
    * regenerated by the Form Editor.
    */
   @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jSpinner1 = new javax.swing.JSpinner();
        zStepLabel_ = new javax.swing.JLabel();
        zStepSpinner_ = new javax.swing.JSpinner();
        zTopLabel_ = new javax.swing.JLabel();
        zTopSpinner_ = new javax.swing.JSpinner();
        zBottomLabel_ = new javax.swing.JLabel();
        zBottomSpinner_ = new javax.swing.JSpinner();
        cancelButton_ = new javax.swing.JButton();
        startExploreButton_ = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("New explore acquisition");

        zStepLabel_.setText("Z step size (µm): ");

        zStepSpinner_.setModel(new javax.swing.SpinnerNumberModel(Double.valueOf(0.0d), null, null, Double.valueOf(1.0d)));

        zTopLabel_.setText("Initial Z top (µm): ");

        zTopSpinner_.setModel(new javax.swing.SpinnerNumberModel(Double.valueOf(0.0d), null, null, Double.valueOf(1.0d)));

        zBottomLabel_.setText("Initial Z bottom (µm): ");

        zBottomSpinner_.setModel(new javax.swing.SpinnerNumberModel(Double.valueOf(1.0d), null, null, Double.valueOf(1.0d)));

        cancelButton_.setText("Cancel");
        cancelButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelButton_ActionPerformed(evt);
            }
        });

        startExploreButton_.setText("Explore!");
        startExploreButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                startExploreButton_ActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(zStepLabel_)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(cancelButton_)
                        .addGap(106, 106, 106)
                        .addComponent(startExploreButton_))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(zStepSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, 55, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(zTopLabel_)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(zTopSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, 56, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(zBottomLabel_)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(zBottomSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, 56, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(24, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(zStepLabel_)
                    .addComponent(zTopLabel_)
                    .addComponent(zTopSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(zBottomLabel_)
                    .addComponent(zBottomSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(zStepSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(cancelButton_)
                    .addComponent(startExploreButton_))
                .addContainerGap(19, Short.MAX_VALUE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

   
   private void initializeValues() {
      CMMCore core = MMStudio.getInstance().getCore();
      double zPos = 0;
      try {
         zPos = core.getPosition(core.getFocusDevice());
      } catch (Exception ex) {
         ReportingUtils.showError("Couldn't get z position from core");
      }      
      zStepSpinner_.setValue(1);
      zBottomSpinner_.setValue(zPos);
      zTopSpinner_.setValue(zPos);
   }
   
   private void startExploreButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_startExploreButton_ActionPerformed
      ExploreAcqSettings settings = new ExploreAcqSettings(((Number)zTopSpinner_.getValue()).doubleValue(), ((Number)zBottomSpinner_.getValue()).doubleValue(), 
              ((Number) zStepSpinner_.getValue()).doubleValue(), dir_, name_);
      eng_.newExploreAcquisition(settings);
      this.setVisible(false);
      this.dispose();
   }//GEN-LAST:event_startExploreButton_ActionPerformed

   private void cancelButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelButton_ActionPerformed
      this.setVisible(false);
      this.dispose();
   }//GEN-LAST:event_cancelButton_ActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton cancelButton_;
    private javax.swing.JSpinner jSpinner1;
    private javax.swing.JButton startExploreButton_;
    private javax.swing.JLabel zBottomLabel_;
    private javax.swing.JSpinner zBottomSpinner_;
    private javax.swing.JLabel zStepLabel_;
    private javax.swing.JSpinner zStepSpinner_;
    private javax.swing.JLabel zTopLabel_;
    private javax.swing.JSpinner zTopSpinner_;
    // End of variables declaration//GEN-END:variables
}
