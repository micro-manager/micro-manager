package org.micromanager.imagedisplay;

import org.micromanager.api.display.DisplayWindow;

/**
 * This event signifies that a display has been destroyed via its
 * forceClosed() method.
 */
public class DisplayDestroyedEvent {
   private DisplayWindow display_;

   public DisplayDestroyedEvent(DisplayWindow display) {
      display_ = display;
   }

   public DisplayWindow getDisplay() {
      return display_;
   }
}
