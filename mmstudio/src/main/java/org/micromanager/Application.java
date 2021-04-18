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


import java.awt.Rectangle;
import java.io.IOException;
import javax.swing.JFrame;


/**
 * Provides access to and control of various aspects of the user interface,
 * allowing code to directly update and control the GUI. This interface can
 * be accessed from the Studio by calling Studio.getApplication() or
 * Studio.app().
 */
public interface Application {
   /**
    * Updates the GUI so that its state reflects the current state of the
    * hardware (as understood by the Micro-Manager Core). This method will
    * poll each device in the system for its current state, which may take
    * significant time. Compare refreshGUIFromCache().
    */
   void refreshGUI();

   /**
    * Updates the GUI so that its state reflects the current state of the
    * hardware (as understood by the Micro-Manager Core). This method will
    * pull values from the Core's System State Cache, which is fast but may not
    * necessarily reflect the actual state of hardware.
    */
   void refreshGUIFromCache();

   /**
    * Set the exposure time for the current channel (if any). Equivalent to
    * updating the exposure time field in the main window.
    * @param exposureMs Exposure time, in milliseconds.
    */
   void setExposure(double exposureMs);

   /**
    * Updates the exposure time associated with the given preset. If the
    * channel-group and channel name match the current active channel settings,
    * then the displayed exposure time will also be updated.
    *
    * @param channelGroup Name of the config group used to control the channel.
    * @param channel Name of the preset in the config group that refers to the
    *        channel that should be updated.
    * @param exposure New exposure time to set.
    */
   void setChannelExposureTime(String channelGroup, String channel,
           double exposure);

   /**
    * Retrieve the exposure time that has been set for the specified channel.
    *
    * @param channelGroup Name of the config group used to control the channel.
    * @param channel Name of the preset in the config group that refers to the
    *        channel whose exposure time is desired.
    * @param defaultExp Default value to return if no exposure time is found.
    * @return Exposure time for the channel, or the provided default value.
    */
   double getChannelExposureTime(String channelGroup, String channel,
           double defaultExp);

   /**
    * Save the current state of the config file to the specified path. If you
    * have generated new config groups and/or presets, they will be included
    * in the new file.
    * @param path Path to save the file to.
    * @param allowOverwrite If true, any existing file at the specified path
    *        will be overwritten.
    * @throws IOException If shouldOverwrite is false and there is already a
    *         file at the chosen path.
    */
   void saveConfigPresets(String path, boolean allowOverwrite) throws IOException;

   /**
    * Pop up the dialog used to configure the autofocus settings for the
    * current autofocus device.
    */
   void showAutofocusDialog();

   /**
    * Display the position list dialog.
    */
   void showPositionList();

   /**
    * Set the default camera's ROI -- a convenience function. Will stop and
    * start Live mode for you, and update the GUI's display of values such as
    * the view dimensions.
    * @param rect Rectangle defining the ROI
    * @throws Exception if there is an error in the Core when setting the ROI
    */
   void setROI(Rectangle rect) throws Exception;

   /**
    * Move the main Micro-Manager window to the top of the user interface.
    */
   void makeActive();

   /**
    * Provide access to the main window of the program. This is largely
    * intended to allow client code to position their windows with respect
    * to the main window.
    * @return the main Window
    */
   JFrame getMainWindow();

   /**
    * Provides access to the application skin API for determining colors for
    * various GUI components.
    * @return ApplicationSkin instance.
    */
   ApplicationSkin skin();

   /**
    * Provides access to the application skin API for determining colors for
    * various GUI components. Identical to skin() except in name.
    * @return ApplicationSkin instance.
    */
   ApplicationSkin getApplicationSkin();
}
