///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Display implementation
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2015
//
// COPYRIGHT:    University of California, San Francisco, 2015
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

package org.micromanager.display.internal.overlays;

import ij.gui.ImageCanvas;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyAdapter;
import java.awt.Font;
import java.awt.Graphics;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTextField;

import net.miginfocom.swing.MigLayout;

import org.micromanager.data.Image;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.OverlayPanel;
import org.micromanager.Studio;

import org.micromanager.internal.utils.ReportingUtils;

/**
 * This class provides a GUI for drawing a scale bar.
 */
public class ScaleBarPanel extends OverlayPanel {
   private static final String DRAW_TEXT = "scaleBarOverlay: whether or not to draw text";
   private static final String FONT_SIZE = "scaleBarOverlay: font size";
   private static final String IS_FILLED = "scaleBarOverlay: if the scale bar is drawn as solid";
   private static final String COLOR = "scaleBarOverlay: color to draw everything";
   private static final String X_OFFSET = "scaleBarOverlay: X offset at which to draw";
   private static final String Y_OFFSET = "scaleBarOverlay: Y offset at which to draw";
   private static final String SIZE = "scaleBarOverlay: size of scalebar in microns";
   private static final String BAR_WIDTH = "scaleBarOverlay: width of scalebar";
   private static final String POSITION = "scaleBarOverlay: corner to draw in";

   private static final Color[] COLORS = new Color[] {
      Color.black, Color.white, Color.gray, Color.yellow, Color.orange,
         Color.red, Color.magenta, Color.blue, Color.cyan, Color.green};
   // Would you believe this isn't built-in to the Color module? Naturally
   // this list of names must match the list of colors, above.
   private static final String[] COLORNAMES = new String[] {
      "Black", "White", "Gray", "Yellow", "Orange", "Red", "Magenta",
         "Blue", "Cyan", "Green"
   };

   private Studio studio_;

   private final JCheckBox shouldDrawText_;
   private final JCheckBox isBarFilled_;
   private final JComboBox color_;
   private JTextField fontSize_;
   private final JTextField xOffset_;
   private final JTextField yOffset_;
   private final JTextField scaleSize_;
   private JTextField barWidth_;
   private final JComboBox position_;

   private boolean haveLoggedError_ = false;
   private boolean shouldIgnoreEvents_ = false;

   public ScaleBarPanel(Studio studio) {
      studio_ = studio;

      ActionListener changeListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
               if (!shouldIgnoreEvents_) {
                   saveSettings();
                   redraw();
               }
            }
      };
      KeyAdapter keyAdapter = new KeyAdapter() {
         @Override
         public void keyPressed(KeyEvent event) {
            if (!shouldIgnoreEvents_) {
               saveSettings();
               redraw();
            }
         }
      };
      setLayout(new MigLayout("flowy"));

      add(new JLabel("Color: "));
      color_ = new JComboBox(COLORNAMES);
      color_.addActionListener(changeListener);
      add(color_);

      shouldDrawText_ = new JCheckBox("Show scale text");
      shouldDrawText_.addActionListener(changeListener);
      add(shouldDrawText_);

      add(new JLabel("Font size: "), "split 2, flowx");
      fontSize_ = new JTextField("14", 3);
      fontSize_.addKeyListener(keyAdapter);
      add(fontSize_);

      add(new JLabel("X offset: "), "split 2, flowx");
      xOffset_ = new JTextField("0", 3);
      xOffset_.addKeyListener(keyAdapter);
      add(xOffset_, "wrap");

      isBarFilled_ = new JCheckBox("Solid scale bar");
      isBarFilled_.addActionListener(changeListener);
      add(isBarFilled_);

      add(new JLabel("Bar thickness: "), "split 2, flowx");
      barWidth_ = new JTextField("5", 3);
      barWidth_.addKeyListener(keyAdapter);
      add(barWidth_);

      add(new JLabel("Position: "));
      position_ = new JComboBox(new String[] {
            "Upper left", "Upper right", "Lower right", "Lower left"});
      position_.addActionListener(changeListener);
      add(position_);

      add(new JLabel("Size (\u00B5m):"), "split 2, flowx");
      scaleSize_ = new JTextField("80", 3);
      scaleSize_.addActionListener(changeListener);
      add(scaleSize_);

      add(new JLabel("Y offset: "), "split 2, flowx");
      yOffset_ = new JTextField("0", 3);
      yOffset_.addKeyListener(keyAdapter);
      add(yOffset_);
   }

   /**
    * Record our current settings in the user profile.
    */
   private void saveSettings() {
      studio_.profile().setInt(ScaleBarPanel.class,
            COLOR, color_.getSelectedIndex());
      studio_.profile().setBoolean(ScaleBarPanel.class,
            DRAW_TEXT, shouldDrawText_.isSelected());
      studio_.profile().setString(ScaleBarPanel.class,
            FONT_SIZE, fontSize_.getText());
      studio_.profile().setString(ScaleBarPanel.class,
            X_OFFSET, xOffset_.getText());
      studio_.profile().setString(ScaleBarPanel.class,
            Y_OFFSET, yOffset_.getText());
      studio_.profile().setBoolean(ScaleBarPanel.class,
            IS_FILLED, isBarFilled_.isSelected());
      studio_.profile().setString(ScaleBarPanel.class,
            BAR_WIDTH, barWidth_.getText());
      studio_.profile().setInt(ScaleBarPanel.class,
            POSITION, position_.getSelectedIndex());
      studio_.profile().setString(ScaleBarPanel.class,
            SIZE, scaleSize_.getText());
   }

   // Update our controls to reflect the settings stored in the profile.
   @Override
   public void setDisplay(DisplayWindow display) {
      super.setDisplay(display);
      if (display == null) {
         return;
      }

      // Don't cause redraws while we're busy resetting our values.
      shouldIgnoreEvents_ = true;
      color_.setSelectedIndex(studio_.profile().getInt(
               ScaleBarPanel.class, COLOR, 0));
      shouldDrawText_.setSelected(studio_.profile().getBoolean(
               ScaleBarPanel.class, DRAW_TEXT, true));
      fontSize_.setText(studio_.profile().getString(
               ScaleBarPanel.class, FONT_SIZE, "14"));
      xOffset_.setText(studio_.profile().getString(
               ScaleBarPanel.class, X_OFFSET, "15"));
      yOffset_.setText(studio_.profile().getString(
               ScaleBarPanel.class, Y_OFFSET, "15"));
      isBarFilled_.setSelected(studio_.profile().getBoolean(
               ScaleBarPanel.class, IS_FILLED, true));
      barWidth_.setText(studio_.profile().getString(
               ScaleBarPanel.class, BAR_WIDTH, "5"));
      position_.setSelectedIndex(studio_.profile().getInt(
               ScaleBarPanel.class, POSITION, 0));
      scaleSize_.setText(studio_.profile().getString(
               ScaleBarPanel.class, SIZE, "100"));
      shouldIgnoreEvents_ = false;
      redraw();
   }

   @Override
   public void drawOverlay(Graphics g, DisplayWindow display, Image image, ImageCanvas canvas) {
      Double pixelSize = image.getMetadata().getPixelSizeUm();
      if (pixelSize == null || Math.abs(pixelSize - 0) < .0001) {
         // No pixel size info available. Log an error message, once, and then
         // do not proceed.
         if (!haveLoggedError_) {
            ReportingUtils.showError("Unable to display scale bar: pixel size information not available.");
            haveLoggedError_ = true;
         }
         return;
      }

      int scaleSize = 0;
      try {
         // Throw away any floating point portion of the scale bar.
         scaleSize = (int) Double.parseDouble(scaleSize_.getText());
      }
      catch (NumberFormatException e) {} // Ignore it.

      int width = (int) (scaleSize / pixelSize * canvas.getMagnification());
      g.setColor(COLORS[color_.getSelectedIndex()]);
      int fontSize = getText(fontSize_, 14);
      int xOffset = getText(xOffset_, 0);
      int yOffset = getText(yOffset_, 0);
      int barWidth = getText(barWidth_, 5);

      String position = (String) position_.getSelectedItem();
      Dimension canvasSize = canvas.getPreferredSize();
      if (position.equals("Upper right") || position.equals("Lower right")) {
         xOffset = canvasSize.width - xOffset - 80;
      }
      if (position.equals("Lower left") || position.equals("Lower right")) {
         yOffset = canvasSize.height - yOffset - 13;
      }

      if (shouldDrawText_.isSelected()) {
         g.setFont(new Font("Arial", Font.PLAIN, fontSize));
         g.drawString(String.format("%dum", scaleSize), xOffset, yOffset);
      }
      if (isBarFilled_.isSelected()) {
         g.fillRect(xOffset, yOffset + 6, width, barWidth);
      }
      else {
         g.drawRect(xOffset, yOffset + 6, width, barWidth);
      }
   }

   /**
    * Try to parse out the value from the provided text field, returning the
    * given default on failure.
    */
   private static int getText(JTextField field, int defaultVal) {
      int result = defaultVal;
      try {
         result = Integer.parseInt(field.getText());
      }
      catch (NumberFormatException e) {}
      return result;
   }
}
