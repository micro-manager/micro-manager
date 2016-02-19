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

package org.micromanager.display.internal.events;

import org.micromanager.data.Image;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.PixelsSetEvent;

/**
 * This class signifies that we just updated the pixels that the canvas is
 * displaying, and thus any associated widgets (e.g. histograms and
 * metadata) also need to be updated.
 */
public class DefaultPixelsSetEvent implements PixelsSetEvent {
   private Image image_;
   private DisplayWindow display_;

   public DefaultPixelsSetEvent(Image image, DisplayWindow display) {
      image_ = image;
      display_ = display;
   }

   @Override
   public Image getImage() {
      return image_;
   }

   @Override
   public DisplayWindow getDisplay() {
      return display_;
   }
}
