package org.micromanager;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * An immutable typed key-value store for various settings.
 * <p>
 * A property map can store string-keyed values of various primitive types,
 * nested property maps, and uniformly typed (ordered) collections of values.
 * Because it is immutable, it can be passed around between objects without
 * worrying about  thread safety. Null values cannot be stored.
 * <p>
 * To create a PropertyMap, use {@link PropertyMaps#builder()}.
 * <p>
 * Property maps can be converted to a specific JSON format and back in a
 * type-preserving manner.
 * <p>
 * Property maps are similar in concept to {@link java.util.Properties} but
 * stores typed values rather than just strings, and supports storage of nested
 * property maps.
 * <p>
 * <strong>Methods to access primitive types</strong><br>
 * <pre><code>
 * PropertyMap pm = ...
 * pm.containsLong("key"); // - true if "key" exists and type is long
 * pm.getLong("key", 0L);  // - 0L if "key" is missing
 *                         // Throws ClassCastException if value is wrong type
 * pm.containsLongList("key2"); // - true if "key2" exists and type is
 *                           // collection of longs
 * pm.getLongList("key2")       // - long[] {} if "key2" is missing
 *                           // Throws ClassCastException if value is wrong type
 * pm.getLongList("key2", 0L, 1L, 2L) // - long[] { 0, 1, 2 } if "key2" is missing
 * pm.getLongList("key2", new ArrayList{@literal <}Long{@literal >}());
 *                           // Return List{@literal <}Long{@literal >}, or
 *                           // the empty arraylist
 * pm.getLongList("key2", null); // - (List{@literal <}Long{@literal >}) null if "key2" missing
 * </code></pre>
 * Note that, for the collections (plural) get methods, the returned value is
 * an array ({@code long[]} if the default value was given as an array or varargs,
 * whereas it is a list ({@code List<long>}) if the
 * default value was given as any collection or iterable.
 * The same pattern applies to {@code boolean}, {@code byte}, {@code short},
 * {@code int}, {@code long}, {@code float}, and {@code double}. (No {@code
 * char} or {@code void}, this is intentional.)
 * <p>
 * <strong>Methods to access primitive types</strong><br>
 * <pre><code>
 * pm.containsString("key3");
 * pm.getString("key3", "foo");
 * pm.containsStringList("key4");
 * // The following both return List{@literal <}String{@literal >}:
 * pm.getStringList("key4", "a", "b", "c"); // Default = List of "a", "b", "c"
 * pm.getStringList("key4", listOfStrings);
 * </code></pre>
 * This differs from the case of primitive types in that the collection (plural)
 * get methods return a {@code List} regardless of the type of the default value.
 * The same pattern applies to {@code String}, {@code Color}, {@code
 * AffineTransform}, {@code Rectangle}, {@code Dimension}, {@code Point}, and
 * nested {@code PropertyMap}.
 * <p>
 * <strong>Methods to access enum types</strong><br>
 * Enum values can be stored by automatically converting to {@code String}.
 * See {@link #containsStringForEnum}, {@link #containsStringListForEnumList},
 * {@link #getStringAsEnum}, and {@link #getStringListAsEnumList}. This is useful for
 * storing multiple-choice settings
 * <p>
 * For how to insert values of the various types, see {@link PropertyMap.Builder}.
 */
public interface PropertyMap {
   /**
    * A value that can be stored in a property map.
    *
    * This is only used for interchange purposes, for example when performing
    * bulk operations on property maps.
    */
   interface OpaqueValue {
      /**
       * 
       * @return 
       */
      Class<?> getValueType();
   }

   /**
    * Builder for {@code PropertyMap}.
    * <p>
    * See {@link PropertyMaps#builder()} for a usage example.
    */
   interface Builder extends PropertyMapBuilder {
      // Note: we could conceivably have a single 'put()' method with
      // overloaded parameter types. We avoid this, because code using property
      // maps generally should be conscious of the type choice. The type will
      // need to be known when getting the value out of the map, so it is best
      // to get the compiler to enforce the type when building.

      // MAINTAINER NOTE: These methods should be kept in sync with those of
      // UserProfile. Also note that LegacyMM2BetaUserProfileDeserializer (and
      // possibly others) determine method names reflectively according to a
      // strict pattern.

      // Primitive
      @Override
      Builder putBoolean(String key, Boolean value);
      Builder putBooleanList(String key, boolean... values);
      Builder putBooleanList(String key, Iterable<Boolean> values);
      Builder putByte(String key, Byte value);
      Builder putByteList(String key, byte... values);
      Builder putByteList(String key, Iterable<Byte> values);
      Builder putShort(String key, Short value);
      Builder putShortList(String key, short... values);
      Builder putShortList(String key, Iterable<Short> values);
      Builder putInteger(String key, Integer value);
      Builder putIntegerList(String key, int... values);
      Builder putIntegerList(String key, Iterable<Integer> values);
      @Override
      Builder putLong(String key, Long value);
      Builder putLongList(String key, long ... values);
      Builder putLongList(String key, Iterable<Long> values);
      Builder putFloat(String key, Float value);
      Builder putFloatList(String key, float... values);
      Builder putFloatList(String key, Iterable<Float> values);
      @Override
      Builder putDouble(String key, Double value);
      Builder putDoubleList(String key, double... values);
      Builder putDoubleList(String key, Iterable<Double> values);

      // Immutable
      @Override
      Builder putString(String key, String value);
      Builder putStringList(String key, String... values);
      Builder putStringList(String key, Iterable<String> values);
      Builder putUUID(String key, UUID value);
      Builder putUUIDList(String key, UUID... values);
      Builder putUUIDList(String key, Iterable<UUID> values);
      Builder putColor(String key, Color value);
      Builder putColorList(String key, Color... values);
      Builder putColorList(String key, Iterable<Color> values);
      Builder putAffineTransform(String key, AffineTransform value);
      Builder putAffineTransformList(String key, AffineTransform... values);
      Builder putAffineTransformList(String key, Iterable<AffineTransform> values);
      @Override
      Builder putPropertyMap(String key, PropertyMap value);
      Builder putPropertyMapList(String key, PropertyMap... values);
      Builder putPropertyMapList(String key, Iterable<PropertyMap> values);

      // TODO Java 8 java.time.ZonedDateTime and LocalDateTime

      // Mutable
      Builder putRectangle(String key, Rectangle value);
      Builder putRectangleList(String key, Rectangle... values);
      Builder putRectangleList(String key, Iterable<Rectangle> values);
      Builder putDimension(String key, Dimension value);
      Builder putDimensionList(String key, Dimension... values);
      Builder putDimensionList(String key, Iterable<Dimension> values);
      Builder putPoint(String key, Point value);
      Builder putPointList(String key, Point... values);
      Builder putPointList(String key, Iterable<Point> values);

      // Enums-as-strings
      <E extends Enum<E>> Builder putEnumAsString(String key, E value);
      <E extends Enum<E>> Builder putEnumListAsStringList(String key, E... values);
      <E extends Enum<E>> Builder putEnumListAsStringList(String key, Iterable<E> values);

      Builder putOpaqueValue(String key, OpaqueValue value);

      Builder putAll(PropertyMap map);
      Builder clear();
      Builder remove(String key);
      Builder removeAll(Collection<?> keys);
      Builder retainAll(Collection<?> keys);

      @Override
      PropertyMap build();


      // Deprecated methods, repeated here to add javadoc

      /** @deprecated Use {@link #putStringList} instead. */
      @Deprecated
      @Override
      PropertyMapBuilder putStringArray(String key, String[] values);
      /** @deprecated Use {@link #putInteger(java.lang.String, java.lang.Integer)} instead. */
      @Deprecated
      @Override
      PropertyMapBuilder putInt(String key, Integer value);
      /** @deprecated Use {@link #putIntegerList} instead. */
      @Deprecated
      @Override
      PropertyMapBuilder putIntArray(String key, Integer[] values);
      /** @deprecated Use {@link #putLongList} instead. */
      @Deprecated
      @Override
      PropertyMapBuilder putLongArray(String key, Long[] values);
      /** @deprecated Use {@link #putDoubleList} instead. */
      @Deprecated
      @Override
      PropertyMapBuilder putDoubleArray(String key, Double[] values);
      /** @deprecated Use {@link #putBooleanList} instead. */
      @Deprecated
      @Override
      PropertyMapBuilder putBooleanArray(String key, Boolean[] values);
   }

   // Note: These methods from 2.0beta should be kept until 2018 and then
   // deleted.
   // putInt, putLong, etc. will then switch to potentially using autoboxing;
   // that can throw NPEs but presumably such usage is rare in user code,
   // where it is likely that autounboxing was rather taking place.
   @Deprecated
   interface PropertyMapBuilder {
      @Deprecated
      PropertyMap build();
      @Deprecated
      PropertyMapBuilder putString(String key, String value);
      @Deprecated
      PropertyMapBuilder putStringArray(String key, String[] values);
      @Deprecated
      PropertyMapBuilder putInt(String key, Integer value);
      @Deprecated
      PropertyMapBuilder putIntArray(String key, Integer[] values);
      @Deprecated
      PropertyMapBuilder putLong(String key, Long value);
      @Deprecated
      PropertyMapBuilder putLongArray(String key, Long[] values);
      @Deprecated
      PropertyMapBuilder putDouble(String key, Double value);
      @Deprecated
      PropertyMapBuilder putDoubleArray(String key, Double[] values);
      @Deprecated
      PropertyMapBuilder putBoolean(String key, Boolean value);
      @Deprecated
      PropertyMapBuilder putBooleanArray(String key, Boolean[] values);
      @Deprecated
      PropertyMapBuilder putPropertyMap(String key, PropertyMap values);
   }

   Builder copyBuilder();

   // MAINTAINER NOTE: These methods should be kept in sync with those of
   // MutablePropertyMapView.

   /**
    * Return an unmodifiable set containing all of the keys.
    * @return the set of keys
    */
   Set<String> keySet();
   boolean containsKey(String key);
   boolean containsAll(Collection<?> keys);
   boolean isEmpty();
   int size();

   /**
    * Return a class indicating the type of the value for the given key.
    * <p>
    * This method is intended for special code that handles serialization or
    * conversion of property maps. In most cases, code that uses PropertyMap
    * as a container for settings or data should use a fixed, pre-determined
    * type for each key.
    * <p>
    * For object (non-primitive) types, the class is returned (e.g. {@code
    * String.class}, {@code PropertyMap.class}).
    * <p>
    * For primitive types, the primitive type class is returned (e.g. {@code
    * int.class}, {@code float.class}). Note that {@code int.class !=
    * Integer.class}.
    * <p>
    * For collection (plural) types, the corresponding array type is returned
    * (e.g. {@code int[].class}, {@code String[].class}).
    * <p>
    * Keys stored as "EnumAsString" return {@code String.class}.
    *
    * @param key
    * @return the class representing the type of the value for key
    * @throws IllegalArgumentException if {@code key} is not found
    */
   Class<?> getValueTypeForKey(String key);

   OpaqueValue getAsOpaqueValue(String key);
   String getValueAsString(String key, String aDefault);

   //
   // Primitive
   //

   boolean containsBoolean(String key);
   boolean getBoolean(String key, boolean aDefault);
   boolean containsBooleanList(String key);
   boolean[] getBooleanList(String key, boolean... defaults);
   List<Boolean> getBooleanList(String key, Iterable<Boolean> defaults);

   boolean containsByte(String key);
   byte getByte(String key, byte aDefault);
   boolean containsByteList(String key);
   byte[] getByteList(String key, byte... defaults);
   List<Byte> getByteList(String key, Iterable<Byte> defaults);

   boolean containsShort(String key);
   short getShort(String key, short aDefault);
   boolean containsShortList(String key);
   short[] getShortList(String key, short... defaults);
   List<Short> getShortList(String key, Iterable<Short> defaults);

   boolean containsInteger(String key);
   int getInteger(String key, int aDefault);
   boolean containsIntegerList(String key);
   int[] getIntegerList(String key, int... defaults);
   List<Integer> getIntegerList(String key, Iterable<Integer> defaults);

   boolean containsLong(String key);
   long getLong(String key, long aDefault);
   boolean containsLongList(String key);
   long[] getLongList(String key, long ... defaults);
   List<Long> getLongList(String key, Iterable<Long> defaults);

   boolean containsFloat(String key);
   float getFloat(String key, float aDefault);
   boolean containsFloatList(String key);
   float[] getFloatList(String key, float... defaults);
   List<Float> getFloatList(String key, Iterable<Float> defaults);

   boolean containsDouble(String key);
   double getDouble(String key, double aDefault);
   boolean containsDoubleList(String key);
   double[] getDoubleList(String key, double... defaults);
   List<Double> getDoubleList(String key, Iterable<Double> defaults);

   //
   // Type-unspecific number access
   //

   // TODO Add these to UserProfile:

   boolean containsNumber(String key);

   /**
    * Retrieve a numerical value, without checking its specific type.
    * <p>
    * This method may be useful for providing backward compatibility (for
    * example, if a value currently stored as a {@code long} used to be stored
    * as an {@code int}). It should not be used when the type-specific get
    * method can be used.
    * <p>
    * Code that calls this method should be prepared to handle any of the
    * following types correctly: {@code Byte, Short, Integer, Long, Float,
    * Double}.
    *
    * @param key
    * @param aDefault
    * @return
    */
   Number getAsNumber(String key, Number aDefault);

   boolean containsNumberList(String key);

   List<Number> getAsNumberList(String key, Number... defaults);
   List<Number> getAsNumberList(String key, Iterable<Number> defaults);

   //
   // Immutable
   //

   boolean containsString(String key);
   String getString(String key, String aDefault);
   boolean containsStringList(String key);
   List<String> getStringList(String key, String... defaults);
   List<String> getStringList(String key, Iterable<String> defaults);

   boolean containsUUID(String key);
   UUID getUUID(String key, UUID aDefault);
   boolean containsUUIDList(String key);
   List<UUID> getUUIDList(String key, UUID... defaults);
   List<UUID> getUUIDList(String key, Iterable<UUID> defaults);

   boolean containsColor(String key);
   Color getColor(String key, Color aDefault);
   boolean containsColorList(String key);
   List<Color> getColorList(String key, Color... defaults);
   List<Color> getColorList(String key, Iterable<Color> defaults);

   boolean containsAffineTransform(String key);
   AffineTransform getAffineTransform(String key, AffineTransform aDefault);
   boolean containsAffineTransformList(String key);
   List<AffineTransform> getAffineTransformList(String key, AffineTransform... defaults);
   List<AffineTransform> getAffineTransformList(String key, Iterable<AffineTransform> defaults);

   boolean containsPropertyMap(String key);
   PropertyMap getPropertyMap(String key, PropertyMap aDefault);
   boolean containsPropertyMapList(String key);
   List<PropertyMap> getPropertyMapList(String key, PropertyMap... defaults);
   List<PropertyMap> getPropertyMapList(String key, Iterable<PropertyMap> defaults);

   //
   // Mutable
   //

   boolean containsRectangle(String key);
   Rectangle getRectangle(String key, Rectangle aDefault);
   boolean containsRectangleList(String key);
   List<Rectangle> getRectangleList(String key, Rectangle... defaults);
   List<Rectangle> getRectangleList(String key, Iterable<Rectangle> defaults);

   boolean containsDimension(String key);
   Dimension getDimension(String key, Dimension aDefault);
   boolean containsDimensionList(String key);
   List<Dimension> getDimensionList(String key, Dimension... defaults);
   List<Dimension> getDimensionList(String key, Iterable<Dimension> defaults);

   boolean containsPoint(String key);
   Point getPoint(String key, Point aDefault);
   boolean containsPointList(String key);
   List<Point> getPointList(String key, Point... defaults);
   List<Point> getPointList(String key, Iterable<Point> defaults);

   //
   // Enum-as-string
   //

   <E extends Enum<E>> boolean containsStringForEnum(String key, Class<E> enumType);

   /**
    * Get a string value, converted to an enum value.
    * <p>
    * The property map does not record the specific class of enum values. It is
    * the caller's responsibility to specify the correct enum class.
    * <p>
    * If the value stored in the property map is not one of the allowed values
    * for {@code enumType}, {@code aDefault} will be returned.
    *
    * @param <E> the enum class
    * @param key the property key
    * @param enumType the enum class
    * @param aDefault a default value to return if the key is missing or
    * the stored value is an enum but not a known value for the given enum
    * class.
    * @return the enum value for {@code key}
    */
   <E extends Enum<E>> E getStringAsEnum(String key, Class<E> enumType, E aDefault);

   <E extends Enum<E>> boolean containsStringListForEnumList(String key, Class<E> enumType);

   <E extends Enum<E>> List<E> getStringListAsEnumList(String key, Class<E> enumType, E... defaults);

   /**
    * Get a collection of strings, converted to a collection of enum values.
    * <p>
    * The property map does not record the specific class of enum values. It is
    * the caller's responsibility to specify the correct enum class.
    * <p>
    * Unless all of the values stored in the property map are allowed values of
    * {@code enumType}, {@code defaults} will be returned.
    *
    * @param <E> the enum class
    * @param key the property key
    * @param enumType the enum class
    * @param defaults a default collection
    * @return the list of enum values for {@code key}
    */
   <E extends Enum<E>> List<E> getStringListAsEnumList(String key, Class<E> enumType, Iterable<E> defaults);


   /**
    * Create a JSON representation of this property map.
    * <p>
    * To create a property map back from the JSON representation, see {@link
    * PropertyMaps#fromJSON}.
    *
    * @return the JSON-serialized contents
    */
   String toJSON();

   /**
    * Save to a file as JSON.
    * <p>
    * To create a property map back from the saved file, see {@link
    * PropertyMaps#loadJSON}.
    *
    * @param file the file to write to
    * @param overwrite if false and file exists, don't write and return false
    * @param createBackup if true and overwriting, first rename the existing
    * file by appending "~" to its name
    * @return true if the file was successfully written
    * @throws IOException if there was an error writing to the file
    */
   boolean saveJSON(File file, boolean overwrite, boolean createBackup) throws IOException;


   //
   // Deprecated old methods (cumbersome to use correctly due to boxed types)
   //

   @Deprecated
   public PropertyMapBuilder copy();

   @Deprecated
   public String getString(String key);
   @Deprecated
   public String[] getStringArray(String key);
   @Deprecated
   public String[] getStringArray(String key, String[] aDefault);
   @Deprecated
   public Integer getInt(String key);
   @Deprecated
   public Integer getInt(String key, Integer aDefault);
   @Deprecated
   public Integer[] getIntArray(String key);
   @Deprecated
   public Integer[] getIntArray(String key, Integer[] aDefault);
   @Deprecated
   public Long getLong(String key);
   @Deprecated
   public Long getLong(String key, Long aDefault);
   @Deprecated
   public Long[] getLongArray(String key);
   @Deprecated
   public Long[] getLongArray(String key, Long[] aDefault);
   @Deprecated
   public Double getDouble(String key);
   @Deprecated
   public Double getDouble(String key, Double aDefault);
   @Deprecated
   public Double[] getDoubleArray(String key);
   @Deprecated
   public Double[] getDoubleArray(String key, Double[] aDefault);
   @Deprecated
   public Boolean getBoolean(String key);
   @Deprecated
   public Boolean getBoolean(String key, Boolean aDefault);
   @Deprecated
   public Boolean[] getBooleanArray(String key);
   @Deprecated
   public Boolean[] getBooleanArray(String key, Boolean[] aDefault);
   @Deprecated
   public PropertyMap getPropertyMap(String key);

   @Deprecated
   public PropertyMap merge(PropertyMap alt);

   @Deprecated
   public Set<String> getKeys();

   @Deprecated
   public Class getPropertyType(String key);

   @Deprecated
   public void save(String path) throws IOException;

   /**
    * @deprecated If necessary, catch ClassCastException instead.
    */
   @Deprecated
   class TypeMismatchException extends ClassCastException {
      /**
       * @param desc
       * @deprecated Constructor should not have been part of API.
       */
      @Deprecated
      public TypeMismatchException(String desc) {
         super(desc);
      }
   }
}