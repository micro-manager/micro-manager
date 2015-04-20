///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Display implementation
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

import org.micromanager.data.Image;

/**
 * This event is fired immediately after the image data displayed by the
 * DisplayWindow is updated. Listen for it if you have a control that displays
 * information pertinent to the currently-displayed image. This event fires
 * in the main update loop, and thus it is safe to update GUI components from
 * it -- but on the flipside that means that doing anything time-consuming in
 * response to this event is a bad idea because you'll slow down the GUI.
 * Also note that this event can fire multiple times for the same image, due
 * to requests to the display to refresh the image display.
 */
public interface PixelsSetEvent {
   /**
    * Provides access to the Image whose pixels were just drawn to the
    * DisplayWindow.
    * @return The most recently-drawn Image.
    */
   public Image getImage();

   /**
    * Provides the DisplayWindow in which the image was drawn; useful if you
    * have a piece of code that listens for events from multiple
    * DisplayWindows.
    * @return The DisplayWindow in which the image was drawn.
    */
   public DisplayWindow getDisplay();
}
