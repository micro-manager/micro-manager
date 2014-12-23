package org.micromanager.imagedisplay.events;

import org.micromanager.api.display.DisplayWindow;

public class RequestToCloseEvent implements org.micromanager.api.display.RequestToCloseEvent {
   private DisplayWindow window_;
   public RequestToCloseEvent(DisplayWindow window) {
      window_ = window;
   }

   public DisplayWindow getDisplay() {
      return window_;
   }
}
