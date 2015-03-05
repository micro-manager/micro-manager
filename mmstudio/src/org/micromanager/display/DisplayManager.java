///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Display API
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2015
//
// COPYRIGHT:    University of California, San Francisco, 2015
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

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
    * and will not be managed by Micro-Manager by default (see the manage()
    * method, below).
    * @param image The Image to display.
    * @return The Datastore created to hold the Image.
    */
   public Datastore show(Image image);

   /**
    * Retrieve a DisplaySettings holding the values the user has saved as their
    * default values.
    * @return The DisplaySettings as of the last time the user clicked the
    *         "Set as default" button in the Settings tab of a DisplayWindow.
    */
   public DisplaySettings getStandardDisplaySettings();

   /**
    * Generate a "blank" DisplaySettings.Builder with all null values.
    * @return A DisplaySettingsBuilder with no pre-set values.
    */
   public DisplaySettings.DisplaySettingsBuilder getDisplaySettingsBuilder();

   /**
    * Load the contents of the display settings file in the specified
    * path, and create a new DisplayWindow for each distinct set of settings,
    * attached to the provided Datastore.
    * @param store The Datastore to load data for
    * @param path The path of the display settings file
    * @return A list of DisplayWindows created according to the display
    *         settings file.
    */
   public List<DisplayWindow> loadDisplaySettings(Datastore store, String path);

   /**
    * Generate a "blank" PropertyMap.PropertyMapBuilder with empty mappings.
    * @return A PropertyMapBuilder with no pre-set values.
    */
   public PropertyMap.PropertyMapBuilder getPropertyMapBuilder();

   /**
    * Create a new DisplayWindow for the specified Datastore and return it.
    * @param store The Datastore whose data should be displayed.
    * @return The created DisplayWindow.
    */
   public DisplayWindow createDisplay(Datastore store);

   /**
    * Create a new DisplayWindow for the specified Datastore and return it.
    * This version allows you to add your own custom controls to the display
    * that will appear underneath the axis scrollbars.
    * @param store The Datastore whose data should be displayed.
    * @param generator A ControlsGenerator used to create custom controls for
    *        the DisplayWindow. May be null.
    * @return The created DisplayWindow.
    */
   public DisplayWindow createDisplay(Datastore store,
         ControlsGenerator generator);

   /**
    * Request that MicroManager manage the specified Datastore for you.
    * In brief: if you want users to receive a prompt to save their data when
    * the last display for a Datastore you created is closed, then use this
    * method.
    * Specifically, this method does the following things:
    * - Add the Datastore to the list returned by getDatastores().
    * - Find all currently-existing DisplayWindows for this Datastore and
    *   associate them (thus, getDisplays() for this Datastore will return the
    *   displays)
    * - When the last DisplayWindow for the Datastore is closed:
    * -- If the Datastore has not been saved, prompt the user to save (and if
    *    they cancel, closing the DisplayWindow is halted)
    * -- The Datastore is frozen, which may have side-effects like finalizing
    *    writing of image data to disk
    * -- The Datastore is removed from the list returned by getDatastores().
    * By default, new Datastores created by the createNewDatastore() method
    * are not managed, which means you are responsible for ensuring that they
    * are properly closed and saved. Datastores created by MicroManager itself
    * (e.g. by running an MDA) are automatically managed.
    * @param store The Datastore to manage.
    */
   public void manage(Datastore store);

   /**
    * Return a list of all Datastores that MicroManager is managing (see the
    * manage() method for more information).
    * @return A list of all Datastores that Micro-Manager is managing.
    */
   public List<Datastore> getTrackedDatastores();

   /**
    * Returns true iff the Datastore is being managed by MicroManager.
    * @param store The Datastore whose management status is under question.
    * @return Whether or not Micro-Manager is managing the Datastore.
    */
   public boolean getIsTracked(Datastore store);

   /**
    * Associate the specified DisplayWindow with the Datastore. This has two
    * effects: first, it will be in the list returned by getDisplays() for that
    * Datastore; second, when the last DisplayWindow for a Datastore is closed,
    * the user is prompted to save (if the Datastore has not already been
    * saved), and if the user cancels, the display is not closed.
    * NOTE: you should only need to call this if you have created displays
    * prior to calling manage() above, and you want to be able to retrieve
    * those displays using getDisplays() later.
    * @param display The DisplayWindow to be associated with the Datastore.
    * @param store The Datastore this DisplayWindow is associated with.
    * @throws IllegalArgumentException if the Datastore is not managed by
    *         MicroManager.
    */
   public void associateDisplay(DisplayWindow display, Datastore store) throws IllegalArgumentException;

   /**
    * Remove the specified DisplayWindow from the list of associated displays
    * for the Datastore. Does nothing if the Datastore is not managed or if
    * the display is already not associated.
    * @param display The DisplayWindow that should no longer be associated.
    * @param store The Datastore to remove the association with.
    */
   public void removeDisplay(DisplayWindow display, Datastore store);

   /**
    * Return all associated DisplayWindows for the Datastore. Returns null if
    * the Datastore is not managed.
    * @return A list of all DisplayWindows Micro-Manager knows are associated
    *         with the specified Datastore, or null.
    */
   public List<DisplayWindow> getDisplays(Datastore store);

   /**
    * Return the DisplayWindow for the top-level window. Will be null if there
    * is no such window or that window is not a DisplayWindow (e.g. it is an
    * ImageJ window instead).
    * @return The top-level DisplayWindow, or null.
    */
   public DisplayWindow getCurrentWindow();

   /**
    * Return all active DisplayWindows. Note this is limited to windows created
    * by Micro-Manager (e.g. not windows created by ImageJ).
    * @return A list of all DisplayWindows that Micro-Manager knows about.
    */
   public List<DisplayWindow> getAllImageWindows();
}
