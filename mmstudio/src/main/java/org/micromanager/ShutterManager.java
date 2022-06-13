///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
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

package org.micromanager;

import java.util.List;

/**
 * This class provides access to the shutter controls, allowing you to
 * open or close the shutter, as well as turn autoshutter on or off. You can
 * access this class via Studio.shutter() or Studio.getShutterManager().
 * It largely shadows methods in MMCore (like setShutterOpen() or
 * getAutoShutter()), but it is recommended that callers use these methods
 * instead to ensure that all shutter-related GUI objects remain up-to-date,
 * as it also posts events that notify GUI elements when the shutter state
 * changes (see ShutterEvent and AutoShutterEvent).
 */
public interface ShutterManager {
   /**
    * Open or close the shutter. If autoshutter is enabled, it will be
    * disabled. This method will post a ShutterEvent on the application event
    * bus, and if autoshutter was enabled, then it will also post an
    * AutoShutterEvent when it is disabled.
    *
    * @param isOpen if true, the shutter will be opened, otherwise it will be
    *               closed.
    * @return true if autoshutter was disabled as a side-effect of calling
    *     this method; false if autoshutter was already off.
    * @throws Exception if there was a problem setting the shutter state.
    */
   boolean setShutter(boolean isOpen) throws Exception;

   /**
    * Return whether or not the shutter is currently open. A straight pass-
    * through to MMCore.getShutterOpen().
    *
    * @return true if shutter is open, false if closed.
    * @throws Exception if there was a problem getting the shutter state.
    */
   boolean getShutter() throws Exception;

   /**
    * Return a list of device names of devices that can be used as shutter
    * devices. Will be null briefly at the start of the program, before a
    * configuration file has been loaded.
    *
    * @return List of strings of shutter device names.
    */
   List<String> getShutterDevices();

   /**
    * Return the current shutter device. A straight pass-through to
    * MMCore.getShutterDevice();
    *
    * @return Name of the device that is the current shutter device.
    * @throws Exception if there was a problem getting the shutter device.
    */
   String getCurrentShutter() throws Exception;

   /**
    * Turn autoshutter on or off. When autoshutter is enabled, the shutter
    * will open automatically whenever the current camera is performing an
    * acquisition (either a single-image snap or a sequence acquisition).
    * This method will also close the shutter as a side-effect.
    * This method will post an AutoShutterEvent on the application event bus.
    *
    * @param isAuto if true, then autoshutter is enabled, otherwise it will be
    *               disabled.
    * @throws Exception if there was an error setting autoshutter or closing
    *                   the shutter.
    */
   void setAutoShutter(boolean isAuto) throws Exception;

   /**
    * Return true if autoshutter is enabled. A straight passthrough to
    * MMCore.getAutoShutter().
    *
    * @return true if autoshutter is enabled, false if disabled.
    */
   boolean getAutoShutter();
}
