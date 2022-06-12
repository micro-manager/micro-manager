/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.display.internal.event;

import org.micromanager.display.DisplayWindow;
import org.micromanager.display.overlay.Overlay;

/**
 * @author mark
 */
public class DisplayWindowDidAddOverlayEvent {
   private final DisplayWindow display_;
   private final Overlay overlay_;

   public static DisplayWindowDidAddOverlayEvent create(DisplayWindow display,
                                                        Overlay overlay) {
      return new DisplayWindowDidAddOverlayEvent(display, overlay);
   }

   private DisplayWindowDidAddOverlayEvent(DisplayWindow display,
                                           Overlay overlay) {
      display_ = display;
      overlay_ = overlay;
   }

   public DisplayWindow getDisplayWindow() {
      return display_;
   }

   public Overlay getOverlay() {
      return overlay_;
   }
}