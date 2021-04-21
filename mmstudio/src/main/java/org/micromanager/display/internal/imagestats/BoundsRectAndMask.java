/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.display.internal.imagestats;

import java.awt.Rectangle;
import java.util.Arrays;

/** @author mark */
public class BoundsRectAndMask {
  private final Rectangle bounds_;
  private final byte[] mask_;

  private static final BoundsRectAndMask UNSELECTED = new BoundsRectAndMask(null, null);

  public static BoundsRectAndMask create(Rectangle bounds, byte[] mask) {
    return new BoundsRectAndMask(bounds, mask);
  }

  public static BoundsRectAndMask unselected() {
    return UNSELECTED;
  }

  private BoundsRectAndMask(Rectangle bounds, byte[] mask) {
    bounds_ = bounds;
    mask_ = mask;
  }

  public Rectangle getBounds() {
    return bounds_ == null ? null : new Rectangle(bounds_);
  }

  public byte[] getMask() {
    return mask_ == null ? null : mask_.clone();
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof BoundsRectAndMask)) {
      return false;
    }
    BoundsRectAndMask otherRoi = (BoundsRectAndMask) other;
    if (bounds_ == null || !bounds_.equals(otherRoi.bounds_)) {
      return false;
    }
    return Arrays.equals(mask_, otherRoi.mask_);
  }

  @Override
  public int hashCode() {
    int hash = 7;
    hash = 61 * hash + (this.bounds_ != null ? this.bounds_.hashCode() : 0);
    hash = 61 * hash + Arrays.hashCode(this.mask_);
    return hash;
  }
}
