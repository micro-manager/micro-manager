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
package org.micromanager.acqj.api.mda;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Objects;
import mmcorej.CMMCore;
import org.json.JSONArray;
import org.json.JSONObject;
import org.micromanager.acqj.internal.acqengj.Engine;
import org.micromanager.acqj.internal.acqengj.Engine;
import org.micromanager.acqj.internal.acqengj.AffineTransformUtils;
import org.micromanager.acqj.internal.acqengj.AffineTransformUtils;

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
   private Integer gridRow_ = null, gridCol_ = null;
   private final boolean inGrid_;

   /**
    * for abitrary positions not neccessarily in a grid
    */
   public XYStagePosition(Point2D.Double stagePosCenter) {
      label_ = "";
      center_ = stagePosCenter;
      if (Engine.getCore().getXYStageDevice().equals("")) {
         throw new RuntimeException("Core XY stage device undefined");
      }
      inGrid_ = false;
   }

   public XYStagePosition(Point2D.Double stagePosCenter, int gridRow, int gridCol) {
      label_ = "";
      center_ = stagePosCenter;
      if (Engine.getCore().getXYStageDevice().equals("")) {
         throw new RuntimeException("Core XY stage device undefined");
      }
      inGrid_ = false;
      gridCol_ = gridCol;
      gridRow_ = gridRow;
      label_ = "Grid_" + gridCol + "_" + gridRow;
   }

   /**
    * If positions are laid out in a grid, get the stage coordinates of the
    * corners of the visible part of each tile (i.e. half of the overlap with
    * other tiles)
    *
    * @return
    */
   public Point2D.Double[] getVisibleTileCorners(int overlapX, int overlapY) {
      //Assume the current camera width and height, could change this
      int fullTileWidth = (int) Engine.getCore().getImageWidth();
      int fullTileHeight = (int) Engine.getCore().getImageHeight();
      int tileWidthMinusOverlap = fullTileWidth - overlapX;
      int tileHeightMinusOverlap = fullTileHeight - overlapY;

      Point2D.Double[] corners = new Point2D.Double[4];
      AffineTransform transform = AffineTransformUtils.getAffineTransform(center_.x, center_.y);
      for (int i = 0; i < 4; i++) {
         Point2D.Double pixelPos;
         if (i == 0) {
            pixelPos = new Point2D.Double(tileHeightMinusOverlap / 2, tileWidthMinusOverlap / 2);
         } else if (i == 1) {
            pixelPos = new Point2D.Double(-tileHeightMinusOverlap / 2, tileWidthMinusOverlap / 2);
         } else if (i == 2) {
            pixelPos = new Point2D.Double(-tileHeightMinusOverlap / 2, -tileWidthMinusOverlap / 2);
         } else {
            pixelPos = new Point2D.Double(tileHeightMinusOverlap / 2, -tileWidthMinusOverlap / 2);
         }
         Point2D.Double stagePos = new Point2D.Double();
         transform.transform(pixelPos, stagePos);
         corners[i] = stagePos;
      }
      return corners;
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

   public Integer getGridRow() {
      return gridRow_;
   }

   public Integer getGridCol() {
      return gridCol_;
   }

   public Point2D.Double getCenter() {
      return center_;
   }

   public String getName() {
      return label_;
   }

   public void setName(String newName) {
      label_ = newName;
   }

   public JSONObject toJSON(CMMCore core) {
      try {
         JSONObject coordinates = new JSONObject();
         JSONArray xy = new JSONArray();
         xy.put(center_.x);
         xy.put(center_.y);
         coordinates.put(core.getXYStageDevice(), xy);
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

}
