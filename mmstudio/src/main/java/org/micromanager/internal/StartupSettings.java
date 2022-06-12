/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.internal;

import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.UserProfile;
import org.micromanager.profile.UserProfileMigration;
import org.micromanager.profile.UserProfileMigrator;
import org.micromanager.propertymap.MutablePropertyMapView;

/**
 * Class that makes it possible to optionally skip profile and/or configuration
 * selection at startup.
 *
 * @author Mark A. Tsuchida
 */
public class StartupSettings {
   private enum ProfileKey implements UserProfileMigration {
      SKIP_PROFILE_SELECTION_AT_STARTUP {
         @Override
         public void migrate(PropertyMap legacy, MutablePropertyMapView modern) {
            PropertyMap legacySettings = legacy.getPropertyMap(
                  "org.micromanager.internal.utils.DefaultUserProfile",
                  PropertyMaps.emptyPropertyMap());
            final String legacyKey = "always use the default user profile";
            if (legacySettings.containsBoolean(legacyKey)) {
               modern.putBoolean(name(), legacySettings.getBoolean(legacyKey, false));
            }
         }
      },

      SKIP_CONFIG_SELECTION_AT_STARTUP {
         @Override
         public void migrate(PropertyMap legacy, MutablePropertyMapView modern) {
            PropertyMap legacySettings = legacy.getPropertyMap(
                  "org.micromanager.internal.dialogs.IntroDlg",
                  PropertyMaps.emptyPropertyMap());
            final String legacyKey =
                  "whether or not the intro dialog should include a prompt for the config file";
            if (legacySettings.containsBoolean(legacyKey)) {
               modern.putBoolean(name(), !legacySettings.getBoolean(legacyKey, true));
            }
         }
      },

      ;

      @Override
      public void migrate(PropertyMap legacy, MutablePropertyMapView modern) {
      }
   }

   static {
      UserProfileMigrator.registerMigrations(StartupSettings.class,
            ProfileKey.values());
   }

   private final UserProfile profile_;

   public static StartupSettings create(UserProfile profile) {
      return new StartupSettings(profile);
   }

   private StartupSettings(UserProfile profile) {
      profile_ = profile;
   }

   /**
    * Sets the option to skip Profile selection at startup.
    *
    * @param flag If true, do skip profile selection, otherwise, do not.
    */
   public void setSkipProfileSelectionAtStartup(boolean flag) {
      profile_.getSettings(getClass())
            .putBoolean(ProfileKey.SKIP_PROFILE_SELECTION_AT_STARTUP.name(), flag);
   }

   /**
    * Returns whether or not the application will skip profile selection at
    * startup.
    *
    * @return True if profile selection should be skipped, false otherwise.
    */
   public boolean shouldSkipProfileSelectionAtStartup() {
      return profile_.getSettings(getClass())
            .getBoolean(ProfileKey.SKIP_PROFILE_SELECTION_AT_STARTUP.name(),
                  false);
   }

   public void setSkipConfigSelectionAtStartup(boolean skip) {
      profile_.getSettings(getClass()).putBoolean(
            ProfileKey.SKIP_CONFIG_SELECTION_AT_STARTUP.name(), skip);
   }

   public boolean shouldSkipConfigSelectionAtStartup() {
      return profile_.getSettings(getClass())
            .getBoolean(ProfileKey.SKIP_CONFIG_SELECTION_AT_STARTUP.name(), false);
   }

   public boolean shouldSkipUserInteractionWithSplashScreen() {
      return shouldSkipProfileSelectionAtStartup()
            && shouldSkipConfigSelectionAtStartup();
   }
}