/*
 * Project: ASI Ring TIRF Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2022, Applied Scientific Instrumentation
 */

package com.asiimaging.tirf.model;

import com.asiimaging.tirf.model.data.Settings;
import com.asiimaging.tirf.ui.TIRFControlFrame;
import java.util.Objects;
import org.micromanager.Studio;
import org.micromanager.UserProfile;
import org.micromanager.propertymap.MutablePropertyMapView;

public class UserSettings {

   private final Studio studio;
   private final String userName;
   private final MutablePropertyMapView settings;

   public UserSettings(final Studio studio) {
      this.studio = Objects.requireNonNull(studio);
      Class<?> cls = UserSettings.class;
      UserProfile profile = studio.getUserProfile();
      userName = profile.getProfileName();
      settings = profile.getSettings(cls);
   }

   /**
    * Returns an object to save and retrieve settings.
    *
    * @return a reference to MutablePropertyMapView
    */
   public MutablePropertyMapView get() {
      return settings;
   }

   /**
    * Returns the name of the user profile.
    *
    * @return a String containing the name
    */
   public String getUserName() {
      return userName;
   }

   /**
    * Clears all user settings associated with this class name.
    */
   public void clear() {
      settings.clear();
   }

   /**
    * Load user settings.
    */
   public String load() {
      final String json = settings.getString("settings", Settings.createDefaultSettings().toJson());
      if (TIRFControlFrame.DEBUG) {
         studio.logs().logMessage("LOADED JSON => " + json);
      }
      return json;
   }

   /**
    * Save user settings.
    */
   public void save(final TIRFControlModel model) {
      settings.putString("settings", model.toJson());
      if (TIRFControlFrame.DEBUG) {
         studio.logs().logMessage("SAVED JSON => " + model.toJson());
      }
   }
}
