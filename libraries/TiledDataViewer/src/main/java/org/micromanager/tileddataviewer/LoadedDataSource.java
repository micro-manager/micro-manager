package org.micromanager.tileddataviewer;

import java.util.Collections;
import java.util.HashMap;
import java.util.Set;
import java.util.stream.Collectors;
import mmcorej.TaggedImage;
import org.micromanager.ndtiffstorage.MultiresNDTiffAPI;
import org.micromanager.ndtiffstorage.NDTiffStorage;

/**
 * A {@link TiledDataViewerDataSource} for a dataset that is being read from disk.
 *
 * <p>Every method delegates to the storage backend. There is deliberately no stage, acquisition
 * or region-selection behaviour here: this source exists to display data that has already been
 * written, and the viewer's acquisition controls are not wired up for it.
 *
 * <p>Closing is handled by a callback rather than by this class, so that whoever opened the
 * dataset can save display settings before the storage is shut down.
 */
public class LoadedDataSource implements TiledDataViewerDataSource {

   private final MultiresNDTiffAPI storage_;
   private final Runnable onClose_;
   private Set<HashMap<String, Object>> imageKeysCache_;

   /**
    * Creates a read-only source over an already-opened storage backend.
    *
    * @param storage the opened dataset
    * @param onClose run when the viewer closes, before the storage is released; may be null
    */
   public LoadedDataSource(MultiresNDTiffAPI storage, Runnable onClose) {
      storage_ = storage;
      onClose_ = onClose;
   }

   @Override
   public boolean isFinished() {
      // Nothing is being written to a loaded dataset.
      return true;
   }

   @Override
   public int[] getBounds() {
      // Null leaves panning and zooming unclamped, which is what a loaded dataset wants: the
      // extent is reported through getFullResolutionSize() instead, so the viewer can bound
      // zoom-out without restricting navigation.
      return null;
   }

   @Override
   public int[] getFullResolutionSize() {
      if (storage_ == null) {
         return null;
      }
      // getImageBounds() is declared on NDTiffAPI, so this works for the OME backends too, not
      // only NDTiff. It returns {xMin, yMin, xMax, yMax}; note that the TiledDataProviderAPI
      // javadoc documents a different order, which does not match the implementation.
      int[] b = storage_.getImageBounds();
      if (b == null || b.length < 4) {
         return null;
      }
      int width = b[2] - b[0];
      int height = b[3] - b[1];
      if (width <= 0 || height <= 0) {
         return null;
      }
      return new int[]{width, height};
   }

   @Override
   public TaggedImage getImageForDisplay(HashMap<String, Object> axes, int resolutionIndex,
                                         double xOffset, double yOffset,
                                         int imageWidth, int imageHeight) {
      if (storage_ == null) {
         return null;
      }
      // The storage composites the mosaic from its tiles internally.
      return storage_.getDisplayImage(axes, resolutionIndex,
            (int) xOffset, (int) yOffset, imageWidth, imageHeight);
   }

   @Override
   public Set<HashMap<String, Object>> getImageKeys() {
      if (storage_ == null) {
         return Collections.emptySet();
      }
      Set<HashMap<String, Object>> cached = imageKeysCache_;
      if (cached != null) {
         return cached;
      }
      // Row and column identify a tile within the mosaic, not a position the user can scroll
      // to, so they are stripped before the viewer builds its scrollbars from these keys.
      Set<HashMap<String, Object>> fresh = storage_.getAxesSet().stream()
            .map(axes -> {
               HashMap<String, Object> copy = new HashMap<>(axes);
               copy.remove(NDTiffStorage.ROW_AXIS);
               copy.remove(NDTiffStorage.COL_AXIS);
               return copy;
            })
            .collect(Collectors.toSet());
      // Wrapped because this instance is cached and handed to every caller: an accidental
      // mutation downstream would corrupt the shared cache rather than a local copy.
      Set<HashMap<String, Object>> result = Collections.unmodifiableSet(fresh);
      imageKeysCache_ = result;
      return result;
   }

   @Override
   public int getMaxResolutionIndex() {
      if (storage_ == null) {
         return 0;
      }
      return storage_.getNumResLevels() - 1;
   }

   @Override
   public void increaseMaxResolutionLevel(int newMaxResolutionLevel) {
      // A loaded dataset is read-only: new resolution levels cannot be written, and asking the
      // storage to add one would try to write into the dataset directory.
   }

   @Override
   public String getDiskLocation() {
      if (storage_ == null) {
         return null;
      }
      return storage_.getDiskLocation();
   }

   @Override
   public void close() {
      if (onClose_ != null) {
         onClose_.run();
      }
   }

   @Override
   public int getImageBitDepth(HashMap<String, Object> axesPositions) {
      if (storage_ == null) {
         return 16;
      }
      try {
         for (HashMap<String, Object> storedAxes : storage_.getAxesSet()) {
            return storage_.getEssentialImageMetadata(storedAxes).bitDepth;
         }
      } catch (RuntimeException e) {
         // Fall through to the default below.
      }
      return 16;
   }
}
