package org.micromanager.tileddataviewer.overlay;


import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;

/**
 * A polygon or polyline region of interest defined by an ordered list of points
 * (in display/screen pixel coordinates, like the other overlay Roi classes).
 *
 * <p>When {@code closed} is true the points form a closed polygon; otherwise they
 * are drawn as an open polyline (used for an in-progress polygon or a freehand trace).
 * This Roi is drawn directly via the Graphics polygon/polyline primitives because the
 * base {@link Roi} only supports rectangles.</p>
 */
public class PolygonRoi extends Roi {

   private final int[] xs_;
   private final int[] ys_;
   private final boolean closed_;

   /**
    * Creates a PolygonRoi from arrays of display-pixel coordinates.
    *
    * @param xs x coordinates (display pixels)
    * @param ys y coordinates (display pixels)
    * @param closed true to draw a closed polygon, false for an open polyline
    */
   public PolygonRoi(int[] xs, int[] ys, boolean closed) {
      super(boundsX(xs), boundsY(ys), boundsW(xs), boundsH(ys));
      xs_ = xs.clone();
      ys_ = ys.clone();
      closed_ = closed;
      type = closed ? POLYGON : POLYLINE;
   }

   private static int boundsX(int[] xs) {
      int min = Integer.MAX_VALUE;
      for (int v : xs) {
         min = Math.min(min, v);
      }
      return xs.length == 0 ? 0 : min;
   }

   private static int boundsY(int[] ys) {
      int min = Integer.MAX_VALUE;
      for (int v : ys) {
         min = Math.min(min, v);
      }
      return ys.length == 0 ? 0 : min;
   }

   private static int boundsW(int[] xs) {
      int min = Integer.MAX_VALUE;
      int max = Integer.MIN_VALUE;
      for (int v : xs) {
         min = Math.min(min, v);
         max = Math.max(max, v);
      }
      return xs.length == 0 ? 1 : Math.max(1, max - min);
   }

   private static int boundsH(int[] ys) {
      int min = Integer.MAX_VALUE;
      int max = Integer.MIN_VALUE;
      for (int v : ys) {
         min = Math.min(min, v);
         max = Math.max(max, v);
      }
      return ys.length == 0 ? 1 : Math.max(1, max - min);
   }

   @Override
   public void draw(Graphics g) {
      Color color = strokeColor != null ? strokeColor : ROIColor;
      g.setColor(color);
      Graphics2D g2d = (Graphics2D) g;
      if (stroke != null) {
         g2d.setStroke(stroke);
      }
      if (xs_.length == 0) {
         return;
      }
      if (closed_) {
         g.drawPolygon(xs_, ys_, xs_.length);
      } else {
         g.drawPolyline(xs_, ys_, xs_.length);
      }
   }
}
