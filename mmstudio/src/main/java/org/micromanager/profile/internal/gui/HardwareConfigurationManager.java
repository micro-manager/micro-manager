/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.profile.internal.gui;

import mmcorej.CMMCore;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.UserProfile;
import org.micromanager.profile.UserProfileMigration;
import org.micromanager.profile.UserProfileMigrator;
import org.micromanager.profile.internal.LegacyMM1Preferences;
import org.micromanager.propertymap.MutablePropertyMapView;

import java.io.File;
import java.util.*;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/** @author mark */
public class HardwareConfigurationManager {
  private static final int MAX_RECENT_CONFIGS = 10;
  private static final String DEMO_CONFIG_FILE_NAME = "MMConfig_demo.cfg";

  private static final List<String> DEFAULT_RECENTLY_USED =
      Collections.singletonList(new File(DEMO_CONFIG_FILE_NAME).getAbsolutePath());

  private static enum ProfileKey implements UserProfileMigration {
    /** String list, absolute paths of recently used config files, in most recent first order. */
    RECENTLY_USED {
      @Override
      public void migrate(PropertyMap legacy, MutablePropertyMapView modern) {
        PropertyMap legacySettings =
            legacy.getPropertyMap(
                "org.micromanager.internal.dialogs.IntroDlg", PropertyMaps.emptyPropertyMap());
        final String legacyKey = "recently-used config files";
        if (legacySettings.containsStringList(legacyKey)) {
          List<String> mostRecentlyUsedLast = legacySettings.getStringList(legacyKey);

          List<String> mostRecentlyUsedFirst = new ArrayList<String>(mostRecentlyUsedLast);
          Collections.reverse(mostRecentlyUsedFirst);

          modern.putStringList(name(), mostRecentlyUsedFirst);
        }
      }
    },
    ;

    @Override
    public void migrate(PropertyMap legacy, MutablePropertyMapView modern) {}
  }

  static {
    UserProfileMigrator.registerMigrations(HardwareConfigurationManager.class, ProfileKey.values());
  }

  private final UserProfile profile_;
  private final CMMCore core_;
  private File currentConfiguration_;

  public static HardwareConfigurationManager create(UserProfile profile, CMMCore core) {
    return new HardwareConfigurationManager(profile, core);
  }

  private HardwareConfigurationManager(UserProfile profile, CMMCore core) {
    profile_ = profile;
    core_ = core;
  }

  /**
   * Return the absolute paths of the recently used hardware configuration files in
   * most-recently-used-first order.
   *
   * @return the recently used configuration file paths
   */
  public List<String> getRecentlyUsedConfigFiles() {
    return getRecentlyUsedConfigFilesFromProfile(profile_);
  }

  public static List<String> getRecentlyUsedConfigFilesFromProfile(UserProfile profile) {
    MutablePropertyMapView settings = profile.getSettings(HardwareConfigurationManager.class);
    List<String> files;
    if (settings.containsKey(ProfileKey.RECENTLY_USED.name())) {
      files = settings.getStringList(ProfileKey.RECENTLY_USED.name(), DEFAULT_RECENTLY_USED);
    } else {
      files = getMM1RecentlyUsedConfigFiles();
    }
    ListIterator<String> it = files.listIterator();
    while (it.hasNext()) {
      if (!new File(it.next()).isFile()) {
        it.remove();
      }
    }
    if (files.isEmpty()) {
      return DEFAULT_RECENTLY_USED;
    }
    return files;
  }

  public void loadHardwareConfiguration(String filename) throws Exception {
    File file = new File(filename);
    rememberLoadedConfig(file);
    unloadHardwareConfiguration();
    loadConfigImpl(file);
  }

  public void reloadHardwareConfiguration() throws Exception {
    File file = currentConfiguration_;
    rememberLoadedConfig(file);
    unloadHardwareConfiguration();
    loadConfigImpl(file);
  }

  public void unloadHardwareConfiguration() throws Exception {
    currentConfiguration_ = null;
    core_.unloadAllDevices();
  }

  public String getCurrentHardwareConfiguration() {
    return currentConfiguration_ == null ? null : currentConfiguration_.getAbsolutePath();
  }

  private void loadConfigImpl(File file) throws Exception {
    currentConfiguration_ = file;
    // TODO Modal dialog
    core_.loadSystemConfiguration(file.getAbsolutePath());
  }

  private void rememberLoadedConfig(File newFile) {
    List<String> files =
        profile_
            .getSettings(getClass())
            .getStringList(ProfileKey.RECENTLY_USED.name(), DEFAULT_RECENTLY_USED);
    ListIterator<String> it = files.listIterator();
    while (it.hasNext()) {
      File file = new File(it.next());
      if (!file.isFile() || file.equals(newFile)) {
        it.remove();
      }
    }
    files.add(0, newFile.getAbsolutePath());

    // Now trim the list to the max length, but prefer removing nonexistent
    // files, if any, in the first pass
    it = files.listIterator(files.size());
    while (it.hasPrevious() && files.size() > MAX_RECENT_CONFIGS) {
      File file = new File(it.previous());
      if (!file.isFile()) {
        it.remove();
      }
    }
    // If still too many, remove from the end unconditionally
    while (files.size() > MAX_RECENT_CONFIGS) {
      files.remove(files.size() - 1);
    }

    profile_.getSettings(getClass()).putStringList(ProfileKey.RECENTLY_USED.name(), files);
  }

  private static List<String> getMM1RecentlyUsedConfigFiles() {
    Preferences root = LegacyMM1Preferences.getUserRoot();
    if (root == null) {
      return Collections.emptyList();
    }

    // The actual config file names are stored with procedurally-generated
    // keys, as Preferences is unable to store String arrays.
    // key = "CFGFileEntry%d", with %d ranging 0-4
    HashSet<String> prefKeys;
    try {
      prefKeys = new HashSet<String>(Arrays.asList(root.keys()));
    } catch (BackingStoreException e) {
      return Collections.emptyList();
    }
    List<String> result = new ArrayList<String>(5);
    for (int i = 0; i < 5; ++i) {
      String key = "CFGFileEntry" + i;
      if (prefKeys.contains(key)) {
        String filename = root.get(key, "");
        if (!filename.isEmpty()) {
          result.add(filename);
        }
      }
    }
    return result;
  }
}
