package org.micromanager.tileddataviewer;

import mmcorej.org.json.JSONObject;
import org.micromanager.Studio;
import org.micromanager.data.DataManager;
import org.micromanager.tileddataviewer.internal.TiledDataViewerDataProvider;
import org.micromanager.tileddataviewer.internal.TiledDataViewerDataViewer;
import org.micromanager.pyramidalstorage.PyramidalStorageAPI;

/**
 * Factory for creating NDViewer2 data providers and viewers.
 *
 * <p>External code should use this factory rather than constructing
 * {@code org.micromanager.ndviewer2.internal.NDViewer2DataProvider} or
 * {@code org.micromanager.ndviewer2.internal.NDViewer2DataViewer} directly,
 * so that the concrete implementations can change without breaking callers.</p>
 */
public final class TiledDataViewerFactory {

   private TiledDataViewerFactory() {
      // static factory — no instances
   }

   /**
    * Create a new NDViewer2 data provider wrapping the given storage.
    *
    * @param dataManager the MM DataManager for creating Image and SummaryMetadata objects
    * @param storage     the storage backend (use {@code new NDTiffStorageAdapter(multiresNDTiff)} to convert MultiresNDTiffAPI)
    * @param name        display name for this data provider
    * @return a new NDViewer2DataProviderAPI instance
    */
   public static TiledDataViewerDataProviderAPI createDataProvider(
         DataManager dataManager, PyramidalStorageAPI storage, String name) {
      return new TiledDataViewerDataProvider(dataManager, storage, name);
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
   public static TiledDataViewerDataViewerAPI createDataViewer(
         Studio studio,
         TiledDataViewerDataSource dataSource,
         TiledDataViewerAcqInterface acqInterface,
         TiledDataViewerDataProviderAPI dataProvider,
         JSONObject summaryMetadata,
         double pixelSizeUm,
         boolean rgb) {
      return new TiledDataViewerDataViewer(
               studio,
               dataSource,
               acqInterface,
               (TiledDataViewerDataProvider) dataProvider,
               summaryMetadata,
               pixelSizeUm,
               rgb);
   }
}
