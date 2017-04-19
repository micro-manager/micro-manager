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

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Rectangle2D;
import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.miginfocom.swing.MigLayout;
import org.micromanager.data.Image;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.overlay.AbstractOverlay;
import org.micromanager.internal.utils.ReportingUtils;


public final class ScaleBarPanel extends AbstractOverlay {
   private static enum BarPosition {
      NORTHWEST("Upper Left"),
      NORTHEAST("Upper Right"),
      SOUTHEAST("Lower Right"),
      SOUTHWEST("Lower Left");

      private final String name_;

      private BarPosition(String name) {
         name_ = name;
      }

      private String getDisplayName() {
         return name_;
      }

      @Override
      public String toString() {
         return getDisplayName();
      }
   }

   private static enum BarColor {
      BLACK("Black", Color.BLACK),
      DARK_GRAY("Dark Gray", Color.DARK_GRAY),
      GRAY("Gray", Color.GRAY),
      LIGHT_GRAY("Light Gray", Color.LIGHT_GRAY),
      WHITE("White", Color.WHITE),
      YELLOW("Yellow", Color.YELLOW),
      ORANGE("Orange", Color.ORANGE),
      RED("Red", Color.RED),
      PINK("Pink", Color.PINK),
      MAGENTA("Magenta", Color.MAGENTA),
      GREEN("Green", Color.GREEN),
      CYAN("Cyan", Color.CYAN),
      BLUE("Blue", Color.BLUE),
      ;

      private final String name_;
      private final Color color_;

      private BarColor(String name, Color color) {
         name_ = name;
         color_ = color;
      }

      private String getDisplayName() {
         return name_;
      }

      private Color getColor() {
         return color_;
      }

      @Override
      public String toString() {
         return getDisplayName();
      }
   }

   private boolean drawLabel_ = true;
   private boolean fillBar_ = true;
   private BarColor barColor_ = BarColor.YELLOW;
   private float fontSize_ = 14.0f;
   private int xOffset_ = 10;
   private int yOffset_ = 10;
   private double lengthUm_ = 100.0;
   private int thickness_ = 5;
   private BarPosition position_ = BarPosition.NORTHWEST;


   private static final String DRAW_TEXT = "scaleBarOverlay: whether or not to draw text";
   private static final String FONT_SIZE = "scaleBarOverlay: font size";
   private static final String IS_FILLED = "scaleBarOverlay: if the scale bar is drawn as solid";
   private static final String COLOR = "scaleBarOverlay: color to draw everything";
   private static final String X_OFFSET = "scaleBarOverlay: X offset at which to draw";
   private static final String Y_OFFSET = "scaleBarOverlay: Y offset at which to draw";
   private static final String SIZE = "scaleBarOverlay: size of scalebar in microns";
   private static final String BAR_WIDTH = "scaleBarOverlay: width of scalebar";
   private static final String POSITION = "scaleBarOverlay: corner to draw in";

   private JPanel configUI_;
   private JCheckBox drawLabelCheckBox_;
   private JCheckBox fillBarCheckBox_;
   private JComboBox colorComboBox_;
   private JTextField fontSizeField_;
   private JTextField xOffsetField_;
   private JTextField yOffsetField_;
   private JTextField lengthField_;
   private JTextField thicknessField_;
   private JComboBox positionComboBox_;

   private boolean programmaticallySettingConfiguration_ = false;

   static ScaleBarPanel create() {
      return new ScaleBarPanel();
   }

   private ScaleBarPanel() {
   }

   private void createConfigUI() {
      if (configUI_ != null) {
         return;
      }
      configUI_ = new JPanel(new MigLayout("ins 2, flowx"));

      configUI_.add(new JLabel("Color: "), "span 3, split 2");
      colorComboBox_ = new JComboBox(BarColor.values());
      colorComboBox_.setSelectedItem(barColor_);
      colorComboBox_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            barColor_ = (BarColor) colorComboBox_.getSelectedItem();
            fireOverlayNeedsRepaint();
         }
      });
      configUI_.add(colorComboBox_);

      configUI_.add(new JLabel("Position: "), "span 4, split 2");
      positionComboBox_ = new JComboBox(BarPosition.values());
      positionComboBox_.setSelectedItem(position_);
      positionComboBox_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            position_ = (BarPosition) positionComboBox_.getSelectedItem();
            fireOverlayNeedsRepaint();
         }
      });
      configUI_.add(positionComboBox_, "wrap");

      drawLabelCheckBox_ = new JCheckBox("Show Label");
      drawLabelCheckBox_.setSelected(drawLabel_);
      drawLabelCheckBox_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            drawLabel_ = drawLabelCheckBox_.isSelected();
            fireOverlayNeedsRepaint();
         }
      });
      configUI_.add(drawLabelCheckBox_, "span 2");

      fillBarCheckBox_ = new JCheckBox("Solid Scale Bar");
      fillBarCheckBox_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            fillBar_ = fillBarCheckBox_.isSelected();
            fireOverlayNeedsRepaint();
         }
      });
      configUI_.add(fillBarCheckBox_, "span 2");

      configUI_.add(new JLabel("Size (\u00B5m):") );
      lengthField_ = new JTextField(String.valueOf(lengthUm_), 3);
      lengthField_.getDocument().addDocumentListener(new DocumentListener() {
         @Override public void insertUpdate(DocumentEvent e) { updateLength(); }
         @Override public void removeUpdate(DocumentEvent e) { updateLength(); }
         @Override public void changedUpdate(DocumentEvent e) { updateLength(); }
      });
      configUI_.add(lengthField_, "wrap");

      configUI_.add(new JLabel("Font Size: "));
      fontSizeField_ = new JTextField(String.valueOf(fontSize_), 3);
      fontSizeField_.getDocument().addDocumentListener(new DocumentListener() {
         @Override public void insertUpdate(DocumentEvent e) { updateFontSize(); }
         @Override public void removeUpdate(DocumentEvent e) { updateFontSize(); }
         @Override public void changedUpdate(DocumentEvent e) { updateFontSize(); }
      });
      configUI_.add(fontSizeField_);

      configUI_.add(new JLabel("Bar Thickness: "), "gapleft 10");
      thicknessField_ = new JTextField(String.valueOf(thickness_), 3);
      thicknessField_.getDocument().addDocumentListener(new DocumentListener() {
         @Override public void insertUpdate(DocumentEvent e) { updateThickness(); }
         @Override public void removeUpdate(DocumentEvent e) { updateThickness(); }
         @Override public void changedUpdate(DocumentEvent e) { updateThickness(); }
      });
      configUI_.add(thicknessField_);

      configUI_.add(new JLabel("X Offset: "), "gapleft 10");
      xOffsetField_ = new JTextField("0", 3);
      xOffsetField_.getDocument().addDocumentListener(new DocumentListener() {
         @Override public void insertUpdate(DocumentEvent e) { updateOffset(SwingConstants.HORIZONTAL); }
         @Override public void removeUpdate(DocumentEvent e) { updateOffset(SwingConstants.HORIZONTAL); }
         @Override public void changedUpdate(DocumentEvent e) { updateOffset(SwingConstants.HORIZONTAL); }
      });
      configUI_.add(xOffsetField_);

      configUI_.add(new JLabel("Y Offset: "), "gapleft 10");
      yOffsetField_ = new JTextField("0", 3);
      yOffsetField_.getDocument().addDocumentListener(new DocumentListener() {
         @Override public void insertUpdate(DocumentEvent e) { updateOffset(SwingConstants.VERTICAL); }
         @Override public void removeUpdate(DocumentEvent e) { updateOffset(SwingConstants.VERTICAL); }
         @Override public void changedUpdate(DocumentEvent e) { updateOffset(SwingConstants.VERTICAL); }
      });
      configUI_.add(yOffsetField_, "wrap");
   }

   private void updateLength() {
      if (programmaticallySettingConfiguration_) {
         return;
      }
      try {
         lengthUm_ = Double.parseDouble(lengthField_.getText());
         fireOverlayNeedsRepaint();
      }
      catch (NumberFormatException e) {
         lengthField_.setText(String.valueOf(lengthUm_));
      }
   }

   private void updateFontSize() {
      if (programmaticallySettingConfiguration_) {
         return;
      }
      try {
         fontSize_ = Float.parseFloat(fontSizeField_.getText());
         fireOverlayNeedsRepaint();
      }
      catch (NumberFormatException e) {
         fontSizeField_.setText(String.valueOf(fontSize_));
      }
   }

   private void updateThickness() {
      if (programmaticallySettingConfiguration_) {
         return;
      }
      try {
         thickness_ = Integer.parseInt(thicknessField_.getText());
         fireOverlayNeedsRepaint();
      }
      catch (NumberFormatException e) {
         thicknessField_.setText(String.valueOf(thickness_));
      }
   }

   private void updateOffset(int orientation) {
      if (programmaticallySettingConfiguration_) {
         return;
      }
      JTextField offsetField = orientation == SwingConstants.HORIZONTAL ? xOffsetField_ : yOffsetField_;
      try {
         int offset = Integer.parseInt(offsetField.getText());
         if (orientation == SwingConstants.HORIZONTAL) {
            xOffset_ = offset;
         }
         else {
            yOffset_ = offset;
         }
         fireOverlayNeedsRepaint();
      }
      catch (NumberFormatException e) {
         offsetField.setText(String.valueOf(
               orientation == SwingConstants.HORIZONTAL ? xOffset_ : yOffset_));
      }
   }

   /**
    * Record our current settings in the user profile.
    */
   private void saveSettings() {
      studio_.profile().setInt(ScaleBarPanel.class,
            COLOR, colorComboBox_.getSelectedIndex());
      studio_.profile().setBoolean(ScaleBarPanel.class,
            DRAW_TEXT, drawLabelCheckBox_.isSelected());
      studio_.profile().setString(ScaleBarPanel.class,
            FONT_SIZE, fontSizeField_.getText());
      studio_.profile().setString(ScaleBarPanel.class,
            X_OFFSET, xOffsetField_.getText());
      studio_.profile().setString(ScaleBarPanel.class,
            Y_OFFSET, yOffsetField_.getText());
      studio_.profile().setBoolean(ScaleBarPanel.class,
            IS_FILLED, fillBarCheckBox_.isSelected());
      studio_.profile().setString(ScaleBarPanel.class,
            BAR_WIDTH, thicknessField_.getText());
      studio_.profile().setInt(ScaleBarPanel.class,
            POSITION, positionComboBox_.getSelectedIndex());
      studio_.profile().setString(ScaleBarPanel.class,
            SIZE, lengthField_.getText());
   }

   // Update our controls to reflect the settings stored in the profile.
   @Override
   public void setDisplay(DisplayWindow display) {
      super.setDisplay(display);
      if (display == null) {
         return;
      }

      // Don't cause redraws while we're busy resetting our values.
      programmaticallySettingConfiguration_ = true;
      colorComboBox_.setSelectedIndex(studio_.profile().getInt(
               ScaleBarPanel.class, COLOR, 0));
      drawLabelCheckBox_.setSelected(studio_.profile().getBoolean(
               ScaleBarPanel.class, DRAW_TEXT, true));
      fontSizeField_.setText(studio_.profile().getString(
               ScaleBarPanel.class, FONT_SIZE, "14"));
      xOffsetField_.setText(studio_.profile().getString(
               ScaleBarPanel.class, X_OFFSET, "15"));
      yOffsetField_.setText(studio_.profile().getString(
               ScaleBarPanel.class, Y_OFFSET, "15"));
      fillBarCheckBox_.setSelected(studio_.profile().getBoolean(
               ScaleBarPanel.class, IS_FILLED, true));
      thicknessField_.setText(studio_.profile().getString(
               ScaleBarPanel.class, BAR_WIDTH, "5"));
      positionComboBox_.setSelectedIndex(studio_.profile().getInt(
               ScaleBarPanel.class, POSITION, 0));
      lengthField_.setText(studio_.profile().getString(
               ScaleBarPanel.class, SIZE, "100"));
      programmaticallySettingConfiguration_ = false;
      redraw();
   }

   @Override
   public void paintOverlay(Graphics g, Rectangle screenRect,
         DisplaySettings displaySettings,
         List<Image> images, Image primaryImage,
         Rectangle2D.Float imageViewPort)
   {
      Double pixelSize = primaryImage.getMetadata().getPixelSizeUm();
      if (pixelSize == null || pixelSize <= 0.0) {
         return;
      }

      float zoomRatio = imageViewPort.width / screenRect.width;
      int width = (int) Math.round(lengthUm_ / pixelSize * zoomRatio);
      g.setColor(barColor_.getColor());
      g.setFont(new Font("Arial", Font.PLAIN, 14).deriveFont(fontSize_));
      int stringHeight = g.getFontMetrics(g.getFont()).getHeight();

      int xOffset = xOffset_;
      int yOffset = yOffset_ + stringHeight;

      String position = (String) positionComboBox_.getSelectedItem();
      if (position.equals("Upper right") || position.equals("Lower right")) {
         xOffset = screenRect.width - xOffset - width;
      }
      if (position.equals("Lower left") || position.equals("Lower right")) {
         yOffset = screenRect.height - yOffset - thickness_ + stringHeight - 6;
      }

      if (drawLabelCheckBox_.isSelected()) {
         int stringWidth = g.getFontMetrics(g.getFont()).stringWidth(String.format("%d \u00B5m", scaleSize));
         int xPosition = xOffset + (int) (0.5 * width) - (int) (0.5 * stringWidth);
         g.drawString(String.format("%d \u00B5m", scaleSize), xPosition, yOffset);
      }
      if (fillBarCheckBox_.isSelected()) {
         g.fillRect(xOffset, yOffset + 6, width, thickness_);
      }
      else {
         g.drawRect(xOffset, yOffset + 6, width, thickness_);
      }
   }
}