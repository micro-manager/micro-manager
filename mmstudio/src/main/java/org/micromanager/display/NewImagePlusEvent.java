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

import ij.ImagePlus;

/**
 * This event is published by the display's EventBus to indicate that it is
 * using a new ImagePlus object.
 */
public interface NewImagePlusEvent {
   /**
    * @return The DisplayWindow that originated the event.
    */
   public DisplayWindow getDisplay();

   /**
    * @return The ImagePlus the DisplayWindow is now using for displaying image
    * data.
    */
   public ImagePlus getImagePlus();
}
