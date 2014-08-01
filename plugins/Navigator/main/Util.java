package main;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
import java.awt.Point;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.MMStudio;
import org.micromanager.api.MultiStagePosition;
import org.micromanager.api.PositionList;
import org.micromanager.api.ScriptInterface;
import org.micromanager.api.StagePosition;
import org.micromanager.utils.JavaUtils;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author Henry
 */
public class Util {
   
   
   public static String DL_OFFSET_KEY = "Depth list offset";

   public static void setDepthListOffset(int posIndex, int offset) {
      ScriptInterface app = MMStudio.getInstance();
      try {
         app.getPositionList().getPosition(posIndex).setProperty(DL_OFFSET_KEY,""+offset);
      } catch (MMScriptException ex) {
         ReportingUtils.showError("Couldn't get depth list offset");
      }
   }
   
   public static int getDepthListOffset(int posIndex) {
      if (posIndex == -1) {
         return 0;
      }
      ScriptInterface app = MMStudio.getInstance();
      try {
         return Integer.parseInt(app.getPositionList().getPosition(posIndex).getProperty(DL_OFFSET_KEY));
      } catch (MMScriptException ex) {
         ReportingUtils.showError("Couldn't get depth list offset");
         return 0;
      }
   }

//   public static Point2D.Double stagePositionFromPixelPosition(
//           double xPixelDispFromCenter, double yPixelDispFromCenter) {
//      try {
//         //get coordinates of center of exisitng grid
//         String xyStage = MMStudio.getInstance().getCore().getXYStageDevice();
//
//         //row column map to coordinates for exisiting stage positiions
//         Point2D.Double[][] coordinates = new Point2D.Double[numCols_][numRows_];
//         for (int i = 0; i < positionList_.length(); i++) {
//            int colInd = (int) positionList_.getJSONObject(i).getLong("GridColumnIndex");
//            int rowInd = (int) positionList_.getJSONObject(i).getLong("GridRowIndex");
//            JSONArray coords = positionList_.getJSONObject(i).getJSONObject("DeviceCoordinatesUm").getJSONArray(xyStage);
//            coordinates[colInd][rowInd] = new Point2D.Double(coords.getDouble(0), coords.getDouble(1));
//         }
//
//         //find stage coordinate of center of existing grid
//         double currentCenterX, currentCenterY;
//         if (coordinates.length % 2 == 0 && coordinates[0].length % 2 == 0) {
//            //even number of tiles in both directions
//            currentCenterX = 0.25 * coordinates[numCols_ / 2 - 1][numRows_ / 2 - 1].x + 0.25 * coordinates[numCols_ / 2 - 1][numRows_ / 2].x
//                    + 0.25 * coordinates[numCols_ / 2][numRows_ / 2 - 1].x + 0.25 * coordinates[numCols_ / 2][numRows_ / 2].x;
//            currentCenterY = 0.25 * coordinates[numCols_ / 2 - 1][numRows_ / 2 - 1].y + 0.25 * coordinates[numCols_ / 2 - 1][numRows_ / 2].y
//                    + 0.25 * coordinates[numCols_ / 2][numRows_ / 2 - 1].y + 0.25 * coordinates[numCols_ / 2][numRows_ / 2].y;
//         } else if (coordinates.length % 2 == 0) {
//            //even number of columns
//            currentCenterX = 0.5 * coordinates[numCols_ / 2 - 1][numRows_ / 2].x + 0.5 * coordinates[numCols_ / 2][numRows_ / 2].x;
//            currentCenterY = 0.5 * coordinates[numCols_ / 2 - 1][numRows_ / 2].y + 0.5 * coordinates[numCols_ / 2][numRows_ / 2].y;
//         } else if (coordinates[0].length % 2 == 0) {
//            //even number of rows
//            currentCenterX = 0.5 * coordinates[numCols_ / 2][numRows_ / 2 - 1].x + 0.5 * coordinates[numCols_ / 2][numRows_ / 2].x;
//            currentCenterY = 0.5 * coordinates[numCols_ / 2][numRows_ / 2 - 1].y + 0.5 * coordinates[numCols_ / 2][numRows_ / 2].y;
//         } else {
//            //odd number of both
//            currentCenterX = coordinates[numCols_ / 2][numRows_ / 2].x;
//            currentCenterY = coordinates[numCols_ / 2][numRows_ / 2].y;
//         }
//
//         //use affine transform to convert to stage coordinate of center of new grid
//         AffineTransform transform = null;
//         Preferences prefs = Preferences.userNodeForPackage(MMStudio.class);
//         try {
//            transform = (AffineTransform) JavaUtils.getObjectFromPrefs(prefs, "affine_transform_"
//                    + MMStudio.getInstance().getCore().getCurrentPixelSizeConfig(), null);
//            //set map origin to current stage position
//            double[] matrix = new double[6];
//            transform.getMatrix(matrix);
//            matrix[4] = currentCenterX;
//            matrix[5] = currentCenterY;
//            transform = new AffineTransform(matrix);
//         } catch (Exception ex) {
//            ReportingUtils.logError(ex);
//            ReportingUtils.showError("Couldnt get affine transform");
//         }
//
//         //convert pixel displacement of center of new grid to new center stage position
//         Point2D.Double pixelPos = new Point2D.Double(xPixelDispFromCenter, yPixelDispFromCenter);
//         Point2D.Double stagePos = new Point2D.Double();
//         transform.transform(pixelPos, stagePos);
//         return stagePos;
//      } catch (Exception e) {
//         ReportingUtils.showError("Couldn't convert pixel coordinates to stage coordinates");
//         return null;
//      }
//   }

   public static AffineTransform getAffineTransform(double xCenter, double yCenter) {
      //Get affine transform
      Preferences prefs = Preferences.userNodeForPackage(MMStudio.class);

      AffineTransform transform = null;
      try {
         transform = JavaUtils.getObjectFromPrefs(prefs, "affine_transform_"
                 + MMStudio.getInstance().getMMCore().getCurrentPixelSizeConfig(), (AffineTransform) null);
         //set map origin to current stage position
         double[] matrix = new double[6];
         transform.getMatrix(matrix);
         matrix[4] = xCenter;
         matrix[5] = yCenter;
         return new AffineTransform(matrix);
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
         ReportingUtils.showError("Couldnt get affine transform");
         return null;
      }
   }

   /**
    *
    * @param xCenter grid center x (stage coordinates)
    * @param yCenter grid center y (stage coordinates)
    * @param numCols
    * @param numRows
    * @param pixelOverlapX
    * @param pixelOverlapY
    */
   public static void createGrid(double xCenter, double yCenter, int numCols, int numRows, int pixelOverlapX, int pixelOverlapY) {
      ScriptInterface app = MMStudio.getInstance();
      AffineTransform transform = getAffineTransform(xCenter, yCenter);

      long height = app.getMMCore().getImageHeight();
      long width = app.getMMCore().getImageWidth();
      ArrayList<MultiStagePosition> positions = new ArrayList<MultiStagePosition>();
      //due to affine transform, xindex and yindex correspond to image space
      for (int col = 0; col < numCols; col++) {
         double xPixelOffset = (col - (numCols - 1) / 2.0) * (width - pixelOverlapX);
         for (int row = 0; row < numRows; row++) {
            double yPixelOffset = (row - (numRows - 1) / 2.0) * (height - pixelOverlapY);

            Point2D.Double pixelPos = new Point2D.Double(xPixelOffset, yPixelOffset);
            Point2D.Double stagePos = new Point2D.Double();
            transform.transform(pixelPos, stagePos);

            MultiStagePosition mpl = new MultiStagePosition();
            StagePosition sp = new StagePosition();
            sp.numAxes = 2;
            sp.stageName = app.getMMCore().getXYStageDevice();
            sp.x = stagePos.x;
            sp.y = stagePos.y;
            mpl.add(sp);

            //label should be Grid_(x index of tile)_(y index of tile) (in image space)
            String lab = new String("Grid_" + col + "_" + row);

            mpl.setLabel(lab);
            //row, column (in image space)
            mpl.setGridCoordinates(row, col);
            positions.add(mpl);            
         }
      }
   
      try {
         PositionList list = app.getPositionList();
         list.clearAllPositions();
         for (MultiStagePosition p : positions ) {
            list.addPosition(p);
         }

         list.notifyChangeListeners();
      } catch (MMScriptException e) {
         ReportingUtils.showError(e.getMessage());
      }
   }

   public static int getPosIndex(JSONArray positionList, int row, int col) {
      for (int i = 0; i < positionList.length(); i++) {
         try {
            long colInd = positionList.getJSONObject(i).getLong("GridColumnIndex");
            long rowInd = positionList.getJSONObject(i).getLong("GridRowIndex");
            if (rowInd == row && colInd == col) {
               return i;
            }
         } catch (JSONException ex) {
            ReportingUtils.showError("Couldn't read position list");
         }
      }
      return -1;
   }
   
   public static int rowFromPosIndex(JSONArray positionList, int index) {
      try {
         return (int) positionList.getJSONObject(index).getLong("GridRowIndex");
      } catch (JSONException ex) {
         ex.printStackTrace();
         ReportingUtils.showError("Couldnt find tile indices");
         return 0;
      }
   }

   public static int colFromPosIndex(JSONArray positionList, int index) {
      try {
         return (int) positionList.getJSONObject(index).getLong("GridColumnIndex");
      } catch (JSONException ex) {
         ex.printStackTrace();
         ReportingUtils.showError("Couldnt find tile indices");
         return 0;
      }
   }

   public static int getStitchedImageLength(int numTiles, int tileLength, int overlap) {
      return numTiles * tileLength - (numTiles - 1) * overlap;
   }

   //get the tile index from the pixel index in larger stitched image
   public static int stitchedPixelToTile(int i, int overlap, int tileLength, int numTiles) {
      int tile;
      if (i < tileLength - overlap / 2) {
         tile = 0;
      } else if (i > getStitchedImageLength(numTiles, tileLength, overlap) - (tileLength - overlap / 2)) {
         tile = numTiles - 1;
      } else {
         tile = 1 + (i - (tileLength - overlap / 2)) / (tileLength - overlap);
      }
      return tile;
   }
   
   public static int tileToLastStitchedPixel(int tile, int overlap, int tileLength, int numTiles, int imageLength) {
      if (tile == numTiles - 1) {
         return imageLength - 1;
      } else {
         return tile * (tileLength - overlap) + (tileLength - overlap / 2) - 1;
      }
   }
   
   //get the index of first pixel in the stitched image of the given tile
   public static int tileToFirstStitchedPixel(int tile, int overlap, int tileLength, int numTiles, int imageLength) {
      if (tile == 0) {
         return 0;
      } else if (tile == numTiles -1) {
         return imageLength - (tileLength - overlap / 2) + 1;
      } else {
         return (tile - 1) * (tileLength - overlap) + (tileLength - overlap / 2);
      }
      
   }
   
   //get the stiched pixel index from the tile pixel index
   public static int tilePixelToStitchedPixel(int tilePixel, int overlap, int tileLength, int tileIndex) {
      if (tileIndex == 0) {
         return tilePixel; //0th tile
      }
      int stitchedPixel = tileLength - overlap / 2; //account for 0th tile
      stitchedPixel += (tileIndex - 1) * (tileLength - overlap); //account for additional tiles
      stitchedPixel += tilePixel - overlap / 2; //account for location in particular tile
      return stitchedPixel;
   }
   
   public static int stitchedPixelToTilePixel(int stitchedPixel, int overlap, int tileLength, int numTiles) {      
      if (stitchedPixel < tileLength - overlap / 2) {
         return stitchedPixel; //in 0th tile
      } 
      stitchedPixel -= tileLength - overlap / 2;
      int tilePixel = stitchedPixel % (tileLength - overlap);
      if ( stitchedPixel / (tileLength - overlap) + 1 == numTiles ) {
         //Add in correction for trailing edge of last tile
         tilePixel += tileLength - overlap;
      }
      //convert to tile index by adding leading edge
      return tilePixel + overlap / 2;
   }


   
}
