/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.internal.propertymap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMap.Builder;
import org.micromanager.PropertyMaps;

/** @author mark */
public final class PropertyMapJSONSerializer {
  private PropertyMapJSONSerializer() {}

  // Gson-serializable
  private static class VersionedMap {
    String encoding; // Always 'UTF-8'; included only to aid posterity
    String format; // Always 'Micro-Manager Property Map'
    int major_version;
    int minor_version;
    PropertyMap map;

    VersionedMap() {}

    VersionedMap(PropertyMap propertyMap) {
      encoding = "UTF-8";
      format = "Micro-Manager Property Map";

      // The first version is 2.0 (1.0 refers to the "legacy" format used
      // during 2.0 beta).
      //
      // The minor version should be incremented when a backward-compatible
      // change is made, such as addition of a new value type.
      // The major version should be incremented when an incompatible change
      // is made to the format (should be avoided as much as possible).
      //
      // Note that the version is of the property map container format and
      // is completely unrelated to the specific keys stored within. When
      // the contents require versioning, they should be given their own
      // version.
      major_version = 2;
      minor_version = 0;

      map = propertyMap;
    }
  }

  // Gson-serializable
  private static class TypeAndValue {
    ValueType type;
    Object scalar; // scalar or List; serialized/deserialized by ValueType
    List<?> array;

    TypeAndValue() {}

    TypeAndValue(ValueType type, Object value) {
      this.type = type;
      if (value instanceof List<?>) {
        this.array = (List<?>) value;
      } else {
        this.scalar = value;
      }
    }

    void construct(Builder builder, String key) {
      if (array != null) {
        type.constructArray(builder, key, (List<?>) array);
      } else {
        type.construct(builder, key, scalar);
      }
    }
  }

  private static final class Keys {
    private static final String TYPE = "type";
    private static final String SCALAR = "scalar";
    private static final String ARRAY = "array";
  }

  // Define the mappint to/from JSON
  // Enum values (STRING, ...) define the value of the 'type' field
  // All type-specific bits are defined here!
  private enum ValueType {
    BOOLEAN {
      @Override
      JsonElement serialize(Object value, JsonSerializationContext context) {
        return new JsonPrimitive((Boolean) value);
      }

      @Override
      Boolean deserialize(JsonElement je, JsonDeserializationContext context) {
        return je.getAsBoolean();
      }

      @Override
      Class<Boolean> getScalarClass() {
        return boolean.class;
      }

      @Override
      Boolean extractValue(PropertyMap map, String key) {
        return map.getBoolean(key, false);
      }

      @Override
      List<Boolean> extractArray(PropertyMap map, String key) {
        return map.getBooleanList(key, (List<Boolean>) null);
      }

      @Override
      void construct(Builder builder, String key, Object value) {
        builder.putBoolean(key, ((Boolean) value).booleanValue());
      }

      @Override
      void constructArray(Builder builder, String key, List<?> values) {
        builder.putBooleanList(key, (List<Boolean>) values);
      }
    },

    BYTE {
      @Override
      JsonElement serialize(Object value, JsonSerializationContext context) {
        return new JsonPrimitive((Byte) value);
      }

      @Override
      Byte deserialize(JsonElement je, JsonDeserializationContext context) {
        return je.getAsByte();
      }

      @Override
      Class<Byte> getScalarClass() {
        return byte.class;
      }

      @Override
      Byte extractValue(PropertyMap map, String key) {
        return map.getByte(key, (byte) 0);
      }

      @Override
      List<Byte> extractArray(PropertyMap map, String key) {
        return map.getByteList(key, (List<Byte>) null);
      }

      @Override
      void construct(Builder builder, String key, Object value) {
        builder.putByte(key, (Byte) value);
      }

      @Override
      void constructArray(Builder builder, String key, List<?> values) {
        builder.putByteList(key, (List<Byte>) values);
      }
    },

    SHORT {
      @Override
      JsonElement serialize(Object value, JsonSerializationContext context) {
        return new JsonPrimitive((Short) value);
      }

      @Override
      Short deserialize(JsonElement je, JsonDeserializationContext context) {
        return je.getAsShort();
      }

      @Override
      Class<Short> getScalarClass() {
        return short.class;
      }

      @Override
      Short extractValue(PropertyMap map, String key) {
        return map.getShort(key, (short) 0);
      }

      @Override
      List<Short> extractArray(PropertyMap map, String key) {
        return map.getShortList(key, (List<Short>) null);
      }

      @Override
      void construct(Builder builder, String key, Object value) {
        builder.putShort(key, (Short) value);
      }

      @Override
      void constructArray(Builder builder, String key, List<?> values) {
        builder.putShortList(key, (List<Short>) values);
      }
    },

    INTEGER {
      @Override
      JsonElement serialize(Object value, JsonSerializationContext context) {
        return new JsonPrimitive((Integer) value);
      }

      @Override
      Integer deserialize(JsonElement je, JsonDeserializationContext context) {
        return je.getAsInt();
      }

      @Override
      Class<Integer> getScalarClass() {
        return int.class;
      }

      @Override
      Object extractValue(PropertyMap map, String key) {
        return map.getInteger(key, 0);
      }

      @Override
      List<?> extractArray(PropertyMap map, String key) {
        return map.getIntegerList(key, (List<Integer>) null);
      }

      @Override
      void construct(Builder builder, String key, Object value) {
        builder.putInteger(key, ((Integer) value).intValue());
      }

      @Override
      void constructArray(Builder builder, String key, List<?> values) {
        builder.putIntegerList(key, (List<Integer>) values);
      }
    },

    LONG {
      @Override
      JsonElement serialize(Object value, JsonSerializationContext context) {
        return new JsonPrimitive((Long) value);
      }

      @Override
      Object deserialize(JsonElement je, JsonDeserializationContext context) {
        return je.getAsLong();
      }

      @Override
      Class<?> getScalarClass() {
        return long.class;
      }

      @Override
      Object extractValue(PropertyMap map, String key) {
        return map.getLong(key, 0L);
      }

      @Override
      List<?> extractArray(PropertyMap map, String key) {
        return map.getLongList(key, (List<Long>) null);
      }

      @Override
      void construct(Builder builder, String key, Object value) {
        builder.putLong(key, ((Long) value).longValue());
      }

      @Override
      void constructArray(Builder builder, String key, List<?> values) {
        builder.putLongList(key, (List<Long>) values);
      }
    },

    FLOAT {
      @Override
      JsonElement serialize(Object value, JsonSerializationContext context) {
        return new JsonPrimitive((Float) value);
      }

      @Override
      Object deserialize(JsonElement je, JsonDeserializationContext context) {
        return je.getAsFloat();
      }

      @Override
      Class<?> getScalarClass() {
        return float.class;
      }

      @Override
      Object extractValue(PropertyMap map, String key) {
        return map.getFloat(key, 0.0f);
      }

      @Override
      List<?> extractArray(PropertyMap map, String key) {
        return map.getFloatList(key, (List<Float>) null);
      }

      @Override
      void construct(Builder builder, String key, Object value) {
        builder.putFloat(key, (Float) value);
      }

      @Override
      void constructArray(Builder builder, String key, List<?> values) {
        builder.putFloatList(key, (List<Float>) values);
      }
    },

    DOUBLE {
      @Override
      JsonElement serialize(Object value, JsonSerializationContext context) {
        return new JsonPrimitive((Double) value);
      }

      @Override
      Object deserialize(JsonElement je, JsonDeserializationContext context) {
        return je.getAsDouble();
      }

      @Override
      Class<?> getScalarClass() {
        return double.class;
      }

      @Override
      Object extractValue(PropertyMap map, String key) {
        return map.getDouble(key, 0.0);
      }

      @Override
      List<?> extractArray(PropertyMap map, String key) {
        return map.getDoubleList(key, (List<Double>) null);
      }

      @Override
      void construct(Builder builder, String key, Object value) {
        builder.putDouble(key, ((Double) value).doubleValue());
      }

      @Override
      void constructArray(Builder builder, String key, List<?> values) {
        builder.putDoubleList(key, (List<Double>) values);
      }
    },

    STRING {
      @Override
      JsonElement serialize(Object value, JsonSerializationContext context) {
        return new JsonPrimitive((String) value);
      }

      @Override
      String deserialize(JsonElement je, JsonDeserializationContext context) {
        return je.getAsString();
      }

      @Override
      Class<String> getScalarClass() {
        return String.class;
      }

      @Override
      String extractValue(PropertyMap map, String key) {
        return map.getString(key, null);
      }

      @Override
      List<String> extractArray(PropertyMap map, String key) {
        return map.getStringList(key, (List<String>) null);
      }

      @Override
      void construct(Builder builder, String key, Object value) {
        builder.putString(key, (String) value);
      }

      @Override
      void constructArray(Builder builder, String key, List<?> values) {
        builder.putStringList(key, (List<String>) values);
      }
    },

    UUID {
      @Override
      JsonElement serialize(Object value, JsonSerializationContext context) {
        return new JsonPrimitive(((java.util.UUID) value).toString());
      }

      @Override
      Object deserialize(JsonElement je, JsonDeserializationContext context) {
        return java.util.UUID.fromString(je.getAsString());
      }

      @Override
      Class<?> getScalarClass() {
        return java.util.UUID.class;
      }

      @Override
      Object extractValue(PropertyMap map, String key) {
        return map.getUUID(key, null);
      }

      @Override
      List<?> extractArray(PropertyMap map, String key) {
        return map.getUUIDList(key, (List<java.util.UUID>) null);
      }

      @Override
      void construct(Builder builder, String key, Object value) {
        builder.putUUID(key, (java.util.UUID) value);
      }

      @Override
      void constructArray(Builder builder, String key, List<?> values) {
        builder.putUUIDList(key, (List<java.util.UUID>) values);
      }
    },

    COLOR {
      // Color, if RGB(A), could be directly serialized, but not if it has
      // an attached ICC_Profile. Keep it simple and only save sRGB and
      // alpha (at this time we don't need alpha but it can't hurt).
      @Override
      JsonElement serialize(Object value, JsonSerializationContext context) {
        JsonObject jo = new JsonObject();
        jo.add("ColorSpace", new JsonPrimitive("sRGB"));
        JsonArray ja = new JsonArray();
        for (float c : ((Color) value).getRGBColorComponents(null)) {
          ja.add(new JsonPrimitive(c));
        }
        jo.add("Components", ja);
        jo.add("Alpha", new JsonPrimitive(((Color) value).getRGBComponents(null)[3]));
        return jo;
      }

      @Override
      Object deserialize(JsonElement je, JsonDeserializationContext context) {
        JsonObject jo = je.getAsJsonObject();
        if (!"sRGB".equals(jo.get("ColorSpace").getAsString())) {
          throw new JsonParseException("Unsupported color space");
        }
        JsonArray components = jo.get("Components").getAsJsonArray();
        return new Color(
            components.get(0).getAsFloat(),
            components.get(1).getAsFloat(),
            components.get(2).getAsFloat(),
            jo.get("Alpha").getAsFloat());
      }

      @Override
      Class<?> getScalarClass() {
        return Color.class;
      }

      @Override
      Object extractValue(PropertyMap map, String key) {
        return map.getColor(key, null);
      }

      @Override
      List<?> extractArray(PropertyMap map, String key) {
        return map.getColorList(key, (List<Color>) null);
      }

      @Override
      void construct(Builder builder, String key, Object value) {
        builder.putColor(key, (Color) value);
      }

      @Override
      void constructArray(Builder builder, String key, List<?> values) {
        builder.putColorList(key, (List<Color>) values);
      }
    },

    AFFINE_TRANSFORM {
      @Override
      JsonElement serialize(Object value, JsonSerializationContext context) {
        JsonArray ja = new JsonArray();
        double[] matrix = new double[6];
        ((AffineTransform) value).getMatrix(matrix);
        for (double a : matrix) {
          ja.add(new JsonPrimitive(a));
        }
        return ja;
      }

      @Override
      Object deserialize(JsonElement je, JsonDeserializationContext context) {
        JsonArray ja = je.getAsJsonArray();
        double[] matrix = new double[6];
        for (int i = 0; i < 6; ++i) {
          matrix[i] = ja.get(i).getAsDouble();
        }
        return new AffineTransform(matrix);
      }

      @Override
      Class<?> getScalarClass() {
        return AffineTransform.class;
      }

      @Override
      Object extractValue(PropertyMap map, String key) {
        return map.getAffineTransform(key, null);
      }

      @Override
      List<?> extractArray(PropertyMap map, String key) {
        return map.getAffineTransformList(key, (List<AffineTransform>) null);
      }

      @Override
      void construct(Builder builder, String key, Object value) {
        builder.putAffineTransform(key, (AffineTransform) value);
      }

      @Override
      void constructArray(Builder builder, String key, List<?> values) {
        builder.putAffineTransformList(key, (List<AffineTransform>) values);
      }
    },

    PROPERTY_MAP {
      @Override
      JsonElement serialize(Object value, JsonSerializationContext context) {
        return context.serialize(value);
      }

      @Override
      Object deserialize(JsonElement je, JsonDeserializationContext context) {
        return context.deserialize(je, PropertyMap.class);
      }

      @Override
      Class<?> getScalarClass() {
        return PropertyMap.class;
      }

      @Override
      Object extractValue(PropertyMap map, String key) {
        return map.getPropertyMap(key, null);
      }

      @Override
      List<?> extractArray(PropertyMap map, String key) {
        return map.getPropertyMapList(key, (List<PropertyMap>) null);
      }

      @Override
      void construct(Builder builder, String key, Object value) {
        builder.putPropertyMap(key, (PropertyMap) value);
      }

      @Override
      void constructArray(Builder builder, String key, List<?> values) {
        builder.putPropertyMapList(key, (List<PropertyMap>) values);
      }
    },

    RECTANGLE {
      @Override
      JsonElement serialize(Object value, JsonSerializationContext context) {
        Rectangle rect = (Rectangle) value;
        JsonObject jo = new JsonObject();
        jo.addProperty("x", rect.x);
        jo.addProperty("y", rect.y);
        jo.addProperty("width", rect.width);
        jo.addProperty("height", rect.height);
        return jo;
      }

      @Override
      Object deserialize(JsonElement je, JsonDeserializationContext context) {
        JsonObject jo = je.getAsJsonObject();
        return new Rectangle(
            jo.get("x").getAsInt(),
            jo.get("y").getAsInt(),
            jo.get("width").getAsInt(),
            jo.get("height").getAsInt());
      }

      @Override
      Class<?> getScalarClass() {
        return Rectangle.class;
      }

      @Override
      Object extractValue(PropertyMap map, String key) {
        return map.getRectangle(key, null);
      }

      @Override
      List<?> extractArray(PropertyMap map, String key) {
        return map.getRectangleList(key, (List<Rectangle>) null);
      }

      @Override
      void construct(Builder builder, String key, Object value) {
        builder.putRectangle(key, (Rectangle) value);
      }

      @Override
      void constructArray(Builder builder, String key, List<?> values) {
        builder.putRectangleList(key, (List<Rectangle>) values);
      }
    },

    DIMENSION {
      @Override
      JsonElement serialize(Object value, JsonSerializationContext context) {
        Dimension dim = (Dimension) value;
        JsonObject jo = new JsonObject();
        jo.addProperty("width", dim.width);
        jo.addProperty("height", dim.height);
        return jo;
      }

      @Override
      Object deserialize(JsonElement je, JsonDeserializationContext context) {
        JsonObject jo = je.getAsJsonObject();
        return new Dimension(jo.get("width").getAsInt(), jo.get("height").getAsInt());
      }

      @Override
      Class<?> getScalarClass() {
        return Dimension.class;
      }

      @Override
      Object extractValue(PropertyMap map, String key) {
        return map.getDimension(key, null);
      }

      @Override
      List<?> extractArray(PropertyMap map, String key) {
        return map.getDimensionList(key, (List<Dimension>) null);
      }

      @Override
      void construct(Builder builder, String key, Object value) {
        builder.putDimension(key, (Dimension) value);
      }

      @Override
      void constructArray(Builder builder, String key, List<?> values) {
        builder.putDimensionList(key, (List<Dimension>) values);
      }
    },

    POINT {
      @Override
      JsonElement serialize(Object value, JsonSerializationContext context) {
        Point point = (Point) value;
        JsonObject jo = new JsonObject();
        jo.addProperty("x", point.x);
        jo.addProperty("y", point.y);
        return jo;
      }

      @Override
      Object deserialize(JsonElement je, JsonDeserializationContext context) {
        JsonObject jo = je.getAsJsonObject();
        return new Point(jo.get("x").getAsInt(), jo.get("y").getAsInt());
      }

      @Override
      Class<?> getScalarClass() {
        return Point.class;
      }

      @Override
      Object extractValue(PropertyMap map, String key) {
        return map.getPoint(key, null);
      }

      @Override
      List<?> extractArray(PropertyMap map, String key) {
        return map.getPointList(key, (List<Point>) null);
      }

      @Override
      void construct(Builder builder, String key, Object value) {
        builder.putPoint(key, (Point) value);
      }

      @Override
      void constructArray(Builder builder, String key, List<?> values) {
        builder.putPointList(key, (List<Point>) values);
      }
    },
    ;

    abstract JsonElement serialize(Object value, JsonSerializationContext context);

    abstract Object deserialize(JsonElement je, JsonDeserializationContext context);

    abstract Class<?> getScalarClass();

    Class<?> getArrayClass() {
      return Array.newInstance(getScalarClass(), 0).getClass();
    }

    abstract Object extractValue(PropertyMap map, String key);

    abstract List<?> extractArray(PropertyMap map, String key);

    abstract void construct(Builder builder, String key, Object value);

    abstract void constructArray(Builder builder, String key, List<?> values);
  }

  private static class ValueSerDes
      implements JsonSerializer<TypeAndValue>, JsonDeserializer<TypeAndValue> {
    @Override
    public JsonElement serialize(TypeAndValue t, Type type, JsonSerializationContext context) {
      JsonObject ret = new JsonObject();
      ret.addProperty(Keys.TYPE, t.type.name());
      if (t.array != null) {
        JsonArray ja = new JsonArray();
        for (Object value : (List<?>) t.array) {
          ja.add(t.type.serialize(value, context));
        }
        ret.add(Keys.ARRAY, ja);
      } else {
        ret.add(Keys.SCALAR, t.type.serialize(t.scalar, context));
      }
      return ret;
    }

    @Override
    public TypeAndValue deserialize(JsonElement je, Type type, JsonDeserializationContext context)
        throws JsonParseException {
      TypeAndValue tv = new TypeAndValue();
      try {
        JsonObject jo = je.getAsJsonObject();
        tv.type = ValueType.valueOf(jo.get(Keys.TYPE).getAsString());
        if (jo.get(Keys.SCALAR) == null || jo.get(Keys.SCALAR).isJsonNull()) {
          List<Object> values = new ArrayList<Object>();
          for (JsonElement ae : jo.get(Keys.ARRAY).getAsJsonArray()) {
            values.add(tv.type.deserialize(ae, context));
          }
          tv.array = values;
        } else {
          tv.scalar = tv.type.deserialize(jo.get(Keys.SCALAR), context);
        }
      } catch (JsonParseException e) {
        throw e;
      } catch (Exception e) {
        throw new JsonParseException(e);
      }
      return tv;
    }
  }

  private static class PropertyMapSerDes
      implements JsonSerializer<PropertyMap>, JsonDeserializer<PropertyMap> {
    @Override
    public JsonElement serialize(PropertyMap t, Type type, JsonSerializationContext context) {
      JsonObject jo = new JsonObject();
      for (Map.Entry<String, TypeAndValue> e : extractValuesAndTypes(t)) {
        jo.add(e.getKey(), context.serialize(e.getValue()));
      }
      return jo;
    }

    @Override
    public PropertyMap deserialize(
        JsonElement json, Type typeOfT, JsonDeserializationContext context)
        throws JsonParseException {
      Builder builder = PropertyMaps.builder();
      JsonObject jo = json.getAsJsonObject();
      for (Map.Entry<String, JsonElement> e : jo.entrySet()) {
        String key = e.getKey();
        JsonElement je = e.getValue();
        TypeAndValue tv = context.deserialize(je, TypeAndValue.class);
        tv.construct(builder, key);
      }
      return builder.build();
    }
  }

  // Convert property map to list of key-TypeAndValue pairs
  private static Iterable<Map.Entry<String, TypeAndValue>> extractValuesAndTypes(PropertyMap map) {
    List<Map.Entry<String, TypeAndValue>> ret = new ArrayList<Map.Entry<String, TypeAndValue>>();
    for (String key : map.keySet()) {
      for (ValueType t : ValueType.values()) {
        if (t.getScalarClass().isAssignableFrom(map.getValueTypeForKey(key))) {
          ret.add(new AbstractMap.SimpleEntry(key, new TypeAndValue(t, t.extractValue(map, key))));
          break;
        } else if (t.getArrayClass().isAssignableFrom(map.getValueTypeForKey(key))) {
          ret.add(new AbstractMap.SimpleEntry(key, new TypeAndValue(t, t.extractArray(map, key))));
          break;
        }
      }
    }

    // For readability only
    Collections.sort(
        ret,
        new Comparator<Map.Entry<String, TypeAndValue>>() {
          @Override
          public int compare(
              Map.Entry<String, TypeAndValue> o1, Map.Entry<String, TypeAndValue> o2) {
            return o1.getKey().compareTo(o2.getKey());
          }
        });

    return ret;
  }

  private static Gson makeGson() {
    return new GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .serializeSpecialFloatingPointValues()
        .registerTypeAdapter(TypeAndValue.class, new ValueSerDes())
        .registerTypeHierarchyAdapter(PropertyMap.class, new PropertyMapSerDes())
        .create();
  }

  /**
   * Deserialize from a Gson JsonElement.
   *
   * <p>This method can be used when a modern property map is embedded within a larger JSON data
   * stream.
   *
   * <p>Only the modern format is supported; PropertyMap "1.0" will fail.
   *
   * <p><strong>Warning:</strong> Throws various unchecked exceptions for format errors.
   *
   * @param je
   * @return
   */
  public static PropertyMap fromGson(JsonElement je) {
    return makeGson().fromJson(je, PropertyMap.class);
  }

  private static PropertyMap fromJSONImpl(String json) throws IOException {
    // The legacy format was not real JSON; instead it contained the version
    // number ("1.0") in the first line.
    final String LEGACY_VERSION = "1.0\n";
    if (json.startsWith(LEGACY_VERSION)) {
      json = json.substring(LEGACY_VERSION.length());
    }

    VersionedMap data = makeGson().fromJson(json, VersionedMap.class);
    VersionedMap template = new VersionedMap(null);
    if (data == null
        || !template.format.equals(data.format)
        || !template.encoding.equals(data.encoding)) {
      // We can be pretty sure that any legacy JSON will satisfy the above
      // condition, although the opposite is not generally true.
      try {
        return LegacyPropertyMap1Deserializer.fromJSON(json);
      } catch (Exception e) {
        throw new IOException(
            "Invalid property map format; attempted to interpret as legacy format but that didn't work either",
            e);
      }
    } else if (data.major_version > template.major_version) {
      throw new IOException(
          "Properties are saved in a newer format that is incompatible with this version of the application.");
    } else if (data.major_version < 2) {
      // Never used with this way of versioning
      throw new IOException("Invalid property map format");
    } else if (data.major_version < template.major_version) {
      // When template.major_version becomes >2, conversion from old format
      // should be added here.
    }
    if (data.minor_version > template.major_version) {
      // TODO We could in this case silently accept unknown value types
      // (and somehow preserve them in the returned property map so that
      // they can be resaved).
    }
    return data.map;
  }

  public static PropertyMap fromJSON(String json) throws IOException {
    try {
      return fromJSONImpl(json);
    } catch (IOException e) {
      throw e;
    } catch (Exception e) {
      throw new IOException(e);
    }
  }

  public static JsonElement toGson(PropertyMap map) {
    return makeGson().toJsonTree(map);
  }

  public static String toJSON(PropertyMap map) {
    VersionedMap vmap = new VersionedMap(map);
    return makeGson().toJson(vmap);
  }
}
