/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.projector;

import ij.gui.PointRoi;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.logging.Level;
import java.util.logging.Logger;
import mmcorej.CMMCore;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author arthur
 */
public class Galvo implements ProjectionDevice {
   String galvo_;
   CMMCore mmc_;
   int side_ = 4096;
   
   public Galvo(CMMCore mmc) {
      mmc_ = mmc;
      galvo_ = mmc_.getGalvoDevice();
   }

   public void displaySpot(double x, double y) {
      try {
         mmc_.setGalvoPosition(galvo_, x, y);
      } catch (Exception ex) {
         ReportingUtils.showError(ex);
      }
   }

   public double getWidth() {
      try {
         return (double) mmc_.getGalvoXRange(galvo_);
      } catch (Exception ex) {
         ReportingUtils.showError("Unable to get galvo width.");
         return 0;
      }
   }

   public double getHeight() {
      try {
         return (double) mmc_.getGalvoYRange(galvo_);
      } catch (Exception ex) {
         ReportingUtils.showError("Unable to get galvo height.");
         return 0;
      }
   }

   public void turnOn() {
      try {
         mmc_.setProperty(galvo_, "CalibrationMode", 1);
      } catch (Exception ex) {
         ReportingUtils.showError(ex);
      }
   }

   public void turnOff() {
      try {
         mmc_.setProperty(galvo_, "CalibrationMode", 0);
      } catch (Exception ex) {
         ReportingUtils.showError(ex);
      }
   }

   public void setRoi(Roi roi, AffineTransform trans) {
      if (roi instanceof PointRoi) {
         Point p = pointRoiToPoint((PointRoi) roi);
         Point2D.Double pIn = new Point2D.Double(p.x, p.y);
         Point2D.Double pOut = new Point2D.Double();
         trans.transform(pIn, pOut);
         displaySpot(pOut.x, pOut.y);
      } else if (roi instanceof PolygonRoi) {
         Polygon poly = ((PolygonRoi) roi).getPolygon();
         try {
            mmc_.deleteGalvoPolygons(galvo_);
            Point2D lastGalvoPoint = null;
            for (int i = 0; i < poly.npoints; ++i) {
               Point2D.Double imagePoint = new Point2D.Double(poly.xpoints[i],poly.ypoints[i]);
               Point2D galvoPoint = trans.transform(imagePoint, null);
               if (i==0) {
                  lastGalvoPoint = galvoPoint;
               }
               mmc_.addGalvoPolygonVertex(galvo_, 0, galvoPoint.getX(), galvoPoint.getY());
            }
            mmc_.addGalvoPolygonVertex(galvo_, 0, lastGalvoPoint.getX(), lastGalvoPoint.getY());
         } catch (Exception ex) {
            ReportingUtils.showError(ex);
         }
      } else {
         ReportingUtils.showError("Not able to handle this type of Roi.");
      }
   }

   private static Point pointRoiToPoint(PointRoi roi) {
      final Rectangle bounds = roi.getBounds();
      return new Point(bounds.x, bounds.y);
   }

   
}
