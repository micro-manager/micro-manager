package org.micromanager.api.display;

/**
 * This interface declares generic display-related methods. You can access
 * a class instance that implements this interface by calling gui.display().
 */
public interface DisplayManager {
   /**
    * Retrieve a DisplaySettings holding the values the user has saved as their
    * default values.
    */
   public DisplaySettings getStandardDisplaySettings();

   /**
    * Generate a "blank" DisplaySettings.Builder with all null values.
    */
   public DisplaySettings.DisplaySettingsBuilder getDisplaySettingsBuilder();
}
