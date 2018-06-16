
package org.micromanager.projector;

import ij.gui.EllipseRoi;
import ij.gui.OvalRoi;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.process.FloatPolygon;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.micromanager.internal.utils.ReportingUtils;

/**
 *
 * @author Nico
 */
public class ProjectorActions {
   
   
    /**
    * Sets the exposure time for the phototargeting device.
    * @param dev ProjectionDevice to be used
    * @param intervalUs  new exposure time in micros
    */
   public static void setExposure(ProjectionDevice dev, double intervalUs) {
      long previousExposure = dev.getExposure();
      long newExposure = (long) intervalUs;
      if (previousExposure != newExposure) {
         dev.setExposure(newExposure);
     }
   }
   
   
    /**
    * Illuminate a spot at position x,y.
    * @param dev ProjectionDevice to be used
    * @param x coordinate of ProjectionDevice
    * @param y coordinate of ProjectionDevice
    */
   public static void displaySpot(ProjectionDevice dev, double x, double y) {
      if (x >= dev.getXMinimum() && x < (dev.getXRange() + dev.getXMinimum())
            && y >= dev.getYMinimum() && y < (dev.getYRange() + dev.getYMinimum())) {
         dev.displaySpot(x, y);
      }
   }
   
   /**
    * Illuminate a spot at the center of the Galvo/SLM range, for
    * the exposure time.
    * @param dev ProjectionDevice to be used
    */
   public static void displayCenterSpot(ProjectionDevice dev) {
      double x = dev.getXRange() / 2 + dev.getXMinimum();
      double y = dev.getYRange() / 2 + dev.getYMinimum();
      dev.displaySpot(x, y);
   }
   
      /**
    * Returns ROIs, transformed by the current mapping.
    * @param rois Array of ImageJ Rois to be converted
    * @param mapping 
    * @return list of Rois converted into Polygons
    * 
    */
   public static List<FloatPolygon> transformROIs(Roi[] rois, 
           Map<Polygon, AffineTransform> mapping) {
      return ProjectorActions.transformRoiPolygons(roisAsPolygons(rois), mapping);
   }
   
    /**
    * Transform the Roi polygons with the given nonlinear mapping.
    * @param roiPolygons
    * @param mapping
    * @return 
    */
   public static List<FloatPolygon> transformRoiPolygons( 
           Polygon[] roiPolygons, Map<Polygon, AffineTransform> mapping) {
      ArrayList<FloatPolygon> transformedROIs = new ArrayList<FloatPolygon>();
      for (Polygon roiPolygon : roiPolygons) {
         FloatPolygon targeterPolygon = new FloatPolygon();
         try {
            Point2D targeterPoint;
            for (int i = 0; i < roiPolygon.npoints; ++i) {
               Point2D.Double imagePoint = new Point2D.Double(
                       roiPolygon.xpoints[i], roiPolygon.ypoints[i]);
               // targeterPoint = transformAndMirrorPoint(mapping, imagePoint);
               targeterPoint = imagePoint;
               if (targeterPoint == null) {
                  throw new Exception();
               }
               targeterPolygon.addPoint( (float) targeterPoint.getX(), 
                       (float) targeterPoint.getY() );
            }
            transformedROIs.add(targeterPolygon);
         } catch (Exception ex) {
            ReportingUtils.showError(ex);
            break;
         }
      }
      return transformedROIs;
   }
   
    // We can't handle Ellipse Rois and compounds PointRois directly.
   private static Roi[] homogenizeROIs(Roi[] rois) {
      List<Roi> roiList = new ArrayList<Roi>();
      for (Roi roi : rois) {
         switch (roi.getType()) {
            case Roi.POINT:
               Polygon poly = ((PointRoi) roi).getPolygon();
               for (int i = 0; i < poly.npoints; ++i) {
                  roiList.add(new PointRoi(
                          poly.xpoints[i],
                          poly.ypoints[i]));
               }  break;
            case Roi.OVAL:
               roiList.add(asEllipseRoi((OvalRoi) roi));
               break;
            default:
               roiList.add(roi);
               break;
         }
      }
      return roiList.toArray(new Roi[roiList.size()]);
   }
   
    // Convert an OvalRoi to an EllipseRoi.
   private static Roi asEllipseRoi(OvalRoi roi) {
      Rectangle bounds = roi.getBounds();
      double aspectRatio = bounds.width / (double) bounds.height;
      if (aspectRatio < 1) {
         return new EllipseRoi(bounds.x + bounds.width / 2,
               bounds.y,
               bounds.x + bounds.width / 2,
               bounds.y + bounds.height,
               aspectRatio);
      } else {
         return new EllipseRoi(bounds.x,
               bounds.y + bounds.height / 2,
               bounds.x + bounds.width,
               bounds.y + bounds.height / 2,
               1 / aspectRatio);
      }
   }
   
   // Converts an ROI to a Polygon.
   private static Polygon asPolygon(Roi roi) {
      if ((roi.getType() == Roi.POINT)
               || (roi.getType() == Roi.FREEROI)
               || (roi.getType() == Roi.POLYGON)
            || (roi.getType() == Roi.RECTANGLE)) {
         return roi.getPolygon();
      } else {
         throw new RuntimeException("Can't use this type of ROI.");
      }
   }
   
   
   // Coverts an array of ImageJ Rois to an array of Polygons.
   // Handles EllipseRois and compound Point ROIs.
   public static Polygon[] roisAsPolygons(Roi[] rois) {
      Roi[] cleanROIs = homogenizeROIs(rois);
      List<Polygon> roiPolygons = new ArrayList<Polygon>();
      for (Roi roi : cleanROIs) {
         roiPolygons.add(asPolygon(roi));
      }
      return roiPolygons.toArray(new Polygon[0]);
   }
   
   
}
