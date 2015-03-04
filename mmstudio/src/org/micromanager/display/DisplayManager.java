package org.micromanager.display;

import java.util.List;

import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import org.micromanager.PropertyMap;

/**
 * This interface declares generic display-related methods. You can access
 * a class instance that implements this interface by calling mm.display().
 */
public interface DisplayManager {
   /**
    * Create a new Datastore with an associated DisplayWindow that will
    * display the provided Image. The Datastore will use RAM-based storage,
    * and will not be tracked by Micro-Manager by default (see the track()
    * method, below).
    * @param image The Image to display.
    * @return The Datastore created to hold the Image.
    */
   public Datastore show(Image image);

   /**
    * Retrieve a DisplaySettings holding the values the user has saved as their
    * default values.
    */
   public DisplaySettings getStandardDisplaySettings();

   /**
    * Generate a "blank" DisplaySettings.Builder with all null values.
    */
   public DisplaySettings.DisplaySettingsBuilder getDisplaySettingsBuilder();

   /**
    * Load the contents of the display settings file in the specified
    * path, and create a new DisplayWindow for each distinct set of settings,
    * attached to the provided Datastore.
    */
   public List<DisplayWindow> loadDisplaySettings(Datastore store, String path);

   /**
    * Generate a "blank" PropertyMap.PropertyMapBuilder with empty mappings.
    */
   public PropertyMap.PropertyMapBuilder getPropertyMapBuilder();

   /**
    * Create a new DisplayWindow for the specified Datastore and return it.
    */
   public DisplayWindow createDisplay(Datastore store);

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
    * Return a list of all Datastores that MicroManager is tracking (see the
    * track() method for more information).
    */
   public List<Datastore> getTrackedDatastores();

   /**
    * Returns true iff the Datastore is being tracked by MicroManager.
    */
   public boolean getIsTracked(Datastore store);

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

   /**
    * Return the DisplayWindow for the top-level window. Will be null if there
    * is no such window or that window is not a DisplayWindow (e.g. it is an
    * ImageJ window instead).
    */
   public DisplayWindow getCurrentWindow();

   /**
    * Return all active DisplayWindows. Note this is limited to windows created
    * by Micro-Manager (e.g. not windows created by ImageJ).
    */
   public List<DisplayWindow> getAllImageWindows();
}
