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

package org.micromanager.display.internal;

import ij.gui.ImageCanvas;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyAdapter;
import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.Graphics;
import java.util.ArrayList;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTextField;

import net.miginfocom.swing.MigLayout;

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
public class TimestampOverlayPanel extends OverlayPanel {
   private static final int LINE_HEIGHT = 13;

   private final JCheckBox amMultiChannel_;
   private final JCheckBox shouldDrawBackground_;
   private final JTextField xOffset_;
   private final JTextField yOffset_;
   private final JComboBox position_;
   private final JComboBox color_;

   public TimestampOverlayPanel() {
      setLayout(new MigLayout("flowx"));

      ActionListener redrawListener = new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent event) {
            redraw();
         }
      };

      add(new JLabel("Position: "), "split 2, flowy");
      position_ = new JComboBox(new String[] {
            "Upper left", "Upper right", "Lower right", "Lower left"});
      position_.addActionListener(redrawListener);
      add(position_, "wrap");

      shouldDrawBackground_ = new JCheckBox("Draw background");
      shouldDrawBackground_.setToolTipText("Draw a background (black, unless black text is selected) underneath the timestamp(s) to improve contrast");
      shouldDrawBackground_.addActionListener(redrawListener);
      add(shouldDrawBackground_, "wrap");

      amMultiChannel_ = new JCheckBox("Per-channel");
      amMultiChannel_.setToolTipText("Draw one timestamp per channel in the display.");
      amMultiChannel_.addActionListener(redrawListener);
      add(amMultiChannel_);

      add(new JLabel("Text color: "), "split 2, flowy");
      color_ = new JComboBox(new String[] {
            "White", "Black", "Channel color"});
      color_.addActionListener(redrawListener);
      add(color_, "wrap");

      add(new JLabel("X offset: "), "split 2");
      xOffset_ = new JTextField("5", 3);
      xOffset_.addKeyListener(new KeyAdapter() {
         @Override
         public void keyPressed(KeyEvent event) {
            redraw();
         }
      });
      add(xOffset_);

      add(new JLabel("Y offset: "), "split 2");
      yOffset_ = new JTextField("15", 3);
      yOffset_.addKeyListener(new KeyAdapter() {
         @Override
         public void keyPressed(KeyEvent event) {
            redraw();
         }
      });
      add(yOffset_, "wrap");
   }

   /**
    * We draw one row of text per channel in multi-channel mode, or just the
    * provided image's timestamp in normal mode.
    */
   @Override
   public void drawOverlay(Graphics g, DisplayWindow display, Image image,
         ImageCanvas canvas) {
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

      // This code is copied from the ScaleBarOverlayPanel, and then
      // slightly adapted to our different Y-positioning needs.
      int xOffset = 0, yOffset = 0;
      int yStep = 1;
      try {
         xOffset = Integer.parseInt(xOffset_.getText());
         yOffset = Integer.parseInt(yOffset_.getText());
      }
      catch (NumberFormatException e) {} // Just use what we have.

      String location = (String) position_.getSelectedItem();
      Dimension canvasSize = canvas.getPreferredSize();
      if (location.equals("Upper right") ||
            location.equals("Lower right")) {
         xOffset = canvasSize.width - xOffset - 80;
      }
      if (location.equals("Lower left") ||
            location.equals("Lower right")) {
         yOffset = canvasSize.height - yOffset - LINE_HEIGHT;
         // Change our step direction.
         yStep = -1;
      }

      if (shouldDrawBackground_.isSelected()) {
         // Determine size and color of background based on our color settings
         // and number/length of timestamps.
         int width = 0;
         int height = LINE_HEIGHT * (timestamps.size() - 1) + 4;
         Color backgroundColor = Color.BLACK;
         for (int i = 0; i < timestamps.size(); ++i) {
            // TODO: find a better way to determine timestamp width than just
            // multiplying number of characters by a constant.
            width = Math.max(width, getStringWidth(timestamps.get(i), g));
            if (colors.get(i) == Color.BLACK) {
               // Can't use black, so use white instead.
               backgroundColor = Color.WHITE;
            }
         }
         g.setColor(backgroundColor);
         g.fillRect(xOffset - 2, yOffset - LINE_HEIGHT,
               xOffset + width + 2, yOffset + height * yStep - 2);
      }

      for (int i = 0; i < timestamps.size(); ++i) {
         g.setColor(colors.get(i));
         g.drawString(timestamps.get(i), xOffset, yOffset + yStep * i * LINE_HEIGHT);
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

      Metadata metadata = image.getMetadata();
      // Try various fallback options for the timestamp.
      String text = metadata.getReceivedTime();
      if (text == null) {
         Double elapsedTime = metadata.getElapsedTimeMs();
         if (elapsedTime != null) {
            text = String.format("T+%.2fms", elapsedTime);
         }
         else {
            text = "No timestamp";
         }
      }
      timestamps.add(text);
   }

   /**
    * Given a string, determine how many pixels wide it is.
    */
   private int getStringWidth(String text, Graphics g) {
      Font font = getFont();
      Rectangle2D textBounds = font.getStringBounds(text,
            new FontRenderContext(new AffineTransform(), true, false));
      return (int) Math.round(textBounds.getWidth());
   }
}
