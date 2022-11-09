///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Display implementation
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman 2022, based on code by Chris Weisiger, 2015
//
// COPYRIGHT:    Altos Labs, 2022
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.miginfocom.layout.CC;
import net.miginfocom.layout.LC;
import net.miginfocom.swing.MigLayout;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.data.Coords;
import org.micromanager.data.Image;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.overlay.AbstractOverlay;
import org.micromanager.internal.utils.DynamicTextField;


/**
 * This overlay draws the timestamps of the currently-displayed images.
 */
public final class TextOverlay extends AbstractOverlay {

   private boolean useChannelName_ = false;
   private String text_ = "";
   private TextColor color_ = TextColor.WHITE;
   private float fontSize_ = 14.0f;
   private boolean addBackground_ = true;
   private TextPosition position_ = TextPosition.NORTHEAST;
   private int xOffset_ = 0;
   private int yOffset_ = 0;

   // GUI elements
   private JPanel configUI_;
   private JCheckBox useChannelNameCheckBox_;
   private DynamicTextField textField_;
   private JComboBox<TextColor> colorComboBox_;
   private JCheckBox addBackgroundCheckBox_;
   private DynamicTextField fontSizeField_;
   private JComboBox<TextPosition> positionComboBox_;
   private DynamicTextField xOffsetField_;
   private DynamicTextField yOffsetField_;


   private enum TextPosition {
      // Enum constant names used for persistence; do not change
      NORTHWEST("Upper Left"),
      NORTHEAST("Upper Right"),
      SOUTHWEST("Lower Left"),
      SOUTHEAST("Lower Right"),
      ;

      private final String displayName_;

      TextPosition(String displayName) {
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

   private enum TextColor {
      // Enum constant names used for persistence; do not change
      WHITE("White", Color.getHSBColor(0.0f, 0.0f, 0.1f)) {
         @Override
         Color getForeground(Image image, DisplaySettings settings) {
            return Color.WHITE;
         }
      },

      BLACK("Black", Color.getHSBColor(0.0f, 0.0f, 0.9f)) {
         @Override
         Color getForeground(Image image, DisplaySettings settings) {
            return Color.BLACK;
         }
      },

      CHANNEL("Channel Color", Color.getHSBColor(0.0f, 0.0f, 0.1f)) {
         @Override
         Color getForeground(Image image, DisplaySettings settings) {
            Coords c = image.getCoords();
            if (!c.hasAxis(Coords.CHANNEL)) {
               return Color.GRAY;
            }
            return settings.getChannelColor(c.getChannel());
         }
      },
      ;

      private final String displayName_;
      private final Color background_;

      TextColor(String displayName, Color background) {
         displayName_ = displayName;
         background_ = background;
      }

      private String getDisplayName() {
         return displayName_;
      }

      abstract Color getForeground(Image image, DisplaySettings settings);

      private Color getBackground() {
         return background_;
      }

      @Override
      public String toString() {
         return getDisplayName();
      }
   }


   // Keys for saving settings
   private enum Key {
      TEXT,
      USE_CHANNEL_NAME,
      COLOR,
      FONT_SIZE,
      ADD_BACKGROUND,
      POSITION,
      X_OFFSET,
      Y_OFFSET,
   }


   private boolean programmaticallySettingConfiguration_ = false;

   static TextOverlay create() {
      return new TextOverlay();
   }

   private TextOverlay() {
   }

   @Override
   public String getTitle() {
      return "Text";
   }

   @Override
   public void paintOverlay(Graphics2D g, Rectangle screenRect,
                            DisplaySettings displaySettings,
                            List<Image> images, Image primaryImage,
                            Rectangle2D.Float imageViewPort) {
      Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 14).deriveFont(fontSize_);
      FontMetrics metrics = g.getFontMetrics(font);

      List<String> texts = new ArrayList<>();
      List<Integer> widths = new ArrayList<>();
      List<Color> foregrounds = new ArrayList<>();
      if (useChannelName_) {
         List<Image> sortedImages = new ArrayList<>(images);
         Collections.sort(sortedImages, (Image img1, Image img2) -> {
            Coords c1 = img1.getCoords();
            Coords c2 = img2.getCoords();
            if (!c1.hasAxis(Coords.CHANNEL) || !c2.hasAxis(Coords.CHANNEL)) {
               return 0;
            }
            return Integer.compare(c1.getChannel(), c2.getChannel());
         });
         if (primaryImage != null && displaySettings.getColorMode()
               != DisplaySettings.ColorMode.COMPOSITE) {
            StringBuilder sb = new StringBuilder();
            int chNr = primaryImage.getCoords().getChannel();
            sb.append(displaySettings.getChannelSettings(chNr).getName()).append(" ");
            String text = sb.toString();
            texts.add(text);
            widths.add(metrics.stringWidth(text));
            foregrounds.add(color_.getForeground(primaryImage, displaySettings));
         } else {
            for (Image image : sortedImages) {
               StringBuilder sb = new StringBuilder();
               int chNr = image.getCoords().getChannel();
               sb.append(displaySettings.getChannelSettings(chNr).getName()).append(" ");
               String text = sb.toString();
               texts.add(text);
               widths.add(metrics.stringWidth(text));
               foregrounds.add(color_.getForeground(image, displaySettings));
            }
         }
      } else {
         texts.add(text_);
         widths.add(metrics.stringWidth(text_));
         foregrounds.add(color_.getForeground(primaryImage, displaySettings));
      }

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

      final int backgroundWidth = Collections.max(widths) + 2;
      final int backgroundHeight = metrics.getAscent() + metrics.getDescent()
            + (texts.size() - 1) * metrics.getHeight() + 2;
      final int backgroundX = atRight
            ? screenRect.width - xOffset_ - backgroundWidth :
            xOffset_;
      final int backgroundY = atBottom
            ? screenRect.height - yOffset_ - backgroundHeight :
            yOffset_;

      if (addBackground_) {
         g.setColor(color_.getBackground());
         g.fillRect(backgroundX, backgroundY, backgroundWidth, backgroundHeight);
      }

      g.setFont(font);
      final int textX = backgroundX + 1;
      final int textY0 = backgroundY + metrics.getAscent() + 1;
      final int textDeltaY = metrics.getHeight();
      for (int i = 0; i < texts.size(); ++i) {
         g.setColor(foregrounds.get(i));
         g.drawString(texts.get(i), textX, textY0 + i * textDeltaY);
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
            .putBoolean(Key.USE_CHANNEL_NAME.name(), useChannelName_)
            .putString(Key.TEXT.name(), text_)
            .putEnumAsString(Key.COLOR.name(), color_)
            .putFloat(Key.FONT_SIZE.name(), fontSize_)
            .putBoolean(Key.ADD_BACKGROUND.name(), addBackground_)
            .putEnumAsString(Key.POSITION.name(), position_)
            .putInteger(Key.X_OFFSET.name(), xOffset_)
            .putInteger(Key.Y_OFFSET.name(), yOffset_)
            .build();
   }

   @Override
   public void setConfiguration(PropertyMap config) {
      useChannelName_ = config.getBoolean(Key.USE_CHANNEL_NAME.name(), useChannelName_);
      text_ = config.getString(Key.TEXT.name(), text_);
      color_ = config.getStringAsEnum(Key.COLOR.name(),
            TextColor.class, color_);
      fontSize_ = config.getFloat(Key.FONT_SIZE.name(), fontSize_);
      addBackground_ = config.getBoolean(Key.ADD_BACKGROUND.name(), addBackground_);
      position_ = config.getStringAsEnum(Key.POSITION.name(),
            TextPosition.class, position_);
      xOffset_ = config.getInteger(Key.X_OFFSET.name(), xOffset_);
      yOffset_ = config.getInteger(Key.Y_OFFSET.name(), yOffset_);

      updateUI();
      fireOverlayConfigurationChanged();
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

   private void updateUI() {
      if (configUI_ == null) {
         return;
      }

      programmaticallySettingConfiguration_ = true;
      try {
         useChannelNameCheckBox_.setSelected(useChannelName_);
         textField_.setText(text_);
         colorComboBox_.setSelectedItem(color_);
         addBackgroundCheckBox_.setSelected(addBackground_);
         positionComboBox_.setSelectedItem(position_);
         xOffsetField_.setText(String.valueOf(xOffset_));
         yOffsetField_.setText(String.valueOf(yOffset_));
      } finally {
         programmaticallySettingConfiguration_ = false;
      }
   }

   private void makeConfigUI() {
      if (configUI_ != null) {
         return;
      }

      colorComboBox_ = new JComboBox<>(TextColor.values());
      colorComboBox_.addActionListener((ActionEvent e) -> {
         color_ = (TextColor) colorComboBox_.getSelectedItem();
         fireOverlayConfigurationChanged();
      });

      textField_ = new DynamicTextField(6);
      textField_.setText(text_);
      textField_.setHorizontalAlignment(SwingConstants.LEFT);
      textField_.setMinimumSize(textField_.getPreferredSize());
      textField_.addDynamicTextFieldListener(
            (DynamicTextField source, boolean shouldForceValidation) -> {
               if (programmaticallySettingConfiguration_) {
                  return;
               }
               text_ = textField_.getText();
               fireOverlayConfigurationChanged();
            });

      positionComboBox_ = new JComboBox<>(TextPosition.values());
      positionComboBox_.addActionListener((ActionEvent e) -> {
         position_ = (TextPosition) positionComboBox_.getSelectedItem();
         fireOverlayConfigurationChanged();
      });

      useChannelNameCheckBox_ = new JCheckBox("Use ChannelName");
      useChannelNameCheckBox_.addActionListener((ActionEvent e) -> {
         useChannelName_ = useChannelNameCheckBox_.isSelected();
         fireOverlayConfigurationChanged();
      });

      addBackgroundCheckBox_ = new JCheckBox("Add Background");
      addBackgroundCheckBox_.addActionListener((ActionEvent e) -> {
         addBackground_ = addBackgroundCheckBox_.isSelected();
         fireOverlayConfigurationChanged();
      });

      fontSizeField_ = new DynamicTextField(3);
      fontSizeField_.setHorizontalAlignment(SwingConstants.RIGHT);
      fontSizeField_.setMinimumSize(fontSizeField_.getPreferredSize());
      fontSizeField_.setText("" + (int) fontSize_);
      fontSizeField_.addDynamicTextFieldListener(
            (DynamicTextField source, boolean shouldForceValidation)
                  -> handleFontSize(shouldForceValidation));

      xOffsetField_ = new DynamicTextField(3);
      xOffsetField_.setHorizontalAlignment(SwingConstants.RIGHT);
      xOffsetField_.setMinimumSize(xOffsetField_.getPreferredSize());
      xOffsetField_.addDynamicTextFieldListener(
            (DynamicTextField source, boolean shouldForceValidation) -> {
               if (programmaticallySettingConfiguration_) {
                  return;
               }
               try {
                  xOffset_ = Integer.parseInt(xOffsetField_.getText());
                  fireOverlayConfigurationChanged();
               } catch (NumberFormatException e) {
                  if (shouldForceValidation) {
                     xOffsetField_.setText(String.valueOf(xOffset_));
                  }
               }
            });

      yOffsetField_ = new DynamicTextField(3);
      yOffsetField_.setHorizontalAlignment(SwingConstants.RIGHT);
      yOffsetField_.setMinimumSize(yOffsetField_.getPreferredSize());
      yOffsetField_.addDynamicTextFieldListener(
            (DynamicTextField source, boolean shouldForceValidation) -> {
               if (programmaticallySettingConfiguration_) {
                  return;
               }
               try {
                  yOffset_ = Integer.parseInt(yOffsetField_.getText());
                  fireOverlayConfigurationChanged();
               } catch (NumberFormatException e) {
                  if (shouldForceValidation) {
                     yOffsetField_.setText(String.valueOf(yOffset_));
                  }
               }
            });


      configUI_ = new JPanel(new MigLayout(new LC().insets("4")));

      configUI_.add(new JLabel("Text:"), new CC().split().gapAfter("rel"));
      configUI_.add(textField_, new CC().gapAfter("48"));
      configUI_.add(useChannelNameCheckBox_, new CC().wrap());

      configUI_.add(new JLabel("Color:"), new CC().split().gapAfter("rel"));
      configUI_.add(colorComboBox_, new CC().gapAfter("unrel"));
      configUI_.add(addBackgroundCheckBox_, new CC());
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
}
