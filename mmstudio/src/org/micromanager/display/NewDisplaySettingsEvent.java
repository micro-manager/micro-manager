package org.micromanager.display;

/**
 * This class signifies that new display settings have been set for a 
 * DisplayWindow.
 */
public interface NewDisplaySettingsEvent {
   public DisplaySettings getDisplaySettings();
   public DisplayWindow getDisplay();
}
