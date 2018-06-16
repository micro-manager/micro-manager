
package org.micromanager.projector.internal;

import ij.IJ;
import ij.plugin.frame.RoiManager;
import ij.process.FloatPolygon;

import java.awt.Checkbox;
import java.awt.event.ItemEvent;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.micromanager.internal.utils.ReportingUtils;

/**
 *
 * @author nico
 */
public class Utils {
   // Show the ImageJ Roi Manager and return a reference to it.
   public static RoiManager showRoiManager() {
      IJ.run("ROI Manager...");
      final RoiManager roiManager = RoiManager.getInstance();
      // "Get the "Show All" checkbox and make sure it is checked.
      Checkbox checkbox = (Checkbox) ((Panel) roiManager.getComponent(1)).getComponent(9);
      checkbox.setState(true);
      // Simulated click of the "Show All" checkbox to force ImageJ
      // to show all of the ROIs.
      roiManager.itemStateChanged(new ItemEvent(checkbox, 0, null, ItemEvent.SELECTED));
      return roiManager;
   }

   public static List<Polygon> FloatToNormalPolygon(List<FloatPolygon> floatPolygons) {
      // manually copy from FloatPolygon to Polygon
      List<Polygon> roiPolygons = new ArrayList<Polygon>();
      for (FloatPolygon fp : floatPolygons) {
         Polygon p = new Polygon();
         for (int i = 0; i < fp.npoints; i++) {
            p.addPoint((int) (0.5 + fp.xpoints[i]),
                    (int) (0.5 + fp.ypoints[i]));
         }
         roiPolygons.add(p);
      }
      return roiPolygons;
   }
   
    /**
    * Simple utility methods for points
    *
    * Adds a point to an existing polygon.
    */
   public static void addVertex(Polygon polygon, Point p) {
      polygon.addPoint(p.x, p.y);
   }
   
   /**
    * Returns the vertices of the given polygon as a series of points.
    */
   public static Point[] getVertices(Polygon polygon) {
      Point vertices[] = new Point[polygon.npoints];
      for (int i = 0; i < polygon.npoints; ++i) {
         vertices[i] = new Point(polygon.xpoints[i], polygon.ypoints[i]);
      }   
      return vertices;
   }
   
   /**
    * Gets the vectorial mean of an array of Points.
    */
   public static Point2D.Double meanPosition2D(Point[] points) {
      double xsum = 0;
      double ysum = 0;
      int n = points.length;
      for (int i = 0; i < n; ++i) {
         xsum += points[i].x;
         ysum += points[i].y;
      }
      return new Point2D.Double(xsum/n, ysum/n);
   }

   /**
    * Converts a Point with double values for x,y to a point
    * with x and y rounded to the nearest integer.
    */
   public static Point toIntPoint(Point2D.Double pt) {
      return new Point((int) (0.5 + pt.x), (int) (0.5 + pt.y));
   }

   /**
    * Converts a Point with integer values to a Point with x and y doubles.
    * @param pt
    * @return 
    */
   public static Point2D.Double toDoublePoint(Point pt) {
      return new Point2D.Double(pt.x, pt.y);
   }
   
      // Transform and mirror (if necessary) a point on an image to 
   // a point on phototargeter coordinates.
   public static Point2D.Double transformAndMirrorPoint(Map<Polygon, AffineTransform> mapping, 
           Point2D.Double pt) {
      //Point2D.Double pOffscreen = mirrorIfNecessary(pt, imgp);
      return Utils.transformPoint(mapping, pt);
   }
   
   /**
    * Transform a point, pt, given the mapping, which is a Map of polygon cells
   // to AffineTransforms.
   */
   public static Point2D.Double transformPoint(Map<Polygon, AffineTransform> mapping, Point2D.Double pt) {
      Set<Polygon> set = mapping.keySet();
      // First find out if the given point is inside a cell, and if so,
      // transform it with that cell's AffineTransform.
      for (Polygon poly : set) {
         if (poly.contains(pt)) {
            return (Point2D.Double) mapping.get(poly).transform(pt, null);
         }
      }
      // The point isn't inside any cell, so search for the closest cell
      // and use the AffineTransform from that.
      double minDistance = Double.MAX_VALUE;
      Polygon bestPoly = null;
      for (Polygon poly : set) {
         double distance = Utils.meanPosition2D(Utils.getVertices(poly)).distance(pt.x, pt.y);
         if (minDistance > distance) {
            bestPoly = poly;
            minDistance = distance;
         }
      }
      if (bestPoly == null) {
         throw new RuntimeException("Unable to map point to device.");
      }
      return (Point2D.Double) mapping.get(bestPoly).transform(pt, null);
   }
   
   // Sleep until the designated clock time.
   public static void sleepUntil(long clockTimeMillis) {
      long delta = clockTimeMillis - System.currentTimeMillis();
      if (delta > 0) {
         try {
            Thread.sleep(delta);
         } catch (InterruptedException ex) {
            ReportingUtils.logError(ex);
         }
      }
   }
   
}
