package org.micromanager.internal.propertymap;

import com.google.common.collect.ImmutableList;
import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.data.internal.PropertyKey;
import org.micromanager.internal.MMStudio;

import java.io.IOException;
import java.io.StringReader;

import static org.micromanager.data.internal.PropertyKey.*;

/**
 * High-level format conversion between MM1-style JSON and modern property maps.
 *
 * @see MM1JSONSerializer
 * @author Mark A. Tsuchida
 */
public abstract class NonPropertyMapJSONFormats {
  private static final NonPropertyMapJSONFormats METADATA_INSTANCE = new MetadataFormat();
  private static final NonPropertyMapJSONFormats SUMMARY_INSTANCE = new SummaryFormat();
  private static final NonPropertyMapJSONFormats POSITION_LIST_INSTANCE = new PositionListFormat();
  private static final NonPropertyMapJSONFormats MSP_INSTANCE = new MultiStagePositionFormat();
  private static final NonPropertyMapJSONFormats MSDP_Instance =
      new MultiStageDevicePositionFormat();
  private static final NonPropertyMapJSONFormats OLD_MSP_INSTANCE = new StagePosition();
  private static final NonPropertyMapJSONFormats COORDS_INSTANCE = new CoordsFormat();
  private static final NonPropertyMapJSONFormats IMAGE_FORMAT_INSTANCE = new ImageFormat();

  public static NonPropertyMapJSONFormats metadata() {
    return METADATA_INSTANCE;
  }

  public static NonPropertyMapJSONFormats summaryMetadata() {
    return SUMMARY_INSTANCE;
  }

  public static NonPropertyMapJSONFormats positionList() {
    return POSITION_LIST_INSTANCE;
  }

  public static NonPropertyMapJSONFormats multiStagePosition() {
    return MSP_INSTANCE;
  }

  public static NonPropertyMapJSONFormats multiStageDevicePosition() {
    return MSDP_Instance;
  }

  public static NonPropertyMapJSONFormats oldStagePosition() {
    return OLD_MSP_INSTANCE;
  }

  public static NonPropertyMapJSONFormats coords() {
    return COORDS_INSTANCE;
  }

  public static NonPropertyMapJSONFormats imageFormat() {
    return IMAGE_FORMAT_INSTANCE;
  }

  @SuppressWarnings("UseSpecificCatch")
  public final PropertyMap fromJSON(String json) throws IOException {
    try {
      JsonReader reader = new JsonReader(new StringReader(json));
      reader.setLenient(true);
      JsonParser parser = new JsonParser();
      return fromGson(parser.parse(reader).getAsJsonObject());
    } catch (Exception e) {
      throw new IOException("Invalid data", e);
    }
  }

  public final String toJSON(PropertyMap canonical) {
    Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    return gson.toJson(toGson(canonical));
  }

  public abstract PropertyMap fromGson(JsonElement je);

  public final JsonElement toGson(PropertyMap pmap) {
    JsonObject jo = new JsonObject();
    addToGson(jo, pmap);
    return jo;
  }

  public void addToGson(JsonObject jo, PropertyMap pmap) {
    throw new UnsupportedOperationException(
        getClass().getSimpleName()
            + "should be written as standard PropertyMap JSON, not MM1-style JSON");
  }

  private static final class MetadataFormat extends NonPropertyMapJSONFormats {
    @Override
    public PropertyMap fromGson(JsonElement je) {
      PropertyMap.Builder builder = PropertyMaps.builder();
      for (PropertyKey key :
          ImmutableList.of(
              PropertyKey.UUID,
              CAMERA,
              BINNING,
              ROI,
              BIT_DEPTH,
              EXPOSURE_MS,
              ELAPSED_TIME_MS,
              IMAGE_NUMBER,
              RECEIVED_TIME,
              PIXEL_SIZE_UM,
              PIXEL_SIZE_AFFINE,
              PIXEL_ASPECT,
              POSITION_NAME,
              X_POSITION_UM,
              Y_POSITION_UM,
              Z_POSITION_UM,
              PIXEL_TYPE, // Needed due to MultipageTiffReader design
              SCOPE_DATA,
              USER_DATA,
              FILE_NAME)) {
        key.extractFromGsonObject(je.getAsJsonObject(), builder);
      }
      return builder.build();
    }

    @Override
    public void addToGson(JsonObject jo, PropertyMap pmap) {
      for (PropertyKey key :
          ImmutableList.of(
              PropertyKey.UUID,
              CAMERA,
              BINNING,
              ROI,
              BIT_DEPTH,
              EXPOSURE_MS,
              ELAPSED_TIME_MS,
              IMAGE_NUMBER,
              RECEIVED_TIME,
              PIXEL_SIZE_UM,
              PIXEL_SIZE_AFFINE,
              PIXEL_ASPECT,
              POSITION_NAME,
              X_POSITION_UM,
              Y_POSITION_UM,
              Z_POSITION_UM,
              SCOPE_DATA,
              SCOPE_DATA_KEYS,
              USER_DATA,
              FILE_NAME)) {
        try {
          key.storeInGsonObject(pmap, jo);
        } catch (NullPointerException npe) {
          MMStudio.getInstance().logs().logError("Null Pointer for Key: " + key);
        }
      }
    }
  }

  private static final class SummaryFormat extends NonPropertyMapJSONFormats {
    @Override
    public PropertyMap fromGson(JsonElement je) {
      PropertyMap.Builder builder = PropertyMaps.builder();
      for (PropertyKey key :
          ImmutableList.of(
              PREFIX,
              USER_NAME,
              PROFILE_NAME,
              MICRO_MANAGER_VERSION,
              METADATA_VERSION,
              COMPUTER_NAME,
              DIRECTORY,
              CHANNEL_GROUP,
              CHANNEL_NAMES,
              Z_STEP_UM,
              INTERVAL_MS,
              CUSTOM_INTERVALS_MS,
              AXIS_ORDER,
              INTENDED_DIMENSIONS,
              START_TIME,
              STAGE_POSITIONS,
              KEEP_SHUTTER_OPEN_SLICES,
              KEEP_SHUTTER_OPEN_CHANNELS,
              PIXEL_TYPE, // Needed due to MultipageTiffReader design
              USER_DATA)) {
        key.extractFromGsonObject(je.getAsJsonObject(), builder);
      }
      return builder.build();
    }

    @Override
    public void addToGson(JsonObject jo, PropertyMap pmap) {
      for (PropertyKey key :
          ImmutableList.of(
              PREFIX,
              USER_NAME,
              PROFILE_NAME,
              MICRO_MANAGER_VERSION,
              METADATA_VERSION,
              COMPUTER_NAME,
              DIRECTORY,
              CHANNEL_GROUP,
              CHANNEL_NAMES,
              Z_STEP_UM,
              INTERVAL_MS,
              CUSTOM_INTERVALS_MS,
              AXIS_ORDER,
              TIME_FIRST, // compat
              SLICES_FIRST, // compat
              INTENDED_DIMENSIONS,
              FRAMES, // compat
              POSITIONS, // compat
              SLICES, // compat
              CHANNELS, // compat
              START_TIME,
              STAGE_POSITIONS,
              KEEP_SHUTTER_OPEN_SLICES,
              KEEP_SHUTTER_OPEN_CHANNELS,
              PIXEL_TYPE, // compat
              WIDTH, // compat
              HEIGHT, // compat
              USER_DATA)) {
        try {
          key.storeInGsonObject(pmap, jo);
        } catch (NullPointerException npe) {
          MMStudio.getInstance().logs().logError(npe, "Key: " + key);
        } catch (UnsupportedOperationException uoe) {
          MMStudio.getInstance().logs().logError(uoe, "Key: " + key);
        }
      }
    }
  }

  /**
   * PositionList JSON format.
   *
   * <p>There are multiple variants of this:
   *
   * <ul>
   *   <li>A JSON object saved separately
   *   <li>A JSON array embedded in SummaryMetadata "InitialPositionList" (1.4)
   *   <li>A JSON array embedded in SummaryMetadata STAGE_POSITIONS (2.x)
   * </ul>
   */
  private static final class PositionListFormat extends NonPropertyMapJSONFormats {
    @Override
    public PropertyMap fromGson(JsonElement je) {
      PropertyMap.Builder builder = PropertyMaps.builder();
      for (PropertyKey key :
          ImmutableList.of(POSITION_LIST__ID, POSITION_LIST__VERSION, STAGE_POSITIONS)) {
        key.extractFromGsonObject(je.getAsJsonObject(), builder);
      }
      return builder.build();
    }
  }

  private static final class MultiStagePositionFormat extends NonPropertyMapJSONFormats {
    @Override
    public PropertyMap fromGson(JsonElement je) {
      PropertyMap.Builder builder = PropertyMaps.builder();
      for (PropertyKey key :
          ImmutableList.of(
              MULTI_STAGE_POSITION__LABEL,
              MULTI_STAGE_POSITION__DEFAULT_XY_STAGE,
              MULTI_STAGE_POSITION__DEFAULT_Z_STAGE,
              MULTI_STAGE_POSITION__GRID_ROW,
              MULTI_STAGE_POSITION__GRID_COLUMN,
              MULTI_STAGE_POSITION__PROPERTIES,
              MULTI_STAGE_POSITION__DEVICE_POSITIONS)) {
        key.extractFromGsonObject(je.getAsJsonObject(), builder);
      }
      return builder.build();
    }

    @Override
    public void addToGson(JsonObject jo, PropertyMap pmap) {
      for (PropertyKey key :
          ImmutableList.of(
              MULTI_STAGE_POSITION__LABEL,
              MULTI_STAGE_POSITION__DEFAULT_XY_STAGE,
              MULTI_STAGE_POSITION__DEFAULT_Z_STAGE,
              MULTI_STAGE_POSITION__GRID_ROW,
              MULTI_STAGE_POSITION__GRID_COLUMN,
              MULTI_STAGE_POSITION__PROPERTIES,
              MULTI_STAGE_POSITION__DEVICE_POSITIONS)) {
        key.storeInGsonObject(pmap, jo);
      }
    }
  }

  private static final class MultiStageDevicePositionFormat extends NonPropertyMapJSONFormats {
    @Override
    public PropertyMap fromGson(JsonElement je) {
      PropertyMap.Builder builder = PropertyMaps.builder();
      for (PropertyKey key :
          ImmutableList.of(STAGE_POSITION__DEVICE, STAGE_POSITION__POSITION_UM)) {
        key.extractFromGsonObject(je.getAsJsonObject(), builder);
      }
      return builder.build();
    }

    @Override
    public void addToGson(JsonObject jo, PropertyMap pmap) {
      for (PropertyKey key :
          ImmutableList.of(STAGE_POSITION__DEVICE, STAGE_POSITION__POSITION_UM)) {
        key.storeInGsonObject(pmap, jo);
      }
    }
  }

  /** The problematic x-y-z-as-first-and-second-axes format. */
  private static final class StagePosition extends NonPropertyMapJSONFormats {
    @Override
    public PropertyMap fromGson(JsonElement je) {
      PropertyMap.Builder builder = PropertyMaps.builder();
      for (PropertyKey key :
          ImmutableList.of(
              STAGE_POSITION__DEVICE,
              STAGE_POSITION__NUMAXES,
              STAGE_POSITION__COORD1_UM,
              STAGE_POSITION__COORD2_UM,
              STAGE_POSITION__COORD3_UM)) {
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
      COMPLETE_COORDS.extractFromGsonObject(je.getAsJsonObject(), builder);
      return builder.build();
    }

    @Override
    public void addToGson(JsonObject jo, PropertyMap pmap) {
      for (PropertyKey key :
          ImmutableList.of(
              COMPLETE_COORDS, FRAME_INDEX, POSITION_INDEX, SLICE_INDEX, CHANNEL_INDEX)) {
        key.storeInGsonObject(pmap, jo);
      }
    }
  }

  private static final class ImageFormat extends NonPropertyMapJSONFormats {
    @Override
    public PropertyMap fromGson(JsonElement je) {
      PropertyMap.Builder builder = PropertyMaps.builder();
      for (PropertyKey key : ImmutableList.of(WIDTH, HEIGHT, PIXEL_TYPE)) {
        key.extractFromGsonObject(je.getAsJsonObject(), builder);
      }
      return builder.build();
    }

    @Override
    public void addToGson(JsonObject jo, PropertyMap pmap) {
      for (PropertyKey key : ImmutableList.of(WIDTH, HEIGHT, PIXEL_TYPE)) {
        key.storeInGsonObject(pmap, jo);
      }
    }
  }

  private static final class Annotation extends NonPropertyMapJSONFormats {
    @Override
    public PropertyMap fromGson(JsonElement je) {
      // TODO parse 2.0beta format
      return PropertyMaps.emptyPropertyMap();
    }
  }

  private static final class DisplaySettings extends NonPropertyMapJSONFormats {
    @Override
    public PropertyMap fromGson(JsonElement je) {
      // TODO parse 1.4 and 2.0beta formats
      return PropertyMaps.emptyPropertyMap();
    }
  }
}
