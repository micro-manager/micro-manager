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
 * information pertinent to the currently-displayed image.
 */
public interface PixelsSetEvent {
   /**
    * Provides access to the Image whose pixels were just drawn to the
    * DisplayWindow.
    * @return The most recently-drawn Image.
    */
   public Image getImage();
}
