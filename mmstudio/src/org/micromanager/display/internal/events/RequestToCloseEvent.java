package org.micromanager.display.internal.events;

import org.micromanager.display.DisplayWindow;

public class RequestToCloseEvent implements org.micromanager.display.RequestToCloseEvent {
   private DisplayWindow window_;
   public RequestToCloseEvent(DisplayWindow window) {
      window_ = window;
   }

   public DisplayWindow getDisplay() {
      return window_;
   }
}
