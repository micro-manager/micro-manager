package org.micromanager.data;

import java.util.List;

import org.micromanager.display.DisplayWindow;

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
    * return it. This Datastore will not be tracked by MicroManager by
    * default (see the org.micromanager.api.display.DisplayManager.track()
    * method for more information).
    */
   public Datastore createNewDatastore();

   /**
    * Retrieve the Datastore associated with the current open album, or null
    * if there is no album.
    */
   public Datastore getAlbumDatastore();

   /**
    * Add the specified image to the current album datastore. If the current
    * album doesn't exist or has been locked, a new album will be created.
    */
   public void addToAlbum(Image image);

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
