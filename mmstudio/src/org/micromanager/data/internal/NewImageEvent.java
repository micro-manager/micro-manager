package org.micromanager.data.internal;

import org.micromanager.data.Coords;
import org.micromanager.data.Image;

/**
 * This class signifies that an image has been added to a Datastore.
 */
public class NewImageEvent implements org.micromanager.data.NewImageEvent {
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
