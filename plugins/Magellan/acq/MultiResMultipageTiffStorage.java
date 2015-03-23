/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package acq;

import coordinates.PositionManager;
import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import mmcorej.TaggedImage;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.acquisition.TaggedImageStorageMultipageTiff;
import org.micromanager.api.TaggedImageStorage;
import org.micromanager.utils.JavaUtils;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMException;
import org.micromanager.utils.ReportingUtils;

/**
 * This class manages multiple multipage Tiff datasets, averaging multiple 2x2
 * squares of pixels to create successively lower resolutions until the
 * downsample factor is greater or equal to the number of tiles in a given
 * direction. This condition ensures that pixels will always be divisible by the
 * downsample factor without truncation
 *
 */
public class MultiResMultipageTiffStorage implements TaggedImageStorage {

//   private static final byte EMPTY_PIXEL_VALUE = (byte) 30;
   private static final String FULL_RES_SUFFIX = "Full resolution";
   private static final String DOWNSAMPLE_SUFFIX = "Downsampled_x";
   private TaggedImageStorageMultipageTiff fullResStorage_;
   private TreeMap<Integer, TaggedImageStorageMultipageTiff> lowResStorages_; //map of resolution index to storage instance
   private String directory_;
   private JSONObject summaryMD_;
   private int xOverlap_, yOverlap_;
   private int fullResImageWidth_, fullResImageHeight_;
   private int tileWidth_, tileHeight_; //Indpendent of zoom level because tile sizes stay the same--which means overlap is cut off
   private PositionManager posManager_;
   private boolean finished_ = false;
   private String uniqueAcqName_;
   private int byteDepth_;

   public MultiResMultipageTiffStorage(String dir, boolean newDataSet, JSONObject summaryMetadata,
           int overlapX, int overlapY, String pixelSizeConfig) {
      try {
         xOverlap_ = overlapX;
         yOverlap_ = overlapY;
         if (summaryMetadata != null) {
            try {
               byteDepth_ = MDUtils.getBytesPerPixel(summaryMetadata);
               fullResImageWidth_ = MDUtils.getWidth(summaryMetadata);
               fullResImageHeight_ = MDUtils.getHeight(summaryMetadata);
               tileWidth_ = fullResImageWidth_ - xOverlap_;
               tileHeight_ = fullResImageHeight_ - yOverlap_;
            } catch (Exception e) {
               ReportingUtils.showError("Problem with Image tags");
            }
         }

         lowResStorages_ = new TreeMap<Integer, TaggedImageStorageMultipageTiff>();

         //prefix is provided by summary metadata
         try {
            if (!summaryMetadata.has("Prefix")) {
               ReportingUtils.showError("Acquisition name prefix not found in summary MD");
            }
            String baseName = summaryMetadata.getString("Prefix");
            uniqueAcqName_ = getUniqueAcqDirName(dir, baseName);
            //create acqusition directory for actual data
            directory_ = dir + (dir.endsWith(File.separator) ? "" : File.separator) + uniqueAcqName_;
         } catch (Exception e) {
            ReportingUtils.showError("Couldn't make acquisition directory");
         }

         //create directory for full res data
         String fullResDir = directory_ + (dir.endsWith(File.separator) ? "" : File.separator) + FULL_RES_SUFFIX;
         try {
            JavaUtils.createDirectory(fullResDir);
         } catch (Exception ex) {
            ReportingUtils.showError("copuldnt create directory");
         }
         try {
            //make a copy in case tag changes are needed later
            summaryMD_ = new JSONObject(summaryMetadata.toString());
            posManager_ = new PositionManager(pixelSizeConfig, summaryMD_, tileWidth_, tileHeight_, fullResImageWidth_, fullResImageHeight_);
         } catch (JSONException ex) {
            ReportingUtils.showError("Couldnt copy summary metadata");
         }
         //Create full Res storage
         fullResStorage_ = new TaggedImageStorageMultipageTiff(fullResDir, newDataSet, summaryMetadata, false, true, true);
      } catch (IOException e) {
         ReportingUtils.showError(e.toString());
      }
   }
   
   public String getUniqueAcqName() {
      return uniqueAcqName_;
   }

   public int getNumResLevels() {
      return 1 + lowResStorages_.keySet().size();
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

   /*
    * It doesnt matter what resolution level the pixel is at since tiles
    * are the same size at every level
    */
   private int tileIndexFromPixelIndex(int i, boolean xDirection) {
      if (i >= 0) {
         return i / (xDirection ? tileWidth_ : tileHeight_);
      } else {
         //highest pixel is -1 for tile indexed -1, so need to add one to pixel values before dividing
         return (i +  1) / (xDirection ? tileWidth_ : tileHeight_) - 1;
      }
   }

   /**
    * Return a subimage of the larger stitched image at the appropriate zoom
    * level, loading only the tiles neccesary to form the subimage
    *
    * @param channel
    * @param slice
    * @param frame
    * @param dsIndex 0 for full res, 1 for 2x downsample, 2 for 4x downsample,
    * etc..
    * @param x coordinate of leftmost pixel in requested resolution
    * @param y coordinate of topmost pixel in requested resolution
    * @param width pixel width of image at requested resolution
    * @param height pixel height of image at requested resolution
    * @return
    */
   public TaggedImage getImageForDisplay(int channel, int slice, int frame, int dsIndex, int x, int y, int width, int height) {
      Object pixels;
      if (byteDepth_ == 1) {
         pixels = new byte[width * height];      
      } else {            
         pixels = new short[width * height];      
      }
      //go line by line through one column of tiles at a time, then move to next column
      JSONObject topLeftMD = null;
      //first calculate how many columns and rows of tiles are relevant and the number of pixels
      //of each tile to copy into the returned image
      int previousCol = tileIndexFromPixelIndex(x, true) - 1; //make it one less than the first col in loop
      LinkedList<Integer> lineWidths = new LinkedList<Integer>();
      for (int i = x; i < x + width; i++) { //Iterate through every column of pixels in the image to be returned
         int colIndex = tileIndexFromPixelIndex(i, true);
         if (colIndex != previousCol) {
            lineWidths.add(0);
         }
         //Increment current width
         lineWidths.add(lineWidths.removeLast() + 1);
         previousCol = colIndex;
      }
      //do the same thing for rows
      int previousRow = tileIndexFromPixelIndex(y, false) - 1; //one less than first row in loop?
      LinkedList<Integer> lineHeights = new LinkedList<Integer>();
      for (int i = y; i < y + height; i++) {
         int rowIndex = tileIndexFromPixelIndex(i, false);
         if (rowIndex != previousRow) {
            lineHeights.add(0);
         }
         //add one to pixel count of current height
         lineHeights.add(lineHeights.removeLast() + 1);
         previousRow = rowIndex;
      }
      //get starting row and column
      int rowStart = tileIndexFromPixelIndex(y, false);
      int colStart = tileIndexFromPixelIndex(x, true);
      //xOffset and y offset are the distance from the top left of the display image into which 
      //we are copying data
      int xOffset = 0;
      for (int col = colStart; col < colStart + lineWidths.size(); col++) {
         int yOffset = 0;
         for (int row = rowStart; row < rowStart + lineHeights.size(); row++) {
            TaggedImage tile = null;          
            if (dsIndex == 0) {
               tile = fullResStorage_.getImage(channel, slice, frame, posManager_.getPositionIndexFromTilePosition(dsIndex, row, col));
            } else {               
               tile = lowResStorages_.get(dsIndex) == null ? null : 
                       lowResStorages_.get(dsIndex).getImage(channel, slice, frame, posManager_.getPositionIndexFromTilePosition(dsIndex, row, col));
            }
            if (tile == null) {
               yOffset += lineHeights.get(row - rowStart); //increment y offset so new tiles appear in correct position
               continue; //If no data present for this tile go on to next one
            } else if ( (tile.pix instanceof byte[] && ((byte[]) tile.pix).length == 0) ||
                    (tile.pix instanceof short[] && ((short[]) tile.pix).length == 0))  {
               //Somtimes an inability to read IFDs soon after they are written results in an image being read 
               //with 0 length pixels. Can't figure out why this happens, but it is rare and will result at worst with
               //a black flickering during acquisition
               yOffset += lineHeights.get(row - rowStart); //increment y offset so new tiles appear in correct position
               continue;
            }
            //take top left tile for metadata
            if (topLeftMD == null) {
               topLeftMD = tile.tags;
            }
            //Copy pixels into the image to be returned
            //yOffset is how many rows from top of viewable area, y is top of image to top of area
            for (int line = yOffset; line < lineHeights.get(row - rowStart) + yOffset; line++) {
               int tileYPix = (y + line) % tileHeight_;
               int tileXPix = (x + xOffset) % tileWidth_;                   
               //make sure tile pixels are positive
               while (tileXPix < 0) {
                  tileXPix += tileWidth_;
               }
               while (tileYPix < 0) {
                  tileYPix += tileHeight_;
               }
               try {
                  if (dsIndex == 0) {
                     //account for overlaps when viewing full resolution tiles
                     tileYPix += yOverlap_ / 2;
                     tileXPix += xOverlap_ / 2;
                     System.arraycopy(tile.pix, tileYPix * fullResImageWidth_ + tileXPix, pixels, xOffset + width * line, lineWidths.get(col - colStart));                 
                  } else {
                     System.arraycopy(tile.pix, tileYPix * tileWidth_ + tileXPix, pixels, xOffset + width * line, lineWidths.get(col - colStart));
                  }
               } catch (Exception e) {
                  e.printStackTrace();
                  ReportingUtils.showError("Problem copying pixels");
               }
            }
            yOffset += lineHeights.get(row - rowStart);

         }
         xOffset += lineWidths.get(col - colStart);
      }
      if (topLeftMD == null) {
         //no tiles for the selected field of view
         return null;
      }
      return new TaggedImage(pixels, topLeftMD);
   }
   
   /**
    * Called before any images have been added to initialize the resolution to the specifiec zoom level
    * @param resIndex 
    */
   public void initializeToLevel(int resIndex) {
      //create a null pointer in lower res storages to signal addToLoResStorage function
      //to continue downsampling to this level
      for (int i = lowResStorages_.keySet().size() + 1; i <= resIndex; i++) {
         lowResStorages_.put(i, null);
      }
      //Make sure position nodes for lower resolutions are created if they weren't automatically
      posManager_.updateLowerResolutionNodes(lowResStorages_.keySet().size());
   }

   /**
    * create an additional lower resolution level so that image can be zoomed out 
    */
   public boolean addLowerResolution() {
      synchronized(this) {
         if (fullResStorage_.imageKeys().size() == 0) {
            //nothing to downsample
            return false;
         }
         //create a null pointer in lower res storages to signal addToLoResStorage function
         //to continue downsampling to this level
         lowResStorages_.put(1 + lowResStorages_.keySet().size(), null);
         //update position manager to reflect addition of new resolution level
         posManager_.updateLowerResolutionNodes(lowResStorages_.keySet().size());
         //TODO: change this somehow when timepoints factor in
         String aLabel = fullResStorage_.imageKeys().iterator().next();
         int[] indices = MDUtils.getIndices(aLabel);
         TaggedImage anImage = fullResStorage_.getImage(indices[0], indices[1], indices[2], indices[3]);         
         addToLowResStorage(anImage, 0, indices[3]);
         return true;
      }
   }
   
   private void addToLowResStorage(TaggedImage img, int imageResIndex, int fullResPositionIndex) {
      //Read indices
      int channel = 0, slice = 0, frame = 0;
      try {
         channel = MDUtils.getChannelIndex(img.tags);
         slice = MDUtils.getSliceIndex(img.tags);
         frame = MDUtils.getFrameIndex(img.tags);
      } catch (JSONException e) {
         ReportingUtils.showError("Couldn't find tags");
      }

      Object previousLevelPix = img.pix;
      int resolutionIndex = imageResIndex + 1;
      
      //Auto downsample until max number of tiles in either direction is less than the highest dsFactor
      //or keep going until lowest user created resolution exists
      while (lowResStorages_.containsKey(resolutionIndex) ||
              (posManager_.getNumRows() >= Math.pow(2, resolutionIndex) || posManager_.getNumCols() >= Math.pow(2, resolutionIndex)) ) {

         //See if storage level exists
         if (!lowResStorages_.containsKey(resolutionIndex) || lowResStorages_.get(resolutionIndex) == null) {
            createDownsampledStorage(resolutionIndex);
            //add all tiles from existing resolution levels to this new one            
            TaggedImageStorage previousLevelStorage;
            if (resolutionIndex == 1) {
               previousLevelStorage = fullResStorage_;
            } else {
               previousLevelStorage = lowResStorages_.get(resolutionIndex - 1);
            }            
            Set<String> imageKeys = previousLevelStorage.imageKeys();
            for (String key : imageKeys) {
               String[] indices = key.split("_");
               TaggedImage ti = previousLevelStorage.getImage(Integer.parseInt(indices[0]), Integer.parseInt(indices[1]),
                       Integer.parseInt(indices[2]), Integer.parseInt(indices[3]));
               addToLowResStorage(ti, resolutionIndex - 1, posManager_.getFullResPositionIndex(Integer.parseInt(indices[3]), resolutionIndex - 1)); 
            }
            return; //this will include the higher res tile intially added, so can return here and
            //not worry about having to add it again
         }
         //Create pixels or get appropriate pixels to add to
         TaggedImage existingImage = lowResStorages_.get(resolutionIndex).getImage(channel, slice, frame,
                 posManager_.getLowResPositionIndex(fullResPositionIndex, resolutionIndex));
         Object currentLevelPix = (existingImage == null ? 
                 (byteDepth_ == 1 ? new byte[tileWidth_*tileHeight_] : new short[tileWidth_*tileHeight_]) : existingImage.pix);

         //Determine which position in 2x2 this tile sits in
         int xPos = (int) Math.abs((posManager_.getGridCol(fullResPositionIndex, resolutionIndex - 1) % 2));
         int yPos = (int) Math.abs((posManager_.getGridRow(fullResPositionIndex, resolutionIndex - 1) % 2));
         //Add one if top or left so border pixels from an odd length image gets added in
         for (int x = 0; x < tileWidth_; x += 2) { //iterate over previous res level pixels
            for (int y = 0; y < tileHeight_; y += 2) {
               //average a square of 4 pixels from previous level
               //edges: if odd number of pixels in tile, round to determine which
               //tiles pixels make it to next res level
               int count = 1; //count is number of pixels (out of 4)
               int pixelX, pixelY;
               if ( resolutionIndex == 1 ) {                  
                  //add offsets to account for overlap pixels at resolution level 0
                  pixelX = x + xOverlap_ / 2;
                  pixelY = y + yOverlap_ /2;
               } else {
                  pixelX = x;
                  pixelY = y;
               }
               
               //always take top left pixel, maybe take others depending on whether at image edge
               int sum = 0;
               if (byteDepth_ == 1) {
                  sum += ((byte[])previousLevelPix)[y * tileWidth_ + x] & 0xff;                  
               } else {
                  sum += ((short[])previousLevelPix)[y * tileWidth_ + x] & 0xffff;
               }
               //pixel index can be different from index in tile at resolution level 0 if there is nonzero overlap
               int previousLevelWidth;
               if (resolutionIndex == 1) {
                  previousLevelWidth = fullResImageWidth_;
               } else {
                  previousLevelWidth = tileWidth_;
               }
               if (x < tileWidth_ - 1 && y < tileHeight_ - 1) {
                  count += 3;
                  if (byteDepth_ == 1) {
                     sum += (((byte[]) previousLevelPix)[(pixelY + 1) * previousLevelWidth + pixelX + 1] & 0xff)
                             + (((byte[]) previousLevelPix)[pixelY * previousLevelWidth + pixelX + 1] & 0xff)
                             + (((byte[]) previousLevelPix)[(pixelY + 1) * previousLevelWidth + pixelX] & 0xff);
                  } else {
                     sum += (((short[]) previousLevelPix)[(pixelY + 1) * previousLevelWidth + pixelX + 1] & 0xffff)
                             + (((short[]) previousLevelPix)[pixelY * previousLevelWidth + pixelX + 1] & 0xffff)
                             + (((short[]) previousLevelPix)[(pixelY + 1) * previousLevelWidth + pixelX] & 0xffff);
                  }
               } else if (x < tileWidth_ - 1) {
                  count++;
                  if (byteDepth_ == 1) {
                     sum += ((byte[]) previousLevelPix)[pixelY * previousLevelWidth + pixelX + 1] & 0xff;
                  } else {
                     sum += ((short[]) previousLevelPix)[pixelY * previousLevelWidth + pixelX + 1] & 0xffff;
                  }
               } else if (y < tileHeight_ - 1) {
                  count++;
                  if (byteDepth_ == 1) {
                     sum += ((byte[]) previousLevelPix)[(pixelY + 1) * previousLevelWidth + pixelX] & 0xff;
                  } else {
                     sum += ((short[]) previousLevelPix)[(pixelY + 1) * previousLevelWidth + pixelX] & 0xffff;
                  }
               }
               //add pixels in to appropriate quadrant of current res level
               try {
                  if (byteDepth_ == 1) {
                     ((byte[])currentLevelPix)[((y + yPos * tileHeight_) * tileWidth_ + (x + xPos * tileWidth_)) / 2] = (byte) (sum / count);
                  } else {
                     ((short[])currentLevelPix)[((y + yPos * tileHeight_) * tileWidth_ + (x + xPos * tileWidth_)) / 2] = (short) (sum / count);
                  }
               } catch (Exception e) {
                  ReportingUtils.showError("Couldn't copy pixels to lower resolution");
                  e.printStackTrace();
                  return;
               }
            }
         }
         
         //add in this tile
         try {
            if (existingImage == null) {     //Image doesn't yet exist at this level, so add it
               //create a copy of tags so tags from a different res level arent inadverntanly modified
               // while waiting for being written to disk
               JSONObject tags = new JSONObject(img.tags.toString());
               //modify tags to reflect image size, and correct position index
               MDUtils.setWidth(tags, tileWidth_);
               MDUtils.setHeight(tags, tileHeight_);
               long gridRow = posManager_.getGridRow(fullResPositionIndex, resolutionIndex);
               long gridCol = posManager_.getGridCol(fullResPositionIndex, resolutionIndex);
               MDUtils.setPositionName(tags, "Grid_" + gridRow + "_" + gridCol);
               MDUtils.setPositionIndex(tags, posManager_.getLowResPositionIndex(fullResPositionIndex, resolutionIndex));  
               lowResStorages_.get(resolutionIndex).putImage(new TaggedImage(currentLevelPix, tags));
            } else {
               //Image already exists, only overwrite pixels to include new tiles
               lowResStorages_.get(resolutionIndex).overwritePixels(currentLevelPix,
                       channel, slice, frame, posManager_.getLowResPositionIndex(fullResPositionIndex, resolutionIndex));
            }
         } catch (Exception e) {
            e.printStackTrace();
            ReportingUtils.showError("Couldnt modify tags for lower resolution level");
         }

         //go on to next level of downsampling
         previousLevelPix = currentLevelPix;
         resolutionIndex++;
      }
   }

   private void createDownsampledStorage(int resIndex) {
      String dsDir = directory_ + (directory_.endsWith(File.separator) ? "" : File.separator) + 
              DOWNSAMPLE_SUFFIX + (int) Math.pow(2, resIndex);
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
         TaggedImageStorageMultipageTiff storage = new TaggedImageStorageMultipageTiff(dsDir, true, smd);
         lowResStorages_.put(resIndex, storage);
      } catch (Exception ex) {
         ReportingUtils.showError("Couldnt create downsampled storage");
      }
   }

   @Override
   public void putImage(TaggedImage taggedImage) throws MMException {
      try {
         synchronized (this) {
            //write to full res storage as normal (i.e. with overlap pixels present)
            fullResStorage_.putImage(taggedImage);
            addToLowResStorage(taggedImage, 0, MDUtils.getPositionIndex(taggedImage.tags));
         }
      } catch (IOException ex) {
         ReportingUtils.showError(ex.toString());
      } catch (JSONException ex) {
         ReportingUtils.showError("Position index missing from tags");
      }
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
         if (s != null) {
            //s shouldn't be null ever, this check is to prevent window from getting into unclosable state
            //when other bugs prevent storage from being properly created
            s.finished();
         }
      }
      finished_ = true;
   }

   @Override
   public boolean isFinished() {
      return finished_;
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
      //put closing on differnt channel so as to not hang up EDT while waiting for finishing
      new Thread(new Runnable() {
         @Override
         public void run() {
            while (!finished_) {
               System.out.print("waiting for files to finish");
               try {
                  Thread.sleep(5);
               } catch (InterruptedException ex) {
                  throw new RuntimeException("closing thread interrupted");
               }
            }
            fullResStorage_.close();
            for (TaggedImageStorageMultipageTiff s : lowResStorages_.values()) {
               s.close();
            }
         } 
      },"closing thread").start();
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

   //Copied from MMAcquisition
   private String getUniqueAcqDirName(String root, String prefix) throws Exception {
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
         if (theName.toUpperCase().startsWith(prefix.toUpperCase())) {
            try {
               //e.g.: "blah_32.ome.tiff"
               Pattern p = Pattern.compile("\\Q" + prefix.toUpperCase() + "\\E" + "(\\d+).*+");
               Matcher m = p.matcher(theName.toUpperCase());
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
}
