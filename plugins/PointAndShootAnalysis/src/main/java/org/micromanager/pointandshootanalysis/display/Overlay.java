///////////////////////////////////////////////////////////////////////////////
// FILE:          Overlay.java
// PROJECT:       Micro-Manager
// SUBSYSTEM:     PointAndShootAnalyzer plugin
// -----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman
//
// COPYRIGHT:    University of California, San Francisco 2019
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

package org.micromanager.pointandshootanalysis.display;

import georegression.struct.point.Point2D_I32;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import net.miginfocom.layout.CC;
import net.miginfocom.layout.LC;
import net.miginfocom.swing.MigLayout;
import org.micromanager.data.Image;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.overlay.AbstractOverlay;
import org.micromanager.pointandshootanalysis.data.ParticleData;

/** @author nico */
public class Overlay extends AbstractOverlay {
  private final String TITLE = "Point and Shoot Overlay";
  private final List<Map<Integer, ParticleData>> controlTracks_;
  private final Map<Integer, List<ParticleData>> tracksIndexedByFrame_;
  private final Map<Integer, List<ParticleData>> controlTracksIndexedByFrame_;
  private final int symbolLenght_ = 30;
  private final Color maskColor_ = new Color(255, 125, 10);
  private final Color controlMaskColor_ = new Color(125, 255, 10);
  private final Color bleachColor_ = new Color(255, 5, 25);

  // UI components
  private JPanel configUI_;
  private JCheckBox showMasksCheckBox_;
  private JCheckBox showBleachMasksCheckBox_;
  private JCheckBox showControlMasksCheckBox_;

  public Overlay(
      Map<Integer, List<ParticleData>> tracksIndexedByFrame,
      List<Map<Integer, ParticleData>> controlTracks) {
    // index tracks by frame for quick look up when we need it
    tracksIndexedByFrame_ = tracksIndexedByFrame;

    controlTracks_ = controlTracks;
    controlTracksIndexedByFrame_ = new TreeMap<>();
    controlTracks_.forEach(
        (track) -> {
          track
              .entrySet()
              .forEach(
                  (entry) -> {
                    List<ParticleData> particlesInFrame =
                        controlTracksIndexedByFrame_.get(entry.getKey());
                    if (particlesInFrame == null) {
                      particlesInFrame = new ArrayList<>();
                    }
                    particlesInFrame.add(entry.getValue());
                    controlTracksIndexedByFrame_.put(entry.getKey(), particlesInFrame);
                  });
        });

    super.setVisible(true);
  }

  @Override
  public String getTitle() {
    return TITLE;
  }

  @Override
  public JComponent getConfigurationComponent() {
    if (configUI_ == null) {

      showMasksCheckBox_ = new JCheckBox("Show masks");
      showMasksCheckBox_.addActionListener(
          (ActionEvent e) -> {
            fireOverlayConfigurationChanged();
          });

      showBleachMasksCheckBox_ = new JCheckBox("Show Bleach");
      showBleachMasksCheckBox_.addActionListener(
          (ActionEvent e) -> {
            fireOverlayConfigurationChanged();
          });

      showControlMasksCheckBox_ = new JCheckBox("Show Controls");
      showControlMasksCheckBox_.addActionListener(
          (ActionEvent e) -> {
            fireOverlayConfigurationChanged();
          });

      configUI_ = new JPanel(new MigLayout(new LC().insets("4")));
      CC cc = new CC();
      configUI_.add(showMasksCheckBox_);
      configUI_.add(showBleachMasksCheckBox_);
      configUI_.add(showControlMasksCheckBox_, cc.wrap());
    }
    return configUI_;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   */
  @Override
  public void paintOverlay(
      Graphics2D g,
      Rectangle screenRect,
      DisplaySettings displaySettings,
      List<Image> images,
      Image primaryImage,
      Rectangle2D.Float imageViewPort) {
    // TODO: make sure this is our dataviewer
    Integer frame = primaryImage.getCoords().getTimePoint();
    if (tracksIndexedByFrame_.get(frame) != null) {
      final double zoomRatio = imageViewPort.width / screenRect.width;
      final int halfLength = symbolLenght_ / 2;

      // Draw the pattern in image pixel coordinates by applying a transform
      Graphics2D gTfm = (Graphics2D) g.create();
      gTfm.transform(AffineTransform.getScaleInstance(1.0 / zoomRatio, 1.0 / zoomRatio));
      gTfm.transform(AffineTransform.getTranslateInstance(-imageViewPort.x, -imageViewPort.y));
      // Stroke width should be 1.0 in screen coordinates
      gTfm.setStroke(new BasicStroke((float) zoomRatio));

      int colorIndex = 0;

      if (showControlMasksCheckBox_ != null && showControlMasksCheckBox_.isSelected()) {
        if (controlTracksIndexedByFrame_.get(frame) != null) {
          for (ParticleData p : controlTracksIndexedByFrame_.get(frame)) {
            gTfm.setColor(controlMaskColor_);
            List<Point2D_I32> mask = p.getMask();
            mask.forEach(
                (point) -> {
                  gTfm.drawRect(point.x, point.y, 1, 1);
                  // Note: drawLine is much faster the g.draw(new Line2D.Float());
                  // gTfm.drawLine(point.x, point.y, point.x, point.y);
                });
          }
        }
      }

      for (ParticleData p : tracksIndexedByFrame_.get(frame)) {
        if (p != null) {
          if (showMasksCheckBox_ != null && showMasksCheckBox_.isSelected()) {
            gTfm.setColor(maskColor_);
            List<Point2D_I32> mask = p.getMask();
            //  if (mask == null) {
            //     mask = p.getMask();
            //  }
            mask.forEach(
                (point) -> {
                  gTfm.drawRect(point.x, point.y, 1, 1);
                  // Note: drawLine is much faster the g.draw(new Line2D.Float());
                  // gTfm.drawLine(point.x, point.y, point.x, point.y);
                });
          }

          if (showBleachMasksCheckBox_ != null && showBleachMasksCheckBox_.isSelected()) {
            List<Point2D_I32> bleachMask = p.getBleachMask();
            if (bleachMask != null) {
              gTfm.setColor(bleachColor_);
              bleachMask.forEach(
                  (point) -> {
                    gTfm.drawRect(point.x, point.y, 1, 1);
                  });
            }
          }
          gTfm.setColor(WidgetSettings.COLORS[colorIndex]);

          drawMarker1(gTfm, p.getCentroid(), halfLength, halfLength / 2);
          if (p.getBleachSpot() != null) {
            //   drawCross(gTfm, p.getBleachSpot(), halfLength / 2);
          }
        }
        // change the color outside of the null check, so that null particles
        // do not change the color with which we draw the next particle
        //  All this is a bit ugly...
        colorIndex++;
        if (colorIndex >= WidgetSettings.COLORS.length) {
          colorIndex = 0;
        }
      }
    }
  }

  /**
   * Draws: | - - |
   *
   * @param g - graphics environment to draw on
   * @param p -
   * @param width1
   * @param width2
   */
  private void drawMarker1(Graphics2D g, Point2D_I32 p, int width1, int width2) {
    g.drawLine(p.x, p.y - width1, p.x, p.y - width1 + width2);
    g.drawLine(p.x, p.y + width1, p.x, p.y + width1 - width2);
    g.drawLine(p.x - width1, p.y, p.x - width1 + width2, p.y);
    g.drawLine(p.x + width1, p.y, p.x + width1 - width2, p.y);
  }

  private void drawCross(Graphics2D g, Point2D_I32 p, int width) {
    g.drawLine(p.x - width, p.y - width, p.x + width, p.y + width);
    g.drawLine(p.x + width, p.y - width, p.x - width, p.y + width);
  }
}
