package org.micromanager.api.data;

/**
 * This class signifies that new display settings have been set for a 
 * Datastore.
 */
public class NewDisplaySettingsEvent {
   private DisplaySettings settings_;
   public NewDisplaySettingsEvent(DisplaySettings settings) {
      settings_ = settings;
   }

   public DisplaySettings getDisplaySettings() {
      return settings_;
   }
}
