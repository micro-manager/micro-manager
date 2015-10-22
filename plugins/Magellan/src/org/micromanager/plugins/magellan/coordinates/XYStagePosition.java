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

package org.micromanager.plugins.magellan.coordinates;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import org.micromanager.plugins.magellan.json.JSONArray;
import org.micromanager.plugins.magellan.json.JSONObject;
import org.micromanager.plugins.magellan.main.Magellan;
import org.micromanager.plugins.magellan.misc.Log;
import mmcorej.CMMCore;

/**
 * Immutable object representing single XY stage position
 * @author Henry
 */
public class XYStagePosition {
   
   private final String label_;
   private final Point2D.Double center_;
   private final Point2D.Double[] displayedTileCorners_;
   private final Point2D.Double[] fullTileCorners_;
   private final long gridRow_, gridCol_;
   
   /**
    * for opening prevously acquired data
    */
   public XYStagePosition(Point2D.Double stagePosCenter, long row, long col) {
      label_ = "Grid_" + col + "_" + row;
      center_ = stagePosCenter;
      gridCol_ = col;
      gridRow_ = row;
      displayedTileCorners_ = null;
      fullTileCorners_ = null;
   }

   /**
    * 
    * @param transform -- must be centered at current stage pos 
    */
   public XYStagePosition(Point2D.Double stagePosCenter, int displayTileWidth, int displayTileHeight, 
           int fullTileWidth, int fullTileHeight, long row, long col, AffineTransform transform) {
      
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
      transform.transform(new Point2D.Double(-fullTileWidth / 2, -fullTileHeight / 2), fullTileCorners_[0]);
      transform.transform(new Point2D.Double(-fullTileWidth / 2, fullTileHeight / 2), fullTileCorners_[1]);
      transform.transform(new Point2D.Double(fullTileWidth / 2, fullTileHeight / 2), fullTileCorners_[2]);
      transform.transform(new Point2D.Double(fullTileWidth / 2, -fullTileHeight / 2), fullTileCorners_[3]);

      gridCol_ = col;
      gridRow_ = row;
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
   
   public JSONObject getMMPosition() {
      try {
         //make intitial position list, with current position and 0,0 as coordinates
         CMMCore core = Magellan.getCore();

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
         Log.log("Couldn't create XY position JSONOBject");
         return null;
      }
   }

}
