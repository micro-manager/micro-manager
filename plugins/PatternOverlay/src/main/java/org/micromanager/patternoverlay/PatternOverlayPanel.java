package org.micromanager.patternoverlay;

import ij.gui.ImageCanvas;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.Graphics;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JSlider;
import javax.swing.JToggleButton;

import net.miginfocom.swing.MigLayout;

import org.micromanager.data.Image;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.OverlayPanel;
import org.micromanager.Studio;

/**
 * Provides the GUI for configuring our various pattern overlays. Adapted
 * from the original system written by Matthijs and Jon, but MM2.0's overlay
 * system is substantially different, hence the rewrite.
 */
public class PatternOverlayPanel extends OverlayPanel {
   private static final String OVERLAY_MODE = "selected overlay mode";
   private static final String IS_DISPLAYED = "is the overlay displayed";
   private static final String OVERLAY_SIZE = "size of overlay";
   private static final String OVERLAY_COLOR = "color of overlay";

   private final JComboBox overlaySelector_;
   private final JToggleButton displayToggle_;
   private final JSlider sizeSlider_;
   private final JComboBox colorSelector_;

   /**
    * NOTE: we store our settings in the profile rather than the display
    * settings, because it's assumed that our use is mostly for during
    * acquisition, where it's appropriate for settings to be more "global".
    * @param studio Micro-Manager Interface
    */
   public PatternOverlayPanel(final Studio studio) {
      setLayout(new MigLayout("", "[right]10[center]", "[]8[]"));
      overlaySelector_ = new JComboBox(OverlayOptions.OPTIONS);
      overlaySelector_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            String selection = (String) overlaySelector_.getSelectedItem();
            displayToggle_.setText("Show " + selection);
            if (displayToggle_.isSelected()) {
               redraw();
            }
            studio.profile().setString(PatternOverlayPanel.class,
               OVERLAY_MODE, selection);
         }
      });

      add(new JLabel("Type:"));
      add(overlaySelector_, "wrap");

      displayToggle_ = new JToggleButton();
      displayToggle_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            redraw();
            studio.profile().setBoolean(PatternOverlayPanel.class,
               IS_DISPLAYED, displayToggle_.isSelected());
         }
      });
      add(displayToggle_, "span 2, wrap, growx");

      sizeSlider_ = new JSlider();
      sizeSlider_.addChangeListener(new ChangeListener() {
         @Override
         public void stateChanged(ChangeEvent e) {
            if (displayToggle_.isSelected()) {
               redraw();
            }
            studio.profile().setInt(PatternOverlayPanel.class, OVERLAY_SIZE,
               sizeSlider_.getValue());
         }
      });
      add(new JLabel("Size:"));
      add(sizeSlider_, "wrap, width ::80");

      colorSelector_ = new JComboBox(OverlayOptions.COLOR_NAMES);
      colorSelector_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            if (displayToggle_.isSelected()) {
               redraw();
            }
            studio.profile().setString(PatternOverlayPanel.class,
               OVERLAY_COLOR, (String) colorSelector_.getSelectedItem());
         }
      });
      add(new JLabel("Color:"));
      add(colorSelector_, "wrap");

      // Load initial settings from the profile.
      overlaySelector_.setSelectedItem(studio.profile().getString(
               PatternOverlayPanel.class, OVERLAY_MODE,
               OverlayOptions.OPTIONS[0]));
      displayToggle_.setSelected(studio.profile().getBoolean(
               PatternOverlayPanel.class, IS_DISPLAYED, false));
      sizeSlider_.setValue(studio.profile().getInt(
               PatternOverlayPanel.class, OVERLAY_SIZE, 50));
      colorSelector_.setSelectedItem(studio.profile().getString(
               PatternOverlayPanel.class, OVERLAY_COLOR,
               OverlayOptions.COLOR_NAMES[0]));
   }

   @Override
   public void drawOverlay(Graphics g, DisplayWindow display, Image image,
         ImageCanvas canvas) {
      if (displayToggle_.isSelected()) {
         OverlayOptions.drawOverlay(g, display, image, canvas,
               (String) overlaySelector_.getSelectedItem(),
               sizeSlider_.getValue(),
               (String) colorSelector_.getSelectedItem());
      }
   }

}
