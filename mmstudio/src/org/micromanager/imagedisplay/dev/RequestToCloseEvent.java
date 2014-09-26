package org.micromanager.imagedisplay.dev;

import org.micromanager.api.data.DisplayWindow;

public class RequestToCloseEvent implements org.micromanager.api.data.RequestToCloseEvent {
   private DisplayWindow window_;
   public RequestToCloseEvent(DisplayWindow window) {
      window_ = window;
   }

   public DisplayWindow getWindow() {
      return window_;
   }
}
