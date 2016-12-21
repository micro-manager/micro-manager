///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2015
//
// COPYRIGHT:    University of California, San Francisco, 2015
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.



package org.micromanager;

import java.io.IOException;

/**
 * This interface provides a way to save and recall parameters across multiple
 * sessions of the program. These parameters are specific to the selected user
 * at login time, and thus allow multiple users of a microscope (who all use
 * the same system-level user account) to have different customizations of the
 * program.
 * You can access this object via Studio.profile() or Studio.getUserProfile().
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
    * Return the name of the profile currently being used.
    * @return The selected profile name.
    */
   public String getProfileName();

   /**
    * Retrieves a specific value from the parameter storage, as a String.
    * @param c A <code>Class</code> which provides scope for where to look for
    *          the key; this is analogous to the parameter to
    *          <code>java.util.prefs.Preferences.userNodeForPackage()</code>,
    *          except that the scope is specific to the class, not the package
    *          the class is in.
    * @param key The identifier for the parameter.
    * @param fallback Value that will be returned if the key is not found or the
    *          key points to null.
    * @return The value in storage, or null if the value does not exist.
    */
   public String getString(Class<?> c, String key, String fallback);

   /**
    * Retrieve a specific value from the parameter storage, as a String array.
    * @param c class providing scope for the key
    * @param key  Identifier for the parameter
    * @param fallback Value that will be returned if the key is not found or the
    * key points to null.
    * @return Stored value, or null if the value does not exist
    */
   public String[] getStringArray(Class<?> c, String key, String[] fallback);

   /**
    * Sets a String value in the storage. 
    * @param c class providing scope for the key
    * @param key  Identifier for the parameter
    * @param value String value to be stored
    */
   public void setString(Class<?> c, String key, String value);

   /**
    * Sets a String Array in the storage. 
    * @param c class providing scope for the key
    * @param key  Identifier for the parameter
    * @param value String Array to be stored
    */
   public void setStringArray(Class<?> c, String key, String[] value);

   /**
    * Retrieves a specific value from the parameter storage, as an Integer.
    * @param c class providing scope for the key
    * @param key  Identifier for the parameter
    * @param fallback Value that will be returned if the key is not found or the
    * key points to null.
    * @return Stored value, or null if the value does not exist
    */
   public Integer getInt(Class<?> c, String key, Integer fallback);

   /**
    * Retrieves a specific value from the parameter storage, as an Array of
    * Integers.
    * @param c class providing scope for the key
    * @param key  Identifier for the parameter
    * @param fallback Value that will be returned if the key is not found or the
    * key points to null.
    * @return Stored value, or null if the value does not exist
    */
   public Integer[] getIntArray(Class<?> c, String key, Integer[] fallback);

   /**
    * Sets an Integer in the storage. 
    * @param c class providing scope for the key
    * @param key  Identifier for the parameter
    * @param value Integer to be stored
    */
   public void setInt(Class<?> c, String key, Integer value);

   /**
    * Sets a new Integer Array in the storage. 
    * @param c class providing scope for the key
    * @param key  Identifier for the parameter
    * @param value Integer Array to be stored
    */
   public void setIntArray(Class<?> c, String key, Integer[] value);

   /**
    * Retrieves a specific value from the parameter storage, as a Long.
    * @param c class providing scope for the key
    * @param key  Identifier for the parameter
    * @param fallback Value that will be returned if the key is not found or the
    * key points to null.
    * @return Stored value, or null if the value does not exist
    */
   public Long getLong(Class<?> c, String key, Long fallback);

   /**
    * Retrieves a specific value from the parameter storage, as an Array of
    * Longs.
    * @param c class providing scope for the key
    * @param key  Identifier for the parameter
    * @param fallback Value that will be returned if the key is not found or the
    * key points to null.
    * @return Stored value, or null if the value does not exist
    */
   public Long[] getLongArray(Class<?> c, String key, Long[] fallback);

   /**
    * Sets a Long in the storage. 
    * @param c class providing scope for the key
    * @param key  Identifier for the parameter
    * @param value Long to be stored
    */
   public void setLong(Class<?> c, String key, Long value);

   /**
    * Sets a new Long Array in the storage. 
    * @param c class providing scope for the key
    * @param key  Identifier for the parameter
    * @param value Long Array to be stored
    */
   public void setLongArray(Class<?> c, String key, Long[] value);

   /**
    * Retrieves a specific value from the parameter storage, as a Double.
    * @param c class providing scope for the key
    * @param key  Identifier for the parameter
    * @param fallback Value that will be returned if the key is not found or the
    * key points to null.
    * @return Stored value, or null if the value does not exist
    */
   public Double getDouble(Class<?> c, String key, Double fallback);

   /**
    * Retrieves a specific value from the parameter storage, as anArray of
    * Doubles.
    * @param c class providing scope for the key
    * @param key  Identifier for the parameter
    * @param fallback Value that will be returned if the key is not found or the
    * key points to null.
    * @return Stored value, or null if the value does not exist
    */
   public Double[] getDoubleArray(Class<?> c, String key, Double[] fallback);

   /**
    * Sets a Double in the storage. 
    * @param c class providing scope for the key
    * @param key  Identifier for the parameter
    * @param value Double to be stored
    */
   public void setDouble(Class<?> c, String key, Double value);

   /**
    * Sets a Double Array in the storage. 
    * @param c class providing scope for the key
    * @param key  Identifier for the parameter
    * @param value Double Array to be stored
    */
   public void setDoubleArray(Class<?> c, String key, Double[] value);

   /**
    * Retrieves a specific value from the parameter storage, as a Boolean
    * @param c class providing scope for the key
    * @param key  Identifier for the parameter
    * @param fallback Value that will be returned if the key is not found or the
    * key points to null.
    * @return Stored value, or null if the value does not exist
    */
   public Boolean getBoolean(Class<?> c, String key, Boolean fallback);

   /**
    * Retrieves a specific value from the parameter storage, as a Boolean Array
    * @param c class providing scope for the key
    * @param key  Identifier for the parameter
    * @param fallback Value that will be returned if the key is not found or the
    * key points to null.
    * @return Stored value, or null if the value does not exist
    */
   public Boolean[] getBooleanArray(Class<?> c, String key, Boolean[] fallback);

   /**
    * Sets a Boolean value in the storage. 
    * @param c class providing scope for the key
    * @param key  Identifier for the parameter
    * @param value Boolean to be stored
    */
   public void setBoolean(Class<?> c, String key, Boolean value);

   /**
    * Sets a Boolean Array in the storage. 
    * @param c class providing scope for the key
    * @param key  Identifier for the parameter
    * @param value Boolean Array to be stored
    */
   public void setBooleanArray(Class<?> c, String key, Boolean[] value);

   /**
    * Retrieve a serialized object from the profile.
    * @param c class providing scope for the key
    * @param key Identifier for the parameter
    * @param fallback Value that will be returned if the key is not found or
    * the key points to null.
    * @return The stored object, or the fallback if that value is not found.
    * @throws IOException if deserialization of the object fails
    */
   public <T> T getObject(Class<?> c, String key, T fallback) throws IOException;

   /**
    * Save a serializable object in the profile. The object will be serialized
    * and converted into base64 for storage.
    * NOTE: it is recommended that you avoid storing large objects in the
    * profile, as they can take up a substantial amount of space and be slow
    * to save/load.
    * @param c class providing scope for the key
    * @param key Identifier for the parameter
    * @param value Object to be stored
    * @throws IOException if the serialization fails for any reason
    */
   public <T> void setObject(Class<?> c, String key, T value) throws IOException;

   /**
    * The UserProfile normally routinely saves changes
    * to disk on a periodic basis; calling this method will force a save to
    * happen immediately, and this method will wait until saving is completed.
    * The saved file contains all values specific to this user (i.e. not
    * "inherited" from the global profile). The contents of the file are
    * identical to those generated by exportProfileToFile(), but the file is
    * automatically selected (stored in an OS-appropriate location for user
    * data).
    * @throws IOException if the file cannot be written for any reason.
    */
   public void syncToDisk() throws IOException;

   /**
    * Exports the current user's profile to the specified file. This will not
    * include any "inherited" values from the global defaults.
    * @param path file path for user profile file
    * @throws IOException if the file cannot be written for any reason.
    */
   public void exportProfileToFile(String path) throws IOException;

   /**
    * Exports the current "combined profile" state to the specified file. This
    * includes both values in the current user profile and values "inherited"
    * from the global defaults.
    * @param path file path to save to
    * @throws IOException if the file cannot be written for any reason.
    */
   public void exportCombinedProfileToFile(String path) throws IOException;

   /**
    * Exports a portion of the current user's profile to the specified file.
    * This will not include any "inherited" values from the global defaults,
    * and only keys that are specific to the provided class are preserved.
    * This can be useful if you want to be able to save/load your settings, in
    * conjunction with appendFile(), below.
    * @param c only setting belonging to this class will be saved
    * @param path file path where the data will be saved
    * @throws IOException if the file cannot be written for any reason.
    */
   public void exportProfileSubsetToFile(Class<?> c, String path) throws IOException;

   /**
    * Export a portion of the user's profile to the specified file. This works
    * like exportProfileSubsetToFile(), except that all classes under the
    * provided package name will be exported. For example, using a package of
    * "org.micromanager.display" would grab all portions of the profile that
    * are in the org.micromanager.display package or any of its child packages.
    * @param packageName Package name indicating subset of profile to be saved
    * @param path file path to save to
    * @throws IOException if the file cannot be written for any reason.
    */
   public void exportPackageProfileToFile(String packageName, String path) throws IOException;

   /**
    * Extract a portion of the current user's profile and provide it in the
    * form of a PropertyMap. This works like exportProfileSubsetToFile,
    * except that the result is made immediately available instead of being
    * saved to disk.
    * @param c Class whose key-value pairs will be extracted.
    * @return A PropertyMap containing all keys associated with the given
    *         class.
    */
   public PropertyMap extractProfileSubset(Class<?> c);

   /**
    * Load the provided PropertyMap (e.g. as provided by
    * extractProfileSubset()) and insert all keys it contains into the current
    * user's profile.
    * @param c Class to associate with all keys in the PropertyMap.
    * @param properties PropertyMap containing keys and values to insert into
    *        the current user's profile.
    */
   public void insertProperties(Class<?> c, PropertyMap properties);

   /**
    * Remove all keys from the profile that are associated with the provided
    * class. This functionally allows you to reset the profile to use the
    * default values (or the values specified in the global settings file).
    * @param c Key-values belonging to this class will be removed
    */
   public void clearProfileSubset(Class<?> c);

   /**
    * Remove all keys from the profile that are associated with the provided
    * package. For example, using a package of "org.micromanager.display" would
    * cause the user's saved settings relating to the org.micromanager.display
    * package to be lost, and the next time those settings would be read,
    * default values would be used instead.
    * @param packageName Package name indicating subset of the profile to be
    *        cleared.
    */
   public void clearPackageProfile(String packageName);

   /**
    * Merge the profile at the specified path into the current active user
    * profile. All keys specified in the file will overwrite keys in the
    * active profile.
    * @param path file to which the profile should be appended
    */
   public void appendFile(String path);

   /**
    * Create a new user using the provided profile file as a basis.
    * @param username Name for the new user.
    * @param path File generated by one of the export methods; its contents
    *        will be used to pre-populate the user's profile. May be null, in
    *        which case a "blank" user with no profile data is created.
    * @throws IllegalArgumentException if a user of that name already exists.
    * @throws IOException if there was an error in copying the profile file.
    */
   public void createUser(String username, String path) throws IllegalArgumentException, IOException;
}
