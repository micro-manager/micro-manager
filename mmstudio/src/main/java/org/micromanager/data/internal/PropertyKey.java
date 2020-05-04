
package org.micromanager.data.internal;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.html.HtmlEscapers;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.micromanager.MultiStagePosition;
import org.micromanager.PositionList;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.SnapLiveManager;
import org.micromanager.StagePosition;
import org.micromanager.acquisition.AcquisitionManager;
import org.micromanager.data.Coords;
import org.micromanager.data.Image;
import org.micromanager.data.Metadata;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.display.ChannelDisplaySettings;
import org.micromanager.display.ComponentDisplaySettings;
import org.micromanager.display.DisplaySettings;
import org.micromanager.internal.propertymap.NonPropertyMapJSONFormats;
import org.micromanager.internal.propertymap.MM1JSONSerializer;
import org.micromanager.internal.propertymap.PropertyMapJSONSerializer;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * Keys that appear in the JSON-formatted metadata (and a few other pieces of
 * data) in the Micro-Manager file format.
 * <p>
 * This is the single-source-of-truth for all JSON metadata keys. All knowledge
 * about what (structure of data) is stored under each key should live here.
 * The affiliation of each key to data structures is defined by the subclasses
 * of {@link NonPropertyMapJSONFormats}.
 * <p>
 * To each key is associated knowledge of how to read and (if still used) write
 * the key to the non-property-map JSON formats. The canonical key strings are
 * also used in modern property maps.
 * <p>
 * When adding new keys, the key string should be camel-cased, starting with
 * a capital letter. As an exception, physical units should be suffixed with
 * an underscore. An example of an existing key following this convention is
 * "ElapsedTime_ms".
 *
 * @author Mark A. Tsuchida, based in part on research and code by Chris Weisiger
 */
public enum PropertyKey {
   // Please maintain alphabetical order
/*
             
            
            putDouble("AutoscaleIgnoredQuantile", extremaQuantile_).
            putPropertyMapList("ChannelSettings", channelSettings).
   */
   
   ACQUISITION_DISPLAY_SETTINGS("AcquisitionDisplaySettings", AcquisitionManager.class),
   
   AUTOSCALE_IGNORED_QUANTILE("AutoscaleIgnoredQuantile", DisplaySettings.class),
   
   AUTOSTRETCH("Autostretch", DisplaySettings.class),
   
   AXIS_ORDER("AxisOrder", SummaryMetadata.class) {
      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         List<String> axes = Lists.newArrayList();
         for (JsonElement e : je.getAsJsonArray()) {
            axes.add(e.getAsString());
         }
         dest.putStringList(key(), axes);
      }

      @Override
      public boolean extractFromGsonObject(JsonObject jo, PropertyMap.Builder dest) {
         if (super.extractFromGsonObject(jo, dest)) {
            return true;
         }

         // MM1 stored acquisition order using two flags, from the MDA settings

         List<String> axes = Lists.newArrayList();
         PropertyMap.Builder builder = PropertyMaps.builder();
         TIME_FIRST.extractFromGsonObject(jo, builder);
         SLICES_FIRST.extractFromGsonObject(jo, builder);
         PropertyMap flags = builder.build();

         if (flags.containsBoolean(TIME_FIRST.key())) {
            if (flags.getBoolean(TIME_FIRST.key(), false)) {
               Collections.addAll(axes, Coords.STAGE_POSITION, Coords.TIME_POINT);
            }
            else {
               Collections.addAll(axes, Coords.TIME_POINT, Coords.STAGE_POSITION);
            }

            if (flags.containsBoolean(SLICES_FIRST.key())) {
               if (flags.getBoolean(SLICES_FIRST.key(), false)) {
                  Collections.addAll(axes, Coords.CHANNEL, Coords.Z_SLICE);
               }
               else {
                  Collections.addAll(axes, Coords.Z_SLICE, Coords.CHANNEL);
               }
               dest.putStringList(key(), axes);
               return true;
            }
         } else {
            // This could be a scripted acquisition that has no information 
            // about axis order.  It is better to guess given what we know
            // about the available axis than not setting this field, since 
            // too much of the downstream code depends on this information
            INTENDED_DIMENSIONS.extractFromGsonObject(jo, builder);
            PropertyMap id = builder.build();
            if (id.containsPropertyMap(INTENDED_DIMENSIONS.key())) {
               PropertyMap id2 = id.getPropertyMap(INTENDED_DIMENSIONS.key(), null);
               String[] axesString = {Coords.P, Coords.T, Coords.Z, Coords.C};
               for (String axis : axesString) {
                  if (id2.containsInteger(axis) && id2.getInteger(axis, 0) > 0) {
                     axes.add(axis);
                  }
               }
               dest.putStringList(key(), axes);
               return true;
            }
         }
      
         return false;
      }

      @Override
      protected JsonElement convertToGson(PropertyMap pmap) {
         if (!pmap.containsKey(key())) {
            return null;
         }
         JsonArray ja = new JsonArray();
         for (String axis : pmap.getStringList(key())) {
            ja.add(new JsonPrimitive(axis));
         }
         return ja;
      }
   },

   BINNING("Binning", Metadata.class) {
      @Override
      public String getDescription() {
         return "The camera pixel binning size (equal vertical and horizontal binning)";
      }

      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         JsonPrimitive jp = je.getAsJsonPrimitive();
         if (jp.isNumber()) {
            dest.putInteger(key(), jp.getAsInt());
         }
         if (jp.isString()) { // "1x1", "2x2", ...
            dest.putInteger(key(),
                  Integer.parseInt(jp.getAsString().split("x", 2)[0]));
         }
      }

      @Override
      protected JsonElement convertToGson(PropertyMap pmap) {
         if (pmap.containsKey(key())) {
            return new JsonPrimitive(pmap.getInteger(key(), 0));
         }
         return null;
      }
   },

   BIT_DEPTH("BitDepth", Metadata.class) {
      @Override
      public String getDescription() {
         return "The number of significant bits in each pixel";
      }

      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         dest.putInteger(key(), je.getAsInt());
      }

      @Override
      public boolean extractFromGsonObject(JsonObject jo, PropertyMap.Builder dest) {
         if (super.extractFromGsonObject(jo, dest)) {
            return true;
         }
         if (jo.has(SUMMARY.key())) {
            JsonObject summary = jo.get(SUMMARY.key()).getAsJsonObject();
            if (SUMMARY.extractFromGsonObject(summary, dest)) {
               return true;
            }
         }
         return false;
      }

      @Override
      protected JsonElement convertToGson(PropertyMap pmap) {
         if (pmap.containsKey(key())) {
            return new JsonPrimitive(pmap.getInteger(key(), 0));
         }
         return null;
      }
   },

   CAMERA("Camera", Metadata.class) {
      @Override
      public String getDescription() {
         return "The device label of the camera used to acquire this image";
      }

      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         dest.putString(key(), je.getAsString());
      }

      @Override
      public boolean extractFromGsonObject(JsonObject jo, PropertyMap.Builder dest) {
         // hack: we should be able to rely on the tag "Camera" in the json data
         // however, the acquisition engine regularly inserts an empty value
         // causing use of an empty string.  The Core-Camera property is sometimes
         // set correctly (but not when collecting images from two cameras
         // running sequences.
         
         // TODO: investigate why the acquisition engine inserts an empty string 
         // fix it, then remove the second if below
         
         if (super.extractFromGsonObject(jo, dest) && 
                 !dest.build().getString(key(), "").isEmpty()) {
            return true;
         }
         if (jo.has("Core-Camera")) {
            JsonElement je = jo.get("Core-Camera");
            try {
               dest.putString(key(), je.getAsString());
               return true;
            } catch (UnsupportedOperationException uoe) {
                // we get this with data saved in 2.0-beta
            }
         }
         return false;
      }      
      
      @Override
      protected JsonElement convertToGson(PropertyMap pmap) {
         String string = pmap.getString(key(), null);
         if (string != null) {
            return new JsonPrimitive(string);
         }
         return null;
      }
   },

   CAMERA_CHANNEL_INDEX("CameraChannelIndex"),

   CHANNELS("Channels", SummaryMetadata.class) { // See INTENDED_DIMENSIONS
      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         dest.putInteger(Coords.CHANNEL, je.getAsInt());
      }

      @Override
      protected JsonElement convertToGson(PropertyMap pmap) {
         PropertyMap intendedDims = pmap.getPropertyMap(INTENDED_DIMENSIONS.
               key(), PropertyMaps.emptyPropertyMap());
         if (intendedDims.containsKey(Coords.CHANNEL)) {
            return new JsonPrimitive(intendedDims.getInteger(Coords.CHANNEL, 0));
         }
         return null;
      }
   },

   CHANNEL_SETTINGS("ChannelSettings", DisplaySettings.class),
   
   CHANNEL_COLOR("ChColor"),
   CHANNEL_COLORS("ChColors"),
   CHANNEL_CONTRAST_MAX("ChContrastMax"),
   CHANNEL_CONTRAST_MIN("ChContrastMin"),

   CHANNEL_GROUP("ChannelGroup", SummaryMetadata.class) {
      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         dest.putString(key(), je.getAsString());
      }

      @Override
      protected JsonElement convertToGson(PropertyMap pmap) {
         if (pmap.containsString(key())) {
            return new JsonPrimitive(pmap.getString(key(), null));
         }
         return JsonNull.INSTANCE;
      }
   },

   CHANNEL_INDEX("ChannelIndex", "channel", Coords.class) {
      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         dest.putInteger(Coords.CHANNEL, je.getAsInt());
      }

      @Override
      protected JsonElement convertToGson(PropertyMap pmap) {
         // Save zero even if missing
         return new JsonPrimitive(pmap.getInteger(Coords.CHANNEL, 0));
      }
   },

   CHANNEL_NAME("Channel", "ChannelName"),

   CHANNEL_NAMES("ChNames", SummaryMetadata.class) {
      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         List<String> names = Lists.newArrayList();
         for (JsonElement e : je.getAsJsonArray()) {
            names.add(e.getAsString());
         }
         dest.putStringList(key(), names);
      }

      @Override
      protected JsonElement convertToGson(PropertyMap pmap) {
         if (!pmap.containsKey(key())) {
            return null;
         }
         JsonArray ja = new JsonArray();
         for (String chan : pmap.getStringList(key())) {
            ja.add(new JsonPrimitive(chan));
         }
         return ja;
      }
   },
   
   COLOR("Color", ChannelDisplaySettings.class),

   COLOR_MODE("ColorMode", DisplaySettings.class),
   
   COMMENT("Comment", SummaryMetadata.class),

   COMPLETE_COORDS("completeCoords", Coords.class) {
      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         for (Map.Entry<String, JsonElement> e : je.getAsJsonObject().entrySet()) {
            dest.putInteger(e.getKey(), e.getValue().getAsInt());
         }
      }

      @Override
      public boolean extractFromGsonObject(JsonObject jo, PropertyMap.Builder dest) {
         if (super.extractFromGsonObject(jo, dest)) {
            return true;
         }

         boolean found = false;
         for (PropertyKey key : ImmutableList.of(PropertyKey.FRAME_INDEX,
               PropertyKey.POSITION_INDEX,
               PropertyKey.SLICE_INDEX,
               PropertyKey.CHANNEL_INDEX)) {
            found = key.extractFromGsonObject(jo, dest) || found;
         }
         return found;
      }

      @Override
      protected JsonElement convertToGson(PropertyMap pmap) {
         if (!pmap.containsKey(key())) {
            return null;
         }
         JsonObject jo = new JsonObject();
         for (String axis : pmap.keySet()) {
            jo.addProperty(axis, pmap.getInteger(axis, 0));
         }
         return jo;
      }
   },
   
   COMPONENT_SETTINGS("ComponentSettings", ChannelDisplaySettings.class),

   COMPUTER_NAME("ComputerName", SummaryMetadata.class) {
      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         dest.putString(key(), je.getAsString());
      }

      @Override
      protected JsonElement convertToGson(PropertyMap pmap) {
         if (pmap.containsString(key())) {
            return new JsonPrimitive(pmap.getString(key(), null));
         }
         return JsonNull.INSTANCE;
      }
   },

   CUSTOM_INTERVALS_MS("CustomIntervals_ms", SummaryMetadata.class) {
      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         List<Double> intervals = Lists.newArrayList();
         for (JsonElement e : je.getAsJsonArray()) {
            intervals.add(e.getAsDouble());
         }
         dest.putDoubleList(key(), intervals);
      }

      @Override
      protected JsonElement convertToGson(PropertyMap pmap) {
         if (!pmap.containsKey(key())) {
            return null;
         }
         JsonArray ja = new JsonArray();
         for (double intervalMs : pmap.getDoubleList(key())) {
            ja.add(new JsonPrimitive(intervalMs));
         }
         return ja;
      }
   },
   
   DEVICE("Device", MultiStagePosition.class) {
      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         dest.putString(key(), je.getAsString());
      }

      @Override
      protected JsonElement convertToGson(PropertyMap pmap) {
         return new JsonPrimitive(pmap.getString(key(), null));
      }
   },

   DIRECTORY("Directory", SummaryMetadata.class) {
      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         dest.putString(key(), je.getAsString());
      }

      @Override
      protected JsonElement convertToGson(PropertyMap pmap) {
         if (pmap.containsString(key())) {
            return new JsonPrimitive(pmap.getString(key(), null));
         }
         return JsonNull.INSTANCE;
      }
   },

   DISPLAY_SETTINGS("DisplaySettings", SummaryMetadata.class),
   
   DISPLAY_SETTINGS_FILE_NAME("DisplaySettings.json", DisplaySettings.class),

   ELAPSED_TIME_MS("ElapsedTime-ms", Metadata.class) {
      @Override
      public String getDescription() {
         return "The time elapsed since the start of acquisition (may use less accurate software timing)";
      }

      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         dest.putDouble(key(), je.getAsDouble());
      }

      @Override
      protected JsonElement convertToGson(PropertyMap pmap) {
         if (pmap.containsKey(key())) {
            return new JsonPrimitive(pmap.getDouble(key(), Double.NaN));
         }
         return null;
      }
   },

   EXPOSURE_MS("Exposure-ms", "ExposureMs", Metadata.class) {
      @Override
      public String getDescription() {
         return "The camera exposure duration";
      }

      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         dest.putDouble(key(), je.getAsDouble());
      }

      @Override
      protected JsonElement convertToGson(PropertyMap pmap) {
         if (pmap.containsKey(key())) {
            return new JsonPrimitive(pmap.getDouble(key(), Double.NaN));
         }
         return null;
      }
   },

   FILE_NAME("FileName", Metadata.class) {
      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         dest.putString(key(), je.getAsString());
      }

      @Override
      protected JsonElement convertToGson(PropertyMap pmap) {
         return new JsonPrimitive(pmap.getString(key(), null));
      }
   },

   FRAME_INDEX("Frame", "FrameIndex", "time", Coords.class) {
      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         dest.putInteger(Coords.TIME_POINT, je.getAsInt());
      }

      @Override
      protected JsonElement convertToGson(PropertyMap pmap) {
         // Save zero even if missing
         return new JsonPrimitive(pmap.getInteger(Coords.TIME_POINT, 0));
      }

      @Override
      protected List<String> additionalKeysToStoreForCompatibility() {
         return Collections.singletonList("FrameIndex");
      }
   },

   FRAMES("Frames") { // See INTENDED_DIMENSIONS
      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         dest.putInteger(Coords.TIME_POINT, je.getAsInt());
      }

      @Override
      protected JsonElement convertToGson(PropertyMap pmap) {
         PropertyMap intendedDims = pmap.getPropertyMap(INTENDED_DIMENSIONS.
               key(), PropertyMaps.emptyPropertyMap());
         if (intendedDims.containsKey(Coords.TIME_POINT)) {
            return new JsonPrimitive(intendedDims.getInteger(Coords.TIME_POINT, 0));
         }
         return null;
      }
   },

   GAMMA("Gamma", ComponentDisplaySettings.class),
   
   GRID_COLUMN("GridColumn", "gridColumn"),
   
   GRID_ROW("GridRow", "gridRow"),

   HEIGHT("Height") {
      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         dest.putInteger(key(), je.getAsInt());
      }
      
      @Override
      protected JsonElement convertToGson(PropertyMap pmap) {
         // Save zero even if missing
         return new JsonPrimitive(pmap.getInteger(key(), 0));
      }
   },
   
   HISTOGRAM_BIT_DEPTH("HistogramBitDepth", ChannelDisplaySettings.class),

   IJ_TYPE("IJType", Image.class) {
      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         dest.putInteger(key(), je.getAsInt());
      }

      @Override
      protected JsonElement convertToGson(PropertyMap pmap) {
         PixelType pixelType = pmap.getStringAsEnum(PIXEL_TYPE.key(),
               PixelType.class, null);
         if (pixelType == null) {
            return null;
         }

         return new JsonPrimitive(pixelType.imageJConstant());
      }
   },

   IMAGE_NUMBER("ImageNumber", Metadata.class) {
      @Override
      public String getDescription() {
         return "The sequence number of this image within a sequence (streaming) acquisition of the camera";
      }

      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         dest.putLong(key(), je.getAsLong());
      }

      @Override
      protected JsonElement convertToGson(PropertyMap pmap) {
         if (pmap.containsKey(key())) {
            return new JsonPrimitive(pmap.getLong(key(), 0));
         }
         return null;
      }
   },

   INTENDED_DIMENSIONS("IntendedDimensions", SummaryMetadata.class) {
      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         // User simple JSON object (axis => coord)
         PropertyMap fromGson = MM1JSONSerializer.fromGson(je);
         // The MM1JSON Serializer makes longs out of numbers.
         // We need them here as ints, so convert
         PropertyMap.Builder builder = PropertyMaps.builder();
         for (String key : fromGson.keySet()) {
            if (fromGson.containsLong(key)) {
               builder.putInteger(key, (int) fromGson.getLong(key, 1));
            } else {
               // NS: not sure how to handle this.  Can we be sure only to 
               // receive the correct keys?  I see no straight forward way 
               // to copy other properties
               ReportingUtils.showError("Found weird key in Intended dimensions: " + key);
            }
         }
         dest.putPropertyMap(key(), builder.build());
      }

      @Override
      public boolean extractFromGsonObject(JsonObject jo, PropertyMap.Builder dest) {
         if (super.extractFromGsonObject(jo, dest)) {
            return true;
         }

         PropertyMap.Builder builder = PropertyMaps.builder();
         FRAMES.extractFromGsonObject(jo, builder);
         POSITIONS.extractFromGsonObject(jo, builder);
         SLICES.extractFromGsonObject(jo, builder);
         CHANNELS.extractFromGsonObject(jo, builder);
         dest.putPropertyMap(key(), builder.build());
         return true;
      }

      @Override
      protected JsonElement convertToGson(PropertyMap pmap) {
         if (pmap.containsKey(key())) {
            // User simple JSON object (axis => coord)
            return MM1JSONSerializer.toGson(pmap.getPropertyMap(key(), null));
         }
         return null;
      }
   },

   INTERVAL_MS("Interval_ms", "IntervalMs", "interval_ms", "WaitInterval",
         SummaryMetadata.class) {
      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         dest.putDouble(key(), je.getAsDouble());
      }

      @Override
      protected JsonElement convertToGson(PropertyMap pmap) {
         if (pmap.containsKey(key())) {
            return new JsonPrimitive(pmap.getDouble(key(), Double.NaN));
         }
         return null;
      }
   },

   KEEP_SHUTTER_OPEN_CHANNELS("KeepShutterOpenChannels", "keepShutterOpenChannels",
         SummaryMetadata.class) {
      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         dest.putBoolean(key(), je.getAsBoolean());
      }

      @Override
      protected JsonElement convertToGson(PropertyMap pmap) {
         if (pmap.containsKey(key())) {
            return new JsonPrimitive(pmap.getBoolean(key(), false));
         }
         return null;
      }
   },

   KEEP_SHUTTER_OPEN_SLICES("KeepShutterOpenSlices", "keepShutterOpenSlices",
         SummaryMetadata.class) {
      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         dest.putBoolean(key(), je.getAsBoolean());
      }

      @Override
      protected JsonElement convertToGson(PropertyMap pmap) {
         if (pmap.containsKey(key())) {
            return new JsonPrimitive(pmap.getBoolean(key(), false));
         }
         return null;
      }
   },

   METADATA_VERSION("MetadataVersion", SummaryMetadata.class) {
      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         dest.putString(key(), je.getAsString());
      }

      @Override
      protected JsonElement convertToGson(PropertyMap pmap) {
         if (pmap.containsKey(key())) {
            return new JsonPrimitive(pmap.getString(key(), null));
         }
         return null;
      }
   },

   MICRO_MANAGER_VERSION("MicroManagerVersion", SummaryMetadata.class) {
      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         dest.putString(key(), je.getAsString());
      }

      @Override
      protected JsonElement convertToGson(PropertyMap pmap) {
         return new JsonPrimitive(pmap.getString(key(), null));
      }
   },

   MULTI_STAGE_POSITION__LABEL("Label", "LABEL", "label", MultiStagePosition.class) {
      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         dest.putString(key(), je.getAsString());
      }
      @Override
      protected JsonElement convertToGson(PropertyMap pMap) {
         if (pMap.containsKey(key())) {
            return new JsonPrimitive(pMap.getString(key(), null));
         }
         return null;
      }
   },

   MULTI_STAGE_POSITION__DEFAULT_XY_STAGE("DefaultXYStage", "DEFAULT_XY_STAGE", "defaultXYStage",
         MultiStagePosition.class) {
      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         dest.putString(key(), je.getAsString());
      }
      @Override
      protected JsonElement convertToGson(PropertyMap pMap) {
         if (pMap.containsKey(key())) {
            return new JsonPrimitive(pMap.getString(key(), null));
         }
         return null;
      }
   },

   MULTI_STAGE_POSITION__DEFAULT_Z_STAGE("DefaultZStage", "DEFAULT_Z_STAGE", "defaultZStage",
         MultiStagePosition.class) {
      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         dest.putString(key(), je.getAsString());
      }
      @Override
      protected JsonElement convertToGson(PropertyMap pMap) {
         if (pMap.containsKey(key())) {
            return new JsonPrimitive(pMap.getString(key(), null));
         }
         return null;
      }
   },

   MULTI_STAGE_POSITION__GRID_ROW("GridRow", "GRID_ROW", "gridRow", "GridRowIndex",
         MultiStagePosition.class) {
      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         dest.putInteger(key(), je.getAsInt());
      }
      @Override
      protected JsonElement convertToGson(PropertyMap pMap) {
         if (pMap.containsKey(key())) {
            return new JsonPrimitive(pMap.getInteger(key(), -1));
         }
         return null;
      }
   },

   MULTI_STAGE_POSITION__GRID_COLUMN("GridCol", "GRID_COL", "gridCol", "GridColumnIndex",
         MultiStagePosition.class) {
      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         dest.putInteger(key(), je.getAsInt());
      }
      @Override
      protected JsonElement convertToGson(PropertyMap pMap) {
         if (pMap.containsKey(key())) {
            return new JsonPrimitive(pMap.getInteger(key(), -1));
         }
         return null;
      }
   },

   MULTI_STAGE_POSITION__PROPERTIES("Properties", "PROPERTIES", "properties",
         MultiStagePosition.class) {
      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         // Could be stored as either MM1JSON/PropertyMap1, or PropertyMap2
         // format. Try the better-defined PM2 first.
         try {
            dest.putPropertyMap(key(), PropertyMapJSONSerializer.fromGson(je));
         }
         catch (Exception e) {
            dest.putPropertyMap(key(), MM1JSONSerializer.fromGson(je));
         }
      }
      @Override
      protected JsonElement convertToGson(PropertyMap pMap) {
         // TODO: Figure out what this is supposed to do (I don't even know
         // if multistageposition properties are ever used by anything
         // It is utterly unclear how to perform this translation, but 
         // returning null bombs saving of stage positions...
            return JsonNull.INSTANCE;
         }
   },
   
   MULTI_STAGE_POSITION__DEVICE("Device", MultiStagePosition.class) {
      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         dest.putString(key(), je.getAsString());
      }

      @Override
      protected JsonElement convertToGson(PropertyMap pmap) {
         return new JsonPrimitive(pmap.getString(key(), null));
      }
   },

   /**
    * It is unclear what is meant with this key.
    * Positions in the positionlist are multi stage positions.  These have Label,
    * DefaultXYSTage, DefaultZStage, GridRow , GridCol, and DevicePositions fields.
    * So, it seems this keyword catches the DevicePositions field within Multi_Stage_Positions
    * However, the code in here refers to NonPropertyMapJSONFormats.multiStagePosition()
    * which goes through all of the fields listed above
    * So, handling of the DevicePositions field is a complete hack, as it 
    * is unclear from the original design what the intention was, and this whole
    * construction going back and forth between different encodings still gives me
    * headaches even after having stared at it for way too many days....
    */
   MULTI_STAGE_POSITION__DEVICE_POSITIONS("DevicePositions", "DEVICES", "subpositions", "DeviceCoordinatesUm",
         MultiStagePosition.class) {
      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         // MM 2.0-beta stores JSON array of JSON objects with keys mapped to
         // StagePosition public fields, under "subpositions".
         // Also, MM 2.0-beta stores the MSPs in a PositionList similarly,
         // under the "DEVICES" key, but with different subkeys.
         List<PropertyMap> devPositions = Lists.newArrayList();
         if (je.isJsonArray()) {
            for (JsonElement e : je.getAsJsonArray()) {
               JsonObject jo = e.getAsJsonObject();
               PropertyMap msp =
                     NonPropertyMapJSONFormats.multiStageDevicePosition().
                     fromGson(jo);
               
               if (msp.size() == 0) { // this may be an old format stage position
                  msp = NonPropertyMapJSONFormats.oldStagePosition().fromGson(jo);
               }
               
               if (!msp.containsInteger(STAGE_POSITION__NUMAXES.key())) 
               {  // "modern" (or also "old"?  who knows...) format that has just the name
                  // and coordinates for whatever axes the device has
                  devPositions.add(msp);
               } else { // old format with 3 values
                  int n = msp.getInteger(STAGE_POSITION__NUMAXES.key(), 0);
                  if (n < 1 || n > 3) {
                     throw new JsonParseException(
                             "Unexpected number of stage axes in stage position record");
                  }
                  double[] coords = new double[n];
                  for (int i = 0; i < n; ++i) {
                     coords[i] = msp.getDouble(ImmutableList.of(
                             STAGE_POSITION__COORD1_UM.key(),
                             STAGE_POSITION__COORD2_UM.key(),
                             STAGE_POSITION__COORD3_UM.key()).get(i), Double.NaN);
                  }
                  devPositions.add(PropertyMaps.builder().
                          putString(STAGE_POSITION__DEVICE.key(),
                                  msp.getString(STAGE_POSITION__DEVICE.key(), null)).
                          putDoubleList(STAGE_POSITION__POSITION_UM.key(), coords).
                          build());
               } 
            }
         }
         // MM 1.x stored JSON object with keys = stage names; values = arrays,
         // under "DeviceCoordinatesUm".
         else {
            for (Map.Entry<String, JsonElement> e : je.getAsJsonObject().
                  entrySet()) {
               JsonArray ja = e.getValue().getAsJsonArray();
               double[] coords = new double[ja.size()];
               for (int i = 0; i < ja.size(); ++i) {
                  coords[i] = ja.get(i).getAsDouble();
               }
               devPositions.add(PropertyMaps.builder().
                     putString(STAGE_POSITION__DEVICE.key(), e.getKey()).
                     putDoubleList(STAGE_POSITION__POSITION_UM.key(), coords).
                     build());
            }
         }
         dest.putPropertyMapList(MULTI_STAGE_POSITION__DEVICE_POSITIONS.key(), devPositions);
      }
      
      @Override
      protected JsonElement convertToGson(PropertyMap pMap) {
         if (pMap.containsPropertyMapList(key())) {
            JsonArray ja = new JsonArray();
            for (PropertyMap pm : pMap.getPropertyMapList(key())) {
               ja.add(NonPropertyMapJSONFormats.multiStageDevicePosition().toGson(pm));
            }
            return ja;
         }
         return JsonNull.INSTANCE;  // Todo: log?
      }
   },

   NEXT_FRAME("NextFrame"),

   PIXEL_ASPECT("PixelAspect", "pixelAspect", Metadata.class) {
      @Override
      public String getDescription() {
         return "The physical aspect ratio of the pixels; for example, if 2.0, the pixels are twice as tall as they are wide";
      }

      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         dest.putDouble(key(), je.getAsDouble());
      }

      @Override
      protected JsonElement convertToGson(PropertyMap pmap) {
         if (pmap.containsKey(key())) {
            return new JsonPrimitive(pmap.getDouble(key(), Double.NaN));
         }
         return null;
      }
   },

   PIXEL_SIZE_AFFINE("PixelSizeAffine", Metadata.class) {
      @Override
      public String getDescription() {
         return "Affine transform describing the geometric relation between stage-space and camera-space";
      }

      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         // PixelSizeAffinString is a ';'-separated sequence of 6 integers
         String s = je.getAsString();

         String[] affNumbers = s.split(";");
         if (affNumbers.length == 6) {  // TODO: handle other situations?
            double[] atf = new double[6];
            for (int i = 0; i < 6; i++) {
               atf[i] = Double.parseDouble(affNumbers[i]);
            }
            // regretfully, the Core and Java representations are permuations
            double[] flatMatrix = {atf[0], atf[3], atf[1], atf[4], atf[2], atf[5]};
            dest.putAffineTransform(key(), new AffineTransform(flatMatrix));
         }         
      }

      @Override
      protected JsonElement convertToGson(PropertyMap pmap) {
         if (!pmap.containsAffineTransform(key())) {
            return null;
         }
         AffineTransform aff = pmap.getAffineTransform(key(), null);
         double[] fm = new double[6];
         aff.getMatrix(fm);
         // regretfully, the Core and Java representations are permuations
         double[] atf = {fm[0], fm[2], fm[2], fm[1], fm[3], fm[5]};
         String afString = Joiner.on(";").join(atf[0], atf[1], atf[2], atf[3], atf[4], atf[5]);
         return new JsonPrimitive(afString);
      }
   },
   
   PIXEL_SIZE_UM("PixelSizeUm", "PixelSize_um", Metadata.class) {
      @Override
      public String getDescription() {
         return "The physical size of the pixels in the specimen space";
      }

      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         dest.putDouble(key(), je.getAsDouble());
      }

      @Override
      protected JsonElement convertToGson(PropertyMap pmap) {
         if (pmap.containsKey(key())) {
            return new JsonPrimitive(pmap.getDouble(key(), Double.NaN));
         }
         return null;
      }
   },

   PIXEL_TYPE("PixelType", Image.class) {
      @Override
      public String getDescription() {
         return "The pixel format of the image (GRAY8, GRAY16, or RGB32)";
      }

      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         dest.putString(key(), je.getAsString());
      }

      @Override
      public boolean extractFromGsonObject(JsonObject jo, PropertyMap.Builder dest) {
         if (super.extractFromGsonObject(jo, dest)) {
            return true;
         }

         PropertyMap.Builder b = PropertyMaps.builder();
         if (IJ_TYPE.extractFromGsonObject(jo, b)) {
            int ijType = b.build().getInteger(IJ_TYPE.key(), -1);
            dest.putEnumAsString(key(), PixelType.valueOfImageJConstant(ijType));
            return true;
         }
         return false;
      }

      @Override
      protected JsonElement convertToGson(PropertyMap pmap) {
         if (pmap.containsKey(key())) {
            return new JsonPrimitive(pmap.getString(key(), null));
         }
         return null;
      }
   },

   PLAYBACK_FPS("PlaybackFPS", DisplaySettings.class),
   
   POSITIONS("Positions", SummaryMetadata.class) { // See INTENDED_DIMENSIONS
      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         dest.putInteger(Coords.STAGE_POSITION, je.getAsInt());
      }

      @Override
      protected JsonElement convertToGson(PropertyMap pmap) {
         PropertyMap intendedDims = pmap.getPropertyMap(INTENDED_DIMENSIONS.
               key(), PropertyMaps.emptyPropertyMap());
         if (intendedDims.containsKey(Coords.STAGE_POSITION)) {
            return new JsonPrimitive(intendedDims.getInteger(Coords.STAGE_POSITION, 0));
         }
         return null;
      }
   },

   POSITION_INDEX("PositionIndex", "position", Coords.class) {
      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         dest.putInteger(Coords.STAGE_POSITION, je.getAsInt());
      }

      @Override
      protected JsonElement convertToGson(PropertyMap pmap) {
         // Save zero even if missing
         return new JsonPrimitive(pmap.getInteger(Coords.STAGE_POSITION, 0));
      }
   },

   POSITION_LIST__ID("ID", PositionList.class),
   POSITION_LIST__VERSION("VERSION", PositionList.class),

   POSITION_NAME("PositionName", "Position", Metadata.class) {
      @Override
      public String getDescription() {
         return "The user-assigned name for the stage position at which this image was acquired";
      }

      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         dest.putString(key(), je.getAsString());
      }

      @Override
      protected JsonElement convertToGson(PropertyMap pmap) {
         return new JsonPrimitive(pmap.getString(key(), null));
      }
   },
   
   POSITION_UM("Position_um", MultiStagePosition.class) {
      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         List<Double> positions = Lists.newArrayList();
         for (JsonElement e : je.getAsJsonArray()) {
            positions.add(e.getAsDouble());
         }
         dest.putDoubleList(key(), positions);
      }

      @Override
      protected JsonElement convertToGson(PropertyMap pmap) {
         if (!pmap.containsKey(key())) {
            return null;
         }
         JsonArray ja = new JsonArray();
         for (double positions : pmap.getDoubleList(key())) {
            ja.add(new JsonPrimitive(positions));
         }
         return ja;
      }
   },

   PREFIX("Prefix", SummaryMetadata.class) {
      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         dest.putString(key(), je.getAsString());
      }

      @Override
      protected JsonElement convertToGson(PropertyMap pmap) {
         String string = pmap.getString(key(), null);
         if (string != null) {
            return new JsonPrimitive(string);
         }
         return null;
      }
   },

   PROFILE_NAME("ProfileName", SummaryMetadata.class) {
      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         dest.putString(key(), je.getAsString());
      }

      @Override
      protected JsonElement convertToGson(PropertyMap pmap) {
         if (pmap.containsString(key())) {
            return new JsonPrimitive(pmap.getString(key(), null));
         }
         return JsonNull.INSTANCE;
      }
   },

   RECEIVED_TIME("ReceivedTime", "receivedTime", Metadata.class) {
      @Override
      public String getDescription() {
         return "The approximate time at which this image was received by the MMStudio application (may be delayed to different degrees from when the exposure actually occurred)";
      }

      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         dest.putString(key(), je.getAsString());
      }

      @Override
      protected JsonElement convertToGson(PropertyMap pmap) {
         // To enable re-saving old data set, do not require this key
         String receivedTime = pmap.getString(key(), null);
         if (receivedTime != null) {
            return new JsonPrimitive(receivedTime);
         }
         return null;
      }
   },

   ROI("ROI", Metadata.class) {
      @Override
      public String getDescription() {
         return "The region of interest on the camera sensor chip (in binned coordinates) used to acquire this image";
      }

      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         // roiString is a '-'-separated sequence of 4 integers (an unfortunate
         // choice when the integers are negative). Although all 4 parameters
         // are positive, negative numbers have been observed in the wild, and
         // the best thing to do here is to preserve their value.
         String s = je.getAsString();

         // First change the separater to comma while preserving minus sign
         s = s.replaceAll("-", "_");
         s = s.replaceAll("__", ",-");
         s = s.replaceFirst("^_", "-");
         s = s.replaceAll("_", ",");

         String[] xywh = s.split(",");
         int x, y, w, h;
         x = Integer.parseInt(xywh[0]);
         y = Integer.parseInt(xywh[1]);
         w = Integer.parseInt(xywh[2]);
         h = Integer.parseInt(xywh[3]);
         Rectangle roi = new Rectangle(x, y, w, h);

         dest.putRectangle(key(), roi);
      }

      @Override
      protected JsonElement convertToGson(PropertyMap pmap) {
         if (!pmap.containsKey(key())) {
            return null;
         }
         Rectangle rect = pmap.getRectangle(key(), null);
         String hyphenated = Joiner.on("-").join(
               rect.x, rect.y, rect.width, rect.height);
         return new JsonPrimitive(hyphenated);
      }
   },

   ROI_AUTOSCALE("ROIAutoscale", DisplaySettings.class),
   
   SCALING_MIN("ScalingMin", ComponentDisplaySettings.class),
   
   SCALING_MAX("ScalingMax", ComponentDisplaySettings.class),
   
   SCOPE_DATA("ScopeData", "scopeData", Metadata.class) {
      @Override
      public String getDescription() {
         return "The states of device properties when this image was acquired (some properties do not update for every image)";
      }

      // Device properties were stored mixed with all other metadata keys
      // in MM1. For backward compatibility, MM2 also does so (although some
      // beta versions erroneously stored PropertyMap-1 style PropType -
      // PropVal pairs). MM2 stores the list of keys for device
      // properties (see SCOPE_DATA_KEYS).

      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         dest.putPropertyMap(key(), MM1JSONSerializer.fromGson(je));
      }

      @Override
      public boolean extractFromGsonObject(JsonObject jo, PropertyMap.Builder dest) {
         if (super.extractFromGsonObject(jo, dest)) {
            return true;
         }
         PropertyMap.Builder tmp = PropertyMaps.builder();
         if (SCOPE_DATA_KEYS.extractFromGsonObject(jo, tmp)) {
            PropertyMap.Builder builder = PropertyMaps.builder();
            for (String key : tmp.build().getStringList(SCOPE_DATA_KEYS.key())) {
               String val = "";
               if (jo.get(key).isJsonObject()) {
                  val = jo.get(key).getAsJsonObject().get("PropVal").getAsString();
               } else if (jo.get(key).isJsonPrimitive()) {
                  val = jo.get(key).getAsString();
               }
               builder.putString(key, val);
            }
            dest.putPropertyMap(key(), builder.build());
            return true;
         }
         // In the absence of ScopeDataKeys (i.e. written by MM1), there is no
         // way to determine which nonstandard keys are device properties, so
         // they all go into USER_DATA. See there.
         return false;
      }

      @Override
      public boolean storeInGsonObject(PropertyMap pmap, JsonObject dest) {
         // For MM1 compatibility, store as flat keys, to be read later with
         // the help of SCOPE_DATA_KEYS
         if (!pmap.containsKey(key())) {
            return false;
         }
         PropertyMap scopeData = pmap.getPropertyMap(key(), null);
         for (String key : scopeData.keySet()) {
            dest.addProperty(key, scopeData.getValueAsString(key, null));
         }
         return true;
      }
   },

   SCOPE_DATA_KEYS("ScopeDataKeys", "scopeDataKeys", "StateCache-keys",
         Metadata.class) {
      @Override
      public String getDescription() {
         return "An internally used list of the device property values stored in image metadata";
      }

      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         JsonArray ja = je.getAsJsonArray();
         List<String> keys = Lists.newArrayList();
         for (JsonElement kje : ja) {
            keys.add(kje.getAsString());
         }
         dest.putStringList(key(), keys);
      }

      @Override
      protected JsonElement convertToGson(PropertyMap pmap) {
         if (!pmap.containsKey(SCOPE_DATA.key())) {
            return null;
         }
         List<String> keys = Lists.newArrayList(
               pmap.getPropertyMap(SCOPE_DATA.key(), null).keySet());
         JsonArray ja = new JsonArray();
         for (String key : keys) {
            ja.add(new JsonPrimitive(key));
         }
         return ja;
      }
   },
   
   SNAP_LIVE_DISPLAY_SETTINGS("SnapLiveDisplaySettings", SnapLiveManager.class),

   SLICES("Slices", SummaryMetadata.class) { // See INTENDED_DIMENSIONS
      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         dest.putInteger(Coords.Z_SLICE, je.getAsInt());
      }

      @Override
      protected JsonElement convertToGson(PropertyMap pmap) {
         PropertyMap intendedDims = pmap.getPropertyMap(INTENDED_DIMENSIONS.
               key(), PropertyMaps.emptyPropertyMap());
         if (intendedDims.containsKey(Coords.Z_SLICE)) {
            return new JsonPrimitive(intendedDims.getInteger(Coords.Z_SLICE, 0));
         }
         return null;
      }
   },

   SLICES_FIRST("SlicesFirst", SummaryMetadata.class) { // See AXIS_ORDER
      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         dest.putBoolean(key(), je.getAsBoolean());
      }

      @Override
      protected JsonElement convertToGson(PropertyMap pmap) {
         // Looping over slices first means Z axis later than channel axis
         List<String> axisOrder = pmap.getStringList(AXIS_ORDER.key());
         boolean slicesFirst =
               axisOrder.indexOf(Coords.Z_SLICE) >
               axisOrder.indexOf(Coords.CHANNEL) &&
               axisOrder.contains(Coords.CHANNEL);
         return new JsonPrimitive(pmap.getBoolean(key(), slicesFirst));
      }
   },

   SLICE_INDEX("Slice", "SliceIndex", "z", Coords.class) {
      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         dest.putInteger(Coords.Z_SLICE, je.getAsInt());
      }

      @Override
      protected JsonElement convertToGson(PropertyMap pmap) {
         // Save zero even if missing
         return new JsonPrimitive(pmap.getInteger(Coords.Z_SLICE, 0));
      }

      @Override
      protected List<String> additionalKeysToStoreForCompatibility() {
         return Collections.singletonList("SliceIndex");
      }
   },

   SLICE_POSITION_UM("SlicePosition"),
   SOURCE("Source"),

   STAGE_POSITION__COORD1_UM("X", "x", StagePosition.class) {
      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         dest.putDouble(key(), je.getAsDouble());
      }
   },

   STAGE_POSITION__COORD2_UM("Y", "y", StagePosition.class) {
      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         dest.putDouble(key(), je.getAsDouble());
      }
   },

   STAGE_POSITION__COORD3_UM("Z", "z", StagePosition.class) {
      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         dest.putDouble(key(), je.getAsDouble());
      }
   },

   STAGE_POSITION__DEVICE("Device", "DEVICE", "stageName", StagePosition.class) {
      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         dest.putString(key(), je.getAsString());
      }
      @Override
      public JsonElement convertToGson(PropertyMap source) {
         if (!source.containsString(key())) {
            return null;
         }
         return new JsonPrimitive(source.getString(key(), null));
      }
   },

   STAGE_POSITION__NUMAXES("NumberOfAxes", "AXES", "numAxes", StagePosition.class) {
      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         dest.putInteger(key(), je.getAsInt() * 3); // old style stage positions always had 3 axes...
      }
   },

   STAGE_POSITION__POSITION_UM("Position_um", StagePosition.class) {
      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         List<Double> coords = Lists.newArrayList();
         for (JsonElement e : je.getAsJsonArray()) {
            coords.add(e.getAsDouble());
         }
         dest.putDoubleList(key(), coords);
      }
      @Override
      protected JsonElement convertToGson(PropertyMap source) {
         if (!source.containsDoubleList((key()))) {
            return JsonNull.INSTANCE;
         }
         JsonArray ja = new JsonArray();
         for (Double d : source.getDoubleList(key(), new ArrayList<>())) {
            ja.add(new JsonPrimitive(d));
         }
         return ja;
      }
   },

   STAGE_POSITIONS("StagePositions", "InitialPositionList", "POSITIONS",
         SummaryMetadata.class, PositionList.class)
   {
      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         List<PropertyMap> positions = Lists.newArrayList();
         for (JsonElement e : je.getAsJsonArray()) {
            positions.add(NonPropertyMapJSONFormats.multiStagePosition().fromGson(e));
         }
         dest.putPropertyMapList(key(), positions);
      }

      @Override
      public JsonElement convertToGson(PropertyMap source) {
         if (!source.containsPropertyMapList(key())) {
            return null;
         }
         JsonArray ja = new JsonArray();
         for (PropertyMap msp : source.getPropertyMapList(key())) {
            ja.add(NonPropertyMapJSONFormats.multiStagePosition().toGson(msp));
         }
         return ja;
      }
   },

   START_TIME("StartTime", "Time", SummaryMetadata.class) { // See also TIME
      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         dest.putString(key(), je.getAsString());
      }

      @Override
      protected JsonElement convertToGson(PropertyMap pmap) {
         if (pmap.containsString(key())) {
            return new JsonPrimitive(pmap.getString(key(), null));
         }
         return JsonNull.INSTANCE;
      }
   },

   SUMMARY("Summary"),
   TIME("Time", Metadata.class), // See also START_TIME

   TIME_FIRST("TimeFirst") { // See AXIS_ORDER
      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         dest.putBoolean(key(), je.getAsBoolean());
      }

      @Override
      protected JsonElement convertToGson(PropertyMap pmap) {
         // Looping over time first means time axis later than position axis
         List<String> axisOrder = pmap.getStringList(AXIS_ORDER.key());
         boolean timeFirst =
               axisOrder.indexOf(Coords.TIME_POINT) >
               axisOrder.indexOf(Coords.STAGE_POSITION) &&
               axisOrder.contains(Coords.STAGE_POSITION);
         return new JsonPrimitive(pmap.getBoolean(key(), timeFirst));
      }
   },
   
   UNIFORM_COMPONENT_SCALING("UniformComponentScaling", ChannelDisplaySettings.class),

   UNIFORM_CHANNEL_SCALING("UniformChannelScaling", DisplaySettings.class),
   
   USE_CAMERA_BIT_DEPTH("UseCameraBitDepth", ChannelDisplaySettings.class),
   
   USER_DATA("UserData", "userData", Metadata.class, SummaryMetadata.class) {
      @Override
      public String getDescription() {
         return "User-assigned and other miscellaneous data attached to this image (in files saved by \u00B5Manager 1.x, this includes device properties)";
      }

      @Override
      public void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         // Could be stored as either MM1JSON/PropertyMap1, or PropertyMap2
         // format. Try the better-defined PM2 first.
         try {
            dest.putPropertyMap(key(), PropertyMapJSONSerializer.fromGson(je));
         }
         catch (Exception e) {
            dest.putPropertyMap(key(), MM1JSONSerializer.fromGson(je));
         }
      }

      @Override
      public boolean extractFromGsonObject(JsonObject jo, PropertyMap.Builder dest) {
         if (super.extractFromGsonObject(jo, dest)) {
            return true;
         }

         // In the absence of explicitly saved user data, we treat all flat
         // keys as user data, excluding known standard keys and scope data
         // keys (to the extent possible).

         Set<String> scopeDataKeys = new HashSet<>();
         PropertyMap.Builder tmp = PropertyMaps.builder();
         if (SCOPE_DATA_KEYS.extractFromGsonObject(jo, tmp)) {
            scopeDataKeys.addAll(tmp.build().getStringList(SCOPE_DATA_KEYS.key()));
         }

         // Treat flat keys that are not standard fields as user data
         // (includes device properties if SCOPE_DATA_KEYS unavailable)
         PropertyMap.Builder builder = PropertyMaps.builder();
         for (Map.Entry<String, JsonElement> e : jo.entrySet()) {
            try {
            if (!isKnownKey(e.getKey()) && !e.getValue().isJsonNull() &&
                  !scopeDataKeys.contains(e.getKey())) {
               if (e.getValue().isJsonArray()) {
                  JsonArray jsonArray = e.getValue().getAsJsonArray();
                  for (int i = 0; i < jsonArray.size(); i++) {
                     JsonElement je2 = jsonArray.get(i);
                     builder.putString(e.getKey(), je2.getAsString());
                  }
               } else {
                  builder.putString(e.getKey(), e.getValue().getAsString());
               }
            }
            } catch (IllegalStateException ise) {
               ReportingUtils.logError(ise, "IllegalStateError reading value of " + e.getKey());
            }
         }
         dest.putPropertyMap(key(), builder.build());
         return true;
      }

      @Override
      public JsonElement convertToGson(PropertyMap pmap) {
         return PropertyMapJSONSerializer.toGson(pmap.getPropertyMap(key(), PropertyMaps.emptyPropertyMap()));
      }
   },

   USER_NAME("UserName", SummaryMetadata.class) {
      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         dest.putString(key(), je.getAsString());
      }

      @Override
      protected JsonElement convertToGson(PropertyMap pmap) {
         String string = pmap.getString(key(), null);
         if (string != null) {
            return new JsonPrimitive(string);
         }
         return null;
      }
   },

   UUID("UUID", Metadata.class) {
      @Override
      public String getDescription() {
         // TODO provide better definition
         return "The Universally Unique Identifier assigned to this image";
      }

      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         dest.putUUID(key(), java.util.UUID.fromString(je.getAsString()));
      }

      @Override
      protected JsonElement convertToGson(PropertyMap pmap) {
         if (pmap.containsKey(key())) {
            return new JsonPrimitive(pmap.getUUID(key(), null).toString());
         }
         return null;
      }
   },
   
   VISIBLE("Visible", ChannelDisplaySettings.class),

   WIDTH("Width", Image.class) {
      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         dest.putInteger(key(), je.getAsInt());
      }
      
      @Override
      protected JsonElement convertToGson(PropertyMap pmap) {
         // Save zero even if missing
         return new JsonPrimitive(pmap.getInteger(key(), 0));
      }
   },

   X_POSITION_UM("XPositionUm", Metadata.class) {
      @Override
      public String getDescription() {
         return "The last-known X position of the default XY stage at the time of acquisition (may not update for every image)";
      }

      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         dest.putDouble(key(), je.getAsDouble());
      }

      @Override
      protected JsonElement convertToGson(PropertyMap pmap) {
         if (pmap.containsKey(key())) {
            return new JsonPrimitive(pmap.getDouble(key(), Double.NaN));
         }
         return null;
      }
   },

   Y_POSITION_UM("YPositionUm", Metadata.class) {
      @Override
      public String getDescription() {
         return "The last-known Y position of the default XY stage at the time of acquisition (may not update for every image)";
      }

      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         dest.putDouble(key(), je.getAsDouble());
      }

      @Override
      protected JsonElement convertToGson(PropertyMap pmap) {
         if (pmap.containsKey(key())) {
            return new JsonPrimitive(pmap.getDouble(key(), Double.NaN));
         }
         return null;
      }
   },

   Z_POSITION_UM("ZPositionUm", Metadata.class) {
      @Override
      public String getDescription() {
         return "The last-known position of the default focus drive or Z stage at the time of acquisition (may not update for every image)";
      }

      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         dest.putDouble(key(), je.getAsDouble());
      }

      @Override
      protected JsonElement convertToGson(PropertyMap pmap) {
         if (pmap.containsKey(key())) {
            return new JsonPrimitive(pmap.getDouble(key(), Double.NaN));
         }
         return null;
      }
   },

   ZOOM_RATIO("ZoomRatio", DisplaySettings.class),
   
   Z_STEP_UM("z-step_um", SummaryMetadata.class) {
      @Override
      protected void convertFromGson(JsonElement je, PropertyMap.Builder dest) {
         dest.putDouble(key(), je.getAsDouble());
      }

      @Override
      protected JsonElement convertToGson(PropertyMap pmap) {
         if (pmap.containsKey(key())) {
            return new JsonPrimitive(pmap.getDouble(key(), Double.NaN));
         }
         return null;
      }
   },

   ;

   private final String canonical_;
   private final String[] historical_;
   private final Set<Class<?>> affiliations_;

   private static final Set<String> ALL_SPELLINGS;
   static {
      ImmutableSet.Builder<String> builder = ImmutableSet.builder();
      for (PropertyKey e : values()) {
         for (String k : e.getAllKeys()) {
            builder.add(k);
         }
      }
      ALL_SPELLINGS = builder.build();
   }

   private static final Map<String, String> UNITS = ImmutableMap.of(
         "um", "\u00B5m",
         "ms", "ms"
   );

   private PropertyKey(String canonical,
         Object... historicalStringsAndAffiliations)
   {
      Preconditions.checkNotNull(canonical);
      canonical_ = canonical;
      List<String> historical = Lists.newArrayList();
      List<Class<?>> affiliations = Lists.newArrayList();
      for (Object o : historicalStringsAndAffiliations) {
         if (o instanceof String) {
            historical.add((String) o);
         }
         else if (o instanceof Class) {
            affiliations.add((Class<?>) o);
         }
      }
      historical_ = historical.toArray(new String[historical.size()]);
      affiliations_ = ImmutableSet.copyOf(affiliations);
   }

   public final String getDisplayName() {
      String name = name().replace("_", " ").toLowerCase();
      name = name.substring(0, 1).toUpperCase() + name.substring(1);
      for (String unitSuffix : UNITS.keySet()) {
         if (name.endsWith(" " + unitSuffix)) {
            name = name.substring(0, name.length() - unitSuffix.length()) +
                  "(" + UNITS.get(unitSuffix) + ")";
         }
      }
      return name;
   }

   public final String key() {
      return canonical_;
   }

   public final List<String> getHistoricalKeys() {
      return Collections.unmodifiableList(Arrays.asList(historical_));
   }

   public final List<String> getAllKeys() {
      return Lists.asList(canonical_, historical_);
   }

   public String getDescription() {
      return "(Description unavailable)";
   }

   public final String getToolTip() {
      StringBuilder sb = new StringBuilder().
            append("<html>").
            append(HtmlEscapers.htmlEscaper().escape(getDescription())).
            append("<br />Technical info: ").
            append("canonical key = ").
            append(key());
      if (!getHistoricalKeys().isEmpty()) {
         sb.append("; historic key(s) = ");
         sb.append(Joiner.on(", ").join(getHistoricalKeys()));
      }
      return sb.append("</html>").toString();
   }

   /**
    * Parse the value for this key given as a JSON element and place it in a
    * property map builder.
    * <p>
    * Implementations should throw (sensible) unchecked exceptions if the value
    * is invalid.
    * <p>
    * The parsed value must be placed in {@code destination} under the key
    * returned by {@code getCanonicalKey}.
    *
    * @param element the Gson element
    * @param destination the property map builder into which the parsed value
    * should be placed
    */
   protected void convertFromGson(JsonElement element,
         PropertyMap.Builder destination)
   {
      throw new UnsupportedOperationException(name());
   }

   /**
    * Create the JSON element representing the value for this key.
    * @param source property map in which to find the value for this key
    * @return the JSON element representing the value for this key
    */
   protected JsonElement convertToGson(PropertyMap source) {
      throw new UnsupportedOperationException(name());
   }

   protected List<String> additionalKeysToStoreForCompatibility() {
      return Collections.emptyList();
   }

   /**
    * Parse the value stored in the containing JSON object.
    * <p>
    * In most cases we just examine all keys (default implementation), but in
    * some cases this method can be overridden to add special behavior, such as
    * looking under different keys.
    *
    * @param source non-property-map JSON object from which to extract the
    * value for this key
    * @param destination property map builder to which the value should be
    * stored
    * @return true if value was found and placed in {@code destination}; false
    * otherwise
    */
   public boolean extractFromGsonObject(JsonObject source,
         PropertyMap.Builder destination) {
      for (String key : getAllKeys()) {
         if (source.has(key) && !source.get(key).isJsonNull()) {
            convertFromGson(source.get(key), destination);
            return true;
         }
      }
      return false;
   }

   /**
    * Adds the key to a JSON object.
    *
    * @param source property map in which to find the value for this key
    * @param destination non-property-map JSON object to which the value should
    * be added under this key
    * @return true if the key was found in {@code source} and was added to
    * {@code destination}; false otherwise
    */
   public boolean storeInGsonObject(PropertyMap source,
         JsonObject destination) {
      JsonElement e = convertToGson(source);
      if (e != null) {
         destination.add(key(), e);
         for (String additionalKey : additionalKeysToStoreForCompatibility()) {
            destination.add(additionalKey, e);
         }
         return true;
      }
      return false;
   }

   public static boolean isKnownKey(String key) {
      return ALL_SPELLINGS.contains(key);
   }

}