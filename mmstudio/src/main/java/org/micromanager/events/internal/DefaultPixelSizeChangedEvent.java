package org.micromanager.events.internal;

import org.micromanager.events.PixelSizeChangedEvent;

/**
 * This event posts when the pixel size, the size of a camera pixel in the object
 * plane, changes.
 *
 * This event posts on the Studio event bus,
 * so subscribe using {@link org.micromanager.events.EventManager}.
 */
public class DefaultPixelSizeChangedEvent implements PixelSizeChangedEvent {
   private final double newPixelSizeUm_;
   public DefaultPixelSizeChangedEvent (double newPixelSizeUm) {
      newPixelSizeUm_ = newPixelSizeUm;
   }

   /**
    * @return new pixel size in microns.
    */
   public double getNewPixelSizeUm() {
      return newPixelSizeUm_;
   }
}
