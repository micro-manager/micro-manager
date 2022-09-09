package org.micromanager.data.internal.ndtiff;

import com.google.common.eventbus.Subscribe;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
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
import org.micromanager.data.internal.PropertyKey;
import org.micromanager.internal.propertymap.NonPropertyMapJSONFormats;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.ndtiffstorage.EssentialImageMetadata;
import org.micromanager.ndtiffstorage.NDTiffAPI;
import org.micromanager.ndtiffstorage.NDTiffStorage;


public class NDTiffAdapter implements Storage {

   private static final int SAVING_QUEUE_SIZE = 40;

   private NDTiffAPI storage_;
   private DefaultDatastore store_;

   public NDTiffAdapter(Datastore store, String dir, Boolean amInWriteMode)
           throws IOException {
      // We must be notified of changes in the Datastore before everyone else,
      // so that others can read those changes out of the Datastore later.
      store_ = (DefaultDatastore) store;
      store_.registerForEvents(this, 0);

      store_.setSavePath(dir);
      store_.setName(new File(dir).getName());


      if (amInWriteMode) {
         // Wait until summary metadata set to create storage
      } else {
         storage_ = new NDTiffStorage(dir);
      }
   }

   public static boolean isNDTiffDataSet(String dir) {
      return new File(dir + (dir.endsWith(File.separator)
              ? "" : File.separator) + "NDTiff.index").exists();
   }

   private HashMap<String, Integer> coordsToHashMap(Coords coords) {
      HashMap<String, Integer> axes = new HashMap<String, Integer>();
      for (String s : coords.getAxes()) {
         axes.put(s, coords.getIndex(s));
      }
      //Axes with a value of 0 aren't explicitly encoded
      for (String s : getSummaryMetadata().getOrderedAxes()) {
         if (!axes.containsKey(s)) {
            axes.put(s, 0);
         }
      }

      return axes;
   }

   private static Coords hashMapToCoords(HashMap<String, Integer> axes) {
      Coords.Builder builder = Coordinates.builder();
      for (String s : axes.keySet()) {
         builder.index(s, axes.get(s));
      }
      return builder.build();
   }

   @Subscribe
   public void onNewSummaryMetadata(DataProviderHasNewSummaryMetadataEvent event) {
      try {

         String summaryMDString = NonPropertyMapJSONFormats.summaryMetadata().toJSON(
                 ((DefaultSummaryMetadata) event.getSummaryMetadata()).toPropertyMap());
         JSONObject summaryMetadata;
         try {
            summaryMetadata = new JSONObject(summaryMDString);
         } catch (JSONException e) {
            throw new RuntimeException("Problem with summary metadata");
         }

         Consumer<String> debugLogger = new Consumer<String>() {
            @Override
            public void accept(String s) {
               ReportingUtils.logDebugMessage(s);
            }
         };
         storage_ = new NDTiffStorage(store_.getSavePath(), store_.getName(),
                 summaryMetadata, 0, 0, false, 0,
                 SAVING_QUEUE_SIZE, debugLogger, false);
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
      boolean rgb = image.getNumComponents() > 1;
      HashMap<String, Integer> axes = coordsToHashMap(image.getCoords());

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

      storage_.putImage(image.getRawPixels(), json, axes, rgb, image.getHeight(), image.getWidth());
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
      if (storage_.getAxesSet().size() == 0) {
         return null;
      }
      HashMap<String, Integer> axes = storage_.getAxesSet().iterator().next();
      TaggedImage ti = storage_.getImage(axes);
      addEssentialImageMetadata(ti, axes);
      return new DefaultImage(ti, hashMapToCoords(axes), studioMetadataFromJSON(ti.tags));
   }

   @Override
   public Iterable<Coords> getUnorderedImageCoords() {
      return () -> {
         Stream<HashMap<String, Integer>> axesStream = storage_.getAxesSet().stream();
         Stream<Coords> coordsStream = axesStream.map(NDTiffAdapter::hashMapToCoords);
         return coordsStream.iterator();
      };
   }

   @Override
   public List<Image> getImagesMatching(Coords coords) throws IOException {
      if (storage_ == null) {
         return new LinkedList<Image>();
      }
      Stream<HashMap<String, Integer>> axesStream = storage_.getAxesSet().stream();
      axesStream = axesStream.filter(stringIntegerHashMap -> {
         for (String axis : coords.getAxes()) {
            if (!stringIntegerHashMap.containsKey(axis)) {
               return false;
            } else if (stringIntegerHashMap.get(axis) != coords.getIndex(axis)) {
               return false;
            }
         }
         return true;
      });
      return axesStream.map(new Function<HashMap<String, Integer>, Image>() {
         @Override
         public Image apply(HashMap<String, Integer> axes) {
            TaggedImage ti = addEssentialImageMetadata(storage_.getImage(axes), axes);
            return new DefaultImage(ti, hashMapToCoords(axes),
                    studioMetadataFromJSON(ti.tags));
         }
      }).collect(Collectors.toList());
   }

   @Override
   public List<Image> getImagesIgnoringAxes(
           Coords coords, String... ignoreTheseAxes) throws IOException {
      // I don't see how this should do anything different than the above one...

      return getImagesMatching(coords);
   }

   @Override
   public int getMaxIndex(String axis) {
      return storage_.getAxesSet().stream().map(new Function<HashMap<String, Integer>, Integer>() {
         @Override
         public Integer apply(HashMap<String, Integer> stringIntegerHashMap) {
            if (stringIntegerHashMap.containsKey(axis)) {
               return stringIntegerHashMap.get(axis);
            }
            return -1;
         }
      }).reduce(Math::max).get();
   }

   @Override
   public List<String> getAxes() {
      return getSummaryMetadata().getOrderedAxes();
//      if (storage_ == null) {
//         return new LinkedList<String>();
//      }
//      return new LinkedList<String>(storage_.getAxesSet().stream().flatMap(hashmap ->
//              hashmap.keySet().stream()).collect(Collectors.toSet()));
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
                       return stringIntegerHashMap.get(axis);
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
    * though the images fed in don't have them in metadata. This function explicitly adds them in
    *
    * @param ti
    * @param axes
    * @return
    */
   private TaggedImage addEssentialImageMetadata(TaggedImage ti, HashMap<String, Integer> axes) {
      EssentialImageMetadata essMD = storage_.getEssentialImageMetadata(axes);
      //Load essential metadata into the image metadata.
      try {
         ti.tags.put(PropertyKey.WIDTH.key(), essMD.width);
         ti.tags.put(PropertyKey.HEIGHT.key(), essMD.height);
         String pixType;
         if (essMD.byteDepth == 1 && essMD.rgb) {
            pixType = "RGB32";
         } else if (essMD.byteDepth == 2) {
            pixType = "GRAY16";
         } else {
            pixType = "GRAY8";
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





