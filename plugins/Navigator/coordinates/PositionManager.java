/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package coordinates;

import gui.SettingsDialog;
import java.awt.Point;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import mmcorej.CMMCore;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.internal.MMStudio;
import org.micromanager.ScriptInterface;
import org.micromanager.internal.utils.ReportingUtils;


/*
 * Class to manage XY positions, their indices, and positions within a grid at
 * multiple resolution levels
 */
public class PositionManager {

   private static final String COORDINATES_KEY = "DeviceCoordinatesUm";
   private static final String ROW_KEY = "GridRowIndex";
   private static final String COL_KEY = "GridColumnIndex";
   private static final String PROPERTIES_KEY = "Properties";
   private static final String MULTI_RES_NODE_KEY = "MultiResPositionNode";
   
   
   private String pixelSizeConfig_;
   private JSONArray positionList_;
   private int minRow_, maxRow_, minCol_, maxCol_; //For the lowest resolution level
   private String xyStageName_ = MMStudio.getInstance().getCore().getXYStageDevice();
   //Map of Res level to set of nodes
   private TreeMap<Integer,TreeSet<MultiResPositionNode>> positionNodes_; 
   private int tileWidth_, tileHeight_;
   
   public PositionManager(String pixelSizeConfig, JSONObject summaryMD, int tileWidth, int tileHeight) {
      pixelSizeConfig_ = pixelSizeConfig;
      positionNodes_ = new TreeMap<Integer,TreeSet<MultiResPositionNode>>();
      readRowsAndColsFromPositionList(summaryMD);
      tileWidth_ = tileWidth;
      tileHeight_ = tileHeight;
   }
   
   public String getSerializedPositionList() {
      return positionList_.toString();
   }
   
   public int getNumPositions() {
      return positionList_.length();
   }

   public double getXCoordinate(int positionIndex) throws JSONException {
      return positionList_.getJSONObject(positionIndex).getJSONObject("DeviceCoordinatesUm").getJSONArray(xyStageName_).getDouble(0);
   }

   public double getYCoordinate(int positionIndex) throws JSONException {
      return positionList_.getJSONObject(positionIndex).getJSONObject("DeviceCoordinatesUm").getJSONArray(xyStageName_).getDouble(1);
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
      ReportingUtils.showError("Could't find full resolution child of node");
      return -1;
   }

   public int getLowResPositionIndex(int fullResPosIndex, int resIndex) {
      try {
         MultiResPositionNode node = (MultiResPositionNode) positionList_.getJSONObject(fullResPosIndex).getJSONObject(PROPERTIES_KEY).get(MULTI_RES_NODE_KEY);
         for (int i = 0; i < resIndex; i++) {
            node = node.parent;
         }
         return node.positionIndex;
      } catch (Exception e) {
         e.printStackTrace();
         ReportingUtils.showError("Couldnt read position list correctly");
         return 0;
      }
   }
   
   public long getGridRow(int fullResPosIndex, int resIndex) {
      try {
         MultiResPositionNode node = (MultiResPositionNode) positionList_.getJSONObject(fullResPosIndex).getJSONObject(PROPERTIES_KEY).get(MULTI_RES_NODE_KEY);
         for (int i = 0; i < resIndex; i++) {
            node = node.parent;
         }
         return node.gridRow;
      } catch (JSONException e) {
         ReportingUtils.showError("Couldnt read position list correctly");
         return 0;
      }
   }

   public long getGridCol(int fullResPosIndex, int resIndex) {
      try {
         MultiResPositionNode node = (MultiResPositionNode) positionList_.getJSONObject(fullResPosIndex).getJSONObject(PROPERTIES_KEY).get(MULTI_RES_NODE_KEY);
         for (int i = 0; i < resIndex; i++) {
            node = node.parent;
         }
         return node.gridCol;
      } catch (JSONException e) {
         ReportingUtils.showError("Couldnt read position list correctly");
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
   public int getPositionIndexFromTilePosition(int dsIndex, int rowIndex, int colIndex) {
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
   public int[] getPositionIndices(int[] rows, int[] cols) {
      try {
         int[] posIndices = new int[rows.length];
         boolean newPositionsAdded = false;

         outerloop:
         for (int h = 0; h < posIndices.length; h++) {
            //check if position is already present in list, and if so, return its index
            for (int i = 0; i < positionList_.length(); i++) {
               if (positionList_.getJSONObject(i).getLong(ROW_KEY) == rows[h]
                       && positionList_.getJSONObject(i).getLong(COL_KEY) == cols[h]) {
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
         ReportingUtils.showError("Problem with position metadata");
         return null;
      }
   }
   
   private void updateMinAndMaxRowsAndCols() throws JSONException {
      //Go through all positions to update numRows and numCols
      for (int i = 0; i < positionList_.length(); i++) {
         JSONObject pos = positionList_.getJSONObject(i);
         minRow_ = (int) Math.min(pos.getLong(ROW_KEY), minRow_);
         minCol_ = (int) Math.min(pos.getLong(COL_KEY), minCol_);
         maxRow_ = (int) Math.max(pos.getLong(ROW_KEY), maxRow_);
         maxCol_ = (int) Math.max(pos.getLong(COL_KEY), maxCol_);
      }
   }

   private void readRowsAndColsFromPositionList(JSONObject summaryMD) {
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
         ReportingUtils.showError("Couldn't read initial position list");
      }
   }

   private JSONObject createPosition(int row, int col) {
      try {
         JSONArray xy = new JSONArray();         
         int xOverlap = SettingsDialog.getOverlapX();
         int yOverlap = SettingsDialog.getOverlapY();
         Point2D.Double stageCoords = getStagePositionCoordinates(row, col, xOverlap, yOverlap);

         JSONObject coords = new JSONObject();
         xy.put(stageCoords.x);
         xy.put(stageCoords.y);
         coords.put(MMStudio.getInstance().getCore().getXYStageDevice(), xy);
         JSONObject pos = new JSONObject();
         pos.put(COORDINATES_KEY, coords);
         pos.put(COL_KEY, col);
         pos.put(ROW_KEY, row);
         pos.put(PROPERTIES_KEY, new JSONObject());

         return pos;
      } catch (Exception e) {
         ReportingUtils.showError("Couldn't create XY position");
         return null;
      }
   }
   
   private MultiResPositionNode[] getFullResNodes() throws JSONException {
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
            MultiResPositionNode n = new MultiResPositionNode(0,position.getLong(ROW_KEY),position.getLong(COL_KEY));
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
         ReportingUtils.showError("Problem reading position metadata");
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
   public Point getPixelCoordsFromStageCoords(double stageX, double stageY) {
      try {
          JSONObject existingPosition = positionList_.getJSONObject(0);
         double exisitngX = existingPosition.getJSONObject(COORDINATES_KEY).getJSONArray(xyStageName_).getDouble(0);
         double exisitngY = existingPosition.getJSONObject(COORDINATES_KEY).getJSONArray(xyStageName_).getDouble(1);
         int existingRow = existingPosition.getInt(ROW_KEY);
         int existingColumn = existingPosition.getInt(COL_KEY);
  
         //get stage displacement from center of the tile we have coordinates for
         double dx = stageX - exisitngX;
         double dy = stageY - exisitngY;
         AffineTransform transform = AffineUtils.getAffineTransform(pixelSizeConfig_,0, 0);
         Point2D.Double pixelOffset = new Point2D.Double(); // offset in number of pixels from the center of this tile
         transform.inverseTransform(new Point2D.Double(dx,dy), pixelOffset);                     
         //Add pixel offset to pixel center of this tile to get absolute pixel position
         int xPixel = (int) ((existingColumn + 0.5) * tileWidth_  + pixelOffset.x);
         int yPixel = (int) ((existingRow + 0.5) * tileHeight_  + pixelOffset.y);
         return new Point(xPixel,yPixel);
      } catch (JSONException ex) {
         ReportingUtils.showError("Problem with current position metadata");
         return null;
      } catch (NoninvertibleTransformException e) {
         ReportingUtils.showError("Problem using affine transform to convert stage coordinates to pixel coordinates");
         return null;
      }
   }

   /**
    * 
    * @param xAbsolute x coordinate in the full Res stitched image
    * @param yAbsolute y coordinate in the full res stitched image
    * @return stage coordinates of the given pixel position
    */
   public Point2D.Double getStageCoordsFromPixelCoords(int xAbsolute, int yAbsolute) {
      try {
         JSONObject existingPosition = positionList_.getJSONObject(0);
         double exisitngX = existingPosition.getJSONObject(COORDINATES_KEY).getJSONArray(xyStageName_).getDouble(0);
         double exisitngY = existingPosition.getJSONObject(COORDINATES_KEY).getJSONArray(xyStageName_).getDouble(1);
         int existingRow = existingPosition.getInt(ROW_KEY);
         int existingColumn = existingPosition.getInt(COL_KEY);
         //get pixel displacement from center of the tile we have coordinates for
         int dxPix = (int) (xAbsolute - (existingColumn + 0.5) * tileWidth_);
         int dyPix = (int) (yAbsolute - (existingRow + 0.5) * tileHeight_);
         AffineTransform transform = AffineUtils.getAffineTransform(pixelSizeConfig_,exisitngX, exisitngY);
         Point2D.Double stagePos = new Point2D.Double();
         transform.transform(new Point2D.Double(dxPix, dyPix), stagePos);  
         return stagePos;
      } catch (JSONException ex) {
         ReportingUtils.showError("Problem with current position metadata");
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
   private Point2D.Double getStagePositionCoordinates(int row, int col, int pixelOverlapX, int pixelOverlapY) {
      try {
         ScriptInterface app = MMStudio.getInstance();
         CMMCore core = app.getMMCore();
         long height = app.getMMCore().getImageHeight();
         long width = app.getMMCore().getImageWidth();
         if ( positionList_.length() == 0) {
            try {
               //create position 0 based on current XY stage position
               return new Point2D.Double(core.getXPosition(core.getXYStageDevice()), core.getYPosition(core.getXYStageDevice()));
            } catch (Exception ex) {
               ReportingUtils.showError("Couldn't create position 0");
               return null;
            }
         } else {
            JSONObject existingPosition = positionList_.getJSONObject(0);

            double exisitngX = existingPosition.getJSONObject(COORDINATES_KEY).getJSONArray(xyStageName_).getDouble(0);
            double exisitngY = existingPosition.getJSONObject(COORDINATES_KEY).getJSONArray(xyStageName_).getDouble(1);
            int existingRow = existingPosition.getInt(ROW_KEY);
            int existingColumn = existingPosition.getInt(COL_KEY);

            double xPixelOffset = (col - existingColumn) * (width - pixelOverlapX);
            double yPixelOffset = (row - existingRow) * (height - pixelOverlapY);

            AffineTransform transform = AffineUtils.getAffineTransform(pixelSizeConfig_, exisitngX, exisitngY);
            Point2D.Double stagePos = new Point2D.Double();
            transform.transform(new Point2D.Double(xPixelOffset, yPixelOffset), stagePos);
            return stagePos;
         }

      } catch (JSONException ex) {
         ReportingUtils.showError("Problem with current position metadata");
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
   
   //TODO: serialize so this can be written to disk
   
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
