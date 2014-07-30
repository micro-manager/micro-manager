package org.micromanager.data;

import org.micromanager.api.data.Coords;
import org.micromanager.api.data.Image;

/**
 * This class signifies that an image has been added to a Datastore.
 */
public class NewImageEvent implements org.micromanager.api.data.NewImageEvent {
   public Image image_;
   public Coords coords_;
   
   public NewImageEvent(Image image, Coords coords) {
      image_ = image;
      coords_ = coords;
   }

   public Image getImage() {
      return image_;
   }

   public Coords getCoords() {
      return coords_;
   }
}
