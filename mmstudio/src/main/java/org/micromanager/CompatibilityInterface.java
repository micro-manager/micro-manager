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


import java.awt.geom.AffineTransform;
import java.awt.Rectangle;

import org.micromanager.data.Datastore;

// These ought not be part of the public API and methods that refer to them are
// deprecated.
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
    * Executes Acquisition with current settings
    * Will open the Acquisition Dialog when it is not open yet
    * Returns after Acquisition finishes
    * Note that this function should not be executed on the EDT (which is the
    * thread running the UI).  
    * @return The Datastore containing the images from the acquisition.
    * @throws MMScriptException
    */
   public Datastore runAcquisition() throws MMScriptException;
   
   /**
    * Executes Acquisition with current settings but allows for changing the data path.
    * Will open the Acquisition Dialog when it is not open yet.
    * Returns after Acquisition finishes.
    * Note that this function should not be executed on the EDT (which is the
    * thread running the UI).
    * @param name Name of this acquisition.
    * @param root Place in the file system where data can be stored.
    * @return The Datastore containing the images from the acquisition.
    * @throws MMScriptException
    */
   public Datastore runAcquisition(String name, String root) throws MMScriptException;

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
    * Currently active autofocus device (can be either a Java or C++ coded device).
    * @return currently active autofocus device
    */
   public AutofocusPlugin getAutofocus();

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
    * One of the allowed inputs to setBackgroundStyle(), to set the program
    * to a bright, high-contrast "daytime" mode.
    */
   public static final String DAY = "Day";
   /**
    * One of the allowed inputs to setBackgroundStyle(), to set the program
    * to a dark, low-contrast "nighttime" mode.
    */
   public static final String NIGHT = "Night";
   /**
    * A list compiling all of the possible inputs to setBackgroundStyle().
    */
   public static final String[] BACKGROUND_OPTIONS = new String[] {
      DAY, NIGHT
   };

   /**
    * Sets the background color of the GUI to the selected mode. Will throw an
    * IllegalArgumentException if the provided input is not an item from
    * BACKGROUND_OPTIONS.
    * @param backgroundType One of the values from the BACKGROUND_OPTIONS list.
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
    * Autostretch each histogram for the currently-active window, as if the
    * "Auto" button had been clicked for each one.
    */
   public void autostretchCurrentWindow();

   /**
    * Retrieve the affine transform describing how the camera image maps to
    * stage coordinates, for a given pixel size config. This method will pull
    * information from the profile if available, and failing that will look in
    * the Java Preferences. Will return null if no transform is found.
    * @param config The configuration (per core.getCurrentPixelSizeConfig())
    *        to find the affine transform for.
    * @return The transform describing how the camera maps to the stage.
    */
   public AffineTransform getCameraTransform(String config);

   /**
    * Set a new affine transform for describing how the camera image maps to
    * the stage coordinates. The value will be stored in the user's profile.
    * @param transform The new transform to use.
    * @param config The configuration (per core.getCurrentPixelSizeConfig())
    *        to set the affine transform for.
    */
   public void setCameraTransform(AffineTransform transform, String config);
}
