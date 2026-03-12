package org.micromanager.tileddataviewer;

import java.io.IOException;
import java.util.HashMap;
import org.micromanager.data.DataProvider;
import org.micromanager.data.Image;
import org.micromanager.pyramidalstorage.PyramidalStorageAPI;

/**
 * Public interface for the NDViewer2 data provider.
 *
 * <p>Use {@link TiledDataViewerFactory#createDataProvider} to obtain an instance.</p>
 */
public interface TiledDataViewerDataProviderAPI extends DataProvider {

   /**
    * Notify this data provider that a new image has arrived.
    * Uses the provided Image directly and derives the channel from the axes map.
    *
    * @param image the image that arrived
    * @param axes  the NDViewer axes of the image (e.g. {channel: "DAPI", ...})
    */
   void newImageArrived(Image image, HashMap<String, Object> axes);

   /**
    * Fetch a downsampled (coarsest pyramid level) version of the image by NDViewer axes.
    *
    * @param axes the NDViewer axes map
    * @return downsampled image, or null if not found
    * @throws IOException if conversion from TaggedImage fails
    */
   Image getDownsampledImageByAxes(HashMap<String, Object> axes) throws IOException;

   /**
    * Return the storage backend for read-only access (e.g. for export).
    */
   PyramidalStorageAPI getStorage();
}
