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
    * return it. This Datastore will not be tracked by MicroManager by
    * default (see the track() method for more information).
    */
   public Datastore createNewDatastore();

   /**
    * Retrieve the Datastore associated with the current open album, or null
    * if there is no album.
    */
   public Datastore getAlbumDatastore();

   /**
    * Return a list of all Datastores that MicroManager is tracking (see the
    * track() method for more information).
    */
   public List<Datastore> getDatastores();

   /**
    * Request that MicroManager track the specified Datastore for you.
    * Tracking does the following things:
    * - Add the Datastore to the list returned by getDatastores().
    * - Find all currently-existing DisplayWindows for this Datastore and
    *   associate them (thus, getDisplays() for this Datastore will return the
    *   displays)
    * - When the last DisplayWindow for the Datastore is closed:
    * -- If the Datastore has not been saved, prompt the user to save (and if
    *    they cancel, closing the DisplayWindow is halted)
    * -- The Datastore is locked, which may have side-effects like finalizing
    *    writing of image data to disk
    * -- The Datastore is removed from the list returned by getDatastores().
    * By default, new Datastores created by the createNewDatastore() method
    * are not tracked, which means you are responsible for ensuring that they
    * are properly closed and saved. Datastores created by MicroManager itself
    * (e.g. by running an MDA) are automatically tracked.
    */
   public void track(Datastore store);

   /**
    * Returns true iff the Datastore is being tracked by MicroManager.
    */
   public boolean getIsTracked(Datastore store);

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

   /**
    * Associate the specified DisplayWindow with the Datastore. This has two
    * effects: first, it will be in the list returned by getDisplays() for that
    * Datastore; second, when the last DisplayWindow for a Datastore is closed,
    * the user is prompted to save (if the Datastore has not already been
    * saved), and if the user cancels, the window is not closed.
    * @throws IllegalArgumentException if the Datastore is not tracked by
    *         MicroManager.
    */
   public void associateDisplay(DisplayWindow window, Datastore store) throws IllegalArgumentException;

   /**
    * Remove the specified DisplayWindow from the list of associated displays
    * for the Datastore. Does nothing if the Datastore is not tracked or if
    * the display is already not associated.
    */
   public void removeDisplay(DisplayWindow window, Datastore store);

   /**
    * Return all associated DisplayWindows for the Datastore. Returns null if
    * the Datastore is not tracked.
    */
   public List<DisplayWindow> getDisplays(Datastore store);
}
