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

import org.micromanager.data.Coords;
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
    * @param factory A ControlsFactory used to create custom controls for
    *        the DisplayWindow. May be null.
    * @return The created DisplayWindow.
    */
   public DisplayWindow createDisplay(Datastore store,
         ControlsFactory factory);

   /**
    * Create a RequestToDrawEvent and return it. This event can be posted to
    * a DisplayWindow's EventBus (using the DisplayWindow.postEvent() method)
    * to request that the DisplayWindow update its displayed image. This will
    * also force a redraw of GUI controls, overlays, and metadata display.
    * @param coords The Coords of the image to draw. May be null, in which
    *        case the currently-display image(s) will be redrawn.
    * @return An object that implements the RequestToDrawEvent interface.
    */
   public RequestToDrawEvent createRequestToDrawEvent(Coords coords);

   /**
    * Load saved DisplayWindows for the given Datastore, which is assumed to
    * represent data that is saved on disk. DisplayWindow settings are saved
    * in a separate display settings file; one new DisplayWindow will be
    * created for every entry in that file. If no file is found then a single
    * default DisplayWindow will be created, as per createDisplay() above.
    * @param store The Datastore to load display settings for.
    * @return The list of DisplayWindows that were created by this method.
    */
   public List<DisplayWindow> loadDisplays(Datastore store);

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
   public List<Datastore> getManagedDatastores();

   /**
    * Returns true iff the Datastore is being managed by MicroManager.
    * @param store The Datastore whose management status is under question.
    * @return Whether or not Micro-Manager is managing the Datastore.
    */
   public boolean getIsManaged(Datastore store);

   /**
    * Return all associated DisplayWindows for the Datastore. Returns null if
    * the Datastore is not managed.
    * @param store Datastore of interest to the caller
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

   /**
    * Display a prompt for the user to save their data. This is the same
    * prompt that is generated when the last DisplayWindow for a managed
    * Datastore is closed.
    * @param store The Datastore to save.
    * @param display The DisplayWindow over which to show the prompt.
    * @return true if saving was successful or the user explicitly declined
    *         to save; false if the user cancelled or if saving failed.
    */
   public boolean promptToSave(Datastore store, DisplayWindow display);

   /**
    * Given a Datastore, close any open DisplayWindows for that Datastore.
    * If the Datastore is managed, then the user may receive a prompt to
    * save their data, which they have the option to cancel.
    * @param store Datastore for which displays should be closed
    * @return True if all windows were closed; false otherwise (e.g. because
    *         the user cancelled saving).
    */
   public boolean closeDisplaysFor(Datastore store);

   /**
    * Close all open image windows.
    * @param shouldPromptToSave If true, then any open windows for Datastores
    *        that have not been saved will show a prompt to save, and if the
    *        user chooses not to save a file, then the process of closing
    *        windows will be halted. If false, then all windows will be closed
    *        regardless of whether or not the data they show has been saved,
    *        with no prompting of the user whatsoever. Note that the
    *        corresponding File menu option to close all open windows does
    *        prompt the user to ensure they are absolutely certain they want to
    *        close all open windows without prompts to save; this API call does
    *        not have that prompt.
    * @return true if all windows are closed; false if any window did not close.
    */
   public boolean closeAllDisplayWindows(boolean shouldPromptToSave);
}
