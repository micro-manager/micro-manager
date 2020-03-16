/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.multiresstorage;

import java.awt.Point;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import mmcorej.TaggedImage;
import org.json.JSONObject;

/**
 *
 * @author henrypinkard
 */
public interface StorageAPI {
   
    /**
     * Get a set of the (row, col) indices at which data has been acquired at this 
     * @param zIndex
     * @return 
     */
   public Set<Point> getTileIndicesWithDataAt(int zIndex);

   /**
    * Add an image into storage, not making use of multi-resolution/stitched image
    * features
    * 
    * @param taggedImg
    * @param axes
    * @param row
    * @param col 
    */
   public void putImage(TaggedImage taggedImg, HashMap<String, Integer> axes);
   
   /**
    * Add an image into storage, which corresponds to a particular row/column in 
    * a larger stitched image 
    * 
    * @param taggedImg
    * @param axes
    * @param row
    * @param col 
    */
   public void putImage(TaggedImage taggedImg, HashMap<String, Integer> axes, int row, int col);

   /**
    * Is this dataset finished writing and now read only
    * @return 
    */
   public boolean isFinished();

   /**
    * Set display settings for storage. No particular structure required
    * @param displaySettings 
    */
   public void setDisplaySettings(JSONObject displaySettings);

   /**
    * The display settings for this dataset
    * Note: the storage only specifies that these are a JSON object, 
    * but nothing about their format. It only stores them
    * @return 
    */
   public JSONObject getDisplaySettings();

   /**
    * 
    * @return the summary metadata for this dataset
    */
   public JSONObject getSummaryMetadata();

   /**
    * Called when no more data will be written to this dataset (but reading still allowed)
    */
   public void finishedWriting();

   /**
    * Get the path to the top level folder where this dataset is
    * @return 
    */
   public String getDiskLocation();

   /**
    * Release all resources
    */
   public void close();

   /**
    * [x_min, y_min, x_max, y_max] bounds where data has been acquired (can be negative)
    * @return 
    */
   public int[] getImageBounds();

   /**
    * return number of resolutions if this is a multiresolution pyramid
    * @return 
    */
   public int getNumResLevels();


   /**
    * Get a single stitched image that spans multiple tiles
    * 
    * @param axes 
    * @param resIndex 0 is full resolution, 1 is downsampled x2, 2 is downsampled x4, etc
    * @param xOffset leftmost pixel in the requested resolution level
    * @param yOffset topmost pixel in the requested resolution level
    * @param imageWidth width of the returned image
    * @param imageHeight height of the returned image
    * @return 
    */
   public TaggedImage getStitchedImage(HashMap<String, Integer> axes,
           int resIndex,
           int xOffset, int yOffset,
           int imageWidth, int imageHeight);
   
   /**
    * Get a single image from full resolution data
    * 
    * @param axes 
    * @return 
    */
   public TaggedImage getImage(HashMap<String, Integer> axes);

   /**
    * Get a set containing all image axes in this dataset (used for loaded data)
    * @return 
    */
   public Set<HashMap<String, Integer>> getAxesSet();

}
