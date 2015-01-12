package org.micromanager.events;

public class PixelSizeChangedEvent {
   private double newPixelSizeUm_;
   public PixelSizeChangedEvent(double newPixelSizeUm) {
      newPixelSizeUm_ = newPixelSizeUm;
   }
   public double getNewPixelSizeUm() {
      return newPixelSizeUm_;
   }
}
