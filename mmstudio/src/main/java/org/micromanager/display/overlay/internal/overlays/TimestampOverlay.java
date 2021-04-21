///////////////////////////////////////////////////////////////////////////////
// PROJECT:       Micro-Manager
// SUBSYSTEM:     Display implementation
// -----------------------------------------------------------------------------
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
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
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

/** This overlay draws the timestamps of the currently-displayed images. */
public final class TimestampOverlay extends AbstractOverlay {

  private TSFormat format_ = TSFormat.RELATIVE_TIME;
  private boolean perChannel_ = false;
  private final String formatExampleRelative_ = "HH:mm:ss.S";
  private String formatString_ = "mm:ss:SSS";
  private String keyString_ = "m:s:ms";
  private TSColor color_ = TSColor.WHITE;
  private float fontSize_ = 14.0f;
  private boolean addBackground_ = true;
  private TSPosition position_ = TSPosition.NORTHEAST;
  private int xOffset_ = 0;
  private int yOffset_ = 0;

  // GUI elements
  private JPanel configUI_;
  private JComboBox formatComboBox_;
  private JCheckBox perChannelCheckBox_;
  private JLabel formatLabelRelative_;
  private DynamicTextField formatField_;
  private DynamicTextField keyField_;
  private JComboBox colorComboBox_;
  private JCheckBox addBackgroundCheckBox_;
  private DynamicTextField fontSizeField_;
  private JComboBox positionComboBox_;
  private DynamicTextField xOffsetField_;
  private DynamicTextField yOffsetField_;

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
      String formatTime(Image image, String formatString) {
        Metadata metadata = image.getMetadata();
        if (metadata.getReceivedTime() == null) {
          return "ABSOLUTE TIME UNAVAILABLE";
        }
        // Strip out timezone. HACK: this format string matches the one in
        // the acquisition engine (mm.clj) that generates the datetime
        // string.

        SimpleDateFormat source = new SimpleDateFormat(ABSOLUTE_FORMAT_STRING + " Z");
        SimpleDateFormat dest = new SimpleDateFormat(ABSOLUTE_FORMAT_STRING);
        try {
          return dest.format(source.parse(metadata.getReceivedTime()));
        } catch (ParseException e) {
          return "TIMESTAMP FORMAT ERROR";
        }
      }
    },

    RELATIVE_TIME("Relative to Start") {
      @Override
      String formatTime(Image image, String formatString) {
        Metadata metadata = image.getMetadata();
        double elapsedMs = metadata.getElapsedTimeMs(-1.0);
        if (elapsedMs < 0.0) {
          return "RELATIVE TIME UNAVAILABLE";
        }
        Date date = new Date((long) elapsedMs);
        SimpleDateFormat dest = new SimpleDateFormat(formatString);
        return dest.format(date);
      }
    },
    ;

    private final String displayName_;

    private static final String ABSOLUTE_FORMAT_STRING = "yyyy-MM-dd HH:mm:ss.SSS";

    private TSFormat(String displayName) {
      displayName_ = displayName;
    }

    private String getDisplayName() {
      return displayName_;
    }

    abstract String formatTime(Image image, String format);

    @Override
    public String toString() {
      return getDisplayName();
    }
  }

  // Keys for saving settings
  private static enum Key {
    FORMAT,
    FORMATSTRING,
    KEYSTRING,
    PER_CHANNEL,
    COLOR,
    FONTSIZE,
    ADD_BACKGROUND,
    POSITION,
    X_OFFSET,
    Y_OFFSET,
  }

  private boolean programmaticallySettingConfiguration_ = false;

  static TimestampOverlay create() {
    return new TimestampOverlay();
  }

  private TimestampOverlay() {}

  @Override
  public String getTitle() {
    return "Timestamp";
  }

  @Override
  public void paintOverlay(
      Graphics2D g,
      Rectangle screenRect,
      DisplaySettings displaySettings,
      List<Image> images,
      Image primaryImage,
      Rectangle2D.Float imageViewPort) {
    Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 14).deriveFont(fontSize_);
    FontMetrics metrics = g.getFontMetrics(font);

    List<String> texts = new ArrayList<>();
    List<Integer> widths = new ArrayList<>();
    List<Color> foregrounds = new ArrayList<>();
    if (perChannel_) {
      List<Image> sortedImages = new ArrayList<>(images);
      Collections.sort(
          sortedImages,
          (Image img1, Image img2) -> {
            Coords c1 = img1.getCoords();
            Coords c2 = img2.getCoords();
            if (!c1.hasAxis(Coords.CHANNEL) || !c2.hasAxis(Coords.CHANNEL)) {
              return 0;
            }
            return new Integer(c1.getChannel()).compareTo(c2.getChannel());
          });
      for (Image image : sortedImages) {
        StringBuilder sb = new StringBuilder();
        sb.append(format_.formatTime(image, formatString_)).append(keyString_);
        String text = sb.toString();
        texts.add(text);
        widths.add(metrics.stringWidth(text));
        foregrounds.add(color_.getForeground(image, displaySettings));
      }
    } else {
      StringBuilder sb = new StringBuilder();
      sb.append(format_.formatTime(primaryImage, formatString_)).append(keyString_);
      String text = sb.toString();
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
    final int backgroundHeight =
        metrics.getAscent() + metrics.getDescent() + (texts.size() - 1) * metrics.getHeight() + 2;
    final int backgroundX = atRight ? screenRect.width - xOffset_ - backgroundWidth : xOffset_;
    final int backgroundY = atBottom ? screenRect.height - yOffset_ - backgroundHeight : yOffset_;

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
        .putEnumAsString(Key.FORMAT.name(), format_)
        .putBoolean(Key.PER_CHANNEL.name(), perChannel_)
        .putString(Key.FORMATSTRING.name(), formatString_)
        .putString(Key.KEYSTRING.name(), keyString_)
        .putEnumAsString(Key.COLOR.name(), color_)
        .putFloat(Key.FONTSIZE.name(), fontSize_)
        .putBoolean(Key.ADD_BACKGROUND.name(), addBackground_)
        .putEnumAsString(Key.POSITION.name(), position_)
        .putInteger(Key.X_OFFSET.name(), xOffset_)
        .putInteger(Key.Y_OFFSET.name(), yOffset_)
        .build();
  }

  @Override
  public void setConfiguration(PropertyMap config) {
    format_ = config.getStringAsEnum(Key.FORMAT.name(), TSFormat.class, format_);
    perChannel_ = config.getBoolean(Key.PER_CHANNEL.name(), perChannel_);
    formatString_ = config.getString(Key.FORMATSTRING.name(), formatString_);
    keyString_ = config.getString(Key.KEYSTRING.name(), keyString_);
    color_ = config.getStringAsEnum(Key.COLOR.name(), TSColor.class, color_);
    fontSize_ = config.getFloat(Key.FONTSIZE.name(), fontSize_);
    addBackground_ = config.getBoolean(Key.ADD_BACKGROUND.name(), addBackground_);
    position_ = config.getStringAsEnum(Key.POSITION.name(), TSPosition.class, position_);
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

  private String validateFormat(String input, boolean forceValidation) {
    StringBuilder sb = new StringBuilder();
    boolean error = false;
    for (int i = 0; i < input.length(); i++) {
      char c = input.charAt(i);
      if (c == 'h' || c == 'H' || c == 'm' || c == 's' || c == 'S' || c == ':' || c == '-'
          || c == '.' || c == ' ') {
        sb.append(c);
      }
    }
    String result = sb.toString();
    if (error && forceValidation) {
      formatField_.setText(result);
    }
    return result;
  }

  private void updateUI() {
    if (configUI_ == null) {
      return;
    }

    programmaticallySettingConfiguration_ = true;
    try {
      formatComboBox_.setSelectedItem(format_);
      perChannelCheckBox_.setSelected(perChannel_);
      formatField_.setText(formatString_);
      keyField_.setText(keyString_);
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

    formatField_ = new DynamicTextField(12);
    keyField_ = new DynamicTextField(6);
    formatField_.setEnabled(format_.equals(TSFormat.RELATIVE_TIME));
    keyField_.setEnabled(format_.equals(TSFormat.RELATIVE_TIME));

    formatComboBox_ = new JComboBox(TSFormat.values());
    formatComboBox_.addActionListener(
        (ActionEvent e) -> {
          format_ = (TSFormat) formatComboBox_.getSelectedItem();
          formatField_.setEnabled(format_.equals(TSFormat.RELATIVE_TIME));
          keyField_.setEnabled(format_.equals(TSFormat.RELATIVE_TIME));
          fireOverlayConfigurationChanged();
        });

    colorComboBox_ = new JComboBox(TSColor.values());
    colorComboBox_.addActionListener(
        (ActionEvent e) -> {
          color_ = (TSColor) colorComboBox_.getSelectedItem();
          fireOverlayConfigurationChanged();
        });

    formatLabelRelative_ = new JLabel(formatExampleRelative_);

    formatField_.setText(formatString_);
    formatField_.setHorizontalAlignment(SwingConstants.RIGHT);
    formatField_.setMinimumSize(formatField_.getPreferredSize());
    formatField_.addDynamicTextFieldListener(
        (DynamicTextField source, boolean shouldForceValidation) -> {
          if (programmaticallySettingConfiguration_) {
            return;
          }
          formatString_ = validateFormat(formatField_.getText(), shouldForceValidation);
          fireOverlayConfigurationChanged();
        });

    keyField_ = new DynamicTextField(6);
    keyField_.setText(keyString_);
    keyField_.setHorizontalAlignment(SwingConstants.LEFT);
    keyField_.setMinimumSize(keyField_.getPreferredSize());
    keyField_.addDynamicTextFieldListener(
        (DynamicTextField source, boolean shouldForceValidation) -> {
          if (programmaticallySettingConfiguration_) {
            return;
          }
          keyString_ = keyField_.getText();
          fireOverlayConfigurationChanged();
        });

    positionComboBox_ = new JComboBox(TSPosition.values());
    positionComboBox_.addActionListener(
        (ActionEvent e) -> {
          position_ = (TSPosition) positionComboBox_.getSelectedItem();
          fireOverlayConfigurationChanged();
        });

    perChannelCheckBox_ = new JCheckBox("Per Channel");
    perChannelCheckBox_.addActionListener(
        (ActionEvent e) -> {
          perChannel_ = perChannelCheckBox_.isSelected();
          fireOverlayConfigurationChanged();
        });

    addBackgroundCheckBox_ = new JCheckBox("Add Background");
    addBackgroundCheckBox_.addActionListener(
        (ActionEvent e) -> {
          addBackground_ = addBackgroundCheckBox_.isSelected();
          fireOverlayConfigurationChanged();
        });

    fontSizeField_ = new DynamicTextField(3);
    fontSizeField_.setHorizontalAlignment(SwingConstants.RIGHT);
    fontSizeField_.setMinimumSize(fontSizeField_.getPreferredSize());
    fontSizeField_.setText("" + (int) fontSize_);
    fontSizeField_.addDynamicTextFieldListener(
        (DynamicTextField source, boolean shouldForceValidation) -> {
          handleFontSize(shouldForceValidation);
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

    configUI_.add(new JLabel("Format:"), new CC().split().gapAfter("rel"));
    configUI_.add(formatComboBox_, new CC().gapAfter("48"));
    configUI_.add(perChannelCheckBox_, new CC().wrap());

    configUI_.add(new JLabel("Format:"), new CC().split().gapAfter("rel"));
    configUI_.add(formatLabelRelative_, new CC().split().gapAfter("rel"));
    configUI_.add(formatField_, new CC().split().gapAfter("rel"));
    configUI_.add(new JLabel("append:"), new CC().split().gapAfter("rel"));
    configUI_.add(keyField_, new CC().wrap());

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
