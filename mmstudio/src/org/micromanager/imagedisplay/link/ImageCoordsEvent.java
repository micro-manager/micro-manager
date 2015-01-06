package org.micromanager.imagedisplay.link;

import org.micromanager.api.data.Coords;

/**
 * Signifies that the coordinates of the displayed image have been changed.
 */
public class ImageCoordsEvent implements DisplaySettingsEvent {
   private Coords newCoords_;
   
   public ImageCoordsEvent(Coords newCoords) {
      newCoords_ = newCoords;
   }

   public Coords getImageCoords() {
      return newCoords_;
   }
}
