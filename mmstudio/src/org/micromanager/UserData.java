package org.micromanager;


/**
 * This interface is used for storing custom information in the Metadata,
 * SummaryMetadata, and DisplaySettings classes (in org.micromanager.data and
 * org.micromanager.display). It presents a simple key/value mapping that can
 * store strings, integers, and doubles. New versions can be instantiated via
 * org.micromanager.data.DataManager or
 * org.micromanager.display.DisplayManager.
 */
public interface UserData {
   interface UserDataBuilder {
      /** Construct a UserData from the Builder. Call this once the builder's
       * values are all set. */
      UserData build();

      /** Put a new String value into the mapping. */
      UserDataBuilder putString(String key, String value);
      
      /** Put a new Integer value into the mapping. */
      UserDataBuilder putInt(String key, Integer value);

      /** Put a new Double value into the mapping. */
      UserDataBuilder putDouble(String key, Double value);
   }

   /** Construct a UserData.UserDataBuilder that can be used to create a
    * modified version of this UserData instance. */
   public UserDataBuilder copy();

   /** Retrieve a String value from the mapping. Will return null if the key is
    * not found, or if the mapped value is not a String. */
   public String getString(String key);

   /** Retrieve an Integer value from the mapping. Will return null if the key
    * is not found, or if the mapped value is not an Integer. */
   public Integer getInt(String key);

   /** Retrieve an Double value from the mapping. Will return null if the key
    * is not found, or if the mapped value is not a Double. */
   public Double getDouble(String key);
}
