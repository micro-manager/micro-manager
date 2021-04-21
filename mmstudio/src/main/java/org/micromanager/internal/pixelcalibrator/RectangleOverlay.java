/*
 * Copyright (C) 2018 Regents of the University of California
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

package org.micromanager.internal.pixelcalibrator;

import org.micromanager.data.Image;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.overlay.AbstractOverlay;

import java.awt.*;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.util.List;

/**
 * This class draws rectangles on the overlay
 *
 * @author nico
 */
public class RectangleOverlay extends AbstractOverlay {
  private int x_;
  private int y_;
  private int width_;
  private int height_;

  @Override
  public String getTitle() {
    return "Square Overlay";
  }

  public void set(int x, int y, int width, int height) {
    x_ = x;
    y_ = y;
    width_ = width;
    height_ = height;
  }

  public void set(Rectangle rect) {
    set(rect.x, rect.y, rect.width, rect.height);
  }

  @Override
  public void paintOverlay(
      Graphics2D graphicsContext,
      Rectangle screenRect,
      DisplaySettings displaySettings,
      List<Image> images,
      Image primaryImage,
      Rectangle2D.Float imageViewPort) {
    final double zoomRatio = imageViewPort.width / screenRect.width;
    graphicsContext.setColor(Color.yellow);
    drawRectangle(
        graphicsContext,
        (int) (x_ / zoomRatio),
        (int) (y_ / zoomRatio),
        (int) (width_ / zoomRatio),
        (int) (height_ / zoomRatio));
  }

  void drawRectangle(Graphics2D g, final int x, final int y, final int width, final int height) {
    g.draw(new Line2D.Float(x, y, x + width, y));
    g.draw(new Line2D.Float(x + width, y, x + width, y + height));
    g.draw(new Line2D.Float(x + width, y + height, x, y + height));
    g.draw(new Line2D.Float(x, y + height, x, y));

    super.fireOverlayConfigurationChanged();
  }
}
