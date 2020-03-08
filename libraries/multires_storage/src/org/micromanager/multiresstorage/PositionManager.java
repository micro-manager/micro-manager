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
package org.micromanager.multiresstorage;

import java.awt.geom.Point2D;
import java.util.TreeMap;
import java.util.TreeSet;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/*
 * Class to manage the relationship position indices at different resolution levels
 */
public class PositionManager {

   private static final String GRID_COL = "GridColumnIndex";
   private static final String GRID_ROW = "GridRowIndex";

   private static final String COORDINATES_KEY = "DeviceCoordinatesUm";
   private static final String PROPERTIES_KEY = "Properties";
   private static final String MULTI_RES_NODE_KEY = "MultiResPositionNode";

   private JSONArray positionList_; //all access to positionlist in synchronized methods for thread safety
   private int minRow_, maxRow_, minCol_, maxCol_; //For the lowest resolution level
   //Map of Res level to set of nodes
   private TreeMap<Integer, TreeSet<MultiResPositionNode>> positionNodes_;

   public PositionManager(int displayTileWidth, int displayTileHeight,
           int fullTileWidth, int fullTileHeight, int overlapX, int overlapY) {
      positionNodes_ = new TreeMap<Integer, TreeSet<MultiResPositionNode>>();
      minRow_ = 0;
      maxRow_ = 0;
      minCol_ = 0;
      maxRow_ = 0;
      positionList_ = new JSONArray();
      updateMinAndMaxRowsAndCols();
      updateLowerResolutionNodes(0); //make sure nodes created for all preexisiting positions
   }

   /**
    * constructor to read data from disk
    */
   public PositionManager(int displayTileWidth, int displayTileHeight,
           int fullTileWidth, int fullTileHeight, int overlapX, int overlapY, JSONArray initialPosList, int maxResLevel) {
      positionNodes_ = new TreeMap<Integer, TreeSet<MultiResPositionNode>>();
      minRow_ = 0;
      maxRow_ = 0;
      minCol_ = 0;
      maxRow_ = 0;
      positionList_ = initialPosList;
      updateMinAndMaxRowsAndCols();
      updateLowerResolutionNodes(maxResLevel); //make sure nodes created for all preexisiting positions
   }

//   /**
//    * constructor that reads initial position list
//    */
//   public PositionManager(JSONObject summaryMD, int displayTileWidth, int displayTileHeight,
//           int fullTileWidth, int fullTileHeight, int overlapX, int overlapY) {
//      positionNodes_ = new TreeMap<Integer, TreeSet<MultiResPositionNode>>();
//      readRowsAndColsFromPositionList(summaryMD);
//   }
   /**
    * Return the position index of any one of the full res positions
    * corresponding a low res position. There is at least one full res position
    * corresponding to the low res one, but this function makes no guarantees
    * about which one is returned if there are more than one.
    *
    * @param lowResPositionIndex
    * @param resIndex - res index corresponding to the low res position
    * @return position index of full res child of node or -1 if doesn't exist
    * (which shouldn't ever happen)
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
      throw new RuntimeException("Could't find full resolution child of node");
   }

   public synchronized int getLowResPositionIndex(int fullResPosIndex, int resIndex) {
      try {

         MultiResPositionNode node = (MultiResPositionNode) positionList_.getJSONObject(fullResPosIndex)
                 .getJSONObject(PROPERTIES_KEY).get(MULTI_RES_NODE_KEY);
         for (int i = 0; i < resIndex; i++) {
            node = node.parent;
         }
         return node.positionIndex;
      } catch (Exception e) {
         e.printStackTrace();
         throw new RuntimeException("Couldnt read position list correctly");
      }
   }

   public synchronized long getGridRow(int fullResPosIndex, int resIndex) {
      try {
         MultiResPositionNode node = (MultiResPositionNode) positionList_.getJSONObject(fullResPosIndex)
                 .getJSONObject(PROPERTIES_KEY).get(MULTI_RES_NODE_KEY);
         for (int i = 0; i < resIndex; i++) {
            node = node.parent;
         }
         return node.gridRow;
      } catch (JSONException e) {
         e.printStackTrace();
         throw new RuntimeException("Couldnt read position list correctly");
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
         throw new RuntimeException("Couldnt read position list correctly");
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

   private synchronized void updateMinAndMaxRowsAndCols() {
      //Go through all positions to update numRows and numCols
      for (int i = 0; i < positionList_.length(); i++) {
         JSONObject pos;
         try {
            pos = positionList_.getJSONObject(i);
         } catch (JSONException ex) {
            throw new RuntimeException("Unexpected error reading positio list");
         }
         minRow_ = (int) Math.min(StorageMD.getGridRow(pos), minRow_);
         minCol_ = (int) Math.min(StorageMD.getGridCol(pos), minCol_);
         maxRow_ = (int) Math.max(StorageMD.getGridRow(pos), maxRow_);
         maxCol_ = (int) Math.max(StorageMD.getGridCol(pos), maxCol_);
      }
   }

   private synchronized void readRowsAndColsFromPositionList(JSONObject summaryMD) {
      minRow_ = 0;
      maxRow_ = 0;
      minCol_ = 0;
      maxRow_ = 0;
      try {
         if (summaryMD.has("InitialPositionList") && !summaryMD.isNull("InitialPositionList")) {
            positionList_ = summaryMD.getJSONArray("InitialPositionList");
            updateMinAndMaxRowsAndCols();
            updateLowerResolutionNodes(); //make sure nodes created for all preexisiting positions
         } else {
            throw new RuntimeException("Missing initialpositionlist");
         }
      } catch (JSONException e) {
         throw new RuntimeException("Couldn't read initial position list");
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
            MultiResPositionNode n = new MultiResPositionNode(0,
                    StorageMD.getGridRow(position),
                    StorageMD.getGridCol(position));
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
         throw new RuntimeException("Problem reading position metadata");
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

   private MultiResPositionNode findExisitngNode(int resLevel, long gridRow, long gridCol) {
      MultiResPositionNode nodeToFind = new MultiResPositionNode(resLevel, gridRow, gridCol);
      if (positionNodes_.containsKey(resLevel) && positionNodes_.get(resLevel).contains(nodeToFind)) {
         return positionNodes_.get(resLevel).ceiling(nodeToFind); //this should return the equal node if everything works properly
      }
      return null;
   }

   private JSONObject createPosition(int row, int col) {
      try {
         JSONObject pos = new JSONObject();
         StorageMD.setGridCol(pos, col);
         StorageMD.setGridRow(pos, row);
         pos.put(PROPERTIES_KEY, new JSONObject());
         return pos;
      } catch (Exception e) {
         throw new RuntimeException("Couldn't create XY position");
      }
   }

   void rowColReceived(int row, int col) {
      try {
         //check if position is already present in list, and if so, return its index
         for (int i = 0; i < positionList_.length(); i++) {
            if (StorageMD.getGridRow(positionList_.getJSONObject(i)) == row
                    && StorageMD.getGridCol(positionList_.getJSONObject(i)) == col) {
               //we already have position
               return;
            }
         }
         //add this position to list
         positionList_.put(createPosition(row, col));
         if (positionList_.length() == 1) {
            updateMinAndMaxRowsAndCols();
            updateLowerResolutionNodes(0);
         }

         updateMinAndMaxRowsAndCols();
         updateLowerResolutionNodes();
      } catch (JSONException e) {
         throw new RuntimeException(e);
      }
   }


   /*
    * This class is a data structure describing positions corresponding to one another at different
    * resolution levels.
    * Full resolution is at the bottom of the tree, with pointers going up to lower resolotion
    * forming a pyrimid shape
    */
   class MultiResPositionNode implements Comparable<MultiResPositionNode> {

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
