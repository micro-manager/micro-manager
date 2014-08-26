package org.micromanager.data;

import org.micromanager.api.data.Coords;
import org.micromanager.api.data.Image;

/**
 * This class signifies that an image has been added to a Datastore.
 */
public class NewImageEvent implements org.micromanager.api.data.NewImageEvent {
   public Image image_;
   
   public NewImageEvent(Image image) {
      image_ = image;
   }

   @Override
   public Image getImage() {
      return image_;
   }

   @Override
   public Coords getCoords() {
      return image_.getCoords();
   }
}
