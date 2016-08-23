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
import org.micromanager.PropertyMap;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;

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
    * Generate a ContrastSettings object with the provided values. This version
    * of the method is to be used for single-component images.
    * @param contrastMin The highest pixel intensity corresponding to black.
    * @param contrastMax The lowest pixel intensity corresponding to full
    *        intensity.
    * @param gamma The gamma curve parameter.
    * @param isVisible Whether or not this channel is visible in the display
    *        when the display is showing multiple channels simultaneously.
    * @return a DisplaySettings.ContrastSettings object, whose properties are
    *         all length-1 arrays with the provided values.
    */
   public DisplaySettings.ContrastSettings getContrastSettings(
         Integer contrastMin, Integer contrastMax, Double gamma,
         Boolean isVisible);

   /**
    * Generate a ContrastSettings object with the provided values. This version
    * of the method is to be used for multi-component (e.g. RGB) images.
    * @param contrastMins Array with the highest pixel intensity corresponding 
    *        to black, for each component.
    * @param contrastMaxes Array with the lowest pixel intensity corresponding to full
    *        intensity, for each component.
    * @param gammas Array with the gamma curve parameters. NOTE: for now this parameter is
    *        not used to control display of multi-component images.
    * @param isVisible Whether or not this channel is visible in the display
    *        when the display is showing multiple channels simultaneously.
    * @return a DisplaySettings.ContrastSettings object.
    */
   public DisplaySettings.ContrastSettings getContrastSettings(
         Integer[] contrastMins, Integer[] contrastMaxes, Double[] gammas,
         Boolean isVisible);

   /**
    * Generate a HistogramData object based on the pixels in the provided
    * Image. HistogramData objects may be used for controlling the histogram(s)
    * in the Inspector window by posting a NewHistogramsEvent to the display.
    * @param image The image whose pixel intensities will be examined.
    * @param component The component of the image (use 0 for grayscale images).
    * @param binPower The number of bins in the resulting histogram, expressed
    *        as an exponent for a power of 2. For example, using 8 here will
    *        result in a histogram with 256 bins. Note: if accurate min/max
    *        pixel intensities are required, then this must equal the bitDepth
    *        parameter; see notes on HistogramData.getMinVal() and
    *        HistogramData.getMaxVal() for more information.
    * @param bitDepth The range of values accepted by the histogram, expressed
    *        as a power of 2. For example, using 10 here would result in a
    *        histogram whose bins accept values from 0 through 1023,
    *        inclusive. The other statistics provided in the HistogramData are
    *        not constrained by this value. Note: if this bitDepth parameter
    *        does not fully encompass the intensities in the image's pixels,
    *        then an ArrayIndexOutOfBoundsException will be thrown.
    * @param extremaPercentage The percentage of pixels to ignore when
    *        calculating the min/max intensities.
    * @param shouldCalcStdDev If true, the standard deviation will be
    *        calculated in the resulting HistogramData; otherwise it will be -1
    * @return a HistogramData derived from the pixels in the image.
    */
   public HistogramData calculateHistogram(Image image, int component,
         int binPower, int bitDepth, double extremaPercentage,
         boolean shouldCalcStdDev);

   /**
    * Generate a HistogramData object based on the pixels in the provided
    * Image. HistogramData objects may be used for controlling the histogram(s)
    * in the Inspector window by posting a NewHistogramsEvent to the display.
    * Behaves as calculateHistogram, except that the binPower, bitDepth, and
    * extremaPercentage parameters will be derived from the provided
    * DisplaySettings (or the image's native bit depth, if the bitDepthIndex
    * property of the DisplaySettings has a value of 0).
    * @param image The image whose pixel intensities will be examined.
    * @param component The component of the image (use 0 for grayscale images).
    * @param settings The DisplaySettings to use for extracting other
    *        histogram calculation values.
    * @return a HistogramData derived from the pixels in the image.
    */
   public HistogramData calculateHistogramWithSettings(Image image,
         int component, DisplaySettings settings);

   /**
    * Send updated histogram information to anyone who is listening for it for
    * the specified display. This is equivalent to calling
    * calculateHistogramWithSettings for every component in every image in the
    * provided list and then posting a NewHistogramsEvent with the resulting
    * HistogramDatas.
    * @param images List of images to calculate histograms for.
    * @param viewer DataViewer that histogram information should be updated for
    */
   public void updateHistogramDisplays(List<Image> images, DataViewer viewer);

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
    * Create a new Inspector window that shows information for the specified
    * DataViewer, or for the topmost window if the DataViewer is null.
    * @param display The DataViewer the inspector should show information on,
    *        or null to show information on the topmost window.
    */
   public void createInspector(DataViewer display);

   /**
    * Ensure that the "default" Inspector window exists and is visible. This
    * Inspector window is normally created when the first Micro-Manager
    * DisplayWindow is created, but it can be created earlier by calling this
    * method. Note that once the default Inspector has been created, this
    * method will not do anything, even if that Inspector is later closed.
    * @return true if an inspector window is created, false otherwise.
    */
   public boolean createFirstInspector();

   /**
    * Signal that the specified DataViewer has been raised to the top, so that
    * any Inspector windows that show information on the topmost display can
    * change to show information for that viewer.
    * @param display The DataViewer that has been raised to top.
    */
   public void raisedToTop(DataViewer display);

   /**
    * Register a DataViewer with Micro-Manager. This makes Micro-Manager
    * aware of the viewer, so that it may be used by Micro-Manager's widgets
    * (chiefly, the Inspector Window). Use removeViewer() to remove a viewer
    * that has been added by this method.
    * @param viewer The new DataViewer that should be tracked.
    */
   public void addViewer(DataViewer viewer);

   /**
    * Cause Micro-Manager to stop tracking a DataViewer that was previously
    * added with addViewer().
    * @param viewer The DataViewer that should no longer be tracked.
    * @throws IllegalArgumentException if the viewer is not currently tracked.
    */
   public void removeViewer(DataViewer viewer);

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
    * Return the DisplayWindow for the topmost window. This window is not
    * guaranteed to have focus as there may be other non-DisplayWindow windows
    * on top of it. Will be null if there is no open DisplayWindow.
    * @return The top-level DisplayWindow, or null.
    */
   public DisplayWindow getCurrentWindow();

   /**
    * Return all active DisplayWindows. Note this is specifically Micro-
    * Manager DisplayWindows; it doesn't include ImageJ's ImageWindows or
    * any other data-representation windows.
    * @return A list of all DisplayWindows that Micro-Manager knows about.
    */
   public List<DisplayWindow> getAllImageWindows();

   /**
    * Return all DataViewers that Micro-Manager knows about. This includes
    * the results of getAllImageWindows, as well as any extant DataViewers
    * that have been registered with Micro-Manager via the addViewer method.
    * @return List with all DataViewers that Micro-Manager knows about
    */
   public List<DataViewer> getAllDataViewers();

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
    * Provide an ImagceExporter for generating image sequences.
    * @return an ImageExporter instance.
    */
   public ImageExporter createExporter();

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
    * Show a dialog to the user giving them a prompt to close all open windows,
    * and allowing them to decide whether or not to be prompted to save each
    * unsaved open window (or simply cancel the entire action). Assuming the
    * user does not cancel, this method will then call closeAllDisplayWindows.
    */
   public void promptToCloseWindows();

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
