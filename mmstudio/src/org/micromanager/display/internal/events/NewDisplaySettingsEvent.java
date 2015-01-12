package org.micromanager.display.internal.events;

import org.micromanager.display.DisplaySettings;
import org.micromanager.display.DisplayWindow;

/**
 * This class signifies that new display settings have been set for a 
 * Datastore.
 */
public class NewDisplaySettingsEvent implements org.micromanager.display.NewDisplaySettingsEvent {
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
