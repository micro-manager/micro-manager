package org.micromanager;

import java.io.IOException;

/**
 * This interface provides a way to save and recall parameters across multiple
 * sessions of the program. These parameters are specific to the selected user
 * at login time, and thus allow multiple users of a microscope (who all use
 * the same system-level user accuont) to have different customizations of the
 * program.
 */
public interface UserProfile {
   /**
    * The profile found in this file provide default values for all users;
    * these values will be used only if the user has not set their own values
    * for a key (and such values are often set automatically as a side-effect
    * of interacting with the program).
    */
   public static final String GLOBAL_SETTINGS_FILE = "GlobalUserProfile.txt";

   /**
    * Retrieve a specific value from the parameter storage, as a String.
    * @param c A Class<?> which provides scope for where to look for the key;
    *          this is analogous to the parameter to
    *          java.util.prefs.Preferences.userNodeForPackage(), except that
    *          the scope is specific to the class, not the package the class
    *          is in.
    * @param key The identifier for the parameter.
    * @param fallback The value to return if the key is not found or the key
    *                 points to null.
    * @return The value in storage, or null if the value does not exist.
    */
   public String getString(Class<?> c, String key, String fallback);
   /** As above, but for String arrays. */
   public String[] getStringArray(Class<?> c, String key, String[] fallback);
   /** Set a new String value in the storage */
   public void setString(Class<?> c, String key, String value);
   /** As above, but for String arrays. */
   public void setStringArray(Class<?> c, String key, String[] value);

   /** As above, but for Integers. */
   public Integer getInt(Class<?> c, String key, Integer fallback);
   /** As above, but for Integer arrays. */
   public Integer[] getIntArray(Class<?> c, String key, Integer[] fallback);
   /** Setter for Integers */
   public void setInt(Class<?> c, String key, Integer value);
   /** As above, but for Integer arrays. */
   public void setIntArray(Class<?> c, String key, Integer[] value);

   /** As above, but for Doubles. */
   public Double getDouble(Class<?> c, String key, Double fallback);
   /** As above, but for Double arrays. */
   public Double[] getDoubleArray(Class<?> c, String key, Double[] fallback);
   /** Setter for Doubles. */
   public void setDouble(Class<?> c, String key, Double value);
   /** As above, but for Double arrays. */
   public void setDoubleArray(Class<?> c, String key, Double[] value);

   /** As above, but for Booleans. */
   public Boolean getBoolean(Class<?> c, String key, Boolean fallback);
   /** As above, but for Boolean arrays. */
   public Boolean[] getBooleanArray(Class<?> c, String key, Boolean[] fallback);
   /** Setter for Booleans. */
   public void setBoolean(Class<?> c, String key, Boolean value);
   /** As above, but for Boolean arrays. */
   public void setBooleanArray(Class<?> c, String key, Boolean[] value);

   /** Save the current user's profile. If the program is closed before this
     * method is called, then any changes made since the last call will not
     * be persisted to the next session. This will not include any values
     * "inherited" from the global defaults.
     * Generates the same output as saveProfileToFile(), but the file is
     * automatically selected (stored in an OS-appropriate location for user
     * data).
     * @throws IOException if the file cannot be written for any reason.
     */
   public void saveProfile() throws IOException;

   /** Save the current user's profile to the specified file. This will not
     * include any "inherited" values from the global defaults.
     * @throws IOException if the file cannot be written for any reason.
     */
   public void saveProfileToFile(String path) throws IOException;

   /** As above, but only keys that are specific to the provided class are
     * preserved. This can be useful if you want to be able to save/load your
     * settings, in conjunction with appendFile(), below. */
   public void saveProfileSubsetToFile(Class<?> c, String path) throws IOException;

   /** Merge the profile at the specified path into the current active user
     * profile. All keys specified in the file will overwrite keys in the
     * active profile. */
   public void appendFile(String path);
}
