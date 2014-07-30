package org.micromanager.data;

import org.micromanager.api.data.DisplaySettings;

/**
 * This class signifies that new display settings have been set for a 
 * Datastore.
 */
public class NewDisplaySettingsEvent implements org.micromanager.api.data.NewDisplaySettingsEvent {
   private DisplaySettings settings_;
   public NewDisplaySettingsEvent(DisplaySettings settings) {
      settings_ = settings;
   }

   public DisplaySettings getDisplaySettings() {
      return settings_;
   }
}
