package org.micromanager.events.internal;

import org.micromanager.events.PixelSizeChangedEvent;

public class DefaultPixelSizeChangedEvent implements PixelSizeChangedEvent {
   private final double newPixelSizeUm_;
   public DefaultPixelSizeChangedEvent (double newPixelSizeUm) {
      newPixelSizeUm_ = newPixelSizeUm;
   }
   public double getNewPixelSizeUm() {
      return newPixelSizeUm_;
   }
}
