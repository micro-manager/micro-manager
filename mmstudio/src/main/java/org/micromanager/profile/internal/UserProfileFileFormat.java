/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.profile.internal;

import com.google.common.collect.Lists;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;

/**
 * The profile and profile index container file formats.
 *
 * <p>This defines the property map representation for profiles and the profile index. It does not
 * deal with the actual contents (settings/preferences) of a profile.
 *
 * @author Mark A. Tsuchida
 */
final class UserProfileFileFormat {
  private static final String KEY_FORMAT = "Format";
  private static final String KEY_PROFILES = "Profiles";
  private static final String KEY_UUID = "UUID";
  private static final String KEY_NAME = "Name";
  private static final String KEY_FILE = "File";
  private static final String KEY_LAST_SAVED = "LastSaved";
  private static final String KEY_PREFS = "Preferences";

  private static final String PROFILE_INDEX_FORMAT = "ProfileIndex";
  private static final String PROFILE_FORMAT = "UserProfile";

  static final class Index {
    private final List<IndexEntry> entries_;

    Index() {
      entries_ = new ArrayList<IndexEntry>();
    }

    Index(Iterable<IndexEntry> entries) {
      entries_ = Lists.newArrayList(entries);
    }

    Index(PropertyMap pmap) {
      entries_ = new ArrayList<IndexEntry>();
      for (PropertyMap entry : pmap.getPropertyMapList(KEY_PROFILES)) {
        entries_.add(new IndexEntry(entry));
      }
    }

    PropertyMap toPropertyMap() {
      List<PropertyMap> entries = new ArrayList<PropertyMap>();
      for (IndexEntry entry : entries_) {
        entries.add(entry.toPropertyMap());
      }
      return PropertyMaps.builder()
          .putString(KEY_FORMAT, PROFILE_INDEX_FORMAT)
          .putPropertyMapList(KEY_PROFILES, entries)
          .build();
    }

    List<IndexEntry> getEntries() {
      return new ArrayList<IndexEntry>(entries_);
    }
  }

  static final class IndexEntry {
    private final UUID uuid_;
    private final String name_;
    private final String filename_;

    IndexEntry(UUID uuid, String name, String filename) {
      uuid_ = uuid;
      name_ = name;
      filename_ = filename;
    }

    IndexEntry(PropertyMap pmap) {
      uuid_ = pmap.getUUID(KEY_UUID, null);
      name_ = pmap.getString(KEY_NAME, "");
      filename_ = pmap.getString(KEY_FILE, null);
    }

    PropertyMap toPropertyMap() {
      return PropertyMaps.builder()
          .putUUID(KEY_UUID, uuid_)
          .putString(KEY_NAME, name_)
          .putString(KEY_FILE, filename_)
          .build();
    }

    UUID getUUID() {
      return uuid_;
    }

    String getName() {
      return name_;
    }

    String getFilename() {
      return filename_;
    }
  }

  static final class Profile {
    private final PropertyMap settings_;

    Profile() {
      settings_ = PropertyMaps.emptyPropertyMap();
    }

    static Profile fromFilePmap(PropertyMap pmap) throws IOException {
      if (!PROFILE_FORMAT.equals(pmap.getString(KEY_FORMAT, null))) {
        throw new IOException("Invalid profile format");
      }
      return new Profile(pmap.getPropertyMap(KEY_PREFS, PropertyMaps.emptyPropertyMap()));
    }

    static Profile fromSettings(PropertyMap pmap) {
      return new Profile(pmap);
    }

    private Profile(PropertyMap settings) {
      settings_ = settings;
    }

    PropertyMap toPropertyMap() {
      return PropertyMaps.builder()
          .putString(KEY_FORMAT, PROFILE_FORMAT)
          .putString(
              KEY_LAST_SAVED, new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z").format(new Date()))
          .putPropertyMap(KEY_PREFS, settings_)
          .build();
    }

    PropertyMap getSettings() {
      return settings_;
    }
  }
}
