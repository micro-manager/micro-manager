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
import org.micromanager.PropertyMap;

import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * This class provides a GUI for drawing a scale bar.
 */
public class ScaleBarOverlayPanel extends OverlayPanel {
   private static final String DRAW_TEXT = "scaleBarOverlay: whether or not to draw text";
   private static final String IS_FILLED = "scaleBarOverlay: if the scale bar is drawn as solid";
   private static final String COLOR = "scaleBarOverlay: color to draw everything";
   private static final String X_OFFSET = "scaleBarOverlay: X offset at which to draw";
   private static final String Y_OFFSET = "scaleBarOverlay: Y offset at which to draw";
   private static final String SIZE = "scaleBarOverlay: size of scalebar in microns";
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

   private final JCheckBox shouldDrawText_;
   private final JCheckBox isBarFilled_;
   private final JComboBox color_;
   private final JTextField xOffset_;
   private final JTextField yOffset_;
   private final JTextField scaleSize_;
   private final JComboBox position_;

   private boolean haveLoggedError_ = false;
   
   public ScaleBarOverlayPanel() {
      ActionListener changeListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
               saveSettings();
               redraw();
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

      add(new JLabel("X offset: "), "split 2, flowx");
      xOffset_ = new JTextField("0", 3);
      xOffset_.addKeyListener(new KeyAdapter() {
         @Override
         public void keyPressed(KeyEvent event) {
            saveSettings();
            redraw();
         }
      });
      add(xOffset_, "wrap");

      isBarFilled_ = new JCheckBox("Solid scale bar");
      isBarFilled_.addActionListener(changeListener);
      add(isBarFilled_);

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
      yOffset_.addKeyListener(new KeyAdapter() {
         @Override
         public void keyPressed(KeyEvent event) {
            saveSettings();
            redraw();
         }
      });
      add(yOffset_);
   }

   /**
    * Record our current settings in the DisplaySettings.
    */
   private void saveSettings() {
      PropertyMap userData = display_.getDisplaySettings().getUserData();
      PropertyMap.PropertyMapBuilder builder;
      if (userData != null) {
         builder = userData.copy();
      }
      else {
         builder = MMStudio.getInstance().data().getPropertyMapBuilder();
      }
      builder.putInt(COLOR, color_.getSelectedIndex());
      builder.putBoolean(DRAW_TEXT, shouldDrawText_.isSelected());
      builder.putString(X_OFFSET, xOffset_.getText());
      builder.putString(Y_OFFSET, yOffset_.getText());
      builder.putBoolean(IS_FILLED, isBarFilled_.isSelected());
      builder.putInt(POSITION, position_.getSelectedIndex());
      builder.putString(SIZE, scaleSize_.getText());
      DisplaySettings newSettings = display_.getDisplaySettings().copy().userData(builder.build()).build();
      display_.setDisplaySettings(newSettings);
   }

   // Update our controls to reflect the settings stored in the DisplaySettings
   // TODO: since we don't save settings *to* the DisplaySettings when the user
   // adjusts them, that means that every time setDisplay() is called, the
   // controls are reset.
   @Override
   public void setDisplay(DisplayWindow display) {
      super.setDisplay(display);
      if (display == null) {
         return;
      }
      PropertyMap userData = display.getDisplaySettings().getUserData();
      if (userData == null) {
         // Start with blank data.
         userData = MMStudio.getInstance().data().getPropertyMapBuilder().build();
      }

      color_.setSelectedIndex(userData.getInt(COLOR, 0));
      shouldDrawText_.setSelected(userData.getBoolean(DRAW_TEXT, true));
      xOffset_.setText(userData.getString(X_OFFSET, "15"));
      yOffset_.setText(userData.getString(Y_OFFSET, "15"));
      isBarFilled_.setSelected(userData.getBoolean(IS_FILLED, true));
      position_.setSelectedIndex(userData.getInt(POSITION, 0));
      scaleSize_.setText(userData.getString(SIZE, "100"));
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
      int xOffset = 0;
      try {
         xOffset = Integer.parseInt(xOffset_.getText());
      }
      catch (NumberFormatException e) {} // Ignore it.
      int yOffset = 0;
      try {
         yOffset = Integer.parseInt(yOffset_.getText());
      }
      catch (NumberFormatException e) {} // Ignore it.

      String position = (String) position_.getSelectedItem();
      Dimension canvasSize = canvas.getPreferredSize();
      if (position.equals("Upper right") || position.equals("Lower right")) {
         xOffset = canvasSize.width - xOffset - 80;
      }
      if (position.equals("Lower left") || position.equals("Lower right")) {
         yOffset = canvasSize.height - yOffset - 13;
      }

      if (shouldDrawText_.isSelected()) {
         g.drawString(String.format("%dum", scaleSize), xOffset, yOffset);
      }
      if (isBarFilled_.isSelected()) {
         g.fillRect(xOffset, yOffset + 6, width, 5);
      }
      else {
         g.drawRect(xOffset, yOffset + 6, width, 5);
      }
   }
}
