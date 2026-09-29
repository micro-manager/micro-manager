package org.micromanager.tileddataviewer;

import java.io.IOException;
import java.util.HashMap;
import org.micromanager.data.DataProvider;
import org.micromanager.data.Image;
import org.micromanager.tileddataprovider.TiledDataProviderAPI;

/**
 * Public interface for the TiledDataViewer data provider.
 *
 * <p>Use {@link TiledDataViewerFactory#createDataProvider} to obtain an instance.</p>
 */
public interface TiledDataViewerDataProviderAPI extends DataProvider {

   /**
    * Notify this data provider that a new image has arrived.
    * Uses the provided Image directly and derives the channel from the axes map.
    *
    * @param image the image that arrived
    * @param axes  the TiledDataViewer axes of the image (e.g. {channel: "DAPI", ...})
    */
   void newImageArrived(Image image, HashMap<String, Object> axes);

   /**
    * Notify this data provider that a new image has arrived at the given axes.
    * Re-reads the image from storage so that per-image metadata tags are included.
    *
    * @param axes the TiledDataViewer axes of the image (for instance:
    *            {row: 0, column: 0, channel: "DAPI", ...})
    */
   void newImageArrived(HashMap<String, Object> axes);

   /**
    * Fetch a downsampled (coarsest pyramid level) version of the image by TiledDataViewer axes.
    *
    * @param axes the TiledDataViewer axes map
    * @return downsampled image, or null if not found
    * @throws IOException if conversion from TaggedImage fails
    */
   Image getDownsampledImageByAxes(HashMap<String, Object> axes) throws IOException;

   /**
    * Return the MM channel index this provider assigned to a channel name.
    *
    * <p>Channels are registered as images arrive, so a channel that appeared after the
    * dataset was created still has an index here even though it is absent from the summary
    * metadata. Callers use this to address the channel in DisplaySettings.</p>
    *
    * @param channelName the channel name as it appears in the axes map
    * @return the MM channel index, or -1 if the name is not known
    */
   int getChannelIndex(String channelName);

   /**
    * Return the storage backend for read-only access (e.g. for export).
    */
   TiledDataProviderAPI getStorage();
}
