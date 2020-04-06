///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger
//
// COPYRIGHT:    University of California, San Francisco, 2011, 2015
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

package org.micromanager.imageflipper;

import com.bulenkov.iconloader.IconLoader;

import ij.process.ByteProcessor;

import java.util.Arrays;
import java.util.List;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import mmcorej.StrVector;



import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.Studio;
import org.micromanager.data.Image;
import org.micromanager.data.ProcessorConfigurator;
import org.micromanager.data.Coordinates;
import org.micromanager.propertymap.MutablePropertyMapView;

// Imports for MMStudio internal packages
// Plugins should not access internal packages, to ensure modularity and
// maintainability. However, this plugin code is older than the current
// MMStudio API, so it still uses internal classes and interfaces. New code
// should not imitate this practice.
import org.micromanager.internal.utils.MMFrame;

public class FlipperConfigurator extends MMFrame implements ProcessorConfigurator {

   private static final String DEFAULT_CAMERA = "Default camera for image flipper";
   private static final String DEFAULT_MIRRORED = "Whether or not to mirror the image flipper";
   private static final String DEFAULT_ROTATION = "How much to rotate the image flipper";
   private static final String R0 = "0" + "\u00B0";
   private static final String R90 = "90" + "\u00B0";
   private static final String R180 = "180" + "\u00B0";
   private static final String R270 = "270" + "\u00B0";
   private static final String[] RS = {R0, R90, R180, R270};
   private static final List<Integer> R_INTS =
      Arrays.asList(new Integer[] {FlipperProcessor.R0,
         FlipperProcessor.R90, FlipperProcessor.R180, FlipperProcessor.R270});
   private static final String EXAMPLE_ICON_PATH =
                        "/org/micromanager/icons/R.png";
   private static final Icon EXAMPLE_ICON = IconLoader.getIcon(EXAMPLE_ICON_PATH);
   
   private final int frameXPos_ = 300;
   private final int frameYPos_ = 300;

   private final Studio studio_;
   private final MutablePropertyMapView defaults_;
   private final String selectedCamera_;

   private javax.swing.JComboBox cameraComboBox_;
   private javax.swing.JLabel exampleImageSource_;
   private javax.swing.JLabel exampleImageTarget_;
   private javax.swing.JLabel jLabel1;
   private javax.swing.JCheckBox mirrorCheckBox_;
   private javax.swing.JComboBox rotateComboBox_;

   public FlipperConfigurator(Studio studio, PropertyMap settings) {
      studio_ = studio;
      defaults_ = studio_.profile().getSettings(this.getClass());
      initComponents();
      selectedCamera_ = settings.getString("camera",
            defaults_.getString(DEFAULT_CAMERA, 
                    studio_.core().getCameraDevice()));
      Boolean shouldMirror = settings.getBoolean("shouldMirror",
            defaults_.getBoolean(DEFAULT_MIRRORED, false));
      Integer rotation = settings.getInteger("rotation",
            defaults_.getInteger(DEFAULT_ROTATION, FlipperProcessor.R0));

      mirrorCheckBox_.setSelected(shouldMirror);
      rotateComboBox_.removeAllItems();
      for (String item: RS) {
         rotateComboBox_.addItem(item);
      }
      rotateComboBox_.setSelectedIndex(R_INTS.indexOf(rotation));
      super.loadAndRestorePosition(frameXPos_, frameYPos_);
      updateCameras();
   }

   @Override
   public void showGUI() {
      setVisible(true);
   }

   @Override
   public void cleanup() {
      dispose();
   }

   /**
    * updates the content of the camera selection drop down box
    * 
    * Shows all available cameras and sets the currently selected camera
    * as the selected item in the drop down box
    */
   final public void updateCameras() {
      cameraComboBox_.removeAllItems();
      try {
         StrVector cameras = studio_.core().getAllowedPropertyValues(
               "Core", "Camera");
         for (String camera : cameras) {
            cameraComboBox_.addItem(camera);
         }
      } catch (Exception ex) {
         studio_.logs().logError(ex, "Error updating valid cameras in image flipper");
      }
      cameraComboBox_.setSelectedItem(selectedCamera_);
   }

   /** This method is called from within the constructor to
    * initialize the form.
    */
   @SuppressWarnings("unchecked")
   private void initComponents() {

      mirrorCheckBox_ = new javax.swing.JCheckBox();
      exampleImageSource_ = new javax.swing.JLabel();
      exampleImageTarget_ = new javax.swing.JLabel();
      cameraComboBox_ = new javax.swing.JComboBox();
      rotateComboBox_ = new javax.swing.JComboBox();
      jLabel1 = new javax.swing.JLabel();

      setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
      setTitle("Image Flipper");
      setBounds(new java.awt.Rectangle(300, 300, 150, 150));
      setMinimumSize(new java.awt.Dimension(200, 200));
      setResizable(false);

      mirrorCheckBox_.setText("Mirror");
      mirrorCheckBox_.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            mirrorCheckBox_ActionPerformed(evt);
         }
      });

      exampleImageSource_.setIcon(EXAMPLE_ICON);

      exampleImageTarget_.setIcon(EXAMPLE_ICON);

      cameraComboBox_.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            cameraComboBox_ActionPerformed(evt);
         }
      });

      rotateComboBox_.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "0", "90", "180", "270" }));
      rotateComboBox_.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            rotateComboBox_ActionPerformed(evt);
         }
      });

      jLabel1.setText("Rotate");

      javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
      getContentPane().setLayout(layout);
      layout.setHorizontalGroup(
         layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(layout.createSequentialGroup()
            .addContainerGap()
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
               .addGroup(layout.createSequentialGroup()
                  .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                     .addComponent(cameraComboBox_, javax.swing.GroupLayout.Alignment.LEADING, 0, 153, Short.MAX_VALUE)
                     .addGroup(layout.createSequentialGroup()
                        .addComponent(exampleImageSource_, javax.swing.GroupLayout.DEFAULT_SIZE, 70, Short.MAX_VALUE)
                        .addGap(18, 18, 18)
                        .addComponent(exampleImageTarget_, javax.swing.GroupLayout.PREFERRED_SIZE, 65, javax.swing.GroupLayout.PREFERRED_SIZE))
                     .addGroup(layout.createSequentialGroup()
                        .addComponent(jLabel1)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(rotateComboBox_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                  .addGap(38, 38, 38))
               .addGroup(layout.createSequentialGroup()
                  .addComponent(mirrorCheckBox_)
                  .addContainerGap(121, Short.MAX_VALUE))))
      );
      layout.setVerticalGroup(
         layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
            .addGap(11, 11, 11)
            .addComponent(cameraComboBox_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
               .addComponent(exampleImageTarget_, javax.swing.GroupLayout.DEFAULT_SIZE, 54, Short.MAX_VALUE)
               .addComponent(exampleImageSource_, javax.swing.GroupLayout.DEFAULT_SIZE, 54, Short.MAX_VALUE))
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
            .addComponent(mirrorCheckBox_)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
               .addComponent(rotateComboBox_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
               .addComponent(jLabel1))
            .addGap(25, 25, 25))
      );

      pack();
   }

    private void mirrorCheckBox_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_mirrorCheckBox_ActionPerformed
       processExample();
       String camera = (String) cameraComboBox_.getSelectedItem();
       if (camera != null) {
          defaults_.putBoolean(DEFAULT_MIRRORED + "-" + camera, 
                  mirrorCheckBox_.isSelected());
       }
       studio_.data().notifyPipelineChanged();
    }

   private void rotateComboBox_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_rotateComboBox_ActionPerformed
      processExample();
      String camera = (String) cameraComboBox_.getSelectedItem();
      if (camera != null && rotateComboBox_.getSelectedItem() != null) {
         defaults_.putString(DEFAULT_ROTATION + "-" + camera,
               (String) rotateComboBox_.getSelectedItem());
      }
      studio_.data().notifyPipelineChanged();
   }

   private void cameraComboBox_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cameraComboBox_ActionPerformed
      String camera = (String) cameraComboBox_.getSelectedItem();
      if (camera != null) {
         mirrorCheckBox_.setSelected(defaults_.getBoolean(
                 DEFAULT_MIRRORED + "-" + camera, false));
         rotateComboBox_.setSelectedItem(defaults_.getString(
                  DEFAULT_ROTATION + "-" + camera, R0));
         defaults_.putString(DEFAULT_CAMERA, camera);
      }
      studio_.data().notifyPipelineChanged();
   }

   public String getCamera() {
      return (String) cameraComboBox_.getSelectedItem();
   }

   /**
    * Indicates users choice for rotation:
    * 0 - 0 degrees
    * 1 - 90 degrees
    * 2 - 180 degrees
    * 3 - 270 degrees
    * degrees are anti-clockwise
    * 
    * @return coded rotation
    */
   public final int getRotate() {
      if (R90.equals((String) rotateComboBox_.getSelectedItem())) {
         return FlipperProcessor.R90;
      }
      if (R180.equals((String) rotateComboBox_.getSelectedItem())) {
         return FlipperProcessor.R180;
      }
      if (R270.equals((String) rotateComboBox_.getSelectedItem())) {
         return FlipperProcessor.R270;
      }
      return FlipperProcessor.R0;
   }

   public final boolean getMirror() {
      return mirrorCheckBox_.isSelected();
   }

   private void processExample() {
      ByteProcessor proc = new ByteProcessor(
            IconLoader.loadFromResource(EXAMPLE_ICON_PATH));

      Image testImage = studio_.data().ij().createImage(proc,
            Coordinates.builder().build(),
            studio_.data().getMetadataBuilder().build());
      testImage = FlipperProcessor.transformImage(studio_, testImage,
            getMirror(), getRotate());
      exampleImageTarget_.setIcon(
            new ImageIcon(studio_.data().ij().createProcessor(testImage).createImage()));
   }
   
   
   @Override
   public PropertyMap getSettings() {
      PropertyMap.Builder builder = PropertyMaps.builder(); 
      builder.putString("camera", getCamera());
      builder.putInteger("rotation", getRotate());
      builder.putBoolean("shouldMirror", getMirror());
      return builder.build();
   }
}