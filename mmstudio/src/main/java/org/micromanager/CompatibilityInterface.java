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
    * Shows the dialog with options for the currently active autofocus device.
    */
   public void showAutofocusDialog();

   /**
    * Shows the position list dialog
    */
   public void showPositionList();

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
    * @return the currently running Micro-Manager version
    */
   public String getVersion();

   /**
    * Returns true if user has chosen to hide MDA window when it runs.
    * @return true if user has chosen to hide MDA window
    */
   public boolean getHideMDADisplayOption();

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
    * Enabled or disable the ROI buttons on the main window.
    * @param enabled true: enable, false: disable ROI buttons
    */
   public void enableRoiButtons(final boolean enabled);

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
