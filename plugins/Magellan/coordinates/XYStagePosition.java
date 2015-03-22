/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package coordinates;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import mmcorej.CMMCore;
import org.json.JSONArray;
import org.json.JSONObject;
import org.micromanager.MMStudio;
import org.micromanager.utils.ReportingUtils;

/**
 * Immutable object representing single XY stage position
 * @author Henry
 */
public class XYStagePosition {
   
   private final String label_;
   private final Point2D.Double center_;
   private final Point2D.Double[] displayedTileCorners_;
   private final Point2D.Double[] fullTileCorners_;
   private final int gridRow_, gridCol_;
   
   public XYStagePosition(Point2D.Double stagePosCenter, int displayTileWidth, int displayTileHeight, 
           int fullTileWidth, int fullTileHeight, int row, int col, String pixelSizeConfig) {
      label_ = "Grid_" + col + "_" + row;
      center_ = stagePosCenter;
      AffineTransform transform = AffineUtils.getAffineTransform(pixelSizeConfig, center_.x, center_.y);
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
   
   public int getGridRow() {
      return gridRow_;
   }
   
   public int getGridCol() {
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
         CMMCore core = MMStudio.getInstance().getCore();

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
         ReportingUtils.showError("Couldn't create XY position JSONOBject");
         return null;
      }
   }

}
