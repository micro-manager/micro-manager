package org.micromanager.tileddataviewer.internal;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.SubscriberExceptionContext;
import com.google.common.eventbus.SubscriberExceptionHandler;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import mmcorej.TaggedImage;
import mmcorej.org.json.JSONArray;
import mmcorej.org.json.JSONException;
import mmcorej.org.json.JSONObject;
import org.micromanager.data.Coordinates;
import org.micromanager.data.Coords;
import org.micromanager.data.DataManager;
import org.micromanager.data.DataProvider;
import org.micromanager.data.DataProviderHasNewImageEvent;
import org.micromanager.data.Image;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.tileddataprovider.TiledDataProviderAPI;
import org.micromanager.tileddataviewer.TiledDataViewerDataProviderAPI;

/**
 * Wraps NDTiffStorage (MultiresNDTiffAPI) as an MM DataProvider.
 *
 * <p>This allows the MM Inspector and other DataProvider consumers to
 * interact with NDTiff datasets that are being displayed through NDViewer2.</p>
 */
public final class TiledDataViewerDataProvider implements TiledDataViewerDataProviderAPI {

   private final TiledDataProviderAPI storage_;
   private final DataManager dataManager_;
   private final AxesBridge axesBridge_;
   private static final SubscriberExceptionHandler EVENT_BUS_EXCEPTION_HANDLER =
         (Throwable ex, SubscriberExceptionContext ctx) ->
               System.err.println("NDViewer2DataProvider EventBus subscriber threw: "
                     + ex + " [event=" + ctx.getEvent()
                     + ", subscriber=" + ctx.getSubscriber() + "]");

   private final EventBus eventBus_ = new EventBus(EVENT_BUS_EXCEPTION_HANDLER);
   private final String name_;

   /**
    * Construct a data provider wrapping the given NDTiff storage.
    *
    * @param dataManager the MM DataManager for creating Image and SummaryMetadata objects
    * @param storage     the NDTiff storage backend
    * @param name        display name for this data provider
    */
   public TiledDataViewerDataProvider(DataManager dataManager,
                                      TiledDataProviderAPI storage,
                                      String name) {
      dataManager_ = dataManager;
      storage_ = storage;
      axesBridge_ = new AxesBridge();
      name_ = name;
      // Discover existing channels
      axesBridge_.discoverChannels(storage_.getAxesSet());
   }

   /**
    * Return the shared axes bridge used by this provider.
    * Package-private: only NDViewer2DataViewer should need this.
    *
    * @return the axes bridge
    */
   AxesBridge getAxesBridge() {
      return axesBridge_;
   }

   @Override
   public Image getImage(Coords coords) throws IOException {
      HashMap<String, Object> axes = axesBridge_.coordsToNDViewer(coords);
      TaggedImage ti = storage_.getImage(axes);
      if (ti == null || ti.pix == null) {
         return null;
      }
      try {
         return dataManager_.convertTaggedImage(ti, coords, null);
      } catch (JSONException e) {
         throw new IOException("Failed to convert TaggedImage", e);
      }
   }

   /**
    * Fetch an image directly using NDViewer axes, bypassing Coords round-trip.
    * This avoids the problem where Coords drops axes with value 0.
    *
    * @param axes the NDViewer axes map
    * @return the image, or null if not found
    */
   public Image getImageByAxes(HashMap<String, Object> axes) throws IOException {
      TaggedImage ti = storage_.getImage(axes);
      if (ti == null || ti.pix == null) {
         return null;
      }
      Coords coords = axesBridge_.ndViewerToCoords(axes);
      try {
         return dataManager_.convertTaggedImage(ti, coords, null);
      } catch (JSONException e) {
         throw new IOException("Failed to convert TaggedImage", e);
      }
   }

   @Override
   public Image getAnyImage() throws IOException {
      Set<HashMap<String, Object>> keys = storage_.getAxesSet();
      if (keys.isEmpty()) {
         return null;
      }
      HashMap<String, Object> firstKey = keys.iterator().next();
      TaggedImage ti = storage_.getImage(firstKey);
      if (ti == null || ti.pix == null) {
         return null;
      }
      Coords coords = axesBridge_.ndViewerToCoords(firstKey);
      try {
         return dataManager_.convertTaggedImage(ti, coords, null);
      } catch (JSONException e) {
         throw new IOException("Failed to convert TaggedImage", e);
      }
   }

   @Override
   public List<String> getAxes() {
      List<String> axes = new ArrayList<>();
      Set<HashMap<String, Object>> keys = storage_.getAxesSet();
      if (keys.isEmpty()) {
         return axes;
      }
      // Collect all axis names from the keys set
      for (HashMap<String, Object> key : keys) {
         for (String axis : key.keySet()) {
            String mmAxis = axis;
            // NDViewer "channel" axis maps to Coords.CHANNEL
            if (TiledDataViewer.CHANNEL_AXIS.equals(axis)) {
               mmAxis = Coords.CHANNEL;
            }
            if (!axes.contains(mmAxis)) {
               axes.add(mmAxis);
            }
         }
      }
      return axes;
   }

   @Override
   public int getNextIndex(String axis) {
      if (Coords.CHANNEL.equals(axis)) {
         return axesBridge_.getChannelNames().size();
      }
      Set<HashMap<String, Object>> keys = storage_.getAxesSet();
      int max = -1;
      for (HashMap<String, Object> key : keys) {
         Object val = key.get(axis);
         if (val instanceof Number) {
            max = Math.max(max, ((Number) val).intValue());
         }
      }
      return max + 1;
   }

   @Override
   @Deprecated
   public int getAxisLength(String axis) {
      return getNextIndex(axis);
   }

   @Override
   public SummaryMetadata getSummaryMetadata() {
      // Parse channel names from NDTiff storage for Inspector display
      SummaryMetadata.Builder builder = dataManager_.summaryMetadataBuilder();

      try {
         JSONObject json = storage_.getSummaryMetadata();

         // Parse channel names if present
         if (json.has("ChNames")) {
            JSONArray chNames = json.getJSONArray("ChNames");
            List<String> channelNames = new ArrayList<>();
            for (int i = 0; i < chNames.length(); i++) {
               channelNames.add(chNames.getString(i));
            }
            String[] channelNamesArray = channelNames.toArray(new String[0]);
            builder.channelNames(channelNamesArray);
         }
      } catch (Exception e) {
         // If parsing fails, return minimal metadata (no channel names)
      }

      return builder.build();
   }

   @Override
   public boolean isFrozen() {
      return storage_.isFinished();
   }

   @Override
   public int getNumImages() {
      return storage_.getAxesSet().size();
   }

   @Override
   public Iterable<Coords> getUnorderedImageCoords() {
      List<Coords> coordsList = new ArrayList<>();
      for (HashMap<String, Object> axes : storage_.getAxesSet()) {
         coordsList.add(axesBridge_.ndViewerToCoords(axes));
      }
      return coordsList;
   }

   @Override
   public boolean hasImage(Coords coords) {
      HashMap<String, Object> axes = axesBridge_.coordsToNDViewer(coords);
      return storage_.hasImage(axes);
   }

   @Deprecated
   @Override
   public List<Image> getImagesMatching(Coords coords) throws IOException {
      return getImagesIgnoringAxes(coords);
   }

   @Override
   public List<Image> getImagesIgnoringAxes(Coords coords,
                                             String... ignoreTheseAxes)
         throws IOException {
      List<Image> result = new ArrayList<>();
      Coords stripped = coords.copyRemovingAxes(ignoreTheseAxes);
      for (HashMap<String, Object> axes : storage_.getAxesSet()) {
         Coords candidate = axesBridge_.ndViewerToCoords(axes);
         Coords candidateStripped = candidate.copyRemovingAxes(ignoreTheseAxes);
         if (candidateStripped.equals(stripped)) {
            Image img = getImage(candidate);
            if (img != null) {
               result.add(img);
            }
         }
      }
      return result;
   }

   @Override
   @Deprecated
   public Coords getMaxIndices() {
      Coords.Builder b = Coordinates.builder();
      for (String axis : getAxes()) {
         int nextIdx = getNextIndex(axis);
         if (nextIdx > 0) {
            b.index(axis, nextIdx - 1);
         }
      }
      return b.build();
   }

   @Override
   public String getName() {
      return name_;
   }

   @Override
   public void registerForEvents(Object obj) {
      eventBus_.register(obj);
   }

   @Override
   public void unregisterForEvents(Object obj) {
      eventBus_.unregister(obj);
   }

   @Override
   public void close() throws IOException {
      // Do not close the storage here — the adapter owns its lifecycle
   }

   /**
    * Fetch a downsampled version of the image for histogram computation.
    * Uses the coarsest available pyramid level. Falls back to full-res
    * if the storage is not multi-resolution or has only one level.
    *
    * @param coords the image coordinates
    * @return downsampled image, or full-res image as fallback, or null
    */
   public Image getDownsampledImage(Coords coords) throws IOException {
      int numLevels = storage_.getNumResLevels();
      if (numLevels <= 1) {
         return getImage(coords);
      }
      HashMap<String, Object> axes = axesBridge_.coordsToNDViewer(coords);
      int resLevel = numLevels - 1;
      TaggedImage ti = storage_.getImage(axes, resLevel);
      if (ti == null || ti.pix == null) {
         return getImage(coords);
      }
      try {
         return dataManager_.convertTaggedImage(ti, coords, null);
      } catch (JSONException e) {
         throw new IOException("Failed to convert TaggedImage", e);
      }
   }

   /**
    * Fetch a downsampled version of the image by NDViewer axes.
    * Uses the coarsest available pyramid level. Falls back to full-res
    * if the storage has only one level or the pyramid level is missing.
    *
    * @param axes the NDViewer axes map
    * @return downsampled image, or null if not found
    */
   public Image getDownsampledImageByAxes(HashMap<String, Object> axes)
         throws IOException {
      // Always fetch from full resolution (level 0).
      // Lower pyramid levels use different row/col indices (floorDiv by 2^N)
      // so passing full-res axes directly to a higher level would return null.
      TaggedImage ti = storage_.getImage(axes);
      if (ti == null || ti.pix == null) {
         return null;
      }
      Coords coords = axesBridge_.ndViewerToCoords(axes);
      try {
         return dataManager_.convertTaggedImage(ti, coords, null);
      } catch (JSONException e) {
         throw new IOException("Failed to convert TaggedImage", e);
      }
   }

   /**
    * Notify this data provider that a new image has arrived at the given axes.
    * Called by NDTiffAndViewerAdapter when putImage() is invoked.
    *
    * @param axes the NDViewer axes of the new image
    */
   public void newImageArrived(HashMap<String, Object> axes) {
      // Register any new channel
      Object ch = axes.get(TiledDataViewer.CHANNEL_AXIS);
      if (ch != null) {
         axesBridge_.registerChannel(ch);
      }
      try {
         // Use direct axes lookup to avoid the Coords round-trip
         // (Coords drops axes with value 0, losing e.g. row=0, column=0)
         Image image = getImageByAxes(axes);
         if (image != null) {
            eventBus_.post(newImageEvent(image));
         }
      } catch (IOException e) {
         // Image may not be written yet; ignore
      }
   }

   /**
    * Notify this data provider that a new image has arrived.
    * Uses the provided Image directly instead of reading from storage,
    * which avoids issues with storage indexing delays.
    *
    * @param image the image that arrived
    * @param axes  the NDViewer axes of the new image
    */
   @Override
   public void newImageArrived(Image image, HashMap<String, Object> axes) {
      Object ch = axes.get(TiledDataViewer.CHANNEL_AXIS);
      if (ch != null) {
         axesBridge_.registerChannel(ch);
      }
      if (image != null) {
         eventBus_.post(newImageEvent(image));
      }
   }

   @Override
   public TiledDataProviderAPI getStorage() {
      return storage_;
   }

   private DataProviderHasNewImageEvent newImageEvent(final Image image) {
      final DataProvider self = this;
      return new DataProviderHasNewImageEvent() {
         @Override
         public Image getImage() {
            return image;
         }

         @Override
         public Coords getCoords() {
            return image.getCoords();
         }

         @Override
         public DataProvider getDataProvider() {
            return self;
         }

      };
   }

}
