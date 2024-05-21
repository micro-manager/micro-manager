///////////////////////////////////////////////////////////////////////////////
//PROJECT:       PWS Plugin
//
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nick Anthony, 2021
//
// COPYRIGHT:    Northwestern University, 2021
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
//

package org.micromanager.display.inspector.internal.panels.sharpnessinspector.ui;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Rectangle2D;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.panel.AbstractOverlay;
import org.jfree.chart.panel.Overlay;


/**
 * A Text overlay for a JFreeChart
 *
 * @author nick
 */
class JFreeTextOverlay extends AbstractOverlay implements Overlay {
   private String text_;
   private boolean vis_ = true;
   private final Font font_ = new Font("arial", Font.BOLD, 15);
    
   public JFreeTextOverlay(String text) {
      this.text_ = text;
   }
    
   public void setVisible(boolean visible) {
      this.vis_ = visible;
   }
    
   public boolean isVisible() {
      return this.vis_;
   }
    
   @Override
   public void paintOverlay(Graphics2D g2, ChartPanel chartPanel) {
      if (this.vis_) {
         Shape savedClip = g2.getClip();
         Rectangle2D dataArea = chartPanel.getScreenDataArea();
         g2.clip(dataArea);
         g2.setFont(this.font_);
         FontMetrics metrics = g2.getFontMetrics();
         int h = metrics.getHeight();
         int w = metrics.stringWidth(text_);
         g2.drawString(this.text_, (int) Math.round(dataArea.getCenterX() - (w / 2)),
                 (int) Math.round(dataArea.getCenterY() - (h / 2)));
            
         g2.setClip(savedClip);
      }
   }
}
