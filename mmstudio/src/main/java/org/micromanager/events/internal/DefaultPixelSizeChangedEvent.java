package org.micromanager.events.internal;

import org.micromanager.events.PixelSizeChangedEvent;

/**
 * This event posts when the pixel size, the size of a camera pixel in the object
 * plane, changes.
 *
 * <p>This event posts on the Studio event bus,
 * so subscribe using {@link org.micromanager.events.EventManager}.</p>
 */
public class DefaultPixelSizeChangedEvent implements PixelSizeChangedEvent {
   private final double newPixelSizeUm_;

   public DefaultPixelSizeChangedEvent(double newPixelSizeUm) {
      newPixelSizeUm_ = newPixelSizeUm;
   }

   /**
    * The new pixel size in microns.
    *
    * @return new pixel size in microns.
    */
   public double getNewPixelSizeUm() {
      return newPixelSizeUm_;
   }
}
