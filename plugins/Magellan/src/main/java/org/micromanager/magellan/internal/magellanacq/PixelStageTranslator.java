/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.magellan.internal.magellanacq;

import java.awt.Point;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.acqj.api.AcqEngMetadata;
import org.micromanager.acqj.api.XYStagePosition;
import org.micromanager.magellan.internal.coordinates.NoPositionsDefinedYetException;
import org.micromanager.magellan.internal.main.Magellan;
import org.micromanager.multiresstorage.PositionManager;

/**
 *
 * @author henrypinkard
 */
public class PixelStageTranslator {

   private static final String COORDINATES_KEY = "DeviceCoordinatesUm";
   
   private AffineTransform affine_;
   private String xyStageName_;
   private int tileWidth_, tileHeight_, displayTileHeight_, displayTileWidth_, overlapX_, overlapY_;
   private JSONArray positionList_; //all access to positionlist in synchronized methods for thread safety

   public PixelStageTranslator(AffineTransform transform, int width, 
           int height, int overlapX, int overlapY, JSONArray initialPosList) {
      affine_ = transform;
      xyStageName_ = Magellan.getCore().getXYStageDevice();
      tileWidth_ = width;
      tileHeight_ = height;
      overlapX_ = overlapX;
      overlapY_ = overlapY;
      displayTileWidth_ = tileWidth_ - overlapX_;
      displayTileHeight_ = tileHeight_ - overlapY_;
      positionList_ = initialPosList;
      overlapX_ = overlapX;
      overlapY_ = overlapY;
   }
   
   /**
    *
    * @param xAbsolute x coordinate in the full Res stitched image
    * @param yAbsolute y coordinate in the full res stitched image
    * @return stage coordinates of the given pixel position
    */
   public synchronized Point2D.Double getStageCoordsFromPixelCoords(long xAbsolute, long yAbsolute) {
      try {
         if (positionList_.length() == 0) {
            throw new NoPositionsDefinedYetException();
         }
         JSONObject existingPosition = positionList_.getJSONObject(0);
         double exisitngX = existingPosition.getJSONObject(COORDINATES_KEY).getJSONArray(xyStageName_).getDouble(0);
         double exisitngY = existingPosition.getJSONObject(COORDINATES_KEY).getJSONArray(xyStageName_).getDouble(1);
         long existingRow = AcqEngMetadata.getGridRow(existingPosition);
         long existingColumn = AcqEngMetadata.getGridCol(existingPosition);
         //get pixel displacement from center of the tile we have coordinates for
         long dxPix = (long) (xAbsolute - (existingColumn + 0.5) * displayTileWidth_);
         long dyPix = (long) (yAbsolute - (existingRow + 0.5) * displayTileHeight_);

         Point2D.Double stagePos = new Point2D.Double();
         double[] mat = new double[4];
         affine_.getMatrix(mat);
         AffineTransform transform = new AffineTransform(mat[0], mat[1], mat[2], mat[3], exisitngX, exisitngY);
         transform.transform(new Point2D.Double(dxPix, dyPix), stagePos);
         return stagePos;
      } catch (JSONException ex) {
         ex.printStackTrace();;
         throw new RuntimeException("Problem with current position metadata");
      }

   }

   /**
    *
    * @param stageCoords x and y coordinates of image in stage space
    * @return absolute, full resolution pixel coordinate of given stage posiiton
    */
   public synchronized Point getPixelCoordsFromStageCoords(double stageX, double stageY) {
      try {
         JSONObject existingPosition = new JSONObject(positionList_.getJSONObject(0).toString());
         double exisitngX = existingPosition.getJSONObject(COORDINATES_KEY).getJSONArray(xyStageName_).getDouble(0);
         double exisitngY = existingPosition.getJSONObject(COORDINATES_KEY).getJSONArray(xyStageName_).getDouble(1);
         long existingRow = AcqEngMetadata.getGridRow(existingPosition);
         long existingColumn = AcqEngMetadata.getGridCol(existingPosition);

         //get stage displacement from center of the tile we have coordinates for
         double dx = stageX - exisitngX;
         double dy = stageY - exisitngY;
         AffineTransform transform = (AffineTransform) affine_.clone();
         Point2D.Double pixelOffset = new Point2D.Double(); // offset in number of pixels from the center of this tile
         transform.inverseTransform(new Point2D.Double(dx, dy), pixelOffset);
         //Add pixel offset to pixel center of this tile to get absolute pixel position
         int xPixel = (int) ((existingColumn + 0.5) * displayTileWidth_ + pixelOffset.x);
         int yPixel = (int) ((existingRow + 0.5) * displayTileHeight_ + pixelOffset.y);
         return new Point(xPixel, yPixel);
      } catch (JSONException ex) {
         ex.printStackTrace();
         throw new RuntimeException("Problem with current position metadata");
      } catch (NoninvertibleTransformException e) {
         throw new RuntimeException("Problem using affine transform to convert stage coordinates to pixel coordinates");
      }
   }

   public synchronized XYStagePosition getXYPosition(int index) {
      try {
         JSONArray jsonPos = new JSONArray(positionList_.getJSONObject(index).getJSONObject(
                 "DeviceCoordinatesUm").getJSONArray(xyStageName_).toString());
         Point2D.Double posCenter = new Point2D.Double(jsonPos.getDouble(0), jsonPos.getDouble(1));
         //Full 
         double[] mat = new double[4];
         affine_.getMatrix(mat);
         AffineTransform transform = new AffineTransform(mat[0], mat[1], mat[2], mat[3], posCenter.x, posCenter.y);
         int gridRow = (int) MagellanMD.getGridRow(positionList_.getJSONObject(index));
         int gridCol = (int) MagellanMD.getGridCol(positionList_.getJSONObject(index));
         return new XYStagePosition(posCenter, tileWidth_, tileHeight_, 
                 overlapX_, overlapY_, gridRow, gridCol, transform);
      } catch (JSONException ex) {
         throw new RuntimeException("problem with position metadata");
      }
   }

   public int getFullResPositionIndexFromStageCoords(double x, double y) {
      Point pixelCoords = getPixelCoordsFromStageCoords(x, y);
      long rowIndex = Math.round((double) (pixelCoords.y - displayTileHeight_ / 2) / (double) displayTileHeight_);
      long colIndex = Math.round((double) (pixelCoords.x - displayTileWidth_ / 2) / (double) displayTileWidth_);
      int[] posIndex = getPositionIndices(new int[]{(int) rowIndex}, new int[]{(int) colIndex});
      return posIndex[0];
   }

   public List<XYStagePosition> getPositionList() {
      ArrayList<XYStagePosition> list = new ArrayList<XYStagePosition>();
      for (int i = 0; i < positionList_.length(); i++) {
         list.add(getXYPosition(i));
      }
      return list;
   }
   
   
   /**
    * Calculate the x and y stage coordinates of a new position given its row
    * and column and the existing metadata for another position
    *
    * @param row
    * @param col
    * @param existingPosition
    * @return
    */
   private synchronized Point2D.Double getStagePositionCoordinates(int row, int col, int pixelOverlapX, int pixelOverlapY) {
      try {
         if (positionList_.length() == 0) {
            try {
               //create position 0 based on current XY stage position--happens at start of explore acquisition
               return new Point2D.Double(Magellan.getCore().getXPosition(xyStageName_), Magellan.getCore().getYPosition(xyStageName_));
            } catch (Exception ex) {
               throw new RuntimeException("Couldn't create position 0");
            }
         } else {
            JSONObject existingPosition = positionList_.getJSONObject(0);

            double exisitngX = existingPosition.getJSONObject(COORDINATES_KEY).getJSONArray(xyStageName_).getDouble(0);
            double exisitngY = existingPosition.getJSONObject(COORDINATES_KEY).getJSONArray(xyStageName_).getDouble(1);
            long existingRow = AcqEngMetadata.getGridRow(existingPosition);
            long existingColumn = AcqEngMetadata.getGridCol(existingPosition);

            double xPixelOffset = (col - existingColumn) * (Magellan.getCore().getImageWidth() - pixelOverlapX);
            double yPixelOffset = (row - existingRow) * (Magellan.getCore().getImageHeight() - pixelOverlapY);

            Point2D.Double stagePos = new Point2D.Double();
            double[] mat = new double[4];
            affine_.getMatrix(mat);
            AffineTransform transform = new AffineTransform(mat[0], mat[1], mat[2], mat[3], exisitngX, exisitngY);
            transform.transform(new Point2D.Double(xPixelOffset, yPixelOffset), stagePos);
            return stagePos;
         }

      } catch (JSONException ex) {
         ex.printStackTrace();
         throw new RuntimeException("Problem with current position metadata");
      }
   }
   
   
     
   private JSONObject createPosition(int row, int col) {
      try {
         JSONArray xy = new JSONArray();
         Point2D.Double stageCoords = getStagePositionCoordinates(row, col, overlapX_, overlapY_);

         JSONObject coords = new JSONObject();
         xy.put(stageCoords.x);
         xy.put(stageCoords.y);
         coords.put(xyStageName_, xy);
         JSONObject pos = new JSONObject();
         pos.put(COORDINATES_KEY, coords);
         AcqEngMetadata.setGridCol(pos, col);
         AcqEngMetadata.setGridRow(pos, row);

         return pos;
      } catch (Exception e) {
         throw new RuntimeException("Couldn't create XY position");
      }
   }
   
     /**
    * Return the position indices for the positions at the specified rows, cols.
    * If no position exists at this location, create one and return its index
    *
    * @param rows
    * @param cols
    * @return
    */
   public synchronized int[] getPositionIndices(int[] rows, int[] cols) {
      try {
         int[] posIndices = new int[rows.length];
         boolean newPositionsAdded = false;

         outerloop:
         for (int h = 0; h < posIndices.length; h++) {
            //check if position is already present in list, and if so, return its index
            for (int i = 0; i < positionList_.length(); i++) {
               if (MagellanMD.getGridRow(positionList_.getJSONObject(i)) == rows[h]
                       && MagellanMD.getGridCol(positionList_.getJSONObject(i)) == cols[h]) {
                  //we already have position, so return its index
                  posIndices[h] = i;
                  continue outerloop;
               }
            }
            //add this position to list
            positionList_.put(createPosition(rows[h], cols[h]));
            newPositionsAdded = true;

            posIndices[h] = positionList_.length() - 1;
         }
         //if size of grid wasn't expanded, return here
         if (!newPositionsAdded) {
            return posIndices;
         }

         return posIndices;
      } catch (JSONException e) {
         throw new RuntimeException("Problem with position metadata");
      }
   }

   public int getTileHeight() {
      return tileHeight_;
   }

   public int getTileWidth() {
      return tileWidth_;
   }
   


}
