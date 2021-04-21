package org.micromanager.events.internal;

import java.awt.geom.AffineTransform;
import org.micromanager.events.PixelSizeAffineChangedEvent;

public class DefaultPixelSizeAffineChangedEvent implements PixelSizeAffineChangedEvent {
  private final AffineTransform affine_;

  public DefaultPixelSizeAffineChangedEvent(AffineTransform affine) {
    affine_ = affine;
  }

  public AffineTransform getNewPixelSizeAffine() {
    return affine_;
  }
}
