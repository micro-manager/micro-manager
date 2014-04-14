/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.imaging100x.twophoton;

import java.awt.Point;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.micromanager.MMStudioMainFrame;
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
      ScriptInterface app = MMStudioMainFrame.getInstance();
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
      ScriptInterface app = MMStudioMainFrame.getInstance();
      try {
         return Integer.parseInt(app.getPositionList().getPosition(posIndex).getProperty(DL_OFFSET_KEY));
      } catch (MMScriptException ex) {
         ReportingUtils.showError("Couldn't get depth list offset");
         return 0;
      }
   }

   public static void createGrid(double xCenter, double yCenter, int xSize, int ySize, int pixelOverlapX, int pixelOverlapY) {      
      ScriptInterface app = MMStudioMainFrame.getInstance();
      //Get affine transform
      Preferences prefs = Preferences.userNodeForPackage(MMStudioMainFrame.class);

      AffineTransform transform = null;
      try {
         transform = (AffineTransform) JavaUtils.getObjectFromPrefs(prefs, "affine_transform_" + app.getMMCore().getCurrentPixelSizeConfig(), null);
         //set map origin to current stage position
         double[] matrix = new double[6];
         transform.getMatrix(matrix);
         matrix[4] = xCenter;
         matrix[5] = yCenter;
         transform = new AffineTransform(matrix);
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
         ReportingUtils.showError("Couldnt get affine transform");
      }

      long height = app.getMMCore().getImageHeight();
      long width = app.getMMCore().getImageWidth();
      ArrayList<MultiStagePosition> positions = new ArrayList<MultiStagePosition>();
      //due to affine transform, xindex and yindex correspond to image space
      for (int xIndex = 0; xIndex < xSize; xIndex++) {
         double xPixelOffset = (xIndex - (xSize - 1) / 2.0) * (width - pixelOverlapX) ;
         for (int yIndex = 0; yIndex < ySize; yIndex++) {
            double yPixelOffset = (yIndex - (ySize - 1) / 2.0) * (height - pixelOverlapY);
            //account for angle between stage axes and image axes
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
             
            int row = yIndex, col = xIndex;

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
         for (MultiStagePosition p : positions) {
            list.addPosition(p);
            p.setProperty(DL_OFFSET_KEY, "0");
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
