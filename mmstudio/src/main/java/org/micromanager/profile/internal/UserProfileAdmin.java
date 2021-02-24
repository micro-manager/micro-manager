/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.profile.internal;

import com.google.common.base.CharMatcher;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.io.Files;
import java.awt.Color;
import java.beans.ExceptionListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.apache.commons.lang3.event.EventListenerSupport;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.UserProfile;
import org.micromanager.internal.propertymap.MM1JSONSerializer;
import org.micromanager.internal.utils.JavaUtils;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.internal.utils.ThreadFactoryFactory;
import org.micromanager.profile.internal.UserProfileFileFormat.Index;
import org.micromanager.profile.internal.UserProfileFileFormat.IndexEntry;
import org.micromanager.profile.internal.UserProfileFileFormat.Profile;
import org.micromanager.propertymap.MutablePropertyMapView;

/**
 * Manage user profiles and their storage.
 *
 * @author Mark A. Tsuchida
 */
public final class UserProfileAdmin {
   /*
    * "Old" and "Legacy" refer to the user profile storage format used in
    * 2.0 beta.
    */

   private static final String GLOBAL_PROFILE_FILE = "MMGlobalProfile.json";
   private static final String OLD_GLOBAL_PROFILE_FILE = "GlobalUserProfile.txt";
   private static final String PROFILE_DIRECTORY = "UserProfiles";
   private static final String INDEX_FILE = "Index.json";
   private static final String OLD_INDEX_FILE = "Profiles.txt";
   private static final String WRITE_LOCK_FILE = "UserProfileWriteLock";

   private static final String OLD_DEFAULT_PROFILE_NAME = "Default user";
   private static final String DEFAULT_PROFILE_NAME = "Default User";
   private static final UUID DEFAULT_PROFILE_UUID =
         UUID.fromString("00000000-0000-0000-0000-000000000000");
   
   public static final String READ_ONLY = "ReadOnly";

   private final ProfileWriteLock writeLock_;

   private final ScheduledExecutorService saverExecutor_ =
         Executors.newSingleThreadScheduledExecutor(
                     ThreadFactoryFactory.createThreadFactory(
                           "User Profile Saver"));

   private boolean didMigrateLegacy_ = false;

   private Index virtualIndex_ = new Index();
   private final Map<String, Profile> virtualProfiles_ = new HashMap<>();

   private UUID currentProfileUUID_ = DEFAULT_PROFILE_UUID;

   private final EventListenerSupport<ChangeListener> currentProfileListeners_ =
         EventListenerSupport.create(ChangeListener.class);
   private final EventListenerSupport<ChangeListener> indexListeners_ =
         EventListenerSupport.create(ChangeListener.class);


   public static UserProfileAdmin create() {
      return new UserProfileAdmin();
   }

   private UserProfileAdmin() {
      writeLock_ = acquireWriteLock();
   }

   private ProfileWriteLock acquireWriteLock() {
      ProfileWriteLock lock = null;
      try {
         lock = ProfileWriteLock.tryCreate(
               new File(getAppDataDirectory(), WRITE_LOCK_FILE));
         if (lock == null) {
            ReportingUtils.logMessage(
                  "Failed to acquire User Profile write lock; profiles will be read-only");
         }
      }
      catch (IOException e) {
         ReportingUtils.logError(e,
               "Failed to acquire User Profile write lock due to IO error; profiles will be read-only");
      }
      return lock;
   }

   public void addCurrentProfileChangeListener(ChangeListener listener) {
      currentProfileListeners_.addListener(listener, true);
   }

   public void removeCurrentProfileChangeListener(ChangeListener listener) {
      currentProfileListeners_.removeListener(listener);
   }

   public void addIndexChangeListener(ChangeListener listener) {
      indexListeners_.addListener(listener, true);
   }

   public void removeIndexChangeListener(ChangeListener listener) {
      indexListeners_.removeListener(listener);
   }

   public boolean isReadOnlyMode() {
      return writeLock_ == null;
   }
   
   public boolean isProfileReadOnly() {
      UserProfile profile;
      try {
         profile = getNonSavingProfile(getUUIDOfCurrentProfile());
      }
      catch (IOException e) {
         return true;
      }
      return profile.getSettings(UserProfileAdmin.class).getBoolean(READ_ONLY, false);
   }
   
   public void setProfileReadOnly(boolean readOnly) throws IOException {
     UUID uuid = getUUIDOfCurrentProfile();
     DefaultUserProfile uprofile = (DefaultUserProfile) getNonSavingProfile(uuid);
     uprofile.getSettings(UserProfileAdmin.class).putBoolean(READ_ONLY, readOnly);
     Profile profile = Profile.fromSettings(uprofile.toPropertyMap()); //This is confusing. `DefaultUserProfile` is the class that actually handles the profile in the code. `Profile` is just a file format.
     for (IndexEntry entry : getIndex().getEntries()) {
         if (entry.getUUID().equals(uuid)) {
            final String filename = entry.getFilename();
            profile.toPropertyMap().saveJSON(getModernFile(filename), true, true); //Force write the file even though the profile may be set to readonly.
            return;
         }
     }     
   }

   /**
    * Migrate from the legacy profile format.
    * <p>
    * It is not necessary to call this method unless you want to learn whether
    * migration took place. If you access the profiles first, migration will
    * happen automatically.
    *
    * @return true if migration was necessary; false otherwise
    * @throws IOException
    */
   public boolean migrateLegacyProfiles() throws IOException {
      if (didMigrateLegacy_) {
         return false;
      }
      getIndex();
      return didMigrateLegacy_;
   }

   /**
    * Shut down the scheduled executor used to autosave profiles.
    * <p>
    * Make sure to call {@code syncToDisk} on each autosaving profile before
    * calling this method.
    */
   public void shutdownAutosaves() {
      saverExecutor_.shutdown();
   }

   /*
    * Since creating/deleting/renaming a profile is a rare event, we don't
    * bother to cache the contents of the profile index file, instead
    * reading it every time we need its contents.
    *
    * If a modern index is not found, and a legacy index is found, we
    * migrate the index and all profiles first and be done with it.
    *
    * In read-only mode, all writes go to the "virtual" index and profiles.
    * When reading, the virtual storage is checked first, then the on-disk
    * storage.
    *
    * In read-write mode, the virtual storage stays empty and thus has no
    * effect.
    */

   /**
    * Get the collection of available profiles.
    *
    * @return a map containing the UUIDs and user-visible names of profiles
    * @throws IOException if there was an error reading the profiles, or
    * migrating legacy profiles
    */
   public Map<UUID, String> getProfileUUIDsAndNames() throws IOException {
      Map<UUID, String> ret = new LinkedHashMap<>();
      for (IndexEntry entry : getIndex().getEntries()) {
         UUID uuid = entry.getUUID();
         String name = entry.getName();
         if (uuid != null && name != null) {
            ret.put(uuid, name);
         }
      }
      return ret;
   }

   public UUID getUUIDOfDefaultProfile() {
      return DEFAULT_PROFILE_UUID;
   }

   public String getProfileName(UUID uuid) throws IOException {
      for (IndexEntry entry : getIndex().getEntries()) {
         if (entry.getUUID().equals(uuid)) {
            return entry.getName();
         }
      }
      throw new IllegalArgumentException("No user profile matching UUID " + uuid);
   }

   public UUID getUUIDOfCurrentProfile() {
      return currentProfileUUID_;
   }

   public void setCurrentUserProfile(UUID uuid) throws IOException {
      Preconditions.checkNotNull(uuid);
      if (currentProfileUUID_.equals(uuid)) {
         return;
      }
      for (IndexEntry entry : getIndex().getEntries()) {
         if (entry.getUUID().equals(uuid)) {
            currentProfileUUID_ = uuid;
            currentProfileListeners_.fire().stateChanged(new ChangeEvent(this));
            return;
         }
      }
      throw new IllegalArgumentException("No user profile matching UUID " + uuid);
   }

   /**
    * 
    * @param uuid The unique ID number associated with the profile you want to get.
    * @return A `User Profile` instance that will not save back to file at all.
    * @throws IOException 
    */
   public UserProfile getNonSavingProfile(UUID uuid) throws IOException {
      return getProfileImpl(uuid, false, null);
   }

   /**
    * 
    * @param uuid The unique ID number associated with the profile you want to get.
    * @param errorHandler If an exception occurs during the autosave process the exception will be passed to this object's `exceptionThrown` method.
    * @return A `User Profile` instance that will routinely save to file in case the program crashes.
    * @throws IOException 
    */
   public UserProfile getAutosavingProfile(UUID uuid,
         final ExceptionListener errorHandler) throws IOException {
      return getProfileImpl(uuid, true, errorHandler);
   }

   private UserProfile getProfileImpl(UUID uuid, boolean autosaving,
         final ExceptionListener errorHandler) throws IOException {
      for (IndexEntry entry : getIndex().getEntries()) {
         if (entry.getUUID().equals(uuid)) {
            final String filename = entry.getFilename();
            Profile profile = readFile(filename);
            final DefaultUserProfile uProfile = DefaultUserProfile.create(this,
                  uuid, profile.getSettings());
            MutablePropertyMapView settings = uProfile.getSettings(UserProfileAdmin.class);
            if (!settings.containsKey(READ_ONLY)) {
               settings.putBoolean(READ_ONLY, false);
            }
            uProfile.setFallbackProfile(getNonSavingGlobalProfile());
            if (autosaving) {
               uProfile.setSaver(
                  ProfileSaver.create(
                     uProfile, 
                     () -> {
                        Profile profile1;
                        profile1 = Profile.fromSettings(uProfile.toPropertyMap());
                        try {
                           writeFile(filename, profile1, false);
                        } catch (IOException e) {
                           if (errorHandler != null) {
                              errorHandler.exceptionThrown(e);
                           }
                        }
                     }, 
                     saverExecutor_));
            }
            return uProfile;
         }
      }
      throw new IllegalArgumentException("No user profile matching UUID " + uuid);
   }

   public UserProfile getNonSavingGlobalProfile() throws IOException {
      // TODO Should we have a UUID for the global profile?
      return DefaultUserProfile.create(this, null,
            getGlobalSettingsWithoutMigration());
   }

   public boolean hasGlobalSettings() throws IOException {
      return new File(GLOBAL_PROFILE_FILE).isFile() ||
            new File(OLD_GLOBAL_PROFILE_FILE).isFile();
   }


   /**
    * Create a profile with the given name.
    * <p>
    * This does not check for duplicate names. If duplicate names should be
    * avoided, the caller is responsible for that.
    *
    * @param name the user-visible name of the profile to create
    * @return the UUID of the newly created profile
    * @throws IOException if there was an error writing the index file
    */
   public UUID createProfile(String name) throws IOException {
      Preconditions.checkNotNull(name);
      UUID uuid = UUID.randomUUID();
      String filename = makeFilename(uuid, name);
      IndexEntry entry = new IndexEntry(uuid, name, filename);

      Index index = readIndex();
      List<IndexEntry> entries = index.getEntries();
      entries.add(entry);
      Index updatedIndex = new Index(entries);
      writeIndex(updatedIndex);

      /*
       * Note that we do not need to create the profile file itself; our
       * storage format defines absence of profile file as equal to that
       * profile being empty.
       */

      indexListeners_.fire().stateChanged(new ChangeEvent(this));
      return uuid;
   }

   public UUID duplicateProfile(UUID originalUUID, String newName)
         throws IOException {
      Preconditions.checkNotNull(originalUUID);
      Preconditions.checkNotNull(newName);
      UUID uuid = createProfile(newName);
      String filename = null;
      for (IndexEntry entry : getIndex().getEntries()) {
         if (entry.getUUID().equals(uuid)) {
            filename = entry.getFilename();
         }
      }
      Profile profile = Profile.fromSettings(getProfileSettingsWithoutMigration(originalUUID));
      writeFile(filename, profile, true);

      indexListeners_.fire().stateChanged(new ChangeEvent(this));
      return uuid;
   }

   public void renameProfile(UUID uuid, String newName) throws IOException {
      Preconditions.checkNotNull(uuid);
      Preconditions.checkNotNull(newName);

      List<IndexEntry> entries = getIndex().getEntries();
      ListIterator<IndexEntry> i = entries.listIterator();
      IndexEntry updated = null;
      while (i.hasNext()) {
         IndexEntry entry = i.next();
         if (entry.getUUID().equals(uuid)) {
            updated = new IndexEntry(uuid, newName,
                  entry.getFilename());
            i.set(updated);
            break;
         }
      }
      if (updated != null) {
         writeIndex(new Index(entries));
         indexListeners_.fire().stateChanged(new ChangeEvent(this));
      }
      else {
         throw new IllegalArgumentException("No user profile matching UUID " + uuid);
      }
   }

   public void removeProfile(UUID uuid) throws IOException {
      Preconditions.checkNotNull(uuid);

      List<IndexEntry> entries = getIndex().getEntries();
      ListIterator<IndexEntry> i = entries.listIterator();
      String filename = null;
      while (i.hasNext()) {
         IndexEntry entry = i.next();
         if (entry.getUUID().equals(uuid)) {
            filename = entry.getFilename();
            i.remove();
            break;
         }
      }
      if (filename != null) {
         if (currentProfileUUID_.equals(uuid)) {
            setCurrentUserProfile(getUUIDOfDefaultProfile());
         }
         writeIndex(new Index(entries));
         deleteFile(filename);
         indexListeners_.fire().stateChanged(new ChangeEvent(this));
      }
      else {
         throw new IllegalArgumentException("No user profile matching UUID " + uuid);
      }
   }

   /*
    * Implementation methods
    */

   private PropertyMap getProfileSettingsWithoutMigration(UUID uuid) throws IOException {
      for (IndexEntry entry : getIndex().getEntries()) {
         if (entry.getUUID().equals(uuid)) {
            Profile profile = readFile(entry.getFilename());
            return profile.getSettings();
         }
      }
      throw new IllegalArgumentException("No user profile matching UUID " + uuid);
   }

   public PropertyMap getGlobalSettingsWithoutMigration() throws IOException {
      // Global settings are located in the current directory (= MM directory)
      File global = new File(GLOBAL_PROFILE_FILE);
      try {
         PropertyMap pmap = PropertyMaps.loadJSON(global);
         try {
            return Profile.fromFilePmap(pmap).getSettings();
         }
         catch (IOException ioe) {
            // Tolerate old-style saved under new filename
            return migrateProfile(pmap);
         }
      }
      catch (FileNotFoundException e) {
         File oldGlobal = new File(OLD_GLOBAL_PROFILE_FILE);
         try {
            return migrateProfile(PropertyMaps.loadJSON(oldGlobal));
         }
         catch (FileNotFoundException e2) {
            return PropertyMaps.emptyPropertyMap();
         }
      }
   }

   private Index getIndex() throws IOException {
      Index index = readIndex();
      if (!index.getEntries().isEmpty()) {
         return index;
      }
      if (migrateProfiles()) {
         return readIndex();
      }
      return createIndexAndDefaultProfile();
   }

   private Index readIndex() throws IOException {
      // Combine virtual and actual, virtual first
      List<IndexEntry> entries = virtualIndex_.getEntries();
      try {
         entries.addAll(new Index(PropertyMaps.loadJSON(getModernIndexFile())).
               getEntries());
      }
      catch (FileNotFoundException e) {
         // Use virtual only
      }
      return new Index(entries);
   }

   private void writeIndex(Index index) throws IOException {
      if (isReadOnlyMode()) {
         virtualIndex_ = index;
         return;
      }
      index.toPropertyMap().saveJSON(getModernIndexFile(), true, true);
   }

   private Profile readFile(String filename) throws IOException {
      // Try virtual first; if not found read actual
      Profile ret = virtualProfiles_.get(filename);
      if (ret != null) {
         return ret;
      }
      try {
         return Profile.fromFilePmap(PropertyMaps.loadJSON(getModernFile(filename)));
      }
      catch (FileNotFoundException e) { // Not present is equivalent to empty
         return new Profile();
      }
      catch (IOException e) { // Present but in a bad state.  Try to restore from backup
         String backup = filename + "~";
         try {
            return Profile.fromFilePmap(PropertyMaps.loadJSON(getModernFile(backup)));
         }
         catch (IOException ex) { // Not present is equivalent to empty
            return new Profile();
         }
      }
   }

   /**
    * Saves a profile to a json file.
    * @param filename The name of the file to save to.
    * @param profile The profile to be saved.
    * @param ignoreProfileReadOnly If the `READ_ONLY` setting of the profile is `true` then this method will do not save unless this argument is `true`.
    * @throws IOException 
    */
   private void writeFile(String filename, Profile profile, boolean ignoreProfileReadOnly) throws IOException {
      boolean readOnly;
      if (ignoreProfileReadOnly) {
        readOnly = false;
      } else {
        readOnly = isProfileReadOnly();
      }
      if (isReadOnlyMode() || readOnly) {
         virtualProfiles_.put(filename, profile);
         return;
      }
      profile.toPropertyMap().saveJSON(getModernFile(filename), true, true);
   }

   private void deleteFile(String filename) throws IOException {
      if (isReadOnlyMode()) {
         virtualProfiles_.remove(filename);
         if (getModernFile(filename).exists()) {
            throw new UnsupportedOperationException("Cannot delete profile in read-only mode");
         }
         return;
      }
      getModernFile(filename).delete();
   }

   /**
    * Creates modern index and profiles from legacy index and profiles.
    *
    * @return true if migration occurred; false if the profile index files
    * was not found or could not be read.
    * 
    * @throws IOException when new profiles could not be written.
    */
   private boolean migrateProfiles() throws IOException {
      /*
       * Legacy index is simple JSON object mapping name to filename.
       */
      PropertyMap legacyIndex;
      try {
         legacyIndex = MM1JSONSerializer.fromJSON(
               Files.toString(getLegacyIndexFile(), Charsets.UTF_8));
      }
      catch (IOException e) {
         return false;
      }

      List<IndexEntry> entries = new ArrayList<>();
      for (String legacyName : legacyIndex.keySet()) {
         UUID uuid;
         String newName = legacyName;
         if (OLD_DEFAULT_PROFILE_NAME.equals(legacyName)) {
            uuid = DEFAULT_PROFILE_UUID;
            newName = DEFAULT_PROFILE_NAME;
         }
         else {
            uuid = UUID.randomUUID();
         }
         String filename = makeFilename(uuid, newName);
         entries.add(new IndexEntry(uuid, newName, filename));

         File legacyFile = getLegacyFile(legacyIndex.getString(legacyName, null));
         try {
            PropertyMap legacyProfile = PropertyMaps.loadJSON(legacyFile);
            Profile modernProfile = Profile.fromSettings(migrateProfile(legacyProfile));
            writeFile(filename, modernProfile, true);
         } catch (IOException ignored) {
            // altough listed, this file does not exist.  Simply continue
         }
      }
      writeIndex(new Index(entries));
      didMigrateLegacy_ = true;
      indexListeners_.fire().stateChanged(new ChangeEvent(this));
      return true;
   }

   /**
    * Create an empty modern index and default profile.
    *
    * @return the modern index pmap
    * @throws IOException if there was an error writing files
    */
   private Index createIndexAndDefaultProfile() throws IOException {
      Profile defaultProfile = new Profile();
      IndexEntry entry = new IndexEntry(DEFAULT_PROFILE_UUID,
            DEFAULT_PROFILE_NAME,
            makeFilename(DEFAULT_PROFILE_UUID, DEFAULT_PROFILE_NAME));
      Index index = new Index(Collections.singletonList(entry));
      writeIndex(index);
      indexListeners_.fire().stateChanged(new ChangeEvent(this));
      return index;
   }

   /**
    * Convert a legacy user profile into the modern pmap format.
    * @param legacy the legacy profile pmap
    * @return the modern profile pmap
    */
   private static PropertyMap migrateProfile(PropertyMap legacy) {
      /*
       * Legacy format is a flat JSON object where each key is the
       * ':'-separated pair of the owner class canonical name and setting key.
       *
       * Modern format uses a nested property map for each owner.
       */

      Map<String, PropertyMap.Builder> settings = new LinkedHashMap<>();
      for (String legacyKey : legacy.keySet()) {
         List<String> split = Splitter.on(':').limit(2).splitToList(legacyKey);
         if (split.size() < 2) {
            continue;
         }
         String owner = split.get(0);
         String key = split.get(1);

         if (!settings.containsKey(owner)) {
            settings.put(owner, PropertyMaps.builder());
         }
         settings.get(owner).putOpaqueValue(key,
               legacy.getAsOpaqueValue(legacyKey));
      }

      PropertyMap.Builder modern = PropertyMaps.builder();
      for (Map.Entry<String, PropertyMap.Builder> e : settings.entrySet()) {
         modern.putPropertyMap(e.getKey(), e.getValue().build());
      }
      return modern.build();
   }

   /**
    * Generate a filename for a profile.
    * <p>
    * This is used once when creating the profile. After that, the filename
    * remains constant even if the profile is renamed.
    *
    * @param uuid the profile UUID
    * @param name the user-visible profile name
    * @return a filename for the profile
    */
   private static String makeFilename(UUID uuid, String name) {
      CharMatcher notAllowed = CharMatcher.JAVA_LETTER_OR_DIGIT.negate();
      return notAllowed.trimFrom(notAllowed.collapseFrom(name, '_')) + "-" +
            uuid.toString() + ".json";
   }

   private static File getModernIndexFile() {
      return getModernFile(INDEX_FILE);
   }

   private static File getModernFile(String basename) {
      return new File(getProfilesDirectory(), basename);
   }

   private static File getLegacyIndexFile() {
      return getLegacyFile(OLD_INDEX_FILE);
   }

   private static File getLegacyFile(String basename) {
      return new File(getAppDataDirectory(), basename);
   }

   private static File getProfilesDirectory() {
      File ret = new File(JavaUtils.getApplicationDataPath(), PROFILE_DIRECTORY);
      if (!ret.isDirectory()) {
         ret.mkdirs();
      }
      return ret;
   }

   private static File getAppDataDirectory() {
      JavaUtils.createApplicationDataPathIfNeeded();
      return new File(JavaUtils.getApplicationDataPath());
   }

   public static void main(String[] args) {
      UserProfileAdmin admin = UserProfileAdmin.create();
      try {
         System.out.println("Read Only = " + admin.isReadOnlyMode());
         System.out.println("Migrated = " + admin.migrateLegacyProfiles());

         UUID uuid = admin.getUUIDOfDefaultProfile();
         UserProfile profile = admin.getAutosavingProfile(uuid, (Exception e) -> {
            System.err.println("Exception Listener:" + e);
         });

         profile.getSettings(UserProfileAdmin.class).putColor("Test!", Color.RED);
         ((DefaultUserProfile) profile).close();

         admin.shutdownAutosaves();
      }
      catch (IOException | InterruptedException e) {
         System.err.println(e.getMessage());
      }
   }
}