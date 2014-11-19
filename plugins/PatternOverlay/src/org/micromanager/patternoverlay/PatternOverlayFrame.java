///////////////////////////////////////////////////////////////////////////////
//FILE:          PatternOverlayFrame.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Image Overlay plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Jon Daniels
//
// COPYRIGHT:    Applied Scientific Instrumentation, 2014
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


package org.micromanager.patternoverlay;


import ij.ImagePlus;
import ij.gui.ImageWindow;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JToggleButton;
import javax.swing.JLabel;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.micromanager.MMStudio;
import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.MMFrame;

import net.miginfocom.swing.MigLayout;


/**
 *  The plugin window.
 *  Enables the user to set preferences like overlay type and size.
 *  
 *  To make a new pattern:
 *  1. Derive from class GenericOverlay and implement getRoi() which
 *     creates and returns an ImageJ Roi object for the overlay.
 *     See existing overlays as examples
 *     (e.g. CrosshairOverlay.java, GridOverlay.java).
 *  2. Add an entry to the OverayOption.Keys enum
 *  3. Create/add the object along with associated key to the overlayModel
 *     variable in the constructor of PatternOverlayFrame.
 *
 *  @author Matthijs
 *  @author Jon
 */
@SuppressWarnings("serial")
public class PatternOverlayFrame extends MMFrame {
   private final ScriptInterface gui_;
   private final JComboBox overlayBox_;
   private final JToggleButton toggleButton_;
   private final JSlider sizeSlider_;
   private final JComboBox colorBox_;
   
   private GenericOverlay lastOverlay_;

   public PatternOverlayFrame(ScriptInterface gui) {
      this.setLayout(new MigLayout(
            "",
            "[right]10[center]",
            "[]8[]"));
      gui_ = gui;
      final MMFrame ourFrame = this;
      loadAndRestorePosition(100, 100, WIDTH, WIDTH);
      
      lastOverlay_ = null;
      
      add(new JLabel("Type:"));
      overlayBox_ = new JComboBox();
      add(overlayBox_, "wrap");
      DefaultComboBoxModel overlayModel = new DefaultComboBoxModel();
      overlayModel.addElement(new OverlayOption(OverlayOption.Keys.CROSSHAIR,
            new CrosshairOverlay(getPrefsNode(), OverlayOption.Keys.CROSSHAIR.toString() + "_")));
      overlayModel.addElement(new OverlayOption(OverlayOption.Keys.GRID,
            new GridOverlay(getPrefsNode(), OverlayOption.Keys.GRID.toString() + "_")));
      overlayModel.addElement(new OverlayOption(OverlayOption.Keys.CIRCLE,
            new CircleOverlay(getPrefsNode(), OverlayOption.Keys.CIRCLE.toString() + "_")));
      overlayModel.addElement(new OverlayOption(OverlayOption.Keys.TARGET,
            new TargetOverlay(getPrefsNode(), OverlayOption.Keys.TARGET.toString() + "_")));
      overlayBox_.setModel(overlayModel);
      overlayBox_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            getPrefsNode().putInt(Constants.TYPE_BOX_IDX, overlayBox_.getSelectedIndex());
            updateToggleButtonLabel();
            GenericOverlay currentOverlay = ((OverlayOption) overlayBox_.getSelectedItem()).getOverlay();
            try {
               // turn off the last-used overlay
               if (lastOverlay_ != null) {
                  lastOverlay_.setVisible(false);
               }
               currentOverlay.setVisible(toggleButton_.isSelected());
               sizeSlider_.setValue(currentOverlay.getSize());
               sizeSlider_.repaint();
               colorBox_.setSelectedIndex(currentOverlay.getColorCode());
            } catch (Exception e1) {
               gui_.showError(e1, ourFrame);
            }
            lastOverlay_ = currentOverlay;
         }
      });
      
      toggleButton_ = new JToggleButton();
      
      add(toggleButton_, "span 2, wrap, growx");
      toggleButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            try {
               boolean visible = toggleButton_.isSelected();
               GenericOverlay selectedOverlay = ((OverlayOption) overlayBox_.getSelectedItem()).getOverlay();
               selectedOverlay.setVisible(visible);
               updateToggleButtonLabel();
            } catch (Exception ex) {
               gui_.logError("Could not enable overlay (" + 
                     ((OverlayOption) overlayBox_.getSelectedItem()).toString() + "). "
                       + "Error Message: " + ex.getMessage() );
               gui_.showMessage(
                       "The overlay could not be shown. Is the live image window active?", 
                       ourFrame);
               toggleButton_.setSelected(false);
            }
         }
      });
      
      add(new JLabel("Size:"));
      sizeSlider_ = new JSlider();
      add(sizeSlider_, "wrap, width ::80");
      sizeSlider_.addChangeListener(new ChangeListener() {
         @Override
         public void stateChanged(ChangeEvent e) {
            ((OverlayOption) overlayBox_.getSelectedItem()).getOverlay().setSize(sizeSlider_.getValue());
            // pref save handled by GenericOverlay
         }
      });

      add(new JLabel("Color:"));
      colorBox_ = new JComboBox();
      add(colorBox_, "wrap");
      DefaultComboBoxModel colorModel = new DefaultComboBoxModel(Constants.COLOR_OPTION_ARRAY);
      colorBox_.setModel(colorModel);
      colorBox_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            ((OverlayOption) overlayBox_.getSelectedItem()).getOverlay().setColorCode(colorBox_.getSelectedIndex());
         // pref save handled by GenericOverlay
         }
      });
      
      // setting this from prefs needs to come after toggle button is created
      // and also color and size boxes because all are referenced by ActionListener
      overlayBox_.setSelectedIndex(getPrefsNode().getInt(Constants.TYPE_BOX_IDX, 0));
      updateToggleButtonLabel();
          
      pack();           // shrinks the window as much as it can
      setResizable(false);

      addWindowListener(new java.awt.event.WindowAdapter() {
         @Override
         public void windowClosing(java.awt.event.WindowEvent evt) {
            // turn overlay off before exiting
            if (toggleButton_.isSelected()) {
               toggleButton_.doClick();
            }
         }
      });

   }//constructor
   
   private void updateToggleButtonLabel() {
      String selectedOverlayStr = ((OverlayOption) overlayBox_.getSelectedItem()).toString();
      if (toggleButton_.isSelected()) {
         toggleButton_.setText("Hide " + selectedOverlayStr);
      } else {
         toggleButton_.setText("Show " + selectedOverlayStr);
      }
   }


   /**
    *  Get reference to the live image window, through the MicroManager
    *  studioMainFrame instance.
    *
    *  @return Reference to the ImagePlus object associated with the live
    *          window. Will return null if no live image is currently active.
    */
   public static ImagePlus getLiveWindowImage () {
      ImageWindow window = MMStudio.getInstance().getSnapLiveWin();
      if (window == null) {
         return null;
      } else {
         return window.getImagePlus();
      }
   }

}
