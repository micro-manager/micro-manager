///////////////////////////////////////////////////////////////////////////////
//FILE:          PointAndShootDialog.java
//PROJECT:       Micro-Manager  
//SUBSYSTEM:     PointAndShoot plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman
//
// COPYRIGHT:    University of California, San Francisco 2018
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


package org.micromanager.pointandshootanalysis;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.dnd.DropTarget;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.event.ChangeEvent;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;
import org.micromanager.display.DataViewer;
import org.micromanager.pointandshootanalysis.data.Terms;
import org.micromanager.propertymap.MutablePropertyMapView;

// Imports for MMStudio internal packages
// Plugins should not access internal packages, to ensure modularity and
// maintainability. However, this plugin code is older than the current
// MMStudio API, so it still uses internal classes and interfaces. New code
// should not imitate this practice.
import org.micromanager.internal.utils.FileDialogs;
import org.micromanager.internal.utils.MMDialog;

/**
 *
 * @author nico
 */
public class PointAndShootDialog extends MMDialog {
   

   private final Studio studio_;
   private final MutablePropertyMapView profileSettings_;
   private boolean wasDisposed_;
   private final JLabel statusString_;
   private final JLabel progressString_;
   
   
   
   PointAndShootDialog(Studio studio) {
      studio_ = studio;
      profileSettings_ = 
              studio_.profile().getSettings(PointAndShootDialog.class);
      wasDisposed_ = false;
      final MMDialog ourDialog = this;
      
      super.setTitle("Point and Shoot Analysis");
      super.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
      super.addWindowListener(new WindowAdapter() {
         @Override
         public void windowClosing(WindowEvent arg0) {
            dispose();
         }
      }
      );
      final Font arialSmallFont = new Font("Arial", Font.PLAIN, 12);
      final Dimension buttonSize = new Dimension(70, 21);
      final Dimension smallButtonSize = new Dimension(25, 21);
      final Dimension spinnerSize = new Dimension(80, 21);
      
      super.setLayout(new MigLayout());
      super.loadAndRestorePosition(100, 100);
      
      JLabel explanationLabel = new JLabel("Provide location of text file " +
             "containing Point and Shoot  timestamps and locations");
      super.add(explanationLabel, "span 2, wrap");
      
      final JTextField locationsField = new JTextField(50);
      locationsField.setFont(arialSmallFont);
      locationsField.setText(profileSettings_.getString(Terms.LOCATIONSFILENAME,
               profileSettings_.getString(Terms.LOCATIONSFILENAME, "")));
      locationsField.setHorizontalAlignment(JTextField.LEFT);
      super.add(locationsField, "span 2, split 2");

      final JButton locationsFieldButton =  mcsButton(smallButtonSize, arialSmallFont);
      locationsFieldButton.setText("...");
      locationsFieldButton.addActionListener((ActionEvent evt) -> {
         File f = FileDialogs.openFile(ourDialog, "Locations File",
                 new FileDialogs.FileType("MMProjector", "Locations File",
                         locationsField.getText(), true, Terms.FILESUFFIXES));
         if (f != null) {
            locationsField.setText(f.getAbsolutePath());
         }
      });
      super.add(locationsFieldButton, "wrap");
      
      JLabel radiusText = new JLabel("Radius of bleach spot (pixels)");
      super.add(radiusText);
      
      int radius = profileSettings_.getInteger(Terms.RADIUS, 3);
      final SpinnerNumberModel sModel = new SpinnerNumberModel(radius, 1, 20, 1);
      final JSpinner radiusSpinner = new JSpinner (sModel);
      radiusSpinner.setMinimumSize(spinnerSize);
      radiusSpinner.addChangeListener((ChangeEvent e) -> {
         profileSettings_.putInteger(Terms.RADIUS, (Integer) radiusSpinner.getValue());
      });
      super.add(radiusSpinner, "wrap");
      
      JLabel nrFramesBeforeText = new JLabel("Frames before bleach (used to normalize)");
      super.add(nrFramesBeforeText);
      int nrFramesBefore = profileSettings_.getInteger(Terms.NRFRAMESBEFORE, 4);
      final SpinnerNumberModel beforeModel = new SpinnerNumberModel(nrFramesBefore, 1, 1000, 1);
      final JSpinner beforeSpinner = new JSpinner (beforeModel);
      beforeSpinner.setMinimumSize(spinnerSize);
      beforeSpinner.addChangeListener((ChangeEvent e) -> {
         profileSettings_.putInteger(Terms.NRFRAMESBEFORE, (Integer) beforeSpinner.getValue());
      });
      super.add(beforeSpinner, "wrap");
      /*
      JLabel nrFramesAfterText = new JLabel("Nr. of Frames after");
      super.add(nrFramesAfterText);
      int nrFramesAfter = profileSettings_.getInteger(Terms.NRFRAMESAFTER, 40);
      final SpinnerNumberModel afterModel = new SpinnerNumberModel(nrFramesAfter, 1, 1000, 1);
      final JSpinner afterSpinner = new JSpinner (afterModel);
      afterSpinner.addChangeListener((ChangeEvent e) -> {
         profileSettings_.putInteger(Terms.NRFRAMESAFTER, (Integer) afterSpinner.getValue());
      });
      super.add(afterSpinner, "wrap");
      */
      
      super.add(new JLabel("Max distance (pixels)"));
      int maxDistance = profileSettings_.getInteger(Terms.MAXDISTANCE, 3);
      final SpinnerNumberModel maxDistanceModel = new SpinnerNumberModel(maxDistance, 1, 100, 1);
      final JSpinner maxDistanceSpinner = new JSpinner (maxDistanceModel);
      maxDistanceSpinner.setMinimumSize(spinnerSize);
      maxDistanceSpinner.addChangeListener((ChangeEvent e) -> {
         profileSettings_.putInteger(Terms.MAXDISTANCE, (Integer) maxDistanceSpinner.getValue());
      });
      super.add(maxDistanceSpinner, "wrap");
      
      super.add(new JLabel("Camera Offset"));
      int offset = profileSettings_.getInteger(Terms.CAMERAOFFSET, 100);
      final SpinnerNumberModel offsetModel = new SpinnerNumberModel(offset, 1, 10000, 10);
      final JSpinner offsetSpinner = new JSpinner (offsetModel);
      offsetSpinner.setMinimumSize(spinnerSize);
      offsetSpinner.addChangeListener((ChangeEvent e) -> {
         profileSettings_.putInteger(Terms.CAMERAOFFSET, (Integer) offsetSpinner.getValue());
      });
      super.add(offsetSpinner, "wrap");
      
      // Even when we no longer
      // can measure a bleach spot, continue tracking it based on previous position relative
      // to particle centroid
      super.add(new JLabel("Fix Bleach positions based on particle centroid"));
      boolean fix = profileSettings_.getBoolean(Terms.FIXBLEACHINPARTICLE, true);
      final JCheckBox fixBleachBox = new JCheckBox();
      fixBleachBox.setSelected(fix);
      fixBleachBox.addChangeListener((ChangeEvent e) -> {
         profileSettings_.putBoolean(Terms.FIXBLEACHINPARTICLE, fixBleachBox.isSelected());
      });
      super.add(fixBleachBox, "wrap");
      
      super.add(new JLabel("Nr of frames used to fix bleach-particle offset"));
      int nrFramesToFixBleach = profileSettings_.getInteger(Terms.NRFRAMESTOFIXBLEACH, 20);
      final SpinnerNumberModel fixFramesModel = new SpinnerNumberModel(nrFramesToFixBleach, 1, 100, 1);
      final JSpinner fixBleachSpinner = new JSpinner (fixFramesModel);
      fixBleachSpinner.setMinimumSize(spinnerSize);
      fixBleachSpinner.addChangeListener((ChangeEvent e) -> {
         profileSettings_.putInteger(Terms.NRFRAMESTOFIXBLEACH, (Integer) fixBleachSpinner.getValue());
      });
      super.add(fixBleachSpinner, "wrap");
      
      JButton helpButton = mcsButton(buttonSize, arialSmallFont);
      helpButton.setText("Help");
      helpButton.addActionListener((ActionEvent evt) -> {
         new Thread(org.micromanager.internal.utils.GUIUtils.makeURLRunnable(
                 "https://micro-manager.org/wiki/Point_and_Shoot_Analysis")).start();
      });
      super.add(helpButton, "span 2, split 3");

      JButton cancelButton = mcsButton(buttonSize, arialSmallFont);
      cancelButton.setText("Cancel");
      cancelButton.addActionListener((ActionEvent evt) -> {
         ourDialog.dispose();
      });
      super.add(cancelButton, "tag cancel");
      
      JButton okButton = mcsButton(buttonSize, arialSmallFont);
      okButton.setText("Execute");
      okButton.addActionListener((ActionEvent evt) -> {
         DataViewer activeDataViewer = studio_.displays().getActiveDataViewer();
         if (activeDataViewer == null) {
            studio_.logs().showError("Please open image data first");
            return;
         }
         String fileName = locationsField.getText();
         File f = new File(fileName);
         if (!f.exists()) {
            studio_.logs().showError("File " + f.getName() + " does not exist");
            return;
         }
         if (!f.canRead()) {
            studio_.logs().showError("File " + f.getName() + " is not readable");
            return;
         }
         profileSettings_.putString(Terms.LOCATIONSFILENAME, fileName);
         Thread analysisThread = new Thread(new PointAndShootAnalyzer(studio,
                 profileSettings_.toPropertyMap(), this));
         analysisThread.start();
         //ourDialog.dispose();
      });
      super.add(okButton, "tag ok, wrap");
      
      statusString_ = new JLabel("Inactive...");
      super.add(statusString_, "wrap");
      
      progressString_ = new JLabel(" 0%");
      super.add(progressString_);   
            
      DragDropListener dragDropListener = new DragDropListener(locationsField);
      new DropTarget(this, dragDropListener);
      new DropTarget(locationsField, dragDropListener);
      
      super.pack();
      super.setVisible(true);
      
   }
   
   boolean wasDisposed() { return wasDisposed_; }
   
   @Override
   public void dispose() {
      super.dispose();
      wasDisposed_ = true;
   }
   
   public void setStatus(String status) {
      if (!SwingUtilities.isEventDispatchThread()) {
         SwingUtilities.invokeLater(() -> {
            setStatus(status);
         });
      }
      statusString_.setText(status);
   }
   
   public void setProgress(double progress) {
      if (!SwingUtilities.isEventDispatchThread()) {
         SwingUtilities.invokeLater(() -> {
            setProgress(progress);
         });
      }
      int p = (int) (progress * 100.0);
      progressString_.setText(" " + p + "%");
   }
   
   
   private JButton mcsButton(Dimension buttonSize, Font font) {
      JButton button = new JButton();
      button.setPreferredSize(buttonSize);
      button.setMinimumSize(buttonSize);
      button.setFont(font);
      button.setMargin(new Insets(0, 0, 0, 0));
      
      return button;
   }
}
