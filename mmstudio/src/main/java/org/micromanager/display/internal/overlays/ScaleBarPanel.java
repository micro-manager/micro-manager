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
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;
import org.micromanager.data.Image;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.OverlayPanel;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * This class provides a GUI for drawing a scale bar.
 */
public final class ScaleBarPanel extends OverlayPanel {
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

   private final Studio studio_;

   private final JCheckBox shouldDrawText_;
   private final JCheckBox isBarFilled_;
   private final JComboBox color_;
   private final JTextField fontSize_;
   private final JTextField xOffset_;
   private final JTextField yOffset_;
   private final JTextField scaleSize_;
   private final JTextField barWidth_;
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
      
      DocumentListener docListener = new DocumentListener() {
         @Override
         public void insertUpdate(DocumentEvent e) {
            update();
         }

         @Override
         public void removeUpdate(DocumentEvent e) {
            update();
         }

         @Override
         public void changedUpdate(DocumentEvent e) {
            update();
         }
         
         public void update() {
            if (!shouldIgnoreEvents_) {
               saveSettings();
               redraw();
            }
         }
      };
      

      super.setLayout(new MigLayout("ins 2, flowx"));

      super.add(new JLabel("Color: "), "span 3, split 2");
      color_ = new JComboBox(COLORNAMES);
      color_.addActionListener(changeListener);
      super.add(color_);
      
      super.add(new JLabel("Position: "), "span 4, split 2");
      position_ = new JComboBox(new String[] {
            "Upper left", "Upper right", "Lower right", "Lower left"});
      position_.addActionListener(changeListener);
      super.add(position_, "wrap");

      shouldDrawText_ = new JCheckBox("Show text");
      shouldDrawText_.addActionListener(changeListener);
      super.add(shouldDrawText_, "span 2");     
      
      isBarFilled_ = new JCheckBox("Solid scale bar");
      isBarFilled_.addActionListener(changeListener);
      super.add(isBarFilled_, "span 2");
            
      super.add(new JLabel("Size (\u00B5m):") );
      scaleSize_ = new JTextField("80", 3);
      scaleSize_.addActionListener(changeListener);
      super.add(scaleSize_, "wrap");

      super.add(new JLabel("Font size: "));
      fontSize_ = new JTextField("14", 3);
      fontSize_.getDocument().addDocumentListener(docListener);
      super.add(fontSize_);
      
      super.add(new JLabel("Bar height: "), "gapleft 10");
      barWidth_ = new JTextField("5", 3);
      barWidth_.getDocument().addDocumentListener(docListener);
      super.add(barWidth_);

      super.add(new JLabel("X offset: "), "gapleft 10");
      xOffset_ = new JTextField("0", 3);
      xOffset_.getDocument().addDocumentListener(docListener);
      super.add(xOffset_);
            
      super.add(new JLabel("Y offset: "), "gapleft 10");
      yOffset_ = new JTextField("0", 3);
      yOffset_.getDocument().addDocumentListener(docListener);
      super.add(yOffset_, "wrap");

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
      int xOffset = getText(xOffset_, 10);
      int yOffset = getText(yOffset_, 10);
      int barHeight = getText(barWidth_, 5);
      Font ourFont = new Font("Arial", Font.PLAIN, fontSize);
      int stringHeight = g.getFontMetrics(g.getFont()).getHeight();
      yOffset += stringHeight;

      String position = (String) position_.getSelectedItem();
      Dimension canvasSize = canvas.getPreferredSize();
      if (position.equals("Upper right") || position.equals("Lower right")) {
         xOffset = canvasSize.width - xOffset - width;
      }
      if (position.equals("Lower left") || position.equals("Lower right")) {
         yOffset = canvasSize.height - yOffset - barHeight + stringHeight - 6;
      }

      if (shouldDrawText_.isSelected()) {
         g.setFont(ourFont);
         int stringWidth = g.getFontMetrics(g.getFont()).stringWidth(String.format("%dum", scaleSize));
         int xPosition = xOffset + (int) (0.5 * width) - (int) (0.5 * stringWidth);
         g.drawString(String.format("%dum", scaleSize), xPosition, yOffset);
      }
      if (isBarFilled_.isSelected()) {
         g.fillRect(xOffset, yOffset + 6, width, barHeight);
      }
      else {
         g.drawRect(xOffset, yOffset + 6, width, barHeight);
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
