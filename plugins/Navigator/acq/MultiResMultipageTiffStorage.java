/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package acq;

import java.awt.Point;
import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
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
 * This class manages multiple multipage Tiff datasets, averaging multiple 
 * 2x2 squares of pixels to create successively lower resolutions until the downsample factor
 * is greater or equal to the number of tiles in a given direction. This condition
 * ensures that pixels will always be divisible by the downsample factor without truncation
 * 
 */
public class MultiResMultipageTiffStorage implements TaggedImageStorage {
   
   private static final String FULL_RES_SUFFIX = "Full resolution";
   private static final String DOWNSAMPLE_SUFFIX = "Downsampled_x";
   
   private TaggedImageStorageMultipageTiff fullResStorage_;
   private TreeMap<Integer,TaggedImageStorageMultipageTiff> lowResStorages_; //map of resolution index to storage instance
   private String directory_;
   private JSONObject summaryMD_;
   private int xOverlap_, yOverlap_; 
   private int tileWidth_, tileHeight_; //Indpendent of zoom level because tile sizes stay the same--which means overlap is cut off
   private PositionManager posManager_;
   
   public MultiResMultipageTiffStorage(String dir, boolean newDataSet, JSONObject summaryMetadata,
           int overlapX, int overlapY) {
      try {
      xOverlap_ = overlapX;
      yOverlap_ = overlapY;
      if (summaryMetadata != null) {
         try {
            tileWidth_ = MDUtils.getWidth(summaryMetadata) - xOverlap_;
            tileHeight_ = MDUtils.getHeight(summaryMetadata) - yOverlap_;
         } catch (Exception e) {
            ReportingUtils.showError("Problem with Image tages");
         }
      }
      
      
      lowResStorages_ = new TreeMap<Integer, TaggedImageStorageMultipageTiff>(); 
      directory_ = dir;
      //create directory for full res data
      String fullResDir = dir + File.separator + FULL_RES_SUFFIX;
      try {
         JavaUtils.createDirectory(fullResDir);
      } catch (Exception ex) {
        ReportingUtils.showError("copuldnt create directory");
      }
      try {
         //make a copy in case tag changes are needed later
         summaryMD_ = new JSONObject(summaryMetadata.toString());  
         posManager_ = new PositionManager(summaryMD_);
      } catch (JSONException ex) {
         ReportingUtils.showError("Couldnt copy summary metadata");
      }
      //Create full Res storage
      fullResStorage_ = new TaggedImageStorageMultipageTiff(fullResDir, newDataSet, summaryMetadata, false, true, true);
      } catch (IOException e) {
         ReportingUtils.showError(e.toString());
      }
   }
   
   public int getTileWidth() {
      return tileWidth_;
   }
   
   public int getTileHeight() {
      return tileHeight_;
   }
   
   public PositionManager getPositionManager() {
      return posManager_;
   }

   private int dsTileIndexFromDSPixel(int i, int dsIndex, boolean xDirection) {
      int fullResPix = (int) (i * Math.pow(2, dsIndex));
      int tileIndex = fullResPix / (xDirection ? tileWidth_ : tileHeight_);
      //tileIndex doesn't neccesasrily start at 0...
      tileIndex += (xDirection ? posManager_.getMinCol() : posManager_.getMinRow());
      for (int ds = 1; ds <= dsIndex; ds++ ) {
         if(tileIndex >= 0) {
            tileIndex = tileIndex / 2;
         } else {
            tileIndex = (tileIndex - 1) / 2;
         }         
      }
      return tileIndex;
   }

   /**
    * Return a subimage of the larger stitched image at the appropriate zoom
    * level, loading only the tiles neccesary to form the subimage
    *
    * @param channel
    * @param slice
    * @param frame
    * @param dsIndex 0 for full res, 1 for 2x downsample, 2 for 4x downsample, etc..
    * @param x coordinate of leftmost pixel in requested resolution
    * @param y coordinate of topmost pixel in requested resolution
    * @param width pixel width of image at requested resolution
    * @param height pixel height of image at requested resolution
    * @return
    */
   public TaggedImage getImageForDisplay(int channel, int slice, int frame, int dsIndex, int x, int y, int width, int height) {
      byte[] pixels = new byte[width * height];
      //go line by line through one column of tiles at a time, then move to next column
      JSONObject topLeftMD = null;
      //first calculate how many columns and rows of tiles are relevant and the number of pixels
      //of each tile to copy into the returned image
      int previousCol = -1;
      LinkedList<Integer> lineWidths = new LinkedList<Integer>(); 
      for (int i = x; i < x + width; i++) { //Iterate through every column of pixels in the image to be returned
         int colIndex = dsTileIndexFromDSPixel(i, dsIndex, true);        
         if (colIndex != previousCol) {
            lineWidths.add(0);
         }
         //Increment current width
         lineWidths.add(lineWidths.removeLast() + 1);
         previousCol = colIndex;
      }
      //do the same thing for rows
      int previousRow = -1;
      LinkedList<Integer> lineHeights = new LinkedList<Integer>();
      for (int i = y; i < y + height; i++) {
         int rowIndex = dsTileIndexFromDSPixel(i, dsIndex, false);                
         if (rowIndex != previousRow) {
            lineHeights.add(0);
         }
         //add one to pixel count of current height
         lineHeights.add(lineHeights.removeLast() + 1);
         previousRow = rowIndex;
      }
      //get starting row and column
      int r = dsTileIndexFromDSPixel(y, dsIndex, false); 
      int c = dsTileIndexFromDSPixel(x, dsIndex, true);        
      int xOffset = 0;
      for (int col = 0; col < lineWidths.size(); col++) {
         int yOffset = 0;
         for (int row = 0; row < lineHeights.size(); row++) {
            TaggedImage tile;
            if (dsIndex == 0) {
               //TODO: offset for overlap
               tile = fullResStorage_.getImage(channel,slice, frame, posManager_.getPositionIndexFromTilePosition(dsIndex, row + r, col + c));
            } else {
               tile = lowResStorages_.get(dsIndex).getImage(channel, slice, frame, posManager_.getPositionIndexFromTilePosition(dsIndex, row + r, col + c));
            }
            if (tile == null) {
               yOffset += lineHeights.get(row); //increment y offset so new tiles appear in correct position
               continue; //If no data present for this tile go on to next one
            }
            //take top left tile for metadata
            if (topLeftMD == null) {
               topLeftMD = tile.tags;
            }
            //Copy pixels into the image to be returned
            //yOffset is how many rows from top of viewable area, y is top of image to top of area
            try {
               for (int line = yOffset; line < lineHeights.get(row) + yOffset; line++) {
                  System.out.println(line);
                  int tileYPix = (y + line) % tileHeight_;
                  int tileXPix = (x + xOffset) % tileWidth_;
                  System.arraycopy(tile.pix, tileYPix * tileWidth_ + tileXPix, pixels, xOffset + width * line, lineWidths.get(col));
               }
               yOffset += lineHeights.get(row);
            } catch (Exception e) {
               System.out.println();
               ReportingUtils.showError("Problem copying pixels");
            }
         }
         xOffset += lineWidths.get(col);
      }
      if (topLeftMD == null) {
         //no tiles for the selected field of view
         return null;
      }
      return new TaggedImage(pixels, topLeftMD);
   }

   private void addToLowResStorage(TaggedImage img) {
      int channel = 0, slice = 0, frame = 0, fullResPositionIndex = 0;
      try {
         channel = MDUtils.getChannelIndex(img.tags);
         slice = MDUtils.getSliceIndex(img.tags);
         frame = MDUtils.getFrameIndex(img.tags);
         fullResPositionIndex = MDUtils.getPositionIndex(img.tags);
      } catch (JSONException e) {
         ReportingUtils.showError("Couldn't find tags");
      }

      //add offsets to account for overlap pixels at resolution level 0
      int xOffset = xOverlap_ / 2;
      int yOffset = yOverlap_ / 2;
      byte[] previousLevelPix = (byte[]) img.pix;
      int resolutionIndex = 1;
      //Downsample until max number of tiles in either direction is less than the highest dsFactor
      while (posManager_.getNumRows() >= Math.pow(2, resolutionIndex) || posManager_.getNumCols() >= Math.pow(2, resolutionIndex)) {

         //See if storage level exists
         if (!lowResStorages_.containsKey(resolutionIndex)) {
            createDownsampledStorage(resolutionIndex);
         }

         //Create pixels or get appropriate pixels to add to
         TaggedImage existingImage = lowResStorages_.get(resolutionIndex).getImage(channel, slice, frame,
                 posManager_.getLowResPositionIndex(fullResPositionIndex, resolutionIndex) );
         byte[] currentLevelPix = (byte[]) (existingImage == null ? new byte[tileWidth_ * tileHeight_] : existingImage.pix);

         //Determine which position in 2x2 this tile sits in
         int xPos = (int) (posManager_.getGridCol(fullResPositionIndex, resolutionIndex - 1) % 2);
         int yPos = (int) (posManager_.getGridRow(fullResPositionIndex, resolutionIndex - 1) % 2);
         //Add one if top or left so border pixels from and odd length image get added in
         for (int x = xOffset; x < tileWidth_ + (xPos == 0 ? 1 : 0) + xOffset; x += 2) {
            for (int y = yOffset; y < tileHeight_ + (yPos == 0 ? 1 : 0) + yOffset; y += 2) {
               //average a square of 4 pixels from previous level
               //edges: if odd number of pixels in tile, round to determine which
               //tiles pixels make it to next res level
               int count = 1;
               int sum = previousLevelPix[y * tileWidth_ + x] & 0xff;
               if (x != tileWidth_ && y != tileHeight_) {
                  count += 3;
                  sum += previousLevelPix[(y + 1) * tileWidth_ + x + 1] & 0xff
                          + previousLevelPix[y * tileWidth_ + x + 1] & 0xff
                          + previousLevelPix[(y + 1) * tileWidth_ + x] & 0xff;
               } else if (x != tileWidth_) {
                  count++;
                  sum += previousLevelPix[y * tileWidth_ + x + 1] & 0xff;
               } else if (y != tileHeight_) {
                  count++;
                  sum += previousLevelPix[(y + 1) * tileWidth_ + x] & 0xff;
               }
               currentLevelPix[((y-yOffset + yPos * tileHeight_) * (2 * tileWidth_) + x-xOffset + xPos * tileWidth_) / 2] = (byte) (sum / count);
            }
         }

         //add in this tile
         try {
//            if (existingImage != null) {
//               lowResStorages_.get(resolutionIndex).overwritePixels(img, channel, slice, frame, 
//                       posManager_.getLowResPositionIndex(fullResPositionIndex, resolutionIndex));           
//            } else {
            
            //TODO: maybe change this to overwriting pixels later on, for now just overwrite the whole image
            
               JSONObject tags = new JSONObject(img.tags.toString());
               //modify tags to reflect image size, and correct position index
               MDUtils.setWidth(tags, tileWidth_);
               MDUtils.setHeight(tags, tileHeight_);
               MDUtils.setPositionIndex(tags, posManager_.getLowResPositionIndex(fullResPositionIndex, resolutionIndex));
               lowResStorages_.get(resolutionIndex).putImage(new TaggedImage(currentLevelPix, tags));
//            }
         } catch (Exception e) {
            ReportingUtils.showError("Couldnt modify tags fro lower resolution level");
         }

         //go on to next level of downsampling
         previousLevelPix = currentLevelPix;
         resolutionIndex++;
         //no offsets after first level
         xOffset = 0;
         yOffset = 0;
      }
   }

   private void createDownsampledStorage(int resIndex) {
      String dsDir = directory_ + DOWNSAMPLE_SUFFIX + Math.pow(2, resIndex);
      try {
         JavaUtils.createDirectory(dsDir);
      } catch (Exception ex) {
         ReportingUtils.showError("copuldnt create directory");
      }
      try {
         JSONObject smd = new JSONObject(summaryMD_.toString());
         //reset dimensions so that overlap not included
         MDUtils.setWidth(smd, tileWidth_);
         MDUtils.setHeight(smd, tileHeight_);         
         TaggedImageStorageMultipageTiff storage = new TaggedImageStorageMultipageTiff(dsDir, true, summaryMD_);
         lowResStorages_.put(resIndex, storage);
      } catch (Exception ex) {
         ReportingUtils.showError("Couldnt create downsampled storage");
      }      
   }

   @Override
   public void putImage(TaggedImage taggedImage) throws MMException {
      //write to full res storage as normal (i.e. with overlap pixels present)
      fullResStorage_.putImage(taggedImage);
      addToLowResStorage(taggedImage);
   }

   @Override
   public TaggedImage getImage(int channelIndex, int sliceIndex, int frameIndex, int positionIndex) {
      //return a single tile from the full res image
      return fullResStorage_.getImage(channelIndex, sliceIndex, frameIndex, positionIndex);
   }

   @Override
   public JSONObject getImageTags(int channelIndex, int sliceIndex, int frameIndex, int positionIndex) {
      return getImage(channelIndex, sliceIndex, frameIndex, positionIndex).tags;
   }

   @Override
   public Set<String> imageKeys() {
      return fullResStorage_.imageKeys();
   }

   @Override
   public void finished() {
      fullResStorage_.finished();
      for (TaggedImageStorageMultipageTiff s : lowResStorages_.values()) {
         s.finished();
      }
   }

   @Override
   public boolean isFinished() {
      return fullResStorage_.isFinished();
   }

   @Override
   public void setSummaryMetadata(JSONObject md) {
      fullResStorage_.setSummaryMetadata(md);
   }

   @Override
   public JSONObject getSummaryMetadata() {
      return fullResStorage_.getSummaryMetadata();
   }

   @Override
   public void setDisplayAndComments(JSONObject settings) {
      fullResStorage_.setDisplayAndComments(settings);
   }

   @Override
   public JSONObject getDisplayAndComments() {
      return fullResStorage_.getDisplayAndComments();
   }

   @Override
   public void close() {
      fullResStorage_.close();
      for (TaggedImageStorageMultipageTiff s : lowResStorages_.values()) {
         s.close();
      }
   }

   @Override
   public String getDiskLocation() {
      //For display purposes
      return directory_;
   }

   @Override
   public int lastAcquiredFrame() {
      return fullResStorage_.lastAcquiredFrame();
   }

   @Override
   public long getDataSetSize() {
      long sum = 0;
      sum += fullResStorage_.getDataSetSize();
      for (TaggedImageStorageMultipageTiff s : lowResStorages_.values()) {
         sum += s.getDataSetSize();
      }
      return sum;
   }

   @Override
   public void writeDisplaySettings() {
      //TODO later
   }
}
