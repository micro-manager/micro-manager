package org.micromanager.tileddataprovider;

import com.google.common.eventbus.Subscribe;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import mmcorej.TaggedImage;
import mmcorej.org.json.JSONArray;
import mmcorej.org.json.JSONException;
import mmcorej.org.json.JSONObject;
import org.micromanager.data.Coordinates;
import org.micromanager.data.Coords;
import org.micromanager.data.DataProvider;
import org.micromanager.data.DataProviderHasNewImageEvent;
import org.micromanager.data.Image;
import org.micromanager.data.SummaryMetadata;

/**
 * Adapts an MM {@link DataProvider} to the {@link TiledDataProviderAPI} read interface.
 *
 * <p>This allows tools that consume {@link TiledDataProviderAPI} (such as ExportTiles) to
 * operate on any standard MM DataProvider — including live/streaming datastores — without
 * requiring NDTiff or pyramidal storage.</p>
 *
 * <h2>Coordinate mapping</h2>
 * <p>MM {@link Coords} integer indices are mapped directly to the axes map:
 * {@code "channel"} → {@code Integer}, {@code "z"} → {@code Integer}, etc.
 * Channel names from {@link SummaryMetadata#getChannelNames()} are used to populate
 * the {@code "channel"} axis value as a {@code String} when available; otherwise
 * the integer index is used.</p>
 *
 * <h2>Tiling and pyramids</h2>
 * <p>Plain MM DataProviders have no tiled or pyramidal structure.
 * {@link #getNumResLevels()} returns {@code 1}.
 * {@link #getDisplayImage} ignores the viewport and returns the full image.
 * {@link #getImage(HashMap, int)} returns {@code null} for resolution levels above 0.</p>
 *
 * <h2>Live data</h2>
 * <p>The adapter subscribes to the source DataProvider's event bus on construction.
 * New images posted by the source are forwarded via the adapter's own
 * {@link #registerNewImageListener(NewImageListener)} callback.
 * Call {@link #close()} to unsubscribe when done.</p>
 */
public class MMDataProviderAdapter implements TiledDataProviderAPI {

   /**
    * Callback interface for new-image notifications forwarded from the source DataProvider.
    */
   public interface NewImageListener {
      /**
       * Called when a new image has arrived.
       *
       * @param image the new image
       * @param axes  the axes map for the new image (integer channel indices, or String names
       *              if channel names are available)
       */
      void newImageArrived(Image image, HashMap<String, Object> axes);
   }

   private final DataProvider source_;
   private final List<NewImageListener> listeners_ = new ArrayList<>();
   private String[] channelNames_;

   /**
    * Construct an adapter wrapping the given MM DataProvider.
    *
    * <p>The adapter subscribes to the source's event bus immediately so that
    * live-data events are forwarded. Call {@link #close()} to unsubscribe.</p>
    *
    * @param source the MM DataProvider to wrap
    */
   public MMDataProviderAdapter(DataProvider source) {
      source_ = source;
      channelNames_ = resolveChannelNames(source_.getSummaryMetadata());
      source_.registerForEvents(this);
   }

   /**
    * Register a listener to receive new-image notifications forwarded from the source.
    *
    * @param listener the listener to add
    */
   public void registerNewImageListener(NewImageListener listener) {
      listeners_.add(listener);
   }

   /**
    * Unregister a previously registered new-image listener.
    *
    * @param listener the listener to remove
    */
   public void unregisterNewImageListener(NewImageListener listener) {
      listeners_.remove(listener);
   }

   /**
    * Unsubscribe from the source DataProvider's event bus.
    *
    * <p>Must be called when this adapter is no longer needed to avoid memory leaks.</p>
    */
   public void close() {
      source_.unregisterForEvents(this);
   }

   // -------------------------------------------------------------------------
   // Internal: Guava EventBus subscriber for source DataProvider events
   // -------------------------------------------------------------------------

   @Subscribe
   public void onNewImage(DataProviderHasNewImageEvent event) {
      Image image = event.getImage();
      HashMap<String, Object> axes = coordsToAxes(image.getCoords());
      for (NewImageListener listener : new ArrayList<>(listeners_)) {
         listener.newImageArrived(image, axes);
      }
   }

   // -------------------------------------------------------------------------
   // TiledDataProviderAPI implementation
   // -------------------------------------------------------------------------

   @Override
   public Set<HashMap<String, Object>> getAxesSet() {
      Set<HashMap<String, Object>> result = new HashSet<>();
      for (Coords coords : source_.getUnorderedImageCoords()) {
         result.add(coordsToAxes(coords));
      }
      return result;
   }

   @Override
   public TaggedImage getImage(HashMap<String, Object> axes) {
      return getImage(axes, 0);
   }

   @Override
   public TaggedImage getImage(HashMap<String, Object> axes, int resolutionLevel) {
      if (resolutionLevel != 0) {
         return null;
      }
      Coords coords = axesToCoords(axes);
      if (coords == null) {
         return null;
      }
      try {
         Image image = source_.getImage(coords);
         if (image == null) {
            return null;
         }
         return imageToTaggedImage(image);
      } catch (IOException e) {
         return null;
      }
   }

   @Override
   public boolean hasImage(HashMap<String, Object> axes) {
      Coords coords = axesToCoords(axes);
      if (coords == null) {
         return false;
      }
      return source_.hasImage(coords);
   }

   @Override
   public JSONObject getSummaryMetadata() {
      SummaryMetadata summary = source_.getSummaryMetadata();
      if (summary == null) {
         return null;
      }
      try {
         JSONObject json = new JSONObject();
         // Channel names
         String[] chNames = resolveChannelNames(summary);
         if (chNames != null && chNames.length > 0) {
            JSONArray arr = new JSONArray();
            for (String name : chNames) {
               arr.put(name);
            }
            json.put("ChNames", arr);
         }
         // No tiled overlap
         json.put("GridPixelOverlapX", 0);
         json.put("GridPixelOverlapY", 0);
         return json;
      } catch (JSONException e) {
         return null;
      }
   }

   @Override
   public boolean isFinished() {
      return source_.isFrozen();
   }

   @Override
   public int getNumResLevels() {
      return 1;
   }

   @Override
   public String getDiskLocation() {
      return null;
   }

   /**
    * Returns the full image at the given axes, ignoring the viewport parameters.
    *
    * <p>Plain MM DataProviders have no tiled structure, so the viewport is ignored and
    * the complete image is returned. The resolution level must be 0; higher levels
    * return {@code null}.</p>
    */
   @Override
   public TaggedImage getDisplayImage(HashMap<String, Object> axes, int resolutionLevel,
                                      int xOffset, int yOffset, int width, int height) {
      return getImage(axes, resolutionLevel);
   }

   @Override
   public int[] getImageBounds() {
      return null;
   }

   // -------------------------------------------------------------------------
   // Coordinate conversion helpers
   // -------------------------------------------------------------------------

   /**
    * Convert MM {@link Coords} to a TiledDataProviderAPI axes map.
    *
    * <p>Integer axes are copied directly. The {@code "channel"} axis is replaced
    * with a String channel name if one is available from the summary metadata.</p>
    */
   private HashMap<String, Object> coordsToAxes(Coords coords) {
      HashMap<String, Object> axes = new HashMap<>();
      for (String axis : coords.getAxes()) {
         int idx = coords.getIndex(axis);
         if (Coords.CHANNEL.equals(axis)) {
            String name = channelNameForIndex(idx);
            axes.put(axis, name != null ? name : idx);
         } else {
            axes.put(axis, idx);
         }
      }
      // Coords.index==0 axes are stripped by the builder, so axes like channel=0,
      // time=0, z=0 are absent from coords.getAxes() even though the image belongs
      // to index 0 on those axes.  Restore them so that downstream filters that
      // match on these axes work correctly.
      SummaryMetadata summary = source_.getSummaryMetadata();
      Coords intendedDims = (summary != null) ? summary.getIntendedDimensions() : null;
      if (intendedDims != null) {
         if (!axes.containsKey(Coords.CHANNEL) && intendedDims.hasAxis(Coords.CHANNEL)) {
            if (channelNames_ != null && channelNames_.length > 0) {
               axes.put(Coords.CHANNEL, channelNames_[0]);
            } else {
               axes.put(Coords.CHANNEL, 0);
            }
         }
         if (!axes.containsKey(Coords.Z_SLICE) && intendedDims.hasAxis(Coords.Z_SLICE)) {
            axes.put(Coords.Z_SLICE, 0);
         }
         if (!axes.containsKey(Coords.TIME_POINT) && intendedDims.hasAxis(Coords.TIME_POINT)) {
            axes.put(Coords.TIME_POINT, 0);
         }
      }
      return axes;
   }

   /**
    * Convert a TiledDataProviderAPI axes map back to MM {@link Coords}.
    *
    * <p>String channel values are resolved to their integer index by matching
    * against the channel names from the source's summary metadata.
    * Returns {@code null} if channel name resolution fails.</p>
    */
   private Coords axesToCoords(HashMap<String, Object> axes) {
      Coords.Builder builder = Coordinates.builder();
      for (Map.Entry<String, Object> entry : axes.entrySet()) {
         String axis = entry.getKey();
         Object val = entry.getValue();
         int idx;
         if (val instanceof Integer) {
            idx = (Integer) val;
         } else if (val instanceof String && Coords.CHANNEL.equals(axis)) {
            idx = channelIndexForName((String) val);
            if (idx < 0) {
               return null;
            }
         } else {
            continue;
         }
         builder.index(axis, idx);
      }
      return builder.build();
   }

   private String channelNameForIndex(int idx) {
      if (channelNames_ != null && idx >= 0 && idx < channelNames_.length) {
         return channelNames_[idx];
      }
      return null;
   }

   private int channelIndexForName(String name) {
      if (channelNames_ != null) {
         for (int i = 0; i < channelNames_.length; i++) {
            if (channelNames_[i].equals(name)) {
               return i;
            }
         }
      }
      return -1;
   }

   private static String[] resolveChannelNames(SummaryMetadata summary) {
      if (summary == null) {
         return null;
      }
      List<String> names = summary.getChannelNameList();
      if (names != null && !names.isEmpty()) {
         return names.toArray(new String[0]);
      }
      return null;
   }

   // -------------------------------------------------------------------------
   // Image conversion helpers
   // -------------------------------------------------------------------------

   /**
    * Convert an MM {@link Image} to a {@link TaggedImage}.
    *
    * <p>Supports 16-bit ({@code short[]}), 8-bit gray ({@code byte[]}), and RGB32
    * ({@code byte[]}, 4 bytes per pixel in BGRA order). The tags JSON is populated
    * with {@code "Width"}, {@code "Height"}, {@code "BytesPerPixel"}, and
    * {@code "NumComponents"} so that downstream code can determine the pixel format.</p>
    */
   private static TaggedImage imageToTaggedImage(Image image) {
      Object rawPix = image.getRawPixels();
      if (!(rawPix instanceof short[]) && !(rawPix instanceof byte[])) {
         return null;
      }
      try {
         JSONObject tags = new JSONObject();
         tags.put("Width", image.getWidth());
         tags.put("Height", image.getHeight());
         tags.put("BytesPerPixel", image.getBytesPerPixel());
         tags.put("NumComponents", image.getNumComponents());
         return new TaggedImage(rawPix, tags);
      } catch (JSONException e) {
         return null;
      }
   }
}
