package org.micromanager.tileddataviewer;

import java.util.HashMap;
import java.util.Set;
import mmcorej.TaggedImage;

/**
 * Interface for a source of image data. This data can be multi-resolution,
 * though this isn't required
 *
 * @author henrypinkard
 */
public interface TiledDataViewerDataSource {


   /**
    * Is the dataset still acquiring data/be written.
    */
   boolean isFinished();

   /**
    * The minimal and maximal pixel coordinates of the image to be viewed.
    *
    * <p>Returning {@code null} means "unbounded": the viewer will not clamp panning or
    * zooming to the data. Sources that want free navigation return null here, but should
    * still implement {@link #getFullResolutionSize()} so the viewer knows how large the data
    * actually is.</p>
    *
    * @return 4 element array x_min, y_min, x_max, y_max
    */
   int[] getBounds();

   /**
    * Size of the whole dataset in full-resolution pixels, independent of any view bounds.
    *
    * <p>Unlike {@link #getBounds()} this is purely informational -- reporting it does not
    * make the viewer clamp navigation to the data. The viewer uses it to keep zoom-out
    * within a sensible multiple of the data size; without it, it has no idea whether the
    * dataset is a thousand pixels across or a million, and has to guess.</p>
    *
    * <p>The default returns null ("unknown"), which is correct for sources that cannot
    * determine their extent. Implementations backed by a tiled storage should return the
    * real value, typically derived from the storage's image bounds.</p>
    *
    * @return 2 element array {width, height} in full-resolution pixels, or null if unknown
    */
   default int[] getFullResolutionSize() {
      return null;
   }

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
