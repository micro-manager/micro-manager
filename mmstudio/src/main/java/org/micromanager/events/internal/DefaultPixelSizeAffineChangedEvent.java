package org.micromanager.events.internal;

import org.micromanager.events.PixelSizeAffineChangedEvent;
import java.awt.geom.AffineTransform;

/**
 * This event posts when the affine transform, describing the relation between
 * stage movement and camera coordinates, changes.
 *
 * This event posts on the Studio event bus,
 * so subscribe using {@link org.micromanager.events.EventManager}.
 */
public class DefaultPixelSizeAffineChangedEvent implements PixelSizeAffineChangedEvent {
   private final AffineTransform affine_;

   public DefaultPixelSizeAffineChangedEvent(AffineTransform affine) {
      affine_ = affine;
   }

   /**
    * New affine transform.
    * @return New affine transform describing relation between stage movement and
    * camera coordinates.
    */
   public AffineTransform getNewPixelSizeAffine() {
      return affine_;
   }
}
