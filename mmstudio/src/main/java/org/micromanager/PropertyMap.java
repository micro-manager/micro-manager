package org.micromanager;

import java.io.IOException;
import java.util.Set;

/**
 * This interface is used for storing custom information in the Metadata,
 * SummaryMetadata, and DisplaySettings classes (in org.micromanager.data and
 * org.micromanager.display), and for passing information between plugins and
 * in similar situations. It presents a simple key/value mapping that can store
 * basic types, arrays of basic types, and generic objects.
 * New versions can be instantiated via org.micromanager.data.DataManager or
 * org.micromanager.display.DisplayManager.
 *
 * This class uses a Builder pattern. Please see
 * https://micro-manager.org/wiki/Using_Builders
 * for more information.
 */
public interface PropertyMap {
   /**
    * This exception is thrown when an incorrect get* method of a PropertyMap
    * is called. For example, if the key "foo" maps to a String value, and you
    * call PropertyMap.getInt("foo") instead of PropertyMap.getString("foo"),
    * then this exception will be thrown.
    */
   class TypeMismatchException extends RuntimeException {
      public TypeMismatchException(String desc) {
         super(desc);
      }
   }

   interface PropertyMapBuilder {
      /**
       * Construct a PropertyMap from the Builder. Call this once the
       * builder's values are all set.
       * @return a new PropertyMap based on the values of the builder.
       */
      PropertyMap build();

      /**
       * Put a new String value into the mapping.
       * @param key a string identifying this property. If there is already
       *        a property with this key in the builder, that property will
       *        be overwritten.
       * @param value the value to associate with the key.
       * @return The PropertyMapBuilder, so that puts can be chained together
       */
      PropertyMapBuilder putString(String key, String value);
      /**
       * Put an array of Strings into the mapping.
       * @param key a string identifying this property. If there is already
       *        a property with this key in the builder, that property will
       *        be overwritten.
       * @param values values to associate with the key.
       * @return The PropertyMapBuilder, so that puts can be chained together
       */
      PropertyMapBuilder putStringArray(String key, String[] values);
     
      /**
       * Put a new Integer value into the mapping.
       * @param key a string identifying this property. If there is already
       *        a property with this key in the builder, that property will
       *        be overwritten.
       * @param value the value to associate with the key.
       * @return The PropertyMapBuilder, so that puts can be chained together
       */
      PropertyMapBuilder putInt(String key, Integer value);
      /**
       * Put an array of Integers into the mapping.
       * @param key a string identifying this property. If there is already
       *        a property with this key in the builder, that property will
       *        be overwritten.
       * @param values values to associate with the key.
       * @return The PropertyMapBuilder, so that puts can be chained together
       */
      PropertyMapBuilder putIntArray(String key, Integer[] values);

      /**
       * Put a new Long value into the mapping.
       * @param key a string identifying this property. If there is already
       *        a property with this key in the builder, that property will
       *        be overwritten.
       * @param value the value to associate with the key.
       * @return The PropertyMapBuilder, so that puts can be chained together
       */
      PropertyMapBuilder putLong(String key, Long value);
      /**
       * Put an array of Longs into the mapping.
       * @param key a string identifying this property. If there is already
       *        a property with this key in the builder, that property will
       *        be overwritten.
       * @param values values to associate with the key.
       * @return The PropertyMapBuilder, so that puts can be chained together
       */
      PropertyMapBuilder putLongArray(String key, Long[] values);

      /**
       * Put a new Double value into the mapping.
       * @param key a string identifying this property. If there is already
       *        a property with this key in the builder, that property will
       *        be overwritten.
       * @param value the value to associate with the key.
       * @return The PropertyMapBuilder, so that puts can be chained together
       */
      PropertyMapBuilder putDouble(String key, Double value);
      /**
       * Put an array of Doubles into the mapping.
       * @param key a string identifying this property. If there is already
       *        a property with this key in the builder, that property will
       *        be overwritten.
       * @param values values to associate with the key.
       * @return The PropertyMapBuilder, so that puts can be chained together
       */
      PropertyMapBuilder putDoubleArray(String key, Double[] values);

      /**
       * Put a new Boolean value into the mapping.
       * @param key a string identifying this property. If there is already
       *        a property with this key in the builder, that property will
       *        be overwritten.
       * @param value the value to associate with the key.
       * @return The PropertyMapBuilder, so that puts can be chained together
       */
      PropertyMapBuilder putBoolean(String key, Boolean value);
      /**
       * Put an array of Booleans into the mapping.
       * @param key a string identifying this property. If there is already
       *        a property with this key in the builder, that property will
       *        be overwritten.
       * @param values values to associate with the key.
       * @return The PropertyMapBuilder, so that puts can be chained together
       */
      PropertyMapBuilder putBooleanArray(String key, Boolean[] values);

      /**
       * Put a PropertyMap into the mapping. Be careful not to make
       * recursive PropertyMaps (i.e. put a PropertyMap into itself) as this
       * will lead to errors when saving the PropertyMap.
       * @param key a string identifying this property. If there is already
       *        a property with this key in the builder, that property will
       *        be overwritten.
       * @param values values to associate with the key.
       * @return The PropertyMapBuilder, so that puts can be chained together
       */
      PropertyMapBuilder putPropertyMap(String key, PropertyMap values);

      /**
       * Put a generic object into the mapping. The object will be internally
       * converted into a byte array and serialized with a base64 encoding.
       * @param key a string identifying this property. If there is already
       *        a property with this key in the builder, that property will
       *        be overwritten.
       * @param value Object to serialize and associate with the key.
       * @return The PropertyMapBuilder, so that puts can be chained together
       */
      PropertyMapBuilder putObject(String key, Object value);
   }

   /**
    * Construct a PropertyMap.PropertyMapBuilder that can be used to create a
    * modified version of this PropertyMap instance.
    * @return a PropertyMapBuilder whose properties match the current state of
    *         this PropertyMap.
    */
   public PropertyMapBuilder copy();

   /**
    * Retrieve a String value from the mapping. Will return null if the key is
    * not found. If the mapped value is not a String, a TypeMismatchException
    * will be thrown.
    * @param key
    * @return the String corresponding to the specified key.
    */
   public String getString(String key);
   /**
    * Retrieve a String value from the mapping. If the key is not found, then
    * the provided default value will be returned. If the mapped value is not a
    * String, a TypeMismatchException will be thrown.
    * @param key
    * @param defaultVal the default value to use if the key is not found.
    * @return the String corresponding to the specified key, or defaultVal
    *         if the key is not found.
    */
   public String getString(String key, String defaultVal);
   /**
    * Retrieve a String array from the mapping. Will return null if the key is
    * not found. If the mapped value is not a String[], a TypeMismatchException
    * will be thrown.
    * @param key
    * @return the String array corresponding to the specified key.
    */
   public String[] getStringArray(String key);
   /**
    * Retrieve a String array from the mapping. If the key is not found, then
    * the provided default value will be returned. If the mapped value is not a
    * String[], a TypeMismatchException will be thrown.
    * @param key
    * @param defaultVal the default value to use if the key is not found.
    * @return the String array corresponding to the specified key, or
    *         defaultVal if the key is not found.
    */
   public String[] getStringArray(String key, String[] defaultVal);

   /**
    * Retrieve an Integer value from the mapping. Will return null if the key
    * is not found. If the mapped value is not an Integer, a
    * TypeMismatchException will be thrown.
    * @param key
    * @return The Integer corresponding to the provided key.
    */
   public Integer getInt(String key);
   /**
    * Retrieve an Integer value from the mapping. If the key is not found, then
    * the provided default value will be returned. If the mapped value is
    * not an Integer, a TypeMismatchException will be thrown.
    * @param key
    * @param defaultVal the default value to use if the key is not found.
    * @return The Integer corresponding to the provided key, or defaultVal
    *         if the key is not found.
    */
   public Integer getInt(String key, Integer defaultVal);
   /**
    * Retrieve an Integer array from the mapping. Will return null if the key
    * is not found. If the mapped value is not an Integer[], a
    * TypeMismatchException will be thrown.
    * @param key
    * @return The Integer[] corresponding to the provided key.
    */
   public Integer[] getIntArray(String key);
   /**
    * Retrieve an Integer array from the mapping. If the key is not found, then
    * the provided default value will be returned. If the mapped value is
    * not an Integer[], a TypeMismatchException will be thrown.
    * @param key
    * @param defaultVal the default value to use if the key is not found.
    * @return The Integer[] corresponding to the provided key, or defaultVal
    *         if the key is not found.
    */
   public Integer[] getIntArray(String key, Integer[] defaultVal);

   /**
    * Retrieve a Long value from the mapping. Will return null if the key
    * is not found. If the mapped value is not an Long, a TypeMismatchException
    * will be thrown.
    * @param key
    * @return The Long corresponding to the provided key.
    */
   public Long getLong(String key);
   /**
    * Retrieve a Long value from the mapping. If the key is not found, then
    * the provided default value will be returned. If the mapped value is
    * not an Long, a TypeMismatchException will be thrown.
    * @param key
    * @param defaultVal the default value to use if the key is not found.
    * @return The Long corresponding to the provided key, or defaultVal
    *         if the key is not found.
    */
   public Long getLong(String key, Long defaultVal);
   /**
    * Retrieve a Long array from the mapping. Will return null if the key
    * is not found. If the mapped value is not an Long, a TypeMismatchException
    * will be thrown.
    * @param key
    * @return The Long[] corresponding to the provided key.
    */
   public Long[] getLongArray(String key);
   /**
    * Retrieve a Long array from the mapping. If the key is not found, then
    * the provided default value will be returned. If the mapped value is
    * not an Long, a TypeMismatchException will be thrown.
    * @param key
    * @param defaultVal the default value to use if the key is not found.
    * @return The Long[] corresponding to the provided key, or defaultVal
    *         if the key is not found.
    */
   public Long[] getLongArray(String key, Long[] defaultVal);

   /**
    * Retrieve a Double value from the mapping. Will return null if the key
    * is not found. If the mapped value is not a Double, a
    * TypeMismatchException will be thrown.
    * @param key
    * @return The Double corresponding to the provided key.
    */
   public Double getDouble(String key);
   /**
    * Retrieve a Double value from the mapping. If the key is not found, then
    * the provided default value will be returned. If the mapped value is
    * not a Double, a TypeMismatchException will be thrown.
    * @param key
    * @param defaultVal the default value to use if the key is not found.
    * @return The Double corresponding to the provided key, or defaultVal
    *         if the key is not found.
    */
   public Double getDouble(String key, Double defaultVal);
   /**
    * Retrieve a Double array from the mapping. Will return null if the key
    * is not found. If the mapped value is not an Double, a
    * TypeMismatchException will be thrown.
    * @param key
    * @return The Double[] corresponding to the provided key.
    */
   public Double[] getDoubleArray(String key);
   /**
    * Retrieve a Double array from the mapping. If the key is not found, then
    * the provided default value will be returned. If the mapped value is
    * not an Double, a TypeMismatchException will be thrown.
    * @param key
    * @param defaultVal the default value to use if the key is not found.
    * @return The Double[] corresponding to the provided key, or defaultVal
    *         if the key is not found.
    */
   public Double[] getDoubleArray(String key, Double[] defaultVal);

   /**
    * Retrieve a Boolean value from the mapping. Will return null if the key
    * is not found. If the mapped value is not a Boolean, a
    * TypeMismatchException will be thrown.
    * @param key
    * @return The Boolean corresponding to the provided key.
    */
   public Boolean getBoolean(String key);
   /**
    * Retrieve a Boolean value from the mapping. If the key is not found, then
    * the provided default value will be returned. If the mapped value is
    * not a Boolean, a TypeMismatchException will be thrown.
    * @param key
    * @param defaultVal the default value to use if the key is not found.
    * @return The Boolean corresponding to the provided key, or defaultVal
    *         if the key is not found.
    */
   public Boolean getBoolean(String key, Boolean defaultVal);
   /**
    * Retrieve a Boolean array from the mapping. Will return null if the key
    * is not found. If the mapped value is not an Boolean, a
    * TypeMismatchException will be thrown.
    * @param key
    * @return The Boolean[] corresponding to the provided key.
    */
   public Boolean[] getBooleanArray(String key);
   /**
    * Retrieve a Boolean array from the mapping. If the key is not found, then
    * the provided default value will be returned. If the mapped value is
    * not an Boolean, a TypeMismatchException will be thrown.
    * @param key
    * @param defaultVal the default value to use if the key is not found.
    * @return The Boolean[] corresponding to the provided key, or defaultVal
    *         if the key is not found.
    */
   public Boolean[] getBooleanArray(String key, Boolean[] defaultVal);

   /**
    * Retrieve a PropertyMap value from the mapping. Will return null if the
    * key is not found. If the mapped value is not a PropertyMap, a
    * TypeMismatchException will be thrown.
    * @param key
    * @return The PropertyMap corresponding to the provided key.
    */
   public PropertyMap getPropertyMap(String key);
   /**
    * Retrieve a PropertyMap value from the mapping. If the key is not found,
    * then the provided default value will be returned. If the mapped value is
    * not a PropertyMap, a TypeMismatchException will be thrown.
    * @param key
    * @param defaultVal the default value to use if the key is not found.
    * @return The PropertyMap corresponding to the provided key, or defaultVal
    *         if the key is not found.
    */
   public PropertyMap getPropertyMap(String key, PropertyMap defaultVal);

   /**
    * Retrieve an object from the mapping. If the key is not found, then the
    * provided default value will be returned. If the mapped value does not
    * have the specified type, then a TypeMismatchException will be thrown.
    * @param key
    * @param defaultVal the default vaule to use if the key is not found.
    * @return the T corresponding to the provided key, or defaultVal if the
    *         key is not found.
    */
   public <T> T getObject(String key, T defaultVal);

   /**
    * Create a new PropertyMap that is this map but with values copied across
    * from the provided PropertyMap. In other words, all keys that are in the
    * provided PropertyMap will replace keys in this PropertyMap in the result
    * object.
    * @param alt
    * @return A PropertyMap that is the combination of this map and the
    *         provided one.
    */
   public PropertyMap merge(PropertyMap alt);

   /**
    * Return a set of all keys of properties in the map.
    * @return set of all keys of properties in the PropertyMap
    */
   public Set<String> getKeys();

   /**
    * Return true if there is an entry in the property map for the given key.
    * @param key Key to be tested
    * @return true if this PropertyMap contains the key, false otherwise
    */
   public boolean containsKey(String key);

   /**
    * Return the type of the specified property, or null if the property does
    * not exist.
    * @param key The key of the property in question.
    * @return the class (e.g. String.class, Boolean.class, Double[].class) of
    *         the property, or null if the property is not found.
    */
   public Class getPropertyType(String key);

   /**
    * Save this PropertyMap to disk at the specified location. This will
    * overwrite any existing file at the location. Saved PropertyMaps can be
    * loaded using DataManager.loadPropertyMap().
    * @param path Location on disk to save the PropertyMap.
    * @throws IOException if there was any error in saving the PropertyMap.
    */
   public void save(String path) throws IOException;
}
