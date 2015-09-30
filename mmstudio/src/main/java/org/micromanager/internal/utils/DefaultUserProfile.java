package org.micromanager.internal.utils;

import com.google.common.base.Charsets;
import com.google.common.io.BaseEncoding;
import com.google.common.io.Files;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Scanner;
import java.util.Set;

import org.json.JSONException;
import org.json.JSONObject;

import org.micromanager.PropertyMap;
import org.micromanager.UserProfile;
import org.micromanager.data.internal.DefaultPropertyMap;
import org.micromanager.internal.MMStudio;

public class DefaultUserProfile implements UserProfile {
   private static final String USERNAME_MAPPING_FILE = "Profiles.txt";
   private static final String GLOBAL_USER = "Global defaults";
   public static final String DEFAULT_USER = "Default user";
   private static final String ALWAYS_USE_DEFAULT_USER = "always use the default user profile";

   private static final DefaultUserProfile staticInstance_;
   static {
      staticInstance_ = new DefaultUserProfile();
   }
   private HashMap<String, String> nameToFile_;
   private String profileName_;
   private final DefaultPropertyMap globalProfile_;
   private DefaultPropertyMap userProfile_;
   // This object exists to give us something consistent to lock on.
   private static final Object lockObject_ = new Object();
   // This thread will be periodically checking if it should save.
   private Thread saveThread_;
   private static final Object syncObject_ = new Object();
   private IOException saveException_;

   public DefaultUserProfile() {
      nameToFile_ = loadProfileMapping();
      String globalPath = new File(UserProfile.GLOBAL_SETTINGS_FILE).getAbsolutePath();
      globalProfile_ = loadPropertyMap(globalPath);
      // Naturally we start with the default user loaded.
      setCurrentProfile(DEFAULT_USER);
      startSaveThread();
   }

   /**
    * Read the username mapping file so we know which user's profile is in
    * which file.
    */
   private HashMap<String, String> loadProfileMapping() {
      JSONObject mapping = new JSONObject();
      HashMap<String, String> result = new HashMap<String, String>();
      File tmp = new File(JavaUtils.getApplicationDataPath() +
            "/" + USERNAME_MAPPING_FILE);
      if (!tmp.exists()) {
         ReportingUtils.logMessage("Creating user profile mapping file");
         // No file to be found; create it with no entry.
         writeProfileMapping(result);
      }
      try {
         mapping = new JSONObject(
               loadFileToString(JavaUtils.getApplicationDataPath() +
                  "/" + USERNAME_MAPPING_FILE));
      }
      catch (Exception e) { // JSONException, IOException
         ReportingUtils.logError(e, "Unable to load user profiles mapping file");
      }
      for (String key : MDUtils.getKeys(mapping)) {
         try {
            result.put(key, mapping.getString(key));
         }
         catch (JSONException e) {
            ReportingUtils.logError(e, "Unable to get filename for user " + key);
         }
      }
      return result;
   }

   /**
    * Write out the current mapping of usernames to profile files.
    */
   private void writeProfileMapping(HashMap<String, String> nameToFile) {
      JSONObject mapping = new JSONObject();
      for (String name : nameToFile.keySet()) {
         try {
            mapping.put(name, nameToFile.get(name));
         }
         catch (JSONException e) {
            ReportingUtils.logError(e, "Unable to add entry for profile " + name + " to file " + nameToFile.get(name));
         }
      }

      JavaUtils.createApplicationDataPathIfNeeded();
      try {
         FileWriter writer = new FileWriter(
               JavaUtils.getApplicationDataPath() + "/" + USERNAME_MAPPING_FILE);
         writer.write(mapping.toString(2) + "\n");
         writer.close();
      }
      catch (FileNotFoundException e) {
         ReportingUtils.logError(e, "Unable to open writer to save user profile mapping file");
      }
      catch (JSONException e) {
         ReportingUtils.logError(e, "Unable to convert JSON mapping into string");
      }
      catch (IOException e) {
         ReportingUtils.logError(e, "Exception when writing user profile mapping file");
      }
   }

   /**
    * Load a PropertyMap for a given user; return an empty PropertyMap if that
    * user doesn't yet exist, creating the profile for them in the process.
    */
   private DefaultPropertyMap loadProfile(String profileName) {
      if (!nameToFile_.containsKey(profileName)) {
         // Create a new profile.
         ReportingUtils.logMessage("Profile name " + profileName + " not found in profile mapping; creating that user");
         addProfile(profileName);
         return (DefaultPropertyMap) (new DefaultPropertyMap.Builder().build());
      }
      String filename = nameToFile_.get(profileName);
      JavaUtils.createApplicationDataPathIfNeeded();
      String path = JavaUtils.getApplicationDataPath() + "/" + filename;
      return loadPropertyMap(path);
   }

   private DefaultPropertyMap loadPropertyMap(String path) {
      String contents;
      try {
         contents = loadFileToString(path);
      }
      catch (IOException e) {
         // That file doesn't exist yet; create a new empty user.
         ReportingUtils.logMessage("Asked to load file at path " + path +
               " which doesn't exist; instead providing a default empty PropertyMap.");
         return (DefaultPropertyMap) (new DefaultPropertyMap.Builder().build());
      }
      try {
         String text = loadFileToString(path);
         return DefaultPropertyMap.fromJSON(new JSONObject(text));
      }
      catch (Exception e) {
         ReportingUtils.showError(e, "There was an error when loading saved user preferences. Please delete the file at " + path + " and re-start Micro-Manager.");
         return null;
      }
   }

   /**
    * Load the contents of a file and return them as a string. Adapted from
    * http://stackoverflow.com/questions/326390/how-to-create-a-java-string-from-the-contents-of-a-file
    * TODO: should probably be moved into utils somewhere.
    */
   private String loadFileToString(String path) throws IOException {
      return Files.toString(new File(path), Charsets.UTF_8);
   }

   private void startSaveThread() {
      saveThread_ = new Thread("User profile save thread") {
         @Override
         public void run() {
            runSaveThread();
         }
      };
      saveThread_.start();
   }

   /**
    * Periodically check for changes in the profile (by seeing if this method's
    * copy of the userProfile_ reference is different from the current
    * reference), and save the profile to disk after a) a change has occurred,
    * and b) it has been at least 5s since the change.
    * We also can be interrupted and forced to save immediately, in which
    * case we immediately write to disk, set the saveException_ exception if
    * an exception occurred, and then exit (we'll be restarted immediately
    * afterwards; check the syncToDisk() method).
    */
   private void runSaveThread() {
      PropertyMap curRef = userProfile_;
      boolean wasInterrupted = false;
      long targetSaveTime = 0;
      boolean haveLoggedFailure = false;
      while (true) {
         try {
            Thread.sleep(100);
         }
         catch (InterruptedException e) {
            wasInterrupted = true;
         }
         if (wasInterrupted || Thread.interrupted() ||
               (targetSaveTime != 0 && System.currentTimeMillis() > targetSaveTime)) {
            // Either enough time has passed or we were interrupted; time to
            // save the profile.
            JavaUtils.createApplicationDataPathIfNeeded();
            try {
               exportProfileToFile(JavaUtils.getApplicationDataPath() +
                     "/" + nameToFile_.get(profileName_));
            }
            catch (IOException e) {
               if (wasInterrupted) {
                  // Record the exception for our "caller"
                  saveException_ = e;
               }
               else if (!haveLoggedFailure) {
                  // Log write failures once per thread, so a) errors aren't
                  // silently swallowed, but b) we don't spam the logs.
                  ReportingUtils.logError(e,
                        "Failed to sync user profile to disk. Further logging of this error will be suppressed.");
                  haveLoggedFailure = true;
               }
            }
            if (wasInterrupted) {
               // Now time to exit.
               return;
            }
            targetSaveTime = 0;
         }
         // Check for changes in the profile, and if the profile changes,
         // start/update the countdown to when we should save.
         if (userProfile_ != curRef) {
            targetSaveTime = System.currentTimeMillis() + 5000;
            curRef = userProfile_;
         }
      }
   }

   /**
    * Append a class name to the provided key to generate a unique-across-MM
    * key.
    * Note that exportProfileSubsetToFile() and clearProfileSubset() assume that
    * keys start with the class' canonical name.
    */
   private String genKey(Class<?> c, String key) {
      return c.getCanonicalName() + ":" + key;
   }

   // Getters/setters. There is a ton of duplication here; unfortunately, I
   // don't see any real way to avoid it. Here's hoping that my auto-generated
   // code doesn't have any typos in it!

   @Override
   public String getString(Class<?> c, String key, String fallback) {
      key = genKey(c, key);
      synchronized(lockObject_) {
         String result = userProfile_.getString(key);
         if (result != null) {
            return result;
         }
      }
      // Try the global profile.
      synchronized(globalProfile_) {
         String result = globalProfile_.getString(key);
         if (result != null) {
            return result;
         }
      }
      // Give up.
      return fallback;
   }
   @Override
   public String[] getStringArray(Class<?> c, String key, String[] fallback) {
      key = genKey(c, key);
      synchronized(lockObject_) {
         String[] result = userProfile_.getStringArray(key);
         if (result != null) {
            return result;
         }
      }
      // Try the global profile.
      synchronized(globalProfile_) {
         String[] result = globalProfile_.getStringArray(key);
         if (result != null) {
            return result;
         }
      }
      // Give up.
      return fallback;
   }
   @Override
   public void setString(Class<?> c, String key, String value) {
      synchronized(lockObject_) {
         userProfile_ = (DefaultPropertyMap) userProfile_.copy().putString(genKey(c, key), value).build();
      }
   }
   @Override
   public void setStringArray(Class<?> c, String key, String[] value) {
      synchronized(lockObject_) {
         userProfile_ = (DefaultPropertyMap) userProfile_.copy().putStringArray(genKey(c, key), value).build();
      }
   }

   @Override
   public Integer getInt(Class<?> c, String key, Integer fallback) {
      key = genKey(c, key);
      synchronized(lockObject_) {
         Integer result = userProfile_.getInt(key);
         if (result != null) {
            return result;
         }
      }
      // Try the global profile.
      synchronized(globalProfile_) {
         Integer result = globalProfile_.getInt(key);
         if (result != null) {
            return result;
         }
      }
      // Give up.
      return fallback;
   }
   @Override
   public Integer[] getIntArray(Class<?> c, String key, Integer[] fallback) {
      key = genKey(c, key);
      synchronized(lockObject_) {
         Integer[] result = userProfile_.getIntArray(key);
         if (result != null) {
            return result;
         }
      }
      // Try the global profile.
      synchronized(globalProfile_) {
         Integer[] result = globalProfile_.getIntArray(key);
         if (result != null) {
            return result;
         }
      }
      // Give up.
      return fallback;
   }
   @Override
   public void setInt(Class<?> c, String key, Integer value) {
      synchronized(lockObject_) {
         userProfile_ = (DefaultPropertyMap) userProfile_.copy().putInt(genKey(c, key), value).build();
      }
   }
   @Override
   public void setIntArray(Class<?> c, String key, Integer[] value) {
      synchronized(lockObject_) {
         userProfile_ = (DefaultPropertyMap) userProfile_.copy().putIntArray(genKey(c, key), value).build();
      }
   }

   @Override
   public Long getLong(Class<?> c, String key, Long fallback) {
      key = genKey(c, key);
      synchronized(lockObject_) {
         Long result = userProfile_.getLong(key);
         if (result != null) {
            return result;
         }
      }
      // Try the global profile.
      synchronized(globalProfile_) {
         Long result = globalProfile_.getLong(key);
         if (result != null) {
            return result;
         }
      }
      // Give up.
      return fallback;
   }
   @Override
   public Long[] getLongArray(Class<?> c, String key, Long[] fallback) {
      key = genKey(c, key);
      synchronized(lockObject_) {
         Long[] result = userProfile_.getLongArray(key);
         if (result != null) {
            return result;
         }
      }
      // Try the global profile.
      synchronized(globalProfile_) {
         Long[] result = globalProfile_.getLongArray(key);
         if (result != null) {
            return result;
         }
      }
      // Give up.
      return fallback;
   }
   @Override
   public void setLong(Class<?> c, String key, Long value) {
      synchronized(lockObject_) {
         userProfile_ = (DefaultPropertyMap) userProfile_.copy().putLong(genKey(c, key), value).build();
      }
   }
   @Override
   public void setLongArray(Class<?> c, String key, Long[] value) {
      synchronized(lockObject_) {
         userProfile_ = (DefaultPropertyMap) userProfile_.copy().putLongArray(genKey(c, key), value).build();
      }
   }

   @Override
   public Double getDouble(Class<?> c, String key, Double fallback) {
      key = genKey(c, key);
      synchronized(lockObject_) {
         Double result = userProfile_.getDouble(key);
         if (result != null) {
            return result;
         }
      }
      // Try the global profile.
      synchronized(globalProfile_) {
         Double result = globalProfile_.getDouble(key);
         if (result != null) {
            return result;
         }
      }
      // Give up.
      return fallback;
   }
   @Override
   public Double[] getDoubleArray(Class<?> c, String key, Double[] fallback) {
      key = genKey(c, key);
      synchronized(lockObject_) {
         Double[] result = userProfile_.getDoubleArray(key);
         if (result != null) {
            return result;
         }
      }
      // Try the global profile.
      synchronized(globalProfile_) {
         Double[] result = globalProfile_.getDoubleArray(key);
         if (result != null) {
            return result;
         }
      }
      // Give up.
      return fallback;
   }
   @Override
   public void setDouble(Class<?> c, String key, Double value) {
      synchronized(lockObject_) {
         userProfile_ = (DefaultPropertyMap) userProfile_.copy().putDouble(genKey(c, key), value).build();
      }
   }
   @Override
   public void setDoubleArray(Class<?> c, String key, Double[] value) {
      synchronized(lockObject_) {
         userProfile_ = (DefaultPropertyMap) userProfile_.copy().putDoubleArray(genKey(c, key), value).build();
      }
   }

   @Override
   public Boolean getBoolean(Class<?> c, String key, Boolean fallback) {
      key = genKey(c, key);
      synchronized(lockObject_) {
         Boolean result = userProfile_.getBoolean(key);
         if (result != null) {
            return result;
         }
      }
      // Try the global profile.
      synchronized(globalProfile_) {
         Boolean result = globalProfile_.getBoolean(key);
         if (result != null) {
            return result;
         }
      }
      // Give up.
      return fallback;
   }
   @Override
   public Boolean[] getBooleanArray(Class<?> c, String key, Boolean[] fallback) {
      key = genKey(c, key);
      synchronized(lockObject_) {
         Boolean[] result = userProfile_.getBooleanArray(key);
         if (result != null) {
            return result;
         }
      }
      // Try the global profile.
      synchronized(globalProfile_) {
         Boolean[] result = globalProfile_.getBooleanArray(key);
         if (result != null) {
            return result;
         }
      }
      // Give up.
      return fallback;
   }
   @Override
   public void setBoolean(Class<?> c, String key, Boolean value) {
      synchronized(lockObject_) {
         userProfile_ = (DefaultPropertyMap) userProfile_.copy().putBoolean(genKey(c, key), value).build();
      }
   }
   @Override
   public void setBooleanArray(Class<?> c, String key, Boolean[] value) {
      synchronized(lockObject_) {
         userProfile_ = (DefaultPropertyMap) userProfile_.copy().putBooleanArray(genKey(c, key), value).build();
      }
   }

   @Override
   public <T> T getObject(Class<?> c, String key, T fallback) throws IOException {
      key = genKey(c, key);
      synchronized(lockObject_) {
         T result = userProfile_.getObject(key, (T) null);
         if (result != null) {
            return result;
         }
      }
      // Try the global profile.
      synchronized(globalProfile_) {
         return globalProfile_.getObject(key, fallback);
      }
   }

   @Override
   public <T> void setObject(Class<?> c, String key, T value) throws IOException {
      synchronized(lockObject_) {
         userProfile_ = (DefaultPropertyMap) userProfile_.copy().putObject(
               genKey(c, key), value).build();
      }
   }

   @Override
   public void exportProfileSubsetToFile(Class<?> c, String path) throws IOException {
      // Make a copy profile, and save that.
      DefaultPropertyMap.Builder builder = new DefaultPropertyMap.Builder();
      // NOTE: this should match genKey()
      String className = c.getCanonicalName();
      synchronized(lockObject_) {
         for (String key : userProfile_.getKeys()) {
            if (key.startsWith(className)) {
               builder.putProperty(key, userProfile_.getProperty(key));
            }
         }
      }
      exportPropertyMapToFile((DefaultPropertyMap) builder.build(), path);
   }

   @Override
   public void syncToDisk() throws IOException {
      // Only one caller can invoke this method at a time.
      synchronized(syncObject_) {
         saveException_ = null;
         saveThread_.interrupt();
         // Wait for saving to complete, indicated by the thread exiting.
         try {
            saveThread_.join();
         }
         catch (InterruptedException e) {
            // This should never happen.
            ReportingUtils.logError(e, "Interrupted while waiting for sync-to-disk to complete");
         }
         // Now re-start the thread.
         startSaveThread();
         if (saveException_ != null) {
            // Something went wrong while saving.
            throw(saveException_);
         }
      }
   }

   @Override
   public void exportProfileToFile(String path) throws IOException {
      exportPropertyMapToFile(userProfile_, path);
   }

   @Override
   public void exportCombinedProfileToFile(String path) throws IOException {
      exportPropertyMapToFile(
            (DefaultPropertyMap) (userProfile_.merge(globalProfile_)), path);
   }

   private void exportPropertyMapToFile(DefaultPropertyMap properties, String path) throws IOException {
      JSONObject serialization;
      synchronized(properties) {
         serialization = properties.toJSON();
      }
      try {
         FileWriter writer = new FileWriter(path);
         writer.write(serialization.toString(2) + "\n");
         writer.close();
      }
      catch (FileNotFoundException e) {
         ReportingUtils.logError(e, "Unable to open writer to save user profile mapping file");
      }
      catch (JSONException e) {
         ReportingUtils.logError(e, "Unable to convert JSON mapping into string");
      }
   }

   @Override
   public void appendFile(String path) {
      File file = new File(path);
      if (!file.exists()) {
         ReportingUtils.logError("Asked to load file at " + path + " that does not exist.");
         return;
      }
      PropertyMap properties = loadPropertyMap(path);
      userProfile_ = (DefaultPropertyMap) userProfile_.merge(properties);
   }

   public Set<String> getProfileNames() {
      // HACK: don't reveal the global profile since it's not technically a
      // "user".
      HashSet<String> result = new HashSet<String>(nameToFile_.keySet());
      result.remove(GLOBAL_USER);
      return result;
   }

   public void addProfile(String profileName) {
      // Assign a filename for the user, which should be 1 greater than the
      // largest current numerical filename we're using.
      Pattern pattern = Pattern.compile("profile-(\\d+).txt");
      int fileIndex = 0;
      for (String key : nameToFile_.keySet()) {
         String fileName = nameToFile_.get(key);
         Matcher matcher = pattern.matcher(fileName);
         if (!matcher.matches()) {
            continue;
         }
         try {
            int index = Integer.parseInt(matcher.group(1));
            fileIndex = Math.max(index, fileIndex);
         }
         catch (NumberFormatException e) {
            // No number to use.
            continue;
         }
      }
      String newFile = "profile-" + (fileIndex + 1) + ".txt";
      nameToFile_.put(profileName, newFile);
      writeProfileMapping(nameToFile_);
   }

   public void setCurrentProfile(String profileName) {
      profileName_ = profileName;
      boolean isFirstProfile = (userProfile_ == null);
      userProfile_ = loadProfile(profileName);
      if (!isFirstProfile) {
         // Update a few things that have already pulled values from the
         // default user profile by the time this has happened.
         DaytimeNighttime.setMode(DaytimeNighttime.getBackgroundMode());
         MMStudio.getInstance().getFrame().resetPosition();
      }
   }

   @Override
   public String getProfileName() {
      return profileName_;
   }

   public boolean getIsDefaultUser() {
      return profileName_.equals(DEFAULT_USER);
   }

   @Override
   public void clearProfileSubset(Class<?> c) {
      // Make a copy of the map that contains everything except the keys for
      // the specified class.
      synchronized(lockObject_) {
         DefaultPropertyMap.Builder builder = new DefaultPropertyMap.Builder();
         for (String key : userProfile_.getKeys()) {
            if (!key.startsWith(c.getCanonicalName())) {
               builder.putProperty(key, userProfile_.getProperty(key));
            }
         }
         userProfile_ = (DefaultPropertyMap) builder.build();
      }
   }

   /**
    * Delete all parameters for the current user.
    */
   public void clearProfile() {
      userProfile_ = (DefaultPropertyMap) (new DefaultPropertyMap.Builder().build());
   }

   /**
    * Delete the specified profile.
    */
   public void deleteProfile(String profileName) {
      ReportingUtils.logDebugMessage("Deleting profile " + profileName +
            " at " + nameToFile_.get(profileName));
      new File(JavaUtils.getApplicationDataPath() + "/" +
            nameToFile_.get(profileName)).delete();
      nameToFile_.remove(profileName);
      writeProfileMapping(nameToFile_);
   }

   public static DefaultUserProfile getInstance() {
      return staticInstance_;
   }

   /**
    * This special property controls whether or not we always use the
    * "Default user" profile.
    */
   public static boolean getShouldAlwaysUseDefaultProfile() {
      DefaultUserProfile profile = getInstance();
      // This parameter is stored for the default user. Just in case we *are*
      // using the default user, wrap this in synchronized.
      synchronized(lockObject_) {
         PropertyMap defaultProfile = profile.loadProfile(DEFAULT_USER);
         Boolean result = defaultProfile.getBoolean(
               profile.genKey(DefaultUserProfile.class, ALWAYS_USE_DEFAULT_USER));
         if (result == null) {
            return false;
         }
         return result;
      }
   }

   /**
    * As above, but set the value. It will only take effect after a restart.
    */
   public static void setShouldAlwaysUseDefaultProfile(boolean shouldUseDefault) {
      DefaultUserProfile profile = getInstance();
      // Again, in case we're using the default user already, we wrap this
      // in synchronized.
      synchronized(lockObject_) {
         // In order to simplify saving things (since syncToDisk() always
         // saves the current user), we temporarily switch to the
         // default user for this operation.
         String curProfile = profile.profileName_;
         profile.setCurrentProfile(DEFAULT_USER);
         profile.setBoolean(DefaultUserProfile.class,
               ALWAYS_USE_DEFAULT_USER, shouldUseDefault);
         try {
            profile.syncToDisk();
         }
         catch (IOException e) {
            ReportingUtils.logError(e, "Unable to set whether default user should be used");
         }
         profile.setCurrentProfile(curProfile);
      }
   }

   /**
    * For backwards compatibility with 1.4, sometimes some bit of code needs
    * to be able to access the old Preferences that 1.4 attached to the
    * org.micromanager.MMStudio class. This method accesses the user prefs
    * under that class's old namespace (since it's in a different location in
    * 2.0). Will be null if the relevant node doesn't exist.
    */
   public static Preferences getLegacyUserPreferences14() {
      return getLegacyPreferences14(Preferences.userRoot());
   }

   /**
    * For backwards compatibility with 1.4, sometimes some bit of code needs
    * to be able to access the old Preferences that 1.4 attached to the
    * org.micromanager.MMStudio class. This method accesses the system prefs
    * under that class's old namespace (since it's in a different location in
    * 2.0). Will be null if the relevant node doesn't exist.
    */
   public static Preferences getLegacySystemPreferences14() {
      return getLegacyPreferences14(Preferences.systemRoot());
   }

   public static Preferences getLegacyPreferences14(Preferences root) {
      // Ensure the necessary nodes exist.
      try {
         if (!root.nodeExists("org")) {
            return null;
         }
         root = root.node("org");
         if (!root.nodeExists("micromanager")) {
            return null;
         }
         return root.node("micromanager");
      }
      catch (BackingStoreException e) {
         ReportingUtils.logError(e, "Error accessing old user preferences");
         return null;
      }
   }
}
