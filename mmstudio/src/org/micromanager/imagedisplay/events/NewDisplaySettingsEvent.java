package org.micromanager.imagedisplay.events;

import org.micromanager.api.display.DisplaySettings;
import org.micromanager.api.display.DisplayWindow;

/**
 * This class signifies that new display settings have been set for a 
 * Datastore.
 */
public class NewDisplaySettingsEvent implements org.micromanager.api.display.NewDisplaySettingsEvent {
   private DisplaySettings settings_;
   private DisplayWindow display_;
   public NewDisplaySettingsEvent(DisplaySettings settings, DisplayWindow display) {
      settings_ = settings;
      display_ = display;
   }

   @Override
   public DisplaySettings getDisplaySettings() {
      return settings_;
   }

   @Override
   public DisplayWindow getDisplay() {
      return display_;
   }
}
