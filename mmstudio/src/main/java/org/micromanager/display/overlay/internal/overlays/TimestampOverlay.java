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
import java.awt.event.ActionListener;
import java.awt.geom.Rectangle2D;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
import org.micromanager.data.Metadata;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.overlay.AbstractOverlay;
import org.micromanager.internal.utils.DynamicTextField;

/**
 * This overlay draws the timestamps of the currently-displayed images.
 */
public final class TimestampOverlay extends AbstractOverlay {
   private static enum TSPosition {
      // Enum constant names used for persistence; do not change
      NORTHWEST("Upper Left"),
      NORTHEAST("Upper Right"),
      SOUTHWEST("Lower Left"),
      SOUTHEAST("Lower Right"),
      ;

      private final String displayName_;

      private TSPosition(String displayName) {
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

   private static enum TSColor {
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

      private TSColor(String displayName, Color background) {
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

   private static enum TSFormat {
      ABSOLUTE_TIME("Absolute") {
         @Override
         String formatTime(Image image) {
            Metadata metadata = image.getMetadata();
            if (metadata.getReceivedTime() == null) {
               return "ABSOLUTE TIME UNAVAILABLE";
            }
            // Strip out timezone. HACK: this format string matches the one in
            // the acquisition engine (mm.clj) that generates the datetime
            // string.
            SimpleDateFormat source = new SimpleDateFormat(
                  "yyyy-MM-dd HH:mm:ss.SSS Z");
            SimpleDateFormat dest = new SimpleDateFormat(
                  "yyyy-MM-dd HH:mm:ss.SSS");
            try {
               return dest.format(source.parse(metadata.getReceivedTime()));
            }
            catch (ParseException e) {
               return "TIMESTAMP FORMAT ERROR";
            }
         }
      },

      RELATIVE_TIME("Relative to Start") {
         @Override
         String formatTime(Image image) {
            Metadata metadata = image.getMetadata();
            double elapsedMs = metadata.getElapsedTimeMs(-1.0);
            if (elapsedMs < 0.0) {
               return "RELATIVE TIME UNAVAILABLE";
            }
            long totalSeconds = (long) Math.floor(elapsedMs / 1000.0);
            int milliseconds = (int) Math.round(elapsedMs - 1000 * totalSeconds);
            int hours = (int) totalSeconds / 3600;
            totalSeconds -= hours * 3600;
            int minutes = (int) totalSeconds / 60;
            totalSeconds -= minutes * 60;
            int seconds = (int) totalSeconds;
            return String.format("%02d:%02d:%02d.%03d", hours, minutes,
                  seconds, milliseconds);
         }
      },
      ;

      private final String displayName_;

      private TSFormat(String displayName) {
         displayName_ = displayName;
      }

      private String getDisplayName() {
         return displayName_;
      }

      abstract String formatTime(Image image);

      @Override
      public String toString() {
         return getDisplayName();
      }
   }

   private TSFormat format_ = TSFormat.RELATIVE_TIME;
   private boolean perChannel_ = false;
   private TSColor color_ = TSColor.WHITE;
   private boolean addBackground_ = true;
   private TSPosition position_ = TSPosition.NORTHEAST;
   private int xOffset_ = 0;
   private int yOffset_ = 0;


   // Keys for saving settings
   private static enum Key {
      FORMAT,
      PER_CHANNEL,
      COLOR,
      ADD_BACKGROUND,
      POSITION,
      X_OFFSET,
      Y_OFFSET,
   }

   private JPanel configUI_;
   private JComboBox formatComboBox_;
   private JCheckBox perChannelCheckBox_;
   private JComboBox colorComboBox_;
   private JCheckBox addBackgroundCheckBox_;
   private JComboBox positionComboBox_;
   private DynamicTextField xOffsetField_;
   private DynamicTextField yOffsetField_;

   private boolean programmaticallySettingConfiguration_ = false;

   static TimestampOverlay create() {
      return new TimestampOverlay();
   }

   private TimestampOverlay() {
   }

   @Override
   public String getTitle() {
      return "Timestamp";
   }

   @Override
   public void paintOverlay(Graphics2D g, Rectangle screenRect,
         DisplaySettings displaySettings,
         List<Image> images, Image primaryImage,
         Rectangle2D.Float imageViewPort)
   {
      Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
      FontMetrics metrics = g.getFontMetrics(font);

      List<String> texts = new ArrayList<>();
      List<Integer> widths = new ArrayList<>();
      List<Color> foregrounds = new ArrayList<>();
      if (perChannel_) {
         List<Image> sortedImages = new ArrayList<>(images);
         Collections.sort(sortedImages, (Image img1, Image img2) -> {
            Coords c1 = img1.getCoords();
            Coords c2 = img2.getCoords();
            if (!c1.hasAxis(Coords.CHANNEL) || !c2.hasAxis(Coords.CHANNEL)) {
               return 0;
            }
            return new Integer(c1.getChannel()).compareTo(c2.getChannel());
         });
         for (Image image : sortedImages) {
            String text = format_.formatTime(image);
            texts.add(text);
            widths.add(metrics.stringWidth(text));
            foregrounds.add(color_.getForeground(image, displaySettings));
         }
      }
      else {
         String text = format_.formatTime(primaryImage);
         texts.add(text);
         widths.add(metrics.stringWidth(text));
         foregrounds.add(color_.getForeground(primaryImage, displaySettings));
      }

      boolean atBottom, atRight;
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
      final int backgroundHeight = metrics.getAscent() + metrics.getDescent() +
            (texts.size() - 1) * metrics.getHeight()+ 2;
      final int backgroundX = atRight ?
            screenRect.width - xOffset_ - backgroundWidth :
            xOffset_;
      final int backgroundY = atBottom ?
            screenRect.height - yOffset_ - backgroundHeight :
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
      return PropertyMaps.builder().
            putEnumAsString(Key.FORMAT.name(), format_).
            putBoolean(Key.PER_CHANNEL.name(), perChannel_).
            putEnumAsString(Key.COLOR.name(), color_).
            putBoolean(Key.ADD_BACKGROUND.name(), addBackground_).
            putEnumAsString(Key.POSITION.name(), position_).putInteger(Key.X_OFFSET.name(), xOffset_).putInteger(Key.Y_OFFSET.name(), yOffset_).
            build();
   }

   @Override
   public void setConfiguration(PropertyMap config) {
      format_ = config.getStringAsEnum(Key.FORMAT.name(),
            TSFormat.class, format_);
      perChannel_ = config.getBoolean(Key.PER_CHANNEL.name(), perChannel_);
      color_ = config.getStringAsEnum(Key.COLOR.name(),
            TSColor.class, color_);
      addBackground_ = config.getBoolean(Key.ADD_BACKGROUND.name(), addBackground_);
      position_ = config.getStringAsEnum(Key.POSITION.name(),
            TSPosition.class, position_);
      xOffset_ = config.getInteger(Key.X_OFFSET.name(), xOffset_);
      yOffset_ = config.getInteger(Key.Y_OFFSET.name(), yOffset_);

      updateUI();
      fireOverlayConfigurationChanged();
   }

   private void updateUI() {
      if (configUI_ == null) {
         return;
      }

      programmaticallySettingConfiguration_ = true;
      try {
         formatComboBox_.setSelectedItem(format_);
         perChannelCheckBox_.setSelected(perChannel_);
         colorComboBox_.setSelectedItem(color_);
         addBackgroundCheckBox_.setSelected(addBackground_);
         positionComboBox_.setSelectedItem(position_);
         xOffsetField_.setText(String.valueOf(xOffset_));
         yOffsetField_.setText(String.valueOf(yOffset_));
      }
      finally {
         programmaticallySettingConfiguration_ = false;
      }
   }

   private void makeConfigUI() {
      if (configUI_ != null) {
         return;
      }

      formatComboBox_ = new JComboBox(TSFormat.values());
      formatComboBox_.addActionListener((ActionEvent e) -> {
         format_ = (TSFormat) formatComboBox_.getSelectedItem();
         fireOverlayConfigurationChanged();
      });

      colorComboBox_ = new JComboBox(TSColor.values());
      colorComboBox_.addActionListener((ActionEvent e) -> {
         color_ = (TSColor) colorComboBox_.getSelectedItem();
         fireOverlayConfigurationChanged();
      });

      positionComboBox_ = new JComboBox(TSPosition.values());
      positionComboBox_.addActionListener((ActionEvent e) -> {
         position_ = (TSPosition) positionComboBox_.getSelectedItem();
         fireOverlayConfigurationChanged();
      });

      perChannelCheckBox_ = new JCheckBox("Per Channel");
      perChannelCheckBox_.addActionListener((ActionEvent e) -> {
         perChannel_ = perChannelCheckBox_.isSelected();
         fireOverlayConfigurationChanged();
      });

      addBackgroundCheckBox_ = new JCheckBox("Add Background");
      addBackgroundCheckBox_.addActionListener((ActionEvent e) -> {
         addBackground_ = addBackgroundCheckBox_.isSelected();
         fireOverlayConfigurationChanged();
      });

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
         }
         catch (NumberFormatException e) {
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
         }
         catch (NumberFormatException e) {
            if (shouldForceValidation) {
               yOffsetField_.setText(String.valueOf(yOffset_));
            }
         }
      });


      configUI_ = new JPanel(new MigLayout(new LC().insets("4")));

      configUI_.add(new JLabel("Format:"), new CC().split().gapAfter("rel"));
      configUI_.add(formatComboBox_, new CC().gapAfter("48"));
      configUI_.add(perChannelCheckBox_, new CC().wrap());

      configUI_.add(new JLabel("Color:"), new CC().split().gapAfter("rel"));
      configUI_.add(colorComboBox_, new CC().gapAfter("unrel"));
      configUI_.add(addBackgroundCheckBox_, new CC().wrap());

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