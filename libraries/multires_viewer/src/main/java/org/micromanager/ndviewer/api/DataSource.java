/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.ndviewer.api;

import java.util.HashMap;
import mmcorej.TaggedImage;

/**
 * Interface for a multiresolution data source
 *
 * @author henrypinkard
 */
public interface DataSource {

   /**
    * The minimal and maximal pixel coordinates of the image to be viewed
    * 
    * @return 4 element array x_min, y_min, x_max, y_max
    */
   public int[] getImageBounds();

   /**
    * Retrieve image with the given parameters so it can be displayed
    *
    * @param me of the channel
    * @param axes Map of axes to indices (e.g. "z": 0, "t": 1) (Note: no position
    * needed for channels as this is automatically inferred
    * @param resolutionIndex Index in level of multiresolution pyramid. (0 is
    * full resolution, 1 is downsampled by 2x, 2 is downsampled by 4x, etc
    * @param xOffset leftmost pixel at the requested resolution
    * @param yOffset rightmost pixel at the requested resolution
    * @param imageWidth pixel width of the image at the requested resolution
    * @param imageHeight pixel height of the image at the requested resolution
    * @return
    */
   public TaggedImage getImageForDisplay(String channelName, HashMap<String, Integer> axes,
           int resolutionindex, double xOffset, double yOffset,
           int imageWidth, int imageHeight);

   /**
    * Index of the log 2 biggest downsample factor in the pyramid 0 is full
    * resolution, 1 is downsampled by 2x, 2 is downsampled by 4x, etc
    *
    * @return
    */
   public int getMaxResolutionIndex();

   /**
    * Path to where the data is stored on disk, if applicable
    *
    * @return
    */
   public String getDiskLocation();

   /**
    * Called when the viewer closes
    */
   public void viewerClosing();

}
