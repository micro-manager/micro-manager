package org.micromanager.api.data;

import java.util.List;

import org.micromanager.api.display.DisplayWindow;

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
    * Retrieve the Datastore associated with the current open album, or null
    * if there is no album.
    */
   public Datastore getAlbumDatastore();

   /**
    * Return a list of all Datastores that MicroManager knows about.
    */
   public List<Datastore> getDatastores();

   /**
    * Add the specified image to the current album datastore. If the current
    * album doesn't exist or has been locked, a new album will be created.
    */
   public void addToAlbum(Image image);

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

   /**
    * Associate the specified DisplayWindow with the Datastore. This does
    * nothing besides ensure that it will be returned by getDisplays().
    */
   public void associateDisplay(DisplayWindow window, Datastore store);

   /**
    * Remove the specified DisplayWindow from the list of associated displays
    * for the Datastore.
    */
   public void removeDisplay(DisplayWindow window, Datastore store);

   /**
    * Return all associated DisplayWindows for the Datastore.
    */
   public List<DisplayWindow> getDisplays(Datastore store);
}
