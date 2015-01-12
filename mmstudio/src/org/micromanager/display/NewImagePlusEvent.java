package org.micromanager.display;

import ij.ImagePlus;

/**
 * This event is published by the display's EventBus to indicate that it is
 * using a new ImagePlus object.
 */
public interface NewImagePlusEvent {
   public ImagePlus getImagePlus();
}
