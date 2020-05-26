///////////////////////////////////////////////////////////////////////////////
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com
//
// COPYRIGHT:    University of California, San Francisco, 2015
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
package org.micromanager.magellan.internal.surfacesandregions;

import org.micromanager.acqj.internal.acqengj.AffineTransformUtils;
import org.micromanager.acqj.api.xystage.XYStagePosition;
import java.awt.geom.Point2D;
import java.util.List;
import org.micromanager.magellan.internal.main.Magellan;
import org.apache.commons.math3.geometry.euclidean.twod.Euclidean2D;
import org.apache.commons.math3.geometry.partitioning.Region;
import org.micromanager.magellan.internal.gui.GUI;

/**
 *
 * @author Henry
 */
public class MultiPosGrid extends XYFootprint {

   private volatile Point2D.Double center_; //stored in stage space
   private volatile int overlapX_, overlapY_, rows_, cols_;

   public MultiPosGrid(SurfaceGridManager manager, String xyDevice, int r, int c, Point2D.Double center) {
      super(xyDevice);
      name_ = manager_.getNewGridName();

      center_ = center;
      updateParams(r, c);
   }

   public double getWidth_um() {
      double pixelSize = Magellan.getCore().getPixelSizeUm();
      int pixelWidth = (int) (cols_ * (Magellan.getCore().getImageWidth() - overlapX_) + overlapX_);
      return pixelSize * pixelWidth;
   }

   public double getHeight_um() {
      double pixelSize = Magellan.getCore().getPixelSizeUm();
      int imageHeight = (int) Magellan.getCore().getImageHeight();
      int pixelHeight = rows_ * (imageHeight - overlapY_) + overlapY_;
      return pixelSize * pixelHeight;
   }

   public void updateParams(int rows, int cols) {
      updateOverlap(GUI.getTileOverlap() / 100);
      rows_ = rows;
      cols_ = cols;
      manager_.surfaceOrGridUpdated(this);
   }

   private void updateOverlap(double overlapPercent) {
      overlapX_ = (int) (Magellan.getCore().getImageWidth() * overlapPercent);
      overlapY_ = (int) (Magellan.getCore().getImageHeight() * overlapPercent);
   }

   public int numCols() {
      return cols_;
   }

   public int numRows() {
      return rows_;
   }

   public int overlapX() {
      return overlapX_;
   }

   public int overlapY() {
      return overlapY_;
   }

   public Point2D.Double center() {
      return center_;
   }

   public void updateCenter(Point2D.Double newCenter) {
      center_.x = newCenter.x;
      center_.y = newCenter.y;
      manager_.surfaceOrGridUpdated(this);
   }

   public void translate(double dx, double dy) {
      center_ = new Point2D.Double(center_.x + dx, center_.y + dy);
      manager_.surfaceOrGridUpdated(this);
   }

   @Override
   public boolean isDefinedAtPosition(Point2D.Double[] posCorners) {
      //create square region correpsonding to stage pos
      Region<Euclidean2D> square = getStagePositionRegion(posCorners);

      for (XYStagePosition pos : getXYPositions()) {
         Region<Euclidean2D> tileSquare = getStagePositionRegion(posCorners);
         Region<Euclidean2D> intersection = regionFactory_.intersection(square, tileSquare);
          if (!intersection.isEmpty()) {
             return true;
          }
      }
      return false;
   }

   @Override
   public List<XYStagePosition> getXYPositions() {
      return AffineTransformUtils.createPositionGrid(
              (int) Magellan.getCore().getImageWidth(), (int) Magellan.getCore().getImageHeight(), 
              center_.x, center_.y, overlapX_, overlapY_, rows_, cols_);
   }

}
