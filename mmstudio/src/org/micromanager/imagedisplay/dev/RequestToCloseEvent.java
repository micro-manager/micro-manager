package org.micromanager.imagedisplay.dev;

import org.micromanager.api.display.DisplayWindow;

public class RequestToCloseEvent implements org.micromanager.api.display.RequestToCloseEvent {
   private DisplayWindow window_;
   public RequestToCloseEvent(DisplayWindow window) {
      window_ = window;
   }

   public DisplayWindow getWindow() {
      return window_;
   }
}
