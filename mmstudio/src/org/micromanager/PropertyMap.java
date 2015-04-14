package org.micromanager;

/**
 * This interface is used for storing custom information in the Metadata,
 * SummaryMetadata, and DisplaySettings classes (in org.micromanager.data and
 * org.micromanager.display). It presents a simple key/value mapping that can
 * store strings, integers, and doubles. New versions can be instantiated via
 * org.micromanager.data.DataManager or
 * org.micromanager.display.DisplayManager.
 */
public interface PropertyMap {
   interface PropertyMapBuilder {
      /**
       * Construct a PropertyMap from the Builder. Call this once the
       * builder's values are all set.
       */
      PropertyMap build();

      /**
       * Put a new String value into the mapping.
       * @return The PropertyMapBuilder, so that puts can be chained together
       */
      PropertyMapBuilder putString(String key, String value);
      /**
       * Put an array of Strings into the mapping.
       * @return The PropertyMapBuilder, so that puts can be chained together
       */
      PropertyMapBuilder putStringArray(String key, String[] values);
     
      /**
       * Put a new Integer value into the mapping.
       * @return The PropertyMapBuilder, so that puts can be chained together
       */
      PropertyMapBuilder putInt(String key, Integer value);
      /**
       * Put an array of Integers into the mapping.
       * @return The PropertyMapBuilder, so that puts can be chained together
       */
      PropertyMapBuilder putIntArray(String key, Integer[] values);

      /**
       * Put a new Long value into the mapping.
       * @return The PropertyMapBuilder, so that puts can be chained together
       */
      PropertyMapBuilder putLong(String key, Long value);
      /**
       * Put an array of Longs into the mapping.
       * @return The PropertyMapBuilder, so that puts can be chained together
       */
      PropertyMapBuilder putLongArray(String key, Long[] values);

      /**
       * Put a new Double value into the mapping.
       * @return The PropertyMapBuilder, so that puts can be chained together
       */
      PropertyMapBuilder putDouble(String key, Double value);
      /**
       * Put an array of Doubles into the mapping.
       * @return The PropertyMapBuilder, so that puts can be chained together
       */
      PropertyMapBuilder putDoubleArray(String key, Double[] values);

      /**
       * Put a new Boolean value into the mapping.
       * @return The PropertyMapBuilder, so that puts can be chained together
       */
      PropertyMapBuilder putBoolean(String key, Boolean value);
      /**
       * Put an array of Booleans into the mapping.
       * @return The PropertyMapBuilder, so that puts can be chained together
       */
      PropertyMapBuilder putBooleanArray(String key, Boolean[] values);
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
    * not found. If the mapped value is not a String, a RuntimeException will
    * be thrown.
    * @return the String corresponding to the specified key.
    */
   public String getString(String key);
   /**
    * Retrieve a String value from the mapping. If the key is not found, then
    * the provided default value will be returned. If the mapped value is not a
    * String, a RuntimeException will be thrown.
    * @param defaultVal the default value to use if the key is not found.
    * @return the String corresponding to the specified key, or defaultVal
    *         if the key is not found.
    */
   public String getString(String key, String defaultVal);
   /**
    * Retrieve a String array from the mapping. Will return null if the key is
    * not found. If the mapped value is not a String[], a RuntimeException will
    * be thrown.
    * @return the String array corresponding to the specified key.
    */
   public String[] getStringArray(String key);
   /**
    * Retrieve a String array from the mapping. If the key is not found, then
    * the provided default value will be returned. If the mapped value is not a
    * String[], a RuntimeException will be thrown.
    * @param defaultVal the default value to use if the key is not found.
    * @return the String array corresponding to the specified key, or
    *         defaultVal if the key is not found.
    */
   public String[] getStringArray(String key, String[] defaultVal);

   /**
    * Retrieve an Integer value from the mapping. Will return null if the key
    * is not found. If the mapped value is not an Integer, a RuntimeException
    * will be thrown.
    * @return The Integer corresponding to the provided key.
    */
   public Integer getInt(String key);
   /**
    * Retrieve an Integer value from the mapping. If the key is not found, then
    * the provided default value will be returned. d. If the mapped value is
    * not an Integer, a RuntimeException will be thrown.
    * @param defaultVal the default value to use if the key is not found.
    * @return The Integer corresponding to the provided key, or defaultVal
    *         if the key is not found.
    */
   public Integer getInt(String key, Integer defaultVal);
   /**
    * Retrieve an Integer array from the mapping. Will return null if the key
    * is not found. If the mapped value is not an Integer[], a RuntimeException
    * will be thrown.
    * @return The Integer[] corresponding to the provided key.
    */
   public Integer[] getIntArray(String key);
   /**
    * Retrieve an Integer array from the mapping. If the key is not found, then
    * the provided default value will be returned. d. If the mapped value is
    * not an Integer[], a RuntimeException will be thrown.
    * @param defaultVal the default value to use if the key is not found.
    * @return The Integer[] corresponding to the provided key, or defaultVal
    *         if the key is not found.
    */
   public Integer[] getIntArray(String key, Integer[] defaultVal);

   /**
    * Retrieve a Long value from the mapping. Will return null if the key
    * is not found. If the mapped value is not an Long, a RuntimeException
    * will be thrown.
    * @return The Long corresponding to the provided key.
    */
   public Long getLong(String key);
   /**
    * Retrieve a Long value from the mapping. If the key is not found, then
    * the provided default value will be returned. d. If the mapped value is
    * not an Long, a RuntimeException will be thrown.
    * @param defaultVal the default value to use if the key is not found.
    * @return The Long corresponding to the provided key, or defaultVal
    *         if the key is not found.
    */
   public Long getLong(String key, Long defaultVal);
   /**
    * Retrieve a Long array from the mapping. Will return null if the key
    * is not found. If the mapped value is not an Long, a RuntimeException
    * will be thrown.
    * @return The Long[] corresponding to the provided key.
    */
   public Long[] getLongArray(String key);
   /**
    * Retrieve a Long array from the mapping. If the key is not found, then
    * the provided default value will be returned. d. If the mapped value is
    * not an Long, a RuntimeException will be thrown.
    * @param defaultVal the default value to use if the key is not found.
    * @return The Long[] corresponding to the provided key, or defaultVal
    *         if the key is not found.
    */
   public Long[] getLongArray(String key, Long[] defaultVal);

   /**
    * Retrieve a Double value from the mapping. Will return null if the key
    * is not found. If the mapped value is not a Double, a RuntimeException
    * will be thrown.
    * @return The Double corresponding to the provided key.
    */
   public Double getDouble(String key);
   /**
    * Retrieve a Double value from the mapping. If the key is not found, then
    * the provided default value will be returned. d. If the mapped value is
    * not a Double, a RuntimeException will be thrown.
    * @param defaultVal the default value to use if the key is not found.
    * @return The Double corresponding to the provided key, or defaultVal
    *         if the key is not found.
    */
   public Double getDouble(String key, Double defaultVal);
   /**
    * Retrieve a Double array from the mapping. Will return null if the key
    * is not found. If the mapped value is not an Double, a RuntimeException
    * will be thrown.
    * @return The Double[] corresponding to the provided key.
    */
   public Double[] getDoubleArray(String key);
   /**
    * Retrieve a Double array from the mapping. If the key is not found, then
    * the provided default value will be returned. d. If the mapped value is
    * not an Double, a RuntimeException will be thrown.
    * @param defaultVal the default value to use if the key is not found.
    * @return The Double[] corresponding to the provided key, or defaultVal
    *         if the key is not found.
    */
   public Double[] getDoubleArray(String key, Double[] defaultVal);

   /**
    * Retrieve a Boolean value from the mapping. Will return null if the key
    * is not found. If the mapped value is not a Boolean, a RuntimeException
    * will be thrown.
    * @return The Boolean corresponding to the provided key.
    */
   public Boolean getBoolean(String key);
   /**
    * Retrieve a Boolean value from the mapping. If the key is not found, then
    * the provided default value will be returned. d. If the mapped value is
    * not a Boolean, a RuntimeException will be thrown.
    * @param defaultVal the default value to use if the key is not found.
    * @return The Boolean corresponding to the provided key, or defaultVal
    *         if the key is not found.
    */
   public Boolean getBoolean(String key, Boolean defaultVal);
   /**
    * Retrieve a Boolean array from the mapping. Will return null if the key
    * is not found. If the mapped value is not an Boolean, a RuntimeException
    * will be thrown.
    * @return The Boolean[] corresponding to the provided key.
    */
   public Boolean[] getBooleanArray(String key);
   /**
    * Retrieve a Boolean array from the mapping. If the key is not found, then
    * the provided default value will be returned. d. If the mapped value is
    * not an Boolean, a RuntimeException will be thrown.
    * @param defaultVal the default value to use if the key is not found.
    * @return The Boolean[] corresponding to the provided key, or defaultVal
    *         if the key is not found.
    */
   public Boolean[] getBooleanArray(String key, Boolean[] defaultVal);

   /**
    * Create a new PropertyMap that is this map but with values copied across
    * from the provided PropertyMap. In other words, all keys that are in the
    * provided PropertyMap will replace keys in this PropertyMap in the result
    * object.
    * @return A PropertyMap that is the combination of this map and the
    *         provided one.
    */
   public PropertyMap merge(PropertyMap alt);
}
