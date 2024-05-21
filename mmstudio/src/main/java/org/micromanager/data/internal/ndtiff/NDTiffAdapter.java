package org.micromanager.data.internal.ndtiff;

import com.google.common.eventbus.Subscribe;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import mmcorej.TaggedImage;
import mmcorej.org.json.JSONException;
import mmcorej.org.json.JSONObject;
import org.micromanager.data.Coordinates;
import org.micromanager.data.Coords;
import org.micromanager.data.DataProviderHasNewSummaryMetadataEvent;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import org.micromanager.data.Metadata;
import org.micromanager.data.Storage;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.data.internal.DefaultDatastore;
import org.micromanager.data.internal.DefaultImage;
import org.micromanager.data.internal.DefaultMetadata;
import org.micromanager.data.internal.DefaultSummaryMetadata;
import org.micromanager.data.internal.ImageSizeChecker;
import org.micromanager.data.internal.PropertyKey;
import org.micromanager.internal.propertymap.NonPropertyMapJSONFormats;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.ndtiffstorage.EssentialImageMetadata;
import org.micromanager.ndtiffstorage.NDTiffAPI;
import org.micromanager.ndtiffstorage.NDTiffStorage;


/**
 * Implements the NDTiff format (Micro-Magellan / PycroManager) to a Micro-Manager Storage
 * so that it can be used with the MM 2.0 storage API.
 *
 * @author Henry Pinkard
 */
public class NDTiffAdapter implements Storage {

   private static final int SAVING_QUEUE_SIZE = 40;

   private NDTiffAPI storage_;
   private DefaultDatastore store_;
   private SummaryMetadata summaryMetadata_ = (new DefaultSummaryMetadata.Builder()).build();
   // ShadowList of Coords in the datastore.  In read mode derived from the storage, in write mode
   // added whenever an image is added
   private final List<Coords> coords_;
   // ShadowList of Coords indexed by Coords missing channel.
   // In read mode derived from the storage, in write mode added whenever an image is added
   private final Map<Coords, List<Coords>> coordsIndexedMissingC_;

   /**
    * Constructor of NDTiffAdapter.
    *
    * @param store Micro-Manager data store that will be used
    * @param dir Where to write the data
    * @param amInWriteMode Whether we are writing
    * @throws IOException Close to inevitable with data storage
    */
   public NDTiffAdapter(Datastore store, String dir, Boolean amInWriteMode)
           throws IOException {
      // We must be notified of changes in the Datastore before everyone else,
      // so that others can read those changes out of the Datastore later.
      store_ = (DefaultDatastore) store;
      store_.registerForEvents(this, 0);

      store_.setSavePath(dir);
      store_.setName(new File(dir).getName());
      coords_ = new ArrayList<>();
      coordsIndexedMissingC_ = new HashMap<>();

      // If not writing, wait until summary metadata set to create storage
      if (!amInWriteMode) {
         storage_ = new NDTiffStorage(dir);
         getUnorderedImageCoords().forEach(this::addCoordsToIndex);
      }
   }

   private void addCoordsToIndex(Coords coords) {
      coords_.add(coords);
      Coords coordMissingC = coords.copyRemovingAxes(Coords.C);
      if (!coordsIndexedMissingC_.containsKey(coordMissingC)) {
         coordsIndexedMissingC_.put(coordMissingC, new LinkedList<>());
      }
      coordsIndexedMissingC_.get(coordMissingC).add(coords);
   }

   public static boolean isNDTiffDataSet(String dir) {
      return new File(dir + (dir.endsWith(File.separator)
              ? "" : File.separator) + "NDTiff.index").exists();
   }

   private HashMap<String, Object> coordsToHashMap(Coords coords) {
      HashMap<String, Object> axes = new HashMap<>();
      for (String s : coords.getAxes()) {
         axes.put(s, coords.getIndex(s));
      }
      // Axes with a value of 0 aren't explicitly encoded
      for (String s : getSummaryMetadata().getOrderedAxes()) {
         if (!axes.containsKey(s)) {
            axes.put(s, 0);
         }
      }

      return axes;
   }

   private static Coords hashMapToCoords(HashMap<String, Object> axes) {
      Coords.Builder builder = Coordinates.builder();
      for (String s : axes.keySet()) {
         builder.index(s, (Integer) axes.get(s));
      }
      return builder.build();
   }

   /**
    * Will be called when the event bus signals that there are new Summary Metadata.
    *
    * @param event The event gives access to the new SummaryMetadata.
    */
   @Subscribe
   public void onNewSummaryMetadata(DataProviderHasNewSummaryMetadataEvent event) {
      setSummaryMetadata(event.getSummaryMetadata());
   }

   /**
    * This is quite strange.  Only when we have SummaryMetadata, we can create the
    * Storage.
    *
    * @param summary To push to the storage.
    */
   public void setSummaryMetadata(SummaryMetadata summary) {
      try {
         String summaryMDString = NonPropertyMapJSONFormats.summaryMetadata().toJSON(
                 ((DefaultSummaryMetadata) summary).toPropertyMap());
         JSONObject jsonSummary;
         try {
            jsonSummary = new JSONObject(summaryMDString);
         } catch (JSONException e) {
            throw new RuntimeException("Problem with summary metadata");
         }
         Consumer<String> debugLogger = s -> ReportingUtils.logDebugMessage(s);
         storage_ = new NDTiffStorage(store_.getSavePath(), store_.getName(),
                 jsonSummary, 0, 0, false, 0,
                 SAVING_QUEUE_SIZE, debugLogger, false);
         try {
            summaryMetadata_ = DefaultSummaryMetadata.fromPropertyMap(
                     NonPropertyMapJSONFormats.summaryMetadata().fromJSON(
                              storage_.getSummaryMetadata().toString()));
         } catch (IOException e) {
            throw new RuntimeException(e);
         }
      } catch (Exception e) {
         ReportingUtils.logError(e, "Error setting new summary metadata");
      }
   }

   @Override
   public void freeze() throws IOException {
      storage_.finishedWriting();
      store_.unregisterForEvents(this);
   }

   @Override
   public void putImage(Image image) throws IOException {
      if (storage_ == null) {
         setSummaryMetadata(DefaultSummaryMetadata.getStandardSummaryMetadata());
      }
      if (summaryMetadata_ != null) {
         ImageSizeChecker.checkImageSizeInSummary(summaryMetadata_, image);
      }
      boolean rgb = image.getNumComponents() > 1;
      HashMap<String, Object> axes = coordsToHashMap(image.getCoords());

      //TODO: This is getting the JSON metadata as a String, and then converting it into
      // A JSONObject again, and then it gets converted to string again in NDTiffStorage
      // Certainly inefficient, possibly performance limitiing depending on which
      // Thread this is called on.
      String mdString = NonPropertyMapJSONFormats.metadata().toJSON(
              ((DefaultMetadata) image.getMetadata()).toPropertyMap());

      JSONObject json = null;
      try {
         json = new JSONObject(mdString);
      } catch (JSONException e) {
         throw new RuntimeException(e);
      }
      // TODO: where to get the actual bit depth?
      int bitDepth = image.getBytesPerPixel() * 8;
      storage_.putImage(image.getRawPixels(), json, axes, rgb, bitDepth,
              image.getHeight(), image.getWidth());
      addCoordsToIndex(image.getCoords());
   }

   @Override
   public Image getImage(Coords coords) throws IOException {
      if (storage_ == null) {
         return null;
      }
      TaggedImage ti = storage_.getImage(coordsToHashMap(coords));
      addEssentialImageMetadata(ti, coordsToHashMap(coords));
      return new DefaultImage(ti, hashMapToCoords(coordsToHashMap(coords)),
              studioMetadataFromJSON(ti.tags));
   }

   @Override
   public boolean hasImage(Coords coords) {
      if (storage_ == null) {
         return false;
      }
      return storage_.hasImage(coordsToHashMap(coords));
   }

   @Override
   public Image getAnyImage() {
      if (storage_.getAxesSet().isEmpty()) {
         return null;
      }
      HashMap<String, Object> axes = storage_.getAxesSet().iterator().next();
      TaggedImage ti = storage_.getImage(axes);
      addEssentialImageMetadata(ti, axes);
      return new DefaultImage(ti, hashMapToCoords(axes), studioMetadataFromJSON(ti.tags));
   }

   @Override
   public Iterable<Coords> getUnorderedImageCoords() {
      return () -> {
         Stream<HashMap<String, Object>> axesStream = storage_.getAxesSet().stream();
         Stream<Coords> coordsStream = axesStream.map(NDTiffAdapter::hashMapToCoords);
         return coordsStream.iterator();
      };
   }

   /**
    * This implementation only check for an exact match. It does not actually return a list
    * of all images that match the given coordinates, as I am not quite sure how to implement that.
    *
    * @param coords Coordinates specifying images to match
    * @return List of images matching the coordinates
    * @throws IOException can always happen.
    */
   @Override
   public List<Image> getImagesMatching(Coords coords) throws IOException {
      List<Image> imageList = new LinkedList<>();
      if (storage_ == null) {
         return imageList;
      }
      HashMap<String, Object> ndTiffCoords = coordsToHashMap(coords);
      if (storage_.hasImage(ndTiffCoords)) {
         TaggedImage ti = addEssentialImageMetadata(storage_.getImage(ndTiffCoords), ndTiffCoords);
         Image img = new DefaultImage(ti, hashMapToCoords(ndTiffCoords),
                 studioMetadataFromJSON(ti.tags));
         imageList.add(img);
      }
      return imageList;
   }

   @Override
   public List<Image> getImagesIgnoringAxes(
           Coords coords, String... ignoreTheseAxes) throws IOException {
      // This is obviously wrong, but not quite sure what to do at this point....
      if (ignoreTheseAxes.length == 0) {
         return getImagesMatching(coords);
      } else if (ignoreTheseAxes.length == 1 && ignoreTheseAxes[0].equals(Coords.C)) {
         Coords matchCoord = coords.copyRemovingAxes(Coords.C);
         List<Coords> coords1 = coordsIndexedMissingC_.get(matchCoord);
         final List<Image> result = new ArrayList<>();
         coords1.forEach(coords2 -> {
            try {
               result.add(getImagesMatching(coords2).get(0));
            } catch (IOException e) {
               throw new RuntimeException(e);
            }
         });
         return result;
      } else {
         Coords matchCoord = coords.copyRemovingAxes(ignoreTheseAxes);
         final List<Image> result = new ArrayList<>();
         getUnorderedImageCoords().iterator().forEachRemaining(
               coords1 -> {
                  if (coords1.copyRemovingAxes(ignoreTheseAxes).equals(matchCoord)) {
                     try {
                        result.add(getImage(coords1));
                     } catch (IOException e) {
                        throw new RuntimeException(e);
                     }
                  }
               });
         return result;
      }
   }

   @Override
   public int getMaxIndex(String axis) {
      if (storage_ == null || storage_.getAxesSet() == null || storage_.getAxesSet().isEmpty()) {
         return -1;
      }
      return storage_.getAxesSet().stream().map(new Function<HashMap<String, Object>, Integer>() {
         @Override
         public Integer apply(HashMap<String, Object> stringIntegerHashMap) {
            if (stringIntegerHashMap.containsKey(axis)) {
               return (Integer) stringIntegerHashMap.get(axis);
            }
            return -1;
         }
      }).reduce(Math::max).get();
   }

   @Override
   public List<String> getAxes() {
      if (getSummaryMetadata() == null) {
         return null;
      }
      return getSummaryMetadata().getOrderedAxes();
   }

   @Override
   public Coords getMaxIndices() {
      if (storage_ == null) {
         return null;
      }
      Coords.Builder builder = Coordinates.builder();
      for (String axis : getAxes()) {
         builder.index(axis,
                 storage_.getAxesSet().stream().map(stringIntegerHashMap -> {
                    if (stringIntegerHashMap.containsKey(axis)) {
                       return (Integer) stringIntegerHashMap.get(axis);
                    }
                    return -1;
                 }).reduce(Math::max).get());
      }
      return builder.build();
   }

   @Override
   public SummaryMetadata getSummaryMetadata() {
      if (storage_ == null) {
         return null;
      }
      try {
         return DefaultSummaryMetadata.fromPropertyMap(
                 NonPropertyMapJSONFormats.summaryMetadata().fromJSON(
                         storage_.getSummaryMetadata().toString()));
      } catch (IOException e) {
         throw new RuntimeException(e);
      }
   }

   @Override
   public int getNumImages() {
      if (storage_ == null) {
         return 0;
      }
      return storage_.getAxesSet().size();
   }

   @Override
   public void close() throws IOException {
      storage_.close();
   }

   /**
    * The DefaultImage converter expects to find width and height keys,
    * though the images fed in don't have them in metadata.
    * This function explicitly adds width and height keys.
    *
    * @param ti TaggedImage to which the width and height keys will be added.
    * @param axes List with axes.  What are these for?
    * @return TaggedImage with width and height metadata added.
    */
   private TaggedImage addEssentialImageMetadata(TaggedImage ti, HashMap<String, Object> axes) {
      EssentialImageMetadata essMD = storage_.getEssentialImageMetadata(axes);
      //Load essential metadata into the image metadata.
      try {
         ti.tags.put(PropertyKey.WIDTH.key(), essMD.width);
         ti.tags.put(PropertyKey.HEIGHT.key(), essMD.height);
         String pixType;
         if (essMD.bitDepth == 8 && essMD.rgb) {
            pixType = "RGB32";
         } else if (essMD.bitDepth == 8) {
            pixType = "GRAY8";
         } else {
            pixType = "GRAY16";
         }
         ti.tags.put(PropertyKey.PIXEL_TYPE.key(), pixType);
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
      return ti;
   }

   private Metadata studioMetadataFromJSON(JSONObject tags) {
      JsonElement je;
      try {
         je = new JsonParser().parse(tags.toString());
      } catch (Exception unlikely) {
         throw new IllegalArgumentException("Failed to parse JSON created from TaggedImage tags",
                 unlikely);
      }
      return DefaultMetadata.fromPropertyMap(
              NonPropertyMapJSONFormats.metadata().fromGson(je));
   }
}





