package org.micromanager.ndviewer2;

import java.util.HashMap;
import org.micromanager.data.DataProvider;
import org.micromanager.data.Image;

/**
 * Public interface for the NDViewer2 data provider.
 *
 * <p>Use {@link NDViewer2Factory#createDataProvider} to obtain an instance.</p>
 */
public interface NDViewer2DataProviderAPI extends DataProvider {

   /**
    * Notify this data provider that a new image has arrived.
    * Uses the provided Image directly and derives the channel from the axes map.
    *
    * @param image the image that arrived
    * @param axes  the NDViewer axes of the image (e.g. {channel: "DAPI", ...})
    */
   void newImageArrived(Image image, HashMap<String, Object> axes);
}
