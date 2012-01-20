/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.projector;

import ij.gui.PointRoi;
import ij.gui.Roi;
import java.awt.Point;
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
   int side_ = 500;
   
   public Galvo(CMMCore mmc) {
      mmc_ = mmc;
      galvo_ = mmc_.getGalvoDevice();
   }

   public void displaySpot(double x, double y) {
      try {
         mmc_.setGalvoPosition(galvo_, x - side_/2,  y - side_/2);
      } catch (Exception ex) {
         ReportingUtils.showError(ex);
      }
   }

   public int getWidth() {
      return side_;
   }

   public int getHeight() {
      return side_;
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
      } else {
         ReportingUtils.showError("Not able to do extended point rois.");
      }
   }

   private static Point pointRoiToPoint(PointRoi roi) {
      final Rectangle bounds = roi.getBounds();
      return new Point(bounds.x, bounds.y);
   }

   
}
