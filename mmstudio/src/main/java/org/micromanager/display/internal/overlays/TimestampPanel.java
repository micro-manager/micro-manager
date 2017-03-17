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
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;
import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import org.micromanager.data.Metadata;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.OverlayPanel;

/**
 * This overlay draws the timestamps of the currently-displayed images.
 */
public final class TimestampPanel extends OverlayPanel {

   // Position options
   private static final String UPPER_LEFT = "Upper left";
   private static final String UPPER_RIGHT = "Upper right";
   private static final String LOWER_LEFT = "Lower left";
   private static final String LOWER_RIGHT = "Lower right";

   // Options for which timestamp to display
   private static final String ABSOLUTE_TIME = "Absolute";
   private static final String RELATIVE_TIME = "Relative to Start";

   // Keys for storing values in the profile.
   private static final String IS_MULTI_CHANNEL = "is multi-channel";
   private static final String INCLUDE_BACKGROUND = "draw on background";
   private static final String X_OFFSET = "x offset";
   private static final String Y_OFFSET = "y offset";
   private static final String POSITION_INDEX = "position";
   private static final String COLOR_INDEX = "color";
   private static final String FORMAT_INDEX = "format";

   private final Studio studio_;
   private final JCheckBox amMultiChannel_;
   private final JCheckBox shouldDrawBackground_;
   private final JTextField xOffset_;
   private final JTextField yOffset_;
   private final JComboBox position_;
   private final JComboBox color_;
   private final JComboBox format_;
   
   private boolean shouldIgnoreEvents_ = false;

   public TimestampPanel(Studio studio) {
      studio_ = studio;
      super.setLayout(new MigLayout("ins 2, flowx"));

      ActionListener redrawListener = new ActionListener() {
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
      
      
      super.add(new JLabel("Color: "));
      color_ = new JComboBox(new String[] {
            "White", "Black", "Channel color"});
      color_.addActionListener(redrawListener);
      super.add(color_);

      super.add(new JLabel("Position: "), "gapleft 10");
      position_ = new JComboBox(new String[] {
            UPPER_LEFT, UPPER_RIGHT, LOWER_RIGHT, LOWER_LEFT});
      position_.addActionListener(redrawListener);
      super.add(position_, "wrap");

      super.add(new JLabel("Format:"));
      format_ = new JComboBox(new String[] {RELATIVE_TIME, ABSOLUTE_TIME});
      format_.addActionListener(redrawListener);
      super.add(format_, "wrap");

      shouldDrawBackground_ = new JCheckBox("Draw background");
      shouldDrawBackground_.setToolTipText("Draw a background (black, unless black text is selected) underneath the timestamp(s) to improve contrast");
      shouldDrawBackground_.addActionListener(redrawListener);
      super.add(shouldDrawBackground_, "span 6, split 6");

      amMultiChannel_ = new JCheckBox("Per-channel");
      amMultiChannel_.setToolTipText("Draw one timestamp per channel in the display.");
      amMultiChannel_.addActionListener(redrawListener);
      super.add(amMultiChannel_, "gapleft 10");


      super.add(new JLabel("X offset: "), "gapleft 10");
      xOffset_ = new JTextField("0", 3);
      xOffset_.getDocument().addDocumentListener(docListener);
      super.add(xOffset_);

      super.add(new JLabel("Y offset: "), "gapleft 10" );
      yOffset_ = new JTextField("0", 3);
      yOffset_.getDocument().addDocumentListener(docListener);
      super.add(yOffset_, "wrap");
   }

   @Override
   public void setDisplay(DisplayWindow display) {
      super.setDisplay(display);
      if (display == null) {
         return;
      }
      
      // Don't cause redraws while we're busy resetting our values.
      shouldIgnoreEvents_ = true;
      position_.setSelectedIndex(studio_.profile().getInt(
               TimestampPanel.class, POSITION_INDEX, 0));
      format_.setSelectedIndex(studio_.profile().getInt(
               TimestampPanel.class, FORMAT_INDEX, 0));
      shouldDrawBackground_.setSelected(
            studio_.profile().getBoolean(
               TimestampPanel.class, INCLUDE_BACKGROUND, false));
      amMultiChannel_.setSelected(
            studio_.profile().getBoolean(
               TimestampPanel.class, IS_MULTI_CHANNEL, false));
      color_.setSelectedIndex(studio_.profile().getInt(
               TimestampPanel.class, COLOR_INDEX, 0));
      xOffset_.setText(studio_.profile().getString(
               TimestampPanel.class, X_OFFSET, "0"));
      yOffset_.setText(studio_.profile().getString(
               TimestampPanel.class, Y_OFFSET, "0"));
      
      shouldIgnoreEvents_ = false;
   }

   /**
    * Record our settings in the profile.
    */
   private void saveSettings() {
      studio_.profile().setBoolean(TimestampPanel.class,
            IS_MULTI_CHANNEL, amMultiChannel_.isSelected());
      studio_.profile().setBoolean(TimestampPanel.class,
            INCLUDE_BACKGROUND, shouldDrawBackground_.isSelected());
      String xo = xOffset_.getText();
      studio_.profile().setString(TimestampPanel.class,
            X_OFFSET, xo);
      studio_.profile().setString(TimestampPanel.class,
            Y_OFFSET, yOffset_.getText());
      studio_.profile().setInt(TimestampPanel.class,
            POSITION_INDEX, position_.getSelectedIndex());
      studio_.profile().setInt(TimestampPanel.class,
            COLOR_INDEX, color_.getSelectedIndex());
      studio_.profile().setInt(TimestampPanel.class,
            FORMAT_INDEX, format_.getSelectedIndex());
   }

   /**
    * We draw one row of text per channel in multi-channel mode, or just the
    * provided image's timestamp in normal mode.
    */
   @Override
   public void drawOverlay(Graphics graphics, DisplayWindow display,
         Image image, ImageCanvas canvas) {
      Graphics2D g = (Graphics2D) graphics;
      ArrayList<String> timestamps = new ArrayList<String>();
      ArrayList<Color> colors = new ArrayList<Color>();
      Datastore store = display.getDatastore();
      if (amMultiChannel_.isSelected()) {
         for (int i = 0; i < store.getAxisLength(Coords.CHANNEL); ++i) {
            Coords channelCoords = image.getCoords().copy().channel(i).build();
            Image channelImage = store.getImage(channelCoords);
            if (channelImage != null) {
               addTimestamp(channelImage, display, timestamps, colors);
            }
         }
      }
      else {
         addTimestamp(image, display, timestamps, colors);
      }

      // Determine text width, and check for background clashes.
      int textWidth = 0;
      Color backgroundColor = Color.BLACK;
      for (int i = 0; i < timestamps.size(); ++i) {
         textWidth = Math.max(textWidth,
               g.getFontMetrics(g.getFont()).stringWidth(timestamps.get(i)));
         if (colors.get(i) == Color.BLACK) {
            // Can't use black, so use white instead.
            backgroundColor = Color.WHITE;
         }
      }

      // This code is copied from the ScaleBarOverlayPanel, and then
      // slightly adapted to our different Y-positioning needs.
      int xOffset = 0, yOffset = 0;
      int yStep = 1;
      int textHeight = g.getFontMetrics(g.getFont()).getHeight();
      try {
         xOffset = Integer.parseInt(xOffset_.getText());
         yOffset = Integer.parseInt(yOffset_.getText());
      }
      catch (NumberFormatException e) {} // Just use what we have.

      String location = (String) position_.getSelectedItem();
      Dimension canvasSize = canvas.getPreferredSize();
      if (location.equals(UPPER_RIGHT) || location.equals(LOWER_RIGHT)) {
         xOffset = canvasSize.width - xOffset - textWidth;
      }
      if (location.equals(LOWER_LEFT) || location.equals(LOWER_RIGHT)) {
         yOffset = canvasSize.height - yOffset;
         // Change our step direction.
         yStep = -1;
      }
      if (location.equals(UPPER_LEFT) || location.equals(UPPER_RIGHT)) {
         // Need to move text down slightly.
         yOffset += textHeight;
      }

      if (shouldDrawBackground_.isSelected()) {
         int x = xOffset - 2;
         int width = textWidth + 4;
         int y = yOffset - textHeight - 2;
         if (yStep == -1) {
            // yOffset is the bottom of the box instead of the top.
            y = yOffset - textHeight * timestamps.size() - 2;
         }
         int height = textHeight * timestamps.size() + 4;
         g.setColor(backgroundColor);
         g.fillRect(x, y, width, height);
      }

      for (int i = 0; i < timestamps.size(); ++i) {
         g.setColor(colors.get(i));
         g.drawString(timestamps.get(i), xOffset,
               yOffset + yStep * i * textHeight);
      }
   }

   /**
    * Add the timestamp string and appropriate color to the provided lists.
    */
   private void addTimestamp(Image image, DisplayWindow display,
         ArrayList<String> timestamps, ArrayList<Color> colors) {
      // Default to black.
      Color textColor = Color.BLACK;
      String textMode = (String) color_.getSelectedItem();
      if (textMode.equals("White")) {
         textColor = Color.WHITE;
      }
      else if (textMode.equals("Black")) {
         // Redundant with the default, but keeps code clear.
         textColor = Color.BLACK;
      }
      else if (textMode.equals("Channel color")) {
         DisplaySettings settings = display.getDisplaySettings();
         Color[] channelColors = settings.getChannelColors();
         int channel = image.getCoords().getChannel();
         if (channelColors != null && channelColors.length >= channel) {
            textColor = channelColors[channel];
         }
      }
      colors.add(textColor);

      String formatMode = (String) format_.getSelectedItem();
      Metadata metadata = image.getMetadata();
      String text;
      if (formatMode.equals(ABSOLUTE_TIME)) {
         text = metadata.getReceivedTime();
         if (text == null) {
            text = "No absolute timestamp";
         }
         else {
            // Strip out timezone. HACK: this format string matches the one in
            // the acquisition engine (mm.clj) that generates the datetime
            // string.
            SimpleDateFormat source = new SimpleDateFormat(
                  "yyyy-MM-dd HH:mm:ss Z");
            SimpleDateFormat dest = new SimpleDateFormat(
                  "yyyy-MM-dd HH:mm:ss");
            try {
               text = dest.format(source.parse(text));
            }
            catch (Exception e) {
               text = "Unable to parse";
            }
         }
      }
      else {
         Double elapsedTime = metadata.getElapsedTimeMs();
         if (elapsedTime != null) {
            int hours = (int) (elapsedTime / 3600000);
            elapsedTime -= hours * 3600000;
            int minutes = (int) (elapsedTime / 60000);
            elapsedTime -= minutes * 60000;
            int seconds = (int) (elapsedTime / 1000);
            elapsedTime -= seconds * 1000;
            int ms = elapsedTime.intValue();
            text = String.format("T+%02d:%02d:%02d.%03d", hours, minutes,
                  seconds, ms);
         }
         else {
            text = "No relative timestamp";
         }
      }
      if (text == null) {
         text = "No timestamp";
      }
      timestamps.add(text);
   }
}
