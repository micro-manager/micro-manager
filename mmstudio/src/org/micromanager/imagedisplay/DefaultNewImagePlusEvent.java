package org.micromanager.imagedisplay;

import ij.ImagePlus;

import org.micromanager.api.display.NewImagePlusEvent;

/**
 * A new ImagePlus object has been set for this display; make it available
 * to consumers.
 */
class DefaultNewImagePlusEvent implements NewImagePlusEvent {
   private ImagePlus plus_;

   public DefaultNewImagePlusEvent(ImagePlus plus) {
      plus_ = plus;
   }

   public ImagePlus getImagePlus() {
      return plus_;
   }
}
