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

import org.micromanager.magellan.internal.acq.MagellanGUIAcquisitionSettings;
import org.micromanager.magellan.internal.coordinates.MagellanAffineUtils;
import org.micromanager.magellan.internal.coordinates.XYStagePosition;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import org.micromanager.magellan.internal.main.Magellan;
import org.micromanager.magellan.internal.misc.Log;
import org.apache.commons.math3.geometry.euclidean.twod.Euclidean2D;
import org.apache.commons.math3.geometry.euclidean.twod.PolygonsSet;
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;
import org.apache.commons.math3.geometry.partitioning.Region;
import org.apache.commons.math3.geometry.partitioning.RegionFactory;

/**
 *
 * @author Henry
 */
public class MultiPosGrid extends XYFootprint {

   private final RegionFactory<Euclidean2D> regionFacotry_ = new RegionFactory<Euclidean2D>();
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
      updateOverlap(MagellanGUIAcquisitionSettings.getStoredTileOverlapPercentage() / 100);
      rows_ = rows;
      cols_ = cols;
      manager_.surfaceOrGridUpdated(this);
   }

   private void updateOverlap(double overlapPercent) {
      overlapX_ = (int) (Magellan.getCore().getImageWidth() * overlapPercent);
      overlapY_ = (int) (Magellan.getCore().getImageHeight() * overlapPercent);
   }

   @Override
   public ArrayList<XYStagePosition> getXYPositionsNoUpdate() {
      try {
         AffineTransform transform = MagellanAffineUtils.getAffineTransform(center_.x, center_.y);
         ArrayList<XYStagePosition> positions = new ArrayList<XYStagePosition>();
         int fullTileWidth = (int) Magellan.getCore().getImageWidth();
         int fullTileHeight = (int) Magellan.getCore().getImageHeight();
         int tileWidthMinusOverlap = fullTileWidth - overlapX_;
         int tileHeightMinusOverlap = fullTileHeight - overlapY_;
         for (int col = 0; col < cols_; col++) {
            double xPixelOffset = (col - (cols_ - 1) / 2.0) * tileWidthMinusOverlap;
            for (int row = 0; row < rows_; row++) {
               double yPixelOffset = (row - (rows_ - 1) / 2.0) * tileHeightMinusOverlap;
               Point2D.Double pixelPos = new Point2D.Double(xPixelOffset, yPixelOffset);
               Point2D.Double stagePos = new Point2D.Double();
               transform.transform(pixelPos, stagePos);
               AffineTransform posTransform = MagellanAffineUtils.getAffineTransform(stagePos.x, stagePos.y);
               positions.add(new XYStagePosition(stagePos, tileWidthMinusOverlap, tileHeightMinusOverlap,
                       fullTileWidth, fullTileHeight, row, col, posTransform));
            }
         }
         return positions;
      } catch (Exception ex) {
         Log.log("Couldn't get affine transform");
         throw new RuntimeException();
      }
   }

   @Override
   public ArrayList<XYStagePosition> getXYPositions(double tileOverlapPercent) {
      try {
         AffineTransform transform = MagellanAffineUtils.getAffineTransform( center_.x, center_.y);
         ArrayList<XYStagePosition> positions = new ArrayList<XYStagePosition>();
         int fullTileWidth = (int) Magellan.getCore().getImageWidth();
         int fullTileHeight = (int) Magellan.getCore().getImageHeight();
         updateOverlap(tileOverlapPercent / 100);
         int tileWidthMinusOverlap = fullTileWidth - overlapX_;
         int tileHeightMinusOverlap = fullTileHeight - overlapY_;
         for (int col = 0; col < cols_; col++) {
            double xPixelOffset = (col - (cols_ - 1) / 2.0) * tileWidthMinusOverlap;
            //add in snaky behavior
                if (col % 2 == 0) {
                    for (int row = 0; row < rows_; row++) {
                        double yPixelOffset = (row - (rows_ - 1) / 2.0) * tileHeightMinusOverlap;
                        Point2D.Double pixelPos = new Point2D.Double(xPixelOffset, yPixelOffset);
                        Point2D.Double stagePos = new Point2D.Double();
                        transform.transform(pixelPos, stagePos);
                        AffineTransform posTransform = MagellanAffineUtils.getAffineTransform(stagePos.x, stagePos.y);
                        positions.add(new XYStagePosition(stagePos, tileWidthMinusOverlap, tileHeightMinusOverlap,
                                fullTileWidth, fullTileHeight, row, col, posTransform));
                    }
                } else {  
                    for (int row = rows_ - 1; row >= 0; row--) {
                        double yPixelOffset = (row - (rows_ - 1) / 2.0) * tileHeightMinusOverlap;
                        Point2D.Double pixelPos = new Point2D.Double(xPixelOffset, yPixelOffset);
                        Point2D.Double stagePos = new Point2D.Double();
                        transform.transform(pixelPos, stagePos);
                        AffineTransform posTransform = MagellanAffineUtils.getAffineTransform( stagePos.x, stagePos.y);
                        positions.add(new XYStagePosition(stagePos, tileWidthMinusOverlap, tileHeightMinusOverlap,
                                fullTileWidth, fullTileHeight, row, col, posTransform));
                    }
                }
         }    
         return positions;
      } catch (Exception ex) {
         Log.log("Couldn't get affine transform");
         throw new RuntimeException();
      }
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
   public boolean isDefinedAtPosition(XYStagePosition position) {
      //create square region correpsonding to stage pos
      Region<Euclidean2D> square = getStagePositionRegion(position);

      for (XYStagePosition pos : getXYPositionsNoUpdate()) {
         Region<Euclidean2D> tileSquare = getStagePositionRegion(pos);
         Region<Euclidean2D> intersection = regionFacotry_.intersection(square, tileSquare);
          if (!intersection.isEmpty()) {
             return true;
          }
      }
      return false;
   }

   private Region<Euclidean2D> getStagePositionRegion(XYStagePosition pos) {
      Region<Euclidean2D> square;
      Point2D.Double[] corners = pos.getDisplayedTileCorners();
      square = new PolygonsSet(0.0001, new Vector2D[]{
         new Vector2D(corners[0].x, corners[0].y),
         new Vector2D(corners[1].x, corners[1].y),
         new Vector2D(corners[2].x, corners[2].y),
         new Vector2D(corners[3].x, corners[3].y)});
      return square.checkPoint(new Vector2D(pos.getCenter().x, pos.getCenter().y)) == Region.Location.OUTSIDE ? regionFacotry_.getComplement(square) : square;
   }

}
