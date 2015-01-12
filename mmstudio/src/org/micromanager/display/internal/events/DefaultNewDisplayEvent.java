package org.micromanager.display.internal.events;

import org.micromanager.display.DisplayWindow;
import org.micromanager.events.NewDisplayEvent;

public class DefaultNewDisplayEvent implements NewDisplayEvent {
   private DisplayWindow window_;

   public DefaultNewDisplayEvent(DisplayWindow window) {
      window_ = window;
   }

   @Override
   public DisplayWindow getDisplayWindow() {
      return window_;
   }
}
