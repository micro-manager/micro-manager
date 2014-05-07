package acq;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
import gui.ZoomableVirtualStack;
import java.awt.Point;
import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import main.Util;
import mmcorej.TaggedImage;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.acquisition.TaggedImageStorageMultipageTiff;
import org.micromanager.api.TaggedImageStorage;
import org.micromanager.utils.JavaUtils;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMException;
import org.micromanager.utils.ReportingUtils;

/**
 * This class acts as an intermediary between the underlying data storage, which
 * stores images without stitching and with metadata intact and the display,
 * which will either request a full res subimage or downsampled version of the
 * fully stitched view
 *
 * @author Henry
 */
public class DynamicStitchingImageStorage implements TaggedImageStorage {

   public static String LO_RES_SAVING_DIR = "C:/Users/bidc/Desktop/testDataDump";
   public static String HI_RES_SAVING_DIR = "C:/Users/bidc/Desktop/testDataDump";
   public static String LOW_RES_METADATA_KEY = "Low resolution stitched storage";
   
   private TaggedImageStorage imageStorage_;
   private TaggedImageStorageMultipageTiff loResStitchedStorage_;
   //local copy of summary MD with different info about positions than underlying storage
   private JSONObject summaryMetadata_;
   private int tileWidth_, tileHeight_, displayImageWidth_, displayImageHeight_;
   private TreeSet<String> imageKeys_;
   private JSONArray positionList_;
   private int xOverlap_ =0 , yOverlap_ = 0, numRows_, numCols_;
   private volatile boolean recalculateDownsampledImage_ = false;

   public DynamicStitchingImageStorage(JSONObject summaryMetadata, boolean exploreMode) {
//      xOverlap_ = SettingsDialog.getXOverlap();
//      yOverlap_ = SettingsDialog.getYOverlap();
      imageKeys_ = new TreeSet<String>();
      
      try {
         summaryMetadata_ = new JSONObject(summaryMetadata.toString());         
         readRowsAndColsFromPositionList();
         if (exploreMode) {
            numRows_ = 3;
            numCols_ = 3;
         } 
         
         tileHeight_ = MDUtils.getHeight(summaryMetadata);
         tileWidth_ = MDUtils.getWidth(summaryMetadata);
         displayImageWidth_ = (int) Math.round(getFullResWidth() / getDSFactor());
         displayImageHeight_ = (int) Math.round(getFullResHeight() / getDSFactor());
         //change summary metadata fields seen by display   
         try {
            summaryMetadata_.put("Positions", 1);
            summaryMetadata_.put("Width", displayImageWidth_);
            summaryMetadata_.put("Height", displayImageHeight_);
         } catch (Exception e) {
            ReportingUtils.showError("Couldn't add tags to summary metadata");
         }

         //get user specified prefix
         if (!summaryMetadata.has("Prefix")) {
            summaryMetadata.put("Prefix", "Untitiled");
         }
         String basePrefix = summaryMetadata.getString("Prefix");
         
         //create acqusition directory for actual data
         String acqDirectory = createAcqDirectory(summaryMetadata.getString("Directory"), basePrefix);
         summaryMetadata.put("Prefix", acqDirectory);
         String savingDir = summaryMetadata.getString("Directory") + File.separator + acqDirectory;
         imageStorage_ = new TaggedImageStorageMultipageTiff(savingDir, true, summaryMetadata, false, true, true);
         //create storage for downsampled stitched image
         
         JSONObject loResSummaryMD = new JSONObject(summaryMetadata_.toString());
         //TODO: get lo res direcory from preference
         String loResDirectory = createAcqDirectory(LO_RES_SAVING_DIR, basePrefix + "LowRes");
         loResSummaryMD.put("Prefix", loResDirectory);
         //add special key for later identification and deletion of lo res data files
         loResSummaryMD.put(LOW_RES_METADATA_KEY, true);
         String loResSavingDir = LO_RES_SAVING_DIR + File.separator + loResDirectory;
         loResStitchedStorage_ = new TaggedImageStorageMultipageTiff(loResSavingDir, true, loResSummaryMD, false, false, true);
      } catch (Exception e) {
         e.printStackTrace();
         ReportingUtils.showError("Couldn't create image storage");
         return;
      }


   }
   
   public int getNumRows() {
      return numRows_;
   }
   
   public int getNumCols() {
      return numCols_;
   }
   
   public int getTileWidth() {
      return tileWidth_;
   }
   
   public int getTileHeight() {
      return tileHeight_;
   }
   
   public int getFullResWidth() {
      return Util.getStitchedImageLength(getNumCols(), tileWidth_, xOverlap_);
   }
   
   public int getFullResHeight() {      
      return Util.getStitchedImageLength(getNumRows(), tileHeight_, yOverlap_);
   }
   
   public double getDSFactor() {
      return Math.max(getFullResHeight(),getFullResWidth()) / (double) ZoomableVirtualStack.DISPLAY_IMAGE_LENGTH_MAX;       
   }
   
   public Point tileIndicesFromLoResPixel(int x, int y) {
      //convert to fullResCoordinates
      int fullResX = (int) Math.round(x * getDSFactor());
      int fullResY = (int) Math.round(y * getDSFactor());
      int row = Util.stitchedPixelToTile(fullResY, yOverlap_, tileHeight_, getNumRows());
      int col = Util.stitchedPixelToTile(fullResX, xOverlap_, tileWidth_, getNumCols());
      return new Point(row, col);
   }
   
   public JSONArray getPositionList() {
      return positionList_;
   }
   
   /**
    * This function takes a JSONObject position and adds it to list if
    * neccessary, updating max number of rows and columns. Then it returns the
    * positions index
    */
   public int[] addPositionsIfNeeded(JSONObject[] newPositions) throws JSONException {
      int[] posIndices = new int[newPositions.length];
      boolean gridExpanded = false;
      outerloop: for (int h = 0; h < posIndices.length; h++) {
         JSONObject newPosition = newPositions[h];
         //check if position is already present in list, and if so, return its index
         long newPosRow = newPosition.getLong("GridRowIndex"), newPosCol = newPosition.getLong("GridColumnIndex");
         for (int i = 0; i < positionList_.length(); i++) {
            if (positionList_.getJSONObject(i).getLong("GridRowIndex") == newPosRow
                    && positionList_.getJSONObject(i).getLong("GridColumnIndex") == newPosCol) {
               //we already have position, so return its index
               posIndices[h] = i;
               continue outerloop;
            }
         }
         //add this position to list
         positionList_.put(newPosition);
         if (newPosRow == 0 || newPosCol == 0 || newPosRow == numRows_ - 1 || newPosCol == numCols_ -1) {
            gridExpanded = true;
         }
         posIndices[h] = positionList_.length() - 1;
      }
      //if size of grid wasn't expanded, return here
      if (!gridExpanded) {
         return posIndices;
      }
      
      //Go through all positions and adjust row and column indices as needed to 
      //minmize numRows and numColumns      
      int oldNumRowCols = numRows_;
      int minRow = Integer.MAX_VALUE, minCol = Integer.MAX_VALUE;
      for (int i = 0; i < positionList_.length(); i++) {
         JSONObject pos = positionList_.getJSONObject(i);
         minRow = (int) Math.min(pos.getLong("GridRowIndex"), minRow);
         minCol = (int) Math.min(pos.getLong("GridColumnIndex"), minCol);
      }      
      
      //if gird is not top left justified with 1 tile border, recalculate
      if (minRow != 1 || minCol != 1) {
         //new position is outside exisiting grid so recalculate rows and columns
         //change position indices so that always start at 1 (to account for border)
         for (int i = 0; i < positionList_.length(); i++) {
            JSONObject pos = positionList_.getJSONObject(i);
            pos.put("GridRowIndex", pos.getLong("GridRowIndex") - minRow + 1);
            pos.put("GridColumnIndex", pos.getLong("GridColumnIndex") - minCol + 1);
         }
      }
      //find size of new grid
      int gridSize = 0;
      for (int i = 0; i < positionList_.length(); i++) {
         gridSize = (int) Math.max(gridSize, Math.max(positionList_.getJSONObject(i).getLong("GridRowIndex") + 1,
                 positionList_.getJSONObject(i).getLong("GridColumnIndex") + 1));
      }     
      //add one to account for bottom right border
      numCols_ = gridSize + 1;
      numRows_ = gridSize + 1;
      
      if (oldNumRowCols != numCols_ || minRow != 1 || minCol != 1) {
         //recaluclate display image
         recalculateDownsampledImage_ = true;
         //flag recalulcation of downsampeled image to be performed by acquisitin thread
         //because can't iterate and change image keys from two threads at once
      }
      return posIndices;
   }
   
   private void readRowsAndColsFromPositionList() {
      numRows_ = 1;
      numCols_ = 1;
      try {
         if (summaryMetadata_.has("InitialPositionList") && !summaryMetadata_.isNull("InitialPositionList")) {
            positionList_ = summaryMetadata_.getJSONArray("InitialPositionList");
            for (int i = 0; i < positionList_.length(); i++) {
               long colInd = positionList_.getJSONObject(i).getLong("GridColumnIndex");
               long rowInd = positionList_.getJSONObject(i).getLong("GridRowIndex");
               if (colInd >= numCols_) {
                  numCols_ = (int) (colInd + 1);
               }
               if (rowInd >= numRows_) {
                  numRows_ = (int) (rowInd + 1);
               }
            }
         }
      } catch (JSONException e) {
         ReportingUtils.showError("Couldn't read initial position list");
      }
   }
   
   //Copied from MMAcquisition
   private String createAcqDirectory(String root, String prefix) throws Exception {
      File rootDir = JavaUtils.createDirectory(root);
      int curIndex = getCurrentMaxDirIndex(rootDir, prefix + "_");
      return prefix + "_" + (1 + curIndex);      
   }

   private int getCurrentMaxDirIndex(File rootDir, String prefix) throws NumberFormatException {
      int maxNumber = 0;
      int number;
      String theName;
      for (File acqDir : rootDir.listFiles()) {
         theName = acqDir.getName();
         if (theName.startsWith(prefix)) {
            try {
               //e.g.: "blah_32.ome.tiff"
               Pattern p = Pattern.compile("\\Q" + prefix + "\\E" + "(\\d+).*+");
               Matcher m = p.matcher(theName);
               if (m.matches()) {
                  number = Integer.parseInt(m.group(1));
                  if (number >= maxNumber) {
                     maxNumber = number;
                  }
               }
            } catch (NumberFormatException e) {
            } // Do nothing.
         }
      }
      return maxNumber;
   }

   //Return a subimage of the larger stitched image, loading only the tiles neccesary to form the subimage
   public TaggedImage getFullResStitchedSubImage(int channel, int slice, int frame, int x, int y, int width, int height) {
      byte[] pixels = new byte[width * height];
      //go line by line through one column of tiles at a time, then move to next column
      JSONObject topLeftMD = null;
      //first calculate how many columns and rows of tiles are relevant and the number of pixels
      //of each tile to copy
      int previousCol = -1;
      LinkedList<Integer> lineWidths = new LinkedList<Integer>();
      for (int i = x; i < x + width; i++) {
         int colIndex = Util.stitchedPixelToTile(i, xOverlap_, tileWidth_, getNumCols());                 
         if (colIndex != previousCol) {
            lineWidths.add(0);
         }
         //add one to pixel count of current width
         lineWidths.add(lineWidths.removeLast() + 1);
         previousCol = colIndex;
      }
      int previousRow = -1;
      LinkedList<Integer> lineHeights = new LinkedList<Integer>();
      for (int i = y; i < y + height; i++) {
         int rowIndex = Util.stitchedPixelToTile(i, yOverlap_, tileHeight_, getNumRows());                 
         if (rowIndex != previousRow) {
            lineHeights.add(0);
         }
         //add one to pixel count of current height
         lineHeights.add(lineHeights.removeLast() + 1);
         previousRow = rowIndex;
      }
      //get starting row and column
      int r = Util.stitchedPixelToTile(y, yOverlap_, tileHeight_, getNumRows());
      int c = Util.stitchedPixelToTile(x, xOverlap_, tileWidth_, getNumCols());
      int xOffset = 0;
      for (int col = 0; col < lineWidths.size(); col++) {
         int yOffset = 0;
         for (int row = 0; row < lineHeights.size(); row++) {
            TaggedImage tile = imageStorage_.getImage(channel, slice, frame, Util.getPosIndex(positionList_, row + r, col + c));
            if (tile == null) {
               yOffset += lineHeights.get(row); //increment y offset so new tiles appear in correct position
               continue; //If no data present for this tile go on to next one
            }
            //take top left tile for metadata
            if (topLeftMD == null) {
               topLeftMD = tile.tags;
            }
            for (int line = yOffset; line < lineHeights.get(row) + yOffset; line++) {
               int tileYPix = Util.stitchedPixelToTilePixel(y + line, yOverlap_, tileHeight_, numRows_);
               int tileXPix = Util.stitchedPixelToTilePixel(x + xOffset, xOverlap_, tileWidth_, numCols_);
               System.arraycopy(tile.pix, tileYPix * tileWidth_ + tileXPix,
                       pixels, xOffset + width * line, lineWidths.get(col));
            }
            yOffset += lineHeights.get(row);
         }
         xOffset += lineWidths.get(col);
      }
      if (topLeftMD == null) {
         //no tiles for the selected field of view
         return null;
      }      
      return new TaggedImage(pixels, topLeftMD);      
   }

   public JSONObject getImageTags(int channelIndex, int sliceIndex, int frameIndex, int positionIndex) {
      return getImage(channelIndex, sliceIndex, frameIndex, positionIndex).tags;
   }

   public TaggedImage getImage(int channel, int slice, int frame, int position) {
      return loResStitchedStorage_.getImage(channel, slice, frame, 0);   
   }
   
   public void putImage(TaggedImage taggedImage) throws MMException {
      try {
         int channel = MDUtils.getChannelIndex(taggedImage.tags);
         int slice = MDUtils.getSliceIndex(taggedImage.tags);
         int frame = MDUtils.getFrameIndex(taggedImage.tags);
         imageKeys_.add(MDUtils.generateLabel(channel,slice,frame,0));
         imageStorage_.putImage(taggedImage);     
         
         //update downsampled image in case new images added
         if (recalculateDownsampledImage_) {
            clearDownsampledPixels();
            //TODO: better way of doing this without reading all tiles? might not be scaleable
            // if switch is made to multi res storage, might only have to modify cerain tiles?
            for (String label : imageStorage_.imageKeys()) {
               //re add all tiles to downsampled image because there is a new downsamplig facotr
               int[] indices = MDUtils.getIndices(label);
               TaggedImage img = imageStorage_.getImage(indices[0], indices[1], indices[2], indices[3]);

               //Is img sometimes still null even though listed in image keys??
               addToLoResStorage(img);
            }
            recalculateDownsampledImage_ = false;
         } else {
            addToLoResStorage(taggedImage);
         }
      } catch (JSONException ex) {
         ReportingUtils.showError("Indices missing from image tags");
         ex.printStackTrace();
      }      
   }
   
   private void clearDownsampledPixels() {
      try {
         for (String label : loResStitchedStorage_.imageKeys()) {
            System.out.println();
            int[] ind = MDUtils.getIndices(label);            
            byte[] blankPix = new byte[displayImageWidth_ * displayImageHeight_];
            loResStitchedStorage_.overwritePixels(blankPix, ind[0], ind[1], ind[2]);            
         }
      } catch (Exception e) {
         ReportingUtils.showError("Couldn't clear downsampled pixels for rewriting");
      }
   }
   
   private void addToLoResStorage(TaggedImage img) {
      try {
         int channel = MDUtils.getChannelIndex(img.tags);
         int slice = MDUtils.getSliceIndex(img.tags);
         int frame = MDUtils.getFrameIndex(img.tags);
         int position = MDUtils.getPositionIndex(img.tags);
         //if lo res storage has no images with this channel/slice/frame at any position, need to 
         //create some blank pixels
         boolean makePix = true;
         for (int p = 0; p < positionList_.length(); p++) {
            if (loResStitchedStorage_.imageKeys().contains(MDUtils.generateLabel(channel, slice, frame, p))) {
               makePix = false;
               break;
            }
         }
         if (makePix) {
            //no pixels for this position yet, so create them         
            byte[] dsPix = new byte[displayImageWidth_ * displayImageHeight_];
            downsampleAndCopyTilePixels(dsPix, (byte[]) img.pix, position);
            //change position index to 0 because there is only 1 "position" for downsampled storage  
            JSONObject newTags = new JSONObject(img.tags.toString());
            MDUtils.setPositionIndex(newTags, 0);
            loResStitchedStorage_.putImage(new TaggedImage(dsPix, newTags));
         } else {
            //read downsampled pixels and copy in new ones
            byte[] dsPix = (byte[]) loResStitchedStorage_.getImage(channel, slice, frame, 0).pix;
            downsampleAndCopyTilePixels(dsPix, (byte[]) img.pix, position);
            try {
               loResStitchedStorage_.overwritePixels(dsPix, channel, slice, frame);
            } catch (IOException e) {
               ReportingUtils.showError("Couldnt overwrite downsmpled image pixels");
            }
         }  
      } catch (Exception e) {
         e.printStackTrace();
         ReportingUtils.showError("Couldn't downsample and add to lo res storage");
      }
   }

   public Set<String> imageKeys() {
      return imageKeys_;
   }

   public void finished() {
      imageStorage_.finished();
      loResStitchedStorage_.finished();
   }

   public boolean isFinished() {
      return imageStorage_.isFinished();
   }

   public void setSummaryMetadata(JSONObject md) {
      imageStorage_.setSummaryMetadata(md);
   }

   public JSONObject getSummaryMetadata() {
      return summaryMetadata_;
   }

   public void setDisplayAndComments(JSONObject settings) {
      imageStorage_.setDisplayAndComments(settings);
   }

   public JSONObject getDisplayAndComments() {
      return imageStorage_.getDisplayAndComments();
   }

   public void close() {
      imageStorage_.close();
      //close and delete lo res storage
      loResStitchedStorage_.close();
      //put on its own thread so ift doesn't hang up EDT for big files
      new Thread(new Runnable() {
         @Override
         public void run() {
            String dir = loResStitchedStorage_.getDiskLocation();
            File storageDir = new File(dir);
            File[] files = storageDir.listFiles();
            for (File f : files) {
               f.delete();
            }
            storageDir.delete();
         }
      }, "LowRes Data deleting thread").start();

   }

   public String getDiskLocation() {
      return imageStorage_.getDiskLocation();
   }

   public int lastAcquiredFrame() {
      return imageStorage_.lastAcquiredFrame();
   }

   public long getDataSetSize() {
      return imageStorage_.getDataSetSize();
   }

   public void writeDisplaySettings() {
      imageStorage_.writeDisplaySettings();
   }
   
   private void downsampleAndCopyTilePixels(byte[] stitchedPix, byte[] tilePix, int positionIndex) {
      int row = Util.rowFromPosIndex(positionList_, positionIndex);
      int col = Util.colFromPosIndex(positionList_, positionIndex);
      double dsFactor = getDSFactor();

      //iterate through relevant downsampled image pixels, at each one
      //sum all the relevant tile pixels
      
      //border pixels in full res stitched image
      int x0 = Util.tileToFirstStitchedPixel(col, xOverlap_, tileWidth_, numCols_, getFullResWidth());
      int y0 = Util.tileToFirstStitchedPixel(row, yOverlap_, tileHeight_, numRows_, getFullResHeight());
      int x1 = Util.tileToLastStitchedPixel(col, xOverlap_, tileWidth_, numCols_, getFullResWidth());
      int y1 = Util.tileToLastStitchedPixel(row, yOverlap_, tileHeight_, numRows_, getFullResHeight());
      
      for (double dsx = x0 / dsFactor; dsx <= x1 / dsFactor; dsx++) {
         for (double dsy = y0 / dsFactor; dsy <= y1 / dsFactor; dsy++) {
            //for each pixel in the downsampled stitched image, average relevant tile pixels and add in
            long sum = 0;
            double count = 0;
            //convert from downsampled pixel to stitched pixel to tile pixel
            for (long fullResX = Math.round(dsx * dsFactor); fullResX < Math.round((dsx + 1) * dsFactor); fullResX++) {
               //compensate at edge
               if (fullResX > x1) {
                  continue;
               }
               for (long fullResY = Math.round(dsy * dsFactor); fullResY < Math.round((dsy+1) *dsFactor); fullResY++) {
                  //compensate at edges
                  if (fullResY > y1) {
                     continue;
                  }
                  int tileX = Util.stitchedPixelToTilePixel((int)fullResX, xOverlap_, tileWidth_, numCols_);
                  int tileY = Util.stitchedPixelToTilePixel((int)fullResY, yOverlap_, tileHeight_, numRows_);
                  sum += tilePix[tileX + tileWidth_ * tileY] & 0xff;
                  count++;
               
               }
            } 
            stitchedPix[(int)(Math.round(dsx) + Math.round(dsy)*displayImageWidth_)] = (byte) (sum / count);
         }
      }
   }
   
   
   
   //new strategy: duplicate pixel values when at tile junctions 
//   private void downsampleAndCopyTilePixels(byte[] stitchedPix, byte[] tilePix, int positionIndex) {
//      int row = Util.rowFromPosIndex(positionList_, positionIndex);
//      int col = Util.colFromPosIndex(positionList_, positionIndex);      
//      int dsFactor = getDSFactor();
//
//      for (int dstx = 0; dstx < (double)tileWidth_ / (double)dsFactor; dstx++) { //dstx = downsampled tile x
//         for (int dsty = 0; dsty < (double)tileHeight_ / (double)dsFactor; dsty++) {
//            long sum = 0;
//            int numPix = 0;
//            for (int tileX = dstx * dsFactor; tileX < (dstx + 1) * dsFactor; tileX++) {
//               for (int tileY = dsty * dsFactor; tileY < (dsty + 1) * dsFactor; tileY++) {
//                  if (tileX < tileWidth_ && tileY < tileHeight_ && //make sure only relevant pixels added
//                          (tileX >= xOverlap_ / 2 || col == 0) && (tileY >= yOverlap_ / 2 || row == 0)) {
//                     int tilei = tileY * tileWidth_ + tileX;
//                     sum += tilePix[tilei] & 0xff;
//                     numPix++;
//                  }
//               }
//            }
//            if (numPix == 0) {
//               continue; // no pixels in this square relevant to final image
//            }
//            //get index in downsampled stitched image
//            int dssx = Util.tilePixelToStitchedPixel(dstx * dsFactor, xOverlap_, tileWidth_, col) / dsFactor;
//            int dssy = Util.tilePixelToStitchedPixel(dsty * dsFactor, yOverlap_, tileHeight_, row) / dsFactor;
//            int dssi = dssx + dssy * displayImageWidth_;
//            //average pixels and add them in
//            stitchedPix[dssi] = (byte) (sum / numPix);
//            
//         }
//      }
//   }
      
}
