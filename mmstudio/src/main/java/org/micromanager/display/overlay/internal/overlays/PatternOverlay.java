/*
 * Copyright (C) 2014 Applied Scientific Instrumentation
 * Copyright (C) 2014-2017 Regents of the University of California
 * Copyright (C) 2015-2017 Open Imaging, Inc.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its
 * contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package org.micromanager.display.overlay.internal.overlays;

import net.miginfocom.layout.CC;
import net.miginfocom.layout.LC;
import net.miginfocom.swing.MigLayout;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.data.Image;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.overlay.AbstractOverlay;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.text.NumberFormat;
import java.util.List;

/**
 * The pattern overlay.
 *
 * @author John Daniels, Chris Weisiger, Mark A. Tsuchida
 */
public class PatternOverlay extends AbstractOverlay {
  private static enum PatternType {
    // Enum constant names used for persistence; do not change
    CROSSHAIR_PLUS("Crosshair (+)") {
      @Override
      void draw(Graphics2D g, int patternSize, float width, float height) {
        float centerX = 0.5f * width;
        float centerY = 0.5f * height;
        float halfSize = 0.5f * (patternSize / 100.0f) * Math.min(width, height);
        g.draw(new Line2D.Float(centerX, centerY - halfSize, centerX, centerY + halfSize));
        g.draw(new Line2D.Float(centerX - halfSize, centerY, centerX + halfSize, centerY));
      }

      @Override
      String getSizeString(int patternSize, double umPerImagePixel, float width, float height) {
        float sizeImgPx = (patternSize / 100.0f) * Math.min(width, height);
        if (Double.isNaN(umPerImagePixel)) {
          return String.format("Crosshair Size: %d px", (int) Math.round(sizeImgPx));
        } else {
          return String.format("Crosshair Size: %.1f \u00B5m", sizeImgPx * umPerImagePixel);
        }
      }
    },

    CROSSHAIR_X("Crosshair (X)") {
      @Override
      void draw(Graphics2D g, int patternSize, float width, float height) {
        float s = 0.5f * patternSize / 100.0f;
        g.draw(
            new Line2D.Float(
                (0.5f - s) * width, (0.5f - s) * height, (0.5f + s) * width, (0.5f + s) * height));
        g.draw(
            new Line2D.Float(
                (0.5f - s) * width, (0.5f + s) * height, (0.5f + s) * width, (0.5f - s) * height));
      }

      @Override
      String getSizeString(int patternSize, double umPerImagePixel, float width, float height) {
        float hSizeImgPx = patternSize * width / 100.0f;
        float vSizeImgPx = patternSize * height / 100.0f;
        if (Double.isNaN(umPerImagePixel)) {
          return String.format(
              "Crosshair Size: %d x %d px",
              (int) Math.round(hSizeImgPx), (int) Math.round(vSizeImgPx));
        } else {
          return String.format(
              "Crosshair Size: %.1f x %.1f \u00B5m",
              hSizeImgPx * umPerImagePixel, vSizeImgPx * umPerImagePixel);
        }
      }
    },

    RECTANGULAR_GRID("Rectangular Grid") {
      @Override
      void draw(Graphics2D g, int patternSize, float width, float height) {
        int nDivs = 2 + 9 * patternSize / 101;
        float hPixPerDiv = (float) width / nDivs;
        float vPixPerDiv = (float) height / nDivs;
        for (int i = 0; i < nDivs; ++i) {
          g.draw(new Line2D.Float(0.0f, i * vPixPerDiv, width, i * vPixPerDiv));
          g.draw(new Line2D.Float(i * hPixPerDiv, 0.0f, i * hPixPerDiv, height));
        }
      }

      @Override
      String getSizeString(int patternSize, double umPerImagePixel, float width, float height) {
        int nDivs = 2 + 9 * patternSize / 101;
        float hPixPerDiv = (float) width / nDivs;
        float vPixPerDiv = (float) height / nDivs;
        if (Double.isNaN(umPerImagePixel)) {
          return String.format(
              "Grid Cell Size: %d x %d px",
              (int) Math.round(hPixPerDiv), (int) Math.round(vPixPerDiv));
        } else {
          return String.format(
              "Grid Cell Size: %.1f x %.1f \u00B5m",
              hPixPerDiv * umPerImagePixel, vPixPerDiv * umPerImagePixel);
        }
      }
    },

    SQUARE_GRID("Square Grid") {
      @Override
      void draw(Graphics2D g, int patternSize, float width, float height) {
        int nominalDivs = 2 + 9 * patternSize / 101;
        float pixPerDiv = (float) Math.min(width, height) / nominalDivs;
        int nDivs = 1 + (int) Math.ceil(Math.max(width, height) / pixPerDiv);
        // The center lines are drawn twice, which is okay.
        for (int i = 0; i < nDivs / 2; ++i) {
          g.draw(
              new Line2D.Float(
                  0.0f, 0.5f * height + i * pixPerDiv, width, 0.5f * height + i * pixPerDiv));
          g.draw(
              new Line2D.Float(
                  0.0f, 0.5f * height - i * pixPerDiv, width, 0.5f * height - i * pixPerDiv));
          g.draw(
              new Line2D.Float(
                  0.5f * width + i * pixPerDiv, 0.0f, 0.5f * width + i * pixPerDiv, height));
          g.draw(
              new Line2D.Float(
                  0.5f * width - i * pixPerDiv, 0.0f, 0.5f * width - i * pixPerDiv, height));
        }
      }

      @Override
      String getSizeString(int patternSize, double umPerImagePixel, float width, float height) {
        int nominalDivs = 2 + 9 * patternSize / 101;
        float pixPerDiv = (float) Math.min(width, height) / nominalDivs;
        if (Double.isNaN(umPerImagePixel)) {
          return String.format("Grid Cell Size: %d px", (int) Math.round(pixPerDiv));
        } else {
          return String.format("Grid Cell Size: %.1f \u00B5m", pixPerDiv * umPerImagePixel);
        }
      }
    },

    CIRCLE("Circle") {
      @Override
      void draw(Graphics2D g, int patternSize, float width, float height) {
        float r = 0.5f * patternSize * Math.max(width, height) / 100.0f * (float) Math.sqrt(2);
        g.draw(new Ellipse2D.Float(0.5f * width - r, 0.5f * height - r, 2.0f * r, 2.0f * r));
      }

      @Override
      String getSizeString(int patternSize, double umPerImagePixel, float width, float height) {
        float dImgPx = patternSize * Math.max(width, height) / 100.0f * (float) Math.sqrt(2);
        if (Double.isNaN(umPerImagePixel)) {
          return String.format("Circle Diameter: %d px", (int) Math.round(dImgPx));
        } else {
          return String.format("Circle Diameter: %.1f \u00B5m", dImgPx * umPerImagePixel);
        }
      }
    },

    TARGET("Target") {
      private static final int N = 3;

      @Override
      void draw(Graphics2D g, int patternSize, float width, float height) {
        float r0 = 0.5f * patternSize * Math.min(width, height) / 100.0f;
        for (int i = 1; i <= N; ++i) {
          float r = r0 * (i / (float) N);
          g.draw(new Ellipse2D.Float(0.5f * width - r, 0.5f * height - r, 2.0f * r, 2.0f * r));
        }
      }

      @Override
      String getSizeString(int patternSize, double umPerImagePixel, float width, float height) {
        float d0ImgPx = patternSize * Math.min(width, height) / 100.0f;
        StringBuilder sb = new StringBuilder("Circle Diameters: ");
        for (int i = 1; i <= N; ++i) {
          float dImgPx = d0ImgPx * (i / (float) N);
          if (Double.isNaN(umPerImagePixel)) {
            sb.append(String.format("%d", (int) Math.round(dImgPx)));
          } else {
            sb.append(String.format("%.1f", dImgPx * umPerImagePixel));
          }
          if (i < N) {
            sb.append(", ");
          }
        }
        if (Double.isNaN(umPerImagePixel)) {
          sb.append(" px");
        } else {
          sb.append(" \u00B5m");
        }
        return sb.toString();
      }
    },
    ;

    private final String displayName_;

    private PatternType(String displayName) {
      displayName_ = displayName;
    }

    private String getDisplayName() {
      return displayName_;
    }

    abstract void draw(Graphics2D g, int patternSize, float width, float height);

    abstract String getSizeString(
        int patternSize, double umPerImagePixel, float width, float height);

    @Override
    public String toString() {
      return getDisplayName();
    }
  }

  private static enum PatternColor {
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

    private PatternColor(String displayName, Color color) {
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

  private PatternType patternType_ = PatternType.CROSSHAIR_PLUS;
  private int patternSize_ = 50;
  private PatternColor color_ = PatternColor.RED;
  private boolean showSize_ = true;

  // Keys for saving configuration
  private static enum Key {
    PATTERN_TYPE,
    PATTERN_SIZE,
    COLOR,
    SHOW_SIZE,
  }

  private JPanel configUI_;
  private JComboBox patternTypeComboBox_;
  private JSlider patternSizeSlider_;
  private JFormattedTextField patternSizeTextBox_;
  private JLabel patternSizeLabel_;
  private JComboBox colorComboBox_;
  private JCheckBox showSizeCheckBox_;

  private boolean programmaticallySettingConfiguration_ = false;

  public static PatternOverlay create() {
    return new PatternOverlay();
  }

  private PatternOverlay() {}

  @Override
  public String getTitle() {
    return "Guide Pattern";
  }

  @Override
  public void paintOverlay(
      Graphics2D g,
      Rectangle screenRect,
      DisplaySettings displaySettings,
      List<Image> images,
      Image primaryImage,
      Rectangle2D.Float imageViewPort) {
    g.setColor(color_.getColor());
    Double pixelSize = primaryImage.getMetadata().getPixelSizeUm();
    double umPerImagePixel = pixelSize != null ? pixelSize : Double.NaN;
    final double zoomRatio = imageViewPort.width / screenRect.width;

    // Draw the pattern in image pixel coordinates by applying a transform
    Graphics2D gTfm = (Graphics2D) g.create();
    gTfm.transform(AffineTransform.getScaleInstance(1.0 / zoomRatio, 1.0 / zoomRatio));
    gTfm.transform(AffineTransform.getTranslateInstance(-imageViewPort.x, -imageViewPort.y));
    // Stroke width should be 1.0 in screen coordinates
    gTfm.setStroke(new BasicStroke((float) zoomRatio));
    patternType_.draw(gTfm, patternSize_, primaryImage.getWidth(), primaryImage.getHeight());

    if (showSize_) {
      Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
      FontMetrics metrics = g.getFontMetrics(font);
      String sizeString =
          patternType_.getSizeString(
              patternSize_, umPerImagePixel, primaryImage.getWidth(), primaryImage.getHeight());
      g.drawString(sizeString, 4, screenRect.height - 4 - metrics.getDescent());
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
        .putEnumAsString(Key.PATTERN_TYPE.name(), patternType_)
        .putInteger(Key.PATTERN_SIZE.name(), patternSize_)
        .putEnumAsString(Key.COLOR.name(), color_)
        .putBoolean(Key.SHOW_SIZE.name(), showSize_)
        .build();
  }

  @Override
  public void setConfiguration(PropertyMap config) {
    patternType_ = config.getStringAsEnum(Key.PATTERN_TYPE.name(), PatternType.class, patternType_);
    patternSize_ = config.getInteger(Key.PATTERN_SIZE.name(), patternSize_);
    color_ = config.getStringAsEnum(Key.COLOR.name(), PatternColor.class, color_);
    showSize_ = config.getBoolean(Key.SHOW_SIZE.name(), showSize_);

    updateUI();
    fireOverlayConfigurationChanged();
  }

  private void updateUI() {
    if (configUI_ == null) {
      return;
    }

    programmaticallySettingConfiguration_ = true;
    try {
      patternTypeComboBox_.setSelectedItem(patternType_);
      patternSizeSlider_.setValue(patternSize_);
      patternSizeTextBox_.setValue(patternSize_);
      colorComboBox_.setSelectedItem(color_);
      showSizeCheckBox_.setSelected(showSize_);
    } finally {
      programmaticallySettingConfiguration_ = false;
    }
  }

  private void makeConfigUI() {
    if (configUI_ != null) {
      return;
    }

    patternTypeComboBox_ = new JComboBox(PatternType.values());
    patternTypeComboBox_.addActionListener(
        (ActionEvent e) -> {
          patternType_ = (PatternType) patternTypeComboBox_.getSelectedItem();
          fireOverlayConfigurationChanged();
        });

    colorComboBox_ = new JComboBox(PatternColor.values());
    colorComboBox_.setMaximumRowCount(PatternColor.values().length);
    colorComboBox_.addActionListener(
        (ActionEvent e) -> {
          color_ = (PatternColor) colorComboBox_.getSelectedItem();
          fireOverlayConfigurationChanged();
        });

    patternSizeSlider_ = new JSlider(0, 100);
    patternSizeSlider_.addChangeListener(
        (ChangeEvent e) -> {
          if (programmaticallySettingConfiguration_) {
            return;
          }
          patternSize_ = patternSizeSlider_.getValue();
          patternSizeTextBox_.setValue(patternSize_);
          fireOverlayConfigurationChanged();
        });

    NumberFormat patternSizeTextFormat = NumberFormat.getInstance();
    patternSizeTextBox_ = new JFormattedTextField(patternSizeTextFormat);
    patternSizeTextBox_.setColumns(2);
    patternSizeLabel_ = new JLabel("%");
    patternSizeTextBox_.addActionListener(
        (ActionEvent e) -> {
          patternSize_ = ((Long) patternSizeTextBox_.getValue()).intValue();
          patternSizeSlider_.setValue(patternSize_);
        });

    showSizeCheckBox_ = new JCheckBox("Display Pattern Size");
    showSizeCheckBox_.addActionListener(
        (ActionEvent e) -> {
          showSize_ = showSizeCheckBox_.isSelected();
          fireOverlayConfigurationChanged();
        });

    configUI_ = new JPanel(new MigLayout(new LC().insets("4")));

    configUI_.add(new JLabel("Pattern Type:"), new CC().split().gapAfter("rel"));
    configUI_.add(patternTypeComboBox_, new CC().gapAfter("unrel"));
    configUI_.add(new JLabel("Color:"), new CC().gapAfter("rel"));
    configUI_.add(colorComboBox_, new CC().wrap());

    configUI_.add(new JLabel("Pattern Size:"), new CC().split().gapAfter("rel"));
    configUI_.add(patternSizeSlider_, new CC().gapAfter("unrel"));
    configUI_.add(patternSizeTextBox_, new CC().gapAfter("rel"));
    configUI_.add(patternSizeLabel_, new CC().wrap());
    configUI_.add(showSizeCheckBox_, new CC().wrap());
  }
}
