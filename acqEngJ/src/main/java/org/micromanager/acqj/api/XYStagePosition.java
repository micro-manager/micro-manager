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

package org.micromanager.acqj.api;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Objects;
import mmcorej.CMMCore;
import org.json.JSONArray;
import org.json.JSONObject;
import org.micromanager.acqj.internal.acqengj.Engine;
import org.micromanager.acqj.internal.acqengj.affineTransformUtils;

/**
 * Immutable object representing single XY stage position
 * 
 * @author Henry
 */
public class XYStagePosition {
   
   private String label_;
   private final Point2D.Double center_;
   //These are conveniences for determining where this position is in coordinates
   //of the XY stage
   private final Point2D.Double[] displayedTileCorners_;
   private final Point2D.Double[] fullTileCorners_;
   private long gridRow_, gridCol_;
   private final String xyName_;
   private final boolean inGrid_;
   
   
   /**
    * for abitrary positions not neccessarily in a grid
    */
   public XYStagePosition(Point2D.Double stagePosCenter) {
      label_ = "";
      center_ = stagePosCenter;
      displayedTileCorners_ = null;
      fullTileCorners_ = null;
      xyName_ = Engine.getCore().getXYStageDevice();
      if (xyName_.equals("")) {
         throw new RuntimeException("Core XY stage device undefined");
      }
      inGrid_ = false;
   }
   
    /**
    * for abitrary positions not neccessarily in a grid
    */
   public XYStagePosition(Point2D.Double stagePosCenter, String xyStageName) {
      label_ = "";
      center_ = stagePosCenter;
      displayedTileCorners_ = null;
      fullTileCorners_ = null;
      xyName_ = xyStageName;
      inGrid_ = false;
   }

   
   /**
    * for opening previously acquired data
    */
   public XYStagePosition(Point2D.Double stagePosCenter, long row, long col, String xyStageName) {
      label_ = "Grid_" + col + "_" + row;
      center_ = stagePosCenter;
      gridCol_ = col;
      gridRow_ = row;
      displayedTileCorners_ = null;
      fullTileCorners_ = null;
      xyName_ = xyStageName;
      inGrid_ = true;
   }

   /**
    * 
    * @param transform -- must be centered at current stage pos 
    */
   public XYStagePosition(Point2D.Double stagePosCenter, int width, int height,
           int overlapX, int overlapY, long row, long col, AffineTransform transform) {
       
      int displayTileWidth = width - overlapX;
      int displayTileHeight = height - overlapY;
      inGrid_ = true;
      label_ = "Grid_" + col + "_" + row;
      center_ = stagePosCenter;
      //coreners of displayed tiles (tiles - overlap)
      displayedTileCorners_ = new Point2D.Double[4];
      displayedTileCorners_[0] = new Point2D.Double();
      displayedTileCorners_[1] = new Point2D.Double();
      displayedTileCorners_[2] = new Point2D.Double();
      displayedTileCorners_[3] = new Point2D.Double();
      transform.transform(new Point2D.Double(-displayTileWidth / 2, -displayTileHeight / 2), displayedTileCorners_[0]);
      transform.transform(new Point2D.Double(-displayTileWidth / 2, displayTileHeight / 2), displayedTileCorners_[1]);
      transform.transform(new Point2D.Double(displayTileWidth / 2, displayTileHeight / 2), displayedTileCorners_[2]);
      transform.transform(new Point2D.Double(displayTileWidth / 2, -displayTileHeight / 2), displayedTileCorners_[3]);
      //corners of full tile (which may not be fully shown)
      fullTileCorners_ = new Point2D.Double[4];
      fullTileCorners_[0] = new Point2D.Double();
      fullTileCorners_[1] = new Point2D.Double();
      fullTileCorners_[2] = new Point2D.Double();
      fullTileCorners_[3] = new Point2D.Double();
      transform.transform(new Point2D.Double(-width / 2, -height / 2), fullTileCorners_[0]);
      transform.transform(new Point2D.Double(-width / 2, height / 2), fullTileCorners_[1]);
      transform.transform(new Point2D.Double(width / 2, height / 2), fullTileCorners_[2]);
      transform.transform(new Point2D.Double(width / 2, -height / 2), fullTileCorners_[3]);

      gridCol_ = col;
      gridRow_ = row;
      xyName_ = Engine.getCore().getXYStageDevice();
   }
   
   public boolean isInGrid() {
      return inGrid_;
   }
   
   //OVerride these methods so positions can be compared
   @Override
   public boolean equals(Object o) {
      if (this == o) {
         return true;
      }
      if (o == null || getClass() != o.getClass()) {
         return false;
      }

      return center_.equals(((XYStagePosition) o).center_);
   }

   @Override
   public int hashCode() {
      return Objects.hash(center_);
   }
   
   public long getGridRow() {
      return gridRow_;
   }
   
   public long getGridCol() {
      return gridCol_;
   }
   
   public Point2D.Double getCenter() {
      return center_;
   }
   
   public Point2D.Double[] getDisplayedTileCorners() {
      return displayedTileCorners_;
   }
   
   public Point2D.Double[] getFullTileCorners() {
      return fullTileCorners_;
   }
   
   public String getName() {
      return label_;
   }
   
   public void setName(String newName) {
      label_ = newName;
   }
   
   public String getXYDevice() {
      return xyName_;
   }
   
   public JSONObject toJSON() {
      try {
         JSONObject coordinates = new JSONObject();
         JSONArray xy = new JSONArray();
         xy.put(center_.x);
         xy.put(center_.y);
         coordinates.put(xyName_, xy);
         JSONObject pos = new JSONObject();
         pos.put("DeviceCoordinatesUm", coordinates);
         pos.put("GridColumnIndex", gridCol_);
         pos.put("GridRowIndex", gridRow_);
         pos.put("Properties", new JSONObject());
         pos.put("Label", label_);
         return pos;
      } catch (Exception e) {
         throw new RuntimeException("Couldn't create XY position JSONOBject");
      }
   }
   
   
   /**
    * Convenience method for creating a grid of positions
    * @param tileOverlapPercent
    * @return 
    */
   public static ArrayList<XYStagePosition> createGrid(Point2D.Double center, 
           double tileOverlapFraction, int numRows, int numCols) {
         AffineTransform transform = affineTransformUtils.getAffineTransform(center.x, center.y);
         ArrayList<XYStagePosition> positions = new ArrayList<XYStagePosition>();
         int fullTileWidth = (int) Engine.getCore().getImageWidth();
         int fullTileHeight = (int) Engine.getCore().getImageHeight();
         int overlapX = (int) (Engine.getCore().getImageWidth() * tileOverlapFraction);
         int overlapY = (int) (Engine.getCore().getImageHeight() * tileOverlapFraction);
         int tileWidthMinusOverlap = fullTileWidth - overlapX;
         int tileHeightMinusOverlap = fullTileHeight - overlapY;
         for (int col = 0; col < numCols; col++) {
            double xPixelOffset = (col - (numCols - 1) / 2.0) * tileWidthMinusOverlap;
            //add them in a snaky order
                if (col % 2 == 0) {
                    for (int row = 0; row < numRows; row++) {
                        double yPixelOffset = (row - (numRows - 1) / 2.0) * tileHeightMinusOverlap;
                        Point2D.Double pixelPos = new Point2D.Double(xPixelOffset, yPixelOffset);
                        Point2D.Double stagePos = new Point2D.Double();
                        transform.transform(pixelPos, stagePos);
                        AffineTransform posTransform = affineTransformUtils.getAffineTransform(stagePos.x, stagePos.y);
                        positions.add(new XYStagePosition(stagePos,
                                fullTileWidth, fullTileHeight, 
                                overlapX, overlapY, row, col, posTransform));
                    }
                } else {  
                    for (int row = numRows - 1; row >= 0; row--) {
                        double yPixelOffset = (row - (numRows - 1) / 2.0) * tileHeightMinusOverlap;
                        Point2D.Double pixelPos = new Point2D.Double(xPixelOffset, yPixelOffset);
                        Point2D.Double stagePos = new Point2D.Double();
                        transform.transform(pixelPos, stagePos);
                        AffineTransform posTransform = affineTransformUtils.getAffineTransform( stagePos.x, stagePos.y);
                        positions.add(new XYStagePosition(stagePos,
                                fullTileWidth, fullTileHeight, 
                                overlapX, overlapY, row, col, posTransform));
                    }
                }
         }    
         return positions;
   }

}
