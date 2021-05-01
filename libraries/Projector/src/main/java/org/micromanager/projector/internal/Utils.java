package org.micromanager.projector.internal;

import ij.IJ;
import ij.plugin.frame.RoiManager;
import ij.process.FloatPolygon;
import java.awt.Checkbox;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.event.ItemEvent;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import mmcorej.CMMCore;
import org.micromanager.internal.utils.ReportingUtils;

/**
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
      List<Polygon> roiPolygons = new ArrayList<>();
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
    * Since the core does not have an api call to detect binning, we need to interpret a property.
    * This is a bit ugly...
    *
    * @param core Micro-Manager core instance
    * @return Integer represnting binning.  Unbinned is 1, 2x2 binning = 2, etc..
    * @throws java.lang.Exception Core will throw an exception, a parse error is also possible
    */
   public static Integer getBinning(CMMCore core) throws Exception {
      // Hamamatsu reports 1x1.  I wish there was an api call for binning
      return Integer.parseInt(core.getProperty(core.getCameraDevice(), "Binning")
            .substring(0, 1));
   }

   /**
    * Simple utility methods for points
    * <p>
    * Adds a point to an existing polygon.
    *
    * @param polygon Existing polygon
    * @param p       Point to be added
    */
   public static void addVertex(Polygon polygon, Point p) {
      polygon.addPoint(p.x, p.y);
   }

   /**
    * Returns the vertices of the given polygon as a series of points.
    *
    * @param polygon Existing Polygon
    * @return Vertices  of the polygon
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
      return new Point2D.Double(xsum / n, ysum / n);
   }

   /**
    * Converts a Point with double values for x,y to a point with x and y rounded to the nearest
    * integer.
    */
   public static Point toIntPoint(Point2D.Double pt) {
      return new Point((int) (0.5 + pt.x), (int) (0.5 + pt.y));
   }

   /**
    * Converts a Point with integer values to a Point with x and y doubles.
    *
    * @param pt
    * @return
    */
   public static Point2D.Double toDoublePoint(Point pt) {
      return new Point2D.Double(pt.x, pt.y);
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
