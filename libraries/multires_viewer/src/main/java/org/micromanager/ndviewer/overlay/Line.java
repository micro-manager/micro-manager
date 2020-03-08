package org.micromanager.ndviewer.overlay;

import java.awt.*;
import java.awt.image.*;
import java.awt.event.*;
import java.awt.geom.*;

/**
 * This class represents a straight line selection.
 */
public class Line extends Roi {

   public int x1, y1, x2, y2;	// the line
   public double x1d, y1d, x2d, y2d;	// the line using sub-pixel coordinates
   protected double x1R, y1R, x2R, y2R;  // the line, relative to base of bounding rect
   private double xHandleOffset, yHandleOffset;
   protected double startxd, startyd;
   static boolean widthChanged;
   private boolean drawOffset;
   private boolean dragged;
   private int mouseUpCount;

   /**
    * Creates a new straight line selection using the specified starting and
    * ending offscreen integer coordinates.
    */
   public Line(int ox1, int oy1, int ox2, int oy2) {
      this((double) ox1, (double) oy1, (double) ox2, (double) oy2);
   }

   /**
    * Creates a new straight line selection using the specified starting and
    * ending offscreen double coordinates.
    */
   public Line(double ox1, double oy1, double ox2, double oy2) {
      super((int) ox1, (int) oy1, 0, 0);
      type = LINE;
      x1d = ox1;
      y1d = oy1;
      x2d = ox2;
      y2d = oy2;
      x1 = (int) x1d;
      y1 = (int) y1d;
      x2 = (int) x2d;
      y2 = (int) y2d;
      x = (int) Math.min(x1d, x2d);
      y = (int) Math.min(y1d, y2d);
      x1R = x1d - x;
      y1R = y1d - y;
      x2R = x2d - x;
      y2R = y2d - y;
      width = (int) Math.abs(x2R - x1R);
      height = (int) Math.abs(y2R - y1R);
//		if (!(this instanceof Arrow) && lineWidth>1)
//			updateWideLine(lineWidth);
//		updateClipRect();
      oldX = x;
      oldY = y;
      oldWidth = width;
      oldHeight = height;
      state = NORMAL;
   }

   protected void grow(int sx, int sy) { //mouseDragged
      drawLine(sx, sy);
      dragged = true;
   }

   public void mouseMoved(MouseEvent e) {
      drawLine(e.getX(), e.getY());
   }

   protected void drawLine(int sx, int sy) {
      double xend = sx;
      double yend = sy;
      if (xend < 0.0) {
         xend = 0.0;
      }
      if (yend < 0.0) {
         yend = 0.0;
      }
      if (xend > xMax) {
         xend = xMax;
      }
      if (yend > yMax) {
         yend = yMax;
      }
      double xstart = x + x1R, ystart = y + y1R;
      if (constrain) {
         int i = 0;
         double dy = Math.abs(yend - ystart);
         double dx = Math.abs(xend - xstart);
         double comp = dy / dx;

         for (; i < PI_SEARCH.length; i++) {
            if (comp < PI_SEARCH[i]) {
               break;
            }
         }

         if (i < PI_SEARCH.length) {
            if (yend > ystart) {
               yend = ystart + dx * PI_MULT[i];
            } else {
               yend = ystart - dx * PI_MULT[i];
            }
         } else {
            xend = xstart;
         }
      }
      x = (int) Math.min(x + x1R, xend);
      y = (int) Math.min(y + y1R, yend);
      x1R = xstart - x;
      y1R = ystart - y;
      x2R = xend - x;
      y2R = yend - y;

      width = (int) Math.abs(x2R - x1R);
      height = (int) Math.abs(y2R - y1R);
      if (width < 1) {
         width = 1;
      }
      if (height < 1) {
         height = 1;
      }

      oldX = x;
      oldY = y;
      oldWidth = width;
      oldHeight = height;
   }

   /**
    * Used for angle searches in line ROI creation
    */
   private static final double[] PI_SEARCH = {Math.tan(Math.PI / 8), Math.tan((3 * Math.PI) / 8)};
   private static final double[] PI_MULT = {0, Math.tan((2 * Math.PI) / 8)};

   /**
    * Draws this line on the image.
    */
   public void draw(Graphics g) {
      Color color = strokeColor != null ? strokeColor : ROIColor;
      boolean isActiveOverlayRoi = !overlay;
      if (isActiveOverlayRoi) {
         if (color == Color.cyan) {
            color = Color.magenta;
         } else {
            color = Color.cyan;
         }
      }
      double x = getXBase();
      double y = getYBase();
      g.setColor(color);
      x1d = x + x1R;
      y1d = y + y1R;
      x2d = x + x2R;
      y2d = y + y2R;
      x1 = (int) x1d;
      y1 = (int) y1d;
      x2 = (int) x2d;
      y2 = (int) y2d;
      double offset = getDrawOffset() && 1 > 1.0 ? -0.5 : 0.0;

      int sx1 = (int) (x1d + offset);
      int sy1 = (int) (y1d + offset);
      int sx2 = (int) (x2d + offset);
      int sy2 = (int) (y2d + offset);
      int sx3 = sx1 + (sx2 - sx1) / 2;
      int sy3 = sy1 + (sy2 - sy1) / 2;
      Graphics2D g2d = (Graphics2D) g;
      if (stroke != null && !isActiveOverlayRoi) {
         g2d.setStroke(stroke);
      }
      g.drawLine(sx1, sy1, sx2, sy2);
      if (wideLine && !overlay) {
         g2d.setStroke(onePixelWide);
         g.setColor(getColor());
         g.drawLine(sx1, sy1, sx2, sy2);
      }
      if (!overlay) {
         int size2 = HANDLE_SIZE / 2;
         mag = 1;
         handleColor = strokeColor != null ? strokeColor : ROIColor;
         drawHandle(g, sx1 - size2, sy1 - size2);
         handleColor = Color.white;
         drawHandle(g, sx2 - size2, sy2 - size2);
         drawHandle(g, sx3 - size2, sy3 - size2);
      }
   }

   /**
    * Returns the length of this line in pixels.
    */
   public double getRawLength() {
      return Math.sqrt((x2d - x1d) * (x2d - x1d) + (y2d - y1d) * (y2d - y1d));
   }

   /**
    * Returns, as a Polygon, the two points that define this line.
    */
   public Polygon getPoints() {
      Polygon p = new Polygon();
      p.addPoint((int) Math.round(x1d), (int) Math.round(y1d));
      p.addPoint((int) Math.round(x2d), (int) Math.round(y2d));
      return p;
   }

   public boolean contains(int x, int y) {
      if (getStrokeWidth() > 1) {
         if ((x == x1 && y == y1) || (x == x2 && y == y2)) {
            return true;
         } else {
            return getPolygon().contains(x, y);
         }
      } else {
         return false;
      }
   }

   public static int getWidth() {
      return lineWidth;
   }

   public static void setWidth(int w) {
      if (w < 1) {
         w = 1;
      }
      int max = 500;
      if (w > max) {
         if (w > max) {
            w = max;
         }
      }
      lineWidth = w;
      widthChanged = true;
   }

   public void setStrokeWidth(float width) {
      super.setStrokeWidth(width);
      if (getStrokeColor() == Roi.getColor()) {
         wideLine = true;
      }
   }

   /**
    * Return the bounding rectangle of this line.
    */
   public Rectangle getBounds() {
      int xmin = (int) Math.round(Math.min(x1d, x2d));
      int ymin = (int) Math.round(Math.min(y1d, y2d));
      int w = (int) Math.round(Math.abs(x2d - x1d));
      int h = (int) Math.round(Math.abs(y2d - y1d));
      return new Rectangle(xmin, ymin, w, h);
   }

   protected int clipRectMargin() {
      return 4;
   }

   public boolean getDrawOffset() {
      return drawOffset;
   }

   public void setDrawOffset(boolean drawOffset) {
      this.drawOffset = drawOffset;
   }

   /**
    * Always returns true.
    */
   public boolean subPixelResolution() {
      return true;
   }

   public void setLocation(int x, int y) {
      super.setLocation(x, y);
      double xx = getXBase();
      double yy = getYBase();
      x1d = xx + x1R;
      y1d = yy + y1R;
      x2d = xx + x2R;
      y2d = yy + y2R;
      x1 = (int) x1d;
      y1 = (int) y1d;
      x2 = (int) x2d;
      y2 = (int) y2d;
   }

}
