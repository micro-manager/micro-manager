package org.micromanager.api.data;

/**
 * This class provides general utility functions for working with
 * Micro-Manager data. You can access it via ScriptInterface's data() method
 * (for example, "gui.data().getCoordsBuilder()").
 */
public interface DataManager {
   /**
    * Generate a "blank" CoordsBuilder for use in constructing new Coords
    * instances.
    */
   public Coords.CoordsBuilder getCoordsBuilder();

   /**
    * Generate a new, "blank" Datastore with no Reader or subscribers, and
    * return it.
    */
   public Datastore createNewDatastore();

   /**
    * Generate a "blank" DisplaySettingsBuilder for use in constructing new
    * DisplaySettings instances.
    */
   public DisplaySettings.DisplaySettingsBuilder getDisplaySettingsBuilder();

   /**
    * Retrieve the DisplaySettings that the user has saved as their default
    * settings.
    */
   public DisplaySettings getStandardDisplaySettings();

   /**
    * Generate a "blank" MetadataBuilder for use in constructing new
    * Metadata instances.
    */
   public Metadata.MetadataBuilder getMetadataBuilder();

   /**
    * Generate a "blank" SummaryMetadataBuilder for use in constructing new
    * SummaryMetadata instances.
    */
   public SummaryMetadata.SummaryMetadataBuilder getSummaryMetadataBuilder();
}
