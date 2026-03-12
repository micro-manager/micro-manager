package org.micromanager.ndviewer2;

import java.util.HashMap;
import java.util.Set;
import mmcorej.TaggedImage;

/**
 * Interface for a source of image data. This data can be multi-resolution,
 * though this isn't required
 *
 * @author henrypinkard
 */
public interface NDViewer2DataSource {


   /**
    * Is the dataset still acquiring data/be written.
    */
   boolean isFinished();

   /**
    * The minimal and maximal pixel coordinates of the image to be viewed.
    *
    * @return 4 element array x_min, y_min, x_max, y_max
    */
   int[] getBounds();

   /**
    * Retrieve image with the given parameters so it can be displayed.
    *
    * @param axes Map of axes to indices (e.g. "z": 0, "t": 1) (Note: no position
    *     needed for channels as this is automatically inferred
    * @param resolutionindex Index in level of multiresolution pyramid. (0 is
    *     full resolution, 1 is downsampled by 2x, 2 is downsampled by 4x, etc
    * @param xOffset leftmost pixel at the requested resolution
    * @param yOffset rightmost pixel at the requested resolution
    * @param imageWidth pixel width of the image at the requested resolution
    * @param imageHeight pixel height of the image at the requested resolution
    * @return taggedImage
    */
   TaggedImage getImageForDisplay(HashMap<String, Object> axes,
           int resolutionindex, double xOffset, double yOffset,
           int imageWidth, int imageHeight);

   /**
    * Get the axes of all available images in this dataset.
    *
    * @return
    */
   Set<HashMap<String, Object>> getImageKeys();

   /**
    * Index of the log 2 biggest downsample factor in the pyramid 0 is full
    * resolution, 1 is downsampled by 2x, 2 is downsampled by 4x, etc..
    * For a non-multi resolution source, should pass 0
    *
    * @return
    */
   int getMaxResolutionIndex();

   /**
    * Viewer will be viewing at this res index, so make sure it exists.
    *
    * @param newMaxResolutionLevel
    */
   void increaseMaxResolutionLevel(int newMaxResolutionLevel);


   /**
    * Path to where the data is stored on disk, if applicable.
    *
    * @return Path to data stored on disk
    */
   String getDiskLocation();

   /**
    * Called when viewer is closing.
    */
   void close();

   /**
    * Get the bits per pixel of image with the given axes positions.
    *
    * @param axesPositions
    * @return
    */
   int getImageBitDepth(HashMap<String, Object> axesPositions);
}
