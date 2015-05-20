///////////////////////////////////////////////////////////////////////////////
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com
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
//
package imagedisplay;

import imagedisplay.DisplayWindow;
import imagedisplay.VirtualAcquisitionDisplay;


/**
 * This event signifies that a new image display window has been created.
 */
public class DisplayCreatedEvent {
   private VirtualAcquisitionDisplay display_;
   private DisplayWindow window_;

   public DisplayCreatedEvent(VirtualAcquisitionDisplay display,
         DisplayWindow window) {
      display_ = display;
      window_ = window;
   }

   public VirtualAcquisitionDisplay getVirtualDisplay() {
      return display_;
   }

   public DisplayWindow getDisplayWindow() {
      return window_;
   }
}

