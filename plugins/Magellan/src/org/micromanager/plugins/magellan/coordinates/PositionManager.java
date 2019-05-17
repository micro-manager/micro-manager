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
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.util.TreeMap;
import java.util.TreeSet;
import org.micromanager.plugins.magellan.bidc.JavaLayerImageConstructor;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.micromanager.plugins.magellan.json.JSONArray;
import org.micromanager.plugins.magellan.json.JSONException;
import org.micromanager.plugins.magellan.json.JSONObject;
import org.micromanager.plugins.magellan.main.Magellan;
import org.micromanager.plugins.magellan.misc.Log;
import org.micromanager.plugins.magellan.misc.LongPoint;
import org.micromanager.plugins.magellan.misc.MD;



/*
 * Class to manage XY positions, their indices, and positions within a grid at
 * multiple resolution levels
 */
public class PositionManager {

   private static final String COORDINATES_KEY = "DeviceCoordinatesUm";
   private static final String PROPERTIES_KEY = "Properties";
   private static final String MULTI_RES_NODE_KEY = "MultiResPositionNode";
   
   
   private AffineTransform affine_;
   private JSONArray positionList_; //all access to positionlist in synchronized methods for thread safety
   private int minRow_, maxRow_, minCol_, maxCol_; //For the lowest resolution level
   private  String xyStageName_;
   //Map of Res level to set of nodes
   private TreeMap<Integer,TreeSet<MultiResPositionNode>> positionNodes_; 
   private int displayTileWidth_, displayTileHeight_;
   private int fullTileWidth_, fullTileHeight_;
   private int overlapX_, overlapY_;
   
   /**
    * constructor to read data from disk
    */
    public PositionManager(AffineTransform transform, JSONObject summaryMD, int displayTileWidth, int displayTileHeight,
           int fullTileWidth, int fullTileHeight, int overlapX, int overlapY, JSONArray initialPosList, int maxResLevel) {
      try {
         xyStageName_ = summaryMD.getString("Core-XYStage");
      } catch (JSONException ex) {
         throw new RuntimeException("No XY stage name in summary metadata");
      }
      affine_ = transform;
      positionNodes_ = new TreeMap<Integer,TreeSet<MultiResPositionNode>>();
      minRow_ = 0; maxRow_ = 0;
       minCol_ = 0;
       maxRow_ = 0;
       positionList_ = initialPosList;
       updateMinAndMaxRowsAndCols();
       updateLowerResolutionNodes(maxResLevel); //make sure nodes created for all preexisiting positions
       displayTileWidth_ = displayTileWidth;
       displayTileHeight_ = displayTileHeight;
      fullTileWidth_ = fullTileWidth;
      fullTileHeight_ = fullTileHeight;
      overlapX_ = overlapX; 
      overlapY_ = overlapY;
   }
   
    /**
     * constructor that reads initial position list
     */
   public PositionManager(AffineTransform transform, JSONObject summaryMD, int displayTileWidth, int displayTileHeight,
           int fullTileWidth, int fullTileHeight, int overlapX, int overlapY) {
      try {
         xyStageName_ = summaryMD.getString("Core-XYStage");
      } catch (JSONException ex) {
         throw new RuntimeException("No XY stage name in summary metadata");
      }
      affine_ = transform;
      positionNodes_ = new TreeMap<Integer,TreeSet<MultiResPositionNode>>();
      readRowsAndColsFromPositionList(summaryMD);
      displayTileWidth_ = displayTileWidth;
      displayTileHeight_ = displayTileHeight;
      fullTileWidth_ = fullTileWidth;
      fullTileHeight_ = fullTileHeight;
      overlapX_ = overlapX; 
      overlapY_ = overlapY;
   }
   
   public synchronized String getSerializedPositionList() {
      return positionList_.toString();
   }
   
   public synchronized int getNumPositions() {
      return positionList_.length();
   }

   public synchronized XYStagePosition getXYPosition(int index) {
      try {
         JSONArray jsonPos = positionList_.getJSONObject(index).getJSONObject("DeviceCoordinatesUm").getJSONArray(xyStageName_);
          Point2D.Double posCenter = new Point2D.Double(jsonPos.getDouble(0), jsonPos.getDouble(1));
          //Full 
          double[] mat = new double[4];
          affine_.getMatrix(mat);
          AffineTransform transform = new AffineTransform(mat[0], mat[1], mat[2], mat[3], posCenter.x, posCenter.y);
          return new XYStagePosition(posCenter, displayTileWidth_, displayTileHeight_,
                  fullTileWidth_, fullTileHeight_, (int) getGridRow(index, 0), (int) getGridCol(index, 0), transform);
      } catch (JSONException ex) {
          Log.log("problem with position metadata");
         throw new RuntimeException();
      }
   }
   
   public int getFullResPositionIndexFromStageCoords(double x, double y) {
       LongPoint pixelCoords = getPixelCoordsFromStageCoords(x, y);
       long rowIndex = Math.round((double) (pixelCoords.y_ - displayTileHeight_ /2) / (double) displayTileHeight_);
       long colIndex = Math.round((double) (pixelCoords.x_ - displayTileWidth_ / 2) / (double) displayTileWidth_);
       int[] posIndex = getPositionIndices(new int[]{(int)rowIndex}, new int[]{(int)colIndex});
       return posIndex[0];
   }
   
   public int getNumRows() {
      return 1 + maxRow_ - minRow_;
   }
   
   public int getNumCols() {
      return 1 + maxCol_ - minCol_;              
   }
   
   public int getMinRow() {
      return minRow_;
   }
   
   public int getMinCol() {
      return minCol_;
   }
   
   /**
    * Return the position index of any one of the full res positions corresponding a low res position.
    * There is at least one full res position corresponding to the low res one, but this function makes no
    * guarantees about which one is returned if there are more than one.
    * @param lowResPositionIndex
    * @param resIndex - res index corresponding to the low res position
    * @return position index of full res child of node or -1 if doesn't exist (which shouldn't ever happen)
    */
   public int getFullResPositionIndex(int lowResPositionIndex, int resIndex) {
      for (MultiResPositionNode node : positionNodes_.get(resIndex)) {
         if (node.positionIndex == lowResPositionIndex) {
            while (node.child != null) {
               node = node.child;
            }
            //now we have a full res node that is a descendent
            return node.positionIndex;
         }
      }
      Log.log("Could't find full resolution child of node");
      return -1;
   }

   public synchronized int getLowResPositionIndex(int fullResPosIndex, int resIndex) {
      try {
         MultiResPositionNode node = (MultiResPositionNode) positionList_.getJSONObject(fullResPosIndex).getJSONObject(PROPERTIES_KEY).get(MULTI_RES_NODE_KEY);
         for (int i = 0; i < resIndex; i++) {
            node = node.parent;
         }
         return node.positionIndex;
      } catch (Exception e) {
         e.printStackTrace();
         Log.log("Couldnt read position list correctly", true);
         return 0;
      }
   }
   
   public synchronized long getGridRow(int fullResPosIndex, int resIndex) {
      try {
         MultiResPositionNode node = (MultiResPositionNode) positionList_.getJSONObject(fullResPosIndex).getJSONObject(PROPERTIES_KEY).get(MULTI_RES_NODE_KEY);
         for (int i = 0; i < resIndex; i++) {
            node = node.parent;
         }
         return node.gridRow;
      } catch (JSONException e) {
         e.printStackTrace();
         Log.log("Couldnt read position list correctly", true);
         return 0;
      }
   }

   public synchronized long getGridCol(int fullResPosIndex, int resIndex) {
      try {
         MultiResPositionNode node = (MultiResPositionNode) positionList_.getJSONObject(fullResPosIndex).getJSONObject(PROPERTIES_KEY).get(MULTI_RES_NODE_KEY);
         for (int i = 0; i < resIndex; i++) {
            node = node.parent;
         }
         return node.gridCol;
      } catch (JSONException e) {
         e.printStackTrace();
         Log.log("Couldnt read position list correctly", true);
         return 0;
      }
   }

   /**
    * 
    * @param dsIndex
    * @param rowIndex
    * @param colIndex
    * @return position index given res level or -1 if it doesn't exist
    */
   public int getPositionIndexFromTilePosition(int dsIndex, long rowIndex, long colIndex) {
      MultiResPositionNode nodeToFind = findExisitngNode(dsIndex, rowIndex, colIndex);
      if (nodeToFind != null) {
         return nodeToFind.positionIndex;
      }
      return -1;
   }

   /**
    * Return the position indices for the positions at the specified rows, cols.
    * If no position exists at this location, create one and return its index
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
               if (MD.getGridRow(positionList_.getJSONObject(i)) == rows[h]
                       && MD.getGridCol(positionList_.getJSONObject(i)) == cols[h]) {
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

         updateMinAndMaxRowsAndCols();
         updateLowerResolutionNodes();
         return posIndices;
      } catch (JSONException e) {
         Log.log("Problem with position metadata");
         return null;
      }
   }
   
   private synchronized void updateMinAndMaxRowsAndCols() {
      //Go through all positions to update numRows and numCols
      for (int i = 0; i < positionList_.length(); i++) {
         JSONObject pos;
         try {
            pos = positionList_.getJSONObject(i);
         } catch (JSONException ex) {
            Log.log("Unexpected error reading positio list");
            throw new RuntimeException();
         }
         minRow_ = (int) Math.min(MD.getGridRow(pos), minRow_);
         minCol_ = (int) Math.min(MD.getGridCol(pos), minCol_);
         maxRow_ = (int) Math.max(MD.getGridRow(pos), maxRow_); 
         maxCol_ = (int) Math.max(MD.getGridCol(pos), maxCol_);
      }
   }

   private synchronized void readRowsAndColsFromPositionList(JSONObject summaryMD) {
      minRow_ = 0; maxRow_ = 0; minCol_ = 0; maxRow_ = 0;
      try {
         if (summaryMD.has("InitialPositionList") && !summaryMD.isNull("InitialPositionList")) {
            positionList_ = summaryMD.getJSONArray("InitialPositionList");           
            updateMinAndMaxRowsAndCols();
            updateLowerResolutionNodes(); //make sure nodes created for all preexisiting positions
         } else {
            positionList_ = new JSONArray();
         }
      } catch (JSONException e) {
         Log.log("Couldn't read initial position list");
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
         MD.setGridCol(pos, col);
         MD.setGridRow(pos, row);
         pos.put(PROPERTIES_KEY, new JSONObject());

         return pos;
      } catch (Exception e) {
         Log.log("Couldn't create XY position");
         return null;
      }
   }
   
   private synchronized MultiResPositionNode[] getFullResNodes() throws JSONException {
      //Go through all base resolution positions and make a list of their multiResNodes, creating nodes when neccessary
      MultiResPositionNode[] fullResNodes = new MultiResPositionNode[positionList_.length()];
      for (int i = 0; i < positionList_.length(); i++) {
         JSONObject position = positionList_.getJSONObject(i);
         if (!position.getJSONObject(PROPERTIES_KEY).has(MULTI_RES_NODE_KEY)) {
            //make sure level 0 set exists
            if (!positionNodes_.containsKey(0)) {
               positionNodes_.put(0, new TreeSet<MultiResPositionNode>());
            }
            //add node in case its a new position
            MultiResPositionNode n = new MultiResPositionNode(0,MD.getGridRow(position),MD.getGridCol(position));
            positionNodes_.get(0).add(n);
            n.positionIndex = i;
            position.getJSONObject(PROPERTIES_KEY).put(MULTI_RES_NODE_KEY, n);
         }
         fullResNodes[i] = (MultiResPositionNode) position.getJSONObject(PROPERTIES_KEY).get(MULTI_RES_NODE_KEY);        
      }
      return fullResNodes;
   }
   
   private void updateLowerResolutionNodes() {
      int gridLength = Math.max(maxRow_ - minRow_ + 1, maxCol_ - minCol_ + 1);
      //check for lower resolution as a result of user zoom
      int lowestResLevel = (int) Math.max(positionNodes_.keySet().size() - 1, Math.ceil(Math.log(gridLength) / Math.log(2)));      
      updateLowerResolutionNodes(lowestResLevel);
   }

   public void updateLowerResolutionNodes(int lowestResLevel) {
      try {
         //Go through all base resolution positions and make a list of their multiResNodes, creating nodes when neccessary
         MultiResPositionNode[] fullResNodes = getFullResNodes();
         for (MultiResPositionNode node : fullResNodes) {
            //recursively link all nodes to their parents to ensure that correct
            //position indices and grid/col indices are know for all needed res levels
            linkToParentNodes(node, lowestResLevel);
         }
      } catch (JSONException e) {
         Log.log("Problem reading position metadata");
      }
   }

   //Lower res levels are actually higher numbers: 0 is full res, 1 is factor of two, 2 facotr of 4, etc
   //lowestResLevel tells you the lowest resolution data is being downsampled to
   private void linkToParentNodes(MultiResPositionNode node, int lowestResLevel) {
      if (node.resLevel == lowestResLevel) {
         return; //lowest resLevel reached, mission complete
      } else if (node.parent == null) {
         //first figure out if the parent node already exists by using the defined
         //relationship between gridRow and gridCol at different res levels, which is
         //that 0 will be topleft child of 0 at next lowest resolution
         long parentRow, parentCol;
         if (node.gridCol >= 0) {
            parentCol = node.gridCol / 2;
         } else {
            parentCol = (node.gridCol - 1) / 2;
         }
         if (node.gridRow >= 0) {
            parentRow = node.gridRow / 2;
         } else {
            parentRow = (node.gridRow - 1) / 2;
         }
         MultiResPositionNode parentNode = findExisitngNode(node.resLevel + 1, parentRow, parentCol);
         if (parentNode == null) {
            //parent node does not exist, so create it
            parentNode = new MultiResPositionNode(node.resLevel + 1, parentRow, parentCol);
            //add to list of all nodes, creating storage level if needed
            if (!positionNodes_.containsKey(parentNode.resLevel)) {
               positionNodes_.put(parentNode.resLevel, new TreeSet<MultiResPositionNode>());
            }
            positionNodes_.get(parentNode.resLevel).add(parentNode);
            //count number of positions at this res level to get position index
            int numPositions = positionNodes_.get(parentNode.resLevel).size();
            parentNode.positionIndex = numPositions - 1;
         }
         //link together child and parent
         node.parent = parentNode;
         parentNode.child = node;
      }
      linkToParentNodes(node.parent, lowestResLevel); //keep traveling up the parent chain
   }

   private MultiResPositionNode findExisitngNode(int resLevel, long gridRow, long gridCol ) {
      MultiResPositionNode nodeToFind = new MultiResPositionNode(resLevel, gridRow, gridCol);
      if (positionNodes_.containsKey(resLevel) && positionNodes_.get(resLevel).contains(nodeToFind)) {
         return positionNodes_.get(resLevel).ceiling(nodeToFind); //this should return the equal node if everything works properly
      }
      return null;
   }
   

   /**
    * 
    * @param stageCoords x and y coordinates of image in stage space
    * @return absolute, full resolution pixel coordinate of given stage posiiton
    */
   public synchronized LongPoint getPixelCoordsFromStageCoords(double stageX, double stageY) {
      try {
          JSONObject existingPosition = positionList_.getJSONObject(0);
         double exisitngX = existingPosition.getJSONObject(COORDINATES_KEY).getJSONArray(xyStageName_).getDouble(0);
         double exisitngY = existingPosition.getJSONObject(COORDINATES_KEY).getJSONArray(xyStageName_).getDouble(1);
         long existingRow = MD.getGridRow(existingPosition);
         long existingColumn = MD.getGridCol(existingPosition);
  
         //get stage displacement from center of the tile we have coordinates for
         double dx = stageX - exisitngX;
         double dy = stageY - exisitngY;
         AffineTransform transform = (AffineTransform) affine_.clone();
         Point2D.Double pixelOffset = new Point2D.Double(); // offset in number of pixels from the center of this tile
         transform.inverseTransform(new Point2D.Double(dx,dy), pixelOffset);                     
         //Add pixel offset to pixel center of this tile to get absolute pixel position
         long xPixel = (int) ((existingColumn + 0.5) * displayTileWidth_  + pixelOffset.x);
         long yPixel = (int) ((existingRow + 0.5) * displayTileHeight_  + pixelOffset.y);
         return new LongPoint(xPixel,yPixel);
      } catch (JSONException ex) {
        ex.printStackTrace();
         Log.log("Problem with current position metadata", true);
         return null;
      } catch (NoninvertibleTransformException e) {
         Log.log("Problem using affine transform to convert stage coordinates to pixel coordinates");
         return null;
      }
   }

   /**
    * 
    * @param xAbsolute x coordinate in the full Res stitched image
    * @param yAbsolute y coordinate in the full res stitched image
    * @return stage coordinates of the given pixel position
    */
   public synchronized Point2D.Double getStageCoordsFromPixelCoords(long xAbsolute, long yAbsolute) {
      try {
         JSONObject existingPosition = positionList_.getJSONObject(0);
         double exisitngX = existingPosition.getJSONObject(COORDINATES_KEY).getJSONArray(xyStageName_).getDouble(0);
         double exisitngY = existingPosition.getJSONObject(COORDINATES_KEY).getJSONArray(xyStageName_).getDouble(1);        
         long existingRow = MD.getGridRow(existingPosition);
         long existingColumn = MD.getGridCol(existingPosition);
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
         Log.log("Problem with current position metadata", true);
         return null;
}
      
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
         if ( positionList_.length() == 0) {
            try {
               //create position 0 based on current XY stage position--happens at start of explore acquisition
               return new Point2D.Double(Magellan.getCore().getXPosition(xyStageName_), Magellan.getCore().getYPosition(xyStageName_));
            } catch (Exception ex) {
               Log.log("Couldn't create position 0");
               return null;
            }
         } else {
            JSONObject existingPosition = positionList_.getJSONObject(0);

            double exisitngX = existingPosition.getJSONObject(COORDINATES_KEY).getJSONArray(xyStageName_).getDouble(0);
            double exisitngY = existingPosition.getJSONObject(COORDINATES_KEY).getJSONArray(xyStageName_).getDouble(1);   
            long existingRow = MD.getGridRow(existingPosition);
            long existingColumn = MD.getGridCol(existingPosition);

            double xPixelOffset = (col - existingColumn) * (JavaLayerImageConstructor.getInstance().getImageWidth() - pixelOverlapX);
            double yPixelOffset = (row - existingRow) * (JavaLayerImageConstructor.getInstance().getImageHeight() - pixelOverlapY);

            Point2D.Double stagePos = new Point2D.Double();
            double[] mat = new double[4];
            affine_.getMatrix(mat);
            AffineTransform transform = new AffineTransform(mat[0], mat[1], mat[2], mat[3], exisitngX, exisitngY);                   
            transform.transform(new Point2D.Double(xPixelOffset, yPixelOffset), stagePos);
            return stagePos;
         }

      } catch (JSONException ex) {
         ex.printStackTrace();
         Log.log("Problem with current position metadata", true);
         return null;
      }
   }

   /*
    * This class is a data structure describing positions corresponding to one another at different
    * resolution levels.
    * Full resolution is at the bottom of the tree, with pointers going up to lower resolotion
    * forming a pyrimid shape
    */
class MultiResPositionNode  implements Comparable<MultiResPositionNode> {
   
   MultiResPositionNode parent, child; 
   //each node actually has 4 children, but only need one to trace down to full res
   long gridRow, gridCol;
   int resLevel;
   int positionIndex;
      
   public MultiResPositionNode(int rLevel, long gRow, long gCol) {
      resLevel = rLevel;
      gridRow = gRow;
      gridCol = gCol;
   }
      
   @Override
   public int compareTo(MultiResPositionNode n) {
      if (this.resLevel != n.resLevel) {
         return this.resLevel - n.resLevel;
      } else if (this.gridRow != n.gridRow) {
         return (int) (this.gridRow - n.gridRow);
      } else {
         return (int) (this.gridCol - n.gridCol);
      }
   }

}

}
