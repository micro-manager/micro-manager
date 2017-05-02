/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.internal.propertymap;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Set;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.data.Coords;
import org.micromanager.data.internal.PropertyKey;

/**
 * High-level format conversion between MM1-style JSON and modern property maps.
 * @see MM1JSONSerializer, MetadataKey
 * @author Mark A. Tsuchida
 */
public abstract class NonPropertyMapJSONFormats {
   private static final NonPropertyMapJSONFormats metadataInstance_ =
         new MetadataFormat();
   private static final NonPropertyMapJSONFormats summaryInstance_ =
         new SummaryFormat();
   private static final NonPropertyMapJSONFormats positionListInstance_ =
         new PositionListFormat();
   private static final NonPropertyMapJSONFormats mspInstance_ =
         new MultiStagePositionFormat();
   private static final NonPropertyMapJSONFormats coordsInstance_ =
         new CoordsFormat();
   private static final NonPropertyMapJSONFormats imageFormatInstance_ =
         new ImageFormat();

   public static NonPropertyMapJSONFormats metadata() {
      return metadataInstance_;
   }

   public static NonPropertyMapJSONFormats summaryMetadata() {
      return summaryInstance_;
   }

   public static NonPropertyMapJSONFormats positionList() {
      return positionListInstance_;
   }

   public static NonPropertyMapJSONFormats multiStagePosition() {
      return mspInstance_;
   }

   public static NonPropertyMapJSONFormats coords() {
      return coordsInstance_;
   }

   public static NonPropertyMapJSONFormats imageFormat() {
      return imageFormatInstance_; }

   @SuppressWarnings("UseSpecificCatch")
   public final PropertyMap fromJSON(String json) throws IOException {
      try {
         JsonReader reader = new JsonReader(new StringReader(json));
         reader.setLenient(true);
         JsonParser parser = new JsonParser();
         return fromGson(parser.parse(reader).getAsJsonObject());
      }
      catch (Exception e) {
         throw new IOException("Invalid data", e);
      }
   }

   public final String toJSON(PropertyMap canonical) {
      Gson gson = new GsonBuilder().
            disableHtmlEscaping().
            create();
      return gson.toJson(toGson(canonical));
   }

   public abstract PropertyMap fromGson(JsonElement je);

   public JsonElement toGson(PropertyMap pmap) {
      throw new UnsupportedOperationException(getClass().getSimpleName() +
            "should be written as standard PropertyMap JSON, not MM1-style JSON");
   }

   private static final class MetadataFormat extends NonPropertyMapJSONFormats {
      @Override
      public PropertyMap fromGson(JsonElement je) {
         PropertyMap.Builder builder = PropertyMaps.builder();
         for (PropertyKey key : ImmutableList.of(
               PropertyKey.UUID,
               PropertyKey.CAMERA,
               PropertyKey.BINNING,
               PropertyKey.ROI,
               PropertyKey.BIT_DEPTH,
               PropertyKey.EXPOSURE_MS,
               PropertyKey.ELAPSED_TIME_MS,
               PropertyKey.IMAGE_NUMBER,
               PropertyKey.RECEIVED_TIME,
               PropertyKey.PIXEL_SIZE_UM,
               PropertyKey.PIXEL_ASPECT,
               PropertyKey.POSITION_NAME,
               PropertyKey.X_POSITION_UM,
               PropertyKey.Y_POSITION_UM,
               PropertyKey.Z_POSITION_UM,
               PropertyKey.SCOPE_DATA,
               PropertyKey.USER_DATA))
         {
            key.extractFromGsonObject(je.getAsJsonObject(), builder);
         }
         return builder.build();
      }

      @Override
      public JsonElement toGson(PropertyMap pmap) {
         JsonObject jo = new JsonObject();
         for (PropertyKey key : ImmutableList.of(
               PropertyKey.UUID,
               PropertyKey.CAMERA,
               PropertyKey.BINNING,
               PropertyKey.ROI,
               PropertyKey.BIT_DEPTH,
               PropertyKey.EXPOSURE_MS,
               PropertyKey.ELAPSED_TIME_MS,
               PropertyKey.IMAGE_NUMBER,
               PropertyKey.RECEIVED_TIME,
               PropertyKey.PIXEL_SIZE_UM,
               PropertyKey.PIXEL_ASPECT,
               PropertyKey.POSITION_NAME,
               PropertyKey.X_POSITION_UM,
               PropertyKey.Y_POSITION_UM,
               PropertyKey.Z_POSITION_UM,
               PropertyKey.SCOPE_DATA,
               PropertyKey.USER_DATA))
         {
            key.storeInGsonObject(pmap, jo);
         }
         return jo;
      }
   }

   private static final class SummaryFormat extends NonPropertyMapJSONFormats {
      @Override
      public PropertyMap fromGson(JsonElement je) {
         PropertyMap.Builder builder = PropertyMaps.builder();
         for (PropertyKey key : ImmutableList.of(
               PropertyKey.PREFIX,
               PropertyKey.USER_NAME,
               PropertyKey.PROFILE_NAME,
               PropertyKey.MICRO_MANAGER_VERSION,
               PropertyKey.METADATA_VERSION,
               PropertyKey.COMPUTER_NAME,
               PropertyKey.DIRECTORY,
               PropertyKey.CHANNEL_GROUP,
               PropertyKey.CHANNEL_NAMES,
               PropertyKey.Z_STEP_UM,
               PropertyKey.INTERVAL_MS,
               PropertyKey.CUSTOM_INTERVALS_MS,
               PropertyKey.AXIS_ORDER,
               PropertyKey.INTENDED_DIMENSIONS,
               PropertyKey.START_TIME,
               PropertyKey.STAGE_POSITIONS,
               PropertyKey.KEEP_SHUTTER_OPEN_SLICES,
               PropertyKey.KEEP_SHUTTER_OPEN_CHANNELS,
               PropertyKey.USER_DATA))
         {
            key.extractFromGsonObject(je.getAsJsonObject(), builder);
         }
         return builder.build();
      }

      @Override
      public JsonElement toGson(PropertyMap pmap) {
         JsonObject jo = new JsonObject();
         for (PropertyKey key : ImmutableList.of(
               PropertyKey.PREFIX,
               PropertyKey.USER_NAME,
               PropertyKey.PROFILE_NAME,
               PropertyKey.MICRO_MANAGER_VERSION,
               PropertyKey.METADATA_VERSION,
               PropertyKey.COMPUTER_NAME,
               PropertyKey.DIRECTORY,
               PropertyKey.CHANNEL_GROUP,
               PropertyKey.CHANNEL_NAMES,
               PropertyKey.Z_STEP_UM,
               PropertyKey.INTERVAL_MS,
               PropertyKey.CUSTOM_INTERVALS_MS,
               PropertyKey.AXIS_ORDER,
               PropertyKey.TIME_FIRST, // compat
               PropertyKey.SLICES_FIRST, // compat
               PropertyKey.INTENDED_DIMENSIONS,
               PropertyKey.FRAMES, // compat
               PropertyKey.POSITIONS, // compat
               PropertyKey.SLICES, // compat
               PropertyKey.CHANNELS, // compat
               PropertyKey.START_TIME,
               PropertyKey.STAGE_POSITIONS,
               PropertyKey.KEEP_SHUTTER_OPEN_SLICES,
               PropertyKey.KEEP_SHUTTER_OPEN_CHANNELS,
               PropertyKey.USER_DATA))
         {
            key.storeInGsonObject(pmap, jo);
         }
         return jo;
      }
   }

   /**
    * PositionList JSON format.
    *
    * There are multiple variants of this:
    * <ul>
    * <li>A JSON object saved separately
    * <li>A JSON array embedded in SummaryMetadata "InitialPositionList" (1.4)
    * <li>A JSON array embedded in SummaryMetadata STAGE_POSITIONS (2.x)
    * </ul>
    */
   private static final class PositionListFormat extends NonPropertyMapJSONFormats {
      @Override
      public PropertyMap fromGson(JsonElement je) {
         PropertyMap.Builder builder = PropertyMaps.builder();
         for (PropertyKey key : ImmutableList.of(
               PropertyKey.POSITION_LIST__ID,
               PropertyKey.POSITION_LIST__VERSION,
               PropertyKey.STAGE_POSITIONS))
         {
            key.extractFromGsonObject(je.getAsJsonObject(), builder);
         }
         return builder.build();
      }
   }

   private static final class MultiStagePositionFormat extends NonPropertyMapJSONFormats {
      @Override
      public PropertyMap fromGson(JsonElement je) {
         PropertyMap.Builder builder = PropertyMaps.builder();
         for (PropertyKey key : ImmutableList.of(
               PropertyKey.MULTI_STAGE_POSITION__LABEL,
               PropertyKey.MULTI_STAGE_POSITION__DEFAULT_XY_STAGE,
               PropertyKey.MULTI_STAGE_POSITION__DEFAULT_Z_STAGE,
               PropertyKey.MULTI_STAGE_POSITION__GRID_ROW,
               PropertyKey.MULTI_STAGE_POSITION__GRID_COLUMN,
               PropertyKey.MULTI_STAGE_POSITION__PROPERTIES,
               PropertyKey.MULTI_STAGE_POSITION__DEVICE_POSITIONS))
         {
            key.extractFromGsonObject(je.getAsJsonObject(), builder);
         }
         return builder.build();
      }

      @Override
      public JsonElement toGson(PropertyMap pmap) {
         JsonObject jo = new JsonObject();
         for (PropertyKey key : ImmutableList.of(
               PropertyKey.MULTI_STAGE_POSITION__LABEL,
               PropertyKey.MULTI_STAGE_POSITION__DEFAULT_XY_STAGE,
               PropertyKey.MULTI_STAGE_POSITION__DEFAULT_Z_STAGE,
               PropertyKey.MULTI_STAGE_POSITION__GRID_ROW,
               PropertyKey.MULTI_STAGE_POSITION__GRID_COLUMN,
               PropertyKey.MULTI_STAGE_POSITION__PROPERTIES,
               PropertyKey.MULTI_STAGE_POSITION__DEVICE_POSITIONS))
         {
            key.storeInGsonObject(pmap, jo);
         }
         return jo;
      }
   }

   /**
    * The problematic x-y-z-as-first-and-second-axes format.
    */
   private static final class StagePosition extends NonPropertyMapJSONFormats {
      @Override
      public PropertyMap fromGson(JsonElement je) {
         PropertyMap.Builder builder = PropertyMaps.builder();
         for (PropertyKey key : ImmutableList.of(
               PropertyKey.STAGE_POSITION__DEVICE,
               PropertyKey.STAGE_POSITION__NUMAXES,
               PropertyKey.STAGE_POSITION__COORD1_UM,
               PropertyKey.STAGE_POSITION__COORD2_UM,
               PropertyKey.STAGE_POSITION__COORD3_UM))
         {
            key.extractFromGsonObject(je.getAsJsonObject(), builder);
         }
         return builder.build();
      }
   }

   private static final class CoordsFormat extends NonPropertyMapJSONFormats {
      @Override
      public PropertyMap fromGson(JsonElement je) {
         // Note that the property map format for Coords uses axis names as
         // keys, not the canonical MetadataKey keys.
         PropertyMap.Builder builder = PropertyMaps.builder();
         PropertyKey.COMPLETE_COORDS.
               extractFromGsonObject(je.getAsJsonObject(), builder);
         return builder.build();
      }

      @Override
      public JsonElement toGson(PropertyMap pmap) {
         JsonObject jo = new JsonObject();
         for (PropertyKey key : ImmutableList.of(
               PropertyKey.COMPLETE_COORDS,
               PropertyKey.FRAME_INDEX,
               PropertyKey.POSITION_INDEX,
               PropertyKey.SLICE_INDEX,
               PropertyKey.CHANNEL_INDEX))
         {
            key.storeInGsonObject(pmap, jo);
         }
         return jo;
      }
   }

   private static final class ImageFormat extends NonPropertyMapJSONFormats {
      @Override
      public PropertyMap fromGson(JsonElement je) {
         PropertyMap.Builder builder = PropertyMaps.builder();
         for (PropertyKey key : ImmutableList.of(
               PropertyKey.WIDTH,
               PropertyKey.HEIGHT,
               PropertyKey.PIXEL_TYPE))
         {
            key.extractFromGsonObject(je.getAsJsonObject(), builder);
         }
         return builder.build();
      }

      @Override
      public JsonElement toGson(PropertyMap pmap) {
         JsonObject jo = new JsonObject();
         for (PropertyKey key : ImmutableList.of(
               PropertyKey.WIDTH,
               PropertyKey.HEIGHT,
               PropertyKey.PIXEL_TYPE))
         {
            key.storeInGsonObject(pmap, jo);
         }
         return jo;
      }
   }

   private static final class Annotation extends NonPropertyMapJSONFormats {
      @Override
      public PropertyMap fromGson(JsonElement je) {
      }
   }

   private static final class DisplaySettings extends NonPropertyMapJSONFormats {
      @Override
      public PropertyMap fromGson(JsonElement je) {
      }
   }
}