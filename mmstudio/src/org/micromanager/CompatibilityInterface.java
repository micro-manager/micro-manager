///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, December 3, 2006
//               Chris Weisiger, 2015
//
// COPYRIGHT:    University of California, San Francisco, 2006-2015
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
//

package org.micromanager;

import ij.gui.ImageWindow;

import java.awt.Rectangle;

import java.util.List;

import mmcorej.CMMCore;
import mmcorej.TaggedImage;

import org.micromanager.data.Datastore;
import org.micromanager.display.OverlayPanel;

// These ought not be part of the public API and methods that refer to them are
// deprecated.
import org.json.JSONObject;
import org.micromanager.internal.dialogs.AcqControlDlg;
import org.micromanager.internal.positionlist.PositionListDlg;
import org.micromanager.acquisition.internal.MMAcquisition;
import org.micromanager.internal.utils.AutofocusManager;
import org.micromanager.internal.utils.MMScriptException;


/**
 * Legacy interface that implements some methods from the old 1.4 API.
 * Available in the Beanshell scripting interface as "mm.compat()" or
 * "mm.getCompatibilityInterface()".
 */
public interface CompatibilityInterface {
   /**
    * Brings GUI up to date with the recent changes in the mmcore.
    */
   public void refreshGUI();
   
    /**
    * Brings GUI up to date with the recent changes in the mmcore.
    * Does not communicate with hardware, only checks Cache
    */
   public void refreshGUIFromCache();

   /**
    * Snaps image and displays in AcqWindow.
    * Opens a new AcqWindow when current one is not open.
    * Calling this function is the same as pressing the "Snap" button on the main
    * Micro-manager GUI
    */
   public void snapSingleImage();
   
   /**
    * Opens a new acquisition data set
    * 
    * @param name - Name of the data set
    * @param rootDir - Directory where the new data set is going to be created 
    * @param nrFrames - Number of Frames (time points) in this acquisition
    * @param nrChannels - Number of Channels in this acquisition
    * @param nrSlices - Number of Slices (Z-positions) in this acquisition
    * @param nrPositions Number of (XY) Positions in this acquisition.
    * @param show Whether or not to show this acquisition.
    * @param save Whether or not save data during acquisition.
    * @throws MMScriptException
    */
   public void openAcquisition(String name, String rootDir, int nrFrames, 
           int nrChannels, int nrSlices, int nrPositions, boolean show, 
           boolean save) 
           throws MMScriptException;
   
   /**
    * Another way to create data set, an alternative to the 
    *  openAcquisition(String name, String rootDir, int nrFrames, int nrChannels, int nrSlices, int nrPositions, boolean show, boolean save)
    * 
    * The caller is responsible for providing all required metadata within the summaryMetadata argument
    * @param summaryMetadata The metadata describing the acquisition parameters
    * @param diskCached True if images are cached on disk; false if they are kept in RAM only.
    * @param displayOff True if no display is to be created or shown.
    * @return 
    *
    * @deprecated Use openAcquisition() instead.
    */
   @Deprecated
   public String createAcquisition(JSONObject summaryMetadata, boolean diskCached, 
           boolean displayOff);

   /**
    * Set up image physical dimensions for the data set that has already been opened.
    * Once dimensions of the image has been set, they can't be changed, i.e. subsequent calls to this method will generate an error.
    * Typically there is no need to call this method, except when display options have to be set before the first image is inserted.
    * If this method is not explicitly called after openAcquisition(), the image dimensions will be automatically initialized based
    * on the first image inserted in the data set.
    * 
    * @param name - Name of the data set
    * @param width - Image width in pixels 
    * @param height - Image height in pixels
    * @param bytesPerPixel - Number of bytes per pixel
    * @param bitDepth - Dynamic range in bits per pixel
    * @throws MMScriptException
    */
   public void initializeAcquisition(String name, int width, int height, 
           int bytesPerPixel, int bitDepth) 
           throws MMScriptException;
        
   /**
    * Change an acquisition so that adding images to it is done asynchronously.
    * All calls to e.g. addImageToAcquisition() and other similar functions
    * will return nearly-instantly.
    * @param name of acquisition
    * @throws MMScriptException if the specified acquisition does not exist.
    */
   public void setAcquisitionAddImageAsynchronous(String name) 
           throws MMScriptException;

   /**
    * Provides access to the data set through the MMAcquisition interface.
    * Typically there is no need to use this low-level method and interfere with the default acquisition execution.
    * Intended use is within advanced plugins.
    * @param name - data set name
    * @return deprecated MMAcquisition
    * @throws MMScriptException
    *
    * @deprecated Because it returns an internal object that is subject to change.
    */
   @Deprecated
   public MMAcquisition getAcquisition(String name) throws MMScriptException;

   /**
    * Return the Datastore for the named acquisition.
    * @param name of acquisition
    * @return DataStore associated with this acquisition
    * @throws MMScriptException if the acquisition name is invalid.
    */
   public Datastore getAcquisitionDatastore(String name) throws MMScriptException;

   /**
    * Returns a name beginning with stem that is not yet used.
    * @param stem Base name from which a unique name will be constructed
    * @return name beginning with stem that is not yet used
    */
   public String getUniqueAcquisitionName(String stem);
   
   /**
    * Returns the name of the current album (i.e. the most recently created one)
    * In addition to their use through the scripting interface, Albums are used
    * by the "Camera --&gt; Album" button in the main window of Micro-Manager and 
    * the "--&gt; Album" button on the snap/live window
    * @return Name of the current Album.
    */
   public String getCurrentAlbum();

   /**
    * Checks whether an acquisition with the given name already exists.
    * @param name name of acquisition 
    * @return true is an acquisition with that name exists
    */
   public Boolean acquisitionExists(String name);

   /**
    * Closes the acquisition.
    * After this command metadata is complete, all the references to this data 
    * set are cleaned-up, and no additional images can be added to the acquisition
    * Does not close the window in which the acquisition data is displayed
    * @param name of acquisition
    * @throws MMScriptException 
    */
   public void closeAcquisition(String name) throws MMScriptException;

   /**
    * Close all open displays for the specified acquisition. They will be
    * forced closed with no prompt to save data.
    * @param name of acquisition
    * @throws org.micromanager.internal.utils.MMScriptException
    */
   public void closeAcquisitionDisplays(String name) throws MMScriptException;
   
   /**
    * Closes all currently open acquisitions.
    */
   public void closeAllAcquisitions();
      
   /**
    * Gets an Array with names of all open acquisitions
    * @return Arrays with names of all acquisitions that are currently open
    */
   public String[] getAcquisitionNames();

   
   /**
    * Returns the width (in pixels) of images in this acquisition
    * @param acqName name of acquisition
    * @return width of the images in this acquisition
    * @throws org.micromanager.internal.utils.MMScriptException 
    */
   public int getAcquisitionImageWidth(String acqName) throws MMScriptException;

   /**
    * Returns the width (in pixels) of images in this acquisition
    * @param acqName name of acquisition
    * @return height of the images in this acquisition
    * @throws org.micromanager.internal.utils.MMScriptException
    */
   public int getAcquisitionImageHeight(String acqName) throws MMScriptException;
   
   /**
    * Returns the number of bits used per pixel
    * @param acqName name of the acquisition
    * @return bit-depth of the images in this acquisition
    * @throws org.micromanager.internal.utils.MMScriptException
    */
   public int getAcquisitionImageBitDepth(String acqName) throws MMScriptException;
   
   /**
    * Returns the number of bytes used per pixel
    * @param acqName name of the acquisition
    * @return number of bytes per pixel for the images in this acquisition
    * @throws org.micromanager.internal.utils.MMScriptException
    */
   public int getAcquisitionImageByteDepth(String acqName) throws MMScriptException;

   /**
    * TODO: what exactly does this function return?????
    * Returns ???
    * @param acqName name of this acquisition
    * @return number 
    * @throws org.micromanager.internal.utils.MMScriptException
    */
   public int getAcquisitionMultiCamNumChannels(String acqName) throws MMScriptException;
   
   /**
    * Executes Acquisition with current settings
    * Will open the Acquisition Dialog when it is not open yet
    * Returns after Acquisition finishes
    * Note that this function should not be executed on the EDT (which is the
    * thread running the UI).  
    * @return The name of the acquisition created.
    * @throws MMScriptException
    */
   public String runAcquisition() throws MMScriptException;
   
   /**
    * Executes Acquisition with current settings but allows for changing the data path.
    * Will open the Acquisition Dialog when it is not open yet.
    * Returns after Acquisition finishes.
    * Note that this function should not be executed on the EDT (which is the
    * thread running the UI).
    * @param name Name of this acquisition.
    * @param root Place in the file system where data can be stored.
    * @return The name of the acquisition created
    * @throws MMScriptException
    */
   public String runAcquisition(String name, String root) throws MMScriptException;

   /**
    * Loads setting for Acquisition Dialog from file
    * Will open Acquisition Dialog when it is not open yet
    * @param path file path from which setting for acquisition dialog should 
    * be loaded
    * @throws MMScriptException
    */  
   public void loadAcquisition(String path) throws MMScriptException;
   
   /**
    * Makes this the 'current' PositionList, i.e., the one used by the 
    * Acquisition Protocol.
    * Replaces the list in the PositionList Window
    * It will open a position list dialog if it was not already open.
    * @param pl PosiionLIst to be made the current one
    * @throws MMScriptException
    */
   public void setPositionList(PositionList pl) throws MMScriptException;
   
   /**
    * Returns a copy of the current PositionList, the one used by the 
    * Acquisition Protocol
    * @return copy of the current PositionList
    * @throws MMScriptException
    */
   public PositionList getPositionList() throws MMScriptException;
   
   /**
    * Updates the exposure time associated with the given preset
    * If the channel-group and channel name match the current state
    * the exposure time will also be updated
    * 
    * @param channelGroup - 
    * 
    * @param channel - preset for which to change exposure time
    * @param exposure - desired exposure time
    */
   public void setChannelExposureTime(String channelGroup, String channel,
           double exposure);
   
    /**
    * Returns exposure time for the desired preset in the given channelgroup
    * Acquires its info from the preferences
    * Same thing is used in MDA window, but this class keeps its own copy
    * 
    * @param channelGroup
    * @param channel - 
    * @param defaultExp - default value
    * @return exposure time
    */
   public double getChannelExposureTime(String channelGroup, String channel,
           double defaultExp);
   
   /**
    * Save current configuration
    */
   public void saveConfigPresets();

   /**
    * Returns the ImageJ ImageWindow instance that is used for Snap and Live display.
    * @return ImageJ ImageWindow instance currently used for Snap/Live display
    */
   public ImageWindow getSnapLiveWin();

   /**
    * Installs an autofocus plugin class from the class path.
    * @param className
    * @return ???
   */
   public String installAutofocusPlugin(String className);

   /**
    * Provides access to the Core and its functionality.
    * @return Micro-Manager core object. 
    */
   public CMMCore getCMMCore();

   /**
    * Currently active autofocus device (can be either a Java or C++ coded device).
    * @return currently active autofocus device
    */
   public Autofocus getAutofocus();

   /**
    * Shows the dialog with options for the currently active autofocus device.
    */
   public void showAutofocusDialog();

   /**
    * Allows MMListeners to register themselves so that they will receive
    * alerts as defined in the MMListenerInterface
    * @param newL
    */
   public void addMMListener(MMListenerInterface newL);

   /**
    * Allows MMListeners to remove themselves
    * @param oldL
    */
   public void removeMMListener(MMListenerInterface oldL);

   /**
    * Show a TaggedImage in the snap/live window (uses current camera settings
    * to figure out the shape of the image)
    * @param image TaggedImage (pixel data and metadata tags) to be displayed
    * TODO:
    * @return ????
    */
   public boolean displayImage(TaggedImage image);

   /**
    * Determines whether live mode is currently running.
    * @return when true, live mode is running, when false live mode is not running.
    */
   public boolean isLiveModeOn();

   /**
    * Turn live mode on or off (equivalent to pressing the Live mode button).
    * @param b true starts live mode, false stops live mode.
    */
   public void enableLiveMode(boolean b);

   /**
    * Get the default camera's ROI -- a convenience function.
    * @return default camera's ROI
    * @throws MMScriptException
    */
   public Rectangle getROI() throws MMScriptException;

   /**
    * Set the default camera's ROI -- a convenience function.
    * @param r
    * @throws MMScriptException
    */
   public void setROI(Rectangle r) throws MMScriptException;

   /**
    * Opens the XYPositionList when it is not opened.
    * Adds the current position to the list (same as pressing the "Mark" button in the XYPositionList)
    */
   public void markCurrentPosition();

   /**
    * Returns the Multi-Dimensional Acquisition Window.
    * To show the window, call:
    * AcqControlDlg dlg = gui.getAcqDlg();
    * dlg.setVisible(true);
    * @return Handle to the MDA acquisition dialog
    *
    * @deprecated Use the get/setAcquisitionSettings() interface instead.
    */
   @Deprecated
   public AcqControlDlg getAcqDlg();

   /**
    * Returns the PositionList Dialog.
    * If the Dialog did not yet exist, it will be created.
    * The Dialog will not necessarily be shown, call the setVisibile method of the dialog to do so
    * @return Handle to the positionList Dialog
    *
    * @deprecated Use the get/setPositionList() interface instead.
    */
   @Deprecated
   public PositionListDlg getXYPosListDlg();

   /**
    * Returns true when an acquisition is currently running (note: this function will
    * not return true if live mode, snap, or "Camera --&gt; Album" is currently running
    * @return true when an acquisition is currently running
    */
   public boolean isAcquisitionRunning();

   /**
    * Displays an error message and returns true if the run-time Micro-Manager version
    * is less than the one specified.
    * Versions in Micro-Manager are of the format:
    * major.minor.minute date
    * where ' date' can be omitted
    * Examples:
    * 1.4.6
    * 1.4.6 20110831
    * When a date is appended to a version number, it will be newer than the same version 
    * without a date
    * @param version - minimum version needen to run this code
    * @return true if the run-time Micro-Manager version is less than the 
    * one specified
    * @throws MMScriptException
    */
   public boolean versionLessThan(String version) throws MMScriptException;

   /**
    * Write various properties of MM and the OS to the log.
    */
   public void logStartupProperties();

   /*
    * Make the main window the frontmost, active window again
    */
   public void makeActive();

   /**
    * @return the currently selected AutoFocusManger object
    */
   public AutofocusManager getAutofocusManager();

   /**
    * @return the currently running Micro-Manager version
    */
   public String getVersion();

   /**
    * These strings are the only valid inputs to setBackgroundStyle, below.
    */
   public static final String DAY = "Day";
   public static final String NIGHT = "Night";

   /**
    * Sets the background color of the GUI and all its registered components to 
    * the selected backGroundType
    * @param backgroundType either ScriptInterface.DAY or
    * ScriptInterface.NIGHT.
    */
   public void setBackgroundStyle(String backgroundType);

   /**
    * @return the current Micro-Manager background style, which will be one
    * of ScriptInterface.DAY or ScriptInterface.NIGHT.
    */
   public String getBackgroundStyle();

   /**
    * lets the GUI know that the current configuration has been changed.  Activates
    * the save button it status is true
    * @param status 
    */
   public void setConfigChanged(boolean status);

   /**
    * shows the position list dialog
    */
   public void showXYPositionList();

   /**
    * Open an existing data set. Shows the acquisition in a window.
    * @param location file path to load
    * @param inRAM if set to false, data will not be loaded into RAM
    * @return acquisition name
    * @throws org.micromanager.internal.utils.MMScriptException
    */
   public String openAcquisitionData(String location, boolean inRAM) throws MMScriptException;

   /**
    * Open an existing data set.
    * @param location file path to load
    * @param inRAM if set to false, data will not be loaded into RAM
    * @param show if true, data will be shown in a viewer
    * @return The name of the acquisition object.
    * @throws org.micromanager.internal.utils.MMScriptException
    */
   public String openAcquisitionData(String location, boolean inRAM, boolean show) throws MMScriptException;

   /**
    * Enabled or disable the ROI buttons on the main window.
    * @param enabled true: enable, false: disable ROI buttons
    */
   public void enableRoiButtons(final boolean enabled);

   /**
    * Returns the pipeline
    * @return instance of the acquisition engine
    */
   public IAcquisitionEngine2010 getAcquisitionEngine2010();
   
   /**
    * Returns true if user has chosen to hide MDA window when it runs.
    * @return true if user has chosen to hide MDA window
    */
   public boolean getHideMDADisplayOption();
   
   /**
    * Adds an image processor to the DataProcessor pipeline.
    * @param processor the processor to be added to the DataProcessor pipeline
    */
   public void addImageProcessor(DataProcessor<TaggedImage> processor);

   /**
    * Removes an image processor from the DataProcessor pipeline.
    * @param taggedImageProcessor processor to be removed from the pipeline
    */
   public void removeImageProcessor(DataProcessor<TaggedImage> taggedImageProcessor);

   /**
    * Retrieve a copy of the current DataProcessor pipeline.
    * @return copy of the current DataProcessor pieline
    */
   public List<DataProcessor<TaggedImage>> getImageProcessorPipeline();

   /**
    * Replace the current DataProcessor pipeline with the provided one.
    * @param pipeline pipeline that will be used from now on
    */
   public void setImageProcessorPipeline(List<DataProcessor<TaggedImage>> pipeline);

   /**
    * Register a new DataProcessor class with the Acquisition Engine. For
    * example, if your processor class is named MyProcessor, then you would
    * call this function as:
    * gui.registerProcessorClass(MyProcessor.class, "My Processor");
    * TODO: Explain what one achieves by registering a processor
    * @param processorClass processor to be registered
    * @param name name displayed to the user for this class
    */
   public void registerProcessorClass(Class<? extends DataProcessor<TaggedImage>> 
           processorClass, String name);
   
   /**
    * Pause/Unpause a running acquisition
    * @param state true if paused, false if no longer paused
    */
   public void setPause(boolean state);
   
   /**
    * Returns true if the acquisition is currently paused.
    * @return true if paused, false if not paused
    */
   public boolean isPaused();

   /**
    * Attach a runnable to the acquisition engine. Each index (f, p, c, s) can
    * be specified. Passing a value of -1 should result in the runnable being attached
    * at all values of that index. For example, if the first argument is -1,
    * then the runnable should execute at every frame.
    * @param frame 0-based frame number
    * @param position 0-based position number
    * @param channel 0-based channel number
    * @param slice 0-based (z) slice number 
    * @param runnable code to be run
    */
   public void attachRunnable(int frame, int position, int channel, int slice, 
           Runnable runnable);

   /**
    * Remove runnables from the acquisition engine
    */
   public void clearRunnables();
   
   /**
    * Return current acquisition settings
    * @return acquisition settings instance
    */ 
    SequenceSettings getAcquisitionSettings();
    
   /**
    * Apply new acquisition settings
    * @param settings acquisition settings
    */ 
    public void setAcquisitionSettings(SequenceSettings settings);
 
   /**
    * Displays dialog to save data for one of the currently open acquisitions
    * @param name file-path where to save the data
    * TODO:  What does this flag do????
    * @param prompt 
    * @throws org.micromanager.internal.utils.MMScriptException
    */
    public void promptToSaveAcquisition(String name, boolean prompt) throws MMScriptException;

   /**
    * Request that the given object be added to our EventBus for notification
    * of events occurring. The available event types that subscribers can
    * listen for is in the org.micromanager.api.events package.
    * @param obj object to be added to the EventBus
    */
    public void registerForEvents(Object obj);

   /**
    * Autostretch each histogram for the currently-active window, as if the
    * "Auto" button had been clicked for each one.
    */
    public void autostretchCurrentWindow();

   /**
    * Register an OverlayPanel with the program so that it is attached to all
    * existing and new image display windows.
    * @param panel OverlayPanel to be attached to all display windows
    */
   public void registerOverlay(OverlayPanel panel);
}
