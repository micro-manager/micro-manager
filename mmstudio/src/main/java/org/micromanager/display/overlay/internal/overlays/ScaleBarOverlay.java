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

package org.micromanager.display.overlay.internal.overlays;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.geom.Rectangle2D;
import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import net.miginfocom.layout.CC;
import net.miginfocom.layout.LC;
import net.miginfocom.swing.MigLayout;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.data.Image;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.overlay.AbstractOverlay;
import org.micromanager.internal.utils.DynamicTextField;
import org.micromanager.internal.utils.ReportingUtils;


/**
 * The scale bar overlay.
 *
 * @author Chris Weisiger, Nico Stuurman, Mark A. Tsuchida
 */
public final class ScaleBarOverlay extends AbstractOverlay {
   private static enum BarPosition {
      // Enum constant names used for persistence; do not change
      NORTHWEST("Upper Left"),
      NORTHEAST("Upper Right"),
      SOUTHWEST("Lower Left"),
      SOUTHEAST("Lower Right"),
      ;

      private final String displayName_;

      private BarPosition(String displayName) {
         displayName_ = displayName;
      }

      private String getDisplayName() {
         return displayName_;
      }

      @Override
      public String toString() {
         return getDisplayName();
      }
   }

   private static enum BarColor {
      // Enum constant names used for persistence; do not change
      RED("Red", Color.RED),
      MAGENTA("Magenta", Color.MAGENTA),
      YELLOW("Yellow", Color.YELLOW),
      GREEN("Green", Color.GREEN),
      BLUE("Blue", Color.BLUE),
      CYAN("Cyan", Color.CYAN),
      ORANGE("Orange", Color.ORANGE),
      PINK("Pink", Color.PINK),
      WHITE("White", Color.WHITE),
      LIGHT_GRAY("Light Gray", Color.LIGHT_GRAY),
      GRAY("Gray", Color.GRAY),
      DARK_GRAY("Dark Gray", Color.DARK_GRAY),
      BLACK("Black", Color.BLACK),
      ;

      private final String displayName_;
      private final Color color_;

      private BarColor(String displayName, Color color) {
         displayName_ = displayName;
         color_ = color;
      }

      private String getDisplayName() {
         return displayName_;
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
   private BarColor color_ = BarColor.ORANGE;
   private float fontSize_ = 14.0f;
   private int xOffset_ = 10;
   private int yOffset_ = 10;
   private boolean autoLength_ = true;
   private double lengthUm_ = 100.0;
   private int thickness_ = 5;
   private BarPosition position_ = BarPosition.SOUTHEAST;


   // Keys for saving configuration
   private static enum Key {
      DRAW_LABEL,
      FONT_SIZE,
      FILL_BAR,
      COLOR,
      X_OFFSET,
      Y_OFFSET,
      AUTO_LENGTH,
      LENGTH_UM,
      THICKNESS,
      POSITION,
   }

   private JPanel configUI_;
   private JCheckBox drawLabelCheckBox_;
   private JCheckBox fillBarCheckBox_;
   private JComboBox colorComboBox_;
   private DynamicTextField fontSizeField_;
   private DynamicTextField xOffsetField_;
   private DynamicTextField yOffsetField_;
   private JRadioButton autoLengthRadio_;
   private JRadioButton manualLengthRadio_;
   private DynamicTextField lengthUmField_;
   private DynamicTextField thicknessField_;
   private JComboBox positionComboBox_;

   private boolean programmaticallySettingConfiguration_ = false;

   static ScaleBarOverlay create() {
      return new ScaleBarOverlay();
   }

   private ScaleBarOverlay() {

      ReportingUtils.logMessage("Class: " + this.getClass());
      ReportingUtils.logMessage("Classloader: " + this.getClass().getClassLoader());
   }

   @Override
   public String getTitle() {
      return "Scale Bar";
   }

   @Override
   public void paintOverlay(Graphics2D g, Rectangle screenRect,
                            DisplaySettings displaySettings,
                            List<Image> images, Image primaryImage,
                            Rectangle2D.Float imageViewPort) {
      boolean atBottom;
      boolean atRight;
      switch (position_) {
         case NORTHWEST:
            atBottom = atRight = false;
            break;
         case NORTHEAST:
            atBottom = false;
            atRight = true;
            break;
         case SOUTHWEST:
            atBottom = true;
            atRight = false;
            break;
         case SOUTHEAST:
            atBottom = atRight = true;
            break;
         default:
            throw new AssertionError(position_.name());
      }

      g.setColor(color_.getColor());
      g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14).deriveFont(fontSize_));
      FontMetrics metrics = g.getFontMetrics(g.getFont());

      Double umPerImagePixel = primaryImage.getMetadata().getPixelSizeUm();
      if (umPerImagePixel == null || umPerImagePixel <= 0.0) {
         String text = "SCALE UNKNOWN";
         int x = atRight ? screenRect.width - xOffset_ - metrics.stringWidth(text) :
               xOffset_;
         int y = atBottom ? screenRect.height - yOffset_ - metrics.getDescent() :
               yOffset_ + metrics.getAscent();
         g.drawString(text, x, y);
         return;
      }

      final double zoomRatio = imageViewPort.width / screenRect.width;
      final double umPerScreenPixel = zoomRatio * umPerImagePixel;

      final double lengthUm;
      if (autoLength_) {
         double maxWidthUm = 0.25 * (screenRect.width * umPerScreenPixel);
         double minLog = Math.floor(Math.log10(maxWidthUm));
         double minLengthUm = Math.pow(10.0, minLog);
         if (5.0 * minLengthUm <= maxWidthUm) {
            lengthUm = 5.0 * minLengthUm;
         } else if (2.0 * minLengthUm <= maxWidthUm) {
            lengthUm = 2.0 * minLengthUm;
         } else {
            lengthUm = minLengthUm;
         }
      } else {
         lengthUm = lengthUm_;
      }

      final int lengthPx = (int) Math.round(lengthUm / umPerScreenPixel);
      final int x = atRight ? screenRect.width - xOffset_ - lengthPx : xOffset_;
      final int y = atBottom ? screenRect.height - yOffset_ - thickness_ : yOffset_;

      if (drawLabel_) {
         final String labelText;
         if (lengthUm >= 0.9995) {
            labelText = String.format("%d \u00B5m", (int) Math.round(lengthUm)); // micro-m, micron
         } else {
            labelText = String.format("%d nm", (int) Math.round(lengthUm * 1000.0));
         }
         final int labelX = x + (lengthPx - metrics.stringWidth(labelText)) / 2;
         final int labelY = atBottom ? y - metrics.getMaxDescent() - 2 :
               y + thickness_ + metrics.getMaxAscent() + 2;
         g.drawString(labelText, labelX, labelY);
      }
      if (fillBar_) {
         g.fillRect(x, y, lengthPx, thickness_);
      } else {
         g.drawRect(x, y, lengthPx, thickness_);
      }
   }

   @Override
   public JComponent getConfigurationComponent() {
      makeConfigUI();
      updateUI();
      return configUI_;
   }

   @Override
   public PropertyMap getConfiguration() {
      return PropertyMaps.builder()
            .putBoolean(Key.DRAW_LABEL.name(), drawLabel_)
            .putBoolean(Key.FILL_BAR.name(), fillBar_)
            .putEnumAsString(Key.COLOR.name(), color_)
            .putFloat(Key.FONT_SIZE.name(), fontSize_).putInteger(Key.X_OFFSET.name(), xOffset_)
            .putInteger(Key.Y_OFFSET.name(), yOffset_)
            .putBoolean(Key.AUTO_LENGTH.name(), autoLength_)
            .putDouble(Key.LENGTH_UM.name(), lengthUm_)
            .putInteger(Key.THICKNESS.name(), thickness_)
            .putEnumAsString(Key.POSITION.name(), position_)
            .build();
   }

   @Override
   public void setConfiguration(PropertyMap config) {
      drawLabel_ = config.getBoolean(Key.DRAW_LABEL.name(), drawLabel_);
      fillBar_ = config.getBoolean(Key.FILL_BAR.name(), fillBar_);
      color_ = config.getStringAsEnum(Key.COLOR.name(), BarColor.class, color_);
      fontSize_ = config.getFloat(Key.FONT_SIZE.name(), fontSize_);
      xOffset_ = config.getInteger(Key.X_OFFSET.name(), xOffset_);
      yOffset_ = config.getInteger(Key.Y_OFFSET.name(), yOffset_);
      autoLength_ = config.getBoolean(Key.AUTO_LENGTH.name(), autoLength_);
      lengthUm_ = config.getDouble(Key.LENGTH_UM.name(), lengthUm_);
      thickness_ = config.getInteger(Key.THICKNESS.name(), thickness_);
      position_ = config.getStringAsEnum(Key.POSITION.name(),
            BarPosition.class, position_);
      if (autoLength_) {
         drawLabel_ = true;
      }

      updateUI();
      fireOverlayConfigurationChanged();
   }

   private void updateUI() {
      if (configUI_ == null) {
         return;
      }

      programmaticallySettingConfiguration_ = true;
      try {
         drawLabelCheckBox_.setSelected(drawLabel_);
         drawLabelCheckBox_.setEnabled(!autoLength_);
         fillBarCheckBox_.setSelected(fillBar_);
         colorComboBox_.setSelectedItem(color_);
         fontSizeField_.setText(String.valueOf(fontSize_));
         xOffsetField_.setText(String.valueOf(xOffset_));
         yOffsetField_.setText(String.valueOf(yOffset_));
         autoLengthRadio_.setSelected(autoLength_);
         manualLengthRadio_.setSelected(!autoLength_);
         lengthUmField_.setText(String.valueOf(lengthUm_));
         thicknessField_.setText(String.valueOf(thickness_));
         positionComboBox_.setSelectedItem(position_);
      } finally {
         programmaticallySettingConfiguration_ = false;
      }
   }

   private void makeConfigUI() {
      if (configUI_ != null) {
         return;
      }

      colorComboBox_ = new JComboBox(BarColor.values());
      colorComboBox_.setMaximumRowCount(BarColor.values().length);
      colorComboBox_.addActionListener((ActionEvent e) -> {
         color_ = (BarColor) colorComboBox_.getSelectedItem();
         fireOverlayConfigurationChanged();
      });

      positionComboBox_ = new JComboBox(BarPosition.values());
      positionComboBox_.addActionListener((ActionEvent e) -> {
         position_ = (BarPosition) positionComboBox_.getSelectedItem();
         fireOverlayConfigurationChanged();
      });

      autoLengthRadio_ = new JRadioButton("Auto");
      autoLengthRadio_.addActionListener((ActionEvent e) -> {
         if (autoLengthRadio_.isSelected()) {
            autoLength_ = true;
            drawLabel_ = true;
            manualLengthRadio_.setSelected(false);
            drawLabelCheckBox_.setSelected(true);
            drawLabelCheckBox_.setEnabled(false);
            fireOverlayConfigurationChanged();
         }
      });

      manualLengthRadio_ = new JRadioButton("");
      manualLengthRadio_.addActionListener((ActionEvent e) -> {
         if (manualLengthRadio_.isSelected()) {
            autoLength_ = false;
            autoLengthRadio_.setSelected(false);
            drawLabelCheckBox_.setEnabled(true);
            fireOverlayConfigurationChanged();
         }
      });

      fillBarCheckBox_ = new JCheckBox("Solid");
      fillBarCheckBox_.addActionListener((ActionEvent e) -> {
         fillBar_ = fillBarCheckBox_.isSelected();
         fireOverlayConfigurationChanged();
      });

      drawLabelCheckBox_ = new JCheckBox("Show Label");
      drawLabelCheckBox_.addActionListener((ActionEvent e) -> {
         drawLabel_ = drawLabelCheckBox_.isSelected();
         fireOverlayConfigurationChanged();
      });

      lengthUmField_ = new DynamicTextField(4);
      lengthUmField_.setHorizontalAlignment(SwingConstants.RIGHT);
      lengthUmField_.setMinimumSize(lengthUmField_.getPreferredSize());
      lengthUmField_.addDynamicTextFieldListener(
            (DynamicTextField source, boolean shouldForceValidation) -> {
               handleLengthUm(shouldForceValidation);
            });

      thicknessField_ = new DynamicTextField(3);
      thicknessField_.setHorizontalAlignment(SwingConstants.RIGHT);
      thicknessField_.setMinimumSize(thicknessField_.getPreferredSize());
      thicknessField_.addDynamicTextFieldListener(
            (DynamicTextField source, boolean shouldForceValidation) -> {
               handleThickness(shouldForceValidation);
            });

      fontSizeField_ = new DynamicTextField(3);
      fontSizeField_.setHorizontalAlignment(SwingConstants.RIGHT);
      fontSizeField_.setMinimumSize(fontSizeField_.getPreferredSize());
      fontSizeField_.addDynamicTextFieldListener(
            (DynamicTextField source, boolean shouldForceValidation) -> {
               handleFontSize(shouldForceValidation);
            });

      xOffsetField_ = new DynamicTextField(3);
      xOffsetField_.setHorizontalAlignment(SwingConstants.RIGHT);
      xOffsetField_.setMinimumSize(xOffsetField_.getPreferredSize());
      xOffsetField_.addDynamicTextFieldListener(
            (DynamicTextField source, boolean shouldForceValidation) -> {
               handleOffset(SwingConstants.HORIZONTAL, shouldForceValidation);
            });

      yOffsetField_ = new DynamicTextField(3);
      yOffsetField_.setHorizontalAlignment(SwingConstants.RIGHT);
      yOffsetField_.setMinimumSize(yOffsetField_.getPreferredSize());
      yOffsetField_.addDynamicTextFieldListener(
            (DynamicTextField source, boolean shouldForceValidation) -> {
               handleOffset(SwingConstants.VERTICAL, shouldForceValidation);
            });


      configUI_ = new JPanel(new MigLayout(new LC().insets("4")));

      configUI_.add(new JLabel("Length:"), new CC().split().gapAfter("rel"));
      configUI_.add(autoLengthRadio_, new CC().gapAfter("rel"));
      configUI_.add(manualLengthRadio_, new CC().gapAfter("0"));
      configUI_.add(lengthUmField_, new CC().gapAfter("0"));
      configUI_.add(new JLabel("\u00B5m"), new CC().gapAfter("48")); // micro-m, i.e. micron
      configUI_.add(new JLabel("Thickness:"), new CC().gapAfter("rel"));
      configUI_.add(thicknessField_, new CC().gapAfter("0"));
      configUI_.add(new JLabel("px"), new CC().wrap());

      configUI_.add(new JLabel("Color:"), new CC().split().gapAfter("rel"));
      configUI_.add(colorComboBox_, new CC().gapAfter("rel"));
      configUI_.add(fillBarCheckBox_, new CC().gapAfter("indent"));
      configUI_.add(drawLabelCheckBox_, new CC().gapAfter("rel"));
      configUI_.add(new JLabel("(Size:"), new CC().gapAfter("rel"));
      configUI_.add(fontSizeField_, new CC().gapAfter("0"));
      configUI_.add(new JLabel("pt)"), new CC().wrap());

      configUI_.add(new JLabel("Position:"), new CC().split().gapAfter("rel"));
      configUI_.add(positionComboBox_, new CC().gapAfter("unrel"));
      configUI_.add(new JLabel("X Offset:"), new CC().gapAfter("rel"));
      configUI_.add(xOffsetField_, new CC().gapAfter("0"));
      configUI_.add(new JLabel("px"), new CC().gapAfter("unrel"));
      configUI_.add(new JLabel("Y Offset:"), new CC().gapAfter("rel"));
      configUI_.add(yOffsetField_, new CC().gapAfter("0"));
      configUI_.add(new JLabel("px"), new CC().wrap());
   }

   private void handleLengthUm(boolean forceValidation) {
      if (programmaticallySettingConfiguration_) {
         return;
      }
      try {
         lengthUm_ = Double.parseDouble(lengthUmField_.getText());
         autoLength_ = false;
         autoLengthRadio_.setSelected(false);
         manualLengthRadio_.setSelected(true);
         drawLabelCheckBox_.setEnabled(true);
         fireOverlayConfigurationChanged();
      } catch (NumberFormatException e) {
         if (forceValidation) {
            lengthUmField_.setText(String.valueOf(lengthUm_));
         }
      }
   }

   private void handleFontSize(boolean forceValidation) {
      if (programmaticallySettingConfiguration_) {
         return;
      }
      try {
         fontSize_ = Float.parseFloat(fontSizeField_.getText());
         fireOverlayConfigurationChanged();
      } catch (NumberFormatException e) {
         if (forceValidation) {
            fontSizeField_.setText(String.valueOf(fontSize_));
         }
      }
   }

   private void handleThickness(boolean forceValidation) {
      if (programmaticallySettingConfiguration_) {
         return;
      }
      try {
         thickness_ = Integer.parseInt(thicknessField_.getText());
         fireOverlayConfigurationChanged();
      } catch (NumberFormatException e) {
         if (forceValidation) {
            thicknessField_.setText(String.valueOf(thickness_));
         }
      }
   }

   private void handleOffset(int orientation, boolean forceValidation) {
      if (programmaticallySettingConfiguration_) {
         return;
      }
      JTextField offsetField =
            orientation == SwingConstants.HORIZONTAL ? xOffsetField_ : yOffsetField_;
      try {
         int offset = Integer.parseInt(offsetField.getText());
         if (orientation == SwingConstants.HORIZONTAL) {
            xOffset_ = offset;
         } else {
            yOffset_ = offset;
         }
         fireOverlayConfigurationChanged();
      } catch (NumberFormatException e) {
         if (forceValidation) {
            offsetField.setText(String.valueOf(
                  orientation == SwingConstants.HORIZONTAL ? xOffset_ : yOffset_));
         }
      }
   }
}