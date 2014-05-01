package org.micromanager.api.events;

public class PixelSizeChangedEvent {
   public double newPixelSizeUm_;
   public PixelSizeChangedEvent(double newPixelSizeUm) {
      newPixelSizeUm_ = newPixelSizeUm;
   }
}
