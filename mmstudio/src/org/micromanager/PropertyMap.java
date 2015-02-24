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
      /** Construct a PropertyMap from the Builder. Call this once the
       * builder's values are all set. */
      PropertyMap build();

      /** Put a new String value into the mapping. */
      PropertyMapBuilder putString(String key, String value);
      /** Put an array of Strings into the mapping. */
      PropertyMapBuilder putStringArray(String key, String[] values);
     
      /** Put a new Integer value into the mapping. */
      PropertyMapBuilder putInt(String key, Integer value);
      /** Put an array of Integers into the mapping. */
      PropertyMapBuilder putIntArray(String key, Integer[] values);

      /** Put a new Double value into the mapping. */
      PropertyMapBuilder putDouble(String key, Double value);
      /** Put an array of Doubles into the mapping. */
      PropertyMapBuilder putDoubleArray(String key, Double[] values);

      /** Put a new Boolean value into the mapping. */
      PropertyMapBuilder putBoolean(String key, Boolean value);
      /** Put an array of Booleans into the mapping. */
      PropertyMapBuilder putBooleanArray(String key, Boolean[] values);
   }

   /** Construct a PropertyMap.PropertyMapBuilder that can be used to create a
    * modified version of this PropertyMap instance. */
   public PropertyMapBuilder copy();

   /** Retrieve a String value from the mapping. Will return null if the key is
    * not found. If the mapped value is not a String, a RuntimeException will
    * be thrown. */
   public String getString(String key);
   /** Retrieve an array of Strings from the mapping, with a similar caveat. */
   public String[] getStringArray(String key);

   /** Retrieve an Integer value from the mapping. Will return null if the key
    * is not found. If the mapped value is not an Integer, a RuntimeException
    * will be thrown. */
   public Integer getInt(String key);
   /** Retrieve an array of Integers from the mapping, with a similar caveat. */
   public Integer[] getIntArray(String key);

   /** Retrieve an Double value from the mapping. Will return null if the key
    * is not found. If the mapped value is not a Double, a RuntimeException
    * will be thrown. */
   public Double getDouble(String key);
   /** Retrieve an array of Doubles from the mapping, with a similar caveat. */
   public Double[] getDoubleArray(String key);

   /** Retrieve an Boolean value from the mapping. Will return null if the key
    * is not found. If the mapped value is not a Boolean, a RuntimeException
    * will be thrown. */
   public Boolean getBoolean(String key);
   /** Retrieve an array of Booleans from the mapping, with a similar caveat. */
   public Boolean[] getBooleanArray(String key);
}
