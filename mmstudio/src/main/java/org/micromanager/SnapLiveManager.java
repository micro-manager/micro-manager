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

import java.util.List;
import org.micromanager.data.Image;
import org.micromanager.display.DisplayWindow;

/**
 * Interface for interacting with the Snap/Live display and live mode. You can
 * access this via Studio.live() or Studio.getSnapLiveManager().
 */
public interface SnapLiveManager {
   /**
    * Perform a snap and display the results, if desired. Returns the raw
    * snapped image(s).
    * If live mode is currently on, then instead of performing a snap, the
    * most recent images from live mode will be returned immediately.
    * Otherwise, this method will call AcquisitionManager.snap() to perform the
    * snap.
    *
    * @param shouldDisplay If true, then the snapped images will be added to
    *                      the Snap/Live display's Datastore and displayed. Note that the
    *                      displayed images will be run through the current application data
    *                      processing pipeline (if any) prior to display. Consequently, there
    *                      is no guarantee that a Snap/Live display window will be open after
    *                      calling snap(), even if shouldDisplay is set to true. If you want
    *                      to know when the display is visible, call
    *                      getDislay().waitUntilVisible() after calling snap().
    * @return A list of acquired Images from the snap.
    */
   List<Image> snap(boolean shouldDisplay);

   /**
    * Returns whether live mode is on.
    *
    * <p>If live mode is on but suspended, this method returns <code>true</code>.</p>
    *
    * @return true if live mode is on.
    */
   boolean isLiveModeOn();

   @Deprecated
   default boolean getIsLiveModeOn() {
      return isLiveModeOn();
   }

   /**
    * Turns live mode on or off. This will post an
    * org.micromanager.events.LiveModeEvent on the global application event
    * bus.
    *
    * @param on If true, then live mode will be activated; otherwise it will
    *           be halted.
    */
   void setLiveModeOn(boolean on);

   @Deprecated
   default void setLiveMode(boolean on) {
      setLiveModeOn(on);
   }

   /**
    * Temporarily halt live mode, or re-start it after a temporary halt. This
    * is useful for actions that cannot be performed while live mode is
    * running (such as changing many camera settings), so that live mode can
    * be re-started once the action is complete. Instead of calling
    * isLiveModeOn(), stopping it if necessary, and then re-starting it if
    * it was on, you can instead blindly do:
    * - setSuspended(true);
    * - do something that can't run when live mode is on;
    * - setSuspended(false);
    * and live mode will only be re-started if it was on to begin with.
    * Note that suspending live mode does not produce LiveModeEvents, as the
    * expectation is that live mode is only suspended for very brief periods.
    *
    * @param shouldSuspend If true, then live mode will be halted if it is
    *                      running. If false, and live mode was running when
    *                      setSuspended(true) was called, then live mode will be restarted.
    */
   void setSuspended(boolean shouldSuspend);

   /**
    * Insert the provided image into the Datastore, causing it to be displayed
    * in any open Snap/Live DisplayWindows. If no displays are open or if the
    * image dimensions don't match those of the previously-displayed images,
    * then a new display will be created.
    *
    * @param image Image to be displayed
    */
   void displayImage(Image image);

   /**
    * Return the DisplayWindow used for snap/live mode. May be null if that
    * display has been closed. Snap/live mode only "knows about" the display
    * it itself created -- thus, if the user duplicates that display and then
    * closes the original, you will get null when you call this method.
    * Likewise, the snap/live display window that the SnapLiveManager uses
    * is prone to being closed and re-created (e.g. when the parameters of
    * images generated by the Core are changed). Consequently, it is not
    * recommended that you keep references to the result of this method hanging
    * around in memory for long periods, as they may become out-of-date.
    *
    * @return DisplayWindow used for snap/live mode
    */
   DisplayWindow getDisplay();
}
