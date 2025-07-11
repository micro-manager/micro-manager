///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, October 29, 2006
//
// COPYRIGHT:    University of California, San Francisco, 2006
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
// CVS:          $Id$
//

package org.micromanager.internal.graph;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.text.DecimalFormat;
import javax.swing.JPanel;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * XY graph view.
 */
public final class GraphPanel extends JPanel {
   private static final long serialVersionUID = -1280955888510181945L;
   private GraphData data_;
   protected GraphData.Bounds bounds_;

   private float xMargin_ = 50;
   private float yMargin_ = 50;
   boolean textVisible_ = true;
   boolean gridVisible_ = true;
   private String xLabel_ = null;
   private String yLabel_ = null;

   float cursorLoPos_;
   float cursorHiPos_;
   double gamma_;

   private boolean fillTrace_ = false;
   private Color traceColor_ = Color.black;

   public GraphPanel() {
      data_ = new GraphData();
      setAutoBounds();
      cursorLoPos_ = (float) bounds_.xMin;
      cursorHiPos_ = (float) bounds_.xMax;
      gamma_ = 1.0;
   }

   public void setData(GraphData d) {
      data_ = d;
   }

   public void setLabels(String xLabel, String yLabel) {
      xLabel_ = xLabel;
      yLabel_ = yLabel;
   }

   public void setGamma(double gamma) {
      gamma_ = gamma;
   }

   public void setTraceStyle(boolean fillTrace, Color color) {
      fillTrace_ = fillTrace;
      traceColor_ = color;
   }

   public final void setAutoBounds() {
      bounds_ = data_.getBounds();
      adjustCursors();
   }

   public void setMargins(float x, float y) {
      xMargin_ = x;
      yMargin_ = y;
   }

   public void setCursors(double low, double high, double gamma) {
      cursorLoPos_ = (float) low;
      cursorHiPos_ = (float) high;
      gamma_ = gamma;
   }

   public void setTextVisible(boolean state) {
      textVisible_ = state;
   }

   public void setGridVisible(boolean state) {
      gridVisible_ = state;
   }

   public GraphData.Bounds getGraphBounds() {
      return bounds_;
   }


   public void setBounds(double xMin, double xMax, double yMin, double yMax) {
      bounds_.xMin = xMin;
      bounds_.xMax = xMax;
      bounds_.yMin = yMin;
      bounds_.yMax = yMax;
      adjustCursors();
   }

   public void setBounds(GraphData.Bounds b) {
      bounds_ = b;
      adjustCursors();
   }

   private void adjustCursors() {
      cursorLoPos_ = Math.max(cursorLoPos_, (float) bounds_.xMin);
      //      cursorHiPos_ = Math.min(cursorHiPos_, (float) bounds_.xMax);
      //took this line out so contrast line can have an endpoint beyond
      //length of  histogram
   }

   /**
    * Draw graph traces.
    *
    * @param g graphics context
    * @param box drawing rectangle
    */
   protected void drawGraph(Graphics2D g, Rectangle box) {
      if (data_.getSize() < 2) {
         return;
      }

      final Color oldColor = g.getColor();
      g.setColor(traceColor_);

      // correct if Y range is zero
      if (bounds_.getRangeY() == 0.0) {
         if (bounds_.yMax > 0.0) {
            bounds_.yMin = 0.0;
         } else if (bounds_.yMax < 0.0) {
            bounds_.yMax = 0.0;
         }
      }

      // bounds can have strange values (i.e. 1e-42).  Avoid artefacts:
      if (bounds_.getRangeX() <= 0.0 || bounds_.getRangeY() <= 1.e-10) {
         return; // invalid range data
      }

      // set scaling
      float xUnit = (float) (box.width / bounds_.getRangeX());
      float yUnit = (float) (box.height / bounds_.getRangeY());

      GeneralPath trace = new GeneralPath(GeneralPath.WIND_EVEN_ODD, data_.getSize() + 1);
      // we need to start and end at y=0 to avoid strange display issues
      Point2D.Float pt0 = getDevicePoint(new Point2D.Float(0.0f, 0.0f), box, xUnit, yUnit);
      trace.moveTo(pt0.x, pt0.y);
      Point2D.Float pt1 = getDevicePoint(new Point2D.Float(1.0f, 0.0f), box, xUnit, yUnit);
      float halfWidth = (pt1.x - pt0.x) / 2;
      for (int i = 0; i < data_.getSize(); i++) {
         // Convert from double to float.
         Point2D.Double tmp = data_.getPoint(i);
         Point2D.Float pt = getDevicePoint(
               new Point2D.Float((float) tmp.x, (float) tmp.y),
               box, xUnit, yUnit);
         trace.lineTo(pt.x - halfWidth, pt.y);
         trace.lineTo(pt.x + halfWidth, pt.y);
      }
      pt0 = getDevicePoint(new Point2D.Float((float) data_.getPoint(
            data_.getSize() - 1).getX(), 0.0f),
            box, xUnit, yUnit);
      trace.lineTo(pt0.x, pt0.y);

      if (fillTrace_) {
         g.fill(trace);
      } else {
         g.draw(trace);
      }

      g.setColor(oldColor);
   }

   public void drawCursor(Graphics2D g, Rectangle box, float xPos) {
      // This should be implemented in a derived class
   }

   public void drawMapping(Graphics2D g, Rectangle box, float xStart, float xEnd, double gamma) {
      // This should be overridden in a derived class
   }

   public Point2D.Float getDevicePointUnclippedXMax(Point2D.Float pt, Rectangle box,
                                                    float xUnit, float yUnit) {
      Point2D.Float ptDev = new Point2D.Float((float) (pt.x - bounds_.xMin) * xUnit + box.x,
            box.height - (float) (pt.y - bounds_.yMin) * yUnit + box.y);
      // clip the drawing region
      ptDev.x = Math.max(ptDev.x, (float) box.x);
      ptDev.y = Math.max(Math.min(ptDev.y, (float) box.y + box.height), (float) box.y);
      return ptDev;
   }

   public Point2D.Float getDevicePoint(Point2D.Float pt, Rectangle box, float xUnit, float yUnit) {
      Point2D.Float ptDev = new Point2D.Float((float) (pt.x - bounds_.xMin) * xUnit + box.x,
            box.height - (float) (pt.y - bounds_.yMin) * yUnit + box.y);
      // clip the drawing region
      ptDev.x = Math.max(Math.min(ptDev.x, (float) box.x + box.width), (float) box.x);
      ptDev.y = Math.max(Math.min(ptDev.y, (float) box.y + box.height), (float) box.y);
      return ptDev;
   }

   public Point2D.Float getPositionPoint(int x, int y) {
      Rectangle box = getBox();
      return new Point2D.Float(
            (float) (((x - box.x) / (float) box.width) * (bounds_.xMax - bounds_.xMin)),
            (float) ((((box.y + box.height) - y)
                  / (float) box.height)
                  * (bounds_.yMax - bounds_.yMin)));
   }

   /**
    * Draw grid on the graph box with tick lines and numbers.
    *
    * @param g graphics context
    * @param box drawing rectangle
    */
   private void drawGrid(Graphics2D g, Rectangle box) {
      if (data_.getSize() < 2) {
         ReportingUtils.logMessage("Invalid size " + data_.getSize());
         return;
      }

      // correct if Y range is zero
      if (bounds_.getRangeY() == 0.0) {
         if (bounds_.yMax > 0.0) {
            bounds_.yMin = 0.0;
         } else if (bounds_.yMax < 0.0) {
            bounds_.yMax = 0.0;
         }
      }

      if (bounds_.getRangeX() <= 0.0 || bounds_.getRangeY() <= 0) {
         return; // invalid range data
      }

      int tickCountX = 5;
      int tickCountY = 5;

      final int tickSizeX = box.width / tickCountX;
      final int tickSizeY = box.height / tickCountY;

      final Color oldColor = g.getColor();
      final Stroke oldStroke = g.getStroke();
      g.setColor(Color.gray);
      g.setStroke(new BasicStroke(1));
      g.draw(box);


      if (gridVisible_) {
         for (int i = 1; i < tickCountX; i++) {
            int x = box.x + tickSizeX * i;
            g.draw(new Line2D.Float(x, box.y + box.height, x, box.y));
         }
         for (int i = 1; i < tickCountX; i++) {
            int y = box.y + tickSizeY * i;
            g.draw(new Line2D.Float(box.x, y, box.x + box.width, y));
         }
      }

      g.setColor(Color.black);

      if (textVisible_) {
         // create font
         int textSize = 10;
         Font fnt = new Font("Arial", Font.PLAIN, textSize);
         Font oldFont = g.getFont();
         g.setFont(fnt);
         DecimalFormat fmt = new DecimalFormat("#0.00");
         float textOffsetY = 20;
         g.drawString(fmt.format(bounds_.xMin), box.x, box.y + box.height + textOffsetY);
         g.drawString(fmt.format(bounds_.xMax), box.x + box.width, box.y + box.height
               + textOffsetY);
         float textOffsetX = 40;
         g.drawString(fmt.format(bounds_.yMin), box.x - textOffsetX, box.y + box.height);
         g.drawString(fmt.format(bounds_.yMax), box.x - textOffsetX, box.y);
         g.setFont(oldFont);
      }

      g.setColor(oldColor);
      g.setStroke(oldStroke);
   }

   public Rectangle getBox() {
      Rectangle box = getBounds();
      box.x = (int) xMargin_;
      box.y = (int) yMargin_;
      box.height -= 2 * yMargin_;
      box.width -= 2 * xMargin_;
      return box;
   }

   @Override
   public void paintComponent(Graphics g) {

      super.paintComponent(g); // JPanel draws background
      Graphics2D g2d = (Graphics2D) g;

      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON);


      // get drawing rectangle
      Rectangle box = getBox();

      // save current settings
      final Color oldColor = g2d.getColor();
      final Paint oldPaint = g2d.getPaint();
      final Stroke oldStroke = g2d.getStroke();

      g2d.setPaint(Color.black);
      g2d.setStroke(new BasicStroke((float) 2));

      drawGraph(g2d, box);
      drawGrid(g2d, box);
      drawCursor(g2d, box, cursorLoPos_);
      drawCursor(g2d, box, cursorHiPos_);
      drawMapping(g2d, box, cursorLoPos_, cursorHiPos_, gamma_);
      if (xLabel_ != null) {
         int strWidth = g2d.getFontMetrics(g2d.getFont()).stringWidth(xLabel_);
         int x = box.x + box.width / 2 - strWidth / 2;
         g2d.drawString(xLabel_, x, box.y + box.height + 15);
      }
      if (yLabel_ != null) {
         // Draw this label rotated 90 degrees.
         int strWidth = g2d.getFontMetrics(g2d.getFont()).stringWidth(yLabel_);
         int strHeight = g2d.getFontMetrics(g2d.getFont()).getHeight();
         int x = box.x - strHeight - 4;
         int y = box.y + box.height / 2 - strWidth / 2;
         AffineTransform orig = g2d.getTransform();
         g2d.translate(x, y);
         g2d.rotate(Math.PI / 2);
         g2d.drawString(yLabel_, 0, 0);
         g2d.setTransform(orig);
      }

      // restore settings
      g2d.setPaint(oldPaint);
      g2d.setStroke(oldStroke);
      g2d.setColor(oldColor);
   }

}
