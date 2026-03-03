package org.micromanager.ndviewer2;

import mmcorej.org.json.JSONObject;
import org.micromanager.Studio;
import org.micromanager.ndtiffstorage.MultiresNDTiffAPI;
import org.micromanager.ndviewer2.NDViewer2DataProviderAPI;
import org.micromanager.ndviewer2.NDViewer2DataViewerAPI;
import org.micromanager.ndviewer2.NDViewerAcqInterface;
import org.micromanager.ndviewer2.NDViewerDataSource;
import org.micromanager.ndviewer2.internal.NDViewer2DataProvider;
import org.micromanager.ndviewer2.internal.NDViewer2DataViewer;

/**
 * Factory for creating NDViewer2 data providers and viewers.
 *
 * <p>External code should use this factory rather than constructing
 * {@code NDViewer2DataProvider} or {@code NDViewer2DataViewer} directly,
 * so that the concrete implementations can change without breaking callers.</p>
 */
public final class NDViewer2Factory {

   private NDViewer2Factory() {
      // static factory — no instances
   }

   /**
    * Create a new NDViewer2 data provider wrapping the given NDTiff storage.
    *
    * @param storage the NDTiff storage backend
    * @param name    display name for this data provider
    * @return a new NDViewer2DataProviderAPI instance
    */
   public static NDViewer2DataProviderAPI createDataProvider(
         MultiresNDTiffAPI storage, String name) {
      return new NDViewer2DataProvider(storage, name);
   }

   /**
    * Create a new NDViewer2 data viewer.
    *
    * @param studio          the MM Studio instance
    * @param dataSource      NDViewer data source
    * @param acqInterface    acquisition control interface (may be null)
    * @param dataProvider    the data provider created by {@link #createDataProvider}
    * @param summaryMetadata NDTiff summary metadata JSON
    * @param pixelSizeUm     pixel size in micrometers
    * @param rgb             whether images are RGB
    * @return a new NDViewer2DataViewerAPI instance
    */
   public static NDViewer2DataViewerAPI createDataViewer(
         Studio studio,
         NDViewerDataSource dataSource,
         NDViewerAcqInterface acqInterface,
         NDViewer2DataProviderAPI dataProvider,
         JSONObject summaryMetadata,
         double pixelSizeUm,
         boolean rgb) {
      return new NDViewer2DataViewer(studio, dataSource, acqInterface,
            (NDViewer2DataProvider) dataProvider,
            summaryMetadata, pixelSizeUm, rgb);
   }
}
