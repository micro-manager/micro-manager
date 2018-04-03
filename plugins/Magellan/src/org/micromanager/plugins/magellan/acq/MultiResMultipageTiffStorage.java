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

package org.micromanager.plugins.magellan.acq;

import org.micromanager.plugins.magellan.coordinates.AffineUtils;
import org.micromanager.plugins.magellan.coordinates.PositionManager;
import org.micromanager.plugins.magellan.coordinates.XYStagePosition;
import java.awt.Point;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.micromanager.plugins.magellan.json.JSONArray;
import org.micromanager.plugins.magellan.json.JSONException;
import org.micromanager.plugins.magellan.json.JSONObject;
import org.micromanager.plugins.magellan.misc.JavaUtils;
import org.micromanager.plugins.magellan.misc.Log;
import org.micromanager.plugins.magellan.misc.LongPoint;
import org.micromanager.plugins.magellan.misc.MD;

/**
 * This class manages multiple multipage Tiff datasets, averaging multiple 2x2
 * squares of pixels to create successively lower resolutions until the
 * downsample factor is greater or equal to the number of tiles in a given
 * direction. This condition ensures that pixels will always be divisible by the
 * downsample factor without truncation
 *
 */
public class MultiResMultipageTiffStorage {

   private final double BACKGROUND_PIXEL_PERCENTILE = 0.1; // assume background pixels are at 10th percentile of histogram
   private static final String FULL_RES_SUFFIX = "Full resolution";
   private static final String DOWNSAMPLE_SUFFIX = "Downsampled_x";
   private TaggedImageStorageMultipageTiff fullResStorage_;
   private TreeMap<Integer, TaggedImageStorageMultipageTiff> lowResStorages_; //map of resolution index to storage instance
   private String directory_;
   private JSONObject summaryMD_;
   private int xOverlap_, yOverlap_;
   private int fullResTileWidthIncludingOverlap_, fullResTileHeightIncludingOverlap_;
   private int tileWidth_, tileHeight_; //Indpendent of zoom level because tile sizes stay the same--which means overlap is cut off
   private PositionManager posManager_;
   private boolean finished_;
   private String uniqueAcqName_;
   private int byteDepth_;
   private TreeMap<Integer, Integer> backgroundPix_ = new TreeMap<Integer, Integer>(); //map of channel index to background pixel value
   private boolean estimateBackground_;
   private double pixelSizeXY_, pixelSizeZ_;
   private AffineTransform affine_;
   private BDVXMLWriter bdvXML_;
   private int currentTP_ = -1;
   private boolean rgb_;
   
   /**
    * Constructor to load existing storage from disk
    * dir --top level saving directory
    */
   public MultiResMultipageTiffStorage(String dir)  throws IOException {
      directory_ = dir;
      finished_ = true;
      estimateBackground_ = false;
      String fullResDir = dir + (dir.endsWith(File.separator) ? "" : File.separator) + FULL_RES_SUFFIX;
      //create fullResStorage
      fullResStorage_ = new TaggedImageStorageMultipageTiff(fullResDir, false, null);
      summaryMD_ = fullResStorage_.getSummaryMetadata();
      processSummaryMetadata();
      lowResStorages_ = new TreeMap<Integer, TaggedImageStorageMultipageTiff>();
      //create low res storages
      int resIndex = 1;
      while (true) {
         String dsDir = directory_ + (directory_.endsWith(File.separator) ? "" : File.separator) + 
              DOWNSAMPLE_SUFFIX + (int) Math.pow(2, resIndex);
         if (!new File(dsDir).exists() ) {
            break;
         }
         lowResStorages_.put(resIndex, new TaggedImageStorageMultipageTiff(dsDir, false, null));
         resIndex++;         
      }
         
      //create position manager
      try {
         TreeMap<Integer, XYStagePosition> positions = new TreeMap<Integer, XYStagePosition>();
         for (String key : fullResStorage_.imageKeys()) {
            // array with entires channelIndex, sliceIndex, frameIndex, positionIndex
            int[] indices = MD.getIndices(key);
            int posIndex = indices[3];
            if (!positions.containsKey(posIndex)) {
               //read rowIndex, colIndex, stageX, stageY from per image metadata
               JSONObject md = fullResStorage_.getImageTags(indices[0], indices[1], indices[2], indices[3]);
               positions.put(posIndex, new XYStagePosition(new Point2D.Double(MD.getStageX(md), MD.getStageY(md)),
                       MD.getGridRow(md), MD.getGridCol(md)));
            }
         }
         JSONArray pList = new JSONArray();
         for (XYStagePosition xyPos : positions.values()) {
            pList.put(xyPos.getMMPosition(MD.getCoreXY(summaryMD_)));
         }
         posManager_ = new PositionManager(affine_, summaryMD_, tileWidth_, tileHeight_, tileWidth_, tileHeight_,
                 xOverlap_, xOverlap_, pList, lowResStorages_.size());

      } catch (Exception e) {
         Log.log("Couldn't create position manager", true);
      }
   }

   /**
    * Constructor for creating new storage prior to acquisition
    */
   public MultiResMultipageTiffStorage(String dir, JSONObject summaryMetadata, boolean estimateBackground) {
      estimateBackground_ = estimateBackground;
      try {
         //make a copy in case tag changes are needed later
         summaryMD_ = new JSONObject(summaryMetadata.toString());
      } catch (JSONException ex) {
         Log.log("Couldnt copy summary metadata", true);
      }
      processSummaryMetadata();

      //prefix is provided by summary metadata
      try {
         String baseName = summaryMetadata.getString("Prefix");
         uniqueAcqName_ = getUniqueAcqDirName(dir, baseName);
         //create acqusition directory for actual data
         directory_ = dir + (dir.endsWith(File.separator) ? "" : File.separator) + uniqueAcqName_;
      } catch (Exception e) {
         Log.log("Couldn't make acquisition directory");
      }

      //create directory for full res data
      String fullResDir = directory_ + (dir.endsWith(File.separator) ? "" : File.separator) + FULL_RES_SUFFIX;
      try {
         JavaUtils.createDirectory(fullResDir);
      } catch (Exception ex) {
         Log.log("couldn't create saving directory", true);
      }

      try {
         posManager_ = new PositionManager(affine_, summaryMD_, tileWidth_, tileHeight_,
                 fullResTileWidthIncludingOverlap_, fullResTileHeightIncludingOverlap_, xOverlap_, yOverlap_);
      } catch (Exception e) {
         Log.log("Couldn't create position manaher", true);
      }
      try {
         //Create full Res storage
         fullResStorage_ = new TaggedImageStorageMultipageTiff(fullResDir, true, summaryMetadata);
      } catch (IOException ex) {
         Log.log("couldn't create Full res storage", true);
      }
      lowResStorages_ = new TreeMap<Integer, TaggedImageStorageMultipageTiff>();
      try {
         bdvXML_ = new BDVXMLWriter(new File(directory_), fullResStorage_.getNumChannels(), MD.getBytesPerPixel(summaryMD_));
      } catch (IOException ex) {
         Log.log("Couldn't create BigDataViewer XML file");
      }
   }

   public static JSONObject readSummaryMetadata(String dir) throws IOException {
      String fullResDir = dir + (dir.endsWith(File.separator) ? "" : File.separator) + FULL_RES_SUFFIX;
      return TaggedImageStorageMultipageTiff.readSummaryMD(fullResDir);
   }

   public boolean isRGB() {
       return rgb_;
   }
   
   public int getXOverlap() {
      return xOverlap_;
   }

   public int getYOverlap() {
      return yOverlap_;
   }
   
   private void processSummaryMetadata() {
      rgb_ = MD.isRGB(summaryMD_);
      xOverlap_ = MD.getPixelOverlapX(summaryMD_);
      yOverlap_ = MD.getPixelOverlapY(summaryMD_);
      byteDepth_ = MD.getBytesPerPixel(summaryMD_);
      fullResTileWidthIncludingOverlap_ = MD.getWidth(summaryMD_);
      fullResTileHeightIncludingOverlap_ = MD.getHeight(summaryMD_);
      tileWidth_ = fullResTileWidthIncludingOverlap_ - xOverlap_;
      tileHeight_ = fullResTileHeightIncludingOverlap_ - yOverlap_;
      pixelSizeZ_ = MD.getZStepUm(summaryMD_);
      pixelSizeXY_ = MD.getPixelSizeUm(summaryMD_);
      affine_ = AffineUtils.stringToTransform(MD.getAffineTransformString(summaryMD_));
   }
   
   public int getByteDepth() {
      return byteDepth_;
   }
   
   public String getUniqueAcqName() {
      return uniqueAcqName_;
   }

   public double getPixelSizeZ() {
      return pixelSizeZ_;
   }
   
   public double getPixelSizeXY() {
      return pixelSizeXY_;
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
   
   public long getNumRows() {
      return posManager_.getNumRows();
   }
   
   public long getNumCols() {
      return posManager_.getNumCols();
   }
   
   public long getGridRow(int fullResPosIndex, int resIndex) {
      return posManager_.getGridRow(fullResPosIndex, resIndex);
   }
   
   public long getGridCol(int fullResPosIndex, int resIndex) {
      return posManager_.getGridCol(fullResPosIndex, resIndex);
   }
   
   public XYStagePosition getXYPosition(int index) {
      return posManager_.getXYPosition(index);
   }
   
   public  int[] getPositionIndices(int[] rows, int[] cols) {
      return posManager_.getPositionIndices(rows, cols);
   }

   /* 
    * @param stageCoords x and y coordinates of image in stage space
    * @return absolute, full resolution pixel coordinate of given stage posiiton
    */
   public LongPoint getPixelCoordsFromStageCoords(double x, double y) {
      return posManager_.getPixelCoordsFromStageCoords(x, y);
   }

   /**
    *
    * @param xAbsolute x coordinate in the full Res stitched image
    * @param yAbsolute y coordinate in the full res stitched image
    * @return stage coordinates of the given pixel position
    */
   public Point2D.Double getStageCoordsFromPixelCoords(long xAbsolute, long yAbsolute) {
      return posManager_.getStageCoordsFromPixelCoords(xAbsolute, yAbsolute);
   }
   
   /*
    * It doesnt matter what resolution level the pixel is at since tiles
    * are the same size at every level
    */
   private long tileIndexFromPixelIndex(long i, boolean xDirection) {
      if (i >= 0) {
         return i / (xDirection ? tileWidth_ : tileHeight_);
      } else {
         //highest pixel is -1 for tile indexed -1, so need to add one to pixel values before dividing
         return (i +  1) / (xDirection ? tileWidth_ : tileHeight_) - 1;
      }
   }
   
   public int[] readBackgroundPixelValues() {
      //grab 5 random images from each channel to estiamte background
      Set<String> keys  = imageKeys();
      int numChannels = fullResStorage_.getNumChannels();
      int[] backgroundVals = new int[numChannels];
      int pixPerImage = tileWidth_*tileHeight_;
      ArrayList<String> keyList = new ArrayList<String>(keys);
      Collections.shuffle(keyList);
      for (int c = 0; c < numChannels; c++) {
         int count = 0;
         ArrayList<Integer> pixels = new ArrayList<Integer>();
         for (String key : keyList) {
            //channel slice frame position
            int[] indices = MD.getIndices(key);
            if (indices[0] != c) {
               continue;
            } 
            //correct channel
            byte[] pix = (byte[]) fullResStorage_.getImage(indices[0], indices[1], indices[2], indices[3]).pix;
            for (byte b : pix) {
               pixels.add( b & 0xff);
            }
            count++;
            if (count == 5) {
               break;
            }
         }
         Collections.sort(pixels);
         backgroundVals[c] = pixels.get((int)(pixels.size() * 0.15));
      }
      return backgroundVals;
   }
   
   public int getBackgroundPixelValue(int channelIndex) {
      return backgroundPix_.containsKey(channelIndex) ? backgroundPix_.get(channelIndex) : 0;
   }

   private void readBackgroundPixelValue(int channel, MagellanTaggedImage img) {
       if (!estimateBackground_ || backgroundPix_.containsKey(channel)) {
           return;
       }
      int[] pixVals = new int[fullResTileHeightIncludingOverlap_ * fullResTileWidthIncludingOverlap_];
      if (byteDepth_ == 1 || byteDepth_ == 4) {
         for (int j = 0; j < pixVals.length; j++) {
            pixVals[j] = ((byte[])img.pix)[j] & 0xff;
         }
      } else {
         for (int j = 0; j < pixVals.length; j++) {
            pixVals[j] = ((short[])img.pix)[j] & 0xffff;
         }
      }
      Arrays.sort(pixVals);
      backgroundPix_.put(channel, pixVals[(int) (pixVals.length * BACKGROUND_PIXEL_PERCENTILE)]);
   }
   
   /**
    * Method for reading 3D volumes for compatibility with TeraFly
    * @return 
    */
   public MagellanTaggedImage loadSubvolume(int channel, int frame, int resIndex,
           int xStart, int yStart, int zStart, int width, int height, int depth) {
      JSONObject metadata = null;
      if (byteDepth_ == 1) {
         byte[] pix = new byte[width*height*depth];
         for (int z = zStart; z < zStart + depth; z++ ) {
            MagellanTaggedImage image = getImageForDisplay(channel, z, frame, resIndex, xStart, yStart, width, height);
            metadata = image.tags;
            System.arraycopy(image.pix, 0, pix, (z-zStart)*(width*height), width*height);
         }
         return new MagellanTaggedImage(pix, metadata);
      } else {
         short[] pix = new short[width*height*depth];
         for (int z = zStart; z < zStart + depth; z++ ) {
            MagellanTaggedImage image = getImageForDisplay(channel, z, frame, resIndex, xStart, yStart, width, height);
            metadata = image.tags;
            System.arraycopy(image.pix, 0, pix, (z-zStart)*(width*height), width*height);
         }
         return new MagellanTaggedImage(pix, metadata);
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
    * @return Tagged image or taggeded image with background pixels and null tags if no
    * pixel data is present
    */
   public MagellanTaggedImage getImageForDisplay(int channel, int slice, int frame, int dsIndex, long x, long y, 
           int width, int height) {
      Object pixels;
       if (rgb_) {
          pixels = new byte[width * height * 4];
       } else {
           if (byteDepth_ == 1) {
               pixels = new byte[width * height];
               if (backgroundPix_.containsKey(channel)) {
                   Arrays.fill((byte[]) pixels, (byte) getBackgroundPixelValue(channel));
               }
           } else {
               pixels = new short[width * height];
               if (backgroundPix_.containsKey(channel)) {
                   Arrays.fill((short[]) pixels, (short) getBackgroundPixelValue(channel));
               }
           }
       }
      //go line by line through one column of tiles at a time, then move to next column
      JSONObject topLeftMD = null;
      //first calculate how many columns and rows of tiles are relevant and the number of pixels
      //of each tile to copy into the returned image
      long previousCol = tileIndexFromPixelIndex(x, true) - 1; //make it one less than the first col in loop
      LinkedList<Integer> lineWidths = new LinkedList<Integer>();
      for (long i = x; i < x + width; i++) { //Iterate through every column of pixels in the image to be returned
         long colIndex = tileIndexFromPixelIndex(i, true);
         if (colIndex != previousCol) {
            lineWidths.add(0);
         }
         //Increment current width
         lineWidths.add(lineWidths.removeLast() + 1);
         previousCol = colIndex;
      }
      //do the same thing for rows
      long previousRow = tileIndexFromPixelIndex(y, false) - 1; //one less than first row in loop?
      LinkedList<Integer> lineHeights = new LinkedList<Integer>();
      for (long i = y; i < y + height; i++) {
         long rowIndex = tileIndexFromPixelIndex(i, false);
         if (rowIndex != previousRow) {
            lineHeights.add(0);
         }
         //add one to pixel count of current height
         lineHeights.add(lineHeights.removeLast() + 1);
         previousRow = rowIndex;
      }
      //get starting row and column
      long rowStart = tileIndexFromPixelIndex(y, false);
      long colStart = tileIndexFromPixelIndex(x, true);
      //xOffset and y offset are the distance from the top left of the display image into which 
      //we are copying data
      int xOffset = 0;
      for (long col = colStart; col < colStart + lineWidths.size(); col++) {
         int yOffset = 0;
         for (long row = rowStart; row < rowStart + lineHeights.size(); row++) {
            MagellanTaggedImage tile = null;          
            if (dsIndex == 0) {
               tile = fullResStorage_.getImage(channel, slice, frame, posManager_.getPositionIndexFromTilePosition(dsIndex, row, col));
            } else {               
               tile = lowResStorages_.get(dsIndex) == null ? null : 
                       lowResStorages_.get(dsIndex).getImage(channel, slice, frame, posManager_.getPositionIndexFromTilePosition(dsIndex, row, col));
            }
            if (tile == null) {
               yOffset += lineHeights.get((int)(row - rowStart)); //increment y offset so new tiles appear in correct position
               continue; //If no data present for this tile go on to next one
            } else if ( (tile.pix instanceof byte[] && ((byte[]) tile.pix).length == 0) ||
                    (tile.pix instanceof short[] && ((short[]) tile.pix).length == 0))  {
               //Somtimes an inability to read IFDs soon after they are written results in an image being read 
               //with 0 length pixels. Can't figure out why this happens, but it is rare and will result at worst with
               //a black flickering during acquisition
               yOffset += lineHeights.get((int)(row - rowStart)); //increment y offset so new tiles appear in correct position
               continue;
            }
            //take top left tile for metadata
            if (topLeftMD == null) {
               topLeftMD = tile.tags;
            }
            //Copy pixels into the image to be returned
            //yOffset is how many rows from top of viewable area, y is top of image to top of area
            for (int line = yOffset; line < lineHeights.get((int)(row - rowStart)) + yOffset; line++) {
               int tileYPix = (int) ((y + line) % tileHeight_);
               int tileXPix = (int) ((x + xOffset) % tileWidth_);                   
               //make sure tile pixels are positive
               while (tileXPix < 0) {
                  tileXPix += tileWidth_;
               }
               while (tileYPix < 0) {
                  tileYPix += tileHeight_;
               }
               try {
                  int multiplier = rgb_ ? 4 : 1; 
                  if (dsIndex == 0) {
                     //account for overlaps when viewing full resolution tiles
                     tileYPix += yOverlap_ / 2;
                     tileXPix += xOverlap_ / 2;
                     System.arraycopy(tile.pix, multiplier*(tileYPix * fullResTileWidthIncludingOverlap_ + tileXPix), pixels, (xOffset + width * line)*multiplier, multiplier*lineWidths.get((int) (col - colStart)));                 
                  } else {
                     System.arraycopy(tile.pix, multiplier*(tileYPix * tileWidth_ + tileXPix), pixels, multiplier*(xOffset + width * line), multiplier*lineWidths.get((int)(col - colStart)));
                  }
               } catch (Exception e) {
                  e.printStackTrace();
                  Log.log("Problem copying pixels");
               }
            }
            yOffset += lineHeights.get((int)(row - rowStart));

         }
         xOffset += lineWidths.get((int)(col - colStart));
      }
      return new MagellanTaggedImage(pixels, topLeftMD);
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
         String aLabel = fullResStorage_.imageKeys().iterator().next();
         int[] indices = MD.getIndices(aLabel);
         MagellanTaggedImage anImage = fullResStorage_.getImage(indices[0], indices[1], indices[2], indices[3]);         
         addToLowResStorage(anImage, 0, indices[3]);
         return true;
      }
   }
   
   private void addToLowResStorage(MagellanTaggedImage img, int previousResIndex, int fullResPositionIndex) {
      //Read indices
      int channel = MD.getChannelIndex(img.tags);
      int slice = MD.getSliceIndex(img.tags);
      int frame = MD.getFrameIndex(img.tags);

      if (estimateBackground_) {
         readBackgroundPixelValue(channel, img); //find a background pixel value in the first image
      }
      Object previousLevelPix = img.pix;
      int resolutionIndex = previousResIndex + 1;
      
      //Auto downsample until max number of tiles in either direction is less than the highest dsFactor
      //or keep going until lowest user created resolution exists
      while (lowResStorages_.containsKey(resolutionIndex) ||
              (posManager_.getNumRows() >= Math.pow(2, resolutionIndex) || posManager_.getNumCols() >= Math.pow(2, resolutionIndex)) ) {

         //See if storage level exists
         if (!lowResStorages_.containsKey(resolutionIndex) || lowResStorages_.get(resolutionIndex) == null) {
            createDownsampledStorage(resolutionIndex);
            //add all tiles from existing resolution levels to this new one            
            TaggedImageStorageMultipageTiff previousLevelStorage;
            if (resolutionIndex == 1) {
               previousLevelStorage = fullResStorage_;
            } else {
               previousLevelStorage = lowResStorages_.get(resolutionIndex - 1);
            }            
            Set<String> imageKeys = previousLevelStorage.imageKeys();
            for (String key : imageKeys) {
               String[] indices = key.split("_");
               MagellanTaggedImage ti = previousLevelStorage.getImage(Integer.parseInt(indices[0]), Integer.parseInt(indices[1]),
                       Integer.parseInt(indices[2]), Integer.parseInt(indices[3]));
               addToLowResStorage(ti, resolutionIndex - 1, posManager_.getFullResPositionIndex(Integer.parseInt(indices[3]), resolutionIndex - 1)); 
            }
            return; //this will include the higher res tile intially added, so can return here and
            //not worry about having to add it again
         }
         //Create pixels or get appropriate pixels to add to
         MagellanTaggedImage existingImage = lowResStorages_.get(resolutionIndex).getImage(channel, slice, frame,
                 posManager_.getLowResPositionIndex(fullResPositionIndex, resolutionIndex));
         Object currentLevelPix;
         if (existingImage == null) {
             if (rgb_) {
                 currentLevelPix = new byte[tileWidth_ * tileHeight_ * 4];
             } else if (byteDepth_ == 1) {
                 currentLevelPix = new byte[tileWidth_ * tileHeight_];
             } else {
                 currentLevelPix = new short[tileWidth_ * tileHeight_];
             }         
            //fill in with background pixel value
            if (rgb_) {
                //whatever
            }else if (byteDepth_ == 1) {
               Arrays.fill((byte[]) currentLevelPix, (byte) getBackgroundPixelValue(channel));
            } else {
               Arrays.fill((short[]) currentLevelPix, (short) getBackgroundPixelValue(channel));
            }
         } else {
            currentLevelPix = existingImage.pix; 
         }     

         
         //Determine which position in 2x2 this tile sits in
         int xPos = (int) Math.abs((posManager_.getGridCol(fullResPositionIndex, resolutionIndex - 1) % 2));
         int yPos = (int) Math.abs((posManager_.getGridRow(fullResPositionIndex, resolutionIndex - 1) % 2));
         //Add one if top or left so border pixels from an odd length image gets added in
         for (int x = 0; x < tileWidth_; x += 2) { //iterate over previous res level pixels
            for (int y = 0; y < tileHeight_; y += 2) {
               //average a square of 4 pixels from previous level
               //edges: if odd number of pixels in tile, round to determine which
               //tiles pixels make it to next res level
               
               //these are the indices of pixels at the previous res level, which are offset
               //when moving from res level 0 to one as we throw away the overlapped image edges
               int pixelX, pixelY, previousLevelWidth, previousLevelHeight; 
               if ( resolutionIndex == 1 ) {                  
                  //add offsets to account for overlap pixels at resolution level 0
                  pixelX = x + xOverlap_ / 2;
                  pixelY = y + yOverlap_ /2;
                  previousLevelWidth = fullResTileWidthIncludingOverlap_;
                  previousLevelHeight = fullResTileHeightIncludingOverlap_;
               } else {
                  pixelX = x;
                  pixelY = y;
                  previousLevelWidth = tileWidth_;
                  previousLevelHeight = tileHeight_;

               }
                int rgbMultiplier_ = rgb_ ? 4 : 1;
                for (int compIndex = 0; compIndex < (rgb_ ? 3 : 1); compIndex++) {
                    int count = 1; //count is number of pixels (out of 4) used to create a pixel at this level
                    //always take top left pixel, maybe take others depending on whether at image edge
                    int sum = 0;
                    if (byteDepth_ == 1 || rgb_) {
                        sum += ((byte[]) previousLevelPix)[(pixelY * previousLevelWidth + pixelX)*rgbMultiplier_ + compIndex] & 0xff;
                    } else {
                        sum += ((short[]) previousLevelPix)[(pixelY * previousLevelWidth + pixelX)*rgbMultiplier_ + compIndex] & 0xffff;
                    }

                    //pixel index can be different from index in tile at resolution level 0 if there is nonzero overlap
                    if (x < previousLevelWidth - 1 && y < previousLevelHeight - 1) { //if not bottom right corner, add three more pix
                        count += 3;
                        if (byteDepth_ == 1 || rgb_) {
                            sum += (((byte[]) previousLevelPix)[((pixelY + 1) * previousLevelWidth + pixelX + 1)*rgbMultiplier_+compIndex] & 0xff)
                                    + (((byte[]) previousLevelPix)[(pixelY * previousLevelWidth + pixelX + 1)*rgbMultiplier_+compIndex] & 0xff)
                                    + (((byte[]) previousLevelPix)[((pixelY + 1) * previousLevelWidth + pixelX)*rgbMultiplier_+compIndex] & 0xff);
                        } else {
                            sum += (((short[]) previousLevelPix)[((pixelY + 1) * previousLevelWidth + pixelX + 1)*rgbMultiplier_+compIndex] & 0xffff)
                                    + (((short[]) previousLevelPix)[(pixelY * previousLevelWidth + pixelX + 1)*rgbMultiplier_+compIndex] & 0xffff)
                                    + (((short[]) previousLevelPix)[((pixelY + 1) * previousLevelWidth + pixelX)*rgbMultiplier_+compIndex] & 0xffff);
                        }
                    } else if (x < previousLevelWidth - 1) { //if not right edge, add one more pix
                        count++;
                        if (byteDepth_ == 1 || rgb_) {
                            sum += ((byte[]) previousLevelPix)[(pixelY * previousLevelWidth + pixelX + 1)*rgbMultiplier_+compIndex] & 0xff;
                        } else {
                            sum += ((short[]) previousLevelPix)[(pixelY * previousLevelWidth + pixelX + 1)*rgbMultiplier_+compIndex] & 0xffff;
                        }
                    } else if (y < previousLevelHeight - 1) { // if not bottom edge, add one more pix
                        count++;
                        if (byteDepth_ == 1 || rgb_) {
                            sum += ((byte[]) previousLevelPix)[((pixelY + 1) * previousLevelWidth + pixelX)*rgbMultiplier_+compIndex] & 0xff;
                        } else {
                            sum += ((short[]) previousLevelPix)[((pixelY + 1) * previousLevelWidth + pixelX)*rgbMultiplier_+compIndex] & 0xffff;
                        }
                    } else {
                        //it is the bottom right corner, no more pix to add
                    }
                    //add averaged pixel into appropriate quadrant of current res level
                    //if full res tile has an odd number of pix, the last one gets chopped off
                    //to make it fit into tile containers
                    try {
                        int index = (((y + yPos * tileHeight_) / 2) * tileWidth_ + (x + xPos * tileWidth_) / 2)*rgbMultiplier_+compIndex;
                        if (byteDepth_ == 1 || rgb_) {
                            ((byte[]) currentLevelPix)[index] = (byte) Math.round(sum / count);
                        } else {
                            ((short[]) currentLevelPix)[index] = (short) Math.round(sum / count);
                        }
                    } catch (Exception e) {
                        Log.log("Couldn't copy pixels to lower resolution");
                        e.printStackTrace();
                        return;
                    }

                }
            }
         }
         
         //store this tile in the storage class correspondign to this resolution
         try {
            if (existingImage == null) {     //Image doesn't yet exist at this level, so add it
               //create a copy of tags so tags from a different res level arent inadverntanly modified
               // while waiting for being written to disk
               JSONObject tags = new JSONObject(img.tags.toString());
               //modify tags to reflect image size, and correct position index
               MD.setWidth(tags, tileWidth_);
               MD.setHeight(tags, tileHeight_);
               long gridRow = posManager_.getGridRow(fullResPositionIndex, resolutionIndex);
               long gridCol = posManager_.getGridCol(fullResPositionIndex, resolutionIndex);
               MD.setPositionName(tags, "Grid_" + gridRow + "_" + gridCol);
               MD.setPositionIndex(tags, posManager_.getLowResPositionIndex(fullResPositionIndex, resolutionIndex));  
               lowResStorages_.get(resolutionIndex).putImage(new MagellanTaggedImage(currentLevelPix, tags));
            } else {
               //Image already exists, only overwrite pixels to include new tiles
               lowResStorages_.get(resolutionIndex).overwritePixels(currentLevelPix,
                       channel, slice, frame, posManager_.getLowResPositionIndex(fullResPositionIndex, resolutionIndex));
            }
         } catch (Exception e) {
            e.printStackTrace();
            Log.log("Couldnt modify tags for lower resolution level");
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
         Log.log("copuldnt create directory");
      }
      try {
         JSONObject smd = new JSONObject(summaryMD_.toString());
         //reset dimensions so that overlap not included
         MD.setWidth(smd, tileWidth_);
         MD.setHeight(smd, tileHeight_);
         TaggedImageStorageMultipageTiff storage = new TaggedImageStorageMultipageTiff(dsDir, true, smd);
         lowResStorages_.put(resIndex, storage);
      } catch (Exception ex) {
         Log.log("Couldnt create downsampled storage");
      }
   }

   public void putImage(MagellanTaggedImage MagellanTaggedImage)  {
      try {
         synchronized (this) {            
            //write to full res storage as normal (i.e. with overlap pixels present)
            fullResStorage_.putImage(MagellanTaggedImage);
            addToLowResStorage(MagellanTaggedImage, 0, MD.getPositionIndex(MagellanTaggedImage.tags));
            if (currentTP_ < MD.getFrameIndex(MagellanTaggedImage.tags)) {
               bdvXML_.addTP();
               currentTP_ = MD.getFrameIndex(MagellanTaggedImage.tags);
            }
         }
      } catch (IOException ex) {
         Log.log(ex.toString());
      } 
   }
   
   public MagellanTaggedImage getImage(int channelIndex, int sliceIndex, int frameIndex, int positionIndex, int resLevel) {
      if (resLevel == 0) {
         return fullResStorage_.getImage(channelIndex, sliceIndex, frameIndex, positionIndex);
      } else {
         return lowResStorages_.get(resLevel).getImage(channelIndex, sliceIndex, frameIndex, positionIndex);
      }
   }

   public MagellanTaggedImage getImage(int channelIndex, int sliceIndex, int frameIndex, int positionIndex) {
      //return a single tile from the full res image
      return fullResStorage_.getImage(channelIndex, sliceIndex, frameIndex, positionIndex);
   }

   public JSONObject getImageTags(int channelIndex, int sliceIndex, int frameIndex, int positionIndex) {
      return getImage(channelIndex, sliceIndex, frameIndex, positionIndex).tags;
   }

   public Set<String> imageKeys() {
      return fullResStorage_.imageKeys();
   }

   public void finished() {
      try {
         if (bdvXML_ != null) { //if its not an oened dataset
            bdvXML_.close();
         }
      } catch (IOException ex) {
         Log.log("Couldn't close BDV XML");
      }
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

   public boolean isFinished() {
      return finished_;
   }

   public void setSummaryMetadata(JSONObject md) {
      fullResStorage_.setSummaryMetadata(md);
   }

   public JSONObject getSummaryMetadata() {
      return fullResStorage_.getSummaryMetadata();
   }

   public void setDisplayAndComments(JSONObject settings) {
      fullResStorage_.setDisplayAndComments(settings);
   }

   public JSONObject getDisplayAndComments() {
      return fullResStorage_.getDisplayAndComments();
   }

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

   public String getDiskLocation() {
      //For display purposes
      return directory_;
   }
   
   public String getChannelName(int index) {
      try {
         return summaryMD_.getJSONArray("ChNames").getString(index);
      } catch (JSONException ex) {
         throw new RuntimeException("Channel names missing");
      }
   }
   
   public int getNumChannels() {
      return fullResStorage_.getNumChannels();
   }

   public int getNumFrames() {
      return fullResStorage_.getMaxFrameIndexOpenedDataset() + 1;
   }
   
   public int getNumSlices() {
      return fullResStorage_.getMaxSliceIndexOpenedDataset() - fullResStorage_.getMinSliceIndexOpenedDataset() + 1;
   }
   
   public int getMinSliceIndexOpenedDataset() {
      return fullResStorage_.getMinSliceIndexOpenedDataset();
   }
   
   public int getMaxSliceIndexOpenedDataset() {
      return fullResStorage_.getMaxSliceIndexOpenedDataset();
   }

   public long getDataSetSize() {
      long sum = 0;
      sum += fullResStorage_.getDataSetSize();
      for (TaggedImageStorageMultipageTiff s : lowResStorages_.values()) {
         sum += s.getDataSetSize();
      }
      return sum;
   }

   public void writeDisplaySettings() {
      //who cares
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

   /**
    * 
    * @param slice
    * @return set of points (col, row) with indices of tiles that have been added at this slice index 
    */
   public Set<Point> getExploredTilesAtSlice(int slice) {
      Set<Point> exploredTiles = new TreeSet<Point>(new Comparator<Point>() {
         @Override
         public int compare(Point o1, Point o2) {
           if (o1.x != o2.x) {
              return o1.x - o2.x;
           } else if (o1.y != o2.y) {
              return o1.y - o2.y;
           }
           return 0;
         }
      } );
      Set<String> keys = new TreeSet<String>(imageKeys());
      for (String s : keys) {
            int[] indices = MD.getIndices(s);
            if (indices[1] == slice) {
               exploredTiles.add(new Point((int) posManager_.getGridCol(indices[3], 0), (int) posManager_.getGridRow(indices[3], 0)));
            }

      }
      return exploredTiles;
   }
   
   public long getMinRow() {
      return posManager_.getMinRow();
   }
   
   
   public long getMinCol() {
      return posManager_.getMinCol();
   }

    int getPositionIndexFromStageCoords(double xPos, double yPos) {
        return posManager_.getFullResPositionIndexFromStageCoords(xPos, yPos);
    }
   
}
