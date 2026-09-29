package org.micromanager.tileddataprovider;

import java.io.IOException;
import org.micromanager.ndtiffstorage.MultiresNDTiffAPI;
import org.micromanager.ndtiffstorage.NDTiffStorage;

/**
 * Opens an existing tiled dataset with whichever storage backend matches it.
 *
 * <p>All four backends implement {@link MultiresNDTiffAPI}, so callers can treat the result
 * uniformly and wrap it with {@link NDTiffProviderAdapter} to obtain a
 * {@link TiledDataProviderAPI}.
 *
 * <p>This dispatch used to be copied into each caller. Keeping one copy here means a new
 * backend, or a change to how an existing one is recognized, is picked up everywhere at once.
 */
public final class TiledDataProviderFactory {

   /** Size of NDTiff's write queue. Irrelevant when reading, but the constructor wants it. */
   private static final int SAVING_QUEUE_SIZE = 30;

   private TiledDataProviderFactory() {
      // static factory - no instances
   }

   /**
    * Opens the dataset at {@code dir} read-only.
    *
    * <p>{@code dir} must be a dataset root, as returned by
    * {@link DatasetPathUtils#normalizeDatasetPath}; this method does not search for one.
    *
    * @param dir root directory of the dataset
    * @return the storage backend for the dataset
    * @throws IOException if the dataset cannot be read
    */
   public static MultiresNDTiffAPI openExisting(String dir) throws IOException {
      if (OMEZarrMultiresStorage.isOMEZarrDataset(dir)) {
         return new OMEZarrMultiresStorage(dir);
      }
      if (OMEBigTiffMultiresStorage.isOMEBigTiffDataset(dir)) {
         // A single-plane tiled dataset (e.g. from Stitch) must use the tiled bridge; the
         // per-tile bridge only handles the one-file-per-tile layout.
         return OMEBigTiffTiledStorage.isTiledDataset(dir)
               ? new OMEBigTiffTiledStorage(dir)
               : new OMEBigTiffMultiresStorage(dir);
      }
      return new NDTiffStorage(dir, SAVING_QUEUE_SIZE, null);
   }
}
