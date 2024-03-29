/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/*
 * zoomControlPanel.java
 *
 * Created on Nov 19, 2009, 3:28:20 PM
 */

package org.micromanager.slideexplorer;

import java.awt.Graphics;
import java.awt.Point;

/**
 * @author arthur
 */
public class ZoomControlPanel extends javax.swing.JPanel {
   private final Display display_;


   ZoomControlPanel(Display display) {
      initComponents();
      display_ = display;
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

      zoomInButton = new javax.swing.JButton();
      zoomOutButton = new javax.swing.JButton();

      setBackground(new java.awt.Color(255, 255, 255));
      setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1));
      setFocusable(false);
      setMaximumSize(new java.awt.Dimension(38, 300));
      setPreferredSize(new java.awt.Dimension(35, 278));

      zoomInButton.setIcon(new javax.swing.ImageIcon(
            getClass().getResource("/org/micromanager/icons/plus.png"))); // NOI18N
      zoomInButton.setToolTipText("Zoom in [+]");
      zoomInButton.setFocusable(false);
      zoomInButton.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            zoomInButtonActionPerformed(evt);
         }
      });

      zoomOutButton.setIcon(new javax.swing.ImageIcon(
            getClass().getResource("/org/micromanager/icons/minus.png"))); // NOI18N
      zoomOutButton.setToolTipText("Zoom out [-]");
      zoomOutButton.setFocusable(false);
      zoomOutButton.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            zoomOutButtonActionPerformed(evt);
         }
      });

      javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
      this.setLayout(layout);
      layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                  .addGroup(layout.createSequentialGroup()
                        .addComponent(zoomOutButton, javax.swing.GroupLayout.PREFERRED_SIZE, 60,
                              javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(zoomInButton, javax.swing.GroupLayout.PREFERRED_SIZE, 60,
                              javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
      );
      layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                  .addComponent(zoomOutButton, javax.swing.GroupLayout.PREFERRED_SIZE, 30,
                        javax.swing.GroupLayout.PREFERRED_SIZE)
                  .addComponent(zoomInButton, javax.swing.GroupLayout.PREFERRED_SIZE, 30,
                        javax.swing.GroupLayout.PREFERRED_SIZE)
      );
   }

   private void zoomInButtonActionPerformed(
         java.awt.event.ActionEvent evt) {
      display_.zoomIn(new Point(0, 0));
   }

   private void zoomOutButtonActionPerformed(
         java.awt.event.ActionEvent evt) {
      display_.zoomOut(new Point(0, 0));
   }


   // Variables declaration - do not modify//GEN-BEGIN:variables
   private javax.swing.JButton zoomInButton;
   private javax.swing.JButton zoomOutButton;
   // End of variables declaration//GEN-END:variables

   public void paint(Graphics g) {
      super.paint(g);
   }

   public void updateControls() {
      //zoomSlider.setValue(display_.getZoomLevel());
   }
}
