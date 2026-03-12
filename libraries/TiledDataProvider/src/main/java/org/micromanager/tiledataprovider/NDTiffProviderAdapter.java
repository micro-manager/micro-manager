package org.micromanager.tiledataprovider;

import java.util.HashMap;
import java.util.Set;
import mmcorej.TaggedImage;
import mmcorej.org.json.JSONObject;
import org.micromanager.ndtiffstorage.MultiresNDTiffAPI;

/**
 * Adapts {@link MultiresNDTiffAPI} to the narrow {@link TiledDataProviderAPI} read interface.
 *
 * <p>Callers that need write access (putImage, finishedWriting, close, etc.) keep their
 * own {@code MultiresNDTiffAPI} reference. This adapter is only for the read path.</p>
 */
public final class NDTiffProviderAdapter implements TiledDataProviderAPI {

   private final MultiresNDTiffAPI storage_;

   public NDTiffProviderAdapter(MultiresNDTiffAPI storage) {
      storage_ = storage;
   }

   @Override
   public Set<HashMap<String, Object>> getAxesSet() {
      return storage_.getAxesSet();
   }

   @Override
   public TaggedImage getImage(HashMap<String, Object> axes) {
      return storage_.getImage(axes);
   }

   @Override
   public TaggedImage getImage(HashMap<String, Object> axes, int resolutionLevel) {
      return storage_.getImage(axes, resolutionLevel);
   }

   @Override
   public boolean hasImage(HashMap<String, Object> axes) {
      return storage_.hasImage(axes);
   }

   @Override
   public JSONObject getSummaryMetadata() {
      return storage_.getSummaryMetadata();
   }

   @Override
   public boolean isFinished() {
      return storage_.isFinished();
   }

   @Override
   public int getNumResLevels() {
      return storage_.getNumResLevels();
   }

   @Override
   public String getDiskLocation() {
      return storage_.getDiskLocation();
   }

   @Override
   public TaggedImage getDisplayImage(HashMap<String, Object> axes, int resolutionLevel,
                                      int xOffset, int yOffset, int width, int height) {
      return storage_.getDisplayImage(axes, resolutionLevel, xOffset, yOffset, width, height);
   }

   @Override
   public int[] getImageBounds() {
      return storage_.getImageBounds();
   }
}
