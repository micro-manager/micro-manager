package org.micromanager.events;

import org.micromanager.imagedisplay.DisplayWindow;
import org.micromanager.imagedisplay.VirtualAcquisitionDisplay;

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
